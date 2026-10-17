package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.model.StoreStrings
import com.example.ui.theme.statusGreen
import com.example.ui.theme.statusRed
import com.example.ui.theme.statusRedContainer

/**
 * Unified, shared customer selector component.
 *
 * Behavior:
 * - Default state (nothing selected): displays "All Customers" / "كل العملاء" with a dropdown chevron (▼).
 * - Tapping the field opens an M3 bottom sheet listing all customers with a search box at the top.
 * - Selecting a customer updates the field to show the customer's name with an "X" icon at the trailing end.
 * - Tapping "X" clears the selection and restores the "All Customers" state.
 * - Full Arabic-first RTL support relying on Compose's built-in LayoutDirection mirroring.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerSelectorField(
    customers: List<CustomerAccount>,
    selectedCustomer: CustomerAccount?,
    onCustomerSelected: (CustomerAccount?) -> Unit,
    modifier: Modifier = Modifier,
    isArabic: Boolean = true,
    allCustomersLabel: String? = null,
    placeholderText: String? = null,
    label: String? = null,
    allowAllCustomers: Boolean = true,
    showBalance: Boolean = true,
    isRequired: Boolean = false,
    testTag: String = "customer_selector_field"
) {
    var isSheetOpen by remember { mutableStateOf(false) }
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val defaultAllText = allCustomersLabel ?: placeholderText ?: if (isArabic) {
        StoreStrings.ALL_CUSTOMERS_AR
    } else {
        StoreStrings.ALL_CUSTOMERS_EN
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(modifier = modifier) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isRequired && selectedCustomer == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Surface(
                onClick = { isSheetOpen = true },
                shape = RoundedCornerShape(12.dp),
                color = if (isRequired && selectedCustomer == null) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                } else if (selectedCustomer != null) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
                border = BorderStroke(
                    1.dp,
                    if (isRequired && selectedCustomer == null) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    } else if (selectedCustomer != null) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Start section: Icon + Customer Name / "All Customers"
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selectedCustomer != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedCustomer != null) {
                                Text(
                                    text = selectedCustomer.customerName.take(1),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            if (selectedCustomer != null && label == null) {
                                Text(
                                    text = if (isArabic) StoreStrings.CUSTOMER_CONTEXT_LABEL_AR else StoreStrings.CUSTOMER_CONTEXT_LABEL_EN,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = selectedCustomer?.customerName ?: defaultAllText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (selectedCustomer != null) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selectedCustomer != null) {
                                    MaterialTheme.colorScheme.primary
                                } else if (isRequired) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("${testTag}_value")
                            )

                            if (selectedCustomer != null && selectedCustomer.phone.isNotBlank()) {
                                Text(
                                    text = selectedCustomer.phone,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else if (isRequired && selectedCustomer == null) {
                                Text(
                                    text = if (isArabic) "(مطلوب)" else "(Required)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // End section: "X" clear icon if selected, or Chevron (▼) if not selected
                    if (selectedCustomer != null) {
                        IconButton(
                            onClick = { onCustomerSelected(null) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("${testTag}_clear")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isArabic) "إلغاء تحديد العميل" else "Clear selected customer",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = if (isArabic) "اختيار عميل" else "Select customer",
                            tint = if (isRequired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("${testTag}_chevron")
                        )
                    }
                }
            }
        }

        // Bottom Sheet Customer Picker
        if (isSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isSheetOpen = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                dragHandle = { BottomSheetDefaults.DragHandle() },
                modifier = Modifier.testTag("${testTag}_sheet")
            ) {
                var searchQuery by remember { mutableStateOf("") }
                val filteredCustomers = remember(customers, searchQuery) {
                    if (searchQuery.isBlank()) {
                        customers
                    } else {
                        val q = searchQuery.trim().lowercase()
                        customers.filter {
                            it.customerName.lowercase().contains(q) || it.phone.contains(q)
                        }
                    }
                }

                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                    ) {
                        // Title row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) StoreStrings.SELECT_CUSTOMER_AR else StoreStrings.SELECT_CUSTOMER_EN,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isArabic) "${customers.size} عميل" else "${customers.size} customers",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Search box
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = if (isArabic) "بحث بالاسم أو الهاتف..." else "Search by name or phone...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = if (isArabic) "بحث" else "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = if (isArabic) "مسح" else "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("${testTag}_search_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // "All Customers" option if allowed
                        if (allowAllCustomers) {
                            val isAllSelected = selectedCustomer == null
                            Surface(
                                onClick = {
                                    onCustomerSelected(null)
                                    isSheetOpen = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isAllSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isAllSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("${testTag}_all_option")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.People,
                                                contentDescription = null,
                                                tint = if (isAllSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = defaultAllText,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isArabic) "عرض كافة الحسابات والمعاملات" else "Show all accounts and transactions",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (isAllSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Customers list
                        if (filteredCustomers.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isArabic) "لا يوجد عملاء مطابقون" else "No matching customers found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 340.dp)
                                    .testTag("${testTag}_list"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredCustomers, key = { it.id }) { cust ->
                                    val isSelected = selectedCustomer?.id == cust.id
                                    Surface(
                                        onClick = {
                                            onCustomerSelected(cust)
                                            isSheetOpen = false
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("${testTag}_item_${cust.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                    ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = cust.customerName.take(1),
                                                        style = MaterialTheme.typography.titleSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = cust.customerName,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                        ),
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (cust.phone.isNotBlank()) {
                                                        Text(
                                                            text = cust.phone,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (showBalance) {
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Text(
                                                            text = AppCurrency.formatAmountWithDecimals(cust.balance, isArabic),
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                            color = if (cust.balance > 0) MaterialTheme.colorScheme.statusRed else MaterialTheme.colorScheme.statusGreen
                                                        )
                                                        Text(
                                                            text = if (cust.balance > 0) {
                                                                if (isArabic) "دين متبقي" else "Debit"
                                                            } else {
                                                                if (isArabic) "خالص" else "Clear"
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
