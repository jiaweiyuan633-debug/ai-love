package com.ailove.it;

import java.util.Map;

import com.ailove.support.BaseIT;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 心情打卡（覆盖式打卡/streak）与成就徽章（实时计算、mood_1 解锁）。
 * AI 回复由打桩客户端返回空 → 走 Service 兜底文案，断言回复非空即可。
 */
class MoodAchievementIT extends BaseIT {

    @Test
    void 打卡_重复打卡覆盖_状态与连击正确() throws Exception {
        String token = newUser();

        var first = post("/api/mood?persona=jiejie", Map.of("score", 4, "note", "今天状态不错"), token);
        assertFalse(first.path("reply").asText().isBlank(), "打卡应有人设回应（兜底文案）");

        var status = get("/api/mood", token);
        assertTrue(status.path("checkedToday").asBoolean());
        assertEquals(4, status.path("todayScore").asInt());
        assertTrue(status.path("streak").asInt() >= 1);
        assertEquals(7, status.path("recent7").size(), "趋势图固定补齐 7 天");

        // 当日重复打卡：覆盖分数
        post("/api/mood?persona=ceo", Map.of("score", 5), token);
        assertEquals(5, get("/api/mood", token).path("todayScore").asInt());
    }

    @Test
    void 非法分数应被拒绝() throws Exception {
        String token = newUser();
        assertEquals(400, raw("POST", "/api/mood?persona=jiejie",
                Map.of("score", 9), token).getStatusCode().value());
    }

    @Test
    void 成就接口返回12枚且打卡成就解锁() throws Exception {
        String token = newUser();
        post("/api/mood?persona=jiejie", Map.of("score", 3), token);

        var achievements = get("/api/achievements", token);
        assertEquals(12, achievements.size(), "成就总数应为 12 枚");

        boolean moodUnlocked = false;
        for (var a : achievements) {
            if ("mood_1".equals(a.path("id").asText())) {
                moodUnlocked = a.path("unlocked").asBoolean();
            }
        }
        assertTrue(moodUnlocked, "首次打卡成就应已解锁");
    }
}
