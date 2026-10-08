package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.AppCurrency
import com.example.model.ArchiveConflict
import com.example.model.CustomerAccount
import com.example.model.LanguageMode
import com.example.model.ProductItem
import com.example.model.StoreStrings
import com.example.model.TransactionItem
import com.example.ui.theme.GeoOutline
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusAmberBg
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import kotlinx.coroutines.launch
import java.io.File

enum class ArchiveTab(val index: Int) {
    CUSTOMERS(0),
    PRODUCTS(1),
}

sealed class PendingDeleteTarget {
    data class Customer(val customer: CustomerAccount) : PendingDeleteTarget()
    data class Product(val product: ProductItem) : PendingDeleteTarget()
}

sealed class PendingRestoreTarget {
    data class Customer(val customer: CustomerAccount) : PendingRestoreTarget()
    data class Product(val product: ProductItem) : PendingRestoreTarget()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    archivedCustomers: List<CustomerAccount>,
    archivedProducts: List<ProductItem>,
        languageMode: LanguageMode,
    onBackClick: () -> Unit,
    onRestoreCustomer: (CustomerAccount) -> Unit = {},
    onPermanentDeleteCustomer: (CustomerAccount) -> Unit = {},
    onRestoreProduct: (ProductItem) -> Unit = {},
    onPermanentDeleteProduct: (ProductItem) -> Unit = {},
            activeConflict: ArchiveConflict? = null,
    onResolveConflictSeparate: (ArchiveConflict) -> Unit = {},
    onDismissConflict: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTabIndex by remember { mutableIntStateOf(ArchiveTab.CUSTOMERS.index) }
    var searchQuery by remember { mutableStateOf("") }
    var showSampleDataPreview by remember { mutableStateOf(false) }

    // Dialog state
    var pendingDeleteTarget by remember { mutableStateOf<PendingDeleteTarget?>(null) }
    var pendingRestoreTarget by remember { mutableStateOf<PendingRestoreTarget?>(null) }

    // Built-in sample records for visual testing and demonstration when actual archives are empty
    val sampleArchivedCustomers = remember {
        listOf(
            CustomerAccount(
                id = "arch_sample_c1",
                customerName = if (isArabic) "خالد ناصر الدوسري" else "Khalid Nasser",
                phone = "0501112233",
                balance = 125.50,
                totalDebt = 125.50,
                lastTransactionDate = "2026-09-01"
            ),
            CustomerAccount(
                id = "arch_sample_c2",
                customerName = if (isArabic) "مؤسسة الوفاء للتجارة" else "Al-Wafa Corp",
                phone = "0559988776",
                balance = 0.0,
                totalDebt = 0.0,
                lastTransactionDate = "2026-08-25"
            )
        )
    }

    val sampleArchivedProducts = remember {
        listOf(
            ProductItem(
                id = "arch_sample_p1",
                name = if (isArabic) "حليب بودرة مجفف 900 جم" else "Powder Milk 900g",
                price = 38.00,
                costPrice = 30.00,
                category = if (isArabic) "ألبان" else "Dairy",
                unit = if (isArabic) "علبة" else "Can",
                imageUri = null
            ),
            ProductItem(
                id = "arch_sample_p2",
                name = if (isArabic) "زيت دوار الشمس 5 لتر" else "Sunflower Oil 5L",
                price = 45.50,
                costPrice = 36.00,
                category = if (isArabic) "زيوت" else "Oils",
                unit = if (isArabic) "حبة" else "Piece",
                imageUri = null
            )
        )
    }

    // Determine displayed records based on actual data or sample preview
    val effectiveCustomers = if (archivedCustomers.isNotEmpty()) {
        archivedCustomers
    } else if (showSampleDataPreview) {
        sampleArchivedCustomers
    } else {
        emptyList()
    }

    val effectiveProducts = if (archivedProducts.isNotEmpty()) {
        archivedProducts
    } else if (showSampleDataPreview) {
        sampleArchivedProducts
    } else {
        emptyList()
    }

    // Filter by search query
    val filteredCustomers = remember(effectiveCustomers, searchQuery) {
        if (searchQuery.isBlank()) effectiveCustomers
        else {
            val q = searchQuery.trim().lowercase()
            effectiveCustomers.filter {
                it.customerName.lowercase().contains(q) || it.phone.contains(q)
            }
        }
    }

    val filteredProducts = remember(effectiveProducts, searchQuery) {
        if (searchQuery.isBlank()) effectiveProducts
        else {
            val q = searchQuery.trim().lowercase()
            effectiveProducts.filter {
                it.name.lowercase().contains(q) || it.category.lowercase().contains(q)
            }
        }
    }



    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("archive_screen")
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = if (isArabic) StoreStrings.SECTION_ARCHIVE_TRASH_AR else StoreStrings.SECTION_ARCHIVE_TRASH_EN,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = if (isArabic) "البيانات المؤرشفة" else "Archived master data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("archive_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isArabic) "رجوع" else "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        // Tab Row: 1. Customers (العملاء), 2. Products (الأصناف), 3. Transactions (المعاملات)
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = GeoPrimary,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = GeoPrimary,
                        height = 3.dp
                    )
                }
            },
            divider = {
                HorizontalDivider(color = GeoOutlineVariant, thickness = 1.dp)
            },
            modifier = Modifier.testTag("archive_tab_row")
        ) {
            // Tab 1: Customers
            Tab(
                selected = selectedTabIndex == ArchiveTab.CUSTOMERS.index,
                onClick = { selectedTabIndex = ArchiveTab.CUSTOMERS.index },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isArabic) StoreStrings.ARCHIVE_TAB_CUSTOMERS_AR else StoreStrings.ARCHIVE_TAB_CUSTOMERS_EN,
                            fontWeight = if (selectedTabIndex == ArchiveTab.CUSTOMERS.index) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        ArchiveTabBadge(count = effectiveCustomers.size, isSelected = selectedTabIndex == ArchiveTab.CUSTOMERS.index)
                    }
                },
                modifier = Modifier.testTag("archive_tab_customers")
            )

            // Tab 2: Products
            Tab(
                selected = selectedTabIndex == ArchiveTab.PRODUCTS.index,
                onClick = { selectedTabIndex = ArchiveTab.PRODUCTS.index },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isArabic) StoreStrings.ARCHIVE_TAB_PRODUCTS_AR else StoreStrings.ARCHIVE_TAB_PRODUCTS_EN,
                            fontWeight = if (selectedTabIndex == ArchiveTab.PRODUCTS.index) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        ArchiveTabBadge(count = effectiveProducts.size, isSelected = selectedTabIndex == ArchiveTab.PRODUCTS.index)
                    }
                },
                modifier = Modifier.testTag("archive_tab_products")
            )

        }

        // Search Bar (Active when list has items)
        val currentTabItemCount = when (selectedTabIndex) {
            ArchiveTab.CUSTOMERS.index -> effectiveCustomers.size
            ArchiveTab.PRODUCTS.index -> effectiveProducts.size
            else -> 0
        }

        if (currentTabItemCount > 0 || searchQuery.isNotBlank()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = when (selectedTabIndex) {
                            ArchiveTab.CUSTOMERS.index -> if (isArabic) "بحث في العملاء المؤرشفين..." else "Search archived customers..."
                            ArchiveTab.PRODUCTS.index -> if (isArabic) "بحث في الأصناف المؤرشفة..." else "Search archived products..."
                            else -> if (isArabic) "بحث في المعاملات المؤرشفة..." else "Search archived transactions..."
                        },
                        style = MaterialTheme.typography.bodyMedium
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
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GeoPrimary,
                    unfocusedBorderColor = GeoOutline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("archive_search_field")
            )
        }

        // Preview notice banner when sample demonstration mode is toggled
        if (showSampleDataPreview) {
            Surface(
                color = GeoPrimary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .border(1.dp, GeoPrimary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = GeoPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) {
                            "معاينة تفاعلية: يتم عرض سجلات نموذجية لاختبار الواجهة والحوارات دون المساس بقاعدة البيانات."
                        } else {
                            "Interactive preview: Displaying sample records to inspect layouts and dialogs without touching database."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                        onClick = { showSampleDataPreview = false },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isArabic) "إخفاء" else "Hide",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeoPrimary
                        )
                    }
                }
            }
        }

        // Content Area per Tab
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTabIndex) {
                ArchiveTab.CUSTOMERS.index -> {
                    if (filteredCustomers.isEmpty()) {
                        ArchiveEmptyState(
                            icon = Icons.Default.Person,
                            title = if (isArabic) "لا يوجد عملاء في الأرشيف" else "No Archived Customers",
                            description = if (isArabic) {
                                "عند أرشفة أي حساب عميل من شاشة إدارة العملاء، سيظهر هنا بأمان مع إمكانية استعادته أو حذفه نهائياً."
                            } else {
                                "When a customer account is archived from Customer Management, it will appear here safely for restore or permanent deletion."
                            },
                            isArabic = isArabic,
                            showSamplePreviewButton = !showSampleDataPreview,
                            onToggleSamplePreview = { showSampleDataPreview = true },
                            modifier = Modifier.testTag("archive_empty_customers")
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredCustomers, key = { it.id }) { customer ->
                                ArchivedCustomerCard(
                                    customer = customer,
                                    isArabic = isArabic,
                                    onRestoreClick = {
                                        pendingRestoreTarget = PendingRestoreTarget.Customer(customer)
                                    },
                                    onDeletePermanentlyClick = {
                                        pendingDeleteTarget = PendingDeleteTarget.Customer(customer)
                                    }
                                )
                            }
                        }
                    }
                }

                ArchiveTab.PRODUCTS.index -> {
                    if (filteredProducts.isEmpty()) {
                        ArchiveEmptyState(
                            icon = Icons.Default.Inventory2,
                            title = if (isArabic) "لا توجد أصناف في الأرشيف" else "No Archived Products",
                            description = if (isArabic) {
                                "عند أرشفة أي صنف أو منتج من شاشة إدارة المنتجات، سيتم حفظه هنا بعيداً عن شاشات البيع المباشرة."
                            } else {
                                "When a product is archived from Product Management, it will be retained here away from active sales."
                            },
                            isArabic = isArabic,
                            showSamplePreviewButton = !showSampleDataPreview,
                            onToggleSamplePreview = { showSampleDataPreview = true },
                            modifier = Modifier.testTag("archive_empty_products")
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredProducts, key = { it.id }) { product ->
                                ArchivedProductCard(
                                    product = product,
                                    isArabic = isArabic,
                                    onRestoreClick = {
                                        pendingRestoreTarget = PendingRestoreTarget.Product(product)
                                    },
                                    onDeletePermanentlyClick = {
                                        pendingDeleteTarget = PendingDeleteTarget.Product(product)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }

    // CONFIRMATION DIALOG: DELETE PERMANENTLY (Prepared visually — NO database deletion executed)
    pendingDeleteTarget?.let { target ->
        val recordName = when (target) {
            is PendingDeleteTarget.Customer -> target.customer.customerName
            is PendingDeleteTarget.Product -> target.product.name
        }

        val recordType = when (target) {
            is PendingDeleteTarget.Customer -> if (isArabic) "عميل" else "Customer"
            is PendingDeleteTarget.Product -> if (isArabic) "صنف / منتج" else "Product"
        }

        AlertDialog(
            onDismissRequest = { pendingDeleteTarget = null },
            icon = {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = if (isArabic) StoreStrings.DELETE_PERMANENTLY_AR else StoreStrings.DELETE_PERMANENTLY_EN,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Record Name Highlight Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GeoOutlineVariant, RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = recordType,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = recordName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Strict user-requested warning
                    Text(
                        text = if (isArabic) {
                            StoreStrings.DELETE_PERMANENT_WARNING_AR
                        } else {
                            StoreStrings.DELETE_PERMANENT_WARNING_EN
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )

                    Text(
                        text = if (isArabic) {
                            "تحذير: سيتم حذف هذا السجل بشكل نهائي وغير قابل للاسترجاع من قاعدة البيانات المحلية."
                        } else {
                            "Warning: This record will be permanently deleted and cannot be recovered from the local database."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentTarget = target
                        val currentTargetName = recordName
                        pendingDeleteTarget = null
                        when (currentTarget) {
                            is PendingDeleteTarget.Customer -> onPermanentDeleteCustomer(currentTarget.customer)
                            is PendingDeleteTarget.Product -> onPermanentDeleteProduct(currentTarget.product)
                        }
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = if (isArabic) {
                                    "تم حذف \"$currentTargetName\" نهائياً بنجاح"
                                } else {
                                    "\"$currentTargetName\" permanently deleted successfully"
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_delete_permanently_button")
                ) {
                    Text(if (isArabic) StoreStrings.DELETE_PERMANENTLY_AR else StoreStrings.DELETE_PERMANENTLY_EN)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingDeleteTarget = null },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("cancel_delete_permanently_button")
                ) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }

    // CONFIRMATION DIALOG: RESTORE ACTION
    pendingRestoreTarget?.let { target ->
        val recordName = when (target) {
            is PendingRestoreTarget.Customer -> target.customer.customerName
            is PendingRestoreTarget.Product -> target.product.name
        }

        AlertDialog(
            onDismissRequest = { pendingRestoreTarget = null },
            icon = {
                Surface(
                    color = GeoPrimary.copy(alpha = 0.12f),
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = GeoPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = if (isArabic) StoreStrings.RESTORE_ACTION_AR else StoreStrings.RESTORE_ACTION_EN,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isArabic) {
                            "هل تريد استعادة السجل \"$recordName\" إلى السجلات النشطة؟"
                        } else {
                            "Do you want to restore \"$recordName\" to active records?"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isArabic) {
                            "سيتم التحقق من عدم وجود تعارض مع السجلات النشطة قبل الاستعادة إلى القوائم النشطة بأمان."
                        } else {
                            "Active records will be checked for potential conflicts before restoring safely."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentTarget = target
                        val currentTargetName = recordName
                        pendingRestoreTarget = null
                        when (currentTarget) {
                            is PendingRestoreTarget.Customer -> onRestoreCustomer(currentTarget.customer)
                            is PendingRestoreTarget.Product -> onRestoreProduct(currentTarget.product)
                        }
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = if (isArabic) {
                                    "جاري استعادة \"$currentTargetName\"..."
                                } else {
                                    "Restoring \"$currentTargetName\"..."
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GeoPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_restore_button")
                ) {
                    Text(if (isArabic) StoreStrings.RESTORE_ACTION_AR else StoreStrings.RESTORE_ACTION_EN)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingRestoreTarget = null },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("cancel_restore_button")
                ) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }

    // CONFLICT REVIEW DIALOG
    activeConflict?.let { conflict ->
        ArchiveConflictDialog(
            conflict = conflict,
            isArabic = isArabic,
            onResolveAsSeparate = { onResolveConflictSeparate(conflict) },
            onKeepArchived = onDismissConflict,
            onDismiss = onDismissConflict
        )
    }
}

/**
 * Tab count badge
 */
@Composable
private fun ArchiveTabBadge(count: Int, isSelected: Boolean) {
    Surface(
        color = if (isSelected) GeoPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        modifier = Modifier.size(20.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = count.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) GeoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Dedicated clean empty state per tab
 */
@Composable
private fun ArchiveEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    isArabic: Boolean,
    showSamplePreviewButton: Boolean,
    onToggleSamplePreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = GeoPrimary.copy(alpha = 0.08f),
            shape = CircleShape,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = GeoPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (showSamplePreviewButton) {
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(
                onClick = onToggleSamplePreview,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = GeoPrimary
                ),
                modifier = Modifier.testTag("archive_preview_sample_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isArabic) "معاينة نماذج تجريبية للأرشيف" else "Preview Sample Archived Records",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
