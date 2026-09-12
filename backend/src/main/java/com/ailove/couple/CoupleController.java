package com.ailove.couple;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

import com.ailove.auth.AuthContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 情侣绑定接口：状态 / 生成绑定码 / 绑定 / 设置纪念日 / 解绑。
 */
@RestController
@RequestMapping("/api/couple")
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class CoupleController {

    private final CoupleService coupleService;

    public CoupleController(CoupleService coupleService) {
        this.coupleService = coupleService;
    }

    @GetMapping
    public CoupleService.CoupleStatus status() {
        return coupleService.status(currentUserId());
    }

    @PostMapping("/code")
    public Map<String, String> generateCode() {
        return Map.of("code", coupleService.generateCode(currentUserId()));
    }

    @PostMapping("/bind")
    public CoupleService.CoupleStatus bind(@RequestBody Map<String, String> body) {
        return coupleService.bind(currentUserId(), body.get("code"));
    }

    @PatchMapping
    public CoupleService.CoupleStatus setAnniversary(@RequestBody Map<String, String> body) {
        String date = body.get("anniversaryDate");
        if (date == null || date.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择纪念日日期");
        }
        try {
            coupleService.setAnniversary(currentUserId(), LocalDate.parse(date));
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日期格式不正确");
        }
        return coupleService.status(currentUserId());
    }

    @DeleteMapping
    public Map<String, Object> unbind() {
        coupleService.unbind(currentUserId());
        return Map.of("unbound", true);
    }

    private long currentUserId() {
        Long userId = AuthContext.userId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return userId;
    }
}
