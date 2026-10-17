package com.example.accounting

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.Expense
import com.example.data.db.ExpenseCategory
import com.example.data.db.FinancialAccount
import com.example.data.db.PaymentMethod
import com.example.data.db.SmallStoreDatabase
import com.example.data.repository.StoreRepository
import com.example.data.db.toModel
import com.example.model.TransactionType
import com.example.model.typedTransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Focused Phase 10 Test Suite for Expense Core.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ExpensePhase10Test {

    private lateinit var context: Context
    private lateinit var db: SmallStoreDatabase
    private lateinit var repository: StoreRepository

    private val testAccountId = "acc_cash"
    private val testPaymentMethodId = "pm_cash"
    private val testCategoryId = "cat_utilities"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StoreRepository.createForTesting(db)

        repository.createFinancialAccount(
            FinancialAccount(
                id = testAccountId,
                name = "Cash",
                type = "CASH",
                isActive = true
            )
        )

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

    // 1. Valid ExpenseCategory can be created/persisted.
    @Test
    fun test01_validExpenseCategory_canBeCreatedAndPersisted() = runBlocking {
        val category = ExpenseCategory(
            id = testCategoryId,
            name = "Utilities",
            description = "Electricity and water"
        )
        val created = repository.insertExpenseCategory(category)
        assertNotNull(created)
        assertEquals(testCategoryId, created.id)
        assertEquals("Utilities", created.name)

        val retrieved = repository.getExpenseCategoryById(testCategoryId)
        assertNotNull(retrieved)
        assertEquals("Utilities", retrieved?.name)
    }

    // 2. Valid Expense can be created with:
    //    category, positive amount, financial account, payment method, date, description
    @Test
    fun test02_validExpense_canBeCreatedWithAllFields() = runBlocking {
        repository.insertExpenseCategory(
            ExpenseCategory(
                id = testCategoryId,
                name = "Utilities",
                description = "Electricity and water"
            )
        )

        val expense = repository.recordExpense(
            categoryId = testCategoryId,
            amount = 150.0,
            financialAccountId = testAccountId,
            paymentMethodId = testPaymentMethodId,
            date = "2026-09-26",
            description = "Electricity bill"
        )

        assertNotNull(expense)
        assertEquals(testCategoryId, expense.categoryId)
        assertEquals(150.0, expense.amount, 0.0001)
        assertEquals(testAccountId, expense.financialAccountId)
        assertEquals(testPaymentMethodId, expense.paymentMethodId)
        assertEquals("2026-09-26", expense.date)
        assertEquals("Electricity bill", expense.description)
        assertEquals("ACTIVE", expense.status)

        val retrieved = repository.getExpenseById(expense.id)
        assertNotNull(retrieved)
        assertEquals(150.0, retrieved?.amount ?: 0.0, 0.0001)
    }

    // 3. Expense with amount <= 0 is rejected.
    @Test
    fun test03_expenseWithAmountZeroOrNegative_isRejected() = runBlocking {
        repository.insertExpenseCategory(
            ExpenseCategory(
                id = testCategoryId,
                name = "Utilities",
                description = "Electricity and water"
            )
        )

        try {
            repository.recordExpense(
                categoryId = testCategoryId,
                amount = 0.0,
                financialAccountId = testAccountId,
                paymentMethodId = testPaymentMethodId,
                date = "2026-09-26",
                description = "Zero amount test"
            )
            fail("Expected IllegalArgumentException for zero amount")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        try {
            repository.recordExpense(
                categoryId = testCategoryId,
                amount = -50.0,
                financialAccountId = testAccountId,
                paymentMethodId = testPaymentMethodId,
                date = "2026-09-26",
                description = "Negative amount test"
            )
            fail("Expected IllegalArgumentException for negative amount")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    // 4. Creating an Expense of 100 against a financial account reduces that account by exactly 100.
    @Test
    fun test04_expenseOf100_reducesFinancialAccountBy100() = runBlocking {
        repository.insertExpenseCategory(
            ExpenseCategory(
                id = testCategoryId,
                name = "Rent",
                description = "Store rent"
            )
        )

        val balanceBefore = repository.getFinancialAccountBalance(testAccountId)
        assertEquals(0.0, balanceBefore, 0.0001)

        val expense = repository.recordExpense(
            categoryId = testCategoryId,
            amount = 100.0,
            financialAccountId = testAccountId,
            paymentMethodId = testPaymentMethodId,
            date = "2026-09-26",
            description = "Monthly store rent"
        )
        assertNotNull(expense)
        assertEquals(100.0, expense.amount, 0.0001)

        val balanceAfter = repository.getFinancialAccountBalance(testAccountId)
        assertEquals(balanceBefore - 100.0, balanceAfter, 0.0001)
        assertEquals(-100.0, balanceAfter, 0.0001)
    }

    // 5. The Expense is recorded as an Expense transaction and is NOT treated as:
    //    - Sale
    //    - Purchase
    //    - Inventory movement
    @Test
    fun test05_expenseIsRecordedAsExpense_andNotSaleOrPurchaseOrInventory() = runBlocking {
        repository.insertExpenseCategory(
            ExpenseCategory(
                id = testCategoryId,
                name = "Maintenance",
                description = "Store equipment repair"
            )
        )

        val expense = repository.recordExpense(
            categoryId = testCategoryId,
            amount = 75.0,
            financialAccountId = testAccountId,
            paymentMethodId = testPaymentMethodId,
            date = "2026-09-26",
            description = "AC repair"
        )
        assertNotNull(expense)

        // 1. Recorded as Expense transaction in transaction activity
        val allTx = db.transactionDao().getAllTransactionsSync()
        val expenseTx = allTx.find { it.id == expense.id }
        assertNotNull("Expense should be recorded in transactions", expenseTx)
        val model = expenseTx!!.toModel()
        assertEquals(TransactionType.EXPENSE, model.typedTransactionType)

        // 2. NOT treated as a Sale
        val allSales = db.saleDao().getAllSalesSync()
        assertTrue("Expense must not create any Sale record", allSales.isEmpty())
        assertNotEquals(TransactionType.SALE, model.typedTransactionType)

        // 3. NOT treated as a Purchase
        val allPurchases = db.purchaseDao().getAllPurchasesSync()
        assertTrue("Expense must not create any Purchase record", allPurchases.isEmpty())
        assertNotEquals(TransactionType.PURCHASE, model.typedTransactionType)

        // 4. NOT treated as Inventory movement
        val purchaseLines = db.purchaseLineDao().getLinesByPurchaseId(expense.id)
        assertTrue("Expense must not create purchase inventory lines", purchaseLines.isEmpty())
        val productCount = db.productDao().getAllProductsSync().size
        assertEquals("Expense must not create or modify product inventory", 0, productCount)
    }
}
