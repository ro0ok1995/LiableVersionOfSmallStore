package com.example.accounting

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.CustomerEntity
import com.example.data.db.SmallStoreDatabase
import com.example.data.db.TransactionItemLineEntity
import com.example.data.repository.StoreRepository
import com.example.model.CustomerAccount
import com.example.model.SettlementType
import com.example.model.TransactionItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2.4 Accounting Invariant Test Suite:
 *
 * ACCOUNTING GOLDEN RULE:
 * FINANCIAL RECORDS ARE NEVER EDITED, ARCHIVED, OR DELETED TO CANCEL THEIR ACCOUNTING EFFECT.
 *
 * Verifies that:
 * 1. Financial transactions cannot be physically deleted through the production repository path.
 * 2. Archiving does NOT remove the transaction from the database.
 * 3. Archiving does NOT neutralize the transaction's accounting effect on customer balances and ledgers.
 * 4. Line items associated with transactions remain physically intact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TransactionPhysicalImmutabilityTest {

    private lateinit var database: SmallStoreDatabase
    private lateinit var repository: StoreRepository

    private val testCustomerId = "cust_immutability_101"
    private val testCustomerName = "سالم الدوسري"

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = StoreRepository.createForTesting(database)

        // Seed base test customer
        val customerEntity = CustomerEntity(
            id = testCustomerId,
            customerName = testCustomerName,
            balance = 0.0,
            totalDebt = 0.0,
            phone = "0559988776",
            lastTransactionDate = "2026-09-20",
            hasRecentActivity = true
        )
        database.customerDao().insertCustomer(customerEntity)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun financialTransaction_cannotBePhysicallyDeleted_throughRepositoryPath() = runBlocking {
        val txId = "tx_immutable_001"
        val transaction = TransactionItem(
            id = txId,
            customerName = testCustomerName,
            activityType = "شراء آجل",
            amount = 350.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "اليوم",
            isArchived = false,
            customerId = testCustomerId
        )

        // 1. Add transaction to the repository
        repository.addTransaction(transaction)

        // Verify transaction exists in allTransactions and direct query
        val transactionsBefore = repository.allTransactions.first()
        assertEquals(1, transactionsBefore.size)
        assertEquals(txId, transactionsBefore[0].id)
        assertNotNull(transactionsBefore.find { it.id == txId })
        assertNotNull(database.transactionDao().getTransactionById(txId))

        // 2. Attempt permanent physical deletion via repository production path
        val deleteResult = repository.deleteTransactionPermanently(txId)

        // Verify deletion was refused
        assertFalse("deleteTransactionPermanently must return false and refuse deletion", deleteResult)

        // 3. Verify transaction STILL physically exists in the database
        val transactionsAfter = repository.allTransactions.first()
        assertEquals("Transaction must still exist in allTransactions", 1, transactionsAfter.size)
        assertEquals(txId, transactionsAfter[0].id)

        val directQuery = database.transactionDao().getTransactionById(txId)
        assertNotNull("Direct DAO query must prove transaction is still physically present in Room", directQuery)
        assertEquals(txId, directQuery?.id)

        // 4. Verify customer balance still reflects the transaction (350.0)
        val customer = repository.customers.first().first { it.id == testCustomerId }
        assertEquals(350.0, customer.balance, 0.0001)
    }

    @Test
    fun transactionLineItems_arePhysicallyPreserved_whenDeletionAttempted() = runBlocking {
        val txId = "tx_immutable_with_lines_002"
        val transaction = TransactionItem(
            id = txId,
            customerName = testCustomerName,
            activityType = "بيع نقدي",
            amount = 120.0,
            isCredit = false,
            date = "2026-09-20",
            relativeTime = "اليوم",
            isArchived = false,
            customerId = testCustomerId
        )
        val lines = listOf(
            TransactionItemLineEntity(
                transactionId = txId,
                productId = "prod_1",
                productNameSnapshot = "سكر الأسرة",
                quantity = 2,
                unitPrice = 20.0,
                subtotal = 40.0
            ),
            TransactionItemLineEntity(
                transactionId = txId,
                productId = "prod_2",
                productNameSnapshot = "أرز الشعلان",
                quantity = 1,
                unitPrice = 80.0,
                subtotal = 80.0
            )
        )

        // Add transaction and item lines
        repository.addTransaction(transaction, lines)

        // Verify lines exist in database
        val linesBefore = database.transactionItemLineDao().getLinesForTransaction(txId).first()
        assertEquals(2, linesBefore.size)

        // Attempt deletion
        repository.deleteTransactionPermanently(txId)

        // Verify lines are physically preserved
        val linesAfter = database.transactionItemLineDao().getLinesForTransaction(txId).first()
        assertEquals("Transaction item lines must remain physically preserved", 2, linesAfter.size)
    }

    @Test
    fun archiving_doesNotRemoveTransaction_andPreservesAccountingEffect() = runBlocking {
        val txId = "tx_archive_test_003"
        val transaction = TransactionItem(
            id = txId,
            customerName = testCustomerName,
            activityType = "شراء آجل",
            amount = 500.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "اليوم",
            isArchived = false,
            customerId = testCustomerId
        )

        repository.addTransaction(transaction)

        // Initial balance before archiving
        val initialCustomer = repository.customers.first().first { it.id == testCustomerId }
        assertEquals(500.0, initialCustomer.balance, 0.0001)

        // Archive the transaction (visibility/lifecycle operation only)
        repository.archiveTransaction(txId, "2026-09-20T10:00:00")

        // 1. Verify transaction was NOT removed from database
        val allTx = repository.allTransactions.first()
        assertEquals(1, allTx.size)
        assertTrue("Transaction must have isArchived = true", allTx[0].isArchived)
        assertEquals("2026-09-20T10:00:00", allTx[0].archivedDate)

        // 2. Verify transaction is still returned by direct query
        val directQuery = database.transactionDao().getTransactionById(txId)
        assertNotNull(directQuery)
        assertEquals(true, directQuery?.isArchived)

        // 3. Accounting Golden Rule: Archiving does NOT neutralize the accounting effect!
        // The customer balance must continue to be 500.0
        val customerAfterArchive = repository.customers.first().first { it.id == testCustomerId }
        assertEquals(
            "Archiving must NOT neutralize accounting balance",
            500.0,
            customerAfterArchive.balance,
            0.0001
        )
    }

    @Test
    fun archivedPayment_continuesToReduceCustomerBalance() = runBlocking {
        // 1. Credit sale of 600.0
        val saleTx = TransactionItem(
            id = "tx_sale_004",
            customerName = testCustomerName,
            activityType = "شراء آجل",
            amount = 600.0,
            isCredit = true,
            date = "2026-09-18",
            relativeTime = "منذ يومين",
            isArchived = false,
            customerId = testCustomerId
        )
        // 2. Payment of 200.0
        val paymentTx = TransactionItem(
            id = "tx_payment_005",
            customerName = testCustomerName,
            activityType = "تسديد دفعة",
            amount = 200.0,
            isCredit = false,
            settlementType = SettlementType.PARTIAL,
            date = "2026-09-19",
            relativeTime = "أمس",
            isArchived = false,
            customerId = testCustomerId
        )

        repository.addTransaction(saleTx)
        repository.addTransaction(paymentTx)

        // Balance before archiving payment: 600 - 200 = 400
        val balanceBefore = repository.customers.first().first { it.id == testCustomerId }.balance
        assertEquals(400.0, balanceBefore, 0.0001)

        // Archive the payment
        repository.archiveTransaction(paymentTx.id, "2026-09-20")

        // 1. Payment still physically exists in the database
        val allTx = repository.allTransactions.first()
        assertEquals(2, allTx.size)
        val archivedPayment = allTx.first { it.id == paymentTx.id }
        assertTrue(archivedPayment.isArchived)

        // 2. Balance must still be 400.0 because archiving the payment does not cancel it
        val balanceAfter = repository.customers.first().first { it.id == testCustomerId }.balance
        assertEquals(
            "Archiving payment must not undo the credit reduction",
            400.0,
            balanceAfter,
            0.0001
        )
    }

    @Test
    fun unarchivingTransaction_restoresVisibility_withoutAlteringLedger() = runBlocking {
        val txId = "tx_unarchive_006"
        val transaction = TransactionItem(
            id = txId,
            customerName = testCustomerName,
            activityType = "شراء آجل",
            amount = 175.0,
            isCredit = true,
            date = "2026-09-20",
            relativeTime = "اليوم",
            isArchived = true,
            archivedDate = "2026-09-20T08:00:00",
            customerId = testCustomerId
        )
        repository.addTransaction(transaction)

        // Balance is 175.0
        val balanceArchived = repository.customers.first().first { it.id == testCustomerId }.balance
        assertEquals(175.0, balanceArchived, 0.0001)

        // Unarchive
        repository.restoreTransaction(txId)

        // Verify isArchived is now false
        val restoredTx = repository.allTransactions.first().first { it.id == txId }
        assertFalse(restoredTx.isArchived)

        // Balance is STILL 175.0 (immutability preserved)
        val balanceRestored = repository.customers.first().first { it.id == testCustomerId }.balance
        assertEquals(175.0, balanceRestored, 0.0001)
    }
}
