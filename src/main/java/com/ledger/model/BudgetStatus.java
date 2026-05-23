package com.ledger.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * DTO representing one row in the budget status report.
 * Carries pre-calculated fields so ReportPrinter is pure formatting.
 *
 * percentUsed() and isOverBudget() are computed from the core fields —
 * a record can have methods, it just can't have mutable state.
 */
public record BudgetStatus(
    Category category,
    BigDecimal limit,       // monthly budget cap (0 if not set)
    BigDecimal spent,       // total spent this month (always positive)
    BigDecimal remaining,   // limit - spent (can be negative if over)
    boolean hasBudget       // false if no budget was set for this category
) {

    /**
     * Returns percentage of budget used, capped at 999 to avoid absurd bars.
     * Returns 0 if no budget is set.
     */
    public int percentUsed() {
        if (!hasBudget || limit.compareTo(BigDecimal.ZERO) == 0) return 0;
        return limit.compareTo(BigDecimal.ZERO) == 0 ? 0 :
            spent.multiply(BigDecimal.valueOf(100))
                 .divide(limit, 0, RoundingMode.HALF_UP)
                 .min(BigDecimal.valueOf(999))
                 .intValue();
    }

    public boolean isOverBudget() {
        return hasBudget && spent.compareTo(limit) > 0;
    }

    public boolean isAtLimit() {
        return hasBudget && spent.compareTo(limit) == 0;
    }
}