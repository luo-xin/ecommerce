package com.ecommerce.refund.dto;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
public class ApplyRefundResp {
    private Long refundId;
    private String refundNo;
    private BigDecimal refundAmount;
    private String status;
}
