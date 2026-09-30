package com.example.accounting

import com.example.data.db.Adjustment
import com.example.data.db.CustomerPayment
import com.example.data.db.Expense
import com.example.data.db.OpeningBalance
import com.example.data.db.Purchase
import com.example.data.db.PurchaseLine
import com.example.data.db.PurchaseReturn
import com.example.data.db.Refund
import com.example.data.db.Sale
import com.example.data.db.SaleLine
import com.example.data.db.SaleReturn
import com.example.data.db.SaleReturnLine
import com.example.data.db.StockMovementEntity
import com.example.data.db.SupplierPayment
import com.example.model.TransactionItem

/**
 * =============================================================================
 * CENTRAL ACCOUNTING ENGINE
 * =============================================================================
 *
 * Phase 3 — Central Accounting Engine:
 * The authoritative, unified calculation entry point for all accounting operations
 * in SmallStore.
 *
 * It centralizes and unifies:
 * 1. Customer Receivable Ledger (Accounts Receivable / Customer balances)
 * 2. Supplier Payable Ledger (Accounts Payable / Supplier balances)
 * 3. Inventory Stock & Valuation Ledger (Perpetual Stock Movements & Stock on hand)
 * 4. Financial Reporting & Ledger Reconciliation (Cash flow, Sales, and Profit totals)
 *
 * Strict Accounting Invariants:
 * - Pure Kotlin: ZERO Android framework or UI dependencies.
 * - Single Source of Truth: Calculations are derived deterministically from persisted
 *   operational records.
 * - Reversal Invariant: Any record with OperationStatus.REVERSED is excluded from
 *   active balances and totals.
 * - No Double Counting: Cash vs Credit operations are split accurately.
 */
object CentralAccountingEngine {

    // =========================================================================
    // 1. CUSTOMER RECEIVABLE LEDGER (Accounts Receivable)
    // =========================================================================

    /**
     * Calculates customer balance and receivable summary from legacy/domain transactions.
     */
    fun calculateCustomerBalance(
        customerId: String,
        transactions: List<TransactionItem>
    ): CustomerBalanceSummary {
        return CustomerLedgerCalculator.calculateCustomerBalance(customerId, transactions)
    }

    /**
     * Calculates customer balance and receivable summary from first-class persisted operations.
     */
    fun calculateCustomerBalance(
        customerId: String,
        sales: List<Sale>,
        payments: List<CustomerPayment> = emptyList(),
        openingBalances: List<OpeningBalance> = emptyList(),
        adjustments: List<Adjustment> = emptyList(),
        saleReturns: List<SaleReturn> = emptyList(),
        refunds: List<Refund> = emptyList()
    ): CustomerBalanceSummary {
        return CustomerLedgerCalculator.calculateCustomerBalance(
            customerId = customerId,
            sales = sales,
            payments = payments,
            openingBalances = openingBalances,
            adjustments = adjustments,
            saleReturns = saleReturns,
            refunds = refunds
        )
    }

    /**
     * Converts a collection of domain [TransactionItem]s for a specific [customerId] into
     * standard customer ledger entries.
     */
    fun toCustomerLedgerEntries(
        customerId: String,
        transactions: List<TransactionItem>
    ): List<CustomerLedgerEntry> {
        return CustomerLedgerCalculator.toLedgerEntries(customerId, transactions)
    }

    /**
     * Builds a detailed customer account statement from first-class persisted operations.
     */
    fun buildCustomerStatement(
        customerId: String,
        sales: List<Sale>,
        payments: List<CustomerPayment> = emptyList(),
        openingBalances: List<OpeningBalance> = emptyList(),
        adjustments: List<Adjustment> = emptyList(),
        saleReturns: List<SaleReturn> = emptyList(),
        refunds: List<Refund> = emptyList()
    ): List<CustomerLedgerEntry> {
        val saleEntries = sales.mapNotNull { CustomerLedgerCalculator.saleToLedgerEntry(customerId, it) }
        val paymentEntries = payments.mapNotNull { CustomerLedgerCalculator.customerPaymentToLedgerEntry(customerId, it) }
        val openingEntries = openingBalances.mapNotNull { CustomerLedgerCalculator.openingBalanceToLedgerEntry(customerId, it) }
        val adjustmentEntries = adjustments.mapNotNull { CustomerLedgerCalculator.adjustmentToLedgerEntry(customerId, it) }
        val returnEntries = saleReturns.mapNotNull { CustomerLedgerCalculator.saleReturnToLedgerEntry(customerId, it) }
        val refundEntries = refunds.mapNotNull { CustomerLedgerCalculator.refundToLedgerEntry(customerId, it) }
        return openingEntries + saleEntries + paymentEntries + adjustmentEntries + returnEntries + refundEntries
    }

    // =========================================================================
    // 2. SUPPLIER PAYABLE LEDGER (Accounts Payable)
    // =========================================================================

    /**
     * Calculates supplier balance and payable summary from first-class persisted operations.
     */
    fun calculateSupplierBalance(
        supplierId: String,
        purchases: List<Purchase>,
        payments: List<SupplierPayment> = emptyList(),
        returns: List<PurchaseReturn> = emptyList(),
        adjustments: List<Adjustment> = emptyList(),
        openingBalances: List<OpeningBalance> = emptyList()
    ): SupplierBalanceSummary {
        return SupplierLedgerCalculator.calculateSupplierBalance(
            supplierId = supplierId,
            purchases = purchases,
            payments = payments,
            returns = returns,
            adjustments = adjustments,
            openingBalances = openingBalances
        )
    }

    /**
     * Builds a detailed supplier account statement from first-class persisted operations.
     */
    fun buildSupplierLedger(
        supplierId: String,
        purchases: List<Purchase>,
        payments: List<SupplierPayment> = emptyList(),
        returns: List<PurchaseReturn> = emptyList(),
        adjustments: List<Adjustment> = emptyList(),
        openingBalances: List<OpeningBalance> = emptyList()
    ): List<SupplierLedgerEntry> {
        return SupplierLedgerCalculator.buildSupplierLedger(
            supplierId = supplierId,
            purchases = purchases,
            payments = payments,
            returns = returns,
            adjustments = adjustments,
            openingBalances = openingBalances
        )
    }

    // =========================================================================
    // 3. INVENTORY STOCK & VALUATION LEDGER (Perpetual Inventory)
    // =========================================================================

    /**
     * Authoritative Phase 1 source-of-truth inventory calculation:
     * Calculates current stock on hand and valuation for a single product directly
     * from persisted [StockMovementEntity] records.
     */
    fun calculateProductStockFromMovements(
        productId: String,
        movements: List<StockMovementEntity>,
        fallbackUnitCost: Double = 0.0,
        productName: String = ""
    ): ProductStockSummary {
        return InventoryLedgerCalculator.calculateProductStockFromMovements(
            productId = productId,
            movements = movements,
            fallbackUnitCost = fallbackUnitCost,
            productName = productName
        )
    }

    /**
     * Authoritative Phase 1 source-of-truth inventory calculation:
     * Calculates current stock on hand and valuation for all products directly
     * from persisted [StockMovementEntity] records.
     */
    fun calculateAllProductsStockFromMovements(
        productIds: Set<String>,
        movements: List<StockMovementEntity>,
        productCostPrices: Map<String, Double> = emptyMap(),
        productNames: Map<String, String> = emptyMap()
    ): Map<String, ProductStockSummary> {
        return InventoryLedgerCalculator.calculateAllProductsStockFromMovements(
            productIds = productIds,
            movements = movements,
            productCostPrices = productCostPrices,
            productNames = productNames
        )
    }

    /**
     * Calculates total inventory valuation across all products.
     */
    fun calculateTotalInventoryValuation(summaries: Collection<ProductStockSummary>): Double {
        return InventoryLedgerCalculator.calculateTotalInventoryValuation(summaries)
    }

    /**
     * Builds a chronological inventory movement ledger for a product.
     */
    fun buildInventoryLedger(
        productId: String,
        purchases: List<Purchase>,
        purchaseLines: List<PurchaseLine>,
        sales: List<Sale>,
        saleLines: List<SaleLine>,
        saleReturns: List<SaleReturn> = emptyList(),
        saleReturnLines: List<SaleReturnLine> = emptyList(),
        purchaseReturns: List<PurchaseReturn> = emptyList(),
        adjustments: List<Adjustment> = emptyList()
    ): List<InventoryMovementEntry> {
        return InventoryLedgerCalculator.buildInventoryLedger(
            productId = productId,
            purchases = purchases,
            purchaseLines = purchaseLines,
            sales = sales,
            saleLines = saleLines,
            saleReturns = saleReturns,
            saleReturnLines = saleReturnLines,
            purchaseReturns = purchaseReturns,
            adjustments = adjustments
        )
    }

    // =========================================================================
    // 4. FINANCIAL REPORTING & DECOMPOSITION
    // =========================================================================

    /**
     * Calculates authoritative financial report totals directly from domain [TransactionItem]s.
     */
    fun calculateFinancialReport(transactions: List<TransactionItem>): FinancialReportTotals {
        return FinancialReportCalculator.calculate(transactions)
    }

    /**
     * Calculates authoritative financial report totals directly from first-class [Sale] entities.
     */
    fun calculateFinancialReportFromSales(
        sales: List<Sale>,
        saleLines: List<SaleLine> = emptyList(),
        saleReturns: List<SaleReturn> = emptyList(),
        saleReturnLines: List<SaleReturnLine> = emptyList(),
        expenses: List<Expense> = emptyList()
    ): FinancialReportTotals {
        return FinancialReportCalculator.calculateFromSales(
            sales = sales,
            saleLines = saleLines,
            saleReturns = saleReturns,
            saleReturnLines = saleReturnLines,
            expenses = expenses
        )
    }

    /**
     * Phase 4C: Calculates Operating Expenses from a list of first-class [Expense] entities.
     */
    fun calculateOperatingExpenses(expenses: List<Expense>): Double {
        return FinancialReportCalculator.calculateOperatingExpenses(expenses)
    }

    /**
     * Phase 4C: Calculates Net Profit.
     * Net Profit = Gross Profit - Operating Expenses
     */
    fun calculateNetProfit(grossProfit: Double, operatingExpenses: Double): Double {
        return FinancialReportCalculator.calculateNetProfit(grossProfit, operatingExpenses)
    }

    /**
     * Calculates authoritative financial report totals for a specific customer.
     */
    fun calculateCustomerFinancialTotals(
        customerId: String,
        transactions: List<TransactionItem>
    ): FinancialReportTotals {
        return FinancialReportCalculator.calculateCustomerTotals(customerId, transactions)
    }

    /**
     * Decomposes a single [TransactionItem] into its pure financial accounting effects.
     */
    fun decomposeTransaction(tx: TransactionItem): TransactionFinancialDecomposition {
        return FinancialReportCalculator.decomposeTransaction(tx)
    }
}

/**
 * Conceptual alias for the central accounting calculation source.
 */
typealias AccountingEngine = CentralAccountingEngine
