package com.example.model

import com.example.accounting.InventoryMovementType
import com.example.data.db.CustomerEntity
import com.example.data.db.CustomerPayment
import com.example.data.db.Expense
import com.example.data.db.ExpenseCategory
import com.example.data.db.FinancialAccount
import com.example.data.db.NotificationEntity
import com.example.data.db.OpeningBalance
import com.example.data.db.PaymentMethod
import com.example.data.db.ProductEntity
import com.example.data.db.Purchase
import com.example.data.db.PurchaseLine
import com.example.data.db.PurchaseReturn
import com.example.data.db.PurchaseReturnLine
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.SaleReturn
import com.example.data.db.SaleReturnLine
import com.example.data.db.StockMovementEntity
import com.example.data.db.StoreInfoEntity
import com.example.data.db.Supplier
import com.example.data.db.SupplierPayment
import com.example.data.db.TransactionEntity
import com.example.data.db.TransactionItemLineEntity

/**
 * Authoritative development & demo dataset for SmallStore.
 * Provides a connected, realistic, and internally consistent dataset across:
 * - Customers & Customer Accounts
 * - Products & Categories
 * - Suppliers & Accounts Payable
 * - Purchases & Purchase Lines
 * - Supplier Payments & Purchase Returns
 * - Sales & Sale Lines (Cash, Credit, Mixed)
 * - Customer Payments & Sale Returns
 * - Expenses & Expense Categories
 * - Opening Balances
 * - Perpetual Stock Movements
 * - Unified Activities/Transactions & Notifications
 */
object SampleData {

    // -------------------------------------------------------------------------
    // 1. FINANCIAL ACCOUNTS & PAYMENT METHODS
    // -------------------------------------------------------------------------
    val sampleFinancialAccounts: List<FinancialAccount> = listOf(
        FinancialAccount(id = "acc_cash", name = "الصندوق الرئيسي", type = "CASH", isActive = true),
        FinancialAccount(id = "acc_bank", name = "الحساب البنكي", type = "BANK", isActive = true)
    )

    val samplePaymentMethods: List<PaymentMethod> = listOf(
        PaymentMethod(id = "pm_cash", name = "نقدي", type = "CASH", isActive = true),
        PaymentMethod(id = "pm_card", name = "شبكة / مدى", type = "CARD", isActive = true),
        PaymentMethod(id = "pm_transfer", name = "تحويل بنكي", type = "TRANSFER", isActive = true)
    )

    // -------------------------------------------------------------------------
    // 2. CUSTOMERS (6 customers)
    // -------------------------------------------------------------------------
    val sampleCustomers: List<CustomerAccount> = listOf(
        CustomerAccount("c1", "أحمد الشمري", 292.0, 292.0, "0501112233", "2026-09-05", true),
        CustomerAccount("c2", "خالد العتيبي", 0.0, 0.0, "0502223344", "2026-09-05", true),
        CustomerAccount("c3", "سعيد القحطاني", 82.0, 82.0, "0503334455", "2026-09-05", true),
        CustomerAccount("c4", "محمد الغامدي", 73.0, 73.0, "0504445566", "2026-09-05", true),
        CustomerAccount("c5", "فهد الدوسري", 0.0, 0.0, "0505556677", "2026-09-05", true),
        CustomerAccount("c6", "عبدالله الشهري", 63.0, 63.0, "0506667788", "2026-09-05", true)
    )

    // -------------------------------------------------------------------------
    // 3. PRODUCTS (12 products with units, realistic prices and costs)
    // -------------------------------------------------------------------------
    val sampleProducts: List<ProductItem> = listOf(
        ProductItem("p1", "أرز بسمتي 5 كجم", 45.0, "مواد غذائية", "كيس", costPrice = 35.0),
        ProductItem("p2", "زيت نباتي 1.5 لتر", 18.0, "زيوت", "حبة", costPrice = 14.0),
        ProductItem("p3", "سكر ناعم 2 كجم", 12.0, "مواد غذائية", "كيس", costPrice = 9.0),
        ProductItem("p4", "حليب مجفف 1.8 كجم", 65.0, "ألبان", "علبة", costPrice = 52.0),
        ProductItem("p5", "شاي أسود 100 كيس", 16.0, "مشروبات", "كرتون", costPrice = 12.0),
        ProductItem("p6", "معكرونة 400 جم", 4.5, "مواد غذائية", "حبة", costPrice = 3.0),
        ProductItem("p7", "صلصة طماطم 135 جم", 2.5, "معلبات", "حبة", costPrice = 1.8),
        ProductItem("p8", "ماء معبأ 330 مل كرتون", 15.0, "مشروبات", "كرتون", costPrice = 11.0),
        ProductItem("p9", "طحين أبيض فاخر 1 كجم", 5.0, "مواد غذائية", "كيس", costPrice = 3.5),
        ProductItem("p10", "تونة خفيفة بالزيت 185 جم", 7.5, "معلبات", "علبة", costPrice = 5.5),
        ProductItem("p11", "جبنة بيضاء فيتا 500 جم", 14.5, "ألبان", "حبة", costPrice = 11.0),
        ProductItem("p12", "مناديل ورقية 10 عبوات", 24.0, "منظفات ومستلزمات", "ربطة", costPrice = 18.0)
    )

    // -------------------------------------------------------------------------
    // 4. SUPPLIERS (5 active suppliers)
    // -------------------------------------------------------------------------
    val sampleSuppliers: List<Supplier> = listOf(
        Supplier("sup_1", "شركة الأغذية المتحدة", "0112345678", "orders@unitedfood.sa", "الرياض - الملز"),
        Supplier("sup_2", "مؤسسة التموين الذهبي", "0123456789", "supply@goldencatering.sa", "جدة - البلد"),
        Supplier("sup_3", "شركة مزارع الألبان الوطنية", "0134567890", "info@nationaldairy.sa", "الخرج - المنطقة الزراعية"),
        Supplier("sup_4", "مؤسسة وادي المشروبات", "0145678901", "sales@bevvalley.sa", "الدمام - المستودعات"),
        Supplier("sup_5", "شركة النظافة والتوزيع الشامل", "0156789012", "contact@cleandist.sa", "الرياض - السلي")
    )

    // -------------------------------------------------------------------------
    // 5. OPENING BALANCES
    // -------------------------------------------------------------------------
    val sampleOpeningBalances: List<OpeningBalance> = listOf(
        OpeningBalance(
            id = "ob_c1",
            entityType = "CUSTOMER",
            entityId = "c1",
            amount = 200.0,
            direction = "DEBIT",
            date = "2026-08-31",
            reason = "رصيد مدين مرحل من الدفتر السابق"
        ),
        OpeningBalance(
            id = "ob_sup1",
            entityType = "SUPPLIER",
            entityId = "sup_1",
            amount = 500.0,
            direction = "CREDIT",
            date = "2026-08-31",
            reason = "رصيد دائن افتتاحي سابق"
        )
    )

    // -------------------------------------------------------------------------
    // 6. PURCHASES (5 purchase transactions)
    // -------------------------------------------------------------------------
    val samplePurchases: List<Purchase> = listOf(
        Purchase(
            id = "pur_1",
            invoiceNumber = "PUR-000001",
            supplierId = "sup_1",
            purchaseDate = "2026-09-01",
            totalAmount = 2320.0,
            paidAmount = 1000.0,
            creditAmount = 1320.0,
            paymentStatus = "PARTIAL",
            paymentMethodId = "pm_cash",
            financialAccountId = "acc_cash",
            notes = "توريد حبوب ومواد تموينية أساسية"
        ),
        Purchase(
            id = "pur_2",
            invoiceNumber = "PUR-000002",
            supplierId = "sup_2",
            purchaseDate = "2026-09-02",
            totalAmount = 1220.0,
            paidAmount = 0.0,
            creditAmount = 1220.0,
            paymentStatus = "UNPAID",
            notes = "توريد زيوت ومعلبات بالآجل"
        ),
        Purchase(
            id = "pur_3",
            invoiceNumber = "PUR-000003",
            supplierId = "sup_3",
            purchaseDate = "2026-09-02",
            totalAmount = 2000.0,
            paidAmount = 2000.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            paymentMethodId = "pm_cash",
            financialAccountId = "acc_cash",
            notes = "توريد منتجات ألبان كاش"
        ),
        Purchase(
            id = "pur_4",
            invoiceNumber = "PUR-000004",
            supplierId = "sup_4",
            purchaseDate = "2026-09-03",
            totalAmount = 910.0,
            paidAmount = 410.0,
            creditAmount = 500.0,
            paymentStatus = "PARTIAL",
            paymentMethodId = "pm_cash",
            financialAccountId = "acc_cash",
            notes = "توريد مياه ومشروبات"
        ),
        Purchase(
            id = "pur_5",
            invoiceNumber = "PUR-000005",
            supplierId = "sup_5",
            purchaseDate = "2026-09-03",
            totalAmount = 720.0,
            paidAmount = 0.0,
            creditAmount = 720.0,
            paymentStatus = "UNPAID",
            notes = "توريد ورق ومنظفات استهلاكية"
        )
    )

    val samplePurchaseLines: List<PurchaseLine> = listOf(
        PurchaseLine("pur_line_1", "pur_1", "p1", "أرز بسمتي 5 كجم", 50, 35.0, 1750.0),
        PurchaseLine("pur_line_2", "pur_1", "p3", "سكر ناعم 2 كجم", 40, 9.0, 360.0),
        PurchaseLine("pur_line_3", "pur_1", "p9", "طحين أبيض فاخر 1 كجم", 60, 3.5, 210.0),

        PurchaseLine("pur_line_4", "pur_2", "p2", "زيت نباتي 1.5 لتر", 40, 14.0, 560.0),
        PurchaseLine("pur_line_5", "pur_2", "p6", "معكرونة 400 جم", 50, 3.0, 150.0),
        PurchaseLine("pur_line_6", "pur_2", "p7", "صلصة طماطم 135 جم", 100, 1.8, 180.0),
        PurchaseLine("pur_line_7", "pur_2", "p10", "تونة خفيفة بالزيت 185 جم", 60, 5.5, 330.0),

        PurchaseLine("pur_line_8", "pur_3", "p4", "حليب مجفف 1.8 كجم", 30, 52.0, 1560.0),
        PurchaseLine("pur_line_9", "pur_3", "p11", "جبنة بيضاء فيتا 500 جم", 40, 11.0, 440.0),

        PurchaseLine("pur_line_10", "pur_4", "p5", "شاي أسود 100 كيس", 30, 12.0, 360.0),
        PurchaseLine("pur_line_11", "pur_4", "p8", "ماء معبأ 330 مل كرتون", 50, 11.0, 550.0),

        PurchaseLine("pur_line_12", "pur_5", "p12", "مناديل ورقية 10 عبوات", 40, 18.0, 720.0)
    )

    // -------------------------------------------------------------------------
    // 7. SUPPLIER PAYMENTS & PURCHASE RETURNS
    // -------------------------------------------------------------------------
    val sampleSupplierPayments: List<SupplierPayment> = listOf(
        SupplierPayment(
            id = "spay_1",
            supplierId = "sup_1",
            amount = 500.0,
            paymentDate = "2026-09-04",
            paymentMethodId = "pm_cash",
            financialAccountId = "acc_cash",
            referenceNumber = "SPAY-000001",
            notes = "سداد دفعة تحت الحساب لشركة الأغذية المتحدة"
        )
    )

    val samplePurchaseReturns: List<PurchaseReturn> = listOf(
        PurchaseReturn(
            id = "pret_1",
            purchaseId = "pur_2",
            supplierId = "sup_2",
            returnDate = "2026-09-04",
            amount = 18.0,
            reason = "علب تالفة أثناء الشحن"
        )
    )

    val samplePurchaseReturnLines: List<PurchaseReturnLine> = listOf(
        PurchaseReturnLine(
            id = "pret_line_1",
            purchaseReturnId = "pret_1",
            purchaseLineId = "pur_line_6",
            productId = "p7",
            productNameSnapshot = "صلصة طماطم 135 جم",
            quantity = 10,
            unitCost = 1.8,
            subtotal = 18.0
        )
    )

    // -------------------------------------------------------------------------
    // 8. SALES (10 sales transactions: Cash, Credit, Mixed)
    // -------------------------------------------------------------------------
    val sampleSales: List<Sale> = listOf(
        Sale(
            id = "sale_1",
            invoiceNumber = "INV-000001",
            customerId = "c1",
            saleType = "CREDIT",
            totalAmount = 142.0,
            paidAmount = 0.0,
            creditAmount = 142.0,
            paymentStatus = "UNPAID",
            transactionDate = "2026-09-04"
        ),
        Sale(
            id = "sale_2",
            invoiceNumber = "INV-000002",
            customerId = "c2",
            saleType = "MIXED",
            totalAmount = 190.0,
            paidAmount = 90.0,
            creditAmount = 100.0,
            paymentStatus = "PARTIAL",
            transactionDate = "2026-09-04",
            financialAccountId = "acc_cash"
        ),
        Sale(
            id = "sale_3",
            invoiceNumber = "INV-000003",
            customerId = null,
            saleType = "CASH",
            totalAmount = 48.0,
            paidAmount = 48.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-09-04",
            financialAccountId = "acc_cash"
        ),
        Sale(
            id = "sale_4",
            invoiceNumber = "INV-000004",
            customerId = "c4",
            saleType = "CREDIT",
            totalAmount = 123.0,
            paidAmount = 0.0,
            creditAmount = 123.0,
            paymentStatus = "UNPAID",
            transactionDate = "2026-09-04"
        ),
        Sale(
            id = "sale_5",
            invoiceNumber = "INV-000005",
            customerId = null,
            saleType = "CASH",
            totalAmount = 30.0,
            paidAmount = 30.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-09-05",
            financialAccountId = "acc_cash"
        ),
        Sale(
            id = "sale_6",
            invoiceNumber = "INV-000006",
            customerId = "c3",
            saleType = "CREDIT",
            totalAmount = 82.0,
            paidAmount = 0.0,
            creditAmount = 82.0,
            paymentStatus = "UNPAID",
            transactionDate = "2026-09-05"
        ),
        Sale(
            id = "sale_7",
            invoiceNumber = "INV-000007",
            customerId = "c5",
            saleType = "CASH",
            totalAmount = 92.0,
            paidAmount = 92.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-09-05",
            financialAccountId = "acc_cash"
        ),
        Sale(
            id = "sale_8",
            invoiceNumber = "INV-000008",
            customerId = "c6",
            saleType = "CREDIT",
            totalAmount = 81.0,
            paidAmount = 0.0,
            creditAmount = 81.0,
            paymentStatus = "UNPAID",
            transactionDate = "2026-09-05"
        ),
        Sale(
            id = "sale_9",
            invoiceNumber = "INV-000009",
            customerId = "c1",
            saleType = "MIXED",
            totalAmount = 80.0,
            paidAmount = 30.0,
            creditAmount = 50.0,
            paymentStatus = "PARTIAL",
            transactionDate = "2026-09-05",
            financialAccountId = "acc_cash"
        ),
        Sale(
            id = "sale_10",
            invoiceNumber = "INV-000010",
            customerId = null,
            saleType = "CASH",
            totalAmount = 69.0,
            paidAmount = 69.0,
            creditAmount = 0.0,
            paymentStatus = "PAID",
            transactionDate = "2026-09-05",
            financialAccountId = "acc_cash"
        )
    )

    val sampleSaleLines: List<SaleLine> = listOf(
        SaleLine("sale_line_1", "sale_1", "p1", "أرز بسمتي 5 كجم", 2, 45.0, 35.0, 90.0),
        SaleLine("sale_line_2", "sale_1", "p2", "زيت نباتي 1.5 لتر", 2, 18.0, 14.0, 36.0),
        SaleLine("sale_line_3", "sale_1", "p5", "شاي أسود 100 كيس", 1, 16.0, 12.0, 16.0),

        SaleLine("sale_line_4", "sale_2", "p4", "حليب مجفف 1.8 كجم", 2, 65.0, 52.0, 130.0),
        SaleLine("sale_line_5", "sale_2", "p3", "سكر ناعم 2 كجم", 5, 12.0, 9.0, 60.0),

        SaleLine("sale_line_6", "sale_3", "p8", "ماء معبأ 330 مل كرتون", 2, 15.0, 11.0, 30.0),
        SaleLine("sale_line_7", "sale_3", "p6", "معكرونة 400 جم", 4, 4.5, 3.0, 18.0),

        SaleLine("sale_line_8", "sale_4", "p12", "مناديل ورقية 10 عبوات", 2, 24.0, 18.0, 48.0),
        SaleLine("sale_line_9", "sale_4", "p10", "تونة خفيفة بالزيت 185 جم", 4, 7.5, 5.5, 30.0),
        SaleLine("sale_line_10", "sale_4", "p1", "أرز بسمتي 5 كجم", 1, 45.0, 35.0, 45.0),

        SaleLine("sale_line_11", "sale_5", "p7", "صلصة طماطم 135 جم", 6, 2.5, 1.8, 15.0),
        SaleLine("sale_line_12", "sale_5", "p9", "طحين أبيض فاخر 1 كجم", 3, 5.0, 3.5, 15.0),

        SaleLine("sale_line_13", "sale_6", "p11", "جبنة بيضاء فيتا 500 جم", 4, 14.5, 11.0, 58.0),
        SaleLine("sale_line_14", "sale_6", "p3", "سكر ناعم 2 كجم", 2, 12.0, 9.0, 24.0),

        SaleLine("sale_line_15", "sale_7", "p8", "ماء معبأ 330 مل كرتون", 4, 15.0, 11.0, 60.0),
        SaleLine("sale_line_16", "sale_7", "p5", "شاي أسود 100 كيس", 2, 16.0, 12.0, 32.0),

        SaleLine("sale_line_17", "sale_8", "p2", "زيت نباتي 1.5 لتر", 3, 18.0, 14.0, 54.0),
        SaleLine("sale_line_18", "sale_8", "p6", "معكرونة 400 جم", 6, 4.5, 3.0, 27.0),

        SaleLine("sale_line_19", "sale_9", "p10", "تونة خفيفة بالزيت 185 جم", 2, 7.5, 5.5, 15.0),
        SaleLine("sale_line_20", "sale_9", "p4", "حليب مجفف 1.8 كجم", 1, 65.0, 52.0, 65.0),

        SaleLine("sale_line_21", "sale_10", "p12", "مناديل ورقية 10 عبوات", 1, 24.0, 18.0, 24.0),
        SaleLine("sale_line_22", "sale_10", "p1", "أرز بسمتي 5 كجم", 1, 45.0, 35.0, 45.0)
    )

    // -------------------------------------------------------------------------
    // 9. CUSTOMER PAYMENTS & SALE RETURNS
    // -------------------------------------------------------------------------
    val sampleCustomerPayments: List<CustomerPayment> = listOf(
        CustomerPayment(
            id = "pay_1",
            customerId = "c1",
            amount = 100.0,
            paymentMethodId = "pm_cash",
            financialAccountId = "acc_cash",
            reference = "PAY-000001",
            transactionDate = "2026-09-05",
            notes = "سداد نقدي جزئي من أحمد الشمري"
        ),
        CustomerPayment(
            id = "pay_2",
            customerId = "c2",
            amount = 100.0,
            paymentMethodId = "pm_transfer",
            financialAccountId = "acc_bank",
            reference = "PAY-000002",
            transactionDate = "2026-09-05",
            notes = "تحويل بنكي تسوية رصيد خالد العتيبي بالكامل"
        ),
        CustomerPayment(
            id = "pay_3",
            customerId = "c4",
            amount = 50.0,
            paymentMethodId = "pm_card",
            financialAccountId = "acc_bank",
            reference = "PAY-000003",
            transactionDate = "2026-09-05",
            notes = "دفع بالشبكة من محمد الغامدي"
        )
    )

    val sampleSaleReturns: List<SaleReturn> = listOf(
        SaleReturn(
            id = "sret_1",
            saleId = "sale_8",
            customerId = "c6",
            returnDate = "2026-09-05",
            reason = "تغيير رغبة العميل",
            reasonCode = "customer_request",
            reasonLabelSnapshot = "تغيير رغبة العميل",
            amount = 18.0,
            status = "ACTIVE"
        )
    )

    val sampleSaleReturnLines: List<SaleReturnLine> = listOf(
        SaleReturnLine(
            id = "sret_line_1",
            saleReturnId = "sret_1",
            saleLineId = "sale_line_17",
            productId = "p2",
            productNameSnapshot = "زيت نباتي 1.5 لتر",
            quantity = 1,
            unitPrice = 18.0,
            costPriceAtReturn = 14.0,
            subtotal = 18.0
        )
    )

    // -------------------------------------------------------------------------
    // 10. EXPENSE CATEGORIES & EXPENSES (5 categories & 5 expenses)
    // -------------------------------------------------------------------------
    val sampleExpenseCategories: List<ExpenseCategory> = listOf(
        ExpenseCategory("exp_cat_1", "إيجار ومرافق", "مصاريف إيجار العقارات والمستودعات", true),
        ExpenseCategory("exp_cat_2", "كهرباء ومياه", "فواتير الخدمات والمرافق الشهرية", true),
        ExpenseCategory("exp_cat_3", "رواتب وأجور", "رواتب وبدلات ومكافآت العاملين", true),
        ExpenseCategory("exp_cat_4", "نقل وضيافة", "أجور التوصيل والوقود وضيافة المحل", true),
        ExpenseCategory("exp_cat_5", "صيانة ونظافة", "أدوات ومواد الصيانة والتعقيم", true)
    )

    val sampleExpenses: List<Expense> = listOf(
        Expense(
            id = "exp_1",
            categoryId = "exp_cat_1",
            amount = 1500.0,
            paymentMethodId = "pm_transfer",
            financialAccountId = "acc_bank",
            date = "2026-09-01",
            description = "دفعة إيجار المحل الشهرية"
        ),
        Expense(
            id = "exp_2",
            categoryId = "exp_cat_2",
            amount = 280.0,
            paymentMethodId = "pm_card",
            financialAccountId = "acc_bank",
            date = "2026-09-02",
            description = "فاتورة الكهرباء لشهر أغسطس"
        ),
        Expense(
            id = "exp_3",
            categoryId = "exp_cat_3",
            amount = 800.0,
            paymentMethodId = "pm_cash",
            financialAccountId = "acc_cash",
            date = "2026-09-03",
            description = "مكافأة العامل الأسبوعية"
        ),
        Expense(
            id = "exp_4",
            categoryId = "exp_cat_4",
            amount = 65.0,
            paymentMethodId = "pm_cash",
            financialAccountId = "acc_cash",
            date = "2026-09-04",
            description = "أجرة نقل وتوصيل بضاعة"
        ),
        Expense(
            id = "exp_5",
            categoryId = "exp_cat_5",
            amount = 95.0,
            paymentMethodId = "pm_cash",
            financialAccountId = "acc_cash",
            date = "2026-09-05",
            description = "مستلزمات نظافة وتعقيم للمحل"
        )
    )

    // -------------------------------------------------------------------------
    // 11. STOCK MOVEMENTS (Perpetual inventory consistency)
    // -------------------------------------------------------------------------
    val sampleStockMovements: List<StockMovementEntity> = buildList {
        // Purchases: PURCHASE_IN
        samplePurchaseLines.forEach { line ->
            if (!line.productId.isNullOrBlank()) {
                val pur = samplePurchases.first { it.id == line.purchaseId }
                add(
                    StockMovementEntity(
                        id = "sm_${line.id}",
                        productId = line.productId,
                        productNameSnapshot = line.productNameSnapshot,
                        transactionId = pur.id,
                        lineId = line.id,
                        date = pur.purchaseDate,
                        movementType = InventoryMovementType.PURCHASE_IN.name,
                        quantityIn = line.quantity,
                        quantityOut = 0,
                        unitCost = line.unitCost,
                        reference = pur.invoiceNumber,
                        referenceType = "PURCHASE",
                        referenceId = pur.id,
                        status = "ACTIVE"
                    )
                )
            }
        }

        // Purchase Returns: PURCHASE_RETURN_OUT
        samplePurchaseReturnLines.forEach { line ->
            if (!line.productId.isNullOrBlank()) {
                val pret = samplePurchaseReturns.first { it.id == line.purchaseReturnId }
                add(
                    StockMovementEntity(
                        id = "sm_${line.id}",
                        productId = line.productId,
                        productNameSnapshot = line.productNameSnapshot,
                        transactionId = pret.id,
                        lineId = line.id,
                        date = pret.returnDate,
                        movementType = InventoryMovementType.PURCHASE_RETURN_OUT.name,
                        quantityIn = 0,
                        quantityOut = line.quantity,
                        unitCost = line.unitCost,
                        reference = pret.id,
                        referenceType = "PURCHASE_RETURN",
                        referenceId = pret.id,
                        status = "ACTIVE"
                    )
                )
            }
        }

        // Sales: SALE_OUT
        sampleSaleLines.forEach { line ->
            if (!line.productId.isNullOrBlank()) {
                val sale = sampleSales.first { it.id == line.saleId }
                add(
                    StockMovementEntity(
                        id = "sm_${line.id}",
                        productId = line.productId,
                        productNameSnapshot = line.productNameSnapshot,
                        transactionId = sale.id,
                        lineId = line.id,
                        date = sale.transactionDate,
                        movementType = InventoryMovementType.SALE_OUT.name,
                        quantityIn = 0,
                        quantityOut = line.quantity,
                        unitCost = line.costPriceAtSale,
                        reference = sale.invoiceNumber,
                        referenceType = "SALE",
                        referenceId = sale.id,
                        status = "ACTIVE"
                    )
                )
            }
        }

        // Sale Returns: SALE_RETURN_IN
        sampleSaleReturnLines.forEach { line ->
            if (!line.productId.isNullOrBlank()) {
                val sret = sampleSaleReturns.first { it.id == line.saleReturnId }
                add(
                    StockMovementEntity(
                        id = "sm_${line.id}",
                        productId = line.productId,
                        productNameSnapshot = line.productNameSnapshot,
                        transactionId = sret.id,
                        lineId = line.id,
                        date = sret.returnDate,
                        movementType = InventoryMovementType.SALE_RETURN_IN.name,
                        quantityIn = line.quantity,
                        quantityOut = 0,
                        unitCost = line.costPriceAtReturn,
                        reference = sret.id,
                        referenceType = "SALE_RETURN",
                        referenceId = sret.id,
                        status = "ACTIVE"
                    )
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // 12. UNIFIED TRANSACTIONS (Backward compatibility, reports, dashboard)
    // -------------------------------------------------------------------------
    val sampleTransactions: List<TransactionItem> = buildList {
        // Sales
        sampleSales.forEach { sale ->
            val customer = sampleCustomers.find { it.id == sale.customerId }
            val cName = customer?.customerName ?: "عميل عام"
            add(
                TransactionItem(
                    id = sale.id,
                    title = "فاتورة ${sale.invoiceNumber}",
                    customerNameSnapshot = cName,
                    customerName = cName,
                    activityType = when (sale.typedSaleType) {
                        SaleType.CASH -> "شراء كاش"
                        SaleType.CREDIT -> "شراء آجل"
                        SaleType.MIXED -> "شراء جزئي"
                    },
                    amount = sale.totalAmount,
                    isCredit = sale.creditAmount > 0.0,
                    date = sale.transactionDate,
                    relativeTime = "اليوم",
                    notes = "فاتورة رقم ${sale.invoiceNumber}",
                    customerId = sale.customerId,
                    transactionType = TransactionType.SALE,
                    saleType = sale.typedSaleType,
                    paymentStatus = sale.typedPaymentStatus,
                    paidAmount = sale.paidAmount,
                    creditAmount = sale.creditAmount
                )
            )
        }

        // Customer Payments
        sampleCustomerPayments.forEach { pay ->
            val customer = sampleCustomers.find { it.id == pay.customerId }
            val cName = customer?.customerName ?: ""
            add(
                TransactionItem(
                    id = pay.id,
                    title = "سداد دفعة",
                    customerNameSnapshot = cName,
                    customerName = cName,
                    activityType = "تسديد",
                    amount = pay.amount,
                    isCredit = false,
                    date = pay.transactionDate,
                    relativeTime = "اليوم",
                    notes = pay.notes ?: "سداد نقدي",
                    settlementType = SettlementType.PARTIAL,
                    customerId = pay.customerId,
                    transactionType = TransactionType.CUSTOMER_PAYMENT,
                    paymentStatus = PaymentStatus.PAID,
                    paidAmount = pay.amount,
                    creditAmount = 0.0
                )
            )
        }

        // Sale Returns
        sampleSaleReturns.forEach { sret ->
            val customer = sampleCustomers.find { it.id == sret.customerId }
            val cName = customer?.customerName ?: ""
            val originalSale = sampleSales.find { it.id == sret.saleId }
            add(
                TransactionItem(
                    id = sret.id,
                    title = "مرتجع فاتورة ${originalSale?.invoiceNumber ?: ""}",
                    customerNameSnapshot = cName,
                    customerName = cName,
                    activityType = "مرتجع مبيعات",
                    amount = sret.amount,
                    isCredit = false,
                    date = sret.returnDate,
                    relativeTime = "اليوم",
                    notes = sret.reason,
                    customerId = sret.customerId,
                    transactionType = TransactionType.SALE_RETURN,
                    paidAmount = 0.0,
                    creditAmount = 0.0
                )
            )
        }

        // Purchases (customerId null to prevent FK constraint with customer entity)
        samplePurchases.forEach { pur ->
            val supplier = sampleSuppliers.find { it.id == pur.supplierId }
            val supName = supplier?.name ?: ""
            add(
                TransactionItem(
                    id = pur.id,
                    title = "فاتورة شراء ${pur.invoiceNumber}",
                    customerNameSnapshot = supName,
                    customerName = supName,
                    activityType = "فاتورة شراء",
                    amount = pur.totalAmount,
                    isCredit = pur.creditAmount > 0.0,
                    date = pur.purchaseDate,
                    relativeTime = "اليوم",
                    notes = pur.notes ?: "",
                    customerId = null,
                    transactionType = TransactionType.PURCHASE,
                    paidAmount = pur.paidAmount,
                    creditAmount = pur.creditAmount
                )
            )
        }

        // Supplier Payments
        sampleSupplierPayments.forEach { spay ->
            val supplier = sampleSuppliers.find { it.id == spay.supplierId }
            val supName = supplier?.name ?: ""
            add(
                TransactionItem(
                    id = spay.id,
                    title = "سداد مورد: $supName",
                    customerNameSnapshot = supName,
                    customerName = supName,
                    activityType = "سداد مورد",
                    amount = spay.amount,
                    isCredit = false,
                    date = spay.paymentDate,
                    relativeTime = "اليوم",
                    notes = spay.notes ?: "",
                    customerId = null,
                    transactionType = TransactionType.SUPPLIER_PAYMENT,
                    paidAmount = spay.amount,
                    creditAmount = 0.0
                )
            )
        }

        // Purchase Returns
        samplePurchaseReturns.forEach { pret ->
            val supplier = sampleSuppliers.find { it.id == pret.supplierId }
            val supName = supplier?.name ?: ""
            val originalPur = samplePurchases.find { it.id == pret.purchaseId }
            add(
                TransactionItem(
                    id = pret.id,
                    title = "مرتجع شراء: ${originalPur?.invoiceNumber ?: ""}",
                    customerNameSnapshot = supName,
                    customerName = supName,
                    activityType = "مرتجع شراء",
                    amount = pret.amount,
                    isCredit = false,
                    date = pret.returnDate,
                    relativeTime = "اليوم",
                    notes = pret.reason,
                    customerId = null,
                    transactionType = TransactionType.PURCHASE_RETURN,
                    paidAmount = 0.0,
                    creditAmount = 0.0
                )
            )
        }

        // Expenses
        sampleExpenses.forEach { exp ->
            val category = sampleExpenseCategories.find { it.id == exp.categoryId }
            add(
                TransactionItem(
                    id = exp.id,
                    title = "مصروف: ${category?.name ?: ""}",
                    customerNameSnapshot = "",
                    customerName = "",
                    activityType = "مصروف تشغيلي",
                    amount = exp.amount,
                    isCredit = false,
                    date = exp.date,
                    relativeTime = "اليوم",
                    notes = exp.description,
                    customerId = null,
                    transactionType = TransactionType.EXPENSE,
                    paidAmount = exp.amount,
                    creditAmount = 0.0
                )
            )
        }
    }

    val sampleTransactionLines: List<TransactionItemLineEntity> = buildList {
        sampleSaleLines.forEach { line ->
            add(
                TransactionItemLineEntity(
                    transactionId = line.saleId,
                    productId = line.productId,
                    productNameSnapshot = line.productNameSnapshot,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    costPrice = line.costPriceAtSale,
                    subtotal = line.subtotal
                )
            )
        }
        samplePurchaseLines.forEach { line ->
            add(
                TransactionItemLineEntity(
                    transactionId = line.purchaseId,
                    productId = line.productId,
                    productNameSnapshot = line.productNameSnapshot,
                    quantity = line.quantity,
                    unitPrice = line.unitCost,
                    costPrice = line.unitCost,
                    subtotal = line.subtotal
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // 13. NOTIFICATIONS & STORE INFO
    // -------------------------------------------------------------------------
    val sampleNotifications: List<NotificationItem> = listOf(
        NotificationItem("n1", "أحمد الشمري", "تسجيل معاملة", 142.0, "منذ ساعة", false, false, "sale_1"),
        NotificationItem("n2", "خالد العتيبي", "تسديد", 100.0, "منذ ساعتين", true, false, "pay_2"),
        NotificationItem("n3", "شركة الأغذية المتحدة", "توريد بضاعة", 2320.0, "أمس", false, true, "pur_1"),
        NotificationItem("n4", "محمد الغامدي", "تسديد", 50.0, "أمس", true, true, "pay_3")
    )

    val sampleStoreInfo: StoreInfoEntity = StoreInfoEntity(
        id = 1,
        storeName = "تموينات الأمانة",
        ownerName = "عبدالله التميمي",
        phone = "0501234567",
        address = "الرياض - شارع العليا العام",
        taxNumber = "300123456700003",
        crNumber = "1010123456",
        isSaved = true
    )
}
