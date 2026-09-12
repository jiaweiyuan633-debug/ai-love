package com.ailove.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.beans.factory.annotation.Value;

/**
 * 认证相关 Bean，仅在持久化模式（app.persistence.enabled=true，即有数据库）下装配。
 * 云端体验模式不注册过滤器与用户相关 Bean。
 */
@Configuration
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class AuthConfig {

    @Bean
    public JwtUtil jwtUtil(@Value("${app.jwt.secret}") String secret,
                           @Value("${app.jwt.expire-hours:168}") long expireHours) {
        return new JwtUtil(secret, expireHours);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilter(JwtUtil jwtUtil) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(new JwtAuthFilter(jwtUtil));
        registration.addUrlPatterns("/ai/*", "/api/*", "/knowledge/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
