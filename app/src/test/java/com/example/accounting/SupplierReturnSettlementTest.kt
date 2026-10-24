package com.example.accounting

import com.example.data.db.Purchase
import com.example.data.db.PurchaseReturn
import com.example.data.db.SupplierPayment
import org.junit.Assert.assertEquals
import org.junit.Test

class SupplierReturnSettlementTest {
    @Test fun cashRefundDoesNotReducePayableTwice() {
        val purchase = Purchase(
            id = "p1", invoiceNumber = "P-1", supplierId = "s1", purchaseDate = "2026-10-13",
            totalAmount = 100.0, paidAmount = 60.0, creditAmount = 40.0, paymentStatus = "PARTIAL"
        )
        val returned = PurchaseReturn(
            id = "r1", purchaseId = "p1", supplierId = "s1", returnDate = "2026-10-13",
            amount = 50.0, reason = "test", payableReductionAmount = 40.0,
            cashRefundAmount = 10.0, financialAccountId = "acc_cash"
        )
        val balance = SupplierLedgerCalculator.calculateSupplierBalance(
            supplierId = "s1", purchases = listOf(purchase), returns = listOf(returned)
        ).balance
        assertEquals(0.0, balance, 0.0001)
    }

    @Test fun oldStyleReturnStillReducesPayableByFullAmount() {
        val purchase = Purchase(
            id = "p1", invoiceNumber = "P-1", supplierId = "s1", purchaseDate = "2026-10-13",
            totalAmount = 100.0, paidAmount = 0.0, creditAmount = 100.0, paymentStatus = "UNPAID"
        )
        val returned = PurchaseReturn(id = "r1", purchaseId = "p1", supplierId = "s1", returnDate = "2026-10-13", amount = 25.0, reason = "test")
        assertEquals(75.0, SupplierLedgerCalculator.calculateSupplierBalance("s1", listOf(purchase), returns = listOf(returned)).balance, 0.0001)
    }
    @Test fun supplierPaymentAbovePayableIsSplitInStatementButNotDuplicatedAsCash() {
        val purchase = Purchase(
            id = "p-split", invoiceNumber = "P-SPLIT", supplierId = "s-split", purchaseDate = "2026-10-13",
            totalAmount = 150.0, paidAmount = 0.0, creditAmount = 150.0, paymentStatus = "UNPAID"
        )
        val payment = SupplierPayment(
            id = "pay-split", supplierId = "s-split", amount = 200.0, paymentDate = "2026-10-14",
            financialAccountId = "acc_cash", referenceNumber = "SPAY-SPLIT", notes = "test"
        )
        val entries = SupplierLedgerCalculator.buildSupplierLedger(
            supplierId = "s-split", purchases = listOf(purchase), payments = listOf(payment)
        )
        val paymentEntries = entries.filter { it.id.startsWith("pay-split_") }
        assertEquals(2, paymentEntries.size)
        assertEquals(150.0, paymentEntries.first { it.id.endsWith("_settlement") }.debit, 0.0001)
        assertEquals(50.0, paymentEntries.first { it.id.endsWith("_advance") }.debit, 0.0001)
        assertEquals(-50.0, entries.last().runningBalance, 0.0001)
        // The persisted payment is still one 200-unit cash movement; the statement split is derived.
        assertEquals(200.0, payment.amount, 0.0001)
    }

}
