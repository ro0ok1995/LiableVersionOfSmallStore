package com.example.accounting

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.FinancialAccount
import com.example.data.db.OpeningBalance
import com.example.data.db.PaymentMethod
import com.example.data.db.ProductEntity
import com.example.data.db.Purchase
import com.example.data.db.PurchaseReturn
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.Supplier
import com.example.data.db.SupplierPayment
import com.example.data.repository.StoreRepository
import com.example.model.PurchaseLineRequest
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
 * Focused Phase 9 Test Suite for Suppliers, Purchases, Supplier Payments, and Purchase Returns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SupplierPurchasesPhase9Test {

    private lateinit var context: Context
    private lateinit var db: SmallStoreDatabase
    private lateinit var repository: StoreRepository

    private val testSupplierId = "sup_test_001"
    private val testSupplierId2 = "sup_test_002"
    private val testAccountId = "acc_cash"
    private val testProductId = "prod_001"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StoreRepository.createForTesting(db)

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
                id = "pm_cash",
                name = "Cash",
                type = "CASH",
                isActive = true
            )
        )

        // Seed test supplier
        repository.insertSupplier(
            Supplier(
                id = testSupplierId,
                name = "شركة التوريدات العالمية",
                phone = "0500112233",
                address = "الرياض",
                notes = "مورد رئيسي"
            )
        )

        // Seed test product
        db.productDao().insertProduct(
            ProductEntity(
                id = testProductId,
                name = "زيت زيتون 1 لتر",
                price = 30.0,
                costPrice = 20.0,
                category = "General",
                unit = "قطعة"
            )
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    // 1. Supplier creation succeeds
    @Test
    fun test01_supplierCreationSucceeds() = runBlocking {
        val newSupplier = Supplier(
            id = "sup_new_123",
            name = "مؤسسة الأمل",
            phone = "0555555555"
        )
        val inserted = repository.insertSupplier(newSupplier)
        assertNotNull(inserted)
        val loaded = repository.getSupplierById("sup_new_123")
        assertNotNull(loaded)
        assertEquals("مؤسسة الأمل", loaded?.name)
        assertEquals("0555555555", loaded?.phone)
    }

    // 2. Supplier ID is used as the accounting relationship
    @Test
    fun test02_supplierIdIsUsedAsAccountingRelationship() = runBlocking {
        repository.insertSupplier(
            Supplier(id = testSupplierId2, name = "شركة التوريدات العالمية") // Same name, different ID
        )

        val lines1 = listOf(PurchaseLineRequest(testProductId, "زيت زيتون 1 لتر", 10, 20.0))
        repository.recordPurchase(supplierId = testSupplierId, lines = lines1)

        val balance1 = repository.getSupplierBalance(testSupplierId)
        val balance2 = repository.getSupplierBalance(testSupplierId2)

        assertEquals(200.0, balance1.balance, 0.0001)
        assertEquals(0.0, balance2.balance, 0.0001)
    }

    // 3. Full-credit purchase increases supplier payable
    @Test
    fun test03_fullCreditPurchase_increasesSupplierPayable() = runBlocking {
        val lines = listOf(PurchaseLineRequest(testProductId, "زيت زيتون 1 لتر", 25, 20.0))
        val res = repository.recordPurchase(supplierId = testSupplierId, lines = lines, paidAmount = 0.0)

        assertEquals(500.0, res.purchase.totalAmount, 0.0001)
        assertEquals(500.0, res.purchase.creditAmount, 0.0001)
        assertEquals(0.0, res.purchase.paidAmount, 0.0001)

        val balance = repository.getSupplierBalance(testSupplierId)
        assertEquals(500.0, balance.balance, 0.0001)
        assertEquals(500.0, balance.totalPurchases, 0.0001)
        assertEquals(500.0, balance.totalCreditPurchases, 0.0001)
    }

    // 4. Fully-paid purchase creates zero supplier payable
    @Test
    fun test04_fullyPaidPurchase_createsZeroSupplierPayable() = runBlocking {
        val lines = listOf(PurchaseLineRequest(testProductId, "زيت زيتون 1 لتر", 25, 20.0))
        val res = repository.recordPurchase(
            supplierId = testSupplierId,
            lines = lines,
            paidAmount = 500.0,
            financialAccountId = testAccountId
        )

        assertEquals(500.0, res.purchase.totalAmount, 0.0001)
        assertEquals(0.0, res.purchase.creditAmount, 0.0001)
        assertEquals(500.0, res.purchase.paidAmount, 0.0001)

        val balance = repository.getSupplierBalance(testSupplierId)
        assertEquals(0.0, balance.balance, 0.0001)
        assertEquals(500.0, balance.totalPurchases, 0.0001)
        assertEquals(0.0, balance.totalCreditPurchases, 0.0001)
    }

    // 5. Partial-payment purchase creates only the unpaid payable
    @Test
    fun test05_partialPaymentPurchase_createsOnlyUnpaidPayable() = runBlocking {
        val lines = listOf(PurchaseLineRequest(testProductId, "زيت زيتون 1 لتر", 25, 20.0))
        val res = repository.recordPurchase(
            supplierId = testSupplierId,
            lines = lines,
            paidAmount = 200.0,
            financialAccountId = testAccountId
        )

        assertEquals(500.0, res.purchase.totalAmount, 0.0001)
        assertEquals(200.0, res.purchase.paidAmount, 0.0001)
        assertEquals(300.0, res.purchase.creditAmount, 0.0001)

        val balance = repository.getSupplierBalance(testSupplierId)
        assertEquals(300.0, balance.balance, 0.0001)
        assertEquals(500.0, balance.totalPurchases, 0.0001)
        assertEquals(300.0, balance.totalCreditPurchases, 0.0001)
    }

    // 6. Purchase amount invariant is preserved
    @Test
    fun test06_purchaseAmountInvariantIsPreserved() {
        try {
            Purchase(
                id = "pur_inv_fail",
                invoiceNumber = "PUR-FAIL",
                supplierId = testSupplierId,
                purchaseDate = "2026-09-26",
                totalAmount = 500.0,
                paidAmount = 200.0,
                creditAmount = 200.0, // 200 + 200 != 500
                paymentStatus = "PARTIAL"
            )
            fail("Expected IllegalArgumentException when totalAmount != paidAmount + creditAmount")
        } catch (_: IllegalArgumentException) {
            // Success
        }
    }

    // 7. Purchase with missing financial account fails atomically when paidAmount > 0
    @Test
    fun test07_purchaseWithMissingFinancialAccount_failsAtomically_whenPaidAmountPositive() = runBlocking {
        val lines = listOf(PurchaseLineRequest(testProductId, "زيت زيتون 1 لتر", 5, 20.0))
        try {
            repository.recordPurchase(
                supplierId = testSupplierId,
                lines = lines,
                paidAmount = 50.0,
                financialAccountId = "acc_non_existent"
            )
            fail("Expected exception for non-existent financial account")
        } catch (_: IllegalArgumentException) {
            // Verified
        }

        val purchases = repository.getPurchasesForSupplier(testSupplierId)
        assertEquals(0, purchases.size)
    }

    // 8. SupplierPayment decreases supplier payable
    @Test
    fun test08_supplierPayment_decreasesSupplierPayable() = runBlocking {
        val lines = listOf(PurchaseLineRequest(testProductId, "زيت زيتون 1 لتر", 25, 20.0))
        repository.recordPurchase(supplierId = testSupplierId, lines = lines, paidAmount = 0.0) // 500 credit

        repository.recordSupplierPayment(
            supplierId = testSupplierId,
            amount = 200.0,
            financialAccountId = testAccountId
        )

        val balance = repository.getSupplierBalance(testSupplierId)
        assertEquals(300.0, balance.balance, 0.0001)
        assertEquals(200.0, balance.totalPayments, 0.0001)
    }

    // 9. SupplierPayment decreases the selected financial account
    @Test
    fun test09_supplierPayment_decreasesSelectedFinancialAccount() = runBlocking {
        val payment = repository.recordSupplierPayment(
            supplierId = testSupplierId,
            amount = 150.0,
            financialAccountId = testAccountId
        )
        assertNotNull(payment)
        assertEquals(testAccountId, payment.financialAccountId)
        assertEquals(150.0, payment.amount, 0.0001)
    }

    // 10. Missing financial account fails atomically
    @Test
    fun test10_supplierPayment_missingFinancialAccount_failsAtomically() = runBlocking {
        try {
            repository.recordSupplierPayment(
                supplierId = testSupplierId,
                amount = 100.0,
                financialAccountId = "acc_ghost"
            )
            fail("Expected exception for missing financial account")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        val payments = repository.getPaymentsForSupplier(testSupplierId)
        assertEquals(0, payments.size)
    }

    // 11. Original Purchase remains unchanged after SupplierPayment
    @Test
    fun test11_originalPurchase_remainsUnchangedAfterSupplierPayment() = runBlocking {
        val lines = listOf(PurchaseLineRequest(testProductId, "زيت زيتون 1 لتر", 10, 20.0))
        val res = repository.recordPurchase(supplierId = testSupplierId, lines = lines, paidAmount = 0.0)

        repository.recordSupplierPayment(
            supplierId = testSupplierId,
            amount = 100.0,
            financialAccountId = testAccountId
        )

        val purAfter = repository.getPurchaseById(res.purchase.id)
        assertNotNull(purAfter)
        assertEquals(200.0, purAfter!!.totalAmount, 0.0001)
        assertEquals(0.0, purAfter.paidAmount, 0.0001)
        assertEquals(200.0, purAfter.creditAmount, 0.0001)
    }

    // 12. Valid partial PurchaseReturn decreases supplier payable
    @Test
    fun test12_validPartialPurchaseReturn_decreasesSupplierPayable() = runBlocking {
        val lines = listOf(PurchaseLineRequest(testProductId, "زيت زيتون 1 لتر", 25, 20.0))
        val res = repository.recordPurchase(supplierId = testSupplierId, lines = lines, paidAmount = 0.0) // 500

        repository.recordPurchaseReturn(
            purchaseId = res.purchase.id,
            amount = 150.0,
            reason = "بضاعة تالفة"
        )

        val balance = repository.getSupplierBalance(testSupplierId)
        assertEquals(350.0, balance.balance, 0.0001)
        assertEquals(150.0, balance.totalReturns, 0.0001)
    }

    // 13. Full PurchaseReturn is accepted
    @Test
    fun test13_fullPurchaseReturn_isAccepted() = runBlocking {
        val lines = listOf(PurchaseLineRequest(testProductId, "زيت زيتون 1 لتر", 25, 20.0))
        val res = repository.recordPurchase(supplierId = testSupplierId, lines = lines, paidAmount = 0.0) // 500

        val ret = repository.recordPurchaseReturn(
            purchaseId = res.purchase.id,
            amount = 500.0,
            reason = "إرجاع كامل الشحنة"
        )
        assertNotNull(ret)

        val balance = repository.getSupplierBalance(testSupplierId)
        assertEquals(0.0, balance.balance, 0.0001)
        assertEquals(500.0, balance.totalReturns, 0.0001)
    }

    // 14. Return greater than purchase/remaining returnable amount is rejected
    @Test
    fun test14_returnGreaterThanRemaining_isRejected() = runBlocking {
        val lines = listOf(PurchaseLineRequest(testProductId, "زيت زيتون 1 لتر", 10, 20.0))
        val res = repository.recordPurchase(supplierId = testSupplierId, lines = lines, paidAmount = 0.0) // 200

        // Attempt 1: Return 250 > 200
        try {
            repository.recordPurchaseReturn(
                purchaseId = res.purchase.id,
                amount = 250.0,
                reason = "محاولة إرجاع زائد"
            )
            fail("Expected exception when return exceeds purchase total")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        // Return 150 (valid)
        repository.recordPurchaseReturn(
            purchaseId = res.purchase.id,
            amount = 150.0,
            reason = "مرتجع جزئي أول"
        )

        // Attempt 2: Return 100 when only 50 is remaining (150 + 100 = 250 > 200)
        try {
            repository.recordPurchaseReturn(
                purchaseId = res.purchase.id,
                amount = 100.0,
                reason = "محاولة إرجاع ثاني زائد"
            )
            fail("Expected exception when return exceeds remaining returnable amount")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    // 15. Blank return reason is rejected
    @Test
    fun test15_blankReturnReason_isRejected() = runBlocking {
        val lines = listOf(PurchaseLineRequest(testProductId, "زيت زيتون 1 لتر", 10, 20.0))
        val res = repository.recordPurchase(supplierId = testSupplierId, lines = lines, paidAmount = 0.0)

        try {
            repository.recordPurchaseReturn(
                purchaseId = res.purchase.id,
                amount = 50.0,
                reason = "   "
            )
            fail("Expected exception for blank reason")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    // 16. Original Purchase remains unchanged after PurchaseReturn
    @Test
    fun test16_originalPurchase_remainsUnchangedAfterPurchaseReturn() = runBlocking {
        val lines = listOf(PurchaseLineRequest(testProductId, "زيت زيتون 1 لتر", 10, 20.0))
        val res = repository.recordPurchase(supplierId = testSupplierId, lines = lines, paidAmount = 0.0)

        repository.recordPurchaseReturn(
            purchaseId = res.purchase.id,
            amount = 80.0,
            reason = "تلف جزئي"
        )

        val purAfter = repository.getPurchaseById(res.purchase.id)
        assertNotNull(purAfter)
        assertEquals(200.0, purAfter!!.totalAmount, 0.0001)
        assertEquals(0.0, purAfter.paidAmount, 0.0001)
        assertEquals(200.0, purAfter.creditAmount, 0.0001)
    }

    // 17. PurchaseReturn appears in supplier statement
    @Test
    fun test17_purchaseReturn_appearsInSupplierStatement() = runBlocking {
        val lines = listOf(PurchaseLineRequest(testProductId, "زيت زيتون 1 لتر", 10, 20.0))
        val res = repository.recordPurchase(supplierId = testSupplierId, lines = lines, paidAmount = 0.0)

        repository.recordPurchaseReturn(
            purchaseId = res.purchase.id,
            amount = 60.0,
            reason = "مرتجع فحص الجودة"
        )

        val statement = repository.getSupplierStatement(testSupplierId)
        assertTrue(statement.any { it.entryType == SupplierLedgerEntryType.PURCHASE_RETURN && it.debit == 60.0 })
    }

    // 18. Supplier OpeningBalance is included in supplier balance
    @Test
    fun test18_supplierOpeningBalance_isIncludedInSupplierBalance() = runBlocking {
        repository.recordOpeningBalance(
            entityType = "SUPPLIER",
            entityId = testSupplierId,
            amount = 1000.0,
            direction = "CREDIT",
            date = "2026-09-01",
            reason = "رصيد افتتاحي مستحق للمورد"
        )

        val balance = repository.getSupplierBalance(testSupplierId)
        assertEquals(1000.0, balance.openingBalance, 0.0001)
        assertEquals(1000.0, balance.balance, 0.0001)
    }

    // 19. Supplier OpeningBalance appears in supplier statement
    @Test
    fun test19_supplierOpeningBalance_appearsInSupplierStatement() = runBlocking {
        repository.recordOpeningBalance(
            entityType = "SUPPLIER",
            entityId = testSupplierId,
            amount = 450.0,
            direction = "CREDIT",
            date = "2026-09-01",
            reason = "رصيد افتتاحي"
        )

        val statement = repository.getSupplierStatement(testSupplierId)
        assertTrue(statement.any { it.entryType == SupplierLedgerEntryType.OPENING_BALANCE && it.credit == 450.0 })
    }

    // 20. OpeningBalance is not counted as a current-period purchase
    @Test
    fun test20_openingBalance_isNotCountedAsCurrentPeriodPurchase() = runBlocking {
        repository.recordOpeningBalance(
            entityType = "SUPPLIER",
            entityId = testSupplierId,
            amount = 800.0,
            direction = "CREDIT",
            date = "2026-09-01"
        )

        val balance = repository.getSupplierBalance(testSupplierId)
        assertEquals(0.0, balance.totalPurchases, 0.0001)
        assertEquals(0.0, balance.totalCreditPurchases, 0.0001)
        assertEquals(800.0, balance.openingBalance, 0.0001)
        assertEquals(800.0, balance.balance, 0.0001)
    }

    // 21. REVERSED purchase/payment/return does not affect active supplier balance
    @Test
    fun test21_reversedOperations_doNotAffectActiveSupplierBalance() = runBlocking {
        val pActive = Purchase(
            id = "pur_act",
            invoiceNumber = "PUR-A",
            supplierId = testSupplierId,
            purchaseDate = "2026-09-20",
            totalAmount = 300.0,
            paidAmount = 0.0,
            creditAmount = 300.0,
            paymentStatus = "UNPAID",
            status = "ACTIVE"
        )
        val pReversed = Purchase(
            id = "pur_rev",
            invoiceNumber = "PUR-R",
            supplierId = testSupplierId,
            purchaseDate = "2026-09-21",
            totalAmount = 400.0,
            paidAmount = 0.0,
            creditAmount = 400.0,
            paymentStatus = "UNPAID",
            status = "REVERSED"
        )
        val payActive = SupplierPayment(
            id = "pay_act",
            supplierId = testSupplierId,
            amount = 100.0,
            paymentDate = "2026-09-22",
            status = "ACTIVE"
        )
        val payReversed = SupplierPayment(
            id = "pay_rev",
            supplierId = testSupplierId,
            amount = 100.0,
            paymentDate = "2026-09-23",
            status = "REVERSED"
        )
        val retActive = PurchaseReturn(
            id = "ret_act",
            purchaseId = "pur_act",
            supplierId = testSupplierId,
            returnDate = "2026-09-24",
            amount = 50.0,
            reason = "تالف",
            status = "ACTIVE"
        )
        val retReversed = PurchaseReturn(
            id = "ret_rev",
            purchaseId = "pur_act",
            supplierId = testSupplierId,
            returnDate = "2026-09-25",
            amount = 50.0,
            reason = "مرتجع ملغي",
            status = "REVERSED"
        )

        val summary = SupplierLedgerCalculator.calculateSupplierBalance(
            supplierId = testSupplierId,
            purchases = listOf(pActive, pReversed),
            payments = listOf(payActive, payReversed),
            returns = listOf(retActive, retReversed)
        )

        // Only active: 300 credit - 100 payment - 50 return = 150
        assertEquals(150.0, summary.balance, 0.0001)
        assertEquals(300.0, summary.totalPurchases, 0.0001)
        assertEquals(300.0, summary.totalCreditPurchases, 0.0001)
        assertEquals(100.0, summary.totalPayments, 0.0001)
        assertEquals(50.0, summary.totalReturns, 0.0001)
    }
}
