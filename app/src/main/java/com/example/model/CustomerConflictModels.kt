package com.example.model

/**
 * Phase 2.5: Historical Identity Migration & Accounting Conflict Models.
 *
 * Strict Accounting Safety Rule:
 * When historical customer identity is ambiguous or unverified, NEVER guess.
 * Free-form strings are never used for internal accounting decisions.
 */

/**
 * Deterministic reasons why a financial transaction could not be automatically
 * linked to a customer identity.
 */
enum class ConflictReason {
    /**
     * Multiple customer accounts share the same customer name.
     * No single customer can be deterministically selected without guessing.
     */
    AMBIGUOUS_CUSTOMER_NAME,

    /**
     * Transaction records a specific customer name, but no matching customer
     * account exists in the customer registry.
     */
    CUSTOMER_NOT_FOUND,

    /**
     * Transaction involves credit/debt or account activity, but the customer name
     * is completely missing or empty.
     */
    MISSING_CUSTOMER_NAME,

    /**
     * Any other situation where customer identity cannot be safely verified.
     */
    OTHER_UNRESOLVED_IDENTITY
}

/**
 * Resolution lifecycle of an accounting identity conflict.
 */
enum class ConflictResolutionStatus {
    /**
     * Conflict is pending explicit, user-driven resolution.
     * Transaction remains unlinked (customerId = null).
     */
    UNRESOLVED,

    /**
     * User has explicitly chosen and assigned a valid persistent customer account.
     * The original transaction is linked to this customerId.
     */
    RESOLVED,

    /**
     * User has explicitly marked this transaction as an anonymous walk-in or non-customer transaction.
     * Audit trail is preserved permanently.
     */
    DISMISSED
}

/**
 * Pure domain representation of a persistent migration/accounting conflict.
 * Preserves complete audit trail of the original state and resolution.
 */
data class CustomerConflictItem(
    val id: String,
    val transactionId: String,
    val originalCustomerName: String,
    val conflictReason: ConflictReason,
    val createdAt: String,
    val resolutionStatus: ConflictResolutionStatus,
    val resolvedCustomerId: String? = null,
    val resolvedAt: String? = null,
    val notes: String? = null
)
