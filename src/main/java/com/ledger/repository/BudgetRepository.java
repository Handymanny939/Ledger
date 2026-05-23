package com.ledger.repository;

import com.ledger.db.DatabaseManager;
import com.ledger.model.Budget;
import com.ledger.model.Category;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all SQL operations for the budgets table.
 */
public class BudgetRepository {

    // ─── CREATE ──────────────────────────────────────────────────────────────

    /**
     * Inserts a new budget.
     * The DB enforces UNIQUE(category, month) — inserting a duplicate
     * will throw a RuntimeException with a clear SQLite message.
     */
    public Budget create(Budget budget) {
        String sql = """
            INSERT INTO budgets (category, monthly_limit, month)
            VALUES (?, ?, ?)
        """;

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, budget.getCategory().name());
            ps.setDouble(2, budget.getMonthlyLimit());
            ps.setString(3, budget.getMonth());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) budget.setId(keys.getLong(1));
            }

            return budget;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to create budget: " + e.getMessage(), e);
        }
    }

    // ─── READ ─────────────────────────────────────────────────────────────────

    /**
     * Returns all budgets for a given month (e.g., "2025-01").
     * This is the primary way the UI will load the budget view.
     */
    public List<Budget> findByMonth(String month) {
        String sql = "SELECT * FROM budgets WHERE month = ? ORDER BY category ASC";

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setString(1, month);
            return mapRows(ps.executeQuery());

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find budgets by month: " + e.getMessage(), e);
        }
    }

    /**
     * Finds the budget for a specific category and month.
     * Returns null if no budget has been set for that combination.
     */
    public Budget findByCategory(Category category, String month) {
        String sql = "SELECT * FROM budgets WHERE category = ? AND month = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setString(1, category.name());
            ps.setString(2, month);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
                return null;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find budget by category: " + e.getMessage(), e);
        }
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    /**
     * Updates the monthly limit for an existing budget.
     * Category and month are immutable — create a new budget to change those.
     */
    public boolean update(Budget budget) {
        String sql = "UPDATE budgets SET monthly_limit = ? WHERE id = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setDouble(1, budget.getMonthlyLimit());
            ps.setLong(2, budget.getId());
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update budget: " + e.getMessage(), e);
        }
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    public boolean delete(long id) {
        String sql = "DELETE FROM budgets WHERE id = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setLong(1, id);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete budget: " + e.getMessage(), e);
        }
    }

    // ─── PRIVATE HELPERS ──────────────────────────────────────────────────────

    private Budget mapRow(ResultSet rs) throws SQLException {
        return new Budget(
            rs.getLong("id"),
            Category.fromString(rs.getString("category")),
            rs.getDouble("monthly_limit"),
            rs.getString("month")
        );
    }

    private List<Budget> mapRows(ResultSet rs) throws SQLException {
        List<Budget> results = new ArrayList<>();
        while (rs.next()) results.add(mapRow(rs));
        return results;
    }
}