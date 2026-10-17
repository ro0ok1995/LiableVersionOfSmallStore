package com.example.accounting

import com.example.data.db.Adjustment
import com.example.data.db.Purchase
import com.example.data.db.PurchaseLine
import com.example.data.db.PurchaseReturn
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.SaleReturn
import com.example.data.db.SaleReturnLine
import com.example.data.db.StockMovementEntity
import com.example.model.OperationStatus
import kotlin.math.roundToInt

/**
 * =============================================================================
 * PURE DOMAIN INVENTORY LEDGER CALCULATOR
 * =============================================================================
 *
 * Phase 11 of SmallStore Accounting:
 * Pure, deterministic domain calculation layer that derives physical product
 * stock levels, perpetual inventory ledger movements, and inventory valuation
 * from immutable transaction lines.
 *
 * CRITICAL INVARIANTS:
 * 1. Product stock is ALWAYS derived dynamically from transaction lines:
 *    - PurchaseLine = IN (+quantity)
 *    - SaleLine = OUT (-quantity)
 *    - SaleReturnLine = IN (+quantity)
 *    - PurchaseReturn = OUT (-quantity)
 *    - Physical Inventory Adjustment (DEBIT = +quantity, CREDIT = -quantity)
 * 2. Zero stored editable stock/balance field on Product.
 * 3. Reversed transactions (status == "REVERSED") NEVER contribute to active quantity on hand.
 * 4. Product identity is strictly by productId.
 */
object InventoryLedgerCalculator {

    /**
     * Calculates the stock summary for a specific product from transaction lines.
     */
    fun calculateProductStock(
        productId: String,
        purchases: List<Purchase>,
        purchaseLines: List<PurchaseLine>,
        sales: List<Sale>,
        saleLines: List<SaleLine>,
        saleReturns: List<SaleReturn>,
        saleReturnLines: List<SaleReturnLine>,
        fallbackUnitCost: Double = 0.0,
        productName: String = "",
        purchaseReturns: List<PurchaseReturn> = emptyList(),
        adjustments: List<Adjustment> = emptyList()
    ): ProductStockSummary {
        val purchasesMap = purchases.associateBy { it.id }
        val salesMap = sales.associateBy { it.id }
        val saleReturnsMap = saleReturns.associateBy { it.id }

        var totalPurchased = 0
        var totalSold = 0
        var totalReturnedFromSales = 0
        var totalReturnedToSuppliers = 0
        var totalAdjustments = 0
        var activeMovements = 0
        var reversedMovements = 0

        var resolvedProductName = productName
        var latestActivePurchaseUnitCost: Double? = null
        var latestPurchaseTimestamp: Long = -1L

        // Inbound: Purchases
        for (line in purchaseLines) {
            if (line.productId != productId) continue
            if (resolvedProductName.isBlank() && line.productNameSnapshot.isNotBlank()) {
                resolvedProductName = line.productNameSnapshot
            }
            val parent = purchasesMap[line.purchaseId]
            val isReversed = parent?.status == "REVERSED"
            if (isReversed) {
                reversedMovements++
            } else {
                totalPurchased += line.quantity
                activeMovements++
                if (line.createdAt >= latestPurchaseTimestamp) {
                    latestPurchaseTimestamp = line.createdAt
                    latestActivePurchaseUnitCost = line.unitCost
                }
            }
        }

        // Outbound: Purchase Returns (Stock returned back to supplier)
        for (pr in purchaseReturns) {
            val parent = purchasesMap[pr.purchaseId]
            val isReversed = pr.status == "REVERSED" || parent?.status == "REVERSED"
            if (isReversed) {
                reversedMovements++
            } else {
                val linesForPurchase = purchaseLines.filter { it.purchaseId == pr.purchaseId && it.productId == productId }
                val totalPurchaseAmt = parent?.totalAmount ?: 0.0
                if (linesForPurchase.isNotEmpty() && totalPurchaseAmt > 0.0) {
                    val returnRatio = (pr.amount / totalPurchaseAmt).coerceAtMost(1.0)
                    for (line in linesForPurchase) {
                        val returnedQty = (line.quantity * returnRatio).roundToInt().coerceAtLeast(0)
                        totalReturnedToSuppliers += returnedQty
                    }
                    activeMovements++
                }
            }
        }

        // Outbound: Sales
        for (line in saleLines) {
            if (line.productId != productId) continue
            if (resolvedProductName.isBlank() && line.productNameSnapshot.isNotBlank()) {
                resolvedProductName = line.productNameSnapshot
            }
            val parent = salesMap[line.saleId]
            val isReversed = parent?.status == "REVERSED"
            if (isReversed) {
                reversedMovements++
            } else {
                totalSold += line.quantity
                activeMovements++
            }
        }

        // Inbound: Sale Returns (Restocked merchandise from customers)
        for (line in saleReturnLines) {
            if (line.productId != productId) continue
            if (resolvedProductName.isBlank() && line.productNameSnapshot.isNotBlank()) {
                resolvedProductName = line.productNameSnapshot
            }
            val parent = saleReturnsMap[line.saleReturnId]
            val isReversed = parent?.status == "REVERSED"
            if (isReversed) {
                reversedMovements++
            } else {
                totalReturnedFromSales += line.quantity
                activeMovements++
            }
        }

        // Physical inventory adjustments (DEBIT = +, CREDIT = -)
        for (adj in adjustments) {
            if (adj.entityType != "PRODUCT" || adj.entityId != productId) continue
            val isReversed = adj.status == "REVERSED"
            if (isReversed) {
                reversedMovements++
            } else {
                val delta = adj.amount.toInt()
                if (adj.direction == "DEBIT") {
                    totalAdjustments += delta
                } else {
                    totalAdjustments -= delta
                }
                activeMovements++
            }
        }

        val quantityOnHand = totalPurchased - totalSold + totalReturnedFromSales - totalReturnedToSuppliers + totalAdjustments
        val unitCost = latestActivePurchaseUnitCost ?: fallbackUnitCost
        val totalValuation = if (quantityOnHand > 0) quantityOnHand * unitCost else 0.0

        return ProductStockSummary(
            productId = productId,
            productName = resolvedProductName,
            totalPurchased = totalPurchased,
            totalSold = totalSold,
            totalReturnedFromSales = totalReturnedFromSales,
            totalReturnedToSuppliers = totalReturnedToSuppliers,
            totalAdjustments = totalAdjustments,
            quantityOnHand = quantityOnHand,
            unitCost = unitCost,
            totalValuation = totalValuation,
            activeMovementCount = activeMovements,
            reversedMovementCount = reversedMovements
        )
    }

    /**
     * Builds a chronological perpetual inventory movement ledger for a product.
     */
    fun buildInventoryLedger(
        productId: String,
        purchases: List<Purchase>,
        purchaseLines: List<PurchaseLine>,
        sales: List<Sale>,
        saleLines: List<SaleLine>,
        saleReturns: List<SaleReturn>,
        saleReturnLines: List<SaleReturnLine>,
        purchaseReturns: List<PurchaseReturn> = emptyList(),
        adjustments: List<Adjustment> = emptyList()
    ): List<InventoryMovementEntry> {
        val purchasesMap = purchases.associateBy { it.id }
        val salesMap = sales.associateBy { it.id }
        val saleReturnsMap = saleReturns.associateBy { it.id }

        val rawEntries = mutableListOf<InventoryMovementEntry>()

        // 1. Purchases (IN)
        for (line in purchaseLines) {
            if (line.productId != productId) continue
            val parent = purchasesMap[line.purchaseId]
            val status = if (parent?.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val date = parent?.purchaseDate ?: ""
            rawEntries.add(
                InventoryMovementEntry(
                    id = "inv_p_${line.id}",
                    productId = productId,
                    productNameSnapshot = line.productNameSnapshot,
                    transactionId = line.purchaseId,
                    lineId = line.id,
                    date = date,
                    timestamp = line.createdAt,
                    movementType = InventoryMovementType.PURCHASE_IN,
                    quantityIn = line.quantity,
                    quantityOut = 0,
                    unitCost = line.unitCost,
                    reference = parent?.invoiceNumber ?: line.purchaseId,
                    referenceType = "PURCHASE",
                    referenceId = line.purchaseId,
                    operationStatus = status
                )
            )
        }

        // 2. Purchase Returns (OUT)
        for (pr in purchaseReturns) {
            val parent = purchasesMap[pr.purchaseId]
            val status = if (pr.status == "REVERSED" || parent?.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val linesForPurchase = purchaseLines.filter { it.purchaseId == pr.purchaseId && it.productId == productId }
            val totalPurchaseAmt = parent?.totalAmount ?: 0.0
            if (linesForPurchase.isNotEmpty() && totalPurchaseAmt > 0.0) {
                val returnRatio = (pr.amount / totalPurchaseAmt).coerceAtMost(1.0)
                for (line in linesForPurchase) {
                    val returnedQty = (line.quantity * returnRatio).roundToInt().coerceAtLeast(0)
                    if (returnedQty > 0) {
                        rawEntries.add(
                            InventoryMovementEntry(
                                id = "inv_pr_${pr.id}_${line.id}",
                                productId = productId,
                                productNameSnapshot = line.productNameSnapshot,
                                transactionId = pr.id,
                                lineId = line.id,
                                date = pr.returnDate,
                                timestamp = pr.createdAt,
                                movementType = InventoryMovementType.PURCHASE_RETURN_OUT,
                                quantityIn = 0,
                                quantityOut = returnedQty,
                                unitCost = line.unitCost,
                                reference = pr.reason,
                                referenceType = "PURCHASE_RETURN",
                                referenceId = pr.id,
                                operationStatus = status
                            )
                        )
                    }
                }
            }
        }

        // 3. Sales (OUT)
        for (line in saleLines) {
            if (line.productId != productId) continue
            val parent = salesMap[line.saleId]
            val status = if (parent?.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val date = parent?.transactionDate ?: ""
            rawEntries.add(
                InventoryMovementEntry(
                    id = "inv_s_${line.id}",
                    productId = productId,
                    productNameSnapshot = line.productNameSnapshot,
                    transactionId = line.saleId,
                    lineId = line.id,
                    date = date,
                    timestamp = line.createdAt,
                    movementType = InventoryMovementType.SALE_OUT,
                    quantityIn = 0,
                    quantityOut = line.quantity,
                    unitCost = line.costPriceAtSale,
                    reference = parent?.invoiceNumber ?: line.saleId,
                    referenceType = "SALE",
                    referenceId = line.saleId,
                    operationStatus = status
                )
            )
        }

        // 4. Sale Returns (IN)
        for (line in saleReturnLines) {
            if (line.productId != productId) continue
            val parent = saleReturnsMap[line.saleReturnId]
            val status = if (parent?.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val date = parent?.returnDate ?: ""
            rawEntries.add(
                InventoryMovementEntry(
                    id = "inv_sr_${line.id}",
                    productId = productId,
                    productNameSnapshot = line.productNameSnapshot,
                    transactionId = line.saleReturnId,
                    lineId = line.id,
                    date = date,
                    timestamp = line.createdAt,
                    movementType = InventoryMovementType.SALE_RETURN_IN,
                    quantityIn = line.quantity,
                    quantityOut = 0,
                    unitCost = line.costPriceAtReturn,
                    reference = line.saleReturnId,
                    referenceType = "SALE_RETURN",
                    referenceId = line.saleReturnId,
                    operationStatus = status
                )
            )
        }

        // 5. Physical Inventory Adjustments (DEBIT = IN, CREDIT = OUT)
        for (adj in adjustments) {
            if (adj.entityType != "PRODUCT" || adj.entityId != productId) continue
            val status = if (adj.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val qty = adj.amount.toInt()
            val isDebit = adj.direction == "DEBIT"
            val isDamage = !isDebit && isDamageReason(adj.reason)
            val movType = when {
                isDebit -> InventoryMovementType.ADJUSTMENT_IN
                isDamage -> InventoryMovementType.DAMAGE_OUT
                else -> InventoryMovementType.ADJUSTMENT_OUT
            }
            rawEntries.add(
                InventoryMovementEntry(
                    id = "inv_adj_${adj.id}",
                    productId = productId,
                    productNameSnapshot = "",
                    transactionId = adj.id,
                    lineId = adj.id,
                    date = adj.date,
                    timestamp = adj.createdAt,
                    movementType = movType,
                    quantityIn = if (isDebit) qty else 0,
                    quantityOut = if (!isDebit) qty else 0,
                    unitCost = 0.0,
                    reference = adj.reason,
                    referenceType = if (isDamage) "DAMAGE" else "ADJUSTMENT",
                    referenceId = adj.id,
                    operationStatus = status
                )
            )
        }

        // Sort chronologically: date ascending, timestamp ascending, id ascending
        val sortedEntries = rawEntries.sortedWith(
            compareBy<InventoryMovementEntry> { it.date }
                .thenBy { it.timestamp }
                .thenBy { it.id }
        )

        // Calculate running inventory balance
        var runningQty = 0
        return sortedEntries.map { entry ->
            if (entry.operationStatus == OperationStatus.ACTIVE) {
                runningQty += (entry.quantityIn - entry.quantityOut)
            }
            entry.copy(runningQuantity = runningQty)
        }
    }

    /**
     * Calculates stock summaries for a collection of products.
     */
    fun calculateAllProductsStock(
        productIds: Set<String>,
        purchases: List<Purchase>,
        purchaseLines: List<PurchaseLine>,
        sales: List<Sale>,
        saleLines: List<SaleLine>,
        saleReturns: List<SaleReturn>,
        saleReturnLines: List<SaleReturnLine>,
        productCostPrices: Map<String, Double> = emptyMap(),
        productNames: Map<String, String> = emptyMap(),
        purchaseReturns: List<PurchaseReturn> = emptyList(),
        adjustments: List<Adjustment> = emptyList()
    ): Map<String, ProductStockSummary> {
        return productIds.associateWith { pid ->
            calculateProductStock(
                productId = pid,
                purchases = purchases,
                purchaseLines = purchaseLines,
                sales = sales,
                saleLines = saleLines,
                saleReturns = saleReturns,
                saleReturnLines = saleReturnLines,
                fallbackUnitCost = productCostPrices[pid] ?: 0.0,
                productName = productNames[pid] ?: "",
                purchaseReturns = purchaseReturns,
                adjustments = adjustments
            )
        }
    }

    /**
     * Calculates ProductStockSummary directly from persistent StockMovementEntity records.
     * Single source of truth for physical stock calculations.
     */
    fun calculateProductStockFromMovements(
        productId: String,
        movements: List<StockMovementEntity>,
        fallbackUnitCost: Double = 0.0,
        productName: String = "",
        openingQuantity: Int = 0
    ): ProductStockSummary {
        var totalPurchased = 0
        var totalSold = 0
        var totalReturnedFromSales = 0
        var totalReturnedToSuppliers = 0
        var totalAdjustments = 0
        var activeMovements = 0
        var reversedMovements = 0

        var resolvedProductName = productName
        var latestActivePurchaseUnitCost: Double? = null
        var latestPurchaseTimestamp: Long = -1L

        for (movement in movements) {
            if (movement.productId != productId) continue

            if (resolvedProductName.isBlank() && movement.productNameSnapshot.isNotBlank()) {
                resolvedProductName = movement.productNameSnapshot
            }

            if (movement.status == "REVERSED") {
                reversedMovements++
                continue
            }

            activeMovements++
            when (movement.movementType) {
                InventoryMovementType.PURCHASE_IN.name -> {
                    totalPurchased += movement.quantityIn
                    if (movement.timestamp >= latestPurchaseTimestamp) {
                        latestPurchaseTimestamp = movement.timestamp
                        if (movement.unitCost > 0.0) {
                            latestActivePurchaseUnitCost = movement.unitCost
                        }
                    }
                }
                InventoryMovementType.SALE_OUT.name -> {
                    totalSold += movement.quantityOut
                }
                InventoryMovementType.SALE_RETURN_IN.name -> {
                    totalReturnedFromSales += movement.quantityIn
                }
                InventoryMovementType.PURCHASE_RETURN_OUT.name -> {
                    totalReturnedToSuppliers += movement.quantityOut
                }
                InventoryMovementType.ADJUSTMENT_IN.name -> {
                    totalAdjustments += movement.quantityIn
                }
                InventoryMovementType.ADJUSTMENT_OUT.name,
                InventoryMovementType.DAMAGE_OUT.name -> {
                    totalAdjustments -= movement.quantityOut
                }
                else -> {
                    if (movement.quantityIn > 0) {
                        totalAdjustments += movement.quantityIn
                    }
                    if (movement.quantityOut > 0) {
                        totalAdjustments -= movement.quantityOut
                    }
                }
            }
        }

        val quantityOnHand = openingQuantity + totalPurchased - totalSold + totalReturnedFromSales - totalReturnedToSuppliers + totalAdjustments
        val unitCost = latestActivePurchaseUnitCost ?: fallbackUnitCost
        val totalValuation = if (quantityOnHand > 0) quantityOnHand * unitCost else 0.0

        return ProductStockSummary(
            productId = productId,
            productName = resolvedProductName,
            totalPurchased = totalPurchased,
            totalSold = totalSold,
            totalReturnedFromSales = totalReturnedFromSales,
            totalReturnedToSuppliers = totalReturnedToSuppliers,
            totalAdjustments = totalAdjustments,
            quantityOnHand = quantityOnHand,
            unitCost = unitCost,
            totalValuation = totalValuation,
            activeMovementCount = activeMovements,
            reversedMovementCount = reversedMovements
        )
    }

    /**
     * Calculates all products stock summaries directly from persistent StockMovementEntity records.
     */
    fun calculateAllProductsStockFromMovements(
        productIds: Set<String>,
        movements: List<StockMovementEntity>,
        productCostPrices: Map<String, Double> = emptyMap(),
        productNames: Map<String, String> = emptyMap(),
        openingQuantities: Map<String, Int> = emptyMap()
    ): Map<String, ProductStockSummary> {
        val movementsByProduct = movements.groupBy { it.productId }
        return productIds.associateWith { pid ->
            calculateProductStockFromMovements(
                productId = pid,
                movements = movementsByProduct[pid] ?: emptyList(),
                fallbackUnitCost = productCostPrices[pid] ?: 0.0,
                productName = productNames[pid] ?: "",
                openingQuantity = openingQuantities[pid] ?: 0
            )
        }
    }

    /**
     * Identifies whether an adjustment reason describes damaged or spoiled merchandise.
     */
    fun isDamageReason(reason: String): Boolean {
        val lower = reason.lowercase()
        return lower.contains("damage") || lower.contains("loss") || lower.contains("تالف") || lower.contains("تلف") || lower.contains("spoil")
    }

    /**
     * Builds and returns the chronological stock movement ledger (StockMovement model).
     */
    fun getStockMovements(
        productId: String,
        purchases: List<Purchase>,
        purchaseLines: List<PurchaseLine>,
        sales: List<Sale>,
        saleLines: List<SaleLine>,
        saleReturns: List<SaleReturn>,
        saleReturnLines: List<SaleReturnLine>,
        purchaseReturns: List<PurchaseReturn> = emptyList(),
        adjustments: List<Adjustment> = emptyList()
    ): List<StockMovement> {
        return buildInventoryLedger(
            productId = productId,
            purchases = purchases,
            purchaseLines = purchaseLines,
            sales = sales,
            saleLines = saleLines,
            saleReturns = saleReturns,
            saleReturnLines = saleReturnLines,
            purchaseReturns = purchaseReturns,
            adjustments = adjustments
        )
    }

    /**
     * Calculates total inventory valuation across multiple product stock summaries.
     */
    fun calculateTotalInventoryValuation(summaries: Collection<ProductStockSummary>): Double {
        return summaries.filter { it.quantityOnHand > 0 }.sumOf { it.totalValuation }
    }
}
