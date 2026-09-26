package com.example.model

import com.example.data.db.Purchase
import com.example.data.db.PurchaseLine

data class PurchaseLineRequest(
    val productId: String?,
    val productNameSnapshot: String,
    val quantity: Int,
    val unitCost: Double
)

data class PurchaseResult(
    val purchase: Purchase,
    val lines: List<PurchaseLine>,
    val supplierPayableIncrease: Double,
    val inventoryValueIncrease: Double,
    val financialAccountDeduction: Double
)
