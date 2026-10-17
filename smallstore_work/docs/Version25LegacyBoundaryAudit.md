# Version 25 — Legacy Accounting Boundary Audit

## Remaining production legacy/string interpretation
After the reporting/UI modernization pass, localized accounting-string parsing remains only in:

1. `app/src/main/java/com/example/model/LegacyAccountingBridge.kt`
   - Compatibility/migration mapping only.
2. `app/src/main/java/com/example/util/AnalyticsPdfGenerator.kt`
   - No longer parses labels for KPI color; KPI color is positional/typed.

## Legacy transaction compatibility
The following repositories still write compatibility `TransactionEntity` records when creating modern operations:
- `SalesRepository`
- `PurchasesRepository`
- `SupplierPaymentRepository`
- `PurchaseReturnRepository`
- `RefundRepository`
- `ExpenseRepository`
- `AdjustmentRepository`
- `SaleReturnRepository`

These writes are retained because the Version 25 plan explicitly requires controlled legacy isolation before deletion. The reporting surfaces now use typed accounting semantics instead of re-parsing localized strings.

## Removal readiness
Legacy tables are **not** deleted in this implementation. A later Phase 7 bounded reconciliation must prove that modern entities can operate without legacy accounting reads before physical removal.
