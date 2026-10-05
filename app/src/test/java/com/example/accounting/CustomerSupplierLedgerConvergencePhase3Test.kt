package com.example.accounting

import com.example.data.db.Adjustment
import com.example.data.db.CustomerPayment
import com.example.data.db.OpeningBalance
import com.example.data.db.Purchase
import com.example.data.db.PurchaseReturn
import com.example.data.db.Refund
import com.example.data.db.Sale
import com.example.data.db.SaleReturn
import com.example.data.db.SupplierPayment
import com.example.model.CustomerAccount
import com.example.model.OperationStatus
import com.example.model.PaymentStatus
import com.example.model.PeriodFilter
import com.example.model.SaleType
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.viewmodel.AnalysisCenterViewModel
import com.example.viewmodel.DebtAgingUtils
import com.example.viewmodel.StatementTxFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 3 Prompt 2: Customer & Supplier Ledger Convergence Verification Suite.
 *
 * Covers all 14 required Phase 3 verification cases:
 * A. Customer opening balance appears exactly once.
 * B. Customer credit sale increases receivable.
 * C. Customer partial payment reduces receivable without revenue duplication.
 * D. Multiple credit sales plus one payment produce the correct outstanding balance.
 * E. Customer return/refund affects outstanding exposure correctly.
 * F. Reversal does not remain active in the statement.
 * G. Fully paid customer has zero active aging exposure.
 * H. Historical credit sales do not create phantom overdue debt.
 * I. Supplier opening balance appears exactly once.
 * J. Supplier purchase increases payable.
 * K. Supplier payment reduces payable.
 * L. Supplier return reduces payable where applicable.
 * M. Supplier reversal is excluded from active payable.
 * N. Statement balance equals authoritative ledger balance.
 */
class CustomerSupplierLedgerConvergencePhase3Test {

    private lateinit var viewModel: AnalysisCenterViewModel
    private val customerId1 = "cust_conv_001"
    private val customerId2 = "cust_conv_002"
    private val supplierId1 = "sup_conv_001"
    private val today = LocalDate.of(2026, 10, 5)

    private val customerAccount1 = CustomerAccount(
        id = customerId1,
        customerName = "عبد الله الشمري",
        balance = 0.0,
        totalDebt = 0.0,
        phone = "0501112233"
    )

    @Before
    fun setup() {
        viewModel = AnalysisCenterViewModel()
    }

    // CASE A: Customer opening balance appears exactly once
    @Test
    fun testA_customerOpeningBalanceAppearsExactlyOnce() {
        val ob = OpeningBalance(
            id = "ob_cust_1",
            entityType = "CUSTOMER",
            entityId = customerId1,
            amount = 300.0,
            direction = "DEBIT",
            date = "2026-10-01",
            reason = "رصيد افتتاحي مرحل"
        )

        // 1. Authoritative ledger calculation
        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = customerId1,
            sales = emptyList(),
            openingBalances = listOf(ob)
        )
        assertEquals("Current receivable must equal opening debit", 300.0, summary.balance, 0.001)
        assertEquals(300.0, summary.openingBalance, 0.001)
        assertEquals(0.0, summary.totalCreditSales, 0.001)
        assertTrue(summary.isDebitBalance)

        // 2. Statement presentation row
        val txOb = TransactionItem(
            id = ob.id,
            title = "رصيد افتتاحي",
            customerNameSnapshot = customerAccount1.customerName,
            activityType = "رصيد افتتاحي",
            amount = 300.0,
            isCredit = true,
            date = "2026-10-01",
            relativeTime = "10:00",
            customerId = customerId1,
            transactionType = TransactionType.OPENING_BALANCE,
            operationStatus = OperationStatus.ACTIVE
        )
        val rows = viewModel.computeStatementRows(
            allTransactions = listOf(txOb),
            selectedCustomer = customerAccount1,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )
        assertEquals("Opening balance row appears exactly once", 1, rows.size)
        assertEquals(300.0, rows[0].amount, 0.001)
        assertEquals("Statement running balance equals opening balance", 300.0, rows[0].runningBalance, 0.001)
    }

    // CASE B: Customer credit sale increases receivable
    @Test
    fun testB_customerCreditSaleIncreasesReceivable() {
        val sale = Sale(
            id = "sale_cr_1",
            invoiceNumber = "INV-001",
            customerId = customerId1,
            saleType = "CREDIT",
            totalAmount = 250.0,
            paidAmount = 0.0,
            creditAmount = 250.0,
            paymentStatus = "UNPAID",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = customerId1,
            sales = listOf(sale)
        )

        assertEquals("Credit sale increases receivable by creditAmount", 250.0, summary.balance, 0.001)
        assertEquals(250.0, summary.totalCreditSales, 0.001)
        assertTrue(summary.isDebitBalance)
    }

    // CASE C: Customer partial payment reduces receivable without revenue duplication
    @Test
    fun testC_customerPartialPaymentReducesReceivableWithoutRevenueDuplication() {
        val sale = Sale(
            id = "sale_cr_2",
            invoiceNumber = "INV-002",
            customerId = customerId1,
            saleType = "CREDIT",
            totalAmount = 200.0,
            paidAmount = 0.0,
            creditAmount = 200.0,
            paymentStatus = "UNPAID",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )
        val payment = CustomerPayment(
            id = "pay_part_1",
            customerId = customerId1,
            amount = 80.0,
            paymentMethodId = "pm_cash",
            financialAccountId = "acc_cash",
            transactionDate = "2026-10-02",
            status = "ACTIVE"
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = customerId1,
            sales = listOf(sale),
            payments = listOf(payment)
        )

        assertEquals("Balance reduced by partial payment (200 - 80 = 120)", 120.0, summary.balance, 0.001)
        assertEquals("Historical credit sales remains 200 without duplication", 200.0, summary.totalCreditSales, 0.001)
        assertEquals("Total payments recorded = 80", 80.0, summary.totalPayments, 0.001)

        // Statement rows verify running balance reduction
        val txSale = TransactionItem(
            id = sale.id,
            title = "فاتورة آجل",
            customerNameSnapshot = customerAccount1.customerName,
            activityType = "شراء آجل",
            amount = 200.0,
            isCredit = true,
            date = "2026-10-01",
            relativeTime = "10:00",
            customerId = customerId1,
            creditAmount = 200.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            operationStatus = OperationStatus.ACTIVE
        )
        val txPay = TransactionItem(
            id = payment.id,
            title = "سداد دفعة",
            customerNameSnapshot = customerAccount1.customerName,
            activityType = "تسديد",
            amount = 80.0,
            isCredit = false,
            date = "2026-10-02",
            relativeTime = "11:00",
            customerId = customerId1,
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            operationStatus = OperationStatus.ACTIVE
        )
        val rows = viewModel.computeStatementRows(
            allTransactions = listOf(txSale, txPay),
            selectedCustomer = customerAccount1,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )
        assertEquals(2, rows.size)
        assertEquals(200.0, rows[0].runningBalance, 0.001)
        assertEquals("Running balance after payment is 120", 120.0, rows[1].runningBalance, 0.001)
        assertTrue("Second row is payment", rows[1].isPayment)
    }

    // CASE D: Multiple credit sales plus one payment produce the correct outstanding balance
    @Test
    fun testD_multipleCreditSalesPlusOnePaymentProduceCorrectOutstandingBalance() {
        val sale1 = Sale(
            id = "sale_multi_1",
            invoiceNumber = "INV-003",
            customerId = customerId1,
            saleType = "CREDIT",
            totalAmount = 100.0,
            paidAmount = 0.0,
            creditAmount = 100.0,
            paymentStatus = "UNPAID",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )
        val sale2 = Sale(
            id = "sale_multi_2",
            invoiceNumber = "INV-004",
            customerId = customerId1,
            saleType = "CREDIT",
            totalAmount = 150.0,
            paidAmount = 0.0,
            creditAmount = 150.0,
            paymentStatus = "UNPAID",
            transactionDate = "2026-10-02",
            status = "ACTIVE"
        )
        val payment = CustomerPayment(
            id = "pay_multi_1",
            customerId = customerId1,
            amount = 70.0,
            paymentMethodId = "pm_cash",
            financialAccountId = "acc_cash",
            transactionDate = "2026-10-03",
            status = "ACTIVE"
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = customerId1,
            sales = listOf(sale1, sale2),
            payments = listOf(payment)
        )

        assertEquals("Total credit sales = 250", 250.0, summary.totalCreditSales, 0.001)
        assertEquals("Total payments = 70", 70.0, summary.totalPayments, 0.001)
        assertEquals("Remaining balance = 180 (100 + 150 - 70)", 180.0, summary.balance, 0.001)
    }

    // CASE E: Customer return/refund affects outstanding exposure correctly
    @Test
    fun testE_customerReturnRefundAffectsOutstandingExposureCorrectly() {
        val sale = Sale(
            id = "sale_ret_1",
            invoiceNumber = "INV-RET-1",
            customerId = customerId1,
            saleType = "CREDIT",
            totalAmount = 200.0,
            paidAmount = 0.0,
            creditAmount = 200.0,
            paymentStatus = "UNPAID",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )
        val saleReturn = SaleReturn(
            id = "sr_1",
            saleId = sale.id,
            customerId = customerId1,
            returnDate = "2026-10-02",
            reason = "Return item",
            amount = 50.0,
            status = "ACTIVE"
        )
        val refund = Refund(
            id = "ref_1",
            saleReturnId = saleReturn.id,
            saleId = sale.id,
            customerId = customerId1,
            amount = 20.0,
            financialAccountId = "acc_cash",
            refundDate = "2026-10-03",
            reason = "Cash refund adjustment",
            status = "ACTIVE"
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = customerId1,
            sales = listOf(sale),
            saleReturns = listOf(saleReturn),
            refunds = listOf(refund)
        )

        // 200 credit sale - 50 return + 20 refund = 170
        assertEquals("Balance reflects return credit and refund debit (200 - 50 + 20 = 170)", 170.0, summary.balance, 0.001)
        assertEquals(50.0, summary.totalReturns, 0.001)
        assertEquals(20.0, summary.debitAdjustments, 0.001)
    }

    // CASE F: Reversal does not remain active in the statement
    @Test
    fun testF_reversalDoesNotRemainActiveInStatement() {
        val activeTx = TransactionItem(
            id = "tx_active_100",
            title = "فاتورة نشطة",
            customerNameSnapshot = customerAccount1.customerName,
            activityType = "شراء آجل",
            amount = 100.0,
            isCredit = true,
            date = "2026-10-01",
            relativeTime = "10:00",
            customerId = customerId1,
            creditAmount = 100.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            operationStatus = OperationStatus.ACTIVE
        )
        val reversedTx = TransactionItem(
            id = "tx_rev_200",
            title = "فاتورة ملغاة",
            customerNameSnapshot = customerAccount1.customerName,
            activityType = "شراء آجل",
            amount = 200.0,
            isCredit = true,
            date = "2026-10-02",
            relativeTime = "11:00",
            customerId = customerId1,
            creditAmount = 200.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            operationStatus = OperationStatus.REVERSED
        )

        val rows = viewModel.computeStatementRows(
            allTransactions = listOf(activeTx, reversedTx),
            selectedCustomer = customerAccount1,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )

        assertEquals(2, rows.size)
        assertEquals("Active transaction adds 100", 100.0, rows[0].runningBalance, 0.001)
        assertFalse(rows[0].isReversed)

        assertTrue("Reversed transaction is flagged", rows[1].isReversed)
        assertEquals("Running balance remains 100 — reversed transaction contributes 0", 100.0, rows[1].runningBalance, 0.001)

        val ledgerSummary = CustomerLedgerCalculator.calculateCustomerBalance(customerId1, listOf(activeTx, reversedTx))
        assertEquals("Ledger balance equals 100", 100.0, ledgerSummary.balance, 0.001)
        assertEquals(rows.last().runningBalance, ledgerSummary.balance, 0.001)
    }

    // CASE G: Fully paid customer has zero active aging exposure
    @Test
    fun testG_fullyPaidCustomerHasZeroActiveAgingExposure() {
        val creditSaleTx = TransactionItem(
            id = "tx_paid_sale",
            title = "فاتورة آجل",
            customerNameSnapshot = customerAccount1.customerName,
            activityType = "شراء آجل",
            amount = 1000.0,
            isCredit = true,
            date = "2026-08-01", // 65 days old
            relativeTime = "10:00",
            customerId = customerId1,
            creditAmount = 1000.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            paymentStatus = PaymentStatus.PAID,
            operationStatus = OperationStatus.ACTIVE
        )
        val paymentTx = TransactionItem(
            id = "tx_full_payment",
            title = "سداد كامل الفاتورة",
            customerNameSnapshot = customerAccount1.customerName,
            activityType = "تسديد",
            amount = 1000.0,
            isCredit = false,
            date = "2026-08-15",
            relativeTime = "11:00",
            customerId = customerId1,
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            operationStatus = OperationStatus.ACTIVE
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId1, listOf(creditSaleTx, paymentTx))
        assertEquals(0.0, summary.balance, 0.001)
        assertTrue("Customer is settled", summary.isSettled)

        val aging = DebtAgingUtils.calculateCustomerAging(customerAccount1, listOf(creditSaleTx, paymentTx), today = today)
        assertEquals("Current debt in aging must be 0", 0.0, aging.currentDebt, 0.001)
        assertEquals(0.0, aging.bucket0To30, 0.001)
        assertEquals(0.0, aging.bucket31To60, 0.001)
        assertEquals(0.0, aging.bucket61To90, 0.001)
        assertEquals(0.0, aging.bucket90Plus, 0.001)
        assertTrue("No individual transactions aged for settled customer", aging.individualTransactions.isEmpty())
    }

    // CASE H: Historical credit sales do not create phantom overdue debt
    @Test
    fun testH_historicalCreditSalesDoNotCreatePhantomOverdueDebt() {
        // Customer with large historical credit sales (5000), but paid 4800 (remaining 200)
        val oldSaleTx = TransactionItem(
            id = "tx_hist_5000",
            title = "شراء آجل تاريخي",
            customerNameSnapshot = customerAccount1.customerName,
            activityType = "شراء آجل",
            amount = 5000.0,
            isCredit = true,
            date = "2026-05-01", // 157 days old
            relativeTime = "10:00",
            customerId = customerId1,
            creditAmount = 5000.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            operationStatus = OperationStatus.ACTIVE
        )
        val largePaymentTx = TransactionItem(
            id = "tx_pay_4800",
            title = "سداد كبير",
            customerNameSnapshot = customerAccount1.customerName,
            activityType = "تسديد",
            amount = 4800.0,
            isCredit = false,
            date = "2026-05-15",
            relativeTime = "11:00",
            customerId = customerId1,
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            operationStatus = OperationStatus.ACTIVE
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId1, listOf(oldSaleTx, largePaymentTx))
        assertEquals(5000.0, summary.totalCreditSales, 0.001)
        assertEquals(4800.0, summary.totalPayments, 0.001)
        assertEquals("Outstanding balance is only 200", 200.0, summary.balance, 0.001)

        val aging = DebtAgingUtils.calculateCustomerAging(customerAccount1, listOf(oldSaleTx, largePaymentTx), today = today)
        assertEquals("Aging currentDebt must be strictly 200, never historical 5000", 200.0, aging.currentDebt, 0.001)
        assertEquals("Overdue bucket receives only remaining 200", 200.0, aging.bucket90Plus, 0.001)
        assertEquals(0.0, aging.bucket0To30, 0.001)
    }

    // CASE I: Supplier opening balance appears exactly once
    @Test
    fun testI_supplierOpeningBalanceAppearsExactlyOnce() {
        val ob = OpeningBalance(
            id = "ob_sup_1",
            entityType = "SUPPLIER",
            entityId = supplierId1,
            amount = 400.0,
            direction = "CREDIT",
            date = "2026-10-01",
            reason = "رصيد افتتاحي للمورد"
        )
        val summary = SupplierLedgerCalculator.calculateSupplierBalance(
            supplierId = supplierId1,
            purchases = emptyList(),
            openingBalances = listOf(ob)
        )

        assertEquals("Supplier payable equals credit opening balance", 400.0, summary.balance, 0.001)
        assertEquals(400.0, summary.openingBalance, 0.001)

        val entries = SupplierLedgerCalculator.buildSupplierLedger(
            supplierId = supplierId1,
            purchases = emptyList(),
            openingBalances = listOf(ob)
        )
        assertEquals(1, entries.size)
        assertEquals(400.0, entries[0].credit, 0.001)
        assertEquals(400.0, entries[0].runningBalance, 0.001)
    }

    // CASE J: Supplier purchase increases payable
    @Test
    fun testJ_supplierPurchaseIncreasesPayable() {
        val purchase = Purchase(
            id = "pur_sup_1",
            invoiceNumber = "PUR-001",
            supplierId = supplierId1,
            purchaseDate = "2026-10-01",
            totalAmount = 500.0,
            paidAmount = 200.0,
            creditAmount = 300.0,
            paymentStatus = "PARTIAL",
            financialAccountId = "acc_cash",
            status = "ACTIVE"
        )

        val summary = SupplierLedgerCalculator.calculateSupplierBalance(
            supplierId = supplierId1,
            purchases = listOf(purchase)
        )

        assertEquals("Only unpaid creditAmount (300) increases payable", 300.0, summary.balance, 0.001)
        assertEquals(500.0, summary.totalPurchases, 0.001)
        assertEquals(300.0, summary.totalCreditPurchases, 0.001)
    }

    // CASE K: Supplier payment reduces payable
    @Test
    fun testK_supplierPaymentReducesPayable() {
        val purchase = Purchase(
            id = "pur_sup_2",
            invoiceNumber = "PUR-002",
            supplierId = supplierId1,
            purchaseDate = "2026-10-01",
            totalAmount = 600.0,
            paidAmount = 0.0,
            creditAmount = 600.0,
            paymentStatus = "UNPAID",
            financialAccountId = "acc_cash",
            status = "ACTIVE"
        )
        val payment = SupplierPayment(
            id = "spay_1",
            supplierId = supplierId1,
            amount = 250.0,
            paymentDate = "2026-10-02",
            financialAccountId = "acc_cash",
            status = "ACTIVE"
        )

        val summary = SupplierLedgerCalculator.calculateSupplierBalance(
            supplierId = supplierId1,
            purchases = listOf(purchase),
            payments = listOf(payment)
        )

        assertEquals("Supplier payable reduced by payment (600 - 250 = 350)", 350.0, summary.balance, 0.001)
        assertEquals(250.0, summary.totalPayments, 0.001)
    }

    // CASE L: Supplier return reduces payable where applicable
    @Test
    fun testL_supplierReturnReducesPayableWhereApplicable() {
        val purchase = Purchase(
            id = "pur_sup_3",
            invoiceNumber = "PUR-003",
            supplierId = supplierId1,
            purchaseDate = "2026-10-01",
            totalAmount = 500.0,
            paidAmount = 0.0,
            creditAmount = 500.0,
            paymentStatus = "UNPAID",
            financialAccountId = "acc_cash",
            status = "ACTIVE"
        )
        val purchaseReturn = PurchaseReturn(
            id = "pret_1",
            purchaseId = purchase.id,
            supplierId = supplierId1,
            returnDate = "2026-10-02",
            amount = 120.0,
            reason = "Damaged goods",
            status = "ACTIVE"
        )

        val summary = SupplierLedgerCalculator.calculateSupplierBalance(
            supplierId = supplierId1,
            purchases = listOf(purchase),
            returns = listOf(purchaseReturn)
        )

        assertEquals("Supplier payable reduced by return: 500 - 120 = 380", 380.0, summary.balance, 0.001)
        assertEquals(120.0, summary.totalReturns, 0.001)
    }

    // CASE M: Supplier reversal is excluded from active payable
    @Test
    fun testM_supplierReversalIsExcludedFromActivePayable() {
        val purchase = Purchase(
            id = "pur_sup_rev",
            invoiceNumber = "PUR-REV",
            supplierId = supplierId1,
            purchaseDate = "2026-10-01",
            totalAmount = 400.0,
            paidAmount = 0.0,
            creditAmount = 400.0,
            paymentStatus = "UNPAID",
            financialAccountId = "acc_cash",
            status = "REVERSED"
        )

        val summary = SupplierLedgerCalculator.calculateSupplierBalance(
            supplierId = supplierId1,
            purchases = listOf(purchase)
        )

        assertEquals("Reversed purchase produces 0 payable liability", 0.0, summary.balance, 0.001)
        assertEquals(0.0, summary.totalCreditPurchases, 0.001)
    }

    // CASE N: Statement balance equals authoritative ledger balance
    @Test
    fun testN_statementBalanceEqualsAuthoritativeLedgerBalance() {
        // Customer side
        val cOb = OpeningBalance(
            id = "ob_c_stmt",
            entityType = "CUSTOMER",
            entityId = customerId1,
            amount = 100.0,
            direction = "DEBIT",
            date = "2026-10-01"
        )
        val cSale = Sale(
            id = "s_c_stmt",
            invoiceNumber = "INV-STMT",
            customerId = customerId1,
            saleType = "CREDIT",
            totalAmount = 200.0,
            paidAmount = 0.0,
            creditAmount = 200.0,
            paymentStatus = "UNPAID",
            transactionDate = "2026-10-02",
            status = "ACTIVE"
        )
        val cPay = CustomerPayment(
            id = "p_c_stmt",
            customerId = customerId1,
            amount = 50.0,
            paymentMethodId = "pm_cash",
            financialAccountId = "acc_cash",
            transactionDate = "2026-10-03",
            status = "ACTIVE"
        )
        val cRet = SaleReturn(
            id = "r_c_stmt",
            saleId = cSale.id,
            customerId = customerId1,
            returnDate = "2026-10-04",
            reason = "Return",
            amount = 30.0,
            status = "ACTIVE"
        )

        val customerSummary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId1,
            sales = listOf(cSale),
            payments = listOf(cPay),
            openingBalances = listOf(cOb),
            saleReturns = listOf(cRet)
        )
        // 100 opening + 200 sale - 50 payment - 30 return = 220
        assertEquals(220.0, customerSummary.balance, 0.001)

        val customerLedgerEntries = CustomerLedgerCalculator.buildCustomerLedger(
            customerId1,
            sales = listOf(cSale),
            payments = listOf(cPay),
            openingBalances = listOf(cOb),
            saleReturns = listOf(cRet)
        )
        val customerStatementBalance = customerLedgerEntries.filter { it.operationStatus == OperationStatus.ACTIVE }
            .sumOf { it.debit - it.credit }
        assertEquals("Customer statement sum matches authoritative ledger balance", customerSummary.balance, customerStatementBalance, 0.001)

        // Supplier side
        val sOb = OpeningBalance(
            id = "ob_s_stmt",
            entityType = "SUPPLIER",
            entityId = supplierId1,
            amount = 200.0,
            direction = "CREDIT",
            date = "2026-10-01"
        )
        val sPur = Purchase(
            id = "p_s_stmt",
            invoiceNumber = "PUR-STMT",
            supplierId = supplierId1,
            purchaseDate = "2026-10-02",
            totalAmount = 300.0,
            paidAmount = 0.0,
            creditAmount = 300.0,
            paymentStatus = "UNPAID",
            financialAccountId = "acc_cash",
            status = "ACTIVE"
        )
        val sPay = SupplierPayment(
            id = "sp_s_stmt",
            supplierId = supplierId1,
            amount = 100.0,
            paymentDate = "2026-10-03",
            financialAccountId = "acc_cash",
            status = "ACTIVE"
        )
        val sRet = PurchaseReturn(
            id = "pr_s_stmt",
            purchaseId = sPur.id,
            supplierId = supplierId1,
            returnDate = "2026-10-04",
            amount = 40.0,
            reason = "Damaged",
            status = "ACTIVE"
        )

        val supplierSummary = SupplierLedgerCalculator.calculateSupplierBalance(
            supplierId1,
            purchases = listOf(sPur),
            payments = listOf(sPay),
            returns = listOf(sRet),
            openingBalances = listOf(sOb)
        )
        // 200 opening + 300 purchase - 100 payment - 40 return = 360
        assertEquals(360.0, supplierSummary.balance, 0.001)

        val supplierLedgerEntries = SupplierLedgerCalculator.buildSupplierLedger(
            supplierId1,
            purchases = listOf(sPur),
            payments = listOf(sPay),
            returns = listOf(sRet),
            openingBalances = listOf(sOb)
        )
        val finalSupplierRunningBalance = supplierLedgerEntries.last().runningBalance
        assertEquals("Supplier statement final running balance matches authoritative ledger balance", supplierSummary.balance, finalSupplierRunningBalance, 0.001)
    }
}
