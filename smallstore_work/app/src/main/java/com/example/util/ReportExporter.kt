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
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {
    fun isPaymentTransaction(tx: TransactionItem): Boolean = ReportPresentationUtils.isPaymentTransaction(tx)

    fun isDebtTransaction(tx: TransactionItem): Boolean = ReportPresentationUtils.isDebtTransaction(tx)

    fun getInvoiceTypeLabel(inv: TransactionItem, isArabic: Boolean): String =
        ReportPresentationUtils.getInvoiceTypeLabel(inv, isArabic)

    fun getTransactionTypeLabel(tx: TransactionItem, isArabic: Boolean, shortLabel: Boolean = false): String =
        ReportPresentationUtils.getTransactionTypeLabel(tx, isArabic, shortLabel)
    /**
     * Formats statement rows into standard CSV with UTF-8 BOM so spreadsheet apps display Arabic correctly.
     */
    fun generateStatementCsv(rows: List<StatementRow>, isArabic: Boolean): String =
        ReportPresentationUtils.generateStatementCsv(rows, isArabic)

    /**
     * Formats generic report preview rows into standard CSV with UTF-8 BOM.
     */
    fun generateReportCsv(headers: List<String>, rows: List<ReportPreviewRow>): String =
        ReportPresentationUtils.generateReportCsv(headers, rows)

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
    ): String = ReportPresentationUtils.generateStatementShareText(
        customerName = customerName,
        rows = rows,
        totalIn = totalIn,
        totalOut = totalOut,
        netBalance = netBalance,
        isArabic = isArabic
    )

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
    ): List<StatementRow> = ReportPresentationUtils.buildStatementRows(
        customer = customer,
        transactions = transactions,
        openingBalance = openingBalance,
        includeOpeningBalanceRow = includeOpeningBalanceRow,
        periodStartDate = periodStartDate,
        isArabic = isArabic
    )

    /**
     * Writes CSV string to the given OutputStream.
     */
    fun writeCsvToStream(csvContent: String, outputStream: OutputStream) {
        ReportFileHelper.writeCsvToStream(csvContent, outputStream)
    }

    /**
     * Writes plain text string to the given OutputStream.
     */
    fun writeTxtToStream(content: String, outputStream: OutputStream) {
        ReportFileHelper.writeTxtToStream(content, outputStream)
    }

    /**
     * Writes a PdfDocument to the given OutputStream and closes the document.
     */
    fun writePdfToStream(pdfDocument: PdfDocument, outputStream: OutputStream) {
        ReportFileHelper.writePdfToStream(pdfDocument, outputStream)
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
        ReportPresentationUtils.generatePdfReport(
            title = title,
            storeName = storeName,
            subtitle = subtitle,
            kpis = kpis,
            headers = headers,
            rows = rows,
            outputStream = outputStream,
            isArabic = isArabic
        )
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
    ): String = ReportPresentationUtils.generateReportHtml(
        title = title,
        storeName = storeName,
        subtitle = subtitle,
        kpis = kpis,
        headers = headers,
        rows = rows,
        isArabic = isArabic
    )

    fun printHtml(context: Context, title: String, htmlContent: String, isArabic: Boolean) {
        ReportFileHelper.printHtml(context, title, htmlContent, isArabic)
    }

    fun shareFile(context: Context, file: File, mimeType: String, subject: String, bodyText: String = "") {
        ReportFileHelper.shareFile(context, file, mimeType, subject, bodyText)
    }

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
    ): File = ReportFileHelper.createCachedPdf(context, fileName) { fos ->
        ReportPresentationUtils.generatePdfReport(
            title = title,
            storeName = storeName,
            subtitle = subtitle,
            kpis = kpis,
            headers = headers,
            rows = rows,
            outputStream = fos,
            isArabic = isArabic
        )
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
    ): File = SalesReportExporter.createCachedSalesAndItemsPdf(
        context = context,
        fileName = fileName,
        title = title,
        storeName = storeName,
        subtitle = subtitle,
        kpis = kpis,
        itemBreakdowns = itemBreakdowns,
        invoices = invoices,
        isArabic = isArabic
    )

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
    ): File = TransactionReportExporter.createCachedTransactionsPdf(
        context = context,
        fileName = fileName,
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
    ): File = CustomerReportExporter.createCachedCustomerPdf(
        context = context,
        fileName = fileName,
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

    /**
     * Creates a specialized Analytics / Statistics PDF report file in cache directory.
     * Generates a structured vector PDF supporting both ALL_CUSTOMERS and ONE_SELECTED_CUSTOMER.
     */
    fun createCachedAnalyticsPdf(
        context: Context,
        fileName: String,
        data: AnalyticsReportData,
        isArabic: Boolean = true
    ): File = AnalyticsReportExporter.createCachedAnalyticsPdf(
        context = context,
        fileName = fileName,
        data = data,
        isArabic = isArabic
    )

    fun createCachedAnalyticsCsv(
        context: Context,
        fileName: String,
        data: AnalyticsReportData,
        isArabic: Boolean = true
    ): File = AnalyticsReportExporter.createCachedAnalyticsCsv(
        context = context,
        fileName = fileName,
        data = data,
        isArabic = isArabic
    )

    fun generateAnalyticsCsv(
        data: AnalyticsReportData,
        isArabic: Boolean = true
    ): String = AnalyticsReportExporter.generateAnalyticsCsv(
        data = data,
        isArabic = isArabic
    )

    fun createCachedAnalyticsTxt(
        context: Context,
        fileName: String,
        data: AnalyticsReportData,
        isArabic: Boolean = true
    ): File = AnalyticsReportExporter.createCachedAnalyticsTxt(
        context = context,
        fileName = fileName,
        data = data,
        isArabic = isArabic
    )

    fun generateAnalyticsTxt(
        data: AnalyticsReportData,
        isArabic: Boolean = true
    ): String = AnalyticsReportExporter.generateAnalyticsTxt(
        data = data,
        isArabic = isArabic
    )

    /**
     * Specialized PDF generator for the Comprehensive Customer Report (ONE SELECTED CUSTOMER).
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
        CustomerReportExporter.generateCustomerPdf(
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
            outputStream = outputStream,
            isArabic = isArabic
        )
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
        TransactionReportExporter.generateTransactionsPdf(
            title = title,
            storeName = storeName,
            subtitle = subtitle,
            kpis = kpis,
            transactions = transactions,
            totalCash = totalCash,
            totalDebt = totalDebt,
            totalPayments = totalPayments,
            outputStream = outputStream,
            isArabic = isArabic
        )
    }

    /**
     * Specialized PDF generator for the Sales & Items report.
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
        SalesReportExporter.generateSalesAndItemsPdf(
            title = title,
            storeName = storeName,
            subtitle = subtitle,
            kpis = kpis,
            itemBreakdowns = itemBreakdowns,
            invoices = invoices,
            outputStream = outputStream,
            isArabic = isArabic
        )
    }

    /**
     * Creates a temporary CSV file in cache directory.
     */
    fun createCachedCsv(context: Context, fileName: String, csvContent: String): File =
        ReportFileHelper.createCachedCsv(context, fileName, csvContent)

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
    ): File = SalesReportExporter.createCachedSalesAndItemsCsv(
        context = context,
        fileName = fileName,
        title = title,
        storeName = storeName,
        subtitle = subtitle,
        kpis = kpis,
        itemBreakdowns = itemBreakdowns,
        invoices = invoices,
        isArabic = isArabic
    )

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
    ): File = CustomerReportExporter.createCachedCustomerCsv(
        context = context,
        fileName = fileName,
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

    fun generateSalesAndItemsCsv(
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        itemBreakdowns: List<AggregatedProductLine>,
        invoices: List<TransactionItem>,
        isArabic: Boolean = true
    ): String = SalesReportExporter.generateSalesAndItemsCsv(
        title = title,
        storeName = storeName,
        subtitle = subtitle,
        kpis = kpis,
        itemBreakdowns = itemBreakdowns,
        invoices = invoices,
        isArabic = isArabic
    )

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
    ): File = TransactionReportExporter.createCachedTransactionsCsv(
        context = context,
        fileName = fileName,
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
    ): String = TransactionReportExporter.generateTransactionsCsv(
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
    ): String = CustomerReportExporter.generateCustomerCsv(
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

    fun escapeCsv(value: String): String = ReportFileHelper.escapeCsv(value)

    fun csvRow(vararg cells: String): String = ReportFileHelper.csvRow(*cells)

    fun csvRow(cells: List<String>): String = ReportFileHelper.csvRow(cells)

    fun createCachedTxt(context: Context, fileName: String, content: String): File =
        ReportFileHelper.createCachedTxt(context, fileName, content)

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
    ): File = SalesReportExporter.createCachedSalesAndItemsTxt(
        context = context,
        fileName = fileName,
        title = title,
        storeName = storeName,
        subtitle = subtitle,
        kpis = kpis,
        itemBreakdowns = itemBreakdowns,
        invoices = invoices,
        isArabic = isArabic
    )

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
    ): File = CustomerReportExporter.createCachedCustomerTxt(
        context = context,
        fileName = fileName,
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

    fun generateSalesAndItemsTxt(
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        itemBreakdowns: List<AggregatedProductLine>,
        invoices: List<TransactionItem>,
        isArabic: Boolean = true
    ): String = SalesReportExporter.generateSalesAndItemsTxt(
        title = title,
        storeName = storeName,
        subtitle = subtitle,
        kpis = kpis,
        itemBreakdowns = itemBreakdowns,
        invoices = invoices,
        isArabic = isArabic
    )

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
    ): File = TransactionReportExporter.createCachedTransactionsTxt(
        context = context,
        fileName = fileName,
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
    ): String = TransactionReportExporter.generateTransactionsTxt(
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
    ): String = CustomerReportExporter.generateCustomerTxt(
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
}
