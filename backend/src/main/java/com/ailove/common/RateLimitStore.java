package com.ailove.common;

/**
 * 限流计数存储抽象：tryAcquire 记录一次命中并判断是否放行。
 * 默认 {@link InMemoryRateLimitStore}（单实例滑窗）；
 * 配置 app.rate-limit.backend=redis 时切换 {@link RedisRateLimitStore}（多实例共享计数）。
 */
public interface RateLimitStore {

    /** 记录一次命中；返回 true = 放行，false = 超限拒绝。 */
    boolean tryAcquire(String key, int limit, long windowMillis);
}
