package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.model.LanguageMode
import com.example.model.PeriodFilter
import com.example.model.ProductItem
import com.example.model.SettlementType
import com.example.model.StoreInfo
import com.example.model.StoreStrings
import com.example.model.TransactionItem
import com.example.data.db.TransactionItemLineEntity
import com.example.ui.components.BreakdownChartItem
import com.example.ui.components.BreakdownChartTabButton
import com.example.ui.components.BreakdownChartType
import com.example.ui.components.BreakdownColumnChart
import com.example.ui.components.BreakdownComboChart
import com.example.ui.components.CustomerSelectorField
import com.example.ui.components.SimpleEmptyState
import com.example.ui.theme.GeoOutline
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusRedBg
import com.example.util.ReportExporter
import com.example.util.ReportPreviewRow
import com.example.util.StatementRow
import com.example.viewmodel.AnalysisCenterUiState
import com.example.viewmodel.AnalysisCenterViewModel
import com.example.viewmodel.AnalysisTab
import com.example.viewmodel.CustomerDebtAgingResult
import com.example.viewmodel.DateFilterUtils
import com.example.viewmodel.DebtAgingUtils
import com.example.viewmodel.ReportType
import com.example.viewmodel.StatementTxFilter
import com.example.viewmodel.TransactionAgingDetail
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AnalysisCenterScreen(
    viewModel: AnalysisCenterViewModel,
    customers: List<CustomerAccount>,
    transactions: List<TransactionItem>,
    storeInfo: StoreInfo,
    products: List<ProductItem> = emptyList(),
    transactionLines: List<TransactionItemLineEntity> = emptyList(),
    languageMode: LanguageMode,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isArabic = languageMode == LanguageMode.ARABIC
    val currency = AppCurrency.SYMBOL
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Calculate active period based on lock state
    val activePeriod = viewModel.getActivePeriod()
    val (activeStartDate, activeEndDate) = viewModel.getActiveDateRange()

    // Period selector is available across all tabs to filter real data dynamically
    val shouldShowPeriodSelector = true

    // Customer selector: shown on Statistics and Account Statement header.
    // For Reports, it is rendered BELOW the 4 report tiles inside the Reports tab only when Comprehensive Customer is selected.
    val shouldShowCustomerSelector = when (uiState.currentTab) {
        AnalysisTab.STATISTICS, AnalysisTab.ACCOUNT_STATEMENT -> true
        AnalysisTab.REPORTS -> false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("analysis_center_screen")
    ) {
        // 1. TOP TAB ROW (Statistics | Account Statement | Reports)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = uiState.currentTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = GeoPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[uiState.currentTab.ordinal]),
                            height = 3.dp,
                            color = GeoPrimary
                        )
                    },
                    divider = {
                        HorizontalDivider(color = GeoOutlineVariant)
                    },
                    modifier = Modifier.testTag("analysis_tab_row")
                ) {
                    Tab(
                        selected = uiState.currentTab == AnalysisTab.STATISTICS,
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.selectTab(AnalysisTab.STATISTICS)
                        },
                        text = {
                            Text(
                                text = if (isArabic) StoreStrings.TAB_STATISTICS_AR else StoreStrings.TAB_STATISTICS_EN,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (uiState.currentTab == AnalysisTab.STATISTICS) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            )
                        },
                        modifier = Modifier.testTag("tab_statistics")
                    )
                    Tab(
                        selected = uiState.currentTab == AnalysisTab.ACCOUNT_STATEMENT,
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.selectTab(AnalysisTab.ACCOUNT_STATEMENT)
                        },
                        text = {
                            Text(
                                text = if (isArabic) StoreStrings.TAB_STATEMENT_AR else StoreStrings.TAB_STATEMENT_EN,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (uiState.currentTab == AnalysisTab.ACCOUNT_STATEMENT) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            )
                        },
                        modifier = Modifier.testTag("tab_statement")
                    )
                    Tab(
                        selected = uiState.currentTab == AnalysisTab.REPORTS,
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.selectTab(AnalysisTab.REPORTS)
                        },
                        text = {
                            Text(
                                text = if (isArabic) StoreStrings.TAB_REPORTS_AR else StoreStrings.TAB_REPORTS_EN,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (uiState.currentTab == AnalysisTab.REPORTS) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            )
                        },
                        modifier = Modifier.testTag("tab_reports")
                    )
                }

                // 2. SHARED, LOCKABLE PERIOD SELECTOR (Progressive disclosure)
                if (shouldShowPeriodSelector) {
                    PeriodSelectorLockableRow(
                        selectedPeriod = activePeriod,
                        customStartDate = activeStartDate,
                        customEndDate = activeEndDate,
                        isLocked = uiState.isPeriodLocked,
                        isArabic = isArabic,
                        onSelectPeriod = { viewModel.selectPeriod(it) },
                        onOpenDatePicker = { viewModel.openCustomDatePicker() },
                        onToggleLock = { viewModel.togglePeriodLock() }
                    )
                }

                // 3. SHARED CUSTOMER CONTEXT SELECTOR (Progressive disclosure)
                if (shouldShowCustomerSelector) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        CustomerSelectorField(
                            customers = customers,
                            selectedCustomer = uiState.selectedCustomer,
                            onCustomerSelected = { cust ->
                                focusManager.clearFocus()
                                if (cust != null) {
                                    viewModel.selectCustomer(cust)
                                } else {
                                    viewModel.clearCustomer()
                                }
                            },
                            isArabic = isArabic,
                            isRequired = uiState.currentTab == AnalysisTab.REPORTS && uiState.selectedReportType == ReportType.COMPREHENSIVE_CUSTOMER,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_context_bar"),
                            testTag = "analysis_customer_selector"
                        )
                    }
                }
            }
        }

        // 4. ACTIVE TAB CONTENT
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (uiState.currentTab) {
                AnalysisTab.STATISTICS -> {
                    StatisticsTabContent(
                        transactions = transactions,
                        selectedCustomer = uiState.selectedCustomer,
                        activePeriod = activePeriod,
                        customStartDate = activeStartDate,
                        customEndDate = activeEndDate,
                        currency = currency,
                        isArabic = isArabic,
                        context = context,
                        storeInfo = storeInfo
                    )
                }
                AnalysisTab.ACCOUNT_STATEMENT -> {
                    AccountStatementTabContent(
                        viewModel = viewModel,
                        uiState = uiState,
                        customers = customers,
                        allTransactions = transactions,
                        activePeriod = activePeriod,
                        customStartDate = activeStartDate,
                        customEndDate = activeEndDate,
                        currency = currency,
                        isArabic = isArabic
                    )
                }
                AnalysisTab.REPORTS -> {
                    ReportsTabContent(
                        viewModel = viewModel,
                        uiState = uiState,
                        customers = customers,
                        allTransactions = transactions,
                        products = products,
                        transactionLines = transactionLines,
                        activePeriod = activePeriod,
                        customStartDate = activeStartDate,
                        customEndDate = activeEndDate,
                        currency = currency,
                        isArabic = isArabic,
                        context = context,
                        storeInfo = storeInfo
                    )
                }
            }
        }
    }

    // 5. CUSTOM DATE RANGE PICKER DIALOG
    if (uiState.showCustomDatePicker) {
        CustomDateRangePickerDialog(
            initialStartDate = activeStartDate,
            initialEndDate = activeEndDate,
            isArabic = isArabic,
            onConfirm = { start, end ->
                viewModel.setCustomDateRange(start, end)
            },
            onDismiss = {
                viewModel.dismissCustomDatePicker()
            }
        )
    }
}

// -------------------------------------------------------------
// PERIOD SELECTOR WITH LOCK/UNLOCK TOGGLE & CUSTOM RANGE
// -------------------------------------------------------------
@Composable
private fun PeriodSelectorLockableRow(
    selectedPeriod: PeriodFilter,
    customStartDate: LocalDate?,
    customEndDate: LocalDate?,
    isLocked: Boolean,
    isArabic: Boolean,
    onSelectPeriod: (PeriodFilter) -> Unit,
    onOpenDatePicker: () -> Unit,
    onToggleLock: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("period_selector_container")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Period Chips: All -> Today -> Month -> Custom
                PeriodSelectorChip(
                    label = if (isArabic) StoreStrings.PERIOD_ALL_AR else StoreStrings.PERIOD_ALL_EN,
                    isSelected = selectedPeriod == PeriodFilter.ALL,
                    testTag = "period_chip_all",
                    onClick = { onSelectPeriod(PeriodFilter.ALL) },
                    modifier = Modifier.weight(1f)
                )
                PeriodSelectorChip(
                    label = if (isArabic) StoreStrings.PERIOD_TODAY_AR else StoreStrings.PERIOD_TODAY_EN,
                    isSelected = selectedPeriod == PeriodFilter.TODAY,
                    testTag = "period_chip_today",
                    onClick = { onSelectPeriod(PeriodFilter.TODAY) },
                    modifier = Modifier.weight(1f)
                )
                PeriodSelectorChip(
                    label = if (isArabic) StoreStrings.PERIOD_MONTH_AR else StoreStrings.PERIOD_MONTH_EN,
                    isSelected = selectedPeriod == PeriodFilter.MONTH,
                    testTag = "period_chip_month",
                    onClick = { onSelectPeriod(PeriodFilter.MONTH) },
                    modifier = Modifier.weight(1f)
                )
                PeriodSelectorChip(
                    label = if (isArabic) StoreStrings.PERIOD_CUSTOM_AR else StoreStrings.PERIOD_CUSTOM_EN,
                    isSelected = selectedPeriod == PeriodFilter.CUSTOM,
                    testTag = "period_chip_custom",
                    onClick = { onSelectPeriod(PeriodFilter.CUSTOM) },
                    modifier = Modifier.weight(1f)
                )

                // Lock / Unlock Toggle Button
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isLocked) GeoPrimary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("period_lock_toggle")
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isLocked) {
                            if (isArabic) "الفترة مقفلة وموحدة عبر التبويبات" else "Locked: Period is shared across tabs"
                        } else {
                            if (isArabic) "الفترة غير مقفلة ومستقلة لكل تبويب" else "Unlocked: Period is independent per tab"
                        },
                        tint = if (isLocked) GeoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // If CUSTOM is selected, show the date range badge with edit button
            if (selectedPeriod == PeriodFilter.CUSTOM) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GeoOutlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenDatePicker() }
                        .testTag("custom_date_range_display")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = GeoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val rangeText = if (customStartDate != null && customEndDate != null) {
                                "$customStartDate  ←  $customEndDate"
                            } else if (customStartDate != null) {
                                if (isArabic) "من $customStartDate" else "From $customStartDate"
                            } else if (customEndDate != null) {
                                if (isArabic) "إلى $customEndDate" else "To $customEndDate"
                            } else {
                                if (isArabic) "اضغط لتحديد نطاق التاريخ" else "Tap to select date range"
                            }
                            Text(
                                text = rangeText,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("custom_date_range_text")
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = if (isArabic) "تعديل التاريخ" else "Edit Date Range",
                            tint = GeoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) GeoPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) null else BorderStroke(1.dp, GeoOutlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDatePickerModal(
    initialDate: LocalDate?,
    title: String,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMillis = remember(initialDate) {
        (initialDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateSelected(selected)
                    }
                    onDismiss()
                }
            ) {
                Text(text = "تأكيد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "إلغاء")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
fun CustomDateRangePickerDialog(
    initialStartDate: LocalDate?,
    initialEndDate: LocalDate?,
    isArabic: Boolean,
    onConfirm: (LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember { LocalDate.now() }
    var startDate by remember(initialStartDate) { mutableStateOf(initialStartDate ?: today.minusDays(30)) }
    var endDate by remember(initialEndDate) { mutableStateOf(initialEndDate ?: today) }

    var isSelectingStartDate by remember { mutableStateOf(false) }
    var isSelectingEndDate by remember { mutableStateOf(false) }

    val isDateOrderInvalid = startDate.isAfter(endDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("custom_date_range_picker_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = GeoPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isArabic) "تحديد نطاق التاريخ المخصص" else "Custom Date Range",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isArabic) "حدد تاريخ البداية والنهاية لتصفية المعاملات بدقة."
                    else "Choose start and end dates to filter transactions precisely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Date Selection Cards (From & To)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Start Date Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GeoOutlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isSelectingStartDate = true }
                            .testTag("custom_start_date_card")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isArabic) "من تاريخ" else "From Date",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = GeoPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = startDate.toString(),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.testTag("custom_start_date_text")
                                )
                            }
                        }
                    }

                    // End Date Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GeoOutlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isSelectingEndDate = true }
                            .testTag("custom_end_date_card")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isArabic) "إلى تاريخ" else "To Date",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = GeoPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = endDate.toString(),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.testTag("custom_end_date_text")
                                )
                            }
                        }
                    }
                }

                // Error indicator if start > end
                if (isDateOrderInvalid) {
                    Text(
                        text = if (isArabic) "تاريخ البداية لا يمكن أن يكون بعد تاريخ النهاية"
                        else "Start date cannot be after end date",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("custom_date_order_error")
                    )
                }

                // Quick presets
                Text(
                    text = if (isArabic) "اختصارات سريعة" else "Quick Presets",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetChip(
                        label = if (isArabic) "٧ أيام" else "7 Days",
                        isSelected = startDate == today.minusDays(6) && endDate == today,
                        onClick = {
                            startDate = today.minusDays(6)
                            endDate = today
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PresetChip(
                        label = if (isArabic) "١٤ يوم" else "14 Days",
                        isSelected = startDate == today.minusDays(13) && endDate == today,
                        onClick = {
                            startDate = today.minusDays(13)
                            endDate = today
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PresetChip(
                        label = if (isArabic) "٣٠ يوم" else "30 Days",
                        isSelected = startDate == today.minusDays(29) && endDate == today,
                        onClick = {
                            startDate = today.minusDays(29)
                            endDate = today
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PresetChip(
                        label = if (isArabic) "هذا الشهر" else "This Month",
                        isSelected = startDate == today.withDayOfMonth(1) && endDate == today,
                        onClick = {
                            startDate = today.withDayOfMonth(1)
                            endDate = today
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(startDate, endDate)
                },
                enabled = !isDateOrderInvalid,
                colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("custom_date_range_confirm_button")
            ) {
                Text(if (isArabic) "تطبيق" else "Apply")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("custom_date_range_dismiss_button")
            ) {
                Text(if (isArabic) "إلغاء" else "Cancel")
            }
        }
    )

    if (isSelectingStartDate) {
        SingleDatePickerModal(
            initialDate = startDate,
            title = if (isArabic) "اختر تاريخ البداية" else "Select Start Date",
            onDateSelected = { picked -> startDate = picked },
            onDismiss = { isSelectingStartDate = false }
        )
    }

    if (isSelectingEndDate) {
        SingleDatePickerModal(
            initialDate = endDate,
            title = if (isArabic) "اختر تاريخ النهاية" else "Select End Date",
            onDateSelected = { picked -> endDate = picked },
            onDismiss = { isSelectingEndDate = false }
        )
    }
}

@Composable
private fun PeriodSelectorChip(
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) GeoPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) null else BorderStroke(1.dp, GeoOutlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// -------------------------------------------------------------
// TAB 1: STATISTICS
// -------------------------------------------------------------
@Composable
private fun StatisticsTabContent(
    transactions: List<TransactionItem>,
    selectedCustomer: CustomerAccount?,
    activePeriod: PeriodFilter,
    customStartDate: LocalDate?,
    customEndDate: LocalDate?,
    currency: String,
    isArabic: Boolean,
    context: Context,
    storeInfo: StoreInfo
) {
    val filteredTransactions = remember(transactions, selectedCustomer, activePeriod, customStartDate, customEndDate) {
        var list = if (selectedCustomer != null) {
            transactions.filter { it.customerName.equals(selectedCustomer.customerName, ignoreCase = true) }
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

    val totalCashSales = remember(filteredTransactions) {
        filteredTransactions
            .filter { !it.isCredit && (it.activityType.contains("كاش") || it.activityType.contains("Cash") || (!it.activityType.contains("تسديد") && !it.activityType.contains("Payment"))) }
            .sumOf { it.amount }
    }
    val totalDebtSales = remember(filteredTransactions) {
        filteredTransactions
            .filter { it.isCredit || it.activityType.contains("آجل") || it.activityType.contains("دين") }
            .sumOf { it.amount }
    }
    val fullSettlementAmount = remember(filteredTransactions) {
        filteredTransactions
            .filter { (it.activityType.contains("تسديد") || it.activityType.contains("Payment")) && (it.settlementType == SettlementType.FULL || it.settlementType == null) }
            .sumOf { it.amount }
    }
    val partialSettlementAmount = remember(filteredTransactions) {
        filteredTransactions
            .filter { (it.activityType.contains("تسديد") || it.activityType.contains("Payment")) && it.settlementType == SettlementType.PARTIAL }
            .sumOf { it.amount }
    }
    val totalPaymentsReceived = fullSettlementAmount + partialSettlementAmount
    val totalSales = totalCashSales + totalDebtSales
    val netBalance = totalDebtSales - totalPaymentsReceived

    // Donut percentages calculation matching HomeScreen.kt
    val totalVolume = totalDebtSales + totalCashSales + fullSettlementAmount + partialSettlementAmount
    val debtPercent = if (totalVolume > 0) ((totalDebtSales / totalVolume) * 100).roundToInt() else 0
    val cashPercent = if (totalVolume > 0) ((totalCashSales / totalVolume) * 100).roundToInt() else 0
    val fullPercent = if (totalVolume > 0) ((fullSettlementAmount / totalVolume) * 100).roundToInt() else 0
    val partialPercent = if (totalVolume > 0) (100 - debtPercent - cashPercent - fullPercent).coerceAtLeast(0) else 0

    val ringSegments = remember(debtPercent, cashPercent, fullPercent, partialPercent) {
        listOf(
            DonutSegment(debtPercent.toFloat(), StatusRed),
            DonutSegment(cashPercent.toFloat(), StatusBlue),
            DonutSegment(fullPercent.toFloat(), StatusGreen),
            DonutSegment(partialPercent.toFloat(), StatusAmber)
        )
    }

    var selectedChartType by remember { mutableStateOf(BreakdownChartType.DONUT) }

    val breakdownChartItems = remember(
        totalDebtSales, totalCashSales, fullSettlementAmount, partialSettlementAmount,
        debtPercent, cashPercent, fullPercent, partialPercent
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
                        onClick = { selectedChartType = BreakdownChartType.DONUT },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chart_tab_donut")
                    )
                    BreakdownChartTabButton(
                        title = if (isArabic) StoreStrings.CHART_TAB_COLUMN_AR else StoreStrings.CHART_TAB_COLUMN_EN,
                        selected = selectedChartType == BreakdownChartType.COLUMN,
                        onClick = { selectedChartType = BreakdownChartType.COLUMN },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chart_tab_column")
                    )
                    BreakdownChartTabButton(
                        title = if (isArabic) StoreStrings.CHART_TAB_COMBO_AR else StoreStrings.CHART_TAB_COMBO_EN,
                        selected = selectedChartType == BreakdownChartType.COMBO,
                        onClick = { selectedChartType = BreakdownChartType.COMBO },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chart_tab_combo")
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

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
                            val headers = if (isArabic) listOf("البند", "القيمة", "النسبة", "العملة") else listOf("Metric", "Amount", "Percentage", "Currency")
                            val rows = listOf(
                                ReportPreviewRow(if (isArabic) "مبيعات الآجل" else "Debt Sales", String.format(Locale.US, "%.2f", totalDebtSales), "$debtPercent%", currency),
                                ReportPreviewRow(if (isArabic) "المبيعات النقدية" else "Cash Sales", String.format(Locale.US, "%.2f", totalCashSales), "$cashPercent%", currency),
                                ReportPreviewRow(if (isArabic) "تسديد كامل" else "Full Payment", String.format(Locale.US, "%.2f", fullSettlementAmount), "$fullPercent%", currency),
                                ReportPreviewRow(if (isArabic) "تسديد جزئي" else "Partial Payment", String.format(Locale.US, "%.2f", partialSettlementAmount), "$partialPercent%", currency)
                            )
                            val kpis = listOf(
                                Pair(if (isArabic) "إجمالي المبيعات" else "Total Sales", String.format(Locale.US, "%.2f %s", totalSales, currency)),
                                Pair(if (isArabic) "المتحصلات" else "Payments", String.format(Locale.US, "%.2f %s", totalPaymentsReceived, currency)),
                                Pair(if (isArabic) "الصافي" else "Net Balance", String.format(Locale.US, "%.2f %s", netBalance, currency))
                            )
                            val title = if (isArabic) "تقرير إحصائيات المبيعات والديون" else "Sales & Debt Statistics Report"
                            val subtitle = selectedCustomer?.customerName ?: (if (isArabic) "كافة العملاء" else "All Customers")
                            val file = ReportExporter.createCachedPdf(
                                context = context,
                                fileName = "Stats_${System.currentTimeMillis()}.pdf",
                                title = title,
                                storeName = storeInfo.storeName,
                                subtitle = subtitle,
                                kpis = kpis,
                                headers = headers,
                                rows = rows,
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
                            val headers = if (isArabic) listOf("البند", "القيمة", "النسبة", "العملة") else listOf("Metric", "Amount", "Percentage", "Currency")
                            val rows = listOf(
                                ReportPreviewRow(if (isArabic) "مبيعات الآجل" else "Debt Sales", String.format(Locale.US, "%.2f", totalDebtSales), "$debtPercent%", currency),
                                ReportPreviewRow(if (isArabic) "المبيعات النقدية" else "Cash Sales", String.format(Locale.US, "%.2f", totalCashSales), "$cashPercent%", currency),
                                ReportPreviewRow(if (isArabic) "تسديد كامل" else "Full Payment", String.format(Locale.US, "%.2f", fullSettlementAmount), "$fullPercent%", currency),
                                ReportPreviewRow(if (isArabic) "تسديد جزئي" else "Partial Payment", String.format(Locale.US, "%.2f", partialSettlementAmount), "$partialPercent%", currency)
                            )
                            val csv = ReportExporter.generateReportCsv(headers, rows)
                            val file = ReportExporter.createCachedCsv(context, "Stats_${System.currentTimeMillis()}.csv", csv)
                            ReportExporter.shareFile(context, file, "text/csv", "Statistics CSV")
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
                            val scope = selectedCustomer?.customerName ?: (if (isArabic) "كافة العملاء" else "All Customers")
                            val shareText = buildString {
                                appendLine("SmallStore - ${storeInfo.storeName}")
                                appendLine(if (isArabic) "تقرير إحصائيات: $scope" else "Statistics Summary: $scope")
                                appendLine(if (isArabic) "إجمالي المبيعات: %,.2f %s".format(Locale.US, totalSales, currency) else "Total Sales: %,.2f %s".format(Locale.US, totalSales, currency))
                                appendLine(if (isArabic) "مبيعات الآجل: %,.2f %s (%d%%)".format(Locale.US, totalDebtSales, currency, debtPercent) else "Debt Sales: %,.2f %s (%d%%)".format(Locale.US, totalDebtSales, currency, debtPercent))
                                appendLine(if (isArabic) "مبيعات كاش: %,.2f %s (%d%%)".format(Locale.US, totalCashSales, currency, cashPercent) else "Cash Sales: %,.2f %s (%d%%)".format(Locale.US, totalCashSales, currency, cashPercent))
                                appendLine(if (isArabic) "تسديد كامل: %,.2f %s (%d%%)".format(Locale.US, fullSettlementAmount, currency, fullPercent) else "Full Payments: %,.2f %s (%d%%)".format(Locale.US, fullSettlementAmount, currency, fullPercent))
                                appendLine(if (isArabic) "تسديد جزئي: %,.2f %s (%d%%)".format(Locale.US, partialSettlementAmount, currency, partialPercent) else "Partial Payments: %,.2f %s (%d%%)".format(Locale.US, partialSettlementAmount, currency, partialPercent))
                                appendLine(if (isArabic) "صافي الديون: %,.2f %s".format(Locale.US, netBalance, currency) else "Net Outstanding: %,.2f %s".format(Locale.US, netBalance, currency))
                            }
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share Statistics"))
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
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GeoOutlineVariant),
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                ),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// TAB 2: ACCOUNT STATEMENT
// -------------------------------------------------------------
@Composable
private fun AccountStatementTabContent(
    viewModel: AnalysisCenterViewModel,
    uiState: AnalysisCenterUiState,
    customers: List<CustomerAccount>,
    allTransactions: List<TransactionItem>,
    activePeriod: PeriodFilter,
    customStartDate: LocalDate?,
    customEndDate: LocalDate?,
    currency: String,
    isArabic: Boolean
) {
    val rows = remember(
        allTransactions,
        uiState.selectedCustomer,
        uiState.statementFilter,
        activePeriod,
        customStartDate,
        customEndDate
    ) {
        viewModel.computeStatementRows(
            allTransactions = allTransactions,
            selectedCustomer = uiState.selectedCustomer,
            filter = uiState.statementFilter,
            period = activePeriod,
            customStartDate = customStartDate,
            customEndDate = customEndDate
        )
    }

    // Scoped transactions reflecting CURRENT statement scope (selected customer or shop-wide), filtered by active period
    val scopedTransactions = remember(allTransactions, uiState.selectedCustomer, activePeriod, customStartDate, customEndDate) {
        val custFiltered = if (uiState.selectedCustomer != null) {
            allTransactions.filter { it.customerName.equals(uiState.selectedCustomer.customerName, ignoreCase = true) }
        } else {
            allTransactions
        }
        custFiltered.filter { tx ->
            DateFilterUtils.isDateInPeriod(
                dateStr = tx.date,
                period = activePeriod,
                customStartDate = customStartDate,
                customEndDate = customEndDate
            )
        }
    }

    // Cash Sales for the period/customer in scope
    val periodCashSales = remember(scopedTransactions) {
        scopedTransactions
            .filter { !it.isCredit && (it.activityType.contains("كاش") || it.activityType.contains("Cash") || (!it.activityType.contains("تسديد") && !it.activityType.contains("Payment") && !it.activityType.contains("آجل") && !it.activityType.contains("دين") && !it.activityType.contains("Debt"))) }
            .sumOf { it.amount }
    }

    // Credit (Debt) Sales for the period/customer in scope
    val periodDebtSales = remember(scopedTransactions) {
        scopedTransactions
            .filter { it.isCredit || it.activityType.contains("آجل") || it.activityType.contains("دين") || it.activityType.contains("Debt") }
            .sumOf { it.amount }
    }

    // CRITICAL: Total Sales = Cash Sales + Credit Sales (Debt Sales) for the period/customer in scope (NOT Debt + Payments)
    val periodTotalSales = periodCashSales + periodDebtSales

    // Payments received toward debt reduction for the period/customer in scope
    val periodPayments = remember(scopedTransactions) {
        scopedTransactions
            .filter { it.activityType.contains("تسديد") || it.activityType.contains("Payment") }
            .sumOf { it.amount }
    }

    // Debt box = current outstanding balance right now (what is currently owed, not a period-summed figure)
    val currentDebtOwed = remember(uiState.selectedCustomer, customers) {
        if (uiState.selectedCustomer != null) {
            val liveCust = customers.find { it.id == uiState.selectedCustomer.id } ?: uiState.selectedCustomer
            if (liveCust.totalDebt > 0) liveCust.totalDebt else liveCust.balance.coerceAtLeast(0.0)
        } else {
            customers.sumOf { if (it.totalDebt > 0) it.totalDebt else it.balance.coerceAtLeast(0.0) }
        }
    }

    // Totals for table list bottom row
    val totalOut = rows.filter { !it.isPayment }.sumOf { it.amount }
    val totalIn = rows.filter { it.isPayment }.sumOf { it.amount }
    val netBalance = totalOut - totalIn

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("account_statement_tab_content")
    ) {
        // 2x2 GRID OF FOUR SUMMARY BOXES (Directly below customer selector)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("statement_summary_grid"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1 (larger, more prominent boxes): "Total" and "Debt"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatementSummaryCard(
                    title = if (isArabic) "المجموع الكلي" else "Total Sales",
                    subtitle = if (isArabic) "(مبيعات كاش + آجل)" else "(Cash + Debt Sales)",
                    value = String.format(Locale.US, "%,.2f %s", periodTotalSales, currency),
                    color = GeoPrimary,
                    isLarge = true,
                    testTag = "statement_summary_total",
                    modifier = Modifier.weight(1f)
                )
                StatementSummaryCard(
                    title = if (isArabic) "الدين المستحق" else "Debt Due",
                    subtitle = if (uiState.selectedCustomer != null) {
                        if (isArabic) "(الرصيد القائم بذمته)" else "(Current Balance Due)"
                    } else {
                        if (isArabic) "(إجمالي الديون القائمة)" else "(Total Shop Debt Due)"
                    },
                    value = String.format(Locale.US, "%,.2f %s", currentDebtOwed, currency),
                    color = StatusRed,
                    isLarge = true,
                    testTag = "statement_summary_debt",
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2 (smaller boxes): "Cash" and "Payments"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatementSummaryCard(
                    title = if (isArabic) "الكاش" else "Cash Sales",
                    subtitle = if (isArabic) "(مبيعات نقدية للفترة)" else "(Period Cash Sales)",
                    value = String.format(Locale.US, "%,.2f %s", periodCashSales, currency),
                    color = StatusBlue,
                    isLarge = false,
                    testTag = "statement_summary_cash",
                    modifier = Modifier.weight(1f)
                )
                StatementSummaryCard(
                    title = if (isArabic) "الدفعات" else "Payments",
                    subtitle = if (isArabic) "(سداد ديون الفترة)" else "(Debt Payments Made)",
                    value = String.format(Locale.US, "%,.2f %s", periodPayments, currency),
                    color = StatusGreen,
                    isLarge = false,
                    testTag = "statement_summary_payments",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider(color = GeoOutlineVariant)

        // FILTERS & SEARCH (View-only, no export controls)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Transaction type filter chips: All / Payment / Cash purchase / Debt purchase
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("statement_filter_chips_row"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatementFilterChip(
                    label = if (isArabic) StoreStrings.TX_FILTER_ALL_AR else StoreStrings.TX_FILTER_ALL_EN,
                    isSelected = uiState.statementFilter == StatementTxFilter.ALL,
                    testTag = "filter_chip_all",
                    onClick = { viewModel.setStatementFilter(StatementTxFilter.ALL) },
                    modifier = Modifier.weight(1f)
                )
                StatementFilterChip(
                    label = if (isArabic) StoreStrings.TX_FILTER_PAYMENT_AR else StoreStrings.TX_FILTER_PAYMENT_EN,
                    isSelected = uiState.statementFilter == StatementTxFilter.PAYMENT,
                    testTag = "filter_chip_payment",
                    onClick = { viewModel.setStatementFilter(StatementTxFilter.PAYMENT) },
                    modifier = Modifier.weight(1f)
                )
                StatementFilterChip(
                    label = if (isArabic) StoreStrings.TX_FILTER_CASH_PURCHASE_AR else StoreStrings.TX_FILTER_CASH_PURCHASE_EN,
                    isSelected = uiState.statementFilter == StatementTxFilter.CASH_PURCHASE,
                    testTag = "filter_chip_cash_purchase",
                    onClick = { viewModel.setStatementFilter(StatementTxFilter.CASH_PURCHASE) },
                    modifier = Modifier.weight(1.2f)
                )
                StatementFilterChip(
                    label = if (isArabic) StoreStrings.TX_FILTER_DEBT_PURCHASE_AR else StoreStrings.TX_FILTER_DEBT_PURCHASE_EN,
                    isSelected = uiState.statementFilter == StatementTxFilter.DEBT_PURCHASE,
                    testTag = "filter_chip_debt_purchase",
                    onClick = { viewModel.setStatementFilter(StatementTxFilter.DEBT_PURCHASE) },
                    modifier = Modifier.weight(1.2f)
                )
            }
        }

        HorizontalDivider(color = GeoOutlineVariant)

        // ROWS LIST
        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                SimpleEmptyState(
                    message = if (isArabic) "لا توجد معاملات مطابقة للفلتر المحدد" else "No transactions found matching criteria",
                    icon = Icons.Default.ReceiptLong,
                    testTag = "statement_empty_state"
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
                    .testTag("statement_rows_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                items(rows, key = { it.id }) { row ->
                    StatementRowCard(row = row, currency = currency, isArabic = isArabic)
                }
                item {
                    // Totals Row at the bottom of the list
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, GeoOutlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .testTag("statement_totals_row")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isArabic) "الإجمالي (${rows.size} معاملة)" else "Total (${rows.size} txs)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isArabic) "الوارد: %,.2f | المنصرف: %,.2f".format(Locale.US, totalIn, totalOut)
                                    else "In: %,.2f | Out: %,.2f".format(Locale.US, totalIn, totalOut),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isArabic) "صافي الحساب" else "Net Total",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%,.2f %s", netBalance, currency),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (netBalance > 0) StatusRed else StatusGreen
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StatementSummaryCard(
    title: String,
    subtitle: String,
    value: String,
    color: Color,
    isLarge: Boolean,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(if (isLarge) 12.dp else 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (isLarge) 1.5.dp else 1.dp, color.copy(alpha = if (isLarge) 0.35f else 0.2f)),
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isLarge) 12.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLarge) 3.dp else 2.dp)
        ) {
            Text(
                text = title,
                style = if (isLarge) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
                else MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = if (isLarge) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isLarge) 10.sp else 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatementFilterChip(
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) GeoPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) null else BorderStroke(1.dp, GeoOutlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatementRowCard(
    row: StatementRow,
    currency: String,
    isArabic: Boolean
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GeoOutlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("statement_row_${row.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.customerName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${row.date} • ${row.description}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = if (row.isPayment) StatusGreenBg else if (row.isCreditDebt) StatusRedBg else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = row.type,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (row.isPayment) StatusGreen else if (row.isCreditDebt) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(
                color = GeoOutlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isArabic) "المبلغ: " else "Amount: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%s%,.2f %s", if (row.isPayment) "-" else "+", row.amount, currency),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (row.isPayment) StatusGreen else StatusRed
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isArabic) "الرصيد التراكمي: " else "Running: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%,.2f %s", row.runningBalance, currency),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (row.runningBalance > 0) StatusRed else StatusGreen
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: REPORTS
// -------------------------------------------------------------
@Composable
private fun ReportsTabContent(
    viewModel: AnalysisCenterViewModel,
    uiState: AnalysisCenterUiState,
    customers: List<CustomerAccount>,
    allTransactions: List<TransactionItem>,
    products: List<ProductItem>,
    transactionLines: List<TransactionItemLineEntity>,
    activePeriod: PeriodFilter,
    customStartDate: LocalDate?,
    customEndDate: LocalDate?,
    currency: String,
    isArabic: Boolean,
    context: Context,
    storeInfo: StoreInfo
) {
    val reportTypes = listOf(
        ReportType.DEBT_BALANCES to (if (isArabic) StoreStrings.REPORT_DEBT_BALANCES_AR else StoreStrings.REPORT_DEBT_BALANCES_EN),
        ReportType.SALES_AND_ITEMS to (if (isArabic) StoreStrings.REPORT_SALES_AND_ITEMS_AR else StoreStrings.REPORT_SALES_AND_ITEMS_EN),
        ReportType.TRANSACTIONS to (if (isArabic) StoreStrings.REPORT_TRANSACTIONS_AR else StoreStrings.REPORT_TRANSACTIONS_EN),
        ReportType.COMPREHENSIVE_CUSTOMER to (if (isArabic) StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_AR else StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_EN)
    )

    val isComprehensiveCustomer = uiState.selectedReportType == ReportType.COMPREHENSIVE_CUSTOMER
    val customerIsSelected = uiState.selectedCustomer != null

    // Filter transactions by active period dynamically
    val periodTransactions = remember(allTransactions, activePeriod, customStartDate, customEndDate) {
        allTransactions.filter { tx ->
            DateFilterUtils.isDateInPeriod(
                dateStr = tx.date,
                period = activePeriod,
                customStartDate = customStartDate,
                customEndDate = customEndDate
            )
        }
    }

    // Period display label
    val periodLabel = remember(activePeriod, customStartDate, customEndDate, isArabic) {
        when (activePeriod) {
            PeriodFilter.ALL -> if (isArabic) StoreStrings.PERIOD_ALL_AR else StoreStrings.PERIOD_ALL_EN
            PeriodFilter.TODAY -> if (isArabic) StoreStrings.PERIOD_TODAY_AR else StoreStrings.PERIOD_TODAY_EN
            PeriodFilter.MONTH -> if (isArabic) StoreStrings.PERIOD_MONTH_AR else StoreStrings.PERIOD_MONTH_EN
            PeriodFilter.CUSTOM -> {
                if (customStartDate != null && customEndDate != null) {
                    "${customStartDate} -> ${customEndDate}"
                } else {
                    if (isArabic) StoreStrings.PERIOD_CUSTOM_AR else StoreStrings.PERIOD_CUSTOM_EN
                }
            }
        }
    }

    // 1. DEBT_BALANCES calculations
    val storeAgingSummary = remember(customers, allTransactions, isArabic) {
        DebtAgingUtils.calculateStoreDebtAgingSummary(customers, allTransactions, isArabic = isArabic)
    }
    val customerDebtAgings = storeAgingSummary.customerAgings
    val totalOutstandingDebt = storeAgingSummary.totalOutstandingDebt
    val totalCustomersWithDebtCount = storeAgingSummary.totalCustomersWithDebtCount
    val sum0To30 = storeAgingSummary.sum0To30
    val sum31To60 = storeAgingSummary.sum31To60
    val sum61To90 = storeAgingSummary.sum61To90
    val sum90Plus = storeAgingSummary.sum90Plus

    // 2. SALES_AND_ITEMS calculations
    val cashSalesInvoices = remember(periodTransactions) {
        periodTransactions.filter { tx ->
            !tx.isCredit && (tx.activityType.contains("كاش") || tx.activityType.contains("Cash") || (!tx.activityType.contains("تسديد") && !tx.activityType.contains("Payment") && !tx.activityType.contains("آجل") && !tx.activityType.contains("دين") && !tx.activityType.contains("Debt")))
        }
    }
    val debtSalesInvoices = remember(periodTransactions) {
        periodTransactions.filter { tx ->
            tx.isCredit || tx.activityType.contains("آجل") || tx.activityType.contains("دين") || tx.activityType.contains("Debt") || tx.activityType.contains("شراء بالدين")
        }
    }
    val totalCashSalesAmount = remember(cashSalesInvoices) { cashSalesInvoices.sumOf { it.amount } }
    val totalDebtSalesAmount = remember(debtSalesInvoices) { debtSalesInvoices.sumOf { it.amount } }
    val totalSalesAmount = remember(totalCashSalesAmount, totalDebtSalesAmount) { totalCashSalesAmount + totalDebtSalesAmount }
    val totalInvoicesCount = remember(cashSalesInvoices, debtSalesInvoices) { cashSalesInvoices.size + debtSalesInvoices.size }

    val itemBreakdowns = remember(periodTransactions, transactionLines) {
        val periodTxIds = periodTransactions.map { it.id }.toSet()
        val linesInPeriod = transactionLines.filter { it.transactionId in periodTxIds }
        linesInPeriod.groupBy { it.productNameSnapshot.ifBlank { it.productId ?: "-" } }
            .map { (name, lines) ->
                val totalQty = lines.sumOf { it.quantity }
                val totalSales = lines.sumOf { it.subtotal }
                val profitMargin = lines.sumOf { line ->
                    val margin = (line.unitPrice - line.costPrice).coerceAtLeast(0.0)
                    margin * line.quantity
                }
                AggregatedProductLine(
                    productId = lines.firstOrNull()?.productId ?: "",
                    productName = name,
                    totalQuantity = totalQty,
                    totalSales = totalSales,
                    profitMargin = profitMargin
                )
            }.sortedByDescending { it.totalQuantity }
    }
    val totalItemProfitMargin = remember(itemBreakdowns) { itemBreakdowns.sumOf { it.profitMargin } }

    // 3. TRANSACTIONS calculations
    val sortedTransactions = remember(periodTransactions) { periodTransactions.sortedByDescending { it.date } }
    val totalTxCash = remember(periodTransactions) {
        periodTransactions.filter { !it.isCredit && (it.activityType.contains("كاش") || it.activityType.contains("Cash") || (!it.activityType.contains("تسديد") && !it.activityType.contains("Payment") && !it.activityType.contains("آجل") && !it.activityType.contains("دين") && !it.activityType.contains("Debt"))) }.sumOf { it.amount }
    }
    val totalTxDebt = remember(periodTransactions) {
        periodTransactions.filter { it.isCredit || it.activityType.contains("آجل") || it.activityType.contains("دين") || it.activityType.contains("Debt") || it.activityType.contains("شراء بالدين") }.sumOf { it.amount }
    }
    val totalTxPayments = remember(periodTransactions) {
        periodTransactions.filter { it.activityType.contains("تسديد") || it.activityType.contains("Payment") }.sumOf { it.amount }
    }

    // 4. COMPREHENSIVE_CUSTOMER calculations
    val selectedCustomer = uiState.selectedCustomer
    val customerTransactions = remember(periodTransactions, selectedCustomer) {
        if (selectedCustomer == null) emptyList()
        else periodTransactions.filter { it.customerName.equals(selectedCustomer.customerName, ignoreCase = true) }.sortedByDescending { it.date }
    }
    val customerCashPurchases = remember(customerTransactions) {
        customerTransactions.filter { !it.isCredit && (it.activityType.contains("كاش") || it.activityType.contains("Cash") || (!it.activityType.contains("تسديد") && !it.activityType.contains("Payment") && !it.activityType.contains("آجل") && !it.activityType.contains("دين") && !it.activityType.contains("Debt"))) }.sumOf { it.amount }
    }
    val customerDebtPurchases = remember(customerTransactions) {
        customerTransactions.filter { it.isCredit || it.activityType.contains("آجل") || it.activityType.contains("دين") || it.activityType.contains("Debt") || it.activityType.contains("شراء بالدين") }.sumOf { it.amount }
    }
    val customerPayments = remember(customerTransactions) {
        customerTransactions.filter { it.activityType.contains("تسديد") || it.activityType.contains("Payment") }.sumOf { it.amount }
    }
    val singleCustomerAging = remember(selectedCustomer, allTransactions, isArabic) {
        if (selectedCustomer == null) null
        else DebtAgingUtils.calculateCustomerAging(selectedCustomer, allTransactions, isArabic = isArabic)
    }
    val customerItemBreakdowns = remember(customerTransactions, transactionLines) {
        val custTxIds = customerTransactions.map { it.id }.toSet()
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
    }

    // Table Headers
    val tableHeaders = when (uiState.selectedReportType) {
        ReportType.DEBT_BALANCES -> listOf(
            if (isArabic) "العميل" else "Customer",
            if (isArabic) "الهاتف" else "Phone",
            if (isArabic) "أعمار الديون" else "Debt Aging",
            if (isArabic) "الرصيد المستحق" else "Balance Due"
        )
        ReportType.SALES_AND_ITEMS -> listOf(
            if (isArabic) "الصنف / البيان" else "Item / Description",
            if (isArabic) "الكمية" else "Quantity",
            if (isArabic) "إجمالي المبيعات" else "Total Sales",
            if (isArabic) "هامش الربح" else "Profit Margin"
        )
        ReportType.TRANSACTIONS -> listOf(
            if (isArabic) "التاريخ" else "Date",
            if (isArabic) "العميل" else "Customer",
            if (isArabic) "نوع المعاملة" else "Type",
            if (isArabic) "المبلغ" else "Amount"
        )
        ReportType.COMPREHENSIVE_CUSTOMER -> listOf(
            if (isArabic) "التاريخ" else "Date",
            if (isArabic) "البيان / الوصف" else "Description",
            if (isArabic) "النوع" else "Type",
            if (isArabic) "المبلغ" else "Amount"
        )
    }

    // Preview rows based on active report type
    val previewRows: List<ReportPreviewRow> = remember(
        uiState.selectedReportType,
        customerDebtAgings,
        itemBreakdowns,
        sortedTransactions,
        customerTransactions,
        selectedCustomer,
        cashSalesInvoices,
        debtSalesInvoices,
        totalCashSalesAmount,
        totalDebtSalesAmount,
        isArabic
    ) {
        when (uiState.selectedReportType) {
            ReportType.DEBT_BALANCES -> {
                customerDebtAgings.filter { it.currentDebt > 0 || it.currentBalance > 0 }.map { aging ->
                    ReportPreviewRow(
                        col1 = aging.customerName,
                        col2 = aging.phone.ifBlank { "-" },
                        col3 = aging.formatAgingSummary(isArabic),
                        col4 = String.format(Locale.US, "%.2f", aging.currentDebt)
                    )
                }
            }
            ReportType.SALES_AND_ITEMS -> {
                if (itemBreakdowns.isEmpty()) {
                    listOf(
                        ReportPreviewRow(
                            col1 = if (isArabic) "إجمالي المبيعات النقدية" else "Total Cash Sales",
                            col2 = "${cashSalesInvoices.size} ${if (isArabic) "فاتورة" else "inv"}",
                            col3 = String.format(Locale.US, "%.2f", totalCashSalesAmount),
                            col4 = "-"
                        ),
                        ReportPreviewRow(
                            col1 = if (isArabic) "إجمالي المبيعات الآجلة" else "Total Debt Sales",
                            col2 = "${debtSalesInvoices.size} ${if (isArabic) "فاتورة" else "inv"}",
                            col3 = String.format(Locale.US, "%.2f", totalDebtSalesAmount),
                            col4 = "-"
                        )
                    )
                } else {
                    itemBreakdowns.map { item ->
                        ReportPreviewRow(
                            col1 = item.productName,
                            col2 = "${item.totalQuantity}",
                            col3 = String.format(Locale.US, "%.2f", item.totalSales),
                            col4 = String.format(Locale.US, "%.2f", item.profitMargin)
                        )
                    }
                }
            }
            ReportType.TRANSACTIONS -> {
                sortedTransactions.map { tx ->
                    val typeLabel = if (tx.activityType.contains("تسديد") || tx.activityType.contains("Payment")) {
                        if (isArabic) "تسديد (دفعة)" else "Payment"
                    } else if (tx.isCredit || tx.activityType.contains("آجل") || tx.activityType.contains("دين") || tx.activityType.contains("Debt") || tx.activityType.contains("شراء بالدين")) {
                        if (isArabic) "شراء آجل (دين)" else "Debt Purchase"
                    } else {
                        if (isArabic) "شراء نقدي (كاش)" else "Cash Purchase"
                    }
                    ReportPreviewRow(
                        col1 = tx.date,
                        col2 = tx.customerName,
                        col3 = typeLabel,
                        col4 = String.format(Locale.US, "%.2f", tx.amount)
                    )
                }
            }
            ReportType.COMPREHENSIVE_CUSTOMER -> {
                if (selectedCustomer == null) emptyList()
                else customerTransactions.map { tx ->
                    val desc = tx.notes.ifBlank { tx.activityType }
                    val typeLabel = if (tx.activityType.contains("تسديد") || tx.activityType.contains("Payment")) {
                        if (isArabic) "تسديد" else "Payment"
                    } else if (tx.isCredit || tx.activityType.contains("آجل") || tx.activityType.contains("دين") || tx.activityType.contains("Debt") || tx.activityType.contains("شراء بالدين")) {
                        if (isArabic) "شراء آجل" else "Debt"
                    } else {
                        if (isArabic) "شراء كاش" else "Cash"
                    }
                    ReportPreviewRow(
                        col1 = tx.date,
                        col2 = desc,
                        col3 = typeLabel,
                        col4 = String.format(Locale.US, "%.2f", tx.amount)
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("reports_tab_content"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Report Type Selector Card (2x2 Grid)
        val canExport = !isComprehensiveCustomer || customerIsSelected

        val reportTitle = when (uiState.selectedReportType) {
            ReportType.DEBT_BALANCES -> if (isArabic) StoreStrings.REPORT_DEBT_BALANCES_AR else StoreStrings.REPORT_DEBT_BALANCES_EN
            ReportType.SALES_AND_ITEMS -> if (isArabic) StoreStrings.REPORT_SALES_AND_ITEMS_AR else StoreStrings.REPORT_SALES_AND_ITEMS_EN
            ReportType.TRANSACTIONS -> if (isArabic) StoreStrings.REPORT_TRANSACTIONS_AR else StoreStrings.REPORT_TRANSACTIONS_EN
            ReportType.COMPREHENSIVE_CUSTOMER -> {
                if (selectedCustomer != null) {
                    "${if (isArabic) StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_AR else StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_EN} - ${selectedCustomer.customerName}"
                } else {
                    if (isArabic) StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_AR else StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_EN
                }
            }
        }

        val exportKpis = when (uiState.selectedReportType) {
            ReportType.DEBT_BALANCES -> listOf(
                (if (isArabic) "إجمالي الديون" else "Total Debt") to AppCurrency.formatAmountWithDecimals(totalOutstandingDebt, isArabic),
                (if (isArabic) "العملاء المدينون" else "Debtor Customers") to "$totalCustomersWithDebtCount",
                (if (isArabic) "+90 يوم" else "90+ Days") to AppCurrency.formatAmount(sum90Plus)
            )
            ReportType.SALES_AND_ITEMS -> listOf(
                (if (isArabic) "إجمالي المبيعات" else "Total Sales") to AppCurrency.formatAmountWithDecimals(totalSalesAmount, isArabic),
                (if (isArabic) "مبيعات كاش" else "Cash Sales") to AppCurrency.formatAmountWithDecimals(totalCashSalesAmount, isArabic),
                (if (isArabic) "مبيعات آجل" else "Debt Sales") to AppCurrency.formatAmountWithDecimals(totalDebtSalesAmount, isArabic)
            )
            ReportType.TRANSACTIONS -> listOf(
                (if (isArabic) "إجمالي الكاش" else "Cash Sum") to AppCurrency.formatAmountWithDecimals(totalTxCash, isArabic),
                (if (isArabic) "إجمالي الآجل" else "Debt Sum") to AppCurrency.formatAmountWithDecimals(totalTxDebt, isArabic),
                (if (isArabic) "إجمالي التسديد" else "Payments") to AppCurrency.formatAmountWithDecimals(totalTxPayments, isArabic)
            )
            ReportType.COMPREHENSIVE_CUSTOMER -> listOf(
                (if (isArabic) "الرصيد المستحق" else "Balance Due") to AppCurrency.formatAmountWithDecimals(selectedCustomer?.balance ?: 0.0, isArabic),
                (if (isArabic) "مشتريات كاش" else "Cash Purchases") to AppCurrency.formatAmountWithDecimals(customerCashPurchases, isArabic),
                (if (isArabic) "مشتريات آجل" else "Debt Purchases") to AppCurrency.formatAmountWithDecimals(customerDebtPurchases, isArabic)
            )
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, GeoOutlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("report_type_selector_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (isArabic) StoreStrings.SELECT_REPORT_TYPE_AR else StoreStrings.SELECT_REPORT_TYPE_EN,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 2x2 Grid of the four report types
                val chunkedReports = reportTypes.chunked(2)
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.testTag("report_types_grid")
                ) {
                    chunkedReports.forEach { rowPair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowPair.forEach { (type, label) ->
                                val isSelected = uiState.selectedReportType == type
                                val icon = when (type) {
                                    ReportType.DEBT_BALANCES -> Icons.Default.Assessment
                                    ReportType.SALES_AND_ITEMS -> Icons.Default.ReceiptLong
                                    ReportType.TRANSACTIONS -> Icons.Default.TableChart
                                    ReportType.COMPREHENSIVE_CUSTOMER -> Icons.Default.Person
                                }
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) GeoPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) GeoPrimary else GeoOutlineVariant
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewModel.selectReportType(type) }
                                        .testTag("report_option_${type.name}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) GeoPrimary else MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(GeoPrimary),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) GeoPrimary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (rowPair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 2. Customer Selector below tiles ONLY when COMPREHENSIVE_CUSTOMER is selected
        if (isComprehensiveCustomer) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, GeoOutlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("report_customer_selector_container")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = GeoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) StoreStrings.CUSTOMER_CONTEXT_LABEL_AR else StoreStrings.CUSTOMER_CONTEXT_LABEL_EN,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    CustomerSelectorField(
                        customers = customers,
                        selectedCustomer = uiState.selectedCustomer,
                        onCustomerSelected = { cust ->
                            if (cust != null) {
                                viewModel.selectCustomer(cust)
                            } else {
                                viewModel.clearCustomer()
                            }
                        },
                        isArabic = isArabic,
                        isRequired = true,
                        allowAllCustomers = false,
                        allCustomersLabel = if (isArabic) StoreStrings.SELECT_CUSTOMER_FOR_REPORT_AR else StoreStrings.SELECT_CUSTOMER_FOR_REPORT_EN,
                        placeholderText = if (isArabic) StoreStrings.SELECT_CUSTOMER_FOR_REPORT_AR else StoreStrings.SELECT_CUSTOMER_FOR_REPORT_EN,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("report_pick_customer_button"),
                        testTag = "report_comprehensive_customer_selector"
                    )

                    if (!customerIsSelected) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(StatusRedBg)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("comprehensive_customer_warning_card")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isArabic) StoreStrings.CUSTOMER_REQUIRED_FOR_REPORT_AR else StoreStrings.CUSTOMER_REQUIRED_FOR_REPORT_EN,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = StatusRed
                            )
                        }
                    }
                }
            }
        }

        // 3. Dynamic Summary / KPI Cards for Each Report Type
        when (uiState.selectedReportType) {
            ReportType.DEBT_BALANCES -> {
                // Store Balances & Debt Aging Summary card relocated to Home screen
            }

            ReportType.SALES_AND_ITEMS -> {
                SalesReportPresentation(
                    isArabic = isArabic,
                    currency = currency,
                    periodLabel = periodLabel,
                    storeInfo = storeInfo,
                    cashSalesInvoices = cashSalesInvoices,
                    debtSalesInvoices = debtSalesInvoices,
                    totalCashSalesAmount = totalCashSalesAmount,
                    totalDebtSalesAmount = totalDebtSalesAmount,
                    totalSalesAmount = totalSalesAmount,
                    totalInvoicesCount = totalInvoicesCount,
                    itemBreakdowns = itemBreakdowns,
                    totalItemProfitMargin = totalItemProfitMargin,
                    previewRows = previewRows
                )
            }

            ReportType.TRANSACTIONS -> {
                TransactionsReportPresentation(
                    isArabic = isArabic,
                    currency = currency,
                    periodLabel = periodLabel,
                    storeInfo = storeInfo,
                    sortedTransactions = sortedTransactions,
                    totalTxCash = totalTxCash,
                    totalTxDebt = totalTxDebt,
                    totalTxPayments = totalTxPayments,
                    previewRows = previewRows
                )
            }

            ReportType.COMPREHENSIVE_CUSTOMER -> {
                ComprehensiveCustomerReportPresentation(
                    isArabic = isArabic,
                    currency = currency,
                    periodLabel = periodLabel,
                    storeInfo = storeInfo,
                    selectedCustomer = selectedCustomer,
                    customerTransactions = customerTransactions,
                    customerCashPurchases = customerCashPurchases,
                    customerDebtPurchases = customerDebtPurchases,
                    customerPayments = customerPayments,
                    singleCustomerAging = singleCustomerAging,
                    customerItemBreakdowns = customerItemBreakdowns,
                    previewRows = previewRows
                )
            }
        }

        // 4. Report Preview Table (For DEBT_BALANCES report type)
        if (uiState.selectedReportType == ReportType.DEBT_BALANCES) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, GeoOutlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("report_preview_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TableChart, contentDescription = null, tint = GeoPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isArabic) StoreStrings.REPORT_PREVIEW_TITLE_AR else StoreStrings.REPORT_PREVIEW_TITLE_EN,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = if (isArabic) "${previewRows.size} صفوف" else "${previewRows.size} rows",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Table Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        tableHeaders.forEachIndexed { idx, title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(if (idx == 1 || idx == 2) 1.2f else 1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isComprehensiveCustomer && !customerIsSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isArabic) StoreStrings.SELECT_CUSTOMER_FOR_REPORT_AR else StoreStrings.SELECT_CUSTOMER_FOR_REPORT_EN,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (previewRows.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isArabic) "لا توجد تفاصيل متاحة في هذه الفترة" else "No details available for this period",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        previewRows.take(15).forEachIndexed { idx, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (idx % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("report_row_$idx"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = row.col1, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = row.col2, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = row.col3, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = row.col4, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            }
                        }
                        if (previewRows.size > 15) {
                            Text(
                                text = if (isArabic) "+ ${previewRows.size - 15} سجلات إضافية في ملف التصدير" else "+ ${previewRows.size - 15} more records in exported file",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Centralized Export Action Bar (PDF, CSV, Print, Share)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // PDF Export
            Button(
                onClick = {
                    if (canExport) {
                        val file = ReportExporter.createCachedPdf(
                            context = context,
                            fileName = "Report_${System.currentTimeMillis()}.pdf",
                            title = reportTitle,
                            storeName = storeInfo.storeName.ifBlank { if (isArabic) "سمول ستور" else "SmallStore" },
                            subtitle = if (isArabic) "الفترة: $periodLabel" else "Period: $periodLabel",
                            kpis = exportKpis,
                            headers = tableHeaders,
                            rows = previewRows,
                            isArabic = isArabic
                        )
                        ReportExporter.shareFile(context, file, "application/pdf", reportTitle)
                    }
                },
                enabled = canExport,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("report_export_pdf_button")
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = if (isArabic) StoreStrings.EXPORT_PDF_AR else StoreStrings.EXPORT_PDF_EN, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            // CSV Export
            OutlinedButton(
                onClick = {
                    if (canExport) {
                        val csv = ReportExporter.generateReportCsv(tableHeaders, previewRows)
                        val file = ReportExporter.createCachedCsv(context, "Report_${System.currentTimeMillis()}.csv", csv)
                        ReportExporter.shareFile(context, file, "text/csv", reportTitle)
                        Toast.makeText(context, if (isArabic) "تم تجهيز ملف CSV للمشاركة" else "CSV report ready for export", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = canExport,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("report_export_csv_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = GeoPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "CSV", color = GeoPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            // Print HTML
            OutlinedButton(
                onClick = {
                    if (canExport) {
                        val html = ReportExporter.generateReportHtml(
                            title = reportTitle,
                            storeName = storeInfo.storeName.ifBlank { if (isArabic) "سمول ستور" else "SmallStore" },
                            subtitle = if (isArabic) "الفترة: $periodLabel" else "Period: $periodLabel",
                            kpis = exportKpis,
                            headers = tableHeaders,
                            rows = previewRows,
                            isArabic = isArabic
                        )
                        ReportExporter.printHtml(context, reportTitle, html, isArabic)
                    }
                },
                enabled = canExport,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("report_print_button")
            ) {
                Icon(Icons.Default.Print, contentDescription = null, tint = GeoPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = if (isArabic) StoreStrings.PRINT_REPORT_AR else StoreStrings.PRINT_REPORT_EN, color = GeoPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            // Share Text
            OutlinedButton(
                onClick = {
                    if (canExport) {
                        val shareText = buildString {
                            appendLine("SmallStore - ${storeInfo.storeName.ifBlank { "Store" }}")
                            appendLine(reportTitle)
                            appendLine("Period / الفترة: $periodLabel")
                            appendLine(tableHeaders.joinToString(" | "))
                            previewRows.take(15).forEach { r ->
                                appendLine("${r.col1} | ${r.col2} | ${r.col3} | ${r.col4}")
                            }
                            if (previewRows.size > 15) {
                                appendLine("... (+${previewRows.size - 15} more)")
                            }
                        }
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Report"))
                    }
                },
                enabled = canExport,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("report_share_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = GeoPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = if (isArabic) "مشاركة" else "Share", color = GeoPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
