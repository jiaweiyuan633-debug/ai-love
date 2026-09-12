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

    /**
     * 支付网关按 app.payment.mode 装配：mock=模拟收银台（默认，演示闭环）/
     * gateway=真实网关（接入时替换此处的 null 为具体实现，见 docs/COMMERCIAL.md）/ off=关闭。
     * 非 mock 模式下返回 null（NullBean），模拟支付端点在 Controller 中被 403 拦截。
     */
    @Bean
    public PaymentGateway paymentGateway(@Value("${app.payment.mode:mock}") String mode) {
        return "mock".equals(mode) ? new MockGateway() : null;
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
