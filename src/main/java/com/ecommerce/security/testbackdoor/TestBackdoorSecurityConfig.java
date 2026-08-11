package com.ecommerce.security.testbackdoor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 仅在 dev/test profile + 显式开关下生效，为 /api/internal/test/** 单独放出免鉴权链路。
 * 链优先级最高（@Order(0)），不会影响业务 SecurityConfig。
 */
@Configuration
@Profile({"dev", "test"})
@ConditionalOnProperty(name = "ecommerce.test-backdoor.enabled", havingValue = "true")
public class TestBackdoorSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain testBackdoorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/internal/test/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
