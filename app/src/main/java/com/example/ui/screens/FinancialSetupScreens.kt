package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.ExpenseCategory
import com.example.data.db.FinancialAccount
import com.example.model.LanguageMode
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCategoriesManagementScreen(
    languageMode: LanguageMode,
    categories: List<ExpenseCategory>,
    onBack: () -> Unit,
    onAdd: (String, String?) -> Unit
) {
    val ar = languageMode == LanguageMode.ARABIC
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text(if (ar) "فئات المصروفات" else "Expense Categories") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) },
        floatingActionButton = { FloatingActionButton(onClick = { name = ""; description = ""; showAdd = true }) { Icon(Icons.Default.Add, contentDescription = if (ar) "إضافة فئة" else "Add category") } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(if (ar) "الفئات المستخدمة لتصنيف المصروفات التشغيلية" else "Categories used to classify operating expenses", style = MaterialTheme.typography.bodyMedium) }
            if (categories.isEmpty()) item { Text(if (ar) "لا توجد فئات بعد. أضف فئة للبدء." else "No categories yet. Add one to get started.", modifier = Modifier.padding(12.dp)) }
            items(categories, key = { it.id }) { category -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(category.name, fontWeight = FontWeight.Bold); category.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
        }
    }
    if (showAdd) AlertDialog(onDismissRequest = { showAdd = false }, title = { Text(if (ar) "إضافة فئة مصروفات" else "Add expense category") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text(if (ar) "اسم الفئة" else "Category name") }, singleLine = true); OutlinedTextField(description, { description = it }, label = { Text(if (ar) "الوصف (اختياري)" else "Description (optional)") }) } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onAdd(name.trim(), description.trim().ifBlank { null }); showAdd = false }) { Text(if (ar) "حفظ" else "Save") } }, dismissButton = { TextButton(onClick = { showAdd = false }) { Text(if (ar) "إلغاء" else "Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialAccountsManagementScreen(
    languageMode: LanguageMode,
    accounts: List<FinancialAccount>,
    onBack: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    val ar = languageMode == LanguageMode.ARABIC
    var showAdd by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }; var type by remember { mutableStateOf("CASH") }
    Scaffold(topBar = { TopAppBar(title = { Text(if (ar) "الحسابات المالية" else "Financial Accounts") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }, floatingActionButton = { FloatingActionButton(onClick = { name = ""; type = "CASH"; showAdd = true }) { Icon(Icons.Default.Add, contentDescription = null) } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(if (ar) "الحساب الافتراضي للعمليات النقدية هو نقدي كاش. كل حساب يحتفظ بمعرّفه ورصيده المستقل." else "Cash is the default account. Each account keeps its own ID and balance.") }
            items(accounts, key = { it.id }) { account -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(account.name, fontWeight = FontWeight.Bold); Text("${account.type} · ${account.id}", color = MaterialTheme.colorScheme.onSurfaceVariant); Text(if (account.isActive) (if (ar) "نشط" else "Active") else (if (ar) "غير نشط" else "Inactive")) } } }
        }
    }
    if (showAdd) AlertDialog(onDismissRequest = { showAdd = false }, title = { Text(if (ar) "إضافة حساب مالي" else "Add financial account") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text(if (ar) "اسم الحساب" else "Account name") }, singleLine = true); Text(if (ar) "نوع الحساب" else "Account type"); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("CASH", "BANK", "CARD", "E_WALLET", "OTHER").forEach { option -> FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option) }) } } } }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onAdd(name.trim(), type); showAdd = false }) { Text(if (ar) "حفظ" else "Save") } }, dismissButton = { TextButton(onClick = { showAdd = false }) { Text(if (ar) "إلغاء" else "Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpeningBalanceSetupScreen(languageMode: LanguageMode, accounts: List<FinancialAccount>, onBack: () -> Unit, onSave: (String, Double, String) -> Unit) {
    val ar = languageMode == LanguageMode.ARABIC
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull { it.id == "acc_cash" }?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }; var direction by remember { mutableStateOf("DEBIT") }; var message by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text(if (ar) "الرصيد الافتتاحي والإعداد الأولي" else "Opening Balance / Initial Setup") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (ar) "سجّل رصيد بداية الحساب مرة واحدة فقط. لا تسجّل الرصيد الافتتاحي كمصروف." else "Record each account's opening balance once. Do not classify it as an expense.")
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) { OutlinedTextField(value = accounts.firstOrNull { it.id == accountId }?.name.orEmpty(), onValueChange = {}, readOnly = true, label = { Text(if (ar) "الحساب المالي" else "Financial account") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { accounts.forEach { account -> DropdownMenuItem(text = { Text(account.name) }, onClick = { accountId = account.id; expanded = false }) } } }
            OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text(if (ar) "المبلغ" else "Amount") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = direction == "DEBIT", onClick = { direction = "DEBIT" }, label = { Text(if (ar) "رصيد موجب / مدين" else "Debit / positive") }); FilterChip(selected = direction == "CREDIT", onClick = { direction = "CREDIT" }, label = { Text(if (ar) "دائن" else "Credit") }) }
            Button(onClick = { val value = amount.toDoubleOrNull(); if (accountId.isBlank() || value == null || value <= 0) message = if (ar) "اختر حسابًا وأدخل مبلغًا صحيحًا أكبر من صفر." else "Select an account and enter a positive amount." else { onSave(accountId, value, direction); message = if (ar) "تم إرسال طلب تسجيل الرصيد الافتتاحي." else "Opening balance request submitted."; amount = "" } }, modifier = Modifier.fillMaxWidth()) { Text(if (ar) "حفظ الرصيد الافتتاحي" else "Save opening balance") }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapitalManagementScreen(languageMode: LanguageMode, accounts: List<FinancialAccount>, onBack: () -> Unit) {
    val ar = languageMode == LanguageMode.ARABIC
    Scaffold(topBar = { TopAppBar(title = { Text(if (ar) "رأس المال" else "Capital") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (ar) "رأس المال تمويل للمتجر وليس مصروفًا تشغيليًا أو إيراد مبيعات." else "Capital is store funding, not an operating expense or sales revenue.", style = MaterialTheme.typography.titleMedium)
            Text(if (ar) "الحسابات المالية المتاحة لاستلام التمويل:" else "Financial accounts available to receive funding:", fontWeight = FontWeight.Bold)
            accounts.forEach { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(it.name, fontWeight = FontWeight.Bold); Text(it.type, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            Text(if (ar) "واجهة التسجيل ظاهرة الآن، لكن تسجيل رأس المال كقيد مستقل في حقوق الملكية يحتاج إلى دفتر رأس مال مخصص؛ لم يتم تسجيله كحركة مالية حتى لا تُحسب الأموال مرتين." else "The screen is available, but recording capital as a separate equity entry requires a dedicated capital ledger. No financial entry is created here to avoid double-counting.", color = MaterialTheme.colorScheme.error)
        }
    }
}
