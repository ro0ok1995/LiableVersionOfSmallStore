package com.example.data.backup

import com.example.data.db.FinancialAccount
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.model.CustomerAccount
import com.example.model.ProductItem
import com.example.model.StoreInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupV2SerializationTest {

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
            financialAccounts = listOf(account)
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
    }
}
