package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 5 -> 6:
 * Phase 2.6 Accounting Decoupling:
 * Separate persistent customer identity from historical customer-name presentation.
 *
 * Invariants:
 * 1. Introduce `customerNameSnapshot: String` in `transactions` table as the historical display name
 *    captured at transaction creation time.
 * 2. `customerNameSnapshot` is NEVER used as a relational identity, customer lookup key, balance
 *    calculation key, ledger grouping key, or transaction ownership key.
 * 3. `customerId` remains the ONLY relational customer identity.
 * 4. Migrate existing `customerName` data into `customerNameSnapshot` without data loss.
 * 5. Legacy `customerName` column is preserved in the database for backward compatibility.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: Add customerNameSnapshot column with default empty string
        db.execSQL("ALTER TABLE transactions ADD COLUMN customerNameSnapshot TEXT NOT NULL DEFAULT ''")

        // Step 2: Migrate existing customerName data into customerNameSnapshot without data loss
        db.execSQL("""
            UPDATE transactions 
            SET customerNameSnapshot = customerName 
            WHERE customerName IS NOT NULL
        """.trimIndent())
    }
}
