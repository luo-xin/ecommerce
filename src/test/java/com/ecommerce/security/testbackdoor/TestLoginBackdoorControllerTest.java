package com.ecommerce.security.testbackdoor;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.security.JwtUtil;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TestLoginBackdoorController.class)
@Import({SecurityConfig.class, TestBackdoorSecurityConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "ecommerce.test-backdoor.enabled=true")
class TestLoginBackdoorControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserMapper userMapper;
    @MockBean JwtUtil jwtUtil;
    // SecurityConfig 引入了 JwtAuthFilter，需要 mock 它的依赖
    @MockBean com.ecommerce.security.UserAuthMapper userAuthMapper;
    @MockBean org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
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
    void loginAs_validUser_returnsToken() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setUsername("alice");
        user.setRole("USER");
        user.setStatus(1);
        user.setPasswordVersion(1);

        when(userMapper.selectById(eq(42L))).thenReturn(user);
        when(jwtUtil.generateToken(eq(42L), eq("USER"), eq(1)))
                .thenReturn("fake-token-xyz");

        mockMvc.perform(post("/api/internal/test/login-as")
                        .with(csrf())
                        .param("userId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("fake-token-xyz"))
                .andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void loginAs_userNotFound_returns11011() throws Exception {
        when(userMapper.selectById(any())).thenReturn(null);

        mockMvc.perform(post("/api/internal/test/login-as")
                        .with(csrf())
                        .param("userId", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(11011));
    }

    @Test
    void loginAs_disabledUser_returns11005() throws Exception {
        User user = new User();
        user.setId(10L);
        user.setStatus(0);
        when(userMapper.selectById(eq(10L))).thenReturn(user);

        mockMvc.perform(post("/api/internal/test/login-as")
                        .with(csrf())
                        .param("userId", "10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(11005));
    }
}
