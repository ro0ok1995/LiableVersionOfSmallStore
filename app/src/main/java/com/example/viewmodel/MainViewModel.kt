package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupPayload
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.TransactionItemLineEntity
import com.example.data.repository.StoreRepository
import com.example.model.AccountFilter
import com.example.model.AppThemeMode
import com.example.model.CartItem
import com.example.model.CustomerAccount
import com.example.model.CustomerConflictItem
import com.example.model.LanguageMode
import com.example.model.NavDestination
import com.example.model.NotificationItem
import com.example.model.PeriodFilter
import com.example.model.ProductItem
import com.example.model.SampleData
import com.example.model.SettlementType
import com.example.model.StoreInfo
import com.example.model.ArchiveConflict
import com.example.model.ThemeDisplayMode
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.model.SaleType
import com.example.model.PaymentStatus
import com.example.model.LegacyAccountingBridge
import com.example.ui.components.SettlementContext
import com.example.util.ProductImageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MainUiState(
    val currentDestination: NavDestination = NavDestination.HOME,
    val activeBottomNav: NavDestination = NavDestination.HOME,
    val isDrawerOpen: Boolean = false,
    val showActionSheet: Boolean = false,
    val showSettlementSheet: Boolean = false,
    val settlementTotal: Double = 0.0,
    val settlementContext: SettlementContext = SettlementContext.RECORD_TRANSACTION,

    val languageMode: LanguageMode = LanguageMode.ARABIC,
    val themeMode: AppThemeMode = AppThemeMode.PURPLE,
    val displayMode: ThemeDisplayMode = ThemeDisplayMode.LIGHT,
    val notificationsEnabled: Boolean = true,
    val storeInfo: StoreInfo = StoreInfo(),

    val customers: List<CustomerAccount> = emptyList(),
    val archivedCustomers: List<CustomerAccount> = emptyList(),
    val archivedCustomerIds: Set<String> = emptySet(),
    val transactions: List<TransactionItem> = emptyList(),
    val archivedTransactions: List<TransactionItem> = emptyList(),
    val allTransactions: List<TransactionItem> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val products: List<ProductItem> = emptyList(),
    val archivedProducts: List<ProductItem> = emptyList(),
    val archivedProductIds: Set<String> = emptySet(),
    val transactionLines: List<com.example.data.db.TransactionItemLineEntity> = emptyList(),
    val unresolvedCustomerConflicts: List<CustomerConflictItem> = emptyList(),
    val unresolvedConflictCount: Int = 0,

    // Archive conflict resolution state
    val pendingArchiveConflict: ArchiveConflict? = null,

    // Home screen state
    val homeSearchQuery: String = "",
    val homeSelectedCustomer: CustomerAccount? = null,
    val homeSelectedPeriod: PeriodFilter = PeriodFilter.ALL,
    val homeCustomStartDate: LocalDate? = null,
    val homeCustomEndDate: LocalDate? = null,
    val showHomeCustomDatePicker: Boolean = false,

    // Accounts screen state
    val accountsSearchQuery: String = "",
    val accountsFilter: AccountFilter = AccountFilter.ALL,
    val accountsSelectedCustomerDetails: CustomerAccount? = null,
    val customerDetailsPreviousDestination: NavDestination = NavDestination.ACCOUNTS,
    val showAddCustomerDialog: Boolean = false,

    // Purchases screen state
    val purchasesCustomer: CustomerAccount? = null,
    val cart: List<CartItem> = emptyList(),
    val isCartExpanded: Boolean = false,
    val purchasesSearchQuery: String = "",

    // Quick Payment screen state
    val quickPaymentCustomer: CustomerAccount? = null,
    val quickPaymentAmount: String = "",
    val quickPaymentNotes: String = ""
)

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: StoreRepository = StoreRepository.getInstance(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Backup & Restore conflict handling
    val pendingRestorePayload = MutableStateFlow<BackupPayload?>(null)
    val showRestoreConflictSheet = MutableStateFlow(false)

    // StoreInfo status
    val isStoreInfoSaved = MutableStateFlow<Boolean?>(null)
    private var hasCheckedFirstLaunch = false

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
            val saved = repository.isStoreInfoSaved()
            isStoreInfoSaved.value = saved
            if (!saved && !hasCheckedFirstLaunch) {
                hasCheckedFirstLaunch = true
                _uiState.update { it.copy(currentDestination = NavDestination.STORE_INFORMATION) }
            }
        }

        viewModelScope.launch {
            repository.customers.collect { list ->
                _uiState.update { it.copy(customers = list) }
            }
        }

        viewModelScope.launch {
            repository.archivedCustomers.collect { list ->
                _uiState.update {
                    it.copy(
                        archivedCustomers = list,
                        archivedCustomerIds = list.map { c -> c.id }.toSet()
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.transactions.collect { list ->
                _uiState.update { it.copy(transactions = list) }
            }
        }

        viewModelScope.launch {
            repository.archivedTransactions.collect { list ->
                _uiState.update { it.copy(archivedTransactions = list) }
            }
        }

        viewModelScope.launch {
            repository.allTransactions.collect { list ->
                _uiState.update { it.copy(allTransactions = list) }
            }
        }

        viewModelScope.launch {
            repository.products.collect { list ->
                _uiState.update { it.copy(products = list) }
            }
        }

        viewModelScope.launch {
            repository.archivedProducts.collect { list ->
                _uiState.update {
                    it.copy(
                        archivedProducts = list,
                        archivedProductIds = list.map { p -> p.id }.toSet()
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.notifications.collect { list ->
                _uiState.update { it.copy(notifications = list) }
            }
        }

        viewModelScope.launch {
            repository.transactionLines.collect { list ->
                _uiState.update { it.copy(transactionLines = list) }
            }
        }

        viewModelScope.launch {
            repository.unresolvedCustomerConflicts.collect { list ->
                _uiState.update { it.copy(unresolvedCustomerConflicts = list) }
            }
        }

        viewModelScope.launch {
            repository.unresolvedConflictCount.collect { count ->
                _uiState.update { it.copy(unresolvedConflictCount = count) }
            }
        }

        viewModelScope.launch {
            repository.storeInfo.collect { info ->
                _uiState.update { it.copy(storeInfo = info) }
            }
        }
    }

    // NAVIGATION
    fun navigateTo(destination: NavDestination) {
        _uiState.update { state ->
            val updatedBottomNav = when (destination) {
                NavDestination.HOME,
                NavDestination.ACCOUNTS,
                NavDestination.ANALYSIS_CENTER,
                NavDestination.MORE -> destination
                else -> state.activeBottomNav
            }
            state.copy(
                currentDestination = destination,
                activeBottomNav = updatedBottomNav,
                isDrawerOpen = false
            )
        }
    }

    fun openDrawer() {
        _uiState.update { it.copy(isDrawerOpen = true) }
    }

    fun closeDrawer() {
        _uiState.update { it.copy(isDrawerOpen = false) }
    }

    fun openActionSheet() {
        _uiState.update { it.copy(showActionSheet = true) }
    }

    fun closeActionSheet() {
        _uiState.update { it.copy(showActionSheet = false) }
    }

    fun setLanguageMode(mode: LanguageMode) {
        _uiState.update { it.copy(languageMode = mode) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setDisplayMode(mode: ThemeDisplayMode) {
        _uiState.update { it.copy(displayMode = mode) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled) }
    }

    fun saveStoreInfo(info: StoreInfo) {
        if (info.storeName.length > 40) return
        viewModelScope.launch {
            val wasSaved = repository.isStoreInfoSaved()
            repository.saveStoreInfo(info, markAsSaved = true)
            isStoreInfoSaved.value = true
            _uiState.update { current ->
                val nextDest = if (!wasSaved && current.currentDestination == NavDestination.STORE_INFORMATION) {
                    NavDestination.HOME
                } else {
                    current.currentDestination
                }
                current.copy(
                    storeInfo = info,
                    currentDestination = nextDest,
                    activeBottomNav = if (nextDest == NavDestination.HOME) NavDestination.HOME else current.activeBottomNav
                )
            }
        }
    }

    // HOME SCREEN
    fun setHomeSearchQuery(query: String) {
        _uiState.update { current ->
            val updatedCustomer = if (current.homeSelectedCustomer != null && query != current.homeSelectedCustomer.customerName) {
                null
            } else {
                current.homeSelectedCustomer
            }
            current.copy(homeSearchQuery = query, homeSelectedCustomer = updatedCustomer)
        }
    }

    fun selectHomeCustomer(customer: CustomerAccount) {
        _uiState.update { it.copy(homeSelectedCustomer = customer, homeSearchQuery = customer.customerName) }
    }

    fun clearHomeSelectedCustomer() {
        _uiState.update { it.copy(homeSelectedCustomer = null, homeSearchQuery = "") }
    }

    fun setHomePeriod(period: PeriodFilter) {
        _uiState.update { state ->
            val shouldOpenPicker = period == PeriodFilter.CUSTOM && (state.homeCustomStartDate == null || state.homeCustomEndDate == null)
            state.copy(
                homeSelectedPeriod = period,
                showHomeCustomDatePicker = if (shouldOpenPicker) true else state.showHomeCustomDatePicker
            )
        }
    }

    fun openHomeCustomDatePicker() {
        _uiState.update { it.copy(showHomeCustomDatePicker = true) }
    }

    fun dismissHomeCustomDatePicker() {
        _uiState.update { it.copy(showHomeCustomDatePicker = false) }
    }

    fun setHomeCustomDateRange(startDate: LocalDate?, endDate: LocalDate?) {
        _uiState.update { state ->
            state.copy(
                homeCustomStartDate = startDate,
                homeCustomEndDate = endDate,
                homeSelectedPeriod = PeriodFilter.CUSTOM,
                showHomeCustomDatePicker = false
            )
        }
    }

    // ACCOUNTS SCREEN
    fun setAccountsSearchQuery(query: String) {
        _uiState.update { it.copy(accountsSearchQuery = query) }
    }

    fun setAccountsFilter(filter: AccountFilter) {
        _uiState.update { it.copy(accountsFilter = filter) }
    }

    fun selectCustomerDetails(customer: CustomerAccount?) {
        _uiState.update { it.copy(accountsSelectedCustomerDetails = customer) }
    }

    fun openCustomerDetailsFromAccounts(customer: CustomerAccount) {
        selectCustomerDetails(customer)
        _uiState.update {
            it.copy(
                customerDetailsPreviousDestination = NavDestination.ACCOUNTS,
                currentDestination = NavDestination.CUSTOMER_DETAILS
            )
        }
    }

    fun navigateToCustomerProfileFromActivity(transaction: TransactionItem) {
        val state = _uiState.value
        val customer = resolveCustomerForTransaction(state.customers, transaction)
        if (customer != null) {
            selectCustomerDetails(customer)
            _uiState.update {
                it.copy(
                    customerDetailsPreviousDestination = NavDestination.HOME,
                    currentDestination = NavDestination.CUSTOMER_DETAILS
                )
            }
        }
    }

    companion object {
        fun resolveCustomerForTransaction(
            customers: List<CustomerAccount>,
            transaction: TransactionItem
        ): CustomerAccount? {
            // Strict customer identity by persistent ID (Phase 2):
            if (!transaction.customerId.isNullOrBlank()) {
                return customers.firstOrNull { it.id == transaction.customerId }
            }
            // If customerId is null, do NOT guess using customerName.
            // Return null / unresolved state.
            return null
        }
    }

    fun navigateBackFromCustomerDetails() {
        val prev = _uiState.value.customerDetailsPreviousDestination
        navigateTo(prev)
    }

    fun openAddCustomerDialog() {
        _uiState.update { it.copy(showAddCustomerDialog = true) }
    }

    fun closeAddCustomerDialog() {
        _uiState.update { it.copy(showAddCustomerDialog = false) }
    }

    fun addCustomer(name: String, phone: String) {
        val newCustomer = CustomerAccount(
            id = "c_${System.currentTimeMillis()}",
            customerName = name.trim(),
            phone = phone.trim(),
            balance = 0.0,
            totalDebt = 0.0,
            hasRecentActivity = true
        )
        _uiState.update { it.copy(showAddCustomerDialog = false) }
        viewModelScope.launch {
            repository.addCustomer(newCustomer)
        }
    }

    fun addCustomer(name: String, phone: String, initialDebt: Double) {
        addCustomer(name, phone)
    }

    fun updateCustomer(customer: CustomerAccount) {
        viewModelScope.launch {
            repository.updateCustomer(customer)
        }
    }

    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    // CUSTOMERS ARCHIVE / RESTORE / DELETE
    fun archiveCustomer(customerId: String, date: String = getCurrentDateString()) {
        _uiState.update { state ->
            state.copy(
                accountsSelectedCustomerDetails = if (state.accountsSelectedCustomerDetails?.id == customerId) null else state.accountsSelectedCustomerDetails,
                homeSelectedCustomer = if (state.homeSelectedCustomer?.id == customerId) null else state.homeSelectedCustomer,
                purchasesCustomer = if (state.purchasesCustomer?.id == customerId) null else state.purchasesCustomer,
                quickPaymentCustomer = if (state.quickPaymentCustomer?.id == customerId) null else state.quickPaymentCustomer
            )
        }
        viewModelScope.launch {
            repository.archiveCustomer(customerId, date)
        }
    }

    fun unarchiveCustomer(customerId: String) {
        val customer = _uiState.value.archivedCustomers.firstOrNull { it.id == customerId }
        if (customer != null) {
            requestRestoreCustomer(customer)
        } else {
            viewModelScope.launch {
                repository.restoreCustomer(customerId)
            }
        }
    }

    fun requestRestoreCustomer(
        customer: CustomerAccount,
        activeList: List<CustomerAccount> = _uiState.value.customers,
        onConflict: ((ArchiveConflict.CustomerConflict) -> Unit)? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        val conflict = checkCustomerConflict(customer, activeList)
        if (conflict != null) {
            _uiState.update { it.copy(pendingArchiveConflict = conflict) }
            onConflict?.invoke(conflict)
        } else {
            viewModelScope.launch {
                repository.restoreCustomer(customer.id)
                onSuccess?.invoke()
            }
        }
    }

    fun checkCustomerConflict(
        customer: CustomerAccount,
        activeList: List<CustomerAccount> = _uiState.value.customers
    ): ArchiveConflict.CustomerConflict? {
        // 1. Same ID
        val idMatch = activeList.firstOrNull { it.id == customer.id }
        if (idMatch != null) {
            return ArchiveConflict.CustomerConflict(
                archivedCustomer = customer,
                conflictingCustomer = idMatch,
                descriptionAr = "يوجد عميل نشط بنفس المعرّف (${idMatch.customerName})",
                descriptionEn = "An active customer already exists with the same ID (${idMatch.customerName})"
            )
        }

        // 2. Intelligent check: Same name with different phone number is NOT treated as an identical conflict
        val cleanName = customer.customerName.trim().lowercase()
        val cleanPhone = customer.phone.trim()

        val nameAndPhoneMatch = activeList.firstOrNull { active ->
            active.customerName.trim().lowercase() == cleanName &&
            ((cleanPhone.isNotEmpty() && active.phone.trim() == cleanPhone) ||
             (cleanPhone.isEmpty() && active.phone.trim().isEmpty()))
        }

        if (nameAndPhoneMatch != null) {
            val phoneInfo = if (nameAndPhoneMatch.phone.isNotBlank()) " - ${nameAndPhoneMatch.phone}" else ""
            return ArchiveConflict.CustomerConflict(
                archivedCustomer = customer,
                conflictingCustomer = nameAndPhoneMatch,
                descriptionAr = "يوجد عميل نشط متطابق بنفس الاسم ورقم الهاتف (${nameAndPhoneMatch.customerName}$phoneInfo)",
                descriptionEn = "An active customer exists with the identical name and phone (${nameAndPhoneMatch.customerName}$phoneInfo)"
            )
        }

        return null
    }

    fun deleteCustomerPermanently(customer: CustomerAccount, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.deleteCustomerPermanently(customer.id)
            onComplete?.invoke()
        }
    }

    // PRODUCTS
    fun addProduct(
        name: String,
        price: Double,
        costPrice: Double = 0.0,
        category: String = "عام",
        unit: String = "حبة",
        imageUri: String? = null
    ) {
        viewModelScope.launch {
            val newProduct = ProductItem(
                id = "p_${System.currentTimeMillis()}",
                name = name,
                price = price,
                costPrice = costPrice,
                category = category,
                unit = unit,
                imageUri = imageUri
            )
            repository.addProduct(newProduct)
        }
    }

    fun updateProduct(product: ProductItem) {
        viewModelScope.launch {
            repository.updateProduct(product)
        }
    }

    fun archiveProduct(productId: String, date: String = getCurrentDateString()) {
        viewModelScope.launch {
            repository.archiveProduct(productId, date)
        }
    }

    fun unarchiveProduct(productId: String) {
        val product = _uiState.value.archivedProducts.firstOrNull { it.id == productId }
        if (product != null) {
            requestRestoreProduct(product)
        } else {
            viewModelScope.launch {
                repository.restoreProduct(productId)
            }
        }
    }

    fun requestRestoreProduct(
        product: ProductItem,
        activeList: List<ProductItem> = _uiState.value.products,
        onConflict: ((ArchiveConflict.ProductConflict) -> Unit)? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        val conflict = checkProductConflict(product, activeList)
        if (conflict != null) {
            _uiState.update { it.copy(pendingArchiveConflict = conflict) }
            onConflict?.invoke(conflict)
        } else {
            viewModelScope.launch {
                repository.restoreProduct(product.id)
                onSuccess?.invoke()
            }
        }
    }

    fun checkProductConflict(
        product: ProductItem,
        activeList: List<ProductItem> = _uiState.value.products
    ): ArchiveConflict.ProductConflict? {
        // 1. Same ID
        val idMatch = activeList.firstOrNull { it.id == product.id }
        if (idMatch != null) {
            return ArchiveConflict.ProductConflict(
                archivedProduct = product,
                conflictingProduct = idMatch,
                descriptionAr = "يوجد صنف نشط بنفس المعرّف (${idMatch.name})",
                descriptionEn = "An active product already exists with the same ID (${idMatch.name})"
            )
        }

        // 2. Same Name check with price/category differences highlighted
        val cleanName = product.name.trim().lowercase()
        val nameMatch = activeList.firstOrNull { it.name.trim().lowercase() == cleanName }
        if (nameMatch != null) {
            val diffPrice = Math.abs(nameMatch.price - product.price) > 0.001
            val descAr = if (diffPrice) {
                "يوجد صنف نشط بنفس الاسم ولكن بسعر مختلف (السعر الحالي: ₪${nameMatch.price} مقابل المؤرشف: ₪${product.price})"
            } else {
                "يوجد صنف نشط مطابق بنفس الاسم والسعر (₪${nameMatch.price})"
            }
            val descEn = if (diffPrice) {
                "An active product exists with this name but a different price (Active: ₪${nameMatch.price} vs Archived: ₪${product.price})"
            } else {
                "An active product exists with the same name and price (₪${nameMatch.price})"
            }
            return ArchiveConflict.ProductConflict(
                archivedProduct = product,
                conflictingProduct = nameMatch,
                descriptionAr = descAr,
                descriptionEn = descEn
            )
        }

        return null
    }

    fun deleteProductPermanently(product: ProductItem, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            if (!product.imageUri.isNullOrBlank()) {
                ProductImageHelper.deleteProductImage(product.imageUri)
            }
            repository.deleteProductPermanently(product.id)
            onComplete?.invoke()
        }
    }

    // TRANSACTIONS ARCHIVE / RESTORE / DELETE
    fun archiveTransaction(transactionId: String, date: String = getCurrentDateString()) {
        viewModelScope.launch {
            repository.archiveTransaction(transactionId, date)
        }
    }

    fun unarchiveTransaction(transactionId: String) {
        val transaction = _uiState.value.archivedTransactions.firstOrNull { it.id == transactionId }
        if (transaction != null) {
            requestRestoreTransaction(transaction)
        } else {
            viewModelScope.launch {
                repository.restoreTransaction(transactionId)
            }
        }
    }

    fun requestRestoreTransaction(
        transaction: TransactionItem,
        activeList: List<TransactionItem> = _uiState.value.transactions,
        onConflict: ((ArchiveConflict.TransactionConflict) -> Unit)? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        val conflict = checkTransactionConflict(transaction, activeList)
        if (conflict != null) {
            _uiState.update { it.copy(pendingArchiveConflict = conflict) }
            onConflict?.invoke(conflict)
        } else {
            viewModelScope.launch {
                repository.restoreTransaction(transaction.id)
                onSuccess?.invoke()
            }
        }
    }

    fun checkTransactionConflict(
        transaction: TransactionItem,
        activeList: List<TransactionItem> = _uiState.value.transactions
    ): ArchiveConflict.TransactionConflict? {
        // 1. Same ID
        val idMatch = activeList.firstOrNull { it.id == transaction.id }
        if (idMatch != null) {
            return ArchiveConflict.TransactionConflict(
                archivedTransaction = transaction,
                conflictingTransaction = idMatch,
                descriptionAr = "توجد معاملة نشطة بنفس المعرّف (#${transaction.id})",
                descriptionEn = "An active transaction already exists with the same ID (#${transaction.id})"
            )
        }

        // 2. Duplicate detection: same customer (by persistent customerId), amount, date, and activity type
        // Conservative behavior: If customerId is null/blank, do not guess identity by name.
        val dupMatch = if (!transaction.customerId.isNullOrBlank()) {
            activeList.firstOrNull {
                it.customerId == transaction.customerId &&
                Math.abs(it.amount - transaction.amount) < 0.001 &&
                it.date.trim() == transaction.date.trim() &&
                it.activityType.trim().equals(transaction.activityType.trim(), ignoreCase = true)
            }
        } else {
            null
        }
        if (dupMatch != null) {
            return ArchiveConflict.TransactionConflict(
                archivedTransaction = transaction,
                conflictingTransaction = dupMatch,
                descriptionAr = "توجد معاملة نشطة متطابقة لنفس العميل والمبلغ والتاريخ (${transaction.customerNameSnapshot} - ₪${transaction.amount} - ${transaction.date})",
                descriptionEn = "A duplicate active transaction exists for the same customer, amount, and date (${transaction.customerNameSnapshot} - ₪${transaction.amount} - ${transaction.date})"
            )
        }

        return null
    }

    @Deprecated(
        message = "Physical deletion of financial transactions is prohibited by Accounting Golden Rule.",
        level = DeprecationLevel.WARNING
    )
    fun deleteTransactionPermanently(transaction: TransactionItem, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.deleteTransactionPermanently(transaction.id)
            onComplete?.invoke()
        }
    }

    /**
     * Phase 2.5: User-driven explicit resolution of customer identity conflicts.
     * Links original transaction to selected persistent customerId and preserves audit trail.
     */
    fun resolveCustomerIdentityConflict(
        conflictId: String,
        resolvedCustomerId: String,
        notes: String? = null,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val success = repository.resolveCustomerConflict(
                conflictId = conflictId,
                resolvedCustomerId = resolvedCustomerId,
                notes = notes
            )
            if (success) {
                onComplete?.invoke()
            }
        }
    }

    /**
     * Phase 2.5: Dismiss an identity conflict (e.g. marked as anonymous walk-in).
     * Preserves audit record in persistent conflict ledger.
     */
    fun dismissCustomerIdentityConflict(
        conflictId: String,
        notes: String? = null,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val success = repository.dismissCustomerConflict(
                conflictId = conflictId,
                notes = notes
            )
            if (success) {
                onComplete?.invoke()
            }
        }
    }

    // ARCHIVE CONFLICT RESOLUTION
    fun resolveConflictAsSeparate(conflict: ArchiveConflict, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            when (conflict) {
                is ArchiveConflict.CustomerConflict -> {
                    val isAr = _uiState.value.languageMode == LanguageMode.ARABIC
                    val suffix = if (isAr) "(مستعاد)" else "(Restored)"
                    val newName = "${conflict.archivedCustomer.customerName} $suffix"
                    val separateCustomer = conflict.archivedCustomer.copy(
                        id = "c_${System.currentTimeMillis()}",
                        customerName = newName,
                        isArchived = false,
                        archivedDate = null
                    )
                    repository.addCustomer(separateCustomer)
                    repository.deleteCustomerPermanently(conflict.archivedCustomer.id)
                }
                is ArchiveConflict.ProductConflict -> {
                    val isAr = _uiState.value.languageMode == LanguageMode.ARABIC
                    val suffix = if (isAr) "(مستعاد)" else "(Restored)"
                    val newName = "${conflict.archivedProduct.name} $suffix"
                    val separateProduct = conflict.archivedProduct.copy(
                        id = "p_${System.currentTimeMillis()}",
                        name = newName,
                        isArchived = false,
                        archivedDate = null
                    )
                    repository.addProduct(separateProduct)
                    repository.deleteProductPermanently(conflict.archivedProduct.id)
                }
                is ArchiveConflict.TransactionConflict -> {
                    // Accounting Golden Rule: Historical transaction records must never be deleted.
                    // Unarchive the transaction directly preserving its immutable historical identity.
                    repository.restoreTransaction(conflict.archivedTransaction.id)
                }
            }
            _uiState.update { it.copy(pendingArchiveConflict = null) }
            onComplete?.invoke()
        }
    }

    fun dismissArchiveConflict() {
        _uiState.update { it.copy(pendingArchiveConflict = null) }
    }

    // NOTIFICATIONS
    fun markNotificationsAsRead() {
        viewModelScope.launch {
            repository.markNotificationsAsRead()
        }
    }

    // PURCHASES / CART
    fun setPurchasesCustomer(customer: CustomerAccount?) {
        if (customer?.isArchived == true) return
        _uiState.update { it.copy(purchasesCustomer = customer) }
    }

    fun setPurchasesSearchQuery(query: String) {
        _uiState.update { it.copy(purchasesSearchQuery = query) }
    }

    fun toggleCartExpanded() {
        _uiState.update { it.copy(isCartExpanded = !it.isCartExpanded) }
    }

    fun addToCart(product: ProductItem) {
        _uiState.update { state ->
            val existing = state.cart.find { it.product.id == product.id }
            val newCart = if (existing != null) {
                state.cart.map {
                    if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it
                }
            } else {
                state.cart + CartItem(product = product, quantity = 1)
            }
            state.copy(cart = newCart)
        }
    }

    fun updateCartQuantity(productId: String, delta: Int) {
        _uiState.update { state ->
            val newCart = state.cart.mapNotNull { item ->
                if (item.product.id == productId) {
                    val newQty = item.quantity + delta
                    if (newQty > 0) item.copy(quantity = newQty) else null
                } else {
                    item
                }
            }
            state.copy(cart = newCart)
        }
    }

    fun removeFromCart(productId: String) {
        _uiState.update { state ->
            state.copy(cart = state.cart.filter { it.product.id != productId })
        }
    }

    fun openPurchasesSettlement(cartItems: List<CartItem> = _uiState.value.cart) {
        val total = cartItems.sumOf { it.product.price * it.quantity }
        _uiState.update {
            it.copy(
                cart = cartItems,
                settlementTotal = total,
                settlementContext = SettlementContext.RECORD_TRANSACTION,
                showSettlementSheet = true
            )
        }
    }

    fun dismissSettlementSheet() {
        _uiState.update { it.copy(showSettlementSheet = false) }
    }

    fun completeSettlement(cashAmount: Double, debtAmount: Double, notes: String) {
        val state = _uiState.value
        val customer = state.purchasesCustomer ?: state.customers.firstOrNull() ?: return
        if (customer.isArchived || (state.customers.isNotEmpty() && state.customers.none { it.id == customer.id })) return
        val total = if (Math.abs(state.settlementTotal - (cashAmount + debtAmount)) < 0.001 && state.settlementTotal > 0.0) {
            state.settlementTotal
        } else {
            cashAmount + debtAmount
        }
        val txId = "tx_${System.currentTimeMillis()}"

        val saleType = when {
            debtAmount <= 0.001 -> SaleType.CASH
            cashAmount <= 0.001 -> SaleType.CREDIT
            else -> SaleType.MIXED
        }
        val paymentStatus = when {
            debtAmount <= 0.001 -> PaymentStatus.PAID
            cashAmount <= 0.001 -> PaymentStatus.UNPAID
            else -> PaymentStatus.PARTIAL
        }
        val legacyFields = LegacyAccountingBridge.toLegacyFields(
            transactionType = TransactionType.SALE,
            saleType = saleType,
            paymentStatus = paymentStatus
        )

        val cartSnapshot = state.cart
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val updatedCustomer = customer.copy(
            hasRecentActivity = true,
            lastTransactionDate = todayDate
        )

        val notif = NotificationItem(
            id = "notif_${System.currentTimeMillis()}",
            customerName = customer.customerName,
            transactionType = legacyFields.activityType,
            amount = total,
            timestamp = "الآن",
            isPayment = false,
            isRead = false,
            transactionId = txId
        )

        _uiState.update {
            it.copy(
                cart = emptyList(),
                showSettlementSheet = false,
                currentDestination = NavDestination.HOME,
                activeBottomNav = NavDestination.HOME
            )
        }

        viewModelScope.launch {
            val invoiceNumber = repository.getNextInvoiceNumber()
            val sale = Sale(
                id = txId,
                invoiceNumber = invoiceNumber,
                customerId = customer.id,
                saleType = saleType.name,
                totalAmount = total,
                paidAmount = cashAmount,
                creditAmount = debtAmount,
                paymentStatus = paymentStatus.name,
                transactionDate = todayDate,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = "ACTIVE"
            )

            val saleLines = cartSnapshot.mapIndexed { index, cartItem ->
                SaleLine(
                    id = "${txId}_line_${index + 1}",
                    saleId = txId,
                    productId = cartItem.product.id,
                    productNameSnapshot = cartItem.product.name,
                    quantity = cartItem.quantity,
                    unitPrice = cartItem.product.price,
                    costPriceAtSale = cartItem.product.costPrice,
                    subtotal = cartItem.product.price * cartItem.quantity
                )
            }

            repository.createSale(
                sale = sale,
                lines = saleLines,
                customerNameSnapshot = customer.customerName,
                notes = notes
            )
            repository.updateCustomer(updatedCustomer)
            repository.addNotification(notif)
        }
    }

    // QUICK PAYMENT
    fun openQuickPayment(customer: CustomerAccount? = null) {
        if (customer?.isArchived == true) return
        _uiState.update {
            it.copy(
                quickPaymentCustomer = customer,
                quickPaymentAmount = "",
                quickPaymentNotes = "",
                currentDestination = NavDestination.QUICK_PAYMENT
            )
        }
    }

    fun setQuickPaymentCustomer(customer: CustomerAccount?) {
        if (customer?.isArchived == true) return
        _uiState.update {
            it.copy(quickPaymentCustomer = customer)
        }
    }

    fun setQuickPaymentAmount(amt: String) {
        _uiState.update { it.copy(quickPaymentAmount = amt) }
    }

    fun setQuickPaymentNotes(notes: String) {
        _uiState.update { it.copy(quickPaymentNotes = notes) }
    }

    fun completeQuickPayment() {
        val state = _uiState.value
        val customer = state.quickPaymentCustomer ?: return
        if (customer.isArchived || (state.customers.isNotEmpty() && state.customers.none { it.id == customer.id })) return
        val amount = state.quickPaymentAmount.toDoubleOrNull() ?: return
        if (amount <= 0.0) return

        val txId = "tx_${System.currentTimeMillis()}"
        val isFullPayment = amount >= (customer.balance - 0.001)
        val legacyFields = LegacyAccountingBridge.toLegacyFields(
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            paymentStatus = if (isFullPayment) PaymentStatus.PAID else PaymentStatus.PARTIAL
        )

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val newTx = TransactionItem(
            id = txId,
            customerNameSnapshot = customer.customerName,
            activityType = legacyFields.activityType,
            amount = amount,
            relativeTime = "الآن",
            date = todayDate,
            isCredit = legacyFields.isCredit,
            notes = state.quickPaymentNotes.ifBlank { "تسديد دفعة سريعة" },
            settlementType = legacyFields.settlementType,
            customerId = customer.id,
            customerName = customer.customerName,
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            paymentStatus = if (isFullPayment) PaymentStatus.PAID else PaymentStatus.PARTIAL,
            operationStatus = com.example.model.OperationStatus.ACTIVE,
            paidAmount = amount,
            creditAmount = 0.0
        )

        val updatedCustomer = customer.copy(
            hasRecentActivity = true,
            lastTransactionDate = todayDate
        )

        val newNotif = NotificationItem(
            id = "notif_${System.currentTimeMillis()}",
            customerName = customer.customerName,
            transactionType = "تسديد",
            amount = amount,
            timestamp = "الآن",
            isPayment = true,
            isRead = false,
            transactionId = txId
        )

        _uiState.update {
            it.copy(
                quickPaymentCustomer = null,
                quickPaymentAmount = "",
                quickPaymentNotes = "",
                currentDestination = NavDestination.HOME,
                activeBottomNav = NavDestination.HOME
            )
        }

        viewModelScope.launch {
            repository.addTransaction(newTx)
            repository.updateCustomer(updatedCustomer)
            repository.addNotification(newNotif)
        }
    }

    fun resetData() {
        _uiState.update {
            it.copy(
                cart = emptyList(),
                homeSelectedCustomer = null,
                purchasesCustomer = null
            )
        }
        viewModelScope.launch {
            repository.resetDatabaseToSampleData()
        }
    }

    // BACKUP & RESTORE
    fun exportBackup(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val payload = repository.getAllDataForBackup()
                val jsonString = BackupManager.serialize(payload)
                val success = BackupManager.writeToUri(context.contentResolver, uri, jsonString)
                onResult(success)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun prepareRestore(context: Context, uri: Uri, onError: () -> Unit, onSuccessSilent: () -> Unit) {
        viewModelScope.launch {
            try {
                val jsonString = BackupManager.readFromUri(context.contentResolver, uri)
                if (jsonString.isNullOrBlank()) {
                    onError()
                    return@launch
                }
                val payload = BackupManager.deserialize(jsonString, context)
                val currentInfo = repository.getStoreInfoSnapshot()

                val differs = isStoreInfoDifferent(payload.storeInfoAtBackupTime, currentInfo)
                if (differs) {
                    pendingRestorePayload.value = payload
                    showRestoreConflictSheet.value = true
                } else {
                    repository.restoreDataFromBackup(payload, replaceStoreInfo = false)
                    pendingRestorePayload.value = null
                    showRestoreConflictSheet.value = false
                    onSuccessSilent()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onError()
            }
        }
    }

    fun confirmRestore(replaceStoreInfo: Boolean, onComplete: () -> Unit) {
        val payload = pendingRestorePayload.value ?: return
        viewModelScope.launch {
            try {
                repository.restoreDataFromBackup(payload, replaceStoreInfo = replaceStoreInfo)
                onComplete()
            } finally {
                pendingRestorePayload.value = null
                showRestoreConflictSheet.value = false
            }
        }
    }

    fun cancelRestore() {
        pendingRestorePayload.value = null
        showRestoreConflictSheet.value = false
    }

    private fun isStoreInfoDifferent(backup: StoreInfo, current: StoreInfo): Boolean {
        return backup.storeName.trim() != current.storeName.trim() ||
                backup.ownerName.trim() != current.ownerName.trim() ||
                backup.phone.trim() != current.phone.trim() ||
                backup.address.trim() != current.address.trim() ||
                backup.taxNumber.trim() != current.taxNumber.trim() ||
                backup.crNumber.trim() != current.crNumber.trim()
    }
}

