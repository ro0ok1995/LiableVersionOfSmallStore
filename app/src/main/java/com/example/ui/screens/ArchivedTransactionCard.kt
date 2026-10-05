package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCurrency
import com.example.model.StoreStrings
import com.example.model.TransactionItem
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusAmberBg
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg

@Composable
fun ArchivedTransactionCard(
    transaction: TransactionItem,
    isArabic: Boolean,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, GeoOutlineVariant, RoundedCornerShape(14.dp))
            .testTag("archived_transaction_card_${transaction.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Transaction Title & ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${if (isArabic) "العميل: " else "Customer: "}${transaction.customerNameSnapshot}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Amount Highlight
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = AppCurrency.formatAmountWithDecimals(transaction.amount, isArabic),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isDebt) MaterialTheme.colorScheme.error else StatusGreen
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "#${transaction.id.takeLast(6)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Transaction Info Surface
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type Tag
                    Surface(
                        color = if (isDebt) StatusAmberBg else StatusGreenBg,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.border(
                            1.dp,
                            (if (isDebt) StatusAmber else StatusGreen).copy(alpha = 0.3f),
                            RoundedCornerShape(6.dp)
                        )
                    ) {
                        Text(
                            text = transaction.activityType,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDebt) StatusAmber else StatusGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    // Archived Date
                    Text(
                        text = "${if (isArabic) "تاريخ المعاملة: " else "Date: "}${transaction.date}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Badge and relative time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = StatusAmberBg,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.border(1.dp, StatusAmber.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(StatusAmber, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isArabic) "معاملة ملغاة / مؤرشفة" else "Cancelled / Archived",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusAmber
                        )
                    }
                }

                Text(
                    text = transaction.relativeTime.ifBlank { transaction.date },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = GeoOutlineVariant, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Restore only (Financial transactions cannot be permanently deleted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Restore Button
                OutlinedButton(
                    onClick = onRestoreClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = GeoPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("restore_transaction_${transaction.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isArabic) StoreStrings.RESTORE_ACTION_AR else StoreStrings.RESTORE_ACTION_EN,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
