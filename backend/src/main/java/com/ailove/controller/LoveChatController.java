package com.ailove.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI 恋爱大师对话接口：支持多轮会话记忆（会话 ID 隔离）、SSE 流式输出，
 * 以及基于 RAG 知识库的检索增强对话。
 */
@RestController
@RequestMapping("/ai/love_chat")
public class LoveChatController {

    private final ChatClient loveChatClient;
    private final VectorStore vectorStore;

    public LoveChatController(ChatClient loveChatClient, VectorStore vectorStore) {
        this.loveChatClient = loveChatClient;
        this.vectorStore = vectorStore;
    }

    /**
     * 同步对话（便于调试与脚本测试）。
     */
    @GetMapping
    public String chat(@RequestParam String message,
                       @RequestParam(defaultValue = "default") String chatId) {
        return loveChatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
    }

    /**
     * SSE 流式对话：前端逐字渲染。以 [DONE] 标记正常结束，避免前端 EventSource 误判断连。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestParam String message,
                                                @RequestParam(defaultValue = "default") String chatId) {
        return loveChatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                .map(token -> ServerSentEvent.builder(token).build())
                .concatWith(Flux.just(ServerSentEvent.builder("[DONE]").build()));
    }

    /**
     * RAG 检索增强对话：先从知识库检索相关片段，再结合人设与记忆回答。
     */
    @GetMapping(value = "/rag_stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> ragStream(@RequestParam String message,
                                                   @RequestParam(defaultValue = "default") String chatId) {
        return loveChatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(4).build())
                        .build())
                .stream()
                .content()
                .map(token -> ServerSentEvent.builder(token).build())
                .concatWith(Flux.just(ServerSentEvent.builder("[DONE]").build()));
    }
}
