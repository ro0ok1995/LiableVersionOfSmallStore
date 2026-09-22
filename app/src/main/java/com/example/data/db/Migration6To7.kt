package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 6 -> 7:
 * Phase 2 Accounting Refactor:
 * Correct representation of MIXED sales and reproducible customer receivables.
 *
 * Invariants:
 * 1. Introduce `paidAmount REAL NOT NULL DEFAULT 0.0` and `creditAmount REAL NOT NULL DEFAULT 0.0`
 *    in `transactions` table.
 * 2. For a sale: totalAmount = paidAmount + creditAmount.
 * 3. Customer receivable must be reproducible from persisted financial operations through the Customer Ledger.
 * 4. Preserves all existing customer and transaction data without data loss.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN paidAmount REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN creditAmount REAL NOT NULL DEFAULT 0.0")
    }
}
