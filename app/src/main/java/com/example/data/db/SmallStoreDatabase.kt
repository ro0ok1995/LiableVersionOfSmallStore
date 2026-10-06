package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CustomerEntity::class,
        ProductEntity::class,
        TransactionEntity::class,
        TransactionItemLineEntity::class,
        NotificationEntity::class,
        StoreInfoEntity::class,
        CustomerIdentityConflictEntity::class,
        Sale::class,
        SaleLine::class,
        FinancialAccount::class,
        PaymentMethod::class,
        CustomerPayment::class,
        OpeningBalance::class,
        Adjustment::class,
        Reversal::class,
        SaleReturn::class,
        SaleReturnLine::class,
        Refund::class,
        Supplier::class,
        Purchase::class,
        PurchaseLine::class,
        SupplierPayment::class,
        PurchaseReturn::class,
        PurchaseReturnLine::class,
        ExpenseCategory::class,
        Expense::class,
        StockMovementEntity::class
    ],
    version = 19,
    exportSchema = false
)
abstract class SmallStoreDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transactionItemLineDao(): TransactionItemLineDao
    abstract fun notificationDao(): NotificationDao
    abstract fun storeInfoDao(): StoreInfoDao
    abstract fun customerConflictDao(): CustomerConflictDao
    abstract fun saleDao(): SaleDao
    abstract fun financialAccountDao(): FinancialAccountDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun customerPaymentDao(): CustomerPaymentDao
    abstract fun openingBalanceDao(): OpeningBalanceDao
    abstract fun adjustmentDao(): AdjustmentDao
    abstract fun reversalDao(): ReversalDao
    abstract fun saleReturnDao(): SaleReturnDao
    abstract fun saleReturnLineDao(): SaleReturnLineDao
    abstract fun refundDao(): RefundDao
    abstract fun supplierDao(): SupplierDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun purchaseLineDao(): PurchaseLineDao
    abstract fun supplierPaymentDao(): SupplierPaymentDao
    abstract fun purchaseReturnDao(): PurchaseReturnDao
    abstract fun purchaseReturnLineDao(): PurchaseReturnLineDao
    abstract fun expenseCategoryDao(): ExpenseCategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun stockMovementDao(): StockMovementDao

    companion object {
        @Volatile
        private var INSTANCE: SmallStoreDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN imageUri TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE customers ADD COLUMN archivedDate TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE products ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN archivedDate TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transactions ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN archivedDate TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sales ADD COLUMN financialAccountId TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS purchase_return_lines (
                        id TEXT NOT NULL PRIMARY KEY,
                        purchaseReturnId TEXT NOT NULL,
                        purchaseLineId TEXT NOT NULL,
                        productId TEXT,
                        productNameSnapshot TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        unitCost REAL NOT NULL,
                        subtotal REAL NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(purchaseReturnId) REFERENCES purchase_returns(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(purchaseLineId) REFERENCES purchase_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_return_lines_purchaseReturnId ON purchase_return_lines(purchaseReturnId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_return_lines_purchaseLineId ON purchase_return_lines(purchaseLineId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_return_lines_productId ON purchase_return_lines(productId)")
            }
        }

        fun getDatabase(context: Context): SmallStoreDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmallStoreDatabase::class.java,
                    "smallstore_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_1_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
