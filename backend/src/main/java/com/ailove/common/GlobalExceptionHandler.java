package com.ailove.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 全局异常兜底：统一 {"error": "..."} JSON 格式，AI 上游故障转 502/422 友好文案，
 * 其余异常转 500 通用文案（细节只进日志），不再向前端暴露堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务型异常（如“语音合成失败”“已开始聊天的会话不能更换角色”），保留原状态码与原因。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException e) {
        String msg = e.getReason() != null ? e.getReason() : "请求失败";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", msg));
    }

    /** AI 上游（DashScope）调用失败：命中内容审核 422，其余 502。 */
    @ExceptionHandler({RestClientResponseException.class, NonTransientAiException.class, TransientAiException.class})
    public ResponseEntity<Map<String, String>> handleAiUpstream(Exception e) {
        log.warn("AI 上游调用失败: {}", e.toString());
        HttpStatus status = AiErrorMessages.isModerationBlocked(e)
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(Map.of("error", AiErrorMessages.friendly(e)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception e) {
        log.error("接口处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "服务器开小差了，请稍后再试"));
    }
}
