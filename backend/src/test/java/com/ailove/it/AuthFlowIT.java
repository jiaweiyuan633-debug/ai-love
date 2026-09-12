package com.ailove.it;

import java.util.Map;

import com.ailove.support.BaseIT;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册/登录/鉴权全流程：密码强度、重复用户名、错误密码、401 保护与 token 放行。
 */
class AuthFlowIT extends BaseIT {

    @Test
    void 弱密码应被拒绝() throws Exception {
        String username = "weak_" + System.nanoTime();
        // 少于 8 位
        assertEquals(400, raw("POST", "/auth/register",
                Map.of("username", username, "password", "ab12"), null).getStatusCode().value());
        // 纯字母无数字
        assertEquals(400, raw("POST", "/auth/register",
                Map.of("username", username, "password", "onlyletters"), null).getStatusCode().value());
        // 纯数字无字母
        assertEquals(400, raw("POST", "/auth/register",
                Map.of("username", username, "password", "12345678"), null).getStatusCode().value());
    }

    @Test
    void 注册后重复注册应拒绝且登录成功() throws Exception {
        String username = "dup_" + System.nanoTime();
        var first = raw("POST", "/auth/register",
                Map.of("username", username, "password", "Abcd1234"), null);
        assertEquals(200, first.getStatusCode().value());

        assertEquals(400, raw("POST", "/auth/register",
                Map.of("username", username, "password", "Abcd1234"), null).getStatusCode().value());

        var login = raw("POST", "/auth/login",
                Map.of("username", username, "password", "Abcd1234"), null);
        assertEquals(200, login.getStatusCode().value());
        assertTrue(login.getBody().contains("\"token\""));
    }

    @Test
    void 错误密码登录应401() throws Exception {
        String username = "wrongpw_" + System.nanoTime();
        assertEquals(200, raw("POST", "/auth/register",
                Map.of("username", username, "password", "Abcd1234"), null).getStatusCode().value());
        var bad = raw("POST", "/auth/login",
                Map.of("username", username, "password", "Wrong999"), null);
        assertEquals(401, bad.getStatusCode().value());
        assertTrue(bad.getBody().contains("用户名或密码错误"));
    }

    @Test
    void 受保护接口无token应401_带token应放行() throws Exception {
        assertEquals(401, raw("GET", "/api/conversations", null, null).getStatusCode().value());

        String token = newUser();
        var ok = raw("GET", "/api/conversations", null, token);
        assertEquals(200, ok.getStatusCode().value());
        // 该用户还没有会话：返回空数组
        assertTrue(ok.getBody().trim().startsWith("["));
    }
}
