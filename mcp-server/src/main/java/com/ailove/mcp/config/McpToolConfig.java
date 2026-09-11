package com.ailove.mcp.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ailove.mcp.tools.FlowerMeaningTool;
import com.ailove.mcp.tools.LoveImageSearchTool;

/**
 * 把 @Tool 注解的工具类注册为 MCP 工具，暴露给连接本服务的 MCP 客户端。
 */
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider loveTools(LoveImageSearchTool loveImageSearchTool,
                                          FlowerMeaningTool flowerMeaningTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(loveImageSearchTool, flowerMeaningTool)
                .build();
    }
}
