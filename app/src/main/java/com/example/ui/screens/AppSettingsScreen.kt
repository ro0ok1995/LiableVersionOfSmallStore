package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC

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
        }
    }
}
