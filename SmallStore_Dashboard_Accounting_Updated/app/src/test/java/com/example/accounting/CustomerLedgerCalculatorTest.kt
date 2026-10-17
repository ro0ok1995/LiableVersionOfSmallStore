package com.example.accounting

import com.example.model.OperationStatus
import com.example.model.SaleType
import com.example.model.SettlementType
import com.example.model.TransactionItem
import com.example.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure accounting validation suite for CustomerLedgerCalculator.
 *
 * Verifies the 7 required examples from the Phase 2 user specification:
 * Example 1: Credit Sale = 100, Payment = 40 -> Balance = 60
 * Example 2: Cash Sale = 100, customerId = NULL -> Customer Balance = 0
 * Example 3: Mixed Sale / Credit portion -> Customer Balance reflects credit
 * Example 4: Credit Sale = 100, Payment = 100 -> Balance = 0 (Settled)
 * Example 5: Credit Sale = 100, Payment = 120 -> Balance = -20 (Negative/Overpayment preserved)
 * Example 6: Credit Sale = 100, Payment = 40, Sale archived -> Balance remains 60 (ARCHIVED != REVERSED)
 * Example 7: Credit Sale = 100, Payment = 40, Sale reversed -> Original sale neutralized
 */
class CustomerLedgerCalculatorTest {

    private val customerId = "cust_test_101"

    @Test
    fun example1_creditSaleAndPayment() {
        // Credit Sale = 100, Payment = 40 -> Balance = 60
        val transactions = listOf(
            TransactionItem(
                id = "tx1",
                customerName = "عميل تجريبي",
                activityType = "شراء آجل",
                amount = 100.0,
                isCredit = true,
                date = "2026-09-20",
                relativeTime = "الآن",
                customerId = customerId
            ),
            TransactionItem(
                id = "tx2",
                customerName = "عميل تجريبي",
                activityType = "تسديد",
                amount = 40.0,
                isCredit = false,
                date = "2026-09-20",
                relativeTime = "الآن",
                settlementType = SettlementType.PARTIAL,
                customerId = customerId
            )
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, transactions)
        assertEquals(60.0, summary.balance, 0.0001)
        assertEquals(100.0, summary.totalCreditSales, 0.0001)
        assertEquals(40.0, summary.totalPayments, 0.0001)
        assertTrue(summary.isDebitBalance)
        assertFalse(summary.isCreditBalance)
        assertFalse(summary.isSettled)
    }

    @Test
    fun example2_anonymousCashSale_doesNotAffectCustomerBalance() {
        // Cash Sale = 100, customerId = NULL -> Customer Balance = 0
        val transactions = listOf(
            TransactionItem(
                id = "tx_cash_1",
                customerName = "عميل عام",
                activityType = "شراء كاش",
                amount = 100.0,
                isCredit = false,
                date = "2026-09-20",
                relativeTime = "الآن",
                customerId = null // Anonymous walk-in
            )
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, transactions)
        assertEquals(0.0, summary.balance, 0.0001)
        assertEquals(0, summary.activeTransactionCount)
        assertTrue(summary.isSettled)
    }

    @Test
    fun example3_knownCustomerCashSale_zeroDebtCreated() {
        // A known customer requests a pure cash invoice (customerId is set, but sale is 100% CASH)
        val transactions = listOf(
            TransactionItem(
                id = "tx_cash_known",
                customerName = "عميل تجريبي",
                activityType = "شراء كاش",
                amount = 150.0,
                isCredit = false,
                date = "2026-09-20",
                relativeTime = "الآن",
                customerId = customerId
            )
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, transactions)
        // A pure cash sale must create ZERO debt for the customer
        assertEquals(0.0, summary.balance, 0.0001)
        assertEquals(0.0, summary.totalCreditSales, 0.0001)
        assertTrue(summary.isSettled)
    }

    @Test
    fun example4_fullSettlement_balanceZero() {
        // Credit Sale = 100, Payment = 100 -> Balance = 0
        val transactions = listOf(
            TransactionItem(
                id = "tx1",
                customerName = "عميل تجريبي",
                activityType = "شراء آجل",
                amount = 100.0,
                isCredit = true,
                date = "2026-09-20",
                relativeTime = "الآن",
                customerId = customerId
            ),
            TransactionItem(
                id = "tx2",
                customerName = "عميل تجريبي",
                activityType = "تسديد",
                amount = 100.0,
                isCredit = false,
                date = "2026-09-20",
                relativeTime = "الآن",
                settlementType = SettlementType.FULL,
                customerId = customerId
            )
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, transactions)
        assertEquals(0.0, summary.balance, 0.0001)
        assertTrue(summary.isSettled)
    }

    @Test
    fun example5_overpayment_preservedAsNegativeBalance() {
        // Credit Sale = 100, Payment = 120 -> Balance = -20 (Customer Credit)
        val transactions = listOf(
            TransactionItem(
                id = "tx1",
                customerName = "عميل تجريبي",
                activityType = "شراء آجل",
                amount = 100.0,
                isCredit = true,
                date = "2026-09-20",
                relativeTime = "الآن",
                customerId = customerId
            ),
            TransactionItem(
                id = "tx2",
                customerName = "عميل تجريبي",
                activityType = "تسديد",
                amount = 120.0,
                isCredit = false,
                date = "2026-09-20",
                relativeTime = "الآن",
                customerId = customerId
            )
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, transactions)
        assertEquals(-20.0, summary.balance, 0.0001)
        assertTrue("Overpayment must be flagged as credit balance", summary.isCreditBalance)
        assertFalse(summary.isDebitBalance)
        assertFalse(summary.isSettled)
    }

    @Test
    fun example6_archivedTransaction_stillAffectsBalance() {
        // CRITICAL INVARIANT: ARCHIVED IS NOT REVERSED!
        // Credit Sale = 100, Payment = 40, Sale is archived -> Balance MUST STILL be 60.
        val transactions = listOf(
            TransactionItem(
                id = "tx1",
                customerName = "عميل تجريبي",
                activityType = "شراء آجل",
                amount = 100.0,
                isCredit = true,
                date = "2026-09-20",
                relativeTime = "الآن",
                isArchived = true, // ARCHIVED FOR UI PURPOSES!
                customerId = customerId
            ),
            TransactionItem(
                id = "tx2",
                customerName = "عميل تجريبي",
                activityType = "تسديد",
                amount = 40.0,
                isCredit = false,
                date = "2026-09-20",
                relativeTime = "الآن",
                customerId = customerId
            )
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, transactions)
        assertEquals(
            "Archived transactions MUST NOT be excluded from accounting balance. Balance must remain 60.",
            60.0,
            summary.balance,
            0.0001
        )
    }

    @Test
    fun example7_reversedTransaction_neutralizedFromBalance() {
        // Credit Sale = 100 (REVERSED), Payment = 40 -> Original sale neutralized -> Balance = -40
        val entries = listOf(
            CustomerLedgerEntry(
                transactionId = "tx1",
                customerId = customerId,
                date = "2026-09-20",
                transactionType = TransactionType.SALE,
                debit = 100.0,
                credit = 0.0,
                operationStatus = OperationStatus.REVERSED // Explicitly reversed!
            ),
            CustomerLedgerEntry(
                transactionId = "tx2",
                customerId = customerId,
                date = "2026-09-20",
                transactionType = TransactionType.CUSTOMER_PAYMENT,
                debit = 0.0,
                credit = 40.0,
                operationStatus = OperationStatus.ACTIVE
            )
        )

        val summary = CustomerLedgerCalculator.calculateSummaryFromEntries(customerId, entries)
        assertEquals(-40.0, summary.balance, 0.0001)
        assertEquals(1, summary.totalReversedCount)
        assertEquals(1, summary.activeTransactionCount)
    }

    @Test
    fun mixedSale_total100_paid60_credit40_producesReceivable40() {
        // MANDATORY REQUIREMENT:
        // Sale total = 100
        // Paid immediately = 60
        // Credit = 40
        // The customer's receivable MUST be 40, not 100.
        val mixedSaleTx = TransactionItem(
            id = "tx_mixed_1",
            title = "فاتورة بيع مجزأ",
            customerNameSnapshot = "عميل تجريبي",
            customerName = "عميل تجريبي",
            activityType = "شراء بالدين",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-22",
            relativeTime = "الآن",
            settlementType = SettlementType.PARTIAL,
            customerId = customerId,
            paidAmount = 60.0,
            creditAmount = 40.0,
            saleType = SaleType.MIXED,
            transactionType = TransactionType.SALE
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, listOf(mixedSaleTx))

        assertEquals("Customer receivable MUST be 40.0, not 100.0", 40.0, summary.balance, 0.0001)
        assertEquals("Total credit sales component MUST be 40.0", 40.0, summary.totalCreditSales, 0.0001)
        assertTrue(summary.isDebitBalance)
        assertFalse(summary.isSettled)
    }

    @Test
    fun saleTypeVariants_cash_credit_and_mixed_produceCorrectReceivableBehavior() {
        // 1. Pure Cash Sale: Total 100, Paid 100, Credit 0 -> Receivable 0
        val cashSale = TransactionItem(
            id = "tx_cash",
            title = "بيع كاش",
            customerNameSnapshot = "عميل تجريبي",
            customerName = "عميل تجريبي",
            activityType = "شراء كاش",
            amount = 100.0,
            isCredit = false,
            date = "2026-09-22",
            relativeTime = "الآن",
            customerId = customerId,
            paidAmount = 100.0,
            creditAmount = 0.0,
            saleType = SaleType.CASH,
            transactionType = TransactionType.SALE
        )
        val cashSummary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, listOf(cashSale))
        assertEquals(0.0, cashSummary.balance, 0.0001)
        assertEquals(0.0, cashSummary.totalCreditSales, 0.0001)

        // 2. Pure Credit Sale: Total 100, Paid 0, Credit 100 -> Receivable 100
        val creditSale = TransactionItem(
            id = "tx_credit",
            title = "بيع آجل",
            customerNameSnapshot = "عميل تجريبي",
            customerName = "عميل تجريبي",
            activityType = "شراء آجل",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-22",
            relativeTime = "الآن",
            customerId = customerId,
            paidAmount = 0.0,
            creditAmount = 100.0,
            saleType = SaleType.CREDIT,
            transactionType = TransactionType.SALE
        )
        val creditSummary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, listOf(creditSale))
        assertEquals(100.0, creditSummary.balance, 0.0001)
        assertEquals(100.0, creditSummary.totalCreditSales, 0.0001)

        // 3. Mixed Sale: Total 100, Paid 60, Credit 40 -> Receivable 40
        val mixedSale = TransactionItem(
            id = "tx_mixed",
            title = "بيع مختلط",
            customerNameSnapshot = "عميل تجريبي",
            customerName = "عميل تجريبي",
            activityType = "شراء بالدين",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-22",
            relativeTime = "الآن",
            customerId = customerId,
            paidAmount = 60.0,
            creditAmount = 40.0,
            saleType = SaleType.MIXED,
            transactionType = TransactionType.SALE
        )
        val mixedSummary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, listOf(mixedSale))
        assertEquals(40.0, mixedSummary.balance, 0.0001)
        assertEquals(40.0, mixedSummary.totalCreditSales, 0.0001)

        // 4. Combined: Cash + Credit + Mixed -> 0 + 100 + 40 = 140
        val combinedSummary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId,
            listOf(cashSale, creditSale, mixedSale)
        )
        assertEquals(140.0, combinedSummary.balance, 0.0001)
        assertEquals(140.0, combinedSummary.totalCreditSales, 0.0001)
    }
}
