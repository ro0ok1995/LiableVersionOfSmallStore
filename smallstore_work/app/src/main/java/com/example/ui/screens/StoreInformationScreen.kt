package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.example.model.LanguageMode
import com.example.model.StoreInfo
import com.example.model.StoreStrings
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusGreen

private fun isValidStoreNameChar(c: Char): Boolean {
    if (c in 'a'..'z' || c in 'A'..'Z') return true
    if (c in '0'..'9' || c in '\u0660'..'\u0669' || c in '\u06F0'..'\u06F9') return true
    if (c == ' ') return true
    if (c == '-' || c == '_' || c == '.' || c == '&' || c == '\'' || c == ',' || c == '،' || c == '/' || c == '(' || c == ')') return true
    val block = Character.UnicodeBlock.of(c)
    return block == Character.UnicodeBlock.ARABIC ||
        block == Character.UnicodeBlock.ARABIC_SUPPLEMENT ||
        block == Character.UnicodeBlock.ARABIC_EXTENDED_A ||
        block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A ||
        block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreInformationScreen(
    storeInfo: StoreInfo,
    languageMode: LanguageMode,
    onBackClick: () -> Unit,
    onSaveStoreInfo: (StoreInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val focusManager = LocalFocusManager.current
    var name by remember(storeInfo) { mutableStateOf(storeInfo.storeName) }
    var owner by remember(storeInfo) { mutableStateOf(storeInfo.ownerName) }
    var phone by remember(storeInfo) { mutableStateOf(storeInfo.phone) }
    var address by remember(storeInfo) { mutableStateOf(storeInfo.address) }
    var taxNumber by remember(storeInfo) { mutableStateOf(storeInfo.taxNumber) }
    var crNumber by remember(storeInfo) { mutableStateOf(storeInfo.crNumber) }
    var showSavedMessage by remember { mutableStateOf(false) }

    val isLengthValid = name.length <= 40
    val isCharsValid = name.all { isValidStoreNameChar(it) }
    val isStoreNameValid = isLengthValid && isCharsValid

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("store_information_screen")
    ) {
        TopAppBar(
            title = {
                Text(
                    text = if (isArabic) StoreStrings.STORE_INFORMATION_AR else StoreStrings.STORE_INFORMATION_EN,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    focusManager.clearFocus()
                    onBackClick()
                }, modifier = Modifier.testTag("store_info_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { input ->
                    if (input.all { isValidStoreNameChar(it) }) {
                        name = input
                        showSavedMessage = false
                    }
                },
                label = { Text(if (isArabic) "اسم المتجر" else "Store Name") },
                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                singleLine = true,
                isError = !isLengthValid,
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (!isLengthValid) {
                            Text(
                                text = if (isArabic) "الحد الأقصى 40 حرفاً" else "Maximum 40 characters",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Text(
                            text = "${name.length}/40",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLengthValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("store_name_counter")
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_store_name")
            )

            OutlinedTextField(
                value = owner,
                onValueChange = { owner = it },
                label = { Text(if (isArabic) "اسم المالك" else "Owner Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_owner_name")
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(if (isArabic) "رقم الهاتف" else "Phone Number") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_store_phone")
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(if (isArabic) "العنوان / الموقع" else "Address / Location") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_store_address")
            )

            OutlinedTextField(
                value = taxNumber,
                onValueChange = { taxNumber = it },
                label = { Text(if (isArabic) "الرقم الضريبي (إن وجد)" else "Tax / VAT Number") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_tax_number")
            )

            OutlinedTextField(
                value = crNumber,
                onValueChange = { crNumber = it },
                label = { Text(if (isArabic) "السجل التجاري (إن وجد)" else "Commercial Register Number") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_cr_number")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (!isStoreNameValid) return@Button
                    focusManager.clearFocus()
                    onSaveStoreInfo(
                        storeInfo.copy(
                            storeName = name.trim(),
                            ownerName = owner,
                            phone = phone,
                            address = address,
                            taxNumber = taxNumber,
                            crNumber = crNumber
                        )
                    )
                    showSavedMessage = true
                },
                enabled = isStoreNameValid,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_store_info_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isArabic) "حفظ البيانات" else "Save Information",
                    fontWeight = FontWeight.Bold
                )
            }

            if (showSavedMessage) {
                Text(
                    text = if (isArabic) "✓ تم حفظ البيانات بنجاح!" else "✓ Information saved successfully!",
                    color = StatusGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
