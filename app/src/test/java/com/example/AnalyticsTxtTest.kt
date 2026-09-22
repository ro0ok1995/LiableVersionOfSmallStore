package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.TransactionItemLineEntity
import com.example.model.AnalyticsExportDataPreparer
import com.example.model.AnalyticsReportScope
import com.example.model.CustomerAccount
import com.example.model.PeriodFilter
import com.example.model.SettlementType
import com.example.model.TransactionItem
import com.example.ui.components.BreakdownChartType
import com.example.util.AnalyticsTxtGenerator
import com.example.util.ReportExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnalyticsTxtTest {

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
        TransactionItemLineEntity(id = 3L, transactionId = "tx2", productId = "p3", productNameSnapshot = "عصير طبيعي", quantity = 10, unitPrice = 15.0, subtotal = 150.0),
        TransactionItemLineEntity(id = 4L, transactionId = "tx4", productId = "p4", productNameSnapshot = "طقم فناجين", quantity = 2, unitPrice = 100.0, subtotal = 200.0)
    )

    @Test
    fun testAllCustomersAnalyticsTxtContentAndStructure() {
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

        val txt = AnalyticsTxtGenerator.generateAnalyticsTxt(data, isArabic = true)

        // 1. Header & Store Info
        assertTrue("Contains store prefix", txt.contains("سمول ستور"))
        assertTrue("Contains store name", txt.contains("بقالة القدس"))
        assertTrue("Contains Arabic Report Title", txt.contains("تقرير الإحصائيات"))
        assertTrue("Contains Period", txt.contains("فترة التقرير"))
        assertTrue("Contains Generated Date", txt.contains("تاريخ ووقت الإصدار"))
        assertTrue("Contains Report Scope All Customers", txt.contains("كافة العملاء"))

        // 2. Summary Section
        assertTrue("Contains Summary Header", txt.contains("الملخص الإحصائي العام"))
        assertTrue("Contains Total Sales", txt.contains("إجمالي المبيعات"))
        assertTrue("Contains Debt Sales", txt.contains("مبيعات الآجل (الديون)"))
        assertTrue("Contains Cash Sales", txt.contains("المبيعات النقدية (كاش)"))
        assertTrue("Contains Payments Received", txt.contains("إجمالي المتحصلات (التسديد)"))
        assertTrue("Contains Net Balance", txt.contains("صافي الديون المستحقة للفترة"))
        assertTrue("Contains Customer Count", txt.contains("إجمالي عدد العملاء"))
        assertTrue("Contains Transaction Count", txt.contains("عدد المعاملات"))
        assertTrue("Contains Currency Symbol", txt.contains("₪"))

        // 3. Operations & Debt Distribution
        assertTrue("Contains Distribution Section", txt.contains("توزيع العمليات والديون"))
        assertTrue("Contains Debt category", txt.contains("مبيعات الآجل (الديون)"))
        assertTrue("Contains Cash category", txt.contains("المبيعات النقدية (كاش)"))
        assertTrue("Contains Full Payment category", txt.contains("تسديد كامل"))
        assertTrue("Contains Partial Payment category", txt.contains("تسديد جزئي"))
        assertTrue("Contains Operations Volume", txt.contains("إجمالي حجم العمليات"))

        // 4. Chart Type
        assertTrue("Contains Chart Type Section", txt.contains("نوع الرسم البياني المحدد"))
        assertTrue("Contains Circular Chart Type", txt.contains("دائري (Circular)"))

        // 5. Additional Statistics
        assertTrue("Contains Additional Stats", txt.contains("إحصائيات إضافية"))
        assertTrue("Contains Full Payments", txt.contains("تسديد كامل (دفعات كاملة)"))
        assertTrue("Contains Partial Payments", txt.contains("تسديد جزئي (دفعات جزئية)"))
        assertTrue("Contains Debt Sales Ratio", txt.contains("نسبة مبيعات الآجل"))
        assertTrue("Contains Cash Sales Ratio", txt.contains("نسبة المبيعات النقدية"))
        assertTrue("Contains Debt Recovery Rate", txt.contains("نسبة تحصيل الديون"))

        // 6. Scope Isolation (No customer-specific sections)
        assertFalse("All-customers TXT must not contain Most Ordered Products section", txt.contains("أكثر الأصناف طلباً لهذا العميل"))
        assertFalse("All-customers TXT must not contain specific customer phone", txt.contains("0599123456"))
    }

    @Test
    fun testSelectedCustomerAnalyticsTxtContentAndIsolation() {
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

        val txt = AnalyticsTxtGenerator.generateAnalyticsTxt(data, isArabic = true)

        // 1. Header & Selected Customer
        assertTrue("Contains Customer Report Title", txt.contains("تقرير إحصائيات العميل"))
        assertTrue("Contains Report Scope Selected Customer", txt.contains("عميل محدد"))
        assertTrue("Contains Selected Customer Name", txt.contains("عمر الفاروق"))
        assertTrue("Contains Selected Customer Phone", txt.contains("0599123456"))
        assertTrue("Contains Current Balance", txt.contains("الرصيد الحالي المستحق"))

        // 2. Customer Summary
        assertTrue("Contains Customer Summary Header", txt.contains("ملخص إحصائيات العميل"))
        assertTrue("Contains Period Net Balance", txt.contains("صافي رصيد الفترة"))

        // 3. Operations & Debt Distribution
        assertTrue("Contains Distribution Section", txt.contains("توزيع العمليات والديون"))

        // 4. Chart Type
        assertTrue("Contains Bar/Column Chart Type", txt.contains("شريطي / أعمدة (Bar / Column)"))

        // 5. Most Ordered Products for THIS customer
        assertTrue("Contains Most Ordered Products Section", txt.contains("أكثر الأصناف طلباً لهذا العميل"))
        assertTrue("Contains Omar's Juice", txt.contains("عصير طبيعي"))
        assertTrue("Contains Omar's Basmati Rice", txt.contains("أرز بسمتي"))
        assertTrue("Contains Omar's White Sugar", txt.contains("سكر أبيض"))

        // CRITICAL DATA ISOLATION: Verify NO other customer's data or products appear
        assertFalse("Must NOT contain Yasser Arafat's name", txt.contains("ياسر عرفات"))
        assertFalse("Must NOT contain Yasser Arafat's phone", txt.contains("0599987654"))
        assertFalse("Must NOT contain Yasser's products", txt.contains("طقم فناجين"))
    }

    @Test
    fun testEnglishAnalyticsTxtGeneration() {
        val testDate = LocalDate.of(2026, 9, 10)
        val data = AnalyticsExportDataPreparer.prepareAnalyticsData(
            transactions = sampleTransactions,
            transactionLines = sampleLines,
            allCustomers = sampleCustomers,
            selectedCustomer = null,
            activePeriod = PeriodFilter.ALL,
            selectedChartType = BreakdownChartType.COMBO,
            storeName = "Al-Quds Central Market",
            currency = "₪",
            isArabic = false,
            today = testDate
        )

        val txt = AnalyticsTxtGenerator.generateAnalyticsTxt(data, isArabic = false)

        // English Headers
        assertTrue("Contains English Store Prefix", txt.contains("SMALLSTORE | Al-Quds Central Market"))
        assertTrue("Contains English Report Title", txt.contains("Analytics Report"))
        assertTrue("Contains English Reporting Period", txt.contains("Reporting Period"))
        assertTrue("Contains English Generated", txt.contains("Generated:"))
        assertTrue("Contains English Scope", txt.contains("REPORT SCOPE: All Customers"))
        assertTrue("Contains English Summary", txt.contains("SUMMARY"))
        assertTrue("Contains Total Sales", txt.contains("Total Sales:"))
        assertTrue("Contains Debt / Credit", txt.contains("Debt / Credit:"))
        assertTrue("Contains Cash", txt.contains("Cash:"))
        assertTrue("Contains Payments", txt.contains("Payments:"))
        assertTrue("Contains Net Outstanding Balance", txt.contains("Net Outstanding Balance:"))
        assertTrue("Contains Customer Count", txt.contains("Customer Count:"))
        assertTrue("Contains Transaction Count", txt.contains("Transaction Count:"))
        assertTrue("Contains Distribution Section", txt.contains("OPERATIONS AND DEBT DISTRIBUTION"))
        assertTrue("Contains Chart Type Section", txt.contains("CHART TYPE"))
        assertTrue("Contains Combined Chart Type", txt.contains("Selected Chart: Combined"))
        assertTrue("Contains Additional Statistics", txt.contains("ADDITIONAL STATISTICS"))
        assertTrue("Contains Currency Symbol ₪", txt.contains("₪"))
    }

    @Test
    fun testCreateCachedAnalyticsTxtFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val data = AnalyticsExportDataPreparer.prepareAnalyticsData(
            transactions = sampleTransactions,
            transactionLines = sampleLines,
            allCustomers = sampleCustomers,
            selectedCustomer = sampleCustomers[0],
            activePeriod = PeriodFilter.ALL,
            selectedChartType = BreakdownChartType.DONUT,
            storeName = "بقالة القدس",
            currency = "₪",
            isArabic = true
        )

        val file = ReportExporter.createCachedAnalyticsTxt(
            context = context,
            fileName = "Analytics_Report_Omar_${System.currentTimeMillis()}.txt",
            data = data,
            isArabic = true
        )

        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
        assertTrue(file.name.endsWith(".txt"))

        val content = file.readText(Charsets.UTF_8)
        assertTrue("File content contains Arabic title", content.contains("تقرير إحصائيات العميل"))
        assertTrue("File content contains customer name", content.contains("عمر الفاروق"))
        assertTrue("File content contains ₪", content.contains("₪"))
    }
}
