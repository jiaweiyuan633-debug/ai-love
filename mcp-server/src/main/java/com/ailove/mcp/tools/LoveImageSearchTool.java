package com.ailove.mcp.tools;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 恋爱图片搜索 MCP 工具：基于 Pexels 免费 API。
 * 未配置 PEXELS_API_KEY 时优雅降级，返回提示信息。
 */
@Component
public class LoveImageSearchTool {

    private static final Logger log = LoggerFactory.getLogger(LoveImageSearchTool.class);

    private final RestClient restClient = RestClient.create();

    @Value("${pexels.api-key:}")
    private String apiKey;

    @Tool(description = "按关键词搜索浪漫主题图片（鲜花、烛光晚餐、求婚等），返回图片链接列表，用于给用户配图")
    public String searchLoveImage(@ToolParam(description = "图片搜索关键词，例如：玫瑰花束") String keyword) {
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置 PEXELS_API_KEY，无法搜索图片。请在服务端环境变量中配置后重试。";
        }
        try {
            Map<String, Object> resp = restClient.get()
                    .uri("https://api.pexels.com/v1/search?query={q}&per_page=3", keyword)
                    .header("Authorization", apiKey)
                    .retrieve()
                    .body(Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> photos = (List<Map<String, Object>>) resp.get("photos");
            if (photos == null || photos.isEmpty()) {
                return "没有找到与「" + keyword + "」相关的图片。";
            }
            StringBuilder sb = new StringBuilder("找到 ").append(photos.size()).append(" 张图片：\n");
            for (Map<String, Object> photo : photos) {
                @SuppressWarnings("unchecked")
                Map<String, Object> src = (Map<String, Object>) photo.get("src");
                sb.append("- ").append(photo.get("alt")).append(": ").append(src.get("large")).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("图片搜索失败: {}", keyword, e);
            return "图片搜索失败：" + e.getMessage();
        }
    }
}
