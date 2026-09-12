package com.ailove.controller;

import java.util.concurrent.atomic.AtomicBoolean;

import com.ailove.auth.AuthContext;
import com.ailove.chat.AutoTitleService;
import com.ailove.chat.ConversationStore;
import com.ailove.chat.MemoryService;
import com.ailove.common.AiErrorMessages;
import com.ailove.couple.CoupleService;
import com.ailove.persona.PersonaCatalog;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

/**
 * AI 恋爱大师对话接口：多轮会话记忆（会话 ID 隔离）+ SSE 流式输出 + RAG 检索增强
 * + 完整历史落库（含“停止生成”时的部分内容）+ 重新生成 + 自动会话标题。
 * 体验模式（无数据库）下自动退回纯内存对话。
 */
@RestController
@RequestMapping("/ai/love_chat")
public class LoveChatController {

    private final ChatClient loveChatClient;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;
    private final ConversationStore conversationStore;
    private final AutoTitleService autoTitleService;
    private final MemoryService memoryService;
    private final CoupleService coupleService;
    private final String advisorPrompt;
    private final String companionPrompt;

    public LoveChatController(ChatClient loveChatClient, ChatMemory chatMemory,
                              ObjectProvider<VectorStore> vectorStoreProvider,
                              ObjectProvider<ConversationStore> conversationStoreProvider,
                              ObjectProvider<AutoTitleService> autoTitleProvider,
                              ObjectProvider<MemoryService> memoryServiceProvider,
                              ObjectProvider<CoupleService> coupleServiceProvider,
                              @Value("classpath:prompts/love-master-system.st") Resource personaResource,
                              @Value("classpath:prompts/companion-system.st") Resource companionResource) {
        this.loveChatClient = loveChatClient;
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.conversationStore = conversationStoreProvider.getIfAvailable();
        this.autoTitleService = autoTitleProvider.getIfAvailable();
        this.memoryService = memoryServiceProvider.getIfAvailable();
        this.coupleService = coupleServiceProvider.getIfAvailable();
        try (var in = personaResource.getInputStream()) {
            this.advisorPrompt = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("无法读取恋爱大师人设提示词", e);
        }
        try (var in = companionResource.getInputStream()) {
            this.companionPrompt = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("无法读取陪伴模式提示词", e);
        }
    }

    /** 同步对话（调试/脚本用），同样落库。 */
    @GetMapping
    public String chat(@RequestParam String message,
                       @RequestParam(defaultValue = "default") String chatId,
                       @RequestParam(defaultValue = "jiejie") String persona,
                       @RequestParam(defaultValue = "advisor") String mode) {
        Long userId = AuthContext.userId();
        ensureConversation(chatId, userId);
        String reply = loveChatClient.prompt()
                .system(buildSystem(userId, persona, mode))
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
        persistExchange(chatId, userId, message, reply);
        extractMemory(userId, message, reply);
        return reply;
    }

    /** SSE 流式对话：前端逐字渲染，[DONE] 为正常结束标记。 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestParam String message,
                                                @RequestParam(defaultValue = "default") String chatId,
                                                @RequestParam(defaultValue = "false") boolean regenerate,
                                                @RequestParam(required = false) String model,
                                                @RequestParam(defaultValue = "jiejie") String persona,
                                                @RequestParam(defaultValue = "advisor") String mode) {
        return chatStream(message, chatId, regenerate, model, false, persona, mode);
    }

    /** RAG 检索增强对话：先检索知识库相关片段再回答（未配置向量库时给出提示）。 */
    @GetMapping(value = "/rag_stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> ragStream(@RequestParam String message,
                                                   @RequestParam(defaultValue = "default") String chatId,
                                                   @RequestParam(defaultValue = "false") boolean regenerate,
                                                   @RequestParam(required = false) String model,
                                                   @RequestParam(defaultValue = "jiejie") String persona,
                                                   @RequestParam(defaultValue = "advisor") String mode) {
        if (vectorStore == null) {
            return Flux.just(ServerSentEvent.builder("知识库功能未启用：当前部署未配置向量数据库。")
                    .build(), ServerSentEvent.builder("[DONE]").build());
        }
        return chatStream(message, chatId, regenerate, model, true, persona, mode);
    }

    private Flux<ServerSentEvent<String>> chatStream(String message, String chatId,
                                                     boolean regenerate, String model, boolean rag,
                                                     String persona, String mode) {
        Long userId = AuthContext.userId();
        ensureConversation(chatId, userId);
        if (conversationStore != null && userId != null && regenerate) {
            // 删掉库里最后一轮问答，并清空上下文缓存（下次访问自动从库中恢复，不含被删轮次）
            conversationStore.deleteLastExchange(chatId);
            chatMemory.clear(chatId);
        }

        StringBuilder reply = new StringBuilder();
        ChatClient.ChatClientRequestSpec spec = loveChatClient.prompt()
                .system(buildSystem(userId, persona, mode))
                .user(message);
        if (model != null && !model.isBlank()) {
            spec = spec.options(OpenAiChatOptions.builder().model(model).build());
        }
        spec = spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId));
        if (rag && vectorStore != null) {
            spec = spec.advisors(QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(SearchRequest.builder().topK(4).build())
                    .build());
        }

        AtomicBoolean titleScheduled = new AtomicBoolean(false);
        // AI 生成内容隐式标识帧（event: ai-meta，不带 data 行）：旧客户端解析不到 data 会自动忽略，
        // 新前端据此把当条回复标记为 AI 生成（《人工智能生成合成内容标识办法》元数据级标识）
        ServerSentEvent<String> aiMeta = ServerSentEvent.<String>builder().event("ai-meta").build();
        return Flux.just(aiMeta)
                .concatWith(spec.stream()
                        .content()
                        .doOnNext(reply::append)
                        .map(token -> ServerSentEvent.builder(token).build())
                        // AI 上游报错/审核拦截：发一条错误帧再正常收尾，前端不再永远停在“思考中”
                        .onErrorResume(e -> Flux.just(
                                ServerSentEvent.builder("[ERROR] " + AiErrorMessages.friendly(e)).build()))
                        .concatWith(Flux.just(ServerSentEvent.builder("[DONE]").build())))
                .doFinally(signal -> {
                    String content = reply.toString();
                    persistExchange(chatId, userId, message, content.isEmpty() ? null : content);
                    extractMemory(userId, message, content);
                    // 首轮问答结束后生成会话标题（一次会话只调度一次）
                    if (autoTitleService != null && conversationStore != null && userId != null
                            && !content.isEmpty() && titleScheduled.compareAndSet(false, true)) {
                        autoTitleService.maybeGenerateTitleAsync(chatId, message);
                    }
                });
    }

    private void ensureConversation(String chatId, Long userId) {
        if (conversationStore != null && userId != null) {
            conversationStore.ensureOwned(chatId, userId);
        }
    }

    /**
     * system prompt = 基础人设（顾问 or 陪伴）+ 角色风格段 + 用户长期记忆块 + 情侣绑定块。
     * 顾问模式：恋爱大师底稿 + 该角色的咨询风格；陪伴模式：陪伴底稿 + 该角色的陪伴人设。
     */
    private String buildSystem(Long userId, String personaId, String mode) {
        PersonaCatalog.Persona persona = PersonaCatalog.find(personaId);
        boolean companion = "companion".equals(mode);
        StringBuilder sb = new StringBuilder(companion ? companionPrompt : advisorPrompt);
        sb.append("\n\n# 你的角色\n")
                .append(companion ? persona.companionRole() : persona.advisorStyle());
        if (userId == null) {
            return sb.toString();
        }
        if (memoryService != null) {
            String memoryBlock = memoryService.memoryBlock(userId);
            if (!memoryBlock.isEmpty()) {
                sb.append("\n\n").append(memoryBlock);
            }
        }
        if (coupleService != null) {
            String coupleBlock = coupleService.coupleBlock(userId);
            if (!coupleBlock.isEmpty()) {
                sb.append("\n\n").append(coupleBlock);
            }
        }
        return sb.toString();
    }

    /** 对话结束后异步提炼长期记忆（体验模式或内容为空时跳过）。 */
    private void extractMemory(Long userId, String userMessage, String assistantReply) {
        if (memoryService != null && userId != null
                && assistantReply != null && !assistantReply.isEmpty()) {
            memoryService.extractAsync(userId, userMessage, assistantReply);
        }
    }

    /** 完整历史落库：用户消息 + 助手回复（为空则跳过）。失败不影响对话本身。 */
    private void persistExchange(String chatId, Long userId, String userMessage, String assistantReply) {
        if (conversationStore == null || userId == null) {
            return;
        }
        try {
            conversationStore.addMessage(chatId, "user", userMessage);
            if (assistantReply != null && !assistantReply.isEmpty()) {
                conversationStore.addMessage(chatId, "assistant", assistantReply);
            }
        } catch (Exception ignored) {
            // 持久化异常不应打断对话
        }
    }
}
