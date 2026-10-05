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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.accounting.SupplierBalanceSummary
import com.example.accounting.SupplierLedgerEntry
import com.example.data.db.Purchase
import com.example.data.db.PurchaseReturn
import com.example.data.db.Supplier
import com.example.data.db.SupplierPayment
import com.example.model.AppCurrency
import com.example.model.CartItem
import com.example.model.CustomerAccount
import com.example.model.LanguageMode
import com.example.model.ProductItem
import com.example.model.PurchaseLineRequest
import com.example.model.PurchaseResult
import com.example.model.StoreStrings
import com.example.data.db.Expense
import com.example.data.db.ExpenseCategory
import com.example.data.db.FinancialAccount
import com.example.data.db.PaymentMethod
import com.example.ui.components.ExpensesSection
import com.example.ui.components.SupplierPurchasesSection
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
    suppliers: List<Supplier> = emptyList(),
    supplierPurchases: List<Purchase> = emptyList(),
    supplierPayments: List<SupplierPayment> = emptyList(),
    expenses: List<Expense> = emptyList(),
    expenseCategories: List<ExpenseCategory> = emptyList(),
    financialAccounts: List<FinancialAccount> = emptyList(),
    paymentMethods: List<PaymentMethod> = emptyList(),
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
    onAddSupplier: (name: String, phone: String, address: String?, notes: String?, onComplete: (Result<Supplier>) -> Unit) -> Unit = { _, _, _, _, _ -> },
    onRecordPurchase: (supplierId: String, lines: List<PurchaseLineRequest>, paidAmount: Double, financialAccountId: String?, notes: String?, date: String, onComplete: (Result<PurchaseResult>) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onRecordSupplierPayment: (supplierId: String, amount: Double, date: String, financialAccountId: String?, notes: String?, onComplete: (Result<SupplierPayment>) -> Unit) -> Unit = { _, _, _, _, _, _ -> },
    onRecordPurchaseReturn: ((purchaseId: String, amount: Double, reason: String, date: String, onComplete: (Result<PurchaseReturn>) -> Unit) -> Unit)? = null,
    onRecordExpense: (categoryId: String, amount: Double, financialAccountId: String, paymentMethodId: String?, date: String?, description: String, onComplete: (Result<Expense>) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onAddExpenseCategory: (name: String, description: String?, onComplete: (Result<ExpenseCategory>) -> Unit) -> Unit = { _, _, _ -> },
    onGetSupplierBalance: (suspend (supplierId: String) -> SupplierBalanceSummary)? = null,
    onGetSupplierStatement: (suspend (supplierId: String) -> List<SupplierLedgerEntry>)? = null,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val currency = AppCurrency.SYMBOL
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showCustomerPicker by remember { mutableStateOf(false) }
    var activeSectionTab by remember { mutableIntStateOf(0) }

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
                Column(modifier = Modifier.fillMaxWidth()) {
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = activeSectionTab == 0,
                            onClick = { activeSectionTab = 0 },
                            label = { Text(if (isArabic) "نقطة البيع (السلة)" else "POS Cart") },
                            modifier = Modifier.testTag("tab_purchases_pos")
                        )
                        FilterChip(
                            selected = activeSectionTab == 1,
                            onClick = { activeSectionTab = 1 },
                            label = { Text(if (isArabic) "مشتريات الموردين" else "Suppliers & Purchases") },
                            modifier = Modifier.testTag("tab_purchases_suppliers")
                        )
                        FilterChip(
                            selected = activeSectionTab == 2,
                            onClick = { activeSectionTab = 2 },
                            label = { Text(if (isArabic) "المصروفات" else "Expenses") },
                            modifier = Modifier.testTag("tab_purchases_expenses")
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (activeSectionTab == 0) {
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
        }
    ) { innerPadding ->
        if (activeSectionTab == 1) {
            SupplierPurchasesSection(
                suppliers = suppliers,
                purchases = supplierPurchases,
                supplierPayments = supplierPayments,
                products = products,
                languageMode = languageMode,
                onAddSupplier = onAddSupplier,
                onRecordPurchase = onRecordPurchase,
                onRecordSupplierPayment = onRecordSupplierPayment,
                onGetSupplierBalance = onGetSupplierBalance ?: { SupplierBalanceSummary(it) },
                onGetSupplierStatement = onGetSupplierStatement ?: { emptyList() },
                onRecordPurchaseReturn = onRecordPurchaseReturn,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else if (activeSectionTab == 2) {
            ExpensesSection(
                expenses = expenses,
                expenseCategories = expenseCategories,
                financialAccounts = financialAccounts,
                paymentMethods = paymentMethods,
                languageMode = languageMode,
                onRecordExpense = onRecordExpense,
                onAddExpenseCategory = onAddExpenseCategory,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
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
