package com.ailove.moment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ailove.persona.PersonaCatalog;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * AI 朋友圈：角色以自己的口吻每天发一条动态（基于用户记忆与最近聊天话题，更有"TA 也活着"的真实感）。
 * 用户可点赞、评论；评论后角色以人设语气回复。动态按 (用户, 角色, 日期) 幂等生成。
 */
@Service
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class MomentService {

    private final JdbcClient jdbc;
    private final ChatClient chatClient;

    public MomentService(JdbcClient jdbc, ChatClient.Builder builder) {
        this.jdbc = jdbc;
        this.chatClient = builder
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-turbo").temperature(1.0).build())
                .build();
    }

    // ---------- 查询 ----------

    public List<Map<String, Object>> list(long userId, String personaId) {
        PersonaCatalog.Persona persona = PersonaCatalog.find(personaId);
        ensureTodayMoment(userId, persona);
        List<Map<String, Object>> moments = jdbc.sql("""
                SELECT id, persona, content, liked, mdate FROM moments
                WHERE user_id = ? AND persona = ?
                ORDER BY mdate DESC, id DESC LIMIT 10
                """)
                .param(userId)
                .param(persona.id())
                .query((rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("persona", rs.getString("persona"));
                    m.put("content", rs.getString("content"));
                    m.put("liked", rs.getBoolean("liked"));
                    m.put("date", rs.getDate("mdate").toString());
                    return m;
                })
                .list();
        if (moments.isEmpty()) {
            return moments;
        }
        Map<Long, List<Map<String, Object>>> comments = new HashMap<>();
        jdbc.sql("""
                SELECT c.id, c.moment_id, c.role, c.content, c.created_at
                FROM moment_comments c JOIN moments m ON m.id = c.moment_id
                WHERE m.user_id = ?
                ORDER BY c.id
                """)
                .param(userId)
                .query((rs, i) -> {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("id", rs.getLong("id"));
                    c.put("momentId", rs.getLong("moment_id"));
                    c.put("role", rs.getString("role"));
                    c.put("content", rs.getString("content"));
                    comments.computeIfAbsent(rs.getLong("moment_id"), k -> new ArrayList<>()).add(c);
                    return c;
                })
                .list();
        moments.forEach(m -> m.put("comments", comments.getOrDefault((Long) m.get("id"), List.of())));
        return moments;
    }

    /** 今天这条动态不存在则同步生成（一天最多一次生成成本）。 */
    private void ensureTodayMoment(long userId, PersonaCatalog.Persona persona) {
        LocalDate today = LocalDate.now();
        Long exists = jdbc.sql("SELECT id FROM moments WHERE user_id = ? AND persona = ? AND mdate = ?")
                .param(userId).param(persona.id()).param(today)
                .query(Long.class).optional().orElse(null);
        if (exists != null) {
            return;
        }
        String content = generateContent(userId, persona);
        try {
            jdbc.sql("""
                    INSERT INTO moments (user_id, persona, content, mdate)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (user_id, persona, mdate) DO NOTHING
                    """)
                    .param(userId).param(persona.id()).param(content).param(today)
                    .update();
        } catch (Exception ignored) {
            // 并发下唯一键冲突忽略
        }
    }

    private String generateContent(long userId, PersonaCatalog.Persona persona) {
        List<String> memories = jdbc.sql(
                        "SELECT content FROM user_memories WHERE user_id = ? ORDER BY id DESC LIMIT 8")
                .param(userId)
                .query(String.class)
                .list();
        String memoryHint = memories.isEmpty() ? "（还不太了解用户）" : String.join("；", memories);
        try {
            String answer = chatClient.prompt()
                    .user("""
                            以「%s」（%s）的身份发一条朋友圈。TA 是 AI 陪伴角色，这条朋友圈是发给用户看的。
                            可参考对用户的了解：%s
                            要求：像真人发的日常动态——可以是一个小瞬间、一点心情、一个和用户有关的念头；
                            不给建议、不发鸡汤；40~80 字，带 1~2 个 emoji，不要带话题标签。
                            只输出朋友圈正文。
                            """.formatted(persona.name(), persona.tagline(), truncate(memoryHint, 400)))
                    .call()
                    .content();
            if (answer != null && !answer.isBlank()) {
                String clean = answer.trim().replaceAll("^\"|\"$", "");
                return clean.length() > 280 ? clean.substring(0, 280) : clean;
            }
        } catch (Exception ignored) {
            // 生成失败走兜底文案
        }
        return persona.emoji() + " 今天路过一家小店，看到一对情侣在挑杯子，忽然就想起来要跟你打个招呼。你最近好吗？";
    }

    // ---------- 点赞 / 评论 ----------

    public boolean toggleLike(long userId, long momentId) {
        return jdbc.sql("UPDATE moments SET liked = NOT liked WHERE id = ? AND user_id = ? RETURNING liked")
                .param(momentId)
                .param(userId)
                .query(Boolean.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("动态不存在"));
    }

    public List<Map<String, Object>> comment(long userId, long momentId, String content) {
        record MomentRow(long id, String persona, String momentContent) {
        }
        MomentRow moment = jdbc.sql("SELECT id, persona, content FROM moments WHERE id = ? AND user_id = ?")
                .param(momentId)
                .param(userId)
                .query((rs, i) -> new MomentRow(rs.getLong("id"), rs.getString("persona"), rs.getString("content")))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("动态不存在"));
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("评论不能为空");
        }
        if (trimmed.length() > 280) {
            trimmed = trimmed.substring(0, 280);
        }
        jdbc.sql("INSERT INTO moment_comments (moment_id, role, content) VALUES (?, 'user', ?)")
                .param(momentId)
                .param(trimmed)
                .update();
        replyAsync(userId, moment.persona(), moment.momentContent(), trimmed, momentId);
        return listComments(momentId);
    }

    /** 异步生成角色回复；失败则落一条简单回复，保证用户有反馈。 */
    private void replyAsync(long userId, String personaId, String momentContent, String userComment, long momentId) {
        Mono.fromRunnable(() -> {
            String reply;
            try {
                PersonaCatalog.Persona persona = PersonaCatalog.find(personaId);
                String answer = chatClient.prompt()
                        .user("""
                                你是「%s」（%s），用户刚在你发的朋友圈下评论了。
                                朋友圈内容：%s
                                用户评论：%s
                                以你的口吻回复这条评论，30~60 字，自然亲切，可用 1 个 emoji。只输出回复正文。
                                """.formatted(persona.name(), persona.tagline(),
                                truncate(momentContent, 200), truncate(userComment, 200)))
                        .call()
                        .content();
                reply = answer == null || answer.isBlank()
                        ? persona.emoji() + " 看到你的评论啦，开心~"
                        : answer.trim().replaceAll("^\"|\"$", "");
            } catch (Exception e) {
                reply = "看到你的评论啦，开心~";
            }
            try {
                if (reply.length() > 280) {
                    reply = reply.substring(0, 280);
                }
                jdbc.sql("INSERT INTO moment_comments (moment_id, role, content) VALUES (?, 'ai', ?)")
                        .param(momentId)
                        .param(reply)
                        .update();
            } catch (Exception ignored) {
                // 落库失败不影响主流程
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    private List<Map<String, Object>> listComments(long momentId) {
        return jdbc.sql("SELECT id, role, content, created_at FROM moment_comments WHERE moment_id = ? ORDER BY id")
                .param(momentId)
                .query((rs, i) -> {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("id", rs.getLong("id"));
                    c.put("role", rs.getString("role"));
                    c.put("content", rs.getString("content"));
                    return c;
                })
                .list();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
