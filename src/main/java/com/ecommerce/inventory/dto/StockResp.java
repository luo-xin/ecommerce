package com.ecommerce.inventory.dto;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class StockResp {
    private Long productId;
    private Integer availableStock;
}
