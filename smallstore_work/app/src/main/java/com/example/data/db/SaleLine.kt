package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 3 Sales Engine:
 * Authoritative [SaleLine] Entity.
 *
 * Invariants:
 * 1. Linked to parent Sale via saleId FK with CASCADE.
 * 2. costPriceAtSale is frozen at time of sale — must never change when Product.costPrice changes later.
 * 3. subtotal = quantity * unitPrice.
 * 4. Index on saleId for fast retrieval.
 */
@Entity(
    tableName = "sale_lines",
    foreignKeys = [
        ForeignKey(
            entity = Sale::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("saleId"),
        Index("productId")
    ]
)
data class SaleLine(
    @PrimaryKey val id: String,
    val saleId: String,
    val productId: String? = null,
    val productNameSnapshot: String,
    val quantity: Int,
    val unitPrice: Double,
    val costPriceAtSale: Double,
    val subtotal: Double,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(quantity > 0) { "quantity ($quantity) must be greater than zero" }
        require(unitPrice >= 0.0) { "unitPrice ($unitPrice) must be non-negative" }
        require(costPriceAtSale >= 0.0) { "costPriceAtSale ($costPriceAtSale) must be non-negative" }
        require(Math.abs(subtotal - (quantity * unitPrice)) < 0.01) {
            "SaleLine subtotal ($subtotal) must equal quantity ($quantity) * unitPrice ($unitPrice)"
        }
        require(productNameSnapshot.isNotBlank()) { "productNameSnapshot must not be blank" }
    }
}
