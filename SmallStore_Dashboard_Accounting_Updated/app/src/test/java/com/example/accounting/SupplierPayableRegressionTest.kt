package com.example.accounting

import com.example.data.db.OpeningBalance
import com.example.data.db.Purchase
import com.example.data.db.PurchaseReturn
import com.example.data.db.SupplierPayment
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression examples for payable balance and cash-vs-credit purchase separation. */
class SupplierPayableRegressionTest {
    @Test
    fun cashPurchaseDoesNotIncreasePayableAndMixedPurchaseAddsOnlyCreditPortion() {
        val supplierId = "supplier-regression"
        val opening = OpeningBalance(
            id = "opening-1", entityType = "SUPPLIER", entityId = supplierId,
            amount = 500.0, direction = "CREDIT", date = "2026-10-01"
        )
        val purchases = listOf(
            Purchase(
                id = "cash-purchase", invoiceNumber = "C-1", supplierId = supplierId,
                purchaseDate = "2026-10-02", totalAmount = 1000.0, paidAmount = 1000.0,
                creditAmount = 0.0, paymentStatus = "PAID"
            ),
            Purchase(
                id = "mixed-purchase", invoiceNumber = "M-1", supplierId = supplierId,
                purchaseDate = "2026-10-03", totalAmount = 500.0, paidAmount = 300.0,
                creditAmount = 200.0, paymentStatus = "PARTIAL"
            )
        )
        val payments = listOf(SupplierPayment(
            id = "payment-1", supplierId = supplierId, amount = 250.0,
            paymentDate = "2026-10-04"
        ))
        val returns = listOf(PurchaseReturn(
            id = "return-1", purchaseId = "mixed-purchase", supplierId = supplierId,
            returnDate = "2026-10-05", amount = 50.0, reason = "Regression test"
        ))

        val result = SupplierLedgerCalculator.calculateSupplierBalance(
            supplierId = supplierId, purchases = purchases, payments = payments,
            returns = returns, openingBalances = listOf(opening)
        )

        assertEquals(1500.0, result.totalPurchases, 0.001)
        assertEquals(200.0, result.totalCreditPurchases, 0.001)
        assertEquals(400.0, result.balance, 0.001) // 500 + 200 - 250 - 50
    }

    @Test
    fun negativePayableIsAdvanceNotAmountOwed() {
        val supplierId = "supplier-advance"
        val result = SupplierLedgerCalculator.calculateSupplierBalance(
            supplierId = supplierId,
            purchases = emptyList(),
            payments = listOf(SupplierPayment(
                id = "advance-payment", supplierId = supplierId, amount = 120.0,
                paymentDate = "2026-10-01"
            ))
        )
        assertEquals(-120.0, result.balance, 0.001)
    }

    @Test
    fun supplierOpeningBalanceCreditAndDebitAffectsBalanceAndStatement() {
        val creditSupplier = "supplier-ob-credit"
        val creditOb = OpeningBalance(
            id = "ob-c1", entityType = "SUPPLIER", entityId = creditSupplier,
            amount = 750.0, direction = "CREDIT", date = "2026-10-01"
        )
        val creditSummary = SupplierLedgerCalculator.calculateSupplierBalance(
            supplierId = creditSupplier,
            purchases = emptyList(),
            payments = emptyList(),
            returns = emptyList(),
            openingBalances = listOf(creditOb)
        )
        assertEquals(750.0, creditSummary.openingBalance, 0.001)
        assertEquals(750.0, creditSummary.balance, 0.001)

        val creditStatement = SupplierLedgerCalculator.buildSupplierLedger(
            supplierId = creditSupplier,
            purchases = emptyList(),
            payments = emptyList(),
            returns = emptyList(),
            openingBalances = listOf(creditOb)
        )
        assertEquals(1, creditStatement.size)
        assertEquals(SupplierLedgerEntryType.OPENING_BALANCE, creditStatement[0].entryType)
        assertEquals(750.0, creditStatement[0].credit, 0.001)
        assertEquals(0.0, creditStatement[0].debit, 0.001)
        assertEquals(750.0, creditStatement[0].runningBalance, 0.001)

        // Debit opening balance (supplier advance)
        val debitSupplier = "supplier-ob-debit"
        val debitOb = OpeningBalance(
            id = "ob-d1", entityType = "SUPPLIER", entityId = debitSupplier,
            amount = 300.0, direction = "DEBIT", date = "2026-10-01"
        )
        val debitSummary = SupplierLedgerCalculator.calculateSupplierBalance(
            supplierId = debitSupplier,
            purchases = emptyList(),
            payments = emptyList(),
            returns = emptyList(),
            openingBalances = listOf(debitOb)
        )
        assertEquals(-300.0, debitSummary.openingBalance, 0.001)
        assertEquals(-300.0, debitSummary.balance, 0.001)

        val debitStatement = SupplierLedgerCalculator.buildSupplierLedger(
            supplierId = debitSupplier,
            purchases = emptyList(),
            payments = emptyList(),
            returns = emptyList(),
            openingBalances = listOf(debitOb)
        )
        assertEquals(1, debitStatement.size)
        assertEquals(SupplierLedgerEntryType.OPENING_BALANCE, debitStatement[0].entryType)
        assertEquals(0.0, debitStatement[0].credit, 0.001)
        assertEquals(300.0, debitStatement[0].debit, 0.001)
        assertEquals(-300.0, debitStatement[0].runningBalance, 0.001)
    }
}

