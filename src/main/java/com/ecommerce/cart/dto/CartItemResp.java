package com.ecommerce.cart.dto;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
public class CartItemResp {
    private Long productId;
    private String productName;
    private BigDecimal price;
    private String status;
    private String mainImage;
    private Integer quantity;
    private BigDecimal subtotal;
}
