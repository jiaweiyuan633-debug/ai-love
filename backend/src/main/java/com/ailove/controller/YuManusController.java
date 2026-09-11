package com.ailove.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ailove.agent.YuManusAgent;

import reactor.core.publisher.Flux;

/**
 * YuManus 自主规划智能体接口：SSE 推送每一步思考与执行过程。
 */
@RestController
@RequestMapping("/ai/yumanus")
public class YuManusController {

    private final YuManusAgent yuManusAgent;

    public YuManusController(YuManusAgent yuManusAgent) {
        this.yuManusAgent = yuManusAgent;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> run(@RequestParam String task) {
        return yuManusAgent.runStream(task);
    }
}
