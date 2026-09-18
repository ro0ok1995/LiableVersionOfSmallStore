package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditScore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TrendingUp
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
import com.example.ui.theme.StatusRed
import com.example.util.ReportPreviewRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Aggregated product line item for the sales and items report.
 */
data class AggregatedProductLine(
    val productId: String,
    val productName: String,
    val totalQuantity: Int,
    val totalSales: Double,
    val profitMargin: Double
)

private enum class SalesReportTableViewMode {
    PRODUCTS,
    INVOICES
}

/**
 * Redesigned visual and structural presentation for "Sales Report for Products and Invoices".
 *
 * Strict Visual Hierarchy:
 * 1. Report Title & Context Header
 * 2. Summary Information / KPI Cards (Cash, Debt, Total Sales, Invoices, Margin, Distribution)
 * 3. Report Preview Section Header with Toggle
 * 4. Long Detailed Table with Full Data Rows and Sticky Grand Total Footer
 */
@Composable
fun SalesReportPresentation(
    isArabic: Boolean,
    currency: String,
    periodLabel: String,
    storeInfo: StoreInfo,
    cashSalesInvoices: List<TransactionItem>,
    debtSalesInvoices: List<TransactionItem>,
    totalCashSalesAmount: Double,
    totalDebtSalesAmount: Double,
    totalSalesAmount: Double,
    totalInvoicesCount: Int,
    itemBreakdowns: List<AggregatedProductLine>,
    totalItemProfitMargin: Double,
    previewRows: List<ReportPreviewRow>,
    modifier: Modifier = Modifier
) {
    var viewMode by remember {
        mutableStateOf(
            if (itemBreakdowns.isNotEmpty()) SalesReportTableViewMode.PRODUCTS else SalesReportTableViewMode.INVOICES
        )
    }

    val allSalesInvoices = remember(cashSalesInvoices, debtSalesInvoices) {
        (cashSalesInvoices + debtSalesInvoices).sortedByDescending { it.date }
    }

    val totalItemsQuantity = remember(itemBreakdowns) {
        itemBreakdowns.sumOf { it.totalQuantity }
    }

    val cashPercentage = remember(totalSalesAmount, totalCashSalesAmount) {
        if (totalSalesAmount > 0) (totalCashSalesAmount / totalSalesAmount) * 100 else 0.0
    }

    val debtPercentage = remember(totalSalesAmount, totalDebtSalesAmount) {
        if (totalSalesAmount > 0) (totalDebtSalesAmount / totalSalesAmount) * 100 else 0.0
    }

    val marginPercentage = remember(totalSalesAmount, totalItemProfitMargin) {
        if (totalSalesAmount > 0) (totalItemProfitMargin / totalSalesAmount) * 100 else 0.0
    }

    val averageInvoiceAmount = remember(totalSalesAmount, totalInvoicesCount) {
        if (totalInvoicesCount > 0) totalSalesAmount / totalInvoicesCount else 0.0
    }

    val currentDateStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sales_items_summary_card"),
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
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = GeoPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = if (isArabic) StoreStrings.REPORT_SALES_AND_ITEMS_AR else StoreStrings.REPORT_SALES_AND_ITEMS_EN,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isArabic) "تقرير مالي وتحليلي مفصل للمبيعات والأصناف" else "Detailed financial sales & product breakdown",
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

                // Meta Info Row (Period, Store Name, Date)
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
        // 2. SUMMARY INFORMATION / CARDS
        // ==========================================
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isArabic) "ملخص المؤشرات المالية والتشغيلية" else "Key Performance & Financial Summary",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Primary 2x2 Metric Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 1: Total Sales (GeoPrimary)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = GeoPrimary.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, GeoPrimary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stat_total_sales_amount")
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
                                text = if (isArabic) "إجمالي المبيعات" else "Total Sales",
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
                            text = AppCurrency.formatAmountWithDecimals(totalSalesAmount, isArabic),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            ),
                            color = GeoPrimary
                        )
                        Text(
                            text = "$totalInvoicesCount ${if (isArabic) "إجمالي الفواتير" else "invoices"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }

                // Card 2: Cash Sales (StatusGreen)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusGreen.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stat_cash_sales_invoices")
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
                                text = if (isArabic) "مبيعات كاش" else "Cash Sales",
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
                            text = AppCurrency.formatAmountWithDecimals(totalCashSalesAmount, isArabic),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            ),
                            color = StatusGreen
                        )
                        Text(
                            text = "${cashSalesInvoices.size} ${if (isArabic) "فاتورة" else "inv"} (${String.format(Locale.US, "%.0f%%", cashPercentage)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 3: Debt / Credit Sales (StatusAmber)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusAmber.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stat_debt_sales_invoices")
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
                                text = if (isArabic) "مبيعات آجل" else "Debt Sales",
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
                            text = AppCurrency.formatAmountWithDecimals(totalDebtSalesAmount, isArabic),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            ),
                            color = StatusAmber
                        )
                        Text(
                            text = "${debtSalesInvoices.size} ${if (isArabic) "فاتورة" else "inv"} (${String.format(Locale.US, "%.0f%%", debtPercentage)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }

                // Card 4: Profit Margin (StatusBlue)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusBlue.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, StatusBlue.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stat_items_profit_margin")
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
                                text = if (isArabic) "هامش الربح" else "Profit Margin",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = null,
                                tint = StatusBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = AppCurrency.formatAmountWithDecimals(totalItemProfitMargin, isArabic),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            ),
                            color = StatusBlue
                        )
                        val marginLabel = if (totalSalesAmount > 0) {
                            "${itemBreakdowns.size} ${if (isArabic) "أصناف" else "items"} (${String.format(Locale.US, "%.1f%%", marginPercentage)})"
                        } else {
                            "${itemBreakdowns.size} ${if (isArabic) "أصناف مسجلة" else "items recorded"}"
                        }
                        Text(
                            text = marginLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Financial Ratio Strip: Visual Ratio & Average Invoice
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
                            text = if (isArabic) "توزيع طرق السداد للمبيعات" else "Payment Method Distribution",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isArabic) "متوسط الفاتورة: ${AppCurrency.formatAmountWithDecimals(averageInvoiceAmount, isArabic)}"
                            else "Avg Invoice: ${AppCurrency.formatAmountWithDecimals(averageInvoiceAmount, isArabic)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Progress Bar for Cash vs Debt
                    val cashWeight = (cashPercentage / 100f).toFloat().coerceIn(0.01f, 0.99f)
                    val debtWeight = (debtPercentage / 100f).toFloat().coerceIn(0.01f, 0.99f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (totalSalesAmount > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(cashWeight)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                                    .background(StatusGreen)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(debtWeight)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                    .background(StatusAmber)
                            )
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
                                text = if (isArabic) "كاش: ${String.format(Locale.US, "%.1f%%", cashPercentage)}"
                                else "Cash: ${String.format(Locale.US, "%.1f%%", cashPercentage)}",
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
                                text = if (isArabic) "آجل: ${String.format(Locale.US, "%.1f%%", debtPercentage)}"
                                else "Debt: ${String.format(Locale.US, "%.1f%%", debtPercentage)}",
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
                                text = if (isArabic) "هامش ربح: ${String.format(Locale.US, "%.1f%%", marginPercentage)}"
                                else "Margin: ${String.format(Locale.US, "%.1f%%", marginPercentage)}",
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
                    text = if (isArabic) "تفاصيل التقرير التفصيلي" else "Detailed Report Details",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // View Mode Toggle Pills (Products Breakdown vs Invoices Log)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, GeoOutlineVariant)
            ) {
                Row(modifier = Modifier.padding(2.dp)) {
                    val isProductsActive = viewMode == SalesReportTableViewMode.PRODUCTS
                    val isInvoiceActive = viewMode == SalesReportTableViewMode.INVOICES

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isProductsActive) GeoPrimary else Color.Transparent)
                            .clickable { viewMode = SalesReportTableViewMode.PRODUCTS }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isArabic) "الأصناف (${itemBreakdowns.size})" else "Products (${itemBreakdowns.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isProductsActive) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isProductsActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isInvoiceActive) GeoPrimary else Color.Transparent)
                            .clickable { viewMode = SalesReportTableViewMode.INVOICES }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isArabic) "الفواتير (${allSalesInvoices.size})" else "Invoices (${allSalesInvoices.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isInvoiceActive) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isInvoiceActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ==========================================
        // 4. LONG DETAILED TABLE (Report Preview Container)
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
                if (viewMode == SalesReportTableViewMode.PRODUCTS) {
                    // ----------------------------------------------------
                    // PRODUCTS TABLE
                    // ----------------------------------------------------
                    // Table Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "الصنف / البيان" else "Item / Description",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1.5f),
                            textAlign = TextAlign.Start
                        )
                        Text(
                            text = if (isArabic) "الكمية" else "Qty",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.75f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isArabic) "إجمالي المبيعات" else "Total Sales",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = if (isArabic) "هامش الربح" else "Profit Margin",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1.15f),
                            textAlign = TextAlign.End
                        )
                    }

                    HorizontalDivider(color = GeoOutlineVariant, thickness = 0.8.dp)

                    if (itemBreakdowns.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = if (isArabic) "لا توجد بنود أصناف مسجلة لهذه الفترة." else "No item line details recorded for this period.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (isArabic) "يمكنك الاطلاع على قائمة الفواتير من التبويب أعلاه." else "You can inspect the sales invoices from the tab above.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // All product items rendered (long scrollable table)
                        itemBreakdowns.forEachIndexed { idx, item ->
                            val isEven = idx % 2 == 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isEven) Color.Transparent
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                                    .testTag("report_row_$idx"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Product Name
                                Text(
                                    text = item.productName,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Start
                                )

                                // Quantity Badge
                                Box(
                                    modifier = Modifier.weight(0.75f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = "${item.totalQuantity}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                // Total Sales
                                Text(
                                    text = AppCurrency.formatAmountWithDecimals(item.totalSales, isArabic),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = GeoPrimary,
                                    modifier = Modifier.weight(1.2f),
                                    textAlign = TextAlign.End
                                )

                                // Profit Margin
                                Text(
                                    text = AppCurrency.formatAmountWithDecimals(item.profitMargin, isArabic),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.profitMargin > 0) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.weight(1.15f),
                                    textAlign = TextAlign.End
                                )
                            }
                            if (idx < itemBreakdowns.size - 1) {
                                HorizontalDivider(color = GeoOutlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
                            }
                        }

                        // Sticky Grand Total Footer Row
                        HorizontalDivider(color = GeoOutlineVariant, thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GeoPrimary.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) "الإجمالي الكلي" else "Grand Total",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1.5f),
                                textAlign = TextAlign.Start
                            )
                            Text(
                                text = "$totalItemsQuantity",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(0.75f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = AppCurrency.formatAmountWithDecimals(totalSalesAmount, isArabic),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = GeoPrimary,
                                modifier = Modifier.weight(1.2f),
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = AppCurrency.formatAmountWithDecimals(totalItemProfitMargin, isArabic),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = StatusGreen,
                                modifier = Modifier.weight(1.15f),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                } else {
                    // ----------------------------------------------------
                    // INVOICES LOG TABLE
                    // ----------------------------------------------------
                    // Table Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "التاريخ / الفاتورة" else "Date / Invoice",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1.3f),
                            textAlign = TextAlign.Start
                        )
                        Text(
                            text = if (isArabic) "العميل" else "Customer",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.Start
                        )
                        Text(
                            text = if (isArabic) "النوع" else "Type",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.85f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isArabic) "المبلغ" else "Amount",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1.15f),
                            textAlign = TextAlign.End
                        )
                    }

                    HorizontalDivider(color = GeoOutlineVariant, thickness = 0.8.dp)

                    if (allSalesInvoices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isArabic) "لا توجد فواتير مبيعات مسجلة لهذه الفترة" else "No sales invoices recorded for this period",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        allSalesInvoices.forEachIndexed { idx, invoice ->
                            val isEven = idx % 2 == 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isEven) Color.Transparent
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                                    .testTag("report_row_$idx"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Date / Invoice
                                Column(modifier = Modifier.weight(1.3f)) {
                                    Text(
                                        text = invoice.date,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    val desc = invoice.title.ifBlank { invoice.notes.ifBlank { invoice.activityType } }
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Customer
                                Text(
                                    text = invoice.customerName,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1.2f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Start
                                )

                                // Payment Type Pill
                                Box(
                                    modifier = Modifier.weight(0.85f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val isCash = !invoice.isCredit && (
                                        invoice.activityType.contains("كاش") ||
                                        invoice.activityType.contains("Cash") ||
                                        (!invoice.activityType.contains("تسديد") &&
                                         !invoice.activityType.contains("Payment") &&
                                         !invoice.activityType.contains("آجل") &&
                                         !invoice.activityType.contains("دين"))
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isCash) StatusGreen.copy(alpha = 0.12f) else StatusAmber.copy(alpha = 0.12f),
                                        border = BorderStroke(
                                            0.5.dp,
                                            if (isCash) StatusGreen.copy(alpha = 0.3f) else StatusAmber.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Text(
                                            text = if (isCash) (if (isArabic) "كاش" else "Cash")
                                            else (if (isArabic) "آجل" else "Debt"),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            color = if (isCash) StatusGreen else StatusAmber,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                // Amount
                                Text(
                                    text = AppCurrency.formatAmountWithDecimals(invoice.amount, isArabic),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = GeoPrimary,
                                    modifier = Modifier.weight(1.15f),
                                    textAlign = TextAlign.End
                                )
                            }
                            if (idx < allSalesInvoices.size - 1) {
                                HorizontalDivider(color = GeoOutlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
                            }
                        }

                        // Sticky Grand Total Footer Row
                        HorizontalDivider(color = GeoOutlineVariant, thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GeoPrimary.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) "الإجمالي الكلي" else "Grand Total",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1.3f),
                                textAlign = TextAlign.Start
                            )
                            Text(
                                text = "${allSalesInvoices.size} ${if (isArabic) "فاتورة" else "inv"}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1.2f),
                                textAlign = TextAlign.Start
                            )
                            Text(
                                text = "",
                                modifier = Modifier.weight(0.85f)
                            )
                            Text(
                                text = AppCurrency.formatAmountWithDecimals(totalSalesAmount, isArabic),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = GeoPrimary,
                                modifier = Modifier.weight(1.15f),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}
