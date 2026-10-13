package com.example.model

enum class LanguageMode {
    ARABIC,
    ENGLISH
}

enum class NavDestination {
    HOME,
    ACCOUNTS,
    PURCHASES,
    SUPPLIER_OPERATIONS,
    ANALYSIS_CENTER,
    NOTIFICATIONS,
    MORE_SETTINGS,
    MORE,
    CUSTOMER_DETAILS,
    SUPPLIER_DETAILS,
    QUICK_PAYMENT,
    STORE_INFORMATION,
    APP_SETTINGS,
    DATA_CENTER,
    CUSTOMER_MANAGEMENT,
    SUPPLIER_MANAGEMENT,
    PRODUCT_MANAGEMENT,
    ARCHIVE,
    REVERSED_TRANSACTIONS,
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

data class CustomerAccount(
    val id: String,
    val customerName: String,
    val balance: Double, 
    val totalDebt: Double, 
    val phone: String,
    val lastTransactionDate: String = "2026-09-05",
    val hasRecentActivity: Boolean = false,
    val isArchived: Boolean = false,
    val archivedDate: String? = null
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
    val archivedDate: String? = null,
    val isCatalogItem: Boolean = false,
    val isRawMaterial: Boolean = false
)

data class CartItem(
    val product: ProductItem,
    val quantity: Int
)

data class SupplierCartItem(
    val itemId: String,
    val name: String,
    val unit: String,
    val quantity: Double,
    val unitCost: Double,
    val imageUri: String? = null,
    val isRawMaterial: Boolean = false
) {
    val subtotal: Double
        get() = com.example.accounting.AccountingDecimal.multiplyToMoney(unitCost, quantity)
}
