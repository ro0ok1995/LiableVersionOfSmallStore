package com.example.data.repository

import androidx.room.withTransaction
import com.example.accounting.InventoryMovementType
import com.example.data.db.CustomerDao
import com.example.data.db.Sale
import com.example.data.db.SaleDao
import com.example.data.db.SaleLine
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.StockMovementDao
import com.example.data.db.StockMovementEntity as PersistentStockMovement
import com.example.data.db.TransactionDao
import com.example.data.db.TransactionItemLineDao
import com.example.data.db.TransactionItemLineEntity
import com.example.data.db.toEntity
import com.example.data.db.toTransactionItem
import kotlinx.coroutines.flow.Flow
import java.util.Locale

class SalesRepository(
    private val database: SmallStoreDatabase,
    private val saleDao: SaleDao = database.saleDao(),
    private val customerDao: CustomerDao = database.customerDao(),
    private val transactionDao: TransactionDao = database.transactionDao(),
    private val transactionItemLineDao: TransactionItemLineDao = database.transactionItemLineDao(),
    private val stockMovementDao: StockMovementDao = database.stockMovementDao()
) {
    val allSales: Flow<List<Sale>> = saleDao.getAllSales()

    suspend fun getSaleById(id: String): Sale? = saleDao.getSaleById(id)

    suspend fun getSaleLines(saleId: String): List<SaleLine> = saleDao.getSaleLinesBySaleId(saleId)

    fun getSalesByCustomerId(customerId: String): Flow<List<Sale>> = saleDao.getSalesByCustomerId(customerId)

    suspend fun getSalesByCustomerIdSync(customerId: String): List<Sale> = saleDao.getSalesByCustomerIdSync(customerId)

    suspend fun getAllSalesSync(): List<Sale> = saleDao.getAllSalesSync()

    suspend fun getNextInvoiceNumber(): String {
        val lastInvoice = saleDao.getLastInvoiceNumber()
        return generateNextInvoiceNumber(lastInvoice)
    }

    /**
     * Records a [Sale] and all associated [SaleLine]s atomically.
     *
     * Invariants:
     * 1. Total amount must strictly equal paidAmount + creditAmount.
     * 2. Cost prices on SaleLines are frozen at time of sale.
     * 3. Either all records succeed or none are committed (withTransaction).
     * 4. Additive to existing data models without breaking legacy flows.
     */
    suspend fun createSale(
        sale: Sale,
        lines: List<SaleLine>,
        customerNameSnapshot: String? = null,
        notes: String? = null
    ): Sale = database.withTransaction {
        // Enforce accounting invariant at repository boundary
        require(Math.abs(sale.totalAmount - (sale.paidAmount + sale.creditAmount)) < 0.001) {
            "Accounting invariant violated: totalAmount (${sale.totalAmount}) must equal paidAmount (${sale.paidAmount}) + creditAmount (${sale.creditAmount})"
        }

        // 1. Insert Sale record into sales table
        saleDao.insertSale(sale)

        // 2. Insert all SaleLines into sale_lines table
        if (lines.isNotEmpty()) {
            saleDao.insertSaleLines(lines)
        }

        // 3. Resolve customer name snapshot for legacy & display consistency
        val resolvedSnapshot = customerNameSnapshot
            ?: if (!sale.customerId.isNullOrBlank()) {
                customerDao.getCustomerById(sale.customerId)?.customerName ?: ""
            } else {
                ""
            }

        // 4. Additive legacy transaction synchronization:
        // Record the sale in transactions table so legacy queries, notifications, and export continue seamlessly
        val saleTx = sale.toTransactionItem(resolvedSnapshot, notes).toEntity()
        transactionDao.insertTransaction(saleTx)

        // Also record transaction item lines in transaction_item_lines table for backward compatibility
        val legacyLines = lines.map { sl ->
            TransactionItemLineEntity(
                transactionId = sale.id,
                productId = sl.productId,
                productNameSnapshot = sl.productNameSnapshot,
                quantity = sl.quantity,
                unitPrice = sl.unitPrice,
                costPrice = sl.costPriceAtSale,
                subtotal = sl.subtotal
            )
        }
        if (legacyLines.isNotEmpty()) {
            transactionItemLineDao.insertLines(legacyLines)
        }

        // 5. Phase 11: Persist SALE_OUT StockMovement for each sold SaleLine
        val stockMovements = lines.mapNotNull { sl ->
            if (!sl.productId.isNullOrBlank()) {
                PersistentStockMovement(
                    id = "sm_${sl.id}",
                    productId = sl.productId,
                    productNameSnapshot = sl.productNameSnapshot,
                    transactionId = sale.id,
                    lineId = sl.id,
                    date = sale.transactionDate,
                    timestamp = sl.createdAt,
                    movementType = InventoryMovementType.SALE_OUT.name,
                    quantityIn = 0,
                    quantityOut = sl.quantity,
                    unitCost = sl.costPriceAtSale,
                    reference = sale.invoiceNumber,
                    referenceType = "SALE",
                    referenceId = sale.id,
                    status = sale.status
                )
            } else null
        }
        if (stockMovements.isNotEmpty()) {
            stockMovementDao.insertStockMovements(stockMovements)
        }

        sale
    }

    companion object {
        fun generateNextInvoiceNumber(lastInvoiceNumber: String?): String {
            if (lastInvoiceNumber.isNullOrBlank()) return "INV-000001"
            val prefix = "INV-"
            val num = if (lastInvoiceNumber.startsWith(prefix)) {
                lastInvoiceNumber.removePrefix(prefix).toIntOrNull() ?: 0
            } else {
                0
            }
            return String.format(Locale.US, "INV-%06d", num + 1)
        }
    }
}
