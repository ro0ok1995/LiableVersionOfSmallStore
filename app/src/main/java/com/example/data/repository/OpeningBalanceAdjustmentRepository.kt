package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.Adjustment
import com.example.data.db.AdjustmentDao
import com.example.data.db.CustomerDao
import com.example.data.db.FinancialAccountDao
import com.example.data.db.OpeningBalance
import com.example.data.db.OpeningBalanceDao
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.SupplierDao
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class OpeningBalanceAdjustmentRepository(
    private val database: SmallStoreDatabase,
    private val openingBalanceDao: OpeningBalanceDao = database.openingBalanceDao(),
    private val adjustmentDao: AdjustmentDao = database.adjustmentDao(),
    private val customerDao: CustomerDao = database.customerDao(),
    private val financialAccountDao: FinancialAccountDao = database.financialAccountDao(),
    private val supplierDao: SupplierDao = database.supplierDao()
) {
    fun getOpeningBalancesByEntity(entityType: String, entityId: String): Flow<List<OpeningBalance>> {
        return openingBalanceDao.getOpeningBalancesByEntity(entityType, entityId)
    }

    suspend fun getOpeningBalancesByEntitySync(entityType: String, entityId: String): List<OpeningBalance> {
        return openingBalanceDao.getOpeningBalancesByEntitySync(entityType, entityId)
    }

    suspend fun insertOpeningBalance(openingBalance: OpeningBalance) {
        openingBalanceDao.insertOpeningBalance(openingBalance)
    }

    fun getAdjustmentsByEntity(entityType: String, entityId: String): Flow<List<Adjustment>> {
        return adjustmentDao.getAdjustmentsByEntity(entityType, entityId)
    }

    suspend fun getAdjustmentsByEntitySync(entityType: String, entityId: String): List<Adjustment> {
        return adjustmentDao.getAdjustmentsByEntitySync(entityType, entityId)
    }

    suspend fun recordOpeningBalance(
        entityType: String,
        entityId: String,
        amount: Double,
        direction: String,
        date: String,
        reason: String? = null,
        reference: String? = null
    ): OpeningBalance = database.withTransaction {
        when (entityType) {
            "CUSTOMER" -> {
                customerDao.getCustomerById(entityId)
                    ?: throw IllegalArgumentException("Customer not found: $entityId")
            }
            "FINANCIAL_ACCOUNT" -> {
                financialAccountDao.getAccountById(entityId)
                    ?: throw IllegalArgumentException("Financial account not found: $entityId")
            }
            "SUPPLIER" -> {
                supplierDao.getSupplierById(entityId)
                    ?: throw IllegalArgumentException("Supplier not found: $entityId")
            }
            else -> throw IllegalArgumentException("Invalid entityType: $entityType. Must be one of: CUSTOMER, SUPPLIER, FINANCIAL_ACCOUNT")
        }

        val existing = openingBalanceDao.getOpeningBalancesByEntitySync(entityType, entityId)
        if (existing.isNotEmpty()) {
            throw IllegalStateException(
                "Opening balance already recorded for this entity; use an Adjustment to correct it, not a second Opening Balance."
            )
        }

        val openingBalance = OpeningBalance(
            id = "ob_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            entityType = entityType,
            entityId = entityId,
            amount = amount,
            direction = direction,
            date = date,
            reason = reason,
            reference = reference,
            createdAt = System.currentTimeMillis()
        )

        openingBalanceDao.insertOpeningBalance(openingBalance)

        openingBalance
    }
}
