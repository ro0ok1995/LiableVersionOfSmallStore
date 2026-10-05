package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accounting.FinancialReportCalculator
import com.example.data.db.TransactionItemLineEntity
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.model.PeriodFilter
import com.example.model.ProductItem
import com.example.model.SaleType
import com.example.model.StoreInfo
import com.example.model.StoreStrings
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.model.typedSaleType
import com.example.model.typedTransactionType
import com.example.ui.components.CustomerSelectorField
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusRedBg
import com.example.util.ReportExporter
import com.example.util.ReportPreviewRow
import com.example.util.ReportPresentationUtils
import com.example.viewmodel.AnalysisCenterUiState
import com.example.viewmodel.AnalysisCenterViewModel
import com.example.viewmodel.DateFilterUtils
import com.example.viewmodel.DebtAgingUtils
import com.example.viewmodel.ReportType
import java.time.LocalDate
import java.util.Locale

// -------------------------------------------------------------
// TAB 3: REPORTS
// -------------------------------------------------------------
@Composable
internal fun ReportsTabContent(
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
    val salesBreakdown = remember(periodTransactions, transactionLines) {
        FinancialReportCalculator.calculate(periodTransactions, transactionLines)
    }
    val cashSalesInvoices = remember(periodTransactions) {
        periodTransactions.filter { ReportPresentationUtils.cashSalesAmount(it) > FinancialReportCalculator.EPSILON }
    }
    val debtSalesInvoices = remember(periodTransactions) {
        periodTransactions.filter { ReportPresentationUtils.creditSalesAmount(it) > FinancialReportCalculator.EPSILON }
    }
    val totalCashSalesAmount = salesBreakdown.cashSales
    val totalDebtSalesAmount = salesBreakdown.creditSales
    val totalSalesAmount = salesBreakdown.totalSales
    val totalInvoicesCount = remember(periodTransactions, cashSalesInvoices, debtSalesInvoices) {
        val sales = periodTransactions.filter { it.typedTransactionType == TransactionType.SALE }
        if (sales.isNotEmpty()) sales.size else (cashSalesInvoices + debtSalesInvoices).distinctBy { it.id }.size
    }

    val itemBreakdowns = remember(periodTransactions, transactionLines) {
        val periodTxIds = periodTransactions.map { it.id }.toSet()
        val linesInPeriod = transactionLines.filter { it.transactionId in periodTxIds }
        linesInPeriod.groupBy { it.productNameSnapshot.ifBlank { it.productId ?: "-" } }
            .map { (name, lines) ->
                val totalQty = lines.sumOf { it.quantity }
                val totalSales = lines.sumOf { it.subtotal }
                val lineCogs = FinancialReportCalculator.calculateCogsFromTransactionLines(periodTransactions, lines)
                val profitMargin = FinancialReportCalculator.calculateGrossProfit(totalSales, lineCogs)
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
    val totalTxCash = salesBreakdown.cashSales
    val totalTxDebt = salesBreakdown.creditSales
    val totalTxPayments = salesBreakdown.customerPayments

    // 4. COMPREHENSIVE_CUSTOMER calculations (Phase 2: Persistent customer identity)
    val selectedCustomer = uiState.selectedCustomer
    val customerTransactions = remember(periodTransactions, selectedCustomer) {
        if (selectedCustomer == null) emptyList()
        else periodTransactions.filter { tx ->
            tx.customerId == selectedCustomer.id
        }.sortedByDescending { it.date }
    }
    val customerBreakdown = remember(customerTransactions, transactionLines) {
        FinancialReportCalculator.calculate(customerTransactions, transactionLines)
    }
    val customerCashPurchases = customerBreakdown.cashSales
    val customerDebtPurchases = customerBreakdown.creditSales
    val customerPayments = customerBreakdown.customerPayments
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
                    profitMargin = FinancialReportCalculator.calculateGrossProfit(
                        gLines.sumOf { it.subtotal },
                        FinancialReportCalculator.calculateCogsFromTransactionLines(customerTransactions, gLines)
                    )
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
                    val typeLabel = ReportPresentationUtils.getTransactionTypeLabel(
                        tx = tx,
                        isArabic = isArabic,
                        shortLabel = false
                    )
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
                    val typeLabel = ReportPresentationUtils.getTransactionTypeLabel(
                        tx = tx,
                        isArabic = isArabic,
                        shortLabel = true
                    )
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

        // 5. Centralized Export Action Bar (PDF, CSV, Share)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // PDF Export
            Button(
                onClick = {
                    if (canExport) {
                        val file = if (uiState.selectedReportType == ReportType.SALES_AND_ITEMS) {
                            ReportExporter.createCachedSalesAndItemsPdf(
                                context = context,
                                fileName = "SalesReport_${System.currentTimeMillis()}.pdf",
                                title = reportTitle,
                                storeName = storeInfo.storeName.ifBlank { if (isArabic) "سمول ستور" else "SmallStore" },
                                subtitle = if (isArabic) "الفترة: $periodLabel" else "Period: $periodLabel",
                                kpis = exportKpis,
                                itemBreakdowns = itemBreakdowns,
                                invoices = (cashSalesInvoices + debtSalesInvoices).distinctBy { it.id }.sortedByDescending { it.date },
                                isArabic = isArabic
                            )
                        } else if (uiState.selectedReportType == ReportType.TRANSACTIONS) {
                            ReportExporter.createCachedTransactionsPdf(
                                context = context,
                                fileName = "TransactionsReport_${System.currentTimeMillis()}.pdf",
                                title = reportTitle,
                                storeName = storeInfo.storeName.ifBlank { if (isArabic) "سمول ستور" else "SmallStore" },
                                subtitle = if (isArabic) "الفترة: $periodLabel" else "Period: $periodLabel",
                                kpis = exportKpis,
                                transactions = sortedTransactions,
                                totalCash = totalTxCash,
                                totalDebt = totalTxDebt,
                                totalPayments = totalTxPayments,
                                isArabic = isArabic
                            )
                        } else if (uiState.selectedReportType == ReportType.COMPREHENSIVE_CUSTOMER && selectedCustomer != null) {
                            val custReportTitle = if (isArabic) StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_AR else StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_EN
                            ReportExporter.createCachedCustomerPdf(
                                context = context,
                                fileName = "CustomerReport_${System.currentTimeMillis()}.pdf",
                                title = custReportTitle,
                                storeName = storeInfo.storeName.ifBlank { if (isArabic) "سمول ستور" else "SmallStore" },
                                subtitle = if (isArabic) "الفترة: $periodLabel" else "Period: $periodLabel",
                                customer = selectedCustomer,
                                kpis = exportKpis,
                                transactions = customerTransactions,
                                totalCash = customerCashPurchases,
                                totalDebt = customerDebtPurchases,
                                totalPayments = customerPayments,
                                itemBreakdowns = customerItemBreakdowns,
                                isArabic = isArabic
                            )
                        } else {
                            ReportExporter.createCachedPdf(
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
                        }
                        ReportExporter.shareFile(context, file, "application/pdf", reportTitle)
                    }
                },
                enabled = canExport,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("report_export_pdf_button")
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }

            // CSV Export
            OutlinedButton(
                onClick = {
                    if (canExport) {
                        val file = if (uiState.selectedReportType == ReportType.SALES_AND_ITEMS) {
                            ReportExporter.createCachedSalesAndItemsCsv(
                                context = context,
                                fileName = "SalesReport_${System.currentTimeMillis()}.csv",
                                title = reportTitle,
                                storeName = storeInfo.storeName.ifBlank { if (isArabic) "سمول ستور" else "SmallStore" },
                                subtitle = if (isArabic) "الفترة: $periodLabel" else "Period: $periodLabel",
                                kpis = exportKpis,
                                itemBreakdowns = itemBreakdowns,
                                invoices = (cashSalesInvoices + debtSalesInvoices).distinctBy { it.id }.sortedByDescending { it.date },
                                isArabic = isArabic
                            )
                        } else if (uiState.selectedReportType == ReportType.TRANSACTIONS) {
                            ReportExporter.createCachedTransactionsCsv(
                                context = context,
                                fileName = "TransactionsReport_${System.currentTimeMillis()}.csv",
                                title = reportTitle,
                                storeName = storeInfo.storeName.ifBlank { if (isArabic) "سمول ستور" else "SmallStore" },
                                subtitle = if (isArabic) "الفترة: $periodLabel" else "Period: $periodLabel",
                                kpis = exportKpis,
                                transactions = sortedTransactions,
                                totalCash = totalTxCash,
                                totalDebt = totalTxDebt,
                                totalPayments = totalTxPayments,
                                isArabic = isArabic
                            )
                        } else if (uiState.selectedReportType == ReportType.COMPREHENSIVE_CUSTOMER && selectedCustomer != null) {
                            val custReportTitle = if (isArabic) StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_AR else StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_EN
                            ReportExporter.createCachedCustomerCsv(
                                context = context,
                                fileName = "CustomerReport_${System.currentTimeMillis()}.csv",
                                title = custReportTitle,
                                storeName = storeInfo.storeName.ifBlank { if (isArabic) "سمول ستور" else "SmallStore" },
                                subtitle = if (isArabic) "الفترة: $periodLabel" else "Period: $periodLabel",
                                customer = selectedCustomer,
                                kpis = exportKpis,
                                transactions = customerTransactions,
                                totalCash = customerCashPurchases,
                                totalDebt = customerDebtPurchases,
                                totalPayments = customerPayments,
                                itemBreakdowns = customerItemBreakdowns,
                                isArabic = isArabic
                            )
                        } else {
                            val csv = ReportExporter.generateReportCsv(tableHeaders, previewRows)
                            ReportExporter.createCachedCsv(context, "Report_${System.currentTimeMillis()}.csv", csv)
                        }
                        ReportExporter.shareFile(context, file, "text/csv", reportTitle)
                        Toast.makeText(context, if (isArabic) "تم تجهيز ملف CSV للمشاركة" else "CSV report ready for export", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = canExport,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("report_export_csv_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = GeoPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "CSV", color = GeoPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }

            // Share Text
            OutlinedButton(
                onClick = {
                    if (canExport) {
                        val file = if (uiState.selectedReportType == ReportType.SALES_AND_ITEMS) {
                            ReportExporter.createCachedSalesAndItemsTxt(
                                context = context,
                                fileName = "Sales_And_Items_Report_${System.currentTimeMillis()}.txt",
                                title = reportTitle,
                                storeName = storeInfo.storeName.ifBlank { if (isArabic) "سمول ستور" else "SmallStore" },
                                subtitle = if (isArabic) "الفترة: $periodLabel" else "Period: $periodLabel",
                                kpis = exportKpis,
                                itemBreakdowns = itemBreakdowns,
                                invoices = (cashSalesInvoices + debtSalesInvoices).distinctBy { it.id }.sortedByDescending { it.date },
                                isArabic = isArabic
                            )
                        } else if (uiState.selectedReportType == ReportType.TRANSACTIONS) {
                            ReportExporter.createCachedTransactionsTxt(
                                context = context,
                                fileName = "Transactions_Report_${System.currentTimeMillis()}.txt",
                                title = reportTitle,
                                storeName = storeInfo.storeName.ifBlank { if (isArabic) "سمول ستور" else "SmallStore" },
                                subtitle = if (isArabic) "الفترة: $periodLabel" else "Period: $periodLabel",
                                kpis = exportKpis,
                                transactions = sortedTransactions,
                                totalCash = totalTxCash,
                                totalDebt = totalTxDebt,
                                totalPayments = totalTxPayments,
                                isArabic = isArabic
                            )
                        } else if (uiState.selectedReportType == ReportType.COMPREHENSIVE_CUSTOMER && selectedCustomer != null) {
                            val custReportTitle = if (isArabic) StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_AR else StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_EN
                            val safeCustName = selectedCustomer.customerName.replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_-]"), "_").take(30)
                            ReportExporter.createCachedCustomerTxt(
                                context = context,
                                fileName = "Comprehensive_Customer_Report_${safeCustName}_${System.currentTimeMillis()}.txt",
                                title = custReportTitle,
                                storeName = storeInfo.storeName.ifBlank { if (isArabic) "سمول ستور" else "SmallStore" },
                                subtitle = if (isArabic) "الفترة: $periodLabel" else "Period: $periodLabel",
                                customer = selectedCustomer,
                                kpis = exportKpis,
                                transactions = customerTransactions,
                                totalCash = customerCashPurchases,
                                totalDebt = customerDebtPurchases,
                                totalPayments = customerPayments,
                                itemBreakdowns = customerItemBreakdowns,
                                isArabic = isArabic
                            )
                        } else {
                            val fallbackTxt = buildString {
                                appendLine("==================================================")
                                appendLine("${if (isArabic) "المتجر" else "Store"}: ${storeInfo.storeName.ifBlank { if (isArabic) "سمول ستور" else "SmallStore" }}")
                                appendLine("${if (isArabic) "عنوان التقرير" else "Report Title"}: $reportTitle")
                                appendLine("${if (isArabic) "الفترة" else "Period"}: $periodLabel")
                                appendLine("==================================================")
                                appendLine(tableHeaders.joinToString(" | "))
                                previewRows.forEach { r ->
                                    appendLine("${r.col1} | ${r.col2} | ${r.col3} | ${r.col4}")
                                }
                            }
                            ReportExporter.createCachedTxt(context, "Report_${System.currentTimeMillis()}.txt", fallbackTxt)
                        }
                        ReportExporter.shareFile(context, file, "text/plain", reportTitle)
                        Toast.makeText(context, if (isArabic) "تم تجهيز التقرير للمشاركة" else "Report ready for sharing", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = canExport,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("report_share_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = GeoPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = if (isArabic) StoreStrings.SHARE_STATEMENT_AR else StoreStrings.SHARE_STATEMENT_EN, color = GeoPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
