package com.ailove.unit;

import java.io.IOException;

import com.ailove.auth.AuthContext;
import com.ailove.membership.DailyQuotaFilter;
import com.ailove.membership.MembershipStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 每日额度过滤器单元测试：AI 端点 + 已登录 + 非 VIP 才计数；
 * 超额 429 且不透传；VIP/未登录/非 AI 端点不计数。
 */
class DailyQuotaFilterTest {

    private final MembershipStore store = mock(MembershipStore.class);
    private final DailyQuotaFilter filter = new DailyQuotaFilter(store, 2);

    private final FilterChain chain = (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(200);

    private int run(String method, String path, Long userId, boolean vip) throws ServletException, IOException {
        if (userId != null) {
            AuthContext.set(userId, "user");
        }
        when(store.isVip(userId == null ? -1L : userId)).thenReturn(vip);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ((OncePerRequestFilter) filter).doFilter(request, response, chain);
        return response.getStatus();
    }

    @AfterEach
    void cleanContext() {
        AuthContext.clear();
    }

    @Test
    void 未登录与非AI端点不计数直接放行() throws Exception {
        assertEquals(200, run("POST", "/ai/love_chat/stream", null, false));
        verify(store, never()).recordAiUsage(-1L);
        assertEquals(200, run("GET", "/api/moments", 7L, false));
        verify(store, never()).recordAiUsage(7L);
    }

    @Test
    void VIP不受每日额度限制且不计数() throws Exception {
        assertEquals(200, run("GET", "/ai/love_chat/stream", 7L, true));
        verify(store, never()).recordAiUsage(7L);
    }

    @Test
    void 免费用户超过每日额度返回429并拦截请求() throws Exception {
        when(store.recordAiUsage(9L)).thenReturn(1, 2, 3);
        assertEquals(200, run("POST", "/api/tts", 9L, false));
        assertEquals(200, run("POST", "/api/tts", 9L, false));
        assertEquals(429, run("POST", "/api/tts", 9L, false));
    }

    @Test
    void 拒绝响应包含升级提示文案() throws Exception {
        AuthContext.set(9L, "user");
        when(store.isVip(9L)).thenReturn(false);
        when(store.recordAiUsage(9L)).thenReturn(21);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ai/love_chat/stream");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain passthrough = (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(200);
        ((OncePerRequestFilter) filter).doFilter(request, response, passthrough);
        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("升级 VIP"));
        assertTrue(response.getContentAsString().contains("免费额度"));
    }
}
