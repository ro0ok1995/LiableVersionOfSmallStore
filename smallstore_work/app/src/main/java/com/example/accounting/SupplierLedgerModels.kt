package com.example.accounting

import com.example.model.OperationStatus
import com.example.model.TransactionType

enum class SupplierLedgerEntryType {
    OPENING_BALANCE,
    PURCHASE,
    SUPPLIER_PAYMENT,
    PURCHASE_RETURN,
    ADJUSTMENT
}

data class SupplierLedgerEntry(
    val id: String,
    val supplierId: String,
    val date: String,
    val entryType: SupplierLedgerEntryType,
    val debit: Double, // decreases payable (payment, return)
    val credit: Double, // increases payable (credit purchase)
    val runningBalance: Double, // running payable balance
    val referenceId: String,
    val description: String,
    val operationStatus: OperationStatus = OperationStatus.ACTIVE
)

data class SupplierBalanceSummary(
    val supplierId: String,
    val openingBalance: Double = 0.0,
    val totalPurchases: Double = 0.0,
    val totalCreditPurchases: Double = 0.0,
    val totalPayments: Double = 0.0,
    val totalReturns: Double = 0.0,
    val debitAdjustments: Double = 0.0,
    val creditAdjustments: Double = 0.0,
    val balance: Double = 0.0, // net payable owed to supplier (liability)
    val activeTransactionCount: Int = 0
) {
    /** Store owes money to supplier (Accounts Payable liability) */
    val isPayable: Boolean get() = balance > 0.0001

    /** Store overpaid supplier (advance payment to supplier) */
    val isOverpaid: Boolean get() = balance < -0.0001

    /** Fully settled with supplier */
    val isSettled: Boolean get() = !isPayable && !isOverpaid
}
