package com.example.accounting

import com.example.model.CustomerAccount
import com.example.model.PeriodFilter
import com.example.model.SettlementType
import com.example.model.TransactionItem
import com.example.viewmodel.AnalysisCenterViewModel
import com.example.viewmodel.DebtAgingUtils
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.StatementTxFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests verifying Phase 2.2 Accounting Rules:
 *
 * 1. Two customers with the exact same name have completely independent financial transactions.
 * 2. A transaction belonging to Customer A is never displayed in Customer B's account merely because their names match.
 * 3. customerId == null is never silently assigned to a customer by name (zero name fallback in financial resolution).
 * 4. Debt aging and statement rows strictly resolve transactions by persistent customerId.
 */
class CustomerPersistentIdentityTest {

    private val sharedName = "محمد عبد الله"

    private val customerA = CustomerAccount(
        id = "cust_alpha_001",
        customerName = sharedName,
        balance = 500.0,
        totalDebt = 500.0,
        phone = "0599111111"
    )

    private val customerB = CustomerAccount(
        id = "cust_beta_002",
        customerName = sharedName,
        balance = 150.0,
        totalDebt = 150.0,
        phone = "0599222222"
    )

    private val allCustomers = listOf(customerA, customerB)

    @Test
    fun twoCustomersWithSameName_haveIndependentTransactionsAndBalances() {
        val txCustA = TransactionItem(
            id = "tx_a_1",
            title = "شراء آجل",
            customerName = sharedName,
            activityType = "شراء آجل",
            amount = 500.0,
            isCredit = true,
            date = "2026-09-15",
            relativeTime = "اليوم",
            customerId = customerA.id
        )

        val txCustB = TransactionItem(
            id = "tx_b_1",
            title = "شراء آجل",
            customerName = sharedName,
            activityType = "شراء آجل",
            amount = 150.0,
            isCredit = true,
            date = "2026-09-16",
            relativeTime = "اليوم",
            customerId = customerB.id
        )

        val allTransactions = listOf(txCustA, txCustB)

        // 1. Independent ledger balances
        val balanceA = CustomerLedgerCalculator.calculateCustomerBalance(customerA.id, allTransactions)
        val balanceB = CustomerLedgerCalculator.calculateCustomerBalance(customerB.id, allTransactions)

        assertEquals(500.0, balanceA.balance, 0.0001)
        assertEquals(500.0, balanceA.totalCreditSales, 0.0001)

        assertEquals(150.0, balanceB.balance, 0.0001)
        assertEquals(150.0, balanceB.totalCreditSales, 0.0001)

        // 2. Independent statement rows in Analysis Center
        val vm = AnalysisCenterViewModel()
        val rowsForCustomerA = vm.computeStatementRows(
            allTransactions = allTransactions,
            selectedCustomer = customerA,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )
        val rowsForCustomerB = vm.computeStatementRows(
            allTransactions = allTransactions,
            selectedCustomer = customerB,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )

        assertEquals(1, rowsForCustomerA.size)
        assertEquals("tx_a_1", rowsForCustomerA[0].id)
        assertEquals(500.0, rowsForCustomerA[0].runningBalance, 0.0001)

        assertEquals(1, rowsForCustomerB.size)
        assertEquals("tx_b_1", rowsForCustomerB[0].id)
        assertEquals(150.0, rowsForCustomerB[0].runningBalance, 0.0001)

        // 3. Independent Debt Aging
        val today = LocalDate.of(2026, 9, 20)
        val agingA = DebtAgingUtils.calculateCustomerAging(customerA, allTransactions, today = today)
        val agingB = DebtAgingUtils.calculateCustomerAging(customerB, allTransactions, today = today)

        assertEquals(customerA.id, agingA.customerId)
        assertEquals(500.0, agingA.currentDebt, 0.0001)
        assertEquals(1, agingA.individualTransactions.size)
        assertEquals("tx_a_1", agingA.individualTransactions[0].transactionId)

        assertEquals(customerB.id, agingB.customerId)
        assertEquals(150.0, agingB.currentDebt, 0.0001)
        assertEquals(1, agingB.individualTransactions.size)
        assertEquals("tx_b_1", agingB.individualTransactions[0].transactionId)
    }

    @Test
    fun transactionBelongingToCustomerA_isNeverDisplayedInCustomerBsAccount_merelyBecauseNamesMatch() {
        val txCustA = TransactionItem(
            id = "tx_private_a",
            title = "شراء آجل خاص",
            customerName = sharedName,
            activityType = "شراء آجل",
            amount = 1200.0,
            isCredit = true,
            date = "2026-09-10",
            relativeTime = "اليوم",
            customerId = customerA.id
        )

        val transactions = listOf(txCustA)

        val vm = AnalysisCenterViewModel()
        val rowsForB = vm.computeStatementRows(
            allTransactions = transactions,
            selectedCustomer = customerB,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )

        // Customer B must have 0 transactions displayed even though names match exactly
        assertTrue("Customer B's statement must NOT contain Customer A's transaction", rowsForB.isEmpty())

        val balanceB = CustomerLedgerCalculator.calculateCustomerBalance(customerB.id, transactions)
        assertEquals(0.0, balanceB.balance, 0.0001)
        assertEquals(0.0, balanceB.totalCreditSales, 0.0001)

        val agingB = DebtAgingUtils.calculateCustomerAging(customerB, transactions)
        assertTrue("Customer B's aging details must not include Customer A's transactions", agingB.individualTransactions.isEmpty())
    }

    @Test
    fun customerIdNull_isNeverSilentlyAssignedToCustomerByName() {
        val txUnlinked = TransactionItem(
            id = "tx_legacy_or_unlinked",
            title = "معاملة بدون معرف عميل",
            customerName = sharedName,
            activityType = "شراء آجل",
            amount = 300.0,
            isCredit = true,
            date = "2026-09-12",
            relativeTime = "اليوم",
            customerId = null
        )

        // 1. resolveCustomerForTransaction must return null when customerId is null
        val resolved = MainViewModel.resolveCustomerForTransaction(allCustomers, txUnlinked)
        assertNull("Transaction with customerId=null must resolve to null, never guessing by name", resolved)

        // Even with an unambiguous unique customer name in the list:
        val singleUniqueCust = CustomerAccount(
            id = "cust_unique_999",
            customerName = "عميل فريد تماما",
            balance = 300.0,
            totalDebt = 300.0,
            phone = "0599333333"
        )
        val txUniqueNameNoId = txUnlinked.copy(customerName = singleUniqueCust.customerName, customerId = null)
        val resolvedUnique = MainViewModel.resolveCustomerForTransaction(listOf(singleUniqueCust), txUniqueNameNoId)
        assertNull("Transaction with customerId=null must never be assigned to a customer even if name is unique", resolvedUnique)

        // 2. Unlinked transaction must not affect Customer A or Customer B's calculated balance
        val balanceA = CustomerLedgerCalculator.calculateCustomerBalance(customerA.id, listOf(txUnlinked))
        val balanceB = CustomerLedgerCalculator.calculateCustomerBalance(customerB.id, listOf(txUnlinked))
        assertEquals(0.0, balanceA.balance, 0.0001)
        assertEquals(0.0, balanceB.balance, 0.0001)

        // 3. Statement rows must exclude unlinked transactions
        val vm = AnalysisCenterViewModel()
        val rowsA = vm.computeStatementRows(
            allTransactions = listOf(txUnlinked),
            selectedCustomer = customerA,
            filter = StatementTxFilter.ALL,
            period = PeriodFilter.ALL
        )
        assertTrue("Statement rows must not include transactions where customerId is null", rowsA.isEmpty())

        // 4. Debt aging must exclude unlinked transactions
        val agingA = DebtAgingUtils.calculateCustomerAging(customerA, listOf(txUnlinked))
        assertTrue("Aging must not recover identity from customerName when customerId is null", agingA.individualTransactions.isEmpty())
    }

    @Test
    fun resolveCustomerForTransaction_resolvesStrictlyByCustomerId() {
        val txForA = TransactionItem(
            id = "tx_match_a",
            title = "شراء",
            customerName = "اسم قديم مختلف", // Even if name changed or differs
            activityType = "شراء آجل",
            amount = 100.0,
            isCredit = true,
            date = "2026-09-18",
            relativeTime = "اليوم",
            customerId = customerA.id
        )

        val resolved = MainViewModel.resolveCustomerForTransaction(allCustomers, txForA)
        assertEquals(customerA.id, resolved?.id)
        assertEquals(customerA.phone, resolved?.phone)
    }
}
