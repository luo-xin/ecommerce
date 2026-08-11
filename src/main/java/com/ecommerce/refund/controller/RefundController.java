package com.ecommerce.refund.controller;

import com.ecommerce.common.Result;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.refund.dto.*;
import com.ecommerce.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    // 4.1 Apply refund
    @PostMapping("/api/orders/{orderId}/refunds")
    public Result<ApplyRefundResp> apply(@PathVariable Long orderId, @RequestBody @Valid ApplyRefundReq req) {
        return Result.success(refundService.applyRefund(orderId, req));
    }

    // 4.2 Admin: approve
    @PutMapping("/api/admin/refunds/{refundId}/approve")
    public Result<?> approve(@PathVariable Long refundId) {
        refundService.approveRefund(refundId);
        return Result.success();
    }

    // 4.3 Admin: reject
    @PutMapping("/api/admin/refunds/{refundId}/reject")
    public Result<?> reject(@PathVariable Long refundId, @RequestBody(required = false) RejectRefundReq req) {
        refundService.rejectRefund(refundId, req);
        return Result.success();
    }

    // 4.4 User: get refund detail by refundId
    @GetMapping("/api/refunds/{refundId}")
    public Result<RefundDetailResp> getDetail(@PathVariable Long refundId) {
        return Result.success(refundService.getRefundDetail(refundId));
    }

    // 4.5 User: get latest refund for order
    @GetMapping("/api/orders/{orderId}/refunds/latest")
    public Result<RefundDetailResp> getLatest(@PathVariable Long orderId) {
        return Result.success(refundService.getLatestRefund(orderId));
    }

    // 4.6 Admin: list refunds
    @GetMapping("/api/admin/refunds")
    public Result<PageResult<RefundListItem>> adminList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(refundService.adminListRefunds(status, userId, page, size));
    }
}
