package com.example.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.CustomerLedgerCalculator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2 Migration 6 to 7 & Customer Receivable Verification Suite:
 *
 * Verifies:
 * 1. Migration 6 -> 7 non-destructively adds paidAmount and creditAmount to transactions table.
 * 2. Transactions remain attached strictly to customerId (authoritative relationship).
 * 3. Duplicate customer names cannot mix financial records.
 * 4. Customer receivable can be recalculated from persisted Room database operations through CustomerLedgerCalculator.
 * 5. CustomerEntity.balance and totalDebt stored values do not override the ledger.
 * 6. MIXED sale: Total 100 / Paid 60 / Credit 40 produces receivable 40, NOT 100.
 * 7. CASH, CREDIT, and MIXED produce the correct receivable behavior in persisted storage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration6To7Test {

    private lateinit var context: Context
    private lateinit var db: SmallStoreDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun migration6To7_addsPaidAmountAndCreditAmount_withDefaultZero() {
        val dbName = "test_migration_6_7.db"
        context.deleteDatabase(dbName)

        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(sdb: SupportSQLiteDatabase) {
                    sdb.execSQL("""
                        CREATE TABLE IF NOT EXISTS transactions (
                            id TEXT PRIMARY KEY NOT NULL,
                            title TEXT NOT NULL DEFAULT '',
                            customerNameSnapshot TEXT NOT NULL,
                            activityType TEXT NOT NULL,
                            amount REAL NOT NULL,
                            isCredit INTEGER NOT NULL,
                            date TEXT NOT NULL,
                            relativeTime TEXT NOT NULL,
                            notes TEXT NOT NULL DEFAULT '',
                            settlementType TEXT DEFAULT NULL,
                            customerId TEXT DEFAULT NULL,
                            isArchived INTEGER NOT NULL DEFAULT 0,
                            archivedDate TEXT DEFAULT NULL,
                            customerName TEXT NOT NULL,
                            transactionDate TEXT NOT NULL
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(sdb: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(config)
        val sqliteDb = helper.writableDatabase

        // Insert row at version 6
        sqliteDb.execSQL("""
            INSERT INTO transactions (
                id, title, customerNameSnapshot, activityType, amount, isCredit,
                date, relativeTime, notes, settlementType, customerId, isArchived,
                archivedDate, customerName, transactionDate
            ) VALUES (
                'tx_legacy_v6', 'شراء قديم', 'عميل سابق', 'شراء بالدين', 150.0, 1,
                '2026-09-01', 'سابقا', '', NULL, 'cust_v6', 0,
                NULL, 'عميل سابق', '2026-09-01'
            )
        """.trimIndent())

        // Run MIGRATION_6_7
        MIGRATION_6_7.migrate(sqliteDb)

        // Query new columns
        val cursor = sqliteDb.query("SELECT id, amount, paidAmount, creditAmount FROM transactions WHERE id = 'tx_legacy_v6'")
        assertTrue(cursor.moveToFirst())
        assertEquals("tx_legacy_v6", cursor.getString(0))
        assertEquals(150.0, cursor.getDouble(1), 0.0001)
        assertEquals(0.0, cursor.getDouble(2), 0.0001) // paidAmount default 0.0
        assertEquals(0.0, cursor.getDouble(3), 0.0001) // creditAmount default 0.0
        cursor.close()
        sqliteDb.close()
    }

    @Test
    fun customerReceivable_recalculatedFromPersistedOperations_provesAllPhase2Rules() = kotlinx.coroutines.runBlocking {
        val customerDao = db.customerDao()
        val transactionDao = db.transactionDao()

        val duplicateName = "أحمد خالد"

        // Two customers with identical display names
        val cust1 = CustomerEntity(
            id = "cust_alpha_01",
            customerName = duplicateName,
            balance = 9999.0, // Stored corrupt balance
            totalDebt = 8888.0, // Stored corrupt totalDebt
            phone = "0501111111",
            lastTransactionDate = "2026-09-22",
            hasRecentActivity = true
        )
        val cust2 = CustomerEntity(
            id = "cust_beta_02",
            customerName = duplicateName,
            balance = 5555.0, // Stored corrupt balance
            totalDebt = 4444.0, // Stored corrupt totalDebt
            phone = "0502222222",
            lastTransactionDate = "2026-09-22",
            hasRecentActivity = true
        )

        customerDao.insertCustomers(listOf(cust1, cust2))

        // Operations for Customer 1:
        // 1. MIXED Sale: Total 100, Paid 60, Credit 40 -> customer receivable MUST be 40, NOT 100
        val tx1Mixed = TransactionEntity(
            id = "tx_c1_mixed",
            title = "فاتورة بيع مختلط",
            customerNameSnapshot = duplicateName,
            activityType = "شراء بالدين",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-22",
            relativeTime = "الآن",
            notes = "",
            settlementType = "PARTIAL",
            customerId = cust1.id,
            isArchived = false,
            archivedDate = null,
            customerName = duplicateName,
            transactionDate = "2026-09-22",
            paidAmount = 60.0,
            creditAmount = 40.0
        )
        // 2. CREDIT Sale: Total 200, Paid 0, Credit 200
        val tx1Credit = TransactionEntity(
            id = "tx_c1_credit",
            title = "بيع آجل كامل",
            customerNameSnapshot = duplicateName,
            activityType = "شراء آجل",
            amount = 200.0,
            isCredit = true,
            date = "2026-09-22",
            relativeTime = "الآن",
            notes = "",
            settlementType = null,
            customerId = cust1.id,
            isArchived = false,
            archivedDate = null,
            customerName = duplicateName,
            transactionDate = "2026-09-22",
            paidAmount = 0.0,
            creditAmount = 200.0
        )
        // 3. Payment: 50
        val tx1Payment = TransactionEntity(
            id = "tx_c1_pay",
            title = "دفعة حساب",
            customerNameSnapshot = duplicateName,
            activityType = "تسديد",
            amount = 50.0,
            isCredit = false,
            date = "2026-09-22",
            relativeTime = "الآن",
            notes = "",
            settlementType = null,
            customerId = cust1.id,
            isArchived = false,
            archivedDate = null,
            customerName = duplicateName,
            transactionDate = "2026-09-22",
            paidAmount = 50.0,
            creditAmount = 0.0
        )

        // Operations for Customer 2:
        // 1. CASH Sale: Total 300, Paid 300, Credit 0 -> zero receivable created
        val tx2Cash = TransactionEntity(
            id = "tx_c2_cash",
            title = "بيع كاش",
            customerNameSnapshot = duplicateName,
            activityType = "شراء كاش",
            amount = 300.0,
            isCredit = false,
            date = "2026-09-22",
            relativeTime = "الآن",
            notes = "",
            settlementType = null,
            customerId = cust2.id,
            isArchived = false,
            archivedDate = null,
            customerName = duplicateName,
            transactionDate = "2026-09-22",
            paidAmount = 300.0,
            creditAmount = 0.0
        )
        // 2. CREDIT Sale: Total 500, Paid 0, Credit 500
        val tx2Credit = TransactionEntity(
            id = "tx_c2_credit",
            title = "بيع آجل",
            customerNameSnapshot = duplicateName,
            activityType = "شراء آجل",
            amount = 500.0,
            isCredit = true,
            date = "2026-09-22",
            relativeTime = "الآن",
            notes = "",
            settlementType = null,
            customerId = cust2.id,
            isArchived = false,
            archivedDate = null,
            customerName = duplicateName,
            transactionDate = "2026-09-22",
            paidAmount = 0.0,
            creditAmount = 500.0
        )

        transactionDao.insertTransactions(listOf(tx1Mixed, tx1Credit, tx1Payment, tx2Cash, tx2Credit))

        // 1. Recalculate Customer 1 receivable from persisted operations
        val persistedTxCust1 = transactionDao.getTransactionsByCustomerIdSync(cust1.id).map { it.toModel() }
        assertEquals(3, persistedTxCust1.size)
        assertTrue(persistedTxCust1.all { it.customerId == cust1.id })

        val summaryCust1 = CustomerLedgerCalculator.calculateCustomerBalance(cust1.id, persistedTxCust1)

        // Receivable calculation:
        // Mixed sale 100/60/40 produces 40.0 receivable (NOT 100)
        // Credit sale produces 200.0 receivable
        // Payment produces -50.0
        // Expected balance: 40 + 200 - 50 = 190.0
        assertEquals("Customer 1 calculated receivable must be 190.0", 190.0, summaryCust1.balance, 0.0001)
        assertEquals("Customer 1 total credit sales must be 240.0", 240.0, summaryCust1.totalCreditSales, 0.0001)
        assertEquals("Customer 1 total payments must be 50.0", 50.0, summaryCust1.totalPayments, 0.0001)

        // 2. Recalculate Customer 2 receivable from persisted operations
        val persistedTxCust2 = transactionDao.getTransactionsByCustomerIdSync(cust2.id).map { it.toModel() }
        assertEquals(2, persistedTxCust2.size)
        assertTrue(persistedTxCust2.all { it.customerId == cust2.id })

        val summaryCust2 = CustomerLedgerCalculator.calculateCustomerBalance(cust2.id, persistedTxCust2)

        // Cash sale creates zero receivable
        // Credit sale creates 500.0 receivable
        // Expected balance: 500.0
        assertEquals("Customer 2 calculated receivable must be 500.0", 500.0, summaryCust2.balance, 0.0001)
        assertEquals("Customer 2 total credit sales must be 500.0", 500.0, summaryCust2.totalCreditSales, 0.0001)

        // 3. Stored CustomerEntity.balance & totalDebt NEVER override the ledger
        assertFalse("Stored balance 9999 must not override ledger balance 190", cust1.balance == summaryCust1.balance)
        assertFalse("Stored totalDebt 8888 must not override ledger debt 240", cust1.totalDebt == summaryCust1.totalCreditSales)

        // 4. Duplicate customer names DO NOT mix financial records
        assertEquals(cust1.customerName, cust2.customerName)
        assertEquals(190.0, summaryCust1.balance, 0.0001)
        assertEquals(500.0, summaryCust2.balance, 0.0001)
    }
}
