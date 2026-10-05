package com.example.model

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
        TransactionItem(
            id = "tx1",
            title = "شراء مواد غذائية",
            customerNameSnapshot = "أحمد الشمري",
            customerName = "أحمد الشمري",
            activityType = "شراء آجل",
            amount = 250.0,
            isCredit = true,
            date = "2026-09-05",
            relativeTime = "منذ 15 دقيقة",
            notes = "فاتورة بقالة",
            customerId = "c1",
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            paymentStatus = PaymentStatus.UNPAID,
            paidAmount = 0.0,
            creditAmount = 250.0
        ),
        TransactionItem(
            id = "tx2",
            title = "تسديد دفعة",
            customerNameSnapshot = "خالد العتيبي",
            customerName = "خالد العتيبي",
            activityType = "تسديد",
            amount = 100.0,
            isCredit = false,
            date = "2026-09-05",
            relativeTime = "منذ ساعة",
            notes = "سداد نقدي",
            settlementType = SettlementType.PARTIAL,
            customerId = "c2",
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            paymentStatus = PaymentStatus.PARTIAL,
            paidAmount = 100.0,
            creditAmount = 0.0
        ),
        TransactionItem(
            id = "tx3",
            title = "شراء كاش",
            customerNameSnapshot = "عميل عام",
            customerName = "عميل عام",
            activityType = "شراء كاش",
            amount = 85.0,
            isCredit = false,
            date = "2026-09-05",
            relativeTime = "منذ ساعتين",
            notes = "دفع فوري",
            customerId = null,
            transactionType = TransactionType.SALE,
            saleType = SaleType.CASH,
            paymentStatus = PaymentStatus.PAID,
            paidAmount = 85.0,
            creditAmount = 0.0
        ),
        TransactionItem(
            id = "tx4",
            title = "شراء مواد استهلاكية",
            customerNameSnapshot = "محمد الغامدي",
            customerName = "محمد الغامدي",
            activityType = "شراء آجل",
            amount = 450.0,
            isCredit = true,
            date = "2026-09-04",
            relativeTime = "أمس",
            notes = "مشتريات منزلية",
            customerId = "c4",
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            paymentStatus = PaymentStatus.UNPAID,
            paidAmount = 0.0,
            creditAmount = 450.0
        ),
        TransactionItem(
            id = "tx5",
            title = "تسديد حساب",
            customerNameSnapshot = "أحمد الشمري",
            customerName = "أحمد الشمري",
            activityType = "تسديد",
            amount = 300.0,
            isCredit = false,
            date = "2026-09-04",
            relativeTime = "أمس",
            notes = "تحويل بنكي",
            settlementType = SettlementType.FULL,
            customerId = "c1",
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            paymentStatus = PaymentStatus.PAID,
            paidAmount = 300.0,
            creditAmount = 0.0
        ),
        TransactionItem(
            id = "tx6",
            title = "شراء آجل",
            customerNameSnapshot = "عبدالله الشهري",
            customerName = "عبدالله الشهري",
            activityType = "شراء آجل",
            amount = 310.0,
            isCredit = true,
            date = "2026-09-03",
            relativeTime = "منذ يومين",
            notes = "أدوات ومستلزمات",
            customerId = "c6",
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            paymentStatus = PaymentStatus.UNPAID,
            paidAmount = 0.0,
            creditAmount = 310.0
        ),
        TransactionItem(
            id = "tx7",
            title = "تسديد نقدي",
            customerNameSnapshot = "سعيد القحطاني",
            customerName = "سعيد القحطاني",
            activityType = "تسديد",
            amount = 500.0,
            isCredit = false,
            date = "2026-09-01",
            relativeTime = "منذ 4 أيام",
            notes = "تسوية كاملة",
            settlementType = SettlementType.FULL,
            customerId = "c3",
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            paymentStatus = PaymentStatus.PAID,
            paidAmount = 500.0,
            creditAmount = 0.0
        )
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
