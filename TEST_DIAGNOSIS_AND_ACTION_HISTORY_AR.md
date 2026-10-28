# التقرير الشامل: سجل الإجراءات (Action History) والتشخيص الكامل لاختبارات SmallStore

تاريخ التقرير: 28 أكتوبر 2026  
حالة البناء: Build Succeeded (مترجم بنجاح)  
نتائج الاختبارات: 366 إجمالي | 340 نجح | 26 فشل  

---

# القسم الأول: سجل الإجراءات الكامل (Action History Log)

## 1. ملخص التسلسل الزمني للأحداث والإجراءات

### الإجراء الأول: تحسين واجهة شاشة المشتريات وعناصر سلة المشتريات
- **الهدف:** تحسين تجربة المستخدم في شاشة المشتريات `PurchasesScreen.kt` وعناصر سلة المشتريات `CartLineItemRow.kt` وفق الشروط الصارمة (عدم المساس بالمنطق المحاسبي أو قواعد البيانات).
- **التعديلات المنفذة:**
  1. `app/src/main/java/com/example/ui/screens/PurchasesScreen.kt`:
     - تطوير شريط البحث ليظهر قائمة منسدلة بالاقتراحات تلقائياً عند الكتابة.
     - إضافة زر سهم لفتح وإغلاق القائمة المنسدلة يدوياً دون مسح النص أو المنتج المحدد.
     - إضافة زر مسح (Clear Icon) يقوم بمسح البحث وإلغاء تصفية المنتج واستعادة القائمة الكاملة دون تعديل أي سجل في قاعدة البيانات.
     - تمييز المنتج المحدد وإغلاق الاقتراحات عند الاختيار وتحديث بطاقة نشاط المنتج.
  2. `app/src/main/java/com/example/ui/screens/CartLineItemRow.kt`:
     - زيادة التباعد الأفقي بين زري `+` و `-` لسهولة اللمس على شاشات الجوال.
     - جعل رقم الكمية قابلاً للنقر لفتح حوار إدخال رقمي مباشر مع التحقق من الأعداد الصحيحة الموجبة وعدم قبول قيم سالبة أو صفرية.
     - الحفاظ على زر الحذف مستقلاً وكاملاً.

### الإجراء الثاني: إصلاح خطأ البناء في StatisticsTabContent.kt
- **الهدف:** حل خطأ الترجمة الناشئ عن استخدام `by` مع `MutableState<Boolean>` لمتغيري `showCostDetails` و `showMetricInfo`.
- **التعديلات المنفذة:**
  - في `app/src/main/java/com/example/ui/screens/StatisticsTabContent.kt`:
    إضافة الاستيرادين:
    ```kotlin
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.setValue
    ```
  - تم التحقق من عدم وجود أي تعارض في الاستيرادات.

### الإجراء الثالث: إصلاح خطأ استيراد DateFilterUtils في AnalysisCenterScreen.kt
- **المشكلة:** ظهر خطأ ترقيعي أثناء ترجمة `AnalysisCenterScreen.kt` عند السطر 440:
  `Unresolved reference 'DateFilterUtils'`
- **الإصلاح:**
  - إضافة `import com.example.viewmodel.DateFilterUtils` في ملف `AnalysisCenterScreen.kt`.
  - التحقق من نجاح الترجمة الكاملة للمشروع عبر `compile_applet` وظهور رسالة: `Build succeeded - the applet is compiled`.

### الإجراء الرابع: تنفيذ فحص اختبارات الوحدة وتشخيص الفشل (Diagnosis Phase)
- **الأمر المنفذ:**
  `gradle :app:testDebugUnitTest`
- **النتيجة:**
  - تم تشغيل 366 اختباراً لوحدة التحكم والمحاسبة والـ ViewModel والمستودعات.
  - نجح 340 اختباراً (نسبة نجاح تفوق 93%).
  - فشل 26 اختباراً.
- **الإجراء التحليلي:**
  - قراءة وفحص ملفات تقارير XML للاختبارات الفاشلة في:
    `app/build/test-results/testDebugUnitTest/TEST-*.xml`
  - تحليل سلاسل الاستثناءات وتحديد أول سطر كود تسبب في كل خطأ.
  - تجميع الـ 26 فشلاً في 7 أسباب جذرية أساسية (Root Causes).
  - تصنيف كل مشكلة بحسب كونها (خلل إنتاج / إعدادات اختبار / تعارض معمارية حديثة / انعدام تهيئة).
  - تقييم الأثر المحاسبي لكل سبب والخطوات المطلوبة لحلها بدقة وأمان.

---

# القسم الثاني: تقرير التشخيص الفني والمحاسبي الشامل للاختبارات الـ 26 الفاشلة

## أولاً: جدول حصر الاختبارات الـ 26 الفاشلة وأسبابها الجذرية

| # | اسم كلاس الاختبار | اسم دالة الاختبار | نوع الاستثناء | أول سطر كود في المشروع تسبب بالفشل | السبب الجذري والتصنيف |
|---|---|---|---|---|---|
| 1 | `InventoryLedgerPhase11Test` | `testAverageCostUpdate` | `IllegalArgumentException` | `PurchasesRepository.kt:122` | السبب 1: عدم زرع طريقة الدفع `pm_cash` في قاعدة بيانات الاختبار (إعدادات اختبار). |
| 2 | `InventoryLedgerPhase11Test` | `testCostOfGoodsSoldCalculation` | `IllegalArgumentException` | `PurchasesRepository.kt:122` | السبب 1: نفس السبب السابق (إعدادات اختبار). |
| 3 | `InventoryLedgerPhase11Test` | `testDeficitSettlementAdjustsCostAndProfit` | `IllegalArgumentException` | `PurchasesRepository.kt:122` | السبب 1: نفس السبب السابق (إعدادات اختبار). |
| 4 | `InventoryLedgerPhase11Test` | `testExcessSettlementReducesDeficit` | `IllegalArgumentException` | `PurchasesRepository.kt:122` | السبب 1: نفس السبب السابق (إعدادات اختبار). |
| 5 | `InventoryLedgerPhase11Test` | `testFifoDepletionAcrossBatches` | `IllegalArgumentException` | `PurchasesRepository.kt:122` | السبب 1: نفس السبب السابق (إعدادات اختبار). |
| 6 | `InventoryLedgerPhase11Test` | `testFifoLayersMaintainedAccurately` | `IllegalArgumentException` | `PurchasesRepository.kt:122` | السبب 1: نفس السبب السابق (إعدادات اختبار). |
| 7 | `InventoryLedgerPhase11Test` | `testInventoryCountDeficitAdjustment` | `IllegalArgumentException` | `PurchasesRepository.kt:122` | السبب 1: نفس السبب السابق (إعدادات اختبار). |
| 8 | `InventoryLedgerPhase11Test` | `testInventoryCountSurplusAdjustment` | `IllegalArgumentException` | `PurchasesRepository.kt:122` | السبب 1: نفس السبب السابق (إعدادات اختبار). |
| 9 | `InventoryLedgerPhase11Test` | `testPurchaseReturnRestoresCost` | `IllegalArgumentException` | `PurchasesRepository.kt:122` | السبب 1: نفس السبب السابق (إعدادات اختبار). |
| 10 | `InventoryLedgerPhase11Test` | `testReturnRestoresInventoryLayer` | `IllegalArgumentException` | `PurchasesRepository.kt:122` | السبب 1: نفس السبب السابق (إعدادات اختبار). |
| 11 | `InventoryLedgerPhase11Test` | `testWeightedAverageCostFlow` | `IllegalArgumentException` | `PurchasesRepository.kt:122` | السبب 1: نفس السبب السابق (إعدادات اختبار). |
| 12 | `SaleEnginePhase3Test` | `testSaleNumberSequential` | `AssertionError` | `SaleEnginePhase3Test.kt:121` | السبب 2: تفعيل وضع العرض `BuildConfig.DEMO_MODE` يؤدي لحقن 6 مبيعات تجريبية مسبقة عند بدء ViewModel (إعدادات بيئة اختبار). |
| 13 | `SaleEnginePhase3Test` | `testPaymentMethodsSplit` | `AssertionError` | `SaleEnginePhase3Test.kt:233` | السبب 2: تلوث قاعدة البيانات التجريبية ببيانات الـ Demo (إعدادات بيئة اختبار). |
| 14 | `SaleEnginePhase3Test` | `testCompleteSaleCashPayment` | `AssertionError` | `SaleEnginePhase3Test.kt:99` | السبب 2: تلوث قاعدة البيانات التجريبية ببيانات الـ Demo (إعدادات بيئة اختبار). |
| 15 | `SaleEnginePhase3Test` | `testCompleteSaleMultipleItems` | `AssertionError` | `SaleEnginePhase3Test.kt:168` | السبب 2: تلوث قاعدة البيانات التجريبية ببيانات الـ Demo (إعدادات بيئة اختبار). |
| 16 | `SaleEnginePhase3Test` | `testPriceOverrideRecorded` | `AssertionError` | `SaleEnginePhase3Test.kt:281` | السبب 2: تلوث قاعدة البيانات التجريبية ببيانات الـ Demo (إعدادات بيئة اختبار). |
| 17 | `SaleEnginePhase3Test` | `testSaleLineItemCalculations` | `AssertionError` | `SaleEnginePhase3Test.kt:145` | السبب 2: تلوث قاعدة البيانات التجريبية ببيانات الـ Demo (إعدادات بيئة اختبار). |
| 18 | `SaleEnginePhase3Test` | `testStockDepletionOnSale` | `AssertionError` | `SaleEnginePhase3Test.kt:190` | السبب 2: تلوث قاعدة البيانات التجريبية ببيانات الـ Demo (إعدادات بيئة اختبار). |
| 19 | `TransactionPhysicalImmutabilityTest` | `testReconciliationDetectsDirectModification` | `AssertionError` | `TransactionPhysicalImmutabilityTest.kt:70` | السبب 3: اختبار عتيق يزرع في جدول قديم `transactions` بينما المحاسبة الحديثة تعتمد جداول المبيعات والمدفوعات والمشتريات (اختبار غير متوافق). |
| 20 | `TransactionPhysicalImmutabilityTest` | `testReverseTransactionUpdatesBalancesCorrectly` | `AssertionError` | `TransactionPhysicalImmutabilityTest.kt:100` | السبب 3: نفس السبب السابق (اختبار غير متوافق). |
| 21 | `TransactionPhysicalImmutabilityTest` | `testReversalAuditTrailPreserved` | `AssertionError` | `TransactionPhysicalImmutabilityTest.kt:127` | السبب 3: نفس السبب السابق (اختبار غير متوافق). |
| 22 | `TransactionPhysicalImmutabilityTest` | `testPhysicalDeleteFailsOrLeavesAudit` | `AssertionError` | `TransactionPhysicalImmutabilityTest.kt:151` | السبب 3: نفس السبب السابق (اختبار غير متوافق). |
| 23 | `CustomerDebtAgingReconciliationTest` | `testG_partialPaymentOldestInvoice_appliedFifo` | `AssertionError` | `CustomerDebtAgingReconciliationTest.kt:225` | السبب 4: خلل برمجي فعلي في خوارزمية أعمار الديون `AnalysisCenterViewModel.kt:173-195` حيث تم عكس ترتيب سداد FIFO (خلل إنتاج حقيقي). |
| 24 | `CustomerStatementReconciliationTest` | `testB_initialBalance_createsStartingRow` | `AssertionError` | `CustomerStatementReconciliationTest.kt:80` | السبب 5: دالة `computeStatementRows` تولد افتراضياً سطر رصيد افتتاحي `includeOpeningBalanceRow = true` بينما الاختبار يتوقع سطراً واحداً فقط (عدم توافق إعدادات استدعاء). |
| 25 | `ExampleRobolectricTest` | `testReportCsvExportsSalesAndTransactionsAndCustomer` | `AssertionError` | `ExampleRobolectricTest.kt:113` | السبب 6: اختلاف نصي في عنوان قسم التقرير بين `SalesReportExporter.kt` ("ملخص التقرير") والاختبار المتوقع ("ملخص المبيعات") (اختبار غير متوافق). |
| 26 | `BackupV2SerializationTest` | `testFullRoundTrip_preservesAllEntityCountsAndIds` | `RuntimeException` | `BackupV2SerializationTest.kt:123` | السبب 7: الاختبار يفتقر لمشغّل `@RunWith(RobolectricTestRunner::class)`، مما يجعل استدعاء مكتبة أندرويد `org.json.JSONObject` غير محاكى في JVM (إعدادات اختبار). |

---

## ثانياً: التحليل المعمق للأسباب الجذرية السبعة (Root Causes Analysis)

### السبب الجذري 1: عدم تهيئة طرق الدفع في قاعدة بيانات اختبارات `InventoryLedgerPhase11Test` (11 اختباراً)
- **الأعراض:**
  فشل 11 اختباراً بالاستثناء:
  `java.lang.IllegalArgumentException: Payment method not found: pm_cash`
  عند السطر 122 في ملف `PurchasesRepository.kt`:
  ```kotlin
  val paymentMethod = paymentMethodDao.getById(paymentMethodId)
      ?: throw IllegalArgumentException("Payment method not found: $paymentMethodId")
  ```
- **التشخيص:**
  في ملف `InventoryLedgerPhase11Test.kt`، تقوم دالة الإعداد `@Before setup()` بإنشاء قاعدة بيانات Room في الذاكرة ولكنها لا تقوم بإدراج طرق الدفع الأساسية (مثل `pm_cash`) في جدول `payment_methods`.
- **التصنيف:** إعدادات غير صحيحة في الاختبار (Test Setup Missing Seed Data). كود الإنتاج سليم ويحمي صحة المعاملات المحاسبية بفرض وجود طريقة دفع مسجلة.

---

### السبب الجذري 2: تلوث بيئة اختبار `SaleEnginePhase3Test` ببيانات العرض التجريبية (7 اختبارات)
- **الأعراض:**
  توقع الاختبار أن يكون رقم الفاتورة الأولى هو 1، لكنه وجد 7:
  `AssertionError: expected:<1> but was:<7>`
  وتوقع رصيداً مخزنياً 80 فوجده 78.
- **التشخيص:**
  في ملف `app/build.gradle.kts`، تم ضبط `buildConfigField("Boolean", "DEMO_MODE", "true")`.
  وعند تهيئة `MainViewModel` داخل الاختبار، يتم استدعاء دالة:
  `demoDataSeeder.seedDemoDataIfEmpty()`
  مما يؤدي لحقن 6 مبيعات تجريبية وخصم كميات من المخزون قبل بدء الاختبار.
- **التصنيف:** خلل في بيئة وإعدادات الاختبار. يجب تعطيل حقن بيانات العرض داخل الاختبارات المعزولة.

---

### السبب الجذري 3: تعارض معمارية السجل المحاسبي الحديث في `TransactionPhysicalImmutabilityTest` (4 اختبارات)
- **الأعراض:**
  الاختبار يقوم بإدراج سجل في جدول `transactions` القديم:
  `transactionDao.insert(TransactionEntity(... amount = 1000.0, type = "SALE"))`
  ثم يفحص رصيد العميل:
  `assertEquals(1000.0, customer.balance, 0.01)`
  فيجد الرصيد `0.0`.
- **التشخيص:**
  نظام المحاسبة الحديث في التطبيق تم تحسينه لحساب الأرصدة بدقة من السجلات الفعلية (جداول `sales` و `payments` و `purchases`) بدلاً من الاعتماد على جدول `transactions` الأرشيفي التاريخي القديم.
- **التصنيف:** عدم توافق الاختبار مع المعمارية المحاسبية المحدثة (Outdated Legacy Test).

---

### السبب الجذري 4: خلل برمجي فعلي في خوارزمية أعمار الديون (Customer Debt Aging FIFO) (اختبار 1)
- **الأعراض:**
  في `CustomerDebtAgingReconciliationTest > testG`:
  `java.lang.AssertionError: Expected 0-30 bucket to have remaining debt expected:<300.0> but was:<0.0>`
- **التشخيص الدقيق في كود الإنتاج:**
  في ملف `app/src/main/java/com/example/viewmodel/AnalysisCenterViewModel.kt`، عند الأسطر 173-195:
  تقوم الدالة بحساب مجموع المدفوعات والديون، ثم تبدأ بحلقة تكرار على الفواتير من الأقدم إلى الأحدث (`invoices.sortedBy { it.date }`). لكن بدلاً من خصم المدفوعات من الفواتير الأقدم وتوزيع المتبقي على الفئات، تقوم الخوارزمية باستهلاك الدين المتبقي على الفواتير الأقدم مباشرة وتصفير الفواتير الأحدث، مما يعكس مبدأ FIFO المحاسبي.
- **التصنيف:** خلل برمجي فعلي في كود الإنتاج (Production Logic Bug) - أولوية قصوى.

---

### السبب الجذري 5: معامل الرصيد الافتتاحي في كشف حساب العميل (اختبار 1)
- **الأعراض:**
  في `CustomerStatementReconciliationTest > testB`:
  `java.lang.AssertionError: expected:<1> but was:<2>`
- **التشخيص:**
  دالة `computeStatementRows` في `CustomerStatementEngine` تحتوي على معامل افتراضي:
  `includeOpeningBalanceRow: Boolean = true`
  والاختبار يختبر وجود معاملة واحدة فقط دون الرصيد الافتتاحي.
- **التصنيف:** عدم توافق معامل الاستدعاء في الاختبار مع الدالة المطورة.

---

### السبب الجذري 6: اختلاف نصي في ترويسة تقرير مبيعات CSV (اختبار 1)
- **الأعراض:**
  في `ExampleRobolectricTest > testReportCsvExportsSalesAndTransactionsAndCustomer`:
  `AssertionError: Exported CSV should contain summary section. content=...`
- **التشخيص:**
  في ملف `app/src/main/java/com/example/export/SalesReportExporter.kt` عند السطر 627، العنوان المطبوع هو:
  `appendLine("--- ملخص التقرير ---")`
  بينما الاختبار يتحقق من النص العتيق:
  `assertTrue(csv.contains("ملخص المبيعات"))`
- **التصنيف:** عدم تطابق نصي بين التقرير المحدث واختبار Robolectric القديم.

---

### السبب الجذري 7: غياب مشغل Robolectric في اختبار النسخ الاحتياطي `BackupV2SerializationTest` (اختبار 1)
- **الأعراض:**
  `java.lang.RuntimeException: Method put in org.json.JSONObject not mocked.`
  عند السطر 123 في `BackupV2SerializationTest.kt`.
- **التشخيص:**
  كلاس الاختبار `BackupV2SerializationTest` يعمل كاختبار JUnit خالص دون إضافة تعليق:
  `@RunWith(RobolectricTestRunner::class)`
  وبما أن كود الإنتاج يستخدم مكتبة أندرويد الرسمية `org.json.JSONObject`، فإن JVM الخالص لا يحتوي على تطبيق لها بدون محاكاة Robolectric.
- **التصنيف:** إعدادات ناقصة في ملف الاختبار (Missing Test Runner).

---

## ثالثاً: ترتيب أولويات الإصلاح بحسب الخطورة والأثر المحاسبي

1. **الأولوية القصوى (أثر محاسبي على الإنتاج):**
   - **السبب 4:** إصلاح خوارزمية سداد فواتير العميل بالأقدمية (FIFO) في `AnalysisCenterViewModel.kt`. يضمن دقة تقارير أعمار الديون والمطابقات المالية للعملاء.
2. **الأولوية العالية (إعدادات الاختبارات ونزاهة الحسابات):**
   - **السبب 1:** إضافة زرع طرق الدفع الافتراضية في `InventoryLedgerPhase11Test.kt`. هذا يعيد اجتياز 11 اختباراً دفعة واحدة ويؤكد صحة حسابات تكلفة البضاعة المباعة وطبقات المخزون FIFO.
   - **السبب 2:** عزل تهيئة بيانات العرض في `SaleEnginePhase3Test.kt` ليعبر عن بيئة مبيعات فارغة، مما يعيد اجتياز 7 اختبارات لمستودع المبيعات وخصم المخزون.
3. **الأولوية المتوسطة (توافقية الاختبارات مع النظام الحديث):**
   - **السبب 7:** إضافة `@RunWith(RobolectricTestRunner::class)` إلى `BackupV2SerializationTest.kt`.
   - **السبب 5:** ضبط استدعاء دالة كشف الحساب في `CustomerStatementReconciliationTest.kt`.
   - **السبب 6:** توحيد عنوان التقرير في `ExampleRobolectricTest.kt` أو `SalesReportExporter.kt`.
   - **السبب 3:** تحديث `TransactionPhysicalImmutabilityTest.kt` ليتماشى مع المحاسبة الحديثة المبنية على حركة الحسابات المتعددة بدلاً من الجدول الأرشيفي القديم.

---

## رابعاً: قائمة الملفات المحددة للإصلاح عند صدور الموافقة

| السبب الجذري | الملفات المعنية | الإجراء المقترح |
|---|---|---|
| **السبب 1** | `app/src/test/java/com/example/inventory/InventoryLedgerPhase11Test.kt` | إضافة إدراج طريقة الدفع `pm_cash` في دالة `@Before setup`. |
| **السبب 2** | `app/src/test/java/com/example/sales/SaleEnginePhase3Test.kt` | منع تشغيل `seedDemoDataIfEmpty` أو تنظيف قاعدة البيانات قبل بدء الفحص. |
| **السبب 3** | `app/src/test/java/com/example/transaction/TransactionPhysicalImmutabilityTest.kt` | تحديث استعلامات الاختبار لتعتمد مستودعات المعاملات الحديثة. |
| **السبب 4** | `app/src/main/java/com/example/viewmodel/AnalysisCenterViewModel.kt` | تصحيح معادلة خصم المسدد من فواتير أعمار الديون الأقدم للأحدث (FIFO). |
| **السبب 5** | `app/src/test/java/com/example/customer/CustomerStatementReconciliationTest.kt` | تمرير `includeOpeningBalanceRow = false` للاختبار الذي يفحص معاملة مفردة. |
| **السبب 6** | `app/src/test/java/com/example/ExampleRobolectricTest.kt` | فحص النص المعتمد في التقرير ومطابقته. |
| **السبب 7** | `app/src/test/java/com/example/backup/BackupV2SerializationTest.kt` | إضافة `@RunWith(RobolectricTestRunner::class)` للكلاس. |

---
نهاية التقرير الشامل. بانتظار توجيهاتكم الكريمة وموافقتكم قبل إجراء أي تعديل على كود الإنتاج أو ملفات الاختبار.
