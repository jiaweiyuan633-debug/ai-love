package com.ailove.membership;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.ailove.auth.AuthContext;
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
    private final int dailyFreeLimit;

    public MembershipController(MembershipStore store,
                                @Value("${app.membership.daily-free-limit:20}") int dailyFreeLimit) {
        this.store = store;
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
        long userId = requireUser();
        Plan plan = Plan.find(body == null ? null : body.get("plan"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知套餐"));
        return store.createOrder(userId, plan);
    }

    /** 模拟支付：真实接入时由支付网关的异步通知（验签后）替代该入口。 */
    @PostMapping("/orders/{id}/pay")
    public MembershipView pay(@PathVariable String id) {
        long userId = requireUser();
        MembershipStore.Order order = store.findOrder(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (order.userId() != userId) {
            // 不暴露他人订单存在性
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
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
