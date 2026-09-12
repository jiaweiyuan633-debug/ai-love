package com.ailove.quote;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 每日情话：一天一句。首次请求用 qwen-turbo 生成并按日期缓存（无数据库时每次实时生成），
 * ?refresh=true 可重新生成并覆盖当天缓存。
 */
@Service
public class DailyQuoteService {

    private final ChatClient quoteClient;
    private final ObjectProvider<JdbcClient> jdbcProvider;

    public DailyQuoteService(ChatClient.Builder builder, ObjectProvider<JdbcClient> jdbcProvider) {
        this.quoteClient = builder
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-turbo").temperature(1.0).build())
                .build();
        this.jdbcProvider = jdbcProvider;
    }

    public String todayQuote(boolean refresh) {
        LocalDate today = LocalDate.now();
        JdbcClient jdbc = jdbcProvider.getIfAvailable();
        if (jdbc != null && !refresh) {
            Optional<String> cached = jdbc.sql("SELECT content FROM daily_quotes WHERE qdate = ?")
                    .param(today)
                    .query(String.class)
                    .optional();
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        String quote = generate();
        if (jdbc != null) {
            try {
                jdbc.sql("""
                                INSERT INTO daily_quotes (qdate, content) VALUES (?, ?)
                                ON CONFLICT (qdate) DO UPDATE SET content = EXCLUDED.content
                                """)
                        .param(today)
                        .param(quote)
                        .update();
            } catch (Exception ignored) {
                // 缓存失败不影响返回
            }
        }
        return quote;
    }

    private String generate() {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String quote = quoteClient.prompt()
                        .user("""
                                写一句原创的中文情话，甜而不腻、有画面感，不超过 40 个字。
                                只输出情话本身，不要引号、不要解释。
                                """)
                        .call()
                        .content();
                if (quote != null && !quote.isBlank()) {
                    return quote.replaceAll("[\\r\\n]", "").trim();
                }
            } catch (Exception ignored) {
                // 重试
            }
        }
        return "遇见你之后，连风都变得温柔了。";
    }
}
