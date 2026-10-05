package com.example.accounting

import com.example.data.db.Adjustment
import com.example.data.db.CustomerPayment
import com.example.data.db.Expense
import com.example.data.db.OpeningBalance
import com.example.data.db.Purchase
import com.example.data.db.Refund
import com.example.data.db.Sale
import com.example.data.db.SupplierPayment
import com.example.model.OperationStatus

/**
 * Supported entry types for Financial Account Ledgers.
 */
enum class FinancialAccountEntryType {
    OPENING_BALANCE,
    SALE_CASH_INFLOW,
    CUSTOMER_PAYMENT,
    SUPPLIER_PAYMENT,
    PURCHASE_CASH_OUTFLOW,
    EXPENSE,
    REFUND,
    ADJUSTMENT
}

/**
 * Individual chronological ledger entry for a financial account.
 */
data class FinancialAccountLedgerEntry(
    val id: String,
    val financialAccountId: String,
    val date: String,
    val entryType: FinancialAccountEntryType,
    val inflow: Double,
    val outflow: Double,
    val runningBalance: Double,
    val referenceId: String?,
    val description: String,
    val operationStatus: OperationStatus
)

/**
 * Complete statement for a financial account across an optional date range.
 */
data class FinancialAccountStatement(
    val financialAccountId: String,
    val startDate: String?,
    val endDate: String?,
    val openingBalance: Double,
    val totalInflows: Double,
    val totalOutflows: Double,
    val closingBalance: Double,
    val entries: List<FinancialAccountLedgerEntry>
)

/**
 * Pure domain calculator for Financial Account Statements & Ledgers.
 *
 * Invariants:
 * 1. ZERO UI or Android dependencies (pure Kotlin).
 * 2. Balances reconcile exactly with CentralAccountingEngine.calculateFinancialAccountBalance.
 * 3. REVERSED operations have 0.0 active accounting effect while remaining auditable in the entry list.
 * 4. Multi-account isolation: only operations matching financialAccountId affect the ledger.
 * 5. Opening balance participates exactly once and is not counted as an in-period movement.
 */
object FinancialAccountLedgerCalculator {

    /**
     * Single authoritative final-balance entry point for financial accounts.
     * The statement builder owns the movement formula; callers must not duplicate it.
     */
    fun calculateAccountBalance(
        accountId: String,
        sales: List<Sale> = emptyList(),
        customerPayments: List<CustomerPayment> = emptyList(),
        openingBalances: List<OpeningBalance> = emptyList(),
        adjustments: List<Adjustment> = emptyList(),
        refunds: List<Refund> = emptyList(),
        purchases: List<Purchase> = emptyList(),
        supplierPayments: List<SupplierPayment> = emptyList(),
        expenses: List<Expense> = emptyList()
    ): Double = buildFinancialAccountStatement(
        accountId = accountId,
        startDate = null,
        endDate = null,
        sales = sales,
        customerPayments = customerPayments,
        openingBalances = openingBalances,
        adjustments = adjustments,
        refunds = refunds,
        purchases = purchases,
        supplierPayments = supplierPayments,
        expenses = expenses
    ).closingBalance

    private data class RawFinancialItem(
        val id: String,
        val date: String,
        val createdAt: Long,
        val type: FinancialAccountEntryType,
        val inflow: Double,
        val outflow: Double,
        val referenceId: String?,
        val description: String,
        val status: OperationStatus
    )

    fun buildFinancialAccountStatement(
        accountId: String,
        startDate: String? = null,
        endDate: String? = null,
        sales: List<Sale> = emptyList(),
        customerPayments: List<CustomerPayment> = emptyList(),
        openingBalances: List<OpeningBalance> = emptyList(),
        adjustments: List<Adjustment> = emptyList(),
        refunds: List<Refund> = emptyList(),
        purchases: List<Purchase> = emptyList(),
        supplierPayments: List<SupplierPayment> = emptyList(),
        expenses: List<Expense> = emptyList()
    ): FinancialAccountStatement {
        val rawItems = mutableListOf<RawFinancialItem>()

        // 1. Sales cash inflow (attributed to sale.financialAccountId ?: "acc_cash")
        for (sale in sales) {
            val destinationAccount = sale.financialAccountId ?: "acc_cash"
            if (destinationAccount != accountId || sale.paidAmount <= 0.0) continue
            val status = if (sale.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val inflow = if (status == OperationStatus.ACTIVE) sale.paidAmount else 0.0
            rawItems.add(
                RawFinancialItem(
                    id = sale.id,
                    date = sale.transactionDate,
                    createdAt = sale.createdAt,
                    type = FinancialAccountEntryType.SALE_CASH_INFLOW,
                    inflow = inflow,
                    outflow = 0.0,
                    referenceId = sale.invoiceNumber,
                    description = "مبيعات نقدية فاتورة ${sale.invoiceNumber}",
                    status = status
                )
            )
        }

        // 2. Customer Payments (inflow)
        for (cp in customerPayments) {
            val destinationAccount = cp.financialAccountId ?: "acc_cash"
            if (destinationAccount != accountId) continue
            val status = if (cp.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val inflow = if (status == OperationStatus.ACTIVE) cp.amount else 0.0
            rawItems.add(
                RawFinancialItem(
                    id = cp.id,
                    date = cp.transactionDate,
                    createdAt = cp.createdAt,
                    type = FinancialAccountEntryType.CUSTOMER_PAYMENT,
                    inflow = inflow,
                    outflow = 0.0,
                    referenceId = cp.reference ?: cp.id,
                    description = cp.notes?.takeIf { it.isNotBlank() } ?: "سداد دفعة من عميل",
                    status = status
                )
            )
        }

        // 3. Supplier Payments (outflow)
        for (sp in supplierPayments) {
            val sourceAccount = sp.financialAccountId ?: "acc_cash"
            if (sourceAccount != accountId) continue
            val status = if (sp.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val outflow = if (status == OperationStatus.ACTIVE) sp.amount else 0.0
            rawItems.add(
                RawFinancialItem(
                    id = sp.id,
                    date = sp.paymentDate,
                    createdAt = sp.createdAt,
                    type = FinancialAccountEntryType.SUPPLIER_PAYMENT,
                    inflow = 0.0,
                    outflow = outflow,
                    referenceId = sp.referenceNumber ?: sp.id,
                    description = sp.notes?.takeIf { it.isNotBlank() } ?: "سداد للمورد",
                    status = status
                )
            )
        }

        // 4. Purchases cash outflow
        for (p in purchases) {
            val sourceAccount = p.financialAccountId ?: "acc_cash"
            if (sourceAccount != accountId || p.paidAmount <= 0.0) continue
            val status = if (p.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val outflow = if (status == OperationStatus.ACTIVE) p.paidAmount else 0.0
            rawItems.add(
                RawFinancialItem(
                    id = p.id,
                    date = p.purchaseDate,
                    createdAt = p.createdAt,
                    type = FinancialAccountEntryType.PURCHASE_CASH_OUTFLOW,
                    inflow = 0.0,
                    outflow = outflow,
                    referenceId = p.invoiceNumber,
                    description = "سداد نقدي لمشتريات فاتورة ${p.invoiceNumber}",
                    status = status
                )
            )
        }

        // 5. Expenses (outflow)
        for (exp in expenses) {
            val sourceAccount = exp.financialAccountId ?: "acc_cash"
            if (sourceAccount != accountId) continue
            val status = if (exp.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val outflow = if (status == OperationStatus.ACTIVE) exp.amount else 0.0
            rawItems.add(
                RawFinancialItem(
                    id = exp.id,
                    date = exp.date,
                    createdAt = exp.createdAt,
                    type = FinancialAccountEntryType.EXPENSE,
                    inflow = 0.0,
                    outflow = outflow,
                    referenceId = exp.id,
                    description = exp.description.ifBlank { "مصروف تشغيلي" },
                    status = status
                )
            )
        }

        // 6. Refunds (outflow)
        for (ref in refunds) {
            val sourceAccount = ref.financialAccountId ?: "acc_cash"
            if (sourceAccount != accountId) continue
            val status = if (ref.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val outflow = if (status == OperationStatus.ACTIVE) ref.amount else 0.0
            rawItems.add(
                RawFinancialItem(
                    id = ref.id,
                    date = ref.refundDate,
                    createdAt = ref.createdAt,
                    type = FinancialAccountEntryType.REFUND,
                    inflow = 0.0,
                    outflow = outflow,
                    referenceId = ref.saleId ?: ref.id,
                    description = ref.reason.ifBlank { "استرداد نقدي لعميل" },
                    status = status
                )
            )
        }

        // 7. Adjustments (inflow if DEBIT, outflow if CREDIT)
        for (adj in adjustments) {
            if (adj.entityType != "FINANCIAL_ACCOUNT" || adj.entityId != accountId) continue
            val status = if (adj.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val isDebit = adj.direction == "DEBIT"
            val inflow = if (status == OperationStatus.ACTIVE && isDebit) adj.amount else 0.0
            val outflow = if (status == OperationStatus.ACTIVE && !isDebit) adj.amount else 0.0
            rawItems.add(
                RawFinancialItem(
                    id = adj.id,
                    date = adj.date,
                    createdAt = adj.createdAt,
                    type = FinancialAccountEntryType.ADJUSTMENT,
                    inflow = inflow,
                    outflow = outflow,
                    referenceId = adj.reference ?: adj.id,
                    description = adj.reason.ifBlank { "تعديل رصيد حساب" },
                    status = status
                )
            )
        }

        // Sort all operational raw items chronologically
        rawItems.sortWith(compareBy<RawFinancialItem> { it.date }.thenBy { it.createdAt }.thenBy { it.id })

        // Partition pre-period vs in-period
        var openingBalance = 0.0

        // Opening Balance entities for this financial account
        for (ob in openingBalances) {
            if (ob.entityType != "FINANCIAL_ACCOUNT" || ob.entityId != accountId) continue
            val netOb = if (ob.direction == "DEBIT") ob.amount else -ob.amount
            openingBalance += netOb
        }

        val inPeriodItems = mutableListOf<RawFinancialItem>()

        for (item in rawItems) {
            if (startDate != null && item.date < startDate) {
                if (item.status == OperationStatus.ACTIVE) {
                    openingBalance += (item.inflow - item.outflow)
                }
            } else if (endDate != null && item.date > endDate) {
                // Beyond period, ignore
                continue
            } else {
                inPeriodItems.add(item)
            }
        }

        // Calculate running balance and entries
        var runningBalance = openingBalance
        val entries = ArrayList<FinancialAccountLedgerEntry>(inPeriodItems.size)
        var totalInflows = 0.0
        var totalOutflows = 0.0

        for (item in inPeriodItems) {
            if (item.status == OperationStatus.ACTIVE) {
                runningBalance += (item.inflow - item.outflow)
                totalInflows += item.inflow
                totalOutflows += item.outflow
            }
            entries.add(
                FinancialAccountLedgerEntry(
                    id = item.id,
                    financialAccountId = accountId,
                    date = item.date,
                    entryType = item.type,
                    inflow = item.inflow,
                    outflow = item.outflow,
                    runningBalance = runningBalance,
                    referenceId = item.referenceId,
                    description = item.description,
                    operationStatus = item.status
                )
            )
        }

        val closingBalance = openingBalance + totalInflows - totalOutflows

        return FinancialAccountStatement(
            financialAccountId = accountId,
            startDate = startDate,
            endDate = endDate,
            openingBalance = openingBalance,
            totalInflows = totalInflows,
            totalOutflows = totalOutflows,
            closingBalance = closingBalance,
            entries = entries
        )
    }

    fun buildFinancialAccountLedger(
        accountId: String,
        startDate: String? = null,
        endDate: String? = null,
        sales: List<Sale> = emptyList(),
        customerPayments: List<CustomerPayment> = emptyList(),
        openingBalances: List<OpeningBalance> = emptyList(),
        adjustments: List<Adjustment> = emptyList(),
        refunds: List<Refund> = emptyList(),
        purchases: List<Purchase> = emptyList(),
        supplierPayments: List<SupplierPayment> = emptyList(),
        expenses: List<Expense> = emptyList()
    ): List<FinancialAccountLedgerEntry> {
        return buildFinancialAccountStatement(
            accountId = accountId,
            startDate = startDate,
            endDate = endDate,
            sales = sales,
            customerPayments = customerPayments,
            openingBalances = openingBalances,
            adjustments = adjustments,
            refunds = refunds,
            purchases = purchases,
            supplierPayments = supplierPayments,
            expenses = expenses
        ).entries
    }
}
