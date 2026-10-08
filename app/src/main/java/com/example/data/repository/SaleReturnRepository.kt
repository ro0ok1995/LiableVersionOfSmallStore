package com.example.data.repository

import androidx.room.withTransaction
import com.example.accounting.FinancialReportCalculator
import com.example.accounting.InventoryMovementType
import com.example.data.db.CustomerDao
import com.example.data.db.Refund
import com.example.data.db.RefundDao
import com.example.data.db.SaleDao
import com.example.data.db.SaleReturn
import com.example.data.db.SaleReturnDao
import com.example.data.db.SaleReturnLine
import com.example.data.db.SaleReturnLineDao
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.StockMovementDao
import com.example.data.db.StockMovementEntity as PersistentStockMovement
import com.example.data.db.TransactionDao
import com.example.data.db.TransactionItemLineDao
import com.example.data.db.TransactionItemLineEntity
import com.example.data.db.toEntity
import com.example.data.db.toTransactionItem
import com.example.model.RefundRequest
import com.example.model.SaleReturnLineRequest
import com.example.model.SaleReturnResult
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SaleReturnRepository(
    private val database: SmallStoreDatabase,
    private val saleReturnDao: SaleReturnDao = database.saleReturnDao(),
    private val saleReturnLineDao: SaleReturnLineDao = database.saleReturnLineDao(),
    private val refundDao: RefundDao = database.refundDao(),
    private val saleDao: SaleDao = database.saleDao(),
    private val customerDao: CustomerDao = database.customerDao(),
    private val transactionDao: TransactionDao = database.transactionDao(),
    private val transactionItemLineDao: TransactionItemLineDao = database.transactionItemLineDao(),
    private val stockMovementDao: StockMovementDao = database.stockMovementDao()
) {
    val allSaleReturns: Flow<List<SaleReturn>> = saleReturnDao.getAllReturns()
    val allRefunds: Flow<List<Refund>> = refundDao.getAllRefunds()

    suspend fun getSaleReturnById(id: String): SaleReturn? = saleReturnDao.getReturnById(id)

    suspend fun getReturnLines(returnId: String): List<SaleReturnLine> = saleReturnLineDao.getLinesForReturn(returnId)

    suspend fun getReturnsForSale(saleId: String): List<SaleReturn> = saleReturnDao.getReturnsBySaleIdSync(saleId)

    suspend fun getRefundsForSale(saleId: String): List<Refund> = refundDao.getRefundsBySaleId(saleId)

    suspend fun getRefundsForReturn(returnId: String): List<Refund> = refundDao.getRefundsByReturnId(returnId)

    suspend fun getRemainingReturnableQuantities(saleId: String): Map<String, Int> {
        val saleLines = saleDao.getSaleLinesBySaleId(saleId)
        val existingReturns = saleReturnDao.getReturnsBySaleIdSync(saleId).filter { it.status != "REVERSED" }
        val activeReturnIds = existingReturns.map { it.id }.toSet()
        val allExistingLines = saleReturnLineDao.getReturnLinesForSaleLines(saleLines.map { it.id })
        val activeExistingLines = allExistingLines.filter { it.saleReturnId in activeReturnIds }
        val alreadyReturnedMap = activeExistingLines.groupBy { it.saleLineId }
            .mapValues { (_, list) -> list.sumOf { it.quantity } }
        return saleLines.associate { it.id to (it.quantity - (alreadyReturnedMap[it.id] ?: 0)).coerceAtLeast(0) }
    }

    suspend fun recordSaleReturn(
        saleId: String,
        returnLines: List<SaleReturnLineRequest>,
        reason: String,
        reasonCode: String = "other",
        reasonLabelSnapshot: String = reason,
        returnDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
        refundRequest: RefundRequest? = null
    ): SaleReturnResult {
        require(saleId.isNotBlank()) { "Sale ID cannot be blank" }
        require(reason.isNotBlank()) { "Return reason cannot be blank" }
        require(returnDate.isNotBlank()) { "Return date cannot be blank" }
        require(returnLines.isNotEmpty()) { "Return must contain at least one item line" }

        val sale = saleDao.getSaleById(saleId)
            ?: throw IllegalArgumentException("Sale not found with id: $saleId")

        if (sale.status == "REVERSED") {
            throw IllegalStateException("Cannot return items from reversed sale: $saleId")
        }

        val saleLines = saleDao.getSaleLinesBySaleId(saleId)
        val lineMap = saleLines.associateBy { it.id }

        // Prevent duplicate lines in same request
        val requestedLineIds = returnLines.map { it.saleLineId }
        require(requestedLineIds.distinct().size == requestedLineIds.size) {
            "Duplicate return lines for the same item are not allowed"
        }

        // Calculate already returned quantities from active returns
        val existingReturns = saleReturnDao.getReturnsBySaleIdSync(saleId).filter { it.status != "REVERSED" }
        val activeReturnIds = existingReturns.map { it.id }.toSet()
        val allExistingLines = saleReturnLineDao.getReturnLinesForSaleLines(saleLines.map { it.id })
        val activeExistingLines = allExistingLines.filter { it.saleReturnId in activeReturnIds }
        val alreadyReturnedMap = activeExistingLines.groupBy { it.saleLineId }
            .mapValues { (_, list) -> list.sumOf { it.quantity } }

        val returnId = UUID.randomUUID().toString()
        val constructedLines = mutableListOf<SaleReturnLine>()

        for (req in returnLines) {
            require(req.quantity > 0) { "Return quantity must be greater than zero, got: ${req.quantity}" }
            val originalLine = lineMap[req.saleLineId]
                ?: throw IllegalArgumentException("Sale line ${req.saleLineId} does not belong to sale $saleId")

            val alreadyReturned = alreadyReturnedMap[req.saleLineId] ?: 0
            val remainingReturnable = originalLine.quantity - alreadyReturned

            require(req.quantity <= remainingReturnable) {
                "Cannot return ${req.quantity} of '${originalLine.productNameSnapshot}'; only $remainingReturnable remaining returnable (sold: ${originalLine.quantity}, already returned: $alreadyReturned)"
            }

            val lineSubtotal = req.quantity * originalLine.unitPrice
            constructedLines.add(
                SaleReturnLine(
                    id = UUID.randomUUID().toString(),
                    saleReturnId = returnId,
                    saleLineId = originalLine.id,
                    productId = originalLine.productId,
                    productNameSnapshot = originalLine.productNameSnapshot,
                    quantity = req.quantity,
                    unitPrice = originalLine.unitPrice,
                    costPriceAtReturn = originalLine.costPriceAtSale,
                    subtotal = lineSubtotal
                )
            )
        }

        val totalReturnAmount = constructedLines.sumOf { it.subtotal }
        require(totalReturnAmount > 0.0) { "Total return amount ($totalReturnAmount) must be greater than zero" }

        // Validate refund request if provided
        var constructedRefund: Refund? = null
        if (refundRequest != null) {
            require(refundRequest.amount > 0.0) { "Refund amount (${refundRequest.amount}) must be greater than zero" }
            require(refundRequest.amount <= totalReturnAmount + 0.001) {
                "Refund amount (${refundRequest.amount}) cannot exceed return amount ($totalReturnAmount)"
            }

            val existingRefunds = refundDao.getRefundsBySaleId(saleId).filter { it.status != "REVERSED" }
            val totalAlreadyRefunded = existingRefunds.sumOf { it.amount }
            val maxRefundEligible = (sale.paidAmount - totalAlreadyRefunded).coerceAtLeast(0.0)

            require(refundRequest.amount <= maxRefundEligible + 0.001) {
                "Refund amount (${refundRequest.amount}) exceeds amount eligible for cash/bank refund ($maxRefundEligible)"
            }

            val effectivePaymentMethodId = refundRequest.paymentMethodId ?: "pm_cash"
            val paymentMethod = database.paymentMethodDao().getPaymentMethodById(effectivePaymentMethodId)
                ?: throw IllegalArgumentException("Payment method not found: $effectivePaymentMethodId")
            require(paymentMethod.isActive) { "Payment method is inactive: $effectivePaymentMethodId" }
            val effectiveFinancialAccountId = refundRequest.financialAccountId ?: "acc_cash"
            val financialAccount = database.financialAccountDao().getAccountById(effectiveFinancialAccountId)
                ?: throw IllegalArgumentException("Financial account not found: $effectiveFinancialAccountId")
            require(financialAccount.isActive) { "Financial account is inactive: $effectiveFinancialAccountId" }

            constructedRefund = Refund(
                id = UUID.randomUUID().toString(),
                saleReturnId = returnId,
                saleId = sale.id,
                customerId = sale.customerId,
                amount = refundRequest.amount,
                paymentMethodId = effectivePaymentMethodId,
                financialAccountId = effectiveFinancialAccountId,
                refundDate = returnDate,
                reason = refundRequest.reason?.trim()?.ifBlank { null } ?: "استرداد نقدي لمرتجع ${sale.invoiceNumber}",
                status = "ACTIVE"
            )
        }

        val saleReturn = SaleReturn(
            id = returnId,
            saleId = sale.id,
            customerId = sale.customerId,
            returnDate = returnDate,
            reason = reason.trim(),
            reasonCode = reasonCode.trim().ifBlank { "other" },
            reasonLabelSnapshot = reasonLabelSnapshot.trim().ifBlank { reason.trim() },
            amount = totalReturnAmount,
            status = "ACTIVE"
        )

        val cogsReversed = constructedLines.sumOf { it.cogsReversed }
        val grossProfitCorrection = FinancialReportCalculator.calculateGrossProfit(totalReturnAmount, cogsReversed)

        database.withTransaction {
            saleReturnDao.insertReturn(saleReturn)
            saleReturnLineDao.insertReturnLines(constructedLines)

            val resolvedCustomerName = if (!sale.customerId.isNullOrBlank()) {
                customerDao.getCustomerById(sale.customerId)?.customerName ?: ""
            } else ""

            val returnTx = saleReturn.toTransactionItem(resolvedCustomerName, sale.invoiceNumber).toEntity()
            transactionDao.insertTransaction(returnTx)

            val txLines = constructedLines.map { rl ->
                TransactionItemLineEntity(
                    transactionId = returnId,
                    productId = rl.productId,
                    productNameSnapshot = rl.productNameSnapshot,
                    quantity = rl.quantity,
                    unitPrice = rl.unitPrice,
                    costPrice = rl.costPriceAtReturn,
                    subtotal = rl.subtotal
                )
            }
            transactionItemLineDao.insertLines(txLines)

            // Phase 11: Persist SALE_RETURN_IN StockMovement for each returned line
            val stockMovements = constructedLines.mapNotNull { rl ->
                if (!rl.productId.isNullOrBlank()) {
                    PersistentStockMovement(
                        id = "sm_${rl.id}",
                        productId = rl.productId,
                        productNameSnapshot = rl.productNameSnapshot,
                        transactionId = returnId,
                        lineId = rl.id,
                        date = saleReturn.returnDate,
                        timestamp = System.currentTimeMillis(),
                        movementType = InventoryMovementType.SALE_RETURN_IN.name,
                        quantityIn = rl.quantity,
                        quantityOut = 0,
                        unitCost = rl.costPriceAtReturn,
                        reference = sale.invoiceNumber,
                        referenceType = "SALE_RETURN",
                        referenceId = returnId,
                        status = saleReturn.status
                    )
                } else null
            }
            if (stockMovements.isNotEmpty()) {
                stockMovementDao.insertStockMovements(stockMovements)
            }

            if (constructedRefund != null) {
                refundDao.insertRefund(constructedRefund)
                val refundTx = constructedRefund.toTransactionItem(resolvedCustomerName, sale.invoiceNumber).toEntity()
                transactionDao.insertTransaction(refundTx)
            }

            if (!sale.customerId.isNullOrBlank()) {
                val customer = customerDao.getCustomerById(sale.customerId)
                if (customer != null) {
                    customerDao.updateCustomer(
                        customer.copy(
                            hasRecentActivity = true,
                            lastTransactionDate = returnDate
                        )
                    )
                }
            }
        }

        return SaleReturnResult(
            saleReturn = saleReturn,
            lines = constructedLines,
            refund = constructedRefund,
            cogsReversed = cogsReversed,
            grossProfitCorrection = grossProfitCorrection
        )
    }
}
