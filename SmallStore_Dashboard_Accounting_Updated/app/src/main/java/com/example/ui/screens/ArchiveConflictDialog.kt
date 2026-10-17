package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ArchiveConflict
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusAmberBg

@Composable
fun ArchiveConflictDialog(
    conflict: ArchiveConflict,
    isArabic: Boolean,
    onResolveAsSeparate: () -> Unit,
    onKeepArchived: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                color = StatusAmberBg,
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = StatusAmber,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = if (isArabic) "تنبيه تعارض في السجلات النشطة" else "Active Record Conflict Detected",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Conflict description banner
                Surface(
                    color = StatusAmberBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StatusAmber.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = if (isArabic) conflict.descriptionAr else conflict.descriptionEn,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusAmber,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Text(
                    text = if (isArabic) {
                        "لحماية سلامة بياناتك، لا يتم الكتابة فوق السجل النشط أو دمجه تلقائياً. اختر أحد الخيارات الآمنة:"
                    } else {
                        "To protect data integrity, active records are never overwritten or merged automatically. Choose a safe option:"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onResolveAsSeparate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GeoPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("conflict_restore_separate_button")
            ) {
                Text(if (isArabic) "استعادة كسجل منفصل" else "Restore as separate record")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onKeepArchived,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("conflict_keep_archived_button")
                ) {
                    Text(if (isArabic) "إبقاء في الأرشيف" else "Keep archived")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("conflict_cancel_button")
                ) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        },
        shape = RoundedCornerShape(18.dp)
    )
}
