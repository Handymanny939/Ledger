package com.ledger.repository;

import com.ledger.db.DatabaseManager;
import com.ledger.model.Category;
import com.ledger.model.Transaction;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all SQL operations for the transactions table.
 *
 * Every public method follows the same structure:
 *   1. Get the shared connection
 *   2. Prepare a parameterized SQL statement
 *   3. Bind values to the ? placeholders
 *   4. Execute and process results
 *   5. Return a model object (or list, or void)
 *
 * SQLExceptions are caught and rethrown as RuntimeException so callers
 * (service layer) don't need checked-exception handling everywhere.
 */
public class TransactionRepository {

    // ─── CREATE ──────────────────────────────────────────────────────────────

    /**
     * Inserts a new transaction and sets the generated ID back on the object.
     *
     * Why Statement.RETURN_GENERATED_KEYS?
     *   After INSERT, SQLite assigns an auto-incremented ID. Without this flag,
     *   that ID is invisible to Java. With it, we can read it back via
     *   getGeneratedKeys() and stamp it onto the Transaction object — so the
     *   caller gets back a fully-populated object.
     */
    public Transaction create(Transaction transaction) {
        String sql = """
            INSERT INTO transactions (account_id, amount, category, description, date)
            VALUES (?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Bind each ? in order (1-indexed, not 0-indexed like arrays)
            ps.setLong(1, transaction.getAccountId());
            ps.setDouble(2, transaction.getAmount());

            // Store the enum's name() as TEXT — e.g., "FOOD", "RENT"
            // We'll convert back to enum in mapRow() using Category.fromString()
            ps.setString(3, transaction.getCategory().name());
            ps.setString(4, transaction.getDescription());

            // LocalDate → String for storage. ISO format: "2025-01-15"
            // SQLite has no native DATE type — TEXT in ISO format sorts correctly
            ps.setString(5, transaction.getDate().toString());

            ps.executeUpdate(); // runs the INSERT

            // Read back the auto-generated primary key
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    transaction.setId(keys.getLong(1));
                }
            }

            return transaction;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to create transaction: " + e.getMessage(), e);
        }
    }

    // ─── READ: single row ─────────────────────────────────────────────────────

    /**
     * Finds a single transaction by its primary key.
     * Returns null if no row matches — callers should null-check.
     *
     * A better approach (used in production) would be Optional<Transaction>,
     * but we keep it simple here to stay focused on the DB concepts.
     */
    public Transaction findById(long id) {
        String sql = "SELECT * FROM transactions WHERE id = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                // rs.next() returns false if no row found
                if (rs.next()) {
                    return mapRow(rs); // delegate to our helper
                }
                return null;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find transaction by id: " + e.getMessage(), e);
        }
    }

    // ─── READ: all rows ───────────────────────────────────────────────────────

    /**
     * Returns every transaction, ordered newest-first.
     * Returns an empty list (never null) if there are no transactions.
     */
    public List<Transaction> findAll() {
        String sql = "SELECT * FROM transactions ORDER BY date DESC";
        return executeQuery(sql); // reuse our private helper
    }

    // ─── READ: by account ─────────────────────────────────────────────────────

    /**
     * Returns all transactions belonging to a specific account.
     * Useful for showing an account's statement view.
     */
    public List<Transaction> findByAccount(long accountId) {
        String sql = "SELECT * FROM transactions WHERE account_id = ? ORDER BY date DESC";

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setLong(1, accountId);
            return mapRows(ps.executeQuery());

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find transactions by account: " + e.getMessage(), e);
        }
    }

    // ─── READ: by category ────────────────────────────────────────────────────

    /**
     * Returns all transactions for a given category.
     * Useful for budget tracking — "how much did I spend on FOOD this month?"
     */
    public List<Transaction> findByCategory(Category category) {
        String sql = "SELECT * FROM transactions WHERE category = ? ORDER BY date DESC";

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            // Store and query by the enum's name, not the displayName
            ps.setString(1, category.name());
            return mapRows(ps.executeQuery());

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find transactions by category: " + e.getMessage(), e);
        }
    }

    // ─── READ: by date range ──────────────────────────────────────────────────

    /**
     * Returns all transactions between two dates (inclusive).
     *
     * Why does TEXT date comparison work?
     *   Because we store dates in "YYYY-MM-DD" format, alphabetical order
     *   equals chronological order. SQLite's BETWEEN works correctly on it.
     *   e.g., "2025-01-01" BETWEEN "2025-01-01" AND "2025-01-31" → true
     */
    public List<Transaction> findByDateRange(LocalDate from, LocalDate to) {
        String sql = """
            SELECT * FROM transactions
            WHERE date BETWEEN ? AND ?
            ORDER BY date DESC
        """;

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setString(1, from.toString()); // "2025-01-01"
            ps.setString(2, to.toString());   // "2025-01-31"
            return mapRows(ps.executeQuery());

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find transactions by date range: " + e.getMessage(), e);
        }
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    /**
     * Deletes a transaction by ID.
     * Returns true if a row was actually deleted, false if ID didn't exist.
     * Checking executeUpdate() > 0 lets callers detect "nothing was deleted".
     */
    public boolean delete(long id) {
        String sql = "DELETE FROM transactions WHERE id = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setLong(1, id);
            return ps.executeUpdate() > 0; // rows affected

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete transaction: " + e.getMessage(), e);
        }
    }

    // ─── PRIVATE HELPERS ──────────────────────────────────────────────────────

    /**
     * Maps a single ResultSet row to a Transaction object.
     *
     * This is the ONLY place that knows the column names of the
     * transactions table. If a column is renamed, fix it here only.
     *
     * Called with the cursor already positioned on a valid row (rs.next()
     * has already been called by the caller).
     */
    private Transaction mapRow(ResultSet rs) throws SQLException {
        return new Transaction(
            rs.getLong("id"),
            rs.getLong("account_id"),
            rs.getDouble("amount"),

            // TEXT in DB → Category enum via our fromString() helper
            Category.fromString(rs.getString("category")),

            rs.getString("description"),

            // TEXT in DB → LocalDate via Java's built-in ISO parser
            // "2025-01-15" → LocalDate.of(2025, 1, 15)
            LocalDate.parse(rs.getString("date"))
        );
    }

    /**
     * Iterates over a full ResultSet, mapping each row.
     * Returns an empty list if there are no rows — never returns null.
     *
     * Used by any method that returns List<Transaction>.
     */
    private List<Transaction> mapRows(ResultSet rs) throws SQLException {
        List<Transaction> results = new ArrayList<>();
        while (rs.next()) {         // advance cursor; false when no more rows
            results.add(mapRow(rs));
        }
        return results;
    }

    /**
     * Runs a no-parameter SELECT and returns all mapped rows.
     * Used by findAll() — avoids boilerplate for simple queries.
     */
    private List<Transaction> executeQuery(String sql) {
        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            return mapRows(rs);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute query: " + e.getMessage(), e);
        }
    }
}