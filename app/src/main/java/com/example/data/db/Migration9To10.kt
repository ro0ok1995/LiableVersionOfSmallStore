package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `opening_balances` (
                `id` TEXT NOT NULL,
                `entityType` TEXT NOT NULL,
                `entityId` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `direction` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `reason` TEXT,
                `reference` TEXT,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_opening_balances_entityType_entityId` ON `opening_balances` (`entityType`, `entityId`)")
    }
}
