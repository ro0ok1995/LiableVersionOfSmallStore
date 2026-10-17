package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.CustomerConflictDao
import com.example.data.db.CustomerDao
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.TransactionDao
import com.example.data.db.toEntity
import com.example.data.db.toModel
import com.example.model.ConflictReason
import com.example.model.ConflictResolutionStatus
import com.example.model.CustomerConflictItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CustomerConflictRepository(
    private val database: SmallStoreDatabase,
    private val customerConflictDao: CustomerConflictDao = database.customerConflictDao(),
    private val customerDao: CustomerDao = database.customerDao(),
    private val transactionDao: TransactionDao = database.transactionDao()
) {
    val customerConflicts: Flow<List<CustomerConflictItem>> = customerConflictDao.getAllConflicts().map { list ->
        list.map { it.toModel() }
    }

    val unresolvedCustomerConflicts: Flow<List<CustomerConflictItem>> = customerConflictDao.getUnresolvedConflicts().map { list ->
        list.map { it.toModel() }
    }

    val unresolvedConflictCount: Flow<Int> = customerConflictDao.getUnresolvedConflictCount()

    suspend fun getConflictById(conflictId: String): CustomerConflictItem? {
        return customerConflictDao.getConflictById(conflictId)?.toModel()
    }

    suspend fun getConflictByTransactionId(transactionId: String): CustomerConflictItem? {
        return customerConflictDao.getConflictByTransactionId(transactionId)?.toModel()
    }

    suspend fun insertCustomerConflict(conflict: CustomerConflictItem) {
        customerConflictDao.insertConflict(conflict.toEntity())
    }

    suspend fun auditAndRegisterHistoricalCustomerConflicts(): Int {
        val allCustomers = customerDao.getAllCustomersSync()
        val customersByName = allCustomers.groupBy { it.customerName.trim().lowercase() }
        val unlinkedTransactions = transactionDao.getAllTransactionsSync().filter { it.customerId == null }

        var conflictCount = 0
        for (tx in unlinkedTransactions) {
            val name = tx.customerName.trim()
            if (name.isBlank() || name == "عميل كاش" || name == "عميل عام" || name == "عميل نقدي" || !tx.isCredit) {
                // Anonymous walk-in cash sale: valid, no customer conflict needed
                continue
            }

            val cleanName = name.lowercase()
            val matches = customersByName[cleanName] ?: emptyList()

            val reason = when {
                matches.size > 1 -> ConflictReason.AMBIGUOUS_CUSTOMER_NAME
                matches.isEmpty() -> ConflictReason.CUSTOMER_NOT_FOUND
                else -> null
            }

            if (reason != null) {
                val existing = customerConflictDao.getConflictByTransactionId(tx.id)
                if (existing == null) {
                    val conflict = CustomerConflictItem(
                        id = "conflict_${tx.id}",
                        transactionId = tx.id,
                        originalCustomerName = name,
                        conflictReason = reason,
                        createdAt = tx.date.ifBlank { java.time.Instant.now().toString() },
                        resolutionStatus = ConflictResolutionStatus.UNRESOLVED
                    )
                    customerConflictDao.insertConflict(conflict.toEntity())
                    conflictCount++
                }
            }
        }
        return conflictCount
    }

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
}
