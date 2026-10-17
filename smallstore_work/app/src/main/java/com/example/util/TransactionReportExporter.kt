package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.accounting.FinancialReportCalculator
import com.example.model.AppCurrency
import com.example.model.OperationStatus
import com.example.model.SettlementType
import com.example.model.TransactionItem
import com.example.model.typedOperationStatus
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated export coordinator for Transaction history reports (PDF, CSV, TXT).
 */
object TransactionReportExporter {

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
    ): File = ReportFileHelper.createCachedPdf(context, fileName) { fos ->
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

                drawTableHeader(activePage.canvas, activePage.currentY)
                activePage.currentY += 22f
            }
        }

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

        drawTableHeader(activePage.canvas, activePage.currentY)
        activePage.currentY += 22f

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

                val dateStr = tx.date
                drawCellText(activePage.canvas, dateStr, 0, activePage.currentY + 15f, paint, alignEnd = false)

                val customerName = tx.customerNameSnapshot.ifBlank { if (isArabic) "عميل عام" else "General" }
                val customerDisplay = fitText(customerName, colWidths[1] - 12f, paint)
                drawCellText(activePage.canvas, customerDisplay, 1, activePage.currentY + 15f, paint, alignEnd = false)

                val isPayment = ReportPresentationUtils.isPaymentTransaction(tx)
                val isDebt = ReportPresentationUtils.isDebtTransaction(tx)
                val typeLabel = ReportPresentationUtils.getTransactionTypeLabel(tx, isArabic)
                paint.color = if (isPayment) blueColor else if (isDebt) amberColor else greenColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                drawCellText(activePage.canvas, typeLabel, 2, activePage.currentY + 15f, paint, alignEnd = false)

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

                paint.color = primaryColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                drawCellText(activePage.canvas, AppCurrency.formatAmountWithDecimals(tx.amount, isArabic), 4, activePage.currentY + 15f, paint, alignEnd = true)

                activePage.currentY += rowHeight
            }

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

        drawFooter(activePage.canvas, activePage.pageNum)
        pdfDocument.finishPage(activePage.page)

        ReportFileHelper.writePdfToStream(pdfDocument, outputStream)
    }

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
        return ReportFileHelper.createCachedCsv(context, fileName, csv)
    }

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

        val summarySecTitle = if (isArabic) "ملخص المعاملات" else "Transactions Summary"
        sb.append(ReportFileHelper.csvRow(summarySecTitle))
        if (kpis.isNotEmpty()) {
            for ((k, v) in kpis) {
                sb.append(ReportFileHelper.csvRow(k, v))
            }
        }
        sb.append("\n")

        val detailsSecTitle = if (isArabic) {
            "تفاصيل المعاملات (${transactions.size} معاملة)"
        } else {
            "Transaction Details (${transactions.size} Transactions)"
        }
        sb.append(ReportFileHelper.csvRow(detailsSecTitle))

        val headers = if (isArabic) {
            listOf("التاريخ", "العميل", "نوع المعاملة", "البيان / التسوية", "المبلغ")
        } else {
            listOf("Date", "Customer", "Transaction Type", "Notes / Settlement", "Amount")
        }
        sb.append(ReportFileHelper.csvRow(headers))

        if (transactions.isEmpty()) {
            val emptyMsg = if (isArabic) "لا توجد معاملات متاحة في هذه الفترة" else "No transactions available for this period"
            sb.append(ReportFileHelper.csvRow(emptyMsg, "", "", "", ""))
        } else {
            for (tx in transactions) {
                val customerName = tx.customerNameSnapshot.ifBlank { if (isArabic) "عميل عام" else "General" }
                val typeLabel = ReportPresentationUtils.getTransactionTypeLabel(tx, isArabic)
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

                sb.append(ReportFileHelper.csvRow(
                    tx.date,
                    customerName,
                    typeLabel,
                    noteText,
                    AppCurrency.formatAmountWithDecimals(tx.amount, isArabic)
                ))
            }

            val totalLbl = if (isArabic) "إجمالي المعاملات (${transactions.size} معاملة)" else "Transactions Total (${transactions.size} Transactions)"
            sb.append(ReportFileHelper.csvRow(
                totalLbl,
                "",
                "",
                "",
                AppCurrency.formatAmountWithDecimals(activeTransactionsTotal, isArabic)
            ))

            sb.append("\n")
            val breakdownTitle = if (isArabic) "ملخص الإجماليات النهائي" else "Final Totals Breakdown"
            sb.append(ReportFileHelper.csvRow(breakdownTitle))
            sb.append(ReportFileHelper.csvRow(if (isArabic) "إجمالي الكاش" else "Cash Total", AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)))
            sb.append(ReportFileHelper.csvRow(if (isArabic) "إجمالي الآجل" else "Debt Total", AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)))
            sb.append(ReportFileHelper.csvRow(if (isArabic) "إجمالي التسديد" else "Payments Total", AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)))
        }

        return sb.toString()
    }

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
        return ReportFileHelper.createCachedTxt(context, fileName, txt)
    }

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

        // 2. Transactions Summary
        sb.appendLine(sepDouble)
        sb.appendLine(if (isArabic) "ملخص المعاملات" else "TRANSACTIONS SUMMARY")
        sb.appendLine(sepDouble)
        if (kpis.isNotEmpty()) {
            for ((k, v) in kpis) {
                sb.appendLine("$k: $v")
            }
        }
        sb.appendLine()

        val detailsSecTitle = if (isArabic) {
            "تفاصيل المعاملات (${transactions.size} معاملة)"
        } else {
            "TRANSACTION DETAILS (${transactions.size} Transactions)"
        }
        sb.appendLine(sepSingle)
        sb.appendLine(detailsSecTitle)
        sb.appendLine(sepSingle)

        if (transactions.isEmpty()) {
            sb.appendLine(if (isArabic) "لا توجد معاملات متاحة في هذه الفترة" else "No transactions available for this period")
        } else {
            transactions.forEachIndexed { idx, tx ->
                val customerName = tx.customerNameSnapshot.ifBlank { if (isArabic) "عميل عام" else "General" }
                val typeLabel = ReportPresentationUtils.getTransactionTypeLabel(tx, isArabic)

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
}
