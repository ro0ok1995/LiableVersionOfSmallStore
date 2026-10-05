package com.example.data.repository

import androidx.room.withTransaction
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
}
