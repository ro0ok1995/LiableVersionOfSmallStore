package com.example.util

import android.content.Context
import com.example.model.AnalyticsReportData
import java.io.File

/**
 * Dedicated export coordinator for Analytics / Statistics reports (PDF, CSV, TXT).
 */
object AnalyticsReportExporter {

    /**
     * Creates a specialized Analytics / Statistics PDF report file in cache directory.
     * Generates a structured vector PDF supporting both ALL_CUSTOMERS and ONE_SELECTED_CUSTOMER.
     */
    fun createCachedAnalyticsPdf(
        context: Context,
        fileName: String,
        data: AnalyticsReportData,
        isArabic: Boolean = true
    ): File = ReportFileHelper.createCachedPdf(context, fileName) { fos ->
        AnalyticsPdfGenerator.generateAnalyticsPdf(
            data = data,
            outputStream = fos,
            isArabic = isArabic
        )
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
        return ReportFileHelper.createCachedCsv(context, fileName, csv)
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
        return ReportFileHelper.createCachedTxt(context, fileName, txt)
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
}
