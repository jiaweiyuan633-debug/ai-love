package com.ailove.common;

import java.io.IOException;
import java.util.function.LongPredicate;

import com.ailove.auth.AuthContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 限流过滤器：登录/注册按客户端 IP 防爆破；AI 消耗端点（/ai/*、TTS、追问建议）
 * 按用户防刷——每次调用都消耗 DashScope 费用，无登录（体验模式）时退化为按 IP。
 * VIP 用户的 AI 端点额度翻倍（会员权益之一）。
 * 计数存储由 {@link RateLimitStore} 提供：默认进程内存，可切换 Redis 实现多实例共享。
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;

    private final RateLimitStore store;
    private final int authPerMinute;
    private final int aiPerMinute;
    /** VIP 判定（来自 MembershipStore）；无会员模块时恒为 false */
    private final LongPredicate vipUsers;

    public RateLimitFilter(RateLimitStore store, int authPerMinute, int aiPerMinute) {
        this(store, authPerMinute, aiPerMinute, userId -> false);
    }

    public RateLimitFilter(RateLimitStore store, int authPerMinute, int aiPerMinute, LongPredicate vipUsers) {
        this.store = store;
        this.authPerMinute = authPerMinute;
        this.aiPerMinute = aiPerMinute;
        this.vipUsers = vipUsers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        Long userId = AuthContext.userId();
        Integer limit = null;
        String key = null;
        if (path.equals("/auth/login") || path.equals("/auth/register")) {
            limit = authPerMinute;
            key = "auth:" + clientIp(request);
        } else if (AiEndpoints.isAiConsumer(request)) {
            // VIP 用户 AI 端点额度翻倍
            limit = userId != null && vipUsers.test(userId) ? aiPerMinute * 2 : aiPerMinute;
            key = userId != null ? "ai:u" + userId : "ai:ip:" + clientIp(request);
        }
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }
        if (store.tryAcquire(key, limit, WINDOW_MS)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"请求太频繁，请喝口水休息一下，稍后再试\"}");
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
