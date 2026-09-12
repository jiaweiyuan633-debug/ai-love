package com.ailove.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS：Web 同源部署用不到（来源列表为空时不放行任何跨域）；
 * Tauri 桌面/移动端壳（tauri://localhost / http://tauri.localhost）直接调用云端接口时需要。
 * 额外来源用 APP_CORS_ALLOWED_ORIGINS（逗号分隔）追加。
 */
@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(
            @Value("${app.cors.allowed-origins:http://tauri.localhost,tauri://localhost}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(config::addAllowedOrigin);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        // 先于 JwtAuthFilter（order 1）：预检请求不带 token，也必须先拿到 CORS 头
        registration.setOrder(0);
        return registration;
    }
}
