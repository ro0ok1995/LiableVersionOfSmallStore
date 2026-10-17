package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 9 Suppliers / Purchases:
 * Authoritative [Supplier] Entity.
 *
 * Invariants:
 * 1. Stable, ID-based identity (e.g. "sup_...").
 * 2. Name is NOT used as foreign key or accounting key.
 * 3. Supports contact details (phone, email, address, notes) and isArchived.
 */
@Entity(
    tableName = "suppliers",
    indices = [
        Index("name"),
        Index("isArchived")
    ]
)
data class Supplier(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Supplier id must not be blank" }
        require(name.isNotBlank()) { "Supplier name must not be blank" }
    }
}
