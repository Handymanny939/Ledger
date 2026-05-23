package com.ledger.model;

import java.math.BigDecimal;

/**
 * Thrown when a withdrawal or transfer would push an account balance negative.
 *
 * We extend RuntimeException (unchecked) so callers don't need a
 * mandatory try/catch — but the UI layer CAN catch it to show a
 * friendly message instead of a stack trace.
 *
 * Carrying both the attempted amount and available balance lets the
 * UI show a useful message: "You tried to withdraw $500 but only
 * $320.00 is available."
 */
public class InsufficientFundsException extends RuntimeException {

    private final BigDecimal attempted;  // what the user tried to move
    private final BigDecimal available;  // what was actually in the account

    public InsufficientFundsException(BigDecimal attempted, BigDecimal available) {
        // super() sets the message returned by getMessage()
        super(String.format(
            "Insufficient funds: attempted %.2f but only %.2f available",
            attempted, available
        ));
        this.attempted = attempted;
        this.available = available;
    }

    public BigDecimal getAttempted() { return attempted; }
    public BigDecimal getAvailable() { return available; }
}