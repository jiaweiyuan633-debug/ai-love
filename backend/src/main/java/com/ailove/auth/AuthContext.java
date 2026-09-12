package com.ailove.auth;

/**
 * 当前请求的登录用户上下文：由 JwtAuthFilter 写入，请求结束时必须清理（避免线程复用串号）。
 */
public final class AuthContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(long userId, String username) {
        USER_ID.set(userId);
        USERNAME.set(username);
    }

    /** 当前登录用户 ID；未登录（体验模式）返回 null。 */
    public static Long userId() {
        return USER_ID.get();
    }

    public static String username() {
        return USERNAME.get();
    }

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
    }
}
