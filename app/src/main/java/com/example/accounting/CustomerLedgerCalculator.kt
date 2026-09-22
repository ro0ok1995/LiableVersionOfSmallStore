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
 * Pure domain calculation engine for Customer Receivable Ledgers.
 *
 * Strict Invariants:
 * 1. ZERO UI or Android dependencies (pure Kotlin).
 * 2. Balance sign convention:
 *    - balance > 0: Customer owes store (Accounts Receivable asset).
 *    - balance == 0: Customer settled.
 *    - balance < 0: Store owes customer (Customer overpayment / advance credit).
 * 3. CRITICAL ACCOUNTING RULE: ARCHIVED IS NOT REVERSED!
 *    - `isArchived == true` transactions continue to contribute to the financial balance.
 *    - Only `operationStatus == REVERSED` neutralizes an operation's financial contribution.
 * 4. Anonymous cash sales (`customerId == null`) are strictly excluded from customer balances.
 */
object CustomerLedgerCalculator {

    const val EPSILON = 0.0001

    /**
     * Converts a collection of domain [TransactionItem]s for a specific [customerId] into
     * standard ledger entries.
     */
    fun toLedgerEntries(customerId: String, transactions: List<TransactionItem>): List<CustomerLedgerEntry> {
        return transactions
            .filter { it.customerId == customerId }
            .map { tx ->
                val type = tx.typedTransactionType
                val status = tx.typedOperationStatus
                var debit = 0.0
                var credit = 0.0

                when (type) {
                    TransactionType.SALE -> {
                        val saleType = tx.typedSaleType
                        when (saleType) {
                            SaleType.CREDIT -> {
                                debit = if (tx.creditAmount > 0.0) tx.creditAmount else tx.amount
                            }
                            SaleType.MIXED -> {
                                // For mixed sale: totalAmount = paidAmount + creditAmount
                                // Mandatory rule: The customer's receivable MUST be creditAmount (or totalAmount - paidAmount), not totalAmount.
                                val receivable = when {
                                    tx.creditAmount > 0.0 -> tx.creditAmount
                                    tx.paidAmount > 0.0 -> (tx.amount - tx.paidAmount).coerceAtLeast(0.0)
                                    else -> tx.amount
                                }
                                debit = receivable
                            }
                            SaleType.CASH -> {
                                // Pure cash sale with a known customer:
                                // Zero debt/receivable impact on the customer balance!
                                debit = 0.0
                                credit = 0.0
                            }
                            null -> {
                                if (tx.creditAmount > 0.0) {
                                    debit = tx.creditAmount
                                } else if (tx.paidAmount > 0.0 && tx.isCredit) {
                                    debit = (tx.amount - tx.paidAmount).coerceAtLeast(0.0)
                                } else if (tx.isCredit) {
                                    debit = tx.amount
                                }
                            }
                        }
                    }
                    TransactionType.CUSTOMER_PAYMENT -> {
                        // Customer payment reduces receivable -> Credit
                        credit = tx.amount
                    }
                    TransactionType.SALE_RETURN -> {
                        // Return of merchandise against account reduces receivable -> Credit
                        credit = tx.amount
                    }
                    TransactionType.CUSTOMER_REFUND -> {
                        // Cash refunded back to customer increases account receivable -> Debit
                        debit = tx.amount
                    }
                    TransactionType.OPENING_BALANCE -> {
                        if (tx.amount >= 0) {
                            debit = tx.amount
                        } else {
                            credit = -tx.amount
                        }
                    }
                    TransactionType.BALANCE_ADJUSTMENT -> {
                        if (tx.isCredit) {
                            debit = tx.amount
                        } else {
                            credit = tx.amount
                        }
                    }
                    TransactionType.REVERSAL -> {
                        // Explicit reversal operations
                        if (tx.isCredit) {
                            debit = tx.amount
                        } else {
                            credit = tx.amount
                        }
                    }
                    else -> {
                        // Non-customer transactions (e.g. PURCHASE, EXPENSE)
                        debit = 0.0
                        credit = 0.0
                    }
                }

                val effectiveType = type ?: if (tx.isCredit) TransactionType.SALE else TransactionType.CUSTOMER_PAYMENT

                CustomerLedgerEntry(
                    transactionId = tx.id,
                    customerId = customerId,
                    date = tx.date,
                    transactionType = effectiveType,
                    debit = debit,
                    credit = credit,
                    operationStatus = status,
                    isArchived = tx.isArchived,
                    description = tx.title.ifBlank { tx.notes }
                )
            }
    }

    /**
     * Calculates the deterministic [CustomerBalanceSummary] from ledger entries.
     */
    fun calculateSummaryFromEntries(customerId: String, entries: List<CustomerLedgerEntry>): CustomerBalanceSummary {
        var openingBalance = 0.0
        var totalCreditSales = 0.0
        var totalPayments = 0.0
        var totalReturns = 0.0
        var debitAdjustments = 0.0
        var creditAdjustments = 0.0
        var reversedCount = 0
        var activeCount = 0

        for (entry in entries) {
            if (entry.operationStatus == OperationStatus.REVERSED) {
                reversedCount++
                // Reversed operations do NOT contribute their original accounting effect
                continue
            }

            activeCount++

            when (entry.transactionType) {
                TransactionType.OPENING_BALANCE -> {
                    openingBalance += (entry.debit - entry.credit)
                }
                TransactionType.SALE -> {
                    totalCreditSales += entry.debit
                }
                TransactionType.CUSTOMER_PAYMENT -> {
                    totalPayments += entry.credit
                }
                TransactionType.SALE_RETURN -> {
                    totalReturns += entry.credit
                }
                TransactionType.CUSTOMER_REFUND -> {
                    debitAdjustments += entry.debit
                }
                TransactionType.BALANCE_ADJUSTMENT -> {
                    debitAdjustments += entry.debit
                    creditAdjustments += entry.credit
                }
                else -> {
                    if (entry.debit > 0) debitAdjustments += entry.debit
                    if (entry.credit > 0) creditAdjustments += entry.credit
                }
            }
        }

        // Authoritative accounting formula:
        // Balance = Opening + Credit Sales + Debit Adjustments - Payments - Returns - Credit Adjustments
        val balance = openingBalance + totalCreditSales + debitAdjustments - (totalPayments + totalReturns + creditAdjustments)

        return CustomerBalanceSummary(
            customerId = customerId,
            openingBalance = openingBalance,
            totalCreditSales = totalCreditSales,
            totalPayments = totalPayments,
            totalReturns = totalReturns,
            debitAdjustments = debitAdjustments,
            creditAdjustments = creditAdjustments,
            totalReversedCount = reversedCount,
            activeTransactionCount = activeCount,
            balance = balance
        )
    }

    /**
     * Convenience method to calculate balance summary directly from [TransactionItem]s.
     */
    fun calculateCustomerBalance(customerId: String, transactions: List<TransactionItem>): CustomerBalanceSummary {
        val entries = toLedgerEntries(customerId, transactions)
        return calculateSummaryFromEntries(customerId, entries)
    }

    /**
     * Converts a first-class [Sale] entity directly into a [CustomerLedgerEntry].
     * Enforces the three sale mode rules:
     * - CASH (100): sales=100, receivable/debit=0.
     * - CREDIT (100): sales=100, receivable/debit=100.
     * - MIXED (100 / paid 60): sales=100, receivable/debit=40.
     */
    fun saleToLedgerEntry(customerId: String, sale: Sale): CustomerLedgerEntry? {
        if (sale.customerId != customerId) return null
        val status = if (sale.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE

        val debit = when (sale.saleType) {
            "CASH" -> 0.0
            "CREDIT" -> if (sale.creditAmount > 0.0) sale.creditAmount else sale.totalAmount
            "MIXED" -> if (sale.creditAmount > 0.0) sale.creditAmount else (sale.totalAmount - sale.paidAmount).coerceAtLeast(0.0)
            else -> if (sale.creditAmount > 0.0) sale.creditAmount else (sale.totalAmount - sale.paidAmount).coerceAtLeast(0.0)
        }

        return CustomerLedgerEntry(
            transactionId = sale.id,
            customerId = customerId,
            date = sale.transactionDate,
            transactionType = TransactionType.SALE,
            debit = debit,
            credit = 0.0,
            operationStatus = status,
            isArchived = false,
            description = "فاتورة ${sale.invoiceNumber}"
        )
    }

    /**
     * Calculates customer balance summary directly from a list of [Sale] entities.
     */
    fun calculateCustomerBalanceFromSales(customerId: String, sales: List<Sale>): CustomerBalanceSummary {
        val entries = sales.mapNotNull { saleToLedgerEntry(customerId, it) }
        return calculateSummaryFromEntries(customerId, entries)
    }
}
