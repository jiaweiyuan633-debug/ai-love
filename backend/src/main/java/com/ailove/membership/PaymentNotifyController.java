package com.ailove.membership;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 支付网关异步通知入口（/payment/notify）。
 * 刻意放在 JWT 保护前缀（/ai/*、/api/*、/knowledge/*）之外：支付网关没有用户登录态，
 * 请求安全性完全由 PaymentGateway.verifyNotify（验签 + 金额比对）保证。
 * 仅 app.payment.mode=gateway 且装配了真实网关实现后启用；当前未接入真实网关时恒为 404。
 */
@RestController
@RequestMapping("/payment")
public class PaymentNotifyController {

    private final MembershipStore store;
    private final PaymentGateway gateway;

    public PaymentNotifyController(MembershipStore store, ObjectProvider<PaymentGateway> gatewayProvider) {
        this.store = store;
        this.gateway = gatewayProvider.getIfAvailable();
    }

    @PostMapping("/notify")
    public Map<String, Object> notify(@RequestBody Map<String, String> payload) {
        if (gateway == null || gateway instanceof MockGateway) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "支付通知入口未启用");
        }
        String orderId = payload == null ? null : payload.get("orderId");
        MembershipStore.Order order = orderId == null || orderId.isBlank()
                ? null
                : store.findOrder(orderId).orElse(null);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        if (!gateway.verifyNotify(payload, order)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付通知校验失败");
        }
        store.payOrder(order.id());
        return Map.of("received", true);
    }
}
