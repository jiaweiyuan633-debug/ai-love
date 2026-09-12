package com.ailove.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * 会话与消息的完整历史落库（append-only）。
 * 注意：模型上下文窗口由 HybridChatMemoryRepository 维护，两者职责分离。
 */
public class ConversationStore {

    public record Conversation(String id, long userId, String title, boolean ragEnabled,
                               String persona, String mode,
                               Instant updatedAt, int messageCount) {
    }

    /**
     * 落库消息。aiGenerated 为 AI 生成内容隐式标识（《人工智能生成合成内容标识办法》），
     * 随消息存储并透传到历史查询与 Markdown 导出。
     */
    public record StoredMessage(long id, String role, String content, boolean aiGenerated,
                                Instant createdAt) {
    }

    /** 消息全文检索命中项 */
    public record SearchHit(String conversationId, String conversationTitle, String role,
                            String snippet, Instant createdAt) {
    }

    private final JdbcClient jdbc;

    public ConversationStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- 会话 ----------

    public List<Conversation> listByUser(long userId) {
        return jdbc.sql("""
                SELECT c.id, c.user_id, c.title, c.rag_enabled, c.persona, c.mode, c.updated_at,
                       COUNT(m.id) AS message_count
                FROM conversations c LEFT JOIN messages m ON m.conversation_id = c.id
                WHERE c.user_id = ?
                GROUP BY c.id, c.user_id, c.title, c.rag_enabled, c.persona, c.mode, c.updated_at
                ORDER BY c.updated_at DESC
                """)
                .param(userId)
                .query((rs, i) -> new Conversation(
                        rs.getString("id"), rs.getLong("user_id"), rs.getString("title"),
                        rs.getBoolean("rag_enabled"), rs.getString("persona"), rs.getString("mode"),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getInt("message_count")))
                .list();
    }

    public Optional<Conversation> find(String id) {
        return jdbc.sql("""
                        SELECT c.id, c.user_id, c.title, c.rag_enabled, c.persona, c.mode, c.updated_at,
                               (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id) AS message_count
                        FROM conversations c WHERE c.id = ?
                        """)
                .param(id)
                .query((rs, i) -> new Conversation(
                        rs.getString("id"), rs.getLong("user_id"), rs.getString("title"),
                        rs.getBoolean("rag_enabled"), rs.getString("persona"), rs.getString("mode"),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getInt("message_count")))
                .optional();
    }

    /** 确认会话归属：存在但不属于该用户 → 403；不存在 → 自动创建（便于调试接口直连）。 */
    public void ensureOwned(String conversationId, long userId) {
        Optional<Conversation> existing = find(conversationId);
        if (existing.isPresent()) {
            if (existing.get().userId() != userId) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该会话");
            }
            return;
        }
        jdbc.sql("INSERT INTO conversations (id, user_id) VALUES (?, ?)")
                .param(conversationId)
                .param(userId)
                .update();
    }

    public Conversation create(long userId, String title, String persona, String mode) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbc.sql("INSERT INTO conversations (id, user_id, title, persona, mode) VALUES (?, ?, ?, ?, ?)")
                .param(id)
                .param(userId)
                .param(title == null || title.isBlank() ? "新对话" : title.trim())
                .param(persona == null ? "jiejie" : persona)
                .param(mode == null ? "advisor" : mode)
                .update();
        return find(id).orElseThrow();
    }

    /** 会话未开始聊天（0 条消息）时允许改绑角色/模式。 */
    public void updatePersonaMode(String conversationId, String persona, String mode) {
        jdbc.sql("UPDATE conversations SET persona = ?, mode = ? WHERE id = ?")
                .param(persona)
                .param(mode)
                .param(conversationId)
                .update();
    }

    public void rename(String conversationId, String title) {
        jdbc.sql("UPDATE conversations SET title = ?, updated_at = now() WHERE id = ?")
                .param(title)
                .param(conversationId)
                .update();
    }

    public void setRagEnabled(String conversationId, boolean ragEnabled) {
        jdbc.sql("UPDATE conversations SET rag_enabled = ? WHERE id = ?")
                .param(ragEnabled)
                .param(conversationId)
                .update();
    }

    public void delete(String conversationId) {
        jdbc.sql("DELETE FROM messages WHERE conversation_id = ?").param(conversationId).update();
        jdbc.sql("DELETE FROM conversations WHERE id = ?").param(conversationId).update();
    }

    // ---------- 消息 ----------

    public List<StoredMessage> listMessages(String conversationId) {
        return jdbc.sql(
                "SELECT id, role, content, ai_generated, created_at FROM messages WHERE conversation_id = ? ORDER BY id")
                .param(conversationId)
                .query((rs, i) -> new StoredMessage(
                        rs.getLong("id"), rs.getString("role"), rs.getString("content"),
                        rs.getBoolean("ai_generated"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    public void addMessage(String conversationId, String role, String content) {
        // assistant 角色即模型生成内容：落库时写入隐式标识
        jdbc.sql("INSERT INTO messages (conversation_id, role, content, ai_generated) VALUES (?, ?, ?, ?)")
                .param(conversationId)
                .param(role)
                .param(content)
                .param("assistant".equals(role))
                .update();
        jdbc.sql("UPDATE conversations SET updated_at = now() WHERE id = ?")
                .param(conversationId)
                .update();
    }

    public int countMessages(String conversationId) {
        return jdbc.sql("SELECT COUNT(*) FROM messages WHERE conversation_id = ?")
                .param(conversationId)
                .query(Integer.class)
                .single();
    }

    /** 按内容关键词检索当前用户的历史消息（新→旧），返回带上下文摘要的命中项。 */
    public List<SearchHit> search(long userId, String keyword, int limit) {
        String like = "%" + keyword + "%";
        return jdbc.sql("""
                SELECT m.conversation_id, c.title, m.role, m.content, m.created_at
                FROM messages m JOIN conversations c ON c.id = m.conversation_id
                WHERE c.user_id = ? AND m.content ILIKE ?
                ORDER BY m.id DESC LIMIT ?
                """)
                .param(userId)
                .param(like)
                .param(limit)
                .query((rs, i) -> new SearchHit(
                        rs.getString("conversation_id"), rs.getString("title"), rs.getString("role"),
                        snippet(rs.getString("content"), keyword),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    /** 截取关键词附近的片段作为搜索摘要。 */
    private static String snippet(String content, String keyword) {
        String flat = content.replaceAll("\\s+", " ").trim();
        int idx = flat.toLowerCase().indexOf(keyword.toLowerCase());
        if (idx < 0) {
            return flat.length() <= 60 ? flat : flat.substring(0, 60) + "…";
        }
        int start = Math.max(0, idx - 24);
        int end = Math.min(flat.length(), idx + keyword.length() + 36);
        return (start > 0 ? "…" : "") + flat.substring(start, end) + (end < flat.length() ? "…" : "");
    }

    /** 重新生成前使用：删除最后一轮问答（最后一条 assistant 及其前面相邻的 user）。 */
    public void deleteLastExchange(String conversationId) {
        record Row(long id, String role) {
        }
        List<Row> rows = jdbc.sql(
                "SELECT id, role FROM messages WHERE conversation_id = ? ORDER BY id DESC LIMIT 2")
                .param(conversationId)
                .query((rs, i) -> new Row(rs.getLong("id"), rs.getString("role")))
                .list();
        if (rows.isEmpty()) {
            return;
        }
        List<Long> toDelete = new ArrayList<>();
        toDelete.add(rows.get(0).id());
        if ("assistant".equals(rows.get(0).role()) && rows.size() > 1 && "user".equals(rows.get(1).role())) {
            toDelete.add(rows.get(1).id());
        }
        for (Long id : toDelete) {
            jdbc.sql("DELETE FROM messages WHERE id = ?").param(id).update();
        }
        jdbc.sql("UPDATE conversations SET updated_at = now() WHERE id = ?")
                .param(conversationId)
                .update();
    }
}
