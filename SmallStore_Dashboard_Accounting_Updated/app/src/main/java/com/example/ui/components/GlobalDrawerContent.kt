package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.model.LanguageMode
import com.example.model.NavDestination
import com.example.model.StoreStrings
import com.example.viewmodel.AnalysisTab

/**
 * GLOBAL DRAWER (HAMBURGER MENU):
 * Hierarchical navigation menu connected to all existing screens and tabs.
 *
 * Structure:
 * 1. Home
 *    └── Notifications
 * 2. Accounts
 *    ├── Customers
 *    └── Customer Profile
 * 3. Analysis Center
 *    ├── Statistics
 *    ├── Account Statement
 *    └── Reports
 * 4. Purchases
 * 5. More
 *    ├── Store Information
 *    ├── App Settings
 *    ├── Data Center
 *    └── About
 *        ├── Privacy Policy
 *        ├── Terms of Use
 *        └── Contact Support
 */
@Composable
fun GlobalDrawerContent(
    currentDestination: NavDestination,
    languageMode: LanguageMode,
    unreadNotificationsCount: Int,
    storeName: String = StoreStrings.APP_NAME,
    storeOwnerName: String = "",
    currentAnalysisTab: AnalysisTab = AnalysisTab.STATISTICS,
    selectedCustomer: CustomerAccount? = null,
    allCustomers: List<CustomerAccount> = emptyList(),
    onSelectDestination: (NavDestination) -> Unit,
    onSelectAnalysisTab: (AnalysisTab) -> Unit = {},
    onSelectCustomerForProfile: (CustomerAccount) -> Unit = {},
    onToggleLanguage: (LanguageMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC

    // Expansion states for hierarchical groups
    var isHomeExpanded by rememberSaveable { mutableStateOf(currentDestination == NavDestination.NOTIFICATIONS) }
    var isAccountsExpanded by rememberSaveable {
        mutableStateOf(
            currentDestination in listOf(
                NavDestination.ACCOUNTS,
                NavDestination.CUSTOMER_DETAILS
            )
        )
    }
    var isAnalysisExpanded by rememberSaveable { mutableStateOf(currentDestination == NavDestination.ANALYSIS_CENTER) }
    var isMoreExpanded by rememberSaveable {
        mutableStateOf(
            currentDestination in listOf(
                NavDestination.MORE,
                NavDestination.MORE_SETTINGS,
                NavDestination.STORE_INFORMATION,
                NavDestination.APP_SETTINGS,
                NavDestination.DATA_CENTER,
                NavDestination.ABOUT,
                NavDestination.PRIVACY_POLICY,
                NavDestination.TERMS_OF_USE,
                NavDestination.CONTACT_SUPPORT
            )
        )
    }
    var isAboutExpanded by rememberSaveable {
        mutableStateOf(
            currentDestination in listOf(
                NavDestination.ABOUT,
                NavDestination.PRIVACY_POLICY,
                NavDestination.TERMS_OF_USE,
                NavDestination.CONTACT_SUPPORT
            )
        )
    }

    // Keep active parents expanded if user navigated outside the drawer
    LaunchedEffect(currentDestination) {
        when (currentDestination) {
            NavDestination.NOTIFICATIONS -> isHomeExpanded = true
            NavDestination.ACCOUNTS, NavDestination.CUSTOMER_DETAILS -> isAccountsExpanded = true
            NavDestination.ANALYSIS_CENTER -> isAnalysisExpanded = true
            NavDestination.MORE, NavDestination.MORE_SETTINGS, NavDestination.STORE_INFORMATION,
            NavDestination.APP_SETTINGS, NavDestination.DATA_CENTER -> isMoreExpanded = true
            NavDestination.ABOUT, NavDestination.PRIVACY_POLICY, NavDestination.TERMS_OF_USE,
            NavDestination.CONTACT_SUPPORT -> {
                isMoreExpanded = true
                isAboutExpanded = true
            }
            else -> {}
        }
    }

    // Dialog state for Customer Profile picker when no customer is pre-selected
    var showCustomerPickerDialog by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp, horizontal = 10.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = StoreStrings.APP_NAME,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (storeName.isNotBlank()) storeName else StoreStrings.APP_NAME,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (storeOwnerName.isNotBlank()) storeOwnerName else (if (isArabic) "إدارة المتجر والحسابات" else "Shop & Balance Management"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Hierarchical Navigation List
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // =============================================================
                // 1. HOME
                // =============================================================
                DrawerParentItem(
                    label = if (isArabic) StoreStrings.HOME_AR else StoreStrings.HOME_EN,
                    icon = Icons.Default.Home,
                    isSelected = currentDestination == NavDestination.HOME,
                    isExpanded = isHomeExpanded,
                    testTag = "drawer_item_home",
                    expandTestTag = "drawer_expand_home",
                    onToggleExpand = { isHomeExpanded = !isHomeExpanded },
                    onClick = {
                        isHomeExpanded = true
                        onSelectDestination(NavDestination.HOME)
                    }
                )

                AnimatedVisibility(
                    visible = isHomeExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        // 1.a Notifications
                        DrawerChildItem(
                            label = if (isArabic) StoreStrings.NOTIFICATIONS_AR else StoreStrings.NOTIFICATIONS_EN,
                            icon = Icons.Default.Notifications,
                            isSelected = currentDestination == NavDestination.NOTIFICATIONS,
                            indentLevel = 1,
                            badgeCount = unreadNotificationsCount,
                            testTag = "drawer_item_notifications",
                            onClick = { onSelectDestination(NavDestination.NOTIFICATIONS) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // =============================================================
                // 2. ACCOUNTS
                // =============================================================
                DrawerParentItem(
                    label = if (isArabic) StoreStrings.ACCOUNTS_AR else StoreStrings.ACCOUNTS_EN,
                    icon = Icons.Default.AccountBalanceWallet,
                    isSelected = currentDestination == NavDestination.ACCOUNTS,
                    isExpanded = isAccountsExpanded,
                    testTag = "drawer_item_accounts",
                    expandTestTag = "drawer_expand_accounts",
                    onToggleExpand = { isAccountsExpanded = !isAccountsExpanded },
                    onClick = {
                        isAccountsExpanded = true
                        onSelectDestination(NavDestination.ACCOUNTS)
                    }
                )

                AnimatedVisibility(
                    visible = isAccountsExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        // 2.a Customers (Accounts screen list)
                        DrawerChildItem(
                            label = if (isArabic) StoreStrings.CUSTOMERS_AR else StoreStrings.CUSTOMERS_EN,
                            icon = Icons.Default.People,
                            isSelected = currentDestination == NavDestination.ACCOUNTS,
                            indentLevel = 1,
                            testTag = "drawer_item_customers",
                            onClick = { onSelectDestination(NavDestination.ACCOUNTS) }
                        )

                        // 2.b Customer Profile
                        DrawerChildItem(
                            label = if (isArabic) StoreStrings.CUSTOMER_PROFILE_AR else StoreStrings.CUSTOMER_PROFILE_EN,
                            icon = Icons.Default.Badge,
                            isSelected = currentDestination == NavDestination.CUSTOMER_DETAILS,
                            indentLevel = 1,
                            testTag = "drawer_item_customer_profile",
                            onClick = {
                                if (selectedCustomer != null) {
                                    onSelectDestination(NavDestination.CUSTOMER_DETAILS)
                                } else {
                                    showCustomerPickerDialog = true
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // =============================================================
                // 3. ANALYSIS CENTER
                // =============================================================
                DrawerParentItem(
                    label = if (isArabic) StoreStrings.ANALYSIS_CENTER_AR else StoreStrings.ANALYSIS_CENTER_EN,
                    icon = Icons.Default.BarChart,
                    isSelected = currentDestination == NavDestination.ANALYSIS_CENTER,
                    isExpanded = isAnalysisExpanded,
                    testTag = "drawer_item_analysis_center",
                    expandTestTag = "drawer_expand_analysis",
                    onToggleExpand = { isAnalysisExpanded = !isAnalysisExpanded },
                    onClick = {
                        isAnalysisExpanded = true
                        onSelectDestination(NavDestination.ANALYSIS_CENTER)
                    }
                )

                AnimatedVisibility(
                    visible = isAnalysisExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        // 3.a Statistics Tab
                        DrawerChildItem(
                            label = if (isArabic) StoreStrings.TAB_STATISTICS_AR else StoreStrings.TAB_STATISTICS_EN,
                            icon = Icons.Default.Insights,
                            isSelected = currentDestination == NavDestination.ANALYSIS_CENTER && currentAnalysisTab == AnalysisTab.STATISTICS,
                            indentLevel = 1,
                            testTag = "drawer_item_analysis_statistics",
                            onClick = { onSelectAnalysisTab(AnalysisTab.STATISTICS) }
                        )

                        // 3.b Account Statement Tab
                        DrawerChildItem(
                            label = if (isArabic) StoreStrings.ACCOUNT_STATEMENT_AR else StoreStrings.ACCOUNT_STATEMENT_EN,
                            icon = Icons.Default.ReceiptLong,
                            isSelected = currentDestination == NavDestination.ANALYSIS_CENTER && currentAnalysisTab == AnalysisTab.ACCOUNT_STATEMENT,
                            indentLevel = 1,
                            testTag = "drawer_item_analysis_statement",
                            onClick = { onSelectAnalysisTab(AnalysisTab.ACCOUNT_STATEMENT) }
                        )

                        // 3.c Reports Tab
                        DrawerChildItem(
                            label = if (isArabic) StoreStrings.TAB_REPORTS_AR else StoreStrings.TAB_REPORTS_EN,
                            icon = Icons.Default.Assessment,
                            isSelected = currentDestination == NavDestination.ANALYSIS_CENTER && currentAnalysisTab == AnalysisTab.REPORTS,
                            indentLevel = 1,
                            testTag = "drawer_item_analysis_reports",
                            onClick = { onSelectAnalysisTab(AnalysisTab.REPORTS) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // =============================================================
                // 4. PURCHASES
                // =============================================================
                DrawerMenuItem(
                    label = if (isArabic) StoreStrings.PURCHASES_AR else StoreStrings.PURCHASES_EN,
                    icon = Icons.Default.ShoppingBag,
                    isSelected = currentDestination == NavDestination.PURCHASES,
                    testTag = "drawer_item_purchases",
                    onClick = { onSelectDestination(NavDestination.PURCHASES) }
                )

                Spacer(modifier = Modifier.height(2.dp))

                // =============================================================
                // 5. MORE
                // =============================================================
                DrawerParentItem(
                    label = if (isArabic) StoreStrings.MORE_AR else StoreStrings.MORE_EN,
                    icon = Icons.Default.MoreHoriz,
                    isSelected = currentDestination in listOf(NavDestination.MORE, NavDestination.MORE_SETTINGS),
                    isExpanded = isMoreExpanded,
                    testTag = "drawer_item_more",
                    expandTestTag = "drawer_expand_more",
                    onToggleExpand = { isMoreExpanded = !isMoreExpanded },
                    onClick = {
                        isMoreExpanded = true
                        onSelectDestination(NavDestination.MORE)
                    }
                )

                AnimatedVisibility(
                    visible = isMoreExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        // 5.a Store Information
                        DrawerChildItem(
                            label = if (isArabic) StoreStrings.STORE_INFORMATION_AR else StoreStrings.STORE_INFORMATION_EN,
                            icon = Icons.Default.Storefront,
                            isSelected = currentDestination == NavDestination.STORE_INFORMATION,
                            indentLevel = 1,
                            testTag = "drawer_item_store_info",
                            onClick = { onSelectDestination(NavDestination.STORE_INFORMATION) }
                        )

                        // 5.b App Settings
                        DrawerChildItem(
                            label = if (isArabic) StoreStrings.APP_SETTINGS_AR else StoreStrings.APP_SETTINGS_EN,
                            icon = Icons.Default.Settings,
                            isSelected = currentDestination == NavDestination.APP_SETTINGS,
                            indentLevel = 1,
                            testTag = "drawer_item_app_settings",
                            onClick = { onSelectDestination(NavDestination.APP_SETTINGS) }
                        )

                        // 5.c Data Center
                        DrawerChildItem(
                            label = if (isArabic) StoreStrings.DATA_CENTER_AR else StoreStrings.DATA_CENTER_EN,
                            icon = Icons.Default.Storage,
                            isSelected = currentDestination == NavDestination.DATA_CENTER,
                            indentLevel = 1,
                            testTag = "drawer_item_data_center",
                            onClick = { onSelectDestination(NavDestination.DATA_CENTER) }
                        )

                        // 5.d About (with its nested sub-children)
                        DrawerChildItem(
                            label = if (isArabic) StoreStrings.ABOUT_SMALLSTORE_AR else StoreStrings.ABOUT_SMALLSTORE_EN,
                            icon = Icons.Default.Info,
                            isSelected = currentDestination == NavDestination.ABOUT,
                            indentLevel = 1,
                            hasChildren = true,
                            isExpanded = isAboutExpanded,
                            testTag = "drawer_item_about",
                            expandTestTag = "drawer_expand_about",
                            onToggleExpand = { isAboutExpanded = !isAboutExpanded },
                            onClick = {
                                isAboutExpanded = true
                                onSelectDestination(NavDestination.ABOUT)
                            }
                        )

                        AnimatedVisibility(
                            visible = isAboutExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                // 5.d.i Privacy Policy
                                DrawerChildItem(
                                    label = if (isArabic) StoreStrings.PRIVACY_POLICY_AR else StoreStrings.PRIVACY_POLICY_EN,
                                    icon = Icons.Default.Lock,
                                    isSelected = currentDestination == NavDestination.PRIVACY_POLICY,
                                    indentLevel = 2,
                                    testTag = "drawer_item_privacy_policy",
                                    onClick = { onSelectDestination(NavDestination.PRIVACY_POLICY) }
                                )

                                // 5.d.ii Terms of Use
                                DrawerChildItem(
                                    label = if (isArabic) StoreStrings.TERMS_OF_USE_AR else StoreStrings.TERMS_OF_USE_EN,
                                    icon = Icons.Default.Description,
                                    isSelected = currentDestination == NavDestination.TERMS_OF_USE,
                                    indentLevel = 2,
                                    testTag = "drawer_item_terms_of_use",
                                    onClick = { onSelectDestination(NavDestination.TERMS_OF_USE) }
                                )

                                // 5.d.iii Contact Support
                                DrawerChildItem(
                                    label = if (isArabic) StoreStrings.CONTACT_SUPPORT_AR else StoreStrings.CONTACT_SUPPORT_EN,
                                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                                    isSelected = currentDestination == NavDestination.CONTACT_SUPPORT,
                                    indentLevel = 2,
                                    testTag = "drawer_item_contact_support",
                                    onClick = { onSelectDestination(NavDestination.CONTACT_SUPPORT) }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Quick Language Switcher in Drawer Footer (Arabic RTL <-> English LTR)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) "اللغة / Language" else "Language / اللغة",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LanguageBadge(
                        label = "العربية (RTL)",
                        isSelected = languageMode == LanguageMode.ARABIC,
                        testTag = "lang_toggle_ar",
                        onClick = { onToggleLanguage(LanguageMode.ARABIC) }
                    )
                    LanguageBadge(
                        label = "English (LTR)",
                        isSelected = languageMode == LanguageMode.ENGLISH,
                        testTag = "lang_toggle_en",
                        onClick = { onToggleLanguage(LanguageMode.ENGLISH) }
                    )
                }
            }
        }
    }

    // Customer Selection Dialog for Drawer Customer Profile navigation
    if (showCustomerPickerDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredCustomers = remember(allCustomers, searchQuery) {
            if (searchQuery.isBlank()) {
                allCustomers
            } else {
                val q = searchQuery.trim().lowercase()
                allCustomers.filter {
                    it.customerName.lowercase().contains(q) || it.phone.contains(q)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showCustomerPickerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "اختر زبوناً لعرض ملفه" else "Select Customer to View Profile",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    Text(
                        text = if (isArabic) "اختر أحد الزبائن لعرض بياناته وحسابه التفصيلي:" else "Choose a customer to open their complete profile:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (allCustomers.isNotEmpty()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = if (isArabic) "بحث بالاسم أو الهاتف..." else "Search by name or phone...",
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("drawer_customer_picker_search")
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (filteredCustomers.isEmpty()) {
                                item {
                                    Text(
                                        text = if (isArabic) "لا توجد نتائج مطابقة" else "No matching customers found",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                }
                            } else {
                                items(filteredCustomers, key = { it.id }) { customer ->
                                    CustomerPickerItem(
                                        customer = customer,
                                        isArabic = isArabic,
                                        onClick = {
                                            showCustomerPickerDialog = false
                                            onSelectCustomerForProfile(customer)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isArabic) "لا يوجد زبائن مسجلون حالياً" else "No registered customers found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            TextButton(
                                onClick = {
                                    showCustomerPickerDialog = false
                                    onSelectDestination(NavDestination.ACCOUNTS)
                                }
                            ) {
                                Text(text = if (isArabic) "الانتقال للحسابات لإضافة عميل" else "Go to Accounts to add a customer")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showCustomerPickerDialog = false },
                    modifier = Modifier.testTag("drawer_customer_picker_dismiss")
                ) {
                    Text(text = if (isArabic) "إلغاء" else "Cancel")
                }
            },
            modifier = Modifier.testTag("drawer_customer_picker_dialog")
        )
    }
}

@Composable
private fun CustomerPickerItem(
    customer: CustomerAccount,
    isArabic: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("drawer_customer_picker_item_${customer.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.customerName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (customer.phone.isNotBlank()) {
                    Text(
                        text = customer.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
            Text(
                text = "%.2f ${AppCurrency.SYMBOL}".format(customer.balance),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = if (customer.balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DrawerParentItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    isExpanded: Boolean,
    testTag: String,
    expandTestTag: String,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (badgeCount > 0) {
            NotificationBadge(unreadCount = badgeCount)
            Spacer(modifier = Modifier.width(4.dp))
        }
        IconButton(
            onClick = onToggleExpand,
            modifier = Modifier
                .size(36.dp)
                .testTag(expandTestTag)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DrawerChildItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    indentLevel: Int = 1,
    testTag: String,
    badgeCount: Int = 0,
    hasChildren: Boolean = false,
    isExpanded: Boolean = false,
    expandTestTag: String = "",
    onToggleExpand: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val startIndent: Dp = if (indentLevel == 2) 42.dp else 24.dp
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp)
            .padding(start = startIndent)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(if (indentLevel == 2) 16.dp else 19.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (indentLevel == 2) 13.sp else 14.sp
            ),
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (badgeCount > 0) {
            NotificationBadge(unreadCount = badgeCount)
            Spacer(modifier = Modifier.width(4.dp))
        }
        if (hasChildren && onToggleExpand != null) {
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier
                    .size(32.dp)
                    .testTag(expandTestTag)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerMenuItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (badgeCount > 0) {
            NotificationBadge(unreadCount = badgeCount)
        }
    }
}

@Composable
private fun LanguageBadge(
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
