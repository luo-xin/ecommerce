package com.ecommerce.product.dto;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder
public class ProductDetailResp {
    private Long productId;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String description;
    private BigDecimal price;
    private String status;
    private List<ImageResp> images;
}
