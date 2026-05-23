package com.ledger.model;

/**
 * Represents a financial account (checking, savings, or credit).
 *
 * Why 'long' for id?
 *   SQLite auto-increments IDs as integers. 'long' (64-bit) future-proofs
 *   us — 'int' (32-bit) maxes out at ~2 billion rows.
 *
 * Why 'double' for balance?
 *   Fine for display purposes. In a production banking app you'd use
 *   BigDecimal to avoid floating-point rounding errors — we'll keep it
 *   simple for now and note where that matters.
 */
public class Account {

    private long id;
    private String name;    // e.g., "Chase Checking"
    private String type;    // "CHECKING", "SAVINGS", or "CREDIT"
    private double balance; // current balance in dollars

    // ─── Constructors ────────────────────────────────────────────────────────

    /**
     * Used when creating a NEW account (id not yet assigned by DB).
     */
    public Account(String name, String type, double balance) {
        this.name = name;
        this.type = type;
        this.balance = balance;
    }

    /**
     * Used when LOADING an account from the database (id already exists).
     */
    public Account(long id, String name, String type, double balance) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.balance = balance;
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    public long getId()       { return id; }
    public String getName()   { return name; }
    public String getType()   { return type; }
    public double getBalance(){ return balance; }

    // ─── Setters ─────────────────────────────────────────────────────────────

    public void setId(long id)          { this.id = id; }
    public void setName(String name)    { this.name = name; }
    public void setType(String type)    { this.type = type; }
    public void setBalance(double bal)  { this.balance = bal; }

    // ─── toString ────────────────────────────────────────────────────────────

    /**
     * toString() is called automatically when you print an object.
     * Useful for debugging: System.out.println(account) shows all fields.
     */
    @Override
    public String toString() {
        return String.format("Account{id=%d, name='%s', type='%s', balance=%.2f}",
                id, name, type, balance);
    }
}