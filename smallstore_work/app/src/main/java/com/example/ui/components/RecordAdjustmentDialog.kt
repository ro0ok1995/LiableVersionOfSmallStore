package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CustomerAccount
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordAdjustmentDialog(
    customer: CustomerAccount,
    currency: String,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (direction: String, amount: Double, date: String, reason: String, reference: String?) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val todayDate = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    var direction by remember { mutableStateOf("DEBIT") } // "DEBIT" or "CREDIT"
    var amountText by remember { mutableStateOf("") }
    var reasonText by remember { mutableStateOf("") }
    var referenceText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(todayDate) }

    var attemptedSubmit by remember { mutableStateOf(false) }

    val amount = amountText.toDoubleOrNull()
    val isAmountValid = amount != null && amount > 0.0
    val isReasonValid = reasonText.isNotBlank()
    val isDateValid = dateText.isNotBlank()

    val isValid = isAmountValid && isReasonValid && isDateValid

    // Projected new balance
    val projectedBalance = if (amount != null && amount > 0.0) {
        if (direction == "DEBIT") customer.balance + amount else customer.balance - amount
    } else {
        customer.balance
    }

    AlertDialog(
        onDismissRequest = {
            focusManager.clearFocus()
            onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = if (isArabic) "تسجيل تسوية رصيد" else "Record Balance Adjustment",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Customer context card
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = customer.customerName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isArabic) "الرصيد الحالي:" else "Current Balance:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.2f %s", customer.balance, currency),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (customer.balance > 0) StatusRed else StatusGreen
                        )
                    }
                }

                // Accounting warning note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isArabic) {
                            "يتم توثيق التسوية كحدث محاسبي رسمي دون المساس بالسجلات التاريخية."
                        } else {
                            "Documented as an official accounting event without mutating historical records."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Direction selection: DEBIT vs CREDIT
                Text(
                    text = if (isArabic) "نوع / اتجاه التسوية *" else "Adjustment Direction *",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // DEBIT Card (+ Receivable)
                    val isDebitSelected = direction == "DEBIT"
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDebitSelected) StatusRed.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            width = if (isDebitSelected) 2.dp else 1.dp,
                            color = if (isDebitSelected) StatusRed else GeoOutlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { direction = "DEBIT" }
                            .testTag("adjustment_direction_debit")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = StatusRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isArabic) "مدين (+)" else "Debit (+)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isDebitSelected) StatusRed else MaterialTheme.colorScheme.onSurface
                                )
                                if (isDebitSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = StatusRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isArabic) "زيادة المديونية" else "Increase Debt",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // CREDIT Card (- Receivable)
                    val isCreditSelected = direction == "CREDIT"
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCreditSelected) StatusGreen.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            width = if (isCreditSelected) 2.dp else 1.dp,
                            color = if (isCreditSelected) StatusGreen else GeoOutlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { direction = "CREDIT" }
                            .testTag("adjustment_direction_credit")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = StatusGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isArabic) "دائن (-)" else "Credit (-)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isCreditSelected) StatusGreen else MaterialTheme.colorScheme.onSurface
                                )
                                if (isCreditSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = StatusGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isArabic) "تخفيض المديونية" else "Reduce Debt",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Amount Field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = {
                        Text(if (isArabic) "مبلغ التسوية *" else "Adjustment Amount *")
                    },
                    placeholder = {
                        Text("0.00")
                    },
                    prefix = {
                        Text(currency, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = attemptedSubmit && !isAmountValid,
                    supportingText = {
                        if (attemptedSubmit && !isAmountValid) {
                            Text(
                                text = if (isArabic) "المبلغ يجب أن يكون أكبر من الصفر" else "Amount must be strictly positive",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeoPrimary,
                        unfocusedBorderColor = GeoOutlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjustment_amount_input")
                )

                // Projected Balance Preview
                if (amount != null && amount > 0.0) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, GeoOutlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) "الرصيد بعد التسوية:" else "Projected Balance:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(Locale.US, "%.2f %s", projectedBalance, currency),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (projectedBalance > 0) StatusRed else StatusGreen
                            )
                        }
                    }
                }

                // Reason Field (Mandatory)
                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = {
                        Text(if (isArabic) "سبب التسوية المحاسبية *" else "Adjustment Reason *")
                    },
                    placeholder = {
                        Text(if (isArabic) "مثال: تصحيح خطأ حسابي سابق، خصم تجاري..." else "e.g., Ledger correction, agreed discount...")
                    },
                    isError = attemptedSubmit && !isReasonValid,
                    supportingText = {
                        if (attemptedSubmit && !isReasonValid) {
                            Text(
                                text = if (isArabic) "سبب التسوية إلزامي لتوثيق القيد المحاسبي" else "Reason is required to document adjustment",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeoPrimary,
                        unfocusedBorderColor = GeoOutlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjustment_reason_input")
                )

                // Reference Field (Optional)
                OutlinedTextField(
                    value = referenceText,
                    onValueChange = { referenceText = it },
                    label = {
                        Text(if (isArabic) "رقم المرجع / المستند (اختياري)" else "Reference / Document No. (Optional)")
                    },
                    placeholder = {
                        Text(if (isArabic) "مثال: ADJ-101، إشعار رقم 5..." else "e.g., ADJ-101, Notice #5...")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeoPrimary,
                        unfocusedBorderColor = GeoOutlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjustment_reference_input")
                )

                // Date Field
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = {
                        Text(if (isArabic) "تاريخ القيد" else "Date")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeoPrimary,
                        unfocusedBorderColor = GeoOutlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjustment_date_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    attemptedSubmit = true
                    if (isValid && amount != null) {
                        focusManager.clearFocus()
                        onConfirm(
                            direction,
                            amount,
                            dateText.trim(),
                            reasonText.trim(),
                            referenceText.trim().ifBlank { null }
                        )
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GeoPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("adjustment_confirm_button")
            ) {
                Text(
                    text = if (isArabic) "حفظ التسوية" else "Save Adjustment",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("adjustment_dismiss_button")
            ) {
                Text(if (isArabic) "إلغاء" else "Cancel")
            }
        }
    )
}
