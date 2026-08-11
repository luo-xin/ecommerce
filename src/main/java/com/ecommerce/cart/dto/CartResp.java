package com.ecommerce.cart.dto;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder
public class CartResp {
    private List<CartItemResp> items;
    private BigDecimal totalAmount;
}
