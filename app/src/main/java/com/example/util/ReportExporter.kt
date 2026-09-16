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
     * Generates a standard PDF document using Android's native PdfDocument.
     */
    fun generatePdfReport(
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        headers: List<String>,
        rows: List<ReportPreviewRow>,
        outputStream: OutputStream
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 portrait: 595 x 842 pt
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val primaryColor = Color.parseColor("#4A3B69")
        val darkTextColor = Color.parseColor("#1C1B1F")
        val grayTextColor = Color.parseColor("#605D62")
        val lightBgColor = Color.parseColor("#F5F3F7")
        val borderColor = Color.parseColor("#D9D5DC")

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // Header Background Banner
        paint.color = primaryColor
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        // Store Name & App Identity
        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("SmallStore - $storeName", 30f, 40f, paint)

        // Report Title
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(title, 30f, 65f, paint)

        // Date and Subtitle under header
        paint.color = grayTextColor
        paint.textSize = 10f
        val currentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())
        canvas.drawText("$subtitle  •  $currentDate", 30f, 115f, paint)

        // KPI Summary Cards
        var startX = 30f
        val cardWidth = 160f
        val cardHeight = 45f
        for ((kpiTitle, kpiVal) in kpis.take(3)) {
            paint.color = lightBgColor
            canvas.drawRoundRect(startX, 130f, startX + cardWidth, 130f + cardHeight, 6f, 6f, paint)
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(startX, 130f, startX + cardWidth, 130f + cardHeight, 6f, 6f, paint)

            paint.style = Paint.Style.FILL
            paint.color = grayTextColor
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(kpiTitle, startX + 10f, 146f, paint)

            paint.color = primaryColor
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(kpiVal, startX + 10f, 164f, paint)

            startX += cardWidth + 20f
        }

        // Table Header
        var currentY = 205f
        paint.color = lightBgColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(30f, currentY, 565f, currentY + 24f, paint)

        paint.color = darkTextColor
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val colX = floatArrayOf(40f, 180f, 340f, 480f)
        for (i in headers.indices) {
            if (i < colX.size) {
                canvas.drawText(headers[i], colX[i], currentY + 16f, paint)
            }
        }
        currentY += 24f

        // Table Rows
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        for ((idx, row) in rows.withIndex()) {
            if (idx % 2 == 1) {
                paint.color = Color.parseColor("#FAF9FB")
                paint.style = Paint.Style.FILL
                canvas.drawRect(30f, currentY, 565f, currentY + 22f, paint)
            }
            paint.color = darkTextColor
            paint.style = Paint.Style.FILL
            paint.textSize = 9.5f
            val cols = listOf(row.col1, row.col2, row.col3, row.col4)
            for (i in cols.indices) {
                if (i < colX.size) {
                    canvas.drawText(cols[i], colX[i], currentY + 15f, paint)
                }
            }
            // Row separator
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.5f
            canvas.drawLine(30f, currentY + 22f, 565f, currentY + 22f, paint)
            currentY += 22f
            if (currentY > 780f) break
        }

        // Footer
        paint.style = Paint.Style.FILL
        paint.color = grayTextColor
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Generated by SmallStore App • Electronic Document", 30f, 815f, paint)
        canvas.drawText("Page 1 of 1", 510f, 815f, paint)

        pdfDocument.finishPage(page)
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
        rows: List<ReportPreviewRow>
    ): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            generatePdfReport(title, storeName, subtitle, kpis, headers, rows, fos)
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
