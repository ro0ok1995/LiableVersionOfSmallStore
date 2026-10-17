package com.example.accounting

import com.example.data.db.Adjustment
import com.example.data.db.CustomerPayment
import com.example.data.db.Expense
import com.example.data.db.OpeningBalance
import com.example.data.db.Purchase
import com.example.data.db.PurchaseReturn
import com.example.data.db.Refund
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.SaleReturn
import com.example.data.db.SaleReturnLine
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

    @Test
    fun testFinancialAccountBalanceCalculation() {
        val accountId = "acc_cash"

        // Inflows
        val sales = listOf(
            Sale(
                id = "sale_cash_1",
                invoiceNumber = "INV-001",
                customerId = null,
                saleType = "CASH",
                totalAmount = 100.0,
                paidAmount = 100.0,
                creditAmount = 0.0,
                paymentStatus = "PAID",
                transactionDate = "2026-10-01",
                status = "ACTIVE"
            ),
            Sale(
                id = "sale_mixed_1",
                invoiceNumber = "INV-002",
                customerId = "cust_1",
                saleType = "MIXED",
                totalAmount = 200.0,
                paidAmount = 80.0,
                creditAmount = 120.0,
                paymentStatus = "PARTIAL",
                transactionDate = "2026-10-01",
                status = "ACTIVE"
            ),
            Sale(
                id = "sale_rev_1",
                invoiceNumber = "INV-003",
                customerId = null,
                saleType = "CASH",
                totalAmount = 50.0,
                paidAmount = 50.0,
                creditAmount = 0.0,
                paymentStatus = "PAID",
                transactionDate = "2026-10-01",
                status = "REVERSED" // Must be excluded
            )
        )

        val customerPayments = listOf(
            CustomerPayment(
                id = "pay_1",
                customerId = "cust_1",
                amount = 40.0,
                paymentMethodId = "pm_cash",
                financialAccountId = accountId,
                transactionDate = "2026-10-01",
                status = "ACTIVE"
            ),
            CustomerPayment(
                id = "pay_rev",
                customerId = "cust_1",
                amount = 30.0,
                paymentMethodId = "pm_cash",
                financialAccountId = accountId,
                transactionDate = "2026-10-01",
                status = "REVERSED" // Must be excluded
            ),
            CustomerPayment(
                id = "pay_other_acc",
                customerId = "cust_1",
                amount = 99.0,
                paymentMethodId = "pm_bank",
                financialAccountId = "acc_bank", // Other account, must be excluded
                transactionDate = "2026-10-01",
                status = "ACTIVE"
            )
        )

        val openingBalances = listOf(
            OpeningBalance(
                id = "ob_1",
                entityType = "FINANCIAL_ACCOUNT",
                entityId = accountId,
                amount = 500.0,
                direction = "DEBIT", // +500
                date = "2026-10-01"
            ),
            OpeningBalance(
                id = "ob_other",
                entityType = "CUSTOMER",
                entityId = "cust_1",
                amount = 200.0,
                direction = "DEBIT", // Other entity, must be excluded
                date = "2026-10-01"
            )
        )

        val adjustments = listOf(
            Adjustment(
                id = "adj_1",
                entityType = "FINANCIAL_ACCOUNT",
                entityId = accountId,
                amount = 25.0,
                direction = "DEBIT", // +25
                date = "2026-10-01",
                reason = "Correction",
                status = "ACTIVE"
            ),
            Adjustment(
                id = "adj_credit",
                entityType = "FINANCIAL_ACCOUNT",
                entityId = accountId,
                amount = 10.0,
                direction = "CREDIT", // -10
                date = "2026-10-01",
                reason = "Correction",
                status = "ACTIVE"
            ),
            Adjustment(
                id = "adj_rev",
                entityType = "FINANCIAL_ACCOUNT",
                entityId = accountId,
                amount = 100.0,
                direction = "DEBIT",
                date = "2026-10-01",
                reason = "Correction",
                status = "REVERSED" // Must be excluded
            )
        )

        // Outflows
        val refunds = listOf(
            Refund(
                id = "ref_1",
                saleReturnId = "sr_1",
                saleId = "s_1",
                customerId = "cust_1",
                amount = 15.0,
                financialAccountId = accountId,
                refundDate = "2026-10-01",
                reason = "Return refund",
                status = "ACTIVE" // -15
            ),
            Refund(
                id = "ref_rev",
                saleReturnId = "sr_2",
                saleId = "s_2",
                customerId = "cust_1",
                amount = 10.0,
                financialAccountId = accountId,
                refundDate = "2026-10-01",
                reason = "Return refund",
                status = "REVERSED" // Must be excluded
            )
        )

        val purchases = listOf(
            Purchase(
                id = "pur_1",
                invoiceNumber = "PUR-001",
                supplierId = "sup_1",
                purchaseDate = "2026-10-01",
                totalAmount = 300.0,
                paidAmount = 70.0, // -70
                creditAmount = 230.0,
                paymentStatus = "PARTIAL",
                financialAccountId = accountId,
                status = "ACTIVE"
            ),
            Purchase(
                id = "pur_rev",
                invoiceNumber = "PUR-002",
                supplierId = "sup_1",
                purchaseDate = "2026-10-01",
                totalAmount = 100.0,
                paidAmount = 100.0,
                creditAmount = 0.0,
                paymentStatus = "PAID",
                financialAccountId = accountId,
                status = "REVERSED" // Must be excluded
            )
        )

        val supplierPayments = listOf(
            SupplierPayment(
                id = "spay_1",
                supplierId = "sup_1",
                amount = 50.0, // -50
                financialAccountId = accountId,
                paymentDate = "2026-10-01",
                status = "ACTIVE"
            ),
            SupplierPayment(
                id = "spay_rev",
                supplierId = "sup_1",
                amount = 20.0,
                financialAccountId = accountId,
                paymentDate = "2026-10-01",
                status = "REVERSED" // Must be excluded
            )
        )

        val expenses = listOf(
            Expense(
                id = "exp_1",
                categoryId = "cat_rent",
                amount = 60.0, // -60
                financialAccountId = accountId,
                date = "2026-10-01",
                description = "Rent",
                status = "ACTIVE"
            ),
            Expense(
                id = "exp_rev",
                categoryId = "cat_rent",
                amount = 40.0,
                financialAccountId = accountId,
                date = "2026-10-01",
                description = "Rent",
                status = "REVERSED" // Must be excluded
            )
        )

        val balance = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId = accountId,
            sales = sales,
            customerPayments = customerPayments,
            openingBalances = openingBalances,
            adjustments = adjustments,
            refunds = refunds,
            purchases = purchases,
            supplierPayments = supplierPayments,
            expenses = expenses
        )

        // Inflows:
        // activeSalesInflow = 100.0 (cash) + 80.0 (mixed paid) = 180.0
        // activeCustomerPaymentsInflow = 40.0
        // openingBalancesInflow = 500.0
        // activeAdjustmentsInflow = +25.0 - 10.0 = +15.0
        // Total Inflows = 180 + 40 + 500 + 15 = 735.0

        // Outflows:
        // activeRefundsOutflow = 15.0
        // activePurchasesOutflow = 70.0
        // activeSupplierPaymentsOutflow = 50.0
        // activeExpensesOutflow = 60.0
        // Total Outflows = 15 + 70 + 50 + 60 = 195.0

        // Expected Net Balance = 735.0 - 195.0 = 540.0
        assertEquals(540.0, balance, 0.001)

        // Verify non-cash account isolation: sales only attribute cash to acc_cash
        val nonCashBalance = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId = "acc_bank",
            sales = sales,
            customerPayments = customerPayments,
            openingBalances = emptyList(),
            adjustments = emptyList(),
            refunds = emptyList(),
            purchases = emptyList(),
            supplierPayments = emptyList(),
            expenses = emptyList()
        )
        assertEquals(99.0, nonCashBalance, 0.001)
    }

    @Test
    fun testSignConventionAndReversalInvariants() {
        val customerId = "cust_invariants"
        val supplierId = "sup_invariants"
        val accountId = "acc_cash"

        // 1. Mixed sale: 100 = 60 paid + 40 credit
        val mixedSale = Sale(
            id = "sale_mixed_100",
            invoiceNumber = "INV-MIXED-100",
            customerId = customerId,
            saleType = "MIXED",
            totalAmount = 100.0,
            paidAmount = 60.0,
            creditAmount = 40.0,
            paymentStatus = "PARTIAL",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )

        // Customer receivable must be 40.0, NOT 100.0
        val custSummaryMixed = CentralAccountingEngine.calculateCustomerBalance(
            customerId = customerId,
            sales = listOf(mixedSale)
        )
        assertEquals(40.0, custSummaryMixed.balance, 0.001)
        assertEquals(40.0, custSummaryMixed.totalCreditSales, 0.001)

        // Financial account must receive only 60.0, NOT 100.0
        val cashBalanceMixed = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId = accountId,
            sales = listOf(mixedSale)
        )
        assertEquals(60.0, cashBalanceMixed, 0.001)

        // 2. Pure Cash Sale: 50.0
        val cashSale = Sale(
            id = "sale_cash_50",
            invoiceNumber = "INV-CASH-50",
            customerId = customerId,
            saleType = "CASH",
            totalAmount = 50.0,
            paidAmount = 50.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )
        val custSummaryCash = CentralAccountingEngine.calculateCustomerBalance(
            customerId = customerId,
            sales = listOf(cashSale)
        )
        assertEquals(0.0, custSummaryCash.balance, 0.001) // 0 debt

        // 3. Customer payment: 40.0
        val custPayment = CustomerPayment(
            id = "pay_40",
            customerId = customerId,
            amount = 40.0,
            paymentMethodId = "pm_cash",
            financialAccountId = accountId,
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )
        val custSettled = CentralAccountingEngine.calculateCustomerBalance(
            customerId = customerId,
            sales = listOf(mixedSale),
            payments = listOf(custPayment)
        )
        assertEquals(0.0, custSettled.balance, 0.001) // 40 credit - 40 payment = 0
        assertTrue(custSettled.isSettled)

        // 4. Supplier Purchase (100 total, 30 paid, 70 credit) & Supplier Payment (70)
        val purchase = Purchase(
            id = "pur_100",
            invoiceNumber = "PUR-100",
            supplierId = supplierId,
            purchaseDate = "2026-10-01",
            totalAmount = 100.0,
            paidAmount = 30.0,
            creditAmount = 70.0,
            paymentStatus = "PARTIAL",
            financialAccountId = accountId,
            status = "ACTIVE"
        )
        val supSummaryInitial = CentralAccountingEngine.calculateSupplierBalance(
            supplierId = supplierId,
            purchases = listOf(purchase)
        )
        assertEquals(70.0, supSummaryInitial.balance, 0.001) // 70 payable

        val supPayment = SupplierPayment(
            id = "spay_70",
            supplierId = supplierId,
            amount = 70.0,
            financialAccountId = accountId,
            paymentDate = "2026-10-01",
            status = "ACTIVE"
        )
        val supSummarySettled = CentralAccountingEngine.calculateSupplierBalance(
            supplierId = supplierId,
            purchases = listOf(purchase),
            payments = listOf(supPayment)
        )
        assertEquals(0.0, supSummarySettled.balance, 0.001) // 70 - 70 = 0

        // 5. Opening Balances
        val custOb = OpeningBalance(
            id = "ob_cust",
            entityType = "CUSTOMER",
            entityId = customerId,
            amount = 150.0,
            direction = "DEBIT",
            date = "2026-10-01"
        )
        val custObSummary = CentralAccountingEngine.calculateCustomerBalance(
            customerId = customerId,
            sales = emptyList(),
            openingBalances = listOf(custOb)
        )
        assertEquals(150.0, custObSummary.balance, 0.001)

        val supOb = OpeningBalance(
            id = "ob_sup",
            entityType = "SUPPLIER",
            entityId = supplierId,
            amount = 200.0,
            direction = "CREDIT",
            date = "2026-10-01"
        )
        val supObSummary = CentralAccountingEngine.calculateSupplierBalance(
            supplierId = supplierId,
            purchases = emptyList(),
            openingBalances = listOf(supOb)
        )
        assertEquals(200.0, supObSummary.balance, 0.001)

        val accOb = OpeningBalance(
            id = "ob_acc",
            entityType = "FINANCIAL_ACCOUNT",
            entityId = accountId,
            amount = 300.0,
            direction = "DEBIT",
            date = "2026-10-01"
        )
        val accObBalance = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId = accountId,
            openingBalances = listOf(accOb)
        )
        assertEquals(300.0, accObBalance, 0.001)

        // 6. Reversal: REVERSED operations have 0.0 active accounting effect
        val reversedMixedSale = mixedSale.copy(id = "sale_mixed_rev", status = "REVERSED")
        val custReversedSummary = CentralAccountingEngine.calculateCustomerBalance(
            customerId = customerId,
            sales = listOf(reversedMixedSale)
        )
        assertEquals(0.0, custReversedSummary.balance, 0.001)

        val cashReversedBalance = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId = accountId,
            sales = listOf(reversedMixedSale)
        )
        assertEquals(0.0, cashReversedBalance, 0.001)

        // 7. Duplicate reversal attempt / Idempotency
        // Evaluating already-reversed operations multiple times causes zero additional changes
        val eval1 = CentralAccountingEngine.calculateCustomerBalance(
            customerId = customerId,
            sales = listOf(reversedMixedSale)
        )
        val eval2 = CentralAccountingEngine.calculateCustomerBalance(
            customerId = customerId,
            sales = listOf(reversedMixedSale, reversedMixedSale)
        )
        assertEquals(eval1.balance, eval2.balance, 0.001)
        assertEquals(0.0, eval2.balance, 0.001)
    }

    @Test
    fun testPhase2AtomicOperationInvariants() {
        val customerId = "cust_p2"
        val supplierId = "sup_p2"
        val accountId = "acc_cash"

        // CASE A: CASH SALE 100
        // Sale 100 -> revenue/sales effect 100, cash/financial-account effect +100, receivable 0
        val cashSale = Sale(
            id = "sale_cash_100",
            invoiceNumber = "INV-C100",
            customerId = customerId,
            saleType = "CASH",
            totalAmount = 100.0,
            paidAmount = 100.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )
        val cashSaleCustSummary = CentralAccountingEngine.calculateCustomerBalance(customerId, listOf(cashSale))
        assertEquals("Case A: Cash sale creates 0 receivable", 0.0, cashSaleCustSummary.balance, 0.001)
        val cashSaleAccBalance = CentralAccountingEngine.calculateFinancialAccountBalance(accountId, sales = listOf(cashSale))
        assertEquals("Case A: Cash sale creates +100 financial effect", 100.0, cashSaleAccBalance, 0.001)
        val cashSaleReport = CentralAccountingEngine.calculateFinancialReportFromSales(listOf(cashSale))
        assertEquals("Case A: Revenue is 100", 100.0, cashSaleReport.totalSales, 0.001)

        // CASE B: CREDIT SALE 100
        // Sale 100 -> revenue/sales effect 100, cash effect 0, receivable +100
        val creditSale = Sale(
            id = "sale_credit_100",
            invoiceNumber = "INV-CR100",
            customerId = customerId,
            saleType = "CREDIT",
            totalAmount = 100.0,
            paidAmount = 0.0,
            creditAmount = 100.0,
            paymentStatus = "UNPAID",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )
        val creditSaleCustSummary = CentralAccountingEngine.calculateCustomerBalance(customerId, listOf(creditSale))
        assertEquals("Case B: Credit sale creates +100 receivable", 100.0, creditSaleCustSummary.balance, 0.001)
        val creditSaleAccBalance = CentralAccountingEngine.calculateFinancialAccountBalance(accountId, sales = listOf(creditSale))
        assertEquals("Case B: Credit sale creates 0 financial effect", 0.0, creditSaleAccBalance, 0.001)
        val creditSaleReport = CentralAccountingEngine.calculateFinancialReportFromSales(listOf(creditSale))
        assertEquals("Case B: Revenue is 100", 100.0, creditSaleReport.totalSales, 0.001)

        // CASE C: MIXED SALE 100 = 60 paid + 40 credit
        // revenue 100, financial account +60, receivable +40 (never full 100)
        val mixedSale = Sale(
            id = "sale_mixed_p2",
            invoiceNumber = "INV-M100",
            customerId = customerId,
            saleType = "MIXED",
            totalAmount = 100.0,
            paidAmount = 60.0,
            creditAmount = 40.0,
            paymentStatus = "PARTIAL",
            transactionDate = "2026-10-01",
            status = "ACTIVE"
        )
        assertEquals("Case C: Invariant totalAmount = paid + credit", 100.0, mixedSale.paidAmount + mixedSale.creditAmount, 0.001)
        val mixedCustSummary = CentralAccountingEngine.calculateCustomerBalance(customerId, listOf(mixedSale))
        assertEquals("Case C: Customer receivable = +40 (never full 100)", 40.0, mixedCustSummary.balance, 0.001)
        val mixedAccBalance = CentralAccountingEngine.calculateFinancialAccountBalance(accountId, sales = listOf(mixedSale))
        assertEquals("Case C: Financial effect = +60 (no full 100 inflow)", 60.0, mixedAccBalance, 0.001)
        val mixedReport = CentralAccountingEngine.calculateFinancialReportFromSales(listOf(mixedSale))
        assertEquals("Case C: Revenue is 100", 100.0, mixedReport.totalSales, 0.001)

        // CASE D: PARTIAL CUSTOMER PAYMENT
        // Reduces receivable, increases financial account, does not alter revenue or historical credit sales
        val partialPayment = CustomerPayment(
            id = "pay_p2_partial_40",
            customerId = customerId,
            amount = 40.0,
            paymentMethodId = "pm_cash",
            financialAccountId = accountId,
            transactionDate = "2026-10-02",
            status = "ACTIVE"
        )
        val custAfterPartialPayment = CentralAccountingEngine.calculateCustomerBalance(
            customerId,
            sales = listOf(creditSale), // 100 credit sale
            payments = listOf(partialPayment) // 40 partial payment
        )
        assertEquals("Case D: Partial payment reduces receivable from 100 to 60", 60.0, custAfterPartialPayment.balance, 0.001)
        assertEquals("Case D: Total credit sales remains 100 (unaltered)", 100.0, custAfterPartialPayment.totalCreditSales, 0.001)
        assertEquals("Case D: Total payments recorded = 40", 40.0, custAfterPartialPayment.totalPayments, 0.001)
        val accAfterPartialPayment = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId,
            sales = listOf(creditSale),
            customerPayments = listOf(partialPayment)
        )
        assertEquals("Case D: Financial account increases by partial payment amount (+40)", 40.0, accAfterPartialPayment, 0.001)
        val reportAfterPartial = CentralAccountingEngine.calculateFinancialReportFromSales(listOf(creditSale))
        assertEquals("Case D: Customer payment does not create new revenue", 100.0, reportAfterPartial.totalSales, 0.001)

        // CASE E: FULL CUSTOMER PAYMENT
        // Subsequent payment of remaining balance (60) brings receivable to 0
        val remainingPayment = CustomerPayment(
            id = "pay_p2_remaining_60",
            customerId = customerId,
            amount = 60.0,
            paymentMethodId = "pm_cash",
            financialAccountId = accountId,
            transactionDate = "2026-10-03",
            status = "ACTIVE"
        )
        val custAfterFullPayment = CentralAccountingEngine.calculateCustomerBalance(
            customerId,
            sales = listOf(creditSale),
            payments = listOf(partialPayment, remainingPayment)
        )
        assertEquals("Case E: Full payment clears receivable to 0", 0.0, custAfterFullPayment.balance, 0.001)
        assertEquals("Case E: Total credit sales still unchanged at 100", 100.0, custAfterFullPayment.totalCreditSales, 0.001)
        val accAfterFullPayment = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId,
            sales = listOf(creditSale),
            customerPayments = listOf(partialPayment, remainingPayment)
        )
        assertEquals("Case E: Financial account has full 100 (40 + 60)", 100.0, accAfterFullPayment, 0.001)

        // CASE F: SALE RETURN / REFUND
        // Reverses only the correct financial effects without deleting/rewriting the original sale
        val saleLine = SaleLine(
            id = "sl_p2",
            saleId = mixedSale.id,
            productId = "prod_1",
            productNameSnapshot = "Widget",
            quantity = 2,
            unitPrice = 50.0,
            costPriceAtSale = 30.0,
            subtotal = 100.0
        )
        val saleReturn = SaleReturn(
            id = "sr_p2",
            saleId = mixedSale.id,
            customerId = customerId,
            returnDate = "2026-10-02",
            reason = "Return",
            amount = 50.0,
            status = "ACTIVE"
        )
        val saleReturnLine = SaleReturnLine(
            id = "srl_p2",
            saleReturnId = saleReturn.id,
            saleLineId = saleLine.id,
            productId = "prod_1",
            productNameSnapshot = "Widget",
            quantity = 1,
            unitPrice = 50.0,
            costPriceAtReturn = 30.0,
            subtotal = 50.0
        )
        val refund = Refund(
            id = "ref_p2",
            saleReturnId = saleReturn.id,
            saleId = mixedSale.id,
            customerId = customerId,
            amount = 50.0,
            financialAccountId = accountId,
            refundDate = "2026-10-02",
            reason = "Return refund",
            status = "ACTIVE"
        )
        val reportWithReturn = CentralAccountingEngine.calculateFinancialReportFromSales(
            sales = listOf(mixedSale),
            saleLines = listOf(saleLine),
            saleReturns = listOf(saleReturn),
            saleReturnLines = listOf(saleReturnLine)
        )
        assertEquals("Case F: Historical cost of returned item is reversed: 60 - 30 = 30", 30.0, reportWithReturn.cogs, 0.001)
        assertEquals("Case F: Net sales reduced by return amount: 100 - 50 = 50", 50.0, reportWithReturn.netSales, 0.001)
        val accWithRefund = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId,
            sales = listOf(mixedSale),
            refunds = listOf(refund)
        )
        assertEquals("Case F: Financial account reflects refund outflow: 60 - 50 = 10", 10.0, accWithRefund, 0.001)

        // CASE G: REVERSAL OF A MAJOR OPERATION
        // Neutralizes active accounting effect while preserving original record
        val purchase = Purchase(
            id = "pur_p2",
            invoiceNumber = "PUR-P2",
            supplierId = supplierId,
            purchaseDate = "2026-10-01",
            totalAmount = 200.0,
            paidAmount = 50.0,
            creditAmount = 150.0,
            paymentStatus = "PARTIAL",
            financialAccountId = accountId,
            status = "ACTIVE"
        )
        val reversedPurchase = purchase.copy(id = "pur_p2_rev", status = "REVERSED")
        val supReversed = CentralAccountingEngine.calculateSupplierBalance(supplierId, purchases = listOf(reversedPurchase))
        assertEquals("Case G: Reversed purchase has 0 active payable effect", 0.0, supReversed.balance, 0.001)
        val accReversedPurchase = CentralAccountingEngine.calculateFinancialAccountBalance(accountId, purchases = listOf(reversedPurchase))
        assertEquals("Case G: Reversed purchase has 0 financial outflow", 0.0, accReversedPurchase, 0.001)

        // CASE H: ATTEMPTED DUPLICATE REVERSAL
        // Neutralized, second reversal produces 0 additional effect
        val supReversedTwice = CentralAccountingEngine.calculateSupplierBalance(
            supplierId,
            purchases = listOf(reversedPurchase, reversedPurchase)
        )
        assertEquals("Case H: Second reversal attempt produces no additional effect", 0.0, supReversedTwice.balance, 0.001)

        // CASE I: OPENING BALANCE FOLLOWED BY NORMAL ACTIVITY
        // Represented exactly once, not double counted by derived balances
        val ob = OpeningBalance(
            id = "ob_p2",
            entityType = "CUSTOMER",
            entityId = customerId,
            amount = 500.0,
            direction = "DEBIT",
            date = "2026-10-01"
        )
        val custWithObAndActivity = CentralAccountingEngine.calculateCustomerBalance(
            customerId,
            sales = listOf(creditSale), // +100
            payments = listOf(partialPayment), // -40
            openingBalances = listOf(ob) // +500
        )
        assertEquals("Case I: Opening balance 500 + sale 100 - payment 40 = 560", 560.0, custWithObAndActivity.balance, 0.001)
        assertEquals("Case I: Opening balance included exactly once", 500.0, custWithObAndActivity.openingBalance, 0.001)

        // CASE J: ADJUSTMENT FOLLOWED BY NORMAL ACTIVITY
        // Represented exactly once, not double counted
        val adjDebit = Adjustment(
            id = "adj_p2_debit",
            entityType = "CUSTOMER",
            entityId = customerId,
            amount = 50.0,
            direction = "DEBIT",
            date = "2026-10-02",
            reason = "Auditing correction",
            status = "ACTIVE"
        )
        val custWithAdjAndActivity = CentralAccountingEngine.calculateCustomerBalance(
            customerId,
            sales = listOf(creditSale), // +100
            payments = listOf(partialPayment), // -40
            adjustments = listOf(adjDebit) // +50
        )
        assertEquals("Case J: Sale 100 - payment 40 + adjustment 50 = 110", 110.0, custWithAdjAndActivity.balance, 0.001)

        // CASE K: SUPPLIER PURCHASE + PARTIAL SUPPLIER PAYMENT
        // Purchase produces payable and initial outflow; supplier payment produces second outflow and reduces payable
        val supInitial = CentralAccountingEngine.calculateSupplierBalance(supplierId, purchases = listOf(purchase))
        assertEquals("Case K: Supplier payable from credit portion = 150", 150.0, supInitial.balance, 0.001)

        val supPayment = SupplierPayment(
            id = "spay_p2",
            supplierId = supplierId,
            amount = 50.0,
            financialAccountId = accountId,
            paymentDate = "2026-10-02",
            status = "ACTIVE"
        )
        val supAfterPayment = CentralAccountingEngine.calculateSupplierBalance(
            supplierId,
            purchases = listOf(purchase),
            payments = listOf(supPayment)
        )
        assertEquals("Case K: Supplier payable reduced by 50 to 100", 100.0, supAfterPayment.balance, 0.001)

        val accAfterSupPayment = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId,
            purchases = listOf(purchase),
            supplierPayments = listOf(supPayment)
        )
        assertEquals("Case K: Financial outflow = -50 (purchase paid) - 50 (payment) = -100", -100.0, accAfterSupPayment, 0.001)

        // CASE L: SUPPLIER RETURN WHERE EXISTING IMPLEMENTATION SUPPORTS IT
        val purchaseReturn = PurchaseReturn(
            id = "pret_p2",
            purchaseId = purchase.id,
            supplierId = supplierId,
            returnDate = "2026-10-02",
            amount = 30.0,
            reason = "Damaged stock",
            status = "ACTIVE"
        )
        val supAfterReturn = CentralAccountingEngine.calculateSupplierBalance(
            supplierId,
            purchases = listOf(purchase),
            payments = listOf(supPayment),
            returns = listOf(purchaseReturn)
        )
        assertEquals("Case L: Supplier payable reduced by return: 100 - 30 = 70", 70.0, supAfterReturn.balance, 0.001)

        // CASE M: EXPENSE AFFECTING SELECTED FINANCIAL ACCOUNT WITHOUT BECOMING REVENUE
        val expense = Expense(
            id = "exp_p2",
            categoryId = "cat_util",
            amount = 25.0,
            financialAccountId = accountId,
            date = "2026-10-01",
            description = "Electricity",
            status = "ACTIVE"
        )
        val accExpense = CentralAccountingEngine.calculateFinancialAccountBalance(accountId, expenses = listOf(expense))
        assertEquals("Case M: Financial account outflow = -25", -25.0, accExpense, 0.001)
        val operatingExp = CentralAccountingEngine.calculateOperatingExpenses(listOf(expense))
        assertEquals("Case M: Operating expense = 25", 25.0, operatingExp, 0.001)
        val netProfit = CentralAccountingEngine.calculateNetProfit(100.0, operatingExp)
        assertEquals("Case M: Net profit reduced once: 100 - 25 = 75", 75.0, netProfit, 0.001)
        val reportWithExpense = CentralAccountingEngine.calculateFinancialReportFromSales(
            sales = listOf(cashSale),
            expenses = listOf(expense)
        )
        assertEquals("Case M: Expense does not create or alter revenue (remains 100)", 100.0, reportWithExpense.totalSales, 0.001)
    }

    @Test
    fun testSaleFinancialAccountAttribution_allScenarios() {
        val accCash = "acc_cash"
        val accBank = "acc_bank"

        // A. Cash/default compatibility: financialAccountId = null goes to acc_cash
        val legacyCashSale = Sale(
            id = "sale_legacy",
            invoiceNumber = "INV-LEGACY",
            customerId = "cust_01",
            saleType = "CASH",
            totalAmount = 50.0,
            paidAmount = 50.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-10-01",
            financialAccountId = null, // historical fallback
            status = "ACTIVE"
        )
        val cashBalanceLegacy = CentralAccountingEngine.calculateFinancialAccountBalance(accCash, sales = listOf(legacyCashSale))
        val bankBalanceLegacy = CentralAccountingEngine.calculateFinancialAccountBalance(accBank, sales = listOf(legacyCashSale))
        assertEquals(50.0, cashBalanceLegacy, 0.001)
        assertEquals(0.0, bankBalanceLegacy, 0.001)

        // B. Explicit non-cash account: financialAccountId = "acc_bank", paidAmount = 100
        val bankSale = Sale(
            id = "sale_bank",
            invoiceNumber = "INV-BANK",
            customerId = "cust_01",
            saleType = "CASH",
            totalAmount = 100.0,
            paidAmount = 100.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-10-01",
            financialAccountId = accBank,
            status = "ACTIVE"
        )
        val cashBalanceBankSale = CentralAccountingEngine.calculateFinancialAccountBalance(accCash, sales = listOf(bankSale))
        val bankBalanceBankSale = CentralAccountingEngine.calculateFinancialAccountBalance(accBank, sales = listOf(bankSale))
        assertEquals(0.0, cashBalanceBankSale, 0.001)
        assertEquals(100.0, bankBalanceBankSale, 0.001)

        // C. Mixed sale: 100 = 60 paid to acc_bank + 40 credit
        val mixedBankSale = Sale(
            id = "sale_mixed_bank",
            invoiceNumber = "INV-MIXED-BANK",
            customerId = "cust_01",
            saleType = "MIXED",
            totalAmount = 100.0,
            paidAmount = 60.0,
            creditAmount = 40.0,
            paymentStatus = "PARTIAL",
            transactionDate = "2026-10-01",
            financialAccountId = accBank,
            status = "ACTIVE"
        )
        val bankBalanceMixed = CentralAccountingEngine.calculateFinancialAccountBalance(accBank, sales = listOf(mixedBankSale))
        val cashBalanceMixed = CentralAccountingEngine.calculateFinancialAccountBalance(accCash, sales = listOf(mixedBankSale))
        assertEquals(60.0, bankBalanceMixed, 0.001)
        assertEquals(0.0, cashBalanceMixed, 0.001)

        // Customer receivable remains creditAmount = 40
        val custBalance = CentralAccountingEngine.calculateCustomerBalance("cust_01", sales = listOf(mixedBankSale))
        assertEquals(40.0, custBalance.balance, 0.001)

        // D. Reversed sale: financialAccountId = "acc_bank", paidAmount = 100, status = REVERSED
        val reversedBankSale = bankSale.copy(id = "sale_bank_rev", status = "REVERSED")
        val bankBalanceReversed = CentralAccountingEngine.calculateFinancialAccountBalance(accBank, sales = listOf(reversedBankSale))
        assertEquals(0.0, bankBalanceReversed, 0.001)

        // E. Cross-account isolation: bank sale does not alter acc_cash
        val combinedSales = listOf(legacyCashSale, bankSale, mixedBankSale, reversedBankSale)
        val cashTotal = CentralAccountingEngine.calculateFinancialAccountBalance(accCash, sales = combinedSales)
        val bankTotal = CentralAccountingEngine.calculateFinancialAccountBalance(accBank, sales = combinedSales)
        assertEquals(50.0, cashTotal, 0.001)
        assertEquals(160.0, bankTotal, 0.001) // 100 (bankSale) + 60 (mixedBankSale) = 160.0
    }
}
