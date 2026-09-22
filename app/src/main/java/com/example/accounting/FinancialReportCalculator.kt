package com.example.accounting

import com.example.data.db.Sale
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
    val partialSettlementAmount: Double = 0.0
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
    val partialSettlementAmount: Double = 0.0
)

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
            partialSettlementAmount = partialSettlementAmount
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
            partialSettlementAmount = partialSettlementAmount
        )
    }

    /**
     * Primary entry point: Calculates authoritative financial totals directly from a list
     * of domain [TransactionItem]s.
     */
    fun calculate(transactions: List<TransactionItem>): FinancialReportTotals {
        val decompositions = transactions.map { decomposeTransaction(it) }
        return calculateTotalsFromDecompositions(decompositions)
    }

    /**
     * Calculates authoritative financial totals directly from a list of first-class [Sale] entities.
     */
    fun calculateFromSales(sales: List<Sale>): FinancialReportTotals {
        val decompositions = sales.map { saleToDecomposition(it) }
        return calculateTotalsFromDecompositions(decompositions)
    }

    /**
     * Convenience method to calculate financial report totals for a specific customer.
     */
    fun calculateCustomerTotals(customerId: String, transactions: List<TransactionItem>): FinancialReportTotals {
        val customerTransactions = transactions.filter { it.customerId == customerId }
        return calculate(customerTransactions)
    }
}
