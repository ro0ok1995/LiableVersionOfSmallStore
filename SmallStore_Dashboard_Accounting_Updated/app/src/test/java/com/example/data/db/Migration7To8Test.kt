package com.example.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration 7 -> 8 Test Suite:
 *
 * Verifies:
 * 1. Creates `sales` table with all required columns and constraints.
 * 2. Creates `sale_lines` table with all required columns, frozen cost prices, and constraints.
 * 3. Enforces foreign key relationships:
 *    - sales.customerId -> customers.id (ON DELETE RESTRICT)
 *    - sale_lines.saleId -> sales.id (ON DELETE CASCADE)
 * 4. Enforces indices:
 *    - Unique index on sales.invoiceNumber
 *    - Index on sales.customerId
 *    - Index on sales(customerId, transactionDate)
 *    - Index on sale_lines.saleId
 *    - Index on sale_lines.productId
 * 5. Existing version-7 data in customers and transactions tables remains completely intact.
 * 6. Migrated database supports standard write/read operations on Sales and SaleLines.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration7To8Test {

    private lateinit var context: Context
    private val dbName = "test_migration_7_8.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
    }

    @After
    fun teardown() {
        context.deleteDatabase(dbName)
    }

    /**
     * Builds a version 7 SQLite database with customers and transactions tables,
     * matching the exact schema at Room database version 7.
     */
    private fun createVersion7Database(): SupportSQLiteDatabase {
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(sdb: SupportSQLiteDatabase) {
                    // Customers table at version 7
                    sdb.execSQL("""
                        CREATE TABLE IF NOT EXISTS `customers` (
                            `id` TEXT NOT NULL,
                            `customerName` TEXT NOT NULL,
                            `balance` REAL NOT NULL,
                            `totalDebt` REAL NOT NULL,
                            `phone` TEXT NOT NULL,
                            `lastTransactionDate` TEXT NOT NULL,
                            `hasRecentActivity` INTEGER NOT NULL,
                            `isArchived` INTEGER NOT NULL DEFAULT 0,
                            `archivedDate` TEXT DEFAULT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    // Transactions table at version 7 (includes paidAmount and creditAmount from Migration 6->7)
                    sdb.execSQL("""
                        CREATE TABLE IF NOT EXISTS `transactions` (
                            `id` TEXT NOT NULL,
                            `title` TEXT NOT NULL DEFAULT '',
                            `customerNameSnapshot` TEXT NOT NULL,
                            `activityType` TEXT NOT NULL,
                            `amount` REAL NOT NULL,
                            `isCredit` INTEGER NOT NULL,
                            `date` TEXT NOT NULL,
                            `relativeTime` TEXT NOT NULL,
                            `notes` TEXT NOT NULL DEFAULT '',
                            `settlementType` TEXT DEFAULT NULL,
                            `customerId` TEXT DEFAULT NULL,
                            `isArchived` INTEGER NOT NULL DEFAULT 0,
                            `archivedDate` TEXT DEFAULT NULL,
                            `customerName` TEXT NOT NULL,
                            `transactionDate` TEXT NOT NULL,
                            `paidAmount` REAL NOT NULL DEFAULT 0.0,
                            `creditAmount` REAL NOT NULL DEFAULT 0.0,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                        )
                    """.trimIndent())

                    sdb.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_customerId` ON `transactions` (`customerId`)")
                    sdb.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_customerId_transactionDate` ON `transactions` (`customerId`, `transactionDate`)")
                }

                override fun onUpgrade(sdb: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        return helper.writableDatabase
    }

    @Test
    fun migration7To8_createsSalesAndSaleLinesTables_andPreservesV7Data() {
        val db = createVersion7Database()

        // Insert version 7 customer row
        db.execSQL("""
            INSERT INTO customers (
                id, customerName, balance, totalDebt, phone, lastTransactionDate, hasRecentActivity, isArchived
            ) VALUES (
                'cust_v7_100', 'سالم الدوسري', 150.0, 150.0, '0551112233', '2026-09-20', 1, 0
            )
        """.trimIndent())

        // Insert version 7 transaction row
        db.execSQL("""
            INSERT INTO transactions (
                id, title, customerNameSnapshot, activityType, amount, isCredit,
                date, relativeTime, notes, settlementType, customerId, isArchived,
                archivedDate, customerName, transactionDate, paidAmount, creditAmount
            ) VALUES (
                'tx_v7_100', 'فاتورة مشتريات قديمة', 'سالم الدوسري', 'شراء بالدين', 150.0, 1,
                '2026-09-20', 'منذ يومين', 'دفعة أولى 50 ومتبقي 100', 'PARTIAL', 'cust_v7_100', 0,
                NULL, 'سالم الدوسري', '2026-09-20', 50.0, 100.0
            )
        """.trimIndent())

        // Execute MIGRATION_7_8 directly against SQLite
        MIGRATION_7_8.migrate(db)

        // 1. Verify tables exist in sqlite_master
        val tablesCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('sales', 'sale_lines')")
        val foundTables = mutableSetOf<String>()
        while (tablesCursor.moveToNext()) {
            foundTables.add(tablesCursor.getString(0))
        }
        tablesCursor.close()
        assertTrue("sales table must exist", foundTables.contains("sales"))
        assertTrue("sale_lines table must exist", foundTables.contains("sale_lines"))

        // 2. Verify existing version 7 customer remains intact
        val custCursor = db.query("SELECT id, customerName, balance, totalDebt, phone FROM customers WHERE id = 'cust_v7_100'")
        assertTrue(custCursor.moveToFirst())
        assertEquals("cust_v7_100", custCursor.getString(0))
        assertEquals("سالم الدوسري", custCursor.getString(1))
        assertEquals(150.0, custCursor.getDouble(2), 0.0001)
        assertEquals(150.0, custCursor.getDouble(3), 0.0001)
        assertEquals("0551112233", custCursor.getString(4))
        custCursor.close()

        // 3. Verify existing version 7 transaction remains intact
        val txCursor = db.query("SELECT id, amount, paidAmount, creditAmount, customerId, customerNameSnapshot FROM transactions WHERE id = 'tx_v7_100'")
        assertTrue(txCursor.moveToFirst())
        assertEquals("tx_v7_100", txCursor.getString(0))
        assertEquals(150.0, txCursor.getDouble(1), 0.0001)
        assertEquals(50.0, txCursor.getDouble(2), 0.0001)
        assertEquals(100.0, txCursor.getDouble(3), 0.0001)
        assertEquals("cust_v7_100", txCursor.getString(4))
        assertEquals("سالم الدوسري", txCursor.getString(5))
        txCursor.close()

        db.close()
    }

    @Test
    fun migration7To8_verifiesSalesTableSchemaAndConstraints() {
        val db = createVersion7Database()
        MIGRATION_7_8.migrate(db)

        // Inspect sales columns via PRAGMA table_info
        val columnsCursor = db.query("PRAGMA table_info(sales)")
        val columnMap = mutableMapOf<String, Pair<String, Int>>() // name -> (type, notNull)
        while (columnsCursor.moveToNext()) {
            val name = columnsCursor.getString(1)
            val type = columnsCursor.getString(2)
            val notNull = columnsCursor.getInt(3)
            columnMap[name] = Pair(type.uppercase(), notNull)
        }
        columnsCursor.close()

        // Required columns for sales table
        assertTrue("id column must exist", columnMap.containsKey("id"))
        assertTrue("invoiceNumber column must exist", columnMap.containsKey("invoiceNumber"))
        assertTrue("customerId column must exist", columnMap.containsKey("customerId"))
        assertTrue("saleType column must exist", columnMap.containsKey("saleType"))
        assertTrue("totalAmount column must exist", columnMap.containsKey("totalAmount"))
        assertTrue("paidAmount column must exist", columnMap.containsKey("paidAmount"))
        assertTrue("creditAmount column must exist", columnMap.containsKey("creditAmount"))
        assertTrue("paymentStatus column must exist", columnMap.containsKey("paymentStatus"))
        assertTrue("transactionDate column must exist", columnMap.containsKey("transactionDate"))
        assertTrue("createdAt column must exist", columnMap.containsKey("createdAt"))
        assertTrue("updatedAt column must exist", columnMap.containsKey("updatedAt"))
        assertTrue("status column must exist", columnMap.containsKey("status"))

        // Types and nullability
        assertEquals("TEXT", columnMap["id"]?.first)
        assertEquals(1, columnMap["id"]?.second) // NOT NULL

        assertEquals("TEXT", columnMap["invoiceNumber"]?.first)
        assertEquals(1, columnMap["invoiceNumber"]?.second) // NOT NULL

        assertEquals("REAL", columnMap["totalAmount"]?.first)
        assertEquals(1, columnMap["totalAmount"]?.second) // NOT NULL

        assertEquals("REAL", columnMap["paidAmount"]?.first)
        assertEquals(1, columnMap["paidAmount"]?.second) // NOT NULL

        assertEquals("REAL", columnMap["creditAmount"]?.first)
        assertEquals(1, columnMap["creditAmount"]?.second) // NOT NULL

        assertEquals("TEXT", columnMap["saleType"]?.first)
        assertEquals("TEXT", columnMap["paymentStatus"]?.first)
        assertEquals("TEXT", columnMap["transactionDate"]?.first)
        assertEquals("INTEGER", columnMap["createdAt"]?.first)
        assertEquals("INTEGER", columnMap["updatedAt"]?.first)
        assertEquals("TEXT", columnMap["status"]?.first)

        // Inspect Foreign Keys via PRAGMA foreign_key_list(sales)
        val fkCursor = db.query("PRAGMA foreign_key_list(sales)")
        var foundCustomerFk = false
        while (fkCursor.moveToNext()) {
            val table = fkCursor.getString(2)
            val from = fkCursor.getString(3)
            val to = fkCursor.getString(4)
            val onDelete = fkCursor.getString(6)
            if (table == "customers" && from == "customerId" && to == "id") {
                foundCustomerFk = true
                assertEquals("RESTRICT", onDelete.uppercase())
            }
        }
        fkCursor.close()
        assertTrue("Foreign key to customers(id) with ON DELETE RESTRICT must exist", foundCustomerFk)

        // Inspect Indices via PRAGMA index_list(sales)
        val indexCursor = db.query("PRAGMA index_list(sales)")
        val indices = mutableMapOf<String, Boolean>() // name -> isUnique
        while (indexCursor.moveToNext()) {
            val name = indexCursor.getString(1)
            val unique = indexCursor.getInt(2) == 1
            indices[name] = unique
        }
        indexCursor.close()

        assertTrue("Unique index on invoiceNumber must exist", indices["index_sales_invoiceNumber"] == true)
        assertTrue("Index on customerId must exist", indices.containsKey("index_sales_customerId"))
        assertTrue("Index on (customerId, transactionDate) must exist", indices.containsKey("index_sales_customerId_transactionDate"))

        db.close()
    }

    @Test
    fun migration7To8_verifiesSaleLinesTableSchemaAndConstraints() {
        val db = createVersion7Database()
        MIGRATION_7_8.migrate(db)

        // Inspect sale_lines columns via PRAGMA table_info
        val columnsCursor = db.query("PRAGMA table_info(sale_lines)")
        val columnMap = mutableMapOf<String, Pair<String, Int>>() // name -> (type, notNull)
        while (columnsCursor.moveToNext()) {
            val name = columnsCursor.getString(1)
            val type = columnsCursor.getString(2)
            val notNull = columnsCursor.getInt(3)
            columnMap[name] = Pair(type.uppercase(), notNull)
        }
        columnsCursor.close()

        // Required columns for sale_lines table
        assertTrue("id column must exist", columnMap.containsKey("id"))
        assertTrue("saleId column must exist", columnMap.containsKey("saleId"))
        assertTrue("productId column must exist", columnMap.containsKey("productId"))
        assertTrue("productNameSnapshot column must exist", columnMap.containsKey("productNameSnapshot"))
        assertTrue("quantity column must exist", columnMap.containsKey("quantity"))
        assertTrue("unitPrice column must exist", columnMap.containsKey("unitPrice"))
        assertTrue("costPriceAtSale column must exist", columnMap.containsKey("costPriceAtSale"))
        assertTrue("subtotal column must exist", columnMap.containsKey("subtotal"))
        assertTrue("createdAt column must exist", columnMap.containsKey("createdAt"))

        // Types and nullability
        assertEquals("TEXT", columnMap["id"]?.first)
        assertEquals(1, columnMap["id"]?.second) // NOT NULL

        assertEquals("TEXT", columnMap["saleId"]?.first)
        assertEquals(1, columnMap["saleId"]?.second) // NOT NULL

        assertEquals("TEXT", columnMap["productNameSnapshot"]?.first)
        assertEquals(1, columnMap["productNameSnapshot"]?.second) // NOT NULL

        assertEquals("INTEGER", columnMap["quantity"]?.first)
        assertEquals(1, columnMap["quantity"]?.second) // NOT NULL

        assertEquals("REAL", columnMap["unitPrice"]?.first)
        assertEquals(1, columnMap["unitPrice"]?.second) // NOT NULL

        assertEquals("REAL", columnMap["costPriceAtSale"]?.first)
        assertEquals(1, columnMap["costPriceAtSale"]?.second) // NOT NULL

        assertEquals("REAL", columnMap["subtotal"]?.first)
        assertEquals(1, columnMap["subtotal"]?.second) // NOT NULL

        assertEquals("INTEGER", columnMap["createdAt"]?.first)

        // Inspect Foreign Keys via PRAGMA foreign_key_list(sale_lines)
        val fkCursor = db.query("PRAGMA foreign_key_list(sale_lines)")
        var foundSaleFk = false
        while (fkCursor.moveToNext()) {
            val table = fkCursor.getString(2)
            val from = fkCursor.getString(3)
            val to = fkCursor.getString(4)
            val onDelete = fkCursor.getString(6)
            if (table == "sales" && from == "saleId" && to == "id") {
                foundSaleFk = true
                assertEquals("CASCADE", onDelete.uppercase())
            }
        }
        fkCursor.close()
        assertTrue("Foreign key to sales(id) with ON DELETE CASCADE must exist", foundSaleFk)

        // Inspect Indices via PRAGMA index_list(sale_lines)
        val indexCursor = db.query("PRAGMA index_list(sale_lines)")
        val indices = mutableSetOf<String>()
        while (indexCursor.moveToNext()) {
            indices.add(indexCursor.getString(1))
        }
        indexCursor.close()

        assertTrue("Index on saleId must exist", indices.contains("index_sale_lines_saleId"))
        assertTrue("Index on productId must exist", indices.contains("index_sale_lines_productId"))

        db.close()
    }

    @Test
    fun migration7To8_canInsertAndQuerySalesAndSaleLines_andEnforcesInvoiceNumberUniqueness() {
        val db = createVersion7Database()
        MIGRATION_7_8.migrate(db)

        // Insert customer for FK reference
        db.execSQL("""
            INSERT INTO customers (
                id, customerName, balance, totalDebt, phone, lastTransactionDate, hasRecentActivity, isArchived
            ) VALUES (
                'cust_test_1', 'عميل تجريبي', 0.0, 0.0, '0500000000', '2026-09-22', 1, 0
            )
        """.trimIndent())

        // Insert valid sale
        db.execSQL("""
            INSERT INTO sales (
                id, invoiceNumber, customerId, saleType, totalAmount, paidAmount, creditAmount,
                paymentStatus, transactionDate, createdAt, updatedAt, status
            ) VALUES (
                'sale_001', 'INV-000001', 'cust_test_1', 'MIXED', 100.0, 60.0, 40.0,
                'PARTIAL', '2026-09-22', 1727000000000, 1727000000000, 'ACTIVE'
            )
        """.trimIndent())

        // Insert valid sale line
        db.execSQL("""
            INSERT INTO sale_lines (
                id, saleId, productId, productNameSnapshot, quantity, unitPrice, costPriceAtSale, subtotal, createdAt
            ) VALUES (
                'line_001', 'sale_001', 'prod_001', 'حليب نادك 1 لتر', 2, 50.0, 35.0, 100.0, 1727000000000
            )
        """.trimIndent())

        // Query sale and verify
        val saleCursor = db.query("SELECT id, invoiceNumber, customerId, totalAmount, paidAmount, creditAmount, saleType, paymentStatus FROM sales WHERE id = 'sale_001'")
        assertTrue(saleCursor.moveToFirst())
        assertEquals("sale_001", saleCursor.getString(0))
        assertEquals("INV-000001", saleCursor.getString(1))
        assertEquals("cust_test_1", saleCursor.getString(2))
        assertEquals(100.0, saleCursor.getDouble(3), 0.0001)
        assertEquals(60.0, saleCursor.getDouble(4), 0.0001)
        assertEquals(40.0, saleCursor.getDouble(5), 0.0001)
        assertEquals("MIXED", saleCursor.getString(6))
        assertEquals("PARTIAL", saleCursor.getString(7))
        saleCursor.close()

        // Query sale line and verify
        val lineCursor = db.query("SELECT id, saleId, productId, productNameSnapshot, quantity, unitPrice, costPriceAtSale, subtotal FROM sale_lines WHERE id = 'line_001'")
        assertTrue(lineCursor.moveToFirst())
        assertEquals("line_001", lineCursor.getString(0))
        assertEquals("sale_001", lineCursor.getString(1))
        assertEquals("prod_001", lineCursor.getString(2))
        assertEquals("حليب نادك 1 لتر", lineCursor.getString(3))
        assertEquals(2, lineCursor.getInt(4))
        assertEquals(50.0, lineCursor.getDouble(5), 0.0001)
        assertEquals(35.0, lineCursor.getDouble(6), 0.0001) // Frozen cost price
        assertEquals(100.0, lineCursor.getDouble(7), 0.0001)
        lineCursor.close()

        // Test Unique Constraint on invoiceNumber: inserting duplicate invoiceNumber must throw SQLiteConstraintException
        try {
            db.execSQL("""
                INSERT INTO sales (
                    id, invoiceNumber, customerId, saleType, totalAmount, paidAmount, creditAmount,
                    paymentStatus, transactionDate, createdAt, updatedAt, status
                ) VALUES (
                    'sale_002', 'INV-000001', 'cust_test_1', 'CASH', 50.0, 50.0, 0.0,
                    'PAID', '2026-09-22', 1727000000000, 1727000000000, 'ACTIVE'
                )
            """.trimIndent())
            fail("Expected SQLiteConstraintException when inserting duplicate invoiceNumber")
        } catch (e: SQLiteConstraintException) {
            // Expected unique constraint violation
            assertNotNull(e.message)
        }

        db.close()
    }
}
