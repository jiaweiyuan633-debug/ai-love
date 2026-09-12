package com.ailove.support;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * 集成测试基类：随机端口起完整上下文 + 独立测试库 ai_love_test。
 * AI 用深度打桩的 ChatClient.Builder 顶替真实模型客户端——所有生成类调用不触网，
 * 各 Service 拿到空结果后走各自的兜底文案（顺带验证兜底逻辑）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(BaseIT.MockAiConfig.class)
public abstract class BaseIT {

    @TestConfiguration
    static class MockAiConfig {
        @Bean
        public ChatClient.Builder chatClientBuilder() {
            return mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        }
    }

    @Autowired
    protected TestRestTemplate rest;

    private final ObjectMapper objectMapper = new ObjectMapper();

    protected JsonNode post(String path, Object body, String token) throws Exception {
        return exchange("POST", path, body, token);
    }

    protected JsonNode get(String path, String token) throws Exception {
        return exchange("GET", path, null, token);
    }

    protected JsonNode patch(String path, Object body, String token) throws Exception {
        return exchange("PATCH", path, body, token);
    }

    protected JsonNode delete(String path, String token) throws Exception {
        return exchange("DELETE", path, null, token);
    }

    /** 返回响应 JSON；无响应体（如 204）返回 null。 */
    protected JsonNode exchange(String method, String path, Object body, String token) throws Exception {
        var resp = raw(method, path, body, token);
        if (resp.getBody() == null || resp.getBody().isBlank()) {
            return null;
        }
        return objectMapper.readTree(resp.getBody());
    }

    /** 需要断言状态码时使用。 */
    protected org.springframework.http.ResponseEntity<String> raw(String method, String path, Object body,
                                                                   String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        var request = new org.springframework.http.HttpEntity<>(body, headers);
        return rest.exchange(path, org.springframework.http.HttpMethod.valueOf(method), request, String.class);
    }

    /** 注册一个随机用户并返回其 token（失败抛错中断测试）。 */
    protected String newUser() throws Exception {
        // 用户名正则不允许连字符，去掉 UUID 中的 -
        String username = "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        JsonNode resp = post("/auth/register",
                Map.of("username", username, "password", "Abcd1234", "nickname", "集成测试"), null);
        if (resp == null || resp.path("token").isMissingNode()) {
            throw new IllegalStateException("注册测试用户失败: " + username);
        }
        return resp.path("token").asText();
    }
}
