package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.OperationStatus
import com.example.model.TransactionItem
import com.example.model.TransactionType

/**
 * Phase 10 Expenses:
 * Authoritative [Expense] Entity.
 *
 * Invariants:
 * 1. categoryId FK references expense_categories(id) with RESTRICT.
 * 2. Expense must NOT be treated as a sale.
 * 3. Expense must NOT be treated as a purchase/inventory transaction.
 * 4. Reduces the designated financial account.
 * 5. Immutable operational transaction: status changes to REVERSED upon reversal.
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseCategory::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("categoryId"),
        Index("financialAccountId"),
        Index("date"),
        Index("status")
    ]
)
data class Expense(
    @PrimaryKey val id: String,
    val categoryId: String,
    val amount: Double,
    val paymentMethodId: String? = null,
    val financialAccountId: String,
    val date: String,
    val description: String,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Expense ID cannot be blank" }
        require(categoryId.isNotBlank()) { "Expense categoryId cannot be blank" }
        require(amount > 0.0) { "Expense amount ($amount) must be strictly greater than zero" }
        require(financialAccountId.isNotBlank()) { "financialAccountId cannot be blank" }
        require(date.isNotBlank()) { "Expense date cannot be blank" }
        require(description.isNotBlank()) { "Expense description cannot be blank" }
        require(status in setOf("ACTIVE", "REVERSED")) { "Invalid status: $status" }
    }

    val typedOperationStatus: OperationStatus
        get() = if (status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
}

fun Expense.toTransactionItem(categoryName: String = ""): TransactionItem {
    return TransactionItem(
        id = id,
        title = "مصروف: $description",
        activityType = "مصروف",
        amount = amount,
        isCredit = false,
        date = date,
        relativeTime = "الآن",
        customerName = categoryName.ifBlank { "مصروف" },
        notes = description,
        customerId = null,
        isArchived = false,
        archivedDate = null,
        transactionType = TransactionType.EXPENSE,
        saleType = null,
        paymentStatus = null,
        operationStatus = typedOperationStatus,
        paidAmount = amount,
        creditAmount = 0.0
    )
}
