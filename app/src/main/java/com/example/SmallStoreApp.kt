package com.example

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AccountFilter
import com.example.model.AppThemeMode
import com.example.model.CustomerAccount
import com.example.model.LanguageMode
import com.example.model.MoreMenuItemId
import com.example.model.NavDestination
import com.example.model.StoreInfo
import com.example.model.StoreStrings
import com.example.model.ThemeDisplayMode
import com.example.ui.components.GlobalBottomBar
import com.example.ui.components.GlobalDrawerContent
import com.example.ui.components.GlobalTopBar
import com.example.ui.components.PlusActionSheet
import com.example.ui.components.StoreInfoConflictSheet
import com.example.ui.components.UnifiedSettlementSheet
import com.example.ui.screens.AboutAppScreen
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.AnalysisCenterScreen
import com.example.ui.screens.AppSettingsScreen
import com.example.ui.screens.ArchiveScreen
import com.example.ui.screens.BackupRestoreScreen
import com.example.ui.screens.ContactSupportScreen
import com.example.ui.screens.CustomerManagementScreen
import com.example.ui.screens.ProductManagementScreen
import com.example.ui.screens.CustomerProfileScreen
import com.example.ui.screens.DataCenterScreen
import com.example.ui.screens.GenericContentScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PurchasesScreen
import com.example.ui.screens.QuickPaymentScreen
import com.example.ui.screens.StoreInformationScreen
import com.example.ui.theme.SmallStoreTheme
import com.example.viewmodel.AnalysisCenterViewModel
import com.example.viewmodel.AnalysisTab
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SmallStoreApp(
    mainViewModel: MainViewModel = viewModel(),
    analysisViewModel: AnalysisCenterViewModel = viewModel()
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val analysisUiState by analysisViewModel.uiState.collectAsState()
    val isArabic = uiState.languageMode == LanguageMode.ARABIC
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Sync drawer state with ViewModel
    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }

    // Handle Android system back press
    BackHandler(enabled = drawerState.isOpen || uiState.currentDestination != NavDestination.HOME) {
        focusManager.clearFocus()
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
            mainViewModel.closeDrawer()
        } else if (uiState.currentDestination == NavDestination.CUSTOMER_DETAILS) {
            mainViewModel.navigateBackFromCustomerDetails()
        } else if (uiState.currentDestination in listOf(NavDestination.CUSTOMER_MANAGEMENT, NavDestination.PRODUCT_MANAGEMENT, NavDestination.ARCHIVE, NavDestination.BACKUP_RESTORE)) {
            mainViewModel.navigateTo(NavDestination.DATA_CENTER)
        } else if (uiState.currentDestination in listOf(NavDestination.PRIVACY_POLICY, NavDestination.TERMS_OF_USE, NavDestination.CONTACT_SUPPORT)) {
            mainViewModel.navigateTo(NavDestination.ABOUT)
        } else if (uiState.currentDestination in listOf(NavDestination.ABOUT, NavDestination.STORE_INFORMATION, NavDestination.APP_SETTINGS, NavDestination.DATA_CENTER)) {
            mainViewModel.navigateTo(NavDestination.MORE)
        } else if (uiState.currentDestination != NavDestination.HOME) {
            mainViewModel.navigateTo(NavDestination.HOME)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        SmallStoreTheme(
            themeMode = uiState.themeMode,
            displayMode = uiState.displayMode
        ) {
            val unreadNotifications = uiState.notifications.count { !it.isRead }

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    GlobalDrawerContent(
                        currentDestination = uiState.currentDestination,
                        languageMode = uiState.languageMode,
                        unreadNotificationsCount = unreadNotifications,
                        storeName = uiState.storeInfo.storeName,
                        storeOwnerName = uiState.storeInfo.ownerName,
                        currentAnalysisTab = analysisUiState.currentTab,
                        selectedCustomer = uiState.accountsSelectedCustomerDetails,
                        allCustomers = uiState.customers,
                        onSelectDestination = { dest ->
                            focusManager.clearFocus()
                            mainViewModel.navigateTo(dest)
                            coroutineScope.launch { drawerState.close() }
                            mainViewModel.closeDrawer()
                        },
                        onSelectAnalysisTab = { tab ->
                            analysisViewModel.selectTab(tab)
                            focusManager.clearFocus()
                            mainViewModel.navigateTo(NavDestination.ANALYSIS_CENTER)
                            coroutineScope.launch { drawerState.close() }
                            mainViewModel.closeDrawer()
                        },
                        onSelectCustomerForProfile = { customer ->
                            mainViewModel.selectCustomerDetails(customer)
                            focusManager.clearFocus()
                            mainViewModel.navigateTo(NavDestination.CUSTOMER_DETAILS)
                            coroutineScope.launch { drawerState.close() }
                            mainViewModel.closeDrawer()
                        },
                        onToggleLanguage = { mode ->
                            focusManager.clearFocus()
                            mainViewModel.setLanguageMode(mode)
                        }
                    )
                },
                modifier = Modifier.testTag("app_navigation_drawer")
            ) {
                val isTopLevelScreen = when (uiState.currentDestination) {
                    NavDestination.HOME,
                    NavDestination.ACCOUNTS,
                    NavDestination.ANALYSIS_CENTER,
                    NavDestination.MORE,
                    NavDestination.MORE_SETTINGS -> true
                    else -> false
                }

                val storeOwnerName = uiState.storeInfo.ownerName.ifBlank { uiState.storeInfo.storeName }
                val screenTitle = when (uiState.currentDestination) {
                    NavDestination.HOME -> if (isArabic) "مرحباً بك، $storeOwnerName" else "Welcome, $storeOwnerName"
                    NavDestination.ACCOUNTS -> if (isArabic) StoreStrings.ACCOUNTS_AR else StoreStrings.ACCOUNTS_EN
                    NavDestination.CUSTOMER_DETAILS -> if (isArabic) StoreStrings.CUSTOMER_PROFILE_AR else StoreStrings.CUSTOMER_PROFILE_EN
                    NavDestination.ANALYSIS_CENTER -> if (isArabic) StoreStrings.ANALYSIS_CENTER_AR else StoreStrings.ANALYSIS_CENTER_EN
                    NavDestination.MORE, NavDestination.MORE_SETTINGS -> if (isArabic) StoreStrings.MORE_AR else StoreStrings.MORE_EN
                    NavDestination.PURCHASES -> if (isArabic) StoreStrings.PURCHASES_AR else StoreStrings.PURCHASES_EN
                    NavDestination.QUICK_PAYMENT -> if (isArabic) StoreStrings.QUICK_PAYMENT_AR else StoreStrings.QUICK_PAYMENT_EN
                    NavDestination.NOTIFICATIONS -> if (isArabic) StoreStrings.NOTIFICATIONS_AR else StoreStrings.NOTIFICATIONS_EN
                    NavDestination.STORE_INFORMATION -> if (isArabic) StoreStrings.STORE_INFORMATION_AR else StoreStrings.STORE_INFORMATION_EN
                    NavDestination.APP_SETTINGS -> if (isArabic) StoreStrings.APP_SETTINGS_AR else StoreStrings.APP_SETTINGS_EN
                    NavDestination.DATA_CENTER -> if (isArabic) StoreStrings.DATA_CENTER_AR else StoreStrings.DATA_CENTER_EN
                    NavDestination.CUSTOMER_MANAGEMENT -> if (isArabic) "إدارة العملاء" else "Customer Management"
                    NavDestination.PRODUCT_MANAGEMENT -> if (isArabic) "إدارة المنتجات" else "Product Management"
                    NavDestination.ARCHIVE -> if (isArabic) StoreStrings.SECTION_ARCHIVE_TRASH_AR else StoreStrings.SECTION_ARCHIVE_TRASH_EN
                    NavDestination.ABOUT -> if (isArabic) StoreStrings.ABOUT_SMALLSTORE_AR else StoreStrings.ABOUT_SMALLSTORE_EN
                    NavDestination.PRIVACY_POLICY -> if (isArabic) StoreStrings.PRIVACY_POLICY_AR else StoreStrings.PRIVACY_POLICY_EN
                    NavDestination.TERMS_OF_USE -> if (isArabic) StoreStrings.TERMS_OF_USE_AR else StoreStrings.TERMS_OF_USE_EN
                    NavDestination.CONTACT_SUPPORT -> if (isArabic) StoreStrings.CONTACT_SUPPORT_AR else StoreStrings.CONTACT_SUPPORT_EN
                    NavDestination.BACKUP_RESTORE -> if (isArabic) StoreStrings.SECTION_BACKUP_AR else StoreStrings.SECTION_BACKUP_EN
                }

                val unreadNotifs = uiState.notifications.count { !it.isRead }

                Scaffold(
                    topBar = {
                        if (isTopLevelScreen) {
                            GlobalTopBar(
                                title = screenTitle,
                                unreadNotificationsCount = unreadNotifs,
                                onDrawerClick = {
                                    focusManager.clearFocus()
                                    mainViewModel.openDrawer()
                                    coroutineScope.launch { drawerState.open() }
                                },
                                onNotificationsClick = {
                                    focusManager.clearFocus()
                                    mainViewModel.navigateTo(NavDestination.NOTIFICATIONS)
                                }
                            )
                        }
                    },
                    bottomBar = {
                        if (isTopLevelScreen) {
                            GlobalBottomBar(
                                currentDestination = uiState.activeBottomNav,
                                languageMode = uiState.languageMode,
                                onNavigate = { dest ->
                                    focusManager.clearFocus()
                                    mainViewModel.navigateTo(dest)
                                },
                                onPlusClick = {
                                    focusManager.clearFocus()
                                    mainViewModel.openActionSheet()
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold")
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (uiState.currentDestination) {
                            NavDestination.HOME -> {
                                val matching = if (uiState.homeSearchQuery.isBlank()) {
                                    uiState.customers
                                } else {
                                    val q = uiState.homeSearchQuery.trim().lowercase()
                                    uiState.customers.filter {
                                        it.customerName.lowercase().contains(q) || it.phone.contains(q)
                                    }
                                }

                                val displayedTxs = remember(uiState.transactions, uiState.allTransactions, uiState.homeSelectedCustomer) {
                                    val selectedCust = uiState.homeSelectedCustomer
                                    if (selectedCust != null) {
                                        uiState.allTransactions.filter { it.customerId == selectedCust.id }
                                    } else {
                                        uiState.transactions
                                    }
                                }

                                val totalDebt = uiState.customers.sumOf { it.balance }
                                val totalBalance = uiState.customers.sumOf { it.totalDebt }

                                HomeScreen(
                                    totalBalance = totalBalance,
                                    totalDebt = totalDebt,
                                    transactionsCount = displayedTxs.size,
                                    transactions = displayedTxs,
                                    matchingCustomers = matching,
                                    allCustomers = uiState.customers,
                                    allTransactions = uiState.allTransactions,
                                    selectedCustomer = uiState.homeSelectedCustomer,
                                    searchQuery = uiState.homeSearchQuery,
                                    selectedPeriod = uiState.homeSelectedPeriod,
                                    languageMode = uiState.languageMode,
                                    onSearchQueryChange = { mainViewModel.setHomeSearchQuery(it) },
                                    onSelectCustomer = { mainViewModel.selectHomeCustomer(it) },
                                    onClearSelectedCustomer = { mainViewModel.clearHomeSelectedCustomer() },
                                    onSelectPeriod = { mainViewModel.setHomePeriod(it) },
                                    customStartDate = uiState.homeCustomStartDate,
                                    customEndDate = uiState.homeCustomEndDate,
                                    showCustomDatePicker = uiState.showHomeCustomDatePicker,
                                    onOpenCustomDatePicker = { mainViewModel.openHomeCustomDatePicker() },
                                    onDismissCustomDatePicker = { mainViewModel.dismissHomeCustomDatePicker() },
                                    onSetCustomDateRange = { start, end -> mainViewModel.setHomeCustomDateRange(start, end) },
                                    onActivityClick = { tx -> mainViewModel.navigateToCustomerProfileFromActivity(tx) },
                                    onReverseTransaction = { tx, reason -> mainViewModel.reverseTransaction(tx.id, reason) },
                                    onReturnTransaction = { tx, lines, reason, refundReq ->
                                        mainViewModel.recordSaleReturn(
                                            saleId = tx.id,
                                            returnLines = lines,
                                            reason = reason,
                                            refundRequest = refundReq
                                        )
                                    },
                                    onLoadReturnDetails = { saleId ->
                                        val sale = mainViewModel.getSaleById(saleId)
                                        val lines = mainViewModel.getSaleLinesForSale(saleId)
                                        val returnable = mainViewModel.getRemainingReturnableQuantities(saleId)
                                        Triple(sale, lines, returnable)
                                    }
                                )
                            }

                            NavDestination.ACCOUNTS -> {
                                val filteredAccounts = remember(uiState.customers, uiState.accountsSearchQuery, uiState.accountsFilter) {
                                    var list = uiState.customers
                                    if (uiState.accountsSearchQuery.isNotBlank()) {
                                        val q = uiState.accountsSearchQuery.trim().lowercase()
                                        list = list.filter { it.customerName.lowercase().contains(q) || it.phone.contains(q) }
                                    }
                                    when (uiState.accountsFilter) {
                                        AccountFilter.ALL -> list
                                        AccountFilter.HAS_DEBT -> list.filter { it.balance > 0 }
                                        AccountFilter.RECENTLY_ACTIVE -> list.filter { it.hasRecentActivity }
                                    }
                                }

                                AccountsScreen(
                                    accounts = filteredAccounts,
                                    allCustomers = uiState.customers,
                                    transactions = uiState.allTransactions,
                                    searchQuery = uiState.accountsSearchQuery,
                                    filter = uiState.accountsFilter,
                                    selectedCustomerDetails = uiState.accountsSelectedCustomerDetails,
                                    showAddCustomerDialog = uiState.showAddCustomerDialog,
                                    languageMode = uiState.languageMode,
                                    onSearchQueryChange = { mainViewModel.setAccountsSearchQuery(it) },
                                    onFilterChange = { mainViewModel.setAccountsFilter(it) },
                                    onCustomerClick = { customer ->
                                        mainViewModel.openCustomerDetailsFromAccounts(customer)
                                    },
                                    onOpenAddCustomerDialog = {
                                        mainViewModel.openAddCustomerDialog()
                                    },
                                    onCloseAddCustomerDialog = {
                                        mainViewModel.closeAddCustomerDialog()
                                    },
                                    onAddCustomer = { name, phone ->
                                        mainViewModel.addCustomer(name, phone)
                                    }
                                )
                            }

                            NavDestination.CUSTOMER_DETAILS -> {
                                CustomerProfileScreen(
                                    customer = uiState.accountsSelectedCustomerDetails,
                                    allCustomers = uiState.customers,
                                    languageMode = uiState.languageMode,
                                    onBackClick = {
                                        mainViewModel.navigateBackFromCustomerDetails()
                                    },
                                    onCustomerSelected = { customer ->
                                        mainViewModel.selectCustomerDetails(customer)
                                    },
                                    onRecordPurchase = { customer ->
                                        mainViewModel.setPurchasesCustomer(customer)
                                        mainViewModel.navigateTo(NavDestination.PURCHASES)
                                    },
                                    onViewAccountStatement = { customer ->
                                        analysisViewModel.selectTab(AnalysisTab.ACCOUNT_STATEMENT)
                                        analysisViewModel.selectCustomer(customer)
                                        mainViewModel.navigateTo(NavDestination.ANALYSIS_CENTER)
                                    },
                                    onRecordPayment = { customer ->
                                        mainViewModel.openQuickPayment(customer)
                                    },
                                    onArchiveCustomer = { customer ->
                                        mainViewModel.archiveCustomer(customer.id)
                                        mainViewModel.navigateBackFromCustomerDetails()
                                    },
                                    onRecordAdjustment = { direction, amount, date, reason, reference ->
                                        val cust = uiState.accountsSelectedCustomerDetails
                                        if (cust != null) {
                                            mainViewModel.recordCustomerAdjustment(
                                                customerId = cust.id,
                                                amount = amount,
                                                direction = direction,
                                                date = date,
                                                reason = reason,
                                                reference = reference
                                            )
                                        }
                                    }
                                )
                            }

                            NavDestination.ANALYSIS_CENTER -> {
                                AnalysisCenterScreen(
                                    viewModel = analysisViewModel,
                                    customers = uiState.customers,
                                    transactions = uiState.allTransactions,
                                    storeInfo = uiState.storeInfo,
                                    products = uiState.products,
                                    transactionLines = uiState.transactionLines,
                                    languageMode = uiState.languageMode
                                )
                            }

                            NavDestination.MORE,
                            NavDestination.MORE_SETTINGS -> {
                                MoreScreen(
                                    storeInfo = uiState.storeInfo,
                                    languageMode = uiState.languageMode,
                                    onNavigate = { dest ->
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(dest)
                                    }
                                )
                            }

                            NavDestination.PURCHASES -> {
                                PurchasesScreen(
                                    customer = uiState.purchasesCustomer,
                                    allCustomers = uiState.customers,
                                    products = uiState.products,
                                    cart = uiState.cart,
                                    searchQuery = uiState.purchasesSearchQuery,
                                    isCartExpanded = uiState.isCartExpanded,
                                    languageMode = uiState.languageMode,
                                    suppliers = uiState.suppliers,
                                    supplierPurchases = uiState.purchases,
                                    supplierPayments = uiState.supplierPayments,
                                    expenses = uiState.expenses,
                                    expenseCategories = uiState.expenseCategories,
                                    financialAccounts = uiState.financialAccounts,
                                    paymentMethods = uiState.paymentMethods,
                                    onBackClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.HOME)
                                    },
                                    onSearchQueryChange = { mainViewModel.setPurchasesSearchQuery(it) },
                                    onAddToCart = { mainViewModel.addToCart(it) },
                                    onUpdateCartQuantity = { id, delta -> mainViewModel.updateCartQuantity(id, delta) },
                                    onRemoveFromCart = { mainViewModel.removeFromCart(it) },
                                    onToggleCartExpanded = { mainViewModel.toggleCartExpanded() },
                                    onSelectCustomer = { mainViewModel.setPurchasesCustomer(it) },
                                    onClearCustomer = { mainViewModel.setPurchasesCustomer(null) },
                                    onCompleteTransaction = { mainViewModel.openPurchasesSettlement() },
                                    onCompleteTransactionWithItems = { items -> mainViewModel.openPurchasesSettlement(items) },
                                    onAddSupplier = { name, phone, address, notes, onComplete ->
                                        mainViewModel.addSupplier(name, phone, address, notes, onComplete)
                                    },
                                    onRecordPurchase = { supplierId, lines, paid, acc, notes, date, onComplete ->
                                        mainViewModel.recordPurchase(supplierId, lines, paid, acc, notes, date, onComplete)
                                    },
                                    onRecordSupplierPayment = { supplierId, amount, date, acc, notes, onComplete ->
                                        mainViewModel.recordSupplierPayment(supplierId, amount, date, acc, notes, onComplete)
                                    },
                                    onRecordPurchaseReturn = { purchaseId, amount, reason, date, onComplete ->
                                        mainViewModel.recordPurchaseReturn(purchaseId, amount, reason, date, onComplete)
                                    },
                                    onRecordExpense = { catId, amt, accId, pmId, dt, desc, onComp ->
                                        mainViewModel.recordExpense(catId, amt, accId, pmId, dt, desc, onComp)
                                    },
                                    onAddExpenseCategory = { name, desc, onComp ->
                                        mainViewModel.addExpenseCategory(name, desc, onComp)
                                    },
                                    onGetSupplierBalance = { supplierId ->
                                        mainViewModel.getSupplierBalance(supplierId)
                                    },
                                    onGetSupplierStatement = { supplierId ->
                                        mainViewModel.getSupplierStatement(supplierId)
                                    }
                                )
                            }

                            NavDestination.QUICK_PAYMENT -> {
                                QuickPaymentScreen(
                                    customer = uiState.quickPaymentCustomer,
                                    allCustomers = uiState.customers,
                                    amount = uiState.quickPaymentAmount,
                                    notes = uiState.quickPaymentNotes,
                                    languageMode = uiState.languageMode,
                                    onBackClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.HOME)
                                    },
                                    onCustomerChange = { mainViewModel.setQuickPaymentCustomer(it) },
                                    onAmountChange = { mainViewModel.setQuickPaymentAmount(it) },
                                    onNotesChange = { mainViewModel.setQuickPaymentNotes(it) },
                                    onComplete = { mainViewModel.completeQuickPayment() }
                                )
                            }

                            NavDestination.NOTIFICATIONS -> {
                                NotificationsScreen(
                                    notifications = uiState.notifications,
                                    languageMode = uiState.languageMode,
                                    onBackClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.HOME)
                                    },
                                    onNotificationClick = { /* Handle notification click */ },
                                    onViewNotifications = { mainViewModel.markNotificationsAsRead() }
                                )
                            }

                            NavDestination.STORE_INFORMATION -> {
                                val isStoreSaved by mainViewModel.isStoreInfoSaved.collectAsState()
                                StoreInformationScreen(
                                    storeInfo = uiState.storeInfo,
                                    languageMode = uiState.languageMode,
                                    onBackClick = {
                                        focusManager.clearFocus()
                                        if (isStoreSaved == true) {
                                            mainViewModel.navigateTo(NavDestination.MORE)
                                        }
                                    },
                                    onSaveStoreInfo = { mainViewModel.saveStoreInfo(it) }
                                )
                            }

                            NavDestination.APP_SETTINGS -> {
                                AppSettingsScreen(
                                    languageMode = uiState.languageMode,
                                    themeMode = uiState.themeMode,
                                    displayMode = uiState.displayMode,
                                    notificationsEnabled = uiState.notificationsEnabled,
                                    onBackClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.MORE)
                                    },
                                    onLanguageChange = { mainViewModel.setLanguageMode(it) },
                                    onThemeChange = { mainViewModel.setThemeMode(it) },
                                    onDisplayModeChange = { mainViewModel.setDisplayMode(it) },
                                    onNotificationsChange = { mainViewModel.setNotificationsEnabled(it) }
                                )
                            }

                            NavDestination.DATA_CENTER -> {
                                DataCenterScreen(
                                    languageMode = uiState.languageMode,
                                    customersCount = uiState.customers.size,
                                    productsCount = uiState.products.size,
                                    onBackClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.MORE)
                                    },
                                    onCustomersClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.CUSTOMER_MANAGEMENT)
                                    },
                                    onProductsClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.PRODUCT_MANAGEMENT)
                                    },
                                    onBackupRestoreClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.BACKUP_RESTORE)
                                    },
                                    onArchiveClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.ARCHIVE)
                                    }
                                )
                            }

                            NavDestination.BACKUP_RESTORE -> {
                                val context = LocalContext.current
                                BackupRestoreScreen(
                                    languageMode = uiState.languageMode,
                                    onBackClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.DATA_CENTER)
                                    },
                                    onExportBackup = { uri ->
                                        mainViewModel.exportBackup(context, uri) { success ->
                                            val msg = if (success) {
                                                if (isArabic) "تم حفظ النسخة الاحتياطية بنجاح" else "Backup saved successfully"
                                            } else {
                                                if (isArabic) "فشل حفظ النسخة الاحتياطية" else "Failed to save backup"
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onImportBackup = { uri ->
                                        mainViewModel.prepareRestore(
                                            context = context,
                                            uri = uri,
                                            onError = {
                                                val msg = if (isArabic) "الملف غير صالح أو تعذرت قراءته" else "Invalid backup file or failed to read"
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            },
                                            onSuccessSilent = {
                                                val msg = if (isArabic) "تم استعادة البيانات بنجاح" else "Data restored successfully"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                    onResetData = { mainViewModel.resetData() }
                                )
                            }

                            NavDestination.CUSTOMER_MANAGEMENT -> {
                                val allCustomers = remember(uiState.customers, uiState.archivedCustomers) {
                                    uiState.customers + uiState.archivedCustomers
                                }
                                CustomerManagementScreen(
                                    customers = allCustomers,
                                    archivedCustomerIds = uiState.archivedCustomerIds,
                                    languageMode = uiState.languageMode,
                                    onBackClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.DATA_CENTER)
                                    },
                                    onAddCustomer = { name, phone ->
                                        mainViewModel.addCustomer(name, phone)
                                    },
                                    onUpdateCustomer = { updated ->
                                        mainViewModel.updateCustomer(updated)
                                    },
                                    onArchiveCustomer = { customerId ->
                                        mainViewModel.archiveCustomer(customerId)
                                    },
                                    onUnarchiveCustomer = { customerId ->
                                        mainViewModel.unarchiveCustomer(customerId)
                                    }
                                )
                            }

                            NavDestination.PRODUCT_MANAGEMENT -> {
                                val allProducts = remember(uiState.products, uiState.archivedProducts) {
                                    uiState.products + uiState.archivedProducts
                                }
                                ProductManagementScreen(
                                    products = allProducts,
                                    archivedProductIds = uiState.archivedProductIds,
                                    languageMode = uiState.languageMode,
                                    onBackClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.DATA_CENTER)
                                    },
                                    onAddProduct = { name, price, costPrice, category, unit, imageUri ->
                                        mainViewModel.addProduct(name, price, costPrice, category, unit, imageUri)
                                    },
                                    onUpdateProduct = { updated ->
                                        mainViewModel.updateProduct(updated)
                                    },
                                    onArchiveProduct = { productId ->
                                        mainViewModel.archiveProduct(productId)
                                    },
                                    onUnarchiveProduct = { productId ->
                                        mainViewModel.unarchiveProduct(productId)
                                    },
                                    productStockMap = uiState.productStockMap,
                                    onRecordStockAdjustment = { productId, delta, reason ->
                                        mainViewModel.recordInventoryAdjustment(productId, delta, reason)
                                    }
                                )
                            }

                            NavDestination.ARCHIVE -> {
                                ArchiveScreen(
                                    archivedCustomers = uiState.archivedCustomers,
                                    archivedProducts = uiState.archivedProducts,
                                    archivedTransactions = uiState.archivedTransactions,
                                    languageMode = uiState.languageMode,
                                    onBackClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.DATA_CENTER)
                                    },
                                    onRestoreCustomer = { customer ->
                                        mainViewModel.requestRestoreCustomer(customer)
                                    },
                                    onPermanentDeleteCustomer = { customer ->
                                        mainViewModel.deleteCustomerPermanently(customer)
                                    },
                                    onRestoreProduct = { product ->
                                        mainViewModel.requestRestoreProduct(product)
                                    },
                                    onPermanentDeleteProduct = { product ->
                                        mainViewModel.deleteProductPermanently(product)
                                    },
                                    onRestoreTransaction = { transaction ->
                                        mainViewModel.requestRestoreTransaction(transaction)
                                    },
                                    onPermanentDeleteTransaction = { _ ->
                                        // Accounting Golden Rule: Financial records are immutable historical entries.
                                        // Physical deletion of transactions is strictly prohibited.
                                    },
                                    activeConflict = uiState.pendingArchiveConflict,
                                    onResolveConflictSeparate = { conflict ->
                                        mainViewModel.resolveConflictAsSeparate(conflict)
                                    },
                                    onDismissConflict = {
                                        mainViewModel.dismissArchiveConflict()
                                    }
                                )
                            }

                            NavDestination.ABOUT -> {
                                AboutAppScreen(
                                    languageMode = uiState.languageMode,
                                    onNavigate = { dest ->
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(dest)
                                    },
                                    onBackClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.MORE)
                                    }
                                )
                            }

                            NavDestination.PRIVACY_POLICY -> {
                                GenericContentScreen(
                                    title = if (isArabic) StoreStrings.PRIVACY_POLICY_AR else StoreStrings.PRIVACY_POLICY_EN,
                                    content = if (isArabic) {
                                        "سياسة الخصوصية لسمول ستور:\nنحن نحترم خصوصيتك بالكامل. جميع بيانات المبيعات والحسابات والعملاء تُحفظ محلياً على جهازك ولا تتم مشاركتها مع أي أطراف ثالثة دون إذنك الصريح."
                                    } else {
                                        "SmallStore Privacy Policy:\nWe respect your data privacy. All customer accounts and transaction records are stored securely on your device."
                                    },
                                    onBackClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.ABOUT)
                                    },
                                    testTag = "screen_privacy"
                                )
                            }

                            NavDestination.TERMS_OF_USE -> {
                                GenericContentScreen(
                                    title = if (isArabic) StoreStrings.TERMS_OF_USE_AR else StoreStrings.TERMS_OF_USE_EN,
                                    content = if (isArabic) {
                                        "شروط الاستخدام:\nباستخدامك لتطبيق سمول ستور، فإنك توافق على الالتزام بالقوانين المعمول بها لتنظيم المعاملات التجارية وتدقيق السجلات المحاسبية الخاصة بمتجرك."
                                    } else {
                                        "Terms of Use:\nBy using SmallStore, you agree to comply with commercial accounting standards and verify data accuracy."
                                    },
                                    onBackClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.ABOUT)
                                    },
                                    testTag = "screen_terms"
                                )
                            }

                            NavDestination.CONTACT_SUPPORT -> {
                                ContactSupportScreen(
                                    languageMode = uiState.languageMode,
                                    onBackClick = {
                                        focusManager.clearFocus()
                                        mainViewModel.navigateTo(NavDestination.ABOUT)
                                    }
                                )
                            }
                        }
                    }
                }

                // Global Central "+" Action Sheet
                PlusActionSheet(
                    isOpen = uiState.showActionSheet,
                    languageMode = uiState.languageMode,
                    onDismiss = {
                        focusManager.clearFocus()
                        mainViewModel.closeActionSheet()
                    },
                    onRecordTransactionClick = {
                        focusManager.clearFocus()
                        mainViewModel.closeActionSheet()
                        mainViewModel.navigateTo(NavDestination.PURCHASES)
                    },
                    onQuickPaymentClick = {
                        focusManager.clearFocus()
                        mainViewModel.closeActionSheet()
                        mainViewModel.openQuickPayment()
                    }
                )

                // Unified Settlement Sheet
                UnifiedSettlementSheet(
                    isOpen = uiState.showSettlementSheet,
                    languageMode = uiState.languageMode,
                    settlementContext = uiState.settlementContext,
                    cartItems = uiState.cart,
                    transactionTotal = uiState.settlementTotal,
                    initialCashAmount = String.format(Locale.US, "%.0f", uiState.settlementTotal),
                    initialDebtAmount = "0",
                    onDismiss = {
                        focusManager.clearFocus()
                        mainViewModel.dismissSettlementSheet()
                    },
                    onComplete = { cash, debt, notes ->
                        focusManager.clearFocus()
                        mainViewModel.completeSettlement(cash, debt, notes)
                    }
                )

                // Store Info Backup Conflict Sheet
                val pendingPayload by mainViewModel.pendingRestorePayload.collectAsState()
                val showConflictSheet by mainViewModel.showRestoreConflictSheet.collectAsState()

                if (showConflictSheet && pendingPayload != null) {
                    val context = LocalContext.current
                    StoreInfoConflictSheet(
                        isOpen = true,
                        currentStoreInfo = uiState.storeInfo,
                        backupStoreInfo = pendingPayload!!.storeInfoAtBackupTime,
                        languageMode = uiState.languageMode,
                        onConfirm = { replaceStoreInfo ->
                            mainViewModel.confirmRestore(replaceStoreInfo) {
                                val msg = if (isArabic) "تم استعادة البيانات بنجاح" else "Data restored successfully"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDismiss = {
                            mainViewModel.cancelRestore()
                        }
                    )
                }
            }
        }
    }
}
