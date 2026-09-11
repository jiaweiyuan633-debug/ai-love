package com.ailove.agent;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 知识库检索工具：供 YuManus 智能体在规划中调用 RAG 检索。
 */
@Component
@ConditionalOnProperty(name = "app.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeSearchTool {

    private final VectorStore vectorStore;

    public KnowledgeSearchTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(description = "在恋爱知识库中检索与查询相关的资料片段，返回参考内容")
    public String searchKnowledge(@ToolParam(description = "检索关键词或问题") String query) {
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(4).build());
        if (docs == null || docs.isEmpty()) {
            return "知识库中没有找到相关内容。";
        }
        return docs.stream()
                .map(d -> "- " + d.getText())
                .collect(Collectors.joining("\n"));
    }
}
