package com.example.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.StoreRepository
import com.example.model.ConflictReason
import com.example.model.ConflictResolutionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2.5 Migration and Accounting Safety Test Suite:
 *
 * ACCOUNTING SAFETY RULE:
 * When historical customer identity is ambiguous or unverified, NEVER guess.
 *
 * Verifies that:
 * 1. Ambiguous customer names create persistent conflicts (AMBIGUOUS_CUSTOMER_NAME).
 * 2. Unmatched customer names create persistent conflicts (CUSTOMER_NOT_FOUND).
 * 3. Credit transactions with empty customer names create conflicts (MISSING_CUSTOMER_NAME).
 * 4. Unique names do NOT create conflicts.
 * 5. Anonymous cash sales do NOT create conflicts.
 * 6. Unresolved transactions are NEVER silently assigned to any customer.
 * 7. Resolving a conflict links the original transaction to the selected persistent customerId.
 * 8. Conflict records are permanently preserved for auditability and never deleted upon resolution.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration4To5Test {

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
    fun migration4To5_createsConflictsForAmbiguousTransactions_andLeavesUniqueAndCashUntouched() {
        // 1. Create SQLite DB at version 4 schema using FrameworkSQLiteOpenHelper
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_4_5.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS customers (
                            id TEXT PRIMARY KEY NOT NULL,
                            customerName TEXT NOT NULL,
                            balance REAL NOT NULL,
                            totalDebt REAL NOT NULL,
                            phone TEXT NOT NULL,
                            lastTransactionDate TEXT NOT NULL,
                            hasRecentActivity INTEGER NOT NULL,
                            isArchived INTEGER NOT NULL DEFAULT 0,
                            archivedDate TEXT DEFAULT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS transactions (
                            id TEXT PRIMARY KEY NOT NULL,
                            title TEXT NOT NULL,
                            customerName TEXT NOT NULL,
                            activityType TEXT NOT NULL,
                            amount REAL NOT NULL,
                            isCredit INTEGER NOT NULL,
                            date TEXT NOT NULL,
                            relativeTime TEXT NOT NULL,
                            isArchived INTEGER NOT NULL DEFAULT 0,
                            archivedDate TEXT DEFAULT NULL,
                            customerId TEXT DEFAULT NULL
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val sqliteDb = helper.writableDatabase

        try {
            // Seed customers:
            // - "أحمد الشمري" is unique (1 customer)
            // - "محمد علي" is ambiguous (2 customers share this exact name)
            sqliteDb.execSQL("INSERT INTO customers (id, customerName, balance, totalDebt, phone, lastTransactionDate, hasRecentActivity) VALUES ('c1', 'أحمد الشمري', 0, 0, '050111', '2026-09-20', 1)")
            sqliteDb.execSQL("INSERT INTO customers (id, customerName, balance, totalDebt, phone, lastTransactionDate, hasRecentActivity) VALUES ('c2_a', 'محمد علي', 0, 0, '050222', '2026-09-20', 1)")
            sqliteDb.execSQL("INSERT INTO customers (id, customerName, balance, totalDebt, phone, lastTransactionDate, hasRecentActivity) VALUES ('c2_b', 'محمد علي', 0, 0, '050333', '2026-09-20', 1)")

            // Seed historical transactions:
            // 1. Unique customer transaction (already resolved to c1):
            sqliteDb.execSQL("INSERT INTO transactions (id, title, customerName, activityType, amount, isCredit, date, relativeTime, customerId) VALUES ('tx_unique', 'فاتورة', 'أحمد الشمري', 'شراء آجل', 100.0, 1, '2026-09-10', 'الآن', 'c1')")

            // 2. Ambiguous customer transaction (customerId = NULL):
            sqliteDb.execSQL("INSERT INTO transactions (id, title, customerName, activityType, amount, isCredit, date, relativeTime, customerId) VALUES ('tx_ambiguous', 'فاتورة آجل', 'محمد علي', 'شراء آجل', 250.0, 1, '2026-09-11', 'الآن', NULL)")

            // 3. Customer not found in registry (customerId = NULL):
            sqliteDb.execSQL("INSERT INTO transactions (id, title, customerName, activityType, amount, isCredit, date, relativeTime, customerId) VALUES ('tx_not_found', 'فاتورة آجل', 'سعد القرني', 'شراء آجل', 180.0, 1, '2026-09-12', 'الآن', NULL)")

            // 4. Missing customer name for a credit transaction (customerId = NULL):
            sqliteDb.execSQL("INSERT INTO transactions (id, title, customerName, activityType, amount, isCredit, date, relativeTime, customerId) VALUES ('tx_missing_name', 'دين بدون اسم', '', 'شراء آجل', 75.0, 1, '2026-09-13', 'الآن', NULL)")

            // 5. Anonymous cash sale (customerId = NULL) -> MUST NOT CREATE CONFLICT:
            sqliteDb.execSQL("INSERT INTO transactions (id, title, customerName, activityType, amount, isCredit, date, relativeTime, customerId) VALUES ('tx_cash_anonymous', 'بيع نقدي', 'عميل كاش', 'شراء كاش', 40.0, 0, '2026-09-14', 'الآن', NULL)")

            // 6. Generic walk-in cash customer (customerId = NULL) -> MUST NOT CREATE CONFLICT:
            sqliteDb.execSQL("INSERT INTO transactions (id, title, customerName, activityType, amount, isCredit, date, relativeTime, customerId) VALUES ('tx_cash_walkin', 'بيع نقدي', 'عميل عام', 'بيع نقدي', 60.0, 0, '2026-09-15', 'الآن', NULL)")

            // Run migration 4 -> 5
            MIGRATION_4_5.migrate(sqliteDb)

            // Verify table customer_identity_conflicts was created
            val tableCheck = sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='customer_identity_conflicts'")
            assertTrue(tableCheck.moveToFirst())
            tableCheck.close()

            // Verify conflict count: exactly 3 conflicts (ambiguous, not found, missing name).
            // Zero conflicts for tx_unique, tx_cash_anonymous, tx_cash_walkin!
            val countCursor = sqliteDb.query("SELECT COUNT(*) FROM customer_identity_conflicts")
            assertTrue(countCursor.moveToFirst())
            assertEquals(3, countCursor.getInt(0))
            countCursor.close()

            // 1. Verify Ambiguous Customer Name Conflict
            val ambCursor = sqliteDb.query("SELECT transactionId, originalCustomerName, conflictReason, resolutionStatus, resolvedCustomerId FROM customer_identity_conflicts WHERE transactionId = 'tx_ambiguous'")
            assertTrue("tx_ambiguous must have a conflict record", ambCursor.moveToFirst())
            assertEquals("tx_ambiguous", ambCursor.getString(0))
            assertEquals("محمد علي", ambCursor.getString(1))
            assertEquals(ConflictReason.AMBIGUOUS_CUSTOMER_NAME.name, ambCursor.getString(2))
            assertEquals(ConflictResolutionStatus.UNRESOLVED.name, ambCursor.getString(3))
            assertNull("resolvedCustomerId must be null before resolution", ambCursor.getString(4))
            ambCursor.close()

            // 2. Verify Customer Not Found Conflict
            val nfCursor = sqliteDb.query("SELECT transactionId, originalCustomerName, conflictReason, resolutionStatus FROM customer_identity_conflicts WHERE transactionId = 'tx_not_found'")
            assertTrue("tx_not_found must have a conflict record", nfCursor.moveToFirst())
            assertEquals("tx_not_found", nfCursor.getString(0))
            assertEquals("سعد القرني", nfCursor.getString(1))
            assertEquals(ConflictReason.CUSTOMER_NOT_FOUND.name, nfCursor.getString(2))
            assertEquals(ConflictResolutionStatus.UNRESOLVED.name, nfCursor.getString(3))
            nfCursor.close()

            // 3. Verify Missing Customer Name Conflict
            val mnCursor = sqliteDb.query("SELECT transactionId, conflictReason, resolutionStatus FROM customer_identity_conflicts WHERE transactionId = 'tx_missing_name'")
            assertTrue("tx_missing_name must have a conflict record", mnCursor.moveToFirst())
            assertEquals(ConflictReason.MISSING_CUSTOMER_NAME.name, mnCursor.getString(1))
            assertEquals(ConflictResolutionStatus.UNRESOLVED.name, mnCursor.getString(2))
            mnCursor.close()

            // 4. Verify Anonymous Cash Sales DO NOT have conflicts
            val cashCursor1 = sqliteDb.query("SELECT COUNT(*) FROM customer_identity_conflicts WHERE transactionId = 'tx_cash_anonymous'")
            assertTrue(cashCursor1.moveToFirst())
            assertEquals("Anonymous cash sale must not create conflict", 0, cashCursor1.getInt(0))
            cashCursor1.close()

            val cashCursor2 = sqliteDb.query("SELECT COUNT(*) FROM customer_identity_conflicts WHERE transactionId = 'tx_cash_walkin'")
            assertTrue(cashCursor2.moveToFirst())
            assertEquals("Walk-in cash sale must not create conflict", 0, cashCursor2.getInt(0))
            cashCursor2.close()

            // 5. Verify Invariant: Unresolved transactions are NEVER silently assigned
            val txCursor = sqliteDb.query("SELECT customerId FROM transactions WHERE id = 'tx_ambiguous'")
            assertTrue(txCursor.moveToFirst())
            assertNull("tx_ambiguous customerId must remain NULL until user explicitly resolves it", txCursor.getString(0))
            txCursor.close()

        } finally {
            sqliteDb.close()
            context.deleteDatabase("test_migration_4_5.db")
        }
    }

    @Test
    fun userDrivenResolution_linksTransaction_andPreservesAuditHistory() = runBlocking {
        val conflictDao = db.customerConflictDao()
        val customerDao = db.customerDao()
        val transactionDao = db.transactionDao()
        val repository = StoreRepository.createForTesting(db)

        // Seed 2 customers with same name
        val c1 = CustomerEntity(id = "cust_ali_1", customerName = "علي الزهراني", balance = 0.0, totalDebt = 0.0, phone = "050111", lastTransactionDate = "2026-09-20", hasRecentActivity = true)
        val c2 = CustomerEntity(id = "cust_ali_2", customerName = "علي الزهراني", balance = 0.0, totalDebt = 0.0, phone = "050222", lastTransactionDate = "2026-09-20", hasRecentActivity = true)
        customerDao.insertCustomers(listOf(c1, c2))

        // Seed unlinked transaction
        val tx = TransactionEntity(
            id = "tx_conflict_test",
            title = "فاتورة آجل",
            customerName = "علي الزهراني",
            activityType = "شراء آجل",
            amount = 320.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerId = null
        )
        transactionDao.insertTransaction(tx)

        // Seed unresolved conflict record
        val conflictEntity = CustomerIdentityConflictEntity(
            id = "conflict_tx_conflict_test",
            transactionId = "tx_conflict_test",
            originalCustomerName = "علي الزهراني",
            conflictReason = ConflictReason.AMBIGUOUS_CUSTOMER_NAME.name,
            createdAt = "2026-09-20T10:00:00",
            resolutionStatus = ConflictResolutionStatus.UNRESOLVED.name,
            resolvedCustomerId = null,
            resolvedAt = null,
            notes = "اسم مكرر"
        )
        conflictDao.insertConflict(conflictEntity)

        // Verify initial state
        val unresolvedList = conflictDao.getUnresolvedConflicts().first()
        assertEquals(1, unresolvedList.size)
        assertEquals(1, conflictDao.getUnresolvedConflictCount().first())

        // Explicit, user-driven resolution: assign to cust_ali_2
        val success = repository.resolveCustomerConflict(
            conflictId = "conflict_tx_conflict_test",
            resolvedCustomerId = "cust_ali_2",
            resolvedAt = "2026-09-21T12:00:00",
            notes = "تم التأكيد من رقم هاتف الفاتورة"
        )
        assertTrue(success)

        // 1. Verify original transaction is now linked to cust_ali_2
        val updatedTx = transactionDao.getTransactionById("tx_conflict_test")
        assertNotNull(updatedTx)
        assertEquals("cust_ali_2", updatedTx?.customerId)

        // 2. Invariant: Conflict history is NEVER deleted
        val allConflicts = conflictDao.getAllConflicts().first()
        assertEquals("Conflict history row must be preserved permanently", 1, allConflicts.size)

        val resolvedConflict = allConflicts[0]
        assertEquals(ConflictResolutionStatus.RESOLVED.name, resolvedConflict.resolutionStatus)
        assertEquals("cust_ali_2", resolvedConflict.resolvedCustomerId)
        assertEquals("2026-09-21T12:00:00", resolvedConflict.resolvedAt)
        assertEquals("تم التأكيد من رقم هاتف الفاتورة", resolvedConflict.notes)

        // 3. Verify no longer in unresolved queue
        assertEquals(0, conflictDao.getUnresolvedConflictCount().first())
        assertEquals(0, conflictDao.getUnresolvedConflicts().first().size)
    }

    @Test
    fun dismissConflict_marksStatusAsDismissed_withoutDeletingAuditHistory() = runBlocking {
        val conflictDao = db.customerConflictDao()
        val repository = StoreRepository.createForTesting(db)

        val conflictEntity = CustomerIdentityConflictEntity(
            id = "conflict_dismiss_test",
            transactionId = "tx_dismiss_test",
            originalCustomerName = "عميل مجهول",
            conflictReason = ConflictReason.OTHER_UNRESOLVED_IDENTITY.name,
            createdAt = "2026-09-20T10:00:00",
            resolutionStatus = ConflictResolutionStatus.UNRESOLVED.name
        )
        conflictDao.insertConflict(conflictEntity)

        // Dismiss
        val success = repository.dismissCustomerConflict(
            conflictId = "conflict_dismiss_test",
            resolvedAt = "2026-09-21T12:30:00",
            notes = "تم التحقق: فاتورة مسجلة بالخطأ بدون عميل"
        )
        assertTrue(success)

        // Conflict row is preserved
        val conflict = conflictDao.getConflictById("conflict_dismiss_test")
        assertNotNull(conflict)
        assertEquals(ConflictResolutionStatus.DISMISSED.name, conflict?.resolutionStatus)
        assertEquals("2026-09-21T12:30:00", conflict?.resolvedAt)
        assertEquals("تم التحقق: فاتورة مسجلة بالخطأ بدون عميل", conflict?.notes)
    }
}
