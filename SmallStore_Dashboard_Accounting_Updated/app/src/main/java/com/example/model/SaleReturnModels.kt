package com.example.model

import com.example.data.db.Refund
import com.example.data.db.SaleReturn
import com.example.data.db.SaleReturnLine

data class SaleReturnLineRequest(
    val saleLineId: String,
    val quantity: Int
)

data class RefundRequest(
    val amount: Double,
    val paymentMethodId: String? = null,
    val financialAccountId: String? = null,
    val reason: String? = null
)

data class SaleReturnResult(
    val saleReturn: SaleReturn,
    val lines: List<SaleReturnLine>,
    val refund: Refund?,
    val cogsReversed: Double,
    val grossProfitCorrection: Double
)
