package com.ailove.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
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

    /**
     * 多轮会话记忆：有持久化仓储（本地/有数据库）时挂接混合仓储（重启后可从库里恢复上下文），
     * 否则退回进程内默认仓储（云端体验模式）。
     */
    @Bean
    public ChatMemory chatMemory(org.springframework.beans.factory.ObjectProvider<ChatMemoryRepository> repositoryProvider) {
        MessageWindowChatMemory.Builder builder = MessageWindowChatMemory.builder()
                .maxMessages(40);
        ChatMemoryRepository repository = repositoryProvider.getIfAvailable();
        if (repository != null) {
            builder.chatMemoryRepository(repository);
        }
        return builder.build();
    }

    @Bean
    public ChatClient loveChatClient(ChatClient.Builder builder, ChatMemory chatMemory,
                                     WeatherTool weatherTool, LoveReportTool loveReportTool,
                                     ObjectProvider<ToolCallbackProvider> mcpProvider,
                                     org.springframework.beans.factory.ObjectProvider<com.ailove.tools.inline.LoveImageSearchTool> inlineImageTool,
                                     org.springframework.beans.factory.ObjectProvider<com.ailove.tools.inline.FlowerMeaningTool> inlineFlowerTool) {
        ChatClient.Builder b = builder
                .defaultSystem(loveMasterSystemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(weatherTool, loveReportTool);
        // MCP 远程工具（MCP 客户端关闭时该 Bean 不存在）
        ToolCallbackProvider provider = mcpProvider.getIfAvailable();
        if (provider != null) {
            b = b.defaultToolCallbacks(provider);
        }
        // 云端内联工具（app.inline-love-tools.enabled=true 时生效，替代 MCP 远程调用）
        var imgTool = inlineImageTool.getIfAvailable();
        var flowerTool = inlineFlowerTool.getIfAvailable();
        if (imgTool != null && flowerTool != null) {
            b = b.defaultTools(imgTool, flowerTool);
        }
        return b.build();
    }
}
