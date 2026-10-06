package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.accounting.SupplierBalanceSummary
import com.example.accounting.SupplierLedgerEntry
import com.example.data.db.Purchase
import com.example.data.db.PurchaseLine
import com.example.data.db.PurchaseReturn
import com.example.data.db.Supplier
import com.example.data.db.SupplierPayment
import com.example.data.repository.StoreRepository
import com.example.model.PurchaseLineRequest
import com.example.model.PurchaseReturnLineRequest
import com.example.model.PurchaseResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SupplierPurchaseViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: StoreRepository = StoreRepository.getInstance(application)
) : AndroidViewModel(application) {

    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers: StateFlow<List<Supplier>> = _suppliers.asStateFlow()

    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()

    private val _supplierPayments = MutableStateFlow<List<SupplierPayment>>(emptyList())
    val supplierPayments: StateFlow<List<SupplierPayment>> = _supplierPayments.asStateFlow()

    val allSuppliers: Flow<List<Supplier>> = repository.allSuppliers
    val allPurchases: Flow<List<Purchase>> = repository.allPurchases
    val allSupplierPayments: Flow<List<SupplierPayment>> = repository.allSupplierPayments

    init {
        viewModelScope.launch {
            repository.allSuppliers.collect { list ->
                _suppliers.value = list
            }
        }

        viewModelScope.launch {
            repository.allPurchases.collect { list ->
                _purchases.value = list
            }
        }

        viewModelScope.launch {
            repository.allSupplierPayments.collect { list ->
                _supplierPayments.value = list
            }
        }
    }

    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun addSupplier(
        name: String,
        phone: String = "",
        address: String? = null,
        notes: String? = null,
        onComplete: (Result<Supplier>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val supplier = Supplier(
                    id = "sup_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                    name = name.trim(),
                    phone = phone.trim(),
                    address = address?.trim(),
                    notes = notes?.trim(),
                    isArchived = false
                )
                val inserted = repository.insertSupplier(supplier)
                onComplete(Result.success(inserted))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            }
        }
    }

    fun recordPurchase(
        supplierId: String,
        lines: List<PurchaseLineRequest>,
        paidAmount: Double = 0.0,
        financialAccountId: String? = null,
        notes: String? = null,
        purchaseDate: String = getCurrentDateString(),
        onComplete: (Result<PurchaseResult>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = repository.recordPurchase(
                    supplierId = supplierId,
                    lines = lines,
                    purchaseDate = purchaseDate,
                    paidAmount = paidAmount,
                    financialAccountId = financialAccountId,
                    notes = notes
                )
                onComplete(Result.success(result))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            }
        }
    }

    fun recordSupplierPayment(
        supplierId: String,
        amount: Double,
        paymentDate: String = getCurrentDateString(),
        financialAccountId: String? = null,
        notes: String? = null,
        onComplete: (Result<SupplierPayment>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = repository.recordSupplierPayment(
                    supplierId = supplierId,
                    amount = amount,
                    paymentDate = paymentDate,
                    financialAccountId = financialAccountId,
                    notes = notes
                )
                onComplete(Result.success(result))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            }
        }
    }

    fun recordPurchaseReturn(
        purchaseId: String,
        returnLines: List<PurchaseReturnLineRequest>,
        reason: String,
        returnDate: String = getCurrentDateString(),
        onComplete: (Result<PurchaseReturn>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = repository.recordPurchaseReturn(
                    purchaseId = purchaseId,
                    returnLines = returnLines,
                    reason = reason,
                    returnDate = returnDate
                )
                onComplete(Result.success(result))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            }
        }
    }

    fun getPurchaseReturnableQuantities(purchaseId: String, onResult: (Map<String, Int>) -> Unit) {
        viewModelScope.launch { onResult(repository.getRemainingPurchaseReturnQuantities(purchaseId)) }
    }

    suspend fun getSupplierBalance(supplierId: String): SupplierBalanceSummary {
        return repository.getSupplierBalance(supplierId)
    }

    suspend fun getSupplierStatement(supplierId: String): List<SupplierLedgerEntry> {
        return repository.getSupplierStatement(supplierId)
    }

    suspend fun getPurchaseLines(purchaseId: String): List<PurchaseLine> {
        return repository.getPurchaseLines(purchaseId)
    }

    suspend fun getPurchasesForSupplier(supplierId: String): List<Purchase> {
        return repository.getPurchasesForSupplier(supplierId)
    }

    suspend fun getPaymentsForSupplier(supplierId: String): List<SupplierPayment> {
        return repository.getPaymentsForSupplier(supplierId)
    }
}
