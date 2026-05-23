package com.ledger.model;

import java.time.LocalDate; // Java 8+ date class — no time zone headaches

/**
 * Represents a single financial transaction (debit or credit).
 *
 * Why LocalDate instead of java.util.Date?
 *   LocalDate is immutable and timezone-free — perfect for "a calendar date"
 *   like a transaction date. java.util.Date is mutable and error-prone.
 *
 * Positive amount = money in (income).
 * Negative amount = money out (expense).
 * This convention lets us sum all transactions to get net flow easily.
 */
public class Transaction {

    private long id;
    private long accountId;       // foreign key → accounts.id
    private double amount;        // positive = income, negative = expense
    private Category category;    // enum, not a raw String
    private String description;   // e.g., "Grocery run at Publix"
    private LocalDate date;       // the calendar date of the transaction

    // ─── Constructors ────────────────────────────────────────────────────────

    /**
     * Used when recording a NEW transaction.
     */
    public Transaction(long accountId, double amount, Category category,
                       String description, LocalDate date) {
        this.accountId = accountId;
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.date = date;
    }

    /**
     * Used when LOADING a transaction from the database.
     */
    public Transaction(long id, long accountId, double amount, Category category,
                       String description, LocalDate date) {
        this.id = id;
        this.accountId = accountId;
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.date = date;
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    public long getId()             { return id; }
    public long getAccountId()      { return accountId; }
    public double getAmount()       { return amount; }
    public Category getCategory()   { return category; }
    public String getDescription()  { return description; }
    public LocalDate getDate()      { return date; }

    // ─── Setters ─────────────────────────────────────────────────────────────

    public void setId(long id)                  { this.id = id; }
    public void setAccountId(long accountId)    { this.accountId = accountId; }
    public void setAmount(double amount)        { this.amount = amount; }
    public void setCategory(Category category)  { this.category = category; }
    public void setDescription(String desc)     { this.description = desc; }
    public void setDate(LocalDate date)         { this.date = date; }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Convenience method — avoids sprinkling "amount < 0" checks everywhere.
     */
    public boolean isExpense() { return amount < 0; }
    public boolean isIncome()  { return amount > 0; }

    @Override
    public String toString() {
        return String.format(
            "Transaction{id=%d, accountId=%d, amount=%.2f, category=%s, desc='%s', date=%s}",
            id, accountId, amount, category, description, date);
    }
}