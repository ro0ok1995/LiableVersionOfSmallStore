package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.Expense
import com.example.data.db.ExpenseCategory
import com.example.data.repository.StoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExpenseViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: StoreRepository = StoreRepository.getInstance(application)
) : AndroidViewModel(application) {

    private val _expenseCategories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    val expenseCategories: StateFlow<List<ExpenseCategory>> = _expenseCategories.asStateFlow()

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    val allExpenseCategories: Flow<List<ExpenseCategory>> = repository.allExpenseCategories
    val allExpenses: Flow<List<Expense>> = repository.allExpenses

    init {
        viewModelScope.launch {
            repository.allExpenseCategories.collect { list ->
                _expenseCategories.value = list
            }
        }

        viewModelScope.launch {
            repository.allExpenses.collect { list ->
                _expenses.value = list
            }
        }
    }

    fun addExpenseCategory(
        name: String,
        description: String? = null,
        onComplete: (Result<ExpenseCategory>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val catId = "exp_cat_${System.currentTimeMillis()}"
                val category = ExpenseCategory(
                    id = catId,
                    name = name.trim(),
                    description = description?.trim(),
                    isActive = true
                )
                val inserted = repository.insertExpenseCategory(category)
                onComplete(Result.success(inserted))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            }
        }
    }

    fun insertExpenseCategory(
        category: ExpenseCategory,
        onComplete: (Result<ExpenseCategory>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val inserted = repository.insertExpenseCategory(category)
                onComplete(Result.success(inserted))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            }
        }
    }

    fun recordExpense(
        categoryId: String,
        amount: Double,
        financialAccountId: String,
        paymentMethodId: String? = null,
        date: String? = null,
        description: String,
        onComplete: (Result<Expense>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val expense = repository.recordExpense(
                    categoryId = categoryId,
                    amount = amount,
                    financialAccountId = financialAccountId,
                    paymentMethodId = paymentMethodId,
                    date = date,
                    description = description
                )
                onComplete(Result.success(expense))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            }
        }
    }

    suspend fun getAllExpenseCategories(): List<ExpenseCategory> {
        return repository.getAllExpenseCategoriesSync()
    }

    suspend fun getAllExpenses(): List<Expense> {
        return repository.getAllExpensesSync()
    }
}
