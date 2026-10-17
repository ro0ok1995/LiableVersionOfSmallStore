package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.accounting.FinancialReportCalculator
import com.example.model.AppCurrency
import com.example.model.CustomerAccount
import com.example.model.OperationStatus
import com.example.model.SettlementType
import com.example.model.StoreStrings
import com.example.model.TransactionItem
import com.example.model.typedOperationStatus
import com.example.ui.screens.AggregatedProductLine
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated export coordinator for Comprehensive Customer reports (PDF, CSV, TXT).
 * Guaranteed to represent ONLY ONE specific selected customer.
 */
object CustomerReportExporter {

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
    ): File = ReportFileHelper.createCachedPdf(context, fileName) { fos ->
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
        val customerLedgerBalance = customer.balance
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

                val balanceStatusText = if (customerLedgerBalance > 0.001) {
                    if (isArabic) "الرصيد المستحق: ${AppCurrency.formatAmountWithDecimals(customerLedgerBalance, isArabic)}"
                    else "Outstanding Balance: ${AppCurrency.formatAmountWithDecimals(customerLedgerBalance, isArabic)}"
                } else {
                    if (isArabic) "الحساب مسدد بالكامل (0.00 ₪)"
                    else "Fully Settled Account (0.00 ₪)"
                }
                paint.color = if (customerLedgerBalance > 0.001) redColor else greenColor
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
                    (if (isArabic) "الرصيد المستحق" else "Balance Due") to AppCurrency.formatAmountWithDecimals(customerLedgerBalance, isArabic),
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

                    paint.color = if (k == 0 && customerLedgerBalance > 0.001) redColor else primaryColor
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
                val isPayment = ReportPresentationUtils.isPaymentTransaction(tx)
                val isDebt = ReportPresentationUtils.isDebtTransaction(tx)
                val typeLabel = ReportPresentationUtils.getTransactionTypeLabel(tx, isArabic, shortLabel = true)
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
                "الرصيد المستحق: ${AppCurrency.formatAmountWithDecimals(customerLedgerBalance, isArabic)}   |   مشتريات كاش: ${AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)}   |   مشتريات آجل: ${AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)}   |   المسدد: ${AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)}"
            } else {
                "Balance Due: ${AppCurrency.formatAmountWithDecimals(customerLedgerBalance, isArabic)}   |   Cash: ${AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)}   |   Debt: ${AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)}   |   Payments: ${AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)}"
            }
            paint.textAlign = Paint.Align.CENTER
            activePage.canvas.drawText(balanceSummaryText, pageWidth / 2f, activePage.currentY + 18f, paint)
            activePage.currentY += breakdownH
        }

        // Finish active page
        drawFooter(activePage.canvas, activePage.pageNum)
        pdfDocument.finishPage(activePage.page)

        ReportFileHelper.writePdfToStream(pdfDocument, outputStream)
    }

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
        return ReportFileHelper.createCachedCsv(context, fileName, csv)
    }

    /**
     * Specialized CSV generator for the Comprehensive Customer Report (ONE SELECTED CUSTOMER).
     * Order of Sections: 1. Report Info, 2. Selected Customer, 3. Customer Summary,
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
        val customerLedgerBalance = customer.balance
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

        sb.append(ReportFileHelper.csvRow(storeLabel, storeVal))
        sb.append(ReportFileHelper.csvRow(titleLabel, reportTitle))
        sb.append(ReportFileHelper.csvRow(periodLabel, subtitle))
        sb.append(ReportFileHelper.csvRow(dateLabel, currentDate))
        sb.append("\n")

        // SECTION 2: Selected Customer
        val custSecTitle = if (isArabic) "بيانات العميل المحدد" else "Selected Customer Details"
        sb.append(ReportFileHelper.csvRow(custSecTitle))
        sb.append(ReportFileHelper.csvRow(if (isArabic) "اسم العميل" else "Customer Name", customer.customerName))
        val phoneVal = customer.phone.ifBlank { if (isArabic) "غير محدد" else "Not Specified" }
        sb.append(ReportFileHelper.csvRow(if (isArabic) "رقم الهاتف" else "Phone Number", phoneVal))
        sb.append(ReportFileHelper.csvRow(if (isArabic) "الرصيد المستحق" else "Outstanding Balance", AppCurrency.formatAmountWithDecimals(customerLedgerBalance, isArabic)))
        val statusVal = if (customerLedgerBalance > 0.001) {
            if (isArabic) "رصيد مستحق" else "Balance Due"
        } else {
            if (isArabic) "الحساب مسدد بالكامل" else "Fully Settled Account"
        }
        sb.append(ReportFileHelper.csvRow(if (isArabic) "حالة الحساب" else "Account Status", statusVal))
        sb.append("\n")

        // SECTION 3: Customer Summary
        val summarySecTitle = if (isArabic) "ملخص حساب العميل" else "Customer Account Summary"
        sb.append(ReportFileHelper.csvRow(summarySecTitle))
        sb.append(ReportFileHelper.csvRow(if (isArabic) "الرصيد المستحق" else "Outstanding Balance", AppCurrency.formatAmountWithDecimals(customerLedgerBalance, isArabic)))
        sb.append(ReportFileHelper.csvRow(if (isArabic) "مشتريات كاش" else "Cash Purchases", AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)))
        sb.append(ReportFileHelper.csvRow(if (isArabic) "مشتريات آجل" else "Credit Purchases", AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)))
        sb.append(ReportFileHelper.csvRow(if (isArabic) "إجمالي المسدد" else "Total Payments", AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)))
        sb.append("\n")

        // SECTION 4: MOST ORDERED PRODUCTS FOR THIS CUSTOMER
        val prodSecTitle = if (isArabic) {
            "أكثر الأصناف طلباً لهذا العميل (${itemBreakdowns.size} صنف)"
        } else {
            "Most Ordered Products for This Customer (${itemBreakdowns.size} Products)"
        }
        sb.append(ReportFileHelper.csvRow(prodSecTitle))
        val prodHeaders = if (isArabic) {
            listOf("الترتيب", "اسم الصنف", "الكمية", "إجمالي المبلغ")
        } else {
            listOf("Rank", "Product Name", "Quantity", "Total Amount")
        }
        sb.append(ReportFileHelper.csvRow(prodHeaders))

        if (itemBreakdowns.isEmpty()) {
            sb.append(ReportFileHelper.csvRow(if (isArabic) "لا توجد تفاصيل أصناف فردية مسجلة لهذا العميل في هذه الفترة" else "No detailed product order records found for this customer in this period", "", "", ""))
        } else {
            itemBreakdowns.forEachIndexed { idx, item ->
                sb.append(ReportFileHelper.csvRow(
                    "#${idx + 1}",
                    item.productName,
                    item.totalQuantity.toString(),
                    AppCurrency.formatAmountWithDecimals(item.totalSales, isArabic)
                ))
            }
            // Product Totals Row
            val prodTotalLbl = if (isArabic) "إجمالي الأصناف المطلوبة" else "Total Ordered Products"
            sb.append(ReportFileHelper.csvRow(
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
        sb.append(ReportFileHelper.csvRow(txSecTitle))
        val txHeaders = if (isArabic) {
            listOf("التاريخ", "نوع المعاملة", "البيان / تفاصيل العملية", "التسوية", "المبلغ")
        } else {
            listOf("Date", "Transaction Type", "Description / Notes", "Settlement", "Amount")
        }
        sb.append(ReportFileHelper.csvRow(txHeaders))

        if (transactions.isEmpty()) {
            sb.append(ReportFileHelper.csvRow(if (isArabic) "لا توجد معاملات مسجلة لهذا العميل في هذه الفترة" else "No transactions recorded for this customer in this period", "", "", "", ""))
        } else {
            for (tx in transactions) {
                val typeLabel = ReportPresentationUtils.getTransactionTypeLabel(tx, isArabic, shortLabel = true)
                val desc = tx.notes.ifBlank { tx.title.ifBlank { tx.activityType } }

                val settlementStr = when (tx.settlementType) {
                    SettlementType.FULL -> if (isArabic) "تسوية كاملة" else "Full Settlement"
                    SettlementType.PARTIAL -> if (isArabic) "تسوية جزئية" else "Partial Settlement"
                    null -> "-"
                }

                sb.append(ReportFileHelper.csvRow(
                    tx.date,
                    typeLabel,
                    desc,
                    settlementStr,
                    AppCurrency.formatAmountWithDecimals(tx.amount, isArabic)
                ))
            }

            // Final Totals Row
            val totalLbl = if (isArabic) "إجمالي العمليات المعروضة (${transactions.size})" else "Total Operations (${transactions.size})"
            sb.append(ReportFileHelper.csvRow(
                totalLbl,
                "",
                "",
                "",
                AppCurrency.formatAmountWithDecimals(activeCustTotalAmount, isArabic)
            ))

            // Balance Summary Row
            sb.append("\n")
            val finalBalanceTitle = if (isArabic) "الرصيد والحساب النهائي للعميل" else "Final Customer Balance & Totals"
            sb.append(ReportFileHelper.csvRow(finalBalanceTitle))
            sb.append(ReportFileHelper.csvRow(if (isArabic) "الرصيد المستحق" else "Balance Due", AppCurrency.formatAmountWithDecimals(customerLedgerBalance, isArabic)))
            sb.append(ReportFileHelper.csvRow(if (isArabic) "مشتريات كاش" else "Cash Purchases", AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)))
            sb.append(ReportFileHelper.csvRow(if (isArabic) "مشتريات آجل" else "Debt Purchases", AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)))
            sb.append(ReportFileHelper.csvRow(if (isArabic) "إجمالي المسدد" else "Total Payments", AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)))
        }

        return sb.toString()
    }

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
        return ReportFileHelper.createCachedTxt(context, fileName, txt)
    }

    /**
     * Specialized TXT generator for the Comprehensive Customer Report (ONE SELECTED CUSTOMER).
     * Order of Sections: 1. Report Info, 2. Selected Customer, 3. Customer Summary,
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
        val customerLedgerBalance = customer.balance
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
        sb.appendLine("${if (isArabic) "الرصيد المستحق" else "Outstanding Balance"}: ${AppCurrency.formatAmountWithDecimals(customerLedgerBalance, isArabic)}")
        val statusVal = if (customerLedgerBalance > 0.001) {
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
        sb.appendLine("${if (isArabic) "الرصيد المستحق" else "Outstanding Balance"}: ${AppCurrency.formatAmountWithDecimals(customerLedgerBalance, isArabic)}")
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
                val typeLabel = ReportPresentationUtils.getTransactionTypeLabel(tx, isArabic, shortLabel = true)
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
            sb.appendLine("${if (isArabic) "الرصيد المستحق" else "Outstanding Balance"}: ${AppCurrency.formatAmountWithDecimals(customerLedgerBalance, isArabic)}")
            sb.appendLine("${if (isArabic) "مشتريات كاش" else "Cash Purchases"}: ${AppCurrency.formatAmountWithDecimals(resolvedCash, isArabic)}")
            sb.appendLine("${if (isArabic) "مشتريات آجل" else "Debt Purchases"}: ${AppCurrency.formatAmountWithDecimals(resolvedDebt, isArabic)}")
            sb.appendLine("${if (isArabic) "إجمالي المسدد" else "Total Payments"}: ${AppCurrency.formatAmountWithDecimals(resolvedPayments, isArabic)}")
        }
        sb.appendLine(sepDouble)

        return sb.toString()
    }
}
