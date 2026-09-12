package com.ailove.it;

import java.util.Map;

import com.ailove.support.BaseIT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * app.payment.mode=off：支付功能整体关闭，下单与模拟支付一律 403。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.payment.mode=off")
@ActiveProfiles("test")
class PaymentModeOffIT extends BaseIT {

    @Test
    void 关闭支付后下单与模拟支付均403() throws Exception {
        String token = newUser();
        assertEquals(403, raw("POST", "/api/membership/orders", Map.of("plan", "month"), token)
                .getStatusCode().value());
        assertEquals(403, raw("POST", "/api/membership/orders/whatever/pay",
                Map.of("credential", "MOCK-TICKET", "amountFen", 1800), token).getStatusCode().value());
        assertFalse(get("/api/membership", token).path("vip").asBoolean());
    }
}
