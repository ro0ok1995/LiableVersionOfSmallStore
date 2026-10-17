package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.ProductItem
import com.example.ui.theme.GeoOutline
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.util.ProductImageHelper
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AddEditProductDialog(
    product: ProductItem?,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, price: Double, costPrice: Double, category: String, unit: String, imageUri: String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(product?.name ?: "") }
    var priceStr by remember { mutableStateOf(product?.let { if (it.price > 0) it.price.toString() else "" } ?: "") }
    var costPriceStr by remember { mutableStateOf(product?.let { if (it.costPrice > 0) it.costPrice.toString() else "" } ?: "") }
    var category by remember { mutableStateOf(product?.category ?: (if (isArabic) "عام" else "General")) }
    var unit by remember { mutableStateOf(product?.unit ?: (if (isArabic) "حبة" else "Piece")) }
    var currentImageUri by remember { mutableStateOf(product?.imageUri) }
    var isCompressingImage by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf(false) }
    var priceError by remember { mutableStateOf(false) }

    // Image Picker launcher (ActivityResultContracts.GetContent - zero permissions, reliable)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { sourceUri: Uri? ->
        if (sourceUri != null) {
            isCompressingImage = true
            coroutineScope.launch {
                val tempId = product?.id ?: "new_${System.currentTimeMillis()}"
                val compressedPath = ProductImageHelper.saveCompressedProductImage(
                    context = context,
                    sourceUri = sourceUri,
                    productId = tempId
                )
                if (compressedPath != null) {
                    currentImageUri = compressedPath
                }
                isCompressingImage = false
            }
        }
    }

    val presetCategories = if (isArabic) {
        listOf("عام", "مواد غذائية", "مشروبات", "ألبان", "زيوت", "معلبات", "منظفات")
    } else {
        listOf("General", "Groceries", "Beverages", "Dairy", "Oils", "Canned", "Cleaners")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (product == null) {
                    if (isArabic) "إضافة منتج جديد" else "Add New Product"
                } else {
                    if (isArabic) "تعديل بيانات المنتج" else "Edit Product"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Product Image Picker Section
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GeoOutlineVariant, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thumbnail Preview
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, GeoOutline, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompressingImage) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = GeoPrimary
                                )
                            } else if (!currentImageUri.isNullOrBlank()) {
                                val file = File(currentImageUri!!)
                                if (file.exists()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(file)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = GeoPrimary
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isArabic) "صورة المنتج (اختيارية)" else "Product Image (Optional)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isArabic) "يتم ضغطها تلقائياً لتوفير المساحة" else "Auto compressed & downscaled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("select_product_image_button")
                                ) {
                                    Text(
                                        text = if (currentImageUri.isNullOrBlank()) {
                                            if (isArabic) "اختيار صورة" else "Select Photo"
                                        } else {
                                            if (isArabic) "تغيير" else "Change"
                                        },
                                        fontSize = 11.sp
                                    )
                                }

                                if (!currentImageUri.isNullOrBlank()) {
                                    TextButton(
                                        onClick = { currentImageUri = null },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("remove_product_image_button")
                                    ) {
                                        Text(
                                            text = if (isArabic) "إزالة" else "Remove",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Product Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError && it.isNotBlank()) nameError = false
                    },
                    label = { Text(if (isArabic) "اسم المنتج *" else "Product Name *") },
                    isError = nameError,
                    supportingText = {
                        if (nameError) {
                            Text(if (isArabic) "يرجى كتابة اسم المنتج" else "Product name is required")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_name_input")
                )

                // Prices Row: Sale Price & Cost Price
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = {
                            priceStr = it
                            if (priceError && it.isNotBlank()) priceError = false
                        },
                        label = { Text(if (isArabic) "سعر البيع *" else "Sale Price *") },
                        isError = priceError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_price_input")
                    )

                    OutlinedTextField(
                        value = costPriceStr,
                        onValueChange = { costPriceStr = it },
                        label = { Text(if (isArabic) "سعر التكلفة" else "Cost Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_cost_price_input")
                    )
                }

                // Category & Unit Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text(if (isArabic) "التصنيف" else "Category") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_category_input")
                    )

                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(if (isArabic) "الوحدة" else "Unit") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_unit_input")
                    )
                }

                // Quick Category Suggestions
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presetCategories) { preset ->
                        Surface(
                            color = if (category == preset) GeoPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { category = preset }
                        ) {
                            Text(
                                text = preset,
                                fontSize = 11.sp,
                                color = if (category == preset) GeoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    val parsedPrice = priceStr.toDoubleOrNull()
                    val parsedCost = costPriceStr.toDoubleOrNull() ?: 0.0

                    if (cleanName.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    if (parsedPrice == null || parsedPrice <= 0.0) {
                        priceError = true
                        return@Button
                    }

                    onSave(
                        cleanName,
                        parsedPrice,
                        parsedCost,
                        category.trim().ifBlank { if (isArabic) "عام" else "General" },
                        unit.trim().ifBlank { if (isArabic) "حبة" else "Piece" },
                        currentImageUri
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GeoPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_product_button")
            ) {
                Text(if (isArabic) "حفظ المنتج" else "Save Product")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_product_dialog_button")
            ) {
                Text(if (isArabic) "إلغاء" else "Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
