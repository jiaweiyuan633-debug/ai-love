package com.ailove.care;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ailove.persona.PersonaCatalog;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 主动关怀：角色在合适时机"主动"给用户发一条消息（进入聊天页时展示为角色来信）。
 * 触发规则：超过 12 小时没聊天 / 当天早晨首次来访 / 深夜来访。
 * 按 (用户, 日期, 类型) 内存去重，每个时机一天最多出现一次；生成结果复用。
 */
@Service
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class CareService {

    /** type: away=好久不见 / morning=早安 / night=晚安 */
    public record CareMessage(String type, String content) {
    }

    private final JdbcClient jdbc;
    private final ChatClient chatClient;
    private final Map<String, CareMessage> shownToday = new ConcurrentHashMap<>();

    public CareService(JdbcClient jdbc, ChatClient.Builder builder) {
        this.jdbc = jdbc;
        this.chatClient = builder
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-turbo").temperature(0.9).build())
                .build();
    }

    /** 返回当前应展示的关怀消息；不需要时返回 null。 */
    public CareMessage pending(long userId, String personaId) {
        PersonaCatalog.Persona persona = PersonaCatalog.find(personaId);
        String type = detectType(userId);
        if (type == null) {
            return null;
        }
        String key = userId + "|" + persona.id() + "|" + type + "|" + LocalDate.now();
        return shownToday.computeIfAbsent(key, k -> generate(persona, type, userId));
    }

    private String detectType(long userId) {
        LocalTime now = LocalTime.now();
        if (now.getHour() >= 23 || now.getHour() < 5) {
            return "night";
        }
        Double hours = jdbc.sql("""
                SELECT EXTRACT(EPOCH FROM (now() - MAX(m.created_at))) / 3600
                FROM messages m JOIN conversations c ON c.id = m.conversation_id
                WHERE c.user_id = ?
                """)
                .param(userId)
                .query(Double.class)
                .optional()
                .orElse(null);
        if (hours == null) {
            return null; // 还没有任何对话，先不"主动"
        }
        if (hours >= 12) {
            return "away";
        }
        if (now.getHour() >= 5 && now.getHour() < 11) {
            return "morning";
        }
        return null;
    }

    private CareMessage generate(PersonaCatalog.Persona persona, String type, long userId) {
        List<String> memories = jdbc.sql(
                        "SELECT content FROM user_memories WHERE user_id = ? ORDER BY id DESC LIMIT 5")
                .param(userId)
                .query(String.class)
                .list();
        String memoryHint = memories.isEmpty() ? "（还不太了解用户）" : String.join("；", memories);
        String scene = switch (type) {
            case "away" -> "用户超过半天没来找你了，你想TA了，主动发一条消息关心一下";
            case "morning" -> "现在是早晨，你向刚上线的用户道一声早安，顺便提一句今天的祝福";
            default -> "深夜了，用户还在线，你温柔地提醒TA早点休息";
        };
        String content;
        try {
            String answer = chatClient.prompt()
                    .user("""
                            你是「%s」（%s）。%s。
                            可参考对用户的了解：%s
                            要求：以你的口吻发一条消息，30~70 字，自然亲切像微信消息，可用 1 个 emoji；只输出消息正文。
                            """.formatted(persona.name(), persona.tagline(), scene, truncate(memoryHint, 300)))
                    .call()
                    .content();
            content = answer == null || answer.isBlank() ? fallback(persona, type) : answer.trim();
        } catch (Exception e) {
            content = fallback(persona, type);
        }
        return new CareMessage(type, content.length() > 280 ? content.substring(0, 280) : content);
    }

    private static String fallback(PersonaCatalog.Persona persona, String type) {
        return switch (type) {
            case "away" -> persona.emoji() + " 好久没聊了，有点想你。最近过得怎么样？";
            case "morning" -> persona.emoji() + " 早安呀~新的一天也要元气满满哦！";
            default -> persona.emoji() + " 这么晚还没睡呀？早点休息，梦里也要开心哦。";
        };
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
