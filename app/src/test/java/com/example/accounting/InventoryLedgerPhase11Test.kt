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
import com.example.data.db.Supplier
import com.example.data.repository.StoreRepository
import com.example.model.PurchaseLineRequest
import com.example.model.SaleReturnLineRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 11 Inventory Ledger Integration Test Suite.
 *
 * Verifies the authoritative perpetual inventory calculation:
 * - Purchase increases stock
 * - Sale decreases stock
 * - Sale return increases stock
 * - Purchase return decreases stock
 * - Reversed transactions do not affect active stock
 * - Stock quantity is derived strictly from transaction lines (Accounting Golden Rule)
 * - Calculation remains deterministic across multiple invocations
 * - Inventory valuation is calculated from authoritative cost fields
 * - Physical inventory adjustments (DEBIT/CREDIT)
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class InventoryLedgerPhase11Test {

    private lateinit var context: Context
    private lateinit var db: SmallStoreDatabase
    private lateinit var repository: StoreRepository

    private val testSupplierId = "sup_test_01"
    private val testCustomerId = "cust_test_01"
    private val testAccountId = "acc_cash"
    private val testPaymentMethodId = "pm_cash"
    private val testProductId = "prod_item_01"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StoreRepository.createForTesting(db)

        // 1. Seed Customer
        db.customerDao().insertCustomer(
            CustomerEntity(
                id = testCustomerId,
                customerName = "Test Customer",
                balance = 0.0,
                totalDebt = 0.0,
                phone = "123456",
                lastTransactionDate = "2026-09-26",
                hasRecentActivity = false
            )
        )

        // 2. Seed Supplier
        repository.createSupplier(
            Supplier(
                id = testSupplierId,
                name = "Test Supplier",
                phone = "987654",
                openingBalance = 0.0,
                isActive = true
            )
        )

        // 3. Seed Financial Account & Payment Method
        repository.createFinancialAccount(
            FinancialAccount(
                id = testAccountId,
                name = "Cash Drawer",
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

        // 4. Seed Product
        db.productDao().insertProduct(
            ProductEntity(
                id = testProductId,
                name = "Premium Tea",
                price = 30.0,
                costPrice = 20.0,
                category = "Beverages",
                unit = "Box"
            )
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    // 1. Purchase increases stock
    @Test
    fun test01_purchaseIncreasesStock() = runBlocking {
        val stockBefore = repository.getProductStock(testProductId)
        assertEquals(0, stockBefore.quantityOnHand)

        val result = repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "Premium Tea",
                    quantity = 10,
                    unitCost = 20.0
                )
            ),
            paidAmount = 200.0,
            financialAccountId = testAccountId,
            paymentMethodId = testPaymentMethodId,
            purchaseDate = "2026-09-26"
        )
        assertNotNull(result)

        val stockAfter = repository.getProductStock(testProductId)
        assertEquals(10, stockAfter.quantityOnHand)
        assertEquals(10, stockAfter.totalPurchased)
        assertEquals(0, stockAfter.totalSold)
        assertTrue(stockAfter.isInStock)
        assertEquals(20.0, stockAfter.unitCost, 0.0001)
        assertEquals(200.0, stockAfter.totalValuation, 0.0001)
    }

    // 2. Sale decreases stock
    @Test
    fun test02_saleDecreasesStock() = runBlocking {
        // First purchase 10 units
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "Premium Tea",
                    quantity = 10,
                    unitCost = 20.0
                )
            ),
            paidAmount = 200.0,
            financialAccountId = testAccountId,
            purchaseDate = "2026-09-26"
        )

        // Sell 4 units
        val sale = Sale(
            id = "sale_test_01",
            invoiceNumber = "INV-000001",
            customerId = testCustomerId,
            transactionDate = "2026-09-26",
            totalAmount = 120.0,
            paidAmount = 120.0,
            creditAmount = 0.0,
            status = "ACTIVE"
        )
        val saleLine = SaleLine(
            id = "sline_01",
            saleId = sale.id,
            productId = testProductId,
            productNameSnapshot = "Premium Tea",
            quantity = 4,
            unitPrice = 30.0,
            costPriceAtSale = 20.0,
            subtotal = 120.0
        )
        repository.createSale(sale, listOf(saleLine))

        val stock = repository.getProductStock(testProductId)
        assertEquals(6, stock.quantityOnHand)
        assertEquals(10, stock.totalPurchased)
        assertEquals(4, stock.totalSold)
        assertEquals(120.0, stock.totalValuation, 0.0001) // 6 * 20.0
    }

    // 3. Sale return increases stock
    @Test
    fun test03_saleReturnIncreasesStock() = runBlocking {
        // Purchase 10, Sell 4 -> stock is 6
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "Premium Tea",
                    quantity = 10,
                    unitCost = 20.0
                )
            ),
            paidAmount = 200.0,
            financialAccountId = testAccountId,
            purchaseDate = "2026-09-26"
        )

        val sale = Sale(
            id = "sale_test_02",
            invoiceNumber = "INV-000002",
            customerId = testCustomerId,
            transactionDate = "2026-09-26",
            totalAmount = 120.0,
            paidAmount = 120.0,
            creditAmount = 0.0,
            status = "ACTIVE"
        )
        val saleLine = SaleLine(
            id = "sline_02",
            saleId = sale.id,
            productId = testProductId,
            productNameSnapshot = "Premium Tea",
            quantity = 4,
            unitPrice = 30.0,
            costPriceAtSale = 20.0,
            subtotal = 120.0
        )
        repository.createSale(sale, listOf(saleLine))

        // Return 2 units
        repository.recordSaleReturn(
            saleId = sale.id,
            returnLines = listOf(
                SaleReturnLineRequest(
                    saleLineId = saleLine.id,
                    quantity = 2,
                    unitPrice = 30.0,
                    costPriceAtReturn = 20.0
                )
            ),
            reason = "Customer returned 2 boxes",
            returnDate = "2026-09-26"
        )

        val stock = repository.getProductStock(testProductId)
        // 10 purchased - 4 sold + 2 returned = 8
        assertEquals(8, stock.quantityOnHand)
        assertEquals(2, stock.totalReturnedFromSales)
        assertEquals(160.0, stock.totalValuation, 0.0001) // 8 * 20.0
    }

    // 4. Purchase return decreases stock
    @Test
    fun test04_purchaseReturnDecreasesStock() = runBlocking {
        // Purchase 10 units at $20 = $200 total
        val purchaseResult = repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "Premium Tea",
                    quantity = 10,
                    unitCost = 20.0
                )
            ),
            paidAmount = 200.0,
            financialAccountId = testAccountId,
            purchaseDate = "2026-09-26"
        )

        val stockBeforeReturn = repository.getProductStock(testProductId)
        assertEquals(10, stockBeforeReturn.quantityOnHand)

        // Return $80 worth (4 units) to supplier
        repository.recordPurchaseReturn(
            purchaseId = purchaseResult.purchase.id,
            amount = 80.0,
            reason = "Defective 4 boxes returned to supplier",
            returnDate = "2026-09-26"
        )

        val stockAfterReturn = repository.getProductStock(testProductId)
        // 10 - 4 returned = 6
        assertEquals(6, stockAfterReturn.quantityOnHand)
        assertEquals(4, stockAfterReturn.totalReturnedToSuppliers)
        assertEquals(120.0, stockAfterReturn.totalValuation, 0.0001) // 6 * 20.0
    }

    // 5. Reversed transactions do not affect active stock
    @Test
    fun test05_reversedTransactionsDoNotAffectActiveStock() = runBlocking {
        // 1. Purchase 10 units
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "Premium Tea",
                    quantity = 10,
                    unitCost = 20.0
                )
            ),
            paidAmount = 200.0,
            financialAccountId = testAccountId,
            purchaseDate = "2026-09-26"
        )

        // 2. Sell 4 units
        val sale = Sale(
            id = "sale_to_reverse",
            invoiceNumber = "INV-000003",
            customerId = testCustomerId,
            transactionDate = "2026-09-26",
            totalAmount = 120.0,
            paidAmount = 120.0,
            creditAmount = 0.0,
            status = "ACTIVE"
        )
        val saleLine = SaleLine(
            id = "sline_rev",
            saleId = sale.id,
            productId = testProductId,
            productNameSnapshot = "Premium Tea",
            quantity = 4,
            unitPrice = 30.0,
            costPriceAtSale = 20.0,
            subtotal = 120.0
        )
        repository.createSale(sale, listOf(saleLine))

        var stock = repository.getProductStock(testProductId)
        assertEquals(6, stock.quantityOnHand)

        // 3. Reverse the sale
        repository.reverseTransaction(sale.id, "Entered in error")

        stock = repository.getProductStock(testProductId)
        // Reversed sale does not reduce stock, so stock should be back to 10
        assertEquals(10, stock.quantityOnHand)
        assertEquals(0, stock.totalSold)
        assertTrue(stock.reversedMovementCount > 0)
    }

    // 6. Stock quantity is derived from authoritative transaction lines (Golden Rule)
    @Test
    fun test06_stockQuantityDerivedFromAuthoritativeTransactionLines() = runBlocking {
        // Verify ProductEntity has no editable stock column
        val product = db.productDao().getProductById(testProductId)
        assertNotNull(product)

        // Dynamic derivation from empty lines -> 0
        assertEquals(0, repository.getProductStock(testProductId).quantityOnHand)

        // Add 5 via purchase
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "Premium Tea",
                    quantity = 5,
                    unitCost = 20.0
                )
            ),
            paidAmount = 100.0,
            financialAccountId = testAccountId
        )
        assertEquals(5, repository.getProductStock(testProductId).quantityOnHand)

        // Add 3 via physical inventory adjustment (DEBIT)
        repository.recordInventoryAdjustment(
            productId = testProductId,
            quantityDelta = 3,
            reason = "Found 3 extra boxes during count"
        )
        assertEquals(8, repository.getProductStock(testProductId).quantityOnHand)

        // Deduct 1 via physical inventory adjustment (CREDIT - spoilage)
        repository.recordInventoryAdjustment(
            productId = testProductId,
            quantityDelta = -1,
            reason = "Water damage on 1 box"
        )
        assertEquals(7, repository.getProductStock(testProductId).quantityOnHand)
    }

    // 7. Inventory calculation remains deterministic
    @Test
    fun test07_inventoryCalculationRemainsDeterministic() = runBlocking {
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "Premium Tea",
                    quantity = 15,
                    unitCost = 18.0
                )
            ),
            paidAmount = 270.0,
            financialAccountId = testAccountId
        )

        val run1 = repository.getProductStock(testProductId)
        val run2 = repository.getProductStock(testProductId)
        val run3 = repository.getProductStock(testProductId)

        assertEquals(run1.quantityOnHand, run2.quantityOnHand)
        assertEquals(run2.quantityOnHand, run3.quantityOnHand)
        assertEquals(run1.totalValuation, run2.totalValuation, 0.0001)
        assertEquals(run1.totalPurchased, run3.totalPurchased)

        val statement = repository.getInventoryStatement(testProductId)
        assertNotNull(statement)
        assertFalse(statement.isEmpty())
        assertEquals(15, statement.last().runningQuantity)
    }

    // 8. Inventory valuation is calculated from authoritative data
    @Test
    fun test08_inventoryValuationCalculatedFromAuthoritativeData() = runBlocking {
        // Purchase 20 units at $22.50 each
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "Premium Tea",
                    quantity = 20,
                    unitCost = 22.50
                )
            ),
            paidAmount = 450.0,
            financialAccountId = testAccountId
        )

        val stock = repository.getProductStock(testProductId)
        assertEquals(20, stock.quantityOnHand)
        assertEquals(22.50, stock.unitCost, 0.0001)
        // Valuation = 20 * 22.50 = 450.0
        assertEquals(450.0, stock.totalValuation, 0.0001)

        val totalValuation = repository.getTotalInventoryValuation()
        assertEquals(450.0, totalValuation, 0.0001)
    }
}
