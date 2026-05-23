package com.ledger.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO carrying all data needed to render the monthly summary report.
 * Immutable — built once by ReportService, read by ReportPrinter.
 *
 * We use a record here — Java 16+ feature that auto-generates:
 *   - constructor, getters, equals(), hashCode(), toString()
 * Perfect for DTOs since they're pure data carriers.
 */
public record MonthlySummary(
    String month,                        // "2026-05"
    BigDecimal totalIncome,              // sum of all positive transactions
    BigDecimal totalExpenses,            // sum of all negative transactions (positive value)
    BigDecimal netCashFlow,              // totalIncome - totalExpenses
    List<CategorySpend> categoryBreakdown // per-category spend, sorted by amount desc
) {}