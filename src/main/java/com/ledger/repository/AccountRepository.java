package com.ledger.repository;

import com.ledger.db.DatabaseManager;
import com.ledger.model.Account;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all SQL operations for the accounts table.
 * Follows the same patterns established in TransactionRepository.
 */
public class AccountRepository {

    // ─── CREATE ──────────────────────────────────────────────────────────────

    public Account create(Account account) {
        String sql = "INSERT INTO accounts (name, type, balance) VALUES (?, ?, ?)";

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, account.getName());
            ps.setString(2, account.getType());
            ps.setDouble(3, account.getBalance());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    account.setId(keys.getLong(1));
                }
            }

            return account;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to create account: " + e.getMessage(), e);
        }
    }

    // ─── READ ─────────────────────────────────────────────────────────────────

    public Account findById(long id) {
        String sql = "SELECT * FROM accounts WHERE id = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
                return null;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find account by id: " + e.getMessage(), e);
        }
    }

    public List<Account> findAll() {
        String sql = "SELECT * FROM accounts ORDER BY name ASC";
        List<Account> results = new ArrayList<>();

        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) results.add(mapRow(rs));
            return results;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all accounts: " + e.getMessage(), e);
        }
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    /**
     * Updates all mutable fields of an existing account.
     * Uses the account's id to locate the row — id itself never changes.
     */
    public boolean update(Account account) {
        String sql = "UPDATE accounts SET name = ?, type = ?, balance = ? WHERE id = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setString(1, account.getName());
            ps.setString(2, account.getType());
            ps.setDouble(3, account.getBalance());
            ps.setLong(4, account.getId()); // WHERE clause — must be last

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update account: " + e.getMessage(), e);
        }
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    /**
     * Deletes an account. Because we set ON DELETE CASCADE in the schema,
     * all transactions belonging to this account are automatically deleted too.
     */
    public boolean delete(long id) {
        String sql = "DELETE FROM accounts WHERE id = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setLong(1, id);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete account: " + e.getMessage(), e);
        }
    }

    // ─── PRIVATE HELPERS ──────────────────────────────────────────────────────

    private Account mapRow(ResultSet rs) throws SQLException {
        return new Account(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("type"),
            rs.getDouble("balance")
        );
    }
}