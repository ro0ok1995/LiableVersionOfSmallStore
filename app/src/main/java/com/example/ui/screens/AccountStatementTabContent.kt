package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CustomerAccount
import com.example.model.PeriodFilter
import com.example.model.StoreStrings
import com.example.model.TransactionItem
import com.example.ui.components.SimpleEmptyState
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusRedBg
import com.example.util.StatementRow
import com.example.viewmodel.AnalysisCenterUiState
import com.example.viewmodel.AnalysisCenterViewModel
import com.example.viewmodel.DateFilterUtils
import com.example.viewmodel.StatementTxFilter
import java.time.LocalDate
import java.util.Locale

// -------------------------------------------------------------
// TAB 2: ACCOUNT STATEMENT
// -------------------------------------------------------------
@Composable
internal fun AccountStatementTabContent(
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
            allTransactions.filter { it.customerId == uiState.selectedCustomer.id }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.customerName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (row.isArchived) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.testTag("archived_badge_${row.id}")
                            ) {
                                Text(
                                    text = if (isArabic) "مؤرشف" else "Archived",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (row.isReversed) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.testTag("reversed_badge_${row.id}")
                            ) {
                                Text(
                                    text = if (isArabic) "ملغي" else "Reversed",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
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
