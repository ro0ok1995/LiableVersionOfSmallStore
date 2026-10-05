package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.accounting.CentralAccountingEngine
import com.example.accounting.CustomerBalanceSummary
import com.example.accounting.CustomerLedgerCalculator
import com.example.accounting.CustomerLedgerEntry
import com.example.data.backup.BackupPayload
import com.example.data.db.Adjustment
import com.example.data.db.CustomerPayment
import com.example.data.db.FinancialAccount
import com.example.data.db.OpeningBalance
import com.example.data.db.PaymentMethod
import com.example.data.db.Refund
import com.example.data.db.Reversal
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.SaleReturn
import com.example.data.db.SaleReturnLine
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.Supplier
import com.example.data.db.Purchase
import com.example.data.db.PurchaseLine
import com.example.data.db.SupplierPayment
import com.example.data.db.PurchaseReturn
import com.example.data.db.ExpenseCategory
import com.example.data.db.Expense
import com.example.data.db.StockMovementEntity as PersistentStockMovement
import com.example.data.db.TransactionEntity
import com.example.data.db.TransactionItemLineEntity
import com.example.data.db.toEntity
import com.example.data.db.toModel
import com.example.data.db.toTransactionItem
import com.example.accounting.SupplierBalanceSummary
import com.example.accounting.SupplierLedgerEntry
import com.example.accounting.InventoryMovementEntry
import com.example.accounting.InventoryMovementType
import com.example.accounting.ProductStockSummary
import com.example.accounting.StockMovement
import com.example.model.CustomerAccount
import com.example.model.CustomerConflictItem
import com.example.model.NotificationItem
import com.example.model.OperationStatus
import com.example.model.ProductItem
import com.example.model.PurchaseLineRequest
import com.example.model.PurchaseResult
import com.example.model.RefundRequest
import com.example.model.SaleReturnLineRequest
import com.example.model.SaleReturnResult
import com.example.model.SampleData
import com.example.model.StoreInfo
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.model.typedTransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class StoreRepository private constructor(
    private val database: SmallStoreDatabase
) {
    private val customerDao = database.customerDao()
    val customerRepository = CustomerRepository(customerDao)
    private val productDao = database.productDao()
    val productRepository = ProductRepository(productDao)
    private val transactionDao = database.transactionDao()
    private val transactionItemLineDao = database.transactionItemLineDao()
    private val notificationDao = database.notificationDao()
    val notificationRepository = NotificationRepository(notificationDao)
    private val storeInfoDao = database.storeInfoDao()
    val storeInfoRepository = StoreInfoRepository(storeInfoDao)
    private val customerConflictDao = database.customerConflictDao()
    val customerConflictRepository = CustomerConflictRepository(database)
    val storeBackupRepository = StoreBackupRepository(database)
    private val saleDao = database.saleDao()
    val salesRepository = SalesRepository(database)
    private val financialAccountDao = database.financialAccountDao()
    val financialAccountRepository = FinancialAccountRepository(financialAccountDao)
    private val paymentMethodDao = database.paymentMethodDao()
    val paymentMethodRepository = PaymentMethodRepository(paymentMethodDao)
    private val customerPaymentDao = database.customerPaymentDao()
    val customerPaymentRepository = CustomerPaymentRepository(database)
    private val openingBalanceDao = database.openingBalanceDao()
    private val adjustmentDao = database.adjustmentDao()
    val openingBalanceAdjustmentRepository = OpeningBalanceAdjustmentRepository(database)
    val adjustmentRepository = AdjustmentRepository(database)
    private val reversalDao = database.reversalDao()
    val reversalRepository = ReversalRepository(reversalDao)
    private val saleReturnDao = database.saleReturnDao()
    private val saleReturnLineDao = database.saleReturnLineDao()
    private val refundDao = database.refundDao()
    val saleReturnRepository = SaleReturnRepository(database)
    val refundRepository = RefundRepository(database, refundDao, saleReturnDao, saleDao, customerDao, transactionDao)
    private val supplierDao = database.supplierDao()
    val supplierRepository = SupplierRepository(supplierDao)
    private val purchaseDao = database.purchaseDao()
    private val purchaseLineDao = database.purchaseLineDao()
    val purchasesRepository = PurchasesRepository(database)
    private val supplierPaymentDao = database.supplierPaymentDao()
    val supplierPaymentRepository = SupplierPaymentRepository(database)
    private val purchaseReturnDao = database.purchaseReturnDao()
    val purchaseReturnRepository = PurchaseReturnRepository(database)
    private val expenseCategoryDao = database.expenseCategoryDao()
    private val expenseDao = database.expenseDao()
    val expenseRepository = ExpenseRepository(database)
    private val stockMovementDao = database.stockMovementDao()
    val inventoryRepository = InventoryRepository(database)

    val allFinancialAccounts: Flow<List<FinancialAccount>> = financialAccountRepository.allFinancialAccounts
    val allPaymentMethods: Flow<List<PaymentMethod>> = paymentMethodRepository.allPaymentMethods
    val allCustomerPayments: Flow<List<CustomerPayment>> = customerPaymentRepository.allCustomerPayments

    val allSales: Flow<List<Sale>> = salesRepository.allSales
    val allSaleReturns: Flow<List<SaleReturn>> = saleReturnRepository.allSaleReturns
    val allRefunds: Flow<List<Refund>> = refundRepository.allRefunds
    val allSuppliers: Flow<List<Supplier>> = supplierRepository.allSuppliers
    val activeSuppliers: Flow<List<Supplier>> = supplierRepository.activeSuppliers
    val allPurchases: Flow<List<Purchase>> = purchasesRepository.allPurchases
    val allSupplierPayments: Flow<List<SupplierPayment>> = supplierPaymentRepository.allSupplierPayments
    val allExpenseCategories: Flow<List<ExpenseCategory>> = expenseRepository.allExpenseCategories
    val allExpenses: Flow<List<Expense>> = expenseRepository.allExpenses
    val allPersistentStockMovements: Flow<List<PersistentStockMovement>> = inventoryRepository.allPersistentStockMovements

    val customerConflicts: Flow<List<CustomerConflictItem>> = customerConflictRepository.customerConflicts
    val unresolvedCustomerConflicts: Flow<List<CustomerConflictItem>> = customerConflictRepository.unresolvedCustomerConflicts
    val unresolvedConflictCount: Flow<Int> = customerConflictRepository.unresolvedConflictCount

    val transactions: Flow<List<TransactionItem>> = transactionDao.getActiveTransactions().map { list ->
        list.map { it.toModel() }
    }

    val archivedTransactions: Flow<List<TransactionItem>> = transactionDao.getArchivedTransactions().map { list ->
        list.map { it.toModel() }
    }

    val allTransactions: Flow<List<TransactionItem>> = transactionDao.getAllTransactions().map { list ->
        list.map { it.toModel() }
    }

    val products: Flow<List<ProductItem>> = productRepository.products
    val archivedProducts: Flow<List<ProductItem>> = productRepository.archivedProducts
    val allProducts: Flow<List<ProductItem>> = productRepository.allProducts

    val customers: Flow<List<CustomerAccount>> = combine(
        customerDao.getActiveCustomers(),
        transactionDao.getAllTransactions()
    ) { custList, txList ->
        val domainTxList = txList.map { it.toModel() }
        custList.map { cEntity ->
            val model = cEntity.toModel()
            val summary = CentralAccountingEngine.calculateCustomerBalance(model.id, domainTxList)
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
            val summary = CentralAccountingEngine.calculateCustomerBalance(model.id, domainTxList)
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
            val summary = CentralAccountingEngine.calculateCustomerBalance(model.id, domainTxList)
            model.copy(
                balance = summary.balance,
                totalDebt = summary.totalCreditSales
            )
        }
    }

    val transactionLines: Flow<List<TransactionItemLineEntity>> = transactionItemLineDao.getAllLines()

    val notifications: Flow<List<NotificationItem>> = notificationRepository.notifications

    val storeInfo: Flow<StoreInfo> = storeInfoRepository.storeInfo

    suspend fun isStoreInfoSaved(): Boolean = storeInfoRepository.isStoreInfoSaved()

    suspend fun getStoreInfoSnapshot(): StoreInfo = storeInfoRepository.getStoreInfoSnapshot()

    suspend fun saveStoreInfo(info: StoreInfo, markAsSaved: Boolean = true) {
        storeInfoRepository.saveStoreInfo(info, markAsSaved)
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
                if (financialAccountDao.getAccountCount() == 0) {
                    financialAccountDao.insertAccount(
                        FinancialAccount(
                            id = "acc_cash",
                            name = "Cash",
                            type = "CASH",
                            isActive = true
                        )
                    )
                    paymentMethodDao.insertPaymentMethod(
                        PaymentMethod(
                            id = "pm_cash",
                            name = "Cash",
                            type = "CASH",
                            isActive = true
                        )
                    )
                }
            }
        } else if (financialAccountDao.getAccountCount() == 0) {
            database.withTransaction {
                financialAccountDao.insertAccount(
                    FinancialAccount(
                        id = "acc_cash",
                        name = "Cash",
                        type = "CASH",
                        isActive = true
                    )
                )
                paymentMethodDao.insertPaymentMethod(
                    PaymentMethod(
                        id = "pm_cash",
                        name = "Cash",
                        type = "CASH",
                        isActive = true
                    )
                )
            }
        }
    }

    suspend fun resetDatabaseToSampleData() {
        database.withTransaction {
            openingBalanceDao.deleteAllOpeningBalances()
            customerPaymentDao.deleteAllPayments()
            paymentMethodDao.deleteAllPaymentMethods()
            financialAccountDao.deleteAllAccounts()
            saleDao.deleteAllSaleLines()
            saleDao.deleteAllSales()
            transactionItemLineDao.deleteAllLines()
            transactionDao.deleteAllTransactions()
            customerDao.deleteAllCustomers()
            productDao.deleteAllProducts()
            notificationDao.deleteAllNotifications()

            customerDao.insertCustomers(SampleData.sampleCustomers.map { it.toEntity() })
            productDao.insertProducts(SampleData.sampleProducts.map { it.toEntity() })
            transactionDao.insertTransactions(SampleData.sampleTransactions.map { it.toEntity() })
            notificationDao.insertNotifications(SampleData.sampleNotifications.map { it.toEntity() })

            financialAccountDao.insertAccount(
                FinancialAccount(
                    id = "acc_cash",
                    name = "Cash",
                    type = "CASH",
                    isActive = true
                )
            )
            paymentMethodDao.insertPaymentMethod(
                PaymentMethod(
                    id = "pm_cash",
                    name = "Cash",
                    type = "CASH",
                    isActive = true
                )
            )
        }
    }

    suspend fun addCustomer(customer: CustomerAccount) {
        customerRepository.addCustomer(customer)
    }

    suspend fun updateCustomer(customer: CustomerAccount) {
        customerRepository.updateCustomer(customer)
    }

    suspend fun archiveCustomer(id: String, archivedDate: String) {
        customerRepository.archiveCustomer(id, archivedDate)
    }

    suspend fun restoreCustomer(id: String) {
        customerRepository.restoreCustomer(id)
    }

    suspend fun deleteCustomerPermanently(id: String) {
        customerRepository.deleteCustomerPermanently(id)
    }

    suspend fun getCustomerById(id: String): CustomerAccount? =
        customerRepository.getCustomerById(id)

    suspend fun addProduct(product: ProductItem) {
        productRepository.addProduct(product)
    }

    suspend fun updateProduct(product: ProductItem) {
        productRepository.updateProduct(product)
    }

    suspend fun deleteProduct(product: ProductItem) {
        productRepository.deleteProduct(product)
    }

    suspend fun archiveProduct(id: String, archivedDate: String) {
        productRepository.archiveProduct(id, archivedDate)
    }

    suspend fun restoreProduct(id: String) {
        productRepository.restoreProduct(id)
    }

    suspend fun deleteProductPermanently(id: String) {
        productRepository.deleteProductPermanently(id)
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
        val sales = saleDao.getSalesByCustomerIdSync(customerId)
        val payments = customerPaymentDao.getPaymentsByCustomerIdSync(customerId)
        val openingBalances = openingBalanceDao.getOpeningBalancesByEntitySync("CUSTOMER", customerId)
        val adjustments = adjustmentDao.getAdjustmentsByEntitySync("CUSTOMER", customerId)
        val saleReturns = saleReturnDao.getReturnsByCustomerIdSync(customerId)
        val refunds = refundDao.getRefundsByCustomerIdSync(customerId)
        if (sales.isNotEmpty() || payments.isNotEmpty() || openingBalances.isNotEmpty() || adjustments.isNotEmpty() || saleReturns.isNotEmpty() || refunds.isNotEmpty()) {
            return CentralAccountingEngine.calculateCustomerBalance(customerId, sales, payments, openingBalances, adjustments, saleReturns, refunds)
        }
        val txEntities = transactionDao.getTransactionsByCustomerIdSync(customerId)
        val domainTransactions = txEntities.map { it.toModel() }
        return CentralAccountingEngine.calculateCustomerBalance(customerId, domainTransactions)
    }

    fun getCustomerBalanceFlow(customerId: String): Flow<CustomerBalanceSummary> {
        val salesAndPaymentsFlow = combine(
            saleDao.getSalesByCustomerId(customerId),
            customerPaymentDao.getPaymentsByCustomerId(customerId),
            openingBalanceDao.getOpeningBalancesByEntity("CUSTOMER", customerId)
        ) { s, p, o -> Triple(s, p, o) }

        val adjustmentsAndReturnsFlow = combine(
            adjustmentDao.getAdjustmentsByEntity("CUSTOMER", customerId),
            saleReturnDao.getReturnsByCustomerId(customerId),
            refundDao.getRefundsByCustomerId(customerId)
        ) { a, r, rf -> Triple(a, r, rf) }

        return combine(
            salesAndPaymentsFlow,
            adjustmentsAndReturnsFlow,
            transactionDao.getTransactionsByCustomerId(customerId)
        ) { (sales, payments, openingBalances), (adjustments, saleReturns, refunds), txEntities ->
            if (sales.isNotEmpty() || payments.isNotEmpty() || openingBalances.isNotEmpty() || adjustments.isNotEmpty() || saleReturns.isNotEmpty() || refunds.isNotEmpty()) {
                CentralAccountingEngine.calculateCustomerBalance(customerId, sales, payments, openingBalances, adjustments, saleReturns, refunds)
            } else {
                val domainTransactions = txEntities.map { it.toModel() }
                CentralAccountingEngine.calculateCustomerBalance(customerId, domainTransactions)
            }
        }
    }

    suspend fun getCustomerStatement(customerId: String): List<CustomerLedgerEntry> {
        val sales = saleDao.getSalesByCustomerIdSync(customerId)
        val payments = customerPaymentDao.getPaymentsByCustomerIdSync(customerId)
        val openingBalances = openingBalanceDao.getOpeningBalancesByEntitySync("CUSTOMER", customerId)
        val adjustments = adjustmentDao.getAdjustmentsByEntitySync("CUSTOMER", customerId)
        val saleReturns = saleReturnDao.getReturnsByCustomerIdSync(customerId)
        val refunds = refundDao.getRefundsByCustomerIdSync(customerId)
        return CustomerLedgerCalculator.buildCustomerLedger(
            customerId = customerId,
            sales = sales,
            payments = payments,
            openingBalances = openingBalances,
            adjustments = adjustments,
            saleReturns = saleReturns,
            refunds = refunds
        )
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
    ): Sale = salesRepository.createSale(
        sale = sale,
        lines = lines,
        customerNameSnapshot = customerNameSnapshot,
        notes = notes
    )

    // =========================================================================
    // Phase 4: Financial Accounts & Payment Methods
    // =========================================================================
    suspend fun createFinancialAccount(account: FinancialAccount) {
        financialAccountRepository.createFinancialAccount(account)
    }


    suspend fun createPaymentMethod(paymentMethod: PaymentMethod) {
        paymentMethodRepository.createPaymentMethod(paymentMethod)
    }

    suspend fun getPaymentMethodById(id: String): PaymentMethod? {
        return paymentMethodRepository.getPaymentMethodById(id)
    }

    fun getActivePaymentMethods(): Flow<List<PaymentMethod>> {
        return paymentMethodRepository.getActivePaymentMethods()
    }

    suspend fun getAllPaymentMethodsSync(): List<PaymentMethod> {
        return paymentMethodRepository.getAllPaymentMethodsSync()
    }

    suspend fun updatePaymentMethod(paymentMethod: PaymentMethod) {
        paymentMethodRepository.updatePaymentMethod(paymentMethod)
    }

    // =========================================================================
    // Phase 4: Customer Payments Engine
    // =========================================================================

    suspend fun getAllCustomerPaymentsSync(): List<CustomerPayment> {
        return customerPaymentRepository.getAllCustomerPaymentsSync()
    }


    /**
     * Phase 4 Customer Payments:
     * Records a customer collection/payment atomically.
     *
     * Invariants:
     * 1. Runs inside db.withTransaction { }.
     * 2. Inserts CustomerPayment into customer_payments.
     * 3. Does NOT modify any Sale row.
     * 4. Does NOT create a transactions/TransactionEntity row of type SALE.
     */
    suspend fun recordCustomerPayment(
        customerId: String,
        amount: Double,
        paymentMethodId: String,
        financialAccountId: String? = null,
        reference: String? = null,
        notes: String? = null
    ): CustomerPayment = customerPaymentRepository.recordCustomerPayment(
        customerId = customerId,
        amount = amount,
        paymentMethodId = paymentMethodId,
        financialAccountId = financialAccountId,
        reference = reference,
        notes = notes
    )

    suspend fun recordCustomerPayment(payment: CustomerPayment): CustomerPayment =
        customerPaymentRepository.recordCustomerPayment(payment)

    // =========================================================================
    // Phase 5: Opening Balances
    // =========================================================================
    fun getOpeningBalancesByEntity(entityType: String, entityId: String): Flow<List<OpeningBalance>> {
        return openingBalanceAdjustmentRepository.getOpeningBalancesByEntity(entityType, entityId)
    }

    suspend fun getOpeningBalancesByEntitySync(entityType: String, entityId: String): List<OpeningBalance> {
        return openingBalanceAdjustmentRepository.getOpeningBalancesByEntitySync(entityType, entityId)
    }

    suspend fun recordOpeningBalance(
        entityType: String,
        entityId: String,
        amount: Double,
        direction: String,
        date: String,
        reason: String? = null,
        reference: String? = null
    ): OpeningBalance = openingBalanceAdjustmentRepository.recordOpeningBalance(
        entityType = entityType,
        entityId = entityId,
        amount = amount,
        direction = direction,
        date = date,
        reason = reason,
        reference = reference
    )

    // =========================================================================
    // Phase 6: Adjustments
    // =========================================================================
    fun getAdjustmentsByEntity(entityType: String, entityId: String): Flow<List<Adjustment>> {
        return adjustmentRepository.getAdjustmentsByEntity(entityType, entityId)
    }

    suspend fun getAdjustmentsByEntitySync(entityType: String, entityId: String): List<Adjustment> {
        return adjustmentRepository.getAdjustmentsByEntitySync(entityType, entityId)
    }

    suspend fun recordAdjustment(
        entityType: String,
        entityId: String,
        amount: Double,
        direction: String,
        date: String,
        reason: String,
        reference: String? = null
    ): Adjustment {
        return adjustmentRepository.recordAdjustment(
            entityType = entityType,
            entityId = entityId,
            amount = amount,
            direction = direction,
            date = date,
            reason = reason,
            reference = reference
        )
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

    suspend fun getConflictById(conflictId: String): CustomerConflictItem? =
        customerConflictRepository.getConflictById(conflictId)

    suspend fun getConflictByTransactionId(transactionId: String): CustomerConflictItem? =
        customerConflictRepository.getConflictByTransactionId(transactionId)


    suspend fun resolveCustomerConflict(
        conflictId: String,
        resolvedCustomerId: String,
        resolvedAt: String = java.time.Instant.now().toString(),
        notes: String? = null
    ): Boolean = customerConflictRepository.resolveCustomerConflict(
        conflictId,
        resolvedCustomerId,
        resolvedAt,
        notes
    )

    suspend fun dismissCustomerConflict(
        conflictId: String,
        resolvedAt: String = java.time.Instant.now().toString(),
        notes: String? = null
    ): Boolean = customerConflictRepository.dismissCustomerConflict(
        conflictId,
        resolvedAt,
        notes
    )

    suspend fun addNotification(notification: NotificationItem) {
        notificationRepository.addNotification(notification)
    }

    suspend fun markNotificationsAsRead() {
        notificationRepository.markNotificationsAsRead()
    }

    suspend fun getAllDataForBackup(): BackupPayload {
        return storeBackupRepository.getAllDataForBackup()
    }

    suspend fun restoreDataFromBackup(payload: BackupPayload, replaceStoreInfo: Boolean) {
        storeBackupRepository.restoreDataFromBackup(payload, replaceStoreInfo)
    }

    // =========================================================================
    // Phase 7: Reversal Engine
    // =========================================================================
    val allReversals: Flow<List<Reversal>> = reversalRepository.allReversals

    suspend fun getAllReversalsSync(): List<Reversal> = reversalRepository.getAllReversalsSync()

    suspend fun getReversalForTransaction(originalTransactionId: String): Reversal? {
        return reversalRepository.getReversalForTransaction(originalTransactionId)
    }


    /**
     * Phase 7 Atomic Transaction Reversal.
     *
     * Rules enforced:
     * - Rule A: Original transaction is preserved; only status is updated to REVERSED.
     * - Rule B: Accounting effect neutralized via existing ledger/reporting status filters.
     * - Rule C: Reversing the same transaction twice is strictly rejected.
     * - Rule D: OperationStatus.REVERSED is marked across relevant entities.
     * - Atomicity: Executed inside database.withTransaction.
     */
    suspend fun reverseTransaction(
        originalTransactionId: String,
        reason: String
    ): Reversal = database.withTransaction {
        require(originalTransactionId.isNotBlank()) { "originalTransactionId must not be blank" }
        require(reason.isNotBlank()) { "Reversal reason must not be blank" }

        // Rule C: One reversal only
        val existingReversal = reversalDao.getReversalByOriginalTransactionId(originalTransactionId)
        if (existingReversal != null && existingReversal.status == "ACTIVE") {
            throw IllegalStateException("Transaction $originalTransactionId has already been reversed on ${existingReversal.reversedAt}")
        }

        // Look up original transaction across supported sources
        val sale = saleDao.getSaleById(originalTransactionId)
        val payment = customerPaymentDao.getPaymentById(originalTransactionId)
        val adjustment = adjustmentDao.getAdjustmentById(originalTransactionId)
        val expense = expenseDao.getExpenseById(originalTransactionId)
        val legacyTx = transactionDao.getTransactionById(originalTransactionId)

        if (sale == null && payment == null && adjustment == null && expense == null && legacyTx == null) {
            throw IllegalArgumentException("Original transaction not found: $originalTransactionId")
        }

        var customerIdToTouch: String? = null
        var foundAnyEligible = false

        if (sale != null) {
            if (sale.status == "REVERSED") {
                throw IllegalStateException("Sale $originalTransactionId is already reversed")
            }
            val activeReturns = saleReturnDao.getReturnsBySaleIdSync(sale.id).filter { it.status != "REVERSED" }
            if (activeReturns.isNotEmpty()) {
                throw IllegalStateException("Cannot reverse sale ${sale.id} because it has active returns. Process returns instead.")
            }
            customerIdToTouch = sale.customerId
            saleDao.updateSale(sale.copy(status = "REVERSED", updatedAt = System.currentTimeMillis()))
            foundAnyEligible = true
        }

        if (payment != null) {
            if (payment.status == "REVERSED") {
                throw IllegalStateException("Customer payment $originalTransactionId is already reversed")
            }
            customerIdToTouch = payment.customerId
            customerPaymentDao.updatePayment(payment.copy(status = "REVERSED"))
            foundAnyEligible = true
        }

        if (adjustment != null) {
            if (adjustment.status == "REVERSED") {
                throw IllegalStateException("Adjustment $originalTransactionId is already reversed")
            }
            if (adjustment.entityType == "CUSTOMER") {
                customerIdToTouch = adjustment.entityId
            }
            adjustmentDao.updateAdjustment(adjustment.copy(status = "REVERSED"))
            foundAnyEligible = true
        }

        if (expense != null) {
            if (expense.status == "REVERSED") {
                throw IllegalStateException("Expense $originalTransactionId is already reversed")
            }
            expenseDao.updateExpense(expense.copy(status = "REVERSED"))
            foundAnyEligible = true
        }

        if (legacyTx != null) {
            if (legacyTx.operationStatus == "REVERSED") {
                throw IllegalStateException("Transaction $originalTransactionId is already reversed")
            }
            val item = legacyTx.toModel()
            val txType = item.typedTransactionType
            when (txType) {
                TransactionType.SALE,
                TransactionType.CUSTOMER_PAYMENT,
                TransactionType.BALANCE_ADJUSTMENT,
                TransactionType.EXPENSE -> {
                    foundAnyEligible = true
                }
                TransactionType.SALE_RETURN,
                TransactionType.CUSTOMER_REFUND -> {
                    throw UnsupportedOperationException("Phase 8 return/refund transactions cannot be reversed in Phase 7")
                }
                TransactionType.OPENING_BALANCE -> {
                    throw UnsupportedOperationException("Opening balances cannot be reversed as operational transactions")
                }
                TransactionType.REVERSAL -> {
                    throw UnsupportedOperationException("A reversal transaction cannot be reversed")
                }
                else -> {
                    throw UnsupportedOperationException("Transaction type $txType cannot be reversed in Phase 7")
                }
            }
            if (customerIdToTouch == null) {
                customerIdToTouch = legacyTx.customerId
            }
            transactionDao.updateTransactionOperationStatus(legacyTx.id, "REVERSED")
        }

        if (!foundAnyEligible) {
            throw UnsupportedOperationException("Transaction $originalTransactionId is not eligible for reversal")
        }

        // Neutralize physical stock movements for reversed transaction
        stockMovementDao.updateStatusByTransactionId(originalTransactionId, "REVERSED")

        // Rule B & 3: Create reversal record
        val reversedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val reversalId = "rev_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

        val reversal = Reversal(
            id = reversalId,
            originalTransactionId = originalTransactionId,
            reason = reason.trim(),
            reversedAt = reversedAt,
            status = "ACTIVE"
        )

        reversalDao.insertReversal(reversal)

        // Touch customer to trigger recalculation if needed
        if (customerIdToTouch != null) {
            val customer = customerDao.getCustomerById(customerIdToTouch)
            if (customer != null) {
                val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                customerDao.updateCustomer(
                    customer.copy(
                        hasRecentActivity = true,
                        lastTransactionDate = todayDate
                    )
                )
            }
        }

        reversal
    }

    /**
     * Phase 8 Returns & Refunds:
     * Records an authoritative [SaleReturn] and [SaleReturnLine]s for a previously sold [Sale].
     *
     * Invariants:
     * 1. Original Sale and SaleLine amounts are NEVER altered.
     * 2. Reverses COGS exactly using the frozen costPriceAtSale on each SaleLine.
     * 3. Prevents returning more quantity than remaining returnable.
     * 4. Multi-step atomic execution inside withTransaction (SaleReturn, SaleReturnLines, TransactionEntity, TransactionItemLines, optional Refund).
     * 5. If a refund is requested, verifies amount <= eligible cash refund amount.
     */
    suspend fun recordSaleReturn(
        saleId: String,
        returnLines: List<SaleReturnLineRequest>,
        reason: String,
        returnDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
        refundRequest: RefundRequest? = null
    ): SaleReturnResult = saleReturnRepository.recordSaleReturn(
        saleId = saleId,
        returnLines = returnLines,
        reason = reason,
        returnDate = returnDate,
        refundRequest = refundRequest
    )

    suspend fun getReturnLines(returnId: String): List<SaleReturnLine> = saleReturnRepository.getReturnLines(returnId)
    suspend fun getReturnsForSale(saleId: String): List<SaleReturn> = saleReturnRepository.getReturnsForSale(saleId)
    suspend fun getRefundsForSale(saleId: String): List<Refund> = refundRepository.getRefundsForSale(saleId)

    suspend fun getRemainingReturnableQuantities(saleId: String): Map<String, Int> =
        saleReturnRepository.getRemainingReturnableQuantities(saleId)

    // =========================================================================
    // PHASE 9: SUPPLIERS, PURCHASES & SUPPLIER PAYMENTS
    // =========================================================================

    suspend fun insertSupplier(supplier: Supplier): Supplier {
        return supplierRepository.insertSupplier(supplier)
    }

    suspend fun updateSupplier(supplier: Supplier) {
        supplierRepository.updateSupplier(supplier)
    }

    suspend fun getSupplierById(id: String): Supplier? = supplierRepository.getSupplierById(id)
    suspend fun getAllSuppliersSync(): List<Supplier> = supplierRepository.getAllSuppliersSync()

    suspend fun recordPurchase(
        supplierId: String,
        lines: List<PurchaseLineRequest>,
        purchaseDate: String? = null,
        paidAmount: Double = 0.0,
        paymentMethodId: String? = null,
        financialAccountId: String? = null,
        notes: String? = null,
        invoiceNumber: String? = null
    ): PurchaseResult = purchasesRepository.recordPurchase(
        supplierId = supplierId,
        lines = lines,
        purchaseDate = purchaseDate,
        paidAmount = paidAmount,
        paymentMethodId = paymentMethodId,
        financialAccountId = financialAccountId,
        notes = notes,
        invoiceNumber = invoiceNumber
    )


    suspend fun generateNextPurchaseInvoiceNumber(): String =
        purchasesRepository.generateNextPurchaseInvoiceNumber()

    suspend fun getPurchaseById(id: String): Purchase? = purchasesRepository.getPurchaseById(id)
    suspend fun getPurchaseLines(purchaseId: String): List<PurchaseLine> = purchasesRepository.getPurchaseLines(purchaseId)
    suspend fun getPurchasesForSupplier(supplierId: String): List<Purchase> = purchasesRepository.getPurchasesForSupplier(supplierId)

    suspend fun recordSupplierPayment(
        supplierId: String,
        amount: Double,
        paymentDate: String? = null,
        paymentMethodId: String? = null,
        financialAccountId: String? = null,
        notes: String? = null,
        referenceNumber: String? = null
    ): SupplierPayment = supplierPaymentRepository.recordSupplierPayment(
        supplierId = supplierId,
        amount = amount,
        paymentDate = paymentDate,
        paymentMethodId = paymentMethodId,
        financialAccountId = financialAccountId,
        notes = notes,
        referenceNumber = referenceNumber
    )

    suspend fun getPaymentsForSupplier(supplierId: String): List<SupplierPayment> =
        supplierPaymentRepository.getPaymentsForSupplier(supplierId)

    suspend fun recordPurchaseReturn(
        purchaseId: String,
        amount: Double,
        reason: String,
        returnDate: String? = null
    ): PurchaseReturn {
        return purchaseReturnRepository.recordPurchaseReturn(
            purchaseId = purchaseId,
            amount = amount,
            reason = reason,
            returnDate = returnDate
        )
    }

    suspend fun getSupplierBalance(supplierId: String): SupplierBalanceSummary {
        val purchases = purchaseDao.getPurchasesBySupplierIdSync(supplierId)
        val payments = supplierPaymentDao.getPaymentsBySupplierIdSync(supplierId)
        val returns = purchaseReturnDao.getReturnsBySupplierIdSync(supplierId)
        val adjustments = adjustmentDao.getAdjustmentsByEntitySync("SUPPLIER", supplierId)
        val openingBalances = openingBalanceDao.getOpeningBalancesByEntitySync("SUPPLIER", supplierId)
        return CentralAccountingEngine.calculateSupplierBalance(
            supplierId = supplierId,
            purchases = purchases,
            payments = payments,
            returns = returns,
            adjustments = adjustments,
            openingBalances = openingBalances
        )
    }

    suspend fun getSupplierStatement(supplierId: String): List<SupplierLedgerEntry> {
        val purchases = purchaseDao.getPurchasesBySupplierIdSync(supplierId)
        val payments = supplierPaymentDao.getPaymentsBySupplierIdSync(supplierId)
        val returns = purchaseReturnDao.getReturnsBySupplierIdSync(supplierId)
        val adjustments = adjustmentDao.getAdjustmentsByEntitySync("SUPPLIER", supplierId)
        val openingBalances = openingBalanceDao.getOpeningBalancesByEntitySync("SUPPLIER", supplierId)
        return CentralAccountingEngine.buildSupplierLedger(
            supplierId = supplierId,
            purchases = purchases,
            payments = payments,
            returns = returns,
            adjustments = adjustments,
            openingBalances = openingBalances
        )
    }

    // =========================================================================
    // Phase 10: Expenses Core
    // =========================================================================

    suspend fun insertExpenseCategory(category: ExpenseCategory): ExpenseCategory =
        expenseRepository.insertExpenseCategory(category)

    suspend fun getExpenseCategoryById(id: String): ExpenseCategory? =
        expenseRepository.getExpenseCategoryById(id)

    suspend fun getAllExpenseCategoriesSync(): List<ExpenseCategory> =
        expenseRepository.getAllExpenseCategoriesSync()


    suspend fun recordExpense(
        categoryId: String,
        amount: Double,
        financialAccountId: String,
        paymentMethodId: String? = null,
        date: String? = null,
        description: String,
        id: String? = null
    ): Expense = expenseRepository.recordExpense(
        categoryId = categoryId,
        amount = amount,
        financialAccountId = financialAccountId,
        paymentMethodId = paymentMethodId,
        date = date,
        description = description,
        id = id
    )

    suspend fun recordExpense(expense: Expense): Expense = expenseRepository.recordExpense(expense)

    suspend fun getExpenseById(id: String): Expense? = expenseRepository.getExpenseById(id)

    suspend fun getAllExpensesSync(): List<Expense> = expenseRepository.getAllExpensesSync()


    /**
     * Calculates the net balance of a specific financial account.
     * Inflows (positive):
     * - Paid amount from active sales
     * - Customer payments
     * - Opening balance (DEBIT positive, CREDIT negative)
     * - Adjustments (DEBIT positive, CREDIT negative)
     *
     * Outflows (negative):
     * - Refunds
     * - Cash paid for purchases
     * - Supplier payments
     * - Expenses
     */
    suspend fun getFinancialAccountBalance(accountId: String): Double {
        financialAccountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Financial account not found: $accountId")

        val sales = saleDao.getAllSalesSync()
        val customerPayments = customerPaymentDao.getAllPaymentsSync()
        val openingBalances = openingBalanceDao.getOpeningBalancesByEntitySync("FINANCIAL_ACCOUNT", accountId)
        val adjustments = adjustmentDao.getAdjustmentsByEntitySync("FINANCIAL_ACCOUNT", accountId)
        val refunds = refundDao.getAllRefundsSync()
        val purchases = purchaseDao.getAllPurchasesSync()
        val supplierPayments = supplierPaymentDao.getAllPaymentsSync()
        val expenses = expenseDao.getExpensesByAccountIdSync(accountId)

        return CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId = accountId,
            sales = sales,
            customerPayments = customerPayments,
            openingBalances = openingBalances,
            adjustments = adjustments,
            refunds = refunds,
            purchases = purchases,
            supplierPayments = supplierPayments,
            expenses = expenses
        )
    }

    suspend fun getFinancialAccountStatement(
        accountId: String,
        startDate: String? = null,
        endDate: String? = null
    ): com.example.accounting.FinancialAccountStatement {
        financialAccountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Financial account not found: $accountId")

        val sales = saleDao.getAllSalesSync()
        val customerPayments = customerPaymentDao.getAllPaymentsSync()
        val openingBalances = openingBalanceDao.getOpeningBalancesByEntitySync("FINANCIAL_ACCOUNT", accountId)
        val adjustments = adjustmentDao.getAdjustmentsByEntitySync("FINANCIAL_ACCOUNT", accountId)
        val refunds = refundDao.getAllRefundsSync()
        val purchases = purchaseDao.getAllPurchasesSync()
        val supplierPayments = supplierPaymentDao.getAllPaymentsSync()
        val expenses = expenseDao.getExpensesByAccountIdSync(accountId)

        return CentralAccountingEngine.buildFinancialAccountStatement(
            accountId = accountId,
            startDate = startDate,
            endDate = endDate,
            sales = sales,
            customerPayments = customerPayments,
            openingBalances = openingBalances,
            adjustments = adjustments,
            refunds = refunds,
            purchases = purchases,
            supplierPayments = supplierPayments,
            expenses = expenses
        )
    }

    suspend fun getFinancialAccountLedger(
        accountId: String,
        startDate: String? = null,
        endDate: String? = null
    ): List<com.example.accounting.FinancialAccountLedgerEntry> {
        return getFinancialAccountStatement(accountId, startDate, endDate).entries
    }

    // =========================================================================
    // PHASE 11: INVENTORY LEDGER INTEGRATION
    // =========================================================================

    suspend fun getProductStock(productId: String): ProductStockSummary =
        inventoryRepository.getProductStock(productId)

    suspend fun getInventoryStatement(productId: String): List<InventoryMovementEntry> =
        inventoryRepository.getInventoryStatement(productId)

    suspend fun getStockMovements(productId: String): List<StockMovement> =
        inventoryRepository.getStockMovements(productId)


    suspend fun getAllProductsStock(): Map<String, ProductStockSummary> =
        inventoryRepository.getAllProductsStock()

    suspend fun getTotalInventoryValuation(): Double =
        inventoryRepository.getTotalInventoryValuation()

    suspend fun recordInventoryAdjustment(
        productId: String,
        quantityDelta: Int,
        reason: String,
        date: String? = null
    ): Adjustment = inventoryRepository.recordInventoryAdjustment(
        productId = productId,
        quantityDelta = quantityDelta,
        reason = reason,
        date = date
    )

    suspend fun recordInventoryDamage(
        productId: String,
        quantity: Int,
        reason: String = "بضاعة تالفة",
        date: String? = null
    ): Adjustment = inventoryRepository.recordInventoryDamage(
        productId = productId,
        quantity = quantity,
        reason = reason,
        date = date
    )

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
            return SalesRepository.generateNextInvoiceNumber(lastInvoiceNumber)
        }

        fun generateNextPaymentReferenceNumber(lastRefNumber: String?): String {
            if (lastRefNumber.isNullOrBlank()) return "PAY-000001"
            val prefix = "PAY-"
            val num = if (lastRefNumber.startsWith(prefix)) {
                lastRefNumber.removePrefix(prefix).toIntOrNull() ?: 0
            } else {
                0
            }
            return String.format(Locale.US, "PAY-%06d", num + 1)
        }
    }
}
