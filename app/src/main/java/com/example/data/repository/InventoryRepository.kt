package com.example.data.repository

import androidx.room.withTransaction
import com.example.accounting.CentralAccountingEngine
import com.example.accounting.InventoryMovementEntry
import com.example.accounting.InventoryMovementType
import com.example.accounting.ProductStockSummary
import com.example.accounting.StockMovement
import com.example.data.db.Adjustment
import com.example.data.db.AdjustmentDao
import com.example.data.db.ProductDao
import com.example.data.db.PurchaseDao
import com.example.data.db.PurchaseLineDao
import com.example.data.db.PurchaseReturnDao
import com.example.data.db.SaleDao
import com.example.data.db.SaleReturnDao
import com.example.data.db.SaleReturnLineDao
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.StockMovementDao
import com.example.data.db.StockMovementEntity as PersistentStockMovement
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class InventoryRepository(
    private val database: SmallStoreDatabase,
    private val stockMovementDao: StockMovementDao = database.stockMovementDao(),
    private val productDao: ProductDao = database.productDao(),
    private val purchaseDao: PurchaseDao = database.purchaseDao(),
    private val purchaseLineDao: PurchaseLineDao = database.purchaseLineDao(),
    private val saleDao: SaleDao = database.saleDao(),
    private val saleReturnDao: SaleReturnDao = database.saleReturnDao(),
    private val saleReturnLineDao: SaleReturnLineDao = database.saleReturnLineDao(),
    private val purchaseReturnDao: PurchaseReturnDao = database.purchaseReturnDao(),
    private val adjustmentDao: AdjustmentDao = database.adjustmentDao()
) {
    val allPersistentStockMovements: Flow<List<PersistentStockMovement>> = stockMovementDao.getAllMovements()

    suspend fun getProductStock(productId: String): ProductStockSummary {
        val product = productDao.getProductById(productId)
        val movements = stockMovementDao.getMovementsByProductIdSync(productId)

        return CentralAccountingEngine.calculateProductStockFromMovements(
            productId = productId,
            movements = movements,
            fallbackUnitCost = product?.costPrice ?: 0.0,
            productName = product?.name ?: ""
        )
    }

    suspend fun getInventoryStatement(productId: String): List<InventoryMovementEntry> {
        val purchases = purchaseDao.getAllPurchasesSync()
        val purchaseLines = purchaseLineDao.getLinesByProductId(productId)
        val sales = saleDao.getAllSalesSync()
        val saleLines = saleDao.getSaleLinesByProductId(productId)
        val saleReturns = saleReturnDao.getAllReturnsSync()
        val saleReturnLines = saleReturnLineDao.getLinesByProductId(productId)
        val purchaseReturns = purchaseReturnDao.getAllReturnsSync()
        val adjustments = adjustmentDao.getAdjustmentsByEntitySync("PRODUCT", productId)

        return CentralAccountingEngine.buildInventoryLedger(
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

    suspend fun getStockMovements(productId: String): List<StockMovement> {
        return getInventoryStatement(productId)
    }

    // Phase 11A: Persistent Stock Movement Operations
    suspend fun insertPersistentStockMovement(movement: PersistentStockMovement): Long =
        stockMovementDao.insertMovement(movement)

    suspend fun insertPersistentStockMovements(movements: List<PersistentStockMovement>) =
        stockMovementDao.insertMovements(movements)

    suspend fun getPersistentStockMovements(productId: String): List<PersistentStockMovement> =
        stockMovementDao.getMovementsByProductIdSync(productId)

    fun observePersistentStockMovements(productId: String): Flow<List<PersistentStockMovement>> =
        stockMovementDao.getMovementsByProductId(productId)

    suspend fun getPersistentStockMovementsByDateRange(startDate: String, endDate: String): List<PersistentStockMovement> =
        stockMovementDao.getMovementsByDateRangeSync(startDate, endDate)

    suspend fun getPersistentStockMovementCount(): Int =
        stockMovementDao.getMovementCount()

    suspend fun getAllProductsStock(): Map<String, ProductStockSummary> {
        val products = productDao.getAllProductsSync()
        val productIds = products.map { it.id }.toSet()
        val movements = stockMovementDao.getAllMovementsSync()

        val costMap = products.associate { it.id to it.costPrice }
        val nameMap = products.associate { it.id to it.name }

        return CentralAccountingEngine.calculateAllProductsStockFromMovements(
            productIds = productIds,
            movements = movements,
            productCostPrices = costMap,
            productNames = nameMap
        )
    }

    suspend fun getTotalInventoryValuation(): Double {
        val stockMap = getAllProductsStock()
        return CentralAccountingEngine.calculateTotalInventoryValuation(stockMap.values)
    }

    suspend fun recordInventoryAdjustment(
        productId: String,
        quantityDelta: Int,
        reason: String,
        date: String? = null
    ): Adjustment {
        require(productId.isNotBlank()) { "Product ID cannot be blank" }
        require(quantityDelta != 0) { "Adjustment quantity delta cannot be zero" }
        require(reason.isNotBlank()) { "Adjustment reason cannot be blank" }

        val product = productDao.getProductById(productId)
            ?: throw IllegalArgumentException("Product $productId not found")

        val dateToUse = date?.takeIf { it.isNotBlank() }
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val direction = if (quantityDelta > 0) "DEBIT" else "CREDIT"
        val amount = Math.abs(quantityDelta).toDouble()
        val adjId = "adj_inv_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

        val adj = Adjustment(
            id = adjId,
            entityType = "PRODUCT",
            entityId = productId,
            amount = amount,
            direction = direction,
            date = dateToUse,
            reason = reason.trim(),
            reference = product.name,
            status = "ACTIVE"
        )

        database.withTransaction {
            adjustmentDao.insertAdjustment(adj)

            val isIncrease = quantityDelta > 0
            val movement = PersistentStockMovement(
                id = "sm_${adj.id}",
                productId = product.id,
                productNameSnapshot = product.name,
                transactionId = adj.id,
                lineId = adj.id,
                date = dateToUse,
                timestamp = System.currentTimeMillis(),
                movementType = if (isIncrease) InventoryMovementType.ADJUSTMENT_IN.name else InventoryMovementType.ADJUSTMENT_OUT.name,
                quantityIn = if (isIncrease) quantityDelta else 0,
                quantityOut = if (!isIncrease) Math.abs(quantityDelta) else 0,
                unitCost = product.costPrice,
                reference = reason.trim(),
                referenceType = "ADJUSTMENT",
                referenceId = adj.id,
                status = adj.status
            )
            stockMovementDao.insertStockMovement(movement)
        }

        return adj
    }

    suspend fun recordInventoryDamage(
        productId: String,
        quantity: Int,
        reason: String = "بضاعة تالفة",
        date: String? = null
    ): Adjustment {
        require(quantity > 0) { "Damage quantity must be greater than zero" }
        return recordInventoryAdjustment(
            productId = productId,
            quantityDelta = -quantity,
            reason = reason,
            date = date
        )
    }
}
