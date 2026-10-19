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
    onGetPurchaseReturnableQuantities: (purchaseId: String, onResult: (Map<String, Int>) -> Unit) -> Unit = { _, onResult -> onResult(emptyMap()) },
    onLinkItemToSupplier: (supplierId: String, productId: String, lastCost: Double) -> Unit,
    onAddNewCatalogItemForSupplier: (supplierId: String, name: String, unit: String, costPrice: Double, category: String, isRawMaterial: Boolean, imageUri: String?) -> Unit,
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
    var supplierChangeCandidate by remember { mutableStateOf<Supplier?>(null) }
    var operationError by remember { mutableStateOf<String?>(null) }

    var itemSearchQuery by remember { mutableStateOf("") }

    // Keep cart state independent of selected supplier so Compose cannot silently erase it on supplier change.
    var supplierCart by remember { mutableStateOf<List<SupplierCartItem>>(emptyList()) }
    var isCartExpanded by remember { mutableStateOf(false) }

    var expandedCardItem by remember { mutableStateOf<ProductItem?>(null) }
    var showPaymentDialog by remember { mutableStateOf(initialAction == SupplierInitialAction.PAYMENT) }
    var showReturnDialog by remember { mutableStateOf(initialAction == SupplierInitialAction.RETURN) }
    var showLinkOrAddItemDialog by remember { mutableStateOf(false) }
    var showSettlementSheet by remember { mutableStateOf(false) }
    var isSubmittingPurchase by remember { mutableStateOf(false) }
    var isSubmittingPayment by remember { mutableStateOf(false) }
    var returnPurchase by remember { mutableStateOf<Purchase?>(null) }
    var returnPurchaseLines by remember { mutableStateOf<List<PurchaseLine>>(emptyList()) }
    var returnableQuantities by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var returnQuantities by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var returnReason by remember { mutableStateOf("") }
    var returnSettlementMode by remember { mutableStateOf("PAYABLE") }
    var returnAccountId by remember { mutableStateOf(financialAccounts.firstOrNull { it.isActive }?.id) }
    var returnError by remember { mutableStateOf<String?>(null) }
    var isSubmittingReturn by remember { mutableStateOf(false) }

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

    val supplierItems = remember(products, supplierLinkedMap, itemSearchQuery) {
        val activeProducts = products.filter { !it.isArchived }
        val baseList = if (supplierLinkedMap.isNotEmpty()) {
            activeProducts.filter { it.id in supplierLinkedMap.keys }
        } else {
            activeProducts
        }
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
                                    isSupplierDropdownExpanded = false
                                    supplierSearchQuery = ""
                                    if (selectedSupplier?.id == sup.id) return@DropdownMenuItem
                                    if (supplierCart.isNotEmpty()) {
                                        supplierChangeCandidate = sup
                                    } else {
                                        selectedSupplier = sup
                                    }
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
                                Text(if (isArabic) "سداد مورد" else "Pay Supplier")
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
                        if (itemSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { itemSearchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
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

            if (supplierItems.isEmpty()) {
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
                        Text(
                            text = if (isArabic) "لا توجد منتجات أو أصناف مطابقة لبحثك" else "No matching products or items found",
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
                val supId = selectedSupplier?.id
                if (supId != null) {
                    // Linking is catalog metadata; do not overwrite the product's last cost until
                    // a purchase containing this cost has actually been saved successfully.
                    onLinkItemToSupplier(supId, item.id, confirmedCost)
                }
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

    if (showSettlementSheet && selectedSupplier != null) {
        val totalOrderAmount = supplierCart.sumOf { it.subtotal }
        SupplierSettlementSheet(
            supplier = selectedSupplier!!,
            totalAmount = totalOrderAmount,
            financialAccounts = financialAccounts,
            isArabic = isArabic,
            isSubmitting = isSubmittingPurchase,
            onDismiss = { if (!isSubmittingPurchase) showSettlementSheet = false },
            onConfirmSettlement = { paidAmt, accountId, notes ->
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                // The current persisted purchase-line schema is integer-based. Never silently
                // round a fractional cart quantity into a different physical quantity.
                val fractionalLine = supplierCart.firstOrNull { kotlin.math.abs(it.quantity - kotlin.math.round(it.quantity)) > 0.0000001 }
                if (fractionalLine != null) {
                    operationError = if (isArabic) {
                        "لا يمكن حفظ كمية عشرية (${formatQuantity(fractionalLine.quantity)}) لأن نموذج وقاعدة بيانات بند الشراء الحاليين يحفظان الكمية كعدد صحيح. لم تُحفظ الفاتورة ولم تُمسح السلة."
                    } else {
                        "Cannot save fractional quantity (${formatQuantity(fractionalLine.quantity)}): the current purchase-line model/database stores whole numbers. Nothing was saved and the cart was kept."
                    }
                    return@SupplierSettlementSheet
                }
                val requests = supplierCart.map { cartItem ->
                    val qtyInt = cartItem.quantity.toInt()
                    PurchaseLineRequest(
                        productId = cartItem.itemId,
                        productNameSnapshot = "${cartItem.name} (${formatQuantity(cartItem.quantity)} ${cartItem.unit})",
                        quantity = qtyInt,
                        unitCost = AccountingDecimal.money(cartItem.unitCost)
                    )
                }
                isSubmittingPurchase = true
                onRecordPurchase(
                    selectedSupplier!!.id,
                    requests,
                    paidAmt,
                    accountId,
                    notes,
                    today
                ) { result ->
                    isSubmittingPurchase = false
                    if (result.isSuccess) {
                        supplierCart.forEach { cartItem ->
                            val product = products.firstOrNull { it.id == cartItem.itemId }
                            if (product != null && abs(product.costPrice - cartItem.unitCost) > 0.001) {
                                onUpdateProductCost(selectedSupplier!!.id, product.id, cartItem.unitCost)
                            }
                        }
                        supplierCart = emptyList()
                        isCartExpanded = false
                        showSettlementSheet = false
                        operationError = null
                        refreshTrigger++
                    } else {
                        operationError = result.exceptionOrNull()?.message
                            ?: if (isArabic) "فشل حفظ فاتورة الشراء" else "Failed to save purchase invoice"
                    }
                }
            }
        )
    }

    if (showPaymentDialog && selectedSupplier != null) {
        SupplierQuickPaymentDialog(
            supplier = selectedSupplier!!,
            currentBalance = balancesMap[selectedSupplier!!.id]?.balance ?: 0.0,
            financialAccounts = financialAccounts,
            isArabic = isArabic,
            onDismiss = { if (!isSubmittingPayment) showPaymentDialog = false },
            isSubmitting = isSubmittingPayment,
            onConfirm = { amount, accountId, notes, onComplete ->
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                isSubmittingPayment = true
                onRecordSupplierPayment(selectedSupplier!!.id, amount, today, accountId, notes) { result ->
                    isSubmittingPayment = false
                    if (result.isSuccess) {
                        showPaymentDialog = false
                        refreshTrigger++
                    }
                    onComplete(result)
                }
            }
        )
    }

    if (showReturnDialog && selectedSupplier != null) {
        val supplierPurchaseHistory = remember(supplierPurchases, selectedSupplier?.id) {
            supplierPurchases.filter { it.supplierId == selectedSupplier?.id && it.status == "ACTIVE" }
                .sortedByDescending { it.purchaseDate }
        }
        val activeAccounts = financialAccounts.filter { it.isActive }
        val selectedReturnTotal = returnPurchaseLines.sumOf { line ->
            (returnQuantities[line.id] ?: 0).coerceAtLeast(0) * line.unitCost
        }
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingReturn) {
                    showReturnDialog = false
                    returnPurchase = null
                    returnPurchaseLines = emptyList()
                    returnableQuantities = emptyMap()
                    returnQuantities = emptyMap()
                    returnError = null
                }
            },
            title = {
                Text(
                    if (returnPurchase == null) {
                        if (isArabic) "اختر فاتورة الشراء للمرتجع" else "Select original purchase invoice"
                    } else {
                        if (isArabic) "مرتجع مشتريات — ${returnPurchase!!.invoiceNumber}" else "Purchase return — ${returnPurchase!!.invoiceNumber}"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (returnPurchase == null) {
                        if (supplierPurchaseHistory.isEmpty()) {
                            Text(if (isArabic) "لا توجد فواتير شراء نشطة لهذا المورد." else "No active purchase invoices for this supplier.")
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(supplierPurchaseHistory, key = { it.id }) { purchase ->
                                    OutlinedButton(
                                        onClick = {
                                            returnPurchase = purchase
                                            returnPurchaseLines = emptyList()
                                            returnableQuantities = emptyMap()
                                            returnQuantities = emptyMap()
                                            returnError = null
                                            onGetPurchaseReturnableQuantities(purchase.id) { quantities ->
                                                returnableQuantities = quantities
                                            }
                                            scope.launch {
                                                runCatching { onGetPurchaseLines(purchase.id) }
                                                    .onSuccess { lines ->
                                                        returnPurchaseLines = lines
                                                        returnQuantities = lines.associate { it.id to 0 }
                                                    }
                                                    .onFailure { error ->
                                                        returnError = error.message ?: if (isArabic) "تعذر تحميل بنود الفاتورة" else "Could not load invoice lines"
                                                    }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(purchase.invoiceNumber, fontWeight = FontWeight.Bold)
                                            Text("${purchase.purchaseDate} • ${String.format(Locale.US, "%.2f", purchase.totalAmount)} ₪")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            if (isArabic) "حدد كميات البنود من الفاتورة الأصلية. سيمنع النظام حفظ أي كمية تتجاوز المتبقي القابل للإرجاع."
                            else "Select quantities from the original invoice. The repository will reject quantities exceeding the remaining returnable amount."
                        )
                        if (returnPurchaseLines.isEmpty()) {
                            Text(if (isArabic) "جارٍ تحميل البنود أو لا توجد بنود في الفاتورة." else "Loading lines or no lines are available.")
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                items(returnPurchaseLines, key = { it.id }) { line ->
                                    val quantity = returnQuantities[line.id] ?: 0
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(line.productNameSnapshot, fontWeight = FontWeight.SemiBold)
                                            Text("${if (isArabic) "المتبقي القابل للإرجاع" else "Remaining returnable"}: ${returnableQuantities[line.id] ?: 0} × ${String.format(Locale.US, "%.2f", line.unitCost)} ₪", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = {
                                            returnQuantities = returnQuantities + (line.id to (quantity - 1).coerceAtLeast(0))
                                            returnError = null
                                        }) { Icon(Icons.Default.Remove, contentDescription = if (isArabic) "إنقاص" else "Decrease") }
                                        Text(quantity.toString(), modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                                        IconButton(onClick = {
                                            returnQuantities = returnQuantities + (line.id to (quantity + 1).coerceAtMost(returnableQuantities[line.id] ?: 0))
                                            returnError = null
                                        }) { Icon(Icons.Default.Add, contentDescription = if (isArabic) "زيادة" else "Increase") }
                                    }
                                }
                            }
                        }
                        Text(
                            "${if (isArabic) "إجمالي المرتجع" else "Return total"}: ${String.format(Locale.US, "%.2f", selectedReturnTotal)} ₪",
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = returnReason,
                            onValueChange = { returnReason = it; returnError = null },
                            label = { Text(if (isArabic) "سبب المرتجع" else "Return reason") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Text(if (isArabic) "طريقة التسوية" else "Settlement")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = returnSettlementMode == "PAYABLE",
                                onClick = { returnSettlementMode = "PAYABLE" },
                                label = { Text(if (isArabic) "خصم من المستحقات" else "Reduce payable") }
                            )
                            FilterChip(
                                selected = returnSettlementMode == "CASH_REFUND",
                                onClick = { returnSettlementMode = "CASH_REFUND" },
                                label = { Text(if (isArabic) "استرداد نقدي" else "Cash refund") }
                            )
                        }
                        if (returnSettlementMode == "CASH_REFUND") {
                            if (activeAccounts.isEmpty()) {
                                Text(if (isArabic) "لا يوجد حساب مالي نشط لاستلام الاسترداد." else "No active account is available for the refund.", color = MaterialTheme.colorScheme.error)
                            } else {
                                activeAccounts.forEach { account ->
                                    FilterChip(
                                        selected = returnAccountId == account.id,
                                        onClick = { returnAccountId = account.id },
                                        label = { Text(account.name) }
                                    )
                                }
                            }
                        }
                    }
                    if (returnError != null) Text(returnError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                if (returnPurchase != null) {
                    Button(
                        enabled = !isSubmittingReturn,
                        onClick = {
                            val selectedLines = returnQuantities.filterValues { it > 0 }
                                .map { (lineId, quantity) -> PurchaseReturnLineRequest(lineId, quantity) }
                            when {
                                selectedLines.isEmpty() -> returnError = if (isArabic) "حدد صنفًا وكمية للمرتجع" else "Select at least one return quantity"
                                returnReason.isBlank() -> returnError = if (isArabic) "سبب المرتجع مطلوب" else "Return reason is required"
                                returnSettlementMode == "CASH_REFUND" && activeAccounts.none { it.id == returnAccountId } -> returnError = if (isArabic) "اختر حسابًا ماليًا نشطًا" else "Select an active financial account"
                                else -> {
                                    isSubmittingReturn = true
                                    returnError = null
                                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                    onRecordPurchaseReturn(
                                        returnPurchase!!.id,
                                        selectedLines,
                                        returnReason.trim(),
                                        today,
                                        returnSettlementMode,
                                        if (returnSettlementMode == "CASH_REFUND") returnAccountId else null
                                    ) { result ->
                                        isSubmittingReturn = false
                                        if (result.isSuccess) {
                                            showReturnDialog = false
                                            returnPurchase = null
                                            returnPurchaseLines = emptyList()
                                            returnableQuantities = emptyMap()
                                            returnQuantities = emptyMap()
                                            returnReason = ""
                                            returnError = null
                                            refreshTrigger++
                                        } else {
                                            returnError = result.exceptionOrNull()?.message ?: if (isArabic) "فشل حفظ المرتجع" else "Failed to save return"
                                        }
                                    }
                                }
                            }
                        }
                    ) { Text(if (isSubmittingReturn) (if (isArabic) "جارٍ الحفظ..." else "Saving..." ) else (if (isArabic) "تأكيد المرتجع" else "Confirm return")) }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSubmittingReturn,
                    onClick = {
                        if (returnPurchase != null) {
                            returnPurchase = null
                            returnPurchaseLines = emptyList()
                            returnableQuantities = emptyMap()
                            returnQuantities = emptyMap()
                            returnError = null
                        } else {
                            showReturnDialog = false
                            returnReason = ""
                        }
                    }
                ) { Text(if (returnPurchase != null) (if (isArabic) "رجوع للفواتير" else "Back to invoices") else (if (isArabic) "إلغاء" else "Cancel")) }
            }
        )
    }

    if (showLinkOrAddItemDialog && selectedSupplier != null) {
        LinkOrCreateSupplierItemDialog(
            allProducts = products.filter { !it.isArchived },
            linkedProductIds = supplierLinkedMap.keys,
            isArabic = isArabic,
            onDismiss = { showLinkOrAddItemDialog = false },
            onLinkExisting = { prod ->
                onLinkItemToSupplier(selectedSupplier!!.id, prod.id, prod.costPrice)
                showLinkOrAddItemDialog = false
            },
            onCreateNew = { name, unit, cost, category, isRawMaterial ->
                onAddNewCatalogItemForSupplier(
                    selectedSupplier!!.id,
                    name,
                    unit,
                    cost,
                    category,
                    isRawMaterial,
                    null
                )
                showLinkOrAddItemDialog = false
            }
        )
    }

    supplierChangeCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { supplierChangeCandidate = null },
            title = { Text(if (isArabic) "تغيير المورد؟" else "Change supplier?") },
            text = {
                Text(
                    if (isArabic) "توجد أصناف في سلة المورد الحالي. تغيير المورد سيمسح السلة الحالية. هل تريد المتابعة إلى ${candidate.name}؟"
                    else "The current supplier cart contains items. Changing supplier will clear this cart. Continue to ${candidate.name}?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    supplierCart = emptyList()
                    isCartExpanded = false
                    selectedSupplier = candidate
                    supplierChangeCandidate = null
                }) { Text(if (isArabic) "تغيير ومسح السلة" else "Change and clear cart") }
            },
            dismissButton = {
                TextButton(onClick = { supplierChangeCandidate = null }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    operationError?.let { message ->
        AlertDialog(
            onDismissRequest = { operationError = null },
            title = { Text(if (isArabic) "تعذر إتمام العملية" else "Operation failed") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { operationError = null }) { Text(if (isArabic) "حسنًا" else "OK") }
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

private fun formatQuantity(qty: Double): String {
    if (!qty.isFinite()) return "—"
    return String.format(Locale.US, "%.3f", qty).trimEnd('0').trimEnd('.')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierSettlementSheet(
    supplier: Supplier,
    totalAmount: Double,
    financialAccounts: List<FinancialAccount>,
    isArabic: Boolean,
    isSubmitting: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmSettlement: (paidAmt: Double, accountId: String?, notes: String) -> Unit
) {
    var paidAmountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", totalAmount)) }
    val activeAccounts = remember(financialAccounts) { financialAccounts.filter { it.isActive } }
    var selectedAccount by remember { mutableStateOf(activeAccounts.firstOrNull()?.id) }
    var notesText by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val parsedPaidAmount = paidAmountText.trim().replace(",", ".").toDoubleOrNull()
    
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(if (isArabic) "إتمام معاملة المورد" else "Complete Supplier Transaction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (isArabic) "الإجمالي: $totalAmount" else "Total Amount: $totalAmount", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            
            OutlinedTextField(
                value = paidAmountText,
                onValueChange = { paidAmountText = it },
                label = { Text(if (isArabic) "المبلغ المدفوع (نقداً/بنك)" else "Paid Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            
            if (activeAccounts.isNotEmpty()) {
                Text(if (isArabic) "حساب الدفع" else "Payment Account")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(activeAccounts) { acc ->
                        FilterChip(
                            selected = selectedAccount == acc.id,
                            onClick = { selectedAccount = acc.id },
                            label = { Text(acc.name) }
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text(if (isArabic) "ملاحظات الفاتورة (اختياري)" else "Notes (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            if (validationError != null) {
                Text(validationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (parsedPaidAmount != null && parsedPaidAmount > 0.001 && activeAccounts.isEmpty()) {
                Text(if (isArabic) "لا يوجد حساب مالي نشط لتسجيل الدفعة" else "No active financial account is available for this payment", color = MaterialTheme.colorScheme.error)
            }
            Button(
                enabled = !isSubmitting,
                onClick = {
                    val amt = parsedPaidAmount
                    when {
                        amt == null || !amt.isFinite() -> validationError = if (isArabic) "أدخل مبلغًا رقميًا صالحًا" else "Enter a valid numeric amount"
                        amt < 0.0 -> validationError = if (isArabic) "لا يمكن أن يكون المبلغ سالبًا" else "Amount cannot be negative"
                        amt > totalAmount + 0.001 -> validationError = if (isArabic) "المبلغ المدفوع لا يمكن أن يتجاوز إجمالي الفاتورة" else "Paid amount cannot exceed the invoice total"
                        amt > 0.001 && activeAccounts.none { it.id == selectedAccount } -> validationError = if (isArabic) "اختر حسابًا ماليًا نشطًا" else "Select an active financial account"
                        else -> { validationError = null; onConfirmSettlement(amt, if (amt > 0.001) selectedAccount else null, notesText.trim()) }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSubmitting) (if (isArabic) "جارٍ الحفظ..." else "Saving...") else (if (isArabic) "تأكيد الدفع" else "Confirm Payment"))
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
    isSubmitting: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, accountId: String?, notes: String, onComplete: (Result<Unit>) -> Unit) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val activeAccounts = remember(financialAccounts) { financialAccounts.filter { it.isActive } }
    var selectedAccount by remember { mutableStateOf(activeAccounts.firstOrNull()?.id) }
    var notesText by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val parsedAmount = amountText.trim().replace(",", ".").toDoubleOrNull()
    val validAmount = parsedAmount != null && parsedAmount.isFinite() && parsedAmount > 0.0
    val validAccount = activeAccounts.any { it.id == selectedAccount }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(if (isArabic) "سداد مورد: ${supplier.name}" else "Pay Supplier: ${supplier.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isArabic) "الرصيد المستحق: $currentBalance" else "Balance Payable: $currentBalance",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(if (isArabic) "المبلغ المراد سداده" else "Amount to Pay") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (activeAccounts.isNotEmpty()) {
                    Text(if (isArabic) "حساب السداد" else "Payment Account")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(activeAccounts) { acc ->
                            FilterChip(
                                selected = selectedAccount == acc.id,
                                onClick = { selectedAccount = acc.id },
                                label = { Text(acc.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text(if (isArabic) "البيان / السند (اختياري)" else "Notes / Ref (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (validationError != null) Text(validationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (activeAccounts.isEmpty()) Text(if (isArabic) "لا يوجد حساب مالي نشط" else "No active financial account", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    val amount = parsedAmount
                    if (amount == null || !amount.isFinite() || amount <= 0.0) {
                        validationError = if (isArabic) "أدخل مبلغًا أكبر من صفر" else "Enter an amount greater than zero"
                    } else if (!validAccount) {
                        validationError = if (isArabic) "اختر حسابًا ماليًا نشطًا" else "Select an active financial account"
                    } else {
                        validationError = null
                        onConfirm(amount, selectedAccount, notesText.trim()) { result ->
                            if (result.isFailure) validationError = result.exceptionOrNull()?.message ?: if (isArabic) "فشل تسجيل الدفعة" else "Failed to record payment"
                        }
                    }
                }
            ) {
                Text(if (isSubmitting) (if (isArabic) "جارٍ الحفظ..." else "Saving...") else (if (isArabic) "تأكيد" else "Confirm"))
            }
        }
    )
}

@Composable
fun LinkOrCreateSupplierItemDialog(
    allProducts: List<ProductItem>,
    linkedProductIds: Set<String>,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onLinkExisting: (ProductItem) -> Unit,
    onCreateNew: (name: String, unit: String, cost: Double, category: String, isRawMaterial: Boolean) -> Unit
) {
    var tabIndex by remember { mutableStateOf(0) }
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
                    val unlinked = allProducts.filter { it.id !in linkedProductIds }
                    if (unlinked.isEmpty()) {
                        Text(if (isArabic) "جميع الأصناف مربوطة مسبقاً بهذا المورد" else "All items are already linked")
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(unlinked) { prod ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { onLinkExisting(prod) }.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(prod.name, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                } else {
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
                    val cost = costText.toDoubleOrNull() ?: 0.0
                    if (nameText.isNotBlank()) onCreateNew(nameText, unitText, cost, if (isArabic) "مواد موردين" else "Supplier Items", isRawMaterial)
                }) {
                    Text(if (isArabic) "حفظ" else "Save")
                }
            } else {
                TextButton(onClick = onDismiss) { Text(if (isArabic) "إغلاق" else "Close") }
            }
        }
    )
}
