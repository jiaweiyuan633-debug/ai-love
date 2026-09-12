package com.ailove.auth;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户注册 / 登录 / 个人信息。密码 BCrypt 加密存储，登录签发 JWT（7 天有效）。
 */
@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {

    record AuthRequest(String username, String password, String nickname) {
    }

    record ProfilePatch(String nickname, Boolean memoryEnabled) {
    }

    // 返回给前端的用户视图（绝不包含密码哈希）
    record UserView(long id, String username, String nickname, boolean memoryEnabled) {
    }

    record TokenResponse(String token, UserView user) {
    }

    private final JdbcClient jdbc;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthController(JdbcClient jdbc, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest req) {
        String username = req.username() == null ? "" : req.username().trim();
        String password = req.password() == null ? "" : req.password();
        if (!username.matches("[A-Za-z0-9_\\u4e00-\\u9fa5]{3,32}")) {
            return badRequest("用户名需为 3-32 位字母、数字、下划线或中文");
        }
        if (password.length() < 8 || password.length() > 64) {
            return badRequest("密码长度需在 8-64 位之间");
        }
        if (!password.matches("^(?=.*[A-Za-z])(?=.*\\d).+$")) {
            return badRequest("密码需同时包含字母和数字");
        }
        Long exists = jdbc.sql("SELECT id FROM users WHERE username = ?")
                .param(username)
                .query(Long.class)
                .optional()
                .orElse(null);
        if (exists != null) {
            return badRequest("该用户名已被注册");
        }
        String nickname = (req.nickname() == null || req.nickname().isBlank())
                ? username : req.nickname().trim();
        Long id = jdbc.sql("""
                INSERT INTO users (username, password_hash, nickname)
                VALUES (?, ?, ?) RETURNING id
                """)
                .param(username)
                .param(passwordEncoder.encode(password))
                .param(nickname)
                .query(Long.class)
                .single();
        return ResponseEntity.ok(new TokenResponse(
                jwtUtil.issue(id, username), new UserView(id, username, nickname, true)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        String username = req.username() == null ? "" : req.username().trim();
        Map<String, Object> row = jdbc.sql(
                "SELECT id, password_hash, nickname, memory_enabled FROM users WHERE username = ?")
                .param(username)
                .query((rs, i) -> Map.<String, Object>of(
                        "id", rs.getLong("id"),
                        "password_hash", rs.getString("password_hash"),
                        "nickname", rs.getObject("nickname"),
                        "memory_enabled", rs.getBoolean("memory_enabled")))
                .optional()
                .orElse(null);
        if (row == null || !passwordEncoder.matches(
                req.password() == null ? "" : req.password(), (String) row.get("password_hash"))) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }
        long id = (Long) row.get("id");
        return ResponseEntity.ok(new TokenResponse(jwtUtil.issue(id, username), new UserView(
                id, username, (String) row.get("nickname"), (Boolean) row.get("memory_enabled"))));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return unauthorized();
        }
        return jdbc.sql("SELECT id, username, nickname, memory_enabled FROM users WHERE id = ?")
                .param(userId)
                .query((rs, i) -> ResponseEntity.ok(new UserView(
                        rs.getLong("id"), rs.getString("username"),
                        rs.getString("nickname"), rs.getBoolean("memory_enabled"))))
                .optional()
                .orElseGet(() -> ResponseEntity.status(401).body(null));
    }

    @PatchMapping("/me")
    public ResponseEntity<?> patchMe(@RequestBody ProfilePatch patch, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return unauthorized();
        }
        if (patch.nickname() != null) {
            String nickname = patch.nickname().trim();
            if (nickname.isEmpty() || nickname.length() > 32) {
                return badRequest("昵称需为 1-32 个字符");
            }
            jdbc.sql("UPDATE users SET nickname = ? WHERE id = ?").param(nickname).param(userId).update();
        }
        if (patch.memoryEnabled() != null) {
            jdbc.sql("UPDATE users SET memory_enabled = ? WHERE id = ?")
                    .param(patch.memoryEnabled())
                    .param(userId)
                    .update();
        }
        return me(request);
    }

    private Long currentUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        JwtUtil.UserId user = jwtUtil.parse(auth.substring(7));
        return user == null ? null : user.id();
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private ResponseEntity<Void> unauthorized() {
        return ResponseEntity.status(401).build();
    }
}
