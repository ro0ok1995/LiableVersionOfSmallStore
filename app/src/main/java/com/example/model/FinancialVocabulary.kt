package com.example.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * =============================================================================
 * TYPED FINANCIAL VOCABULARY & ACCOUNTING CORE
 * =============================================================================
 *
 * Phase 1 of SmallStore Accounting Refactor:
 * Establishes typed, non-string-based domain concepts to serve as the foundation
 * for subsequent accounting, ledger, and database architecture phases.
 *
 * CRITICAL ACCOUNTING INVARIANTS:
 * 1. DEBT is NOT a payment method.
 * 2. FULL / PARTIAL is NOT a sale type.
 * 3. CASH / CREDIT / MIXED describes how a sale is settled (SaleType).
 * 4. PAID / PARTIAL / UNPAID describes payment settlement status (PaymentStatus).
 * 5. ACTIVE / REVERSED describes the lifecycle state of an accounting operation (OperationStatus).
 * 6. UI localization labels (Arabic/English) must NEVER be used as accounting state values.
 * 7. No accounting logic should be based on free-form string matching.
 * 8. Every financial operation must possess a stable internal identity.
 * 9. Financial operations must have a real timestamp representation rather than relativeTime text.
 */

// -----------------------------------------------------------------------------
// 1. TRANSACTION TYPE
// -----------------------------------------------------------------------------
/**
 * Root accounting classification of every business event recorded in SmallStore.
 */
enum class TransactionType {
    SALE,
    CUSTOMER_PAYMENT,
    SALE_RETURN,
    CUSTOMER_REFUND,
    PURCHASE,
    SUPPLIER_PAYMENT,
    PURCHASE_RETURN,
    EXPENSE,
    OPENING_BALANCE,
    BALANCE_ADJUSTMENT,
    STOCK_ADJUSTMENT,
    REVERSAL
}

// -----------------------------------------------------------------------------
// 2. SALE TYPE
// -----------------------------------------------------------------------------
/**
 * Describes how a sale is settled between customer and merchant at checkout.
 * - CASH: Fully settled immediately with liquid funds (zero debt created).
 * - CREDIT: Wholly unpaid at time of sale (full amount added to customer debt).
 * - MIXED: Partially settled in cash; remaining balance added to customer debt.
 */
enum class SaleType {
    CASH,
    CREDIT,
    MIXED
}

// -----------------------------------------------------------------------------
// 3. PAYMENT STATUS
// -----------------------------------------------------------------------------
/**
 * Settlement progress of a financial obligation or invoice.
 * - PAID: Fully settled, no outstanding balance remains on this obligation.
 * - PARTIAL: A payment has been recorded, but an unpaid balance remains.
 * - UNPAID: No payment has been received toward this obligation.
 */
enum class PaymentStatus {
    PAID,
    PARTIAL,
    UNPAID
}

// -----------------------------------------------------------------------------
// 4. OPERATION STATUS
// -----------------------------------------------------------------------------
/**
 * Lifecycle state of an accounting record or ledger entry.
 * - ACTIVE: Valid, active accounting transaction currently counted in ledger balances.
 * - REVERSED: Voided, cancelled, or reversed by an offsetting accounting operation.
 */
enum class OperationStatus {
    ACTIVE,
    REVERSED
}

// -----------------------------------------------------------------------------
// 5. PAYMENT METHOD TYPE
// -----------------------------------------------------------------------------
/**
 * Real instruments used to transfer liquid monetary funds.
 * Note: DEBT is intentionally absent because debt is an account receivable obligation,
 * not a liquid payment instrument.
 */
enum class PaymentMethodType {
    CASH,
    BANK,
    CARD,
    E_WALLET,
    OTHER
}

// -----------------------------------------------------------------------------
// 6. FINANCIAL IDENTITY & REAL TIMESTAMP CONTRACT
// -----------------------------------------------------------------------------
/**
 * Contract guaranteeing stable internal identity and an absolute machine-readable
 * timestamp for any accounting operation.
 */
interface FinancialOperation {
    val operationId: String
    val timestampMillis: Long
    val operationStatus: OperationStatus
}

/**
 * Utility for resolving real epoch milliseconds from legacy date strings and timestamps.
 */
object FinancialTimestampUtils {
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    /**
     * Resolves epoch milliseconds from a date string formatted as "yyyy-MM-dd".
     * Falls back to [fallbackMillis] if parsing fails.
     */
    fun parseDateToEpochMillis(dateStr: String, fallbackMillis: Long = System.currentTimeMillis()): Long {
        return try {
            val clean = if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr
            val localDate = LocalDate.parse(clean.trim(), DATE_FORMATTER)
            localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            fallbackMillis
        }
    }

    /**
     * Resolves the most precise available epoch milliseconds for a [TransactionItem].
     * Ordering resolution hierarchy:
     * 1. ISO-8601 or datetime timestamp embedded in [TransactionItem.date]
     * 2. Epoch millisecond timestamp embedded in [TransactionItem.id] (e.g. "tx_1727000000000")
     * 3. Start-of-day epoch millis parsed from "yyyy-MM-dd" date
     */
    fun resolveTransactionTimestamp(tx: TransactionItem): Long {
        val dateStr = tx.date.trim()
        if (dateStr.contains("T")) {
            try {
                return Instant.parse(dateStr).toEpochMilli()
            } catch (_: Exception) {}
            try {
                return LocalDateTime.parse(dateStr).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) {}
        }
        if (dateStr.contains(" ") && dateStr.contains(":")) {
            try {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]", Locale.US)
                return LocalDateTime.parse(dateStr, formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) {}
        }

        val idDigits = tx.id.substringAfterLast("_")
        val idTimestamp = idDigits.toLongOrNull()
        if (idTimestamp != null && idTimestamp > 1_000_000_000_000L) {
            return idTimestamp
        }

        return parseDateToEpochMillis(dateStr)
    }
}

// -----------------------------------------------------------------------------
// 7. ISOLATED LEGACY COMPATIBILITY LAYER
// -----------------------------------------------------------------------------
/**
 * Legacy compatibility façade.
 * All logic has been centralized and strictly isolated in [LegacyAccountingBridge].
 */
@Deprecated(
    message = "Use LegacyAccountingBridge for centralized deterministic mappings.",
    replaceWith = ReplaceWith("LegacyAccountingBridge")
)
object LegacyAccountingCompatibility {

    fun inferTransactionType(activityType: String, isCredit: Boolean): TransactionType? =
        LegacyAccountingBridge.toTransactionType(activityType, isCredit)

    fun inferSaleType(activityType: String, isCredit: Boolean, settlementType: SettlementType?): SaleType? =
        LegacyAccountingBridge.toSaleType(activityType, isCredit)

    fun inferPaymentStatus(activityType: String, isCredit: Boolean, settlementType: SettlementType?): PaymentStatus? =
        LegacyAccountingBridge.toPaymentStatus(settlementType, isCredit, inferTransactionType(activityType, isCredit))

    fun inferOperationStatus(isArchived: Boolean): OperationStatus =
        LegacyAccountingBridge.toOperationStatus(isArchived)

    @Suppress("DEPRECATION")
    fun mapLegacyPaymentMethod(option: PaymentMethodOption?): PaymentMethodType? =
        LegacyAccountingBridge.toPaymentMethodType(option)

    fun toLegacyActivityType(
        type: TransactionType,
        saleType: SaleType? = null,
        isArabic: Boolean = true
    ): String = LegacyAccountingBridge.toLegacyFields(type, saleType).activityType
}

// -----------------------------------------------------------------------------
// 8. EXTENSIONS ON TRANSACTION ITEM FOR NON-INVASIVE TYPED ACCESS
// -----------------------------------------------------------------------------

/**
 * Non-invasive typed getters for existing [TransactionItem] instances.
 * Allows new accounting logic to read typed domain concepts without breaking
 * existing callers or database serialization.
 */
val TransactionItem.typedTransactionType: TransactionType?
    get() = transactionType ?: LegacyAccountingBridge.toTransactionType(activityType, isCredit)

val TransactionItem.typedSaleType: SaleType?
    get() = saleType ?: when {
        creditAmount > 0.0 && paidAmount > 0.0 -> SaleType.MIXED
        creditAmount > 0.0 && paidAmount <= 0.0 -> SaleType.CREDIT
        paidAmount > 0.0 && creditAmount <= 0.0 && !isCredit -> SaleType.CASH
        else -> LegacyAccountingBridge.toSaleType(activityType, isCredit)
    }

val TransactionItem.typedPaymentStatus: PaymentStatus?
    get() = paymentStatus ?: when {
        creditAmount > 0.0 && paidAmount > 0.0 -> PaymentStatus.PARTIAL
        creditAmount > 0.0 && paidAmount <= 0.0 -> PaymentStatus.UNPAID
        paidAmount > 0.0 && creditAmount <= 0.0 -> PaymentStatus.PAID
        else -> LegacyAccountingBridge.toPaymentStatus(settlementType, isCredit, typedTransactionType)
    }

val TransactionItem.typedOperationStatus: OperationStatus
    get() = operationStatus ?: LegacyAccountingBridge.toOperationStatus(isArchived)

val TransactionItem.typedPaymentMethod: PaymentMethodType?
    get() = paymentMethod

val TransactionItem.epochTimestampMillis: Long
    get() = FinancialTimestampUtils.resolveTransactionTimestamp(this)
