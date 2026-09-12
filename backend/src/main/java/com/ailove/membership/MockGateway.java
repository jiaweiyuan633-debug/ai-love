package com.ailove.membership;

import java.util.Map;

/**
 * 模拟收银台（app.payment.mode=mock，默认）：凭证非空且金额与订单一致即视为支付成功，
 * 用于本地与演示环境跑通「下单 → 支付 → VIP 生效」闭环。
 * 生产配置必须切换为 gateway 或 off，届时本网关不再装配、模拟支付端点自动 403。
 */
public class MockGateway implements PaymentGateway {

    public static final String CREDENTIAL_FIELD = "credential";
    public static final String AMOUNT_FIELD = "amountFen";

    @Override
    public String id() {
        return "mock";
    }

    @Override
    public boolean verifyNotify(Map<String, String> payload, MembershipStore.Order order) {
        if (payload == null || order == null) {
            return false;
        }
        String credential = payload.get(CREDENTIAL_FIELD);
        if (credential == null || credential.isBlank()) {
            return false;
        }
        try {
            return Long.parseLong(payload.getOrDefault(AMOUNT_FIELD, "").trim()) == order.priceFen();
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
