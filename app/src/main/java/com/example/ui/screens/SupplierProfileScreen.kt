package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier
) {
    val ar = languageMode == LanguageMode.ARABIC
    val current = supplier ?: suppliers.firstOrNull()
    var invoiceFilter by remember { mutableIntStateOf(0) }
    var invoiceSearch by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember(current?.id) { mutableStateOf(current?.name.orEmpty()) }
    var editPhone by remember(current?.id) { mutableStateOf(current?.phone.orEmpty()) }
    var editAddress by remember(current?.id) { mutableStateOf(current?.address.orEmpty()) }
    var editNotes by remember(current?.id) { mutableStateOf(current?.notes.orEmpty()) }
    if (current == null) {
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (ar) "المورد غير موجود" else "Supplier not found", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onBack) { Text(if (ar) "رجوع" else "Back") }
        }
        return
    }
    var balance by remember(current.id) { mutableStateOf(SupplierBalanceSummary(current.id)) }
    LaunchedEffect(current.id, purchases, payments, returns) {
        balance = runCatching { onGetSupplierBalance(current.id) }.getOrDefault(SupplierBalanceSummary(current.id))
    }
    val invoices = remember(current.id, purchases, returns, invoiceFilter, invoiceSearch) {
        purchases.filter { it.supplierId == current.id }
            .filter { invoiceSearch.isBlank() || it.invoiceNumber.contains(invoiceSearch.trim(), ignoreCase = true) }
            .filter { invoice ->
                val linkedReturns = returns.filter { it.purchaseId == invoice.id && it.status != "REVERSED" }.sumOf { it.amount }
                val remaining = (invoice.creditAmount - linkedReturns).coerceAtLeast(0.0)
                when (invoiceFilter) { 1 -> remaining > 0.0001; 2 -> remaining <= 0.0001; else -> true }
            }
            .sortedWith(compareByDescending<Purchase> { it.purchaseDate }.thenByDescending { it.createdAt })
    }
    Scaffold(
        modifier = modifier.testTag("supplier_profile_screen"),
        topBar = { TopAppBar(title = { Text(current.name, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }, actions = { IconButton(onClick = { editName = current.name; editPhone = current.phone; editAddress = current.address.orEmpty(); editNotes = current.notes.orEmpty(); showEditDialog = true }) { Icon(Icons.Default.Edit, contentDescription = if (ar) "تعديل المورد" else "Edit supplier") } }) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(current.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (!current.phone.isNullOrBlank()) Text(current.phone, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        current.address?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        current.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(6.dp))
                        Text(if (ar) "المبلغ المستحق للمورد" else "Amount currently owed", style = MaterialTheme.typography.labelLarge)
                        Text(money(balance.balance), style = MaterialTheme.typography.headlineMedium, color = if (balance.balance > 0.0001) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        if (balance.balance < -0.0001) Text(if (ar) "لديك رصيد مقدم لدى المورد" else "Supplier advance / credit", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryTile(if (ar) "إجمالي المشتريات" else "Total purchases", money(balance.totalPurchases), Modifier.weight(1f))
                    SummaryTile(if (ar) "المدفوعات" else "Payments", money(balance.totalPayments), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryTile(if (ar) "المرتجعات" else "Returns", money(balance.totalReturns), Modifier.weight(1f))
                    SummaryTile(if (ar) "الرصيد الحالي" else "Current balance", money(balance.balance), Modifier.weight(1f))
                }
            }
            item {
                Text(if (ar) "إجراءات المورد" else "Supplier actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onPurchaseAction(current) }, modifier = Modifier.weight(1f).testTag("supplier_profile_add_purchase")) { Icon(Icons.Default.ShoppingCart, null); Spacer(Modifier.width(4.dp)); Text(if (ar) "فاتورة شراء" else "Add Purchase", fontSize = 12.sp) }
                    Button(onClick = { onPaymentAction(current) }, modifier = Modifier.weight(1f).testTag("supplier_profile_payment")) { Icon(Icons.Default.Payments, null); Spacer(Modifier.width(4.dp)); Text(if (ar) "تسجيل دفعة" else "Payment", fontSize = 12.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onReturnAction(current) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Undo, null); Spacer(Modifier.width(4.dp)); Text(if (ar) "مرتجع شراء" else "Purchase Return", fontSize = 12.sp) }
                    OutlinedButton(onClick = { onStatementAction(current) }, modifier = Modifier.weight(1f).testTag("supplier_profile_statement")) { Icon(Icons.Default.ReceiptLong, null); Spacer(Modifier.width(4.dp)); Text(if (ar) "كشف الحساب" else "Statement", fontSize = 12.sp) }
                }
                OutlinedButton(onClick = { onArchive(current) }, modifier = Modifier.fillMaxWidth().testTag("supplier_profile_archive")) { Icon(Icons.Default.Archive, null); Spacer(Modifier.width(6.dp)); Text(if (ar) "أرشفة المورد" else "Archive supplier") }
            }
            item {
                Text(if (ar) "فواتير المشتريات" else "Purchase invoices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = invoiceSearch, onValueChange = { invoiceSearch = it }, modifier = Modifier.fillMaxWidth().testTag("supplier_invoice_search"), label = { Text(if (ar) "بحث برقم الفاتورة" else "Search invoice number") }, singleLine = true)
                Text(if (ar) "المتبقي هنا هو رصيد الفاتورة بعد المرتجعات المرتبطة بها؛ المدفوعات اللاحقة تُسجّل على حساب المورد ولا يربطها النموذج الحالي بفاتورة بعينها." else "Invoice remaining reflects the original credit less returns linked to that invoice. The current data model records later supplier payments at account level, not against a specific invoice.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(if (ar) "الكل" else "All", if (ar) "غير المسددة" else "Unpaid / partial", if (ar) "المسددة" else "Paid").forEachIndexed { index, label ->
                        FilterChip(selected = invoiceFilter == index, onClick = { invoiceFilter = index }, label = { Text(label, fontSize = 12.sp) })
                    }
                }
            }
            if (invoices.isEmpty()) item { Text(if (ar) "لا توجد فواتير مطابقة" else "No matching purchase invoices", modifier = Modifier.fillMaxWidth().padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(invoices, key = { it.id }) { invoice ->
                val linkedReturns = returns.filter { it.purchaseId == invoice.id && it.status != "REVERSED" }.sumOf { it.amount }
                val remaining = (invoice.creditAmount - linkedReturns).coerceAtLeast(0.0)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().testTag("supplier_invoice_${invoice.id}")) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(invoice.invoiceNumber, fontWeight = FontWeight.Bold)
                            Text(if (remaining <= 0.0001) { if (ar) "مسددة" else "Paid" } else if (invoice.paidAmount > 0.0001) { if (ar) "مسددة جزئياً" else "Partially paid" } else { if (ar) "غير مسددة" else "Unpaid" }, color = if (remaining <= 0.0001) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        Text(invoice.purchaseDate, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${if (ar) "الإجمالي" else "Total"}: ${money(invoice.totalAmount)}"); Text("${if (ar) "المدفوع" else "Paid"}: ${money(invoice.paidAmount)}") }
                        Text("${if (ar) "المتبقي" else "Remaining"}: ${money(remaining)}", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(if (ar) "تعديل بيانات المورد" else "Edit supplier") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text(if (ar) "اسم المورد" else "Supplier name") }, singleLine = true)
                    OutlinedTextField(value = editPhone, onValueChange = { editPhone = it }, label = { Text(if (ar) "الهاتف" else "Phone") }, singleLine = true)
                    OutlinedTextField(value = editAddress, onValueChange = { editAddress = it }, label = { Text(if (ar) "العنوان" else "Address") })
                    OutlinedTextField(value = editNotes, onValueChange = { editNotes = it }, label = { Text(if (ar) "ملاحظات" else "Notes") })
                }
            },
            confirmButton = { Button(onClick = { if (editName.isNotBlank()) { onEdit(current.copy(name = editName.trim(), phone = editPhone.trim(), address = editAddress.trim().ifBlank { null }, notes = editNotes.trim().ifBlank { null })); showEditDialog = false } }, enabled = editName.isNotBlank()) { Text(if (ar) "حفظ" else "Save") } },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text(if (ar) "إلغاء" else "Cancel") } }
        )
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
    }
}

private fun money(value: Double): String = String.format(Locale.US, "%,.2f %s", value, AppCurrency.SYMBOL)
