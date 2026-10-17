package com.example.accounting

import com.example.model.CustomerAccount
import com.example.model.OperationStatus
import com.example.model.SaleType
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.util.ReportExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportExporterAccountingTest {

    private fun createTx(
        id: String,
        amount: Double,
        activityType: String = "بيع",
        isCredit: Boolean = false,
        customerId: String? = "c1",
        customerName: String = "عميل",
        date: String = "2026-03-30",
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
            customerNameSnapshot = customerName,
            activityType = activityType,
            amount = amount,
            isCredit = isCredit,
            date = date,
            relativeTime = "الآن",
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
    fun testClassification_isPayment_typedDomainLogic() {
        // A customer payment transaction
        val paymentTx = createTx(
            id = "tx1",
            amount = 50.0,
            activityType = "تسديد",
            isCredit = false,
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            notes = "دفعة عادية"
        )
        assertTrue(ReportExporter.isPaymentTransaction(paymentTx))

        // A sale return is also an inflow/credit to the customer account
        val returnTx = createTx(
            id = "tx2",
            amount = 30.0,
            activityType = "مرتجع",
            isCredit = false,
            transactionType = TransactionType.SALE_RETURN,
            notes = "مرتجع بضاعة"
        )
        assertTrue(ReportExporter.isPaymentTransaction(returnTx))

        // A cash sale even if notes contain "تسديد" should NOT be classified as payment
        val trickySaleTx = createTx(
            id = "tx3",
            amount = 100.0,
            activityType = "بيع كاش",
            isCredit = false,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CASH,
            notes = "تسديد نقدي لشراء بضاعة"
        )
        assertFalse(ReportExporter.isPaymentTransaction(trickySaleTx))

        // A reversed payment should NOT be considered an active payment
        val reversedPayment = paymentTx.copy(
            id = "tx4",
            operationStatus = OperationStatus.REVERSED
        )
        assertFalse(ReportExporter.isPaymentTransaction(reversedPayment))
    }

    @Test
    fun testClassification_isDebt_typedDomainLogic() {
        // Credit sale
        val creditSale = createTx(
            id = "tx1",
            amount = 100.0,
            activityType = "بيع آجل",
            isCredit = true,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            creditAmount = 100.0
        )
        assertTrue(ReportExporter.isDebtTransaction(creditSale))

        // Mixed sale
        val mixedSale = createTx(
            id = "tx2",
            amount = 100.0,
            activityType = "بيع مركب",
            isCredit = true,
            paidAmount = 60.0,
            creditAmount = 40.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED
        )
        assertTrue(ReportExporter.isDebtTransaction(mixedSale))

        // Pure cash sale is NOT debt, even if notes say "دين"
        val cashSale = createTx(
            id = "tx3",
            amount = 100.0,
            activityType = "بيع كاش",
            isCredit = false,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CASH,
            notes = "فاتورة دين قديم مدفوعة كاش"
        )
        assertFalse(ReportExporter.isDebtTransaction(cashSale))

        // Reversed credit sale is NOT an active debt transaction
        val reversedCredit = creditSale.copy(
            id = "tx4",
            operationStatus = OperationStatus.REVERSED
        )
        assertFalse(ReportExporter.isDebtTransaction(reversedCredit))
    }

    @Test
    fun testMixedSale_splitCashAndCredit_inTransactionsCsv() {
        // total = 100, paid = 60, credit = 40
        val mixedSale = createTx(
            id = "m1",
            amount = 100.0,
            activityType = "بيع مركب",
            isCredit = true,
            paidAmount = 60.0,
            creditAmount = 40.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED,
            date = "2026-03-30 10:00"
        )

        val csv = ReportExporter.generateTransactionsCsv(
            title = "تقرير المعاملات",
            storeName = "متجري",
            subtitle = "الفترة الحالية",
            kpis = emptyList(),
            transactions = listOf(mixedSale),
            isArabic = true
        )

        // Verify that Cash is 60.00 and Debt is 40.00 (not 100 as debt)
        assertTrue("CSV must contain cash 60.00", csv.contains("60.00"))
        assertTrue("CSV must contain debt 40.00", csv.contains("40.00"))
    }

    @Test
    fun testReversalsExcluded_inTransactionsCsvAndTxt() {
        val activeSale = createTx(
            id = "tx1",
            amount = 100.0,
            activityType = "بيع كاش",
            isCredit = false,
            paidAmount = 100.0,
            creditAmount = 0.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CASH,
            operationStatus = OperationStatus.ACTIVE,
            date = "2026-03-30 10:00"
        )

        val reversedSale = createTx(
            id = "tx2",
            amount = 500.0,
            activityType = "بيع كاش",
            isCredit = false,
            paidAmount = 500.0,
            creditAmount = 0.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CASH,
            operationStatus = OperationStatus.REVERSED,
            date = "2026-03-30 11:00"
        )

        val csv = ReportExporter.generateTransactionsCsv(
            title = "تقرير المعاملات",
            storeName = "متجري",
            subtitle = "الفترة الحالية",
            kpis = emptyList(),
            transactions = listOf(activeSale, reversedSale),
            isArabic = true
        )

        val txt = ReportExporter.generateTransactionsTxt(
            title = "تقرير المعاملات",
            storeName = "متجري",
            subtitle = "الفترة الحالية",
            kpis = emptyList(),
            transactions = listOf(activeSale, reversedSale),
            isArabic = true
        )

        // The cash total must reflect only activeSale (100.00), not activeSale + reversedSale (600.00)
        assertTrue("CSV should have 100.00 cash", csv.contains("100.00"))
        assertFalse("CSV cash total should not be 600.00", csv.contains("600.00"))

        assertTrue("TXT should have 100.00 cash", txt.contains("100.00"))
        assertFalse("TXT cash total should not be 600.00", txt.contains("600.00"))
    }

    @Test
    fun testCustomerCsvAndTxt_matchesDomainTotals() {
        val customer = CustomerAccount(
            id = "c1",
            customerName = "أحمد علي",
            balance = 40.0,
            totalDebt = 40.0,
            phone = "123456"
        )

        val mixedSale = createTx(
            id = "tx1",
            amount = 100.0,
            activityType = "بيع مركب",
            isCredit = true,
            paidAmount = 60.0,
            creditAmount = 40.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED,
            operationStatus = OperationStatus.ACTIVE,
            date = "2026-03-30 10:00"
        )

        val payment = createTx(
            id = "tx2",
            amount = 20.0,
            activityType = "تسديد",
            isCredit = false,
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            operationStatus = OperationStatus.ACTIVE,
            date = "2026-03-30 12:00"
        )

        val csv = ReportExporter.generateCustomerCsv(
            title = "كشف حساب",
            storeName = "متجري",
            subtitle = "الفترة",
            customer = customer,
            kpis = emptyList(),
            transactions = listOf(mixedSale, payment),
            itemBreakdowns = emptyList(),
            isArabic = true
        )

        val txt = ReportExporter.generateCustomerTxt(
            title = "كشف حساب",
            storeName = "متجري",
            subtitle = "الفترة",
            customer = customer,
            kpis = emptyList(),
            transactions = listOf(mixedSale, payment),
            itemBreakdowns = emptyList(),
            isArabic = true
        )

        // Cash purchases should be 60.00, credit debt 40.00, payments 20.00
        assertTrue("CSV cash purchase must be 60.00", csv.contains("60.00"))
        assertTrue("CSV credit purchase must be 40.00", csv.contains("40.00"))
        assertTrue("CSV payments must be 20.00", csv.contains("20.00"))

        assertTrue("TXT cash purchase must be 60.00", txt.contains("60.00"))
        assertTrue("TXT credit purchase must be 40.00", txt.contains("40.00"))
        assertTrue("TXT payments must be 20.00", txt.contains("20.00"))
    }

    @Test
    fun testBuildStatementRows_runningBalancesMatchAccountingDomain() {
        val customer = CustomerAccount(
            id = "c1",
            customerName = "خالد",
            balance = 100.0,
            totalDebt = 100.0,
            phone = "0555555555"
        )

        // 1. Initial credit sale of 100 -> debt +100, running = 100
        val tx1 = createTx(
            id = "tx1",
            amount = 100.0,
            activityType = "بيع آجل",
            isCredit = true,
            paidAmount = 0.0,
            creditAmount = 100.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            date = "2026-03-01"
        )

        // 2. Mixed sale: 100 total, 70 cash, 30 credit -> receivable +30, running = 130
        val tx2 = createTx(
            id = "tx2",
            amount = 100.0,
            activityType = "بيع مركب",
            isCredit = true,
            paidAmount = 70.0,
            creditAmount = 30.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED,
            date = "2026-03-02"
        )

        // 3. Payment of 50 -> receivable -50, running = 80
        val tx3 = createTx(
            id = "tx3",
            amount = 50.0,
            activityType = "تسديد",
            isCredit = false,
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            date = "2026-03-03"
        )

        // 4. Reversed transaction -> 0 effect, running = 80
        val tx4 = createTx(
            id = "tx4",
            amount = 200.0,
            activityType = "بيع آجل",
            isCredit = true,
            paidAmount = 0.0,
            creditAmount = 200.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            operationStatus = OperationStatus.REVERSED,
            date = "2026-03-04"
        )

        val rows = ReportExporter.buildStatementRows(
            customer = customer,
            transactions = listOf(tx4, tx1, tx3, tx2), // intentionally unsorted
            openingBalance = 0.0,
            includeOpeningBalanceRow = false,
            isArabic = true
        )

        assertEquals(4, rows.size)
        // Verify chronological order and running balances
        assertEquals("tx1", rows[0].id)
        assertEquals(100.0, rows[0].runningBalance, 0.001)

        assertEquals("tx2", rows[1].id)
        assertEquals(130.0, rows[1].runningBalance, 0.001)

        assertEquals("tx3", rows[2].id)
        assertEquals(80.0, rows[2].runningBalance, 0.001)

        assertEquals("tx4", rows[3].id)
        assertEquals(80.0, rows[3].runningBalance, 0.001) // reversed had 0 impact
        assertTrue(rows[3].isArchived)
    }
}
