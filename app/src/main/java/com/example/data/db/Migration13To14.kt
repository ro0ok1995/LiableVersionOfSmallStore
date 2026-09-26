package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Phase 9 Room Migration (v13 -> v14):
 * 1. Creates `suppliers` table with indices.
 * 2. Creates `purchases` table with foreign keys and indices.
 * 3. Creates `purchase_lines` table with foreign keys and indices.
 * 4. Creates `supplier_payments` table with foreign keys and indices.
 * 5. Creates `purchase_returns` table with foreign keys and indices.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `suppliers` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `phone` TEXT NOT NULL,
                `email` TEXT,
                `address` TEXT,
                `notes` TEXT,
                `isArchived` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_name` ON `suppliers` (`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_isArchived` ON `suppliers` (`isArchived`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `purchases` (
                `id` TEXT NOT NULL,
                `invoiceNumber` TEXT NOT NULL,
                `supplierId` TEXT NOT NULL,
                `purchaseDate` TEXT NOT NULL,
                `totalAmount` REAL NOT NULL,
                `paidAmount` REAL NOT NULL,
                `creditAmount` REAL NOT NULL,
                `paymentStatus` TEXT NOT NULL,
                `paymentMethodId` TEXT,
                `financialAccountId` TEXT,
                `notes` TEXT,
                `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchases_supplierId` ON `purchases` (`supplierId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchases_purchaseDate` ON `purchases` (`purchaseDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchases_status` ON `purchases` (`status`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `purchase_lines` (
                `id` TEXT NOT NULL,
                `purchaseId` TEXT NOT NULL,
                `productId` TEXT,
                `productNameSnapshot` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `unitCost` REAL NOT NULL,
                `subtotal` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_lines_purchaseId` ON `purchase_lines` (`purchaseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_lines_productId` ON `purchase_lines` (`productId`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `supplier_payments` (
                `id` TEXT NOT NULL,
                `supplierId` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `paymentDate` TEXT NOT NULL,
                `paymentMethodId` TEXT,
                `financialAccountId` TEXT,
                `referenceNumber` TEXT,
                `notes` TEXT,
                `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payments_supplierId` ON `supplier_payments` (`supplierId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payments_paymentDate` ON `supplier_payments` (`paymentDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payments_status` ON `supplier_payments` (`status`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `purchase_returns` (
                `id` TEXT NOT NULL,
                `purchaseId` TEXT NOT NULL,
                `supplierId` TEXT NOT NULL,
                `returnDate` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `reason` TEXT NOT NULL,
                `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_returns_purchaseId` ON `purchase_returns` (`purchaseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_returns_supplierId` ON `purchase_returns` (`supplierId`)")
    }
}
