package com.ailove.unit;

import java.util.UUID;

import com.ailove.common.RedisRateLimitStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 真实 Redis 交互测试：本地/CI 运行了 Redis（127.0.0.1:6379）才执行，否则自动跳过。
 * 本地启用：docker run --rm -d -p 6379:6379 redis
 */
class RedisRateLimitStoreIT {

    private static LettuceConnectionFactory factory;
    private static RedisRateLimitStore store;

    @BeforeAll
    static void requireLocalRedis() {
        factory = new LettuceConnectionFactory("127.0.0.1", 6379);
        factory.afterPropertiesSet();
        boolean available;
        try {
            try (var conn = factory.getConnection()) {
                available = "PONG".equalsIgnoreCase(conn.ping());
            }
        } catch (Exception e) {
            available = false;
        }
        assumeTrue(available, "本地未运行 Redis，跳过（docker run --rm -p 6379:6379 redis 可启用）");
        store = new RedisRateLimitStore(new StringRedisTemplate(factory));
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void 固定窗口超限拒绝_窗口过期后恢复() throws InterruptedException {
        String key = "it:" + UUID.randomUUID();
        assertTrue(store.tryAcquire(key, 2, 700));
        assertTrue(store.tryAcquire(key, 2, 700));
        assertFalse(store.tryAcquire(key, 2, 700));
        Thread.sleep(900);
        assertTrue(store.tryAcquire(key, 2, 700));
    }

    @Test
    void 不同键互不影响() {
        String prefix = "it:" + UUID.randomUUID();
        assertTrue(store.tryAcquire(prefix + ":a", 1, 60_000));
        assertFalse(store.tryAcquire(prefix + ":a", 1, 60_000));
        assertTrue(store.tryAcquire(prefix + ":b", 1, 60_000));
    }
}
