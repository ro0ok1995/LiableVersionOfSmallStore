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
    onAddNewCatalogItemForSupplier: (supplierId: String, name: String, unit: String, costPrice: Double, category: String, isRawMaterial: Boolean, imageUri: String?, onComplete: (Result<ProductItem>) -> Unit) -> Unit,
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
            activeSuppliers.firstOrNull { it.id == initialSupplierId }
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

    var itemSearchQuery by remember { mutableStateOf("") }
    var isItemSearchExpanded by remember { mutableStateOf(true) }

    var supplierCart by remember(selectedSupplier?.id) { mutableStateOf<List<SupplierCartItem>>(emptyList()) }
    var isCartExpanded by remember { mutableStateOf(false) }

    var expandedCardItem by remember { mutableStateOf<ProductItem?>(null) }
    var showPaymentDialog by remember { mutableStateOf(initialAction == SupplierInitialAction.PAYMENT) }
    var showReturnDialog by remember { mutableStateOf(initialAction == SupplierInitialAction.RETURN) }
    var showLinkOrAddItemDialog by remember { mutableStateOf(false) }
    var showSettlementSheet by remember { mutableStateOf(false) }
    var showQuantitySchemaWarning by remember { mutableStateOf(false) }
    var operationErrorMessage by remember { mutableStateOf<String?>(null) }

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
                OutlinedTextField(
                    value = itemSearchQuery,
                    onValueChange = { itemSearchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(if (isArabic) "ابحث في أصناف ومنتجات المورد..." else "Search supplier items & products...")
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (itemSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { itemSearchQuery = ""; isItemSearchExpanded = true }) {
                                    Icon(Icons.Default.Clear, contentDescription = if (isArabic) "مسح البحث" else "Clear search")
                                }
                            }
                            IconButton(onClick = { isItemSearchExpanded = !isItemSearchExpanded }) {
                                Icon(if (isItemSearchExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = if (isArabic) "عرض الأصناف" else "Show items")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                FilledTonalIconButton(
                    onClick = { showLinkOrAddItemDialog = true },
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLink,
                        contentDescription = if (isArabic) "ربط أو إضافة صنف للمورد" else "Link or Add Item"
                    )
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
                selectedProducts.forEach { prod ->
                    onLinkItemToSupplier(selectedSupplier!!.id, prod.id, prod.costPrice)
                }
                showLinkOrAddItemDialog = false
            },
            onCreateNew = { name, unit, cost, category, isRawMaterial, onComplete ->
                onAddNewCatalogItemForSupplier(
                    selectedSupplier!!.id,
                    name,
                    unit,
                    cost,
                    category,
                    isRawMaterial,
                    null,
                    onComplete
                )
            }
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
    onCompleteTransaction: () -> Unit
) {
    val totalAmount = cart.sumOf { it.subtotal }

    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (cart.isNotEmpty()) onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BadgedBox(badge = { Badge { Text("${cart.size}") } }) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = if (isArabic) "سلة توريد المورد" else "Supplier Cart",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = String.format(Locale.US, "%.2f", totalAmount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = null
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded && cart.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(cart, key = { it.itemId }) { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditCartItem(item) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${String.format(Locale.US, "%.2f", item.unitCost)} × ${formatQuantity(item.quantity)} ${item.unit} = ${String.format(Locale.US, "%.2f", item.subtotal)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "${formatQuantity(item.quantity)} ${item.unit}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onRemoveFromCart(item.itemId) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = if (isArabic) "حذف" else "Remove",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onCompleteTransaction,
                enabled = cart.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isArabic) "إتمام المعاملة" else "Complete Transaction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
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
    currentSupplierBalance: Double,
    financialAccounts: List<FinancialAccount>,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onConfirmSettlement: (paidAmt: Double, accountId: String?, notes: String, purchaseDate: String) -> Unit
) {
    var paidAmountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", totalAmount)) }
    var selectedAccount by remember { mutableStateOf(financialAccounts.firstOrNull { it.isActive }?.id) }
    var notesText by remember { mutableStateOf("") }
    var purchaseDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val parsedPaidAmount = paidAmountText.replace(',', '.').toDoubleOrNull()
    val validPurchaseDate = isValidSupplierDate(purchaseDate)
    
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(if (isArabic) "إتمام معاملة المورد" else "Complete Supplier Transaction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (isArabic) "إجمالي الفاتورة: ${String.format(Locale.US, "%.2f", totalAmount)} ₪" else "Invoice total: ${String.format(Locale.US, "%.2f", totalAmount)} ₪", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(
                if (isArabic) "الرصيد الحالي: ${String.format(Locale.US, "%.2f", currentSupplierBalance)} ₪ • الرصيد المتوقع بعد الفاتورة: ${String.format(Locale.US, "%.2f", currentSupplierBalance + totalAmount - (paidAmountText.replace(',', '.').toDoubleOrNull() ?: 0.0))} ₪" else "Current balance: ${String.format(Locale.US, "%.2f", currentSupplierBalance)} ₪ • Expected balance after invoice: ${String.format(Locale.US, "%.2f", currentSupplierBalance + totalAmount - (paidAmountText.replace(',', '.').toDoubleOrNull() ?: 0.0))} ₪",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            OutlinedTextField(
                value = paidAmountText,
                onValueChange = { paidAmountText = it },
                label = { Text(if (isArabic) "المبلغ المدفوع (نقداً/بنك)" else "Paid Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            
            if (financialAccounts.isNotEmpty()) {
                Text(if (isArabic) "حساب الدفع" else "Payment Account")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(financialAccounts) { acc ->
                        FilterChip(
                            selected = selectedAccount == acc.id,
                            onClick = { selectedAccount = acc.id },
                            label = { Text(acc.name) }
                        )
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = purchaseDate,
                    onValueChange = { purchaseDate = it },
                    label = { Text(if (isArabic) "تاريخ الفاتورة (yyyy-MM-dd)" else "Invoice date (yyyy-MM-dd)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = if (isArabic) "اختيار التاريخ" else "Choose date")
                }
            }
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text(if (isArabic) "ملاحظات الفاتورة (اختياري)" else "Notes (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                purchaseDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
                            }
                            showDatePicker = false
                        }) { Text(if (isArabic) "اختيار" else "Select") }
                    },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(if (isArabic) "إلغاء" else "Cancel") } }
                ) { DatePicker(state = datePickerState) }
            }
            
            if (parsedPaidAmount != null && parsedPaidAmount > totalAmount + 0.001) {
                Text(if (isArabic) "المبلغ المدفوع لا يمكن أن يتجاوز إجمالي الفاتورة." else "Paid amount cannot exceed invoice total.", color = MaterialTheme.colorScheme.error)
            }
            Button(
                enabled = parsedPaidAmount != null && parsedPaidAmount.isFinite() && parsedPaidAmount >= 0.0 && parsedPaidAmount <= totalAmount + 0.001 && validPurchaseDate && (parsedPaidAmount <= 0.001 || selectedAccount != null),
                onClick = {
                    onConfirmSettlement(parsedPaidAmount ?: 0.0, selectedAccount, notesText.trim(), purchaseDate)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isArabic) "تأكيد فاتورة الشراء" else "Confirm Purchase Invoice")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = when {
                        currentBalance > 0.001 -> if (isArabic) "المحل مدين للمورد: ${String.format(Locale.US, "%.2f", currentBalance)} ₪" else "Store owes supplier: ${String.format(Locale.US, "%.2f", currentBalance)} ₪"
                        currentBalance < -0.001 -> if (isArabic) "المورد مدين للمحل: ${String.format(Locale.US, "%.2f", -currentBalance)} ₪" else "Supplier owes store: ${String.format(Locale.US, "%.2f", -currentBalance)} ₪"
                        else -> if (isArabic) "الرصيد خالص" else "Balance settled"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                if (isCollection && excess > 0.001) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (isArabic) "تحذير: المبلغ أكبر من الرصيد القابل للتسوية بمقدار ${String.format(Locale.US, "%.2f", excess)} ₪." else "Warning: amount exceeds the settleable balance by ${String.format(Locale.US, "%.2f", excess)} ₪.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (isArabic) "سيُسجّل النظام عملية تسوية مستقلة بمقدار ${String.format(Locale.US, "%.2f", settlementCapacity)} ₪، وعملية ثانية للزيادة ${String.format(Locale.US, "%.2f", excess)} ₪ بعنوان «زيادة تحصيل من المورد — رصيد مستحق للمورد على المحل»." else "The system will record a settlement entry and a separate supplier-credit entry for the excess.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
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
                    isSaving = true
                    errorMessage = null
                    onConfirm(isCollection, amount, selectedAccount, notesText.trim(), operationDate) { result ->
                        isSaving = false
                        result.onFailure { errorMessage = it.message ?: if (isArabic) "فشل حفظ العملية." else "Could not save operation." }
                    }
                }
            ) { Text(if (isSaving) if (isArabic) "جارٍ الحفظ..." else "Saving..." else if (isArabic) if (isCollection && excess > 0.001) "تأكيد العمليتين" else "تأكيد التسوية" else "Confirm settlement") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text(if (isArabic) "إلغاء" else "Cancel") } }
    )
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
    var settlementMode by remember { mutableStateOf("PAYABLE") }
    var selectedAccount by remember { mutableStateOf(financialAccounts.firstOrNull()?.id) }
    var reason by remember { mutableStateOf("") }
    var returnDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

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
    val validDate = isValidSupplierDate(returnDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isArabic) "مرتجع مشتريات — ${supplier.name}" else "Purchase return — ${supplier.name}") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text(if (isArabic) "سبب المرتجع (مطلوب)" else "Return reason (required)") }, modifier = Modifier.fillMaxWidth())
                    Text(if (isArabic) "إجمالي المرتجع: ${String.format(Locale.US, "%.2f", total)} ₪" else "Return total: ${String.format(Locale.US, "%.2f", total)} ₪", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = purchases.isNotEmpty() && selectedPurchaseId != null && selectedLines.isNotEmpty() && reason.isNotBlank() && validDate && !loading && !saving && (settlementMode != "CASH_REFUND" || selectedAccount != null),
                onClick = {
                    val purchaseId = selectedPurchaseId ?: return@Button
                    saving = true
                    errorMessage = null
                    val requests = selectedLines.map { PurchaseReturnLineRequest(it.id, quantities[it.id] ?: 0) }
                    onConfirm(purchaseId, requests, reason.trim(), returnDate, settlementMode, if (settlementMode == "CASH_REFUND") selectedAccount else null) { result ->
                        saving = false
                        result.onSuccess {
                            onSuccess()
                            onDismiss()
                        }.onFailure {
                            errorMessage = it.message ?: if (isArabic) "فشل حفظ المرتجع." else "Failed to save return."
                        }
                    }
                }
            ) { Text(if (isArabic) "مراجعة المرتجع وتأكيده" else "Review and confirm return") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(if (isArabic) "إلغاء" else "Cancel") } }
    )
}

@Composable
fun LinkOrCreateSupplierItemDialog(
    allProducts: List<ProductItem>,
    linkedProductIds: Set<String>,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onLinkExisting: (List<ProductItem>) -> Unit,
    onCreateNew: (name: String, unit: String, cost: Double, category: String, isRawMaterial: Boolean, onComplete: (Result<ProductItem>) -> Unit) -> Unit
) {
    var tabIndex by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var newlyCreatedProduct by remember { mutableStateOf<ProductItem?>(null) }
    var createError by remember { mutableStateOf<String?>(null) }
    var nameText by remember { mutableStateOf("") }
    var unitText by remember { mutableStateOf("وحدة") }
    var costText by remember { mutableStateOf("") }
    var isRawMaterial by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isArabic) "إضافة صنف للمورد" else "Add Item to Supplier") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text(if (isArabic) "ربط صنف موجود" else "Link Existing") })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text(if (isArabic) "إنشاء جديد" else "Create New") })
                }
                
                if (tabIndex == 0) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(if (isArabic) "ابحث باسم الصنف أو رمزه..." else "Search item or code...") },
                        trailingIcon = {
                            Row {
                                if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = null) }
                                IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = if (isArabic) "عرض الكل" else "Show all") }
                            }
                        },
                        singleLine = true
                    )
                    val selectableProducts = (allProducts + listOfNotNull(newlyCreatedProduct)).distinctBy { it.id }
                    val unlinked = selectableProducts.filter { product ->
                        (product.id == newlyCreatedProduct?.id || product.id !in linkedProductIds) &&
                            (searchQuery.isBlank() || product.name.contains(searchQuery, true) || product.category.contains(searchQuery, true))
                    }
                    if (unlinked.isEmpty()) {
                        Text(if (isArabic) "لا توجد أصناف متاحة غير مربوطة تطابق البحث" else "No matching unlinked items")
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(unlinked, key = { it.id }) { prod ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedIds = if (prod.id in selectedIds) selectedIds - prod.id else selectedIds + prod.id
                                    }.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = prod.id in selectedIds, onCheckedChange = { checked -> selectedIds = if (checked) selectedIds + prod.id else selectedIds - prod.id })
                                    Column(Modifier.weight(1f)) {
                                        Text(prod.name)
                                        Text(prod.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (prod.id in selectedIds) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                } else {
                    createError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text(if (isArabic) "اسم الصنف" else "Item Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { unitText = it },
                        label = { Text(if (isArabic) "الوحدة (مثل: غرام، متر)" else "Unit (e.g., Gram, Meter)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text(if (isArabic) "سعر التكلفة" else "Cost Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isRawMaterial, onCheckedChange = { isRawMaterial = it })
                        Text(if (isArabic) "هذا الصنف مادة خام (لا يظهر للعميل العادي)" else "Raw material (hidden from customers)")
                    }
                }
            }
        },
        confirmButton = {
            if (tabIndex == 1) {
                Button(onClick = {
                    val cost = costText.replace(',', '.').toDoubleOrNull()
                    if (nameText.isBlank() || cost == null || !cost.isFinite() || cost < 0.0) {
                        createError = if (isArabic) "أدخل اسمًا صحيحًا وتكلفة غير سالبة." else "Enter a name and a valid non-negative cost."
                    } else {
                        createError = null
                        onCreateNew(nameText.trim(), unitText.trim(), cost, if (isArabic) "مواد موردين" else "Supplier Items", isRawMaterial) { result ->
                            result.onSuccess { product ->
                                newlyCreatedProduct = product
                                selectedIds = selectedIds + product.id
                                tabIndex = 0
                                searchQuery = ""
                                nameText = ""
                                costText = ""
                                createError = if (isArabic) "تم إنشاء الصنف وتحديده. اضغط «إضافة المحدد إلى المورد» للمتابعة." else "Item created and selected. Add the selected items to continue."
                            }.onFailure { error ->
                                createError = error.message ?: if (isArabic) "فشل حفظ الصنف." else "Failed to save item."
                            }
                        }
                    }
                }) {
                    Text(if (isArabic) "حفظ والعودة للاختيار" else "Save and return to selection")
                }
            } else {
                Button(onClick = { onLinkExisting((allProducts + listOfNotNull(newlyCreatedProduct)).distinctBy { it.id }.filter { it.id in selectedIds && (it.id !in linkedProductIds || it.id == newlyCreatedProduct?.id) }) }, enabled = selectedIds.isNotEmpty()) {
                    Text(if (isArabic) "إضافة المحدد إلى المورد (${selectedIds.size})" else "Add selected (${selectedIds.size})")
                }
            }
        }
    )
}
