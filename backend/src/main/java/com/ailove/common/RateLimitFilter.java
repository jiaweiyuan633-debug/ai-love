package com.ailove.common;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.ailove.auth.AuthContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 内存滑动窗口限流：登录/注册按客户端 IP 防爆破；AI 消耗端点（/ai/*、TTS、追问建议）
 * 按用户防刷——每次调用都消耗 DashScope 费用，无登录（体验模式）时退化为按 IP。
 * 单实例/少量实例下够用；多实例部署时各实例独立计数（见 docs/LAUNCH.md）。
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;
    /** 键数量硬上限：防止伪造 X-Forwarded-For 刷出大量键撑爆内存 */
    private static final int MAX_KEYS = 200_000;

    private final int authPerMinute;
    private final int aiPerMinute;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final AtomicLong inserts = new AtomicLong();

    public RateLimitFilter(int authPerMinute, int aiPerMinute) {
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
        if (allow(limitKey(request, limit), limit)) {
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

    private boolean allow(String key, int limit) {
        evictIfNeeded();
        long now = System.currentTimeMillis();
        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    /** 周期性清理已过期的键，内存占用稳定在活跃键数量级。 */
    private void evictIfNeeded() {
        if (inserts.incrementAndGet() % 4096 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        hits.entrySet().removeIf(e -> {
            Deque<Long> window = e.getValue();
            synchronized (window) {
                while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MS) {
                    window.pollFirst();
                }
                return window.isEmpty();
            }
        });
        if (hits.size() > MAX_KEYS) {
            hits.clear();
        }
    }
}
