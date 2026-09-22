package com.example.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class BackupPayload(
    val version: Int = 1,
    val backupTimestamp: Long = System.currentTimeMillis(),
    val storeInfoAtBackupTime: StoreInfo,
    val customers: List<CustomerAccount>,
    val products: List<ProductItem>,
    val transactions: List<TransactionItem>,
    val transactionItemLines: List<TransactionItemLineEntity> = emptyList(),
    val notifications: List<NotificationItem>
)

object BackupManager {

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

        return BackupPayload(
            version = version,
            backupTimestamp = timestamp,
            storeInfoAtBackupTime = storeInfo,
            customers = customers,
            products = products,
            transactions = transactions,
            transactionItemLines = lines,
            notifications = notifications
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
