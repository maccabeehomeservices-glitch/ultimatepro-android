package com.ultimatepro.ui.pricebook

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.PricebookCategory
import com.ultimatepro.domain.model.PricebookItem
import com.ultimatepro.ui.common.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Picker ViewModel ─────────────────────────────────────────────────────
// Scoped to the NavGraph or Activity for cross-screen shared state

data class PickedEntry(
    val item: PricebookItem,
    val qty: Int = 1,
    val price: Double? = null        // null = use item.unit_price
) {
    val effectivePrice get() = price ?: item.unit_price
    val total get() = effectivePrice * qty
    val priceOverridden get() = price != null && price != item.unit_price
}

@HiltViewModel
class PricebookPickerViewModel @Inject constructor(
    private val repo: CrmRepository
) : ViewModel() {

    private val _categories = MutableStateFlow<List<PricebookCategory>>(emptyList())
    val categories = _categories.asStateFlow()

    private val _items = MutableStateFlow<List<PricebookItem>>(emptyList())
    val items = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _search = MutableStateFlow("")
    val search = _search.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    // Selected items — survives navigation between category → item list
    private val _picked = MutableStateFlow<Map<String, PickedEntry>>(emptyMap())
    val picked = _picked.asStateFlow()

    val pickedCount  get() = _picked.value.values.sumOf { it.qty }
    val pickedTotal  get() = _picked.value.values.sumOf { it.total }

    fun loadCategories() {
        viewModelScope.launch {
            _loading.value = true
            when (val r = repo.getPricebookCategories()) {
                is Result.Success -> { _categories.value = r.data; _error.value = null }
                is Result.Error   -> _error.value = r.message ?: "Failed to load categories"
            }
            _loading.value = false
        }
    }

    fun loadItems(categoryId: String? = null, filter: String? = null) {
        viewModelScope.launch {
            _loading.value = true
            val q = _search.value.ifBlank { null }
            when (val r = repo.getPricebookItems(categoryId, q, filter)) {
                is Result.Success -> { _items.value = r.data; _error.value = null }
                is Result.Error   -> _error.value = r.message ?: "Failed to load items"
            }
            _loading.value = false
        }
    }

    fun setSearch(q: String) { _search.value = q }

    fun searchAll(filter: String? = null) {
        viewModelScope.launch {
            _loading.value = true
            when (val r = repo.getPricebookItems(null, _search.value.ifBlank { null }, filter)) {
                is Result.Success -> { _items.value = r.data; _error.value = null }
                is Result.Error   -> _error.value = r.message ?: "Failed to load items"
            }
            _loading.value = false
        }
    }

    fun clearError() { _error.value = null }

    fun pick(item: PricebookItem, qty: Int = 1) {
        val cur = _picked.value.toMutableMap()
        val ex  = cur[item.id]
        cur[item.id] = ex?.copy(qty = ex.qty + qty) ?: PickedEntry(item, qty)
        _picked.value = cur
    }

    fun setQty(itemId: String, qty: Int) {
        val cur = _picked.value.toMutableMap()
        val ex  = cur[itemId] ?: return
        if (qty <= 0) cur.remove(itemId) else cur[itemId] = ex.copy(qty = qty)
        _picked.value = cur
    }

    fun setPrice(itemId: String, price: Double?) {
        val cur = _picked.value.toMutableMap()
        cur[itemId] = cur[itemId]?.copy(price = price) ?: return
        _picked.value = cur
    }

    fun remove(itemId: String) {
        _picked.value = _picked.value.toMutableMap().also { it.remove(itemId) }
    }

    fun clearPicked() { _picked.value = emptyMap() }
}

// ─── Price Book Manage ViewModel ──────────────────────────────────────────
@HiltViewModel
class PricebookManageViewModel @Inject constructor(
    private val repo: CrmRepository
) : ViewModel() {

    private val _categories = MutableStateFlow<List<PricebookCategory>>(emptyList())
    val categories = _categories.asStateFlow()

    private val _items = MutableStateFlow<List<PricebookItem>>(emptyList())
    val items = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _msg = MutableStateFlow<String?>(null)
    val message = _msg.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            when (val r = repo.getPricebookCategories()) {
                is Result.Success -> _categories.value = r.data
                is Result.Error   -> _error.value = r.message ?: "Failed to load categories"
            }
            when (val r = repo.getPricebookItems()) {
                is Result.Success -> _items.value = r.data
                is Result.Error   -> _error.value = r.message ?: "Failed to load items"
            }
            _loading.value = false
        }
    }

    fun saveItem(id: String?, data: Map<String, Any?>, onDone: () -> Unit) {
        viewModelScope.launch {
            val r = if (id == null) repo.createPricebookItem(data) else repo.updatePricebookItem(id, data)
            when (r) {
                is Result.Success -> onDone()
                is Result.Error   -> _error.value = r.message
            }
        }
    }

    /** Create an item and return the server-assigned object to the caller (used by the picker). */
    fun createAndReturn(
        data: Map<String, Any?>,
        onSuccess: (PricebookItem) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            when (val r = repo.createPricebookItem(data)) {
                is Result.Success -> onSuccess(r.data)
                is Result.Error   -> onError(r.message ?: "Failed to save item")
            }
        }
    }

    fun deleteItem(id: String, onDone: () -> Unit = {}) {
        viewModelScope.launch { repo.deletePricebookItem(id); onDone() }
    }

    fun uploadPricebookImage(bytes: ByteArray, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "item.jpg", body)
            when (val r = repo.uploadFile(part, "pricebook_item", "pricebook", null)) {
                is Result.Success -> onResult(r.data["url"])
                is Result.Error   -> { _error.value = r.message; onResult(null) }
            }
        }
    }

    fun saveCategory(id: String?, data: Map<String, Any?>, onDone: () -> Unit) {
        viewModelScope.launch {
            val r = if (id == null) repo.createPricebookCategory(data) else repo.updatePricebookCategory(id, data)
            when (r) {
                is Result.Success -> {
                    // Reload only categories so we don't overwrite category-specific _items
                    when (val cats = repo.getPricebookCategories()) {
                        is Result.Success -> _categories.value = cats.data
                        else -> {}
                    }
                    onDone()
                }
                is Result.Error   -> _error.value = r.message
            }
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            repo.deletePricebookCategory(id)
            when (val cats = repo.getPricebookCategories()) {
                is Result.Success -> _categories.value = cats.data
                else -> {}
            }
        }
    }

    /** Load categories + items for a specific category (used by PricebookCategoryItemsScreen). */
    fun loadForCategory(categoryId: String) {
        viewModelScope.launch {
            _loading.value = true
            when (val r = repo.getPricebookCategories()) {
                is Result.Success -> _categories.value = r.data
                else -> {}
            }
            when (val r = repo.getPricebookItems(categoryId)) {
                is Result.Success -> _items.value = r.data
                is Result.Error   -> _error.value = r.message ?: "Failed to load items"
            }
            _loading.value = false
        }
    }

    fun loadCategoryItems(categoryId: String) {
        viewModelScope.launch {
            when (val r = repo.getPricebookItems(categoryId)) {
                is Result.Success -> _items.value = r.data
                is Result.Error   -> _error.value = r.message
            }
        }
    }

    fun refresh() = load()

    fun clearError() { _error.value = null }
    fun clearMsg()   { _msg.value   = null }
}

// ─── SCREEN 1: Category Grid ──────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricebookCategoryScreen(
    filter: String? = null,
    onBack: () -> Unit,
    onCategory: (PricebookCategory) -> Unit,
    onDone: () -> Unit,
    onRequestRestock: (() -> Unit)? = null,
    vm: PricebookPickerViewModel = hiltViewModel()
) {
    val categories by vm.categories.collectAsState()
    val items      by vm.items.collectAsState()
    val loading    by vm.loading.collectAsState()
    val search     by vm.search.collectAsState()
    val picked     by vm.picked.collectAsState()
    val error      by vm.error.collectAsState()

    LaunchedEffect(Unit) { vm.loadCategories() }

    val pickedCount = picked.values.sumOf { it.qty }
    val pickedTotal = picked.values.sumOf { it.total }

    // Load items: search query → filtered results; blank → all items (needed when no categories)
    LaunchedEffect(search) {
        if (search.isNotBlank()) vm.searchAll(filter) else vm.loadItems(null, filter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Price Book", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    if (onRequestRestock != null) {
                        IconButton(onClick = { onRequestRestock.invoke() }) {
                            Icon(Icons.Default.Inventory2, "Request Restock")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (pickedCount > 0) {
                Surface(tonalElevation = 8.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$pickedCount item${if (pickedCount != 1) "s" else ""} · ${formatMoney(pickedTotal)}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(onClick = onDone, shape = RoundedCornerShape(10.dp)) {
                            Text("Done →")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                value = search, onValueChange = { vm.setSearch(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val showItemList = search.isNotBlank() || (categories.isEmpty() && !loading)

            when {
                // Initial load with nothing yet
                loading && categories.isEmpty() && items.isEmpty() -> LoadingView()

                // Top-level error (nothing loaded at all)
                error != null && !loading && categories.isEmpty() && items.isEmpty() ->
                    PricebookErrorView(error!!) { vm.clearError(); vm.loadCategories(); vm.loadItems(null, filter) }

                // Search mode OR no categories — show flat item list
                showItemList -> {
                    when {
                        loading && items.isEmpty() -> LoadingView()
                        error != null && items.isEmpty() ->
                            PricebookErrorView(error!!) { vm.clearError(); vm.loadItems(null, filter) }
                        items.isEmpty() -> EmptyView(
                            if (search.isNotBlank()) "No items match \"$search\"" else "No items in price book",
                            Icons.Default.Inventory2
                        )
                        else -> LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items, key = { it.id }) { item ->
                                val entry = picked[item.id]
                                PricebookItemRow(
                                    item = item,
                                    entry = entry,
                                    onTap = { vm.pick(item) },
                                    onAdd = { vm.pick(item) },
                                    onQtyChange = { qty -> vm.setQty(item.id, qty) }
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }

                // Default: category grid
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(categories.filter { filter == null || it.type == filter || filter == "all" }) { cat ->
                        val countInCat = picked.values.count { it.item.category_id == cat.id }
                        CategoryCard(cat = cat, selectedCount = countInCat, onClick = { onCategory(cat) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    cat: PricebookCategory,
    selectedCount: Int,
    onClick: () -> Unit
) {
    val typeColor = when (cat.type) {
        "labor", "service" -> AppColors.Blue
        "material"         -> AppColors.Green
        "discount"         -> AppColors.Orange
        else               -> AppColors.Slate
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(typeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (cat.type) {
                            "labor"    -> Icons.Default.Build
                            "material" -> Icons.Default.Inventory
                            "discount" -> Icons.Default.LocalOffer
                            else       -> Icons.Default.Category
                        },
                        contentDescription = null,
                        tint = typeColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(cat.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                StatusBadge(cat.type.replaceFirstChar { it.uppercase() }, typeColor, small = true)
                if (cat.item_count > 0) {
                    Text(
                        "${cat.item_count} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selectedCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(AppColors.Blue),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$selectedCount", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

}

// ─── SCREEN 2: Item List ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricebookItemListScreen(
    categoryId: String?,
    categoryName: String = "Items",
    filter: String? = null,
    onBack: () -> Unit,
    onItem: (PricebookItem) -> Unit,
    onDone: () -> Unit,
    vm: PricebookPickerViewModel = hiltViewModel(),
    manageVm: PricebookManageViewModel = hiltViewModel()
) {
    val items   by vm.items.collectAsState()
    val loading by vm.loading.collectAsState()
    val picked  by vm.picked.collectAsState()
    val search  by vm.search.collectAsState()
    val error   by vm.error.collectAsState()
    val perms by androidx.hilt.navigation.compose.hiltViewModel<com.ultimatepro.ui.auth.AuthViewModel>().permissions.collectAsState()

    // null or blank categoryId → load all items (no category filter)
    LaunchedEffect(categoryId, search) {
        vm.loadItems(categoryId?.takeIf { it.isNotBlank() }, filter)
    }

    val pickedCount = picked.values.sumOf { it.qty }
    val pickedTotal = picked.values.sumOf { it.total }

    var showNewItemForm by remember { mutableStateOf(false) }
    var newItemError    by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryName, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        },
        floatingActionButton = {
            // Only shown on the flat "All Items" picker (categoryId == null = opened from estimate)
            if (categoryId == null && com.ultimatepro.domain.model.canPermission(perms, "pricebook", "edit_self")) {
                ExtendedFloatingActionButton(
                    onClick = { showNewItemForm = true; newItemError = null },
                    icon    = { Icon(Icons.Default.Add, null) },
                    text    = { Text("New Item") }
                )
            }
        },
        bottomBar = {
            if (pickedCount > 0) {
                Surface(tonalElevation = 8.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$pickedCount item${if (pickedCount != 1) "s" else ""} · ${formatMoney(pickedTotal)}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(onClick = onDone, shape = RoundedCornerShape(10.dp)) { Text("Done →") }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                value = search, onValueChange = { vm.setSearch(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (loading && items.isEmpty()) {
                LoadingView()
            } else if (error != null && items.isEmpty()) {
                PricebookErrorView(error!!) {
                    vm.clearError()
                    vm.loadItems(categoryId?.takeIf { it.isNotBlank() }, filter)
                }
            } else if (items.isEmpty()) {
                EmptyView(
                    if (categoryId != null) "No items in this category" else "No items found",
                    Icons.Default.Inventory2
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        val entry = picked[item.id]
                        PricebookItemRow(
                            item = item,
                            entry = entry,
                            onTap = { onItem(item) },
                            onAdd = { vm.pick(item) },
                            onQtyChange = { qty -> vm.setQty(item.id, qty) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // "New Item" quick-add dialog — only reachable when categoryId == null (estimate picker flow)
    if (showNewItemForm) {
        ItemFormDialog(
            item                      = null,
            categories                = emptyList(),
            error                     = newItemError,
            onDismiss                 = { showNewItemForm = false; newItemError = null },
            showSaveToPricebookToggle = true,
            onUploadImage             = { bytes, cb -> manageVm.uploadPricebookImage(bytes, cb) },
            onSave                    = { _, data, saveToPricebook ->
                if (saveToPricebook) {
                    // Persist to server, then auto-pick the returned item and return to estimate
                    manageVm.createAndReturn(
                        data      = data,
                        onSuccess = { newItem ->
                            vm.pick(newItem)
                            showNewItemForm = false
                            onDone()
                        },
                        onError   = { msg -> newItemError = msg }
                    )
                } else {
                    // Temporary item for this estimate only — no API call
                    vm.pick(PricebookItem(
                        id          = java.util.UUID.randomUUID().toString(),
                        name        = data["name"] as? String ?: "",
                        sku         = data["sku"] as? String,
                        description = data["description"] as? String,
                        unit_price  = (data["unit_price"] as? Double) ?: 0.0,
                        cost_price  = (data["cost_price"] as? Double) ?: 0.0,
                        item_type   = (data["item_type"] as? String) ?: "service",
                        taxable     = (data["taxable"] as? Boolean) ?: false
                    ))
                    showNewItemForm = false
                    onDone()
                }
            }
        )
    }
}

@Composable
private fun PricebookItemRow(
    item: PricebookItem,
    entry: PickedEntry?,
    onTap: () -> Unit,
    onAdd: () -> Unit,
    onQtyChange: (Int) -> Unit
) {
    Card(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Thumbnail or type icon
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!item.image_url.isNullOrBlank()) {
                    AsyncImage(model = item.image_url, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(
                        if (item.item_type == "material") Icons.Default.Inventory else Icons.Default.Build,
                        null, tint = AppColors.Blue, modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                item.sku?.let {
                    Text("SKU: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatMoney(item.unit_price), color = AppColors.Blue, fontWeight = FontWeight.Bold)
            }
            if (entry != null) {
                // Qty stepper
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { onQtyChange(entry.qty - 1) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
                    }
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(AppColors.Blue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${entry.qty}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    IconButton(onClick = { onQtyChange(entry.qty + 1) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.AddCircle, null, tint = AppColors.Blue)
                }
            }
        }
    }
}

// ─── SCREEN 3: Item Detail / Add to Estimate ─────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricebookItemDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onAdded: () -> Unit,
    vm: PricebookPickerViewModel = hiltViewModel(),
    manageVm: PricebookManageViewModel = hiltViewModel()
) {
    val items    by vm.items.collectAsState()
    val picked   by vm.picked.collectAsState()
    val item     = items.firstOrNull { it.id == itemId }

    var qty        by remember { mutableStateOf(1) }
    var priceText  by remember { mutableStateOf("") }
    var taxable    by remember { mutableStateOf(false) }
    var overriding by remember { mutableStateOf(false) }

    LaunchedEffect(item) {
        item?.let {
            priceText = "%.2f".format(it.unit_price)
            taxable   = it.taxable
        }
    }

    val entry = picked[itemId]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Item", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (item == null) { LoadingView(); return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Item header card
            Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (item.item_type == "material") Icons.Default.Inventory else Icons.Default.Build,
                                null, tint = AppColors.Blue, modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            item.sku?.let { Text("SKU: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item.category_name?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    item.description?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Qty + Price
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Quantity stepper
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Quantity", fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedIconButton(onClick = { if (qty > 1) qty-- }) { Icon(Icons.Default.Remove, null) }
                            Text("$qty", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            OutlinedIconButton(onClick = { qty++ }) { Icon(Icons.Default.Add, null) }
                        }
                    }

                    // Price (editable override)
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Unit Price", fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { overriding = !overriding }) {
                                Text(if (overriding) "Use default" else "Override price", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it; overriding = true },
                            prefix = { Text("$") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = overriding
                        )
                    }

                    // Taxable toggle
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Taxable", fontWeight = FontWeight.SemiBold)
                            Text("Apply tax to this item", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AppSwitch(checked = taxable, onCheckedChange = { taxable = it })
                    }

                    // Line total preview
                    val linePrice = priceText.toDoubleOrNull() ?: item.unit_price
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Line Total", fontWeight = FontWeight.Bold)
                        Text(formatMoney(qty * linePrice), fontWeight = FontWeight.Bold, color = AppColors.Blue)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Add button
            Button(
                onClick = {
                    val price = priceText.toDoubleOrNull() ?: item.unit_price
                    val overridePrice = if (overriding && price != item.unit_price) price else null
                    if (entry != null) {
                        vm.setQty(item.id, qty)
                        if (overridePrice != null) vm.setPrice(item.id, overridePrice)
                    } else {
                        vm.pick(item, qty)
                        if (overridePrice != null) vm.setPrice(item.id, overridePrice)
                    }
                    onAdded()
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AddShoppingCart, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (entry != null) "Update in Estimate" else "Add to Estimate",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─── SCREEN 4: Manage (Owner/Admin) ──────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricebookManageScreen(
    onBack: () -> Unit,
    vm: PricebookManageViewModel = hiltViewModel()
) {
    val items     by vm.items.collectAsState()
    val categories by vm.categories.collectAsState()
    val loading   by vm.loading.collectAsState()
    val error     by vm.error.collectAsState()
    val message   by vm.message.collectAsState()

    var showItemForm by remember { mutableStateOf(false) }
    var editItem     by remember { mutableStateOf<PricebookItem?>(null) }
    var search       by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load() }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(error)   { error?.let   { if (!showItemForm) { snack.showSnackbar(it); vm.clearError() } } }
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); vm.clearMsg()   } }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Price Book", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editItem = null; showItemForm = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Item") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (loading && items.isEmpty()) {
                LoadingView()
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filtered = items.filter {
                        search.isBlank() || it.name.contains(search, true) ||
                            (it.sku?.contains(search, true) == true)
                    }
                    items(filtered, key = { it.id }) { item ->
                        ManageItemRow(
                            item = item,
                            onEdit = { editItem = item; showItemForm = true },
                            onDelete = { vm.deleteItem(item.id) { vm.load() } }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showItemForm) {
        ItemFormDialog(
            item = editItem,
            categories = categories,
            error = error,
            onDismiss = { showItemForm = false; vm.clearError() },
            onUploadImage = { bytes, cb -> vm.uploadPricebookImage(bytes, cb) },
            onSave = { id, data, _ -> vm.saveItem(id, data) { vm.load(); showItemForm = false } }
        )
    }
}

@Composable
private fun ManageItemRow(
    item: PricebookItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!item.image_url.isNullOrBlank()) {
                AsyncImage(model = item.image_url, contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop)
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold)
                item.sku?.let { Text("SKU: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(formatMoney(item.unit_price), color = AppColors.Blue, fontWeight = FontWeight.Bold)
                    StatusBadge(item.item_type.replaceFirstChar { it.uppercase() },
                        if (item.item_type == "material") AppColors.Green else AppColors.Blue, small = true)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = AppColors.Blue) }
            IconButton(onClick = { showConfirm = true }) { Icon(Icons.Default.Delete, null, tint = AppColors.Red) }
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete Item?") },
            text  = { Text("\"${item.name}\" will be deactivated.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Red)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemFormDialog(
    item: PricebookItem?,
    categories: List<PricebookCategory>,
    error: String?,
    onDismiss: () -> Unit,
    showSaveToPricebookToggle: Boolean = false,
    onUploadImage: ((ByteArray, (String?) -> Unit) -> Unit)? = null,
    onSave: (String?, Map<String, Any?>, saveToPricebook: Boolean) -> Unit
) {
    val ctx            = LocalContext.current
    var name           by remember { mutableStateOf(item?.name ?: "") }
    var sku            by remember { mutableStateOf(item?.sku ?: "") }
    var desc           by remember { mutableStateOf(item?.description ?: "") }
    var price          by remember { mutableStateOf(item?.unit_price?.let { "%.2f".format(it) } ?: "") }
    var costPrice      by remember { mutableStateOf(item?.cost_price?.let { "%.2f".format(it) } ?: "") }
    var itemType       by remember { mutableStateOf(item?.item_type ?: "service") }
    var taxable        by remember { mutableStateOf(item?.taxable ?: false) }
    var categoryId     by remember { mutableStateOf(item?.category_id) }
    var catExpanded    by remember { mutableStateOf(false) }
    var addToPricebook by remember { mutableStateOf(true) }
    var imageUrl       by remember { mutableStateOf(item?.image_url) }
    var imageUploading by remember { mutableStateOf(false) }

    val imagePicker = if (onUploadImage != null) {
        rememberLauncherForActivityResult(
            if (Build.VERSION.SDK_INT >= 33)
                ActivityResultContracts.PickVisualMedia()
            else
                ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                val bytes = ctx.contentResolver.openInputStream(uri)?.readBytes()
                if (bytes != null) {
                    imageUploading = true
                    onUploadImage(bytes) { url ->
                        imageUrl = url
                        imageUploading = false
                    }
                }
            }
        }
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "New Item" else "Edit Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Image picker
                if (onUploadImage != null) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .clickable(enabled = !imageUploading) {
                                if (Build.VERSION.SDK_INT >= 33)
                                    (imagePicker as? androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>)
                                        ?.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                else
                                    (imagePicker as? androidx.activity.result.ActivityResultLauncher<String>)
                                        ?.launch("image/*")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (imageUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Add image",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                OutlinedTextField(sku,  { sku  = it }, label = { Text("SKU")    }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(price,     { price     = it }, label = { Text("Price")      }, prefix = { Text("$") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                    OutlinedTextField(costPrice, { costPrice = it }, label = { Text("Cost")       }, prefix = { Text("$") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                }
                // Type chips — 2×2 grid so "Discount" is never cut off
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(listOf("service", "material"), listOf("labor", "discount")).forEach { rowTypes ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowTypes.forEach { t ->
                                FilterChip(
                                    selected = itemType == t,
                                    onClick  = { itemType = t },
                                    label    = { Text(t.replaceFirstChar { it.uppercase() }) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Taxable")
                    AppSwitch(checked = taxable, onCheckedChange = { taxable = it })
                }
                if (showSaveToPricebookToggle) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Add to Price Book")
                            Text("Save for future use", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AppSwitch(checked = addToPricebook, onCheckedChange = { addToPricebook = it })
                    }
                }
                // Category dropdown
                if (categories.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = it }) {
                        OutlinedTextField(
                            value = categories.firstOrNull { it.id == categoryId }?.name ?: "No category",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                            DropdownMenuItem(text = { Text("No category") }, onClick = { categoryId = null; catExpanded = false })
                            categories.forEach { c ->
                                DropdownMenuItem(text = { Text(c.name) }, onClick = { categoryId = c.id; catExpanded = false })
                            }
                        }
                    }
                }
                // Error message inside the dialog, above the buttons
                if (error != null) {
                    Text(
                        text  = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(item?.id, mapOf(
                            "name"        to name,
                            "sku"         to sku.ifBlank { null },
                            "description" to desc.ifBlank { null },
                            "unit_price"  to (price.toDoubleOrNull() ?: 0.0),
                            "cost_price"  to (costPrice.toDoubleOrNull() ?: 0.0),
                            "item_type"   to itemType,
                            "taxable"     to taxable,
                            "category_id" to categoryId,
                            "image_url"   to imageUrl
                        ), addToPricebook)
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PricebookErrorView(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
            Text("Failed to load items", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tap to retry")
            }
        }
    }
}

// ─── SCREEN 5: Pricebook Main (category-first manage screen) ──────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricebookMainScreen(
    onBack: () -> Unit,
    onCategory: (PricebookCategory) -> Unit,
    onImport: () -> Unit = {},
    vm: PricebookManageViewModel = hiltViewModel()
) {
    val categories by vm.categories.collectAsState()
    val loading    by vm.loading.collectAsState()
    val error      by vm.error.collectAsState()
    val snack      = remember { SnackbarHostState() }
    var showAddCat by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Only show snackbar for non-category-form errors
    LaunchedEffect(error) { if (error != null && !showAddCat) { snack.showSnackbar(error!!); vm.clearError() } }

    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) { LaunchedEffect(Unit) { vm.refresh() } }
    LaunchedEffect(loading) { if (!loading && pullState.isRefreshing) pullState.endRefresh() }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Price Book", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = onImport) { Icon(Icons.Default.Upload, "Import") }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).nestedScroll(pullState.nestedScrollConnection)) {
            Column(Modifier.fillMaxSize()) {
                if (loading && categories.isEmpty()) {
                    LoadingView()
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement   = Arrangement.spacedBy(12.dp)
                    ) {
                        items(categories) { cat ->
                            ManageCategoryCard(cat = cat, onClick = { onCategory(cat) })
                        }
                    }
                    OutlinedButton(
                        onClick   = { showAddCat = true },
                        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        shape     = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Category")
                    }
                }
            }
            PullToRefreshContainer(state = pullState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }

    if (showAddCat) {
        CategoryFormSheet(
            category  = null,
            onDismiss = { showAddCat = false; vm.clearError() },
            onSave    = { name, taxable ->
                vm.saveCategory(null, mapOf("name" to name, "taxable" to taxable)) { showAddCat = false; vm.clearError() }
            },
            error = error
        )
    }
}

@Composable
private fun ManageCategoryCard(cat: PricebookCategory, onClick: () -> Unit) {
    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().height(160.dp),
        shape    = RoundedCornerShape(12.dp),
        border   = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                cat.name,
                fontWeight = FontWeight.Bold,
                style      = MaterialTheme.typography.titleSmall,
                maxLines   = 2
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${cat.item_count} ${if (cat.item_count == 1) "item" else "items"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (cat.taxable) {
                Spacer(Modifier.height(4.dp))
                StatusBadge("Taxable", AppColors.Green, small = true)
            }
        }
    }
}

// ─── SCREEN 6: Category Items (manage items within one category) ──────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricebookCategoryItemsScreen(
    categoryId: String,
    onBack: () -> Unit,
    vm: PricebookManageViewModel = hiltViewModel()
) {
    val categories by vm.categories.collectAsState()
    val items      by vm.items.collectAsState()
    val loading    by vm.loading.collectAsState()
    val error      by vm.error.collectAsState()
    val snack      = remember { SnackbarHostState() }

    val category     = categories.firstOrNull { it.id == categoryId }
    var showItemForm  by remember { mutableStateOf(false) }
    var editItem      by remember { mutableStateOf<PricebookItem?>(null) }
    var showEditCat   by remember { mutableStateOf(false) }
    var showDeleteCat by remember { mutableStateOf(false) }
    var search        by remember { mutableStateOf("") }

    LaunchedEffect(categoryId) { vm.loadForCategory(categoryId) }
    LaunchedEffect(error) { error?.let { if (!showItemForm && !showEditCat) { snack.showSnackbar(it); vm.clearError() } } }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(category?.name ?: "Items", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showEditCat = true }) {
                        Icon(Icons.Default.Edit, null)
                    }
                    IconButton(onClick = { showDeleteCat = true }) {
                        Icon(Icons.Default.Delete, null, tint = AppColors.Red)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editItem = null; showItemForm = true },
                icon    = { Icon(Icons.Default.Add, null) },
                text    = { Text("Add Item") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value         = search,
                onValueChange = { search = it },
                placeholder   = { Text("Search items...") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            val filtered = remember(items, search) {
                if (search.isBlank()) items
                else items.filter {
                    it.name.contains(search, ignoreCase = true) ||
                    (it.sku?.contains(search, ignoreCase = true) == true)
                }
            }
            when {
                loading && items.isEmpty() -> LoadingView()
                filtered.isEmpty() -> EmptyView(
                    if (search.isNotBlank()) "No items match \"$search\""
                    else "No items in this category — tap + to add one",
                    Icons.Default.Inventory2
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sorted = filtered.sortedWith(compareBy(nullsLast()) { it.sku })
                    items(sorted, key = { it.id }) { item ->
                        ManageItemRow(
                            item     = item,
                            onEdit   = { editItem = item; showItemForm = true },
                            onDelete = { vm.deleteItem(item.id) { vm.loadCategoryItems(categoryId) } }
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }

    if (showItemForm) {
        ItemFormDialog(
            item       = editItem,
            categories = categories,
            error      = error,
            onDismiss     = { showItemForm = false; vm.clearError() },
            onUploadImage = { bytes, cb -> vm.uploadPricebookImage(bytes, cb) },
            onSave        = { id, data, _ ->
                // New items default to this category; edits use whatever the dropdown shows
                val finalData = if (id == null) data + ("category_id" to categoryId) else data
                vm.saveItem(id, finalData) {
                    showItemForm = false
                    vm.loadCategoryItems(categoryId)
                }
            }
        )
    }

    if (showEditCat && category != null) {
        CategoryFormSheet(
            category  = category,
            onDismiss = { showEditCat = false; vm.clearError() },
            onSave    = { name, taxable ->
                vm.saveCategory(categoryId, mapOf("name" to name, "taxable" to taxable)) {
                    showEditCat = false; vm.clearError()
                }
            },
            error = error
        )
    }

    if (showDeleteCat && category != null) {
        AlertDialog(
            onDismissRequest = { showDeleteCat = false },
            title = { Text("Delete Category?") },
            text  = { Text("\"${category.name}\" and all its items will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteCategory(categoryId); showDeleteCat = false; onBack() },
                    colors  = ButtonDefaults.textButtonColors(contentColor = AppColors.Red)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteCat = false }) { Text("Cancel") } }
        )
    }
}

// ─── Category add/edit bottom sheet ──────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFormSheet(
    category: PricebookCategory?,
    onDismiss: () -> Unit,
    onSave: (name: String, taxable: Boolean) -> Unit,
    error: String? = null
) {
    var name    by remember { mutableStateOf(category?.name ?: "") }
    var taxable by remember { mutableStateOf(category?.taxable ?: false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (category == null) "Add Category" else "Edit Category",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Category Name *") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(10.dp)
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text("Taxable", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Items in this category are taxable by default",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppSwitch(checked = taxable, onCheckedChange = { taxable = it })
            }
            Button(
                onClick  = { if (name.isNotBlank()) onSave(name.trim(), taxable) },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                enabled  = name.isNotBlank()
            ) { Text("Save Category", fontWeight = FontWeight.SemiBold) }
        }
    }
}
