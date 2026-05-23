package com.ledger.model;

import java.math.BigDecimal;

/**
 * DTO for the account balances overview report.
 * Pairs an Account with its pre-formatted display balance.
 */
public record AccountSummary(
    String name,
    String type,
    BigDecimal balance
) {}