package com.ecommerce.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterReq {
    @NotBlank private String phone;
    @NotBlank private String password;
    @NotBlank private String confirmPassword;
    private String username;
}
