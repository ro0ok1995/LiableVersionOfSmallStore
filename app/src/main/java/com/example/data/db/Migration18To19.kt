package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Phase 12 Room Migration (v18 -> v19):
 * Creates `purchase_return_lines` table with foreign keys and indices
 * for line-level purchase return tracking.
 */
val MIGRATION_18_19: Migration = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `purchase_return_lines` (
                `id` TEXT NOT NULL,
                `purchaseReturnId` TEXT NOT NULL,
                `purchaseLineId` TEXT NOT NULL,
                `productId` TEXT,
                `productNameSnapshot` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `unitCost` REAL NOT NULL,
                `subtotal` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`purchaseReturnId`) REFERENCES `purchase_returns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`purchaseLineId`) REFERENCES `purchase_lines`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_return_lines_purchaseReturnId` ON `purchase_return_lines` (`purchaseReturnId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_return_lines_purchaseLineId` ON `purchase_return_lines` (`purchaseLineId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_return_lines_productId` ON `purchase_return_lines` (`productId`)")
    }
}
