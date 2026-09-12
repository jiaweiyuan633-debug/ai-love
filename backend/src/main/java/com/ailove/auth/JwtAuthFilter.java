package com.ailove.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 受保护路径（/ai/*、/api/*、/knowledge/*）的 JWT 鉴权过滤器：
 * 校验 Bearer Token，失败统一返回 401 JSON。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            JwtUtil.UserId user = jwtUtil.parse(auth.substring(7));
            if (user != null) {
                AuthContext.set(user.id(), user.username());
                try {
                    chain.doFilter(request, response);
                } finally {
                    AuthContext.clear();
                }
                return;
            }
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"未登录或登录已过期\"}");
    }
}
