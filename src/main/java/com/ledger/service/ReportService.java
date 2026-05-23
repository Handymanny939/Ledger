package com.ledger.service;

import com.ledger.db.DatabaseManager;
import com.ledger.model.*;
import com.ledger.repository.AccountRepository;
import com.ledger.repository.BudgetRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates all report data using SQL aggregations.
 *
 * Design rule: ReportService produces DTOs (data, no formatting).
 * ReportPrinter consumes DTOs (formatting, no data logic).
 * This separation means you could swap CLI output for a PDF or HTML
 * report without touching ReportService at all.
 *
 * SQL is used for all GROUP BY aggregations — never load all rows
 * into Java just to sum them. Let the database engine do that work.
 */
public class ReportService {

    private final AccountRepository accountRepo;
    private final BudgetRepository budgetRepo;

    public ReportService(AccountRepository accountRepo,
                         BudgetRepository budgetRepo) {
        this.accountRepo = accountRepo;
        this.budgetRepo  = budgetRepo;
    }

    // ─── REPORT 1: MONTHLY SUMMARY ────────────────────────────────────────────

    /**
     * Generates a full monthly summary for a given month.
     *
     * SQL strategy:
     *   - One query for total income (SUM where amount > 0)
     *   - One query for total expenses (SUM where amount < 0)
     *   - One GROUP BY query for the per-category breakdown
     *
     * All filtered to the month using: date LIKE '2026-05%'
     * The LIKE pattern matches all days in the month safely.
     */
    public MonthlySummary getMonthlySummary(String month) {
        BigDecimal totalIncome   = sumTransactions(month, true);  // positive
        BigDecimal totalExpenses = sumTransactions(month, false); // negative → positive

        // net = income minus expenses (can be negative if you spent more than earned)
        BigDecimal netCashFlow = totalIncome.subtract(totalExpenses)
                                            .setScale(2, RoundingMode.HALF_UP);

        List<CategorySpend> breakdown = getCategoryBreakdown(month);

        return new MonthlySummary(month, totalIncome, totalExpenses,
                                  netCashFlow, breakdown);
    }

    /**
     * SUMs all transactions for a month, either income or expenses.
     *
     * @param incomeOnly true → SUM(amount) WHERE amount > 0
     *                   false → SUM(ABS(amount)) WHERE amount < 0
     */
    private BigDecimal sumTransactions(String month, boolean incomeOnly) {
        // ABS() converts negative expense amounts to positive for display
        String condition = incomeOnly ? "amount > 0" : "amount < 0";
        String sql = """
            SELECT COALESCE(SUM(ABS(amount)), 0) as total
            FROM transactions
            WHERE date LIKE ? AND %s
        """.formatted(condition);
        // COALESCE(SUM(...), 0) returns 0 instead of NULL when no rows match

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setString(1, month + "%"); // "2026-05%" matches all days in May
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("total")
                             .setScale(2, RoundingMode.HALF_UP);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sum transactions: " + e.getMessage(), e);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Returns per-category expense totals for a month, sorted by spend desc.
     *
     * GROUP BY category aggregates all expense rows per category into one row.
     * COUNT(*) gives us the transaction count for free in the same query.
     */
    private List<CategorySpend> getCategoryBreakdown(String month) {
        String sql = """
            SELECT category,
                   SUM(ABS(amount))  as total_spent,
                   COUNT(*)          as tx_count
            FROM transactions
            WHERE date LIKE ? AND amount < 0
            GROUP BY category
            ORDER BY total_spent DESC
        """;

        List<CategorySpend> results = new ArrayList<>();

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setString(1, month + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new CategorySpend(
                        Category.fromString(rs.getString("category")),
                        rs.getBigDecimal("total_spent")
                          .setScale(2, RoundingMode.HALF_UP),
                        rs.getInt("tx_count")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get category breakdown: " + e.getMessage(), e);
        }

        return results;
    }

    // ─── REPORT 2: BUDGET STATUS ──────────────────────────────────────────────

    /**
     * Generates budget status for every category for a given month.
     *
     * For categories WITH a budget → show limit, spent, remaining, % bar.
     * For categories WITHOUT a budget but WITH spending → show as unbudgeted.
     * For categories with neither → omit entirely (no noise).
     *
     * Strategy:
     *   1. Load all budgets for the month
     *   2. Load all category spending for the month (via SQL GROUP BY)
     *   3. Merge them — budget may exist without spending, spending may
     *      exist without a budget
     */
    public List<BudgetStatus> getBudgetStatus(String month) {
        // Map of category → budget (may be empty if no budgets set)
        Map<Category, Budget> budgetMap = budgetRepo.findByMonth(month)
            .stream()
            .collect(Collectors.toMap(Budget::getCategory, b -> b));

        // Map of category → amount spent (from SQL GROUP BY)
        Map<Category, BigDecimal> spendMap = getCategorySpendMap(month);

        // Union of all categories that appear in either map
        Set<Category> allCategories = new HashSet<>();
        allCategories.addAll(budgetMap.keySet());
        allCategories.addAll(spendMap.keySet());

        List<BudgetStatus> statuses = new ArrayList<>();

        for (Category category : allCategories) {
            Budget budget = budgetMap.get(category);
            // Default to ZERO if no spending recorded for this category
            BigDecimal spent = spendMap.getOrDefault(category, BigDecimal.ZERO)
                                       .setScale(2, RoundingMode.HALF_UP);

            if (budget != null) {
                BigDecimal limit     = BigDecimal.valueOf(budget.getMonthlyLimit());
                BigDecimal remaining = limit.subtract(spent)
                                           .setScale(2, RoundingMode.HALF_UP);
                statuses.add(new BudgetStatus(category, limit, spent,
                                              remaining, true));
            } else {
                // Spending exists but no budget cap set
                statuses.add(new BudgetStatus(category, BigDecimal.ZERO,
                                              spent, BigDecimal.ZERO, false));
            }
        }

        // Sort: over-budget first, then by % used desc, then alphabetically
        statuses.sort(Comparator
            .comparing(BudgetStatus::isOverBudget, Comparator.reverseOrder())
            .thenComparing(BudgetStatus::percentUsed, Comparator.reverseOrder())
            .thenComparing(s -> s.category().name()));

        return statuses;
    }

    /**
     * Returns a Map of Category → total spent (expenses only) for a month.
     * Uses SQL GROUP BY — one round trip to the database for all categories.
     */
    private Map<Category, BigDecimal> getCategorySpendMap(String month) {
        String sql = """
            SELECT category, SUM(ABS(amount)) as total_spent
            FROM transactions
            WHERE date LIKE ? AND amount < 0
            GROUP BY category
        """;

        Map<Category, BigDecimal> map = new HashMap<>();

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setString(1, month + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(
                        Category.fromString(rs.getString("category")),
                        rs.getBigDecimal("total_spent")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get spend map: " + e.getMessage(), e);
        }

        return map;
    }

    // ─── REPORT 3: ACCOUNT BALANCES OVERVIEW ─────────────────────────────────

    /**
     * Returns all accounts as AccountSummary DTOs plus the total net worth.
     * Net worth = sum of all account balances.
     *
     * We return a List<AccountSummary> — the net worth is computed by
     * ReportPrinter by summing the list (avoids an extra DB query).
     */
    public List<AccountSummary> getAccountOverview() {
        return accountRepo.findAll()
            .stream()
            .map(a -> new AccountSummary(
                a.getName(),
                a.getType(),
                BigDecimal.valueOf(a.getBalance()).setScale(2, RoundingMode.HALF_UP)
            ))
            // Sort: CHECKING first, then SAVINGS, then CREDIT
            .sorted(Comparator.comparing(s -> accountTypeOrder(s.type())))
            .collect(Collectors.toList());
    }

    /** Maps account type to a sort order integer */
    private int accountTypeOrder(String type) {
        return switch (type) {
            case "CHECKING" -> 0;
            case "SAVINGS"  -> 1;
            case "CREDIT"   -> 2;
            default         -> 3;
        };
    }

    // ─── REPORT 4: TOP SPENDING CATEGORIES ───────────────────────────────────

    /**
     * Returns the top N spending categories for a date range.
     *
     * Uses a date range (from/to) rather than a month string — more flexible.
     * SQL BETWEEN handles the range; GROUP BY aggregates per category.
     *
     * @param from  start date (inclusive)
     * @param to    end date (inclusive)
     * @param limit how many top categories to return (e.g., 5 for "Top 5")
     */
    public List<CategorySpend> getTopSpendingCategories(LocalDate from,
                                                         LocalDate to,
                                                         int limit) {
        String sql = """
            SELECT category,
                   SUM(ABS(amount)) as total_spent,
                   COUNT(*)         as tx_count
            FROM transactions
            WHERE date BETWEEN ? AND ?
              AND amount < 0
            GROUP BY category
            ORDER BY total_spent DESC
            LIMIT ?
        """;

        List<CategorySpend> results = new ArrayList<>();

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql)) {

            ps.setString(1, from.toString()); // "2026-05-01"
            ps.setString(2, to.toString());   // "2026-05-31"
            ps.setInt(3, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new CategorySpend(
                        Category.fromString(rs.getString("category")),
                        rs.getBigDecimal("total_spent")
                          .setScale(2, RoundingMode.HALF_UP),
                        rs.getInt("tx_count")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get top spending categories: " + e.getMessage(), e);
        }

        return results;
    }
}