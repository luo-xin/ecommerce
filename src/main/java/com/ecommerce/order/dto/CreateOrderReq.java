package com.ecommerce.order.dto;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CreateOrderReq {
    @NotEmpty
    private List<Long> productIds;
    @NotNull
    private Long addressId;
}
