package com.example.ui.screens

import com.example.ui.components.ReversalConfirmationDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
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
    modifier: Modifier = Modifier,
    allCustomers: List<CustomerAccount> = matchingCustomers,
    allTransactions: List<TransactionItem> = transactions,
    customStartDate: LocalDate? = null,
    customEndDate: LocalDate? = null,
    showCustomDatePicker: Boolean = false,
    onOpenCustomDatePicker: () -> Unit = {},
    onDismissCustomDatePicker: () -> Unit = {},
    onSetCustomDateRange: (LocalDate?, LocalDate?) -> Unit = { _, _ -> },
    onActivityClick: ((TransactionItem) -> Unit)? = null,
    onReverseTransaction: ((TransactionItem, String, String) -> Unit)? = null,
    onReturnTransaction: ((TransactionItem, List<SaleReturnLineRequest>, String, String, RefundRequest?) -> Unit)? = null,
    onLoadReturnDetails: (suspend (String) -> Triple<Sale?, List<SaleLine>, Map<String, Int>>)? = null
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val currency = AppCurrency.SYMBOL
    val focusManager = LocalFocusManager.current
    var transactionToReverse by remember { mutableStateOf<TransactionItem?>(null) }
    var transactionToReturn by remember { mutableStateOf<TransactionItem?>(null) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showOverdueCustomersDialog by remember { mutableStateOf(false) }

    // CURRENT BALANCES (Never filtered by period):
    // 1. Customer Receivables & Debt Aging
    val storeAgingSummary = remember(allCustomers, allTransactions, isArabic) {
        DebtAgingUtils.calculateStoreDebtAgingSummary(
            customers = allCustomers.ifEmpty { matchingCustomers },
            allTransactions = allTransactions,
            isArabic = isArabic
        )
    }

    val customerReceivables = if (selectedCustomer != null) {
        selectedCustomer.balance.coerceAtLeast(0.0)
    } else if (allCustomers.isNotEmpty()) {
        allCustomers.sumOf { it.balance.coerceAtLeast(0.0) }
    } else {
        totalDebt.coerceAtLeast(0.0)
    }

    val customersWithDebtCount = if (selectedCustomer != null) {
        if (selectedCustomer.balance > 0.001) 1 else 0
    } else {
        storeAgingSummary.totalCustomersWithDebtCount
    }

    // Overdue Receivables (Aging > 30 days)
    val overdueReceivables = storeAgingSummary.sum31To60 + storeAgingSummary.sum61To90 + storeAgingSummary.sum90Plus
    val overdueCustomersCount = storeAgingSummary.customerAgings.count { (it.bucket31To60 + it.bucket61To90 + it.bucket90Plus) > 0.001 }

    // Supplier Payables (Balance from supplier activities in allTransactions)
    val supplierPayables = remember(allTransactions) {
        // Purchases create supplier liability (+credit/payable), Payments reduce liability (-debit)
        allTransactions.filter { 
            it.typedOperationStatus != OperationStatus.REVERSED && (
                it.typedTransactionType == TransactionType.PURCHASE ||
                it.typedTransactionType == TransactionType.SUPPLIER_PAYMENT
            )
        }.groupBy { it.customerId ?: it.id }.values.sumOf { txGroup ->
            val totalPurchased = txGroup.filter { it.typedTransactionType == TransactionType.PURCHASE }.sumOf { it.amount }
            val totalPaid = txGroup.filter { it.typedTransactionType == TransactionType.SUPPLIER_PAYMENT }.sumOf { it.amount }
            (totalPurchased - totalPaid).coerceAtLeast(0.0)
        }
    }

    // PERIOD FLOWS (Filtered strictly by selected period):
    val periodTransactions = remember(transactions, selectedPeriod, selectedCustomer, customStartDate, customEndDate) {
        val base = if (selectedCustomer != null) {
            transactions.filter { it.customerId == selectedCustomer.id }
        } else {
            transactions
        }
        base.filter { tx ->
            DateFilterUtils.isDateInPeriod(
                dateStr = tx.date,
                period = selectedPeriod,
                customStartDate = customStartDate,
                customEndDate = customEndDate
            )
        }
    }

    // Period KPI metrics:
    val periodSalesTxs = remember(periodTransactions) {
        periodTransactions.filter { 
            it.typedOperationStatus != OperationStatus.REVERSED && 
            it.typedTransactionType == TransactionType.SALE 
        }
    }
    val totalPeriodSales = periodSalesTxs.sumOf { it.amount }
    val periodSalesCount = periodSalesTxs.size

    val totalPeriodCollections = remember(periodTransactions) {
        periodTransactions.filter { 
            it.typedOperationStatus != OperationStatus.REVERSED && 
            it.typedTransactionType == TransactionType.CUSTOMER_PAYMENT 
        }.sumOf { it.amount }
    }

    val totalPeriodExpenses = remember(periodTransactions) {
        periodTransactions.filter { 
            it.typedOperationStatus != OperationStatus.REVERSED && 
            it.typedTransactionType == TransactionType.EXPENSE 
        }.sumOf { it.amount }
    }

    val totalPeriodCreditSales = remember(periodSalesTxs) {
        periodSalesTxs.sumOf { it.creditAmount }
    }

    val totalPeriodPurchases = remember(periodTransactions) {
        periodTransactions.filter { 
            it.typedOperationStatus != OperationStatus.REVERSED && 
            it.typedTransactionType == TransactionType.PURCHASE 
        }.sumOf { it.amount }
    }

    // Recent store activities (top 6 recent items, filtered by search query if entered)
    val recentActivities = remember(periodTransactions, searchQuery, allCustomers, matchingCustomers) {
        val trimmedQuery = searchQuery.trim()
        val filtered = if (trimmedQuery.isBlank()) {
            periodTransactions
        } else {
            val customerPool = allCustomers.ifEmpty { matchingCustomers }
            val matchingCustomerIds = customerPool
                .filter { it.customerName.contains(trimmedQuery, ignoreCase = true) || it.phone.contains(trimmedQuery) }
                .map { it.id }
                .toSet()

            periodTransactions.filter { tx ->
                (tx.customerId != null && tx.customerId in matchingCustomerIds) ||
                tx.customerNameSnapshot.contains(trimmedQuery, ignoreCase = true)
            }
        }
        filtered.take(6)
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

        // 3. PERIOD FLOW KPIS (2x2 Grid)
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
                    // KPI 1: Sales
                    KpiCard(
                        title = if (isArabic) "المبيعات" else "Sales",
                        subtitle = if (periodSalesCount > 0) "$periodSalesCount ${if (isArabic) "عملية" else "txs"}" else null,
                        amount = totalPeriodSales,
                        currency = currency,
                        valueColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surface,
                        testTag = "kpi_sales",
                        modifier = Modifier.weight(1f)
                    )
                    // KPI 2: Collections
                    KpiCard(
                        title = if (isArabic) "التحصيلات النقدية" else "Collections",
                        subtitle = if (isArabic) "سداد العملاء" else "Customer payments",
                        amount = totalPeriodCollections,
                        currency = currency,
                        valueColor = MaterialTheme.colorScheme.statusGreen,
                        containerColor = MaterialTheme.colorScheme.surface,
                        testTag = "kpi_collections",
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
                                    color = if (customerReceivables > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
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

        // 6 & 7. SUPPLIER PAYABLES & PURCHASES
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section 6: Current Supplier Payables
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_supplier_payables_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (isArabic) "مستحقات الموردين" else "Supplier Payables",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.US, "%,.2f %s", supplierPayables, currency),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (supplierPayables > 0) MaterialTheme.colorScheme.statusAmber else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isArabic) "رصيد الالتزام الحالي" else "Current balance owed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Section 7: Purchases (Period Flow)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_purchases_period_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (isArabic) "مشتريات الفترة" else "Period Purchases",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.US, "%,.2f %s", totalPeriodPurchases, currency),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isArabic) "توريد بضائع بالفترة" else "Goods acquired in period",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        color = MaterialTheme.colorScheme.error
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
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
                                color = MaterialTheme.colorScheme.error
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
                            text = if (isArabic) "آخر نشاطات المتجر" else "Recent Store Activity",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isArabic) "${periodTransactions.size} عملية مسجلة" else "${periodTransactions.size} total entries",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (selectedCustomer != null) {
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
                    }
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
                    val customerPool = allCustomers.ifEmpty { matchingCustomers }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentActivities.forEach { tx ->
                            val isSale = if (tx.typedTransactionType != null) {
                                tx.typedTransactionType == TransactionType.SALE
                            } else {
                                tx.activityType.contains("فاتورة") || tx.activityType.contains("بيع")
                            }
                            val isReversed = tx.typedOperationStatus == OperationStatus.REVERSED
                            val isReturnEligible = !isReversed && isSale

                            val isCustomerTx = when (tx.typedTransactionType) {
                                TransactionType.SALE,
                                TransactionType.CUSTOMER_PAYMENT,
                                TransactionType.CUSTOMER_OPENING_BALANCE,
                                TransactionType.CUSTOMER_ADJUSTMENT -> true
                                TransactionType.PURCHASE,
                                TransactionType.SUPPLIER_PAYMENT,
                                TransactionType.EXPENSE,
                                TransactionType.PURCHASE_RETURN,
                                TransactionType.SALE_RETURN -> false
                                null -> tx.customerId != null && !tx.activityType.contains("مورد") && !tx.activityType.contains("مصروف")
                            }

                            val matchedCustomer = if (isCustomerTx) {
                                if (tx.customerId != null) {
                                    customerPool.find { it.id == tx.customerId }
                                } else if (tx.customerNameSnapshot.isNotBlank()) {
                                    customerPool.find { it.name.trim() == tx.customerNameSnapshot.trim() }
                                } else null
                            } else null

                            ActivityRowCard(
                                transaction = tx,
                                currency = currency,
                                isArabic = isArabic,
                                onClick = if (onActivityClick != null) { { onActivityClick(tx) } } else null,
                                onCustomerClick = if (matchedCustomer != null) {
                                    { onSelectCustomer(matchedCustomer) }
                                } else null,
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

                // Compact Search Bar at the bottom of Recent Store Activities
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
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
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
                        customer?.balance?.coerceAtLeast(0.0) ?: aging.currentBalance.coerceAtLeast(0.0),
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
                                            onSelectCustomer(it)
                                            showOverdueCustomersDialog = false
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
    modifier: Modifier = Modifier
) {
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
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format(Locale.US, "%,.2f %s", amount, currency),
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
                        text = transaction.customerNameSnapshot,
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
