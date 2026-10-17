package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.OperationStatus
import com.example.model.TransactionItem
import com.example.model.TransactionType

/**
 * Phase 9 Suppliers / Purchases:
 * Authoritative [SupplierPayment] Entity.
 *
 * Invariants:
 * 1. Linked to Supplier via supplierId FK with RESTRICT.
 * 2. Independent historical financial movement (never mutates Purchase amounts).
 * 3. Reduces supplier payable and reduces the designated financial account.
 */
@Entity(
    tableName = "supplier_payments",
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
        Index("paymentDate"),
        Index("status")
    ]
)
data class SupplierPayment(
    @PrimaryKey val id: String,
    val supplierId: String,
    val amount: Double,
    val paymentDate: String,
    val paymentMethodId: String? = null,
    val financialAccountId: String? = null,
    val referenceNumber: String? = null,
    val notes: String? = null,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "SupplierPayment id must not be blank" }
        require(supplierId.isNotBlank()) { "supplierId must not be blank" }
        require(amount > 0.0) { "amount ($amount) must be greater than zero" }
        require(paymentDate.isNotBlank()) { "paymentDate must not be blank" }
    }

    val typedOperationStatus: OperationStatus
        get() = if (status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
}

fun SupplierPayment.toTransactionItem(supplierName: String = ""): TransactionItem {
    return TransactionItem(
        id = id,
        title = "سداد مورد: $supplierName",
        activityType = "سداد مورد",
        amount = amount,
        isCredit = false,
        date = paymentDate,
        relativeTime = "الآن",
        customerName = supplierName,
        notes = notes ?: "سداد للمورد",
        customerId = supplierId,
        isArchived = false,
        archivedDate = null,
        transactionType = TransactionType.SUPPLIER_PAYMENT,
        saleType = null,
        paymentStatus = null,
        operationStatus = typedOperationStatus,
        paidAmount = amount,
        creditAmount = 0.0
    )
}
