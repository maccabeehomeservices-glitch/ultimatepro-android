package com.ultimatepro.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.InventoryItem
import com.ultimatepro.domain.model.InventorySettings
import com.ultimatepro.domain.model.PricebookItem
import com.ultimatepro.domain.model.RestockRequest
import com.ultimatepro.domain.model.Truck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repo: CrmRepository
) : ViewModel() {

    // ── Refresh (pull-to-refresh) ─────────────────────────────────────────────
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadSettings().join()
            if (_settings.value.enabled) {
                loadWarehouse().join()
                loadTrucks().join()
            }
            _isRefreshing.value = false
        }
    }

    // ── Pricebook Materials ───────────────────────────────────────────────────
    private val _pricebookMaterials = MutableStateFlow<List<PricebookItem>>(emptyList())
    val pricebookMaterials: StateFlow<List<PricebookItem>> = _pricebookMaterials

    fun loadPricebookMaterials() = viewModelScope.launch {
        when (val r = repo.getPricebookItems(type = "material")) {
            is Result.Success -> _pricebookMaterials.value = r.data
            is Result.Error   -> { /* keep empty */ }
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────
    private val _settings = MutableStateFlow(InventorySettings())
    val settings: StateFlow<InventorySettings> = _settings

    private val _settingsLoading = MutableStateFlow(false)
    val settingsLoading: StateFlow<Boolean> = _settingsLoading

    fun loadSettings() = viewModelScope.launch {
        _settingsLoading.value = true
        when (val r = repo.getInventorySettings()) {
            is Result.Success -> _settings.value = r.data
            is Result.Error   -> { /* keep default */ }
        }
        _settingsLoading.value = false
    }

    fun enableInventory(onDone: (Boolean) -> Unit) = viewModelScope.launch {
        when (val r = repo.updateInventorySettings(true)) {
            is Result.Success -> { _settings.value = r.data; onDone(true) }
            is Result.Error   -> onDone(false)
        }
    }

    // ── Warehouse ─────────────────────────────────────────────────────────────
    private val _warehouse = MutableStateFlow<List<InventoryItem>>(emptyList())
    val warehouse: StateFlow<List<InventoryItem>> = _warehouse

    private val _warehouseLoading = MutableStateFlow(false)
    val warehouseLoading: StateFlow<Boolean> = _warehouseLoading

    fun loadWarehouse() = viewModelScope.launch {
        _warehouseLoading.value = true
        when (val r = repo.getWarehouseInventory()) {
            is Result.Success -> _warehouse.value = r.data
            is Result.Error   -> { /* no-op */ }
        }
        _warehouseLoading.value = false
    }

    fun upsertWarehouseItem(pricebookItemId: String, qtyOnHand: Int, minQty: Int, onDone: (Boolean) -> Unit) =
        viewModelScope.launch {
            when (repo.upsertWarehouseItem(pricebookItemId, qtyOnHand, minQty)) {
                is Result.Success -> { loadWarehouse().join(); onDone(true) }
                is Result.Error   -> onDone(false)
            }
        }

    fun updateWarehouseItem(itemId: String, qtyOnHand: Int? = null, minQty: Int? = null, onDone: (Boolean) -> Unit = {}) =
        viewModelScope.launch {
            when (repo.updateWarehouseItem(itemId, qtyOnHand, minQty)) {
                is Result.Success -> { loadWarehouse(); onDone(true) }
                is Result.Error   -> onDone(false)
            }
        }

    /** Inline stepper save — only updates qty_on_hand. */
    fun updateWarehouseQty(itemId: String, newQty: Int, onSuccess: () -> Unit, onError: () -> Unit) =
        updateWarehouseItem(itemId, qtyOnHand = newQty) { if (it) onSuccess() else onError() }

    // ── Trucks ────────────────────────────────────────────────────────────────
    private val _trucks = MutableStateFlow<List<Truck>>(emptyList())
    val trucks: StateFlow<List<Truck>> = _trucks

    private val _trucksLoading = MutableStateFlow(false)
    val trucksLoading: StateFlow<Boolean> = _trucksLoading

    fun loadTrucks() = viewModelScope.launch {
        _trucksLoading.value = true
        when (val r = repo.getTrucks()) {
            is Result.Success -> _trucks.value = r.data
            is Result.Error   -> { /* no-op */ }
        }
        _trucksLoading.value = false
    }

    fun createTruck(name: String, assignedToId: String?, assignedToType: String?, assignedToName: String?, onDone: (Boolean) -> Unit) =
        viewModelScope.launch {
            when (repo.createTruck(name, assignedToId, assignedToType, assignedToName)) {
                is Result.Success -> { loadTrucks(); onDone(true) }
                is Result.Error   -> onDone(false)
            }
        }

    fun updateTruck(id: String, body: Map<String, Any?>, onDone: (Boolean) -> Unit = {}) =
        viewModelScope.launch {
            when (repo.updateTruck(id, body)) {
                is Result.Success -> { loadTrucks(); onDone(true) }
                is Result.Error   -> onDone(false)
            }
        }

    fun deleteTruck(id: String) = viewModelScope.launch {
        repo.deleteTruck(id)
        loadTrucks()
    }

    // ── Truck Stock ───────────────────────────────────────────────────────────
    private val _truckStock = MutableStateFlow<List<InventoryItem>>(emptyList())
    val truckStock: StateFlow<List<InventoryItem>> = _truckStock

    private val _truckStockLoading = MutableStateFlow(false)
    val truckStockLoading: StateFlow<Boolean> = _truckStockLoading

    fun loadTruckStock(truckId: String) = viewModelScope.launch {
        _truckStockLoading.value = true
        when (val r = repo.getTruckStock(truckId)) {
            is Result.Success -> _truckStock.value = r.data
            is Result.Error   -> { /* no-op */ }
        }
        _truckStockLoading.value = false
    }

    fun upsertTruckStock(truckId: String, pricebookItemId: String, qtyOnHand: Int, minQty: Int, isPermanent: Boolean, onDone: (Boolean) -> Unit = {}) =
        viewModelScope.launch {
            when (repo.upsertTruckStockItem(truckId, pricebookItemId, qtyOnHand, minQty, isPermanent)) {
                is Result.Success -> { loadTruckStock(truckId); onDone(true) }
                is Result.Error   -> onDone(false)
            }
        }

    fun updateTruckStock(truckId: String, itemId: String, qtyOnHand: Int? = null, minQty: Int? = null, isPermanent: Boolean? = null, onDone: (Boolean) -> Unit = {}) =
        viewModelScope.launch {
            when (repo.updateTruckStockItem(truckId, itemId, qtyOnHand, minQty, isPermanent)) {
                is Result.Success -> { loadTruckStock(truckId); onDone(true) }
                is Result.Error   -> onDone(false)
            }
        }

    fun deleteTruckStock(truckId: String, itemId: String) = viewModelScope.launch {
        repo.deleteTruckStockItem(truckId, itemId)
        loadTruckStock(truckId)
    }

    /** Deletes stock item and returns its qty to warehouse. */
    fun deleteTruckStockWithReturn(truckId: String, itemId: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        when (repo.deleteTruckStockItem(truckId, itemId)) {
            is Result.Success -> { loadTruckStock(truckId); loadWarehouse(); onDone(true) }
            is Result.Error   -> onDone(false)
        }
    }

    /**
     * Adjusts truck qty by diffing newQty vs prevQty.
     * diff > 0 → sends from warehouse to truck (deducts warehouse)
     * diff < 0 → returns from truck to warehouse (adds to warehouse)
     */
    fun updateTruckQty(
        truckId: String,
        pricebookItemId: String,
        newQty: Int,
        prevQty: Int,
        minQty: Int,
        isPermanent: Boolean,
        onDone: (Boolean) -> Unit
    ) = viewModelScope.launch {
        val diff = newQty - prevQty
        if (diff == 0) { onDone(true); return@launch }
        val result = if (diff > 0) {
            repo.sendItemsToTruck(truckId, listOf(mapOf(
                "pricebook_item_id" to pricebookItemId,
                "qty" to diff,
                "min_qty" to minQty,
                "is_permanent" to isPermanent
            )))
        } else {
            repo.returnItemsFromTruck(truckId, listOf(mapOf(
                "pricebook_item_id" to pricebookItemId,
                "qty" to (-diff)
            )))
        }
        when (result) {
            is Result.Success -> { loadTruckStock(truckId); loadWarehouse(); onDone(true) }
            is Result.Error   -> onDone(false)
        }
    }

    fun sendItemsToTruck(truckId: String, items: List<Map<String, Any?>>, onDone: (Boolean) -> Unit) =
        viewModelScope.launch {
            when (repo.sendItemsToTruck(truckId, items)) {
                is Result.Success -> { loadTruckStock(truckId); loadWarehouse(); onDone(true) }
                is Result.Error   -> onDone(false)
            }
        }

    // ── Restock Requests ──────────────────────────────────────────────────────
    private val _restockRequests = MutableStateFlow<List<RestockRequest>>(emptyList())
    val restockRequests: StateFlow<List<RestockRequest>> = _restockRequests

    private val _restockLoading = MutableStateFlow(false)
    val restockLoading: StateFlow<Boolean> = _restockLoading

    fun loadRestockRequests(status: String? = null) = viewModelScope.launch {
        _restockLoading.value = true
        when (val r = repo.getRestockRequests(status)) {
            is Result.Success -> _restockRequests.value = r.data
            is Result.Error   -> { /* no-op */ }
        }
        _restockLoading.value = false
    }

    fun createRestockRequest(truckId: String, notes: String?, items: List<Map<String, Any?>>, onDone: (Boolean) -> Unit) =
        viewModelScope.launch {
            when (repo.createRestockRequest(truckId, notes, items)) {
                is Result.Success -> { loadRestockRequests(); onDone(true) }
                is Result.Error   -> onDone(false)
            }
        }

    fun fulfillRestockRequest(id: String, items: List<Map<String, Any?>>, onDone: (Boolean) -> Unit) =
        viewModelScope.launch {
            when (repo.fulfillRestockRequest(id, items)) {
                is Result.Success -> { loadRestockRequests(); onDone(true) }
                is Result.Error   -> onDone(false)
            }
        }

    // ── Tech Truck ────────────────────────────────────────────────────────────
    private val _techTruck = MutableStateFlow<Truck?>(null)
    val techTruck: StateFlow<Truck?> = _techTruck

    fun loadTechTruck(userId: String) = viewModelScope.launch {
        when (val r = repo.getTechTruck(userId)) {
            is Result.Success -> _techTruck.value = r.data
            is Result.Error   -> { /* no truck */ }
        }
    }

    /** Resolves the current logged-in user's ID then loads their truck. */
    fun loadTechTruckForCurrentUser() = viewModelScope.launch {
        val userId = repo.getCurrentUser()?.id
        if (!userId.isNullOrBlank()) loadTechTruck(userId)
    }

    fun deductJobParts(jobId: String, truckId: String, items: List<Map<String, Any?>>) =
        viewModelScope.launch {
            try { repo.deductJobParts(jobId, truckId, items) } catch (_: Exception) { /* silent */ }
        }
}
