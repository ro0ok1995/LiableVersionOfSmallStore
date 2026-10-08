package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TransactionItem
import java.util.Locale

private data class PredefinedReversalReason(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val descAr: String,
    val descEn: String
)

private val PREDEFINED_REVERSAL_REASONS = listOf(
    PredefinedReversalReason("incorrect_entry", "خطأ في تسجيل العملية", "Incorrect Transaction Entry", "تم تسجيل العملية بشكل غير صحيح.", "The transaction was entered incorrectly."),
    PredefinedReversalReason("duplicate_transaction", "إدخال عملية مكررة", "Duplicate Transaction", "تم تسجيل نفس العملية أكثر من مرة.", "The same transaction was recorded more than once."),
    PredefinedReversalReason("incorrect_amount", "خطأ في المبلغ", "Incorrect Amount", "المبلغ المسجل غير صحيح.", "The recorded amount is incorrect."),
    PredefinedReversalReason("wrong_customer", "خطأ في العميل", "Wrong Customer", "تم تسجيل العملية على العميل الخطأ.", "The transaction was recorded against the wrong customer."),
    PredefinedReversalReason("wrong_supplier", "خطأ في المورد", "Wrong Supplier", "تم تسجيل العملية على المورد الخطأ.", "The transaction was recorded against the wrong supplier."),
    PredefinedReversalReason("wrong_product", "خطأ في المنتج", "Wrong Product", "تم استخدام أو تسجيل المنتج الخطأ.", "The wrong product was used or recorded."),
    PredefinedReversalReason("user_request", "طلب من المستخدم", "User Request", "تم طلب إلغاء العملية من المستخدم.", "The user requested that the transaction be reversed."),
    PredefinedReversalReason("accounting_correction", "تصحيح محاسبي", "Accounting Correction", "تصحيح محاسبي موثق للعملية.", "A documented accounting correction."),
    PredefinedReversalReason("operational", "سبب تشغيلي آخر", "Other Operational Reason", "سبب تشغيلي غير موجود في القائمة مع كتابة التفاصيل.", "Another operational reason; details are required."),
    PredefinedReversalReason("other", "سبب آخر", "Other Reason", "سبب معتمد غير موجود في القائمة مع كتابة التفاصيل.", "Another approved reason; details are required.")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReversalConfirmationDialog(
    transaction: TransactionItem,
    currency: String,
    isArabic: Boolean,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedReasonId by remember { mutableStateOf<String?>(null) }
    var customReasonText by remember { mutableStateOf("") }
    var showReasonInfoDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isArabic) "إلغاء المعاملة محاسبياً" else "Reverse Financial Transaction",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = if (isArabic)
                            "سيتم تحييد الأثر المالي لهذه المعاملة محاسبياً وتصفير تأثيرها على رصيد العميل مع الاحتفاظ بسجل المعاملة الأصلية للتدقيق والمطابقة."
                        else
                            "The financial impact of this transaction will be neutralized in accounts and reports, while preserving the original record for audit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "${if (isArabic) "العميل: " else "Customer: "}${transaction.customerNameSnapshot}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${if (isArabic) "المبلغ: " else "Amount: "}%,.2f %s (%s)".format(Locale.US, transaction.amount, currency, transaction.activityType),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${if (isArabic) "التاريخ: " else "Date: "}${transaction.date}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Reason Selection Header with Info Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isArabic) "سبب الإلغاء *" else "Reversal Reason *",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { showReasonInfoDialog = true },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("reversal_reason_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = if (isArabic) "دليل أسباب الإلغاء" else "Reversal Reasons Guide",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                var reasonMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = reasonMenuExpanded,
                    onExpandedChange = { reasonMenuExpanded = !reasonMenuExpanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("predefined_reversal_reasons_dropdown")
                ) {
                    val selected = PREDEFINED_REVERSAL_REASONS.find { it.id == selectedReasonId }
                    OutlinedTextField(
                        value = selected?.let { if (isArabic) it.nameAr else it.nameEn } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (isArabic) "اختر سبب الإلغاء *" else "Select reversal reason *") },
                        placeholder = { Text(if (isArabic) "اختر من القائمة" else "Select from the list") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonMenuExpanded) },
                        isError = showError && selectedReasonId == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = reasonMenuExpanded,
                        onDismissRequest = { reasonMenuExpanded = false }
                    ) {
                        PREDEFINED_REVERSAL_REASONS.forEach { reasonItem ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(if (isArabic) reasonItem.nameAr else reasonItem.nameEn, fontWeight = FontWeight.SemiBold)
                                        Text(if (isArabic) reasonItem.descAr else reasonItem.descEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    selectedReasonId = reasonItem.id
                                    reasonMenuExpanded = false
                                },
                                modifier = Modifier.testTag("reversal_reason_option_${reasonItem.id}")
                            )
                        }
                    }
                }

                // Custom Reason Input - ONLY displayed when "سبب آخر" / "Other reason" is selected
                if (selectedReasonId == "other") {
                    OutlinedTextField(
                        value = customReasonText,
                        onValueChange = {
                            customReasonText = it
                            if (it.isNotBlank()) showError = false
                        },
                        label = { Text(if (isArabic) "سبب الإلغاء (مطلوب)" else "Reversal Reason (Required)") },
                        placeholder = { Text(if (isArabic) "اكتب سبب الإلغاء هنا..." else "Enter reversal reason here...") },
                        isError = showError && customReasonText.trim().isBlank(),
                        supportingText = if (showError && customReasonText.trim().isBlank()) {
                            { Text(if (isArabic) "يرجى كتابة سبب الإلغاء" else "Please enter a reversal reason", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reversal_reason_input")
                    )
                }

                if (showError && selectedReasonId == null) {
                    Text(
                        text = if (isArabic) "يرجى اختيار سبب الإلغاء من القائمة *" else "Please select a reversal reason *",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalReason = when {
                        selectedReasonId == "other" -> customReasonText.trim().ifEmpty { null }
                        selectedReasonId != null -> {
                            val sel = PREDEFINED_REVERSAL_REASONS.find { it.id == selectedReasonId }
                            if (isArabic) sel?.nameAr else sel?.nameEn
                        }
                        else -> null
                    }
                    if (finalReason != null) {
                        onConfirm(selectedReasonId ?: "other", finalReason)
                    } else {
                        showError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_reversal_button")
            ) {
                Text(if (isArabic) "تأكيد الإلغاء" else "Confirm Reversal")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_reversal_button")
            ) {
                Text(if (isArabic) "تراجع" else "Cancel")
            }
        }
    )

    if (showReasonInfoDialog) {
        AlertDialog(
            onDismissRequest = { showReasonInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = if (isArabic) "دليل أسباب الإلغاء" else "Reversal Reasons Guide",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PREDEFINED_REVERSAL_REASONS.forEach { reasonItem ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reversal_info_reason_${reasonItem.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (isArabic) reasonItem.nameAr else reasonItem.nameEn,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isArabic) reasonItem.descAr else reasonItem.descEn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showReasonInfoDialog = false },
                    modifier = Modifier.testTag("dismiss_reversal_reason_info_dialog")
                ) {
                    Text(if (isArabic) "فهمت" else "Got it")
                }
            },
            modifier = Modifier.testTag("reversal_reason_info_dialog")
        )
    }
}
