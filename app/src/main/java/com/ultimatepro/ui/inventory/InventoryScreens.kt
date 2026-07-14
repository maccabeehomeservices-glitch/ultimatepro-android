package com.ultimatepro.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultimatepro.domain.model.InventoryItem
import com.ultimatepro.domain.model.PricebookItem
import com.ultimatepro.domain.model.RestockRequest
import com.ultimatepro.domain.model.Truck
import com.ultimatepro.ui.common.AppButton
import com.ultimatepro.ui.common.AppColors
import com.ultimatepro.ui.common.AppSwitch
import com.ultimatepro.ui.common.CRMCard
import com.ultimatepro.ui.common.QtyStepperRow
import com.ultimatepro.ui.common.ShineHairline
import com.ultimatepro.ui.common.StatusBadge
import java.util.Locale
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════
//  MAIN INVENTORY SCREEN
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onBack: () -> Unit,
    onTruckStock: (String) -> Unit,
    onRestockRequests: () -> Unit,
    vm: InventoryViewModel = hiltViewModel()
) {
    val settings by vm.settings.collectAsState()
    val settingsLoading by vm.settingsLoading.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { vm.loadSettings() }
    LaunchedEffect(settings.enabled) {
        if (settings.enabled) {
            vm.loadWarehouse()
            vm.loadTrucks()
        }
    }

    val pullState = rememberPullToRefreshState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    if (pullState.isRefreshing) { LaunchedEffect(Unit) { vm.refresh() } }
    LaunchedEffect(isRefreshing) { if (!isRefreshing) pullState.endRefresh() }

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = { Text("Inventory") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    if (settings.enabled) {
                        IconButton(onClick = onRestockRequests) { Icon(Icons.Default.Assignment, "Restock Requests") }
                    }
                }
            )
            ShineHairline()
            }
        },
        floatingActionButton = {
            if (settings.enabled && selectedTab == 1) {
                FloatingActionButton(onClick = { /* AddTruckDialog triggers from tab */ }) {
                    Icon(Icons.Default.Add, null)
                }
            }
        }
    ) { padding ->
        if (settingsLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (!settings.enabled) {
            InventoryDisabledView(
                modifier = Modifier.padding(padding),
                onEnable = { vm.enableInventory { if (it) { vm.loadWarehouse(); vm.loadTrucks() } } }
            )
            return@Scaffold
        }

        Box(Modifier.fillMaxSize().padding(padding).nestedScroll(pullState.nestedScrollConnection)) {
            Column(Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Warehouse") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Trucks") })
                }
                when (selectedTab) {
                    0 -> WarehouseTab(vm)
                    1 -> TrucksTab(vm, onTruckStock)
                }
            }
            PullToRefreshContainer(state = pullState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun InventoryDisabledView(modifier: Modifier = Modifier, onEnable: () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(32.dp)) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Inventory Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Track parts across your warehouse and trucks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppButton(onClick = onEnable, label = "Enable Inventory", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  WAREHOUSE TAB
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarehouseTab(vm: InventoryViewModel) {
    val items by vm.warehouse.collectAsState()
    val loading by vm.warehouseLoading.collectAsState()
    val materials by vm.pricebookMaterials.collectAsState()
    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<InventoryItem?>(null) }

    LaunchedEffect(showAdd) { if (showAdd) vm.loadPricebookMaterials() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search items…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Add Item") }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val filtered = items.filter { search.isBlank() || it.itemName.contains(search, ignoreCase = true) || it.sku?.contains(search, ignoreCase = true) == true }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Inventory2, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(48.dp))
                        Text("No warehouse items", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (search.isBlank()) TextButton(onClick = { showAdd = true }) { Text("Add first item") }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        WarehouseItemCard(
                            item = item,
                            onEdit = { editItem = item },
                            onSaveQty = { newQty, onSuccess, onError ->
                                vm.updateWarehouseQty(item.id, newQty, onSuccess, onError)
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAdd) {
        AddWarehouseItemDialog(
            pricebookItems = materials,
            onDismiss = { showAdd = false },
            onSave = { pbId, qty, min ->
                vm.upsertWarehouseItem(pbId, qty, min) { showAdd = false }
            }
        )
    }
    editItem?.let { item ->
        EditQtyDialog(
            title = "Edit ${item.itemName}",
            item = item,
            onDismiss = { editItem = null },
            onSave = { min ->
                vm.updateWarehouseItem(item.id, minQty = min) { editItem = null }
            }
        )
    }
}

@Composable
private fun WarehouseItemCard(
    item: InventoryItem,
    onEdit: () -> Unit,
    onSaveQty: (newQty: Int, onSuccess: () -> Unit, onError: () -> Unit) -> Unit
) {
    val isLow = item.isLowStock
    var localQty by remember(item.id, item.qtyOnHand) { mutableIntStateOf(item.qtyOnHand) }
    var isDirty by remember(item.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    CRMCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.itemName, fontWeight = FontWeight.SemiBold)
                item.sku?.let { Text("SKU: $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Min: ${item.minQty}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            QtyStepperRow(
                qty = localQty,
                onDecrement = {
                    localQty = (localQty - 1).coerceAtLeast(0)
                    isDirty = localQty != item.qtyOnHand
                },
                onIncrement = { localQty++; isDirty = true },
                minQty = 0,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            if (isDirty) {
                IconButton(
                    onClick = {
                        saving = true
                        onSaveQty(
                            localQty,
                            { saving = false; isDirty = false },
                            { saving = false; localQty = item.qtyOnHand; isDirty = false
                              saveError = "Failed to update quantity. Try again." }
                        )
                    },
                    enabled = !saving,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Check, "Save", Modifier.size(18.dp), tint = AppColors.Green)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null) }
        }
    }
    saveError?.let { msg ->
        AlertDialog(
            onDismissRequest = { saveError = null },
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { saveError = null }) { Text("OK") } }
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  TRUCKS TAB
// ═══════════════════════════════════════════════════════════

@Composable
private fun TrucksTab(vm: InventoryViewModel, onTruckStock: (String) -> Unit) {
    val trucks by vm.trucks.collectAsState()
    val loading by vm.trucksLoading.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (loading && trucks.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else if (trucks.isEmpty()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocalShipping, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(48.dp))
                Text("No trucks configured", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { showAdd = true }) { Text("Add first truck") }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(trucks, key = { it.id }) { truck ->
                    TruckCard(truck, onViewStock = { onTruckStock(truck.id) }, onDelete = { vm.deleteTruck(truck.id) })
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, "Add Truck") }
    }

    if (showAdd) {
        AddTruckDialog(
            onDismiss = { showAdd = false },
            onSave = { name, assignedId, assignedType, assignedName ->
                vm.createTruck(name, assignedId, assignedType, assignedName) { showAdd = false }
            }
        )
    }
}

@Composable
private fun TruckCard(truck: Truck, onViewStock: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    CRMCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalShipping, null, tint = AppColors.Blue, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(truck.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                truck.assignedToName?.let {
                    Text("Assigned: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${truck.itemCount} items", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (truck.lowStockCount > 0) {
                        Text("${truck.lowStockCount} low", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            TextButton(onClick = onViewStock) { Text("View Stock") }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove Truck") },
            text = { Text("Remove \"${truck.name}\"? Stock records will be kept.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  TRUCK STOCK SCREEN
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TruckStockScreen(
    truckId: String,
    onBack: () -> Unit,
    onRestockRequests: () -> Unit,
    vm: InventoryViewModel = hiltViewModel()
) {
    val trucks by vm.trucks.collectAsState()
    val stock by vm.truckStock.collectAsState()
    val loading by vm.truckStockLoading.collectAsState()
    val warehouse by vm.warehouse.collectAsState()

    val truck = trucks.find { it.id == truckId }
    val lowCount = stock.count { it.isLowStock }

    var showSendItems by remember { mutableStateOf(false) }

    LaunchedEffect(truckId) { vm.loadWarehouse(); vm.loadTruckStock(truckId) }

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = { Text(truck?.name ?: "Truck Stock") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showSendItems = true }) { Icon(Icons.Default.Add, "Add Item") }
                    IconButton(onClick = onRestockRequests) { Icon(Icons.Default.Assignment, "Restock Requests") }
                }
            )
            ShineHairline()
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Low stock banner
            if (lowCount > 0) {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Text("$lowCount item${if (lowCount != 1) "s" else ""} low — consider restocking", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (loading && stock.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (stock.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Inventory2, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(48.dp))
                        Text("No items on this truck", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { showSendItems = true }) { Text("Send items to truck") }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(stock, key = { it.id }) { item ->
                        TruckStockItemCard(
                            item = item,
                            warehouseQtyForItem = warehouse.find { it.pricebookItemId == item.pricebookItemId }?.qtyOnHand ?: 0,
                            onSaveQty = { newQty, prevQty, onSuccess, onError ->
                                vm.updateTruckQty(truckId, item.pricebookItemId, newQty, prevQty, item.minQty, item.isPermanent) {
                                    if (it) onSuccess() else onError()
                                }
                            },
                            onRemove = { _, onSuccess, onError ->
                                vm.deleteTruckStockWithReturn(truckId, item.id) {
                                    if (it) onSuccess() else onError()
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }

            // Bottom action buttons
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppButton(
                    onClick = { showSendItems = true },
                    label = "Send Items",
                    modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Default.MoveToInbox
                )
                AppButton(
                    onClick = onRestockRequests,
                    label = "Requests",
                    modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Default.Assignment
                )
            }
        }
    }

    if (showSendItems) {
        AddTruckStockDialog(
            warehouseItems = warehouse,
            onDismiss = { showSendItems = false },
            onSave = { pbId, qty, min, perm ->
                vm.sendItemsToTruck(
                    truckId,
                    listOf(mapOf("pricebook_item_id" to pbId, "qty" to qty, "min_qty" to min, "is_permanent" to perm))
                ) { showSendItems = false }
            }
        )
    }
}

@Composable
private fun TruckStockItemCard(
    item: InventoryItem,
    warehouseQtyForItem: Int,
    onSaveQty: (newQty: Int, prevQty: Int, onSuccess: () -> Unit, onError: () -> Unit) -> Unit,
    onRemove: (qty: Int, onSuccess: () -> Unit, onError: () -> Unit) -> Unit
) {
    // max = current truck qty + available warehouse qty (can take from warehouse)
    val maxQty = item.qtyOnHand + warehouseQtyForItem
    var localQty by remember(item.id, item.qtyOnHand) { mutableIntStateOf(item.qtyOnHand) }
    var saving by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    // Debounced auto-save: fires 800ms after last change
    LaunchedEffect(localQty) {
        if (localQty == item.qtyOnHand) return@LaunchedEffect
        delay(800)
        val prevQty = item.qtyOnHand
        saving = true
        onSaveQty(
            localQty, prevQty,
            { saving = false },
            { saving = false; localQty = item.qtyOnHand
              saveError = "Failed to update quantity. Try again." }
        )
    }

    CRMCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.itemName, fontWeight = FontWeight.SemiBold)
                    if (item.isPermanent) StatusBadge("Permanent", AppColors.Blue, small = true)
                }
                item.sku?.let { Text("SKU: $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Min: ${item.minQty}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(4.dp))
            }
            QtyStepperRow(
                qty = localQty,
                onDecrement = { if (localQty > 0) localQty-- },
                onIncrement = { if (localQty < maxQty) localQty++ },
                minQty = 0,
                maxQty = maxQty
            )
            TextButton(onClick = { confirmRemove = true }) {
                Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove Item") },
            text = { Text("Remove \"${item.itemName}\" from truck? $localQty unit${if (localQty != 1) "s" else ""} will be returned to warehouse.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove(localQty, {}, { saveError = "Failed to remove item. Try again." })
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
        )
    }
    saveError?.let { msg ->
        AlertDialog(
            onDismissRequest = { saveError = null },
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { saveError = null }) { Text("OK") } }
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  RESTOCK REQUESTS SCREEN
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestockRequestsScreen(
    onBack: () -> Unit,
    onDetail: (String) -> Unit,
    vm: InventoryViewModel = hiltViewModel()
) {
    val requests by vm.restockRequests.collectAsState()
    val loading by vm.restockLoading.collectAsState()
    var filterStatus by remember { mutableStateOf("all") }

    LaunchedEffect(filterStatus) { vm.loadRestockRequests(filterStatus.takeIf { it != "all" }) }

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = { Text("Restock Requests") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
            ShineHairline()
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Filter chips
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all", "pending", "fulfilled").forEach { s ->
                    FilterChip(selected = filterStatus == s, onClick = { filterStatus = s }, label = { Text(s.replaceFirstChar { it.uppercase() }) })
                }
            }
            if (loading && requests.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (requests.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No restock requests", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(requests, key = { it.id }) { request ->
                        RestockRequestCard(request, onClick = { onDetail(request.id) })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RestockRequestCard(request: RestockRequest, onClick: () -> Unit) {
    val statusColor = if (request.status == "pending") AppColors.Orange else AppColors.Green
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(request.truckName ?: "Unknown truck", fontWeight = FontWeight.SemiBold)
                request.requestedByName?.let { Text("By: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                request.createdAt?.take(10)?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("${request.items.size} item${if (request.items.size != 1) "s" else ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusBadge(request.status.replaceFirstChar { it.uppercase() }, statusColor, small = true)
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  RESTOCK REQUEST DETAIL SCREEN
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestockRequestDetailScreen(
    requestId: String,
    onBack: () -> Unit,
    vm: InventoryViewModel = hiltViewModel()
) {
    val requests by vm.restockRequests.collectAsState()
    val request = requests.find { it.id == requestId }

    // Fulfilled quantity state per item
    val fulfilledQtys = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(Unit) { if (requests.isEmpty()) vm.loadRestockRequests() }
    LaunchedEffect(request) {
        request?.items?.forEach { item ->
            fulfilledQtys[item.pricebookItemId] = item.qtyRequested.toString()
        }
    }

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = { Text("Restock Request") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
            ShineHairline()
            }
        }
    ) { padding ->
        if (request == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            CRMCard {
                val statusColor = if (request.status == "pending") AppColors.Orange else AppColors.Green
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Request Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    StatusBadge(request.status.replaceFirstChar { it.uppercase() }, statusColor)
                }
                Spacer(Modifier.height(8.dp))
                request.truckName?.let { Text("Truck: $it", fontWeight = FontWeight.Medium) }
                request.requestedByName?.let { Text("Requested by: $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                request.createdAt?.take(10)?.let { Text("Date: $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                request.notes?.let { Text("Notes: $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (request.status == "fulfilled") {
                    request.fulfilledAt?.take(10)?.let { Text("Fulfilled: $it", fontSize = 13.sp, color = AppColors.Green) }
                }
            }

            CRMCard {
                Text("Items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                request.items.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.itemName, fontWeight = FontWeight.Medium)
                            Text("Requested: ${item.qtyRequested}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (request.status == "pending") {
                            OutlinedTextField(
                                value = fulfilledQtys[item.pricebookItemId] ?: "",
                                onValueChange = { fulfilledQtys[item.pricebookItemId] = it },
                                label = { Text("Fulfill") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(100.dp),
                                singleLine = true
                            )
                        } else {
                            Text("Fulfilled: ${item.qtyFulfilled}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Green)
                        }
                    }
                    HorizontalDivider()
                }
            }

            if (request.status == "pending") {
                AppButton(
                    onClick = {
                        val items = request.items.map { item ->
                            mapOf(
                                "pricebook_item_id" to item.pricebookItemId,
                                "qty_fulfilled" to (fulfilledQtys[item.pricebookItemId]?.toIntOrNull() ?: 0)
                            )
                        }
                        vm.fulfillRestockRequest(requestId, items) { onBack() }
                    },
                    label = "Fulfill Request",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  SHARED DIALOGS
// ═══════════════════════════════════════════════════════════

@Composable
private fun EditQtyDialog(
    title: String,
    item: InventoryItem,
    onDismiss: () -> Unit,
    onSave: (minQty: Int) -> Unit
) {
    var minText by remember { mutableStateOf(item.minQty.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.itemName, fontWeight = FontWeight.SemiBold)
                item.sku?.let { Text("SKU: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it },
                    label = { Text("Min Qty (low stock threshold)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(minText.toIntOrNull() ?: 0) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditStockItemDialog(
    item: InventoryItem,
    onDismiss: () -> Unit,
    onSave: (qtyOnHand: Int, minQty: Int, isPermanent: Boolean) -> Unit
) {
    var qtyText by remember { mutableStateOf(item.qtyOnHand.toString()) }
    var minText by remember { mutableStateOf(item.minQty.toString()) }
    var isPermanent by remember { mutableStateOf(item.isPermanent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.itemName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Qty on Hand") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it },
                    label = { Text("Min Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Permanent item on this truck", Modifier.weight(1f))
                    AppSwitch(checked = isPermanent, onCheckedChange = { isPermanent = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(qtyText.toIntOrNull() ?: item.qtyOnHand, minText.toIntOrNull() ?: item.minQty, isPermanent) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTruckDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, assignedToId: String?, assignedToType: String?, assignedToName: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var assignedName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Truck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Truck Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = assignedName,
                    onValueChange = { assignedName = it },
                    label = { Text("Assigned to (tech name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "To assign to a tech, enter their name. The truck will be linked when the tech's user ID is set from Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                onSave(name.trim(), null, if (assignedName.isNotBlank()) "tech" else null, assignedName.ifBlank { null })
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddWarehouseItemDialog(
    pricebookItems: List<PricebookItem>,
    onDismiss: () -> Unit,
    onSave: (pricebookItemId: String, qtyOnHand: Int, minQty: Int) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<PricebookItem?>(null) }
    var qty by remember(selected?.id) { mutableIntStateOf(0) }
    var minText by remember { mutableStateOf("0") }

    val filtered = remember(search, pricebookItems) {
        if (search.isBlank()) pricebookItems
        else pricebookItems.filter {
            it.name.contains(search, ignoreCase = true) ||
            it.sku?.contains(search, ignoreCase = true) == true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (selected == null) "Add to Warehouse" else selected!!.name) },
        text = {
            if (selected == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search by name or SKU…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.heightIn(max = 260.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (filtered.isEmpty()) {
                                item {
                                    Text(
                                        if (pricebookItems.isEmpty()) "No materials in pricebook yet" else "No matches",
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(filtered, key = { it.id }) { item ->
                                    ListItem(
                                        headlineContent = { Text(item.name, fontWeight = FontWeight.Medium) },
                                        supportingContent = item.sku?.let { sku -> { Text("SKU: $sku", fontSize = 12.sp) } },
                                        modifier = Modifier.clickable { selected = item }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    selected!!.sku?.let {
                        Text("SKU: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Qty on Hand", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        QtyStepperRow(
                            qty = qty,
                            onDecrement = { qty = (qty - 1).coerceAtLeast(0) },
                            onIncrement = { qty++ },
                            minQty = 0
                        )
                    }
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("Min Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (selected != null) {
                TextButton(onClick = {
                    onSave(selected!!.id, qty, minText.toIntOrNull() ?: 0)
                }) { Text("Add to Warehouse") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (selected != null) selected = null else onDismiss() }) {
                Text(if (selected != null) "Back" else "Cancel")
            }
        }
    )
}

@Composable
private fun AddTruckStockDialog(
    warehouseItems: List<InventoryItem>,
    onDismiss: () -> Unit,
    onSave: (pricebookItemId: String, qtyOnHand: Int, minQty: Int, isPermanent: Boolean) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<InventoryItem?>(null) }
    var qty by remember(selected?.id) { mutableIntStateOf(1) }
    var minText by remember { mutableStateOf("0") }
    var isPermanent by remember { mutableStateOf(false) }

    val filtered = remember(search, warehouseItems) {
        if (search.isBlank()) warehouseItems
        else warehouseItems.filter {
            it.itemName.contains(search, ignoreCase = true) ||
            it.sku?.contains(search, ignoreCase = true) == true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (selected == null) "Add to Truck" else selected!!.itemName) },
        text = {
            if (selected == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search warehouse items…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.heightIn(max = 260.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (filtered.isEmpty()) {
                                item {
                                    Text(
                                        if (warehouseItems.isEmpty()) "No items in warehouse yet" else "No matches",
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(filtered, key = { it.id }) { item ->
                                    ListItem(
                                        headlineContent = { Text(item.itemName, fontWeight = FontWeight.Medium) },
                                        supportingContent = item.sku?.let { sku -> { Text("SKU: $sku", fontSize = 12.sp) } },
                                        modifier = Modifier.clickable { selected = item }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    selected!!.sku?.let {
                        Text("SKU: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Available in warehouse: ${selected!!.qtyOnHand}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Qty to Send", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        QtyStepperRow(
                            qty = qty,
                            onDecrement = { qty = (qty - 1).coerceAtLeast(1) },
                            onIncrement = { qty++ },
                            minQty = 1,
                            maxQty = selected!!.qtyOnHand.coerceAtLeast(1)
                        )
                    }
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("Min Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Permanent item on this truck", Modifier.weight(1f))
                        AppSwitch(checked = isPermanent, onCheckedChange = { isPermanent = it })
                    }
                }
            }
        },
        confirmButton = {
            if (selected != null) {
                TextButton(onClick = {
                    onSave(selected!!.pricebookItemId, qty, minText.toIntOrNull() ?: 0, isPermanent)
                }) { Text("Add to Truck") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (selected != null) selected = null else onDismiss() }) {
                Text(if (selected != null) "Back" else "Cancel")
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════
//  REQUEST RESTOCK DIALOG (used from pricebook)
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestRestockDialog(
    truck: Truck,
    truckStock: List<InventoryItem>,
    onDismiss: () -> Unit,
    onSubmit: (truckId: String, notes: String?, items: List<Map<String, Any?>>) -> Unit
) {
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val qtys     = remember { mutableStateMapOf<String, Int>() }
    var notes by remember { mutableStateOf("") }

    // Pre-select all permanent items
    LaunchedEffect(truckStock) {
        truckStock.filter { it.isPermanent }.forEach { item ->
            selected[item.pricebookItemId] = true
            qtys[item.pricebookItemId]     = 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request Restock — ${truck.name}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (truckStock.isEmpty()) {
                    Text("No items configured on this truck.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    truckStock.forEach { item ->
                        val isSelected = selected[item.pricebookItemId] == true
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { selected[item.pricebookItemId] = it }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(item.itemName, fontSize = 13.sp)
                                Text("On hand: ${item.qtyOnHand}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isSelected) {
                                QtyStepperRow(
                                    qty = qtys[item.pricebookItemId] ?: 1,
                                    onDecrement = { qtys[item.pricebookItemId] = ((qtys[item.pricebookItemId] ?: 1) - 1).coerceAtLeast(1) },
                                    onIncrement = { qtys[item.pricebookItemId] = (qtys[item.pricebookItemId] ?: 1) + 1 },
                                    minQty = 1
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    maxLines = 2, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val items = truckStock.filter { selected[it.pricebookItemId] == true }.map { item ->
                    mapOf("pricebook_item_id" to item.pricebookItemId, "item_name" to item.itemName, "qty_requested" to (qtys[item.pricebookItemId] ?: 1))
                }
                if (items.isNotEmpty()) onSubmit(truck.id, notes.ifBlank { null }, items)
            }) { Text("Send Request") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
