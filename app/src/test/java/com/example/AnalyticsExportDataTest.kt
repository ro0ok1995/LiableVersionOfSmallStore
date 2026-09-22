package com.example

import com.example.data.db.TransactionItemLineEntity
import com.example.model.AnalyticsChartCategory
import com.example.model.AnalyticsChartType
import com.example.model.AnalyticsExportDataPreparer
import com.example.model.AnalyticsReportScope
import com.example.model.CustomerAccount
import com.example.model.PeriodFilter
import com.example.model.SettlementType
import com.example.model.TransactionItem
import com.example.model.toAnalyticsChartType
import com.example.model.toBreakdownChartType
import com.example.ui.components.BreakdownChartType
import com.example.viewmodel.AnalysisCenterViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsExportDataTest {

    private val sampleCustomers = listOf(
        CustomerAccount(id = "c1", customerName = "أحمد محمد", balance = 300.0, totalDebt = 500.0, phone = "0501111111"),
        CustomerAccount(id = "c2", customerName = "خالد عمر", balance = 100.0, totalDebt = 100.0, phone = "0502222222")
    )

    private val sampleTransactions = listOf(
        // c1: Debt sale 500
        TransactionItem("tx1", "شراء آجل", "أحمد محمد", "آجل", 500.0, true, "2026-09-01", "اليوم", "سكر وطحين", settlementType = null, customerId = "c1"),
        // c1: Cash sale 150
        TransactionItem("tx2", "شراء نقدي", "أحمد محمد", "كاش", 150.0, false, "2026-09-02", "اليوم", "شاي وعصير", settlementType = null, customerId = "c1"),
        // c1: Partial payment 200
        TransactionItem("tx3", "تسديد جزئي", "أحمد محمد", "تسديد", 200.0, false, "2026-09-03", "اليوم", "دفعة جزئية", settlementType = SettlementType.PARTIAL, customerId = "c1"),
        // c2: Debt sale 200
        TransactionItem("tx4", "شراء آجل", "خالد عمر", "آجل", 200.0, true, "2026-09-04", "اليوم", "زيت وأرز", settlementType = null, customerId = "c2"),
        // c2: Full payment 100
        TransactionItem("tx5", "تسديد كامل", "خالد عمر", "تسديد", 100.0, false, "2026-09-05", "اليوم", "تسديد كامل الحساب", settlementType = SettlementType.FULL, customerId = "c2")
    )

    private val sampleLines = listOf(
        TransactionItemLineEntity(id = 1L, transactionId = "tx1", productId = "p1", productNameSnapshot = "سكر 10 كجم", quantity = 4, unitPrice = 50.0, subtotal = 200.0),
        TransactionItemLineEntity(id = 2L, transactionId = "tx1", productId = "p2", productNameSnapshot = "طحين فاخر", quantity = 3, unitPrice = 100.0, subtotal = 300.0),
        TransactionItemLineEntity(id = 3L, transactionId = "tx2", productId = "p1", productNameSnapshot = "سكر 10 كجم", quantity = 3, unitPrice = 50.0, subtotal = 150.0),
        TransactionItemLineEntity(id = 4L, transactionId = "tx4", productId = "p3", productNameSnapshot = "زيت ذرة", quantity = 1, unitPrice = 200.0, subtotal = 200.0)
    )

    @Test
    fun testAllCustomersScopeDataPreparation() {
        val testDate = LocalDate.of(2026, 9, 15)
        val data = AnalyticsExportDataPreparer.prepareAnalyticsData(
            transactions = sampleTransactions,
            transactionLines = sampleLines,
            allCustomers = sampleCustomers,
            selectedCustomer = null,
            activePeriod = PeriodFilter.ALL,
            selectedChartType = BreakdownChartType.DONUT,
            storeName = "متجر النور",
            currency = "ر.س",
            isArabic = true,
            today = testDate
        )

        // 1. Verify Scope
        assertEquals(AnalyticsReportScope.ALL_CUSTOMERS, data.scope)
        assertNull(data.selectedCustomer)
        assertEquals(2, data.metrics.customerCount)
        assertEquals("كافة العملاء", data.getLocalizedScopeName(isArabic = true))
        assertEquals("All Customers", data.getLocalizedScopeName(isArabic = false))

        // 2. Verify Metrics
        // Total Debt Sales: tx1 (500) + tx4 (200) = 700
        assertEquals(700.0, data.metrics.totalDebtSales, 0.001)
        // Total Cash Sales: tx2 (150) = 150
        assertEquals(150.0, data.metrics.totalCashSales, 0.001)
        // Total Sales = 700 + 150 = 850
        assertEquals(850.0, data.metrics.totalSales, 0.001)
        // Full Settlement: tx5 (100) = 100
        assertEquals(100.0, data.metrics.fullSettlementAmount, 0.001)
        // Partial Settlement: tx3 (200) = 200
        assertEquals(200.0, data.metrics.partialSettlementAmount, 0.001)
        // Total Payments = 100 + 200 = 300
        assertEquals(300.0, data.metrics.totalPaymentsReceived, 0.001)
        // Net Balance = 700 - 300 = 400
        assertEquals(400.0, data.metrics.netOutstandingBalance, 0.001)
        // Transaction Count = 5
        assertEquals(5, data.metrics.transactionCount)

        // 3. Verify Chart Categories
        assertEquals(4, data.chartData.size)
        val debtCat = data.chartData.first { it.categoryKey == "DEBT" }
        assertEquals(700.0, debtCat.amount, 0.001)
        val cashCat = data.chartData.first { it.categoryKey == "CASH" }
        assertEquals(150.0, cashCat.amount, 0.001)
        val fullCat = data.chartData.first { it.categoryKey == "FULL_PAYMENT" }
        assertEquals(100.0, fullCat.amount, 0.001)
        val partialCat = data.chartData.first { it.categoryKey == "PARTIAL_PAYMENT" }
        assertEquals(200.0, partialCat.amount, 0.001)

        // 4. Verify Most Ordered Products is empty for ALL_CUSTOMERS
        assertTrue(data.mostOrderedProducts.isEmpty())

        // 5. Verify Chart Type
        assertEquals(AnalyticsChartType.CIRCULAR, data.selectedChartType)
        assertEquals(BreakdownChartType.DONUT, data.chartBreakdownType)
    }

    @Test
    fun testSelectedCustomerScopeDataPreparation() {
        val testDate = LocalDate.of(2026, 9, 15)
        val selected = sampleCustomers[0] // "أحمد محمد"
        val data = AnalyticsExportDataPreparer.prepareAnalyticsData(
            transactions = sampleTransactions,
            transactionLines = sampleLines,
            allCustomers = sampleCustomers,
            selectedCustomer = selected,
            activePeriod = PeriodFilter.ALL,
            selectedChartType = BreakdownChartType.COLUMN,
            storeName = "متجر النور",
            currency = "ر.س",
            isArabic = true,
            today = testDate
        )

        // 1. Verify Scope & Isolation
        assertEquals(AnalyticsReportScope.ONE_SELECTED_CUSTOMER, data.scope)
        assertNotNull(data.selectedCustomer)
        assertEquals("c1", data.selectedCustomer?.customerId)
        assertEquals("أحمد محمد", data.selectedCustomer?.customerName)
        assertEquals("0501111111", data.selectedCustomer?.phone)
        assertEquals(300.0, data.selectedCustomer?.currentBalance ?: 0.0, 0.001)
        assertEquals(500.0, data.selectedCustomer?.totalDebt ?: 0.0, 0.001)
        assertNull(data.metrics.customerCount) // Customer count null in single customer report

        // 2. Verify Customer Metrics
        // c1 only has tx1, tx2, tx3
        assertEquals(3, data.metrics.transactionCount)
        assertEquals(500.0, data.metrics.totalDebtSales, 0.001)
        assertEquals(150.0, data.metrics.totalCashSales, 0.001)
        assertEquals(650.0, data.metrics.totalSales, 0.001)
        assertEquals(0.0, data.metrics.fullSettlementAmount, 0.001)
        assertEquals(200.0, data.metrics.partialSettlementAmount, 0.001)
        assertEquals(200.0, data.metrics.totalPaymentsReceived, 0.001)
        assertEquals(300.0, data.metrics.netOutstandingBalance, 0.001)

        // 3. Verify Most Ordered Products for Selected Customer
        // c1 ordered:
        // "سكر 10 كجم": tx1 (4) + tx2 (3) = 7 qty
        // "طحين فاخر": tx1 (3) = 3 qty
        // Sorted by quantity descending!
        assertEquals(2, data.mostOrderedProducts.size)
        assertEquals("سكر 10 كجم", data.mostOrderedProducts[0].productName)
        assertEquals(7, data.mostOrderedProducts[0].totalQuantity)
        assertEquals(350.0, data.mostOrderedProducts[0].totalSales, 0.001)

        assertEquals("طحين فاخر", data.mostOrderedProducts[1].productName)
        assertEquals(3, data.mostOrderedProducts[1].totalQuantity)
        assertEquals(300.0, data.mostOrderedProducts[1].totalSales, 0.001)

        // 4. Verify Chart Type is Column / Bar
        assertEquals(AnalyticsChartType.BAR, data.selectedChartType)
        assertEquals(BreakdownChartType.COLUMN, data.chartBreakdownType)
    }

    @Test
    fun testChartTypeMappingAndViewModelIntegration() {
        // Test Enum bidirectional conversion
        assertEquals(AnalyticsChartType.CIRCULAR, BreakdownChartType.DONUT.toAnalyticsChartType())
        assertEquals(AnalyticsChartType.BAR, BreakdownChartType.COLUMN.toAnalyticsChartType())
        assertEquals(AnalyticsChartType.COMBINED, BreakdownChartType.COMBO.toAnalyticsChartType())

        assertEquals(BreakdownChartType.DONUT, AnalyticsChartType.CIRCULAR.toBreakdownChartType())
        assertEquals(BreakdownChartType.COLUMN, AnalyticsChartType.BAR.toBreakdownChartType())
        assertEquals(BreakdownChartType.COMBO, AnalyticsChartType.COMBINED.toBreakdownChartType())

        // Test ViewModel state tracking
        val vm = AnalysisCenterViewModel()
        assertEquals(BreakdownChartType.DONUT, vm.uiState.value.selectedChartType)

        vm.selectChartType(BreakdownChartType.COMBO)
        assertEquals(BreakdownChartType.COMBO, vm.uiState.value.selectedChartType)

        val preparedData = vm.getAnalyticsExportData(
            transactions = sampleTransactions,
            transactionLines = sampleLines,
            allCustomers = sampleCustomers,
            storeName = "متجر الأمل",
            currency = "SAR",
            isArabic = false
        )
        assertEquals(AnalyticsChartType.COMBINED, preparedData.selectedChartType)
        assertEquals(BreakdownChartType.COMBO, preparedData.chartBreakdownType)
    }

    @Test
    fun testDateFilteringPreserved() {
        val testDate = LocalDate.of(2026, 9, 2)
        val dataToday = AnalyticsExportDataPreparer.prepareAnalyticsData(
            transactions = sampleTransactions,
            selectedCustomer = null,
            activePeriod = PeriodFilter.TODAY,
            today = testDate
        )
        // On 2026-09-02, only tx2 exists
        assertEquals(1, dataToday.metrics.transactionCount)
        assertEquals("tx2", dataToday.transactions[0].id)
    }
}
