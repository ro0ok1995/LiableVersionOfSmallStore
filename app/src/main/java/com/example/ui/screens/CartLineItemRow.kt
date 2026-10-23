package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CartItem
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusRed
import java.util.Locale

@Composable
fun CartLineItemRow(
    item: CartItem,
    currency: String,
    isArabic: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetQuantity: (Int) -> Unit = {},
    onRemove: () -> Unit
) {
    var showQuantityEditor by remember { mutableStateOf(false) }
    var quantityText by remember(item.quantity) { mutableStateOf(item.quantity.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val lineTotal = item.product.price * item.quantity

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.product.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = String.format(Locale.US, "%.2f %s × %d = %.2f %s", item.product.price, currency, item.quantity, lineTotal, currency),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("cart_item_controls_${item.product.id}")
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onDecrement() }
                    .testTag("cart_decrement_${item.product.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = if (isArabic) "تقليل الكمية" else "Decrease quantity",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .clickable {
                        quantityText = item.quantity.toString()
                        errorMessage = null
                        showQuantityEditor = true
                    }
                    .testTag("cart_quantity_display_${item.product.id}")
            ) {
                Text(
                    text = "${item.quantity}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(GeoPrimary)
                    .clickable { onIncrement() }
                    .testTag("cart_increment_${item.product.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (isArabic) "زيادة الكمية" else "Increase quantity",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("cart_remove_${item.product.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = if (isArabic) "حذف العنصر" else "Remove item",
                    tint = StatusRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showQuantityEditor) {
        AlertDialog(
            onDismissRequest = {
                showQuantityEditor = false
                errorMessage = null
            },
            title = { Text(if (isArabic) "تعديل الكمية" else "Edit quantity") },
            text = {
                Column {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = {
                            quantityText = it
                            errorMessage = null
                        },
                        label = { Text(if (isArabic) "الكمية" else "Quantity") },
                        isError = errorMessage != null,
                        supportingText = if (errorMessage != null) {
                            { Text(errorMessage!!, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("cart_quantity_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = quantityText.trim()
                        val value = trimmed.toIntOrNull()
                        if (value == null || value <= 0) {
                            errorMessage = if (isArabic) "يرجى إدخال كمية صحيحة أكبر من صفر" else "Please enter a valid quantity greater than 0"
                        } else {
                            onSetQuantity(value)
                            showQuantityEditor = false
                            errorMessage = null
                        }
                    },
                    modifier = Modifier.testTag("cart_quantity_save_button")
                ) { Text(if (isArabic) "حفظ" else "Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showQuantityEditor = false
                        errorMessage = null
                    },
                    modifier = Modifier.testTag("cart_quantity_cancel_button")
                ) { Text(if (isArabic) "إلغاء" else "Cancel") }
            }
        )
    }
}
