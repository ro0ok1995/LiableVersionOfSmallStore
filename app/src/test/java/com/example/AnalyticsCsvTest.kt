package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.TransactionItemLineEntity
import com.example.model.AnalyticsChartType
import com.example.model.AnalyticsExportDataPreparer
import com.example.model.AnalyticsReportScope
import com.example.model.CustomerAccount
import com.example.model.PeriodFilter
import com.example.model.SettlementType
import com.example.model.TransactionItem
import com.example.ui.components.BreakdownChartType
import com.example.util.AnalyticsCsvGenerator
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
class AnalyticsCsvTest {

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
    fun testAllCustomersAnalyticsCsvContentAndStructure() {
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

        val csv = AnalyticsCsvGenerator.generateAnalyticsCsv(data, isArabic = true)

        // 1. Verify UTF-8 BOM
        assertTrue("CSV must start with UTF-8 BOM", csv.startsWith("\uFEFF"))

        // 2. Section 1: Report Information
        assertTrue("Contains Section 1", csv.contains("معلومات التقرير"))
        assertTrue("Contains Store Name", csv.contains("بقالة القدس"))
        assertTrue("Contains Report Scope All Customers", csv.contains("كافة العملاء"))
        assertTrue("Contains Selected Chart Type", csv.contains("Chart Type = Circular"))
        assertTrue("Contains Language", csv.contains("العربية"))

        // 3. Section 2: Summary
        assertTrue("Contains Section 2 Summary", csv.contains("الملخص الإحصائي العام"))
        assertTrue("Contains Total Sales", csv.contains("إجمالي المبيعات"))
        assertTrue("Contains Debt Sales", csv.contains("مبيعات الآجل (الديون)"))
        assertTrue("Contains Cash Sales", csv.contains("المبيعات النقدية (كاش)"))
        assertTrue("Contains Payments Received", csv.contains("إجمالي المتحصلات (التسديد)"))
        assertTrue("Contains Net Outstanding Balance", csv.contains("صافي الديون المستحقة للفترة"))
        assertTrue("Contains Customer Count", csv.contains("إجمالي عدد العملاء"))
        assertTrue("Contains Transaction Count", csv.contains("عدد المعاملات"))
        assertTrue("Contains Currency Symbol", csv.contains("₪"))

        // 4. Section 3: Chart Data
        assertTrue("Contains Section 3 Chart Data", csv.contains("بيانات الرسم البياني"))
        assertTrue("Contains Chart Metadata", csv.contains("Chart Type = Circular"))
        assertTrue("Contains Category Column", csv.contains("التصنيف / الشريحة"))
        assertTrue("Contains Operations Volume", csv.contains("إجمالي حجم العمليات"))

        // 5. Section 4: Additional Statistics
        assertTrue("Contains Section 4 Additional Stats", csv.contains("إحصائيات إضافية"))
        assertTrue("Contains Full Settlement", csv.contains("تسديد كامل"))
        assertTrue("Contains Partial Settlement", csv.contains("تسديد جزئي"))
        assertTrue("Contains Debt Sales Ratio", csv.contains("نسبة مبيعات الآجل"))
        assertTrue("Contains Cash Sales Ratio", csv.contains("نسبة المبيعات النقدية"))

        // 6. Verify Scope Isolation (No customer-specific items)
        assertFalse("All-customers CSV must not contain Most Ordered Products section", csv.contains("أكثر الأصناف طلباً لهذا العميل"))
        assertFalse("All-customers CSV must not contain specific customer phone", csv.contains("0599123456"))
    }

    @Test
    fun testSelectedCustomerAnalyticsCsvContentAndIsolation() {
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

        val csv = AnalyticsCsvGenerator.generateAnalyticsCsv(data, isArabic = true)

        // 1. Verify UTF-8 BOM
        assertTrue("CSV must start with UTF-8 BOM", csv.startsWith("\uFEFF"))

        // 2. Section 1: Report Information
        assertTrue("Contains Report Scope Selected Customer", csv.contains("عميل محدد"))
        assertTrue("Contains Selected Customer Name", csv.contains("عمر الفاروق"))
        assertTrue("Contains Selected Customer Phone", csv.contains("0599123456"))
        assertTrue("Contains Bar/Column Chart metadata", csv.contains("Chart Type = Bar / Column"))

        // 3. Section 2: Customer Summary
        assertTrue("Contains Customer Summary Header", csv.contains("ملخص إحصائيات العميل"))
        assertTrue("Contains Current Balance Due", csv.contains("الرصيد الحالي المستحق"))
        assertTrue("Contains Net Period Balance", csv.contains("صافي رصيد الفترة"))

        // 4. Section 4: Most Ordered Products for THIS customer
        assertTrue("Contains Most Ordered Products Section", csv.contains("أكثر الأصناف طلباً لهذا العميل"))
        assertTrue("Contains Omar's Juice", csv.contains("عصير طبيعي"))
        assertTrue("Contains Omar's Basmati Rice", csv.contains("أرز بسمتي"))
        assertTrue("Contains Omar's White Sugar", csv.contains("سكر أبيض"))

        // CRITICAL: Verify NO other customer's data or products appear
        assertFalse("Must NOT contain Yasser Arafat's data", csv.contains("ياسر عرفات"))
        assertFalse("Must NOT contain Yasser Arafat's phone", csv.contains("0599987654"))
        assertFalse("Must NOT contain Yasser's products", csv.contains("طقم فناجين"))
    }

    @Test
    fun testCombinedChartMetadataAndEscaping() {
        val testDate = LocalDate.of(2026, 9, 10)
        val data = AnalyticsExportDataPreparer.prepareAnalyticsData(
            transactions = sampleTransactions,
            transactionLines = sampleLines,
            allCustomers = sampleCustomers,
            selectedCustomer = null,
            activePeriod = PeriodFilter.ALL,
            selectedChartType = BreakdownChartType.COMBO,
            storeName = "Store \"Al-Quds, Central\"",
            currency = "₪",
            isArabic = false,
            today = testDate
        )

        val csv = AnalyticsCsvGenerator.generateAnalyticsCsv(data, isArabic = false)

        // Verify CSV escaping of quotes and commas in store name
        assertTrue("Quotes must be escaped with double quotes", csv.contains("\"Store \"\"Al-Quds, Central\"\"\""))
        assertTrue("Contains Combined Chart Metadata", csv.contains("Chart Type = Combined"))
        assertTrue("Contains English headers", csv.contains("REPORT INFORMATION"))
        assertTrue("Contains Category header", csv.contains("Category"))
    }

    @Test
    fun testCreateCachedAnalyticsCsvFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val data = AnalyticsExportDataPreparer.prepareAnalyticsData(
            transactions = sampleTransactions,
            transactionLines = sampleLines,
            allCustomers = sampleCustomers,
            selectedCustomer = null,
            activePeriod = PeriodFilter.ALL,
            selectedChartType = BreakdownChartType.DONUT,
            storeName = "بقالة القدس",
            currency = "₪",
            isArabic = true
        )

        val file = ReportExporter.createCachedAnalyticsCsv(
            context = context,
            fileName = "Test_Analytics_${System.currentTimeMillis()}.csv",
            data = data,
            isArabic = true
        )

        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)

        // Verify file bytes start with UTF-8 BOM
        val bytes = file.readBytes()
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])
    }
}
