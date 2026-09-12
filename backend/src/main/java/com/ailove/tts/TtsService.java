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

import com.ailove.persona.PersonaCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 高拟真人设语音合成（阿里云 DashScope CosyVoice）。
 * 音色跟随 PersonaCatalog 的 6 个统一角色；结果按 (人设+文本) 做内存 LRU 缓存，重复朗读零成本。
 * 下发音频均写入 AI 生成隐式标识（ID3 元数据，见 {@link AiAudioLabel}）。
 */
@Service
public class TtsService {

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

    public List<PersonaCatalog.Persona> listPersonas() {
        return PersonaCatalog.ALL;
    }

    public byte[] synthesize(String personaId, String text, Double userRate) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文本不能为空");
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            trimmed = trimmed.substring(0, MAX_TEXT_LENGTH);
        }
        PersonaCatalog.Persona persona = PersonaCatalog.find(personaId);
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
        // 写入 AI 生成隐式标识（ID3 元数据）后再缓存，标识随音频文件持续存在
        byte[] labeled = AiAudioLabel.tagMp3(audio);
        synchronized (cache) {
            cache.put(cacheKey, labeled);
        }
        return labeled;
    }

    private byte[] callCosyVoice(PersonaCatalog.Persona persona, String text, double rate) {
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
        for (PersonaCatalog.Persona p : PersonaCatalog.ALL) {
            views.add(Map.of(
                    "id", p.id(),
                    "label", p.name(),
                    "emoji", p.emoji(),
                    "description", p.tagline()));
        }
        return views;
    }
}
