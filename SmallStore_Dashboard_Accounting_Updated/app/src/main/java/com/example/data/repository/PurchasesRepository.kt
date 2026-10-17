package com.example.data.repository

import androidx.room.withTransaction
import com.example.accounting.InventoryMovementType
import com.example.data.db.FinancialAccountDao
import com.example.data.db.ProductDao
import com.example.data.db.Purchase
import com.example.data.db.PurchaseDao
import com.example.data.db.PurchaseLine
import com.example.data.db.PurchaseLineDao
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.StockMovementDao
import com.example.data.db.StockMovementEntity as PersistentStockMovement
import com.example.data.db.SupplierDao
import com.example.data.db.TransactionDao
import com.example.data.db.SupplierCatalogItemEntity
import com.example.data.db.toEntity
import com.example.data.db.toTransactionItem
import com.example.model.PurchaseLineRequest
import com.example.model.PurchaseResult
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PurchasesRepository(
    private val database: SmallStoreDatabase,
    private val purchaseDao: PurchaseDao = database.purchaseDao(),
    private val purchaseLineDao: PurchaseLineDao = database.purchaseLineDao(),
    private val supplierDao: SupplierDao = database.supplierDao(),
    private val productDao: ProductDao = database.productDao(),
    private val financialAccountDao: FinancialAccountDao = database.financialAccountDao(),
    private val stockMovementDao: StockMovementDao = database.stockMovementDao(),
    private val transactionDao: TransactionDao = database.transactionDao()
) {
    val allPurchases: Flow<List<Purchase>> = purchaseDao.getAllPurchases()

    suspend fun generateNextPurchaseInvoiceNumber(): String {
        val lastInvoice = purchaseDao.getLastPurchaseInvoiceNumber()
        if (lastInvoice.isNullOrBlank()) return "PUR-000001"
        val prefix = "PUR-"
        val num = if (lastInvoice.startsWith(prefix)) {
            lastInvoice.removePrefix(prefix).toIntOrNull() ?: 0
        } else {
            0
        }
        return String.format(Locale.US, "PUR-%06d", num + 1)
    }

    suspend fun getPurchaseById(id: String): Purchase? = purchaseDao.getPurchaseById(id)

    suspend fun getPurchaseLines(purchaseId: String): List<PurchaseLine> =
        purchaseLineDao.getLinesByPurchaseId(purchaseId)

    suspend fun getPurchasesForSupplier(supplierId: String): List<Purchase> =
        purchaseDao.getPurchasesBySupplierIdSync(supplierId)

    fun getPurchasesBySupplierId(supplierId: String): Flow<List<Purchase>> =
        purchaseDao.getPurchasesBySupplierId(supplierId)

    suspend fun getAllPurchasesSync(): List<Purchase> = purchaseDao.getAllPurchasesSync()

    suspend fun recordPurchase(
        supplierId: String,
        lines: List<PurchaseLineRequest>,
        purchaseDate: String? = null,
        paidAmount: Double = 0.0,
        paymentMethodId: String? = null,
        financialAccountId: String? = null,
        notes: String? = null,
        invoiceNumber: String? = null
    ): PurchaseResult {
        // Invariant 1: Valid supplier
        require(supplierId.isNotBlank()) { "Supplier ID cannot be blank" }
        val supplier = supplierDao.getSupplierById(supplierId)
            ?: throw IllegalArgumentException("Supplier with ID $supplierId not found")

        // Invariant 2: Non-empty lines and valid items
        require(lines.isNotEmpty()) { "Purchase must contain at least one line item" }
        for (line in lines) {
            require(line.quantity > 0) { "Quantity (${line.quantity}) must be greater than zero" }
            require(line.unitCost > 0.0) { "Unit cost (${line.unitCost}) must be greater than zero" }
            require(line.productNameSnapshot.isNotBlank()) { "Product name snapshot cannot be blank" }
            if (!line.productId.isNullOrBlank()) {
                val prod = productDao.getProductById(line.productId)
                require(prod != null) { "Product with ID ${line.productId} not found" }
            }
        }

        val totalAmount = com.example.accounting.AccountingDecimal.money(lines.sumOf { com.example.accounting.AccountingDecimal.multiplyToMoney(it.unitCost, it.quantity.toDouble()) })
        require(paidAmount.isFinite() && paidAmount >= 0.0) { "Paid amount ($paidAmount) must be finite and non-negative" }
        val postedPaidAmount = com.example.accounting.AccountingDecimal.money(paidAmount)
        require(postedPaidAmount <= totalAmount + 0.001) {
            "Paid amount ($paidAmount) cannot exceed total purchase amount ($totalAmount)"
        }

        val creditAmount = com.example.accounting.AccountingDecimal.money((totalAmount - postedPaidAmount).coerceAtLeast(0.0))
        val paymentStatus = when {
            creditAmount <= 0.0001 -> "PAID"
            paidAmount <= 0.0001 -> "UNPAID"
            else -> "PARTIAL"
        }

        val dateToUse = purchaseDate?.takeIf { it.isNotBlank() }
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val purchaseId = "pur_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val invNum = invoiceNumber?.takeIf { it.isNotBlank() }
            ?: generateNextPurchaseInvoiceNumber()

        val effectiveFinancialAccountId = if (paidAmount > 0.0) {
            val accId = financialAccountId ?: "acc_cash"
            val account = financialAccountDao.getAccountById(accId)
                ?: throw IllegalArgumentException("Financial account not found: $accId")
            require(account.isActive) { "Financial account is inactive: $accId" }
            accId
        } else null
        val effectivePaymentMethodId = if (paidAmount > 0.0) {
            val methodId = paymentMethodId ?: "pm_cash"
            val method = database.paymentMethodDao().getPaymentMethodById(methodId)
                ?: throw IllegalArgumentException("Payment method not found: $methodId")
            require(method.isActive) { "Payment method is inactive: $methodId" }
            methodId
        } else null

        val purchase = Purchase(
            id = purchaseId,
            invoiceNumber = invNum,
            supplierId = supplierId,
            purchaseDate = dateToUse,
            totalAmount = totalAmount,
            paidAmount = postedPaidAmount,
            creditAmount = creditAmount,
            paymentStatus = paymentStatus,
            paymentMethodId = effectivePaymentMethodId,
            financialAccountId = effectiveFinancialAccountId,
            notes = notes,
            status = "ACTIVE"
        )

        val purchaseLines = lines.map { req ->
            PurchaseLine(
                id = "pur_line_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                purchaseId = purchaseId,
                productId = req.productId,
                productNameSnapshot = req.productNameSnapshot,
                quantity = req.quantity,
                unitCost = req.unitCost,
                subtotal = com.example.accounting.AccountingDecimal.multiplyToMoney(req.unitCost, req.quantity.toDouble())
            )
        }

        // Atomicity requirement: All financial effects commit together or rollback together
        database.withTransaction {
            if (paidAmount > 0.0) {
                val accId = effectiveFinancialAccountId ?: error("financial account missing for paid purchase")
                financialAccountDao.getAccountById(accId)
                    ?: throw IllegalArgumentException("Financial account not found: $accId")
            }

            purchaseDao.insertPurchase(purchase)
            purchaseLineDao.insertLines(purchaseLines)

            // Update product cost price with Moving Weighted Average Cost (MWAC)
            for (line in purchaseLines) {
                if (!line.productId.isNullOrBlank()) {
                    val p = productDao.getProductById(line.productId)
                    if (p != null) {
                        val currentStock = stockMovementDao.getAvailableStockForProduct(line.productId).coerceAtLeast(0)
                        val newTotalQty = currentStock + line.quantity
                        val weightedCost = if (newTotalQty > 0) {
                            ((currentStock * p.costPrice) + (line.quantity * line.unitCost)) / newTotalQty
                        } else {
                            line.unitCost
                        }
                        productDao.updateProduct(
                            p.copy(costPrice = com.example.accounting.AccountingDecimal.money(weightedCost))
                        )
                    }
                }
            }

            // Phase 11: Persist StockMovement rows for each purchased line in the same transaction
            val stockMovements = purchaseLines.mapNotNull { line ->
                if (!line.productId.isNullOrBlank()) {
                    PersistentStockMovement(
                        id = "sm_${line.id}",
                        productId = line.productId,
                        productNameSnapshot = line.productNameSnapshot,
                        transactionId = purchase.id,
                        lineId = line.id,
                        date = purchase.purchaseDate,
                        timestamp = line.createdAt,
                        movementType = InventoryMovementType.PURCHASE_IN.name,
                        quantityIn = line.quantity,
                        quantityOut = 0,
                        unitCost = line.unitCost,
                        reference = purchase.invoiceNumber,
                        referenceType = "PURCHASE",
                        referenceId = purchase.id,
                        status = purchase.status
                    )
                } else null
            }
            if (stockMovements.isNotEmpty()) {
                stockMovementDao.insertStockMovements(stockMovements)
            }

            // Insert historical activity record (customerId null to prevent FK conflict with customers table)
            val txEntity = purchase.toTransactionItem(supplier.name).copy(
                customerId = null,
                customerName = supplier.name
            ).toEntity()
            transactionDao.insertTransaction(txEntity)
        }

        return PurchaseResult(
            purchase = purchase,
            lines = purchaseLines,
            supplierPayableIncrease = creditAmount,
            inventoryValueIncrease = totalAmount,
            financialAccountDeduction = paidAmount
        )
    }
}
