package com.ailove.membership;

import java.io.IOException;

import com.ailove.auth.AuthContext;
import com.ailove.common.AiEndpoints;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 免费用户每日 AI 额度：AI 消耗端点 + 已登录 + 非 VIP 时计数校验，
 * 超额返回 429（前端 SSE 错误通道会展示文案）。VIP 不限每日额度。
 * 注册在限流（order 2）之后，order 3。
 */
public class DailyQuotaFilter extends OncePerRequestFilter {

    private final MembershipStore store;
    private final int dailyFreeLimit;

    public DailyQuotaFilter(MembershipStore store, int dailyFreeLimit) {
        this.store = store;
        this.dailyFreeLimit = dailyFreeLimit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!AiEndpoints.isAiConsumer(request)) {
            chain.doFilter(request, response);
            return;
        }
        Long userId = AuthContext.userId();
        // 体验模式无登录（userId=null）与 VIP 用户不受每日额度限制
        if (userId == null || store.isVip(userId)) {
            chain.doFilter(request, response);
            return;
        }
        int used = store.recordAiUsage(userId);
        if (used > dailyFreeLimit) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"今日免费额度（" + dailyFreeLimit
                    + " 条）已用完，升级 VIP 畅聊无限次～\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
