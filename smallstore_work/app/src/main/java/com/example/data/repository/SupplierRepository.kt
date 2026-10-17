package com.example.data.repository

import com.example.data.db.SmallStoreDatabase
import com.example.data.db.Supplier
import com.example.data.db.SupplierDao
import kotlinx.coroutines.flow.Flow

class SupplierRepository(
    private val supplierDao: SupplierDao
) {
    constructor(database: SmallStoreDatabase) : this(database.supplierDao())

    val allSuppliers: Flow<List<Supplier>> = supplierDao.getAllSuppliers()
    val activeSuppliers: Flow<List<Supplier>> = supplierDao.getAllActiveSuppliers()

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
}
