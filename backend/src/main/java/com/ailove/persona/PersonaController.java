package com.ailove.persona;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 角色目录接口：供前端角色选择卡片渲染（含双模式开场白）。 */
@RestController
@RequestMapping("/api/personas")
public class PersonaController {

    @GetMapping
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> views = new ArrayList<>();
        for (PersonaCatalog.Persona p : PersonaCatalog.ALL) {
            views.add(Map.of(
                    "id", p.id(),
                    "name", p.name(),
                    "emoji", p.emoji(),
                    "tagline", p.tagline(),
                    "color", p.color(),
                    "advisorGreeting", p.advisorGreeting(),
                    "companionGreeting", p.companionGreeting()));
        }
        return views;
    }
}
