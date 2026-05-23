package com.ledger.ui;

import com.ledger.model.Category;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/**
 * Centralized input reading and validation for the CLI.
 *
 * All methods loop until valid input is received — the user cannot
 * proceed past a prompt by entering garbage. This prevents
 * NumberFormatException and DateTimeParseException from crashing the app.
 *
 * A single shared Scanner is passed in — never create multiple Scanners
 * on System.in. Only one Scanner should own stdin at a time.
 */
public class InputHelper {

    private final Scanner scanner;
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public InputHelper(Scanner scanner) {
        this.scanner = scanner;
    }

    // ─── STRING ───────────────────────────────────────────────────────────────

    /**
     * Reads a non-blank string. Re-prompts if the user just hits Enter.
     */
    public String readString(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (!input.isBlank()) return input;
            System.out.println("  [!] Input cannot be empty. Please try again.");
        }
    }

    // ─── INTEGER ──────────────────────────────────────────────────────────────

    /**
     * Reads a positive integer (used for menu choices and list indices).
     * Rejects floats, text, and negative numbers.
     */
    public int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(input);
                if (value >= 0) return value;
                System.out.println("  [!] Please enter a non-negative number.");
            } catch (NumberFormatException e) {
                System.out.println("  [!] Invalid number: '" + input + "'. Please try again.");
            }
        }
    }

    // ─── BIGDECIMAL (MONEY) ───────────────────────────────────────────────────

    /**
     * Reads a positive monetary amount using BigDecimal for precision.
     * Rejects zero, negative values, and non-numeric input.
     *
     * We parse into BigDecimal directly (not double) to avoid the
     * floating-point imprecision introduced by Double.parseDouble().
     */
    public BigDecimal readPositiveAmount(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                BigDecimal value = new BigDecimal(input);
                if (value.compareTo(BigDecimal.ZERO) > 0) return value;
                System.out.println("  [!] Amount must be greater than zero.");
            } catch (NumberFormatException e) {
                System.out.println("  [!] Invalid amount: '" + input + "'. " +
                    "Enter a number like 42.50");
            }
        }
    }

    // ─── DATE ─────────────────────────────────────────────────────────────────

    /**
     * Reads a date in yyyy-MM-dd format. Re-prompts on bad format.
     * Offers "today" as a shortcut — the user types "today" to use today's date.
     */
    public LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt + " (yyyy-MM-dd or 'today'): ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("today")) return LocalDate.now();
            try {
                return LocalDate.parse(input, DATE_FORMAT);
            } catch (DateTimeParseException e) {
                System.out.println("  [!] Invalid date: '" + input +
                    "'. Use format yyyy-MM-dd, e.g. 2026-05-01");
            }
        }
    }

    // ─── MONTH ────────────────────────────────────────────────────────────────

    /**
     * Reads a month string in yyyy-MM format.
     * Offers "now" as a shortcut for the current month.
     */
    public String readMonth(String prompt) {
        while (true) {
            System.out.print(prompt + " (yyyy-MM or 'now'): ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("now")) {
                return LocalDate.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM"));
            }
            if (input.matches("\\d{4}-\\d{2}")) return input;
            System.out.println("  [!] Invalid month: '" + input +
                "'. Use format yyyy-MM, e.g. 2026-05");
        }
    }

    // ─── CATEGORY ─────────────────────────────────────────────────────────────

    /**
     * Displays a numbered list of categories and reads the user's choice.
     * Validates that the choice is within the valid range.
     *
     * Using ordinal numbers (1-based) instead of raw enum names
     * makes the CLI friendlier — no typing "ENTERTAINMENT" exactly.
     */
    public Category readCategory(String prompt) {
        Category[] categories = Category.values();
        System.out.println(prompt);
        for (int i = 0; i < categories.length; i++) {
            // Right-align the index number for a clean list
            System.out.printf("    %d) %s%n", i + 1,
                categories[i].getDisplayName());
        }
        while (true) {
            System.out.print("  Enter number (1-" + categories.length + "): ");
            String input = scanner.nextLine().trim();
            try {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= categories.length) {
                    return categories[choice - 1]; // convert 1-based to 0-based
                }
                System.out.println("  [!] Choose between 1 and " + categories.length);
            } catch (NumberFormatException e) {
                System.out.println("  [!] Enter a number, not text.");
            }
        }
    }

    // ─── ACCOUNT TYPE ─────────────────────────────────────────────────────────

    /**
     * Reads and validates an account type (CHECKING, SAVINGS, CREDIT).
     */
    public String readAccountType(String prompt) {
        System.out.println(prompt);
        System.out.println("    1) CHECKING");
        System.out.println("    2) SAVINGS");
        System.out.println("    3) CREDIT");
        while (true) {
            System.out.print("  Enter number (1-3): ");
            String input = scanner.nextLine().trim();
            switch (input) {
                case "1" -> { return "CHECKING"; }
                case "2" -> { return "SAVINGS"; }
                case "3" -> { return "CREDIT"; }
                default  -> System.out.println("  [!] Enter 1, 2, or 3.");
            }
        }
    }

    // ─── YES/NO ───────────────────────────────────────────────────────────────

    /**
     * Reads a yes/no confirmation. Accepts y/yes/n/no (case-insensitive).
     */
    public boolean readConfirm(String prompt) {
        while (true) {
            System.out.print(prompt + " (y/n): ");
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.equals("y") || input.equals("yes")) return true;
            if (input.equals("n") || input.equals("no"))  return false;
            System.out.println("  [!] Enter y or n.");
        }
    }
}