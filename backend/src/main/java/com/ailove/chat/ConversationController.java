package com.ailove.chat;

import java.util.List;
import java.util.Map;

import com.ailove.auth.AuthContext;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 会话管理：列表 / 新建 / 重命名 / 删除 / 历史消息。全部按登录用户隔离。
 */
@RestController
@RequestMapping("/api/conversations")
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class ConversationController {

    private final ConversationStore store;
    private final ChatMemory chatMemory;

    public ConversationController(ConversationStore store, ChatMemory chatMemory) {
        this.store = store;
        this.chatMemory = chatMemory;
    }

    @GetMapping
    public List<ConversationStore.Conversation> list() {
        return store.listByUser(currentUserId());
    }

    @PostMapping
    public ConversationStore.Conversation create(@RequestBody(required = false) Map<String, String> body) {
        String title = body == null ? null : body.get("title");
        return store.create(currentUserId(), title);
    }

    @GetMapping("/{id}/messages")
    public List<ConversationStore.StoredMessage> messages(@PathVariable String id) {
        requireOwned(id);
        return store.listMessages(id);
    }

    @PatchMapping("/{id}")
    public ConversationStore.Conversation rename(@PathVariable String id,
                                                 @RequestBody Map<String, String> body) {
        requireOwned(id);
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题不能为空");
        }
        store.rename(id, title.trim().length() > 60 ? title.trim().substring(0, 60) : title.trim());
        return store.find(id).orElseThrow();
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        requireOwned(id);
        store.delete(id);
        // 同时清掉模型上下文缓存，下次同名会话不会串旧内容
        chatMemory.clear(id);
        return Map.of("deleted", id);
    }

    private void requireOwned(String conversationId) {
        ConversationStore.Conversation conversation = store.find(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        if (conversation.userId() != currentUserId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该会话");
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
