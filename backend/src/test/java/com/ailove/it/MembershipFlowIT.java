package com.ailove.it;

import java.util.HashMap;
import java.util.Map;

import com.ailove.support.BaseIT;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会员订阅闭环集成测试：套餐目录 → 状态查询 → 下单 → 模拟支付（凭证+金额校验）激活 VIP →
 * 重复支付幂等 → 续费顺延有效期 → 校验失败不得入账。
 */
class MembershipFlowIT extends BaseIT {

    @Test
    void 未登录访问会员接口应401() throws Exception {
        assertEquals(401, raw("GET", "/api/membership", null, null).getStatusCode().value());
        assertEquals(401, raw("POST", "/api/membership/orders", Map.of("plan", "month"), null).getStatusCode().value());
    }

    @Test
    void 套餐目录含三档且金额为分() throws Exception {
        var plans = get("/api/membership/plans", newUser());
        assertEquals(3, plans.size());
        assertEquals("month", plans.get(0).path("id").asText());
        assertEquals(1800, plans.get(0).path("priceFen").asLong());
        assertEquals(15800, plans.get(2).path("priceFen").asLong());
        assertEquals(365, plans.get(2).path("days").asInt());
    }

    @Test
    void 新用户免费态_下单支付激活VIP_重复支付幂等() throws Exception {
        String token = newUser();

        // 免费态
        var before = get("/api/membership", token);
        assertFalse(before.path("vip").asBoolean());
        assertEquals(20, before.path("dailyLimit").asInt());
        assertEquals(0, before.path("dailyUsed").asInt());

        // 下单（未知套餐 400）
        assertEquals(400, raw("POST", "/api/membership/orders", Map.of("plan", "diamond"), token).getStatusCode().value());
        var order = post("/api/membership/orders", Map.of("plan", "month"), token);
        assertEquals("pending", order.path("status").asText());
        assertEquals(1800, order.path("priceFen").asLong());
        String orderId = order.path("id").asText();

        // 支付激活（模拟收银台：凭证 + 金额必须与订单一致）
        var afterPay = objectMapperRead(pay(token, orderId, 1800L, "MOCK-TICKET"));
        assertTrue(afterPay.path("vip").asBoolean());
        assertEquals("month", afterPay.path("plan").asText());
        String vipUntilFirst = afterPay.path("vipUntil").asText();
        assertFalse(vipUntilFirst.isBlank());

        // 重复支付：幂等（有效期不再顺延）
        var repeatPay = objectMapperRead(pay(token, orderId, 1800L, "MOCK-TICKET"));
        assertEquals(vipUntilFirst, repeatPay.path("vipUntil").asText());

        // 状态查询：已激活
        assertTrue(get("/api/membership", token).path("vip").asBoolean());
    }

    @Test
    void 凭证缺失或金额不符不得入账() throws Exception {
        String token = newUser();
        String orderId = post("/api/membership/orders", Map.of("plan", "month"), token)
                .path("id").asText();

        // 缺凭证
        assertEquals(400, pay(token, orderId, 1800L, null).getStatusCode().value());
        // 金额与订单不符（少付 / 多付）
        assertEquals(400, pay(token, orderId, 1L, "MOCK-TICKET").getStatusCode().value());
        assertEquals(400, pay(token, orderId, 999999L, "MOCK-TICKET").getStatusCode().value());
        assertFalse(get("/api/membership", token).path("vip").asBoolean());

        // 正确金额后激活
        assertEquals(200, pay(token, orderId, 1800L, "MOCK-TICKET").getStatusCode().value());
        assertTrue(get("/api/membership", token).path("vip").asBoolean());
    }

    @Test
    void 续费在剩余有效期上顺延() throws Exception {
        String token = newUser();
        var order1 = post("/api/membership/orders", Map.of("plan", "month"), token);
        String until1 = objectMapperRead(pay(token, order1.path("id").asText(), 1800L, "MOCK-TICKET"))
                .path("vipUntil").asText();

        var order2 = post("/api/membership/orders", Map.of("plan", "year"), token);
        var pay2 = objectMapperRead(pay(token, order2.path("id").asText(), 15800L, "MOCK-TICKET"));
        String until2 = pay2.path("vipUntil").asText();

        assertTrue(until2.compareTo(until1) > 0, "续费后有效期应顺延: " + until1 + " -> " + until2);
        assertEquals("year", pay2.path("plan").asText());
    }

    @Test
    void 他人订单不可支付() throws Exception {
        String tokenA = newUser();
        String tokenB = newUser();
        var order = post("/api/membership/orders", Map.of("plan", "month"), tokenA);
        var resp = pay(tokenB, order.path("id").asText(), 1800L, "MOCK-TICKET");
        assertEquals(404, resp.getStatusCode().value());
        assertFalse(get("/api/membership", tokenB).path("vip").asBoolean());
    }

    /** 模拟支付请求；amountFen/credential 传 null 表示不携带对应字段。 */
    private org.springframework.http.ResponseEntity<String> pay(
            String token, String orderId, Long amountFen, String credential) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (amountFen != null) {
            body.put("amountFen", amountFen);
        }
        if (credential != null) {
            body.put("credential", credential);
        }
        return raw("POST", "/api/membership/orders/" + orderId + "/pay", body, token);
    }

    private com.fasterxml.jackson.databind.JsonNode objectMapperRead(
            org.springframework.http.ResponseEntity<String> resp) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(resp.getBody());
    }
}
