package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.model.AppCurrency
import com.example.model.OperationStatus
import com.example.model.SaleType
import com.example.model.TransactionItem
import com.example.model.typedOperationStatus
import com.example.model.typedSaleType
import com.example.ui.screens.AggregatedProductLine
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated export coordinator for Sales & Items reports (PDF, CSV, TXT).
 */
object SalesReportExporter {

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
    ): File = ReportFileHelper.createCachedPdf(context, fileName) { fos ->
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

                val typeLabel = ReportPresentationUtils.getInvoiceTypeLabel(inv, isArabic)
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

        ReportFileHelper.writePdfToStream(pdfDocument, outputStream)
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
        return ReportFileHelper.createCachedCsv(context, fileName, csv)
    }

    fun generateSalesAndItemsCsv(
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        itemBreakdowns: List<AggregatedProductLine>,
        invoices: List<TransactionItem>,
        isArabic: Boolean = true
    ): String {
        val totalItemsQuantity = itemBreakdowns.sumOf { it.totalQuantity }
        val totalItemsSales = itemBreakdowns.sumOf { it.totalSales }
        val totalItemsProfit = itemBreakdowns.sumOf { it.profitMargin }

        val activeInvoices = invoices.filter { (it.operationStatus ?: it.typedOperationStatus) != OperationStatus.REVERSED }
        val totalInvoicesAmount = activeInvoices.sumOf { it.amount }

        val sb = StringBuilder()
        sb.append("\uFEFF") // UTF-8 BOM
        val currentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())

        // 1. Report Info
        val storeLabel = if (isArabic) "المتجر" else "Store"
        val storeVal = if (isArabic) "سمول ستور | $storeName" else "SmallStore | $storeName"
        val titleLabel = if (isArabic) "عنوان التقرير" else "Report Title"
        val periodLabel = if (isArabic) "الفترة" else "Period"
        val dateLabel = if (isArabic) "تاريخ الإصدار" else "Generated Date"

        sb.append(ReportFileHelper.csvRow(storeLabel, storeVal))
        sb.append(ReportFileHelper.csvRow(titleLabel, title))
        sb.append(ReportFileHelper.csvRow(periodLabel, subtitle))
        sb.append(ReportFileHelper.csvRow(dateLabel, currentDate))
        sb.append("\n")

        // 2. KPIs Summary
        val summaryTitle = if (isArabic) "ملخص التقرير" else "Report Summary"
        sb.append(ReportFileHelper.csvRow(summaryTitle))
        if (kpis.isNotEmpty()) {
            for ((k, v) in kpis) {
                sb.append(ReportFileHelper.csvRow(k, v))
            }
        }
        sb.append("\n")

        // 3. Item Details Breakdown Table
        val itemsSecTitle = if (isArabic) {
            "جدول تفاصيل مبيعات الأصناف (${itemBreakdowns.size} صنف)"
        } else {
            "Item Sales Breakdown (${itemBreakdowns.size} Items)"
        }
        sb.append(ReportFileHelper.csvRow(itemsSecTitle))
        val itemHeaders = if (isArabic) {
            listOf("الصنف / البيان", "الكمية المباعة", "إجمالي المبيعات", "هامش الربح")
        } else {
            listOf("Item / Description", "Qty Sold", "Total Sales", "Profit Margin")
        }
        sb.append(ReportFileHelper.csvRow(itemHeaders))
        for (item in itemBreakdowns) {
            val name = item.productName.ifBlank { if (isArabic) "صنف عام" else "General Item" }
            sb.append(ReportFileHelper.csvRow(
                name,
                item.totalQuantity.toString(),
                AppCurrency.formatAmountWithDecimals(item.totalSales, isArabic),
                AppCurrency.formatAmountWithDecimals(item.profitMargin, isArabic)
            ))
        }
        sb.append(ReportFileHelper.csvRow(
            if (isArabic) "إجمالي الأصناف" else "Items Total",
            totalItemsQuantity.toString(),
            AppCurrency.formatAmountWithDecimals(totalItemsSales, isArabic),
            AppCurrency.formatAmountWithDecimals(totalItemsProfit, isArabic)
        ))
        sb.append("\n")

        // 4. Sales Invoices Log Table
        sb.append(ReportFileHelper.csvRow(if (isArabic) "سجل فواتير المبيعات" else "Sales Invoices Log"))
        val invHeaders = if (isArabic) {
            listOf("التاريخ / الفاتورة", "العميل", "طريقة الدفع", "المبلغ")
        } else {
            listOf("Date / Ref", "Customer", "Payment Type", "Amount")
        }
        sb.append(ReportFileHelper.csvRow(invHeaders))
        for (inv in invoices) {
            val dateRef = inv.date
            val cust = inv.customerNameSnapshot.ifBlank { if (isArabic) "عميل عام" else "General" }
            val paymentType = ReportPresentationUtils.getInvoiceTypeLabel(inv, isArabic)
            val amountStr = AppCurrency.formatAmountWithDecimals(inv.amount, isArabic)
            sb.append(ReportFileHelper.csvRow(dateRef, cust, paymentType, amountStr))
        }
        sb.append(ReportFileHelper.csvRow(
            if (isArabic) "إجمالي الفواتير" else "Invoices Total",
            "${invoices.size} ${if (isArabic) "فاتورة" else "Invoices"}",
            "-",
            AppCurrency.formatAmountWithDecimals(totalInvoicesAmount, isArabic)
        ))

        return sb.toString()
    }

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
        return ReportFileHelper.createCachedTxt(context, fileName, txt)
    }

    fun generateSalesAndItemsTxt(
        title: String,
        storeName: String,
        subtitle: String,
        kpis: List<Pair<String, String>>,
        itemBreakdowns: List<AggregatedProductLine>,
        invoices: List<TransactionItem>,
        isArabic: Boolean = true
    ): String {
        val totalItemsQuantity = itemBreakdowns.sumOf { it.totalQuantity }
        val totalItemsSales = itemBreakdowns.sumOf { it.totalSales }
        val totalItemsProfit = itemBreakdowns.sumOf { it.profitMargin }

        val activeInvoices = invoices.filter { (it.operationStatus ?: it.typedOperationStatus) != OperationStatus.REVERSED }
        val totalInvoicesAmount = activeInvoices.sumOf { it.amount }

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

        // 2. Sales Summary
        sb.appendLine(sepDouble)
        sb.appendLine(if (isArabic) "ملخص المبيعات" else "SALES SUMMARY")
        sb.appendLine(sepDouble)
        if (kpis.isNotEmpty()) {
            for ((k, v) in kpis) {
                sb.appendLine("$k: $v")
            }
        }
        sb.appendLine()

        // 3. Items Section
        sb.appendLine(sepDouble)
        sb.appendLine(if (isArabic) "جدول تفاصيل مبيعات الأصناف" else "ITEM SALES DETAILS BREAKDOWN")
        sb.appendLine(sepDouble)
        if (itemBreakdowns.isEmpty()) {
            sb.appendLine(if (isArabic) "لا توجد أصناف مباعة في هذه الفترة" else "No items sold in this period")
        } else {
            itemBreakdowns.forEachIndexed { idx, item ->
                val name = item.productName.ifBlank { if (isArabic) "صنف عام" else "General Item" }
                sb.appendLine("#${idx + 1} | $name")
                sb.appendLine("   ${if (isArabic) "الكمية المباعة" else "Quantity Sold"}: ${item.totalQuantity}")
                sb.appendLine("   ${if (isArabic) "إجمالي المبيعات" else "Total Sales"}: ${AppCurrency.formatAmountWithDecimals(item.totalSales, isArabic)}")
                sb.appendLine("   ${if (isArabic) "هامش الربح" else "Profit Margin"}: ${AppCurrency.formatAmountWithDecimals(item.profitMargin, isArabic)}")
                sb.appendLine(sepSingle)
            }
            sb.appendLine("${if (isArabic) "إجمالي الأصناف" else "Total Items"}: ${itemBreakdowns.size}")
            sb.appendLine("${if (isArabic) "إجمالي مبيعات الأصناف" else "Total Items Sales"}: ${AppCurrency.formatAmountWithDecimals(totalItemsSales, isArabic)}")
        }
        sb.appendLine()

        // 4. Invoices Section
        sb.appendLine(sepDouble)
        sb.appendLine(if (isArabic) "سجل فواتير المبيعات" else "SALES INVOICES LOG")
        sb.appendLine(sepDouble)
        if (invoices.isEmpty()) {
            sb.appendLine(if (isArabic) "لا توجد فواتير مبيعات في هذه الفترة" else "No invoices in this period")
        } else {
            invoices.forEachIndexed { idx, inv ->
                val cust = inv.customerNameSnapshot.ifBlank { if (isArabic) "عميل عام" else "General" }
                val paymentType = ReportPresentationUtils.getInvoiceTypeLabel(inv, isArabic)
                sb.appendLine("#${idx + 1} | ${inv.date} | $cust")
                sb.appendLine("   ${if (isArabic) "طريقة الدفع" else "Payment Type"}: $paymentType")
                sb.appendLine("   ${if (isArabic) "المبلغ" else "Amount"}: ${AppCurrency.formatAmountWithDecimals(inv.amount, isArabic)}")
                sb.appendLine(sepSingle)
            }
            sb.appendLine("${if (isArabic) "إجمالي الفواتير" else "Total Invoices"}: ${invoices.size} ${if (isArabic) "فاتورة" else "Invoices"}")
            sb.appendLine("${if (isArabic) "إجمالي مبالغ الفواتير" else "Total Invoices Amount"}: ${AppCurrency.formatAmountWithDecimals(totalInvoicesAmount, isArabic)}")
        }
        sb.appendLine(sepDouble)

        return sb.toString()
    }
}
