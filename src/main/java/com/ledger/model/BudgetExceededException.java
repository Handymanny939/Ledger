package com.ledger.model;

import java.math.BigDecimal;

/**
 * Thrown when recording a transaction would exceed the monthly budget
 * limit for its category.
 *
 * Carrying the overage amount (how much over the limit this would put
 * the user) lets the UI give actionable feedback:
 * "This would exceed your FOOD budget by $42.50."
 */
public class BudgetExceededException extends RuntimeException {

    private final Category category;
    private final BigDecimal limit;    // the monthly budget cap
    private final BigDecimal overage;  // how far over the limit this goes

    public BudgetExceededException(Category category, BigDecimal limit, BigDecimal overage) {
        super(String.format(
            "Budget exceeded for %s: limit is %.2f, overage is %.2f",
            category.getDisplayName(), limit, overage
        ));
        this.category = category;
        this.limit = limit;
        this.overage = overage;
    }

    public Category getCategory() { return category; }
    public BigDecimal getLimit()   { return limit; }
    public BigDecimal getOverage() { return overage; }
}