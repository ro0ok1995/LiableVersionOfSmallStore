package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LanguageMode
import com.example.model.StoreInfo
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.GeoPrimaryContainer

enum class StoreInfoRestoreChoice {
    KEEP_CURRENT,
    REPLACE_WITH_BACKUP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreInfoConflictSheet(
    isOpen: Boolean,
    currentStoreInfo: StoreInfo,
    backupStoreInfo: StoreInfo,
    languageMode: LanguageMode,
    onConfirm: (replaceWithBackup: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isArabic = languageMode == LanguageMode.ARABIC
    var selectedChoice by remember { mutableStateOf(StoreInfoRestoreChoice.KEEP_CURRENT) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = GeoPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isArabic) "تعارض في بيانات المتجر" else "Store Information Conflict",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = if (isArabic)
                    "معلومات المتجر في هذه النسخة الاحتياطية تختلف عن معلومات متجرك الحالية. يرجى اختيار ما تود فعله:"
                else
                    "Store information in this backup differs from your current store information. Please select an option:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Choice 1: Keep current (default)
            val isKeepSelected = selectedChoice == StoreInfoRestoreChoice.KEEP_CURRENT
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isKeepSelected) 2.dp else 1.dp,
                        color = if (isKeepSelected) GeoPrimary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(if (isKeepSelected) GeoPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
                    .clickable { selectedChoice = StoreInfoRestoreChoice.KEEP_CURRENT }
                    .padding(14.dp)
                    .testTag("choice_keep_current")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isKeepSelected,
                        onClick = { selectedChoice = StoreInfoRestoreChoice.KEEP_CURRENT },
                        colors = RadioButtonDefaults.colors(selectedColor = GeoPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isArabic) "الاحتفاظ بمعلومات المتجر الحالية (الافتراضي)" else "Keep current store information (Default)",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${currentStoreInfo.storeName} - ${currentStoreInfo.phone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Choice 2: Replace with backup
            val isReplaceSelected = selectedChoice == StoreInfoRestoreChoice.REPLACE_WITH_BACKUP
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isReplaceSelected) 2.dp else 1.dp,
                        color = if (isReplaceSelected) GeoPrimary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(if (isReplaceSelected) GeoPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
                    .clickable { selectedChoice = StoreInfoRestoreChoice.REPLACE_WITH_BACKUP }
                    .padding(14.dp)
                    .testTag("choice_replace_backup")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isReplaceSelected,
                        onClick = { selectedChoice = StoreInfoRestoreChoice.REPLACE_WITH_BACKUP },
                        colors = RadioButtonDefaults.colors(selectedColor = GeoPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isArabic) "الاستبدال بمعلومات المتجر من النسخة الاحتياطية" else "Replace with the backup's store information",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${backupStoreInfo.storeName} - ${backupStoreInfo.phone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("cancel_restore_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }

                Button(
                    onClick = {
                        onConfirm(selectedChoice == StoreInfoRestoreChoice.REPLACE_WITH_BACKUP)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("confirm_restore_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary)
                ) {
                    Text(
                        text = if (isArabic) "تأكيد الاستعادة" else "Confirm Restore",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
