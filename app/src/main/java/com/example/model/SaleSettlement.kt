package com.example.model

import java.lang.IllegalArgumentException

/**
 * Domain entity representing a validated sale settlement breakdown.
 *
 * Invariants:
 * 1. totalAmount == paidAmount + creditAmount (within EPSILON).
 * 2. totalAmount > 0, paidAmount >= 0, creditAmount >= 0.
 * 3. SaleType is deterministically derived:
 *    - creditAmount <= 0 -> CASH
 *    - paidAmount <= 0 -> CREDIT
 *    - paidAmount > 0 && creditAmount > 0 -> MIXED
 * 4. PaymentStatus is deterministically derived:
 *    - creditAmount <= 0 -> PAID
 *    - paidAmount <= 0 -> UNPAID
 *    - paidAmount > 0 && creditAmount > 0 -> PARTIAL
 */
data class SaleSettlement(
    val totalAmount: Double,
    val paidAmount: Double,
    val creditAmount: Double
) {
    companion object {
        const val EPSILON = 0.001

        /**
         * Validates and constructs a [SaleSettlement], throwing [IllegalArgumentException]
         * if the fundamental accounting equation totalAmount == paidAmount + creditAmount is violated.
         */
        fun create(totalAmount: Double, paidAmount: Double, creditAmount: Double): SaleSettlement {
            require(totalAmount > -EPSILON) { "totalAmount must not be negative: $totalAmount" }
            require(paidAmount > -EPSILON) { "paidAmount must not be negative: $paidAmount" }
            require(creditAmount > -EPSILON) { "creditAmount must not be negative: $creditAmount" }

            val difference = Math.abs(totalAmount - (paidAmount + creditAmount))
            require(difference <= EPSILON) {
                "Invalid sale settlement: totalAmount ($totalAmount) != paidAmount ($paidAmount) + creditAmount ($creditAmount)"
            }

            return SaleSettlement(
                totalAmount = totalAmount,
                paidAmount = paidAmount,
                creditAmount = creditAmount
            )
        }
    }

    init {
        val difference = Math.abs(totalAmount - (paidAmount + creditAmount))
        require(difference <= EPSILON) {
            "Invalid sale settlement: totalAmount ($totalAmount) != paidAmount ($paidAmount) + creditAmount ($creditAmount)"
        }
    }

    val saleType: SaleType
        get() = when {
            creditAmount <= EPSILON -> SaleType.CASH
            paidAmount <= EPSILON -> SaleType.CREDIT
            else -> SaleType.MIXED
        }

    val paymentStatus: PaymentStatus
        get() = when {
            creditAmount <= EPSILON -> PaymentStatus.PAID
            paidAmount <= EPSILON -> PaymentStatus.UNPAID
            else -> PaymentStatus.PARTIAL
        }
}

/**
 * Snapshot of a line item at the exact moment of sale.
 *
 * Invariant:
 * Historical line item records must preserve the exact costPriceAtSale and unitPriceAtSale,
 * immune to subsequent updates to the catalog [ProductItem.costPrice] or [ProductItem.price].
 */
data class SaleLineItemSnapshot(
    val productId: String,
    val productNameSnapshot: String,
    val quantity: Double,
    val unitPriceAtSale: Double,
    val costPriceAtSale: Double
) {
    val subtotal: Double
        get() = quantity * unitPriceAtSale

    val totalCost: Double
        get() = quantity * costPriceAtSale

    val profitMargin: Double
        get() = subtotal - totalCost
}
