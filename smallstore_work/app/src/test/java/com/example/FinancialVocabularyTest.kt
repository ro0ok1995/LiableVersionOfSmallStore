package com.example

import com.example.accounting.CustomerLedgerCalculator
import com.example.model.FinancialTimestampUtils
import com.example.model.LegacyAccountingBridge
import com.example.model.LegacyAccountingCompatibility
import com.example.model.OperationStatus
import com.example.model.PaymentMethodOption
import com.example.model.PaymentMethodType
import com.example.model.PaymentStatus
import com.example.model.SaleType
import com.example.model.SettlementType
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.model.epochTimestampMillis
import com.example.model.typedOperationStatus
import com.example.model.typedPaymentMethod
import com.example.model.typedPaymentStatus
import com.example.model.typedSaleType
import com.example.model.typedTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verification test suite for Phase 1: Typed Financial Vocabulary and LegacyAccountingBridge.
 */
class FinancialVocabularyTest {

    @Test
    fun testTransactionTypeEnumValues() {
        val expectedTypes = listOf(
            "SALE",
            "CUSTOMER_PAYMENT",
            "SALE_RETURN",
            "CUSTOMER_REFUND",
            "PURCHASE",
            "SUPPLIER_PAYMENT",
            "PURCHASE_RETURN",
            "EXPENSE",
            "OPENING_BALANCE",
            "BALANCE_ADJUSTMENT",
            "STOCK_ADJUSTMENT",
            "REVERSAL"
        )
        val actualTypes = TransactionType.entries.map { it.name }
        assertEquals(expectedTypes, actualTypes)
    }

    @Test
    fun testSaleTypeEnumValues() {
        val expected = listOf("CASH", "CREDIT", "MIXED")
        assertEquals(expected, SaleType.entries.map { it.name })
    }

    @Test
    fun testPaymentStatusEnumValues() {
        val expected = listOf("PAID", "PARTIAL", "UNPAID")
        assertEquals(expected, PaymentStatus.entries.map { it.name })
    }

    @Test
    fun testOperationStatusEnumValues() {
        val expected = listOf("ACTIVE", "REVERSED")
        assertEquals(expected, OperationStatus.entries.map { it.name })
    }

    @Test
    fun testPaymentMethodTypeEnumValues() {
        val expected = listOf("CASH", "BANK", "CARD", "E_WALLET", "OTHER")
        val actual = PaymentMethodType.entries.map { it.name }
        assertEquals(expected, actual)

        // CRITICAL INVARIANT 1: DEBT is NOT a payment method
        assertFalse(actual.contains("DEBT"))
    }

    @Test
    fun testLegacyMappingForCashSale() {
        val tx = TransactionItem(
            id = "tx1",
            title = "شراء مواد",
            customerName = "عميل",
            activityType = "شراء كاش",
            amount = 100.0,
            isCredit = false,
            date = "2026-09-20",
            relativeTime = "الآن",
            settlementType = SettlementType.FULL
        )

        assertEquals(TransactionType.SALE, tx.typedTransactionType)
        assertEquals(SaleType.CASH, tx.typedSaleType)
        assertEquals(PaymentStatus.PAID, tx.typedPaymentStatus)
        assertEquals(OperationStatus.ACTIVE, tx.typedOperationStatus)
    }

    @Test
    fun testLegacyMappingForCreditSaleDoesNotGuessSaleType() {
        // Accounting Rule 3: Legacy isCredit alone cannot distinguish pure CREDIT from MIXED sales.
        // Therefore, do not guess; tx.typedSaleType must return null for legacy records.
        val tx = TransactionItem(
            id = "tx2",
            title = "شراء مواد",
            customerName = "عميل",
            activityType = "شراء آجل",
            amount = 250.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "الآن",
            settlementType = null
        )

        assertEquals(TransactionType.SALE, tx.typedTransactionType)
        assertNull("isCredit=true alone cannot safely distinguish pure CREDIT vs MIXED", tx.typedSaleType)
        assertEquals(PaymentStatus.UNPAID, tx.typedPaymentStatus)
        assertEquals(OperationStatus.ACTIVE, tx.typedOperationStatus)
    }

    @Test
    fun testLegacyMappingNeverInterpretsPartialAsSaleType() {
        // Accounting Rule 2: Never interpret FULL/PARTIAL as SaleType.
        // FULL/PARTIAL describes PaymentStatus, not SaleType.
        val tx = TransactionItem(
            id = "tx3",
            title = "شراء مواد",
            customerName = "عميل",
            activityType = "شراء آجل",
            amount = 300.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "الآن",
            settlementType = SettlementType.PARTIAL
        )

        assertEquals(TransactionType.SALE, tx.typedTransactionType)
        assertNull("Rule 2: PARTIAL must not be interpreted as SaleType", tx.typedSaleType)
        assertEquals(PaymentStatus.PARTIAL, tx.typedPaymentStatus)
    }

    @Test
    fun testLegacyMappingForPayment() {
        val tx = TransactionItem(
            id = "tx4",
            title = "تسديد",
            customerName = "عميل",
            activityType = "تسديد",
            amount = 150.0,
            isCredit = false,
            date = "2026-09-20",
            relativeTime = "الآن",
            settlementType = null
        )

        assertEquals(TransactionType.CUSTOMER_PAYMENT, tx.typedTransactionType)
        assertNull(tx.typedSaleType) // Payments do not have a SaleType
        assertEquals(PaymentStatus.PAID, tx.typedPaymentStatus)
    }

    @Test
    fun testLegacyMappingForPartialPayment() {
        val tx = TransactionItem(
            id = "tx5",
            title = "تسديد دفعة",
            customerName = "عميل",
            activityType = "تسديد",
            amount = 50.0,
            isCredit = false,
            date = "2026-09-20",
            relativeTime = "الآن",
            settlementType = SettlementType.PARTIAL
        )

        assertEquals(TransactionType.CUSTOMER_PAYMENT, tx.typedTransactionType)
        assertEquals(PaymentStatus.PARTIAL, tx.typedPaymentStatus)
    }

    @Test
    fun testLegacyAmbiguousStringReturnsNull() {
        // Accounting Rule 5 & 6: Never use fuzzy contains(); unknown values must return null
        val unknownType = LegacyAccountingBridge.toTransactionType("شراء بالتقسيط غير معروف", false)
        assertNull("Ambiguous legacy string must not be silently guessed", unknownType)
    }

    @Test
    @Suppress("DEPRECATION")
    fun testRule1DebtIsNotAPaymentMethod() {
        // Accounting Rule 1: Never map DEBT to PaymentMethodType
        assertEquals(PaymentMethodType.CASH, LegacyAccountingBridge.toPaymentMethodType(PaymentMethodOption.CASH))
        assertNull("DEBT option must return null", LegacyAccountingBridge.toPaymentMethodType(PaymentMethodOption.DEBT))
        assertNull("null option must return null", LegacyAccountingBridge.toPaymentMethodType(null as PaymentMethodOption?))

        // Raw string mapping
        assertEquals(PaymentMethodType.CASH, LegacyAccountingBridge.toPaymentMethodType("CASH"))
        assertEquals(PaymentMethodType.BANK, LegacyAccountingBridge.toPaymentMethodType("BANK"))
        assertEquals(PaymentMethodType.CARD, LegacyAccountingBridge.toPaymentMethodType("CARD"))
        assertEquals(PaymentMethodType.E_WALLET, LegacyAccountingBridge.toPaymentMethodType("E_WALLET"))
        assertEquals(PaymentMethodType.OTHER, LegacyAccountingBridge.toPaymentMethodType("OTHER"))
        assertNull("String 'DEBT' must return null", LegacyAccountingBridge.toPaymentMethodType("DEBT"))
        assertNull("Unrecognized method must return null", LegacyAccountingBridge.toPaymentMethodType("UNKNOWN"))
    }

    @Test
    fun testOutboundLegacyFieldsConversion() {
        // Cash sale
        val cashFields = LegacyAccountingBridge.toLegacyFields(
            transactionType = TransactionType.SALE,
            saleType = SaleType.CASH,
            paymentStatus = PaymentStatus.PAID
        )
        assertEquals("شراء كاش", cashFields.activityType)
        assertFalse(cashFields.isCredit)
        assertEquals(SettlementType.FULL, cashFields.settlementType)

        // Pure credit sale
        val creditFields = LegacyAccountingBridge.toLegacyFields(
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            paymentStatus = PaymentStatus.UNPAID
        )
        assertEquals("شراء آجل", creditFields.activityType)
        assertTrue(creditFields.isCredit)
        assertNull(creditFields.settlementType)

        // Mixed sale
        val mixedFields = LegacyAccountingBridge.toLegacyFields(
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED,
            paymentStatus = PaymentStatus.PARTIAL
        )
        assertEquals("شراء آجل", mixedFields.activityType)
        assertTrue(mixedFields.isCredit)
        assertEquals(SettlementType.PARTIAL, mixedFields.settlementType)

        // Customer payment full
        val payFullFields = LegacyAccountingBridge.toLegacyFields(
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            paymentStatus = PaymentStatus.PAID
        )
        assertEquals("تسديد", payFullFields.activityType)
        assertFalse(payFullFields.isCredit)
        assertEquals(SettlementType.FULL, payFullFields.settlementType)

        // Customer payment partial
        val payPartialFields = LegacyAccountingBridge.toLegacyFields(
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            paymentStatus = PaymentStatus.PARTIAL
        )
        assertEquals("تسديد", payPartialFields.activityType)
        assertFalse(payPartialFields.isCredit)
        assertEquals(SettlementType.PARTIAL, payPartialFields.settlementType)
    }

    @Test
    fun testTimestampResolution() {
        val millis = FinancialTimestampUtils.parseDateToEpochMillis("2026-09-20")
        assertTrue("Epoch millis must be positive", millis > 0L)

        val tx = TransactionItem(
            id = "tx6",
            title = "اختبار",
            customerName = "عميل",
            activityType = "شراء كاش",
            amount = 10.0,
            isCredit = false,
            date = "2026-09-20",
            relativeTime = "الآن"
        )
        assertTrue(tx.epochTimestampMillis > 0L)
    }

    // -------------------------------------------------------------------------
    // PHASE 1 VERIFICATION TESTS
    // -------------------------------------------------------------------------

    @Test
    fun testTypedTransactionClassificationWorks() {
        val tx = TransactionItem(
            id = "tx_typed_1",
            title = "فاتورة بيع",
            customerNameSnapshot = "عميل تجربة",
            activityType = "legacy_string_ignored",
            amount = 150.0,
            isCredit = false, // Even if legacy boolean says false, typed saleType takes precedence
            date = "2026-09-20",
            relativeTime = "الآن",
            customerId = "cust_1",
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT,
            paymentStatus = PaymentStatus.UNPAID,
            operationStatus = OperationStatus.ACTIVE,
            paymentMethod = null
        )

        assertEquals(TransactionType.SALE, tx.typedTransactionType)
        assertEquals(SaleType.CREDIT, tx.typedSaleType)
        assertEquals(PaymentStatus.UNPAID, tx.typedPaymentStatus)
        assertEquals(OperationStatus.ACTIVE, tx.typedOperationStatus)
        assertNull(tx.typedPaymentMethod)
    }

    @Test
    fun testCashCreditAndMixedAreDistinct() {
        // 1. Enum values are strictly distinct
        val distinctEntries = setOf(SaleType.CASH, SaleType.CREDIT, SaleType.MIXED)
        assertEquals(3, distinctEntries.size)

        // 2. Distinct accounting effects in ledger
        val cashTx = TransactionItem(
            id = "tx_cash",
            amount = 100.0,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerNameSnapshot = "عميل",
            activityType = "",
            isCredit = false,
            customerId = "cust_dist",
            transactionType = TransactionType.SALE,
            saleType = SaleType.CASH
        )
        val creditTx = TransactionItem(
            id = "tx_credit",
            amount = 100.0,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerNameSnapshot = "عميل",
            activityType = "",
            isCredit = false, // Ignored because saleType is CREDIT
            customerId = "cust_dist",
            transactionType = TransactionType.SALE,
            saleType = SaleType.CREDIT
        )
        val mixedTx = TransactionItem(
            id = "tx_mixed",
            amount = 100.0,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerNameSnapshot = "عميل",
            activityType = "",
            isCredit = false, // Ignored because saleType is MIXED
            customerId = "cust_dist",
            transactionType = TransactionType.SALE,
            saleType = SaleType.MIXED
        )

        val cashEntries = CustomerLedgerCalculator.toLedgerEntries("cust_dist", listOf(cashTx))
        val creditEntries = CustomerLedgerCalculator.toLedgerEntries("cust_dist", listOf(creditTx))
        val mixedEntries = CustomerLedgerCalculator.toLedgerEntries("cust_dist", listOf(mixedTx))

        // CASH sale produces 0 debit (no debt incurred)
        assertEquals(0.0, cashEntries.first().debit, 0.001)
        assertEquals(0.0, cashEntries.first().credit, 0.001)

        // CREDIT sale produces full debit
        assertEquals(100.0, creditEntries.first().debit, 0.001)

        // MIXED sale is recognized as distinct from CASH
        assertTrue(mixedEntries.first().debit > 0.0)
        assertEquals(SaleType.MIXED, mixedTx.typedSaleType)
        assertEquals(SaleType.CREDIT, creditTx.typedSaleType)
        assertEquals(SaleType.CASH, cashTx.typedSaleType)
    }

    @Test
    fun testDebtCannotBeAPaymentMethodRule() {
        // Rule 1: "DEBT" must never be treated as a payment method
        val paymentMethodNames = PaymentMethodType.entries.map { it.name }
        assertFalse("DEBT must never exist in PaymentMethodType", paymentMethodNames.contains("DEBT"))

        assertNull(
            "Legacy DEBT option must map to null PaymentMethodType",
            LegacyAccountingBridge.toPaymentMethodType(PaymentMethodOption.DEBT)
        )
        assertNull(
            "String 'DEBT' must map to null PaymentMethodType",
            LegacyAccountingBridge.toPaymentMethodType("DEBT")
        )
        assertNull(
            "String 'debt' must map to null PaymentMethodType",
            LegacyAccountingBridge.toPaymentMethodType("debt")
        )
    }

    @Test
    fun testNewAccountingLogicDoesNotRelyOnLegacyTextMatching() {
        // Provide completely non-standard / arbitrary strings for activityType
        val arbitraryPaymentTx = TransactionItem(
            id = "tx_custom_payment",
            customerNameSnapshot = "عميل",
            activityType = "XYZ_ARBITRARY_UNRECOGNIZED_STRING_123",
            amount = 75.0,
            isCredit = true, // Legacy isCredit=true is NOT authoritative over typed TransactionType
            date = "2026-09-20",
            relativeTime = "الآن",
            customerId = "cust_new_logic",
            transactionType = TransactionType.CUSTOMER_PAYMENT,
            paymentStatus = PaymentStatus.PARTIAL
        )

        assertEquals(TransactionType.CUSTOMER_PAYMENT, arbitraryPaymentTx.typedTransactionType)

        // Calculate ledger entries: should be treated as customer payment (Credit = 75.0)
        val entries = CustomerLedgerCalculator.toLedgerEntries("cust_new_logic", listOf(arbitraryPaymentTx))
        assertEquals(1, entries.size)
        assertEquals(0.0, entries.first().debit, 0.001)
        assertEquals(75.0, entries.first().credit, 0.001) // Payment credits customer balance
        assertEquals(TransactionType.CUSTOMER_PAYMENT, entries.first().transactionType)

        val summary = CustomerLedgerCalculator.calculateCustomerBalance("cust_new_logic", listOf(arbitraryPaymentTx))
        assertEquals(75.0, summary.totalPayments, 0.001)
    }

    @Test
    fun testLegacyCompatibilityDoesNotChangeMeaningOfExistingData() {
        // 1. Historical Cash Sale
        val legacyCash = TransactionItem(
            id = "tx_hist_1",
            customerNameSnapshot = "عميل قديم",
            activityType = "شراء كاش",
            amount = 200.0,
            isCredit = false,
            date = "2026-01-15",
            relativeTime = "منذ أشهر",
            settlementType = SettlementType.FULL
        )
        assertEquals(TransactionType.SALE, legacyCash.typedTransactionType)
        assertEquals(SaleType.CASH, legacyCash.typedSaleType)
        assertEquals(PaymentStatus.PAID, legacyCash.typedPaymentStatus)

        // 2. Historical Credit Sale
        val legacyCredit = TransactionItem(
            id = "tx_hist_2",
            customerNameSnapshot = "عميل قديم",
            activityType = "شراء آجل",
            amount = 350.0,
            isCredit = true,
            date = "2026-01-16",
            relativeTime = "منذ أشهر",
            settlementType = null
        )
        assertEquals(TransactionType.SALE, legacyCredit.typedTransactionType)
        assertNull(legacyCredit.typedSaleType) // Does not guess between pure CREDIT and MIXED
        assertEquals(PaymentStatus.UNPAID, legacyCredit.typedPaymentStatus)

        // 3. Historical Payment
        val legacyPayment = TransactionItem(
            id = "tx_hist_3",
            customerNameSnapshot = "عميل قديم",
            activityType = "تسديد",
            amount = 100.0,
            isCredit = false,
            date = "2026-01-17",
            relativeTime = "منذ أشهر",
            settlementType = SettlementType.FULL
        )
        assertEquals(TransactionType.CUSTOMER_PAYMENT, legacyPayment.typedTransactionType)
        assertEquals(PaymentStatus.PAID, legacyPayment.typedPaymentStatus)

        // 4. Historical Partial Payment
        val legacyPartial = TransactionItem(
            id = "tx_hist_4",
            customerNameSnapshot = "عميل قديم",
            activityType = "تسديد دفعة",
            amount = 50.0,
            isCredit = false,
            date = "2026-01-18",
            relativeTime = "منذ أشهر",
            settlementType = SettlementType.PARTIAL
        )
        assertEquals(TransactionType.CUSTOMER_PAYMENT, legacyPartial.typedTransactionType)
        assertEquals(PaymentStatus.PARTIAL, legacyPartial.typedPaymentStatus)
    }
}

