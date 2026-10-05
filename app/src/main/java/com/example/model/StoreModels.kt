package com.example.model

enum class LanguageMode {
    ARABIC,
    ENGLISH
}

enum class NavDestination {
    HOME,
    ACCOUNTS,
    PURCHASES,
    ANALYSIS_CENTER,
    NOTIFICATIONS,
    MORE_SETTINGS,
    MORE,
    CUSTOMER_DETAILS,
    QUICK_PAYMENT,
    STORE_INFORMATION,
    APP_SETTINGS,
    DATA_CENTER,
    CUSTOMER_MANAGEMENT,
    PRODUCT_MANAGEMENT,
    ARCHIVE,
    ABOUT,
    PRIVACY_POLICY,
    TERMS_OF_USE,
    CONTACT_SUPPORT,
    BACKUP_RESTORE
}

enum class MoreMenuItemId {
    STORE_INFORMATION,
    APP_SETTINGS,
    DATA_CENTER,
    ABOUT,
    PRIVACY_POLICY,
    TERMS_OF_USE,
    CONTACT_SUPPORT;

    fun toNavDestination(): NavDestination = when (this) {
        STORE_INFORMATION -> NavDestination.STORE_INFORMATION
        APP_SETTINGS -> NavDestination.APP_SETTINGS
        DATA_CENTER -> NavDestination.DATA_CENTER
        ABOUT -> NavDestination.ABOUT
        PRIVACY_POLICY -> NavDestination.PRIVACY_POLICY
        TERMS_OF_USE -> NavDestination.TERMS_OF_USE
        CONTACT_SUPPORT -> NavDestination.CONTACT_SUPPORT
    }

    companion object {
        fun fromNavDestination(destination: NavDestination): MoreMenuItemId? = when (destination) {
            NavDestination.STORE_INFORMATION -> STORE_INFORMATION
            NavDestination.APP_SETTINGS -> APP_SETTINGS
            NavDestination.DATA_CENTER -> DATA_CENTER
            NavDestination.ABOUT -> ABOUT
            NavDestination.PRIVACY_POLICY -> PRIVACY_POLICY
            NavDestination.TERMS_OF_USE -> TERMS_OF_USE
            NavDestination.CONTACT_SUPPORT -> CONTACT_SUPPORT
            else -> null
        }
    }
}

enum class AppThemeMode {
    NEUTRAL,
    PURPLE,
    GOLD
}

data class StoreInfo(
    val storeName: String = StoreStrings.SAMPLE_STORE_NAME_AR,
    val ownerName: String = StoreStrings.SAMPLE_OWNER_NAME_AR,
    val phone: String = StoreStrings.SAMPLE_PHONE,
    val address: String = StoreStrings.SAMPLE_ADDRESS_AR,
    val taxNumber: String = "300123456700003",
    val crNumber: String = "1010123456"
)

enum class ThemeDisplayMode {
    LIGHT,
    DARK,
    AUTO
}

enum class PeriodFilter {
    ALL,
    TODAY,
    MONTH,
    CUSTOM
}

enum class AccountFilter {
    ALL,
    HAS_DEBT,
    RECENTLY_ACTIVE
}

enum class AccountSortOption {
    DEFAULT,
    HIGHEST_DEBT,
    HIGHEST_CASH
}

enum class SettlementType {
    FULL,
    PARTIAL
}

/**
 * Legacy payment option.
 *
 * NOTE (Accounting Refactor Phase 1):
 * In accounting standards, DEBT is an account receivable obligation, NOT a payment method.
 * Real payment methods are defined in [PaymentMethodType] (CASH, BANK, CARD, etc.).
 * Debt-based transactions are classified under [SaleType.CREDIT] or [SaleType.MIXED].
 */
@Deprecated(
    message = "Use PaymentMethodType for real monetary instruments and SaleType for checkout settlement method. DEBT is not a payment method.",
    replaceWith = ReplaceWith("PaymentMethodType")
)
enum class PaymentMethodOption {
    CASH,
    DEBT
}

data class TransactionItem(
    val id: String,
    val title: String = "",
    val customerNameSnapshot: String = "", // ONLY historical display name captured at the time of transaction
    val activityType: String, // "Purchase" / "Payment" or "شراء كاش" / "تسديد" / "شراء بالدين"
    val amount: Double,
    val isCredit: Boolean, // true = receivable/credit (+), false = payment/debit (-)
    val date: String,
    val relativeTime: String, // e.g., "منذ ساعتين", "منذ 15 دقيقة", "اليوم"
    val notes: String = "",
    val settlementType: SettlementType? = null, // Optional: FULL or PARTIAL
    val customerId: String? = null, // Relational customer identity (Source of Truth)
    val isArchived: Boolean = false,
    val archivedDate: String? = null,
    @Deprecated(
        message = "Legacy customerName property preserved for backward compatibility. Use customerNameSnapshot for presentation and customerId for identity.",
        replaceWith = ReplaceWith("customerNameSnapshot")
    )
    val customerName: String = customerNameSnapshot,
    // Phase 1 typed financial vocabulary fields:
    val transactionType: TransactionType? = null,
    val saleType: SaleType? = null,
    val paymentStatus: PaymentStatus? = null,
    val operationStatus: OperationStatus? = null,
    val paymentMethod: PaymentMethodType? = null,
    // Phase 2 mixed sales representation:
    val paidAmount: Double = 0.0,
    val creditAmount: Double = 0.0
) {
    @Deprecated("Legacy constructor for backward compatibility. Use customerNameSnapshot.")
    constructor(
        id: String,
        title: String = "",
        activityType: String,
        amount: Double,
        isCredit: Boolean,
        date: String,
        relativeTime: String,
        customerName: String,
        notes: String = "",
        settlementType: SettlementType? = null,
        customerId: String? = null,
        isArchived: Boolean = false,
        archivedDate: String? = null
    ) : this(
        id = id,
        title = title,
        customerNameSnapshot = customerName,
        activityType = activityType,
        amount = amount,
        isCredit = isCredit,
        date = date,
        relativeTime = relativeTime,
        notes = notes,
        settlementType = settlementType,
        customerId = customerId,
        isArchived = isArchived,
        archivedDate = archivedDate,
        customerName = customerName,
        transactionType = null,
        saleType = null,
        paymentStatus = null,
        operationStatus = null,
        paymentMethod = null,
        paidAmount = 0.0,
        creditAmount = 0.0
    )
}

data class CustomerAccount(
    val id: String,
    val customerName: String,
    @Deprecated("Legacy stored balance. Use CustomerLedgerCalculator to calculate balance from persistent ledger.")
    val balance: Double, // positive = customer owes store, negative = store owes customer
    @Deprecated("Legacy stored total debt. Use CustomerLedgerCalculator to calculate balance from persistent ledger.")
    val totalDebt: Double, // total outstanding debt
    val phone: String,
    val lastTransactionDate: String = "2026-09-05",
    val hasRecentActivity: Boolean = false,
    val isArchived: Boolean = false,
    val archivedDate: String? = null
)

data class NotificationItem(
    val id: String,
    val customerName: String,
    val transactionType: String, // "تسجيل معاملة" / "تسديد" or "Record Transaction" / "Payment"
    val amount: Double,
    val timestamp: String, // e.g., "منذ 15 دقيقة", "اليوم", "أمس"
    val isPayment: Boolean, // true for Payment, false for Record Transaction
    val isRead: Boolean = false,
    val transactionId: String? = null
)

data class ProductItem(
    val id: String,
    val name: String,
    val price: Double,
    val category: String = "عام",
    val unit: String = "حبة",
    val costPrice: Double = 0.0,
    val imageUri: String? = null,
    val isArchived: Boolean = false,
    val archivedDate: String? = null
)

sealed class ArchiveConflict {
    abstract val descriptionAr: String
    abstract val descriptionEn: String

    data class CustomerConflict(
        val archivedCustomer: CustomerAccount,
        val conflictingCustomer: CustomerAccount,
        override val descriptionAr: String,
        override val descriptionEn: String
    ) : ArchiveConflict()

    data class ProductConflict(
        val archivedProduct: ProductItem,
        val conflictingProduct: ProductItem,
        override val descriptionAr: String,
        override val descriptionEn: String
    ) : ArchiveConflict()

    data class TransactionConflict(
        val archivedTransaction: TransactionItem,
        val conflictingTransaction: TransactionItem,
        override val descriptionAr: String,
        override val descriptionEn: String
    ) : ArchiveConflict()
}

data class CartItem(
    val product: ProductItem,
    val quantity: Int
)

data class ArchivedProductItem(
    val id: String,
    val name: String,
    val category: String,
    val archivedDateAr: String,
    val archivedDateEn: String,
    val price: Double
)

data class DeletedItem(
    val id: String,
    val title: String,
    val typeAr: String,
    val typeEn: String,
    val deletedDateAr: String,
    val deletedDateEn: String
)
