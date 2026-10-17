package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 9 Suppliers / Purchases:
 * Authoritative [PurchaseLine] Entity.
 */
@Entity(
    tableName = "purchase_lines",
    foreignKeys = [
        ForeignKey(
            entity = Purchase::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("purchaseId"),
        Index("productId")
    ]
)
data class PurchaseLine(
    @PrimaryKey val id: String,
    val purchaseId: String,
    val productId: String? = null,
    val productNameSnapshot: String,
    val quantity: Int,
    val unitCost: Double,
    val subtotal: Double,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(purchaseId.isNotBlank()) { "purchaseId must not be blank" }
        require(quantity > 0) { "quantity ($quantity) must be greater than zero" }
        require(unitCost > 0.0) { "unitCost ($unitCost) must be greater than zero" }
        require(Math.abs(subtotal - (quantity * unitCost)) < 0.01) {
            "PurchaseLine subtotal ($subtotal) must equal quantity ($quantity) * unitCost ($unitCost)"
        }
        require(productNameSnapshot.isNotBlank()) { "productNameSnapshot must not be blank" }
    }
}
