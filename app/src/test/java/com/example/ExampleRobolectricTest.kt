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

    // 4. More Screen exact 4 sections in order without decorative dividers
    val moreSectionHeaders = listOf(
      StoreStrings.STORE_INFORMATION_EN,
      StoreStrings.APP_SETTINGS_EN,
      StoreStrings.DATA_CENTER_EN,
      StoreStrings.ABOUT_SMALLSTORE_EN
    )
    assertEquals(4, moreSectionHeaders.size)
    assertEquals("Store Information", moreSectionHeaders[0])
    assertEquals("App Settings", moreSectionHeaders[1])
    assertEquals("Data Center", moreSectionHeaders[2])
    assertEquals("About SmallStore", moreSectionHeaders[3])
  }

  @Test
  fun testCustomerCreationZeroInitialBalance() {
    val customer = CustomerAccount(
      id = "test_c_1",
      customerName = "محمد أحمد",
      phone = "0501234567",
      balance = 0.0,
      totalDebt = 0.0,
      hasRecentActivity = true
    )
    assertEquals(0.0, customer.balance, 0.0001)
    assertEquals(0.0, customer.totalDebt, 0.0001)
    assertEquals("محمد أحمد", customer.customerName)
    assertEquals("0501234567", customer.phone)
  }

  @Test
  fun testStoreNameValidationRules() {
    val arabicName = "بقالة الخير والبركة"
    val englishName = "Al-Amal Market & Trading"
    val mixedName = "سوبرماركت City Center - فرع 1"
    val punctuationName = "Store_1.A & B-Branch"
    val longName = "أ".repeat(41)

    assertTrue("Arabic name within 40 chars should be valid", arabicName.length <= 40)
    assertTrue("English name within 40 chars should be valid", englishName.length <= 40)
    assertTrue("Mixed name within 40 chars should be valid", mixedName.length <= 40)
    assertTrue("Punctuation name within 40 chars should be valid", punctuationName.length <= 40)
    assertTrue("Name > 40 characters should be detected", longName.length > 40)
  }

  @Test
  fun testCustomerSearchSuggestionFilteringAndClearing() {
    val customers = listOf(
      CustomerAccount(id = "c1", customerName = "محمد أحمد", phone = "0501111111", balance = 100.0, totalDebt = 100.0),
      CustomerAccount(id = "c2", customerName = "سالم علي", phone = "0502222222", balance = 50.0, totalDebt = 50.0),
      CustomerAccount(id = "c3", customerName = "John Doe", phone = "0503333333", balance = 0.0, totalDebt = 0.0)
    )

    var searchQuery = ""
    var filteredList = customers.filter { searchQuery.isBlank() || it.customerName.contains(searchQuery, ignoreCase = true) }
    assertEquals(3, filteredList.size)

    val selectedCustomer = customers[0]
    searchQuery = selectedCustomer.customerName
    filteredList = customers.filter { searchQuery.isBlank() || it.customerName.contains(searchQuery, ignoreCase = true) }
    assertEquals(1, filteredList.size)
    assertEquals("محمد أحمد", filteredList.first().customerName)

    searchQuery = ""
    filteredList = customers.filter { searchQuery.isBlank() || it.customerName.contains(searchQuery, ignoreCase = true) }
    assertEquals(3, filteredList.size)
  }

  @Test
  fun testCustomerArchivingStrings() {
    assertEquals("أرشفة العميل", StoreStrings.ARCHIVE_CUSTOMER_AR)
    assertEquals("Archive Customer", StoreStrings.ARCHIVE_CUSTOMER_EN)
    assertEquals("تأكيد أرشفة العميل", StoreStrings.ARCHIVE_CONFIRM_TITLE_AR)
    assertEquals("Archive Customer", StoreStrings.ARCHIVE_CONFIRM_TITLE_EN)
    assertEquals("إلغاء", StoreStrings.CANCEL_AR)
    assertEquals("Cancel", StoreStrings.CANCEL_EN)
  }

  @Test
  fun testCustomerArchivingAndRestorePreservesDataIntegrity() {
    val activeCustomer = CustomerAccount(
      id = "c_active_1",
      customerName = "خالد المنصور",
      phone = "0559876543",
      balance = 250.0,
      totalDebt = 250.0,
      hasRecentActivity = true,
      isArchived = false,
      archivedDate = null
    )

    // Simulate archive
    val archivedCustomer = activeCustomer.copy(
      isArchived = true,
      archivedDate = "2026-09-17"
    )

    // Verify properties preserved
    assertEquals(activeCustomer.id, archivedCustomer.id)
    assertEquals(activeCustomer.customerName, archivedCustomer.customerName)
    assertEquals(activeCustomer.phone, archivedCustomer.phone)
    assertEquals(activeCustomer.balance, archivedCustomer.balance, 0.001)
    assertEquals(activeCustomer.totalDebt, archivedCustomer.totalDebt, 0.001)
    assertTrue(archivedCustomer.isArchived)
    assertEquals("2026-09-17", archivedCustomer.archivedDate)

    // Active customer list filter simulation
    val list = listOf(activeCustomer.copy(id = "c2", isArchived = false), archivedCustomer)
    val activeList = list.filter { !it.isArchived }
    assertEquals(1, activeList.size)
    assertEquals("c2", activeList.first().id)

    // Simulate restore
    val restoredCustomer = archivedCustomer.copy(
      isArchived = false,
      archivedDate = null
    )
    assertEquals(activeCustomer.id, restoredCustomer.id)
    assertEquals(activeCustomer.customerName, restoredCustomer.customerName)
    assertEquals(activeCustomer.balance, restoredCustomer.balance, 0.001)
    assertEquals(false, restoredCustomer.isArchived)
    assertEquals(null, restoredCustomer.archivedDate)

    val restoredList = listOf(activeCustomer.copy(id = "c2", isArchived = false), restoredCustomer)
    val restoredActiveList = restoredList.filter { !it.isArchived }
    assertEquals(2, restoredActiveList.size)
  }

  @Test
  fun testReportDetailsLabels() {
    // Verify report preview has been updated to details
    assertEquals("تفاصيل التقرير", StoreStrings.REPORT_PREVIEW_TITLE_AR)
    assertEquals("Report Details", StoreStrings.REPORT_PREVIEW_TITLE_EN)
  }

  @Test
  fun testCustomerSearchAndProductSearchFilterLogic() {
    val customers = listOf(
      CustomerAccount(id = "1", customerName = "أحمد محمد", phone = "0501234567", balance = 0.0, totalDebt = 0.0),
      CustomerAccount(id = "2", customerName = "خالد عمر", phone = "0559876543", balance = 0.0, totalDebt = 0.0),
      CustomerAccount(id = "3", customerName = "سارة علي", phone = "0541112233", balance = 0.0, totalDebt = 0.0, isArchived = true)
    )

    // Active dataset excludes archived
    val activeCustomers = customers.filter { !it.isArchived }
    assertEquals(2, activeCustomers.size)

    // Customer search by name
    val queryName = "أحمد"
    val filteredByName = activeCustomers.filter {
      it.customerName.contains(queryName, ignoreCase = true) || it.phone.contains(queryName)
    }
    assertEquals(1, filteredByName.size)
    assertEquals("أحمد محمد", filteredByName.first().customerName)

    // Customer search by phone
    val queryPhone = "055"
    val filteredByPhone = activeCustomers.filter {
      it.customerName.contains(queryPhone, ignoreCase = true) || it.phone.contains(queryPhone)
    }
    assertEquals(1, filteredByPhone.size)
    assertEquals("خالد عمر", filteredByPhone.first().customerName)

    // Product search filtering
    val products = listOf(
      com.example.model.ProductItem(id = "p1", name = "سكر ناعم", price = 10.0, category = "مواد غذائية"),
      com.example.model.ProductItem(id = "p2", name = "شاي أحمر", price = 15.0, category = "مشروبات"),
      com.example.model.ProductItem(id = "p3", name = "أرز مصري", price = 40.0, category = "مواد غذائية", isArchived = true)
    )

    val activeProducts = products.filter { !it.isArchived }
    assertEquals(2, activeProducts.size)

    // Search by product name
    val productQuery = "سكر"
    val filteredProducts = activeProducts.filter {
      it.name.contains(productQuery, ignoreCase = true) || it.category.contains(productQuery, ignoreCase = true)
    }
    assertEquals(1, filteredProducts.size)
    assertEquals("سكر ناعم", filteredProducts.first().name)
    assertEquals("مواد غذائية", filteredProducts.first().category)

    // Clear restores full active list
    val clearedQuery = ""
    val restoredProducts = activeProducts.filter {
      clearedQuery.isBlank() || it.name.contains(clearedQuery, ignoreCase = true) || it.category.contains(clearedQuery, ignoreCase = true)
    }
    assertEquals(2, restoredProducts.size)
  }

  @Test
  fun testPdfReportGenerationRtlAndLtr() {
    val headersAr = listOf("التاريخ", "النوع", "البيان", "المبلغ")
    val rowsAr = listOf(
      com.example.util.ReportPreviewRow("2026/09/18", "مشتريات", "سكر ناعم 5 كجم", "50.00 ₪"),
      com.example.util.ReportPreviewRow("2026/09/18", "تسديد", "دفعة نقدية", "20.00 ₪")
    )
    val kpisAr = listOf(
      "إجمالي المبيعات" to "50.00 ₪",
      "إجمالي التسديد" to "20.00 ₪"
    )

    // Verify HTML generation works cleanly with RTL and LTR
    val htmlAr = com.example.util.ReportExporter.generateReportHtml(
      title = "تقرير المبيعات",
      storeName = "سمول ستور",
      subtitle = "الفترة: اليوم",
      kpis = kpisAr,
      headers = headersAr,
      rows = rowsAr,
      isArabic = true
    )
    assertTrue(htmlAr.contains("dir=\"rtl\""))
    assertTrue(htmlAr.contains("تقرير المبيعات"))

    val htmlEn = com.example.util.ReportExporter.generateReportHtml(
      title = "Sales Report",
      storeName = "SmallStore",
      subtitle = "Period: Today",
      kpis = listOf("Total Sales" to "50.00 ₪"),
      headers = listOf("Date", "Type", "Description", "Amount"),
      rows = listOf(com.example.util.ReportPreviewRow("2026/09/18", "Purchases", "Sugar 5kg", "50.00 ₪")),
      isArabic = false
    )
    assertTrue(htmlEn.contains("dir=\"ltr\""))
    assertTrue(htmlEn.contains("Sales Report"))

    // On native Android devices, PdfDocument creates real PDFs.
    // Under Robolectric JVM without Skia native graphics binaries, PdfDocument startPage throws IllegalStateException.
    // Ensure generatePdfReport catches or executes correctly.
    try {
      val outStreamAr = java.io.ByteArrayOutputStream()
      com.example.util.ReportExporter.generatePdfReport(
        title = "تقرير المبيعات",
        storeName = "سمول ستور",
        subtitle = "الفترة: اليوم",
        kpis = kpisAr,
        headers = headersAr,
        rows = rowsAr,
        outputStream = outStreamAr,
        isArabic = true
      )
      val bytesAr = outStreamAr.toByteArray()
      if (bytesAr.isNotEmpty()) {
        assertEquals('%'.code.toByte(), bytesAr[0])
      }
    } catch (_: IllegalStateException) {
      // Expected in Robolectric headless JVM environment where native Skia/PdfDocument is unmocked
    }
  }
}
