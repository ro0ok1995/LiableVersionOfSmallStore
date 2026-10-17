package com.example.accounting

import com.example.model.OperationStatus
import com.example.model.TransactionType

/**
 * Normalized domain ledger item representing a financial debit/credit entry
 * against a customer's account receivable balance.
 */
data class CustomerLedgerEntry(
    val transactionId: String,
    val customerId: String,
    val date: String,
    val transactionType: TransactionType,
    val debit: Double = 0.0,
    val credit: Double = 0.0,
    val operationStatus: OperationStatus = OperationStatus.ACTIVE,
    val isArchived: Boolean = false,
    val description: String = ""
) {
    /**
     * Net change in receivable from the merchant's perspective:
     * debit (+) increases what the customer owes (receivable asset),
     * credit (-) decreases what the customer owes.
     */
    val netReceivableEffect: Double
        get() = when (operationStatus) {
            OperationStatus.ACTIVE -> debit - credit
            OperationStatus.REVERSED -> 0.0
        }
}

/**
 * Immutable aggregated summary of a customer's account receivable status,
 * deterministically calculated from persistent ledger entries.
 */
data class CustomerBalanceSummary(
    val customerId: String,
    val openingBalance: Double = 0.0,
    val totalCreditSales: Double = 0.0,
    val totalPayments: Double = 0.0,
    val totalReturns: Double = 0.0,
    val debitAdjustments: Double = 0.0,
    val creditAdjustments: Double = 0.0,
    val totalReversedCount: Int = 0,
    val activeTransactionCount: Int = 0,
    val balance: Double = 0.0
) {
    /** Customer owes money to store */
    val isDebitBalance: Boolean get() = balance > 0.0001

    /** Store owes money / credit to customer (overpayment) */
    val isCreditBalance: Boolean get() = balance < -0.0001

    /** Zero balance / fully settled */
    val isSettled: Boolean get() = !isDebitBalance && !isCreditBalance
}
