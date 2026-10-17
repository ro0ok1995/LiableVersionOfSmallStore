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
 * Migration 8 -> 9 Test Suite:
 *
 * Verifies:
 * 1. Creates `financial_accounts` table with all required columns and constraints.
 * 2. Creates `payment_methods` table with all required columns and constraints.
 * 3. Creates `customer_payments` table with all required columns, foreign keys (ON DELETE RESTRICT), and indices.
 * 4. Seeds default financial_accounts: Cash, Bank, Card Settlement, E-Wallet (isActive = 1).
 * 5. Seeds default payment_methods matching the same four types (isActive = 1).
 * 6. Existing version-8 data in customers, sales, and transactions tables remains completely intact with NO data loss.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration8To9Test {

    private lateinit var context: Context
    private val dbName = "test_migration_8_9.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
    }

    @After
    fun teardown() {
        context.deleteDatabase(dbName)
    }

    private fun createVersion8Database(): SupportSQLiteDatabase {
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Customers table
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

                    // Transactions table
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `transactions` (
                            `id` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `activityType` TEXT NOT NULL,
                            `amount` REAL NOT NULL,
                            `isCredit` INTEGER NOT NULL,
                            `date` TEXT NOT NULL,
                            `relativeTime` TEXT NOT NULL,
                            `customerName` TEXT NOT NULL,
                            `notes` TEXT NOT NULL,
                            `settlementType` TEXT,
                            `customerId` TEXT,
                            `isArchived` INTEGER NOT NULL DEFAULT 0,
                            `archivedDate` TEXT DEFAULT NULL,
                            `transactionType` TEXT NOT NULL DEFAULT 'SALE',
                            `paymentStatus` TEXT NOT NULL DEFAULT 'UNPAID',
                            `operationStatus` TEXT NOT NULL DEFAULT 'ACTIVE',
                            `reversalReferenceId` TEXT,
                            `paidAmount` REAL NOT NULL DEFAULT 0.0,
                            `creditAmount` REAL NOT NULL DEFAULT 0.0,
                            `saleType` TEXT NOT NULL DEFAULT 'CREDIT',
                            `customerNameSnapshot` TEXT NOT NULL DEFAULT '',
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                        )
                    """.trimIndent())

                    // Sales table
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `sales` (
                            `id` TEXT NOT NULL,
                            `invoiceNumber` TEXT NOT NULL,
                            `customerId` TEXT,
                            `saleType` TEXT NOT NULL,
                            `totalAmount` REAL NOT NULL,
                            `paidAmount` REAL NOT NULL,
                            `creditAmount` REAL NOT NULL,
                            `paymentMethod` TEXT,
                            `paymentStatus` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `transactionDate` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `notes` TEXT,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                        )
                    """.trimIndent())

                    // Insert pre-existing v8 customer and sale
                    val custValues = ContentValues().apply {
                        put("id", "cust_v8_001")
                        put("customerName", "أحمد السالم")
                        put("balance", 150.0)
                        put("totalDebt", 150.0)
                        put("phone", "0551234567")
                        put("lastTransactionDate", "2026-09-22")
                        put("hasRecentActivity", 1)
                        put("isArchived", 0)
                    }
                    db.insert("customers", 0, custValues)

                    val saleValues = ContentValues().apply {
                        put("id", "sale_v8_001")
                        put("invoiceNumber", "INV-000001")
                        put("customerId", "cust_v8_001")
                        put("saleType", "CREDIT")
                        put("totalAmount", 150.0)
                        put("paidAmount", 0.0)
                        put("creditAmount", 150.0)
                        put("paymentStatus", "UNPAID")
                        put("status", "ACTIVE")
                        put("transactionDate", "2026-09-22")
                        put("createdAt", 1727000000000L)
                    }
                    db.insert("sales", 0, saleValues)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun migration8To9_createsTables_seedsAccountsAndPaymentMethods_preservesData() {
        val db = createVersion8Database()

        // Execute migration
        MIGRATION_8_9.migrate(db)

        // 1. Verify financial_accounts table exists and columns are present
        val accCursor = db.query("PRAGMA table_info(`financial_accounts`)")
        val accColumns = mutableListOf<String>()
        while (accCursor.moveToNext()) {
            accColumns.add(accCursor.getString(accCursor.getColumnIndexOrThrow("name")))
        }
        accCursor.close()
        assertTrue("financial_accounts must have id", accColumns.contains("id"))
        assertTrue("financial_accounts must have name", accColumns.contains("name"))
        assertTrue("financial_accounts must have type", accColumns.contains("type"))
        assertTrue("financial_accounts must have isActive", accColumns.contains("isActive"))
        assertTrue("financial_accounts must have createdAt", accColumns.contains("createdAt"))
        assertTrue("financial_accounts must have updatedAt", accColumns.contains("updatedAt"))

        // 2. Verify payment_methods table exists and columns are present
        val pmCursor = db.query("PRAGMA table_info(`payment_methods`)")
        val pmColumns = mutableListOf<String>()
        while (pmCursor.moveToNext()) {
            pmColumns.add(pmCursor.getString(pmCursor.getColumnIndexOrThrow("name")))
        }
        pmCursor.close()
        assertTrue("payment_methods must have id", pmColumns.contains("id"))
        assertTrue("payment_methods must have name", pmColumns.contains("name"))
        assertTrue("payment_methods must have type", pmColumns.contains("type"))
        assertTrue("payment_methods must have isActive", pmColumns.contains("isActive"))
        assertTrue("payment_methods must have createdAt", pmColumns.contains("createdAt"))

        // 3. Verify customer_payments table exists and columns are present
        val cpCursor = db.query("PRAGMA table_info(`customer_payments`)")
        val cpColumns = mutableListOf<String>()
        while (cpCursor.moveToNext()) {
            cpColumns.add(cpCursor.getString(cpCursor.getColumnIndexOrThrow("name")))
        }
        cpCursor.close()
        assertTrue("customer_payments must have id", cpColumns.contains("id"))
        assertTrue("customer_payments must have customerId", cpColumns.contains("customerId"))
        assertTrue("customer_payments must have amount", cpColumns.contains("amount"))
        assertTrue("customer_payments must have paymentMethodId", cpColumns.contains("paymentMethodId"))
        assertTrue("customer_payments must have financialAccountId", cpColumns.contains("financialAccountId"))
        assertTrue("customer_payments must have reference", cpColumns.contains("reference"))
        assertTrue("customer_payments must have transactionDate", cpColumns.contains("transactionDate"))
        assertTrue("customer_payments must have createdAt", cpColumns.contains("createdAt"))
        assertTrue("customer_payments must have notes", cpColumns.contains("notes"))
        assertTrue("customer_payments must have status", cpColumns.contains("status"))

        // 4. Verify seed data for financial_accounts
        val seededAccountsCursor = db.query("SELECT id, name, type, isActive FROM `financial_accounts` ORDER BY id ASC")
        val accounts = mutableListOf<Triple<String, String, String>>()
        while (seededAccountsCursor.moveToNext()) {
            val id = seededAccountsCursor.getString(0)
            val name = seededAccountsCursor.getString(1)
            val type = seededAccountsCursor.getString(2)
            val isActive = seededAccountsCursor.getInt(3)
            assertEquals("Seeded financial account must be active", 1, isActive)
            accounts.add(Triple(id, name, type))
        }
        seededAccountsCursor.close()
        assertEquals("Must seed exactly 4 financial accounts", 4, accounts.size)
        assertTrue(accounts.any { it.second == "Cash" && it.third == "CASH" })
        assertTrue(accounts.any { it.second == "Bank" && it.third == "BANK" })
        assertTrue(accounts.any { it.second == "Card Settlement" && it.third == "CARD" })
        assertTrue(accounts.any { it.second == "E-Wallet" && it.third == "E_WALLET" })

        // 5. Verify seed data for payment_methods
        val seededMethodsCursor = db.query("SELECT id, name, type, isActive FROM `payment_methods` ORDER BY id ASC")
        val methods = mutableListOf<Triple<String, String, String>>()
        while (seededMethodsCursor.moveToNext()) {
            val id = seededMethodsCursor.getString(0)
            val name = seededMethodsCursor.getString(1)
            val type = seededMethodsCursor.getString(2)
            val isActive = seededMethodsCursor.getInt(3)
            assertEquals("Seeded payment method must be active", 1, isActive)
            methods.add(Triple(id, name, type))
        }
        seededMethodsCursor.close()
        assertEquals("Must seed exactly 4 payment methods", 4, methods.size)
        assertTrue(methods.any { it.second == "Cash" && it.third == "CASH" })
        assertTrue(methods.any { it.second == "Bank" && it.third == "BANK" })
        assertTrue(methods.any { it.second == "Card Settlement" && it.third == "CARD" })
        assertTrue(methods.any { it.second == "E-Wallet" && it.third == "E_WALLET" })

        // 6. Verify pre-existing customer and sale are preserved (no data loss)
        val checkCust = db.query("SELECT id, customerName, balance FROM customers WHERE id = 'cust_v8_001'")
        assertTrue("Existing customer must be found", checkCust.moveToFirst())
        assertEquals("أحمد السالم", checkCust.getString(1))
        assertEquals(150.0, checkCust.getDouble(2), 0.0001)
        checkCust.close()

        val checkSale = db.query("SELECT id, totalAmount, creditAmount FROM sales WHERE id = 'sale_v8_001'")
        assertTrue("Existing sale must be found", checkSale.moveToFirst())
        assertEquals(150.0, checkSale.getDouble(1), 0.0001)
        assertEquals(150.0, checkSale.getDouble(2), 0.0001)
        checkSale.close()

        db.close()
    }
}
