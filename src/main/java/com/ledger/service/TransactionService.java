package com.ledger.service;

import com.ledger.db.DatabaseManager;
import com.ledger.model.*;
import com.ledger.repository.AccountRepository;
import com.ledger.repository.TransactionRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Business logic for recording and querying transactions.
 *
 * Key responsibility: recordTransaction() must update the account balance
 * AND insert the transaction row atomically — if either step fails,
 * both are rolled back. We manage the database transaction manually here.
 *
 * This service only speaks to repositories — never to DatabaseManager
 * directly EXCEPT to get the Connection for transaction control.
 * (Connection management is the one legitimate exception to that rule.)
 */
public class TransactionService {

    // Services hold repository references — injected via constructor
    private final TransactionRepository transactionRepo;
    private final AccountRepository accountRepo;
    private final BudgetService budgetService;

    /**
     * Constructor injection: we pass in the dependencies rather than
     * creating them inside. This is the first step toward testability —
     * in a test you can pass in a fake repository.
     */
    public TransactionService(TransactionRepository transactionRepo,
                               AccountRepository accountRepo,
                               BudgetService budgetService) {
        this.transactionRepo = transactionRepo;
        this.accountRepo = accountRepo;
        this.budgetService = budgetService;
    }

    // ─── RECORD TRANSACTION ───────────────────────────────────────────────────

    /**
     * Records a transaction and updates the account balance atomically.
     *
     * ATOMIC means: either BOTH the insert and the balance update succeed,
     * or NEITHER does. No half-applied state is ever left in the database.
     *
     * Flow:
     *   1. Validate the account exists
     *   2. Check budget (for expenses) — throw BudgetExceededException if over
     *   3. Disable auto-commit → begin a database transaction
     *   4. Insert the transaction row
     *   5. Update the account balance
     *   6. COMMIT if both succeed, ROLLBACK if anything throws
     *
     * @param accountId   which account to debit/credit
     * @param amount      positive = income, negative = expense
     * @param category    spending category
     * @param description human-readable note
     * @param date        calendar date of the transaction
     * @return the saved Transaction with its DB-assigned id
     * @throws BudgetExceededException   if expense would exceed monthly budget
     * @throws IllegalArgumentException  if account doesn't exist
     */
    public Transaction recordTransaction(long accountId, BigDecimal amount,
                                          Category category, String description,
                                          LocalDate date) {

        // ── Step 1: Validate account exists ──────────────────────────────────
        Account account = accountRepo.findById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found with id: " + accountId);
        }

        // ── Step 2: Budget check for expenses only ────────────────────────────
        // amount < 0 means expense. We only budget expenses, not income.
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            String month = date.format(DateTimeFormatter.ofPattern("yyyy-MM"));

            // Convert the negative amount to a positive expense value for comparison
            BigDecimal expenseAmount = amount.abs();

            // Ask BudgetService: would this expense exceed the cap?
            if (budgetService.wouldExceedBudget(category, month, expenseAmount)) {
                BigDecimal remaining = budgetService.getRemainingBudget(category, month);
                BigDecimal limit     = budgetService.getBudgetLimit(category, month);
                BigDecimal overage   = expenseAmount.subtract(remaining);
                throw new BudgetExceededException(category, limit, overage);
            }
        }

        // ── Step 3: Get connection and disable auto-commit ────────────────────
        // By default, every SQL statement is auto-committed (its own transaction).
        // Setting autoCommit=false lets US decide when to commit.
        Connection conn = DatabaseManager.getConnection();

        try {
            conn.setAutoCommit(false); // BEGIN TRANSACTION

            // ── Step 4: Insert the transaction row ────────────────────────────
            Transaction transaction = new Transaction(
                accountId, amount.doubleValue(), category, description, date
            );
            transactionRepo.create(transaction); // sets transaction.id via RETURN_GENERATED_KEYS

            // ── Step 5: Update the account balance ────────────────────────────
            // Use BigDecimal for the math, then store as double in the model.
            // compareTo(ZERO) < 0 means negative (expense), which reduces balance.
            BigDecimal currentBalance = BigDecimal.valueOf(account.getBalance());
            BigDecimal newBalance = currentBalance.add(amount); // add() handles negatives correctly

            // Enforce non-negative balance on expenses
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                // Must rollback before throwing — never leave a half-committed state
                conn.rollback();
                conn.setAutoCommit(true);
                throw new InsufficientFundsException(amount.abs(), currentBalance);
            }

            account.setBalance(newBalance.doubleValue());
            accountRepo.update(account); // UPDATE accounts SET balance = ? WHERE id = ?

            // ── Step 6: Commit — both operations succeeded ────────────────────
            conn.commit();
            conn.setAutoCommit(true); // restore default behavior

            return transaction;

        } catch (InsufficientFundsException | BudgetExceededException e) {
            // These are expected business exceptions — re-throw as-is
            // (rollback already called above for InsufficientFunds)
            safeRollback(conn);
            throw e;

        } catch (SQLException e) {
            // Unexpected DB error — rollback and wrap
            safeRollback(conn);
            throw new RuntimeException("Failed to record transaction atomically: " + e.getMessage(), e);
        }
    }

    // ─── QUERY METHODS ────────────────────────────────────────────────────────

    /**
     * Returns all transactions for an account within a date range.
     * "Period" = a start and end date — e.g., the current month.
     */
    public List<Transaction> getTransactionsByPeriod(long accountId,
                                                      LocalDate from,
                                                      LocalDate to) {
        // Get all transactions in the date range, then filter by account
        // This keeps the repository query simple — filtering in Java for now.
        // For large datasets you'd add a combined SQL query instead.
        return transactionRepo.findByAccount(accountId)
            .stream()
            .filter(t -> !t.getDate().isBefore(from) && !t.getDate().isAfter(to))
            .toList(); // Java 16+ — collects Stream back to an unmodifiable List
    }

    /**
     * Returns all transactions for a given account.
     */
    public List<Transaction> getTransactionsForAccount(long accountId) {
        return transactionRepo.findByAccount(accountId);
    }

    /**
     * Returns all transactions for a given category.
     */
    public List<Transaction> getTransactionsByCategory(Category category) {
        return transactionRepo.findByCategory(category);
    }

    /**
     * Deletes a transaction and reverses its effect on the account balance.
     * Also atomic — both the delete and the balance reversal commit together.
     */
    public boolean deleteTransaction(long transactionId) {
        Transaction transaction = transactionRepo.findById(transactionId);
        if (transaction == null) return false;

        Account account = accountRepo.findById(transaction.getAccountId());
        if (account == null) return false;

        Connection conn = DatabaseManager.getConnection();

        try {
            conn.setAutoCommit(false);

            // Reverse the balance effect (subtract what was originally added)
            BigDecimal currentBalance = BigDecimal.valueOf(account.getBalance());
            BigDecimal reversal = BigDecimal.valueOf(transaction.getAmount()).negate();
            account.setBalance(currentBalance.add(reversal).doubleValue());

            accountRepo.update(account);
            transactionRepo.delete(transactionId);

            conn.commit();
            conn.setAutoCommit(true);
            return true;

        } catch (SQLException e) {
            safeRollback(conn);
            throw new RuntimeException("Failed to delete transaction: " + e.getMessage(), e);
        }
    }

    // ─── PRIVATE HELPERS ──────────────────────────────────────────────────────

    /**
     * Rolls back without throwing — used in catch blocks where we're
     * already handling another exception and don't want to lose it.
     */
    private void safeRollback(Connection conn) {
        try {
            conn.rollback();
            conn.setAutoCommit(true);
        } catch (SQLException ex) {
            System.err.println("Warning: rollback failed: " + ex.getMessage());
        }
    }
}