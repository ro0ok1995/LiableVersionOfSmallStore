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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditScore
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.model.StoreStrings
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
import com.example.model.CustomerAccount
import com.example.model.StoreInfo
import com.example.model.TransactionItem
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusRedBg
import com.example.util.ReportPreviewRow
import com.example.viewmodel.CustomerDebtAgingResult

/**
 * Filter mode for customer transactions in the detailed table preview.
 */
private enum class CustomerTxFilterMode {
    ALL,
    CASH,
    CREDIT,
    PAYMENTS
}

/**
 * Dedicated visual presentation for "Comprehensive Customer Report" (تقرير العميل الشامل).
 *
 * Strict Visual Order from TOP to BOTTOM:
 * 1. Customer Identity / Header Area
 *    - Customer name
 *    - Phone number when available
 *    - Current outstanding balance
 * 2. Financial Summary Cards
 *    - Cash purchases
 *    - Credit purchases
 *    - Payments / settlements
 * 3. Debt Aging Section
 *    - 0–30 days
 *    - 31–60 days
 *    - 61–90 days
 *    - 90+ days
 * 4. Most Requested Products Section
 *    - Products requested by this customer with quantity & total amount
 * 5. Report Preview Section
 *    - Section header, record count badge, interactive filter pills
 * 6. Long Detailed Transaction Table
 *    - Date
 *    - Description
 *    - Type
 *    - Amount
 *    - Grand Total Footer
 */
@Composable
fun ComprehensiveCustomerReportPresentation(
    isArabic: Boolean,
    currency: String,
    periodLabel: String,
    storeInfo: StoreInfo,
    selectedCustomer: CustomerAccount?,
    customerTransactions: List<TransactionItem>,
    customerCashPurchases: Double,
    customerDebtPurchases: Double,
    customerPayments: Double,
    singleCustomerAging: CustomerDebtAgingResult?,
    customerItemBreakdowns: List<AggregatedProductLine>,
    previewRows: List<ReportPreviewRow>,
    modifier: Modifier = Modifier
) {
    if (selectedCustomer == null) {
        // Empty state when no customer has been selected yet
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, GeoOutlineVariant),
            modifier = modifier
                .fillMaxWidth()
                .testTag("report_customer_empty_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(GeoPrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = GeoPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = if (isArabic) "يرجى اختيار عميل لعرض التقرير الشامل" else "Please select a customer to view the comprehensive report",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (isArabic)
                        "حدد العميل من القائمة أعلاه للاطلاع على كشف الحساب المالي، وأعمار الديون، والأصناف المطلوبة، وسجل الحركات."
                    else
                        "Select a customer above to view their financial summary, debt aging, requested products, and detailed transaction ledger.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var filterMode by remember { mutableStateOf(CustomerTxFilterMode.ALL) }

    // Categorized transaction lists for this customer
    val cashTxList = remember(customerTransactions) {
        customerTransactions.filter { tx ->
            !tx.isCredit && (tx.activityType.contains("كاش") || tx.activityType.contains("Cash") ||
                (!tx.activityType.contains("تسديد") && !tx.activityType.contains("Payment") &&
                    !tx.activityType.contains("آجل") && !tx.activityType.contains("دين") && !tx.activityType.contains("Debt")))
        }
    }

    val debtTxList = remember(customerTransactions) {
        customerTransactions.filter { tx ->
            tx.isCredit || tx.activityType.contains("آجل") || tx.activityType.contains("دين") ||
                tx.activityType.contains("Debt") || tx.activityType.contains("شراء بالدين")
        }
    }

    val paymentsTxList = remember(customerTransactions) {
        customerTransactions.filter { tx ->
            tx.activityType.contains("تسديد") || tx.activityType.contains("Payment")
        }
    }

    // Filtered transaction list for the detailed table
    val displayedTransactions = remember(customerTransactions, filterMode) {
        when (filterMode) {
            CustomerTxFilterMode.ALL -> customerTransactions
            CustomerTxFilterMode.CASH -> cashTxList
            CustomerTxFilterMode.CREDIT -> debtTxList
            CustomerTxFilterMode.PAYMENTS -> paymentsTxList
        }
    }

    val displayedTotalAmount = remember(displayedTransactions) {
        displayedTransactions.sumOf { it.amount }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("comprehensive_customer_report_content"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =====================================================================
        // SECTION 1: Customer Identity / Header Area
        // =====================================================================
        CustomerIdentityHeaderCard(
            customer = selectedCustomer,
            periodLabel = periodLabel,
            storeInfo = storeInfo,
            isArabic = isArabic
        )

        // =====================================================================
        // SECTION 2: Financial Summary Cards
        // =====================================================================
        CustomerFinancialSummaryCards(
            cashPurchases = customerCashPurchases,
            cashCount = cashTxList.size,
            debtPurchases = customerDebtPurchases,
            debtCount = debtTxList.size,
            payments = customerPayments,
            paymentsCount = paymentsTxList.size,
            isArabic = isArabic
        )

        // =====================================================================
        // SECTION 3: Debt Aging Section
        // =====================================================================
        CustomerDebtAgingSection(
            customer = selectedCustomer,
            aging = singleCustomerAging,
            isArabic = isArabic
        )

        // =====================================================================
        // SECTION 4: Most Requested Products Section
        // =====================================================================
        CustomerMostRequestedProductsSection(
            itemBreakdowns = customerItemBreakdowns,
            isArabic = isArabic
        )

        // =====================================================================
        // SECTION 5: Report Preview Section Header & Filter Pills
        // =====================================================================
        CustomerReportPreviewHeader(
            totalCount = customerTransactions.size,
            cashCount = cashTxList.size,
            debtCount = debtTxList.size,
            paymentsCount = paymentsTxList.size,
            activeFilter = filterMode,
            onFilterSelected = { filterMode = it },
            isArabic = isArabic
        )

        // =====================================================================
        // SECTION 6: Long Detailed Transaction Table
        // =====================================================================
        CustomerDetailedTransactionsTable(
            displayedTransactions = displayedTransactions,
            displayedTotalAmount = displayedTotalAmount,
            filterMode = filterMode,
            isArabic = isArabic
        )
    }
}

// =============================================================================
// SUB-COMPONENTS
// =============================================================================

/**
 * 1. Customer Identity / Header Area
 */
@Composable
private fun CustomerIdentityHeaderCard(
    customer: CustomerAccount,
    periodLabel: String,
    storeInfo: StoreInfo,
    isArabic: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GeoOutlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("report_customer_summary_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Row: Customer Avatar, Identity & Period Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Customer Avatar Initial
                    val initial = customer.customerName.trim().take(1).uppercase()
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(GeoPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (initial.isNotBlank()) initial else "ع",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = GeoPrimary
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = customer.customerName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (customer.phone.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = customer.phone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Period chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(0.8.dp, GeoOutlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = GeoPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = periodLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            HorizontalDivider(color = GeoOutlineVariant.copy(alpha = 0.6f), thickness = 0.8.dp)

            // Current Outstanding Balance Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (customer.balance > 0) StatusRedBg.copy(alpha = 0.6f)
                        else StatusGreenBg.copy(alpha = 0.6f)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isArabic) "الرصيد المستحق الحالي" else "Current Outstanding Balance",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = if (customer.balance > 0) StatusRed else StatusGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (customer.balance > 0) {
                                if (isArabic) "مطلوب سداده" else "Balance Due"
                            } else {
                                if (isArabic) "مسدد بالكامل / لا توجد ديون" else "Fully Settled / Zero Debt"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (customer.balance > 0) StatusRed else StatusGreen
                        )
                    }
                }

                Text(
                    text = AppCurrency.formatAmountWithDecimals(customer.balance, isArabic),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = if (customer.balance > 0) StatusRed else StatusGreen
                )
            }
        }
    }
}

/**
 * 2. Financial Summary Cards (Cash, Credit, Payments)
 */
@Composable
private fun CustomerFinancialSummaryCards(
    cashPurchases: Double,
    cashCount: Int,
    debtPurchases: Double,
    debtCount: Int,
    payments: Double,
    paymentsCount: Int,
    isArabic: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Cash Purchases
        CustomerSummaryKpiCard(
            title = if (isArabic) "مشتريات كاش" else "Cash Purchases",
            amount = cashPurchases,
            countText = "$cashCount ${if (isArabic) "عملية" else "txs"}",
            icon = Icons.Default.Payments,
            accentColor = StatusGreen,
            testTag = "stat_customer_cash",
            modifier = Modifier.weight(1f),
            isArabic = isArabic
        )

        // 2. Credit Purchases
        CustomerSummaryKpiCard(
            title = if (isArabic) "مشتريات آجل" else "Credit Purchases",
            amount = debtPurchases,
            countText = "$debtCount ${if (isArabic) "فاتورة" else "invoices"}",
            icon = Icons.Default.CreditScore,
            accentColor = StatusAmber,
            testTag = "stat_customer_debt",
            modifier = Modifier.weight(1f),
            isArabic = isArabic
        )

        // 3. Payments & Settlements
        CustomerSummaryKpiCard(
            title = if (isArabic) "تسديدات" else "Payments",
            amount = payments,
            countText = "$paymentsCount ${if (isArabic) "دفعة" else "receipts"}",
            icon = Icons.Default.PriceCheck,
            accentColor = StatusBlue,
            testTag = "stat_customer_payments",
            modifier = Modifier.weight(1f),
            isArabic = isArabic
        )
    }
}

@Composable
private fun CustomerSummaryKpiCard(
    title: String,
    amount: Double,
    countText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    isArabic: Boolean
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GeoOutlineVariant),
        modifier = modifier.testTag(testTag)
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
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = AppCurrency.formatAmountWithDecimals(amount, isArabic),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 3. Debt Aging Section (0–30, 31–60, 61–90, 90+)
 */
@Composable
private fun CustomerDebtAgingSection(
    customer: CustomerAccount,
    aging: CustomerDebtAgingResult?,
    isArabic: Boolean
) {
    val b0To30 = aging?.bucket0To30 ?: 0.0
    val b31To60 = aging?.bucket31To60 ?: 0.0
    val b61To90 = aging?.bucket61To90 ?: 0.0
    val b90Plus = aging?.bucket90Plus ?: 0.0
    val totalDebt = aging?.currentDebt ?: customer.balance

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GeoOutlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("customer_debt_aging_section")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GeoPrimary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = GeoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isArabic) "تحليل أعمار الديون" else "Debt Aging Analysis",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isArabic) "توزيع مبالغ الديون حسب مدة استحقاقها الفعلية" else "Aging distribution calculated from actual transaction dates",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (totalDebt > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = StatusRedBg
                    ) {
                        Text(
                            text = AppCurrency.formatAmountWithDecimals(totalDebt, isArabic),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = StatusRed,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 4 Aging Bucket Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AgingBucketCard(
                    bucketLabel = if (isArabic) "0–30 يوم" else "0–30d",
                    bucketAmount = b0To30,
                    tintColor = if (b0To30 > 0) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    hasAmount = b0To30 > 0,
                    modifier = Modifier.weight(1f),
                    isArabic = isArabic
                )
                AgingBucketCard(
                    bucketLabel = if (isArabic) "31–60 يوم" else "31–60d",
                    bucketAmount = b31To60,
                    tintColor = if (b31To60 > 0) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    hasAmount = b31To60 > 0,
                    modifier = Modifier.weight(1f),
                    isArabic = isArabic
                )
                AgingBucketCard(
                    bucketLabel = if (isArabic) "61–90 يوم" else "61–90d",
                    bucketAmount = b61To90,
                    tintColor = if (b61To90 > 0) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    hasAmount = b61To90 > 0,
                    modifier = Modifier.weight(1f),
                    isArabic = isArabic
                )
                AgingBucketCard(
                    bucketLabel = if (isArabic) "+90 يوم" else "90+d",
                    bucketAmount = b90Plus,
                    tintColor = if (b90Plus > 0) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    hasAmount = b90Plus > 0,
                    modifier = Modifier.weight(1f),
                    isArabic = isArabic
                )
            }

            // Visual multi-segment proportion bar if customer has outstanding debt
            if (totalDebt > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val p0 = (b0To30 / totalDebt).toFloat().coerceIn(0f, 1f)
                        val p31 = (b31To60 / totalDebt).toFloat().coerceIn(0f, 1f)
                        val p61 = (b61To90 / totalDebt).toFloat().coerceIn(0f, 1f)
                        val p90 = (b90Plus / totalDebt).toFloat().coerceIn(0f, 1f)

                        if (p0 > 0f) Box(modifier = Modifier.weight(p0).fillMaxWidth().background(StatusAmber))
                        if (p31 > 0f) Box(modifier = Modifier.weight(p31).fillMaxWidth().background(StatusAmber.copy(alpha = 0.8f)))
                        if (p61 > 0f) Box(modifier = Modifier.weight(p61).fillMaxWidth().background(StatusRed.copy(alpha = 0.8f)))
                        if (p90 > 0f) Box(modifier = Modifier.weight(p90).fillMaxWidth().background(StatusRed))
                    }
                }
            }
        }
    }
}

@Composable
private fun AgingBucketCard(
    bucketLabel: String,
    bucketAmount: Double,
    tintColor: Color,
    hasAmount: Boolean,
    modifier: Modifier = Modifier,
    isArabic: Boolean
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasAmount) tintColor.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(
            0.8.dp,
            if (hasAmount) tintColor.copy(alpha = 0.35f) else GeoOutlineVariant
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = bucketLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = AppCurrency.formatAmount(bucketAmount),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (hasAmount) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp
                ),
                color = tintColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 4. Most Requested Products Section
 */
@Composable
private fun CustomerMostRequestedProductsSection(
    itemBreakdowns: List<AggregatedProductLine>,
    isArabic: Boolean
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GeoOutlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("customer_most_requested_products_section")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GeoPrimary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = GeoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = if (isArabic) "أكثر الأصناف طلباً لهذا العميل" else "Most Requested Products",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (itemBreakdowns.isNotEmpty()) {
                    Text(
                        text = "${itemBreakdowns.size} ${if (isArabic) "صنف" else "items"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (itemBreakdowns.isEmpty()) {
                // Empty state for products
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isArabic) "لا توجد تفاصيل أصناف فردية مسجلة لهذا العميل في هذه الفترة"
                        else "No detailed item line records found for this customer in this period",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Products list with ranking badges, quantities, and sales totals
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemBreakdowns.take(5).forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (idx == 0) GeoPrimary.copy(alpha = 0.05f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Rank badge
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (idx == 0) GeoPrimary else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "#${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = if (idx == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = item.productName,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = "${item.totalQuantity} ${if (isArabic) "قطعة" else "pcs"}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = AppCurrency.formatAmountWithDecimals(item.totalSales, isArabic),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = GeoPrimary
                                )
                            }
                        }
                    }

                    if (itemBreakdowns.size > 5) {
                        Text(
                            text = if (isArabic) "+ ${itemBreakdowns.size - 5} أصناف إضافية" else "+ ${itemBreakdowns.size - 5} more items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 5. Report Preview Section Header with Filter Pills
 */
@Composable
private fun CustomerReportPreviewHeader(
    totalCount: Int,
    cashCount: Int,
    debtCount: Int,
    paymentsCount: Int,
    activeFilter: CustomerTxFilterMode,
    onFilterSelected: (CustomerTxFilterMode) -> Unit,
    isArabic: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("customer_report_preview_header"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(GeoPrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = null,
                        tint = GeoPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = if (isArabic) StoreStrings.REPORT_PREVIEW_TITLE_AR else StoreStrings.REPORT_PREVIEW_TITLE_EN,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GeoPrimary.copy(alpha = 0.08f),
                border = BorderStroke(0.8.dp, GeoPrimary.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "$totalCount ${if (isArabic) "معاملة" else "records"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = GeoPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Filter Pills Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CustomerFilterPill(
                label = if (isArabic) "الكل ($totalCount)" else "All ($totalCount)",
                isSelected = activeFilter == CustomerTxFilterMode.ALL,
                onClick = { onFilterSelected(CustomerTxFilterMode.ALL) },
                activeColor = GeoPrimary,
                modifier = Modifier.weight(1f)
            )
            CustomerFilterPill(
                label = if (isArabic) "كاش ($cashCount)" else "Cash ($cashCount)",
                isSelected = activeFilter == CustomerTxFilterMode.CASH,
                onClick = { onFilterSelected(CustomerTxFilterMode.CASH) },
                activeColor = StatusGreen,
                modifier = Modifier.weight(1f)
            )
            CustomerFilterPill(
                label = if (isArabic) "آجل ($debtCount)" else "Credit ($debtCount)",
                isSelected = activeFilter == CustomerTxFilterMode.CREDIT,
                onClick = { onFilterSelected(CustomerTxFilterMode.CREDIT) },
                activeColor = StatusAmber,
                modifier = Modifier.weight(1f)
            )
            CustomerFilterPill(
                label = if (isArabic) "تسديد ($paymentsCount)" else "Payments ($paymentsCount)",
                isSelected = activeFilter == CustomerTxFilterMode.PAYMENTS,
                onClick = { onFilterSelected(CustomerTxFilterMode.PAYMENTS) },
                activeColor = StatusBlue,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CustomerFilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) activeColor else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isSelected) activeColor else GeoOutlineVariant
        ),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp
                ),
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 6. Long Detailed Transaction Table
 * Columns: Date, Description, Type, Amount
 */
@Composable
private fun CustomerDetailedTransactionsTable(
    displayedTransactions: List<TransactionItem>,
    displayedTotalAmount: Double,
    filterMode: CustomerTxFilterMode,
    isArabic: Boolean
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GeoOutlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("report_preview_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Table Column Headers Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date
                Text(
                    text = if (isArabic) "التاريخ" else "Date",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1.0f)
                )

                // Description
                Text(
                    text = if (isArabic) "البيان / الوصف" else "Description",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1.4f)
                )

                // Type
                Text(
                    text = if (isArabic) "النوع" else "Type",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1.1f)
                )

                // Amount
                Text(
                    text = if (isArabic) "المبلغ" else "Amount",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.1f)
                )
            }

            // Transaction Rows
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
                            text = if (isArabic) "لا توجد حركات مسجلة لهذا العميل في هذه الفترة"
                            else "No transactions recorded for this customer in this period",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                displayedTransactions.forEachIndexed { idx, tx ->
                    val isPayment = tx.activityType.contains("تسديد") || tx.activityType.contains("Payment")
                    val isCredit = tx.isCredit || tx.activityType.contains("آجل") || tx.activityType.contains("دين") ||
                        tx.activityType.contains("Debt") || tx.activityType.contains("شراء بالدين")

                    val typeLabel = when {
                        isPayment -> if (isArabic) "تسديد" else "Payment"
                        isCredit -> if (isArabic) "شراء آجل" else "Credit"
                        else -> if (isArabic) "شراء كاش" else "Cash"
                    }

                    val typeColor = when {
                        isPayment -> StatusBlue
                        isCredit -> StatusAmber
                        else -> StatusGreen
                    }

                    val description = tx.notes.ifBlank {
                        tx.title.ifBlank { tx.activityType }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (idx % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("report_row_$idx"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Date
                        Column(
                            modifier = Modifier.weight(1.0f),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = tx.date,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (tx.relativeTime.isNotBlank()) {
                                Text(
                                    text = tx.relativeTime,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        // 2. Description
                        Text(
                            text = description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.4f)
                        )

                        // 3. Type Badge
                        Box(modifier = Modifier.weight(1.1f)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = typeColor.copy(alpha = 0.12f),
                                border = BorderStroke(0.6.dp, typeColor.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
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
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp
                                        ),
                                        color = typeColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // 4. Amount
                        Text(
                            text = AppCurrency.formatAmountWithDecimals(tx.amount, isArabic),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                }

                HorizontalDivider(color = GeoOutlineVariant, thickness = 1.dp)

                // Grand Total / Summary Footer Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(GeoPrimary.copy(alpha = 0.06f))
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isArabic) "إجمالي العمليات المعروضة" else "Total Displayed Operations",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "(${displayedTransactions.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = AppCurrency.formatAmountWithDecimals(displayedTotalAmount, isArabic),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = GeoPrimary
                    )
                }
            }
        }
    }
}
