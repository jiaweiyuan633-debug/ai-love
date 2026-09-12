package com.ailove.membership;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 会员状态/订单/每日额度计数存储，双模式与其它 Store 对齐：
 * 有 JdbcClient（app.persistence.enabled=true）时落库，否则进程内（云端体验模式）。
 * 每日额度为演示实现：持久化模式跨重启保留，体验模式随实例生命周期。
 */
public class MembershipStore {

    /** 当前会员状态：vip=false 时 plan/vipUntil 置空（已过期同此） */
    public record Status(boolean vip, String plan, Instant vipUntil) {
    }

    public record Order(String id, long userId, String plan, long priceFen,
                        String status, Instant createdAt, Instant paidAt) {
    }

    private final JdbcClient jdbc;
    // 体验模式（无数据库）的进程内存储
    private final Map<Long, Status> mem = new ConcurrentHashMap<>();
    private final Map<String, Order> memOrders = new ConcurrentHashMap<>();
    private final Map<String, Integer> memDaily = new ConcurrentHashMap<>();

    /** 体验模式：进程内存储 */
    public MembershipStore() {
        this.jdbc = null;
    }

    /** 持久化模式： memberships / membership_orders / membership_daily_usage 三表落库 */
    public MembershipStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isVip(long userId) {
        return status(userId).vip();
    }

    public Status status(long userId) {
        Status raw = raw(userId);
        if (raw.vipUntil() != null && raw.vipUntil().isAfter(Instant.now())) {
            return raw;
        }
        return new Status(false, null, null);
    }

    private Status raw(long userId) {
        if (jdbc != null) {
            return jdbc.sql("SELECT plan, vip_until FROM memberships WHERE user_id = ?")
                    .param(userId)
                    .query((rs, i) -> new Status(true, rs.getString("plan"),
                            rs.getTimestamp("vip_until").toInstant()))
                    .optional()
                    .orElse(new Status(false, null, null));
        }
        return mem.getOrDefault(userId, new Status(false, null, null));
    }

    /** 记录一次 AI 调用并返回今日已用次数（含本次）。 */
    public int recordAiUsage(long userId) {
        if (jdbc != null) {
            return jdbc.sql("""
                            INSERT INTO membership_daily_usage(user_id, uday, used) VALUES (?, ?, 1)
                            ON CONFLICT (user_id, uday) DO UPDATE SET used = membership_daily_usage.used + 1
                            RETURNING used""")
                    .param(userId)
                    .param(LocalDate.now())
                    .query((rs, i) -> rs.getInt(1))
                    .single();
        }
        return memDaily.merge(LocalDate.now() + ":" + userId, 1, Integer::sum);
    }

    public int dailyUsed(long userId) {
        if (jdbc != null) {
            return jdbc.sql("SELECT used FROM membership_daily_usage WHERE user_id = ? AND uday = ?")
                    .param(userId)
                    .param(LocalDate.now())
                    .query((rs, i) -> rs.getInt(1))
                    .optional()
                    .orElse(0);
        }
        return memDaily.getOrDefault(LocalDate.now() + ":" + userId, 0);
    }

    public Order createOrder(long userId, Plan plan) {
        Order order = new Order(UUID.randomUUID().toString(), userId, plan.id(),
                plan.priceFen(), "pending", Instant.now(), null);
        if (jdbc != null) {
            jdbc.sql("INSERT INTO membership_orders(id, user_id, plan, price_fen, status) VALUES (?, ?, ?, ?, 'pending')")
                    .param(order.id()).param(order.userId()).param(order.plan()).param(order.priceFen())
                    .update();
        } else {
            memOrders.put(order.id(), order);
        }
        return order;
    }

    public Optional<Order> findOrder(String id) {
        if (jdbc != null) {
            return jdbc.sql("SELECT id, user_id, plan, price_fen, status, created_at, paid_at FROM membership_orders WHERE id = ?")
                    .param(id)
                    .query((rs, i) -> new Order(rs.getString("id"), rs.getLong("user_id"), rs.getString("plan"),
                            rs.getLong("price_fen"), rs.getString("status"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("paid_at") == null ? null : rs.getTimestamp("paid_at").toInstant()))
                    .optional();
        }
        return Optional.ofNullable(memOrders.get(id));
    }

    /**
     * 支付回调（幂等）：仅首次 pending→paid 激活/顺延 VIP；对已支付订单重复调用是安全的空操作。
     * 接入真实支付宝/微信时，只需把这里的"直接置 paid"换成"验签后的异步通知入口"。
     */
    public void payOrder(String id) {
        tryMarkPaid(id).ifPresent(order ->
                Plan.find(order.plan()).ifPresent(plan -> extendVip(order.userId(), plan)));
    }

    /** 仅在 pending→paid 的首次迁移时返回订单；已支付（幂等）或不存在返回 empty。 */
    private Optional<Order> tryMarkPaid(String id) {
        if (jdbc != null) {
            int updated = jdbc.sql("UPDATE membership_orders SET status = 'paid', paid_at = now() WHERE id = ? AND status = 'pending'")
                    .param(id)
                    .update();
            return updated > 0 ? findOrder(id) : Optional.empty();
        }
        Order o = memOrders.get(id);
        if (o == null || !"pending".equals(o.status())) {
            return Optional.empty();
        }
        Order paid = new Order(o.id(), o.userId(), o.plan(), o.priceFen(), "paid", o.createdAt(), Instant.now());
        memOrders.put(id, paid);
        return Optional.of(paid);
    }

    /** 激活/续费：未过期则在剩余有效期上顺延，否则从现在起算。 */
    private void extendVip(long userId, Plan plan) {
        Status raw = raw(userId);
        Instant base = raw.vipUntil() != null && raw.vipUntil().isAfter(Instant.now())
                ? raw.vipUntil()
                : Instant.now();
        Instant until = base.plus(plan.days(), java.time.temporal.ChronoUnit.DAYS);
        if (jdbc != null) {
            jdbc.sql("""
                            INSERT INTO memberships(user_id, plan, vip_until, updated_at) VALUES (?, ?, ?, now())
                            ON CONFLICT (user_id) DO UPDATE SET plan = EXCLUDED.plan, vip_until = EXCLUDED.vip_until, updated_at = now()""")
                    .param(userId).param(plan.id()).param(Timestamp.from(until))
                    .update();
        } else {
            mem.put(userId, new Status(true, plan.id(), until));
        }
    }
}
