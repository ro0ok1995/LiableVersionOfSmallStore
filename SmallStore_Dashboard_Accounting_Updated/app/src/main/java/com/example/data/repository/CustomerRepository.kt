package com.example.data.repository

import com.example.data.db.CustomerDao
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.toEntity
import com.example.data.db.toModel
import com.example.model.CustomerAccount

class CustomerRepository(
    private val customerDao: CustomerDao
) {
    constructor(database: SmallStoreDatabase) : this(database.customerDao())

    suspend fun addCustomer(customer: CustomerAccount) {
        customerDao.insertCustomer(customer.toEntity())
    }

    suspend fun updateCustomer(customer: CustomerAccount) {
        customerDao.updateCustomer(customer.toEntity())
    }

    suspend fun archiveCustomer(id: String, archivedDate: String) {
        customerDao.archiveCustomer(id, archivedDate)
    }

    suspend fun restoreCustomer(id: String) {
        customerDao.unarchiveCustomer(id)
    }

    suspend fun deleteCustomerPermanently(id: String) {
        customerDao.deleteCustomerById(id)
    }

    suspend fun getCustomerById(id: String): CustomerAccount? {
        return customerDao.getCustomerById(id)?.toModel()
    }

    suspend fun getCustomerCount(): Int {
        return customerDao.getCustomerCount()
    }
}
