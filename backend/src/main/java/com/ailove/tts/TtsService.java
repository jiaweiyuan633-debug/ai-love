package com.ailove.tts;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 高拟真人设语音合成（阿里云 DashScope CosyVoice）。
 * 6 个人设音色：温柔御姐/邻家小妹/高冷总裁/清纯男大/中二少年/知心姐姐。
 * 结果按 (人设+文本) 做内存 LRU 缓存，重复朗读零成本。
 */
@Service
public class TtsService {

    /** 人设定义：id 由前端引用，voice 为 CosyVoice 音色，rate/pitch 为基准语速/音调 */
    public record Persona(String id, String label, String emoji, String description,
                          String voice, double rate, double pitch) {
    }

    private static final List<Persona> PERSONAS = List.of(
            new Persona("yujie", "温柔御姐", "🌙", "低柔从容、成熟魅力", "loongbella_v2", 0.95, 0.95),
            new Persona("xiaomei", "邻家小妹", "🍬", "甜美俏皮、元气满满", "longwan_v2", 1.05, 1.1),
            new Persona("ceo", "高冷总裁", "🧊", "磁性低沉、冷静克制", "longcheng_v2", 0.9, 0.9),
            new Persona("nanda", "清纯男大", "🎓", "干净清爽、真诚少年", "longshu_v2", 1.0, 1.05),
            new Persona("zhonger", "中二少年", "⚡", "热血中二、戏剧张力", "longjielidou_v2", 1.1, 1.15),
            new Persona("jiejie", "知心姐姐", "☕", "温暖亲切、治愈抚慰", "longxiaoxia_v2", 0.95, 1.0));

    /** 单次合成文本上限（前端按句合成，正常远小于此值） */
    private static final int MAX_TEXT_LENGTH = 300;
    private static final int CACHE_MAX_ENTRIES = 200;

    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, byte[]> cache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > CACHE_MAX_ENTRIES;
        }
    };

    public TtsService(@Value("${spring.ai.openai.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    public List<Persona> listPersonas() {
        return PERSONAS;
    }

    public byte[] synthesize(String personaId, String text, Double userRate) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文本不能为空");
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            trimmed = trimmed.substring(0, MAX_TEXT_LENGTH);
        }
        Persona persona = PERSONAS.stream()
                .filter(p -> p.id().equals(personaId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知的音色人设"));
        double factor = userRate == null ? 1.0 : clamp(userRate, 0.5, 2.0);
        double rate = clamp(persona.rate() * factor, 0.5, 2.0);
        String cacheKey = persona.id() + '|' + rate + '|' + trimmed;
        synchronized (cache) {
            byte[] cached = cache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        byte[] audio = callCosyVoice(persona, trimmed, rate);
        synchronized (cache) {
            cache.put(cacheKey, audio);
        }
        return audio;
    }

    private byte[] callCosyVoice(Persona persona, String text, double rate) {
        try {
            Map<String, Object> body = Map.of(
                    "model", "cosyvoice-v2",
                    "input", Map.of(
                            "text", text,
                            "voice", persona.voice(),
                            "format", "mp3",
                            "rate", rate,
                            "pitch", persona.pitch()));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://dashscope.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String message = resp.body();
                try {
                    JsonNode node = mapper.readTree(resp.body());
                    message = node.path("message").asText(resp.body());
                } catch (Exception ignored) {
                    // 保留原始响应体
                }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "语音合成失败：" + message);
            }
            JsonNode root = mapper.readTree(resp.body());
            String audioUrl = root.path("output").path("audio").path("url").asText("");
            if (audioUrl.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "语音合成未返回音频地址");
            }
            HttpResponse<byte[]> audioResp = http.send(HttpRequest.newBuilder()
                    .uri(URI.create(audioUrl))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofByteArray());
            if (audioResp.statusCode() != 200 || audioResp.body().length == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "音频下载失败");
            }
            return audioResp.body();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "语音合成服务不可用：" + e.getMessage());
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 供前端渲染的人设列表（不含内部字段）。 */
    public List<Map<String, Object>> personaViews() {
        List<Map<String, Object>> views = new ArrayList<>();
        for (Persona p : PERSONAS) {
            views.add(Map.of(
                    "id", p.id(),
                    "label", p.label(),
                    "emoji", p.emoji(),
                    "description", p.description()));
        }
        return views;
    }
}
