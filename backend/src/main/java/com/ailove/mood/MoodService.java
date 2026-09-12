package com.ailove.mood;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ailove.persona.PersonaCatalog;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * 心情打卡：每天记录一个 1-5 分的心情（可选附言）；打卡后角色以自己的口吻回应一句。
 * 连续打卡天数与 7 天趋势用于成就系统。
 */
@Service
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class MoodService {

    public record MoodEntry(String date, int score, String note) {
    }

    public record MoodStatus(boolean checkedToday, Integer todayScore, String note,
                             int streak, List<MoodEntry> recent7) {
    }

    private final JdbcClient jdbc;
    private final ChatClient chatClient;

    public MoodService(JdbcClient jdbc, ChatClient.Builder builder) {
        this.jdbc = jdbc;
        this.chatClient = builder
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-turbo").temperature(0.9).build())
                .build();
    }

    public MoodStatus status(long userId) {
        LocalDate today = LocalDate.now();
        List<MoodEntry> recent = jdbc.sql(
                        "SELECT mdate, score, note FROM mood_logs WHERE user_id = ? ORDER BY mdate DESC LIMIT 7")
                .param(userId)
                .query((rs, i) -> new MoodEntry(rs.getDate("mdate").toString(),
                        rs.getInt("score"), rs.getString("note")))
                .list();
        boolean checkedToday = !recent.isEmpty() && recent.get(0).date().equals(today.toString());
        // 最近 7 天补齐（无打卡的日期 score=0），旧→新排列，前端画折线
        Map<String, MoodEntry> byDate = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            byDate.put(d.toString(), new MoodEntry(d.toString(), 0, null));
        }
        for (MoodEntry e : recent) {
            if (byDate.containsKey(e.date())) {
                byDate.put(e.date(), e);
            }
        }
        return new MoodStatus(checkedToday,
                checkedToday ? recent.get(0).score() : null,
                checkedToday ? recent.get(0).note() : null,
                streak(recent, today),
                List.copyOf(byDate.values()));
    }

    private int streak(List<MoodEntry> recentDesc, LocalDate today) {
        if (recentDesc.isEmpty()) {
            return 0;
        }
        LocalDate expected = recentDesc.get(0).date().equals(today.toString())
                ? today
                : today.minusDays(1);
        int streak = 0;
        for (MoodEntry e : recentDesc) {
            if (e.date().equals(expected.toString())) {
                streak++;
                expected = expected.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    /** 打卡（重复打卡覆盖今天的分数）；返回角色的人设回应。 */
    public String checkIn(long userId, int score, String note, String personaId) {
        if (score < 1 || score > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "心情分数需在 1-5 之间");
        }
        PersonaCatalog.Persona persona = PersonaCatalog.find(personaId);
        LocalDate today = LocalDate.now();
        String trimmedNote = note == null ? null
                : (note.trim().isEmpty() ? null : note.trim().substring(0, Math.min(200, note.trim().length())));
        jdbc.sql("""
                INSERT INTO mood_logs (user_id, score, note, mdate) VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, mdate) DO UPDATE SET score = EXCLUDED.score, note = EXCLUDED.note
                """)
                .param(userId)
                .param(score)
                .param(trimmedNote)
                .param(today)
                .update();
        return personaReply(userId, persona, score, trimmedNote);
    }

    private String personaReply(long userId, PersonaCatalog.Persona persona, int score, String note) {
        String moodWord = switch (score) {
            case 5 -> "非常开心";
            case 4 -> "还不错";
            case 3 -> "一般般";
            case 2 -> "有点低落";
            default -> "很难受";
        };
        try {
            String answer = chatClient.prompt()
                    .user("""
                            你是「%s」（%s）。用户刚刚完成了今日心情打卡：心情%s（%d/5）%s。
                            以你的口吻回应一句，30~60 字，像关心朋友的微信消息，可用 1 个 emoji；只输出正文。
                            """.formatted(persona.name(), persona.tagline(), moodWord, score,
                            note == null ? "" : "，附言：" + note))
                    .call()
                    .content();
            if (answer != null && !answer.isBlank()) {
                String clean = answer.trim();
                return clean.length() > 280 ? clean.substring(0, 280) : clean;
            }
        } catch (Exception ignored) {
            // 生成失败走兜底
        }
        return switch (score) {
            case 5, 4 -> persona.emoji() + " 看到你心情不错，我也跟着开心！保持住哦～";
            case 3 -> persona.emoji() + " 平平淡淡也是好日子，我一直在呢。";
            default -> persona.emoji() + " 抱抱你，想聊聊吗？我都在。";
        };
    }
}
