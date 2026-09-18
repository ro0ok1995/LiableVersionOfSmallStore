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
    CONTACT_SUPPORT
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

enum class PaymentMethodOption {
    CASH,
    DEBT
}

data class TransactionItem(
    val id: String,
    val title: String = "",
    val customerName: String,
    val activityType: String, // "Purchase" / "Payment" or "شراء كاش" / "تسديد" / "شراء بالدين"
    val amount: Double,
    val isCredit: Boolean, // true = receivable/credit (+), false = payment/debit (-)
    val date: String,
    val relativeTime: String, // e.g., "منذ ساعتين", "منذ 15 دقيقة", "اليوم"
    val notes: String = "",
    val settlementType: SettlementType? = null, // Optional: FULL or PARTIAL
    val customerId: String? = null,
    val isArchived: Boolean = false,
    val archivedDate: String? = null
)

data class CustomerAccount(
    val id: String,
    val customerName: String,
    val balance: Double, // positive = customer owes store, negative = store owes customer
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

object StoreStrings {
    // App Identity
    const val APP_NAME = "SmallStore"
    const val ABOUT_APP_NAME_EN = "SmallStore"
    const val ABOUT_APP_NAME_AR = "سمول ستور"
    const val ABOUT_VERSION_LABEL = "v1.0.0"
    const val ABOUT_PURPOSE_EN = "SmallStore is an all-in-one store management application designed to organize customers, track balances, manage transactions, and deliver actionable sales and debt reports for retail and wholesale businesses."
    const val ABOUT_PURPOSE_AR = "سمول ستور هو تطبيق متكامل لإدارة المتاجر لمتابعة حسابات العملاء، الأرصدة، تسجيل المعاملات والمشتريات، وإصدار تقارير مالية تفصيلية للمبيعات والديون."

    // Terminology (Exact matches required by spec)
    const val RECORD_TRANSACTION_EN = "Record Transaction"
    const val RECORD_TRANSACTION_AR = "تسجيل معاملة"
    const val QUICK_PAYMENT_EN = "Quick Payment"
    const val QUICK_PAYMENT_AR = "تسديد سريع"
    const val PURCHASES_EN = "Purchases"
    const val PURCHASES_AR = "المشتريات"
    const val PAYMENT_EN = "Payment"
    const val PAYMENT_AR = "تسديد"
    const val ACCOUNT_STATEMENT_EN = "Account Statement"
    const val ACCOUNT_STATEMENT_AR = "كشف الحساب"
    const val SETTLEMENT_EN = "Settlement"
    const val SETTLEMENT_AR = "المحاسبة"
    const val CUSTOMER_DETAILS_EN = "Customer Details"
    const val CUSTOMER_DETAILS_AR = "تفاصيل العميل"
    const val CUSTOMERS_EN = "Customers"
    const val CUSTOMERS_AR = "العملاء"
    const val CUSTOMER_PROFILE_EN = "Customer Profile"
    const val CUSTOMER_PROFILE_AR = "ملف الزبون"
    const val BASIC_ACTIONS_EN = "Basic Actions"
    const val BASIC_ACTIONS_AR = "العمليات الأساسية"
    const val RECORD_PURCHASE_EN = "Record Purchase"
    const val RECORD_PURCHASE_AR = "تسجيل مشتريات"
    const val VIEW_ACCOUNT_STATEMENT_EN = "View Account Statement"
    const val VIEW_ACCOUNT_STATEMENT_AR = "عرض كشف الحساب"
    const val RECORD_PAYMENT_EN = "Record Payment"
    const val RECORD_PAYMENT_AR = "تسجيل دفعة سداد"
    const val ANALYSIS_CENTER_EN = "Analysis Center"
    const val ANALYSIS_CENTER_AR = "مركز التحليل"

    // Volume & Debt Breakdown Chart Tabs
    const val CHART_TAB_DONUT_EN = "Donut"
    const val CHART_TAB_DONUT_AR = "دائري"
    const val CHART_TAB_COLUMN_EN = "Column"
    const val CHART_TAB_COLUMN_AR = "عمودي"
    const val CHART_TAB_COMBO_EN = "Combo"
    const val CHART_TAB_COMBO_AR = "مركب"

    // Notifications specific
    const val NO_NOTIFICATIONS_EN = "No notifications yet"
    const val NO_NOTIFICATIONS_AR = "لا توجد إشعارات بعد"
    const val NOTIF_RECORD_TRANSACTION_EN = "Record Transaction"
    const val NOTIF_RECORD_TRANSACTION_AR = "تسجيل معاملة"
    const val NOTIF_PAYMENT_EN = "Payment"
    const val NOTIF_PAYMENT_AR = "تسديد"
    const val NOTIFICATIONS_EN = "Notifications"
    const val NOTIFICATIONS_AR = "الإشعارات"

    // Navigation & Shell
    const val HOME_EN = "Home"
    const val HOME_AR = "الرئيسية"
    const val ACCOUNTS_EN = "Accounts"
    const val ACCOUNTS_AR = "الحسابات"
    const val MORE_EN = "More"
    const val MORE_AR = "المزيد"
    const val MORE_SETTINGS_EN = "More/Settings"
    const val MORE_SETTINGS_AR = "المزيد / الإعدادات"

    // More Screen Menu Items
    const val STORE_INFO_EN = "Store Information"
    const val STORE_INFO_AR = "معلومات المتجر"
    const val STORE_INFO_DESC_EN = "Store-level identity and contact info"
    const val STORE_INFO_DESC_AR = "بيانات المتجر وهوية النشاط التجاري ومعلومات الاتصال"

    const val APP_SETTINGS_EN = "App Settings"
    const val APP_SETTINGS_AR = "إعدادات التطبيق"
    const val APP_SETTINGS_DESC_EN = "Appearance, language, backup/restore, preferences"
    const val APP_SETTINGS_DESC_AR = "المظهر، اللغة، النسخ الاحتياطي والاستعادة، والتفضيلات"

    const val DATA_CENTER_EN = "Data Center"
    const val DATA_CENTER_AR = "مركز البيانات"
    const val DATA_CENTER_DESC_EN = "Customer, product, and archive/data-management functions"
    const val DATA_CENTER_DESC_AR = "إدارة الأرشيف وسلة المهملات والنسخ الاحتياطي"

    const val ABOUT_EN = "About"
    const val ABOUT_AR = "حول التطبيق"
    const val ABOUT_DESC_EN = "Application information and version details"
    const val ABOUT_DESC_AR = "معلومات التطبيق ورقم الإصدار وبيانات الدعم"

    // Store Information Form Strings
    const val STORE_NAME_LABEL_EN = "Store Name"
    const val STORE_NAME_LABEL_AR = "اسم المتجر"
    const val OWNER_NAME_LABEL_EN = "Owner Name"
    const val OWNER_NAME_LABEL_AR = "اسم المالك"
    const val PHONE_LABEL_EN = "Phone"
    const val PHONE_LABEL_AR = "رقم الهاتف"
    const val ADDRESS_LABEL_EN = "Address"
    const val ADDRESS_LABEL_AR = "العنوان"
    const val CHANGE_LOGO_EN = "Change"
    const val CHANGE_LOGO_AR = "تغيير"
    const val SAVE_EN = "Save"
    const val SAVE_AR = "حفظ"
    const val STORE_INFO_SAVED_EN = "Store information saved successfully"
    const val STORE_INFO_SAVED_AR = "تم حفظ معلومات المتجر بنجاح"

    // Plausible Arabic Sample Values
    const val SAMPLE_STORE_NAME_AR = "متجر الأمل للمواد الغذائية"
    const val SAMPLE_STORE_NAME_EN = "Al-Amal Grocery Store"
    const val SAMPLE_OWNER_NAME_AR = "عبدالله فهد المنصور"
    const val SAMPLE_OWNER_NAME_EN = "Abdullah Fahad Al-Mansoor"
    const val SAMPLE_PHONE = "0551234567"
    const val SAMPLE_ADDRESS_AR = "الرياض، السليمانية، طريق الملك عبدالعزيز، مبنى 24"
    const val SAMPLE_ADDRESS_EN = "Riyadh, As Sulimaniyah, King Abdulaziz Road, Building 24"

    // App Settings Screen Strings
    const val SECTION_APPEARANCE_EN = "Appearance"
    const val SECTION_APPEARANCE_AR = "المظهر والسمات"
    const val THEME_MODE_LABEL_EN = "Theme Mode"
    const val THEME_MODE_LABEL_AR = "وضع المظهر"
    const val THEME_MODE_LIGHT_EN = "Light"
    const val THEME_MODE_LIGHT_AR = "فاتح"
    const val THEME_MODE_DARK_EN = "Dark"
    const val THEME_MODE_DARK_AR = "داكن"
    const val THEME_MODE_AUTO_EN = "Auto"
    const val THEME_MODE_AUTO_AR = "تلقائي"
    const val THEME_ACCENT_LABEL_EN = "Accent Theme"
    const val THEME_ACCENT_LABEL_AR = "لون السمة"
    const val ACCENT_SIMPLE_EN = "Simple (default)"
    const val ACCENT_SIMPLE_AR = "بسيط (افتراضي)"
    const val ACCENT_PURPLE_EN = "Purple"
    const val ACCENT_PURPLE_AR = "بنفسجي"
    const val ACCENT_GOLD_EN = "Gold"
    const val ACCENT_GOLD_AR = "ذهبي"

    const val SECTION_LANGUAGE_EN = "Language"
    const val SECTION_LANGUAGE_AR = "اللغة والاتجاه"
    const val LANG_ARABIC = "العربية"
    const val LANG_ENGLISH = "English"

    const val SECTION_BACKUP_EN = "Backup & Restore"
    const val SECTION_BACKUP_AR = "النسخ الاحتياطي والاستعادة"
    const val BACKUP_NOW_EN = "Backup Now"
    const val BACKUP_NOW_AR = "نسخ احتياطي الآن"
    const val BACKUP_NOW_DESC_EN = "Save a local backup of store data and transactions"
    const val BACKUP_NOW_DESC_AR = "حفظ ملف نسخة احتياطية محلية لبيانات المتجر والعملاء"
    const val RESTORE_FROM_BACKUP_EN = "Restore from Backup"
    const val RESTORE_FROM_BACKUP_AR = "استعادة من نسخة احتياطية"
    const val RESTORE_FROM_BACKUP_DESC_EN = "Restore previously saved store data"
    const val RESTORE_FROM_BACKUP_DESC_AR = "استرجاع بيانات المتجر من ملف محفوظ مسبقاً"

    const val SECTION_PREFERENCES_EN = "Preferences"
    const val SECTION_PREFERENCES_AR = "التفضيلات والتنبيهات"
    const val PREF_ENABLE_NOTIFICATIONS_EN = "Enable notifications"
    const val PREF_ENABLE_NOTIFICATIONS_AR = "تفعيل الإشعارات"
    const val PREF_ENABLE_NOTIFICATIONS_DESC_EN = "Alerts for payments and due debts"
    const val PREF_ENABLE_NOTIFICATIONS_DESC_AR = "تنبيهات فورية عند تسجيل دفعات ومستحقات الديون"
    const val PREF_TRANSACTION_SOUNDS_EN = "Transaction sound effects"
    const val PREF_TRANSACTION_SOUNDS_AR = "أصوات المعاملات"
    const val PREF_TRANSACTION_SOUNDS_DESC_EN = "Play subtle chime when recording transactions"
    const val PREF_TRANSACTION_SOUNDS_DESC_AR = "تشغيل صوت خفيف عند تسجيل المعاملات المالية"

    const val BACKUP_SUCCESS_EN = "Local backup created successfully"
    const val BACKUP_SUCCESS_AR = "تم إنشاء النسخة الاحتياطية بنجاح"
    const val BACKUP_ERROR_EN = "Backup failed"
    const val BACKUP_ERROR_AR = "فشل إنشاء النسخة الاحتياطية"
    const val RESTORE_SUCCESS_EN = "Latest backup restored successfully"
    const val RESTORE_SUCCESS_AR = "تمت استعادة البيانات بنجاح"
    const val RESTORE_ERROR_EN = "Restore failed: Invalid file format"
    const val RESTORE_ERROR_AR = "فشلت الاستعادة: ملف غير صالح"

    // Data Center Screen Strings
    const val SECTION_ARCHIVE_TRASH_EN = "Archive / Trash"
    const val SECTION_ARCHIVE_TRASH_AR = "الأرشيف وسلة المهملات"
    const val ARCHIVED_CUSTOMERS_EN = "Archived Customers"
    const val ARCHIVED_CUSTOMERS_AR = "العملاء المؤرشفون"
    const val ARCHIVED_CUSTOMERS_DESC_EN = "Customers moved out of active balances"
    const val ARCHIVED_CUSTOMERS_DESC_AR = "العملاء المنقولون من الحسابات النشطة إلى الأرشيف"
    const val ARCHIVED_PRODUCTS_EN = "Archived Products"
    const val ARCHIVED_PRODUCTS_AR = "المنتجات المؤرشفة"
    const val ARCHIVED_PRODUCTS_DESC_EN = "Inactive inventory items kept for history"
    const val ARCHIVED_PRODUCTS_DESC_AR = "عناصر المخزون غير النشطة المحفوظة للسجلات"
    const val DELETED_ITEMS_EN = "Deleted Items"
    const val DELETED_ITEMS_AR = "العناصر المحذوفة"
    const val DELETED_ITEMS_DESC_EN = "Items waiting for 30-day permanent cleanup"
    const val DELETED_ITEMS_DESC_AR = "العناصر المعلقة في سلة المهملات قبل الحذف النهائي"
    const val CREATE_BACKUP_EN = "Create Backup"
    const val CREATE_BACKUP_AR = "إنشاء نسخة احتياطية"
    const val CREATE_BACKUP_DESC_EN = "Save full database snapshot to local storage"
    const val CREATE_BACKUP_DESC_AR = "حفظ لقطة كاملة لقاعدة البيانات في الذاكرة المحلية"
    const val RESTORE_BACKUP_EN = "Restore Backup"
    const val RESTORE_BACKUP_AR = "استعادة النسخة الاحتياطية"
    const val RESTORE_BACKUP_DESC_EN = "Restore database from previously created snapshot"
    const val RESTORE_BACKUP_DESC_AR = "استرجاع قاعدة البيانات من لقطة سابقة"
    const val RESTORE_ACTION_EN = "Restore"
    const val RESTORE_ACTION_AR = "استعادة"
    const val DELETE_PERMANENTLY_EN = "Delete Permanently"
    const val DELETE_PERMANENTLY_AR = "حذف نهائي"
    const val DELETE_PERMANENT_WARNING_EN = "This record will be permanently deleted and cannot be restored."
    const val DELETE_PERMANENT_WARNING_AR = "سيتم حذف هذا السجل نهائياً ولا يمكن استعادته."
    const val ARCHIVE_TAB_CUSTOMERS_EN = "Customers"
    const val ARCHIVE_TAB_CUSTOMERS_AR = "العملاء"
    const val ARCHIVE_TAB_PRODUCTS_EN = "Products"
    const val ARCHIVE_TAB_PRODUCTS_AR = "الأصناف"
    const val ARCHIVE_TAB_TRANSACTIONS_EN = "Transactions"
    const val ARCHIVE_TAB_TRANSACTIONS_AR = "المعاملات"
    const val ARCHIVED_ON_EN = "Archived on"
    const val ARCHIVED_ON_AR = "تاريخ الأرشفة"
    const val EXAMPLE_EXPANDED_TITLE_EN = "Example: Archived Customers"
    const val EXAMPLE_EXPANDED_TITLE_AR = "مثال: سجلات العملاء المؤرشفين"
    const val CONFLICT_DIALOG_TITLE_EN = "Example: Restore Conflict"
    const val CONFLICT_DIALOG_TITLE_AR = "مثال: تعارض عند الاستعادة"
    const val CONFLICT_DESC_EN = "An existing customer with the same name already exists in active accounts. How would you like to handle this conflict?"
    const val CONFLICT_DESC_AR = "يوجد عميل بنفس الاسم في الحسابات النشطة. كيف تود معالجة هذا التعارض؟"
    const val CONFLICT_REPLACE_EN = "Replace"
    const val CONFLICT_REPLACE_AR = "استبدال"
    const val CONFLICT_ADD_EN = "Add"
    const val CONFLICT_ADD_AR = "إضافة كجديد"
    const val CONFLICT_SKIP_EN = "Skip"
    const val CONFLICT_SKIP_AR = "تخطي"
    const val DATA_LIFECYCLE_PHILOSOPHY_EN = "Active → Archive/Trash → Review → Restore OR Permanent Cleanup"
    const val DATA_LIFECYCLE_PHILOSOPHY_AR = "نشط ← الأرشيف / سلة المهملات ← مراجعة ← استعادة أو حذف نهائي"

    // About & Support
    const val ABOUT_PRIVACY_POLICY_EN = "Privacy Policy"
    const val ABOUT_PRIVACY_POLICY_AR = "سياسة الخصوصية"
    const val ABOUT_TERMS_OF_USE_EN = "Terms of Use"
    const val ABOUT_TERMS_OF_USE_AR = "شروط الاستخدام"
    const val ABOUT_CONTACT_SUPPORT_EN = "Contact Support"
    const val ABOUT_CONTACT_SUPPORT_AR = "الاتصال بالدعم الفني"
    const val SUPPORT_EMAIL = "support@smallstore.app"
    const val NO_EMAIL_APP_EN = "No email application found on this device"
    const val NO_EMAIL_APP_AR = "لم يتم العثور على تطبيق بريد إلكتروني"

    // Privacy Policy screen
    const val PRIVACY_TITLE_EN = "Privacy Policy"
    const val PRIVACY_TITLE_AR = "سياسة الخصوصية"
    const val PRIVACY_LAST_UPDATED_EN = "Last Updated: September 2026"
    const val PRIVACY_LAST_UPDATED_AR = "آخر تحديث: سبتمبر 2026"
    const val PRIVACY_SEC1_TITLE_EN = "1. Local-Only Data Storage"
    const val PRIVACY_SEC1_TITLE_AR = "1. تخزين محلي حصري على جهازك"
    const val PRIVACY_SEC1_DESC_EN = "SmallStore operates entirely offline on your device. All your store details, customer names, balances, transactions, and inventory items remain exclusively within your device's local memory."
    const val PRIVACY_SEC1_DESC_AR = "يعمل تطبيق سمول ستور دون اتصال تماماً. كافة تفاصيل متجرك، أسماء العملاء، الأرصدة، المعاملات، والمخزون محفوظة حصرياً داخل ذاكرة جهازك ولا تُرسل لأي خادم خارجي."
    const val PRIVACY_SEC2_TITLE_EN = "2. Zero Third-Party Tracking"
    const val PRIVACY_SEC2_TITLE_AR = "2. انعدام التتبع الخارجي"
    const val PRIVACY_SEC2_DESC_EN = "We do not sell, rent, or transmit your business data to any third-party advertisers or external platforms. Your commercial activity belongs entirely to you."
    const val PRIVACY_SEC2_DESC_AR = "لا نقوم ببيع أو تأجير أو مشاركة أي بيانات تجارية مع أطراف ثالثة أو معلنين. نشاطك التجاري ملكك وحدك."
    const val PRIVACY_SEC3_TITLE_EN = "3. Full Backup & Export Control"
    const val PRIVACY_SEC3_TITLE_AR = "3. تحكم كامل بالنسخ والتصدير"
    const val PRIVACY_SEC3_DESC_EN = "You have complete authority to generate JSON backup files, export CSV statements, or share reports whenever needed. You can delete or clear your data at any time."
    const val PRIVACY_SEC3_DESC_AR = "لديك السيطرة الكاملة لتصدير النسخ الاحتياطية بصيغة JSON، أو استخراج كشوفات الحسابات بصيغة CSV أو مشاركة التقارير بصيغة PDF متى شئت."

    // Terms of Use screen
    const val TERMS_TITLE_EN = "Terms of Use"
    const val TERMS_TITLE_AR = "شروط الاستخدام"
    const val TERMS_LAST_UPDATED_EN = "Effective Date: September 2026"
    const val TERMS_LAST_UPDATED_AR = "تاريخ السريان: سبتمبر 2026"
    const val TERMS_SEC1_TITLE_EN = "1. Permitted Store Management Use"
    const val TERMS_SEC1_TITLE_AR = "1. الاستخدام المصرح به لإدارة المتجر"
    const val TERMS_SEC1_DESC_EN = "SmallStore is designed to assist small and medium businesses with recording customer balances, quick settlements, and inventory tracking. The app is provided for lawful business management purposes."
    const val TERMS_SEC1_DESC_AR = "صُمم تطبيق سمول ستور لمساعدة المتاجر الصغيرة والمتوسطة في توثيق أرصدة العملاء، وحسابات الديون، والتسديد السريع."
    const val TERMS_SEC2_TITLE_EN = "2. Backup Responsibility"
    const val TERMS_SEC2_TITLE_AR = "2. مسؤولية النسخ الاحتياطي"
    const val TERMS_SEC2_DESC_EN = "Because all data is stored locally on your device without cloud sync, you are responsible for maintaining regular backups to avoid data loss in the event of device failure, loss, or reset."
    const val TERMS_SEC2_DESC_AR = "نظراً لأن جميع البيانات تُحفظ محلياً على هاتفك دون مزامنة سحابية، يتحمل المستخدم مسؤولية أخذ نسخ احتياطية دورية لتفادي ضياع البيانات."
    const val TERMS_SEC3_TITLE_EN = "3. Accuracy of Recorded Numbers"
    const val TERMS_SEC3_TITLE_AR = "3. دقة الأرقام والعمليات المسجلة"
    const val TERMS_SEC3_DESC_EN = "SmallStore provides mathematical calculation and ledger tools based strictly on your input. Users are advised to review transaction totals before finalizing customer settlements."
    const val TERMS_SEC3_DESC_AR = "يوفر التطبيق أدوات محاسبية تعتمد بدقة على مدخلاتك، ويُنصح بمراجعة إجمالي المبالغ قبل اعتماد المحاسبة النهائية."

    // Backup & Restore toast strings
    const val BACKUP_FILE_SAVED_EN = "Backup saved successfully"
    const val BACKUP_FILE_SAVED_AR = "تم حفظ النسخة الاحتياطية بنجاح"
    const val BACKUP_FAILED_EN = "Failed to create backup"
    const val BACKUP_FAILED_AR = "فشل إنشاء النسخة الاحتياطية"
    const val RESTORE_COMPLETED_EN = "Data restored successfully"
    const val RESTORE_COMPLETED_AR = "تم استرجاع البيانات بنجاح"
    const val RESTORE_FAILED_EN = "Failed to restore backup (invalid or corrupt file)"
    const val RESTORE_FAILED_AR = "فشلت استعادة النسخة (ملف غير صالح أو تالف)"

    const val OPTIONAL_EN = "(optional)"
    const val OPTIONAL_AR = "(اختياري)"

    // Home screen specific
    const val SEARCH_CUSTOMER_EN = "Search customer"
    const val SEARCH_CUSTOMER_AR = "بحث عن عميل"
    const val LATEST_ACTIVITIES_EN = "Latest Activities"
    const val LATEST_ACTIVITIES_AR = "النشاط الأخير"
    const val TOTAL_BALANCE_EN = "Total Balance"
    const val TOTAL_BALANCE_AR = "إجمالي الرصيد"
    const val TOTAL_DEBT_EN = "Total Debt"
    const val TOTAL_DEBT_AR = "إجمالي الديون"
    const val TODAY_TRANSACTIONS_EN = "Today's Transactions"
    const val TODAY_TRANSACTIONS_AR = "معاملات اليوم"
    const val PERIOD_ALL_EN = "All"
    const val PERIOD_ALL_AR = "كل"
    const val PERIOD_TODAY_EN = "Today"
    const val PERIOD_TODAY_AR = "اليوم"
    const val PERIOD_MONTH_EN = "Month"
    const val PERIOD_MONTH_AR = "الشهر"
    const val PERIOD_CUSTOM_EN = "Custom"
    const val PERIOD_CUSTOM_AR = "مخصص"

    // Accounts screen specific
    const val SEARCH_CUSTOMER_ACCOUNTS_EN = "Search customer by name or phone"
    const val SEARCH_CUSTOMER_ACCOUNTS_AR = "بحث عن عميل بالاسم أو الهاتف"
    const val FILTER_ALL_EN = "All"
    const val FILTER_ALL_AR = "الكل"
    const val FILTER_HAS_DEBT_EN = "Has Debt"
    const val FILTER_HAS_DEBT_AR = "عليه دين"
    const val FILTER_RECENTLY_ACTIVE_EN = "Recently Active"
    const val FILTER_RECENTLY_ACTIVE_AR = "نشط مؤخراً"
    const val ADD_CUSTOMER_EN = "+ Add Customer"
    const val ADD_CUSTOMER_AR = "+ إضافة عميل"
    const val NO_CUSTOMERS_YET_EN = "No customers yet"
    const val NO_CUSTOMERS_YET_AR = "لا يوجد عملاء بعد"
    const val PAID_UP_EN = "Paid up"
    const val PAID_UP_AR = "خالص"

    // Customer Details screen specific
    const val EDIT_CUSTOMER_EN = "Edit customer"
    const val EDIT_CUSTOMER_AR = "تعديل العميل"
    const val ARCHIVE_CUSTOMER_EN = "Archive Customer"
    const val ARCHIVE_CUSTOMER_AR = "أرشفة العميل"
    const val ARCHIVE_CUSTOMER_SUBTITLE_EN = "Move customer to archive and remove from active list"
    const val ARCHIVE_CUSTOMER_SUBTITLE_AR = "نقل العميل إلى الأرشيف واستبعاده من القائمة النشطة"
    const val ARCHIVE_CONFIRM_TITLE_EN = "Archive Customer"
    const val ARCHIVE_CONFIRM_TITLE_AR = "تأكيد أرشفة العميل"
    const val ARCHIVE_CONFIRM_MESSAGE_EN = "Are you sure you want to archive this customer? The customer will be removed from the normal active customer list while preserving all transaction history and balances."
    const val ARCHIVE_CONFIRM_MESSAGE_AR = "هل أنت متأكد من أرشفة هذا العميل؟ سيتم نقل العميل إلى الأرشيف واستبعاده من قائمة الحسابات النشطة مع الحفاظ الكامل على كافة سجلات المعاملات والأرصدة السابقة."
    const val CANCEL_EN = "Cancel"
    const val CANCEL_AR = "إلغاء"
    const val OWES_EN = "Owes"
    const val OWES_AR = "مدين بـ"
    const val SETTLED_EN = "Settled"
    const val SETTLED_AR = "خالص"
    const val RECENT_ACTIVITY_EN = "Recent Activity"
    const val RECENT_ACTIVITY_AR = "النشاط الأخير"

    // Purchases screen specific
    const val SEARCH_PRODUCTS_EN = "Search products"
    const val SEARCH_PRODUCTS_AR = "بحث في المنتجات"
    const val COMPLETE_TRANSACTION_EN = "Complete Transaction"
    const val COMPLETE_TRANSACTION_AR = "إتمام المعاملة"
    const val SELECT_CUSTOMER_EN = "Select Customer"
    const val SELECT_CUSTOMER_AR = "تحديد العميل"
    const val SELECT_CUSTOMER_REQUIRED_EN = "Required: Select a customer to enable checkout"
    const val SELECT_CUSTOMER_REQUIRED_AR = "مطلوب: حدد عميلاً لتفعيل إتمام المعاملة"
    const val CHANGE_CUSTOMER_EN = "Change"
    const val CHANGE_CUSTOMER_AR = "تغيير"
    const val VIEW_CART_EN = "View Cart"
    const val VIEW_CART_AR = "عرض السلة"
    const val HIDE_CART_EN = "Hide Cart"
    const val HIDE_CART_AR = "إخفاء السلة"
    const val CART_ITEMS_EN = "items"
    const val CART_ITEMS_AR = "عناصر"
    const val TOTAL_EN = "Total"
    const val TOTAL_AR = "المجموع"
    const val NO_PRODUCTS_FOUND_EN = "No products found"
    const val NO_PRODUCTS_FOUND_AR = "لم يتم العثور على منتجات"

    // Quick Payment screen specific
    const val SETTLEMENT_TYPE_FULL_EN = "Full"
    const val SETTLEMENT_TYPE_FULL_AR = "كامل"
    const val SETTLEMENT_TYPE_PARTIAL_EN = "Partial"
    const val SETTLEMENT_TYPE_PARTIAL_AR = "جزئي"
    const val METHOD_CASH_EN = "Cash"
    const val METHOD_CASH_AR = "كاش"
    const val METHOD_DEBT_EN = "Debt"
    const val METHOD_DEBT_AR = "آجل (دين)"
    const val COMPLETE_ACTION_EN = "Complete"
    const val COMPLETE_ACTION_AR = "إتمام"
    const val NOTES_LABEL_EN = "Notes (optional)"
    const val NOTES_LABEL_AR = "ملاحظات (اختياري)"
    const val TRANSACTION_TOTAL_EN = "Transaction Total"
    const val TRANSACTION_TOTAL_AR = "إجمالي المعاملة"
    const val AMOUNT_LIMIT_HELPER_EN = "The amount entered cannot exceed transaction total"
    const val AMOUNT_LIMIT_HELPER_AR = "المبلغ المدخل لا يمكن أن يتجاوز إجمالي المعاملة"
    const val CONFIRM_PAYMENT_EN = "Confirm Payment"
    const val CONFIRM_PAYMENT_AR = "تأكيد التسديد"
    const val PAYMENT_EXCEEDS_DEBT_ERROR_EN = "The payment amount cannot exceed the outstanding balance"
    const val PAYMENT_EXCEEDS_DEBT_ERROR_AR = "لا يمكن أن يتجاوز مبلغ التسديد الرصيد المستحق"

    // Settlement Bottom Sheet specific
    const val CASH_AMOUNT_LABEL_EN = "Cash Amount"
    const val CASH_AMOUNT_LABEL_AR = "المبلغ نقداً (كاش)"
    const val DEBT_AMOUNT_LABEL_EN = "Debt Amount"
    const val DEBT_AMOUNT_LABEL_AR = "المبلغ بالدين (آجل)"
    const val TOTAL_PAID_FORMULA_EN = "Total Paid = Cash Amount + Debt Amount"
    const val TOTAL_PAID_FORMULA_AR = "إجمالي المدفوع = المبلغ كاش + المبلغ آجل"
    const val REMAINING_BALANCE_FORMULA_EN = "Remaining Balance = Transaction Total - Total Paid"
    const val REMAINING_BALANCE_FORMULA_AR = "الرصيد المتبقي = إجمالي المعاملة - إجمالي المدفوع"
    const val TOTAL_PAID_EN = "Total Paid"
    const val TOTAL_PAID_AR = "إجمالي المدفوع"
    const val REMAINING_BALANCE_EN = "Remaining Balance"
    const val REMAINING_BALANCE_AR = "الرصيد المتبقي"

    // Analysis Center specific
    const val TAB_STATISTICS_EN = "Statistics"
    const val TAB_STATISTICS_AR = "الإحصائيات"
    const val TAB_ACCOUNT_STATEMENT_EN = "Account Statement"
    const val TAB_ACCOUNT_STATEMENT_AR = "كشف الحساب"
    const val TAB_REPORTS_EN = "Reports"
    const val TAB_REPORTS_AR = "التقارير"

    const val TOTAL_SALES_EN = "Total Sales"
    const val TOTAL_SALES_AR = "إجمالي المبيعات"
    const val TOTAL_PAYMENTS_EN = "Total Payments"
    const val TOTAL_PAYMENTS_AR = "إجمالي المقبوضات"
    const val TOTAL_OUTSTANDING_DEBT_EN = "Total Outstanding Debt"
    const val TOTAL_OUTSTANDING_DEBT_AR = "إجمالي الديون المستحقة"
    const val NUMBER_OF_TRANSACTIONS_EN = "Number of Transactions"
    const val NUMBER_OF_TRANSACTIONS_AR = "عدد المعاملات"
    const val ACTIVITY_SUMMARY_EN = "Activity Summary"
    const val ACTIVITY_SUMMARY_AR = "ملخص النشاط"

    const val SELECT_CUSTOMER_OPTIONAL_EN = "Select Customer (optional)"
    const val SELECT_CUSTOMER_OPTIONAL_AR = "تحديد العميل (اختياري)"
    const val ALL_CUSTOMERS_SHOP_WIDE_EN = "All Customers (Shop-wide)"
    const val ALL_CUSTOMERS_SHOP_WIDE_AR = "جميع العملاء (شامل المحل)"
    const val ALL_CUSTOMERS_LABEL_EN = "All customers"
    const val ALL_CUSTOMERS_LABEL_AR = "جميع العملاء"

    const val TRANSACTION_TYPE_FILTER_EN = "Transaction Type"
    const val TRANSACTION_TYPE_FILTER_AR = "نوع المعاملة"
    const val ALL_TYPES_EN = "All"
    const val ALL_TYPES_AR = "الكل"
    const val TX_FILTER_PURCHASE_EN = "Purchase"
    const val TX_FILTER_PURCHASE_AR = "مشتريات"
    const val TX_FILTER_PAYMENT_EN = "Payment"
    const val TX_FILTER_PAYMENT_AR = "تسديد"
    const val TX_FILTER_CASH_PURCHASE_EN = "Cash purchase"
    const val TX_FILTER_CASH_PURCHASE_AR = "شراء كاش"
    const val TX_FILTER_DEBT_PURCHASE_EN = "Debt purchase"
    const val TX_FILTER_DEBT_PURCHASE_AR = "شراء بالدين"

    const val PERIOD_LOCKED_CAPTION_EN = "Period is locked across all tabs."
    const val PERIOD_LOCKED_CAPTION_AR = "الفترة الزمنية مقفلة عبر كافة التبويبات."
    const val PERIOD_UNLOCKED_CAPTION_EN = "Each tab has its own period."
    const val PERIOD_UNLOCKED_CAPTION_AR = "لكل تبويب فترته الزمنية الخاصة."

    const val REPORT_COMPREHENSIVE_CUSTOMER_EN = "Comprehensive Customer Report"
    const val REPORT_COMPREHENSIVE_CUSTOMER_AR = "التقرير المخصص الشامل"
    const val REPORT_COMPREHENSIVE_CUSTOMER_DESC_EN = "Unified comprehensive customer statement including purchases, payments, balance and debt aging"
    const val REPORT_COMPREHENSIVE_CUSTOMER_DESC_AR = "تقرير كشف حساب تفصيلي شامل للعميل متضمناً المشتريات، المدفوعات، الأرصدة وتاريخ الديون"

    const val USES_PERIOD_SELECTED_ABOVE_EN = "Uses the period selected above"
    const val USES_PERIOD_SELECTED_ABOVE_AR = "يستخدم الفترة المحددة أعلاه"
    const val SELECT_CUSTOMER_FOR_REPORT_EN = "Please select a customer for this comprehensive report"
    const val SELECT_CUSTOMER_FOR_REPORT_AR = "يرجى اختيار عميل لإصدار التقرير الشامل"

    const val SELECT_PRODUCT_OPTIONAL_EN = "Product (optional)"
    const val SELECT_PRODUCT_OPTIONAL_AR = "المنتج (اختياري)"
    const val ALL_PRODUCTS_EN = "All Products"
    const val ALL_PRODUCTS_AR = "جميع المنتجات"

    const val TOTAL_IN_EN = "Total In"
    const val TOTAL_IN_AR = "إجمالي الوارد (تسديد)"
    const val TOTAL_OUT_EN = "Total Out"
    const val TOTAL_OUT_AR = "إجمالي الصادر (مشتريات)"
    const val NET_BALANCE_EN = "Net"
    const val NET_BALANCE_AR = "الصافي"
    const val RUNNING_BALANCE_EN = "Balance"
    const val RUNNING_BALANCE_AR = "الرصيد التراكمي"

    const val EXPORT_STATEMENT_EN = "Export"
    const val EXPORT_STATEMENT_AR = "تصدير"
    const val SHARE_STATEMENT_EN = "Share"
    const val SHARE_STATEMENT_AR = "مشاركة"
    const val CURRENCY_SYMBOL = AppCurrency.SYMBOL

    const val EXPORT_CSV_SUCCESS_EN = "Statement exported as CSV successfully"
    const val EXPORT_CSV_SUCCESS_AR = "تم تصدير كشف الحساب بصيغة CSV بنجاح"
    const val REPORT_EXPORT_PDF_SUCCESS_EN = "Report generated and saved as PDF successfully"
    const val REPORT_EXPORT_PDF_SUCCESS_AR = "تم إصدار التقرير وحفظه بصيغة PDF بنجاح"
    const val SHARE_REPORT_TITLE_EN = "Share Report"
    const val SHARE_REPORT_TITLE_AR = "مشاركة التقرير"
    const val PRINT_FAILED_EN = "Printing is not supported on this device"
    const val PRINT_FAILED_AR = "الطباعة غير مدعومة على هذا الجهاز"

    // Contact Support Screen
    const val CONTACT_SUPPORT_TITLE_EN = "Contact Support"
    const val CONTACT_SUPPORT_TITLE_AR = "الدعم الفني والمساندة"
    const val CONTACT_SUPPORT_DEVELOPER_EN = "Developer & Technical Support"
    const val CONTACT_SUPPORT_DEVELOPER_AR = "فريق التطوير والمساعدة الفنية"
    const val CONTACT_SUPPORT_NAME = "MrGazawe Studio"
    const val CONTACT_SUPPORT_PHONE_LABEL_EN = "WhatsApp"
    const val CONTACT_SUPPORT_PHONE_LABEL_AR = "واتساب"
    const val CONTACT_SUPPORT_PHONE = "+972592727830"
    const val CONTACT_SUPPORT_EMAIL = "ro0ok1995@gmail.com"
    const val CONTACT_SUPPORT_DESC_AR = "على استعداد لتنفيذ طلبات برامج خاصة على الطلب."
    const val CONTACT_SUPPORT_DESC_EN = "Available to develop custom software and applications on request."
    const val CONTACT_SUPPORT_CALL_EN = "Call"
    const val CONTACT_SUPPORT_CALL_AR = "اتصال"
    const val CONTACT_SUPPORT_COPY_EN = "Copy"
    const val CONTACT_SUPPORT_COPY_AR = "نسخ"
    const val CONTACT_SUPPORT_COPIED_EN = "Copied to clipboard"
    const val CONTACT_SUPPORT_COPIED_AR = "تم النسخ إلى الحافظة"

    // Home Screen Stats Redesign
    const val STAT_DEBT_CREDIT_EN = "Debt"
    const val STAT_DEBT_CREDIT_AR = "دين"
    const val STAT_CASH_SALES_EN = "Cash"
    const val STAT_CASH_SALES_AR = "كاش"
    const val STAT_PAYMENTS_RECEIVED_EN = "Payment"
    const val STAT_PAYMENTS_RECEIVED_AR = "دفعة"

    // Accounts Screen Sort
    const val SORT_DEFAULT_AR = "الافتراضي"
    const val SORT_DEFAULT_EN = "Default"
    const val SORT_HIGHEST_DEBT_AR = "الأعلى دين"
    const val SORT_HIGHEST_DEBT_EN = "Highest Debt"
    const val SORT_HIGHEST_CASH_AR = "الأعلى كاش"
    const val SORT_HIGHEST_CASH_EN = "Highest Cash"

    // Analysis Center & General App Strings
    const val APP_NAME_AR = "سمول ستور"
    const val APP_NAME_EN = "SmallStore"
    const val STORE_INFORMATION_AR = "معلومات المتجر"
    const val STORE_INFORMATION_EN = "Store Information"
    const val ABOUT_SMALLSTORE_AR = "حول سمول ستور"
    const val ABOUT_SMALLSTORE_EN = "About SmallStore"
    const val ABOUT_APP_SUMMARY_AR = "تطبيق متكامل لإدارة حسابات المتاجر الصغيرة ومتابعة ديون العملاء والمبيعات النقدية والآجلة والتقارير المالية دون الحاجة للاتصال بالإنترنت."
    const val ABOUT_APP_SUMMARY_EN = "Comprehensive retail management app for tracking customer debts, cash and credit purchases, instant payments, and detailed financial reports offline."
    const val PRIVACY_POLICY_AR = "سياسة الخصوصية"
    const val PRIVACY_POLICY_EN = "Privacy Policy"
    const val TERMS_OF_USE_AR = "شروط الاستخدام"
    const val TERMS_OF_USE_EN = "Terms of Use"
    const val CONTACT_SUPPORT_AR = "الاتصال بالدعم الفني"
    const val CONTACT_SUPPORT_EN = "Contact Support"
    const val TAB_STATEMENT_AR = "كشف الحساب"
    const val TAB_STATEMENT_EN = "Account Statement"
    const val CUSTOMER_CONTEXT_LABEL_AR = "حساب العميل:"
    const val CUSTOMER_CONTEXT_LABEL_EN = "Customer context:"
    const val ALL_CUSTOMERS_AR = "كافة العملاء"
    const val ALL_CUSTOMERS_EN = "All Customers"
    const val STAT_TOTAL_SALES_AR = "إجمالي المبيعات"
    const val STAT_TOTAL_SALES_EN = "Total Sales"
    const val TX_FILTER_ALL_AR = "الكل"
    const val TX_FILTER_ALL_EN = "All"
    const val REPORT_SALES_AND_ITEMS_AR = "تقرير المبيعات للأصناف والفواتير"
    const val REPORT_SALES_AND_ITEMS_EN = "Sales, Items & Invoices Report"
    const val REPORT_DEBT_BALANCES_AR = "تقرير أرصدة العملاء والديون"
    const val REPORT_DEBT_BALANCES_EN = "Customer Debt Balances Report"
    const val REPORT_TRANSACTIONS_AR = "تقرير المعاملات"
    const val REPORT_TRANSACTIONS_EN = "Transactions Report"
    const val SELECT_REPORT_TYPE_AR = "اختر نوع التقرير"
    const val SELECT_REPORT_TYPE_EN = "Select Report Type"
    const val CUSTOMER_REQUIRED_FOR_REPORT_AR = "اختيار عميل مطلوب لتقرير العميل الشامل"
    const val CUSTOMER_REQUIRED_FOR_REPORT_EN = "Customer selection required for comprehensive report"
    const val REPORT_PREVIEW_TITLE_AR = "تفاصيل التقرير"
    const val REPORT_PREVIEW_TITLE_EN = "Report Details"
    const val EXPORT_PDF_AR = "تصدير PDF"
    const val EXPORT_PDF_EN = "Export PDF"
    const val PRINT_REPORT_AR = "طباعة"
    const val PRINT_REPORT_EN = "Print"
    const val STORE_DEBT_AGING_SUMMARY_AR = "ملخص مديونيات المتجر وأعمار الديون"
    const val STORE_DEBT_AGING_SUMMARY_EN = "Store Balances & Debt Aging Summary"
    const val DEBT_AGING_BUCKETS_TITLE_AR = "توزيع أعمار الديون (حسب تواريخ المعاملات الفعلية):"
    const val DEBT_AGING_BUCKETS_TITLE_EN = "Debt Aging Buckets (From Actual Dates):"
    const val IN_DEBT_CUSTOMERS_AR = "العملاء المدينون"
    const val IN_DEBT_CUSTOMERS_EN = "In Debt"

    // Archive Strings
    const val ARCHIVE_TITLE_AR = "الأرشيف"
    const val ARCHIVE_TITLE_EN = "Archive"
    const val TAB_CUSTOMERS_AR = "العملاء"
    const val TAB_CUSTOMERS_EN = "Customers"
    const val TAB_PRODUCTS_AR = "الأصناف"
    const val TAB_PRODUCTS_EN = "Products"
    const val TAB_TRANSACTIONS_AR = "المعاملات"
    const val TAB_TRANSACTIONS_EN = "Transactions"
    const val DELETE_PERMANENTLY_ACTION_AR = "حذف نهائي"
    const val DELETE_PERMANENTLY_ACTION_EN = "Delete Permanently"
    const val PERMANENT_DELETE_WARNING_AR = "تحذير: سيتم حذف هذا السجل نهائياً ولا يمكن استرجاعه."
    const val PERMANENT_DELETE_WARNING_EN = "Warning: This record will be permanently deleted and cannot be restored."
    const val CONFIRM_RESTORE_TITLE_AR = "تأكيد الاستعادة"
    const val CONFIRM_RESTORE_TITLE_EN = "Confirm Restore"
    const val RESTORE_AS_SEPARATE_AR = "استعادة كسجل منفصل"
    const val RESTORE_AS_SEPARATE_EN = "Restore as separate record"
    const val KEEP_ARCHIVED_AR = "إبقاء في الأرشيف"
    const val KEEP_ARCHIVED_EN = "Keep archived"
    const val CONFLICT_DETECTED_TITLE_AR = "تنبيه تعارض في السجلات النشطة"
    const val CONFLICT_DETECTED_TITLE_EN = "Active Record Conflict Detected"
}

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

object SampleData {
    val sampleCustomers: List<CustomerAccount> = listOf(
        CustomerAccount("c1", "أحمد الشمري", 1250.0, 1250.0, "0501112233", "2026-09-05", true),
        CustomerAccount("c2", "خالد العتيبي", 420.0, 420.0, "0502223344", "2026-09-04", true),
        CustomerAccount("c3", "سعيد القحطاني", 0.0, 0.0, "0503334455", "2026-09-01", false),
        CustomerAccount("c4", "محمد الغامدي", 850.0, 850.0, "0504445566", "2026-09-03", true),
        CustomerAccount("c5", "فهد الدوسري", 0.0, 0.0, "0505556677", "2026-08-28", false),
        CustomerAccount("c6", "عبدالله الشهري", 310.0, 310.0, "0506667788", "2026-09-05", true),
        CustomerAccount("c7", "عمر الحربي", 180.0, 180.0, "0507778899", "2026-09-02", false)
    )

    val sampleTransactions: List<TransactionItem> = listOf(
        TransactionItem("tx1", "شراء مواد غذائية", "أحمد الشمري", "شراء آجل", 250.0, true, "2026-09-05", "منذ 15 دقيقة", "فاتورة بقالة", customerId = "c1"),
        TransactionItem("tx2", "تسديد دفعة", "خالد العتيبي", "تسديد", 100.0, false, "2026-09-05", "منذ ساعة", "سداد نقدي", SettlementType.PARTIAL, customerId = "c2"),
        TransactionItem("tx3", "شراء كاش", "عميل عام", "شراء كاش", 85.0, false, "2026-09-05", "منذ ساعتين", "دفع فوري", customerId = null),
        TransactionItem("tx4", "شراء مواد استهلاكية", "محمد الغامدي", "شراء آجل", 450.0, true, "2026-09-04", "أمس", "مشتريات منزلية", customerId = "c4"),
        TransactionItem("tx5", "تسديد حساب", "أحمد الشمري", "تسديد", 300.0, false, "2026-09-04", "أمس", "تحويل بنكي", SettlementType.FULL, customerId = "c1"),
        TransactionItem("tx6", "شراء آجل", "عبدالله الشهري", "شراء آجل", 310.0, true, "2026-09-03", "منذ يومين", "أدوات ومستلزمات", customerId = "c6"),
        TransactionItem("tx7", "تسديد نقدي", "سعيد القحطاني", "تسديد", 500.0, false, "2026-09-01", "منذ 4 أيام", "تسوية كاملة", SettlementType.FULL, customerId = "c3")
    )

    val sampleNotifications: List<NotificationItem> = listOf(
        NotificationItem("n1", "أحمد الشمري", "تسجيل معاملة", 250.0, "منذ 15 دقيقة", false, false, "tx1"),
        NotificationItem("n2", "خالد العتيبي", "تسديد", 100.0, "منذ ساعة", true, false, "tx2"),
        NotificationItem("n3", "محمد الغامدي", "تسجيل معاملة", 450.0, "أمس", false, true, "tx4"),
        NotificationItem("n4", "أحمد الشمري", "تسديد", 300.0, "أمس", true, true, "tx5")
    )

    val sampleProducts: List<ProductItem> = listOf(
        ProductItem("p1", "أرز بسمتي 5 كجم", 45.0, "مواد غذائية", "كيس"),
        ProductItem("p2", "زيت نباتي 1.5 لتر", 18.0, "زيوت", "حبة"),
        ProductItem("p3", "سكر ناعم 2 كجم", 12.0, "مواد غذائية", "كيس"),
        ProductItem("p4", "حليب مجفف 1.8 كجم", 65.0, "ألبان", "علبة"),
        ProductItem("p5", "شاي أسود 100 كيس", 16.0, "مشروبات", "كرتون"),
        ProductItem("p6", "معكرونة 400 جم", 4.5, "مواد غذائية", "حبة"),
        ProductItem("p7", "صلصة طماطم 135 جم", 2.5, "معلبات", "حبة"),
        ProductItem("p8", "ماء معبأ 330 مل كرتون", 15.0, "مشروبات", "كرتون")
    )
}
