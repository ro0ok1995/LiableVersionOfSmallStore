# SmallStore — Dashboard & Accounting Update Candidate

This archive contains the complete Android project rooted at the top level of this ZIP. It is based on the uploaded `smallstore (15).zip` project root, with targeted dashboard, transaction reporting, customer payment, and debt-aging updates merged into the original source files.

## Important
- The archive intentionally does not contain a second nested copy of the project.
- Original non-empty source files are preserved. In particular, the four Kotlin files that were accidentally zero-byte in the previous archive have been restored from the uploaded project.
- The project has **not** been verified by a successful Gradle build in this environment. The Gradle wrapper could not download Gradle 9.3.1 because `services.gradle.org` was unreachable (`UnknownHostException`).
- Please run the GitHub Actions `Build APK` workflow and test on a backup/test database before using real store data.

## Main areas updated
- Dashboard tab state persistence and global KPI scope while filtering activity lists by customer.
- Activity history source keeps reversed operations available for display; financial KPI calculations should continue excluding reversed operations.
- Dashboard “view all transactions” navigation with selected period passed to the analysis report.
- Customer payment validation and same-ID payment/transaction recording path.
- Debt aging allocation and unaged opening/legacy balance handling.

See `IMPLEMENTATION_STATUS_AR.md` and `SMALLSTORE_ACCOUNTING_UI_PATCH_NOTES_AR.md` for details and limitations.
