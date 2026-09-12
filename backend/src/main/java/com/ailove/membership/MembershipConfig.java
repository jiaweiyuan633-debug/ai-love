package com.ailove.membership;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 会员模块装配：两种部署模式都需要（云端体验模式为进程内存储），
 * 因此不挂在 PersistenceConfig 下，按 JdbcClient 是否存在自动选择实现。
 */
@Configuration
public class MembershipConfig {

    @Bean
    public MembershipStore membershipStore(ObjectProvider<JdbcClient> jdbc) {
        JdbcClient client = jdbc.getIfAvailable();
        return client != null ? new MembershipStore(client) : new MembershipStore();
    }

    @Bean
    public FilterRegistrationBean<DailyQuotaFilter> dailyQuotaFilter(
            MembershipStore store,
            @Value("${app.membership.daily-free-limit:20}") int dailyFreeLimit) {
        FilterRegistrationBean<DailyQuotaFilter> registration =
                new FilterRegistrationBean<>(new DailyQuotaFilter(store, dailyFreeLimit));
        registration.addUrlPatterns("/ai/*", "/api/*");
        registration.setOrder(3);
        return registration;
    }
}
