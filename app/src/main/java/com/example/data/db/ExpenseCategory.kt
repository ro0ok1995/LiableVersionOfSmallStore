package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 10 Expenses:
 * Authoritative [ExpenseCategory] Entity.
 */
@Entity(
    tableName = "expense_categories",
    indices = [
        Index("name"),
        Index("isActive")
    ]
)
data class ExpenseCategory(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Expense category ID cannot be blank" }
        require(name.isNotBlank()) { "Expense category name cannot be blank" }
    }
}
