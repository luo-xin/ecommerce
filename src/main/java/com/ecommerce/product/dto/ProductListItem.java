package com.ecommerce.product.dto;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
public class ProductListItem {
    private Long productId;
    private String name;
    private BigDecimal price;
    private String status;
    private String mainImage;
}
