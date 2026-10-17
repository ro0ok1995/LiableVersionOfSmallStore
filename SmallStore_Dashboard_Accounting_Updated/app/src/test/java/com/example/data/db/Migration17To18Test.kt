package com.example.data.db

import android.content.ContentValues
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration 17 -> 18 Test Suite:
 *
 * Verifies:
 * 1. Migration succeeds cleanly.
 * 2. `sales` table contains the new nullable `financialAccountId` column.
 * 3. Existing historical sales rows remain valid with zero data loss.
 * 4. Existing historical sales rows have `financialAccountId == NULL`.
 * 5. New sales with explicit `financialAccountId` insert successfully.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration17To18Test {

    private lateinit var context: Context
    private val dbName = "test_migration_17_18.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
    }

    @After
    fun teardown() {
        context.deleteDatabase(dbName)
    }

    private fun createVersion17Database(): SupportSQLiteDatabase {
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(17) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Version 17 sales table schema (without financialAccountId)
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `sales` (
                            `id` TEXT NOT NULL,
                            `invoiceNumber` TEXT NOT NULL,
                            `customerId` TEXT,
                            `saleType` TEXT NOT NULL,
                            `totalAmount` REAL NOT NULL,
                            `paidAmount` REAL NOT NULL,
                            `creditAmount` REAL NOT NULL,
                            `paymentStatus` TEXT NOT NULL,
                            `transactionDate` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun testMigration17To18_addsFinancialAccountId_andPreservesExistingRows() {
        // 1. Setup version 17 database
        val dbV17 = createVersion17Database()

        // 2. Insert historical sale row (v17)
        val historicalSale = ContentValues().apply {
            put("id", "sale_hist_001")
            put("invoiceNumber", "INV-000001")
            put("customerId", "cust_001")
            put("saleType", "MIXED")
            put("totalAmount", 100.0)
            put("paidAmount", 60.0)
            put("creditAmount", 40.0)
            put("paymentStatus", "PARTIAL")
            put("transactionDate", "2026-10-01")
            put("createdAt", 1000L)
            put("updatedAt", 1000L)
            put("status", "ACTIVE")
        }
        dbV17.insert("sales", 0, historicalSale)
        dbV17.close()

        // 3. Reopen and apply MIGRATION_17_18
        val upgradeConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(18) {
                override fun onCreate(db: SupportSQLiteDatabase) {}

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    if (oldVersion == 17 && newVersion == 18) {
                        SmallStoreDatabase.MIGRATION_17_18.migrate(db)
                    }
                }
            })
            .build()
        val dbV18 = FrameworkSQLiteOpenHelperFactory().create(upgradeConfig).writableDatabase

        // 4. Verify column exists in sales table via PRAGMA
        val cursorPragma = dbV18.query("PRAGMA table_info(sales)")
        val columnNames = mutableListOf<String>()
        while (cursorPragma.moveToNext()) {
            val nameIndex = cursorPragma.getColumnIndex("name")
            columnNames.add(cursorPragma.getString(nameIndex))
        }
        cursorPragma.close()

        assertTrue("Column financialAccountId must exist after migration", columnNames.contains("financialAccountId"))

        // 5. Verify historical row is preserved and financialAccountId is NULL
        val cursorQuery = dbV18.query("SELECT * FROM sales WHERE id = 'sale_hist_001'")
        assertTrue("Historical sale row must exist", cursorQuery.moveToFirst())

        val idIndex = cursorQuery.getColumnIndexOrThrow("id")
        val invoiceIndex = cursorQuery.getColumnIndexOrThrow("invoiceNumber")
        val totalIndex = cursorQuery.getColumnIndexOrThrow("totalAmount")
        val paidIndex = cursorQuery.getColumnIndexOrThrow("paidAmount")
        val creditIndex = cursorQuery.getColumnIndexOrThrow("creditAmount")
        val finAccIndex = cursorQuery.getColumnIndexOrThrow("financialAccountId")

        assertEquals("sale_hist_001", cursorQuery.getString(idIndex))
        assertEquals("INV-000001", cursorQuery.getString(invoiceIndex))
        assertEquals(100.0, cursorQuery.getDouble(totalIndex), 0.001)
        assertEquals(60.0, cursorQuery.getDouble(paidIndex), 0.001)
        assertEquals(40.0, cursorQuery.getDouble(creditIndex), 0.001)
        assertTrue("Historical sale must have NULL financialAccountId", cursorQuery.isNull(finAccIndex))
        assertNull(cursorQuery.getString(finAccIndex))

        cursorQuery.close()

        // 6. Verify new sale with non-null financialAccountId can be inserted
        val newSale = ContentValues().apply {
            put("id", "sale_new_002")
            put("invoiceNumber", "INV-000002")
            put("customerId", "cust_001")
            put("saleType", "CASH")
            put("totalAmount", 50.0)
            put("paidAmount", 50.0)
            put("creditAmount", 0.0)
            put("paymentStatus", "PAID")
            put("transactionDate", "2026-10-02")
            put("createdAt", 2000L)
            put("updatedAt", 2000L)
            put("status", "ACTIVE")
            put("financialAccountId", "acc_bank")
        }
        val insertResult = dbV18.insert("sales", 0, newSale)
        assertTrue("Insert with financialAccountId must succeed", insertResult > 0)

        val cursorNew = dbV18.query("SELECT financialAccountId FROM sales WHERE id = 'sale_new_002'")
        assertTrue(cursorNew.moveToFirst())
        assertEquals("acc_bank", cursorNew.getString(0))
        cursorNew.close()

        dbV18.close()
    }
}
