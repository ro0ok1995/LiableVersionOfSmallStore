package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.backup.BackupPayload
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.TransactionItemLineEntity
import com.example.data.db.toEntity
import com.example.data.db.toModel
import com.example.model.CustomerAccount
import com.example.model.NotificationItem
import com.example.model.ProductItem
import com.example.model.SampleData
import com.example.model.StoreInfo
import com.example.model.TransactionItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class StoreRepository private constructor(
    private val database: SmallStoreDatabase
) {
    private val customerDao = database.customerDao()
    private val productDao = database.productDao()
    private val transactionDao = database.transactionDao()
    private val transactionItemLineDao = database.transactionItemLineDao()
    private val notificationDao = database.notificationDao()
    private val storeInfoDao = database.storeInfoDao()

    val customers: Flow<List<CustomerAccount>> = customerDao.getActiveCustomers().map { list ->
        list.map { it.toModel() }
    }

    val archivedCustomers: Flow<List<CustomerAccount>> = customerDao.getArchivedCustomers().map { list ->
        list.map { it.toModel() }
    }

    val allCustomers: Flow<List<CustomerAccount>> = customerDao.getAllCustomers().map { list ->
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

    val transactions: Flow<List<TransactionItem>> = combine(
        transactionDao.getActiveTransactions(),
        customerDao.getAllCustomers()
    ) { txEntities, custEntities ->
        mapTransactionsWithCustomer(txEntities, custEntities)
    }

    val archivedTransactions: Flow<List<TransactionItem>> = combine(
        transactionDao.getArchivedTransactions(),
        customerDao.getAllCustomers()
    ) { txEntities, custEntities ->
        mapTransactionsWithCustomer(txEntities, custEntities)
    }

    val allTransactions: Flow<List<TransactionItem>> = combine(
        transactionDao.getAllTransactions(),
        customerDao.getAllCustomers()
    ) { txEntities, custEntities ->
        mapTransactionsWithCustomer(txEntities, custEntities)
    }

    private fun mapTransactionsWithCustomer(
        txEntities: List<com.example.data.db.TransactionEntity>,
        custEntities: List<com.example.data.db.CustomerEntity>
    ): List<TransactionItem> {
        val customerMapById = custEntities.associateBy { it.id }
        val customerMapByName = custEntities.groupBy { it.customerName }
        return txEntities.map { entity ->
            val model = entity.toModel()
            val resolvedCid = model.customerId?.takeIf { customerMapById.containsKey(it) }
                ?: run {
                    val matchingCustomers = customerMapByName[entity.customerName].orEmpty()
                    when {
                        matchingCustomers.size == 1 -> matchingCustomers.first().id
                        matchingCustomers.size > 1 -> {
                            matchingCustomers.firstOrNull { it.lastTransactionDate == entity.date }?.id
                                ?: matchingCustomers.firstOrNull { it.hasRecentActivity }?.id
                                ?: matchingCustomers.first().id
                        }
                        else -> null
                    }
                }
            model.copy(customerId = resolvedCid)
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

    suspend fun deleteTransactionPermanently(id: String) {
        database.withTransaction {
            transactionItemLineDao.deleteLinesForTransaction(id)
            transactionDao.deleteTransactionById(id)
        }
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
    }
}
