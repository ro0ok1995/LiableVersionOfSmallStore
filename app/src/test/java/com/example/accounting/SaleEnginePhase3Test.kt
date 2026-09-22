package com.example.accounting

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.CustomerEntity
import com.example.data.db.ProductEntity
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.toModel
import com.example.data.repository.StoreRepository
import com.example.model.CartItem
import com.example.model.CustomerAccount
import com.example.model.OperationStatus
import com.example.model.PaymentStatus
import com.example.model.ProductItem
import com.example.model.SaleType
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.viewmodel.MainViewModel
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
import org.robolectric.shadows.ShadowLooper

/**
 * Phase 3 Sales Engine & Integration Verification Suite.
 *
 * Verifies:
 * 1. REGRESSION: Mixed sale (total 100, cash 60, credit 40) records 40 debt, NEVER full 100.
 * 2. Invariant: totalAmount = paidAmount + creditAmount at data-model level.
 * 3. Invariant: SaleLine freezes historical costPriceAtSale and validates subtotal.
 * 4. Human-readable sequential invoice numbers (INV-XXXXXX).
 * 5. Integration between Sale entity and CustomerLedgerCalculator.
 * 6. Additive legacy bridge mapping (toTransactionItem).
 * 7. Test 1: completeSettlement creates Sale in "sales" table with correct fields.
 * 8. Test 2: SaleLines are created in "sale_lines" table with correct historical cost.
 * 9. Test 3: NO synthetic payment transaction ("pay_<saleId>") is inserted into transactions.
 * 10. Test 4: Full cash sale does not duplicate cash (no 200 appearing from 100 sale).
 * 11. Test 5: Mixed sale regression (100 total / 60 cash / 40 credit -> 40 customer balance).
 * 12. Test 6: completeQuickPayment remains a payment and does NOT create a Sale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SaleEnginePhase3Test {

    private val testCustomerId = "cust_phase3_test"
    private lateinit var context: Context
    private lateinit var db: SmallStoreDatabase
    private lateinit var repository: StoreRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StoreRepository.createForTesting(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    // --- Domain / Pure Invariant Tests ---

    @Test
    fun regression_mixedSaleRecordsDebtOfUnpaidPortionOnly_notFullTotal() {
        val mixedTx = TransactionItem(
            id = "tx_regression_mixed",
            title = "فاتورة بيع مختلط",
            customerNameSnapshot = "عميل تجريبي",
            customerName = "عميل تجريبي",
            activityType = "شراء بالدين",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-22",
            relativeTime = "الآن",
            notes = "دفعة نقدية 60 ومتبقي 40",
            customerId = testCustomerId,
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED,
            paymentStatus = PaymentStatus.PARTIAL,
            operationStatus = OperationStatus.ACTIVE,
            paidAmount = 60.0,
            creditAmount = 40.0
        )

        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            testCustomerId,
            listOf(mixedTx)
        )

        // Customer receivable MUST be 40.0, never 100.0
        assertEquals(40.0, summary.balance, 0.0001)
        assertEquals(40.0, summary.totalCreditSales, 0.0001)
        assertEquals(0.0, summary.totalPayments, 0.0001)
    }

    @Test
    fun saleEntity_enforcesTotalAmountEqualsPaidPlusCredit() {
        // Valid sale: 100 = 60 + 40
        val validSale = Sale(
            id = "sale_1",
            invoiceNumber = "INV-000001",
            customerId = testCustomerId,
            saleType = "MIXED",
            totalAmount = 100.0,
            paidAmount = 60.0,
            creditAmount = 40.0,
            paymentStatus = "PARTIAL",
            transactionDate = "2026-09-22"
        )
        assertEquals(100.0, validSale.totalAmount, 0.0001)
        assertEquals(60.0, validSale.paidAmount, 0.0001)
        assertEquals(40.0, validSale.creditAmount, 0.0001)

        // Invalid sale: total 100 != 50 + 40 -> must throw IllegalArgumentException
        try {
            Sale(
                id = "sale_invalid",
                invoiceNumber = "INV-000002",
                customerId = testCustomerId,
                saleType = "MIXED",
                totalAmount = 100.0,
                paidAmount = 50.0,
                creditAmount = 40.0,
                paymentStatus = "PARTIAL",
                transactionDate = "2026-09-22"
            )
            fail("Expected IllegalArgumentException when totalAmount != paidAmount + creditAmount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Accounting invariant violated") == true)
        }
    }

    @Test
    fun saleEntity_threeModesAccountingVerification() {
        // 1. CASH: total 100, paid 100, credit 0 -> Customer receivable = 0
        val cashSale = Sale(
            id = "s_cash",
            invoiceNumber = "INV-000001",
            customerId = testCustomerId,
            saleType = "CASH",
            totalAmount = 100.0,
            paidAmount = 100.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-09-22"
        )
        val cashEntry = CustomerLedgerCalculator.saleToLedgerEntry(testCustomerId, cashSale)
        assertNotNull(cashEntry)
        assertEquals(0.0, cashEntry!!.debit, 0.0001)

        // 2. CREDIT: total 100, paid 0, credit 100 -> Customer receivable = 100
        val creditSale = Sale(
            id = "s_credit",
            invoiceNumber = "INV-000002",
            customerId = testCustomerId,
            saleType = "CREDIT",
            totalAmount = 100.0,
            paidAmount = 0.0,
            creditAmount = 100.0,
            paymentStatus = "UNPAID",
            transactionDate = "2026-09-22"
        )
        val creditEntry = CustomerLedgerCalculator.saleToLedgerEntry(testCustomerId, creditSale)
        assertNotNull(creditEntry)
        assertEquals(100.0, creditEntry!!.debit, 0.0001)

        // 3. MIXED: total 100, paid 60, credit 40 -> Customer receivable = 40
        val mixedSale = Sale(
            id = "s_mixed",
            invoiceNumber = "INV-000003",
            customerId = testCustomerId,
            saleType = "MIXED",
            totalAmount = 100.0,
            paidAmount = 60.0,
            creditAmount = 40.0,
            paymentStatus = "PARTIAL",
            transactionDate = "2026-09-22"
        )
        val mixedEntry = CustomerLedgerCalculator.saleToLedgerEntry(testCustomerId, mixedSale)
        assertNotNull(mixedEntry)
        assertEquals(40.0, mixedEntry!!.debit, 0.0001)

        // Combined summary across all 3 sales
        val summary = CustomerLedgerCalculator.calculateCustomerBalanceFromSales(
            testCustomerId,
            listOf(cashSale, creditSale, mixedSale)
        )
        assertEquals(140.0, summary.balance, 0.0001)
        assertEquals(140.0, summary.totalCreditSales, 0.0001)
    }

    @Test
    fun saleLine_freezesHistoricalCostAndValidatesSubtotal() {
        val line = SaleLine(
            id = "line_1",
            saleId = "sale_1",
            productId = "prod_1",
            productNameSnapshot = "أرز بسمتي 5 كجم",
            quantity = 3,
            unitPrice = 45.0,
            costPriceAtSale = 35.0,
            subtotal = 135.0
        )
        assertEquals(3, line.quantity)
        assertEquals(45.0, line.unitPrice, 0.0001)
        assertEquals(35.0, line.costPriceAtSale, 0.0001)
        assertEquals(135.0, line.subtotal, 0.0001)

        try {
            SaleLine(
                id = "line_bad",
                saleId = "sale_1",
                productId = "prod_1",
                productNameSnapshot = "أرز",
                quantity = 2,
                unitPrice = 50.0,
                costPriceAtSale = 35.0,
                subtotal = 80.0
            )
            fail("Expected IllegalArgumentException when subtotal != quantity * unitPrice")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("subtotal") == true)
        }
    }

    @Test
    fun invoiceNumberGenerator_formatsSequentially() {
        assertEquals("INV-000001", StoreRepository.generateNextInvoiceNumber(null))
        assertEquals("INV-000001", StoreRepository.generateNextInvoiceNumber(""))
        assertEquals("INV-000002", StoreRepository.generateNextInvoiceNumber("INV-000001"))
        assertEquals("INV-000043", StoreRepository.generateNextInvoiceNumber("INV-000042"))
        assertEquals("INV-001000", StoreRepository.generateNextInvoiceNumber("INV-000999"))
    }

    @Test
    fun sale_toTransactionItem_preservesBothTypedAndLegacyFields() {
        val sale = Sale(
            id = "sale_test",
            invoiceNumber = "INV-000005",
            customerId = testCustomerId,
            saleType = "MIXED",
            totalAmount = 200.0,
            paidAmount = 50.0,
            creditAmount = 150.0,
            paymentStatus = "PARTIAL",
            transactionDate = "2026-09-22"
        )

        val txItem = sale.toTransactionItem("سالم الدوسري", "ملاحظة خاصة")

        assertEquals("sale_test", txItem.id)
        assertEquals(testCustomerId, txItem.customerId)
        assertEquals("سالم الدوسري", txItem.customerNameSnapshot)
        assertEquals(200.0, txItem.amount, 0.0001)
        assertEquals(50.0, txItem.paidAmount, 0.0001)
        assertEquals(150.0, txItem.creditAmount, 0.0001)
        assertEquals("ملاحظة خاصة", txItem.notes)
        assertEquals(SaleType.MIXED, txItem.saleType)
        assertEquals(PaymentStatus.PARTIAL, txItem.paymentStatus)
        assertEquals(TransactionType.SALE, txItem.transactionType)
        assertTrue(txItem.isCredit)
    }

    private suspend fun waitForCondition(timeoutMs: Long = 3000, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            if (condition()) return
            kotlinx.coroutines.delay(20)
        }
        ShadowLooper.idleMainLooper()
    }

    // --- End-to-End Database & Repository Integration Tests ---

    @Test
    fun test1_and_test2_completeSettlementCreatesSaleAndSaleLinesInDatabase() = runBlocking {
        // Setup customer and product in database
        val customer = CustomerEntity(
            id = "cust_001",
            customerName = "عبد الله السالم",
            balance = 0.0,
            totalDebt = 0.0,
            phone = "0551234567",
            lastTransactionDate = "2026-09-20",
            hasRecentActivity = false
        )
        db.customerDao().insertCustomer(customer)

        val product1 = ProductItem(
            id = "prod_001",
            name = "حليب نادك 1 لتر",
            price = 6.0,
            costPrice = 4.5,
            category = "ألبان",
            unit = "حبة"
        )
        val product2 = ProductItem(
            id = "prod_002",
            name = "خبز توست",
            price = 5.0,
            costPrice = 3.5,
            category = "مخبوزات",
            unit = "كيس"
        )

        val viewModel = MainViewModel(
            application = ApplicationProvider.getApplicationContext(),
            repository = repository
        )
        ShadowLooper.idleMainLooper()
        viewModel.setPurchasesCustomer(customer.toModel())

        // Add items to cart: 2 * 6.0 = 12.0, 1 * 5.0 = 5.0 -> total = 17.0
        val cartItems = listOf(
            CartItem(product1, 2),
            CartItem(product2, 1)
        )
        viewModel.openPurchasesSettlement(cartItems)

        // Complete settlement: total 17.0, cash 10.0, debt 7.0
        viewModel.completeSettlement(cashAmount = 10.0, debtAmount = 7.0, notes = "فاتورة بقالة")
        waitForCondition { repository.getAllSalesSync().isNotEmpty() }

        // Allow coroutine execution
        val allSales = repository.getAllSalesSync()
        assertEquals("Exactly 1 Sale must exist in sales table", 1, allSales.size)

        val createdSale = allSales.first()
        assertEquals("INV-000001", createdSale.invoiceNumber)
        assertEquals("cust_001", createdSale.customerId)
        assertEquals(17.0, createdSale.totalAmount, 0.0001)
        assertEquals(10.0, createdSale.paidAmount, 0.0001)
        assertEquals(7.0, createdSale.creditAmount, 0.0001)
        assertEquals("MIXED", createdSale.saleType)
        assertEquals("PARTIAL", createdSale.paymentStatus)

        // Test 2: Verify SaleLines exist and have correct frozen cost
        val lines = repository.getSaleLines(createdSale.id)
        assertEquals("Expected exactly 2 SaleLine records", 2, lines.size)

        val line1 = lines.first { it.productId == "prod_001" }
        assertEquals("حليب نادك 1 لتر", line1.productNameSnapshot)
        assertEquals(2, line1.quantity)
        assertEquals(6.0, line1.unitPrice, 0.0001)
        assertEquals(4.5, line1.costPriceAtSale, 0.0001)
        assertEquals(12.0, line1.subtotal, 0.0001)

        val line2 = lines.first { it.productId == "prod_002" }
        assertEquals("خبز توست", line2.productNameSnapshot)
        assertEquals(1, line2.quantity)
        assertEquals(5.0, line2.unitPrice, 0.0001)
        assertEquals(3.5, line2.costPriceAtSale, 0.0001)
        assertEquals(5.0, line2.subtotal, 0.0001)
    }

    @Test
    fun test3_and_test4_noSyntheticPaymentTransactionAndNoDuplicateCash() = runBlocking {
        val customer = CustomerEntity(
            id = "cust_cash_test",
            customerName = "خالد المنصور",
            balance = 0.0,
            totalDebt = 0.0,
            phone = "0559998888",
            lastTransactionDate = "2026-09-21",
            hasRecentActivity = false
        )
        db.customerDao().insertCustomer(customer)

        val product = ProductItem(
            id = "prod_oil",
            name = "زيت ذرة 1.5 لتر",
            price = 25.0,
            costPrice = 18.0,
            category = "زيوت",
            unit = "حبة"
        )

        val viewModel = MainViewModel(
            application = ApplicationProvider.getApplicationContext(),
            repository = repository
        )
        ShadowLooper.idleMainLooper()
        viewModel.setPurchasesCustomer(customer.toModel())

        // 4 units * 25.0 = 100.0
        val cartItems = listOf(CartItem(product, 4))
        viewModel.openPurchasesSettlement(cartItems)

        // Case 1: Full Cash Sale (total 100, cash 100, debt 0)
        viewModel.completeSettlement(cashAmount = 100.0, debtAmount = 0.0, notes = "دفع نقدي كامل")
        waitForCondition { db.transactionDao().getAllTransactionsSync().isNotEmpty() }

        // Verify transactions table:
        val legacyTransactions = db.transactionDao().getAllTransactionsSync()

        // Test 3: Must NOT contain any synthetic "pay_<saleId>" transaction
        val syntheticPayTxs = legacyTransactions.filter { it.id.startsWith("pay_") }
        assertTrue("No synthetic pay_<saleId> transaction must be created", syntheticPayTxs.isEmpty())

        // Test 4: Exactly ONE transaction for the sale, total amount is 100, NOT 200
        assertEquals("Exactly one synchronized legacy transaction must exist", 1, legacyTransactions.size)
        val singleTx = legacyTransactions.first()
        assertEquals(100.0, singleTx.amount, 0.0001)
        assertEquals(100.0, singleTx.paidAmount, 0.0001)
        assertEquals(0.0, singleTx.creditAmount, 0.0001)

        val totalLegacySum = legacyTransactions.sumOf { it.amount }
        assertEquals("Total transaction sum must be 100.0, not duplicated to 200.0", 100.0, totalLegacySum, 0.0001)
    }

    @Test
    fun test5_mixedSaleRegression_produces40BalanceNot100() = runBlocking {
        val customer = CustomerEntity(
            id = "cust_mixed_40",
            customerName = "فيصل الدوسري",
            balance = 0.0,
            totalDebt = 0.0,
            phone = "0550001122",
            lastTransactionDate = "2026-09-22",
            hasRecentActivity = false
        )
        db.customerDao().insertCustomer(customer)

        val product = ProductItem(
            id = "prod_meat",
            name = "لحم ضأن طازج",
            price = 100.0,
            costPrice = 75.0,
            category = "لحوم",
            unit = "كجم"
        )

        val viewModel = MainViewModel(
            application = ApplicationProvider.getApplicationContext(),
            repository = repository
        )
        ShadowLooper.idleMainLooper()
        viewModel.setPurchasesCustomer(customer.toModel())

        viewModel.openPurchasesSettlement(listOf(CartItem(product, 1)))

        // Mixed sale: Total 100, Cash 60, Credit 40
        viewModel.completeSettlement(cashAmount = 60.0, debtAmount = 40.0, notes = "دفعة 60 ومتبقي 40")
        waitForCondition { repository.getSalesByCustomerIdSync("cust_mixed_40").isNotEmpty() }

        // 1. Verify Sale-based ledger calculation
        val sales = repository.getSalesByCustomerIdSync("cust_mixed_40")
        assertEquals(1, sales.size)
        val sale = sales.first()
        assertEquals(100.0, sale.totalAmount, 0.0001)
        assertEquals(60.0, sale.paidAmount, 0.0001)
        assertEquals(40.0, sale.creditAmount, 0.0001)

        val saleBalanceSummary = CustomerLedgerCalculator.calculateCustomerBalanceFromSales("cust_mixed_40", sales)
        assertEquals("Customer balance from Sales MUST be exactly 40.0", 40.0, saleBalanceSummary.balance, 0.0001)

        // 2. Verify legacy synchronized transaction calculation agrees
        val txs = db.transactionDao().getTransactionsByCustomerIdSync("cust_mixed_40")
        assertEquals(1, txs.size)
        val txItem = txs.first()
        val txBalanceSummary = CustomerLedgerCalculator.calculateCustomerBalance(
            "cust_mixed_40",
            listOf(txItem.toModel())
        )
        assertEquals("Customer balance from legacy synchronized Tx MUST be exactly 40.0", 40.0, txBalanceSummary.balance, 0.0001)
    }

    @Test
    fun test6_completeQuickPayment_remainsPaymentAndDoesNotCreateSale() = runBlocking {
        val customer = CustomerEntity(
            id = "cust_quick_pay",
            customerName = "يوسف الحربي",
            balance = 200.0,
            totalDebt = 200.0,
            phone = "0553334444",
            lastTransactionDate = "2026-09-20",
            hasRecentActivity = true
        )
        db.customerDao().insertCustomer(customer)

        val viewModel = MainViewModel(
            application = ApplicationProvider.getApplicationContext(),
            repository = repository
        )
        ShadowLooper.idleMainLooper()

        // Prepare quick payment of 80.0
        val customerAccount = customer.toModel()
        viewModel.openQuickPayment(customerAccount)
        viewModel.setQuickPaymentAmount("80.0")
        viewModel.setQuickPaymentNotes("سداد جزئي على الحساب")

        // Execute quick payment
        viewModel.completeQuickPayment()
        waitForCondition { db.transactionDao().getAllTransactionsSync().isNotEmpty() }

        // Verify: sales table must remain completely empty!
        val allSales = repository.getAllSalesSync()
        assertTrue("Quick payment must NOT create any Sale in sales table", allSales.isEmpty())

        // Verify: transactions table contains the payment transaction
        val transactions = db.transactionDao().getAllTransactionsSync()
        assertEquals(1, transactions.size)
        val payTx = transactions.first()
        assertEquals("تسديد", payTx.activityType)
        assertEquals(80.0, payTx.amount, 0.0001)
        assertEquals(80.0, payTx.paidAmount, 0.0001)
        assertEquals(0.0, payTx.creditAmount, 0.0001)
        assertEquals("cust_quick_pay", payTx.customerId)
    }
}
