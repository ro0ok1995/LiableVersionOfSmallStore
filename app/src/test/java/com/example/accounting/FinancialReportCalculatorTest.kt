package com.example.accounting

import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.SaleReturn
import com.example.data.db.SaleReturnLine
import com.example.model.OperationStatus
import com.example.model.SaleType
import com.example.model.TransactionItem
import com.example.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure domain unit test suite for [FinancialReportCalculator].
 *
 * Verifies all 9 required cases from Phase 4 Step 2 user specification:
 * 1. Cash-only sale.
 * 2. Credit-only sale.
 * 3. Mixed sale.
 * 4. Reversed sale.
 * 5. Reversed payment.
 * 6. Multiple sales with mixed payment states.
 * 7. Totals reconcile with ledger movements.
 * 8. Zero-credit mixed/cash-only edge case.
 * 9. Zero-paid credit-only edge case.
 *
 * Also verifies strict independence from localized / display strings.
 */
class FinancialReportCalculatorTest {

    private val customerId = "cust_report_test_99"

    /**
     * Test 1: Cash-only sale.
     * total = 100, paidAmount = 100, creditAmount = 0
     * Then:
     * - cash sales = 100
     * - credit sales = 0
     * - receivable increase = 0
     */
    @Test
    fun test1_cashOnlySale() {
        val tx = TransactionItem(
            id = "tx_cash_1",
            amount = 100.0,
            isCredit = false,
            date = "2026-09-22",
            relativeTime = "اليوم",
            activityType = "شراء نقدي",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CASH,
            paidAmount = 100.0,
            creditAmount = 0.0,
            operationStatus = OperationStatus.ACTIVE
        )

        val totals = FinancialReportCalculator.calculate(listOf(tx))

        assertEquals("Cash sales must be 100.0", 100.0, totals.cashSales, 0.0001)
        assertEquals("Credit sales must be 0.0", 0.0, totals.creditSales, 0.0001)
        assertEquals("Total sales must be 100.0", 100.0, totals.totalSales, 0.0001)
        assertEquals("Customer payments must be 0.0", 0.0, totals.customerPayments, 0.0001)
        assertEquals("Receivable increase must be 0.0", 0.0, totals.netReceivableIncrease, 0.0001)
        assertEquals("Active transaction count must be 1", 1, totals.activeTransactionCount)
        assertEquals("Reversed count must be 0", 0, totals.reversedTransactionCount)
        assertEquals("Cash sale count must be 1", 1, totals.cashSaleCount)
        assertEquals("Credit sale count must be 0", 0, totals.creditSaleCount)
        assertEquals("Mixed sale count must be 0", 0, totals.mixedSaleCount)
    }

    /**
     * Test 2: Credit-only sale.
     * total = 100, paidAmount = 0, creditAmount = 100
     * Then:
     * - cash sales = 0
     * - credit sales = 100
     * - receivable increase = 100
     */
    @Test
    fun test2_creditOnlySale() {
        val tx = TransactionItem(
            id = "tx_credit_1",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-22",
            relativeTime = "اليوم",
            activityType = "شراء آجل",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            paidAmount = 0.0,
            creditAmount = 100.0,
            operationStatus = OperationStatus.ACTIVE
        )

        val totals = FinancialReportCalculator.calculate(listOf(tx))

        assertEquals("Cash sales must be 0.0", 0.0, totals.cashSales, 0.0001)
        assertEquals("Credit sales must be 100.0", 100.0, totals.creditSales, 0.0001)
        assertEquals("Total sales must be 100.0", 100.0, totals.totalSales, 0.0001)
        assertEquals("Customer payments must be 0.0", 0.0, totals.customerPayments, 0.0001)
        assertEquals("Receivable increase must be 100.0", 100.0, totals.netReceivableIncrease, 0.0001)
        assertEquals("Active transaction count must be 1", 1, totals.activeTransactionCount)
        assertEquals("Reversed count must be 0", 0, totals.reversedTransactionCount)
        assertEquals("Cash sale count must be 0", 0, totals.cashSaleCount)
        assertEquals("Credit sale count must be 1", 1, totals.creditSaleCount)
        assertEquals("Mixed sale count must be 0", 0, totals.mixedSaleCount)
    }

    /**
     * Test 3: Mixed sale.
     * total = 100, paidAmount = 60, creditAmount = 40
     * Then:
     * - cash sales = 60
     * - credit sales = 40
     * - customer receivable increase = 40
     * - the sale must NOT be counted as 100 of credit sales.
     */
    @Test
    fun test3_mixedSale() {
        val tx = TransactionItem(
            id = "tx_mixed_1",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-22",
            relativeTime = "اليوم",
            activityType = "شراء مشرك",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED,
            paidAmount = 60.0,
            creditAmount = 40.0,
            operationStatus = OperationStatus.ACTIVE
        )

        val totals = FinancialReportCalculator.calculate(listOf(tx))

        assertEquals("Cash sales must be exactly paidAmount (60.0)", 60.0, totals.cashSales, 0.0001)
        assertEquals("Credit sales must be exactly creditAmount (40.0), NOT total (100.0)", 40.0, totals.creditSales, 0.0001)
        assertEquals("Total sales must be 100.0", 100.0, totals.totalSales, 0.0001)
        assertEquals("Customer receivable increase must be 40.0, NOT 100.0", 40.0, totals.netReceivableIncrease, 0.0001)
        assertEquals("Mixed sale count must be 1", 1, totals.mixedSaleCount)
        assertEquals("Cash sale count must be 0", 0, totals.cashSaleCount)
        assertEquals("Credit sale count must be 0", 0, totals.creditSaleCount)
    }

    /**
     * Test 4: Reversed sale.
     * Transactions with OperationStatus.REVERSED must not contribute to active financial totals.
     */
    @Test
    fun test4_reversedSale() {
        val txReversed = TransactionItem(
            id = "tx_reversed_sale",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-22",
            relativeTime = "اليوم",
            activityType = "شراء مشرك ملغي",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED,
            paidAmount = 60.0,
            creditAmount = 40.0,
            operationStatus = OperationStatus.REVERSED
        )

        val totals = FinancialReportCalculator.calculate(listOf(txReversed))

        assertEquals("Active cash sales must be 0.0 for reversed sale", 0.0, totals.cashSales, 0.0001)
        assertEquals("Active credit sales must be 0.0 for reversed sale", 0.0, totals.creditSales, 0.0001)
        assertEquals("Active total sales must be 0.0 for reversed sale", 0.0, totals.totalSales, 0.0001)
        assertEquals("Active receivable increase must be 0.0 for reversed sale", 0.0, totals.netReceivableIncrease, 0.0001)
        assertEquals("Active transaction count must be 0", 0, totals.activeTransactionCount)
        assertEquals("Reversed transaction count must be 1", 1, totals.reversedTransactionCount)
        assertEquals("Reversed sales volume must be 100.0", 100.0, totals.reversedSalesVolume, 0.0001)
        assertEquals("Total reversed volume must be 100.0", 100.0, totals.totalReversedVolume, 0.0001)
    }

    /**
     * Test 5: Reversed payment.
     * Customer payment with OperationStatus.REVERSED must not reduce receivables or count as active payment.
     */
    @Test
    fun test5_reversedPayment() {
        val txReversedPayment = TransactionItem(
            id = "tx_reversed_pmt",
            amount = 50.0,
            isCredit = false,
            date = "2026-09-22",
            relativeTime = "اليوم",
            activityType = "تسديد ملغي",
            customerId = customerId,
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            operationStatus = OperationStatus.REVERSED
        )

        val totals = FinancialReportCalculator.calculate(listOf(txReversedPayment))

        assertEquals("Active customer payments must be 0.0 for reversed payment", 0.0, totals.customerPayments, 0.0001)
        assertEquals("Active receivable increase must be 0.0", 0.0, totals.netReceivableIncrease, 0.0001)
        assertEquals("Active transaction count must be 0", 0, totals.activeTransactionCount)
        assertEquals("Reversed transaction count must be 1", 1, totals.reversedTransactionCount)
        assertEquals("Reversed payments volume must be 50.0", 50.0, totals.reversedPaymentsVolume, 0.0001)
        assertEquals("Total reversed volume must be 50.0", 50.0, totals.totalReversedVolume, 0.0001)
    }

    /**
     * Test 6: Multiple sales with mixed payment states.
     * Verifies that in a batch of cash, credit, mixed, payments, and reversals,
     * totals are aggregated strictly and deterministically.
     */
    @Test
    fun test6_multipleSalesWithMixedPaymentStates() {
        val transactions = listOf(
            // Cash sale: 100 (cash: 100, credit: 0)
            TransactionItem(
                id = "tx_1",
                activityType = "شراء نقدي",
                amount = 100.0,
                isCredit = false,
                date = "2026-09-20",
                relativeTime = "الآن",
                customerId = customerId,
                transactionType = TransactionType.SALE,
                saleType = SaleType.CASH,
                paidAmount = 100.0,
                creditAmount = 0.0
            ),
            // Credit sale: 200 (cash: 0, credit: 200)
            TransactionItem(
                id = "tx_2",
                activityType = "شراء آجل",
                amount = 200.0,
                isCredit = true,
                date = "2026-09-20",
                relativeTime = "الآن",
                customerId = customerId,
                transactionType = TransactionType.SALE,
                saleType = SaleType.CREDIT,
                paidAmount = 0.0,
                creditAmount = 200.0
            ),
            // Mixed sale 1: 150 (cash: 50, credit: 100)
            TransactionItem(
                id = "tx_3",
                activityType = "شراء مشرك",
                amount = 150.0,
                isCredit = true,
                date = "2026-09-21",
                relativeTime = "الآن",
                customerId = customerId,
                transactionType = TransactionType.SALE,
                saleType = SaleType.MIXED,
                paidAmount = 50.0,
                creditAmount = 100.0
            ),
            // Mixed sale 2: 80 (cash: 60, credit: 20)
            TransactionItem(
                id = "tx_4",
                activityType = "شراء مشرك",
                amount = 80.0,
                isCredit = true,
                date = "2026-09-21",
                relativeTime = "الآن",
                customerId = customerId,
                transactionType = TransactionType.SALE,
                saleType = SaleType.MIXED,
                paidAmount = 60.0,
                creditAmount = 20.0
            ),
            // Active payment: 70
            TransactionItem(
                id = "tx_5",
                activityType = "تسديد",
                amount = 70.0,
                isCredit = false,
                date = "2026-09-22",
                relativeTime = "الآن",
                customerId = customerId,
                transactionType = TransactionType.CUSTOMER_PAYMENT
            ),
            // Reversed credit sale: 300 (REVERSED)
            TransactionItem(
                id = "tx_6_rev",
                activityType = "شراء آجل ملغي",
                amount = 300.0,
                isCredit = true,
                date = "2026-09-22",
                relativeTime = "الآن",
                customerId = customerId,
                transactionType = TransactionType.SALE,
                saleType = SaleType.CREDIT,
                creditAmount = 300.0,
                operationStatus = OperationStatus.REVERSED
            ),
            // Reversed payment: 45 (REVERSED)
            TransactionItem(
                id = "tx_7_rev",
                activityType = "تسديد ملغي",
                amount = 45.0,
                isCredit = false,
                date = "2026-09-22",
                relativeTime = "الآن",
                customerId = customerId,
                transactionType = TransactionType.CUSTOMER_PAYMENT,
                operationStatus = OperationStatus.REVERSED
            )
        )

        val totals = FinancialReportCalculator.calculate(transactions)

        // Expected active totals:
        // Cash sales = 100 + 0 + 50 + 60 = 210.0
        assertEquals(210.0, totals.cashSales, 0.0001)
        // Credit sales = 0 + 200 + 100 + 20 = 320.0
        assertEquals(320.0, totals.creditSales, 0.0001)
        // Total sales = 210 + 320 = 530.0
        assertEquals(530.0, totals.totalSales, 0.0001)
        // Customer payments = 70.0
        assertEquals(70.0, totals.customerPayments, 0.0001)
        // Net receivable increase = 320 (credit sales) - 70 (payments) = 250.0
        assertEquals(250.0, totals.netReceivableIncrease, 0.0001)

        // Counts:
        assertEquals(5, totals.activeTransactionCount)
        assertEquals(2, totals.reversedTransactionCount)
        assertEquals(1, totals.cashSaleCount)
        assertEquals(1, totals.creditSaleCount)
        assertEquals(2, totals.mixedSaleCount)
        assertEquals(1, totals.customerPaymentCount)

        // Reversed volumes:
        assertEquals(300.0, totals.reversedSalesVolume, 0.0001)
        assertEquals(45.0, totals.reversedPaymentsVolume, 0.0001)
        assertEquals(345.0, totals.totalReversedVolume, 0.0001)
    }

    /**
     * Test 7: Totals reconcile with ledger movements.
     * Verifies that for any set of transactions for a customer,
     * the net receivable increase from [FinancialReportCalculator] matches
     * the final balance from [CustomerLedgerCalculator].
     */
    @Test
    fun test7_totalsReconcileWithLedgerMovements() {
        val transactions = listOf(
            // Mixed sale: total 120, paid 50, credit 70
            TransactionItem(
                id = "tx_rec_1",
                activityType = "شراء مشرك",
                amount = 120.0,
                isCredit = true,
                date = "2026-09-01",
                relativeTime = "الآن",
                customerId = customerId,
                transactionType = TransactionType.SALE,
                saleType = SaleType.MIXED,
                paidAmount = 50.0,
                creditAmount = 70.0
            ),
            // Credit sale: total 80, credit 80
            TransactionItem(
                id = "tx_rec_2",
                activityType = "شراء آجل",
                amount = 80.0,
                isCredit = true,
                date = "2026-09-02",
                relativeTime = "الآن",
                customerId = customerId,
                transactionType = TransactionType.SALE,
                saleType = SaleType.CREDIT,
                paidAmount = 0.0,
                creditAmount = 80.0
            ),
            // Customer payment: 40
            TransactionItem(
                id = "tx_rec_3",
                activityType = "تسديد",
                amount = 40.0,
                isCredit = false,
                date = "2026-09-03",
                relativeTime = "الآن",
                customerId = customerId,
                transactionType = TransactionType.CUSTOMER_PAYMENT
            ),
            // Pure cash sale for the customer: 60 (paid 60, credit 0)
            TransactionItem(
                id = "tx_rec_4",
                activityType = "شراء نقدي",
                amount = 60.0,
                isCredit = false,
                date = "2026-09-04",
                relativeTime = "الآن",
                customerId = customerId,
                transactionType = TransactionType.SALE,
                saleType = SaleType.CASH,
                paidAmount = 60.0,
                creditAmount = 0.0
            ),
            // Reversed credit sale: 200 (REVERSED)
            TransactionItem(
                id = "tx_rec_5_rev",
                activityType = "شراء آجل ملغي",
                amount = 200.0,
                isCredit = true,
                date = "2026-09-05",
                relativeTime = "الآن",
                customerId = customerId,
                transactionType = TransactionType.SALE,
                saleType = SaleType.CREDIT,
                creditAmount = 200.0,
                operationStatus = OperationStatus.REVERSED
            )
        )

        val reportTotals = FinancialReportCalculator.calculateCustomerTotals(customerId, transactions)
        val ledgerSummary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, transactions)

        // 1. Credit sales must reconcile
        assertEquals(
            "Report credit sales matches ledger total credit sales",
            ledgerSummary.totalCreditSales,
            reportTotals.creditSales,
            0.0001
        )

        // 2. Payments must reconcile
        assertEquals(
            "Report customer payments matches ledger total payments",
            ledgerSummary.totalPayments,
            reportTotals.customerPayments,
            0.0001
        )

        // 3. Net receivable change must reconcile with ledger balance
        assertEquals(
            "Report net receivable increase matches ledger balance",
            ledgerSummary.balance,
            reportTotals.netReceivableIncrease,
            0.0001
        )

        // 4. Exact expected balance: 70 (mixed credit) + 80 (credit) - 40 (pmt) = 110.0
        assertEquals(110.0, reportTotals.netReceivableIncrease, 0.0001)
        assertEquals(110.0, ledgerSummary.balance, 0.0001)
    }

    /**
     * Test 8: Zero-credit mixed/cash-only edge case.
     * total = 100, paidAmount = 100, creditAmount = 0, saleType = MIXED
     * Then:
     * - cash sales = 100
     * - credit sales = 0
     * - receivable increase = 0
     */
    @Test
    fun test8_zeroCreditMixedCashOnlyEdgeCase() {
        val tx = TransactionItem(
            id = "tx_zero_credit",
            amount = 100.0,
            isCredit = false,
            date = "2026-09-22",
            relativeTime = "اليوم",
            activityType = "شراء مشرك",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED,
            paidAmount = 100.0,
            creditAmount = 0.0,
            operationStatus = OperationStatus.ACTIVE
        )

        val totals = FinancialReportCalculator.calculate(listOf(tx))

        assertEquals("Cash sales must be 100.0", 100.0, totals.cashSales, 0.0001)
        assertEquals("Credit sales must be 0.0", 0.0, totals.creditSales, 0.0001)
        assertEquals("Total sales must be 100.0", 100.0, totals.totalSales, 0.0001)
        assertEquals("Receivable increase must be 0.0", 0.0, totals.netReceivableIncrease, 0.0001)
    }

    /**
     * Test 9: Zero-paid credit-only edge case.
     * total = 100, paidAmount = 0, creditAmount = 100, saleType = MIXED
     * Then:
     * - cash sales = 0
     * - credit sales = 100
     * - receivable increase = 100
     */
    @Test
    fun test9_zeroPaidCreditOnlyEdgeCase() {
        val tx = TransactionItem(
            id = "tx_zero_paid",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-22",
            relativeTime = "اليوم",
            activityType = "شراء مشرك",
            customerId = customerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED,
            paidAmount = 0.0,
            creditAmount = 100.0,
            operationStatus = OperationStatus.ACTIVE
        )

        val totals = FinancialReportCalculator.calculate(listOf(tx))

        assertEquals("Cash sales must be 0.0", 0.0, totals.cashSales, 0.0001)
        assertEquals("Credit sales must be 100.0", 100.0, totals.creditSales, 0.0001)
        assertEquals("Total sales must be 100.0", 100.0, totals.totalSales, 0.0001)
        assertEquals("Receivable increase must be 100.0", 100.0, totals.netReceivableIncrease, 0.0001)
    }

    /**
     * Test 10: Verify calculator does NOT depend on localized or display strings.
     * Pass arbitrary, contradictory, or empty strings in activityType, title, notes,
     * and verify calculation depends purely on the typed fields.
     */
    @Test
    fun test10_calculatorDoesNotDependOnLocalizedOrDisplayStrings() {
        val mixedSaleWithMisleadingText = TransactionItem(
            id = "tx_misleading_1",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-22",
            relativeTime = "منذ قرن",
            // Deliberately contradictory Arabic & English labels:
            activityType = "تسديد دفعات نقدية بالكامل Cash Payment Only",
            title = "دفعة سداد حساب قديم",
            notes = "تم استلام المبلغ نقدا بدون ديون",
            customerId = customerId,
            // The TYPED truth:
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED,
            paidAmount = 60.0,
            creditAmount = 40.0,
            operationStatus = OperationStatus.ACTIVE
        )

        val paymentWithMisleadingText = TransactionItem(
            id = "tx_misleading_2",
            amount = 35.0,
            isCredit = false,
            date = "2026-09-22",
            relativeTime = "منذ دقيقة",
            // Deliberately contradictory sale text:
            activityType = "شراء بضاعة بالدين Credit Debt Sale",
            title = "فاتورة شراء آجل",
            notes = "مشتريات على الحساب",
            customerId = customerId,
            // The TYPED truth:
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            operationStatus = OperationStatus.ACTIVE
        )

        val totals = FinancialReportCalculator.calculate(listOf(mixedSaleWithMisleadingText, paymentWithMisleadingText))

        // tx_misleading_1 must be evaluated purely as typed MIXED SALE (cash 60, credit 40),
        // completely ignoring its contradictory "تسديد / Cash Payment" activityType!
        assertEquals("Cash sales must be 60.0", 60.0, totals.cashSales, 0.0001)
        assertEquals("Credit sales must be 40.0", 40.0, totals.creditSales, 0.0001)
        assertEquals("Total sales must be 100.0", 100.0, totals.totalSales, 0.0001)

        // tx_misleading_2 must be evaluated purely as typed CUSTOMER_PAYMENT (35.0),
        // completely ignoring its contradictory "شراء بالدين / Credit Sale" activityType!
        assertEquals("Customer payments must be 35.0", 35.0, totals.customerPayments, 0.0001)

        // Net receivable change: 40.0 (credit sale portion) - 35.0 (payment) = 5.0
        assertEquals("Net receivable increase must be 5.0", 5.0, totals.netReceivableIncrease, 0.0001)
    }

    /**
     * Test 11: Calculate directly from Room Sale entities.
     */
    @Test
    fun test11_calculateFromSaleEntities() {
        val sales = listOf(
            Sale(
                id = "s_1",
                invoiceNumber = "INV-001",
                customerId = customerId,
                saleType = "CASH",
                totalAmount = 100.0,
                paidAmount = 100.0,
                creditAmount = 0.0,
                paymentStatus = "PAID",
                transactionDate = "2026-09-22",
                status = "ACTIVE"
            ),
            Sale(
                id = "s_2",
                invoiceNumber = "INV-002",
                customerId = customerId,
                saleType = "CREDIT",
                totalAmount = 200.0,
                paidAmount = 0.0,
                creditAmount = 200.0,
                paymentStatus = "UNPAID",
                transactionDate = "2026-09-22",
                status = "ACTIVE"
            ),
            Sale(
                id = "s_3",
                invoiceNumber = "INV-003",
                customerId = customerId,
                saleType = "MIXED",
                totalAmount = 150.0,
                paidAmount = 60.0,
                creditAmount = 90.0,
                paymentStatus = "PARTIAL",
                transactionDate = "2026-09-22",
                status = "ACTIVE"
            ),
            Sale(
                id = "s_4_rev",
                invoiceNumber = "INV-004",
                customerId = customerId,
                saleType = "CREDIT",
                totalAmount = 500.0,
                paidAmount = 0.0,
                creditAmount = 500.0,
                paymentStatus = "UNPAID",
                transactionDate = "2026-09-22",
                status = "REVERSED"
            )
        )

        val totals = FinancialReportCalculator.calculateFromSales(sales)

        // Cash sales: 100 + 0 + 60 = 160.0
        assertEquals(160.0, totals.cashSales, 0.0001)
        // Credit sales: 0 + 200 + 90 = 290.0
        assertEquals(290.0, totals.creditSales, 0.0001)
        // Total sales: 160 + 290 = 450.0
        assertEquals(450.0, totals.totalSales, 0.0001)
        // Receivable increase: 290.0
        assertEquals(290.0, totals.netReceivableIncrease, 0.0001)
        // Counts:
        assertEquals(3, totals.activeTransactionCount)
        assertEquals(1, totals.reversedTransactionCount)
        assertEquals(1, totals.cashSaleCount)
        assertEquals(1, totals.creditSaleCount)
        assertEquals(1, totals.mixedSaleCount)
        assertEquals(500.0, totals.reversedSalesVolume, 0.0001)
    }

    // =========================================================================
    // Phase 4 Step 1: Central COGS, Gross Profit & Gross Margin Verification
    // =========================================================================

    /**
     * Test A: Normal sale.
     * Revenue 100, COGS 60, Gross Profit 40, Gross Margin 40% (0.40)
     */
    @Test
    fun testPhase4Step1_TestA_normalSale() {
        val sale = Sale(
            id = "s_normal",
            invoiceNumber = "INV-P4-001",
            customerId = customerId,
            saleType = "CASH",
            totalAmount = 100.0,
            paidAmount = 100.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-09-29",
            status = "ACTIVE"
        )
        val line = SaleLine(
            id = "sl_1",
            saleId = "s_normal",
            productId = "prod_1",
            productNameSnapshot = "Product 1",
            quantity = 2,
            unitPrice = 50.0,
            costPriceAtSale = 30.0, // 2 * 30 = 60 COGS
            subtotal = 100.0
        )

        val totals = FinancialReportCalculator.calculateFromSales(
            sales = listOf(sale),
            saleLines = listOf(line)
        )

        assertEquals(100.0, totals.totalSales, 0.0001)
        assertEquals(100.0, totals.netSales, 0.0001)
        assertEquals(60.0, totals.cogs, 0.0001)
        assertEquals(40.0, totals.grossProfit, 0.0001)
        assertEquals(0.40, totals.grossMargin, 0.0001)
        assertEquals(40.0, totals.grossMarginPercent, 0.0001)
    }

    /**
     * Test B: Multiple sale lines.
     * Verify COGS equals the sum of quantity * frozen historical cost.
     */
    @Test
    fun testPhase4Step1_TestB_multipleSaleLines() {
        val sale = Sale(
            id = "s_multi",
            invoiceNumber = "INV-P4-002",
            customerId = customerId,
            saleType = "MIXED",
            totalAmount = 250.0,
            paidAmount = 100.0,
            creditAmount = 150.0,
            paymentStatus = "PARTIAL",
            transactionDate = "2026-09-29",
            status = "ACTIVE"
        )
        val line1 = SaleLine(
            id = "sl_m1",
            saleId = "s_multi",
            productId = "prod_1",
            productNameSnapshot = "Product 1",
            quantity = 3,
            unitPrice = 50.0,
            costPriceAtSale = 25.0, // 3 * 25 = 75
            subtotal = 150.0
        )
        val line2 = SaleLine(
            id = "sl_m2",
            saleId = "s_multi",
            productId = "prod_2",
            productNameSnapshot = "Product 2",
            quantity = 2,
            unitPrice = 50.0,
            costPriceAtSale = 35.0, // 2 * 35 = 70
            subtotal = 100.0
        )

        // Expected COGS = 75 + 70 = 145.0
        val cogs = FinancialReportCalculator.calculateCogs(
            sales = listOf(sale),
            saleLines = listOf(line1, line2)
        )
        assertEquals(145.0, cogs, 0.0001)

        val totals = FinancialReportCalculator.calculateFromSales(
            sales = listOf(sale),
            saleLines = listOf(line1, line2)
        )
        assertEquals(250.0, totals.netSales, 0.0001)
        assertEquals(145.0, totals.cogs, 0.0001)
        assertEquals(105.0, totals.grossProfit, 0.0001)
        assertEquals(105.0 / 250.0, totals.grossMargin, 0.0001)
    }

    /**
     * Test C: Sale return.
     * Verify returned quantity reverses its historical COGS contribution.
     */
    @Test
    fun testPhase4Step1_TestC_saleReturnReversesCogs() {
        val sale = Sale(
            id = "s_ret_orig",
            invoiceNumber = "INV-P4-003",
            customerId = customerId,
            saleType = "CASH",
            totalAmount = 200.0,
            paidAmount = 200.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-09-29",
            status = "ACTIVE"
        )
        val saleLine = SaleLine(
            id = "sl_ret_1",
            saleId = "s_ret_orig",
            productId = "prod_ret",
            productNameSnapshot = "Returnable Item",
            quantity = 4,
            unitPrice = 50.0,
            costPriceAtSale = 30.0, // Initial COGS = 4 * 30 = 120
            subtotal = 200.0
        )

        val saleReturn = SaleReturn(
            id = "sr_1",
            saleId = "s_ret_orig",
            customerId = customerId,
            returnDate = "2026-09-29",
            reason = "Customer changed mind",
            amount = 50.0, // 1 item returned
            status = "ACTIVE"
        )
        val returnLine = SaleReturnLine(
            id = "srl_1",
            saleReturnId = "sr_1",
            saleLineId = "sl_ret_1",
            productId = "prod_ret",
            productNameSnapshot = "Returnable Item",
            quantity = 1,
            unitPrice = 50.0,
            costPriceAtReturn = 30.0, // Reverses 1 * 30 = 30 COGS
            subtotal = 50.0
        )

        val totals = FinancialReportCalculator.calculateFromSales(
            sales = listOf(sale),
            saleLines = listOf(saleLine),
            saleReturns = listOf(saleReturn),
            saleReturnLines = listOf(returnLine)
        )

        // Net Sales = 200 - 50 = 150.0
        assertEquals(150.0, totals.netSales, 0.0001)
        // Net COGS = 120 - 30 = 90.0
        assertEquals(90.0, totals.cogs, 0.0001)
        // Gross Profit = 150 - 90 = 60.0
        assertEquals(60.0, totals.grossProfit, 0.0001)
        // Gross Margin = 60 / 150 = 0.40 (40%)
        assertEquals(0.40, totals.grossMargin, 0.0001)
    }

    /**
     * Test D: Reversed sale.
     * Verify it does not contribute to active COGS/profit.
     */
    @Test
    fun testPhase4Step1_TestD_reversedSaleExclusion() {
        val activeSale = Sale(
            id = "s_active",
            invoiceNumber = "INV-P4-004A",
            customerId = customerId,
            saleType = "CASH",
            totalAmount = 100.0,
            paidAmount = 100.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-09-29",
            status = "ACTIVE"
        )
        val activeLine = SaleLine(
            id = "sl_act",
            saleId = "s_active",
            productId = "prod_a",
            productNameSnapshot = "Active Item",
            quantity = 2,
            unitPrice = 50.0,
            costPriceAtSale = 30.0, // COGS = 60.0
            subtotal = 100.0
        )

        val reversedSale = Sale(
            id = "s_reversed",
            invoiceNumber = "INV-P4-004B",
            customerId = customerId,
            saleType = "CREDIT",
            totalAmount = 300.0,
            paidAmount = 0.0,
            creditAmount = 300.0,
            paymentStatus = "UNPAID",
            transactionDate = "2026-09-29",
            status = "REVERSED"
        )
        val reversedLine = SaleLine(
            id = "sl_rev",
            saleId = "s_reversed",
            productId = "prod_b",
            productNameSnapshot = "Reversed Item",
            quantity = 3,
            unitPrice = 100.0,
            costPriceAtSale = 70.0, // COGS = 210.0 (MUST BE EXCLUDED)
            subtotal = 300.0
        )

        val totals = FinancialReportCalculator.calculateFromSales(
            sales = listOf(activeSale, reversedSale),
            saleLines = listOf(activeLine, reversedLine)
        )

        // Active revenue = 100.0, reversed excluded
        assertEquals(100.0, totals.totalSales, 0.0001)
        assertEquals(100.0, totals.netSales, 0.0001)
        // Active COGS = 60.0, reversed line (210.0) excluded
        assertEquals(60.0, totals.cogs, 0.0001)
        // Active Gross Profit = 100 - 60 = 40.0
        assertEquals(40.0, totals.grossProfit, 0.0001)
        assertEquals(0.40, totals.grossMargin, 0.0001)
    }

    /**
     * Test E: Zero revenue.
     * Verify no division-by-zero.
     */
    @Test
    fun testPhase4Step1_TestE_zeroRevenueDivisionByZero() {
        val totals = FinancialReportCalculator.calculateFromSales(
            sales = emptyList(),
            saleLines = emptyList()
        )

        assertEquals(0.0, totals.totalSales, 0.0001)
        assertEquals(0.0, totals.netSales, 0.0001)
        assertEquals(0.0, totals.cogs, 0.0001)
        assertEquals(0.0, totals.grossProfit, 0.0001)
        assertEquals(0.0, totals.grossMargin, 0.0001)
        assertFalse(totals.grossMargin.isNaN())
        assertFalse(totals.grossMargin.isInfinite())

        // Also test direct helper method
        val margin = FinancialReportCalculator.calculateGrossMargin(grossProfit = 0.0, netSales = 0.0)
        assertEquals(0.0, margin, 0.0001)
        assertFalse(margin.isNaN())
        assertFalse(margin.isInfinite())
    }

    // =========================================================================
    // Phase 6: Revenue & Historical COGS Finalization Unit Tests
    // =========================================================================

    /**
     * Requirement: Positive-margin sale.
     * Net sales 100, COGS 60 -> Gross profit = 40.0, Margin = 40%.
     */
    @Test
    fun testPhase6_positiveMarginSale() {
        val sale = Sale(
            id = "s_p6_pos",
            invoiceNumber = "INV-P6-001",
            customerId = customerId,
            saleType = "CASH",
            totalAmount = 100.0,
            paidAmount = 100.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )
        val line = SaleLine(
            id = "sl_p6_pos",
            saleId = "s_p6_pos",
            productId = "prod_p6_1",
            productNameSnapshot = "Item Positive Margin",
            quantity = 2,
            unitPrice = 50.0,
            costPriceAtSale = 30.0,
            subtotal = 100.0
        )

        val totals = FinancialReportCalculator.calculateFromSales(
            sales = listOf(sale),
            saleLines = listOf(line)
        )

        assertEquals(100.0, totals.totalSales, 0.0001)
        assertEquals(100.0, totals.netSales, 0.0001)
        assertEquals(60.0, totals.cogs, 0.0001)
        assertEquals(40.0, totals.grossProfit, 0.0001)
        assertEquals(0.40, totals.grossMargin, 0.0001)
        assertTrue("Gross profit must be strictly positive", totals.grossProfit > 0.0)
    }

    /**
     * Requirement: Zero-margin sale.
     * Net sales 100, COGS 100 -> Gross profit = 0.0, Margin = 0%.
     */
    @Test
    fun testPhase6_zeroMarginSale() {
        val sale = Sale(
            id = "s_p6_zero",
            invoiceNumber = "INV-P6-002",
            customerId = customerId,
            saleType = "CASH",
            totalAmount = 100.0,
            paidAmount = 100.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )
        val line = SaleLine(
            id = "sl_p6_zero",
            saleId = "s_p6_zero",
            productId = "prod_p6_2",
            productNameSnapshot = "At-Cost Item",
            quantity = 2,
            unitPrice = 50.0,
            costPriceAtSale = 50.0,
            subtotal = 100.0
        )

        val totals = FinancialReportCalculator.calculateFromSales(
            sales = listOf(sale),
            saleLines = listOf(line)
        )

        assertEquals(100.0, totals.totalSales, 0.0001)
        assertEquals(100.0, totals.netSales, 0.0001)
        assertEquals(100.0, totals.cogs, 0.0001)
        assertEquals(0.0, totals.grossProfit, 0.0001)
        assertEquals(0.0, totals.grossMargin, 0.0001)
    }

    /**
     * Requirement: Negative-margin sale if supported by existing business rules.
     * Net sales 100, COGS 125 -> Gross profit = -25.0 (UNCLAMPED), Margin = -25%.
     */
    @Test
    fun testPhase6_negativeMarginSale() {
        val sale = Sale(
            id = "s_p6_neg",
            invoiceNumber = "INV-P6-003",
            customerId = customerId,
            saleType = "CASH",
            totalAmount = 100.0,
            paidAmount = 100.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )
        val line = SaleLine(
            id = "sl_p6_neg",
            saleId = "s_p6_neg",
            productId = "prod_p6_3",
            productNameSnapshot = "Clearance Loss Leader",
            quantity = 2,
            unitPrice = 50.0,
            costPriceAtSale = 62.5, // 2 * 62.5 = 125.0 COGS
            subtotal = 100.0
        )

        val totals = FinancialReportCalculator.calculateFromSales(
            sales = listOf(sale),
            saleLines = listOf(line)
        )

        assertEquals(100.0, totals.totalSales, 0.0001)
        assertEquals(100.0, totals.netSales, 0.0001)
        assertEquals(125.0, totals.cogs, 0.0001)
        assertEquals("Gross profit must not be clamped to zero", -25.0, totals.grossProfit, 0.0001)
        assertEquals(-0.25, totals.grossMargin, 0.0001)
        assertTrue("Gross profit must be strictly negative", totals.grossProfit < 0.0)
    }

    /**
     * Requirement: Mixed sale where totalAmount differs from paidAmount.
     * totalAmount 150 = paidAmount 60 (cash) + creditAmount 90 (receivable).
     * COGS 80 -> Gross profit = 70.0.
     * Revenue basis must be totalAmount (150), NOT paidAmount (60).
     */
    @Test
    fun testPhase6_mixedSaleWhereTotalAmountDiffersFromPaidAmount() {
        val mixedSale = Sale(
            id = "s_p6_mixed",
            invoiceNumber = "INV-P6-004",
            customerId = customerId,
            saleType = "MIXED",
            totalAmount = 150.0,
            paidAmount = 60.0,
            creditAmount = 90.0,
            paymentStatus = "PARTIAL",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )
        val line = SaleLine(
            id = "sl_p6_mixed",
            saleId = "s_p6_mixed",
            productId = "prod_p6_4",
            productNameSnapshot = "Mixed Sale Item",
            quantity = 3,
            unitPrice = 50.0,
            costPriceAtSale = 26.6667, // ~80 COGS
            subtotal = 150.0
        )

        // Also add customer payment of 40.0 to verify collections do NOT increase revenue
        val paymentTx = TransactionItem(
            id = "tx_p6_pay",
            amount = 40.0,
            isCredit = false,
            date = "2026-10-01",
            relativeTime = "اليوم",
            activityType = "تسديد",
            customerId = customerId,
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            paidAmount = 40.0,
            creditAmount = 0.0,
            operationStatus = OperationStatus.ACTIVE
        )

        val totals = FinancialReportCalculator.calculateFromSales(
            sales = listOf(mixedSale),
            saleLines = listOf(line)
        )

        // 1. Revenue basis is totalAmount (150), NOT paidAmount (60)
        assertEquals(150.0, totals.totalSales, 0.0001)
        assertEquals(150.0, totals.netSales, 0.0001)
        assertEquals(60.0, totals.cashSales, 0.0001)
        assertEquals(90.0, totals.creditSales, 0.0001)
        assertEquals(90.0, totals.netReceivableIncrease, 0.0001)

        // 2. COGS & profit
        assertEquals(3 * 26.6667, totals.cogs, 0.001)
        assertEquals(150.0 - (3 * 26.6667), totals.grossProfit, 0.001)

        // 3. Customer payment collection added to transaction list does NOT increase totalSales
        val txTotals = FinancialReportCalculator.calculate(
            transactions = listOf(mixedSale.toTransactionItem(), paymentTx)
        )
        assertEquals("Total sales must still be 150.0 after payment collection", 150.0, txTotals.totalSales, 0.0001)
        assertEquals("Customer collections must be 40.0", 40.0, txTotals.customerPayments, 0.0001)
        // Net receivable: 90 (credit portion) - 40 (payment) = 50.0
        assertEquals(50.0, txTotals.netReceivableIncrease, 0.0001)
    }

    /**
     * Requirement: Historical sale cost differs from current product cost.
     * Sale line was recorded when unit cost was 20.0 (COGS = 2 * 20 = 40.0).
     * Even if product current purchase cost is 50.0, COGS must strictly use historical 20.0.
     */
    @Test
    fun testPhase6_historicalSaleCostDiffersFromCurrentProductCost() {
        val sale = Sale(
            id = "s_p6_hist",
            invoiceNumber = "INV-P6-005",
            customerId = customerId,
            saleType = "CASH",
            totalAmount = 80.0,
            paidAmount = 80.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )
        // Historical frozen cost = 20.0 per unit
        val saleLine = SaleLine(
            id = "sl_p6_hist",
            saleId = "s_p6_hist",
            productId = "prod_p6_hist",
            productNameSnapshot = "Historical Cost Item",
            quantity = 2,
            unitPrice = 40.0,
            costPriceAtSale = 20.0,
            subtotal = 80.0
        )

        // Suppose current product cost is 55.0. calculateCogs MUST use costPriceAtSale (20.0).
        val cogs = FinancialReportCalculator.calculateCogs(
            sales = listOf(sale),
            saleLines = listOf(saleLine)
        )

        assertEquals("COGS must use frozen historical cost (2 * 20 = 40)", 40.0, cogs, 0.0001)

        val totals = FinancialReportCalculator.calculateFromSales(
            sales = listOf(sale),
            saleLines = listOf(saleLine)
        )

        assertEquals(80.0, totals.totalSales, 0.0001)
        assertEquals(40.0, totals.cogs, 0.0001)
        assertEquals(40.0, totals.grossProfit, 0.0001)
        assertEquals(0.50, totals.grossMargin, 0.0001)
    }

    /**
     * Requirement: Reversed sale contributes zero active revenue/COGS.
     * Active sale 100 (COGS 60) + Reversed sale 200 (COGS 140).
     * Totals must reflect only active sale (Revenue 100, COGS 60, Gross Profit 40).
     */
    @Test
    fun testPhase6_reversedSale() {
        val activeSale = Sale(
            id = "s_p6_active",
            invoiceNumber = "INV-P6-006A",
            customerId = customerId,
            saleType = "CASH",
            totalAmount = 100.0,
            paidAmount = 100.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )
        val activeLine = SaleLine(
            id = "sl_p6_active",
            saleId = "s_p6_active",
            productId = "prod_p6_act",
            productNameSnapshot = "Active Item",
            quantity = 2,
            unitPrice = 50.0,
            costPriceAtSale = 30.0,
            subtotal = 100.0
        )

        val reversedSale = Sale(
            id = "s_p6_reversed",
            invoiceNumber = "INV-P6-006B",
            customerId = customerId,
            saleType = "CREDIT",
            totalAmount = 200.0,
            paidAmount = 0.0,
            creditAmount = 200.0,
            paymentStatus = "UNPAID",
            transactionDate = "2026-10-01",
            status = "REVERSED"
        )
        val reversedLine = SaleLine(
            id = "sl_p6_reversed",
            saleId = "s_p6_reversed",
            productId = "prod_p6_rev",
            productNameSnapshot = "Reversed Item",
            quantity = 4,
            unitPrice = 50.0,
            costPriceAtSale = 35.0, // 4 * 35 = 140 COGS
            subtotal = 200.0
        )

        val totals = FinancialReportCalculator.calculateFromSales(
            sales = listOf(activeSale, reversedSale),
            saleLines = listOf(activeLine, reversedLine)
        )

        assertEquals("Active revenue must be 100.0", 100.0, totals.totalSales, 0.0001)
        assertEquals("Active net sales must be 100.0", 100.0, totals.netSales, 0.0001)
        assertEquals("Active COGS must be 60.0 (reversed 140 excluded)", 60.0, totals.cogs, 0.0001)
        assertEquals("Active gross profit must be 40.0", 40.0, totals.grossProfit, 0.0001)
        assertEquals(0.40, totals.grossMargin, 0.0001)

        assertEquals("Active count must be 1", 1, totals.activeTransactionCount)
        assertEquals("Reversed count must be 1", 1, totals.reversedTransactionCount)
        assertEquals("Reversed sales volume must be 200.0", 200.0, totals.reversedSalesVolume, 0.0001)
    }
}
