package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.Expense
import com.example.data.db.ExpenseCategory
import com.example.data.db.ExpenseCategoryDao
import com.example.data.db.ExpenseDao
import com.example.data.db.FinancialAccountDao
import com.example.data.db.PaymentMethodDao
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.TransactionDao
import com.example.data.db.toEntity
import com.example.data.db.toTransactionItem
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ExpenseRepository(
    private val database: SmallStoreDatabase,
    private val expenseCategoryDao: ExpenseCategoryDao = database.expenseCategoryDao(),
    private val expenseDao: ExpenseDao = database.expenseDao(),
    private val financialAccountDao: FinancialAccountDao = database.financialAccountDao(),
    private val paymentMethodDao: PaymentMethodDao = database.paymentMethodDao(),
    private val transactionDao: TransactionDao = database.transactionDao()
) {
    val allExpenseCategories: Flow<List<ExpenseCategory>> = expenseCategoryDao.getActiveCategories()
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()

    suspend fun insertExpenseCategory(category: ExpenseCategory): ExpenseCategory {
        require(category.id.isNotBlank()) { "Expense category ID cannot be blank" }
        require(category.name.isNotBlank()) { "Expense category name cannot be blank" }
        expenseCategoryDao.insertCategory(category)
        return category
    }

    suspend fun getExpenseCategoryById(id: String): ExpenseCategory? =
        expenseCategoryDao.getCategoryById(id)

    suspend fun getAllExpenseCategoriesSync(): List<ExpenseCategory> =
        expenseCategoryDao.getAllCategoriesSync()

    suspend fun updateExpenseCategory(category: ExpenseCategory) {
        expenseCategoryDao.updateCategory(category)
    }

    suspend fun getExpenseById(id: String): Expense? = expenseDao.getExpenseById(id)

    suspend fun getAllExpensesSync(): List<Expense> = expenseDao.getAllExpensesSync()

    suspend fun getExpensesByCategorySync(categoryId: String): List<Expense> =
        expenseDao.getExpensesByCategoryIdSync(categoryId)

    suspend fun recordExpense(
        categoryId: String,
        amount: Double,
        financialAccountId: String,
        paymentMethodId: String? = null,
        date: String? = null,
        description: String,
        id: String? = null
    ): Expense = database.withTransaction {
        require(amount > 0.0) { "Expense amount ($amount) must be strictly greater than zero" }
        require(description.isNotBlank()) { "Expense description cannot be blank" }

        val category = expenseCategoryDao.getCategoryById(categoryId)
            ?: throw IllegalArgumentException("Expense category not found: $categoryId")

        val financialAccount = financialAccountDao.getAccountById(financialAccountId)
            ?: throw IllegalArgumentException("Financial account not found: $financialAccountId")

        if (!paymentMethodId.isNullOrBlank()) {
            paymentMethodDao.getPaymentMethodById(paymentMethodId)
                ?: throw IllegalArgumentException("Payment method not found: $paymentMethodId")
        }

        val dateToUse = date?.takeIf { it.isNotBlank() }
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val expenseId = id?.takeIf { it.isNotBlank() }
            ?: "exp_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

        val expense = Expense(
            id = expenseId,
            categoryId = categoryId,
            amount = amount,
            paymentMethodId = paymentMethodId,
            financialAccountId = financialAccountId,
            date = dateToUse,
            description = description.trim(),
            status = "ACTIVE"
        )

        expenseDao.insertExpense(expense)

        // Insert historical activity record into transactions table
        val txEntity = expense.toTransactionItem(category.name).toEntity()
        transactionDao.insertTransaction(txEntity)

        expense
    }

    suspend fun recordExpense(expense: Expense): Expense = database.withTransaction {
        require(expense.amount > 0.0) { "Expense amount (${expense.amount}) must be strictly greater than zero" }
        require(expense.description.isNotBlank()) { "Expense description cannot be blank" }

        val category = expenseCategoryDao.getCategoryById(expense.categoryId)
            ?: throw IllegalArgumentException("Expense category not found: ${expense.categoryId}")

        val financialAccount = financialAccountDao.getAccountById(expense.financialAccountId)
            ?: throw IllegalArgumentException("Financial account not found: ${expense.financialAccountId}")

        if (!expense.paymentMethodId.isNullOrBlank()) {
            paymentMethodDao.getPaymentMethodById(expense.paymentMethodId)
                ?: throw IllegalArgumentException("Payment method not found: ${expense.paymentMethodId}")
        }

        expenseDao.insertExpense(expense)

        val txEntity = expense.toTransactionItem(category.name).toEntity()
        transactionDao.insertTransaction(txEntity)

        expense
    }
}
