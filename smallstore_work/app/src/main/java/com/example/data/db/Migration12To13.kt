package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Phase 8 Room Migration (v12 -> v13):
 * 1. Creates `sale_returns` table with indices.
 * 2. Creates `sale_return_lines` table with foreign keys and indices.
 * 3. Creates `refunds` table with indices.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sale_returns` (
                `id` TEXT NOT NULL,
                `saleId` TEXT NOT NULL,
                `customerId` TEXT,
                `returnDate` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_returns_saleId` ON `sale_returns` (`saleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_returns_customerId` ON `sale_returns` (`customerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_returns_returnDate` ON `sale_returns` (`returnDate`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sale_return_lines` (
                `id` TEXT NOT NULL,
                `saleReturnId` TEXT NOT NULL,
                `saleLineId` TEXT NOT NULL,
                `productId` TEXT,
                `productNameSnapshot` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `unitPrice` REAL NOT NULL,
                `costPriceAtReturn` REAL NOT NULL,
                `subtotal` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`saleReturnId`) REFERENCES `sale_returns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`saleLineId`) REFERENCES `sale_lines`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_lines_saleReturnId` ON `sale_return_lines` (`saleReturnId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_lines_saleLineId` ON `sale_return_lines` (`saleLineId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_lines_productId` ON `sale_return_lines` (`productId`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `refunds` (
                `id` TEXT NOT NULL,
                `saleReturnId` TEXT,
                `saleId` TEXT,
                `customerId` TEXT,
                `amount` REAL NOT NULL,
                `paymentMethodId` TEXT,
                `financialAccountId` TEXT,
                `refundDate` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_refunds_saleReturnId` ON `refunds` (`saleReturnId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_refunds_saleId` ON `refunds` (`saleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_refunds_customerId` ON `refunds` (`customerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_refunds_financialAccountId` ON `refunds` (`financialAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_refunds_refundDate` ON `refunds` (`refundDate`)")
    }
}
