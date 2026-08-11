package com.ecommerce.cart.dto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddCartItemReq {
    @NotNull
    private Long productId;
    @NotNull @Min(1) @Max(999)
    private Integer quantity;
}
