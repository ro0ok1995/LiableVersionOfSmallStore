package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.example.data.db.TransactionItemLineEntity
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.data.db.Supplier
import com.example.data.db.Purchase
import com.example.data.db.SupplierPayment
import com.example.data.db.PurchaseReturn
import com.example.accounting.SupplierLedgerEntry
import com.example.accounting.SupplierLedgerCalculator
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
    suppliers: List<Supplier> = emptyList(),
    supplierPurchases: List<Purchase> = emptyList(),
    supplierPayments: List<SupplierPayment> = emptyList(),
    supplierReturns: List<PurchaseReturn> = emptyList(),
    initialSupplierId: String? = null,
    onGetSupplierStatement: (suspend (String) -> List<SupplierLedgerEntry>)? = null,
    onGetSupplierBalance: (suspend (String) -> com.example.accounting.SupplierBalanceSummary)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isArabic = languageMode == LanguageMode.ARABIC
    val currency = AppCurrency.SYMBOL
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var analysisEntityMode by remember { mutableIntStateOf(if (initialSupplierId != null) 1 else 0) } // 0 customers, 1 suppliers
    var selectedSupplierId by remember { mutableStateOf(initialSupplierId) }
    var supplierStatementEntries by remember { mutableStateOf<List<SupplierLedgerEntry>>(emptyList()) }
    val selectedSupplier = suppliers.firstOrNull { it.id == selectedSupplierId } ?: suppliers.firstOrNull()
    LaunchedEffect(initialSupplierId, suppliers) {
        if (initialSupplierId != null) analysisEntityMode = 1
        if (initialSupplierId != null && suppliers.any { it.id == initialSupplierId }) selectedSupplierId = initialSupplierId
        else if (suppliers.none { it.id == selectedSupplierId }) selectedSupplierId = suppliers.firstOrNull()?.id
    }
    LaunchedEffect(selectedSupplier?.id, supplierPurchases, supplierPayments, uiState.currentTab, analysisEntityMode) {
        val id = selectedSupplier?.id
        supplierStatementEntries = if (analysisEntityMode == 1 && id != null && onGetSupplierStatement != null) runCatching { onGetSupplierStatement(id) }.getOrDefault(emptyList()) else emptyList()
    }

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
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilterChip(selected = analysisEntityMode == 0, onClick = { analysisEntityMode = 0 }, label = { Text(if (isArabic) "العملاء" else "Customers") }, modifier = Modifier.testTag("analysis_mode_customers"))
                            FilterChip(selected = analysisEntityMode == 1, onClick = { analysisEntityMode = 1 }, label = { Text(if (isArabic) "الموردون" else "Suppliers") }, modifier = Modifier.testTag("analysis_mode_suppliers"))
                        }
                        if (analysisEntityMode == 0) Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
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
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text(if (isArabic) "المورد المحدد" else "Selected supplier", style = MaterialTheme.typography.labelMedium)
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    suppliers.filter { !it.isArchived }.forEach { supplier ->
                                        FilterChip(selected = selectedSupplier?.id == supplier.id, onClick = { selectedSupplierId = supplier.id }, label = { Text(supplier.name, maxLines = 1) })
                                    }
                                }
                            }
                        }
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
                    if (analysisEntityMode == 1) {
                        SupplierStatisticsContent(supplier = selectedSupplier, suppliers = suppliers.filter { !it.isArchived }, purchases = supplierPurchases, payments = supplierPayments, returns = supplierReturns, startDate = activeStartDate?.toString(), endDate = activeEndDate?.toString(), onGetSupplierBalance = onGetSupplierBalance, isArabic = isArabic, currency = currency)
                    } else StatisticsTabContent(
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
                    if (analysisEntityMode == 1) {
                        SupplierStatementContent(entries = supplierStatementEntries, supplierName = selectedSupplier?.name.orEmpty(), startDate = activeStartDate?.toString(), endDate = activeEndDate?.toString(), isArabic = isArabic, currency = currency)
                    } else AccountStatementTabContent(
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


@Composable
private fun SupplierStatisticsContent(
    supplier: Supplier?, suppliers: List<Supplier>, purchases: List<Purchase>, payments: List<SupplierPayment>, returns: List<PurchaseReturn>,
    startDate: String?, endDate: String?, onGetSupplierBalance: (suspend (String) -> com.example.accounting.SupplierBalanceSummary)?, isArabic: Boolean, currency: String
) {
    var authoritativeBalances by remember { mutableStateOf<Map<String, com.example.accounting.SupplierBalanceSummary>>(emptyMap()) }
    LaunchedEffect(suppliers, purchases, payments, returns, onGetSupplierBalance) {
        authoritativeBalances = suppliers.associate { sup ->
            val result = if (onGetSupplierBalance != null) runCatching { onGetSupplierBalance(sup.id) }.getOrNull() else null
            sup.id to (result ?: SupplierLedgerCalculator.calculateSupplierBalance(sup.id, purchases, payments, returns))
        }
    }
    val totalPayables = authoritativeBalances.values.sumOf { it.balance.coerceAtLeast(0.0) }
    val periodPurchases = remember(purchases, startDate, endDate) { purchases.filter { (startDate == null || it.purchaseDate >= startDate) && (endDate == null || it.purchaseDate <= endDate) && it.status != "REVERSED" }.sumOf { it.totalAmount } }
    val periodPayments = remember(payments, startDate, endDate) { payments.filter { (startDate == null || it.paymentDate >= startDate) && (endDate == null || it.paymentDate <= endDate) && it.status != "REVERSED" }.sumOf { it.amount } }
    val periodReturns = remember(returns, startDate, endDate) { returns.filter { (startDate == null || it.returnDate >= startDate) && (endDate == null || it.returnDate <= endDate) && it.status != "REVERSED" }.sumOf { it.amount } }
    val summary = supplier?.let { authoritativeBalances[it.id] ?: SupplierLedgerCalculator.calculateSupplierBalance(it.id, purchases, payments, returns) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (isArabic) "إحصائيات الموردين" else "Supplier statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        SupplierMetricCard(if (isArabic) "إجمالي مستحقات الموردين الحالية" else "Current total supplier payables", totalPayables, currency)
        SupplierMetricCard(if (isArabic) "المشتريات خلال الفترة" else "Purchases in selected period", periodPurchases, currency)
        SupplierMetricCard(if (isArabic) "المدفوعات خلال الفترة" else "Payments in selected period", periodPayments, currency)
        SupplierMetricCard(if (isArabic) "المرتجعات خلال الفترة" else "Returns in selected period", periodReturns, currency)
        if (supplier != null && summary != null) {
            Text(if (isArabic) "ملخص المورد المحدد: ${supplier.name}" else "Selected supplier: ${supplier.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SupplierMetricCard(if (isArabic) "الرصيد الحالي" else "Current balance", summary.balance, currency)
            if (summary.balance < 0.0) SupplierMetricCard(if (isArabic) "الرصيد المقدم" else "Supplier advance", -summary.balance, currency)
        } else if (suppliers.isEmpty()) Text(if (isArabic) "لا يوجد موردون نشطون" else "No active suppliers")
        Text(if (isArabic) "مؤشرات الفترة تستخدم تاريخ كل حركة؛ الرصيد الحالي يشمل الحركات النشطة التاريخية." else "Period metrics use each movement date; current balance includes active historical movements.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SupplierMetricCard(label: String, value: Double, currency: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(String.format(Locale.US, "%,.2f %s", value, currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SupplierStatementContent(entries: List<SupplierLedgerEntry>, supplierName: String, startDate: String?, endDate: String?, isArabic: Boolean, currency: String) {
    val visible = remember(entries, startDate, endDate) { entries.filter { (startDate == null || it.date >= startDate) && (endDate == null || it.date <= endDate) } }
    val opening = remember(entries, startDate) { if (startDate == null) 0.0 else entries.lastOrNull { it.date < startDate }?.runningBalance ?: 0.0 }
    val closing = visible.lastOrNull()?.runningBalance ?: opening
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (isArabic) "كشف حساب المورد" else "Supplier account statement", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(supplierName, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SupplierMetricCard(if (isArabic) "الرصيد الافتتاحي" else "Opening balance", opening, currency)
        }
        if (visible.isEmpty()) Text(if (isArabic) "لا توجد حركات في الفترة المحددة" else "No movements in the selected period", color = MaterialTheme.colorScheme.onSurfaceVariant)
        visible.forEach { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(entry.date, style = MaterialTheme.typography.labelMedium); Text(String.format(Locale.US, "%,.2f %s", entry.runningBalance, currency), fontWeight = FontWeight.Bold) }
                    Text(entry.description, fontWeight = FontWeight.SemiBold)
                    Text("${if (isArabic) "مدين" else "Debit"}: ${String.format(Locale.US, "%,.2f", entry.debit)}  •  ${if (isArabic) "دائن" else "Credit"}: ${String.format(Locale.US, "%,.2f", entry.credit)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        SupplierMetricCard(if (isArabic) "الرصيد الختامي" else "Closing balance", closing, currency)
    }
}
