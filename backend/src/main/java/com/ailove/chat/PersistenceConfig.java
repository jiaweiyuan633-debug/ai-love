package com.ailove.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 持久化模式（app.persistence.enabled=true，本地默认）下装配的会话相关 Bean。
 * 云端体验模式（无数据库）整个不装配。
 */
@Configuration
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class PersistenceConfig {

    @Bean
    public HybridChatMemoryRepository hybridChatMemoryRepository(JdbcClient jdbc) {
        return new HybridChatMemoryRepository(jdbc);
    }

    @Bean
    public ConversationStore conversationStore(JdbcClient jdbc) {
        return new ConversationStore(jdbc);
    }
}
