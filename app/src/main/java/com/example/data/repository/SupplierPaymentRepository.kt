package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.FinancialAccountDao
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.SupplierDao
import com.example.data.db.SupplierPayment
import com.example.data.db.SupplierPaymentDao
import com.example.data.db.TransactionDao
import com.example.data.db.toEntity
import com.example.data.db.toTransactionItem
import com.example.accounting.SupplierLedgerCalculator
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SupplierPaymentRepository(
    private val database: SmallStoreDatabase,
    private val supplierPaymentDao: SupplierPaymentDao = database.supplierPaymentDao(),
    private val supplierDao: SupplierDao = database.supplierDao(),
    private val financialAccountDao: FinancialAccountDao = database.financialAccountDao(),
    private val transactionDao: TransactionDao = database.transactionDao()
) {
    val allSupplierPayments: Flow<List<SupplierPayment>> = supplierPaymentDao.getAllPayments()

    fun getPaymentsBySupplierId(supplierId: String): Flow<List<SupplierPayment>> {
        return supplierPaymentDao.getPaymentsBySupplierId(supplierId)
    }

    suspend fun getPaymentsForSupplier(supplierId: String): List<SupplierPayment> {
        return supplierPaymentDao.getPaymentsBySupplierIdSync(supplierId)
    }

    suspend fun getPaymentsBySupplierIdSync(supplierId: String): List<SupplierPayment> {
        return supplierPaymentDao.getPaymentsBySupplierIdSync(supplierId)
    }

    suspend fun getAllSupplierPaymentsSync(): List<SupplierPayment> {
        return supplierPaymentDao.getAllPaymentsSync()
    }

    suspend fun getSupplierPaymentById(id: String): SupplierPayment? {
        return supplierPaymentDao.getPaymentById(id)
    }

    suspend fun recordSupplierPayment(
        supplierId: String,
        amount: Double,
        paymentDate: String? = null,
        paymentMethodId: String? = null,
        financialAccountId: String? = null,
        notes: String? = null,
        referenceNumber: String? = null
    ): SupplierPayment {
        require(supplierId.isNotBlank()) { "Supplier ID cannot be blank" }
        require(amount.isFinite() && amount > 0.0) { "Payment amount must be finite and greater than zero" }
        val supplier = supplierDao.getSupplierById(supplierId)
            ?: throw IllegalArgumentException("Supplier with ID $supplierId not found")

        val accId = financialAccountId ?: "acc_cash"
        var account = financialAccountDao.getAccountById(accId)
        if (account == null) {
            PaymentMethodRepository.ensureDefaultConfiguration(database)
            account = financialAccountDao.getAccountById(accId)
                ?: throw IllegalArgumentException("Financial account not found: $accId")
        }
        require(account.isActive) { "Financial account is inactive: $accId" }

        val method = PaymentMethodRepository.resolvePaymentMethodForOperation(
            database = database,
            explicitPaymentMethodId = paymentMethodId,
            financialAccount = account
        )

        val dateToUse = paymentDate?.takeIf { it.isNotBlank() }
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val payId = "sup_pay_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val refNum = referenceNumber?.takeIf { it.isNotBlank() }
            ?: "SPAY-${System.currentTimeMillis().toString().takeLast(6)}"

        val payment = SupplierPayment(
            id = payId,
            supplierId = supplierId,
            amount = amount,
            paymentDate = dateToUse,
            paymentMethodId = method.id,
            financialAccountId = account.id,
            referenceNumber = refNum,
            notes = notes,
            status = "ACTIVE"
        )

        database.withTransaction {
            financialAccountDao.getAccountById(accId)
                ?: throw IllegalArgumentException("Financial account not found: $accId")

            // Supplier advances and prepayments are fully supported.
            // Any payment exceeding current payable establishes or increases a supplier advance credit.

            supplierPaymentDao.insertPayment(payment)

            // Insert historical activity record (customerId null to prevent FK conflict with customers table)
            val txEntity = payment.toTransactionItem(supplier.name).copy(
                customerId = null,
                customerName = supplier.name
            ).toEntity()
            transactionDao.insertTransaction(txEntity)
        }

        return payment
    }
}
