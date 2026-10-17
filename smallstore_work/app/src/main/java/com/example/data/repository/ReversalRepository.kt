package com.example.data.repository

import com.example.data.db.Reversal
import com.example.data.db.ReversalDao
import com.example.data.db.SmallStoreDatabase
import kotlinx.coroutines.flow.Flow

class ReversalRepository(
    private val reversalDao: ReversalDao
) {
    constructor(database: SmallStoreDatabase) : this(database.reversalDao())

    val allReversals: Flow<List<Reversal>> = reversalDao.getAllReversals()

    suspend fun getAllReversalsSync(): List<Reversal> = reversalDao.getAllReversalsSync()

    suspend fun getReversalForTransaction(originalTransactionId: String): Reversal? {
        return reversalDao.getReversalByOriginalTransactionId(originalTransactionId)
    }

    suspend fun isTransactionReversed(originalTransactionId: String): Boolean {
        val rev = reversalDao.getReversalByOriginalTransactionId(originalTransactionId)
        return rev != null && rev.status == "ACTIVE"
    }
}
