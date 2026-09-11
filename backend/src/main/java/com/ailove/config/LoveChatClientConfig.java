package com.ailove.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import com.ailove.tools.LoveReportTool;
import com.ailove.tools.WeatherTool;

/**
 * AI 恋爱大师专用 ChatClient：人设 System Prompt + 多轮会话记忆 + 本地工具 + MCP 远程工具。
 */
@Configuration
public class LoveChatClientConfig {

    @Value("classpath:prompts/love-master-system.st")
    private Resource loveMasterSystemPrompt;

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient loveChatClient(ChatClient.Builder builder, ChatMemory chatMemory,
                                     WeatherTool weatherTool, LoveReportTool loveReportTool,
                                     ToolCallbackProvider mcpToolCallbackProvider) {
        return builder
                .defaultSystem(loveMasterSystemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(weatherTool, loveReportTool)
                .defaultToolCallbacks(mcpToolCallbackProvider)
                .build();
    }
}
