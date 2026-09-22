package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.accounting.CustomerBalanceSummary
import com.example.accounting.CustomerLedgerCalculator
import com.example.data.backup.BackupPayload
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.TransactionEntity
import com.example.data.db.TransactionItemLineEntity
import com.example.data.db.toEntity
import com.example.data.db.toModel
import com.example.model.CustomerAccount
import com.example.model.CustomerConflictItem
import com.example.model.NotificationItem
import com.example.model.ProductItem
import com.example.model.SampleData
import com.example.model.SettlementType
import com.example.model.StoreInfo
import com.example.model.TransactionItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Locale

class StoreRepository private constructor(
    private val database: SmallStoreDatabase
) {
    private val customerDao = database.customerDao()
    private val productDao = database.productDao()
    private val transactionDao = database.transactionDao()
    private val transactionItemLineDao = database.transactionItemLineDao()
    private val notificationDao = database.notificationDao()
    private val storeInfoDao = database.storeInfoDao()
    private val customerConflictDao = database.customerConflictDao()
    private val saleDao = database.saleDao()

    val allSales: Flow<List<Sale>> = saleDao.getAllSales()

    val customerConflicts: Flow<List<CustomerConflictItem>> = customerConflictDao.getAllConflicts().map { list ->
        list.map { it.toModel() }
    }

    val unresolvedCustomerConflicts: Flow<List<CustomerConflictItem>> = customerConflictDao.getUnresolvedConflicts().map { list ->
        list.map { it.toModel() }
    }

    val unresolvedConflictCount: Flow<Int> = customerConflictDao.getUnresolvedConflictCount()

    val transactions: Flow<List<TransactionItem>> = transactionDao.getActiveTransactions().map { list ->
        list.map { it.toModel() }
    }

    val archivedTransactions: Flow<List<TransactionItem>> = transactionDao.getArchivedTransactions().map { list ->
        list.map { it.toModel() }
    }

    val allTransactions: Flow<List<TransactionItem>> = transactionDao.getAllTransactions().map { list ->
        list.map { it.toModel() }
    }

    val products: Flow<List<ProductItem>> = productDao.getActiveProducts().map { list ->
        list.map { it.toModel() }
    }

    val archivedProducts: Flow<List<ProductItem>> = productDao.getArchivedProducts().map { list ->
        list.map { it.toModel() }
    }

    val allProducts: Flow<List<ProductItem>> = productDao.getAllProducts().map { list ->
        list.map { it.toModel() }
    }

    val customers: Flow<List<CustomerAccount>> = combine(
        customerDao.getActiveCustomers(),
        transactionDao.getAllTransactions()
    ) { custList, txList ->
        val domainTxList = txList.map { it.toModel() }
        custList.map { cEntity ->
            val model = cEntity.toModel()
            val summary = CustomerLedgerCalculator.calculateCustomerBalance(model.id, domainTxList)
            model.copy(
                balance = summary.balance,
                totalDebt = summary.totalCreditSales
            )
        }
    }

    val archivedCustomers: Flow<List<CustomerAccount>> = combine(
        customerDao.getArchivedCustomers(),
        transactionDao.getAllTransactions()
    ) { custList, txList ->
        val domainTxList = txList.map { it.toModel() }
        custList.map { cEntity ->
            val model = cEntity.toModel()
            val summary = CustomerLedgerCalculator.calculateCustomerBalance(model.id, domainTxList)
            model.copy(
                balance = summary.balance,
                totalDebt = summary.totalCreditSales
            )
        }
    }

    val allCustomers: Flow<List<CustomerAccount>> = combine(
        customerDao.getAllCustomers(),
        transactionDao.getAllTransactions()
    ) { custList, txList ->
        val domainTxList = txList.map { it.toModel() }
        custList.map { cEntity ->
            val model = cEntity.toModel()
            val summary = CustomerLedgerCalculator.calculateCustomerBalance(model.id, domainTxList)
            model.copy(
                balance = summary.balance,
                totalDebt = summary.totalCreditSales
            )
        }
    }

    val transactionLines: Flow<List<TransactionItemLineEntity>> = transactionItemLineDao.getAllLines()

    val notifications: Flow<List<NotificationItem>> = notificationDao.getAllNotifications().map { list ->
        list.map { it.toModel() }
    }

    val storeInfo: Flow<StoreInfo> = storeInfoDao.getStoreInfo().map { entity ->
        entity?.toModel() ?: StoreInfo()
    }

    suspend fun isStoreInfoSaved(): Boolean {
        val entity = storeInfoDao.getStoreInfoSync()
        return entity?.isSaved == true
    }

    suspend fun getStoreInfoSnapshot(): StoreInfo {
        return storeInfoDao.getStoreInfoSync()?.toModel() ?: StoreInfo()
    }

    suspend fun saveStoreInfo(info: StoreInfo, markAsSaved: Boolean = true) {
        storeInfoDao.insertOrUpdate(info.toEntity(isSaved = markAsSaved))
    }

    suspend fun seedIfEmpty() {
        if (customerDao.getCustomerCount() == 0) {
            database.withTransaction {
                customerDao.insertCustomers(SampleData.sampleCustomers.map { it.toEntity() })
                productDao.insertProducts(SampleData.sampleProducts.map { it.toEntity() })
                transactionDao.insertTransactions(SampleData.sampleTransactions.map { it.toEntity() })
                notificationDao.insertNotifications(SampleData.sampleNotifications.map { it.toEntity() })
                if (storeInfoDao.getStoreInfoSync() == null) {
                    storeInfoDao.insertOrUpdate(StoreInfo().toEntity(isSaved = false))
                }
            }
        }
    }

    suspend fun resetDatabaseToSampleData() {
        database.withTransaction {
            transactionItemLineDao.deleteAllLines()
            transactionDao.deleteAllTransactions()
            customerDao.deleteAllCustomers()
            productDao.deleteAllProducts()
            notificationDao.deleteAllNotifications()

            customerDao.insertCustomers(SampleData.sampleCustomers.map { it.toEntity() })
            productDao.insertProducts(SampleData.sampleProducts.map { it.toEntity() })
            transactionDao.insertTransactions(SampleData.sampleTransactions.map { it.toEntity() })
            notificationDao.insertNotifications(SampleData.sampleNotifications.map { it.toEntity() })
        }
    }

    suspend fun addCustomer(customer: CustomerAccount) {
        customerDao.insertCustomer(customer.toEntity())
    }

    suspend fun updateCustomer(customer: CustomerAccount) {
        customerDao.updateCustomer(customer.toEntity())
    }

    suspend fun archiveCustomer(id: String, archivedDate: String) {
        customerDao.archiveCustomer(id, archivedDate)
    }

    suspend fun restoreCustomer(id: String) {
        customerDao.unarchiveCustomer(id)
    }

    suspend fun deleteCustomerPermanently(id: String) {
        customerDao.deleteCustomerById(id)
    }

    suspend fun addProduct(product: ProductItem) {
        productDao.insertProduct(product.toEntity())
    }

    suspend fun updateProduct(product: ProductItem) {
        productDao.updateProduct(product.toEntity())
    }

    suspend fun deleteProduct(product: ProductItem) {
        productDao.deleteProduct(product.toEntity())
    }

    suspend fun archiveProduct(id: String, archivedDate: String) {
        productDao.archiveProduct(id, archivedDate)
    }

    suspend fun restoreProduct(id: String) {
        productDao.unarchiveProduct(id)
    }

    suspend fun deleteProductPermanently(id: String) {
        productDao.deleteProductById(id)
    }

    suspend fun addTransaction(
        transaction: TransactionItem,
        lines: List<TransactionItemLineEntity> = emptyList()
    ) {
        database.withTransaction {
            transactionDao.insertTransaction(transaction.toEntity())
            if (lines.isNotEmpty()) {
                transactionItemLineDao.insertLines(lines)
            }
        }
    }

    suspend fun archiveTransaction(id: String, archivedDate: String) {
        transactionDao.archiveTransaction(id, archivedDate)
    }

    suspend fun restoreTransaction(id: String) {
        transactionDao.unarchiveTransaction(id)
    }

    /**
     * Phase 2 Customer Receivable:
     * Reproduces customer balance and receivable summary directly from persisted financial
     * operations through the Customer Ledger.
     */
    suspend fun getCustomerBalance(customerId: String): CustomerBalanceSummary {
        val txEntities = transactionDao.getTransactionsByCustomerIdSync(customerId)
        val domainTransactions = txEntities.map { it.toModel() }
        return CustomerLedgerCalculator.calculateCustomerBalance(customerId, domainTransactions)
    }

    fun getCustomerBalanceFlow(customerId: String): Flow<CustomerBalanceSummary> {
        return transactionDao.getTransactionsByCustomerId(customerId).map { txEntities ->
            val domainTransactions = txEntities.map { it.toModel() }
            CustomerLedgerCalculator.calculateCustomerBalance(customerId, domainTransactions)
        }
    }

    /**
     * Phase 3 Sales Engine:
     * Creates a formal [Sale] record, all associated [SaleLine]s, and (if paidAmount > 0)
     * a linked payment record within a single atomic Room database transaction (withTransaction).
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

        sale
    }

    suspend fun getSaleById(id: String): Sale? = saleDao.getSaleById(id)

    suspend fun getSaleLines(saleId: String): List<SaleLine> = saleDao.getSaleLinesBySaleId(saleId)

    fun getSalesByCustomerId(customerId: String): Flow<List<Sale>> = saleDao.getSalesByCustomerId(customerId)

    suspend fun getSalesByCustomerIdSync(customerId: String): List<Sale> = saleDao.getSalesByCustomerIdSync(customerId)

    suspend fun getAllSalesSync(): List<Sale> = saleDao.getAllSalesSync()

    suspend fun getNextInvoiceNumber(): String {
        val lastInvoice = saleDao.getLastInvoiceNumber()
        return generateNextInvoiceNumber(lastInvoice)
    }

    @Deprecated(
        message = "Financial records cannot be physically deleted under the Accounting Golden Rule.",
        level = DeprecationLevel.WARNING
    )
    suspend fun deleteTransactionPermanently(id: String): Boolean {
        // ACCOUNTING GOLDEN RULE:
        // FINANCIAL RECORDS ARE NEVER EDITED, ARCHIVED, OR DELETED TO CANCEL THEIR ACCOUNTING EFFECT.
        // Physical deletion of financial transactions and their line items is strictly prohibited.
        // Historical transaction records must remain permanently preserved in the ledger.
        android.util.Log.w(
            "StoreRepository",
            "Physical deletion of financial transaction $id was refused: financial records are immutable."
        )
        return false
    }

    suspend fun getConflictById(conflictId: String): CustomerConflictItem? {
        return customerConflictDao.getConflictById(conflictId)?.toModel()
    }

    suspend fun getConflictByTransactionId(transactionId: String): CustomerConflictItem? {
        return customerConflictDao.getConflictByTransactionId(transactionId)?.toModel()
    }

    suspend fun insertCustomerConflict(conflict: CustomerConflictItem) {
        customerConflictDao.insertConflict(conflict.toEntity())
    }

    /**
     * Resolves an accounting identity conflict explicitly and deterministically.
     *
     * Invariants:
     * 1. Explicit, user-driven customer selection (NEVER automated or guessed).
     * 2. Transaction customerId is updated to the persistent customer account.
     * 3. Financial data (amount, isCredit, activityType, date) is strictly preserved.
     * 4. The conflict record is NEVER deleted, preserving a permanent audit trail.
     */
    suspend fun resolveCustomerConflict(
        conflictId: String,
        resolvedCustomerId: String,
        resolvedAt: String = java.time.Instant.now().toString(),
        notes: String? = null
    ): Boolean {
        val customer = customerDao.getCustomerById(resolvedCustomerId) ?: return false
        val conflict = customerConflictDao.getConflictById(conflictId) ?: return false
        val transaction = transactionDao.getTransactionById(conflict.transactionId) ?: return false

        database.withTransaction {
            // 1. Link original transaction to the persistent customerId
            transactionDao.updateTransactionCustomerId(conflict.transactionId, resolvedCustomerId)

            // 2. Mark conflict resolved with audit trail (NEVER deleted)
            customerConflictDao.markResolved(
                conflictId = conflictId,
                resolvedCustomerId = resolvedCustomerId,
                resolvedAt = resolvedAt,
                notes = notes ?: "تم تعيين العميل يدوياً: ${customer.customerName} ($resolvedCustomerId)"
            )
        }
        return true
    }

    /**
     * Dismisses an identity conflict (e.g. marked as an anonymous walk-in transaction).
     * Invariants:
     * 1. Original transaction remains in database with all financial data intact.
     * 2. Transaction customerId remains NULL (never assigned to a fake customer).
     * 3. The conflict record is NEVER deleted, preserving a permanent audit trail.
     */
    suspend fun dismissCustomerConflict(
        conflictId: String,
        resolvedAt: String = java.time.Instant.now().toString(),
        notes: String? = null
    ): Boolean {
        val conflict = customerConflictDao.getConflictById(conflictId) ?: return false
        val transaction = transactionDao.getTransactionById(conflict.transactionId)

        database.withTransaction {
            // Ensure transaction customerId remains NULL
            if (transaction != null && transaction.customerId != null) {
                transactionDao.updateTransactionCustomerId(conflict.transactionId, null)
            }
            customerConflictDao.markDismissed(
                conflictId = conflictId,
                resolvedAt = resolvedAt,
                notes = notes ?: "تم الاستبعاد يدوياً كمعاملة عامة بدون عميل"
            )
        }
        return true
    }

    suspend fun addNotification(notification: NotificationItem) {
        notificationDao.insertNotification(notification.toEntity())
    }

    suspend fun markNotificationsAsRead() {
        notificationDao.markAllAsRead()
    }

    suspend fun getAllDataForBackup(): BackupPayload {
        val currentStoreInfo = getStoreInfoSnapshot()
        val customersList = mutableListOf<CustomerAccount>()
        val productsList = mutableListOf<ProductItem>()
        val transactionsList = mutableListOf<TransactionItem>()
        val linesList = mutableListOf<TransactionItemLineEntity>()
        val notificationsList = mutableListOf<NotificationItem>()

        database.withTransaction {
            customersList.addAll(customerDao.getAllCustomersSync().map { it.toModel() })
            productsList.addAll(productDao.getAllProductsSync().map { it.toModel() })
            transactionsList.addAll(transactionDao.getAllTransactionsSync().map { it.toModel() })
            linesList.addAll(transactionItemLineDao.getAllLinesSync())
            notificationsList.addAll(notificationDao.getAllNotificationsSync().map { it.toModel() })
        }

        return BackupPayload(
            version = 1,
            backupTimestamp = System.currentTimeMillis(),
            storeInfoAtBackupTime = currentStoreInfo,
            customers = customersList,
            products = productsList,
            transactions = transactionsList,
            transactionItemLines = linesList,
            notifications = notificationsList
        )
    }

    suspend fun restoreDataFromBackup(payload: BackupPayload, replaceStoreInfo: Boolean) {
        database.withTransaction {
            transactionItemLineDao.deleteAllLines()
            transactionDao.deleteAllTransactions()
            customerDao.deleteAllCustomers()
            productDao.deleteAllProducts()
            notificationDao.deleteAllNotifications()

            if (payload.customers.isNotEmpty()) {
                customerDao.insertCustomers(payload.customers.map { it.toEntity() })
            }
            if (payload.products.isNotEmpty()) {
                productDao.insertProducts(payload.products.map { it.toEntity() })
            }
            if (payload.transactions.isNotEmpty()) {
                transactionDao.insertTransactions(payload.transactions.map { it.toEntity() })
            }
            if (payload.transactionItemLines.isNotEmpty()) {
                transactionItemLineDao.insertLines(payload.transactionItemLines)
            }
            if (payload.notifications.isNotEmpty()) {
                notificationDao.insertNotifications(payload.notifications.map { it.toEntity() })
            }

            if (replaceStoreInfo) {
                storeInfoDao.insertOrUpdate(payload.storeInfoAtBackupTime.toEntity(isSaved = true))
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: StoreRepository? = null

        fun getInstance(context: Context): StoreRepository {
            return INSTANCE ?: synchronized(this) {
                val db = SmallStoreDatabase.getDatabase(context)
                val instance = StoreRepository(db)
                INSTANCE = instance
                instance
            }
        }

        fun createForTesting(database: SmallStoreDatabase): StoreRepository {
            return StoreRepository(database)
        }

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
