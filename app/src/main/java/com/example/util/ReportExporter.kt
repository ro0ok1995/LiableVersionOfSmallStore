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
import com.example.model.AppCurrency
import com.example.model.StoreStrings
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
    val runningBalance: Double
)

data class ReportPreviewRow(
    val col1: String,
    val col2: String,
    val col3: String,
    val col4: String
)

object ReportExporter {
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
     * Creates a temporary CSV file in cache directory.
     */
    fun createCachedCsv(context: Context, fileName: String, csvContent: String): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            writeCsvToStream(csvContent, fos)
        }
        return file
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"")
    }
}
