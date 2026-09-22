package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.ConflictReason
import com.example.model.ConflictResolutionStatus
import com.example.model.CustomerConflictItem

/**
 * Room entity for persistent customer identity conflicts.
 *
 * Preserves audit information for unassigned transactions that could not be
 * deterministically resolved during migration or accounting reconciliation.
 *
 * Conflict rows are NEVER deleted upon resolution, guaranteeing full auditability.
 */
@Entity(
    tableName = "customer_identity_conflicts",
    indices = [
        Index("transactionId"),
        Index("resolutionStatus")
    ]
)
data class CustomerIdentityConflictEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val originalCustomerName: String,
    val conflictReason: String,
    val createdAt: String,
    val resolutionStatus: String,
    val resolvedCustomerId: String? = null,
    val resolvedAt: String? = null,
    val notes: String? = null
)

fun CustomerIdentityConflictEntity.toModel(): CustomerConflictItem = CustomerConflictItem(
    id = id,
    transactionId = transactionId,
    originalCustomerName = originalCustomerName,
    conflictReason = runCatching { ConflictReason.valueOf(conflictReason) }
        .getOrDefault(ConflictReason.OTHER_UNRESOLVED_IDENTITY),
    createdAt = createdAt,
    resolutionStatus = runCatching { ConflictResolutionStatus.valueOf(resolutionStatus) }
        .getOrDefault(ConflictResolutionStatus.UNRESOLVED),
    resolvedCustomerId = resolvedCustomerId,
    resolvedAt = resolvedAt,
    notes = notes
)

fun CustomerConflictItem.toEntity(): CustomerIdentityConflictEntity = CustomerIdentityConflictEntity(
    id = id,
    transactionId = transactionId,
    originalCustomerName = originalCustomerName,
    conflictReason = conflictReason.name,
    createdAt = createdAt,
    resolutionStatus = resolutionStatus.name,
    resolvedCustomerId = resolvedCustomerId,
    resolvedAt = resolvedAt,
    notes = notes
)
