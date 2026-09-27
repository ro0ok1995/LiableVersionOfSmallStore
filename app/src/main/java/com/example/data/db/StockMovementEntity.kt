package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.accounting.InventoryMovementEntry
import com.example.accounting.InventoryMovementType
import com.example.model.OperationStatus

/**
 * Phase 11 Inventory Ledger:
 * Persistent Stock Movement Entity.
 * Represents an immutable physical stock change in the `stock_movements` ledger table.
 */
@Entity(
    tableName = "stock_movements",
    indices = [
        Index("productId"),
        Index("timestamp"),
        Index(value = ["productId", "timestamp"]),
        Index("transactionId"),
        Index("status")
    ]
)
data class StockMovementEntity(
    @PrimaryKey
    val id: String,
    val productId: String,
    val productNameSnapshot: String = "",
    val transactionId: String = "",
    val lineId: String = "",
    val date: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val movementType: String,
    val quantityIn: Int = 0,
    val quantityOut: Int = 0,
    val unitCost: Double = 0.0,
    val reference: String = "",
    val referenceType: String = "",
    val referenceId: String = "",
    val status: String = "ACTIVE"
) {
    init {
        require(id.isNotBlank()) { "StockMovementEntity id must not be blank" }
        require(productId.isNotBlank()) { "StockMovementEntity productId must not be blank" }
        require(quantityIn >= 0) { "quantityIn ($quantityIn) cannot be negative" }
        require(quantityOut >= 0) { "quantityOut ($quantityOut) cannot be negative" }
        require(quantityIn > 0 || quantityOut > 0) {
            "StockMovementEntity must have either quantityIn > 0 or quantityOut > 0"
        }
    }

    fun toDomain(): InventoryMovementEntry {
        val type = try {
            InventoryMovementType.valueOf(movementType)
        } catch (_: Exception) {
            InventoryMovementType.ADJUSTMENT_IN
        }
        val opStatus = try {
            OperationStatus.valueOf(status)
        } catch (_: Exception) {
            OperationStatus.ACTIVE
        }
        return InventoryMovementEntry(
            id = id,
            productId = productId,
            productNameSnapshot = productNameSnapshot,
            transactionId = transactionId,
            lineId = lineId,
            date = date,
            timestamp = timestamp,
            movementType = type,
            quantityIn = quantityIn,
            quantityOut = quantityOut,
            unitCost = unitCost,
            reference = reference,
            referenceType = referenceType,
            referenceId = referenceId,
            operationStatus = opStatus
        )
    }

    companion object {
        fun fromDomain(entry: InventoryMovementEntry): StockMovementEntity {
            return StockMovementEntity(
                id = entry.id,
                productId = entry.productId,
                productNameSnapshot = entry.productNameSnapshot,
                transactionId = entry.transactionId,
                lineId = entry.lineId,
                date = entry.date,
                timestamp = entry.timestamp,
                movementType = entry.movementType.name,
                quantityIn = entry.quantityIn,
                quantityOut = entry.quantityOut,
                unitCost = entry.unitCost,
                reference = entry.reference,
                referenceType = entry.referenceType,
                referenceId = entry.referenceId,
                status = entry.operationStatus.name
            )
        }
    }
}
