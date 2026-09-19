package com.example.model

import com.example.data.db.TransactionItemLineEntity
import com.example.ui.components.BreakdownChartType
import com.example.ui.screens.AggregatedProductLine
import com.example.viewmodel.DateFilterUtils
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Report scope for Analytics / Statistics export.
 * Strictly distinguishes between ALL_CUSTOMERS and ONE_SELECTED_CUSTOMER.
 */
enum class AnalyticsReportScope {
    ALL_CUSTOMERS,
    ONE_SELECTED_CUSTOMER
}

/**
 * High-level presentation options for the chart in Analytics export.
 * CIRCULAR = Donut (دائري)
 * BAR = Column (عمودي)
 * COMBINED = Combo (مركب)
 */
enum class AnalyticsChartType {
    CIRCULAR,
    BAR,
    COMBINED
}

fun BreakdownChartType.toAnalyticsChartType(): AnalyticsChartType = when (this) {
    BreakdownChartType.DONUT -> AnalyticsChartType.CIRCULAR
    BreakdownChartType.COLUMN -> AnalyticsChartType.BAR
    BreakdownChartType.COMBO -> AnalyticsChartType.COMBINED
}

fun AnalyticsChartType.toBreakdownChartType(): BreakdownChartType = when (this) {
    AnalyticsChartType.CIRCULAR -> BreakdownChartType.DONUT
    AnalyticsChartType.BAR -> BreakdownChartType.COLUMN
    AnalyticsChartType.COMBINED -> BreakdownChartType.COMBO
}

/**
 * Structured entry representing a category of the "Distribution of Operations and Debts" chart.
 * Preserves the exact 4 existing categories:
 * - Debt / Credit (آجل / ديون)
 * - Cash (كاش / نقدي)
 * - Full Payment (تسديد كامل)
 * - Partial Payment (تسديد جزئي)
 */
data class AnalyticsChartCategory(
    val categoryKey: String, // "DEBT", "CASH", "FULL_PAYMENT", "PARTIAL_PAYMENT"
    val labelAr: String,
    val labelEn: String,
    val amount: Double,
    val percentage: Int
) {
    fun getLocalizedLabel(isArabic: Boolean): String = if (isArabic) labelAr else labelEn
}

/**
 * Customer metadata when report scope is ONE_SELECTED_CUSTOMER.
 * Never instantiated or fabricated for ALL_CUSTOMERS.
 */
data class AnalyticsCustomerInfo(
    val customerId: String,
    val customerName: String,
    val phone: String,
    val currentBalance: Double,
    val totalDebt: Double
)

/**
 * Reused KPIs and metrics visible in the Statistics screen.
 */
data class AnalyticsSummaryMetrics(
    val totalSales: Double,
    val totalDebtSales: Double,
    val totalCashSales: Double,
    val totalPaymentsReceived: Double,
    val fullSettlementAmount: Double,
    val partialSettlementAmount: Double,
    val netOutstandingBalance: Double,
    val transactionCount: Int,
    val customerCount: Int? = null // non-null for ALL_CUSTOMERS, null for ONE_SELECTED_CUSTOMER
)

/**
 * Complete prepared analytics data model for future PDF, CSV, and TXT exports.
 * Contains the EXACT calculated figures, chart categories, customer isolation, and selected chart mode.
 */
data class AnalyticsReportData(
    val scope: AnalyticsReportScope,
    val period: PeriodFilter,
    val periodLabelAr: String,
    val periodLabelEn: String,
    val customStartDate: LocalDate?,
    val customEndDate: LocalDate?,
    val selectedChartType: AnalyticsChartType,
    val chartBreakdownType: BreakdownChartType,
    val chartData: List<AnalyticsChartCategory>,
    val totalVolume: Double,
    val metrics: AnalyticsSummaryMetrics,
    val selectedCustomer: AnalyticsCustomerInfo?,
    val mostOrderedProducts: List<AggregatedProductLine>,
    val transactions: List<TransactionItem>,
    val storeName: String,
    val currency: String
) {
    fun getLocalizedScopeName(isArabic: Boolean): String = when (scope) {
        AnalyticsReportScope.ALL_CUSTOMERS -> if (isArabic) "كافة العملاء" else "All Customers"
        AnalyticsReportScope.ONE_SELECTED_CUSTOMER -> selectedCustomer?.customerName ?: (if (isArabic) "عميل محدد" else "Selected Customer")
    }

    fun getLocalizedPeriodLabel(isArabic: Boolean): String = if (isArabic) periodLabelAr else periodLabelEn

    fun getLocalizedChartTypeName(isArabic: Boolean): String = when (selectedChartType) {
        AnalyticsChartType.CIRCULAR -> if (isArabic) StoreStrings.CHART_TAB_DONUT_AR else StoreStrings.CHART_TAB_DONUT_EN
        AnalyticsChartType.BAR -> if (isArabic) StoreStrings.CHART_TAB_COLUMN_AR else StoreStrings.CHART_TAB_COLUMN_EN
        AnalyticsChartType.COMBINED -> if (isArabic) StoreStrings.CHART_TAB_COMBO_AR else StoreStrings.CHART_TAB_COMBO_EN
    }

    fun getLocalizedReportTitle(isArabic: Boolean): String = when (scope) {
        AnalyticsReportScope.ALL_CUSTOMERS -> if (isArabic) "تقرير الإحصائيات" else "Analytics Report"
        AnalyticsReportScope.ONE_SELECTED_CUSTOMER -> if (isArabic) "تقرير إحصائيات العميل" else "Customer Analytics Report"
    }
}

/**
 * Engine to prepare Analytics export data strictly using the existing calculations from the Statistics screen.
 */
object AnalyticsExportDataPreparer {

    fun prepareAnalyticsData(
        transactions: List<TransactionItem>,
        transactionLines: List<TransactionItemLineEntity> = emptyList(),
        allCustomers: List<CustomerAccount> = emptyList(),
        selectedCustomer: CustomerAccount? = null,
        activePeriod: PeriodFilter = PeriodFilter.MONTH,
        customStartDate: LocalDate? = null,
        customEndDate: LocalDate? = null,
        selectedChartType: BreakdownChartType = BreakdownChartType.DONUT,
        storeName: String = "",
        currency: String = AppCurrency.SYMBOL,
        isArabic: Boolean = true,
        today: LocalDate = LocalDate.now()
    ): AnalyticsReportData {
        // 1. Determine Scope
        val scope = if (selectedCustomer != null) {
            AnalyticsReportScope.ONE_SELECTED_CUSTOMER
        } else {
            AnalyticsReportScope.ALL_CUSTOMERS
        }

        // 2. Filter transactions by customer and period (matches StatisticsTabContent lines 850-864)
        val customerFilteredTxs = if (selectedCustomer != null) {
            transactions.filter { it.customerName.equals(selectedCustomer.customerName, ignoreCase = true) }
        } else {
            transactions
        }
        val filteredTransactions = customerFilteredTxs.filter { tx ->
            DateFilterUtils.isDateInPeriod(
                dateStr = tx.date,
                period = activePeriod,
                customStartDate = customStartDate,
                customEndDate = customEndDate,
                today = today
            )
        }

        // 3. Exact calculations matching StatisticsTabContent lines 866-896
        val totalCashSales = filteredTransactions
            .filter { !it.isCredit && (it.activityType.contains("كاش") || it.activityType.contains("Cash") || (!it.activityType.contains("تسديد") && !it.activityType.contains("Payment"))) }
            .sumOf { it.amount }

        val totalDebtSales = filteredTransactions
            .filter { it.isCredit || it.activityType.contains("آجل") || it.activityType.contains("دين") }
            .sumOf { it.amount }

        val fullSettlementAmount = filteredTransactions
            .filter { (it.activityType.contains("تسديد") || it.activityType.contains("Payment")) && (it.settlementType == SettlementType.FULL || it.settlementType == null) }
            .sumOf { it.amount }

        val partialSettlementAmount = filteredTransactions
            .filter { (it.activityType.contains("تسديد") || it.activityType.contains("Payment")) && it.settlementType == SettlementType.PARTIAL }
            .sumOf { it.amount }

        val totalPaymentsReceived = fullSettlementAmount + partialSettlementAmount
        val totalSales = totalCashSales + totalDebtSales
        val netBalance = totalDebtSales - totalPaymentsReceived

        // Volume & percentages matching HomeScreen.kt and StatisticsTabContent
        val totalVolume = totalDebtSales + totalCashSales + fullSettlementAmount + partialSettlementAmount
        val debtPercent = if (totalVolume > 0) ((totalDebtSales / totalVolume) * 100).roundToInt() else 0
        val cashPercent = if (totalVolume > 0) ((totalCashSales / totalVolume) * 100).roundToInt() else 0
        val fullPercent = if (totalVolume > 0) ((fullSettlementAmount / totalVolume) * 100).roundToInt() else 0
        val partialPercent = if (totalVolume > 0) (100 - debtPercent - cashPercent - fullPercent).coerceAtLeast(0) else 0

        // 4. Structured chart categories preserving existing values & labels
        val chartCategories = listOf(
            AnalyticsChartCategory(
                categoryKey = "DEBT",
                labelAr = "آجل / ديون",
                labelEn = "Debt",
                amount = totalDebtSales,
                percentage = debtPercent
            ),
            AnalyticsChartCategory(
                categoryKey = "CASH",
                labelAr = "كاش / نقدي",
                labelEn = "Cash",
                amount = totalCashSales,
                percentage = cashPercent
            ),
            AnalyticsChartCategory(
                categoryKey = "FULL_PAYMENT",
                labelAr = "تسديد كامل",
                labelEn = "Full Payment",
                amount = fullSettlementAmount,
                percentage = fullPercent
            ),
            AnalyticsChartCategory(
                categoryKey = "PARTIAL_PAYMENT",
                labelAr = "تسديد جزئي",
                labelEn = "Partial Payment",
                amount = partialSettlementAmount,
                percentage = partialPercent
            )
        )

        // 5. Selected Customer Info (strictly only for ONE_SELECTED_CUSTOMER)
        val customerInfo = if (selectedCustomer != null) {
            AnalyticsCustomerInfo(
                customerId = selectedCustomer.id,
                customerName = selectedCustomer.customerName,
                phone = selectedCustomer.phone,
                currentBalance = selectedCustomer.balance,
                totalDebt = selectedCustomer.totalDebt
            )
        } else null

        // 6. Most Ordered Products (for selected customer only, matching Comprehensive Customer Report)
        val mostOrderedProducts = if (selectedCustomer != null) {
            val custTxIds = filteredTransactions.map { it.id }.toSet()
            val lines = transactionLines.filter { it.transactionId in custTxIds }
            lines.groupBy { it.productNameSnapshot.ifBlank { it.productId ?: "-" } }
                .map { (name, gLines) ->
                    AggregatedProductLine(
                        productId = gLines.firstOrNull()?.productId ?: "",
                        productName = name,
                        totalQuantity = gLines.sumOf { it.quantity },
                        totalSales = gLines.sumOf { it.subtotal },
                        profitMargin = 0.0
                    )
                }.sortedByDescending { it.totalQuantity }
        } else {
            emptyList()
        }

        // 7. Period Labels matching existing standard formatting
        val periodLabelAr = when (activePeriod) {
            PeriodFilter.ALL -> "كافة الفترات"
            PeriodFilter.TODAY -> "اليوم ($today)"
            PeriodFilter.MONTH -> "هذا الشهر (${today.monthValue}/${today.year})"
            PeriodFilter.CUSTOM -> {
                if (customStartDate != null && customEndDate != null) "$customStartDate إلى $customEndDate"
                else "فترة مخصصة"
            }
        }
        val periodLabelEn = when (activePeriod) {
            PeriodFilter.ALL -> "All Time"
            PeriodFilter.TODAY -> "Today ($today)"
            PeriodFilter.MONTH -> "This Month (${today.monthValue}/${today.year})"
            PeriodFilter.CUSTOM -> {
                if (customStartDate != null && customEndDate != null) "$customStartDate to $customEndDate"
                else "Custom Period"
            }
        }

        // 8. Summary metrics
        val summaryMetrics = AnalyticsSummaryMetrics(
            totalSales = totalSales,
            totalDebtSales = totalDebtSales,
            totalCashSales = totalCashSales,
            totalPaymentsReceived = totalPaymentsReceived,
            fullSettlementAmount = fullSettlementAmount,
            partialSettlementAmount = partialSettlementAmount,
            netOutstandingBalance = netBalance,
            transactionCount = filteredTransactions.size,
            customerCount = if (selectedCustomer == null) allCustomers.size else null
        )

        return AnalyticsReportData(
            scope = scope,
            period = activePeriod,
            periodLabelAr = periodLabelAr,
            periodLabelEn = periodLabelEn,
            customStartDate = customStartDate,
            customEndDate = customEndDate,
            selectedChartType = selectedChartType.toAnalyticsChartType(),
            chartBreakdownType = selectedChartType,
            chartData = chartCategories,
            totalVolume = totalVolume,
            metrics = summaryMetrics,
            selectedCustomer = customerInfo,
            mostOrderedProducts = mostOrderedProducts,
            transactions = filteredTransactions,
            storeName = storeName.ifBlank { if (isArabic) StoreStrings.SAMPLE_STORE_NAME_AR else StoreStrings.SAMPLE_STORE_NAME_EN },
            currency = currency
        )
    }
}
