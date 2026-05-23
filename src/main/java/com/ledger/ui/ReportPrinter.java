package com.ledger.ui;

import com.ledger.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Formats report DTOs as human-readable text tables.
 *
 * Design rule: zero business logic here. ReportPrinter only knows
 * how to turn numbers into aligned strings. If a number is wrong,
 * the bug is in ReportService — not here.
 *
 * String.format() cheat sheet used throughout:
 *   %-20s  → left-align string in 20-char field
 *   %10.2f → right-align float in 10-char field, 2 decimal places
 *   %3d    → right-align int in 3-char field
 */
public class ReportPrinter {

    // ── Shared formatting constants ───────────────────────────────────────────
    private static final String DIVIDER =
        "  " + "─".repeat(65);
    private static final String DOUBLE_DIVIDER =
        "  " + "═".repeat(65);

    // ─── REPORT 1: MONTHLY SUMMARY ────────────────────────────────────────────

    public void printMonthlySummary(MonthlySummary summary) {
        printHeader("MONTHLY SUMMARY — " + formatMonth(summary.month()));

        // Top-line numbers
        System.out.printf("  %-20s %s%n", "Total Income:",
            formatMoney(summary.totalIncome()));
        System.out.printf("  %-20s %s%n", "Total Expenses:",
            formatMoney(summary.totalExpenses()));
        System.out.println(DIVIDER);
        System.out.printf("  %-20s %s%n", "Net Cash Flow:",
            formatSignedMoney(summary.netCashFlow()));
        System.out.println();

        // Per-category breakdown
        if (summary.categoryBreakdown().isEmpty()) {
            System.out.println("  No expense transactions recorded this month.");
        } else {
            System.out.printf("  %-22s %10s   %5s%n",
                "Category", "Spent", "Txns");
            System.out.println(DIVIDER);

            for (CategorySpend cs : summary.categoryBreakdown()) {
                System.out.printf("  %-22s %10s   %3d transaction%s%n",
                    cs.category().getDisplayName(),
                    formatMoney(cs.totalSpent()),
                    cs.transactionCount(),
                    cs.transactionCount() == 1 ? "" : "s" // "1 transaction" not "1 transactions"
                );
            }
        }

        printFooter();
    }

    // ─── REPORT 2: BUDGET STATUS ──────────────────────────────────────────────

    public void printBudgetStatus(List<BudgetStatus> statuses, String month) {
        printHeader("BUDGET STATUS — " + formatMonth(month));

        // Separate budgeted from unbudgeted
        List<BudgetStatus> budgeted   = statuses.stream()
            .filter(BudgetStatus::hasBudget).toList();
        List<BudgetStatus> unbudgeted = statuses.stream()
            .filter(s -> !s.hasBudget()).toList();

        if (budgeted.isEmpty()) {
            System.out.println("  No budgets set for this month.");
        } else {
            // Column headers
            System.out.printf("  %-18s %8s %8s %9s   %s%n",
                "Category", "Limit", "Spent", "Remaining", "Usage");
            System.out.println(DIVIDER);

            BigDecimal totalLimit     = BigDecimal.ZERO;
            BigDecimal totalSpent     = BigDecimal.ZERO;
            BigDecimal totalRemaining = BigDecimal.ZERO;

            for (BudgetStatus s : budgeted) {
                String bar    = buildProgressBar(s.percentUsed(), 10);
                String status = s.isOverBudget()  ? "  ✗ OVER BUDGET"  :
                                s.isAtLimit()     ? "  ! LIMIT REACHED" : "";

                System.out.printf("  %-18s %8s %8s %9s   [%s] %3d%%%s%n",
                    truncate(s.category().getDisplayName(), 18),
                    formatMoney(s.limit()),
                    formatMoney(s.spent()),
                    formatSignedMoney(s.remaining()),
                    bar,
                    s.percentUsed(),
                    status
                );

                // Running totals for the summary row
                totalLimit     = totalLimit.add(s.limit());
                totalSpent     = totalSpent.add(s.spent());
                totalRemaining = totalRemaining.add(s.remaining());
            }

            // Summary row
            System.out.println(DIVIDER);
            System.out.printf("  %-18s %8s %8s %9s%n",
                "TOTAL",
                formatMoney(totalLimit),
                formatMoney(totalSpent),
                formatSignedMoney(totalRemaining)
            );
        }

        // Unbudgeted spending
        if (!unbudgeted.isEmpty()) {
            System.out.println();
            System.out.println("  Spending with no budget set:");
            for (BudgetStatus s : unbudgeted) {
                System.out.printf("    %-22s %s (no limit set)%n",
                    s.category().getDisplayName(),
                    formatMoney(s.spent())
                );
            }
        }

        printFooter();
    }

    // ─── REPORT 3: ACCOUNT OVERVIEW ───────────────────────────────────────────

    public void printAccountOverview(List<AccountSummary> accounts) {
        printHeader("ACCOUNT BALANCES OVERVIEW");

        if (accounts.isEmpty()) {
            System.out.println("  No accounts found.");
            printFooter();
            return;
        }

        System.out.printf("  %-25s %-10s %12s%n", "Account", "Type", "Balance");
        System.out.println(DIVIDER);

        BigDecimal netWorth = BigDecimal.ZERO;

        for (AccountSummary a : accounts) {
            System.out.printf("  %-25s %-10s %12s%n",
                truncate(a.name(), 25),
                a.type(),
                formatMoney(a.balance())
            );
            netWorth = netWorth.add(a.balance());
        }

        System.out.println(DIVIDER);
        System.out.printf("  %-25s %-10s %12s%n",
            "NET WORTH", "", formatSignedMoney(netWorth));
        printFooter();
    }

    // ─── REPORT 4: TOP SPENDING CATEGORIES ───────────────────────────────────

    public void printTopSpendingCategories(List<CategorySpend> categories,
                                            String fromDate, String toDate) {
        printHeader("TOP SPENDING CATEGORIES");
        System.out.println("  Period: " + fromDate + " to " + toDate);
        System.out.println();

        if (categories.isEmpty()) {
            System.out.println("  No expense transactions in this period.");
            printFooter();
            return;
        }

        // Find the max spend for scaling the bar chart
        BigDecimal max = categories.get(0).totalSpent(); // already sorted desc

        System.out.printf("  %-3s %-20s %10s  %s%n",
            "#", "Category", "Spent", "Relative Spend");
        System.out.println(DIVIDER);

        int rank = 1;
        for (CategorySpend cs : categories) {
            // Scale bar to max 20 chars wide relative to the top spender
            int barLen = max.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                cs.totalSpent()
                  .multiply(BigDecimal.valueOf(20))
                  .divide(max, 0, RoundingMode.HALF_UP)
                  .intValue();

            String bar = "█".repeat(barLen);

            System.out.printf("  %2d. %-20s %10s  %s (%d tx)%n",
                rank++,
                truncate(cs.category().getDisplayName(), 20),
                formatMoney(cs.totalSpent()),
                bar,
                cs.transactionCount()
            );
        }

        printFooter();
    }

    // ─── PRIVATE FORMATTING HELPERS ───────────────────────────────────────────

    /**
     * Builds a fixed-width progress bar string.
     * e.g., buildProgressBar(75, 10) → "███████░░░"
     *
     * @param percent  0–999 (capped internally to width)
     * @param width    total number of characters in the bar
     */
    private String buildProgressBar(int percent, int width) {
        // How many filled blocks? Cap at width so over-budget doesn't overflow
        int filled = Math.min((int) Math.round(percent / 100.0 * width), width);
        int empty  = width - filled;
        return "█".repeat(filled) + "░".repeat(empty);
    }

    /**
     * Formats a positive money value: "$1,234.56"
     * Always shows exactly 2 decimal places.
     */
    private String formatMoney(BigDecimal amount) {
        return String.format("$%,.2f", amount.doubleValue());
    }

    /**
     * Formats money with an explicit sign for net/remaining values.
     * Positive → green-ish: "+$500.00"
     * Negative → shows as "-$40.00"
     */
    private String formatSignedMoney(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) >= 0) {
            return String.format("+$%,.2f", amount.doubleValue());
        } else {
            // String.format already adds the minus sign for negative doubles
            return String.format("-$%,.2f", amount.abs().doubleValue());
        }
    }

    /**
     * Converts "2026-05" to "May 2026" for display.
     */
    private String formatMonth(String yearMonth) {
        String[] parts = yearMonth.split("-");
        String[] months = {"", "January", "February", "March", "April",
                           "May", "June", "July", "August", "September",
                           "October", "November", "December"};
        int monthNum = Integer.parseInt(parts[1]);
        return months[monthNum] + " " + parts[0];
    }

    /**
     * Truncates a string to maxLen characters to keep table columns aligned.
     * Adds "…" if truncated so the user knows something was cut.
     */
    private String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 1) + "…";
    }

    private void printHeader(String title) {
        System.out.println();
        System.out.println(DOUBLE_DIVIDER);
        System.out.printf("  %s%n", title);
        System.out.println(DOUBLE_DIVIDER);
        System.out.println();
    }

    private void printFooter() {
        System.out.println(DIVIDER);
        System.out.println();
    }
}