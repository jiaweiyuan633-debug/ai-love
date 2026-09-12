package com.ailove.chat;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 长期记忆：每轮对话后异步提炼“值得长期记住的用户信息”入库，
 * 并在后续对话中作为记忆块注入 system prompt。
 * 用户可在设置中关闭（users.memory_enabled=false 时不提炼、不注入）。
 */
@Service
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class MemoryService {

    private static final int MAX_MEMORIES_PER_USER = 100;
    private static final int INJECT_TOP_N = 20;

    private final JdbcClient jdbc;
    private final ChatClient extractClient;

    public MemoryService(JdbcClient jdbc, ChatClient.Builder builder) {
        this.jdbc = jdbc;
        this.extractClient = builder
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-turbo").temperature(0.2).build())
                .build();
    }

    // ---------- 提炼 ----------

    /** 流结束后调用；异步执行，失败静默（不影响对话）。 */
    public void extractAsync(long userId, String userMessage, String assistantReply) {
        Mono.fromRunnable(() -> doExtract(userId, userMessage, assistantReply))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private void doExtract(long userId, String userMessage, String assistantReply) {
        try {
            if (!memoryEnabled(userId)) {
                return;
            }
            String answer = extractClient.prompt()
                    .user("""
                            你是记忆管理员。从下面这轮对话中提取「值得长期记住的用户信息」，
                            例如：用户称呼/名字、恋爱状态、伴侣或心仪对象的信息、喜好与讨厌、
                            重要日期（生日/纪念日）、长期目标等。

                            规则：
                            1. 只提取确定的事实，不要推测，不要给建议；
                            2. 每条一行，以“- ”开头，最多 3 条，每条不超过 50 字；
                            3. 没有值得记住的信息时只输出 NONE。

                            对话：
                            用户：%s
                            助手：%s
                            """.formatted(
                            truncate(userMessage, 600),
                            truncate(assistantReply, 600)))
                    .call()
                    .content();
            if (answer == null || answer.contains("NONE")) {
                return;
            }
            List<String> facts = answer.lines()
                    .map(String::trim)
                    .filter(line -> line.startsWith("-"))
                    .map(line -> line.substring(1).trim())
                    .filter(line -> !line.isEmpty() && line.length() <= 200)
                    .limit(3)
                    .toList();
            for (String fact : facts) {
                insertIfAbsent(userId, fact);
            }
            trimToCap(userId);
        } catch (Exception ignored) {
            // 记忆提炼失败不影响主流程
        }
    }

    private void insertIfAbsent(long userId, String content) {
        Long exists = jdbc.sql("SELECT id FROM user_memories WHERE user_id = ? AND content = ?")
                .param(userId)
                .param(content)
                .query(Long.class)
                .optional()
                .orElse(null);
        if (exists == null) {
            jdbc.sql("INSERT INTO user_memories (user_id, content) VALUES (?, ?)")
                    .param(userId)
                    .param(content)
                    .update();
        }
    }

    private void trimToCap(long userId) {
        jdbc.sql("""
                DELETE FROM user_memories
                WHERE user_id = ? AND id NOT IN (
                    SELECT id FROM user_memories WHERE user_id = ? ORDER BY id DESC LIMIT ?
                )
                """)
                .param(userId)
                .param(userId)
                .param(MAX_MEMORIES_PER_USER)
                .update();
    }

    // ---------- 注入与管理 ----------

    /** 生成注入 system prompt 的记忆块；无记忆或用户关闭记忆时返回空串。 */
    public String memoryBlock(long userId) {
        if (!memoryEnabled(userId)) {
            return "";
        }
        List<String> memories = jdbc.sql(
                        "SELECT content FROM user_memories WHERE user_id = ? ORDER BY id DESC LIMIT ?")
                .param(userId)
                .param(INJECT_TOP_N)
                .query(String.class)
                .list();
        if (memories.isEmpty()) {
            return "";
        }
        return memories.stream()
                .map(content -> "- " + content)
                .collect(Collectors.joining(
                        "\n",
                        "# 关于用户的长期记忆（请自然地运用这些信息，不要生硬复述）\n",
                        ""));
    }

    public List<MemoryItem> list(long userId) {
        return jdbc.sql("SELECT id, content, created_at FROM user_memories WHERE user_id = ? ORDER BY id DESC")
                .param(userId)
                .query((rs, i) -> new MemoryItem(
                        rs.getLong("id"), rs.getString("content"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    /** 删除一条记忆（校验归属）。 */
    public boolean delete(long userId, long memoryId) {
        return jdbc.sql("DELETE FROM user_memories WHERE user_id = ? AND id = ?")
                .param(userId)
                .param(memoryId)
                .update() > 0;
    }

    public void clear(long userId) {
        jdbc.sql("DELETE FROM user_memories WHERE user_id = ?").param(userId).update();
    }

    public boolean memoryEnabled(long userId) {
        return jdbc.sql("SELECT memory_enabled FROM users WHERE id = ?")
                .param(userId)
                .query(Boolean.class)
                .optional()
                .orElse(true);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    public record MemoryItem(long id, String content, java.time.Instant createdAt) {
    }
}
