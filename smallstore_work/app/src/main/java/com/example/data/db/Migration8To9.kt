package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create financial_accounts table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `financial_accounts` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        // 2. Create payment_methods table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `payment_methods` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        // 3. Create customer_payments table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `customer_payments` (
                `id` TEXT NOT NULL,
                `customerId` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `paymentMethodId` TEXT NOT NULL,
                `financialAccountId` TEXT,
                `reference` TEXT,
                `transactionDate` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `notes` TEXT,
                `status` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`paymentMethodId`) REFERENCES `payment_methods`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`financialAccountId`) REFERENCES `financial_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())

        // Indices
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_payments_customerId` ON `customer_payments` (`customerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_payments_customerId_transactionDate` ON `customer_payments` (`customerId`, `transactionDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_payments_paymentMethodId` ON `customer_payments` (`paymentMethodId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_payments_financialAccountId` ON `customer_payments` (`financialAccountId`)")

        // 4. Seed financial_accounts: Cash, Bank, Card Settlement, E-Wallet (isActive=1)
        val now = System.currentTimeMillis()
        db.execSQL("""
            INSERT OR IGNORE INTO `financial_accounts` (`id`, `name`, `type`, `isActive`, `createdAt`, `updatedAt`) VALUES
            ('acc_cash', 'Cash', 'CASH', 1, $now, $now),
            ('acc_bank', 'Bank', 'BANK', 1, $now, $now),
            ('acc_card', 'Card Settlement', 'CARD', 1, $now, $now),
            ('acc_wallet', 'E-Wallet', 'E_WALLET', 1, $now, $now)
        """.trimIndent())

        // 5. Seed payment_methods matching the same four types
        db.execSQL("""
            INSERT OR IGNORE INTO `payment_methods` (`id`, `name`, `type`, `isActive`, `createdAt`) VALUES
            ('pm_cash', 'Cash', 'CASH', 1, $now),
            ('pm_bank', 'Bank', 'BANK', 1, $now),
            ('pm_card', 'Card Settlement', 'CARD', 1, $now),
            ('pm_wallet', 'E-Wallet', 'E_WALLET', 1, $now)
        """.trimIndent())
    }
}
