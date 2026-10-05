package com.example.data.repository

import androidx.room.withTransaction
import com.example.accounting.CentralAccountingEngine
import com.example.data.backup.BackupPayload
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.TransactionItemLineEntity
import com.example.data.db.toEntity
import com.example.data.db.toModel
import com.example.model.CustomerAccount
import com.example.model.NotificationItem
import com.example.model.ProductItem
import com.example.model.StoreInfo
import com.example.model.TransactionItem

class StoreBackupRepository(
    private val database: SmallStoreDatabase
) {
    private val customerDao = database.customerDao()
    private val productDao = database.productDao()
    private val transactionDao = database.transactionDao()
    private val transactionItemLineDao = database.transactionItemLineDao()
    private val notificationDao = database.notificationDao()
    private val storeInfoDao = database.storeInfoDao()
    private val conflictDao = database.customerConflictDao()
    private val saleDao = database.saleDao()
    private val accountDao = database.financialAccountDao()
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
    private val stockMovementDao = database.stockMovementDao()

    suspend fun getAllDataForBackup(): BackupPayload {
        val currentStoreInfo = storeInfoDao.getStoreInfoSync()?.toModel() ?: StoreInfo()
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
            version = 2,
            backupTimestamp = System.currentTimeMillis(),
            storeInfoAtBackupTime = currentStoreInfo,
            customers = customersList,
            products = productsList,
            transactions = transactionsList,
            transactionItemLines = linesList,
            notifications = notificationsList,
            customerEntities = customerDao.getAllCustomersSync(),
            customerIdentityConflicts = conflictDao.getAllConflictsSync(),
            sales = saleDao.getAllSalesSync(),
            saleLines = saleDao.getAllSaleLinesSync(),
            financialAccounts = accountDao.getAllAccountsSync(),
            paymentMethods = paymentMethodDao.getAllPaymentMethodsSync(),
            customerPayments = customerPaymentDao.getAllPaymentsSync(),
            openingBalances = openingBalanceDao.getAllOpeningBalancesSync(),
            adjustments = adjustmentDao.getAllAdjustmentsSync(),
            reversals = reversalDao.getAllReversalsSync(),
            saleReturns = saleReturnDao.getAllReturnsSync(),
            saleReturnLines = saleReturnLineDao.getAllLinesSync(),
            refunds = refundDao.getAllRefundsSync(),
            suppliers = supplierDao.getAllSuppliersSync(),
            purchases = purchaseDao.getAllPurchasesSync(),
            purchaseLines = purchaseLineDao.getAllLinesSync(),
            supplierPayments = supplierPaymentDao.getAllPaymentsSync(),
            purchaseReturns = purchaseReturnDao.getAllReturnsSync(),
            expenseCategories = expenseCategoryDao.getAllCategoriesSync(),
            expenses = expenseDao.getAllExpensesSync(),
            stockMovements = stockMovementDao.getAllMovementsSync()
        )
    }

    suspend fun restoreDataFromBackup(payload: BackupPayload, replaceStoreInfo: Boolean) {
        val hasModernSnapshot = payload.sales.isNotEmpty() ||
            payload.financialAccounts.isNotEmpty() ||
            payload.suppliers.isNotEmpty() ||
            payload.purchases.isNotEmpty() ||
            payload.customerPayments.isNotEmpty() ||
            payload.expenses.isNotEmpty() ||
            payload.stockMovements.isNotEmpty() ||
            payload.customerEntities.isNotEmpty()

        val expectedSnapshot = if (hasModernSnapshot) {
            buildAccountingSnapshot(
                customers = if (payload.customerEntities.isNotEmpty()) payload.customerEntities else payload.customers.map { it.toEntity() },
                suppliers = payload.suppliers,
                accounts = payload.financialAccounts,
                sales = payload.sales,
                saleLines = payload.saleLines,
                customerPayments = payload.customerPayments,
                openingBalances = payload.openingBalances,
                adjustments = payload.adjustments,
                saleReturns = payload.saleReturns,
                saleReturnLines = payload.saleReturnLines,
                refunds = payload.refunds,
                purchases = payload.purchases,
                supplierPayments = payload.supplierPayments,
                purchaseReturns = payload.purchaseReturns,
                expenses = payload.expenses,
                stockMovements = payload.stockMovements,
                products = payload.products.map { it.toEntity() }
            )
        } else null

        database.withTransaction {
            // Delete child/dependent records before parent records to respect foreign keys.
            stockMovementDao.deleteAllStockMovements()
            expenseDao.deleteAllExpenses()
            expenseCategoryDao.deleteAllCategories()
            purchaseReturnDao.deleteAllReturns()
            supplierPaymentDao.deleteAllPayments()
            purchaseLineDao.deleteAllLines()
            purchaseDao.deleteAllPurchases()
            saleReturnLineDao.deleteAllReturnLines()
            saleReturnDao.deleteAllReturns()
            refundDao.deleteAllRefunds()
            reversalDao.deleteAllReversals()
            adjustmentDao.deleteAllAdjustments()
            openingBalanceDao.deleteAllOpeningBalances()
            customerPaymentDao.deleteAllPayments()
            saleDao.deleteAllSaleLines()
            saleDao.deleteAllSales()
            paymentMethodDao.deleteAllPaymentMethods()
            accountDao.deleteAllAccounts()
            conflictDao.deleteAllConflicts()
            transactionItemLineDao.deleteAllLines()
            transactionDao.deleteAllTransactions()
            notificationDao.deleteAllNotifications()
            customerDao.deleteAllCustomers()
            productDao.deleteAllProducts()
            supplierDao.deleteAllSuppliers()

            if (hasModernSnapshot) {
                if (payload.customerEntities.isNotEmpty()) customerDao.insertCustomers(payload.customerEntities)
                else if (payload.customers.isNotEmpty()) customerDao.insertCustomers(payload.customers.map { it.toEntity() })
                if (payload.products.isNotEmpty()) productDao.insertProducts(payload.products.map { it.toEntity() })
                if (payload.suppliers.isNotEmpty()) supplierDao.insertSuppliers(payload.suppliers)
                if (payload.financialAccounts.isNotEmpty()) accountDao.insertAccounts(payload.financialAccounts)
                if (payload.paymentMethods.isNotEmpty()) paymentMethodDao.insertPaymentMethods(payload.paymentMethods)
                if (payload.expenseCategories.isNotEmpty()) expenseCategoryDao.insertCategories(payload.expenseCategories)

                if (payload.sales.isNotEmpty()) saleDao.insertSales(payload.sales)
                if (payload.saleLines.isNotEmpty()) saleDao.insertSaleLines(payload.saleLines)
                if (payload.customerPayments.isNotEmpty()) customerPaymentDao.insertPayments(payload.customerPayments)
                payload.openingBalances.forEach { openingBalanceDao.insertOpeningBalance(it) }
                payload.adjustments.forEach { adjustmentDao.insertAdjustment(it) }
                payload.reversals.forEach { reversalDao.insertReversal(it) }
                if (payload.saleReturns.isNotEmpty()) saleReturnDao.insertReturns(payload.saleReturns)
                if (payload.saleReturnLines.isNotEmpty()) saleReturnLineDao.insertReturnLines(payload.saleReturnLines)
                if (payload.refunds.isNotEmpty()) refundDao.insertRefunds(payload.refunds)
                if (payload.purchases.isNotEmpty()) purchaseDao.insertPurchases(payload.purchases)
                if (payload.purchaseLines.isNotEmpty()) purchaseLineDao.insertLines(payload.purchaseLines)
                if (payload.supplierPayments.isNotEmpty()) supplierPaymentDao.insertPayments(payload.supplierPayments)
                if (payload.purchaseReturns.isNotEmpty()) purchaseReturnDao.insertReturns(payload.purchaseReturns)
                payload.expenses.forEach { expenseDao.insertExpense(it) }
                if (payload.stockMovements.isNotEmpty()) stockMovementDao.insertMovements(payload.stockMovements)
                if (payload.customerIdentityConflicts.isNotEmpty()) conflictDao.insertConflicts(payload.customerIdentityConflicts)

                // Legacy compatibility data is retained when present, but never replaces modern truth.
                if (payload.transactions.isNotEmpty()) transactionDao.insertTransactions(payload.transactions.map { it.toEntity() })
                if (payload.transactionItemLines.isNotEmpty()) transactionItemLineDao.insertLines(payload.transactionItemLines)
            } else {
                // Backward-compatible restore for Version 1 backups.
                if (payload.customers.isNotEmpty()) customerDao.insertCustomers(payload.customers.map { it.toEntity() })
                if (payload.products.isNotEmpty()) productDao.insertProducts(payload.products.map { it.toEntity() })
                if (payload.transactions.isNotEmpty()) transactionDao.insertTransactions(payload.transactions.map { it.toEntity() })
                if (payload.transactionItemLines.isNotEmpty()) transactionItemLineDao.insertLines(payload.transactionItemLines)
            }

            if (payload.notifications.isNotEmpty()) {
                notificationDao.insertNotifications(payload.notifications.map { it.toEntity() })
            }

            if (replaceStoreInfo) {
                storeInfoDao.insertOrUpdate(payload.storeInfoAtBackupTime.toEntity(isSaved = true))
            }
        }

        expectedSnapshot?.let { expected ->
            val actual = buildAccountingSnapshot(
                customers = customerDao.getAllCustomersSync(),
                suppliers = supplierDao.getAllSuppliersSync(),
                accounts = accountDao.getAllAccountsSync(),
                sales = saleDao.getAllSalesSync(),
                saleLines = saleDao.getAllSaleLinesSync(),
                customerPayments = customerPaymentDao.getAllPaymentsSync(),
                openingBalances = openingBalanceDao.getAllOpeningBalancesSync(),
                adjustments = adjustmentDao.getAllAdjustmentsSync(),
                saleReturns = saleReturnDao.getAllReturnsSync(),
                saleReturnLines = saleReturnLineDao.getAllLinesSync(),
                refunds = refundDao.getAllRefundsSync(),
                purchases = purchaseDao.getAllPurchasesSync(),
                supplierPayments = supplierPaymentDao.getAllPaymentsSync(),
                purchaseReturns = purchaseReturnDao.getAllReturnsSync(),
                expenses = expenseDao.getAllExpensesSync(),
                stockMovements = stockMovementDao.getAllMovementsSync(),
                products = productDao.getAllProductsSync()
            )
            require(expected.reconcilesWith(actual)) {
                "Backup restore accounting reconciliation failed: restored authoritative results differ from source snapshot."
            }
        }
    }

    private data class AccountingSnapshot(
        val customerBalances: Map<String, Double>,
        val supplierBalances: Map<String, Double>,
        val financialAccountBalances: Map<String, Double>,
        val inventoryValuation: Double,
        val totalSales: Double,
        val cogs: Double,
        val grossProfit: Double,
        val netProfit: Double
    ) {
        fun reconcilesWith(other: AccountingSnapshot): Boolean {
            fun mapsEqual(a: Map<String, Double>, b: Map<String, Double>): Boolean =
                a.keys == b.keys && a.keys.all { kotlin.math.abs((a[it] ?: 0.0) - (b[it] ?: 0.0)) < 0.0001 }

            return mapsEqual(customerBalances, other.customerBalances) &&
                mapsEqual(supplierBalances, other.supplierBalances) &&
                mapsEqual(financialAccountBalances, other.financialAccountBalances) &&
                kotlin.math.abs(inventoryValuation - other.inventoryValuation) < 0.0001 &&
                kotlin.math.abs(totalSales - other.totalSales) < 0.0001 &&
                kotlin.math.abs(cogs - other.cogs) < 0.0001 &&
                kotlin.math.abs(grossProfit - other.grossProfit) < 0.0001 &&
                kotlin.math.abs(netProfit - other.netProfit) < 0.0001
        }
    }

    private fun buildAccountingSnapshot(
        customers: List<com.example.data.db.CustomerEntity>,
        suppliers: List<com.example.data.db.Supplier>,
        accounts: List<com.example.data.db.FinancialAccount>,
        sales: List<com.example.data.db.Sale>,
        saleLines: List<com.example.data.db.SaleLine>,
        customerPayments: List<com.example.data.db.CustomerPayment>,
        openingBalances: List<com.example.data.db.OpeningBalance>,
        adjustments: List<com.example.data.db.Adjustment>,
        saleReturns: List<com.example.data.db.SaleReturn>,
        saleReturnLines: List<com.example.data.db.SaleReturnLine>,
        refunds: List<com.example.data.db.Refund>,
        purchases: List<com.example.data.db.Purchase>,
        supplierPayments: List<com.example.data.db.SupplierPayment>,
        purchaseReturns: List<com.example.data.db.PurchaseReturn>,
        expenses: List<com.example.data.db.Expense>,
        stockMovements: List<com.example.data.db.StockMovementEntity>,
        products: List<com.example.data.db.ProductEntity>
    ): AccountingSnapshot {
        val customerBalances = customers.associate { customer ->
            customer.id to CentralAccountingEngine.calculateCustomerBalance(
                customerId = customer.id,
                sales = sales,
                payments = customerPayments,
                openingBalances = openingBalances,
                adjustments = adjustments,
                saleReturns = saleReturns,
                refunds = refunds
            ).balance
        }
        val supplierBalances = suppliers.associate { supplier ->
            supplier.id to CentralAccountingEngine.calculateSupplierBalance(
                supplierId = supplier.id,
                purchases = purchases,
                payments = supplierPayments,
                returns = purchaseReturns,
                adjustments = adjustments,
                openingBalances = openingBalances
            ).balance
        }
        val financialAccountBalances = accounts.associate { account ->
            account.id to CentralAccountingEngine.calculateFinancialAccountBalance(
                accountId = account.id,
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
        val productIds = products.map { it.id }.toSet()
        val stock = CentralAccountingEngine.calculateAllProductsStockFromMovements(
            productIds = productIds,
            movements = stockMovements,
            productCostPrices = products.associate { it.id to it.costPrice },
            productNames = products.associate { it.id to it.name }
        )
        val report = CentralAccountingEngine.calculateFinancialReportFromSales(
            sales = sales,
            saleLines = saleLines,
            saleReturns = saleReturns,
            saleReturnLines = saleReturnLines,
            expenses = expenses,
            refunds = refunds
        )
        return AccountingSnapshot(
            customerBalances = customerBalances,
            supplierBalances = supplierBalances,
            financialAccountBalances = financialAccountBalances,
            inventoryValuation = stock.values.sumOf { it.valuation },
            totalSales = report.totalSales,
            cogs = report.cogs,
            grossProfit = report.grossProfit,
            netProfit = report.netProfit
        )
    }

}
