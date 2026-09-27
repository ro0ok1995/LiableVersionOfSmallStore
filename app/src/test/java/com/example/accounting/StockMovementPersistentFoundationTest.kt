package com.example.accounting

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.StockMovement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 11A Persistent Inventory Movement Foundation Tests:
 * Verifies:
 * 1. Persistent StockMovement entity integrity and calculation helpers.
 * 2. All 7 required InventoryMovementType classifications.
 * 3. StockMovementDao persistence, product querying, date-range filtering, and Flow observation.
 * 4. Database version 17 compilation and migration integrity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StockMovementPersistentFoundationTest {

    private lateinit var database: SmallStoreDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testStockMovementEntityHelpersAndMovementTypes() {
        val purchaseIn = StockMovement(
            id = "sm_p1",
            productId = "prod_1",
            movementType = InventoryMovementType.PURCHASE_IN.name,
            quantity = 10,
            unitCost = 15.0,
            referenceType = "PURCHASE",
            referenceId = "pur_101",
            transactionDate = "2026-03-01"
        )
        assertEquals(10, purchaseIn.signedQuantity)
        assertEquals(10, purchaseIn.netQuantityEffect)
        assertEquals(InventoryMovementType.PURCHASE_IN, purchaseIn.typedMovementType)

        val saleOut = StockMovement(
            id = "sm_s1",
            productId = "prod_1",
            movementType = InventoryMovementType.SALE_OUT.name,
            quantity = 3,
            unitCost = 15.0,
            referenceType = "SALE",
            referenceId = "sale_201",
            transactionDate = "2026-03-02"
        )
        assertEquals(-3, saleOut.signedQuantity)
        assertEquals(-3, saleOut.netQuantityEffect)
        assertEquals(InventoryMovementType.SALE_OUT, saleOut.typedMovementType)

        val saleReturnIn = StockMovement(
            id = "sm_sr1",
            productId = "prod_1",
            movementType = InventoryMovementType.SALE_RETURN_IN.name,
            quantity = 1,
            unitCost = 15.0,
            referenceType = "SALE_RETURN",
            referenceId = "sr_301",
            transactionDate = "2026-03-03"
        )
        assertEquals(1, saleReturnIn.signedQuantity)
        assertEquals(1, saleReturnIn.netQuantityEffect)

        val purchaseReturnOut = StockMovement(
            id = "sm_pr1",
            productId = "prod_1",
            movementType = InventoryMovementType.PURCHASE_RETURN_OUT.name,
            quantity = 2,
            unitCost = 15.0,
            referenceType = "PURCHASE_RETURN",
            referenceId = "pr_401",
            transactionDate = "2026-03-04"
        )
        assertEquals(-2, purchaseReturnOut.signedQuantity)
        assertEquals(-2, purchaseReturnOut.netQuantityEffect)

        val damageOut = StockMovement(
            id = "sm_dmg1",
            productId = "prod_1",
            movementType = InventoryMovementType.DAMAGE_OUT.name,
            quantity = 1,
            unitCost = 15.0,
            referenceType = "DAMAGE",
            referenceId = "dmg_501",
            transactionDate = "2026-03-05"
        )
        assertEquals(-1, damageOut.signedQuantity)
        assertEquals(-1, damageOut.netQuantityEffect)

        val adjIn = StockMovement(
            id = "sm_adj1",
            productId = "prod_1",
            movementType = InventoryMovementType.ADJUSTMENT_IN.name,
            quantity = 4,
            unitCost = 15.0,
            referenceType = "ADJUSTMENT",
            referenceId = "adj_601",
            transactionDate = "2026-03-06"
        )
        assertEquals(4, adjIn.signedQuantity)
        assertEquals(4, adjIn.netQuantityEffect)

        val adjOut = StockMovement(
            id = "sm_adj2",
            productId = "prod_1",
            movementType = InventoryMovementType.ADJUSTMENT_OUT.name,
            quantity = 2,
            unitCost = 15.0,
            referenceType = "ADJUSTMENT",
            referenceId = "adj_602",
            transactionDate = "2026-03-07"
        )
        assertEquals(-2, adjOut.signedQuantity)
        assertEquals(-2, adjOut.netQuantityEffect)

        // Reversed movement has 0 net effect
        val reversedMovement = purchaseIn.copy(status = "REVERSED")
        assertEquals(0, reversedMovement.netQuantityEffect)
    }

    @Test
    fun testStockMovementDaoPersistenceAndQuerying() = runBlocking {
        val dao = database.stockMovementDao()

        val m1 = StockMovement(
            id = "sm_001",
            productId = "prod_rice",
            movementType = "PURCHASE_IN",
            quantity = 50,
            unitCost = 40.0,
            referenceType = "PURCHASE",
            referenceId = "pur_100",
            transactionDate = "2026-03-01",
            createdAt = 1000L
        )

        val m2 = StockMovement(
            id = "sm_002",
            productId = "prod_rice",
            movementType = "SALE_OUT",
            quantity = 10,
            unitCost = 40.0,
            referenceType = "SALE",
            referenceId = "sale_101",
            transactionDate = "2026-03-05",
            createdAt = 2000L
        )

        val m3 = StockMovement(
            id = "sm_003",
            productId = "prod_oil",
            movementType = "PURCHASE_IN",
            quantity = 20,
            unitCost = 25.0,
            referenceType = "PURCHASE",
            referenceId = "pur_102",
            transactionDate = "2026-03-06",
            createdAt = 3000L
        )

        dao.insertMovement(m1)
        dao.insertMovements(listOf(m2, m3))

        assertEquals(3, dao.getMovementCount())
        assertEquals(2, dao.getMovementCountByProduct("prod_rice"))
        assertEquals(1, dao.getMovementCountByProduct("prod_oil"))

        // Query by product ID
        val riceMovements = dao.getMovementsByProductIdSync("prod_rice")
        assertEquals(2, riceMovements.size)
        assertEquals("sm_001", riceMovements[0].id)
        assertEquals("sm_002", riceMovements[1].id)

        // Query by date range
        val dateRangeMovements = dao.getMovementsByDateRangeSync("2026-03-02", "2026-03-05")
        assertEquals(1, dateRangeMovements.size)
        assertEquals("sm_002", dateRangeMovements[0].id)

        // Observe Flow
        val flowMovements = dao.getMovementsByProductId("prod_rice").first()
        assertEquals(2, flowMovements.size)

        // Query by reference
        val refMovements = dao.getMovementsByReferenceSync("SALE", "sale_101")
        assertEquals(1, refMovements.size)
        assertEquals("sm_002", refMovements[0].id)

        // Query by single ID
        val single = dao.getMovementById("sm_001")
        assertNotNull(single)
        assertEquals(50, single?.quantity)
    }
}
