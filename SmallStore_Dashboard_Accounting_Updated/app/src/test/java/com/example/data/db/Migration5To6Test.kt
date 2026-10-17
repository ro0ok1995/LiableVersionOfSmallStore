package com.example.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.CustomerLedgerCalculator
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupPayload
import com.example.model.CustomerAccount
import com.example.model.TransactionItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2.6 Accounting Decoupling Test Suite:
 *
 * Separate persistent customer identity from historical customer-name presentation.
 *
 * Invariants proven:
 * 1. Old `customerName` is preserved as `customerNameSnapshot` during Migration 5->6 without data loss.
 * 2. `customerId` remains the ONLY relational customer identity.
 * 3. Changing `CustomerEntity.customerName` does NOT change historical transaction `customerNameSnapshot`.
 * 4. Backward compatibility: legacy `customerName` reads and falls back to snapshot gracefully.
 * 5. Backup & restore preserves `customerNameSnapshot` and maintains compatibility with legacy payloads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration5To6Test {

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
    fun migration5To6_preservesOldCustomerNameAsSnapshot_andPreservesLegacyColumn() {
        val dbName = "test_migration_5_6.db"
        context.deleteDatabase(dbName)

        // 1. Create SQLite DB at version 5 schema
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(sdb: SupportSQLiteDatabase) {
                    sdb.execSQL("""
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

                    sdb.execSQL("""
                        CREATE TABLE IF NOT EXISTS transactions (
                            id TEXT PRIMARY KEY NOT NULL,
                            title TEXT NOT NULL,
                            customerName TEXT NOT NULL,
                            activityType TEXT NOT NULL,
                            amount REAL NOT NULL,
                            isCredit INTEGER NOT NULL,
                            date TEXT NOT NULL,
                            relativeTime TEXT NOT NULL,
                            notes TEXT NOT NULL DEFAULT '',
                            settlementType TEXT DEFAULT NULL,
                            customerId TEXT DEFAULT NULL,
                            isArchived INTEGER NOT NULL DEFAULT 0,
                            archivedDate TEXT DEFAULT NULL
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(sdb: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val sqliteDb = helper.writableDatabase

        try {
            // Seed customer
            sqliteDb.execSQL("INSERT INTO customers (id, customerName, balance, totalDebt, phone, lastTransactionDate, hasRecentActivity) VALUES ('c100', 'أحمد الشمري', 0, 0, '050111', '2026-09-20', 1)")

            // Seed transactions with version 5 schema (only customerName, no customerNameSnapshot)
            sqliteDb.execSQL("INSERT INTO transactions (id, title, customerName, activityType, amount, isCredit, date, relativeTime, customerId) VALUES ('tx1', 'فاتورة مواد غذائية', 'أحمد الشمري', 'شراء آجل', 150.0, 1, '2026-09-20', 'الآن', 'c100')")
            sqliteDb.execSQL("INSERT INTO transactions (id, title, customerName, activityType, amount, isCredit, date, relativeTime, customerId) VALUES ('tx2', 'بيع نقدي', 'عميل كاش', 'شراء كاش', 50.0, 0, '2026-09-20', 'الآن', NULL)")

            // 2. Execute Migration 5 -> 6
            MIGRATION_5_6.migrate(sqliteDb)

            // 3. Verify columns and data integrity
            val cursor = sqliteDb.query("SELECT id, customerName, customerNameSnapshot, customerId FROM transactions ORDER BY id ASC")
            assertNotNull(cursor)
            assertEquals(2, cursor.count)

            // Verify tx1
            cursor.moveToNext()
            assertEquals("tx1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("أحمد الشمري", cursor.getString(cursor.getColumnIndexOrThrow("customerName")))
            assertEquals("أحمد الشمري", cursor.getString(cursor.getColumnIndexOrThrow("customerNameSnapshot")))
            assertEquals("c100", cursor.getString(cursor.getColumnIndexOrThrow("customerId")))

            // Verify tx2
            cursor.moveToNext()
            assertEquals("tx2", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("عميل كاش", cursor.getString(cursor.getColumnIndexOrThrow("customerName")))
            assertEquals("عميل كاش", cursor.getString(cursor.getColumnIndexOrThrow("customerNameSnapshot")))
            assertEquals(null, cursor.getString(cursor.getColumnIndexOrThrow("customerId")))

            cursor.close()
        } finally {
            sqliteDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun customerProfileNameChange_doesNotChangeHistoricalTransactionSnapshot() = runBlocking {
        val customerDao = db.customerDao()
        val transactionDao = db.transactionDao()

        // 1. Create a persistent customer
        val originalCustomer = CustomerEntity(
            id = "cust_abc",
            customerName = "خالد القحطاني الأصلي",
            balance = 0.0,
            totalDebt = 0.0,
            phone = "0559998877",
            lastTransactionDate = "2026-09-20",
            hasRecentActivity = true
        )
        customerDao.insertCustomer(originalCustomer)

        // 2. Create a historical transaction linked to cust_abc capturing the customerName at creation time
        val tx = TransactionEntity(
            id = "tx_hist_1",
            title = "فاتورة مشتريات",
            customerNameSnapshot = originalCustomer.customerName,
            activityType = "شراء آجل",
            amount = 350.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "الآن",
            notes = "دفتر الحساب القديم",
            settlementType = null,
            customerId = originalCustomer.id,
            isArchived = false,
            archivedDate = null
        )
        transactionDao.insertTransaction(tx)

        // Verify initial state
        val loadedTxBefore = transactionDao.getTransactionById("tx_hist_1")
        assertNotNull(loadedTxBefore)
        assertEquals("خالد القحطاني الأصلي", loadedTxBefore!!.customerNameSnapshot)
        assertEquals("cust_abc", loadedTxBefore.customerId)

        // 3. User updates customer profile/name (e.g. adding company name or correcting spelling)
        val renamedCustomer = originalCustomer.copy(
            customerName = "مؤسسة خالد القحطاني للمقاولات"
        )
        customerDao.updateCustomer(renamedCustomer)

        // 4. Verify customer profile reflects the new name
        val updatedCustomerEntity = customerDao.getCustomerById("cust_abc")
        assertNotNull(updatedCustomerEntity)
        assertEquals("مؤسسة خالد القحطاني للمقاولات", updatedCustomerEntity!!.customerName)

        // 5. INVARIANT: Historical transaction customerNameSnapshot is IMMUTABLE and unchanged
        val loadedTxAfter = transactionDao.getTransactionById("tx_hist_1")
        assertNotNull(loadedTxAfter)
        assertEquals("خالد القحطاني الأصلي", loadedTxAfter!!.customerNameSnapshot)
        assertEquals("cust_abc", loadedTxAfter.customerId)

        // 6. INVARIANT: customerId remains the relational identity for ledger calculation
        val summary = CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = "cust_abc",
            transactions = listOf(loadedTxAfter.toModel())
        )
        assertEquals(350.0, summary.totalCreditSales, 0.001)
        assertEquals(350.0, summary.balance, 0.001)
    }

    @Test
    fun legacyCompatibility_legacyField_and_mappers_workSeamlessly() {
        // Direct instantiation with snapshot
        val txModel = TransactionItem(
            id = "tx_test",
            title = "فاتورة",
            customerNameSnapshot = "سعيد الغامدي",
            activityType = "شراء آجل",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerId = "c1"
        )
        // Legacy customerName property reflects customerNameSnapshot
        @Suppress("DEPRECATION")
        assertEquals("سعيد الغامدي", txModel.customerName)
        assertEquals("سعيد الغامدي", txModel.customerNameSnapshot)

        // Mapper to Entity preserves snapshot
        val entity = txModel.toEntity()
        assertEquals("سعيد الغامدي", entity.customerNameSnapshot)
        @Suppress("DEPRECATION")
        assertEquals("سعيد الغامدي", entity.customerName)

        // Entity mapper to Model preserves snapshot
        val mappedModel = entity.toModel()
        assertEquals("سعيد الغامدي", mappedModel.customerNameSnapshot)
        @Suppress("DEPRECATION")
        assertEquals("سعيد الغامدي", mappedModel.customerName)
    }

    @Test
    fun backupAndRestore_preservesSnapshot_andSupportsLegacyBackupPayload() {
        val tx = TransactionItem(
            id = "tx_backup_1",
            title = "دفعة نقدية",
            customerNameSnapshot = "ناصر الدوسري",
            activityType = "تسديد",
            amount = 200.0,
            isCredit = false,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerId = "c99"
        )

        val payload = BackupPayload(
            storeInfoAtBackupTime = com.example.model.StoreInfo("متجر الاختبار"),
            customers = emptyList(),
            products = emptyList(),
            transactions = listOf(tx),
            transactionItemLines = emptyList(),
            notifications = emptyList()
        )

        // 1. Serialize
        val json = BackupManager.serialize(payload)

        // 2. Verify JSON contains customerNameSnapshot and legacy customerName
        val parsedJson = org.json.JSONObject(json)
        val serializedTx = parsedJson.getJSONArray("transactions").getJSONObject(0)
        assertEquals("ناصر الدوسري", serializedTx.getString("customerNameSnapshot"))
        assertEquals("ناصر الدوسري", serializedTx.getString("customerName"))

        // 3. Deserialize
        val restored = BackupManager.deserialize(json)
        assertEquals(1, restored.transactions.size)
        val restoredTx = restored.transactions[0]
        assertEquals("ناصر الدوسري", restoredTx.customerNameSnapshot)
        @Suppress("DEPRECATION")
        assertEquals("ناصر الدوسري", restoredTx.customerName)
        assertEquals("c99", restoredTx.customerId)

        // 4. Test legacy backup payload without customerNameSnapshot (only customerName)
        val legacyJson = """
            {
                "version": 1,
                "exportedAt": "2026-09-20T10:00:00Z",
                "customers": [],
                "products": [],
                "transactions": [
                    {
                        "id": "tx_legacy_1",
                        "title": "فاتورة قديمة",
                        "customerName": "عبدالعزيز المري",
                        "activityType": "شراء آجل",
                        "amount": 75.0,
                        "isCredit": true,
                        "date": "2026-09-19",
                        "relativeTime": "أمس",
                        "customerId": "c50"
                    }
                ],
                "transactionItemLines": [],
                "notifications": []
            }
        """.trimIndent()

        val restoredLegacy = BackupManager.deserialize(legacyJson)
        assertEquals(1, restoredLegacy.transactions.size)
        val legacyTx = restoredLegacy.transactions[0]
        assertEquals("عبدالعزيز المري", legacyTx.customerNameSnapshot)
        assertEquals("c50", legacyTx.customerId)
    }

    @Test
    fun transactionWithNullCustomerId_isNeverLinkedByCustomerNameSnapshot() = runBlocking {
        val customerDao = db.customerDao()
        val transactionDao = db.transactionDao()

        // Create a customer with a specific name
        val customer = CustomerEntity(
            id = "c_real",
            customerName = "سالم الدوسري",
            balance = 0.0,
            totalDebt = 0.0,
            phone = "0512345678",
            lastTransactionDate = "2026-09-20",
            hasRecentActivity = true
        )
        customerDao.insertCustomer(customer)

        // Create a cash sale with NO customerId, but the same snapshot name
        val unlinkedTx = TransactionEntity(
            id = "tx_unlinked_1",
            title = "بيع نقدي",
            customerNameSnapshot = "سالم الدوسري",
            activityType = "شراء كاش",
            amount = 120.0,
            isCredit = false,
            date = "2026-09-20",
            relativeTime = "الآن",
            notes = "شراء نقدي من الزبون",
            customerId = null
        )
        transactionDao.insertTransaction(unlinkedTx)

        val retrievedTx = transactionDao.getTransactionById("tx_unlinked_1")
        assertNotNull(retrievedTx)
        assertEquals(null, retrievedTx!!.customerId)
        assertEquals("سالم الدوسري", retrievedTx.customerNameSnapshot)

        // Financial ledger calculation for customer 'c_real' must NEVER include tx_unlinked_1
        val customerTxs = listOf(retrievedTx.toModel())
        val summary = CustomerLedgerCalculator.calculateCustomerBalance("c_real", customerTxs)
        assertEquals(0, summary.activeTransactionCount)
        assertEquals(0.0, summary.balance, 0.001)
        assertEquals(0.0, summary.totalCreditSales, 0.001)
        assertEquals(0.0, summary.totalPayments, 0.001)
    }

    @Test
    fun twoCustomersWithIdenticalNames_remainCompletelyIndependent() = runBlocking {
        val customerDao = db.customerDao()
        val transactionDao = db.transactionDao()

        // Two distinct customers sharing identical names
        val cust1 = CustomerEntity(
            id = "cust_ident_1",
            customerName = "محمد علي",
            balance = 0.0,
            totalDebt = 0.0,
            phone = "0500000001",
            lastTransactionDate = "2026-09-20",
            hasRecentActivity = true
        )
        val cust2 = CustomerEntity(
            id = "cust_ident_2",
            customerName = "محمد علي",
            balance = 0.0,
            totalDebt = 0.0,
            phone = "0500000002",
            lastTransactionDate = "2026-09-20",
            hasRecentActivity = true
        )
        customerDao.insertCustomer(cust1)
        customerDao.insertCustomer(cust2)

        // Transaction for Customer 1: 500 debt
        val tx1 = TransactionEntity(
            id = "tx_c1",
            title = "شراء آجل",
            customerNameSnapshot = "محمد علي",
            activityType = "شراء آجل",
            amount = 500.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerId = "cust_ident_1"
        )
        // Transaction for Customer 2: 150 payment
        val tx2 = TransactionEntity(
            id = "tx_c2",
            title = "تسديد دفعة",
            customerNameSnapshot = "محمد علي",
            activityType = "تسديد",
            amount = 150.0,
            isCredit = false,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerId = "cust_ident_2"
        )
        transactionDao.insertTransaction(tx1)
        transactionDao.insertTransaction(tx2)

        val allTransactions = listOf(tx1.toModel(), tx2.toModel())

        // Calculate Customer 1 ledger -> must only reflect tx1 (balance = 500.0)
        val summary1 = CustomerLedgerCalculator.calculateCustomerBalance("cust_ident_1", allTransactions)
        assertEquals(500.0, summary1.balance, 0.001)
        assertEquals(500.0, summary1.totalCreditSales, 0.001)
        assertEquals(0.0, summary1.totalPayments, 0.001)

        // Calculate Customer 2 ledger -> must only reflect tx2 (balance = -150.0)
        val summary2 = CustomerLedgerCalculator.calculateCustomerBalance("cust_ident_2", allTransactions)
        assertEquals(-150.0, summary2.balance, 0.001)
        assertEquals(0.0, summary2.totalCreditSales, 0.001)
        assertEquals(150.0, summary2.totalPayments, 0.001)
    }

    @Test
    fun reportsDisplayCustomerNameSnapshot_withoutChangingAccountingOwnership() {
        // Presentation model captures customerNameSnapshot for display
        val tx = TransactionItem(
            id = "tx_rep_1",
            title = "فاتورة بيع",
            customerNameSnapshot = "يوسف الحربي التاريخي",
            activityType = "شراء آجل",
            amount = 300.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerId = "c_relational_real"
        )

        // Verify CSV export uses customerNameSnapshot for display
        val csv = com.example.util.ReportExporter.generateTransactionsCsv(
            title = "تقرير المعاملات",
            storeName = "متجر النور",
            subtitle = "الفترة الحالية",
            kpis = emptyList(),
            transactions = listOf(tx),
            totalCash = 0.0,
            totalDebt = 300.0,
            totalPayments = 0.0,
            isArabic = true
        )
        org.junit.Assert.assertTrue(csv.contains("يوسف الحربي التاريخي"))

        // Verify ledger ownership remains strictly tied to customerId "c_relational_real"
        val unrelatedLedger = CustomerLedgerCalculator.calculateCustomerBalance(
            "unrelated_id",
            listOf(tx)
        )
        assertEquals(0.0, unrelatedLedger.balance, 0.001)
        assertEquals(0, unrelatedLedger.activeTransactionCount)

        val actualLedger = CustomerLedgerCalculator.calculateCustomerBalance(
            "c_relational_real",
            listOf(tx)
        )
        assertEquals(300.0, actualLedger.balance, 0.001)
        assertEquals(1, actualLedger.activeTransactionCount)
    }
}
