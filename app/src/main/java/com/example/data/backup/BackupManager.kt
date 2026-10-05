package com.example.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.data.db.Adjustment
import com.example.data.db.CustomerEntity
import com.example.data.db.CustomerIdentityConflictEntity
import com.example.data.db.CustomerPayment
import com.example.data.db.Expense
import com.example.data.db.ExpenseCategory
import com.example.data.db.FinancialAccount
import com.example.data.db.OpeningBalance
import com.example.data.db.PaymentMethod
import com.example.data.db.Purchase
import com.example.data.db.PurchaseLine
import com.example.data.db.PurchaseReturn
import com.example.data.db.Refund
import com.example.data.db.Reversal
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.SaleReturn
import com.example.data.db.SaleReturnLine
import com.example.data.db.StockMovementEntity
import com.example.data.db.Supplier
import com.example.data.db.SupplierPayment
import com.example.data.db.TransactionItemLineEntity
import com.example.model.CustomerAccount
import com.example.model.NotificationItem
import com.example.model.ProductItem
import com.example.model.SettlementType
import com.example.model.StoreInfo
import com.example.model.TransactionItem
import com.example.util.ProductImageHelper
import org.json.JSONArray
import org.json.JSONObject
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class BackupPayload(
    val version: Int = 2,
    val backupTimestamp: Long = System.currentTimeMillis(),
    val storeInfoAtBackupTime: StoreInfo,
    val customers: List<CustomerAccount>,
    val products: List<ProductItem>,
    val transactions: List<TransactionItem>,
    val transactionItemLines: List<TransactionItemLineEntity> = emptyList(),
    val notifications: List<NotificationItem>,
    // Version 2 authoritative accounting snapshot. Legacy fields above remain for backward compatibility.
    val customerEntities: List<CustomerEntity> = emptyList(),
    val customerIdentityConflicts: List<CustomerIdentityConflictEntity> = emptyList(),
    val sales: List<Sale> = emptyList(),
    val saleLines: List<SaleLine> = emptyList(),
    val financialAccounts: List<FinancialAccount> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val customerPayments: List<CustomerPayment> = emptyList(),
    val openingBalances: List<OpeningBalance> = emptyList(),
    val adjustments: List<Adjustment> = emptyList(),
    val reversals: List<Reversal> = emptyList(),
    val saleReturns: List<SaleReturn> = emptyList(),
    val saleReturnLines: List<SaleReturnLine> = emptyList(),
    val refunds: List<Refund> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val purchases: List<Purchase> = emptyList(),
    val purchaseLines: List<PurchaseLine> = emptyList(),
    val supplierPayments: List<SupplierPayment> = emptyList(),
    val purchaseReturns: List<PurchaseReturn> = emptyList(),
    val expenseCategories: List<ExpenseCategory> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val stockMovements: List<StockMovementEntity> = emptyList()
)

object BackupManager {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun <T> encodeList(value: List<T>, elementClass: Class<T>): JSONArray {
        val type = Types.newParameterizedType(List::class.java, elementClass)
        val adapter = moshi.adapter<List<T>>(type)
        return JSONArray(adapter.toJson(value))
    }

    private fun <T> decodeList(root: JSONObject, key: String, elementClass: Class<T>): List<T> {
        val array = root.optJSONArray(key) ?: return emptyList()
        val type = Types.newParameterizedType(List::class.java, elementClass)
        return runCatching {
            moshi.adapter<List<T>>(type).fromJson(array.toString()) ?: emptyList()
        }.getOrDefault(emptyList())
    }


    fun serialize(payload: BackupPayload): String {
        val root = JSONObject()
        root.put("version", payload.version)
        root.put("backupTimestamp", payload.backupTimestamp)

        // storeInfoAtBackupTime
        val storeObj = JSONObject().apply {
            put("storeName", payload.storeInfoAtBackupTime.storeName)
            put("ownerName", payload.storeInfoAtBackupTime.ownerName)
            put("phone", payload.storeInfoAtBackupTime.phone)
            put("address", payload.storeInfoAtBackupTime.address)
            put("taxNumber", payload.storeInfoAtBackupTime.taxNumber)
            put("crNumber", payload.storeInfoAtBackupTime.crNumber)
        }
        root.put("storeInfoAtBackupTime", storeObj)

        // customers
        val custArray = JSONArray()
        for (c in payload.customers) {
            val obj = JSONObject().apply {
                put("id", c.id)
                put("customerName", c.customerName)
                put("balance", c.balance)
                put("totalDebt", c.totalDebt)
                put("phone", c.phone)
                put("lastTransactionDate", c.lastTransactionDate)
                put("hasRecentActivity", c.hasRecentActivity)
                put("isArchived", c.isArchived)
                if (c.archivedDate != null) {
                    put("archivedDate", c.archivedDate)
                }
            }
            custArray.put(obj)
        }
        root.put("customers", custArray)

        // products
        val prodArray = JSONArray()
        for (p in payload.products) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("price", p.price)
                put("costPrice", p.costPrice)
                put("category", p.category)
                put("unit", p.unit)
                put("isArchived", p.isArchived)
                if (p.archivedDate != null) {
                    put("archivedDate", p.archivedDate)
                }
                if (!p.imageUri.isNullOrBlank()) {
                    put("imageUri", p.imageUri)
                    val base64 = ProductImageHelper.encodeImageToBase64(p.imageUri)
                    if (base64 != null) {
                        put("imageBase64", base64)
                    }
                }
            }
            prodArray.put(obj)
        }
        root.put("products", prodArray)

        // transactions
        val txArray = JSONArray()
        for (t in payload.transactions) {
            val obj = JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("customerNameSnapshot", t.customerNameSnapshot)
                put("customerName", t.customerNameSnapshot) // Backward compatibility
                put("activityType", t.activityType)
                put("amount", t.amount)
                put("isCredit", t.isCredit)
                put("date", t.date)
                put("relativeTime", t.relativeTime)
                put("notes", t.notes)
                put("isArchived", t.isArchived)
                if (t.archivedDate != null) {
                    put("archivedDate", t.archivedDate)
                }
                if (t.settlementType != null) {
                    put("settlementType", t.settlementType.name)
                }
                if (t.customerId != null) {
                    put("customerId", t.customerId)
                }
                put("paidAmount", t.paidAmount)
                put("creditAmount", t.creditAmount)
                if (t.transactionType != null) {
                    put("transactionType", t.transactionType.name)
                }
                if (t.saleType != null) {
                    put("saleType", t.saleType.name)
                }
                if (t.paymentStatus != null) {
                    put("paymentStatus", t.paymentStatus.name)
                }
            }
            txArray.put(obj)
        }
        root.put("transactions", txArray)

        // transactionItemLines
        val linesArray = JSONArray()
        for (l in payload.transactionItemLines) {
            val obj = JSONObject().apply {
                put("id", l.id)
                put("transactionId", l.transactionId)
                put("productId", l.productId ?: JSONObject.NULL)
                put("productNameSnapshot", l.productNameSnapshot)
                put("quantity", l.quantity)
                put("unitPrice", l.unitPrice)
                put("costPrice", l.costPrice)
                put("subtotal", l.subtotal)
            }
            linesArray.put(obj)
        }
        root.put("transactionItemLines", linesArray)

        // notifications
        val notifArray = JSONArray()
        for (n in payload.notifications) {
            val obj = JSONObject().apply {
                put("id", n.id)
                put("customerName", n.customerName)
                put("transactionType", n.transactionType)
                put("amount", n.amount)
                put("timestamp", n.timestamp)
                put("isPayment", n.isPayment)
                put("isRead", n.isRead)
                put("transactionId", n.transactionId ?: JSONObject.NULL)
            }
            notifArray.put(obj)
        }
        root.put("notifications", notifArray)

        // Version 2: preserve the complete authoritative accounting state.
        val modern = JSONObject().apply {
            put("customerEntities", encodeList(payload.customerEntities, CustomerEntity::class.java))
            put("customerIdentityConflicts", encodeList(payload.customerIdentityConflicts, CustomerIdentityConflictEntity::class.java))
            put("sales", encodeList(payload.sales, Sale::class.java))
            put("saleLines", encodeList(payload.saleLines, SaleLine::class.java))
            put("financialAccounts", encodeList(payload.financialAccounts, FinancialAccount::class.java))
            put("paymentMethods", encodeList(payload.paymentMethods, PaymentMethod::class.java))
            put("customerPayments", encodeList(payload.customerPayments, CustomerPayment::class.java))
            put("openingBalances", encodeList(payload.openingBalances, OpeningBalance::class.java))
            put("adjustments", encodeList(payload.adjustments, Adjustment::class.java))
            put("reversals", encodeList(payload.reversals, Reversal::class.java))
            put("saleReturns", encodeList(payload.saleReturns, SaleReturn::class.java))
            put("saleReturnLines", encodeList(payload.saleReturnLines, SaleReturnLine::class.java))
            put("refunds", encodeList(payload.refunds, Refund::class.java))
            put("suppliers", encodeList(payload.suppliers, Supplier::class.java))
            put("purchases", encodeList(payload.purchases, Purchase::class.java))
            put("purchaseLines", encodeList(payload.purchaseLines, PurchaseLine::class.java))
            put("supplierPayments", encodeList(payload.supplierPayments, SupplierPayment::class.java))
            put("purchaseReturns", encodeList(payload.purchaseReturns, PurchaseReturn::class.java))
            put("expenseCategories", encodeList(payload.expenseCategories, ExpenseCategory::class.java))
            put("expenses", encodeList(payload.expenses, Expense::class.java))
            put("stockMovements", encodeList(payload.stockMovements, StockMovementEntity::class.java))
        }
        root.put("modernAccounting", modern)

        return root.toString(2)
    }

    fun deserialize(jsonString: String, context: Context? = null): BackupPayload {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", 1)
        val timestamp = root.optLong("backupTimestamp", System.currentTimeMillis())

        val storeObj = root.optJSONObject("storeInfoAtBackupTime")
        val storeInfo = StoreInfo(
            storeName = storeObj?.optString("storeName", "") ?: "",
            ownerName = storeObj?.optString("ownerName", "") ?: "",
            phone = storeObj?.optString("phone", "") ?: "",
            address = storeObj?.optString("address", "") ?: "",
            taxNumber = storeObj?.optString("taxNumber", "") ?: "",
            crNumber = storeObj?.optString("crNumber", "") ?: ""
        )

        val customers = mutableListOf<CustomerAccount>()
        val custArr = root.optJSONArray("customers") ?: JSONArray()
        for (i in 0 until custArr.length()) {
            val obj = custArr.getJSONObject(i)
            val archDate = if (obj.has("archivedDate") && !obj.isNull("archivedDate")) obj.getString("archivedDate") else null
            customers.add(
                CustomerAccount(
                    id = obj.getString("id"),
                    customerName = obj.getString("customerName"),
                    balance = obj.optDouble("balance", 0.0),
                    totalDebt = obj.optDouble("totalDebt", 0.0),
                    phone = obj.optString("phone", ""),
                    lastTransactionDate = obj.optString("lastTransactionDate", "2026-09-05"),
                    hasRecentActivity = obj.optBoolean("hasRecentActivity", false),
                    isArchived = obj.optBoolean("isArchived", false),
                    archivedDate = archDate
                )
            )
        }

        val products = mutableListOf<ProductItem>()
        val prodArr = root.optJSONArray("products") ?: JSONArray()
        for (i in 0 until prodArr.length()) {
            val obj = prodArr.getJSONObject(i)
            val prodId = obj.getString("id")
            val archDate = if (obj.has("archivedDate") && !obj.isNull("archivedDate")) obj.getString("archivedDate") else null
            var imageUri = if (obj.has("imageUri") && !obj.isNull("imageUri")) obj.getString("imageUri") else null
            val imageBase64 = if (obj.has("imageBase64") && !obj.isNull("imageBase64")) obj.getString("imageBase64") else null
            if (!imageBase64.isNullOrBlank() && context != null) {
                val restored = ProductImageHelper.saveBase64ToImageFile(context, imageBase64, prodId)
                if (restored != null) {
                    imageUri = restored
                }
            }
            products.add(
                ProductItem(
                    id = prodId,
                    name = obj.getString("name"),
                    price = obj.optDouble("price", 0.0),
                    category = obj.optString("category", "عام"),
                    unit = obj.optString("unit", "حبة"),
                    costPrice = obj.optDouble("costPrice", 0.0),
                    imageUri = imageUri,
                    isArchived = obj.optBoolean("isArchived", false),
                    archivedDate = archDate
                )
            )
        }

        val transactions = mutableListOf<TransactionItem>()
        val txArr = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txArr.length()) {
            val obj = txArr.getJSONObject(i)
            val archDate = if (obj.has("archivedDate") && !obj.isNull("archivedDate")) obj.getString("archivedDate") else null
            val stStr = if (obj.has("settlementType") && !obj.isNull("settlementType")) obj.getString("settlementType") else null
            val settlementType = when (stStr) {
                "FULL" -> SettlementType.FULL
                "PARTIAL" -> SettlementType.PARTIAL
                else -> null
            }
            val snapshot = if (obj.has("customerNameSnapshot") && !obj.isNull("customerNameSnapshot")) {
                obj.getString("customerNameSnapshot")
            } else {
                obj.optString("customerName", "")
            }
            val cid = if (obj.has("customerId") && !obj.isNull("customerId")) obj.getString("customerId") else null
            val paidAmt = obj.optDouble("paidAmount", 0.0)
            val creditAmt = obj.optDouble("creditAmount", 0.0)
            val txTypeStr = if (obj.has("transactionType") && !obj.isNull("transactionType")) obj.getString("transactionType") else null
            val txType = txTypeStr?.let { runCatching { com.example.model.TransactionType.valueOf(it) }.getOrNull() }
            val sTypeStr = if (obj.has("saleType") && !obj.isNull("saleType")) obj.getString("saleType") else null
            val sType = sTypeStr?.let { runCatching { com.example.model.SaleType.valueOf(it) }.getOrNull() }
            val pStatusStr = if (obj.has("paymentStatus") && !obj.isNull("paymentStatus")) obj.getString("paymentStatus") else null
            val pStatus = pStatusStr?.let { runCatching { com.example.model.PaymentStatus.valueOf(it) }.getOrNull() }

            transactions.add(
                TransactionItem(
                    id = obj.getString("id"),
                    title = obj.optString("title", ""),
                    customerNameSnapshot = snapshot,
                    activityType = obj.optString("activityType", ""),
                    amount = obj.optDouble("amount", 0.0),
                    isCredit = obj.optBoolean("isCredit", false),
                    date = obj.optString("date", ""),
                    relativeTime = obj.optString("relativeTime", ""),
                    notes = obj.optString("notes", ""),
                    settlementType = settlementType,
                    customerId = cid,
                    isArchived = obj.optBoolean("isArchived", false),
                    archivedDate = archDate,
                    customerName = snapshot,
                    transactionType = txType,
                    saleType = sType,
                    paymentStatus = pStatus,
                    paidAmount = paidAmt,
                    creditAmount = creditAmt
                )
            )
        }

        val lines = mutableListOf<TransactionItemLineEntity>()
        val linesArr = root.optJSONArray("transactionItemLines") ?: JSONArray()
        for (i in 0 until linesArr.length()) {
            val obj = linesArr.getJSONObject(i)
            val pId = if (obj.has("productId") && !obj.isNull("productId")) obj.getString("productId") else null
            lines.add(
                TransactionItemLineEntity(
                    id = obj.optLong("id", 0L),
                    transactionId = obj.getString("transactionId"),
                    productId = pId,
                    productNameSnapshot = obj.optString("productNameSnapshot", ""),
                    quantity = obj.optInt("quantity", 1),
                    unitPrice = obj.optDouble("unitPrice", 0.0),
                    costPrice = obj.optDouble("costPrice", 0.0),
                    subtotal = obj.optDouble("subtotal", 0.0)
                )
            )
        }

        val notifications = mutableListOf<NotificationItem>()
        val notifArr = root.optJSONArray("notifications") ?: JSONArray()
        for (i in 0 until notifArr.length()) {
            val obj = notifArr.getJSONObject(i)
            val txId = if (obj.has("transactionId") && !obj.isNull("transactionId")) obj.getString("transactionId") else null
            notifications.add(
                NotificationItem(
                    id = obj.getString("id"),
                    customerName = obj.getString("customerName"),
                    transactionType = obj.optString("transactionType", ""),
                    amount = obj.optDouble("amount", 0.0),
                    timestamp = obj.optString("timestamp", ""),
                    isPayment = obj.optBoolean("isPayment", false),
                    isRead = obj.optBoolean("isRead", false),
                    transactionId = txId
                )
            )
        }

        val modern = root.optJSONObject("modernAccounting")
        val modernCustomerEntities = modern?.let { decodeList(it, "customerEntities", CustomerEntity::class.java) } ?: emptyList()
        val modernConflicts = modern?.let { decodeList(it, "customerIdentityConflicts", CustomerIdentityConflictEntity::class.java) } ?: emptyList()
        val modernSales = modern?.let { decodeList(it, "sales", Sale::class.java) } ?: emptyList()
        val modernSaleLines = modern?.let { decodeList(it, "saleLines", SaleLine::class.java) } ?: emptyList()
        val modernAccounts = modern?.let { decodeList(it, "financialAccounts", FinancialAccount::class.java) } ?: emptyList()
        val modernPaymentMethods = modern?.let { decodeList(it, "paymentMethods", PaymentMethod::class.java) } ?: emptyList()
        val modernCustomerPayments = modern?.let { decodeList(it, "customerPayments", CustomerPayment::class.java) } ?: emptyList()
        val modernOpeningBalances = modern?.let { decodeList(it, "openingBalances", OpeningBalance::class.java) } ?: emptyList()
        val modernAdjustments = modern?.let { decodeList(it, "adjustments", Adjustment::class.java) } ?: emptyList()
        val modernReversals = modern?.let { decodeList(it, "reversals", Reversal::class.java) } ?: emptyList()
        val modernSaleReturns = modern?.let { decodeList(it, "saleReturns", SaleReturn::class.java) } ?: emptyList()
        val modernSaleReturnLines = modern?.let { decodeList(it, "saleReturnLines", SaleReturnLine::class.java) } ?: emptyList()
        val modernRefunds = modern?.let { decodeList(it, "refunds", Refund::class.java) } ?: emptyList()
        val modernSuppliers = modern?.let { decodeList(it, "suppliers", Supplier::class.java) } ?: emptyList()
        val modernPurchases = modern?.let { decodeList(it, "purchases", Purchase::class.java) } ?: emptyList()
        val modernPurchaseLines = modern?.let { decodeList(it, "purchaseLines", PurchaseLine::class.java) } ?: emptyList()
        val modernSupplierPayments = modern?.let { decodeList(it, "supplierPayments", SupplierPayment::class.java) } ?: emptyList()
        val modernPurchaseReturns = modern?.let { decodeList(it, "purchaseReturns", PurchaseReturn::class.java) } ?: emptyList()
        val modernExpenseCategories = modern?.let { decodeList(it, "expenseCategories", ExpenseCategory::class.java) } ?: emptyList()
        val modernExpenses = modern?.let { decodeList(it, "expenses", Expense::class.java) } ?: emptyList()
        val modernStockMovements = modern?.let { decodeList(it, "stockMovements", StockMovementEntity::class.java) } ?: emptyList()

        return BackupPayload(
            version = version,
            backupTimestamp = timestamp,
            storeInfoAtBackupTime = storeInfo,
            customers = customers,
            products = products,
            transactions = transactions,
            transactionItemLines = lines,
            notifications = notifications,
            customerEntities = modernCustomerEntities,
            customerIdentityConflicts = modernConflicts,
            sales = modernSales,
            saleLines = modernSaleLines,
            financialAccounts = modernAccounts,
            paymentMethods = modernPaymentMethods,
            customerPayments = modernCustomerPayments,
            openingBalances = modernOpeningBalances,
            adjustments = modernAdjustments,
            reversals = modernReversals,
            saleReturns = modernSaleReturns,
            saleReturnLines = modernSaleReturnLines,
            refunds = modernRefunds,
            suppliers = modernSuppliers,
            purchases = modernPurchases,
            purchaseLines = modernPurchaseLines,
            supplierPayments = modernSupplierPayments,
            purchaseReturns = modernPurchaseReturns,
            expenseCategories = modernExpenseCategories,
            expenses = modernExpenses,
            stockMovements = modernStockMovements
        )
    }

    fun writeToUri(contentResolver: ContentResolver, uri: Uri, jsonString: String): Boolean {
        return try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonString)
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun readFromUri(contentResolver: ContentResolver, uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
