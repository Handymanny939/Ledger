package com.ledger.ui;

import com.ledger.model.Account;
import com.ledger.model.InsufficientFundsException;
import com.ledger.service.AccountService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Handles all account-related menu interactions.
 * Each menu method loops until the user chooses to go back.
 *
 * Design: menus print options, read a choice, delegate to a service,
 * print the result, then loop. They never contain business logic.
 */
public class AccountMenu {

    private final AccountService accountService;
    private final InputHelper input;

    public AccountMenu(AccountService accountService, InputHelper input) {
        this.accountService = accountService;
        this.input = input;
    }

    public void show() {
        while (true) {
            System.out.println();
            System.out.println("  ── MANAGE ACCOUNTS ──────────────────────────");
            System.out.println("  1) View all accounts");
            System.out.println("  2) Add new account");
            System.out.println("  3) Deposit");
            System.out.println("  4) Withdraw");
            System.out.println("  5) Transfer between accounts");
            System.out.println("  6) Back to main menu");
            System.out.println();

            int choice = input.readInt("  Choose: ");
            System.out.println();

            switch (choice) {
                case 1 -> viewAccounts();
                case 2 -> addAccount();
                case 3 -> deposit();
                case 4 -> withdraw();
                case 5 -> transfer();
                case 6 -> { return; } // exit the while(true) loop
                default -> System.out.println("  [!] Invalid choice.");
            }
        }
    }

    // ─── VIEW ─────────────────────────────────────────────────────────────────

    private void viewAccounts() {
        List<Account> accounts = accountService.getAllAccounts();
        if (accounts.isEmpty()) {
            System.out.println("  No accounts yet. Add one first.");
            return;
        }
        System.out.printf("  %-5s %-25s %-10s %12s%n",
            "ID", "Name", "Type", "Balance");
        System.out.println("  " + "-".repeat(55));
        for (Account a : accounts) {
            System.out.printf("  %-5d %-25s %-10s %12s%n",
                a.getId(),
                a.getName(),
                a.getType(),
                String.format("$%,.2f", a.getBalance())
            );
        }
    }

    // ─── ADD ──────────────────────────────────────────────────────────────────

    private void addAccount() {
        System.out.println("  -- New Account --");
        String name          = input.readString("  Account name: ");
        String type          = input.readAccountType("  Account type:");
        BigDecimal balance   = input.readPositiveAmount("  Opening balance: $");

        Account created = accountService.createAccount(name, type, balance);
        System.out.printf("  [OK] Account '%s' created with balance $%,.2f (id=%d)%n",
            created.getName(), created.getBalance(), created.getId());
    }

    // ─── DEPOSIT ──────────────────────────────────────────────────────────────

    private void deposit() {
        viewAccounts();
        System.out.println();
        long id             = input.readInt("  Account ID to deposit into: ");
        BigDecimal amount   = input.readPositiveAmount("  Amount to deposit: $");

        try {
            Account updated = accountService.deposit(id, amount);
            System.out.printf("  [OK] Deposited $%,.2f -> new balance: $%,.2f%n",
                amount, updated.getBalance());
        } catch (IllegalArgumentException e) {
            System.out.println("  [!] " + e.getMessage());
        }
    }

    // ─── WITHDRAW ─────────────────────────────────────────────────────────────

    private void withdraw() {
        viewAccounts();
        System.out.println();
        long id             = input.readInt("  Account ID to withdraw from: ");
        BigDecimal amount   = input.readPositiveAmount("  Amount to withdraw: $");

        try {
            Account updated = accountService.withdraw(id, amount);
            System.out.printf("  [OK] Withdrew $%,.2f -> new balance: $%,.2f%n",
                amount, updated.getBalance());
        } catch (InsufficientFundsException e) {
            System.out.println("  [!] " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("  [!] " + e.getMessage());
        }
    }

    // ─── TRANSFER ─────────────────────────────────────────────────────────────

    private void transfer() {
        viewAccounts();
        System.out.println();
        long fromId         = input.readInt("  Transfer FROM account ID: ");
        long toId           = input.readInt("  Transfer TO account ID:   ");
        BigDecimal amount   = input.readPositiveAmount("  Amount to transfer: $");

        try {
            accountService.transfer(fromId, toId, amount);
            System.out.printf("  [OK] Transferred $%,.2f from account %d to account %d%n",
                amount, fromId, toId);
        } catch (InsufficientFundsException | IllegalArgumentException e) {
            System.out.println("  [!] " + e.getMessage());
        }
    }
}