package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.CustomerAccount
import com.example.model.NotificationItem
import com.example.model.ProductItem
import com.example.model.SettlementType
import com.example.model.StoreInfo
import com.example.model.TransactionItem

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val customerName: String,
    @Deprecated("Legacy stored balance column. Source of truth is persistent Customer Ledger.")
    val balance: Double,
    @Deprecated("Legacy stored debt column. Source of truth is persistent Customer Ledger.")
    val totalDebt: Double,
    val phone: String,
    val lastTransactionDate: String,
    val hasRecentActivity: Boolean,
    val isArchived: Boolean = false,
    val archivedDate: String? = null
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val price: Double,
    val costPrice: Double = 0.0,
    val category: String,
    val unit: String,
    val imageUri: String? = null,
    val isArchived: Boolean = false,
    val archivedDate: String? = null
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("customerId"),
        Index(value = ["customerId", "transactionDate"])
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val title: String = "",
    val customerNameSnapshot: String, // ONLY historical display name captured at the time of transaction
    val activityType: String,
    val amount: Double,
    val isCredit: Boolean,
    val date: String,
    val relativeTime: String,
    val notes: String = "",
    val settlementType: String? = null,
    val customerId: String? = null, // Relational customer identity (Source of Truth)
    val isArchived: Boolean = false,
    val archivedDate: String? = null,
    @Deprecated(
        message = "Legacy customerName column preserved for backward compatibility. Use customerNameSnapshot for presentation and customerId for identity.",
        replaceWith = ReplaceWith("customerNameSnapshot")
    )
    val customerName: String = customerNameSnapshot,
    val transactionDate: String = date,
    val paidAmount: Double = 0.0,
    val creditAmount: Double = 0.0
) {
    @androidx.room.Ignore
    @Deprecated("Legacy constructor for backward compatibility. Use customerNameSnapshot.")
    constructor(
        id: String,
        title: String = "",
        activityType: String = "",
        amount: Double = 0.0,
        isCredit: Boolean = false,
        date: String = "",
        relativeTime: String = "",
        customerName: String = "",
        notes: String = "",
        settlementType: String? = null,
        customerId: String? = null,
        isArchived: Boolean = false,
        archivedDate: String? = null
    ) : this(
        id = id,
        title = title,
        customerNameSnapshot = customerName,
        activityType = activityType,
        amount = amount,
        isCredit = isCredit,
        date = date,
        relativeTime = relativeTime,
        notes = notes,
        settlementType = settlementType,
        customerId = customerId,
        isArchived = isArchived,
        archivedDate = archivedDate,
        customerName = customerName,
        transactionDate = date,
        paidAmount = 0.0,
        creditAmount = 0.0
    )

    @androidx.room.Ignore
    constructor(
        id: String,
        title: String = "",
        customerNameSnapshot: String = "",
        activityType: String = "",
        amount: Double = 0.0,
        isCredit: Boolean = false,
        date: String = "",
        relativeTime: String = "",
        notes: String = "",
        settlementType: String? = null,
        customerId: String? = null,
        isArchived: Boolean = false,
        archivedDate: String? = null,
        customerName: String = customerNameSnapshot,
        transactionDate: String = date
    ) : this(
        id = id,
        title = title,
        customerNameSnapshot = customerNameSnapshot,
        activityType = activityType,
        amount = amount,
        isCredit = isCredit,
        date = date,
        relativeTime = relativeTime,
        notes = notes,
        settlementType = settlementType,
        customerId = customerId,
        isArchived = isArchived,
        archivedDate = archivedDate,
        customerName = customerName,
        transactionDate = transactionDate,
        paidAmount = 0.0,
        creditAmount = 0.0
    )
}

@Entity(
    tableName = "transaction_item_lines",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("transactionId"),
        Index("productId")
    ]
)
data class TransactionItemLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: String,
    val productId: String? = null,
    val productNameSnapshot: String,
    val quantity: Int,
    val unitPrice: Double,
    val costPrice: Double = 0.0,
    val subtotal: Double
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val customerName: String,
    val transactionType: String,
    val amount: Double,
    val timestamp: String,
    val isPayment: Boolean,
    val isRead: Boolean = false,
    val transactionId: String? = null
)

@Entity(tableName = "store_info")
data class StoreInfoEntity(
    @PrimaryKey val id: Int = 1,
    val storeName: String,
    val ownerName: String,
    val phone: String,
    val address: String,
    val taxNumber: String,
    val crNumber: String,
    val isSaved: Boolean = false
)

// Extension converters between Room entities and domain models
fun CustomerEntity.toModel(): CustomerAccount = CustomerAccount(
    id = id,
    customerName = customerName,
    balance = balance,
    totalDebt = totalDebt,
    phone = phone,
    lastTransactionDate = lastTransactionDate,
    hasRecentActivity = hasRecentActivity,
    isArchived = isArchived,
    archivedDate = archivedDate
)

fun CustomerAccount.toEntity(): CustomerEntity = CustomerEntity(
    id = id,
    customerName = customerName,
    balance = balance,
    totalDebt = totalDebt,
    phone = phone,
    lastTransactionDate = lastTransactionDate,
    hasRecentActivity = hasRecentActivity,
    isArchived = isArchived,
    archivedDate = archivedDate
)

fun ProductEntity.toModel(): ProductItem = ProductItem(
    id = id,
    name = name,
    price = price,
    category = category,
    unit = unit,
    costPrice = costPrice,
    imageUri = imageUri,
    isArchived = isArchived,
    archivedDate = archivedDate
)

fun ProductItem.toEntity(): ProductEntity = ProductEntity(
    id = id,
    name = name,
    price = price,
    category = category,
    unit = unit,
    costPrice = costPrice,
    imageUri = imageUri,
    isArchived = isArchived,
    archivedDate = archivedDate
)

fun TransactionEntity.toModel(): TransactionItem {
    val snapshot = customerNameSnapshot.ifBlank { customerName }
    return TransactionItem(
        id = id,
        title = title,
        customerNameSnapshot = snapshot,
        activityType = activityType,
        amount = amount,
        isCredit = isCredit,
        date = date,
        relativeTime = relativeTime,
        notes = notes,
        settlementType = settlementType?.let { runCatching { SettlementType.valueOf(it) }.getOrNull() },
        customerId = customerId,
        isArchived = isArchived,
        archivedDate = archivedDate,
        customerName = snapshot,
        paidAmount = paidAmount,
        creditAmount = creditAmount
    )
}

fun TransactionItem.toEntity(): TransactionEntity {
    val snapshot = customerNameSnapshot.ifBlank { customerName }
    return TransactionEntity(
        id = id,
        title = title,
        customerNameSnapshot = snapshot,
        activityType = activityType,
        amount = amount,
        isCredit = isCredit,
        date = date,
        relativeTime = relativeTime,
        notes = notes,
        settlementType = settlementType?.name,
        customerId = customerId,
        isArchived = isArchived,
        archivedDate = archivedDate,
        customerName = snapshot,
        transactionDate = date,
        paidAmount = paidAmount,
        creditAmount = creditAmount
    )
}

fun NotificationEntity.toModel(): NotificationItem = NotificationItem(
    id = id,
    customerName = customerName,
    transactionType = transactionType,
    amount = amount,
    timestamp = timestamp,
    isPayment = isPayment,
    isRead = isRead,
    transactionId = transactionId
)

fun NotificationItem.toEntity(): NotificationEntity = NotificationEntity(
    id = id,
    customerName = customerName,
    transactionType = transactionType,
    amount = amount,
    timestamp = timestamp,
    isPayment = isPayment,
    isRead = isRead,
    transactionId = transactionId
)

fun StoreInfoEntity.toModel(): StoreInfo = StoreInfo(
    storeName = storeName,
    ownerName = ownerName,
    phone = phone,
    address = address,
    taxNumber = taxNumber,
    crNumber = crNumber
)

fun StoreInfo.toEntity(isSaved: Boolean = false): StoreInfoEntity = StoreInfoEntity(
    id = 1,
    storeName = storeName,
    ownerName = ownerName,
    phone = phone,
    address = address,
    taxNumber = taxNumber,
    crNumber = crNumber,
    isSaved = isSaved
)
