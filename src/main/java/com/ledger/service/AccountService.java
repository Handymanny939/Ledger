package com.ledger.service;

import com.ledger.model.Account;
import com.ledger.model.InsufficientFundsException;
import com.ledger.repository.AccountRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Business logic for account management.
 *
 * All money math uses BigDecimal — balances are rounded to 2 decimal
 * places using HALF_UP (standard financial rounding: 0.005 → 0.01).
 *
 * This service never touches the database directly — it delegates
 * all persistence to AccountRepository.
 */
public class AccountService {

    private final AccountRepository accountRepo;

    public AccountService(AccountRepository accountRepo) {
        this.accountRepo = accountRepo;
    }

    // ─── CREATE ───────────────────────────────────────────────────────────────

    /**
     * Creates a new account after validating inputs.
     *
     * @param name    display name, e.g. "Chase Checking"
     * @param type    must be CHECKING, SAVINGS, or CREDIT
     * @param initialBalance  must be >= 0
     */
    public Account createAccount(String name, String type, BigDecimal initialBalance) {
        // Input validation — fail fast with clear messages
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Account name cannot be empty.");
        }

        // Normalize type to uppercase so "checking" works too
        String normalizedType = type.toUpperCase().trim();
        if (!normalizedType.equals("CHECKING") &&
            !normalizedType.equals("SAVINGS") &&
            !normalizedType.equals("CREDIT")) {
            throw new IllegalArgumentException(
                "Account type must be CHECKING, SAVINGS, or CREDIT. Got: " + type
            );
        }

        // Initial balance can be 0 but never negative
        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative.");
        }

        // Round to 2 decimal places before storing
        BigDecimal rounded = initialBalance.setScale(2, RoundingMode.HALF_UP);
        Account account = new Account(name, normalizedType, rounded.doubleValue());
        return accountRepo.create(account);
    }

    // ─── DEPOSIT ──────────────────────────────────────────────────────────────

    /**
     * Adds money to an account.
     * Deposit is always safe (can't go negative) but we still validate > 0.
     */
    public Account deposit(long accountId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive.");
        }

        Account account = getAccountOrThrow(accountId);

        BigDecimal newBalance = BigDecimal.valueOf(account.getBalance())
            .add(amount)
            .setScale(2, RoundingMode.HALF_UP);

        account.setBalance(newBalance.doubleValue());
        accountRepo.update(account);
        return account;
    }

    // ─── WITHDRAW ─────────────────────────────────────────────────────────────

    /**
     * Removes money from an account.
     * Throws InsufficientFundsException if balance would go negative.
     *
     * Note: TransactionService.recordTransaction() ALSO checks this during
     * an expense transaction. This method exists for direct withdrawals
     * that aren't categorized transactions (e.g., ATM fee adjustments).
     */
    public Account withdraw(long accountId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive.");
        }

        Account account = getAccountOrThrow(accountId);
        BigDecimal currentBalance = BigDecimal.valueOf(account.getBalance());

        // compareTo returns negative if currentBalance < amount
        if (currentBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(amount, currentBalance);
        }

        BigDecimal newBalance = currentBalance
            .subtract(amount)
            .setScale(2, RoundingMode.HALF_UP);

        account.setBalance(newBalance.doubleValue());
        accountRepo.update(account);
        return account;
    }

    // ─── TRANSFER ─────────────────────────────────────────────────────────────

    /**
     * Moves money from one account to another.
     *
     * This is NOT atomic at the DB level (that would require TransactionService
     * involvement). It's two sequential updates — if the second fails, the
     * first has already been committed. For a production app, you'd wrap
     * this in a DB transaction. Noted here as a known limitation.
     */
    public void transfer(long fromAccountId, long toAccountId, BigDecimal amount) {
        if (fromAccountId == toAccountId) {
            throw new IllegalArgumentException("Cannot transfer to the same account.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive.");
        }

        // withdraw() will throw InsufficientFundsException if needed
        withdraw(fromAccountId, amount);
        deposit(toAccountId, amount);
    }

    // ─── QUERIES ──────────────────────────────────────────────────────────────

    public Account getAccount(long id) {
        return getAccountOrThrow(id);
    }

    public List<Account> getAllAccounts() {
        return accountRepo.findAll();
    }

    public boolean deleteAccount(long id) {
        getAccountOrThrow(id); // verify it exists before deleting
        return accountRepo.delete(id);
    }

    // ─── PRIVATE HELPERS ──────────────────────────────────────────────────────

    /**
     * Fetches an account or throws a clear exception if not found.
     * Avoids duplicating null-check logic in every method.
     */
    private Account getAccountOrThrow(long accountId) {
        Account account = accountRepo.findById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found with id: " + accountId);
        }
        return account;
    }
}