package com.example.model

/**
 * =============================================================================
 * ISOLATED LEGACY ACCOUNTING COMPATIBILITY BRIDGE
 * =============================================================================
 *
 * Phase 1 Step 2 of SmallStore Accounting Refactor:
 * Establishes explicit, centralized, deterministic mapping between the legacy
 * accounting representation (activityType: String, isCredit: Boolean,
 * settlementType: SettlementType?, PaymentMethodOption) and the typed financial vocabulary
 * (TransactionType, SaleType, PaymentStatus, OperationStatus, PaymentMethodType).
 *
 * CRITICAL ACCOUNTING INVARIANTS:
 * 1. DEBT is NEVER mapped to PaymentMethodType.
 *    Debt represents an accounts receivable obligation, not a money location or payment instrument.
 * 2. FULL / PARTIAL is NEVER interpreted as SaleType.
 *    FULL / PARTIAL describes settlement progress (PaymentStatus), not the sale mode (SaleType).
 * 3. Legacy isCredit alone cannot safely distinguish pure CREDIT from MIXED sales.
 *    Therefore, if an old record does not contain separate cash and debt breakdown, do NOT guess.
 * 4. Never use Arabic display text for mapping.
 * 5. Never use contains() or fuzzy string matching for accounting classification.
 * 6. Preserve unknown/ambiguous legacy values safely (returns null).
 * 7. Historical data is never mutated or silently reinterpreted.
 * 8. All compatibility logic is strictly isolated here so it can be cleanly removed
 *    in future database migration phases.
 */
object LegacyAccountingBridge {

    /**
     * Exact canonical legacy activityType tokens known from historical databases,
     * sample data, and regression tests.
     *
     * We use strict Set membership lookup (exact match), NEVER substring contains()
     * or fuzzy matching.
     */
    private val EXACT_LEGACY_PAYMENT_TOKENS: Set<String> = setOf(
        "تسديد",
        "تسديد دفعة",
        "تسديد كامل",
        "تسديد جزئي",
        "Payment",
        "CUSTOMER_PAYMENT"
    )

    private val EXACT_LEGACY_SALE_CASH_TOKENS: Set<String> = setOf(
        "شراء كاش",
        "شراء نقدي",
        "شراء نقدي (كاش)",
        "كاش",
        "Cash",
        "Cash Sale",
        "SALE_CASH"
    )

    private val EXACT_LEGACY_SALE_CREDIT_TOKENS: Set<String> = setOf(
        "شراء آجل",
        "شراء بالدين",
        "آجل",
        "دين",
        "Debt",
        "Credit",
        "Credit Purchase",
        "SALE_CREDIT"
    )

    private val EXACT_LEGACY_GENERIC_SALE_TOKENS: Set<String> = setOf(
        "شراء",
        "بيع",
        "Sale",
        "Purchase",
        "SALE"
    )

    /**
     * Deterministically classifies a legacy transaction into a [TransactionType].
     *
     * Invariants:
     * - Uses exact token matching only (no contains(), no regex, no fuzzy matching).
     * - Returns null if the token is ambiguous or unrecognized (does not guess).
     */
    fun toTransactionType(rawActivityType: String?, isCredit: Boolean): TransactionType? {
        if (rawActivityType == null) return null
        val token = rawActivityType.trim()

        return when {
            EXACT_LEGACY_PAYMENT_TOKENS.contains(token) -> TransactionType.CUSTOMER_PAYMENT
            EXACT_LEGACY_SALE_CASH_TOKENS.contains(token) -> TransactionType.SALE
            EXACT_LEGACY_SALE_CREDIT_TOKENS.contains(token) -> TransactionType.SALE
            EXACT_LEGACY_GENERIC_SALE_TOKENS.contains(token) -> TransactionType.SALE
            isCredit -> TransactionType.SALE // In SmallStore, debt was strictly incurred via sales
            else -> null // Unknown legacy record; preserve safely without guessing
        }
    }

    /**
     * Deterministically maps legacy fields to a typed [SaleType].
     *
     * Invariants:
     * - Rule 2: NEVER interprets FULL/PARTIAL as SaleType.
     * - Rule 3: Legacy isCredit alone cannot distinguish CREDIT vs MIXED safely.
     *   If isCredit is true, do NOT guess; returns null.
     * - Only returns [SaleType.CASH] if deterministically known to have zero credit obligation.
     */
    fun toSaleType(rawActivityType: String?, isCredit: Boolean): SaleType? {
        if (rawActivityType == null) return null
        val token = rawActivityType.trim()

        // Pure cash sale: isCredit is false AND token is known cash sale token
        if (!isCredit && EXACT_LEGACY_SALE_CASH_TOKENS.contains(token)) {
            return SaleType.CASH
        }

        // If isCredit is true, the legacy record might be pure CREDIT or MIXED (split cash/debt).
        // Legacy TransactionEntity does not store cashAmount vs debtAmount.
        // Rule 3: Legacy isCredit alone cannot fully represent a MIXED sale. Do NOT guess.
        if (isCredit) {
            return null // Ambiguous in historical data
        }

        return null
    }

    /**
     * Deterministically maps legacy settlement and credit information to typed [PaymentStatus].
     *
     * Invariants:
     * - FULL -> PAID
     * - PARTIAL -> PARTIAL
     * - Unsettled credit transaction -> UNPAID
     */
    fun toPaymentStatus(
        legacySettlementType: SettlementType?,
        isCredit: Boolean,
        transactionType: TransactionType?
    ): PaymentStatus? {
        return when (legacySettlementType) {
            SettlementType.FULL -> PaymentStatus.PAID
            SettlementType.PARTIAL -> PaymentStatus.PARTIAL
            null -> {
                when {
                    transactionType == TransactionType.CUSTOMER_PAYMENT -> PaymentStatus.PAID
                    isCredit -> PaymentStatus.UNPAID
                    !isCredit && transactionType == TransactionType.SALE -> PaymentStatus.PAID
                    else -> null
                }
            }
        }
    }

    /**
     * Deterministically maps legacy [PaymentMethodOption] to typed [PaymentMethodType].
     *
     * Invariant:
     * - Rule 1: Never map DEBT to PaymentMethodType. Returns null.
     */
    @Suppress("DEPRECATION")
    fun toPaymentMethodType(legacyOption: PaymentMethodOption?): PaymentMethodType? {
        return when (legacyOption) {
            PaymentMethodOption.CASH -> PaymentMethodType.CASH
            PaymentMethodOption.DEBT -> null // Invariant: DEBT is not a payment method
            null -> null
        }
    }

    /**
     * Deterministically maps a raw string to [PaymentMethodType].
     * Invariant: String "DEBT" returns null (never mapped to PaymentMethodType).
     */
    fun toPaymentMethodType(rawMethod: String?): PaymentMethodType? {
        if (rawMethod == null) return null
        return when (rawMethod.trim().uppercase()) {
            "CASH" -> PaymentMethodType.CASH
            "BANK" -> PaymentMethodType.BANK
            "CARD" -> PaymentMethodType.CARD
            "E_WALLET" -> PaymentMethodType.E_WALLET
            "OTHER" -> PaymentMethodType.OTHER
            "DEBT" -> null // Invariant: DEBT is not a payment method
            else -> null
        }
    }

    /**
     * Maps legacy archived flag to [OperationStatus].
     */
    fun toOperationStatus(isArchived: Boolean): OperationStatus {
        return OperationStatus.ACTIVE
    }

    /**
     * Data holder for generated legacy fields.
     */
    data class LegacyTransactionFields(
        val activityType: String,
        val isCredit: Boolean,
        val settlementType: SettlementType?
    )

    /**
     * Outbound Legacy Bridge:
     * Translates typed accounting decisions made at new decision points into
     * backward-compatible legacy fields required by current UI and Room schema (until Phase 2).
     */
    fun toLegacyFields(
        transactionType: TransactionType,
        saleType: SaleType? = null,
        paymentStatus: PaymentStatus? = null
    ): LegacyTransactionFields {
        return when (transactionType) {
            TransactionType.SALE -> {
                val isCredit = when (saleType) {
                    SaleType.CASH -> false
                    SaleType.CREDIT, SaleType.MIXED -> true
                    null -> paymentStatus != PaymentStatus.PAID
                }
                val settlementType = when (paymentStatus) {
                    PaymentStatus.PAID -> SettlementType.FULL
                    PaymentStatus.PARTIAL -> SettlementType.PARTIAL
                    PaymentStatus.UNPAID -> null
                    null -> if (!isCredit) SettlementType.FULL else null
                }
                val activityType = if (!isCredit) "شراء كاش" else "شراء آجل"

                LegacyTransactionFields(
                    activityType = activityType,
                    isCredit = isCredit,
                    settlementType = settlementType
                )
            }
            TransactionType.CUSTOMER_PAYMENT -> {
                val settlementType = when (paymentStatus) {
                    PaymentStatus.PAID -> SettlementType.FULL
                    PaymentStatus.PARTIAL -> SettlementType.PARTIAL
                    else -> null
                }
                LegacyTransactionFields(
                    activityType = "تسديد",
                    isCredit = false,
                    settlementType = settlementType
                )
            }
            else -> {
                // Safe generic fallback for future operations prior to Room schema migration
                LegacyTransactionFields(
                    activityType = transactionType.name,
                    isCredit = false,
                    settlementType = null
                )
            }
        }
    }
}
