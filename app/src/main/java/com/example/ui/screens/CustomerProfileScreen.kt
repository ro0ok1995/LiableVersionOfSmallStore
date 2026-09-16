package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.model.LanguageMode
import com.example.model.StoreStrings
import com.example.ui.components.CustomerSelectorField
import com.example.ui.theme.statusGreen
import com.example.ui.theme.statusGreenContainer
import com.example.ui.theme.statusRed
import com.example.ui.theme.statusRedContainer
import java.util.Locale

/**
 * Full-screen Customer Profile (ملف الزبون)
 *
 * Header:
 * - Back arrow
 * - Title ("ملف الزبون" / "Customer Profile")
 * - Subtitle with selected customer name
 * - Edit icon button (safe informational feedback)
 *
 * Customer selector at top:
 * - When opened without a preselected customer, allows choosing an existing customer.
 * - When opened with an already selected customer, shows that customer immediately.
 *
 * Customer Information Card:
 * - Avatar/initial, name, phone (if present), balance/debt, and status badge.
 * - Strictly uses existing CustomerAccount fields (no invented address).
 *
 * Basic Actions:
 * - 1. Record Purchase ("تسجيل مشتريات") -> Existing purchases flow.
 * - 2. View Account Statement ("عرض كشف الحساب") -> Existing Analysis Center statement flow.
 * - 3. Record Payment ("تسجيل دفعة سداد") -> Existing Quick Payment flow.
 */
@Composable
fun CustomerProfileScreen(
    customer: CustomerAccount?,
    allCustomers: List<CustomerAccount>,
    languageMode: LanguageMode,
    onBackClick: () -> Unit,
    onCustomerSelected: (CustomerAccount?) -> Unit,
    onRecordPurchase: (CustomerAccount) -> Unit,
    onViewAccountStatement: (CustomerAccount) -> Unit,
    onRecordPayment: (CustomerAccount) -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val context = LocalContext.current
    val currency = AppCurrency.SYMBOL

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("customer_profile_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isArabic) "رجوع" else "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isArabic) StoreStrings.CUSTOMER_PROFILE_AR else StoreStrings.CUSTOMER_PROFILE_EN,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("customer_profile_title")
                        )
                        if (customer != null) {
                            Text(
                                text = customer.customerName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val msg = if (isArabic) {
                                "تعديل بيانات العميل غير متاح حالياً"
                            } else {
                                "Customer editing is not available currently"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("customer_profile_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = if (isArabic) "تعديل" else "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("customer_profile_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =================================================================
            // CUSTOMER SELECTOR AT TOP
            // =================================================================
            Column {
                Text(
                    text = if (isArabic) "اختر الزبون" else "Select Customer",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                CustomerSelectorField(
                    customers = allCustomers,
                    selectedCustomer = customer,
                    onCustomerSelected = onCustomerSelected,
                    isArabic = isArabic,
                    allowAllCustomers = false,
                    placeholderText = if (isArabic) "اختر زبوناً لعرض الملف..." else "Select customer to view profile...",
                    showBalance = true,
                    isRequired = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_profile_selector"),
                    testTag = "customer_profile_selector_field"
                )
            }

            if (customer == null) {
                // Empty state when opened from Global Drawer without pre-selection
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .testTag("customer_profile_empty_state")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isArabic) "يرجى اختيار زبون" else "Please select a customer",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isArabic) {
                                "اختر أحد الزبائن المسجلين من القائمة أعلاه لعرض ملفه وعملياته الأساسية."
                            } else {
                                "Choose a customer from the dropdown above to view their profile and perform actions."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // =============================================================
                // CUSTOMER INFORMATION CARD
                // =============================================================
                val hasDebt = customer.balance > 0.0
                val isPaidUp = customer.balance <= 0.0

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_info_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = customer.customerName.take(1),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = customer.customerName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("customer_profile_name")
                                )

                                if (customer.phone.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = customer.phone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.testTag("customer_profile_phone")
                                        )
                                    }
                                }
                            }

                            // Status badge
                            Surface(
                                color = if (isPaidUp) {
                                    MaterialTheme.colorScheme.statusGreenContainer
                                } else {
                                    MaterialTheme.colorScheme.statusRedContainer
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("customer_profile_status_badge")
                            ) {
                                Text(
                                    text = if (isPaidUp) {
                                        if (isArabic) "مسدد بالكامل" else "Paid Up"
                                    } else {
                                        if (isArabic) "عليه مستحقات" else "Has Debt"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isPaidUp) {
                                        MaterialTheme.colorScheme.statusGreen
                                    } else {
                                        MaterialTheme.colorScheme.statusRed
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 14.dp)
                        )

                        // Balances Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (isArabic) StoreStrings.TOTAL_BALANCE_AR else StoreStrings.TOTAL_BALANCE_EN,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format(Locale.US, "%,.2f %s", customer.balance, currency),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    ),
                                    color = if (hasDebt) {
                                        MaterialTheme.colorScheme.statusRed
                                    } else {
                                        MaterialTheme.colorScheme.statusGreen
                                    },
                                    modifier = Modifier.testTag("customer_profile_balance")
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isArabic) StoreStrings.TOTAL_DEBT_AR else StoreStrings.TOTAL_DEBT_EN,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format(Locale.US, "%,.2f %s", customer.totalDebt, currency),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    ),
                                    color = MaterialTheme.colorScheme.statusRed,
                                    modifier = Modifier.testTag("customer_profile_debt")
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // MAIN SECTION: BASIC ACTIONS (العمليات الأساسية)
                // =============================================================
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isArabic) StoreStrings.BASIC_ACTIONS_AR else StoreStrings.BASIC_ACTIONS_EN,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .testTag("basic_actions_title")
                    )

                    // 1. Record Purchase ("تسجيل مشتريات")
                    ProfileActionButton(
                        icon = Icons.Default.ShoppingBag,
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        title = if (isArabic) StoreStrings.RECORD_PURCHASE_AR else StoreStrings.RECORD_PURCHASE_EN,
                        subtitle = if (isArabic) "تسجيل عملية بيع ومشتريات جديدة للزبون" else "Record new sales & purchases for customer",
                        testTag = "action_record_purchase",
                        onClick = { onRecordPurchase(customer) }
                    )

                    // 2. View Account Statement ("عرض كشف الحساب")
                    ProfileActionButton(
                        icon = Icons.Default.ReceiptLong,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        iconContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        title = if (isArabic) StoreStrings.VIEW_ACCOUNT_STATEMENT_AR else StoreStrings.VIEW_ACCOUNT_STATEMENT_EN,
                        subtitle = if (isArabic) "الاطلاع على الحركات والتقرير المالي التفصيلي" else "View detailed transactions & statement",
                        testTag = "action_view_statement",
                        onClick = { onViewAccountStatement(customer) }
                    )

                    // 3. Record Payment ("تسجيل دفعة سداد")
                    ProfileActionButton(
                        icon = Icons.Default.Payments,
                        iconTint = MaterialTheme.colorScheme.statusGreen,
                        iconContainerColor = MaterialTheme.colorScheme.statusGreenContainer,
                        title = if (isArabic) StoreStrings.RECORD_PAYMENT_AR else StoreStrings.RECORD_PAYMENT_EN,
                        subtitle = if (isArabic) "تسجيل دفعة نقدية فورية لتقليص المديونية" else "Record cash payment to settle customer balance",
                        testTag = "action_record_payment",
                        onClick = { onRecordPayment(customer) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileActionButton(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    iconContainerColor: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = iconContainerColor,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
