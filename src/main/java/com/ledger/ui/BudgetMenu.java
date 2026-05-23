package com.ledger.ui;

import com.ledger.model.Budget;
import com.ledger.model.Category;
import com.ledger.service.BudgetService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Handles budget setting and viewing from the CLI.
 */
public class BudgetMenu {

    private final BudgetService budgetService;
    private final InputHelper input;

    public BudgetMenu(BudgetService budgetService, InputHelper input) {
        this.budgetService = budgetService;
        this.input = input;
    }

    public void show() {
        while (true) {
            System.out.println();
            System.out.println("  ── MANAGE BUDGETS ───────────────────────────");
            System.out.println("  1) View budgets for a month");
            System.out.println("  2) Set / update a budget");
            System.out.println("  3) Check remaining budget for category");
            System.out.println("  4) Back to main menu");
            System.out.println();

            int choice = input.readInt("  Choose: ");
            System.out.println();

            switch (choice) {
                case 1 -> viewBudgets();
                case 2 -> setBudget();
                case 3 -> checkRemaining();
                case 4 -> { return; }
                default -> System.out.println("  [!] Invalid choice.");
            }
        }
    }

    // ─── VIEW ─────────────────────────────────────────────────────────────────

    private void viewBudgets() {
        String month = input.readMonth("  Month");
        List<Budget> budgets = budgetService.getBudgetsForMonth(month);

        if (budgets.isEmpty()) {
            System.out.println("  No budgets set for " + month + ".");
            return;
        }

        System.out.println();
        System.out.printf("  %-22s %12s%n", "Category", "Monthly Limit");
        System.out.println("  " + "-".repeat(36));
        for (Budget b : budgets) {
            System.out.printf("  %-22s %12s%n",
                b.getCategory().getDisplayName(),
                String.format("$%,.2f", b.getMonthlyLimit())
            );
        }
    }

    // ─── SET ──────────────────────────────────────────────────────────────────

    private void setBudget() {
        System.out.println("  -- Set Budget --");
        Category category  = input.readCategory("  Category:");
        String month       = input.readMonth("  Month");
        BigDecimal limit   = input.readPositiveAmount("  Monthly limit: $");

        Budget budget = budgetService.setBudget(category, limit, month);
        System.out.printf("  [OK] Budget set: %s = $%,.2f for %s (id=%d)%n",
            category.getDisplayName(), limit, month, budget.getId());
    }

    // ─── CHECK REMAINING ──────────────────────────────────────────────────────

    private void checkRemaining() {
        Category category  = input.readCategory("  Category:");
        String month       = input.readMonth("  Month");

        BigDecimal spent     = budgetService.getTotalSpent(category, month);
        BigDecimal remaining = budgetService.getRemainingBudget(category, month);
        boolean overBudget   = budgetService.isOverBudget(category, month);

        System.out.println();
        System.out.printf("  Category : %s%n", category.getDisplayName());
        System.out.printf("  Month    : %s%n", month);
        System.out.printf("  Spent    : $%,.2f%n", spent);

        if (remaining.compareTo(new BigDecimal("999999.99")) == 0) {
            System.out.println("  Remaining: No budget set (unlimited)");
        } else {
            System.out.printf("  Remaining: $%,.2f%n", remaining);
            System.out.printf("  Status   : %s%n",
                overBudget ? "X OVER BUDGET" : "OK within limit");
        }
    }
}