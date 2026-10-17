package com.example.accounting

import com.example.model.AnalyticsExportDataPreparer
import com.example.model.CustomerAccount
import com.example.model.OperationStatus
import com.example.model.PeriodFilter
import com.example.model.SaleType
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.ui.components.BreakdownChartType
import com.example.util.ReportExporter
import com.example.viewmodel.AnalysisCenterViewModel
import com.example.viewmodel.DebtAgingUtils
import com.example.viewmodel.StatementTxFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 4 — Step 6: Final Cross-Surface Accounting Reconciliation Audit Test.
 *
 * Verifies that the deterministic test dataset specified in Phase 4 Step 6
 * reconciles identically across all accounting and reporting surfaces:
 * - Customer statement (ViewModel & Exporter)
 * - FinancialReportCalculator
 * - Analytics (AnalyticsExportDataPreparer)
 * - Debt aging (DebtAgingUtils)
 * - CSV export
 * - PDF statement generation logic
 * - TXT export
 */
class CrossSurfaceReconciliationFinalAuditTest {

    private val customerId = "cust_audit_deterministic"
    private val customer = CustomerAccount(
        id = customerId,
        customerName = "عميل التدقيق النهائي",
        balance = 170.0,
        totalDebt = 170.0,
        phone = "0509999999"
    )

    private fun createTx(
        id: String,
        amount: Double,
        activityType: String,
        isCredit: Boolean,
        date: String,
        relativeTime: String = "10:00",
        notes: String = "",
        transactionType: TransactionType? = null,
        saleType: SaleType? = null,
        operationStatus: OperationStatus? = OperationStatus.ACTIVE,
        paidAmount: Double = 0.0,
        creditAmount: Double = 0.0
    ): TransactionItem {
        return TransactionItem(
            id = id,
            title = activityType,
            customerNameSnapshot = customer.customerName,
            activityType = activityType,
            amount = amount,
            isCredit = isCredit,
            date = date,
            relativeTime = relativeTime,
            notes = notes,
            customerId = customerId,
            transactionType = transactionType,
            saleType = saleType,
            operationStatus = operationStatus,
            paidAmount = paidAmount,
            creditAmount = creditAmount
        )
    }

    @Test
    fun testDeterministicCrossSurfaceReconciliation() {
        val today = LocalDate.of(2026, 9, 30)
        val periodStart = LocalDate.of(2026, 9, 10)
        val periodEnd = LocalDate.of(2026, 9, 30)

        // 1. Transactions before selected reporting period:
        // 1a. Historical fully-paid credit sale: 50 credit sale, 50 payment
        val txPriorPaidSale = createTx(
            id = "tx_prior_paid_sale",
            amount = 50.0,
            activityType = "شراء آجل مسدد",
            isCredit = true,
            date = "2026-09-01 08:00",
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            creditAmount = 50.0
        )
        val txPriorFullPay = createTx(
            id = "tx_prior_full_pay",
            amount = 50.0,
            activityType = "تسديد كامل",
            isCredit = false,
            date = "2026-09-02 09:00",
            transactionType = TransactionType.CUSTOMER_PAYMENT
        )

        // 1b. Prior active credit sale (+100) and partial payment (-40) -> net opening balance = +60
        val txPriorCreditSale = createTx(
            id = "tx_prior_credit_sale",
            amount = 100.0,
            activityType = "شراء آجل سابق",
            isCredit = true,
            date = "2026-09-03 10:00",
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            creditAmount = 100.0
        )
        val txPriorPartialPay = createTx(
            id = "tx_prior_partial_pay",
            amount = 40.0,
            activityType = "تسديد جزئي سابق",
            isCredit = false,
            date = "2026-09-05 11:00",
            transactionType = TransactionType.CUSTOMER_PAYMENT
        )

        // 2. Transactions INSIDE selected reporting period (2026-09-10 to 2026-09-30):
        // 2a. Multiple transactions on the same date with different timestamps:
        // Transaction A on 2026-09-12 10:00 -> Cash-only sale: 100 paid, 0 credit
        val txCashOnly = createTx(
            id = "tx_inside_cash_only",
            amount = 100.0,
            activityType = "بيع نقدي كاش",
            isCredit = false,
            date = "2026-09-12 10:00",
            transactionType = TransactionType.SALE,
            saleType = SaleType.CASH,
            paidAmount = 100.0,
            creditAmount = 0.0
        )
        // Transaction B on 2026-09-12 14:00 -> Credit-only sale: 100 credit
        val txCreditOnly = createTx(
            id = "tx_inside_credit_only",
            amount = 100.0,
            activityType = "شراء آجل",
            isCredit = true,
            date = "2026-09-12 14:00",
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            paidAmount = 0.0,
            creditAmount = 100.0
        )

        // 2b. Mixed sale: 100 total, 60 paid, 40 credit
        val txMixed = createTx(
            id = "tx_inside_mixed",
            amount = 100.0,
            activityType = "بيع مركب",
            isCredit = true,
            date = "2026-09-15 09:00",
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED,
            paidAmount = 60.0,
            creditAmount = 40.0
        )

        // 2c. Customer payment: 30
        val txPayment = createTx(
            id = "tx_inside_payment",
            amount = 30.0,
            activityType = "تسديد دفعة",
            isCredit = false,
            date = "2026-09-16 11:00",
            transactionType = TransactionType.CUSTOMER_PAYMENT
        )

        // 2d. Reversed credit sale: 200 credit
        val txReversed = createTx(
            id = "tx_inside_reversed",
            amount = 200.0,
            activityType = "شراء آجل ملغي",
            isCredit = true,
            date = "2026-09-18 12:00",
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            creditAmount = 200.0,
            operationStatus = OperationStatus.REVERSED
        )

        val allTransactions = listOf(
            txPriorPaidSale,
            txPriorFullPay,
            txPriorCreditSale,
            txPriorPartialPay,
            txCashOnly,
            txCreditOnly,
            txMixed,
            txPayment,
            txReversed
        )

        val insidePeriodTransactions = listOf(
            txCashOnly,
            txCreditOnly,
            txMixed,
            txPayment,
            txReversed
        )

        // --- Surface 1: CustomerLedgerCalculator (Source of Truth) ---
        val fullLedger = CustomerLedgerCalculator.calculateCustomerBalance(customerId, allTransactions)
        assertEquals("Total credit sales in ledger", 290.0, fullLedger.totalCreditSales, 0.001) // 50 + 100 + 100 + 40
        assertEquals("Total payments in ledger", 120.0, fullLedger.totalPayments, 0.001) // 50 + 40 + 30
        assertEquals("Net ledger balance", 170.0, fullLedger.balance, 0.001)

        // --- Surface 2: FinancialReportCalculator (Domain Core) ---
        val periodReport = FinancialReportCalculator.calculate(insidePeriodTransactions)
        assertEquals("Cash sales in period", 160.0, periodReport.cashSales, 0.001) // 100 cash + 60 mixed
        assertEquals("Credit sales in period", 140.0, periodReport.creditSales, 0.001) // 100 credit + 40 mixed
        assertEquals("Total sales in period", 300.0, periodReport.totalSales, 0.001) // 160 + 140 (reversed excluded)
        assertEquals("Customer payments in period", 30.0, periodReport.customerPayments, 0.001)
        assertEquals("Receivable change in period", 110.0, periodReport.netReceivableIncrease, 0.001) // 140 - 30

        // --- Surface 3: AnalysisCenterViewModel (Opening Balance & Statement Rows) ---
        val viewModel = AnalysisCenterViewModel()
        val openingBalance = viewModel.calculateOpeningBalance(
            allTransactions = allTransactions,
            selectedCustomer = customer,
            period = PeriodFilter.CUSTOM,
            customStartDate = periodStart,
            customEndDate = periodEnd,
            today = today
        )
        assertEquals("Opening balance strictly prior to period", 60.0, openingBalance, 0.001) // (50-50) + (100-40)

        val vmStatementRows = viewModel.computeStatementRows(
            allTransactions = allTransactions,
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.CUSTOM,
            customStartDate = periodStart,
            customEndDate = periodEnd,
            today = today,
            includeOpeningBalanceRow = true
        )
        assertEquals("Opening row + 5 inside transactions", 6, vmStatementRows.size)
        // Opening balance row
        assertEquals("opening_balance", vmStatementRows[0].id)
        assertEquals(60.0, vmStatementRows[0].runningBalance, 0.001)
        // txCashOnly (10:00) -> running balance unchanged
        assertEquals("tx_inside_cash_only", vmStatementRows[1].id)
        assertEquals(60.0, vmStatementRows[1].runningBalance, 0.001)
        // txCreditOnly (14:00) -> running balance increases by 100
        assertEquals("tx_inside_credit_only", vmStatementRows[2].id)
        assertEquals(160.0, vmStatementRows[2].runningBalance, 0.001)
        // txMixed (09:00) -> running balance increases by 40 (NOT 100!)
        assertEquals("tx_inside_mixed", vmStatementRows[3].id)
        assertEquals(200.0, vmStatementRows[3].runningBalance, 0.001)
        // txPayment (11:00) -> running balance decreases by 30
        assertEquals("tx_inside_payment", vmStatementRows[4].id)
        assertEquals(170.0, vmStatementRows[4].runningBalance, 0.001)
        // txReversed -> 0 impact on running balance
        assertEquals("tx_inside_reversed", vmStatementRows[5].id)
        assertEquals(170.0, vmStatementRows[5].runningBalance, 0.001)

        // --- Surface 4: ReportExporter Statement Rows ---
        val exporterRows = ReportExporter.buildStatementRows(
            customer = customer,
            transactions = insidePeriodTransactions,
            openingBalance = openingBalance,
            includeOpeningBalanceRow = true,
            isArabic = true
        )
        assertEquals(6, exporterRows.size)
        assertEquals(170.0, exporterRows.last().runningBalance, 0.001)

        // --- Surface 5: Debt Aging (DebtAgingUtils) ---
        val aging = DebtAgingUtils.calculateCustomerAging(customer, allTransactions, today = today)
        assertEquals("Aging currentDebt must reconcile with ledger balance", 170.0, aging.currentDebt, 0.001)
        assertEquals("Reconciles with ledger balance", fullLedger.balance, aging.currentDebt, 0.001)

        // --- Surface 6: AnalyticsExportDataPreparer ---
        val analyticsReport = AnalyticsExportDataPreparer.prepareAnalyticsData(
            transactions = insidePeriodTransactions,
            transactionLines = emptyList(),
            allCustomers = listOf(customer),
            selectedCustomer = null,
            activePeriod = PeriodFilter.ALL,
            selectedChartType = BreakdownChartType.DONUT,
            storeName = "متجر التجربة",
            isArabic = true,
            today = today
        )
        assertEquals(160.0, analyticsReport.metrics.totalCashSales, 0.001)
        assertEquals(140.0, analyticsReport.metrics.totalDebtSales, 0.001)
        assertEquals(300.0, analyticsReport.metrics.totalSales, 0.001)
        assertEquals(30.0, analyticsReport.metrics.totalPaymentsReceived, 0.001)

        // --- Surface 7: Customer CSV Export ---
        val customerCsv = ReportExporter.generateCustomerCsv(
            title = "كشف حساب عميل",
            storeName = "متجر التجربة",
            subtitle = "الفترة المحددة",
            customer = customer,
            kpis = emptyList(),
            transactions = insidePeriodTransactions,
            itemBreakdowns = emptyList(),
            isArabic = true
        )
        assertTrue("CSV contains cash sales 160.00", customerCsv.contains("160.00"))
        assertTrue("CSV contains credit sales 140.00", customerCsv.contains("140.00"))
        assertTrue("CSV contains customer payments 30.00", customerCsv.contains("30.00"))
        assertTrue("CSV active operations total is 330.00", customerCsv.contains("330.00"))
        assertFalse("Reversed 200.00 sale must not be in credit sales total (340.00)", customerCsv.contains("340.00"))
        assertFalse("Reversed 200.00 sale must not be in active operations total (530.00)", customerCsv.contains("530.00"))

        // --- Surface 8: Customer TXT Export ---
        val customerTxt = ReportExporter.generateCustomerTxt(
            title = "كشف حساب عميل",
            storeName = "متجر التجربة",
            subtitle = "الفترة المحددة",
            customer = customer,
            kpis = emptyList(),
            transactions = insidePeriodTransactions,
            itemBreakdowns = emptyList(),
            isArabic = true
        )
        assertTrue("TXT contains cash sales 160.00", customerTxt.contains("160.00"))
        assertTrue("TXT contains credit sales 140.00", customerTxt.contains("140.00"))
        assertTrue("TXT contains customer payments 30.00", customerTxt.contains("30.00"))
        assertTrue("TXT active operations total is 330.00", customerTxt.contains("330.00"))
        assertFalse("TXT must not add reversed 200.00 to credit sales total (340.00)", customerTxt.contains("340.00"))
        assertFalse("TXT must not add reversed 200.00 to active operations total (530.00)", customerTxt.contains("530.00"))
    }
}
