package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TransactionItemLineEntity
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.model.LanguageMode
import com.example.model.ProductItem
import com.example.model.StoreInfo
import com.example.model.StoreStrings
import com.example.model.TransactionItem
import com.example.ui.components.CustomerSelectorField
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.viewmodel.AnalysisCenterViewModel
import com.example.viewmodel.AnalysisTab
import com.example.viewmodel.ReportType

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
                        allCustomers = customers,
                        transactionLines = transactionLines,
                        activePeriod = activePeriod,
                        customStartDate = activeStartDate,
                        customEndDate = activeEndDate,
                        selectedChartType = uiState.selectedChartType,
                        onChartTypeSelected = { viewModel.selectChartType(it) },
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
