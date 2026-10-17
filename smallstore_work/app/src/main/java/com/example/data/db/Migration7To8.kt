package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 7 -> 8:
 * Phase 3 Accounting Refactor: Sales Engine Foundations.
 *
 * Invariants & Schema Architecture:
 * 1. Creates `sales` table as first-class financial entity for sales operations.
 *    - Invariant: totalAmount = paidAmount + creditAmount.
 *    - Sequential human-readable invoiceNumber (INV-XXXXXX) separate from UUID/timestamp PK.
 *    - customerId FK references customers(id) with ON DELETE RESTRICT (preserving customer accounting).
 *    - Indexed on (customerId) and (customerId, transactionDate) for Customer Ledger performance.
 *    - Unique index on invoiceNumber.
 * 2. Creates `sale_lines` table for line-item snapshots.
 *    - saleId FK references sales(id) with ON DELETE CASCADE.
 *    - costPriceAtSale freezes historical cost at moment of sale (never mutated when product cost changes).
 *    - subtotal = quantity * unitPrice.
 *    - Indexed on (saleId) and (productId).
 * 3. Preserves all existing tables (customers, products, transactions, etc.) without data loss.
 * 4. Existing transaction and customer records remain completely intact.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: Create sales table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sales` (
                `id` TEXT NOT NULL,
                `invoiceNumber` TEXT NOT NULL,
                `customerId` TEXT,
                `saleType` TEXT NOT NULL,
                `totalAmount` REAL NOT NULL,
                `paidAmount` REAL NOT NULL,
                `creditAmount` REAL NOT NULL,
                `paymentStatus` TEXT NOT NULL,
                `transactionDate` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())

        // Step 2: Create sales indices
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_customerId` ON `sales` (`customerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_customerId_transactionDate` ON `sales` (`customerId`, `transactionDate`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_invoiceNumber` ON `sales` (`invoiceNumber`)")

        // Step 3: Create sale_lines table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sale_lines` (
                `id` TEXT NOT NULL,
                `saleId` TEXT NOT NULL,
                `productId` TEXT,
                `productNameSnapshot` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `unitPrice` REAL NOT NULL,
                `costPriceAtSale` REAL NOT NULL,
                `subtotal` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        // Step 4: Create sale_lines indices
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_lines_saleId` ON `sale_lines` (`saleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_lines_productId` ON `sale_lines` (`productId`)")
    }
}
