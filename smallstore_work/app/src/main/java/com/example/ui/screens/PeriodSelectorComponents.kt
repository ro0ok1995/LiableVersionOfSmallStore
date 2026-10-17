package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PeriodFilter
import com.example.model.StoreStrings
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// -------------------------------------------------------------
// PERIOD SELECTOR WITH LOCK/UNLOCK TOGGLE & CUSTOM RANGE
// -------------------------------------------------------------
@Composable
internal fun PeriodSelectorLockableRow(
    selectedPeriod: PeriodFilter,
    customStartDate: LocalDate?,
    customEndDate: LocalDate?,
    isLocked: Boolean,
    isArabic: Boolean,
    onSelectPeriod: (PeriodFilter) -> Unit,
    onOpenDatePicker: () -> Unit,
    onToggleLock: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("period_selector_container")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Period Chips: All -> Today -> Month -> Custom
                PeriodSelectorChip(
                    label = if (isArabic) StoreStrings.PERIOD_ALL_AR else StoreStrings.PERIOD_ALL_EN,
                    isSelected = selectedPeriod == PeriodFilter.ALL,
                    testTag = "period_chip_all",
                    onClick = { onSelectPeriod(PeriodFilter.ALL) },
                    modifier = Modifier.weight(1f)
                )
                PeriodSelectorChip(
                    label = if (isArabic) StoreStrings.PERIOD_TODAY_AR else StoreStrings.PERIOD_TODAY_EN,
                    isSelected = selectedPeriod == PeriodFilter.TODAY,
                    testTag = "period_chip_today",
                    onClick = { onSelectPeriod(PeriodFilter.TODAY) },
                    modifier = Modifier.weight(1f)
                )
                PeriodSelectorChip(
                    label = if (isArabic) StoreStrings.PERIOD_MONTH_AR else StoreStrings.PERIOD_MONTH_EN,
                    isSelected = selectedPeriod == PeriodFilter.MONTH,
                    testTag = "period_chip_month",
                    onClick = { onSelectPeriod(PeriodFilter.MONTH) },
                    modifier = Modifier.weight(1f)
                )
                PeriodSelectorChip(
                    label = if (isArabic) StoreStrings.PERIOD_CUSTOM_AR else StoreStrings.PERIOD_CUSTOM_EN,
                    isSelected = selectedPeriod == PeriodFilter.CUSTOM,
                    testTag = "period_chip_custom",
                    onClick = { onSelectPeriod(PeriodFilter.CUSTOM) },
                    modifier = Modifier.weight(1f)
                )

                // Lock / Unlock Toggle Button
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isLocked) GeoPrimary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("period_lock_toggle")
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isLocked) {
                            if (isArabic) "الفترة مقفلة وموحدة عبر التبويبات" else "Locked: Period is shared across tabs"
                        } else {
                            if (isArabic) "الفترة غير مقفلة ومستقلة لكل تبويب" else "Unlocked: Period is independent per tab"
                        },
                        tint = if (isLocked) GeoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // If CUSTOM is selected, show the date range badge with edit button
            if (selectedPeriod == PeriodFilter.CUSTOM) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GeoOutlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenDatePicker() }
                        .testTag("custom_date_range_display")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = GeoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val rangeText = if (customStartDate != null && customEndDate != null) {
                                "$customStartDate  ←  $customEndDate"
                            } else if (customStartDate != null) {
                                if (isArabic) "من $customStartDate" else "From $customStartDate"
                            } else if (customEndDate != null) {
                                if (isArabic) "إلى $customEndDate" else "To $customEndDate"
                            } else {
                                if (isArabic) "اضغط لتحديد نطاق التاريخ" else "Tap to select date range"
                            }
                            Text(
                                text = rangeText,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("custom_date_range_text")
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = if (isArabic) "تعديل التاريخ" else "Edit Date Range",
                            tint = GeoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) GeoPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) null else BorderStroke(1.dp, GeoOutlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDatePickerModal(
    initialDate: LocalDate?,
    title: String,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMillis = remember(initialDate) {
        (initialDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateSelected(selected)
                    }
                    onDismiss()
                }
            ) {
                Text(text = "تأكيد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "إلغاء")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
fun CustomDateRangePickerDialog(
    initialStartDate: LocalDate?,
    initialEndDate: LocalDate?,
    isArabic: Boolean,
    onConfirm: (LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember { LocalDate.now() }
    var startDate by remember(initialStartDate) { mutableStateOf(initialStartDate ?: today.minusDays(30)) }
    var endDate by remember(initialEndDate) { mutableStateOf(initialEndDate ?: today) }

    var isSelectingStartDate by remember { mutableStateOf(false) }
    var isSelectingEndDate by remember { mutableStateOf(false) }

    val isDateOrderInvalid = startDate.isAfter(endDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("custom_date_range_picker_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = GeoPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isArabic) "تحديد نطاق التاريخ المخصص" else "Custom Date Range",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isArabic) "حدد تاريخ البداية والنهاية لتصفية المعاملات بدقة."
                    else "Choose start and end dates to filter transactions precisely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Date Selection Cards (From & To)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Start Date Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GeoOutlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isSelectingStartDate = true }
                            .testTag("custom_start_date_card")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isArabic) "من تاريخ" else "From Date",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = GeoPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = startDate.toString(),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.testTag("custom_start_date_text")
                                )
                            }
                        }
                    }

                    // End Date Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GeoOutlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isSelectingEndDate = true }
                            .testTag("custom_end_date_card")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isArabic) "إلى تاريخ" else "To Date",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = GeoPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = endDate.toString(),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.testTag("custom_end_date_text")
                                )
                            }
                        }
                    }
                }

                // Error indicator if start > end
                if (isDateOrderInvalid) {
                    Text(
                        text = if (isArabic) "تاريخ البداية لا يمكن أن يكون بعد تاريخ النهاية"
                        else "Start date cannot be after end date",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("custom_date_order_error")
                    )
                }

                // Quick presets
                Text(
                    text = if (isArabic) "اختصارات سريعة" else "Quick Presets",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetChip(
                        label = if (isArabic) "٧ أيام" else "7 Days",
                        isSelected = startDate == today.minusDays(6) && endDate == today,
                        onClick = {
                            startDate = today.minusDays(6)
                            endDate = today
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PresetChip(
                        label = if (isArabic) "١٤ يوم" else "14 Days",
                        isSelected = startDate == today.minusDays(13) && endDate == today,
                        onClick = {
                            startDate = today.minusDays(13)
                            endDate = today
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PresetChip(
                        label = if (isArabic) "٣٠ يوم" else "30 Days",
                        isSelected = startDate == today.minusDays(29) && endDate == today,
                        onClick = {
                            startDate = today.minusDays(29)
                            endDate = today
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PresetChip(
                        label = if (isArabic) "هذا الشهر" else "This Month",
                        isSelected = startDate == today.withDayOfMonth(1) && endDate == today,
                        onClick = {
                            startDate = today.withDayOfMonth(1)
                            endDate = today
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(startDate, endDate)
                },
                enabled = !isDateOrderInvalid,
                colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("custom_date_range_confirm_button")
            ) {
                Text(if (isArabic) "تطبيق" else "Apply")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("custom_date_range_dismiss_button")
            ) {
                Text(if (isArabic) "إلغاء" else "Cancel")
            }
        }
    )

    if (isSelectingStartDate) {
        SingleDatePickerModal(
            initialDate = startDate,
            title = if (isArabic) "اختر تاريخ البداية" else "Select Start Date",
            onDateSelected = { picked -> startDate = picked },
            onDismiss = { isSelectingStartDate = false }
        )
    }

    if (isSelectingEndDate) {
        SingleDatePickerModal(
            initialDate = endDate,
            title = if (isArabic) "اختر تاريخ النهاية" else "Select End Date",
            onDateSelected = { picked -> endDate = picked },
            onDismiss = { isSelectingEndDate = false }
        )
    }
}

@Composable
private fun PeriodSelectorChip(
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) GeoPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) null else BorderStroke(1.dp, GeoOutlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
