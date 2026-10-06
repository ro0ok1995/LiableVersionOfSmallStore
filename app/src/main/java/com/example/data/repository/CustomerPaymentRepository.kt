package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.CustomerDao
import com.example.data.db.CustomerPayment
import com.example.data.db.CustomerPaymentDao
import com.example.data.db.FinancialAccountDao
import com.example.data.db.PaymentMethodDao
import com.example.data.db.SmallStoreDatabase
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class CustomerPaymentRepository(
    private val database: SmallStoreDatabase,
    private val customerPaymentDao: CustomerPaymentDao = database.customerPaymentDao(),
    private val customerDao: CustomerDao = database.customerDao(),
    private val paymentMethodDao: PaymentMethodDao = database.paymentMethodDao(),
    private val financialAccountDao: FinancialAccountDao = database.financialAccountDao()
) {
    val allCustomerPayments: Flow<List<CustomerPayment>> = customerPaymentDao.getAllPayments()

    fun getCustomerPaymentsByCustomerId(customerId: String): Flow<List<CustomerPayment>> {
        return customerPaymentDao.getPaymentsByCustomerId(customerId)
    }

    suspend fun getCustomerPaymentsByCustomerIdSync(customerId: String): List<CustomerPayment> {
        return customerPaymentDao.getPaymentsByCustomerIdSync(customerId)
    }

    suspend fun getAllCustomerPaymentsSync(): List<CustomerPayment> {
        return customerPaymentDao.getAllPaymentsSync()
    }

    suspend fun getCustomerPaymentById(id: String): CustomerPayment? {
        return customerPaymentDao.getPaymentById(id)
    }

    suspend fun insertCustomerPayment(payment: CustomerPayment) {
        customerPaymentDao.insertPayment(if (payment.financialAccountId == accountId) payment else payment.copy(financialAccountId = accountId))
    }

    suspend fun recordCustomerPayment(
        customerId: String,
        amount: Double,
        paymentMethodId: String,
        financialAccountId: String? = null,
        reference: String? = null,
        notes: String? = null
    ): CustomerPayment = database.withTransaction {
        require(amount.isFinite() && amount > 0.0) { "Customer payment amount must be finite and strictly positive, was: $amount" }
        require(paymentMethodId.isNotBlank()) { "Payment method is required" }

        val customer = customerDao.getCustomerById(customerId)
            ?: throw IllegalArgumentException("Customer not found: $customerId")

        val paymentMethod = paymentMethodDao.getPaymentMethodById(paymentMethodId)
            ?: throw IllegalArgumentException("Payment method not found: $paymentMethodId")
        require(paymentMethod.isActive) { "Payment method is inactive: $paymentMethodId" }

        val accountId = financialAccountId ?: "acc_cash"
        val account = financialAccountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Financial account not found: $accountId")
        require(account.isActive) { "Financial account is inactive: $accountId" }

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val paymentId = "pay_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

        val payment = CustomerPayment(
            id = paymentId,
            customerId = customerId,
            amount = amount,
            paymentMethodId = paymentMethodId,
            financialAccountId = accountId,
            reference = reference,
            transactionDate = todayDate,
            createdAt = System.currentTimeMillis(),
            notes = notes,
            status = "ACTIVE"
        )

        customerPaymentDao.insertPayment(if (payment.financialAccountId == accountId) payment else payment.copy(financialAccountId = accountId))

        customerDao.updateCustomer(
            customer.copy(
                hasRecentActivity = true,
                lastTransactionDate = todayDate
            )
        )

        payment
    }

    suspend fun recordCustomerPayment(payment: CustomerPayment): CustomerPayment = database.withTransaction {
        require(payment.amount > 0.0) { "Customer payment amount must be strictly positive, was: ${payment.amount}" }

        val customer = customerDao.getCustomerById(payment.customerId)
            ?: throw IllegalArgumentException("Customer not found: ${payment.customerId}")

        val paymentMethod = paymentMethodDao.getPaymentMethodById(payment.paymentMethodId)
            ?: throw IllegalArgumentException("Payment method not found: ${payment.paymentMethodId}")
        require(paymentMethod.isActive) { "Payment method is inactive: ${payment.paymentMethodId}" }

        val accountId = payment.financialAccountId ?: "acc_cash"
        val account = financialAccountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Financial account not found: $accountId")
        require(account.isActive) { "Financial account is inactive: $accountId" }

        customerPaymentDao.insertPayment(if (payment.financialAccountId == accountId) payment else payment.copy(financialAccountId = accountId))

        customerDao.updateCustomer(
            customer.copy(
                hasRecentActivity = true,
                lastTransactionDate = payment.transactionDate
            )
        )

        payment
    }
}
