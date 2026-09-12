package com.ailove.chat;

import java.util.List;
import java.util.Map;

import com.ailove.auth.AuthContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 长期记忆管理：查看、删除单条、清空。
 */
@RestController
@RequestMapping("/api/memory")
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public Map<String, Object> list() {
        long userId = currentUserId();
        return Map.of(
                "enabled", memoryService.memoryEnabled(userId),
                "items", memoryService.list(userId));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        boolean deleted = memoryService.delete(currentUserId(), id);
        return Map.of("deleted", deleted);
    }

    @DeleteMapping
    public Map<String, Object> clear() {
        memoryService.clear(currentUserId());
        return Map.of("cleared", true);
    }

    private long currentUserId() {
        Long userId = AuthContext.userId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return userId;
    }
}
