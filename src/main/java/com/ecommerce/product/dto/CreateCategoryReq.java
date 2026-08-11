package com.ecommerce.product.dto;
import lombok.Data;

@Data
public class CreateCategoryReq {
    private String name;
    private Long parentId;   // null or 0 = top-level
    private Integer sort;
}
