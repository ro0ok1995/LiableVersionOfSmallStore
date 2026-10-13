package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.ui.components.BreakdownChartItem
import com.example.ui.components.BreakdownChartTabButton
import com.example.ui.components.BreakdownChartType
import com.example.ui.components.BreakdownColumnChart
import com.example.ui.components.BreakdownComboChart
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusRedBg
import com.example.util.ReportExporter
import com.example.util.ReportPreviewRow
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
    var supplierChartType by remember { mutableStateOf(BreakdownChartType.DONUT) }
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
                            SupplierSearchSelector(
                                suppliers = suppliers.filter { !it.isArchived },
                                selectedSupplier = selectedSupplier,
                                isArabic = isArabic,
                                onSelected = { selectedSupplierId = it.id },
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
                        SupplierStatisticsContent(supplier = selectedSupplier, suppliers = suppliers.filter { !it.isArchived }, purchases = supplierPurchases, payments = supplierPayments, returns = supplierReturns, startDate = activeStartDate?.toString(), endDate = activeEndDate?.toString(), onGetSupplierBalance = onGetSupplierBalance, isArabic = isArabic, currency = currency, chartType = supplierChartType, onChartTypeSelected = { supplierChartType = it }, context = context, storeName = storeInfo.storeName)
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
private fun SupplierSearchSelector(
    suppliers: List<Supplier>,
    selectedSupplier: Supplier?,
    isArabic: Boolean,
    onSelected: (Supplier) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(selectedSupplier?.id) { mutableStateOf(selectedSupplier?.name.orEmpty()) }
    val filtered = suppliers.filter { it.name.contains(query, ignoreCase = true) || (it.phone?.contains(query, ignoreCase = true) == true) }
    Column(modifier) {
        Text(if (isArabic) "المورد المحدد" else "Selected supplier", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; expanded = true },
                modifier = Modifier.fillMaxWidth().testTag("supplier_context_search").onFocusChanged { state ->
                    if (state.isFocused) {
                        expanded = true
                        if (query == selectedSupplier?.name) query = ""
                    }
                },
                singleLine = true,
                label = { Text(if (isArabic) "ابحث باسم المورد أو الهاتف" else "Search supplier by name or phone") },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GeoPrimary, unfocusedBorderColor = GeoOutlineVariant)
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth()) {
                filtered.forEach { item ->
                    DropdownMenuItem(
                        text = { Column { Text(item.name, fontWeight = if (item.id == selectedSupplier?.id) FontWeight.Bold else FontWeight.Normal); item.phone?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } },
                        onClick = { onSelected(item); query = item.name; expanded = false }
                    )
                }
                if (filtered.isEmpty()) DropdownMenuItem(text = { Text(if (isArabic) "لا توجد نتائج" else "No results") }, onClick = { expanded = false })
            }
        }
    }
}

@Composable
private fun SupplierStatisticsContent(
    supplier: Supplier?, suppliers: List<Supplier>, purchases: List<Purchase>, payments: List<SupplierPayment>, returns: List<PurchaseReturn>,
    startDate: String?, endDate: String?, onGetSupplierBalance: (suspend (String) -> com.example.accounting.SupplierBalanceSummary)?, isArabic: Boolean, currency: String,
    chartType: BreakdownChartType, onChartTypeSelected: (BreakdownChartType) -> Unit, context: android.content.Context, storeName: String
) {
    var authoritativeBalances by remember { mutableStateOf<Map<String, com.example.accounting.SupplierBalanceSummary>>(emptyMap()) }
    LaunchedEffect(suppliers, purchases, payments, returns, onGetSupplierBalance) {
        authoritativeBalances = suppliers.associate { sup ->
            val result = if (onGetSupplierBalance != null) runCatching { onGetSupplierBalance(sup.id) }.getOrNull() else null
            sup.id to (result ?: SupplierLedgerCalculator.calculateSupplierBalance(sup.id, purchases, payments, returns))
        }
    }
    val activePurchases = remember(purchases, startDate, endDate) { purchases.filter { (startDate == null || it.purchaseDate >= startDate) && (endDate == null || it.purchaseDate <= endDate) && it.status != "REVERSED" } }
    val activePayments = remember(payments, startDate, endDate) { payments.filter { (startDate == null || it.paymentDate >= startDate) && (endDate == null || it.paymentDate <= endDate) && it.status != "REVERSED" } }
    val activeReturns = remember(returns, startDate, endDate) { returns.filter { (startDate == null || it.returnDate >= startDate) && (endDate == null || it.returnDate <= endDate) && it.status != "REVERSED" } }
    val summary = supplier?.let { authoritativeBalances[it.id] ?: SupplierLedgerCalculator.calculateSupplierBalance(it.id, purchases, payments, returns) }
    val scopedPurchases = activePurchases.filter { supplier == null || it.supplierId == supplier.id }
    val scopedPayments = activePayments.filter { supplier == null || it.supplierId == supplier.id }
    val scopedReturns = activeReturns.filter { supplier == null || it.supplierId == supplier.id }
    val totalPayables = summary?.balance?.coerceAtLeast(0.0) ?: authoritativeBalances.values.sumOf { it.balance.coerceAtLeast(0.0) }
    val periodPurchases = scopedPurchases.sumOf { it.totalAmount }
    val periodPayments = scopedPayments.sumOf { it.amount }
    val periodCreditPurchases = scopedPurchases.sumOf { it.creditAmount }
    val periodCashPurchases = scopedPurchases.sumOf { it.paidAmount }
    // Payable changes only by the unpaid purchase portion, not the full invoice total.
    // Cash purchases are already settled and must not inflate Accounts Payable.
    val netPayableMovement = periodCreditPurchases - periodPayments - periodReturns
    val totalVolume = (periodCreditPurchases + periodCashPurchases + periodPayments + periodReturns).coerceAtLeast(0.0)
    fun percent(v: Double) = if (totalVolume > 0.0) ((v / totalVolume) * 100).toInt().coerceIn(0, 100) else 0
    val chartItems = remember(periodCreditPurchases, periodCashPurchases, periodPayments, periodReturns, totalVolume, isArabic) {
        listOf(
            BreakdownChartItem(if (isArabic) "مشتريات آجلة" else "Credit Purchases", "Credit Purchases", periodCreditPurchases, percent(periodCreditPurchases), StatusRed),
            BreakdownChartItem(if (isArabic) "مشتريات نقدية" else "Cash Purchases", "Cash Purchases", periodCashPurchases, percent(periodCashPurchases), StatusBlue),
            BreakdownChartItem(if (isArabic) "المدفوعات" else "Payments", "Payments", periodPayments, percent(periodPayments), StatusGreen),
            BreakdownChartItem(if (isArabic) "المرتجعات" else "Returns", "Returns", periodReturns, percent(periodReturns), StatusAmber)
        )
    }
    val reportRows = listOf(
        ReportPreviewRow(if (isArabic) "المؤشر" else "Metric", if (isArabic) "القيمة" else "Amount", "", ""),
        ReportPreviewRow(if (isArabic) "إجمالي المستحقات الحالية" else "Current payables", String.format(Locale.US, "%,.2f", totalPayables), currency, ""),
        ReportPreviewRow(if (isArabic) "المشتريات خلال الفترة" else "Purchases in period", String.format(Locale.US, "%,.2f", periodPurchases), currency, ""),
        ReportPreviewRow(if (isArabic) "المدفوعات خلال الفترة" else "Payments in period", String.format(Locale.US, "%,.2f", periodPayments), currency, ""),
        ReportPreviewRow(if (isArabic) "المرتجعات خلال الفترة" else "Returns in period", String.format(Locale.US, "%,.2f", periodReturns), currency, ""),
        ReportPreviewRow(if (isArabic) "صافي تغير المستحقات" else "Net payable change", String.format(Locale.US, "%,.2f", netPayableMovement), currency, "")
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("supplier_statistics_content"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SupplierKpiCard(if (isArabic) "إجمالي المستحقات" else "Current Payables", String.format(Locale.US, "%,.2f %s", totalPayables, currency), StatusRed, Modifier.weight(1f), "supplier_kpi_payables")
            SupplierKpiCard(if (isArabic) "المشتريات" else "Purchases", String.format(Locale.US, "%,.2f %s", periodPurchases, currency), GeoPrimary, Modifier.weight(1f), "supplier_kpi_purchases")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SupplierKpiCard(if (isArabic) "المدفوعات" else "Payments", String.format(Locale.US, "%,.2f %s", periodPayments, currency), StatusGreen, Modifier.weight(1f), "supplier_kpi_payments")
            SupplierKpiCard(if (isArabic) "المرتجعات" else "Returns", String.format(Locale.US, "%,.2f %s", periodReturns, currency), StatusBlue, Modifier.weight(1f), "supplier_kpi_returns")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SupplierKpiCard(if (isArabic) "مشتريات آجلة" else "Credit Purchases", String.format(Locale.US, "%,.2f %s", periodCreditPurchases, currency), StatusRed, Modifier.weight(1f), "supplier_kpi_credit_purchases")
            SupplierKpiCard(if (isArabic) "مشتريات نقدية" else "Cash Purchases", String.format(Locale.US, "%,.2f %s", periodCashPurchases, currency), StatusBlue, Modifier.weight(1f), "supplier_kpi_cash_purchases")
        }
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, GeoOutlineVariant), modifier = Modifier.fillMaxWidth().testTag("supplier_net_balance_card")) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (isArabic) "صافي تغير مستحقات الموردين خلال الفترة" else "Net Supplier Payable Change", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (isArabic) "(المشتريات الآجلة - المدفوعات - المرتجعات)" else "(Credit purchases - Payments - Returns)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f))
                }
                Text(String.format(Locale.US, "%,.2f %s", netPayableMovement, currency), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp), color = if (netPayableMovement > 0) StatusRed else StatusGreen)
            }
        }
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, GeoOutlineVariant), modifier = Modifier.fillMaxWidth().testTag("supplier_statistics_chart_card")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (isArabic) "توزيع المشتريات والمدفوعات والمرتجعات" else "Purchases, Payments & Returns Breakdown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BreakdownChartTabButton(if (isArabic) "دائري" else "Donut", chartType == BreakdownChartType.DONUT, { onChartTypeSelected(BreakdownChartType.DONUT) }, Modifier.weight(1f).testTag("supplier_chart_tab_donut"))
                    BreakdownChartTabButton(if (isArabic) "عمودي" else "Column", chartType == BreakdownChartType.COLUMN, { onChartTypeSelected(BreakdownChartType.COLUMN) }, Modifier.weight(1f).testTag("supplier_chart_tab_column"))
                    BreakdownChartTabButton(if (isArabic) "مركب" else "Combo", chartType == BreakdownChartType.COMBO, { onChartTypeSelected(BreakdownChartType.COMBO) }, Modifier.weight(1f).testTag("supplier_chart_tab_combo"))
                }
                Text(if (isArabic) "💡 الرسم المحدد سيظهر ضمن تقرير PDF." else "💡 The selected chart will be included in the PDF report.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (chartType) {
                    BreakdownChartType.DONUT -> SupplierBreakdownDonut(items = chartItems, totalPercent = percent(periodCreditPurchases), isArabic = isArabic, currency = currency)
                    BreakdownChartType.COLUMN -> BreakdownColumnChart(items = chartItems, currency = currency, isArabic = isArabic)
                    BreakdownChartType.COMBO -> BreakdownComboChart(items = chartItems, currency = currency, isArabic = isArabic)
                }
            }
        }
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, GeoOutlineVariant), modifier = Modifier.fillMaxWidth().testTag("supplier_statistics_export_card")) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (isArabic) "تصدير الإحصائيات والمشاركة" else "Export & Share Statistics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val kpis = listOf(
                            (if (isArabic) "المستحقات الحالية" else "Current Payables") to String.format(Locale.US, "%,.2f %s", totalPayables, currency),
                            (if (isArabic) "المشتريات" else "Purchases") to String.format(Locale.US, "%,.2f %s", periodPurchases, currency),
                            (if (isArabic) "المدفوعات" else "Payments") to String.format(Locale.US, "%,.2f %s", periodPayments, currency),
                            (if (isArabic) "المرتجعات" else "Returns") to String.format(Locale.US, "%,.2f %s", periodReturns, currency)
                        )
                        val headers = listOf(if (isArabic) "المؤشر" else "Metric", if (isArabic) "القيمة" else "Amount", if (isArabic) "العملة" else "Currency", "")
                        val file = ReportExporter.createCachedPdf(
                            context = context,
                            fileName = "Supplier_Analytics_${System.currentTimeMillis()}.pdf",
                            title = if (isArabic) "تقرير إحصائيات الموردين" else "Supplier Analytics Report",
                            storeName = storeName,
                            subtitle = if (isArabic) "إحصائيات الموردين للفترة المحددة" else "Supplier analytics for selected period",
                            kpis = kpis,
                            headers = headers,
                            rows = reportRows.drop(1),
                            isArabic = isArabic
                        )
                        ReportExporter.shareFile(context, file, "application/pdf", if (isArabic) "تقرير إحصائيات الموردين" else "Supplier Analytics Report")
                        Toast.makeText(context, if (isArabic) "تم تجهيز تقرير PDF" else "PDF report ready", Toast.LENGTH_SHORT).show()
                    }, Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary)) { Icon(Icons.Default.PictureAsPdf, null, Modifier.size(16.dp)); Text(" PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = {
                        val csv = ReportExporter.generateReportCsv(listOf(if (isArabic) "المؤشر" else "Metric", if (isArabic) "القيمة" else "Amount", if (isArabic) "العملة" else "Currency", ""), reportRows.drop(1))
                        val file = ReportExporter.createCachedCsv(context, "Supplier_Analytics_${System.currentTimeMillis()}.csv", csv)
                        ReportExporter.shareFile(context, file, "text/csv", if (isArabic) "إحصائيات الموردين CSV" else "Supplier Analytics CSV")
                    }, Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp)) { Text("CSV", color = GeoPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = {
                        val text = reportRows.drop(1).joinToString("\n") { "${it.col1}: ${it.col2} ${it.col3}" }
                        val file = ReportExporter.createCachedTxt(context, "Supplier_Analytics_${System.currentTimeMillis()}.txt", text)
                        ReportExporter.shareFile(context, file, "text/plain", if (isArabic) "إحصائيات الموردين" else "Supplier Analytics")
                    }, Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Text(if (isArabic) "مشاركة" else "Share", color = GeoPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (suppliers.isEmpty()) Text(if (isArabic) "لا يوجد موردون نشطون" else "No active suppliers")
    }
}

@Composable
private fun SupplierKpiCard(title: String, value: String, color: Color, modifier: Modifier = Modifier, tag: String) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, color.copy(alpha = .28f)), modifier = modifier.testTag(tag)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp), color = color, maxLines = 1)
        }
    }
}

@Composable
private fun SupplierBreakdownDonut(items: List<BreakdownChartItem>, totalPercent: Int, isArabic: Boolean, currency: String) {
    val total = items.sumOf { it.amount }.coerceAtLeast(0.0)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(136.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                var start = -90f
                val sum = items.sumOf { it.amount }.coerceAtLeast(0.0001)
                items.forEach { item ->
                    val sweep = (item.amount / sum * 360f).toFloat()
                    drawArc(item.color, start, sweep, false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * .16f, cap = androidx.compose.ui.graphics.StrokeCap.Butt))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$totalPercent%", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                Text(if (isArabic) "مشتريات آجلة" else "Credit purchases", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(item.color))
                        Text(item.labelAr.takeIf { isArabic } ?: item.labelEn, modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(String.format(Locale.US, "%,.0f %s", item.amount, currency), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("${if (total > 0) (item.amount / total * 100).toInt() else 0}%", style = MaterialTheme.typography.labelSmall, color = item.color)
                    }
                }
            }
        }
    }
}

@Composable
private fun SupplierStatementContent(entries: List<SupplierLedgerEntry>, supplierName: String, startDate: String?, endDate: String?, isArabic: Boolean, currency: String) {
    var searchQuery by remember(supplierName) { mutableStateOf("") }
    var selectedFilter by remember(supplierName) { mutableStateOf("ALL") }
    val chronological = remember(entries) { entries.sortedWith(compareBy<SupplierLedgerEntry> { it.date }.thenBy { it.id }) }
    val opening = remember(chronological, startDate) { if (startDate == null) 0.0 else chronological.lastOrNull { it.date < startDate }?.runningBalance ?: 0.0 }
    val inPeriod = remember(chronological, startDate, endDate) { chronological.filter { (startDate == null || it.date >= startDate) && (endDate == null || it.date <= endDate) } }
    val visible = remember(inPeriod, searchQuery, selectedFilter) { inPeriod.filter { entry ->
        val matchesQuery = searchQuery.isBlank() || entry.description.contains(searchQuery, true) || entry.referenceId.contains(searchQuery, true) || entry.date.contains(searchQuery, true)
        val matchesType = when (selectedFilter) {
            "PURCHASE" -> entry.entryType == com.example.accounting.SupplierLedgerEntryType.PURCHASE
            "PAYMENT" -> entry.entryType == com.example.accounting.SupplierLedgerEntryType.SUPPLIER_PAYMENT
            "RETURN" -> entry.entryType == com.example.accounting.SupplierLedgerEntryType.PURCHASE_RETURN
            else -> true
        }
        matchesQuery && matchesType
    } }
    val closing = inPeriod.lastOrNull()?.runningBalance ?: opening
    val periodPurchases = inPeriod.filter { it.entryType == com.example.accounting.SupplierLedgerEntryType.PURCHASE }.sumOf { it.credit }
    val periodPayments = inPeriod.filter { it.entryType == com.example.accounting.SupplierLedgerEntryType.SUPPLIER_PAYMENT }.sumOf { it.debit }
    val periodReturns = inPeriod.filter { it.entryType == com.example.accounting.SupplierLedgerEntryType.PURCHASE_RETURN }.sumOf { it.debit }
    Column(Modifier.fillMaxSize().testTag("supplier_statement_content")) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 8.dp).testTag("supplier_statement_summary_grid"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SupplierStatementSummaryCard(if (isArabic) "إجمالي المشتريات" else "Total Purchases", "(${if (isArabic) "مشتريات الفترة" else "Purchases in period"})", String.format(Locale.US, "%,.2f %s", periodPurchases, currency), GeoPrimary, true, Modifier.weight(1f), "supplier_statement_purchases")
                SupplierStatementSummaryCard(if (isArabic) "المستحق للمورد" else "Payable to Supplier", "(${if (isArabic) "الرصيد القائم" else "Current payable"})", String.format(Locale.US, "%,.2f %s", closing.coerceAtLeast(0.0), currency), StatusRed, true, Modifier.weight(1f), "supplier_statement_payable")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SupplierStatementSummaryCard(if (isArabic) "المدفوعات" else "Payments", "(${if (isArabic) "سداد خلال الفترة" else "Paid in period"})", String.format(Locale.US, "%,.2f %s", periodPayments, currency), StatusGreen, false, Modifier.weight(1f), "supplier_statement_payments")
                SupplierStatementSummaryCard(if (isArabic) "المرتجعات" else "Returns", "(${if (isArabic) "مرتجعات الفترة" else "Returns in period"})", String.format(Locale.US, "%,.2f %s", periodReturns, currency), StatusBlue, false, Modifier.weight(1f), "supplier_statement_returns")
            }
        }
        HorizontalDivider(color = GeoOutlineVariant)
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth().testTag("supplier_statement_search"), singleLine = true, label = { Text(if (isArabic) "بحث في الحركات (الوصف أو المرجع أو التاريخ)" else "Search movements (description, reference or date)") }, shape = RoundedCornerShape(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to (if (isArabic) "الكل" else "All"), "PAYMENT" to (if (isArabic) "تسديد" else "Payments"), "PURCHASE" to (if (isArabic) "مشتريات" else "Purchases"), "RETURN" to (if (isArabic) "مرتجعات" else "Returns")).forEach { (key, label) ->
                    FilterChip(selected = selectedFilter == key, onClick = { selectedFilter = key }, label = { Text(label) })
                }
            }
        }
        HorizontalDivider(color = GeoOutlineVariant)
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text(if (isArabic) "لا توجد حركات مطابقة" else "No matching movements", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (startDate != null) SupplierStatementBalanceCard(if (isArabic) "الرصيد الافتتاحي" else "Opening Balance", opening, currency, isArabic, "supplier_statement_opening")
                visible.forEach { entry ->
                    val purchase = entry.entryType == com.example.accounting.SupplierLedgerEntryType.PURCHASE
                    val payment = entry.entryType == com.example.accounting.SupplierLedgerEntryType.SUPPLIER_PAYMENT
                    val returnEntry = entry.entryType == com.example.accounting.SupplierLedgerEntryType.PURCHASE_RETURN
                    val amount = if (purchase) entry.credit else entry.debit
                    val amountColor = if (purchase) StatusRed else if (payment) StatusGreen else if (returnEntry) StatusBlue else MaterialTheme.colorScheme.onSurface
                    val bg = if (purchase) StatusRedBg else if (payment) StatusGreenBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, amountColor.copy(alpha = .24f)), modifier = Modifier.fillMaxWidth().testTag("supplier_statement_row_${entry.id}")) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Surface(color = bg, shape = RoundedCornerShape(6.dp)) { Text(when { purchase -> if (isArabic) "مشتريات" else "Purchase"; payment -> if (isArabic) "تسديد" else "Payment"; returnEntry -> if (isArabic) "مرتجع" else "Return"; else -> if (isArabic) "تسوية" else "Adjustment" }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = amountColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                            }
                            Text(entry.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            HorizontalDivider(color = GeoOutlineVariant.copy(alpha = .5f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column { Text(if (isArabic) "المبلغ" else "Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(String.format(Locale.US, "%s%,.2f %s", if (purchase) "+" else "−", amount, currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = amountColor) }
                                Column(horizontalAlignment = Alignment.End) { Text(if (isArabic) "الرصيد التراكمي" else "Running Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(String.format(Locale.US, "%,.2f %s", entry.runningBalance, currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = if (entry.runningBalance > 0.0001) StatusRed else if (entry.runningBalance < -0.0001) StatusBlue else StatusGreen) }
                            }
                        }
                    }
                }
                SupplierStatementBalanceCard(if (isArabic) "الرصيد الختامي" else "Closing Balance", closing, currency, isArabic, "supplier_statement_closing")
            }
        }
    }
}

@Composable
private fun SupplierStatementSummaryCard(title: String, subtitle: String, value: String, color: Color, large: Boolean, modifier: Modifier, tag: String) {
    Card(shape = RoundedCornerShape(if (large) 12.dp else 10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(if (large) 1.5.dp else 1.dp, color.copy(alpha = if (large) .35f else .2f)), modifier = modifier.testTag(tag)) {
        Column(Modifier.fillMaxWidth().padding(if (large) 12.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(if (large) 3.dp else 2.dp)) {
            Text(title, style = if (large) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp) else MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(value, style = if (large) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = color, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(fontSize = if (large) 10.sp else 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f), maxLines = 1)
        }
    }
}

@Composable
private fun SupplierStatementBalanceCard(title: String, balance: Double, currency: String, isArabic: Boolean, tag: String) {
    val color = if (balance > .0001) StatusRed else if (balance < -.0001) StatusBlue else StatusGreen
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, color.copy(alpha = .28f)), modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(String.format(Locale.US, "%,.2f %s", balance, currency), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = color)
        }
    }
}
