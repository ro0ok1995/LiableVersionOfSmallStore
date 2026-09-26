package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.model.RefundRequest
import com.example.model.SaleReturnLineRequest
import com.example.model.TransactionItem

@Composable
fun RecordSaleReturnDialog(
    transaction: TransactionItem,
    currency: String,
    isArabic: Boolean,
    onLoadDetails: suspend (String) -> Triple<Sale?, List<SaleLine>, Map<String, Int>>,
    onConfirm: (List<SaleReturnLineRequest>, String, RefundRequest?) -> Unit,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var sale by remember { mutableStateOf<Sale?>(null) }
    var saleLines by remember { mutableStateOf<List<SaleLine>>(emptyList()) }
    var returnableMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    // Map of saleLineId to selected return quantity
    val selectedQuantities = remember { mutableStateMapOf<String, Int>() }

    var reasonText by remember { mutableStateOf("") }
    var issueRefund by remember { mutableStateOf(false) }
    var refundAmountText by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }

    LaunchedEffect(transaction.id) {
        isLoading = true
        val (loadedSale, loadedLines, loadedReturnable) = onLoadDetails(transaction.id)
        sale = loadedSale
        saleLines = loadedLines
        returnableMap = loadedReturnable
        // Initialize all quantities to 0
        loadedLines.forEach { line ->
            selectedQuantities[line.id] = 0
        }
        isLoading = false
    }

    val totalReturnAmount = saleLines.sumOf { line ->
        val qty = selectedQuantities[line.id] ?: 0
        qty * line.unitPrice
    }

    val totalSelectedItems = selectedQuantities.values.sum()
    val hasItemsSelected = totalSelectedItems > 0
    val isReasonValid = reasonText.isNotBlank()

    val maxEligibleRefund = sale?.let {
        minOf(totalReturnAmount, it.paidAmount)
    } ?: 0.0

    // Auto-update refund amount if issueRefund is enabled and user hasn't typed custom
    LaunchedEffect(totalReturnAmount, issueRefund) {
        if (issueRefund && refundAmountText.isBlank()) {
            refundAmountText = String.format("%.2f", maxEligibleRefund)
        }
    }

    val refundAmount = refundAmountText.toDoubleOrNull() ?: 0.0
    val isRefundValid = !issueRefund || (refundAmount > 0.0 && refundAmount <= maxEligibleRefund + 0.01)

    val canSubmit = hasItemsSelected && isReasonValid && isRefundValid

    AlertDialog(
        onDismissRequest = onDismiss,
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
                            imageVector = Icons.Default.Reply,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = if (isArabic) "مرتجع مبيعات" else "Sale Return",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
            }
        },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Sale Context Card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isArabic) "الفاتورة: ${sale?.invoiceNumber ?: transaction.title}" else "Invoice: ${sale?.invoiceNumber ?: transaction.title}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format("%.2f %s", transaction.amount, currency),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (transaction.customerNameSnapshot.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = transaction.customerNameSnapshot,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Items Selection Header
                    Text(
                        text = if (isArabic) "حدد الأصناف والكميات المرتجعة:" else "Select returned items and quantities:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (saleLines.isEmpty()) {
                        Text(
                            text = if (isArabic) "لا توجد أصناف قابلة للإرجاع" else "No returnable items found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        saleLines.forEach { line ->
                            val maxReturnable = returnableMap[line.id] ?: line.quantity
                            val currentQty = selectedQuantities[line.id] ?: 0

                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (currentQty > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .testTag("return_line_${line.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = line.productNameSnapshot,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isArabic)
                                                "${String.format("%.2f", line.unitPrice)} $currency × المتاح: $maxReturnable (المباع: ${line.quantity})"
                                            else
                                                "${String.format("%.2f", line.unitPrice)} $currency × Avail: $maxReturnable (Sold: ${line.quantity})",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Quantity selector
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (currentQty > 0) {
                                                    selectedQuantities[line.id] = currentQty - 1
                                                }
                                            },
                                            enabled = currentQty > 0,
                                            modifier = Modifier.size(32.dp).testTag("btn_minus_${line.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = "-",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Text(
                                            text = "$currentQty",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 4.dp).testTag("qty_text_${line.id}")
                                        )

                                        IconButton(
                                            onClick = {
                                                if (currentQty < maxReturnable) {
                                                    selectedQuantities[line.id] = currentQty + 1
                                                }
                                            },
                                            enabled = currentQty < maxReturnable,
                                            modifier = Modifier.size(32.dp).testTag("btn_plus_${line.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "+",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (attemptedSubmit && !hasItemsSelected) {
                        Text(
                            text = if (isArabic) "يرجى تحديد كمية صنف واحد على الأقل" else "Please select at least one item quantity",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Return Summary Bar
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp),
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
                                text = if (isArabic) "إجمالي المرتجع:" else "Total Return Value:",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = String.format("%.2f %s", totalReturnAmount, currency),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Reason Field
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text(if (isArabic) "سبب الإرجاع *" else "Return Reason *") },
                        isError = attemptedSubmit && !isReasonValid,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("return_reason_input")
                    )
                    if (attemptedSubmit && !isReasonValid) {
                        Text(
                            text = if (isArabic) "سبب الإرجاع إلزامي" else "Reason is required",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Refund Section (if original sale had cash paid)
                    if (sale != null && sale!!.paidAmount > 0) {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = issueRefund,
                                        onCheckedChange = { issueRefund = it },
                                        modifier = Modifier.testTag("issue_refund_checkbox")
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = if (isArabic) "صرف استرداد نقدي فوري" else "Issue Immediate Cash Refund",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isArabic)
                                                "الحد الأقصى المؤهل للاسترداد النقدي: ${String.format("%.2f", maxEligibleRefund)} $currency"
                                            else
                                                "Max eligible cash refund: ${String.format("%.2f", maxEligibleRefund)} $currency",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (issueRefund) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = refundAmountText,
                                        onValueChange = { refundAmountText = it },
                                        label = { Text(if (isArabic) "مبلغ الاسترداد النقدي" else "Refund Amount") },
                                        isError = attemptedSubmit && !isRefundValid,
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("refund_amount_input")
                                    )
                                    if (attemptedSubmit && !isRefundValid) {
                                        Text(
                                            text = if (isArabic) "المبلغ غير صالح أو يتجاوز الحد المؤهل" else "Invalid amount or exceeds eligible limit",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
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
            Button(
                onClick = {
                    attemptedSubmit = true
                    if (canSubmit) {
                        val requests = selectedQuantities.filter { it.value > 0 }.map { (lineId, qty) ->
                            SaleReturnLineRequest(saleLineId = lineId, quantity = qty)
                        }
                        val refundReq = if (issueRefund && refundAmount > 0.0) {
                            RefundRequest(amount = refundAmount, reason = reasonText)
                        } else null
                        onConfirm(requests, reasonText, refundReq)
                    }
                },
                enabled = !isLoading && hasItemsSelected && isReasonValid && isRefundValid,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("confirm_return_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isArabic) "تأكيد المرتجع" else "Confirm Return")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_return_button")
            ) {
                Text(if (isArabic) "إلغاء" else "Cancel")
            }
        }
    )
}
