package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.Adjustment
import com.example.data.db.AdjustmentDao
import com.example.data.db.CustomerDao
import com.example.data.db.FinancialAccountDao
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.SupplierDao
import com.example.data.db.TransactionDao
import com.example.data.db.toEntity
import com.example.model.OperationStatus
import com.example.model.TransactionItem
import com.example.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class AdjustmentRepository(
    private val database: SmallStoreDatabase,
    private val adjustmentDao: AdjustmentDao = database.adjustmentDao(),
    private val customerDao: CustomerDao = database.customerDao(),
    private val financialAccountDao: FinancialAccountDao = database.financialAccountDao(),
    private val supplierDao: SupplierDao = database.supplierDao(),
    private val transactionDao: TransactionDao = database.transactionDao()
) {
    fun getAdjustmentsByEntity(entityType: String, entityId: String): Flow<List<Adjustment>> {
        return adjustmentDao.getAdjustmentsByEntity(entityType, entityId)
    }

    suspend fun getAdjustmentsByEntitySync(entityType: String, entityId: String): List<Adjustment> {
        return adjustmentDao.getAdjustmentsByEntitySync(entityType, entityId)
    }

    suspend fun recordAdjustment(
        entityType: String,
        entityId: String,
        amount: Double,
        direction: String,
        date: String,
        reason: String,
        reference: String? = null
    ): Adjustment = database.withTransaction {
        val customer = when (entityType) {
            "CUSTOMER" -> {
                customerDao.getCustomerById(entityId)
                    ?: throw IllegalArgumentException("Customer not found: $entityId")
            }
            "FINANCIAL_ACCOUNT" -> {
                financialAccountDao.getAccountById(entityId)
                    ?: throw IllegalArgumentException("Financial account not found: $entityId")
                null
            }
            "SUPPLIER" -> {
                supplierDao.getSupplierById(entityId)
                    ?: throw IllegalArgumentException("Supplier not found: $entityId")
                null
            }
            else -> throw IllegalArgumentException("Invalid entityType: $entityType. Must be one of: CUSTOMER, SUPPLIER, FINANCIAL_ACCOUNT")
        }

        val adjustment = Adjustment(
            id = "adj_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            entityType = entityType,
            entityId = entityId,
            amount = amount,
            direction = direction,
            date = date,
            reason = reason,
            reference = reference,
            createdAt = System.currentTimeMillis()
        )

        adjustmentDao.insertAdjustment(adjustment)

        // Additive legacy transaction synchronization:
        // Record customer adjustment in transactions table so legacy statements, history, and queries reflect it seamlessly
        if (entityType == "CUSTOMER" && customer != null) {
            val isDebit = direction.equals("DEBIT", ignoreCase = true)
            val noteText = if (!reference.isNullOrBlank()) "$reason [$reference]" else reason
            val legacyTx = TransactionItem(
                id = adjustment.id,
                title = if (isDebit) "تعديل رصيد (مدين)" else "تعديل رصيد (دائن)",
                customerNameSnapshot = customer.customerName,
                activityType = if (isDebit) "تعديل رصيد (+)" else "تعديل رصيد (-)",
                amount = amount,
                isCredit = isDebit,
                date = date,
                relativeTime = "الآن",
                notes = noteText,
                settlementType = null,
                customerId = entityId,
                customerName = customer.customerName,
                isArchived = false,
                archivedDate = null,
                transactionType = TransactionType.BALANCE_ADJUSTMENT,
                saleType = null,
                paymentStatus = null,
                operationStatus = OperationStatus.ACTIVE,
                paidAmount = 0.0,
                creditAmount = 0.0
            ).toEntity()
            transactionDao.insertTransaction(legacyTx)
        }

        adjustment
    }
}
