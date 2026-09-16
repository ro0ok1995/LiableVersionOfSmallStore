package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditScore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.model.AppCurrency
import com.example.model.StoreInfo
import com.example.model.StoreStrings
import com.example.model.TransactionItem
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.util.ReportPreviewRow
import java.util.Locale

/**
 * Filter mode for transactions in the detailed table preview.
 */
private enum class TxFilterMode {
    ALL,
    CASH,
    CREDIT,
    PAYMENTS
}

/**
 * Redesigned visual and structural presentation for "Transactions Report" (تقرير المعاملات والحركات المالية).
 *
 * Strict Visual Hierarchy from TOP to BOTTOM:
 * 1. Report Title & Context Header (Card with title, certified badge, period, store info)
 * 2. Summary Cards:
 *    - Total Cash (إجمالي الكاش)
 *    - Total Credit (إجمالي الآجل)
 *    - Total Collections/Payments (إجمالي المقبوضات)
 *    - Number of Transactions (عدد المعاملات)
 * 3. Report Preview Section Header (Section title, count, filter pills)
 * 4. Long Detailed Transactions Table:
 *    - Date (التاريخ)
 *    - Customer (العميل)
 *    - Transaction Type (نوع المعاملة)
 *    - Amount (المبلغ)
 *    - Grand Total / Summary Footer Row
 */
@Composable
fun TransactionsReportPresentation(
    isArabic: Boolean,
    currency: String,
    periodLabel: String,
    storeInfo: StoreInfo,
    sortedTransactions: List<TransactionItem>,
    totalTxCash: Double,
    totalTxDebt: Double,
    totalTxPayments: Double,
    previewRows: List<ReportPreviewRow>,
    modifier: Modifier = Modifier
) {
    var filterMode by remember { mutableStateOf(TxFilterMode.ALL) }

    // Categorization counts & stats
    val cashTxList = remember(sortedTransactions) {
        sortedTransactions.filter { tx ->
            !tx.isCredit && (tx.activityType.contains("كاش") || tx.activityType.contains("Cash") ||
                (!tx.activityType.contains("تسديد") && !tx.activityType.contains("Payment") &&
                    !tx.activityType.contains("آجل") && !tx.activityType.contains("دين") && !tx.activityType.contains("Debt")))
        }
    }

    val debtTxList = remember(sortedTransactions) {
        sortedTransactions.filter { tx ->
            tx.isCredit || tx.activityType.contains("آجل") || tx.activityType.contains("دين") ||
                tx.activityType.contains("Debt") || tx.activityType.contains("شراء بالدين")
        }
    }

    val paymentsTxList = remember(sortedTransactions) {
        sortedTransactions.filter { tx ->
            tx.activityType.contains("تسديد") || tx.activityType.contains("Payment")
        }
    }

    val totalFinancialVolume = remember(totalTxCash, totalTxDebt, totalTxPayments) {
        totalTxCash + totalTxDebt + totalTxPayments
    }

    val cashPercentage = remember(totalFinancialVolume, totalTxCash) {
        if (totalFinancialVolume > 0) (totalTxCash / totalFinancialVolume) * 100 else 0.0
    }

    val debtPercentage = remember(totalFinancialVolume, totalTxDebt) {
        if (totalFinancialVolume > 0) (totalTxDebt / totalFinancialVolume) * 100 else 0.0
    }

    val paymentsPercentage = remember(totalFinancialVolume, totalTxPayments) {
        if (totalFinancialVolume > 0) (totalTxPayments / totalFinancialVolume) * 100 else 0.0
    }

    // Filtered transactions for the detailed table
    val displayedTransactions = remember(sortedTransactions, filterMode) {
        when (filterMode) {
            TxFilterMode.ALL -> sortedTransactions
            TxFilterMode.CASH -> cashTxList
            TxFilterMode.CREDIT -> debtTxList
            TxFilterMode.PAYMENTS -> paymentsTxList
        }
    }

    val displayedTotalAmount = remember(displayedTransactions) {
        displayedTransactions.sumOf { it.amount }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("transactions_summary_card"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. REPORT TITLE & CONTEXT CARD
        // ==========================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, GeoOutlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(GeoPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = GeoPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = if (isArabic) StoreStrings.REPORT_TRANSACTIONS_AR else StoreStrings.REPORT_TRANSACTIONS_EN,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isArabic) "سجل مالي مفصل للعمليات النقدية والآجلة والتحصيلات" else "Detailed financial ledger of cash, credit & collection entries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = StatusGreenBg,
                        border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(StatusGreen)
                            )
                            Text(
                                text = if (isArabic) "تقرير معتمد" else "Certified",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = StatusGreen
                            )
                        }
                    }
                }

                HorizontalDivider(color = GeoOutlineVariant.copy(alpha = 0.7f), thickness = 0.8.dp)

                // Meta Info Row (Period, Store Name)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Period Tag
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = GeoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = if (isArabic) "فترة التقرير" else "Period",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = periodLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Store Name Tag
                    val effectiveStoreName = storeInfo.storeName.ifBlank {
                        if (isArabic) StoreStrings.SAMPLE_STORE_NAME_AR else "SmallStore"
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Store,
                                contentDescription = null,
                                tint = GeoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = if (isArabic) "المنشأة / المتجر" else "Store",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = effectiveStoreName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. SUMMARY CARDS
        //    - Total Cash (إجمالي الكاش)
        //    - Total Credit (إجمالي الآجل)
        //    - Total Collections/Payments (إجمالي المقبوضات)
        //    - Number of Transactions (عدد المعاملات)
        // ==========================================
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isArabic) "ملخص إجماليات المعاملات ($periodLabel)" else "Transactions Totals ($periodLabel)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Row 1: Total Cash & Total Credit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 1: Total Cash
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusGreen.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stat_tx_sum_cash")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) "إجمالي الكاش" else "Total Cash",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = AppCurrency.formatAmountWithDecimals(totalTxCash, isArabic),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            ),
                            color = StatusGreen
                        )
                        Text(
                            text = "${cashTxList.size} ${if (isArabic) "حركات" else "txs"} (${String.format(Locale.US, "%.0f%%", cashPercentage)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }

                // Card 2: Total Credit (Debt)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusAmber.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stat_tx_sum_debt")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) "إجمالي الآجل" else "Total Credit",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.CreditScore,
                                contentDescription = null,
                                tint = StatusAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = AppCurrency.formatAmountWithDecimals(totalTxDebt, isArabic),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            ),
                            color = StatusAmber
                        )
                        Text(
                            text = "${debtTxList.size} ${if (isArabic) "حركات" else "txs"} (${String.format(Locale.US, "%.0f%%", debtPercentage)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Row 2: Total Collections/Payments & Number of Transactions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 3: Total Collections / Payments
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusBlue.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, StatusBlue.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stat_tx_sum_payments")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) "إجمالي المقبوضات" else "Total Collections",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.PriceCheck,
                                contentDescription = null,
                                tint = StatusBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = AppCurrency.formatAmountWithDecimals(totalTxPayments, isArabic),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            ),
                            color = StatusBlue
                        )
                        Text(
                            text = "${paymentsTxList.size} ${if (isArabic) "سندات قبض" else "payments"} (${String.format(Locale.US, "%.0f%%", paymentsPercentage)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }

                // Card 4: Number of Transactions
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = GeoPrimary.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, GeoPrimary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stat_tx_total_count")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) "عدد المعاملات" else "Transactions Count",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = null,
                                tint = GeoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "${sortedTransactions.size}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            ),
                            color = GeoPrimary
                        )
                        Text(
                            text = if (isArabic) "حركة مالية مسجلة" else "Recorded transactions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Financial Ratio Strip: Visual distribution bar
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, GeoOutlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "توزيع الحركات المالية" else "Transactions Flow Distribution",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isArabic) "إجمالي التدفق: ${AppCurrency.formatAmountWithDecimals(totalFinancialVolume, isArabic)}"
                            else "Total Volume: ${AppCurrency.formatAmountWithDecimals(totalFinancialVolume, isArabic)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Multi-Segment Progress Bar for Cash vs Debt vs Payments
                    val cashWeight = (cashPercentage / 100f).toFloat().coerceIn(0.01f, 0.98f)
                    val debtWeight = (debtPercentage / 100f).toFloat().coerceIn(0.01f, 0.98f)
                    val paymentsWeight = (paymentsPercentage / 100f).toFloat().coerceIn(0.01f, 0.98f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (totalFinancialVolume > 0) {
                            if (totalTxCash > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(cashWeight)
                                        .fillMaxWidth()
                                        .background(StatusGreen)
                                )
                            }
                            if (totalTxDebt > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(debtWeight)
                                        .fillMaxWidth()
                                        .background(StatusAmber)
                                )
                            }
                            if (totalTxPayments > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(paymentsWeight)
                                        .fillMaxWidth()
                                        .background(StatusBlue)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }

                    // Legend Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StatusGreen))
                            Text(
                                text = if (isArabic) "كاش: ${String.format(Locale.US, "%.0f%%", cashPercentage)}"
                                else "Cash: ${String.format(Locale.US, "%.0f%%", cashPercentage)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StatusAmber))
                            Text(
                                text = if (isArabic) "آجل: ${String.format(Locale.US, "%.0f%%", debtPercentage)}"
                                else "Debt: ${String.format(Locale.US, "%.0f%%", debtPercentage)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StatusBlue))
                            Text(
                                text = if (isArabic) "مقبوضات: ${String.format(Locale.US, "%.0f%%", paymentsPercentage)}"
                                else "Payments: ${String.format(Locale.US, "%.0f%%", paymentsPercentage)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. REPORT PREVIEW SECTION HEADER
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = null,
                    tint = GeoPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (isArabic) StoreStrings.REPORT_PREVIEW_TITLE_AR else StoreStrings.REPORT_PREVIEW_TITLE_EN,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Quick Filter Pills (All, Cash, Credit, Payments)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, GeoOutlineVariant)
            ) {
                Row(modifier = Modifier.padding(2.dp)) {
                    TxFilterPill(
                        title = if (isArabic) "الكل (${sortedTransactions.size})" else "All (${sortedTransactions.size})",
                        isSelected = filterMode == TxFilterMode.ALL,
                        onClick = { filterMode = TxFilterMode.ALL }
                    )
                    TxFilterPill(
                        title = if (isArabic) "كاش (${cashTxList.size})" else "Cash (${cashTxList.size})",
                        isSelected = filterMode == TxFilterMode.CASH,
                        onClick = { filterMode = TxFilterMode.CASH }
                    )
                    TxFilterPill(
                        title = if (isArabic) "آجل (${debtTxList.size})" else "Credit (${debtTxList.size})",
                        isSelected = filterMode == TxFilterMode.CREDIT,
                        onClick = { filterMode = TxFilterMode.CREDIT }
                    )
                    TxFilterPill(
                        title = if (isArabic) "مقبوضات (${paymentsTxList.size})" else "Pay (${paymentsTxList.size})",
                        isSelected = filterMode == TxFilterMode.PAYMENTS,
                        onClick = { filterMode = TxFilterMode.PAYMENTS }
                    )
                }
            }
        }

        // ==========================================
        // 4. LONG DETAILED TRANSACTIONS TABLE
        //    Concepts from reference:
        //    - Date (التاريخ)
        //    - Customer (العميل)
        //    - Transaction type (نوع المعاملة)
        //    - Amount (المبلغ)
        // ==========================================
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, GeoOutlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("report_preview_card")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Table Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isArabic) "التاريخ" else "Date",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1.1f)
                    )
                    Text(
                        text = if (isArabic) "العميل" else "Customer",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1.3f)
                    )
                    Text(
                        text = if (isArabic) "نوع المعاملة" else "Transaction Type",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1.3f)
                    )
                    Text(
                        text = if (isArabic) "المبلغ" else "Amount",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1.1f),
                        textAlign = TextAlign.End
                    )
                }

                HorizontalDivider(color = GeoOutlineVariant, thickness = 0.8.dp)

                if (displayedTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = if (isArabic) "لا توجد معاملات مسجلة مطابقة في هذه الفترة" else "No matching transactions recorded in this period",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Full detailed transactions list
                    displayedTransactions.forEachIndexed { idx, tx ->
                        val isEven = idx % 2 == 1
                        val isPayment = tx.activityType.contains("تسديد") || tx.activityType.contains("Payment")
                        val isDebt = !isPayment && (tx.isCredit || tx.activityType.contains("آجل") ||
                            tx.activityType.contains("دين") || tx.activityType.contains("Debt") ||
                            tx.activityType.contains("شراء بالدين"))

                        val typeLabel = if (isPayment) {
                            if (isArabic) "تسديد (دفعة)" else "Payment"
                        } else if (isDebt) {
                            if (isArabic) "شراء آجل (دين)" else "Credit Purchase"
                        } else {
                            if (isArabic) "شراء نقدي (كاش)" else "Cash Purchase"
                        }

                        val typeColor = if (isPayment) StatusBlue else if (isDebt) StatusAmber else StatusGreen

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isEven) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("report_row_$idx"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Col 1: Date
                            Column(modifier = Modifier.weight(1.1f)) {
                                Text(
                                    text = tx.date,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (tx.relativeTime.isNotBlank()) {
                                    Text(
                                        text = tx.relativeTime,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1
                                    )
                                }
                            }

                            // Col 2: Customer
                            Column(modifier = Modifier.weight(1.3f)) {
                                Text(
                                    text = tx.customerName.ifBlank { if (isArabic) "عميل عام" else "General" },
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (tx.title.isNotBlank() && tx.title != tx.customerName) {
                                    Text(
                                        text = tx.title,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Col 3: Transaction Type (Styled Pill)
                            Box(
                                modifier = Modifier
                                    .weight(1.3f)
                                    .padding(end = 4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = typeColor.copy(alpha = 0.12f),
                                    border = BorderStroke(0.8.dp, typeColor.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(typeColor)
                                        )
                                        Text(
                                            text = typeLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            color = typeColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Col 4: Amount
                            Text(
                                text = AppCurrency.formatAmountWithDecimals(tx.amount, isArabic),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = typeColor,
                                modifier = Modifier.weight(1.1f),
                                textAlign = TextAlign.End,
                                maxLines = 1
                            )
                        }

                        if (idx < displayedTransactions.size - 1) {
                            HorizontalDivider(
                                color = GeoOutlineVariant.copy(alpha = 0.35f),
                                thickness = 0.5.dp
                            )
                        }
                    }

                    // ==========================================
                    // GRAND TOTAL SUMMARY FOOTER ROW
                    // ==========================================
                    HorizontalDivider(color = GeoOutlineVariant, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(2.4f)) {
                            Text(
                                text = if (isArabic) "الإجمالي (${displayedTransactions.size} سجلات)" else "Total (${displayedTransactions.size} records)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isArabic) "مجموع مبالغ الحركات المعروضة" else "Sum of displayed transactions",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = AppCurrency.formatAmountWithDecimals(displayedTotalAmount, isArabic),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = GeoPrimary,
                            modifier = Modifier.weight(1.4f),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TxFilterPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) GeoPrimary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 10.5.sp
            ),
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
