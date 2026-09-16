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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.AppCurrency
import com.example.model.LanguageMode
import com.example.model.ProductItem
import com.example.ui.theme.GeoOutline
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusAmberBg
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.util.ProductImageHelper
import kotlinx.coroutines.launch
import java.io.File

private enum class ProductTabFilter {
    ACTIVE,
    ARCHIVED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductManagementScreen(
    products: List<ProductItem>,
    archivedProductIds: Set<String>,
    languageMode: LanguageMode,
    onBackClick: () -> Unit,
    onAddProduct: (name: String, price: Double, costPrice: Double, category: String, unit: String, imageUri: String?) -> Unit,
    onUpdateProduct: (ProductItem) -> Unit,
    onArchiveProduct: (String) -> Unit,
    onUnarchiveProduct: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(ProductTabFilter.ACTIVE) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var showAddDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<ProductItem?>(null) }
    var productToArchive by remember { mutableStateOf<ProductItem?>(null) }

    val activeProducts = remember(products, archivedProductIds) {
        products.filter { it.id !in archivedProductIds }
    }
    val archivedProducts = remember(products, archivedProductIds) {
        products.filter { it.id in archivedProductIds }
    }

    // Dynamic categories extracted from all products
    val allCategories = remember(products) {
        products.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    // Current list by tab and search
    val baseList = when (selectedTab) {
        ProductTabFilter.ACTIVE -> activeProducts
        ProductTabFilter.ARCHIVED -> archivedProducts
    }

    val filteredProducts = remember(baseList, searchQuery, selectedCategoryFilter) {
        baseList.filter { product ->
            val matchesQuery = searchQuery.isBlank() ||
                    product.name.contains(searchQuery.trim(), ignoreCase = true) ||
                    product.category.contains(searchQuery.trim(), ignoreCase = true)

            val matchesCat = selectedCategoryFilter == null || product.category.trim() == selectedCategoryFilter
            matchesQuery && matchesCat
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("product_management_screen")
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = if (isArabic) "إدارة المنتجات" else "Product Management",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("product_management_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isArabic) "رجوع" else "Back"
                    )
                }
            },
            actions = {
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GeoPrimary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("product_management_add_product_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isArabic) "إضافة منتج" else "Add Product",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Summary Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .border(1.dp, GeoOutlineVariant, RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${products.size}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = GeoPrimary
                    )
                    Text(
                        text = if (isArabic) "إجمالي الأصناف" else "Total Items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(GeoOutlineVariant)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${activeProducts.size}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = StatusGreen
                    )
                    Text(
                        text = if (isArabic) "المنتجات النشطة" else "Active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(GeoOutlineVariant)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${archivedProducts.size}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (archivedProducts.isNotEmpty()) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isArabic) "المؤرشفة" else "Archived",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = if (isArabic) "البحث باسم المنتج أو التصنيف..." else "Search product name or category...",
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = if (isArabic) "مسح" else "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = GeoPrimary,
                unfocusedBorderColor = GeoOutline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("product_search_input")
        )

        // Filter Tabs & Categories Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedTab == ProductTabFilter.ACTIVE,
                onClick = { selectedTab = ProductTabFilter.ACTIVE },
                label = {
                    Text(
                        text = if (isArabic) "النشطة (${activeProducts.size})" else "Active (${activeProducts.size})",
                        fontSize = 12.sp,
                        fontWeight = if (selectedTab == ProductTabFilter.ACTIVE) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GeoPrimary.copy(alpha = 0.15f),
                    selectedLabelColor = GeoPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("filter_tab_active_products")
            )

            FilterChip(
                selected = selectedTab == ProductTabFilter.ARCHIVED,
                onClick = { selectedTab = ProductTabFilter.ARCHIVED },
                label = {
                    Text(
                        text = if (isArabic) "المؤرشفة (${archivedProducts.size})" else "Archived (${archivedProducts.size})",
                        fontSize = 12.sp,
                        fontWeight = if (selectedTab == ProductTabFilter.ARCHIVED) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StatusAmber.copy(alpha = 0.15f),
                    selectedLabelColor = StatusAmber
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("filter_tab_archived_products")
            )

            if (allCategories.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(GeoOutline)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == null,
                            onClick = { selectedCategoryFilter = null },
                            label = { Text(if (isArabic) "كل التصنيفات" else "All Categories", fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    items(allCategories) { category ->
                        FilterChip(
                            selected = selectedCategoryFilter == category,
                            onClick = {
                                selectedCategoryFilter = if (selectedCategoryFilter == category) null else category
                            },
                            label = { Text(category, fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }

        // Product Cards List
        if (filteredProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (selectedTab == ProductTabFilter.ARCHIVED) Icons.Default.Archive else Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (selectedTab == ProductTabFilter.ARCHIVED) {
                            if (isArabic) "لا توجد منتجات في الأرشيف" else "No archived products"
                        } else if (searchQuery.isNotEmpty() || selectedCategoryFilter != null) {
                            if (isArabic) "لا توجد نتائج مطابقة للبحث" else "No matching products found"
                        } else {
                            if (isArabic) "لا توجد منتجات مسجلة" else "No products registered"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (selectedTab == ProductTabFilter.ACTIVE && searchQuery.isEmpty() && selectedCategoryFilter == null) {
                            if (isArabic) "اضغط على زر 'إضافة منتج' لإدراج منتج جديد في قاعدة البيانات" else "Tap 'Add Product' to register a new product item"
                        } else {
                            if (isArabic) "جرب تغيير مصطلح البحث أو إزالة التصفية" else "Try adjusting your search terms or filters"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = filteredProducts,
                    key = { it.id }
                ) { product ->
                    val isArchived = product.id in archivedProductIds
                    ProductCardItem(
                        product = product,
                        isArchived = isArchived,
                        isArabic = isArabic,
                        onEditClick = { productToEdit = product },
                        onArchiveClick = { productToArchive = product },
                        onRestoreClick = { onUnarchiveProduct(product.id) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Add Product Dialog
    if (showAddDialog) {
        AddEditProductDialog(
            product = null,
            isArabic = isArabic,
            onDismiss = { showAddDialog = false },
            onSave = { name, price, costPrice, category, unit, imageUri ->
                onAddProduct(name, price, costPrice, category, unit, imageUri)
                showAddDialog = false
            }
        )
    }

    // Edit Product Dialog
    productToEdit?.let { product ->
        AddEditProductDialog(
            product = product,
            isArabic = isArabic,
            onDismiss = { productToEdit = null },
            onSave = { name, price, costPrice, category, unit, imageUri ->
                onUpdateProduct(
                    product.copy(
                        name = name,
                        price = price,
                        costPrice = costPrice,
                        category = category,
                        unit = unit,
                        imageUri = imageUri
                    )
                )
                productToEdit = null
            }
        )
    }

    // Archive Confirmation Dialog
    productToArchive?.let { product ->
        AlertDialog(
            onDismissRequest = { productToArchive = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = null,
                    tint = StatusAmber,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (isArabic) "أرشفة المنتج" else "Archive Product",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isArabic) {
                            "هل تريد بالتأكيد نقل المنتج \"${product.name}\" إلى الأرشيف؟"
                        } else {
                            "Are you sure you want to archive \"${product.name}\"?"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isArabic) {
                            "لن يتم حذف المنتج أو سجل مبيعاته السابقة نهائياً. سيتم إخفاؤه من قوائم البيع المباشرة فقط، ويمكنك استعادته في أي وقت من قسم المؤرشفات."
                        } else {
                            "The product and its sales history will NOT be permanently deleted. It will only be hidden from direct sale lists and can be restored at any time from the archive tab."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onArchiveProduct(product.id)
                        productToArchive = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusAmber,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_archive_product_button")
                ) {
                    Text(if (isArabic) "أرشفة المنتج" else "Archive")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { productToArchive = null },
                    modifier = Modifier.testTag("cancel_archive_product_button")
                ) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun ProductCardItem(
    product: ProductItem,
    isArchived: Boolean,
    isArabic: Boolean,
    onEditClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, GeoOutlineVariant, RoundedCornerShape(14.dp))
            .testTag("product_card_${product.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Optional Compact Product Thumbnail
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(1.dp, GeoOutlineVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!product.imageUri.isNullOrBlank()) {
                        val file = File(product.imageUri)
                        if (file.exists()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(file)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = product.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            // File not yet on disk or path mismatch, display clean fallback
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = GeoPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    } else {
                        // Clean default placeholder when product has no image
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = GeoPrimary.copy(alpha = 0.85f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 2. Product Name, Category, and Unit
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = product.category,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = product.unit,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 3. Price Highlight
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = GeoPrimary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, GeoPrimary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = AppCurrency.formatAmountWithDecimals(product.price, isArabic),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = GeoPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (product.costPrice > 0.0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isArabic) "التكلفة: ${AppCurrency.formatAmountWithDecimals(product.costPrice, isArabic)}"
                            else "Cost: ${AppCurrency.formatAmountWithDecimals(product.costPrice, isArabic)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = GeoOutlineVariant, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // 4. Status Badge & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status Badge
                if (isArchived) {
                    Surface(
                        color = StatusAmberBg,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.border(1.dp, StatusAmber.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(StatusAmber, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isArabic) "مؤرشف" else "Archived",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusAmber
                            )
                        }
                    }
                } else {
                    Surface(
                        color = StatusGreenBg,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.border(1.dp, StatusGreen.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(StatusGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isArabic) "نشط" else "Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusGreen
                            )
                        }
                    }
                }

                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isArchived) {
                        OutlinedButton(
                            onClick = onRestoreClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StatusAmber
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("restore_product_${product.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isArabic) "استعادة" else "Restore",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onEditClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = GeoPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("edit_product_${product.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isArabic) "تعديل" else "Edit",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = onArchiveClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("archive_product_${product.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isArabic) "أرشفة" else "Archive",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddEditProductDialog(
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
