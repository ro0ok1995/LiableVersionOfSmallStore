package com.example.accounting

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.CustomerEntity
import com.example.data.db.FinancialAccount
import com.example.data.db.ProductEntity
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.StockMovementEntity
import com.example.data.db.Supplier
import com.example.data.repository.StoreRepository
import com.example.model.PurchaseLineRequest
import com.example.model.SaleReturnLineRequest
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

/**
 * Phase 11 Inventory Ledger Verification Test Suite.
 *
 * Verifies:
 * A) Purchase: purchase 5 units => PURCHASE_IN movement, stock increases by 5
 * B) Sale: sell 2 units => SALE_OUT movement, stock decreases by 2
 * C) Sale return: return 1 unit => SALE_RETURN_IN movement, stock increases by 1
 * D) Adjustment: increase stock by 3 => ADJUSTMENT_IN, stock increases by 3
 * E) Adjustment: decrease stock by 2 => ADJUSTMENT_OUT, stock decreases by 2
 * Damage: write off 1 unit => DAMAGE_OUT, stock decreases by 1
 * F) Movement history: stock balance explained from movement ledger and running quantities
 * G) Atomicity: transaction rollback ensures no partial or dangling inventory movements
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class InventoryLedgerPhase11Test {

    private lateinit var context: Context
    private lateinit var db: SmallStoreDatabase
    private lateinit var repository: StoreRepository

    private val testProductId = "prod_inv_001"
    private val testSupplierId = "sup_inv_001"
    private val testCustomerId = "cust_inv_001"
    private val testAccountId = "acc_cash_01"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        repository = StoreRepository.createForTesting(db)

        // Seed product
        db.productDao().insertProduct(
            ProductEntity(
                id = testProductId,
                name = "زيت زيتون بكر",
                price = 30.0,
                costPrice = 20.0,
                category = "زيوت",
                unit = "حبة"
            )
        )

        // Seed supplier
        db.supplierDao().insertSupplier(
            Supplier(
                id = testSupplierId,
                name = "مورد الزيوت الوطنية",
                phone = "0500000001",
                address = "الرياض",
                notes = "مورد معتمد"
            )
        )

        // Seed customer
        db.customerDao().insertCustomer(
            CustomerEntity(
                id = testCustomerId,
                customerName = "عميل المتجر الأول",
                balance = 0.0,
                totalDebt = 0.0,
                phone = "0555555555",
                lastTransactionDate = "2026-03-01",
                hasRecentActivity = false
            )
        )

        // Seed financial account
        db.financialAccountDao().insertAccount(
            FinancialAccount(
                id = testAccountId,
                name = "الصندوق النقدي",
                type = "CASH"
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Requirement A:
     * Purchase: purchase 5 units => one corresponding PURCHASE_IN movement => stock increases by 5
     */
    @Test
    fun testPurchaseCreatesPurchaseInMovementAndIncreasesStock() = runBlocking {
        val initialStock = repository.getProductStock(testProductId)
        assertEquals(0, initialStock.quantityOnHand)

        val result = repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "زيت زيتون بكر",
                    quantity = 5,
                    unitCost = 20.0
                )
            ),
            paidAmount = 100.0,
            paymentMethodId = "pm_cash",
            financialAccountId = testAccountId,
            notes = "شراء 5 حبات زيت"
        )
        assertNotNull(result.purchase)

        val stockAfterPurchase = repository.getProductStock(testProductId)
        assertEquals(5, stockAfterPurchase.quantityOnHand)
        assertEquals(5, stockAfterPurchase.totalPurchased)

        val movements = repository.getStockMovements(testProductId)
        assertEquals(1, movements.size)
        val mov = movements.first()
        assertEquals(InventoryMovementType.PURCHASE_IN, mov.movementType)
        assertEquals(5, mov.quantityIn)
        assertEquals(0, mov.quantityOut)
        assertEquals(5, mov.quantity)
        assertEquals(20.0, mov.unitCost, 0.001)
        assertEquals("PURCHASE", mov.referenceType)
        assertEquals(result.purchase.id, mov.referenceId)
        assertEquals(5, mov.runningQuantity)
    }

    /**
     * Requirement B:
     * Sale: sell 2 units => SALE_OUT movement => stock decreases by 2
     */
    @Test
    fun testSaleCreatesSaleOutMovementAndDecreasesStock() = runBlocking {
        // Step 1: Purchase 5 units
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "زيت زيتون بكر",
                    quantity = 5,
                    unitCost = 20.0
                )
            ),
            paidAmount = 100.0,
            financialAccountId = testAccountId,
            purchaseDate = "2026-03-01"
        )

        // Step 2: Sell 2 units
        val saleId = "sale_inv_001"
        val saleLineId = "sale_line_001"
        val sale = Sale(
            id = saleId,
            customerId = testCustomerId,
            invoiceNumber = "INV-000101",
            saleType = "CASH",
            totalAmount = 60.0,
            paidAmount = 60.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-03-10",
            status = "ACTIVE"
        )
        val lines = listOf(
            SaleLine(
                id = saleLineId,
                saleId = saleId,
                productId = testProductId,
                productNameSnapshot = "زيت زيتون بكر",
                quantity = 2,
                unitPrice = 30.0,
                costPriceAtSale = 20.0,
                subtotal = 60.0
            )
        )
        repository.createSale(sale, lines)

        val stockAfterSale = repository.getProductStock(testProductId)
        assertEquals(3, stockAfterSale.quantityOnHand)
        assertEquals(5, stockAfterSale.totalPurchased)
        assertEquals(2, stockAfterSale.totalSold)

        val movements = repository.getStockMovements(testProductId)
        assertEquals(2, movements.size)

        val saleMov = movements.find { it.movementType == InventoryMovementType.SALE_OUT }
        assertNotNull(saleMov)
        assertEquals(2, saleMov!!.quantityOut)
        assertEquals(0, saleMov.quantityIn)
        assertEquals(2, saleMov.quantity)
        assertEquals("SALE", saleMov.referenceType)
        assertEquals(saleId, saleMov.referenceId)
        assertEquals(3, saleMov.runningQuantity)
    }

    /**
     * Requirement C:
     * Sale return: return 1 unit => SALE_RETURN_IN movement => stock increases by 1
     */
    @Test
    fun testSaleReturnCreatesSaleReturnInMovementAndIncreasesStock() = runBlocking {
        // Purchase 5, Sell 2
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "زيت زيتون بكر",
                    quantity = 5,
                    unitCost = 20.0
                )
            ),
            paidAmount = 100.0,
            financialAccountId = testAccountId,
            purchaseDate = "2026-03-01"
        )
        val saleId = "sale_inv_002"
        val saleLineId = "sale_line_002"
        val sale = Sale(
            id = saleId,
            customerId = testCustomerId,
            invoiceNumber = "INV-000102",
            saleType = "CASH",
            totalAmount = 60.0,
            paidAmount = 60.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-03-10",
            status = "ACTIVE"
        )
        val lines = listOf(
            SaleLine(
                id = saleLineId,
                saleId = saleId,
                productId = testProductId,
                productNameSnapshot = "زيت زيتون بكر",
                quantity = 2,
                unitPrice = 30.0,
                costPriceAtSale = 20.0,
                subtotal = 60.0
            )
        )
        repository.createSale(sale, lines)

        // Return 1 unit
        val returnResult = repository.recordSaleReturn(
            saleId = saleId,
            returnLines = listOf(
                SaleReturnLineRequest(
                    saleLineId = saleLineId,
                    quantity = 1
                )
            ),
            reason = "استرجاع حبة غير مستخدمة"
        )
        assertNotNull(returnResult.saleReturn)

        val stockAfterReturn = repository.getProductStock(testProductId)
        assertEquals(4, stockAfterReturn.quantityOnHand)
        assertEquals(1, stockAfterReturn.totalReturnedFromSales)

        val movements = repository.getStockMovements(testProductId)
        assertEquals(3, movements.size)

        val returnMov = movements.find { it.movementType == InventoryMovementType.SALE_RETURN_IN }
        assertNotNull(returnMov)
        assertEquals(1, returnMov!!.quantityIn)
        assertEquals(0, returnMov.quantityOut)
        assertEquals(1, returnMov.quantity)
        assertEquals("SALE_RETURN", returnMov.referenceType)
        assertEquals(4, returnMov.runningQuantity)
    }

    /**
     * Requirement D:
     * Adjustment: increase stock by 3 => ADJUSTMENT_IN => stock increases by 3
     */
    @Test
    fun testAdjustmentIncreasesStockWithAdjustmentIn() = runBlocking {
        val initialStock = repository.getProductStock(testProductId)
        assertEquals(0, initialStock.quantityOnHand)

        val adj = repository.recordInventoryAdjustment(
            productId = testProductId,
            quantityDelta = 3,
            reason = "جرد فعلي زيادة",
            date = "2026-03-12"
        )
        assertNotNull(adj)

        val stockAfterAdj = repository.getProductStock(testProductId)
        assertEquals(3, stockAfterAdj.quantityOnHand)
        assertEquals(3, stockAfterAdj.totalAdjustments)

        val movements = repository.getStockMovements(testProductId)
        assertEquals(1, movements.size)
        val mov = movements.first()
        assertEquals(InventoryMovementType.ADJUSTMENT_IN, mov.movementType)
        assertEquals(3, mov.quantityIn)
        assertEquals(0, mov.quantityOut)
        assertEquals(3, mov.quantity)
        assertEquals("ADJUSTMENT", mov.referenceType)
        assertEquals(adj.id, mov.referenceId)
        assertEquals(3, mov.runningQuantity)
    }

    /**
     * Requirement E:
     * Adjustment: decrease stock by 2 => ADJUSTMENT_OUT => stock decreases by 2
     */
    @Test
    fun testAdjustmentDecreasesStockWithAdjustmentOut() = runBlocking {
        // First establish 5 units via purchase
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "زيت زيتون بكر",
                    quantity = 5,
                    unitCost = 20.0
                )
            ),
            paidAmount = 100.0,
            financialAccountId = testAccountId,
            purchaseDate = "2026-03-01"
        )

        // Decrease stock by 2 via adjustment
        val adj = repository.recordInventoryAdjustment(
            productId = testProductId,
            quantityDelta = -2,
            reason = "عجز جرد مستودع",
            date = "2026-03-13"
        )
        assertNotNull(adj)

        val stockAfterAdj = repository.getProductStock(testProductId)
        assertEquals(3, stockAfterAdj.quantityOnHand)
        assertEquals(-2, stockAfterAdj.totalAdjustments)

        val movements = repository.getStockMovements(testProductId)
        assertEquals(2, movements.size)

        val mov = movements.find { it.movementType == InventoryMovementType.ADJUSTMENT_OUT }
        assertNotNull(mov)
        assertEquals(2, mov!!.quantityOut)
        assertEquals(0, mov.quantityIn)
        assertEquals(2, mov.quantity)
        assertEquals("ADJUSTMENT", mov.referenceType)
        assertEquals(adj.id, mov.referenceId)
        assertEquals(3, mov.runningQuantity)
    }

    /**
     * Requirement 4:
     * Inventory damage/loss creates DAMAGE_OUT movement.
     */
    @Test
    fun testInventoryDamageCreatesDamageOutMovement() = runBlocking {
        // Purchase 5 units
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "زيت زيتون بكر",
                    quantity = 5,
                    unitCost = 20.0
                )
            ),
            paidAmount = 100.0,
            financialAccountId = testAccountId,
            purchaseDate = "2026-03-01"
        )

        // Record damage of 1 unit
        val damageAdj = repository.recordInventoryDamage(
            productId = testProductId,
            quantity = 1,
            reason = "بضاعة تالفة نتيجة كسر",
            date = "2026-03-14"
        )
        assertNotNull(damageAdj)

        val stockAfterDamage = repository.getProductStock(testProductId)
        assertEquals(4, stockAfterDamage.quantityOnHand)

        val movements = repository.getStockMovements(testProductId)
        assertEquals(2, movements.size)

        val damageMov = movements.find { it.movementType == InventoryMovementType.DAMAGE_OUT }
        assertNotNull(damageMov)
        assertEquals(1, damageMov!!.quantityOut)
        assertEquals(0, damageMov.quantityIn)
        assertEquals(1, damageMov.quantity)
        assertEquals("DAMAGE", damageMov.referenceType)
        assertEquals(damageAdj.id, damageMov.referenceId)
        assertEquals(4, damageMov.runningQuantity)
    }

    /**
     * Requirement F:
     * Stock balance must be explainable from the movement ledger.
     */
    @Test
    fun testMovementHistoryExplainsStockBalance() = runBlocking {
        // 1. Purchase 10 units
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "زيت زيتون بكر",
                    quantity = 10,
                    unitCost = 20.0
                )
            ),
            paidAmount = 200.0,
            financialAccountId = testAccountId,
            purchaseDate = "2026-03-01"
        )

        // 2. Sell 4 units
        val saleId = "sale_inv_audit"
        val saleLineId = "sale_line_audit"
        repository.createSale(
            Sale(
                id = saleId,
                customerId = testCustomerId,
                invoiceNumber = "INV-AUDIT-01",
                saleType = "CASH",
                totalAmount = 120.0,
                paidAmount = 120.0,
                creditAmount = 0.0,
                paymentStatus = "PAID",
                transactionDate = "2026-03-15",
                status = "ACTIVE"
            ),
            listOf(
                SaleLine(
                    id = saleLineId,
                    saleId = saleId,
                    productId = testProductId,
                    productNameSnapshot = "زيت زيتون بكر",
                    quantity = 4,
                    unitPrice = 30.0,
                    costPriceAtSale = 20.0,
                    subtotal = 120.0
                )
            )
        )

        // 3. Customer returns 1 unit
        repository.recordSaleReturn(
            saleId = saleId,
            returnLines = listOf(SaleReturnLineRequest(saleLineId = saleLineId, quantity = 1)),
            reason = "مرتجع فحص"
        )

        // 4. Damage 1 unit
        repository.recordInventoryDamage(
            productId = testProductId,
            quantity = 1,
            reason = "تلف أثناء التخزين"
        )

        // 5. Positive adjustment of 2 units
        repository.recordInventoryAdjustment(
            productId = testProductId,
            quantityDelta = 2,
            reason = "تسوية جرد بزيادة"
        )

        // Expected final quantity on hand: 10 - 4 + 1 - 1 + 2 = 8
        val summary = repository.getProductStock(testProductId)
        assertEquals(8, summary.quantityOnHand)
        assertEquals(10, summary.totalPurchased)
        assertEquals(4, summary.totalSold)
        assertEquals(1, summary.totalReturnedFromSales)
        assertEquals(1, summary.totalAdjustments) // +2 adj - 1 damage = +1

        val movements = repository.getStockMovements(testProductId)
        assertEquals(5, movements.size)

        // Verify running quantities explain the balance at each chronological step
        var runningCalculated = 0
        for (m in movements) {
            runningCalculated += (m.quantityIn - m.quantityOut)
            assertEquals(runningCalculated, m.runningQuantity)
        }
        assertEquals(8, runningCalculated)
    }

    /**
     * Requirement G:
     * Atomicity: if a transaction affecting financial/accounting state and inventory fails,
     * there must not be a partially saved inventory movement.
     */
    @Test
    fun testTransactionFailureRollsBackInventoryMovements() = runBlocking {
        // Initial stock is 0
        assertEquals(0, repository.getProductStock(testProductId).quantityOnHand)

        // Attempt purchase with invalid paid amount on non-existent account -> should throw and rollback
        try {
            repository.recordPurchase(
                supplierId = testSupplierId,
                lines = listOf(
                    PurchaseLineRequest(
                        productId = testProductId,
                        productNameSnapshot = "زيت زيتون بكر",
                        quantity = 5,
                        unitCost = 20.0
                    )
                ),
                paidAmount = 100.0,
                financialAccountId = "non_existent_account_xyz"
            )
            fail("Expected IllegalArgumentException for invalid financial account")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Verify NO inventory movement or stock increase occurred
        val stockAfterFailed = repository.getProductStock(testProductId)
        assertEquals(0, stockAfterFailed.quantityOnHand)
        val movements = repository.getStockMovements(testProductId)
        assertTrue("Inventory movements must be completely rolled back", movements.isEmpty())
    }

    /**
     * Requirement TEST D:
     * Purchase 5, Purchase Return 2 => Expected stock = 3
     */
    @Test
    fun testPurchaseReturnDecreasesStock() = runBlocking {
        // Purchase 5 units at 20.0 (total 100.0)
        val purchaseResult = repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "زيت زيتون بكر",
                    quantity = 5,
                    unitCost = 20.0
                )
            ),
            paidAmount = 100.0,
            financialAccountId = testAccountId,
            purchaseDate = "2026-03-01"
        )
        assertEquals(5, repository.getProductStock(testProductId).quantityOnHand)

        // Return 2 units (amount = 40.0)
        val pr = repository.recordPurchaseReturn(
            purchaseId = purchaseResult.purchase.id,
            amount = 40.0,
            reason = "إرجاع وحدتين للمورد"
        )
        assertNotNull(pr)

        val stockAfterReturn = repository.getProductStock(testProductId)
        assertEquals(3, stockAfterReturn.quantityOnHand)
        assertEquals(2, stockAfterReturn.totalReturnedToSuppliers)

        val movements = db.stockMovementDao().getMovementsByProductIdSync(testProductId)
        val prMov = movements.find { it.movementType == "PURCHASE_RETURN_OUT" }
        assertNotNull(prMov)
        assertEquals(2, prMov?.quantityOut)
    }

    /**
     * Requirement TEST E:
     * Purchase 5, Adjustment +3 => Expected stock = 8
     */
    @Test
    fun testPurchaseAndPositiveAdjustmentIncreasesStock() = runBlocking {
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "زيت زيتون بكر",
                    quantity = 5,
                    unitCost = 20.0
                )
            ),
            paidAmount = 100.0,
            financialAccountId = testAccountId
        )
        assertEquals(5, repository.getProductStock(testProductId).quantityOnHand)

        repository.recordInventoryAdjustment(
            productId = testProductId,
            quantityDelta = 3,
            reason = "جرد إضافي"
        )

        assertEquals(8, repository.getProductStock(testProductId).quantityOnHand)
    }

    /**
     * Requirement TEST G:
     * Purchase 5, Sale 2, Reverse Sale => Expected stock = 5
     */
    @Test
    fun testReverseSaleRestoresStockToOriginal() = runBlocking {
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "زيت زيتون بكر",
                    quantity = 5,
                    unitCost = 20.0
                )
            ),
            paidAmount = 100.0,
            financialAccountId = testAccountId
        )
        assertEquals(5, repository.getProductStock(testProductId).quantityOnHand)

        val saleId = "sale_rev_test_01"
        repository.createSale(
            Sale(
                id = saleId,
                customerId = testCustomerId,
                invoiceNumber = "INV-REV-01",
                saleType = "CASH",
                totalAmount = 60.0,
                paidAmount = 60.0,
                creditAmount = 0.0,
                paymentStatus = "PAID",
                transactionDate = "2026-03-10",
                status = "ACTIVE"
            ),
            listOf(
                SaleLine(
                    id = "sale_rev_line_01",
                    saleId = saleId,
                    productId = testProductId,
                    productNameSnapshot = "زيت زيتون بكر",
                    quantity = 2,
                    unitPrice = 30.0,
                    costPriceAtSale = 20.0,
                    subtotal = 60.0
                )
            )
        )
        assertEquals(3, repository.getProductStock(testProductId).quantityOnHand)

        // Reverse the sale
        val reversal = repository.reverseTransaction(saleId, "إلغاء البيع بناء على طلب العميل")
        assertNotNull(reversal)

        // Stock must return to 5 (not remain 3)
        val stockAfterReversal = repository.getProductStock(testProductId)
        assertEquals(5, stockAfterReversal.quantityOnHand)
    }

    /**
     * Requirement TEST H:
     * Purchase 5, Adjustment +3, Reverse Adjustment => Expected stock = 5
     */
    @Test
    fun testReverseAdjustmentRestoresStockToOriginal() = runBlocking {
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "زيت زيتون بكر",
                    quantity = 5,
                    unitCost = 20.0
                )
            ),
            paidAmount = 100.0,
            financialAccountId = testAccountId
        )
        assertEquals(5, repository.getProductStock(testProductId).quantityOnHand)

        val adj = repository.recordInventoryAdjustment(
            productId = testProductId,
            quantityDelta = 3,
            reason = "تسوية خاطئة"
        )
        assertEquals(8, repository.getProductStock(testProductId).quantityOnHand)

        // Reverse the adjustment
        val reversal = repository.reverseTransaction(adj.id, "تصحيح تسوية خاطئة")
        assertNotNull(reversal)

        // Stock must return to 5 (not remain 8)
        val stockAfterReversal = repository.getProductStock(testProductId)
        assertEquals(5, stockAfterReversal.quantityOnHand)
    }

    /**
     * Requirement TEST I:
     * Verify persisted StockMovement records contain the expected movement types and active/reversed behavior.
     */
    @Test
    fun testPersistedStockMovementTypesAndReversedStatus() = runBlocking {
        // Purchase 5
        repository.recordPurchase(
            supplierId = testSupplierId,
            lines = listOf(
                PurchaseLineRequest(
                    productId = testProductId,
                    productNameSnapshot = "زيت زيتون بكر",
                    quantity = 5,
                    unitCost = 20.0
                )
            ),
            paidAmount = 100.0,
            financialAccountId = testAccountId
        )

        // Sell 2
        val saleId = "sale_type_test"
        repository.createSale(
            Sale(
                id = saleId,
                customerId = testCustomerId,
                invoiceNumber = "INV-T-01",
                saleType = "CASH",
                totalAmount = 60.0,
                paidAmount = 60.0,
                creditAmount = 0.0,
                paymentStatus = "PAID",
                transactionDate = "2026-03-10",
                status = "ACTIVE"
            ),
            listOf(
                SaleLine(
                    id = "sale_line_type_01",
                    saleId = saleId,
                    productId = testProductId,
                    productNameSnapshot = "زيت زيتون بكر",
                    quantity = 2,
                    unitPrice = 30.0,
                    costPriceAtSale = 20.0,
                    subtotal = 60.0
                )
            )
        )

        val movementsBefore = db.stockMovementDao().getMovementsByProductIdSync(testProductId)
        assertEquals(2, movementsBefore.size)
        assertTrue(movementsBefore.any { it.movementType == "PURCHASE_IN" && it.status == "ACTIVE" })
        val saleMovBefore = movementsBefore.find { it.movementType == "SALE_OUT" }
        assertNotNull(saleMovBefore)
        assertEquals("ACTIVE", saleMovBefore?.status)

        // Reverse sale
        repository.reverseTransaction(saleId, "إلغاء المعاملة")

        val movementsAfter = db.stockMovementDao().getMovementsByProductIdSync(testProductId)
        assertEquals(2, movementsAfter.size)
        val saleMovAfter = movementsAfter.find { it.movementType == "SALE_OUT" }
        assertNotNull(saleMovAfter)
        assertEquals("REVERSED", saleMovAfter?.status)
    }

    /**
     * Requirement TEST J:
     * Verify the stock calculation uses persisted StockMovement state and does not retain
     * the effect of a reversed stock movement.
     */
    @Test
    fun testStockCalculationFromPersistedMovementsExcludesReversed() = runBlocking {
        val dao = db.stockMovementDao()

        // Manually insert active movements and reversed movements directly into stock_movements
        dao.insertMovement(
            StockMovementEntity(
                id = "sm_p1",
                productId = testProductId,
                movementType = "PURCHASE_IN",
                quantityIn = 10,
                quantityOut = 0,
                status = "ACTIVE"
            )
        )
        dao.insertMovement(
            StockMovementEntity(
                id = "sm_s1",
                productId = testProductId,
                movementType = "SALE_OUT",
                quantityIn = 0,
                quantityOut = 4,
                status = "REVERSED" // Reversed movement
            )
        )
        dao.insertMovement(
            StockMovementEntity(
                id = "sm_s2",
                productId = testProductId,
                movementType = "SALE_OUT",
                quantityIn = 0,
                quantityOut = 2,
                status = "ACTIVE"
            )
        )

        // Expected stock: 10 - 2 = 8 (the -4 SALE_OUT is REVERSED and must be excluded)
        val summary = repository.getProductStock(testProductId)
        assertEquals(8, summary.quantityOnHand)
        assertEquals(1, summary.reversedMovementCount)
    }
}
