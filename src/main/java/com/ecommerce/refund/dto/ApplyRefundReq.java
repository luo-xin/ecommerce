package com.ecommerce.refund.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApplyRefundReq {
    @NotBlank private String reason;
}
