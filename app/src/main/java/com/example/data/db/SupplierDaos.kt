package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplier(supplier: Supplier): Long

    @Update
    suspend fun updateSupplier(supplier: Supplier)

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun getSupplierById(id: String): Supplier?

    @Query("SELECT * FROM suppliers WHERE isArchived = 0 ORDER BY name ASC")
    fun getAllActiveSuppliers(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    suspend fun getAllSuppliersSync(): List<Supplier>

    @Query("DELETE FROM suppliers")
    suspend fun deleteAllSuppliers()
}

@Dao
interface PurchaseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchase(purchase: Purchase): Long

    @Update
    suspend fun updatePurchase(purchase: Purchase)

    @Query("SELECT * FROM purchases WHERE id = :id LIMIT 1")
    suspend fun getPurchaseById(id: String): Purchase?

    @Query("SELECT * FROM purchases WHERE supplierId = :supplierId ORDER BY purchaseDate DESC, createdAt DESC")
    fun getPurchasesBySupplierId(supplierId: String): Flow<List<Purchase>>

    @Query("SELECT * FROM purchases WHERE supplierId = :supplierId ORDER BY purchaseDate DESC, createdAt DESC")
    suspend fun getPurchasesBySupplierIdSync(supplierId: String): List<Purchase>

    @Query("SELECT * FROM purchases ORDER BY purchaseDate DESC, createdAt DESC")
    fun getAllPurchases(): Flow<List<Purchase>>

    @Query("SELECT * FROM purchases ORDER BY purchaseDate DESC, createdAt DESC")
    suspend fun getAllPurchasesSync(): List<Purchase>

    @Query("SELECT COUNT(*) FROM purchases")
    suspend fun getPurchaseCount(): Int

    @Query("DELETE FROM purchases")
    suspend fun deleteAllPurchases()
}

@Dao
interface PurchaseLineDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLine(line: PurchaseLine): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<PurchaseLine>)

    @Query("SELECT * FROM purchase_lines WHERE purchaseId = :purchaseId ORDER BY createdAt ASC")
    suspend fun getLinesByPurchaseId(purchaseId: String): List<PurchaseLine>

    @Query("SELECT * FROM purchase_lines WHERE productId = :productId ORDER BY createdAt ASC")
    suspend fun getLinesByProductId(productId: String): List<PurchaseLine>

    @Query("SELECT * FROM purchase_lines ORDER BY createdAt ASC")
    suspend fun getAllLinesSync(): List<PurchaseLine>

    @Query("DELETE FROM purchase_lines")
    suspend fun deleteAllLines()
}

@Dao
interface SupplierPaymentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: SupplierPayment): Long

    @Update
    suspend fun updatePayment(payment: SupplierPayment)

    @Query("SELECT * FROM supplier_payments WHERE id = :id LIMIT 1")
    suspend fun getPaymentById(id: String): SupplierPayment?

    @Query("SELECT * FROM supplier_payments WHERE supplierId = :supplierId ORDER BY paymentDate DESC, createdAt DESC")
    fun getPaymentsBySupplierId(supplierId: String): Flow<List<SupplierPayment>>

    @Query("SELECT * FROM supplier_payments WHERE supplierId = :supplierId ORDER BY paymentDate DESC, createdAt DESC")
    suspend fun getPaymentsBySupplierIdSync(supplierId: String): List<SupplierPayment>

    @Query("SELECT * FROM supplier_payments ORDER BY paymentDate DESC, createdAt DESC")
    fun getAllPayments(): Flow<List<SupplierPayment>>

    @Query("SELECT * FROM supplier_payments ORDER BY paymentDate DESC, createdAt DESC")
    suspend fun getAllPaymentsSync(): List<SupplierPayment>

    @Query("DELETE FROM supplier_payments")
    suspend fun deleteAllPayments()
}

@Dao
interface PurchaseReturnDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReturn(purchaseReturn: PurchaseReturn): Long

    @Update
    suspend fun updateReturn(purchaseReturn: PurchaseReturn)

    @Query("SELECT * FROM purchase_returns WHERE id = :id LIMIT 1")
    suspend fun getReturnById(id: String): PurchaseReturn?

    @Query("SELECT * FROM purchase_returns WHERE supplierId = :supplierId ORDER BY returnDate DESC, createdAt DESC")
    suspend fun getReturnsBySupplierIdSync(supplierId: String): List<PurchaseReturn>

    @Query("SELECT * FROM purchase_returns WHERE purchaseId = :purchaseId ORDER BY returnDate DESC, createdAt DESC")
    suspend fun getReturnsByPurchaseIdSync(purchaseId: String): List<PurchaseReturn>

    @Query("SELECT * FROM purchase_returns ORDER BY returnDate DESC, createdAt DESC")
    suspend fun getAllReturnsSync(): List<PurchaseReturn>

    @Query("DELETE FROM purchase_returns")
    suspend fun deleteAllReturns()
}
