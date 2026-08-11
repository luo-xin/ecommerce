package com.ecommerce.refund.dto;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
public class RefundItemResp {
    private Long refundItemId;
    private Long productId;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
}
