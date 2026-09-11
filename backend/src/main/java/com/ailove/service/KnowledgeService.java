package com.ailove.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * RAG 知识库服务：文档上传 → Tika 解析 → TokenTextSplitter 切分 → 向量化入库 (PgVector)。
 * 无数据库部署时通过 app.knowledge.enabled=false 整体关闭。
 */
@Service
@ConditionalOnProperty(name = "app.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final VectorStore vectorStore;
    private final JdbcClient jdbcClient;

    public KnowledgeService(VectorStore vectorStore, JdbcClient jdbcClient) {
        this.vectorStore = vectorStore;
        this.jdbcClient = jdbcClient;
    }

    /**
     * 上传并处理文档，返回切分入库的分块数量。
     */
    public int ingest(MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
            Resource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            TokenTextSplitter splitter = TokenTextSplitter.builder().build();
            List<Document> chunks = splitter.apply(reader.get());
            String docId = UUID.randomUUID().toString();
            for (Document chunk : chunks) {
                Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
                metadata.put("file_name", fileName);
                metadata.put("doc_id", docId);
                chunk.getMetadata().clear();
                chunk.getMetadata().putAll(metadata);
            }
            vectorStore.add(chunks);
            log.info("文档已入库: {} ({} chunks, doc_id={})", fileName, chunks.size(), docId);
            return chunks.size();
        } catch (Exception e) {
            throw new IllegalStateException("文档处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出已入库的文档（按文件名汇总分块数）。
     */
    public List<Map<String, Object>> listDocuments() {
        return jdbcClient.sql(
                        "SELECT metadata->>'file_name' AS file_name, metadata->>'doc_id' AS doc_id, count(*) AS chunks " +
                                "FROM vector_store GROUP BY 1, 2 ORDER BY 1")
                .query((rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("file_name", rs.getString("file_name"));
                    row.put("doc_id", rs.getString("doc_id"));
                    row.put("chunks", rs.getLong("chunks"));
                    return row;
                })
                .list();
    }

    public void deleteDocument(String docId) {
        jdbcClient.sql("DELETE FROM vector_store WHERE metadata->>'doc_id' = :docId")
                .param("docId", docId)
                .update();
    }
}
