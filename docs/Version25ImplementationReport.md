# SmallStore Version 25 — Implementation Report

## Execution mode
- Baseline: supplied project ZIP `smallstore (27).zip`
- Specification: supplied `SmallStore_Final_Accounting_Logic_Completion_Plan.docx` (Version 25)
- Scope: accounting core, reporting, UI/UX accounting surfaces, legacy boundary, backup/restore, reconciliation tests
- Project workflow preserved: Google Studio -> GitHub -> GitHub Actions -> APK
- GitHub Actions workflow was not modified.
- No new repository/project configuration was introduced.

## Phase status after this implementation

| Phase | Status after changes | Notes |
|---|---|---|
| Phase 0 | Improved / baseline documented | Added current Version 25 source-of-truth map; actual DB is v18. |
| Phase 1 | Strengthened | Customer balance flows now use first-class typed sales/payments/opening balances/adjustments/returns/refunds. Financial-account final balance now delegates to the dedicated ledger calculator. |
| Phase 2 | Preserved / strengthened | Reversal and typed-operation invariants retained; focused tests extended. |
| Phase 3 | Strengthened | Current balance vs historical credit sales is preserved and made explicit in UI. |
| Phase 4 | Strengthened | Financial-account balance formula is centralized in `FinancialAccountLedgerCalculator`; reporting/statement behavior remains typed. |
| Phase 5 | Major completion | Removed localized string heuristics from report/UI accounting classification; statement opening balance is shown; mixed-sale components are handled correctly; analytics exports receive profit figures. |
| Phase 6 | Major completion | Revenue, historical COGS, returns, refunds, expenses, reversals and profit tests were extended; COGS return clamping removed so reversal effects are preserved. |
| Phase 7 | Boundary hardened, not physically deleted | Legacy tables/writes remain intentionally as compatibility/migration infrastructure. Production report/UI string parsing is isolated; no destructive legacy deletion was performed. |
| Phase 8 | Implemented | Backup v2 now carries the authoritative accounting entity graph and restore performs post-restore accounting reconciliation. |
| Phase 9 | Strengthened | Existing cross-surface reconciliation test remains; reporting surfaces were aligned to the same typed calculations. |
| Phase 10 | Not runtime-closed | Release gate cannot honestly be marked PASS until Gradle/device validation is run in a networked Android build environment. |

## Accounting core changes

### Revenue
- CASH sales use total sale amount.
- CREDIT sales use credit component.
- MIXED sales split into paid/cash and credit components.
- Customer payments do not create revenue.
- Reversed operations contribute zero active accounting effect.

### COGS
- Historical `SaleLine.costPriceAtSale` remains the source for sale COGS.
- Return COGS uses `SaleReturnLine.cogsReversed`.
- Removed `coerceAtLeast(0.0)` from net COGS calculation so legitimate return reversal effects are not silently flattened.

### Profit
- Gross profit remains `Net Sales - COGS`.
- Net profit remains `Gross Profit - Operating Expenses`.
- Negative gross/net profit is preserved.
- Active refunds are tracked separately and do not double-reduce revenue.

## Statement / reporting changes

- Opening balance row is enabled by default for period-based customer statements.
- Statement opening balance is calculated from transactions strictly before the selected period.
- Same-day ordering continues to use precise epoch timestamp + stable ID.
- Mixed sale statement amount represents the receivable/credit component, not the full invoice total.
- Cash-only customer sale remains visible with its full invoice amount while causing zero receivable change.
- Debt filters no longer depend on localized labels.
- Customer current debt uses `CustomerAccount.balance`, not historical cumulative credit sales.
- Historical credit sales are explicitly labeled as historical credit sales in the customer profile.
- Report/analytics type labels are generated from typed transaction/sale enums.
- Added Gross Profit and Net Profit KPI presentation to Statistics and Analytics exports.
- PDF KPI styling no longer parses localized labels to decide accounting meaning/color.

## UI/UX changes

### Home
- Current outstanding debt shown from current customer balances, with all-customer fallback.
- Activity amount direction is based on typed transaction type rather than the legacy `isCredit` flag alone.
- Reversal/archived badges remain intact.

### Customer Profile
- Historical cumulative credit sales are no longer mislabeled as current debt.
- Current balance remains the authoritative outstanding balance.

### Accounts
- Debt sorting/current balances use the authoritative customer balance supplied by the repository instead of recalculating from legacy transaction rows.

### Customer Statement
- Opening balance is visible for period statements.
- Mixed sale displays only its receivable component in the statement amount.
- Running balance is based on receivable impact.
- Cash sale remains visible without increasing receivable.

### Reports
- Cash/debt/mixed classification uses typed accounting data.
- Mixed sales are counted in both component totals where appropriate.
- Localized display text is no longer used to determine accounting classification.

### Transactions Report
- Filters use typed transaction/sale semantics.
- Transaction labels cover sale, payment, return, refund, purchase, supplier payment, purchase return, expense, stock adjustment, opening balance and reversal.

### Sales Report
- Payment-type pill now distinguishes CASH / CREDIT / MIXED instead of forcing mixed sales into cash/debt.

### Statistics / Analytics
- Statistics now calculates using transaction lines for COGS/profit.
- Added Gross Profit and Net Profit KPI cards.
- Analytics prepared data now carries COGS/Gross Profit/Net Profit.
- CSV/TXT/PDF analytics exports include the profit figures.

### Backup / Restore
- Added Version 2 backup information card to the backup UI.
- UI explains what the backup contains and that restore performs accounting reconciliation.

## Legacy boundary

Localized accounting-string parsing remaining after this pass:
- `LegacyAccountingBridge.kt` only — compatibility/migration mapping.
- No report/UI accounting classifier still uses `contains("cash/debt/payment/...")`.

Legacy `TransactionEntity` writes remain in operational repositories because Version 25 requires a controlled compatibility boundary and reconciliation before destructive removal. They were not deleted.

## Backup v2 contents

The backup now preserves:
- customers and customer identity conflicts
- products
- sales / sale lines
- financial accounts
- payment methods
- customer payments
- opening balances
- adjustments
- reversals
- sale returns / return lines
- refunds
- suppliers
- purchases / purchase lines
- supplier payments
- purchase returns
- expense categories / expenses
- stock movements
- notifications
- legacy compatibility snapshot where present

Version 1 payloads remain deserializable/restorable.

## Tests added/updated

Added/extended focused tests for:
- sale return reversing revenue and historical COGS
- reversed sale return
- active vs reversed refund
- expense vs reversed expense
- Version 2 authoritative backup serialization
- mixed-sale statement amount

Existing Version 25 tests for:
- positive margin
- zero margin
- negative margin
- mixed sale
- historical sale cost
- reversed sale
remain in the project.

## Validation limitation

A complete Gradle test run was attempted from the supplied project, but this execution environment does not have the Gradle 9.3.1 distribution cached and cannot reach `services.gradle.org`.

Observed blocker:
`UnknownHostException: services.gradle.org`

Therefore:
- No test suite is falsely marked PASS.
- No APK build is falsely marked PASS.
- The project is packaged with the changes, but final runtime/build validation must be performed in the user's connected Android/Google Studio or another networked Gradle environment.

## Intentionally unchanged

- `.github/workflows/build-apk.yml`
- Gradle project/repository identity
- database version/migrations
- existing legacy tables
- app theme/navigation architecture
- unrelated UI features
- existing Google Studio/GitHub workflow
