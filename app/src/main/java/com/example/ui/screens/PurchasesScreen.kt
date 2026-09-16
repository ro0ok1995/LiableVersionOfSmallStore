package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCurrency
import com.example.model.CartItem
import com.example.model.CustomerAccount
import com.example.model.LanguageMode
import com.example.model.ProductItem
import com.example.model.StoreStrings
import com.example.ui.theme.GeoOutline
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusRedBg
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(
    customer: CustomerAccount? = null,
    allCustomers: List<CustomerAccount> = emptyList(),
    products: List<ProductItem> = emptyList(),
    cart: List<CartItem> = emptyList(),
    searchQuery: String = "",
    isCartExpanded: Boolean = false,
    languageMode: LanguageMode = LanguageMode.ARABIC,
    onBackClick: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onAddToCart: (ProductItem) -> Unit = {},
    onUpdateCartQuantity: (productId: String, delta: Int) -> Unit = { _, _ -> },
    onRemoveFromCart: (productId: String) -> Unit = {},
    onToggleCartExpanded: () -> Unit = {},
    onSelectCustomer: (CustomerAccount) -> Unit = {},
    onClearCustomer: () -> Unit = {},
    onCompleteTransaction: () -> Unit = {},
    onCompleteTransactionWithItems: ((List<CartItem>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val currency = AppCurrency.SYMBOL
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showCustomerPicker by remember { mutableStateOf(false) }

    val totalCartItems = cart.sumOf { it.quantity }
    val totalCartAmount = cart.sumOf { it.product.price * it.quantity }
    val isCheckoutEnabled = customer != null && cart.isNotEmpty()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("purchases_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                        onClick = {
                            focusManager.clearFocus()
                            onBackClick()
                        },
                        modifier = Modifier.testTag("purchases_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isArabic) "رجوع" else "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isArabic) StoreStrings.PURCHASES_AR else StoreStrings.PURCHASES_EN,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("purchases_screen_title")
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = GeoOutlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .animateContentSize()
                ) {
                    AnimatedVisibility(
                        visible = isCartExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isArabic) "محتويات السلة" else "Cart Line Items",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(
                                    onClick = onToggleCartExpanded,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isArabic) StoreStrings.HIDE_CART_AR else StoreStrings.HIDE_CART_EN,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GeoPrimary
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = GeoOutlineVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            if (cart.isEmpty()) {
                                Text(
                                    text = if (isArabic) "السلة فارغة. اضغط على أي منتج لإضافته." else "Cart is empty. Tap any product to add it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                ) {
                                    items(cart, key = { it.product.id }) { item ->
                                        CartLineItemRow(
                                            item = item,
                                            currency = currency,
                                            isArabic = isArabic,
                                            onIncrement = { onUpdateCartQuantity(item.product.id, 1) },
                                            onDecrement = { onUpdateCartQuantity(item.product.id, -1) },
                                            onRemove = { onRemoveFromCart(item.product.id) }
                                        )
                                        HorizontalDivider(
                                            color = GeoOutlineVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Persistent Cart Summary Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { onToggleCartExpanded() }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("cart_summary_bar"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(GeoPrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = GeoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (totalCartItems == 0) {
                                        if (isArabic) "السلة فارغة" else "Empty cart"
                                    } else {
                                        if (isArabic) "$totalCartItems ${StoreStrings.CART_ITEMS_AR}" else "$totalCartItems ${StoreStrings.CART_ITEMS_EN}"
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isCartExpanded) {
                                        if (isArabic) StoreStrings.HIDE_CART_AR else StoreStrings.HIDE_CART_EN
                                    } else {
                                        if (isArabic) StoreStrings.VIEW_CART_AR else StoreStrings.VIEW_CART_EN
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GeoPrimary
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isArabic) StoreStrings.TOTAL_AR else StoreStrings.TOTAL_EN,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2f %s", totalCartAmount, currency),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    ),
                                    color = GeoPrimary,
                                    modifier = Modifier.testTag("cart_total_price")
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isCartExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isCartExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Primary Checkout Button
                    Button(
                        onClick = {
                            if (isCheckoutEnabled) {
                                focusManager.clearFocus()
                                if (onCompleteTransactionWithItems != null) {
                                    onCompleteTransactionWithItems(cart)
                                } else {
                                    onCompleteTransaction()
                                }
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = if (isArabic) "تم فتح المحاسبة للعميل ${customer?.customerName}" else "Opened transaction settlement for ${customer?.customerName}"
                                    )
                                }
                            }
                        },
                        enabled = isCheckoutEnabled,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeoPrimary,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("complete_transaction_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) StoreStrings.COMPLETE_TRANSACTION_AR else StoreStrings.COMPLETE_TRANSACTION_EN,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (!isCheckoutEnabled) {
                        val requirementNote = when {
                            customer == null && cart.isEmpty() -> {
                                if (isArabic) "اختر عميلاً وأضف منتجات لتتمكن من إتمام المعاملة" else "Select a customer and add items to checkout"
                            }
                            customer == null -> {
                                if (isArabic) "يرجى اختيار عميل من الأعلى للمتابعة" else "Please select a customer above to continue"
                            }
                            else -> {
                                if (isArabic) "يرجى إضافة منتج واحد على الأقل إلى السلة" else "Please add at least one product to the cart"
                            }
                        }
                        Text(
                            text = requirementNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 1. SELECT CUSTOMER CONTROL
            CustomerSelectorCard(
                selectedCustomer = customer,
                isArabic = isArabic,
                onSelectClick = { showCustomerPicker = true },
                onChangeClick = { showCustomerPicker = true },
                onClearClick = onClearCustomer
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. SEARCH PRODUCTS FIELD
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = if (isArabic) StoreStrings.SEARCH_PRODUCTS_AR else StoreStrings.SEARCH_PRODUCTS_EN,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = if (isArabic) "بحث" else "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            onSearchQueryChange("")
                            focusManager.clearFocus()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isArabic) "مسح" else "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GeoPrimary,
                    unfocusedBorderColor = GeoOutlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("purchases_search_field")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. PRODUCT GRID
            val filteredProducts = if (searchQuery.isBlank()) {
                products
            } else {
                val q = searchQuery.trim().lowercase()
                products.filter { it.name.lowercase().contains(q) || it.category.lowercase().contains(q) }
            }

            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isArabic) StoreStrings.NO_PRODUCTS_FOUND_AR else StoreStrings.NO_PRODUCTS_FOUND_EN,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("purchases_product_grid")
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        val qtyInCart = cart.find { it.product.id == product.id }?.quantity ?: 0
                        ProductGridCard(
                            product = product,
                            quantityInCart = qtyInCart,
                            currency = currency,
                            onCardClick = { onAddToCart(product) }
                        )
                    }
                }
            }
        }
    }

    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = allCustomers,
            selectedCustomerId = customer?.id,
            isArabic = isArabic,
            onDismiss = { showCustomerPicker = false },
            onSelectCustomer = {
                onSelectCustomer(it)
                showCustomerPicker = false
            }
        )
    }
}

@Composable
private fun CustomerSelectorCard(
    selectedCustomer: CustomerAccount?,
    isArabic: Boolean,
    onSelectClick: () -> Unit,
    onChangeClick: () -> Unit,
    onClearClick: () -> Unit
) {
    if (selectedCustomer == null) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    color = GeoPrimary.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable { onSelectClick() }
                .testTag("customer_selector_empty")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GeoPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = GeoPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isArabic) StoreStrings.SELECT_CUSTOMER_AR else StoreStrings.SELECT_CUSTOMER_EN,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "*",
                                    color = StatusRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (isArabic) StoreStrings.SELECT_CUSTOMER_REQUIRED_AR else StoreStrings.SELECT_CUSTOMER_REQUIRED_EN,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onSelectClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("choose_customer_button")
                    ) {
                        Text(
                            text = if (isArabic) "اختيار" else "Choose",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = GeoPrimary
                        )
                    }
                }
            }
        }
    } else {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = GeoOutlineVariant, shape = RoundedCornerShape(14.dp))
                .testTag("customer_selector_selected")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(GeoPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedCustomer.customerName.take(1),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = GeoPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = selectedCustomer.customerName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = selectedCustomer.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (selectedCustomer.balance > 0) {
                            Text(
                                text = if (isArabic) "مدين بـ ${AppCurrency.formatAmountWithDecimals(selectedCustomer.balance, isArabic = true)}" else "Owes ${AppCurrency.formatAmountWithDecimals(selectedCustomer.balance, isArabic = false)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = StatusRed
                            )
                        } else {
                            Text(
                                text = if (isArabic) "خالص (لا يوجد ديون)" else "Settled",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = StatusGreen
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onChangeClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("change_customer_button")
                    ) {
                        Text(
                            text = if (isArabic) StoreStrings.CHANGE_CUSTOMER_AR else StoreStrings.CHANGE_CUSTOMER_EN,
                            style = MaterialTheme.typography.labelMedium,
                            color = GeoPrimary
                        )
                    }
                    IconButton(
                        onClick = onClearClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (isArabic) "مسح العميل" else "Clear customer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductGridCard(
    product: ProductItem,
    quantityInCart: Int,
    currency: String,
    onCardClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (quantityInCart > 0) 1.5.dp else 1.dp,
                color = if (quantityInCart > 0) GeoPrimary else GeoOutlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onCardClick() }
            .testTag("product_card_${product.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (quantityInCart > 0) GeoPrimary.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = if (quantityInCart > 0) GeoPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(36.dp)
                )
                if (quantityInCart > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GeoPrimary)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "× $quantityInCart",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "%.2f %s", product.price, currency),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = GeoPrimary
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(GeoPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = GeoPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CartLineItemRow(
    item: CartItem,
    currency: String,
    isArabic: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit
) {
    val lineTotal = item.product.price * item.quantity
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.product.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = String.format(Locale.US, "%.2f %s × %d = %.2f %s", item.product.price, currency, item.quantity, lineTotal, currency),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onDecrement() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = "${item.quantity}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(GeoPrimary)
                    .clickable { onIncrement() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = if (isArabic) "حذف العنصر" else "Remove item",
                    tint = StatusRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CustomerPickerDialog(
    customers: List<CustomerAccount>,
    selectedCustomerId: String?,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onSelectCustomer: (CustomerAccount) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCustomers = remember(customers, searchQuery) {
        if (searchQuery.isBlank()) customers
        else {
            val q = searchQuery.trim().lowercase()
            customers.filter { it.customerName.lowercase().contains(q) || it.phone.contains(q) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isArabic) StoreStrings.SELECT_CUSTOMER_AR else StoreStrings.SELECT_CUSTOMER_EN,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Search field inside dialog
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = if (isArabic) StoreStrings.SEARCH_CUSTOMER_AR else StoreStrings.SEARCH_CUSTOMER_EN,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = GeoPrimary,
                        unfocusedBorderColor = GeoOutline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_customer_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredCustomers.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isArabic) "لا يوجد عملاء مطابقون" else "No matching customers found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(filteredCustomers, key = { it.id }) { customer ->
                            val isSelected = customer.id == selectedCustomerId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) GeoPrimary.copy(alpha = 0.12f)
                                        else Color.Transparent
                                    )
                                    .clickable { onSelectCustomer(customer) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = customer.customerName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) GeoPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = customer.phone,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = GeoPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = GeoOutlineVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = if (isArabic) "إلغاء" else "Cancel",
                    color = GeoPrimary
                )
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
