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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
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
import com.example.accounting.SupplierLedgerEntryType
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
                            SupplierSelectorField(
                                suppliers = suppliers.filter { !it.isArchived },
                                selectedSupplier = selectedSupplier,
                                onSupplierSelected = { selectedSupplierId = it.id },
                                isArabic = isArabic,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
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


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SupplierSelectorField(
    suppliers: List<Supplier>,
    selectedSupplier: Supplier?,
    onSupplierSelected: (Supplier) -> Unit,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    var isSheetOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Surface(
        onClick = { query = ""; isSheetOpen = true },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedSupplier != null) GeoPrimary.copy(alpha = 0.45f) else GeoOutlineVariant),
        modifier = modifier.testTag("analysis_supplier_selector")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (isArabic) "المورد المحدد" else "Selected supplier", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selectedSupplier?.name ?: if (isArabic) "اختر مورداً" else "Select a supplier", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                selectedSupplier?.phone?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("⌄", color = GeoPrimary, style = MaterialTheme.typography.titleLarge)
        }
    }
    if (isSheetOpen) {
        ModalBottomSheet(onDismissRequest = { isSheetOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (isArabic) "اختيار المورد" else "Choose supplier", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().testTag("analysis_supplier_search"),
                    singleLine = true,
                    placeholder = { Text(if (isArabic) "ابحث باسم المورد أو الهاتف..." else "Search supplier name or phone...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { if (query.isNotEmpty()) androidx.compose.material3.IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = if (isArabic) "مسح البحث" else "Clear search") } },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors()
                )
                val filtered = suppliers.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.phone.contains(query.trim(), ignoreCase = true) }
                if (filtered.isEmpty()) {
                    Text(if (isArabic) "لا توجد نتائج مطابقة" else "No matching suppliers", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(filtered, key = { it.id }) { supplier ->
                            Surface(onClick = { onSupplierSelected(supplier); isSheetOpen = false }, color = if (supplier.id == selectedSupplier?.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                    Text(supplier.name, fontWeight = if (supplier.id == selectedSupplier?.id) FontWeight.Bold else FontWeight.Medium)
                                    if (supplier.phone.isNotBlank()) Text(supplier.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (isArabic) "إحصائيات الموردين" else "Supplier statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SupplierMetricCard(if (isArabic) "المستحقات الحالية" else "Current payables", totalPayables, currency, Modifier.weight(1f))
            SupplierMetricCard(if (isArabic) "المشتريات خلال الفترة" else "Period purchases", periodPurchases, currency, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SupplierMetricCard(if (isArabic) "المدفوعات خلال الفترة" else "Period payments", periodPayments, currency, Modifier.weight(1f))
            SupplierMetricCard(if (isArabic) "المرتجعات خلال الفترة" else "Period returns", periodReturns, currency, Modifier.weight(1f))
        }
        if (supplier != null && summary != null) {
            Text(if (isArabic) "ملخص المورد المحدد" else "Selected supplier summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(supplier.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SupplierMetricCard(if (isArabic) "الرصيد الحالي المستحق" else "Current balance", summary.balance, currency, Modifier.weight(1f), emphasize = true)
                SupplierMetricCard(if (isArabic) "دفعة مقدمة" else "Supplier advance", (-summary.balance).coerceAtLeast(0.0), currency, Modifier.weight(1f))
            }
        } else if (suppliers.isEmpty()) {
            Text(if (isArabic) "لا يوجد موردون نشطون" else "No active suppliers", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (isArabic) "المؤشرات الدورية تعتمد على تاريخ الحركة، أما الرصيد الحالي فيعكس الحركات النشطة." else "Period metrics use movement dates; current balance reflects active ledger movements.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SupplierMetricCard(label: String, value: Double, currency: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (emphasize) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f) else MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (emphasize) GeoPrimary.copy(alpha = 0.22f) else GeoOutlineVariant.copy(alpha = 0.65f)),
        modifier = modifier
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
            Text(String.format(Locale.US, "%,.2f %s", value, currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (value < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, maxLines = 2)
        }
    }
}

@Composable
private fun SupplierStatementContent(entries: List<SupplierLedgerEntry>, supplierName: String, startDate: String?, endDate: String?, isArabic: Boolean, currency: String) {
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    val visiblePeriod = remember(entries, startDate, endDate) { entries.filter { (startDate == null || it.date >= startDate) && (endDate == null || it.date <= endDate) } }
    val opening = remember(entries, startDate) { if (startDate == null) 0.0 else entries.lastOrNull { it.date < startDate }?.runningBalance ?: 0.0 }
    val filteredEntries = remember(visiblePeriod, query, selectedFilter) {
        visiblePeriod.filter { entry ->
            val typeMatches = when (selectedFilter) {
                "PURCHASE" -> entry.entryType == SupplierLedgerEntryType.PURCHASE
                "PAYMENT" -> entry.entryType == SupplierLedgerEntryType.SUPPLIER_PAYMENT
                "RETURN" -> entry.entryType == SupplierLedgerEntryType.PURCHASE_RETURN
                else -> true
            }
            typeMatches && (query.isBlank() || entry.description.contains(query.trim(), ignoreCase = true) || entry.referenceId.contains(query.trim(), ignoreCase = true) || entry.date.contains(query.trim(), ignoreCase = true))
        }
    }
    val closing = visiblePeriod.lastOrNull()?.runningBalance ?: opening
    val totalDebit = visiblePeriod.sumOf { it.debit }
    val totalCredit = visiblePeriod.sumOf { it.credit }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (isArabic) "كشف حساب المورد" else "Supplier account statement", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(supplierName, style = MaterialTheme.typography.titleMedium, color = GeoPrimary, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SupplierMetricCard(if (isArabic) "الرصيد الافتتاحي" else "Opening balance", opening, currency, Modifier.weight(1f))
                SupplierMetricCard(if (isArabic) "إجمالي المدين" else "Total debit", totalDebit, currency, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SupplierMetricCard(if (isArabic) "إجمالي الدائن" else "Total credit", totalCredit, currency, Modifier.weight(1f))
                SupplierMetricCard(if (isArabic) "الرصيد الختامي" else "Closing balance", closing, currency, Modifier.weight(1f), emphasize = true)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().testTag("supplier_statement_search"),
            singleLine = true,
            placeholder = { Text(if (isArabic) "بحث في الحركات أو المرجع أو التاريخ..." else "Search movements, reference or date...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) androidx.compose.material3.IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = if (isArabic) "مسح البحث" else "Clear search") } },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors()
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL", "PURCHASE", "PAYMENT", "RETURN").forEach { filter ->
                FilterChip(selected = selectedFilter == filter, onClick = { selectedFilter = filter }, label = {
                    Text(when (filter) {
                        "PURCHASE" -> if (isArabic) "المشتريات" else "Purchases"
                        "PAYMENT" -> if (isArabic) "المدفوعات" else "Payments"
                        "RETURN" -> if (isArabic) "المرتجعات" else "Returns"
                        else -> if (isArabic) "الكل" else "All"
                    })
                })
            }
        }
        Text(if (isArabic) "الحركات (${filteredEntries.size})" else "Movements (${filteredEntries.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (filteredEntries.isEmpty()) {
            Text(if (isArabic) "لا توجد حركات مطابقة للفترة والبحث المحددين" else "No movements match the selected period and search", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 18.dp))
        } else {
            filteredEntries.forEach { entry ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, GeoOutlineVariant.copy(alpha = 0.6f)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format(Locale.US, "%,.2f %s", entry.runningBalance, currency), fontWeight = FontWeight.Bold, color = if (entry.runningBalance < 0) MaterialTheme.colorScheme.error else GeoPrimary)
                        }
                        Text(entry.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        HorizontalDivider(color = GeoOutlineVariant.copy(alpha = 0.6f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(if (isArabic) "مدين · يخفض المستحق" else "Debit · reduces payable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.US, "%,.2f %s", entry.debit, currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (entry.debit > 0) com.example.ui.theme.StatusGreen else MaterialTheme.colorScheme.onSurface)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(if (isArabic) "دائن · يزيد المستحق" else "Credit · increases payable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.US, "%,.2f %s", entry.credit, currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (entry.credit > 0) com.example.ui.theme.StatusRed else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}
