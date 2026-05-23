package com.ledger;

/**
 * Ledger — Personal Finance Tracker
 * JVM entry point. Delegates everything to App.
 *
 * Main is intentionally tiny — it exists only because the JVM
 * requires a public static void main(String[]) to start.
 * All real logic lives in App.start().
 */
public class Main {

    public static void main(String[] args) {
        new App().start();
    }
}