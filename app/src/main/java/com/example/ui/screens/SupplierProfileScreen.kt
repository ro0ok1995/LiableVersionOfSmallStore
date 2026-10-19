package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accounting.SupplierBalanceSummary
import com.example.accounting.SupplierLedgerCalculator
import com.example.data.db.Purchase
import com.example.data.db.PurchaseReturn
import com.example.data.db.Supplier
import com.example.data.db.SupplierPayment
import com.example.model.AppCurrency
import com.example.model.LanguageMode
import com.example.ui.theme.statusGreen
import com.example.ui.theme.statusGreenContainer
import com.example.ui.theme.statusRed
import com.example.ui.theme.statusRedContainer
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierProfileScreen(
    supplier: Supplier?,
    suppliers: List<Supplier>,
    purchases: List<Purchase>,
    payments: List<SupplierPayment>,
    returns: List<PurchaseReturn> = emptyList(),
    languageMode: LanguageMode,
    onBack: () -> Unit,
    onEdit: (Supplier) -> Unit,
    onPurchaseAction: (Supplier) -> Unit,
    onPaymentAction: (Supplier) -> Unit,
    onReturnAction: (Supplier) -> Unit,
    onStatementAction: (Supplier) -> Unit,
    onArchive: (Supplier) -> Unit,
    onGetSupplierBalance: suspend (String) -> SupplierBalanceSummary,
    onSupplierSelected: (Supplier?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ar = languageMode == LanguageMode.ARABIC
    val current = supplier?.let { selected -> suppliers.firstOrNull { it.id == selected.id } ?: selected }
    var selectorExpanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showArchiveConfirmDialog by remember { mutableStateOf(false) }
    var invoiceFilter by remember { mutableIntStateOf(0) }
    var invoiceSearch by remember { mutableStateOf("") }
    var editName by remember(current?.id) { mutableStateOf(current?.name.orEmpty()) }
    var editPhone by remember(current?.id) { mutableStateOf(current?.phone.orEmpty()) }
    var editAddress by remember(current?.id) { mutableStateOf(current?.address.orEmpty()) }
    var editNotes by remember(current?.id) { mutableStateOf(current?.notes.orEmpty()) }

    BackHandler { onBack() }

    var balance by remember(current?.id) { mutableStateOf<SupplierBalanceSummary?>(null) }
    var balanceLoading by remember(current?.id) { mutableStateOf(false) }
    var balanceLoadFailed by remember(current?.id) { mutableStateOf(false) }
    var balanceRetryKey by remember(current?.id) { mutableIntStateOf(0) }
    LaunchedEffect(current?.id, purchases, payments, returns, balanceRetryKey) {
        val selected = current
        if (selected != null) {
            balanceLoading = true
            balanceLoadFailed = false
            val result = runCatching { onGetSupplierBalance(selected.id) }
            balance = result.getOrNull()
            balanceLoadFailed = result.isFailure
            balanceLoading = false
        } else {
            balance = null
            balanceLoading = false
            balanceLoadFailed = false
        }
    }
    val invoices = remember(current?.id, purchases, returns, invoiceFilter, invoiceSearch) {
        val selectedId = current?.id
        if (selectedId == null) emptyList() else purchases.filter { it.supplierId == selectedId }
            .filter { invoiceSearch.isBlank() || it.invoiceNumber.contains(invoiceSearch.trim(), ignoreCase = true) }
            .filter { invoice ->
                val linkedReturns = returns.filter { it.purchaseId == invoice.id && it.status != "REVERSED" }.sumOf { it.amount }
                val remaining = (invoice.creditAmount - linkedReturns).coerceAtLeast(0.0)
                when (invoiceFilter) { 1 -> remaining > 0.0001; 2 -> remaining <= 0.0001; else -> true }
            }
            .sortedWith(compareByDescending<Purchase> { it.purchaseDate }.thenByDescending { it.createdAt })
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("supplier_profile_screen"),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("supplier_profile_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (ar) "رجوع" else "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (ar) "ملف المورد" else "Supplier Profile", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("supplier_profile_title"))
                        if (current != null) Text(current.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = {
                        current?.let {
                            editName = it.name; editPhone = it.phone; editAddress = it.address.orEmpty(); editNotes = it.notes.orEmpty(); showEditDialog = true
                        }
                    }, enabled = current != null, modifier = Modifier.testTag("supplier_profile_edit_button")) {
                        Icon(Icons.Default.Edit, contentDescription = if (ar) "تعديل المورد" else "Edit supplier", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Supplier selector follows the customer profile's placement and hierarchy.
            Column {
                Text(if (ar) "اختر المورد" else "Select Supplier", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                ExposedDropdownMenuBox(expanded = selectorExpanded, onExpandedChange = { selectorExpanded = it }, modifier = Modifier.fillMaxWidth().testTag("supplier_profile_selector")) {
                    OutlinedTextField(
                        value = current?.let { name -> if (name.phone.isNullOrBlank()) name.name else "${name.name} • ${name.phone}" }.orEmpty(),
                        onValueChange = {}, readOnly = true, singleLine = true,
                        placeholder = { Text(if (ar) "اختر مورداً لعرض الملف..." else "Select supplier to view profile...") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = selectorExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor().testTag("supplier_profile_selector_field"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = selectorExpanded, onDismissRequest = { selectorExpanded = false }) {
                        suppliers.filter { !it.isArchived }.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(option.name, fontWeight = FontWeight.SemiBold)
                                        option.phone?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                },
                                onClick = { selectorExpanded = false; if (option.id != current?.id) onSupplierSelected(option) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                            )
                        }
                    }
                }
            }

            if (current == null) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp), modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("supplier_profile_empty_state")) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = CircleShape, modifier = Modifier.size(64.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(if (ar) "يرجى اختيار مورد" else "Please select a supplier", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text(if (ar) "اختر مورداً مسجلاً من القائمة أعلاه لعرض بياناته ورصيده وفواتير مشترياته." else "Choose a registered supplier above to view their details, balance and purchase invoices.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val liveBalance = balance?.balance ?: 0.0
                val hasPayable = liveBalance > 0.0001
                val hasAdvance = liveBalance < -0.0001
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), modifier = Modifier.fillMaxWidth().testTag("supplier_info_card")) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = CircleShape, modifier = Modifier.size(52.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text(current.name.take(1), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary) }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(current.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("supplier_profile_name"))
                                if (!current.phone.isNullOrBlank()) {
                                    Spacer(Modifier.height(3.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(current.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("supplier_profile_phone"))
                                    }
                                }
                                current.address?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                            Surface(color = when { hasPayable -> MaterialTheme.colorScheme.statusRedContainer; hasAdvance -> MaterialTheme.colorScheme.statusGreenContainer; else -> MaterialTheme.colorScheme.surfaceVariant }, shape = RoundedCornerShape(8.dp), modifier = Modifier.testTag("supplier_profile_status_badge")) {
                                Text(when { hasPayable -> if (ar) "مستحق للمورد" else "Payable"; hasAdvance -> if (ar) "للمحل رصيد لدى المورد" else "Store advance"; else -> if (ar) "خالص" else "Settled" }, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = when { hasPayable -> MaterialTheme.colorScheme.statusRed; hasAdvance -> MaterialTheme.colorScheme.statusGreen; else -> MaterialTheme.colorScheme.statusGreen }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(if (ar) "الرصيد الحالي" else "Current balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                                if (balanceLoading) {
                                    Text(if (ar) "جارٍ تحميل الرصيد…" else "Loading balance…", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("supplier_profile_balance_loading"))
                                } else if (balanceLoadFailed || balance == null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (ar) "تعذر تحميل الرصيد" else "Could not load balance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("supplier_profile_balance_error"))
                                        TextButton(onClick = { balanceRetryKey++ }, contentPadding = PaddingValues(0.dp)) { Text(if (ar) "إعادة المحاولة" else "Retry") }
                                    }
                                } else {
                                    Text(money(liveBalance), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp), color = when { hasPayable -> MaterialTheme.colorScheme.statusRed; hasAdvance -> MaterialTheme.colorScheme.statusGreen; else -> MaterialTheme.colorScheme.statusGreen }, modifier = Modifier.testTag("supplier_profile_balance"))
                                    Spacer(Modifier.height(3.dp))
                                    Text(when { hasPayable -> if (ar) "مستحق للمورد على المحل" else "The store owes the supplier"; hasAdvance -> if (ar) "للمحل رصيد مقدم لدى المورد" else "The store has an advance with supplier"; else -> if (ar) "خالص" else "Settled" }, style = MaterialTheme.typography.labelSmall, color = if (kotlin.math.abs(liveBalance) <= 0.001) MaterialTheme.colorScheme.statusGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (ar) "إجمالي المشتريات" else "Total purchases", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                                Text(money(balance?.totalPurchases ?: 0.0), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("supplier_profile_total_purchases"))
                            }
                        }
                    }
                }

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (ar) "العمليات الأساسية" else "Basic Actions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 4.dp).testTag("supplier_basic_actions_title"))
                    SupplierProfileActionButton(Icons.Default.ShoppingBag, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = .12f), if (ar) "تسجيل فاتورة شراء" else "Record Purchase Invoice", if (ar) "تسجيل مشتريات جديدة من هذا المورد" else "Record a new purchase from this supplier", "supplier_profile_add_purchase") { onPurchaseAction(current) }
                    SupplierProfileActionButton(Icons.Default.ReceiptLong, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondary.copy(alpha = .12f), if (ar) "عرض كشف الحساب" else "View Account Statement", if (ar) "عرض الحركات والرصيد والدفعات والمرتجعات" else "Review account activity, balance, payments and returns", "supplier_profile_statement") { onStatementAction(current) }
                    SupplierProfileActionButton(Icons.Default.Payments, MaterialTheme.colorScheme.statusGreen, MaterialTheme.colorScheme.statusGreenContainer, if (ar) "تسجيل دفعة للمورد" else "Record Supplier Payment", if (ar) "تسجيل دفعة لتسوية المستحقات" else "Record a payment toward supplier payables", "supplier_profile_payment") { onPaymentAction(current) }
                    SupplierProfileActionButton(Icons.Default.Undo, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiary.copy(alpha = .12f), if (ar) "تسجيل مرتجع شراء" else "Record Purchase Return", if (ar) "تسجيل البضائع المرتجعة لهذا المورد" else "Record goods returned to this supplier", "supplier_profile_return") { onReturnAction(current) }
                }

                // Archiving is a separate management card, intentionally placed below the four primary actions.
                if (!current.isArchived) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (ar) "إدارة المورد" else "Supplier Management",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 4.dp).testTag("supplier_management_actions_title")
                        )
                        SupplierProfileActionButton(
                            Icons.Default.Archive,
                            MaterialTheme.colorScheme.error,
                            MaterialTheme.colorScheme.errorContainer,
                            if (ar) "أرشفة المورد" else "Archive Supplier",
                            if (ar) "إخفاء المورد من القوائم النشطة مع الاحتفاظ بالفواتير والحساب" else "Remove from active lists while preserving invoices and account history",
                            "supplier_profile_archive"
                        ) { showArchiveConfirmDialog = true }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (ar) "فواتير المشتريات" else "Purchase Invoices", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("supplier_purchase_invoices_title"))
                    OutlinedTextField(value = invoiceSearch, onValueChange = { invoiceSearch = it }, modifier = Modifier.fillMaxWidth().testTag("supplier_invoice_search"), label = { Text(if (ar) "بحث برقم الفاتورة" else "Search invoice number") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                    Text(if (ar) "المتبقي هنا هو قيمة الفاتورة بعد المرتجعات المرتبطة بها؛ تُسجّل دفعات المورد على مستوى الحساب ولا يربطها النموذج الحالي بفاتورة بعينها." else "Invoice remaining reflects the invoice value after linked returns. Supplier payments are recorded at account level and are not linked to a specific invoice in the current model.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(if (ar) "الكل" else "All", if (ar) "غير المسددة" else "Unpaid / partial", if (ar) "المسددة" else "Paid").forEachIndexed { index, label ->
                            FilterChip(selected = invoiceFilter == index, onClick = { invoiceFilter = index }, label = { Text(label, fontSize = 12.sp) })
                        }
                    }
                    if (invoices.isEmpty()) Text(if (ar) "لا توجد فواتير مطابقة" else "No matching purchase invoices", modifier = Modifier.fillMaxWidth().padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    invoices.forEach { invoice ->
                        val linkedReturns = returns.filter { it.purchaseId == invoice.id && it.status != "REVERSED" }.sumOf { it.amount }
                        val remaining = (invoice.creditAmount - linkedReturns).coerceAtLeast(0.0)
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().testTag("supplier_invoice_${invoice.id}")) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(invoice.invoiceNumber, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(if (remaining <= 0.0001) { if (ar) "مسددة" else "Paid" } else if (invoice.paidAmount > 0.0001) { if (ar) "مسددة جزئياً" else "Partially paid" } else { if (ar) "غير مسددة" else "Unpaid" }, color = if (remaining <= 0.0001) MaterialTheme.colorScheme.statusGreen else MaterialTheme.colorScheme.statusRed, style = MaterialTheme.typography.labelMedium)
                                }
                                Text(invoice.purchaseDate, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${if (ar) "الإجمالي" else "Total"}: ${money(invoice.totalAmount)}", style = MaterialTheme.typography.bodySmall); Text("${if (ar) "المدفوع" else "Paid"}: ${money(invoice.paidAmount)}", style = MaterialTheme.typography.bodySmall) }
                                Text("${if (ar) "المتبقي المرتبط بالفاتورة بعد المرتجعات" else "Invoice-linked remaining after returns"}: ${money(remaining)}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && current != null) AlertDialog(
        onDismissRequest = { showEditDialog = false },
        title = { Text(if (ar) "تعديل بيانات المورد" else "Edit supplier", fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(editName, { editName = it }, label = { Text(if (ar) "اسم المورد" else "Supplier name") }, singleLine = true)
            OutlinedTextField(editPhone, { editPhone = it }, label = { Text(if (ar) "الهاتف" else "Phone") }, singleLine = true)
            OutlinedTextField(editAddress, { editAddress = it }, label = { Text(if (ar) "العنوان" else "Address") })
            OutlinedTextField(editNotes, { editNotes = it }, label = { Text(if (ar) "ملاحظات" else "Notes") })
        } },
        confirmButton = { Button(onClick = { if (editName.isNotBlank()) { onEdit(current.copy(name = editName.trim(), phone = editPhone.trim(), address = editAddress.trim().ifBlank { null }, notes = editNotes.trim().ifBlank { null })); showEditDialog = false } }, enabled = editName.isNotBlank()) { Text(if (ar) "حفظ" else "Save") } },
        dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text(if (ar) "إلغاء" else "Cancel") } }
    )

    if (showArchiveConfirmDialog && current != null) AlertDialog(
        onDismissRequest = { showArchiveConfirmDialog = false },
        title = { Text(if (ar) "تأكيد أرشفة المورد" else "Confirm supplier archive", fontWeight = FontWeight.Bold) },
        text = { Text(if (ar) "هل تريد أرشفة المورد ${current.name}؟ لن يُحذف سجل مشترياته أو حركات حسابه." else "Archive ${current.name}? Their purchase history and account transactions will be retained.") },
        confirmButton = { Button(onClick = { showArchiveConfirmDialog = false; onArchive(current) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.testTag("confirm_archive_supplier_button")) { Text(if (ar) "أرشفة المورد" else "Archive supplier") } },
        dismissButton = { TextButton(onClick = { showArchiveConfirmDialog = false }, modifier = Modifier.testTag("cancel_archive_supplier_button")) { Text(if (ar) "إلغاء" else "Cancel") } }
    )
}

@Composable
private fun SupplierProfileActionButton(
    icon: ImageVector,
    iconTint: Color,
    iconContainerColor: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).testTag(testTag)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = iconContainerColor, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }
    }
}

private fun money(value: Double): String = String.format(Locale.US, "%,.2f %s", value, AppCurrency.SYMBOL)
