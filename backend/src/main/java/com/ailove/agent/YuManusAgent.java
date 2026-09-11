package com.ailove.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.ailove.tools.LoveReportTool;
import com.ailove.tools.WeatherTool;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * YuManus 自主规划智能体（ReAct 模式）。
 * <p>
 * 循环执行 Plan（思考）→ Act（调用工具）→ Observe（观察结果），
 * 直到模型给出 [FINISH] 或达到最大步数。每一步的过程通过 Flux 实时推送。
 */
@Component
public class YuManusAgent {

    private static final Logger log = LoggerFactory.getLogger(YuManusAgent.class);

    private static final int MAX_STEPS = 8;
    private static final String FINISH = "[FINISH]";

    private static final String SYSTEM_PROMPT = """
            你是 YuManus，一个能自主规划并使用工具完成任务的智能体。

            你必须严格按以下格式输出每一步（不要输出多余内容）：
            Thought: 简要分析当前局面和下一步计划
            Action: 工具名称（必须是可用工具之一；如果任务已完成，写 [FINISH]）
            Action Input: 传给工具的参数，JSON 字符串格式（如果 Action 是 [FINISH]，则此处写最终答案）

            可用工具：
            %s

            规则：
            1. 每次只执行一个动作，等待观察结果后再思考下一步；
            2. 如果现有信息足够回答任务，立即输出 Action: [FINISH]；
            3. 不要重复执行参数完全相同的工具调用；
            4. 所有输出使用简体中文。
            """;

    private final ChatClient chatClient;
    private final Map<String, ToolCallback> toolRegistry = new LinkedHashMap<>();

    public YuManusAgent(ChatClient.Builder chatClientBuilder,
                        WeatherTool weatherTool,
                        LoveReportTool loveReportTool,
                        ObjectProvider<KnowledgeSearchTool> knowledgeToolProvider,
                        ObjectProvider<org.springframework.ai.tool.ToolCallbackProvider> mcpProvider) {
        this.chatClient = chatClientBuilder.build();
        for (ToolCallback callback : ToolCallbacks.from(weatherTool, loveReportTool)) {
            toolRegistry.put(callback.getToolDefinition().name(), callback);
        }
        // 知识库检索工具（无数据库部署时该 Bean 不存在）
        KnowledgeSearchTool knowledgeSearchTool = knowledgeToolProvider.getIfAvailable();
        if (knowledgeSearchTool != null) {
            for (ToolCallback callback : ToolCallbacks.from(knowledgeSearchTool)) {
                toolRegistry.put(callback.getToolDefinition().name(), callback);
            }
        }
        // MCP 远程工具（MCP 客户端关闭时该 Bean 不存在）
        org.springframework.ai.tool.ToolCallbackProvider provider = mcpProvider.getIfAvailable();
        if (provider != null) {
            for (ToolCallback callback : provider.getToolCallbacks()) {
                toolRegistry.put(callback.getToolDefinition().name(), callback);
            }
        }
        log.info("YuManus 已注册工具: {}", toolRegistry.keySet());
    }

    /**
     * 执行任务，逐步推送 ReAct 过程文本。
     */
    public Flux<String> runStream(String task) {
        return Flux.<String>create(sink -> {
            List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>();
            history.add(new org.springframework.ai.chat.messages.SystemMessage(
                    SYSTEM_PROMPT.formatted(describeTools())));
            history.add(new org.springframework.ai.chat.messages.UserMessage(task));

            for (int step = 1; step <= MAX_STEPS; step++) {
                sink.next("── 第 %d 步 ──".formatted(step));
                String output = chatClient.prompt()
                        .messages(history)
                        .call()
                        .content();
                history.add(new org.springframework.ai.chat.messages.AssistantMessage(output));
                sink.next(output);

                String action = parseField(output, "Action");
                if (action == null) {
                    sink.next("【观察】模型输出格式异常，终止执行。");
                    sink.complete();
                    return;
                }
                if (action.trim().equalsIgnoreCase(FINISH)) {
                    String answer = parseField(output, "Action Input");
                    sink.next("【最终回答】" + (answer == null ? output : answer));
                    sink.complete();
                    return;
                }
                String input = parseField(output, "Action Input");
                ToolCallback callback = toolRegistry.get(action.trim());
                if (callback == null) {
                    sink.next("【观察】未知工具: " + action + "，可用工具: " + toolRegistry.keySet());
                    history.add(new org.springframework.ai.chat.messages.UserMessage(
                            "Observation: 工具 " + action + " 不存在，请改用其他工具或给出最终答案。"));
                    continue;
                }
                try {
                    String observation = callback.call(input == null ? "{}" : input.trim());
                    sink.next("【观察】" + observation);
                    history.add(new org.springframework.ai.chat.messages.UserMessage(
                            "Observation: " + observation));
                } catch (Exception e) {
                    sink.next("【观察】工具执行失败: " + e.getMessage());
                    history.add(new org.springframework.ai.chat.messages.UserMessage(
                            "Observation: 工具执行失败: " + e.getMessage()));
                }
            }
            sink.next("【最终回答】已达到最大步数（" + MAX_STEPS + "），以上是目前的执行结果。");
            sink.complete();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String describeTools() {
        StringBuilder sb = new StringBuilder();
        toolRegistry.values().forEach(cb -> sb.append("- ").append(cb.getToolDefinition().name())
                .append(": ").append(cb.getToolDefinition().description()).append("\n"));
        return sb.toString();
    }

    /**
     * 从 ReAct 输出中解析指定字段（Thought / Action / Action Input）。
     */
    private String parseField(String output, String field) {
        if (output == null) {
            return null;
        }
        String prefix = field + ":";
        String altPrefix = field + "：";
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return trimmed.substring(prefix.length()).trim();
            }
            if (trimmed.regionMatches(true, 0, altPrefix, 0, altPrefix.length())) {
                return trimmed.substring(altPrefix.length()).trim();
            }
        }
        return null;
    }
}
