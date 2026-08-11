package com.ecommerce.product.dto;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateProductReq {
    private Long categoryId;
    private String name;
    private String description;
    private BigDecimal price;
}
