package com.example.data.repository

import androidx.room.withTransaction
import com.example.accounting.InventoryMovementType
import com.example.data.db.PurchaseDao
import com.example.data.db.PurchaseLineDao
import com.example.data.db.PurchaseReturn
import com.example.data.db.PurchaseReturnDao
import com.example.data.db.PurchaseReturnLine
import com.example.data.db.PurchaseReturnLineDao
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.StockMovementDao
import com.example.data.db.StockMovementEntity as PersistentStockMovement
import com.example.data.db.SupplierDao
import com.example.data.db.TransactionDao
import com.example.data.db.toEntity
import com.example.data.db.toTransactionItem
import com.example.model.PurchaseReturnLineRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PurchaseReturnRepository(
    private val database: SmallStoreDatabase,
    private val purchaseReturnDao: PurchaseReturnDao = database.purchaseReturnDao(),
    private val purchaseReturnLineDao: PurchaseReturnLineDao = database.purchaseReturnLineDao(),
    private val purchaseDao: PurchaseDao = database.purchaseDao(),
    private val purchaseLineDao: PurchaseLineDao = database.purchaseLineDao(),
    private val supplierDao: SupplierDao = database.supplierDao(),
    private val stockMovementDao: StockMovementDao = database.stockMovementDao(),
    private val transactionDao: TransactionDao = database.transactionDao()
) {
    suspend fun getReturnById(id: String): PurchaseReturn? = purchaseReturnDao.getReturnById(id)

    suspend fun getReturnLines(returnId: String): List<PurchaseReturnLine> =
        purchaseReturnLineDao.getLinesForReturn(returnId)

    suspend fun getReturnsByPurchaseIdSync(purchaseId: String): List<PurchaseReturn> =
        purchaseReturnDao.getReturnsByPurchaseIdSync(purchaseId)

    suspend fun getReturnsBySupplierIdSync(supplierId: String): List<PurchaseReturn> =
        purchaseReturnDao.getReturnsBySupplierIdSync(supplierId)

    suspend fun getAllReturnsSync(): List<PurchaseReturn> = purchaseReturnDao.getAllReturnsSync()

    suspend fun getRemainingReturnableQuantities(purchaseId: String): Map<String, Int> {
        val purchaseLines = purchaseLineDao.getLinesByPurchaseId(purchaseId)
        val activeReturns = purchaseReturnDao.getReturnsByPurchaseIdSync(purchaseId)
            .filter { it.status == "ACTIVE" }
        val activeReturnIds = activeReturns.map { it.id }.toSet()
        val existingLines = purchaseReturnLineDao.getLinesForPurchaseLines(purchaseLines.map { it.id })
            .filter { it.purchaseReturnId in activeReturnIds }
        val returnedByLine = existingLines.groupBy { it.purchaseLineId }
            .mapValues { (_, lines) -> lines.sumOf { it.quantity } }
        return purchaseLines.associate { line ->
            line.id to (line.quantity - (returnedByLine[line.id] ?: 0)).coerceAtLeast(0)
        }
    }

    /**
     * Records an exact line-level purchase return. The returned amount is derived from
     * the selected original purchase lines; it is never used to guess quantities.
     */
    suspend fun recordPurchaseReturn(
        purchaseId: String,
        returnLines: List<PurchaseReturnLineRequest>,
        reason: String,
        returnDate: String? = null
    ): PurchaseReturn {
        require(purchaseId.isNotBlank()) { "Purchase ID cannot be blank" }
        require(returnLines.isNotEmpty()) { "Purchase return must contain at least one line" }
        require(reason.isNotBlank()) { "Return reason cannot be blank" }

        val purchase = purchaseDao.getPurchaseById(purchaseId)
            ?: throw IllegalArgumentException("Purchase $purchaseId not found")
        require(purchase.status == "ACTIVE") { "Cannot return from non-active purchase (status: ${purchase.status})" }

        val purchaseLines = purchaseLineDao.getLinesByPurchaseId(purchaseId)
        val lineMap = purchaseLines.associateBy { it.id }
        require(returnLines.map { it.purchaseLineId }.distinct().size == returnLines.size) {
            "Duplicate purchase return lines are not allowed"
        }

        val remaining = getRemainingReturnableQuantities(purchaseId)
        val currentMovements = stockMovementDao.getAllMovementsSync()
        val constructedLines = returnLines.map { request ->
            require(request.quantity > 0) { "Return quantity must be greater than zero" }
            val original = lineMap[request.purchaseLineId]
                ?: throw IllegalArgumentException("Purchase line ${request.purchaseLineId} does not belong to purchase $purchaseId")
            val remainingQty = remaining[original.id] ?: 0
            require(request.quantity <= remainingQty) {
                "Cannot return ${request.quantity} of '${original.productNameSnapshot}'; only $remainingQty remaining"
            }
            if (!original.productId.isNullOrBlank()) {
                val product = database.productDao().getProductById(original.productId)
                    ?: throw IllegalArgumentException("Product not found: ${original.productId}")
                val stock = com.example.accounting.CentralAccountingEngine.calculateProductStockFromMovements(
                    productId = original.productId,
                    movements = currentMovements,
                    fallbackUnitCost = product.costPrice,
                    productName = product.name
                )
                require(request.quantity <= stock.quantityOnHand) {
                    "Cannot return ${request.quantity} of '${original.productNameSnapshot}' to supplier; current stock is only ${stock.quantityOnHand}"
                }
            }
            PurchaseReturnLine(
                id = "pur_ret_line_${UUID.randomUUID()}",
                purchaseReturnId = "pending",
                purchaseLineId = original.id,
                productId = original.productId,
                productNameSnapshot = original.productNameSnapshot,
                quantity = request.quantity,
                unitCost = original.unitCost,
                subtotal = request.quantity * original.unitCost
            )
        }

        val totalAmount = constructedLines.sumOf { it.subtotal }
        require(totalAmount > 0.0) { "Purchase return amount must be greater than zero" }
        require(totalAmount <= purchase.totalAmount + 0.001) { "Purchase return exceeds original purchase amount" }

        val dateToUse = returnDate?.takeIf { it.isNotBlank() }
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val retId = "pur_ret_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val supplier = supplierDao.getSupplierById(purchase.supplierId)
            ?: throw IllegalArgumentException("Supplier ${purchase.supplierId} not found")
        val pr = PurchaseReturn(
            id = retId,
            purchaseId = purchaseId,
            supplierId = purchase.supplierId,
            returnDate = dateToUse,
            amount = totalAmount,
            reason = reason.trim(),
            status = "ACTIVE"
        )
        val persistedLines = constructedLines.map { it.copy(purchaseReturnId = retId) }

        database.withTransaction {
            purchaseReturnDao.insertReturn(pr)
            purchaseReturnLineDao.insertLines(persistedLines)

            val stockMovements = persistedLines.mapNotNull { line ->
                if (!line.productId.isNullOrBlank()) {
                    PersistentStockMovement(
                        id = "sm_${line.id}",
                        productId = line.productId,
                        productNameSnapshot = line.productNameSnapshot,
                        transactionId = pr.id,
                        lineId = line.id,
                        date = pr.returnDate,
                        timestamp = pr.createdAt,
                        movementType = InventoryMovementType.PURCHASE_RETURN_OUT.name,
                        quantityIn = 0,
                        quantityOut = line.quantity,
                        unitCost = line.unitCost,
                        reference = purchase.invoiceNumber,
                        referenceType = "PURCHASE_RETURN",
                        referenceId = pr.id,
                        status = pr.status
                    )
                } else null
            }
            if (stockMovements.isNotEmpty()) stockMovementDao.insertStockMovements(stockMovements)

            val txEntity = pr.toTransactionItem(supplier.name, purchase.invoiceNumber).copy(
                customerId = null,
                customerName = supplier.name
            ).toEntity()
            transactionDao.insertTransaction(txEntity)
        }
        return pr
    }

    /** Compatibility overload retained only for exact, whole-unit amounts. */
    @Deprecated("Use the line-level recordPurchaseReturn overload")
    suspend fun recordPurchaseReturn(
        purchaseId: String,
        amount: Double,
        reason: String,
        returnDate: String? = null
    ): PurchaseReturn {
        val lines = purchaseLineDao.getLinesByPurchaseId(purchaseId)
        var remainingAmount = amount
        val requests = mutableListOf<PurchaseReturnLineRequest>()
        for (line in lines) {
            if (remainingAmount <= 0.005) break
            val maxQty = kotlin.math.floor((remainingAmount + 0.001) / line.unitCost).toInt()
            val qty = minOf(maxQty, line.quantity)
            if (qty > 0) {
                requests += PurchaseReturnLineRequest(line.id, qty)
                remainingAmount -= qty * line.unitCost
            }
        }
        require(kotlin.math.abs(remainingAmount) < 0.001) {
            "Amount-only purchase returns are no longer supported for amounts that cannot be represented by whole original units. Select exact returned products and quantities."
        }
        return recordPurchaseReturn(purchaseId, requests, reason, returnDate)
    }
}
