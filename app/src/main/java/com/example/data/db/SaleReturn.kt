package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 8: Sale Return Entity.
 *
 * Represents an authoritative return of merchandise previously sold in a [Sale].
 *
 * Accounting Invariants:
 * 1. Linked to original Sale via saleId (ON DELETE RESTRICT).
 * 2. Original Sale and SaleLine amounts are NEVER overwritten or reduced.
 * 3. An independent accounting event that reduces net sales and corrects customer receivable/cash.
 */
@Entity(
    tableName = "sale_returns",
    foreignKeys = [
        ForeignKey(
            entity = Sale::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("saleId"),
        Index("customerId"),
        Index("returnDate")
    ]
)
data class SaleReturn(
    @PrimaryKey val id: String,
    val saleId: String,
    val customerId: String? = null,
    val returnDate: String,
    val reason: String,
    val amount: Double,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Sale return ID cannot be blank" }
        require(saleId.isNotBlank()) { "Original sale ID cannot be blank" }
        require(returnDate.isNotBlank()) { "Return date cannot be blank" }
        require(reason.isNotBlank()) { "Return reason cannot be blank" }
        require(amount > 0.0) { "Return amount ($amount) must be greater than zero" }
    }
}

fun SaleReturn.toTransactionItem(customerNameSnapshot: String = "", invoiceNumber: String = ""): com.example.model.TransactionItem {
    return com.example.model.TransactionItem(
        id = id,
        activityType = "مرتجع مبيعات",
        title = if (invoiceNumber.isNotBlank()) "مرتجع فاتورة $invoiceNumber" else "مرتجع مبيعات",
        amount = amount,
        isCredit = false,
        date = returnDate,
        relativeTime = "الآن",
        notes = reason,
        settlementType = null,
        customerId = customerId,
        customerName = customerNameSnapshot,
        customerNameSnapshot = customerNameSnapshot,
        isArchived = false,
        archivedDate = null,
        transactionType = com.example.model.TransactionType.SALE_RETURN,
        saleType = null,
        paymentStatus = null,
        operationStatus = com.example.model.OperationStatus.ACTIVE,
        paidAmount = 0.0,
        creditAmount = 0.0
    )
}

