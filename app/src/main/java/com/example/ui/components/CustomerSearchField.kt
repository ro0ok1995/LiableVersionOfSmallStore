package com.example.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.model.StoreStrings
import com.example.ui.theme.statusGreen
import com.example.ui.theme.statusRed
import java.util.Locale

/**
 * Reusable Customer Search Field with attached dropdown suggestions.
 * Follows the SmallStore Design System with Arabic RTL support,
 * clean empty states, and reliable selection/clearing behavior.
 */
@Composable
fun CustomerSearchField(
    customers: List<CustomerAccount>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCustomerSelected: (CustomerAccount) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
    selectedCustomerId: String? = null,
    placeholderText: String? = null,
    currency: String = AppCurrency.SYMBOL,
    isArabic: Boolean = true,
    showBalance: Boolean = true,
    inputTestTag: String = "customer_search_input",
    dropdownTestTag: String = "customer_search_suggestions",
    itemTagPrefix: String = "customer_suggestion_",
    maxDropdownHeight: Dp = 240.dp
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    var isDropdownOpen by remember { mutableStateOf(false) }

    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    // Filter customers immediately based on input (name or phone)
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

    val placeholder = placeholderText ?: if (isArabic) StoreStrings.SEARCH_CUSTOMER_AR else StoreStrings.SEARCH_CUSTOMER_EN

    // Close dropdown on Android system back press when open
    BackHandler(enabled = isDropdownOpen) {
        isDropdownOpen = false
        focusManager.clearFocus()
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = modifier.fillMaxWidth()
        ) {
            // THE SEARCH INPUT FIELD
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { newQuery ->
                    onSearchQueryChange(newQuery)
                    isDropdownOpen = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            isDropdownOpen = true
                        }
                    }
                    .testTag(inputTestTag),
                placeholder = {
                    Text(
                        text = placeholder,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = if (isArabic) "بحث" else "Search",
                        tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (searchQuery.isNotBlank() || selectedCustomerId != null) {
                            IconButton(
                                onClick = {
                                    onSearchQueryChange("")
                                    onClearSelection()
                                    // Close dropdown on explicit clear to restore normal screen view
                                    isDropdownOpen = false
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("${inputTestTag}_clear")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = if (isArabic) "مسح البحث" else "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else if (customers.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    isDropdownOpen = !isDropdownOpen
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDropdownOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isArabic) "قائمة العملاء" else "Customer list",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (selectedCustomerId != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        isDropdownOpen = false
                        focusManager.clearFocus()
                    }
                )
            )

            // ATTACHED DROPDOWN SUGGESTIONS PANEL
            if (isDropdownOpen) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag(dropdownTestTag)
                ) {
                    if (filteredCustomers.isEmpty()) {
                        // Clean empty state when no matching customers
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isArabic) "لا يوجد عملاء مطابقون" else "No matching customers found",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Scrollable list of available customers
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxDropdownHeight)
                        ) {
                            items(filteredCustomers, key = { it.id }) { customer ->
                                val isSelected = customer.id == selectedCustomerId
                                CustomerDropdownRow(
                                    customer = customer,
                                    isSelected = isSelected,
                                    currency = currency,
                                    showBalance = showBalance,
                                    testTag = "$itemTagPrefix${customer.id}",
                                    onClick = {
                                        onCustomerSelected(customer)
                                        isDropdownOpen = false
                                        focusManager.clearFocus()
                                    }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerDropdownRow(
    customer: CustomerAccount,
    isSelected: Boolean,
    currency: String,
    showBalance: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
            )
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Customer avatar with initial
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = customer.customerName.take(1),
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.customerName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (customer.phone.isNotBlank()) {
                    Text(
                        text = customer.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showBalance) {
                Text(
                    text = String.format(Locale.US, "%,.2f %s", customer.balance, currency),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (customer.balance > 0) MaterialTheme.colorScheme.statusRed else MaterialTheme.colorScheme.statusGreen
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
