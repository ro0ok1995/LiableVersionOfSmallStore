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
import com.example.ui.components.ProductSearchField
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
import com.example.accounting.ProductStockSummary
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
    ALL,
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
    productStockMap: Map<String, ProductStockSummary> = emptyMap(),
    onRecordStockAdjustment: ((productId: String, delta: Int, reason: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(ProductTabFilter.ALL) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var showAddDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<ProductItem?>(null) }
    var productToArchive by remember { mutableStateOf<ProductItem?>(null) }
    var productToAdjust by remember { mutableStateOf<ProductItem?>(null) }

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
        ProductTabFilter.ALL -> products
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

        // Search Bar (unified design matching Home and Accounts)
        ProductSearchField(
            products = baseList,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onProductSelected = { product ->
                searchQuery = product.name
            },
            onClearSelection = {
                searchQuery = ""
            },
            placeholderText = if (isArabic) "البحث باسم المنتج أو التصنيف..." else "Search product name or category...",
            isArabic = isArabic,
            inputTestTag = "product_search_input",
            dropdownTestTag = "product_search_suggestions",
            itemTagPrefix = "product_suggestion_",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Filter Area: Exactly Two Rows (Status row + Product Types/Categories row)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ROW 1 — STATUS: All | Active | Archived
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedTab == ProductTabFilter.ALL,
                    onClick = { selectedTab = ProductTabFilter.ALL },
                    label = {
                        Text(
                            text = if (isArabic) "الكل (${products.size})" else "All (${products.size})",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == ProductTabFilter.ALL) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoPrimary.copy(alpha = 0.15f),
                        selectedLabelColor = GeoPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("filter_tab_all_products")
                )

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
                        selectedContainerColor = StatusGreen.copy(alpha = 0.15f),
                        selectedLabelColor = StatusGreen
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
            }

            // ROW 2 — EXISTING PRODUCT TYPES / CATEGORIES
            if (allCategories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == null,
                            onClick = { selectedCategoryFilter = null },
                            label = { Text(if (isArabic) "كل التصنيفات" else "All Categories", fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("filter_category_all")
                        )
                    }
                    items(allCategories) { category ->
                        FilterChip(
                            selected = selectedCategoryFilter == category,
                            onClick = {
                                selectedCategoryFilter = if (selectedCategoryFilter == category) null else category
                            },
                            label = { Text(category, fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("filter_category_$category")
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
                        text = if (selectedTab != ProductTabFilter.ARCHIVED && searchQuery.isEmpty() && selectedCategoryFilter == null) {
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
                    val stockSummary = productStockMap[product.id]
                    ProductCardItem(
                        product = product,
                        isArchived = isArchived,
                        isArabic = isArabic,
                        stockSummary = stockSummary,
                        onEditClick = { productToEdit = product },
                        onArchiveClick = { productToArchive = product },
                        onRestoreClick = { onUnarchiveProduct(product.id) },
                        onAdjustStockClick = { productToAdjust = product }
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

    // Physical Inventory Adjustment Dialog
    if (productToAdjust != null) {
        val currentStock = productStockMap[productToAdjust!!.id]?.quantityOnHand ?: 0
        var deltaText by remember { mutableStateOf("") }
        var isAddition by remember { mutableStateOf(true) }
        var reasonText by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { productToAdjust = null },
            title = {
                Text(
                    text = if (isArabic) "تسوية مخزون: ${productToAdjust!!.name}" else "Inventory Adjustment: ${productToAdjust!!.name}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isArabic) "المخزون المحسوب حالياً: $currentStock" else "Current Ledger Stock: $currentStock",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isAddition,
                            onClick = { isAddition = true },
                            label = { Text(if (isArabic) "زيادة (+)" else "Add (+)") }
                        )
                        FilterChip(
                            selected = !isAddition,
                            onClick = { isAddition = false },
                            label = { Text(if (isArabic) "عجز / خصم (-)" else "Reduce (-)") }
                        )
                    }
                    OutlinedTextField(
                        value = deltaText,
                        onValueChange = { deltaText = it; errorMsg = null },
                        label = { Text(if (isArabic) "الكمية" else "Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_adjust_stock_qty")
                    )
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it; errorMsg = null },
                        label = { Text(if (isArabic) "السبب / البيان" else "Reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_adjust_stock_reason")
                    )
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = deltaText.toIntOrNull() ?: 0
                        if (qty <= 0) {
                            errorMsg = if (isArabic) "يرجى إدخال كمية صحيحة أكبر من صفر" else "Please enter a valid quantity > 0"
                            return@Button
                        }
                        if (reasonText.isBlank()) {
                            errorMsg = if (isArabic) "السبب مطلوب" else "Reason is required"
                            return@Button
                        }
                        val signedDelta = if (isAddition) qty else -qty
                        onRecordStockAdjustment?.invoke(productToAdjust!!.id, signedDelta, reasonText.trim())
                        productToAdjust = null
                    },
                    modifier = Modifier.testTag("btn_confirm_adjust_stock")
                ) {
                    Text(if (isArabic) "تأكيد التسوية" else "Confirm Adjustment")
                }
            },
            dismissButton = {
                TextButton(onClick = { productToAdjust = null }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }
}
