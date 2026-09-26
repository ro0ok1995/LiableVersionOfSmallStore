package com.example.accounting

import com.example.data.db.Adjustment
import com.example.data.db.OpeningBalance
import com.example.data.db.Purchase
import com.example.data.db.PurchaseReturn
import com.example.data.db.SupplierPayment
import com.example.model.OperationStatus

/**
 * =============================================================================
 * PURE DOMAIN SUPPLIER PAYABLE LEDGER CALCULATOR
 * =============================================================================
 *
 * Phase 9 of SmallStore Accounting Refactor:
 * Provides a pure, deterministic domain calculation layer that calculates
 * supplier payable balances and ledger statements from immutable accounting records.
 *
 * CRITICAL ACCOUNTING INVARIANTS:
 * 1. ZERO UI or Android dependencies (pure Kotlin).
 * 2. Supplier balance is ALWAYS calculated dynamically from ledger events, NEVER stored as editable state.
 * 3. Accounts Payable (AP) convention:
 *    - balance > 0: Store owes supplier (Accounts Payable liability).
 *    - balance == 0: Fully settled.
 *    - balance < 0: Store overpaid supplier (advance credit with supplier).
 * 4. Purchases:
 *    - Only creditAmount (unpaid portion) increases the supplier payable liability.
 *    - Fully paid cash purchases create zero net payable liability.
 * 5. Supplier Payments:
 *    - Reduce the supplier payable liability (debit).
 * 6. Purchase Returns:
 *    - Reduce the supplier payable liability (debit).
 * 7. REVERSALS:
 *    - OperationStatus.REVERSED / status == "REVERSED" operations NEVER contribute to active balance.
 */
object SupplierLedgerCalculator {

    const val EPSILON = 0.0001

    fun calculateSupplierBalance(
        supplierId: String,
        purchases: List<Purchase>,
        payments: List<SupplierPayment> = emptyList(),
        returns: List<PurchaseReturn> = emptyList(),
        adjustments: List<Adjustment> = emptyList(),
        openingBalances: List<OpeningBalance> = emptyList()
    ): SupplierBalanceSummary {
        var openingCredit = 0.0
        var openingDebit = 0.0
        var totalPurchases = 0.0
        var totalCreditPurchases = 0.0
        var totalPayments = 0.0
        var totalReturns = 0.0
        var debitAdjustments = 0.0
        var creditAdjustments = 0.0
        var activeCount = 0

        for (ob in openingBalances) {
            if (ob.entityType != "SUPPLIER" || ob.entityId != supplierId) continue
            activeCount++
            if (ob.direction == "CREDIT") {
                openingCredit += ob.amount
            } else {
                openingDebit += ob.amount
            }
        }

        for (p in purchases) {
            if (p.supplierId != supplierId) continue
            if (p.status == "REVERSED") continue

            activeCount++
            totalPurchases += p.totalAmount
            totalCreditPurchases += p.creditAmount
        }

        for (pm in payments) {
            if (pm.supplierId != supplierId) continue
            if (pm.status == "REVERSED") continue

            activeCount++
            totalPayments += pm.amount
        }

        for (pr in returns) {
            if (pr.supplierId != supplierId) continue
            if (pr.status == "REVERSED") continue

            activeCount++
            totalReturns += pr.amount
        }

        for (adj in adjustments) {
            if (adj.entityType != "SUPPLIER" || adj.entityId != supplierId) continue
            if (adj.status == "REVERSED") continue

            activeCount++
            if (adj.direction == "DEBIT") {
                debitAdjustments += adj.amount
            } else {
                creditAdjustments += adj.amount
            }
        }

        val netOpeningBalance = openingCredit - openingDebit
        val netPayable = netOpeningBalance + totalCreditPurchases - totalPayments - totalReturns + creditAdjustments - debitAdjustments

        return SupplierBalanceSummary(
            supplierId = supplierId,
            openingBalance = netOpeningBalance,
            totalPurchases = totalPurchases,
            totalCreditPurchases = totalCreditPurchases,
            totalPayments = totalPayments,
            totalReturns = totalReturns,
            debitAdjustments = debitAdjustments,
            creditAdjustments = creditAdjustments,
            balance = netPayable,
            activeTransactionCount = activeCount
        )
    }

    fun buildSupplierLedger(
        supplierId: String,
        purchases: List<Purchase>,
        payments: List<SupplierPayment> = emptyList(),
        returns: List<PurchaseReturn> = emptyList(),
        adjustments: List<Adjustment> = emptyList(),
        openingBalances: List<OpeningBalance> = emptyList()
    ): List<SupplierLedgerEntry> {
        val rawEntries = mutableListOf<RawLedgerItem>()

        for (ob in openingBalances) {
            if (ob.entityType != "SUPPLIER" || ob.entityId != supplierId) continue
            val credit = if (ob.direction == "CREDIT") ob.amount else 0.0
            val debit = if (ob.direction == "DEBIT") ob.amount else 0.0
            rawEntries.add(
                RawLedgerItem(
                    id = ob.id,
                    date = ob.date,
                    createdAt = ob.createdAt,
                    type = SupplierLedgerEntryType.OPENING_BALANCE,
                    debit = debit,
                    credit = credit,
                    referenceId = ob.reference ?: ob.id,
                    description = ob.reason?.takeIf { it.isNotBlank() }
                        ?: ob.reference?.takeIf { it.isNotBlank() }
                        ?: "رصيد افتتاحي",
                    status = OperationStatus.ACTIVE
                )
            )
        }

        for (p in purchases) {
            if (p.supplierId != supplierId) continue
            val status = if (p.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val credit = if (status == OperationStatus.ACTIVE) p.creditAmount else 0.0
            rawEntries.add(
                RawLedgerItem(
                    id = p.id,
                    date = p.purchaseDate,
                    createdAt = p.createdAt,
                    type = SupplierLedgerEntryType.PURCHASE,
                    debit = 0.0,
                    credit = credit,
                    referenceId = p.invoiceNumber,
                    description = "فاتورة مشتريات ${p.invoiceNumber} (إجمالي: ${p.totalAmount})",
                    status = status
                )
            )
        }

        for (pm in payments) {
            if (pm.supplierId != supplierId) continue
            val status = if (pm.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val debit = if (status == OperationStatus.ACTIVE) pm.amount else 0.0
            rawEntries.add(
                RawLedgerItem(
                    id = pm.id,
                    date = pm.paymentDate,
                    createdAt = pm.createdAt,
                    type = SupplierLedgerEntryType.SUPPLIER_PAYMENT,
                    debit = debit,
                    credit = 0.0,
                    referenceId = pm.referenceNumber ?: pm.id,
                    description = pm.notes?.takeIf { it.isNotBlank() } ?: "سداد للمورد",
                    status = status
                )
            )
        }

        for (pr in returns) {
            if (pr.supplierId != supplierId) continue
            val status = if (pr.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val debit = if (status == OperationStatus.ACTIVE) pr.amount else 0.0
            rawEntries.add(
                RawLedgerItem(
                    id = pr.id,
                    date = pr.returnDate,
                    createdAt = pr.createdAt,
                    type = SupplierLedgerEntryType.PURCHASE_RETURN,
                    debit = debit,
                    credit = 0.0,
                    referenceId = pr.purchaseId,
                    description = pr.reason.takeIf { it.isNotBlank() } ?: "مرتجع مشتريات",
                    status = status
                )
            )
        }

        for (adj in adjustments) {
            if (adj.entityType != "SUPPLIER" || adj.entityId != supplierId) continue
            val status = if (adj.status == "REVERSED") OperationStatus.REVERSED else OperationStatus.ACTIVE
            val isDebit = adj.direction == "DEBIT"
            val debit = if (status == OperationStatus.ACTIVE && isDebit) adj.amount else 0.0
            val credit = if (status == OperationStatus.ACTIVE && !isDebit) adj.amount else 0.0
            rawEntries.add(
                RawLedgerItem(
                    id = adj.id,
                    date = adj.date,
                    createdAt = adj.createdAt,
                    type = SupplierLedgerEntryType.ADJUSTMENT,
                    debit = debit,
                    credit = credit,
                    referenceId = adj.id,
                    description = adj.reason,
                    status = status
                )
            )
        }

        // Sort chronologically
        rawEntries.sortWith(compareBy<RawLedgerItem> { it.date }.thenBy { it.createdAt })

        var runningBalance = 0.0
        return rawEntries.map { item ->
            if (item.status == OperationStatus.ACTIVE) {
                runningBalance += (item.credit - item.debit)
            }
            SupplierLedgerEntry(
                id = item.id,
                supplierId = supplierId,
                date = item.date,
                entryType = item.type,
                debit = item.debit,
                credit = item.credit,
                runningBalance = runningBalance,
                referenceId = item.referenceId,
                description = item.description,
                operationStatus = item.status
            )
        }
    }

    private data class RawLedgerItem(
        val id: String,
        val date: String,
        val createdAt: Long,
        val type: SupplierLedgerEntryType,
        val debit: Double,
        val credit: Double,
        val referenceId: String,
        val description: String,
        val status: OperationStatus
    )
}
