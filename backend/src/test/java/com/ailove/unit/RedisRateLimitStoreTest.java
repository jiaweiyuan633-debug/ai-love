package com.ailove.unit;

import com.ailove.common.RedisRateLimitStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis 限流存储单元测试：固定窗口判定与 Redis 故障时的 fail-open 语义。
 * 真实 Redis 交互见 {@link RedisRateLimitStoreIT}（本地有 Redis 时运行）。
 */
class RedisRateLimitStoreTest {

    @Test
    @SuppressWarnings("unchecked")
    void 计数超过上限应拒绝() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString())).thenReturn(31L);
        RedisRateLimitStore store = new RedisRateLimitStore(redis);
        assertFalse(store.tryAcquire("ai:u1", 30, 60_000));
        assertTrue(store.tryAcquire("ai:u1", 31, 60_000));
    }

    @Test
    @SuppressWarnings("unchecked")
    void Redis不可用时放行_failOpen() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("connection refused"));
        RedisRateLimitStore store = new RedisRateLimitStore(redis);
        assertTrue(store.tryAcquire("ai:u1", 1, 60_000));
    }
}
