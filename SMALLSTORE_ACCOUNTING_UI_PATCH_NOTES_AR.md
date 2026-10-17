# SmallStore — Accounting and UI Patch Notes (2026-10-12)

## Files included
- `app/src/main/java/com/example/ui/screens/SupplierManagementScreen.kt`
- `app/src/main/java/com/example/ui/screens/AnalysisCenterScreen.kt`
- `app/src/main/java/com/example/viewmodel/AnalysisCenterViewModel.kt`
- `app/src/test/java/com/example/accounting/SupplierPayableRegressionTest.kt`

## Changes
1. Supplier management no longer converts supplier-balance lookup failures into a fake zero balance. A failed lookup is displayed as unavailable, and suppliers with unknown balances are not incorrectly classified as settled or payable.
2. Supplier card colors use shared status tokens rather than hard-coded green/red hex values. Balance wording distinguishes amount owed to supplier, supplier advance, settled balance, and unavailable balance.
3. Supplier analytics net payable movement now uses `creditAmount - payments - returns`, not `totalAmount - payments - returns`. Cash purchases no longer inflate payable movement. Report explanatory text was updated to explicitly say credit purchases.
4. Customer statement sale rows display the credit/receivable portion of a sale, while the running balance continues to use the ledger impact. This prevents mixed cash-and-credit sales from showing the whole invoice as a debt movement.
5. Added regression examples for supplier payable arithmetic, including opening payable 500 + cash purchase 1,000 + mixed purchase credit 200 - payment 250 - return 50 = 400, and a negative payable representing a supplier advance.

## Verification
- Arithmetic scenario independently checked: `500 + 200 - 250 - 50 = 400`; the cash purchase has `creditAmount = 0` and does not affect payable.
- A Gradle compile/test run was attempted, but could not start because the Gradle wrapper distribution was not cached and this environment could not resolve `services.gradle.org` (`UnknownHostException`). Therefore Kotlin compilation and JUnit execution are **not verified** here.

## Important scope limitations / follow-up required
This is a targeted patch, not a claim that every screen in the project has been fully repaired. In particular:
- `SupplierProfileScreen.kt` and `AccountsScreen.kt` still have fallback balance paths that do not receive opening balances/adjustments in their local fallback arguments. The authoritative repository callback should be the only trusted result, or the complete ledger inputs must be supplied to every fallback.
- The supplier analytics `totalPayables` fallback and export/report parity should be checked against all supplier balances and opening balances.
- Full app build, all unit tests, visual QA in light/dark and all selectable themes, PDF/CSV export verification, and customer/supplier statement reconciliation remain required before production use.
- Purchase returns are currently subtracted from payable movement as their recorded return amount; confirm that this matches business semantics for returns already paid/refunded or linked to cash purchases.

## Manual acceptance scenario
Expected supplier payable:
- Opening supplier credit balance: 500
- Cash purchase: total 1,000, paid 1,000, credit 0
- Mixed purchase: total 500, paid 300, credit 200
- Supplier payment: 250
- Purchase return reducing payable: 50
- Expected ending payable: 400

The same ending balance must be shown by supplier profile, accounts, dashboard, analytics, and supplier statement for the same data scope. If one differs, do not release until its source/fallback is reconciled.
