package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.LegacyAccountingBridge
import com.example.model.OperationStatus
import com.example.model.PaymentStatus
import com.example.model.SaleType
import com.example.model.TransactionItem
import com.example.model.TransactionType

/**
 * Phase 3 Sales Engine:
 * Authoritative [Sale] Entity.
 *
 * Invariants:
 * 1. totalAmount = paidAmount + creditAmount, ALWAYS, at data-model level.
 * 2. Sequential human-readable invoiceNumber (e.g. INV-000001) separate from UUID/timestamp PK.
 * 3. customerId FK references customers(id) with RESTRICT, nullable for anonymous cash sales.
 * 4. Indices on (customerId) and (customerId, transactionDate) for ledger queries.
 * 5. Immutable financial transaction: physical edits/deletions forbidden.
 */
@Entity(
    tableName = "sales",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("customerId"),
        Index(value = ["customerId", "transactionDate"]),
        Index(value = ["invoiceNumber"], unique = true)
    ]
)
data class Sale(
    @PrimaryKey val id: String,
    val invoiceNumber: String,
    val customerId: String?,
    val saleType: String,
    val totalAmount: Double,
    val paidAmount: Double,
    val creditAmount: Double,
    val paymentStatus: String,
    val transactionDate: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE"
) {
    init {
        require(Math.abs(totalAmount - (paidAmount + creditAmount)) < 0.001) {
            "Accounting invariant violated: totalAmount ($totalAmount) must equal paidAmount ($paidAmount) + creditAmount ($creditAmount)"
        }
        require(paidAmount >= 0.0) { "paidAmount must be non-negative" }
        require(creditAmount >= 0.0) { "creditAmount must be non-negative" }
        require(totalAmount >= 0.0) { "totalAmount must be non-negative" }
        require(invoiceNumber.isNotBlank()) { "invoiceNumber must not be blank" }
    }

    val typedSaleType: SaleType
        get() = when (saleType) {
            "CASH" -> SaleType.CASH
            "CREDIT" -> SaleType.CREDIT
            "MIXED" -> SaleType.MIXED
            else -> runCatching { SaleType.valueOf(saleType) }.getOrDefault(SaleType.CASH)
        }

    val typedPaymentStatus: PaymentStatus
        get() = when (paymentStatus) {
            "PAID" -> PaymentStatus.PAID
            "UNPAID" -> PaymentStatus.UNPAID
            "PARTIAL" -> PaymentStatus.PARTIAL
            else -> runCatching { PaymentStatus.valueOf(paymentStatus) }.getOrDefault(PaymentStatus.PAID)
        }

    val typedStatus: OperationStatus
        get() = when (status) {
            "REVERSED" -> OperationStatus.REVERSED
            else -> OperationStatus.ACTIVE
        }

    fun toTransactionItem(customerNameSnapshot: String = "", notes: String? = null): TransactionItem {
        val legacyFields = LegacyAccountingBridge.toLegacyFields(
            transactionType = TransactionType.SALE,
            saleType = typedSaleType,
            paymentStatus = typedPaymentStatus
        )
        val resolvedNotes = if (!notes.isNullOrBlank()) notes else "فاتورة رقم $invoiceNumber"
        return TransactionItem(
            id = id,
            title = "فاتورة $invoiceNumber",
            customerNameSnapshot = customerNameSnapshot,
            activityType = legacyFields.activityType,
            amount = totalAmount,
            relativeTime = "الآن",
            date = transactionDate,
            isCredit = legacyFields.isCredit,
            notes = resolvedNotes,
            settlementType = legacyFields.settlementType,
            customerId = customerId,
            customerName = customerNameSnapshot,
            transactionType = TransactionType.SALE,
            saleType = typedSaleType,
            paymentStatus = typedPaymentStatus,
            operationStatus = typedStatus,
            paidAmount = paidAmount,
            creditAmount = creditAmount
        )
    }
}
