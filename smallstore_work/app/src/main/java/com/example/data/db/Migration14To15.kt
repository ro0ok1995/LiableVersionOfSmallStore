package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Phase 10 Room Migration (v14 -> v15):
 * 1. Creates `expense_categories` table with indices.
 * 2. Creates `expenses` table with foreign key and indices.
 * 3. Seeds default expense categories.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `expense_categories` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `isActive` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_categories_name` ON `expense_categories` (`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_categories_isActive` ON `expense_categories` (`isActive`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `expenses` (
                `id` TEXT NOT NULL,
                `categoryId` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `paymentMethodId` TEXT,
                `financialAccountId` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`categoryId`) REFERENCES `expense_categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_categoryId` ON `expenses` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_financialAccountId` ON `expenses` (`financialAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_status` ON `expenses` (`status`)")

        // Seed default expense categories
        val now = System.currentTimeMillis()
        db.execSQL("""
            INSERT OR IGNORE INTO `expense_categories` (`id`, `name`, `description`, `isActive`, `createdAt`) VALUES
            ('exp_cat_rent', 'Rent / إيجار', 'Store rent', 1, $now),
            ('exp_cat_utilities', 'Utilities / فواتير', 'Electricity, water, internet', 1, $now),
            ('exp_cat_salaries', 'Salaries / رواتب', 'Employee salaries', 1, $now),
            ('exp_cat_general', 'General Expenses / مصاريف عامة', 'General operational expenses', 1, $now)
        """.trimIndent())
    }
}
