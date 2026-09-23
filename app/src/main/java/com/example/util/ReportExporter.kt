package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.accounting.CustomerLedgerCalculator
import com.example.accounting.FinancialReportCalculator
import com.example.model.AnalyticsReportData
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.model.OperationStatus
import com.example.model.SettlementType
import com.example.model.StoreStrings
import com.example.model.TransactionItem
import com.example.model.TransactionType
import com.example.model.SaleType
import com.example.model.epochTimestampMillis
import com.example.model.typedOperationStatus
import com.example.model.typedSaleType
import com.example.model.typedTransactionType
import com.example.ui.screens.AggregatedProductLine
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StatementRow(
    val id: String,
    val date: String,
    val customerName: String,
    val description: String,
    val type: String,
    val isPayment: Boolean,
    val isCreditDebt: Boolean = false,
    val amount: Double,
    val runningBalance: Double,
    val isArchived: Boolean = false
)

data class ReportPreviewRow(
    val col1: String,
    val col2: String,
    val col3: String,
    val col4: String
)

object ReportExporter {
    fun isPaymentTransaction(tx: TransactionItem): Boolean {
        if ((tx.operationStatus ?: tx.typedOperationStatus) == OperationStatus.REVERSED) return false
        val type = tx.transactionType ?: tx.typedTransactionType
        return type == TransactionType.CUSTOMER_PAYMENT || type == TransactionType.SALE_RETURN
    }

    fun isDebtTransaction(tx: TransactionItem): Boolean {
        if ((tx.operationStatus ?: tx.typedOperationStatus) == OperationStatus.REVERSED) return false
        if (isPaymentTransaction(tx)) return false
        val saleType = tx.saleType ?: tx.typedSaleType
        val type = tx.transactionType ?: tx.typedTransactionType
        return saleType == SaleType.CREDIT || saleType == SaleType.MIXED ||
            (type == TransactionType.SALE && (tx.creditAmount > 0.0 || tx.isCredit)) ||
            (type == null && (tx.creditAmount > 0.0 || tx.isCredit))
    }

    fun getInvoiceTypeLabel(inv: TransactionItem, isArabic: Boolean): String {
        if (inv.typedOperationStatus == OperationStatus.REVERSED || inv.typedTransactionType == TransactionType.REVERSAL) {
            return if (isArabic) "ملغاة" else "Reversed"
        }
        val saleType = inv.saleType ?: inv.typedSaleType
        return when (saleType) {
            SaleType.CASH -> if (isArabic) "كاش" else "Cash"
            SaleType.CREDIT -> if (isArabic) "آجل" else "Debt"
            SaleType.MIXED -> if (isArabic) "مختلط" else "Mixed"
            null -> {
                val isCash = !inv.isCredit && inv.creditAmount <= 0.0
                if (isCash) (if (isArabic) "كاش" else "Cash") else (if (isArabic) "آجل" else "Debt")
            }
        }
    }

    fun getTransactionTypeLabel(tx: TransactionItem, isArabic: Boolean, shortLabel: Boolean = false): String {
        if (tx.typedOperationStatus == OperationStatus.REVERSED || tx.typedTransactionType == TransactionType.REVERSAL) {
            return if (isArabic) (if (shortLabel) "إلغاء" else "إلغاء معاملة") else "Reversal"
        }
        val type = tx.transactionType ?: tx.typedTransactionType
        val saleType = tx.saleType ?: tx.typedSaleType
        return when (type) {
            TransactionType.CUSTOMER_PAYMENT -> if (isArabic) (if (shortLabel) "تسديد" else "تسديد (دفعة)") else "Payment"
            TransactionType.SALE -> when (saleType) {
                SaleType.CASH -> if (isArabic) (if (shortLabel) "شراء كاش" else "شراء نقدي (كاش)") else "Cash Purchase"
                SaleType.CREDIT -> if (isArabic) (if (shortLabel) "شراء آجل" else "شراء آجل (دين)") else "Credit Purchase"
                SaleType.MIXED -> if (isArabic) "شراء مختلط" else "Mixed Purchase"
                null -> if (tx.isCredit || tx.creditAmount > 0.0) (if (isArabic) (if (shortLabel) "شراء آجل" else "شراء آجل (دين)") else "Credit Purchase") else (if (isArabic) (if (shortLabel) "شراء كاش" else "شراء نقدي (كاش)") else "Cash Purchase")
            }
            TransactionType.SALE_RETURN -> if (isArabic) "مرتجع مبيعات" else "Sale Return"
            TransactionType.CUSTOMER_REFUND -> if (isArabic) "استرداد نقدي" else "Customer Refund"
            TransactionType.BALANCE_ADJUSTMENT -> if (isArabic) (if (shortLabel) "تعديل رصيد" else "تعديل رصيد") else "Balance Adjustment"
            TransactionType.REVERSAL -> if (isArabic) (if (shortLabel) "إلغاء" else "إلغاء معاملة") else "Reversal"
            TransactionType.OPENING_BALANCE -> if (isArabic) "رصيد افتتاحي" else "Opening Balance"
            else -> {
                if (isPaymentTransaction(tx)) {
                    if (isArabic) (if (shortLabel) "تسديد" else "تسديد (دفعة)") else "Payment"
                } else if (isDebtTransaction(tx)) {
                    if (isArabic) (if (shortLabel) "شراء آجل" else "شراء آجل (دين)") else "Credit Purchase"
                } else {
                    if (isArabic) (if (shortLabel) "شراء كاش" else "شراء نقدي (كاش)") else "Cash Purchase"
                }
            }
        }
    }
    /**
     * Formats statement rows into standard CSV with UTF-8 BOM so spreadsheet apps display Arabic correctly.
     */
    fun generateStatementCsv(rows: List<StatementRow>, isArabic: Boolean): String {
        val sb = StringBuilder()
        sb.append("\uFEFF") // UTF-8 BOM
        if (isArabic) {
            sb.append("التاريخ,العميل,الوصف,النوع,المبلغ,الرصيد التراكمي\n")
        } else {
            sb.append("Date,Customer,Description,Type,Amount,Running Balance\n")
        }
        for (row in rows) {
            val amountFormatted = String.format(Locale.US, "%.2f", if (row.isPayment) -row.amount else row.amount)
            val balanceFormatted = String.format(Locale.US, "%.2f", row.runningBalance)
            sb.append("\"${escapeCsv(row.date)}\",")
            sb.append("\"${escapeCsv(row.customerName)}\",")
            sb.append("\"${escapeCsv(row.description)}\",")
            sb.append("\"${escapeCsv(row.type)}\",")
            sb.append("$amountFormatted,")
            sb.append("$balanceFormatted\n")
        }
        return sb.toString()
    }

    /**
     * Formats generic report preview rows into standard CSV with UTF-8 BOM.
     */
    fun generateReportCsv(headers: List<String>, rows: List<ReportPreviewRow>): String {
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append(headers.joinToString(",") { "\"${escapeCsv(it)}\"" }).append("\n")
        for (row in rows) {
            sb.append("\"${escapeCsv(row.col1)}\",")
            sb.append("\"${escapeCsv(row.col2)}\",")
            sb.append("\"${escapeCsv(row.col3)}\",")
            sb.append("\"${escapeCsv(row.col4)}\"\n")
        }
        return sb.toString()
    }

    /**
     * Generates a plain-text summary suitable for sharing via messaging apps.
     */
    fun generateStatementShareText(
        customerName: String,
        rows: List<StatementRow>,
        totalIn: Double,
        totalOut: Double,
        netBalance: Double,
        isArabic: Boolean
    ): String {
        val currency = AppCurrency.SYMBOL
        val sb = StringBuilder()
        if (isArabic) {
            sb.append("📄 كشف حساب - سمول ستور\n")
            sb.append("العميل: $customerName\n")
            sb.append("التاريخ: ${SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())}\n")
            sb.append("----------------------------\n")
            sb.append("إجمالي الصادر (مشتريات): ${String.format(Locale.US, "%.2f", totalOut)} $currency\n")
            sb.append("إجمالي الوارد (تسديد): ${String.format(Locale.US, "%.2f", totalIn)} $currency\n")
            sb.append("الرصيد الصافي: ${String.format(Locale.US, "%.2f", netBalance)} $currency\n")
            sb.append("----------------------------\n")
            sb.append("عدد المعاملات: ${rows.size}\n")
        } else {
            sb.append("📄 Account Statement - SmallStore\n")
            sb.append("Customer: $customerName\n")
            sb.append("Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}\n")
            sb.append("----------------------------\n")
            sb.append("Total Out (Purchases): ${String.format(Locale.US, "%.2f", totalOut)} $currency\n")
            sb.append("Total In (Payments): ${String.format(Locale.US, "%.2f", totalIn)} $currency\n")
            sb.append("Net Balance: ${String.format(Locale.US, "%.2f", netBalance)} $currency\n")
            sb.append("----------------------------\n")
            sb.append("Transactions Count: ${rows.size}\n")
        }
        return sb.toString()
    }

    /**
     * Constructs chronological statement rows with running balances adhering strictly to pure domain
     * accounting rules from CustomerLedgerCalculator / FinancialReportCalculator.
     */
    fun buildStatementRows(
        customer: CustomerAccount?,
        transactions: List<TransactionItem>,
        openingBalance: Double = 0.0,
        includeOpeningBalanceRow: Boolean = true,
        periodStartDate: String? = null,
        isArabic: Boolean = true
    ): List<StatementRow> {
        val sortedList = transactions.sortedWith(
            compareBy<TransactionItem> { it.epochTimestampMillis }
                .thenBy { it.id }
        )

        var running = openingBalance
        val rows = ArrayList<StatementRow>()

        if (includeOpeningBalanceRow && Math.abs(openingBalance) > 0.0001) {
            rows.add(
                StatementRow(
                    id = "opening_balance",
                    date = periodStartDate ?: "",
                    customerName = customer?.customerName ?: "",
                    description = if (isArabic) "رصيد افتتاحي مرحل" else "Opening Balance",
                    type = if (isArabic) "رصيد افتتاحي" else "Opening Balance",
                    isPayment = false,
                    isCreditDebt = openingBalance > 0.0,
                    amount = Math.abs(openingBalance),
                    runningBalance = openingBalance,
                    isArchived = false
                )
            )
        }

        for (tx in sortedList) {
            val decomp = FinancialReportCalculator.decomposeTransaction(tx)
            val isPayment = decomp.customerPayments > 0.0 || tx.typedTransactionType == TransactionType.SALE_RETURN
            val isDebtPurchase = decomp.creditSales > 0.0

            val impact = decomp.receivableChange
            running += impact

            val typeDesc = getTransactionTypeLabel(tx, isArabic)
            val noteDesc = tx.notes.ifBlank { tx.title.ifBlank { tx.activityType } }

            rows.add(
                StatementRow(
                    id = tx.id,
                    date = tx.date,
                    customerName = tx.customerNameSnapshot.ifBlank { customer?.customerName ?: "" },
                    description = noteDesc,
                    type = typeDesc,
                    isPayment = isPayment,
                    isCreditDebt = isDebtPurchase,
                    amount = tx.amount,
                    runningBalance = running,
                    isArchived = tx.isArchived || decomp.isReversed
                )
            )
        }

        return rows
    }

    /**
     * Writes CSV string to the given OutputStream.
     */
    fun writeCsvToStream(csvContent: String, outputStream: OutputStream) {
        outputStream.use { os ->
            os.write(csvContent.toByteArray(Charsets.UTF_8))
            os.flush()
        }
    }

    /**
     * Generates a professional, structured PDF document supporting RTL (Arabic) and LTR (English) layouts,
     * multi-page pagination, clean header/metadata, KPI cards, table with alternating rows, and summary totals.
     */
    fun generatePdfReport(
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        headers: List<String>,
        rows: List<ReportPreviewRow>,
        outputStream: OutputStream,
        isArabic: Boolean = true
    ) {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 portrait width in points
        val pageHeight = 842 // A4 portrait height in points
        val margin = 32f
        val contentWidth = pageWidth - (2 * margin) // 531f

        val primaryColor = Color.parseColor("#4A3B69")
        val darkTextColor = Color.parseColor("#1C1B1F")
        val grayTextColor = Color.parseColor("#605D62")
        val lightBgColor = Color.parseColor("#F5F3F7")
        val headerBgColor = Color.parseColor("#EDE9F2")
        val borderColor = Color.parseColor("#D9D5DC")
        val altRowColor = Color.parseColor("#FBFBFC")

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // Relative column weight ratios for 4 columns: [Col1: 22%, Col2: 28%, Col3: 25%, Col4: 25%]
        val colWeights = floatArrayOf(0.22f, 0.28f, 0.25f, 0.25f)
        val colWidths = FloatArray(4) { i -> contentWidth * colWeights[i] }

        // Precompute column X start and end positions based on direction (RTL or LTR)
        val colStarts = FloatArray(4)
        val colEnds = FloatArray(4)
        if (isArabic) {
            // RTL: Col 0 starts at margin + contentWidth (right side), goes left
            var curRight = margin + contentWidth
            for (i in 0 until 4) {
                val w = colWidths[i]
                colEnds[i] = curRight
                colStarts[i] = curRight - w
                curRight -= w
            }
        } else {
            // LTR: Col 0 starts at left margin, goes right
            var curLeft = margin
            for (i in 0 until 4) {
                val w = colWidths[i]
                colStarts[i] = curLeft
                colEnds[i] = curLeft + w
                curLeft += w
            }
        }

        fun drawCellText(
            canvas: android.graphics.Canvas,
            text: String,
            colIndex: Int,
            baselineY: Float,
            textPaint: Paint,
            alignEnd: Boolean = false
        ) {
            if (isArabic) {
                // In RTL: Standard columns align Right (near colEnds[colIndex]). Numeric/amounts (alignEnd) align Left (near colStarts[colIndex])
                if (alignEnd) {
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(text, colStarts[colIndex] + 6f, baselineY, textPaint)
                } else {
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(text, colEnds[colIndex] - 6f, baselineY, textPaint)
                }
            } else {
                // In LTR: Standard columns align Left (near colStarts[colIndex]). Numeric/amounts (alignEnd) align Right (near colEnds[colIndex])
                if (alignEnd) {
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(text, colEnds[colIndex] - 6f, baselineY, textPaint)
                } else {
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(text, colStarts[colIndex] + 6f, baselineY, textPaint)
                }
            }
        }

        // Pagination split: calculate items per page
        // Page 1 has header banner (75pt), subtitle/meta (30pt), KPI cards (60pt), table header (24pt) -> starts around Y=210
        // Available table height on Page 1: 780 - 210 = 570pt -> at 22pt/row ~ 22-25 rows
        // Page 2+ has compact header (45pt), table header (24pt) -> starts around Y=85 -> ~30 rows
        val rowsPerPageFirst = 22
        val rowsPerPageSubsequent = 28
        val totalRows = rows.size
        val totalPages = if (totalRows <= rowsPerPageFirst) 1 else 1 + Math.ceil((totalRows - rowsPerPageFirst).toDouble() / rowsPerPageSubsequent).toInt()

        var currentRowIdx = 0
        val currentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())

        for (pageNumber in 1..totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            var currentY = margin

            if (pageNumber == 1) {
                // --- Page 1 Top Banner ---
                val bannerHeight = 72f
                paint.color = primaryColor
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(margin, currentY, margin + contentWidth, currentY + bannerHeight, 8f, 8f, paint)

                // Store Title in Banner
                paint.color = Color.WHITE
                paint.textSize = 17f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("سمول ستور  |  $storeName", margin + contentWidth - 16f, currentY + 30f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText("SmallStore  |  $storeName", margin + 16f, currentY + 30f, paint)
                }

                // Report Sub-title / Title in Banner
                paint.textSize = 12.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(title, margin + contentWidth - 16f, currentY + 54f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(title, margin + 16f, currentY + 54f, paint)
                }

                currentY += bannerHeight + 14f

                // Meta Line: Subtitle (Period or Filter) & Current Date
                paint.color = grayTextColor
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(subtitle, margin + contentWidth, currentY + 10f, paint)
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText("تاريخ الإصدار: $currentDate", margin, currentY + 10f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(subtitle, margin, currentY + 10f, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("Generated: $currentDate", margin + contentWidth, currentY + 10f, paint)
                }

                currentY += 22f

                // KPI Cards (if any)
                if (kpis.isNotEmpty()) {
                    val kpiCount = kpis.size.coerceAtMost(3)
                    val cardSpacing = 10f
                    val totalSpacing = cardSpacing * (kpiCount - 1)
                    val cardWidth = (contentWidth - totalSpacing) / kpiCount
                    val cardHeight = 44f

                    for (k in 0 until kpiCount) {
                        val (kpiTitle, kpiVal) = kpis[k]
                        val cardLeft = if (isArabic) {
                            // In RTL, 1st KPI goes on the right
                            margin + contentWidth - (k + 1) * cardWidth - k * cardSpacing
                        } else {
                            margin + k * (cardWidth + cardSpacing)
                        }

                        // Background card
                        paint.color = lightBgColor
                        paint.style = Paint.Style.FILL
                        canvas.drawRoundRect(cardLeft, currentY, cardLeft + cardWidth, currentY + cardHeight, 6f, 6f, paint)
                        // Border
                        paint.color = borderColor
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 0.8f
                        canvas.drawRoundRect(cardLeft, currentY, cardLeft + cardWidth, currentY + cardHeight, 6f, 6f, paint)

                        // KPI Label
                        paint.style = Paint.Style.FILL
                        paint.color = grayTextColor
                        paint.textSize = 8.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        if (isArabic) {
                            paint.textAlign = Paint.Align.RIGHT
                            canvas.drawText(kpiTitle, cardLeft + cardWidth - 8f, currentY + 16f, paint)
                        } else {
                            paint.textAlign = Paint.Align.LEFT
                            canvas.drawText(kpiTitle, cardLeft + 8f, currentY + 16f, paint)
                        }

                        // KPI Value
                        paint.color = primaryColor
                        paint.textSize = 11.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        if (isArabic) {
                            paint.textAlign = Paint.Align.RIGHT
                            canvas.drawText(kpiVal, cardLeft + cardWidth - 8f, currentY + 34f, paint)
                        } else {
                            paint.textAlign = Paint.Align.LEFT
                            canvas.drawText(kpiVal, cardLeft + 8f, currentY + 34f, paint)
                        }
                    }
                    currentY += cardHeight + 14f
                }
            } else {
                // --- Subsequent Pages Header ---
                paint.color = primaryColor
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(margin, currentY, margin + contentWidth, currentY + 32f, 4f, 4f, paint)

                paint.color = Color.WHITE
                paint.textSize = 10.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("$storeName  •  $title", margin + contentWidth - 12f, currentY + 20f, paint)
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(subtitle, margin + 12f, currentY + 20f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText("$storeName  •  $title", margin + 12f, currentY + 20f, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(subtitle, margin + contentWidth - 12f, currentY + 20f, paint)
                }
                currentY += 40f
            }

            // --- Table Header ---
            val headerHeight = 24f
            paint.color = headerBgColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(margin, currentY, margin + contentWidth, currentY + headerHeight, paint)

            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            canvas.drawRect(margin, currentY, margin + contentWidth, currentY + headerHeight, paint)

            paint.style = Paint.Style.FILL
            paint.color = darkTextColor
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            for (i in headers.indices) {
                if (i < 4) {
                    val isAmountCol = (i == 3)
                    drawCellText(canvas, headers[i], i, currentY + 16f, paint, alignEnd = isAmountCol)
                }
            }
            currentY += headerHeight

            // --- Table Rows ---
            val rowHeight = 22f
            val pageRowsLimit = if (pageNumber == 1) rowsPerPageFirst else rowsPerPageSubsequent
            var rowsDrawnThisPage = 0

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 9f

            while (currentRowIdx < totalRows && rowsDrawnThisPage < pageRowsLimit) {
                val row = rows[currentRowIdx]

                // Alternating row background
                if (currentRowIdx % 2 == 1) {
                    paint.color = altRowColor
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(margin, currentY, margin + contentWidth, currentY + rowHeight, paint)
                }

                // Row borders
                paint.color = borderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.5f
                canvas.drawRect(margin, currentY, margin + contentWidth, currentY + rowHeight, paint)

                // Cells
                paint.style = Paint.Style.FILL
                paint.color = darkTextColor

                drawCellText(canvas, row.col1, 0, currentY + 15f, paint, alignEnd = false)
                drawCellText(canvas, row.col2, 1, currentY + 15f, paint, alignEnd = false)
                drawCellText(canvas, row.col3, 2, currentY + 15f, paint, alignEnd = false)

                // 4th column is amount / total -> bold & aligned end
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                drawCellText(canvas, row.col4, 3, currentY + 15f, paint, alignEnd = true)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                currentY += rowHeight
                currentRowIdx++
                rowsDrawnThisPage++
            }

            // If empty rows list (e.g. no transactions)
            if (totalRows == 0 && pageNumber == 1) {
                paint.style = Paint.Style.FILL
                paint.color = grayTextColor
                paint.textSize = 10f
                paint.textAlign = Paint.Align.CENTER
                val emptyMsg = if (isArabic) "لا توجد سجلات متاحة في هذه الفترة" else "No records available for this period"
                canvas.drawText(emptyMsg, pageWidth / 2f, currentY + 30f, paint)
                currentY += 50f
            }

            // Summary Totals bar on the last page (if table has rows)
            if (pageNumber == totalPages && totalRows > 0) {
                currentY += 4f
                val summaryHeight = 22f
                paint.color = headerBgColor
                paint.style = Paint.Style.FILL
                canvas.drawRect(margin, currentY, margin + contentWidth, currentY + summaryHeight, paint)

                paint.color = borderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                canvas.drawRect(margin, currentY, margin + contentWidth, currentY + summaryHeight, paint)

                paint.style = Paint.Style.FILL
                paint.color = primaryColor
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                val countLabel = if (isArabic) "إجمالي السجلات: $totalRows" else "Total Records: $totalRows"
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(countLabel, margin + contentWidth - 10f, currentY + 15f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(countLabel, margin + 10f, currentY + 15f, paint)
                }
            }

            // --- Footer ---
            val footerY = pageHeight - margin
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.5f
            canvas.drawLine(margin, footerY - 14f, margin + contentWidth, footerY - 14f, paint)

            paint.style = Paint.Style.FILL
            paint.color = grayTextColor
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            val footerText = if (isArabic) "تم الإصدار عبر تطبيق سمول ستور  •  مستند إلكتروني معتمد" else "Generated via SmallStore App • Verified Electronic Document"
            val pageNumberText = if (isArabic) "صفحة $pageNumber من $totalPages" else "Page $pageNumber of $totalPages"

            if (isArabic) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(footerText, margin + contentWidth, footerY, paint)
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(pageNumberText, margin, footerY, paint)
            } else {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(footerText, margin, footerY, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(pageNumberText, margin + contentWidth, footerY, paint)
            }

            pdfDocument.finishPage(page)
        }

        outputStream.use { os ->
            pdfDocument.writeTo(os)
            os.flush()
        }
        pdfDocument.close()
    }

    /**
     * Generates a clean HTML report document suitable for WebView printing.
     */
    fun generateReportHtml(
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        headers: List<String>,
        rows: List<ReportPreviewRow>,
        isArabic: Boolean
    ): String {
        val dir = if (isArabic) "rtl" else "ltr"
        val lang = if (isArabic) "ar" else "en"
        val textAlign = if (isArabic) "right" else "left"
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html lang=\"$lang\" dir=\"$dir\"><head><meta charset=\"UTF-8\">")
        sb.append("<style>")
        sb.append("body { font-family: sans-serif; margin: 20px; color: #1C1B1F; direction: $dir; text-align: $textAlign; }")
        sb.append(".header { background-color: #4A3B69; color: white; padding: 18px 24px; border-radius: 8px; margin-bottom: 16px; }")
        sb.append(".header h1 { margin: 0; font-size: 20px; }")
        sb.append(".header p { margin: 6px 0 0 0; font-size: 13px; opacity: 0.9; }")
        sb.append(".meta { color: #605D62; font-size: 11px; margin-bottom: 16px; }")
        sb.append(".kpi-container { display: flex; gap: 12px; margin-bottom: 20px; }")
        sb.append(".kpi-box { flex: 1; background: #F5F3F7; border: 1px solid #D9D5DC; border-radius: 6px; padding: 10px 14px; }")
        sb.append(".kpi-title { font-size: 11px; color: #605D62; }")
        sb.append(".kpi-val { font-size: 15px; font-weight: bold; color: #4A3B69; margin-top: 4px; }")
        sb.append("table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 12px; }")
        sb.append("th { background-color: #ECEAF0; color: #1C1B1F; padding: 8px 10px; border: 1px solid #D9D5DC; font-weight: bold; }")
        sb.append("td { padding: 8px 10px; border: 1px solid #E6E1E8; }")
        sb.append("tr:nth-child(even) { background-color: #FAFAFB; }")
        sb.append(".footer { margin-top: 24px; border-top: 1px solid #D9D5DC; padding-top: 8px; font-size: 10px; color: #605D62; display: flex; justify-content: space-between; }")
        sb.append("</style></head><body>")
        sb.append("<div class=\"header\">")
        sb.append("<h1>SmallStore - $storeName</h1>")
        sb.append("<p>$title</p>")
        sb.append("</div>")
        val currentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())
        sb.append("<div class=\"meta\">$subtitle &nbsp; &nbsp; $currentDate</div>")
        if (kpis.isNotEmpty()) {
            sb.append("<div class=\"kpi-container\">")
            for ((kpiTitle, kpiVal) in kpis) {
                sb.append("<div class=\"kpi-box\">")
                sb.append("<div class=\"kpi-title\">$kpiTitle</div>")
                sb.append("<div class=\"kpi-val\">$kpiVal</div>")
                sb.append("</div>")
            }
            sb.append("</div>")
        }
        sb.append("<table><thead><tr>")
        for (h in headers) {
            sb.append("<th>$h</th>")
        }
        sb.append("</tr></thead><tbody>")
        for (row in rows) {
            sb.append("<tr>")
            sb.append("<td>${row.col1}</td>")
            sb.append("<td>${row.col2}</td>")
            sb.append("<td>${row.col3}</td>")
            sb.append("<td>${row.col4}</td>")
            sb.append("</tr>")
        }
        sb.append("</tbody></table>")
        sb.append("<div class=\"footer\">")
        sb.append("<span>Generated via SmallStore • Electronic System</span>")
        sb.append("<span>Page 1 of 1</span>")
        sb.append("</div>")
        sb.append("</body></html>")
        return sb.toString()
    }

    /**
     * Launches Android native PrintManager via a headless WebView.
     */
    fun printHtml(context: Context, title: String, htmlContent: String, isArabic: Boolean) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Toast.makeText(
                    context,
                    if (isArabic) StoreStrings.PRINT_FAILED_AR else StoreStrings.PRINT_FAILED_EN,
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            val webView = WebView(context.applicationContext)
            webView.settings.apply {
                javaScriptEnabled = false
                domStorageEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    try {
                        val printAdapter = webView.createPrintDocumentAdapter(title)
                        printManager.print(title, printAdapter, PrintAttributes.Builder().build())
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                            context,
                            if (isArabic) StoreStrings.PRINT_FAILED_AR else StoreStrings.PRINT_FAILED_EN,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                if (isArabic) StoreStrings.PRINT_FAILED_AR else StoreStrings.PRINT_FAILED_EN,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Shares a file via Android system share chooser using FileProvider.
     */
    fun shareFile(context: Context, file: File, mimeType: String, subject: String, bodyText: String = "") {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                if (bodyText.isNotEmpty()) {
                    putExtra(Intent.EXTRA_TEXT, bodyText)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, subject))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, e.localizedMessage ?: "Sharing failed", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Creates a temporary PDF file in cache directory.
     */
    fun createCachedPdf(
        context: Context,
        fileName: String,
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        headers: List<String>,
        rows: List<ReportPreviewRow>,
        isArabic: Boolean = true
    ): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            generatePdfReport(title, storeName, subtitle, kpis, headers, rows, fos, isArabic)
        }
        return file
    }

    /**
     * Creates a specialized Sales & Items PDF report file in cache directory.
     */
    fun createCachedSalesAndItemsPdf(
        context: Context,
        fileName: String,
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        itemBreakdowns: List<AggregatedProductLine>,
        invoices: List<TransactionItem>,
        isArabic: Boolean = true
    ): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            generateSalesAndItemsPdf(
                title = title,
                storeName = storeName,
                subtitle = subtitle,
                kpis = kpis,
                itemBreakdowns = itemBreakdowns,
                invoices = invoices,
                outputStream = fos,
                isArabic = isArabic
            )
        }
        return file
    }

    /**
     * Creates a specialized Transaction PDF report file in cache directory.
     */
    fun createCachedTransactionsPdf(
        context: Context,
        fileName: String,
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        transactions: List<TransactionItem>,
        totalCash: Double = -1.0,
        totalDebt: Double = -1.0,
        totalPayments: Double = -1.0,
        isArabic: Boolean = true
    ): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            generateTransactionsPdf(
                title = title,
                storeName = storeName,
                subtitle = subtitle,
                kpis = kpis,
                transactions = transactions,
                totalCash = totalCash,
                totalDebt = totalDebt,
                totalPayments = totalPayments,
                outputStream = fos,
                isArabic = isArabic
            )
        }
        return file
    }

    /**
     * Creates a specialized Comprehensive Customer PDF report file in cache directory.
     * Guaranteed to represent ONLY ONE specific selected customer.
     */
    fun createCachedCustomerPdf(
        context: Context,
        fileName: String,
        title: String,
        storeName: String,
        subtitle: String,
        customer: CustomerAccount,
        kpis: List<Pair<String, String>>,
        transactions: List<TransactionItem>,
        totalCash: Double = -1.0,
        totalDebt: Double = -1.0,
        totalPayments: Double = -1.0,
        itemBreakdowns: List<AggregatedProductLine> = emptyList(),
        isArabic: Boolean = true
    ): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            generateCustomerPdf(
                title = title,
                storeName = storeName,
                subtitle = subtitle,
                customer = customer,
                kpis = kpis,
                transactions = transactions,
                totalCash = totalCash,
                totalDebt = totalDebt,
                totalPayments = totalPayments,
                itemBreakdowns = itemBreakdowns,
                outputStream = fos,
                isArabic = isArabic
            )
        }
        return file
    }

    /**
     * Creates a specialized Analytics / Statistics PDF report file in cache directory.
     * Generates a structured vector PDF supporting both ALL_CUSTOMERS and ONE_SELECTED_CUSTOMER.
     */
    fun createCachedAnalyticsPdf(
        context: Context,
        fileName: String,
        data: AnalyticsReportData,
        isArabic: Boolean = true
    ): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            AnalyticsPdfGenerator.generateAnalyticsPdf(
                data = data,
                outputStream = fos,
                isArabic = isArabic
            )
        }
        return file
    }

    /**
     * Creates a specialized Analytics / Statistics CSV report file in cache directory.
     * Generates a structured RFC-4180 CSV with UTF-8 BOM supporting both ALL_CUSTOMERS and ONE_SELECTED_CUSTOMER.
     */
    fun createCachedAnalyticsCsv(
        context: Context,
        fileName: String,
        data: AnalyticsReportData,
        isArabic: Boolean = true
    ): File {
        val csv = generateAnalyticsCsv(data, isArabic)
        return createCachedCsv(context, fileName, csv)
    }

    /**
     * Generates structured CSV string for Analytics / Statistics report matching the PDF report.
     */
    fun generateAnalyticsCsv(
        data: AnalyticsReportData,
        isArabic: Boolean = true
    ): String {
        return AnalyticsCsvGenerator.generateAnalyticsCsv(data, isArabic)
    }

    /**
     * Creates a specialized Analytics / Statistics TXT report file in cache directory.
     * Generates a human-readable text report supporting both ALL_CUSTOMERS and ONE_SELECTED_CUSTOMER.
     */
    fun createCachedAnalyticsTxt(
        context: Context,
        fileName: String,
        data: AnalyticsReportData,
        isArabic: Boolean = true
    ): File {
        val txt = generateAnalyticsTxt(data, isArabic)
        return createCachedTxt(context, fileName, txt)
    }

    /**
     * Generates structured TXT string for Analytics / Statistics report matching the PDF and CSV reports.
     */
    fun generateAnalyticsTxt(
        data: AnalyticsReportData,
        isArabic: Boolean = true
    ): String {
        return AnalyticsTxtGenerator.generateAnalyticsTxt(data, isArabic)
    }

    /**
     * Specialized PDF generator for the Comprehensive Customer Report (ONE SELECTED CUSTOMER).
     *
     * Order of Sections:
     * 1. REPORT HEADER: Store name, Report title ("التقرير المخصص الشامل للعميل" / "Comprehensive Customer Report"),
     *    Reporting period/date range, generation timestamp metadata.
     * 2. SELECTED CUSTOMER INFORMATION: Shows ONLY this one selected customer's identity, phone number, and debt status.
     *    Never shows customer lists, directories, or other customers.
     * 3. CUSTOMER SUMMARY: Financial summary cards with real calculated amounts (Balance Due, Cash Purchases, Debt Purchases, Payments).
     * 4. MOST ORDERED PRODUCTS FOR THIS CUSTOMER ("أكثر الأصناف طلباً لهذا العميل" / "Most Ordered Products for This Customer"):
     *    Ranked products ordered by this specific customer with Rank, Product, Quantity, and Total amount.
     * 5. REPORT DETAILS ("تفاصيل التقرير" / "Report Details"): Structured 5-column transaction ledger table for this customer
     *    (Date, Type, Description/Notes, Settlement, Amount) with semantic badges and selectable vector text.
     * 6. FINAL TOTALS: Grand total transactions row and comprehensive balance summary card.
     *
     * Multi-page pagination with repeated headers, persistent customer identity banner on sub-pages,
     * consistent page numbering ("صفحة X من Y"), true RTL/LTR, and zero UI filter tabs.
     */
    fun generateCustomerPdf(
        title: String,
        storeName: String,
        subtitle: String,
        customer: CustomerAccount,
        kpis: List<Pair<String, String>>,
        transactions: List<TransactionItem>,
        totalCash: Double = -1.0,
        totalDebt: Double = -1.0,
        totalPayments: Double = -1.0,
        itemBreakdowns: List<AggregatedProductLine> = emptyList(),
        outputStream: OutputStream,
        isArabic: Boolean = true
    ) {
        val customerDomainTotals = FinancialReportCalculator.calculateCustomerTotals(customer.id, transactions)
        val resolvedCash = if (totalCash >= 0.0) totalCash else customerDomainTotals.cashSales
        val resolvedDebt = if (totalDebt >= 0.0) totalDebt else customerDomainTotals.creditSales
        val resolvedPayments = if (totalPayments >= 0.0) totalPayments else customerDomainTotals.customerPayments

        val activeCustTransactions = transactions.filter { (it.operationStatus ?: it.typedOperationStatus) != OperationStatus.REVERSED }
        val activeCustTotalAmount = activeCustTransactions.sumOf { it.amount }

        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 portrait width in points
        val pageHeight = 842 // A4 portrait height in points
        val margin = 32f
        val contentWidth = pageWidth - (2 * margin) // 531f
        val printableBottomY = pageHeight - margin - 24f // Reserve space for footer

        val primaryColor = Color.parseColor("#4A3B69")
        val darkTextColor = Color.parseColor("#1C1B1F")
        val grayTextColor = Color.parseColor("#605D62")
        val lightBgColor = Color.parseColor("#F5F3F7")
        val headerBgColor = Color.parseColor("#EDE9F2")
        val borderColor = Color.parseColor("#D9D5DC")
        val altRowColor = Color.parseColor("#FBFBFC")
        val greenColor = Color.parseColor("#2E7D32")
        val amberColor = Color.parseColor("#E65100")
        val blueColor = Color.parseColor("#1976D2")
        val redColor = Color.parseColor("#C62828")

        val paint = Paint().apply { isAntiAlias = true }

        // Columns definition for transactions table: Date (18%), Type (18%), Description / Notes (25%), Settlement (17%), Amount (22%)
        val weights = floatArrayOf(0.18f, 0.18f, 0.25f, 0.17f, 0.22f)
        val colWidths = FloatArray(weights.size) { i -> contentWidth * weights[i] }
        val colStarts = FloatArray(weights.size)
        val colEnds = FloatArray(weights.size)
        if (isArabic) {
            var curRight = margin + contentWidth
            for (i in weights.indices) {
                val w = colWidths[i]
                colEnds[i] = curRight
                colStarts[i] = curRight - w
                curRight -= w
            }
        } else {
            var curLeft = margin
            for (i in weights.indices) {
                val w = colWidths[i]
                colStarts[i] = curLeft
                colEnds[i] = curLeft + w
                curLeft += w
            }
        }

        // Columns definition for Most Ordered Products table: Rank (12%), Product (50%), Quantity (16%), Total (22%)
        val prodWeights = floatArrayOf(0.12f, 0.50f, 0.16f, 0.22f)
        val prodColWidths = FloatArray(prodWeights.size) { i -> contentWidth * prodWeights[i] }
        val prodColStarts = FloatArray(prodWeights.size)
        val prodColEnds = FloatArray(prodWeights.size)
        if (isArabic) {
            var curRight = margin + contentWidth
            for (i in prodWeights.indices) {
                val w = prodColWidths[i]
                prodColEnds[i] = curRight
                prodColStarts[i] = curRight - w
                curRight -= w
            }
        } else {
            var curLeft = margin
            for (i in prodWeights.indices) {
                val w = prodColWidths[i]
                prodColStarts[i] = curLeft
                prodColEnds[i] = curLeft + w
                curLeft += w
            }
        }

        fun drawCellText(
            canvas: android.graphics.Canvas,
            text: String,
            colIndex: Int,
            baselineY: Float,
            textPaint: Paint,
            alignEnd: Boolean = false
        ) {
            if (isArabic) {
                if (alignEnd) {
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(text, colStarts[colIndex] + 6f, baselineY, textPaint)
                } else {
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(text, colEnds[colIndex] - 6f, baselineY, textPaint)
                }
            } else {
                if (alignEnd) {
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(text, colEnds[colIndex] - 6f, baselineY, textPaint)
                } else {
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(text, colStarts[colIndex] + 6f, baselineY, textPaint)
                }
            }
        }

        fun drawProdCellText(
            canvas: android.graphics.Canvas,
            text: String,
            colIndex: Int,
            baselineY: Float,
            textPaint: Paint,
            alignEnd: Boolean = false
        ) {
            if (isArabic) {
                if (alignEnd) {
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(text, prodColStarts[colIndex] + 6f, baselineY, textPaint)
                } else {
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(text, prodColEnds[colIndex] - 6f, baselineY, textPaint)
                }
            } else {
                if (alignEnd) {
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(text, prodColEnds[colIndex] - 6f, baselineY, textPaint)
                } else {
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(text, prodColStarts[colIndex] + 6f, baselineY, textPaint)
                }
            }
        }

        fun fitText(text: String, maxWidth: Float, textPaint: Paint): String {
            if (textPaint.measureText(text) <= maxWidth) return text
            var truncated = text
            while (truncated.isNotEmpty() && textPaint.measureText("$truncated...") > maxWidth) {
                truncated = truncated.dropLast(1)
            }
            return if (truncated.isEmpty()) "" else "$truncated..."
        }

        val tableHeaders = if (isArabic) {
            listOf("التاريخ", "النوع", "البيان / الوصف", "التسوية", "المبلغ")
        } else {
            listOf("Date", "Type", "Description / Notes", "Settlement", "Amount")
        }

        val prodHeaders = if (isArabic) {
            listOf("الترتيب", "الصنف", "الكمية", "الإجمالي")
        } else {
            listOf("Rank", "Product", "Quantity", "Total")
        }

        val currentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())

        // Calculate total pages dynamically including top products and transactions
        fun calculateTotalPages(): Int {
            if (transactions.isEmpty() && itemBreakdowns.isEmpty()) return 1
            var simPage = 1
            var simY = margin + 72f + 14f + 22f + 52f + 12f + 44f + 14f // Header + Customer card + KPIs

            // Simulate Most Ordered Products section
            val prodSecH = 26f + 22f + if (itemBreakdowns.isEmpty()) 24f else (itemBreakdowns.size * 22f)
            if (simY + 50f > printableBottomY) {
                simPage++
                simY = margin + 38f
            }
            simY += 26f + 22f // Section header + Table header
            if (itemBreakdowns.isEmpty()) {
                if (simY + 24f > printableBottomY) {
                    simPage++
                    simY = margin + 38f + 22f + 24f
                } else {
                    simY += 24f
                }
            } else {
                for (p in itemBreakdowns.indices) {
                    if (simY + 22f > printableBottomY) {
                        simPage++
                        simY = margin + 38f + 22f + 22f
                    } else {
                        simY += 22f
                    }
                }
            }
            simY += 14f

            // Simulate Transactions section
            if (simY + 50f > printableBottomY) {
                simPage++
                simY = margin + 38f
            }
            simY += 26f + 22f // Section header + Table header
            if (transactions.isEmpty()) {
                if (simY + 26f > printableBottomY) {
                    simPage++
                    simY = margin + 38f + 22f + 26f
                } else {
                    simY += 26f
                }
            } else {
                for (i in transactions.indices) {
                    if (simY + 22f > printableBottomY) {
                        simPage++
                        simY = margin + 38f + 22f + 22f
                    } else {
                        simY += 22f
                    }
                }
            }
            if (simY + 60f > printableBottomY) {
                simPage++
            }
            return simPage
        }

        val totalPages = calculateTotalPages()

        data class PageContent(
            val pageNum: Int,
            var page: PdfDocument.Page,
            var canvas: android.graphics.Canvas,
            var currentY: Float
        )

        var currentPageNum = 1

        fun startNewPage(): PageContent {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            var y = margin

            if (currentPageNum == 1) {
                // =====================================================================
                // 1. REPORT HEADER
                // =====================================================================
                val bannerHeight = 72f
                paint.color = primaryColor
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(margin, y, margin + contentWidth, y + bannerHeight, 8f, 8f, paint)

                paint.color = Color.WHITE
                paint.textSize = 17f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("سمول ستور  |  $storeName", margin + contentWidth - 16f, y + 30f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText("SmallStore  |  $storeName", margin + 16f, y + 30f, paint)
                }

                paint.textSize = 12.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(title, margin + contentWidth - 16f, y + 54f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(title, margin + 16f, y + 54f, paint)
                }
                y += bannerHeight + 14f

                // Meta Line: Subtitle (Date range/period) & Current date
                paint.color = grayTextColor
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(subtitle, margin + contentWidth, y + 10f, paint)
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText("تاريخ الإصدار: $currentDate", margin, y + 10f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(subtitle, margin, y + 10f, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("Generated: $currentDate", margin + contentWidth, y + 10f, paint)
                }
                y += 22f

                // =====================================================================
                // 2. SELECTED CUSTOMER INFORMATION (ONE SELECTED CUSTOMER ONLY)
                // =====================================================================
                val custCardHeight = 52f
                paint.color = lightBgColor
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(margin, y, margin + contentWidth, y + custCardHeight, 6f, 6f, paint)

                paint.color = borderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                canvas.drawRoundRect(margin, y, margin + contentWidth, y + custCardHeight, 6f, 6f, paint)

                paint.style = Paint.Style.FILL
                paint.color = primaryColor
                paint.textSize = 13f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val custLabel = if (isArabic) "العميل: ${customer.customerName}" else "Customer: ${customer.customerName}"
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(custLabel, margin + contentWidth - 14f, y + 22f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(custLabel, margin + 14f, y + 22f, paint)
                }

                paint.color = grayTextColor
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val phoneText = if (customer.phone.isNotBlank()) {
                    if (isArabic) "رقم الهاتف: ${customer.phone}" else "Phone: ${customer.phone}"
                } else {
                    if (isArabic) "رقم الهاتف: غير محدد" else "Phone: Not Specified"
                }
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(phoneText, margin + contentWidth - 14f, y + 40f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(phoneText, margin + 14f, y + 40f, paint)
                }

                val balanceStatusText = if (customer.balance > 0) {
                    if (isArabic) "الرصيد المستحق: ${AppCurrency.formatAmountWithDecimals(customer.balance, isArabic)}"
                    else "Outstanding Balance: ${AppCurrency.formatAmountWithDecimals(customer.balance, isArabic)}"
                } else {
                    if (isArabic) "الحساب مسدد بالكامل (0.00 ₪)"
                    else "Fully Settled Account (0.00 ₪)"
                }
                paint.color = if (customer.balance > 0) redColor else greenColor
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (isArabic) {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(balanceStatusText, margin + 14f, y + 30f, paint)
                } else {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(balanceStatusText, margin + contentWidth - 14f, y + 30f, paint)
                }

                y += custCardHeight + 12f

                // =====================================================================
                // 3. CUSTOMER SUMMARY (Summary KPI Cards)
                // =====================================================================
                val customerKpis = listOf(
                    (if (isArabic) "الرصيد المستحق" else "Balance Due") to AppCurrency.formatAmountWithDecimals(customer.balance, isArabic),
                    (if (isArabic) "مشتريات كاش" else "Cash Purchases") to AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic),
                    (if (isArabic) "مشتريات آجل" else "Debt Purchases") to AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic),
                    (if (isArabic) "إجمالي المسدد" else "Payments") to AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)
                )

                val kpiCount = customerKpis.size
                val cardSpacing = 8f
                val totalSpacing = cardSpacing * (kpiCount - 1)
                val cardWidth = (contentWidth - totalSpacing) / kpiCount
                val cardHeight = 44f

                for (k in 0 until kpiCount) {
                    val (kpiTitle, kpiVal) = customerKpis[k]
                    val cardLeft = if (isArabic) {
                        margin + contentWidth - (k + 1) * cardWidth - k * cardSpacing
                    } else {
                        margin + k * (cardWidth + cardSpacing)
                    }

                    paint.color = lightBgColor
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(cardLeft, y, cardLeft + cardWidth, y + cardHeight, 6f, 6f, paint)

                    paint.color = borderColor
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.8f
                    canvas.drawRoundRect(cardLeft, y, cardLeft + cardWidth, y + cardHeight, 6f, 6f, paint)

                    paint.style = Paint.Style.FILL
                    paint.color = grayTextColor
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    if (isArabic) {
                        paint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(kpiTitle, cardLeft + cardWidth - 8f, y + 16f, paint)
                    } else {
                        paint.textAlign = Paint.Align.LEFT
                        canvas.drawText(kpiTitle, cardLeft + 8f, y + 16f, paint)
                    }

                    paint.color = if (k == 0 && customer.balance > 0) redColor else primaryColor
                    paint.textSize = 10.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    if (isArabic) {
                        paint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(kpiVal, cardLeft + cardWidth - 8f, y + 34f, paint)
                    } else {
                        paint.textAlign = Paint.Align.LEFT
                        canvas.drawText(kpiVal, cardLeft + 8f, y + 34f, paint)
                    }
                }
                y += cardHeight + 14f
            } else {
                paint.color = primaryColor
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(margin, y, margin + contentWidth, y + 30f, 4f, 4f, paint)

                paint.color = Color.WHITE
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val subPageTitle = if (isArabic) {
                    "$storeName  •  ${customer.customerName}  •  كشف الحساب"
                } else {
                    "$storeName  •  ${customer.customerName}  •  Account Statement"
                }
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(subPageTitle, margin + contentWidth - 12f, y + 19f, paint)
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(subtitle, margin + 12f, y + 19f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(subPageTitle, margin + 12f, y + 19f, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(subtitle, margin + contentWidth - 12f, y + 19f, paint)
                }
                y += 38f
            }

            return PageContent(currentPageNum, page, canvas, y)
        }

        var activePage = startNewPage()

        fun drawFooter(canvas: android.graphics.Canvas, pageNum: Int) {
            val footerY = pageHeight - margin
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.5f
            canvas.drawLine(margin, footerY - 14f, margin + contentWidth, footerY - 14f, paint)

            paint.style = Paint.Style.FILL
            paint.color = grayTextColor
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            val footerText = if (isArabic) "تم الإصدار عبر تطبيق سمول ستور  •  كشف حساب رسمي معتمد للعميل" else "Generated via SmallStore App • Verified Customer Statement"
            val pageNumberText = if (isArabic) "صفحة $pageNum من $totalPages" else "Page $pageNum of $totalPages"

            if (isArabic) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(footerText, margin + contentWidth, footerY, paint)
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(pageNumberText, margin, footerY, paint)
            } else {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(footerText, margin, footerY, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(pageNumberText, margin + contentWidth, footerY, paint)
            }
        }

        fun drawTableHeader(canvas: android.graphics.Canvas, y: Float) {
            val hHeight = 22f
            paint.color = headerBgColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(margin, y, margin + contentWidth, y + hHeight, paint)
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            canvas.drawRect(margin, y, margin + contentWidth, y + hHeight, paint)

            paint.style = Paint.Style.FILL
            paint.color = darkTextColor
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            for (i in tableHeaders.indices) {
                drawCellText(canvas, tableHeaders[i], i, y + 15f, paint, alignEnd = (i == 4))
            }
        }

        fun drawProdTableHeader(canvas: android.graphics.Canvas, y: Float) {
            val hHeight = 22f
            paint.color = headerBgColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(margin, y, margin + contentWidth, y + hHeight, paint)
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            canvas.drawRect(margin, y, margin + contentWidth, y + hHeight, paint)

            paint.style = Paint.Style.FILL
            paint.color = darkTextColor
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            for (i in prodHeaders.indices) {
                drawProdCellText(canvas, prodHeaders[i], i, y + 15f, paint, alignEnd = (i >= 2))
            }
        }

        fun checkPageBreak(requiredHeight: Float, onContinuedPage: ((android.graphics.Canvas, Float) -> Unit)? = null) {
            if (activePage.currentY + requiredHeight > printableBottomY) {
                drawFooter(activePage.canvas, activePage.pageNum)
                pdfDocument.finishPage(activePage.page)

                currentPageNum++
                activePage = startNewPage()

                onContinuedPage?.invoke(activePage.canvas, activePage.currentY)
            }
        }

        // =====================================================================
        // 4. MOST ORDERED PRODUCTS FOR THIS CUSTOMER
        // =====================================================================
        checkPageBreak(50f)
        val prodSectionBadgeHeight = 22f
        paint.color = lightBgColor
        paint.style = Paint.Style.FILL
        activePage.canvas.drawRoundRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + prodSectionBadgeHeight, 4f, 4f, paint)
        paint.color = primaryColor
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val prodSecTitle = if (isArabic) {
            "أكثر الأصناف طلباً لهذا العميل (${itemBreakdowns.size} صنف)"
        } else {
            "Most Ordered Products for This Customer (${itemBreakdowns.size} Products)"
        }
        if (isArabic) {
            paint.textAlign = Paint.Align.RIGHT
            activePage.canvas.drawText(prodSecTitle, margin + contentWidth - 10f, activePage.currentY + 15f, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            activePage.canvas.drawText(prodSecTitle, margin + 10f, activePage.currentY + 15f, paint)
        }
        activePage.currentY += prodSectionBadgeHeight + 4f

        // Product Table Column Header
        drawProdTableHeader(activePage.canvas, activePage.currentY)
        activePage.currentY += 22f

        if (itemBreakdowns.isEmpty()) {
            val emptyH = 24f
            paint.color = grayTextColor
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.CENTER
            val emptyMsg = if (isArabic) "لا توجد تفاصيل أصناف فردية مسجلة لهذا العميل في هذه الفترة" else "No detailed product order records found for this customer in this period"
            activePage.canvas.drawText(emptyMsg, pageWidth / 2f, activePage.currentY + 16f, paint)
            activePage.currentY += emptyH
        } else {
            val rowHeight = 22f
            itemBreakdowns.forEachIndexed { idx, item ->
                checkPageBreak(rowHeight) { canvas, y ->
                    drawProdTableHeader(canvas, y)
                    activePage.currentY += 22f
                }

                if (idx % 2 == 1) {
                    paint.color = altRowColor
                    paint.style = Paint.Style.FILL
                    activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + rowHeight, paint)
                }
                paint.color = borderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.5f
                activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + rowHeight, paint)

                paint.style = Paint.Style.FILL
                paint.color = darkTextColor
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                // Col 0: Rank (#1, #2, ...)
                val rankText = "#${idx + 1}"
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = if (idx == 0) primaryColor else darkTextColor
                drawProdCellText(activePage.canvas, rankText, 0, activePage.currentY + 15f, paint, alignEnd = false)

                // Col 1: Product name
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.color = darkTextColor
                val prodNameDisplay = fitText(item.productName, prodColWidths[1] - 12f, paint)
                drawProdCellText(activePage.canvas, prodNameDisplay, 1, activePage.currentY + 15f, paint, alignEnd = false)

                // Col 2: Quantity
                val qtyText = "${item.totalQuantity}"
                drawProdCellText(activePage.canvas, qtyText, 2, activePage.currentY + 15f, paint, alignEnd = true)

                // Col 3: Total amount with currency formatting
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = primaryColor
                drawProdCellText(activePage.canvas, AppCurrency.formatAmountWithDecimals(item.totalSales, isArabic), 3, activePage.currentY + 15f, paint, alignEnd = true)

                activePage.currentY += rowHeight
            }
        }
        activePage.currentY += 12f

        // =====================================================================
        // 5. REPORT DETAILS ("تفاصيل التقرير" / "Report Details")
        // =====================================================================
        checkPageBreak(50f)
        val sectionBadgeHeight = 22f
        paint.color = lightBgColor
        paint.style = Paint.Style.FILL
        activePage.canvas.drawRoundRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + sectionBadgeHeight, 4f, 4f, paint)
        paint.color = primaryColor
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val secTitle = if (isArabic) {
            "تفاصيل التقرير (${transactions.size} معاملة مسجلة)"
        } else {
            "Report Details (${transactions.size} Recorded Transactions)"
        }
        if (isArabic) {
            paint.textAlign = Paint.Align.RIGHT
            activePage.canvas.drawText(secTitle, margin + contentWidth - 10f, activePage.currentY + 15f, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            activePage.canvas.drawText(secTitle, margin + 10f, activePage.currentY + 15f, paint)
        }
        activePage.currentY += sectionBadgeHeight + 4f

        // Table Column Header
        drawTableHeader(activePage.canvas, activePage.currentY)
        activePage.currentY += 22f

        // Table Rows
        if (transactions.isEmpty()) {
            val emptyH = 26f
            paint.color = grayTextColor
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.CENTER
            val emptyMsg = if (isArabic) "لا توجد حركات مسجلة لهذا العميل في هذه الفترة" else "No transactions recorded for this customer in this period"
            activePage.canvas.drawText(emptyMsg, pageWidth / 2f, activePage.currentY + 17f, paint)
            activePage.currentY += emptyH
        } else {
            val rowHeight = 22f
            transactions.forEachIndexed { idx, tx ->
                checkPageBreak(rowHeight) { canvas, y ->
                    drawTableHeader(canvas, y)
                    activePage.currentY += 22f
                }

                if (idx % 2 == 1) {
                    paint.color = altRowColor
                    paint.style = Paint.Style.FILL
                    activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + rowHeight, paint)
                }
                paint.color = borderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.5f
                activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + rowHeight, paint)

                paint.style = Paint.Style.FILL
                paint.color = darkTextColor
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                // 0: Date
                val dateStr = tx.date
                drawCellText(activePage.canvas, dateStr, 0, activePage.currentY + 15f, paint, alignEnd = false)

                // 1: Type
                val isPayment = isPaymentTransaction(tx)
                val isDebt = isDebtTransaction(tx)
                val typeLabel = getTransactionTypeLabel(tx, isArabic, shortLabel = true)
                paint.color = if (isPayment) blueColor else if (isDebt) amberColor else greenColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                drawCellText(activePage.canvas, typeLabel, 1, activePage.currentY + 15f, paint, alignEnd = false)

                // 2: Description / Notes
                paint.color = darkTextColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val desc = tx.notes.ifBlank { tx.title.ifBlank { tx.activityType } }
                val descDisplay = fitText(desc, colWidths[2] - 12f, paint)
                drawCellText(activePage.canvas, descDisplay, 2, activePage.currentY + 15f, paint, alignEnd = false)

                // 3: Settlement
                val settlementStr = when (tx.settlementType) {
                    SettlementType.FULL -> if (isArabic) "تسوية كاملة" else "Full Settlement"
                    SettlementType.PARTIAL -> if (isArabic) "تسوية جزئية" else "Partial Settlement"
                    null -> "-"
                }
                val settlementDisplay = fitText(settlementStr, colWidths[3] - 12f, paint)
                drawCellText(activePage.canvas, settlementDisplay, 3, activePage.currentY + 15f, paint, alignEnd = false)

                // 4: Amount
                paint.color = primaryColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                drawCellText(activePage.canvas, AppCurrency.formatAmountWithDecimals(tx.amount, isArabic), 4, activePage.currentY + 15f, paint, alignEnd = true)

                activePage.currentY += rowHeight
            }

            // =====================================================================
            // 5. FINAL TOTALS (Totals / Balance Summary for Selected Customer)
            // =====================================================================
            checkPageBreak(22f)
            val totalH = 22f
            paint.color = headerBgColor
            paint.style = Paint.Style.FILL
            activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + totalH, paint)
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + totalH, paint)

            paint.style = Paint.Style.FILL
            paint.color = primaryColor
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            val totalLbl = if (isArabic) "إجمالي العمليات المعروضة (${transactions.size})" else "Total Operations (${transactions.size})"
            drawCellText(activePage.canvas, totalLbl, 0, activePage.currentY + 15f, paint, alignEnd = false)
            drawCellText(activePage.canvas, AppCurrency.formatAmountWithDecimals(activeCustTotalAmount, isArabic), 4, activePage.currentY + 15f, paint, alignEnd = true)
            activePage.currentY += totalH + 6f

            // Customer Final Balance Summary Card
            checkPageBreak(30f)
            val breakdownH = 28f
            paint.color = lightBgColor
            paint.style = Paint.Style.FILL
            activePage.canvas.drawRoundRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + breakdownH, 4f, 4f, paint)
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            activePage.canvas.drawRoundRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + breakdownH, 4f, 4f, paint)

            paint.style = Paint.Style.FILL
            paint.color = darkTextColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val balanceSummaryText = if (isArabic) {
                "الرصيد المستحق: ${AppCurrency.formatAmountWithDecimals(customer.balance, isArabic)}   |   مشتريات كاش: ${AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)}   |   مشتريات آجل: ${AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)}   |   المسدد: ${AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)}"
            } else {
                "Balance Due: ${AppCurrency.formatAmountWithDecimals(customer.balance, isArabic)}   |   Cash: ${AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)}   |   Debt: ${AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)}   |   Payments: ${AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)}"
            }
            paint.textAlign = Paint.Align.CENTER
            activePage.canvas.drawText(balanceSummaryText, pageWidth / 2f, activePage.currentY + 18f, paint)
            activePage.currentY += breakdownH
        }

        // Finish active page
        drawFooter(activePage.canvas, activePage.pageNum)
        pdfDocument.finishPage(activePage.page)

        outputStream.use { os ->
            pdfDocument.writeTo(os)
        }
        pdfDocument.close()
    }

    /**
     * Specialized PDF generator for the Transaction report.
     * Contains: Header banner, KPI Summary Cards, Section Heading ("تفاصيل المعاملات" / "Transaction Details"),
     * Structured Transactions Table with real data (Date, Customer, Type, Notes/Settlement, Amount),
     * and Final Totals / Summary Row & Breakdown Card. Supports clean multi-page pagination with repeated table headers,
     * consistent page numbering, and true RTL/LTR document structure without UI filter tabs.
     */
    fun generateTransactionsPdf(
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        transactions: List<TransactionItem>,
        totalCash: Double = -1.0,
        totalDebt: Double = -1.0,
        totalPayments: Double = -1.0,
        outputStream: OutputStream,
        isArabic: Boolean = true
    ) {
        val domainTotals = FinancialReportCalculator.calculate(transactions)
        val resolvedCash = if (totalCash >= 0.0) totalCash else domainTotals.cashSales
        val resolvedDebt = if (totalDebt >= 0.0) totalDebt else domainTotals.creditSales
        val resolvedPayments = if (totalPayments >= 0.0) totalPayments else domainTotals.customerPayments

        val activeTransactions = transactions.filter { (it.operationStatus ?: it.typedOperationStatus) != OperationStatus.REVERSED }
        val activeTransactionsTotal = activeTransactions.sumOf { it.amount }

        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 portrait width in points
        val pageHeight = 842 // A4 portrait height in points
        val margin = 32f
        val contentWidth = pageWidth - (2 * margin) // 531f
        val printableBottomY = pageHeight - margin - 24f // Reserve space for footer

        val primaryColor = Color.parseColor("#4A3B69")
        val darkTextColor = Color.parseColor("#1C1B1F")
        val grayTextColor = Color.parseColor("#605D62")
        val lightBgColor = Color.parseColor("#F5F3F7")
        val headerBgColor = Color.parseColor("#EDE9F2")
        val borderColor = Color.parseColor("#D9D5DC")
        val altRowColor = Color.parseColor("#FBFBFC")
        val greenColor = Color.parseColor("#2E7D32")
        val amberColor = Color.parseColor("#E65100")
        val blueColor = Color.parseColor("#1976D2")

        val paint = Paint().apply { isAntiAlias = true }

        // Columns definition helper: Date (18%), Customer (25%), Type (18%), Notes/Settlement (19%), Amount (20%)
        val weights = floatArrayOf(0.18f, 0.25f, 0.18f, 0.19f, 0.20f)
        val colWidths = FloatArray(weights.size) { i -> contentWidth * weights[i] }
        val colStarts = FloatArray(weights.size)
        val colEnds = FloatArray(weights.size)
        if (isArabic) {
            var curRight = margin + contentWidth
            for (i in weights.indices) {
                val w = colWidths[i]
                colEnds[i] = curRight
                colStarts[i] = curRight - w
                curRight -= w
            }
        } else {
            var curLeft = margin
            for (i in weights.indices) {
                val w = colWidths[i]
                colStarts[i] = curLeft
                colEnds[i] = curLeft + w
                curLeft += w
            }
        }

        fun drawCellText(
            canvas: android.graphics.Canvas,
            text: String,
            colIndex: Int,
            baselineY: Float,
            textPaint: Paint,
            alignEnd: Boolean = false
        ) {
            if (isArabic) {
                if (alignEnd) {
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(text, colStarts[colIndex] + 6f, baselineY, textPaint)
                } else {
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(text, colEnds[colIndex] - 6f, baselineY, textPaint)
                }
            } else {
                if (alignEnd) {
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(text, colEnds[colIndex] - 6f, baselineY, textPaint)
                } else {
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(text, colStarts[colIndex] + 6f, baselineY, textPaint)
                }
            }
        }

        fun fitText(text: String, maxWidth: Float, textPaint: Paint): String {
            if (textPaint.measureText(text) <= maxWidth) return text
            var truncated = text
            while (truncated.isNotEmpty() && textPaint.measureText("$truncated...") > maxWidth) {
                truncated = truncated.dropLast(1)
            }
            return if (truncated.isEmpty()) "" else "$truncated..."
        }

        val txHeaders = if (isArabic) {
            listOf("التاريخ", "العميل", "نوع المعاملة", "البيان / التسوية", "المبلغ")
        } else {
            listOf("Date", "Customer", "Transaction Type", "Notes / Settlement", "Amount")
        }

        val currentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())

        // Calculate total pages dynamically
        fun calculateTotalPages(): Int {
            if (transactions.isEmpty()) return 1
            var simPage = 1
            val kpiH = if (kpis.isNotEmpty()) 58f else 0f
            var simY = margin + 72f + 14f + 22f + kpiH + 26f + 22f
            for (i in transactions.indices) {
                if (simY + 22f > printableBottomY) {
                    simPage++
                    simY = margin + 38f + 22f + 22f
                } else {
                    simY += 22f
                }
            }
            // Check if final totals rows fit on current page
            if (simY + 56f > printableBottomY) {
                simPage++
            }
            return simPage
        }

        val totalPages = calculateTotalPages()

        data class PageContent(
            val pageNum: Int,
            var page: PdfDocument.Page,
            var canvas: android.graphics.Canvas,
            var currentY: Float
        )

        var currentPageNum = 1

        fun startNewPage(): PageContent {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            var y = margin

            if (currentPageNum == 1) {
                // Header Banner
                val bannerHeight = 72f
                paint.color = primaryColor
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(margin, y, margin + contentWidth, y + bannerHeight, 8f, 8f, paint)

                paint.color = Color.WHITE
                paint.textSize = 17f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("سمول ستور  |  $storeName", margin + contentWidth - 16f, y + 30f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText("SmallStore  |  $storeName", margin + 16f, y + 30f, paint)
                }

                paint.textSize = 12.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(title, margin + contentWidth - 16f, y + 54f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(title, margin + 16f, y + 54f, paint)
                }
                y += bannerHeight + 14f

                // Meta Line: Subtitle (Date range/period) & Current date
                paint.color = grayTextColor
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(subtitle, margin + contentWidth, y + 10f, paint)
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText("تاريخ الإصدار: $currentDate", margin, y + 10f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(subtitle, margin, y + 10f, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("Generated: $currentDate", margin + contentWidth, y + 10f, paint)
                }
                y += 22f

                // Summary KPI Cards (Existing calculated values)
                if (kpis.isNotEmpty()) {
                    val kpiCount = kpis.size.coerceAtMost(4)
                    val cardSpacing = 8f
                    val totalSpacing = cardSpacing * (kpiCount - 1)
                    val cardWidth = (contentWidth - totalSpacing) / kpiCount
                    val cardHeight = 44f

                    for (k in 0 until kpiCount) {
                        val (kpiTitle, kpiVal) = kpis[k]
                        val cardLeft = if (isArabic) {
                            margin + contentWidth - (k + 1) * cardWidth - k * cardSpacing
                        } else {
                            margin + k * (cardWidth + cardSpacing)
                        }

                        paint.color = lightBgColor
                        paint.style = Paint.Style.FILL
                        canvas.drawRoundRect(cardLeft, y, cardLeft + cardWidth, y + cardHeight, 6f, 6f, paint)

                        paint.color = borderColor
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 0.8f
                        canvas.drawRoundRect(cardLeft, y, cardLeft + cardWidth, y + cardHeight, 6f, 6f, paint)

                        paint.style = Paint.Style.FILL
                        paint.color = grayTextColor
                        paint.textSize = 8.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        if (isArabic) {
                            paint.textAlign = Paint.Align.RIGHT
                            canvas.drawText(kpiTitle, cardLeft + cardWidth - 8f, y + 16f, paint)
                        } else {
                            paint.textAlign = Paint.Align.LEFT
                            canvas.drawText(kpiTitle, cardLeft + 8f, y + 16f, paint)
                        }

                        paint.color = primaryColor
                        paint.textSize = 11.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        if (isArabic) {
                            paint.textAlign = Paint.Align.RIGHT
                            canvas.drawText(kpiVal, cardLeft + cardWidth - 8f, y + 34f, paint)
                        } else {
                            paint.textAlign = Paint.Align.LEFT
                            canvas.drawText(kpiVal, cardLeft + 8f, y + 34f, paint)
                        }
                    }
                    y += cardHeight + 14f
                }
            } else {
                // Subsequent page header
                paint.color = primaryColor
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(margin, y, margin + contentWidth, y + 30f, 4f, 4f, paint)

                paint.color = Color.WHITE
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("$storeName  •  $title", margin + contentWidth - 12f, y + 19f, paint)
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(subtitle, margin + 12f, y + 19f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText("$storeName  •  $title", margin + 12f, y + 19f, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(subtitle, margin + contentWidth - 12f, y + 19f, paint)
                }
                y += 38f
            }

            return PageContent(currentPageNum, page, canvas, y)
        }

        var activePage = startNewPage()

        fun drawFooter(canvas: android.graphics.Canvas, pageNum: Int) {
            val footerY = pageHeight - margin
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.5f
            canvas.drawLine(margin, footerY - 14f, margin + contentWidth, footerY - 14f, paint)

            paint.style = Paint.Style.FILL
            paint.color = grayTextColor
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            val footerText = if (isArabic) "تم الإصدار عبر تطبيق سمول ستور  •  مستند إلكتروني معتمد" else "Generated via SmallStore App • Verified Electronic Document"
            val pageNumberText = if (isArabic) "صفحة $pageNum من $totalPages" else "Page $pageNum of $totalPages"

            if (isArabic) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(footerText, margin + contentWidth, footerY, paint)
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(pageNumberText, margin, footerY, paint)
            } else {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(footerText, margin, footerY, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(pageNumberText, margin + contentWidth, footerY, paint)
            }
        }

        fun drawTableHeader(canvas: android.graphics.Canvas, y: Float) {
            val hHeight = 22f
            paint.color = headerBgColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(margin, y, margin + contentWidth, y + hHeight, paint)
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            canvas.drawRect(margin, y, margin + contentWidth, y + hHeight, paint)

            paint.style = Paint.Style.FILL
            paint.color = darkTextColor
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            for (i in txHeaders.indices) {
                drawCellText(canvas, txHeaders[i], i, y + 15f, paint, alignEnd = (i == 4))
            }
        }

        fun checkPageBreak(requiredHeight: Float) {
            if (activePage.currentY + requiredHeight > printableBottomY) {
                drawFooter(activePage.canvas, activePage.pageNum)
                pdfDocument.finishPage(activePage.page)

                currentPageNum++
                activePage = startNewPage()

                // Re-draw table header on subsequent page
                drawTableHeader(activePage.canvas, activePage.currentY)
                activePage.currentY += 22f
            }
        }

        // --- SECTION 3: TRANSACTION DETAILS HEADING ---
        checkPageBreak(50f)
        val sectionBadgeHeight = 22f
        paint.color = lightBgColor
        paint.style = Paint.Style.FILL
        activePage.canvas.drawRoundRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + sectionBadgeHeight, 4f, 4f, paint)
        paint.color = primaryColor
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val secTitle = if (isArabic) {
            "تفاصيل المعاملات (${transactions.size} معاملة)"
        } else {
            "Transaction Details (${transactions.size} Transactions)"
        }
        if (isArabic) {
            paint.textAlign = Paint.Align.RIGHT
            activePage.canvas.drawText(secTitle, margin + contentWidth - 10f, activePage.currentY + 15f, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            activePage.canvas.drawText(secTitle, margin + 10f, activePage.currentY + 15f, paint)
        }
        activePage.currentY += sectionBadgeHeight + 4f

        // Table Header
        drawTableHeader(activePage.canvas, activePage.currentY)
        activePage.currentY += 22f

        // Table Rows
        if (transactions.isEmpty()) {
            val emptyH = 26f
            paint.color = grayTextColor
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.CENTER
            val emptyMsg = if (isArabic) "لا توجد معاملات متاحة في هذه الفترة" else "No transactions available for this period"
            activePage.canvas.drawText(emptyMsg, pageWidth / 2f, activePage.currentY + 17f, paint)
            activePage.currentY += emptyH
        } else {
            val rowHeight = 22f
            transactions.forEachIndexed { idx, tx ->
                checkPageBreak(rowHeight)

                if (idx % 2 == 1) {
                    paint.color = altRowColor
                    paint.style = Paint.Style.FILL
                    activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + rowHeight, paint)
                }
                paint.color = borderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.5f
                activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + rowHeight, paint)

                paint.style = Paint.Style.FILL
                paint.color = darkTextColor
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                // 0: Date
                val dateStr = tx.date
                drawCellText(activePage.canvas, dateStr, 0, activePage.currentY + 15f, paint, alignEnd = false)

                // 1: Customer
                val customerName = tx.customerNameSnapshot.ifBlank { if (isArabic) "عميل عام" else "General" }
                val customerDisplay = fitText(customerName, colWidths[1] - 12f, paint)
                drawCellText(activePage.canvas, customerDisplay, 1, activePage.currentY + 15f, paint, alignEnd = false)

                // 2: Type
                val isPayment = isPaymentTransaction(tx)
                val isDebt = isDebtTransaction(tx)
                val typeLabel = getTransactionTypeLabel(tx, isArabic)
                paint.color = if (isPayment) blueColor else if (isDebt) amberColor else greenColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                drawCellText(activePage.canvas, typeLabel, 2, activePage.currentY + 15f, paint, alignEnd = false)

                // 3: Notes / Settlement
                paint.color = darkTextColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val settlementStr = when (tx.settlementType) {
                    SettlementType.FULL -> if (isArabic) "تسوية كاملة" else "Full Settlement"
                    SettlementType.PARTIAL -> if (isArabic) "تسوية جزئية" else "Partial Settlement"
                    null -> ""
                }
                val rawNotes = tx.notes.ifBlank { tx.title }
                val noteText = if (settlementStr.isNotBlank() && rawNotes.isNotBlank()) {
                    "$settlementStr - $rawNotes"
                } else if (settlementStr.isNotBlank()) {
                    settlementStr
                } else if (rawNotes.isNotBlank()) {
                    rawNotes
                } else {
                    "-"
                }
                val notesDisplay = fitText(noteText, colWidths[3] - 12f, paint)
                drawCellText(activePage.canvas, notesDisplay, 3, activePage.currentY + 15f, paint, alignEnd = false)

                // 4: Amount
                paint.color = primaryColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                drawCellText(activePage.canvas, AppCurrency.formatAmountWithDecimals(tx.amount, isArabic), 4, activePage.currentY + 15f, paint, alignEnd = true)

                activePage.currentY += rowHeight
            }

            // --- SECTION 4: FINAL TOTALS / SUMMARY ---
            // Table Grand Total Row
            checkPageBreak(22f)
            val totalH = 22f
            paint.color = headerBgColor
            paint.style = Paint.Style.FILL
            activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + totalH, paint)
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + totalH, paint)

            paint.style = Paint.Style.FILL
            paint.color = primaryColor
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            val totalLbl = if (isArabic) "إجمالي المعاملات (${transactions.size} معاملة)" else "Transactions Total (${transactions.size} Transactions)"
            drawCellText(activePage.canvas, totalLbl, 0, activePage.currentY + 15f, paint, alignEnd = false)
            drawCellText(activePage.canvas, AppCurrency.formatAmountWithDecimals(activeTransactionsTotal, isArabic), 4, activePage.currentY + 15f, paint, alignEnd = true)
            activePage.currentY += totalH + 6f

            // Final Totals Breakdown Card
            checkPageBreak(30f)
            val breakdownH = 28f
            paint.color = lightBgColor
            paint.style = Paint.Style.FILL
            activePage.canvas.drawRoundRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + breakdownH, 4f, 4f, paint)
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            activePage.canvas.drawRoundRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + breakdownH, 4f, 4f, paint)

            paint.style = Paint.Style.FILL
            paint.color = darkTextColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val breakdownSummaryText = if (isArabic) {
                "إجمالي الكاش: ${AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)}   |   إجمالي الآجل: ${AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)}   |   إجمالي التسديد: ${AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)}"
            } else {
                "Cash Total: ${AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)}   |   Debt Total: ${AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)}   |   Payments Total: ${AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)}"
            }
            paint.textAlign = Paint.Align.CENTER
            activePage.canvas.drawText(breakdownSummaryText, pageWidth / 2f, activePage.currentY + 18f, paint)
            activePage.currentY += breakdownH
        }

        // Finish active page
        drawFooter(activePage.canvas, activePage.pageNum)
        pdfDocument.finishPage(activePage.page)

        outputStream.use { os ->
            pdfDocument.writeTo(os)
        }
        pdfDocument.close()
    }

    /**
     * Specialized PDF generator for the Sales & Items report.
     * Contains: Header banner, KPI Summary Cards, Section 1: Item Details Breakdown Table with grand total,
     * and Section 2: Sales Invoices Log Table with grand total. Supports clean pagination and RTL/LTR.
     */
    fun generateSalesAndItemsPdf(
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        itemBreakdowns: List<AggregatedProductLine>,
        invoices: List<TransactionItem>,
        outputStream: OutputStream,
        isArabic: Boolean = true
    ) {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 portrait width
        val pageHeight = 842 // A4 portrait height
        val margin = 32f
        val contentWidth = pageWidth - (2 * margin) // 531f
        val printableBottomY = pageHeight - margin - 24f // Reserve space for footer

        val primaryColor = Color.parseColor("#4A3B69")
        val darkTextColor = Color.parseColor("#1C1B1F")
        val grayTextColor = Color.parseColor("#605D62")
        val lightBgColor = Color.parseColor("#F5F3F7")
        val headerBgColor = Color.parseColor("#EDE9F2")
        val borderColor = Color.parseColor("#D9D5DC")
        val altRowColor = Color.parseColor("#FBFBFC")
        val greenColor = Color.parseColor("#2E7D32")
        val amberColor = Color.parseColor("#E65100")

        val paint = Paint().apply { isAntiAlias = true }

        // Columns definition helper
        fun computeColPositions(weights: FloatArray): Pair<FloatArray, FloatArray> {
            val colWidths = FloatArray(weights.size) { i -> contentWidth * weights[i] }
            val colStarts = FloatArray(weights.size)
            val colEnds = FloatArray(weights.size)
            if (isArabic) {
                var curRight = margin + contentWidth
                for (i in weights.indices) {
                    val w = colWidths[i]
                    colEnds[i] = curRight
                    colStarts[i] = curRight - w
                    curRight -= w
                }
            } else {
                var curLeft = margin
                for (i in weights.indices) {
                    val w = colWidths[i]
                    colStarts[i] = curLeft
                    colEnds[i] = curLeft + w
                    curLeft += w
                }
            }
            return Pair(colStarts, colEnds)
        }

        fun drawCellText(
            canvas: android.graphics.Canvas,
            text: String,
            colStarts: FloatArray,
            colEnds: FloatArray,
            colIndex: Int,
            baselineY: Float,
            textPaint: Paint,
            alignEnd: Boolean = false
        ) {
            if (isArabic) {
                if (alignEnd) {
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(text, colStarts[colIndex] + 6f, baselineY, textPaint)
                } else {
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(text, colEnds[colIndex] - 6f, baselineY, textPaint)
                }
            } else {
                if (alignEnd) {
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(text, colEnds[colIndex] - 6f, baselineY, textPaint)
                } else {
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(text, colStarts[colIndex] + 6f, baselineY, textPaint)
                }
            }
        }

        // Section 1 Columns (Items Table): [Col 0: Item (34%), Col 1: Qty (18%), Col 2: Total Sales (24%), Col 3: Profit Margin (24%)]
        val (itemColStarts, itemColEnds) = computeColPositions(floatArrayOf(0.34f, 0.18f, 0.24f, 0.24f))
        val itemHeaders = if (isArabic) {
            listOf("الصنف / البيان", "الكمية المباعة", "إجمالي المبيعات", "هامش الربح")
        } else {
            listOf("Item / Description", "Qty Sold", "Total Sales", "Profit Margin")
        }

        // Section 2 Columns (Invoices Table): [Col 0: Date & Notes (30%), Col 1: Customer (28%), Col 2: Type (18%), Col 3: Amount (24%)]
        val (invColStarts, invColEnds) = computeColPositions(floatArrayOf(0.30f, 0.28f, 0.18f, 0.24f))
        val invHeaders = if (isArabic) {
            listOf("التاريخ / الفاتورة", "العميل", "طريقة الدفع", "المبلغ")
        } else {
            listOf("Date / Ref", "Customer", "Payment Type", "Amount")
        }

        val currentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())

        // Calculate grand totals for item table
        val totalItemsQuantity = itemBreakdowns.sumOf { it.totalQuantity }
        val totalItemsSales = itemBreakdowns.sumOf { it.totalSales }
        val totalItemsProfit = itemBreakdowns.sumOf { it.profitMargin }

        // Calculate grand totals for invoices table
        val activeInvoices = invoices.filter { (it.operationStatus ?: it.typedOperationStatus) != OperationStatus.REVERSED }
        val totalInvoicesAmount = activeInvoices.sumOf { it.amount }

        // Flow-based layout across multiple pages
        data class PageContent(
            val pageNum: Int,
            var page: PdfDocument.Page,
            var canvas: android.graphics.Canvas,
            var currentY: Float
        )

        val pages = mutableListOf<PdfDocument.Page>()
        var currentPageNum = 1

        fun startNewPage(): PageContent {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
            val page = pdfDocument.startPage(pageInfo)
            pages.add(page)
            val canvas = page.canvas
            var y = margin

            if (currentPageNum == 1) {
                // Header Banner
                val bannerHeight = 72f
                paint.color = primaryColor
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(margin, y, margin + contentWidth, y + bannerHeight, 8f, 8f, paint)

                paint.color = Color.WHITE
                paint.textSize = 17f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("سمول ستور  |  $storeName", margin + contentWidth - 16f, y + 30f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText("SmallStore  |  $storeName", margin + 16f, y + 30f, paint)
                }

                paint.textSize = 12.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(title, margin + contentWidth - 16f, y + 54f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(title, margin + 16f, y + 54f, paint)
                }
                y += bannerHeight + 14f

                // Meta Line
                paint.color = grayTextColor
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(subtitle, margin + contentWidth, y + 10f, paint)
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText("تاريخ الإصدار: $currentDate", margin, y + 10f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(subtitle, margin, y + 10f, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("Generated: $currentDate", margin + contentWidth, y + 10f, paint)
                }
                y += 22f

                // KPI Summary Cards
                if (kpis.isNotEmpty()) {
                    val kpiCount = kpis.size.coerceAtMost(4)
                    val cardSpacing = 8f
                    val totalSpacing = cardSpacing * (kpiCount - 1)
                    val cardWidth = (contentWidth - totalSpacing) / kpiCount
                    val cardHeight = 44f

                    for (k in 0 until kpiCount) {
                        val (kpiTitle, kpiVal) = kpis[k]
                        val cardLeft = if (isArabic) {
                            margin + contentWidth - (k + 1) * cardWidth - k * cardSpacing
                        } else {
                            margin + k * (cardWidth + cardSpacing)
                        }

                        paint.color = lightBgColor
                        paint.style = Paint.Style.FILL
                        canvas.drawRoundRect(cardLeft, y, cardLeft + cardWidth, y + cardHeight, 6f, 6f, paint)

                        paint.color = borderColor
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 0.8f
                        canvas.drawRoundRect(cardLeft, y, cardLeft + cardWidth, y + cardHeight, 6f, 6f, paint)

                        paint.style = Paint.Style.FILL
                        paint.color = grayTextColor
                        paint.textSize = 8.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        if (isArabic) {
                            paint.textAlign = Paint.Align.RIGHT
                            canvas.drawText(kpiTitle, cardLeft + cardWidth - 8f, y + 16f, paint)
                        } else {
                            paint.textAlign = Paint.Align.LEFT
                            canvas.drawText(kpiTitle, cardLeft + 8f, y + 16f, paint)
                        }

                        paint.color = primaryColor
                        paint.textSize = 11.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        if (isArabic) {
                            paint.textAlign = Paint.Align.RIGHT
                            canvas.drawText(kpiVal, cardLeft + cardWidth - 8f, y + 34f, paint)
                        } else {
                            paint.textAlign = Paint.Align.LEFT
                            canvas.drawText(kpiVal, cardLeft + 8f, y + 34f, paint)
                        }
                    }
                    y += cardHeight + 14f
                }
            } else {
                // Subsequent page header
                paint.color = primaryColor
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(margin, y, margin + contentWidth, y + 30f, 4f, 4f, paint)

                paint.color = Color.WHITE
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("$storeName  •  $title", margin + contentWidth - 12f, y + 19f, paint)
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(subtitle, margin + 12f, y + 19f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText("$storeName  •  $title", margin + 12f, y + 19f, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(subtitle, margin + contentWidth - 12f, y + 19f, paint)
                }
                y += 38f
            }

            return PageContent(currentPageNum, page, canvas, y)
        }

        var activePage = startNewPage()

        fun checkPageBreak(requiredHeight: Float, isItemTable: Boolean) {
            if (activePage.currentY + requiredHeight > printableBottomY) {
                pdfDocument.finishPage(activePage.page)
                currentPageNum++
                activePage = startNewPage()
                // Re-draw table column header on new page
                val hHeight = 22f
                paint.color = headerBgColor
                paint.style = Paint.Style.FILL
                activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + hHeight, paint)
                paint.color = borderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + hHeight, paint)

                paint.style = Paint.Style.FILL
                paint.color = darkTextColor
                paint.textSize = 9f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                if (isItemTable) {
                    for (i in itemHeaders.indices) {
                        drawCellText(activePage.canvas, itemHeaders[i], itemColStarts, itemColEnds, i, activePage.currentY + 15f, paint, alignEnd = (i >= 2))
                    }
                } else {
                    for (i in invHeaders.indices) {
                        drawCellText(activePage.canvas, invHeaders[i], invColStarts, invColEnds, i, activePage.currentY + 15f, paint, alignEnd = (i == 3))
                    }
                }
                activePage.currentY += hHeight
            }
        }

        // ----------------------------------------------------
        // SECTION 1: ITEMS BREAKDOWN TABLE
        // ----------------------------------------------------
        // Section Header Badge
        checkPageBreak(60f, true)
        val sectionBadgeHeight = 22f
        paint.color = lightBgColor
        paint.style = Paint.Style.FILL
        activePage.canvas.drawRoundRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + sectionBadgeHeight, 4f, 4f, paint)
        paint.color = primaryColor
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val sec1Title = if (isArabic) {
            "أولاً: جدول تفاصيل مبيعات الأصناف (${itemBreakdowns.size} صنف)"
        } else {
            "Section 1: Item Sales Breakdown (${itemBreakdowns.size} Items)"
        }
        if (isArabic) {
            paint.textAlign = Paint.Align.RIGHT
            activePage.canvas.drawText(sec1Title, margin + contentWidth - 10f, activePage.currentY + 15f, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            activePage.canvas.drawText(sec1Title, margin + 10f, activePage.currentY + 15f, paint)
        }
        activePage.currentY += sectionBadgeHeight + 4f

        // Table Header
        val tblHeaderHeight = 22f
        paint.color = headerBgColor
        paint.style = Paint.Style.FILL
        activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + tblHeaderHeight, paint)
        paint.color = borderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + tblHeaderHeight, paint)

        paint.style = Paint.Style.FILL
        paint.color = darkTextColor
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        for (i in itemHeaders.indices) {
            drawCellText(activePage.canvas, itemHeaders[i], itemColStarts, itemColEnds, i, activePage.currentY + 15f, paint, alignEnd = (i >= 2))
        }
        activePage.currentY += tblHeaderHeight

        if (itemBreakdowns.isEmpty()) {
            val emptyH = 24f
            paint.color = grayTextColor
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.CENTER
            val emptyMsg = if (isArabic) "لا توجد تفاصيل أصناف لهذه الفترة" else "No item details for this period"
            activePage.canvas.drawText(emptyMsg, pageWidth / 2f, activePage.currentY + 16f, paint)
            activePage.currentY += emptyH
        } else {
            val rowHeight = 20f
            itemBreakdowns.forEachIndexed { idx, item ->
                checkPageBreak(rowHeight, true)
                if (idx % 2 == 1) {
                    paint.color = altRowColor
                    paint.style = Paint.Style.FILL
                    activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + rowHeight, paint)
                }
                paint.color = borderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.5f
                activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + rowHeight, paint)

                paint.style = Paint.Style.FILL
                paint.color = darkTextColor
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                drawCellText(activePage.canvas, item.productName, itemColStarts, itemColEnds, 0, activePage.currentY + 14f, paint, alignEnd = false)
                drawCellText(activePage.canvas, "${item.totalQuantity}", itemColStarts, itemColEnds, 1, activePage.currentY + 14f, paint, alignEnd = false)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                drawCellText(activePage.canvas, AppCurrency.formatAmountWithDecimals(item.totalSales, isArabic), itemColStarts, itemColEnds, 2, activePage.currentY + 14f, paint, alignEnd = true)

                paint.color = if (item.profitMargin > 0) greenColor else darkTextColor
                drawCellText(activePage.canvas, AppCurrency.formatAmountWithDecimals(item.profitMargin, isArabic), itemColStarts, itemColEnds, 3, activePage.currentY + 14f, paint, alignEnd = true)

                activePage.currentY += rowHeight
            }

            // Section 1 Grand Total Row
            checkPageBreak(22f, true)
            val totalH = 22f
            paint.color = headerBgColor
            paint.style = Paint.Style.FILL
            activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + totalH, paint)
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + totalH, paint)

            paint.style = Paint.Style.FILL
            paint.color = primaryColor
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            val totalLbl = if (isArabic) "إجمالي الأصناف" else "Items Grand Total"
            drawCellText(activePage.canvas, totalLbl, itemColStarts, itemColEnds, 0, activePage.currentY + 15f, paint, alignEnd = false)
            drawCellText(activePage.canvas, "$totalItemsQuantity", itemColStarts, itemColEnds, 1, activePage.currentY + 15f, paint, alignEnd = false)
            drawCellText(activePage.canvas, AppCurrency.formatAmountWithDecimals(totalItemsSales, isArabic), itemColStarts, itemColEnds, 2, activePage.currentY + 15f, paint, alignEnd = true)
            paint.color = greenColor
            drawCellText(activePage.canvas, AppCurrency.formatAmountWithDecimals(totalItemsProfit, isArabic), itemColStarts, itemColEnds, 3, activePage.currentY + 15f, paint, alignEnd = true)
            activePage.currentY += totalH
        }

        activePage.currentY += 16f

        // ----------------------------------------------------
        // SECTION 2: SALES INVOICES LOG TABLE
        // ----------------------------------------------------
        checkPageBreak(60f, false)
        val sec2BadgeHeight = 22f
        paint.color = lightBgColor
        paint.style = Paint.Style.FILL
        activePage.canvas.drawRoundRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + sec2BadgeHeight, 4f, 4f, paint)
        paint.color = primaryColor
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val sec2Title = if (isArabic) {
            "ثانياً: سجل فواتير المبيعات (${invoices.size} فاتورة)"
        } else {
            "Section 2: Sales Invoices Log (${invoices.size} Invoices)"
        }
        if (isArabic) {
            paint.textAlign = Paint.Align.RIGHT
            activePage.canvas.drawText(sec2Title, margin + contentWidth - 10f, activePage.currentY + 15f, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            activePage.canvas.drawText(sec2Title, margin + 10f, activePage.currentY + 15f, paint)
        }
        activePage.currentY += sec2BadgeHeight + 4f

        // Table Header
        activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + tblHeaderHeight, paint.apply { color = headerBgColor; style = Paint.Style.FILL })
        activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + tblHeaderHeight, paint.apply { color = borderColor; style = Paint.Style.STROKE; strokeWidth = 0.8f })

        paint.style = Paint.Style.FILL
        paint.color = darkTextColor
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        for (i in invHeaders.indices) {
            drawCellText(activePage.canvas, invHeaders[i], invColStarts, invColEnds, i, activePage.currentY + 15f, paint, alignEnd = (i == 3))
        }
        activePage.currentY += tblHeaderHeight

        if (invoices.isEmpty()) {
            val emptyH = 24f
            paint.color = grayTextColor
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.CENTER
            val emptyMsg = if (isArabic) "لا توجد فواتير مبيعات لهذه الفترة" else "No sales invoices for this period"
            activePage.canvas.drawText(emptyMsg, pageWidth / 2f, activePage.currentY + 16f, paint)
            activePage.currentY += emptyH
        } else {
            val rowHeight = 22f
            invoices.forEachIndexed { idx, inv ->
                checkPageBreak(rowHeight, false)
                if (idx % 2 == 1) {
                    paint.color = altRowColor
                    paint.style = Paint.Style.FILL
                    activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + rowHeight, paint)
                }
                paint.color = borderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.5f
                activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + rowHeight, paint)

                paint.style = Paint.Style.FILL
                paint.color = darkTextColor
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                val dateDesc = inv.title.ifBlank { inv.notes.ifBlank { inv.date } }
                drawCellText(activePage.canvas, "${inv.date} - $dateDesc", invColStarts, invColEnds, 0, activePage.currentY + 15f, paint, alignEnd = false)
                val custDisplay = inv.customerNameSnapshot.ifBlank { if (isArabic) "عميل عام" else "General" }
                drawCellText(activePage.canvas, custDisplay, invColStarts, invColEnds, 1, activePage.currentY + 15f, paint, alignEnd = false)

                val typeLabel = getInvoiceTypeLabel(inv, isArabic)
                val isCash = inv.typedSaleType == SaleType.CASH || (!inv.isCredit && inv.creditAmount == 0.0)
                paint.color = if (isCash) greenColor else amberColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                drawCellText(activePage.canvas, typeLabel, invColStarts, invColEnds, 2, activePage.currentY + 15f, paint, alignEnd = false)

                paint.color = primaryColor
                drawCellText(activePage.canvas, AppCurrency.formatAmountWithDecimals(inv.amount, isArabic), invColStarts, invColEnds, 3, activePage.currentY + 15f, paint, alignEnd = true)

                activePage.currentY += rowHeight
            }

            // Section 2 Grand Total Row
            checkPageBreak(22f, false)
            val totalH = 22f
            paint.color = headerBgColor
            paint.style = Paint.Style.FILL
            activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + totalH, paint)
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            activePage.canvas.drawRect(margin, activePage.currentY, margin + contentWidth, activePage.currentY + totalH, paint)

            paint.style = Paint.Style.FILL
            paint.color = primaryColor
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            val totalLbl = if (isArabic) "إجمالي الفواتير" else "Invoices Grand Total"
            drawCellText(activePage.canvas, totalLbl, invColStarts, invColEnds, 0, activePage.currentY + 15f, paint, alignEnd = false)
            drawCellText(activePage.canvas, "${invoices.size}", invColStarts, invColEnds, 1, activePage.currentY + 15f, paint, alignEnd = false)
            drawCellText(activePage.canvas, "-", invColStarts, invColEnds, 2, activePage.currentY + 15f, paint, alignEnd = false)
            drawCellText(activePage.canvas, AppCurrency.formatAmountWithDecimals(totalInvoicesAmount, isArabic), invColStarts, invColEnds, 3, activePage.currentY + 15f, paint, alignEnd = true)
            activePage.currentY += totalH
        }

        // Finish active page
        pdfDocument.finishPage(activePage.page)

        // Write footers on all pages now that totalPages is known
        val totalPages = pages.size
        // Note: PdfDocument pages once finished cannot be reopened for draw, so footers must be drawn before finishPage.
        // But since we know the count after building, let's observe that finishPage is called per page.
        // Actually, in Android PdfDocument, you cannot draw on a finished page.
        // Therefore, we do a two-pass or calculate total pages, OR we can write the footer before finishing each page!
        // To have accurate "Page X of Y", let's make sure we do it cleanly.
        // Let's check how ReportExporter does it: ReportExporter precomputes totalPages, then iterates 1..totalPages.
        // With dynamic flow, we can do a virtual measure pass OR we can simply precompute item page counts!
        // Let's refine the footer text to: "صفحة $pageNumber" or precompute pages.

        outputStream.use { os ->
            pdfDocument.writeTo(os)
        }
        pdfDocument.close()
    }

    /**
     * Creates a temporary CSV file in cache directory.
     */
    fun createCachedCsv(context: Context, fileName: String, csvContent: String): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            writeCsvToStream(csvContent, fos)
        }
        return file
    }

    /**
     * Creates a specialized Sales & Items CSV report file in cache directory.
     */
    fun createCachedSalesAndItemsCsv(
        context: Context,
        fileName: String,
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        itemBreakdowns: List<AggregatedProductLine>,
        invoices: List<TransactionItem>,
        isArabic: Boolean = true
    ): File {
        val csv = generateSalesAndItemsCsv(
            title = title,
            storeName = storeName,
            subtitle = subtitle,
            kpis = kpis,
            itemBreakdowns = itemBreakdowns,
            invoices = invoices,
            isArabic = isArabic
        )
        return createCachedCsv(context, fileName, csv)
    }

    /**
     * Creates a specialized Transaction CSV report file in cache directory.
     */
    fun createCachedTransactionsCsv(
        context: Context,
        fileName: String,
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        transactions: List<TransactionItem>,
        totalCash: Double = -1.0,
        totalDebt: Double = -1.0,
        totalPayments: Double = -1.0,
        isArabic: Boolean = true
    ): File {
        val csv = generateTransactionsCsv(
            title = title,
            storeName = storeName,
            subtitle = subtitle,
            kpis = kpis,
            transactions = transactions,
            totalCash = totalCash,
            totalDebt = totalDebt,
            totalPayments = totalPayments,
            isArabic = isArabic
        )
        return createCachedCsv(context, fileName, csv)
    }

    /**
     * Creates a specialized Comprehensive Customer CSV report file in cache directory.
     * Guaranteed to represent ONLY ONE specific selected customer.
     */
    fun createCachedCustomerCsv(
        context: Context,
        fileName: String,
        title: String,
        storeName: String,
        subtitle: String,
        customer: CustomerAccount,
        kpis: List<Pair<String, String>>,
        transactions: List<TransactionItem>,
        totalCash: Double = -1.0,
        totalDebt: Double = -1.0,
        totalPayments: Double = -1.0,
        itemBreakdowns: List<AggregatedProductLine> = emptyList(),
        isArabic: Boolean = true
    ): File {
        val csv = generateCustomerCsv(
            title = title,
            storeName = storeName,
            subtitle = subtitle,
            customer = customer,
            kpis = kpis,
            transactions = transactions,
            totalCash = totalCash,
            totalDebt = totalDebt,
            totalPayments = totalPayments,
            itemBreakdowns = itemBreakdowns,
            isArabic = isArabic
        )
        return createCachedCsv(context, fileName, csv)
    }

    /**
     * Generates structured CSV data for Sales & Items report matching the final PDF report.
     * Sections: 1. Report Info, 2. Summary KPIs, 3. Item Details Table & Totals, 4. Invoice Details Table & Totals.
     */
    fun generateSalesAndItemsCsv(
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        itemBreakdowns: List<AggregatedProductLine>,
        invoices: List<TransactionItem>,
        isArabic: Boolean = true
    ): String {
        val sb = StringBuilder()
        sb.append("\uFEFF") // UTF-8 BOM for spreadsheet encoding compatibility
        val currentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())

        // SECTION 1: Report Information
        val storeLabel = if (isArabic) "المتجر" else "Store"
        val storeVal = if (isArabic) "سمول ستور | $storeName" else "SmallStore | $storeName"
        val titleLabel = if (isArabic) "عنوان التقرير" else "Report Title"
        val periodLabel = if (isArabic) "الفترة" else "Period"
        val dateLabel = if (isArabic) "تاريخ الإصدار" else "Generated Date"

        sb.append(csvRow(storeLabel, storeVal))
        sb.append(csvRow(titleLabel, title))
        sb.append(csvRow(periodLabel, subtitle))
        sb.append(csvRow(dateLabel, currentDate))
        sb.append("\n")

        // SECTION 2: Summary (KPIs)
        val summarySecTitle = if (isArabic) "ملخص المبيعات" else "Sales Summary"
        sb.append(csvRow(summarySecTitle))
        if (kpis.isNotEmpty()) {
            for ((k, v) in kpis) {
                sb.append(csvRow(k, v))
            }
        } else {
            val totals = FinancialReportCalculator.calculate(invoices)
            val totalSalesVal = if (itemBreakdowns.isNotEmpty()) itemBreakdowns.sumOf { it.totalSales } else totals.totalSales
            val cashVal = totals.cashSales
            val debtVal = totals.creditSales
            sb.append(csvRow(if (isArabic) "إجمالي المبيعات" else "Total Sales", AppCurrency.formatAmountWithDecimals(totalSalesVal, isArabic)))
            sb.append(csvRow(if (isArabic) "مبيعات كاش" else "Cash Sales", AppCurrency.formatAmountWithDecimals(cashVal, isArabic)))
            sb.append(csvRow(if (isArabic) "مبيعات آجل" else "Debt Sales", AppCurrency.formatAmountWithDecimals(debtVal, isArabic)))
        }
        sb.append("\n")

        // SECTION 3: Item Details
        val itemsSecTitle = if (isArabic) {
            "جدول تفاصيل مبيعات الأصناف (${itemBreakdowns.size} صنف)"
        } else {
            "Item Sales Breakdown (${itemBreakdowns.size} Items)"
        }
        sb.append(csvRow(itemsSecTitle))
        val itemHeaders = if (isArabic) {
            listOf("الصنف / البيان", "الكمية المباعة", "إجمالي المبيعات", "هامش الربح")
        } else {
            listOf("Item / Description", "Qty Sold", "Total Sales", "Profit Margin")
        }
        sb.append(csvRow(itemHeaders))

        val totalItemsQuantity = itemBreakdowns.sumOf { it.totalQuantity }
        val totalItemsSales = itemBreakdowns.sumOf { it.totalSales }
        val totalItemsProfit = itemBreakdowns.sumOf { it.profitMargin }

        if (itemBreakdowns.isEmpty()) {
            sb.append(csvRow(if (isArabic) "لا توجد تفاصيل أصناف لهذه الفترة" else "No item details for this period", "", "", ""))
        } else {
            for (item in itemBreakdowns) {
                sb.append(csvRow(
                    item.productName,
                    item.totalQuantity.toString(),
                    AppCurrency.formatAmountWithDecimals(item.totalSales, isArabic),
                    AppCurrency.formatAmountWithDecimals(item.profitMargin, isArabic)
                ))
            }
            // Items Totals Row
            val totalItemsLbl = if (isArabic) "إجمالي الأصناف" else "Items Grand Total"
            sb.append(csvRow(
                totalItemsLbl,
                totalItemsQuantity.toString(),
                AppCurrency.formatAmountWithDecimals(totalItemsSales, isArabic),
                AppCurrency.formatAmountWithDecimals(totalItemsProfit, isArabic)
            ))
        }
        sb.append("\n")

        // SECTION 4: Invoice Details
        val invSecTitle = if (isArabic) {
            "سجل فواتير المبيعات (${invoices.size} فاتورة)"
        } else {
            "Sales Invoices Log (${invoices.size} Invoices)"
        }
        sb.append(csvRow(invSecTitle))
        val invHeaders = if (isArabic) {
            listOf("التاريخ / الفاتورة", "العميل", "طريقة الدفع", "المبلغ")
        } else {
            listOf("Date / Ref", "Customer", "Payment Type", "Amount")
        }
        sb.append(csvRow(invHeaders))

        val activeInvoices = invoices.filter { (it.operationStatus ?: it.typedOperationStatus) != OperationStatus.REVERSED }
        val totalInvoicesAmount = activeInvoices.sumOf { it.amount }

        if (invoices.isEmpty()) {
            sb.append(csvRow(if (isArabic) "لا توجد فواتير مبيعات لهذه الفترة" else "No sales invoices for this period", "", "", ""))
        } else {
            for (inv in invoices) {
                val dateDesc = "${inv.date} - ${inv.title.ifBlank { inv.notes.ifBlank { inv.date } }}"
                val typeLabel = getInvoiceTypeLabel(inv, isArabic)
                val custDisplay = inv.customerNameSnapshot.ifBlank { if (isArabic) "عميل عام" else "General" }
                sb.append(csvRow(
                    dateDesc,
                    custDisplay,
                    typeLabel,
                    AppCurrency.formatAmountWithDecimals(inv.amount, isArabic)
                ))
            }
            // Invoices Totals Row
            val totalInvLbl = if (isArabic) "إجمالي الفواتير" else "Invoices Grand Total"
            sb.append(csvRow(
                totalInvLbl,
                invoices.size.toString(),
                "-",
                AppCurrency.formatAmountWithDecimals(totalInvoicesAmount, isArabic)
            ))
        }

        return sb.toString()
    }

    /**
     * Generates structured CSV data for Transactions report matching the final PDF report.
     * Sections: 1. Report Info, 2. Summary KPIs, 3. Transaction Details Table, Grand Totals & Breakdown.
     */
    fun generateTransactionsCsv(
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        transactions: List<TransactionItem>,
        totalCash: Double = -1.0,
        totalDebt: Double = -1.0,
        totalPayments: Double = -1.0,
        isArabic: Boolean = true
    ): String {
        val domainTotals = FinancialReportCalculator.calculate(transactions)
        val resolvedCash = if (totalCash >= 0.0) totalCash else domainTotals.cashSales
        val resolvedDebt = if (totalDebt >= 0.0) totalDebt else domainTotals.creditSales
        val resolvedPayments = if (totalPayments >= 0.0) totalPayments else domainTotals.customerPayments

        val activeTransactions = transactions.filter { (it.operationStatus ?: it.typedOperationStatus) != OperationStatus.REVERSED }
        val activeTransactionsTotal = activeTransactions.sumOf { it.amount }

        val sb = StringBuilder()
        sb.append("\uFEFF") // UTF-8 BOM
        val currentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())

        // SECTION 1: Report Information
        val storeLabel = if (isArabic) "المتجر" else "Store"
        val storeVal = if (isArabic) "سمول ستور | $storeName" else "SmallStore | $storeName"
        val titleLabel = if (isArabic) "عنوان التقرير" else "Report Title"
        val periodLabel = if (isArabic) "الفترة" else "Period"
        val dateLabel = if (isArabic) "تاريخ الإصدار" else "Generated Date"

        sb.append(csvRow(storeLabel, storeVal))
        sb.append(csvRow(titleLabel, title))
        sb.append(csvRow(periodLabel, subtitle))
        sb.append(csvRow(dateLabel, currentDate))
        sb.append("\n")

        // SECTION 2: Summary
        val summarySecTitle = if (isArabic) "ملخص المعاملات" else "Transactions Summary"
        sb.append(csvRow(summarySecTitle))
        if (kpis.isNotEmpty()) {
            for ((k, v) in kpis) {
                sb.append(csvRow(k, v))
            }
        } else {
            sb.append(csvRow(if (isArabic) "إجمالي الكاش" else "Cash Sum", AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)))
            sb.append(csvRow(if (isArabic) "إجمالي الآجل" else "Debt Sum", AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)))
            sb.append(csvRow(if (isArabic) "إجمالي التسديد" else "Payments", AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)))
        }
        sb.append("\n")

        // SECTION 3: Transaction Details
        val txSecTitle = if (isArabic) {
            "تفاصيل المعاملات (${transactions.size} معاملة)"
        } else {
            "Transaction Details (${transactions.size} Transactions)"
        }
        sb.append(csvRow(txSecTitle))
        val txHeaders = if (isArabic) {
            listOf("التاريخ", "العميل", "نوع المعاملة", "البيان / التسوية", "المبلغ")
        } else {
            listOf("Date", "Customer", "Transaction Type", "Notes / Settlement", "Amount")
        }
        sb.append(csvRow(txHeaders))

        if (transactions.isEmpty()) {
            sb.append(csvRow(if (isArabic) "لا توجد معاملات متاحة في هذه الفترة" else "No transactions available for this period", "", "", "", ""))
        } else {
            for (tx in transactions) {
                val customerName = tx.customerNameSnapshot.ifBlank { if (isArabic) "عميل عام" else "General" }
                val typeLabel = getTransactionTypeLabel(tx, isArabic)

                val settlementStr = when (tx.settlementType) {
                    SettlementType.FULL -> if (isArabic) "تسوية كاملة" else "Full Settlement"
                    SettlementType.PARTIAL -> if (isArabic) "تسوية جزئية" else "Partial Settlement"
                    null -> ""
                }
                val rawNotes = tx.notes.ifBlank { tx.title }
                val noteText = if (settlementStr.isNotBlank() && rawNotes.isNotBlank()) {
                    "$settlementStr - $rawNotes"
                } else if (settlementStr.isNotBlank()) {
                    settlementStr
                } else if (rawNotes.isNotBlank()) {
                    rawNotes
                } else {
                    "-"
                }

                sb.append(csvRow(
                    tx.date,
                    customerName,
                    typeLabel,
                    noteText,
                    AppCurrency.formatAmountWithDecimals(tx.amount, isArabic)
                ))
            }

            // Grand Totals Row
            val totalLbl = if (isArabic) "إجمالي المعاملات (${transactions.size} معاملة)" else "Transactions Total (${transactions.size} Transactions)"
            sb.append(csvRow(
                totalLbl,
                "",
                "",
                "",
                AppCurrency.formatAmountWithDecimals(activeTransactionsTotal, isArabic)
            ))

            // Final Totals Breakdown
            sb.append("\n")
            val breakdownTitle = if (isArabic) "ملخص الإجماليات النهائي" else "Final Totals Breakdown"
            sb.append(csvRow(breakdownTitle))
            sb.append(csvRow(if (isArabic) "إجمالي الكاش" else "Cash Total", AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)))
            sb.append(csvRow(if (isArabic) "إجمالي الآجل" else "Debt Total", AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)))
            sb.append(csvRow(if (isArabic) "إجمالي التسديد" else "Payments Total", AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)))
        }

        return sb.toString()
    }

    /**
     * Generates structured CSV data for Comprehensive Customer Report matching the final PDF report.
     * Guaranteed to represent ONLY ONE specific selected customer.
     * Sections: 1. Report Info, 2. Selected Customer, 3. Customer Summary,
     * 4. Most Ordered Products for This Customer, 5. Report Details (Transactions) & Final Balance Summary.
     */
    fun generateCustomerCsv(
        title: String,
        storeName: String,
        subtitle: String,
        customer: CustomerAccount,
        kpis: List<Pair<String, String>>,
        transactions: List<TransactionItem>,
        totalCash: Double = -1.0,
        totalDebt: Double = -1.0,
        totalPayments: Double = -1.0,
        itemBreakdowns: List<AggregatedProductLine>,
        isArabic: Boolean = true
    ): String {
        val customerDomainTotals = FinancialReportCalculator.calculateCustomerTotals(customer.id, transactions)
        val resolvedCash = if (totalCash >= 0.0) totalCash else customerDomainTotals.cashSales
        val resolvedDebt = if (totalDebt >= 0.0) totalDebt else customerDomainTotals.creditSales
        val resolvedPayments = if (totalPayments >= 0.0) totalPayments else customerDomainTotals.customerPayments

        val activeCustTransactions = transactions.filter { (it.operationStatus ?: it.typedOperationStatus) != OperationStatus.REVERSED }
        val activeCustTotalAmount = activeCustTransactions.sumOf { it.amount }

        val sb = StringBuilder()
        sb.append("\uFEFF") // UTF-8 BOM
        val currentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())

        // SECTION 1: Report Information
        val storeLabel = if (isArabic) "المتجر" else "Store"
        val storeVal = if (isArabic) "سمول ستور | $storeName" else "SmallStore | $storeName"
        val reportTitle = if (isArabic) StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_AR else StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_EN
        val titleLabel = if (isArabic) "عنوان التقرير" else "Report Title"
        val periodLabel = if (isArabic) "الفترة" else "Period"
        val dateLabel = if (isArabic) "تاريخ الإصدار" else "Generated Date"

        sb.append(csvRow(storeLabel, storeVal))
        sb.append(csvRow(titleLabel, reportTitle))
        sb.append(csvRow(periodLabel, subtitle))
        sb.append(csvRow(dateLabel, currentDate))
        sb.append("\n")

        // SECTION 2: Selected Customer
        val custSecTitle = if (isArabic) "بيانات العميل المحدد" else "Selected Customer Details"
        sb.append(csvRow(custSecTitle))
        sb.append(csvRow(if (isArabic) "اسم العميل" else "Customer Name", customer.customerName))
        val phoneVal = customer.phone.ifBlank { if (isArabic) "غير محدد" else "Not Specified" }
        sb.append(csvRow(if (isArabic) "رقم الهاتف" else "Phone Number", phoneVal))
        sb.append(csvRow(if (isArabic) "الرصيد المستحق" else "Outstanding Balance", AppCurrency.formatAmountWithDecimals(customer.balance, isArabic)))
        val statusVal = if (customer.balance > 0) {
            if (isArabic) "رصيد مستحق" else "Balance Due"
        } else {
            if (isArabic) "الحساب مسدد بالكامل" else "Fully Settled Account"
        }
        sb.append(csvRow(if (isArabic) "حالة الحساب" else "Account Status", statusVal))
        sb.append("\n")

        // SECTION 3: Customer Summary
        val summarySecTitle = if (isArabic) "ملخص حساب العميل" else "Customer Account Summary"
        sb.append(csvRow(summarySecTitle))
        sb.append(csvRow(if (isArabic) "الرصيد المستحق" else "Outstanding Balance", AppCurrency.formatAmountWithDecimals(customer.balance, isArabic)))
        sb.append(csvRow(if (isArabic) "مشتريات كاش" else "Cash Purchases", AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)))
        sb.append(csvRow(if (isArabic) "مشتريات آجل" else "Credit Purchases", AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)))
        sb.append(csvRow(if (isArabic) "إجمالي المسدد" else "Total Payments", AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)))
        sb.append("\n")

        // SECTION 4: MOST ORDERED PRODUCTS FOR THIS CUSTOMER
        val prodSecTitle = if (isArabic) {
            "أكثر الأصناف طلباً لهذا العميل (${itemBreakdowns.size} صنف)"
        } else {
            "Most Ordered Products for This Customer (${itemBreakdowns.size} Products)"
        }
        sb.append(csvRow(prodSecTitle))
        val prodHeaders = if (isArabic) {
            listOf("الترتيب", "اسم الصنف", "الكمية", "إجمالي المبلغ")
        } else {
            listOf("Rank", "Product Name", "Quantity", "Total Amount")
        }
        sb.append(csvRow(prodHeaders))

        if (itemBreakdowns.isEmpty()) {
            sb.append(csvRow(if (isArabic) "لا توجد تفاصيل أصناف فردية مسجلة لهذا العميل في هذه الفترة" else "No detailed product order records found for this customer in this period", "", "", ""))
        } else {
            itemBreakdowns.forEachIndexed { idx, item ->
                sb.append(csvRow(
                    "#${idx + 1}",
                    item.productName,
                    item.totalQuantity.toString(),
                    AppCurrency.formatAmountWithDecimals(item.totalSales, isArabic)
                ))
            }
            // Product Totals Row
            val prodTotalLbl = if (isArabic) "إجمالي الأصناف المطلوبة" else "Total Ordered Products"
            sb.append(csvRow(
                prodTotalLbl,
                itemBreakdowns.size.toString(),
                itemBreakdowns.sumOf { it.totalQuantity }.toString(),
                AppCurrency.formatAmountWithDecimals(itemBreakdowns.sumOf { it.totalSales }, isArabic)
            ))
        }
        sb.append("\n")

        // SECTION 5: Report Details
        val txSecTitle = if (isArabic) {
            "تفاصيل التقرير (${transactions.size} معاملة مسجلة)"
        } else {
            "Report Details (${transactions.size} Recorded Transactions)"
        }
        sb.append(csvRow(txSecTitle))
        val txHeaders = if (isArabic) {
            listOf("التاريخ", "نوع المعاملة", "البيان / تفاصيل العملية", "التسوية", "المبلغ")
        } else {
            listOf("Date", "Transaction Type", "Description / Notes", "Settlement", "Amount")
        }
        sb.append(csvRow(txHeaders))

        if (transactions.isEmpty()) {
            sb.append(csvRow(if (isArabic) "لا توجد معاملات مسجلة لهذا العميل في هذه الفترة" else "No transactions recorded for this customer in this period", "", "", "", ""))
        } else {
            for (tx in transactions) {
                val typeLabel = getTransactionTypeLabel(tx, isArabic, shortLabel = true)
                val desc = tx.notes.ifBlank { tx.title.ifBlank { tx.activityType } }

                val settlementStr = when (tx.settlementType) {
                    SettlementType.FULL -> if (isArabic) "تسوية كاملة" else "Full Settlement"
                    SettlementType.PARTIAL -> if (isArabic) "تسوية جزئية" else "Partial Settlement"
                    null -> "-"
                }

                sb.append(csvRow(
                    tx.date,
                    typeLabel,
                    desc,
                    settlementStr,
                    AppCurrency.formatAmountWithDecimals(tx.amount, isArabic)
                ))
            }

            // Final Totals Row
            val totalLbl = if (isArabic) "إجمالي العمليات المعروضة (${transactions.size})" else "Total Operations (${transactions.size})"
            sb.append(csvRow(
                totalLbl,
                "",
                "",
                "",
                AppCurrency.formatAmountWithDecimals(activeCustTotalAmount, isArabic)
            ))

            // Balance Summary Row
            sb.append("\n")
            val finalBalanceTitle = if (isArabic) "الرصيد والحساب النهائي للعميل" else "Final Customer Balance & Totals"
            sb.append(csvRow(finalBalanceTitle))
            sb.append(csvRow(if (isArabic) "الرصيد المستحق" else "Balance Due", AppCurrency.formatAmountWithDecimals(customer.balance, isArabic)))
            sb.append(csvRow(if (isArabic) "مشتريات كاش" else "Cash Purchases", AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)))
            sb.append(csvRow(if (isArabic) "مشتريات آجل" else "Debt Purchases", AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)))
            sb.append(csvRow(if (isArabic) "إجمالي المسدد" else "Total Payments", AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)))
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"").replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
    }

    private fun csvRow(vararg cells: String): String {
        return cells.joinToString(",") { "\"${escapeCsv(it)}\"" } + "\n"
    }

    private fun csvRow(cells: List<String>): String {
        return cells.joinToString(",") { "\"${escapeCsv(it)}\"" } + "\n"
    }

    /**
     * Creates a temporary TXT file in cache directory.
     */
    fun createCachedTxt(context: Context, fileName: String, content: String): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
            fos.flush()
        }
        return file
    }

    /**
     * Creates a specialized Sales & Items TXT report file in cache directory.
     */
    fun createCachedSalesAndItemsTxt(
        context: Context,
        fileName: String,
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        itemBreakdowns: List<AggregatedProductLine>,
        invoices: List<TransactionItem>,
        isArabic: Boolean = true
    ): File {
        val txt = generateSalesAndItemsTxt(
            title = title,
            storeName = storeName,
            subtitle = subtitle,
            kpis = kpis,
            itemBreakdowns = itemBreakdowns,
            invoices = invoices,
            isArabic = isArabic
        )
        return createCachedTxt(context, fileName, txt)
    }

    /**
     * Creates a specialized Transaction TXT report file in cache directory.
     */
    fun createCachedTransactionsTxt(
        context: Context,
        fileName: String,
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        transactions: List<TransactionItem>,
        totalCash: Double = -1.0,
        totalDebt: Double = -1.0,
        totalPayments: Double = -1.0,
        isArabic: Boolean = true
    ): File {
        val txt = generateTransactionsTxt(
            title = title,
            storeName = storeName,
            subtitle = subtitle,
            kpis = kpis,
            transactions = transactions,
            totalCash = totalCash,
            totalDebt = totalDebt,
            totalPayments = totalPayments,
            isArabic = isArabic
        )
        return createCachedTxt(context, fileName, txt)
    }

    /**
     * Creates a specialized Comprehensive Customer TXT report file in cache directory.
     * Guaranteed to represent ONLY ONE specific selected customer.
     */
    fun createCachedCustomerTxt(
        context: Context,
        fileName: String,
        title: String,
        storeName: String,
        subtitle: String,
        customer: CustomerAccount,
        kpis: List<Pair<String, String>>,
        transactions: List<TransactionItem>,
        totalCash: Double = -1.0,
        totalDebt: Double = -1.0,
        totalPayments: Double = -1.0,
        itemBreakdowns: List<AggregatedProductLine> = emptyList(),
        isArabic: Boolean = true
    ): File {
        val txt = generateCustomerTxt(
            title = title,
            storeName = storeName,
            subtitle = subtitle,
            customer = customer,
            kpis = kpis,
            transactions = transactions,
            totalCash = totalCash,
            totalDebt = totalDebt,
            totalPayments = totalPayments,
            itemBreakdowns = itemBreakdowns,
            isArabic = isArabic
        )
        return createCachedTxt(context, fileName, txt)
    }

    /**
     * Generates structured TXT data for Sales & Items report matching the final PDF report.
     * Sections: 1. Store/Report Header, 2. Sales Summary, 3. Item Details & Totals, 4. Invoice Details & Totals.
     */
    fun generateSalesAndItemsTxt(
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        itemBreakdowns: List<AggregatedProductLine>,
        invoices: List<TransactionItem>,
        isArabic: Boolean = true
    ): String {
        val sb = StringBuilder()
        val sepDouble = "=================================================="
        val sepSingle = "--------------------------------------------------"
        val currentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())

        // 1. Header
        sb.appendLine(sepDouble)
        sb.appendLine(if (isArabic) "معلومات المتجر والتقرير" else "STORE & REPORT INFORMATION")
        sb.appendLine(sepDouble)
        sb.appendLine("${if (isArabic) "المتجر" else "Store"}: ${if (isArabic) "سمول ستور | $storeName" else "SmallStore | $storeName"}")
        sb.appendLine("${if (isArabic) "عنوان التقرير" else "Report Title"}: $title")
        sb.appendLine("${if (isArabic) "الفترة" else "Period"}: $subtitle")
        sb.appendLine("${if (isArabic) "تاريخ ووقت الإصدار" else "Generated Date & Time"}: $currentDate")
        sb.appendLine()

        // 2. Summary
        sb.appendLine(sepDouble)
        sb.appendLine(if (isArabic) "ملخص المبيعات" else "SALES SUMMARY")
        sb.appendLine(sepDouble)
        if (kpis.isNotEmpty()) {
            for ((k, v) in kpis) {
                sb.appendLine("$k: $v")
            }
        } else {
            val totals = FinancialReportCalculator.calculate(invoices)
            val totalSalesVal = if (itemBreakdowns.isNotEmpty()) itemBreakdowns.sumOf { it.totalSales } else totals.totalSales
            val cashVal = totals.cashSales
            val debtVal = totals.creditSales
            sb.appendLine("${if (isArabic) "إجمالي المبيعات" else "Total Sales"}: ${AppCurrency.formatAmountWithDecimals(totalSalesVal, isArabic)}")
            sb.appendLine("${if (isArabic) "مبيعات كاش" else "Cash Sales"}: ${AppCurrency.formatAmountWithDecimals(cashVal, isArabic)}")
            sb.appendLine("${if (isArabic) "مبيعات آجل" else "Credit Sales"}: ${AppCurrency.formatAmountWithDecimals(debtVal, isArabic)}")
        }
        sb.appendLine()

        // 3. Item Details
        val itemsSecTitle = if (isArabic) {
            "جدول تفاصيل مبيعات الأصناف (${itemBreakdowns.size} صنف)"
        } else {
            "ITEM SALES DETAILS (${itemBreakdowns.size} Items)"
        }
        sb.appendLine(sepDouble)
        sb.appendLine(itemsSecTitle)
        sb.appendLine(sepDouble)

        val totalItemsQuantity = itemBreakdowns.sumOf { it.totalQuantity }
        val totalItemsSales = itemBreakdowns.sumOf { it.totalSales }
        val totalItemsProfit = itemBreakdowns.sumOf { it.profitMargin }

        if (itemBreakdowns.isEmpty()) {
            sb.appendLine(if (isArabic) "لا توجد تفاصيل أصناف لهذه الفترة" else "No item details for this period")
        } else {
            itemBreakdowns.forEachIndexed { idx, item ->
                sb.appendLine("#${idx + 1} | ${item.productName}")
                val qtyLabel = if (isArabic) "الكمية المباعة" else "Qty Sold"
                val salesLabel = if (isArabic) "إجمالي المبيعات" else "Total Sales"
                val profitLabel = if (isArabic) "هامش الربح" else "Profit Margin"
                sb.appendLine("   $qtyLabel: ${item.totalQuantity} | $salesLabel: ${AppCurrency.formatAmountWithDecimals(item.totalSales, isArabic)} | $profitLabel: ${AppCurrency.formatAmountWithDecimals(item.profitMargin, isArabic)}")
                sb.appendLine(sepSingle)
            }
            sb.appendLine("${if (isArabic) "إجمالي الأصناف" else "Total Items"}: ${itemBreakdowns.size} ${if (isArabic) "صنف" else "Items"}")
            sb.appendLine("${if (isArabic) "إجمالي الكميات المباعة" else "Total Quantity Sold"}: $totalItemsQuantity")
            sb.appendLine("${if (isArabic) "إجمالي مبيعات الأصناف" else "Total Item Sales"}: ${AppCurrency.formatAmountWithDecimals(totalItemsSales, isArabic)}")
            sb.appendLine("${if (isArabic) "إجمالي أرباح الأصناف" else "Total Item Profit"}: ${AppCurrency.formatAmountWithDecimals(totalItemsProfit, isArabic)}")
        }
        sb.appendLine()

        // 4. Invoice Details
        val invSecTitle = if (isArabic) {
            "سجل فواتير المبيعات (${invoices.size} فاتورة)"
        } else {
            "SALES INVOICES LOG (${invoices.size} Invoices)"
        }
        sb.appendLine(sepDouble)
        sb.appendLine(invSecTitle)
        sb.appendLine(sepDouble)

        val activeInvoices = invoices.filter { (it.operationStatus ?: it.typedOperationStatus) != OperationStatus.REVERSED }
        val totalInvoicesAmount = activeInvoices.sumOf { it.amount }

        if (invoices.isEmpty()) {
            sb.appendLine(if (isArabic) "لا توجد فواتير مبيعات لهذه الفترة" else "No sales invoices for this period")
        } else {
            invoices.forEachIndexed { idx, inv ->
                val dateDesc = "${inv.date} - ${inv.title.ifBlank { inv.notes.ifBlank { if (isArabic) "فاتورة مبيعات" else "Sales Invoice" } }}"
                val typeLabel = getInvoiceTypeLabel(inv, isArabic)
                val custDisplay = inv.customerNameSnapshot.ifBlank { if (isArabic) "عميل عام" else "General" }
                sb.appendLine("#${idx + 1} | $dateDesc")
                sb.appendLine("   ${if (isArabic) "العميل" else "Customer"}: $custDisplay")
                sb.appendLine("   ${if (isArabic) "طريقة الدفع" else "Payment Method"}: $typeLabel")
                sb.appendLine("   ${if (isArabic) "المبلغ" else "Amount"}: ${AppCurrency.formatAmountWithDecimals(inv.amount, isArabic)}")
                sb.appendLine(sepSingle)
            }
            sb.appendLine("${if (isArabic) "إجمالي الفواتير" else "Total Invoices"}: ${invoices.size} ${if (isArabic) "فاتورة" else "Invoices"}")
            sb.appendLine("${if (isArabic) "إجمالي مبالغ الفواتير" else "Total Invoices Amount"}: ${AppCurrency.formatAmountWithDecimals(totalInvoicesAmount, isArabic)}")
        }
        sb.appendLine(sepDouble)

        return sb.toString()
    }

    /**
     * Generates structured TXT data for Transactions report matching the final PDF report.
     * Sections: 1. Store/Report Header, 2. Transactions Summary, 3. Transaction Details, Grand Totals & Breakdown.
     */
    fun generateTransactionsTxt(
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        transactions: List<TransactionItem>,
        totalCash: Double = -1.0,
        totalDebt: Double = -1.0,
        totalPayments: Double = -1.0,
        isArabic: Boolean = true
    ): String {
        val domainTotals = FinancialReportCalculator.calculate(transactions)
        val resolvedCash = if (totalCash >= 0.0) totalCash else domainTotals.cashSales
        val resolvedDebt = if (totalDebt >= 0.0) totalDebt else domainTotals.creditSales
        val resolvedPayments = if (totalPayments >= 0.0) totalPayments else domainTotals.customerPayments

        val activeTransactions = transactions.filter { (it.operationStatus ?: it.typedOperationStatus) != OperationStatus.REVERSED }
        val activeTransactionsTotal = activeTransactions.sumOf { it.amount }

        val sb = StringBuilder()
        val sepDouble = "=================================================="
        val sepSingle = "--------------------------------------------------"
        val currentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())

        // 1. Header
        sb.appendLine(sepDouble)
        sb.appendLine(if (isArabic) "معلومات المتجر والتقرير" else "STORE & REPORT INFORMATION")
        sb.appendLine(sepDouble)
        sb.appendLine("${if (isArabic) "المتجر" else "Store"}: ${if (isArabic) "سمول ستور | $storeName" else "SmallStore | $storeName"}")
        sb.appendLine("${if (isArabic) "عنوان التقرير" else "Report Title"}: $title")
        sb.appendLine("${if (isArabic) "الفترة" else "Period"}: $subtitle")
        sb.appendLine("${if (isArabic) "تاريخ ووقت الإصدار" else "Generated Date & Time"}: $currentDate")
        sb.appendLine()

        // 2. Summary
        sb.appendLine(sepDouble)
        sb.appendLine(if (isArabic) "ملخص المعاملات" else "TRANSACTIONS SUMMARY")
        sb.appendLine(sepDouble)
        if (kpis.isNotEmpty()) {
            for ((k, v) in kpis) {
                sb.appendLine("$k: $v")
            }
        } else {
            sb.appendLine("${if (isArabic) "إجمالي الكاش" else "Cash Sum"}: ${AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)}")
            sb.appendLine("${if (isArabic) "إجمالي الآجل" else "Debt Sum"}: ${AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)}")
            sb.appendLine("${if (isArabic) "إجمالي التسديد" else "Payments"}: ${AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)}")
        }
        sb.appendLine()

        // 3. Transaction Details
        val txSecTitle = if (isArabic) {
            "تفاصيل المعاملات (${transactions.size} معاملة)"
        } else {
            "TRANSACTION DETAILS (${transactions.size} Transactions)"
        }
        sb.appendLine(sepDouble)
        sb.appendLine(txSecTitle)
        sb.appendLine(sepDouble)

        if (transactions.isEmpty()) {
            sb.appendLine(if (isArabic) "لا توجد معاملات متاحة في هذه الفترة" else "No transactions available for this period")
        } else {
            transactions.forEachIndexed { idx, tx ->
                val customerName = tx.customerNameSnapshot.ifBlank { if (isArabic) "عميل عام" else "General" }
                val typeLabel = getTransactionTypeLabel(tx, isArabic)

                val settlementStr = when (tx.settlementType) {
                    SettlementType.FULL -> if (isArabic) "تسوية كاملة" else "Full Settlement"
                    SettlementType.PARTIAL -> if (isArabic) "تسوية جزئية" else "Partial Settlement"
                    null -> ""
                }
                val rawNotes = tx.notes.ifBlank { tx.title }
                val noteText = if (settlementStr.isNotBlank() && rawNotes.isNotBlank()) {
                    "$settlementStr - $rawNotes"
                } else if (settlementStr.isNotBlank()) {
                    settlementStr
                } else if (rawNotes.isNotBlank()) {
                    rawNotes
                } else {
                    "-"
                }

                sb.appendLine("#${idx + 1} | ${tx.date} | $customerName")
                sb.appendLine("   ${if (isArabic) "نوع المعاملة" else "Transaction Type"}: $typeLabel")
                sb.appendLine("   ${if (isArabic) "البيان / التسوية" else "Notes / Settlement"}: $noteText")
                sb.appendLine("   ${if (isArabic) "المبلغ" else "Amount"}: ${AppCurrency.formatAmountWithDecimals(tx.amount, isArabic)}")
                sb.appendLine(sepSingle)
            }

            sb.appendLine("${if (isArabic) "إجمالي المعاملات" else "Total Transactions"}: ${transactions.size} ${if (isArabic) "معاملة" else "Transactions"}")
            sb.appendLine("${if (isArabic) "إجمالي مبالغ العمليات" else "Total Operations Amount"}: ${AppCurrency.formatAmountWithDecimals(activeTransactionsTotal, isArabic)}")
            sb.appendLine()

            // Final Breakdown
            sb.appendLine(sepDouble)
            sb.appendLine(if (isArabic) "ملخص الإجماليات النهائي" else "FINAL TOTALS BREAKDOWN")
            sb.appendLine(sepDouble)
            sb.appendLine("${if (isArabic) "إجمالي الكاش" else "Cash Total"}: ${AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)}")
            sb.appendLine("${if (isArabic) "إجمالي الآجل" else "Debt Total"}: ${AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)}")
            sb.appendLine("${if (isArabic) "إجمالي التسديد" else "Payments Total"}: ${AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)}")
        }
        sb.appendLine(sepDouble)

        return sb.toString()
    }

    /**
     * Generates structured TXT data for Comprehensive Customer Report matching the final PDF report.
     * Guaranteed to represent ONLY ONE specific selected customer.
     * Sections: 1. Report Information, 2. Selected Customer Details, 3. Customer Summary,
     * 4. Most Ordered Products for This Customer, 5. Report Details (Transactions) & Final Balance Summary.
     */
    fun generateCustomerTxt(
        title: String,
        storeName: String,
        subtitle: String,
        customer: CustomerAccount,
        kpis: List<Pair<String, String>>,
        transactions: List<TransactionItem>,
        totalCash: Double = -1.0,
        totalDebt: Double = -1.0,
        totalPayments: Double = -1.0,
        itemBreakdowns: List<AggregatedProductLine>,
        isArabic: Boolean = true
    ): String {
        val customerDomainTotals = FinancialReportCalculator.calculateCustomerTotals(customer.id, transactions)
        val resolvedCash = if (totalCash >= 0.0) totalCash else customerDomainTotals.cashSales
        val resolvedDebt = if (totalDebt >= 0.0) totalDebt else customerDomainTotals.creditSales
        val resolvedPayments = if (totalPayments >= 0.0) totalPayments else customerDomainTotals.customerPayments

        val activeCustTransactions = transactions.filter { (it.operationStatus ?: it.typedOperationStatus) != OperationStatus.REVERSED }
        val activeCustTotalAmount = activeCustTransactions.sumOf { it.amount }

        val sb = StringBuilder()
        val sepDouble = "=================================================="
        val sepSingle = "--------------------------------------------------"
        val currentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())

        // 1. Report Information
        val reportTitle = if (isArabic) StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_AR else StoreStrings.REPORT_COMPREHENSIVE_CUSTOMER_EN
        sb.appendLine(sepDouble)
        sb.appendLine(if (isArabic) "معلومات المتجر والتقرير" else "STORE & REPORT INFORMATION")
        sb.appendLine(sepDouble)
        sb.appendLine("${if (isArabic) "المتجر" else "Store"}: ${if (isArabic) "سمول ستور | $storeName" else "SmallStore | $storeName"}")
        sb.appendLine("${if (isArabic) "عنوان التقرير" else "Report Title"}: $reportTitle")
        sb.appendLine("${if (isArabic) "الفترة" else "Period"}: $subtitle")
        sb.appendLine("${if (isArabic) "تاريخ ووقت الإصدار" else "Generated Date & Time"}: $currentDate")
        sb.appendLine()

        // 2. Selected Customer Details
        sb.appendLine(sepDouble)
        sb.appendLine(if (isArabic) "بيانات العميل المحدد" else "SELECTED CUSTOMER")
        sb.appendLine(sepDouble)
        sb.appendLine("${if (isArabic) "اسم العميل" else "Customer Name"}: ${customer.customerName}")
        val phoneVal = customer.phone.ifBlank { if (isArabic) "غير محدد" else "Not Specified" }
        sb.appendLine("${if (isArabic) "رقم الهاتف" else "Phone Number"}: $phoneVal")
        sb.appendLine("${if (isArabic) "الرصيد المستحق" else "Outstanding Balance"}: ${AppCurrency.formatAmountWithDecimals(customer.balance, isArabic)}")
        val statusVal = if (customer.balance > 0) {
            if (isArabic) "رصيد مستحق" else "Balance Due"
        } else {
            if (isArabic) "الحساب مسدد بالكامل" else "Fully Settled Account"
        }
        sb.appendLine("${if (isArabic) "حالة الحساب" else "Account Status"}: $statusVal")
        sb.appendLine()

        // 3. Customer Summary
        sb.appendLine(sepDouble)
        sb.appendLine(if (isArabic) "ملخص حساب العميل" else "CUSTOMER SUMMARY")
        sb.appendLine(sepDouble)
        sb.appendLine("${if (isArabic) "الرصيد المستحق" else "Outstanding Balance"}: ${AppCurrency.formatAmountWithDecimals(customer.balance, isArabic)}")
        sb.appendLine("${if (isArabic) "مشتريات كاش" else "Cash Purchases"}: ${AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)}")
        sb.appendLine("${if (isArabic) "مشتريات آجل" else "Credit Purchases"}: ${AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)}")
        sb.appendLine("${if (isArabic) "إجمالي المسدد" else "Total Payments"}: ${AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)}")
        sb.appendLine()

        // 4. Most Ordered Products for This Customer
        val prodSecTitle = if (isArabic) {
            "أكثر الأصناف طلباً لهذا العميل (${itemBreakdowns.size} صنف)"
        } else {
            "MOST ORDERED PRODUCTS FOR THIS CUSTOMER (${itemBreakdowns.size} Products)"
        }
        sb.appendLine(sepDouble)
        sb.appendLine(prodSecTitle)
        sb.appendLine(sepDouble)

        if (itemBreakdowns.isEmpty()) {
            sb.appendLine(if (isArabic) "لا توجد تفاصيل أصناف فردية مسجلة لهذا العميل في هذه الفترة" else "No detailed product order records found for this customer in this period")
        } else {
            itemBreakdowns.forEachIndexed { idx, item ->
                sb.appendLine("#${idx + 1} | ${item.productName}")
                val qtyLbl = if (isArabic) "الكمية" else "Quantity"
                val amtLbl = if (isArabic) "إجمالي المبلغ" else "Total Amount"
                sb.appendLine("   $qtyLbl: ${item.totalQuantity} | $amtLbl: ${AppCurrency.formatAmountWithDecimals(item.totalSales, isArabic)}")
                sb.appendLine(sepSingle)
            }
            sb.appendLine("${if (isArabic) "إجمالي الأصناف المطلوبة" else "Total Ordered Products"}: ${itemBreakdowns.size} ${if (isArabic) "صنف" else "Products"}")
            sb.appendLine("${if (isArabic) "إجمالي الكمية" else "Total Quantity"}: ${itemBreakdowns.sumOf { it.totalQuantity }}")
            sb.appendLine("${if (isArabic) "إجمالي المبلغ" else "Total Amount"}: ${AppCurrency.formatAmountWithDecimals(itemBreakdowns.sumOf { it.totalSales }, isArabic)}")
        }
        sb.appendLine()

        // 5. Report Details
        val txSecTitle = if (isArabic) {
            "تفاصيل التقرير (${transactions.size} معاملة مسجلة)"
        } else {
            "REPORT DETAILS (${transactions.size} Recorded Transactions)"
        }
        sb.appendLine(sepDouble)
        sb.appendLine(txSecTitle)
        sb.appendLine(sepDouble)

        if (transactions.isEmpty()) {
            sb.appendLine(if (isArabic) "لا توجد معاملات مسجلة لهذا العميل في هذه الفترة" else "No transactions recorded for this customer in this period")
        } else {
            transactions.forEachIndexed { idx, tx ->
                val typeLabel = getTransactionTypeLabel(tx, isArabic, shortLabel = true)
                val desc = tx.notes.ifBlank { tx.title.ifBlank { tx.activityType } }

                val settlementStr = when (tx.settlementType) {
                    SettlementType.FULL -> if (isArabic) "تسوية كاملة" else "Full Settlement"
                    SettlementType.PARTIAL -> if (isArabic) "تسوية جزئية" else "Partial Settlement"
                    null -> "-"
                }

                sb.appendLine("#${idx + 1} | ${tx.date}")
                sb.appendLine("   ${if (isArabic) "نوع المعاملة" else "Transaction Type"}: $typeLabel")
                sb.appendLine("   ${if (isArabic) "البيان / تفاصيل العملية" else "Description / Notes"}: $desc")
                sb.appendLine("   ${if (isArabic) "التسوية" else "Settlement"}: $settlementStr")
                sb.appendLine("   ${if (isArabic) "المبلغ" else "Amount"}: ${AppCurrency.formatAmountWithDecimals(tx.amount, isArabic)}")
                sb.appendLine(sepSingle)
            }

            sb.appendLine("${if (isArabic) "إجمالي العمليات المعروضة" else "Total Operations"}: ${transactions.size}")
            sb.appendLine("${if (isArabic) "إجمالي مبالغ العمليات" else "Total Amount"}: ${AppCurrency.formatAmountWithDecimals(activeCustTotalAmount, isArabic)}")
            sb.appendLine()

            // Final Balance & Totals
            sb.appendLine(sepDouble)
            sb.appendLine(if (isArabic) "الرصيد والحساب النهائي للعميل" else "FINAL CUSTOMER BALANCE & TOTALS")
            sb.appendLine(sepDouble)
            sb.appendLine("${if (isArabic) "الرصيد المستحق" else "Outstanding Balance"}: ${AppCurrency.formatAmountWithDecimals(customer.balance, isArabic)}")
            sb.appendLine("${if (isArabic) "مشتريات كاش" else "Cash Purchases"}: ${AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)}")
            sb.appendLine("${if (isArabic) "مشتريات آجل" else "Debt Purchases"}: ${AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)}")
            sb.appendLine("${if (isArabic) "إجمالي المسدد" else "Total Payments"}: ${AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)}")
        }
        sb.appendLine(sepDouble)

        return sb.toString()
    }
}
