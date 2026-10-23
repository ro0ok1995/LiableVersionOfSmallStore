package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.accounting.AccountingDecimal
import com.example.accounting.SupplierBalanceSummary
import com.example.data.db.FinancialAccount
import com.example.data.db.Purchase
import com.example.data.db.PurchaseLine
import com.example.data.db.Supplier
import com.example.data.db.SupplierCatalogItemEntity
import com.example.model.LanguageMode
import com.example.model.ProductItem
import com.example.model.PurchaseLineRequest
import com.example.model.PurchaseReturnLineRequest
import com.example.model.SupplierCartItem
import com.example.ui.components.SupplierInitialAction
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// Keeps the in-progress supplier draft while the user temporarily visits Data Center to add an item.
private object SupplierOperationsDraftState {
    var selectedSupplierId: String? = null
    var cart: List<SupplierCartItem> = emptyList()
    var itemSearchQuery: String = ""
    var showManagementDialog: Boolean = false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierOperationsScreen(
    suppliers: List<Supplier>,
    products: List<ProductItem>,
    supplierCatalogItems: List<SupplierCatalogItemEntity>,
    supplierPurchases: List<Purchase>,
    financialAccounts: List<FinancialAccount>,
    languageMode: LanguageMode,
    initialSupplierId: String? = null,
    initialAction: SupplierInitialAction = SupplierInitialAction.NONE,
    onGetSupplierBalance: suspend (String) -> SupplierBalanceSummary,
    onGetPurchaseLines: suspend (String) -> List<PurchaseLine>,
    onGetRemainingPurchaseReturnQuantities: suspend (String) -> Map<String, Int>,
    onLinkItemToSupplier: (supplierId: String, productId: String, lastCost: Double) -> Unit,
    onUnlinkItemFromSupplier: (supplierId: String, productId: String, onComplete: (Result<Unit>) -> Unit) -> Unit,
    onNavigateToAddCatalogItem: (isCatalogItem: Boolean) -> Unit,
    onUpdateProductCost: (supplierId: String, productId: String, newCost: Double) -> Unit,
    onRecordPurchase: (
        supplierId: String,
        lines: List<PurchaseLineRequest>,
        paidAmount: Double,
        financialAccountId: String?,
        notes: String?,
        purchaseDate: String,
        onComplete: (Result<Unit>) -> Unit
    ) -> Unit,
    onRecordSupplierPayment: (
        supplierId: String,
        amount: Double,
        paymentDate: String,
        financialAccountId: String?,
        notes: String?,
        onComplete: (Result<Unit>) -> Unit
    ) -> Unit,
    onRecordSupplierReceipt: (
        supplierId: String,
        amount: Double,
        receiptDate: String,
        financialAccountId: String,
        notes: String?,
        onComplete: (Result<Unit>) -> Unit
    ) -> Unit,
    onRecordPurchaseReturn: (
        purchaseId: String,
        returnLines: List<PurchaseReturnLineRequest>,
        reason: String,
        returnDate: String,
        settlementMode: String,
        financialAccountId: String?,
        onComplete: (Result<Unit>) -> Unit
    ) -> Unit
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val scope = rememberCoroutineScope()
    val activeSuppliers = remember(suppliers) { suppliers.filter { !it.isArchived } }

    var selectedSupplier by remember(activeSuppliers, initialSupplierId) {
        mutableStateOf(
            activeSuppliers.firstOrNull { it.id == SupplierOperationsDraftState.selectedSupplierId }
                ?: activeSuppliers.firstOrNull { it.id == initialSupplierId }
                ?: activeSuppliers.firstOrNull()
        )
    }

    var balancesMap by remember { mutableStateOf<Map<String, SupplierBalanceSummary>>(emptyMap()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeSuppliers, supplierPurchases, refreshTrigger) {
        val map = mutableMapOf<String, SupplierBalanceSummary>()
        for (sup in activeSuppliers) {
            map[sup.id] = onGetSupplierBalance(sup.id)
        }
        balancesMap = map
    }

    var supplierSearchQuery by remember { mutableStateOf("") }
    var isSupplierDropdownExpanded by remember { mutableStateOf(false) }
    var pendingSupplierChange by remember { mutableStateOf<Supplier?>(null) }

    var itemSearchQuery by remember { mutableStateOf(SupplierOperationsDraftState.itemSearchQuery) }
    var isItemSearchExpanded by remember { mutableStateOf(true) }
    var showItemSuggestions by remember { mutableStateOf(false) }

    var supplierCart by remember(selectedSupplier?.id) {
        mutableStateOf(if (SupplierOperationsDraftState.selectedSupplierId == selectedSupplier?.id) SupplierOperationsDraftState.cart else emptyList())
    }
    var isCartExpanded by remember { mutableStateOf(false) }

    var expandedCardItem by remember { mutableStateOf<ProductItem?>(null) }
    var showPaymentDialog by remember { mutableStateOf(initialAction == SupplierInitialAction.PAYMENT) }
    var showReturnDialog by remember { mutableStateOf(initialAction == SupplierInitialAction.RETURN) }
    var showLinkOrAddItemDialog by remember { mutableStateOf(SupplierOperationsDraftState.showManagementDialog) }
    var showSettlementSheet by remember { mutableStateOf(false) }
    var showQuantitySchemaWarning by remember { mutableStateOf(false) }
    var operationErrorMessage by remember { mutableStateOf<String?>(null) }

    SideEffect {
        SupplierOperationsDraftState.selectedSupplierId = selectedSupplier?.id
        SupplierOperationsDraftState.cart = supplierCart
        SupplierOperationsDraftState.itemSearchQuery = itemSearchQuery
        SupplierOperationsDraftState.showManagementDialog = showLinkOrAddItemDialog
    }

    LaunchedEffect(initialAction) {
        if (initialAction == SupplierInitialAction.PAYMENT) showPaymentDialog = true
        if (initialAction == SupplierInitialAction.RETURN) showReturnDialog = true
    }

    val supplierLinkedMap = remember(selectedSupplier, supplierCatalogItems) {
        val supId = selectedSupplier?.id ?: return@remember emptyMap<String, Double>()
        supplierCatalogItems
            .filter { it.supplierId == supId }
            .associate { it.productId to it.lastUnitCost }
    }

    val supplierItems = remember(products, supplierLinkedMap, itemSearchQuery, selectedSupplier) {
        if (selectedSupplier == null) return@remember emptyList<ProductItem>()
        val activeProducts = products.filter { !it.isArchived }
        val baseList = activeProducts.filter { it.id in supplierLinkedMap.keys }
        if (itemSearchQuery.isBlank()) {
            baseList
        } else {
            val q = itemSearchQuery.trim().lowercase()
            baseList.filter { it.name.lowercase().contains(q) || it.category.lowercase().contains(q) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = if (isSupplierDropdownExpanded) supplierSearchQuery else (selectedSupplier?.name ?: ""),
                    onValueChange = {
                        supplierSearchQuery = it
                        isSupplierDropdownExpanded = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSupplierDropdownExpanded = !isSupplierDropdownExpanded },
                    placeholder = {
                        Text(if (isArabic) "ابحث أو اختر المورد..." else "Search or select supplier...")
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            supplierSearchQuery = ""
                            isSupplierDropdownExpanded = !isSupplierDropdownExpanded
                        }) {
                            Icon(
                                imageVector = if (isSupplierDropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isArabic) "قائمة الموردين" else "Suppliers list"
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                DropdownMenu(
                    expanded = isSupplierDropdownExpanded,
                    onDismissRequest = {
                        isSupplierDropdownExpanded = false
                        supplierSearchQuery = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .heightIn(max = 300.dp)
                ) {
                    val filteredSuppliers = if (supplierSearchQuery.isBlank()) {
                        activeSuppliers
                    } else {
                        val q = supplierSearchQuery.trim().lowercase()
                        activeSuppliers.filter {
                            it.name.lowercase().contains(q) || it.phone.contains(q)
                        }
                    }

                    if (filteredSuppliers.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(if (isArabic) "لا يوجد مورد مطابق" else "No matching supplier") },
                            onClick = { isSupplierDropdownExpanded = false }
                        )
                    } else {
                        filteredSuppliers.forEach { sup ->
                            val bal = balancesMap[sup.id]?.balance ?: 0.0
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sup.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (sup.phone.isNotBlank()) {
                                                Text(
                                                    text = sup.phone,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        SupplierBalanceBadge(balance = bal, isArabic = isArabic)
                                    }
                                },
                                onClick = {
                                    selectedSupplier = sup
                                    isSupplierDropdownExpanded = false
                                    supplierSearchQuery = ""
                                }
                            )
                        }
                    }
                }
            }

            selectedSupplier?.let { sup ->
                val currentBal = balancesMap[sup.id]?.balance ?: 0.0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sup.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (sup.phone.isNotBlank()) {
                                    Text(
                                        text = sup.phone,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            SupplierBalanceBadge(balance = currentBal, isArabic = isArabic, isLarge = true)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showPaymentDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isArabic) "تسوية حساب المورد" else "Settle Supplier Account")
                            }

                            OutlinedButton(
                                onClick = { showReturnDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AssignmentReturn, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isArabic) "مرتجع مشتريات" else "Purchase Return")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = itemSearchQuery,
                        onValueChange = { itemSearchQuery = it; showItemSuggestions = it.isNotBlank(); isItemSearchExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(if (isArabic) "ابحث في المنتجات والأصناف والمواد..." else "Search products, items and materials...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (itemSearchQuery.isNotEmpty()) IconButton(onClick = { itemSearchQuery = ""; showItemSuggestions = false; isItemSearchExpanded = true }) { Icon(Icons.Default.Clear, contentDescription = if (isArabic) "مسح البحث والتحديد" else "Clear search and selection") }
                                IconButton(onClick = { showItemSuggestions = !showItemSuggestions }) {
                                    Icon(if (showItemSuggestions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = if (isArabic) "فتح أو إغلاق الاقتراحات" else "Toggle suggestions")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownMenu(
                        expanded = showItemSuggestions && supplierItems.isNotEmpty(),
                        onDismissRequest = { showItemSuggestions = false },
                        modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 280.dp)
                    ) {
                        supplierItems.take(12).forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Column { Text(suggestion.name, fontWeight = FontWeight.Medium); Text(suggestion.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                                onClick = { itemSearchQuery = suggestion.name; showItemSuggestions = false; isItemSearchExpanded = true }
                            )
                        }
                    }
                }
                FilledTonalIconButton(onClick = { showLinkOrAddItemDialog = true }, modifier = Modifier.size(52.dp), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.AddLink, contentDescription = if (isArabic) "إدارة منتجات وأصناف المورد" else "Manage supplier products and items")
                }
            }

            if (!isItemSearchExpanded) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (isArabic) "قائمة الأصناف مخفية — اضغط السهم لعرضها" else "Item list hidden — tap the arrow to show it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (supplierItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        val emptyMessage = when {
                            selectedSupplier == null ->
                                if (isArabic) "اختر مورداً لعرض أو إضافة أصناف الكتالوج" else "Select a supplier to view or add catalog items"
                            itemSearchQuery.isNotBlank() ->
                                if (isArabic) "لا توجد منتجات أو أصناف مطابقة لبحثك" else "No matching products or items found"
                            else ->
                                if (isArabic) "لا توجد أصناف مرتبطة بهذا المورد حتى الآن\nاضغط على زر الربط (+) لإضافة صنف" else "No items linked to this supplier yet\nTap the link button (+) to add items"
                        }
                        Text(
                            text = emptyMessage,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(supplierItems, key = { it.id }) { item ->
                        val lastWholesaleCost = supplierLinkedMap[item.id] ?: item.costPrice
                        val cartEntry = supplierCart.firstOrNull { it.itemId == item.id }
                        SupplierCatalogCard(
                            item = item,
                            wholesaleCost = lastWholesaleCost,
                            selectedQuantity = cartEntry?.quantity,
                            isArabic = isArabic,
                            onClick = { expandedCardItem = item }
                        )
                    }
                }
            }
        }

        SupplierBottomCartBar(
            cart = supplierCart,
            isExpanded = isCartExpanded,
            isArabic = isArabic,
            onToggleExpand = { isCartExpanded = !isCartExpanded },
            onEditCartItem = { cartItem ->
                products.firstOrNull { it.id == cartItem.itemId }?.let { expandedCardItem = it }
            },
            onRemoveFromCart = { itemId ->
                supplierCart = supplierCart.filterNot { it.itemId == itemId }
            },
            onChangeQuantity = { itemId, delta ->
                supplierCart = supplierCart.map { item ->
                    if (item.itemId == itemId) item.copy(quantity = (item.quantity + delta).coerceAtLeast(1.0)) else item
                }
            },
            onSetQuantity = { itemId, quantity ->
                if (quantity.isFinite() && quantity > 0.0) {
                    supplierCart = supplierCart.map { item -> if (item.itemId == itemId) item.copy(quantity = quantity) else item }
                }
            },
            onCompleteTransaction = {
                if (supplierCart.isNotEmpty() && selectedSupplier != null) {
                    showSettlementSheet = true
                }
            }
        )
    }

    expandedCardItem?.let { item ->
        val existingInCart = supplierCart.firstOrNull { it.itemId == item.id }
        val defaultCost = existingInCart?.unitCost ?: supplierLinkedMap[item.id] ?: item.costPrice
        val defaultQty = existingInCart?.quantity ?: 1.0

        ExpandedSupplierItemDialog(
            item = item,
            initialQuantity = defaultQty,
            initialUnitCost = defaultCost,
            isArabic = isArabic,
            onDismiss = { expandedCardItem = null },
            onConfirm = { confirmedQty, confirmedCost ->
                // Cost edits stay in the draft cart. Persist the stock cost only after invoice save.
                val newCartItem = SupplierCartItem(
                    itemId = item.id,
                    name = item.name,
                    unit = item.unit,
                    quantity = confirmedQty,
                    unitCost = confirmedCost,
                    imageUri = item.imageUri,
                    isRawMaterial = item.isRawMaterial
                )
                supplierCart = if (supplierCart.any { it.itemId == item.id }) {
                    supplierCart.map { if (it.itemId == item.id) newCartItem else it }
                } else {
                    supplierCart + newCartItem
                }
                expandedCardItem = null
            }
        )
    }

    if (showReturnDialog && selectedSupplier != null) {
        SupplierPurchaseReturnDialog(
            supplier = selectedSupplier!!,
            purchases = supplierPurchases.filter { it.supplierId == selectedSupplier!!.id && it.status == "ACTIVE" },
            financialAccounts = financialAccounts.filter { it.isActive },
            isArabic = isArabic,
            onGetPurchaseLines = onGetPurchaseLines,
            onGetRemainingQuantities = onGetRemainingPurchaseReturnQuantities,
            onDismiss = { showReturnDialog = false },
            onConfirm = { purchaseId, returnLines, reason, date, mode, accountId, onComplete ->
                onRecordPurchaseReturn(purchaseId, returnLines, reason, date, mode, accountId, onComplete)
            },
            onSuccess = { refreshTrigger++ }
        )
    }

    pendingSupplierChange?.let { targetSupplier ->
        AlertDialog(
            onDismissRequest = { pendingSupplierChange = null },
            title = { Text(if (isArabic) "تغيير المورد؟" else "Change supplier?") },
            text = { Text(if (isArabic) "توجد أصناف في سلة التوريد. تغيير المورد سيُفرغ السلة الحالية. هل تريد المتابعة؟" else "The supplier cart contains items. Changing supplier will clear the current draft. Continue?") },
            confirmButton = {
                Button(onClick = {
                    selectedSupplier = targetSupplier
                    supplierSearchQuery = ""
                    itemSearchQuery = ""
                    showItemSuggestions = false
                    supplierCart = emptyList()
                    isCartExpanded = false
                    pendingSupplierChange = null
                }) { Text(if (isArabic) "تغيير المورد وإفراغ السلة" else "Change supplier and clear cart") }
            },
            dismissButton = { TextButton(onClick = { pendingSupplierChange = null }) { Text(if (isArabic) "إلغاء" else "Cancel") } }
        )
    }

    if (showSettlementSheet && selectedSupplier != null) {
        val totalOrderAmount = supplierCart.sumOf { it.subtotal }
        SupplierSettlementSheet(
            supplier = selectedSupplier!!,
            totalAmount = totalOrderAmount,
            cartItems = supplierCart,
            currentSupplierBalance = balancesMap[selectedSupplier!!.id]?.balance ?: 0.0,
            financialAccounts = financialAccounts.filter { it.isActive },
            isArabic = isArabic,
            onDismiss = { showSettlementSheet = false },
            onConfirmSettlement = { paidAmt, accountId, notes, purchaseDate ->
                // The current Room purchase-line schema stores quantity as Int. Never round a fractional draft silently.
                if (supplierCart.any { kotlin.math.abs(it.quantity - kotlin.math.round(it.quantity)) > 0.000001 }) {
                    showQuantitySchemaWarning = true
                } else {
                    val requests = supplierCart.map { cartItem ->
                        val qtyWhole = cartItem.quantity.toInt()
                        PurchaseLineRequest(
                            productId = cartItem.itemId,
                            productNameSnapshot = "${cartItem.name} (${formatQuantity(cartItem.quantity)} ${cartItem.unit})",
                            quantity = qtyWhole,
                            unitCost = AccountingDecimal.money(cartItem.unitCost)
                        )
                    }
                    onRecordPurchase(
                        selectedSupplier!!.id,
                        requests,
                        paidAmt,
                        accountId,
                        notes,
                        purchaseDate
                    ) { result ->
                        if (result.isSuccess) {
                            supplierCart.forEach { item ->
                                onLinkItemToSupplier(selectedSupplier!!.id, item.itemId, item.unitCost)
                            }
                            supplierCart = emptyList()
                            isCartExpanded = false
                            showSettlementSheet = false
                            refreshTrigger++
                        } else {
                            operationErrorMessage = result.exceptionOrNull()?.message ?: if (isArabic) "فشل حفظ فاتورة الشراء." else "Failed to save purchase invoice."
                        }
                    }
                }
            }
        )
    }

    operationErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { operationErrorMessage = null },
            title = { Text(if (isArabic) "تعذر حفظ العملية" else "Operation could not be saved") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { operationErrorMessage = null }) { Text(if (isArabic) "حسنًا" else "OK") } }
        )
    }

    if (showQuantitySchemaWarning) {
        AlertDialog(
            onDismissRequest = { showQuantitySchemaWarning = false },
            title = { Text(if (isArabic) "تعذر حفظ الكمية العشرية بأمان" else "Fractional quantity cannot be saved safely") },
            text = { Text(if (isArabic) "المصدر الحالي يخزّن كمية بند فاتورة الشراء كعدد صحيح. لم يتم تقريب الكمية أو حفظ الفاتورة حتى لا تتغير الكمية والتكلفة بصمت. يلزم ترحيل آمن لبيانات بنود الشراء والمرتجعات وحركات المخزون إلى دقة عشرية قبل تفعيل الكميات الكسرية." else "The current database schema stores purchase-line quantities as whole numbers. No rounding or save was performed. A safe decimal migration of purchase lines, returns, and stock movements is required first.") },
            confirmButton = { TextButton(onClick = { showQuantitySchemaWarning = false }) { Text(if (isArabic) "حسنًا" else "OK") } }
        )
    }

    if (showPaymentDialog && selectedSupplier != null) {
        SupplierQuickPaymentDialog(
            supplier = selectedSupplier!!,
            currentBalance = balancesMap[selectedSupplier!!.id]?.balance ?: 0.0,
            financialAccounts = financialAccounts.filter { it.isActive },
            isArabic = isArabic,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { isCollection, amount, accountId, notes, operationDate, onComplete ->
                if (isCollection) {
                    val accId = accountId
                    if (accId == null) {
                        onComplete(Result.failure(IllegalArgumentException(if (isArabic) "اختر حسابًا ماليًا نشطًا." else "Select an active financial account.")))
                    } else {
                        onRecordSupplierReceipt(selectedSupplier!!.id, amount, operationDate, accId, notes) { result ->
                            onComplete(result)
                            if (result.isSuccess) {
                                showPaymentDialog = false
                                refreshTrigger++
                            }
                        }
                    }
                } else {
                    onRecordSupplierPayment(selectedSupplier!!.id, amount, operationDate, accountId, notes) { result ->
                        onComplete(result)
                        if (result.isSuccess) {
                            showPaymentDialog = false
                            refreshTrigger++
                        }
                    }
                }
            }
        )
    }

    if (showLinkOrAddItemDialog && selectedSupplier != null) {
        LinkOrCreateSupplierItemDialog(
            allProducts = products.filter { !it.isArchived },
            linkedProductIds = supplierLinkedMap.keys,
            isArabic = isArabic,
            onDismiss = { showLinkOrAddItemDialog = false },
            onLinkExisting = { selectedProducts ->
                selectedProducts.forEach { prod -> onLinkItemToSupplier(selectedSupplier!!.id, prod.id, prod.costPrice) }
                refreshTrigger++
            },
            onUnlinkExisting = { productId ->
                onUnlinkItemFromSupplier(selectedSupplier!!.id, productId) { result ->
                    if (result.isFailure) operationErrorMessage = result.exceptionOrNull()?.message ?: if (isArabic) "تعذر إزالة الصنف من المورد." else "Could not unlink item from supplier."
                    refreshTrigger++
                }
            },
            onRequestAddNew = { isCatalogItem -> onNavigateToAddCatalogItem(isCatalogItem) }
        )
    }
}

@Composable
private fun SupplierBalanceBadge(
    balance: Double,
    isArabic: Boolean,
    isLarge: Boolean = false
) {
    val isPayable = balance > 0.001 
    val isOverpaid = balance < -0.001 
    val badgeColor = when {
        isPayable -> MaterialTheme.colorScheme.errorContainer
        isOverpaid -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        isPayable -> MaterialTheme.colorScheme.onErrorContainer
        isOverpaid -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when {
        isPayable -> if (isArabic) "له: ${String.format(Locale.US, "%.2f", balance)}" else "Credit: ${String.format(Locale.US, "%.2f", balance)}"
        isOverpaid -> if (isArabic) "عليه: ${String.format(Locale.US, "%.2f", abs(balance))}" else "Debit: ${String.format(Locale.US, "%.2f", abs(balance))}"
        else -> if (isArabic) "متزن (0.00)" else "Settled (0.00)"
    }

    Surface(
        color = badgeColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = if (isLarge) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SupplierCatalogCard(
    item: ProductItem,
    wholesaleCost: Double,
    selectedQuantity: Double?,
    isArabic: Boolean,
    onClick: () -> Unit
) {
    val isSelected = selectedQuantity != null && selectedQuantity > 0.0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!item.imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = item.imageUri,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (item.isRawMaterial) Icons.Default.Diamond else Icons.Default.Inventory,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = if (isArabic) "الوحدة: ${item.unit}" else "Unit: ${item.unit}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = if (isArabic) "سعر الجملة: ${String.format(Locale.US, "%.2f", wholesaleCost)}"
                else "Cost: ${String.format(Locale.US, "%.2f", wholesaleCost)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isSelected) {
                        if (isArabic) "الكمية المحددة: ${formatQuantity(selectedQuantity!!)} ${item.unit}"
                        else "Selected: ${formatQuantity(selectedQuantity!!)} ${item.unit}"
                    } else {
                        if (isArabic) "اضغط لتحديد الكمية" else "Tap to set quantity"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpandedSupplierItemDialog(
    item: ProductItem,
    initialQuantity: Double,
    initialUnitCost: Double,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Double, unitCost: Double) -> Unit
) {
    var quantityText by remember { mutableStateOf(formatQuantity(initialQuantity)) }
    var costText by remember { mutableStateOf(String.format(Locale.US, "%.2f", initialUnitCost)) }

    val parsedQty = quantityText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val parsedCost = costText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val totalLineBalance = if (parsedQty > 0 && parsedCost >= 0) {
        AccountingDecimal.multiplyToMoney(parsedCost, parsedQty)
    } else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!item.imageUri.isNullOrBlank()) {
                        AsyncImage(
                            model = item.imageUri,
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = if (item.isRawMaterial) Icons.Default.Diamond else Icons.Default.Inventory,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text(if (isArabic) "الكمية" else "Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = item.unit,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(if (isArabic) "الوحدة" else "Unit") },
                        singleLine = true,
                        modifier = Modifier.width(110.dp)
                    )
                }

                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text(if (isArabic) "سعر الشراء للوحدة" else "Unit Purchase Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "المجموع الكلي للصنف:" else "Item Total:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f", totalLineBalance),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedQty > 0.0 && parsedCost > 0.0) {
                        onConfirm(parsedQty, parsedCost)
                    }
                },
                enabled = parsedQty > 0.0 && parsedCost > 0.0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isArabic) "تأكيد" else "Confirm")
            }
        }
    )
}

@Composable
private fun SupplierBottomCartBar(
    cart: List<SupplierCartItem>,
    isExpanded: Boolean,
    isArabic: Boolean,
    onToggleExpand: () -> Unit,
    onEditCartItem: (SupplierCartItem) -> Unit,
    onRemoveFromCart: (String) -> Unit,
    onChangeQuantity: (String, Double) -> Unit,
    onSetQuantity: (String, Double) -> Unit,
    onCompleteTransaction: () -> Unit
) {
    val totalAmount = cart.sumOf { it.subtotal }
    var editingItem by remember { mutableStateOf<SupplierCartItem?>(null) }
    var quantityText by remember(editingItem?.itemId) { mutableStateOf(editingItem?.let { formatQuantity(it.quantity) } ?: "") }
    val parsedQuantity = quantityText.replace(',', '.').toDoubleOrNull()

    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { if (cart.isNotEmpty()) onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BadgedBox(badge = { Badge { Text("${cart.size}") } }) { Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    Text(if (isArabic) "سلة توريد المورد" else "Supplier Cart", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(String.format(Locale.US, "%.2f", totalAmount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Icon(if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess, contentDescription = null)
                }
            }
            AnimatedVisibility(visible = isExpanded && cart.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(cart, key = { it.itemId }) { item ->
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f).clickable { onEditCartItem(item) }) {
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${String.format(Locale.US, "%.2f", item.unitCost)} × ${formatQuantity(item.quantity)} ${item.unit} = ${String.format(Locale.US, "%.2f", item.subtotal)} ₪", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(onClick = { onChangeQuantity(item.itemId, -1.0) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Remove, contentDescription = if (isArabic) "إنقاص الكمية" else "Decrease quantity") }
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.widthIn(min = 76.dp).clickable { editingItem = item; quantityText = formatQuantity(item.quantity) }
                                    ) {
                                        Text(formatQuantity(item.quantity), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                                    }
                                    IconButton(onClick = { onChangeQuantity(item.itemId, 1.0) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Add, contentDescription = if (isArabic) "زيادة الكمية" else "Increase quantity") }
                                    IconButton(onClick = { onRemoveFromCart(item.itemId) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.DeleteOutline, contentDescription = if (isArabic) "حذف" else "Remove", tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
            Button(onClick = onCompleteTransaction, enabled = cart.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (isArabic) "إتمام المعاملة" else "Complete Transaction", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }

    editingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text(if (isArabic) "تعديل كمية ${item.name}" else "Edit quantity: ${item.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = quantityText, onValueChange = { quantityText = it }, label = { Text(if (isArabic) "الكمية" else "Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    if (parsedQuantity == null || !parsedQuantity.isFinite() || parsedQuantity <= 0.0) Text(if (isArabic) "أدخل كمية أكبر من صفر." else "Enter a quantity greater than zero.", color = MaterialTheme.colorScheme.error)
                    Text(if (isArabic) "ملاحظة: الكميات الكسرية قد لا تُحفظ في الفاتورة وفق مخطط قاعدة البيانات الحالي." else "Note: fractional quantities may not be saveable with the current purchase schema.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { Button(enabled = parsedQuantity != null && parsedQuantity.isFinite() && parsedQuantity > 0.0, onClick = { onSetQuantity(item.itemId, parsedQuantity!!); editingItem = null }) { Text(if (isArabic) "حفظ الكمية" else "Save quantity") } },
            dismissButton = { TextButton(onClick = { editingItem = null }) { Text(if (isArabic) "إلغاء" else "Cancel") } }
        )
    }
}

private fun isValidSupplierDate(value: String): Boolean {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        parser.parse(value) != null
    } catch (_: Exception) {
        false
    }
}

private fun formatQuantity(qty: Double): String {
    return if (qty % 1.0 == 0.0) {
        qty.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", qty).trimEnd('0').trimEnd('.')
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierSettlementSheet(
    supplier: Supplier,
    totalAmount: Double,
    cartItems: List<SupplierCartItem>,
    currentSupplierBalance: Double,
    financialAccounts: List<FinancialAccount>,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onConfirmSettlement: (paidAmt: Double, accountId: String?, notes: String, purchaseDate: String) -> Unit
) {
    var paymentMode by remember { mutableStateOf("FULL") }
    var paidAmountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", totalAmount)) }
    var selectedAccount by remember { mutableStateOf(financialAccounts.firstOrNull { it.isActive }?.id) }
    var notesText by remember { mutableStateOf("") }
    var purchaseDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val parsedPaidAmount = paidAmountText.replace(',', '.').toDoubleOrNull()
    val validPurchaseDate = isValidSupplierDate(purchaseDate)
    val paid = parsedPaidAmount?.takeIf { it.isFinite() } ?: 0.0
    val remaining = (totalAmount - paid).coerceAtLeast(0.0)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (isArabic) "تسجيل معاملة المورد" else "Record Supplier Transaction", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (isArabic) "إجمالي المعاملة" else "Transaction total", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₪ ${String.format(Locale.US, "%.2f", totalAmount)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text(if (isArabic) "المورد: ${supplier.name}" else "Supplier: ${supplier.name}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(if (isArabic) "المنتجات المحددة (${cartItems.size})" else "Selected items (${cartItems.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cartItems.forEach { item ->
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text("${formatQuantity(item.quantity)} ${item.unit} × ${String.format(Locale.US, "%.2f", item.unitCost)} ₪", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text("₪ ${String.format(Locale.US, "%.2f", item.subtotal)}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text(if (isArabic) "اختر طريقة تسديد فاتورة المورد" else "Choose supplier invoice payment type", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = paymentMode == "PARTIAL", onClick = { paymentMode = "PARTIAL"; paidAmountText = "0.00" }, label = { Text(if (isArabic) "جزئي" else "Partial") }, modifier = Modifier.weight(1f))
                FilterChip(selected = paymentMode == "FULL", onClick = { paymentMode = "FULL"; paidAmountText = String.format(Locale.US, "%.2f", totalAmount) }, label = { Text(if (isArabic) "كلي" else "Full") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = paidAmountText, onValueChange = { if (paymentMode != "FULL") paidAmountText = it }, readOnly = paymentMode == "FULL", label = { Text(if (isArabic) "المبلغ المدفوع للمورد" else "Amount paid to supplier") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Text("₪") })
            if (paid > totalAmount + 0.001 || paid < 0.0) Text(if (isArabic) "المبلغ المدفوع يجب أن يكون بين صفر وإجمالي الفاتورة." else "Paid amount must be between zero and invoice total.", color = MaterialTheme.colorScheme.error)
            if (paid > 0.001) {
                Text(if (isArabic) "إيداع المبلغ في الحساب المالي" else "Payment account", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (financialAccounts.isEmpty()) Text(if (isArabic) "لا يوجد حساب مالي نشط. أضف حسابًا قبل تسجيل الدفع." else "No active financial account is available.", color = MaterialTheme.colorScheme.error)
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(financialAccounts) { acc -> FilterChip(selected = selectedAccount == acc.id, onClick = { selectedAccount = acc.id }, label = { Text(acc.name) }) } }
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryAmountRow(if (isArabic) "إجمالي الفاتورة" else "Invoice total", totalAmount)
                    SummaryAmountRow(if (isArabic) "المبلغ المدفوع" else "Paid amount", paid)
                    HorizontalDivider()
                    SummaryAmountRow(if (isArabic) "المتبقي على المحل للمورد" else "Remaining payable", remaining, MaterialTheme.colorScheme.error)
                    Text(if (isArabic) "الرصيد الحالي للمورد: ${String.format(Locale.US, "%.2f", currentSupplierBalance)} ₪" else "Current supplier balance: ${String.format(Locale.US, "%.2f", currentSupplierBalance)} ₪", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (isArabic) "الرصيد المتوقع بعد الفاتورة: ${String.format(Locale.US, "%.2f", currentSupplierBalance + totalAmount - paid)} ₪" else "Expected balance after invoice: ${String.format(Locale.US, "%.2f", currentSupplierBalance + totalAmount - paid)} ₪", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(value = purchaseDate, onValueChange = { purchaseDate = it }, label = { Text(if (isArabic) "تاريخ الفاتورة" else "Invoice date") }, modifier = Modifier.fillMaxWidth(), singleLine = true, trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarMonth, contentDescription = null) } })
            Text(if (isArabic) "تاريخ الإدخال يسجله النظام تلقائيًا بصورة منفصلة." else "Entry timestamp is recorded separately by the system.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = notesText, onValueChange = { notesText = it }, label = { Text(if (isArabic) "ملاحظات الفاتورة (اختياري)" else "Invoice notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (showDatePicker) DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { purchaseDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) }; showDatePicker = false }) { Text(if (isArabic) "اختيار" else "Select") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(if (isArabic) "إلغاء" else "Cancel") } }) { DatePicker(state = datePickerState) }
            Button(enabled = parsedPaidAmount != null && parsedPaidAmount.isFinite() && paid >= 0.0 && paid <= totalAmount + 0.001 && validPurchaseDate && (paid <= 0.001 || selectedAccount != null), onClick = { onConfirmSettlement(paid, selectedAccount, notesText.trim(), purchaseDate) }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (isArabic) "تأكيد فاتورة الشراء" else "Confirm Purchase Invoice", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryAmountRow(label: String, amount: Double, amountColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("₪ ${String.format(Locale.US, "%.2f", amount)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = amountColor)
    }
}

@Composable
fun SupplierQuickPaymentDialog(
    supplier: Supplier,
    currentBalance: Double,
    financialAccounts: List<FinancialAccount>,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (isCollection: Boolean, amount: Double, accountId: String?, notes: String, operationDate: String, onComplete: (Result<Unit>) -> Unit) -> Unit
) {
    var isCollection by remember { mutableStateOf(currentBalance <= 0.001) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCollectionReview by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var selectedAccount by remember { mutableStateOf(financialAccounts.firstOrNull { it.isActive }?.id) }
    var notesText by remember { mutableStateOf("") }
    var operationDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    val amount = amountText.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    val settlementCapacity = kotlin.math.abs(currentBalance)
    val excess = (amount - settlementCapacity).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isArabic) "تسوية حساب المورد: ${supplier.name}" else "Settle Supplier: ${supplier.name}") },
        text = {
            Column(modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (isArabic) "وضع الحساب الحالي" else "Current balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                    text = when {
                        currentBalance > 0.001 -> if (isArabic) "المحل مدين للمورد: ${String.format(Locale.US, "%.2f", currentBalance)} ₪" else "Store owes supplier: ${String.format(Locale.US, "%.2f", currentBalance)} ₪"
                        currentBalance < -0.001 -> if (isArabic) "المورد مدين للمحل: ${String.format(Locale.US, "%.2f", -currentBalance)} ₪" else "Supplier owes store: ${String.format(Locale.US, "%.2f", -currentBalance)} ₪"
                        else -> if (isArabic) "الرصيد خالص" else "Balance settled"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                    }
                }
                Text(if (isArabic) "نوع العملية" else "Operation type", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !isCollection, onClick = { isCollection = false }, label = { Text(if (isArabic) "دفع للمورد" else "Pay supplier") })
                    FilterChip(selected = isCollection, onClick = { isCollection = true }, label = { Text(if (isArabic) "تحصيل من المورد" else "Collect from supplier") })
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(if (isCollection) if (isArabic) "المبلغ المستلم من المورد" else "Amount received" else if (isArabic) "المبلغ المدفوع للمورد" else "Amount paid") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = operationDate,
                    onValueChange = { operationDate = it },
                    label = { Text(if (isArabic) "تاريخ العملية (yyyy-MM-dd)" else "Operation date (yyyy-MM-dd)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (financialAccounts.isNotEmpty()) {
                    Text(if (isArabic) "الحساب المالي" else "Financial account")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(financialAccounts.filter { it.isActive }) { acc ->
                            FilterChip(selected = selectedAccount == acc.id, onClick = { selectedAccount = acc.id }, label = { Text(acc.name) })
                        }
                    }
                } else {
                    Text(if (isArabic) "لا يوجد حساب مالي نشط؛ لا يمكن حفظ حركة نقدية." else "No active financial account is available.", color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text(if (isArabic) "البيان / السند (اختياري)" else "Notes / reference (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = amount > 0.0 && selectedAccount != null && isValidSupplierDate(operationDate) && !isSaving,
                onClick = {
                    if (isCollection) {
                        showCollectionReview = true
                    } else {
                        isSaving = true
                        errorMessage = null
                        onConfirm(false, amount, selectedAccount, notesText.trim(), operationDate) { result ->
                            isSaving = false
                            result.onFailure { errorMessage = it.message ?: if (isArabic) "فشل حفظ العملية." else "Could not save operation." }
                        }
                    }
                }
            ) { Text(if (isSaving) if (isArabic) "جارٍ الحفظ..." else "Saving..." else if (isArabic) if (isCollection && excess > 0.001) "مراجعة التحصيل" else if (isCollection) "مراجعة التحصيل" else "تأكيد الدفع" else "Review / confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text(if (isArabic) "إلغاء" else "Cancel") } }
    )
    if (showCollectionReview) {
        val appliedToDebt = minOf(amount, settlementCapacity)
        AlertDialog(
            onDismissRequest = { if (!isSaving) showCollectionReview = false },
            title = { Text(if (isArabic) "معاينة التحصيل قبل التأكيد" else "Review collection before confirming") },
            text = {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryAmountRow(if (isArabic) "المبلغ المستلم" else "Amount received", amount)
                    SummaryAmountRow(if (isArabic) "الدين الذي سيتم إغلاقه" else "Debt settled", appliedToDebt)
                    SummaryAmountRow(if (isArabic) "الزيادة" else "Excess", excess, if (excess > 0.001) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column { Text(if (isArabic) "1. تحصيل دين المورد" else "1. Settle supplier debt", fontWeight = FontWeight.Bold); Text("₪ ${String.format(Locale.US, "%.2f", appliedToDebt)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(if (isArabic) "يُغلق هذا الجزء من المبلغ المستحق على المورد." else "This portion closes the supplier's outstanding debt.", style = MaterialTheme.typography.bodySmall) }
                            }
                            if (excess > 0.001) {
                                HorizontalDivider()
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                    Column { Text(if (isArabic) "2. زيادة تحصيل من المورد" else "2. Excess collection from supplier", fontWeight = FontWeight.Bold); Text("₪ ${String.format(Locale.US, "%.2f", excess)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(if (isArabic) "تُسجّل كبند مستقل: رصيد مستحق للمورد على المحل. لا تُدمج مع تسوية الدين." else "Recorded separately as a balance owed by the store to the supplier.", style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                    if (excess > 0.001) Text(if (isArabic) "تحذير قبل الحفظ: المبلغ المستلم يزيد عن الدين القابل للإغلاق بمقدار ${String.format(Locale.US, "%.2f", excess)} ₪. سيتم تسجيل عمليتين منفصلتين مرتبطتين بعملية التحصيل نفسها. هل تريد المتابعة؟" else "Warning: the received amount exceeds the debt by ₪ ${String.format(Locale.US, "%.2f", excess)}. Two separate entries will be recorded. Continue?", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(enabled = amount > 0.0 && selectedAccount != null && isValidSupplierDate(operationDate) && !isSaving, onClick = {
                    isSaving = true; errorMessage = null
                    onConfirm(true, amount, selectedAccount, notesText.trim(), operationDate) { result ->
                        isSaving = false
                        result.onSuccess { showCollectionReview = false }.onFailure { errorMessage = it.message ?: if (isArabic) "فشل حفظ التحصيل." else "Could not save collection." }
                    }
                }) { Text(if (isArabic) "تأكيد التحصيل" else "Confirm collection") }
            },
            dismissButton = { TextButton(onClick = { showCollectionReview = false }, enabled = !isSaving) { Text(if (isArabic) "العودة للتعديل" else "Back to edit") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplierPurchaseReturnDialog(
    supplier: Supplier,
    purchases: List<Purchase>,
    financialAccounts: List<FinancialAccount>,
    isArabic: Boolean,
    onGetPurchaseLines: suspend (String) -> List<PurchaseLine>,
    onGetRemainingQuantities: suspend (String) -> Map<String, Int>,
    onDismiss: () -> Unit,
    onConfirm: (String, List<PurchaseReturnLineRequest>, String, String, String, String?, (Result<Unit>) -> Unit) -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedPurchaseId by remember(supplier.id, purchases) { mutableStateOf(purchases.firstOrNull()?.id) }
    var purchaseMenuExpanded by remember { mutableStateOf(false) }
    var lines by remember { mutableStateOf<List<PurchaseLine>>(emptyList()) }
    var remaining by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var quantities by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var reasonsByLine by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var notesByLine by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var expandedReasonLineId by remember { mutableStateOf<String?>(null) }
    var settlementMode by remember { mutableStateOf("PAYABLE") }
    var selectedAccount by remember { mutableStateOf(financialAccounts.firstOrNull()?.id) }
    var returnDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showReturnReview by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPurchaseId) {
        val id = selectedPurchaseId
        if (id == null) {
            lines = emptyList()
            remaining = emptyMap()
            quantities = emptyMap()
        } else {
            loading = true
            runCatching {
                val loadedLines = onGetPurchaseLines(id)
                val remainingQuantities = onGetRemainingQuantities(id)
                loadedLines to remainingQuantities
            }.onSuccess { (loadedLines, remainingQuantities) ->
                lines = loadedLines
                remaining = remainingQuantities
                quantities = emptyMap()
                reasonsByLine = emptyMap()
                notesByLine = emptyMap()
                errorMessage = null
            }.onFailure {
                lines = emptyList()
                remaining = emptyMap()
                errorMessage = it.message ?: if (isArabic) "تعذر تحميل بنود الفاتورة." else "Could not load invoice lines."
            }
            loading = false
        }
    }

    val selectedLines = lines.filter { (quantities[it.id] ?: 0) > 0 }
    val total = selectedLines.sumOf { line -> line.unitCost * (quantities[line.id] ?: 0) }
    val returnReasons = listOf(
        "بضاعة تالفة أو مكسورة" to "تلف أو كسر أو ضرر يجعل البضاعة غير صالحة للبيع.",
        "بضاعة منتهية الصلاحية" to "انتهاء صلاحية المنتج وقت اكتشاف الحالة.",
        "صلاحية قصيرة أو قاربت على الانتهاء" to "بضاعة لا تحقق مدة الصلاحية المتفق عليها.",
        "صنف أو منتج غير مطلوب" to "المورد سلّم صنفًا مختلفًا عن المطلوب.",
        "مواصفات أو حجم أو نوع غير مطابق" to "العبوة أو الوزن أو النوع لا يطابق الاتفاق أو الفاتورة.",
        "كمية زائدة عن الطلب" to "استلام كمية أكبر من الكمية المطلوبة أو المتفق عليها.",
        "جودة غير مطابقة" to "جودة المنتج أقل من المواصفات أو العينة المتفق عليها.",
        "عيب تصنيع أو منتج لا يعمل" to "عيب فني أو صناعي لا ينتج عن سوء التخزين في المحل.",
        "تغليف أو عبوات متضررة" to "تلف العبوة أو التغليف بما يبرر رفض المنتج أو إرجاعه.",
        "سحب المنتج أو تحذير من المورد" to "طلب المورد إرجاع المنتج أو صدور تحذير بشأنه.",
        "إرجاع حسب اتفاق مسبق مع المورد" to "بضاعة قابلة للإرجاع وفق اتفاق أو سياسة توريد متفق عليها.",
        "خطأ في السعر أو الفاتورة" to "اختلاف السعر أو الفاتورة، بعد الاتفاق على إعادة البضاعة فعليًا.",
        "رفض الاستلام أو مخالفة شروط التوريد" to "مخالفة شرط متفق عليه في التوريد تستوجب الإرجاع.",
        "سبب آخر" to "يتطلب كتابة توضيح إضافي."
    )
    val validDate = isValidSupplierDate(returnDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isArabic) "مرتجع مشتريات — ${supplier.name}" else "Purchase return — ${supplier.name}") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 590.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (purchases.isEmpty()) {
                    Text(if (isArabic) "لا توجد فواتير شراء نشطة لهذا المورد." else "No active purchase invoices for this supplier.")
                } else {
                    Box {
                        OutlinedButton(onClick = { purchaseMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(purchases.firstOrNull { it.id == selectedPurchaseId }?.let { "${it.invoiceNumber} • ${it.purchaseDate}" }
                                ?: if (isArabic) "اختر الفاتورة الأصلية" else "Choose original invoice")
                        }
                        DropdownMenu(expanded = purchaseMenuExpanded, onDismissRequest = { purchaseMenuExpanded = false }) {
                            purchases.forEach { purchase ->
                                DropdownMenuItem(
                                    text = { Text("${purchase.invoiceNumber} • ${purchase.purchaseDate} • ${String.format(Locale.US, "%.2f", purchase.totalAmount)} ₪") },
                                    onClick = { selectedPurchaseId = purchase.id; purchaseMenuExpanded = false }
                                )
                            }
                        }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(lines, key = { it.id }) { line ->
                            val available = remaining[line.id] ?: line.quantity
                            val qty = quantities[line.id] ?: 0
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
                                Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(line.productNameSnapshot, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (isArabic) "الأصل: ${line.quantity} • المرتجع المتاح: $available • التكلفة: ${String.format(Locale.US, "%.2f", line.unitCost)} ₪" else "Original: ${line.quantity} • Returnable: $available • Cost: ${String.format(Locale.US, "%.2f", line.unitCost)} ₪",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(if (isArabic) "كمية الإرجاع" else "Return quantity", modifier = Modifier.weight(1f))
                                        IconButton(onClick = { quantities = quantities + (line.id to (qty - 1).coerceAtLeast(0)) }, enabled = qty > 0) { Icon(Icons.Default.Remove, contentDescription = null) }
                                        Text(qty.toString(), fontWeight = FontWeight.Bold)
                                        IconButton(onClick = {
                                            if (qty < available) quantities = quantities + (line.id to qty + 1)
                                        }, enabled = qty < available) { Icon(Icons.Default.Add, contentDescription = null) }
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = settlementMode == "PAYABLE", onClick = { settlementMode = "PAYABLE" }, label = { Text(if (isArabic) "خصم من مستحقات المورد" else "Reduce supplier payable") })
                        FilterChip(selected = settlementMode == "CASH_REFUND", onClick = { settlementMode = "CASH_REFUND" }, label = { Text(if (isArabic) "استرداد نقدي" else "Cash refund") })
                    }
                    if (settlementMode == "CASH_REFUND") {
                        if (financialAccounts.isEmpty()) {
                            Text(if (isArabic) "لا يوجد حساب مالي نشط لاستلام المبلغ." else "No active account to receive the refund.", color = MaterialTheme.colorScheme.error)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(financialAccounts) { account ->
                                    FilterChip(selected = selectedAccount == account.id, onClick = { selectedAccount = account.id }, label = { Text(account.name) })
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = returnDate, onValueChange = { returnDate = it }, label = { Text(if (isArabic) "تاريخ المرتجع (yyyy-MM-dd)" else "Return date (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text(if (isArabic) "سبب الإرجاع لكل صنف" else "Return reason for each item", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    selectedLines.forEach { line ->
                        val chosenReason = reasonsByLine[line.id].orEmpty()
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(line.productNameSnapshot, fontWeight = FontWeight.SemiBold)
                                Box {
                                    OutlinedButton(onClick = { expandedReasonLineId = line.id }, modifier = Modifier.fillMaxWidth()) {
                                        Text(chosenReason.ifBlank { if (isArabic) "اختر سبب الإرجاع (مطلوب)" else "Select return reason (required)" }, modifier = Modifier.weight(1f), maxLines = 2)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                    DropdownMenu(expanded = expandedReasonLineId == line.id, onDismissRequest = { expandedReasonLineId = null }, modifier = Modifier.heightIn(max = 340.dp)) {
                                        returnReasons.forEach { (reasonTitle, description) ->
                                            DropdownMenuItem(text = { Column { Text(reasonTitle, fontWeight = FontWeight.Medium); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, onClick = { reasonsByLine = reasonsByLine + (line.id to reasonTitle); expandedReasonLineId = null })
                                        }
                                    }
                                }
                                if (chosenReason == "سبب آخر") {
                                    OutlinedTextField(value = notesByLine[line.id].orEmpty(), onValueChange = { notesByLine = notesByLine + (line.id to it) }, label = { Text(if (isArabic) "وضّح السبب (مطلوب)" else "Explain the reason (required)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                                } else {
                                    OutlinedTextField(value = notesByLine[line.id].orEmpty(), onValueChange = { notesByLine = notesByLine + (line.id to it) }, label = { Text(if (isArabic) "ملاحظات إضافية (اختياري)" else "Additional notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 1)
                                }
                            }
                        }
                    }
                    Text(if (isArabic) "إجمالي المرتجع: ${String.format(Locale.US, "%.2f", total)} ₪" else "Return total: ${String.format(Locale.US, "%.2f", total)} ₪", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = purchases.isNotEmpty() && selectedPurchaseId != null && selectedLines.isNotEmpty() && selectedLines.all { line -> reasonsByLine[line.id].orEmpty().isNotBlank() && (reasonsByLine[line.id] != "سبب آخر" || notesByLine[line.id].orEmpty().isNotBlank()) } && validDate && !loading && !saving && (settlementMode != "CASH_REFUND" || selectedAccount != null),
                onClick = { showReturnReview = true }
            ) { Text(if (isArabic) "مراجعة المرتجع وتأكيده" else "Review and confirm return") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(if (isArabic) "إلغاء" else "Cancel") } }
    )
    if (showReturnReview) {
        AlertDialog(
            onDismissRequest = { if (!saving) showReturnReview = false },
            title = { Text(if (isArabic) "مراجعة المرتجع قبل التأكيد" else "Review return before confirming") },
            text = {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (isArabic) "المورد: ${supplier.name}" else "Supplier: ${supplier.name}", fontWeight = FontWeight.Bold)
                    Text(if (isArabic) "الفاتورة الأصلية: ${purchases.firstOrNull { it.id == selectedPurchaseId }?.invoiceNumber ?: "—"}" else "Original invoice: ${purchases.firstOrNull { it.id == selectedPurchaseId }?.invoiceNumber ?: "—"}")
                    selectedLines.forEach { line ->
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(line.productNameSnapshot, fontWeight = FontWeight.SemiBold)
                                Text(if (isArabic) "الكمية: ${quantities[line.id] ?: 0} • السبب: ${reasonsByLine[line.id]}" else "Quantity: ${quantities[line.id] ?: 0} • Reason: ${reasonsByLine[line.id]}")
                                if (notesByLine[line.id].orEmpty().isNotBlank()) Text(notesByLine[line.id].orEmpty(), style = MaterialTheme.typography.bodySmall)
                                Text("₪ ${String.format(Locale.US, "%.2f", line.unitCost * (quantities[line.id] ?: 0))}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    SummaryAmountRow(if (isArabic) "إجمالي المرتجع" else "Return total", total, MaterialTheme.colorScheme.primary)
                    Text(if (settlementMode == "PAYABLE") if (isArabic) "سيتم خصم الإجمالي من مستحقات المورد." else "The total will reduce the amount payable to the supplier." else if (isArabic) "سيتم تسجيل الاسترداد في الحساب المالي المحدد." else "The refund will be recorded in the selected financial account.", style = MaterialTheme.typography.bodySmall)
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { Button(enabled = !saving, onClick = {
                val purchaseId = selectedPurchaseId ?: return@Button
                saving = true; errorMessage = null
                val requests = selectedLines.map { PurchaseReturnLineRequest(it.id, quantities[it.id] ?: 0) }
                val reasonSummary = selectedLines.joinToString("\n") { line ->
                    val reasonTitle = reasonsByLine[line.id].orEmpty()
                    val note = notesByLine[line.id].orEmpty().trim()
                    "${line.productNameSnapshot}: $reasonTitle" + if (note.isNotBlank()) " — $note" else ""
                }
                onConfirm(purchaseId, requests, reasonSummary, returnDate, settlementMode, if (settlementMode == "CASH_REFUND") selectedAccount else null) { result ->
                    saving = false
                    result.onSuccess { onSuccess(); onDismiss() }.onFailure { errorMessage = it.message ?: if (isArabic) "فشل حفظ المرتجع." else "Failed to save return." }
                }
            }) { Text(if (isArabic) "تأكيد المرتجع" else "Confirm return") } },
            dismissButton = { TextButton(onClick = { showReturnReview = false }, enabled = !saving) { Text(if (isArabic) "العودة للتعديل" else "Back to edit") } }
        )
    }
}

@Composable
fun LinkOrCreateSupplierItemDialog(
    allProducts: List<ProductItem>,
    linkedProductIds: Set<String>,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onLinkExisting: (List<ProductItem>) -> Unit,
    onUnlinkExisting: (String) -> Unit,
    onRequestAddNew: (isCatalogItem: Boolean) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val isCatalogTab = tabIndex == 1
    val filtered = allProducts.filter { it.isCatalogItem == isCatalogTab && (searchQuery.isBlank() || it.name.contains(searchQuery.trim(), true) || it.category.contains(searchQuery.trim(), true)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isArabic) "إدارة منتجات وأصناف المورد" else "Manage Supplier Products and Items") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0; searchQuery = "" }, text = { Text(if (isArabic) "المنتجات" else "Products") })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1; searchQuery = "" }, text = { Text(if (isArabic) "الأصناف" else "Items") })
                }
                Button(onClick = { onRequestAddNew(isCatalogTab) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(if (isArabic) if (isCatalogTab) "إضافة صنف" else "إضافة منتج" else if (isCatalogTab) "Add item" else "Add product")
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (isArabic) "ابحث بالاسم أو الفئة..." else "Search by name or category...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { Row { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = if (isArabic) "مسح" else "Clear") }; IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = if (isArabic) "عرض الكل" else "Show all") } } },
                    singleLine = true
                )
                if (filtered.isEmpty()) Text(if (isArabic) "لا توجد نتائج مطابقة." else "No matching records.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else LazyColumn(Modifier.heightIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.id }) { product ->
                        val linked = product.id in linkedProductIds
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) { Text(product.name, fontWeight = FontWeight.Medium); Text(product.category.ifBlank { product.unit }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            IconButton(onClick = { if (linked) onUnlinkExisting(product.id) else onLinkExisting(listOf(product)) }, modifier = Modifier.size(42.dp)) {
                                Icon(if (linked) Icons.Default.RemoveCircle else Icons.Default.AddCircle, contentDescription = if (linked) if (isArabic) "إزالة من المورد" else "Remove from supplier" else if (isArabic) "إضافة للمورد" else "Add to supplier", tint = if (linked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (isArabic) "تم" else "Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (isArabic) "إغلاق" else "Close") } }
    )
}
