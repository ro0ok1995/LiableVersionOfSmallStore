package com.example.data.repository

import com.example.data.db.FinancialAccount
import com.example.data.db.FinancialAccountDao
import com.example.data.db.SmallStoreDatabase
import kotlinx.coroutines.flow.Flow

class FinancialAccountRepository(
    private val financialAccountDao: FinancialAccountDao
) {
    constructor(database: SmallStoreDatabase) : this(database.financialAccountDao())

    val allFinancialAccounts: Flow<List<FinancialAccount>> = financialAccountDao.getAllAccounts()

    suspend fun createFinancialAccount(account: FinancialAccount) {
        financialAccountDao.insertAccount(account)
    }

    suspend fun getFinancialAccountById(id: String): FinancialAccount? {
        return financialAccountDao.getAccountById(id)
    }

    fun getActiveFinancialAccounts(): Flow<List<FinancialAccount>> {
        return financialAccountDao.getActiveAccounts()
    }

    suspend fun getAllFinancialAccountsSync(): List<FinancialAccount> {
        return financialAccountDao.getAllAccountsSync()
    }

    suspend fun updateFinancialAccount(account: FinancialAccount) {
        financialAccountDao.updateAccount(account)
    }
}
