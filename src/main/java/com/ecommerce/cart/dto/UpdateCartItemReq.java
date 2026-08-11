package com.ecommerce.cart.dto;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateCartItemReq {
    @NotNull
    private Integer quantity;
}
