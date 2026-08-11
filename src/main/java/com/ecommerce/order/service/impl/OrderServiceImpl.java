package com.ecommerce.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.cart.service.CartService;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.ErrorCode;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.order.dto.*;
import com.ecommerce.order.entity.*;
import com.ecommerce.order.mapper.*;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.mapper.ProductImageMapper;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.security.UserContext;
import com.ecommerce.user.entity.UserAddress;
import com.ecommerce.user.mapper.UserAddressMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final ProductMapper productMapper;
    private final ProductImageMapper imageMapper;
    private final UserAddressMapper addressMapper;
    private final InventoryService inventoryService;
    private final CartService cartService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CreateOrderResp createOrder(CreateOrderReq req) {
        // Step 1: validate productIds
        if (req.getProductIds() == null || req.getProductIds().isEmpty())
            throw new BusinessException(ErrorCode.ORDER_EMPTY_PRODUCTS);

        // Deduplicate while preserving order
        List<Long> productIds = req.getProductIds().stream()
                .distinct().collect(Collectors.toList());

        Long userId = UserContext.getUserId();

        // Step 2: validate address
        UserAddress address = addressMapper.selectById(req.getAddressId());
        if (address == null) throw new BusinessException(ErrorCode.ORDER_ADDRESS_NOT_FOUND);
        if (!address.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.ORDER_ADDRESS_NO_PERMISSION);

        // Step 3: get quantities from cart
        Map<Long, Integer> qtyMap = new LinkedHashMap<>();
        for (Long pid : productIds) {
            String cartKey = "cart:" + userId;
            String qtyStr = (String) redisTemplate.opsForHash().get(cartKey, String.valueOf(pid));
            if (qtyStr == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_IN_CART);
            qtyMap.put(pid, Integer.parseInt(qtyStr));
        }

        // Step 4: validate products ON_SALE
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>().in(Product::getId, productIds));
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        for (Long pid : productIds) {
            Product p = productMap.get(pid);
            if (p == null || !"ON_SALE".equals(p.getStatus()))
                throw new BusinessException(ErrorCode.ORDER_PRODUCT_UNAVAILABLE,
                        p != null ? p.getName() : pid.toString());
        }

        // Step 5: deduct inventory (with explicit rollback on failure)
        List<Long> deductedIds = new ArrayList<>();
        String orderNo = generateOrderNo(userId);
        try {
            for (Long pid : productIds) {
                inventoryService.deduct(pid, qtyMap.get(pid), orderNo);
                deductedIds.add(pid);
            }
        } catch (BusinessException e) {
            // Rollback already-deducted Redis keys
            for (Long pid : deductedIds) {
                try { inventoryService.rollback(pid, qtyMap.get(pid), orderNo); }
                catch (Exception re) { log.error("Rollback failed for product {}", pid, re); }
            }
            // Determine which product failed (the one NOT yet deducted)
            Long failedPid = productIds.size() > deductedIds.size()
                    ? productIds.get(deductedIds.size()) : productIds.get(productIds.size() - 1);
            String productName = productMap.containsKey(failedPid)
                    ? productMap.get(failedPid).getName() : failedPid.toString();
            throw new BusinessException(ErrorCode.ORDER_PRODUCT_INSUFFICIENT_STOCK, productName);
        }

        // Step 6: compute total amount
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Long pid : productIds) {
            totalAmount = totalAmount.add(
                    productMap.get(pid).getPrice().multiply(BigDecimal.valueOf(qtyMap.get(pid))));
        }

        // Step 7: build address snapshot
        AddressSnapshot snap = new AddressSnapshot();
        snap.setReceiverName(address.getReceiverName());
        snap.setReceiverPhone(address.getReceiverPhone());
        snap.setProvince(address.getProvince());
        snap.setCity(address.getCity());
        snap.setDistrict(address.getDistrict());
        snap.setDetail(address.getDetail());

        // Step 8: persist order
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus("PENDING_PAYMENT");
        try { order.setAddressSnapshot(objectMapper.writeValueAsString(snap)); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
        // Retry up to 3 times on duplicate orderNo
        int retries = 3;
        while (retries-- > 0) {
            try {
                orderMapper.insert(order);
                break;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                if (retries == 0) throw e;
                order.setOrderNo(generateOrderNo(userId));
                log.warn("OrderNo collision, retrying: {}", order.getOrderNo());
            }
        }

        // Step 9: persist order items
        for (Long pid : productIds) {
            Product p = productMap.get(pid);
            int qty = qtyMap.get(pid);
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(pid);
            item.setProductName(p.getName());
            item.setPrice(p.getPrice());
            item.setQuantity(qty);
            item.setSubtotal(p.getPrice().multiply(BigDecimal.valueOf(qty)));
            orderItemMapper.insert(item);
        }

        // Step 10: insert status log
        insertStatusLog(order.getId(), null, "PENDING_PAYMENT",
                String.valueOf(userId), "创建订单");

        // Step 11: clear cart items (Redis op, outside transaction semantics)
        cartService.removeItems(userId, productIds);

        return CreateOrderResp.builder()
                .orderId(order.getId()).orderNo(orderNo)
                .totalAmount(totalAmount).status("PENDING_PAYMENT").build();
    }

    @Override
    public PageResult<OrderListItem> listOrders(String status, int page, int size) {
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreatedAt);
        Page<Order> p = orderMapper.selectPage(new Page<>(page + 1L, Math.min(size, 100)), qw);
        return buildOrderListResult(p, page, size);
    }

    @Override
    public OrderDetailResp getOrderDetail(Long orderId) {
        Order order = getOwnedOrder(orderId);
        List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);
        AddressSnapshot snap = parseSnapshot(order.getAddressSnapshot());

        return OrderDetailResp.builder()
                .orderId(order.getId()).orderNo(order.getOrderNo())
                .userId(order.getUserId()).totalAmount(order.getTotalAmount())
                .status(order.getStatus()).addressSnapshot(snap)
                .cancelReason(order.getCancelReason())
                .createdAt(order.getCreatedAt()).paidAt(order.getPaidAt())
                .shippedAt(order.getShippedAt()).completedAt(order.getCompletedAt())
                .items(items.stream().map(i -> OrderItemResp.builder()
                        .orderItemId(i.getId()).productId(i.getProductId())
                        .productName(i.getProductName()).price(i.getPrice())
                        .quantity(i.getQuantity()).subtotal(i.getSubtotal())
                        .mainImage(imageMapper.selectMainImageUrl(i.getProductId())).build())
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId, CancelOrderReq req) {
        Order order = getOwnedOrder(orderId);
        if (!"PENDING_PAYMENT".equals(order.getStatus()))
            throw new BusinessException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);

        String reason = req != null ? req.getCancelReason() : null;
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, "PENDING_PAYMENT")
                .set(Order::getStatus, "CANCELLED")
                .set(Order::getCancelReason, reason));
        if (updated == 0) throw new BusinessException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);

        insertStatusLog(orderId, "PENDING_PAYMENT", "CANCELLED",
                String.valueOf(order.getUserId()), reason);

        // Rollback inventory after MySQL commit (REQUIRES_NEW handles its own transaction)
        List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);
        for (OrderItem item : items) {
            try { inventoryService.rollback(item.getProductId(), item.getQuantity(), order.getOrderNo()); }
            catch (Exception e) {
                log.warn("INVENTORY_ROLLBACK_REQUIRED orderNo={} productId={} qty={}",
                    order.getOrderNo(), item.getProductId(), item.getQuantity(), e);
            }
        }
    }

    @Override
    @Transactional
    public void payOrder(Long orderId) {
        Order order = getOwnedOrder(orderId);
        if (!"PENDING_PAYMENT".equals(order.getStatus()))
            throw new BusinessException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, "PENDING_PAYMENT")
                .set(Order::getStatus, "PAID")
                .set(Order::getPaidAt, LocalDateTime.now()));
        if (updated == 0) throw new BusinessException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
        insertStatusLog(orderId, "PENDING_PAYMENT", "PAID",
                String.valueOf(order.getUserId()), "模拟付款");
    }

    @Override
    @Transactional
    public void shipOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        if (!"PAID".equals(order.getStatus()))
            throw new BusinessException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
        String adminId = "ADMIN:" + UserContext.getUserId();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, "PAID")
                .set(Order::getStatus, "SHIPPED")
                .set(Order::getShippedAt, LocalDateTime.now()));
        if (updated == 0) throw new BusinessException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
        insertStatusLog(orderId, "PAID", "SHIPPED", adminId, "管理员发货");
    }

    @Override
    @Transactional
    public void confirmOrder(Long orderId) {
        Order order = getOwnedOrder(orderId);
        if (!"SHIPPED".equals(order.getStatus()))
            throw new BusinessException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, "SHIPPED")
                .set(Order::getStatus, "COMPLETED")
                .set(Order::getCompletedAt, LocalDateTime.now()));
        if (updated == 0) throw new BusinessException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
        insertStatusLog(orderId, "SHIPPED", "COMPLETED",
                String.valueOf(order.getUserId()), "确认收货");
    }

    @Override
    public List<StatusLogResp> getStatusLogs(Long orderId) {
        getOwnedOrder(orderId);
        return statusLogMapper.selectByOrderId(orderId).stream()
                .map(l -> StatusLogResp.builder()
                        .fromStatus(l.getFromStatus()).toStatus(l.getToStatus())
                        .operator(l.getOperator()).remark(l.getRemark())
                        .createdAt(l.getCreatedAt()).build())
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<OrderListItem> adminListOrders(Long filterUserId, String status, int page, int size) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<Order>()
                .eq(filterUserId != null, Order::getUserId, filterUserId)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreatedAt);
        Page<Order> p = orderMapper.selectPage(new Page<>(page + 1L, Math.min(size, 100)), qw);
        return buildOrderListResult(p, page, size);
    }

    @Override
    @Transactional
    public void updateOrderStatus(Long orderId, String fromStatus, String toStatus,
                                  String operator, String remark) {
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, fromStatus)
                .set(Order::getStatus, toStatus));
        if (updated == 0) throw new BusinessException(ErrorCode.REFUND_PROCESS_FAILED);
        insertStatusLog(orderId, fromStatus, toStatus, operator, remark);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Order getOwnedOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        if (!order.getUserId().equals(UserContext.getUserId()))
            throw new BusinessException(ErrorCode.ORDER_NO_PERMISSION);
        return order;
    }

    private void insertStatusLog(Long orderId, String from, String to, String operator, String remark) {
        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setOrderId(orderId);
        statusLog.setFromStatus(from);
        statusLog.setToStatus(to);
        statusLog.setOperator(operator);
        statusLog.setRemark(remark);
        statusLog.setCreatedAt(LocalDateTime.now());
        statusLogMapper.insert(statusLog);
    }

    private String generateOrderNo(Long userId) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uid = String.format("%06d", userId % 1_000_000);
        String rand = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return ts + uid + rand;
    }

    private AddressSnapshot parseSnapshot(String json) {
        try { return objectMapper.readValue(json, AddressSnapshot.class); }
        catch (Exception e) { return null; }
    }

    private PageResult<OrderListItem> buildOrderListResult(Page<Order> p, int page, int size) {
        List<OrderListItem> items = p.getRecords().stream().map(order -> {
            List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
            String firstName = orderItems.isEmpty() ? null : orderItems.get(0).getProductName();
            String firstImage = orderItems.isEmpty() ? null :
                    imageMapper.selectMainImageUrl(orderItems.get(0).getProductId());
            return OrderListItem.builder()
                    .orderId(order.getId()).orderNo(order.getOrderNo())
                    .userId(order.getUserId()).totalAmount(order.getTotalAmount())
                    .status(order.getStatus()).createdAt(order.getCreatedAt())
                    .firstProductName(firstName).firstProductMainImage(firstImage).build();
        }).collect(Collectors.toList());
        return PageResult.<OrderListItem>builder()
                .total(p.getTotal()).page(page).size(size).items(items).build();
    }
}
