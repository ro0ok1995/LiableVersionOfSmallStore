package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.EntityType

@Entity(
    tableName = "adjustments",
    indices = [
        Index(value = ["entityType", "entityId"])
    ]
)
data class Adjustment(
    @PrimaryKey
    val id: String,
    val entityType: String,
    val entityId: String,
    val amount: Double,
    val direction: String,
    val date: String,
    val reason: String,
    val reference: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE"
) {
    val typedEntityType: EntityType
        get() = try {
            EntityType.valueOf(entityType)
        } catch (_: Exception) {
            EntityType.CUSTOMER
        }

    init {
        require(id.isNotBlank()) { "Adjustment ID must not be blank" }
        require(entityId.isNotBlank()) { "Adjustment entityId must not be blank" }
        require(amount > 0.0) { "Adjustment amount must be strictly positive, was: $amount" }
        require(entityType in setOf("CUSTOMER", "SUPPLIER", "FINANCIAL_ACCOUNT", "PRODUCT")) {
            "Invalid entityType: $entityType. Must be one of: CUSTOMER, SUPPLIER, FINANCIAL_ACCOUNT, PRODUCT"
        }
        require(direction in setOf("DEBIT", "CREDIT")) {
            "Invalid direction: $direction. Must be one of: DEBIT, CREDIT"
        }
        require(reason.isNotBlank()) { "Adjustment reason must not be blank" }
    }
}
