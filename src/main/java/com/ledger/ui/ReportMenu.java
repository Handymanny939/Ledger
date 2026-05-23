package com.ledger.ui;

import com.ledger.service.ReportService;

import java.time.LocalDate;

/**
 * Handles report selection and printing from the CLI.
 * All heavy lifting is done by ReportService + ReportPrinter.
 */
public class ReportMenu {

    private final ReportService reportService;
    private final ReportPrinter printer;
    private final InputHelper input;

    public ReportMenu(ReportService reportService,
                      ReportPrinter printer,
                      InputHelper input) {
        this.reportService = reportService;
        this.printer = printer;
        this.input = input;
    }

    public void show() {
        while (true) {
            System.out.println();
            System.out.println("  ── VIEW REPORTS ─────────────────────────────");
            System.out.println("  1) Monthly summary");
            System.out.println("  2) Budget status");
            System.out.println("  3) Account balances overview");
            System.out.println("  4) Top spending categories");
            System.out.println("  5) Back to main menu");
            System.out.println();

            int choice = input.readInt("  Choose: ");

            switch (choice) {
                case 1 -> {
                    String month = input.readMonth("  Month");
                    printer.printMonthlySummary(
                        reportService.getMonthlySummary(month));
                }
                case 2 -> {
                    String month = input.readMonth("  Month");
                    printer.printBudgetStatus(
                        reportService.getBudgetStatus(month), month);
                }
                case 3 -> printer.printAccountOverview(
                    reportService.getAccountOverview());
                case 4 -> {
                    System.out.println();
                    LocalDate from = input.readDate("  From date");
                    LocalDate to   = input.readDate("  To date  ");
                    int limit      = input.readInt("  How many top categories (e.g. 5): ");
                    printer.printTopSpendingCategories(
                        reportService.getTopSpendingCategories(from, to, limit),
                        from.toString(), to.toString());
                }
                case 5 -> { return; }
                default -> System.out.println("  [!] Invalid choice.");
            }
        }
    }
}