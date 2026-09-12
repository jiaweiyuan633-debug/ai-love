package com.ailove.chat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.ailove.auth.AuthContext;
import com.ailove.persona.PersonaCatalog;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

/**
 * 会话管理：列表 / 新建 / 重命名 / 删除 / 历史消息。全部按登录用户隔离。
 */
@RestController
@RequestMapping("/api/conversations")
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class ConversationController {

    private final ConversationStore store;
    private final ChatMemory chatMemory;
    private final SuggestionService suggestionService;

    public ConversationController(ConversationStore store, ChatMemory chatMemory, SuggestionService suggestionService) {
        this.store = store;
        this.chatMemory = chatMemory;
        this.suggestionService = suggestionService;
    }

    @GetMapping
    public List<ConversationStore.Conversation> list() {
        return store.listByUser(currentUserId());
    }

    @PostMapping
    public ConversationStore.Conversation create(@RequestBody(required = false) Map<String, String> body) {
        String title = body == null ? null : body.get("title");
        String persona = body == null ? null : body.get("persona");
        String mode = body == null ? null : body.get("mode");
        PersonaCatalog.Persona p = PersonaCatalog.find(persona);
        String normalizedMode = "companion".equals(mode) ? "companion" : "advisor";
        ConversationStore.Conversation conv = store.create(currentUserId(), title, p.id(), normalizedMode);
        if ("companion".equals(normalizedMode)) {
            // 陪伴模式：落库一条角色开场白，进入会话即可看到（同时进入模型上下文）
            store.addMessage(conv.id(), "assistant", p.companionGreeting());
        }
        return store.find(conv.id()).orElseThrow();
    }

    /** 重命名；也支持在会话还没有消息时改绑角色与模式（陪伴模式补发开场白）。 */
    @PatchMapping("/{id}")
    public ConversationStore.Conversation rename(@PathVariable String id,
                                                 @RequestBody Map<String, String> body) {
        requireOwned(id);
        String title = body.get("title");
        if (title != null && !title.isBlank()) {
            store.rename(id, title.trim().length() > 60 ? title.trim().substring(0, 60) : title.trim());
        }
        String persona = body.get("persona");
        String mode = body.get("mode");
        if (persona != null || mode != null) {
            if (store.countMessages(id) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话已开始，不能更换角色");
            }
            PersonaCatalog.Persona p = PersonaCatalog.find(persona);
            String normalizedMode = "companion".equals(mode) ? "companion" : "advisor";
            store.updatePersonaMode(id, p.id(), normalizedMode);
            if ("companion".equals(normalizedMode) && store.countMessages(id) == 0) {
                store.addMessage(id, "assistant", p.companionGreeting());
            }
        }
        return store.find(id).orElseThrow();
    }

    @GetMapping("/{id}/messages")
    public List<ConversationStore.StoredMessage> messages(@PathVariable String id) {
        requireOwned(id);
        return store.listMessages(id);
    }

    /** 按最后一轮问答生成 3 个追问建议（进程内缓存）。 */
    @PostMapping("/{id}/suggestions")
    public Map<String, Object> suggestions(@PathVariable String id) {
        requireOwned(id);
        return Map.of("suggestions", suggestionService.suggest(id));
    }

    @GetMapping("/search")
    public List<ConversationStore.SearchHit> search(@RequestParam String q) {
        String keyword = q.trim();
        if (keyword.isEmpty()) {
            return List.of();
        }
        return store.search(currentUserId(), keyword, 30);
    }

    /** 导出会话为 Markdown 文件下载。 */
    @GetMapping(value = "/{id}/export", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> export(@PathVariable String id) {
        requireOwned(id);
        ConversationStore.Conversation conversation = store.find(id).orElseThrow();
        List<ConversationStore.StoredMessage> messages = store.listMessages(id);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault());
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(conversation.title()).append("\n\n")
                .append("> 导出自 AI 恋爱大师 · ").append(LocalDate.now())
                .append(" · 共 ").append(messages.size()).append(" 条消息\n\n---\n\n");
        for (ConversationStore.StoredMessage m : messages) {
            sb.append("**").append("user".equals(m.role()) ? "我" : "恋爱大师").append("**（")
                    .append(fmt.format(m.createdAt())).append("）：\n\n")
                    .append(m.content()).append("\n\n---\n\n");
        }
        String filename = UriUtils.encode(conversation.title() + ".md", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(sb.toString());
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
