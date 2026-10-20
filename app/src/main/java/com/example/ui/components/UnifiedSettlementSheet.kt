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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.example.data.db.FinancialAccount
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
 * Top-level settlement mode tabs: Full (كلي) vs Partial (جزئي).
 */
enum class SettlementMode {
    FULL,
    PARTIAL
}

/**
 * Settlement payment method chosen in Full mode: Cash vs Credit.
 */
enum class FullPaymentMethod {
    CASH,
    CREDIT
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
    isCreditAllowed: Boolean = true,
    cartItems: List<CartItem> = emptyList(),
    transactionTotal: Double = 100.0,
    initialCashAmount: String = "50",
    initialDebtAmount: String = "40",
    initialNotes: String = "",
    financialAccounts: List<FinancialAccount> = emptyList(),
    onDismiss: () -> Unit = {},
    onComplete: (cash: Double, debt: Double, notes: String, financialAccountId: String?) -> Unit = { _, _, _, _ -> }
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
                isCreditAllowed = isCreditAllowed,
                cartItems = cartItems,
                transactionTotal = transactionTotal,
                initialCashAmount = initialCashAmount,
                initialDebtAmount = initialDebtAmount,
                initialNotes = initialNotes,
                financialAccounts = financialAccounts,
                onComplete = onComplete
            )
        }
    }
}

@Composable
fun UnifiedSettlementSheetContent(
    languageMode: LanguageMode = LanguageMode.ARABIC,
    settlementContext: SettlementContext = SettlementContext.RECORD_TRANSACTION,
    isCreditAllowed: Boolean = true,
    cartItems: List<CartItem> = emptyList(),
    transactionTotal: Double = 100.0,
    initialCashAmount: String = "50",
    initialDebtAmount: String = "40",
    initialNotes: String = "",
    financialAccounts: List<FinancialAccount> = emptyList(),
    onComplete: (cash: Double, debt: Double, notes: String, financialAccountId: String?) -> Unit = { _, _, _, _ -> }
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val currency = AppCurrency.SYMBOL
    val focusManager = LocalFocusManager.current

    fun formatAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", amount)
        } else {
            String.format(Locale.US, "%.2f", amount)
        }
    }

    // Top-level Settlement Tab: Full (كلي) vs Partial (جزئي)
    // Anonymous customer flow: If no customer is selected (isCreditAllowed == false),
    // Partial tab is not allowed and mode is strictly forced to FULL.
    var settlementMode by remember(isCreditAllowed) { mutableStateOf(SettlementMode.FULL) }
    val effectiveMode = if (isCreditAllowed) settlementMode else SettlementMode.FULL

    // In FULL mode: choose Cash vs Credit. Credit is only available if isCreditAllowed.
    var fullMethod by remember(isCreditAllowed) { mutableStateOf(FullPaymentMethod.CASH) }
    val effectiveFullMethod = if (isCreditAllowed) fullMethod else FullPaymentMethod.CASH

    var cashAmountText by remember(transactionTotal, isCreditAllowed) {
        mutableStateOf(formatAmount(transactionTotal))
    }
    var debtAmountText by remember(isCreditAllowed) {
        mutableStateOf("0")
    }
    var notesText by remember(initialNotes) { mutableStateOf(initialNotes) }

    val activeAccounts = remember(financialAccounts) { financialAccounts.filter { it.isActive } }
    var selectedAccountId by remember(financialAccounts) {
        mutableStateOf(
            activeAccounts.find { it.id == "acc_cash" }?.id
                ?: activeAccounts.firstOrNull()?.id
                ?: "acc_cash"
        )
    }

    // Calculate parsed values based on mode
    val rawCash = cashAmountText.toDoubleOrNull() ?: 0.0
    val rawDebt = debtAmountText.toDoubleOrNull() ?: 0.0

    val parsedCash = when (effectiveMode) {
        SettlementMode.FULL -> if (effectiveFullMethod == FullPaymentMethod.CASH) rawCash else 0.0
        SettlementMode.PARTIAL -> rawCash
    }
    val parsedDebt = when (effectiveMode) {
        SettlementMode.FULL -> if (effectiveFullMethod == FullPaymentMethod.CREDIT) rawDebt else 0.0
        SettlementMode.PARTIAL -> rawDebt
    }

    val totalPaid = parsedCash + parsedDebt
    val remainingBalance = transactionTotal - totalPaid

    // Validation
    val isRemainingZero = Math.abs(remainingBalance) <= 0.001
    val isTotalPaidValid = Math.abs(totalPaid - transactionTotal) <= 0.001 && transactionTotal > 0.0
    val isValidAllocation = isRemainingZero && isTotalPaidValid && (parsedCash >= -0.001) && (parsedDebt >= -0.001) &&
        when (effectiveMode) {
            SettlementMode.FULL -> {
                if (effectiveFullMethod == FullPaymentMethod.CASH) {
                    parsedDebt <= 0.001 && Math.abs(parsedCash - transactionTotal) <= 0.001
                } else {
                    isCreditAllowed && parsedCash <= 0.001 && Math.abs(parsedDebt - transactionTotal) <= 0.001
                }
            }
            SettlementMode.PARTIAL -> {
                isCreditAllowed && isRemainingZero
            }
        }

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

            // READ-ONLY PRODUCT LIST SUMMARY (RECORD_TRANSACTION mode)
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

            Spacer(modifier = Modifier.height(16.dp))

            // TOP-LEVEL SETTLEMENT TABS: "كلي" / "Full" vs "جزئي" / "Partial"
            // If credit is not allowed (anonymous customer), only Full Cash is available; Partial tab is not displayed.
            if (isCreditAllowed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // TAB 1: FULL
                    Surface(
                        selected = effectiveMode == SettlementMode.FULL,
                        onClick = {
                            settlementMode = SettlementMode.FULL
                            if (effectiveFullMethod == FullPaymentMethod.CASH) {
                                cashAmountText = formatAmount(transactionTotal)
                                debtAmountText = "0"
                            } else {
                                cashAmountText = "0"
                                debtAmountText = formatAmount(transactionTotal)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (effectiveMode == SettlementMode.FULL) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (effectiveMode == SettlementMode.FULL) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("settlement_tab_full")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isArabic) "كلي" else "Full",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // TAB 2: PARTIAL
                    Surface(
                        selected = effectiveMode == SettlementMode.PARTIAL,
                        onClick = {
                            settlementMode = SettlementMode.PARTIAL
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (effectiveMode == SettlementMode.PARTIAL) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (effectiveMode == SettlementMode.PARTIAL) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("settlement_tab_partial")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isArabic) "جزئي" else "Partial",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // FULL MODE PRESENTATION
            if (effectiveMode == SettlementMode.FULL) {
                // In Full mode with a selected customer, choose exactly ONE settlement method: Cash or Credit
                if (isCreditAllowed) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = effectiveFullMethod == FullPaymentMethod.CASH,
                            onClick = {
                                fullMethod = FullPaymentMethod.CASH
                                cashAmountText = formatAmount(transactionTotal)
                                debtAmountText = "0"
                            },
                            label = {
                                Text(
                                    text = if (isArabic) "نقدي" else "Cash",
                                    fontWeight = if (effectiveFullMethod == FullPaymentMethod.CASH) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settlement_method_cash")
                        )
                        FilterChip(
                            selected = effectiveFullMethod == FullPaymentMethod.CREDIT,
                            onClick = {
                                fullMethod = FullPaymentMethod.CREDIT
                                debtAmountText = formatAmount(transactionTotal)
                                cashAmountText = "0"
                            },
                            label = {
                                Text(
                                    text = if (isArabic) "آجل" else "Credit",
                                    fontWeight = if (effectiveFullMethod == FullPaymentMethod.CREDIT) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settlement_method_credit")
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // If Cash is selected: Show only Cash input. Do NOT show Credit input.
                if (effectiveFullMethod == FullPaymentMethod.CASH) {
                    SettlementFieldLabel(
                        text = if (isArabic) StoreStrings.CASH_AMOUNT_LABEL_AR else StoreStrings.CASH_AMOUNT_LABEL_EN
                    )
                    OutlinedTextField(
                        value = cashAmountText,
                        onValueChange = { input ->
                            if (isValidNumberInput(input, transactionTotal)) {
                                cashAmountText = input
                            }
                        },
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

                    // FINANCIAL ACCOUNT SELECTION FOR CASH
                    if (activeAccounts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isArabic) "إيداع المبلغ في الحساب المالي:" else "Deposit paid amount into account:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settlement_account_row")
                        ) {
                            items(activeAccounts) { acc ->
                                FilterChip(
                                    selected = selectedAccountId == acc.id,
                                    onClick = { selectedAccountId = acc.id },
                                    label = { Text(acc.name) },
                                    modifier = Modifier.testTag("settlement_chip_account_${acc.id}")
                                )
                            }
                        }
                    }
                } else {
                    // If Credit is selected: Show only Credit input. Cash is not active/visible.
                    SettlementFieldLabel(
                        text = if (isArabic) StoreStrings.DEBT_AMOUNT_LABEL_AR else StoreStrings.DEBT_AMOUNT_LABEL_EN
                    )
                    OutlinedTextField(
                        value = debtAmountText,
                        onValueChange = { input ->
                            if (isValidNumberInput(input, transactionTotal)) {
                                debtAmountText = input
                            }
                        },
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
                }
            } else {
                // PARTIAL MODE: Show both Cash and Credit inputs with allocation limits
                val currentDebt = debtAmountText.toDoubleOrNull() ?: 0.0
                val maxCashAllowed = (transactionTotal - currentDebt).coerceAtLeast(0.0)

                val currentCash = cashAmountText.toDoubleOrNull() ?: 0.0
                val maxDebtAllowed = (transactionTotal - currentCash).coerceAtLeast(0.0)

                // Cash Amount Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettlementFieldLabel(
                        text = if (isArabic) StoreStrings.CASH_AMOUNT_LABEL_AR else StoreStrings.CASH_AMOUNT_LABEL_EN
                    )
                    if (maxCashAllowed > 0.001) {
                        Text(
                            text = if (isArabic) "المتبقي (${formatAmount(maxCashAllowed)})" else "Remaining (${formatAmount(maxCashAllowed)})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .clickable { cashAmountText = formatAmount(maxCashAllowed) }
                                .padding(bottom = 6.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = cashAmountText,
                    onValueChange = { input ->
                        if (isValidNumberInput(input, maxCashAllowed)) {
                            cashAmountText = input
                        }
                    },
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

                // Financial account selector for cash portion
                if (activeAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isArabic) "إيداع المبلغ في الحساب المالي:" else "Deposit paid amount into account:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settlement_account_row")
                    ) {
                        items(activeAccounts) { acc ->
                            FilterChip(
                                selected = selectedAccountId == acc.id,
                                onClick = { selectedAccountId = acc.id },
                                label = { Text(acc.name) },
                                modifier = Modifier.testTag("settlement_chip_account_${acc.id}")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Debt Amount Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettlementFieldLabel(
                        text = if (isArabic) StoreStrings.DEBT_AMOUNT_LABEL_AR else StoreStrings.DEBT_AMOUNT_LABEL_EN
                    )
                    if (maxDebtAllowed > 0.001) {
                        Text(
                            text = if (isArabic) "المتبقي (${formatAmount(maxDebtAllowed)})" else "Remaining (${formatAmount(maxDebtAllowed)})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .clickable { debtAmountText = formatAmount(maxDebtAllowed) }
                                .padding(bottom = 6.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = debtAmountText,
                    onValueChange = { input ->
                        if (isValidNumberInput(input, maxDebtAllowed)) {
                            debtAmountText = input
                        }
                    },
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
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. ALLOCATION SUMMARY (Invoice Total, Allocated, Remaining)
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
                    // Line 1: Invoice Total
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "إجمالي الفاتورة" else "Invoice Total",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f %s", transactionTotal, currency),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Line 2: Allocated = Cash Amount + Debt Amount
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settlement_total_paid_summary"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isArabic) "المبلغ المخصص" else "Allocated",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isArabic) {
                                    "نقدي: ${String.format(Locale.US, "%.2f", parsedCash)} + آجل: ${String.format(Locale.US, "%.2f", parsedDebt)}"
                                } else {
                                    "Cash: ${String.format(Locale.US, "%.2f", parsedCash)} + Credit: ${String.format(Locale.US, "%.2f", parsedDebt)}"
                                },
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
                            color = if (isRemainingZero) MaterialTheme.colorScheme.statusGreen else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Line 3: Remaining = Invoice Total - Allocated
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settlement_remaining_summary"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isArabic) "المتبقي" else "Remaining",
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
                            color = if (isRemainingZero) MaterialTheme.colorScheme.statusGreen else MaterialTheme.colorScheme.statusRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. OPTIONAL NOTES FIELD
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
                        if (!isValidAllocation) return@Button
                        focusManager.clearFocus()
                        onComplete(parsedCash, parsedDebt, notesText.trim(), selectedAccountId)
                    },
                    enabled = isValidAllocation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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

private fun isValidNumberInput(newText: String, maxAllowed: Double): Boolean {
    if (newText.isEmpty()) return true
    if (newText.contains("-")) return false
    if (newText.count { it == '.' } > 1) return false
    if (newText == ".") return maxAllowed >= 0.0
    val numStr = if (newText.endsWith(".")) newText.dropLast(1) else newText
    val value = numStr.toDoubleOrNull() ?: return false
    if (value < 0.0) return false
    return value <= maxAllowed + 0.001
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
