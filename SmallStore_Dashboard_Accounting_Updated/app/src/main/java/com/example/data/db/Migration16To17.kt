package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Phase 11A Room Migration (v16 -> v17):
 * Creates the persistent `stock_movements` table and its indexes
 * for perpetual inventory ledger tracking.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `stock_movements` (
                `id` TEXT NOT NULL,
                `productId` TEXT NOT NULL,
                `productNameSnapshot` TEXT NOT NULL DEFAULT '',
                `transactionId` TEXT NOT NULL DEFAULT '',
                `lineId` TEXT NOT NULL DEFAULT '',
                `date` TEXT NOT NULL DEFAULT '',
                `timestamp` INTEGER NOT NULL,
                `movementType` TEXT NOT NULL,
                `quantityIn` INTEGER NOT NULL DEFAULT 0,
                `quantityOut` INTEGER NOT NULL DEFAULT 0,
                `unitCost` REAL NOT NULL DEFAULT 0.0,
                `reference` TEXT NOT NULL DEFAULT '',
                `referenceType` TEXT NOT NULL DEFAULT '',
                `referenceId` TEXT NOT NULL DEFAULT '',
                `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_productId` ON `stock_movements` (`productId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_timestamp` ON `stock_movements` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_productId_timestamp` ON `stock_movements` (`productId`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_transactionId` ON `stock_movements` (`transactionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_status` ON `stock_movements` (`status`)")
    }
}
