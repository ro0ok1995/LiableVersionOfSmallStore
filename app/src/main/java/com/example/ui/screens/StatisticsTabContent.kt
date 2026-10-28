package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accounting.FinancialReportCalculator
import com.example.data.db.TransactionItemLineEntity
import com.example.model.AnalyticsExportDataPreparer
import com.example.model.AnalyticsReportData
import com.example.model.AnalyticsReportScope
import com.example.model.CustomerAccount
import com.example.model.PeriodFilter
import com.example.model.StoreInfo
import com.example.model.StoreStrings
import com.example.model.TransactionItem
import com.example.ui.components.BreakdownChartItem
import com.example.ui.components.BreakdownChartTabButton
import com.example.ui.components.BreakdownChartType
import com.example.ui.components.BreakdownColumnChart
import com.example.ui.components.BreakdownComboChart
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.util.ReportExporter
import com.example.viewmodel.DateFilterUtils
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

// -------------------------------------------------------------
// TAB 1: STATISTICS
// -------------------------------------------------------------
@Composable
internal fun StatisticsTabContent(
    transactions: List<TransactionItem>,
    selectedCustomer: CustomerAccount?,
    allCustomers: List<CustomerAccount> = emptyList(),
    transactionLines: List<TransactionItemLineEntity> = emptyList(),
    activePeriod: PeriodFilter,
    customStartDate: LocalDate?,
    customEndDate: LocalDate?,
    selectedChartType: BreakdownChartType = BreakdownChartType.DONUT,
    onChartTypeSelected: (BreakdownChartType) -> Unit = {},
    currency: String,
    isArabic: Boolean,
    context: Context,
    storeInfo: StoreInfo
) {
    var showCostDetails by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val filteredTransactions = remember(transactions, selectedCustomer, activePeriod, customStartDate, customEndDate) {
        var list = if (selectedCustomer != null) {
            transactions.filter { it.customerId == selectedCustomer.id }
        } else {
            transactions
        }
        list.filter { tx ->
            DateFilterUtils.isDateInPeriod(
                dateStr = tx.date,
                period = activePeriod,
                customStartDate = customStartDate,
                customEndDate = customEndDate
            )
        }
    }

    val filteredTransactionIds = remember(filteredTransactions) { filteredTransactions.map { it.id }.toSet() }
    val scopedTransactionLines = remember(transactionLines, filteredTransactionIds) {
        transactionLines.filter { it.transactionId in filteredTransactionIds }
    }
    val statisticsBreakdown = remember(filteredTransactions, scopedTransactionLines) {
        FinancialReportCalculator.calculate(filteredTransactions, scopedTransactionLines)
    }
    val transactionsWithMissingCost = remember(filteredTransactions, scopedTransactionLines) {
        filteredTransactions.filter { tx ->
            val isSale = FinancialReportCalculator.decomposeTransaction(tx).transactionType == com.example.model.TransactionType.SALE
            isSale && (scopedTransactionLines.none { it.transactionId == tx.id } || scopedTransactionLines.filter { it.transactionId == tx.id }.any { !it.costPriceKnown || !it.costPrice.isFinite() || it.quantity <= 0 })
        }
    }
    val totalCashSales = statisticsBreakdown.cashSales
    val totalDebtSales = statisticsBreakdown.creditSales
    val fullSettlementAmount = statisticsBreakdown.fullSettlementAmount
    val partialSettlementAmount = statisticsBreakdown.partialSettlementAmount
    val totalPaymentsReceived = fullSettlementAmount + partialSettlementAmount
    val totalSales = totalCashSales + totalDebtSales
    val netBalance = totalDebtSales - totalPaymentsReceived

    // Donut percentages calculation matching HomeScreen.kt
    val totalReturns = statisticsBreakdown.saleReturns
    // Include returns in the chart volume so the graph responds to returns as well as sales/collections.
    val totalVolume = totalDebtSales + totalCashSales + fullSettlementAmount + partialSettlementAmount + totalReturns
    val chartPercentages = remember(totalDebtSales, totalCashSales, fullSettlementAmount, partialSettlementAmount, totalReturns) {
        calculateChartPercentages(listOf(totalDebtSales, totalCashSales, fullSettlementAmount, partialSettlementAmount, totalReturns))
    }
    val debtPercent = chartPercentages[0]
    val cashPercent = chartPercentages[1]
    val fullPercent = chartPercentages[2]
    val partialPercent = chartPercentages[3]
    val returnsPercent = chartPercentages[4]

    val ringSegments = remember(debtPercent, cashPercent, fullPercent, partialPercent) {
        listOf(
            DonutSegment(debtPercent.toFloat(), StatusRed),
            DonutSegment(cashPercent.toFloat(), StatusBlue),
            DonutSegment(fullPercent.toFloat(), StatusGreen),
            DonutSegment(partialPercent.toFloat(), StatusAmber),
            DonutSegment(returnsPercent.toFloat(), StatusRed.copy(alpha = 0.55f))
        )
    }

    // Prepared Analytics export data reflecting current calculation models, scope, and selected chart mode
    val analyticsReportData = remember(
        transactions, transactionLines, allCustomers, selectedCustomer,
        activePeriod, customStartDate, customEndDate, selectedChartType,
        storeInfo.storeName, currency, isArabic
    ) {
        AnalyticsExportDataPreparer.prepareAnalyticsData(
            transactions = transactions,
            transactionLines = transactionLines,
            allCustomers = allCustomers,
            selectedCustomer = selectedCustomer,
            activePeriod = activePeriod,
            customStartDate = customStartDate,
            customEndDate = customEndDate,
            selectedChartType = selectedChartType,
            storeName = storeInfo.storeName,
            currency = currency,
            isArabic = isArabic
        )
    }

    val breakdownChartItems = remember(
        totalDebtSales, totalCashSales, fullSettlementAmount, partialSettlementAmount, totalReturns,
        debtPercent, cashPercent, fullPercent, partialPercent, returnsPercent
    ) {
        listOf(
            BreakdownChartItem(
                labelAr = "آجل / ديون",
                labelEn = "Debt",
                amount = totalDebtSales,
                percentage = debtPercent,
                color = StatusRed
            ),
            BreakdownChartItem(
                labelAr = "كاش / نقدي",
                labelEn = "Cash",
                amount = totalCashSales,
                percentage = cashPercent,
                color = StatusBlue
            ),
            BreakdownChartItem(
                labelAr = "تسديد كامل",
                labelEn = "Full Payment",
                amount = fullSettlementAmount,
                percentage = fullPercent,
                color = StatusGreen
            ),
            BreakdownChartItem(
                labelAr = "تسديد جزئي",
                labelEn = "Partial Payment",
                amount = partialSettlementAmount,
                percentage = partialPercent,
                color = StatusAmber
            ),
            BreakdownChartItem(
                labelAr = "المرتجعات",
                labelEn = "Returns",
                amount = totalReturns,
                percentage = returnsPercent,
                color = StatusRed.copy(alpha = 0.55f)
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("statistics_tab_content"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // High-level KPI grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiMetricCard(
                title = if (isArabic) StoreStrings.STAT_TOTAL_SALES_AR else StoreStrings.STAT_TOTAL_SALES_EN,
                value = String.format(Locale.US, "%,.2f %s", totalSales, currency),
                color = GeoPrimary,
                testTag = "kpi_total_sales",
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                title = if (isArabic) StoreStrings.STAT_DEBT_CREDIT_AR else StoreStrings.STAT_DEBT_CREDIT_EN,
                value = String.format(Locale.US, "%,.2f %s", totalDebtSales, currency),
                color = StatusRed,
                testTag = "kpi_debt_sales",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiMetricCard(
                title = if (isArabic) StoreStrings.STAT_PAYMENTS_RECEIVED_AR else StoreStrings.STAT_PAYMENTS_RECEIVED_EN,
                value = String.format(Locale.US, "%,.2f %s", totalPaymentsReceived, currency),
                color = StatusGreen,
                testTag = "kpi_payments_received",
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                title = if (isArabic) StoreStrings.STAT_CASH_SALES_AR else StoreStrings.STAT_CASH_SALES_EN,
                value = String.format(Locale.US, "%,.2f %s", totalCashSales, currency),
                color = StatusBlue,
                testTag = "kpi_cash_sales",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiMetricCard(
                title = if (isArabic) StoreStrings.STAT_GROSS_PROFIT_AR else StoreStrings.STAT_GROSS_PROFIT_EN,
                value = String.format(Locale.US, "%,.2f %s", statisticsBreakdown.grossProfit, currency),
                color = if (statisticsBreakdown.grossProfit >= 0.0) StatusGreen else StatusRed,
                testTag = "kpi_gross_profit",
                modifier = Modifier.weight(1f),
                warning = !statisticsBreakdown.costDataComplete
            )
            KpiMetricCard(
                title = if (isArabic) StoreStrings.STAT_NET_PROFIT_AR else StoreStrings.STAT_NET_PROFIT_EN,
                value = String.format(Locale.US, "%,.2f %s", statisticsBreakdown.netProfit, currency),
                color = if (statisticsBreakdown.netProfit >= 0.0) StatusGreen else StatusRed,
                testTag = "kpi_net_profit",
                modifier = Modifier.weight(1f),
                warning = !statisticsBreakdown.costDataComplete
            )
        }

        if (!statisticsBreakdown.costDataComplete) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(androidx.compose.material.icons.Icons.Default.Warning, contentDescription = null, tint = StatusRed, modifier = Modifier.size(18.dp))
                Text(
                    text = if (isArabic) "تكلفة بعض المبيعات غير مكتملة؛ اضغط لمعرفة التفاصيل والإجراء المطلوب." else "Some sale costs are incomplete; tap for details and corrective steps.",
                    color = StatusRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showCostDetails = true }) {
                    Icon(Icons.Default.Info, contentDescription = if (isArabic) "تفاصيل نقص التكلفة" else "Cost details", tint = StatusRed)
                }
            }
        }
        if (showCostDetails) {
            AlertDialog(
                onDismissRequest = { showCostDetails = false },
                title = { Text(if (isArabic) "تفاصيل اكتمال تكلفة المبيعات" else "Sales cost completeness") },
                text = {
                    Text(if (isArabic) {
                        "عدد عمليات البيع المتأثرة: ${transactionsWithMissingCost.size}. " +
                            (if (transactionsWithMissingCost.isEmpty()) "لم يُحدد اسم عملية بعينها؛ راجع بنود التكلفة ضمن الفترة." else "أمثلة: " + transactionsWithMissingCost.take(5).joinToString("، ") { it.title.ifBlank { it.id } } + if (transactionsWithMissingCost.size > 5) "، وغيرها" else "") +
                            "\n\nالسبب: بند تكلفة غير موجود أو غير موثّق، أو تكلفة صفرية قديمة لا يوجد ما يثبت أنها صفر حقيقي. الأثر: لا يُعتمد الربح كرقم نهائي. الحل: راجع تكلفة المنتج وبنود البيع التاريخية من مستندات موثوقة. لا تُخمن تكلفة تاريخية؛ بعد الترحيل تُعد التكلفة الصفرية معروفة فقط إذا أُدخلت أو تحققت صراحةً."
                    } else {
                        "Affected sales: ${transactionsWithMissingCost.size}. " +
                            (if (transactionsWithMissingCost.isEmpty()) "No specific sale name was identified; review cost lines in the selected period." else "Examples: " + transactionsWithMissingCost.take(5).joinToString(", ") { it.title.ifBlank { it.id } } + if (transactionsWithMissingCost.size > 5) ", and others" else "") +
                            "\n\nCause: missing or unverified cost lines, or a historical zero cost with no evidence that it is genuinely zero. Impact: profit is not final. Review product costs and historical sale lines using reliable source documents. Do not guess historical costs; a zero is known only when explicitly entered or verified after migration."
                    })
                },
                confirmButton = { TextButton(onClick = { showCostDetails = false }) { Text(if (isArabic) "فهمت" else "Understood") } }
            )
        }

        // Net Balance Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, GeoOutlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("kpi_net_balance_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isArabic) "صافي مستحقات الديون خلال الفترة" else "Net Outstanding Debt for Period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isArabic) "(مبيعات الآجل - السدادات المستلمة)" else "(Debt Sales - Payments)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = String.format(Locale.US, "%,.2f %s", netBalance, currency),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    ),
                    color = if (netBalance > 0) StatusRed else StatusGreen,
                    modifier = Modifier.testTag("kpi_net_balance_value")
                )
            }
        }

        // VOLUME & DEBT BREAKDOWN CARD WITH 3 CHART TABS (DONUT, COLUMN, COMBO)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, GeoOutlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("statistics_chart_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isArabic) "توزيع حجم العمليات والديون" else "Volume & Debt Breakdown",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Section-level chart tabs: [ دائري / Donut ] [ عمودي / Column ] [ مركب / Combo ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BreakdownChartTabButton(
                        title = if (isArabic) StoreStrings.CHART_TAB_DONUT_AR else StoreStrings.CHART_TAB_DONUT_EN,
                        selected = selectedChartType == BreakdownChartType.DONUT,
                        onClick = { onChartTypeSelected(BreakdownChartType.DONUT) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chart_tab_donut")
                    )
                    BreakdownChartTabButton(
                        title = if (isArabic) StoreStrings.CHART_TAB_COLUMN_AR else StoreStrings.CHART_TAB_COLUMN_EN,
                        selected = selectedChartType == BreakdownChartType.COLUMN,
                        onClick = { onChartTypeSelected(BreakdownChartType.COLUMN) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chart_tab_column")
                    )
                    BreakdownChartTabButton(
                        title = if (isArabic) StoreStrings.CHART_TAB_COMBO_AR else StoreStrings.CHART_TAB_COMBO_EN,
                        selected = selectedChartType == BreakdownChartType.COMBO,
                        onClick = { onChartTypeSelected(BreakdownChartType.COMBO) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chart_tab_combo")
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Informational note directly below the chart type selector
                Text(
                    text = if (isArabic) {
                        "💡 الرسم البياني المحدد حاليًا هو الذي سيتم تضمينه في تقرير PDF."
                    } else {
                        "💡 The currently selected chart will be included in the PDF report."
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .testTag("chart_pdf_note")
                )
                Spacer(modifier = Modifier.height(12.dp))

                when (selectedChartType) {
                    BreakdownChartType.DONUT -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Donut Chart
                            Box(
                                modifier = Modifier.size(136.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                DebtRatioRingChart(
                                    segments = ringSegments,
                                    centerPercentage = debtPercent,
                                    subtitle = if (isArabic) "نسبة الديون" else "Debt Ratio",
                                    modifier = Modifier
                                        .size(136.dp)
                                        .testTag("statistics_donut_chart")
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // 4-item Legend (Debt, Cash, Full Payment, Partial)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatisticsLegendRow(
                                    dotColor = StatusRed,
                                    label = if (isArabic) "آجل / ديون" else "Debt",
                                    amount = totalDebtSales,
                                    percentage = debtPercent,
                                    currency = currency,
                                    testTag = "stat_legend_debt"
                                )
                                StatisticsLegendRow(
                                    dotColor = StatusBlue,
                                    label = if (isArabic) "كاش / نقدي" else "Cash",
                                    amount = totalCashSales,
                                    percentage = cashPercent,
                                    currency = currency,
                                    testTag = "stat_legend_cash"
                                )
                                StatisticsLegendRow(
                                    dotColor = StatusGreen,
                                    label = if (isArabic) "تسديد كامل" else "Full Payment",
                                    amount = fullSettlementAmount,
                                    percentage = fullPercent,
                                    currency = currency,
                                    testTag = "stat_legend_payment_full"
                                )
                                StatisticsLegendRow(
                                    dotColor = StatusAmber,
                                    label = if (isArabic) "تسديد جزئي" else "Partial Payment",
                                    amount = partialSettlementAmount,
                                    percentage = partialPercent,
                                    currency = currency,
                                    testTag = "stat_legend_partial"
                                )
                                StatisticsLegendRow(
                                    dotColor = StatusRed.copy(alpha = 0.55f),
                                    label = if (isArabic) "المرتجعات" else "Returns",
                                    amount = totalReturns,
                                    percentage = returnsPercent,
                                    currency = currency,
                                    testTag = "stat_legend_returns"
                                )
                            }
                        }
                    }

                    BreakdownChartType.COLUMN -> {
                        BreakdownColumnChart(
                            items = breakdownChartItems,
                            currency = currency,
                            isArabic = isArabic
                        )
                    }

                    BreakdownChartType.COMBO -> {
                        BreakdownComboChart(
                            items = breakdownChartItems,
                            currency = currency,
                            isArabic = isArabic
                        )
                    }
                }
            }
        }

        // STANDARDIZED EXPORT ACTIONS (PDF, CSV, Share)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, GeoOutlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (isArabic) "تصدير الإحصائيات والمشاركة" else "Export & Share Statistics",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Export PDF Button
                    Button(
                        onClick = {
                            val title = if (analyticsReportData.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) {
                                if (isArabic) "تقرير إحصائيات العميل" else "Customer Analytics Report"
                            } else {
                                if (isArabic) "تقرير الإحصائيات" else "Analytics Report"
                            }
                            val fileName = if (analyticsReportData.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) {
                                "Customer_Analytics_${System.currentTimeMillis()}.pdf"
                            } else {
                                "Analytics_Report_${System.currentTimeMillis()}.pdf"
                            }
                            val file = ReportExporter.createCachedAnalyticsPdf(
                                context = context,
                                fileName = fileName,
                                data = analyticsReportData,
                                isArabic = isArabic
                            )
                            ReportExporter.shareFile(context, file, "application/pdf", title)
                            Toast.makeText(context, if (isArabic) "تم تجهيز تقرير PDF للمشاركة" else "PDF statistics ready for export", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("statistics_export_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isArabic) "PDF" else "PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Export CSV Button
                    OutlinedButton(
                        onClick = {
                            val title = analyticsReportData.getLocalizedReportTitle(isArabic)
                            val fileName = if (analyticsReportData.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) {
                                "Customer_Analytics_${System.currentTimeMillis()}.csv"
                            } else {
                                "Analytics_Report_${System.currentTimeMillis()}.csv"
                            }
                            val file = ReportExporter.createCachedAnalyticsCsv(
                                context = context,
                                fileName = fileName,
                                data = analyticsReportData,
                                isArabic = isArabic
                            )
                            ReportExporter.shareFile(context, file, "text/csv", title)
                            Toast.makeText(context, if (isArabic) "تم تجهيز ملف CSV للمشاركة" else "CSV statistics ready for export", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("statistics_export_csv_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = GeoPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isArabic) "CSV" else "CSV", color = GeoPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Share Text Button
                    OutlinedButton(
                        onClick = {
                            val title = analyticsReportData.getLocalizedReportTitle(isArabic)
                            val fileName = if (analyticsReportData.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER && analyticsReportData.selectedCustomer != null) {
                                val safeCustName = analyticsReportData.selectedCustomer.customerName
                                    .replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_-]"), "_")
                                    .take(30)
                                "Analytics_Report_${safeCustName}_${System.currentTimeMillis()}.txt"
                            } else {
                                "Analytics_Report_${System.currentTimeMillis()}.txt"
                            }
                            val file = ReportExporter.createCachedAnalyticsTxt(
                                context = context,
                                fileName = fileName,
                                data = analyticsReportData,
                                isArabic = isArabic
                            )
                            ReportExporter.shareFile(context, file, "text/plain", title)
                            Toast.makeText(context, if (isArabic) "تم تجهيز تقرير TXT للمشاركة" else "TXT statistics ready for export", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("statistics_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = GeoPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isArabic) "مشاركة" else "Share", color = GeoPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun StatisticsLegendRow(
    dotColor: Color,
    label: String,
    amount: Double,
    percentage: Int,
    currency: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = String.format(Locale.US, "%,.0f %s", amount, currency),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "($percentage%)",
                style = MaterialTheme.typography.labelSmall,
                color = dotColor
            )
        }
    }
}

@Composable
private fun StatLegendIndicator(
    dotColor: Color,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    value: String,
    color: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false
) {
    var showMetricInfo by androidx.compose.runtime.remember(title, value) { androidx.compose.runtime.mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GeoOutlineVariant),
        modifier = modifier.testTag(testTag)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (warning) Icon(Icons.Default.Warning, contentDescription = if (title.any { it in '\u0600'..'\u06FF' }) "بيانات ناقصة" else "Missing data", tint = StatusRed, modifier = Modifier.size(16.dp))
                IconButton(
                    onClick = { showMetricInfo = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = if (title.any { it in '\u0600'..'\u06FF' }) "شرح المؤشر" else "Metric explanation",
                        tint = GeoPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (showMetricInfo) {
        AlertDialog(
            onDismissRequest = { showMetricInfo = false },
            title = { Text(title) },
            text = { Text(metricExplanation(title, title.any { it in '\u0600'..'\u06FF' })) },
            confirmButton = { TextButton(onClick = { showMetricInfo = false }) { Text(if (title.any { it in '\u0600'..'\u06FF' }) "حسنًا" else "OK") } }
        )
    }
}

private fun metricExplanation(title: String, isArabic: Boolean): String = when {
    title.contains("صافي المبيعات") || title.contains("Net Sales", true) -> if (isArabic)
        "صافي المبيعات = إجمالي المبيعات النشطة − مرتجعات المبيعات النشطة خلال الفترة.\n\nمثال: 1,000 − 100 = 900.\n\nلا تدخل العمليات الملغاة أو المعكوسة في الحساب. هذا المؤشر يخص الفترة المحددة فقط، وليس إجمالي المبيعات التاريخي أو الرصيد الحالي."
    else "Net sales = active sales − active sales returns during the selected period.\n\nExample: 1,000 − 100 = 900.\n\nCancelled or reversed operations are excluded. This metric covers the selected period only, not all-time sales or the current balance."
    title.contains("المبيعات") || title.contains("Sales", true) -> if (isArabic)
        "تعرض هذه البطاقة قيمة المبيعات المسجلة ضمن الفترة المحددة. تُستبعد العمليات الملغاة أو المعكوسة. إذا كانت البطاقة لصافي المبيعات، فيُخصم منها إجمالي مرتجعات المبيعات النشطة.\n\nمثال: مبيعات 1,000 − مرتجعات 100 = صافي مبيعات 900."
    else "This card shows sales recorded in the selected period, excluding cancelled or reversed operations. For net sales, active sales returns are deducted.\n\nExample: 1,000 sales − 100 returns = 900 net sales."
    title.contains("الربح") || title.contains("Profit", true) -> if (isArabic)
        "الربح الإجمالي = صافي المبيعات − تكلفة البضاعة المباعة. الربح الصافي يخصم أيضًا المصاريف الداخلة في الحساب.\n\nإذا ظهر مثلث تحذير، فهذا يعني أن تكلفة بعض المبيعات غير مكتملة أو غير مؤكدة؛ لذلك لا تعتمد قيمة الربح كرقم نهائي قبل مراجعة التفاصيل وتصحيح بيانات التكلفة من مصدر موثوق. لا تُخمّن التكلفة التاريخية."
    else "Gross profit = net sales − cost of goods sold. Net profit also deducts expenses included in the calculation.\n\nA warning triangle means some sale costs are incomplete or unverified. Do not treat profit as final until the cost details are reviewed and corrected using reliable records. Do not guess historical costs."
    title.contains("المدفوعات") || title.contains("Payments", true) || title.contains("السدادات") || title.contains("تحصيل") || title.contains("Collection", true) -> if (isArabic)
        "هذا إجمالي الدفعات أو التحصيلات المسجلة خلال الفترة المحددة بعد استبعاد العمليات الملغاة أو المعكوسة.\n\nمثال: دفعتان بقيمة 100 و50 = إجمالي 150. لا تجمع سجل الدفعة مع سجل آخر يمثل الدفعة نفسها مرة ثانية. هذا الرقم حركة فترة وليس الرصيد الحالي."
    else "This is the total payments or collections recorded during the selected period, excluding cancelled or reversed operations.\n\nExample: two payments of 100 and 50 total 150. Do not count a payment and another record mirroring the same payment twice. This is period activity, not the current balance."
    title.contains("كاش") || title.contains("نقدي") || title.contains("Cash", true) -> if (isArabic)
        "إجمالي قيمة المبيعات النقدية المسجلة ضمن الفترة المحددة. لا يعني هذا المبلغ إجمالي التحصيلات من العملاء، ولا يشمل المبيعات الآجلة. تُستبعد العمليات الملغاة أو المعكوسة."
    else "Total cash sales recorded in the selected period. This is not the same as total customer collections and does not include credit sales. Cancelled or reversed operations are excluded."
    title.contains("ديون") || title.contains("Debt", true) || title.contains("مستحقات") || title.contains("رصيد") || title.contains("Balance", true) -> if (isArabic)
        "يعرض هذا المؤشر حركة أو قيمة الديون وفق تعريف البطاقة وخلال الفترة المختارة؛ وقد يختلف عن الرصيد الحالي للعميل أو المورد. راجع عنوان البطاقة والفترة المحددة قبل المقارنة، ولا تخلط بين حركة الفترة والرصيد المتراكم."
    else "This metric shows debt activity or value according to the card definition and selected period. It may differ from the customer or supplier's current balance. Check the card title and period, and do not confuse period movement with the accumulated balance."
    else -> if (isArabic)
        "تُحسب القيمة من العمليات الفعالة ضمن الفترة المحددة، مع استبعاد العمليات الملغاة أو المعكوسة.\n\nمثال: إذا سُجلت عمليتان بقيمة 100 و50، فالإجمالي 150. قد تختلف هذه الحركة عن الرصيد الحالي؛ استخدم تفاصيل التقرير للتحقق من مكونات القيمة."
    else "This value is calculated from active operations in the selected period, excluding cancelled or reversed operations.\n\nExample: two operations of 100 and 50 total 150. This period activity may differ from the current balance; use the report details to verify the components."
}


/** Calculates whole-number shares using the largest-remainder method; non-zero datasets sum to 100%. */
internal fun calculateChartPercentages(values: List<Double>): List<Int> {
    if (values.isEmpty()) return emptyList()
    val safe = values.map { if (it.isFinite() && it > 0.0) it else 0.0 }
    val total = safe.sum()
    if (total <= 0.0) return List(values.size) { 0 }
    val exact = safe.map { it / total * 100.0 }
    val base = exact.map { kotlin.math.floor(it).toInt() }
    var remaining = 100 - base.sum()
    val order = exact.indices.sortedByDescending { exact[it] - base[it] }
    val result = base.toMutableList()
    var i = 0
    while (remaining > 0 && order.isNotEmpty()) {
        result[order[i % order.size]] += 1
        remaining--
        i++
    }
    return result
}
