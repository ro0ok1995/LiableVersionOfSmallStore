package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.Supplier
import com.example.accounting.SupplierBalanceSummary
import com.example.model.LanguageMode
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary

private enum class SupplierFilterTab { ACTIVE, PAYABLE, SETTLED, ARCHIVED }

/** Dedicated supplier master-data management screen. Layout intentionally mirrors CustomerManagementScreen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierManagementScreen(
    suppliers: List<Supplier>,
    languageMode: LanguageMode,
    onBackClick: () -> Unit,
    onAddSupplier: (name: String, phone: String) -> Unit,
    onAddSupplierWithOpeningBalance: (name: String, phone: String, openingAmount: Double?, openingDirection: String) -> Unit,
    onRecordOpeningBalance: (String, Double, String, (Result<Unit>) -> Unit) -> Unit,
    onUpdateSupplier: (Supplier) -> Unit,
    onGetSupplierBalance: suspend (String) -> SupplierBalanceSummary,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(SupplierFilterTab.ACTIVE) }
    var showAddDialog by remember { mutableStateOf(false) }
    var supplierToEdit by remember { mutableStateOf<Supplier?>(null) }
    var supplierToArchive by remember { mutableStateOf<Supplier?>(null) }
    var supplierToRestore by remember { mutableStateOf<Supplier?>(null) }
    var supplierForOpeningBalance by remember { mutableStateOf<Supplier?>(null) }

    val active = remember(suppliers) { suppliers.filterNot { it.isArchived } }
    val archived = remember(suppliers) { suppliers.filter { it.isArchived } }
    var balances by remember { mutableStateOf<Map<String, SupplierBalanceSummary>>(emptyMap()) }
    LaunchedEffect(suppliers) {
        balances = suppliers.associate { supplier ->
            supplier.id to runCatching { onGetSupplierBalance(supplier.id) }.getOrDefault(SupplierBalanceSummary(supplierId = supplier.id))
        }
    }
    val visible = remember(active, archived, selectedFilter, searchQuery, balances) {
        val pool = when (selectedFilter) {
            SupplierFilterTab.ACTIVE -> active
            SupplierFilterTab.PAYABLE -> active.filter { (balances[it.id]?.balance ?: 0.0) > 0.0001 }
            SupplierFilterTab.SETTLED -> active.filter { kotlin.math.abs(balances[it.id]?.balance ?: 0.0) <= 0.0001 }
            SupplierFilterTab.ARCHIVED -> archived
        }
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) pool else pool.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("supplier_management_screen")) {
        TopAppBar(
            title = {
                Column {
                    Text(if (isArabic) "إدارة الموردين" else "Supplier Management", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (isArabic) "${active.size} مورد نشط • ${archived.size} مؤرشف" else "${active.size} active • ${archived.size} archived", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick, modifier = Modifier.testTag("supplier_mgmt_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (isArabic) "رجوع" else "Back")
                }
            },
            actions = {
                Button(onClick = { showAddDialog = true }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), modifier = Modifier.padding(end = 8.dp).testTag("supplier_mgmt_add_button")) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (isArabic) "إضافة مورد" else "Add Supplier", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
        HorizontalDivider(color = GeoOutlineVariant, thickness = 1.dp)
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 10.dp)) {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth().testTag("supplier_mgmt_search_input"),
                placeholder = { Text(if (isArabic) "بحث عن مورد بالاسم أو الهاتف" else "Search suppliers by name or phone") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) TextButton(onClick = { searchQuery = "" }) { Text(if (isArabic) "مسح" else "Clear") } },
                singleLine = true, shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = GeoOutlineVariant)
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    SupplierFilterTab.ACTIVE to (if (isArabic) "النشطون (${active.size})" else "Active (${active.size})"),
                    SupplierFilterTab.PAYABLE to (if (isArabic) "لهم مستحقات" else "Payable"),
                    SupplierFilterTab.SETTLED to (if (isArabic) "خالص" else "Settled"),
                    SupplierFilterTab.ARCHIVED to (if (isArabic) "الأرشيف (${archived.size})" else "Archive (${archived.size})")
                ).forEach { (tab, label) ->
                    FilterChip(selected = selectedFilter == tab, onClick = { selectedFilter = tab }, label = { Text(label, fontSize = 11.sp) }, modifier = Modifier.weight(1f), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GeoPrimary.copy(alpha = .12f), selectedLabelColor = GeoPrimary))
                }
            }
        }
        HorizontalDivider(color = GeoOutlineVariant, thickness = 1.dp)
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (selectedFilter == SupplierFilterTab.ARCHIVED) Icons.Default.Archive else Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f), modifier = Modifier.size(48.dp))
                    Text(if (isArabic) if (selectedFilter == SupplierFilterTab.ARCHIVED) "لا يوجد موردون مؤرشفون" else if (searchQuery.isNotBlank()) "لا توجد نتائج مطابقة للبحث" else "لا يوجد موردون بعد" else if (selectedFilter == SupplierFilterTab.ARCHIVED) "No archived suppliers" else if (searchQuery.isNotBlank()) "No matching suppliers found" else "No suppliers yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible, key = { it.id }) { supplier ->
                    SupplierManagementCard(supplier, isArabic, balances[supplier.id]?.balance ?: 0.0, onEdit = { supplierToEdit = supplier }, onOpeningBalance = { supplierForOpeningBalance = supplier }, onArchive = { supplierToArchive = supplier }, onRestore = { supplierToRestore = supplier })
                }
            }
        }
    }

    if (showAddDialog) SupplierEditDialog(isArabic, null, onDismiss = { showAddDialog = false }, onConfirm = { name, phone, amount, direction -> onAddSupplierWithOpeningBalance(name, phone, amount, direction); showAddDialog = false })
    supplierToEdit?.let { supplier -> SupplierEditDialog(isArabic, supplier, onDismiss = { supplierToEdit = null }, onConfirm = { name, phone, _, _ -> onUpdateSupplier(supplier.copy(name = name.trim(), phone = phone.trim())); supplierToEdit = null }) }
    supplierForOpeningBalance?.let { supplier ->
        ExistingSupplierOpeningBalanceDialog(
            isArabic = isArabic, onDismiss = { supplierForOpeningBalance = null },
            onConfirm = { amount, direction ->
                onRecordOpeningBalance(supplier.id, amount, direction) { result ->
                    if (result.isSuccess) supplierForOpeningBalance = null
                }
            }
        )
    }
    supplierToArchive?.let { supplier ->
        AlertDialog(onDismissRequest = { supplierToArchive = null }, icon = { Icon(Icons.Default.Archive, null, tint = GeoPrimary) }, title = { Text(if (isArabic) "نقل إلى الأرشيف" else "Move to Archive", fontWeight = FontWeight.Bold) }, text = { Text(if (isArabic) "هل تريد أرشفة المورد «${supplier.name}»؟ ستبقى جميع السجلات المالية محفوظة." else "Archive supplier “${supplier.name}”? All financial records will be preserved.") }, confirmButton = { Button(onClick = { onUpdateSupplier(supplier.copy(isArchived = true)); supplierToArchive = null }) { Text(if (isArabic) "أرشفة" else "Archive") } }, dismissButton = { TextButton(onClick = { supplierToArchive = null }) { Text(if (isArabic) "إلغاء" else "Cancel") } })
    }
    supplierToRestore?.let { supplier ->
        AlertDialog(onDismissRequest = { supplierToRestore = null }, icon = { Icon(Icons.Default.Refresh, null, tint = GeoPrimary) }, title = { Text(if (isArabic) "استعادة المورد" else "Restore Supplier", fontWeight = FontWeight.Bold) }, text = { Text(if (isArabic) "هل تريد إعادة «${supplier.name}» إلى قائمة الموردين النشطين؟" else "Restore “${supplier.name}” to active suppliers?") }, confirmButton = { Button(onClick = { onUpdateSupplier(supplier.copy(isArchived = false)); supplierToRestore = null }, colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary)) { Text(if (isArabic) "استعادة" else "Restore") } }, dismissButton = { TextButton(onClick = { supplierToRestore = null }) { Text(if (isArabic) "إلغاء" else "Cancel") } })
    }
}

@Composable
private fun SupplierManagementCard(supplier: Supplier, isArabic: Boolean, balance: Double, onEdit: () -> Unit, onOpeningBalance: () -> Unit, onArchive: () -> Unit, onRestore: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().border(1.dp, GeoOutlineVariant, RoundedCornerShape(18.dp))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = if (supplier.isArchived) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color(0xFFDDF8E8), shape = RoundedCornerShape(9.dp)) {
                    Text(if (isArabic) if (supplier.isArchived) "مؤرشف" else "نشط" else if (supplier.isArchived) "Archived" else "Active", color = if (supplier.isArchived) MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.ui.graphics.Color(0xFF218B50), modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                Text(supplier.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            if (supplier.phone.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)); Text(supplier.phone, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .32f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isArabic) "الرصيد المستحق للمورد:" else "Amount payable:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("₪ ${String.format(java.util.Locale.US, "%.2f", balance)}", color = if (balance > 0.0001) androidx.compose.ui.graphics.Color(0xFFC0392B) else androidx.compose.ui.graphics.Color(0xFF218B50), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(14.dp)); HorizontalDivider(color = GeoOutlineVariant.copy(alpha = .55f)); Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!supplier.isArchived) OutlinedButton(onClick = onOpeningBalance, shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GeoOutlineVariant)) { Text(if (isArabic) "رصيد افتتاحي" else "Opening balance", fontSize = 12.sp) }
                OutlinedButton(onClick = if (supplier.isArchived) onRestore else onArchive, shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GeoOutlineVariant)) { Icon(if (supplier.isArchived) Icons.Default.Refresh else Icons.Default.Archive, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(if (supplier.isArchived) { if (isArabic) "استعادة" else "Restore" } else { if (isArabic) "أرشفة" else "Archive" }) }
                OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GeoOutlineVariant)) { Icon(Icons.Default.Edit, null, tint = GeoPrimary, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(if (isArabic) "تعديل المورد" else "Edit Supplier", color = GeoPrimary) }
            }
        }
    }
}

@Composable
private fun SupplierEditDialog(isArabic: Boolean, supplier: Supplier?, onDismiss: () -> Unit, onConfirm: (String, String, Double?, String) -> Unit) {
    var name by remember(supplier?.id) { mutableStateOf(supplier?.name.orEmpty()) }
    var phone by remember(supplier?.id) { mutableStateOf(supplier?.phone.orEmpty()) }
    var openingAmount by remember(supplier?.id) { mutableStateOf("") }
    var direction by remember(supplier?.id) { mutableStateOf("CREDIT") }
    var amountError by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (supplier == null) { if (isArabic) "إضافة مورد" else "Add Supplier" } else { if (isArabic) "تعديل المورد" else "Edit Supplier" }, fontWeight = FontWeight.Bold) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(if (isArabic) "اسم المورد" else "Supplier name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(if (isArabic) "رقم الهاتف" else "Phone number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
            if (supplier == null) {
                Text(if (isArabic) "الرصيد الافتتاحي (اختياري) — ₪" else "Opening balance (optional) — ₪", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(value = openingAmount, onValueChange = { openingAmount = it; amountError = false }, label = { Text(if (isArabic) "المبلغ" else "Amount") }, isError = amountError, supportingText = { if (amountError) Text(if (isArabic) "أدخل مبلغًا موجبًا مثل 150.25" else "Enter a positive amount such as 150.25") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("supplier_opening_balance_amount"))
                Text(if (isArabic) "اتجاه الرصيد" else "Balance direction", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = direction == "CREDIT", onClick = { direction = "CREDIT" }, label = { Text(if (isArabic) "المحل مدين للمورد" else "Store owes supplier") })
                    FilterChip(selected = direction == "DEBIT", onClick = { direction = "DEBIT" }, label = { Text(if (isArabic) "المورد مدين للمحل" else "Supplier owes store") })
                }
            }
        }
    }, confirmButton = { Button(onClick = {
        if (name.isBlank()) return@Button
        val amount = if (supplier != null || openingAmount.isBlank()) null else openingAmount.trim().replace(',', '.').toDoubleOrNull()
        if (supplier == null && openingAmount.isNotBlank() && (amount == null || !amount.isFinite() || amount <= 0.0)) { amountError = true; return@Button }
        onConfirm(name, phone, amount, direction)
    }, enabled = name.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary)) { Text(if (isArabic) "حفظ" else "Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text(if (isArabic) "إلغاء" else "Cancel") } })
}


@Composable
private fun ExistingSupplierOpeningBalanceDialog(isArabic: Boolean, onDismiss: () -> Unit, onConfirm: (Double, String) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("CREDIT") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (isArabic) "إضافة رصيد افتتاحي للمورد" else "Add supplier opening balance") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = amountText, onValueChange = { amountText = it; error = null }, label = { Text(if (isArabic) "المبلغ بالشيكل ₪" else "Amount in ILS ₪") }, isError = error != null, supportingText = { if (error != null) Text(error!!) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = direction == "CREDIT", onClick = { direction = "CREDIT" }, label = { Text(if (isArabic) "المحل مدين للمورد" else "Store owes supplier") })
                FilterChip(selected = direction == "DEBIT", onClick = { direction = "DEBIT" }, label = { Text(if (isArabic) "المورد مدين للمحل" else "Supplier owes store") })
            }
        }
    }, confirmButton = { Button(onClick = { val amount = amountText.trim().replace(',', '.').toDoubleOrNull(); if (amount == null || !amount.isFinite() || amount <= 0.0) error = if (isArabic) "أدخل مبلغًا موجبًا صحيحًا" else "Enter a valid positive amount" else onConfirm(amount, direction) }) { Text(if (isArabic) "حفظ الرصيد" else "Save balance") } }, dismissButton = { TextButton(onClick = onDismiss) { Text(if (isArabic) "إلغاء" else "Cancel") } })
}
