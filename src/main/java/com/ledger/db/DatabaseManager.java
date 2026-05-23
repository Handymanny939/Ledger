package com.ledger.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite database connection and schema creation.
 *
 * Design decisions explained:
 *
 * 1. SINGLETON PATTERN
 *    We use a private static instance so only one DatabaseManager
 *    ever exists. This prevents multiple competing connections to
 *    the same SQLite file (SQLite handles concurrency poorly).
 *
 * 2. LAZY INITIALIZATION
 *    The connection is created the first time getInstance() is called,
 *    not when the class is loaded. This avoids errors at startup if
 *    the DB path isn't ready yet.
 *
 * 3. CREATE TABLE IF NOT EXISTS
 *    Safe to run on every startup. If tables already exist, SQLite
 *    skips creation silently. This is our primitive "migration" strategy.
 */
public class DatabaseManager {

    // Path to the SQLite file — will be created if it doesn't exist
    private static final String DB_URL = "jdbc:sqlite:finance.db";

    // The single shared Connection instance
    private static Connection connection;

    // Private constructor — prevents anyone doing: new DatabaseManager()
    private DatabaseManager() {}

    /**
     * Returns the shared Connection, creating it on first call.
     * All repository classes call this to get a connection.
     *
     * @throws RuntimeException if the DB file can't be opened
     */
    public static Connection getConnection() {
        if (connection == null) {
            try {
                // DriverManager reads the jdbc:sqlite: prefix and delegates
                // to the SQLite JDBC driver we included in pom.xml
                connection = DriverManager.getConnection(DB_URL);

                // Enforce foreign key constraints — SQLite disables them
                // by default for legacy reasons. We want them ON.
                connection.createStatement().execute("PRAGMA foreign_keys = ON");

                System.out.println("Database connected: finance.db");

                // Create tables immediately after connecting
                initializeSchema();

            } catch (SQLException e) {
                // Wrap in RuntimeException — callers don't need to handle
                // DB errors on every method call
                throw new RuntimeException("Failed to connect to database", e);
            }
        }
        return connection;
    }

    /**
     * Creates all tables if they don't already exist.
     * Called once automatically by getConnection().
     *
     * Schema design notes:
     * - INTEGER PRIMARY KEY in SQLite is an alias for the rowid (auto-increment)
     * - REAL is SQLite's floating-point type (maps to Java double)
     * - TEXT is SQLite's string type
     * - NOT NULL enforces required fields at the database level too
     */
    private static void initializeSchema() throws SQLException {
        // Using a try-with-resources Statement so it closes automatically
        try (Statement stmt = connection.createStatement()) {

            // ── Accounts table ───────────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS accounts (
                    id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    name    TEXT    NOT NULL,
                    type    TEXT    NOT NULL,
                    balance REAL    NOT NULL DEFAULT 0.0
                )
            """);

            // ── Transactions table ───────────────────────────────────────────
            // account_id is a FOREIGN KEY — ensures every transaction belongs
            // to a real account. ON DELETE CASCADE means if an account is
            // deleted, its transactions are automatically deleted too.
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    account_id  INTEGER NOT NULL,
                    amount      REAL    NOT NULL,
                    category    TEXT    NOT NULL,
                    description TEXT,
                    date        TEXT    NOT NULL,
                    FOREIGN KEY (account_id) REFERENCES accounts(id)
                        ON DELETE CASCADE
                )
            """);

            // ── Budgets table ────────────────────────────────────────────────
            // UNIQUE(category, month) prevents duplicate budgets for the
            // same category in the same month — enforced by the database.
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS budgets (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    category      TEXT    NOT NULL,
                    monthly_limit REAL    NOT NULL,
                    month         TEXT    NOT NULL,
                    UNIQUE(category, month)
                )
            """);

            System.out.println("Schema initialized.");
        }
    }

    /**
     * Cleanly closes the connection.
     * Call this when the application is shutting down.
     */
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
                System.out.println("Database connection closed.");
            } catch (SQLException e) {
                System.err.println("Warning: could not close DB connection: " + e.getMessage());
            }
        }
    }
}