package com.ecommerce.refund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.ErrorCode;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.mapper.OrderItemMapper;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.mapper.OrderStatusLogMapper;
import com.ecommerce.order.entity.OrderStatusLog;
import com.ecommerce.refund.dto.*;
import com.ecommerce.refund.entity.Refund;
import com.ecommerce.refund.entity.RefundItem;
import com.ecommerce.refund.mapper.RefundItemMapper;
import com.ecommerce.refund.mapper.RefundMapper;
import com.ecommerce.refund.service.RefundService;
import com.ecommerce.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundServiceImpl implements RefundService {

    private final RefundMapper refundMapper;
    private final RefundItemMapper refundItemMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final InventoryService inventoryService;

    @Override
    @Transactional
    public ApplyRefundResp applyRefund(Long orderId, ApplyRefundReq req) {
        Long userId = UserContext.getUserId();

        // Step 1: SELECT FOR UPDATE to prevent concurrent race
        Order order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        if (!order.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.ORDER_NO_PERMISSION);

        // Step 2: Must be PAID
        if (!"PAID".equals(order.getStatus()))
            throw new BusinessException(ErrorCode.REFUND_ORDER_STATUS_NOT_ALLOWED);

        // Step 3: No existing APPLYING refund
        if (refundMapper.selectApplyingByOrderId(orderId) != null)
            throw new BusinessException(ErrorCode.REFUND_ALREADY_APPLYING);

        // Step 4-5: Create refund
        String refundNo = "RF" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));

        Refund refund = new Refund();
        refund.setRefundNo(refundNo);
        refund.setOrderId(orderId);
        refund.setUserId(userId);
        refund.setRefundAmount(order.getTotalAmount());
        refund.setStatus("APPLYING");
        refund.setReason(req.getReason());
        refundMapper.insert(refund);

        // Step 6: Create refund items (copy from order_item)
        List<OrderItem> orderItems = orderItemMapper.selectByOrderId(orderId);
        for (OrderItem oi : orderItems) {
            RefundItem ri = new RefundItem();
            ri.setRefundId(refund.getId());
            ri.setOrderItemId(oi.getId());
            ri.setProductId(oi.getProductId());
            ri.setProductName(oi.getProductName());
            ri.setPrice(oi.getPrice());
            ri.setQuantity(oi.getQuantity());
            refundItemMapper.insert(ri);
        }

        // Step 7: Update order to REFUNDING (with status guard)
        // 0 rows means the order status changed concurrently (race condition) — it is not in PAID state anymore
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId).eq(Order::getStatus, "PAID")
                .set(Order::getStatus, "REFUNDING"));
        if (updated == 0) throw new BusinessException(ErrorCode.REFUND_ALREADY_APPLYING);

        // Step 8: Status log
        insertOrderStatusLog(orderId, "PAID", "REFUNDING", String.valueOf(userId), "申请退款");

        return ApplyRefundResp.builder()
                .refundId(refund.getId()).refundNo(refundNo)
                .refundAmount(refund.getRefundAmount()).status("APPLYING").build();
    }

    @Override
    @Transactional
    public void approveRefund(Long refundId) {
        Refund refund = getRefundById(refundId);
        if (!"APPLYING".equals(refund.getStatus()))
            throw new BusinessException(ErrorCode.REFUND_ALREADY_PROCESSED);

        // Step 3: Update refund → COMPLETED
        refundMapper.update(null, new LambdaUpdateWrapper<Refund>()
                .eq(Refund::getId, refundId)
                .set(Refund::getStatus, "COMPLETED")
                .set(Refund::getProcessedAt, LocalDateTime.now()));

        // Step 4: Update orders → REFUNDED (with status guard)
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, refund.getOrderId()).eq(Order::getStatus, "REFUNDING")
                .set(Order::getStatus, "REFUNDED"));
        if (updated == 0) throw new BusinessException(ErrorCode.REFUND_PROCESS_FAILED);

        // Step 5: Order status log
        String adminOp = "ADMIN:" + UserContext.getUserId();
        insertOrderStatusLog(refund.getOrderId(), "REFUNDING", "REFUNDED", adminOp, "退款审核通过");

        // Step 6: Rollback inventory per refund item (REQUIRES_NEW each)
        // TODO: Each inventoryService.rollback() runs in its own REQUIRES_NEW transaction and commits
        //       independently. If a later item's rollback fails, prior items' stock has already been
        //       restored but the outer transaction rolls back (refund stays APPLYING). This is a known
        //       partial-rollback risk that would require a saga/compensation table to fully resolve.
        List<RefundItem> items = refundItemMapper.selectByRefundId(refundId);
        for (RefundItem item : items) {
            try {
                inventoryService.rollback(item.getProductId(), item.getQuantity(), refund.getRefundNo());
            } catch (BusinessException e) {
                log.error("Inventory rollback failed for refund item productId={}", item.getProductId(), e);
                throw e;
            } catch (Exception e) {
                log.error("Inventory rollback failed for refund item productId={}", item.getProductId(), e);
                throw new BusinessException(ErrorCode.REFUND_PROCESS_FAILED);
            }
        }
    }

    @Override
    @Transactional
    public void rejectRefund(Long refundId, RejectRefundReq req) {
        Refund refund = getRefundById(refundId);
        if (!"APPLYING".equals(refund.getStatus()))
            throw new BusinessException(ErrorCode.REFUND_ALREADY_PROCESSED);

        refundMapper.update(null, new LambdaUpdateWrapper<Refund>()
                .eq(Refund::getId, refundId)
                .set(Refund::getStatus, "REJECTED")
                .set(Refund::getRejectReason, req != null ? req.getRejectReason() : null)
                .set(Refund::getProcessedAt, LocalDateTime.now()));

        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, refund.getOrderId()).eq(Order::getStatus, "REFUNDING")
                .set(Order::getStatus, "PAID"));
        if (updated == 0) throw new BusinessException(ErrorCode.REFUND_PROCESS_FAILED);

        String adminOp = "ADMIN:" + UserContext.getUserId();
        insertOrderStatusLog(refund.getOrderId(), "REFUNDING", "PAID", adminOp, "退款申请驳回");
    }

    @Override
    public RefundDetailResp getRefundDetail(Long refundId) {
        Refund refund = getRefundById(refundId);
        if (!refund.getUserId().equals(UserContext.getUserId()))
            throw new BusinessException(ErrorCode.REFUND_NO_PERMISSION);
        return toDetailResp(refund);
    }

    @Override
    public RefundDetailResp getLatestRefund(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        if (!order.getUserId().equals(UserContext.getUserId()))
            throw new BusinessException(ErrorCode.ORDER_NO_PERMISSION);
        Refund refund = refundMapper.selectLatestByOrderId(orderId);
        return refund == null ? null : toDetailResp(refund);
    }

    @Override
    public PageResult<RefundListItem> adminListRefunds(String status, Long filterUserId, int page, int size) {
        LambdaQueryWrapper<Refund> qw = new LambdaQueryWrapper<Refund>()
                .eq(status != null, Refund::getStatus, status)
                .eq(filterUserId != null, Refund::getUserId, filterUserId)
                .orderByDesc(Refund::getCreatedAt);
        int effectiveSize = Math.min(size, 100);
        Page<Refund> p = refundMapper.selectPage(new Page<>(page + 1L, effectiveSize), qw);
        List<RefundListItem> items = p.getRecords().stream().map(r ->
                RefundListItem.builder()
                        .refundId(r.getId()).refundNo(r.getRefundNo()).orderId(r.getOrderId())
                        .userId(r.getUserId()).refundAmount(r.getRefundAmount())
                        .status(r.getStatus()).reason(r.getReason())
                        .createdAt(r.getCreatedAt()).processedAt(r.getProcessedAt()).build()
        ).collect(Collectors.toList());
        return PageResult.<RefundListItem>builder()
                .total(p.getTotal()).page(page).size(effectiveSize).items(items).build();
    }

    private Refund getRefundById(Long refundId) {
        Refund r = refundMapper.selectById(refundId);
        if (r == null) throw new BusinessException(ErrorCode.REFUND_NOT_FOUND);
        return r;
    }

    private RefundDetailResp toDetailResp(Refund r) {
        List<RefundItem> items = refundItemMapper.selectByRefundId(r.getId());
        return RefundDetailResp.builder()
                .refundId(r.getId()).refundNo(r.getRefundNo()).orderId(r.getOrderId())
                .refundAmount(r.getRefundAmount()).status(r.getStatus())
                .reason(r.getReason()).rejectReason(r.getRejectReason())
                .createdAt(r.getCreatedAt()).processedAt(r.getProcessedAt())
                .items(items.stream().map(i -> RefundItemResp.builder()
                        .refundItemId(i.getId()).productId(i.getProductId())
                        .productName(i.getProductName()).price(i.getPrice())
                        .quantity(i.getQuantity()).build())
                        .collect(Collectors.toList()))
                .build();
    }

    private void insertOrderStatusLog(Long orderId, String from, String to, String operator, String remark) {
        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setOrderId(orderId);
        statusLog.setFromStatus(from);
        statusLog.setToStatus(to);
        statusLog.setOperator(operator);
        statusLog.setRemark(remark);
        statusLog.setCreatedAt(LocalDateTime.now());
        statusLogMapper.insert(statusLog);
    }
}
