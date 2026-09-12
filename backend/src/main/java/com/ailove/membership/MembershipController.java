package com.ailove.membership;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.ailove.auth.AuthContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 会员订阅接口：状态查询、套餐目录、下单与模拟支付回调。
 * 支付安全：仅 app.payment.mode=mock（默认）时开放模拟支付端点，且必须通过
 * PaymentGateway.verifyNotify（凭证 + 金额比对）才入账；gateway/off 模式下一律 403，
 * 防止真实收费后有人绕过网关自刷 VIP。
 * 依赖登录（体验模式下前端隐藏入口，接口按 401 兜底）。
 */
@RestController
@RequestMapping("/api/membership")
public class MembershipController {

    public record MembershipView(boolean vip, String plan, Instant vipUntil,
                                 int dailyUsed, int dailyLimit) {
    }

    public record PlanView(String id, String label, long priceFen, int days) {
    }

    private final MembershipStore store;
    private final PaymentGateway gateway;
    private final String paymentMode;
    private final int dailyFreeLimit;

    public MembershipController(MembershipStore store,
                                ObjectProvider<PaymentGateway> gatewayProvider,
                                @Value("${app.payment.mode:mock}") String paymentMode,
                                @Value("${app.membership.daily-free-limit:20}") int dailyFreeLimit) {
        this.store = store;
        this.gateway = gatewayProvider.getIfAvailable();
        this.paymentMode = paymentMode;
        this.dailyFreeLimit = dailyFreeLimit;
    }

    @GetMapping
    public MembershipView me() {
        long userId = requireUser();
        MembershipStore.Status s = store.status(userId);
        return new MembershipView(s.vip(), s.plan(), s.vipUntil(),
                s.vip() ? 0 : store.dailyUsed(userId), dailyFreeLimit);
    }

    @GetMapping("/plans")
    public List<PlanView> plans() {
        return java.util.Arrays.stream(Plan.values())
                .map(p -> new PlanView(p.id(), p.label(), p.priceFen(), p.days()))
                .toList();
    }

    @PostMapping("/orders")
    public MembershipStore.Order create(@RequestBody Map<String, String> body) {
        if ("off".equals(paymentMode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "支付功能未开启");
        }
        long userId = requireUser();
        Plan plan = Plan.find(body == null ? null : body.get("plan"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知套餐"));
        return store.createOrder(userId, plan);
    }

    /**
     * 模拟支付（仅 mock 模式）：payload 携带凭证与金额，经 MockGateway 校验（金额必须与订单一致）后
     * 幂等入账。真实接入时此端点关闭，由 /payment/notify 的网关异步通知（验签后）替代。
     */
    @PostMapping("/orders/{id}/pay")
    public MembershipView pay(@PathVariable String id,
                              @RequestBody(required = false) Map<String, Object> payload) {
        long userId = requireUser();
        if (gateway == null || !"mock".equals(paymentMode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "gateway".equals(paymentMode) ? "请通过支付网关完成支付" : "支付功能未开启");
        }
        MembershipStore.Order order = store.findOrder(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (order.userId() != userId) {
            // 不暴露他人订单存在性
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        Map<String, String> normalized = new java.util.HashMap<>();
        if (payload != null) {
            payload.forEach((k, v) -> normalized.put(k, v == null ? null : String.valueOf(v)));
        }
        if (!gateway.verifyNotify(normalized, order)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付凭证或金额校验失败");
        }
        store.payOrder(id);
        return me();
    }

    private long requireUser() {
        Long userId = AuthContext.userId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }
}
