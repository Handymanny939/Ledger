package com.ledger.ui;

import com.ledger.model.*;
import com.ledger.service.AccountService;
import com.ledger.service.TransactionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Handles transaction recording and viewing from the CLI.
 */
public class TransactionMenu {

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final InputHelper input;

    public TransactionMenu(TransactionService transactionService,
                           AccountService accountService,
                           InputHelper input) {
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.input = input;
    }

    public void show() {
        while (true) {
            System.out.println();
            System.out.println("  ── TRANSACTIONS ─────────────────────────────");
            System.out.println("  1) Record expense");
            System.out.println("  2) Record income");
            System.out.println("  3) View transactions for account");
            System.out.println("  4) Back to main menu");
            System.out.println();

            int choice = input.readInt("  Choose: ");
            System.out.println();

            switch (choice) {
                case 1 -> recordTransaction(false); // false = expense
                case 2 -> recordTransaction(true);  // true  = income
                case 3 -> viewTransactions();
                case 4 -> { return; }
                default -> System.out.println("  [!] Invalid choice.");
            }
        }
    }

    // ─── RECORD ───────────────────────────────────────────────────────────────

    private void recordTransaction(boolean isIncome) {
        String kind = isIncome ? "Income" : "Expense";
        System.out.println("  -- Record " + kind + " --");

        // Show accounts so user can pick by ID
        printAccountList();
        System.out.println();

        long accountId    = input.readInt("  Account ID: ");
        BigDecimal amount = input.readPositiveAmount("  Amount: $");
        Category category = input.readCategory("  Category:");
        String description = input.readString("  Description: ");
        LocalDate date    = input.readDate("  Date");

        // Expenses are negative amounts internally
        BigDecimal signedAmount = isIncome ? amount : amount.negate();

        try {
            Transaction t = transactionService.recordTransaction(
                accountId, signedAmount, category, description, date);
            System.out.printf(
                "%n  [OK] %s recorded: %s $%,.2f on %s (id=%d)%n",
                kind, category.getDisplayName(),
                amount, date, t.getId()
            );
        } catch (BudgetExceededException e) {
            System.out.println();
            System.out.println("  [!] BUDGET EXCEEDED: " + e.getMessage());
            System.out.println("      Record anyway?");
            if (input.readConfirm("      Override budget limit")) {
                // Force-insert via a direct repository call would go here.
                // For now we inform the user and let them adjust the budget first.
                System.out.println("  [i] Tip: Go to Manage Budgets to raise your limit.");
            }
        } catch (InsufficientFundsException e) {
            System.out.println("  [!] INSUFFICIENT FUNDS: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("  [!] " + e.getMessage());
        }
    }

    // ─── VIEW ─────────────────────────────────────────────────────────────────

    private void viewTransactions() {
        printAccountList();
        System.out.println();
        long accountId = input.readInt("  Account ID: ");

        List<Transaction> transactions =
            transactionService.getTransactionsForAccount(accountId);

        if (transactions.isEmpty()) {
            System.out.println("  No transactions found for this account.");
            return;
        }

        System.out.println();
        System.out.printf("  %-5s %-12s %-20s %-16s %10s%n",
            "ID", "Date", "Description", "Category", "Amount");
        System.out.println("  " + "-".repeat(67));

        for (Transaction t : transactions) {
            // Negative = expense (red in a real terminal), positive = income
            String amountStr = t.getAmount() < 0
                ? String.format("-$%,.2f", Math.abs(t.getAmount()))
                : String.format("+$%,.2f", t.getAmount());

            // Truncate description to 20 chars to keep columns aligned
            String desc = t.getDescription().length() > 20
                ? t.getDescription().substring(0, 19) + "~"
                : t.getDescription();

            System.out.printf("  %-5d %-12s %-20s %-16s %10s%n",
                t.getId(),
                t.getDate(),
                desc,
                t.getCategory().getDisplayName(),
                amountStr
            );
        }
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private void printAccountList() {
        List<Account> accounts = accountService.getAllAccounts();
        if (accounts.isEmpty()) {
            System.out.println("  No accounts found. Add one first.");
            return;
        }
        System.out.printf("  %-5s %-25s %-10s %12s%n",
            "ID", "Name", "Type", "Balance");
        System.out.println("  " + "-".repeat(55));
        for (Account a : accounts) {
            System.out.printf("  %-5d %-25s %-10s %12s%n",
                a.getId(), a.getName(), a.getType(),
                String.format("$%,.2f", a.getBalance())
            );
        }
    }
}