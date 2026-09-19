package com.example.util

import com.example.model.AnalyticsChartType
import com.example.model.AnalyticsReportData
import com.example.model.AnalyticsReportScope
import com.example.model.AppCurrency
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated CSV Generator for the Analytics / Statistics Report.
 * Exports the EXACT calculated figures, chart data, scope isolation, and metadata
 * shared with the Analytics PDF and Statistics UI.
 *
 * Requirements:
 * - UTF-8 with BOM for complete Arabic compatibility in Excel, Google Sheets, LibreOffice Calc.
 * - Standard RFC-4180 CSV escaping.
 * - Full dual-scope support: ALL_CUSTOMERS and ONE_SELECTED_CUSTOMER.
 * - Exact chart data export with chart selection metadata.
 * - Customer-specific product breakdown for ONE_SELECTED_CUSTOMER scope.
 * - Preserves ₪ currency formatting.
 */
object AnalyticsCsvGenerator {

    fun generateAnalyticsCsv(
        data: AnalyticsReportData,
        isArabic: Boolean = true
    ): String {
        val sb = StringBuilder()
        sb.append("\uFEFF") // UTF-8 BOM for Microsoft Excel / Google Sheets compatibility

        val reportTitle = data.getLocalizedReportTitle(isArabic)
        val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

        // =========================================================================
        // SECTION 1 — REPORT INFORMATION
        // =========================================================================
        val sec1Title = if (isArabic) "معلومات التقرير" else "REPORT INFORMATION"
        sb.append(csvRow("=== $sec1Title ==="))

        val storeLabel = if (isArabic) "اسم المتجر" else "Store Name"
        sb.append(csvRow(storeLabel, data.storeName))

        val titleLabel = if (isArabic) "عنوان التقرير" else "Report Title"
        sb.append(csvRow(titleLabel, reportTitle))

        val scopeLabel = if (isArabic) "نطاق التقرير" else "Report Scope"
        val scopeValue = when (data.scope) {
            AnalyticsReportScope.ALL_CUSTOMERS -> if (isArabic) "كافة العملاء (All Customers)" else "All Customers"
            AnalyticsReportScope.ONE_SELECTED_CUSTOMER -> if (isArabic) "عميل محدد (Selected Customer)" else "Selected Customer"
        }
        sb.append(csvRow(scopeLabel, scopeValue))

        // Selected Customer Details if in ONE_SELECTED_CUSTOMER scope
        if (data.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) {
            val cust = data.selectedCustomer
            val custNameLabel = if (isArabic) "اسم العميل" else "Customer Name"
            sb.append(csvRow(custNameLabel, cust?.customerName ?: "-"))

            val phoneLabel = if (isArabic) "رقم الهاتف" else "Phone Number"
            val phoneVal = cust?.phone?.ifBlank { if (isArabic) "غير محدد" else "Not Specified" } ?: (if (isArabic) "غير محدد" else "Not Specified")
            sb.append(csvRow(phoneLabel, phoneVal))
        }

        // Chart Selection Metadata (Excel / Google Sheets / Spreadsheet compatibility)
        val chartTypeLabel = if (isArabic) "نوع الرسم البياني المحدد" else "Selected Chart Type"
        val chartTypeMetadata = when (data.selectedChartType) {
            AnalyticsChartType.CIRCULAR -> "Chart Type = Circular"
            AnalyticsChartType.BAR -> "Chart Type = Bar / Column"
            AnalyticsChartType.COMBINED -> "Chart Type = Combined"
        }
        val chartTypeDisplay = if (isArabic) {
            when (data.selectedChartType) {
                AnalyticsChartType.CIRCULAR -> "دائري ($chartTypeMetadata)"
                AnalyticsChartType.BAR -> "شريطي / أعمدة ($chartTypeMetadata)"
                AnalyticsChartType.COMBINED -> "مدمج ($chartTypeMetadata)"
            }
        } else {
            chartTypeMetadata
        }
        sb.append(csvRow(chartTypeLabel, chartTypeDisplay))

        // Reporting Period
        val periodLabel = if (isArabic) "فترة التقرير" else "Reporting Period"
        sb.append(csvRow(periodLabel, data.getLocalizedPeriodLabel(isArabic)))

        if (data.customStartDate != null && data.customEndDate != null) {
            val rangeLabel = if (isArabic) "نطاق التاريخ المخصص" else "Custom Date Range"
            sb.append(csvRow(rangeLabel, "${data.customStartDate} - ${data.customEndDate}"))
        }

        val genDateLabel = if (isArabic) "تاريخ ووقت الإصدار" else "Generation Date/Time"
        sb.append(csvRow(genDateLabel, currentDate))

        val langLabel = if (isArabic) "اللغة" else "Language"
        sb.append(csvRow(langLabel, if (isArabic) "العربية (Arabic)" else "English"))
        sb.append("\n")

        // =========================================================================
        // SECTION 2 — SUMMARY / CUSTOMER SUMMARY
        // =========================================================================
        val sec2Title = if (data.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) {
            if (isArabic) "ملخص إحصائيات العميل" else "CUSTOMER SUMMARY"
        } else {
            if (isArabic) "الملخص الإحصائي العام" else "SUMMARY"
        }
        sb.append(csvRow("=== $sec2Title ==="))

        val summaryHeaders = if (isArabic) {
            listOf("المؤشر الإحصائي", "القيمة", "العملة")
        } else {
            listOf("Metric", "Value", "Currency")
        }
        sb.append(csvRow(summaryHeaders))

        // Total Sales
        val totalSalesLabel = if (isArabic) "إجمالي المبيعات" else "Total Sales"
        sb.append(csvRow(totalSalesLabel, AppCurrency.formatAmountWithDecimals(data.metrics.totalSales, isArabic), data.currency))

        // Debt / Credit Sales
        val debtLabel = if (isArabic) "مبيعات الآجل (الديون)" else "Debt / Credit Sales"
        sb.append(csvRow(debtLabel, AppCurrency.formatAmountWithDecimals(data.metrics.totalDebtSales, isArabic), data.currency))

        // Cash Sales
        val cashLabel = if (isArabic) "المبيعات النقدية (كاش)" else "Cash Sales"
        sb.append(csvRow(cashLabel, AppCurrency.formatAmountWithDecimals(data.metrics.totalCashSales, isArabic), data.currency))

        // Payments Received
        val payLabel = if (isArabic) "إجمالي المتحصلات (التسديد)" else "Total Payments Received"
        sb.append(csvRow(payLabel, AppCurrency.formatAmountWithDecimals(data.metrics.totalPaymentsReceived, isArabic), data.currency))

        if (data.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) {
            // Customer's current overall balance due
            val currentBalLabel = if (isArabic) "الرصيد الحالي المستحق" else "Current Balance Due"
            val currentBalVal = data.selectedCustomer?.currentBalance ?: data.metrics.netOutstandingBalance
            sb.append(csvRow(currentBalLabel, AppCurrency.formatAmountWithDecimals(currentBalVal, isArabic), data.currency))

            // Net Period Balance
            val periodNetLabel = if (isArabic) "صافي رصيد الفترة" else "Net Period Balance"
            sb.append(csvRow(periodNetLabel, AppCurrency.formatAmountWithDecimals(data.metrics.netOutstandingBalance, isArabic), data.currency))
        } else {
            // All Customers: Net Outstanding Balance
            val netBalLabel = if (isArabic) "صافي الديون المستحقة للفترة" else "Net Outstanding Balance"
            sb.append(csvRow(netBalLabel, AppCurrency.formatAmountWithDecimals(data.metrics.netOutstandingBalance, isArabic), data.currency))

            // Customer Count
            val custCountLabel = if (isArabic) "إجمالي عدد العملاء" else "Customer Count"
            sb.append(csvRow(custCountLabel, "${data.metrics.customerCount ?: 0}", "-"))
        }

        // Transaction Count
        val txCountLabel = if (isArabic) "عدد المعاملات" else "Transaction Count"
        sb.append(csvRow(txCountLabel, "${data.metrics.transactionCount}", "-"))
        sb.append("\n")

        // =========================================================================
        // SECTION 3 — CHART DATA
        // =========================================================================
        val sec3Title = if (isArabic) "بيانات الرسم البياني" else "CHART DATA"
        sb.append(csvRow("=== $sec3Title ==="))
        sb.append(csvRow(if (isArabic) "الرسم البياني المعتمد" else "Chart Configuration", chartTypeMetadata))

        val chartHeaders = if (isArabic) {
            listOf("التصنيف / الشريحة", "المبلغ", "النسبة المئوية", "العملة")
        } else {
            listOf("Category", "Amount", "Percentage", "Currency")
        }
        sb.append(csvRow(chartHeaders))

        for (cat in data.chartData) {
            sb.append(csvRow(
                cat.getLocalizedLabel(isArabic),
                AppCurrency.formatAmountWithDecimals(cat.amount, isArabic),
                "${cat.percentage}%",
                data.currency
            ))
        }

        // Total Chart Volume Row
        val totalVolLabel = if (isArabic) "إجمالي حجم العمليات" else "Total Operations Volume"
        sb.append(csvRow(
            totalVolLabel,
            AppCurrency.formatAmountWithDecimals(data.totalVolume, isArabic),
            "100%",
            data.currency
        ))
        sb.append("\n")

        // =========================================================================
        // SECTION 4 — MOST ORDERED PRODUCTS (SELECTED CUSTOMER SCOPE ONLY)
        // =========================================================================
        if (data.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) {
            val prodSecTitle = if (isArabic) {
                "أكثر الأصناف طلباً لهذا العميل (${data.mostOrderedProducts.size} صنف)"
            } else {
                "MOST ORDERED PRODUCTS FOR THIS CUSTOMER (${data.mostOrderedProducts.size} Products)"
            }
            sb.append(csvRow("=== 4. $prodSecTitle ==="))

            val prodHeaders = if (isArabic) {
                listOf("الترتيب", "اسم الصنف", "الكمية", "إجمالي القيمة")
            } else {
                listOf("Rank", "Product Name", "Quantity", "Total Amount")
            }
            sb.append(csvRow(prodHeaders))

            if (data.mostOrderedProducts.isEmpty()) {
                val emptyMsg = if (isArabic) {
                    "لا توجد أصناف مسجلة لهذا العميل في هذه الفترة"
                } else {
                    "No product lines recorded for this customer in this period"
                }
                sb.append(csvRow("-", emptyMsg, "0", AppCurrency.formatAmountWithDecimals(0.0, isArabic)))
            } else {
                for ((idx, item) in data.mostOrderedProducts.withIndex()) {
                    sb.append(csvRow(
                        (idx + 1).toString(),
                        item.productName,
                        item.totalQuantity.toString(),
                        AppCurrency.formatAmountWithDecimals(item.totalSales, isArabic)
                    ))
                }

                // Total Row for products
                val prodTotalLabel = if (isArabic) "إجمالي الأصناف المطلوبة" else "Total Ordered Products"
                val totalQty = data.mostOrderedProducts.sumOf { it.totalQuantity }
                val totalProdAmount = data.mostOrderedProducts.sumOf { it.totalSales }
                sb.append(csvRow(
                    "-",
                    prodTotalLabel,
                    totalQty.toString(),
                    AppCurrency.formatAmountWithDecimals(totalProdAmount, isArabic)
                ))
            }
            sb.append("\n")
        }

        // =========================================================================
        // SECTION 5 (or 4) — ADDITIONAL STATISTICS
        // =========================================================================
        val addSecNumber = if (data.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) 5 else 4
        val addSecTitle = if (isArabic) "إحصائيات إضافية" else "ADDITIONAL STATISTICS"
        sb.append(csvRow("=== $addSecNumber. $addSecTitle ==="))

        val addHeaders = if (isArabic) {
            listOf("المؤشر الإضافي", "القيمة", "الوحدة / العملة")
        } else {
            listOf("Additional Metric", "Value", "Unit / Currency")
        }
        sb.append(csvRow(addHeaders))

        // Full Settlements
        val fullPayLabel = if (isArabic) "تسديد كامل (دفعات كاملة)" else "Full Payments"
        sb.append(csvRow(fullPayLabel, AppCurrency.formatAmountWithDecimals(data.metrics.fullSettlementAmount, isArabic), data.currency))

        // Partial Settlements
        val partialPayLabel = if (isArabic) "تسديد جزئي (دفعات جزئية)" else "Partial Payments"
        sb.append(csvRow(partialPayLabel, AppCurrency.formatAmountWithDecimals(data.metrics.partialSettlementAmount, isArabic), data.currency))

        // Debt Sales Ratio %
        val debtRatio = if (data.totalVolume > 0) ((data.metrics.totalDebtSales / data.totalVolume) * 100).toInt() else 0
        val debtRatioLabel = if (isArabic) "نسبة مبيعات الآجل من حجم العمليات" else "Debt Sales Ratio"
        sb.append(csvRow(debtRatioLabel, "$debtRatio%", "%"))

        // Cash Sales Ratio %
        val cashRatio = if (data.totalVolume > 0) ((data.metrics.totalCashSales / data.totalVolume) * 100).toInt() else 0
        val cashRatioLabel = if (isArabic) "نسبة المبيعات النقدية من حجم العمليات" else "Cash Sales Ratio"
        sb.append(csvRow(cashRatioLabel, "$cashRatio%", "%"))

        // Debt Collection / Recovery Rate %
        val recoveryRate = if (data.metrics.totalDebtSales > 0) {
            String.format(Locale.US, "%.1f%%", (data.metrics.totalPaymentsReceived / data.metrics.totalDebtSales) * 100)
        } else {
            if (isArabic) "لا توجد ديون" else "No Debts"
        }
        val recoveryLabel = if (isArabic) "نسبة تحصيل الديون" else "Debt Collection Rate"
        sb.append(csvRow(recoveryLabel, recoveryRate, "%"))

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"").replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
    }

    private fun csvRow(vararg cells: String): String {
        return cells.joinToString(",") { "\"${escapeCsv(it)}\"" } + "\n"
    }

    private fun csvRow(cells: List<String>): String {
        return cells.joinToString(",") { "\"${escapeCsv(it)}\"" } + "\n"
    }
}
