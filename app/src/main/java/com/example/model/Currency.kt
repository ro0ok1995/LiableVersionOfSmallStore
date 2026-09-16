package com.example.model

import java.util.Locale

/**
 * Centralized currency configuration for SmallStore.
 * The only currency is the Israeli New Shekel (₪).
 */
object AppCurrency {
    const val SYMBOL = "₪"

    /**
     * Formats an amount with the currency symbol in the Arabic-preferred display (e.g., "100 ₪" or "100.00 ₪").
     */
    fun formatAmount(amount: Double, isArabic: Boolean = true): String {
        return if (amount % 1.0 == 0.0) {
            String.format(Locale.US, "%,.0f %s", amount, SYMBOL)
        } else {
            String.format(Locale.US, "%,.2f %s", amount, SYMBOL)
        }
    }

    fun formatAmountWithDecimals(amount: Double, isArabic: Boolean = true): String {
        return String.format(Locale.US, "%,.2f %s", amount, SYMBOL)
    }

    fun formatAmount(amount: String, isArabic: Boolean = true): String {
        val num = amount.toDoubleOrNull() ?: 0.0
        return formatAmount(num, isArabic)
    }
}
