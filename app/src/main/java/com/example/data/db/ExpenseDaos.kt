package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Phase 10 Expenses:
 * Room DAOs for [ExpenseCategory] and [Expense].
 */
@Dao
interface ExpenseCategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: ExpenseCategory): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<ExpenseCategory>)

    @Update
    suspend fun updateCategory(category: ExpenseCategory)

    @Query("SELECT * FROM expense_categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: String): ExpenseCategory?

    @Query("SELECT * FROM expense_categories WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveCategories(): Flow<List<ExpenseCategory>>

    @Query("SELECT * FROM expense_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<ExpenseCategory>>

    @Query("SELECT * FROM expense_categories ORDER BY name ASC")
    suspend fun getAllCategoriesSync(): List<ExpenseCategory>

    @Query("SELECT COUNT(*) FROM expense_categories")
    suspend fun getCategoryCount(): Int

    @Query("DELETE FROM expense_categories")
    suspend fun deleteAllCategories()
}

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: String): Expense?

    @Query("SELECT * FROM expenses WHERE categoryId = :categoryId ORDER BY date DESC, createdAt DESC")
    fun getExpensesByCategoryId(categoryId: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE categoryId = :categoryId ORDER BY date DESC, createdAt DESC")
    suspend fun getExpensesByCategoryIdSync(categoryId: String): List<Expense>

    @Query("SELECT * FROM expenses WHERE financialAccountId = :accountId ORDER BY date DESC, createdAt DESC")
    suspend fun getExpensesByAccountIdSync(accountId: String): List<Expense>

    @Query("SELECT * FROM expenses ORDER BY date DESC, createdAt DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY date DESC, createdAt DESC")
    suspend fun getAllExpensesSync(): List<Expense>

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun getExpenseCount(): Int

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
}
