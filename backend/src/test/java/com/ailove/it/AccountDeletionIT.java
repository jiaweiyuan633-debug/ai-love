package com.ailove.it;

import java.util.Map;

import com.ailove.support.BaseIT;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注销账号集成测试：注销后数据全删、无法登录、旧 token 只能读到空数据、
 * 用户名释放可重新注册。
 */
class AccountDeletionIT extends BaseIT {

    private String register(String username) throws Exception {
        var resp = post("/auth/register", Map.of("username", username, "password", "Abcd1234", "nickname", "注销测试"), null);
        if (resp == null || resp.path("token").isMissingNode()) {
            throw new IllegalStateException("注册失败: " + username);
        }
        return resp.path("token").asText();
    }

    @Test
    void 注销后数据全删_无法登录_同名可重新注册() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String username = "del" + suffix;
        String token = register(username);

        // 准备数据：会话（陪伴模式带开场白）+ 心情打卡
        var conv = post("/api/conversations", Map.of("title", "注销前会话", "persona", "jiejie", "mode", "companion"), token);
        String convId = conv.path("id").asText();
        assertFalse(conv.path("id").isMissingNode());
        post("/api/mood?persona=jiejie", Map.of("score", 4), token);
        assertEquals(1, get("/api/conversations", token).size());

        // 注销
        assertEquals(204, raw("DELETE", "/auth/me", null, token).getStatusCode().value());

        // 旧 token：/auth/me 401（账号已删）；业务列表返回空；打卡记录清空
        assertEquals(401, raw("GET", "/auth/me", null, token).getStatusCode().value());
        assertEquals(0, get("/api/conversations", token).size(), "注销后会话应清空");
        var mood = get("/api/mood", token);
        assertFalse(mood.path("checkedToday").asBoolean(), "打卡记录应清空");

        // 无法用原密码登录
        assertEquals(401, raw("POST", "/auth/login",
                Map.of("username", username, "password", "Abcd1234"), null).getStatusCode().value());

        // 同名重新注册成功，得到全新空账号
        String newToken = register(username);
        assertEquals(0, get("/api/conversations", newToken).size());
    }

    @Test
    void 未登录注销应401() throws Exception {
        assertEquals(401, raw("DELETE", "/auth/me", null, null).getStatusCode().value());
    }
}
