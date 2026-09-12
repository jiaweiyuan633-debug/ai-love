package com.ailove.achieve;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ailove.auth.AuthContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 成就徽章：全部规则实时计算（不落表）。
 * 覆盖对话量、心情打卡连续性、情侣绑定与在一起时长、记忆积累、多角色体验。
 */
@Service
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class AchievementService {

    public record Achievement(String id, String name, String emoji, String description,
                              boolean unlocked, double progress, String progressText) {
    }

    private final JdbcClient jdbc;

    public AchievementService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Achievement> list(long userId) {
        long turns = jdbc.sql("""
                SELECT COUNT(*) FROM messages m JOIN conversations c ON c.id = m.conversation_id
                WHERE c.user_id = ? AND m.role = 'user'
                """)
                .param(userId)
                .query(Long.class)
                .single();
        Integer moodStreak = jdbc.sql("""
                WITH days AS (
                    SELECT mdate, mdate - ROW_NUMBER() OVER (ORDER BY mdate)::int AS grp
                    FROM mood_logs WHERE user_id = ?
                )
                SELECT COUNT(*) FROM days GROUP BY grp ORDER BY COUNT(*) DESC LIMIT 1
                """)
                .param(userId)
                .query(Integer.class)
                .optional()
                .orElse(0);
        boolean checkedToday = jdbc.sql("SELECT COUNT(*) FROM mood_logs WHERE user_id = ? AND mdate = CURRENT_DATE")
                .param(userId)
                .query(Integer.class)
                .single() > 0;
        long memories = jdbc.sql("SELECT COUNT(*) FROM user_memories WHERE user_id = ?")
                .param(userId)
                .query(Long.class)
                .single();
        long distinctPersonas = jdbc.sql("SELECT COUNT(DISTINCT persona) FROM conversations WHERE user_id = ?")
                .param(userId)
                .query(Long.class)
                .single();
        record CoupleRow(Long anniversary, boolean bound) {
        }
        CoupleRow couple = jdbc.sql("""
                SELECT anniversary_date, (user_b IS NOT NULL) AS bound
                FROM couples WHERE user_a = ? OR user_b = ? LIMIT 1
                """)
                .param(userId)
                .param(userId)
                .query((rs, i) -> {
                    java.sql.Date d = rs.getDate("anniversary_date");
                    return new CoupleRow(d == null ? null : d.toLocalDate().toEpochDay(), rs.getBoolean("bound"));
                })
                .optional()
                .orElse(new CoupleRow(null, false));
        long daysTogether = couple.anniversary() == null
                ? 0
                : ChronoUnit.DAYS.between(LocalDate.ofEpochDay(couple.anniversary()), LocalDate.now());

        List<Achievement> list = new ArrayList<>();
        list.add(chat("first_chat", "初次相遇", "💬", "完成第一次对话", turns, 1));
        list.add(chat("chat_10", "畅聊新手", "🌱", "累计对话 10 轮", turns, 10));
        list.add(chat("chat_50", "恋爱进修生", "📚", "累计对话 50 轮", turns, 50));
        list.add(chat("chat_200", "情感大师", "🎓", "累计对话 200 轮", turns, 200));
        list.add(mood("mood_1", "心情晴雨表", "🌤️", "完成第一次心情打卡", checkedToday ? 1 : 0, 1));
        list.add(mood("mood_3", "坚持记录", "📅", "连续打卡 3 天", moodStreak, 3));
        list.add(mood("mood_7", "打卡一周", "🗓️", "连续打卡 7 天", moodStreak, 7));
        list.add(flag("couple", "双向奔赴", "💑", "与另一半完成情侣绑定", couple.bound()));
        list.add(metric("days_30", "热恋进行时", "💖", "在一起满 30 天", daysTogether, 30,
                daysTogether + " 天"));
        list.add(metric("days_365", "一周年快乐", "🎉", "在一起满 365 天", daysTogether, 365,
                daysTogether + " 天"));
        list.add(metric("memory_10", "记忆收藏家", "🧠", "AI 记住你 10 件事", memories, 10,
                (int) memories + " 条"));
        list.add(metric("persona_2", "多角色玩家", "🎭", "体验 2 位不同角色", distinctPersonas, 2,
                (int) distinctPersonas + " 位"));
        return list;
    }

    private Achievement chat(String id, String name, String emoji, String desc, long actual, long target) {
        return metric(id, name, emoji, desc, actual, target, actual + "/" + target + " 轮");
    }

    private Achievement mood(String id, String name, String emoji, String desc, long actual, long target) {
        return metric(id, name, emoji, desc, actual, target, actual + "/" + target + " 天");
    }

    private Achievement metric(String id, String name, String emoji, String desc,
                               long actual, long target, String progressText) {
        boolean unlocked = actual >= target;
        double progress = Math.min(1.0, target == 0 ? 1.0 : (double) actual / target);
        return new Achievement(id, name, emoji, desc, unlocked, progress, progressText);
    }

    private Achievement flag(String id, String name, String emoji, String desc, boolean unlocked) {
        return new Achievement(id, name, emoji, desc, unlocked, unlocked ? 1.0 : 0.0,
                unlocked ? "已达成" : "未达成");
    }

    long currentUserId() {
        Long userId = AuthContext.userId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return userId;
    }
}
