package com.ailove.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 混合聊天记忆仓储：
 * - 进程内缓存承担高频读写（每轮对话都会整体保存窗口）；
 * - 首次访问某会话时从 messages 表恢复最近 40 条，实现后端重启后上下文不丢；
 * - 完整历史由 ConversationStore 另行落库（本仓储只服务模型上下文窗口）。
 */
public class HybridChatMemoryRepository implements ChatMemoryRepository {

    /** 与 MessageWindowChatMemory 的窗口大小一致 */
    static final int CONTEXT_WINDOW = 40;

    private final JdbcClient jdbc;
    private final Map<String, List<Message>> cache = new ConcurrentHashMap<>();

    public HybridChatMemoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> findConversationIds() {
        return new ArrayList<>(cache.keySet());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return cache.computeIfAbsent(conversationId, this::loadFromDb);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        cache.put(conversationId, List.copyOf(messages));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        cache.remove(conversationId);
    }

    private List<Message> loadFromDb(String conversationId) {
        List<String[]> rows = jdbc.sql(
                "SELECT role, content FROM messages WHERE conversation_id = ? ORDER BY id DESC LIMIT "
                        + CONTEXT_WINDOW)
                .param(conversationId)
                .query((rs, i) -> new String[] { rs.getString(1), rs.getString(2) })
                .list();
        Collections.reverse(rows);
        List<Message> messages = new ArrayList<>();
        for (String[] row : rows) {
            Message message = toMessage(row[0], row[1]);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    static Message toMessage(String role, String content) {
        return switch (role) {
            case "user" -> new UserMessage(content);
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            default -> null; // tool 等中间消息不参与上下文重建
        };
    }
}
