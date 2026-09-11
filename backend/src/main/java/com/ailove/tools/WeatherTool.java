package com.ailove.tools;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 天气查询工具：基于 Open-Meteo 免费 API（无需 API Key），
 * 供模型按需调用，例如为约会建议查询当天天气。
 */
@Component
public class WeatherTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);

    private static final Map<Integer, String> WEATHER_CODES = Map.ofEntries(
            Map.entry(0, "晴"), Map.entry(1, "大致晴朗"), Map.entry(2, "多云"), Map.entry(3, "阴"),
            Map.entry(45, "雾"), Map.entry(48, "雾凇"),
            Map.entry(51, "小毛毛雨"), Map.entry(53, "毛毛雨"), Map.entry(55, "大毛毛雨"),
            Map.entry(61, "小雨"), Map.entry(63, "中雨"), Map.entry(65, "大雨"),
            Map.entry(66, "冻雨"), Map.entry(67, "强冻雨"),
            Map.entry(71, "小雪"), Map.entry(73, "中雪"), Map.entry(75, "大雪"), Map.entry(77, "雪粒"),
            Map.entry(80, "小阵雨"), Map.entry(81, "阵雨"), Map.entry(82, "强阵雨"),
            Map.entry(85, "小阵雪"), Map.entry(86, "大阵雪"),
            Map.entry(95, "雷阵雨"), Map.entry(96, "雷阵雨伴冰雹"), Map.entry(99, "强雷阵雨伴冰雹"));

    private final RestClient restClient = RestClient.create();

    public record GeoResult(double latitude, double longitude, String city) {
    }

    @Tool(description = "查询指定城市的当前天气，返回天气现象和气温，用于约会安排等场景")
    public String getWeather(@ToolParam(description = "城市名称，例如：北京") String city) {
        try {
            GeoResult geo = geocode(city);
            if (geo == null) {
                return "未找到城市：" + city;
            }
            Map<String, Object> resp = restClient.get()
                    .uri("https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,weather_code&timezone=auto",
                            geo.latitude(), geo.longitude())
                    .retrieve()
                    .body(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> current = (Map<String, Object>) resp.get("current");
            int code = ((Number) current.get("weather_code")).intValue();
            double temp = ((Number) current.get("temperature_2m")).doubleValue();
            String desc = WEATHER_CODES.getOrDefault(code, "天气代码 " + code);
            return String.format("%s当前天气：%s，气温 %.1f°C", geo.city(), desc, temp);
        } catch (Exception e) {
            log.warn("天气查询失败: {}", city, e);
            return "天气查询失败：" + e.getMessage();
        }
    }

    private GeoResult geocode(String city) {
        Map<String, Object> resp = restClient.get()
                .uri("https://geocoding-api.open-meteo.com/v1/search?name={name}&count=1&language=zh&format=json", city)
                .retrieve()
                .body(Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) resp.get("results");
        if (results == null || results.isEmpty()) {
            return null;
        }
        Map<String, Object> first = results.get(0);
        return new GeoResult(
                ((Number) first.get("latitude")).doubleValue(),
                ((Number) first.get("longitude")).doubleValue(),
                (String) first.get("name"));
    }
}
