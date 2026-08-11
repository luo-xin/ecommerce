package com.ecommerce.refund.service;

import com.ecommerce.common.dto.PageResult;
import com.ecommerce.refund.dto.*;

public interface RefundService {
    ApplyRefundResp applyRefund(Long orderId, ApplyRefundReq req);
    void approveRefund(Long refundId);
    void rejectRefund(Long refundId, RejectRefundReq req);
    RefundDetailResp getRefundDetail(Long refundId);
    RefundDetailResp getLatestRefund(Long orderId);
    PageResult<RefundListItem> adminListRefunds(String status, Long userId, int page, int size);
}
