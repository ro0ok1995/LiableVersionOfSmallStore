package com.example.data.repository

import androidx.room.withTransaction
import com.example.accounting.InventoryMovementType
import com.example.accounting.CentralAccountingEngine
import com.example.data.db.CustomerDao
import com.example.data.db.FinancialAccountDao
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
    private val financialAccountDao: FinancialAccountDao = database.financialAccountDao(),
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
        // Enforce accounting and operation invariants at the repository boundary.
        require(lines.isNotEmpty()) { "Sale must contain at least one line item" }
        require(Math.abs(sale.totalAmount - (sale.paidAmount + sale.creditAmount)) < 0.001) {
            "Accounting invariant violated: totalAmount (${sale.totalAmount}) must equal paidAmount (${sale.paidAmount}) + creditAmount (${sale.creditAmount})"
        }
        require(sale.totalAmount > 0.0) { "Sale total must be greater than zero" }
        require(sale.paidAmount >= 0.0 && sale.creditAmount >= 0.0) { "Sale payment amounts cannot be negative" }
        require(sale.customerId != null || sale.creditAmount <= 0.001) {
            "A credit or mixed sale requires a customer; anonymous sales must be fully cash"
        }
        require(lines.map { it.id }.distinct().size == lines.size) { "Duplicate sale line IDs are not allowed" }
        require(lines.all { it.saleId == sale.id }) { "Every sale line must belong to the sale" }
        val calculatedTotal = lines.sumOf { it.subtotal }
        require(Math.abs(calculatedTotal - sale.totalAmount) < 0.001) {
            "Sale total (${sale.totalAmount}) must equal the sum of sale line subtotals ($calculatedTotal)"
        }
        lines.forEach { line ->
            require(line.quantity > 0) { "Sale quantity must be greater than zero" }
            require(line.unitPrice >= 0.0 && line.costPriceAtSale >= 0.0) { "Sale prices cannot be negative" }
            require(line.productNameSnapshot.isNotBlank()) { "Sale product snapshot cannot be blank" }
        }
        if (sale.paidAmount > 0.001) {
            val accountId = sale.financialAccountId ?: "acc_cash"
            val account = financialAccountDao.getAccountById(accountId)
                ?: throw IllegalArgumentException("Financial account not found: $accountId")
            require(account.isActive) { "Financial account is inactive: $accountId" }
        }
        val currentMovements = stockMovementDao.getAllMovementsSync()
        lines.filter { !it.productId.isNullOrBlank() }.groupBy { it.productId!! }.forEach { (productId, requestedLines) ->
            val product = database.productDao().getProductById(productId)
                ?: throw IllegalArgumentException("Product not found: $productId")
            val stock = CentralAccountingEngine.calculateProductStockFromMovements(
                productId = productId,
                movements = currentMovements,
                fallbackUnitCost = product.costPrice,
                productName = product.name
            )
            val requestedQty = requestedLines.sumOf { it.quantity }
            require(requestedQty <= stock.quantityOnHand) {
                "Insufficient stock for '${product.name}'. Available: ${stock.quantityOnHand}, requested: $requestedQty"
            }
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
