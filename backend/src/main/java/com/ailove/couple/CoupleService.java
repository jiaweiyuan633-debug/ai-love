package com.ailove.couple;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 情侣绑定：一方生成绑定码、另一方提交完成绑定。
 * 绑定后双方的 system prompt 会注入伴侣信息（纪念日/在一起天数）与伴侣的长期记忆（共享记忆）。
 */
@Service
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class CoupleService {

    /** 前端展示的状态视图 */
    public record CoupleStatus(boolean bound, boolean pending, String code, String partnerNickname,
                               LocalDate anniversaryDate, Long daysTogether, Long daysToAnniversary) {
    }

    private record Couple(long id, String code, long userA, Long userB, LocalDate anniversaryDate) {
    }

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SplittableRandom random = new SplittableRandom();

    private final JdbcClient jdbc;

    public CoupleService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- 绑定流程 ----------

    public String generateCode(long userId) {
        Couple existing = findByUser(userId).orElse(null);
        if (existing != null) {
            if (existing.userB() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "你们已经绑定过了，如需换绑请先解除绑定");
            }
            return existing.code(); // 已有待使用的绑定码
        }
        String code = randomCode();
        jdbc.sql("INSERT INTO couples (code, user_a) VALUES (?, ?)")
                .param(code)
                .param(userId)
                .update();
        return code;
    }

    public CoupleStatus bind(long userId, String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        if (!normalized.matches("[A-Z2-9]{6,12}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "绑定码格式不正确");
        }
        if (findByUser(userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "你已生成过绑定码或已绑定，如需换绑请先解除");
        }
        Couple target = jdbc.sql(
                        "SELECT id, code, user_a, user_b, anniversary_date FROM couples WHERE code = ?")
                .param(normalized)
                .query(this::mapCouple)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "绑定码不存在，请让对方重新生成"));
        if (target.userB() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该绑定码已被使用");
        }
        if (target.userA() == userId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能和自己绑定哦");
        }
        int updated = jdbc.sql("UPDATE couples SET user_b = ? WHERE id = ? AND user_b IS NULL")
                .param(userId)
                .param(target.id())
                .update();
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "绑定失败：绑定码刚刚被别人使用");
        }
        return status(userId);
    }

    public void setAnniversary(long userId, LocalDate date) {
        Couple couple = requireBound(userId);
        jdbc.sql("UPDATE couples SET anniversary_date = ? WHERE id = ?")
                .param(date)
                .param(couple.id())
                .update();
    }

    public void unbind(long userId) {
        Couple couple = findByUser(userId).orElse(null);
        if (couple == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "你们还没有绑定");
        }
        jdbc.sql("DELETE FROM couples WHERE id = ?").param(couple.id()).update();
    }

    // ---------- 状态与注入 ----------

    public CoupleStatus status(long userId) {
        Couple couple = findByUser(userId).orElse(null);
        if (couple == null) {
            return new CoupleStatus(false, false, null, null, null, null, null);
        }
        if (couple.userB() == null) {
            return new CoupleStatus(false, true, couple.code(), null, null, null, null);
        }
        long partnerId = couple.userA() == userId ? couple.userB() : couple.userA();
        String nickname = nicknameOf(partnerId);
        LocalDate anniversary = couple.anniversaryDate();
        Long daysTogether = anniversary == null ? null : ChronoUnit.DAYS.between(anniversary, LocalDate.now()) + 1;
        Long daysTo = anniversary == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), nextAnniversary(anniversary));
        return new CoupleStatus(true, false, null, nickname, anniversary, daysTogether, daysTo);
    }

    /** 生成注入 system prompt 的情侣信息块（未绑定/未完成返回空串）。 */
    public String coupleBlock(long userId) {
        Couple couple = findByUser(userId).filter(c -> c.userB() != null).orElse(null);
        if (couple == null) {
            return "";
        }
        long partnerId = couple.userA() == userId ? couple.userB() : couple.userA();
        String nickname = nicknameOf(partnerId);
        StringBuilder sb = new StringBuilder("# 情侣绑定信息\n");
        sb.append("用户已与「").append(nickname).append("」绑定为本产品中的情侣关系，你们都在使用这个恋爱顾问。");
        if (couple.anniversaryDate() != null) {
            long days = ChronoUnit.DAYS.between(couple.anniversaryDate(), LocalDate.now()) + 1;
            long until = ChronoUnit.DAYS.between(LocalDate.now(), nextAnniversary(couple.anniversaryDate()));
            sb.append("恋爱纪念日是 ").append(couple.anniversaryDate()).append("，今天是在一起的第 ")
                    .append(days).append(" 天");
            if (until == 0) {
                sb.append("。【今天就是你们的纪念日！记得自然地送上祝福，并建议一些庆祝方式】");
            } else if (until <= 7) {
                sb.append("。【提醒：").append(until).append(" 天后就是纪念日，可以关心一下 TA 的庆祝计划】");
            }
            sb.append("。");
        }
        List<String> partnerMemories = jdbc.sql(
                        "SELECT content FROM user_memories WHERE user_id = ? ORDER BY id DESC LIMIT 10")
                .param(partnerId)
                .query(String.class)
                .list();
        if (!partnerMemories.isEmpty()) {
            sb.append("\n伴侣「").append(nickname).append("」的记忆（回答时可以自然运用，例如 TA 喜欢什么、重要的日子等）：\n");
            partnerMemories.forEach(m -> sb.append("- ").append(m).append("\n"));
        }
        return sb.toString().trim();
    }

    // ---------- 内部 ----------

    private Optional<Couple> findByUser(long userId) {
        return jdbc.sql("""
                        SELECT id, code, user_a, user_b, anniversary_date
                        FROM couples WHERE user_a = ? OR user_b = ?
                        ORDER BY id DESC LIMIT 1
                        """)
                .param(userId)
                .param(userId)
                .query(this::mapCouple)
                .optional();
    }

    private Couple requireBound(long userId) {
        Couple couple = findByUser(userId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "你们还没有绑定"));
        if (couple.userB() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先完成绑定");
        }
        return couple;
    }

    private String nicknameOf(long userId) {
        return jdbc.sql("SELECT nickname FROM users WHERE id = ?")
                .param(userId)
                .query(String.class)
                .optional()
                .orElse("伴侣");
    }

    private LocalDate nextAnniversary(LocalDate anniversary) {
        LocalDate today = LocalDate.now();
        LocalDate thisYear;
        try {
            thisYear = anniversary.withYear(today.getYear());
        } catch (Exception e) { // 2 月 29 日在平年会抛异常，退到 2 月 28 日
            thisYear = LocalDate.of(today.getYear(), 2, 28);
        }
        if (thisYear.isBefore(today)) {
            try {
                return anniversary.withYear(today.getYear() + 1);
            } catch (Exception e) {
                return LocalDate.of(today.getYear() + 1, 2, 28);
            }
        }
        return thisYear; // 今天是纪念日时返回今天
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private Couple mapCouple(java.sql.ResultSet rs, int i) {
        try {
            java.sql.Date date = rs.getDate("anniversary_date");
            return new Couple(rs.getLong("id"), rs.getString("code"), rs.getLong("user_a"),
                    rs.getObject("user_b") == null ? null : rs.getLong("user_b"),
                    date == null ? null : date.toLocalDate());
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
