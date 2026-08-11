package com.ecommerce.refund.dto;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class RefundDetailResp {
    private Long refundId;
    private String refundNo;
    private Long orderId;
    private BigDecimal refundAmount;
    private String status;
    private String reason;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private List<RefundItemResp> items;
}
