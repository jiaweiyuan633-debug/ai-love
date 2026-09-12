package com.ailove.care;

import java.util.Map;

import com.ailove.auth.AuthContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 主动关怀：进入聊天页时拉取角色的"主动来信"。 */
@RestController
@RequestMapping("/api/care")
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class CareController {

    private final CareService careService;

    public CareController(CareService careService) {
        this.careService = careService;
    }

    @GetMapping("/pending")
    public Map<String, Object> pending(@RequestParam(defaultValue = "jiejie") String persona) {
        Long userId = AuthContext.userId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        CareService.CareMessage msg = careService.pending(userId, persona);
        return msg == null ? Map.of() : Map.of("type", msg.type(), "content", msg.content());
    }
}
