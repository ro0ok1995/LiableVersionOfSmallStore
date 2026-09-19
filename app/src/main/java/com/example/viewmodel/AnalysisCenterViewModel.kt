package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.TransactionItemLineEntity
import com.example.model.AnalyticsExportDataPreparer
import com.example.model.AnalyticsReportData
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.model.PeriodFilter
import com.example.model.TransactionItem
import com.example.ui.components.BreakdownChartType
import com.example.util.ReportPreviewRow
import com.example.util.StatementRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AnalysisTab {
    STATISTICS,
    ACCOUNT_STATEMENT,
    REPORTS
}

enum class StatementTxFilter {
    ALL,
    PAYMENT,
    CASH_PURCHASE,
    DEBT_PURCHASE
}

enum class ReportType {
    DEBT_BALANCES,
    SALES_AND_ITEMS,
    TRANSACTIONS,
    COMPREHENSIVE_CUSTOMER
}

data class TransactionAgingDetail(
    val transactionId: String,
    val date: String,
    val amount: Double,
    val daysOld: Long,
    val bucketLabel: String
)

data class CustomerDebtAgingResult(
    val customerId: String,
    val customerName: String,
    val phone: String,
    val currentBalance: Double,
    val currentDebt: Double,
    val bucket0To30: Double,
    val bucket31To60: Double,
    val bucket61To90: Double,
    val bucket90Plus: Double,
    val individualTransactions: List<TransactionAgingDetail>
) {
    fun formatAgingSummary(isArabic: Boolean): String {
        if (currentDebt <= 0.0) return if (isArabic) "مسدد بالكامل" else "Fully Settled"
        val parts = mutableListOf<String>()
        if (bucket0To30 > 0.0) parts.add("${if (isArabic) "0-30:" else "0-30d:"} ${String.format(Locale.US, "%.0f", bucket0To30)}")
        if (bucket31To60 > 0.0) parts.add("${if (isArabic) "31-60:" else "31-60d:"} ${String.format(Locale.US, "%.0f", bucket31To60)}")
        if (bucket61To90 > 0.0) parts.add("${if (isArabic) "61-90:" else "61-90d:"} ${String.format(Locale.US, "%.0f", bucket61To90)}")
        if (bucket90Plus > 0.0) parts.add("${if (isArabic) "+90:" else "90+d:"} ${String.format(Locale.US, "%.0f", bucket90Plus)}")
        return if (parts.isEmpty()) (if (isArabic) "لا توجد ديون" else "No debt") else parts.joinToString(" | ")
    }
}

data class StoreDebtAgingSummary(
    val totalOutstandingDebt: Double,
    val totalCustomersWithDebtCount: Int,
    val sum0To30: Double,
    val sum31To60: Double,
    val sum61To90: Double,
    val sum90Plus: Double,
    val customerAgings: List<CustomerDebtAgingResult>
)

object DebtAgingUtils {
    /**
     * Determines aging bucket label for a given number of days old.
     * Buckets: 0–30 days, 31–60 days, 61–90 days, 90+ days.
     */
    fun getBucketLabel(days: Long, isArabic: Boolean = false): String {
        return when {
            days in 0..30 -> if (isArabic) "0–30 يوم" else "0–30 Days"
            days in 31..60 -> if (isArabic) "31–60 يوم" else "31–60 Days"
            days in 61..90 -> if (isArabic) "61–90 يوم" else "61–90 Days"
            else -> if (isArabic) "أكثر من 90 يوم" else "90+ Days"
        }
    }

    /**
     * Computes the age in days between transaction date and evaluation date.
     * Uses real java.time.LocalDate.
     */
    fun computeDaysOld(dateStr: String, today: LocalDate = LocalDate.now()): Long {
        val txDate = DateFilterUtils.parseDate(dateStr) ?: return 0L
        return java.time.temporal.ChronoUnit.DAYS.between(txDate, today).coerceAtLeast(0L)
    }

    /**
     * Computes debt aging for a single customer based on their real individual debt/credit transactions
     * versus the current device date.
     * Example: a debt transaction dated 2026-08-01 evaluated on 2026-09-10 reports 40 days old, falling in 31-60 bucket.
     */
    fun calculateCustomerAging(
        customer: CustomerAccount,
        allTransactions: List<TransactionItem>,
        today: LocalDate = LocalDate.now(),
        isArabic: Boolean = false
    ): CustomerDebtAgingResult {
        val outstandingDebt = if (customer.totalDebt > 0) customer.totalDebt else customer.balance.coerceAtLeast(0.0)

        // Find customer's debt/credit transactions
        val customerDebtTxs = allTransactions.filter { tx ->
            tx.customerName.equals(customer.customerName, ignoreCase = true) &&
            (tx.isCredit || tx.activityType.contains("آجل") || tx.activityType.contains("دين") || tx.activityType.contains("Debt") || tx.activityType.contains("شراء بالدين"))
        }.sortedByDescending { it.date }

        val details = customerDebtTxs.map { tx ->
            val days = computeDaysOld(tx.date, today)
            TransactionAgingDetail(
                transactionId = tx.id,
                date = tx.date,
                amount = tx.amount,
                daysOld = days,
                bucketLabel = getBucketLabel(days, isArabic)
            )
        }

        if (outstandingDebt <= 0.0) {
            return CustomerDebtAgingResult(
                customerId = customer.id,
                customerName = customer.customerName,
                phone = customer.phone,
                currentBalance = customer.balance,
                currentDebt = 0.0,
                bucket0To30 = 0.0,
                bucket31To60 = 0.0,
                bucket61To90 = 0.0,
                bucket90Plus = 0.0,
                individualTransactions = details
            )
        }

        // Allocate outstanding debt to aging buckets (newest to oldest)
        var remainingDebt = outstandingDebt
        var b0To30 = 0.0
        var b31To60 = 0.0
        var b61To90 = 0.0
        var b90Plus = 0.0

        for (txDetail in details) {
            if (remainingDebt <= 0.0) break
            val alloc = minOf(remainingDebt, txDetail.amount)
            remainingDebt -= alloc
            when {
                txDetail.daysOld in 0..30 -> b0To30 += alloc
                txDetail.daysOld in 31..60 -> b31To60 += alloc
                txDetail.daysOld in 61..90 -> b61To90 += alloc
                else -> b90Plus += alloc
            }
        }

        // Any leftover debt not covered by recorded debt transactions (e.g. initial debt balance)
        // is placed in 90+ days bucket
        if (remainingDebt > 0.0) {
            b90Plus += remainingDebt
        }

        return CustomerDebtAgingResult(
            customerId = customer.id,
            customerName = customer.customerName,
            phone = customer.phone,
            currentBalance = customer.balance,
            currentDebt = outstandingDebt,
            bucket0To30 = b0To30,
            bucket31To60 = b31To60,
            bucket61To90 = b61To90,
            bucket90Plus = b90Plus,
            individualTransactions = details
        )
    }

    /**
     * Calculates the overall store-level debt aging summary across all customers and transactions.
     * Evaluates actual transaction dates against the current date.
     */
    fun calculateStoreDebtAgingSummary(
        customers: List<CustomerAccount>,
        allTransactions: List<TransactionItem>,
        today: LocalDate = LocalDate.now(),
        isArabic: Boolean = false
    ): StoreDebtAgingSummary {
        val customerAgings = customers.map { cust ->
            calculateCustomerAging(cust, allTransactions, today, isArabic)
        }.sortedByDescending { it.currentDebt }

        return StoreDebtAgingSummary(
            totalOutstandingDebt = customerAgings.sumOf { it.currentDebt },
            totalCustomersWithDebtCount = customerAgings.count { it.currentDebt > 0 },
            sum0To30 = customerAgings.sumOf { it.bucket0To30 },
            sum31To60 = customerAgings.sumOf { it.bucket31To60 },
            sum61To90 = customerAgings.sumOf { it.bucket61To90 },
            sum90Plus = customerAgings.sumOf { it.bucket90Plus },
            customerAgings = customerAgings
        )
    }
}

object DateFilterUtils {
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    /**
     * Parses a date string safely into a LocalDate.
     * Expects "yyyy-MM-dd" or formats starting with "yyyy-MM-dd".
     */
    fun parseDate(dateStr: String): LocalDate? {
        if (dateStr.isBlank()) return null
        return try {
            val clean = if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr
            LocalDate.parse(clean, DATE_FORMATTER)
        } catch (e: Exception) {
            try {
                LocalDate.parse(dateStr)
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Checks if a transaction date falls within the requested period filter.
     * Computes dynamically from current device date (LocalDate.now()):
     * - ALL: All available non-archived/current records.
     * - TODAY: Transaction date equals current calendar day.
     * - MONTH: Transaction date is within the current calendar month (txDate.year == today.year && txDate.month == today.month).
     * - CUSTOM: Transaction date is within customStartDate..customEndDate inclusive.
     */
    fun isDateInPeriod(
        dateStr: String,
        period: PeriodFilter,
        customStartDate: LocalDate? = null,
        customEndDate: LocalDate? = null,
        today: LocalDate = LocalDate.now()
    ): Boolean {
        if (period == PeriodFilter.ALL) return true
        val txDate = parseDate(dateStr) ?: return false
        return when (period) {
            PeriodFilter.ALL -> true
            PeriodFilter.TODAY -> txDate.isEqual(today)
            PeriodFilter.MONTH -> {
                txDate.year == today.year && txDate.month == today.month
            }
            PeriodFilter.CUSTOM -> {
                val startOk = customStartDate == null || !txDate.isBefore(customStartDate)
                val endOk = customEndDate == null || !txDate.isAfter(customEndDate)
                startOk && endOk
            }
        }
    }
}

data class AnalysisCenterUiState(
    val currentTab: AnalysisTab = AnalysisTab.STATISTICS,
    // Shared lockable period
    val isPeriodLocked: Boolean = true,
    val sharedPeriod: PeriodFilter = PeriodFilter.MONTH,
    val statisticsPeriod: PeriodFilter = PeriodFilter.MONTH,
    val statementPeriod: PeriodFilter = PeriodFilter.MONTH,
    val reportsPeriod: PeriodFilter = PeriodFilter.MONTH,

    // Custom date ranges (active and per-tab)
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    val sharedStartDate: LocalDate? = null,
    val sharedEndDate: LocalDate? = null,
    val statisticsStartDate: LocalDate? = null,
    val statisticsEndDate: LocalDate? = null,
    val statementStartDate: LocalDate? = null,
    val statementEndDate: LocalDate? = null,
    val reportsStartDate: LocalDate? = null,
    val reportsEndDate: LocalDate? = null,

    // UI state for custom date picker dialog
    val showCustomDatePicker: Boolean = false,

    // Shared Customer Context across Statistics and Statement, and conditional in Reports
    val selectedCustomer: CustomerAccount? = null, // null means "All customers"

    // Statement tab filters
    val statementFilter: StatementTxFilter = StatementTxFilter.ALL,

    // Statistics tab chart mode (circular/donut, bar/column, combined/combo)
    val selectedChartType: BreakdownChartType = BreakdownChartType.DONUT,

    // Reports tab state
    val selectedReportType: ReportType = ReportType.DEBT_BALANCES,
    val isExporting: Boolean = false,
    val exportSuccessMessage: String? = null
)

class AnalysisCenterViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisCenterUiState())
    val uiState: StateFlow<AnalysisCenterUiState> = _uiState.asStateFlow()

    fun selectChartType(chartType: BreakdownChartType) {
        _uiState.update { it.copy(selectedChartType = chartType) }
    }

    fun selectTab(tab: AnalysisTab) {
        _uiState.update { state ->
            if (state.isPeriodLocked) {
                state.copy(
                    currentTab = tab,
                    customStartDate = state.sharedStartDate,
                    customEndDate = state.sharedEndDate
                )
            } else {
                val (start, end) = when (tab) {
                    AnalysisTab.STATISTICS -> Pair(state.statisticsStartDate, state.statisticsEndDate)
                    AnalysisTab.ACCOUNT_STATEMENT -> Pair(state.statementStartDate, state.statementEndDate)
                    AnalysisTab.REPORTS -> Pair(state.reportsStartDate, state.reportsEndDate)
                }
                state.copy(
                    currentTab = tab,
                    customStartDate = start,
                    customEndDate = end
                )
            }
        }
    }

    fun togglePeriodLock() {
        _uiState.update { state ->
            val newLocked = !state.isPeriodLocked
            if (newLocked) {
                // When re-locking, synchronize current tab's period AND custom dates to sharedPeriod
                val (activePeriod, activeStart, activeEnd) = when (state.currentTab) {
                    AnalysisTab.STATISTICS -> Triple(state.statisticsPeriod, state.statisticsStartDate, state.statisticsEndDate)
                    AnalysisTab.ACCOUNT_STATEMENT -> Triple(state.statementPeriod, state.statementStartDate, state.statementEndDate)
                    AnalysisTab.REPORTS -> Triple(state.reportsPeriod, state.reportsStartDate, state.reportsEndDate)
                }
                state.copy(
                    isPeriodLocked = true,
                    sharedPeriod = activePeriod,
                    sharedStartDate = activeStart,
                    sharedEndDate = activeEnd,
                    statisticsPeriod = activePeriod,
                    statisticsStartDate = activeStart,
                    statisticsEndDate = activeEnd,
                    statementPeriod = activePeriod,
                    statementStartDate = activeStart,
                    statementEndDate = activeEnd,
                    reportsPeriod = activePeriod,
                    reportsStartDate = activeStart,
                    reportsEndDate = activeEnd,
                    customStartDate = activeStart,
                    customEndDate = activeEnd
                )
            } else {
                state.copy(isPeriodLocked = false)
            }
        }
    }

    fun selectPeriod(period: PeriodFilter) {
        _uiState.update { state ->
            val shouldOpenPicker = period == PeriodFilter.CUSTOM && (state.customStartDate == null || state.customEndDate == null)
            if (state.isPeriodLocked) {
                // Update all tabs synchronously
                state.copy(
                    sharedPeriod = period,
                    statisticsPeriod = period,
                    statementPeriod = period,
                    reportsPeriod = period,
                    showCustomDatePicker = if (period == PeriodFilter.CUSTOM) true else state.showCustomDatePicker
                )
            } else {
                // Update only current tab's period
                when (state.currentTab) {
                    AnalysisTab.STATISTICS -> state.copy(
                        statisticsPeriod = period,
                        showCustomDatePicker = if (period == PeriodFilter.CUSTOM) true else state.showCustomDatePicker
                    )
                    AnalysisTab.ACCOUNT_STATEMENT -> state.copy(
                        statementPeriod = period,
                        showCustomDatePicker = if (period == PeriodFilter.CUSTOM) true else state.showCustomDatePicker
                    )
                    AnalysisTab.REPORTS -> state.copy(
                        reportsPeriod = period,
                        showCustomDatePicker = if (period == PeriodFilter.CUSTOM) true else state.showCustomDatePicker
                    )
                }
            }
        }
    }

    fun setCustomDateRange(startDate: LocalDate?, endDate: LocalDate?) {
        _uiState.update { state ->
            if (state.isPeriodLocked) {
                state.copy(
                    sharedStartDate = startDate,
                    sharedEndDate = endDate,
                    statisticsStartDate = startDate,
                    statisticsEndDate = endDate,
                    statementStartDate = startDate,
                    statementEndDate = endDate,
                    reportsStartDate = startDate,
                    reportsEndDate = endDate,
                    customStartDate = startDate,
                    customEndDate = endDate,
                    sharedPeriod = PeriodFilter.CUSTOM,
                    statisticsPeriod = PeriodFilter.CUSTOM,
                    statementPeriod = PeriodFilter.CUSTOM,
                    reportsPeriod = PeriodFilter.CUSTOM,
                    showCustomDatePicker = false
                )
            } else {
                when (state.currentTab) {
                    AnalysisTab.STATISTICS -> state.copy(
                        statisticsStartDate = startDate,
                        statisticsEndDate = endDate,
                        statisticsPeriod = PeriodFilter.CUSTOM,
                        customStartDate = startDate,
                        customEndDate = endDate,
                        showCustomDatePicker = false
                    )
                    AnalysisTab.ACCOUNT_STATEMENT -> state.copy(
                        statementStartDate = startDate,
                        statementEndDate = endDate,
                        statementPeriod = PeriodFilter.CUSTOM,
                        customStartDate = startDate,
                        customEndDate = endDate,
                        showCustomDatePicker = false
                    )
                    AnalysisTab.REPORTS -> state.copy(
                        reportsStartDate = startDate,
                        reportsEndDate = endDate,
                        reportsPeriod = PeriodFilter.CUSTOM,
                        customStartDate = startDate,
                        customEndDate = endDate,
                        showCustomDatePicker = false
                    )
                }
            }
        }
    }

    fun openCustomDatePicker() {
        _uiState.update { it.copy(showCustomDatePicker = true) }
    }

    fun dismissCustomDatePicker() {
        _uiState.update { it.copy(showCustomDatePicker = false) }
    }

    fun selectCustomer(customer: CustomerAccount?) {
        _uiState.update { it.copy(selectedCustomer = customer) }
    }

    fun clearCustomer() {
        _uiState.update { it.copy(selectedCustomer = null) }
    }

    fun setStatementFilter(filter: StatementTxFilter) {
        _uiState.update { it.copy(statementFilter = filter) }
    }

    fun selectReportType(type: ReportType) {
        _uiState.update { it.copy(selectedReportType = type) }
    }

    fun setExportSuccessMessage(msg: String?) {
        _uiState.update { it.copy(exportSuccessMessage = msg) }
    }

    /**
     * Active period for whichever tab is currently displayed
     */
    fun getActivePeriod(): PeriodFilter {
        val state = _uiState.value
        return if (state.isPeriodLocked) {
            state.sharedPeriod
        } else {
            when (state.currentTab) {
                AnalysisTab.STATISTICS -> state.statisticsPeriod
                AnalysisTab.ACCOUNT_STATEMENT -> state.statementPeriod
                AnalysisTab.REPORTS -> state.reportsPeriod
            }
        }
    }

    /**
     * Active custom date range for whichever tab is currently displayed
     */
    fun getActiveDateRange(): Pair<LocalDate?, LocalDate?> {
        val state = _uiState.value
        return if (state.isPeriodLocked) {
            Pair(state.sharedStartDate, state.sharedEndDate)
        } else {
            when (state.currentTab) {
                AnalysisTab.STATISTICS -> Pair(state.statisticsStartDate, state.statisticsEndDate)
                AnalysisTab.ACCOUNT_STATEMENT -> Pair(state.statementStartDate, state.statementEndDate)
                AnalysisTab.REPORTS -> Pair(state.reportsStartDate, state.reportsEndDate)
            }
        }
    }

    /**
     * Helper to compute filtered statement rows with running balances.
     */
    fun computeStatementRows(
        allTransactions: List<TransactionItem>,
        selectedCustomer: CustomerAccount?,
        filter: StatementTxFilter,
        period: PeriodFilter,
        customStartDate: LocalDate? = null,
        customEndDate: LocalDate? = null,
        today: LocalDate = LocalDate.now()
    ): List<StatementRow> {
        // 1. Filter by customer
        var txList = if (selectedCustomer != null) {
            allTransactions.filter { it.customerName.equals(selectedCustomer.customerName, ignoreCase = true) }
        } else {
            allTransactions
        }

        // 2. Filter by period using real date math
        txList = txList.filter { tx ->
            DateFilterUtils.isDateInPeriod(
                dateStr = tx.date,
                period = period,
                customStartDate = customStartDate,
                customEndDate = customEndDate,
                today = today
            )
        }

        // 3. Filter by transaction type
        txList = when (filter) {
            StatementTxFilter.ALL -> txList
            StatementTxFilter.PAYMENT -> txList.filter { it.activityType.contains("تسديد") || it.activityType.contains("Payment") }
            StatementTxFilter.CASH_PURCHASE -> txList.filter { !it.isCredit && (it.activityType.contains("كاش") || it.activityType.contains("Cash")) }
            StatementTxFilter.DEBT_PURCHASE -> txList.filter { it.isCredit || it.activityType.contains("آجل") || it.activityType.contains("دين") || it.activityType.contains("Debt") }
        }

        // 4. Build statement rows with running balance calculation
        var running = 0.0
        return txList.map { tx ->
            val isPayment = tx.activityType.contains("تسديد") || tx.activityType.contains("Payment")
            val isDebtPurchase = tx.isCredit || tx.activityType.contains("آجل") || tx.activityType.contains("دين")
            if (isPayment) {
                running -= tx.amount
            } else if (isDebtPurchase) {
                running += tx.amount
            }
            StatementRow(
                id = tx.id,
                date = tx.date,
                customerName = tx.customerName,
                description = if (tx.notes.isNotBlank()) tx.notes else tx.activityType,
                type = tx.activityType,
                isPayment = isPayment,
                isCreditDebt = isDebtPurchase,
                amount = tx.amount,
                runningBalance = running
            )
        }
    }

    /**
     * Prepares structured Analytics export data reflecting the current state, active period,
     * selected customer scope (ALL_CUSTOMERS or ONE_SELECTED_CUSTOMER), and selected chart mode.
     */
    fun getAnalyticsExportData(
        transactions: List<TransactionItem>,
        transactionLines: List<TransactionItemLineEntity> = emptyList(),
        allCustomers: List<CustomerAccount> = emptyList(),
        storeName: String = "",
        currency: String = AppCurrency.SYMBOL,
        isArabic: Boolean = true,
        today: LocalDate = LocalDate.now()
    ): AnalyticsReportData {
        val state = _uiState.value
        val (start, end) = getActiveDateRange()
        return AnalyticsExportDataPreparer.prepareAnalyticsData(
            transactions = transactions,
            transactionLines = transactionLines,
            allCustomers = allCustomers,
            selectedCustomer = state.selectedCustomer,
            activePeriod = getActivePeriod(),
            customStartDate = start,
            customEndDate = end,
            selectedChartType = state.selectedChartType,
            storeName = storeName,
            currency = currency,
            isArabic = isArabic,
            today = today
        )
    }
}
