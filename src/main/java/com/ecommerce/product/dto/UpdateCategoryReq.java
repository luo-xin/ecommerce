package com.ecommerce.product.dto;
import lombok.Data;

@Data
public class UpdateCategoryReq {
    private String name;
    private Integer sort;
    private Integer status;
}
