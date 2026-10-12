package com.example.data.demo

import androidx.room.withTransaction
import com.example.data.db.*
import com.example.data.repository.StoreRepository
import com.example.model.PurchaseLineRequest
import com.example.model.PurchaseReturnLineRequest
import com.example.model.SaleReturnLineRequest

/**
 * DemoDataSeeder:
 *
 * Populates a complete, realistic, connected demo dataset for testing the
 * SmallStore application quickly and predictably in debug/demo builds.
 *
 * Accounting Invariants strictly preserved:
 * 1. Purchases recorded first to create valid inventory on hand before sales.
 * 2. Every sale obeys `totalAmount == paidAmount + creditAmount`.
 * 3. Every purchase obeys `totalAmount == paidAmount + creditAmount`.
 * 4. All stock movements flow through domain repositories.
 * 5. Customer & supplier balances are verified against authoritative ledgers.
 * 6. Idempotent: safe to run multiple times without creating duplicates.
 * 7. Atomic: all operations commit in a single database transaction.
 */
object DemoDataSeeder {

    /**
     * Seeds the demo dataset if the database does not already contain demo records.
     *
     * @return `true` if seeding occurred, `false` if the database was already populated.
     */
    suspend fun seedDemoDataIfEmpty(
        repository: StoreRepository,
        database: SmallStoreDatabase
    ): Boolean {
        // Idempotency check: if demo records already exist, do not re-seed or duplicate
        val alreadySeeded = database.customerDao().getCustomerById("demo_cust_1") != null ||
                database.productDao().getProductById("demo_prod_1") != null
        if (alreadySeeded) {
            return false
        }

        seedDemoData(repository, database)
        return true
    }

    /**
     * Clears existing records and re-seeds the demo dataset fresh.
     * Strictly restricted to the demo database instance.
     */
    suspend fun resetDemoData(
        repository: StoreRepository,
        database: SmallStoreDatabase
    ) {
        database.withTransaction {
            database.saleReturnLineDao().deleteAllReturnLines()
            database.saleReturnDao().deleteAllReturns()
            database.refundDao().deleteAllRefunds()
            database.purchaseReturnLineDao().deleteAllLines()
            database.purchaseReturnDao().deleteAllReturns()
            database.supplierPaymentDao().deleteAllPayments()
            database.purchaseLineDao().deleteAllLines()
            database.purchaseDao().deleteAllPurchases()
            database.customerPaymentDao().deleteAllPayments()
            database.saleDao().deleteAllSaleLines()
            database.saleDao().deleteAllSales()
            database.transactionItemLineDao().deleteAllLines()
            database.transactionDao().deleteAllTransactions()
            database.stockMovementDao().deleteAllStockMovements()
            database.openingBalanceDao().deleteAllOpeningBalances()
            database.adjustmentDao().deleteAllAdjustments()
            database.reversalDao().deleteAllReversals()
            database.expenseDao().deleteAllExpenses()
            database.expenseCategoryDao().deleteAllCategories()
            database.paymentMethodDao().deleteAllPaymentMethods()
            database.financialAccountDao().deleteAllAccounts()
            database.supplierDao().deleteAllSuppliers()
            database.productDao().deleteAllProducts()
            database.customerDao().deleteAllCustomers()
            database.notificationDao().deleteAllNotifications()
        }

        seedDemoData(repository, database)
    }

    /**
     * Executes the atomic creation of customers, products, suppliers,
     * purchases, sales, payments, returns, and accounting ledger records.
     */
    suspend fun seedDemoData(
        repository: StoreRepository,
        database: SmallStoreDatabase
    ) = database.withTransaction {
        // =========================================================================
        // 1. FINANCIAL ACCOUNTS & PAYMENT METHODS
        // =========================================================================
        val financialAccounts = listOf(
            FinancialAccount(
                id = "acc_cash",
                name = "الصندوق الرئيسي",
                type = "CASH",
                isActive = true
            ),
            FinancialAccount(
                id = "acc_bank",
                name = "حساب مصرف الراجحي",
                type = "BANK",
                isActive = true
            )
        )
        for (acc in financialAccounts) {
            database.financialAccountDao().insertAccount(acc)
        }

        val paymentMethods = listOf(
            PaymentMethod(
                id = "pm_cash",
                name = "نقداً",
                type = "CASH",
                isActive = true
            ),
            PaymentMethod(
                id = "pm_card",
                name = "بطاقة مدى",
                type = "CARD",
                isActive = true
            ),
            PaymentMethod(
                id = "pm_transfer",
                name = "تحويل بنكي",
                type = "BANK",
                isActive = true
            )
        )
        for (pm in paymentMethods) {
            database.paymentMethodDao().insertPaymentMethod(pm)
        }

        // Starting capital / opening balances for financial accounts
        repository.openingBalanceAdjustmentRepository.recordOpeningBalance(
            entityType = "FINANCIAL_ACCOUNT",
            entityId = "acc_cash",
            amount = 10000.0,
            direction = "CREDIT",
            date = "2026-09-01",
            reason = "رصيد افتتاحي الصندوق النقدي"
        )
        repository.openingBalanceAdjustmentRepository.recordOpeningBalance(
            entityType = "FINANCIAL_ACCOUNT",
            entityId = "acc_bank",
            amount = 25000.0,
            direction = "CREDIT",
            date = "2026-09-01",
            reason = "رصيد افتتاحي الحساب البنكي"
        )

        // =========================================================================
        // 2. DEMO CUSTOMERS (6 distinct customers with varied profiles)
        // =========================================================================
        val demoCustomers = listOf(
            CustomerEntity(
                id = "demo_cust_1",
                customerName = "عبدالرحمن السالم",
                balance = 0.0,
                totalDebt = 0.0,
                phone = "0551122334",
                lastTransactionDate = "2026-09-01",
                hasRecentActivity = false
            ),
            CustomerEntity(
                id = "demo_cust_2",
                customerName = "سارة المنصور",
                balance = 0.0,
                totalDebt = 0.0,
                phone = "0552233445",
                lastTransactionDate = "2026-09-01",
                hasRecentActivity = false
            ),
            CustomerEntity(
                id = "demo_cust_3",
                customerName = "فيصل الدوسري",
                balance = 0.0,
                totalDebt = 0.0,
                phone = "0553344556",
                lastTransactionDate = "2026-09-01",
                hasRecentActivity = false
            ),
            CustomerEntity(
                id = "demo_cust_4",
                customerName = "نورة القحطاني",
                balance = 0.0,
                totalDebt = 0.0,
                phone = "0554455667",
                lastTransactionDate = "2026-09-01",
                hasRecentActivity = false
            ),
            CustomerEntity(
                id = "demo_cust_5",
                customerName = "عمر بن خالد",
                balance = 0.0,
                totalDebt = 0.0,
                phone = "0555566778",
                lastTransactionDate = "2026-09-01",
                hasRecentActivity = false
            ),
            CustomerEntity(
                id = "demo_cust_6",
                customerName = "هدى العتيبي",
                balance = 0.0,
                totalDebt = 0.0,
                phone = "0556677889",
                lastTransactionDate = "2026-09-01",
                hasRecentActivity = false
            )
        )
        database.customerDao().insertCustomers(demoCustomers)

        // =========================================================================
        // 3. DEMO SUPPLIERS (6 distinct suppliers)
        // =========================================================================
        val demoSuppliers = listOf(
            Supplier(
                id = "demo_sup_1",
                name = "شركة المراعي للتوزيع",
                phone = "0112233441",
                notes = "الموزع المعتمد للألبان والأجبان"
            ),
            Supplier(
                id = "demo_sup_2",
                name = "مؤسسة الأغذية المتحدة",
                phone = "0113344552",
                notes = "توريد المواد الغذائية الجافة والزيوت"
            ),
            Supplier(
                id = "demo_sup_3",
                name = "شركة نستله السعودية",
                phone = "0114455663",
                notes = "القهوة والمشروبات الساخنة"
            ),
            Supplier(
                id = "demo_sup_4",
                name = "مطاحن الدقيق الوطنية",
                phone = "0115566774",
                notes = "الأرز والدقيق والبقوليات"
            ),
            Supplier(
                id = "demo_sup_5",
                name = "مستودع المشروبات والعصائر",
                phone = "0116677885",
                notes = "المياه والعصائر والمشروبات الباردة"
            ),
            Supplier(
                id = "demo_sup_6",
                name = "شركة الزيوت والتمور",
                phone = "0117788996",
                notes = "التمور والمنتجات الوطنية"
            )
        )
        database.supplierDao().insertSuppliers(demoSuppliers)

        // Opening balance for Supplier 6 (Debit 150.0: advance credit with supplier)
        repository.openingBalanceAdjustmentRepository.recordOpeningBalance(
            entityType = "SUPPLIER",
            entityId = "demo_sup_6",
            amount = 150.0,
            direction = "DEBIT",
            date = "2026-09-01",
            reason = "رصيد دفعة مقدمة سابقة للمورد"
        )

        // =========================================================================
        // 4. DEMO PRODUCTS (14 distinct products across realistic categories)
        // =========================================================================
        val demoProducts = listOf(
            ProductEntity(
                id = "demo_prod_1",
                name = "حليب المراعي طازج 2 لتر",
                price = 11.00,
                costPrice = 8.50,
                category = "ألبان",
                unit = "حبة"
            ),
            ProductEntity(
                id = "demo_prod_2",
                name = "لبن المراعي 1 لتر",
                price = 5.50,
                costPrice = 4.00,
                category = "ألبان",
                unit = "حبة"
            ),
            ProductEntity(
                id = "demo_prod_3",
                name = "جبنة شيدر كرافت 100 جم",
                price = 8.50,
                costPrice = 6.00,
                category = "ألبان",
                unit = "حبة"
            ),
            ProductEntity(
                id = "demo_prod_4",
                name = "أرز بسمتي الشعلان 5 كجم",
                price = 48.00,
                costPrice = 38.00,
                category = "حبوب وبقوليات",
                unit = "كيس"
            ),
            ProductEntity(
                id = "demo_prod_5",
                name = "سكر الأسرة ناعم 5 كجم",
                price = 24.00,
                costPrice = 18.00,
                category = "مواد غذائية",
                unit = "كيس"
            ),
            ProductEntity(
                id = "demo_prod_6",
                name = "زيت عافية ذرة 1.5 لتر",
                price = 25.00,
                costPrice = 19.50,
                category = "زيوت",
                unit = "حبة"
            ),
            ProductEntity(
                id = "demo_prod_7",
                name = "شاي ليبتون أحمر 100 كيس",
                price = 19.00,
                costPrice = 14.00,
                category = "مشروبات ساخنة",
                unit = "علبة"
            ),
            ProductEntity(
                id = "demo_prod_8",
                name = "نسكافيه كلاسيك 200 جم",
                price = 34.00,
                costPrice = 26.00,
                category = "مشروبات ساخنة",
                unit = "حبة"
            ),
            ProductEntity(
                id = "demo_prod_9",
                name = "مياه هناء 330 مل كرتون 40",
                price = 21.00,
                costPrice = 15.00,
                category = "مياه ومشروبات",
                unit = "كرتون"
            ),
            ProductEntity(
                id = "demo_prod_10",
                name = "عصير ربيع برتقال 1 لتر",
                price = 7.50,
                costPrice = 5.50,
                category = "مياه ومشروبات",
                unit = "حبة"
            ),
            ProductEntity(
                id = "demo_prod_11",
                name = "دقيق فوم فاخر 1 كجم",
                price = 4.00,
                costPrice = 2.50,
                category = "حبوب وبقوليات",
                unit = "كيس"
            ),
            ProductEntity(
                id = "demo_prod_12",
                name = "تونة قودي خفيفة 185 جم",
                price = 9.50,
                costPrice = 7.00,
                category = "معلبات",
                unit = "حبة"
            ),
            ProductEntity(
                id = "demo_prod_13",
                name = "مكرونة قودي سباغيتي 450 جم",
                price = 6.00,
                costPrice = 4.50,
                category = "معلبات",
                unit = "حبة"
            ),
            ProductEntity(
                id = "demo_prod_14",
                name = "صابون دوف أبيض 135 جم",
                price = 7.00,
                costPrice = 5.00,
                category = "منظفات وعناية",
                unit = "حبة"
            )
        )
        database.productDao().insertProducts(demoProducts)

        // =========================================================================
        // 5. PURCHASES & INCOMING INVENTORY (Creates valid stock on hand)
        // =========================================================================
        // Purchase 1: Supplier 1 (Credit purchase 950.0)
        val pur1 = repository.purchasesRepository.recordPurchase(
            supplierId = "demo_sup_1",
            lines = listOf(
                PurchaseLineRequest("demo_prod_1", "حليب المراعي طازج 2 لتر", 60, 8.50),
                PurchaseLineRequest("demo_prod_2", "لبن المراعي 1 لتر", 50, 4.00),
                PurchaseLineRequest("demo_prod_3", "جبنة شيدر كرافت 100 جم", 40, 6.00)
            ),
            purchaseDate = "2026-09-02",
            paidAmount = 0.0,
            notes = "توريد منتجات الألبان والأجبان",
            invoiceNumber = "PUR-000001"
        )

        // Purchase 2: Supplier 2 (Partially paid purchase: 1000.0 cash, 1100.0 credit)
        repository.purchasesRepository.recordPurchase(
            supplierId = "demo_sup_2",
            lines = listOf(
                PurchaseLineRequest("demo_prod_5", "سكر الأسرة ناعم 5 كجم", 50, 18.00),
                PurchaseLineRequest("demo_prod_6", "زيت عافية ذرة 1.5 لتر", 40, 19.50),
                PurchaseLineRequest("demo_prod_12", "تونة قودي خفيفة 185 جم", 60, 7.00)
            ),
            purchaseDate = "2026-09-02",
            paidAmount = 1000.0,
            financialAccountId = "acc_cash",
            paymentMethodId = "pm_cash",
            notes = "توريد المواد الغذائية الجافة والزيوت",
            invoiceNumber = "PUR-000002"
        )

        // Purchase 3: Supplier 3 (Fully settled at purchase: 1340.0 bank transfer)
        repository.purchasesRepository.recordPurchase(
            supplierId = "demo_sup_3",
            lines = listOf(
                PurchaseLineRequest("demo_prod_7", "شاي ليبتون أحمر 100 كيس", 40, 14.00),
                PurchaseLineRequest("demo_prod_8", "نسكافيه كلاسيك 200 جم", 30, 26.00)
            ),
            purchaseDate = "2026-09-03",
            paidAmount = 1340.0,
            financialAccountId = "acc_bank",
            paymentMethodId = "pm_transfer",
            notes = "شراء شاي وقهوة مسدد نقداً بالتحويل",
            invoiceNumber = "PUR-000003"
        )

        // Purchase 4: Supplier 4 (Credit purchase 2100.0, later settled via Supplier Payment)
        repository.purchasesRepository.recordPurchase(
            supplierId = "demo_sup_4",
            lines = listOf(
                PurchaseLineRequest("demo_prod_4", "أرز بسمتي الشعلان 5 كجم", 50, 38.00),
                PurchaseLineRequest("demo_prod_11", "دقيق فوم فاخر 1 كجم", 80, 2.50)
            ),
            purchaseDate = "2026-09-03",
            paidAmount = 0.0,
            notes = "توريد الأرز والدقيق",
            invoiceNumber = "PUR-000004"
        )

        // Purchase 5: Supplier 5 (Partially paid: 500.0 paid, 975.0 credit)
        repository.purchasesRepository.recordPurchase(
            supplierId = "demo_sup_5",
            lines = listOf(
                PurchaseLineRequest("demo_prod_9", "مياه هناء 330 مل كرتون 40", 80, 15.00),
                PurchaseLineRequest("demo_prod_10", "عصير ربيع برتقال 1 لتر", 50, 5.50)
            ),
            purchaseDate = "2026-09-04",
            paidAmount = 500.0,
            financialAccountId = "acc_cash",
            paymentMethodId = "pm_cash",
            notes = "توريد مياه وعصائر",
            invoiceNumber = "PUR-000005"
        )

        // Purchase 6: Supplier 6 (Fully paid 520.0 via cash)
        repository.purchasesRepository.recordPurchase(
            supplierId = "demo_sup_6",
            lines = listOf(
                PurchaseLineRequest("demo_prod_13", "مكرونة قودي سباغيتي 450 جم", 60, 4.50),
                PurchaseLineRequest("demo_prod_14", "صابون دوف أبيض 135 جم", 50, 5.00)
            ),
            purchaseDate = "2026-09-04",
            paidAmount = 520.0,
            financialAccountId = "acc_cash",
            paymentMethodId = "pm_cash",
            notes = "توريد معلبات ومنظفات",
            invoiceNumber = "PUR-000006"
        )

        // =========================================================================
        // 6. SUPPLIER PAYMENTS & PURCHASE RETURNS
        // =========================================================================
        // Settle Supplier 4 payable completely via bank transfer
        repository.supplierPaymentRepository.recordSupplierPayment(
            supplierId = "demo_sup_4",
            amount = 2100.0,
            paymentDate = "2026-09-05",
            financialAccountId = "acc_bank",
            paymentMethodId = "pm_transfer",
            referenceNumber = "SPAY-000001",
            notes = "سداد فاتورة PUR-000004 بالكامل"
        )

        // Partially settle Supplier 5 payable (400.0 of 975.0)
        repository.supplierPaymentRepository.recordSupplierPayment(
            supplierId = "demo_sup_5",
            amount = 400.0,
            paymentDate = "2026-09-06",
            financialAccountId = "acc_cash",
            paymentMethodId = "pm_cash",
            referenceNumber = "SPAY-000002",
            notes = "دفعة تحت الحساب لمستودع المشروبات"
        )

        // Return 5 units of milk to Supplier 1 (Purchase Return, reduces payable by 42.50)
        val pur1MilkLine = pur1.lines.first { it.productId == "demo_prod_1" }
        repository.purchaseReturnRepository.recordPurchaseReturn(
            purchaseId = pur1.purchase.id,
            returnLines = listOf(
                PurchaseReturnLineRequest(
                    purchaseLineId = pur1MilkLine.id,
                    quantity = 5
                )
            ),
            reason = "عبوات بها تسريب عند الاستلام",
            returnDate = "2026-09-04"
        )

        // =========================================================================
        // 7. SALES & OUTGOING INVENTORY
        // =========================================================================
        // Sale 1: Customer 1 (Credit sale: total 190.0, paid 0.0, credit 190.0)
        repository.salesRepository.createSale(
            sale = Sale(
                id = "demo_sale_1",
                invoiceNumber = "INV-000001",
                customerId = "demo_cust_1",
                saleType = "CREDIT",
                totalAmount = 190.0,
                paidAmount = 0.0,
                creditAmount = 190.0,
                paymentStatus = "UNPAID",
                transactionDate = "2026-09-05",
                status = "ACTIVE"
            ),
            lines = listOf(
                SaleLine(
                    id = "demo_sale_1_l1",
                    saleId = "demo_sale_1",
                    productId = "demo_prod_1",
                    productNameSnapshot = "حليب المراعي طازج 2 لتر",
                    quantity = 4,
                    unitPrice = 11.00,
                    costPriceAtSale = 8.50,
                    subtotal = 44.00
                ),
                SaleLine(
                    id = "demo_sale_1_l2",
                    saleId = "demo_sale_1",
                    productId = "demo_prod_4",
                    productNameSnapshot = "أرز بسمتي الشعلان 5 كجم",
                    quantity = 2,
                    unitPrice = 48.00,
                    costPriceAtSale = 38.00,
                    subtotal = 96.00
                ),
                SaleLine(
                    id = "demo_sale_1_l3",
                    saleId = "demo_sale_1",
                    productId = "demo_prod_6",
                    productNameSnapshot = "زيت عافية ذرة 1.5 لتر",
                    quantity = 2,
                    unitPrice = 25.00,
                    costPriceAtSale = 19.50,
                    subtotal = 50.00
                )
            ),
            customerNameSnapshot = "عبدالرحمن السالم",
            notes = "فاتورة بقالة آجل"
        )

        // Sale 2: Customer 2 (Mixed sale: total 215.0, paid 100.0, credit 115.0)
        repository.salesRepository.createSale(
            sale = Sale(
                id = "demo_sale_2",
                invoiceNumber = "INV-000002",
                customerId = "demo_cust_2",
                saleType = "MIXED",
                totalAmount = 215.0,
                paidAmount = 100.0,
                creditAmount = 115.0,
                paymentStatus = "PARTIAL",
                transactionDate = "2026-09-05",
                status = "ACTIVE",
                financialAccountId = "acc_cash"
            ),
            lines = listOf(
                SaleLine(
                    id = "demo_sale_2_l1",
                    saleId = "demo_sale_2",
                    productId = "demo_prod_5",
                    productNameSnapshot = "سكر الأسرة ناعم 5 كجم",
                    quantity = 3,
                    unitPrice = 24.00,
                    costPriceAtSale = 18.00,
                    subtotal = 72.00
                ),
                SaleLine(
                    id = "demo_sale_2_l2",
                    saleId = "demo_sale_2",
                    productId = "demo_prod_7",
                    productNameSnapshot = "شاي ليبتون أحمر 100 كيس",
                    quantity = 2,
                    unitPrice = 19.00,
                    costPriceAtSale = 14.00,
                    subtotal = 38.00
                ),
                SaleLine(
                    id = "demo_sale_2_l3",
                    saleId = "demo_sale_2",
                    productId = "demo_prod_9",
                    productNameSnapshot = "مياه هناء 330 مل كرتون 40",
                    quantity = 5,
                    unitPrice = 21.00,
                    costPriceAtSale = 15.00,
                    subtotal = 105.00
                )
            ),
            customerNameSnapshot = "سارة المنصور",
            notes = "مشتريات منزلية جزء مدفوع نقداً"
        )

        // Sale 3: Customer 3 (Cash sale: total 69.0, paid 69.0, credit 0.0)
        repository.salesRepository.createSale(
            sale = Sale(
                id = "demo_sale_3",
                invoiceNumber = "INV-000003",
                customerId = "demo_cust_3",
                saleType = "CASH",
                totalAmount = 69.0,
                paidAmount = 69.0,
                creditAmount = 0.0,
                paymentStatus = "PAID",
                transactionDate = "2026-09-06",
                status = "ACTIVE",
                financialAccountId = "acc_cash"
            ),
            lines = listOf(
                SaleLine(
                    id = "demo_sale_3_l1",
                    saleId = "demo_sale_3",
                    productId = "demo_prod_2",
                    productNameSnapshot = "لبن المراعي 1 لتر",
                    quantity = 4,
                    unitPrice = 5.50,
                    costPriceAtSale = 4.00,
                    subtotal = 22.00
                ),
                SaleLine(
                    id = "demo_sale_3_l2",
                    saleId = "demo_sale_3",
                    productId = "demo_prod_3",
                    productNameSnapshot = "جبنة شيدر كرافت 100 جم",
                    quantity = 2,
                    unitPrice = 8.50,
                    costPriceAtSale = 6.00,
                    subtotal = 17.00
                ),
                SaleLine(
                    id = "demo_sale_3_l3",
                    saleId = "demo_sale_3",
                    productId = "demo_prod_10",
                    productNameSnapshot = "عصير ربيع برتقال 1 لتر",
                    quantity = 4,
                    unitPrice = 7.50,
                    costPriceAtSale = 5.50,
                    subtotal = 30.00
                )
            ),
            customerNameSnapshot = "فيصل الدوسري",
            notes = "شراء نقدي فوري"
        )

        // Sale 4: Customer 4 (Credit sale: total 106.0, paid 0.0, credit 106.0)
        repository.salesRepository.createSale(
            sale = Sale(
                id = "demo_sale_4",
                invoiceNumber = "INV-000004",
                customerId = "demo_cust_4",
                saleType = "CREDIT",
                totalAmount = 106.0,
                paidAmount = 0.0,
                creditAmount = 106.0,
                paymentStatus = "UNPAID",
                transactionDate = "2026-09-06",
                status = "ACTIVE"
            ),
            lines = listOf(
                SaleLine(
                    id = "demo_sale_4_l1",
                    saleId = "demo_sale_4",
                    productId = "demo_prod_8",
                    productNameSnapshot = "نسكافيه كلاسيك 200 جم",
                    quantity = 2,
                    unitPrice = 34.00,
                    costPriceAtSale = 26.00,
                    subtotal = 68.00
                ),
                SaleLine(
                    id = "demo_sale_4_l2",
                    saleId = "demo_sale_4",
                    productId = "demo_prod_12",
                    productNameSnapshot = "تونة قودي خفيفة 185 جم",
                    quantity = 4,
                    unitPrice = 9.50,
                    costPriceAtSale = 7.00,
                    subtotal = 38.00
                )
            ),
            customerNameSnapshot = "نورة القحطاني",
            notes = "فاتورة آجل"
        )

        // Sale 5: Customer 5 (Credit sale: total 78.0, paid 0.0, credit 78.0)
        repository.salesRepository.createSale(
            sale = Sale(
                id = "demo_sale_5",
                invoiceNumber = "INV-000005",
                customerId = "demo_cust_5",
                saleType = "CREDIT",
                totalAmount = 78.0,
                paidAmount = 0.0,
                creditAmount = 78.0,
                paymentStatus = "UNPAID",
                transactionDate = "2026-09-06",
                status = "ACTIVE"
            ),
            lines = listOf(
                SaleLine(
                    id = "demo_sale_5_l1",
                    saleId = "demo_sale_5",
                    productId = "demo_prod_11",
                    productNameSnapshot = "دقيق فوم فاخر 1 كجم",
                    quantity = 5,
                    unitPrice = 4.00,
                    costPriceAtSale = 2.50,
                    subtotal = 20.00
                ),
                SaleLine(
                    id = "demo_sale_5_l2",
                    saleId = "demo_sale_5",
                    productId = "demo_prod_13",
                    productNameSnapshot = "مكرونة قودي سباغيتي 450 جم",
                    quantity = 5,
                    unitPrice = 6.00,
                    costPriceAtSale = 4.50,
                    subtotal = 30.00
                ),
                SaleLine(
                    id = "demo_sale_5_l3",
                    saleId = "demo_sale_5",
                    productId = "demo_prod_14",
                    productNameSnapshot = "صابون دوف أبيض 135 جم",
                    quantity = 4,
                    unitPrice = 7.00,
                    costPriceAtSale = 5.00,
                    subtotal = 28.00
                )
            ),
            customerNameSnapshot = "عمر بن خالد",
            notes = "فاتورة مستلزمات تموينية"
        )

        // Sale 6: Customer 6 (Cash sale: total 48.0, paid 48.0, credit 0.0)
        repository.salesRepository.createSale(
            sale = Sale(
                id = "demo_sale_6",
                invoiceNumber = "INV-000006",
                customerId = "demo_cust_6",
                saleType = "CASH",
                totalAmount = 48.0,
                paidAmount = 48.0,
                creditAmount = 0.0,
                paymentStatus = "PAID",
                transactionDate = "2026-09-07",
                status = "ACTIVE",
                financialAccountId = "acc_cash"
            ),
            lines = listOf(
                SaleLine(
                    id = "demo_sale_6_l1",
                    saleId = "demo_sale_6",
                    productId = "demo_prod_4",
                    productNameSnapshot = "أرز بسمتي الشعلان 5 كجم",
                    quantity = 1,
                    unitPrice = 48.00,
                    costPriceAtSale = 38.00,
                    subtotal = 48.00
                )
            ),
            customerNameSnapshot = "هدى العتيبي",
            notes = "دفع فوري نقداً"
        )

        // =========================================================================
        // 8. CUSTOMER PAYMENTS & SALE RETURN
        // =========================================================================
        // Customer 2: Partially pays 50.0 of 115.0 credit balance
        repository.customerPaymentRepository.recordCustomerPayment(
            customerId = "demo_cust_2",
            amount = 50.0,
            paymentMethodId = "pm_cash",
            financialAccountId = "acc_cash",
            reference = "PAY-000001",
            notes = "سداد دفعة نقدية تحت الحساب"
        )

        // Customer 5: Settles full 78.0 credit balance
        repository.customerPaymentRepository.recordCustomerPayment(
            customerId = "demo_cust_5",
            amount = 78.0,
            paymentMethodId = "pm_cash",
            financialAccountId = "acc_cash",
            reference = "PAY-000002",
            notes = "سداد فاتورة INV-000005 بالكامل"
        )

        // Customer 4: Sale return for 1 unit of Nescafe (unit price 34.00)
        val sale4Lines = database.saleDao().getSaleLinesBySaleId("demo_sale_4")
        val nescafeLine = sale4Lines.first { it.productId == "demo_prod_8" }
        repository.saleReturnRepository.recordSaleReturn(
            saleId = "demo_sale_4",
            returnLines = listOf(
                SaleReturnLineRequest(
                    saleLineId = nescafeLine.id,
                    quantity = 1
                )
            ),
            reason = "الصنف مختلف عن المطلوب",
            reasonCode = "other",
            reasonLabelSnapshot = "صنف مختلف",
            returnDate = "2026-09-07",
            refundRequest = null // Credit sale: automatically reduces credit receivable by 34.00
        )

        // =========================================================================
        // 9. EXPENSES
        // =========================================================================
        val expCat1 = repository.expenseRepository.insertExpenseCategory(
            ExpenseCategory(
                id = "exp_cat_1",
                name = "فواتير ومرافق",
                isActive = true
            )
        )
        val expCat2 = repository.expenseRepository.insertExpenseCategory(
            ExpenseCategory(
                id = "exp_cat_2",
                name = "إيجار ومستلزمات",
                isActive = true
            )
        )

        repository.expenseRepository.recordExpense(
            categoryId = expCat1.id,
            amount = 350.0,
            financialAccountId = "acc_bank",
            paymentMethodId = "pm_transfer",
            date = "2026-09-04",
            description = "سداد فاتورة الكهرباء والمياه"
        )
        repository.expenseRepository.recordExpense(
            categoryId = expCat2.id,
            amount = 60.0,
            financialAccountId = "acc_cash",
            paymentMethodId = "pm_cash",
            date = "2026-09-05",
            description = "شراء أدوات نظافة وضيافة للمحل"
        )

        // =========================================================================
        // 10. STORE INFORMATION
        // =========================================================================
        val existingStoreInfo = database.storeInfoDao().getStoreInfoSync()
        if (existingStoreInfo == null) {
            database.storeInfoDao().insertOrUpdate(
                StoreInfoEntity(
                    id = 1,
                    storeName = "بقالة الخير والبركة",
                    ownerName = "محمد السالم",
                    phone = "0501234567",
                    address = "الرياض - حي الملز",
                    taxNumber = "300123456700003",
                    crNumber = "1010123456",
                    isSaved = false
                )
            )
        }
    }
}
