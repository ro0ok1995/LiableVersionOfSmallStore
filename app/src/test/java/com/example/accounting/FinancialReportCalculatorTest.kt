package com.example.accounting

import com.example.data.db.Sale
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
}
