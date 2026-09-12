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

    /** 与 application.yml 中 JWT_SECRET 的开发默认值保持一致（fail-fast 校验用） */
    private static final String KNOWN_DEV_DEFAULT_SECRET =
            "ai-love-dev-secret-change-me-in-production-0123456789";

    @Bean
    public JwtUtil jwtUtil(@Value("${app.jwt.secret}") String secret,
                           @Value("${app.jwt.expire-hours:168}") long expireHours,
                           @Value("${app.security.allow-insecure-jwt:false}") boolean allowInsecureJwt) {
        // fail-fast：持久化模式下默认密钥等于公开可知的值，任何人可伪造任意用户 token，直接拒绝启动
        if (!allowInsecureJwt && KNOWN_DEV_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException("""
                    检测到持久化模式仍在使用内置默认 JWT_SECRET（存在伪造任意用户登录态的风险）。
                    请设置环境变量 JWT_SECRET 为随机长密钥（至少 32 字节，例如 openssl rand -base64 48 生成），
                    或仅限本机开发时显式配置 app.security.allow-insecure-jwt=true 跳过本检查。""");
        }
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
