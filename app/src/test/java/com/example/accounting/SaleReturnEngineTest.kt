package com.example.accounting

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.CustomerEntity
import com.example.data.db.FinancialAccount
import com.example.data.db.PaymentMethod
import com.example.data.db.ProductEntity
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.toTransactionItem
import com.example.data.repository.StoreRepository
import com.example.model.OperationStatus
import com.example.model.PaymentStatus
import com.example.model.RefundRequest
import com.example.model.SaleReturnLineRequest
import com.example.model.SaleType
import com.example.model.TransactionType
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SaleReturnEngineTest {

    private lateinit var context: Context
    private lateinit var db: SmallStoreDatabase
    private lateinit var repository: StoreRepository

    private val testCustomerId = "cust_ret_test_01"
    private val cashAccountId = "acc_cash_01"
    private val bankAccountId = "acc_bank_01"
    private val cashMethodId = "pm_cash_01"
    private val bankMethodId = "pm_bank_01"
    private val prodA = "prod_01"
    private val prodB = "prod_02"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StoreRepository.createForTesting(db)

        // Seed customer
        db.customerDao().insertCustomer(
            CustomerEntity(
                id = testCustomerId,
                customerName = "عميل تجربة المرتجعات",
                balance = 0.0,
                totalDebt = 0.0,
                phone = "0501234567",
                lastTransactionDate = "2026-03-01",
                hasRecentActivity = false
            )
        )

        // Seed financial accounts
        db.financialAccountDao().insertAccount(
            FinancialAccount(
                id = cashAccountId,
                name = "الصندوق الرئيسي",
                type = "CASH"
            )
        )
        db.financialAccountDao().insertAccount(
            FinancialAccount(
                id = bankAccountId,
                name = "حساب بنك الرياض",
                type = "BANK"
            )
        )

        // Seed payment methods
        db.paymentMethodDao().insertPaymentMethod(
            PaymentMethod(
                id = cashMethodId,
                name = "نقدي",
                type = "CASH"
            )
        )
        db.paymentMethodDao().insertPaymentMethod(
            PaymentMethod(
                id = bankMethodId,
                name = "شبكة / بنك",
                type = "CARD"
            )
        )

        // Seed products
        db.productDao().insertProduct(
            ProductEntity(
                id = prodA,
                name = "أرز بسمتي 5 كجم",
                price = 40.0,
                costPrice = 25.0,
                category = "مواد غذائية",
                unit = "كيس"
            )
        )
        db.productDao().insertProduct(
            ProductEntity(
                id = prodB,
                name = "زيت ذرة 1.5 لتر",
                price = 20.0,
                costPrice = 12.0,
                category = "زيوت",
                unit = "حبة"
            )
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    /**
     * Helper to create a Sale with SaleLines in repository.
     */
    private suspend fun createTestSale(
        saleId: String,
        invoiceNumber: String,
        customerId: String?,
        saleType: SaleType,
        totalAmount: Double,
        paidAmount: Double,
        lines: List<SaleLine>,
        date: String = "2026-03-10"
    ): Sale {
        val creditAmount = totalAmount - paidAmount
        val paymentStatus = when {
            creditAmount <= 0.001 -> PaymentStatus.PAID
            paidAmount <= 0.001 -> PaymentStatus.UNPAID
            else -> PaymentStatus.PARTIAL
        }
        val sale = Sale(
            id = saleId,
            invoiceNumber = invoiceNumber,
            customerId = customerId,
            saleType = saleType.name,
            totalAmount = totalAmount,
            paidAmount = paidAmount,
            creditAmount = creditAmount,
            paymentStatus = paymentStatus.name,
            transactionDate = date,
            status = "ACTIVE"
        )
        return repository.createSale(sale, lines)
    }

    /**
     * Test 1 & 11: Full sale return & Original Sale remains unchanged.
     */
    @Test
    fun testFullSaleReturn() = runBlocking {
        val saleLine = SaleLine(
            id = "sl_01",
            saleId = "sale_full_01",
            productId = prodA,
            productNameSnapshot = "أرز بسمتي",
            quantity = 10,
            unitPrice = 40.0,
            costPriceAtSale = 25.0,
            subtotal = 400.0
        )
        val sale = createTestSale(
            saleId = "sale_full_01",
            invoiceNumber = "INV-000001",
            customerId = testCustomerId,
            saleType = SaleType.CREDIT,
            totalAmount = 400.0,
            paidAmount = 0.0,
            lines = listOf(saleLine)
        )

        val balanceBefore = repository.getCustomerBalance(testCustomerId)
        assertEquals(400.0, balanceBefore.balance, 0.001)

        val returnResult = repository.recordSaleReturn(
            saleId = sale.id,
            returnLines = listOf(SaleReturnLineRequest(saleLineId = saleLine.id, quantity = 10)),
            reason = "إرجاع كامل الفاتورة - منتج غير مطابق",
            returnDate = "2026-03-11"
        )

        assertEquals(400.0, returnResult.saleReturn.amount, 0.001)
        assertEquals(1, returnResult.lines.size)
        assertEquals(10, returnResult.lines[0].quantity)
        assertEquals("ACTIVE", returnResult.saleReturn.status)

        // Verify original Sale remains unchanged
        val originalSaleAfter = repository.getSaleById(sale.id)
        assertNotNull(originalSaleAfter)
        assertEquals(400.0, originalSaleAfter!!.totalAmount, 0.001)
        assertEquals("ACTIVE", originalSaleAfter.status)
        val originalLinesAfter = repository.getSaleLines(sale.id)
        assertEquals(10, originalLinesAfter[0].quantity)

        // Customer receivable is reduced back to 0.0
        val balanceAfter = repository.getCustomerBalance(testCustomerId)
        assertEquals(0.0, balanceAfter.balance, 0.001)
    }

    /**
     * Test 2: Partial sale return.
     */
    @Test
    fun testPartialSaleReturn() = runBlocking {
        val saleLine = SaleLine(
            id = "sl_02",
            saleId = "sale_part_01",
            productId = prodA,
            productNameSnapshot = "أرز بسمتي",
            quantity = 10,
            unitPrice = 40.0,
            costPriceAtSale = 25.0,
            subtotal = 400.0
        )
        val sale = createTestSale(
            saleId = "sale_part_01",
            invoiceNumber = "INV-000002",
            customerId = testCustomerId,
            saleType = SaleType.CREDIT,
            totalAmount = 400.0,
            paidAmount = 0.0,
            lines = listOf(saleLine)
        )

        val returnResult = repository.recordSaleReturn(
            saleId = sale.id,
            returnLines = listOf(SaleReturnLineRequest(saleLineId = saleLine.id, quantity = 3)),
            reason = "إرجاع جزئي 3 أكياس",
            returnDate = "2026-03-11"
        )

        assertEquals(120.0, returnResult.saleReturn.amount, 0.001)
        assertEquals(3, returnResult.lines[0].quantity)

        val returnableMap = repository.getRemainingReturnableQuantities(sale.id)
        assertEquals(7, returnableMap[saleLine.id])

        val balanceAfter = repository.getCustomerBalance(testCustomerId)
        assertEquals(280.0, balanceAfter.balance, 0.001)
    }

    /**
     * Test 3 & 12: Cannot return more than sold quantity or remaining returnable.
     */
    @Test
    fun testCannotReturnMoreThanSoldQuantity() = runBlocking {
        val saleLine = SaleLine(
            id = "sl_03",
            saleId = "sale_quant_01",
            productId = prodA,
            productNameSnapshot = "أرز بسمتي",
            quantity = 5,
            unitPrice = 40.0,
            costPriceAtSale = 25.0,
            subtotal = 200.0
        )
        val sale = createTestSale(
            saleId = "sale_quant_01",
            invoiceNumber = "INV-000003",
            customerId = testCustomerId,
            saleType = SaleType.CREDIT,
            totalAmount = 200.0,
            paidAmount = 0.0,
            lines = listOf(saleLine)
        )

        // Try returning 6 when sold is 5 -> MUST fail
        try {
            repository.recordSaleReturn(
                saleId = sale.id,
                returnLines = listOf(SaleReturnLineRequest(saleLineId = saleLine.id, quantity = 6)),
                reason = "محاولة إرجاع كمية أكبر من المباعة"
            )
            fail("Expected exception when returning more than sold quantity")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("remaining returnable"))
        }

        // Return 3 legitimately
        repository.recordSaleReturn(
            saleId = sale.id,
            returnLines = listOf(SaleReturnLineRequest(saleLineId = saleLine.id, quantity = 3)),
            reason = "إرجاع 3 حبات"
        )

        // Now try returning 3 more when only 2 remain -> MUST fail
        try {
            repository.recordSaleReturn(
                saleId = sale.id,
                returnLines = listOf(SaleReturnLineRequest(saleLineId = saleLine.id, quantity = 3)),
                reason = "محاولة إرجاع أكثر من المتبقي"
            )
            fail("Expected exception when exceeding remaining returnable quantity")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("remaining returnable"))
        }
    }

    /**
     * Test 4: Return reduces net sales correctly in FinancialReportCalculator.
     */
    @Test
    fun testReturnReducesNetSalesCorrectly() = runBlocking {
        val saleLine = SaleLine(
            id = "sl_04",
            saleId = "sale_rep_01",
            productId = prodA,
            productNameSnapshot = "أرز بسمتي",
            quantity = 5,
            unitPrice = 40.0,
            costPriceAtSale = 25.0,
            subtotal = 200.0
        )
        val sale = createTestSale(
            saleId = "sale_rep_01",
            invoiceNumber = "INV-000004",
            customerId = testCustomerId,
            saleType = SaleType.CASH,
            totalAmount = 200.0,
            paidAmount = 200.0,
            lines = listOf(saleLine)
        )

        val returnResult = repository.recordSaleReturn(
            saleId = sale.id,
            returnLines = listOf(SaleReturnLineRequest(saleLineId = saleLine.id, quantity = 2)),
            reason = "إرجاع 2 وحدات",
            returnDate = "2026-03-11"
        )

        val saleTx = sale.toTransactionItem()
        val returnTx = returnResult.saleReturn.toTransactionItem(invoiceNumber = sale.invoiceNumber)

        val totals = FinancialReportCalculator.calculate(listOf(saleTx, returnTx))

        assertEquals(200.0, totals.totalSales, 0.001)
        assertEquals(80.0, totals.saleReturns, 0.001)
        assertEquals(120.0, totals.netSales, 0.001)
    }

    /**
     * Test 5: Return corrects customer receivable correctly in CustomerLedgerCalculator.
     */
    @Test
    fun testReturnCorrectsCustomerReceivableCorrectly() = runBlocking {
        val saleLine = SaleLine(
            id = "sl_05",
            saleId = "sale_led_01",
            productId = prodB,
            productNameSnapshot = "زيت ذرة",
            quantity = 15,
            unitPrice = 20.0,
            costPriceAtSale = 12.0,
            subtotal = 300.0
        )
        val sale = createTestSale(
            saleId = "sale_led_01",
            invoiceNumber = "INV-000005",
            customerId = testCustomerId,
            saleType = SaleType.CREDIT,
            totalAmount = 300.0,
            paidAmount = 0.0,
            lines = listOf(saleLine)
        )

        val returnResult = repository.recordSaleReturn(
            saleId = sale.id,
            returnLines = listOf(SaleReturnLineRequest(saleLineId = saleLine.id, quantity = 5)),
            reason = "مرتجع 5 حبات زيت",
            returnDate = "2026-03-12"
        )

        val balance = repository.getCustomerBalance(testCustomerId)
        assertEquals(200.0, balance.balance, 0.001)
        assertEquals(100.0, balance.totalReturns, 0.001)
        assertEquals(300.0, balance.totalCreditSales, 0.001)

        val saleTx = sale.toTransactionItem()
        val returnTx = returnResult.saleReturn.toTransactionItem(invoiceNumber = sale.invoiceNumber)
        val entries = CustomerLedgerCalculator.toLedgerEntries(testCustomerId, listOf(saleTx, returnTx))

        assertEquals(2, entries.size)
        assertEquals(TransactionType.SALE, entries[0].transactionType)
        assertEquals(300.0, entries[0].debit, 0.001)
        assertEquals(TransactionType.SALE_RETURN, entries[1].transactionType)
        assertEquals(100.0, entries[1].credit, 0.001)
    }

    /**
     * Test 6: Return reverses the appropriate COGS effect.
     */
    @Test
    fun testReturnReversesCOGS() = runBlocking {
        val saleLine = SaleLine(
            id = "sl_06",
            saleId = "sale_cogs_01",
            productId = prodA,
            productNameSnapshot = "أرز بسمتي",
            quantity = 4,
            unitPrice = 40.0,
            costPriceAtSale = 25.0,
            subtotal = 160.0
        )
        val sale = createTestSale(
            saleId = "sale_cogs_01",
            invoiceNumber = "INV-000006",
            customerId = testCustomerId,
            saleType = SaleType.CASH,
            totalAmount = 160.0,
            paidAmount = 160.0,
            lines = listOf(saleLine)
        )

        val returnResult = repository.recordSaleReturn(
            saleId = sale.id,
            returnLines = listOf(SaleReturnLineRequest(saleLineId = saleLine.id, quantity = 2)),
            reason = "إرجاع وحدتين"
        )

        // 2 units * costPrice 25 = 50.0 COGS reversed
        assertEquals(50.0, returnResult.cogsReversed, 0.001)
        // Gross profit correction = 2 * (40 - 25) = 30.0
        assertEquals(30.0, returnResult.grossProfitCorrection, 0.001)

        val returnLine = returnResult.lines.first()
        assertEquals(25.0, returnLine.costPriceAtReturn, 0.001)
        assertEquals(50.0, returnLine.cogsReversed, 0.001)
    }

    /**
     * Test 7: Returned inventory quantity is recorded when supported.
     */
    @Test
    fun testReturnedInventoryRecorded() = runBlocking {
        val lineA = SaleLine(
            id = "sl_inv_01",
            saleId = "sale_inv_01",
            productId = prodA,
            productNameSnapshot = "أرز بسمتي",
            quantity = 5,
            unitPrice = 40.0,
            costPriceAtSale = 25.0,
            subtotal = 200.0
        )
        val lineB = SaleLine(
            id = "sl_inv_02",
            saleId = "sale_inv_01",
            productId = prodB,
            productNameSnapshot = "زيت ذرة",
            quantity = 8,
            unitPrice = 20.0,
            costPriceAtSale = 12.0,
            subtotal = 160.0
        )
        val sale = createTestSale(
            saleId = "sale_inv_01",
            invoiceNumber = "INV-000007",
            customerId = testCustomerId,
            saleType = SaleType.CREDIT,
            totalAmount = 360.0,
            paidAmount = 0.0,
            lines = listOf(lineA, lineB)
        )

        val returnResult = repository.recordSaleReturn(
            saleId = sale.id,
            returnLines = listOf(
                SaleReturnLineRequest(saleLineId = lineA.id, quantity = 2),
                SaleReturnLineRequest(saleLineId = lineB.id, quantity = 4)
            ),
            reason = "مرتجع أصناف متعددة"
        )

        val returnLines = repository.getReturnLines(returnResult.saleReturn.id)
        assertEquals(2, returnLines.size)

        val retA = returnLines.first { it.productId == prodA }
        val retB = returnLines.first { it.productId == prodB }

        assertEquals(2, retA.quantity)
        assertEquals(4, retB.quantity)
        assertEquals(25.0, retA.costPriceAtReturn, 0.001)
        assertEquals(12.0, retB.costPriceAtReturn, 0.001)
    }

    /**
     * Test 8 & 9 & 10: Refund is stored separately from Sale, can use different financial account,
     * and affects the correct financial account.
     */
    @Test
    fun testRefundSeparationAndAccountSelection() = runBlocking {
        val saleLine = SaleLine(
            id = "sl_ref_01",
            saleId = "sale_ref_01",
            productId = prodA,
            productNameSnapshot = "أرز بسمتي",
            quantity = 2,
            unitPrice = 50.0,
            costPriceAtSale = 30.0,
            subtotal = 100.0
        )
        val sale = createTestSale(
            saleId = "sale_ref_01",
            invoiceNumber = "INV-000008",
            customerId = testCustomerId,
            saleType = SaleType.CASH,
            totalAmount = 100.0,
            paidAmount = 100.0,
            lines = listOf(saleLine)
        )

        // Return 1 unit = 50.0, and issue Refund from BANK account
        val returnResult = repository.recordSaleReturn(
            saleId = sale.id,
            returnLines = listOf(SaleReturnLineRequest(saleLineId = saleLine.id, quantity = 1)),
            reason = "إرجاع حبة واسترداد بالبنك",
            refundRequest = RefundRequest(
                amount = 50.0,
                financialAccountId = bankAccountId,
                paymentMethodId = bankMethodId,
                reason = "استرداد عبر البنك"
            )
        )

        val refund = returnResult.refund
        assertNotNull(refund)
        assertEquals(50.0, refund!!.amount, 0.001)
        assertEquals(bankAccountId, refund.financialAccountId)
        assertEquals(bankMethodId, refund.paymentMethodId)
        assertEquals(sale.id, refund.saleId)
        assertEquals(returnResult.saleReturn.id, refund.saleReturnId)

        // Verify Refund is stored separately
        val storedRefunds = repository.getRefundsForSale(sale.id)
        assertEquals(1, storedRefunds.size)
        assertEquals(refund.id, storedRefunds.first().id)

        // Original sale amount & paidAmount remain untouched!
        val unchangedSale = repository.getSaleById(sale.id)
        assertEquals(100.0, unchangedSale!!.totalAmount, 0.001)
        assertEquals(100.0, unchangedSale.paidAmount, 0.001)
    }

    /**
     * Test 13: Invalid return data is rejected.
     */
    @Test
    fun testInvalidReturnDataRejected() = runBlocking {
        val saleLine = SaleLine(
            id = "sl_inv_err",
            saleId = "sale_inv_err",
            productId = prodA,
            productNameSnapshot = "أرز بسمتي",
            quantity = 5,
            unitPrice = 40.0,
            costPriceAtSale = 25.0,
            subtotal = 200.0
        )
        val sale = createTestSale(
            saleId = "sale_inv_err",
            invoiceNumber = "INV-000009",
            customerId = testCustomerId,
            saleType = SaleType.CASH,
            totalAmount = 200.0,
            paidAmount = 200.0,
            lines = listOf(saleLine)
        )

        // Blank sale ID
        try {
            repository.recordSaleReturn(saleId = "", returnLines = listOf(SaleReturnLineRequest(saleLine.id, 1)), reason = "Test")
            fail("Expected failure for blank sale ID")
        } catch (e: IllegalArgumentException) {}

        // Blank reason
        try {
            repository.recordSaleReturn(saleId = sale.id, returnLines = listOf(SaleReturnLineRequest(saleLine.id, 1)), reason = "")
            fail("Expected failure for blank reason")
        } catch (e: IllegalArgumentException) {}

        // Non-positive quantity
        try {
            repository.recordSaleReturn(saleId = sale.id, returnLines = listOf(SaleReturnLineRequest(saleLine.id, 0)), reason = "Test")
            fail("Expected failure for zero quantity")
        } catch (e: IllegalArgumentException) {}

        // Empty lines
        try {
            repository.recordSaleReturn(saleId = sale.id, returnLines = emptyList(), reason = "Test")
            fail("Expected failure for empty lines")
        } catch (e: IllegalArgumentException) {}

        // Excessive refund amount (exceeding sale paidAmount)
        try {
            repository.recordSaleReturn(
                saleId = sale.id,
                returnLines = listOf(SaleReturnLineRequest(saleLine.id, 1)),
                reason = "Test",
                refundRequest = RefundRequest(amount = 250.0) // Sale only paid 200.0
            )
            fail("Expected failure for refund exceeding paid amount")
        } catch (e: IllegalArgumentException) {}
    }

    /**
     * Test 14: Failed multi-step return is atomic.
     */
    @Test
    fun testFailedReturnIsAtomic() = runBlocking {
        val saleLine = SaleLine(
            id = "sl_atom_01",
            saleId = "sale_atom_01",
            productId = prodA,
            productNameSnapshot = "أرز بسمتي",
            quantity = 5,
            unitPrice = 40.0,
            costPriceAtSale = 25.0,
            subtotal = 200.0
        )
        val sale = createTestSale(
            saleId = "sale_atom_01",
            invoiceNumber = "INV-000010",
            customerId = testCustomerId,
            saleType = SaleType.CREDIT,
            totalAmount = 200.0,
            paidAmount = 0.0,
            lines = listOf(saleLine)
        )

        val returnsBefore = repository.getReturnsForSale(sale.id)
        assertEquals(0, returnsBefore.size)

        // Try invalid refund for credit sale with 0.0 paid -> validation rejects before commit
        try {
            repository.recordSaleReturn(
                saleId = sale.id,
                returnLines = listOf(SaleReturnLineRequest(saleLine.id, 2)),
                reason = "محاولة استرداد نقدي لبيع آجل بدون دفع",
                refundRequest = RefundRequest(amount = 80.0)
            )
            fail("Should fail because credit sale has 0 paidAmount")
        } catch (e: IllegalArgumentException) {}

        // Confirm database is completely clean
        val returnsAfter = repository.getReturnsForSale(sale.id)
        assertEquals(0, returnsAfter.size)
        val balance = repository.getCustomerBalance(testCustomerId)
        assertEquals(200.0, balance.balance, 0.001)
    }
}
