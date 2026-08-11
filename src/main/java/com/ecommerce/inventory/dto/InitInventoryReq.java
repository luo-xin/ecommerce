package com.ecommerce.inventory.dto;
import lombok.Data;

@Data
public class InitInventoryReq {
    private Long productId;
    private Integer quantity;
}
