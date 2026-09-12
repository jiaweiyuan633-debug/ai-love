package com.ailove.it;

import java.util.Map;

import com.ailove.support.BaseIT;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话生命周期：角色/模式绑定、陪伴开场白落库、改绑保护、重命名与删除、AI 生成隐式标识。
 */
class ConversationFlowIT extends BaseIT {

    @org.springframework.beans.factory.annotation.Autowired
    private com.ailove.chat.ConversationStore conversationStore;

    @Test
    void 助手消息带AI生成标识_用户消息不带_导出含标识元数据() throws Exception {
        String token = newUser();
        var created = post("/api/conversations", Map.of("persona", "jiejie", "mode", "advisor"), token);
        String convId = created.path("id").asText();

        // 直接经仓储补一轮问答（模型调用已打桩，不触网）
        conversationStore.addMessage(convId, "user", "帮我分析一下她的想法");
        conversationStore.addMessage(convId, "assistant", "她的想法可以从三个角度看……");

        var messages = get("/api/conversations/" + convId + "/messages", token);
        assertEquals(2, messages.size());
        assertEquals(false, messages.get(0).path("aiGenerated").asBoolean());
        assertEquals(true, messages.get(1).path("aiGenerated").asBoolean());

        // 导出文件携带隐式标识（头部队列元数据 + 助手消息逐条标注）
        var export = raw("GET", "/api/conversations/" + convId + "/export", null, token);
        assertEquals(200, export.getStatusCode().value());
        String markdown = export.getBody();
        assertTrue(markdown.contains("ai-generated: true"));
        assertTrue(markdown.contains("内容由人工智能生成"));
    }

    @Test
    void 陪伴会话创建_开场白落库_有消息后改绑被拒() throws Exception {
        String token = newUser();

        var created = post("/api/conversations",
                Map.of("title", "新对话", "persona", "ceo", "mode", "companion"), token);
        assertEquals("ceo", created.path("persona").asText());
        assertEquals("companion", created.path("mode").asText());
        String convId = created.path("id").asText();
        assertFalse(convId.isBlank());

        // 开场白已由服务端落库
        var messages = get("/api/conversations/" + convId + "/messages", token);
        assertEquals(1, messages.size());
        assertEquals("assistant", messages.get(0).path("role").asText());
        assertFalse(messages.get(0).path("content").asText().isBlank());

        // 已有消息的会话不能更换角色
        assertEquals(400, raw("PATCH", "/api/conversations/" + convId,
                Map.of("persona", "yujie", "mode", "companion"), token).getStatusCode().value());

        // 重命名不受影响（从列表确认标题）
        assertEquals(200, raw("PATCH", "/api/conversations/" + convId,
                Map.of("title", "总裁陪聊"), token).getStatusCode().value());
        String renamed = null;
        for (var c : get("/api/conversations", token)) {
            if (convId.equals(c.path("id").asText())) {
                renamed = c.path("title").asText();
            }
        }
        assertEquals("总裁陪聊", renamed);
    }

    @Test
    void 空会话可改绑角色_改绑陪伴后补开场白_删除后会话消失() throws Exception {
        String token = newUser();

        // 顾问模式创建：无消息
        var created = post("/api/conversations", Map.of("persona", "jiejie", "mode", "advisor"), token);
        String convId = created.path("id").asText();
        assertEquals(0, get("/api/conversations/" + convId + "/messages", token).size());

        // 空会话改绑为陪伴模式 → 成功并补发开场白
        var patched = patch("/api/conversations/" + convId,
                Map.of("persona", "yujie", "mode", "companion"), token);
        assertEquals("yujie", patched.path("persona").asText());
        assertEquals("companion", patched.path("mode").asText());
        assertEquals(1, get("/api/conversations/" + convId + "/messages", token).size());

        // 删除后列表不再包含
        assertEquals(200, raw("DELETE", "/api/conversations/" + convId, null, token).getStatusCode().value());
        var list = get("/api/conversations", token);
        boolean stillThere = false;
        for (var c : list) {
            if (convId.equals(c.path("id").asText())) {
                stillThere = true;
            }
        }
        assertFalse(stillThere);
    }
}
