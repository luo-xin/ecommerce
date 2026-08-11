package com.ecommerce.inventory.dto;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class RestockResp {
    private Long productId;
    private Integer beforeStock;
    private Integer afterStock;
}
