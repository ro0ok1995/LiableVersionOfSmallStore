package com.example.data.repository

import androidx.room.withTransaction
import com.example.accounting.InventoryMovementType
import com.example.data.db.PurchaseDao
import com.example.data.db.PurchaseLineDao
import com.example.data.db.PurchaseReturn
import com.example.data.db.PurchaseReturnDao
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.StockMovementDao
import com.example.data.db.StockMovementEntity as PersistentStockMovement
import com.example.data.db.SupplierDao
import com.example.data.db.TransactionDao
import com.example.data.db.toEntity
import com.example.data.db.toTransactionItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PurchaseReturnRepository(
    private val database: SmallStoreDatabase,
    private val purchaseReturnDao: PurchaseReturnDao = database.purchaseReturnDao(),
    private val purchaseDao: PurchaseDao = database.purchaseDao(),
    private val purchaseLineDao: PurchaseLineDao = database.purchaseLineDao(),
    private val supplierDao: SupplierDao = database.supplierDao(),
    private val stockMovementDao: StockMovementDao = database.stockMovementDao(),
    private val transactionDao: TransactionDao = database.transactionDao()
) {
    suspend fun getReturnById(id: String): PurchaseReturn? = purchaseReturnDao.getReturnById(id)

    suspend fun getReturnsByPurchaseIdSync(purchaseId: String): List<PurchaseReturn> =
        purchaseReturnDao.getReturnsByPurchaseIdSync(purchaseId)

    suspend fun getReturnsBySupplierIdSync(supplierId: String): List<PurchaseReturn> =
        purchaseReturnDao.getReturnsBySupplierIdSync(supplierId)

    suspend fun getAllReturnsSync(): List<PurchaseReturn> = purchaseReturnDao.getAllReturnsSync()

    suspend fun recordPurchaseReturn(
        purchaseId: String,
        amount: Double,
        reason: String,
        returnDate: String? = null
    ): PurchaseReturn {
        val purchase = purchaseDao.getPurchaseById(purchaseId)
            ?: throw IllegalArgumentException("Purchase $purchaseId not found")

        require(purchase.status == "ACTIVE") { "Cannot return from non-active purchase (status: ${purchase.status})" }
        require(amount > 0.0) { "Return amount ($amount) must be greater than zero" }
        require(reason.isNotBlank()) { "Return reason cannot be blank" }

        val supplier = supplierDao.getSupplierById(purchase.supplierId)
            ?: throw IllegalArgumentException("Supplier ${purchase.supplierId} not found")

        val previousReturns = purchaseReturnDao.getReturnsByPurchaseIdSync(purchaseId)
        val activePreviousReturns = previousReturns.filter { it.status == "ACTIVE" }.sumOf { it.amount }
        val remainingReturnable = (purchase.totalAmount - activePreviousReturns).coerceAtLeast(0.0)
        require(amount <= remainingReturnable + 0.0001) {
            "Return amount ($amount) exceeds remaining returnable amount ($remainingReturnable) for purchase $purchaseId"
        }

        val dateToUse = returnDate?.takeIf { it.isNotBlank() }
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val retId = "pur_ret_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

        val pr = PurchaseReturn(
            id = retId,
            purchaseId = purchaseId,
            supplierId = purchase.supplierId,
            returnDate = dateToUse,
            amount = amount,
            reason = reason.trim(),
            status = "ACTIVE"
        )

        database.withTransaction {
            purchaseReturnDao.insertReturn(pr)

            // Phase 11: Persist PURCHASE_RETURN_OUT StockMovement for returned items
            val purchaseLines = purchaseLineDao.getLinesByPurchaseId(purchaseId)
            val totalPurchaseAmt = purchase.totalAmount
            if (purchaseLines.isNotEmpty() && totalPurchaseAmt > 0.0) {
                val returnRatio = (amount / totalPurchaseAmt).coerceAtMost(1.0)
                val stockMovements = purchaseLines.mapNotNull { line ->
                    if (!line.productId.isNullOrBlank()) {
                        val returnedQty = kotlin.math.round((line.quantity * returnRatio)).toInt().coerceAtLeast(0)
                        if (returnedQty > 0) {
                            PersistentStockMovement(
                                id = "sm_${pr.id}_${line.id}",
                                productId = line.productId,
                                productNameSnapshot = line.productNameSnapshot,
                                transactionId = pr.id,
                                lineId = line.id,
                                date = pr.returnDate,
                                timestamp = pr.createdAt,
                                movementType = InventoryMovementType.PURCHASE_RETURN_OUT.name,
                                quantityIn = 0,
                                quantityOut = returnedQty,
                                unitCost = line.unitCost,
                                reference = pr.reason,
                                referenceType = "PURCHASE_RETURN",
                                referenceId = pr.id,
                                status = pr.status
                            )
                        } else null
                    } else null
                }
                if (stockMovements.isNotEmpty()) {
                    stockMovementDao.insertStockMovements(stockMovements)
                }
            }

            val txEntity = pr.toTransactionItem(supplier.name, purchase.invoiceNumber).copy(
                customerId = null,
                customerName = supplier.name
            ).toEntity()
            transactionDao.insertTransaction(txEntity)
        }

        return pr
    }
}
