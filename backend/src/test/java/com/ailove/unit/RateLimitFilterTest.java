package com.ailove.unit;

import java.io.IOException;

import com.ailove.auth.AuthContext;
import com.ailove.common.RateLimitFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 限流过滤器单元测试：登录防爆破按 IP、AI 端点按 userId、不同键互不影响、放行路径不限流。
 */
class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(2, 3);

    @AfterEach
    void cleanContext() {
        AuthContext.clear();
    }

    private int run(String method, String path, String xff) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("203.0.113.7");
        if (xff != null) {
            request.addHeader("X-Forwarded-For", xff);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(200);
        ((OncePerRequestFilter) filter).doFilter(request, response, chain);
        return response.getStatus();
    }

    @Test
    void 登录接口超限应429() throws Exception {
        assertEquals(200, run("POST", "/auth/login", null));
        assertEquals(200, run("POST", "/auth/login", null));
        assertEquals(429, run("POST", "/auth/login", null));
    }

    @Test
    void 不同IP互不影响() throws Exception {
        assertEquals(200, run("POST", "/auth/register", "198.51.100.1"));
        assertEquals(200, run("POST", "/auth/register", "198.51.100.2"));
        assertEquals(200, run("POST", "/auth/register", "198.51.100.1"));
        // 第三个 IP 首次访问放行；伪造的 XFF 前缀段不影响（取最后一段）
        assertEquals(200, run("POST", "/auth/register", "fakeip, 198.51.100.3"));
    }

    @Test
    void AI端点按用户限流_登录与AI额度隔离() throws Exception {
        AuthContext.set(42L, "user42");
        assertEquals(200, run("GET", "/ai/love_chat/stream", null));
        assertEquals(200, run("GET", "/ai/love_chat/stream", null));
        assertEquals(200, run("GET", "/ai/love_chat/stream", null));
        assertEquals(429, run("GET", "/ai/love_chat/stream", null));
        // 登录额度独立，未受 AI 额度耗尽影响（该 IP 首次登录）
        assertEquals(200, run("POST", "/auth/login", null));
    }

    @Test
    void 体验模式下AI端点按IP限流() throws Exception {
        // 未登录（AuthContext 为空）：AI 端点退化为按 IP 计数（与按用户的键互不影响）
        assertEquals(200, run("POST", "/api/tts", null));
        assertEquals(200, run("POST", "/api/tts", null));
        assertEquals(200, run("POST", "/api/tts", null));
        assertEquals(429, run("POST", "/api/tts", null));
    }

    @Test
    void 非消耗端点不限流() throws Exception {
        for (int i = 0; i < 6; i++) {
            assertEquals(200, run("GET", "/api/moments", null));
            assertEquals(200, run("GET", "/auth/status", null));
        }
    }
}
