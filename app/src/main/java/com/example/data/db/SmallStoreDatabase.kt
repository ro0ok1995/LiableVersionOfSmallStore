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
        StoreInfoEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SmallStoreDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transactionItemLineDao(): TransactionItemLineDao
    abstract fun notificationDao(): NotificationDao
    abstract fun storeInfoDao(): StoreInfoDao

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

        fun getDatabase(context: Context): SmallStoreDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmallStoreDatabase::class.java,
                    "smallstore_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
