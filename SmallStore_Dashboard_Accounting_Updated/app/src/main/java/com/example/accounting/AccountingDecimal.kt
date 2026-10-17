package com.example.accounting

import java.math.BigDecimal
import java.math.RoundingMode

/** Shared decimal policy for posted ILS amounts and intermediate line calculations.
 * Posted money is rounded half-up to 2 decimals; quantities/weights keep their source precision.
 */
object AccountingDecimal {
    private val MONEY_SCALE = 2
    fun parseMoney(value: String): BigDecimal {
        val normalized = value.trim().replace("\u066B", ".").replace("\u066C", "").replace(",", ".")
        require(normalized.isNotEmpty()) { "Amount is required" }
        val parsed = normalized.toBigDecimalOrNull() ?: throw IllegalArgumentException("Invalid amount")
        require(parsed >= BigDecimal.ZERO) { "Amount cannot be negative" }
        return parsed.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    }
    fun money(value: Double): Double {
        require(value.isFinite()) { "Money amount must be finite" }
        return BigDecimal.valueOf(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP).toDouble()
    }
    fun addMoney(vararg values: Double): Double = values.fold(BigDecimal.ZERO) { total, value ->
        require(value.isFinite()) { "Money amount must be finite" }
        total.add(BigDecimal.valueOf(value))
    }.setScale(MONEY_SCALE, RoundingMode.HALF_UP).toDouble()
    fun multiplyToMoney(unitPrice: Double, quantityOrWeight: Double): Double {
        require(unitPrice.isFinite() && quantityOrWeight.isFinite()) { "Price and quantity must be finite" }
        return BigDecimal.valueOf(unitPrice).multiply(BigDecimal.valueOf(quantityOrWeight))
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP).toDouble()
    }
}
