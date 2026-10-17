package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.ConflictReason
import com.example.model.ConflictResolutionStatus

/**
 * Migration 4 -> 5:
 * Phase 2.5 Accounting Invariant:
 * When historical customer identity is ambiguous or unverified, NEVER guess.
 *
 * 1. Create table `customer_identity_conflicts` for tracking transactions that could
 *    not be deterministically assigned to a customer account.
 * 2. Create performance and query indices on `transactionId` and `resolutionStatus`.
 * 3. Inspect existing transactions where `customerId IS NULL`:
 *    - Anonymous cash sales (e.g. "عميل كاش", "عميل عام", "عميل نقدي", empty name with cash sale):
 *      Do NOT create conflicts (valid walk-in sales).
 *    - Ambiguous customer name (multiple customers match same name):
 *      Create persistent conflict with [ConflictReason.AMBIGUOUS_CUSTOMER_NAME].
 *    - Customer not found (non-cash transaction, customerName does not exist in customers):
 *      Create persistent conflict with [ConflictReason.CUSTOMER_NOT_FOUND].
 *    - Missing customer name (credit/debt transaction with empty customer name):
 *      Create persistent conflict with [ConflictReason.MISSING_CUSTOMER_NAME].
 *    - Other non-cash unlinked transactions:
 *      Create persistent conflict with [ConflictReason.OTHER_UNRESOLVED_IDENTITY].
 * 4. All conflicts remain in [ConflictResolutionStatus.UNRESOLVED] state awaiting explicit
 *    user-driven resolution.
 * 5. Transactions are NEVER automatically or silently assigned to any customer.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: Create table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS customer_identity_conflicts (
                id TEXT PRIMARY KEY NOT NULL,
                transactionId TEXT NOT NULL,
                originalCustomerName TEXT NOT NULL,
                conflictReason TEXT NOT NULL,
                createdAt TEXT NOT NULL,
                resolutionStatus TEXT NOT NULL,
                resolvedCustomerId TEXT DEFAULT NULL,
                resolvedAt TEXT DEFAULT NULL,
                notes TEXT DEFAULT NULL
            )
        """.trimIndent())

        // Step 2: Create indices
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_identity_conflicts_transactionId ON customer_identity_conflicts(transactionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_identity_conflicts_resolutionStatus ON customer_identity_conflicts(resolutionStatus)")

        // Step 3: Populate conflict records for existing unassigned transactions
        val cursor = db.query("""
            SELECT id, customerName, isCredit, activityType, date
            FROM transactions
            WHERE customerId IS NULL
        """.trimIndent())

        val insertStmt = db.compileStatement("""
            INSERT OR IGNORE INTO customer_identity_conflicts (
                id, transactionId, originalCustomerName, conflictReason, createdAt, resolutionStatus, resolvedCustomerId, resolvedAt, notes
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent())

        try {
            while (cursor.moveToNext()) {
                val txId = cursor.getString(0)
                val rawName = cursor.getString(1)
                val isCredit = cursor.getInt(2) != 0
                val activityType = cursor.getString(3) ?: ""
                val date = cursor.getString(4) ?: ""
                val customerName = rawName?.trim() ?: ""

                // Check if this is an anonymous cash sale
                val isAnonymousToken = isAnonymousCashName(customerName)
                val isCashActivity = isCashSaleActivity(activityType)

                // Anonymous cash sales do NOT create customer conflicts
                if (!isCredit && (isAnonymousToken || (customerName.isEmpty() && isCashActivity))) {
                    continue
                }

                // Determine precise conflict reason
                val conflictReason: ConflictReason
                val notes: String?

                if (customerName.isEmpty()) {
                    conflictReason = ConflictReason.MISSING_CUSTOMER_NAME
                    notes = "المعاملة تتضمن مديونية أو نشاط حساب ولكن اسم العميل مفقود تماماً"
                } else if (isAnonymousToken && isCredit) {
                    // Credit transaction marked with generic cash token - invalid ledger link
                    conflictReason = ConflictReason.OTHER_UNRESOLVED_IDENTITY
                    notes = "معاملة آجل مسجلة باسم عميل عام ($customerName) دون تحديد حساب العميل الفعلي"
                } else {
                    // Check customer registry for matches
                    val countCursor = db.query(
                        "SELECT COUNT(*) FROM customers WHERE customerName = ?",
                        arrayOf(customerName)
                    )
                    val matchCount = if (countCursor.moveToFirst()) countCursor.getInt(0) else 0
                    countCursor.close()

                    when {
                        matchCount > 1 -> {
                            conflictReason = ConflictReason.AMBIGUOUS_CUSTOMER_NAME
                            notes = "يوجد $matchCount عملاء يحملون نفس الاسم '$customerName'. لا يمكن التخمين تلقائياً ويجب التعيين اليدوي."
                        }
                        matchCount == 0 -> {
                            conflictReason = ConflictReason.CUSTOMER_NOT_FOUND
                            notes = "لا يوجد عميل مسجل بهذا الاسم '$customerName'. يتطلب إنشاء عميل أو ربط المعاملة بعميل موجود."
                        }
                        else -> {
                            conflictReason = ConflictReason.OTHER_UNRESOLVED_IDENTITY
                            notes = "لم يتم تحديد هوية العميل للمطابقة الدقيقة ($customerName)."
                        }
                    }
                }

                val conflictId = "conflict_${txId}"
                val createdAt = if (date.isNotBlank()) date else "2026-09-21T00:00:00"

                insertStmt.bindString(1, conflictId)
                insertStmt.bindString(2, txId)
                insertStmt.bindString(3, if (customerName.isNotEmpty()) customerName else "بدون اسم")
                insertStmt.bindString(4, conflictReason.name)
                insertStmt.bindString(5, createdAt)
                insertStmt.bindString(6, ConflictResolutionStatus.UNRESOLVED.name)
                insertStmt.bindNull(7) // resolvedCustomerId
                insertStmt.bindNull(8) // resolvedAt
                if (notes != null) {
                    insertStmt.bindString(9, notes)
                } else {
                    insertStmt.bindNull(9)
                }

                insertStmt.executeInsert()
            }
        } finally {
            cursor.close()
            insertStmt.close()
        }
    }

    private fun isAnonymousCashName(name: String): Boolean {
        if (name.isBlank()) return false
        val trimmed = name.trim()
        return when (trimmed) {
            "عميل كاش",
            "عميل عام",
            "عميل نقدي",
            "زبون كاش",
            "زبون نقدي",
            "زبون عام",
            "Cash Customer",
            "Cash",
            "كاش" -> true
            else -> false
        }
    }

    private fun isCashSaleActivity(activityType: String): Boolean {
        val trimmed = activityType.trim()
        return when (trimmed) {
            "شراء كاش",
            "شراء نقدي",
            "شراء نقدي (كاش)",
            "بيع نقدي",
            "كاش",
            "Cash",
            "Cash Sale",
            "SALE_CASH" -> true
            else -> false
        }
    }
}
