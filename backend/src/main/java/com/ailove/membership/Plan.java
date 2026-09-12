package com.ailove.membership;

import java.util.Optional;

/**
 * 会员套餐目录（演示定价）。价格单位为分；期限按天计（续费在剩余有效期上顺延）。
 */
public enum Plan {

    MONTH("month", "月度 VIP", 1800, 30),
    QUARTER("quarter", "季度 VIP", 4800, 90),
    YEAR("year", "年度 VIP", 15800, 365);

    private final String id;
    private final String label;
    private final long priceFen;
    private final int days;

    Plan(String id, String label, long priceFen, int days) {
        this.id = id;
        this.label = label;
        this.priceFen = priceFen;
        this.days = days;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public long priceFen() {
        return priceFen;
    }

    public int days() {
        return days;
    }

    public static Optional<Plan> find(String id) {
        for (Plan p : values()) {
            if (p.id.equals(id)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
