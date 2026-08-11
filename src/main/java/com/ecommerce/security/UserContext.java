package com.ecommerce.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserContext {
    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private Long userId;
    private String role;
    private String jti;

    public static void set(UserContext ctx) { HOLDER.set(ctx); }
    public static UserContext get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }
    public static Long getUserId() { return get() != null ? get().userId : null; }
    public static String getRole() { return get() != null ? get().role : null; }
    public static boolean isAdmin() { return "ADMIN".equals(getRole()); }
}
