package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE isArchived = 0 ORDER BY hasRecentActivity DESC, lastTransactionDate DESC")
    fun getActiveCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE isArchived = 0 ORDER BY hasRecentActivity DESC, lastTransactionDate DESC")
    suspend fun getActiveCustomersSync(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE isArchived = 1 ORDER BY archivedDate DESC, customerName ASC")
    fun getArchivedCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE isArchived = 1 ORDER BY archivedDate DESC, customerName ASC")
    suspend fun getArchivedCustomersSync(): List<CustomerEntity>

    @Query("SELECT * FROM customers ORDER BY hasRecentActivity DESC, lastTransactionDate DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY hasRecentActivity DESC, lastTransactionDate DESC")
    suspend fun getAllCustomersSync(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun getCustomerCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<CustomerEntity>)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("UPDATE customers SET isArchived = 1, archivedDate = :archivedDate WHERE id = :id")
    suspend fun archiveCustomer(id: String, archivedDate: String)

    @Query("UPDATE customers SET isArchived = 0, archivedDate = NULL WHERE id = :id")
    suspend fun unarchiveCustomer(id: String)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomerById(id: String)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isArchived = 0 ORDER BY name ASC")
    fun getActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isArchived = 0 ORDER BY name ASC")
    suspend fun getActiveProductsSync(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE isArchived = 1 ORDER BY archivedDate DESC, name ASC")
    fun getArchivedProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isArchived = 1 ORDER BY archivedDate DESC, name ASC")
    suspend fun getArchivedProductsSync(): List<ProductEntity>

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsSync(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): ProductEntity?

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET isArchived = 1, archivedDate = :archivedDate WHERE id = :id")
    suspend fun archiveProduct(id: String, archivedDate: String)

    @Query("UPDATE products SET isArchived = 0, archivedDate = NULL WHERE id = :id")
    suspend fun unarchiveProduct(id: String)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: String)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE isArchived = 0 ORDER BY id DESC")
    fun getActiveTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isArchived = 0 ORDER BY id DESC")
    suspend fun getActiveTransactionsSync(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE isArchived = 1 ORDER BY archivedDate DESC, id DESC")
    fun getArchivedTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isArchived = 1 ORDER BY archivedDate DESC, id DESC")
    suspend fun getArchivedTransactionsSync(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY id DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY id DESC")
    suspend fun getAllTransactionsSync(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY date ASC, id ASC")
    fun getTransactionsByCustomerId(customerId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY date ASC, id ASC")
    suspend fun getTransactionsByCustomerIdSync(customerId: String): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET isArchived = 1, archivedDate = :archivedDate WHERE id = :id")
    suspend fun archiveTransaction(id: String, archivedDate: String)

    @Query("UPDATE transactions SET isArchived = 0, archivedDate = NULL WHERE id = :id")
    suspend fun unarchiveTransaction(id: String)

    @Query("UPDATE transactions SET customerId = :customerId WHERE id = :transactionId")
    suspend fun updateTransactionCustomerId(transactionId: String, customerId: String?)

    @Deprecated(
        message = "Direct deletion of financial transactions violates the Accounting Golden Rule (ledger immutability).",
        level = DeprecationLevel.WARNING
    )
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Deprecated(
        message = "Direct deletion of financial transactions violates the Accounting Golden Rule (ledger immutability).",
        level = DeprecationLevel.WARNING
    )
    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}

@Dao
interface TransactionItemLineDao {
    @Query("SELECT * FROM transaction_item_lines WHERE transactionId = :transactionId")
    fun getLinesForTransaction(transactionId: String): Flow<List<TransactionItemLineEntity>>

    @Query("SELECT * FROM transaction_item_lines WHERE id = :id LIMIT 1")
    suspend fun getLineById(id: Long): TransactionItemLineEntity?

    @Query("SELECT * FROM transaction_item_lines")
    fun getAllLines(): Flow<List<TransactionItemLineEntity>>

    @Query("SELECT * FROM transaction_item_lines")
    suspend fun getAllLinesSync(): List<TransactionItemLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLine(line: TransactionItemLineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<TransactionItemLineEntity>)

    @Update
    suspend fun updateLine(line: TransactionItemLineEntity)

    @Delete
    suspend fun deleteLine(line: TransactionItemLineEntity)

    @Deprecated(
        message = "Direct deletion of transaction lines violates historical audit trail preservation.",
        level = DeprecationLevel.WARNING
    )
    @Query("DELETE FROM transaction_item_lines WHERE transactionId = :transactionId")
    suspend fun deleteLinesForTransaction(transactionId: String)

    @Query("DELETE FROM transaction_item_lines")
    suspend fun deleteAllLines()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY id DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY id DESC")
    suspend fun getAllNotificationsSync(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun getNotificationById(id: String): NotificationEntity?

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getNotificationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)

    @Update
    suspend fun updateNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Delete
    suspend fun deleteNotification(notification: NotificationEntity)

    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()
}

@Dao
interface StoreInfoDao {
    @Query("SELECT * FROM store_info WHERE id = 1 LIMIT 1")
    fun getStoreInfo(): Flow<StoreInfoEntity?>

    @Query("SELECT * FROM store_info WHERE id = 1 LIMIT 1")
    suspend fun getStoreInfoSync(): StoreInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(storeInfo: StoreInfoEntity)

    @Query("DELETE FROM store_info")
    suspend fun deleteStoreInfo()
}

@Dao
interface CustomerConflictDao {
    @Query("SELECT * FROM customer_identity_conflicts ORDER BY createdAt DESC, id DESC")
    fun getAllConflicts(): Flow<List<CustomerIdentityConflictEntity>>

    @Query("SELECT * FROM customer_identity_conflicts ORDER BY createdAt DESC, id DESC")
    suspend fun getAllConflictsSync(): List<CustomerIdentityConflictEntity>

    @Query("SELECT * FROM customer_identity_conflicts WHERE resolutionStatus = 'UNRESOLVED' ORDER BY createdAt DESC, id DESC")
    fun getUnresolvedConflicts(): Flow<List<CustomerIdentityConflictEntity>>

    @Query("SELECT * FROM customer_identity_conflicts WHERE resolutionStatus = 'UNRESOLVED' ORDER BY createdAt DESC, id DESC")
    suspend fun getUnresolvedConflictsSync(): List<CustomerIdentityConflictEntity>

    @Query("SELECT COUNT(*) FROM customer_identity_conflicts WHERE resolutionStatus = 'UNRESOLVED'")
    fun getUnresolvedConflictCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM customer_identity_conflicts WHERE resolutionStatus = 'UNRESOLVED'")
    suspend fun getUnresolvedConflictCountSync(): Int

    @Query("SELECT * FROM customer_identity_conflicts WHERE id = :id LIMIT 1")
    suspend fun getConflictById(id: String): CustomerIdentityConflictEntity?

    @Query("SELECT * FROM customer_identity_conflicts WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getConflictByTransactionId(transactionId: String): CustomerIdentityConflictEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: CustomerIdentityConflictEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflicts(conflicts: List<CustomerIdentityConflictEntity>)

    @Update
    suspend fun updateConflict(conflict: CustomerIdentityConflictEntity)

    @Query("""
        UPDATE customer_identity_conflicts
        SET resolutionStatus = 'RESOLVED',
            resolvedCustomerId = :resolvedCustomerId,
            resolvedAt = :resolvedAt,
            notes = :notes
        WHERE id = :conflictId
    """)
    suspend fun markResolved(
        conflictId: String,
        resolvedCustomerId: String,
        resolvedAt: String,
        notes: String? = null
    )

    @Query("""
        UPDATE customer_identity_conflicts
        SET resolutionStatus = 'DISMISSED',
            resolvedAt = :resolvedAt,
            notes = :notes
        WHERE id = :conflictId
    """)
    suspend fun markDismissed(
        conflictId: String,
        resolvedAt: String,
        notes: String? = null
    )
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY createdAt DESC, id DESC")
    fun getAllSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sales ORDER BY createdAt DESC, id DESC")
    suspend fun getAllSalesSync(): List<Sale>

    @Query("SELECT * FROM sales WHERE customerId = :customerId ORDER BY transactionDate DESC, createdAt DESC")
    fun getSalesByCustomerId(customerId: String): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE customerId = :customerId ORDER BY transactionDate DESC, createdAt DESC")
    suspend fun getSalesByCustomerIdSync(customerId: String): List<Sale>

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getSaleById(id: String): Sale?

    @Query("SELECT * FROM sales WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getSaleByInvoiceNumber(invoiceNumber: String): Sale?

    @Query("SELECT invoiceNumber FROM sales WHERE invoiceNumber LIKE 'INV-%' ORDER BY invoiceNumber DESC LIMIT 1")
    suspend fun getLastInvoiceNumber(): String?

    @Query("SELECT COUNT(*) FROM sales")
    suspend fun getSaleCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSale(sale: Sale)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSaleLines(lines: List<SaleLine>)

    @Query("SELECT * FROM sale_lines WHERE saleId = :saleId ORDER BY createdAt ASC, id ASC")
    suspend fun getSaleLinesBySaleId(saleId: String): List<SaleLine>

    @Query("SELECT * FROM sale_lines WHERE saleId = :saleId ORDER BY createdAt ASC, id ASC")
    fun getSaleLinesBySaleIdFlow(saleId: String): Flow<List<SaleLine>>

    @Update
    suspend fun updateSale(sale: Sale)

    @Query("DELETE FROM sales")
    suspend fun deleteAllSales()

    @Query("DELETE FROM sale_lines")
    suspend fun deleteAllSaleLines()
}


