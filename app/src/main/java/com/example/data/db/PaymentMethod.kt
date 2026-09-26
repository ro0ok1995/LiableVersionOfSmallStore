package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.PaymentMethodType

@Entity(tableName = "payment_methods")
data class PaymentMethod(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val typedType: PaymentMethodType
        get() = try {
            PaymentMethodType.valueOf(type)
        } catch (_: Exception) {
            PaymentMethodType.OTHER
        }

    init {
        require(id.isNotBlank()) { "Payment method ID cannot be blank" }
        require(name.isNotBlank()) { "Payment method name cannot be blank" }
        require(try { PaymentMethodType.valueOf(type); true } catch (_: Exception) { false }) {
            "Invalid payment method type: $type. Must match PaymentMethodType."
        }
    }
}
