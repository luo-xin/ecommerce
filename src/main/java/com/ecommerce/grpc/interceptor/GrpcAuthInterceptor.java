package com.ecommerce.grpc.interceptor;

import com.ecommerce.security.JwtUtil;
import com.ecommerce.security.UserAuthMapper;
import com.ecommerce.security.UserContext;
import io.grpc.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 全局 gRPC 鉴权拦截器：
 * - 白名单方法直接放行
 * - 其余方法从 Metadata 读取 "authorization: Bearer <token>"
 * - 复用 JwtUtil 解析 Token，检查 Redis 黑名单，验证 passwordVersion
 * - 通过则写入 UserContext；失败则返回 UNAUTHENTICATED
 *
 * 注意：不加 @Component，通过 GrpcInterceptorConfig 的 @Bean 方法注册，
 * @GrpcGlobalServerInterceptor 标注在 @Bean 方法上（net.devh starter 要求）。
 */
@RequiredArgsConstructor
public class GrpcAuthInterceptor implements ServerInterceptor {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final UserAuthMapper userAuthMapper;

    /** 无需鉴权的 gRPC 方法全名（package.Service/Method） */
    private static final Set<String> PUBLIC_METHODS = Set.of(
            "ecommerce.ProductService/ListProducts",
            "ecommerce.ProductService/GetProduct",
            "ecommerce.ProductService/ListCategories",
            "ecommerce.UserService/Register",
            "ecommerce.UserService/Login",
            "ecommerce.InventoryService/GetStock"
    );

    /** 以此前缀开头的方法直接放行（gRPC 内置服务：反射、健康检查） */
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "grpc.reflection.",
            "grpc.health."
    );

    private static final Metadata.Key<String> AUTH_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String fullMethodName = call.getMethodDescriptor().getFullMethodName();

        // 白名单（精确匹配）：直接放行，不做任何鉴权
        if (PUBLIC_METHODS.contains(fullMethodName)) {
            return next.startCall(call, headers);
        }

        // 白名单（前缀匹配）：gRPC 内置服务（反射、健康检查）直接放行
        for (String prefix : PUBLIC_PREFIXES) {
            if (fullMethodName.startsWith(prefix)) {
                return next.startCall(call, headers);
            }
        }

        // 读取 Authorization header
        String authHeader = headers.get(AUTH_KEY);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            call.close(Status.UNAUTHENTICATED.withDescription("缺少 Authorization Token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtUtil.parseToken(token);
            String jti = claims.getId();

            // 检查 Redis 黑名单（退出登录后的 token 立即失效）
            if (Boolean.TRUE.equals(redisTemplate.hasKey("token:blacklist:" + jti))) {
                call.close(Status.UNAUTHENTICATED.withDescription("Token 已失效（已退出登录）"), new Metadata());
                return new ServerCall.Listener<>() {};
            }

            Long userId = claims.get("userId", Long.class);
            String role = claims.get("role", String.class);
            Integer tokenPV = claims.get("passwordVersion", Integer.class);

            // 验证 passwordVersion（改密后旧 token 立即失效）
            Integer dbPV = userAuthMapper.selectPasswordVersion(userId);
            if (dbPV == null || !dbPV.equals(tokenPV)) {
                call.close(Status.UNAUTHENTICATED.withDescription("Token 已失效（密码已修改）"), new Metadata());
                return new ServerCall.Listener<>() {};
            }

            // 设置 UserContext，并在调用结束时清除（ThreadLocal 清理）
            UserContext.set(new UserContext(userId, role, jti));
            ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
                @Override
                public void onComplete() {
                    UserContext.clear();
                    super.onComplete();
                }

                @Override
                public void onCancel() {
                    UserContext.clear();
                    super.onCancel();
                }
            };

        } catch (JwtException e) {
            call.close(Status.UNAUTHENTICATED.withDescription("Token 无效或已过期"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
    }
}
