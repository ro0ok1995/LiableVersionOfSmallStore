package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 3 -> 4:
 * 1. Add nullable `customerId` column to `transactions` table.
 * 2. Deterministically resolve existing transactions to customers where unambiguous:
 *    - An exact customerName match to exactly ONE customer in `customers` table.
 *    - When ambiguous (multiple customers with same name) or no matching customer:
 *      leave customerId NULL (or preserved for conflict review) without guessing.
 * 3. Create index on `transactions.customerId`.
 * 4. Ensure foreign key constraint integrity. In SQLite/Room, adding a column with foreign key
 *    or creating the index on customerId allows proper indexing and relational integrity.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: Add customerId column
        db.execSQL("ALTER TABLE transactions ADD COLUMN customerId TEXT DEFAULT NULL")

        // Step 2: Deterministic backfill:
        // Update customerId only where customerName matches EXACTLY ONE customer in the customers table.
        // If multiple customers share the same name, or no customer matches, customerId remains NULL.
        db.execSQL("""
            UPDATE transactions
            SET customerId = (
                SELECT id FROM customers
                WHERE customers.customerName = transactions.customerName
                GROUP BY customerName
                HAVING COUNT(*) = 1
            )
            WHERE transactions.customerName IS NOT NULL
              AND transactions.customerName != ''
              AND transactions.customerName != 'عميل كاش'
              AND transactions.customerName != 'عميل عام'
              AND transactions.customerName != 'عميل نقدي'
              AND (
                  SELECT COUNT(*) FROM customers
                  WHERE customers.customerName = transactions.customerName
              ) = 1
        """.trimIndent())

        // Step 3: Create index on customerId
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_customerId ON transactions(customerId)")
    }
}
