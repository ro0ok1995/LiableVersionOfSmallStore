package com.example.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.model.AnalyticsChartCategory
import com.example.model.AnalyticsChartType
import com.example.model.AnalyticsReportData
import com.example.model.AnalyticsReportScope
import com.example.model.AppCurrency
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dedicated structured vector PDF generator for Analytics / Statistics reports.
 * Supports both ALL_CUSTOMERS and ONE_SELECTED_CUSTOMER scopes, preserving the user's
 * selected chart presentation (CIRCULAR, BAR, COMBINED), exact KPI calculations,
 * and customer-specific product order breakdowns.
 */
object AnalyticsPdfGenerator {

    private const val PAGE_WIDTH = 595 // A4 portrait width in points
    private const val PAGE_HEIGHT = 842 // A4 portrait height in points
    private const val MARGIN = 32f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (2 * MARGIN) // 531f
    private const val PRINTABLE_BOTTOM_Y = PAGE_HEIGHT - MARGIN - 24f // Reserve room for footer

    // Brand and theme colors
    private const val primaryColor = 0xFF4A3B69.toInt()
    private const val darkTextColor = 0xFF1C1B1F.toInt()
    private const val grayTextColor = 0xFF605D62.toInt()
    private const val lightBgColor = 0xFFF5F3F7.toInt()
    private const val headerBgColor = 0xFFEDE9F2.toInt()
    private const val borderColor = 0xFFD9D5DC.toInt()
    private const val altRowColor = 0xFFFBFBFC.toInt()
    private const val whiteColor = 0xFFFFFFFF.toInt()
    private const val greenTextColor = 0xFF1B5E20.toInt()
    private const val trackLightColor = 0x50D9D5DC.toInt()

    // Chart category colors matching application theme
    private const val debtColor = 0xFFE53935.toInt()    // Red
    private const val cashColor = 0xFF1E88E5.toInt()    // Blue
    private const val fullPayColor = 0xFF43A047.toInt() // Green
    private const val partPayColor = 0xFFFB8C00.toInt() // Amber

    fun generateAnalyticsPdf(
        data: AnalyticsReportData,
        outputStream: OutputStream,
        isArabic: Boolean = true
    ) {
        val pdfDocument = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true }
        val currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

        val reportTitle = if (data.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) {
            if (isArabic) "تقرير إحصائيات العميل" else "Customer Analytics Report"
        } else {
            if (isArabic) "تقرير الإحصائيات" else "Analytics Report"
        }

        val periodSubtitle = if (isArabic) {
            "فترة التقرير: ${data.getLocalizedPeriodLabel(isArabic)}"
        } else {
            "Period: ${data.getLocalizedPeriodLabel(isArabic)}"
        }

        // Table column dimensions for Breakdown table (Category 40%, Amount 25%, Percentage 17%, Currency 18%)
        val breakdownWeights = floatArrayOf(0.40f, 0.25f, 0.17f, 0.18f)
        val breakdownColWidths = FloatArray(breakdownWeights.size) { i -> CONTENT_WIDTH * breakdownWeights[i] }
        val breakdownColStarts = FloatArray(breakdownWeights.size)
        val breakdownColEnds = FloatArray(breakdownWeights.size)
        setupColumnPositions(breakdownColWidths, breakdownColStarts, breakdownColEnds, isArabic)

        // Table column dimensions for Most Ordered Products table (Rank 12%, Product 50%, Quantity 16%, Total 22%)
        val prodWeights = floatArrayOf(0.12f, 0.50f, 0.16f, 0.22f)
        val prodColWidths = FloatArray(prodWeights.size) { i -> CONTENT_WIDTH * prodWeights[i] }
        val prodColStarts = FloatArray(prodWeights.size)
        val prodColEnds = FloatArray(prodWeights.size)
        setupColumnPositions(prodColWidths, prodColStarts, prodColEnds, isArabic)

        // -------------------------------------------------------------
        // Two-pass calculation: Calculate total pages for accurate footers
        // -------------------------------------------------------------
        fun calculateTotalPages(): Int {
            var simPage = 1
            var simY = MARGIN

            // 1. Header Banner + meta
            simY += 72f + 14f + 22f

            // 2. Customer Card if ONE_SELECTED_CUSTOMER
            if (data.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER && data.selectedCustomer != null) {
                simY += 52f + 12f
            }

            // 3. KPI Summary Cards
            val kpiRows = if (data.scope == AnalyticsReportScope.ALL_CUSTOMERS) 3 else 3
            simY += 22f + (kpiRows * 42f) + 14f

            // 4. Chart Section & Distribution Header
            simY += 26f // Section title bar
            val chartBoxHeight = when (data.selectedChartType) {
                AnalyticsChartType.CIRCULAR -> 118f
                AnalyticsChartType.BAR -> 110f
                AnalyticsChartType.COMBINED -> 118f
            }
            simY += chartBoxHeight + 10f

            // 5. Breakdown Table (Header + 4 rows + Total row)
            val breakdownHeight = 24f + (4 * 20f) + 22f + 14f
            if (simY + breakdownHeight > PRINTABLE_BOTTOM_Y) {
                simPage++
                simY = MARGIN + 45f + breakdownHeight
            } else {
                simY += breakdownHeight
            }

            // 6. Most Ordered Products (Only for ONE_SELECTED_CUSTOMER)
            if (data.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) {
                val prodHeaderHeight = 24f + 24f
                val rowCount = if (data.mostOrderedProducts.isEmpty()) 1 else data.mostOrderedProducts.size + 1
                val totalProdsHeight = prodHeaderHeight + (rowCount * 20f) + 12f

                if (simY + 48f > PRINTABLE_BOTTOM_Y) {
                    simPage++
                    simY = MARGIN + 45f
                }
                simY += 24f + 24f // section header + table header
                for (p in data.mostOrderedProducts) {
                    if (simY + 20f > PRINTABLE_BOTTOM_Y) {
                        simPage++
                        simY = MARGIN + 45f + 24f + 20f
                    } else {
                        simY += 20f
                    }
                }
                // total row or empty row
                if (simY + 22f > PRINTABLE_BOTTOM_Y) {
                    simPage++
                    simY = MARGIN + 45f + 22f
                } else {
                    simY += 22f
                }
            }

            // 7. Summary / Status Notes block
            if (simY + 44f > PRINTABLE_BOTTOM_Y) {
                simPage++
            }
            return simPage
        }

        val totalPages = calculateTotalPages()

        // -------------------------------------------------------------
        // Page management state
        // -------------------------------------------------------------
        var currentPageNum = 1
        var currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNum).create())
        var canvas = currentPage.canvas
        var currentY = MARGIN

        fun drawFooter() {
            paint.color = borderColor
            paint.strokeWidth = 0.8f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(MARGIN, PAGE_HEIGHT - MARGIN - 14f, MARGIN + CONTENT_WIDTH, PAGE_HEIGHT - MARGIN - 14f, paint)

            paint.style = Paint.Style.FILL
            paint.color = grayTextColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            val pageStr = if (isArabic) "صفحة $currentPageNum من $totalPages" else "Page $currentPageNum of $totalPages"
            val footerBranding = if (isArabic) "سمول ستور  |  تقرير إحصائيات معتمد" else "SmallStore  |  Verified Analytics Report"

            if (isArabic) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(footerBranding, MARGIN + CONTENT_WIDTH, PAGE_HEIGHT - MARGIN - 2f, paint)
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(pageStr, MARGIN, PAGE_HEIGHT - MARGIN - 2f, paint)
            } else {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(footerBranding, MARGIN, PAGE_HEIGHT - MARGIN - 2f, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(pageStr, MARGIN + CONTENT_WIDTH, PAGE_HEIGHT - MARGIN - 2f, paint)
            }
        }

        fun startNewPage() {
            drawFooter()
            pdfDocument.finishPage(currentPage)
            currentPageNum++
            currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNum).create())
            canvas = currentPage.canvas
            currentY = MARGIN

            // Continuation Banner
            val bannerH = 34f
            paint.color = primaryColor
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + bannerH, 6f, 6f, paint)

            paint.color = whiteColor
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val contTitle = if (isArabic) {
                "${data.storeName}  |  $reportTitle (تابع)"
            } else {
                "${data.storeName}  |  $reportTitle (Continued)"
            }

            if (isArabic) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(contTitle, MARGIN + CONTENT_WIDTH - 12f, currentY + 22f, paint)
            } else {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(contTitle, MARGIN + 12f, currentY + 22f, paint)
            }
            currentY += bannerH + 12f
        }

        // =============================================================
        // 1. TOP HEADER BANNER
        // =============================================================
        val bannerHeight = 72f
        paint.color = primaryColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + bannerHeight, 8f, 8f, paint)

        // Store branding
        paint.color = whiteColor
        paint.textSize = 17f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val storeBrand = if (isArabic) "سمول ستور  |  ${data.storeName}" else "SmallStore  |  ${data.storeName}"
        if (isArabic) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(storeBrand, MARGIN + CONTENT_WIDTH - 16f, currentY + 30f, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(storeBrand, MARGIN + 16f, currentY + 30f, paint)
        }

        // Report Title
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        if (isArabic) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(reportTitle, MARGIN + CONTENT_WIDTH - 16f, currentY + 54f, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(reportTitle, MARGIN + 16f, currentY + 54f, paint)
        }
        currentY += bannerHeight + 12f

        // Meta Line: Subtitle & Generation Timestamp
        paint.color = grayTextColor
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        if (isArabic) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(periodSubtitle, MARGIN + CONTENT_WIDTH, currentY + 10f, paint)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("تاريخ الإصدار: $currentDate", MARGIN, currentY + 10f, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(periodSubtitle, MARGIN, currentY + 10f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Generated: $currentDate", MARGIN + CONTENT_WIDTH, currentY + 10f, paint)
        }
        currentY += 22f

        // =============================================================
        // 2. CUSTOMER INFORMATION CARD (ONE_SELECTED_CUSTOMER ONLY)
        // =============================================================
        if (data.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER && data.selectedCustomer != null) {
            val cust = data.selectedCustomer
            val custCardHeight = 48f
            paint.color = lightBgColor
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + custCardHeight, 6f, 6f, paint)

            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            canvas.drawRoundRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + custCardHeight, 6f, 6f, paint)

            paint.style = Paint.Style.FILL
            paint.color = primaryColor
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val custLabel = if (isArabic) "العميل: ${cust.customerName}" else "Customer: ${cust.customerName}"
            if (isArabic) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(custLabel, MARGIN + CONTENT_WIDTH - 14f, currentY + 20f, paint)
            } else {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(custLabel, MARGIN + 14f, currentY + 20f, paint)
            }

            paint.color = grayTextColor
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val phoneText = if (cust.phone.isNotBlank()) {
                if (isArabic) "رقم الهاتف: ${cust.phone}" else "Phone: ${cust.phone}"
            } else {
                if (isArabic) "رقم الهاتف: غير محدد" else "Phone: Not Specified"
            }
            if (isArabic) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(phoneText, MARGIN + CONTENT_WIDTH - 14f, currentY + 38f, paint)
            } else {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(phoneText, MARGIN + 14f, currentY + 38f, paint)
            }

            // Current balance status on opposite side
            val balanceStatusText = if (cust.currentBalance > 0) {
                if (isArabic) "الرصيد المستحق: ${AppCurrency.formatAmountWithDecimals(cust.currentBalance, isArabic)}"
                else "Outstanding Balance: ${AppCurrency.formatAmountWithDecimals(cust.currentBalance, isArabic)}"
            } else {
                if (isArabic) "الحساب مسدد بالكامل (0.00 ₪)" else "Fully Settled Account (0.00 ₪)"
            }
            paint.color = if (cust.currentBalance > 0) debtColor else fullPayColor
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            if (isArabic) {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(balanceStatusText, MARGIN + 14f, currentY + 28f, paint)
            } else {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(balanceStatusText, MARGIN + CONTENT_WIDTH - 14f, currentY + 28f, paint)
            }
            currentY += custCardHeight + 12f
        }

        // =============================================================
        // 3. SUMMARY KPI SECTION
        // =============================================================
        paint.color = darkTextColor
        paint.textSize = 11.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        if (isArabic) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("ملخص المؤشرات المالية", MARGIN + CONTENT_WIDTH, currentY + 10f, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Financial KPI Summary", MARGIN, currentY + 10f, paint)
        }
        currentY += 16f

        val kpiList = mutableListOf<Pair<String, String>>()
        kpiList.add((if (isArabic) "إجمالي المبيعات" else "Total Sales") to AppCurrency.formatAmountWithDecimals(data.metrics.totalSales, isArabic))
        kpiList.add((if (isArabic) "مبيعات الآجل" else "Debt Sales") to AppCurrency.formatAmountWithDecimals(data.metrics.totalDebtSales, isArabic))
        kpiList.add((if (isArabic) "مبيعات كاش" else "Cash Sales") to AppCurrency.formatAmountWithDecimals(data.metrics.totalCashSales, isArabic))
        kpiList.add((if (isArabic) "المتحصلات" else "Payments Received") to AppCurrency.formatAmountWithDecimals(data.metrics.totalPaymentsReceived, isArabic))
        kpiList.add((if (isArabic) "تسديد كامل" else "Full Settlement") to AppCurrency.formatAmountWithDecimals(data.metrics.fullSettlementAmount, isArabic))
        kpiList.add((if (isArabic) "تسديد جزئي" else "Partial Settlement") to AppCurrency.formatAmountWithDecimals(data.metrics.partialSettlementAmount, isArabic))
        kpiList.add((if (isArabic) "الصافي المتبقي" else "Net Outstanding") to AppCurrency.formatAmountWithDecimals(data.metrics.netOutstandingBalance, isArabic))
        kpiList.add((if (isArabic) "عدد العمليات" else "Operations Count") to data.metrics.transactionCount.toString())
        if (data.scope == AnalyticsReportScope.ALL_CUSTOMERS && data.metrics.customerCount != null) {
            kpiList.add((if (isArabic) "إجمالي العملاء" else "Customer Count") to data.metrics.customerCount.toString())
        }

        val kpiCols = 3
        val kpiGap = 6f
        val kpiCardW = (CONTENT_WIDTH - ((kpiCols - 1) * kpiGap)) / kpiCols
        val kpiCardH = 38f

        kpiList.chunked(kpiCols).forEach { rowKpis ->
            for (c in rowKpis.indices) {
                val item = rowKpis[c]
                val cardX = if (isArabic) {
                    MARGIN + CONTENT_WIDTH - ((c + 1) * kpiCardW) - (c * kpiGap)
                } else {
                    MARGIN + (c * (kpiCardW + kpiGap))
                }

                paint.color = lightBgColor
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(cardX, currentY, cardX + kpiCardW, currentY + kpiCardH, 4f, 4f, paint)

                paint.color = borderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.6f
                canvas.drawRoundRect(cardX, currentY, cardX + kpiCardW, currentY + kpiCardH, 4f, 4f, paint)

                // Label
                paint.style = Paint.Style.FILL
                paint.color = grayTextColor
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(item.first, cardX + kpiCardW - 8f, currentY + 14f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(item.first, cardX + 8f, currentY + 14f, paint)
                }

                // Value
                paint.color = if (item.first.contains("آجل") || item.first.contains("Debt") || item.first.contains("الصافي") || item.first.contains("Net")) {
                    primaryColor
                } else if (item.first.contains("كاش") || item.first.contains("Cash") || item.first.contains("كامل") || item.first.contains("المتحصلات")) {
                    greenTextColor
                } else {
                    darkTextColor
                }
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(item.second, cardX + kpiCardW - 8f, currentY + 30f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(item.second, cardX + 8f, currentY + 30f, paint)
                }
            }
            currentY += kpiCardH + kpiGap
        }
        currentY += 8f

        // =============================================================
        // 4. CHART SECTION (OPERATIONS AND DEBT DISTRIBUTION)
        // =============================================================
        // Section Header Bar
        paint.color = headerBgColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + 24f, 4f, 4f, paint)

        paint.color = primaryColor
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val sectionTitle = if (isArabic) "توزيع حجم العمليات والديون" else "Operations and Debt Distribution"
        val chartBadge = "[ ${data.getLocalizedChartTypeName(isArabic)} ]"

        if (isArabic) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(sectionTitle, MARGIN + CONTENT_WIDTH - 10f, currentY + 16f, paint)
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 9f
            paint.color = grayTextColor
            canvas.drawText(chartBadge, MARGIN + 10f, currentY + 16f, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(sectionTitle, MARGIN + 10f, currentY + 16f, paint)
            paint.textAlign = Paint.Align.RIGHT
            paint.textSize = 9f
            paint.color = grayTextColor
            canvas.drawText(chartBadge, MARGIN + CONTENT_WIDTH - 10f, currentY + 16f, paint)
        }
        currentY += 28f

        // Render Vector Chart according to selected chart presentation
        when (data.selectedChartType) {
            AnalyticsChartType.CIRCULAR -> {
                drawCircularDonutChart(
                    canvas = canvas,
                    paint = paint,
                    startY = currentY,
                    data = data,
                    isArabic = isArabic
                )
                currentY += 118f
            }
            AnalyticsChartType.BAR -> {
                drawBarColumnChart(
                    canvas = canvas,
                    paint = paint,
                    startY = currentY,
                    data = data,
                    isArabic = isArabic
                )
                currentY += 110f
            }
            AnalyticsChartType.COMBINED -> {
                drawCombinedChart(
                    canvas = canvas,
                    paint = paint,
                    startY = currentY,
                    data = data,
                    isArabic = isArabic
                )
                currentY += 118f
            }
        }

        // =============================================================
        // 5. BREAKDOWN TABLE
        // =============================================================
        if (currentY + 130f > PRINTABLE_BOTTOM_Y) {
            startNewPage()
        }

        // Table Header
        paint.color = primaryColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + 22f, paint)

        val breakdownHeaders = if (isArabic) {
            listOf("البند / التصنيف", "المبلغ", "النسبة", "العملة")
        } else {
            listOf("Category / Classification", "Amount", "Percentage", "Currency")
        }

        paint.color = whiteColor
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        for (i in breakdownHeaders.indices) {
            val align = if (i == 0) {
                if (isArabic) Paint.Align.RIGHT else Paint.Align.LEFT
            } else if (i == 1 || i == 2) {
                Paint.Align.RIGHT
            } else {
                Paint.Align.CENTER
            }
            paint.textAlign = align
            val textX = when (align) {
                Paint.Align.RIGHT -> breakdownColEnds[i] - 6f
                Paint.Align.LEFT -> breakdownColStarts[i] + 6f
                else -> (breakdownColStarts[i] + breakdownColEnds[i]) / 2f
            }
            canvas.drawText(breakdownHeaders[i], textX, currentY + 15f, paint)
        }
        currentY += 22f

        // Table Data Rows
        val rowHeight = 20f
        for ((index, cat) in data.chartData.withIndex()) {
            if (currentY + rowHeight > PRINTABLE_BOTTOM_Y) {
                startNewPage()
            }

            paint.color = if (index % 2 == 1) altRowColor else whiteColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + rowHeight, paint)

            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.5f
            canvas.drawLine(MARGIN, currentY + rowHeight, MARGIN + CONTENT_WIDTH, currentY + rowHeight, paint)

            // Category color swatch
            val swatchColor = when (cat.categoryKey) {
                "DEBT" -> debtColor
                "CASH" -> cashColor
                "FULL_PAYMENT" -> fullPayColor
                else -> partPayColor
            }
            paint.style = Paint.Style.FILL
            paint.color = swatchColor
            val swatchX = if (isArabic) breakdownColEnds[0] - 12f else breakdownColStarts[0] + 6f
            canvas.drawCircle(swatchX + 3f, currentY + (rowHeight / 2f), 3.5f, paint)

            // Category Name
            paint.color = darkTextColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            if (isArabic) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(cat.getLocalizedLabel(isArabic), swatchX - 6f, currentY + 14f, paint)
            } else {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(cat.getLocalizedLabel(isArabic), swatchX + 12f, currentY + 14f, paint)
            }

            // Amount
            paint.textAlign = Paint.Align.RIGHT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val amtStr = String.format(Locale.US, "%,.2f", cat.amount)
            canvas.drawText(amtStr, breakdownColEnds[1] - 6f, currentY + 14f, paint)

            // Percentage
            paint.textAlign = Paint.Align.RIGHT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val pctStr = "${cat.percentage}%"
            canvas.drawText(pctStr, breakdownColEnds[2] - 6f, currentY + 14f, paint)

            // Currency
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(data.currency, (breakdownColStarts[3] + breakdownColEnds[3]) / 2f, currentY + 14f, paint)

            currentY += rowHeight
        }

        // Breakdown Total Row
        paint.color = headerBgColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + 22f, paint)

        paint.color = darkTextColor
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val totalLabel = if (isArabic) "إجمالي حجم العمليات والديون" else "Total Operations & Volume"
        if (isArabic) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(totalLabel, breakdownColEnds[0] - 6f, currentY + 15f, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(totalLabel, breakdownColStarts[0] + 6f, currentY + 15f, paint)
        }

        paint.textAlign = Paint.Align.RIGHT
        val totalAmtStr = String.format(Locale.US, "%,.2f", data.totalVolume)
        canvas.drawText(totalAmtStr, breakdownColEnds[1] - 6f, currentY + 15f, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("100%", breakdownColEnds[2] - 6f, currentY + 15f, paint)

        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(data.currency, (breakdownColStarts[3] + breakdownColEnds[3]) / 2f, currentY + 15f, paint)
        currentY += 26f

        // =============================================================
        // 6. SELECTED CUSTOMER — MOST ORDERED PRODUCTS
        // =============================================================
        if (data.scope == AnalyticsReportScope.ONE_SELECTED_CUSTOMER) {
            if (currentY + 80f > PRINTABLE_BOTTOM_Y) {
                startNewPage()
            }

            // Section Header
            paint.color = primaryColor
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + 22f, 4f, 4f, paint)

            paint.color = whiteColor
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val prodSectionTitle = if (isArabic) "أكثر الأصناف طلباً لهذا العميل" else "Most Ordered Products for This Customer"
            if (isArabic) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(prodSectionTitle, MARGIN + CONTENT_WIDTH - 10f, currentY + 15f, paint)
            } else {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(prodSectionTitle, MARGIN + 10f, currentY + 15f, paint)
            }
            currentY += 24f

            // Products Table Header
            paint.color = headerBgColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + 20f, paint)

            val prodHeaders = if (isArabic) {
                listOf("#", "اسم الصنف", "الكمية", "إجمالي القيمة")
            } else {
                listOf("#", "Product Name", "Quantity", "Total Amount")
            }

            paint.color = darkTextColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            for (i in prodHeaders.indices) {
                val align = if (i == 1) {
                    if (isArabic) Paint.Align.RIGHT else Paint.Align.LEFT
                } else if (i == 2 || i == 3) {
                    Paint.Align.RIGHT
                } else {
                    Paint.Align.CENTER
                }
                paint.textAlign = align
                val textX = when (align) {
                    Paint.Align.RIGHT -> prodColEnds[i] - 6f
                    Paint.Align.LEFT -> prodColStarts[i] + 6f
                    else -> (prodColStarts[i] + prodColEnds[i]) / 2f
                }
                canvas.drawText(prodHeaders[i], textX, currentY + 14f, paint)
            }
            currentY += 20f

            if (data.mostOrderedProducts.isEmpty()) {
                paint.color = whiteColor
                paint.style = Paint.Style.FILL
                canvas.drawRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + rowHeight, paint)

                paint.color = grayTextColor
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                paint.textAlign = Paint.Align.CENTER
                val emptyMsg = if (isArabic) {
                    "لا توجد أصناف مسجلة لهذا العميل في هذه الفترة"
                } else {
                    "No product lines recorded for this customer in this period"
                }
                canvas.drawText(emptyMsg, MARGIN + (CONTENT_WIDTH / 2f), currentY + 14f, paint)
                currentY += rowHeight
            } else {
                var totalQtySum = 0
                var totalAmountSum = 0.0

                for ((idx, item) in data.mostOrderedProducts.withIndex()) {
                    if (currentY + rowHeight > PRINTABLE_BOTTOM_Y) {
                        startNewPage()
                        // Re-draw table header on new page
                        paint.color = headerBgColor
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + 20f, paint)
                        paint.color = darkTextColor
                        paint.textSize = 8.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        for (i in prodHeaders.indices) {
                            val align = if (i == 1) (if (isArabic) Paint.Align.RIGHT else Paint.Align.LEFT) else if (i == 2 || i == 3) Paint.Align.RIGHT else Paint.Align.CENTER
                            paint.textAlign = align
                            val textX = when (align) {
                                Paint.Align.RIGHT -> prodColEnds[i] - 6f
                                Paint.Align.LEFT -> prodColStarts[i] + 6f
                                else -> (prodColStarts[i] + prodColEnds[i]) / 2f
                            }
                            canvas.drawText(prodHeaders[i], textX, currentY + 14f, paint)
                        }
                        currentY += 20f
                    }

                    totalQtySum += item.totalQuantity
                    totalAmountSum += item.totalSales

                    paint.color = if (idx % 2 == 1) altRowColor else whiteColor
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + rowHeight, paint)

                    paint.color = borderColor
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.5f
                    canvas.drawLine(MARGIN, currentY + rowHeight, MARGIN + CONTENT_WIDTH, currentY + rowHeight, paint)

                    // Rank
                    paint.style = Paint.Style.FILL
                    paint.color = grayTextColor
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText("${idx + 1}", (prodColStarts[0] + prodColEnds[0]) / 2f, currentY + 14f, paint)

                    // Product Name
                    paint.color = darkTextColor
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    if (isArabic) {
                        paint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(item.productName, prodColEnds[1] - 6f, currentY + 14f, paint)
                    } else {
                        paint.textAlign = Paint.Align.LEFT
                        canvas.drawText(item.productName, prodColStarts[1] + 6f, currentY + 14f, paint)
                    }

                    // Quantity
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("${item.totalQuantity}", prodColEnds[2] - 6f, currentY + 14f, paint)

                    // Total Sales
                    paint.textAlign = Paint.Align.RIGHT
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val salesStr = AppCurrency.formatAmountWithDecimals(item.totalSales, isArabic)
                    canvas.drawText(salesStr, prodColEnds[3] - 6f, currentY + 14f, paint)

                    currentY += rowHeight
                }

                // Total Row for Products
                paint.color = lightBgColor
                paint.style = Paint.Style.FILL
                canvas.drawRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + 22f, paint)

                paint.color = darkTextColor
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val sumLabel = if (isArabic) "إجمالي مشتريات الأصناف" else "Total Product Purchases"
                if (isArabic) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(sumLabel, prodColEnds[1] - 6f, currentY + 15f, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(sumLabel, prodColStarts[1] + 6f, currentY + 15f, paint)
                }

                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("$totalQtySum", prodColEnds[2] - 6f, currentY + 15f, paint)

                val sumStr = AppCurrency.formatAmountWithDecimals(totalAmountSum, isArabic)
                canvas.drawText(sumStr, prodColEnds[3] - 6f, currentY + 15f, paint)
                currentY += 26f
            }
        }

        // =============================================================
        // 7. FINAL SUMMARY / NET BALANCE STATEMENT
        // =============================================================
        if (currentY + 44f > PRINTABLE_BOTTOM_Y) {
            startNewPage()
        }

        paint.color = lightBgColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + 36f, 6f, 6f, paint)

        paint.color = borderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        canvas.drawRoundRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + 36f, 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        paint.color = darkTextColor
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val netStatusSummary = if (isArabic) {
            "صافي الرصيد المتبقي لهذه الفترة: ${AppCurrency.formatAmountWithDecimals(data.metrics.netOutstandingBalance, isArabic)} (إجمالي العمليات: ${data.metrics.transactionCount})"
        } else {
            "Net Outstanding Balance for Period: ${AppCurrency.formatAmountWithDecimals(data.metrics.netOutstandingBalance, isArabic)} (Total Operations: ${data.metrics.transactionCount})"
        }

        if (isArabic) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(netStatusSummary, MARGIN + CONTENT_WIDTH - 12f, currentY + 22f, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(netStatusSummary, MARGIN + 12f, currentY + 22f, paint)
        }

        // Finish last page
        drawFooter()
        pdfDocument.finishPage(currentPage)

        // Write to output stream
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }

    // -------------------------------------------------------------
    // CHART 1: CIRCULAR (DONUT) VECTOR RENDERING
    // -------------------------------------------------------------
    private fun drawCircularDonutChart(
        canvas: Canvas,
        paint: Paint,
        startY: Float,
        data: AnalyticsReportData,
        isArabic: Boolean
    ) {
        val totalVolume = data.totalVolume
        val donutBoxH = 110f
        val centerX = if (isArabic) MARGIN + CONTENT_WIDTH - 85f else MARGIN + 85f
        val centerY = startY + (donutBoxH / 2f)
        val radius = 42f
        val innerRadius = 24f

        val oval = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        if (totalVolume <= 0.0) {
            // Empty ring
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 14f
            paint.color = borderColor
            canvas.drawCircle(centerX, centerY, (radius + innerRadius) / 2f, paint)

            paint.style = Paint.Style.FILL
            paint.color = grayTextColor
            paint.textSize = 8.5f
            paint.textAlign = Paint.Align.CENTER
            val emptyTxt = if (isArabic) "لا توجد عمليات" else "No Data"
            canvas.drawText(emptyTxt, centerX, centerY + 3f, paint)
        } else {
            // Draw donut ring segments using drawArc with thick stroke
            var startAngle = -90f
            val ringThickness = radius - innerRadius
            val ringMidRadius = innerRadius + (ringThickness / 2f)
            val ringOval = RectF(centerX - ringMidRadius, centerY - ringMidRadius, centerX + ringMidRadius, centerY + ringMidRadius)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = ringThickness

            for (cat in data.chartData) {
                if (cat.percentage <= 0) continue
                val sweepAngle = (cat.percentage / 100f) * 360f
                paint.color = when (cat.categoryKey) {
                    "DEBT" -> debtColor
                    "CASH" -> cashColor
                    "FULL_PAYMENT" -> fullPayColor
                    else -> partPayColor
                }
                canvas.drawArc(ringOval, startAngle, sweepAngle, false, paint)
                startAngle += sweepAngle
            }

            // Center hole text
            paint.style = Paint.Style.FILL
            paint.color = grayTextColor
            paint.textSize = 7f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.CENTER
            val centerLabel = if (isArabic) "إجمالي الحجم" else "Total Vol"
            canvas.drawText(centerLabel, centerX, centerY - 2f, paint)

            paint.color = darkTextColor
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val centerVal = String.format(Locale.US, "%,.0f %s", totalVolume, data.currency)
            canvas.drawText(centerVal, centerX, centerY + 9f, paint)
        }

        // Legend blocks next to donut
        val legendLeft = if (isArabic) MARGIN + 16f else MARGIN + 180f
        val legendWidth = CONTENT_WIDTH - 200f
        var legY = startY + 12f

        for (cat in data.chartData) {
            val swatchColor = when (cat.categoryKey) {
                "DEBT" -> debtColor
                "CASH" -> cashColor
                "FULL_PAYMENT" -> fullPayColor
                else -> partPayColor
            }

            // Color indicator
            paint.style = Paint.Style.FILL
            paint.color = swatchColor
            val boxX = if (isArabic) legendLeft + legendWidth - 12f else legendLeft
            canvas.drawRoundRect(boxX, legY, boxX + 10f, legY + 10f, 2f, 2f, paint)

            // Category Label & Percentage
            paint.color = darkTextColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val labelStr = "${cat.getLocalizedLabel(isArabic)} (${cat.percentage}%)"
            val valStr = AppCurrency.formatAmountWithDecimals(cat.amount, isArabic)

            if (isArabic) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(labelStr, boxX - 8f, legY + 9f, paint)
                paint.textAlign = Paint.Align.LEFT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(valStr, legendLeft, legY + 9f, paint)
            } else {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(labelStr, boxX + 16f, legY + 9f, paint)
                paint.textAlign = Paint.Align.RIGHT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(valStr, legendLeft + legendWidth, legY + 9f, paint)
            }
            legY += 22f
        }
    }

    // -------------------------------------------------------------
    // CHART 2: BAR / COLUMN VECTOR RENDERING
    // -------------------------------------------------------------
    private fun drawBarColumnChart(
        canvas: Canvas,
        paint: Paint,
        startY: Float,
        data: AnalyticsReportData,
        isArabic: Boolean
    ) {
        val totalVolume = data.totalVolume.coerceAtLeast(1.0)
        var barY = startY + 8f
        val labelW = 120f
        val valueW = 90f
        val barTrackW = CONTENT_WIDTH - labelW - valueW - 16f
        val barH = 12f

        for (cat in data.chartData) {
            val swatchColor = when (cat.categoryKey) {
                "DEBT" -> debtColor
                "CASH" -> cashColor
                "FULL_PAYMENT" -> fullPayColor
                else -> partPayColor
            }

            // Category Label
            paint.style = Paint.Style.FILL
            paint.color = darkTextColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            if (isArabic) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(cat.getLocalizedLabel(isArabic), MARGIN + CONTENT_WIDTH, barY + 9f, paint)
            } else {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(cat.getLocalizedLabel(isArabic), MARGIN, barY + 9f, paint)
            }

            // Background Track
            val trackX = if (isArabic) {
                MARGIN + valueW + 8f
            } else {
                MARGIN + labelW + 8f
            }
            paint.color = trackLightColor
            canvas.drawRoundRect(trackX, barY, trackX + barTrackW, barY + barH, 4f, 4f, paint)

            // Filled Bar proportional to volume percentage
            val fillRatio = (cat.amount / totalVolume).toFloat().coerceIn(0f, 1f)
            val fillW = (barTrackW * fillRatio).coerceAtLeast(if (cat.amount > 0) 4f else 0f)

            paint.color = swatchColor
            if (isArabic) {
                // RTL: Fill from right to left inside track
                val fillStartX = trackX + barTrackW - fillW
                canvas.drawRoundRect(fillStartX, barY, trackX + barTrackW, barY + barH, 4f, 4f, paint)
            } else {
                // LTR: Fill from left to right inside track
                canvas.drawRoundRect(trackX, barY, trackX + fillW, barY + barH, 4f, 4f, paint)
            }

            // Value Text (Amount + Percentage)
            paint.color = darkTextColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val valText = "${AppCurrency.formatAmountWithDecimals(cat.amount, isArabic)} (${cat.percentage}%)"
            if (isArabic) {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(valText, MARGIN, barY + 9f, paint)
            } else {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(valText, MARGIN + CONTENT_WIDTH, barY + 9f, paint)
            }
            barY += 24f
        }
    }

    // -------------------------------------------------------------
    // CHART 3: COMBINED VECTOR RENDERING
    // -------------------------------------------------------------
    private fun drawCombinedChart(
        canvas: Canvas,
        paint: Paint,
        startY: Float,
        data: AnalyticsReportData,
        isArabic: Boolean
    ) {
        val totalSales = (data.metrics.totalSales).coerceAtLeast(0.0)
        val totalPayments = (data.metrics.totalPaymentsReceived).coerceAtLeast(0.0)
        val totalVolume = data.totalVolume.coerceAtLeast(1.0)

        // Subtitle / Label: Sales Volume vs Collections Volume
        paint.color = grayTextColor
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val comboSubtitle = if (isArabic) {
            "مقارنة حجم المبيعات مقابل إجمالي المتحصلات وتوزيع الشرائح"
        } else {
            "Sales Volume vs Collections & Proportional Distribution"
        }
        if (isArabic) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(comboSubtitle, MARGIN + CONTENT_WIDTH, startY + 10f, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(comboSubtitle, MARGIN, startY + 10f, paint)
        }

        // 1. Dual Macro Bars (Sales vs Collections)
        val macroBarY = startY + 18f
        val macroBarH = 14f
        val macroLabelW = 85f
        val macroValW = 85f
        val macroTrackW = CONTENT_WIDTH - macroLabelW - macroValW - 16f
        val maxMacro = maxOf(totalSales, totalPayments, 1.0)

        // Sales Bar
        val salesLabel = if (isArabic) "حجم المبيعات" else "Sales Volume"
        val salesValStr = AppCurrency.formatAmountWithDecimals(totalSales, isArabic)
        drawMacroRow(canvas, paint, macroBarY, salesLabel, salesValStr, (totalSales / maxMacro).toFloat(), primaryColor, isArabic, macroLabelW, macroValW, macroTrackW, macroBarH)

        // Payments Bar
        val payLabel = if (isArabic) "المتحصلات" else "Collections"
        val payValStr = AppCurrency.formatAmountWithDecimals(totalPayments, isArabic)
        drawMacroRow(canvas, paint, macroBarY + 22f, payLabel, payValStr, (totalPayments / maxMacro).toFloat(), fullPayColor, isArabic, macroLabelW, macroValW, macroTrackW, macroBarH)

        // 2. Segmented Proportional Strip (4 Categories)
        val stripY = macroBarY + 48f
        val stripH = 12f

        paint.color = trackLightColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(MARGIN, stripY, MARGIN + CONTENT_WIDTH, stripY + stripH, 4f, 4f, paint)

        var curStripX = if (isArabic) MARGIN + CONTENT_WIDTH else MARGIN
        for (cat in data.chartData) {
            if (cat.percentage <= 0) continue
            val segW = (CONTENT_WIDTH * (cat.percentage / 100f))
            paint.color = when (cat.categoryKey) {
                "DEBT" -> debtColor
                "CASH" -> cashColor
                "FULL_PAYMENT" -> fullPayColor
                else -> partPayColor
            }
            if (isArabic) {
                canvas.drawRect(curStripX - segW, stripY, curStripX, stripY + stripH, paint)
                curStripX -= segW
            } else {
                canvas.drawRect(curStripX, stripY, curStripX + segW, stripY + stripH, paint)
                curStripX += segW
            }
        }

        // Strip Legend below
        val legY = stripY + 22f
        val colCount = 4
        val legColW = CONTENT_WIDTH / colCount
        for (i in data.chartData.indices) {
            val cat = data.chartData[i]
            val swatchColor = when (cat.categoryKey) {
                "DEBT" -> debtColor
                "CASH" -> cashColor
                "FULL_PAYMENT" -> fullPayColor
                else -> partPayColor
            }
            val itemX = if (isArabic) {
                MARGIN + CONTENT_WIDTH - ((i + 1) * legColW)
            } else {
                MARGIN + (i * legColW)
            }

            paint.color = swatchColor
            paint.style = Paint.Style.FILL
            val boxX = if (isArabic) itemX + legColW - 10f else itemX
            canvas.drawCircle(boxX + 4f, legY + 4f, 3.5f, paint)

            paint.color = darkTextColor
            paint.textSize = 7.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val legTxt = "${cat.getLocalizedLabel(isArabic)}: ${cat.percentage}%"
            if (isArabic) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(legTxt, boxX - 4f, legY + 7f, paint)
            } else {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(legTxt, boxX + 10f, legY + 7f, paint)
            }
        }
    }

    private fun drawMacroRow(
        canvas: Canvas,
        paint: Paint,
        y: Float,
        label: String,
        valStr: String,
        ratio: Float,
        barColor: Int,
        isArabic: Boolean,
        labelW: Float,
        valW: Float,
        trackW: Float,
        barH: Float
    ) {
        paint.color = darkTextColor
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        if (isArabic) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(label, MARGIN + CONTENT_WIDTH, y + 10f, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, MARGIN, y + 10f, paint)
        }

        val trackX = if (isArabic) MARGIN + valW + 8f else MARGIN + labelW + 8f
        paint.color = trackLightColor
        canvas.drawRoundRect(trackX, y, trackX + trackW, y + barH, 4f, 4f, paint)

        val fillW = (trackW * ratio).coerceIn(4f, trackW)
        paint.color = barColor
        if (isArabic) {
            canvas.drawRoundRect(trackX + trackW - fillW, y, trackX + trackW, y + barH, 4f, 4f, paint)
        } else {
            canvas.drawRoundRect(trackX, y, trackX + fillW, y + barH, 4f, 4f, paint)
        }

        paint.color = darkTextColor
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        if (isArabic) {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(valStr, MARGIN, y + 10f, paint)
        } else {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(valStr, MARGIN + CONTENT_WIDTH, y + 10f, paint)
        }
    }

    private fun setupColumnPositions(
        colWidths: FloatArray,
        colStarts: FloatArray,
        colEnds: FloatArray,
        isArabic: Boolean
    ) {
        if (isArabic) {
            var curRight = MARGIN + CONTENT_WIDTH
            for (i in colWidths.indices) {
                val w = colWidths[i]
                colEnds[i] = curRight
                colStarts[i] = curRight - w
                curRight -= w
            }
        } else {
            var curLeft = MARGIN
            for (i in colWidths.indices) {
                val w = colWidths[i]
                colStarts[i] = curLeft
                colEnds[i] = curLeft + w
                curLeft += w
            }
        }
    }
}
