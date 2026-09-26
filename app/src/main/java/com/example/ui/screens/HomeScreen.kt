package com.example.ui.screens

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
    onReverseTransaction: ((TransactionItem, String) -> Unit)? = null,
    onReturnTransaction: ((TransactionItem, List<SaleReturnLineRequest>, String, RefundRequest?) -> Unit)? = null,
    onLoadReturnDetails: (suspend (String) -> Triple<Sale?, List<SaleLine>, Map<String, Int>>)? = null
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val currency = AppCurrency.SYMBOL
    val focusManager = LocalFocusManager.current
    var transactionToReverse by remember { mutableStateOf<TransactionItem?>(null) }
    var transactionToReturn by remember { mutableStateOf<TransactionItem?>(null) }

    val storeAgingSummary = remember(allCustomers, allTransactions, isArabic) {
        DebtAgingUtils.calculateStoreDebtAgingSummary(
            customers = allCustomers.ifEmpty { matchingCustomers },
            allTransactions = allTransactions,
            isArabic = isArabic
        )
    }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_container")
    ) {
        // FIXED TOP SECTION
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("home_fixed_top_section")
        ) {
            // Reusable Customer Search Field with Dropdown
            CustomerSearchField(
                customers = allCustomers.ifEmpty { matchingCustomers },
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onCustomerSelected = onSelectCustomer,
                onClearSelection = onClearSelectedCustomer,
                selectedCustomerId = selectedCustomer?.id,
                currency = currency,
                isArabic = isArabic,
                inputTestTag = "customer_search_input",
                dropdownTestTag = "customer_search_suggestions",
                itemTagPrefix = "customer_suggestion_"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. PERIOD SELECTOR (All / Today / Month / Custom)
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

            // If CUSTOM is selected, show the date range badge with edit button
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("home_custom_date_range_text")
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = if (isArabic) "تعديل التاريخ" else "Edit Date Range",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. METRICS CARD
            val homeBreakdown = remember(periodTransactions) {
                FinancialReportCalculator.calculate(periodTransactions)
            }
            val cashSalesAmount = homeBreakdown.cashSales
            val fullSettlementAmount = homeBreakdown.fullSettlementAmount
            val debtAmount = if (selectedCustomer != null) selectedCustomer.balance else totalDebt
            val totalActivity = debtAmount + cashSalesAmount + fullSettlementAmount
            val debtPercent = if (totalActivity > 0) ((debtAmount / totalActivity) * 100).roundToInt() else 0
            val cashPercent = if (totalActivity > 0) ((cashSalesAmount / totalActivity) * 100).roundToInt() else 0
            val fullPercent = if (totalActivity > 0) (100 - debtPercent - cashPercent).coerceAtLeast(0) else 0

            val redColor = MaterialTheme.colorScheme.statusRed
            val blueColor = MaterialTheme.colorScheme.statusBlue
            val greenColor = MaterialTheme.colorScheme.statusGreen

            val ringSegments = remember(debtPercent, cashPercent, fullPercent, redColor, blueColor, greenColor) {
                listOf(
                    DonutSegment(debtPercent.toFloat(), redColor),
                    DonutSegment(cashPercent.toFloat(), blueColor),
                    DonutSegment(fullPercent.toFloat(), greenColor)
                )
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_debt_ratio_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = if (selectedCustomer != null) {
                                if (isArabic) "ديون العميل المستحقة" else "Customer Outstanding Debt"
                            } else {
                                if (isArabic) StoreStrings.TOTAL_OUTSTANDING_DEBT_AR else StoreStrings.TOTAL_OUTSTANDING_DEBT_EN
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.US, "%,.1f %s", debtAmount, currency),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 24.sp
                            ),
                            color = MaterialTheme.colorScheme.statusRed,
                            modifier = Modifier.testTag("stat_total_debt")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DebtRatioRingChart(
                                segments = ringSegments,
                                centerPercentage = debtPercent,
                                subtitle = if (isArabic) StoreStrings.STAT_DEBT_CREDIT_AR else StoreStrings.STAT_DEBT_CREDIT_EN,
                                modifier = Modifier
                                    .size(130.dp)
                                    .testTag("home_debt_ring_chart")
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            CompositionLocalProvider(LocalLayoutDirection provides (if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("home_stats_legend"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HomeStatLegendItem(
                                        dotColor = MaterialTheme.colorScheme.statusRed,
                                        label = if (isArabic) StoreStrings.STAT_DEBT_CREDIT_AR else StoreStrings.STAT_DEBT_CREDIT_EN,
                                        amount = String.format(Locale.US, "%,.1f %s", debtAmount, currency),
                                        percentage = "$debtPercent%",
                                        testTag = "legend_debt_credit"
                                    )
                                    HomeStatLegendItem(
                                        dotColor = MaterialTheme.colorScheme.statusBlue,
                                        label = if (isArabic) StoreStrings.STAT_CASH_SALES_AR else StoreStrings.STAT_CASH_SALES_EN,
                                        amount = String.format(Locale.US, "%,.1f %s", cashSalesAmount, currency),
                                        percentage = "$cashPercent%",
                                        testTag = "legend_cash_sales"
                                    )
                                    HomeStatLegendItem(
                                        dotColor = MaterialTheme.colorScheme.statusGreen,
                                        label = if (isArabic) StoreStrings.STAT_PAYMENTS_RECEIVED_AR else StoreStrings.STAT_PAYMENTS_RECEIVED_EN,
                                        amount = String.format(Locale.US, "%,.1f %s", fullSettlementAmount, currency),
                                        percentage = "$fullPercent%",
                                        testTag = "legend_payment_full"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            StoreDebtAgingSummaryCard(
                summary = storeAgingSummary,
                isArabic = isArabic
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

        // SCROLLABLE LATEST ACTIVITIES LIST
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .testTag("latest_activities_section")
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) StoreStrings.LATEST_ACTIVITIES_AR else StoreStrings.LATEST_ACTIVITIES_EN,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (selectedCustomer != null) {
                    Text(
                        text = if (isArabic) "السجل الكامل" else "Complete History",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (periodTransactions.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    SimpleEmptyState(
                        message = if (isArabic) "لا توجد معاملات مسجلة." else "No activities recorded.",
                        icon = Icons.Default.ReceiptLong,
                        testTag = "activities_empty_state"
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("latest_activities_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = periodTransactions, key = { it.id }) { tx ->
                        val isSale = tx.typedTransactionType == TransactionType.SALE || tx.activityType.contains("فاتورة") || tx.activityType.contains("بيع")
                        val isReversed = tx.typedOperationStatus == OperationStatus.REVERSED
                        val isReturnEligible = !isReversed && isSale

                        ActivityRowCard(
                            transaction = tx,
                            currency = currency,
                            isArabic = isArabic,
                            onClick = if (onActivityClick != null) { { onActivityClick(tx) } } else null,
                            onReverseClick = if (onReverseTransaction != null && !isReversed) {
                                { transactionToReverse = tx }
                            } else null,
                            onReturnClick = if (onReturnTransaction != null && onLoadReturnDetails != null && isReturnEligible) {
                                { transactionToReturn = tx }
                            } else null
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
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
            onConfirm = { reason ->
                onReverseTransaction?.invoke(transactionToReverse!!, reason)
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
            onConfirm = { lines, reason, refundReq ->
                onReturnTransaction(transactionToReturn!!, lines, reason, refundReq)
                transactionToReturn = null
            },
            onDismiss = { transactionToReturn = null }
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

            // Draw circular background track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeftOffset,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )

            // Draw segments sequentially starting at -90deg (top)
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
private fun HomeStatLegendItem(
    dotColor: Color,
    label: String,
    amount: String,
    percentage: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = percentage,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = dotColor
                )
            }
            Text(
                text = amount,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onReverseClick: (() -> Unit)? = null,
    onReturnClick: (() -> Unit)? = null
) {
    val isCredit = transaction.isCredit
    val isPayment = transaction.typedTransactionType == TransactionType.CUSTOMER_PAYMENT
    val isReversed = transaction.typedOperationStatus == OperationStatus.REVERSED
    val badgeBg = if (isPayment || isCredit) MaterialTheme.colorScheme.statusGreenContainer else MaterialTheme.colorScheme.statusRedContainer
    val badgeTint = if (isPayment || isCredit) MaterialTheme.colorScheme.statusGreen else MaterialTheme.colorScheme.statusRed
    val iconVector = if (isPayment || isCredit) Icons.Default.Add else Icons.Default.Remove

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
                        overflow = TextOverflow.Ellipsis
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
                        if (isPayment || isCredit) "+" else "-",
                        transaction.amount,
                        currency
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = if (isPayment || isCredit) MaterialTheme.colorScheme.statusGreen else MaterialTheme.colorScheme.statusRed
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

/**
 * Phase 7: Financial Reversal Confirmation Dialog.
 * Prompts user for a mandatory reason, explains the audit and accounting effect,
 * and neutralizes the active financial impact.
 */
@Composable
fun ReversalConfirmationDialog(
    transaction: TransactionItem,
    currency: String,
    isArabic: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isArabic) "إلغاء المعاملة محاسبياً" else "Reverse Financial Transaction",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = if (isArabic)
                            "سيتم تحييد الأثر المالي لهذه المعاملة محاسبياً وتصفير تأثيرها على رصيد العميل مع الاحتفاظ بسجل المعاملة الأصلية للتدقيق والمطابقة."
                        else
                            "The financial impact of this transaction will be neutralized in accounts and reports, while preserving the original record for audit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "${if (isArabic) "العميل: " else "Customer: "}${transaction.customerNameSnapshot}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${if (isArabic) "المبلغ: " else "Amount: "}%,.2f %s (%s)".format(Locale.US, transaction.amount, currency, transaction.activityType),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${if (isArabic) "التاريخ: " else "Date: "}${transaction.date}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        if (it.isNotBlank()) showError = false
                    },
                    label = { Text(if (isArabic) "سبب الإلغاء (مطلوب)" else "Reversal Reason (Required)") },
                    placeholder = { Text(if (isArabic) "مثال: خطأ في إدخال المبلغ" else "e.g. Incorrect amount recorded") },
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(if (isArabic) "يرجى كتابة سبب الإلغاء" else "Please enter a reversal reason", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reversal_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reason.trim().isNotBlank()) {
                        onConfirm(reason.trim())
                    } else {
                        showError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_reversal_button")
            ) {
                Text(if (isArabic) "تأكيد الإلغاء" else "Confirm Reversal")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_reversal_button")
            ) {
                Text(if (isArabic) "تراجع" else "Cancel")
            }
        }
    )
}
