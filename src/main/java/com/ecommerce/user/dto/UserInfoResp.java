package com.ecommerce.user.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class UserInfoResp {
    private Long userId;
    private String phone;
    private String username;
    private String role;
    private LocalDateTime createdAt;
}
