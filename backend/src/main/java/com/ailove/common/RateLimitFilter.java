package com.ailove.common;

import java.io.IOException;

import com.ailove.auth.AuthContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 限流过滤器：登录/注册按客户端 IP 防爆破；AI 消耗端点（/ai/*、TTS、追问建议）
 * 按用户防刷——每次调用都消耗 DashScope 费用，无登录（体验模式）时退化为按 IP。
 * 计数存储由 {@link RateLimitStore} 提供：默认进程内存，可切换 Redis 实现多实例共享。
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;

    private final RateLimitStore store;
    private final int authPerMinute;
    private final int aiPerMinute;

    public RateLimitFilter(RateLimitStore store, int authPerMinute, int aiPerMinute) {
        this.store = store;
        this.authPerMinute = authPerMinute;
        this.aiPerMinute = aiPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Integer limit = limitFor(request);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }
        if (store.tryAcquire(limitKey(request, limit), limit, WINDOW_MS)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"请求太频繁，请喝口水休息一下，稍后再试\"}");
    }

    /** 该请求命中的限流额度：null = 不限流。 */
    private Integer limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/auth/login") || path.equals("/auth/register")) {
            return authPerMinute;
        }
        // AI 消耗端点：对话/智能体（含 SSE）、语音合成、追问建议
        if (path.startsWith("/ai/")
                || (path.equals("/api/tts") && "POST".equalsIgnoreCase(request.getMethod()))
                || (path.endsWith("/suggestions") && "POST".equalsIgnoreCase(request.getMethod()))) {
            return aiPerMinute;
        }
        return null;
    }

    private String limitKey(HttpServletRequest request, int limit) {
        if (limit == aiPerMinute) {
            Long userId = AuthContext.userId();
            if (userId != null) {
                return "ai:u" + userId;
            }
            return "ai:ip:" + clientIp(request);
        }
        return "auth:" + clientIp(request);
    }

    /** 客户端 IP：优先可信网关（nginx/FC）写入的头；X-Forwarded-For 取最后一段（客户端伪造的前缀段不可信）。 */
    private String clientIp(HttpServletRequest request) {
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int idx = xff.lastIndexOf(',');
            return (idx >= 0 ? xff.substring(idx + 1) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}
