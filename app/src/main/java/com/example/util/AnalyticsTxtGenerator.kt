package com.example.util

import com.example.model.AnalyticsChartType
import com.example.model.AnalyticsReportData
import com.example.model.AnalyticsReportScope
import com.example.model.AppCurrency
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated TXT Generator for the Analytics / Statistics Report.
 * Exports the EXACT calculated figures, chart data, scope isolation, and metadata
 * shared with the Analytics PDF, Analytics CSV, and Statistics UI.
 *
 * Requirements:
 * - Full dual-scope support: ALL_CUSTOMERS and ONE_SELECTED_CUSTOMER.
 * - Customer-specific product breakdown strictly isolated to ONE_SELECTED_CUSTOMER.
 * - Preserves ₪ currency formatting in both Arabic and English.
 * - Human-readable clean layout with structured sections.
 * - Real .txt file generation and sharing via standard Android Share mechanism.
 */
object AnalyticsTxtGenerator {

    private const val SEP_DOUBLE = "=================================================="
    private const val SEP_SINGLE = "--------------------------------------------------"

    fun generateAnalyticsTxt(
        data: AnalyticsReportData,
        isArabic: Boolean = true
    ): String {
        val sb = StringBuilder()
        val reportTitle = data.getLocalizedReportTitle(isArabic)
        val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

        // =========================================================================
        // SECTION 1 — HEADER & REPORT INFORMATION
        // =========================================================================
        sb.appendLine(SEP_DOUBLE)
        val storePrefix = if (isArabic) "سمول ستور" else "SMALLSTORE"
        sb.appendLine("$storePrefix | ${data.storeName}")
        sb.appendLine(SEP_DOUBLE)
        sb.appendLine(reportTitle)
        sb.appendLine(SEP_SINGLE)

        val periodLabel = if (isArabic) "فترة التقرير" else "Reporting Period"
        sb.appendLine("$periodLabel: ${data.getLocalizedPeriodLabel(isArabic)}")

        if (data.customStartDate != null && data.customEndDate != null) {
            val rangeLabel = if (isArabic) "نطاق التاريخ المخصص" else "Custom Date Range"
            sb.appendLine("$rangeLabel: ${data.customStartDate} - ${data.customEndDate}")
        }

        val genDateLabel = if (isArabic) "تاريخ ووقت الإصدار" else "Generated"
        sb.appendLine("$genDateLabel: $currentDate")

        val scopeLabel = if (isArabic) "نطاق التقرير" else "REPORT SCOPE"
        val scopeValue = when (data.scope) {
            AnalyticsReportScope.ALL_CUSTOMERS -> if (isArabic) "كافة العملاء" else "All Customers"
            AnalyticsReportScope.ONE_SELECTED_CUSTOMER -> if (isArabic) "عميل محدد" else "Selected Customer"
        }
        sb.appendLine("$scopeLabel: $scopeValue")

        // Selected Customer Details if in ONE_SELECTED_CUSTOMER scope
        if (data.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) {
            val cust = data.selectedCustomer
            val custNameLabel = if (isArabic) "اسم العميل" else "Customer Name"
            sb.appendLine("$custNameLabel: ${cust?.customerName ?: "-"}")

            val phoneLabel = if (isArabic) "رقم الهاتف" else "Phone"
            val phoneVal = cust?.phone?.ifBlank { if (isArabic) "غير محدد" else "Not Specified" } ?: (if (isArabic) "غير محدد" else "Not Specified")
            sb.appendLine("$phoneLabel: $phoneVal")

            val currentBalLabel = if (isArabic) "الرصيد الحالي المستحق" else "Current Balance"
            val currentBalVal = cust?.currentBalance ?: data.metrics.netOutstandingBalance
            sb.appendLine("$currentBalLabel: ${AppCurrency.formatAmountWithDecimals(currentBalVal, isArabic)}")
        }

        sb.appendLine(SEP_SINGLE)
        sb.appendLine()

        // =========================================================================
        // SECTION 2 — SUMMARY / CUSTOMER SUMMARY
        // =========================================================================
        sb.appendLine(SEP_DOUBLE)
        val sec2Title = if (data.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) {
            if (isArabic) "ملخص إحصائيات العميل" else "CUSTOMER SUMMARY"
        } else {
            if (isArabic) "الملخص الإحصائي العام" else "SUMMARY"
        }
        sb.appendLine(sec2Title)
        sb.appendLine(SEP_DOUBLE)

        val totalSalesLabel = if (isArabic) "إجمالي المبيعات" else "Total Sales"
        sb.appendLine("$totalSalesLabel: ${AppCurrency.formatAmountWithDecimals(data.metrics.totalSales, isArabic)}")

        val debtLabel = if (isArabic) "مبيعات الآجل (الديون)" else "Debt / Credit"
        sb.appendLine("$debtLabel: ${AppCurrency.formatAmountWithDecimals(data.metrics.totalDebtSales, isArabic)}")

        val cashLabel = if (isArabic) "المبيعات النقدية (كاش)" else "Cash"
        sb.appendLine("$cashLabel: ${AppCurrency.formatAmountWithDecimals(data.metrics.totalCashSales, isArabic)}")

        val payLabel = if (isArabic) "إجمالي المتحصلات (التسديد)" else "Payments"
        sb.appendLine("$payLabel: ${AppCurrency.formatAmountWithDecimals(data.metrics.totalPaymentsReceived, isArabic)}")

        if (data.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) {
            val periodNetLabel = if (isArabic) "صافي رصيد الفترة" else "Net Balance"
            sb.appendLine("$periodNetLabel: ${AppCurrency.formatAmountWithDecimals(data.metrics.netOutstandingBalance, isArabic)}")
        } else {
            val netBalLabel = if (isArabic) "صافي الديون المستحقة للفترة" else "Net Outstanding Balance"
            sb.appendLine("$netBalLabel: ${AppCurrency.formatAmountWithDecimals(data.metrics.netOutstandingBalance, isArabic)}")

            val custCountLabel = if (isArabic) "إجمالي عدد العملاء" else "Customer Count"
            sb.appendLine("$custCountLabel: ${data.metrics.customerCount ?: 0}")
        }

        val txCountLabel = if (isArabic) "عدد المعاملات" else "Transaction Count"
        sb.appendLine("$txCountLabel: ${data.metrics.transactionCount}")
        sb.appendLine(SEP_SINGLE)
        sb.appendLine()

        // =========================================================================
        // SECTION 3 — OPERATIONS AND DEBT DISTRIBUTION
        // =========================================================================
        sb.appendLine(SEP_DOUBLE)
        val sec3Title = if (isArabic) "توزيع العمليات والديون" else "OPERATIONS AND DEBT DISTRIBUTION"
        sb.appendLine(sec3Title)
        sb.appendLine(SEP_DOUBLE)

        // Find each category from chartData if available, otherwise calculate percentage
        for (cat in data.chartData) {
            val label = cat.getLocalizedLabel(isArabic)
            val amtStr = AppCurrency.formatAmountWithDecimals(cat.amount, isArabic)
            sb.appendLine("$label: $amtStr (${cat.percentage}%)")
        }

        val totalVolLabel = if (isArabic) "إجمالي حجم العمليات" else "Total Operations Volume"
        sb.appendLine("$totalVolLabel: ${AppCurrency.formatAmountWithDecimals(data.totalVolume, isArabic)} (100%)")
        sb.appendLine(SEP_SINGLE)
        sb.appendLine()

        // =========================================================================
        // SECTION 4 — CHART TYPE
        // =========================================================================
        sb.appendLine(SEP_DOUBLE)
        val sec4Title = if (isArabic) "نوع الرسم البياني المحدد" else "CHART TYPE"
        sb.appendLine(sec4Title)
        sb.appendLine(SEP_DOUBLE)

        val selectedChartLabel = if (isArabic) "الرسم البياني المحدد" else "Selected Chart"
        val chartTypeName = when (data.selectedChartType) {
            AnalyticsChartType.CIRCULAR -> if (isArabic) "دائري (Circular)" else "Circular"
            AnalyticsChartType.BAR -> if (isArabic) "شريطي / أعمدة (Bar / Column)" else "Bar / Column"
            AnalyticsChartType.COMBINED -> if (isArabic) "مدمج (Combined)" else "Combined"
        }
        sb.appendLine("$selectedChartLabel: $chartTypeName")
        sb.appendLine(SEP_SINGLE)
        sb.appendLine()

        // =========================================================================
        // SECTION 5 — MOST ORDERED PRODUCTS (SELECTED CUSTOMER SCOPE ONLY)
        // =========================================================================
        if (data.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) {
            sb.appendLine(SEP_DOUBLE)
            val prodSecTitle = if (isArabic) {
                "أكثر الأصناف طلباً لهذا العميل (${data.mostOrderedProducts.size} صنف)"
            } else {
                "MOST ORDERED PRODUCTS FOR THIS CUSTOMER (${data.mostOrderedProducts.size} Products)"
            }
            sb.appendLine(prodSecTitle)
            sb.appendLine(SEP_DOUBLE)

            val rankHeader = if (isArabic) "الترتيب" else "Rank"
            val prodHeader = if (isArabic) "اسم الصنف" else "Product"
            val qtyHeader = if (isArabic) "الكمية" else "Quantity"
            val totalHeader = if (isArabic) "إجمالي القيمة" else "Total"
            sb.appendLine("$rankHeader | $prodHeader | $qtyHeader | $totalHeader")
            sb.appendLine(SEP_SINGLE)

            if (data.mostOrderedProducts.isEmpty()) {
                val emptyMsg = if (isArabic) {
                    "لا توجد أصناف مسجلة لهذا العميل في هذه الفترة"
                } else {
                    "No product lines recorded for this customer in this period"
                }
                sb.appendLine(emptyMsg)
            } else {
                for ((idx, item) in data.mostOrderedProducts.withIndex()) {
                    val amtStr = AppCurrency.formatAmountWithDecimals(item.totalSales, isArabic)
                    sb.appendLine("#${idx + 1} | ${item.productName} | ${item.totalQuantity} | $amtStr")
                }
                sb.appendLine(SEP_SINGLE)

                val prodTotalLabel = if (isArabic) "إجمالي الأصناف المطلوبة" else "Total Ordered Products"
                val totalQty = data.mostOrderedProducts.sumOf { it.totalQuantity }
                val totalProdAmount = data.mostOrderedProducts.sumOf { it.totalSales }
                val totalProdAmtStr = AppCurrency.formatAmountWithDecimals(totalProdAmount, isArabic)
                sb.appendLine("$prodTotalLabel: $totalQty | $totalProdAmtStr")
            }
            sb.appendLine(SEP_SINGLE)
            sb.appendLine()
        }

        // =========================================================================
        // SECTION 6 (or 5) — ADDITIONAL STATISTICS
        // =========================================================================
        sb.appendLine(SEP_DOUBLE)
        val addSecTitle = if (isArabic) "إحصائيات إضافية" else "ADDITIONAL STATISTICS"
        sb.appendLine(addSecTitle)
        sb.appendLine(SEP_DOUBLE)

        // Full Payments
        val fullPayLabel = if (isArabic) "تسديد كامل (دفعات كاملة)" else "Full Payments"
        sb.appendLine("$fullPayLabel: ${AppCurrency.formatAmountWithDecimals(data.metrics.fullSettlementAmount, isArabic)}")

        // Partial Payments
        val partialPayLabel = if (isArabic) "تسديد جزئي (دفعات جزئية)" else "Partial Payments"
        sb.appendLine("$partialPayLabel: ${AppCurrency.formatAmountWithDecimals(data.metrics.partialSettlementAmount, isArabic)}")

        // Debt Sales Ratio %
        val debtRatio = if (data.totalVolume > 0) ((data.metrics.totalDebtSales / data.totalVolume) * 100).toInt() else 0
        val debtRatioLabel = if (isArabic) "نسبة مبيعات الآجل" else "Debt Sales Ratio"
        sb.appendLine("$debtRatioLabel: $debtRatio%")

        // Cash Sales Ratio %
        val cashRatio = if (data.totalVolume > 0) ((data.metrics.totalCashSales / data.totalVolume) * 100).toInt() else 0
        val cashRatioLabel = if (isArabic) "نسبة المبيعات النقدية" else "Cash Sales Ratio"
        sb.appendLine("$cashRatioLabel: $cashRatio%")

        // Debt Collection / Recovery Rate %
        val recoveryRate = if (data.metrics.totalDebtSales > 0) {
            String.format(Locale.US, "%.1f%%", (data.metrics.totalPaymentsReceived / data.metrics.totalDebtSales) * 100)
        } else {
            if (isArabic) "لا توجد ديون" else "No Debts"
        }
        val recoveryLabel = if (isArabic) "نسبة تحصيل الديون" else "Debt Collection Rate"
        sb.appendLine("$recoveryLabel: $recoveryRate")

        sb.appendLine(SEP_DOUBLE)

        return sb.toString()
    }
}
