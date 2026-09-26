package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accounting.SupplierBalanceSummary
import com.example.accounting.SupplierLedgerEntry
import com.example.accounting.SupplierLedgerEntryType
import com.example.data.db.Purchase
import com.example.data.db.PurchaseReturn
import com.example.data.db.Supplier
import com.example.data.db.SupplierPayment
import com.example.model.AppCurrency
import com.example.model.LanguageMode
import com.example.model.ProductItem
import com.example.model.PurchaseLineRequest
import com.example.model.PurchaseResult
import com.example.ui.theme.GeoOutline
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusRedBg
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SupplierPurchasesSection(
    suppliers: List<Supplier>,
    purchases: List<Purchase>,
    supplierPayments: List<SupplierPayment>,
    products: List<ProductItem>,
    languageMode: LanguageMode,
    onAddSupplier: (name: String, phone: String, address: String?, notes: String?, onComplete: (Result<Supplier>) -> Unit) -> Unit,
    onRecordPurchase: (supplierId: String, lines: List<PurchaseLineRequest>, paidAmount: Double, financialAccountId: String?, notes: String?, date: String, onComplete: (Result<PurchaseResult>) -> Unit) -> Unit,
    onRecordSupplierPayment: (supplierId: String, amount: Double, date: String, financialAccountId: String?, notes: String?, onComplete: (Result<SupplierPayment>) -> Unit) -> Unit,
    onGetSupplierBalance: suspend (supplierId: String) -> SupplierBalanceSummary,
    onGetSupplierStatement: suspend (supplierId: String) -> List<SupplierLedgerEntry>,
    onRecordPurchaseReturn: ((purchaseId: String, amount: Double, reason: String, date: String, onComplete: (Result<PurchaseReturn>) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val currency = AppCurrency.SYMBOL
    val coroutineScope = rememberCoroutineScope()

    var selectedSupplierId by remember { mutableStateOf<String?>(suppliers.firstOrNull()?.id) }
    val selectedSupplier = suppliers.firstOrNull { it.id == selectedSupplierId } ?: suppliers.firstOrNull()

    var balanceSummary by remember { mutableStateOf<SupplierBalanceSummary?>(null) }
    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var showRecordPurchaseDialog by remember { mutableStateOf(false) }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var showRecordReturnDialog by remember { mutableStateOf(false) }
    var purchaseToReturn by remember { mutableStateOf<Purchase?>(null) }
    var showStatementDialog by remember { mutableStateOf(false) }
    var statementEntries by remember { mutableStateOf<List<SupplierLedgerEntry>>(emptyList()) }

    fun refreshBalance() {
        val sId = selectedSupplier?.id ?: return
        coroutineScope.launch {
            balanceSummary = onGetSupplierBalance(sId)
        }
    }

    LaunchedEffect(selectedSupplier?.id, purchases.size, supplierPayments.size) {
        refreshBalance()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("supplier_purchases_section"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- SUPPLIERS SELECTION ROW ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isArabic) "الموردون" else "Suppliers",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(
                onClick = { showAddSupplierDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("add_supplier_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = if (isArabic) "مورد جديد" else "New Supplier")
            }
        }

        if (suppliers.isEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Default.Business, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isArabic) "لا يوجد موردون مسجلون بعد" else "No suppliers registered yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("suppliers_list_row")
            ) {
                items(suppliers) { sup ->
                    val isSelected = (sup.id == (selectedSupplier?.id ?: ""))
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedSupplierId = sup.id
                            refreshBalance()
                        },
                        label = { Text(sup.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GeoPrimary.copy(alpha = 0.15f),
                            selectedLabelColor = GeoPrimary
                        ),
                        modifier = Modifier.testTag("supplier_chip_${sup.id}")
                    )
                }
            }
        }

        // --- SELECTED SUPPLIER OVERVIEW CARD ---
        if (selectedSupplier != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = GeoOutlineVariant, shape = RoundedCornerShape(16.dp))
                    .testTag("selected_supplier_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = selectedSupplier.name,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (selectedSupplier.phone.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = selectedSupplier.phone,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        val bal = balanceSummary?.balance ?: 0.0
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (bal > 0.001) StatusRedBg else StatusGreenBg,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isArabic) "مستحق للمورد" else "Payable",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (bal > 0.001) StatusRed else StatusGreen
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2f %s", bal, currency),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (bal > 0.001) StatusRed else StatusGreen
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = GeoOutlineVariant)

                    // Summary statistics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isArabic) "إجمالي المشتريات" else "Total Purchases",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(Locale.US, "%.2f %s", balanceSummary?.totalPurchases ?: 0.0, currency),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isArabic) "إجمالي المدفوعات" else "Total Paid",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(Locale.US, "%.2f %s", balanceSummary?.totalPayments ?: 0.0, currency),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ACTION BUTTONS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showRecordPurchaseDialog = true },
                            modifier = Modifier.weight(1f).testTag("btn_new_purchase"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isArabic) "فاتورة شراء" else "Purchase", fontSize = 13.sp)
                        }

                        Button(
                            onClick = { showRecordPaymentDialog = true },
                            modifier = Modifier.weight(1f).testTag("btn_supplier_payment"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
                        ) {
                            Icon(imageVector = Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isArabic) "سداد مورد" else "Payment", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    statementEntries = onGetSupplierStatement(selectedSupplier.id)
                                    showStatementDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("btn_supplier_statement"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isArabic) "كشف حساب" else "Statement", fontSize = 13.sp)
                        }
                    }
                }
            }

            // --- RECENT PURCHASES FOR THIS SUPPLIER ---
            val supplierPurchases = remember(purchases, selectedSupplier.id) {
                purchases.filter { it.supplierId == selectedSupplier.id }
            }

            Text(
                text = if (isArabic) "سجل فواتير المشتريات (${supplierPurchases.size})" else "Purchase Invoices (${supplierPurchases.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (supplierPurchases.isEmpty()) {
                Text(
                    text = if (isArabic) "لا توجد فواتير مشتريات لهذا المورد" else "No purchase invoices for this supplier",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).testTag("supplier_purchases_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(supplierPurchases) { pur ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth().testTag("purchase_card_${pur.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = pur.invoiceNumber,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = pur.purchaseDate,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format(Locale.US, "%.2f %s", pur.totalAmount, currency),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = when (pur.paymentStatus) {
                                            "PAID" -> if (isArabic) "مدفوع بالكامل" else "Paid"
                                            "PARTIAL" -> if (isArabic) "مدفوع جزئياً" else "Partial"
                                            else -> if (isArabic) "آجل (غير مدفوع)" else "Credit (Unpaid)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (pur.paymentStatus) {
                                            "PAID" -> StatusGreen
                                            "PARTIAL" -> GeoPrimary
                                            else -> StatusRed
                                        }
                                    )
                                    if (onRecordPurchaseReturn != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        OutlinedButton(
                                            onClick = {
                                                purchaseToReturn = pur
                                                showRecordReturnDialog = true
                                            },
                                            modifier = Modifier.testTag("btn_return_purchase_${pur.id}"),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (isArabic) "مرتجع" else "Return",
                                                style = MaterialTheme.typography.labelSmall
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

    // =========================================================================
    // DIALOGS
    // =========================================================================

    // 1. ADD SUPPLIER DIALOG
    if (showAddSupplierDialog) {
        var sName by remember { mutableStateOf("") }
        var sPhone by remember { mutableStateOf("") }
        var sAddress by remember { mutableStateOf("") }
        var sNotes by remember { mutableStateOf("") }
        var sError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddSupplierDialog = false },
            title = { Text(text = if (isArabic) "إضافة مورد جديد" else "Add New Supplier") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (sError != null) {
                        Text(text = sError!!, color = StatusRed, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = sName,
                        onValueChange = { sName = it; sError = null },
                        label = { Text(if (isArabic) "اسم المورد *" else "Supplier Name *") },
                        modifier = Modifier.fillMaxWidth().testTag("input_supplier_name")
                    )
                    OutlinedTextField(
                        value = sPhone,
                        onValueChange = { sPhone = it },
                        label = { Text(if (isArabic) "رقم الهاتف" else "Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("input_supplier_phone")
                    )
                    OutlinedTextField(
                        value = sAddress,
                        onValueChange = { sAddress = it },
                        label = { Text(if (isArabic) "العنوان" else "Address") },
                        modifier = Modifier.fillMaxWidth().testTag("input_supplier_address")
                    )
                    OutlinedTextField(
                        value = sNotes,
                        onValueChange = { sNotes = it },
                        label = { Text(if (isArabic) "ملاحظات" else "Notes") },
                        modifier = Modifier.fillMaxWidth().testTag("input_supplier_notes")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sName.trim().isBlank()) {
                            sError = if (isArabic) "اسم المورد مطلوب" else "Supplier name is required"
                            return@Button
                        }
                        onAddSupplier(sName, sPhone, sAddress, sNotes) { result ->
                            if (result.isSuccess) {
                                selectedSupplierId = result.getOrNull()?.id
                                showAddSupplierDialog = false
                            } else {
                                sError = result.exceptionOrNull()?.message
                            }
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_add_supplier")
                ) {
                    Text(if (isArabic) "حفظ" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSupplierDialog = false }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    // 2. RECORD PURCHASE DIALOG
    if (showRecordPurchaseDialog && selectedSupplier != null) {
        val lines = remember { mutableStateListOf<PurchaseLineRequest>() }
        var currentProductName by remember { mutableStateOf("") }
        var currentSelectedProduct by remember { mutableStateOf<ProductItem?>(null) }
        var currentQtyText by remember { mutableStateOf("1") }
        var currentCostText by remember { mutableStateOf("") }
        var paidAmountText by remember { mutableStateOf("0") }
        var purchaseNotes by remember { mutableStateOf("") }
        var purchaseError by remember { mutableStateOf<String?>(null) }

        val totalAmount = lines.sumOf { it.quantity * it.unitCost }
        val paidAmount = paidAmountText.toDoubleOrNull() ?: 0.0
        val creditAmount = (totalAmount - paidAmount).coerceAtLeast(0.0)

        AlertDialog(
            onDismissRequest = { showRecordPurchaseDialog = false },
            title = {
                Text(
                    text = if (isArabic) "تسجيل فاتورة مشتريات: ${selectedSupplier.name}"
                    else "Record Purchase: ${selectedSupplier.name}"
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (purchaseError != null) {
                        Text(text = purchaseError!!, color = StatusRed, style = MaterialTheme.typography.bodySmall)
                    }

                    // Item addition section
                    Text(
                        text = if (isArabic) "إضافة صنف للفاتورة" else "Add Line Item",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    // Product quick pick or text
                    if (products.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(products.take(6)) { p ->
                                FilterChip(
                                    selected = currentSelectedProduct?.id == p.id,
                                    onClick = {
                                        currentSelectedProduct = p
                                        currentProductName = p.name
                                        if (currentCostText.isBlank() && p.costPrice > 0.0) {
                                            currentCostText = p.costPrice.toString()
                                        }
                                    },
                                    label = { Text(p.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = currentProductName,
                        onValueChange = {
                            currentProductName = it
                            currentSelectedProduct = null
                        },
                        label = { Text(if (isArabic) "اسم الصنف *" else "Item Name *") },
                        modifier = Modifier.fillMaxWidth().testTag("input_purchase_item_name")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = currentQtyText,
                            onValueChange = { currentQtyText = it },
                            label = { Text(if (isArabic) "الكمية" else "Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_purchase_qty")
                        )
                        OutlinedTextField(
                            value = currentCostText,
                            onValueChange = { currentCostText = it },
                            label = { Text(if (isArabic) "سعر التكلفة" else "Unit Cost") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("input_purchase_cost")
                        )
                    }

                    Button(
                        onClick = {
                            val qty = currentQtyText.toIntOrNull() ?: 0
                            val cost = currentCostText.toDoubleOrNull() ?: 0.0
                            if (currentProductName.isBlank()) {
                                purchaseError = if (isArabic) "اسم الصنف مطلوب" else "Item name required"
                                return@Button
                            }
                            if (qty <= 0) {
                                purchaseError = if (isArabic) "الكمية يجب أن تكون أكبر من صفر" else "Quantity must be > 0"
                                return@Button
                            }
                            if (cost <= 0.0) {
                                purchaseError = if (isArabic) "سعر التكلفة يجب أن يكون أكبر من صفر" else "Cost must be > 0"
                                return@Button
                            }
                            lines.add(
                                PurchaseLineRequest(
                                    productId = currentSelectedProduct?.id,
                                    productNameSnapshot = currentProductName.trim(),
                                    quantity = qty,
                                    unitCost = cost
                                )
                            )
                            currentProductName = ""
                            currentSelectedProduct = null
                            currentQtyText = "1"
                            currentCostText = ""
                            purchaseError = null
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_add_purchase_line"),
                        colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isArabic) "إضافة الصنف" else "Add Line")
                    }

                    // Added lines list
                    if (lines.isNotEmpty()) {
                        Text(
                            text = if (isArabic) "الأصناف المضافة (${lines.size}):" else "Added Items (${lines.size}):",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        lines.forEachIndexed { idx, line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${line.productNameSnapshot} x${line.quantity} = ${line.quantity * line.unitCost} $currency",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                IconButton(onClick = { lines.removeAt(idx) }, modifier = Modifier.size(24.dp)) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = StatusRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }

                    // Financial Accounting Preview
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "${if (isArabic) "إجمالي الفاتورة" else "Total"}: $totalAmount $currency",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = paidAmountText,
                                onValueChange = { paidAmountText = it },
                                label = { Text(if (isArabic) "المدفوع نقداً للمورد" else "Paid Amount") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth().testTag("input_purchase_paid_amount")
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• ${if (isArabic) "زيادة قيمة المخزون" else "Inventory Value"}: +$totalAmount",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusGreen
                            )
                            Text(
                                text = "• ${if (isArabic) "المستحق للمورد (آجل)" else "Supplier Payable"}: +$creditAmount",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (creditAmount > 0) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• ${if (isArabic) "الخصم من الخزينة/الحساب" else "Cash Deducted"}: -$paidAmount",
                                style = MaterialTheme.typography.labelSmall,
                                color = GeoPrimary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = purchaseNotes,
                        onValueChange = { purchaseNotes = it },
                        label = { Text(if (isArabic) "ملاحظات الفاتورة" else "Notes") },
                        modifier = Modifier.fillMaxWidth().testTag("input_purchase_notes")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (lines.isEmpty()) {
                            purchaseError = if (isArabic) "يجب إضافة صنف واحد على الأقل" else "At least one item required"
                            return@Button
                        }
                        if (paidAmount < 0.0) {
                            purchaseError = if (isArabic) "المبلغ المدفوع غير صحيح" else "Invalid paid amount"
                            return@Button
                        }
                        if (paidAmount > totalAmount + 0.001) {
                            purchaseError = if (isArabic) "المدفوع لا يمكن أن يتجاوز إجمالي الفاتورة" else "Paid cannot exceed total"
                            return@Button
                        }
                        onRecordPurchase(
                            selectedSupplier.id,
                            lines.toList(),
                            paidAmount,
                            null,
                            purchaseNotes.takeIf { it.isNotBlank() },
                            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        ) { res ->
                            if (res.isSuccess) {
                                showRecordPurchaseDialog = false
                                refreshBalance()
                            } else {
                                purchaseError = res.exceptionOrNull()?.message
                            }
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_purchase")
                ) {
                    Text(if (isArabic) "تأكيد وحفظ الفاتورة" else "Record Purchase")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecordPurchaseDialog = false }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    // 3. RECORD SUPPLIER PAYMENT DIALOG
    if (showRecordPaymentDialog && selectedSupplier != null) {
        var pAmountText by remember { mutableStateOf("") }
        var pNotes by remember { mutableStateOf("") }
        var pError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showRecordPaymentDialog = false },
            title = {
                Text(
                    text = if (isArabic) "تسجيل سداد للمورد: ${selectedSupplier.name}"
                    else "Supplier Payment: ${selectedSupplier.name}"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (pError != null) {
                        Text(text = pError!!, color = StatusRed, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "${if (isArabic) "الرصيد المستحق حالياً" else "Current Payable"}: ${String.format(Locale.US, "%.2f %s", balanceSummary?.balance ?: 0.0, currency)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = StatusRed
                    )
                    OutlinedTextField(
                        value = pAmountText,
                        onValueChange = { pAmountText = it; pError = null },
                        label = { Text(if (isArabic) "مبلغ الدفعة *" else "Payment Amount *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("input_supplier_payment_amount")
                    )
                    OutlinedTextField(
                        value = pNotes,
                        onValueChange = { pNotes = it },
                        label = { Text(if (isArabic) "ملاحظات السداد" else "Notes") },
                        modifier = Modifier.fillMaxWidth().testTag("input_supplier_payment_notes")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = pAmountText.toDoubleOrNull() ?: 0.0
                        if (amt <= 0.0) {
                            pError = if (isArabic) "المبلغ يجب أن يكون أكبر من صفر" else "Amount must be > 0"
                            return@Button
                        }
                        onRecordSupplierPayment(
                            selectedSupplier.id,
                            amt,
                            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            null,
                            pNotes.takeIf { it.isNotBlank() }
                        ) { res ->
                            if (res.isSuccess) {
                                showRecordPaymentDialog = false
                                refreshBalance()
                            } else {
                                pError = res.exceptionOrNull()?.message
                            }
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_supplier_payment"),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
                ) {
                    Text(if (isArabic) "تأكيد السداد" else "Confirm Payment")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecordPaymentDialog = false }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    // 4. SUPPLIER STATEMENT DIALOG
    if (showStatementDialog && selectedSupplier != null) {
        AlertDialog(
            onDismissRequest = { showStatementDialog = false },
            title = {
                Text(
                    text = if (isArabic) "كشف حساب: ${selectedSupplier.name}"
                    else "Statement: ${selectedSupplier.name}"
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Balance banner
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = if (isArabic) "إجمالي المشتريات" else "Purchases", style = MaterialTheme.typography.labelSmall)
                                Text(text = String.format(Locale.US, "%.2f", balanceSummary?.totalPurchases ?: 0.0), fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text(text = if (isArabic) "إجمالي السداد" else "Payments", style = MaterialTheme.typography.labelSmall)
                                Text(text = String.format(Locale.US, "%.2f", balanceSummary?.totalPayments ?: 0.0), fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text(text = if (isArabic) "المتبقي" else "Payable", style = MaterialTheme.typography.labelSmall, color = StatusRed)
                                Text(text = String.format(Locale.US, "%.2f", balanceSummary?.balance ?: 0.0), fontWeight = FontWeight.Bold, color = StatusRed)
                            }
                        }
                    }

                    if (statementEntries.isEmpty()) {
                        Text(
                            text = if (isArabic) "لا توجد حركات في كشف الحساب" else "No ledger transactions found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(statementEntries) { entry ->
                                Card(
                                    shape = RoundedCornerShape(6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth().border(0.5.dp, GeoOutlineVariant, RoundedCornerShape(6.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = when (entry.entryType) {
                                                    SupplierLedgerEntryType.PURCHASE -> if (isArabic) "فاتورة مشتريات" else "Purchase"
                                                    SupplierLedgerEntryType.SUPPLIER_PAYMENT -> if (isArabic) "سداد دفعة" else "Payment"
                                                    SupplierLedgerEntryType.PURCHASE_RETURN -> if (isArabic) "مرتجع مشتريات" else "Return"
                                                    SupplierLedgerEntryType.ADJUSTMENT -> if (isArabic) "تسوية حساب" else "Adjustment"
                                                    SupplierLedgerEntryType.OPENING_BALANCE -> if (isArabic) "رصيد افتتاحي" else "Opening Balance"
                                                },
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "${entry.date} • ${entry.description}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            if (entry.credit > 0) {
                                                Text(
                                                    text = "+${String.format(Locale.US, "%.2f", entry.credit)}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = StatusRed
                                                )
                                            }
                                            if (entry.debit > 0) {
                                                Text(
                                                    text = "-${String.format(Locale.US, "%.2f", entry.debit)}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = StatusGreen
                                                )
                                            }
                                            Text(
                                                text = "${if (isArabic) "الرصيد" else "Bal"}: ${String.format(Locale.US, "%.2f", entry.runningBalance)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showStatementDialog = false }) {
                    Text(if (isArabic) "إغلاق" else "Close")
                }
            }
        )
    }

    // 5. RECORD PURCHASE RETURN DIALOG
    if (showRecordReturnDialog && purchaseToReturn != null) {
        val pur = purchaseToReturn!!
        var returnAmountText by remember { mutableStateOf(pur.totalAmount.toString()) }
        var returnReason by remember { mutableStateOf("") }
        var returnError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showRecordReturnDialog = false; purchaseToReturn = null },
            title = {
                Text(text = if (isArabic) "تسجيل مرتجع مشتريات" else "Record Purchase Return")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (returnError != null) {
                        Text(text = returnError!!, color = StatusRed, style = MaterialTheme.typography.bodySmall)
                    }

                    Text(
                        text = "${if (isArabic) "فاتورة" else "Invoice"}: ${pur.invoiceNumber} (${String.format(Locale.US, "%.2f", pur.totalAmount)} $currency)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    OutlinedTextField(
                        value = returnAmountText,
                        onValueChange = { returnAmountText = it; returnError = null },
                        label = { Text(if (isArabic) "مبلغ المرتجع *" else "Return Amount *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("input_purchase_return_amount")
                    )

                    OutlinedTextField(
                        value = returnReason,
                        onValueChange = { returnReason = it; returnError = null },
                        label = { Text(if (isArabic) "سبب المرتجع (إجباري) *" else "Reason (Required) *") },
                        modifier = Modifier.fillMaxWidth().testTag("input_purchase_return_reason")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = returnAmountText.toDoubleOrNull() ?: 0.0
                        if (amount <= 0.0) {
                            returnError = if (isArabic) "مبلغ المرتجع يجب أن يكون أكبر من صفر" else "Amount must be > 0"
                            return@Button
                        }
                        if (returnReason.trim().isBlank()) {
                            returnError = if (isArabic) "سبب المرتجع مطلوب" else "Reason is required"
                            return@Button
                        }
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        onRecordPurchaseReturn?.invoke(
                            pur.id,
                            amount,
                            returnReason.trim(),
                            today
                        ) { result ->
                            if (result.isSuccess) {
                                refreshBalance()
                                showRecordReturnDialog = false
                                purchaseToReturn = null
                            } else {
                                returnError = result.exceptionOrNull()?.message ?: "Error recording return"
                            }
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_purchase_return")
                ) {
                    Text(if (isArabic) "تأكيد المرتجع" else "Confirm Return")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecordReturnDialog = false; purchaseToReturn = null }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }
}
