package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.OperationStatus

@Entity(
    tableName = "customer_payments",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PaymentMethod::class,
            parentColumns = ["id"],
            childColumns = ["paymentMethodId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = FinancialAccount::class,
            parentColumns = ["id"],
            childColumns = ["financialAccountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("customerId"),
        Index(value = ["customerId", "transactionDate"]),
        Index("paymentMethodId"),
        Index("financialAccountId")
    ]
)
data class CustomerPayment(
    @PrimaryKey
    val id: String,
    val customerId: String,
    val amount: Double,
    val paymentMethodId: String,
    val financialAccountId: String? = null,
    val reference: String? = null,
    val transactionDate: String,
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val status: String = "ACTIVE"
) {
    init {
        require(id.isNotBlank()) { "Customer payment ID must not be blank" }
        require(customerId.isNotBlank()) { "Customer payment customerId must not be blank" }
        require(paymentMethodId.isNotBlank()) { "Customer payment paymentMethodId must not be blank" }
        require(amount > 0.0) { "Customer payment amount must be strictly positive, was: $amount" }
        require(status == "ACTIVE" || status == "REVERSED") {
            "Invalid status: $status. Must be one of: 'ACTIVE', 'REVERSED'"
        }
    }

    val typedStatus: OperationStatus
        get() = if (status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
}
