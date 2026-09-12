package com.ailove.auth;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证能力探测：前端据此决定是否显示登录页（体验模式下跳过登录直接使用）。
 * 该端点在持久化关闭时也必须可用，因此不放在需要数据库的 AuthController 里。
 */
@RestController
public class AuthStatusController {

    @Value("${app.persistence.enabled:true}")
    private boolean persistenceEnabled;

    @GetMapping("/auth/status")
    public Map<String, Object> status() {
        return Map.of("enabled", persistenceEnabled);
    }
}
