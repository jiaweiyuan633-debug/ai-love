package com.ailove.common;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ailove.membership.MembershipStore;

/**
 * 限流过滤器注册：默认开启，可用 RATE_LIMIT_ENABLED=false 关闭（本地压测时）。
 * 计数后端 RATE_LIMIT_BACKEND=memory（默认，进程内）或 redis（多实例共享，需 Redis 实例）。
 * 在 JwtAuthFilter（order 1）之后执行，AI 端点才能按 userId 限流。
 */
@Configuration
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfig {

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.backend", havingValue = "redis")
    public RateLimitStore redisRateLimitStore(StringRedisTemplate redisTemplate) {
        return new RedisRateLimitStore(redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.backend", havingValue = "memory", matchIfMissing = true)
    public RateLimitStore inMemoryRateLimitStore() {
        return new InMemoryRateLimitStore();
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimitStore store,
            ObjectProvider<MembershipStore> membershipStore,
            @Value("${app.rate-limit.auth-per-minute:10}") int authPerMinute,
            @Value("${app.rate-limit.ai-per-minute:30}") int aiPerMinute) {
        MembershipStore membership = membershipStore.getIfAvailable();
        RateLimitFilter filter = new RateLimitFilter(store, authPerMinute, aiPerMinute,
                membership != null ? membership::isVip : userId -> false);
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/auth/*", "/ai/*", "/api/*");
        registration.setOrder(2);
        return registration;
    }
}
