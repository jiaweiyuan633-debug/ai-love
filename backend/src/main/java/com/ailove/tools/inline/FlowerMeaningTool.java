package com.ailove.tools.inline;

import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 花语查询 MCP 工具：送花是恋爱的经典场景，模型可调用此工具给出花束建议。
 */
@Component
@ConditionalOnProperty(name = "app.inline-love-tools.enabled", havingValue = "true")
public class FlowerMeaningTool {

    private static final Map<String, String> FLOWER_MEANINGS = Map.ofEntries(
            Map.entry("玫瑰", "热恋、真爱；红玫瑰=炽热爱恋，粉玫瑰=初恋与心动，白玫瑰=纯洁的爱"),
            Map.entry("郁金香", "体贴、优雅的爱；粉色郁金香代表永远的幸福"),
            Map.entry("向日葵", "沉默的爱、忠诚与阳光，适合表白或鼓励对方"),
            Map.entry("百合", "纯洁、百年好合，适合纪念日与婚礼场合"),
            Map.entry("满天星", "甘愿做配角的守护，常与玫瑰搭配表示真心陪伴"),
            Map.entry("康乃馨", "温馨的关怀，也可用于表达对恋人的体贴"),
            Map.entry("薰衣草", "等待爱情、心心相印"),
            Map.entry("栀子花", "永恒的爱与约定，坚强而持久"),
            Map.entry("桔梗", "真诚不变的爱，适合异地恋表达思念"),
            Map.entry("绣球", "希望、团圆，寓意感情圆满"));

    @Tool(description = "查询常见花束的花语与送花寓意，帮助用户挑选合适的花")
    public String getFlowerMeaning(@ToolParam(description = "花的名称，例如：玫瑰") String flower) {
        String meaning = FLOWER_MEANINGS.get(flower);
        if (meaning != null) {
            return flower + "的花语：" + meaning;
        }
        return "暂未收录「" + flower + "」的花语，常见可选：玫瑰、郁金香、向日葵、百合、满天星、康乃馨、薰衣草、栀子花、桔梗、绣球。";
    }
}
