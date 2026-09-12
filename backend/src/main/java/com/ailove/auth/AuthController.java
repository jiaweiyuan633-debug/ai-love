package com.ailove.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.ailove.chat.HybridChatMemoryRepository;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户注册 / 登录 / 个人信息 / 注销 / 邮箱绑定与找回密码。
 * 密码 BCrypt 加密存储，登录签发 JWT（7 天有效）；验证码只存 SHA-256 摘要。
 */
@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {

    record AuthRequest(String username, String password, String nickname) {
    }

    record ProfilePatch(String nickname, Boolean memoryEnabled) {
    }

    record BindEmailRequest(String email, String code) {
    }

    record ResetRequest(String username, String email, String code, String newPassword) {
    }

    // 返回给前端的用户视图（绝不包含密码哈希）
    record UserView(long id, String username, String nickname, boolean memoryEnabled, String email) {
    }

    record TokenResponse(String token, UserView user) {
    }

    private record CodeRow(String codeHash, Instant expiresAt, int attempts) {
    }

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;

    private final JdbcClient jdbc;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final ObjectProvider<HybridChatMemoryRepository> chatMemoryRepository;
    private final String mailFrom;
    private final SecureRandom random = new SecureRandom();

    public AuthController(JdbcClient jdbc, JwtUtil jwtUtil, PasswordEncoder passwordEncoder,
                          ObjectProvider<JavaMailSender> mailSender,
                          ObjectProvider<HybridChatMemoryRepository> chatMemoryRepository,
                          @Value("${spring.mail.username:noreply@ailove.app}") String mailFrom) {
        this.jdbc = jdbc;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.chatMemoryRepository = chatMemoryRepository;
        this.mailFrom = mailFrom;
    }

    // ================= 注册 / 登录 / 个人信息 =================

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest req) {
        String username = req.username() == null ? "" : req.username().trim();
        String password = req.password() == null ? "" : req.password();
        if (!username.matches("[A-Za-z0-9_\\u4e00-\\u9fa5]{3,32}")) {
            return badRequest("用户名需为 3-32 位字母、数字、下划线或中文");
        }
        if (!isValidPassword(password)) {
            return badRequest("密码需为 8-64 位且同时包含字母和数字");
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
                jwtUtil.issue(id, username), new UserView(id, username, nickname, true, null)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        String username = req.username() == null ? "" : req.username().trim();
        // email 可能为 null，Map.of 不接受 null 值，这里用 HashMap
        Map<String, Object> row = jdbc.sql(
                "SELECT id, password_hash, nickname, memory_enabled, email FROM users WHERE username = ?")
                .param(username)
                .query((rs, i) -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("password_hash", rs.getString("password_hash"));
                    m.put("nickname", rs.getObject("nickname"));
                    m.put("memory_enabled", rs.getBoolean("memory_enabled"));
                    m.put("email", rs.getObject("email"));
                    return m;
                })
                .optional()
                .orElse(null);
        if (row == null || !passwordEncoder.matches(
                req.password() == null ? "" : req.password(), (String) row.get("password_hash"))) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }
        long id = (Long) row.get("id");
        return ResponseEntity.ok(new TokenResponse(jwtUtil.issue(id, username), new UserView(
                id, username, (String) row.get("nickname"), (Boolean) row.get("memory_enabled"),
                (String) row.get("email"))));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return unauthorized();
        }
        return jdbc.sql("SELECT id, username, nickname, memory_enabled, email FROM users WHERE id = ?")
                .param(userId)
                .query((rs, i) -> ResponseEntity.ok(new UserView(
                        rs.getLong("id"), rs.getString("username"),
                        rs.getString("nickname"), rs.getBoolean("memory_enabled"),
                        rs.getObject("email") == null ? null : rs.getString("email"))))
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

    // ================= 注销账号 =================

    /**
     * 注销：删除该用户全部数据（会话/消息/长期记忆/心情/朋友圈与评论/情侣绑定/会员数据），
     * 最后删除账号本身。情侣关系中被绑定的另一方解绑（保留其可重新生成/提交绑定码）。
     * 无状态 JWT 在过期前仍有效，但因账号与数据均已删除，只能看到空数据、无法再登录。
     */
    @DeleteMapping("/me")
    @Transactional
    public ResponseEntity<?> deleteMe(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return unauthorized();
        }
        List<String> convIds = jdbc.sql("SELECT id FROM conversations WHERE user_id = ?")
                .param(userId)
                .query(String.class)
                .list();
        // 清掉进程内的模型上下文缓存（消息表数据随之删除）
        HybridChatMemoryRepository memoryRepo = chatMemoryRepository.getIfAvailable();
        if (memoryRepo != null) {
            convIds.forEach(memoryRepo::deleteByConversationId);
        }
        jdbc.sql("DELETE FROM messages WHERE conversation_id IN (SELECT id FROM conversations WHERE user_id = ?)")
                .param(userId).update();
        jdbc.sql("DELETE FROM conversations WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM user_memories WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM mood_logs WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM moment_comments WHERE moment_id IN (SELECT id FROM moments WHERE user_id = ?)")
                .param(userId).update();
        jdbc.sql("DELETE FROM moments WHERE user_id = ?").param(userId).update();
        // 情侣：主动方删除绑定关系；被绑定方解绑（对方保留账号与记忆）
        jdbc.sql("UPDATE couples SET user_b = NULL WHERE user_b = ?").param(userId).update();
        jdbc.sql("DELETE FROM couples WHERE user_a = ?").param(userId).update();
        jdbc.sql("DELETE FROM memberships WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM membership_orders WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM membership_daily_usage WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM users WHERE id = ?").param(userId).update();
        return ResponseEntity.noContent().build();
    }

    // ================= 邮箱绑定（找回密码的前提） =================

    @PostMapping("/me/email/request")
    public ResponseEntity<?> requestBindEmail(@RequestBody BindEmailRequest req, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return unauthorized();
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            return serviceUnavailable("邮件服务未配置，请联系管理员或稍后再试");
        }
        String email = req.email() == null ? "" : req.email().trim().toLowerCase();
        if (!isValidEmail(email)) {
            return badRequest("邮箱格式不正确");
        }
        Long taken = jdbc.sql("SELECT id FROM users WHERE email = ? AND id <> ?")
                .param(email).param(userId)
                .query(Long.class).optional().orElse(null);
        if (taken != null) {
            return badRequest("该邮箱已被其他账号绑定");
        }
        String code = issueCode(email, "bind");
        return sendCodeOrError(sender, email, code, true);
    }

    @PostMapping("/me/email/confirm")
    public ResponseEntity<?> confirmBindEmail(@RequestBody BindEmailRequest req, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return unauthorized();
        }
        String email = req.email() == null ? "" : req.email().trim().toLowerCase();
        Long taken = jdbc.sql("SELECT id FROM users WHERE email = ? AND id <> ?")
                .param(email).param(userId)
                .query(Long.class).optional().orElse(null);
        if (taken != null) {
            return badRequest("该邮箱已被其他账号绑定");
        }
        String error = verifyAndConsumeCode(email, "bind", req.code());
        if (error != null) {
            return badRequest(error);
        }
        jdbc.sql("UPDATE users SET email = ? WHERE id = ?").param(email).param(userId).update();
        return me(request);
    }

    // ================= 找回密码 =================

    /** 始终返回成功文案（不暴露用户名/邮箱是否匹配），仅匹配时才真正发码。 */
    @PostMapping("/password/reset/request")
    public ResponseEntity<?> requestPasswordReset(@RequestBody ResetRequest req) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            return serviceUnavailable("邮件服务未配置，请联系管理员或稍后再试");
        }
        String username = req.username() == null ? "" : req.username().trim();
        String email = req.email() == null ? "" : req.email().trim().toLowerCase();
        Long userId = jdbc.sql("SELECT id FROM users WHERE username = ? AND email = ?")
                .param(username).param(email)
                .query(Long.class).optional().orElse(null);
        if (userId != null) {
            String code = issueCode(email, "reset");
            ResponseEntity<?> sendResult = sendCodeOrError(sender, email, code, false);
            if (sendResult.getStatusCode().isError()) {
                return sendResult;
            }
        }
        return ResponseEntity.ok(Map.of("message", "如果该邮箱已绑定此账号，验证码已发送，请查收（含垃圾邮件箱）"));
    }

    @PostMapping("/password/reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@RequestBody ResetRequest req) {
        String username = req.username() == null ? "" : req.username().trim();
        String email = req.email() == null ? "" : req.email().trim().toLowerCase();
        if (!isValidPassword(req.newPassword() == null ? "" : req.newPassword())) {
            return badRequest("新密码需为 8-64 位且同时包含字母和数字");
        }
        Long userId = jdbc.sql("SELECT id FROM users WHERE username = ? AND email = ?")
                .param(username).param(email)
                .query(Long.class).optional().orElse(null);
        if (userId == null) {
            return badRequest("验证码错误或已过期");
        }
        String error = verifyAndConsumeCode(email, "reset", req.code());
        if (error != null) {
            return badRequest(error);
        }
        jdbc.sql("UPDATE users SET password_hash = ? WHERE id = ?")
                .param(passwordEncoder.encode(req.newPassword()))
                .param(userId)
                .update();
        return ResponseEntity.ok(Map.of("message", "密码已重置，请使用新密码登录"));
    }

    // ================= 内部工具 =================

    private boolean isValidPassword(String password) {
        return password.length() >= 8 && password.length() <= 64
                && password.matches("^(?=.*[A-Za-z])(?=.*\\d).+$");
    }

    private static boolean isValidEmail(String email) {
        return email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") && email.length() <= 120;
    }

    /** 生成 6 位验证码，SHA-256 摘要入库（按 email+purpose 覆盖），返回明文码。 */
    private String issueCode(String email, String purpose) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        jdbc.sql("""
                INSERT INTO auth_codes(email, purpose, code_hash, expires_at, attempts)
                VALUES (?, ?, ?, ?, 0)
                ON CONFLICT (email, purpose) DO UPDATE SET
                    code_hash = EXCLUDED.code_hash, expires_at = EXCLUDED.expires_at, attempts = 0
                """)
                .param(email)
                .param(purpose)
                .param(sha256(code))
                .param(Timestamp.from(Instant.now().plus(CODE_TTL)))
                .update();
        return code;
    }

    /** 校验并消费验证码；返回错误文案，null = 通过。错误累计超过上限后作废。 */
    private String verifyAndConsumeCode(String email, String purpose, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return "验证码格式不正确";
        }
        CodeRow row = jdbc.sql("SELECT code_hash, expires_at, attempts FROM auth_codes WHERE email = ? AND purpose = ?")
                .param(email).param(purpose)
                .query((rs, i) -> new CodeRow(rs.getString("code_hash"),
                        rs.getTimestamp("expires_at").toInstant(), rs.getInt("attempts")))
                .optional()
                .orElse(null);
        if (row == null) {
            return "请先获取验证码";
        }
        if (row.expiresAt().isBefore(Instant.now()) || row.attempts() >= MAX_ATTEMPTS) {
            jdbc.sql("DELETE FROM auth_codes WHERE email = ? AND purpose = ?").param(email).param(purpose).update();
            return "验证码已过期，请重新获取";
        }
        if (!row.codeHash().equals(sha256(code))) {
            jdbc.sql("UPDATE auth_codes SET attempts = attempts + 1 WHERE email = ? AND purpose = ?")
                    .param(email).param(purpose).update();
            return "验证码错误";
        }
        jdbc.sql("DELETE FROM auth_codes WHERE email = ? AND purpose = ?").param(email).param(purpose).update();
        return null;
    }

    private ResponseEntity<?> sendCodeOrError(JavaMailSender sender, String to, String code, boolean bind) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject("AI 恋爱大师 " + (bind ? "邮箱绑定" : "密码重置") + "验证码");
        message.setText("""
                你的验证码是：%s

                %d 分钟内有效。若非本人操作，请忽略本邮件。
                """.formatted(code, CODE_TTL.toMinutes()));
        try {
            sender.send(message);
            return ResponseEntity.ok(Map.of("message", "验证码已发送，请查收（含垃圾邮件箱）"));
        } catch (MailException e) {
            return ResponseEntity.status(502).body(Map.of("error", "验证码发送失败，请稍后重试"));
        }
    }

    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
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

    private ResponseEntity<Map<String, String>> serviceUnavailable(String message) {
        return ResponseEntity.status(503).body(Map.of("error", message));
    }

    private ResponseEntity<Void> unauthorized() {
        return ResponseEntity.status(401).build();
    }
}
