package com.example.data.demo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.CentralAccountingEngine
import com.example.data.db.SmallStoreDatabase
import com.example.data.repository.StoreRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoDataSeederTest {

    private lateinit var context: Context
    private lateinit var database: SmallStoreDatabase
    private lateinit var repository: StoreRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Use an in-memory database to verify seeding rules and accounting invariants
        database = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StoreRepository.createForTesting(database)
        DemoConfig.demoModeOverride = true
    }

    @After
    fun tearDown() {
        DemoConfig.demoModeOverride = null
        database.close()
    }

    @Test
    fun testDemoModeIsolationConfig() {
        // Verify Demo Mode flag isolation
        assertTrue(DemoConfig.isDemoMode(context))
        assertEquals("smallstore_demo_database", DemoConfig.DEMO_DATABASE_NAME)
        assertEquals("smallstore_database", DemoConfig.PRODUCTION_DATABASE_NAME)
    }

    @Test
    fun testSeedDemoDataCreatesRequiredEntities() = runBlocking {
        val seeded = DemoDataSeeder.seedDemoDataIfEmpty(repository, database)
        assertTrue("Initial seeding should succeed", seeded)

        // 1. Verify Customers (at least 5 distinct customers)
        val customers = database.customerDao().getAllCustomersSync()
        assertTrue("Expected at least 5 customers, found ${customers.size}", customers.size >= 5)

        // 2. Verify Products (at least 12 distinct products)
        val products = database.productDao().getAllProductsSync()
        assertTrue("Expected at least 12 products, found ${products.size}", products.size >= 12)

        // 3. Verify Suppliers (at least 5 distinct suppliers)
        val suppliers = database.supplierDao().getAllSuppliersSync()
        assertTrue("Expected at least 5 suppliers, found ${suppliers.size}", suppliers.size >= 5)

        // 4. Verify Purchases & Purchase Lines
        val purchases = database.purchaseDao().getAllPurchasesSync()
        val purchaseLines = database.purchaseLineDao().getAllLinesSync()
        assertTrue("Expected multiple purchases", purchases.size >= 4)
        assertTrue("Expected multiple purchase lines", purchaseLines.size >= 8)

        // 5. Verify Sales & Sale Lines
        val sales = database.saleDao().getAllSalesSync()
        val saleLines = database.saleDao().getAllSaleLinesSync()
        assertTrue("Expected multiple sales", sales.size >= 4)
        assertTrue("Expected multiple sale lines", saleLines.size >= 8)

        // 6. Verify Customer Payments & Supplier Payments
        val customerPayments = database.customerPaymentDao().getAllPaymentsSync()
        val supplierPayments = database.supplierPaymentDao().getAllPaymentsSync()
        assertTrue("Expected customer payments", customerPayments.isNotEmpty())
        assertTrue("Expected supplier payments", supplierPayments.isNotEmpty())

        // 7. Verify Returns
        val purchaseReturns = database.purchaseReturnDao().getAllReturnsSync()
        val saleReturns = database.saleReturnDao().getAllReturnsSync()
        assertTrue("Expected purchase return", purchaseReturns.isNotEmpty())
        assertTrue("Expected sale return", saleReturns.isNotEmpty())
    }

    @Test
    fun testIdempotencyPreventsDuplicateSeeding() = runBlocking {
        val firstSeed = DemoDataSeeder.seedDemoDataIfEmpty(repository, database)
        assertTrue("First seed should return true", firstSeed)

        val customerCountBefore = database.customerDao().getCustomerCount()
        val productCountBefore = database.productDao().getProductCount()
        val supplierCountBefore = database.supplierDao().getAllSuppliersSync().size
        val saleCountBefore = database.saleDao().getSaleCount()

        val secondSeed = DemoDataSeeder.seedDemoDataIfEmpty(repository, database)
        assertFalse("Second seed should return false without altering records", secondSeed)

        val customerCountAfter = database.customerDao().getCustomerCount()
        val productCountAfter = database.productDao().getProductCount()
        val supplierCountAfter = database.supplierDao().getAllSuppliersSync().size
        val saleCountAfter = database.saleDao().getSaleCount()

        assertEquals("Customer count must remain unchanged", customerCountBefore, customerCountAfter)
        assertEquals("Product count must remain unchanged", productCountBefore, productCountAfter)
        assertEquals("Supplier count must remain unchanged", supplierCountBefore, supplierCountAfter)
        assertEquals("Sale count must remain unchanged", saleCountBefore, saleCountAfter)
    }

    @Test
    fun testAccountingInvariantsAndLedgers() = runBlocking {
        DemoDataSeeder.seedDemoDataIfEmpty(repository, database)

        // 1. Verify Sales Invariant: totalAmount == paidAmount + creditAmount
        val sales = database.saleDao().getAllSalesSync()
        for (sale in sales) {
            val delta = Math.abs(sale.totalAmount - (sale.paidAmount + sale.creditAmount))
            assertTrue("Sale ${sale.id} violates total = paid + credit", delta < 0.001)
        }

        // 2. Verify Purchases Invariant: totalAmount == paidAmount + creditAmount
        val purchases = database.purchaseDao().getAllPurchasesSync()
        for (purchase in purchases) {
            val delta = Math.abs(purchase.totalAmount - (purchase.paidAmount + purchase.creditAmount))
            assertTrue("Purchase ${purchase.id} violates total = paid + credit", delta < 0.001)
        }

        // 3. Verify Stock Movements: stock quantity on hand must be >= 0 for all products
        val movements = database.stockMovementDao().getAllMovementsSync()
        val products = database.productDao().getAllProductsSync()
        for (product in products) {
            val stock = CentralAccountingEngine.calculateProductStockFromMovements(
                productId = product.id,
                movements = movements,
                fallbackUnitCost = product.costPrice,
                productName = product.name
            )
            assertTrue(
                "Stock for product ${product.name} should not be negative: ${stock.quantityOnHand}",
                stock.quantityOnHand >= 0
            )
        }

        // 4. Verify Customer Ledger balances:
        // Customer 1: credit sale 190.0, balance = 190.0
        val cust1Balance = repository.getCustomerBalance("demo_cust_1").balance
        assertEquals("Customer 1 balance should be 190.0", 190.0, cust1Balance, 0.001)

        // Customer 2: mixed sale credit portion 115.0 - payment 50.0 = 65.0
        val cust2Balance = repository.getCustomerBalance("demo_cust_2").balance
        assertEquals("Customer 2 balance should be 65.0", 65.0, cust2Balance, 0.001)

        // Customer 3: cash sale (0 credit balance)
        val cust3Balance = repository.getCustomerBalance("demo_cust_3").balance
        assertEquals("Customer 3 balance should be 0.0", 0.0, cust3Balance, 0.001)

        // Customer 5: credit sale 78.0 - full settlement 78.0 = 0.0
        val cust5Balance = repository.getCustomerBalance("demo_cust_5").balance
        assertEquals("Customer 5 balance should be 0.0", 0.0, cust5Balance, 0.001)
    }

    @Test
    fun testResetDemoDataProvidesFreshDataset() = runBlocking {
        DemoDataSeeder.seedDemoDataIfEmpty(repository, database)
        val initialCustCount = database.customerDao().getCustomerCount()
        assertTrue(initialCustCount >= 5)

        // Execute reset
        DemoDataSeeder.resetDemoData(repository, database)
        val resetCustCount = database.customerDao().getCustomerCount()
        assertEquals("Reset should reseed fresh dataset", initialCustCount, resetCustCount)
    }
}
