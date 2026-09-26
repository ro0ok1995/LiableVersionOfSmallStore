package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 8: Sale Return Line Entity.
 *
 * Represents an individual returned product item line linked to a [SaleReturn] and original [SaleLine].
 *
 * Accounting Invariants:
 * 1. Supports partial returns (quantity <= original SaleLine returnable quantity).
 * 2. costPriceAtReturn freezes the historical cost at sale for exact COGS reversal.
 * 3. subtotal = quantity * unitPrice.
 */
@Entity(
    tableName = "sale_return_lines",
    foreignKeys = [
        ForeignKey(
            entity = SaleReturn::class,
            parentColumns = ["id"],
            childColumns = ["saleReturnId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SaleLine::class,
            parentColumns = ["id"],
            childColumns = ["saleLineId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("saleReturnId"),
        Index("saleLineId"),
        Index("productId")
    ]
)
data class SaleReturnLine(
    @PrimaryKey val id: String,
    val saleReturnId: String,
    val saleLineId: String,
    val productId: String? = null,
    val productNameSnapshot: String,
    val quantity: Int,
    val unitPrice: Double,
    val costPriceAtReturn: Double,
    val subtotal: Double,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Sale return line ID cannot be blank" }
        require(saleReturnId.isNotBlank()) { "Sale return ID cannot be blank" }
        require(saleLineId.isNotBlank()) { "Sale line ID cannot be blank" }
        require(quantity > 0) { "Return quantity ($quantity) must be greater than zero" }
        require(unitPrice >= 0.0) { "unitPrice ($unitPrice) must be non-negative" }
        require(costPriceAtReturn >= 0.0) { "costPriceAtReturn ($costPriceAtReturn) must be non-negative" }
        require(Math.abs(subtotal - (quantity * unitPrice)) < 0.01) {
            "SaleReturnLine subtotal ($subtotal) must equal quantity ($quantity) * unitPrice ($unitPrice)"
        }
        require(productNameSnapshot.isNotBlank()) { "productNameSnapshot must not be blank" }
    }

    val cogsReversed: Double
        get() = quantity * costPriceAtReturn

    val grossProfitReversed: Double
        get() = subtotal - cogsReversed
}
