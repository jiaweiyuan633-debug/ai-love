package com.ailove.mood;

import java.util.Map;

import com.ailove.auth.AuthContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 心情打卡接口：今日状态 + 7 天趋势 + 打卡（角色回应）。 */
@RestController
@RequestMapping("/api/mood")
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class MoodController {

    private final MoodService moodService;

    public MoodController(MoodService moodService) {
        this.moodService = moodService;
    }

    @GetMapping
    public MoodService.MoodStatus status() {
        return moodService.status(currentUserId());
    }

    @PostMapping
    public Map<String, Object> checkIn(@RequestBody Map<String, Object> body,
                                       @RequestParam(defaultValue = "jiejie") String persona) {
        Integer score = body.get("score") instanceof Number n ? n.intValue() : null;
        if (score == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 score");
        }
        String note = (String) body.get("note");
        String reply = moodService.checkIn(currentUserId(), score, note, persona);
        return Map.of("reply", reply, "status", moodService.status(currentUserId()));
    }

    private long currentUserId() {
        Long userId = AuthContext.userId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return userId;
    }
}
