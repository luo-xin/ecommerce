package com.ecommerce.product.dto;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
public class CategoryTreeResp {
    private Long categoryId;
    private String name;
    private Integer sort;
    private Integer status;
    private List<CategoryTreeResp> children;
}
