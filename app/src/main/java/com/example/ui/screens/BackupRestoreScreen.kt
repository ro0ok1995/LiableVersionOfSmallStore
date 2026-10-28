package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.example.model.LanguageMode
import com.example.model.StoreStrings
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    languageMode: LanguageMode,
    onBackClick: () -> Unit,
    onExportBackup: ((android.net.Uri) -> Unit)? = null,
    onImportBackup: ((android.net.Uri) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC

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
            .testTag("backup_restore_screen")
    ) {
        TopAppBar(
            title = {
                Text(
                    text = if (isArabic) StoreStrings.SECTION_BACKUP_AR else StoreStrings.SECTION_BACKUP_EN,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick, modifier = Modifier.testTag("backup_restore_back_button")) {
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
            // Backup & Restore Section
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

            // Version 2 accounting backup assurance
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backup_v2_info_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isArabic) "نسخة محاسبية كاملة — الإصدار 2" else "Complete Accounting Backup — Version 2",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isArabic) {
                            "تشمل المبيعات والمرتجعات والتسويات والمدفوعات والموردين والمصاريف وحركات المخزون والحسابات المالية، مع الحفاظ على المعرّفات والروابط. بعد الاستعادة تتم مطابقة النتائج المحاسبية تلقائيًا."
                        } else {
                            "Includes sales, returns, payments, suppliers, expenses, inventory movements and financial accounts while preserving IDs and relationships. Restore performs an accounting reconciliation before completing."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                }
            }


        }
    }

}
