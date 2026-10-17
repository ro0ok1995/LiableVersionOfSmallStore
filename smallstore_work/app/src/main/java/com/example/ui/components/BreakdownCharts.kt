package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Supported chart types inside the Volume & Debt Breakdown section.
 */
enum class BreakdownChartType {
    DONUT,
    COLUMN,
    COMBO
}

/**
 * Shared item model for the Volume & Debt Breakdown charts.
 * Strictly holds the exact 4 categories and values derived from the donut dataset.
 */
data class BreakdownChartItem(
    val labelAr: String,
    val labelEn: String,
    val amount: Double,
    val percentage: Int,
    val color: Color
)

/**
 * Tab button for selecting chart type inside the Volume & Debt Breakdown section.
 */
@Composable
fun BreakdownChartTabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Column Chart implementation representing the exact four categories and monetary amounts.
 */
@Composable
fun BreakdownColumnChart(
    items: List<BreakdownChartItem>,
    currency: String,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val maxAmount = remember(items) {
        items.maxOfOrNull { it.amount }?.takeIf { it > 0 } ?: 1.0
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("statistics_column_chart")
    ) {
        // Subtle metric note
        Text(
            text = if (isArabic) "مقارنة المبالغ المالية بالعملة ($currency)" else "Monetary Amount Comparison ($currency)",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Chart container with axes & grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            // Background horizontal grid lines
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                val gridColor = Color.LightGray.copy(alpha = 0.35f)
                val stroke = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                )
                // 100% line (top)
                drawLine(gridColor, Offset(0f, 16.dp.toPx()), Offset(size.width, 16.dp.toPx()), strokeWidth = 1.dp.toPx(), pathEffect = stroke.pathEffect)
                // 50% line (mid)
                val midY = (size.height + 16.dp.toPx() - 8.dp.toPx()) / 2f
                drawLine(gridColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.dp.toPx(), pathEffect = stroke.pathEffect)
                // 0% baseline (bottom)
                val baseY = size.height - 4.dp.toPx()
                drawLine(gridColor.copy(alpha = 0.7f), Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.5.dp.toPx())
            }

            // Columns row
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom
            ) {
                items.forEachIndexed { index, item ->
                    val barFraction = if (maxAmount > 0) (item.amount / maxAmount).coerceIn(0.0, 1.0).toFloat() else 0f
                    val availableBarHeight = 110.dp

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp)
                            .testTag("column_bar_$index")
                    ) {
                        // Value label above bar
                        Text(
                            text = formatAmountDisplay(item.amount),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Column bar
                        val barHeight = (availableBarHeight * barFraction).coerceAtLeast(if (item.amount > 0) 6.dp else 2.dp)
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(item.color)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category labels & percentages row under the columns
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            items.forEach { item ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isArabic) item.labelAr else item.labelEn,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        color = item.color.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${item.percentage}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = item.color,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Combo Chart implementation (Google-style dual axis):
 * - Columns = Monetary amount for each category (Primary Axis)
 * - Line = Percentage share within the same total volume (Secondary Axis)
 */
@Composable
fun BreakdownComboChart(
    items: List<BreakdownChartItem>,
    currency: String,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val maxAmount = remember(items) {
        items.maxOfOrNull { it.amount }?.takeIf { it > 0 } ?: 1.0
    }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("statistics_combo_chart")
    ) {
        // Dual-axis Legend Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bars Legend (Monetary amount)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isArabic) "الأعمدة: المبالغ ($currency)" else "Bars: Amount ($currency)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Line Legend (Percentage share)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(2.5.dp)
                        .background(Color(0xFF4F46E5))
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4F46E5))
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isArabic) "الخط: النسبة (%)" else "Line: Share (%)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Chart Area with Y-axis markers on sides
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Primary Axis (Amount) labels on start
            Column(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .padding(bottom = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = if (isRtl) Alignment.Start else Alignment.End
            ) {
                Text(
                    text = formatCurrencyShort(maxAmount),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = formatCurrencyShort(maxAmount / 2),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = "0",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Center chart area (Bars + Connecting Line Canvas overlay)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Background grid lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridColor = Color.LightGray.copy(alpha = 0.35f)
                    val stroke = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                    // Top grid line
                    drawLine(gridColor, Offset(0f, 10.dp.toPx()), Offset(size.width, 10.dp.toPx()), strokeWidth = 1.dp.toPx(), pathEffect = stroke.pathEffect)
                    // Mid grid line
                    val midY = size.height / 2f
                    drawLine(gridColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.dp.toPx(), pathEffect = stroke.pathEffect)
                    // Baseline
                    val baseY = size.height - 4.dp.toPx()
                    drawLine(gridColor.copy(alpha = 0.7f), Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.5.dp.toPx())
                }

                // Bars Row
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.Bottom
                ) {
                    items.forEachIndexed { index, item ->
                        val barFraction = if (maxAmount > 0) (item.amount / maxAmount).coerceIn(0.0, 1.0).toFloat() else 0f
                        val availableBarHeight = 110.dp

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("combo_bar_$index")
                        ) {
                            val barHeight = (availableBarHeight * barFraction).coerceAtLeast(if (item.amount > 0) 6.dp else 2.dp)
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(item.color.copy(alpha = 0.85f))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                // Overlay Canvas: Line + Markers connecting the percentage share of each category
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val count = items.size
                    if (count > 0) {
                        val topY = 16.dp.toPx()
                        val bottomY = size.height - 12.dp.toPx()
                        val availableH = (bottomY - topY).coerceAtLeast(1f)

                        val points = mutableListOf<Offset>()
                        for (i in 0 until count) {
                            val pct = items[i].percentage.coerceIn(0, 100)
                            // X position centered on column i
                            val colFraction = if (isRtl) (count - 1 - i + 0.5f) / count.toFloat() else (i + 0.5f) / count.toFloat()
                            val px = size.width * colFraction
                            // Y position based on percentage (0% at bottomY, 100% at topY)
                            val py = bottomY - (pct / 100f * availableH)
                            points.add(Offset(px, py))
                        }

                        // Draw path connecting all points
                        val lineColor = Color(0xFF4F46E5)
                        val linePath = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }

                        // Draw connecting line
                        drawPath(
                            path = linePath,
                            color = lineColor,
                            style = Stroke(width = 2.5.dp.toPx())
                        )

                        // Draw markers at each node
                        for (pt in points) {
                            // Outer circle
                            drawCircle(
                                color = lineColor,
                                radius = 5.dp.toPx(),
                                center = pt
                            )
                            // Inner white circle
                            drawCircle(
                                color = Color.White,
                                radius = 2.5.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }

                // Percentage tags row over the line points
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    items.forEach { item ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Surface(
                                color = Color(0xFF4F46E5).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(3.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = "${item.percentage}%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 9.sp
                                    ),
                                    color = Color(0xFF4F46E5),
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Secondary Axis (Percentage) labels on end
            Column(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight()
                    .padding(bottom = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = if (isRtl) Alignment.End else Alignment.Start
            ) {
                Text(
                    text = "100%",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color(0xFF4F46E5).copy(alpha = 0.8f)
                )
                Text(
                    text = "50%",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color(0xFF4F46E5).copy(alpha = 0.8f)
                )
                Text(
                    text = "0%",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color(0xFF4F46E5).copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category labels row at the bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            items.forEach { item ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isArabic) item.labelAr else item.labelEn,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = formatAmountDisplay(item.amount),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatCurrencyShort(value: Double): String {
    return when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        value == value.toLong().toDouble() -> String.format(Locale.US, "%,d", value.toLong())
        else -> String.format(Locale.US, "%,.1f", value)
    }
}

private fun formatAmountDisplay(value: Double): String {
    return if (value >= 100_000) {
        formatCurrencyShort(value)
    } else {
        String.format(Locale.US, "%,.0f", value)
    }
}
