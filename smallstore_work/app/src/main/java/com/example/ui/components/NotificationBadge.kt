package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Geometric Balance Notification Badge:
 * Implements the global NOTIFICATION BADGE RULE:
 * - 0 unread: no badge shown at all.
 * - 1–9 unread: show the exact number.
 * - 10+ unread: show "+9" (not the real count).
 * Styled with 2dp surface border cutout and theme error background.
 */
@Composable
fun NotificationBadge(
    unreadCount: Int,
    modifier: Modifier = Modifier
) {
    if (unreadCount <= 0) return

    val displayText = when {
        unreadCount in 1..9 -> unreadCount.toString()
        else -> "+9"
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .border(width = 2.dp, color = MaterialTheme.colorScheme.surface, shape = CircleShape)
            .background(
                color = MaterialTheme.colorScheme.error,
                shape = CircleShape
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = MaterialTheme.colorScheme.onError,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp
        )
    }
}
