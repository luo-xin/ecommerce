package com.ecommerce.order.dto;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
public class OrderItemResp {
    private Long orderItemId;
    private Long productId;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
    private String mainImage;  // 商品主图 URL（来自 product_image 表）
}
