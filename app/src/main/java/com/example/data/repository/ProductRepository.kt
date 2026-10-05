package com.example.data.repository

import com.example.data.db.ProductDao
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.toEntity
import com.example.data.db.toModel
import com.example.model.ProductItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProductRepository(
    private val productDao: ProductDao
) {
    constructor(database: SmallStoreDatabase) : this(database.productDao())

    val products: Flow<List<ProductItem>> = productDao.getActiveProducts().map { list ->
        list.map { it.toModel() }
    }

    val archivedProducts: Flow<List<ProductItem>> = productDao.getArchivedProducts().map { list ->
        list.map { it.toModel() }
    }

    val allProducts: Flow<List<ProductItem>> = productDao.getAllProducts().map { list ->
        list.map { it.toModel() }
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

    suspend fun getProductById(id: String): ProductItem? {
        return productDao.getProductById(id)?.toModel()
    }

    suspend fun getAllProductsSync(): List<ProductItem> {
        return productDao.getAllProductsSync().map { it.toModel() }
    }

    suspend fun getActiveProductsSync(): List<ProductItem> {
        return productDao.getActiveProductsSync().map { it.toModel() }
    }
}
