package com.ailove.chat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 追问建议：每轮回答结束后，按最后一轮问答生成 3 个用户可能想继续问的问题。
 * 与角色卡片（待接入）联动时也沿用此服务。结果仅做进程内 LRU 缓存，不落库。
 */
@Service
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class SuggestionService {

    private static final int CACHE_CAP = 200;

    private final JdbcClient jdbc;
    private final ChatClient chatClient;
    private final Map<String, List<String>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
                    return size() > CACHE_CAP;
                }
            });

    public SuggestionService(JdbcClient jdbc, ChatClient.Builder builder) {
        this.jdbc = jdbc;
        this.chatClient = builder
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-turbo").temperature(0.8).build())
                .build();
    }

    /** 取会话最后一轮问答生成追问；调用方需先校验会话归属。 */
    public List<String> suggest(String conversationId) {
        List<String> cached = cache.get(conversationId);
        if (cached != null) {
            return cached;
        }
        record Exchange(String role, String content) {
        }
        List<Exchange> recent = jdbc.sql("""
                SELECT role, content FROM messages WHERE conversation_id = ?
                ORDER BY id DESC LIMIT 2
                """)
                .param(conversationId)
                .query((rs, i) -> new Exchange(rs.getString("role"), rs.getString("content")))
                .list();
        if (recent.size() < 2
                || !"assistant".equals(recent.get(0).role())
                || !"user".equals(recent.get(1).role())) {
            return List.of();
        }
        List<String> result = doGenerate(recent.get(1).content(), recent.get(0).content());
        if (!result.isEmpty()) {
            cache.put(conversationId, result);
        }
        return result;
    }

    private List<String> doGenerate(String userMessage, String assistantReply) {
        try {
            String answer = chatClient.prompt()
                    .user("""
                            你是对话运营助手。根据下面这轮“恋爱咨询”对话，站在用户角度生成 3 个接下来最可能想问的追问。
                            要求：口语化、具体、可直接作为消息发送；每行一个，以“- ”开头；不要编号、不要解释。

                            用户：%s
                            助手：%s
                            """.formatted(truncate(userMessage, 500), truncate(assistantReply, 800)))
                    .call()
                    .content();
            if (answer == null) {
                return List.of();
            }
            return answer.lines()
                    .map(String::trim)
                    .filter(line -> line.startsWith("-"))
                    .map(line -> line.substring(1).trim())
                    .filter(q -> !q.isEmpty() && q.length() <= 40)
                    .limit(3)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
