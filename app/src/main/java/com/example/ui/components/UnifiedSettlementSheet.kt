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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCurrency
import com.example.model.CartItem
import com.example.model.LanguageMode
import com.example.model.StoreStrings
import com.example.ui.theme.statusGreen
import com.example.ui.theme.statusRed
import java.util.Locale

/**
 * Settlement mode/context for UnifiedSettlementSheet.
 * Supports RECORD_TRANSACTION (from Purchases checkout) and QUICK_PAYMENT_LEGACY.
 */
enum class SettlementContext {
    QUICK_PAYMENT_LEGACY,
    RECORD_TRANSACTION
}

/**
 * UNIFIED SETTLEMENT dialog/bottom-sheet of SmallStore.
 * Generic, reusable version used both after "Complete Transaction" in Purchases
 * and for legacy quick payment settlements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSettlementSheet(
    isOpen: Boolean,
    languageMode: LanguageMode = LanguageMode.ARABIC,
    settlementContext: SettlementContext = SettlementContext.RECORD_TRANSACTION,
    cartItems: List<CartItem> = emptyList(),
    transactionTotal: Double = 100.0,
    initialCashAmount: String = "50",
    initialDebtAmount: String = "40",
    initialNotes: String = "",
    onDismiss: () -> Unit = {},
    onComplete: (cash: Double, debt: Double, notes: String) -> Unit = { _, _, _ -> }
) {
    if (!isOpen) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val layoutDirection = if (languageMode == LanguageMode.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .testTag("settlement_drag_handle")
                )
            },
            modifier = Modifier.testTag("unified_settlement_sheet")
        ) {
            UnifiedSettlementSheetContent(
                languageMode = languageMode,
                settlementContext = settlementContext,
                cartItems = cartItems,
                transactionTotal = transactionTotal,
                initialCashAmount = initialCashAmount,
                initialDebtAmount = initialDebtAmount,
                initialNotes = initialNotes,
                onComplete = onComplete
            )
        }
    }
}

@Composable
fun UnifiedSettlementSheetContent(
    languageMode: LanguageMode = LanguageMode.ARABIC,
    settlementContext: SettlementContext = SettlementContext.RECORD_TRANSACTION,
    cartItems: List<CartItem> = emptyList(),
    transactionTotal: Double = 100.0,
    initialCashAmount: String = "50",
    initialDebtAmount: String = "40",
    initialNotes: String = "",
    onComplete: (cash: Double, debt: Double, notes: String) -> Unit = { _, _, _ -> }
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val currency = AppCurrency.SYMBOL
    val focusManager = LocalFocusManager.current

    var cashAmountText by remember(initialCashAmount) { mutableStateOf(initialCashAmount) }
    var debtAmountText by remember(initialDebtAmount) { mutableStateOf(initialDebtAmount) }
    var notesText by remember(initialNotes) { mutableStateOf(initialNotes) }

    val parsedCash = cashAmountText.toDoubleOrNull() ?: 0.0
    val parsedDebt = debtAmountText.toDoubleOrNull() ?: 0.0
    val totalPaid = parsedCash + parsedDebt
    val remainingBalance = transactionTotal - totalPaid

    val sheetTitle = when (settlementContext) {
        SettlementContext.RECORD_TRANSACTION -> {
            if (isArabic) "تسجيل المعاملة" else "Record Transaction"
        }
        SettlementContext.QUICK_PAYMENT_LEGACY -> {
            if (isArabic) StoreStrings.SETTLEMENT_AR else StoreStrings.SETTLEMENT_EN
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unified_settlement_content")
    ) {
        // 1. TITLE
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = sheetTitle,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("settlement_title")
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        // 2. SCROLLABLE FORM BODY
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            // PROMINENT TRANSACTION TOTAL AT TOP
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settlement_transaction_total_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isArabic) StoreStrings.TRANSACTION_TOTAL_AR else StoreStrings.TRANSACTION_TOTAL_EN,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.2f %s", transactionTotal, currency),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("settlement_transaction_total")
                    )
                }
            }

            // READ-ONLY PRODUCT LIST SUMMARY (Displayed at the TOP of the sheet, ABOVE the notes section, only in RECORD_TRANSACTION mode)
            if (settlementContext == SettlementContext.RECORD_TRANSACTION && cartItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                SettlementFieldLabel(
                    text = if (isArabic) "المنتجات المحددة (${cartItems.sumOf { it.quantity }})" else "Selected Products (${cartItems.sumOf { it.quantity }})"
                )
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settlement_cart_items_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        cartItems.forEachIndexed { index, item ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settlement_item_row_${item.product.id}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.product.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("settlement_item_name_${item.product.id}")
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${item.quantity} × ${String.format(Locale.US, "%.2f %s", item.product.price, currency)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag("settlement_item_qty_price_${item.product.id}")
                                    )
                                }
                                Text(
                                    text = String.format(Locale.US, "%.2f %s", item.product.price * item.quantity, currency),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("settlement_item_subtotal_${item.product.id}")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // CASH AMOUNT
            SettlementFieldLabel(
                text = if (isArabic) StoreStrings.CASH_AMOUNT_LABEL_AR else StoreStrings.CASH_AMOUNT_LABEL_EN
            )
            OutlinedTextField(
                value = cashAmountText,
                onValueChange = { cashAmountText = it },
                placeholder = { Text("0.00") },
                trailingIcon = {
                    Text(
                        text = currency,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settlement_cash_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // DEBT AMOUNT
            SettlementFieldLabel(
                text = if (isArabic) StoreStrings.DEBT_AMOUNT_LABEL_AR else StoreStrings.DEBT_AMOUNT_LABEL_EN
            )
            OutlinedTextField(
                value = debtAmountText,
                onValueChange = { debtAmountText = it },
                placeholder = { Text("0.00") },
                trailingIcon = {
                    Text(
                        text = currency,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settlement_debt_input")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3. TWO COMPUTED READ-ONLY SUMMARY LINES
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settlement_summary_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Line 1: Total Paid = Cash Amount + Debt Amount
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settlement_total_paid_summary"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isArabic) StoreStrings.TOTAL_PAID_FORMULA_AR else StoreStrings.TOTAL_PAID_FORMULA_EN,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.2f", parsedCash)} + ${String.format(Locale.US, "%.2f", parsedDebt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.2f %s", totalPaid, currency),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.statusGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Line 2: Remaining Balance = Transaction Total - Total Paid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settlement_remaining_summary"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isArabic) StoreStrings.REMAINING_BALANCE_FORMULA_AR else StoreStrings.REMAINING_BALANCE_FORMULA_EN,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.2f", transactionTotal)} - ${String.format(Locale.US, "%.2f", totalPaid)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.2f %s", remainingBalance, currency),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = if (remainingBalance > 0) MaterialTheme.colorScheme.statusRed else MaterialTheme.colorScheme.statusGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. OPTIONAL "NOTES" FIELD
            val optionalLabel = if (isArabic) StoreStrings.NOTES_LABEL_AR else StoreStrings.NOTES_LABEL_EN
            SettlementFieldLabel(text = optionalLabel)
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                placeholder = {
                    Text(
                        text = if (isArabic) "أدخل أي ملاحظات إضافية هنا..." else "Enter any additional notes here...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settlement_notes_input")
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 5. STICKY FOOTER ACTION BUTTON
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onComplete(parsedCash, parsedDebt, notesText.trim())
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("settlement_complete_button")
                ) {
                    Text(
                        text = if (isArabic) StoreStrings.COMPLETE_ACTION_AR else StoreStrings.COMPLETE_ACTION_EN,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SettlementFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
