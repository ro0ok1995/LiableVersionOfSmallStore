package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.model.LanguageMode
import com.example.model.StoreStrings
import com.example.ui.components.CustomerSelectorField
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusRedBg
import java.util.Locale

/**
 * Quick Payment Screen (تسديد سريع)
 *
 * A dedicated cash debt-reduction tool.
 * Flow (Top to Bottom):
 *   a. Customer field (Shared CustomerSelectorField: default "All Customers ▼", shows "X" to clear)
 *   b. Amount field (Single numeric input with currency symbol ₪, with real debt validation)
 *   c. Notes field (Optional)
 *   d. Confirm Payment button ("Confirm Payment" / "تأكيد التسديد")
 *
 * NOTE: There is no cash/debt toggle, no Full/Partial toggle, and no settlement-type selector.
 */
@Composable
fun QuickPaymentScreen(
    customer: CustomerAccount?,
    allCustomers: List<CustomerAccount>,
    amount: String,
    notes: String,
    languageMode: LanguageMode = LanguageMode.ARABIC,
    onBackClick: () -> Unit = {},
    onCustomerChange: (CustomerAccount?) -> Unit = {},
    onAmountChange: (String) -> Unit = {},
    onNotesChange: (String) -> Unit = {},
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val currency = AppCurrency.SYMBOL
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Validation logic against real customer balance
    val parsedAmount = amount.toDoubleOrNull()
    val customerDebt = customer?.balance ?: 0.0
    val isExceeded = customer != null && parsedAmount != null && parsedAmount > (customerDebt + 0.001)
    val hasZeroOrNegativeDebt = customer != null && customerDebt <= 0.0
    val isValid = customer != null &&
            parsedAmount != null &&
            parsedAmount > 0.0 &&
            !isExceeded &&
            customerDebt > 0.0

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("quick_payment_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onBackClick()
                        },
                        modifier = Modifier.testTag("quick_payment_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isArabic) "رجوع" else "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isArabic) StoreStrings.QUICK_PAYMENT_AR else StoreStrings.QUICK_PAYMENT_EN,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("quick_payment_title")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // =================================================================
            // a. CUSTOMER FIELD (Shared CustomerSelectorField)
            // =================================================================
            Text(
                text = if (isArabic) "العميل *" else "Customer *",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            CustomerSelectorField(
                customers = allCustomers,
                selectedCustomer = customer,
                onCustomerSelected = { cust ->
                    focusManager.clearFocus()
                    onCustomerChange(cust)
                },
                isArabic = isArabic,
                allowAllCustomers = true,
                allCustomersLabel = if (isArabic) StoreStrings.ALL_CUSTOMERS_AR else StoreStrings.ALL_CUSTOMERS_EN,
                placeholderText = if (isArabic) StoreStrings.ALL_CUSTOMERS_AR else StoreStrings.ALL_CUSTOMERS_EN,
                showBalance = true,
                isRequired = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_payment_customer_selector"),
                testTag = "quick_payment_customer"
            )

            // Current Outstanding Debt Display (if customer selected)
            if (customer != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (customer.balance > 0) StatusRedBg else StatusGreenBg
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (customer.balance > 0) StatusRed.copy(alpha = 0.3f) else StatusGreen.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_payment_customer_debt_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = if (customer.balance > 0) StatusRed else StatusGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isArabic) "الرصيد المستحق (الدين):" else "Current Outstanding Debt:",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%,.2f %s", customer.balance, currency),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (customer.balance > 0) StatusRed else StatusGreen
                            ),
                            modifier = Modifier.testTag("quick_payment_customer_debt_amount")
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isArabic) "يرجى تحديد العميل لتسديد الدين" else "Please select a customer to record payment",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // =================================================================
            // b. AMOUNT FIELD (Single numeric input with currency symbol ₪)
            // =================================================================
            Text(
                text = if (isArabic) "المبلغ ($currency) *" else "Amount ($currency) *",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { input ->
                    val sanitized = input.filter { it.isDigit() || it == '.' }
                    if (sanitized.count { it == '.' } <= 1) {
                        onAmountChange(sanitized)
                    }
                },
                placeholder = {
                    Text(
                        text = if (customer != null && customer.balance > 0) {
                            String.format(Locale.US, "%.2f", customer.balance)
                        } else {
                            "0.00"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                trailingIcon = {
                    Text(
                        text = currency,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = GeoPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = isExceeded,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isExceeded) StatusRed else GeoPrimary,
                    unfocusedBorderColor = if (isExceeded) StatusRed else GeoOutlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_payment_amount_input")
            )

            // Inline Validation Error: Amount exceeds customer's debt
            if (isExceeded) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusRedBg)
                        .border(1.dp, StatusRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("quick_payment_validation_error")
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = StatusRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) {
                            StoreStrings.PAYMENT_EXCEEDS_DEBT_ERROR_AR
                        } else {
                            StoreStrings.PAYMENT_EXCEEDS_DEBT_ERROR_EN
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = StatusRed
                    )
                }
            } else if (hasZeroOrNegativeDebt) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusGreenBg)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "هذا العميل ليس عليه ديون مستحقة (الرصيد خالص)" else "This customer has no outstanding debt (Paid up)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = StatusGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // =================================================================
            // c. NOTES FIELD (Optional, consistent visual style)
            // =================================================================
            Text(
                text = if (isArabic) "${StoreStrings.NOTES_LABEL_AR} (${if (isArabic) "اختياري" else "Optional"})" else "${StoreStrings.NOTES_LABEL_EN} (Optional)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                placeholder = {
                    Text(
                        text = if (isArabic) "أدخل أي ملاحظات للتسديد..." else "Enter any payment notes...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                minLines = 3,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GeoPrimary,
                    unfocusedBorderColor = GeoOutlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_payment_notes_input")
            )

            Spacer(modifier = Modifier.height(28.dp))

            // =================================================================
            // d. CONFIRM PAYMENT BUTTON ("Confirm Payment" / "تأكيد التسديد")
            // =================================================================
            Button(
                onClick = {
                    if (isValid) {
                        focusManager.clearFocus()
                        Toast.makeText(
                            context,
                            if (isArabic) "تم تسجيل التسديد بنجاح" else "Payment recorded successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        onComplete()
                    }
                },
                enabled = isValid,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GeoPrimary,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("quick_payment_confirm_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isArabic) StoreStrings.CONFIRM_PAYMENT_AR else StoreStrings.CONFIRM_PAYMENT_EN,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
