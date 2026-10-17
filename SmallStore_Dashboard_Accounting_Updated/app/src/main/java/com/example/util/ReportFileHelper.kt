package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.model.StoreStrings
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Shared file I/O, Android print/share intents, and CSV formatting primitives for reporting.
 */
object ReportFileHelper {

    /**
     * Escapes CSV values according to RFC-4180 rules.
     */
    fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"").replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
    }

    /**
     * Formats a row of cells into a comma-separated CSV line with trailing newline.
     */
    fun csvRow(vararg cells: String): String {
        return cells.joinToString(",") { "\"${escapeCsv(it)}\"" } + "\n"
    }

    /**
     * Formats a list of cells into a comma-separated CSV line with trailing newline.
     */
    fun csvRow(cells: List<String>): String {
        return cells.joinToString(",") { "\"${escapeCsv(it)}\"" } + "\n"
    }

    /**
     * Writes CSV string with UTF-8 encoding to the given OutputStream.
     */
    fun writeCsvToStream(csvContent: String, outputStream: OutputStream) {
        outputStream.use { os ->
            os.write(csvContent.toByteArray(Charsets.UTF_8))
            os.flush()
        }
    }

    /**
     * Writes plain text string with UTF-8 encoding to the given OutputStream.
     */
    fun writeTxtToStream(content: String, outputStream: OutputStream) {
        outputStream.use { os ->
            os.write(content.toByteArray(Charsets.UTF_8))
            os.flush()
        }
    }

    /**
     * Writes a PdfDocument to the given OutputStream and closes the document.
     */
    fun writePdfToStream(pdfDocument: PdfDocument, outputStream: OutputStream) {
        outputStream.use { os ->
            pdfDocument.writeTo(os)
        }
        pdfDocument.close()
    }

    /**
     * Creates a temporary CSV file in the application's cache directory.
     */
    fun createCachedCsv(context: Context, fileName: String, csvContent: String): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            writeCsvToStream(csvContent, fos)
        }
        return file
    }

    /**
     * Creates a temporary TXT file in the application's cache directory.
     */
    fun createCachedTxt(context: Context, fileName: String, content: String): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            writeTxtToStream(content, fos)
        }
        return file
    }

    /**
     * Creates a temporary PDF file in the application's cache directory via a generator callback.
     */
    fun createCachedPdf(context: Context, fileName: String, pdfWriter: (OutputStream) -> Unit): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            pdfWriter(fos)
        }
        return file
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
}
