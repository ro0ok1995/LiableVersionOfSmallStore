package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.OperationStatus
import com.example.model.PaymentStatus
import com.example.model.TransactionItem
import com.example.model.TransactionType

/**
 * Phase 9 Suppliers / Purchases:
 * Authoritative [Purchase] Entity.
 *
 * Invariants:
 * 1. totalAmount = paidAmount + creditAmount, ALWAYS, at data-model level.
 * 2. supplierId FK references suppliers(id) with RESTRICT.
 * 3. Never treat a purchase as a sale.
 * 4. Immutable financial transaction: physical edits/deletions forbidden.
 */
@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = Supplier::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("supplierId"),
        Index("purchaseDate"),
        Index("status")
    ]
)
data class Purchase(
    @PrimaryKey val id: String,
    val invoiceNumber: String,
    val supplierId: String,
    val purchaseDate: String,
    val totalAmount: Double,
    val paidAmount: Double,
    val creditAmount: Double,
    val paymentStatus: String,
    val paymentMethodId: String? = null,
    val financialAccountId: String? = null,
    val notes: String? = null,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(supplierId.isNotBlank()) { "supplierId must not be blank" }
        require(totalAmount > 0.0) { "totalAmount ($totalAmount) must be greater than zero" }
        require(paidAmount >= 0.0) { "paidAmount must be non-negative" }
        require(creditAmount >= 0.0) { "creditAmount must be non-negative" }
        require(Math.abs(totalAmount - (paidAmount + creditAmount)) < 0.001) {
            "Accounting invariant violated: totalAmount ($totalAmount) must equal paidAmount ($paidAmount) + creditAmount ($creditAmount)"
        }
        require(purchaseDate.isNotBlank()) { "purchaseDate must not be blank" }
    }

    val typedPaymentStatus: PaymentStatus
        get() = try {
            PaymentStatus.valueOf(paymentStatus)
        } catch (_: Exception) {
            when {
                paidAmount >= totalAmount -> PaymentStatus.PAID
                paidAmount <= 0.0 -> PaymentStatus.UNPAID
                else -> PaymentStatus.PARTIAL
            }
        }

    val typedOperationStatus: OperationStatus
        get() = if (status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
}

fun Purchase.toTransactionItem(supplierName: String = ""): TransactionItem {
    return TransactionItem(
        id = id,
        title = "فاتورة مشتريات $invoiceNumber",
        activityType = "فاتورة مشتريات",
        amount = totalAmount,
        isCredit = creditAmount > 0.0,
        date = purchaseDate,
        relativeTime = "الآن",
        customerName = supplierName,
        notes = notes ?: "مشتريات من مورد",
        customerId = supplierId,
        isArchived = false,
        archivedDate = null,
        transactionType = TransactionType.PURCHASE,
        saleType = null,
        paymentStatus = typedPaymentStatus,
        operationStatus = typedOperationStatus,
        paidAmount = paidAmount,
        creditAmount = creditAmount
    )
}
