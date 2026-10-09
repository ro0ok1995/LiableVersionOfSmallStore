package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AccountFilter
import com.example.model.AccountSortOption
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.model.LanguageMode
import com.example.model.SettlementType
import com.example.model.StoreStrings
import com.example.model.TransactionItem
import com.example.accounting.FinancialReportCalculator
import com.example.accounting.SupplierBalanceSummary
import com.example.accounting.SupplierLedgerCalculator
import com.example.data.db.Purchase
import com.example.data.db.Supplier
import com.example.data.db.SupplierPayment
import com.example.ui.components.CustomerSearchField
import com.example.ui.theme.GeoOutline
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusRedBg
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    accounts: List<CustomerAccount>,
    allCustomers: List<CustomerAccount> = emptyList(),
    transactions: List<TransactionItem> = emptyList(),
    searchQuery: String,
    filter: AccountFilter,
    selectedCustomerDetails: CustomerAccount?,
    showAddCustomerDialog: Boolean,
    languageMode: LanguageMode,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (AccountFilter) -> Unit,
    onCustomerClick: (CustomerAccount) -> Unit,
    onOpenAddCustomerDialog: () -> Unit,
    onCloseAddCustomerDialog: () -> Unit,
    onAddCustomer: (name: String, phone: String) -> Unit,
    suppliers: List<Supplier> = emptyList(),
    purchases: List<Purchase> = emptyList(),
    supplierPayments: List<SupplierPayment> = emptyList(),
    onSupplierClick: ((Supplier) -> Unit)? = null,
    onOpenAddSupplierDialog: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val currency = AppCurrency.SYMBOL
    val focusManager = LocalFocusManager.current

    // 0 = Customers (default), 1 = Suppliers
    var selectedAccountTab by remember { mutableIntStateOf(0) }
    var sortOption by remember { mutableStateOf(AccountSortOption.DEFAULT) }

    // Supplier local search & filter state
    var supplierSearchQuery by remember { mutableStateOf("") }
    // 0 = All, 1 = Has Payable (Debt), 2 = Settled
    var supplierFilter by remember { mutableIntStateOf(0) }
    var showLocalAddSupplierDialog by remember { mutableStateOf(false) }

    val matchingCustomers = remember(searchQuery, allCustomers, accounts) {
        val pool = if (allCustomers.isNotEmpty()) allCustomers else accounts
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else pool.filter { it.customerName.lowercase().contains(q) || it.phone.contains(q) }
    }

    val customerCashTotals = remember(transactions) {
        transactions.groupBy { it.customerId }.mapValues { (_, txs) ->
            FinancialReportCalculator.calculate(txs).cashSales
        }
    }

    val customerBalances = remember(accounts) {
        accounts.associate { customer -> customer.id to customer.balance }
    }

    val sortedAccounts = remember(accounts, sortOption, customerCashTotals, customerBalances) {
        when (sortOption) {
            AccountSortOption.DEFAULT -> accounts
            AccountSortOption.HIGHEST_DEBT -> accounts.sortedByDescending { customerBalances[it.id] ?: 0.0 }
            AccountSortOption.HIGHEST_CASH -> accounts.sortedByDescending { customer ->
                customerCashTotals[customer.id] ?: 0.0
            }
        }
    }

    // Authoritative supplier balances calculated via SupplierLedgerCalculator
    val supplierBalances = remember(suppliers, purchases, supplierPayments) {
        suppliers.associate { sup ->
            sup.id to SupplierLedgerCalculator.calculateSupplierBalance(
                supplierId = sup.id,
                purchases = purchases,
                payments = supplierPayments
            )
        }
    }

    val filteredSuppliers = remember(suppliers, supplierSearchQuery, supplierFilter, supplierBalances) {
        var list = suppliers
        val q = supplierSearchQuery.trim().lowercase()
        if (q.isNotEmpty()) {
            list = list.filter { sup ->
                sup.name.lowercase().contains(q) || sup.phone.lowercase().contains(q)
            }
        }
        when (supplierFilter) {
            1 -> list.filter { sup -> (supplierBalances[sup.id]?.balance ?: 0.0) > 0.001 }
            2 -> list.filter { sup -> Math.abs(supplierBalances[sup.id]?.balance ?: 0.0) <= 0.001 }
            else -> list
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("accounts_screen")
    ) {
        // TOP ACTION BAR & SEARCH
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) StoreStrings.ACCOUNTS_AR else StoreStrings.ACCOUNTS_EN,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (selectedAccountTab == 0) {
                    Button(
                        onClick = onOpenAddCustomerDialog,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                        contentPadding = ButtonDefaults.ContentPadding,
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("add_customer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isArabic) StoreStrings.ADD_CUSTOMER_AR else StoreStrings.ADD_CUSTOMER_EN,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (onOpenAddSupplierDialog != null) {
                                onOpenAddSupplierDialog()
                            } else {
                                showLocalAddSupplierDialog = true
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                        contentPadding = ButtonDefaults.ContentPadding,
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("add_supplier_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isArabic) "مورد جديد" else "New Supplier",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // CUSTOMERS / SUPPLIERS TAB SELECTOR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("accounts_tab_selector_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = if (selectedAccountTab == 0) GeoPrimary else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    border = if (selectedAccountTab == 0) null else BorderStroke(1.dp, GeoOutlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedAccountTab = 0 }
                        .testTag("tab_customers")
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isArabic) StoreStrings.CUSTOMERS_AR else StoreStrings.CUSTOMERS_EN,
                            fontSize = 12.sp,
                            fontWeight = if (selectedAccountTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedAccountTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (selectedAccountTab == 1) GeoPrimary else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    border = if (selectedAccountTab == 1) null else BorderStroke(1.dp, GeoOutlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedAccountTab = 1 }
                        .testTag("tab_suppliers")
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isArabic) "الموردون" else "Suppliers",
                            fontSize = 12.sp,
                            fontWeight = if (selectedAccountTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedAccountTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedAccountTab == 0) {
                // Reusable Customer Search Field with Dropdown
                CustomerSearchField(
                customers = if (allCustomers.isNotEmpty()) allCustomers else accounts,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onCustomerSelected = { customer ->
                    onSearchQueryChange(customer.customerName)
                },
                onClearSelection = {
                    onSearchQueryChange("")
                },
                selectedCustomerId = null,
                placeholderText = if (isArabic) StoreStrings.SEARCH_CUSTOMER_ACCOUNTS_AR else StoreStrings.SEARCH_CUSTOMER_ACCOUNTS_EN,
                currency = currency,
                isArabic = isArabic,
                showBalance = false,
                simpleSuggestions = true,
                inputTestTag = "accounts_search_input",
                dropdownTestTag = "accounts_search_results_overlay",
                itemTagPrefix = "search_result_item_"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Control: "All / Has Debt / Recently Active"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("accounts_filter_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == AccountFilter.ALL,
                    onClick = { onFilterChange(AccountFilter.ALL) },
                    label = {
                        Text(
                            text = if (isArabic) StoreStrings.FILTER_ALL_AR else StoreStrings.FILTER_ALL_EN,
                            fontSize = 12.sp,
                            fontWeight = if (filter == AccountFilter.ALL) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoPrimary.copy(alpha = 0.12f),
                        selectedLabelColor = GeoPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filter == AccountFilter.ALL,
                        borderColor = if (filter == AccountFilter.ALL) GeoPrimary else GeoOutline
                    ),
                    modifier = Modifier.testTag("filter_all")
                )

                FilterChip(
                    selected = filter == AccountFilter.HAS_DEBT,
                    onClick = { onFilterChange(AccountFilter.HAS_DEBT) },
                    label = {
                        Text(
                            text = if (isArabic) StoreStrings.FILTER_HAS_DEBT_AR else StoreStrings.FILTER_HAS_DEBT_EN,
                            fontSize = 12.sp,
                            fontWeight = if (filter == AccountFilter.HAS_DEBT) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoPrimary.copy(alpha = 0.12f),
                        selectedLabelColor = GeoPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filter == AccountFilter.HAS_DEBT,
                        borderColor = if (filter == AccountFilter.HAS_DEBT) GeoPrimary else GeoOutline
                    ),
                    modifier = Modifier.testTag("filter_has_debt")
                )

                FilterChip(
                    selected = filter == AccountFilter.RECENTLY_ACTIVE,
                    onClick = { onFilterChange(AccountFilter.RECENTLY_ACTIVE) },
                    label = {
                        Text(
                            text = if (isArabic) StoreStrings.FILTER_RECENTLY_ACTIVE_AR else StoreStrings.FILTER_RECENTLY_ACTIVE_EN,
                            fontSize = 12.sp,
                            fontWeight = if (filter == AccountFilter.RECENTLY_ACTIVE) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoPrimary.copy(alpha = 0.12f),
                        selectedLabelColor = GeoPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filter == AccountFilter.RECENTLY_ACTIVE,
                        borderColor = if (filter == AccountFilter.RECENTLY_ACTIVE) GeoPrimary else GeoOutline
                    ),
                    modifier = Modifier.testTag("filter_recently_active")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sort by control (independent of filter chips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("accounts_sort_row")
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Sort by",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isArabic) "ترتيب:" else "Sort:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilterChip(
                    selected = sortOption == AccountSortOption.DEFAULT,
                    onClick = { sortOption = AccountSortOption.DEFAULT },
                    label = {
                        Text(
                            text = if (isArabic) StoreStrings.SORT_DEFAULT_AR else StoreStrings.SORT_DEFAULT_EN,
                            fontSize = 11.sp,
                            fontWeight = if (sortOption == AccountSortOption.DEFAULT) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("sort_default")
                )
                FilterChip(
                    selected = sortOption == AccountSortOption.HIGHEST_DEBT,
                    onClick = { sortOption = AccountSortOption.HIGHEST_DEBT },
                    label = {
                        Text(
                            text = if (isArabic) StoreStrings.SORT_HIGHEST_DEBT_AR else StoreStrings.SORT_HIGHEST_DEBT_EN,
                            fontSize = 11.sp,
                            fontWeight = if (sortOption == AccountSortOption.HIGHEST_DEBT) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("sort_highest_debt")
                )
                FilterChip(
                    selected = sortOption == AccountSortOption.HIGHEST_CASH,
                    onClick = { sortOption = AccountSortOption.HIGHEST_CASH },
                    label = {
                        Text(
                            text = if (isArabic) StoreStrings.SORT_HIGHEST_CASH_AR else StoreStrings.SORT_HIGHEST_CASH_EN,
                            fontSize = 11.sp,
                            fontWeight = if (sortOption == AccountSortOption.HIGHEST_CASH) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("sort_highest_cash")
                )
            }
        } else {
            // SUPPLIER SEARCH FIELD
            OutlinedTextField(
                value = supplierSearchQuery,
                onValueChange = { supplierSearchQuery = it },
                placeholder = {
                    Text(
                        text = if (isArabic) "بحث عن مورد بالاسم أو الهاتف..." else "Search suppliers by name or phone...",
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (supplierSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { supplierSearchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isArabic) "مسح" else "Clear",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GeoPrimary,
                    unfocusedBorderColor = GeoOutlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("suppliers_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // SUPPLIERS FILTER ROW: ALL / HAS DEBT / SETTLED
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("suppliers_filter_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = supplierFilter == 0,
                    onClick = { supplierFilter = 0 },
                    label = {
                        Text(
                            text = if (isArabic) "الكل" else "All",
                            fontSize = 12.sp,
                            fontWeight = if (supplierFilter == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoPrimary.copy(alpha = 0.12f),
                        selectedLabelColor = GeoPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = supplierFilter == 0,
                        borderColor = if (supplierFilter == 0) GeoPrimary else GeoOutline
                    ),
                    modifier = Modifier.testTag("supplier_filter_all")
                )

                FilterChip(
                    selected = supplierFilter == 1,
                    onClick = { supplierFilter = 1 },
                    label = {
                        Text(
                            text = if (isArabic) "مستحق للمورد" else "Has Payable",
                            fontSize = 12.sp,
                            fontWeight = if (supplierFilter == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoPrimary.copy(alpha = 0.12f),
                        selectedLabelColor = GeoPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = supplierFilter == 1,
                        borderColor = if (supplierFilter == 1) GeoPrimary else GeoOutline
                    ),
                    modifier = Modifier.testTag("supplier_filter_has_debt")
                )

                FilterChip(
                    selected = supplierFilter == 2,
                    onClick = { supplierFilter = 2 },
                    label = {
                        Text(
                            text = if (isArabic) "خالص الحساب" else "Settled",
                            fontSize = 12.sp,
                            fontWeight = if (supplierFilter == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoPrimary.copy(alpha = 0.12f),
                        selectedLabelColor = GeoPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = supplierFilter == 2,
                        borderColor = if (supplierFilter == 2) GeoPrimary else GeoOutline
                    ),
                    modifier = Modifier.testTag("supplier_filter_settled")
                )
            }
        }
    }

        HorizontalDivider(color = GeoOutline, thickness = 1.dp)

        if (selectedAccountTab == 0) {
            // CUSTOMER LIST OR EMPTY STATE
            if (sortedAccounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.testTag("accounts_empty_state")
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isArabic) StoreStrings.NO_CUSTOMERS_YET_AR else StoreStrings.NO_CUSTOMERS_YET_EN,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onOpenAddCustomerDialog,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                            modifier = Modifier.testTag("empty_state_add_customer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isArabic) StoreStrings.ADD_CUSTOMER_AR else StoreStrings.ADD_CUSTOMER_EN,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .testTag("accounts_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(items = sortedAccounts, key = { it.id }) { customer ->
                        val liveBalance = customerBalances[customer.id] ?: 0.0
                        CustomerCardItem(
                            customer = customer,
                            balance = liveBalance,
                            currency = currency,
                            isArabic = isArabic,
                            onClick = { onCustomerClick(customer) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        } else {
            // SUPPLIER LIST OR EMPTY STATE
            if (filteredSuppliers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.testTag("suppliers_empty_state")
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isArabic) "لا يوجد موردون مسجلون بعد" else "No suppliers registered yet",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (onOpenAddSupplierDialog != null) {
                                    onOpenAddSupplierDialog()
                                } else {
                                    showLocalAddSupplierDialog = true
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                            modifier = Modifier.testTag("empty_state_add_supplier_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isArabic) "إضافة مورد جديد" else "Add New Supplier",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .testTag("suppliers_accounts_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(items = filteredSuppliers, key = { it.id }) { supplier ->
                        val summary = supplierBalances[supplier.id] ?: SupplierBalanceSummary(supplierId = supplier.id)
                        SupplierAccountCardItem(
                            supplier = supplier,
                            summary = summary,
                            currency = currency,
                            isArabic = isArabic,
                            onClick = { onSupplierClick?.invoke(supplier) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showAddCustomerDialog) {
        AddCustomerDialog(
            isArabic = isArabic,
            onDismiss = onCloseAddCustomerDialog,
            onConfirm = onAddCustomer
        )
    }
}

@Composable
private fun CustomerCardItem(
    customer: CustomerAccount,
    balance: Double,
    currency: String,
    isArabic: Boolean = true,
    onClick: () -> Unit
) {
    val hasDebt = balance > 0.001
    val isPaidUp = Math.abs(balance) <= 0.001

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = GeoOutlineVariant, shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("customer_card_${customer.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = GeoPrimary.copy(alpha = 0.08f),
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = customer.customerName.take(1),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = GeoPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.customerName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (customer.phone.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = customer.phone,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (isPaidUp) {
                Surface(
                    color = StatusGreenBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isArabic) StoreStrings.PAID_UP_AR else StoreStrings.PAID_UP_EN,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = if (hasDebt) StatusRedBg else StatusGreenBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "%s%,.2f %s",
                                if (hasDebt) "" else "-",
                                balance,
                                currency
                            ),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasDebt) StatusRed else StatusGreen,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AddCustomerDialog(
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            focusManager.clearFocus()
            onDismiss()
        },
        title = {
            Text(
                text = if (isArabic) StoreStrings.ADD_CUSTOMER_AR else StoreStrings.ADD_CUSTOMER_EN,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = {
                        Text(if (isArabic) "اسم العميل *" else "Customer Name *")
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    isError = nameError,
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_customer_name_field")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = {
                        Text(if (isArabic) "رقم الهاتف (اختياري)" else "Phone number (optional)")
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_customer_phone_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        focusManager.clearFocus()
                        onConfirm(name.trim(), phone.trim())
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                modifier = Modifier.testTag("confirm_add_customer_button")
            ) {
                Text(
                    text = if (isArabic) "حفظ" else "Save",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("cancel_add_customer_button")
            ) {
                Text(text = if (isArabic) "إلغاء" else "Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun SupplierAccountCardItem(
    supplier: Supplier,
    summary: SupplierBalanceSummary,
    currency: String,
    isArabic: Boolean = true,
    onClick: () -> Unit
) {
    val payable = summary.balance
    val hasPayable = payable > 0.001
    val isSettled = Math.abs(payable) <= 0.001

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = GeoOutlineVariant, shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("supplier_card_${supplier.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = supplier.name.take(1),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = supplier.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (supplier.phone.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = supplier.phone,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (isSettled) {
                Surface(
                    color = StatusGreenBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isArabic) "خالص الحساب" else "Settled",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = if (hasPayable) StatusRedBg else StatusGreenBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "%s%,.2f %s",
                                if (hasPayable) "" else "-",
                                Math.abs(payable),
                                currency
                            ),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasPayable) StatusRed else StatusGreen,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasPayable) (if (isArabic) "مستحق للمورد" else "Payable") else (if (isArabic) "رصيد مقدم" else "Advance"),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
