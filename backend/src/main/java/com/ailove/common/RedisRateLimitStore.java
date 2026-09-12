package com.ailove.common;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis 固定窗口存储：app.rate-limit.backend=redis 时启用，多实例共享计数
 * （需配合 Redis 实例与 spring.data.redis 连接配置，见 docs/DEPLOY.md）。
 * Redis 不可用时放行（fail-open）——限流是防刷手段而非安全边界，可用性优先。
 */
public class RedisRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);

    /** INCR + 首次 PEXPIRE 的原子固定窗口 */
    private static final DefaultRedisScript<Long> WINDOW_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c", Long.class);

    private static final String KEY_PREFIX = "ailove:rl:";
    /** 故障告警最小间隔（毫秒），避免 Redis 宕机时每条请求刷日志 */
    private static final long WARN_INTERVAL_MS = 30_000L;

    private final StringRedisTemplate redis;
    private final AtomicLong lastWarnAt = new AtomicLong();

    public RedisRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key, int limit, long windowMillis) {
        try {
            Long count = redis.execute(WINDOW_SCRIPT, List.of(KEY_PREFIX + key), String.valueOf(windowMillis));
            return count != null && count <= limit;
        } catch (DataAccessException e) {
            long now = System.currentTimeMillis();
            long prev = lastWarnAt.get();
            if (now - prev > WARN_INTERVAL_MS && lastWarnAt.compareAndSet(prev, now)) {
                log.warn("Redis 限流存储不可用，已降级为放行（fail-open）：{}", e.getMessage());
            }
            return true;
        }
    }
}
