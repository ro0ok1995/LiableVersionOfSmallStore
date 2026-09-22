package com.example.accounting

import com.example.model.CustomerAccount
import com.example.model.OperationStatus
import com.example.model.PeriodFilter
import com.example.model.SaleType
import com.example.model.SettlementType
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.viewmodel.AnalysisCenterViewModel
import com.example.viewmodel.StatementTxFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 2.3 Accounting Reconciliation Test Suite:
 *
 * Verifies that customer statements, running balances, and accounting summaries
 * reconcile with the same transaction population used by CustomerLedgerCalculator.
 *
 * Core accounting invariant:
 * Archiving a financial record is NOT an accounting reversal.
 * An archived transaction must continue to affect the ledger unless explicitly reversed.
 */
class CustomerStatementReconciliationTest {

    private lateinit var viewModel: AnalysisCenterViewModel
    private val customerId = "cust_reconcile_001"
    private val customer = CustomerAccount(
        id = customerId,
        customerName = "خالد العتيبي",
        balance = 0.0,
        totalDebt = 0.0,
        phone = "0501234567"
    )

    @Before
    fun setup() {
        viewModel = AnalysisCenterViewModel()
    }

    @Test
    fun activeCreditSale_affectsBalanceAndStatement() {
        val tx1 = TransactionItem(
            id = "tx_active_1",
            customerName = customer.customerName,
            activityType = "شراء آجل",
            amount = 150.0,
            isCredit = true,
            date = "2026-09-10",
            relativeTime = "الآن",
            isArchived = false,
            customerId = customerId
        )
        val txList = listOf(tx1)

        // 1. CustomerLedgerCalculator balance calculation
        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, txList)
        assertEquals(150.0, summary.balance, 0.0001)
        assertEquals(150.0, summary.totalCreditSales, 0.0001)
        assertEquals(0.0, summary.totalPayments, 0.0001)

        // 2. Customer statement computation
        val rows = viewModel.computeStatementRows(
            allTransactions = txList,
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )
        assertEquals(1, rows.size)
        assertEquals("tx_active_1", rows[0].id)
        assertEquals(150.0, rows[0].amount, 0.0001)
        assertEquals(150.0, rows[0].runningBalance, 0.0001)
        assertFalse("Active transaction must not be marked as archived", rows[0].isArchived)
        assertEquals("Statement running balance must match ledger calculator", summary.balance, rows[0].runningBalance, 0.0001)
    }

    @Test
    fun archivedCreditSale_stillAffectsBalanceAndStatement() {
        // Accounting Rule: Archiving a transaction is NOT a reversal!
        val txActive = TransactionItem(
            id = "tx_active_sale",
            customerName = customer.customerName,
            activityType = "شراء آجل",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-01",
            relativeTime = "سابقاً",
            isArchived = false,
            customerId = customerId
        )
        val txArchived = TransactionItem(
            id = "tx_archived_sale",
            customerName = customer.customerName,
            activityType = "شراء آجل",
            amount = 200.0,
            isCredit = true,
            date = "2026-09-05",
            relativeTime = "سابقاً",
            isArchived = true, // ARCHIVED!
            customerId = customerId
        )
        val txList = listOf(txActive, txArchived)

        // 1. CustomerLedgerCalculator: archived sale STILL affects balance!
        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, txList)
        assertEquals(300.0, summary.balance, 0.0001)
        assertEquals(300.0, summary.totalCreditSales, 0.0001)

        // 2. Customer Statement: archived sale MUST appear in statement and running balance
        val rows = viewModel.computeStatementRows(
            allTransactions = txList,
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )
        assertEquals(2, rows.size)
        assertEquals("tx_active_sale", rows[0].id)
        assertEquals(100.0, rows[0].runningBalance, 0.0001)
        assertFalse(rows[0].isArchived)

        assertEquals("tx_archived_sale", rows[1].id)
        assertEquals(300.0, rows[1].runningBalance, 0.0001)
        assertTrue("Archived transaction must be flagged as archived", rows[1].isArchived)

        // Reconcile: final statement row matches CustomerLedgerCalculator
        assertEquals("Final statement balance must strictly reconcile with CustomerLedgerCalculator",
            summary.balance, rows.last().runningBalance, 0.0001)
    }

    @Test
    fun archivedPayment_stillAffectsBalanceAndStatement() {
        val txSale = TransactionItem(
            id = "tx_sale_1",
            customerName = customer.customerName,
            activityType = "شراء آجل",
            amount = 250.0,
            isCredit = true,
            date = "2026-09-01",
            relativeTime = "سابقاً",
            isArchived = false,
            customerId = customerId
        )
        val txArchivedPayment = TransactionItem(
            id = "tx_archived_pay",
            customerName = customer.customerName,
            activityType = "تسديد",
            amount = 100.0,
            isCredit = false,
            date = "2026-09-08",
            relativeTime = "سابقاً",
            settlementType = SettlementType.PARTIAL,
            isArchived = true, // ARCHIVED PAYMENT!
            customerId = customerId
        )
        val txList = listOf(txSale, txArchivedPayment)

        // 1. CustomerLedgerCalculator: payment reduces debt to 150 even if archived
        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, txList)
        assertEquals(150.0, summary.balance, 0.0001)
        assertEquals(250.0, summary.totalCreditSales, 0.0001)
        assertEquals(100.0, summary.totalPayments, 0.0001)

        // 2. Customer Statement: archived payment MUST appear and deduct from running balance
        val rows = viewModel.computeStatementRows(
            allTransactions = txList,
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )
        assertEquals(2, rows.size)
        assertEquals("tx_sale_1", rows[0].id)
        assertEquals(250.0, rows[0].runningBalance, 0.0001)

        assertEquals("tx_archived_pay", rows[1].id)
        assertEquals(150.0, rows[1].runningBalance, 0.0001)
        assertTrue("Archived payment must have isArchived = true for visual indication", rows[1].isArchived)

        // Reconcile: final statement row matches CustomerLedgerCalculator
        assertEquals("Statement running balance must reconcile with CustomerLedgerCalculator",
            summary.balance, rows.last().runningBalance, 0.0001)
    }

    @Test
    fun archivedTransaction_isVisuallyDistinguishable() {
        val activeTx = TransactionItem(
            id = "tx_1",
            customerName = customer.customerName,
            activityType = "شراء آجل",
            amount = 50.0,
            isCredit = true,
            date = "2026-09-01",
            relativeTime = "الآن",
            isArchived = false,
            customerId = customerId
        )
        val archivedTx = TransactionItem(
            id = "tx_2",
            customerName = customer.customerName,
            activityType = "تسديد",
            amount = 20.0,
            isCredit = false,
            date = "2026-09-02",
            relativeTime = "الآن",
            isArchived = true,
            customerId = customerId
        )
        val rows = viewModel.computeStatementRows(
            allTransactions = listOf(activeTx, archivedTx),
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )
        assertEquals(2, rows.size)
        assertFalse("Active transaction is not archived", rows[0].isArchived)
        assertTrue("Archived transaction is flagged isArchived=true for visual badge", rows[1].isArchived)
    }

    @Test
    fun noArchivedFinancialTransactionDisappearsFromAccountingCalculations() {
        // Complex ledger with mixed active and archived transactions
        val txs = listOf(
            TransactionItem(
                id = "tx_a1",
                customerName = customer.customerName,
                activityType = "شراء آجل",
                amount = 500.0,
                isCredit = true,
                date = "2026-08-01",
                relativeTime = "سابقاً",
                isArchived = false,
                customerId = customerId
            ),
            TransactionItem(
                id = "tx_a2_archived",
                customerName = customer.customerName,
                activityType = "شراء آجل",
                amount = 300.0,
                isCredit = true,
                date = "2026-08-15",
                relativeTime = "سابقاً",
                isArchived = true,
                customerId = customerId
            ),
            TransactionItem(
                id = "tx_p1",
                customerName = customer.customerName,
                activityType = "تسديد",
                amount = 200.0,
                isCredit = false,
                date = "2026-08-20",
                relativeTime = "سابقاً",
                isArchived = false,
                customerId = customerId
            ),
            TransactionItem(
                id = "tx_p2_archived",
                customerName = customer.customerName,
                activityType = "تسديد",
                amount = 150.0,
                isCredit = false,
                date = "2026-09-01",
                relativeTime = "سابقاً",
                isArchived = true,
                customerId = customerId
            )
        )

        // Total sales: 500 + 300 = 800
        // Total payments: 200 + 150 = 350
        // Net balance: 800 - 350 = 450
        val ledgerSummary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, txs)
        assertEquals(800.0, ledgerSummary.totalCreditSales, 0.0001)
        assertEquals(350.0, ledgerSummary.totalPayments, 0.0001)
        assertEquals(450.0, ledgerSummary.balance, 0.0001)

        val statementRows = viewModel.computeStatementRows(
            allTransactions = txs,
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )

        assertEquals("No transaction must disappear from statement", 4, statementRows.size)
        assertEquals(500.0, statementRows[0].runningBalance, 0.0001)
        assertEquals(800.0, statementRows[1].runningBalance, 0.0001)
        assertEquals(600.0, statementRows[2].runningBalance, 0.0001)
        assertEquals(450.0, statementRows[3].runningBalance, 0.0001)

        assertEquals("Final statement running balance matches CustomerLedgerCalculator exactly",
            ledgerSummary.balance, statementRows.last().runningBalance, 0.0001)
    }

    // =========================================================================
    // PHASE 4 STEP 1: FOCUSED CUSTOMER STATEMENT TESTS (A - H)
    // =========================================================================

    /**
     * Test A: PeriodFilter.ALL running balance.
     * Verifies that running balance starts from 0.0 and accumulates across all historical transactions.
     */
    @Test
    fun testA_periodFilterAllRunningBalance() {
        val txs = listOf(
            TransactionItem(
                id = "tx_a1",
                customerNameSnapshot = customer.customerName,
                activityType = "شراء آجل",
                amount = 100.0,
                isCredit = true,
                date = "2026-09-01",
                relativeTime = "اليوم",
                customerId = customerId,
                transactionType = TransactionType.SALE,
                saleType = SaleType.CREDIT,
                creditAmount = 100.0
            ),
            TransactionItem(
                id = "tx_a2",
                customerNameSnapshot = customer.customerName,
                activityType = "تسديد",
                amount = 40.0,
                isCredit = false,
                date = "2026-09-02",
                relativeTime = "اليوم",
                customerId = customerId,
                transactionType = TransactionType.CUSTOMER_PAYMENT
            ),
            TransactionItem(
                id = "tx_a3",
                customerNameSnapshot = customer.customerName,
                activityType = "شراء آجل",
                amount = 70.0,
                isCredit = true,
                date = "2026-09-03",
                relativeTime = "اليوم",
                customerId = customerId,
                transactionType = TransactionType.SALE,
                saleType = SaleType.CREDIT,
                creditAmount = 70.0
            )
        )

        val rows = viewModel.computeStatementRows(
            allTransactions = txs,
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )

        assertEquals(3, rows.size)
        // tx_a1: 100.0 debt -> running = 100.0
        assertEquals(100.0, rows[0].runningBalance, 0.0001)
        // tx_a2: -40.0 payment -> running = 60.0
        assertEquals(60.0, rows[1].runningBalance, 0.0001)
        // tx_a3: +70.0 debt -> running = 130.0
        assertEquals(130.0, rows[2].runningBalance, 0.0001)
    }

    /**
     * Test B: Date-filtered statement with correct opening balance.
     * Specification:
     * Day 1: +100 credit
     * Day 2: -40 payment
     * Day 5: selected period starts
     * Opening balance must be +60.
     */
    @Test
    fun testB_dateFilteredStatementWithCorrectOpeningBalance() {
        val txDay1 = TransactionItem(
            id = "tx_b_day1",
            customerNameSnapshot = customer.customerName,
            activityType = "شراء آجل",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-01",
            relativeTime = "اليوم",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            creditAmount = 100.0
        )
        val txDay2 = TransactionItem(
            id = "tx_b_day2",
            customerNameSnapshot = customer.customerName,
            activityType = "تسديد",
            amount = 40.0,
            isCredit = false,
            date = "2026-09-02",
            relativeTime = "اليوم",
            customerId = customerId,
            transactionType = TransactionType.CUSTOMER_PAYMENT
        )
        val txDay5 = TransactionItem(
            id = "tx_b_day5",
            customerNameSnapshot = customer.customerName,
            activityType = "شراء آجل",
            amount = 50.0,
            isCredit = true,
            date = "2026-09-05",
            relativeTime = "اليوم",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            creditAmount = 50.0
        )
        val allTxs = listOf(txDay1, txDay2, txDay5)

        val periodStart = LocalDate.of(2026, 9, 5)
        val periodEnd = LocalDate.of(2026, 9, 30)

        // 1. Calculate opening balance scalar directly
        val openingBalance = viewModel.calculateOpeningBalance(
            allTransactions = allTxs,
            selectedCustomer = customer,
            period = PeriodFilter.CUSTOM,
            customStartDate = periodStart,
            customEndDate = periodEnd
        )
        assertEquals(60.0, openingBalance, 0.0001)

        // 2. Verify statement rows start from opening balance
        val statementRows = viewModel.computeStatementRows(
            allTransactions = allTxs,
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.CUSTOM,
            customStartDate = periodStart,
            customEndDate = periodEnd
        )

        assertEquals("Only Day 5 transaction is in selected range", 1, statementRows.size)
        assertEquals("tx_b_day5", statementRows[0].id)
        assertEquals(50.0, statementRows[0].amount, 0.0001)
        // Running balance must begin from opening balance (+60) + Day 5 (+50) = 110.0
        assertEquals(110.0, statementRows[0].runningBalance, 0.0001)

        // 3. Verify statement rows with optional opening balance row
        val rowsWithOpening = viewModel.computeStatementRows(
            allTransactions = allTxs,
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.CUSTOM,
            customStartDate = periodStart,
            customEndDate = periodEnd,
            includeOpeningBalanceRow = true
        )

        assertEquals(2, rowsWithOpening.size)
        assertEquals("opening_balance", rowsWithOpening[0].id)
        assertEquals(60.0, rowsWithOpening[0].amount, 0.0001)
        assertEquals(60.0, rowsWithOpening[0].runningBalance, 0.0001)
        assertEquals("tx_b_day5", rowsWithOpening[1].id)
        assertEquals(110.0, rowsWithOpening[1].runningBalance, 0.0001)
    }

    /**
     * Test C: Mixed sale (100 total / 60 paid / 40 credit).
     * The customer receivable statement must increase by ONLY 40.
     * Cash portion must NOT increase customer receivable.
     */
    @Test
    fun testC_mixedSale_increasesReceivableOnlyByCreditPortion() {
        val mixedSaleTx = TransactionItem(
            id = "tx_mixed_sale",
            customerNameSnapshot = customer.customerName,
            activityType = "شراء مشرك",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-10",
            relativeTime = "اليوم",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED,
            paidAmount = 60.0,
            creditAmount = 40.0
        )

        val rows = viewModel.computeStatementRows(
            allTransactions = listOf(mixedSaleTx),
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )

        assertEquals(1, rows.size)
        assertEquals(100.0, rows[0].amount, 0.0001)
        // Customer receivable running balance must increase by ONLY 40.0, NOT 100.0
        assertEquals(40.0, rows[0].runningBalance, 0.0001)
    }

    /**
     * Test D: Same-day transactions ordered by precise timestamp.
     * Stable chronological ordering even when multiple transactions occur on the same day.
     */
    @Test
    fun testD_sameDayTransactionsOrderedByPreciseTimestamp() {
        // Create 3 transactions on 2026-09-15 with distinct timestamps
        val tx1Morning = TransactionItem(
            id = "tx_1726394400000", // 10:00 AM
            customerNameSnapshot = customer.customerName,
            activityType = "شراء آجل",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-15 10:00:00",
            relativeTime = "الصباح",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            creditAmount = 100.0
        )
        val tx2Noon = TransactionItem(
            id = "tx_1726401600000", // 12:00 PM
            customerNameSnapshot = customer.customerName,
            activityType = "تسديد",
            amount = 30.0,
            isCredit = false,
            date = "2026-09-15 12:00:00",
            relativeTime = "الظهر",
            customerId = customerId,
            transactionType = TransactionType.CUSTOMER_PAYMENT
        )
        val tx3Afternoon = TransactionItem(
            id = "tx_1726408800000", // 02:00 PM
            customerNameSnapshot = customer.customerName,
            activityType = "شراء آجل",
            amount = 50.0,
            isCredit = true,
            date = "2026-09-15 14:00:00",
            relativeTime = "العصر",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            creditAmount = 50.0
        )

        // Pass out of chronological order: Afternoon, Morning, Noon
        val unorderedList = listOf(tx3Afternoon, tx1Morning, tx2Noon)

        val rows = viewModel.computeStatementRows(
            allTransactions = unorderedList,
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )

        assertEquals(3, rows.size)
        // Must sort strictly chronologically: Morning -> Noon -> Afternoon
        assertEquals("tx_1726394400000", rows[0].id)
        assertEquals(100.0, rows[0].runningBalance, 0.0001)

        assertEquals("tx_1726401600000", rows[1].id)
        assertEquals(70.0, rows[1].runningBalance, 0.0001)

        assertEquals("tx_1726408800000", rows[2].id)
        assertEquals(120.0, rows[2].runningBalance, 0.0001)
    }

    /**
     * Test E: Reversed transaction excluded from financial balance.
     * Typed OperationStatus.REVERSED must not affect customer running balance.
     */
    @Test
    fun testE_reversedTransactionExcludedFromFinancialBalance() {
        val tx1Active = TransactionItem(
            id = "tx_e1",
            customerNameSnapshot = customer.customerName,
            activityType = "شراء آجل",
            amount = 200.0,
            isCredit = true,
            date = "2026-09-01",
            relativeTime = "اليوم",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            creditAmount = 200.0,
            operationStatus = OperationStatus.ACTIVE
        )
        val tx2Reversed = TransactionItem(
            id = "tx_e2_reversed",
            customerNameSnapshot = customer.customerName,
            activityType = "شراء آجل ملغي",
            amount = 150.0,
            isCredit = true,
            date = "2026-09-02",
            relativeTime = "اليوم",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            creditAmount = 150.0,
            operationStatus = OperationStatus.REVERSED
        )
        val tx3Payment = TransactionItem(
            id = "tx_e3",
            customerNameSnapshot = customer.customerName,
            activityType = "تسديد",
            amount = 50.0,
            isCredit = false,
            date = "2026-09-03",
            relativeTime = "اليوم",
            customerId = customerId,
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            operationStatus = OperationStatus.ACTIVE
        )

        val rows = viewModel.computeStatementRows(
            allTransactions = listOf(tx1Active, tx2Reversed, tx3Payment),
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )

        assertEquals(3, rows.size)
        // tx_e1: 200.0 debt -> running = 200.0
        assertEquals(200.0, rows[0].runningBalance, 0.0001)
        // tx_e2_reversed: REVERSED -> running remains 200.0!
        assertEquals(200.0, rows[1].runningBalance, 0.0001)
        // tx_e3: -50.0 payment -> running = 150.0
        assertEquals(150.0, rows[2].runningBalance, 0.0001)
    }

    /**
     * Test F: Archived-but-not-reversed follows existing accounting rule.
     * Archiving a transaction does NOT reverse it. It continues to contribute to financial balance.
     */
    @Test
    fun testF_archivedButNotReversedFollowsExistingAccountingRule() {
        val txActive = TransactionItem(
            id = "tx_f1_active",
            customerNameSnapshot = customer.customerName,
            activityType = "شراء آجل",
            amount = 200.0,
            isCredit = true,
            date = "2026-09-01",
            relativeTime = "اليوم",
            customerId = customerId,
            isArchived = false,
            operationStatus = OperationStatus.ACTIVE,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            creditAmount = 200.0
        )
        val txArchived = TransactionItem(
            id = "tx_f2_archived",
            customerNameSnapshot = customer.customerName,
            activityType = "شراء آجل مؤرشف",
            amount = 150.0,
            isCredit = true,
            date = "2026-09-02",
            relativeTime = "سابقاً",
            customerId = customerId,
            isArchived = true,
            operationStatus = null, // legacy archived, not explicitly reversed
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            creditAmount = 150.0
        )

        val rows = viewModel.computeStatementRows(
            allTransactions = listOf(txActive, txArchived),
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )

        assertEquals(2, rows.size)
        assertEquals(200.0, rows[0].runningBalance, 0.0001)
        // Archived transaction MUST contribute to balance: 200 + 150 = 350.0
        assertEquals(350.0, rows[1].runningBalance, 0.0001)
        assertTrue(rows[1].isArchived)
    }

    /**
     * Test G: Cash-only sale creates no customer receivable.
     */
    @Test
    fun testG_cashOnlySaleCreatesNoCustomerReceivable() {
        val cashSale = TransactionItem(
            id = "tx_g1_cash",
            customerNameSnapshot = customer.customerName,
            activityType = "شراء نقدي",
            amount = 120.0,
            isCredit = false,
            date = "2026-09-10",
            relativeTime = "اليوم",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CASH,
            paidAmount = 120.0,
            creditAmount = 0.0
        )

        val rows = viewModel.computeStatementRows(
            allTransactions = listOf(cashSale),
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )

        assertEquals(1, rows.size)
        assertEquals(120.0, rows[0].amount, 0.0001)
        // Cash sale has 0 debt impact -> running balance remains 0.0
        assertEquals(0.0, rows[0].runningBalance, 0.0001)
    }

    /**
     * Test H: Credit-only sale increases receivable correctly.
     */
    @Test
    fun testH_creditOnlySaleIncreasesReceivableCorrectly() {
        val creditSale1 = TransactionItem(
            id = "tx_h1_credit",
            customerNameSnapshot = customer.customerName,
            activityType = "شراء آجل",
            amount = 150.0,
            isCredit = true,
            date = "2026-09-10",
            relativeTime = "اليوم",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            paidAmount = 0.0,
            creditAmount = 150.0
        )
        val creditSale2 = TransactionItem(
            id = "tx_h2_credit",
            customerNameSnapshot = customer.customerName,
            activityType = "شراء آجل",
            amount = 250.0,
            isCredit = true,
            date = "2026-09-12",
            relativeTime = "اليوم",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            paidAmount = 0.0,
            creditAmount = 250.0
        )

        val rows = viewModel.computeStatementRows(
            allTransactions = listOf(creditSale1, creditSale2),
            selectedCustomer = customer,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )

        assertEquals(2, rows.size)
        assertEquals(150.0, rows[0].runningBalance, 0.0001)
        assertEquals(400.0, rows[1].runningBalance, 0.0001)
    }
}
