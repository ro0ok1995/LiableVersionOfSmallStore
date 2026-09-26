package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.PaymentMethodType

@Entity(tableName = "financial_accounts")
data class FinancialAccount(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val typedType: PaymentMethodType
        get() = try {
            PaymentMethodType.valueOf(type)
        } catch (_: Exception) {
            PaymentMethodType.OTHER
        }

    init {
        require(id.isNotBlank()) { "Financial account ID cannot be blank" }
        require(name.isNotBlank()) { "Financial account name cannot be blank" }
        require(try { PaymentMethodType.valueOf(type); true } catch (_: Exception) { false }) {
            "Invalid financial account type: $type. Must match PaymentMethodType."
        }
    }
}
