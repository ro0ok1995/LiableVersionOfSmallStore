package com.example.accounting

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.Adjustment
import com.example.data.db.CustomerEntity
import com.example.data.db.CustomerPayment
import com.example.data.db.FinancialAccount
import com.example.data.db.OpeningBalance
import com.example.data.db.PaymentMethod
import com.example.data.db.Sale
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.toModel
import com.example.data.repository.StoreRepository
import com.example.model.OperationStatus
import com.example.model.PaymentStatus
import com.example.model.SaleType
import com.example.model.TransactionType
import com.example.model.typedTransactionType
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
 * AdjustmentLedgerTest:
 *
 * Verifies Phase 6 Adjustments:
 * 1. DEBIT/CREDIT math in isolation.
 * 2. Combined with sales + payments + openingBalances.
 * 3. Entity-type / ID filtering returns null for non-CUSTOMER or mismatched entityId.
 * 4. Invariant violations (zero/negative amount, blank reason, blank id, blank entityId, invalid direction/type).
 * 5. Repository test confirming recordAdjustment never writes to CustomerEntity.balance directly (assert customer row is unchanged).
 * 6. Adjustments allow multiple records per entity (unlike opening balances).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AdjustmentLedgerTest {

    private lateinit var context: Context
    private lateinit var db: SmallStoreDatabase
    private lateinit var repository: StoreRepository

    private val testCustomerId = "cust_adj_test"
    private val testAccountId = "acc_cash"
    private val testPaymentMethodId = "pm_cash"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StoreRepository.createForTesting(db)

        // Seed test customer with fixed 0.0 balance and debt
        db.customerDao().insertCustomer(
            CustomerEntity(
                id = testCustomerId,
                customerName = "خالد الحربي",
                balance = 0.0,
                totalDebt = 0.0,
                phone = "0559988776",
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
     * Requirement: DEBIT adjustment math in isolation -> balance == +amount, debitAdjustments == amount.
     */
    @Test
    fun customerWithDebitAdjustment_inIsolation_increasesReceivableBalance() {
        val adj = Adjustment(
            id = "adj_debit_50",
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 50.0,
            direction = "DEBIT",
            date = "2026-09-23",
            reason = "تصحيح خطأ حسابي سابق بزيادة المديونية",
            reference = "ADJ-001"
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = emptyList(),
            payments = emptyList(),
            openingBalances = emptyList(),
            adjustments = listOf(adj)
        )

        assertEquals("Balance must be 50.0 for isolated DEBIT adjustment", 50.0, summary.balance, 0.0001)
        assertEquals("debitAdjustments must be 50.0", 50.0, summary.debitAdjustments, 0.0001)
        assertEquals("creditAdjustments must be 0.0", 0.0, summary.creditAdjustments, 0.0001)
        assertEquals("openingBalance must be 0.0", 0.0, summary.openingBalance, 0.0001)
        assertEquals("totalCreditSales must be 0.0", 0.0, summary.totalCreditSales, 0.0001)
        assertEquals("totalPayments must be 0.0", 0.0, summary.totalPayments, 0.0001)
    }

    /**
     * Requirement: CREDIT adjustment math in isolation -> balance == -amount, creditAdjustments == amount.
     */
    @Test
    fun customerWithCreditAdjustment_inIsolation_decreasesReceivableBalance() {
        val adj = Adjustment(
            id = "adj_credit_30",
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 30.0,
            direction = "CREDIT",
            date = "2026-09-23",
            reason = "خصم تسوية تجارية معتمدة",
            reference = "ADJ-002"
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = emptyList(),
            payments = emptyList(),
            openingBalances = emptyList(),
            adjustments = listOf(adj)
        )

        assertEquals("Balance must be -30.0 for isolated CREDIT adjustment", -30.0, summary.balance, 0.0001)
        assertEquals("debitAdjustments must be 0.0", 0.0, summary.debitAdjustments, 0.0001)
        assertEquals("creditAdjustments must be 30.0", 30.0, summary.creditAdjustments, 0.0001)
        assertEquals("openingBalance must be 0.0", 0.0, summary.openingBalance, 0.0001)
        assertEquals("totalCreditSales must be 0.0", 0.0, summary.totalCreditSales, 0.0001)
        assertEquals("totalPayments must be 0.0", 0.0, summary.totalPayments, 0.0001)
    }

    /**
     * Requirement: Combined math with sales + payments + openingBalances + adjustments:
     * Balance = Opening (200) + Credit Sales (100) + Debit Adjustments (40) - Payments (50) - Credit Adjustments (15) = 275.
     */
    @Test
    fun combinedLedgerCalculation_withSalesPaymentsOpeningBalancesAndAdjustments() {
        val ob = OpeningBalance(
            id = "ob_200",
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 200.0,
            direction = "DEBIT",
            date = "2026-09-01"
        )
        val sale = Sale(
            id = "sale_100",
            invoiceNumber = "INV-001",
            customerId = testCustomerId,
            saleType = SaleType.CREDIT.name,
            totalAmount = 100.0,
            paidAmount = 0.0,
            creditAmount = 100.0,
            paymentStatus = PaymentStatus.UNPAID.name,
            transactionDate = "2026-09-05",
            status = OperationStatus.ACTIVE.name
        )
        val payment = CustomerPayment(
            id = "pay_50",
            customerId = testCustomerId,
            amount = 50.0,
            paymentMethodId = testPaymentMethodId,
            financialAccountId = testAccountId,
            reference = "PAY-001",
            transactionDate = "2026-09-10",
            status = "ACTIVE"
        )
        val debitAdj = Adjustment(
            id = "adj_debit_40",
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 40.0,
            direction = "DEBIT",
            date = "2026-09-15",
            reason = "رسوم إضافية متفق عليها"
        )
        val creditAdj = Adjustment(
            id = "adj_credit_15",
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 15.0,
            direction = "CREDIT",
            date = "2026-09-20",
            reason = "تسوية خصم تجاري"
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = listOf(sale),
            payments = listOf(payment),
            openingBalances = listOf(ob),
            adjustments = listOf(debitAdj, creditAdj)
        )

        // Balance = 200 + 100 + 40 - (50 + 15) = 275
        assertEquals("Calculated receivable balance must be 275.0", 275.0, summary.balance, 0.0001)
        assertEquals("openingBalance must be strictly 200.0", 200.0, summary.openingBalance, 0.0001)
        assertEquals("totalCreditSales must be strictly 100.0", 100.0, summary.totalCreditSales, 0.0001)
        assertEquals("totalPayments must be strictly 50.0", 50.0, summary.totalPayments, 0.0001)
        assertEquals("debitAdjustments must be strictly 40.0", 40.0, summary.debitAdjustments, 0.0001)
        assertEquals("creditAdjustments must be strictly 15.0", 15.0, summary.creditAdjustments, 0.0001)
    }

    /**
     * Requirement: entity-type/id filtering returns null when adjustment.entityType != "CUSTOMER"
     * or adjustment.entityId does not match the customerId passed in.
     */
    @Test
    fun adjustmentToLedgerEntry_filteringInvariants() {
        val validAdj = Adjustment(
            id = "adj_cust",
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 75.0,
            direction = "DEBIT",
            date = "2026-09-23",
            reason = "فارق رصيد مدين",
            reference = "REF-75"
        )

        // Matching customer -> maps correctly
        val entry = CustomerLedgerCalculator.adjustmentToLedgerEntry(testCustomerId, validAdj)
        assertNotNull("Must not be null for matching CUSTOMER entity", entry)
        assertEquals(testCustomerId, entry?.customerId)
        assertEquals(TransactionType.BALANCE_ADJUSTMENT, entry?.transactionType)
        assertEquals(75.0, entry?.debit ?: 0.0, 0.0001)
        assertEquals(0.0, entry?.credit ?: 0.0, 0.0001)
        assertEquals(OperationStatus.ACTIVE, entry?.operationStatus)
        assertEquals("فارق رصيد مدين", entry?.description)

        // CREDIT direction mapping
        val creditAdj = validAdj.copy(id = "adj_credit", direction = "CREDIT", amount = 25.0)
        val creditEntry = CustomerLedgerCalculator.adjustmentToLedgerEntry(testCustomerId, creditAdj)
        assertNotNull(creditEntry)
        assertEquals(0.0, creditEntry?.debit ?: 0.0, 0.0001)
        assertEquals(25.0, creditEntry?.credit ?: 0.0, 0.0001)

        // Mismatched customer ID -> returns null
        val mismatchedEntry = CustomerLedgerCalculator.adjustmentToLedgerEntry("other_customer_id", validAdj)
        assertNull("Must return null when adjustment.entityId does not match customerId", mismatchedEntry)

        // Non-CUSTOMER entity types -> returns null
        val supplierAdj = Adjustment(
            id = "adj_sup",
            entityType = "SUPPLIER",
            entityId = testCustomerId,
            amount = 100.0,
            direction = "CREDIT",
            date = "2026-09-23",
            reason = "تعديل رصيد مورد"
        )
        assertNull(
            "Must return null for SUPPLIER adjustment in customer ledger",
            CustomerLedgerCalculator.adjustmentToLedgerEntry(testCustomerId, supplierAdj)
        )

        val accountAdj = Adjustment(
            id = "adj_fa",
            entityType = "FINANCIAL_ACCOUNT",
            entityId = testCustomerId,
            amount = 200.0,
            direction = "DEBIT",
            date = "2026-09-23",
            reason = "تعديل رصيد حساب مالي"
        )
        assertNull(
            "Must return null for FINANCIAL_ACCOUNT adjustment in customer ledger",
            CustomerLedgerCalculator.adjustmentToLedgerEntry(testCustomerId, accountAdj)
        )
    }

    /**
     * Requirement: Invariant violations (zero/negative amount, blank reason, blank id, blank entityId).
     */
    @Test
    fun adjustment_enforcesEntityInvariants() {
        // Zero amount must throw
        try {
            Adjustment(id = "adj_1", entityType = "CUSTOMER", entityId = "c1", amount = 0.0, direction = "DEBIT", date = "2026-09-23", reason = "Reason")
            fail("Should have thrown IllegalArgumentException for amount == 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("strictly positive") == true)
        }

        // Negative amount must throw
        try {
            Adjustment(id = "adj_2", entityType = "CUSTOMER", entityId = "c1", amount = -10.0, direction = "DEBIT", date = "2026-09-23", reason = "Reason")
            fail("Should have thrown IllegalArgumentException for negative amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("strictly positive") == true)
        }

        // Blank reason must throw
        try {
            Adjustment(id = "adj_3", entityType = "CUSTOMER", entityId = "c1", amount = 10.0, direction = "DEBIT", date = "2026-09-23", reason = "   ")
            fail("Should have thrown IllegalArgumentException for blank reason")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("reason must not be blank") == true)
        }

        // Blank id must throw
        try {
            Adjustment(id = "", entityType = "CUSTOMER", entityId = "c1", amount = 10.0, direction = "DEBIT", date = "2026-09-23", reason = "Reason")
            fail("Should have thrown IllegalArgumentException for blank id")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("ID must not be blank") == true)
        }

        // Blank entityId must throw
        try {
            Adjustment(id = "adj_5", entityType = "CUSTOMER", entityId = "  ", amount = 10.0, direction = "DEBIT", date = "2026-09-23", reason = "Reason")
            fail("Should have thrown IllegalArgumentException for blank entityId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("entityId must not be blank") == true)
        }

        // Invalid entityType must throw
        try {
            Adjustment(id = "adj_6", entityType = "INVALID", entityId = "c1", amount = 10.0, direction = "DEBIT", date = "2026-09-23", reason = "Reason")
            fail("Should have thrown IllegalArgumentException for invalid entityType")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid entityType") == true)
        }

        // Invalid direction must throw
        try {
            Adjustment(id = "adj_7", entityType = "CUSTOMER", entityId = "c1", amount = 10.0, direction = "UNKNOWN", date = "2026-09-23", reason = "Reason")
            fail("Should have thrown IllegalArgumentException for invalid direction")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid direction") == true)
        }
    }

    /**
     * Requirement: Repository test confirming recordAdjustment never writes to CustomerEntity.balance directly
     * (assert the customer row is unchanged after the call).
     */
    @Test
    fun repository_recordAdjustment_neverMutatesCustomerEntityDirectly() = runBlocking {
        // Confirm baseline customer entity values
        val customerBefore = db.customerDao().getCustomerById(testCustomerId)
        assertNotNull(customerBefore)
        assertEquals(0.0, customerBefore?.balance ?: -1.0, 0.0001)
        assertEquals(0.0, customerBefore?.totalDebt ?: -1.0, 0.0001)

        // Record a DEBIT adjustment of 150.0 via StoreRepository
        val recordedAdj = repository.recordAdjustment(
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 150.0,
            direction = "DEBIT",
            date = "2026-09-23",
            reason = "تصحيح رصيد يدوي",
            reference = "ADJ-TEST-DIRECT"
        )
        assertNotNull(recordedAdj)

        // Query the persisted CustomerEntity directly from the database table
        val customerAfter = db.customerDao().getCustomerById(testCustomerId)
        assertNotNull(customerAfter)
        assertEquals(
            "CustomerEntity.balance must NOT be modified by recordAdjustment directly",
            0.0,
            customerAfter?.balance ?: -1.0,
            0.0001
        )
        assertEquals(
            "CustomerEntity.totalDebt must NOT be modified by recordAdjustment directly",
            0.0,
            customerAfter?.totalDebt ?: -1.0,
            0.0001
        )

        // However, the ledger calculator reproducing balance reflects the adjustment:
        val ledgerSummary = repository.getCustomerBalance(testCustomerId)
        assertEquals("Ledger balance must reflect the adjustment", 150.0, ledgerSummary.balance, 0.0001)
        assertEquals("Ledger debitAdjustments must be 150.0", 150.0, ledgerSummary.debitAdjustments, 0.0001)
    }

    /**
     * Requirement: Unlike opening balances, adjustments are NOT limited to one per entity.
     * Multiple adjustments for the same entity succeed.
     */
    @Test
    fun repository_allowsMultipleAdjustmentsForSameEntity() = runBlocking {
        val adj1 = repository.recordAdjustment(
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 100.0,
            direction = "DEBIT",
            date = "2026-09-23",
            reason = "Adjustment 1"
        )
        val adj2 = repository.recordAdjustment(
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 40.0,
            direction = "CREDIT",
            date = "2026-09-23",
            reason = "Adjustment 2"
        )

        assertNotNull(adj1)
        assertNotNull(adj2)

        val customerAdjustments = repository.getAdjustmentsByEntitySync("CUSTOMER", testCustomerId)
        assertEquals("Must contain 2 adjustment records for this customer", 2, customerAdjustments.size)
    }

    /**
     * Requirement: Recording an adjustment for a non-existent CUSTOMER throws IllegalArgumentException.
     */
    @Test
    fun repository_recordAdjustment_nonExistentCustomer_throwsIllegalArgumentException() = runBlocking {
        try {
            repository.recordAdjustment(
                entityType = "CUSTOMER",
                entityId = "non_existent_cust_404",
                amount = 80.0,
                direction = "DEBIT",
                date = "2026-09-23",
                reason = "Correction"
            )
            fail("Expected IllegalArgumentException for non-existent customer")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Exception message must mention Customer not found",
                e.message?.contains("Customer not found") == true
            )
        }
    }

    /**
     * Requirement: Adjustment appears in the customer statement/history.
     */
    @Test
    fun adjustmentAppearsInCustomerStatementHistory() = runBlocking {
        val adj = repository.recordAdjustment(
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 95.0,
            direction = "DEBIT",
            date = "2026-09-23",
            reason = "تسوية فروقات جردية",
            reference = "STMT-REF-95"
        )
        assertNotNull(adj)

        // Query customer transactions (which feed the account statement / activity history)
        val customerTxs = db.transactionDao().getTransactionsByCustomerIdSync(testCustomerId).map { it.toModel() }
        val statementTx = customerTxs.find { it.id == adj.id }

        assertNotNull("Recorded adjustment must appear in customer transaction history/statement", statementTx)
        assertEquals(95.0, statementTx?.amount ?: 0.0, 0.0001)
        assertEquals("2026-09-23", statementTx?.date)
        assertEquals(TransactionType.BALANCE_ADJUSTMENT, statementTx?.typedTransactionType)
        assertTrue("Statement notes must contain reason", statementTx?.notes?.contains("تسوية فروقات جردية") == true)
        assertTrue("Statement notes must contain reference", statementTx?.notes?.contains("STMT-REF-95") == true)
    }

    /**
     * Requirement: Existing unrelated sales and payments remain unchanged when an adjustment is recorded.
     */
    @Test
    fun existingUnrelatedSalesAndPaymentsRemainUnchanged() = runBlocking {
        // Create an existing sale
        val initialSale = Sale(
            id = "sale_unrelated_1",
            invoiceNumber = "INV-UNCHANGED-1",
            customerId = testCustomerId,
            saleType = SaleType.CREDIT.name,
            totalAmount = 500.0,
            paidAmount = 100.0,
            creditAmount = 400.0,
            paymentStatus = PaymentStatus.PARTIAL.name,
            transactionDate = "2026-09-01",
            status = OperationStatus.ACTIVE.name
        )
        db.saleDao().insertSale(initialSale)

        // Create an existing payment
        val initialPayment = CustomerPayment(
            id = "pay_unrelated_1",
            customerId = testCustomerId,
            amount = 100.0,
            paymentMethodId = testPaymentMethodId,
            financialAccountId = testAccountId,
            reference = "REF-UNCHANGED-1",
            transactionDate = "2026-09-02",
            status = "ACTIVE"
        )
        db.customerPaymentDao().insertPayment(initialPayment)

        // Record a new adjustment
        repository.recordAdjustment(
            entityType = "CUSTOMER",
            entityId = testCustomerId,
            amount = 50.0,
            direction = "CREDIT",
            date = "2026-09-23",
            reason = "خصم استثنائي"
        )

        // Assert sale row is untouched
        val retrievedSale = db.saleDao().getSaleById("sale_unrelated_1")
        assertNotNull(retrievedSale)
        assertEquals(500.0, retrievedSale?.totalAmount ?: 0.0, 0.0001)
        assertEquals(400.0, retrievedSale?.creditAmount ?: 0.0, 0.0001)
        assertEquals(PaymentStatus.PARTIAL.name, retrievedSale?.paymentStatus)

        // Assert payment row is untouched
        val retrievedPayment = db.customerPaymentDao().getPaymentById("pay_unrelated_1")
        assertNotNull(retrievedPayment)
        assertEquals(100.0, retrievedPayment?.amount ?: 0.0, 0.0001)
        assertEquals("ACTIVE", retrievedPayment?.status)
        assertEquals("REF-UNCHANGED-1", retrievedPayment?.reference)
    }
}
