package com.example.accounting

import com.example.model.OperationStatus

/**
 * Phase 11 Inventory Ledger:
 * Movement classification for perpetual stock tracking.
 *
 * Rules:
 * - PURCHASE_IN: Inbound stock received from suppliers (+quantity).
 * - SALE_OUT: Outbound stock delivered to customers (-quantity).
 * - SALE_RETURN_IN: Inbound stock restocked from customer returns (+quantity).
 * - DAMAGE_OUT: Outbound stock written off due to damage/spoilage (-quantity).
 * - ADJUSTMENT_IN: Inbound stock adjustment (+quantity).
 * - ADJUSTMENT_OUT: Outbound stock adjustment (-quantity).
 */
enum class InventoryMovementType {
    PURCHASE_IN,
    SALE_OUT,
    SALE_RETURN_IN,
    PURCHASE_RETURN_OUT,
    DAMAGE_OUT,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT
}

/**
 * Normalized domain inventory movement entry representing a physical stock change.
 * Satisfies the Phase 11 StockMovement conceptual model.
 */
data class InventoryMovementEntry(
    val id: String,
    val productId: String,
    val productNameSnapshot: String = "",
    val transactionId: String = "",
    val lineId: String = "",
    val date: String = "",
    val timestamp: Long = 0L,
    val movementType: InventoryMovementType,
    val quantityIn: Int = 0,
    val quantityOut: Int = 0,
    val unitCost: Double = 0.0,
    val runningQuantity: Int = 0,
    val reference: String = "",
    val referenceType: String = "",
    val referenceId: String = "",
    val operationStatus: OperationStatus = OperationStatus.ACTIVE
) {
    /**
     * Absolute quantity of this stock movement.
     */
    val quantity: Int
        get() = if (quantityIn > 0) quantityIn else quantityOut

    /**
     * Net physical quantity impact:
     * Positive for stock additions (purchases, sale returns, adjustments in),
     * Negative for stock reductions (sales, damage, adjustments out).
     */
    val netQuantityEffect: Int
        get() = when (operationStatus) {
            OperationStatus.ACTIVE -> quantityIn - quantityOut
            OperationStatus.REVERSED -> 0
        }
}

/**
 * Phase 11 conceptual model alias for inventory ledger movements.
 */
typealias StockMovement = InventoryMovementEntry

/**
 * Immutable aggregated stock summary for a specific product.
 * Deterministically derived from persistent transaction lines.
 */
data class ProductStockSummary(
    val productId: String,
    val productName: String = "",
    val totalPurchased: Int = 0,
    val totalSold: Int = 0,
    val totalReturnedFromSales: Int = 0,
    val totalReturnedToSuppliers: Int = 0,
    val totalAdjustments: Int = 0,
    val quantityOnHand: Int = 0,
    val unitCost: Double = 0.0,
    val totalValuation: Double = 0.0,
    val activeMovementCount: Int = 0,
    val reversedMovementCount: Int = 0
) {
    val isInStock: Boolean get() = quantityOnHand > 0
    val isOutOfStock: Boolean get() = quantityOnHand <= 0
}
