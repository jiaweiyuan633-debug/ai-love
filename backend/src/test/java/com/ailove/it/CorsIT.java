package com.ailove.it;

import com.ailove.support.BaseIT;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CORS 预检集成测试：Tauri 壳来源可以预检通过并拿到放行头；普通浏览器来源不放大。
 */
class CorsIT extends BaseIT {

    private ResponseEntity<String> preflight(String origin) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", origin);
        headers.set("Access-Control-Request-Method", "GET");
        return rest.exchange("/api/personas", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    }

    @Test
    void tauri壳来源预检放行() {
        ResponseEntity<String> resp = preflight("http://tauri.localhost");
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("http://tauri.localhost",
                resp.getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    @Test
    void tauri桌面来源预检放行() {
        ResponseEntity<String> resp = preflight("tauri://localhost");
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("tauri://localhost",
                resp.getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    @Test
    void 未知来源不返回放行头() {
        ResponseEntity<String> resp = preflight("https://evil.example.com");
        assertEquals(null, resp.getHeaders().getFirst("Access-Control-Allow-Origin"));
    }
}
