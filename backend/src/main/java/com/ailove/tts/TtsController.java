package com.ailove.tts;

import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 高拟真人设语音合成接口。
 * 朗读功能才会调用（前端按句合成）；体验模式（无鉴权过滤器）下同样可用。
 */
@RestController
@RequestMapping("/api/tts")
public class TtsController {

    record TtsRequest(String text, String persona, Double rate) {
    }

    private final TtsService ttsService;

    public TtsController(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    /** 可用人设列表（前端渲染卡片用）。 */
    @GetMapping("/voices")
    public List<Map<String, Object>> voices() {
        return ttsService.personaViews();
    }

    /** 合成一段文本的语音，返回 mp3。 */
    @PostMapping
    public ResponseEntity<byte[]> tts(@RequestBody TtsRequest request) {
        byte[] audio = ttsService.synthesize(request.persona(), request.text(), request.rate());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .cacheControl(CacheControl.noStore())
                .body(audio);
    }
}
