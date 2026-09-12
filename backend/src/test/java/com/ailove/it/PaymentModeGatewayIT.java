package com.ailove.it;

import java.util.Map;

import com.ailove.support.BaseIT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * app.payment.mode=gateway：真实网关模式。模拟支付端点关闭（403，防自刷 VIP），
 * 下单仍可用；网关异步通知入口在真实网关接入前为 404。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.payment.mode=gateway")
@ActiveProfiles("test")
class PaymentModeGatewayIT extends BaseIT {

    @Test
    void 网关模式下模拟支付被拒_下单可用_通知入口未启用() throws Exception {
        String token = newUser();

        // 下单正常
        var order = post("/api/membership/orders", Map.of("plan", "month"), token);
        assertEquals("pending", order.path("status").asText());
        String orderId = order.path("id").asText();

        // 模拟支付端点关闭，即使凭证金额齐全也 403
        var payResp = raw("POST", "/api/membership/orders/" + orderId + "/pay",
                Map.of("credential", "MOCK-TICKET", "amountFen", 1800), token);
        assertEquals(403, payResp.getStatusCode().value());

        // 异步通知入口：真实网关接入前 404（未启用）
        assertEquals(404, raw("POST", "/payment/notify",
                Map.of("orderId", orderId, "credential", "x"), null).getStatusCode().value());

        assertFalse(get("/api/membership", token).path("vip").asBoolean());
        assertTrue(orderId.length() > 10);
    }
}
