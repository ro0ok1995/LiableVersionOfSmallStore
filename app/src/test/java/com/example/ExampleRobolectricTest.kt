package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.CustomerAccount
import com.example.model.LanguageMode
import com.example.model.NavDestination
import com.example.model.StoreStrings
import com.example.viewmodel.AnalysisTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SmallStore", appName)
  }

  @Test
  fun testDrawerHierarchyStringsAndDestinations() {
    // Verify all hierarchical navigation string constants exist in both Arabic and English
    assertEquals("الرئيسية", StoreStrings.HOME_AR)
    assertEquals("Home", StoreStrings.HOME_EN)
    assertEquals("الإشعارات", StoreStrings.NOTIFICATIONS_AR)
    assertEquals("Notifications", StoreStrings.NOTIFICATIONS_EN)

    assertEquals("الحسابات", StoreStrings.ACCOUNTS_AR)
    assertEquals("Accounts", StoreStrings.ACCOUNTS_EN)
    assertEquals("العملاء", StoreStrings.CUSTOMERS_AR)
    assertEquals("Customers", StoreStrings.CUSTOMERS_EN)
    assertEquals("ملف الزبون", StoreStrings.CUSTOMER_PROFILE_AR)
    assertEquals("Customer Profile", StoreStrings.CUSTOMER_PROFILE_EN)

    assertEquals("مركز التحليل", StoreStrings.ANALYSIS_CENTER_AR)
    assertEquals("Analysis Center", StoreStrings.ANALYSIS_CENTER_EN)
    assertEquals("الإحصائيات", StoreStrings.TAB_STATISTICS_AR)
    assertEquals("Statistics", StoreStrings.TAB_STATISTICS_EN)
    assertEquals("كشف الحساب", StoreStrings.ACCOUNT_STATEMENT_AR)
    assertEquals("Account Statement", StoreStrings.ACCOUNT_STATEMENT_EN)
    assertEquals("التقارير", StoreStrings.TAB_REPORTS_AR)
    assertEquals("Reports", StoreStrings.TAB_REPORTS_EN)

    assertEquals("المشتريات", StoreStrings.PURCHASES_AR)
    assertEquals("Purchases", StoreStrings.PURCHASES_EN)

    assertEquals("المزيد", StoreStrings.MORE_AR)
    assertEquals("More", StoreStrings.MORE_EN)
    assertEquals("معلومات المتجر", StoreStrings.STORE_INFORMATION_AR)
    assertEquals("Store Information", StoreStrings.STORE_INFORMATION_EN)
    assertEquals("إعدادات التطبيق", StoreStrings.APP_SETTINGS_AR)
    assertEquals("App Settings", StoreStrings.APP_SETTINGS_EN)
    assertEquals("مركز البيانات", StoreStrings.DATA_CENTER_AR)
    assertEquals("Data Center", StoreStrings.DATA_CENTER_EN)
    assertEquals("حول سمول ستور", StoreStrings.ABOUT_SMALLSTORE_AR)
    assertEquals("About SmallStore", StoreStrings.ABOUT_SMALLSTORE_EN)

    assertEquals("سياسة الخصوصية", StoreStrings.PRIVACY_POLICY_AR)
    assertEquals("Privacy Policy", StoreStrings.PRIVACY_POLICY_EN)
    assertEquals("شروط الاستخدام", StoreStrings.TERMS_OF_USE_AR)
    assertEquals("Terms of Use", StoreStrings.TERMS_OF_USE_EN)
    assertEquals("الاتصال بالدعم الفني", StoreStrings.CONTACT_SUPPORT_AR)
    assertEquals("Contact Support", StoreStrings.CONTACT_SUPPORT_EN)

    // Verify AnalysisTab values
    assertEquals(AnalysisTab.STATISTICS, AnalysisTab.valueOf("STATISTICS"))
    assertEquals(AnalysisTab.ACCOUNT_STATEMENT, AnalysisTab.valueOf("ACCOUNT_STATEMENT"))
    assertEquals(AnalysisTab.REPORTS, AnalysisTab.valueOf("REPORTS"))

    // Verify NavDestinations
    assertNotNull(NavDestination.HOME)
    assertNotNull(NavDestination.NOTIFICATIONS)
    assertNotNull(NavDestination.ACCOUNTS)
    assertNotNull(NavDestination.CUSTOMER_DETAILS)
    assertNotNull(NavDestination.ANALYSIS_CENTER)
    assertNotNull(NavDestination.PURCHASES)
    assertNotNull(NavDestination.MORE)
    assertNotNull(NavDestination.STORE_INFORMATION)
    assertNotNull(NavDestination.APP_SETTINGS)
    assertNotNull(NavDestination.DATA_CENTER)
    assertNotNull(NavDestination.ABOUT)
    assertNotNull(NavDestination.PRIVACY_POLICY)
    assertNotNull(NavDestination.TERMS_OF_USE)
    assertNotNull(NavDestination.CONTACT_SUPPORT)
  }

  @Test
  fun testBackupPayloadSerializationRoundtrip() {
    val storeInfo = com.example.model.StoreInfo(
      storeName = "متجر الأمل",
      ownerName = "سالم",
      phone = "0501234567"
    )
    val customer = CustomerAccount(
      id = "c_1",
      customerName = "محمد",
      phone = "0509999999",
      balance = 200.0,
      totalDebt = 200.0,
      hasRecentActivity = true
    )
    val originalPayload = com.example.data.backup.BackupPayload(
      version = 1,
      backupTimestamp = 1700000000000L,
      storeInfoAtBackupTime = storeInfo,
      customers = listOf(customer),
      products = emptyList(),
      transactions = emptyList(),
      transactionItemLines = emptyList(),
      notifications = emptyList()
    )

    val json = com.example.data.backup.BackupManager.serialize(originalPayload)
    assertTrue(json.contains("متجر الأمل"))
    assertTrue(json.contains("0509999999"))

    val restoredPayload = com.example.data.backup.BackupManager.deserialize(json)
    assertEquals(1, restoredPayload.version)
    assertEquals("متجر الأمل", restoredPayload.storeInfoAtBackupTime.storeName)
    assertEquals(1, restoredPayload.customers.size)
    assertEquals("محمد", restoredPayload.customers[0].customerName)
    assertEquals(200.0, restoredPayload.customers[0].balance, 0.001)
  }

  @Test
  fun testProductBackupWithOptionalImage() {
    val products = listOf(
      com.example.model.ProductItem(id = "p1", name = "ماء هنا", price = 1.0, category = "مشروبات", unit = "كرتون", costPrice = 0.7, imageUri = null),
      com.example.model.ProductItem(id = "p2", name = "معجون طماطم", price = 3.5, category = "معلبات", unit = "علبة", costPrice = 2.0, imageUri = null)
    )
    val payload = com.example.data.backup.BackupPayload(
      storeInfoAtBackupTime = com.example.model.StoreInfo(storeName = "متجر التجربة"),
      customers = emptyList(),
      products = products,
      transactions = emptyList(),
      notifications = emptyList()
    )

    val serialized = com.example.data.backup.BackupManager.serialize(payload)
    assertTrue(serialized.contains("ماء هنا"))
    assertTrue(serialized.contains("معجون طماطم"))
    assertTrue(serialized.contains("معلبات"))

    val deserialized = com.example.data.backup.BackupManager.deserialize(serialized)
    assertEquals(2, deserialized.products.size)
    assertEquals("p1", deserialized.products[0].id)
    assertEquals(1.0, deserialized.products[0].price, 0.001)
    assertEquals("p2", deserialized.products[1].id)
    assertEquals("معجون طماطم", deserialized.products[1].name)
    assertEquals(3.5, deserialized.products[1].price, 0.001)
  }

  @Test
  fun testMoreScreenAndDataCenterAndAppSettingsStructure() {
    // 1. More Screen 4 conceptual sections exist
    assertEquals("معلومات المتجر", StoreStrings.STORE_INFORMATION_AR)
    assertEquals("Store Information", StoreStrings.STORE_INFORMATION_EN)
    assertEquals("إعدادات التطبيق", StoreStrings.APP_SETTINGS_AR)
    assertEquals("App Settings", StoreStrings.APP_SETTINGS_EN)
    assertEquals("مركز البيانات", StoreStrings.DATA_CENTER_AR)
    assertEquals("Data Center", StoreStrings.DATA_CENTER_EN)
    assertEquals("حول سمول ستور", StoreStrings.ABOUT_SMALLSTORE_AR)
    assertEquals("About SmallStore", StoreStrings.ABOUT_SMALLSTORE_EN)

    // 2. App Settings features
    assertNotNull(com.example.model.LanguageMode.ARABIC)
    assertNotNull(com.example.model.LanguageMode.ENGLISH)
    assertNotNull(com.example.model.AppThemeMode.NEUTRAL)
    assertNotNull(com.example.model.AppThemeMode.PURPLE)
    assertNotNull(com.example.model.AppThemeMode.GOLD)
    assertNotNull(com.example.model.ThemeDisplayMode.LIGHT)
    assertNotNull(com.example.model.ThemeDisplayMode.DARK)
    assertNotNull(com.example.model.ThemeDisplayMode.AUTO)
    assertEquals("النسخ الاحتياطي والاستعادة", StoreStrings.SECTION_BACKUP_AR)
    assertEquals("إنشاء نسخة احتياطية", StoreStrings.CREATE_BACKUP_AR)
    assertEquals("استعادة النسخة الاحتياطية", StoreStrings.RESTORE_BACKUP_AR)
    assertEquals("تفعيل الإشعارات", StoreStrings.PREF_ENABLE_NOTIFICATIONS_AR)
    assertEquals("Enable notifications", StoreStrings.PREF_ENABLE_NOTIFICATIONS_EN)

    // 3. Data Center: Customers, Products, Archive
    assertEquals("العملاء", StoreStrings.CUSTOMERS_AR)
    assertEquals("Customers", StoreStrings.CUSTOMERS_EN)
    assertEquals("الأرشيف وسلة المهملات", StoreStrings.SECTION_ARCHIVE_TRASH_AR)
    assertEquals("Archive / Trash", StoreStrings.SECTION_ARCHIVE_TRASH_EN)
  }
}
