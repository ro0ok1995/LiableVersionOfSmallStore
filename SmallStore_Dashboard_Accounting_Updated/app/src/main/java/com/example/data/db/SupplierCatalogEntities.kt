package com.example.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "supplier_catalog_items",
    primaryKeys = ["supplierId", "productId"],
    foreignKeys = [
        ForeignKey(
            entity = Supplier::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("supplierId"), Index("productId")]
)
data class SupplierCatalogItemEntity(
    val supplierId: String,
    val productId: String,
    val lastUnitCost: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface SupplierCatalogDao {
    @Query("SELECT * FROM supplier_catalog_items WHERE supplierId = :supplierId")
    fun getCatalogForSupplier(supplierId: String): Flow<List<SupplierCatalogItemEntity>>

    @Query("SELECT * FROM supplier_catalog_items WHERE supplierId = :supplierId")
    suspend fun getCatalogForSupplierSync(supplierId: String): List<SupplierCatalogItemEntity>

    @Query("SELECT * FROM supplier_catalog_items")
    fun getAllSupplierCatalogItems(): Flow<List<SupplierCatalogItemEntity>>

    @Query("SELECT * FROM supplier_catalog_items")
    suspend fun getAllSupplierCatalogItemsSync(): List<SupplierCatalogItemEntity>

    @Query("SELECT * FROM supplier_catalog_items WHERE supplierId = :supplierId AND productId = :productId")
    suspend fun getSupplierCatalogItem(supplierId: String, productId: String): SupplierCatalogItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSupplierItem(item: SupplierCatalogItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSupplierItems(items: List<SupplierCatalogItemEntity>)

    @Query("DELETE FROM supplier_catalog_items WHERE supplierId = :supplierId AND productId = :productId")
    suspend fun unlinkSupplierItem(supplierId: String, productId: String)
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `products` ADD COLUMN `isCatalogItem` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `isRawMaterial` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS `supplier_catalog_items` (`supplierId` TEXT NOT NULL, `productId` TEXT NOT NULL, `lastUnitCost` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`supplierId`, `productId`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_catalog_items_supplierId` ON `supplier_catalog_items` (`supplierId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_catalog_items_productId` ON `supplier_catalog_items` (`productId`)")
    }
}


/** Adds referential integrity to the non-historical supplier/product catalog links.
 * Orphan links from older databases are removed; purchase, sale and stock history is untouched.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM supplier_catalog_items WHERE supplierId NOT IN (SELECT id FROM suppliers) OR productId NOT IN (SELECT id FROM products)")
        db.execSQL("CREATE TABLE IF NOT EXISTS supplier_catalog_items_new (supplierId TEXT NOT NULL, productId TEXT NOT NULL, lastUnitCost REAL NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(supplierId, productId), FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("INSERT INTO supplier_catalog_items_new (supplierId, productId, lastUnitCost, updatedAt) SELECT supplierId, productId, lastUnitCost, updatedAt FROM supplier_catalog_items")
        db.execSQL("DROP TABLE supplier_catalog_items")
        db.execSQL("ALTER TABLE supplier_catalog_items_new RENAME TO supplier_catalog_items")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_catalog_items_supplierId ON supplier_catalog_items (supplierId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_catalog_items_productId ON supplier_catalog_items (productId)")
    }
}
