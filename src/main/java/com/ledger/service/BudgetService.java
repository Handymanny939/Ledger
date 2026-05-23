package com.ledger.service;

import com.ledger.model.Budget;
import com.ledger.model.Category;
import com.ledger.repository.BudgetRepository;
import com.ledger.repository.TransactionRepository;
import com.ledger.model.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Business logic for budget management and spending analysis.
 *
 * "Remaining budget" = monthly limit MINUS total expenses already recorded
 * in that category for that month.
 *
 * This service reads transactions to calculate spending — so it depends
 * on both BudgetRepository AND TransactionRepository.
 */
public class BudgetService {

    private final BudgetRepository budgetRepo;
    private final TransactionRepository transactionRepo;

    public BudgetService(BudgetRepository budgetRepo,
                         TransactionRepository transactionRepo) {
        this.budgetRepo = budgetRepo;
        this.transactionRepo = transactionRepo;
    }

    // ─── SET BUDGET ───────────────────────────────────────────────────────────

    /**
     * Creates or updates the monthly budget for a category.
     *
     * If a budget already exists for this category+month, we update its limit.
     * If not, we create a new one. This is called "upsert" logic.
     *
     * @param category     the spending category to cap
     * @param limit        the maximum allowed spend (must be > 0)
     * @param month        "YYYY-MM" format, e.g., "2025-06"
     */
    public Budget setBudget(Category category, BigDecimal limit, String month) {
        if (limit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Budget limit must be positive.");
        }

        // Validate month format loosely — a real app would use DateTimeFormatter
        if (!month.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("Month must be in YYYY-MM format. Got: " + month);
        }

        BigDecimal rounded = limit.setScale(2, RoundingMode.HALF_UP);

        // Upsert: update if exists, create if not
        Budget existing = budgetRepo.findByCategory(category, month);
        if (existing != null) {
            existing.setMonthlyLimit(rounded.doubleValue());
            budgetRepo.update(existing);
            return existing;
        } else {
            Budget budget = new Budget(category, rounded.doubleValue(), month);
            return budgetRepo.create(budget);
        }
    }

    // ─── SPENDING CALCULATIONS ────────────────────────────────────────────────

    /**
     * Calculates total money spent in a category during a month.
     *
     * We fetch all transactions for the category, filter to the target month,
     * then sum only the negative amounts (expenses).
     *
     * "2025-06" matches dates starting with "2025-06-" → June 2025.
     */
    public BigDecimal getTotalSpent(Category category, String month) {
        List<Transaction> transactions = transactionRepo.findByCategory(category);

        return transactions.stream()
            // Keep only transactions in the target month
            .filter(t -> t.getDate().toString().startsWith(month))
            // Keep only expenses (negative amounts)
            .filter(t -> t.getAmount() < 0)
            // Sum the absolute values
            .map(t -> BigDecimal.valueOf(Math.abs(t.getAmount())))
            // reduce() folds the stream into a single value starting from ZERO
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Returns how much budget is left for a category in a given month.
     *
     * If no budget is set, returns BigDecimal.ZERO (treat as uncapped —
     * callers should check for null budget before calling this).
     */
    public BigDecimal getRemainingBudget(Category category, String month) {
        Budget budget = budgetRepo.findByCategory(category, month);
        if (budget == null) {
            // No budget set → remaining is effectively unlimited
            // Return a large sentinel value so callers don't treat it as 0
            return new BigDecimal("999999.99");
        }

        BigDecimal limit = BigDecimal.valueOf(budget.getMonthlyLimit());
        BigDecimal spent = getTotalSpent(category, month);

        // Clamp at zero — don't return negative remaining (already over budget)
        BigDecimal remaining = limit.subtract(spent);
        return remaining.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Returns the monthly limit for a category, or ZERO if no budget set.
     * Used by TransactionService when building a BudgetExceededException.
     */
    public BigDecimal getBudgetLimit(Category category, String month) {
        Budget budget = budgetRepo.findByCategory(category, month);
        if (budget == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(budget.getMonthlyLimit());
    }

    /**
     * Returns true if adding 'expenseAmount' would exceed the monthly budget.
     * Called by TransactionService BEFORE recording an expense.
     *
     * If no budget is set for this category, always returns false (uncapped).
     */
    public boolean wouldExceedBudget(Category category, String month,
                                      BigDecimal expenseAmount) {
        Budget budget = budgetRepo.findByCategory(category, month);
        if (budget == null) return false; // no cap set → never exceeds

        BigDecimal spent     = getTotalSpent(category, month);
        BigDecimal limit     = BigDecimal.valueOf(budget.getMonthlyLimit());
        BigDecimal projected = spent.add(expenseAmount); // what total would be

        // compareTo > 0 means projected > limit
        return projected.compareTo(limit) > 0;
    }

    /**
     * Returns true if the budget for this category/month is already exceeded.
     * Used for reporting — different from wouldExceedBudget (past vs future).
     */
    public boolean isOverBudget(Category category, String month) {
        Budget budget = budgetRepo.findByCategory(category, month);
        if (budget == null) return false;

        BigDecimal spent = getTotalSpent(category, month);
        BigDecimal limit = BigDecimal.valueOf(budget.getMonthlyLimit());
        return spent.compareTo(limit) > 0;
    }

    /**
     * Returns all budgets for a month with their spending status.
     */
    public List<Budget> getBudgetsForMonth(String month) {
        return budgetRepo.findByMonth(month);
    }
}