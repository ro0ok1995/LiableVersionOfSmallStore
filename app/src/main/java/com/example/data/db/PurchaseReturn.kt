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
 * Authoritative [PurchaseReturn] Entity.
 */
@Entity(
    tableName = "purchase_returns",
    foreignKeys = [
        ForeignKey(
            entity = Purchase::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Supplier::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("purchaseId"),
        Index("supplierId")
    ]
)
data class PurchaseReturn(
    @PrimaryKey val id: String,
    val purchaseId: String,
    val supplierId: String,
    val returnDate: String,
    val amount: Double,
    val reason: String,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "PurchaseReturn id must not be blank" }
        require(purchaseId.isNotBlank()) { "purchaseId must not be blank" }
        require(supplierId.isNotBlank()) { "supplierId must not be blank" }
        require(amount > 0.0) { "amount ($amount) must be greater than zero" }
        require(returnDate.isNotBlank()) { "returnDate must not be blank" }
        require(reason.isNotBlank()) { "reason must not be blank" }
    }

    val typedOperationStatus: OperationStatus
        get() = if (status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
}

fun PurchaseReturn.toTransactionItem(supplierName: String = "", invoiceNumber: String = ""): TransactionItem {
    return TransactionItem(
        id = id,
        title = "مرتجع مشتريات $invoiceNumber",
        activityType = "مرتجع مشتريات",
        amount = amount,
        isCredit = false,
        date = returnDate,
        relativeTime = "الآن",
        customerName = supplierName,
        notes = reason,
        customerId = supplierId,
        isArchived = false,
        archivedDate = null,
        transactionType = TransactionType.PURCHASE_RETURN,
        saleType = null,
        paymentStatus = null,
        operationStatus = typedOperationStatus,
        paidAmount = 0.0,
        creditAmount = 0.0
    )
}
