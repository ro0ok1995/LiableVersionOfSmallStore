package com.example.ui.components

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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.Expense
import com.example.data.db.ExpenseCategory
import com.example.data.db.FinancialAccount
import com.example.data.db.PaymentMethod
import com.example.model.AppCurrency
import com.example.model.LanguageMode
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusRedBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 10 Expenses UI:
 * Displays expense categories, expense history, and controls to record new expenses.
 */
@Composable
fun ExpensesSection(
    expenses: List<Expense>,
    expenseCategories: List<ExpenseCategory>,
    financialAccounts: List<FinancialAccount>,
    paymentMethods: List<PaymentMethod>,
    languageMode: LanguageMode,
    onRecordExpense: (categoryId: String, amount: Double, financialAccountId: String, paymentMethodId: String?, date: String?, description: String, onComplete: (Result<Expense>) -> Unit) -> Unit,
    onAddExpenseCategory: (name: String, description: String?, onComplete: (Result<ExpenseCategory>) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val currency = AppCurrency.SYMBOL

    var showRecordExpenseDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isFeedbackError by remember { mutableStateOf(false) }

    val activeExpenses = expenses.filter { it.status == "ACTIVE" }
    val totalExpenseAmount = activeExpenses.sumOf { it.amount }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("expenses_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Feedback message banner
        if (feedbackMessage != null) {
            Surface(
                color = if (isFeedbackError) StatusRedBg else StatusGreenBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { feedbackMessage = null }
                    .testTag("expenses_feedback_banner")
            ) {
                Text(
                    text = feedbackMessage!!,
                    color = if (isFeedbackError) StatusRed else StatusGreen,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        // Summary Card & Action Button
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GeoOutlineVariant, RoundedCornerShape(14.dp))
                .testTag("expenses_summary_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isArabic) "إجمالي المصروفات النشطة" else "Total Active Expenses",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f %s", totalExpenseAmount, currency),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = StatusRed,
                            modifier = Modifier.testTag("text_total_expenses")
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = if (isArabic) "${activeExpenses.size} مصروف" else "${activeExpenses.size} items",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showRecordExpenseDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_open_record_expense"),
                        colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isArabic) "تسجيل مصروف" else "Record Expense")
                    }

                    Button(
                        onClick = { showAddCategoryDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("btn_open_add_category")
                    ) {
                        Icon(imageVector = Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isArabic) "تصنيف" else "Category")
                    }
                }
            }
        }

        // Section Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isArabic) "سجل المصروفات" else "Expense History",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Expenses List
        if (expenses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("empty_expenses_view"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isArabic) "لا توجد مصروفات مسجلة بعد" else "No expenses recorded yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("expenses_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(expenses, key = { it.id }) { exp ->
                    val category = expenseCategories.find { it.id == exp.categoryId }
                    val account = financialAccounts.find { it.id == exp.financialAccountId }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GeoOutlineVariant, RoundedCornerShape(12.dp))
                            .testTag("expense_card_${exp.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exp.description,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = GeoPrimary.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = category?.name ?: exp.categoryId,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GeoPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (account != null) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = account.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = exp.date,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format(Locale.US, "-%.2f %s", exp.amount, currency),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (exp.status == "REVERSED") MaterialTheme.colorScheme.onSurfaceVariant else StatusRed
                                )
                                if (exp.status == "REVERSED") {
                                    Surface(
                                        color = StatusRedBg,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isArabic) "ملغي" else "REVERSED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StatusRed,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
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

    // -------------------------------------------------------------------------
    // DIALOG: Record Expense
    // -------------------------------------------------------------------------
    if (showRecordExpenseDialog) {
        val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
        var selectedCatId by remember {
            mutableStateOf(expenseCategories.firstOrNull()?.id ?: "")
        }
        var amountText by remember { mutableStateOf("") }
        var selectedAccountId by remember {
            mutableStateOf(financialAccounts.find { it.id == "acc_cash" }?.id ?: financialAccounts.firstOrNull()?.id ?: "acc_cash")
        }
        var selectedMethodId by remember {
            mutableStateOf(paymentMethods.firstOrNull()?.id)
        }
        var dateText by remember { mutableStateOf(todayStr) }
        var descriptionText by remember { mutableStateOf("") }
        var dialogError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showRecordExpenseDialog = false },
            title = {
                Text(
                    text = if (isArabic) "تسجيل مصروف جديد" else "Record New Expense",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (dialogError != null) {
                        Surface(
                            color = StatusRedBg,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = dialogError!!,
                                color = StatusRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // Category selection
                    Text(
                        text = if (isArabic) "التصنيف:" else "Category:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    if (expenseCategories.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(expenseCategories) { cat ->
                                FilterChip(
                                    selected = selectedCatId == cat.id,
                                    onClick = { selectedCatId = cat.id },
                                    label = { Text(cat.name) },
                                    modifier = Modifier.testTag("chip_category_${cat.id}")
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (isArabic) "لا توجد تصنيفات، يرجى إضافة تصنيف أولاً" else "No categories found. Add one first.",
                            color = StatusRed,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Amount input
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; dialogError = null },
                        label = { Text(if (isArabic) "المبلغ ($currency)" else "Amount ($currency)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_expense_amount")
                    )

                    // Financial Account selection
                    Text(
                        text = if (isArabic) "الحساب المالي (الخصم منه):" else "Financial Account:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(financialAccounts) { acc ->
                            FilterChip(
                                selected = selectedAccountId == acc.id,
                                onClick = { selectedAccountId = acc.id },
                                label = { Text(acc.name) },
                                modifier = Modifier.testTag("chip_account_${acc.id}")
                            )
                        }
                    }

                    // Payment Method selection
                    if (paymentMethods.isNotEmpty()) {
                        Text(
                            text = if (isArabic) "طريقة الدفع (اختياري):" else "Payment Method:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(paymentMethods) { pm ->
                                FilterChip(
                                    selected = selectedMethodId == pm.id,
                                    onClick = {
                                        selectedMethodId = if (selectedMethodId == pm.id) null else pm.id
                                    },
                                    label = { Text(pm.name) },
                                    modifier = Modifier.testTag("chip_method_${pm.id}")
                                )
                            }
                        }
                    }

                    // Date input
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it; dialogError = null },
                        label = { Text(if (isArabic) "التاريخ (YYYY-MM-DD)" else "Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_expense_date")
                    )

                    // Description input
                    OutlinedTextField(
                        value = descriptionText,
                        onValueChange = { descriptionText = it; dialogError = null },
                        label = { Text(if (isArabic) "الوصف / البيان" else "Description") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_expense_description")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (selectedCatId.isBlank()) {
                            dialogError = if (isArabic) "يرجى اختيار تصنيف المصروف" else "Please select an expense category"
                            return@Button
                        }
                        if (amount <= 0.0) {
                            dialogError = if (isArabic) "المبلغ يجب أن يكون أكبر من صفر" else "Amount must be strictly greater than zero"
                            return@Button
                        }
                        if (selectedAccountId.isBlank()) {
                            dialogError = if (isArabic) "يرجى اختيار الحساب المالي" else "Please select a financial account"
                            return@Button
                        }
                        if (descriptionText.isBlank()) {
                            dialogError = if (isArabic) "البيان / الوصف مطلوب" else "Description cannot be blank"
                            return@Button
                        }

                        onRecordExpense(
                            selectedCatId,
                            amount,
                            selectedAccountId,
                            selectedMethodId,
                            dateText.trim(),
                            descriptionText.trim()
                        ) { result ->
                            if (result.isSuccess) {
                                feedbackMessage = if (isArabic) "تم تسجيل المصروف بنجاح (-$amount $currency)" else "Expense recorded successfully (-$amount $currency)"
                                isFeedbackError = false
                                showRecordExpenseDialog = false
                            } else {
                                dialogError = result.exceptionOrNull()?.message ?: "Failed to record expense"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                    modifier = Modifier.testTag("btn_confirm_save_expense")
                ) {
                    Text(if (isArabic) "حفظ المصروف" else "Save Expense")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecordExpenseDialog = false }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    // -------------------------------------------------------------------------
    // DIALOG: Add Expense Category
    // -------------------------------------------------------------------------
    if (showAddCategoryDialog) {
        var catName by remember { mutableStateOf("") }
        var catDesc by remember { mutableStateOf("") }
        var catError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = {
                Text(
                    text = if (isArabic) "إضافة تصنيف مصروفات جديد" else "Add New Category",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (catError != null) {
                        Text(text = catError!!, color = StatusRed, style = MaterialTheme.typography.bodySmall)
                    }

                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it; catError = null },
                        label = { Text(if (isArabic) "اسم التصنيف" else "Category Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_new_category_name")
                    )

                    OutlinedTextField(
                        value = catDesc,
                        onValueChange = { catDesc = it },
                        label = { Text(if (isArabic) "الوصف (اختياري)" else "Description (Optional)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_new_category_desc")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (catName.isBlank()) {
                            catError = if (isArabic) "اسم التصنيف مطلوب" else "Category name required"
                            return@Button
                        }
                        onAddExpenseCategory(catName.trim(), catDesc.trim().ifBlank { null }) { result ->
                            if (result.isSuccess) {
                                feedbackMessage = if (isArabic) "تمت إضافة التصنيف بنجاح" else "Category added successfully"
                                isFeedbackError = false
                                showAddCategoryDialog = false
                            } else {
                                catError = result.exceptionOrNull()?.message ?: "Failed to add category"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                    modifier = Modifier.testTag("btn_confirm_add_category")
                ) {
                    Text(if (isArabic) "إضافة" else "Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }
}
