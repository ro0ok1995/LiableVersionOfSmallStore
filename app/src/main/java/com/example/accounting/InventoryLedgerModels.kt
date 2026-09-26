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
 */
enum class InventoryMovementType {
    PURCHASE_IN,
    SALE_OUT,
    SALE_RETURN_IN,
    PURCHASE_RETURN_OUT,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT
}

/**
 * Normalized domain inventory movement entry representing a physical stock change.
 */
data class InventoryMovementEntry(
    val id: String,
    val productId: String,
    val productNameSnapshot: String,
    val transactionId: String,
    val lineId: String,
    val date: String,
    val timestamp: Long,
    val movementType: InventoryMovementType,
    val quantityIn: Int = 0,
    val quantityOut: Int = 0,
    val unitCost: Double = 0.0,
    val runningQuantity: Int = 0,
    val reference: String = "",
    val operationStatus: OperationStatus = OperationStatus.ACTIVE
) {
    /**
     * Net physical quantity impact:
     * Positive for stock additions (purchases, sale returns),
     * Negative for stock reductions (sales).
     */
    val netQuantityEffect: Int
        get() = when (operationStatus) {
            OperationStatus.ACTIVE -> quantityIn - quantityOut
            OperationStatus.REVERSED -> 0
        }
}

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
