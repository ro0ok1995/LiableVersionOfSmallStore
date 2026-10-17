# تقرير تنفيذ تحديث SmallStore — 2026-10-13 (المراجعة الثانية)

## نطاق هذا التحديث

تم البناء على `SmallStore_Accounting_Remediation_Updated_2026-10-13.zip` السابق، وأضيفت إصلاحات واجهة الأرصدة الافتتاحية، ودقة مبالغ الأموال، وتسوية مرتجع المورد النقدية. لم يتم تعديل Gradle أو GitHub Actions أو الاعتماديات عمدًا.

## التغييرات المنفذة

### 1. الأرصدة الافتتاحية
- إضافة مبلغ افتتاحي اختياري واتجاهه في نموذج إنشاء العميل والمورد.
- اتجاه العميل: `DEBIT` = العميل مدين للمحل، و`CREDIT` = المحل مدين للعميل.
- اتجاه المورد: `CREDIT` = المحل مدين للمورد، و`DEBIT` = المورد مدين للمحل.
- إنشاء سجل العميل/المورد وسجل الرصيد الافتتاحي ضمن معاملة قاعدة بيانات واحدة.
- تقريب مبلغ الرصيد الافتتاحي إلى منزلتين عشريتين والتحقق من أنه موجب ومنتهٍ وصالح.
- شاشة إدارة العملاء تقرأ الرصيد من دفتر العميل المحاسبي بدل الاعتماد على حقل الرصيد القديم وحده، وتستخدم دلالات اللون: الأخضر للمال المستحق للمحل، والأحمر لما على المحل، والمحايد للصفر.
- إضافة إجراء لإضافة رصيد افتتاحي لحساب عميل أو مورد قائم؛ يمنع المستودع تسجيل رصيد افتتاحي ثانٍ لنفس الحساب ويطلب استخدام تسوية موثقة بدل التكرار.

### 2. دقة مبالغ الأموال
- إضافة `AccountingDecimal` لاستخدام `BigDecimal` عند التقريب والجمع وضرب سعر الوحدة في الكمية.
- سياسة المبالغ المرحلة: منزلتان عشريتان، والتقريب `HALF_UP`.
- دعم إدخال أمثلة مثل `150.25` و`150,25`، ورفض القيم غير المنتهية في دالة المال.
- تطبيق الحساب العشري على إجماليات الشراء وبنود الشراء والمرتجعات، وعلى تجميع إجمالي بنود البيع عند التحقق.
- إضافة اختبارات لوظائف الحساب العشري.

**حد مهم:** لم يتم تحويل حقول الكميات المخزنة كأعداد صحيحة إلى كسور، ولم تتم إعادة بناء كل جداول Room لدعم الأوزان مثل `1.237 g`. المخطط الحالي يستخدم أنواعًا صحيحة في بعض مسارات الكميات؛ تغييرها بأمان يتطلب ترحيلًا شاملًا ومراجعة كل مسارات المخزون والنسخ الاحتياطي. لذلك لا يُدّعى أن دقة الأوزان/الكميات الكسرية اكتملت في هذا الأرشيف.

### 3. التسوية النقدية لمرتجع المورد
- توسيع `PurchaseReturn` بحقوق التسوية: الجزء الذي يخفض مستحقات المورد، والجزء المسترد نقدًا، والحساب المالي المستلم.
- إضافة ترحيل Room من الإصدار 20 إلى 21، مع ترحيل المرتجعات القديمة باعتبار قيمتها كلها تخفيضًا لمستحقات المورد حتى لا تتغير أرصدتها التاريخية.
- واجهة المرتجع تسمح بالاختيار بين خصم المرتجع من مستحقات المورد أو استرداد نقدي، وتطلب حسابًا ماليًا نشطًا عند الاسترداد النقدي.
- عند الاسترداد النقدي، يخصم المرتجع أولًا مستحقات المورد القائمة؛ وأي قيمة زائدة تُسجل كدخل نقدي فعلي للحساب المختار.
- دفتر المورد يخفض الالتزام فقط بمقدار `payableReductionAmount`، ودفتر الحساب المالي يسجل `cashRefundAmount` كدخل مرة واحدة.
- التحقق من التسوية والحساب المالي وحفظ المرتجع والبنود وحركة المخزون والسجل القديم داخل معاملة واحدة.
- إضافة اختبارات حسابية لتقسيم المرتجع بين تخفيض الالتزام والاسترداد النقدي، وللتأكد من استمرار سلوك المرتجعات القديمة.

## ملفات جديدة
- `app/src/main/java/com/example/accounting/AccountingDecimal.kt`
- `app/src/test/java/com/example/accounting/AccountingDecimalTest.kt`
- `app/src/test/java/com/example/accounting/SupplierReturnSettlementTest.kt`

## ملفات رئيسية معدلة في هذه الجولة
- `app/src/main/java/com/example/SmallStoreApp.kt`
- `app/src/main/java/com/example/accounting/CentralAccountingEngine.kt`
- `app/src/main/java/com/example/accounting/FinancialAccountLedgerCalculator.kt`
- `app/src/main/java/com/example/accounting/SupplierLedgerCalculator.kt`
- `app/src/main/java/com/example/data/db/PurchaseReturn.kt`
- `app/src/main/java/com/example/data/db/SmallStoreDatabase.kt`
- `app/src/main/java/com/example/data/repository/PurchaseReturnRepository.kt`
- `app/src/main/java/com/example/data/repository/PurchasesRepository.kt`
- `app/src/main/java/com/example/data/repository/SalesRepository.kt`
- `app/src/main/java/com/example/data/repository/SaleReturnRepository.kt`
- `app/src/main/java/com/example/data/repository/StoreRepository.kt`
- `app/src/main/java/com/example/ui/components/SupplierPurchasesSection.kt`
- `app/src/main/java/com/example/ui/screens/CustomerManagementScreen.kt`
- `app/src/main/java/com/example/ui/screens/PurchasesScreen.kt`
- `app/src/main/java/com/example/ui/screens/SupplierManagementScreen.kt`
- `app/src/main/java/com/example/viewmodel/CustomerViewModel.kt`
- `app/src/main/java/com/example/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/example/viewmodel/SupplierPurchaseViewModel.kt`

بالإضافة إلى إصلاحات الجولة السابقة في المستودعات والتقارير والتصدير والنسخ الاحتياطي.

## التحقق المنفذ
- نجح تجميع `AccountingDecimal.kt` منفردًا باستخدام Kotlin compiler.
- نجحت اختبارات smoke محلية لـ `10 + 0.2 + 0.03 = 10.23`، وقراءة `150,25`، والتقريب إلى منزلتين.
- تعذر تشغيل `./gradlew testDebugUnitTest --offline` لأن Gradle Wrapper حاول تنزيل Gradle 9.3.1 من `services.gradle.org`، ولم يتوفر اتصال DNS (`UnknownHostException`).
- لم يتم إثبات نجاح تجميع Android أو تشغيل جميع اختبارات Room/Compose أو إنشاء APK في هذه البيئة.

## ما يجب اختباره قبل اعتماد APK
1. تشغيل `testDebugUnitTest` و`assembleDebug` من GitHub Actions على الفرع المقصود.
2. اختبار ترحيل قاعدة البيانات من الإصدار 20 إلى 21 على نسخة قاعدة بيانات اختبارية، والتحقق من أن المرتجعات القديمة بقيت تخفض مستحقات المورد بكامل قيمتها.
3. اختبار مرتجع مشتريات بمبلغ 50 ₪ مع مستحقات مورد 40 ₪: ينخفض المستحق 40 ₪ ويُسجل 10 ₪ فقط كدخل نقدي في الحساب المحدد.
4. اختبار مرتجع نقدي دون حساب نشط، ومرتجع يعادل المستحق بالكامل، ومرتجع أكبر من المستحق.
5. اختبار الرصيد الافتتاحي للعميل والمورد، والتحقق من الاتجاه والألوان ومنع التكرار.
6. اختبار توافق النسخ الاحتياطية القديمة والجديدة مع حقول مرتجع المورد الجديدة.
7. مراجعة دقة الأوزان والكميات الكسرية ضمن ترحيل منفصل؛ لم تُحوّل أنواع الكميات الصحيحة في هذا التحديث.

## الحكم النهائي

هذا أرشيف محدث يحتوي تغييرات المصدر والترحيل والاختبارات المذكورة، لكنه **غير معتمد بعد كنسخة نهائية محاسبيًا** حتى ينجح بناء Android والاختبارات على بيئة Gradle متصلة، ويُختبر ترحيل قاعدة البيانات والنسخ الاحتياطي. دقة مبالغ الأموال عولجت في مسارات محددة؛ دعم الأوزان/الكميات الكسرية ما زال غير مكتمل كما هو موضح أعلاه.
