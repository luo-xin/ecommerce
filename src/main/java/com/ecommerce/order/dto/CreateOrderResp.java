package com.ecommerce.order.dto;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
public class CreateOrderResp {
    private Long orderId;
    private String orderNo;
    private BigDecimal totalAmount;
    private String status;
}
