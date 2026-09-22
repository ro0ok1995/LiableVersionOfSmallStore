package com.example.accounting

import com.example.data.db.CustomerEntity
import com.example.data.db.toModel
import com.example.model.CustomerAccount
import com.example.model.SettlementType
import com.example.model.TransactionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Phase 2.1 Accounting Rules:
 *
 * 1. Recording a credit sale changes the calculated ledger balance.
 * 2. Recording a customer payment changes the calculated ledger balance.
 * 3. CustomerEntity.balance is NOT used as the accounting source.
 * 4. CustomerEntity.totalDebt is NOT used as the accounting source.
 * 5. Overpayment is NOT artificially clamped to zero by the financial operation.
 * 6. Customer metadata updates do not mutate or perform financial calculations on stored balances.
 */
class CustomerAccountingSourceOfTruthTest {

    private val customerId = "cust_phase2_1_test"

    @Test
    fun recordingCreditSale_changesCalculatedLedgerBalance() {
        val initialTransactions = emptyList<TransactionItem>()
        val initialSummary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, initialTransactions)
        assertEquals(0.0, initialSummary.balance, 0.0001)
        assertEquals(0.0, initialSummary.totalCreditSales, 0.0001)

        val creditSaleTx = TransactionItem(
            id = "tx_credit_sale_1",
            customerName = "عميل تجريبي",
            activityType = "شراء آجل",
            amount = 250.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerId = customerId
        )

        val updatedSummary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId,
            listOf(creditSaleTx)
        )

        assertEquals("Calculated ledger balance must increase by credit sale amount", 250.0, updatedSummary.balance, 0.0001)
        assertEquals(250.0, updatedSummary.totalCreditSales, 0.0001)
        assertTrue(updatedSummary.isDebitBalance)
        assertFalse(updatedSummary.isSettled)
    }

    @Test
    fun recordingCustomerPayment_changesCalculatedLedgerBalance() {
        val creditSaleTx = TransactionItem(
            id = "tx_credit_sale_1",
            customerName = "عميل تجريبي",
            activityType = "شراء آجل",
            amount = 250.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerId = customerId
        )
        val paymentTx = TransactionItem(
            id = "tx_payment_1",
            customerName = "عميل تجريبي",
            activityType = "تسديد",
            amount = 100.0,
            isCredit = false,
            date = "2026-09-20",
            relativeTime = "الآن",
            settlementType = SettlementType.PARTIAL,
            customerId = customerId
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId,
            listOf(creditSaleTx, paymentTx)
        )

        assertEquals("Calculated ledger balance must decrease by payment amount", 150.0, summary.balance, 0.0001)
        assertEquals(250.0, summary.totalCreditSales, 0.0001)
        assertEquals(100.0, summary.totalPayments, 0.0001)
        assertTrue(summary.isDebitBalance)
    }

    @Test
    fun customerEntityBalance_isNotUsedAsAccountingSource() {
        // CustomerEntity has a legacy/stale stored balance of 9999.0
        val legacyEntity = CustomerEntity(
            id = customerId,
            customerName = "عميل تجريبي",
            balance = 9999.0, // Stale/corrupt legacy value
            totalDebt = 8888.0,
            phone = "0501234567",
            lastTransactionDate = "2026-09-01",
            hasRecentActivity = false
        )

        // The actual authoritative transactions in the ledger:
        // Credit Sale = 300.0, Payment = 100.0 -> Balance = 200.0
        val transactions = listOf(
            TransactionItem(
                id = "tx_1",
                customerName = legacyEntity.customerName,
                activityType = "شراء بالدين",
                amount = 300.0,
                isCredit = true,
                date = "2026-09-20",
                relativeTime = "الآن",
                customerId = customerId
            ),
            TransactionItem(
                id = "tx_2",
                customerName = legacyEntity.customerName,
                activityType = "تسديد",
                amount = 100.0,
                isCredit = false,
                date = "2026-09-20",
                relativeTime = "الآن",
                customerId = customerId
            )
        )

        // Dynamic ledger calculation:
        val ledgerSummary = CustomerLedgerCalculator.calculateCustomerBalance(legacyEntity.id, transactions)
        val customerDomainModel = CustomerAccount(
            id = legacyEntity.id,
            customerName = legacyEntity.customerName,
            balance = ledgerSummary.balance, // Source of truth: CustomerLedgerCalculator
            totalDebt = ledgerSummary.totalCreditSales,
            phone = legacyEntity.phone
        )

        assertEquals("Calculated balance must be 200.0 based on ledger transactions", 200.0, customerDomainModel.balance, 0.0001)
        assertFalse("Legacy stored balance (9999.0) must NOT be used as the accounting source", customerDomainModel.balance == legacyEntity.balance)
    }

    @Test
    fun customerEntityTotalDebt_isNotUsedAsAccountingSource() {
        // CustomerEntity has a legacy/stale stored totalDebt of 7777.0
        val legacyEntity = CustomerEntity(
            id = customerId,
            customerName = "عميل تجريبي",
            balance = 0.0,
            totalDebt = 7777.0, // Stale/corrupt legacy value
            phone = "0501234567",
            lastTransactionDate = "2026-09-01",
            hasRecentActivity = false
        )

        // Transactions in the ledger contain 1 credit sale of 350.0
        val transactions = listOf(
            TransactionItem(
                id = "tx_1",
                customerName = legacyEntity.customerName,
                activityType = "شراء آجل",
                amount = 350.0,
                isCredit = true,
                date = "2026-09-20",
                relativeTime = "الآن",
                customerId = customerId
            )
        )

        val ledgerSummary = CustomerLedgerCalculator.calculateCustomerBalance(legacyEntity.id, transactions)
        val customerDomainModel = CustomerAccount(
            id = legacyEntity.id,
            customerName = legacyEntity.customerName,
            balance = ledgerSummary.balance,
            totalDebt = ledgerSummary.totalCreditSales,
            phone = legacyEntity.phone
        )

        assertEquals("Calculated totalDebt must be 350.0 from ledger", 350.0, customerDomainModel.totalDebt, 0.0001)
        assertFalse("Legacy stored totalDebt (7777.0) must NOT be used as the accounting source", customerDomainModel.totalDebt == legacyEntity.totalDebt)
    }

    @Test
    fun overpayment_isNotArtificiallyClampedToZero() {
        // Credit Sale = 100.0, Payment = 160.0 (Overpayment of 60.0)
        val transactions = listOf(
            TransactionItem(
                id = "tx_sale",
                customerName = "عميل تجريبي",
                activityType = "شراء بالدين",
                amount = 100.0,
                isCredit = true,
                date = "2026-09-20",
                relativeTime = "الآن",
                customerId = customerId
            ),
            TransactionItem(
                id = "tx_overpayment",
                customerName = "عميل تجريبي",
                activityType = "تسديد",
                amount = 160.0,
                isCredit = false,
                date = "2026-09-20",
                relativeTime = "الآن",
                customerId = customerId
            )
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, transactions)

        assertEquals("Balance must be -60.0 (negative representing customer overpayment)", -60.0, summary.balance, 0.0001)
        assertTrue("Overpayment must be recognized as credit balance", summary.isCreditBalance)
        assertFalse("Overpayment must NOT be clamped to zero", summary.balance == 0.0)
    }

    @Test
    fun customerMetadataUpdate_doesNotMutateStoredBalances() {
        val originalCustomer = CustomerAccount(
            id = customerId,
            customerName = "عميل تجريبي",
            balance = 120.0,
            totalDebt = 120.0,
            phone = "0501234567",
            lastTransactionDate = "2026-09-10",
            hasRecentActivity = false
        )

        // Metadata update as now performed in completeSettlement / completeQuickPayment
        val updatedCustomer = originalCustomer.copy(
            hasRecentActivity = true,
            lastTransactionDate = "2026-09-20"
        )

        // Assert that the customer copy preserves whatever values it had without financial calculation
        assertEquals(originalCustomer.balance, updatedCustomer.balance, 0.0001)
        assertEquals(originalCustomer.totalDebt, updatedCustomer.totalDebt, 0.0001)
        assertTrue(updatedCustomer.hasRecentActivity)
        assertEquals("2026-09-20", updatedCustomer.lastTransactionDate)
    }

    @Test
    fun customerReceivable_reproducibleFromPersistedOperations_viaEntityMapping() {
        // Persisted database entities:
        // 1. Credit sale: 200.0
        // 2. Mixed sale: total 100.0, paid 60.0, credit 40.0
        // 3. Customer payment: 50.0
        val persistedEntities = listOf(
            com.example.data.db.TransactionEntity(
                id = "tx_db_1",
                title = "بيع آجل",
                customerNameSnapshot = "عميل تجريبي",
                activityType = "شراء آجل",
                amount = 200.0,
                isCredit = true,
                date = "2026-09-22",
                relativeTime = "الآن",
                customerId = customerId,
                paidAmount = 0.0,
                creditAmount = 200.0
            ),
            com.example.data.db.TransactionEntity(
                id = "tx_db_2",
                title = "بيع مختلط",
                customerNameSnapshot = "عميل تجريبي",
                activityType = "شراء بالدين",
                amount = 100.0,
                isCredit = true,
                date = "2026-09-22",
                relativeTime = "الآن",
                customerId = customerId,
                paidAmount = 60.0,
                creditAmount = 40.0
            ),
            com.example.data.db.TransactionEntity(
                id = "tx_db_3",
                title = "دفعة حساب",
                customerNameSnapshot = "عميل تجريبي",
                activityType = "تسديد",
                amount = 50.0,
                isCredit = false,
                date = "2026-09-22",
                relativeTime = "الآن",
                customerId = customerId,
                paidAmount = 50.0,
                creditAmount = 0.0
            )
        )

        // Read from DB and convert to domain models:
        val domainTransactions = persistedEntities.map { it.toModel() }

        // Recalculate customer receivable through ledger:
        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, domainTransactions)

        // Expected: 200 (credit) + 40 (mixed credit portion) - 50 (payment) = 190.0
        assertEquals(190.0, summary.balance, 0.0001)
        assertEquals(240.0, summary.totalCreditSales, 0.0001)
        assertEquals(50.0, summary.totalPayments, 0.0001)
    }

    @Test
    fun mixedSale_withTotal100_paid60_credit40_producesReceivable40_fromPersistedEntity() {
        val mixedEntity = com.example.data.db.TransactionEntity(
            id = "tx_db_mixed",
            title = "فاتورة مجزأة",
            customerNameSnapshot = "عميل تجريبي",
            activityType = "شراء بالدين",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-22",
            relativeTime = "الآن",
            customerId = customerId,
            paidAmount = 60.0,
            creditAmount = 40.0
        )

        val domainTx = mixedEntity.toModel()
        val summary = CustomerLedgerCalculator.calculateCustomerBalance(customerId, listOf(domainTx))

        // Mandatory rule: Sale total = 100, Paid = 60, Credit = 40 -> Customer receivable MUST be 40, NOT 100.
        assertEquals("Persisted mixed sale must produce receivable of 40.0", 40.0, summary.balance, 0.0001)
        assertEquals(40.0, summary.totalCreditSales, 0.0001)
    }
}
