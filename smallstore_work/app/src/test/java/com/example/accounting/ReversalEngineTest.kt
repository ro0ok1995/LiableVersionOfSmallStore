package com.example.accounting

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.Adjustment
import com.example.data.db.CustomerEntity
import com.example.data.db.CustomerPayment
import com.example.data.db.FinancialAccount
import com.example.data.db.PaymentMethod
import com.example.data.db.Reversal
import com.example.data.db.Sale
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.TransactionEntity
import com.example.data.db.toModel
import com.example.data.repository.StoreRepository
import com.example.model.OperationStatus
import com.example.model.PaymentStatus
import com.example.model.SaleType
import com.example.model.TransactionType
import com.example.model.typedOperationStatus
import com.example.model.typedTransactionType
import com.example.viewmodel.AnalysisCenterViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 7: Reversal Engine Comprehensive Test Suite.
 *
 * Verifies all 12 core requirements from Phase 7:
 * 1. Sale can be reversed if supported.
 * 2. Customer payment can be reversed if supported.
 * 3. Adjustment can be reversed if supported.
 * 4. Original transaction remains stored.
 * 5. Reversal record is created.
 * 6. Reversal produces the opposite accounting effect.
 * 7. Active balance becomes net-neutral after reversal.
 * 8. Reversing the same transaction twice is rejected.
 * 9. Reversal requires a valid reason.
 * 10. Reversal is atomic.
 * 11. Existing unrelated transactions are unchanged.
 * 12. Existing reports/ledger calculations do not count the reversed effect as active.
 * 13. Unsupported transaction types are safely rejected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ReversalEngineTest {

    private lateinit var context: Context
    private lateinit var db: SmallStoreDatabase
    private lateinit var repository: StoreRepository

    private val testCustomerId = "cust_rev_test_01"
    private val testAccountId = "acc_cash_01"
    private val testPaymentMethodId = "pm_cash_01"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StoreRepository.createForTesting(db)

        // Seed test customer
        db.customerDao().insertCustomer(
            CustomerEntity(
                id = testCustomerId,
                customerName = "أحمد المحاسبي",
                balance = 0.0,
                totalDebt = 0.0,
                phone = "0501234567",
                lastTransactionDate = "2026-09-24",
                hasRecentActivity = false
            )
        )

        // Seed financial account and payment method
        db.financialAccountDao().insertAccount(
            FinancialAccount(
                id = testAccountId,
                name = "الخزينة النقدية",
                type = "CASH"
            )
        )
        db.paymentMethodDao().insertPaymentMethod(
            PaymentMethod(
                id = testPaymentMethodId,
                name = "نقدي",
                type = "CASH"
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // -------------------------------------------------------------------------
    // 1. Sale can be reversed if supported
    // -------------------------------------------------------------------------
    @Test
    fun test1_saleCanBeReversed() = runBlocking {
        val saleId = "sale_rev_001"
        db.saleDao().insertSale(
            Sale(
                id = saleId,
                invoiceNumber = "INV-000101",
                customerId = testCustomerId,
                saleType = SaleType.CREDIT.name,
                totalAmount = 250.0,
                paidAmount = 0.0,
                creditAmount = 250.0,
                paymentStatus = PaymentStatus.UNPAID.name,
                transactionDate = "2026-09-24",
                status = "ACTIVE"
            )
        )

        // Customer ledger balance before reversal
        val salesBefore = db.saleDao().getSalesByCustomerIdSync(testCustomerId)
        val summaryBefore = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = salesBefore,
            payments = emptyList(),
            openingBalances = emptyList(),
            adjustments = emptyList()
        )
        assertEquals(250.0, summaryBefore.balance, 0.001)

        // Perform reversal
        val reversal = repository.reverseTransaction(saleId, "Duplicate sale invoice recorded")

        // Assert reversal succeeded
        assertNotNull(reversal)
        assertEquals(saleId, reversal.originalTransactionId)
        assertEquals("Duplicate sale invoice recorded", reversal.reason)

        // Check updated sale entity status
        val updatedSale = db.saleDao().getSaleById(saleId)
        assertNotNull(updatedSale)
        assertEquals("REVERSED", updatedSale!!.status)

        // Recalculate customer balance: must be 0.0 (net-neutral)
        val salesAfter = db.saleDao().getSalesByCustomerIdSync(testCustomerId)
        val summaryAfter = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = salesAfter,
            payments = emptyList(),
            openingBalances = emptyList(),
            adjustments = emptyList()
        )
        assertEquals(0.0, summaryAfter.balance, 0.001)
        assertEquals(1, summaryAfter.totalReversedCount)
    }

    // -------------------------------------------------------------------------
    // 2. Customer payment can be reversed if supported
    // -------------------------------------------------------------------------
    @Test
    fun test2_customerPaymentCanBeReversed() = runBlocking {
        val paymentId = "pay_rev_001"
        db.customerPaymentDao().insertPayment(
            CustomerPayment(
                id = paymentId,
                customerId = testCustomerId,
                amount = 150.0,
                paymentMethodId = testPaymentMethodId,
                financialAccountId = testAccountId,
                transactionDate = "2026-09-24",
                status = "ACTIVE"
            )
        )

        // Before reversal: payment is active (credit 150 -> balance -150)
        val paymentsBefore = db.customerPaymentDao().getPaymentsByCustomerIdSync(testCustomerId)
        val summaryBefore = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = emptyList(),
            payments = paymentsBefore,
            openingBalances = emptyList(),
            adjustments = emptyList()
        )
        assertEquals(-150.0, summaryBefore.balance, 0.001)

        // Perform reversal
        val reversal = repository.reverseTransaction(paymentId, "Payment bounced / check returned")
        assertNotNull(reversal)

        // Payment status is now REVERSED
        val updatedPayment = db.customerPaymentDao().getPaymentById(paymentId)
        assertNotNull(updatedPayment)
        assertEquals("REVERSED", updatedPayment!!.status)

        // Recalculate: active balance is net-neutral (0.0)
        val paymentsAfter = db.customerPaymentDao().getPaymentsByCustomerIdSync(testCustomerId)
        val summaryAfter = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = emptyList(),
            payments = paymentsAfter,
            openingBalances = emptyList(),
            adjustments = emptyList()
        )
        assertEquals(0.0, summaryAfter.balance, 0.001)
        assertEquals(1, summaryAfter.totalReversedCount)
    }

    // -------------------------------------------------------------------------
    // 3. Adjustment can be reversed if supported
    // -------------------------------------------------------------------------
    @Test
    fun test3_adjustmentCanBeReversed() = runBlocking {
        val adjId = "adj_rev_001"
        db.adjustmentDao().insertAdjustment(
            Adjustment(
                id = adjId,
                entityType = "CUSTOMER",
                entityId = testCustomerId,
                amount = 80.0,
                direction = "DEBIT",
                date = "2026-09-24",
                reason = "Late penalty fee",
                status = "ACTIVE"
            )
        )

        // Before reversal: adjustment is active debit +80
        val adjustmentsBefore = db.adjustmentDao().getAdjustmentsByEntitySync("CUSTOMER", testCustomerId)
        val summaryBefore = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = emptyList(),
            payments = emptyList(),
            openingBalances = emptyList(),
            adjustments = adjustmentsBefore
        )
        assertEquals(80.0, summaryBefore.balance, 0.001)

        // Perform reversal
        val reversal = repository.reverseTransaction(adjId, "Penalty waived by manager")
        assertNotNull(reversal)

        // Adjustment status is now REVERSED
        val updatedAdj = db.adjustmentDao().getAdjustmentById(adjId)
        assertNotNull(updatedAdj)
        assertEquals("REVERSED", updatedAdj!!.status)

        // Recalculate: net balance restored to 0.0
        val adjustmentsAfter = db.adjustmentDao().getAdjustmentsByEntitySync("CUSTOMER", testCustomerId)
        val summaryAfter = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = emptyList(),
            payments = emptyList(),
            openingBalances = emptyList(),
            adjustments = adjustmentsAfter
        )
        assertEquals(0.0, summaryAfter.balance, 0.001)
        assertEquals(1, summaryAfter.totalReversedCount)
    }

    // -------------------------------------------------------------------------
    // 4. Original transaction remains stored
    // -------------------------------------------------------------------------
    @Test
    fun test4_originalTransactionRemainsStored() = runBlocking {
        val saleId = "sale_audit_001"
        db.saleDao().insertSale(
            Sale(
                id = saleId,
                invoiceNumber = "INV-000202",
                customerId = testCustomerId,
                saleType = SaleType.CREDIT.name,
                totalAmount = 500.0,
                paidAmount = 0.0,
                creditAmount = 500.0,
                paymentStatus = PaymentStatus.UNPAID.name,
                transactionDate = "2026-09-24",
                status = "ACTIVE"
            )
        )

        repository.reverseTransaction(saleId, "Audit test reversal")

        // Rule A check: row is NEVER deleted, historical amount is UNTOUCHED
        val storedSale = db.saleDao().getSaleById(saleId)
        assertNotNull("Original sale must remain stored in database", storedSale)
        assertEquals(500.0, storedSale!!.totalAmount, 0.001)
        assertEquals(500.0, storedSale.creditAmount, 0.001)
        assertEquals("INV-000202", storedSale.invoiceNumber)
        assertEquals(testCustomerId, storedSale.customerId)
        assertEquals("REVERSED", storedSale.status)
    }

    // -------------------------------------------------------------------------
    // 5. Reversal record is created
    // -------------------------------------------------------------------------
    @Test
    fun test5_reversalRecordIsCreated() = runBlocking {
        val saleId = "sale_rev_rec_001"
        db.saleDao().insertSale(
            Sale(
                id = saleId,
                invoiceNumber = "INV-000303",
                customerId = testCustomerId,
                saleType = SaleType.CREDIT.name,
                totalAmount = 120.0,
                paidAmount = 0.0,
                creditAmount = 120.0,
                paymentStatus = PaymentStatus.UNPAID.name,
                transactionDate = "2026-09-24",
                status = "ACTIVE"
            )
        )

        val reversal = repository.reverseTransaction(saleId, "Customer returned all goods")
        assertNotNull(reversal.id)
        assertTrue(reversal.id.startsWith("rev_"))
        assertEquals(saleId, reversal.originalTransactionId)
        assertEquals("Customer returned all goods", reversal.reason)
        assertTrue("reversedAt must not be blank", reversal.reversedAt.isNotBlank())
        assertEquals("ACTIVE", reversal.status)

        // Verify retrieval from DAO
        val queried = repository.getReversalForTransaction(saleId)
        assertNotNull(queried)
        assertEquals(reversal.id, queried!!.id)
        assertEquals("Customer returned all goods", queried.reason)
    }

    // -------------------------------------------------------------------------
    // 6. Reversal produces opposite accounting effect
    // -------------------------------------------------------------------------
    @Test
    fun test6_reversalProducesOppositeAccountingEffect() = runBlocking {
        val saleId = "sale_effect_001"
        val sale = Sale(
            id = saleId,
            invoiceNumber = "INV-000404",
            customerId = testCustomerId,
            saleType = SaleType.CREDIT.name,
            totalAmount = 300.0,
            paidAmount = 0.0,
            creditAmount = 300.0,
            paymentStatus = PaymentStatus.UNPAID.name,
            transactionDate = "2026-09-24",
            status = "ACTIVE"
        )
        db.saleDao().insertSale(sale)

        // Ledger entry when ACTIVE has debit 300
        val activeEntry = CustomerLedgerCalculator.saleToLedgerEntry(testCustomerId, sale)!!
        assertEquals(300.0, activeEntry.debit, 0.001)
        assertEquals(OperationStatus.ACTIVE, activeEntry.operationStatus)

        repository.reverseTransaction(saleId, "Accounting offset verification")

        // Ledger entry when REVERSED has OperationStatus.REVERSED
        val reversedSale = db.saleDao().getSaleById(saleId)!!
        val reversedEntry = CustomerLedgerCalculator.saleToLedgerEntry(testCustomerId, reversedSale)!!
        assertEquals(OperationStatus.REVERSED, reversedEntry.operationStatus)

        // Calculator summary excludes REVERSED entries (producing net effect 0.0)
        val summary = CustomerLedgerCalculator.calculateSummaryFromEntries(testCustomerId, listOf(reversedEntry))
        assertEquals(0.0, summary.balance, 0.001)
        assertEquals(0.0, summary.totalCreditSales, 0.001)
        assertEquals(1, summary.totalReversedCount)
    }

    // -------------------------------------------------------------------------
    // 7. Active balance becomes net-neutral after reversal
    // -------------------------------------------------------------------------
    @Test
    fun test7_activeBalanceBecomesNetNeutralAfterReversal() = runBlocking {
        // Initial baseline: 0.0
        val initialSummary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = db.saleDao().getSalesByCustomerIdSync(testCustomerId),
            payments = emptyList(),
            openingBalances = emptyList(),
            adjustments = emptyList()
        )
        assertEquals(0.0, initialSummary.balance, 0.001)

        // Add sale of 450.0
        val saleId = "sale_neutral_001"
        db.saleDao().insertSale(
            Sale(
                id = saleId,
                invoiceNumber = "INV-000505",
                customerId = testCustomerId,
                saleType = SaleType.CREDIT.name,
                totalAmount = 450.0,
                paidAmount = 0.0,
                creditAmount = 450.0,
                paymentStatus = PaymentStatus.UNPAID.name,
                transactionDate = "2026-09-24",
                status = "ACTIVE"
            )
        )

        // Balance increased to 450.0
        val midSummary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = db.saleDao().getSalesByCustomerIdSync(testCustomerId),
            payments = emptyList(),
            openingBalances = emptyList(),
            adjustments = emptyList()
        )
        assertEquals(450.0, midSummary.balance, 0.001)

        // Reverse the sale
        repository.reverseTransaction(saleId, "Cancelled transaction")

        // Balance restored back to initial baseline 0.0
        val finalSummary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = db.saleDao().getSalesByCustomerIdSync(testCustomerId),
            payments = emptyList(),
            openingBalances = emptyList(),
            adjustments = emptyList()
        )
        assertEquals(0.0, finalSummary.balance, 0.001)
    }

    // -------------------------------------------------------------------------
    // 8. Reversing the same transaction twice is rejected
    // -------------------------------------------------------------------------
    @Test
    fun test8_reversingSameTransactionTwiceIsRejected() = runBlocking {
        val saleId = "sale_double_001"
        db.saleDao().insertSale(
            Sale(
                id = saleId,
                invoiceNumber = "INV-000606",
                customerId = testCustomerId,
                saleType = SaleType.CREDIT.name,
                totalAmount = 200.0,
                paidAmount = 0.0,
                creditAmount = 200.0,
                paymentStatus = PaymentStatus.UNPAID.name,
                transactionDate = "2026-09-24",
                status = "ACTIVE"
            )
        )

        // First reversal succeeds
        val firstReversal = repository.reverseTransaction(saleId, "First reason")
        assertNotNull(firstReversal)

        // Second reversal MUST fail
        try {
            repository.reverseTransaction(saleId, "Second reason")
            fail("Second reversal attempt on the same transaction must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("already") == true)
        }

        // Database constraint check: exactly 1 reversal record exists
        val count = db.reversalDao().getActiveReversalCount(saleId)
        assertEquals(1, count)
    }

    // -------------------------------------------------------------------------
    // 9. Reversal requires a valid reason
    // -------------------------------------------------------------------------
    @Test
    fun test9_reversalRequiresValidReason() = runBlocking {
        val saleId = "sale_reason_001"
        db.saleDao().insertSale(
            Sale(
                id = saleId,
                invoiceNumber = "INV-000707",
                customerId = testCustomerId,
                saleType = SaleType.CREDIT.name,
                totalAmount = 100.0,
                paidAmount = 0.0,
                creditAmount = 100.0,
                paymentStatus = PaymentStatus.UNPAID.name,
                transactionDate = "2026-09-24",
                status = "ACTIVE"
            )
        )

        // Blank reason
        try {
            repository.reverseTransaction(saleId, "")
            fail("Reversal with blank reason must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("reason") == true)
        }

        // Whitespace only reason
        try {
            repository.reverseTransaction(saleId, "   ")
            fail("Reversal with whitespace-only reason must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("reason") == true)
        }

        // Transaction must remain ACTIVE
        val sale = db.saleDao().getSaleById(saleId)
        assertEquals("ACTIVE", sale!!.status)
    }

    // -------------------------------------------------------------------------
    // 10. Reversal is atomic
    // -------------------------------------------------------------------------
    @Test
    fun test10_reversalIsAtomic() = runBlocking {
        val nonExistentTxId = "non_existent_tx_999"

        try {
            repository.reverseTransaction(nonExistentTxId, "Valid reason")
            fail("Reversing nonexistent transaction must fail")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // No reversal record was created
        val rev = repository.getReversalForTransaction(nonExistentTxId)
        assertTrue(rev == null)
        val allRev: List<Reversal> = repository.getAllReversalsSync()
        assertTrue(allRev.none { it.originalTransactionId == nonExistentTxId })
    }

    // -------------------------------------------------------------------------
    // 11. Existing unrelated transactions are unchanged
    // -------------------------------------------------------------------------
    @Test
    fun test11_existingUnrelatedTransactionsAreUnchanged() = runBlocking {
        val sale1 = "sale_multi_001"
        val sale2 = "sale_multi_002"

        db.saleDao().insertSale(
            Sale(
                id = sale1,
                invoiceNumber = "INV-000801",
                customerId = testCustomerId,
                saleType = SaleType.CREDIT.name,
                totalAmount = 100.0,
                paidAmount = 0.0,
                creditAmount = 100.0,
                paymentStatus = PaymentStatus.UNPAID.name,
                transactionDate = "2026-09-24",
                status = "ACTIVE"
            )
        )
        db.saleDao().insertSale(
            Sale(
                id = sale2,
                invoiceNumber = "INV-000802",
                customerId = testCustomerId,
                saleType = SaleType.CREDIT.name,
                totalAmount = 250.0,
                paidAmount = 0.0,
                creditAmount = 250.0,
                paymentStatus = PaymentStatus.UNPAID.name,
                transactionDate = "2026-09-24",
                status = "ACTIVE"
            )
        )

        // Reverse sale1 only
        repository.reverseTransaction(sale1, "Reversed sale 1 only")

        // Sale 1 is REVERSED
        assertEquals("REVERSED", db.saleDao().getSaleById(sale1)!!.status)

        // Sale 2 is completely UNCHANGED: still ACTIVE, amount = 250.0
        val storedSale2 = db.saleDao().getSaleById(sale2)!!
        assertEquals("ACTIVE", storedSale2.status)
        assertEquals(250.0, storedSale2.totalAmount, 0.001)

        // Customer balance reflects exactly Sale 2 (250.0)
        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = db.saleDao().getSalesByCustomerIdSync(testCustomerId),
            payments = emptyList(),
            openingBalances = emptyList(),
            adjustments = emptyList()
        )
        assertEquals(250.0, summary.balance, 0.001)
    }

    // -------------------------------------------------------------------------
    // 12. Existing reports/ledger calculations do not count reversed effect as active
    // -------------------------------------------------------------------------
    @Test
    fun test12_existingReportsAndLedgerCalculationsDoNotCountReversedEffectAsActive() = runBlocking {
        val txEntity = TransactionEntity(
            id = "tx_legacy_001",
            title = "فاتورة شراء",
            customerNameSnapshot = "أحمد المحاسبي",
            activityType = "شراء آجل",
            amount = 350.0,
            isCredit = true,
            date = "2026-09-24",
            relativeTime = "اليوم",
            customerId = testCustomerId,
            creditAmount = 350.0,
            operationStatus = "ACTIVE"
        )
        db.transactionDao().insertTransaction(txEntity)

        // Active transaction impact
        val activeItem = db.transactionDao().getTransactionById("tx_legacy_001")!!.toModel()
        assertEquals(350.0, AnalysisCenterViewModel.getTransactionReceivableImpact(activeItem), 0.001)

        // Reverse it
        repository.reverseTransaction("tx_legacy_001", "Returned completely")

        // Reversed transaction impact is strictly 0.0
        val reversedItem = db.transactionDao().getTransactionById("tx_legacy_001")!!.toModel()
        assertEquals(OperationStatus.REVERSED, reversedItem.typedOperationStatus)
        assertEquals(0.0, AnalysisCenterViewModel.getTransactionReceivableImpact(reversedItem), 0.001)

        // Customer balance excludes it
        val custSummary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            transactions = listOf(reversedItem)
        )
        assertEquals(0.0, custSummary.balance, 0.001)
        assertEquals(1, custSummary.totalReversedCount)
    }

    // -------------------------------------------------------------------------
    // 13. Unsupported transaction types safely rejected
    // -------------------------------------------------------------------------
    @Test
    fun test13_unsupportedTransactionTypesRejected() = runBlocking {
        // Attempting to reverse a reversal itself
        val revTx = TransactionEntity(
            id = "tx_unsupported_rev",
            customerNameSnapshot = "أحمد",
            activityType = "إلغاء قيد",
            amount = 100.0,
            isCredit = false,
            date = "2026-09-24",
            relativeTime = "اليوم",
            notes = "Reversal transaction [REVERSAL]"
        )
        db.transactionDao().insertTransaction(revTx)

        try {
            repository.reverseTransaction("tx_unsupported_rev", "Cannot reverse a reversal")
            fail("Reversing a reversal must throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("cannot be reversed") == true)
        }
    }
}
