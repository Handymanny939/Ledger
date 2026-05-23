package com.ledger.model;

/**
 * Represents a monthly spending limit for a specific category.
 *
 * 'month' is stored as a String in "YYYY-MM" format (e.g., "2025-01").
 * This makes it easy to query ("WHERE month = '2025-01'") and sort
 * alphabetically, which also sorts chronologically.
 *
 * Example: Budget for FOOD in January 2025 with a $500 limit.
 */
public class Budget {

    private long id;
    private Category category;    // which spending category this cap applies to
    private double monthlyLimit;  // maximum allowed spend for the month
    private String month;         // "YYYY-MM" format, e.g., "2025-01"

    // ─── Constructors ────────────────────────────────────────────────────────

    /**
     * Used when creating a NEW budget entry.
     */
    public Budget(Category category, double monthlyLimit, String month) {
        this.category = category;
        this.monthlyLimit = monthlyLimit;
        this.month = month;
    }

    /**
     * Used when LOADING a budget from the database.
     */
    public Budget(long id, Category category, double monthlyLimit, String month) {
        this.id = id;
        this.category = category;
        this.monthlyLimit = monthlyLimit;
        this.month = month;
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    public long getId()              { return id; }
    public Category getCategory()    { return category; }
    public double getMonthlyLimit()  { return monthlyLimit; }
    public String getMonth()         { return month; }

    // ─── Setters ─────────────────────────────────────────────────────────────

    public void setId(long id)                  { this.id = id; }
    public void setCategory(Category category)  { this.category = category; }
    public void setMonthlyLimit(double limit)   { this.monthlyLimit = limit; }
    public void setMonth(String month)          { this.month = month; }

    @Override
    public String toString() {
        return String.format("Budget{id=%d, category=%s, limit=%.2f, month='%s'}",
                id, category, monthlyLimit, month);
    }
}