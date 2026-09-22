package com.example.accounting

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.CustomerEntity
import com.example.data.db.CustomerIdentityConflictEntity
import com.example.data.db.MIGRATION_4_5
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.TransactionEntity
import com.example.data.db.toModel
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
 * Phase 2.5 Correction and Hardening Pass: Customer Identity Conflict Test Suite.
 *
 * Mandatory Verification Invariants:
 * 1. Ambiguous names never auto-link.
 * 2. Missing customers never auto-create or auto-link.
 * 3. Null customerId remains null until explicit resolution.
 * 4. Explicit resolution links only to the selected customer ID.
 * 5. Resolving a conflict preserves the original transaction amount and financial data.
 * 6. Dismissing a conflict preserves the transaction.
 * 7. Dismissing a conflict does not create a fake customer.
 * 8. Conflict history remains after resolution.
 * 9. Conflict history remains after dismissal.
 * 10. Two customers with identical names remain financially independent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CustomerIdentityConflictHardeningTest {

    private lateinit var context: Context
    private lateinit var db: SmallStoreDatabase
    private lateinit var repository: StoreRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StoreRepository.createForTesting(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    /**
     * Requirement 1: Ambiguous names never auto-link.
     * When multiple customers exist with identical names, a transaction with that name
     * and customerId == null must NEVER be auto-linked to any customer.
     * Migration 4->5 creates an AMBIGUOUS_CUSTOMER_NAME conflict and customerId remains NULL.
     */
    @Test
    fun test1_ambiguousNames_neverAutoLink() {
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_hardening_req1.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
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
                            isArchived INTEGER NOT NULL DEFAULT 0,
                            archivedDate TEXT DEFAULT NULL,
                            customerId TEXT DEFAULT NULL
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(sdb: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val sdb = helper.writableDatabase

        try {
            // Two distinct customers with identical name "سالم الدوسري"
            sdb.execSQL("INSERT INTO customers (id, customerName, balance, totalDebt, phone, lastTransactionDate, hasRecentActivity) VALUES ('cust_salem_1', 'سالم الدوسري', 0.0, 0.0, '0501111111', '2026-09-20', 1)")
            sdb.execSQL("INSERT INTO customers (id, customerName, balance, totalDebt, phone, lastTransactionDate, hasRecentActivity) VALUES ('cust_salem_2', 'سالم الدوسري', 0.0, 0.0, '0502222222', '2026-09-20', 1)")

            // Unresolved historical transaction with customerId == NULL
            sdb.execSQL("INSERT INTO transactions (id, title, customerName, activityType, amount, isCredit, date, relativeTime, customerId) VALUES ('tx_ambig_test', 'فاتورة آجل', 'سالم الدوسري', 'شراء آجل', 450.0, 1, '2026-09-18', 'منذ يومين', NULL)")

            // Execute Migration 4->5
            MIGRATION_4_5.migrate(sdb)

            // Verify transaction customerId was NEVER auto-assigned (remains NULL)
            val txCursor = sdb.query("SELECT customerId FROM transactions WHERE id = 'tx_ambig_test'")
            assertTrue(txCursor.moveToFirst())
            assertNull("CustomerId must NEVER be auto-linked when names are ambiguous", txCursor.getString(0))
            txCursor.close()

            // Verify conflict was recorded with AMBIGUOUS_CUSTOMER_NAME reason
            val confCursor = sdb.query("SELECT conflictReason, resolutionStatus, resolvedCustomerId FROM customer_identity_conflicts WHERE transactionId = 'tx_ambig_test'")
            assertTrue(confCursor.moveToFirst())
            assertEquals(ConflictReason.AMBIGUOUS_CUSTOMER_NAME.name, confCursor.getString(0))
            assertEquals(ConflictResolutionStatus.UNRESOLVED.name, confCursor.getString(1))
            assertNull(confCursor.getString(2))
            confCursor.close()
        } finally {
            sdb.close()
            context.deleteDatabase("test_hardening_req1.db")
        }
    }

    /**
     * Requirement 2: Missing customers never auto-create or auto-link.
     * If a transaction has a customer name but no matching CustomerEntity exists:
     * - keep customerId NULL
     * - preserve historical name
     * - do not automatically create a customer
     * - create/retain conflict with CUSTOMER_NOT_FOUND
     */
    @Test
    fun test2_missingCustomers_neverAutoCreateOrAutoLink() {
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_hardening_req2.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
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
                            isArchived INTEGER NOT NULL DEFAULT 0,
                            archivedDate TEXT DEFAULT NULL,
                            customerId TEXT DEFAULT NULL
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(sdb: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val sdb = helper.writableDatabase

        try {
            // Customers table is EMPTY
            val initialCustomerCountCursor = sdb.query("SELECT COUNT(*) FROM customers")
            assertTrue(initialCustomerCountCursor.moveToFirst())
            assertEquals(0, initialCustomerCountCursor.getInt(0))
            initialCustomerCountCursor.close()

            // Historical transaction with customer name "عميل غير موجود"
            sdb.execSQL("INSERT INTO transactions (id, title, customerName, activityType, amount, isCredit, date, relativeTime, customerId) VALUES ('tx_missing_test', 'فاتورة غير مسجلة', 'عميل غير موجود', 'شراء آجل', 300.0, 1, '2026-09-17', 'منذ 3 أيام', NULL)")

            // Execute Migration 4->5
            MIGRATION_4_5.migrate(sdb)

            // Verify customerId remains NULL
            val txCursor = sdb.query("SELECT customerId, customerName FROM transactions WHERE id = 'tx_missing_test'")
            assertTrue(txCursor.moveToFirst())
            assertNull("CustomerId must remain NULL when customer is not found", txCursor.getString(0))
            assertEquals("عميل غير موجود", txCursor.getString(1))
            txCursor.close()

            // Verify NO customer was auto-created in customers table
            val customerCountCursor = sdb.query("SELECT COUNT(*) FROM customers")
            assertTrue(customerCountCursor.moveToFirst())
            assertEquals("No customer account must be auto-created", 0, customerCountCursor.getInt(0))
            customerCountCursor.close()

            // Verify conflict reason is CUSTOMER_NOT_FOUND
            val confCursor = sdb.query("SELECT conflictReason, resolutionStatus FROM customer_identity_conflicts WHERE transactionId = 'tx_missing_test'")
            assertTrue(confCursor.moveToFirst())
            assertEquals(ConflictReason.CUSTOMER_NOT_FOUND.name, confCursor.getString(0))
            assertEquals(ConflictResolutionStatus.UNRESOLVED.name, confCursor.getString(1))
            confCursor.close()
        } finally {
            sdb.close()
            context.deleteDatabase("test_hardening_req2.db")
        }
    }

    /**
     * Requirement 3: Null customerId remains null until explicit resolution.
     */
    @Test
    fun test3_nullCustomerId_remainsNullUntilExplicitResolution() = runBlocking {
        val txDao = db.transactionDao()
        val custDao = db.customerDao()
        val conflictDao = db.customerConflictDao()

        val cust = CustomerEntity(id = "c_t3", customerName = "طارق الحامد", balance = 0.0, totalDebt = 0.0, phone = "0555555555", lastTransactionDate = "2026-09-20", hasRecentActivity = true)
        custDao.insertCustomer(cust)

        val tx = TransactionEntity(
            id = "tx_t3",
            title = "فاتورة آجل",
            customerName = "طارق الحامد",
            activityType = "شراء آجل",
            amount = 120.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "اليوم",
            customerId = null
        )
        txDao.insertTransaction(tx)

        val conflict = CustomerIdentityConflictEntity(
            id = "conf_tx_t3",
            transactionId = "tx_t3",
            originalCustomerName = "طارق الحامد",
            conflictReason = ConflictReason.OTHER_UNRESOLVED_IDENTITY.name,
            createdAt = "2026-09-20T10:00:00",
            resolutionStatus = ConflictResolutionStatus.UNRESOLVED.name
        )
        conflictDao.insertConflict(conflict)

        // Read active transactions from repository
        val txList = repository.transactions.first()
        val foundTx = txList.firstOrNull { it.id == "tx_t3" }
        assertNotNull(foundTx)
        assertNull("CustomerId must strictly remain null until explicit resolution", foundTx?.customerId)

        // Customer's balance must not include unassigned transactions
        val customers = repository.customers.first()
        val foundCust = customers.firstOrNull { it.id == "c_t3" }
        assertNotNull(foundCust)
        assertEquals(0.0, foundCust!!.balance, 0.001)
    }

    /**
     * Requirement 4: Explicit resolution links only to the selected customer ID.
     */
    @Test
    fun test4_explicitResolution_linksOnlyToSelectedCustomerId() = runBlocking {
        val txDao = db.transactionDao()
        val custDao = db.customerDao()
        val conflictDao = db.customerConflictDao()

        // Two customers with exact same name
        val cust1 = CustomerEntity(id = "cust_rep4_a", customerName = "فهد الغامدي", balance = 0.0, totalDebt = 0.0, phone = "050111", lastTransactionDate = "2026-09-20", hasRecentActivity = true)
        val cust2 = CustomerEntity(id = "cust_rep4_b", customerName = "فهد الغامدي", balance = 0.0, totalDebt = 0.0, phone = "050222", lastTransactionDate = "2026-09-20", hasRecentActivity = true)
        custDao.insertCustomers(listOf(cust1, cust2))

        val tx = TransactionEntity(
            id = "tx_rep4",
            title = "فاتورة مشتريات",
            customerName = "فهد الغامدي",
            activityType = "شراء آجل",
            amount = 500.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "اليوم",
            customerId = null
        )
        txDao.insertTransaction(tx)

        val conflict = CustomerIdentityConflictEntity(
            id = "conf_rep4",
            transactionId = "tx_rep4",
            originalCustomerName = "فهد الغامدي",
            conflictReason = ConflictReason.AMBIGUOUS_CUSTOMER_NAME.name,
            createdAt = "2026-09-20T10:00:00",
            resolutionStatus = ConflictResolutionStatus.UNRESOLVED.name
        )
        conflictDao.insertConflict(conflict)

        // Explicit user action: select cust_rep4_b
        val success = repository.resolveCustomerConflict(
            conflictId = "conf_rep4",
            resolvedCustomerId = "cust_rep4_b",
            resolvedAt = "2026-09-21T10:00:00",
            notes = "تم التأكيد من رقم الجوال"
        )
        assertTrue(success)

        val updatedTx = txDao.getTransactionById("tx_rep4")
        assertNotNull(updatedTx)
        assertEquals("cust_rep4_b", updatedTx?.customerId)

        // Customer cust_rep4_b now has 500.0 debt, while cust_rep4_a has 0.0!
        val allTx = txDao.getAllTransactionsSync().map { it.toModel() }
        val balanceA = com.example.accounting.CustomerLedgerCalculator.calculateCustomerBalance("cust_rep4_a", allTx)
        val balanceB = com.example.accounting.CustomerLedgerCalculator.calculateCustomerBalance("cust_rep4_b", allTx)
        assertEquals(0.0, balanceA.balance, 0.001)
        assertEquals(500.0, balanceB.balance, 0.001)
    }

    /**
     * Requirement 5: Resolving a conflict preserves the original transaction amount and financial data.
     */
    @Test
    fun test5_resolvingConflict_preservesOriginalTransactionAmountAndFinancialData() = runBlocking {
        val txDao = db.transactionDao()
        val custDao = db.customerDao()
        val conflictDao = db.customerConflictDao()

        val cust = CustomerEntity(id = "cust_r5", customerName = "يوسف النجار", balance = 0.0, totalDebt = 0.0, phone = "051111", lastTransactionDate = "2026-09-20", hasRecentActivity = true)
        custDao.insertCustomer(cust)

        val originalTx = TransactionEntity(
            id = "tx_r5_strict",
            title = "فاتورة تفصيلية",
            customerName = "يوسف النجار",
            activityType = "شراء آجل",
            amount = 875.50,
            isCredit = true,
            date = "2026-09-15",
            relativeTime = "منذ 5 أيام",
            notes = "ملاحظات سرية هامة",
            settlementType = null,
            isArchived = false,
            archivedDate = null,
            customerId = null,
            customerNameSnapshot = "يوسف النجار (السابق)"
        )
        txDao.insertTransaction(originalTx)

        val conflict = CustomerIdentityConflictEntity(
            id = "conf_r5",
            transactionId = "tx_r5_strict",
            originalCustomerName = "يوسف النجار",
            conflictReason = ConflictReason.OTHER_UNRESOLVED_IDENTITY.name,
            createdAt = "2026-09-15T10:00:00",
            resolutionStatus = ConflictResolutionStatus.UNRESOLVED.name
        )
        conflictDao.insertConflict(conflict)

        // Resolve
        val resSuccess = repository.resolveCustomerConflict(
            conflictId = "conf_r5",
            resolvedCustomerId = "cust_r5"
        )
        assertTrue(resSuccess)

        // Verify transaction fields after resolution
        val resolvedTx = txDao.getTransactionById("tx_r5_strict")
        assertNotNull(resolvedTx)
        assertEquals(originalTx.id, resolvedTx?.id)
        assertEquals(originalTx.title, resolvedTx?.title)
        assertEquals(originalTx.amount, resolvedTx?.amount ?: 0.0, 0.0001)
        assertEquals(originalTx.isCredit, resolvedTx?.isCredit)
        assertEquals(originalTx.activityType, resolvedTx?.activityType)
        assertEquals(originalTx.date, resolvedTx?.date)
        assertEquals(originalTx.relativeTime, resolvedTx?.relativeTime)
        assertEquals(originalTx.notes, resolvedTx?.notes)
        assertEquals(originalTx.settlementType, resolvedTx?.settlementType)
        assertEquals(originalTx.isArchived, resolvedTx?.isArchived)
        assertEquals(originalTx.archivedDate, resolvedTx?.archivedDate)
        assertEquals(originalTx.customerNameSnapshot, resolvedTx?.customerNameSnapshot)
        // ONLY customerId changed
        assertEquals("cust_r5", resolvedTx?.customerId)
    }

    /**
     * Requirement 6: Dismissing a conflict preserves the transaction.
     */
    @Test
    fun test6_dismissingConflict_preservesTransaction() = runBlocking {
        val txDao = db.transactionDao()
        val conflictDao = db.customerConflictDao()

        val tx = TransactionEntity(
            id = "tx_r6_dismiss",
            title = "فاتورة عامة",
            customerName = "عميل غير محدد",
            activityType = "شراء آجل",
            amount = 210.0,
            isCredit = true,
            date = "2026-09-16",
            relativeTime = "منذ 4 أيام",
            notes = "دفعة لم يحدد صاحبها",
            customerId = null
        )
        txDao.insertTransaction(tx)

        val conflict = CustomerIdentityConflictEntity(
            id = "conf_r6",
            transactionId = "tx_r6_dismiss",
            originalCustomerName = "عميل غير محدد",
            conflictReason = ConflictReason.CUSTOMER_NOT_FOUND.name,
            createdAt = "2026-09-16T10:00:00",
            resolutionStatus = ConflictResolutionStatus.UNRESOLVED.name
        )
        conflictDao.insertConflict(conflict)

        // Dismiss
        val success = repository.dismissCustomerConflict(
            conflictId = "conf_r6",
            resolvedAt = "2026-09-21T11:00:00",
            notes = "تم الاستبعاد بعد المراجعة"
        )
        assertTrue(success)

        // Transaction MUST still exist in database
        val existingTx = txDao.getTransactionById("tx_r6_dismiss")
        assertNotNull("Dismissed transaction must remain in the database", existingTx)
        assertEquals(210.0, existingTx?.amount ?: 0.0, 0.001)
        assertNull("CustomerId must remain NULL after dismissal", existingTx?.customerId)
    }

    /**
     * Requirement 7: Dismissing a conflict does not create a fake customer.
     */
    @Test
    fun test7_dismissingConflict_doesNotCreateFakeCustomer() = runBlocking {
        val txDao = db.transactionDao()
        val custDao = db.customerDao()
        val conflictDao = db.customerConflictDao()

        val initialCustCount = custDao.getAllCustomersSync().size
        assertEquals(0, initialCustCount)

        val tx = TransactionEntity(
            id = "tx_r7",
            title = "شراء بدون هوية",
            customerName = "عميل مجهول",
            activityType = "شراء آجل",
            amount = 350.0,
            isCredit = true,
            date = "2026-09-17",
            relativeTime = "منذ 3 أيام",
            customerId = null
        )
        txDao.insertTransaction(tx)

        val conflict = CustomerIdentityConflictEntity(
            id = "conf_r7",
            transactionId = "tx_r7",
            originalCustomerName = "عميل مجهول",
            conflictReason = ConflictReason.OTHER_UNRESOLVED_IDENTITY.name,
            createdAt = "2026-09-17T10:00:00",
            resolutionStatus = ConflictResolutionStatus.UNRESOLVED.name
        )
        conflictDao.insertConflict(conflict)

        // Dismiss
        repository.dismissCustomerConflict(conflictId = "conf_r7")

        // Verify customer count is still zero! No fake "عميل كاش" created in customers table
        val finalCustCount = custDao.getAllCustomersSync().size
        assertEquals("Dismissal must never create fake customer accounts", 0, finalCustCount)
    }

    /**
     * Requirement 8: Conflict history remains after resolution.
     */
    @Test
    fun test8_conflictHistoryRemains_afterResolution() = runBlocking {
        val txDao = db.transactionDao()
        val custDao = db.customerDao()
        val conflictDao = db.customerConflictDao()

        val cust = CustomerEntity(id = "cust_r8", customerName = "ماجد المهندس", balance = 0.0, totalDebt = 0.0, phone = "053333", lastTransactionDate = "2026-09-20", hasRecentActivity = true)
        custDao.insertCustomer(cust)

        val tx = TransactionEntity(
            id = "tx_r8",
            title = "فاتورة",
            customerName = "ماجد المهندس",
            activityType = "شراء آجل",
            amount = 150.0,
            isCredit = true,
            date = "2026-09-19",
            relativeTime = "أمس",
            customerId = null
        )
        txDao.insertTransaction(tx)

        val conflict = CustomerIdentityConflictEntity(
            id = "conf_r8",
            transactionId = "tx_r8",
            originalCustomerName = "ماجد المهندس",
            conflictReason = ConflictReason.OTHER_UNRESOLVED_IDENTITY.name,
            createdAt = "2026-09-19T12:00:00",
            resolutionStatus = ConflictResolutionStatus.UNRESOLVED.name
        )
        conflictDao.insertConflict(conflict)

        // Verify conflict count before resolution
        assertEquals(1, conflictDao.getAllConflictsSync().size)
        assertEquals(1, conflictDao.getUnresolvedConflictCountSync())

        // Resolve
        val resAt = "2026-09-21T14:30:00"
        val success = repository.resolveCustomerConflict(
            conflictId = "conf_r8",
            resolvedCustomerId = "cust_r8",
            resolvedAt = resAt,
            notes = "تم التأكيد والمطابقة اليدوية"
        )
        assertTrue(success)

        // Verify conflict row is NOT deleted
        val allConflicts = conflictDao.getAllConflictsSync()
        assertEquals("Conflict row must remain in customer_identity_conflicts table", 1, allConflicts.size)

        val storedConflict = allConflicts[0]
        assertEquals("conf_r8", storedConflict.id)
        assertEquals(ConflictResolutionStatus.RESOLVED.name, storedConflict.resolutionStatus)
        assertEquals("cust_r8", storedConflict.resolvedCustomerId)
        assertEquals(resAt, storedConflict.resolvedAt)
        assertEquals("تم التأكيد والمطابقة اليدوية", storedConflict.notes)

        // But unresolved count is 0
        assertEquals(0, conflictDao.getUnresolvedConflictCountSync())
    }

    /**
     * Requirement 9: Conflict history remains after dismissal.
     */
    @Test
    fun test9_conflictHistoryRemains_afterDismissal() = runBlocking {
        val conflictDao = db.customerConflictDao()

        val conflict = CustomerIdentityConflictEntity(
            id = "conf_r9",
            transactionId = "tx_r9",
            originalCustomerName = "معاملة غير معروفة",
            conflictReason = ConflictReason.MISSING_CUSTOMER_NAME.name,
            createdAt = "2026-09-19T15:00:00",
            resolutionStatus = ConflictResolutionStatus.UNRESOLVED.name
        )
        conflictDao.insertConflict(conflict)

        // Dismiss
        val disAt = "2026-09-21T16:00:00"
        val success = repository.dismissCustomerConflict(
            conflictId = "conf_r9",
            resolvedAt = disAt,
            notes = "معاملة ملغاة أو بدون ذمة مالية لعميل محدد"
        )
        assertTrue(success)

        // Verify row is NOT deleted
        val allConflicts = conflictDao.getAllConflictsSync()
        assertEquals("Conflict row must remain after dismissal", 1, allConflicts.size)

        val stored = allConflicts[0]
        assertEquals(ConflictResolutionStatus.DISMISSED.name, stored.resolutionStatus)
        assertEquals(disAt, stored.resolvedAt)
        assertEquals("معاملة ملغاة أو بدون ذمة مالية لعميل محدد", stored.notes)
        assertNull("resolvedCustomerId must remain null on dismissal", stored.resolvedCustomerId)
    }

    /**
     * Requirement 10: Two customers with identical names remain financially independent.
     */
    @Test
    fun test10_twoCustomersWithIdenticalNames_remainFinanciallyIndependent() = runBlocking {
        val custDao = db.customerDao()
        val txDao = db.transactionDao()

        val sharedName = "عبدالرحمن المطيري"

        val custA = CustomerEntity(
            id = "cust_moti_A",
            customerName = sharedName,
            balance = 0.0,
            totalDebt = 0.0,
            phone = "0501110000",
            lastTransactionDate = "2026-09-20",
            hasRecentActivity = true
        )
        val custB = CustomerEntity(
            id = "cust_moti_B",
            customerName = sharedName,
            balance = 0.0,
            totalDebt = 0.0,
            phone = "0502220000",
            lastTransactionDate = "2026-09-20",
            hasRecentActivity = true
        )
        custDao.insertCustomers(listOf(custA, custB))

        // Transaction 1: Customer A buys on credit for 800.0
        val txA1 = TransactionEntity(
            id = "tx_a_debt",
            title = "فاتورة أ",
            customerName = sharedName,
            customerNameSnapshot = sharedName,
            activityType = "شراء آجل",
            amount = 800.0,
            isCredit = true,
            date = "2026-09-18",
            relativeTime = "اليوم",
            customerId = "cust_moti_A"
        )

        // Transaction 2: Customer B buys on credit for 250.0
        val txB1 = TransactionEntity(
            id = "tx_b_debt",
            title = "فاتورة ب",
            customerName = sharedName,
            customerNameSnapshot = sharedName,
            activityType = "شراء آجل",
            amount = 250.0,
            isCredit = true,
            date = "2026-09-19",
            relativeTime = "اليوم",
            customerId = "cust_moti_B"
        )

        txDao.insertTransactions(listOf(txA1, txB1))

        // Check balances via repository/calculator
        var allTx = txDao.getAllTransactionsSync().map { it.toModel() }
        var summaryA = com.example.accounting.CustomerLedgerCalculator.calculateCustomerBalance(custA.id, allTx)
        var summaryB = com.example.accounting.CustomerLedgerCalculator.calculateCustomerBalance(custB.id, allTx)

        assertEquals("Customer A debt must be 800.0", 800.0, summaryA.balance, 0.001)
        assertEquals("Customer B debt must be 250.0", 250.0, summaryB.balance, 0.001)

        // Customer A pays 800.0 full settlement
        val txAPay = TransactionEntity(
            id = "tx_a_settle",
            title = "سداد حساب",
            customerName = sharedName,
            customerNameSnapshot = sharedName,
            activityType = "تسديد",
            amount = 800.0,
            isCredit = false,
            date = "2026-09-20",
            relativeTime = "اليوم",
            customerId = "cust_moti_A"
        )
        txDao.insertTransaction(txAPay)

        // Re-check balances
        allTx = txDao.getAllTransactionsSync().map { it.toModel() }
        summaryA = com.example.accounting.CustomerLedgerCalculator.calculateCustomerBalance(custA.id, allTx)
        summaryB = com.example.accounting.CustomerLedgerCalculator.calculateCustomerBalance(custB.id, allTx)

        assertEquals("Customer A balance must be 0.0 after payment", 0.0, summaryA.balance, 0.001)
        assertEquals("Customer B balance must remain unaffected at 250.0", 250.0, summaryB.balance, 0.001)
    }
}
