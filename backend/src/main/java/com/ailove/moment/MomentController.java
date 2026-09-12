package com.ailove.moment;

import java.util.List;
import java.util.Map;

import com.ailove.auth.AuthContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** AI 朋友圈接口：动态流 / 点赞 / 评论（AI 异步回复）。 */
@RestController
@RequestMapping("/api/moments")
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class MomentController {

    private final MomentService momentService;

    public MomentController(MomentService momentService) {
        this.momentService = momentService;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "jiejie") String persona) {
        return momentService.list(currentUserId(), persona);
    }

    @PostMapping("/{id}/like")
    public Map<String, Object> like(@PathVariable long id) {
        return Map.of("liked", momentService.toggleLike(currentUserId(), id));
    }

    @PostMapping("/{id}/comments")
    public Map<String, Object> comment(@PathVariable long id, @RequestBody Map<String, String> body) {
        try {
            return Map.of("comments", momentService.comment(currentUserId(), id, body.get("content")));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private long currentUserId() {
        Long userId = AuthContext.userId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return userId;
    }
}
