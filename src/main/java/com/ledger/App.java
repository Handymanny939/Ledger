package com.ledger;

import com.ledger.db.DatabaseManager;
import com.ledger.repository.*;
import com.ledger.service.*;
import com.ledger.ui.*;

import java.util.Scanner;

/**
 * Application orchestrator — wires all dependencies and runs the main loop.
 *
 * Dependency wiring order matters:
 *   1. Repositories (no dependencies)
 *   2. Services (depend on repositories)
 *   3. Menus (depend on services + InputHelper)
 *
 * This manual wiring is called "poor man's DI" (dependency injection).
 * In a larger app, a framework like Guice handles this automatically.
 */
public class App {

    public void start() {
        // ── Initialize database ───────────────────────────────────────────────
        DatabaseManager.getConnection(); // creates tables if first run

        // ── Wire repositories ─────────────────────────────────────────────────
        AccountRepository accountRepo     = new AccountRepository();
        TransactionRepository txRepo      = new TransactionRepository();
        BudgetRepository budgetRepo       = new BudgetRepository();

        // ── Wire services ─────────────────────────────────────────────────────
        BudgetService budgetService       = new BudgetService(budgetRepo, txRepo);
        AccountService accountService     = new AccountService(accountRepo);
        TransactionService txService      = new TransactionService(
                                               txRepo, accountRepo, budgetService);
        ReportService reportService       = new ReportService(accountRepo, budgetRepo);

        // ── Seed data on first run ────────────────────────────────────────────
        // Only seed if no accounts exist — prevents duplicates on restart
        if (accountService.getAllAccounts().isEmpty()) {
            SeedData.seed(accountService, budgetService, accountRepo, txRepo);
        }

        // ── Wire UI ───────────────────────────────────────────────────────────
        // One Scanner shared across all menus — never open two on System.in
        Scanner scanner   = new Scanner(System.in);
        InputHelper input  = new InputHelper(scanner);
        ReportPrinter printer = new ReportPrinter();

        AccountMenu accountMenu   = new AccountMenu(accountService, input);
        TransactionMenu txMenu    = new TransactionMenu(txService, accountService, input);
        BudgetMenu budgetMenu     = new BudgetMenu(budgetService, input);
        ReportMenu reportMenu     = new ReportMenu(reportService, printer, input);

        // ── Main menu loop ────────────────────────────────────────────────────
        printBanner();

        while (true) {
            printMainMenu();
            int choice = input.readInt("  Choose: ");
            System.out.println();

            switch (choice) {
                case 1 -> accountMenu.show();
                case 2 -> txMenu.show();
                case 3 -> budgetMenu.show();
                case 4 -> reportMenu.show();
                case 5 -> {
                    System.out.println("  Goodbye!");
                    DatabaseManager.closeConnection();
                    scanner.close();
                    return; // exit start(), then main() returns, JVM exits
                }
                default -> System.out.println("  [!] Please choose 1-5.");
            }
        }
    }

    // ─── PRIVATE HELPERS ──────────────────────────────────────────────────────

    private void printBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════╗");
        System.out.println("  ║         LEDGER v1.0                 ║");
        System.out.println("  ║    Personal Finance Tracker         ║");
        System.out.println("  ╚══════════════════════════════════════╝");
        System.out.println();
    }

    private void printMainMenu() {
        System.out.println();
        System.out.println("  ═══════════════ MAIN MENU ═══════════════");
        System.out.println("  1) Manage accounts");
        System.out.println("  2) Record transaction");
        System.out.println("  3) Manage budgets");
        System.out.println("  4) View reports");
        System.out.println("  5) Exit");
        System.out.println("  =========================================");
    }
}