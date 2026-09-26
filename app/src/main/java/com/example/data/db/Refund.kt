package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 8: Refund Entity.
 *
 * Represents an independent financial payout to a customer (e.g. cash or bank refund),
 * separate from the sale or return.
 *
 * Accounting Invariants:
 * 1. Independent financial movement that credits the financial account (Cash/Bank)
 *    and debits customer ledger (if linked to a customer account).
 * 2. May use a different payment method / account than original sale payment.
 * 3. Never mutates the original sale or customer payment records.
 */
@Entity(
    tableName = "refunds",
    indices = [
        Index("saleReturnId"),
        Index("saleId"),
        Index("customerId"),
        Index("financialAccountId"),
        Index("refundDate")
    ]
)
data class Refund(
    @PrimaryKey val id: String,
    val saleReturnId: String? = null,
    val saleId: String? = null,
    val customerId: String? = null,
    val amount: Double,
    val paymentMethodId: String? = null,
    val financialAccountId: String? = null,
    val refundDate: String,
    val reason: String,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Refund ID cannot be blank" }
        require(amount > 0.0) { "Refund amount ($amount) must be greater than zero" }
        require(refundDate.isNotBlank()) { "Refund date cannot be blank" }
        require(reason.isNotBlank()) { "Refund reason cannot be blank" }
    }
}

fun Refund.toTransactionItem(customerNameSnapshot: String = "", invoiceNumber: String = ""): com.example.model.TransactionItem {
    return com.example.model.TransactionItem(
        id = id,
        activityType = "استرداد نقدي",
        title = if (invoiceNumber.isNotBlank()) "استرداد نقدي لمرتجع فاتورة $invoiceNumber" else "استرداد نقدي",
        amount = amount,
        isCredit = false,
        date = refundDate,
        relativeTime = "الآن",
        notes = reason,
        settlementType = null,
        customerId = customerId,
        customerName = customerNameSnapshot,
        customerNameSnapshot = customerNameSnapshot,
        isArchived = false,
        archivedDate = null,
        transactionType = com.example.model.TransactionType.CUSTOMER_REFUND,
        saleType = null,
        paymentStatus = null,
        operationStatus = com.example.model.OperationStatus.ACTIVE,
        paidAmount = 0.0,
        creditAmount = 0.0
    )
}

