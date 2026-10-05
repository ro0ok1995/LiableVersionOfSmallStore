package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.CustomerDao
import com.example.data.db.Refund
import com.example.data.db.RefundDao
import com.example.data.db.SaleDao
import com.example.data.db.SaleReturnDao
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.TransactionDao
import com.example.data.db.toEntity
import com.example.data.db.toTransactionItem
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RefundRepository(
    private val database: SmallStoreDatabase,
    private val refundDao: RefundDao = database.refundDao(),
    private val saleReturnDao: SaleReturnDao = database.saleReturnDao(),
    private val saleDao: SaleDao = database.saleDao(),
    private val customerDao: CustomerDao = database.customerDao(),
    private val transactionDao: TransactionDao = database.transactionDao()
) {
    val allRefunds: Flow<List<Refund>> = refundDao.getAllRefunds()

    suspend fun getAllRefundsSync(): List<Refund> = refundDao.getAllRefundsSync()

    suspend fun getRefundById(id: String): Refund? = refundDao.getRefundById(id)

    suspend fun getRefundsForSale(saleId: String): List<Refund> = refundDao.getRefundsBySaleId(saleId)

    suspend fun getRefundsForReturn(returnId: String): List<Refund> = refundDao.getRefundsByReturnId(returnId)

    fun getRefundsByCustomerId(customerId: String): Flow<List<Refund>> = refundDao.getRefundsByCustomerId(customerId)

    suspend fun getRefundsByCustomerIdSync(customerId: String): List<Refund> = refundDao.getRefundsByCustomerIdSync(customerId)

    /**
     * Phase 8: Records a standalone [Refund] for an existing [SaleReturn].
     */
    suspend fun recordRefund(
        saleReturnId: String,
        amount: Double,
        financialAccountId: String? = null,
        paymentMethodId: String? = null,
        reason: String,
        refundDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    ): Refund {
        require(saleReturnId.isNotBlank()) { "Sale return ID cannot be blank" }
        require(amount > 0.0) { "Refund amount must be greater than zero" }
        require(reason.isNotBlank()) { "Refund reason cannot be blank" }

        val saleReturn = saleReturnDao.getReturnById(saleReturnId)
            ?: throw IllegalArgumentException("Sale return not found with id: $saleReturnId")

        if (saleReturn.status == "REVERSED") {
            throw IllegalStateException("Cannot issue refund for reversed return: $saleReturnId")
        }

        val sale = saleDao.getSaleById(saleReturn.saleId)
            ?: throw IllegalArgumentException("Original sale not found: ${saleReturn.saleId}")

        if (sale.status == "REVERSED") {
            throw IllegalStateException("Cannot issue refund for reversed sale: ${sale.id}")
        }

        val existingRefunds = refundDao.getRefundsBySaleId(sale.id).filter { it.status != "REVERSED" }
        val totalAlreadyRefunded = existingRefunds.sumOf { it.amount }
        val maxRefundEligible = (sale.paidAmount - totalAlreadyRefunded).coerceAtLeast(0.0)

        require(amount <= maxRefundEligible + 0.001) {
            "Refund amount ($amount) exceeds amount eligible for cash/bank refund ($maxRefundEligible)"
        }

        val refundId = UUID.randomUUID().toString()
        val refund = Refund(
            id = refundId,
            saleReturnId = saleReturn.id,
            saleId = sale.id,
            customerId = sale.customerId,
            amount = amount,
            paymentMethodId = paymentMethodId,
            financialAccountId = financialAccountId,
            refundDate = refundDate,
            reason = reason.trim(),
            status = "ACTIVE"
        )

        database.withTransaction {
            refundDao.insertRefund(refund)

            val resolvedCustomerName = if (!sale.customerId.isNullOrBlank()) {
                customerDao.getCustomerById(sale.customerId)?.customerName ?: ""
            } else ""

            val refundTx = refund.toTransactionItem(resolvedCustomerName, sale.invoiceNumber).toEntity()
            transactionDao.insertTransaction(refundTx)

            if (!sale.customerId.isNullOrBlank()) {
                val customer = customerDao.getCustomerById(sale.customerId)
                if (customer != null) {
                    customerDao.updateCustomer(
                        customer.copy(
                            hasRecentActivity = true,
                            lastTransactionDate = refundDate
                        )
                    )
                }
            }
        }

        return refund
    }
}
