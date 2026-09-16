package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GeoOutline

/**
 * Geometric Balance Global Top Bar:
 * White background, h-14 (56dp), border-b border-neutral-200 (#E5E7EB),
 * 48dp rounded action icons, and notification bell with red badge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalTopBar(
    title: String,
    unreadNotificationsCount: Int,
    onDrawerClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = title,
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
                onClick = onDrawerClick,
                modifier = Modifier
                    .testTag("top_bar_drawer_button")
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Drawer",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        actions = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .padding(end = 4.dp)
            ) {
                IconButton(
                    onClick = onNotificationsClick,
                    modifier = Modifier
                        .testTag("top_bar_notifications_button")
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                NotificationBadge(
                    unreadCount = unreadNotificationsCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = run {
            val borderColor = MaterialTheme.colorScheme.outlineVariant
            modifier.drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height - strokeWidth / 2),
                    end = Offset(size.width, size.height - strokeWidth / 2),
                    strokeWidth = strokeWidth
                )
            }
        }
    )
}
