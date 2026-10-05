package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.accounting.InventoryMovementEntry
import com.example.accounting.ProductStockSummary
import com.example.accounting.StockMovement
import com.example.data.db.Adjustment
import com.example.data.repository.StoreRepository
import com.example.model.ArchiveConflict
import com.example.model.ProductItem
import com.example.util.ProductImageHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InventoryViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: StoreRepository = StoreRepository.getInstance(application)
) : AndroidViewModel(application) {

    private val _products = MutableStateFlow<List<ProductItem>>(emptyList())
    val products: StateFlow<List<ProductItem>> = _products.asStateFlow()

    private val _archivedProducts = MutableStateFlow<List<ProductItem>>(emptyList())
    val archivedProducts: StateFlow<List<ProductItem>> = _archivedProducts.asStateFlow()

    private val _archivedProductIds = MutableStateFlow<Set<String>>(emptySet())
    val archivedProductIds: StateFlow<Set<String>> = _archivedProductIds.asStateFlow()

    private val _productStockMap = MutableStateFlow<Map<String, ProductStockSummary>>(emptyMap())
    val productStockMap: StateFlow<Map<String, ProductStockSummary>> = _productStockMap.asStateFlow()

    private val _totalInventoryValuation = MutableStateFlow(0.0)
    val totalInventoryValuation: StateFlow<Double> = _totalInventoryValuation.asStateFlow()

    private val _pendingProductConflict = MutableStateFlow<ArchiveConflict.ProductConflict?>(null)
    val pendingProductConflict: StateFlow<ArchiveConflict.ProductConflict?> = _pendingProductConflict.asStateFlow()

    val allProducts: Flow<List<ProductItem>> = repository.products
    val allArchivedProducts: Flow<List<ProductItem>> = repository.archivedProducts

    init {
        viewModelScope.launch {
            repository.products.collect { list ->
                _products.value = list
            }
        }

        viewModelScope.launch {
            repository.archivedProducts.collect { list ->
                _archivedProducts.value = list
                _archivedProductIds.value = list.map { p -> p.id }.toSet()
            }
        }

        refreshInventory()
    }

    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun refreshInventory() {
        viewModelScope.launch {
            try {
                val stockMap = repository.getAllProductsStock()
                val valuation = repository.getTotalInventoryValuation()
                _productStockMap.value = stockMap
                _totalInventoryValuation.value = valuation
            } catch (_: Exception) {}
        }
    }

    suspend fun getProductStock(productId: String): ProductStockSummary {
        return repository.getProductStock(productId)
    }

    suspend fun getInventoryStatement(productId: String): List<InventoryMovementEntry> {
        return repository.getInventoryStatement(productId)
    }

    suspend fun getStockMovements(productId: String): List<StockMovement> {
        return repository.getStockMovements(productId)
    }

    fun recordInventoryAdjustment(
        productId: String,
        quantityDelta: Int,
        reason: String,
        date: String? = null,
        onComplete: (Result<Adjustment>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val adj = repository.recordInventoryAdjustment(productId, quantityDelta, reason, date)
                refreshInventory()
                onComplete(Result.success(adj))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            }
        }
    }

    fun recordInventoryDamage(
        productId: String,
        quantity: Int,
        reason: String = "بضاعة تالفة",
        date: String? = null,
        onComplete: (Result<Adjustment>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val adj = repository.recordInventoryDamage(productId, quantity, reason, date)
                refreshInventory()
                onComplete(Result.success(adj))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            }
        }
    }

    fun addProduct(
        name: String,
        price: Double,
        costPrice: Double = 0.0,
        category: String = "عام",
        unit: String = "حبة",
        imageUri: String? = null
    ) {
        viewModelScope.launch {
            val newProduct = ProductItem(
                id = "p_${System.currentTimeMillis()}",
                name = name,
                price = price,
                costPrice = costPrice,
                category = category,
                unit = unit,
                imageUri = imageUri
            )
            repository.addProduct(newProduct)
        }
    }

    fun updateProduct(product: ProductItem) {
        viewModelScope.launch {
            repository.updateProduct(product)
        }
    }

    fun archiveProduct(productId: String, date: String = getCurrentDateString()) {
        viewModelScope.launch {
            repository.archiveProduct(productId, date)
        }
    }

    fun unarchiveProduct(productId: String) {
        val product = _archivedProducts.value.firstOrNull { it.id == productId }
        if (product != null) {
            requestRestoreProduct(product)
        } else {
            viewModelScope.launch {
                repository.restoreProduct(productId)
            }
        }
    }

    fun requestRestoreProduct(
        product: ProductItem,
        activeList: List<ProductItem> = _products.value,
        onConflict: ((ArchiveConflict.ProductConflict) -> Unit)? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        val conflict = checkProductConflict(product, activeList)
        if (conflict != null) {
            _pendingProductConflict.value = conflict
            onConflict?.invoke(conflict)
        } else {
            viewModelScope.launch {
                repository.restoreProduct(product.id)
                onSuccess?.invoke()
            }
        }
    }

    fun checkProductConflict(
        product: ProductItem,
        activeList: List<ProductItem> = _products.value
    ): ArchiveConflict.ProductConflict? {
        // 1. Same ID
        val idMatch = activeList.firstOrNull { it.id == product.id }
        if (idMatch != null) {
            return ArchiveConflict.ProductConflict(
                archivedProduct = product,
                conflictingProduct = idMatch,
                descriptionAr = "يوجد صنف نشط بنفس المعرّف (${idMatch.name})",
                descriptionEn = "An active product already exists with the same ID (${idMatch.name})"
            )
        }

        // 2. Same Name check with price/category differences highlighted
        val cleanName = product.name.trim().lowercase()
        val nameMatch = activeList.firstOrNull { it.name.trim().lowercase() == cleanName }
        if (nameMatch != null) {
            val diffPrice = Math.abs(nameMatch.price - product.price) > 0.001
            val descAr = if (diffPrice) {
                "يوجد صنف نشط بنفس الاسم ولكن بسعر مختلف (السعر الحالي: ₪${nameMatch.price} مقابل المؤرشف: ₪${product.price})"
            } else {
                "يوجد صنف نشط مطابق بنفس الاسم والسعر (₪${nameMatch.price})"
            }
            val descEn = if (diffPrice) {
                "An active product exists with this name but a different price (Active: ₪${nameMatch.price} vs Archived: ₪${product.price})"
            } else {
                "An active product exists with the same name and price (₪${nameMatch.price})"
            }
            return ArchiveConflict.ProductConflict(
                archivedProduct = product,
                conflictingProduct = nameMatch,
                descriptionAr = descAr,
                descriptionEn = descEn
            )
        }

        return null
    }

    fun clearPendingProductConflict() {
        _pendingProductConflict.value = null
    }

    fun deleteProductPermanently(product: ProductItem, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            if (!product.imageUri.isNullOrBlank()) {
                ProductImageHelper.deleteProductImage(product.imageUri)
            }
            repository.deleteProductPermanently(product.id)
            onComplete?.invoke()
        }
    }
}
