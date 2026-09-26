package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Phase 11 Room Migration (v15 -> v16):
 * Creates `purchase_return_lines` table with foreign key and indices.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `purchase_return_lines` (
                `id` TEXT NOT NULL,
                `purchaseReturnId` TEXT NOT NULL,
                `productId` TEXT,
                `productNameSnapshot` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `costPriceAtReturn` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`purchaseReturnId`) REFERENCES `purchase_returns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_return_lines_purchaseReturnId` ON `purchase_return_lines` (`purchaseReturnId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_return_lines_productId` ON `purchase_return_lines` (`productId`)")
    }
}
