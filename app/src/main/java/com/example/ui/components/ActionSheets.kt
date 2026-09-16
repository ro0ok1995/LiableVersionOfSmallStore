package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCurrency
import com.example.model.LanguageMode
import com.example.model.StoreStrings

/**
 * The "+" button action sheet:
 * Exactly two choices: "Record Transaction" and "Quick Payment".
 * Do not add more options to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlusActionSheet(
    isOpen: Boolean,
    languageMode: LanguageMode,
    onDismiss: () -> Unit,
    onRecordTransactionClick: () -> Unit,
    onQuickPaymentClick: () -> Unit
) {
    if (!isOpen) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isArabic = languageMode == LanguageMode.ARABIC

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = if (isArabic) "إجراء جديد" else "New Action",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            // Choice 1: Record Transaction
            ActionSheetOptionItem(
                title = if (isArabic) StoreStrings.RECORD_TRANSACTION_AR else StoreStrings.RECORD_TRANSACTION_EN,
                description = if (isArabic) "إضافة عملية شراء أو بيع في الحساب" else "Add sales or purchase transaction entry",
                icon = Icons.Default.AddCard,
                testTag = "action_sheet_record_transaction",
                onClick = onRecordTransactionClick
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Choice 2: Quick Payment
            ActionSheetOptionItem(
                title = if (isArabic) StoreStrings.QUICK_PAYMENT_AR else StoreStrings.QUICK_PAYMENT_EN,
                description = if (isArabic) "تسجيل استلام دفعة نقدية أو تسديد دين" else "Record payment receipt or supplier disbursement",
                icon = Icons.Default.Payment,
                testTag = "action_sheet_quick_payment",
                onClick = onQuickPaymentClick
            )
        }
    }
}

@Composable
private fun ActionSheetOptionItem(
    title: String,
    description: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordTransactionSheet(
    isOpen: Boolean,
    languageMode: LanguageMode,
    onDismiss: () -> Unit,
    onSubmit: (customer: String, amount: Double, notes: String) -> Unit
) {
    if (!isOpen) return
    val isArabic = languageMode == LanguageMode.ARABIC
    var customerName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) StoreStrings.RECORD_TRANSACTION_AR else StoreStrings.RECORD_TRANSACTION_EN,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("record_tx_cancel_button")
                ) {
                    Text(
                        text = if (isArabic) "إلغاء" else "Cancel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Scrollable Form Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Customer Name Field
                FormFieldLabel(text = if (isArabic) "اسم العميل / الحساب" else "Customer / Account Name")
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it; errorText = null },
                    placeholder = { Text(if (isArabic) "أدخل اسم العميل" else "Enter customer name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("record_tx_customer_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Amount Field
                FormFieldLabel(text = if (isArabic) "المبلغ (${AppCurrency.SYMBOL})" else "Amount (${AppCurrency.SYMBOL})")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; errorText = null },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("record_tx_amount_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Notes Field (Optional)
                val optionalSuffix = if (isArabic) StoreStrings.OPTIONAL_AR else StoreStrings.OPTIONAL_EN
                FormFieldLabel(text = "${if (isArabic) "ملاحظات المعاملة" else "Transaction Notes"} $optionalSuffix")
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    placeholder = { Text(if (isArabic) "تفاصيل إضافية" else "Additional details") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("record_tx_notes_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            // Sticky Footer Action Button (Only ONE primary filled button)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull()
                            if (customerName.isBlank()) {
                                errorText = if (isArabic) "يرجى إدخال اسم العميل" else "Please enter customer name"
                            } else if (amount == null || amount <= 0) {
                                errorText = if (isArabic) "يرجى إدخال مبلغ صحيح" else "Please enter a valid amount"
                            } else {
                                onSubmit(customerName.trim(), amount, notesText.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("record_tx_submit_button")
                    ) {
                        Text(
                            text = if (isArabic) StoreStrings.RECORD_TRANSACTION_AR else StoreStrings.RECORD_TRANSACTION_EN,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPaymentSheet(
    isOpen: Boolean,
    languageMode: LanguageMode,
    onDismiss: () -> Unit,
    onSubmit: (customer: String, amount: Double, notes: String) -> Unit
) {
    if (!isOpen) return
    val isArabic = languageMode == LanguageMode.ARABIC
    var customerName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var refText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) StoreStrings.QUICK_PAYMENT_AR else StoreStrings.QUICK_PAYMENT_EN,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("quick_payment_cancel_button")
                ) {
                    Text(
                        text = if (isArabic) "إلغاء" else "Cancel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Scrollable Form Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Customer / Account
                FormFieldLabel(text = if (isArabic) "العميل / الحساب" else "Customer / Account")
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it; errorText = null },
                    placeholder = { Text(if (isArabic) "أدخل اسم العميل" else "Enter customer name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("quick_payment_customer_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Payment Amount
                FormFieldLabel(text = if (isArabic) "مبلغ الدفعة (${AppCurrency.SYMBOL})" else "Payment Amount (${AppCurrency.SYMBOL})")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; errorText = null },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("quick_payment_amount_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Reference / Receipt (Optional)
                val optionalSuffix = if (isArabic) StoreStrings.OPTIONAL_AR else StoreStrings.OPTIONAL_EN
                FormFieldLabel(text = "${if (isArabic) "رقم السند / المرجع" else "Receipt / Reference No."} $optionalSuffix")
                OutlinedTextField(
                    value = refText,
                    onValueChange = { refText = it },
                    placeholder = { Text(if (isArabic) "مثال: سند #402" else "e.g. Receipt #402") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("quick_payment_ref_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            // Sticky Footer Action Button (Only ONE primary filled button)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull()
                            if (customerName.isBlank()) {
                                errorText = if (isArabic) "يرجى إدخال اسم العميل" else "Please enter customer name"
                            } else if (amount == null || amount <= 0) {
                                errorText = if (isArabic) "يرجى إدخال مبلغ صحيح" else "Please enter a valid amount"
                            } else {
                                onSubmit(customerName.trim(), amount, refText.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("quick_payment_submit_button")
                    ) {
                        Text(
                            text = if (isArabic) StoreStrings.QUICK_PAYMENT_AR else StoreStrings.QUICK_PAYMENT_EN,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
