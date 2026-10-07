package com.example.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Centralized continuity test for the migration chain from v10 through the current v19.
 * Earlier migration tests cover the 3->10 portion; this test ensures every remaining
 * migration can be applied sequentially without breaking its required schema dependencies.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration10To19ChainTest {

    private lateinit var context: Context
    private val dbName = "test_migration_10_to_19_chain.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun teardown() {
        context.deleteDatabase(dbName)
    }

    private fun createVersion10Database(): SupportSQLiteDatabase {
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE transactions (
                            id TEXT NOT NULL PRIMARY KEY
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE sales (
                            id TEXT NOT NULL PRIMARY KEY,
                            invoiceNumber TEXT NOT NULL,
                            customerId TEXT,
                            saleType TEXT NOT NULL,
                            totalAmount REAL NOT NULL,
                            paidAmount REAL NOT NULL,
                            creditAmount REAL NOT NULL,
                            paymentStatus TEXT NOT NULL,
                            transactionDate TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            status TEXT NOT NULL DEFAULT 'ACTIVE'
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE sale_lines (
                            id TEXT NOT NULL PRIMARY KEY,
                            saleId TEXT NOT NULL,
                            productId TEXT,
                            productNameSnapshot TEXT NOT NULL,
                            quantity INTEGER NOT NULL,
                            unitPrice REAL NOT NULL,
                            costPriceAtSale REAL NOT NULL,
                            subtotal REAL NOT NULL,
                            createdAt INTEGER NOT NULL
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    private fun hasTable(db: SupportSQLiteDatabase, table: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
            return it.moveToFirst()
        }
    }

    private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
        db.query("PRAGMA table_info(`$table`)").use {
            val nameIndex = it.getColumnIndex("name")
            while (it.moveToNext()) if (it.getString(nameIndex) == column) return true
        }
        return false
    }

    @Test
    fun migration10Through19_appliesSequentiallyAndCreatesExpectedCurrentSchema() {
        val db = createVersion10Database()

        MIGRATION_10_11.migrate(db)
        MIGRATION_11_12.migrate(db)
        MIGRATION_12_13.migrate(db)
        MIGRATION_13_14.migrate(db)
        MIGRATION_14_15.migrate(db)
        MIGRATION_15_16.migrate(db)
        MIGRATION_16_17.migrate(db)
        MIGRATION_17_18.migrate(db)
        MIGRATION_18_19.migrate(db)

        assertTrue(hasTable(db, "adjustments"))
        assertTrue(hasColumn(db, "adjustments", "status"))
        assertTrue(hasTable(db, "reversals"))
        assertTrue(hasColumn(db, "transactions", "operationStatus"))
        assertTrue(hasTable(db, "sale_returns"))
        assertTrue(hasTable(db, "sale_return_lines"))
        assertTrue(hasTable(db, "refunds"))
        assertTrue(hasTable(db, "suppliers"))
        assertTrue(hasTable(db, "purchases"))
        assertTrue(hasTable(db, "purchase_lines"))
        assertTrue(hasTable(db, "supplier_payments"))
        assertTrue(hasTable(db, "purchase_returns"))
        assertTrue(hasTable(db, "expense_categories"))
        assertTrue(hasTable(db, "expenses"))
        assertTrue(hasTable(db, "stock_movements"))
        assertTrue(hasColumn(db, "sales", "financialAccountId"))
        assertTrue(hasTable(db, "purchase_return_lines"))

        db.close()
    }
}
