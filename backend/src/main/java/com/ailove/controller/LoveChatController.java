package com.ailove.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI 恋爱大师对话接口：支持多轮会话记忆（会话 ID 隔离）与 SSE 流式输出。
 */
@RestController
@RequestMapping("/ai/love_chat")
public class LoveChatController {

    private final ChatClient loveChatClient;

    public LoveChatController(ChatClient loveChatClient) {
        this.loveChatClient = loveChatClient;
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
     * SSE 流式对话：前端逐字渲染。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestParam String message,
                                                @RequestParam(defaultValue = "default") String chatId) {
        return loveChatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                .map(token -> ServerSentEvent.builder(token).build());
    }
}
