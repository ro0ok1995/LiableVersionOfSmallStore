package com.example.accounting

import com.example.data.db.Expense
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.SaleReturn
import com.example.data.db.SaleReturnLine
import com.example.data.db.Refund
import com.example.data.db.TransactionItemLineEntity
import com.example.model.OperationStatus
import com.example.model.SaleType
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.model.typedOperationStatus
import com.example.model.typedSaleType
import com.example.model.typedTransactionType

/**
 * =============================================================================
 * PURE DOMAIN FINANCIAL REPORTING CALCULATOR
 * =============================================================================
 *
 * Phase 4 Step 2 of SmallStore Accounting Refactor:
 * Provides a pure, deterministic domain calculation layer that produces authoritative
 * financial reporting totals from typed accounting data.
 *
 * CRITICAL ACCOUNTING INVARIANTS:
 * 1. ZERO UI or Android dependencies (pure Kotlin).
 * 2. Independent from localized display strings (no Arabic/English string matching).
 * 3. Mixed sales:
 *    - cash portion (paidAmount) -> cash sales
 *    - credit portion (creditAmount) -> credit sales
 *    - customer receivable increase -> creditAmount ONLY (NOT totalAmount)
 * 4. Cash-only sales:
 *    - cash sales -> totalAmount
 *    - credit sales -> 0.0
 *    - customer receivable increase -> 0.0
 * 5. Credit-only sales:
 *    - cash sales -> 0.0
 *    - credit sales -> totalAmount (or creditAmount)
 *    - customer receivable increase -> totalAmount
 * 6. REVERSALS:
 *    - OperationStatus.REVERSED transactions NEVER contribute to active financial totals.
 *    - Both reversed sales and reversed payments are neutralized (0.0 active effect).
 * 7. Reconciles 1:1 with CustomerLedgerCalculator.
 */

/**
 * Immutable domain model representing decomposed financial values of an individual
 * transaction or business event.
 */
data class TransactionFinancialDecomposition(
    val transactionId: String,
    val transactionType: TransactionType?,
    val saleType: SaleType?,
    val operationStatus: OperationStatus,
    val totalAmount: Double,
    val cashSales: Double = 0.0,
    val creditSales: Double = 0.0,
    val customerPayments: Double = 0.0,
    val saleReturns: Double = 0.0,
    val customerRefunds: Double = 0.0,
    val debitAdjustments: Double = 0.0,
    val creditAdjustments: Double = 0.0,
    val receivableChange: Double = 0.0,
    val isReversed: Boolean = false,
    val fullSettlementAmount: Double = 0.0,
    val partialSettlementAmount: Double = 0.0,
    val expenses: Double = 0.0
)

/**
 * Immutable aggregated reporting totals calculated deterministically from typed
 * accounting operations.
 *
 * Invariant: All active totals exclude transactions where operationStatus == REVERSED.
 */
data class FinancialReportTotals(
    val cashSales: Double = 0.0,
    val creditSales: Double = 0.0,
    val totalSales: Double = 0.0,
    val customerPayments: Double = 0.0,
    val saleReturns: Double = 0.0,
    val customerRefunds: Double = 0.0,
    val debitAdjustments: Double = 0.0,
    val creditAdjustments: Double = 0.0,
    val netReceivableIncrease: Double = 0.0,
    val activeTransactionCount: Int = 0,
    val reversedTransactionCount: Int = 0,
    val cashSaleCount: Int = 0,
    val creditSaleCount: Int = 0,
    val mixedSaleCount: Int = 0,
    val customerPaymentCount: Int = 0,
    val saleReturnCount: Int = 0,
    val reversedSalesVolume: Double = 0.0,
    val reversedPaymentsVolume: Double = 0.0,
    val totalReversedVolume: Double = 0.0,
    val fullSettlementAmount: Double = 0.0,
    val partialSettlementAmount: Double = 0.0,
    val cogs: Double = 0.0,
    val grossProfit: Double = 0.0,
    val grossMargin: Double = 0.0,
    val expenses: Double = 0.0,
    val netProfit: Double = 0.0
) {
    val netSales: Double
        get() = totalSales - saleReturns

    val grossMarginPercent: Double
        get() = grossMargin * 100.0

    val netMargin: Double
        get() = if (netSales > 0.0001) netProfit / netSales else 0.0

    val netMarginPercent: Double
        get() = netMargin * 100.0
}

object FinancialReportCalculator {

    const val EPSILON = 0.0001

    /**
     * Decomposes a single [TransactionItem] into its pure financial accounting effects.
     * Uses strictly typed fields and properties, completely independent of display strings.
     */
    fun decomposeTransaction(tx: TransactionItem): TransactionFinancialDecomposition {
        val status = tx.operationStatus ?: tx.typedOperationStatus
        val isReversed = status == OperationStatus.REVERSED
        val type = tx.transactionType ?: tx.typedTransactionType
        val saleType = tx.saleType ?: tx.typedSaleType

        if (isReversed) {
            return TransactionFinancialDecomposition(
                transactionId = tx.id,
                transactionType = type,
                saleType = saleType,
                operationStatus = OperationStatus.REVERSED,
                totalAmount = tx.amount,
                cashSales = 0.0,
                creditSales = 0.0,
                customerPayments = 0.0,
                saleReturns = 0.0,
                customerRefunds = 0.0,
                receivableChange = 0.0,
                isReversed = true
            )
        }

        var cashSales = 0.0
        var creditSales = 0.0
        var customerPayments = 0.0
        var fullSettlementAmount = 0.0
        var partialSettlementAmount = 0.0
        var saleReturns = 0.0
        var customerRefunds = 0.0
        var debitAdjustments = 0.0
        var creditAdjustments = 0.0
        var receivableChange = 0.0
        var expenses = 0.0

        when (type) {
            TransactionType.SALE -> {
                when (saleType) {
                    SaleType.CASH -> {
                        cashSales = tx.amount
                        creditSales = 0.0
                        receivableChange = 0.0
                    }
                    SaleType.CREDIT -> {
                        cashSales = 0.0
                        creditSales = if (tx.creditAmount > 0.0) tx.creditAmount else tx.amount
                        receivableChange = creditSales
                    }
                    SaleType.MIXED -> {
                        // Accounting rule for mixed sales:
                        // total = 100, paidAmount = 60, creditAmount = 40
                        // cash sales = 60, credit sales = 40, receivable increase = 40
                        val credit = when {
                            tx.creditAmount > 0.0 -> tx.creditAmount
                            tx.paidAmount > 0.0 -> (tx.amount - tx.paidAmount).coerceAtLeast(0.0)
                            else -> if (tx.isCredit) tx.amount else 0.0
                        }
                        val cash = when {
                            tx.paidAmount > 0.0 -> tx.paidAmount
                            tx.creditAmount > 0.0 -> (tx.amount - tx.creditAmount).coerceAtLeast(0.0)
                            else -> if (!tx.isCredit) tx.amount else 0.0
                        }
                        cashSales = cash
                        creditSales = credit
                        receivableChange = credit
                    }
                    null -> {
                        // Fallback when saleType is null: determine using explicit amounts or isCredit flag
                        when {
                            tx.paidAmount > 0.0 && tx.creditAmount > 0.0 -> {
                                cashSales = tx.paidAmount
                                creditSales = tx.creditAmount
                                receivableChange = creditSales
                            }
                            tx.creditAmount > 0.0 -> {
                                creditSales = tx.creditAmount
                                cashSales = (tx.amount - tx.creditAmount).coerceAtLeast(0.0)
                                receivableChange = creditSales
                            }
                            tx.paidAmount > 0.0 && tx.isCredit -> {
                                cashSales = tx.paidAmount
                                creditSales = (tx.amount - tx.paidAmount).coerceAtLeast(0.0)
                                receivableChange = creditSales
                            }
                            tx.isCredit -> {
                                cashSales = 0.0
                                creditSales = tx.amount
                                receivableChange = tx.amount
                            }
                            else -> {
                                cashSales = tx.amount
                                creditSales = 0.0
                                receivableChange = 0.0
                            }
                        }
                    }
                }
            }
            TransactionType.CUSTOMER_PAYMENT -> {
                customerPayments = tx.amount
                receivableChange = -tx.amount
                if (tx.settlementType == com.example.model.SettlementType.PARTIAL) {
                    partialSettlementAmount = tx.amount
                } else {
                    fullSettlementAmount = tx.amount
                }
            }
            TransactionType.SALE_RETURN -> {
                saleReturns = tx.amount
                receivableChange = -tx.amount
            }
            TransactionType.CUSTOMER_REFUND -> {
                customerRefunds = tx.amount
                receivableChange = tx.amount
            }
            TransactionType.OPENING_BALANCE -> {
                if (tx.amount >= 0) {
                    debitAdjustments = tx.amount
                    receivableChange = tx.amount
                } else {
                    creditAdjustments = -tx.amount
                    receivableChange = tx.amount
                }
            }
            TransactionType.BALANCE_ADJUSTMENT -> {
                if (tx.isCredit) {
                    debitAdjustments = tx.amount
                    receivableChange = tx.amount
                } else {
                    creditAdjustments = tx.amount
                    receivableChange = -tx.amount
                }
            }
            TransactionType.REVERSAL -> {
                if (tx.isCredit) {
                    debitAdjustments = tx.amount
                    receivableChange = tx.amount
                } else {
                    creditAdjustments = tx.amount
                    receivableChange = -tx.amount
                }
            }
            TransactionType.EXPENSE -> {
                expenses = tx.amount
            }
            else -> {
                if (tx.isCredit) {
                    receivableChange = tx.amount
                }
            }
        }

        return TransactionFinancialDecomposition(
            transactionId = tx.id,
            transactionType = type,
            saleType = saleType,
            operationStatus = OperationStatus.ACTIVE,
            totalAmount = tx.amount,
            cashSales = cashSales,
            creditSales = creditSales,
            customerPayments = customerPayments,
            saleReturns = saleReturns,
            customerRefunds = customerRefunds,
            debitAdjustments = debitAdjustments,
            creditAdjustments = creditAdjustments,
            receivableChange = receivableChange,
            isReversed = false,
            fullSettlementAmount = fullSettlementAmount,
            partialSettlementAmount = partialSettlementAmount,
            expenses = expenses
        )
    }

    /**
     * Converts a first-class [Sale] entity into its domain financial decomposition.
     */
    fun saleToDecomposition(sale: Sale): TransactionFinancialDecomposition {
        val isReversed = sale.status == "REVERSED"
        val saleType = sale.typedSaleType

        if (isReversed) {
            return TransactionFinancialDecomposition(
                transactionId = sale.id,
                transactionType = TransactionType.SALE,
                saleType = saleType,
                operationStatus = OperationStatus.REVERSED,
                totalAmount = sale.totalAmount,
                cashSales = 0.0,
                creditSales = 0.0,
                customerPayments = 0.0,
                saleReturns = 0.0,
                customerRefunds = 0.0,
                receivableChange = 0.0,
                isReversed = true
            )
        }

        val cashSales: Double
        val creditSales: Double

        when (saleType) {
            SaleType.CASH -> {
                cashSales = sale.totalAmount
                creditSales = 0.0
            }
            SaleType.CREDIT -> {
                cashSales = 0.0
                creditSales = if (sale.creditAmount > 0.0) sale.creditAmount else sale.totalAmount
            }
            SaleType.MIXED -> {
                cashSales = sale.paidAmount
                creditSales = sale.creditAmount
            }
        }

        return TransactionFinancialDecomposition(
            transactionId = sale.id,
            transactionType = TransactionType.SALE,
            saleType = saleType,
            operationStatus = OperationStatus.ACTIVE,
            totalAmount = sale.totalAmount,
            cashSales = cashSales,
            creditSales = creditSales,
            customerPayments = 0.0,
            saleReturns = 0.0,
            customerRefunds = 0.0,
            receivableChange = creditSales,
            isReversed = false
        )
    }

    /**
     * Aggregates individual transaction decompositions into definitive financial report totals.
     * Enforces that REVERSED operations have 0.0 active financial contribution.
     */
    fun calculateTotalsFromDecompositions(decompositions: List<TransactionFinancialDecomposition>): FinancialReportTotals {
        var cashSales = 0.0
        var creditSales = 0.0
        var customerPayments = 0.0
        var fullSettlementAmount = 0.0
        var partialSettlementAmount = 0.0
        var saleReturns = 0.0
        var customerRefunds = 0.0
        var debitAdjustments = 0.0
        var creditAdjustments = 0.0
        var netReceivableIncrease = 0.0
        var expenses = 0.0

        var activeCount = 0
        var reversedCount = 0
        var cashSaleCount = 0
        var creditSaleCount = 0
        var mixedSaleCount = 0
        var customerPaymentCount = 0
        var saleReturnCount = 0

        var reversedSalesVolume = 0.0
        var reversedPaymentsVolume = 0.0
        var totalReversedVolume = 0.0

        for (d in decompositions) {
            if (d.isReversed || d.operationStatus == OperationStatus.REVERSED) {
                reversedCount++
                totalReversedVolume += d.totalAmount
                when (d.transactionType) {
                    TransactionType.SALE -> reversedSalesVolume += d.totalAmount
                    TransactionType.CUSTOMER_PAYMENT -> reversedPaymentsVolume += d.totalAmount
                    else -> {}
                }
                // CRITICAL INVARIANT: Reversed operations NEVER contribute to active financial totals
                continue
            }

            activeCount++
            cashSales += d.cashSales
            creditSales += d.creditSales
            customerPayments += d.customerPayments
            fullSettlementAmount += d.fullSettlementAmount
            partialSettlementAmount += d.partialSettlementAmount
            saleReturns += d.saleReturns
            customerRefunds += d.customerRefunds
            debitAdjustments += d.debitAdjustments
            creditAdjustments += d.creditAdjustments
            netReceivableIncrease += d.receivableChange
            expenses += d.expenses

            when (d.transactionType) {
                TransactionType.SALE -> {
                    when (d.saleType) {
                        SaleType.CASH -> cashSaleCount++
                        SaleType.CREDIT -> creditSaleCount++
                        SaleType.MIXED -> mixedSaleCount++
                        null -> {
                            if (d.creditSales > 0.0 && d.cashSales > 0.0) mixedSaleCount++
                            else if (d.creditSales > 0.0) creditSaleCount++
                            else cashSaleCount++
                        }
                    }
                }
                TransactionType.CUSTOMER_PAYMENT -> customerPaymentCount++
                TransactionType.SALE_RETURN -> saleReturnCount++
                else -> {}
            }
        }

        val totalSales = cashSales + creditSales

        return FinancialReportTotals(
            cashSales = cashSales,
            creditSales = creditSales,
            totalSales = totalSales,
            customerPayments = customerPayments,
            saleReturns = saleReturns,
            customerRefunds = customerRefunds,
            debitAdjustments = debitAdjustments,
            creditAdjustments = creditAdjustments,
            netReceivableIncrease = netReceivableIncrease,
            activeTransactionCount = activeCount,
            reversedTransactionCount = reversedCount,
            cashSaleCount = cashSaleCount,
            creditSaleCount = creditSaleCount,
            mixedSaleCount = mixedSaleCount,
            customerPaymentCount = customerPaymentCount,
            saleReturnCount = saleReturnCount,
            reversedSalesVolume = reversedSalesVolume,
            reversedPaymentsVolume = reversedPaymentsVolume,
            totalReversedVolume = totalReversedVolume,
            fullSettlementAmount = fullSettlementAmount,
            partialSettlementAmount = partialSettlementAmount,
            expenses = expenses
        )
    }

    /**
     * Phase 4: Calculates Cost of Goods Sold (COGS) from persisted [SaleLine]s and [SaleReturnLine]s.
     *
     * Invariants:
     * - Line COGS = quantity * costPriceAtSale (frozen historical cost snapshot).
     * - Sale Return COGS reversed = quantity * costPriceAtReturn (via cogsReversed).
     * - REVERSED sales and REVERSED sale returns are strictly excluded.
     * - Net COGS = activeSalesCogs - activeReturnsCogs; legitimate reversal effects are preserved.
     */
    fun calculateCogs(
        sales: List<Sale>,
        saleLines: List<SaleLine>,
        saleReturns: List<SaleReturn> = emptyList(),
        saleReturnLines: List<SaleReturnLine> = emptyList()
    ): Double {
        val reversedSaleIds = sales.filter { it.status == "REVERSED" }.map { it.id }.toSet()
        val reversedReturnIds = saleReturns.filter { it.status == "REVERSED" }.map { it.id }.toSet()
        return calculateCogs(saleLines, reversedSaleIds, saleReturnLines, reversedReturnIds)
    }

    /**
     * Calculates Cost of Goods Sold (COGS) directly from [SaleLine]s with explicit set of reversed IDs.
     */
    fun calculateCogs(
        saleLines: List<SaleLine>,
        reversedSaleIds: Set<String> = emptySet(),
        saleReturnLines: List<SaleReturnLine> = emptyList(),
        reversedReturnIds: Set<String> = emptySet()
    ): Double {
        val salesCogs = saleLines
            .filter { it.saleId !in reversedSaleIds }
            .sumOf { it.quantity * it.costPriceAtSale }

        val returnsCogs = saleReturnLines
            .filter { it.saleReturnId !in reversedReturnIds }
            .sumOf { it.cogsReversed }

        return salesCogs - returnsCogs
    }

    /**
     * Phase 4: Calculates Cost of Goods Sold (COGS) from legacy [TransactionItemLineEntity] rows.
     */
    fun calculateCogsFromTransactionLines(
        transactions: List<TransactionItem>,
        transactionLines: List<TransactionItemLineEntity>
    ): Double {
        val reversedTxIds = transactions
            .filter { (it.operationStatus ?: it.typedOperationStatus) == OperationStatus.REVERSED }
            .map { it.id }
            .toSet()
        val returnTxIds = transactions
            .filter { (it.transactionType ?: it.typedTransactionType) == TransactionType.SALE_RETURN }
            .map { it.id }
            .toSet()

        val salesCogs = transactionLines
            .filter { it.transactionId !in reversedTxIds && it.transactionId !in returnTxIds }
            .sumOf { it.quantity * it.costPrice }

        val returnsCogs = transactionLines
            .filter { it.transactionId !in reversedTxIds && it.transactionId in returnTxIds }
            .sumOf { it.quantity * it.costPrice }

        return salesCogs - returnsCogs
    }

    /**
     * Phase 4: Calculates Gross Profit.
     * Gross Profit = Net Sales Revenue - COGS
     */
    fun calculateGrossProfit(netSales: Double, cogs: Double): Double {
        return netSales - cogs
    }

    /**
     * Phase 4: Calculates Gross Margin.
     * Gross Margin = Gross Profit / Net Sales Revenue
     * Avoids division by zero if Net Sales Revenue <= 0.
     */
    fun calculateGrossMargin(grossProfit: Double, netSales: Double): Double {
        return if (netSales > EPSILON) grossProfit / netSales else 0.0
    }

    /**
     * Phase 4C: Calculates Operating Expenses from a list of first-class [Expense] entities.
     * Invariant: Excludes REVERSED expenses.
     */
    fun calculateOperatingExpenses(expenses: List<Expense>): Double {
        return expenses.filter { it.status != "REVERSED" }.sumOf { it.amount }
    }

    /**
     * Phase 4C: Calculates Net Profit.
     * Net Profit = Gross Profit - Operating Expenses
     *
     * Invariants:
     * - Loss values remain negative (never clamped to zero).
     * - Cash movements (collections, deposits) are NOT treated as profit.
     * - Customer payments are receivables collections, NOT revenue.
     */
    fun calculateNetProfit(grossProfit: Double, operatingExpenses: Double): Double {
        return grossProfit - operatingExpenses
    }

    /**
     * Primary entry point: Calculates authoritative financial totals directly from a list
     * of domain [TransactionItem]s and optional line entities.
     */
    fun calculate(
        transactions: List<TransactionItem>,
        transactionLines: List<TransactionItemLineEntity> = emptyList()
    ): FinancialReportTotals {
        val decompositions = transactions.map { decomposeTransaction(it) }
        val baseTotals = calculateTotalsFromDecompositions(decompositions)
        if (transactionLines.isEmpty()) {
            val netProfit = calculateNetProfit(baseTotals.netSales, baseTotals.expenses)
            return baseTotals.copy(
                grossProfit = baseTotals.netSales,
                grossMargin = calculateGrossMargin(baseTotals.netSales, baseTotals.netSales),
                netProfit = netProfit
            )
        }

        val cogs = calculateCogsFromTransactionLines(transactions, transactionLines)
        val grossProfit = calculateGrossProfit(baseTotals.netSales, cogs)
        val grossMargin = calculateGrossMargin(grossProfit, baseTotals.netSales)
        val netProfit = calculateNetProfit(grossProfit, baseTotals.expenses)

        return baseTotals.copy(
            cogs = cogs,
            grossProfit = grossProfit,
            grossMargin = grossMargin,
            netProfit = netProfit
        )
    }

    /**
     * Calculates authoritative financial totals directly from a list of first-class [Sale] entities,
     * with optional line items, return lines, and expenses for authoritative COGS, Gross Profit, and Net Profit calculation.
     */
    fun calculateFromSales(
        sales: List<Sale>,
        saleLines: List<SaleLine> = emptyList(),
        saleReturns: List<SaleReturn> = emptyList(),
        saleReturnLines: List<SaleReturnLine> = emptyList(),
        expenses: List<Expense> = emptyList(),
        refunds: List<Refund> = emptyList()
    ): FinancialReportTotals {
        val decompositions = sales.map { saleToDecomposition(it) }
        val baseTotals = calculateTotalsFromDecompositions(decompositions)

        val activeReturns = saleReturns.filter { it.status != "REVERSED" }
        val returnAmount = activeReturns.sumOf { it.amount }
        val effectiveReturns = if (returnAmount > 0.0) returnAmount else baseTotals.saleReturns
        val netSales = baseTotals.totalSales - effectiveReturns
        val totalExpenses = calculateOperatingExpenses(expenses)
        val activeRefunds = refunds
            .filter { it.status != "REVERSED" }
            .sumOf { it.amount }

        if (saleLines.isEmpty()) {
            val grossProfit = netSales
            val grossMargin = calculateGrossMargin(grossProfit, netSales)
            val netProfit = calculateNetProfit(grossProfit, totalExpenses)
            return baseTotals.copy(
                saleReturns = effectiveReturns,
                customerRefunds = activeRefunds,
                grossProfit = grossProfit,
                grossMargin = grossMargin,
                expenses = totalExpenses,
                netProfit = netProfit
            )
        }

        val cogs = calculateCogs(sales, saleLines, saleReturns, saleReturnLines)
        val grossProfit = calculateGrossProfit(netSales, cogs)
        val grossMargin = calculateGrossMargin(grossProfit, netSales)
        val netProfit = calculateNetProfit(grossProfit, totalExpenses)

        return baseTotals.copy(
            saleReturns = effectiveReturns,
            customerRefunds = activeRefunds,
            cogs = cogs,
            grossProfit = grossProfit,
            grossMargin = grossMargin,
            expenses = totalExpenses,
            netProfit = netProfit
        )
    }

    /**
     * Convenience method to calculate financial report totals for a specific customer.
     */
    fun calculateCustomerTotals(
        customerId: String,
        transactions: List<TransactionItem>,
        transactionLines: List<TransactionItemLineEntity> = emptyList()
    ): FinancialReportTotals {
        val customerTransactions = transactions.filter { it.customerId == customerId }
        val custTxIds = customerTransactions.map { it.id }.toSet()
        val customerLines = transactionLines.filter { it.transactionId in custTxIds }
        return calculate(customerTransactions, customerLines)
    }
}
