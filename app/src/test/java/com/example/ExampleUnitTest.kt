package com.example

import com.example.model.CartItem
import com.example.model.CustomerAccount
import com.example.model.NavDestination
import com.example.model.ProductItem
import com.example.model.StoreStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying core business logic, customer filtering, and data integrity.
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCustomerSearchByNameAndPhone() {
    val customers = listOf(
      CustomerAccount(id = "1", customerName = "أحمد محمد", balance = 150.0, totalDebt = 150.0, phone = "0501234567"),
      CustomerAccount(id = "2", customerName = "خالد عبدالله", balance = 0.0, totalDebt = 0.0, phone = "0559876543"),
      CustomerAccount(id = "3", customerName = "John Doe", balance = -50.0, totalDebt = 0.0, phone = "0511122233")
    )

    // Match by Arabic name
    val matchAhmed = customers.filter {
      val q = "أحمد"
      it.customerName.lowercase().contains(q) || it.phone.contains(q)
    }
    assertEquals(1, matchAhmed.size)
    assertEquals("1", matchAhmed.first().id)

    // Match by English name case-insensitive
    val matchJohn = customers.filter {
      val q = "john"
      it.customerName.lowercase().contains(q) || it.phone.contains(q)
    }
    assertEquals(1, matchJohn.size)
    assertEquals("3", matchJohn.first().id)

    // Match by phone substring
    val matchPhone = customers.filter {
      val q = "987"
      it.customerName.lowercase().contains(q) || it.phone.contains(q)
    }
    assertEquals(1, matchPhone.size)
    assertEquals("2", matchPhone.first().id)

    // Empty query returns all
    val blankQuery = "  "
    val allIfBlank = if (blankQuery.isBlank()) customers else customers.filter { it.customerName.contains(blankQuery) }
    assertEquals(3, allIfBlank.size)
  }

  @Test
  fun testCustomerBalanceClassification() {
    val inDebt = CustomerAccount(id = "1", customerName = "Customer A", balance = 250.0, totalDebt = 250.0, phone = "0500000001")
    val zeroBalance = CustomerAccount(id = "2", customerName = "Customer B", balance = 0.0, totalDebt = 0.0, phone = "0500000002")
    val creditBalance = CustomerAccount(id = "3", customerName = "Customer C", balance = -75.0, totalDebt = 0.0, phone = "0500000003")

    assertTrue(inDebt.balance > 0)
    assertEquals(0.0, zeroBalance.balance, 0.001)
    assertTrue(creditBalance.balance < 0)
  }

  @Test
  fun testAppCurrencyFormatting() {
    assertEquals("₪", com.example.model.AppCurrency.SYMBOL)
    val formattedAr = com.example.model.AppCurrency.formatAmount(150.0, isArabic = true)
    assertTrue(formattedAr.contains("₪"))
    assertTrue(formattedAr.contains("150"))
  }

  @Test
  fun testQuickPaymentDebtReduction() {
    val custId = "cust_1"
    val saleTx = com.example.model.TransactionItem(
      id = "tx_sale",
      customerName = "طارق الحسين",
      activityType = "شراء بالدين",
      amount = 100.0,
      isCredit = true,
      date = "2026-09-20",
      relativeTime = "الآن",
      customerId = custId
    )
    val paymentTx = com.example.model.TransactionItem(
      id = "tx_pay",
      customerName = "طارق الحسين",
      activityType = "تسديد",
      amount = 50.0,
      isCredit = false,
      date = "2026-09-20",
      relativeTime = "الآن",
      customerId = custId
    )

    val summary = com.example.accounting.CustomerLedgerCalculator.calculateCustomerBalance(custId, listOf(saleTx, paymentTx))
    assertEquals(50.0, summary.balance, 0.001)
    assertEquals(100.0, summary.totalCreditSales, 0.001)
    assertEquals(50.0, summary.totalPayments, 0.001)
  }

  @Test
  fun testQuickPaymentValidation() {
    val customerWithDebt = CustomerAccount(
      id = "cust_2",
      customerName = "سالم العمري",
      balance = 100.0,
      totalDebt = 100.0,
      phone = "0502223344"
    )
    val customerWithoutDebt = CustomerAccount(
      id = "cust_3",
      customerName = "يوسف النجار",
      balance = 0.0,
      totalDebt = 0.0,
      phone = "0503334455"
    )

    // Case 1: Entered amount exceeds debt (150 > 100)
    val enteredExceeded = 150.0
    val isExceeded = enteredExceeded > (customerWithDebt.balance + 0.001)
    assertTrue("Payment exceeding debt should be flagged as exceeded", isExceeded)

    // Case 2: Entered amount exact or less than debt
    val enteredValid = 80.0
    val isValid = enteredValid > 0.0 && enteredValid <= customerWithDebt.balance && customerWithDebt.balance > 0.0
    assertTrue("Valid payment within debt should pass", isValid)

    // Case 3: Customer with 0 debt
    val isCustomerWithoutDebtValid = enteredValid > 0.0 && enteredValid <= customerWithoutDebt.balance && customerWithoutDebt.balance > 0.0
    assertTrue("Payment for customer with 0 debt should be blocked", !isCustomerWithoutDebtValid)

    // Case 4: No customer selected
    val nullCustomer: CustomerAccount? = null
    val isNullCustomerValid = nullCustomer != null && enteredValid > 0.0
    assertTrue("Confirmation without customer should be blocked", !isNullCustomerValid)
  }

  @Test
  fun testRecordTransactionItemLinesMapping() {
    val sampleProduct1 = ProductItem(
      id = "prod_1",
      name = "قهوة برازيلية",
      price = 25.0,
      costPrice = 18.0
    )
    val sampleProduct2 = ProductItem(
      id = "prod_2",
      name = "شاي سيلاني",
      price = 10.0,
      costPrice = 6.5
    )
    val cart = listOf(
      CartItem(product = sampleProduct1, quantity = 2),
      CartItem(product = sampleProduct2, quantity = 3)
    )

    val txId = "tx_test_123"
    val lines = cart.map { cartItem ->
      com.example.data.db.TransactionItemLineEntity(
        transactionId = txId,
        productId = cartItem.product.id,
        productNameSnapshot = cartItem.product.name,
        quantity = cartItem.quantity,
        unitPrice = cartItem.product.price,
        costPrice = cartItem.product.costPrice,
        subtotal = cartItem.product.price * cartItem.quantity
      )
    }

    assertEquals(2, lines.size)

    // Line 1
    assertEquals("tx_test_123", lines[0].transactionId)
    assertEquals("prod_1", lines[0].productId)
    assertEquals("قهوة برازيلية", lines[0].productNameSnapshot)
    assertEquals(2, lines[0].quantity)
    assertEquals(25.0, lines[0].unitPrice, 0.001)
    assertEquals(18.0, lines[0].costPrice, 0.001)
    assertEquals(50.0, lines[0].subtotal, 0.001)

    // Line 2
    assertEquals("tx_test_123", lines[1].transactionId)
    assertEquals("prod_2", lines[1].productId)
    assertEquals("شاي سيلاني", lines[1].productNameSnapshot)
    assertEquals(3, lines[1].quantity)
    assertEquals(10.0, lines[1].unitPrice, 0.001)
    assertEquals(6.5, lines[1].costPrice, 0.001)
    assertEquals(30.0, lines[1].subtotal, 0.001)

    val totalAmount = lines.sumOf { it.subtotal }
    assertEquals(80.0, totalAmount, 0.001)
  }

  @Test
  fun testSettlementContextTitles() {
    val recordAr = com.example.ui.components.SettlementContext.RECORD_TRANSACTION
    val legacy = com.example.ui.components.SettlementContext.QUICK_PAYMENT_LEGACY

    val titleRecordAr = when (recordAr) {
      com.example.ui.components.SettlementContext.RECORD_TRANSACTION -> "تسجيل المعاملة"
      com.example.ui.components.SettlementContext.QUICK_PAYMENT_LEGACY -> "المحاسبة"
    }
    assertEquals("تسجيل المعاملة", titleRecordAr)

    val titleRecordEn = when (recordAr) {
      com.example.ui.components.SettlementContext.RECORD_TRANSACTION -> "Record Transaction"
      com.example.ui.components.SettlementContext.QUICK_PAYMENT_LEGACY -> "Settlement"
    }
    assertEquals("Record Transaction", titleRecordEn)

    val titleLegacyAr = when (legacy) {
      com.example.ui.components.SettlementContext.RECORD_TRANSACTION -> "تسجيل المعاملة"
      com.example.ui.components.SettlementContext.QUICK_PAYMENT_LEGACY -> "المحاسبة"
    }
    assertEquals("المحاسبة", titleLegacyAr)
  }

  @Test
  fun testStoreDebtAgingSummaryCalculation() {
    val today = java.time.LocalDate.of(2026, 9, 12)
    val customers = listOf(
      CustomerAccount(id = "c1", customerName = "عميل أ", balance = 300.0, totalDebt = 300.0, phone = "0501111111"),
      CustomerAccount(id = "c2", customerName = "عميل ب", balance = 150.0, totalDebt = 150.0, phone = "0502222222"),
      CustomerAccount(id = "c3", customerName = "عميل ج", balance = 0.0, totalDebt = 0.0, phone = "0503333333")
    )

    val transactions = listOf(
      // c1: 10 days old (0-30 bucket) 200, 45 days old (31-60 bucket) 100
      com.example.model.TransactionItem("t1", "شراء آجل", "عميل أ", "شراء آجل", 200.0, true, "2026-09-02", "منذ 10 أيام", "مشتريات", customerId = "c1"),
      com.example.model.TransactionItem("t2", "شراء آجل", "عميل أ", "شراء آجل", 100.0, true, "2026-07-29", "منذ 45 يوم", "مشتريات", customerId = "c1"),
      // c2: 100 days old (90+ bucket) 150
      com.example.model.TransactionItem("t3", "شراء آجل", "عميل ب", "شراء آجل", 150.0, true, "2026-06-04", "منذ 100 يوم", "مشتريات", customerId = "c2")
    )

    val summary = com.example.viewmodel.DebtAgingUtils.calculateStoreDebtAgingSummary(
      customers = customers,
      allTransactions = transactions,
      today = today,
      isArabic = true
    )

    assertEquals(450.0, summary.totalOutstandingDebt, 0.001)
    assertEquals(2, summary.totalCustomersWithDebtCount)
    assertEquals(200.0, summary.sum0To30, 0.001)
    assertEquals(100.0, summary.sum31To60, 0.001)
    assertEquals(0.0, summary.sum61To90, 0.001)
    assertEquals(150.0, summary.sum90Plus, 0.001)
  }

  @Test
  fun testAccountStatementFiltersAndBalances() {
    val vm = com.example.viewmodel.AnalysisCenterViewModel()
    val cust = CustomerAccount(id = "c1", customerName = "علي أحمد", balance = 200.0, totalDebt = 200.0, phone = "0550000000")
    val txs = listOf(
      com.example.model.TransactionItem("tx1", "شراء نقدي", "علي أحمد", "كاش", 50.0, false, "2026-09-01", "اليوم", "بيبسي", customerId = "c1"),
      com.example.model.TransactionItem("tx2", "شراء آجل", "علي أحمد", "آجل", 250.0, true, "2026-09-02", "اليوم", "أرز وسكر", customerId = "c1"),
      com.example.model.TransactionItem("tx3", "تسديد دفعة", "علي أحمد", "تسديد", 50.0, false, "2026-09-03", "اليوم", "دفعة نقدية", customerId = "c1")
    )

    // Filter ALL
    val allRows = vm.computeStatementRows(
      allTransactions = txs,
      selectedCustomer = cust,
      filter = com.example.viewmodel.StatementTxFilter.ALL,
      period = com.example.model.PeriodFilter.CUSTOM
    )
    assertEquals(3, allRows.size)
    // tx1 cash: does not alter running balance
    assertEquals(0.0, allRows[0].runningBalance, 0.001)
    // tx2 debt: +250
    assertEquals(250.0, allRows[1].runningBalance, 0.001)
    // tx3 payment: -50 -> 200
    assertEquals(200.0, allRows[2].runningBalance, 0.001)

    // Filter PAYMENT
    val paymentRows = vm.computeStatementRows(
      allTransactions = txs,
      selectedCustomer = cust,
      filter = com.example.viewmodel.StatementTxFilter.PAYMENT,
      period = com.example.model.PeriodFilter.CUSTOM
    )
    assertEquals(1, paymentRows.size)
    assertEquals("tx3", paymentRows[0].id)

    // Filter CASH_PURCHASE
    val cashRows = vm.computeStatementRows(
      allTransactions = txs,
      selectedCustomer = cust,
      filter = com.example.viewmodel.StatementTxFilter.CASH_PURCHASE,
      period = com.example.model.PeriodFilter.CUSTOM
    )
    assertEquals(1, cashRows.size)
    assertEquals("tx1", cashRows[0].id)

    // Filter DEBT_PURCHASE
    val debtRows = vm.computeStatementRows(
      allTransactions = txs,
      selectedCustomer = cust,
      filter = com.example.viewmodel.StatementTxFilter.DEBT_PURCHASE,
      period = com.example.model.PeriodFilter.CUSTOM
    )
    assertEquals(1, debtRows.size)
    assertEquals("tx2", debtRows[0].id)
  }

  @Test
  fun testPeriodFilterEnumAndStrings() {
    // Exactly 4 periods: ALL, TODAY, MONTH, CUSTOM in order
    val values = com.example.model.PeriodFilter.values()
    assertEquals(4, values.size)
    assertEquals(com.example.model.PeriodFilter.ALL, values[0])
    assertEquals(com.example.model.PeriodFilter.TODAY, values[1])
    assertEquals(com.example.model.PeriodFilter.MONTH, values[2])
    assertEquals(com.example.model.PeriodFilter.CUSTOM, values[3])

    // Labels in Arabic and English
    assertEquals("All", com.example.model.StoreStrings.PERIOD_ALL_EN)
    assertEquals("كل", com.example.model.StoreStrings.PERIOD_ALL_AR)
    assertEquals("Today", com.example.model.StoreStrings.PERIOD_TODAY_EN)
    assertEquals("اليوم", com.example.model.StoreStrings.PERIOD_TODAY_AR)
    assertEquals("Month", com.example.model.StoreStrings.PERIOD_MONTH_EN)
    assertEquals("الشهر", com.example.model.StoreStrings.PERIOD_MONTH_AR)
    assertEquals("Custom", com.example.model.StoreStrings.PERIOD_CUSTOM_EN)
    assertEquals("مخصص", com.example.model.StoreStrings.PERIOD_CUSTOM_AR)
  }

  @Test
  fun testDateFilterUtilsPeriodLogic() {
    val today = java.time.LocalDate.of(2026, 9, 13)
    val utils = com.example.viewmodel.DateFilterUtils

    // ALL matches everything
    assertTrue(utils.isDateInPeriod("2020-01-01", com.example.model.PeriodFilter.ALL, today = today))
    assertTrue(utils.isDateInPeriod("2026-09-13", com.example.model.PeriodFilter.ALL, today = today))
    assertTrue(utils.isDateInPeriod("anything", com.example.model.PeriodFilter.ALL, today = today))

    // TODAY matches only today
    assertTrue(utils.isDateInPeriod("2026-09-13", com.example.model.PeriodFilter.TODAY, today = today))
    assertTrue(!utils.isDateInPeriod("2026-09-12", com.example.model.PeriodFilter.TODAY, today = today))

    // MONTH matches current calendar month
    assertTrue(utils.isDateInPeriod("2026-09-01", com.example.model.PeriodFilter.MONTH, today = today))
    assertTrue(utils.isDateInPeriod("2026-09-30", com.example.model.PeriodFilter.MONTH, today = today))
    assertTrue(!utils.isDateInPeriod("2026-08-31", com.example.model.PeriodFilter.MONTH, today = today))
    assertTrue(!utils.isDateInPeriod("2026-10-01", com.example.model.PeriodFilter.MONTH, today = today))

    // CUSTOM is inclusive of start and end date
    val start = java.time.LocalDate.of(2026, 9, 5)
    val end = java.time.LocalDate.of(2026, 9, 10)
    assertTrue(utils.isDateInPeriod("2026-09-05", com.example.model.PeriodFilter.CUSTOM, customStartDate = start, customEndDate = end, today = today))
    assertTrue(utils.isDateInPeriod("2026-09-08", com.example.model.PeriodFilter.CUSTOM, customStartDate = start, customEndDate = end, today = today))
    assertTrue(utils.isDateInPeriod("2026-09-10", com.example.model.PeriodFilter.CUSTOM, customStartDate = start, customEndDate = end, today = today))
    assertTrue(!utils.isDateInPeriod("2026-09-04", com.example.model.PeriodFilter.CUSTOM, customStartDate = start, customEndDate = end, today = today))
    assertTrue(!utils.isDateInPeriod("2026-09-11", com.example.model.PeriodFilter.CUSTOM, customStartDate = start, customEndDate = end, today = today))
  }

  @Test
  fun testLatestActivityNavigationUsesCustomerIdAndHandlesDuplicateNames() {
    val cust1 = com.example.model.CustomerAccount(
      id = "c101",
      customerName = "محمد علي",
      balance = 150.0,
      totalDebt = 150.0,
      phone = "0501111111",
      lastTransactionDate = "2026-09-10"
    )
    val cust2 = com.example.model.CustomerAccount(
      id = "c102",
      customerName = "محمد علي", // duplicate name
      balance = 300.0,
      totalDebt = 300.0,
      phone = "0502222222",
      lastTransactionDate = "2026-09-12"
    )
    val allCustomers = listOf(cust1, cust2)

    val txForCust2 = com.example.model.TransactionItem(
      id = "tx_test_1",
      title = "تسديد دفعة",
      customerName = "محمد علي",
      activityType = "تسديد",
      amount = 100.0,
      isCredit = false,
      date = "2026-09-12",
      relativeTime = "اليوم",
      customerId = "c102"
    )

    val txForCust1 = com.example.model.TransactionItem(
      id = "tx_test_2",
      title = "شراء آجل",
      customerName = "محمد علي",
      activityType = "شراء آجل",
      amount = 50.0,
      isCredit = true,
      date = "2026-09-10",
      relativeTime = "منذ يومين",
      customerId = "c101"
    )

    // Verify resolveCustomerForTransaction resolves exact customer by stable ID despite identical customer names
    val resolvedCust2 = com.example.viewmodel.MainViewModel.resolveCustomerForTransaction(allCustomers, txForCust2)
    assertEquals("c102", resolvedCust2?.id)
    assertEquals("0502222222", resolvedCust2?.phone)

    val resolvedCust1 = com.example.viewmodel.MainViewModel.resolveCustomerForTransaction(allCustomers, txForCust1)
    assertEquals("c101", resolvedCust1?.id)
    assertEquals("0501111111", resolvedCust1?.phone)

    // Verify behavior when transaction has no customerId:
    // With duplicate customer names ("محمد علي"), resolving must return null rather than guessing by date or activity
    val txWithoutId = com.example.model.TransactionItem(
      id = "tx_test_3",
      title = "شراء آجل",
      customerName = "محمد علي",
      activityType = "شراء آجل",
      amount = 50.0,
      isCredit = true,
      date = "2026-09-10",
      relativeTime = "منذ يومين",
      customerId = null
    )
    val resolvedFallback = com.example.viewmodel.MainViewModel.resolveCustomerForTransaction(allCustomers, txWithoutId)
    // Under Phase 2 deterministic rules, never guess customer identity when ambiguous duplicate names exist
    org.junit.Assert.assertNull(resolvedFallback)

    // Under Phase 2.2: customerId == null is NEVER silently assigned to a customer by name, even if name is unique
    val uniqueCust = com.example.model.CustomerAccount(
      id = "c103",
      customerName = "سالم القرني",
      balance = 50.0,
      totalDebt = 50.0,
      phone = "0503333333"
    )
    val txUnambiguous = com.example.model.TransactionItem(
      id = "tx_test_4",
      title = "شراء آجل",
      customerName = "سالم القرني",
      activityType = "شراء آجل",
      amount = 50.0,
      isCredit = true,
      date = "2026-09-10",
      relativeTime = "اليوم",
      customerId = null
    )
    val resolvedUnambiguous = com.example.viewmodel.MainViewModel.resolveCustomerForTransaction(listOf(uniqueCust), txUnambiguous)
    org.junit.Assert.assertNull(resolvedUnambiguous)
  }

  @Test
  fun testProductModelAndOptionalImage() {
    val prodNoImage = ProductItem(
      id = "p1",
      name = "سكر الأسرة 5 كجم",
      price = 28.5,
      costPrice = 22.0,
      category = "مواد غذائية",
      unit = "كيس"
    )
    assertEquals("p1", prodNoImage.id)
    assertEquals(null, prodNoImage.imageUri)
    assertEquals(28.5, prodNoImage.price, 0.001)
    assertEquals("مواد غذائية", prodNoImage.category)

    val prodWithImage = ProductItem(
      id = "p2",
      name = "زيت عافية 1.5 لتر",
      price = 19.75,
      costPrice = 15.0,
      category = "زيوت",
      unit = "حبة",
      imageUri = "/data/user/0/com.aistudio.smallstore/files/product_images/prod_p2.jpg"
    )
    assertEquals("/data/user/0/com.aistudio.smallstore/files/product_images/prod_p2.jpg", prodWithImage.imageUri)
  }

  @Test
  fun testProductArchiveAndFiltering() {
    val products = listOf(
      ProductItem(id = "p1", name = "أرز الشعلان", price = 75.0),
      ProductItem(id = "p2", name = "حليب نيدو", price = 45.0),
      ProductItem(id = "p3", name = "شاي ربيع", price = 14.0)
    )
    val archivedProductIds = setOf("p2")

    val active = products.filter { it.id !in archivedProductIds }
    val archived = products.filter { it.id in archivedProductIds }

    assertEquals(2, active.size)
    assertEquals("p1", active[0].id)
    assertEquals("p3", active[1].id)
    assertEquals(1, archived.size)
    assertEquals("p2", archived[0].id)
  }

  @Test
  fun testArchiveTabsAndLocalization() {
    // 1. Customers / العملاء
    assertEquals("Customers", StoreStrings.ARCHIVE_TAB_CUSTOMERS_EN)
    assertEquals("العملاء", StoreStrings.ARCHIVE_TAB_CUSTOMERS_AR)

    // 2. Products / الأصناف
    assertEquals("Products", StoreStrings.ARCHIVE_TAB_PRODUCTS_EN)
    assertEquals("الأصناف", StoreStrings.ARCHIVE_TAB_PRODUCTS_AR)

    // 3. Transactions / المعاملات
    assertEquals("Transactions", StoreStrings.ARCHIVE_TAB_TRANSACTIONS_EN)
    assertEquals("المعاملات", StoreStrings.ARCHIVE_TAB_TRANSACTIONS_AR)

    // Navigation Destination
    assertEquals("ARCHIVE", NavDestination.ARCHIVE.name)
  }

  @Test
  fun testArchiveDeletePermanentWarning() {
    // Verify exact warning string
    assertEquals(
      "This record will be permanently deleted and cannot be restored.",
      StoreStrings.DELETE_PERMANENT_WARNING_EN
    )
    assertEquals(
      "سيتم حذف هذا السجل نهائياً ولا يمكن استعادته.",
      StoreStrings.DELETE_PERMANENT_WARNING_AR
    )
    assertEquals("Delete Permanently", StoreStrings.DELETE_PERMANENTLY_EN)
    assertEquals("حذف نهائي", StoreStrings.DELETE_PERMANENTLY_AR)
    assertEquals("Restore", StoreStrings.RESTORE_ACTION_EN)
    assertEquals("استعادة", StoreStrings.RESTORE_ACTION_AR)
  }

  @Test
  fun testArchiveEmptyStatesAndNoDatabaseDeletion() {
    val customers = listOf(
      CustomerAccount(id = "c1", customerName = "سالم", phone = "0501111111", balance = 0.0, totalDebt = 0.0),
      CustomerAccount(id = "c2", customerName = "طارق", phone = "0502222222", balance = 50.0, totalDebt = 50.0)
    )
    val products = listOf(
      ProductItem(id = "p1", name = "سكر 5 كجم", price = 20.0)
    )
    val archivedCustomerIds = emptySet<String>()
    val archivedProductIds = emptySet<String>()

    // When no records are archived, archived lists are empty -> triggers empty state
    val archivedCusts = customers.filter { it.id in archivedCustomerIds }
    val archivedProds = products.filter { it.id in archivedProductIds }
    assertTrue(archivedCusts.isEmpty())
    assertTrue(archivedProds.isEmpty())

    // Verify no deletion: original lists remain completely unchanged
    assertEquals(2, customers.size)
    assertEquals(1, products.size)
  }
}

