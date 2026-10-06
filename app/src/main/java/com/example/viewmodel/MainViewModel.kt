package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupPayload
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.SaleReturn
import com.example.data.db.SaleReturnLine
import com.example.data.db.Refund
import com.example.data.db.Reversal
import com.example.data.db.Supplier
import com.example.data.db.Purchase
import com.example.data.db.PurchaseLine
import com.example.data.db.SupplierPayment
import com.example.data.db.PurchaseReturn
import com.example.model.PurchaseReturnLineRequest
import com.example.data.db.ExpenseCategory
import com.example.data.db.Expense
import com.example.data.db.FinancialAccount
import com.example.data.db.PaymentMethod
import com.example.data.db.Adjustment
import com.example.data.db.TransactionItemLineEntity
import com.example.data.repository.StoreRepository
import com.example.accounting.CustomerLedgerCalculator
import com.example.accounting.SupplierBalanceSummary
import com.example.accounting.SupplierLedgerEntry
import com.example.accounting.InventoryMovementEntry
import com.example.accounting.ProductStockSummary
import com.example.accounting.StockMovement
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
import com.example.model.PurchaseLineRequest
import com.example.model.PurchaseResult
import com.example.model.RefundRequest
import com.example.model.SaleReturnLineRequest
import com.example.model.SaleReturnResult
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
    val suppliers: List<Supplier> = emptyList(),
    val purchases: List<Purchase> = emptyList(),
    val supplierPayments: List<SupplierPayment> = emptyList(),
    val expenseCategories: List<ExpenseCategory> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val financialAccounts: List<FinancialAccount> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val productStockMap: Map<String, ProductStockSummary> = emptyMap(),
    val totalInventoryValuation: Double = 0.0,

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

    val backupRestoreViewModel = BackupRestoreViewModel(application, repository)
    val expenseViewModel = ExpenseViewModel(application, repository)
    val supplierPurchaseViewModel = SupplierPurchaseViewModel(application, repository)
    val inventoryViewModel = InventoryViewModel(application, repository)
    val customerViewModel = CustomerViewModel(application, repository)

    // Backup & Restore conflict handling
    val pendingRestorePayload: MutableStateFlow<BackupPayload?> = backupRestoreViewModel.pendingRestorePayload
    val showRestoreConflictSheet: MutableStateFlow<Boolean> = backupRestoreViewModel.showRestoreConflictSheet

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
            customerViewModel.customers.collect { list ->
                _uiState.update { it.copy(customers = list) }
            }
        }

        viewModelScope.launch {
            customerViewModel.archivedCustomers.collect { list ->
                _uiState.update {
                    it.copy(
                        archivedCustomers = list,
                        archivedCustomerIds = list.map { c -> c.id }.toSet()
                    )
                }
            }
        }

        viewModelScope.launch {
            customerViewModel.accountsSearchQuery.collect { query ->
                _uiState.update { it.copy(accountsSearchQuery = query) }
            }
        }

        viewModelScope.launch {
            customerViewModel.accountsFilter.collect { filter ->
                _uiState.update { it.copy(accountsFilter = filter) }
            }
        }

        viewModelScope.launch {
            customerViewModel.accountsSelectedCustomerDetails.collect { customer ->
                _uiState.update { it.copy(accountsSelectedCustomerDetails = customer) }
            }
        }

        viewModelScope.launch {
            customerViewModel.customerDetailsPreviousDestination.collect { prev ->
                _uiState.update { it.copy(customerDetailsPreviousDestination = prev) }
            }
        }

        viewModelScope.launch {
            customerViewModel.showAddCustomerDialog.collect { show ->
                _uiState.update { it.copy(showAddCustomerDialog = show) }
            }
        }

        viewModelScope.launch {
            customerViewModel.pendingCustomerConflict.collect { conflict ->
                if (conflict != null) {
                    _uiState.update { it.copy(pendingArchiveConflict = conflict) }
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
            inventoryViewModel.products.collect { list ->
                _uiState.update { it.copy(products = list) }
            }
        }

        viewModelScope.launch {
            inventoryViewModel.archivedProducts.collect { list ->
                _uiState.update {
                    it.copy(
                        archivedProducts = list,
                        archivedProductIds = list.map { p -> p.id }.toSet()
                    )
                }
            }
        }

        viewModelScope.launch {
            inventoryViewModel.productStockMap.collect { map ->
                _uiState.update { it.copy(productStockMap = map) }
            }
        }

        viewModelScope.launch {
            inventoryViewModel.totalInventoryValuation.collect { valuation ->
                _uiState.update { it.copy(totalInventoryValuation = valuation) }
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

        viewModelScope.launch {
            supplierPurchaseViewModel.suppliers.collect { list ->
                _uiState.update { it.copy(suppliers = list) }
            }
        }

        viewModelScope.launch {
            supplierPurchaseViewModel.purchases.collect { list ->
                _uiState.update { it.copy(purchases = list) }
            }
        }

        viewModelScope.launch {
            supplierPurchaseViewModel.supplierPayments.collect { list ->
                _uiState.update { it.copy(supplierPayments = list) }
            }
        }

        viewModelScope.launch {
            expenseViewModel.expenseCategories.collect { list ->
                _uiState.update { it.copy(expenseCategories = list) }
            }
        }

        viewModelScope.launch {
            expenseViewModel.expenses.collect { list ->
                _uiState.update { it.copy(expenses = list) }
            }
        }

        viewModelScope.launch {
            repository.allFinancialAccounts.collect { list ->
                _uiState.update { it.copy(financialAccounts = list) }
            }
        }

        viewModelScope.launch {
            repository.allPaymentMethods.collect { list ->
                _uiState.update { it.copy(paymentMethods = list) }
            }
        }

        viewModelScope.launch {
            repository.allProducts.collect {
                refreshInventory()
            }
        }

        viewModelScope.launch {
            repository.allPurchases.collect {
                refreshInventory()
            }
        }

        viewModelScope.launch {
            repository.allSales.collect {
                refreshInventory()
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
        customerViewModel.setAccountsSearchQuery(query)
    }

    fun setAccountsFilter(filter: AccountFilter) {
        customerViewModel.setAccountsFilter(filter)
    }

    fun selectCustomerDetails(customer: CustomerAccount?) {
        customerViewModel.selectCustomerDetails(customer)
    }

    fun openCustomerDetailsFromAccounts(customer: CustomerAccount) {
        customerViewModel.openCustomerDetailsFromAccounts(customer)
        _uiState.update {
            it.copy(
                accountsSelectedCustomerDetails = customer,
                customerDetailsPreviousDestination = NavDestination.ACCOUNTS,
                currentDestination = NavDestination.CUSTOMER_DETAILS
            )
        }
    }

    fun navigateToCustomerProfileFromActivity(transaction: TransactionItem) {
        val state = _uiState.value
        val customer = resolveCustomerForTransaction(state.customers, transaction)
        if (customer != null) {
            customerViewModel.selectCustomerDetails(customer)
            _uiState.update {
                it.copy(
                    accountsSelectedCustomerDetails = customer,
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
            return CustomerViewModel.resolveCustomerForTransaction(customers, transaction)
        }
    }

    fun navigateBackFromCustomerDetails() {
        val prev = customerViewModel.customerDetailsPreviousDestination.value
        navigateTo(prev)
    }

    fun openAddCustomerDialog() {
        customerViewModel.openAddCustomerDialog()
    }

    fun closeAddCustomerDialog() {
        customerViewModel.closeAddCustomerDialog()
    }

    fun addCustomer(name: String, phone: String) {
        customerViewModel.addCustomer(name, phone)
    }

    fun addCustomer(name: String, phone: String, initialDebt: Double) {
        customerViewModel.addCustomer(name, phone, initialDebt)
    }

    fun updateCustomer(customer: CustomerAccount) {
        customerViewModel.updateCustomer(customer)
    }

    fun recordCustomerAdjustment(
        customerId: String,
        amount: Double,
        direction: String,
        date: String,
        reason: String,
        reference: String? = null
    ) {
        customerViewModel.recordCustomerAdjustment(customerId, amount, direction, date, reason, reference)
    }

    // Phase 7: Reversal Engine
    fun reverseTransaction(
        originalTransactionId: String,
        reason: String,
        onComplete: (Result<Reversal>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val reversal = repository.reverseTransaction(originalTransactionId, reason)
                onComplete(Result.success(reversal))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            }
        }
    }

    // Phase 8: Returns & Refunds
    fun recordSaleReturn(
        saleId: String,
        returnLines: List<SaleReturnLineRequest>,
        reason: String,
        returnDate: String = getCurrentDateString(),
        refundRequest: RefundRequest? = null,
        onComplete: (Result<SaleReturnResult>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = repository.saleReturnRepository.recordSaleReturn(
                    saleId = saleId,
                    returnLines = returnLines,
                    reason = reason,
                    returnDate = returnDate,
                    refundRequest = refundRequest
                )
                onComplete(Result.success(result))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            }
        }
    }

    suspend fun getRemainingReturnableQuantities(saleId: String): Map<String, Int> {
        return repository.saleReturnRepository.getRemainingReturnableQuantities(saleId)
    }

    suspend fun getSaleLinesForSale(saleId: String): List<SaleLine> {
        return repository.salesRepository.getSaleLines(saleId)
    }

    suspend fun getReturnsForSale(saleId: String): List<SaleReturn> {
        return repository.saleReturnRepository.getReturnsForSale(saleId)
    }

    suspend fun getSaleById(saleId: String): Sale? {
        return repository.salesRepository.getSaleById(saleId)
    }

    // Phase 9: Suppliers & Purchases
    fun addSupplier(
        name: String,
        phone: String = "",
        address: String? = null,
        notes: String? = null,
        onComplete: (Result<Supplier>) -> Unit = {}
    ) {
        supplierPurchaseViewModel.addSupplier(name, phone, address, notes, onComplete)
    }

    fun recordPurchase(
        supplierId: String,
        lines: List<PurchaseLineRequest>,
        paidAmount: Double = 0.0,
        financialAccountId: String? = null,
        notes: String? = null,
        purchaseDate: String = getCurrentDateString(),
        onComplete: (Result<PurchaseResult>) -> Unit = {}
    ) {
        supplierPurchaseViewModel.recordPurchase(
            supplierId = supplierId,
            lines = lines,
            paidAmount = paidAmount,
            financialAccountId = financialAccountId,
            notes = notes,
            purchaseDate = purchaseDate,
            onComplete = onComplete
        )
    }

    fun recordSupplierPayment(
        supplierId: String,
        amount: Double,
        paymentDate: String = getCurrentDateString(),
        financialAccountId: String? = null,
        notes: String? = null,
        onComplete: (Result<SupplierPayment>) -> Unit = {}
    ) {
        supplierPurchaseViewModel.recordSupplierPayment(
            supplierId = supplierId,
            amount = amount,
            paymentDate = paymentDate,
            financialAccountId = financialAccountId,
            notes = notes,
            onComplete = onComplete
        )
    }

    fun recordPurchaseReturn(
        purchaseId: String,
        returnLines: List<PurchaseReturnLineRequest>,
        reason: String,
        returnDate: String = getCurrentDateString(),
        onComplete: (Result<PurchaseReturn>) -> Unit = {}
    ) {
        supplierPurchaseViewModel.recordPurchaseReturn(
            purchaseId = purchaseId,
            returnLines = returnLines,
            reason = reason,
            returnDate = returnDate,
            onComplete = onComplete
        )
    }

    fun getPurchaseReturnableQuantities(purchaseId: String, onResult: (Map<String, Int>) -> Unit) {
        supplierPurchaseViewModel.getPurchaseReturnableQuantities(purchaseId, onResult)
    }

    suspend fun getSupplierBalance(supplierId: String): SupplierBalanceSummary {
        return supplierPurchaseViewModel.getSupplierBalance(supplierId)
    }

    suspend fun getSupplierStatement(supplierId: String): List<SupplierLedgerEntry> {
        return supplierPurchaseViewModel.getSupplierStatement(supplierId)
    }

    suspend fun getPurchaseLines(purchaseId: String): List<PurchaseLine> {
        return supplierPurchaseViewModel.getPurchaseLines(purchaseId)
    }

    suspend fun getPurchasesForSupplier(supplierId: String): List<Purchase> {
        return supplierPurchaseViewModel.getPurchasesForSupplier(supplierId)
    }

    suspend fun getPaymentsForSupplier(supplierId: String): List<SupplierPayment> {
        return supplierPurchaseViewModel.getPaymentsForSupplier(supplierId)
    }

    // EXPENSES (Phase 10 Core)
    fun addExpenseCategory(
        name: String,
        description: String? = null,
        onComplete: (Result<ExpenseCategory>) -> Unit = {}
    ) {
        expenseViewModel.addExpenseCategory(name, description, onComplete)
    }

    fun insertExpenseCategory(
        category: ExpenseCategory,
        onComplete: (Result<ExpenseCategory>) -> Unit = {}
    ) {
        expenseViewModel.insertExpenseCategory(category, onComplete)
    }

    fun recordExpense(
        categoryId: String,
        amount: Double,
        financialAccountId: String,
        paymentMethodId: String? = null,
        date: String? = null,
        description: String,
        onComplete: (Result<Expense>) -> Unit = {}
    ) {
        expenseViewModel.recordExpense(
            categoryId = categoryId,
            amount = amount,
            financialAccountId = financialAccountId,
            paymentMethodId = paymentMethodId,
            date = date,
            description = description,
            onComplete = onComplete
        )
    }

    suspend fun getFinancialAccountBalance(accountId: String): Double {
        return repository.getFinancialAccountBalance(accountId)
    }

    suspend fun getAllExpenseCategories(): List<ExpenseCategory> {
        return expenseViewModel.getAllExpenseCategories()
    }

    suspend fun getAllExpenses(): List<Expense> {
        return expenseViewModel.getAllExpenses()
    }

    // INVENTORY (Phase 11 Core)
    fun refreshInventory() {
        inventoryViewModel.refreshInventory()
    }

    suspend fun getProductStock(productId: String): ProductStockSummary {
        return inventoryViewModel.getProductStock(productId)
    }

    suspend fun getInventoryStatement(productId: String): List<InventoryMovementEntry> {
        return inventoryViewModel.getInventoryStatement(productId)
    }

    suspend fun getStockMovements(productId: String): List<StockMovement> {
        return inventoryViewModel.getStockMovements(productId)
    }

    fun recordInventoryAdjustment(
        productId: String,
        quantityDelta: Int,
        reason: String,
        date: String? = null,
        onComplete: (Result<Adjustment>) -> Unit = {}
    ) {
        inventoryViewModel.recordInventoryAdjustment(productId, quantityDelta, reason, date, onComplete)
    }

    fun recordInventoryDamage(
        productId: String,
        quantity: Int,
        reason: String = "بضاعة تالفة",
        date: String? = null,
        onComplete: (Result<Adjustment>) -> Unit = {}
    ) {
        inventoryViewModel.recordInventoryDamage(productId, quantity, reason, date, onComplete)
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
        customerViewModel.archiveCustomer(customerId, date)
    }

    fun unarchiveCustomer(customerId: String) {
        val customer = _uiState.value.archivedCustomers.firstOrNull { it.id == customerId }
        if (customer != null) {
            requestRestoreCustomer(customer)
        } else {
            customerViewModel.unarchiveCustomer(customerId)
        }
    }

    fun requestRestoreCustomer(
        customer: CustomerAccount,
        activeList: List<CustomerAccount> = _uiState.value.customers,
        onConflict: ((ArchiveConflict.CustomerConflict) -> Unit)? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        customerViewModel.requestRestoreCustomer(
            customer = customer,
            activeList = activeList,
            onConflict = { conflict ->
                _uiState.update { it.copy(pendingArchiveConflict = conflict) }
                onConflict?.invoke(conflict)
            },
            onSuccess = onSuccess
        )
    }

    fun checkCustomerConflict(
        customer: CustomerAccount,
        activeList: List<CustomerAccount> = _uiState.value.customers
    ): ArchiveConflict.CustomerConflict? {
        return customerViewModel.checkCustomerConflict(customer, activeList)
    }

    fun deleteCustomerPermanently(customer: CustomerAccount, onComplete: (() -> Unit)? = null) {
        customerViewModel.deleteCustomerPermanently(customer, onComplete)
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
        inventoryViewModel.addProduct(name, price, costPrice, category, unit, imageUri)
    }

    fun updateProduct(product: ProductItem) {
        inventoryViewModel.updateProduct(product)
    }

    fun archiveProduct(productId: String, date: String = getCurrentDateString()) {
        inventoryViewModel.archiveProduct(productId, date)
    }

    fun unarchiveProduct(productId: String) {
        val product = _uiState.value.archivedProducts.firstOrNull { it.id == productId }
        if (product != null) {
            requestRestoreProduct(product)
        } else {
            inventoryViewModel.unarchiveProduct(productId)
        }
    }

    fun requestRestoreProduct(
        product: ProductItem,
        activeList: List<ProductItem> = _uiState.value.products,
        onConflict: ((ArchiveConflict.ProductConflict) -> Unit)? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        inventoryViewModel.requestRestoreProduct(
            product = product,
            activeList = activeList,
            onConflict = { conflict ->
                _uiState.update { it.copy(pendingArchiveConflict = conflict) }
                onConflict?.invoke(conflict)
            },
            onSuccess = onSuccess
        )
    }

    fun checkProductConflict(
        product: ProductItem,
        activeList: List<ProductItem> = _uiState.value.products
    ): ArchiveConflict.ProductConflict? {
        return inventoryViewModel.checkProductConflict(product, activeList)
    }

    fun deleteProductPermanently(product: ProductItem, onComplete: (() -> Unit)? = null) {
        inventoryViewModel.deleteProductPermanently(product, onComplete)
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
        customerViewModel.clearPendingCustomerConflict()
        inventoryViewModel.clearPendingProductConflict()
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
        viewModelScope.launch {
            val stock = repository.getProductStock(product.id)
            val currentQty = _uiState.value.cart.find { it.product.id == product.id }?.quantity ?: 0
            if (stock.quantityOnHand <= currentQty) return@launch
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
    }

    fun updateCartQuantity(productId: String, delta: Int) {
        if (delta == 0) return
        if (delta < 0) {
            _uiState.update { state ->
                state.copy(cart = state.cart.mapNotNull { item ->
                    if (item.product.id == productId) {
                        val newQty = item.quantity + delta
                        if (newQty > 0) item.copy(quantity = newQty) else null
                    } else item
                })
            }
            return
        }
        viewModelScope.launch {
            val stock = repository.getProductStock(productId)
            _uiState.update { state ->
                state.copy(cart = state.cart.mapNotNull { item ->
                    if (item.product.id == productId) {
                        val newQty = (item.quantity + delta).coerceAtMost(stock.quantityOnHand)
                        if (newQty > 0) item.copy(quantity = newQty) else null
                    } else item
                })
            }
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

    fun completeSettlement(cashAmount: Double, debtAmount: Double, notes: String, financialAccountId: String? = null) {
        val state = _uiState.value
        val customer = state.purchasesCustomer
        if (customer?.isArchived == true) return
        if (customer != null && state.customers.isNotEmpty() && state.customers.none { it.id == customer.id }) return
        if (state.cart.isEmpty()) return
        if (!cashAmount.isFinite() || !debtAmount.isFinite() || cashAmount < 0.0 || debtAmount < 0.0) return
        if (debtAmount > 0.001 && customer == null) return
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
        val updatedCustomer = customer?.copy(
            hasRecentActivity = true,
            lastTransactionDate = todayDate
        )

        val notif = NotificationItem(
            id = "notif_${System.currentTimeMillis()}",
            customerName = customer?.customerName ?: "عميل نقدي",
            transactionType = legacyFields.activityType,
            amount = total,
            timestamp = "الآن",
            isPayment = false,
            isRead = false,
            transactionId = txId
        )

        viewModelScope.launch {
            val invoiceNumber = repository.salesRepository.getNextInvoiceNumber()
            val sale = Sale(
                id = txId,
                invoiceNumber = invoiceNumber,
                customerId = customer?.id,
                saleType = saleType.name,
                totalAmount = total,
                paidAmount = cashAmount,
                creditAmount = debtAmount,
                paymentStatus = paymentStatus.name,
                transactionDate = todayDate,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = "ACTIVE",
                financialAccountId = financialAccountId ?: "acc_cash"
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

            repository.salesRepository.createSale(
                sale = sale,
                lines = saleLines,
                customerNameSnapshot = customer?.customerName ?: "عميل نقدي",
                notes = notes
            )
            if (updatedCustomer != null) repository.updateCustomer(updatedCustomer)
            repository.addNotification(notif)
            _uiState.update {
                it.copy(
                    cart = emptyList(),
                    showSettlementSheet = false,
                    currentDestination = NavDestination.HOME,
                    activeBottomNav = NavDestination.HOME
                )
            }
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
        val liveBalance = CustomerLedgerCalculator.calculateCustomerBalance(customer.id, state.allTransactions).balance
        val isFullPayment = amount >= (liveBalance - 0.001)
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

        val newPayment = com.example.data.db.CustomerPayment(
            id = "cp_${txId}",
            customerId = customer.id,
            amount = amount,
            paymentMethodId = "pm_cash",
            financialAccountId = "acc_cash",
            transactionDate = todayDate,
            createdAt = System.currentTimeMillis(),
            notes = state.quickPaymentNotes.ifBlank { "تسديد دفعة سريعة" },
            status = "ACTIVE"
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
            repository.recordCustomerPayment(newPayment)
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
        backupRestoreViewModel.exportBackup(context, uri, onResult)
    }

    fun prepareRestore(context: Context, uri: Uri, onError: () -> Unit, onSuccessSilent: () -> Unit) {
        backupRestoreViewModel.prepareRestore(context, uri, onError, onSuccessSilent)
    }

    fun confirmRestore(replaceStoreInfo: Boolean, onComplete: () -> Unit) {
        backupRestoreViewModel.confirmRestore(replaceStoreInfo, onComplete)
    }

    fun cancelRestore() {
        backupRestoreViewModel.cancelRestore()
    }
}

