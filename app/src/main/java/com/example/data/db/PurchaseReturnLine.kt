package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 11 Inventory:
 * Authoritative [PurchaseReturnLine] Entity.
 *
 * Invariants:
 * 1. Linked to parent PurchaseReturn via purchaseReturnId FK with CASCADE.
 * 2. Links returned goods to a specific productId.
 * 3. costPriceAtReturn freezes the historical cost at return for exact inventory/AP reduction.
 */
@Entity(
    tableName = "purchase_return_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseReturn::class,
            parentColumns = ["id"],
            childColumns = ["purchaseReturnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("purchaseReturnId"),
        Index("productId")
    ]
)
data class PurchaseReturnLine(
    @PrimaryKey val id: String,
    val purchaseReturnId: String,
    val productId: String? = null,
    val productNameSnapshot: String,
    val quantity: Int,
    val costPriceAtReturn: Double,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "PurchaseReturnLine id must not be blank" }
        require(purchaseReturnId.isNotBlank()) { "purchaseReturnId must not be blank" }
        require(quantity > 0) { "quantity ($quantity) must be greater than zero" }
        require(costPriceAtReturn >= 0.0) { "costPriceAtReturn ($costPriceAtReturn) must be non-negative" }
        require(productNameSnapshot.isNotBlank()) { "productNameSnapshot must not be blank" }
    }
}
