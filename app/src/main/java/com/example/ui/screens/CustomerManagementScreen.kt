package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.example.ui.components.CustomerSearchField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.model.LanguageMode
import com.example.model.StoreStrings
import com.example.ui.theme.GeoOutline
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary

private enum class CustomerFilterTab {
    ACTIVE,
    HAS_DEBT,
    SETTLED,
    ARCHIVED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerManagementScreen(
    customers: List<CustomerAccount>,
    archivedCustomerIds: Set<String>,
    languageMode: LanguageMode,
    onBackClick: () -> Unit,
    onAddCustomer: (name: String, phone: String) -> Unit,
    onUpdateCustomer: (CustomerAccount) -> Unit,
    onArchiveCustomer: (String) -> Unit,
    onUnarchiveCustomer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(CustomerFilterTab.ACTIVE) }

    // Dialog states
    var showAddDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<CustomerAccount?>(null) }
    var customerToArchive by remember { mutableStateOf<CustomerAccount?>(null) }
    var customerToRestore by remember { mutableStateOf<CustomerAccount?>(null) }

    // Active vs Archived split
    val activeCustomers = remember(customers, archivedCustomerIds) {
        customers.filter { it.id !in archivedCustomerIds }
    }
    val archivedCustomers = remember(customers, archivedCustomerIds) {
        customers.filter { it.id in archivedCustomerIds }
    }

    // Filtered list based on search and selected tab
    val displayedList = remember(activeCustomers, archivedCustomers, selectedFilter, searchQuery) {
        val basePool = if (selectedFilter == CustomerFilterTab.ARCHIVED) {
            archivedCustomers
        } else {
            when (selectedFilter) {
                CustomerFilterTab.ACTIVE -> activeCustomers
                CustomerFilterTab.HAS_DEBT -> activeCustomers.filter { it.balance > 0.001 }
                CustomerFilterTab.SETTLED -> activeCustomers.filter { it.balance <= 0.001 }
                CustomerFilterTab.ARCHIVED -> archivedCustomers
            }
        }

        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            basePool
        } else {
            basePool.filter {
                it.customerName.lowercase().contains(q) || it.phone.contains(q)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("customer_management_screen")
    ) {
        // TOP APP BAR
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = if (isArabic) "إدارة العملاء" else "Customer Management",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = if (isArabic) {
                            "${activeCustomers.size} عميل نشط • ${archivedCustomers.size} مؤرشف"
                        } else {
                            "${activeCustomers.size} active • ${archivedCustomers.size} archived"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("customer_mgmt_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isArabic) "رجوع" else "Back"
                    )
                }
            },
            actions = {
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("customer_mgmt_add_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isArabic) "إضافة عميل" else "Add Customer",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        HorizontalDivider(color = GeoOutlineVariant, thickness = 1.dp)

        // SEARCH & FILTER BAR
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Reusable Customer Search Field matching Home & Accounts
            CustomerSearchField(
                customers = if (selectedFilter == CustomerFilterTab.ARCHIVED) archivedCustomers else activeCustomers,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onCustomerSelected = { customer ->
                    searchQuery = customer.customerName
                },
                onClearSelection = {
                    searchQuery = ""
                },
                selectedCustomerId = null,
                placeholderText = if (isArabic) StoreStrings.SEARCH_CUSTOMER_ACCOUNTS_AR else StoreStrings.SEARCH_CUSTOMER_ACCOUNTS_EN,
                isArabic = isArabic,
                showBalance = false,
                simpleSuggestions = true,
                inputTestTag = "customer_mgmt_search_input",
                dropdownTestTag = "customer_mgmt_search_suggestions",
                itemTagPrefix = "customer_mgmt_suggestion_"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == CustomerFilterTab.ACTIVE,
                    onClick = { selectedFilter = CustomerFilterTab.ACTIVE },
                    label = {
                        Text(
                            text = if (isArabic) "النشطون (${activeCustomers.size})" else "Active (${activeCustomers.size})",
                            fontSize = 12.sp,
                            fontWeight = if (selectedFilter == CustomerFilterTab.ACTIVE) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoPrimary.copy(alpha = 0.12f),
                        selectedLabelColor = GeoPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == CustomerFilterTab.ACTIVE,
                        borderColor = if (selectedFilter == CustomerFilterTab.ACTIVE) GeoPrimary else GeoOutline
                    ),
                    modifier = Modifier.testTag("customer_mgmt_filter_active")
                )

                FilterChip(
                    selected = selectedFilter == CustomerFilterTab.HAS_DEBT,
                    onClick = { selectedFilter = CustomerFilterTab.HAS_DEBT },
                    label = {
                        Text(
                            text = if (isArabic) "عليهم ديون" else "In Debt",
                            fontSize = 12.sp,
                            fontWeight = if (selectedFilter == CustomerFilterTab.HAS_DEBT) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoPrimary.copy(alpha = 0.12f),
                        selectedLabelColor = GeoPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == CustomerFilterTab.HAS_DEBT,
                        borderColor = if (selectedFilter == CustomerFilterTab.HAS_DEBT) GeoPrimary else GeoOutline
                    ),
                    modifier = Modifier.testTag("customer_mgmt_filter_has_debt")
                )

                FilterChip(
                    selected = selectedFilter == CustomerFilterTab.SETTLED,
                    onClick = { selectedFilter = CustomerFilterTab.SETTLED },
                    label = {
                        Text(
                            text = if (isArabic) "خالص" else "Settled",
                            fontSize = 12.sp,
                            fontWeight = if (selectedFilter == CustomerFilterTab.SETTLED) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoPrimary.copy(alpha = 0.12f),
                        selectedLabelColor = GeoPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == CustomerFilterTab.SETTLED,
                        borderColor = if (selectedFilter == CustomerFilterTab.SETTLED) GeoPrimary else GeoOutline
                    ),
                    modifier = Modifier.testTag("customer_mgmt_filter_settled")
                )

                FilterChip(
                    selected = selectedFilter == CustomerFilterTab.ARCHIVED,
                    onClick = { selectedFilter = CustomerFilterTab.ARCHIVED },
                    label = {
                        Text(
                            text = if (isArabic) "الأرشيف (${archivedCustomers.size})" else "Archive (${archivedCustomers.size})",
                            fontSize = 12.sp,
                            fontWeight = if (selectedFilter == CustomerFilterTab.ARCHIVED) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == CustomerFilterTab.ARCHIVED,
                        borderColor = if (selectedFilter == CustomerFilterTab.ARCHIVED) MaterialTheme.colorScheme.secondary else GeoOutline
                    ),
                    modifier = Modifier.testTag("customer_mgmt_filter_archived")
                )
            }
        }

        HorizontalDivider(color = GeoOutlineVariant, thickness = 0.5.dp)

        // CUSTOMER CARDS LIST
        if (displayedList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (selectedFilter == CustomerFilterTab.ARCHIVED) Icons.Default.Archive else Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (selectedFilter == CustomerFilterTab.ARCHIVED) {
                            if (isArabic) "لا يوجد عملاء مؤرشفون" else "No archived customers"
                        } else if (searchQuery.isNotBlank()) {
                            if (isArabic) "لا توجد نتائج مطابقة للبحث" else "No matching customers found"
                        } else {
                            if (isArabic) StoreStrings.NO_CUSTOMERS_YET_AR else StoreStrings.NO_CUSTOMERS_YET_EN
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedList, key = { it.id }) { customer ->
                    val isArchived = customer.id in archivedCustomerIds
                    CustomerManagementCard(
                        customer = customer,
                        isArchived = isArchived,
                        isArabic = isArabic,
                        onEditClick = { customerToEdit = customer },
                        onArchiveClick = { customerToArchive = customer },
                        onRestoreClick = { customerToRestore = customer }
                    )
                }
            }
        }
    }

    // ==========================================
    // DIALOG: ADD NEW CUSTOMER
    // ==========================================
    if (showAddDialog) {
        AddCustomerDialog(
            isArabic = isArabic,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, phone ->
                onAddCustomer(name, phone)
                showAddDialog = false
            }
        )
    }

    // ==========================================
    // DIALOG: EDIT CUSTOMER
    // ==========================================
    customerToEdit?.let { customer ->
        EditCustomerDialog(
            customer = customer,
            isArabic = isArabic,
            onDismiss = { customerToEdit = null },
            onConfirm = { updated ->
                onUpdateCustomer(updated)
                customerToEdit = null
            }
        )
    }

    // ==========================================
    // DIALOG: ARCHIVE / CANCEL CONFIRMATION
    // (Moves to Archive, NEVER deletes permanently)
    // ==========================================
    customerToArchive?.let { customer ->
        AlertDialog(
            onDismissRequest = { customerToArchive = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = if (isArabic) "نقل إلى الأرشيف" else "Move to Archive",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isArabic) {
                            "هل أنت متأكد من رغبتك في نقل العميل \"${customer.customerName}\" إلى الأرشيف؟"
                        } else {
                            "Are you sure you want to move customer \"${customer.customerName}\" to the archive?"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isArabic) {
                            "ملاحظة: سيتم نقل هذا الحساب من القائمة النشطة إلى الأرشيف مع الحفاظ الكامل على كافة سجلات العمليات المالية والتاريخية دون أي حذف نهائي."
                        } else {
                            "Notice: This account will be moved from active management into the archive. All financial and transaction history is completely preserved without permanent deletion."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onArchiveCustomer(customer.id)
                        customerToArchive = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("confirm_archive_button")
                ) {
                    Text(if (isArabic) "أرشفة" else "Archive")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { customerToArchive = null },
                    modifier = Modifier.testTag("cancel_archive_button")
                ) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    // ==========================================
    // DIALOG: RESTORE FROM ARCHIVE
    // ==========================================
    customerToRestore?.let { customer ->
        AlertDialog(
            onDismissRequest = { customerToRestore = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = GeoPrimary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = if (isArabic) "استعادة العميل من الأرشيف" else "Restore Customer from Archive",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = if (isArabic) {
                        "هل ترغب في إعادة العميل \"${customer.customerName}\" إلى قائمة العملاء النشطين؟"
                    } else {
                        "Do you want to restore customer \"${customer.customerName}\" back to active customers?"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUnarchiveCustomer(customer.id)
                        customerToRestore = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                    modifier = Modifier.testTag("confirm_restore_button")
                ) {
                    Text(if (isArabic) "استعادة" else "Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { customerToRestore = null }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }
}

/**
 * Individual Customer Card adhering strictly to the requested hierarchy:
 * 1. Customer name
 * 2. Phone number
 * 3. Current outstanding balance
 * 4. Status
 * 5. Edit action
 * 6. Archive/Cancel action
 *
 * NOTE: NO images or avatars are displayed, respecting SmallStore's image prohibition.
 */
@Composable
private fun CustomerManagementCard(
    customer: CustomerAccount,
    isArchived: Boolean,
    isArabic: Boolean,
    onEditClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = GeoOutlineVariant, shape = RoundedCornerShape(12.dp))
            .testTag("customer_mgmt_card_${customer.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // -------------------------------------------------------------
            // ROW 1: Customer Name & Status Badge
            // -------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = customer.customerName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // 4. Status Badge (Active or Archived)
                CustomerStatusBadge(
                    isArchived = isArchived,
                    isArabic = isArabic
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // -------------------------------------------------------------
            // ROW 2: Phone number
            // -------------------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = customer.phone.ifBlank {
                        if (isArabic) "لا يوجد رقم هاتف" else "No phone number"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (customer.phone.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // -------------------------------------------------------------
            // ROW 3: Current outstanding balance
            // -------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) "الرصيد القائم:" else "Current Balance:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = AppCurrency.formatAmountWithDecimals(customer.balance, isArabic),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = when {
                        customer.balance > 0.001 -> Color(0xFFDC2626) // Red debt
                        customer.balance < -0.001 -> Color(0xFF2563EB) // Blue credit
                        else -> Color(0xFF16A34A) // Green settled
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = GeoOutlineVariant.copy(alpha = 0.6f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // -------------------------------------------------------------
            // ACTIONS ROW: 5. Edit Action & 6. Archive/Cancel Action
            // -------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isArchived) {
                    // Restore action for archived customers
                    OutlinedButton(
                        onClick = onRestoreClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("customer_mgmt_restore_${customer.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isArabic) "استعادة" else "Restore",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // 5. Edit Action
                    OutlinedButton(
                        onClick = onEditClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("customer_mgmt_edit_${customer.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isArabic) StoreStrings.EDIT_CUSTOMER_AR else StoreStrings.EDIT_CUSTOMER_EN,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 6. Archive/Cancel Action (Never permanent delete!)
                    OutlinedButton(
                        onClick = onArchiveClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("customer_mgmt_archive_${customer.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isArabic) "أرشفة" else "Archive",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean status chip indicating customer real active or archived state.
 * Active: "نشط" / "Active" with green status styling
 * Archived: "مؤرشف" / "Archived" with slate/gray status styling
 */
@Composable
private fun CustomerStatusBadge(
    isArchived: Boolean,
    isArabic: Boolean
) {
    if (isArchived) {
        Surface(
            color = Color(0xFFF1F5F9),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                .testTag("customer_status_archived")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF64748B), CircleShape)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isArabic) "مؤرشف" else "Archived",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569)
                )
            }
        }
    } else {
        Surface(
            color = Color(0xFFDCFCE7),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .border(1.dp, Color(0xFF16A34A).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                .testTag("customer_status_active")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF16A34A), CircleShape)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isArabic) "نشط" else "Active",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF16A34A)
                )
            }
        }
    }
}

/**
 * Edit Customer Dialog allowing name and phone editing with validation.
 */
@Composable
private fun EditCustomerDialog(
    customer: CustomerAccount,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CustomerAccount) -> Unit
) {
    var name by remember { mutableStateOf(customer.customerName) }
    var phone by remember { mutableStateOf(customer.phone) }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isArabic) StoreStrings.EDIT_CUSTOMER_AR else StoreStrings.EDIT_CUSTOMER_EN,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text(if (isArabic) "اسم العميل" else "Customer Name") },
                    isError = nameError,
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_customer_name_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(if (isArabic) "رقم الهاتف" else "Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_customer_phone_input")
                )

                // Current balance display (informative)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "الرصيد المالي الحالي:" else "Current Balance:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = AppCurrency.formatAmountWithDecimals(customer.balance, isArabic),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isBlank()) {
                        nameError = true
                    } else {
                        onConfirm(
                            customer.copy(
                                customerName = name.trim(),
                                phone = phone.trim()
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                modifier = Modifier.testTag("save_edit_customer_button")
            ) {
                Text(if (isArabic) "حفظ التعديلات" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isArabic) "إلغاء" else "Cancel")
            }
        }
    )
}

/**
 * Add Customer Dialog creating a new customer record via existing data flow.
 */
@Composable
private fun AddCustomerDialog(
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isArabic) "إضافة عميل جديد" else "Add New Customer",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text(if (isArabic) "اسم العميل *" else "Customer Name *") },
                    isError = nameError,
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_customer_name_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(if (isArabic) "رقم الهاتف" else "Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_customer_phone_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isBlank()) {
                        nameError = true
                    } else {
                        onConfirm(name.trim(), phone.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                modifier = Modifier.testTag("save_add_customer_button")
            ) {
                Text(if (isArabic) "إضافة" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isArabic) "إلغاء" else "Cancel")
            }
        }
    )
}
