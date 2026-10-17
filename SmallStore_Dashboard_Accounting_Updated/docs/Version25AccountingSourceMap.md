# SmallStore Version 25 — Accounting Source-of-Truth Map

## Baseline
- Room database: v18
- Typed vocabulary: `FinancialVocabulary.kt`
- Authoritative engines:
  - `CentralAccountingEngine.kt`
  - `CustomerLedgerCalculator.kt`
  - `SupplierLedgerCalculator.kt`
  - `FinancialAccountLedgerCalculator.kt`
  - `InventoryLedgerCalculator.kt`
  - `FinancialReportCalculator.kt`

## Authoritative concepts

| Concept | Authoritative source |
|---|---|
| Customer current balance | `CustomerLedgerCalculator` / `CentralAccountingEngine.calculateCustomerBalance` |
| Historical credit sales | Customer ledger `totalCreditSales` |
| Supplier payable | `SupplierLedgerCalculator` / `CentralAccountingEngine.calculateSupplierBalance` |
| Financial account balance | `CentralAccountingEngine.calculateFinancialAccountBalance` |
| Inventory stock | `InventoryLedgerCalculator` from stock movements |
| Revenue / sales decomposition | `FinancialReportCalculator` |
| Historical COGS | `SaleLine.costPriceAtSale` |
| Return COGS reversal | `SaleReturnLine.cogsReversed` |
| Gross profit | `FinancialReportCalculator.calculateGrossProfit` |
| Operating expenses | `FinancialReportCalculator.calculateOperatingExpenses` |
| Net profit | `FinancialReportCalculator.calculateNetProfit` |
| Statement opening balance | `AnalysisCenterViewModel.calculateOpeningBalance` |
| Statement ordering | `FinancialTimestampUtils.resolveTransactionTimestamp` + stable ID |
| Customer identity | `customerId` |
| Sale settlement | `SaleType` + `paidAmount` + `creditAmount` |
| Operation lifecycle | `OperationStatus` / persisted status |

## Presentation contract
Presentation and exporters may localize/format typed results, but must not infer accounting meaning from:
- Arabic/English labels
- `activityType.contains(...)`
- localized payment/debt/cash strings
- display-only text

The remaining legacy string interpretation is isolated in `LegacyAccountingBridge` for compatibility/migration only.

## Legacy boundary
`TransactionEntity` / `TransactionItemLineEntity` remain compatibility data during migration. Their typed fields can be consumed as a compatibility read model, but they are not allowed to introduce a competing accounting formula.

## Backup
Version 2 backup payload preserves the complete authoritative accounting entity graph and retains Version 1 fields for backward compatibility. Restore performs a post-restore accounting reconciliation before returning success.
