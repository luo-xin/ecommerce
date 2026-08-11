package com.ecommerce.user;

import com.ecommerce.common.ErrorCode;
import com.ecommerce.user.dto.RegisterReq;
import com.ecommerce.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = com.ecommerce.user.controller.UserController.class)
@Import(com.ecommerce.config.SecurityConfig.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean UserService userService;
    @MockBean com.ecommerce.security.UserAuthMapper userAuthMapper;
    @MockBean org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    @MockBean com.ecommerce.security.JwtUtil jwtUtil;
    @MockBean com.ecommerce.user.mapper.UserMapper userMapper;
    @MockBean com.ecommerce.user.mapper.UserAddressMapper userAddressMapper;
    @MockBean com.ecommerce.inventory.mapper.InventoryLogMapper inventoryLogMapper;
    @MockBean com.ecommerce.inventory.mapper.InventoryMapper inventoryMapper;
    @MockBean com.ecommerce.order.mapper.OrderItemMapper orderItemMapper;
    @MockBean com.ecommerce.order.mapper.OrderMapper orderMapper;
    @MockBean com.ecommerce.order.mapper.OrderStatusLogMapper orderStatusLogMapper;
    @MockBean com.ecommerce.product.mapper.CategoryMapper categoryMapper;
    @MockBean com.ecommerce.product.mapper.ProductImageMapper productImageMapper;
    @MockBean com.ecommerce.product.mapper.ProductMapper productMapper;
    @MockBean com.ecommerce.refund.mapper.RefundItemMapper refundItemMapper;
    @MockBean com.ecommerce.refund.mapper.RefundMapper refundMapper;

    @Test
    void register_success() throws Exception {
        when(userService.register(any())).thenReturn(Map.of("userId", 1L, "phone", "138****5678", "username", "张三"));

        RegisterReq req = new RegisterReq();
        req.setPhone("13812345678"); req.setPassword("Pass1234");
        req.setConfirmPassword("Pass1234"); req.setUsername("张三");

        mockMvc.perform(post("/api/users/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(1));
    }

    @Test
    void register_duplicatePhone_returns11002() throws Exception {
        when(userService.register(any()))
                .thenThrow(new com.ecommerce.common.BusinessException(ErrorCode.PHONE_EXISTS));

        RegisterReq req = new RegisterReq();
        req.setPhone("13812345678"); req.setPassword("Pass1234");
        req.setConfirmPassword("Pass1234");

        mockMvc.perform(post("/api/users/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(11002));
    }

    @Test
    @WithMockUser
    void getMe_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk());
    }
}
