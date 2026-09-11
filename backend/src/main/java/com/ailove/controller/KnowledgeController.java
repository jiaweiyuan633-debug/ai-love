package com.ailove.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ailove.service.KnowledgeService;

/**
 * RAG 知识库管理接口：上传文档、列表、删除。
 */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        int chunks = knowledgeService.ingest(file);
        return Map.of("file_name", file.getOriginalFilename(), "chunks", chunks);
    }

    @GetMapping("/list")
    public List<Map<String, Object>> list() {
        return knowledgeService.listDocuments();
    }

    @DeleteMapping("/{docId}")
    public Map<String, Object> delete(@PathVariable String docId) {
        knowledgeService.deleteDocument(docId);
        return Map.of("deleted", docId);
    }
}
