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
        StockMovementEntity::class,
        SupplierCatalogItemEntity::class
    ],
    version = 22,
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
    abstract fun supplierCatalogDao(): SupplierCatalogDao

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

        val MIGRATION_17_18 = com.example.data.db.MIGRATION_17_18
        val MIGRATION_18_19 = com.example.data.db.MIGRATION_18_19
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sale_returns ADD COLUMN reasonCode TEXT NOT NULL DEFAULT 'other'")
                db.execSQL("ALTER TABLE sale_returns ADD COLUMN reasonLabelSnapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE sale_returns SET reasonLabelSnapshot = reason WHERE reasonLabelSnapshot = ''")
                db.execSQL("ALTER TABLE reversals ADD COLUMN reasonCode TEXT NOT NULL DEFAULT 'other'")
                db.execSQL("ALTER TABLE reversals ADD COLUMN reasonLabelSnapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE reversals SET reasonLabelSnapshot = reason WHERE reasonLabelSnapshot = ''")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE purchase_returns ADD COLUMN payableReductionAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE purchase_returns ADD COLUMN cashRefundAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE purchase_returns ADD COLUMN financialAccountId TEXT DEFAULT NULL")
                // Existing returns historically reduced the supplier payable by the full amount.
                db.execSQL("UPDATE purchase_returns SET payableReductionAmount = amount, cashRefundAmount = 0.0, financialAccountId = NULL")
            }
        }

        val MIGRATION_21_22 = com.example.data.db.MIGRATION_21_22

        @Volatile
        private var DEMO_INSTANCE: SmallStoreDatabase? = null

        fun getDatabase(context: Context, databaseName: String = if (com.example.data.demo.DemoConfig.isDemoMode(context)) com.example.data.demo.DemoConfig.DEMO_DATABASE_NAME else com.example.data.demo.DemoConfig.PRODUCTION_DATABASE_NAME): SmallStoreDatabase {
            val isDemo = databaseName == com.example.data.demo.DemoConfig.DEMO_DATABASE_NAME
            if (isDemo) {
                return DEMO_INSTANCE ?: synchronized(this) {
                    DEMO_INSTANCE ?: buildDatabase(context, databaseName).also { DEMO_INSTANCE = it }
                }
            } else {
                return INSTANCE ?: synchronized(this) {
                    INSTANCE ?: buildDatabase(context, databaseName).also { INSTANCE = it }
                }
            }
        }

        private fun buildDatabase(context: Context, databaseName: String): SmallStoreDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                SmallStoreDatabase::class.java,
                databaseName
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
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22
                )
                .build()
        }
    }
}
