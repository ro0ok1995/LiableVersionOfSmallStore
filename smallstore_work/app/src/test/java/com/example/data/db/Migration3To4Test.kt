package com.example.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.model.CustomerAccount
import com.example.model.TransactionItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration3To4Test {

    private lateinit var db: SmallStoreDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testTransactionWithCustomerIdPersistedAndQueried() = runBlocking {
        val customerDao = db.customerDao()
        val transactionDao = db.transactionDao()

        // Insert customers
        val c1 = CustomerEntity(
            id = "c1",
            customerName = "أحمد الشمري",
            balance = 0.0,
            totalDebt = 0.0,
            phone = "0501112233",
            lastTransactionDate = "2026-09-20",
            hasRecentActivity = true
        )
        val c2 = CustomerEntity(
            id = "c2",
            customerName = "خالد العتيبي",
            balance = 0.0,
            totalDebt = 0.0,
            phone = "0502223344",
            lastTransactionDate = "2026-09-20",
            hasRecentActivity = true
        )
        customerDao.insertCustomers(listOf(c1, c2))

        // Insert transactions with customerId
        val tx1 = TransactionEntity(
            id = "tx1",
            title = "فاتورة آجل",
            customerName = "أحمد الشمري",
            activityType = "شراء آجل",
            amount = 150.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerId = "c1"
        )
        val tx2 = TransactionEntity(
            id = "tx2",
            title = "بيع نقدي",
            customerName = "عميل كاش",
            activityType = "شراء كاش",
            amount = 50.0,
            isCredit = false,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerId = null // Anonymous cash sale
        )
        transactionDao.insertTransactions(listOf(tx1, tx2))

        // Query by customerId
        val c1Txs = transactionDao.getTransactionsByCustomerIdSync("c1")
        assertEquals(1, c1Txs.size)
        assertEquals("tx1", c1Txs[0].id)
        assertEquals("c1", c1Txs[0].customerId)

        // Verify anonymous cash sale has null customerId
        val anonymousTx = transactionDao.getTransactionById("tx2")
        assertNotNull(anonymousTx)
        assertNull(anonymousTx?.customerId)
    }
}
