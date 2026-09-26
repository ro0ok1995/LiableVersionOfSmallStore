package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.EntityType

@Entity(
    tableName = "opening_balances",
    indices = [
        Index(value = ["entityType", "entityId"])
    ]
)
data class OpeningBalance(
    @PrimaryKey
    val id: String,
    val entityType: String,
    val entityId: String,
    val amount: Double,
    val direction: String,
    val date: String,
    val reason: String? = null,
    val reference: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val typedEntityType: EntityType
        get() = try {
            EntityType.valueOf(entityType)
        } catch (_: Exception) {
            EntityType.CUSTOMER
        }

    init {
        require(id.isNotBlank()) { "Opening balance ID must not be blank" }
        require(entityId.isNotBlank()) { "Opening balance entityId must not be blank" }
        require(amount > 0.0) { "Opening balance amount must be strictly positive, was: $amount" }
        require(entityType in setOf("CUSTOMER", "SUPPLIER", "FINANCIAL_ACCOUNT")) {
            "Invalid entityType: $entityType. Must be one of: CUSTOMER, SUPPLIER, FINANCIAL_ACCOUNT"
        }
        require(direction in setOf("DEBIT", "CREDIT")) {
            "Invalid direction: $direction. Must be one of: DEBIT, CREDIT"
        }
    }
}
