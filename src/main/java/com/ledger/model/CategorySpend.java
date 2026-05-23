package com.ledger.model;

import java.math.BigDecimal;

/**
 * DTO representing how much was spent in a single category.
 * Used in both MonthlySummary and the top-spending-categories report.
 *
 * 'transactionCount' lets the printer show "12 transactions" alongside
 * the total — useful context for the user.
 */
public record CategorySpend(
    Category category,          // which category
    BigDecimal totalSpent,      // absolute value (always positive)
    int transactionCount        // how many transactions make up this total
) implements Comparable<CategorySpend> {

    /**
     * Natural sort order: highest spend first.
     * Allows Collections.sort() and Stream.sorted() to work without
     * a custom Comparator.
     */
    @Override
    public int compareTo(CategorySpend other) {
        // reversed: higher spend sorts first
        return other.totalSpent().compareTo(this.totalSpent);
    }
}