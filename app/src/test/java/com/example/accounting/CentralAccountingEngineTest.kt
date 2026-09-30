package com.example.accounting

import com.example.data.db.Adjustment
import com.example.data.db.CustomerPayment
import com.example.data.db.Purchase
import com.example.data.db.PurchaseReturn
import com.example.data.db.Sale
import com.example.data.db.SaleReturn
import com.example.data.db.StockMovementEntity
import com.example.data.db.SupplierPayment
import com.example.model.OperationStatus
import com.example.model.PaymentStatus
import com.example.model.SaleType
import com.example.model.TransactionItem
import com.example.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 Verification Test:
 * Tests the unified CentralAccountingEngine calculation source across all four domains:
 * 1. Customer Receivable Ledger
 * 2. Supplier Payable Ledger
 * 3. Inventory Stock on Hand & Valuation
 * 4. Financial Reporting Totals
 */
class CentralAccountingEngineTest {

    @Test
    fun testCustomerReceivableLedgerCalculation() {
        val customerId = "cust_central_1"

        val sales = listOf(
            Sale(
                id = "sale_1",
                invoiceNumber = "INV-000001",
                customerId = customerId,
                saleType = "CREDIT",
                totalAmount = 200.0,
                paidAmount = 50.0,
                creditAmount = 150.0,
                paymentStatus = "PARTIAL",
                transactionDate = "2026-09-29",
                status = "ACTIVE"
            ),
            Sale(
                id = "sale_reversed",
                invoiceNumber = "INV-000002",
                customerId = customerId,
                saleType = "CREDIT",
                totalAmount = 100.0,
                paidAmount = 0.0,
                creditAmount = 100.0,
                paymentStatus = "UNPAID",
                transactionDate = "2026-09-29",
                status = "REVERSED"
            )
        )

        val payments = listOf(
            CustomerPayment(
                id = "pay_1",
                customerId = customerId,
                amount = 40.0,
                paymentMethodId = "pm_cash",
                transactionDate = "2026-09-29",
                status = "ACTIVE"
            )
        )

        val returns = listOf(
            SaleReturn(
                id = "ret_1",
                saleId = "sale_1",
                customerId = customerId,
                returnDate = "2026-09-29",
                reason = "Defective",
                amount = 20.0,
                status = "ACTIVE"
            )
        )

        // Customer owes: 150 (credit sale) - 40 (payment) - 20 (return) = 90
        val summary = CentralAccountingEngine.calculateCustomerBalance(
            customerId = customerId,
            sales = sales,
            payments = payments,
            saleReturns = returns
        )

        assertEquals(90.0, summary.balance, 0.001)
        assertTrue(summary.isDebitBalance)
        assertFalse(summary.isSettled)

        // Verify AccountingEngine alias works identically
        val aliasSummary = AccountingEngine.calculateCustomerBalance(
            customerId = customerId,
            sales = sales,
            payments = payments,
            saleReturns = returns
        )
        assertEquals(summary.balance, aliasSummary.balance, 0.001)
    }

    @Test
    fun testSupplierPayableLedgerCalculation() {
        val supplierId = "supp_central_1"

        val purchases = listOf(
            Purchase(
                id = "purch_1",
                invoiceNumber = "PINV-000001",
                supplierId = supplierId,
                purchaseDate = "2026-09-29",
                totalAmount = 300.0,
                paidAmount = 100.0,
                creditAmount = 200.0,
                paymentStatus = "PARTIAL",
                status = "ACTIVE"
            ),
            Purchase(
                id = "purch_reversed",
                invoiceNumber = "PINV-000002",
                supplierId = supplierId,
                purchaseDate = "2026-09-29",
                totalAmount = 500.0,
                paidAmount = 0.0,
                creditAmount = 500.0,
                paymentStatus = "UNPAID",
                status = "REVERSED"
            )
        )

        val payments = listOf(
            SupplierPayment(
                id = "spay_1",
                supplierId = supplierId,
                amount = 80.0,
                paymentDate = "2026-09-29",
                status = "ACTIVE"
            )
        )

        val returns = listOf(
            PurchaseReturn(
                id = "pret_1",
                purchaseId = "purch_1",
                supplierId = supplierId,
                returnDate = "2026-09-29",
                reason = "Incorrect item",
                amount = 30.0,
                status = "ACTIVE"
            )
        )

        // Store owes supplier: 200 (credit purchase) - 80 (payment) - 30 (return) = 90
        val summary = CentralAccountingEngine.calculateSupplierBalance(
            supplierId = supplierId,
            purchases = purchases,
            payments = payments,
            returns = returns
        )

        assertEquals(90.0, summary.balance, 0.001)
        assertTrue(summary.isPayable)
        assertFalse(summary.isSettled)
    }

    @Test
    fun testInventoryLedgerCalculationFromPersistentStockMovements() {
        val productId = "prod_central_1"

        // Phase 1 Scenario:
        // Opening = 0
        // Purchase +10 = 10
        // Sale -3 = 7
        // Sale Return +1 = 8
        // Purchase Return -2 = 6
        // Adjustment +4 = 10
        // Adjustment -3 = 7
        // Plus a REVERSED movement that MUST be ignored: Sale -5 (REVERSED)
        val movements = listOf(
            StockMovementEntity(
                id = "sm_1",
                productId = productId,
                movementType = InventoryMovementType.PURCHASE_IN.name,
                quantityIn = 10,
                quantityOut = 0,
                unitCost = 15.0,
                status = "ACTIVE"
            ),
            StockMovementEntity(
                id = "sm_2",
                productId = productId,
                movementType = InventoryMovementType.SALE_OUT.name,
                quantityIn = 0,
                quantityOut = 3,
                unitCost = 15.0,
                status = "ACTIVE"
            ),
            StockMovementEntity(
                id = "sm_3",
                productId = productId,
                movementType = InventoryMovementType.SALE_RETURN_IN.name,
                quantityIn = 1,
                quantityOut = 0,
                unitCost = 15.0,
                status = "ACTIVE"
            ),
            StockMovementEntity(
                id = "sm_4",
                productId = productId,
                movementType = InventoryMovementType.PURCHASE_RETURN_OUT.name,
                quantityIn = 0,
                quantityOut = 2,
                unitCost = 15.0,
                status = "ACTIVE"
            ),
            StockMovementEntity(
                id = "sm_5",
                productId = productId,
                movementType = InventoryMovementType.ADJUSTMENT_IN.name,
                quantityIn = 4,
                quantityOut = 0,
                unitCost = 15.0,
                status = "ACTIVE"
            ),
            StockMovementEntity(
                id = "sm_6",
                productId = productId,
                movementType = InventoryMovementType.ADJUSTMENT_OUT.name,
                quantityIn = 0,
                quantityOut = 3,
                unitCost = 15.0,
                status = "ACTIVE"
            ),
            StockMovementEntity(
                id = "sm_reversed",
                productId = productId,
                movementType = InventoryMovementType.SALE_OUT.name,
                quantityIn = 0,
                quantityOut = 5,
                unitCost = 15.0,
                status = "REVERSED"
            )
        )

        val stock = CentralAccountingEngine.calculateProductStockFromMovements(
            productId = productId,
            movements = movements,
            fallbackUnitCost = 15.0,
            productName = "Central Test Product"
        )

        assertEquals(7, stock.quantityOnHand)
        assertEquals(7 * 15.0, stock.totalValuation, 0.001)
        assertEquals(6, stock.activeMovementCount)
        assertEquals(1, stock.reversedMovementCount)
        assertTrue(stock.isInStock)
    }

    @Test
    fun testFinancialReportingCalculation() {
        val transactions: List<TransactionItem> = listOf(
            TransactionItem(
                id = "tx_1",
                title = "Cash Sale",
                activityType = "شراء كاش",
                amount = 100.0,
                isCredit = false,
                date = "2026-09-29",
                relativeTime = "اليوم",
                transactionType = TransactionType.SALE,
                saleType = SaleType.CASH,
                operationStatus = OperationStatus.ACTIVE,
                paidAmount = 100.0,
                creditAmount = 0.0
            ),
            TransactionItem(
                id = "tx_2",
                title = "Credit Sale",
                activityType = "شراء آجل",
                amount = 250.0,
                isCredit = true,
                date = "2026-09-29",
                relativeTime = "اليوم",
                transactionType = TransactionType.SALE,
                saleType = SaleType.CREDIT,
                operationStatus = OperationStatus.ACTIVE,
                paidAmount = 0.0,
                creditAmount = 250.0
            ),
            TransactionItem(
                id = "tx_3",
                title = "Customer Payment",
                activityType = "تسديد",
                amount = 80.0,
                isCredit = false,
                date = "2026-09-29",
                relativeTime = "اليوم",
                transactionType = TransactionType.CUSTOMER_PAYMENT,
                operationStatus = OperationStatus.ACTIVE
            ),
            TransactionItem(
                id = "tx_rev",
                title = "Reversed Sale",
                activityType = "شراء آجل",
                amount = 500.0,
                isCredit = true,
                date = "2026-09-29",
                relativeTime = "اليوم",
                transactionType = TransactionType.SALE,
                saleType = SaleType.CREDIT,
                operationStatus = OperationStatus.REVERSED,
                creditAmount = 500.0
            )
        )

        val totals = CentralAccountingEngine.calculateFinancialReport(transactions)

        assertEquals(100.0, totals.cashSales, 0.001)
        assertEquals(250.0, totals.creditSales, 0.001)
        assertEquals(350.0, totals.totalSales, 0.001)
        assertEquals(80.0, totals.customerPayments, 0.001)
        assertEquals(3, totals.activeTransactionCount)
        assertEquals(1, totals.reversedTransactionCount)
        assertEquals(500.0, totals.totalReversedVolume, 0.001)
    }
}
