package com.ailove.it;

import java.util.Map;
import java.util.UUID;

import com.ailove.support.BaseIT;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 邮箱绑定与找回密码集成测试（JavaMailSender 用 mock，验证码从捕获的邮件正文中提取）：
 * 覆盖验证码重发覆盖旧码、错误码拒绝、绑定后重置密码、防用户名枚举。
 */
class PasswordResetIT extends BaseIT {

    @TestConfiguration
    static class MailMockConfig {
        @Bean
        public JavaMailSender javaMailSender() {
            return mock(JavaMailSender.class);
        }
    }

    @Autowired
    private JavaMailSender mailSender;

    /** 取最近一封验证码邮件中的 6 位码 */
    private String lastSentCode() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        SimpleMailMessage last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{6}")
                .matcher(last.getText());
        assertTrue(m.find(), "邮件正文应包含 6 位验证码: " + last.getText());
        return m.group();
    }

    private String[] registerUser(String email) throws Exception {
        String username = "pr" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var resp = post("/auth/register", Map.of("username", username, "password", "Abcd1234"), null);
        return new String[] { username, resp.path("token").asText() };
    }

    @Test
    void 绑定邮箱_重发覆盖旧码_错误码拒绝_绑定成功() throws Exception {
        String email = "user-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        var user = registerUser(email);
        String username = user[0];
        String token = user[1];

        // 第一次发码
        assertEquals(200, raw("POST", "/auth/me/email/request", Map.of("email", email), token).getStatusCode().value());
        String firstCode = lastSentCode();

        // 重发：新码覆盖旧码，旧码应失效
        post("/auth/me/email/request", Map.of("email", email), token);
        String secondCode = lastSentCode();
        assertNotEquals(firstCode, secondCode, "重发应产生新验证码");
        assertEquals(400, raw("POST", "/auth/me/email/confirm", Map.of("email", email, "code", firstCode), token)
                .getStatusCode().value());

        // 正确码绑定成功，/auth/me 带出邮箱；同一码二次消费应失败（已删除）
        var bound = post("/auth/me/email/confirm", Map.of("email", email, "code", secondCode), token);
        assertEquals(email, bound.path("email").asText());
        assertEquals(400, raw("POST", "/auth/me/email/confirm", Map.of("email", email, "code", secondCode), token)
                .getStatusCode().value());

        // 用绑定好的邮箱走找回密码全流程
        assertEquals(200, raw("POST", "/auth/password/reset/request",
                Map.of("username", username, "email", email), null).getStatusCode().value());
        String resetCode = lastSentCode();
        assertEquals(400, raw("POST", "/auth/password/reset/confirm",
                Map.of("username", username, "email", email, "code", "000000", "newPassword", "NewPass123"), null)
                .getStatusCode().value());
        assertEquals(200, raw("POST", "/auth/password/reset/confirm",
                Map.of("username", username, "email", email, "code", resetCode, "newPassword", "NewPass123"), null)
                .getStatusCode().value());

        // 旧密码失效、新密码可登录
        assertEquals(401, raw("POST", "/auth/login", Map.of("username", username, "password", "Abcd1234"), null)
                .getStatusCode().value());
        assertEquals(200, raw("POST", "/auth/login", Map.of("username", username, "password", "NewPass123"), null)
                .getStatusCode().value());
    }

    @Test
    void 用户名与邮箱不匹配时不发码且不暴露存在性() throws Exception {
        String email = "ghost-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        var user = registerUser("real-" + UUID.randomUUID().toString().substring(0, 8) + "@x.dev");
        // 用错误用户名+不存在邮箱请求：仍返回 200 通用文案
        var resp = raw("POST", "/auth/password/reset/request",
                Map.of("username", "不存在的用户", "email", email), null);
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(new String(resp.getBody()).contains("如果该邮箱已绑定"));
    }

    @Test
    void 弱新密码被拒绝() throws Exception {
        String email = "weak-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        var user = registerUser(email);
        String username = user[0];
        String token = user[1];
        post("/auth/me/email/request", Map.of("email", email), token);
        String code = lastSentCode();
        post("/auth/me/email/confirm", Map.of("email", email, "code", code), token);

        post("/auth/password/reset/request", Map.of("username", username, "email", email), null);
        String resetCode = lastSentCode();
        assertEquals(400, raw("POST", "/auth/password/reset/confirm",
                Map.of("username", username, "email", email, "code", resetCode, "newPassword", "short"), null)
                .getStatusCode().value());
    }
}
