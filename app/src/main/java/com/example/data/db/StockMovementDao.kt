package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockMovement(movement: StockMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovement(movement: StockMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockMovements(movements: List<StockMovementEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovements(movements: List<StockMovementEntity>)

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY timestamp ASC, id ASC")
    fun getMovementsByProduct(productId: String): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY timestamp ASC, id ASC")
    fun getMovementsByProductId(productId: String): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY timestamp ASC, id ASC")
    suspend fun getMovementsByProductSync(productId: String): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY timestamp ASC, id ASC")
    suspend fun getMovementsByProductIdSync(productId: String): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements ORDER BY timestamp ASC, id ASC")
    fun getAllMovementsChronological(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY timestamp ASC, id ASC")
    fun getAllMovements(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY timestamp ASC, id ASC")
    suspend fun getAllMovementsChronologicalSync(): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements ORDER BY timestamp ASC, id ASC")
    suspend fun getAllMovementsSync(): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC, timestamp ASC, id ASC")
    fun getMovementsByDateRange(startDate: String, endDate: String): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC, timestamp ASC, id ASC")
    suspend fun getMovementsByDateRangeSync(startDate: String, endDate: String): List<StockMovementEntity>

    @Query("SELECT COUNT(*) FROM stock_movements")
    suspend fun getMovementCount(): Int

    @Query("SELECT COUNT(*) FROM stock_movements WHERE productId = :productId")
    suspend fun getMovementCountByProduct(productId: String): Int

    @Query("SELECT * FROM stock_movements WHERE id = :id LIMIT 1")
    suspend fun getMovementById(id: String): StockMovementEntity?

    @Query("SELECT * FROM stock_movements WHERE transactionId = :transactionId ORDER BY timestamp ASC")
    suspend fun getMovementsByTransactionId(transactionId: String): List<StockMovementEntity>

    @Query("DELETE FROM stock_movements")
    suspend fun deleteAllStockMovements()
}
