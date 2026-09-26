package com.example.data.db

import android.content.ContentValues
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
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
 * Migration 9 -> 10 Test Suite:
 *
 * Verifies:
 * 1. Creates `opening_balances` table with all required columns and constraints.
 * 2. Creates index on (entityType, entityId) for opening_balances.
 * 3. Preserves all pre-migration data in customers, financial_accounts, payment_methods, and customer_payments with zero data loss.
 * 4. Ensures no seed rows are auto-inserted into opening_balances (count == 0).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration9To10Test {

    private lateinit var context: Context
    private val dbName = "test_migration_9_10.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
    }

    @After
    fun teardown() {
        context.deleteDatabase(dbName)
    }

    private fun createVersion9Database(): SupportSQLiteDatabase {
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 1. Customers table
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `customers` (
                            `id` TEXT NOT NULL,
                            `customerName` TEXT NOT NULL,
                            `balance` REAL NOT NULL DEFAULT 0.0,
                            `totalDebt` REAL NOT NULL DEFAULT 0.0,
                            `phone` TEXT NOT NULL,
                            `lastTransactionDate` TEXT NOT NULL,
                            `hasRecentActivity` INTEGER NOT NULL,
                            `isArchived` INTEGER NOT NULL DEFAULT 0,
                            `archivedDate` TEXT DEFAULT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    // 2. Financial Accounts table
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `financial_accounts` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `type` TEXT NOT NULL,
                            `isActive` INTEGER NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    // 3. Payment Methods table
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `payment_methods` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `type` TEXT NOT NULL,
                            `isActive` INTEGER NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    // 4. Customer Payments table
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `customer_payments` (
                            `id` TEXT NOT NULL,
                            `customerId` TEXT NOT NULL,
                            `amount` REAL NOT NULL,
                            `paymentMethodId` TEXT NOT NULL,
                            `financialAccountId` TEXT,
                            `reference` TEXT,
                            `transactionDate` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `notes` TEXT,
                            `status` TEXT NOT NULL,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                            FOREIGN KEY(`paymentMethodId`) REFERENCES `payment_methods`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                            FOREIGN KEY(`financialAccountId`) REFERENCES `financial_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                        )
                    """.trimIndent())

                    // Insert pre-existing v9 rows
                    val custValues = ContentValues().apply {
                        put("id", "cust_v9_001")
                        put("customerName", "سالم الغامدي")
                        put("balance", 200.0)
                        put("totalDebt", 200.0)
                        put("phone", "0559876543")
                        put("lastTransactionDate", "2026-09-23")
                        put("hasRecentActivity", 1)
                        put("isArchived", 0)
                    }
                    db.insert("customers", 0, custValues)

                    val accValues = ContentValues().apply {
                        put("id", "acc_v9_cash")
                        put("name", "Cash")
                        put("type", "CASH")
                        put("isActive", 1)
                        put("createdAt", 1727000000000L)
                        put("updatedAt", 1727000000000L)
                    }
                    db.insert("financial_accounts", 0, accValues)

                    val pmValues = ContentValues().apply {
                        put("id", "pm_v9_cash")
                        put("name", "Cash")
                        put("type", "CASH")
                        put("isActive", 1)
                        put("createdAt", 1727000000000L)
                    }
                    db.insert("payment_methods", 0, pmValues)

                    val payValues = ContentValues().apply {
                        put("id", "pay_v9_001")
                        put("customerId", "cust_v9_001")
                        put("amount", 50.0)
                        put("paymentMethodId", "pm_v9_cash")
                        put("financialAccountId", "acc_v9_cash")
                        put("reference", "PAY-V9-001")
                        put("transactionDate", "2026-09-23")
                        put("createdAt", 1727000000000L)
                        put("notes", "سداد دفعة أولى")
                        put("status", "ACTIVE")
                    }
                    db.insert("customer_payments", 0, payValues)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun migration9To10_createsOpeningBalancesTable_withIndex_preservesExistingData_noSeedData() {
        val db = createVersion9Database()

        // Execute migration 9 -> 10
        MIGRATION_9_10.migrate(db)

        // a) Verify opening_balances table exists with correct columns
        val obCursor = db.query("PRAGMA table_info(`opening_balances`)")
        val obColumns = mutableListOf<String>()
        while (obCursor.moveToNext()) {
            obColumns.add(obCursor.getString(obCursor.getColumnIndexOrThrow("name")))
        }
        obCursor.close()

        assertTrue("opening_balances must have id", obColumns.contains("id"))
        assertTrue("opening_balances must have entityType", obColumns.contains("entityType"))
        assertTrue("opening_balances must have entityId", obColumns.contains("entityId"))
        assertTrue("opening_balances must have amount", obColumns.contains("amount"))
        assertTrue("opening_balances must have direction", obColumns.contains("direction"))
        assertTrue("opening_balances must have date", obColumns.contains("date"))
        assertTrue("opening_balances must have reason", obColumns.contains("reason"))
        assertTrue("opening_balances must have reference", obColumns.contains("reference"))
        assertTrue("opening_balances must have createdAt", obColumns.contains("createdAt"))

        // b) Verify index on (entityType, entityId) exists
        val indexListCursor = db.query("PRAGMA index_list(`opening_balances`)")
        val indexNames = mutableListOf<String>()
        while (indexListCursor.moveToNext()) {
            indexNames.add(indexListCursor.getString(indexListCursor.getColumnIndexOrThrow("name")))
        }
        indexListCursor.close()
        assertTrue(
            "Index on (entityType, entityId) must exist",
            indexNames.contains("index_opening_balances_entityType_entityId")
        )

        val indexInfoCursor = db.query("PRAGMA index_info(`index_opening_balances_entityType_entityId`)")
        val indexedColumns = mutableListOf<String>()
        while (indexInfoCursor.moveToNext()) {
            indexedColumns.add(indexInfoCursor.getString(indexInfoCursor.getColumnIndexOrThrow("name")))
        }
        indexInfoCursor.close()
        assertEquals(listOf("entityType", "entityId"), indexedColumns)

        // c) Verify all pre-migration data in customers, financial_accounts, payment_methods, customer_payments is intact
        val checkCust = db.query("SELECT id, customerName, balance, phone FROM customers WHERE id = 'cust_v9_001'")
        assertTrue("Existing customer must be found", checkCust.moveToFirst())
        assertEquals("سالم الغامدي", checkCust.getString(1))
        assertEquals(200.0, checkCust.getDouble(2), 0.0001)
        assertEquals("0559876543", checkCust.getString(3))
        checkCust.close()

        val checkAcc = db.query("SELECT id, name, type, isActive FROM financial_accounts WHERE id = 'acc_v9_cash'")
        assertTrue("Existing financial account must be found", checkAcc.moveToFirst())
        assertEquals("Cash", checkAcc.getString(1))
        assertEquals("CASH", checkAcc.getString(2))
        assertEquals(1, checkAcc.getInt(3))
        checkAcc.close()

        val checkPm = db.query("SELECT id, name, type, isActive FROM payment_methods WHERE id = 'pm_v9_cash'")
        assertTrue("Existing payment method must be found", checkPm.moveToFirst())
        assertEquals("Cash", checkPm.getString(1))
        assertEquals("CASH", checkPm.getString(2))
        assertEquals(1, checkPm.getInt(3))
        checkPm.close()

        val checkPay = db.query("SELECT id, customerId, amount, status FROM customer_payments WHERE id = 'pay_v9_001'")
        assertTrue("Existing customer payment must be found", checkPay.moveToFirst())
        assertEquals("cust_v9_001", checkPay.getString(1))
        assertEquals(50.0, checkPay.getDouble(2), 0.0001)
        assertEquals("ACTIVE", checkPay.getString(3))
        checkPay.close()

        // d) Verify no rows were auto-inserted into opening_balances (no seed data)
        val countCursor = db.query("SELECT COUNT(*) FROM opening_balances")
        assertTrue(countCursor.moveToFirst())
        assertEquals("Opening balances table must have 0 rows after migration", 0, countCursor.getInt(0))
        countCursor.close()

        db.close()
    }
}
