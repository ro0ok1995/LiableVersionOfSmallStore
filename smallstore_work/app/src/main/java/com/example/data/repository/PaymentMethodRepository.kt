package com.example.data.repository

import com.example.data.db.PaymentMethod
import com.example.data.db.PaymentMethodDao
import com.example.data.db.SmallStoreDatabase
import kotlinx.coroutines.flow.Flow

class PaymentMethodRepository(
    private val paymentMethodDao: PaymentMethodDao
) {
    constructor(database: SmallStoreDatabase) : this(database.paymentMethodDao())

    val allPaymentMethods: Flow<List<PaymentMethod>> = paymentMethodDao.getAllPaymentMethods()

    suspend fun createPaymentMethod(paymentMethod: PaymentMethod) {
        paymentMethodDao.insertPaymentMethod(paymentMethod)
    }

    suspend fun getPaymentMethodById(id: String): PaymentMethod? {
        return paymentMethodDao.getPaymentMethodById(id)
    }

    fun getActivePaymentMethods(): Flow<List<PaymentMethod>> {
        return paymentMethodDao.getActivePaymentMethods()
    }

    suspend fun getAllPaymentMethodsSync(): List<PaymentMethod> {
        return paymentMethodDao.getAllPaymentMethodsSync()
    }

    suspend fun updatePaymentMethod(paymentMethod: PaymentMethod) {
        paymentMethodDao.updatePaymentMethod(paymentMethod)
    }
}
