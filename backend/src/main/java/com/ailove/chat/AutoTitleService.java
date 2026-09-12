package com.ailove.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 会话标题自动生成：首轮对话结束后用 qwen-turbo 概括一个短标题（便宜模型，不占用主对话）。
 */
@Service
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class AutoTitleService {

    private final ChatClient titleClient;
    private final ConversationStore store;

    public AutoTitleService(ChatClient.Builder builder, ConversationStore store) {
        this.titleClient = builder
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-turbo").temperature(0.3).build())
                .build();
        this.store = store;
    }

    /** 首轮问答完成后调用；仅在标题仍为默认“新对话”时生成。失败静默降级为截断首条消息。 */
    public void maybeGenerateTitle(String conversationId, String firstUserMessage) {
        try {
            ConversationStore.Conversation conversation = store.find(conversationId).orElse(null);
            if (conversation == null || !"新对话".equals(conversation.title())
                    || store.countMessages(conversationId) < 2) {
                return;
            }
            String title = titleClient.prompt()
                    .user("""
                            请为下面的对话起一个不超过12个字的中文标题。
                            要求：只输出标题本身，不要引号、句号或任何解释。
                            对话开头：%s
                            """.formatted(firstUserMessage.substring(0, Math.min(firstUserMessage.length(), 200))))
                    .call()
                    .content();
            if (title == null || title.isBlank()) {
                throw new IllegalStateException("空标题");
            }
            title = title.replaceAll("[\\r\\n\"“”]", "").trim();
            if (title.length() > 20) {
                title = title.substring(0, 20);
            }
            store.rename(conversationId, title);
        } catch (Exception e) {
            // 静默降级：截断首条用户消息作为标题
            String fallback = firstUserMessage.replaceAll("\\s+", " ").trim();
            store.rename(conversationId, fallback.length() > 16 ? fallback.substring(0, 16) + "…" : fallback);
        }
    }

    /** 供 doFinally 触发（忽略订阅结果）。 */
    public void maybeGenerateTitleAsync(String conversationId, String firstUserMessage) {
        Mono.fromRunnable(() -> maybeGenerateTitle(conversationId, firstUserMessage))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}
