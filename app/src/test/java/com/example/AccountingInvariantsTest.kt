package com.example

import com.example.model.CustomerAccount
import com.example.model.OperationStatus
import com.example.model.PaymentMethodType
import com.example.model.PaymentStatus
import com.example.model.ProductItem
import com.example.model.SaleLineItemSnapshot
import com.example.model.SaleSettlement
import com.example.model.SaleType
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.model.typedPaymentStatus
import com.example.model.typedSaleType
import com.example.model.typedTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * =============================================================================
 * AUTOMATED ACCOUNTING FOUNDATION TEST SUITE (PHASE 1 -> PHASE 2 PREREQUISITE)
 * =============================================================================
 *
 * Validates the 10 Core Accounting Invariants required by the SmallStore
 * accounting specification before proceeding to Phase 2 (Customer Ledger & Schema Migration).
 */
class AccountingInvariantsTest {

    /**
     * TEST 1 — CASH SALE
     *
     * A sale has:
     * totalAmount = 100
     * paidAmount = 100
     * creditAmount = 0
     *
     * Expected:
     * SaleType = CASH
     * PaymentStatus = PAID
     */
    @Test
    fun test1_CashSale() {
        val settlement = SaleSettlement.create(
            totalAmount = 100.0,
            paidAmount = 100.0,
            creditAmount = 0.0
        )

        assertEquals("SaleType must be CASH when creditAmount is 0", SaleType.CASH, settlement.saleType)
        assertEquals("PaymentStatus must be PAID for full cash settlement", PaymentStatus.PAID, settlement.paymentStatus)
    }

    /**
     * TEST 2 — CREDIT SALE
     *
     * A sale has:
     * totalAmount = 100
     * paidAmount = 0
     * creditAmount = 100
     *
     * Expected:
     * SaleType = CREDIT
     * PaymentStatus = UNPAID
     */
    @Test
    fun test2_CreditSale() {
        val settlement = SaleSettlement.create(
            totalAmount = 100.0,
            paidAmount = 0.0,
            creditAmount = 100.0
        )

        assertEquals("SaleType must be CREDIT when paidAmount is 0", SaleType.CREDIT, settlement.saleType)
        assertEquals("PaymentStatus must be UNPAID when zero paid upfront", PaymentStatus.UNPAID, settlement.paymentStatus)
    }

    /**
     * TEST 3 — MIXED SALE
     *
     * A sale has:
     * totalAmount = 100
     * paidAmount = 60
     * creditAmount = 40
     *
     * Expected:
     * SaleType = MIXED
     * And:
     * totalAmount = paidAmount + creditAmount
     */
    @Test
    fun test3_MixedSale() {
        val totalAmount = 100.0
        val paidAmount = 60.0
        val creditAmount = 40.0

        val settlement = SaleSettlement.create(
            totalAmount = totalAmount,
            paidAmount = paidAmount,
            creditAmount = creditAmount
        )

        assertEquals("SaleType must be MIXED for split payments", SaleType.MIXED, settlement.saleType)
        assertEquals("PaymentStatus must be PARTIAL for split payment", PaymentStatus.PARTIAL, settlement.paymentStatus)
        assertEquals(
            "totalAmount must exactly equal paidAmount + creditAmount",
            totalAmount,
            settlement.paidAmount + settlement.creditAmount,
            0.0001
        )
    }

    /**
     * TEST 4 — PAYMENT IS NOT A SALE
     *
     * A customer payment of 30 against an existing receivable must be represented as:
     * TransactionType.CUSTOMER_PAYMENT
     *
     * It must never become:
     * TransactionType.SALE
     */
    @Test
    fun test4_PaymentIsNotASale() {
        val paymentTransaction = TransactionItem(
            id = "tx_pay_001",
            title = "تسديد دفعة",
            customerName = "محمد علي",
            activityType = "تسديد",
            amount = 30.0,
            isCredit = false,
            date = "2026-09-20",
            relativeTime = "الآن",
            notes = "دفعة على الحساب"
        )

        val resolvedType = paymentTransaction.typedTransactionType

        assertEquals(
            "Customer payment must resolve to TransactionType.CUSTOMER_PAYMENT",
            TransactionType.CUSTOMER_PAYMENT,
            resolvedType
        )
        assertNotEquals(
            "Customer payment must NEVER be classified as TransactionType.SALE",
            TransactionType.SALE,
            resolvedType
        )
    }

    /**
     * TEST 5 — DEBT IS NOT A PAYMENT METHOD
     *
     * Verify that the new PaymentMethodType does not contain DEBT.
     */
    @Test
    fun test5_DebtIsNotAPaymentMethod() {
        val methodNames = PaymentMethodType.entries.map { it.name }

        assertFalse(
            "PaymentMethodType MUST NOT contain DEBT. Debt is an accounts receivable obligation, not a payment instrument.",
            methodNames.contains("DEBT")
        )

        // Verify valid liquid instruments exist
        assertTrue(methodNames.contains("CASH"))
        assertTrue(methodNames.contains("BANK"))
        assertTrue(methodNames.contains("CARD"))
        assertTrue(methodNames.contains("E_WALLET"))
        assertTrue(methodNames.contains("OTHER"))
    }

    /**
     * TEST 6 — FULL/PARTIAL ARE NOT SALE TYPES
     *
     * Verify that SaleType only represents:
     * CASH
     * CREDIT
     * MIXED
     *
     * And never contains FULL or PARTIAL (which belong to PaymentStatus).
     */
    @Test
    fun test6_FullPartialAreNotSaleTypes() {
        val saleTypeNames = SaleType.entries.map { it.name }.toSet()
        val expectedSaleTypes = setOf("CASH", "CREDIT", "MIXED")

        assertEquals(
            "SaleType must strictly and exclusively contain CASH, CREDIT, MIXED",
            expectedSaleTypes,
            saleTypeNames
        )
        assertFalse(
            "FULL must not be a SaleType (it belongs to PaymentStatus)",
            saleTypeNames.contains("FULL")
        )
        assertFalse(
            "PARTIAL must not be a SaleType (it belongs to PaymentStatus)",
            saleTypeNames.contains("PARTIAL")
        )
    }

    /**
     * TEST 7 — OPERATION STATUS
     *
     * A new operation must be ACTIVE.
     * A reversed operation must be REVERSED.
     */
    @Test
    fun test7_OperationStatus() {
        val activeOperationStatus = OperationStatus.ACTIVE
        val reversedOperationStatus = OperationStatus.REVERSED

        assertEquals("ACTIVE operation status name", "ACTIVE", activeOperationStatus.name)
        assertEquals("REVERSED operation status name", "REVERSED", reversedOperationStatus.name)

        // Verify all valid lifecycle states
        val statusNames = OperationStatus.entries.map { it.name }
        assertTrue(statusNames.contains("ACTIVE"))
        assertTrue(statusNames.contains("REVERSED"))
        assertEquals("OperationStatus has exactly 2 lifecycle states", 2, statusNames.size)
    }

    /**
     * TEST 8 — INVALID MIXED SALE
     *
     * Reject or flag a mixed sale where:
     * paidAmount + creditAmount != totalAmount
     */
    @Test
    fun test8_InvalidMixedSaleRejected() {
        val totalAmount = 100.0
        val paidAmount = 50.0
        val creditAmount = 30.0 // 50 + 30 = 80 != 100

        try {
            SaleSettlement.create(
                totalAmount = totalAmount,
                paidAmount = paidAmount,
                creditAmount = creditAmount
            )
            fail("Expected IllegalArgumentException when paidAmount + creditAmount != totalAmount")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Exception message must mention mismatch",
                e.message?.contains("Invalid sale settlement") == true
            )
        }
    }

    /**
     * TEST 9 — HISTORICAL COST IMMUTABILITY
     *
     * If a historical sale has costPriceAtSale = 5,
     * changing Product.costPrice to 8 must not change the historical value.
     */
    @Test
    fun test9_HistoricalCostImmutability() {
        // 1. Initial product with cost price 5.0
        var catalogProduct = ProductItem(
            id = "prod_sugar_1kg",
            name = "سكر 1 كجم",
            price = 7.0,
            costPrice = 5.0
        )

        // 2. Sale recorded preserving snapshot of costPrice at sale time
        val historicalSaleLine = SaleLineItemSnapshot(
            productId = catalogProduct.id,
            productNameSnapshot = catalogProduct.name,
            quantity = 2.0,
            unitPriceAtSale = catalogProduct.price,
            costPriceAtSale = catalogProduct.costPrice // Captured at 5.0
        )

        assertEquals("Cost price at sale must be 5.0", 5.0, historicalSaleLine.costPriceAtSale, 0.0001)
        assertEquals("Total cost at sale must be 10.0", 10.0, historicalSaleLine.totalCost, 0.0001)
        assertEquals("Subtotal at sale must be 14.0", 14.0, historicalSaleLine.subtotal, 0.0001)
        assertEquals("Profit margin must be 4.0", 4.0, historicalSaleLine.profitMargin, 0.0001)

        // 3. Merchant subsequently updates catalog product cost price to 8.0
        catalogProduct = catalogProduct.copy(costPrice = 8.0, price = 10.0)

        // 4. Assert historical sale line snapshot remains completely unchanged
        assertEquals(
            "Historical costPriceAtSale must remain 5.0 even after catalog product cost was updated to 8.0",
            5.0,
            historicalSaleLine.costPriceAtSale,
            0.0001
        )
        assertEquals(
            "Historical profit margin must remain 4.0",
            4.0,
            historicalSaleLine.profitMargin,
            0.0001
        )
        assertNotEquals(
            "Historical line item must not reflect new catalog cost",
            catalogProduct.costPrice,
            historicalSaleLine.costPriceAtSale
        )
    }

    /**
     * TEST 10 — CUSTOMER IDENTITY BY STABLE ID (NOT NAME)
     *
     * Do not create tests that depend on customer names being unique.
     * Two customers may have the same name.
     * The tests must prepare the architecture for stable-ID relationships in Phase 2.
     */
    @Test
    fun test10_CustomerIdentityByStableIdNotName() {
        val commonName = "خالد عبدالله"

        // Two distinct customer accounts sharing identical names but distinct unique IDs
        val customerA = CustomerAccount(
            id = "cust_uuid_001",
            customerName = commonName,
            balance = 150.0,
            totalDebt = 150.0,
            phone = "0599111222"
        )

        val customerB = CustomerAccount(
            id = "cust_uuid_002",
            customerName = commonName,
            balance = 0.0,
            totalDebt = 0.0,
            phone = "0599333444"
        )

        // Assert customer names are identical
        assertEquals(customerA.customerName, customerB.customerName)

        // Assert customer identities are distinct via ID
        assertNotEquals(
            "Customers with same name must have distinct IDs",
            customerA.id,
            customerB.id
        )

        // Verify transaction linking via customerId (preparing architecture for Phase 2 ledger)
        val txForCustomerA = TransactionItem(
            id = "tx_001",
            customerName = commonName,
            activityType = "شراء آجل",
            amount = 150.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerId = customerA.id
        )

        val txForCustomerB = TransactionItem(
            id = "tx_002",
            customerName = commonName,
            activityType = "شراء كاش",
            amount = 50.0,
            isCredit = false,
            date = "2026-09-20",
            relativeTime = "الآن",
            customerId = customerB.id
        )

        // Lookup by stable customer ID unambiguously routes transactions to the correct entity
        assertEquals("Transaction 1 belongs to Customer A", customerA.id, txForCustomerA.customerId)
        assertEquals("Transaction 2 belongs to Customer B", customerB.id, txForCustomerB.customerId)
        assertNotEquals(txForCustomerA.customerId, txForCustomerB.customerId)
    }
}
