package com.example.accounting

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.CustomerEntity
import com.example.data.db.CustomerPayment
import com.example.data.db.FinancialAccount
import com.example.data.db.OpeningBalance
import com.example.data.db.PaymentMethod
import com.example.data.db.Sale
import com.example.data.db.SmallStoreDatabase
import com.example.data.repository.StoreRepository
import com.example.model.OperationStatus
import com.example.model.PaymentStatus
import com.example.model.SaleType
import com.example.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OpeningBalanceLedgerTest:
 *
 * Verifies Phase 5 Opening Balances:
 * 1. Customer with a DEBIT opening balance of 200 and no other activity -> balance == 200.
 * 2. Customer with a CREDIT opening balance of 50 and no other activity -> balance == -50.
 * 3. Customer with DEBIT opening balance 200, credit sale 100, payment 50 -> balance == 250.
 *    openingBalance and totalCreditSales remain separate fields on CustomerBalanceSummary.
 * 4. openingBalanceToLedgerEntry returns null for mismatched entityId or non-CUSTOMER entityType.
 * 5. Entity invariants on OpeningBalance enforce non-blank IDs, positive amount, valid entityType and direction.
 * 6. Repository-level tests:
 *    - Duplicate opening balance for the same (entityType, entityId) throws IllegalStateException.
 *    - Opening balance for non-existent CUSTOMER throws IllegalArgumentException.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OpeningBalanceLedgerTest {

    private lateinit var context: Context
    private lateinit var db: SmallStoreDatabase
    private lateinit var repository: StoreRepository

    private val testCustomerId = "cust_ob_test"
    private val testAccountId = "acc_cash"
    private val testPaymentMethodId = "pm_cash"

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
                customerName = "عبدالرحمن الشمري",
                balance = 0.0,
                totalDebt = 0.0,
                phone = "0551122334",
                lastTransactionDate = "2026-09-23",
                hasRecentActivity = false
            )
        )

        // Seed test financial account
        repository.createFinancialAccount(
            FinancialAccount(
                id = testAccountId,
                name = "Cash",
                type = "CASH",
                isActive = true
            )
        )

        // Seed test payment method
        repository.createPaymentMethod(
            PaymentMethod(
                id = testPaymentMethodId,
                name = "Cash",
                type = "CASH",
                isActive = true
            )
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    /**
     * Requirement: Customer with a DEBIT opening balance of 200 and no other activity -> balance == 200.
     */
    @Test
    fun customerWithDebitOpeningBalance200_noOtherActivity_resultsInBalance200() {
        val ob = OpeningBalance(
            id = "ob_debit_200",
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 200.0,
            direction = "DEBIT",
            date = "2026-09-01",
            reason = "رصيد مرحل سابق",
            reference = "OB-001"
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = emptyList(),
            payments = emptyList(),
            openingBalances = listOf(ob)
        )

        assertEquals("Balance must be 200.0 for DEBIT opening balance", 200.0, summary.balance, 0.0001)
        assertEquals("openingBalance field must be 200.0", 200.0, summary.openingBalance, 0.0001)
        assertEquals("totalCreditSales must be 0.0", 0.0, summary.totalCreditSales, 0.0001)
        assertEquals("totalPayments must be 0.0", 0.0, summary.totalPayments, 0.0001)
    }

    /**
     * Requirement: Customer with a CREDIT opening balance of 50 (store owes customer) and no other activity -> balance == -50.
     */
    @Test
    fun customerWithCreditOpeningBalance50_noOtherActivity_resultsInBalanceNegative50() {
        val ob = OpeningBalance(
            id = "ob_credit_50",
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 50.0,
            direction = "CREDIT",
            date = "2026-09-01",
            reason = "دفعة مقدمة سابقة للمحل",
            reference = "OB-002"
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = emptyList(),
            payments = emptyList(),
            openingBalances = listOf(ob)
        )

        assertEquals("Balance must be -50.0 for CREDIT opening balance", -50.0, summary.balance, 0.0001)
        assertEquals("openingBalance field must be -50.0", -50.0, summary.openingBalance, 0.0001)
        assertEquals("totalCreditSales must be 0.0", 0.0, summary.totalCreditSales, 0.0001)
        assertEquals("totalPayments must be 0.0", 0.0, summary.totalPayments, 0.0001)
    }

    /**
     * Requirement: Customer with DEBIT opening balance 200, then a credit sale of 100, then a payment of 50 ->
     * balance == 250 (200 + 100 - 50).
     * Assert this does NOT get counted as part of totalCreditSales for the period —
     * openingBalance and totalCreditSales remain separate fields on CustomerBalanceSummary, not merged.
     */
    @Test
    fun customerWithDebitOpeningBalance200_creditSale100_payment50_resultsInBalance250_withSeparateFields() {
        val ob = OpeningBalance(
            id = "ob_debit_200",
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 200.0,
            direction = "DEBIT",
            date = "2026-09-01"
        )
        val sale = Sale(
            id = "sale_100",
            invoiceNumber = "INV-0001",
            customerId = testCustomerId,
            saleType = SaleType.CREDIT.name,
            totalAmount = 100.0,
            paidAmount = 0.0,
            creditAmount = 100.0,
            paymentStatus = PaymentStatus.UNPAID.name,
            transactionDate = "2026-09-10",
            status = OperationStatus.ACTIVE.name
        )
        val payment = CustomerPayment(
            id = "pay_50",
            customerId = testCustomerId,
            amount = 50.0,
            paymentMethodId = testPaymentMethodId,
            financialAccountId = testAccountId,
            reference = "PAY-001",
            transactionDate = "2026-09-15",
            status = "ACTIVE"
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = listOf(sale),
            payments = listOf(payment),
            openingBalances = listOf(ob)
        )

        // Invariant: Balance = 200 (Opening) + 100 (Credit Sale) - 50 (Payment) = 250
        assertEquals("Receivable balance must be 250.0", 250.0, summary.balance, 0.0001)
        // Invariant: Opening balance is kept separate from period credit sales
        assertEquals("openingBalance must remain strictly 200.0", 200.0, summary.openingBalance, 0.0001)
        assertEquals("totalCreditSales must remain strictly 100.0 (not 300.0)", 100.0, summary.totalCreditSales, 0.0001)
        assertEquals("totalPayments must be 50.0", 50.0, summary.totalPayments, 0.0001)
    }

    /**
     * Requirement: openingBalanceToLedgerEntry returns null when ob.entityType != "CUSTOMER" or ob.entityId
     * does not match the customerId passed in.
     */
    @Test
    fun openingBalanceToLedgerEntry_filteringInvariants() {
        val validOb = OpeningBalance(
            id = "ob_cust",
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 120.0,
            direction = "DEBIT",
            date = "2026-09-01"
        )

        // Matching customer -> maps correctly
        val entry = CustomerLedgerCalculator.openingBalanceToLedgerEntry(testCustomerId, validOb)
        assertNotNull("Must not be null for matching CUSTOMER entity", entry)
        assertEquals(testCustomerId, entry?.customerId)
        assertEquals(TransactionType.OPENING_BALANCE, entry?.transactionType)
        assertEquals(120.0, entry?.debit ?: 0.0, 0.0001)
        assertEquals(0.0, entry?.credit ?: 0.0, 0.0001)
        assertEquals(OperationStatus.ACTIVE, entry?.operationStatus)

        // Credit direction mapping
        val creditOb = validOb.copy(id = "ob_credit", direction = "CREDIT", amount = 80.0)
        val creditEntry = CustomerLedgerCalculator.openingBalanceToLedgerEntry(testCustomerId, creditOb)
        assertNotNull(creditEntry)
        assertEquals(0.0, creditEntry?.debit ?: 0.0, 0.0001)
        assertEquals(80.0, creditEntry?.credit ?: 0.0, 0.0001)

        // Mismatched customer ID -> returns null
        val mismatchedCustomerEntry = CustomerLedgerCalculator.openingBalanceToLedgerEntry("other_customer_id", validOb)
        assertNull("Must return null when ob.entityId does not match customerId", mismatchedCustomerEntry)

        // Non-CUSTOMER entity types -> returns null
        val supplierOb = OpeningBalance(
            id = "ob_supplier",
            entityType = "SUPPLIER",
            entityId = testCustomerId,
            amount = 300.0,
            direction = "CREDIT",
            date = "2026-09-01"
        )
        assertNull("Must return null for SUPPLIER opening balance in customer ledger",
            CustomerLedgerCalculator.openingBalanceToLedgerEntry(testCustomerId, supplierOb)
        )

        val accountOb = OpeningBalance(
            id = "ob_acc",
            entityType = "FINANCIAL_ACCOUNT",
            entityId = testCustomerId,
            amount = 500.0,
            direction = "CREDIT",
            date = "2026-09-01"
        )
        assertNull("Must return null for FINANCIAL_ACCOUNT opening balance in customer ledger",
            CustomerLedgerCalculator.openingBalanceToLedgerEntry(testCustomerId, accountOb)
        )
    }

    /**
     * Requirement: Entity invariant tests on OpeningBalance:
     * amount <= 0 throws, blank entityId throws, invalid entityType/direction string throws.
     */
    @Test
    fun openingBalance_enforcesEntityInvariants() {
        // Zero amount must throw
        try {
            OpeningBalance(id = "ob_1", entityType = "CUSTOMER", entityId = "c1", amount = 0.0, direction = "DEBIT", date = "2026-09-01")
            fail("Should have thrown IllegalArgumentException for amount == 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("strictly positive") == true)
        }

        // Negative amount must throw
        try {
            OpeningBalance(id = "ob_2", entityType = "CUSTOMER", entityId = "c1", amount = -50.0, direction = "DEBIT", date = "2026-09-01")
            fail("Should have thrown IllegalArgumentException for negative amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("strictly positive") == true)
        }

        // Blank entityId must throw
        try {
            OpeningBalance(id = "ob_3", entityType = "CUSTOMER", entityId = "   ", amount = 100.0, direction = "DEBIT", date = "2026-09-01")
            fail("Should have thrown IllegalArgumentException for blank entityId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("entityId must not be blank") == true)
        }

        // Blank id must throw
        try {
            OpeningBalance(id = "", entityType = "CUSTOMER", entityId = "c1", amount = 100.0, direction = "DEBIT", date = "2026-09-01")
            fail("Should have thrown IllegalArgumentException for blank id")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("ID must not be blank") == true)
        }

        // Invalid entityType must throw
        try {
            OpeningBalance(id = "ob_4", entityType = "INVALID_TYPE", entityId = "c1", amount = 100.0, direction = "DEBIT", date = "2026-09-01")
            fail("Should have thrown IllegalArgumentException for invalid entityType")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid entityType") == true)
        }

        // Invalid direction must throw
        try {
            OpeningBalance(id = "ob_5", entityType = "CUSTOMER", entityId = "c1", amount = 100.0, direction = "INVALID_DIR", date = "2026-09-01")
            fail("Should have thrown IllegalArgumentException for invalid direction")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid direction") == true)
        }
    }

    /**
     * Requirement: Repository-level test:
     * Recording a second opening balance for the same (entityType, entityId) throws IllegalStateException.
     */
    @Test
    fun repository_recordDuplicateOpeningBalance_throwsIllegalStateException() = runBlocking {
        // First record succeeds
        val ob1 = repository.recordOpeningBalance(
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 200.0,
            direction = "DEBIT",
            date = "2026-09-01",
            reason = "Initial setup"
        )
        assertNotNull(ob1)

        // Second record for the same customer must throw IllegalStateException
        try {
            repository.recordOpeningBalance(
                entityType = "CUSTOMER",
                entityId = testCustomerId,
                amount = 100.0,
                direction = "DEBIT",
                date = "2026-09-02"
            )
            fail("Expected IllegalStateException when recording a second opening balance for the same entity")
        } catch (e: IllegalStateException) {
            assertTrue(
                "Exception message must mention that an opening balance already exists",
                e.message?.contains("Opening balance already recorded") == true
            )
        }
    }

    /**
     * Requirement: Repository-level test:
     * Recording an opening balance for a CUSTOMER entityId that does not exist throws IllegalArgumentException.
     */
    @Test
    fun repository_recordOpeningBalance_nonExistentCustomer_throwsIllegalArgumentException() = runBlocking {
        try {
            repository.recordOpeningBalance(
                entityType = "CUSTOMER",
                entityId = "non_existent_cust_999",
                amount = 150.0,
                direction = "DEBIT",
                date = "2026-09-01"
            )
            fail("Expected IllegalArgumentException when recording opening balance for non-existent customer")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Exception message must mention Customer not found",
                e.message?.contains("Customer not found") == true
            )
        }
    }
}
