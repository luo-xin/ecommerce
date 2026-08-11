package com.ecommerce.security.testbackdoor;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.ErrorCode;
import com.ecommerce.common.Result;
import com.ecommerce.security.JwtUtil;
import com.ecommerce.user.dto.LoginResp;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.mapper.UserMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅用于接口自动化测试的"免密换 token"后门接口。
 *
 * 三重隔离保证绝不上 prod：
 *   1. @Profile         — 仅 dev / test profile 加载本 Bean
 *   2. @ConditionalOnProperty — 必须显式开启 ecommerce.test-backdoor.enabled=true
 *   3. @PostConstruct   — 启动时硬校验当前不是 prod profile
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/test")
@Profile({"dev", "test"})
@ConditionalOnProperty(name = "ecommerce.test-backdoor.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TestLoginBackdoorController {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final Environment environment;

    @PostConstruct
    public void guardProd() {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "FATAL: TestLoginBackdoorController must NEVER run with prod profile active");
        }
        log.warn("[TEST-BACKDOOR] /api/internal/test/login-as is ENABLED. This must NOT happen in production.");
    }

    @PostMapping("/login-as")
    public Result<LoginResp> loginAs(@RequestParam Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (user.getStatus() != 1) throw new BusinessException(ErrorCode.LOGIN_FAILED);

        String token = jwtUtil.generateToken(user.getId(), user.getRole(), user.getPasswordVersion());
        log.warn("[TEST-BACKDOOR] issued token for userId={}, role={}", user.getId(), user.getRole());
        return Result.success(LoginResp.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build());
    }
}
