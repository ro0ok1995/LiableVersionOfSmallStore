package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.accounting.CustomerBalanceSummary
import com.example.accounting.CustomerLedgerCalculator
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
import com.example.data.db.TransactionEntity
import com.example.data.db.TransactionItemLineEntity
import com.example.data.db.toEntity
import com.example.data.db.toModel
import com.example.data.db.toTransactionItem
import com.example.accounting.SupplierBalanceSummary
import com.example.accounting.SupplierLedgerCalculator
import com.example.accounting.SupplierLedgerEntry
import com.example.accounting.InventoryLedgerCalculator
import com.example.accounting.InventoryMovementEntry
import com.example.accounting.ProductStockSummary
import com.example.model.CustomerAccount
import com.example.model.CustomerConflictItem
import com.example.model.NotificationItem
import com.example.model.OperationStatus
import com.example.model.PaymentStatus
import com.example.model.ProductItem
import com.example.model.PurchaseLineRequest
import com.example.model.PurchaseResult
import com.example.model.RefundRequest
import com.example.model.SaleReturnLineRequest
import com.example.model.SaleReturnResult
import com.example.model.SampleData
import com.example.model.SettlementType
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
    private val productDao = database.productDao()
    private val transactionDao = database.transactionDao()
    private val transactionItemLineDao = database.transactionItemLineDao()
    private val notificationDao = database.notificationDao()
    private val storeInfoDao = database.storeInfoDao()
    private val customerConflictDao = database.customerConflictDao()
    private val saleDao = database.saleDao()
    private val financialAccountDao = database.financialAccountDao()
    private val paymentMethodDao = database.paymentMethodDao()
    private val customerPaymentDao = database.customerPaymentDao()
    private val openingBalanceDao = database.openingBalanceDao()
    private val adjustmentDao = database.adjustmentDao()
    private val reversalDao = database.reversalDao()
    private val saleReturnDao = database.saleReturnDao()
    private val saleReturnLineDao = database.saleReturnLineDao()
    private val refundDao = database.refundDao()
    private val supplierDao = database.supplierDao()
    private val purchaseDao = database.purchaseDao()
    private val purchaseLineDao = database.purchaseLineDao()
    private val supplierPaymentDao = database.supplierPaymentDao()
    private val purchaseReturnDao = database.purchaseReturnDao()
    private val expenseCategoryDao = database.expenseCategoryDao()
    private val expenseDao = database.expenseDao()

    val allFinancialAccounts: Flow<List<FinancialAccount>> = financialAccountDao.getAllAccounts()
    val allPaymentMethods: Flow<List<PaymentMethod>> = paymentMethodDao.getAllPaymentMethods()
    val allCustomerPayments: Flow<List<CustomerPayment>> = customerPaymentDao.getAllPayments()

    val allSales: Flow<List<Sale>> = saleDao.getAllSales()
    val allSaleReturns: Flow<List<SaleReturn>> = saleReturnDao.getAllReturns()
    val allRefunds: Flow<List<Refund>> = refundDao.getAllRefunds()
    val allSuppliers: Flow<List<Supplier>> = supplierDao.getAllSuppliers()
    val activeSuppliers: Flow<List<Supplier>> = supplierDao.getAllActiveSuppliers()
    val allPurchases: Flow<List<Purchase>> = purchaseDao.getAllPurchases()
    val allSupplierPayments: Flow<List<SupplierPayment>> = supplierPaymentDao.getAllPayments()
    val allExpenseCategories: Flow<List<ExpenseCategory>> = expenseCategoryDao.getActiveCategories()
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()

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
        val sales = saleDao.getSalesByCustomerIdSync(customerId)
        val payments = customerPaymentDao.getPaymentsByCustomerIdSync(customerId)
        val openingBalances = openingBalanceDao.getOpeningBalancesByEntitySync("CUSTOMER", customerId)
        val adjustments = adjustmentDao.getAdjustmentsByEntitySync("CUSTOMER", customerId)
        val saleReturns = saleReturnDao.getReturnsByCustomerIdSync(customerId)
        val refunds = refundDao.getRefundsByCustomerIdSync(customerId)
        if (sales.isNotEmpty() || payments.isNotEmpty() || openingBalances.isNotEmpty() || adjustments.isNotEmpty() || saleReturns.isNotEmpty() || refunds.isNotEmpty()) {
            return CustomerLedgerCalculator.calculateCustomerBalance(customerId, sales, payments, openingBalances, adjustments, saleReturns, refunds)
        }
        val txEntities = transactionDao.getTransactionsByCustomerIdSync(customerId)
        val domainTransactions = txEntities.map { it.toModel() }
        return CustomerLedgerCalculator.calculateCustomerBalance(customerId, domainTransactions)
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
                CustomerLedgerCalculator.calculateCustomerBalance(customerId, sales, payments, openingBalances, adjustments, saleReturns, refunds)
            } else {
                val domainTransactions = txEntities.map { it.toModel() }
                CustomerLedgerCalculator.calculateCustomerBalance(customerId, domainTransactions)
            }
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

    // =========================================================================
    // Phase 4: Financial Accounts & Payment Methods
    // =========================================================================
    suspend fun createFinancialAccount(account: FinancialAccount) {
        financialAccountDao.insertAccount(account)
    }

    suspend fun getFinancialAccountById(id: String): FinancialAccount? {
        return financialAccountDao.getAccountById(id)
    }

    fun getActiveFinancialAccounts(): Flow<List<FinancialAccount>> {
        return financialAccountDao.getActiveAccounts()
    }

    suspend fun getAllFinancialAccountsSync(): List<FinancialAccount> {
        return financialAccountDao.getAllAccountsSync()
    }

    suspend fun updateFinancialAccount(account: FinancialAccount) {
        financialAccountDao.updateAccount(account)
    }

    suspend fun createPaymentMethod(paymentMethod: PaymentMethod) {
        paymentMethodDao.insertPaymentMethod(paymentMethod)
    }

    suspend fun getPaymentMethodById(id: String): PaymentMethod? {
        return paymentMethodDao.getPaymentMethodById(id)
    }

    fun getActivePaymentMethods(): Flow<List<PaymentMethod>> {
        return paymentMethodDao.getActivePaymentMethods()
    }

    suspend fun getAllPaymentMethodsSync(): List<PaymentMethod> {
        return paymentMethodDao.getAllPaymentMethodsSync()
    }

    suspend fun updatePaymentMethod(paymentMethod: PaymentMethod) {
        paymentMethodDao.updatePaymentMethod(paymentMethod)
    }

    // =========================================================================
    // Phase 4: Customer Payments Engine
    // =========================================================================
    fun getCustomerPaymentsByCustomerId(customerId: String): Flow<List<CustomerPayment>> {
        return customerPaymentDao.getPaymentsByCustomerId(customerId)
    }

    suspend fun getCustomerPaymentsByCustomerIdSync(customerId: String): List<CustomerPayment> {
        return customerPaymentDao.getPaymentsByCustomerIdSync(customerId)
    }

    suspend fun getAllCustomerPaymentsSync(): List<CustomerPayment> {
        return customerPaymentDao.getAllPaymentsSync()
    }

    suspend fun getCustomerPaymentById(id: String): CustomerPayment? {
        return customerPaymentDao.getPaymentById(id)
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
    ): CustomerPayment = database.withTransaction {
        require(amount > 0.0) { "Customer payment amount must be strictly positive, was: $amount" }

        val customer = customerDao.getCustomerById(customerId)
            ?: throw IllegalArgumentException("Customer not found: $customerId")

        paymentMethodDao.getPaymentMethodById(paymentMethodId)
            ?: throw IllegalArgumentException("Payment method not found: $paymentMethodId")

        if (financialAccountId != null) {
            financialAccountDao.getAccountById(financialAccountId)
                ?: throw IllegalArgumentException("Financial account not found: $financialAccountId")
        }

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val paymentId = "pay_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

        val payment = CustomerPayment(
            id = paymentId,
            customerId = customerId,
            amount = amount,
            paymentMethodId = paymentMethodId,
            financialAccountId = financialAccountId,
            reference = reference,
            transactionDate = todayDate,
            createdAt = System.currentTimeMillis(),
            notes = notes,
            status = "ACTIVE"
        )

        customerPaymentDao.insertPayment(payment)

        customerDao.updateCustomer(
            customer.copy(
                hasRecentActivity = true,
                lastTransactionDate = todayDate
            )
        )

        payment
    }

    suspend fun recordCustomerPayment(payment: CustomerPayment): CustomerPayment = database.withTransaction {
        require(payment.amount > 0.0) { "Customer payment amount must be strictly positive, was: ${payment.amount}" }

        val customer = customerDao.getCustomerById(payment.customerId)
            ?: throw IllegalArgumentException("Customer not found: ${payment.customerId}")

        paymentMethodDao.getPaymentMethodById(payment.paymentMethodId)
            ?: throw IllegalArgumentException("Payment method not found: ${payment.paymentMethodId}")

        if (payment.financialAccountId != null) {
            financialAccountDao.getAccountById(payment.financialAccountId)
                ?: throw IllegalArgumentException("Financial account not found: ${payment.financialAccountId}")
        }

        customerPaymentDao.insertPayment(payment)

        customerDao.updateCustomer(
            customer.copy(
                hasRecentActivity = true,
                lastTransactionDate = payment.transactionDate
            )
        )

        payment
    }

    // =========================================================================
    // Phase 5: Opening Balances
    // =========================================================================
    fun getOpeningBalancesByEntity(entityType: String, entityId: String): Flow<List<OpeningBalance>> {
        return openingBalanceDao.getOpeningBalancesByEntity(entityType, entityId)
    }

    suspend fun getOpeningBalancesByEntitySync(entityType: String, entityId: String): List<OpeningBalance> {
        return openingBalanceDao.getOpeningBalancesByEntitySync(entityType, entityId)
    }

    suspend fun recordOpeningBalance(
        entityType: String,
        entityId: String,
        amount: Double,
        direction: String,
        date: String,
        reason: String? = null,
        reference: String? = null
    ): OpeningBalance = database.withTransaction {
        when (entityType) {
            "CUSTOMER" -> {
                customerDao.getCustomerById(entityId)
                    ?: throw IllegalArgumentException("Customer not found: $entityId")
            }
            "FINANCIAL_ACCOUNT" -> {
                financialAccountDao.getAccountById(entityId)
                    ?: throw IllegalArgumentException("Financial account not found: $entityId")
            }
            "SUPPLIER" -> {
                supplierDao.getSupplierById(entityId)
                    ?: throw IllegalArgumentException("Supplier not found: $entityId")
            }
            else -> throw IllegalArgumentException("Invalid entityType: $entityType. Must be one of: CUSTOMER, SUPPLIER, FINANCIAL_ACCOUNT")
        }

        val existing = openingBalanceDao.getOpeningBalancesByEntitySync(entityType, entityId)
        if (existing.isNotEmpty()) {
            throw IllegalStateException(
                "Opening balance already recorded for this entity; use an Adjustment to correct it, not a second Opening Balance."
            )
        }

        val openingBalance = OpeningBalance(
            id = "ob_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            entityType = entityType,
            entityId = entityId,
            amount = amount,
            direction = direction,
            date = date,
            reason = reason,
            reference = reference,
            createdAt = System.currentTimeMillis()
        )

        openingBalanceDao.insertOpeningBalance(openingBalance)

        openingBalance
    }

    // =========================================================================
    // Phase 6: Adjustments
    // =========================================================================
    fun getAdjustmentsByEntity(entityType: String, entityId: String): Flow<List<Adjustment>> {
        return adjustmentDao.getAdjustmentsByEntity(entityType, entityId)
    }

    suspend fun getAdjustmentsByEntitySync(entityType: String, entityId: String): List<Adjustment> {
        return adjustmentDao.getAdjustmentsByEntitySync(entityType, entityId)
    }

    suspend fun recordAdjustment(
        entityType: String,
        entityId: String,
        amount: Double,
        direction: String,
        date: String,
        reason: String,
        reference: String? = null
    ): Adjustment = database.withTransaction {
        val customer = when (entityType) {
            "CUSTOMER" -> {
                customerDao.getCustomerById(entityId)
                    ?: throw IllegalArgumentException("Customer not found: $entityId")
            }
            "FINANCIAL_ACCOUNT" -> {
                financialAccountDao.getAccountById(entityId)
                    ?: throw IllegalArgumentException("Financial account not found: $entityId")
                null
            }
            "SUPPLIER" -> {
                supplierDao.getSupplierById(entityId)
                    ?: throw IllegalArgumentException("Supplier not found: $entityId")
                null
            }
            else -> throw IllegalArgumentException("Invalid entityType: $entityType. Must be one of: CUSTOMER, SUPPLIER, FINANCIAL_ACCOUNT")
        }

        val adjustment = Adjustment(
            id = "adj_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            entityType = entityType,
            entityId = entityId,
            amount = amount,
            direction = direction,
            date = date,
            reason = reason,
            reference = reference,
            createdAt = System.currentTimeMillis()
        )

        adjustmentDao.insertAdjustment(adjustment)

        // Additive legacy transaction synchronization:
        // Record customer adjustment in transactions table so legacy statements, history, and queries reflect it seamlessly
        if (entityType == "CUSTOMER" && customer != null) {
            val isDebit = direction.equals("DEBIT", ignoreCase = true)
            val noteText = if (!reference.isNullOrBlank()) "$reason [$reference]" else reason
            val legacyTx = TransactionItem(
                id = adjustment.id,
                title = if (isDebit) "تعديل رصيد (مدين)" else "تعديل رصيد (دائن)",
                customerNameSnapshot = customer.customerName,
                activityType = if (isDebit) "تعديل رصيد (+)" else "تعديل رصيد (-)",
                amount = amount,
                isCredit = isDebit,
                date = date,
                relativeTime = "الآن",
                notes = noteText,
                settlementType = null,
                customerId = entityId,
                customerName = customer.customerName,
                isArchived = false,
                archivedDate = null,
                transactionType = TransactionType.BALANCE_ADJUSTMENT,
                saleType = null,
                paymentStatus = null,
                operationStatus = OperationStatus.ACTIVE,
                paidAmount = 0.0,
                creditAmount = 0.0
            ).toEntity()
            transactionDao.insertTransaction(legacyTx)
        }

        adjustment
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

    // =========================================================================
    // Phase 7: Reversal Engine
    // =========================================================================
    val allReversals: Flow<List<Reversal>> = reversalDao.getAllReversals()

    suspend fun getAllReversalsSync(): List<Reversal> = reversalDao.getAllReversalsSync()

    suspend fun getReversalForTransaction(originalTransactionId: String): Reversal? {
        return reversalDao.getReversalByOriginalTransactionId(originalTransactionId)
    }

    suspend fun isTransactionReversed(originalTransactionId: String): Boolean {
        val rev = reversalDao.getReversalByOriginalTransactionId(originalTransactionId)
        return rev != null && rev.status == "ACTIVE"
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
    ): SaleReturnResult {
        require(saleId.isNotBlank()) { "Sale ID cannot be blank" }
        require(reason.isNotBlank()) { "Return reason cannot be blank" }
        require(returnDate.isNotBlank()) { "Return date cannot be blank" }
        require(returnLines.isNotEmpty()) { "Return must contain at least one item line" }

        val sale = saleDao.getSaleById(saleId)
            ?: throw IllegalArgumentException("Sale not found with id: $saleId")

        if (sale.status == "REVERSED") {
            throw IllegalStateException("Cannot return items from reversed sale: $saleId")
        }

        val saleLines = saleDao.getSaleLinesBySaleId(saleId)
        val lineMap = saleLines.associateBy { it.id }

        // Prevent duplicate lines in same request
        val requestedLineIds = returnLines.map { it.saleLineId }
        require(requestedLineIds.distinct().size == requestedLineIds.size) {
            "Duplicate return lines for the same item are not allowed"
        }

        // Calculate already returned quantities from active returns
        val existingReturns = saleReturnDao.getReturnsBySaleIdSync(saleId).filter { it.status != "REVERSED" }
        val activeReturnIds = existingReturns.map { it.id }.toSet()
        val allExistingLines = saleReturnLineDao.getReturnLinesForSaleLines(saleLines.map { it.id })
        val activeExistingLines = allExistingLines.filter { it.saleReturnId in activeReturnIds }
        val alreadyReturnedMap = activeExistingLines.groupBy { it.saleLineId }
            .mapValues { (_, list) -> list.sumOf { it.quantity } }

        val returnId = UUID.randomUUID().toString()
        val constructedLines = mutableListOf<SaleReturnLine>()

        for (req in returnLines) {
            require(req.quantity > 0) { "Return quantity must be greater than zero, got: ${req.quantity}" }
            val originalLine = lineMap[req.saleLineId]
                ?: throw IllegalArgumentException("Sale line ${req.saleLineId} does not belong to sale $saleId")

            val alreadyReturned = alreadyReturnedMap[req.saleLineId] ?: 0
            val remainingReturnable = originalLine.quantity - alreadyReturned

            require(req.quantity <= remainingReturnable) {
                "Cannot return ${req.quantity} of '${originalLine.productNameSnapshot}'; only $remainingReturnable remaining returnable (sold: ${originalLine.quantity}, already returned: $alreadyReturned)"
            }

            val lineSubtotal = req.quantity * originalLine.unitPrice
            constructedLines.add(
                SaleReturnLine(
                    id = UUID.randomUUID().toString(),
                    saleReturnId = returnId,
                    saleLineId = originalLine.id,
                    productId = originalLine.productId,
                    productNameSnapshot = originalLine.productNameSnapshot,
                    quantity = req.quantity,
                    unitPrice = originalLine.unitPrice,
                    costPriceAtReturn = originalLine.costPriceAtSale,
                    subtotal = lineSubtotal
                )
            )
        }

        val totalReturnAmount = constructedLines.sumOf { it.subtotal }
        require(totalReturnAmount > 0.0) { "Total return amount ($totalReturnAmount) must be greater than zero" }

        // Validate refund request if provided
        var constructedRefund: Refund? = null
        if (refundRequest != null) {
            require(refundRequest.amount > 0.0) { "Refund amount (${refundRequest.amount}) must be greater than zero" }
            require(refundRequest.amount <= totalReturnAmount + 0.001) {
                "Refund amount (${refundRequest.amount}) cannot exceed return amount ($totalReturnAmount)"
            }

            val existingRefunds = refundDao.getRefundsBySaleId(saleId).filter { it.status != "REVERSED" }
            val totalAlreadyRefunded = existingRefunds.sumOf { it.amount }
            val maxRefundEligible = (sale.paidAmount - totalAlreadyRefunded).coerceAtLeast(0.0)

            require(refundRequest.amount <= maxRefundEligible + 0.001) {
                "Refund amount (${refundRequest.amount}) exceeds amount eligible for cash/bank refund ($maxRefundEligible)"
            }

            constructedRefund = Refund(
                id = UUID.randomUUID().toString(),
                saleReturnId = returnId,
                saleId = sale.id,
                customerId = sale.customerId,
                amount = refundRequest.amount,
                paymentMethodId = refundRequest.paymentMethodId,
                financialAccountId = refundRequest.financialAccountId,
                refundDate = returnDate,
                reason = refundRequest.reason?.trim()?.ifBlank { null } ?: "استرداد نقدي لمرتجع ${sale.invoiceNumber}",
                status = "ACTIVE"
            )
        }

        val saleReturn = SaleReturn(
            id = returnId,
            saleId = sale.id,
            customerId = sale.customerId,
            returnDate = returnDate,
            reason = reason.trim(),
            amount = totalReturnAmount,
            status = "ACTIVE"
        )

        val cogsReversed = constructedLines.sumOf { it.cogsReversed }
        val grossProfitCorrection = totalReturnAmount - cogsReversed

        database.withTransaction {
            saleReturnDao.insertReturn(saleReturn)
            saleReturnLineDao.insertReturnLines(constructedLines)

            val resolvedCustomerName = if (!sale.customerId.isNullOrBlank()) {
                customerDao.getCustomerById(sale.customerId)?.customerName ?: ""
            } else ""

            val returnTx = saleReturn.toTransactionItem(resolvedCustomerName, sale.invoiceNumber).toEntity()
            transactionDao.insertTransaction(returnTx)

            val txLines = constructedLines.map { rl ->
                TransactionItemLineEntity(
                    transactionId = returnId,
                    productId = rl.productId,
                    productNameSnapshot = rl.productNameSnapshot,
                    quantity = rl.quantity,
                    unitPrice = rl.unitPrice,
                    costPrice = rl.costPriceAtReturn,
                    subtotal = rl.subtotal
                )
            }
            transactionItemLineDao.insertLines(txLines)

            if (constructedRefund != null) {
                refundDao.insertRefund(constructedRefund)
                val refundTx = constructedRefund.toTransactionItem(resolvedCustomerName, sale.invoiceNumber).toEntity()
                transactionDao.insertTransaction(refundTx)
            }

            if (!sale.customerId.isNullOrBlank()) {
                val customer = customerDao.getCustomerById(sale.customerId)
                if (customer != null) {
                    customerDao.updateCustomer(
                        customer.copy(
                            hasRecentActivity = true,
                            lastTransactionDate = returnDate
                        )
                    )
                }
            }
        }

        return SaleReturnResult(
            saleReturn = saleReturn,
            lines = constructedLines,
            refund = constructedRefund,
            cogsReversed = cogsReversed,
            grossProfitCorrection = grossProfitCorrection
        )
    }

    /**
     * Phase 8: Records a standalone [Refund] for an existing [SaleReturn].
     */
    suspend fun recordRefund(
        saleReturnId: String,
        amount: Double,
        financialAccountId: String? = null,
        paymentMethodId: String? = null,
        reason: String,
        refundDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    ): Refund {
        require(saleReturnId.isNotBlank()) { "Sale return ID cannot be blank" }
        require(amount > 0.0) { "Refund amount must be greater than zero" }
        require(reason.isNotBlank()) { "Refund reason cannot be blank" }

        val saleReturn = saleReturnDao.getReturnById(saleReturnId)
            ?: throw IllegalArgumentException("Sale return not found with id: $saleReturnId")

        if (saleReturn.status == "REVERSED") {
            throw IllegalStateException("Cannot issue refund for reversed return: $saleReturnId")
        }

        val sale = saleDao.getSaleById(saleReturn.saleId)
            ?: throw IllegalArgumentException("Original sale not found: ${saleReturn.saleId}")

        if (sale.status == "REVERSED") {
            throw IllegalStateException("Cannot issue refund for reversed sale: ${sale.id}")
        }

        val existingRefunds = refundDao.getRefundsBySaleId(sale.id).filter { it.status != "REVERSED" }
        val totalAlreadyRefunded = existingRefunds.sumOf { it.amount }
        val maxRefundEligible = (sale.paidAmount - totalAlreadyRefunded).coerceAtLeast(0.0)

        require(amount <= maxRefundEligible + 0.001) {
            "Refund amount ($amount) exceeds amount eligible for cash/bank refund ($maxRefundEligible)"
        }

        val refundId = UUID.randomUUID().toString()
        val refund = Refund(
            id = refundId,
            saleReturnId = saleReturn.id,
            saleId = sale.id,
            customerId = sale.customerId,
            amount = amount,
            paymentMethodId = paymentMethodId,
            financialAccountId = financialAccountId,
            refundDate = refundDate,
            reason = reason.trim(),
            status = "ACTIVE"
        )

        database.withTransaction {
            refundDao.insertRefund(refund)

            val resolvedCustomerName = if (!sale.customerId.isNullOrBlank()) {
                customerDao.getCustomerById(sale.customerId)?.customerName ?: ""
            } else ""

            val refundTx = refund.toTransactionItem(resolvedCustomerName, sale.invoiceNumber).toEntity()
            transactionDao.insertTransaction(refundTx)

            if (!sale.customerId.isNullOrBlank()) {
                val customer = customerDao.getCustomerById(sale.customerId)
                if (customer != null) {
                    customerDao.updateCustomer(
                        customer.copy(
                            hasRecentActivity = true,
                            lastTransactionDate = refundDate
                        )
                    )
                }
            }
        }

        return refund
    }

    suspend fun getSaleReturnById(id: String): SaleReturn? = saleReturnDao.getReturnById(id)
    suspend fun getReturnLines(returnId: String): List<SaleReturnLine> = saleReturnLineDao.getLinesForReturn(returnId)
    suspend fun getReturnsForSale(saleId: String): List<SaleReturn> = saleReturnDao.getReturnsBySaleIdSync(saleId)
    suspend fun getRefundsForSale(saleId: String): List<Refund> = refundDao.getRefundsBySaleId(saleId)
    suspend fun getRefundsForReturn(returnId: String): List<Refund> = refundDao.getRefundsByReturnId(returnId)

    suspend fun getRemainingReturnableQuantities(saleId: String): Map<String, Int> {
        val saleLines = saleDao.getSaleLinesBySaleId(saleId)
        val existingReturns = saleReturnDao.getReturnsBySaleIdSync(saleId).filter { it.status != "REVERSED" }
        val activeReturnIds = existingReturns.map { it.id }.toSet()
        val allExistingLines = saleReturnLineDao.getReturnLinesForSaleLines(saleLines.map { it.id })
        val activeExistingLines = allExistingLines.filter { it.saleReturnId in activeReturnIds }
        val alreadyReturnedMap = activeExistingLines.groupBy { it.saleLineId }
            .mapValues { (_, list) -> list.sumOf { it.quantity } }
        return saleLines.associate { it.id to (it.quantity - (alreadyReturnedMap[it.id] ?: 0)).coerceAtLeast(0) }
    }

    // =========================================================================
    // PHASE 9: SUPPLIERS, PURCHASES & SUPPLIER PAYMENTS
    // =========================================================================

    suspend fun insertSupplier(supplier: Supplier): Supplier {
        require(supplier.id.isNotBlank()) { "Supplier id must not be blank" }
        require(supplier.name.isNotBlank()) { "Supplier name must not be blank" }
        supplierDao.insertSupplier(supplier)
        return supplier
    }

    suspend fun updateSupplier(supplier: Supplier) {
        require(supplier.id.isNotBlank()) { "Supplier id must not be blank" }
        require(supplier.name.isNotBlank()) { "Supplier name must not be blank" }
        supplierDao.updateSupplier(supplier)
    }

    suspend fun getSupplierById(id: String): Supplier? = supplierDao.getSupplierById(id)
    suspend fun getAllSuppliersSync(): List<Supplier> = supplierDao.getAllSuppliersSync()

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

        val totalAmount = lines.sumOf { it.quantity * it.unitCost }
        require(paidAmount >= 0.0) { "Paid amount ($paidAmount) cannot be negative" }
        require(paidAmount <= totalAmount + 0.001) {
            "Paid amount ($paidAmount) cannot exceed total purchase amount ($totalAmount)"
        }

        val creditAmount = (totalAmount - paidAmount).coerceAtLeast(0.0)
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
            financialAccountDao.getAccountById(accId)
                ?: throw IllegalArgumentException("Financial account not found: $accId")
            accId
        } else {
            financialAccountId
        }

        val purchase = Purchase(
            id = purchaseId,
            invoiceNumber = invNum,
            supplierId = supplierId,
            purchaseDate = dateToUse,
            totalAmount = totalAmount,
            paidAmount = paidAmount,
            creditAmount = creditAmount,
            paymentStatus = paymentStatus,
            paymentMethodId = paymentMethodId,
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
                subtotal = req.quantity * req.unitCost
            )
        }

        // Atomicity requirement: All financial effects commit together or rollback together
        database.withTransaction {
            if (paidAmount > 0.0) {
                val accId = financialAccountId ?: "acc_cash"
                financialAccountDao.getAccountById(accId)
                    ?: throw IllegalArgumentException("Financial account not found: $accId")
            }

            purchaseDao.insertPurchase(purchase)
            purchaseLineDao.insertLines(purchaseLines)

            // Update product cost price
            for (line in purchaseLines) {
                if (!line.productId.isNullOrBlank()) {
                    val p = productDao.getProductById(line.productId)
                    if (p != null) {
                        productDao.updateProduct(
                            p.copy(costPrice = line.unitCost)
                        )
                    }
                }
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

    suspend fun generateNextPurchaseInvoiceNumber(): String {
        val count = purchaseDao.getPurchaseCount()
        return String.format(Locale.US, "PUR-%06d", count + 1)
    }

    suspend fun getPurchaseById(id: String): Purchase? = purchaseDao.getPurchaseById(id)
    suspend fun getPurchaseLines(purchaseId: String): List<PurchaseLine> = purchaseLineDao.getLinesByPurchaseId(purchaseId)
    suspend fun getPurchasesForSupplier(supplierId: String): List<Purchase> = purchaseDao.getPurchasesBySupplierIdSync(supplierId)

    suspend fun recordSupplierPayment(
        supplierId: String,
        amount: Double,
        paymentDate: String? = null,
        paymentMethodId: String? = null,
        financialAccountId: String? = null,
        notes: String? = null,
        referenceNumber: String? = null
    ): SupplierPayment {
        require(supplierId.isNotBlank()) { "Supplier ID cannot be blank" }
        val supplier = supplierDao.getSupplierById(supplierId)
            ?: throw IllegalArgumentException("Supplier with ID $supplierId not found")

        require(amount > 0.0) { "Payment amount ($amount) must be greater than zero" }

        val accId = financialAccountId ?: "acc_cash"
        financialAccountDao.getAccountById(accId)
            ?: throw IllegalArgumentException("Financial account not found: $accId")

        val dateToUse = paymentDate?.takeIf { it.isNotBlank() }
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val payId = "sup_pay_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val refNum = referenceNumber?.takeIf { it.isNotBlank() }
            ?: "SPAY-${System.currentTimeMillis().toString().takeLast(6)}"

        val payment = SupplierPayment(
            id = payId,
            supplierId = supplierId,
            amount = amount,
            paymentDate = dateToUse,
            paymentMethodId = paymentMethodId,
            financialAccountId = accId,
            referenceNumber = refNum,
            notes = notes,
            status = "ACTIVE"
        )

        database.withTransaction {
            financialAccountDao.getAccountById(accId)
                ?: throw IllegalArgumentException("Financial account not found: $accId")

            supplierPaymentDao.insertPayment(payment)

            // Insert historical activity record (customerId null to prevent FK conflict with customers table)
            val txEntity = payment.toTransactionItem(supplier.name).copy(
                customerId = null,
                customerName = supplier.name
            ).toEntity()
            transactionDao.insertTransaction(txEntity)
        }

        return payment
    }

    suspend fun getPaymentsForSupplier(supplierId: String): List<SupplierPayment> =
        supplierPaymentDao.getPaymentsBySupplierIdSync(supplierId)

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

            val txEntity = pr.toTransactionItem(supplier.name, purchase.invoiceNumber).copy(
                customerId = null,
                customerName = supplier.name
            ).toEntity()
            transactionDao.insertTransaction(txEntity)
        }

        return pr
    }

    suspend fun getSupplierBalance(supplierId: String): SupplierBalanceSummary {
        val purchases = purchaseDao.getPurchasesBySupplierIdSync(supplierId)
        val payments = supplierPaymentDao.getPaymentsBySupplierIdSync(supplierId)
        val returns = purchaseReturnDao.getReturnsBySupplierIdSync(supplierId)
        val adjustments = adjustmentDao.getAdjustmentsByEntitySync("SUPPLIER", supplierId)
        val openingBalances = openingBalanceDao.getOpeningBalancesByEntitySync("SUPPLIER", supplierId)
        return SupplierLedgerCalculator.calculateSupplierBalance(
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
        return SupplierLedgerCalculator.buildSupplierLedger(
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

    suspend fun insertExpenseCategory(category: ExpenseCategory): ExpenseCategory {
        require(category.id.isNotBlank()) { "Expense category ID cannot be blank" }
        require(category.name.isNotBlank()) { "Expense category name cannot be blank" }
        expenseCategoryDao.insertCategory(category)
        return category
    }

    suspend fun getExpenseCategoryById(id: String): ExpenseCategory? =
        expenseCategoryDao.getCategoryById(id)

    suspend fun getAllExpenseCategoriesSync(): List<ExpenseCategory> =
        expenseCategoryDao.getAllCategoriesSync()

    suspend fun updateExpenseCategory(category: ExpenseCategory) {
        expenseCategoryDao.updateCategory(category)
    }

    suspend fun recordExpense(
        categoryId: String,
        amount: Double,
        financialAccountId: String,
        paymentMethodId: String? = null,
        date: String? = null,
        description: String,
        id: String? = null
    ): Expense = database.withTransaction {
        require(amount > 0.0) { "Expense amount ($amount) must be strictly greater than zero" }
        require(description.isNotBlank()) { "Expense description cannot be blank" }

        val category = expenseCategoryDao.getCategoryById(categoryId)
            ?: throw IllegalArgumentException("Expense category not found: $categoryId")

        val financialAccount = financialAccountDao.getAccountById(financialAccountId)
            ?: throw IllegalArgumentException("Financial account not found: $financialAccountId")

        if (!paymentMethodId.isNullOrBlank()) {
            paymentMethodDao.getPaymentMethodById(paymentMethodId)
                ?: throw IllegalArgumentException("Payment method not found: $paymentMethodId")
        }

        val dateToUse = date?.takeIf { it.isNotBlank() }
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val expenseId = id?.takeIf { it.isNotBlank() }
            ?: "exp_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

        val expense = Expense(
            id = expenseId,
            categoryId = categoryId,
            amount = amount,
            paymentMethodId = paymentMethodId,
            financialAccountId = financialAccountId,
            date = dateToUse,
            description = description.trim(),
            status = "ACTIVE"
        )

        expenseDao.insertExpense(expense)

        // Insert historical activity record into transactions table
        val txEntity = expense.toTransactionItem(category.name).toEntity()
        transactionDao.insertTransaction(txEntity)

        expense
    }

    suspend fun recordExpense(expense: Expense): Expense = database.withTransaction {
        require(expense.amount > 0.0) { "Expense amount (${expense.amount}) must be strictly greater than zero" }
        require(expense.description.isNotBlank()) { "Expense description cannot be blank" }

        val category = expenseCategoryDao.getCategoryById(expense.categoryId)
            ?: throw IllegalArgumentException("Expense category not found: ${expense.categoryId}")

        val financialAccount = financialAccountDao.getAccountById(expense.financialAccountId)
            ?: throw IllegalArgumentException("Financial account not found: ${expense.financialAccountId}")

        if (!expense.paymentMethodId.isNullOrBlank()) {
            paymentMethodDao.getPaymentMethodById(expense.paymentMethodId)
                ?: throw IllegalArgumentException("Payment method not found: ${expense.paymentMethodId}")
        }

        expenseDao.insertExpense(expense)

        val txEntity = expense.toTransactionItem(category.name).toEntity()
        transactionDao.insertTransaction(txEntity)

        expense
    }

    suspend fun getExpenseById(id: String): Expense? = expenseDao.getExpenseById(id)

    suspend fun getAllExpensesSync(): List<Expense> = expenseDao.getAllExpensesSync()

    suspend fun getExpensesByCategorySync(categoryId: String): List<Expense> =
        expenseDao.getExpensesByCategoryIdSync(categoryId)

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

        val sales = if (accountId == "acc_cash") {
            saleDao.getAllSalesSync()
                .filter { it.status != "REVERSED" }
                .sumOf { it.paidAmount }
        } else {
            0.0
        }

        val customerPayments = customerPaymentDao.getAllPaymentsSync()
            .filter { it.financialAccountId == accountId && it.status != "REVERSED" }
            .sumOf { it.amount }

        val openingBalances = openingBalanceDao.getOpeningBalancesByEntitySync("FINANCIAL_ACCOUNT", accountId)
            .sumOf { if (it.direction == "DEBIT") it.amount else -it.amount }

        val adjustments = adjustmentDao.getAdjustmentsByEntitySync("FINANCIAL_ACCOUNT", accountId)
            .filter { it.status != "REVERSED" }
            .sumOf { if (it.direction == "DEBIT") it.amount else -it.amount }

        val refunds = refundDao.getAllRefundsSync()
            .filter { it.financialAccountId == accountId && it.status != "REVERSED" }
            .sumOf { it.amount }

        val purchases = purchaseDao.getAllPurchasesSync()
            .filter { it.financialAccountId == accountId && it.status != "REVERSED" }
            .sumOf { it.paidAmount }

        val supplierPayments = supplierPaymentDao.getAllPaymentsSync()
            .filter { it.financialAccountId == accountId && it.status != "REVERSED" }
            .sumOf { it.amount }

        val expenses = expenseDao.getExpensesByAccountIdSync(accountId)
            .filter { it.status != "REVERSED" }
            .sumOf { it.amount }

        return (sales + customerPayments + openingBalances + adjustments) -
                (refunds + purchases + supplierPayments + expenses)
    }

    // =========================================================================
    // PHASE 11: INVENTORY LEDGER INTEGRATION
    // =========================================================================

    suspend fun getProductStock(productId: String): ProductStockSummary {
        val product = productDao.getProductById(productId)
        val purchases = purchaseDao.getAllPurchasesSync()
        val purchaseLines = purchaseLineDao.getLinesByProductId(productId)
        val sales = saleDao.getAllSalesSync()
        val saleLines = saleDao.getSaleLinesByProductId(productId)
        val saleReturns = saleReturnDao.getAllReturnsSync()
        val saleReturnLines = saleReturnLineDao.getLinesByProductId(productId)
        val purchaseReturns = purchaseReturnDao.getAllReturnsSync()
        val adjustments = adjustmentDao.getAdjustmentsByEntitySync("PRODUCT", productId)

        return InventoryLedgerCalculator.calculateProductStock(
            productId = productId,
            purchases = purchases,
            purchaseLines = purchaseLines,
            sales = sales,
            saleLines = saleLines,
            saleReturns = saleReturns,
            saleReturnLines = saleReturnLines,
            fallbackUnitCost = product?.costPrice ?: 0.0,
            productName = product?.name ?: "",
            purchaseReturns = purchaseReturns,
            adjustments = adjustments
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

        return InventoryLedgerCalculator.buildInventoryLedger(
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

    suspend fun getAllProductsStock(): Map<String, ProductStockSummary> {
        val products = productDao.getAllProductsSync()
        val productIds = products.map { it.id }.toSet()
        val purchases = purchaseDao.getAllPurchasesSync()
        val purchaseLines = purchaseLineDao.getAllLinesSync()
        val sales = saleDao.getAllSalesSync()
        val saleLines = saleDao.getAllSaleLinesSync()
        val saleReturns = saleReturnDao.getAllReturnsSync()
        val saleReturnLines = saleReturnLineDao.getAllLinesSync()
        val purchaseReturns = purchaseReturnDao.getAllReturnsSync()
        val adjustments = adjustmentDao.getAllAdjustmentsSync()

        val costMap = products.associate { it.id to it.costPrice }
        val nameMap = products.associate { it.id to it.name }

        return InventoryLedgerCalculator.calculateAllProductsStock(
            productIds = productIds,
            purchases = purchases,
            purchaseLines = purchaseLines,
            sales = sales,
            saleLines = saleLines,
            saleReturns = saleReturns,
            saleReturnLines = saleReturnLines,
            productCostPrices = costMap,
            productNames = nameMap,
            purchaseReturns = purchaseReturns,
            adjustments = adjustments
        )
    }

    suspend fun getTotalInventoryValuation(): Double {
        val stockMap = getAllProductsStock()
        return InventoryLedgerCalculator.calculateTotalInventoryValuation(stockMap.values)
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

        adjustmentDao.insertAdjustment(adj)
        return adj
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
