package com.ailove.membership;

import java.util.Map;

/**
 * 支付网关抽象。接入真实支付宝/微信时：
 * 1. 实现本接口，verifyNotify 内完成「验签 + 金额比对（order.priceFen，单位分）+ 支付状态判断」；
 * 2. 网关的异步通知会打到 /payment/notify（无登录态，安全完全依赖验签）；
 * 3. app.payment.mode 配置为 gateway 后，模拟支付端点自动关闭，防止绕过网关自刷 VIP。
 * 商户号申请与接入步骤见 docs/COMMERCIAL.md。
 */
public interface PaymentGateway {

    /** 网关标识：mock / alipay / wechat-pay 等 */
    String id();

    /**
     * 校验一笔支付通知是否真实有效。返回 true 才允许把订单标记为已支付（幂等）。
     * 实现必须自行验证签名，并确保通知中的金额与订单金额（priceFen）一致。
     */
    boolean verifyNotify(Map<String, String> payload, MembershipStore.Order order);
}
