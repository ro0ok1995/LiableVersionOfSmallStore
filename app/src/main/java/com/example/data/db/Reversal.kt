package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 7 Accounting Plan: Reversal Record.
 *
 * Represents an immutable, auditable financial reversal.
 * Enforces "One reversal only" at the database level via a unique index on originalTransactionId.
 */
@Entity(
    tableName = "reversals",
    indices = [
        Index(value = ["originalTransactionId"], unique = true)
    ]
)
data class Reversal(
    @PrimaryKey
    val id: String,
    val originalTransactionId: String,
    val reason: String,
    val reversedAt: String,
    val status: String = "ACTIVE"
) {
    init {
        require(id.isNotBlank()) { "Reversal id must not be blank" }
        require(originalTransactionId.isNotBlank()) { "originalTransactionId must not be blank" }
        require(reason.isNotBlank()) { "Reversal reason must not be blank" }
        require(reversedAt.isNotBlank()) { "reversedAt must not be blank" }
    }
}
