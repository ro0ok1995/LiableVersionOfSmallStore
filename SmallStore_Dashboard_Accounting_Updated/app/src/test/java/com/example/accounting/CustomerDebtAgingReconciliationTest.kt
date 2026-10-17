package com.example.accounting

import com.example.model.CustomerAccount
import com.example.model.OperationStatus
import com.example.model.PaymentStatus
import com.example.model.SaleType
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.viewmodel.DebtAgingUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 4 — Step 4: Verification suite for Debt Aging reconciliation with Customer Ledger.
 *
 * Enforces:
 * 1. Debt aging is calculated from verified/net customer ledger rather than historical cumulative debt fields.
 * 2. Customer.totalDebt being positive must NEVER manufacture phantom outstanding debt.
 * 3. Exact reconciliation with CustomerLedgerCalculator.
 */
class CustomerDebtAgingReconciliationTest {

    private val customerId = "cust_aging_01"
    private val today = LocalDate.of(2026, 9, 20)

    private val baseCustomer = CustomerAccount(
        id = customerId,
        customerName = "عميل الآجل التجريبي",
        balance = 0.0,
        totalDebt = 0.0,
        phone = "0500123456"
    )

    // A. Credit sale creates aging
    @Test
    fun testA_creditSaleCreatesAging() {
        val tx = TransactionItem(
            id = "tx_credit_1",
            title = "فاتورة آجل",
            customerNameSnapshot = "عميل الآجل التجريبي",
            activityType = "شراء آجل",
            amount = 500.0,
            isCredit = true,
            date = "2026-09-10", // 10 days old -> bucket 0-30
            relativeTime = "10:00",
            customerId = customerId,
            creditAmount = 500.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            paymentStatus = PaymentStatus.UNPAID,
            operationStatus = OperationStatus.ACTIVE
        )

        val aging = DebtAgingUtils.calculateCustomerAging(baseCustomer, listOf(tx), today = today)

        assertEquals(500.0, aging.currentDebt, 0.0001)
        assertEquals(500.0, aging.bucket0To30, 0.0001)
        assertEquals(0.0, aging.bucket31To60, 0.0001)
        assertEquals(0.0, aging.bucket61To90, 0.0001)
        assertEquals(0.0, aging.bucket90Plus, 0.0001)
        assertEquals(1, aging.individualTransactions.size)
        assertEquals("tx_credit_1", aging.individualTransactions[0].transactionId)
        assertEquals(500.0, aging.individualTransactions[0].amount, 0.0001)
    }

    // B. Cash-only sale creates no aging
    @Test
    fun testB_cashOnlySaleCreatesNoAging() {
        val tx = TransactionItem(
            id = "tx_cash_1",
            title = "فاتورة نقدي",
            customerNameSnapshot = "عميل الآجل التجريبي",
            activityType = "شراء نقدي",
            amount = 350.0,
            isCredit = false,
            date = "2026-09-10",
            relativeTime = "10:00",
            customerId = customerId,
            paidAmount = 350.0,
            creditAmount = 0.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CASH,
            paymentStatus = PaymentStatus.PAID,
            operationStatus = OperationStatus.ACTIVE
        )

        val aging = DebtAgingUtils.calculateCustomerAging(baseCustomer, listOf(tx), today = today)

        assertEquals(0.0, aging.currentDebt, 0.0001)
        assertEquals(0.0, aging.bucket0To30, 0.0001)
        assertEquals(0.0, aging.bucket31To60, 0.0001)
        assertEquals(0.0, aging.bucket61To90, 0.0001)
        assertEquals(0.0, aging.bucket90Plus, 0.0001)
        assertTrue(aging.individualTransactions.isEmpty())
    }

    // C. Mixed sale ages only creditAmount
    @Test
    fun testC_mixedSaleAgesOnlyCreditAmount() {
        val tx = TransactionItem(
            id = "tx_mixed_1",
            title = "فاتورة مدمجة",
            customerNameSnapshot = "عميل الآجل التجريبي",
            activityType = "فاتورة مدمجة",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-15", // 5 days old -> bucket 0-30
            relativeTime = "10:00",
            customerId = customerId,
            paidAmount = 60.0,
            creditAmount = 40.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED,
            paymentStatus = PaymentStatus.PARTIAL,
            operationStatus = OperationStatus.ACTIVE
        )

        val aging = DebtAgingUtils.calculateCustomerAging(baseCustomer, listOf(tx), today = today)

        // Total was 100, but only creditAmount (40) is outstanding receivable
        assertEquals(40.0, aging.currentDebt, 0.0001)
        assertEquals(40.0, aging.bucket0To30, 0.0001)
        assertEquals(0.0, aging.bucket31To60, 0.0001)
        assertEquals(0.0, aging.bucket61To90, 0.0001)
        assertEquals(0.0, aging.bucket90Plus, 0.0001)
        assertEquals(1, aging.individualTransactions.size)
        assertEquals(40.0, aging.individualTransactions[0].amount, 0.0001)
    }

    // D. Payment reduces aging
    @Test
    fun testD_paymentReducesAging() {
        val txSale = TransactionItem(
            id = "tx_sale_1",
            title = "شراء آجل",
            customerNameSnapshot = "عميل الآجل التجريبي",
            activityType = "شراء آجل",
            amount = 300.0,
            isCredit = true,
            date = "2026-09-05", // 15 days old -> bucket 0-30
            relativeTime = "10:00",
            customerId = customerId,
            creditAmount = 300.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            operationStatus = OperationStatus.ACTIVE
        )
        val txPayment = TransactionItem(
            id = "tx_pay_1",
            title = "تسديد دفعة",
            customerNameSnapshot = "عميل الآجل التجريبي",
            activityType = "تسديد",
            amount = 100.0,
            isCredit = false,
            date = "2026-09-12",
            relativeTime = "11:00",
            customerId = customerId,
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            operationStatus = OperationStatus.ACTIVE
        )

        val aging = DebtAgingUtils.calculateCustomerAging(baseCustomer, listOf(txSale, txPayment), today = today)

        // 300 - 100 = 200 remaining debt
        assertEquals(200.0, aging.currentDebt, 0.0001)
        assertEquals(200.0, aging.bucket0To30, 0.0001)
        assertEquals(0.0, aging.bucket31To60, 0.0001)
        assertEquals(1, aging.individualTransactions.size)
        assertEquals(200.0, aging.individualTransactions[0].amount, 0.0001)
    }

    // E. Fully paid historical credit sale produces zero current aging
    @Test
    fun testE_fullyPaidHistoricalCreditSaleProducesZeroCurrentAging() {
        val txSale = TransactionItem(
            id = "tx_hist_sale",
            title = "شراء آجل قديم",
            customerNameSnapshot = "عميل الآجل التجريبي",
            activityType = "شراء آجل",
            amount = 800.0,
            isCredit = true,
            date = "2026-07-15", // 67 days old
            relativeTime = "10:00",
            customerId = customerId,
            creditAmount = 800.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            operationStatus = OperationStatus.ACTIVE
        )
        val txPayment = TransactionItem(
            id = "tx_hist_pay",
            title = "تسديد كامل المبلغ",
            customerNameSnapshot = "عميل الآجل التجريبي",
            activityType = "تسديد",
            amount = 800.0,
            isCredit = false,
            date = "2026-08-01",
            relativeTime = "11:00",
            customerId = customerId,
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            operationStatus = OperationStatus.ACTIVE
        )

        val aging = DebtAgingUtils.calculateCustomerAging(baseCustomer, listOf(txSale, txPayment), today = today)

        assertEquals(0.0, aging.currentDebt, 0.0001)
        assertEquals(0.0, aging.bucket0To30, 0.0001)
        assertEquals(0.0, aging.bucket31To60, 0.0001)
        assertEquals(0.0, aging.bucket61To90, 0.0001)
        assertEquals(0.0, aging.bucket90Plus, 0.0001)
        assertTrue(aging.individualTransactions.isEmpty())
    }

    // F. Reversed credit sale does not age
    @Test
    fun testF_reversedCreditSaleDoesNotAge() {
        val txReversed = TransactionItem(
            id = "tx_rev_sale",
            title = "فاتورة آجل ملغاة",
            customerNameSnapshot = "عميل الآجل التجريبي",
            activityType = "شراء آجل",
            amount = 450.0,
            isCredit = true,
            date = "2026-09-01",
            relativeTime = "10:00",
            customerId = customerId,
            creditAmount = 450.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            operationStatus = OperationStatus.REVERSED
        )

        val aging = DebtAgingUtils.calculateCustomerAging(baseCustomer, listOf(txReversed), today = today)

        assertEquals(0.0, aging.currentDebt, 0.0001)
        assertEquals(0.0, aging.bucket0To30, 0.0001)
        assertEquals(0.0, aging.bucket31To60, 0.0001)
        assertEquals(0.0, aging.bucket61To90, 0.0001)
        assertEquals(0.0, aging.bucket90Plus, 0.0001)
        assertTrue(aging.individualTransactions.isEmpty())
    }

    // G. Multiple invoices are allocated to correct aging buckets
    @Test
    fun testG_multipleInvoicesAllocatedToCorrectAgingBuckets() {
        // Today is 2026-09-20:
        // tx1: 2026-06-01 -> 111 days old (bucket: 90+ Days)
        val tx1 = TransactionItem(
            id = "tx_1_90plus",
            title = "فاتورة 1",
            customerNameSnapshot = "عميل الآجل التجريبي",
            activityType = "شراء آجل",
            amount = 100.0,
            isCredit = true,
            date = "2026-06-01",
            relativeTime = "10:00",
            customerId = customerId,
            creditAmount = 100.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT
        )
        // tx2: 2026-07-15 -> 67 days old (bucket: 61-90 Days)
        val tx2 = TransactionItem(
            id = "tx_2_61to90",
            title = "فاتورة 2",
            customerNameSnapshot = "عميل الآجل التجريبي",
            activityType = "شراء آجل",
            amount = 200.0,
            isCredit = true,
            date = "2026-07-15",
            relativeTime = "10:00",
            customerId = customerId,
            creditAmount = 200.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT
        )
        // tx3: 2026-08-15 -> 36 days old (bucket: 31-60 Days)
        val tx3 = TransactionItem(
            id = "tx_3_31to60",
            title = "فاتورة 3",
            customerNameSnapshot = "عميل الآجل التجريبي",
            activityType = "شراء آجل",
            amount = 300.0,
            isCredit = true,
            date = "2026-08-15",
            relativeTime = "10:00",
            customerId = customerId,
            creditAmount = 300.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT
        )
        // tx4: 2026-09-10 -> 10 days old (bucket: 0-30 Days)
        val tx4 = TransactionItem(
            id = "tx_4_0to30",
            title = "فاتورة 4",
            customerNameSnapshot = "عميل الآجل التجريبي",
            activityType = "شراء آجل",
            amount = 400.0,
            isCredit = true,
            date = "2026-09-10",
            relativeTime = "10:00",
            customerId = customerId,
            creditAmount = 400.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT
        )

        val txs = listOf(tx1, tx2, tx3, tx4)

        // Case 1: No payment, all 1000 outstanding
        val agingAll = DebtAgingUtils.calculateCustomerAging(baseCustomer, txs, today = today)
        assertEquals(1000.0, agingAll.currentDebt, 0.0001)
        assertEquals(400.0, agingAll.bucket0To30, 0.0001)
        assertEquals(300.0, agingAll.bucket31To60, 0.0001)
        assertEquals(200.0, agingAll.bucket61To90, 0.0001)
        assertEquals(100.0, agingAll.bucket90Plus, 0.0001)

        // Case 2: Customer pays 600.
        // FIFO payment application: oldest invoices (tx1: 100, tx2: 200, and 300 of tx3) are paid off.
        // The remaining 400 debt belongs to the newest invoice tx4 (0-30 bucket).
        val txPayment = TransactionItem(
            id = "tx_pay_600",
            title = "تسديد 600",
            customerNameSnapshot = "عميل الآجل التجريبي",
            activityType = "تسديد",
            amount = 600.0,
            isCredit = false,
            date = "2026-09-18",
            relativeTime = "12:00",
            customerId = customerId,
            transactionType = TransactionType.CUSTOMER_PAYMENT
        )
        val agingPaid = DebtAgingUtils.calculateCustomerAging(baseCustomer, txs + txPayment, today = today)
        assertEquals(400.0, agingPaid.currentDebt, 0.0001)
        assertEquals(400.0, agingPaid.bucket0To30, 0.0001)
        assertEquals(0.0, agingPaid.bucket31To60, 0.0001)
        assertEquals(0.0, agingPaid.bucket61To90, 0.0001)
        assertEquals(0.0, agingPaid.bucket90Plus, 0.0001)
        assertEquals(1, agingPaid.individualTransactions.size)
        assertEquals("tx_4_0to30", agingPaid.individualTransactions[0].transactionId)
        assertEquals(400.0, agingPaid.individualTransactions[0].amount, 0.0001)
    }

    // H. Aging total reconciles exactly with current customer ledger balance
    @Test
    fun testH_agingTotalReconcilesExactlyWithCurrentCustomerLedgerBalance() {
        val txs = listOf(
            // Credit sale 1
            TransactionItem(
                id = "tx_rec_1",
                customerNameSnapshot = "عميل الآجل التجريبي",
                activityType = "شراء آجل",
                amount = 250.0,
                isCredit = true,
                date = "2026-08-10",
                relativeTime = "10:00",
                customerId = customerId,
                creditAmount = 250.0,
                transactionType = TransactionType.SALE,
                saleType = SaleType.CREDIT
            ),
            // Mixed sale: total 150, paid 50, credit 100
            TransactionItem(
                id = "tx_rec_2",
                customerNameSnapshot = "عميل الآجل التجريبي",
                activityType = "فاتورة مدمجة",
                amount = 150.0,
                isCredit = true,
                date = "2026-08-25",
                relativeTime = "11:00",
                customerId = customerId,
                paidAmount = 50.0,
                creditAmount = 100.0,
                transactionType = TransactionType.SALE,
                saleType = SaleType.MIXED
            ),
            // Cash sale: total 200, credit 0
            TransactionItem(
                id = "tx_rec_3",
                customerNameSnapshot = "عميل الآجل التجريبي",
                activityType = "شراء نقدي",
                amount = 200.0,
                isCredit = false,
                date = "2026-09-01",
                relativeTime = "12:00",
                customerId = customerId,
                paidAmount = 200.0,
                creditAmount = 0.0,
                transactionType = TransactionType.SALE,
                saleType = SaleType.CASH
            ),
            // Payment: 120
            TransactionItem(
                id = "tx_rec_4",
                customerNameSnapshot = "عميل الآجل التجريبي",
                activityType = "تسديد",
                amount = 120.0,
                isCredit = false,
                date = "2026-09-05",
                relativeTime = "13:00",
                customerId = customerId,
                transactionType = TransactionType.CUSTOMER_PAYMENT
            ),
            // Reversed credit sale: 500
            TransactionItem(
                id = "tx_rec_5",
                customerNameSnapshot = "عميل الآجل التجريبي",
                activityType = "شراء آجل",
                amount = 500.0,
                isCredit = true,
                date = "2026-09-10",
                relativeTime = "14:00",
                customerId = customerId,
                creditAmount = 500.0,
                transactionType = TransactionType.SALE,
                saleType = SaleType.CREDIT,
                operationStatus = OperationStatus.REVERSED
            )
        )

        // Ledger calculation:
        // Credit sale 1: +250
        // Mixed sale: +100
        // Cash sale: +0
        // Payment: -120
        // Reversed: +0
        // Total expected ledger balance: 250 + 100 - 120 = 230.0
        val ledgerSummary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, txs)
        assertEquals(230.0, ledgerSummary.balance, 0.0001)

        val aging = DebtAgingUtils.calculateCustomerAging(baseCustomer, txs, today = today)

        // 1. Aging currentDebt == ledger balance
        assertEquals(ledgerSummary.balance, aging.currentDebt, 0.0001)
        // 2. Sum of buckets == currentDebt
        val sumOfBuckets = aging.bucket0To30 + aging.bucket31To60 + aging.bucket61To90 + aging.bucket90Plus
        assertEquals(aging.currentDebt, sumOfBuckets, 0.0001)
        // 3. Sum of active details == currentDebt
        val sumOfDetails = aging.individualTransactions.sumOf { it.amount }
        assertEquals(aging.currentDebt, sumOfDetails, 0.0001)
    }

    // I. Customer.totalDebt being positive must not create phantom outstanding debt
    @Test
    fun testI_customerTotalDebtBeingPositive_mustNotCreatePhantomOutstandingDebt() {
        // Customer object with large historical cumulative debt (e.g. 50,000)
        // but current balance is 0.0 and all credit purchases were fully paid
        val customerWithHistoricalTotalDebt = CustomerAccount(
            id = customerId,
            customerName = "عميل تاريخي مسدد",
            balance = 0.0,
            totalDebt = 50000.0, // Historical cumulative credit sales
            phone = "0509999999"
        )

        val historicalTxSale = TransactionItem(
            id = "tx_hist_50k",
            title = "مشتريات تاريخية",
            customerNameSnapshot = "عميل تاريخي مسدد",
            activityType = "شراء آجل",
            amount = 50000.0,
            isCredit = true,
            date = "2026-01-01",
            relativeTime = "10:00",
            customerId = customerId,
            creditAmount = 50000.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT
        )
        val historicalTxPay = TransactionItem(
            id = "tx_hist_50k_pay",
            title = "تسديد تاريخي كامل",
            customerNameSnapshot = "عميل تاريخي مسدد",
            activityType = "تسديد",
            amount = 50000.0,
            isCredit = false,
            date = "2026-01-15",
            relativeTime = "10:00",
            customerId = customerId,
            transactionType = TransactionType.CUSTOMER_PAYMENT
        )

        val txs = listOf(historicalTxSale, historicalTxPay)

        // Customer aging:
        val aging = DebtAgingUtils.calculateCustomerAging(customerWithHistoricalTotalDebt, txs, today = today)

        assertEquals(0.0, aging.currentDebt, 0.0001)
        assertEquals(0.0, aging.bucket0To30, 0.0001)
        assertEquals(0.0, aging.bucket31To60, 0.0001)
        assertEquals(0.0, aging.bucket61To90, 0.0001)
        assertEquals(0.0, aging.bucket90Plus, 0.0001)
        assertTrue(aging.individualTransactions.isEmpty())

        // Store summary:
        val storeSummary = DebtAgingUtils.calculateStoreDebtAgingSummary(
            customers = listOf(customerWithHistoricalTotalDebt),
            allTransactions = txs,
            today = today
        )
        assertEquals(0.0, storeSummary.totalOutstandingDebt, 0.0001)
        assertEquals(0, storeSummary.totalCustomersWithDebtCount)
        assertEquals(0.0, storeSummary.sum0To30, 0.0001)
        assertEquals(0.0, storeSummary.sum31To60, 0.0001)
        assertEquals(0.0, storeSummary.sum61To90, 0.0001)
        assertEquals(0.0, storeSummary.sum90Plus, 0.0001)
    }

    // Additional test: Customer with totalDebt > 0 and no transactions in allTransactions
    // must not produce phantom debt if balance is 0
    @Test
    fun testCustomerWithPositiveTotalDebtAndZeroBalance_producesZeroAging() {
        val customerZeroBalPositiveDebt = CustomerAccount(
            id = "cust_phantom_check",
            customerName = "عميل رصيد صفر",
            balance = 0.0,
            totalDebt = 12000.0,
            phone = "0507777777"
        )

        val aging = DebtAgingUtils.calculateCustomerAging(customerZeroBalPositiveDebt, emptyList(), today = today)

        assertEquals(0.0, aging.currentDebt, 0.0001)
        assertEquals(0.0, aging.bucket0To30, 0.0001)
        assertEquals(0.0, aging.bucket31To60, 0.0001)
        assertEquals(0.0, aging.bucket61To90, 0.0001)
        assertEquals(0.0, aging.bucket90Plus, 0.0001)
        assertTrue(aging.individualTransactions.isEmpty())
    }
}
