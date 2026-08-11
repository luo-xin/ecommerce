package com.ecommerce.security;

import com.ecommerce.common.ErrorCode;
import com.ecommerce.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final UserAuthMapper userAuthMapper;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(req);
        if (token == null) {
            chain.doFilter(req, res);
            return;
        }
        try {
            Claims claims = jwtUtil.parseToken(token);
            String jti = claims.getId();

            if (Boolean.TRUE.equals(redisTemplate.hasKey("token:blacklist:" + jti))) {
                sendError(res, ErrorCode.TOKEN_EXPIRED);
                return;
            }

            Long userId = claims.get("userId", Long.class);
            String role = claims.get("role", String.class);
            Integer tokenPV = claims.get("passwordVersion", Integer.class);

            Integer dbPV = userAuthMapper.selectPasswordVersion(userId);
            if (dbPV == null || !dbPV.equals(tokenPV)) {
                sendError(res, ErrorCode.TOKEN_EXPIRED);
                return;
            }

            UserContext.set(new UserContext(userId, role, jti));
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null,
                    List.of(new SimpleGrantedAuthority(role))));

            chain.doFilter(req, res);
        } catch (JwtException e) {
            sendError(res, ErrorCode.TOKEN_EXPIRED);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String extractToken(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        return (StringUtils.hasText(h) && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }

    private void sendError(HttpServletResponse res, ErrorCode ec) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(objectMapper.writeValueAsString(
                Result.error(ec.getCode(), ec.getMessage())));
    }
}
