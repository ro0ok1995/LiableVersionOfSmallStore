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
        customerPaymentDao.insertPayment(payment)
    }

    suspend fun recordCustomerPayment(
        customerId: String,
        amount: Double,
        paymentMethodId: String,
        financialAccountId: String? = null,
        reference: String? = null,
        notes: String? = null
    ): CustomerPayment = database.withTransaction {
        require(amount > 0.0) { "Customer payment amount must be strictly positive, was: $amount" }

        val customer = customerDao.getCustomerById(customerId)
            ?: throw IllegalArgumentException("Customer not found: $customerId")

        paymentMethodDao.getPaymentMethodById(paymentMethodId)
            ?: throw IllegalArgumentException("Payment method not found: $paymentMethodId")

        if (financialAccountId != null) {
            financialAccountDao.getAccountById(financialAccountId)
                ?: throw IllegalArgumentException("Financial account not found: $financialAccountId")
        }

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val paymentId = "pay_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

        val payment = CustomerPayment(
            id = paymentId,
            customerId = customerId,
            amount = amount,
            paymentMethodId = paymentMethodId,
            financialAccountId = financialAccountId,
            reference = reference,
            transactionDate = todayDate,
            createdAt = System.currentTimeMillis(),
            notes = notes,
            status = "ACTIVE"
        )

        customerPaymentDao.insertPayment(payment)

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

        paymentMethodDao.getPaymentMethodById(payment.paymentMethodId)
            ?: throw IllegalArgumentException("Payment method not found: ${payment.paymentMethodId}")

        if (payment.financialAccountId != null) {
            financialAccountDao.getAccountById(payment.financialAccountId)
                ?: throw IllegalArgumentException("Financial account not found: ${payment.financialAccountId}")
        }

        customerPaymentDao.insertPayment(payment)

        customerDao.updateCustomer(
            customer.copy(
                hasRecentActivity = true,
                lastTransactionDate = payment.transactionDate
            )
        )

        payment
    }
}
