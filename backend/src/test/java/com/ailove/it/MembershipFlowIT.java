package com.ailove.it;

import java.util.Map;

import com.ailove.support.BaseIT;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会员订阅闭环集成测试：套餐目录 → 状态查询 → 下单 → 模拟支付激活 VIP →
 * 重复支付幂等 → 续费顺延有效期。
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

        // 支付激活
        var afterPay = post("/api/membership/orders/" + orderId + "/pay", null, token);
        assertTrue(afterPay.path("vip").asBoolean());
        assertEquals("month", afterPay.path("plan").asText());
        assertFalse(afterPay.path("vipUntil").isNull());
        String vipUntilFirst = afterPay.path("vipUntil").asText();

        // 重复支付：幂等（有效期不再顺延）
        var repeatPay = post("/api/membership/orders/" + orderId + "/pay", null, token);
        assertTrue(repeatPay.path("vip").asBoolean());
        assertEquals(vipUntilFirst, repeatPay.path("vipUntil").asText());

        // 订单状态已更新为 paid
        var me = get("/api/membership", token);
        assertTrue(me.path("vip").asBoolean());
        assertEquals(0, me.path("dailyUsed").asInt());
    }

    @Test
    void 续费在剩余有效期上顺延() throws Exception {
        String token = newUser();
        var order1 = post("/api/membership/orders", Map.of("plan", "month"), token);
        var pay1 = post("/api/membership/orders/" + order1.path("id").asText() + "/pay", null, token);
        String until1 = pay1.path("vipUntil").asText();

        var order2 = post("/api/membership/orders", Map.of("plan", "year"), token);
        var pay2 = post("/api/membership/orders/" + order2.path("id").asText() + "/pay", null, token);
        String until2 = pay2.path("vipUntil").asText();

        assertTrue(until2.compareTo(until1) > 0, "续费后有效期应顺延: " + until1 + " -> " + until2);
        assertEquals("year", pay2.path("plan").asText());
    }

    @Test
    void 他人订单不可支付() throws Exception {
        String tokenA = newUser();
        String tokenB = newUser();
        var order = post("/api/membership/orders", Map.of("plan", "month"), tokenA);
        var resp = raw("POST", "/api/membership/orders/" + order.path("id").asText() + "/pay", null, tokenB);
        assertEquals(404, resp.getStatusCode().value());
        assertFalse(get("/api/membership", tokenB).path("vip").asBoolean());
    }
}
