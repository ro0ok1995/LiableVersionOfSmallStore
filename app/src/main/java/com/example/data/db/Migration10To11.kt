package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `adjustments` (
                `id` TEXT NOT NULL,
                `entityType` TEXT NOT NULL,
                `entityId` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `direction` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `reference` TEXT,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_adjustments_entityType_entityId` ON `adjustments` (`entityType`, `entityId`)")
    }
}
