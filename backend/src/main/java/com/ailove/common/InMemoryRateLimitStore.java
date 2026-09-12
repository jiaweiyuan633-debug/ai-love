package com.ailove.common;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内滑动窗口存储：单实例/少量实例下够用，多实例部署时各实例独立计数。
 * 键数量硬上限：防止伪造 X-Forwarded-For 刷出大量键撑爆内存。
 */
public class InMemoryRateLimitStore implements RateLimitStore {

    private static final int MAX_KEYS = 200_000;
    /** 每积累 4096 次插入触发一轮过期键清理 */
    private static final int EVICT_INTERVAL = 4096;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final AtomicLong inserts = new AtomicLong();

    @Override
    public boolean tryAcquire(String key, int limit, long windowMillis) {
        evictIfNeeded(windowMillis);
        long now = System.currentTimeMillis();
        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) {
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
    private void evictIfNeeded(long windowMillis) {
        if (inserts.incrementAndGet() % EVICT_INTERVAL != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        hits.entrySet().removeIf(e -> {
            Deque<Long> window = e.getValue();
            synchronized (window) {
                while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) {
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
