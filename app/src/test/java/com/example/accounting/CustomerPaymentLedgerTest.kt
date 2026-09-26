package com.example.accounting

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.CustomerEntity
import com.example.data.db.CustomerPayment
import com.example.data.db.FinancialAccount
import com.example.data.db.PaymentMethod
import com.example.data.db.Sale
import com.example.data.db.SmallStoreDatabase
import com.example.data.repository.StoreRepository
import com.example.model.OperationStatus
import com.example.model.PaymentStatus
import com.example.model.SaleType
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
 * CustomerPaymentLedgerTest:
 *
 * Verifies Phase 4 Customer Payments and Financial Accounts:
 * 1. Credit sale 100, payment 30 -> receivable 70.
 * 2. Credit sale 100, payment 100 -> receivable 0.
 * 3. Payment never appears in a sales total; a Sale never appears in a payments total.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CustomerPaymentLedgerTest {

    private lateinit var context: Context
    private lateinit var db: SmallStoreDatabase
    private lateinit var repository: StoreRepository

    private val testCustomerId = "cust_ledger_test"
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
                customerName = "خالد العتيبي",
                balance = 0.0,
                totalDebt = 0.0,
                phone = "0550001122",
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
     * Requirement: Credit sale 100, payment 30 -> receivable 70.
     */
    @Test
    fun creditSale100_payment30_resultsInReceivable70() = runBlocking {
        // Pure domain calculation check
        val sale = Sale(
            id = "sale_100",
            invoiceNumber = "INV-0001",
            customerId = testCustomerId,
            saleType = SaleType.CREDIT.name,
            totalAmount = 100.0,
            paidAmount = 0.0,
            creditAmount = 100.0,
            paymentStatus = PaymentStatus.UNPAID.name,
            transactionDate = "2026-09-23",
            status = OperationStatus.ACTIVE.name
        )
        val payment30 = CustomerPayment(
            id = "pay_30",
            customerId = testCustomerId,
            amount = 30.0,
            paymentMethodId = testPaymentMethodId,
            financialAccountId = testAccountId,
            reference = "REF-30",
            transactionDate = "2026-09-23",
            status = "ACTIVE"
        )

        val pureSummary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = listOf(sale),
            payments = listOf(payment30)
        )
        assertEquals("Receivable must be 70 after 30 payment on 100 sale", 70.0, pureSummary.balance, 0.0001)
        assertEquals("Total credit sales must be 100", 100.0, pureSummary.totalCreditSales, 0.0001)
        assertEquals("Total payments must be 30", 30.0, pureSummary.totalPayments, 0.0001)

        // Repository & Database check
        repository.createSale(sale, emptyList())
        val recordedPayment = repository.recordCustomerPayment(
            customerId = testCustomerId,
            amount = 30.0,
            paymentMethodId = testPaymentMethodId,
            financialAccountId = testAccountId,
            reference = "REF-30",
            notes = "دفعة على الحساب"
        )
        assertNotNull(recordedPayment)
        assertEquals(30.0, recordedPayment.amount, 0.0001)

        val repoSummary = repository.getCustomerBalance(testCustomerId)
        assertEquals("Repo customer balance must be 70.0", 70.0, repoSummary.balance, 0.0001)
        assertEquals(100.0, repoSummary.totalCreditSales, 0.0001)
        assertEquals(30.0, repoSummary.totalPayments, 0.0001)
    }

    /**
     * Requirement: Credit sale 100, payment 100 -> receivable 0.
     */
    @Test
    fun creditSale100_payment100_resultsInReceivable0() = runBlocking {
        // Pure domain calculation check
        val sale = Sale(
            id = "sale_100",
            invoiceNumber = "INV-0002",
            customerId = testCustomerId,
            saleType = SaleType.CREDIT.name,
            totalAmount = 100.0,
            paidAmount = 0.0,
            creditAmount = 100.0,
            paymentStatus = PaymentStatus.UNPAID.name,
            transactionDate = "2026-09-23",
            status = OperationStatus.ACTIVE.name
        )
        val payment100 = CustomerPayment(
            id = "pay_100",
            customerId = testCustomerId,
            amount = 100.0,
            paymentMethodId = testPaymentMethodId,
            financialAccountId = testAccountId,
            reference = "REF-100",
            transactionDate = "2026-09-23",
            status = "ACTIVE"
        )

        val pureSummary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = testCustomerId,
            sales = listOf(sale),
            payments = listOf(payment100)
        )
        assertEquals("Receivable must be 0 after full 100 payment on 100 sale", 0.0, pureSummary.balance, 0.0001)
        assertEquals("Total credit sales must be 100", 100.0, pureSummary.totalCreditSales, 0.0001)
        assertEquals("Total payments must be 100", 100.0, pureSummary.totalPayments, 0.0001)

        // Repository & Database check
        repository.createSale(sale, emptyList())
        val recordedPayment = repository.recordCustomerPayment(
            customerId = testCustomerId,
            amount = 100.0,
            paymentMethodId = testPaymentMethodId,
            financialAccountId = testAccountId,
            reference = "REF-100",
            notes = "سداد كامل الحساب"
        )
        assertNotNull(recordedPayment)
        assertEquals(100.0, recordedPayment.amount, 0.0001)

        val repoSummary = repository.getCustomerBalance(testCustomerId)
        assertEquals("Repo customer balance must be fully settled at 0.0", 0.0, repoSummary.balance, 0.0001)
        assertEquals(100.0, repoSummary.totalCreditSales, 0.0001)
        assertEquals(100.0, repoSummary.totalPayments, 0.0001)
    }

    /**
     * Requirement: Payment never appears in a sales total; a Sale never appears in a payments total.
     */
    @Test
    fun paymentNeverAppearsInSalesTotal_andSaleNeverAppearsInPaymentsTotal() = runBlocking {
        // Step 1: Create a credit sale of 100
        val sale = Sale(
            id = "sale_iso_001",
            invoiceNumber = "INV-ISO-01",
            customerId = testCustomerId,
            saleType = SaleType.CREDIT.name,
            totalAmount = 100.0,
            paidAmount = 0.0,
            creditAmount = 100.0,
            paymentStatus = PaymentStatus.UNPAID.name,
            transactionDate = "2026-09-23",
            status = OperationStatus.ACTIVE.name
        )
        repository.createSale(sale, emptyList())

        // Step 2: Record a customer payment of 30
        repository.recordCustomerPayment(
            customerId = testCustomerId,
            amount = 30.0,
            paymentMethodId = testPaymentMethodId,
            financialAccountId = testAccountId,
            reference = "REF-ISO-30",
            notes = "سداد نقدي"
        )

        // 1. Verify Sales table contains ONLY the sale (never the payment)
        val salesList = repository.getAllSalesSync()
        assertEquals("sales table must contain exactly 1 sale", 1, salesList.size)
        val salesTotal = salesList.sumOf { it.totalAmount }
        assertEquals("Sales total must be exactly 100.0, not contaminated by payment", 100.0, salesTotal, 0.0001)

        // 2. Verify Customer Payments table contains ONLY the payment (never the sale)
        val paymentsList = repository.getAllCustomerPaymentsSync()
        assertEquals("customer_payments table must contain exactly 1 payment", 1, paymentsList.size)
        val paymentsTotal = paymentsList.sumOf { it.amount }
        assertEquals("Payments total must be exactly 30.0, not contaminated by sale", 30.0, paymentsTotal, 0.0001)

        // 3. Verify ledger summary separation
        val summary = repository.getCustomerBalance(testCustomerId)
        assertEquals("totalCreditSales must reflect ONLY sales (100.0)", 100.0, summary.totalCreditSales, 0.0001)
        assertEquals("totalPayments must reflect ONLY payments (30.0)", 30.0, summary.totalPayments, 0.0001)
        assertEquals("Receivable balance must be 70.0 (100 - 30)", 70.0, summary.balance, 0.0001)
    }

    /**
     * Requirement: CustomerPayment invariants (amount > 0, status in {"ACTIVE", "REVERSED"}).
     */
    @Test
    fun customerPayment_enforcesInvariants() {
        // Amount <= 0 must throw IllegalArgumentException
        try {
            CustomerPayment(
                id = "pay_inv_1",
                customerId = testCustomerId,
                amount = 0.0,
                paymentMethodId = testPaymentMethodId,
                transactionDate = "2026-09-23"
            )
            fail("Expected IllegalArgumentException for amount <= 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive"))
        }

        try {
            CustomerPayment(
                id = "pay_inv_2",
                customerId = testCustomerId,
                amount = -50.0,
                paymentMethodId = testPaymentMethodId,
                transactionDate = "2026-09-23"
            )
            fail("Expected IllegalArgumentException for negative amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive"))
        }

        // Invalid status must throw IllegalArgumentException
        try {
            CustomerPayment(
                id = "pay_inv_3",
                customerId = testCustomerId,
                amount = 50.0,
                paymentMethodId = testPaymentMethodId,
                transactionDate = "2026-09-23",
                status = "PENDING"
            )
            fail("Expected IllegalArgumentException for invalid status")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("status"))
        }
    }
}
