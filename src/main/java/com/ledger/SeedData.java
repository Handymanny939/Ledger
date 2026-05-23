package com.ledger;

import com.ledger.model.Category;
import com.ledger.model.Transaction;
import com.ledger.repository.AccountRepository;
import com.ledger.repository.TransactionRepository;
import com.ledger.service.AccountService;
import com.ledger.service.BudgetService;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Populates the database with realistic sample data so reports
 * are immediately interesting on first run.
 *
 * Uses the repository directly for transactions that intentionally
 * exceed budget limits (transport) — bypasses the service-layer guard
 * so we can demo the "over budget" indicator in reports.
 *
 * Call this ONCE on a fresh database. Running it twice will create
 * duplicate accounts — guard against this with the isEmpty() check in App.
 */
public class SeedData {

    public static void seed(AccountService accountService,
                            BudgetService budgetService,
                            AccountRepository accountRepo,
                            TransactionRepository txRepo) {

        System.out.println("  [i] Seeding sample data...");

        LocalDate today = LocalDate.now();
        String month = today.format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));

        // ── 3 Accounts ────────────────────────────────────────────────────────
        var checking = accountService.createAccount(
            "Chase Checking", "CHECKING", new BigDecimal("4500.00"));
        var savings = accountService.createAccount(
            "Ally Savings", "SAVINGS", new BigDecimal("15000.00"));
        var credit = accountService.createAccount(
            "Citi Rewards Card", "CREDIT", new BigDecimal("0.00"));

        // ── 2 Budgets ─────────────────────────────────────────────────────────
        budgetService.setBudget(Category.FOOD,      new BigDecimal("600.00"), month);
        budgetService.setBudget(Category.TRANSPORT, new BigDecimal("200.00"), month);
        budgetService.setBudget(Category.ENTERTAINMENT, new BigDecimal("150.00"), month);
        budgetService.setBudget(Category.UTILITIES, new BigDecimal("200.00"), month);

        // ── 10 Transactions ───────────────────────────────────────────────────

        // Income
        txRepo.create(new Transaction(checking.getId(),  3800.00, Category.SALARY,
            "Monthly salary",           today.minusDays(20)));
        txRepo.create(new Transaction(checking.getId(),   500.00, Category.SALARY,
            "Freelance project",        today.minusDays(10)));

        // Food — within budget
        txRepo.create(new Transaction(checking.getId(),  -95.40, Category.FOOD,
            "Publix weekly shop",       today.minusDays(18)));
        txRepo.create(new Transaction(checking.getId(),  -62.75, Category.FOOD,
            "Costco run",               today.minusDays(12)));
        txRepo.create(new Transaction(checking.getId(),  -38.50, Category.FOOD,
            "Chipotle x3",              today.minusDays(6)));

        // Transport — intentionally over $200 budget (demo purposes)
        txRepo.create(new Transaction(checking.getId(), -110.00, Category.TRANSPORT,
            "Gas fill-ups",             today.minusDays(15)));
        txRepo.create(new Transaction(checking.getId(),  -75.00, Category.TRANSPORT,
            "Uber rides this week",     today.minusDays(5)));
        txRepo.create(new Transaction(checking.getId(),  -60.00, Category.TRANSPORT,
            "Parking monthly pass",     today.minusDays(2)));

        // Entertainment — within budget
        txRepo.create(new Transaction(credit.getId(),   -45.99, Category.ENTERTAINMENT,
            "Netflix + Hulu + Spotify", today.minusDays(14)));

        // Utilities — within budget
        txRepo.create(new Transaction(checking.getId(), -132.00, Category.UTILITIES,
            "Electric + internet bill", today.minusDays(8)));

        // Manually adjust checking balance for repo-inserted transactions
        // (repository bypasses the automatic balance update in TransactionService)
        double expenses = 95.40 + 62.75 + 38.50 + 110.00 + 75.00 + 60.00 + 132.00;
        double income   = 3800.00 + 500.00;
        var acct = accountRepo.findById(checking.getId());
        acct.setBalance(
            BigDecimal.valueOf(acct.getBalance())
                      .add(BigDecimal.valueOf(income))
                      .subtract(BigDecimal.valueOf(expenses))
                      .doubleValue()
        );
        accountRepo.update(acct);

        // Adjust credit card balance for entertainment charge
        var creditAcct = accountRepo.findById(credit.getId());
        creditAcct.setBalance(
            BigDecimal.valueOf(creditAcct.getBalance())
                      .add(BigDecimal.valueOf(45.99))
                      .doubleValue()
        );
        accountRepo.update(creditAcct);

        System.out.println("  [OK] Sample data loaded. 3 accounts, 4 budgets, 10 transactions.");
    }
}