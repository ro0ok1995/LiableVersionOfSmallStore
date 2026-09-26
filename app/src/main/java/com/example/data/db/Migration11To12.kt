package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Phase 7 Room Migration (v11 -> v12):
 * 1. Creates `reversals` table with unique index on `originalTransactionId`.
 * 2. Adds `status` column to `adjustments` table.
 * 3. Adds `operationStatus` column to `transactions` table.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `reversals` (
                `id` TEXT NOT NULL,
                `originalTransactionId` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `reversedAt` TEXT NOT NULL,
                `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reversals_originalTransactionId` ON `reversals` (`originalTransactionId`)")

        db.execSQL("ALTER TABLE `adjustments` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'ACTIVE'")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `operationStatus` TEXT NOT NULL DEFAULT 'ACTIVE'")
    }
}
