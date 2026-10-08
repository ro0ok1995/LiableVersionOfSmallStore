package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Phase 11B Room Migration (v17 -> v18):
 * Adds nullable `financialAccountId` column to `sales` table
 * for cash/bank/wallet financial account tracking.
 */
val MIGRATION_17_18: Migration = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sales ADD COLUMN financialAccountId TEXT DEFAULT NULL")
    }
}
