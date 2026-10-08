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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
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

data class PredefinedReturnReason(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val descAr: String,
    val descEn: String
)

val PREDEFINED_RETURN_REASONS = listOf(
    PredefinedReturnReason("damaged", "المنتج تالف أو معيب", "Damaged or Defective Item", "عيب مصنعي أو كسر أو تلف يمنع الاستخدام.", "Manufacturing defect, breakage, or damage preventing use."),
    PredefinedReturnReason("incorrect_item", "المنتج غير مطابق للطلب", "Item Does Not Match Order", "الصنف المسلم لا يطابق طلب العميل.", "The supplied item does not match the customer order."),
    PredefinedReturnReason("expired", "المنتج منتهي الصلاحية", "Expired Product", "المنتج تجاوز تاريخ انتهاء صلاحيته.", "The product has passed its expiration date."),
    PredefinedReturnReason("incorrect_quantity", "الكمية غير صحيحة", "Incorrect Quantity", "تم تسجيل أو تسليم كمية غير صحيحة.", "The recorded or delivered quantity is incorrect."),
    PredefinedReturnReason("not_suitable", "المنتج غير مناسب للعميل", "Product Not Suitable", "المنتج لا يناسب العميل أو احتياجه.", "The product is not suitable for the customer or their needs."),
    PredefinedReturnReason("customer_changed_mind", "العميل غيّر رأيه", "Customer Changed Mind", "إرجاع المنتج بناءً على رغبة العميل.", "The customer changed their mind and returned the item."),
    PredefinedReturnReason("billing_error", "خطأ في الفاتورة", "Billing Error", "يوجد خطأ في تسجيل الفاتورة.", "The invoice was recorded incorrectly."),
    PredefinedReturnReason("product_error", "خطأ في المنتج", "Product Error", "تم تسجيل أو اختيار المنتج بشكل غير صحيح.", "The wrong product was recorded or selected."),
    PredefinedReturnReason("price_error", "خطأ في السعر", "Price Error", "تم تسجيل سعر غير صحيح.", "The wrong price was recorded."),
    PredefinedReturnReason("quality_issue", "مشكلة جودة", "Quality Issue", "مشكلة تتعلق بجودة المنتج.", "A product quality issue."),
    PredefinedReturnReason("agreed_return", "استرجاع متفق عليه", "Agreed Return", "إرجاع تم الاتفاق عليه مع العميل.", "A return agreed with the customer."),
    PredefinedReturnReason("operational", "سبب تشغيلي", "Operational Reason", "سبب متعلق بإجراء تشغيلي في المتجر.", "A store operational reason."),
    PredefinedReturnReason("other", "سبب آخر معتمد", "Other Approved Reason", "سبب استثنائي غير موجود في القائمة مع كتابة التفاصيل.", "An approved reason not listed above; details are required.")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSaleReturnDialog(
    transaction: TransactionItem,
    currency: String,
    isArabic: Boolean,
    onLoadDetails: suspend (String) -> Triple<Sale?, List<SaleLine>, Map<String, Int>>,
    onConfirm: (List<SaleReturnLineRequest>, String, String, RefundRequest?) -> Unit,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var sale by remember { mutableStateOf<Sale?>(null) }
    var saleLines by remember { mutableStateOf<List<SaleLine>>(emptyList()) }
    var returnableMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    // Map of saleLineId to selected return quantity
    val selectedQuantities = remember { mutableStateMapOf<String, Int>() }

    var selectedReasonId by remember { mutableStateOf<String?>(null) }
    var customReasonText by remember { mutableStateOf("") }
    var showReasonInfoDialog by remember { mutableStateOf(false) }
    var issueRefund by remember { mutableStateOf(false) }
    var refundAmountText by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

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
    val selectedReason = PREDEFINED_RETURN_REASONS.find { it.id == selectedReasonId }
    val isOtherReasonSelected = selectedReasonId == "other"
    val isReasonValid = when {
        selectedReasonId == null -> false
        isOtherReasonSelected -> customReasonText.isNotBlank()
        else -> true
    }

    val effectiveReason = when {
        isOtherReasonSelected -> customReasonText.trim()
        selectedReason != null -> if (isArabic) selectedReason.nameAr else selectedReason.nameEn
        else -> ""
    }

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

                    // Reason Selection Header with Info Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isArabic) "سبب الإرجاع *" else "Return Reason *",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { showReasonInfoDialog = true },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("return_reason_info_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = if (isArabic) "دليل أسباب الإرجاع" else "Return Reasons Guide",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    var reasonMenuExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = reasonMenuExpanded,
                        onExpandedChange = { reasonMenuExpanded = !reasonMenuExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("predefined_return_reasons_dropdown")
                    ) {
                        OutlinedTextField(
                            value = selectedReason?.let { if (isArabic) it.nameAr else it.nameEn } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (isArabic) "اختر سبب الإرجاع *" else "Select return reason *") },
                            placeholder = { Text(if (isArabic) "اختر من القائمة" else "Select from the list") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonMenuExpanded) },
                            isError = attemptedSubmit && selectedReasonId == null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = reasonMenuExpanded,
                            onDismissRequest = { reasonMenuExpanded = false }
                        ) {
                            PREDEFINED_RETURN_REASONS.forEach { reason ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(if (isArabic) reason.nameAr else reason.nameEn, fontWeight = FontWeight.SemiBold)
                                            Text(if (isArabic) reason.descAr else reason.descEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        selectedReasonId = reason.id
                                        reasonMenuExpanded = false
                                    },
                                    modifier = Modifier.testTag("reason_option_${reason.id}")
                                )
                            }
                        }
                    }

                    // Custom Reason Input - ONLY displayed when "سبب آخر" is selected
                    if (isOtherReasonSelected) {
                        OutlinedTextField(
                            value = customReasonText,
                            onValueChange = { customReasonText = it },
                            label = { Text(if (isArabic) "يرجى كتابة سبب الإرجاع *" else "Specify Return Reason *") },
                            placeholder = { Text(if (isArabic) "اكتب تفاصيل السبب هنا..." else "Enter reason details here...") },
                            isError = attemptedSubmit && customReasonText.isBlank(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("return_reason_input")
                        )
                        if (attemptedSubmit && customReasonText.isBlank()) {
                            Text(
                                text = if (isArabic) "يرجى كتابة سبب الإرجاع عند اختيار (سبب آخر) *" else "Please specify the reason details *",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (attemptedSubmit && selectedReasonId == null) {
                        Text(
                            text = if (isArabic) "يرجى اختيار سبب الإرجاع من القائمة *" else "Please select a return reason from the list *",
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
                    if (canSubmit && !isSubmitting) {
                        isSubmitting = true
                        val requests = selectedQuantities.filter { it.value > 0 }.map { (lineId, qty) ->
                            SaleReturnLineRequest(saleLineId = lineId, quantity = qty)
                        }
                        val refundReq = if (issueRefund && refundAmount > 0.0) {
                            RefundRequest(amount = refundAmount, reason = effectiveReason)
                        } else null
                        onConfirm(requests, selectedReasonId ?: "other", effectiveReason, refundReq)
                    }
                },
                enabled = !isLoading && !isSubmitting && hasItemsSelected && isReasonValid && isRefundValid,
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
                enabled = !isSubmitting,
                modifier = Modifier.testTag("cancel_return_button")
            ) {
                Text(if (isArabic) "إلغاء" else "Cancel")
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
                    text = if (isArabic) "دليل أسباب الإرجاع" else "Return Reasons Guide",
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
                    PREDEFINED_RETURN_REASONS.forEach { reason ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("info_reason_${reason.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (isArabic) reason.nameAr else reason.nameEn,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isArabic) reason.descAr else reason.descEn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showReasonInfoDialog = false },
                    modifier = Modifier.testTag("dismiss_return_reason_info_dialog")
                ) {
                    Text(if (isArabic) "فهمت" else "Got it")
                }
            },
            modifier = Modifier.testTag("return_reason_info_dialog")
        )
    }
}
