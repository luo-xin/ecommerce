package com.ecommerce.inventory.dto;
import lombok.Data;

@Data
public class RestockReq {
    private Integer quantity;
    private String remark;
}
