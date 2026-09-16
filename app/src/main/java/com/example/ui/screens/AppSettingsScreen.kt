package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppThemeMode
import com.example.model.LanguageMode
import com.example.model.StoreStrings
import com.example.model.ThemeDisplayMode
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    languageMode: LanguageMode,
    themeMode: AppThemeMode,
    displayMode: ThemeDisplayMode,
    notificationsEnabled: Boolean = true,
    onBackClick: () -> Unit,
    onLanguageChange: (LanguageMode) -> Unit,
    onThemeChange: (AppThemeMode) -> Unit,
    onDisplayModeChange: (ThemeDisplayMode) -> Unit,
    onNotificationsChange: (Boolean) -> Unit = {},
    onExportBackup: ((android.net.Uri) -> Unit)? = null,
    onImportBackup: ((android.net.Uri) -> Unit)? = null,
    onResetData: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    var showConfirmReset by remember { mutableStateOf(false) }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: android.net.Uri? ->
        uri?.let { onExportBackup?.invoke(it) }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        uri?.let { onImportBackup?.invoke(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("app_settings_screen")
    ) {
        TopAppBar(
            title = {
                Text(
                    text = if (isArabic) StoreStrings.APP_SETTINGS_AR else StoreStrings.APP_SETTINGS_EN,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick, modifier = Modifier.testTag("settings_back_button")) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Language Selection
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GeoOutlineVariant, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Translate, contentDescription = null, tint = GeoPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "لغة التطبيق" else "App Language",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = languageMode == LanguageMode.ARABIC,
                            onClick = { onLanguageChange(LanguageMode.ARABIC) },
                            label = { Text("العربية (RTL)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lang_arabic_chip")
                        )
                        FilterChip(
                            selected = languageMode == LanguageMode.ENGLISH,
                            onClick = { onLanguageChange(LanguageMode.ENGLISH) },
                            label = { Text("English (LTR)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lang_english_chip")
                        )
                    }
                }
            }

            // 2. Appearance Mode (Light / Dark / Auto)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GeoOutlineVariant, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isArabic) "وضع المظهر" else "Display Mode",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = displayMode == ThemeDisplayMode.LIGHT,
                            onClick = { onDisplayModeChange(ThemeDisplayMode.LIGHT) },
                            label = { Text(if (isArabic) "فاتح" else "Light") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_light_chip")
                        )
                        FilterChip(
                            selected = displayMode == ThemeDisplayMode.DARK,
                            onClick = { onDisplayModeChange(ThemeDisplayMode.DARK) },
                            label = { Text(if (isArabic) "داكن" else "Dark") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_dark_chip")
                        )
                        FilterChip(
                            selected = displayMode == ThemeDisplayMode.AUTO,
                            onClick = { onDisplayModeChange(ThemeDisplayMode.AUTO) },
                            label = { Text(if (isArabic) "تلقائي" else "System") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_auto_chip")
                        )
                    }
                }
            }

            // 3. Themes (Color Palette)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GeoOutlineVariant, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = GeoPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "سمة الألوان" else "Color Palette",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = themeMode == AppThemeMode.NEUTRAL,
                            onClick = { onThemeChange(AppThemeMode.NEUTRAL) },
                            label = { Text(if (isArabic) "حيادي" else "Neutral") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("palette_neutral_chip")
                        )
                        FilterChip(
                            selected = themeMode == AppThemeMode.PURPLE,
                            onClick = { onThemeChange(AppThemeMode.PURPLE) },
                            label = { Text(if (isArabic) "بنفسجي" else "Purple") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("palette_purple_chip")
                        )
                        FilterChip(
                            selected = themeMode == AppThemeMode.GOLD,
                            onClick = { onThemeChange(AppThemeMode.GOLD) },
                            label = { Text(if (isArabic) "ذهبي" else "Gold") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("palette_gold_chip")
                        )
                    }
                }
            }

            // 4. Notifications ON/OFF
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GeoOutlineVariant, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = GeoPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isArabic) StoreStrings.PREF_ENABLE_NOTIFICATIONS_AR else StoreStrings.PREF_ENABLE_NOTIFICATIONS_EN,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isArabic) StoreStrings.PREF_ENABLE_NOTIFICATIONS_DESC_AR else StoreStrings.PREF_ENABLE_NOTIFICATIONS_DESC_EN,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = onNotificationsChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.surface,
                            checkedTrackColor = GeoPrimary
                        ),
                        modifier = Modifier.testTag("setting_notifications_switch")
                    )
                }
            }

            // 5. Backup & Restore Section
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GeoOutlineVariant, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isArabic) StoreStrings.SECTION_BACKUP_AR else StoreStrings.SECTION_BACKUP_EN,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Create Backup Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = GeoPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isArabic) StoreStrings.CREATE_BACKUP_AR else StoreStrings.CREATE_BACKUP_EN,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isArabic) StoreStrings.CREATE_BACKUP_DESC_AR else StoreStrings.CREATE_BACKUP_DESC_EN,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                createDocLauncher.launch("SmallStore_Backup_$timestamp.json")
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                            modifier = Modifier.testTag("backup_button")
                        ) {
                            Text(if (isArabic) "نسخ" else "Backup")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = GeoOutlineVariant, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Restore Backup Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = GeoPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isArabic) StoreStrings.RESTORE_BACKUP_AR else StoreStrings.RESTORE_BACKUP_EN,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isArabic) StoreStrings.RESTORE_BACKUP_DESC_AR else StoreStrings.RESTORE_BACKUP_DESC_EN,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                openDocLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("restore_button")
                        ) {
                            Text(if (isArabic) "استعادة" else "Restore")
                        }
                    }
                }
            }

            // 6. Danger Zone (Clearly Separated Section Inside App Settings)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StatusRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = StatusRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "منطقة الخطر: إعادة ضبط المصنع للبيانات" else "Danger Zone: Reset Application Data",
                            fontWeight = FontWeight.Bold,
                            color = StatusRed,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isArabic) {
                            "تحذير: سيؤدي هذا الإجراء إلى حذف جميع المعاملات والحسابات والعملاء وإعادة تعيين بيانات التطبيق الافتراضية. لا يمكن التراجع عن هذه العملية."
                        } else {
                            "Warning: This will permanently erase all transactions, customer accounts, and records and restore default demo data. This action cannot be undone."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { showConfirmReset = true },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("reset_data_button")
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "مسح وإعادة ضبط البيانات" else "Reset All Data",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showConfirmReset) {
        AlertDialog(
            onDismissRequest = { showConfirmReset = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (isArabic) "⚠️ تأكيد شديد لإعادة ضبط البيانات" else "⚠️ Strong Warning: Confirm Data Reset",
                    fontWeight = FontWeight.Bold,
                    color = StatusRed
                )
            },
            text = {
                Text(
                    text = if (isArabic) {
                        "أنت على وشك حذف جميع سجلات العملاء، المعاملات، الحسابات، والفواتير المسجلة نهائياً.\n\nلن تتمكن من استعادة هذه البيانات إلا إذا كنت تمتلك نسخة احتياطية سابقة.\n\nهل أنت متأكد تماماً من رغبتك في المتابعة ومسح كافة البيانات؟"
                    } else {
                        "You are about to permanently delete all customer accounts, transaction records, and ledger history.\n\nYou will not be able to recover this data unless you have an existing backup file.\n\nAre you absolutely sure you want to proceed and reset all data?"
                    },
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetData?.invoke()
                        showConfirmReset = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text(text = if (isArabic) "نعم، مسح كافة البيانات" else "Yes, Delete Everything")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmReset = false }) {
                    Text(text = if (isArabic) "إلغاء وتراجع" else "Cancel")
                }
            }
        )
    }
}
