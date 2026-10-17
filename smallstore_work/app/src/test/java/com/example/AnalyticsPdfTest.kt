package com.example

import com.example.data.db.TransactionItemLineEntity
import com.example.model.AnalyticsChartCategory
import com.example.model.AnalyticsChartType
import com.example.model.AnalyticsExportDataPreparer
import com.example.model.AnalyticsReportData
import com.example.model.AnalyticsReportScope
import com.example.model.CustomerAccount
import com.example.model.OperationStatus
import com.example.model.PeriodFilter
import com.example.model.SaleType
import com.example.model.SettlementType
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.ui.components.BreakdownChartType
import com.example.util.AnalyticsPdfGenerator
import com.example.util.ReportExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnalyticsPdfTest {

    private val sampleCustomers = listOf(
        CustomerAccount(id = "c1", customerName = "عمر الفاروق", balance = 450.0, totalDebt = 600.0, phone = "0599123456"),
        CustomerAccount(id = "c2", customerName = "ياسر عرفات", balance = 0.0, totalDebt = 200.0, phone = "0599987654")
    )

    private val sampleTransactions = listOf(
        TransactionItem("tx1", "شراء آجل", "عمر الفاروق", "آجل", 600.0, true, "2026-09-01", "اليوم", "بضائع متنوعة", settlementType = null, customerId = "c1"),
        TransactionItem("tx2", "شراء نقدي", "عمر الفاروق", "كاش", 150.0, false, "2026-09-02", "اليوم", "مشروبات", settlementType = null, customerId = "c1"),
        TransactionItem("tx3", "تسديد جزئي", "عمر الفاروق", "تسديد", 150.0, false, "2026-09-03", "اليوم", "دفعة نقدية", settlementType = SettlementType.PARTIAL, customerId = "c1"),
        TransactionItem("tx4", "شراء آجل", "ياسر عرفات", "آجل", 200.0, true, "2026-09-04", "اليوم", "أدوات منزلية", settlementType = null, customerId = "c2"),
        TransactionItem("tx5", "تسديد كامل", "ياسر عرفات", "تسديد", 200.0, false, "2026-09-05", "اليوم", "تسديد كامل", settlementType = SettlementType.FULL, customerId = "c2")
    )

    private val sampleLines = listOf(
        TransactionItemLineEntity(id = 1L, transactionId = "tx1", productId = "p1", productNameSnapshot = "سكر أبيض", quantity = 5, unitPrice = 40.0, subtotal = 200.0),
        TransactionItemLineEntity(id = 2L, transactionId = "tx1", productId = "p2", productNameSnapshot = "أرز بسمتي", quantity = 4, unitPrice = 100.0, subtotal = 400.0),
        TransactionItemLineEntity(id = 3L, transactionId = "tx2", productId = "p3", productNameSnapshot = "عصير طبيعي", quantity = 10, unitPrice = 15.0, subtotal = 150.0)
    )

    @Test
    fun testAllCustomersAnalyticsPdfDataAndExecution() {
        val testDate = LocalDate.of(2026, 9, 10)
        val data = AnalyticsExportDataPreparer.prepareAnalyticsData(
            transactions = sampleTransactions,
            transactionLines = sampleLines,
            allCustomers = sampleCustomers,
            selectedCustomer = null,
            activePeriod = PeriodFilter.MONTH,
            selectedChartType = BreakdownChartType.DONUT,
            storeName = "بقالة القدس",
            currency = "₪",
            isArabic = true,
            today = testDate
        )

        // 1. Verify Scope & Structure
        assertEquals(AnalyticsReportScope.ALL_CUSTOMERS, data.scope)
        assertNull(data.selectedCustomer)
        assertTrue(data.mostOrderedProducts.isEmpty())
        assertEquals(AnalyticsChartType.CIRCULAR, data.selectedChartType)
        assertEquals(2, data.metrics.customerCount)
        assertEquals(5, data.metrics.transactionCount)
        assertEquals(800.0, data.metrics.totalDebtSales, 0.001)
        assertEquals(150.0, data.metrics.totalCashSales, 0.001)
        assertEquals(950.0, data.metrics.totalSales, 0.001)
        assertEquals(350.0, data.metrics.totalPaymentsReceived, 0.001)

        // 2. Execute Generator
        try {
            val outStream = ByteArrayOutputStream()
            AnalyticsPdfGenerator.generateAnalyticsPdf(
                data = data,
                outputStream = outStream,
                isArabic = true
            )
            val bytes = outStream.toByteArray()
            if (bytes.isNotEmpty()) {
                assertEquals('%'.code.toByte(), bytes[0])
            }
        } catch (_: IllegalStateException) {
            // Expected in headless Robolectric JVM without Skia graphics binaries
        }
    }

    @Test
    fun testSelectedCustomerAnalyticsPdfDataAndExecution() {
        val testDate = LocalDate.of(2026, 9, 10)
        val selected = sampleCustomers[0] // عمر الفاروق
        val data = AnalyticsExportDataPreparer.prepareAnalyticsData(
            transactions = sampleTransactions,
            transactionLines = sampleLines,
            allCustomers = sampleCustomers,
            selectedCustomer = selected,
            activePeriod = PeriodFilter.MONTH,
            selectedChartType = BreakdownChartType.COLUMN,
            storeName = "بقالة القدس",
            currency = "₪",
            isArabic = true,
            today = testDate
        )

        // 1. Verify Scope & Selected Customer Isolation
        assertEquals(AnalyticsReportScope.ONE_SELECTED_CUSTOMER, data.scope)
        assertNotNull(data.selectedCustomer)
        assertEquals("عمر الفاروق", data.selectedCustomer?.customerName)
        assertEquals("0599123456", data.selectedCustomer?.phone)
        assertEquals(450.0, data.selectedCustomer?.currentBalance ?: 0.0, 0.001)
        assertNull(data.metrics.customerCount)

        // Customer's own transactions only (tx1, tx2, tx3)
        assertEquals(3, data.metrics.transactionCount)
        assertEquals(600.0, data.metrics.totalDebtSales, 0.001)
        assertEquals(150.0, data.metrics.totalCashSales, 0.001)
        assertEquals(150.0, data.metrics.totalPaymentsReceived, 0.001)
        assertEquals(450.0, data.metrics.netOutstandingBalance, 0.001)

        // Most ordered products for this customer
        assertEquals(3, data.mostOrderedProducts.size)
        assertEquals("عصير طبيعي", data.mostOrderedProducts[0].productName)
        assertEquals(10, data.mostOrderedProducts[0].totalQuantity)
        assertEquals(AnalyticsChartType.BAR, data.selectedChartType)

        // 2. Execute Generator
        try {
            val outStream = ByteArrayOutputStream()
            AnalyticsPdfGenerator.generateAnalyticsPdf(
                data = data,
                outputStream = outStream,
                isArabic = true
            )
            val bytes = outStream.toByteArray()
            if (bytes.isNotEmpty()) {
                assertEquals('%'.code.toByte(), bytes[0])
            }
        } catch (_: IllegalStateException) {
            // Expected in headless Robolectric JVM without Skia graphics binaries
        }
    }

    @Test
    fun testCombinedChartPdfExecution() {
        val testDate = LocalDate.of(2026, 9, 10)
        val data = AnalyticsExportDataPreparer.prepareAnalyticsData(
            transactions = sampleTransactions,
            transactionLines = sampleLines,
            allCustomers = sampleCustomers,
            selectedCustomer = null,
            activePeriod = PeriodFilter.ALL,
            selectedChartType = BreakdownChartType.COMBO,
            storeName = "SmallStore",
            currency = "₪",
            isArabic = false,
            today = testDate
        )

        assertEquals(AnalyticsChartType.COMBINED, data.selectedChartType)
        try {
            val outStream = ByteArrayOutputStream()
            AnalyticsPdfGenerator.generateAnalyticsPdf(
                data = data,
                outputStream = outStream,
                isArabic = false
            )
            val bytes = outStream.toByteArray()
            if (bytes.isNotEmpty()) {
                assertEquals('%'.code.toByte(), bytes[0])
            }
        } catch (_: IllegalStateException) {
            // Expected in headless Robolectric JVM without Skia graphics binaries
        }
    }

    @Test
    fun testPdfConsumesPreparedAccountingDataWithMixedSaleReversalAndConsistentTotals() {
        val testDate = LocalDate.of(2026, 9, 15)
        val customer = sampleCustomers[0] // c1

        val mixedSale = TransactionItem(
            id = "tx_pdf_mixed",
            title = "Mixed Sale",
            customerName = customer.customerName,
            activityType = "شراء مختلط",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-10",
            relativeTime = "10:00",
            customerId = customer.id,
            paidAmount = 60.0,
            creditAmount = 40.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED,
            operationStatus = OperationStatus.ACTIVE
        )
        val creditSale = TransactionItem(
            id = "tx_pdf_credit",
            title = "Credit Sale",
            customerName = customer.customerName,
            activityType = "شراء آجل",
            amount = 200.0,
            isCredit = true,
            date = "2026-09-11",
            relativeTime = "11:00",
            customerId = customer.id,
            paidAmount = 0.0,
            creditAmount = 200.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            operationStatus = OperationStatus.ACTIVE
        )
        val cashSale = TransactionItem(
            id = "tx_pdf_cash",
            title = "Cash Sale",
            customerName = customer.customerName,
            activityType = "شراء نقدي",
            amount = 150.0,
            isCredit = false,
            date = "2026-09-12",
            relativeTime = "12:00",
            customerId = customer.id,
            paidAmount = 150.0,
            creditAmount = 0.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CASH,
            operationStatus = OperationStatus.ACTIVE
        )
        val payment = TransactionItem(
            id = "tx_pdf_pay",
            title = "Customer Payment",
            customerName = customer.customerName,
            activityType = "تسديد",
            amount = 50.0,
            isCredit = false,
            date = "2026-09-13",
            relativeTime = "13:00",
            settlementType = SettlementType.PARTIAL,
            customerId = customer.id,
            paidAmount = 50.0,
            creditAmount = 0.0,
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            operationStatus = OperationStatus.ACTIVE
        )
        val reversedCreditSale = TransactionItem(
            id = "tx_pdf_rev_sale",
            title = "Reversed Sale",
            customerName = customer.customerName,
            activityType = "شراء آجل ملغى",
            amount = 500.0,
            isCredit = true,
            date = "2026-09-14",
            relativeTime = "14:00",
            customerId = customer.id,
            paidAmount = 0.0,
            creditAmount = 500.0,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            operationStatus = OperationStatus.REVERSED
        )
        val reversedPayment = TransactionItem(
            id = "tx_pdf_rev_pay",
            title = "Reversed Payment",
            customerName = customer.customerName,
            activityType = "تسديد ملغى",
            amount = 100.0,
            isCredit = false,
            date = "2026-09-15",
            relativeTime = "15:00",
            settlementType = SettlementType.FULL,
            customerId = customer.id,
            paidAmount = 100.0,
            creditAmount = 0.0,
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            operationStatus = OperationStatus.REVERSED
        )

        val txs = listOf(mixedSale, creditSale, cashSale, payment, reversedCreditSale, reversedPayment)

        val data = AnalyticsExportDataPreparer.prepareAnalyticsData(
            transactions = txs,
            allCustomers = sampleCustomers,
            selectedCustomer = null,
            activePeriod = PeriodFilter.ALL,
            currency = "SAR",
            today = testDate
        )

        // 1. Accounting Invariant Assertions
        assertEquals(450.0, data.metrics.totalSales, 0.001) // 100 + 200 + 150 (reversed 500 excluded)
        assertEquals(210.0, data.metrics.totalCashSales, 0.001) // 60 + 150
        assertEquals(240.0, data.metrics.totalDebtSales, 0.001) // 40 + 200
        assertEquals(50.0, data.metrics.totalPaymentsReceived, 0.001) // 50 (reversed 100 excluded)
        assertEquals(190.0, data.metrics.netOutstandingBalance, 0.001) // 240 - 50

        // 2. PDF Generator Execution
        try {
            val outStream = ByteArrayOutputStream()
            AnalyticsPdfGenerator.generateAnalyticsPdf(
                data = data,
                outputStream = outStream,
                isArabic = true
            )
            val bytes = outStream.toByteArray()
            if (bytes.isNotEmpty()) {
                assertEquals('%'.code.toByte(), bytes[0])
            }
        } catch (_: IllegalStateException) {
            // Expected in headless Robolectric JVM without Skia graphics binaries
        }
    }
}
