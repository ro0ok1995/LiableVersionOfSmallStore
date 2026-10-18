package com.example.ui.screens

import com.example.ui.components.ReversalConfirmationDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.model.LanguageMode
import com.example.model.OperationStatus
import com.example.model.PeriodFilter
import com.example.model.RefundRequest
import com.example.model.SaleReturnLineRequest
import com.example.model.SettlementType
import com.example.model.StoreStrings
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.Supplier
import com.example.data.db.Purchase
import com.example.data.db.SupplierPayment
import com.example.data.db.PurchaseReturn
import com.example.accounting.SupplierBalanceSummary
import com.example.accounting.CustomerLedgerCalculator
import com.example.ui.components.RecordSaleReturnDialog
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.model.typedOperationStatus
import com.example.model.typedTransactionType
import com.example.accounting.FinancialReportCalculator
import com.example.ui.components.CustomerSearchField
import com.example.ui.components.SimpleEmptyState
import com.example.ui.components.StoreDebtAgingSummaryCard
import com.example.ui.theme.statusAmber
import com.example.ui.theme.statusBlue
import com.example.ui.theme.statusGreen
import com.example.ui.theme.statusGreenContainer
import com.example.ui.theme.statusRed
import com.example.ui.theme.statusRedContainer
import com.example.viewmodel.DateFilterUtils
import com.example.viewmodel.DebtAgingUtils
import com.example.viewmodel.StoreDebtAgingSummary
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    totalBalance: Double,
    totalDebt: Double,
    transactionsCount: Int,
    transactions: List<TransactionItem>,
    matchingCustomers: List<CustomerAccount>,
    selectedCustomer: CustomerAccount?,
    searchQuery: String,
    selectedPeriod: PeriodFilter,
    languageMode: LanguageMode,
    onSearchQueryChange: (String) -> Unit,
    onSelectCustomer: (CustomerAccount) -> Unit,
    onClearSelectedCustomer: () -> Unit,
    onSelectPeriod: (PeriodFilter) -> Unit,
    selectedDashboardTab: Int = 0,
    onDashboardTabChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    allCustomers: List<CustomerAccount> = matchingCustomers,
    allTransactions: List<TransactionItem> = transactions,
    authoritativeCustomerBalances: Map<String, Double>? = null,
    customStartDate: LocalDate? = null,
    customEndDate: LocalDate? = null,
    showCustomDatePicker: Boolean = false,
    onOpenCustomDatePicker: () -> Unit = {},
    onDismissCustomDatePicker: () -> Unit = {},
    onSetCustomDateRange: (LocalDate?, LocalDate?) -> Unit = { _, _ -> },
    onActivityClick: ((TransactionItem) -> Unit)? = null,
    onNavigateToCustomerProfile: ((CustomerAccount) -> Unit)? = null,
    onNavigateToSupplierProfile: ((Supplier) -> Unit)? = null,
    onNavigateToSuppliers: (() -> Unit)? = null,
    onViewAllTransactions: ((PeriodFilter, LocalDate?, LocalDate?) -> Unit)? = null,
    onReverseTransaction: ((TransactionItem, String, String) -> Unit)? = null,
    onReturnTransaction: ((TransactionItem, List<SaleReturnLineRequest>, String, String, RefundRequest?) -> Unit)? = null,
    onLoadReturnDetails: (suspend (String) -> Triple<Sale?, List<SaleLine>, Map<String, Int>>)? = null,
    suppliers: List<Supplier> = emptyList(),
    supplierPurchases: List<Purchase> = emptyList(),
    supplierPayments: List<SupplierPayment> = emptyList(),
    supplierReturns: List<PurchaseReturn> = emptyList(),
    onGetSupplierBalance: (suspend (String) -> SupplierBalanceSummary)? = null
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val currency = AppCurrency.SYMBOL
    val focusManager = LocalFocusManager.current
    var transactionToReverse by remember { mutableStateOf<TransactionItem?>(null) }
    var transactionToReturn by remember { mutableStateOf<TransactionItem?>(null) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showOverdueCustomersDialog by remember { mutableStateOf(false) }
    var selectedSupplier by remember { mutableStateOf<Supplier?>(null) }
    var supplierSearchQuery by remember { mutableStateOf("") }
    var showSupplierPayablesDialog by remember { mutableStateOf(false) }
    var showSupplierAdvancesDialog by remember { mutableStateOf(false) }


    // CURRENT BALANCES (Never filtered by period):
    // 1. Customer Receivables & Debt Aging
    val storeAgingSummary = remember(allCustomers, allTransactions, isArabic, authoritativeCustomerBalances) {
        DebtAgingUtils.calculateStoreDebtAgingSummary(
            customers = allCustomers.ifEmpty { matchingCustomers },
            allTransactions = allTransactions,
            isArabic = isArabic,
            authoritativeBalances = authoritativeCustomerBalances.orEmpty()
        )
    }

    // Dashboard headline indicators remain store-wide and prefer persistent balances including opening balances/adjustments.
    val customerReceivables = if (authoritativeCustomerBalances != null) {
        authoritativeCustomerBalances.values.sumOf { it.coerceAtLeast(0.0) }
    } else if (allCustomers.isNotEmpty()) {
        allCustomers.sumOf { customer ->
            CustomerLedgerCalculator.calculateCustomerBalance(customer.id, allTransactions).balance.coerceAtLeast(0.0)
        }
    } else totalDebt.coerceAtLeast(0.0)

    val customersWithDebtCount = storeAgingSummary.totalCustomersWithDebtCount

    // Overdue Receivables (Aging > 30 days)
    val overdueReceivables = storeAgingSummary.sum31To60 + storeAgingSummary.sum61To90 + storeAgingSummary.sum90Plus
    val overdueCustomersCount = storeAgingSummary.customerAgings.count { (it.bucket31To60 + it.bucket61To90 + it.bucket90Plus) > 0.001 }

    // Supplier payables use the same authoritative ledger calculator as the Supplier Profile.
    // This avoids reconstructing balances from generic transaction labels or customerId.
    var supplierPayables by remember { mutableStateOf<Double?>(null) }
    var supplierAdvances by remember { mutableStateOf<Double?>(null) }
    var supplierBalancesById by remember { mutableStateOf<Map<String, Double>?>(null) }
    LaunchedEffect(suppliers, supplierPurchases, supplierPayments, supplierReturns, onGetSupplierBalance) {
        val activeSuppliers = suppliers.filter { !it.isArchived }
        if (onGetSupplierBalance == null) {
            // Do not show an incomplete fallback: it omits opening balances and adjustments.
            supplierPayables = null
            supplierAdvances = null
            supplierBalancesById = null
        } else {
            val summaries = activeSuppliers.map { supplier ->
                runCatching { onGetSupplierBalance(supplier.id) }.getOrNull()
            }
            if (summaries.any { it == null }) {
                // Avoid silently presenting a partial/incorrect total when any supplier ledger fails.
                supplierPayables = null
                supplierAdvances = null
                supplierBalancesById = null
            } else {
                val byId = activeSuppliers.mapIndexed { index, supplier -> supplier.id to summaries[index]!!.balance }.toMap()
                supplierBalancesById = byId
                val balances = byId.values
                supplierPayables = balances.sumOf { it.coerceAtLeast(0.0) }
                supplierAdvances = balances.sumOf { (-it).coerceAtLeast(0.0) }
            }
        }
    }
    val periodSupplierPurchasesList = remember(supplierPurchases, selectedPeriod, customStartDate, customEndDate) {
        supplierPurchases.filter { purchase ->
            purchase.status != "REVERSED" && DateFilterUtils.isDateInPeriod(
                dateStr = purchase.purchaseDate,
                period = selectedPeriod,
                customStartDate = customStartDate,
                customEndDate = customEndDate
            )
        }
    }
    val periodSupplierPurchases = remember(periodSupplierPurchasesList) {
        periodSupplierPurchasesList.sumOf { it.totalAmount }
    }
    val periodInvoiceCount = remember(periodSupplierPurchasesList) {
        periodSupplierPurchasesList.size
    }
    val periodSupplierPayments = remember(supplierPayments, selectedPeriod, customStartDate, customEndDate) {
        supplierPayments.filter { payment ->
            payment.status != "REVERSED" && DateFilterUtils.isDateInPeriod(
                dateStr = payment.paymentDate,
                period = selectedPeriod,
                customStartDate = customStartDate,
                customEndDate = customEndDate
            )
        }.sumOf { it.amount }
    }
    val periodSupplierReturns = remember(supplierReturns, selectedPeriod, customStartDate, customEndDate) {
        supplierReturns.filter { purchaseReturn ->
            purchaseReturn.status != "REVERSED" && DateFilterUtils.isDateInPeriod(
                dateStr = purchaseReturn.returnDate,
                period = selectedPeriod,
                customStartDate = customStartDate,
                customEndDate = customEndDate
            )
        }.sumOf { it.amount }
    }

    // Store-wide period data drives KPIs. Search/selected customer only filters the activity list.
    val allPeriodTransactions = remember(transactions, selectedPeriod, customStartDate, customEndDate) {
        transactions.filter { tx ->
            DateFilterUtils.isDateInPeriod(tx.date, selectedPeriod, customStartDate, customEndDate)
        }
    }
    val periodTransactions = remember(allPeriodTransactions, selectedCustomer, selectedDashboardTab) {
        if (selectedDashboardTab == 0 && selectedCustomer != null) allPeriodTransactions.filter { it.customerId == selectedCustomer.id }
        else allPeriodTransactions
    }

    // Period KPI metrics always use the store-wide period, never the search scope.
    val periodSalesTxs = remember(allPeriodTransactions) {
        periodTransactions.filter { 
            it.typedOperationStatus != OperationStatus.REVERSED && 
            it.typedTransactionType == TransactionType.SALE 
        }
    }
    val totalPeriodSales = periodSalesTxs.sumOf { it.amount }
    val periodSalesCount = periodSalesTxs.size
    val periodSaleReturns = remember(allPeriodTransactions) {
        allPeriodTransactions.filter { 
            it.typedOperationStatus != OperationStatus.REVERSED && 
            it.typedTransactionType == TransactionType.SALE_RETURN 
        }.sumOf { it.amount }
    }
    val totalPeriodNetSales = (totalPeriodSales - periodSaleReturns).coerceAtLeast(0.0)

    val totalPeriodCollections = remember(allPeriodTransactions) {
        allPeriodTransactions.filter { 
            it.typedOperationStatus != OperationStatus.REVERSED && 
            it.typedTransactionType == TransactionType.CUSTOMER_PAYMENT 
        }.sumOf { it.amount }
    }

    val totalPeriodExpenses = remember(allPeriodTransactions) {
        allPeriodTransactions.filter { 
            it.typedOperationStatus != OperationStatus.REVERSED && 
            it.typedTransactionType == TransactionType.EXPENSE 
        }.sumOf { it.amount }
    }

    val totalPeriodCreditSales = remember(periodSalesTxs) {
        periodSalesTxs.sumOf { it.creditAmount }
    }

    val totalPeriodPurchases = remember(allPeriodTransactions) {
        allPeriodTransactions.filter { 
            it.typedOperationStatus != OperationStatus.REVERSED && 
            it.typedTransactionType == TransactionType.PURCHASE 
        }.sumOf { it.amount }
    }

    // Authoritative Supplier Period Activities (from actual purchases, supplier payments, and purchase returns)
    val supplierPeriodActivities = remember(
        supplierPurchases,
        supplierPayments,
        supplierReturns,
        suppliers,
        selectedPeriod,
        customStartDate,
        customEndDate,
        isArabic
    ) {
        val supplierMap = suppliers.associateBy { it.id }

        val purchaseItems = supplierPurchases.filter { purchase ->
            DateFilterUtils.isDateInPeriod(
                dateStr = purchase.purchaseDate,
                period = selectedPeriod,
                customStartDate = customStartDate,
                customEndDate = customEndDate
            )
        }.map { purchase ->
            val sup = supplierMap[purchase.supplierId]
            val supName = sup?.name ?: ""
            val invoiceSuffix = if (purchase.invoiceNumber.isNotBlank()) " ${purchase.invoiceNumber}" else ""
            TransactionItem(
                id = purchase.id,
                title = if (isArabic) "فاتورة مشتريات$invoiceSuffix" else "Purchase Invoice$invoiceSuffix",
                customerNameSnapshot = supName,
                activityType = if (isArabic) "فاتورة مشتريات" else "Purchase",
                amount = purchase.totalAmount,
                isCredit = purchase.creditAmount > 0.0,
                date = purchase.purchaseDate,
                relativeTime = "الآن",
                notes = purchase.notes ?: (if (isArabic) "مشتريات من مورد" else "Purchase from supplier"),
                customerId = purchase.supplierId,
                isArchived = false,
                archivedDate = null,
                customerName = supName,
                transactionType = TransactionType.PURCHASE,
                saleType = null,
                paymentStatus = purchase.typedPaymentStatus,
                operationStatus = purchase.typedOperationStatus,
                paidAmount = purchase.paidAmount,
                creditAmount = purchase.creditAmount
            )
        }

        val paymentItems = supplierPayments.filter { payment ->
            DateFilterUtils.isDateInPeriod(
                dateStr = payment.paymentDate,
                period = selectedPeriod,
                customStartDate = customStartDate,
                customEndDate = customEndDate
            )
        }.map { payment ->
            val sup = supplierMap[payment.supplierId]
            val supName = sup?.name ?: ""
            TransactionItem(
                id = payment.id,
                title = if (isArabic) "سداد مورد: $supName" else "Supplier Payment: $supName",
                customerNameSnapshot = supName,
                activityType = if (isArabic) "سداد مورد" else "Supplier Payment",
                amount = payment.amount,
                isCredit = false,
                date = payment.paymentDate,
                relativeTime = "الآن",
                notes = payment.notes ?: (if (isArabic) "سداد للمورد" else "Payment to supplier"),
                customerId = payment.supplierId,
                isArchived = false,
                archivedDate = null,
                customerName = supName,
                transactionType = TransactionType.SUPPLIER_PAYMENT,
                saleType = null,
                paymentStatus = null,
                operationStatus = payment.typedOperationStatus,
                paidAmount = payment.amount,
                creditAmount = 0.0
            )
        }

        val returnItems = supplierReturns.filter { ret ->
            DateFilterUtils.isDateInPeriod(
                dateStr = ret.returnDate,
                period = selectedPeriod,
                customStartDate = customStartDate,
                customEndDate = customEndDate
            )
        }.map { ret ->
            val sup = supplierMap[ret.supplierId]
            val supName = sup?.name ?: ""
            TransactionItem(
                id = ret.id,
                title = if (isArabic) "مرتجع مشتريات" else "Purchase Return",
                customerNameSnapshot = supName,
                activityType = if (isArabic) "مرتجع مشتريات" else "Purchase Return",
                amount = ret.amount,
                isCredit = false,
                date = ret.returnDate,
                relativeTime = "الآن",
                notes = ret.reason ?: (if (isArabic) "مرتجع بضاعة لمورد" else "Return to supplier"),
                customerId = ret.supplierId,
                isArchived = false,
                archivedDate = null,
                customerName = supName,
                transactionType = TransactionType.PURCHASE_RETURN,
                saleType = null,
                paymentStatus = null,
                operationStatus = ret.typedOperationStatus,
                paidAmount = 0.0,
                creditAmount = 0.0
            )
        }

        (purchaseItems + paymentItems + returnItems)
            .distinctBy { it.id }
            .sortedWith(compareByDescending<TransactionItem> { it.date }.thenByDescending { it.id })
    }

    // Recent store activities (top 6 recent items, filtered by dashboard tab and tab-specific search query)
    val recentActivities = remember(
        selectedDashboardTab,
        periodTransactions,
        searchQuery,
        allCustomers,
        matchingCustomers,
        supplierPeriodActivities,
        supplierSearchQuery,
        selectedSupplier,
        suppliers
    ) {
        if (selectedDashboardTab == 1) {
            // SUPPLIER DASHBOARD: Real supplier purchases, payments, and returns
            val trimmedSupplierQuery = supplierSearchQuery.trim()
            val filteredBySelectedSupplier = if (selectedSupplier != null) {
                supplierPeriodActivities.filter { it.customerId == selectedSupplier?.id }
            } else if (trimmedSupplierQuery.isNotBlank()) {
                val matchingSupplierIds = suppliers
                    .filter { it.name.contains(trimmedSupplierQuery, ignoreCase = true) || it.phone.contains(trimmedSupplierQuery) }
                    .map { it.id }
                    .toSet()
                supplierPeriodActivities.filter { tx ->
                    (tx.customerId != null && tx.customerId in matchingSupplierIds) ||
                    tx.customerNameSnapshot.contains(trimmedSupplierQuery, ignoreCase = true) ||
                    tx.title.contains(trimmedSupplierQuery, ignoreCase = true)
                }
            } else {
                supplierPeriodActivities
            }
            filteredBySelectedSupplier.sortedWith(compareByDescending<TransactionItem> { it.date }.thenByDescending { it.id }).take(30)
        } else {
            // CUSTOMER DASHBOARD: Sales, customer payments, sale returns
            val trimmedQuery = searchQuery.trim()
            val customerTxs = periodTransactions.filter { tx ->
                val isSupplierTx = when (tx.typedTransactionType) {
                    TransactionType.PURCHASE,
                    TransactionType.SUPPLIER_PAYMENT,
                    TransactionType.PURCHASE_RETURN -> true
                    TransactionType.EXPENSE -> false
                    TransactionType.SALE,
                    TransactionType.CUSTOMER_PAYMENT,
                    TransactionType.SALE_RETURN -> false
                    else -> tx.activityType.contains("مورد")
                }
                !isSupplierTx
            }

            val filtered = if (trimmedQuery.isBlank()) {
                customerTxs
            } else {
                val customerPool = allCustomers.ifEmpty { matchingCustomers }
                val matchingCustomerIds = customerPool
                    .filter { it.customerName.contains(trimmedQuery, ignoreCase = true) || it.phone.contains(trimmedQuery) }
                    .map { it.id }
                    .toSet()

                customerTxs.filter { tx ->
                    (tx.customerId != null && tx.customerId in matchingCustomerIds) ||
                    tx.customerNameSnapshot.contains(trimmedQuery, ignoreCase = true)
                }
            }
            filtered.sortedWith(compareByDescending<TransactionItem> { it.date }.thenByDescending { it.id }).take(30)
        }
    }

    // Sales Trend by day in period (visual bar trend)
    val salesTrendPoints = remember(periodSalesTxs) {
        if (periodSalesTxs.isEmpty()) emptyList()
        else {
            periodSalesTxs
                .groupBy { if (it.date.length >= 10) it.date.substring(0, 10) else it.date }
                .map { (date, txs) -> Pair(date, txs.sumOf { it.amount }) }
                .sortedBy { it.first }
                .takeLast(7)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_container"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. HEADER
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("home_fixed_top_section")
            ) {
                Text(
                    text = if (isArabic) "لوحة التحكم" else "Dashboard",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isArabic) "نظرة عامة على نشاط المتجر وأرصدته" else "Store activity and balance overview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. PERIOD SELECTOR (Activity scope controller)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("period_selector_row"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PeriodChip(
                        label = if (isArabic) StoreStrings.PERIOD_ALL_AR else StoreStrings.PERIOD_ALL_EN,
                        isSelected = selectedPeriod == PeriodFilter.ALL,
                        testTag = "period_all",
                        onClick = { onSelectPeriod(PeriodFilter.ALL) },
                        modifier = Modifier.weight(1f)
                    )
                    PeriodChip(
                        label = if (isArabic) StoreStrings.PERIOD_TODAY_AR else StoreStrings.PERIOD_TODAY_EN,
                        isSelected = selectedPeriod == PeriodFilter.TODAY,
                        testTag = "period_today",
                        onClick = { onSelectPeriod(PeriodFilter.TODAY) },
                        modifier = Modifier.weight(1f)
                    )
                    PeriodChip(
                        label = if (isArabic) StoreStrings.PERIOD_MONTH_AR else StoreStrings.PERIOD_MONTH_EN,
                        isSelected = selectedPeriod == PeriodFilter.MONTH,
                        testTag = "period_month",
                        onClick = { onSelectPeriod(PeriodFilter.MONTH) },
                        modifier = Modifier.weight(1f)
                    )
                    PeriodChip(
                        label = if (isArabic) StoreStrings.PERIOD_CUSTOM_AR else StoreStrings.PERIOD_CUSTOM_EN,
                        isSelected = selectedPeriod == PeriodFilter.CUSTOM,
                        testTag = "period_custom",
                        onClick = { onSelectPeriod(PeriodFilter.CUSTOM) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (selectedPeriod == PeriodFilter.CUSTOM) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenCustomDatePicker() }
                            .testTag("home_custom_date_range_display")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
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
                                    modifier = Modifier.testTag("home_custom_date_range_text")
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2B. DASHBOARD TAB SELECTOR (Customer vs Supplier)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("dashboard_tab_selector_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PeriodChip(
                    label = if (isArabic) "لوحة العملاء" else "Customer Dashboard",
                    isSelected = selectedDashboardTab == 0,
                    testTag = "dashboard_tab_customer",
                    onClick = { onDashboardTabChange(0) },
                    modifier = Modifier.weight(1f)
                )
                PeriodChip(
                    label = if (isArabic) "لوحة الموردين" else "Supplier Dashboard",
                    isSelected = selectedDashboardTab == 1,
                    testTag = "dashboard_tab_supplier",
                    onClick = { onDashboardTabChange(1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. PERIOD FLOW KPIS (2x2 Grid)
        if (selectedDashboardTab == 0) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "نشاط الفترة المحددة" else "Selected Period Flow",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // KPI 1: Sales (Net sales subtracting returns for accounting parity)
                        KpiCard(
                            title = if (isArabic) "صافي المبيعات" else "Net Sales",
                            subtitle = if (periodSaleReturns > 0.001) {
                                if (isArabic) "بعد مرتجعات: ${String.format(Locale.US, "%,.2f %s", periodSaleReturns, currency)}" else "After returns: ${String.format(Locale.US, "%,.2f %s", periodSaleReturns, currency)}"
                            } else if (periodSalesCount > 0) "$periodSalesCount ${if (isArabic) "عملية" else "txs"}" else null,
                            amount = totalPeriodNetSales,
                            currency = currency,
                            valueColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surface,
                            testTag = "kpi_sales",
                            helpText = if (isArabic) "صافي المبيعات = إجمالي المبيعات النشطة − مرتجعات المبيعات النشطة خلال الفترة. مثال: 1,000 − 100 = 900. لا تدخل العمليات الملغاة." else "Net sales = active sales − active sales returns in the selected period. Example: 1,000 − 100 = 900. Reversed operations are excluded.",
                            modifier = Modifier.weight(1f)
                        )
                        // KPI 2: Collections
                        KpiCard(
                            title = if (isArabic) "تحصيلات العملاء" else "Customer Collections",
                            subtitle = if (isArabic) "سداد العملاء" else "Customer payments",
                            amount = totalPeriodCollections,
                            currency = currency,
                            valueColor = MaterialTheme.colorScheme.statusGreen,
                            containerColor = MaterialTheme.colorScheme.surface,
                            testTag = "kpi_collections",
                            helpText = if (isArabic) "مجموع دفعات العملاء المسجلة كتحصيلات خلال الفترة، بجميع وسائل الدفع المعتمدة، بعد استبعاد الملغى. مثال: دفعتان 100 و50 = 150. يجب ألا يُجمع سجل الدفعة مع سجل المعاملة نفسه مرتين." else "Sum of customer-payment transactions in the selected period across supported payment methods, excluding reversed entries. Example: 100 + 50 = 150. A payment and its mirrored transaction must not be counted twice.",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // KPI 3: Expenses
                        KpiCard(
                            title = if (isArabic) "المصروفات" else "Expenses",
                            subtitle = if (isArabic) "مصاريف تشغيلية" else "Operating costs",
                            amount = totalPeriodExpenses,
                            currency = currency,
                            valueColor = MaterialTheme.colorScheme.error,
                            containerColor = MaterialTheme.colorScheme.surface,
                            testTag = "kpi_expenses",
                            helpText = if (isArabic) "مجموع المصروفات غير الملغاة وتاريخها داخل الفترة المحددة. مثال: مصروف 30 + مصروف 20 = 50." else "Sum of non-reversed expenses dated within the selected period. Example: 30 + 20 = 50.",
                            modifier = Modifier.weight(1f)
                        )
                        // KPI 4: Credit Sales
                        KpiCard(
                            title = if (isArabic) "مبيعات بالدين" else "Credit Sales",
                            subtitle = if (isArabic) "آجل غير مسدد" else "Deferred sales",
                            amount = totalPeriodCreditSales,
                            currency = currency,
                            valueColor = MaterialTheme.colorScheme.statusAmber,
                            containerColor = MaterialTheme.colorScheme.surface,
                            testTag = "kpi_credit_sales",
                            helpText = if (isArabic) "مجموع الجزء الآجل (creditAmount) من المبيعات النشطة في الفترة، وليس إجمالي الفاتورة المختلطة. مثال: فاتورة 500، المدفوع 300، الآجل 200؛ المحتسب 200." else "Sum of the creditAmount portion of active sales in the period, not the full mixed invoice. Example: 500 invoice − 300 paid = 200 credit.",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4 & 5. CURRENT BALANCES: CUSTOMER RECEIVABLES & OVERDUE WARNING
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isArabic) "الأرصدة والمستحقات الحالية (لحظياً)" else "Current Outstanding Balances",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_customer_receivables_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column {
                                    Text(
                                        text = if (isArabic) "مستحقات العملاء (الديون لك)" else "Customer Receivables",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = String.format(Locale.US, "%,.2f %s", customerReceivables, currency),
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 24.sp
                                        ),
                                        color = if (customerReceivables > 0) MaterialTheme.colorScheme.statusGreen else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("stat_total_debt")
                                    )
                                }
                                if (customersWithDebtCount > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "$customersWithDebtCount ${if (isArabic) "عملاء مدينين" else "debtors"}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8. NEEDS ATTENTION SECTION
            item {
                if (overdueReceivables > 0.001) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isArabic) "يحتاج انتباهك" else "Needs Attention",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.statusAmber
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.statusAmber.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showOverdueCustomersDialog = true }
                                .testTag("home_needs_attention_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.ReceiptLong,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (isArabic) "متابعة تحصيل الديون المتأخرة" else "Follow up overdue receivables",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = if (isArabic) "$overdueCustomersCount عملاء لديهم ديون متأخرة أكثر من 30 يوماً" else "$overdueCustomersCount customers have debt older than 30 days",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = String.format(Locale.US, "%,.1f %s", overdueReceivables, currency),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.statusAmber
                                )
                            }
                        }
                    }
                }
            }

            // 9. SALES TREND SECTION (Visual trend in period)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isArabic) "مؤشر حركة المبيعات بالفترة" else "Sales Period Trend",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (salesTrendPoints.size < 2) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (periodSalesTxs.isEmpty()) {
                                        if (isArabic) "لا توجد مبيعات في الفترة المحددة لعرض المؤشر." else "No sales in selected period to plot trend."
                                    } else {
                                        if (isArabic) "تم تسجيل ${periodSalesTxs.size} عملية بيع بإجمالي ${String.format(Locale.US, "%,.2f %s", totalPeriodSales, currency)} في يوم واحد." else "Single day sales recorded: ${String.format(Locale.US, "%,.2f %s", totalPeriodSales, currency)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val maxPointAmount = salesTrendPoints.maxOf { it.second }.coerceAtLeast(1.0)
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    salesTrendPoints.forEach { point ->
                                        val barRatio = (point.second / maxPointAmount).toFloat().coerceIn(0.08f, 1f)
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = String.format(Locale.US, "%.0f", point.second),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.55f)
                                                    .height((60 * barRatio).dp)
                                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val dayLabel = if (point.first.length >= 10) point.first.substring(5) else point.first
                                            Text(
                                                text = dayLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // SUPPLIER DASHBOARD: 2x2 Grid of Period Flow & Outstanding Debts
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "نشاط الفترة المحددة" else "Selected Period Flow",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Row 1: Right = Purchases This Period, Left = Supplier Payables (in red)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val purchasesCard = @Composable {
                            KpiCard(
                                title = if (isArabic) "المشتريات" else "Purchases",
                                subtitle = if (periodInvoiceCount > 0) "$periodInvoiceCount ${if (isArabic) "فاتورة" else "invoices"}" else null,
                                amount = periodSupplierPurchases,
                                currency = currency,
                                valueColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surface,
                                testTag = "home_purchases_period_card",
                                helpText = if (isArabic) "مجموع إجمالي فواتير المشتريات المسجلة في الفترة، مع استبعاد الفواتير الملغاة. هذا إجمالي مشتريات وليس مقدار الدين وحده." else "Sum of purchase invoice totals dated in this period, excluding reversed invoices. This is gross purchases, not payable debt only.",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        val payablesCard = @Composable {
                            KpiCard(
                                title = if (isArabic) "مستحقات الموردين" else "Supplier Payables",
                                subtitle = if (isArabic) "التزامات مستحقة" else "Outstanding obligations",
                                amount = supplierPayables ?: 0.0,
                                currency = currency,
                                valueText = supplierPayables?.let { String.format(Locale.US, "%,.2f %s", it, currency) } ?: if (isArabic) "غير متاح" else "Unavailable",
                                valueColor = MaterialTheme.colorScheme.error,
                                containerColor = MaterialTheme.colorScheme.surface,
                                testTag = "kpi_supplier_payables",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (isArabic) {
                            // In Arabic (RTL): first child is Right, second is Left
                            purchasesCard()
                            payablesCard()
                        } else {
                            // In English (LTR): first child is Left, second is Right
                            payablesCard()
                            purchasesCard()
                        }
                    }

                    // Row 2: Right = Invoice Count for This Period, Left = Supplier Payments This Period
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val returnsCard = @Composable {
                            KpiCard(
                                title = if (isArabic) "مرتجعات المشتريات" else "Purchase Returns",
                                subtitle = if (isArabic) "مرتجعات الفترة" else "Returns in period",
                                amount = periodSupplierReturns,
                                currency = currency,
                                valueColor = MaterialTheme.colorScheme.tertiary,
                                containerColor = MaterialTheme.colorScheme.surface,
                                testTag = "home_supplier_returns_period_card",
                                helpText = if (isArabic) "مجموع مرتجعات المشتريات المسجلة في الفترة بعد استبعاد المرتجعات الملغاة. أثرها على المستحقات يعتمد على تسوية المرتجع الأصلي." else "Sum of purchase returns recorded in this period, excluding reversed returns. Their payable effect depends on the original purchase settlement.",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        val paymentsCard = @Composable {
                            KpiCard(
                                title = if (isArabic) "مدفوعات الموردين" else "Supplier Payments",
                                subtitle = if (isArabic) "سداد بالفترة" else "Paid in period",
                                amount = periodSupplierPayments,
                                currency = currency,
                                valueColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surface,
                                testTag = "home_supplier_payments_period_card",
                                helpText = if (isArabic) "مجموع دفعات الموردين المسجلة في الفترة بعد استبعاد الدفعات الملغاة." else "Sum of supplier payments recorded in this period, excluding reversed payments.",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (isArabic) {
                            // In Arabic (RTL): first child is Right, second is Left
                            returnsCard()
                            paymentsCard()
                        } else {
                            // In English (LTR): first child is Left, second is Right
                            paymentsCard()
                            returnsCard()
                        }
                    }
                }
            }

            // Current supplier balances are separate and drill down to the exact supplier list.
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isArabic) "الأرصدة والمستحقات الحالية" else "Current Supplier Balances",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Card(
                            onClick = { showSupplierPayablesDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.weight(1f).testTag("home_supplier_payables_card")
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(if (isArabic) "إجمالي ديون الموردين المستحقة على المحل" else "Supplier Debts Owed by Store", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(5.dp))
                                Text(supplierPayables?.let { String.format(Locale.US, "%,.2f %s", it, currency) } ?: if (isArabic) "غير متاح" else "Unavailable", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("stat_total_supplier_debt"))
                                Text(if (isArabic) "اضغط لعرض الموردين والمبالغ" else "Tap to list suppliers and amounts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Card(
                            onClick = { showSupplierAdvancesDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.weight(1f).testTag("home_supplier_advances_card")
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(if (isArabic) "الأرصدة المقدمة للموردين" else "Supplier Advances", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(5.dp))
                                Text(supplierAdvances?.let { String.format(Locale.US, "%,.2f %s", it, currency) } ?: if (isArabic) "غير متاح" else "Unavailable", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.statusGreen)
                                Text(if (isArabic) "اضغط لعرض الأرصدة المقدمة" else "Tap to list advances", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // 10. RECENT ACTIVITIES
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isArabic) "نشاطات المتجر خلال الفترة المحددة" else "Store Activity in Selected Period",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val totalEntriesCount = if (selectedDashboardTab == 1) supplierPeriodActivities.size else periodTransactions.size
                        Text(
                            text = if (isArabic) "يعرض ${recentActivities.size} من $totalEntriesCount عملية في الفترة" else "Showing ${recentActivities.size} of $totalEntriesCount entries in period",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (selectedDashboardTab == 0 && selectedCustomer != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.testTag("home_active_customer_filter_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedCustomer.customerName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = onClearSelectedCustomer,
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = if (isArabic) "إلغاء التصفية" else "Clear filter",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    } else if (selectedDashboardTab == 1 && selectedSupplier != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.testTag("home_active_supplier_filter_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedSupplier!!.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { selectedSupplier = null },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = if (isArabic) "إلغاء التصفية" else "Clear filter",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Search Bar immediately below section title and above activity items
                if (selectedDashboardTab == 0) {
                    // Customer Search Bar
                    CustomerSearchField(
                        customers = allCustomers.ifEmpty { matchingCustomers },
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        onCustomerSelected = { customer ->
                            onSelectCustomer(customer)
                        },
                        onClearSelection = {
                            onClearSelectedCustomer()
                            onSearchQueryChange("")
                        },
                        selectedCustomerId = selectedCustomer?.id,
                        placeholderText = if (isArabic) "بحث بالاسم أو الهاتف في النشاطات..." else "Search activity by name or phone...",
                        currency = currency,
                        isArabic = isArabic,
                        inputTestTag = "customer_search_input",
                        dropdownTestTag = "customer_search_suggestions",
                        itemTagPrefix = "customer_suggestion_",
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    // Supplier Search Bar
                    SupplierSearchField(
                        suppliers = suppliers.filter { !it.isArchived },
                        searchQuery = supplierSearchQuery,
                        onSearchQueryChange = { newQuery ->
                            supplierSearchQuery = newQuery
                            if (selectedSupplier != null && selectedSupplier?.name != newQuery) {
                                selectedSupplier = null
                            }
                        },
                        onSupplierSelected = { supplier ->
                            selectedSupplier = supplier
                            supplierSearchQuery = supplier.name
                        },
                        onClearSelection = {
                            selectedSupplier = null
                            supplierSearchQuery = ""
                        },
                        selectedSupplierId = selectedSupplier?.id,
                        placeholderText = if (isArabic) "بحث عن مورد بالاسم أو الهاتف في النشاطات..." else "Search supplier activity by name or phone...",
                        isArabic = isArabic,
                        inputTestTag = "supplier_search_input",
                        dropdownTestTag = "supplier_search_suggestions",
                        itemTagPrefix = "supplier_suggestion_",
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (recentActivities.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    ) {
                        SimpleEmptyState(
                            message = if (isArabic) "لا توجد نشاطات مسجلة في هذه الفترة." else "No activities recorded in this period.",
                            icon = Icons.Default.ReceiptLong,
                            testTag = "activities_empty_state"
                        )
                    }
                } else {
                    val customerPool = remember(allCustomers, matchingCustomers) {
                        (allCustomers + matchingCustomers).distinctBy { it.id }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentActivities.forEach { tx ->
                            val isSale = if (tx.typedTransactionType != null) {
                                tx.typedTransactionType == TransactionType.SALE
                            } else {
                                tx.activityType.contains("فاتورة") || tx.activityType.contains("بيع")
                            }
                            val isReversed = tx.typedOperationStatus == OperationStatus.REVERSED
                            val isReturnEligible = !isReversed && isSale

                            val isSupplierTx = when (tx.typedTransactionType) {
                                TransactionType.PURCHASE,
                                TransactionType.SUPPLIER_PAYMENT,
                                TransactionType.PURCHASE_RETURN -> true
                                else -> tx.activityType.contains("مورد")
                            }

                            val isSupplierOrExpense = isSupplierTx || tx.typedTransactionType == TransactionType.EXPENSE || tx.activityType.contains("مصروف")

                            val rawSnapshot = tx.customerNameSnapshot.ifBlank { tx.customerName }.trim()
                            val normalizedSnapshot = normalizeCustomerName(rawSnapshot)
                            val isAnonymousSnapshot = isAnonymousCustomerName(rawSnapshot)

                            val matchedCustomer: CustomerAccount? = if (!isSupplierOrExpense && !isAnonymousSnapshot) {
                                var match: CustomerAccount? = null

                                // 1. If the activity has a valid "customerId", resolve by ID from existing customer collections
                                val candidateId = tx.customerId?.trim()
                                if (!candidateId.isNullOrEmpty()) {
                                    match = customerPool.firstOrNull {
                                        it.id.trim() == candidateId || it.id.trim().equals(candidateId, ignoreCase = true)
                                    }
                                }

                                // 2. If the ID is absent or does not resolve, fallback to existing customer-name snapshot
                                // only when it uniquely identifies one real customer
                                if (match == null && normalizedSnapshot.isNotEmpty()) {
                                    val uniqueMatches = customerPool.filter { customer ->
                                        !isAnonymousCustomerName(customer.customerName) &&
                                        normalizeCustomerName(customer.customerName) == normalizedSnapshot
                                    }
                                    if (uniqueMatches.size == 1) {
                                        match = uniqueMatches.first()
                                    }
                                }

                                // 3. Do not treat generic/anonymous customer as a real customer profile
                                if (match != null && isAnonymousCustomerName(match.customerName)) {
                                    null
                                } else {
                                    match
                                }
                            } else {
                                null
                            }

                            // Authoritative Supplier resolution by ID
                            val matchedSupplier: Supplier? = if (isSupplierTx) {
                                val candidateId = tx.customerId?.trim()
                                if (!candidateId.isNullOrEmpty()) {
                                    suppliers.firstOrNull { it.id.trim() == candidateId }
                                } else null
                            } else null

                            val customerNavigationAction: (() -> Unit)? = if (matchedCustomer != null) {
                                if (onNavigateToCustomerProfile != null) {
                                    { onNavigateToCustomerProfile(matchedCustomer) }
                                } else if (onActivityClick != null) {
                                    { onActivityClick(tx) }
                                } else null
                            } else if (!isSupplierOrExpense && !isAnonymousSnapshot && !tx.customerId.isNullOrBlank() && onActivityClick != null) {
                                // Safe fallback through existing activity navigation flow if customerId is valid
                                { onActivityClick(tx) }
                            } else null

                            val supplierNavigationAction: (() -> Unit)? = if (matchedSupplier != null) {
                                if (onNavigateToSupplierProfile != null) {
                                    { onNavigateToSupplierProfile(matchedSupplier) }
                                } else if (onActivityClick != null) {
                                    { onActivityClick(tx) }
                                } else null
                            } else if (isSupplierTx && !tx.customerId.isNullOrBlank() && onActivityClick != null) {
                                { onActivityClick(tx) }
                            } else null

                            val navigationAction = if (isSupplierTx) supplierNavigationAction else customerNavigationAction

                            ActivityRowCard(
                                transaction = tx,
                                currency = currency,
                                isArabic = isArabic,
                                onClick = navigationAction,
                                onCustomerClick = navigationAction,
                                onReverseClick = if (onReverseTransaction != null && !isReversed) {
                                    { transactionToReverse = tx }
                                } else null,
                                onReturnClick = if (onReturnTransaction != null && onLoadReturnDetails != null && isReturnEligible) {
                                    { transactionToReturn = tx }
                                } else null
                            )
                        }
                    }
                }
                Button(
                    onClick = { onViewAllTransactions?.invoke(selectedPeriod, customStartDate, customEndDate) },
                    enabled = onViewAllTransactions != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isArabic) "عرض الكل" else "View All")
                }
            }
        }
    }

        if (showSupplierPayablesDialog || showSupplierAdvancesDialog) {
            val advancesMode = showSupplierAdvancesDialog
            val targetSuppliers = suppliers.filter { supplier ->
                val balance = supplierBalancesById?.get(supplier.id)
                balance != null && if (advancesMode) balance < -0.0001 else balance > 0.0001
            }.sortedBy { it.name.lowercase(Locale.ROOT) }
            AlertDialog(
                onDismissRequest = { showSupplierPayablesDialog = false; showSupplierAdvancesDialog = false },
                title = { Text(if (advancesMode) { if (isArabic) "الأرصدة المقدمة للموردين" else "Supplier Advances" } else { if (isArabic) "ديون الموردين المستحقة" else "Supplier Payables" }) },
                text = {
                    if (supplierBalancesById == null) {
                        Text(if (isArabic) "تعذر تحميل أرصدة الموردين كاملة. أعد المحاولة لاحقًا." else "Supplier balances could not all be loaded. Please try again later.")
                    } else if (targetSuppliers.isEmpty()) {
                        Text(if (isArabic) "لا توجد أرصدة ضمن هذه القائمة." else "There are no balances in this list.")
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(targetSuppliers, key = { it.id }) { supplier ->
                                val balance = supplierBalancesById?.get(supplier.id) ?: 0.0
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        showSupplierPayablesDialog = false; showSupplierAdvancesDialog = false
                                        onNavigateToSupplierProfile?.invoke(supplier)
                                    },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(supplier.name, fontWeight = FontWeight.SemiBold)
                                            if (supplier.phone.isNotBlank()) Text(supplier.phone, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(String.format(Locale.US, "%,.2f %s", kotlin.math.abs(balance), currency), fontWeight = FontWeight.Bold, color = if (advancesMode) MaterialTheme.colorScheme.statusGreen else MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showSupplierPayablesDialog = false; showSupplierAdvancesDialog = false }) { Text(if (isArabic) "إغلاق" else "Close") } }
            )
        }


    if (showCustomDatePicker) {
        CustomDateRangePickerDialog(
            initialStartDate = customStartDate,
            initialEndDate = customEndDate,
            isArabic = isArabic,
            onConfirm = { start, end ->
                onSetCustomDateRange(start, end)
            },
            onDismiss = onDismissCustomDatePicker
        )
    }

    if (transactionToReverse != null) {
        ReversalConfirmationDialog(
            transaction = transactionToReverse!!,
            currency = currency,
            isArabic = isArabic,
            onConfirm = { reasonCode, reasonLabel ->
                onReverseTransaction?.invoke(transactionToReverse!!, reasonCode, reasonLabel)
                transactionToReverse = null
            },
            onDismiss = { transactionToReverse = null }
        )
    }

    if (transactionToReturn != null && onLoadReturnDetails != null && onReturnTransaction != null) {
        RecordSaleReturnDialog(
            transaction = transactionToReturn!!,
            currency = currency,
            isArabic = isArabic,
            onLoadDetails = onLoadReturnDetails,
            onConfirm = { lines, reasonCode, reasonLabel, refundReq ->
                onReturnTransaction(transactionToReturn!!, lines, reasonCode, reasonLabel, refundReq)
                transactionToReturn = null
            },
            onDismiss = { transactionToReturn = null }
        )
    }

    if (showOverdueCustomersDialog) {
        val overdueCustomerItems = remember(storeAgingSummary, allCustomers, matchingCustomers) {
            val customerPool = allCustomers.ifEmpty { matchingCustomers }
            storeAgingSummary.customerAgings
                .filter { (it.bucket31To60 + it.bucket61To90 + it.bucket90Plus) > 0.001 }
                .map { aging ->
                    val customer = customerPool.find { it.id == aging.customerId }
                    Triple(
                        aging.customerName,
                        aging.bucket31To60 + aging.bucket61To90 + aging.bucket90Plus,
                        customer
                    )
                }
                .sortedByDescending { it.second }
        }

        AlertDialog(
            onDismissRequest = { showOverdueCustomersDialog = false },
            title = {
                Text(
                    text = if (isArabic) "العملاء المتأخرون في السداد" else "Overdue Customers",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                if (overdueCustomerItems.isEmpty()) {
                    Text(
                        text = if (isArabic) "لا يوجد عملاء متأخرون حالياً" else "No overdue customers found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(overdueCustomerItems) { (name, balance, customer) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = customer != null) {
                                        customer?.let {
                                            showOverdueCustomersDialog = false
                                            if (onNavigateToCustomerProfile != null) {
                                                onNavigateToCustomerProfile(it)
                                            } else {
                                                onSelectCustomer(it)
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = String.format(Locale.US, "%,.2f %s", balance, currency),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOverdueCustomersDialog = false }) {
                    Text(if (isArabic) "إغلاق" else "Close")
                }
            }
        )
    }
}

@Composable
private fun KpiCard(
    title: String,
    subtitle: String?,
    amount: Double,
    currency: String,
    valueColor: Color,
    containerColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    valueText: String? = null,
    helpText: String? = null
) {
    var showHelp by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (helpText != null) {
                    IconButton(onClick = { showHelp = true }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Info, contentDescription = if (title.isNotBlank()) "Explain $title" else "Explain metric", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = valueText ?: String.format(Locale.US, "%,.2f %s", amount, currency),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                ),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (showHelp && helpText != null) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(title) },
            text = { Text(helpText) },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun PeriodChip(
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


data class DonutSegment(
    val percentage: Float,
    val color: Color
)

@Composable
fun DebtRatioRingChart(
    segments: List<DonutSegment>,
    centerPercentage: Int,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val strokeWidth = 12.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeftOffset = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeftOffset,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )

            var currentAngle = -90f
            for (segment in segments) {
                val sweep = (segment.percentage / 100f) * 360f
                if (sweep > 0f) {
                    drawArc(
                        color = segment.color,
                        startAngle = currentAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeftOffset,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                    currentAngle += sweep
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = "$centerPercentage%",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActivityRowCard(
    transaction: TransactionItem,
    currency: String,
    isArabic: Boolean,
    onClick: (() -> Unit)? = null,
    onCustomerClick: (() -> Unit)? = null,
    onReverseClick: (() -> Unit)? = null,
    onReturnClick: (() -> Unit)? = null
) {
    val type = transaction.typedTransactionType
    val isPositiveMovement = when (type) {
        TransactionType.SALE,
        TransactionType.CUSTOMER_PAYMENT,
        TransactionType.PURCHASE_RETURN -> true
        else -> false
    }
    val isReversed = transaction.typedOperationStatus == OperationStatus.REVERSED
    val badgeBg = if (isPositiveMovement) MaterialTheme.colorScheme.statusGreenContainer else MaterialTheme.colorScheme.statusRedContainer
    val badgeTint = if (isPositiveMovement) MaterialTheme.colorScheme.statusGreen else MaterialTheme.colorScheme.statusRed
    val iconVector = if (isPositiveMovement) Icons.Default.Add else Icons.Default.Remove

    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .testTag("activity_row_${transaction.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = badgeTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.customerNameSnapshot.ifBlank { transaction.customerName }.ifBlank { transaction.title },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onCustomerClick != null) {
                            Modifier
                                .clickable { onCustomerClick() }
                                .testTag("activity_customer_name_${transaction.id}")
                        } else Modifier
                    )
                    if (transaction.isArchived) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("archived_badge_${transaction.id}")
                        ) {
                            Text(
                                text = if (isArabic) "مؤرشف" else "Archived",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isReversed) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("reversed_badge_${transaction.id}")
                        ) {
                            Text(
                                text = if (isArabic) "ملغي" else "Reversed",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = transaction.activityType,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (transaction.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = transaction.notes,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(
                        Locale.US,
                        "%s%,.2f %s",
                        if (isPositiveMovement) "+" else "-",
                        transaction.amount,
                        currency
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = if (isPositiveMovement) MaterialTheme.colorScheme.statusGreen else MaterialTheme.colorScheme.statusRed
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = transaction.relativeTime,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isReversed && onReturnClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onReturnClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("action_return_${transaction.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Reply,
                        contentDescription = if (isArabic) "مرتجع مبيعات" else "Sale Return",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (!isReversed && onReverseClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onReverseClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("action_reverse_${transaction.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = if (isArabic) "إلغاء المعاملة" else "Reverse Transaction",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun normalizeCustomerName(name: String?): String {
    if (name.isNullOrBlank()) return ""
    return name
        .trim()
        .replace(Regex("\\s+"), " ")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ة', 'ه')
        .replace('ى', 'ي')
        .lowercase()
}

private fun isAnonymousCustomerName(name: String?): Boolean {
    if (name.isNullOrBlank()) return true
    val trimmed = name.trim()
    val normalized = normalizeCustomerName(trimmed)
    return when {
        normalized == "عميل عام" || normalized == "عميل كاش" || normalized == "عميل نقدي" ||
        normalized == "زبون كاش" || normalized == "زبون نقدي" || normalized == "زبون عام" ||
        normalized == "عميل افتراضي" || normalized == "عميل" || normalized == "زبون" -> true
        trimmed.equals("Cash Customer", ignoreCase = true) -> true
        trimmed.equals("Cash", ignoreCase = true) -> true
        trimmed.equals("كاش", ignoreCase = true) -> true
        trimmed.equals("General", ignoreCase = true) -> true
        else -> false
    }
}

@Composable
internal fun SupplierSearchField(
    suppliers: List<Supplier>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSupplierSelected: (Supplier) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
    selectedSupplierId: String? = null,
    placeholderText: String? = null,
    isArabic: Boolean = true,
    inputTestTag: String = "supplier_search_input",
    dropdownTestTag: String = "supplier_search_suggestions",
    itemTagPrefix: String = "supplier_suggestion_"
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    var isDropdownOpen by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                isDropdownOpen = true
            }
        }
    }

    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    val filteredSuppliers = remember(suppliers, searchQuery) {
        if (searchQuery.isBlank()) suppliers
        else {
            val q = searchQuery.trim().lowercase()
            suppliers.filter {
                it.name.lowercase().contains(q) || it.phone.contains(q)
            }
        }
    }

    val placeholder = placeholderText ?: if (isArabic) "بحث عن مورد بالاسم أو الهاتف في النشاطات..." else "Search supplier activity by name or phone..."

    BackHandler(enabled = isDropdownOpen) {
        isDropdownOpen = false
        focusManager.clearFocus()
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(modifier = modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { newQuery ->
                    onSearchQueryChange(newQuery)
                    isDropdownOpen = true
                },
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            isDropdownOpen = true
                        }
                    }
                    .testTag(inputTestTag),
                placeholder = {
                    Text(
                        text = placeholder,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = if (isArabic) "بحث" else "Search",
                        tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (searchQuery.isNotBlank() || selectedSupplierId != null) {
                            IconButton(
                                onClick = {
                                    onSearchQueryChange("")
                                    onClearSelection()
                                    isDropdownOpen = false
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("${inputTestTag}_clear")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = if (isArabic) "مسح البحث" else "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                isDropdownOpen = !isDropdownOpen
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("${inputTestTag}_arrow")
                        ) {
                            Icon(
                                imageVector = if (isDropdownOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isArabic) "قائمة الموردين" else "Supplier list",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (selectedSupplierId != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        isDropdownOpen = false
                        focusManager.clearFocus()
                    }
                )
            )

            if (isDropdownOpen) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag(dropdownTestTag)
                ) {
                    if (filteredSuppliers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isArabic) "لا يوجد موردون مطابقون" else "No matching suppliers found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            items(filteredSuppliers, key = { it.id }) { supplier ->
                                Surface(
                                    color = if (supplier.id == selectedSupplierId) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSupplierSelected(supplier)
                                            isDropdownOpen = false
                                            focusManager.clearFocus()
                                        }
                                        .testTag("$itemTagPrefix${supplier.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (supplier.id == selectedSupplierId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = supplier.name.firstOrNull()?.uppercase() ?: "S",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = if (supplier.id == selectedSupplierId) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = supplier.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (supplier.phone.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = supplier.phone,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
