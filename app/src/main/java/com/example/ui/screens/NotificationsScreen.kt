package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCurrency
import com.example.model.LanguageMode
import com.example.model.NotificationItem
import com.example.model.StoreStrings
import com.example.ui.components.SimpleEmptyState
import com.example.ui.theme.GeoOutline
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusRedBg
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    notifications: List<NotificationItem>,
    languageMode: LanguageMode,
    onBackClick: () -> Unit,
    onNotificationClick: (NotificationItem) -> Unit,
    onViewNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val currency = AppCurrency.SYMBOL

    LaunchedEffect(Unit) {
        onViewNotifications()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("notifications_screen")
    ) {
        TopAppBar(
            title = {
                Text(
                    text = if (isArabic) StoreStrings.NOTIFICATIONS_AR else StoreStrings.NOTIFICATIONS_EN,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .testTag("notifications_back_button")
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isArabic) "رجوع" else "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = GeoOutline,
                    start = Offset(0f, size.height - strokeWidth / 2),
                    end = Offset(size.width, size.height - strokeWidth / 2),
                    strokeWidth = strokeWidth
                )
            }
        )

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                SimpleEmptyState(
                    message = if (isArabic) StoreStrings.NO_NOTIFICATIONS_AR else StoreStrings.NO_NOTIFICATIONS_EN,
                    icon = Icons.Default.NotificationsNone,
                    testTag = "notifications_empty_state"
                )
            }
        } else {
            val displayItems = notifications.take(50)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .testTag("notifications_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                items(items = displayItems, key = { it.id }) { item ->
                    NotificationRowItem(
                        item = item,
                        currency = currency,
                        onClick = { onNotificationClick(item) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun NotificationRowItem(
    item: NotificationItem,
    currency: String,
    onClick: () -> Unit
) {
    val isUnread = !item.isRead
    val isPayment = item.isPayment

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isUnread) GeoPrimary.copy(alpha = 0.35f) else GeoOutlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .testTag("notification_item_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isUnread) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GeoPrimary)
                            .testTag("unread_dot_${item.id}")
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.customerName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (isPayment) StatusGreenBg else StatusRedBg,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.transactionType,
                            fontSize = 10.sp,
                            fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPayment) StatusGreen else StatusRed,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.timestamp,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = String.format(
                    Locale.US,
                    "%s%,.2f %s",
                    if (isPayment) "+" else "-",
                    item.amount,
                    currency
                ),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = if (isPayment) StatusGreen else StatusRed
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
