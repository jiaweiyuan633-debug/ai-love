package com.ailove.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 限流过滤器注册：默认开启，可用 RATE_LIMIT_ENABLED=false 关闭（本地压测时）。
 * 在 JwtAuthFilter（order 1）之后执行，AI 端点才能按 userId 限流。
 */
@Configuration
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            @Value("${app.rate-limit.auth-per-minute:10}") int authPerMinute,
            @Value("${app.rate-limit.ai-per-minute:30}") int aiPerMinute) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(authPerMinute, aiPerMinute));
        registration.addUrlPatterns("/auth/*", "/ai/*", "/api/*");
        registration.setOrder(2);
        return registration;
    }
}
