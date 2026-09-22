# SmallStore — Accounting & Database Baseline Audit Report

**Status:** Completed Baseline Audit  
**Date:** September 2026  
**Target Specification:** SmallStore — Complete Accounting & Database Refactor Plan  
**Scope:** Read-only inspection of Room schema, DAOs, Repository, ViewModels, UI Screens, Presentation Layers, Backup/Restore, and Test Suites.  
**Constraint Adherence:** No schema migrations applied, no database versions incremented, no code or UI rewritten in this step.

---

## Executive Summary

SmallStore is currently a functional, single-currency Android application built on Room (version 3) and Jetpack Compose. While the application provides a smooth user experience across POS, Customer Profiles, Analysis Center, and Backup/Restore, the underlying accounting engine relies heavily on **imperative state synchronization**, **String-based heuristic classification**, and a **denormalized customer association model** (`customerName` string matching rather than a persistent foreign key).

This audit documents the exact current state of the database, models, business logic, report generation, and backup serialization. It proves and validates all 12 key assumptions with line-level code citations, highlights latent risks and data integrity pitfalls, and establishes the phase-by-phase migration blueprint for the upcoming refactor.

---

## Section A: Current Database Schema & Version

The database is declared in `/app/src/main/java/com/example/data/db/SmallStoreDatabase.kt`:
- **Database Name:** `smallstore.db`
- **Room Schema Version:** `3`
- **Export Schema:** `false`
- **Registered Entities (6):**
  1. `CustomerEntity` (`customers`)
  2. `TransactionEntity` (`transactions`)
  3. `ProductEntity` (`products`)
  4. `NotificationEntity` (`notifications`)
  5. `StoreInfoEntity` (`store_info`)
  6. `TransactionItemLineEntity` (`transaction_item_lines`)

### Database Evolution History
- **Version 1:** Initial entities (`CustomerEntity`, `TransactionEntity`, `ProductEntity`, `NotificationEntity`, `StoreInfoEntity`).
- **Version 1 -> 2 Migration:** Added `TransactionItemLineEntity` (`transaction_item_lines`) with foreign key `CASCADE` referencing `transactions(id)`.
- **Version 2 -> 3 Migration:** Added `archivedDate` (`TEXT NULL`) and `isArchived` (`INTEGER NOT NULL DEFAULT 0`) to `customers` table.

### SQLite Entity DDL Analysis

#### 1. `customers` Table (`CustomerEntity`)
```sql
CREATE TABLE IF NOT EXISTS `customers` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `customerName` TEXT NOT NULL,
    `balance` REAL NOT NULL,
    `totalDebt` REAL NOT NULL,
    `phone` TEXT NOT NULL,
    `lastTransactionDate` TEXT NOT NULL,
    `hasRecentActivity` INTEGER NOT NULL,
    `isArchived` INTEGER NOT NULL DEFAULT 0,
    `archivedDate` TEXT
);
```
- **Primary Key:** `id` (String UUID / custom prefix like `"c1"`, `"cust_172...""`)
- **Indices:** None declared in Room `@Entity`. Lookups by `customerName` or `phone` require full table scans.
- **Financial Columns:** `balance` (REAL) and `totalDebt` (REAL) are physically stored directly in the customer row.

#### 2. `transactions` Table (`TransactionEntity`)
```sql
CREATE TABLE IF NOT EXISTS `transactions` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `title` TEXT NOT NULL,
    `customerName` TEXT NOT NULL,
    `activityType` TEXT NOT NULL,
    `amount` REAL NOT NULL,
    `isCredit` INTEGER NOT NULL,
    `date` TEXT NOT NULL,
    `relativeTime` TEXT NOT NULL,
    `notes` TEXT NOT NULL,
    `settlementType` TEXT,
    `isArchived` INTEGER NOT NULL DEFAULT 0,
    `archivedDate` TEXT
);
```
- **Primary Key:** `id` (String UUID / `"tx_..."`)
- **Missing Foreign Key:** There is **NO `customerId` column** in `transactions`. The only customer link is the `customerName` text string.
- **Indices:** None declared.
- **Temporal Columns:** `date` is a formatted String (`"yyyy-MM-dd"`), `relativeTime` is an Arabic/English display string (`"الآن"`, `"منذ 15 دقيقة"`). No numeric Unix epoch timestamp column exists.
- **Accounting Columns:** `amount` (REAL), `isCredit` (INTEGER boolean), `settlementType` (TEXT null/`"FULL"`/`"PARTIAL"`).

#### 3. `transaction_item_lines` Table (`TransactionItemLineEntity`)
```sql
CREATE TABLE IF NOT EXISTS `transaction_item_lines` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `transactionId` TEXT NOT NULL,
    `productId` TEXT,
    `productNameSnapshot` TEXT NOT NULL,
    `quantity` INTEGER NOT NULL,
    `unitPrice` REAL NOT NULL,
    `costPrice` REAL NOT NULL DEFAULT 0.0,
    `subtotal` REAL NOT NULL,
    FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE
);
CREATE INDEX `index_transaction_item_lines_transactionId` ON `transaction_item_lines` (`transactionId`);
CREATE INDEX `index_transaction_item_lines_productId` ON `transaction_item_lines` (`productId`);
```

#### 4. Auxiliary Tables
- `products`: Catalog items (`id`, `name`, `price`, `costPrice`, `category`, `unit`, `isArchived`, `archivedDate`).
- `notifications`: Activity alerts (`id`, `customerName`, `transactionType`, `amount`, `timestamp`, `isPayment`, `isRead`, `transactionId`).
- `store_info`: Single-row store metadata (`id=1`, `storeName`, `ownerName`, `phone`, `taxNumber`, `address`, `currencySymbol`).

---

## Section B: Entity Models vs Domain Models

| Entity (Database Model) | Domain Model (UI / Repository) | Mapping Discrepancy & Conversion Details |
|---|---|---|
| `CustomerEntity`<br>`(Entities.kt:15-26)` | `CustomerAccount`<br>`(StoreModels.kt:130-139)` | **Identical fields:** `id`, `customerName`, `balance`, `totalDebt`, `phone`, `lastTransactionDate`, `hasRecentActivity`, `isArchived`, `archivedDate`. Direct 1:1 mapping in `Entities.kt:116-126`. |
| `TransactionEntity`<br>`(Entities.kt:39-54)` | `TransactionItem`<br>`(StoreModels.kt:114-129)` | **CRITICAL GAP:** `TransactionItem` defines `customerId: String? = null`. However, `TransactionEntity` has **no `customerId` field**. When converting `TransactionItem.toEntity()` (`Entities.kt:171-185`), `customerId` is dropped! When mapping `TransactionEntity.toDomain()`, `customerId` defaults to `null` and is later filled via an in-memory repository heuristic. |
| `TransactionItemLineEntity`<br>`(Entities.kt:70-93)` | `TransactionItemLineEntity`<br>(used directly in domain) | Direct usage across repository and UI export layers without a separate domain abstraction. |
| `StoreInfoEntity`<br>`(Entities.kt:100-109)` | `StoreInfo`<br>`(StoreModels.kt:40-48)` | Direct mapping in `Entities.kt:143-155`. |

---

## Section C: Customer Identity & Transaction Relationship (The `customerId` vs `customerName` Gap)

### 1. Where the Gap Exists
In `Entities.kt`, `TransactionEntity` defines:
```kotlin
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val customerName: String, // String name only!
    val activityType: String,
    val amount: Double,
    val isCredit: Boolean,
    val date: String,
    val relativeTime: String,
    val notes: String,
    val settlementType: String? = null,
    val isArchived: Boolean = false,
    val archivedDate: String? = null
)
```
There is no `customerId` column. Consequently, SQLite cannot enforce relational integrity between transactions and customers.

### 2. The In-Memory Heuristic Workaround
To supply a `customerId` to the UI, `StoreRepository.kt:111-147` implements `mapTransactionsWithCustomer()`:
```kotlin
private fun mapTransactionsWithCustomer(
    entities: List<TransactionEntity>,
    customers: List<CustomerEntity>
): List<TransactionItem> {
    val customerMapByName = customers.associateBy { it.customerName.trim().lowercase() }
    return entities.map { entity ->
        val matchedCustomer = customerMapByName[entity.customerName.trim().lowercase()]
        entity.toDomain(matchedCustomer?.id)
    }
}
```

### 3. Concrete Failure Modes of Current Architecture
1. **Renaming a Customer:** If a customer's name is edited in `CustomerManagementScreen` (e.g. from "أحمد الشمري" to "أحمد م. الشمري"), all past transactions remain tagged with the old string. The customer profile will suddenly show **zero transactions**, and aging/statement calculations for that customer become empty.
2. **Duplicate or Similar Names:** If two distinct customers share the same name (e.g., two customers named "محمد علي"), all transactions for both individuals collide and resolve to whichever customer appears last in `associateBy`.
3. **Typo / Special Characters:** Whitespace discrepancies or Arabic letter differences (e.g. `أ` vs `ا` vs `إ`, or `ي` vs `ى`) lead to broken matching.
4. **General / Cash Customer:** Cash sales use `"عميل عام"` (`SampleData.kt:812`). If an actual customer named "عميل عام" is registered, cash sales erroneously link to their account.

---

## Section D: Financial Calculations & Source of Truth

### 1. The Dual-State Dilemma
Currently, SmallStore maintains two conflicting sources of truth:
1. **Source of Truth A (Stored Balance & Debt):**
   - `CustomerEntity.balance` (Real)
   - `CustomerEntity.totalDebt` (Real)
2. **Source of Truth B (Transaction History):**
   - The ledger of `TransactionEntity` rows associated with that customer.

### 2. Imperative Balance Mutation Points
The stored customer balance is mutated imperatively in `MainViewModel.kt`:

#### Flow 1: Purchases Checkout Settlement (`MainViewModel.kt:852-882`)
```kotlin
val updatedCustomer = customer.copy(
    balance = customer.balance + debtAmount,
    totalDebt = customer.totalDebt + debtAmount,
    hasRecentActivity = true
)
viewModelScope.launch {
    repository.addTransaction(newTx, lines)
    repository.updateCustomer(updatedCustomer)
    repository.addNotification(notif)
}
```
*Issue:* The new transaction records `amount = total` (the whole cart amount). But `customer.balance` and `customer.totalDebt` are increased only by `debtAmount`. The transaction row does not record what part was paid in cash versus debt.

#### Flow 2: Quick Payment (`MainViewModel.kt:934-965`)
```kotlin
val updatedCustomer = customer.copy(
    balance = (customer.balance - amount).coerceAtLeast(0.0),
    totalDebt = (customer.totalDebt - amount).coerceAtLeast(0.0),
    hasRecentActivity = true
)
viewModelScope.launch {
    repository.addTransaction(newTx)
    repository.updateCustomer(updatedCustomer)
    repository.addNotification(newNotif)
}
```
*Issue:* `balance` and `totalDebt` are decremented by `amount`. If transactions are subsequently deleted or archived, these balances are never adjusted.

#### Flow 3: Transaction Deletion / Archival (`StoreRepository.kt:205-236`)
When `deleteTransactionPermanently` or `archiveTransaction` is executed, **no customer balance update is triggered**.
The customer's stored balance remains frozen at its mutated value, permanently diverging from the sum of active transactions.

---

## Section E: Transaction Lifecycle

```
[ Cart Checkout ] ----> creates TransactionEntity (amount=total) 
                                 + updates CustomerEntity (balance += debtAmount)
                                 + creates TransactionItemLineEntity (CASCADE)

[ Quick Payment ] ----> creates TransactionEntity (amount=paymentAmount, activityType="تسديد")
                                 + updates CustomerEntity (balance -= paymentAmount)

[ Transaction Archive ] -> sets isArchived=1, archivedDate=now
                            (CustomerEntity.balance is NOT updated)

[ Transaction Unarchive]-> sets isArchived=0, archivedDate=null
                            (CustomerEntity.balance is NOT updated)

[ Permanent Delete ] ----> deletes from `transactions` table
                            + SQLite CASCADE deletes all `transaction_item_lines`
                            (CustomerEntity.balance is NOT updated)
```

---

## Section F: Settlement & Payment Model

### 1. The Split Cash/Debt Problem
In `UnifiedSettlementSheet.kt`, the user can split a checkout total into:
- Cash portion (paid immediately)
- Debt portion (remains on account)

However, `TransactionEntity` has only one `amount: Double` field and no split columns.
In `MainViewModel.kt:827-838`:
- If `debtAmount <= 0.01`:
  - `activityType = "شراء كاش"`
  - `isCredit = false`
  - `settlementType = SettlementType.FULL`
- Else:
  - `activityType = "شراء آجل"`
  - `isCredit = true`
  - `settlementType = SettlementType.PARTIAL`
- `amount = total` (the entire cart sum!)

**The Missing Ledger Entry:**
If a transaction is 100 SAR total, with 40 SAR cash and 60 SAR debt:
- `TransactionEntity` is saved with `amount = 100.0`, `isCredit = true`, `settlementType = "PARTIAL"`.
- The 40 SAR cash payment **disappears from transaction records**.
- Any report that sums `transactions.filter { it.isCredit }.sumOf { it.amount }` computes 100 SAR of credit instead of 60 SAR!
- Any report that calculates `runningBalance` will add 100 SAR instead of 60 SAR!

---

## Section G: Account Statements & Running Balance Logic

In `AnalysisCenterViewModel.kt:555-576`, the customer account statement is generated dynamically:
```kotlin
var running = 0.0
return txList.map { tx ->
    val isPayment = tx.activityType.contains("تسديد") || tx.activityType.contains("Payment")
    val isDebtPurchase = tx.isCredit || tx.activityType.contains("آجل") || tx.activityType.contains("دين")
    if (isPayment) {
        running -= tx.amount
    } else if (isDebtPurchase) {
        running += tx.amount
    }
    StatementRow(
        id = tx.id,
        date = tx.date,
        customerName = tx.customerName,
        description = if (tx.notes.isNotBlank()) tx.notes else tx.activityType,
        type = tx.activityType,
        isPayment = isPayment,
        isCreditDebt = isDebtPurchase,
        amount = tx.amount,
        runningBalance = running
    )
}
```

### Deficiencies in Running Balance
1. **Initial / Opening Balance Ignored:** `running` starts at `0.0`. If filtering by period (e.g. "This Month"), previous unpaid balances are omitted, so the first row does not reflect previous accumulated debt.
2. **Sort Order Inversion:** `TransactionDao` returns transactions sorted descending by rowid/date. If passed directly to `map`, `running` evaluates from newest to oldest, rendering the running balance column mathematically inverted.
3. **Partial Settlement Distortion:** As noted in Section F, partial settlements add `tx.amount` (the full invoice amount) rather than the debt portion, causing the statement running balance to disagree with the customer's actual account balance.

---

## Section H: Reports, Statistics & Analytics Classification

Throughout the entire codebase, transaction types are determined by ad-hoc String inspections:

| Location | String Match Condition | Purpose |
|---|---|---|
| `AnalyticsExportData.kt:173` | `!it.isCredit && (it.activityType.contains("كاش") \|\| it.activityType.contains("Cash") \|\| (!it.activityType.contains("تسديد") && !it.activityType.contains("Payment")))` | Cash Sales calculation |
| `AnalyticsExportData.kt:177` | `it.isCredit \|\| it.activityType.contains("آجل") \|\| it.activityType.contains("دين")` | Debt Sales calculation |
| `AnalyticsExportData.kt:181` | `(it.activityType.contains("تسديد") \|\| it.activityType.contains("Payment")) && (it.settlementType == SettlementType.FULL \|\| it.settlementType == null)` | Full Settlements |
| `AnalyticsExportData.kt:185` | `(it.activityType.contains("تسديد") \|\| it.activityType.contains("Payment")) && it.settlementType == SettlementType.PARTIAL` | Partial Settlements |
| `AnalysisCenterScreen.kt:1503` | Extended string check including `!it.activityType.contains("Debt") && !it.activityType.contains("شراء بالدين")` | PDF/CSV Sales Reports |
| `ReportExporter.kt:1538` | Repeated long boolean predicate across 10 distinct export generator methods | Statement & Invoices Export |

**Fragility:** If any new transaction type is introduced or localized into another string, these heuristics fail silently, placing transactions into incorrect buckets or dropping them entirely from reports.

---

## Section I: Aging Logic & Allocation

In `AnalysisCenterViewModel.kt:108-185`:
- Computes `computeDaysOld(tx.date, today)`.
- Buckets: `0-30 Days`, `31-60 Days`, `61-90 Days`, `90+ Days`.
- Outstanding debt is allocated from newest transactions to oldest (`for (txDetail in details)`).
- **Limitation:** Aging is evaluated against customer's stored `customer.totalDebt` or `customer.balance`. If individual debt transactions do not sum to `outstandingDebt` (due to split payments or manual balance adjustments), the aging algorithm produces unallocated balances or runs out of transactions.

---

## Section J: Backup & Restore Payload Structure & Vulnerabilities

In `/app/src/main/java/com/example/data/backup/BackupManager.kt`:
- Payload version: `1`
- Format: Single JSON file encoded in UTF-8.

### Stored Keys:
1. `metadata`: `exportDate`, `version=1`, `app="SmallStore"`.
2. `customers`: Serializes `balance` and `totalDebt` directly.
3. `transactions`: Serializes `customerName`, `activityType`, `amount`, `isCredit`, `date`, `relativeTime`, `notes`, `settlementType`, `isArchived`, `archivedDate`.
   - **Does NOT store `customerId`.**
   - **Does NOT store split cash/debt amounts.**
   - **Does NOT store millisecond epoch timestamps.**
4. `transaction_item_lines`: Serializes `transactionId`, `productId`, `productNameSnapshot`, `quantity`, `unitPrice`, `costPrice`, `subtotal`.
5. `products`: Catalog items.
6. `notifications`: Notification items.
7. `storeInfo`: Store details.

### Vulnerabilities:
- Upon restore (`restoreDataFromBackup`), transactions are re-inserted with whatever `customerName` was backed up.
- If customer IDs in the restored database change, `mapTransactionsWithCustomer` will attempt to re-link by name only.
- Backups lack validation checksums or schema version headers beyond a simple integer `1`.

---

## Section K: Test Coverage & Fragility Points

### Current Test Suite Inventory
1. `ExampleRobolectricTest.kt` (1029 lines):
   - Tests navigation drawer strings and destinations.
   - Tests backup serialization round-trip (`testBackupPayloadSerializationRoundtrip`).
   - Tests customer management search and filtering.
   - Tests `DataCenterScreen` and `BackupRestoreScreen` integration.
2. `AnalyticsExportDataTest.kt`:
   - Tests calculation of KPIs, chart percentages, customer isolation.
3. `AnalyticsCsvTest.kt`, `AnalyticsTxtTest.kt`, `AnalyticsPdfTest.kt`:
   - Verify export formatting and column layouts.
4. `GreetingScreenshotTest.kt`:
   - Roborazzi visual verification.

### Fragility Points for Refactor
- Tests assert exact string formats and mock `SampleData`.
- Any change to `BackupManager.serialize` / `deserialize` must maintain backwards-compatibility with version 1 JSON schemas.
- Tests in `AnalyticsExportDataTest` rely on the exact four `AnalyticsChartCategory` keys (`"DEBT"`, `"CASH"`, `"FULL_PAYMENT"`, `"PARTIAL_PAYMENT"`).

---

## Section L: Concrete List of Ambiguities, Risks & Technical Debt

1. **Denormalized Customer Linking:** `transactions.customerName` is the sole link. Customer renaming breaks history.
2. **Missing Cash/Debt Split Persistence:** Split settlements record full cart amount on the invoice and lose the cash payment breakdown.
3. **No Double-Entry / Ledger Invariant:** Customer balance is an independent mutable variable; deleting a transaction corrupts the balance invariant.
4. **No Timestamp / Sequence Number:** Transactions use string dates (`"2026-09-05"`) and relative strings (`"الآن"`). Sorting identical-day transactions relies on arbitrary database insertion order.
5. **String-Matching Everywhere:** Financial classification relies on `contains("كاش")`, `contains("آجل")`, `contains("تسديد")`.
6. **No Foreign Key on `TransactionEntity.customerId`:** Orphaned transactions can exist if a customer is hard-deleted.
7. **Interchangeable Balance vs TotalDebt Usage:** In `SmallStoreApp.kt:250-251` and other screens, `balance` and `totalDebt` are frequently swapped or used interchangeably.

---

## Section M: Verification of All 12 Assumptions

| # | Prompt Assumption | Audit Finding | Verification Result | Evidence & Code References |
|---|---|---|---|---|
| **1** | `CustomerEntity` stores `balance` and `totalDebt` | Direct physical columns in SQLite; updated imperatively in ViewModel. | **TRUE** | `Entities.kt:18-19`, `MainViewModel.kt:853-854`, `935-936` |
| **2** | `TransactionEntity` uses `customerName` instead of a persistent `customerId` relationship | No `customerId` in entity; repository uses in-memory map by name. | **TRUE** | `Entities.kt:44`, `StoreRepository.kt:111-147` |
| **3** | `TransactionEntity` uses `activityType` as `String` | Entity property is `val activityType: String`. | **TRUE** | `Entities.kt:45` |
| **4** | `TransactionEntity` uses `isCredit` as `Boolean` | Entity property is `val isCredit: Boolean`. | **TRUE** | `Entities.kt:47`, `StoreModels.kt:120` |
| **5** | `TransactionEntity` uses `settlementType` as `String?` | Entity property is `val settlementType: String? = null`. | **TRUE** | `Entities.kt:51` |
| **6** | `TransactionEntity` stores `date` and `relativeTime` as presentation-oriented values | `date` is formatted String, `relativeTime` is Arabic/English relative text. | **TRUE** | `Entities.kt:48-49` |
| **7** | Financial transactions have archive/delete paths | Entities have `isArchived`; DAO has delete/archive without balance readjustment. | **TRUE** | `Entities.kt:52-53`, `Daos.kt:124`, `StoreRepository.kt:205-236` |
| **8** | `TransactionItemLine` uses a `CASCADE` relationship | Room `ForeignKey` specifies `onDelete = ForeignKey.CASCADE`. | **TRUE** | `Entities.kt:79-84` |
| **9** | Transactions cannot currently support partial debt and partial cash on a single transaction entity | Only one `amount` field exists; cash split is discarded, total invoice recorded. | **TRUE** | `MainViewModel.kt:827-838`, `Entities.kt:46` |
| **10** | Reports and analytics use String-based accounting classification | Code checks `contains("كاش")`, `contains("تسديد")`, `contains("آجل")`, etc. | **TRUE** | `AnalyticsExportData.kt:172-187`, `AnalysisCenterScreen.kt:877-896` |
| **11** | `PaymentMethodOption` contains `CASH` and `DEBT` | Enum defined with values `CASH`, `DEBT`. | **TRUE** | `StoreModels.kt:109-112` |
| **12** | Backup data still represents the legacy accounting model | Version 1 JSON schema mirrors current untyped entities without `customerId`. | **TRUE** | `BackupManager.kt:98-116`, `220-250` |

---

## Section N: Recommended Phase-by-Phase Roadmap for Refactor

To execute the complete accounting and database refactor safely without breaking the UI, losing data, or degrading performance, the following multi-phase sequence is recommended:

```
+-------------------------------------------------------------------------+
| Phase 0: Baseline Audit & Invariant Specification [CURRENT PHASE DONE] |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
| Phase 1: Database Migration (Room v3 -> v4)                            |
| - Add `customerId` (TEXT NULL) to `transactions` table                  |
| - Add `cashAmount` (REAL DEFAULT 0.0), `debtAmount` (REAL DEFAULT 0.0) |
| - Add `timestamp` (INTEGER DEFAULT 0) for precise chronological order   |
| - Add indices on `transactions(customerId)` and `transactions(date)`   |
| - Migration script populates `customerId` by matching `customerName`    |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
| Phase 2: Domain Model & Enum Modernization                              |
| - Define strongly-typed `TransactionType` enum (SALE_CASH, SALE_DEBT,   |
|   PAYMENT_FULL, PAYMENT_PARTIAL, ADJUSTMENT)                            |
| - Map legacy `activityType` strings to typed enums seamlessly           |
| - Retain `customerName` as immutable display snapshot                   |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
| Phase 3: Split-Settlement Accounting & Ledger Consistency               |
| - Update `MainViewModel.completeSettlement` to record both `cashAmount` |
|   and `debtAmount` or generate balancing ledger entries                 |
| - Provide repository ledger balance calculation:                        |
|   `recomputeCustomerBalance(customerId): Double`                        |
| - Ensure transaction archival/deletion triggers balance recomputation   |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
| Phase 4: Reports, Analytics & Running Balance Modernization             |
| - Replace `contains("كاش")` string heuristics with typed enum queries   |
| - Fix `generateAccountStatement` running balance calculation            |
|   (chronological sort order + opening balance support)                  |
| - Standardize aging calculation against verified ledger balance         |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
| Phase 5: Backup & Restore Schema Version 2 Upgrade                      |
| - Upgrade `BackupManager` to support version 2 JSON format              |
| - Ensure transparent backwards-compatibility for restoring v1 backups   |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
| Phase 6: Full Verification, Robolectric CUJ Tests & Screen Audits       |
| - Run comprehensive regression tests                                    |
| - Verify zero visual regressions on POS, Customer Profile, & Analytics  |
+-------------------------------------------------------------------------+
```

---
*Audit Document generated and stored at `/docs/AccountingBaselineAudit.md`.*
