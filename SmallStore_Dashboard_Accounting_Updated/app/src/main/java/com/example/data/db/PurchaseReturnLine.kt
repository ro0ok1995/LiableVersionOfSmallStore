package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Exact line-level merchandise returned from an original purchase. */
@Entity(
    tableName = "purchase_return_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseReturn::class,
            parentColumns = ["id"],
            childColumns = ["purchaseReturnId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PurchaseLine::class,
            parentColumns = ["id"],
            childColumns = ["purchaseLineId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("purchaseReturnId"), Index("purchaseLineId"), Index("productId")]
)
data class PurchaseReturnLine(
    @PrimaryKey val id: String,
    val purchaseReturnId: String,
    val purchaseLineId: String,
    val productId: String? = null,
    val productNameSnapshot: String,
    val quantity: Int,
    val unitCost: Double,
    val subtotal: Double,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Purchase return line ID cannot be blank" }
        require(purchaseReturnId.isNotBlank()) { "Purchase return ID cannot be blank" }
        require(purchaseLineId.isNotBlank()) { "Purchase line ID cannot be blank" }
        require(quantity > 0) { "Return quantity must be greater than zero" }
        require(unitCost > 0.0) { "unitCost must be greater than zero" }
        require(productNameSnapshot.isNotBlank()) { "productNameSnapshot must not be blank" }
        require(kotlin.math.abs(subtotal - quantity * unitCost) < 0.01) {
            "PurchaseReturnLine subtotal must equal quantity * unitCost"
        }
    }
}
