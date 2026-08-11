package com.ecommerce.refund.dto;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
public class RefundListItem {
    private Long refundId;
    private String refundNo;
    private Long orderId;
    private Long userId;
    private BigDecimal refundAmount;
    private String status;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
