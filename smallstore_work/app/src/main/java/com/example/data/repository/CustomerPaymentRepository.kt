package com.example.data.repository

import androidx.room.withTransaction
import com.example.accounting.CentralAccountingEngine
import com.example.data.db.CustomerDao
import com.example.data.db.CustomerPayment
import com.example.data.db.CustomerPaymentDao
import com.example.data.db.SaleDao
import com.example.data.db.OpeningBalanceDao
import com.example.data.db.AdjustmentDao
import com.example.data.db.SaleReturnDao
import com.example.data.db.RefundDao
import com.example.data.db.TransactionDao
import com.example.data.db.toModel
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
    private val financialAccountDao: FinancialAccountDao = database.financialAccountDao(),
    private val saleDao: SaleDao = database.saleDao(),
    private val openingBalanceDao: OpeningBalanceDao = database.openingBalanceDao(),
    private val adjustmentDao: AdjustmentDao = database.adjustmentDao(),
    private val saleReturnDao: SaleReturnDao = database.saleReturnDao(),
    private val refundDao: RefundDao = database.refundDao(),
    private val transactionDao: TransactionDao = database.transactionDao()
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

    /** Raw insertion is intentionally private so callers cannot bypass ledger validation. */
    private suspend fun insertCustomerPayment(payment: CustomerPayment) {
        customerPaymentDao.insertPayment(payment)
    }

    private suspend fun currentOutstandingBalance(customerId: String): Double {
        val sales = saleDao.getSalesByCustomerIdSync(customerId)
        val payments = customerPaymentDao.getPaymentsByCustomerIdSync(customerId)
        val opening = openingBalanceDao.getOpeningBalancesByEntitySync("CUSTOMER", customerId)
        val adjustments = adjustmentDao.getAdjustmentsByEntitySync("CUSTOMER", customerId)
        val returns = saleReturnDao.getReturnsByCustomerIdSync(customerId)
        val refunds = refundDao.getRefundsByCustomerIdSync(customerId)
        if (sales.isNotEmpty() || payments.isNotEmpty() || opening.isNotEmpty() || adjustments.isNotEmpty() || returns.isNotEmpty() || refunds.isNotEmpty()) {
            return CentralAccountingEngine.calculateCustomerBalance(customerId, sales, payments, opening, adjustments, returns, refunds).balance
        }
        return CentralAccountingEngine.calculateCustomerBalance(
            customerId, transactionDao.getTransactionsByCustomerIdSync(customerId).map { it.toModel() }
        ).balance
    }

    private fun requireWithinOutstanding(amount: Double, balance: Double, customerId: String) {
        // Customer advances and credit balances are supported: amount can exceed balance to create a customer credit
        require(amount.isFinite() && amount > 0.0) { "Customer payment amount must be finite and strictly positive" }
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

        require(customer.isArchived.not()) { "Cannot accept payment from an archived customer" }
        require(dateIsValid(todayDate)) { "Invalid payment date" }
        requireWithinOutstanding(amount, currentOutstandingBalance(customerId), customerId)

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

        insertCustomerPayment(payment)

        customerDao.updateCustomer(
            customer.copy(
                hasRecentActivity = true,
                lastTransactionDate = todayDate
            )
        )

        payment
    }

    private fun dateIsValid(value: String): Boolean = try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        format.parse(value) != null
    } catch (_: Exception) { false }

    suspend fun recordCustomerPayment(payment: CustomerPayment): CustomerPayment = database.withTransaction {
        require(payment.amount.isFinite() && payment.amount > 0.0) { "Customer payment amount must be finite and strictly positive, was: ${payment.amount}" }
        require(payment.transactionDate.isNotBlank() && dateIsValid(payment.transactionDate)) { "Invalid payment date: ${payment.transactionDate}" }

        val customer = customerDao.getCustomerById(payment.customerId)
            ?: throw IllegalArgumentException("Customer not found: ${payment.customerId}")

        require(!customer.isArchived) { "Cannot accept payment from an archived customer" }
        requireWithinOutstanding(payment.amount, currentOutstandingBalance(payment.customerId), payment.customerId)

        val paymentMethod = paymentMethodDao.getPaymentMethodById(payment.paymentMethodId)
            ?: throw IllegalArgumentException("Payment method not found: ${payment.paymentMethodId}")
        require(paymentMethod.isActive) { "Payment method is inactive: ${payment.paymentMethodId}" }

        val accountId = payment.financialAccountId ?: "acc_cash"
        val account = financialAccountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Financial account not found: $accountId")
        require(account.isActive) { "Financial account is inactive: $accountId" }

        val normalizedPayment = payment.copy(financialAccountId = accountId)
        insertCustomerPayment(normalizedPayment)

        customerDao.updateCustomer(
            customer.copy(
                hasRecentActivity = true,
                lastTransactionDate = payment.transactionDate
            )
        )

        normalizedPayment
    }
}
