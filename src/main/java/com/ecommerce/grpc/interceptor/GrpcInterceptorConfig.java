package com.ecommerce.grpc.interceptor;

import com.ecommerce.security.JwtUtil;
import com.ecommerce.security.UserAuthMapper;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class GrpcInterceptorConfig {

    /**
     * @GrpcGlobalServerInterceptor 标注在 @Bean 方法上（不是类上），
     * net.devh starter 会自动将此 Bean 注册为全局拦截器，应用到所有 gRPC 服务。
     */
    @GrpcGlobalServerInterceptor
    @Bean
    public GrpcAuthInterceptor grpcAuthInterceptor(JwtUtil jwtUtil,
                                                    StringRedisTemplate redisTemplate,
                                                    UserAuthMapper userAuthMapper) {
        return new GrpcAuthInterceptor(jwtUtil, redisTemplate, userAuthMapper);
    }
}
