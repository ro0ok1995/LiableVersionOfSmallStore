package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.StoreRepository
import com.example.model.AccountFilter
import com.example.model.ArchiveConflict
import com.example.model.CustomerAccount
import com.example.model.NavDestination
import com.example.model.TransactionItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CustomerViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: StoreRepository = StoreRepository.getInstance(application)
) : AndroidViewModel(application) {

    private val _customers = MutableStateFlow<List<CustomerAccount>>(emptyList())
    val customers: StateFlow<List<CustomerAccount>> = _customers.asStateFlow()

    private val _archivedCustomers = MutableStateFlow<List<CustomerAccount>>(emptyList())
    val archivedCustomers: StateFlow<List<CustomerAccount>> = _archivedCustomers.asStateFlow()

    private val _archivedCustomerIds = MutableStateFlow<Set<String>>(emptySet())
    val archivedCustomerIds: StateFlow<Set<String>> = _archivedCustomerIds.asStateFlow()

    private val _accountsSearchQuery = MutableStateFlow("")
    val accountsSearchQuery: StateFlow<String> = _accountsSearchQuery.asStateFlow()

    private val _accountsFilter = MutableStateFlow(AccountFilter.ALL)
    val accountsFilter: StateFlow<AccountFilter> = _accountsFilter.asStateFlow()

    private val _accountsSelectedCustomerDetails = MutableStateFlow<CustomerAccount?>(null)
    val accountsSelectedCustomerDetails: StateFlow<CustomerAccount?> = _accountsSelectedCustomerDetails.asStateFlow()

    private val _customerDetailsPreviousDestination = MutableStateFlow(NavDestination.ACCOUNTS)
    val customerDetailsPreviousDestination: StateFlow<NavDestination> = _customerDetailsPreviousDestination.asStateFlow()

    private val _showAddCustomerDialog = MutableStateFlow(false)
    val showAddCustomerDialog: StateFlow<Boolean> = _showAddCustomerDialog.asStateFlow()

    private val _pendingCustomerConflict = MutableStateFlow<ArchiveConflict.CustomerConflict?>(null)
    val pendingCustomerConflict: StateFlow<ArchiveConflict.CustomerConflict?> = _pendingCustomerConflict.asStateFlow()

    val allCustomers: Flow<List<CustomerAccount>> = repository.customers
    val allArchivedCustomers: Flow<List<CustomerAccount>> = repository.archivedCustomers

    init {
        viewModelScope.launch {
            repository.customers.collect { list ->
                _customers.value = list
            }
        }

        viewModelScope.launch {
            repository.archivedCustomers.collect { list ->
                _archivedCustomers.value = list
                _archivedCustomerIds.value = list.map { c -> c.id }.toSet()
            }
        }
    }

    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun setAccountsSearchQuery(query: String) {
        _accountsSearchQuery.value = query
    }

    fun setAccountsFilter(filter: AccountFilter) {
        _accountsFilter.value = filter
    }

    fun selectCustomerDetails(customer: CustomerAccount?) {
        _accountsSelectedCustomerDetails.value = customer
    }

    fun openCustomerDetailsFromAccounts(customer: CustomerAccount) {
        selectCustomerDetails(customer)
        _customerDetailsPreviousDestination.value = NavDestination.ACCOUNTS
    }

    fun navigateToCustomerProfileFromActivity(transaction: TransactionItem) {
        val customer = resolveCustomerForTransaction(_customers.value, transaction)
        if (customer != null) {
            selectCustomerDetails(customer)
            _customerDetailsPreviousDestination.value = NavDestination.HOME
        }
    }

    fun openAddCustomerDialog() {
        _showAddCustomerDialog.value = true
    }

    fun closeAddCustomerDialog() {
        _showAddCustomerDialog.value = false
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
        _showAddCustomerDialog.value = false
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

    fun recordCustomerAdjustment(
        customerId: String,
        amount: Double,
        direction: String,
        date: String,
        reason: String,
        reference: String? = null
    ) {
        viewModelScope.launch {
            repository.recordAdjustment(
                entityType = "CUSTOMER",
                entityId = customerId,
                amount = amount,
                direction = direction,
                date = date,
                reason = reason,
                reference = reference
            )
        }
    }

    fun archiveCustomer(customerId: String, date: String = getCurrentDateString()) {
        if (_accountsSelectedCustomerDetails.value?.id == customerId) {
            _accountsSelectedCustomerDetails.value = null
        }
        viewModelScope.launch {
            repository.archiveCustomer(customerId, date)
        }
    }

    fun unarchiveCustomer(customerId: String) {
        val customer = _archivedCustomers.value.firstOrNull { it.id == customerId }
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
        activeList: List<CustomerAccount> = _customers.value,
        onConflict: ((ArchiveConflict.CustomerConflict) -> Unit)? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        val conflict = checkCustomerConflict(customer, activeList)
        if (conflict != null) {
            _pendingCustomerConflict.value = conflict
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
        activeList: List<CustomerAccount> = _customers.value
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

    fun clearPendingCustomerConflict() {
        _pendingCustomerConflict.value = null
    }

    fun deleteCustomerPermanently(customer: CustomerAccount, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.deleteCustomerPermanently(customer.id)
            onComplete?.invoke()
        }
    }

    companion object {
        fun resolveCustomerForTransaction(
            customers: List<CustomerAccount>,
            transaction: TransactionItem
        ): CustomerAccount? {
            if (!transaction.customerId.isNullOrBlank()) {
                return customers.firstOrNull { it.id == transaction.customerId }
            }
            return null
        }
    }
}
