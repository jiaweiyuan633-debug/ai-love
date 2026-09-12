package com.ailove.quote;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 每日情话接口。持久化模式下需要登录（走统一鉴权过滤器），体验模式公开。
 */
@RestController
@RequestMapping("/api/daily-quote")
public class DailyQuoteController {

    private final DailyQuoteService dailyQuoteService;

    public DailyQuoteController(DailyQuoteService dailyQuoteService) {
        this.dailyQuoteService = dailyQuoteService;
    }

    @GetMapping
    public Map<String, Object> quote(@RequestParam(defaultValue = "false") boolean refresh) {
        return Map.of("date", LocalDate.now().toString(), "quote", dailyQuoteService.todayQuote(refresh));
    }
}
