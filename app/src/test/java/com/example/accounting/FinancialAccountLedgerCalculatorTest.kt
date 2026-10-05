package com.example.accounting

import com.example.data.db.Adjustment
import com.example.data.db.CustomerPayment
import com.example.data.db.Expense
import com.example.data.db.OpeningBalance
import com.example.data.db.Purchase
import com.example.data.db.Refund
import com.example.data.db.Sale
import com.example.data.db.SupplierPayment
import com.example.model.OperationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused Phase 4 test suite for Financial Account Statements and Ledgers.
 * Verifies all 10 required invariants:
 * 1. Opening balance before period + in-period customer payment.
 * 2. Supplier payment.
 * 3. Expense.
 * 4. Customer refund.
 * 5. Financial-account adjustment.
 * 6. Reversal produces no active balance effect while remaining auditable.
 * 7. Two financial accounts remain isolated.
 * 8. Chronological running balance is correct.
 * 9. Opening balance is not counted again as an in-period movement.
 * 10. Final ledger closing balance equals CentralAccountingEngine.calculateFinancialAccountBalance.
 */
class FinancialAccountLedgerCalculatorTest {

    private val accountCash = "acc_cash"
    private val accountBank = "acc_bank"

    @Test
    fun testCoreSequence_opening100_payment40_supplier20_expense10_equals110() {
        val opening = OpeningBalance(
            id = "ob_cash",
            entityType = "FINANCIAL_ACCOUNT",
            entityId = accountCash,
            amount = 100.0,
            direction = "DEBIT",
            date = "2026-10-01",
            createdAt = 1000L
        )
        val custPayment = CustomerPayment(
            id = "cp_1",
            customerId = "cust_01",
            amount = 40.0,
            paymentMethodId = "pm_cash",
            financialAccountId = accountCash,
            transactionDate = "2026-10-02",
            createdAt = 2000L,
            status = "ACTIVE"
        )
        val supPayment = SupplierPayment(
            id = "sp_1",
            supplierId = "sup_01",
            amount = 20.0,
            paymentDate = "2026-10-03",
            financialAccountId = accountCash,
            createdAt = 3000L,
            status = "ACTIVE"
        )
        val expense = Expense(
            id = "exp_1",
            categoryId = "cat_general",
            amount = 10.0,
            financialAccountId = accountCash,
            date = "2026-10-04",
            description = "مستلزمات",
            createdAt = 4000L,
            status = "ACTIVE"
        )

        val statement = CentralAccountingEngine.buildFinancialAccountStatement(
            accountId = accountCash,
            openingBalances = listOf(opening),
            customerPayments = listOf(custPayment),
            supplierPayments = listOf(supPayment),
            expenses = listOf(expense)
        )

        // 1. Opening balance
        assertEquals(100.0, statement.openingBalance, 0.001)

        // 2. In-period movements: exactly 3 movements (opening balance is NOT duplicated as a movement)
        assertEquals(3, statement.entries.size)
        assertEquals(40.0, statement.totalInflows, 0.001)
        assertEquals(30.0, statement.totalOutflows, 0.001)

        // 3. Chronological running balances:
        // After cp_1 (+40): running balance = 140.0
        assertEquals("cp_1", statement.entries[0].id)
        assertEquals(40.0, statement.entries[0].inflow, 0.001)
        assertEquals(0.0, statement.entries[0].outflow, 0.001)
        assertEquals(140.0, statement.entries[0].runningBalance, 0.001)

        // After sp_1 (-20): running balance = 120.0
        assertEquals("sp_1", statement.entries[1].id)
        assertEquals(0.0, statement.entries[1].inflow, 0.001)
        assertEquals(20.0, statement.entries[1].outflow, 0.001)
        assertEquals(120.0, statement.entries[1].runningBalance, 0.001)

        // After exp_1 (-10): running balance = 110.0
        assertEquals("exp_1", statement.entries[2].id)
        assertEquals(0.0, statement.entries[2].inflow, 0.001)
        assertEquals(10.0, statement.entries[2].outflow, 0.001)
        assertEquals(110.0, statement.entries[2].runningBalance, 0.001)

        // 4. Closing balance equals 110.0
        assertEquals(110.0, statement.closingBalance, 0.001)

        // 5. Reconciles exactly to CentralAccountingEngine.calculateFinancialAccountBalance
        val expectedBalance = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId = accountCash,
            openingBalances = listOf(opening),
            customerPayments = listOf(custPayment),
            supplierPayments = listOf(supPayment),
            expenses = listOf(expense)
        )
        assertEquals(expectedBalance, statement.closingBalance, 0.001)
    }

    @Test
    fun testOpeningBalanceBeforePeriod_andDateFiltering() {
        val opening = OpeningBalance(
            id = "ob_cash",
            entityType = "FINANCIAL_ACCOUNT",
            entityId = accountCash,
            amount = 100.0,
            direction = "DEBIT",
            date = "2026-09-01",
            createdAt = 1000L
        )
        val prePeriodPayment = CustomerPayment(
            id = "cp_pre",
            customerId = "cust_01",
            amount = 50.0,
            paymentMethodId = "pm_cash",
            financialAccountId = accountCash,
            transactionDate = "2026-09-15",
            createdAt = 2000L,
            status = "ACTIVE"
        )
        val inPeriodPayment = CustomerPayment(
            id = "cp_in",
            customerId = "cust_01",
            amount = 30.0,
            paymentMethodId = "pm_cash",
            financialAccountId = accountCash,
            transactionDate = "2026-10-05",
            createdAt = 3000L,
            status = "ACTIVE"
        )
        val postPeriodPayment = CustomerPayment(
            id = "cp_post",
            customerId = "cust_01",
            amount = 70.0,
            paymentMethodId = "pm_cash",
            financialAccountId = accountCash,
            transactionDate = "2026-10-25",
            createdAt = 4000L,
            status = "ACTIVE"
        )

        // Period: 2026-10-01 to 2026-10-15
        val statement = CentralAccountingEngine.buildFinancialAccountStatement(
            accountId = accountCash,
            startDate = "2026-10-01",
            endDate = "2026-10-15",
            openingBalances = listOf(opening),
            customerPayments = listOf(prePeriodPayment, inPeriodPayment, postPeriodPayment)
        )

        // Opening balance includes OB (100) + pre-period payment (50) = 150.0
        assertEquals(150.0, statement.openingBalance, 0.001)

        // In-period entries: only cp_in
        assertEquals(1, statement.entries.size)
        assertEquals("cp_in", statement.entries[0].id)
        assertEquals(30.0, statement.entries[0].inflow, 0.001)
        assertEquals(180.0, statement.entries[0].runningBalance, 0.001)

        assertEquals(30.0, statement.totalInflows, 0.001)
        assertEquals(0.0, statement.totalOutflows, 0.001)
        assertEquals(180.0, statement.closingBalance, 0.001)
    }

    @Test
    fun testAllOperations_refund_adjustment_purchases_sales_reversals() {
        // Cash Sale (+100.0)
        val sale = Sale(
            id = "sale_1",
            invoiceNumber = "INV-001",
            customerId = "cust_01",
            saleType = "CASH",
            totalAmount = 100.0,
            paidAmount = 100.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-10-01",
            createdAt = 1000L,
            status = "ACTIVE"
        )

        // Purchase cash outflow (-40.0)
        val purchase = Purchase(
            id = "pur_1",
            invoiceNumber = "PUR-001",
            supplierId = "sup_01",
            purchaseDate = "2026-10-02",
            totalAmount = 40.0,
            paidAmount = 40.0,
            creditAmount = 0.0,
            financialAccountId = accountCash,
            paymentStatus = "PAID",
            createdAt = 2000L,
            status = "ACTIVE"
        )

        // Customer refund (-15.0)
        val refund = Refund(
            id = "ref_1",
            saleId = "sale_1",
            customerId = "cust_01",
            amount = 15.0,
            financialAccountId = accountCash,
            refundDate = "2026-10-03",
            reason = "استرجاع",
            createdAt = 3000L,
            status = "ACTIVE"
        )

        // Financial account adjustment DEBIT (+25.0)
        val adjDebit = Adjustment(
            id = "adj_1",
            entityType = "FINANCIAL_ACCOUNT",
            entityId = accountCash,
            amount = 25.0,
            direction = "DEBIT",
            date = "2026-10-04",
            reason = "فائض نقدي",
            createdAt = 4000L,
            status = "ACTIVE"
        )

        // Financial account adjustment CREDIT (-10.0)
        val adjCredit = Adjustment(
            id = "adj_2",
            entityType = "FINANCIAL_ACCOUNT",
            entityId = accountCash,
            amount = 10.0,
            direction = "CREDIT",
            date = "2026-10-05",
            reason = "تسوية عجز",
            createdAt = 5000L,
            status = "ACTIVE"
        )

        // REVERSED Customer Payment (+50.0 should NOT contribute)
        val reversedPayment = CustomerPayment(
            id = "cp_rev",
            customerId = "cust_01",
            amount = 50.0,
            paymentMethodId = "pm_cash",
            financialAccountId = accountCash,
            transactionDate = "2026-10-06",
            createdAt = 6000L,
            status = "REVERSED"
        )

        val statement = CentralAccountingEngine.buildFinancialAccountStatement(
            accountId = accountCash,
            sales = listOf(sale),
            purchases = listOf(purchase),
            refunds = listOf(refund),
            adjustments = listOf(adjDebit, adjCredit),
            customerPayments = listOf(reversedPayment)
        )

        // Total active inflows = 100.0 (sale) + 25.0 (adjDebit) = 125.0
        // Total active outflows = 40.0 (purchase) + 15.0 (refund) + 10.0 (adjCredit) = 65.0
        // Expected Net = 125.0 - 65.0 = 60.0
        assertEquals(125.0, statement.totalInflows, 0.001)
        assertEquals(65.0, statement.totalOutflows, 0.001)
        assertEquals(60.0, statement.closingBalance, 0.001)

        // Reversed payment is listed for audit trail, but has 0.0 active inflow and does not change running balance
        val revEntry = statement.entries.first { it.id == "cp_rev" }
        assertEquals(OperationStatus.REVERSED, revEntry.operationStatus)
        assertEquals(0.0, revEntry.inflow, 0.001)
        assertEquals(60.0, revEntry.runningBalance, 0.001)

        // Exactly matches CentralAccountingEngine.calculateFinancialAccountBalance
        val engineBalance = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId = accountCash,
            sales = listOf(sale),
            purchases = listOf(purchase),
            refunds = listOf(refund),
            adjustments = listOf(adjDebit, adjCredit),
            customerPayments = listOf(reversedPayment)
        )
        assertEquals(engineBalance, statement.closingBalance, 0.001)
    }

    @Test
    fun testTwoAccounts_strictFinancialIsolation() {
        val cashPayment = CustomerPayment(
            id = "cp_cash",
            customerId = "cust_01",
            amount = 100.0,
            paymentMethodId = "pm_cash",
            financialAccountId = accountCash,
            transactionDate = "2026-10-01",
            createdAt = 1000L,
            status = "ACTIVE"
        )
        val bankPayment = CustomerPayment(
            id = "cp_bank",
            customerId = "cust_02",
            amount = 250.0,
            paymentMethodId = "pm_bank",
            financialAccountId = accountBank,
            transactionDate = "2026-10-01",
            createdAt = 2000L,
            status = "ACTIVE"
        )

        val cashStatement = CentralAccountingEngine.buildFinancialAccountStatement(
            accountId = accountCash,
            customerPayments = listOf(cashPayment, bankPayment)
        )
        val bankStatement = CentralAccountingEngine.buildFinancialAccountStatement(
            accountId = accountBank,
            customerPayments = listOf(cashPayment, bankPayment)
        )

        // Cash statement receives ONLY cashPayment (100.0)
        assertEquals(1, cashStatement.entries.size)
        assertEquals(100.0, cashStatement.closingBalance, 0.001)
        assertEquals("cp_cash", cashStatement.entries[0].id)

        // Bank statement receives ONLY bankPayment (250.0)
        assertEquals(1, bankStatement.entries.size)
        assertEquals(250.0, bankStatement.closingBalance, 0.001)
        assertEquals("cp_bank", bankStatement.entries[0].id)
    }

    @Test
    fun testSaleFinancialAccountStatementAttribution_allScenarios() {
        // A. Cash/default compatibility: financialAccountId = null goes to acc_cash
        val legacySale = Sale(
            id = "sale_leg",
            invoiceNumber = "INV-LEG",
            customerId = "cust_01",
            saleType = "CASH",
            totalAmount = 75.0,
            paidAmount = 75.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-10-01",
            createdAt = 1000L,
            financialAccountId = null, // fallback to acc_cash
            status = "ACTIVE"
        )

        // B. Explicit non-cash account: financialAccountId = "acc_bank", paidAmount = 100
        val bankSale = Sale(
            id = "sale_bnk",
            invoiceNumber = "INV-BNK",
            customerId = "cust_01",
            saleType = "CASH",
            totalAmount = 100.0,
            paidAmount = 100.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-10-02",
            createdAt = 2000L,
            financialAccountId = accountBank,
            status = "ACTIVE"
        )

        // C. Mixed sale: 100 = 60 paid to acc_bank + 40 credit
        val mixedBankSale = Sale(
            id = "sale_mxd_bnk",
            invoiceNumber = "INV-MXD-BNK",
            customerId = "cust_01",
            saleType = "MIXED",
            totalAmount = 100.0,
            paidAmount = 60.0,
            creditAmount = 40.0,
            paymentStatus = "PARTIAL",
            transactionDate = "2026-10-03",
            createdAt = 3000L,
            financialAccountId = accountBank,
            status = "ACTIVE"
        )

        // D. Reversed sale: financialAccountId = "acc_bank", paidAmount = 50, status = REVERSED
        val reversedBankSale = Sale(
            id = "sale_rev_bnk",
            invoiceNumber = "INV-REV-BNK",
            customerId = "cust_01",
            saleType = "CASH",
            totalAmount = 50.0,
            paidAmount = 50.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-10-04",
            createdAt = 4000L,
            financialAccountId = accountBank,
            status = "REVERSED"
        )

        val allSales = listOf(legacySale, bankSale, mixedBankSale, reversedBankSale)

        // 1. Cash Statement: only legacySale should be present
        val cashStatement = CentralAccountingEngine.buildFinancialAccountStatement(
            accountId = accountCash,
            sales = allSales
        )
        assertEquals(1, cashStatement.entries.size)
        assertEquals("sale_leg", cashStatement.entries[0].id)
        assertEquals(75.0, cashStatement.totalInflows, 0.001)
        assertEquals(0.0, cashStatement.totalOutflows, 0.001)
        assertEquals(75.0, cashStatement.closingBalance, 0.001)
        assertEquals(
            CentralAccountingEngine.calculateFinancialAccountBalance(accountCash, sales = allSales),
            cashStatement.closingBalance,
            0.001
        )

        // 2. Bank Statement: bankSale, mixedBankSale, and reversedBankSale
        val bankStatement = CentralAccountingEngine.buildFinancialAccountStatement(
            accountId = accountBank,
            sales = allSales
        )
        // 3 entries: bankSale (+100), mixedBankSale (+60), reversedBankSale (0.0 active)
        assertEquals(3, bankStatement.entries.size)
        assertEquals(160.0, bankStatement.totalInflows, 0.001)
        assertEquals(0.0, bankStatement.totalOutflows, 0.001)
        assertEquals(160.0, bankStatement.closingBalance, 0.001)

        // Verify reversed sale has REVERSED status and 0 active inflow
        val revEntry = bankStatement.entries.first { it.id == "sale_rev_bnk" }
        assertEquals(OperationStatus.REVERSED, revEntry.operationStatus)
        assertEquals(0.0, revEntry.inflow, 0.001)
        assertEquals(160.0, revEntry.runningBalance, 0.001)

        // E. Reconciles exactly with CentralAccountingEngine
        assertEquals(
            CentralAccountingEngine.calculateFinancialAccountBalance(accountBank, sales = allSales),
            bankStatement.closingBalance,
            0.001
        )
    }

    @Test
    fun testCustomerPaymentWithNullFinancialAccount_defaultsToAccCash() {
        val paymentWithNullAccount = CustomerPayment(
            id = "cp_legacy_null",
            customerId = "cust_01",
            amount = 85.0,
            paymentMethodId = "pm_cash",
            financialAccountId = null, // legacy / default
            transactionDate = "2026-10-01",
            createdAt = 1000L,
            status = "ACTIVE"
        )

        // 1. Should attribute to acc_cash
        val cashBalance = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId = accountCash,
            customerPayments = listOf(paymentWithNullAccount)
        )
        assertEquals("Customer payment with null financialAccountId defaults to acc_cash", 85.0, cashBalance, 0.001)

        val cashStatement = CentralAccountingEngine.buildFinancialAccountStatement(
            accountId = accountCash,
            customerPayments = listOf(paymentWithNullAccount)
        )
        assertEquals(1, cashStatement.entries.size)
        assertEquals(85.0, cashStatement.closingBalance, 0.001)

        // 2. Should NOT attribute to acc_bank
        val bankBalance = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId = accountBank,
            customerPayments = listOf(paymentWithNullAccount)
        )
        assertEquals("Customer payment with null financialAccountId does not affect acc_bank", 0.0, bankBalance, 0.001)
    }

    @Test
    fun testCardAndWalletAccounts_independentConvergence() {
        val accountCard = "acc_card"
        val accountWallet = "acc_wallet"

        val cardSale = Sale(
            id = "sale_card_1",
            invoiceNumber = "INV-CRD-1",
            customerId = "cust_01",
            saleType = "CASH",
            totalAmount = 300.0,
            paidAmount = 300.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            financialAccountId = accountCard,
            transactionDate = "2026-10-01",
            createdAt = 1000L,
            status = "ACTIVE"
        )
        val walletPayment = CustomerPayment(
            id = "cp_wlt_1",
            customerId = "cust_02",
            amount = 150.0,
            paymentMethodId = "pm_wallet",
            financialAccountId = accountWallet,
            transactionDate = "2026-10-02",
            createdAt = 2000L,
            status = "ACTIVE"
        )
        val cardExpense = Expense(
            id = "exp_crd_1",
            categoryId = "cat_01",
            amount = 50.0,
            financialAccountId = accountCard,
            date = "2026-10-03",
            description = "رسوم بطاقة",
            createdAt = 3000L,
            status = "ACTIVE"
        )

        val allSales = listOf(cardSale)
        val allPayments = listOf(walletPayment)
        val allExpenses = listOf(cardExpense)

        // Card account: 300 (sale) - 50 (expense) = 250
        val cardBalance = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId = accountCard,
            sales = allSales,
            customerPayments = allPayments,
            expenses = allExpenses
        )
        assertEquals(250.0, cardBalance, 0.001)

        val cardStatement = CentralAccountingEngine.buildFinancialAccountStatement(
            accountId = accountCard,
            sales = allSales,
            customerPayments = allPayments,
            expenses = allExpenses
        )
        assertEquals(2, cardStatement.entries.size)
        assertEquals(250.0, cardStatement.closingBalance, 0.001)

        // Wallet account: 150 (payment)
        val walletBalance = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId = accountWallet,
            sales = allSales,
            customerPayments = allPayments,
            expenses = allExpenses
        )
        assertEquals(150.0, walletBalance, 0.001)

        val walletStatement = CentralAccountingEngine.buildFinancialAccountStatement(
            accountId = accountWallet,
            sales = allSales,
            customerPayments = allPayments,
            expenses = allExpenses
        )
        assertEquals(1, walletStatement.entries.size)
        assertEquals(150.0, walletStatement.closingBalance, 0.001)

        // Cash account untouched
        val cashBalance = CentralAccountingEngine.calculateFinancialAccountBalance(
            accountId = accountCash,
            sales = allSales,
            customerPayments = allPayments,
            expenses = allExpenses
        )
        assertEquals(0.0, cashBalance, 0.001)
    }

    @Test
    fun testReversalAndDuplicateReversal_idempotency() {
        val sale = Sale(
            id = "sale_idempotent",
            invoiceNumber = "INV-IDEM",
            customerId = "cust_01",
            saleType = "CASH",
            totalAmount = 200.0,
            paidAmount = 200.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            financialAccountId = accountCash,
            transactionDate = "2026-10-01",
            createdAt = 1000L,
            status = "ACTIVE"
        )

        // Active sale produces 200 balance
        assertEquals(200.0, CentralAccountingEngine.calculateFinancialAccountBalance(accountCash, sales = listOf(sale)), 0.001)

        // Reversed once: balance is 0.0
        val reversedSaleOnce = sale.copy(status = "REVERSED")
        assertEquals(0.0, CentralAccountingEngine.calculateFinancialAccountBalance(accountCash, sales = listOf(reversedSaleOnce)), 0.001)

        // Duplicate reversal: balance remains strictly 0.0 (no negative double-count)
        val reversedSaleTwice = reversedSaleOnce.copy(status = "REVERSED")
        assertEquals(0.0, CentralAccountingEngine.calculateFinancialAccountBalance(accountCash, sales = listOf(reversedSaleTwice)), 0.001)

        val statement = CentralAccountingEngine.buildFinancialAccountStatement(accountCash, sales = listOf(reversedSaleTwice))
        assertEquals(1, statement.entries.size)
        assertEquals(0.0, statement.totalInflows, 0.001)
        assertEquals(0.0, statement.closingBalance, 0.001)
        assertEquals(OperationStatus.REVERSED, statement.entries[0].operationStatus)
        assertEquals(0.0, statement.entries[0].inflow, 0.001)
    }

    @Test
    fun testCreditSaleAndSupplierReturn_doNotMoveFinancialAccountBalance() {
        // Pure credit sale: total 500, paid 0, credit 500
        val creditSale = Sale(
            id = "sale_pure_credit",
            invoiceNumber = "INV-CR-99",
            customerId = "cust_01",
            saleType = "CREDIT",
            totalAmount = 500.0,
            paidAmount = 0.0,
            creditAmount = 500.0,
            paymentStatus = "UNPAID",
            financialAccountId = accountCash,
            transactionDate = "2026-10-01",
            createdAt = 1000L,
            status = "ACTIVE"
        )

        // Financial account balance remains strictly 0.0
        val balance = CentralAccountingEngine.calculateFinancialAccountBalance(accountCash, sales = listOf(creditSale))
        assertEquals("Credit sale with paidAmount 0 creates NO cash movement", 0.0, balance, 0.001)

        val statement = CentralAccountingEngine.buildFinancialAccountStatement(accountCash, sales = listOf(creditSale))
        assertEquals("No entry in financial account statement for 0 paid credit sale", 0, statement.entries.size)
        assertEquals(0.0, statement.closingBalance, 0.001)
    }
}
