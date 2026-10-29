package com.example.data.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.FinancialAccount
import com.example.data.db.PaymentMethod
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.SmallStoreDatabase
import com.example.data.repository.PaymentMethodRepository
import com.example.data.repository.StoreBackupRepository
import com.example.model.CustomerAccount
import com.example.model.ProductItem
import com.example.model.StoreInfo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BackupV2SerializationTest {

    private lateinit var context: Context
    private lateinit var db: SmallStoreDatabase
    private lateinit var backupRepository: StoreBackupRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SmallStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backupRepository = StoreBackupRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun version2_preserves_authoritativeAccountingEntities() {
        val sale = Sale(
            id = "backup_sale_1",
            invoiceNumber = "INV-BACKUP-001",
            customerId = "cust_backup_1",
            saleType = "MIXED",
            totalAmount = 150.0,
            paidAmount = 60.0,
            creditAmount = 90.0,
            paymentStatus = "PARTIAL",
            transactionDate = "2026-10-05",
            financialAccountId = "acc_cash"
        )
        val saleLine = SaleLine(
            id = "backup_line_1",
            saleId = sale.id,
            productId = "prod_backup_1",
            productNameSnapshot = "Backup Item",
            quantity = 3,
            unitPrice = 50.0,
            costPriceAtSale = 20.0,
            subtotal = 150.0
        )
        val account = FinancialAccount(
            id = "acc_cash",
            name = "Cash",
            type = "CASH"
        )
        val paymentMethod = PaymentMethod(
            id = "pm_cash",
            name = "Cash",
            type = "CASH"
        )

        val payload = BackupPayload(
            storeInfoAtBackupTime = StoreInfo(storeName = "Backup Test"),
            customers = listOf(
                CustomerAccount(
                    id = "cust_backup_1",
                    customerName = "Backup Customer",
                    balance = 90.0,
                    totalDebt = 90.0,
                    phone = ""
                )
            ),
            products = listOf(
                ProductItem(
                    id = "prod_backup_1",
                    name = "Backup Item",
                    price = 50.0,
                    costPrice = 20.0,
                    category = "Test",
                    unit = "piece"
                )
            ),
            transactions = emptyList(),
            notifications = emptyList(),
            sales = listOf(sale),
            saleLines = listOf(saleLine),
            financialAccounts = listOf(account),
            paymentMethods = listOf(paymentMethod)
        )

        val json = BackupManager.serialize(payload)
        assertTrue(json.contains("\"modernAccounting\""))
        assertTrue(json.contains("INV-BACKUP-001"))

        val restored = BackupManager.deserialize(json)
        assertEquals(2, restored.version)
        assertEquals(1, restored.sales.size)
        assertEquals(150.0, restored.sales.first().totalAmount, 0.0001)
        assertEquals(60.0, restored.sales.first().paidAmount, 0.0001)
        assertEquals(90.0, restored.sales.first().creditAmount, 0.0001)
        assertEquals(1, restored.saleLines.size)
        assertEquals(20.0, restored.saleLines.first().costPriceAtSale, 0.0001)
        assertEquals(1, restored.financialAccounts.size)
        assertEquals("acc_cash", restored.financialAccounts.first().id)
        assertEquals(1, restored.paymentMethods.size)
        assertEquals("pm_cash", restored.paymentMethods.first().id)
    }

    @Test
    fun restore_ensuresCanonicalPaymentMethods_whenBackupHasEmptyPaymentMethods() = runBlocking {
        val payload = BackupPayload(
            storeInfoAtBackupTime = StoreInfo(storeName = "Restore Test"),
            customers = emptyList(),
            products = emptyList(),
            transactions = emptyList(),
            notifications = emptyList(),
            financialAccounts = listOf(
                FinancialAccount(id = "acc_cash", name = "نقدي كاش", type = "CASH"),
                FinancialAccount(id = "acc_bank_palestine", name = "بنك فلسطين", type = "BANK")
            ),
            paymentMethods = emptyList() // User backup without payment methods
        )

        backupRepository.restoreDataFromBackup(payload, replaceStoreInfo = true)

        val methods = db.paymentMethodDao().getAllPaymentMethodsSync().associateBy { it.id }
        assertTrue("pm_cash must exist after restore", methods.containsKey("pm_cash"))
        assertTrue("pm_bank_palestine must exist after restore", methods.containsKey("pm_bank_palestine"))
        assertEquals("CASH", methods["pm_cash"]?.type)
        assertEquals("BANK", methods["pm_bank_palestine"]?.type)

        // Financial accounts and payment methods must remain distinct
        val accounts = db.financialAccountDao().getAllAccountsSync().associateBy { it.id }
        assertTrue(accounts.containsKey("acc_cash"))
        assertTrue(accounts.containsKey("acc_bank_palestine"))
        assertTrue(!accounts.containsKey("pm_cash"))
        assertTrue(!methods.containsKey("acc_cash"))
    }

    @Test
    fun repeatedRestoreOrInitialization_doesNotCreateDuplicatePaymentMethods() = runBlocking {
        val payload = BackupPayload(
            storeInfoAtBackupTime = StoreInfo(storeName = "Duplicate Test"),
            customers = emptyList(),
            products = emptyList(),
            transactions = emptyList(),
            notifications = emptyList(),
            financialAccounts = listOf(
                FinancialAccount(id = "acc_cash", name = "نقدي كاش", type = "CASH")
            ),
            paymentMethods = listOf(
                PaymentMethod(id = "pm_cash", name = "نقدي كاش", type = "CASH")
            )
        )

        backupRepository.restoreDataFromBackup(payload, replaceStoreInfo = true)
        val countAfterFirst = db.paymentMethodDao().getPaymentMethodCount()

        // Repeated ensure calls
        PaymentMethodRepository.ensurePaymentMethods(db)
        PaymentMethodRepository.ensureDefaultConfiguration(db)
        val countAfterEnsures = db.paymentMethodDao().getPaymentMethodCount()

        // Second restore
        backupRepository.restoreDataFromBackup(payload, replaceStoreInfo = true)
        val countAfterSecondRestore = db.paymentMethodDao().getPaymentMethodCount()

        assertEquals(countAfterFirst, countAfterSecondRestore)
        val allMethods = db.paymentMethodDao().getAllPaymentMethodsSync()
        val uniqueIds = allMethods.map { it.id }.toSet()
        assertEquals("All payment method IDs must be unique", allMethods.size, uniqueIds.size)
    }
}
