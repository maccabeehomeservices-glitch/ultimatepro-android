package com.ultimatepro.ui.estimates
import android.util.Log

import coil.compose.AsyncImage
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.*
import com.ultimatepro.ui.common.*
import com.ultimatepro.ui.pricebook.PickedEntry
import com.ultimatepro.ui.pricebook.PricebookPickerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

// ─── Editable line item ───────────────────────────────────────────────────
data class EditableLineItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String? = null,
    val sku: String? = null,
    val quantity: Double = 1.0,
    val unit_price: Double = 0.0,
    val item_type: String = "service",
    val taxable: Boolean = false,
    val tax_rate: Double = 0.0,
    val image_url: String? = null,
    val pricebook_id: String? = null,
    val price_overridden: Boolean = false
) {
    val total get() = quantity * unit_price
    fun toApiMap() = mapOf(
        "name" to name, "description" to description, "sku" to sku, "quantity" to quantity,
        "unit_price" to unit_price, "item_type" to item_type, "taxable" to taxable,
        "tax_rate" to tax_rate, "image_url" to image_url,
        "pricebook_id" to pricebook_id, "price_overridden" to price_overridden, "total" to total
    )
}

// GBB tier-count policy (mirrors web Session 3)
private const val MAX_TIERS = 5
private const val MIN_TIERS = 1

// ─── GBB tier (edit-time) ─────────────────────────────────────────────────
data class EditableTier(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val description: String = "",
    val lineItems: List<EditableLineItem> = emptyList()
) {
    val subtotal get() = lineItems.filter { it.item_type != "discount" }.sumOf { it.total }
    val discountTotal get() = lineItems.filter { it.item_type == "discount" }.sumOf { it.quantity * it.unit_price }
    val taxTotal get() = lineItems.filter { it.taxable }.sumOf { it.total * it.tax_rate / 100.0 }
    val total get() = subtotal - discountTotal + taxTotal
    fun toApiMap() = mapOf<String, Any?>(
        "tier_label"  to label,
        "description" to description.ifBlank { null },
        "line_items"  to lineItems.map { it.toApiMap() }
    )
}

// ─── Estimate Build ViewModel ─────────────────────────────────────────────
data class EstimateBuildState(
    val job: Job? = null, val customer: Customer? = null,
    val loading: Boolean = true, val saving: Boolean = false,
    val error: String? = null, val lineItems: List<EditableLineItem> = emptyList(),
    val notes: String = "", val terms: String = "",
    val defaultTaxRate: Double = 0.0, val taxLabel: String = "Sales Tax",
    val depositRequired: Boolean = false,
    val depositType: String = "fixed",
    val depositAmount: Double = 0.0,
    // GBB
    val presentationMode: String = "standard",
    // Default 2 tiers for new GBB estimates (web parity, Session 3).
    // Existing GBB estimates load tiers via getEstimateTiers — this default
    // is only used when toggling into GBB mode for a never-saved estimate.
    val tiers: List<EditableTier> = listOf(
        EditableTier(label = "Tier 1"),
        EditableTier(label = "Tier 2")
    ),
    val selectedGbbTab: Int = 0
)

@HiltViewModel
class EstimateBuildViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {
    private val _s = MutableStateFlow(EstimateBuildState()); val state = _s.asStateFlow()

    fun loadJob(jobId: String) {
        viewModelScope.launch {
            _s.update { it.copy(loading = true) }
            when (val r = repo.getJob(jobId)) {
                is Result.Success -> _s.update { it.copy(job = r.data, loading = false) }
                is Result.Error   -> _s.update { it.copy(error = r.message, loading = false) }
            }
        }
    }

    fun loadCustomer(customerId: String) {
        viewModelScope.launch {
            _s.update { it.copy(loading = true) }
            when (val r = repo.getCustomer(customerId)) {
                is Result.Success -> _s.update { it.copy(customer = r.data, loading = false) }
                is Result.Error   -> _s.update { it.copy(error = r.message, loading = false) }
            }
        }
    }

    private var loadedExistingId: String? = null
    fun loadExisting(estimateId: String) {
        // P2.24 ROOT CAUSE: returning from the pricebook picker RECREATES EstimateBuildScreen,
        // re-firing LaunchedEffect(jobId,customerId) → this loadExisting re-ran and OVERWROTE
        // lineItems, wiping the just-added item (proven via logs: addFromPricebook newList=2,
        // then loadExisting SETTING lineItems=1). Guard it to load exactly ONCE per estimate so
        // re-composition can't clobber user edits. (CREATE has no loadExisting → it never failed.)
        if (loadedExistingId == estimateId) return
        loadedExistingId = estimateId
        viewModelScope.launch {
            when (val r = repo.getEstimate(estimateId)) {
                is Result.Success -> {
                    val est = r.data
                    val toEditable: (com.ultimatepro.domain.model.LineItem) -> EditableLineItem = { li ->
                        EditableLineItem(name = li.name, description = li.description, sku = li.sku,
                            quantity = li.quantity, unit_price = li.unit_price,
                            item_type = li.item_type, taxable = li.taxable,
                            tax_rate = li.tax_rate, image_url = li.image_url,
                            pricebook_id = li.pricebook_id, price_overridden = li.price_overridden)
                    }
                    _s.update { it.copy(
                        lineItems = est.line_items.orEmpty().map(toEditable), // P24LOG below
                        notes = est.notes ?: "", terms = est.terms ?: "",
                        depositRequired = est.deposit_required,
                        depositType = est.deposit_type,
                        depositAmount = est.deposit_amount,
                        presentationMode = est.presentationMode
                    )}
                    est.job_id?.let { loadJob(it) }
                    // Load existing tiers for GBB estimates
                    if (est.presentationMode == "gbb") {
                        val crashlytics = FirebaseCrashlytics.getInstance()
                        crashlytics.log("GBB build-load: estimate=$estimateId presentation_mode=gbb")
                        try {
                            when (val tr = repo.getEstimateTiers(estimateId)) {
                                is Result.Success -> {
                                    val editableTiers = tr.data.map { t ->
                                        EditableTier(id = t.id, label = t.tierLabel,
                                            description = t.description ?: "",
                                            lineItems = t.lineItems.map(toEditable))
                                    }
                                    crashlytics.log("GBB build-load OK: estimate=$estimateId tiers_count=${tr.data.size}")
                                    if (editableTiers.isNotEmpty()) {
                                        _s.update { it.copy(tiers = editableTiers) }
                                    }
                                }
                                is Result.Error -> {
                                    crashlytics.log("GBB build-load API ERROR: estimate=$estimateId msg=${tr.message}")
                                    crashlytics.recordException(IllegalStateException("getEstimateTiers failed: ${tr.message}"))
                                }
                            }
                        } catch (e: Exception) {
                            crashlytics.log("GBB build-load THREW: estimate=$estimateId")
                            crashlytics.recordException(e)
                            throw e
                        }
                    }
                }
                else -> {}
            }
        }
    }

    fun addFromPricebook(entries: Collection<PickedEntry>) {
        val list = _s.value.lineItems.toMutableList()
        entries.forEach { e ->
            val idx = list.indexOfFirst { it.pricebook_id == e.item.id }
            val li = EditableLineItem(name = e.item.name, description = e.item.description, sku = e.item.sku,
                quantity = e.qty.toDouble(), unit_price = e.effectivePrice,
                item_type = e.item.item_type, taxable = e.item.taxable,
                tax_rate = if (e.item.taxable) _s.value.defaultTaxRate else 0.0,
                image_url = e.item.image_url, pricebook_id = e.item.id,
                price_overridden = e.priceOverridden)
            if (idx >= 0) list[idx] = li else list.add(li)
        }
        _s.update { it.copy(lineItems = list) }
    }

    fun addLineItem(li: EditableLineItem) { _s.update { it.copy(lineItems = it.lineItems + li) } }
    fun removeLineItem(id: String) { _s.update { it.copy(lineItems = it.lineItems.filter { l -> l.id != id }) } }
    fun updateLineItem(li: EditableLineItem) { _s.update { it.copy(lineItems = it.lineItems.map { l -> if (l.id == li.id) li else l }) } }
    fun setNotes(v: String) { _s.update { it.copy(notes = v) } }
    fun setTerms(v: String) { _s.update { it.copy(terms = v) } }
    fun clearError() { _s.update { it.copy(error = null) } }
    fun setDepositRequired(v: Boolean) { _s.update { it.copy(depositRequired = v) } }
    fun setDepositType(v: String) { _s.update { it.copy(depositType = v) } }
    fun setDepositAmount(v: Double) { _s.update { it.copy(depositAmount = v) } }

    // GBB
    fun setPresentationMode(mode: String) { _s.update { it.copy(presentationMode = mode) } }
    fun setGbbTab(tab: Int) { _s.update { it.copy(selectedGbbTab = tab) } }
    fun updateTierLabel(idx: Int, label: String) { _s.update { st -> st.copy(tiers = st.tiers.mapIndexed { i, t -> if (i == idx) t.copy(label = label) else t }) } }
    fun updateTierDescription(idx: Int, desc: String) { _s.update { st -> st.copy(tiers = st.tiers.mapIndexed { i, t -> if (i == idx) t.copy(description = desc) else t }) } }
    fun addTierLineItem(tierIdx: Int, li: EditableLineItem) { _s.update { st -> st.copy(tiers = st.tiers.mapIndexed { i, t -> if (i == tierIdx) t.copy(lineItems = t.lineItems + li) else t }) } }
    fun removeTierLineItem(tierIdx: Int, itemId: String) { _s.update { st -> st.copy(tiers = st.tiers.mapIndexed { i, t -> if (i == tierIdx) t.copy(lineItems = t.lineItems.filter { l -> l.id != itemId }) else t }) } }
    fun updateTierLineItem(tierIdx: Int, li: EditableLineItem) { _s.update { st -> st.copy(tiers = st.tiers.mapIndexed { i, t -> if (i == tierIdx) t.copy(lineItems = t.lineItems.map { l -> if (l.id == li.id) li else l }) else t }) } }
    fun addTier() {
        _s.update { st ->
            if (st.tiers.size >= MAX_TIERS) return@update st
            val n = st.tiers.size + 1
            st.copy(
                tiers = st.tiers + EditableTier(label = "Tier $n"),
                selectedGbbTab = st.tiers.size  // jump to new tier (which becomes index = old size)
            )
        }
    }

    fun removeTier(idx: Int) {
        _s.update { st ->
            if (st.tiers.size <= MIN_TIERS) return@update st
            val next = st.tiers.filterIndexed { i, _ -> i != idx }
            val newSelected = st.selectedGbbTab.coerceAtMost(next.size - 1).coerceAtLeast(0)
            st.copy(tiers = next, selectedGbbTab = newSelected)
        }
    }

    fun duplicateTier(idx: Int) {
        _s.update { st ->
            if (st.tiers.size >= MAX_TIERS) return@update st
            val src = st.tiers.getOrNull(idx) ?: return@update st
            // Fresh ids on tier + every line item so removeTierLineItem keys
            // don't collide between source and copy.
            val copy = src.copy(
                id = UUID.randomUUID().toString(),
                label = "${src.label} (Copy)",
                lineItems = src.lineItems.map { it.copy(id = UUID.randomUUID().toString()) }
            )
            st.copy(tiers = st.tiers + copy, selectedGbbTab = st.tiers.size)
        }
    }

    fun addFromPricebookToTier(tierIdx: Int, entries: Collection<PickedEntry>) {
        _s.update { st ->
            st.copy(tiers = st.tiers.mapIndexed { i, t ->
                if (i != tierIdx) t else {
                    val list = t.lineItems.toMutableList()
                    entries.forEach { e ->
                        val idx2 = list.indexOfFirst { it.pricebook_id == e.item.id }
                        val li = EditableLineItem(name = e.item.name, description = e.item.description, sku = e.item.sku,
                            quantity = e.qty.toDouble(), unit_price = e.effectivePrice,
                            item_type = e.item.item_type, taxable = e.item.taxable,
                            tax_rate = if (e.item.taxable) st.defaultTaxRate else 0.0,
                            image_url = e.item.image_url, pricebook_id = e.item.id,
                            price_overridden = e.priceOverridden)
                        if (idx2 >= 0) list[idx2] = li else list.add(li)
                    }
                    t.copy(lineItems = list)
                }
            })
        }
    }

    val services      get() = _s.value.lineItems.filter { it.item_type in listOf("service","labor") }
    val materials     get() = _s.value.lineItems.filter { it.item_type == "material" }
    val discounts     get() = _s.value.lineItems.filter { it.item_type == "discount" }
    val subtotal      get() = _s.value.lineItems.filter { it.item_type != "discount" }.sumOf { it.total }
    val discountTotal get() = _s.value.lineItems.filter { it.item_type == "discount" }.sumOf { it.quantity * it.unit_price }
    val taxTotal      get() = _s.value.lineItems.filter { it.taxable }.sumOf { it.total * it.tax_rate / 100.0 }
    val grandTotal    get() = subtotal - discountTotal + taxTotal

    fun save(jobId: String, estimateId: String? = null, onDone: (String) -> Unit) {
        val st = _s.value
        val customerId = st.customer?.id ?: st.job?.customer_id ?: return
        viewModelScope.launch {
            _s.update { it.copy(saving = true, error = null) }
            // For GBB, use first tier's items (or empty) as the base estimate line_items
            val baseItems = if (st.presentationMode == "gbb") {
                st.tiers.firstOrNull()?.lineItems?.map { it.toApiMap() } ?: emptyList<Map<String, Any?>>()
            } else {
                st.lineItems.map { it.toApiMap() }
            }
            val data = buildMap {
                put("customer_id", customerId)
                if (jobId.isNotBlank()) put("job_id", jobId)
                put("line_items", baseItems)
                put("notes", st.notes.ifBlank { null })
                put("terms", st.terms.ifBlank { null })
            }
            val r = if (estimateId == null) repo.createEstimate(data) else repo.updateEstimate(estimateId, data)
            when (r) {
                is Result.Success -> {
                    val newId = r.data.id
                    repo.updateDepositSettings(newId, st.depositRequired, st.depositAmount, st.depositType)
                    // Save tiers if GBB mode
                    if (st.presentationMode == "gbb") {
                        repo.saveEstimateTiers(newId, st.tiers.map { it.toApiMap() })
                    }
                    _s.update { it.copy(saving = false) }
                    onDone(newId)
                }
                is Result.Error   -> _s.update { it.copy(saving = false, error = r.message) }
            }
        }
    }
}

// ─── Estimate List/Detail ViewModel ──────────────────────────────────────
@HiltViewModel
class EstimateViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {
    private val _ests     = MutableStateFlow<List<Estimate>>(emptyList()); val estimates   = _ests.asStateFlow()
    private val _sel      = MutableStateFlow<Estimate?>(null);             val selected    = _sel.asStateFlow()
    private val _customer = MutableStateFlow<Customer?>(null);             val customer    = _customer.asStateFlow()
    private val _l        = MutableStateFlow(true);                        val loading     = _l.asStateFlow()
    private val _msg      = MutableStateFlow<String?>(null);               val message     = _msg.asStateFlow()
    // P2.38: estimate-deposit ScanPay QR/link + status poll.
    private val _depQr     = MutableStateFlow<ScanPayQrResponse?>(null);     val depositQr     = _depQr.asStateFlow()
    private val _depLink   = MutableStateFlow<ScanPayLinkResponse?>(null);   val depositLink   = _depLink.asStateFlow()
    private val _depStatus = MutableStateFlow<DepositStatusResponse?>(null); val depositStatus = _depStatus.asStateFlow()
    private val _depLoading = MutableStateFlow(false);                       val depositLoading = _depLoading.asStateFlow()
    init { load() }
    fun load() { viewModelScope.launch { _l.value = true; when (val r = repo.getEstimates()) { is Result.Success -> _ests.value = r.data.estimates; else -> {} }; _l.value = false } }
    fun loadEst(id: String) {
        viewModelScope.launch {
            when (val r = repo.getEstimate(id)) {
                is Result.Success -> {
                    var est = r.data
                    loadCustomer(est.customer_id)
                    // Fetch tiers for GBB estimates. The tap-crash on tablet
                    // most likely originates from this branch (GBB-only data
                    // path), so we log to Crashlytics on every entry/exit and
                    // record any thrown exception with full stack trace.
                    if (est.presentationMode == "gbb") {
                        val crashlytics = FirebaseCrashlytics.getInstance()
                        crashlytics.setCustomKey("last_estimate_id", id)
                        crashlytics.log("GBB detail-load: estimate=$id estimate_number=${est.estimate_number}")
                        try {
                            when (val tr = repo.getEstimateTiers(id)) {
                                is Result.Success -> {
                                    est = est.copy(tiers = tr.data)
                                    crashlytics.log("GBB detail-load OK: estimate=$id tiers_count=${tr.data.size} first_label=${tr.data.firstOrNull()?.tierLabel}")
                                }
                                is Result.Error -> {
                                    crashlytics.log("GBB detail-load API ERROR: estimate=$id msg=${tr.message}")
                                    crashlytics.recordException(IllegalStateException("getEstimateTiers failed: ${tr.message}"))
                                }
                            }
                        } catch (e: Exception) {
                            crashlytics.log("GBB detail-load THREW: estimate=$id")
                            crashlytics.recordException(e)
                            throw e
                        }
                    }
                    _sel.value = est
                }
                else -> {}
            }
        }
    }
    fun loadCustomer(customerId: String) { viewModelScope.launch { when (val r = repo.getCustomer(customerId)) { is Result.Success -> _customer.value = r.data; else -> {} } } }
    fun send(id: String, sms: Boolean = true, email: Boolean = true, emails: List<String> = emptyList(), phones: List<String> = emptyList()) {
        android.util.Log.d("SendEstimate", "VM.send emails=$emails phones=$phones")
        viewModelScope.launch {
            when (val r = repo.sendEstimate(id, sms, email, emails, phones)) {
                is Result.Success -> {
                    val sentTo = (r.data["sent_to"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val errors = (r.data["errors"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    _msg.value = when {
                        sentTo.isEmpty() -> errors.firstOrNull() ?: "Failed to send"
                        errors.isNotEmpty() -> when {
                            sentTo.any { it.startsWith("Email", ignoreCase = true) } &&
                            sentTo.none { it.startsWith("SMS",   ignoreCase = true) } -> "Sent via email. SMS unavailable."
                            sentTo.any { it.startsWith("SMS",   ignoreCase = true) } &&
                            sentTo.none { it.startsWith("Email", ignoreCase = true) } -> "Sent via SMS. Email unavailable."
                            else -> "Estimate sent! (some channels unavailable)"
                        }
                        else -> "Estimate sent!"
                    }
                }
                is Result.Error -> _msg.value = r.message
            }
        }
    }
    fun sign(id: String, sig: String, signer: String? = null, onDone: (jobId: String?, needsDeposit: Boolean) -> Unit) {
        viewModelScope.launch {
            when (val r = repo.signEstimate(id, sig, signer)) {
                is Result.Success -> {
                    val estMap = r.data["estimate"] as? Map<*, *>
                    val jobId = estMap?.get("job_id") as? String
                    val depositRequired = (estMap?.get("deposit_required") as? Boolean) ?: false
                    val depositCollected = (estMap?.get("deposit_collected") as? Boolean) ?: false
                    loadEst(id)
                    onDone(jobId, depositRequired && !depositCollected)
                }
                is Result.Error -> _msg.value = r.message
            }
        }
    }
    fun convert(id: String, onDone: (String) -> Unit) { viewModelScope.launch { when (val r = repo.convertEstimateToInvoice(id)) { is Result.Success -> { val inv = (r.data["invoice"] as? Map<*,*>)?.get("id") as? String ?: ""; _msg.value = "Converted!"; onDone(inv) }; is Result.Error -> _msg.value = r.message } } }
    fun fetchJobInvoice(jobId: String, onResult: (existingItems: List<LineItem>) -> Unit) {
        viewModelScope.launch {
            when (val r = repo.getInvoices(jobId = jobId)) {
                is Result.Success -> onResult(r.data.invoices.firstOrNull()?.line_items ?: emptyList())
                is Result.Error   -> onResult(emptyList())
            }
        }
    }
    fun appendItemsToInvoice(invoiceId: String, extraItems: List<LineItem>, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = repo.getInvoice(invoiceId)) {
                is Result.Success -> {
                    val merged = r.data.line_items + extraItems
                    repo.updateInvoice(invoiceId, mapOf("line_items" to merged))
                    onDone()
                }
                is Result.Error -> onDone()
            }
        }
    }
    fun saveDepositSettings(estimateId: String, depositRequired: Boolean, depositAmount: Double, depositType: String, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = repo.updateDepositSettings(estimateId, depositRequired, depositAmount, depositType)) {
                is Result.Success -> { _sel.value = r.data; onDone() }
                is Result.Error   -> _msg.value = r.message
            }
        }
    }
    fun collectDeposit(estimateId: String, amount: Double, method: String, onDone: (remaining: Double) -> Unit) {
        viewModelScope.launch {
            when (val r = repo.collectDeposit(estimateId, amount, method)) {
                is Result.Success -> {
                    val remaining = (r.data["remaining_balance"] as? Number)?.toDouble() ?: 0.0
                    loadEst(estimateId)
                    onDone(remaining)
                }
                is Result.Error -> _msg.value = r.message
            }
        }
    }
    // P2.38: estimate deposit via ScanPay.
    fun createDepositScanPayQr(estimateId: String) {
        viewModelScope.launch {
            _depLoading.value = true
            when (val r = repo.createDepositScanPayQr(estimateId)) {
                is Result.Success -> _depQr.value = r.data
                is Result.Error   -> _msg.value = r.message
            }
            _depLoading.value = false
        }
    }
    fun createDepositScanPayLink(estimateId: String, method: String = "sms") {
        viewModelScope.launch {
            _depLoading.value = true
            when (val r = repo.createDepositScanPayLink(estimateId, method)) {
                is Result.Success -> _depLink.value = r.data
                is Result.Error   -> _msg.value = r.message
            }
            _depLoading.value = false
        }
    }
    fun pollDepositStatus(estimateId: String) {
        viewModelScope.launch {
            when (val r = repo.getDepositStatus(estimateId)) {
                is Result.Success -> _depStatus.value = r.data
                is Result.Error   -> {}
            }
        }
    }
    fun clearDepositScanPay() { _depQr.value = null; _depLink.value = null; _depStatus.value = null }
    fun selectTierAndSign(estimateId: String, tierId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = repo.selectEstimateTier(estimateId, tierId)) {
                is Result.Success -> { loadEst(estimateId); onDone() }
                is Result.Error   -> _msg.value = r.message
            }
        }
    }

    fun addContact(customerId: String, type: String, value: String, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = repo.addCustomerContact(customerId, type, value)) {
                is Result.Success -> { loadCustomer(customerId); onDone() }
                is Result.Error   -> _msg.value = r.message
            }
        }
    }
    fun deleteEstimate(estimateId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = repo.deleteEstimate(estimateId)) {
                is Result.Success -> { _msg.value = "Estimate deleted"; onDone() }
                is Result.Error   -> _msg.value = r.message
            }
        }
    }
    fun clearMsg() { _msg.value = null }
}

// ─── Customer Picker ─────────────────────────────────────────────────────────
@HiltViewModel
class CustomerPickerViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {
    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers = _customers.asStateFlow()
    private val _creating = MutableStateFlow(false)
    val creating = _creating.asStateFlow()

    fun search(query: String) {
        viewModelScope.launch {
            val r = repo.getCustomers(search = query.ifBlank { null })
            if (r is Result.Success) _customers.value = r.data.customers
        }
    }

    fun createCustomer(firstName: String, lastName: String, phone: String, email: String, onDone: (Customer) -> Unit) {
        viewModelScope.launch {
            _creating.value = true
            val data = buildMap<String, Any?> {
                put("first_name", firstName)
                if (lastName.isNotBlank()) put("last_name", lastName)
                if (phone.isNotBlank()) put("phone", phone)
                if (email.isNotBlank()) put("email", email)
            }
            when (val r = repo.createCustomer(data)) {
                is Result.Success -> { _creating.value = false; onDone(r.data) }
                is Result.Error   -> { _creating.value = false }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerPickerSheet(
    onPick: (Customer) -> Unit,
    onDismiss: () -> Unit,
    vm: CustomerPickerViewModel = hiltViewModel()
) {
    val customers by vm.customers.collectAsState()
    val creating  by vm.creating.collectAsState()
    var query        by remember { mutableStateOf("") }
    var showNewForm  by remember { mutableStateOf(false) }
    var newFirst     by remember { mutableStateOf("") }
    var newLast      by remember { mutableStateOf("") }
    var newPhone     by remember { mutableStateOf("") }
    var newEmail     by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.search("") }
    LaunchedEffect(query) { vm.search(query) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHandle()
        Text("Select Customer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        SearchField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Spacer(Modifier.height(4.dp))
        // New customer option
        ListItem(
            headlineContent = { Text("+ New Customer", fontWeight = FontWeight.SemiBold, color = AppColors.Blue) },
            leadingContent = { Icon(Icons.Default.PersonAdd, null, tint = AppColors.Blue) },
            modifier = Modifier.clickable { showNewForm = !showNewForm }
        )
        if (showNewForm) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newFirst, { newFirst = it }, label = { Text("First Name*") },
                        singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                    OutlinedTextField(newLast, { newLast = it }, label = { Text("Last Name") },
                        singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                }
                OutlinedTextField(newPhone, { newPhone = it }, label = { Text("Phone") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(newEmail, { newEmail = it }, label = { Text("Email") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                AppButton(
                    onClick = {
                        vm.createCustomer(newFirst, newLast, newPhone, newEmail) { cust -> onPick(cust) }
                    },
                    label = "Create & Select",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = newFirst.isNotBlank() && !creating,
                    loading = creating
                )
            }
        }
        HorizontalDivider()
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
            items(customers, key = { it.id }) { cust ->
                ListItem(
                    headlineContent = { Text(cust.fullName, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { cust.phone?.let { Text(it) } },
                    leadingContent = {
                        Box(Modifier.size(36.dp).background(AppColors.Blue.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center) {
                            Text(cust.initials, color = AppColors.Blue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    modifier = Modifier.clickable { onPick(cust) }
                )
                HorizontalDivider()
            }
        }
    }
}

// ─── SCREEN: Estimate List ────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateListScreen(
    onEstimate: (String) -> Unit,
    onBack: () -> Unit,
    onNewEstimate: (String) -> Unit = {},
    vm: EstimateViewModel = hiltViewModel()
) {
    val estimates by vm.estimates.collectAsState(); val loading by vm.loading.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Estimates", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) },
        floatingActionButton = {
            AppButton(
                onClick = { showPicker = true },
                label = "New Estimate",
                leadingIcon = Icons.Default.Add
            )
        }
    ) { padding ->
        when {
            loading && estimates.isEmpty() -> LoadingView()
            estimates.isEmpty() -> EmptyView("No estimates yet", Icons.Default.Description)
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(estimates, key = { it.id }) { est ->
                    EstimateRow(est) { onEstimate(est.id) }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    if (showPicker) {
        CustomerPickerSheet(
            onPick    = { cust -> showPicker = false; onNewEstimate(cust.id) },
            onDismiss = { showPicker = false }
        )
    }
}

// ─── Reusable row (list screen + Customer Detail Estimates tab) ───────────
@Composable
fun EstimateRow(est: Estimate, onClick: () -> Unit) {
    val sc = when (est.status) { "approved" -> AppColors.Green; "sent" -> AppColors.Blue; "declined" -> AppColors.Red; else -> AppColors.Slate }
    Card(onClick = onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(est.estimate_number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(est.customerName, fontWeight = FontWeight.SemiBold)
                est.valid_until?.let { Text("Valid: $it", style = MaterialTheme.typography.bodySmall) }
                if (est.isSigned) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Draw, null, tint = AppColors.Green, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("Signed", style = MaterialTheme.typography.labelSmall, color = AppColors.Green) }
            }
            Column(horizontalAlignment = Alignment.End) { Text(formatMoney(est.total), fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); StatusBadge(est.status.replaceFirstChar { it.uppercase() }, sc, small = true) }
        }
    }
}

// ─── SCREEN: Estimate Detail ──────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateDetailScreen(
    id: String, onBack: () -> Unit,
    onSign: (String) -> Unit = {}, onSend: (String) -> Unit = {},
    onPresent: (String) -> Unit = {},
    onConvertToInvoice: (String) -> Unit = {}, onEdit: (String) -> Unit = {},
    onCollectDeposit: (String) -> Unit = {},
    vm: EstimateViewModel = hiltViewModel()
) {
    val est by vm.selected.collectAsState(); val msg by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    var showEditConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var converting by remember { mutableStateOf(false) }
    var showApprovedBanner by remember { mutableStateOf(false) }
    var prevStatus by remember { mutableStateOf<String?>(null) }
    var showDepositDialog by remember { mutableStateOf(false) }
    LaunchedEffect(id) { vm.loadEst(id) }
    // Reload when returning from sign/edit screens so signature/changes appear immediately
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, id) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.loadEst(id)
        }
    }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.clearMsg(); converting = false } }
    // Poll every 10 s while waiting for remote signature; detect approved transition
    LaunchedEffect(est?.status) {
        val status = est?.status
        if (status == "approved" && (prevStatus == "sent" || prevStatus == "pending")) {
            showApprovedBanner = true
        }
        prevStatus = status
        if (status == "sent" || status == "pending") {
            while (true) {
                delay(10_000L)
                vm.loadEst(id)
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
        TopAppBar(title = { Text(est?.estimate_number ?: "Estimate", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = {
                est?.let { e ->
                    IconButton(onClick = { if (e.isSigned) showEditConfirm = true else onEdit(e.id) }) {
                        Icon(Icons.Default.Edit, null)
                    }
                    if (!e.isSigned) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AppColors.Red)
                        }
                    }
                }
            })
    }) { padding ->
        est?.let { e ->
            // Decode signature bitmap once
            val sigBitmap = remember(e.customer_signature) {
                e.customer_signature?.let { sig ->
                    try {
                        val b64 = if (sig.contains(",")) sig.substringAfter(",") else sig
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (_: Exception) { null }
                }
            }

            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (showApprovedBanner) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(AppColors.Green.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .border(1.dp, AppColors.Green, RoundedCornerShape(10.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = AppColors.Green, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Customer signed remotely!", color = AppColors.Green, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                item {
                    CRMCard {
                        InfoRow(Icons.Default.Person, "Customer", e.customerName)
                        e.cust_email?.let { InfoRow(Icons.Default.Email, "Email", it) }
                        e.cust_phone?.let { InfoRow(Icons.Default.Phone, "Phone", it) }
                        InfoRow(Icons.Default.Info, "Status", e.status.replaceFirstChar { it.uppercase() })
                        e.valid_until?.let { InfoRow(Icons.Default.CalendarToday, "Valid Until", it) }
                    }
                }
                // Deposit status card
                item {
                    val depositDollarAmt = if (e.deposit_type == "percentage")
                        e.total * e.deposit_amount / 100.0 else e.deposit_amount
                    CRMCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("DEPOSIT")
                            if (!e.isSigned && e.status in listOf("draft", "sent")) {
                                TextButton(onClick = { showDepositDialog = true }) { Text("Edit") }
                            }
                        }
                        when {
                            e.deposit_collected -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = AppColors.Green.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, tint = AppColors.Green, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("Deposit Paid: ${formatMoney(depositDollarAmt)} ✓", color = AppColors.Green, fontWeight = FontWeight.SemiBold)
                                            e.deposit_collected_at?.let { Text("Collected on ${it.take(10)}", style = MaterialTheme.typography.labelSmall, color = AppColors.Green) }
                                        }
                                    }
                                }
                            }
                            e.deposit_required -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = AppColors.Orange.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, null, tint = AppColors.Orange, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Deposit Required: ${formatMoney(depositDollarAmt)}", color = AppColors.Orange, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            else -> Text("No deposit required", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Signature display
                if (e.isSigned) {
                    item {
                        CRMCard {
                            SectionLabel("CUSTOMER SIGNATURE")
                            sigBitmap?.let { bm ->
                                Image(
                                    bitmap = bm.asImageBitmap(),
                                    contentDescription = "Customer Signature",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().height(120.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(androidx.compose.ui.graphics.Color.White)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = AppColors.Green, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Signed on ${e.customer_signature_date?.take(10) ?: ""}",
                                    style = MaterialTheme.typography.bodySmall, color = AppColors.Green,
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                if (e.isGbb && !e.tiers.isNullOrEmpty()) {
                    // GBB: show tier cards
                    item {
                        CRMCard {
                            SectionLabel("PRICING OPTIONS")
                            Spacer(Modifier.height(4.dp))
                            Text("Customer selects one of the options below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(e.tiers.orEmpty(), key = { it.id }) { tier ->
                        val isSelected = tier.id == e.selectedTierId
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, AppColors.Blue) else null,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    AppColors.Blue.copy(alpha = 0.08f)
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(tier.tierLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    if (isSelected) {
                                        Surface(shape = RoundedCornerShape(4.dp), color = AppColors.Blue) {
                                            Text("Selected", color = androidx.compose.ui.graphics.Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                tier.description?.takeIf { it.isNotBlank() }?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(8.dp))
                                val tierSvcs = tier.lineItems.filter { it.item_type in listOf("service","labor") }
                                val tierMats = tier.lineItems.filter { it.item_type == "material" }
                                val tierDisc = tier.lineItems.filter { it.item_type == "discount" }
                                if (tierSvcs.isNotEmpty()) {
                                    Text("Labor", style = MaterialTheme.typography.labelSmall, color = AppColors.Blue, fontWeight = FontWeight.Bold)
                                    tierSvcs.forEach { li -> TierDetailItemRow(li) }
                                }
                                if (tierMats.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Materials", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    tierMats.forEach { li -> TierDetailItemRow(li) }
                                }
                                if (tierDisc.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Discounts", style = MaterialTheme.typography.labelSmall, color = AppColors.Orange, fontWeight = FontWeight.Bold)
                                    tierDisc.forEach { li -> TierDetailItemRow(li, isDiscount = true) }
                                }
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatMoney(tier.subtotal), style = MaterialTheme.typography.bodyMedium)
                                }
                                if (tier.discountTotal > 0.0) {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Discount", style = MaterialTheme.typography.bodyMedium, color = AppColors.Orange)
                                        Text("-${formatMoney(tier.discountTotal)}", style = MaterialTheme.typography.bodyMedium, color = AppColors.Orange)
                                    }
                                }
                                if (tier.taxTotal > 0.0) {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Tax", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(formatMoney(tier.taxTotal), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total", fontWeight = FontWeight.Bold)
                                    Text(formatMoney(tier.total), fontWeight = FontWeight.Bold, color = AppColors.Blue, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                } else {
                    val items = e.line_items.orEmpty()
                    val svcs = items.filter { it.item_type in listOf("service","labor") }
                    val mats = items.filter { it.item_type == "material" }
                    val disc = items.filter { it.item_type == "discount" }
                    if (svcs.isNotEmpty()) item { LineItemsCard("LABOR", svcs) }
                    if (mats.isNotEmpty()) item { LineItemsCard("MATERIALS", mats) }
                    if (disc.isNotEmpty()) item { CRMCard { SectionLabel("DISCOUNTS"); disc.forEach { li -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(li.name, fontWeight = FontWeight.Medium); Text("-${formatMoney(li.quantity * li.unit_price)}", color = AppColors.Orange, fontWeight = FontWeight.SemiBold) } } } }
                    item { TotalsCard(e.subtotal, e.tax_total, e.discount_total, e.total) }
                }
                // For GBB with a selected tier, show the selected totals
                if (e.isGbb && e.selectedTierId != null) {
                    item { TotalsCard(e.subtotal, e.tax_total, e.discount_total, e.total) }
                }
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Present in Person button for GBB unsigned estimates
                        if (e.isGbb && !e.isSigned) {
                            AppButton(
                                onClick = { onPresent(e.id) },
                                label = "Present to Customer",
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = Icons.Default.Slideshow
                            )
                        }
                        if (e.isSigned) {
                            // Signed state: Convert to Invoice is the primary action
                            AppButton(
                                onClick = { converting = true; vm.convert(e.id) { onConvertToInvoice(it) } },
                                label = "Convert to Invoice",
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !converting,
                                loading = converting,
                                leadingIcon = Icons.Default.Receipt
                            )
                            if (e.deposit_required && !e.deposit_collected) {
                                AppButton(
                                    onClick = { onCollectDeposit(e.id) },
                                    label = "Collect Deposit",
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = Icons.Default.Payment
                                )
                            }
                            AppButton(onClick = { onSend(e.id) }, label = "Send for Signature", modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Default.Draw)
                        } else if (!e.isGbb) {
                            AppButton(onClick = { onSign(e.id) }, label = "Get Signature", modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Default.Draw)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppButton(onClick = { onSend(e.id) }, label = "For Signature", modifier = Modifier.weight(1f), leadingIcon = Icons.Default.Draw)
                                AppButton(onClick = { converting = true; vm.convert(e.id) { onConvertToInvoice(it) } }, label = "→ Invoice", modifier = Modifier.weight(1f), enabled = !converting)
                            }
                        } else {
                            // GBB unsigned — additional send for signature option
                            AppButton(onClick = { onSend(e.id) }, label = "Send for Remote Signature", modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Default.Draw)
                        }
                    }
                }
            }
        } ?: LoadingView()
    }

    // Deposit settings dialog
    if (showDepositDialog) {
        est?.let { e ->
            DepositSettingsDialog(
                estimate  = e,
                onDismiss = { showDepositDialog = false },
                onSave    = { req, amt, type ->
                    vm.saveDepositSettings(e.id, req, amt, type) { showDepositDialog = false }
                }
            )
        }
    }

    // Edit confirmation dialog (shown when estimate is signed)
    if (showEditConfirm) {
        AlertDialog(
            onDismissRequest = { showEditConfirm = false },
            title = { Text("Edit Estimate") },
            text  = { Text("Editing will require a new signature. Continue?") },
            confirmButton = {
                TextButton(onClick = { showEditConfirm = false; est?.let { onEdit(it.id) } }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showEditConfirm = false }) { Text("Cancel") } }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteConfirm = false },
            title = { Text("Delete Estimate") },
            text  = { Text("Are you sure? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        est?.let { e ->
                            vm.deleteEstimate(e.id) {
                                deleting = false
                                showDeleteConfirm = false
                                onBack()
                            }
                        }
                    }
                ) { Text(if (deleting) "Deleting..." else "Delete", color = AppColors.Red) }
            },
            dismissButton = { TextButton(enabled = !deleting, onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LineItemsCard(label: String, items: List<LineItem>) {
    CRMCard {
        SectionLabel(label)
        items.forEach { li ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!li.image_url.isNullOrBlank()) {
                    AsyncImage(
                        model = li.image_url,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(li.name, fontWeight = FontWeight.Medium)
                        if (li.price_overridden) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.Warning, null, tint = AppColors.Orange, modifier = Modifier.size(12.dp)) }
                    }
                    if (!li.description.isNullOrBlank()) {
                        Text(li.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        if (!li.sku.isNullOrBlank()) {
                            Text("SKU: ${li.sku}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("${formatQty(li.quantity)} × ${formatMoney(li.unit_price)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(formatMoney(li.total), fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun TotalsCard(subtotal: Double, taxTotal: Double, discountTotal: Double, total: Double) {
    CRMCard {
        SectionLabel("TOTALS")
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Subtotal"); Text(formatMoney(subtotal)) }
        if (discountTotal > 0) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Potential Savings", color = AppColors.Orange); Text("-${formatMoney(discountTotal)}", color = AppColors.Orange) }
        if (taxTotal > 0) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Tax"); Text(formatMoney(taxTotal)) }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total", fontWeight = FontWeight.Bold); Text(formatMoney(total), fontWeight = FontWeight.Bold, color = AppColors.Blue) }
    }
}

// ─── SCREEN: Estimate Build ───────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateBuildScreen(
    jobId: String, estimateId: String? = null, customerId: String? = null,
    onBack: () -> Unit, onSign: (String) -> Unit, onSend: (String) -> Unit,
    onAddFromPricebook: (String) -> Unit,
    pickerVm: PricebookPickerViewModel,
    vm: EstimateBuildViewModel = hiltViewModel()
) {
    val st     by vm.state.collectAsState()
    val picked by pickerVm.picked.collectAsState()
    // P2.24: DO NOT clear picks on mount. On the EDIT path loadExisting's async
    // recompositions delayed this mount-time clearPicked() so it fired AFTER the user
    // returned from the picker, wiping the just-added item (CREATE has no loadExisting, so
    // it worked). Stale picks are now cleared at NAVIGATION time (onAddFromPricebook in
    // Navigation.kt) instead, so a returned pick is always consumed by LaunchedEffect(picked).
    var showDiscount by remember { mutableStateOf(false) }
    var depositAmtText by remember { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isGbb = st.presentationMode == "gbb"

    // Sync deposit amount text when loading an existing estimate
    LaunchedEffect(st.depositAmount) {
        if (st.depositAmount > 0 && depositAmtText.isBlank())
            depositAmtText = "%.2f".format(st.depositAmount)
    }

    LaunchedEffect(jobId, customerId) {
        if (jobId.isNotBlank()) vm.loadJob(jobId)
        else if (!customerId.isNullOrBlank()) vm.loadCustomer(customerId)
        if (estimateId != null) vm.loadExisting(estimateId)
    }
    LaunchedEffect(picked) {
        if (picked.isNotEmpty()) {
            if (isGbb) vm.addFromPricebookToTier(st.selectedGbbTab, picked.values)
            else vm.addFromPricebook(picked.values)
            pickerVm.clearPicked()
        }
    }
    LaunchedEffect(st.error) { st.error?.let { snack.showSnackbar(it); vm.clearError() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(title = { Text(if (estimateId == null) "Create Estimate" else "Edit Estimate", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { if (st.saving) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 8.dp), strokeWidth = 2.dp) })
        },
        bottomBar = {
            val hasItems = if (isGbb) st.tiers.any { it.lineItems.isNotEmpty() } else st.lineItems.isNotEmpty()
            Surface(tonalElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(onClick = { vm.save(jobId, estimateId) { scope.launch { snack.showSnackbar("Estimate saved") }; scope.launch { delay(700); onBack() } } }, label = "Save", modifier = Modifier.weight(1f), enabled = !st.saving)
                    AppButton(onClick = { vm.save(jobId, estimateId) { onSign(it) } }, label = "Get Signature", modifier = Modifier.weight(1f), enabled = hasItems && !st.saving, leadingIcon = Icons.Default.Draw)
                    AppButton(onClick = { vm.save(jobId, estimateId) { onSend(it) } }, label = "Send for Sig", modifier = Modifier.weight(1f), enabled = hasItems && !st.saving, leadingIcon = Icons.Default.Draw)
                }
            }
        }
    ) { padding ->
        if (st.loading) { LoadingView(); return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // Customer info
            st.job?.let { job ->
                CRMCard {
                    SectionLabel("CUSTOMER")
                    InfoRow(Icons.Default.Person, "Name", job.customerName)
                    job.cust_phone?.let { InfoRow(Icons.Default.Phone, "Phone", it) }
                    job.custFullAddress.takeIf { it.isNotBlank() }?.let { InfoRow(Icons.Default.LocationOn, "Address", it) }
                    job.techName?.let { InfoRow(Icons.Default.Engineering, "Technician", it) }
                }
            }
            // Presentation mode toggle
            CRMCard {
                SectionLabel("PRESENTATION MODE")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isGbb, onClick = { vm.setPresentationMode("standard") },
                        label = { Text("Standard") }, modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isGbb, onClick = { vm.setPresentationMode("gbb") },
                        label = { Text("Good / Better / Best") }, modifier = Modifier.weight(1f)
                    )
                }
                if (isGbb) {
                    Spacer(Modifier.height(4.dp))
                    Text("Build separate line items per tier. Customer selects one before signing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (isGbb) {
                // GBB tier tab row
                val tierLabels = st.tiers.map { it.label.ifBlank { "Tier ${st.tiers.indexOf(it) + 1}" } }
                ScrollableTabRow(selectedTabIndex = st.selectedGbbTab, edgePadding = 16.dp) {
                    tierLabels.forEachIndexed { idx, label ->
                        Tab(
                            selected = st.selectedGbbTab == idx,
                            onClick = { vm.setGbbTab(idx) },
                            text = { Text(label, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                    if (st.tiers.size < MAX_TIERS) {
                        Tab(
                            selected = false,
                            onClick = { vm.addTier() },
                            text = { Text("+ Add Option", color = AppColors.Blue, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
                val tier = st.tiers[st.selectedGbbTab]
                val tIdx = st.selectedGbbTab
                var showRemoveTierConfirm by remember { mutableStateOf(false) }
                // Tier label and description
                CRMCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel("TIER NAME & DESCRIPTION")
                        Row {
                            if (st.tiers.size < MAX_TIERS) {
                                IconButton(
                                    onClick = { vm.duplicateTier(tIdx) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, "Duplicate this option",
                                        Modifier.size(18.dp), tint = AppColors.Blue)
                                }
                            }
                            if (st.tiers.size > MIN_TIERS) {
                                IconButton(
                                    onClick = { showRemoveTierConfirm = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Delete, "Remove this option",
                                        Modifier.size(18.dp), tint = AppColors.Red)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = tier.label, onValueChange = { vm.updateTierLabel(tIdx, it) },
                        label = { Text("Tier Name (e.g. Good, Better, Best)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tier.description, onValueChange = { vm.updateTierDescription(tIdx, it) },
                        label = { Text("Description (optional)") },
                        minLines = 2, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
                    )
                }
                if (showRemoveTierConfirm) {
                    AlertDialog(
                        onDismissRequest = { showRemoveTierConfirm = false },
                        title = { Text("Remove this option?") },
                        text = { Text("\"${tier.label.ifBlank { "Tier ${tIdx + 1}" }}\" and all its line items will be removed. You can undo by tapping Cancel before saving.") },
                        confirmButton = {
                            TextButton(onClick = {
                                vm.removeTier(tIdx)
                                showRemoveTierConfirm = false
                            }) { Text("Remove", color = AppColors.Red, fontWeight = FontWeight.SemiBold) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRemoveTierConfirm = false }) { Text("Cancel") }
                        }
                    )
                }
                val tierServices  = tier.lineItems.filter { it.item_type in listOf("service","labor") }
                val tierMaterials = tier.lineItems.filter { it.item_type == "material" }
                val tierDiscounts = tier.lineItems.filter { it.item_type == "discount" }
                LineItemSection("LABOR", tierServices,   // P2.22: Services→Labor
                    onAdd = { onAddFromPricebook("labor") },
                    onRemove = { vm.removeTierLineItem(tIdx, it) },
                    onUpdate = { vm.updateTierLineItem(tIdx, it) })
                LineItemSection("MATERIALS", tierMaterials,
                    onAdd = { onAddFromPricebook("material") },
                    onRemove = { vm.removeTierLineItem(tIdx, it) },
                    onUpdate = { vm.updateTierLineItem(tIdx, it) })
                CRMCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("DISCOUNTS")
                        TextButton(onClick = { showDiscount = true }) { Text("Add Discount") }
                    }
                    tierDiscounts.forEach { li ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.weight(1f)) { Text(li.name, fontWeight = FontWeight.Medium); Text("-${formatMoney(li.quantity * li.unit_price)}", color = AppColors.Orange) }
                            IconButton(onClick = { vm.removeTierLineItem(tIdx, li.id) }, Modifier.size(32.dp)) { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                        }
                    }
                }
                // Tier total summary
                CRMCard {
                    SectionLabel("TIER TOTAL")
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Subtotal"); Text(formatMoney(tier.subtotal)) }
                    if (tier.discountTotal > 0) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Discounts", color = AppColors.Orange); Text("-${formatMoney(tier.discountTotal)}", color = AppColors.Orange) }
                    if (tier.taxTotal > 0) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Tax"); Text(formatMoney(tier.taxTotal)) }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${tier.label.ifBlank { "Tier" }} Total", fontWeight = FontWeight.Bold)
                        Text(formatMoney(tier.total), fontWeight = FontWeight.Bold, color = AppColors.Blue, style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                // Standard mode
                // Services
                LineItemSection("LABOR", vm.services, onAdd = { onAddFromPricebook("labor") }, onRemove = { vm.removeLineItem(it) }, onUpdate = { vm.updateLineItem(it) })  // P2.22: Services→Labor
                // Materials
                LineItemSection("MATERIALS", vm.materials, onAdd = { onAddFromPricebook("material") }, onRemove = { vm.removeLineItem(it) }, onUpdate = { vm.updateLineItem(it) })
                // Discounts
                CRMCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { SectionLabel("DISCOUNTS"); TextButton(onClick = { showDiscount = true }) { Text("Add Discount") } }
                    vm.discounts.forEach { li -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Column(Modifier.weight(1f)) { Text(li.name, fontWeight = FontWeight.Medium); Text("-${formatMoney(li.quantity * li.unit_price)}", color = AppColors.Orange) }; IconButton(onClick = { vm.removeLineItem(li.id) }, Modifier.size(32.dp)) { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) } } }
                }
            } // end if/else isGbb

            // Notes & Terms
            CRMCard { SectionLabel("NOTES"); OutlinedTextField(st.notes, { vm.setNotes(it) }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) }
            CRMCard { SectionLabel("TERMS"); OutlinedTextField(st.terms, { vm.setTerms(it) }, label = { Text("Terms & Conditions") }, minLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) }
            // Totals (standard mode only)
            if (!isGbb) {
                CRMCard {
                    SectionLabel("SUMMARY")
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Subtotal"); Text(formatMoney(vm.subtotal)) }
                    if (vm.discountTotal > 0) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Potential Savings", color = AppColors.Orange); Text("-${formatMoney(vm.discountTotal)}", color = AppColors.Orange) }
                    if (vm.taxTotal > 0) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(st.taxLabel); Text(formatMoney(vm.taxTotal)) }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Grand Total", fontWeight = FontWeight.Bold); Text(formatMoney(vm.grandTotal), fontWeight = FontWeight.Bold, color = AppColors.Blue, style = MaterialTheme.typography.titleMedium) }
                }
            }
            // Deposit
            CRMCard {
                SectionLabel("DEPOSIT")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Require Deposit", Modifier.weight(1f))
                    AppSwitch(checked = st.depositRequired, onCheckedChange = { vm.setDepositRequired(it) })
                }
                if (st.depositRequired) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = st.depositType == "fixed",
                            onClick = { vm.setDepositType("fixed") },
                            label = { Text("Fixed \$") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = st.depositType == "percentage",
                            onClick = { vm.setDepositType("percentage") },
                            label = { Text("Percentage %") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = depositAmtText,
                        onValueChange = { depositAmtText = it; vm.setDepositAmount(it.toDoubleOrNull() ?: 0.0) },
                        label = { Text(if (st.depositType == "fixed") "Deposit Amount" else "Deposit Percentage") },
                        prefix = if (st.depositType == "fixed") { { Text("$") } } else null,
                        suffix = if (st.depositType == "percentage") { { Text("%") } } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showDiscount) {
        val discSubtotal = if (isGbb) st.tiers.getOrNull(st.selectedGbbTab)?.subtotal ?: 0.0 else vm.subtotal
        DiscountDialog(subtotal = discSubtotal, onDismiss = { showDiscount = false }, onAdd = { li ->
            if (isGbb) vm.addTierLineItem(st.selectedGbbTab, li) else vm.addLineItem(li)
            showDiscount = false
        })
    }
}

// Detail-screen tier line item row. Mirrors web TierCard fidelity:
// thumbnail (left), name + description + SKU + qty x unit_price (middle),
// line total (right). Read-only. The build/edit screen uses
// EditableLineItemRow with QtyStepperRow + edit fields and is unrelated.
@Composable
private fun TierDetailItemRow(li: LineItem, isDiscount: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (!li.image_url.isNullOrBlank()) {
            AsyncImage(
                model = li.image_url,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(li.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            li.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            li.sku?.takeIf { it.isNotBlank() }?.let {
                Text("SKU: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${formatQty(li.quantity)} × ${formatMoney(li.unit_price)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Prefer the persisted total when present; fall back to qty x price.
        val displayTotal = if (li.total > 0.0) li.total else li.quantity * li.unit_price
        Text(
            (if (isDiscount) "-" else "") + formatMoney(displayTotal),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isDiscount) AppColors.Orange else androidx.compose.ui.graphics.Color.Unspecified
        )
    }
}

@Composable
private fun LineItemSection(label: String, items: List<EditableLineItem>, onAdd: () -> Unit, onRemove: (String) -> Unit, onUpdate: (EditableLineItem) -> Unit) {
    CRMCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(label)
            TextButton(onClick = onAdd) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add from Price Book") }
        }
        if (items.isEmpty()) Text("No items added", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        items.forEach { li -> EditableLineItemRow(li, onRemove, onUpdate); HorizontalDivider() }
        if (items.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Subtotal", fontWeight = FontWeight.SemiBold); Text(formatMoney(items.sumOf { it.total }), fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun EditableLineItemRow(li: EditableLineItem, onRemove: (String) -> Unit, onUpdate: (EditableLineItem) -> Unit) {
    var editing   by remember { mutableStateOf(false) }
    var priceText by remember(li.id) { mutableStateOf("%.2f".format(li.unit_price)) }
    var qtyInt    by remember(li.id) { mutableIntStateOf(li.quantity.toInt().coerceAtLeast(1)) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!li.image_url.isNullOrBlank()) {
                AsyncImage(
                    model = li.image_url,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(li.name, fontWeight = FontWeight.Medium, maxLines = 1)
                    if (li.price_overridden) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.Warning, null, tint = AppColors.Orange, modifier = Modifier.size(12.dp)) }
                }
                if (editing) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        QtyStepperRow(qty = qtyInt, onDecrement = { qtyInt = (qtyInt - 1).coerceAtLeast(1) }, onIncrement = { qtyInt++ }, minQty = 1)
                        OutlinedTextField(priceText, { priceText = it }, label = { Text("Price") }, prefix = { Text("$") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.width(110.dp), shape = RoundedCornerShape(8.dp))
                    }
                } else {
                    Text("${formatQty(li.quantity)} × ${formatMoney(li.unit_price)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(formatMoney(li.total), fontWeight = FontWeight.SemiBold)
            IconButton(onClick = {
                if (editing) {
                    val p = priceText.toDoubleOrNull() ?: li.unit_price
                    onUpdate(li.copy(unit_price = p, quantity = qtyInt.toDouble(), price_overridden = p != li.unit_price || li.price_overridden))
                }
                editing = !editing
            }, modifier = Modifier.size(32.dp)) { Icon(if (editing) Icons.Default.Check else Icons.Default.Edit, null, Modifier.size(16.dp), tint = if (editing) AppColors.Green else MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = { onRemove(li.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = AppColors.Red) }
        }
    }
}

@Composable
private fun DiscountDialog(subtotal: Double, onDismiss: () -> Unit, onAdd: (EditableLineItem) -> Unit) {
    var name      by remember { mutableStateOf("") }
    var amount    by remember { mutableStateOf("") }
    var isPercent by remember { mutableStateOf(false) }

    val dollarAmount = if (isPercent) {
        amount.toDoubleOrNull()?.let { pct -> subtotal * pct / 100.0 }
    } else {
        amount.toDoubleOrNull()
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add Discount") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Type toggle
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isPercent, onClick = { isPercent = false; amount = "" },
                    label = { Text("Fixed \$") }, modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = isPercent, onClick = { isPercent = true; amount = "" },
                    label = { Text("Percentage %") }, modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(name, { name = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
            OutlinedTextField(
                value = amount, onValueChange = { amount = it },
                label = { Text(if (isPercent) "Percentage" else "Amount") },
                prefix = if (!isPercent) { { Text("$") } } else null,
                suffix = if (isPercent)  { { Text("%") } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
            )
            if (isPercent && dollarAmount != null && dollarAmount > 0) {
                Text("= -\$${String.format("%.2f", dollarAmount)} discount",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }, confirmButton = {
        TextButton(
            onClick = {
                val v = dollarAmount ?: return@TextButton
                val label = if (isPercent) {
                    "${name.ifBlank { "Discount" }} (${amount.trimEnd('0').trimEnd('.')}%)"
                } else {
                    name.ifBlank { "Discount" }
                }
                onAdd(EditableLineItem(name = label, quantity = 1.0, unit_price = v, item_type = "discount"))
            },
            enabled = dollarAmount != null && dollarAmount > 0
        ) { Text("Add") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

// ─── SCREEN: Present Tiers (GBB in-person) ───────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentTiersScreen(
    estimateId: String,
    onBack: () -> Unit,
    onTierSelected: (tierId: String, estimateId: String) -> Unit,
    vm: EstimateViewModel = hiltViewModel()
) {
    val est by vm.selected.collectAsState()
    val msg by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selecting by remember { mutableStateOf(false) }

    LaunchedEffect(estimateId) { vm.loadEst(estimateId) }
    LaunchedEffect(est) { est?.selectedTierId?.let { if (it.isNotBlank()) selectedId = it } }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.clearMsg() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
        TopAppBar(
            title = { Text("Choose Your Option", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
        )
    }, bottomBar = {
        Surface(tonalElevation = 8.dp) {
            AppButton(
                onClick = {
                    val tid = selectedId ?: return@AppButton
                    selecting = true
                    vm.selectTierAndSign(estimateId, tid) { onTierSelected(tid, estimateId) }
                },
                label = "Confirm Selection & Sign",
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).height(52.dp),
                enabled = selectedId != null && !selecting,
                loading = selecting,
                leadingIcon = Icons.Default.Check
            )
        }
    }) { padding ->
        est?.let { e ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Show the customer their pricing options. They tap to select the one they prefer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(e.tiers.orEmpty(), key = { it.id }) { tier ->
                    val isSelected = tier.id == selectedId
                    Card(
                        onClick = { selectedId = tier.id },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, AppColors.Blue) else null,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                AppColors.Blue.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(tier.tierLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, null, tint = AppColors.Blue, modifier = Modifier.size(24.dp))
                                } else {
                                    Box(Modifier.size(24.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
                                }
                            }
                            tier.description?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(12.dp))
                            val svcs = tier.lineItems.filter { it.item_type in listOf("service","labor") }
                            val mats = tier.lineItems.filter { it.item_type == "material" }
                            val all  = svcs + mats
                            all.forEach { li ->
                                // P2.17 PART 1 — show item image + description + SKU on the
                                // in-person present screen (reuse the detail GBB row), not just name+price.
                                TierDetailItemRow(li)
                                HorizontalDivider()
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(formatMoney(tier.total), fontWeight = FontWeight.Bold, color = AppColors.Blue, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        } ?: LoadingView()
    }
}

// ─── SCREEN: Estimate Signature ───────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateSignScreen(
    estimateId: String, onBack: () -> Unit, onSigned: (jobId: String?, needsDeposit: Boolean) -> Unit,
    vm: EstimateViewModel = hiltViewModel()
) {
    val est    by vm.selected.collectAsState()
    val msg    by vm.message.collectAsState()
    val snack  = remember { SnackbarHostState() }
    var agreed by remember { mutableStateOf(false) }
    var signing by remember { mutableStateOf(false) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke = remember { mutableStateListOf<Offset>() }
    var pendingSignedJobId by remember { mutableStateOf<String?>(null) }
    var pendingNeedsDeposit by remember { mutableStateOf(false) }
    var showAddToInvoiceDialog by remember { mutableStateOf(false) }
    var showKeepReplaceDialog by remember { mutableStateOf(false) }
    var pendingOldItems by remember { mutableStateOf<List<LineItem>>(emptyList()) }

    LaunchedEffect(estimateId) { vm.loadEst(estimateId) }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.clearMsg() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
        TopAppBar(title = { Text("Customer Signature", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Summary
            est?.let { e ->
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(e.estimate_number, fontWeight = FontWeight.Bold, color = AppColors.Blue)
                        Text(e.customerName)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total", fontWeight = FontWeight.SemiBold); Text(formatMoney(e.total), fontWeight = FontWeight.Bold, color = AppColors.Blue) }
                    }
                }
                if (!e.terms.isNullOrBlank()) {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) { Text("Terms & Conditions", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(6.dp)); Text(e.terms, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = agreed, onCheckedChange = { agreed = it }); Text("I agree to the terms above") }
            Text("Sign Below", fontWeight = FontWeight.SemiBold)
            SignatureCanvas(strokes = strokes, currentStroke = currentStroke)
            val density = LocalDensity.current
            AppButton(onClick = {
                if (strokes.isEmpty()) return@AppButton
                signing = true
                val w = with(density) { 380.dp.toPx().toInt() }; val h = with(density) { 200.dp.toPx().toInt() }
                val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val ac = android.graphics.Canvas(bm); ac.drawColor(android.graphics.Color.WHITE)
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = 6f; style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND }
                strokes.forEach { stroke -> val path = android.graphics.Path(); stroke.forEachIndexed { i, pt -> if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y) }; ac.drawPath(path, p) }
                val baos = ByteArrayOutputStream(); bm.compress(Bitmap.CompressFormat.PNG, 90, baos)
                val b64 = "data:image/png;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                vm.sign(estimateId, b64, est?.customerName) { jobId, needsDeposit ->
                    signing = false
                    pendingSignedJobId = jobId
                    pendingNeedsDeposit = needsDeposit
                    showAddToInvoiceDialog = true
                }
            }, label = "Confirm & Sign", modifier = Modifier.fillMaxWidth().height(52.dp), enabled = agreed && strokes.isNotEmpty() && !signing, loading = signing, leadingIcon = Icons.Default.Check)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddToInvoiceDialog) {
        AlertDialog(
            onDismissRequest = { showAddToInvoiceDialog = false; onSigned(pendingSignedJobId, pendingNeedsDeposit) },
            title = { Text("Add to Invoice?") },
            text = { Text("Signature captured! Would you like to convert this estimate to an invoice now?") },
            confirmButton = {
                TextButton(onClick = {
                    showAddToInvoiceDialog = false
                    val jobId = pendingSignedJobId
                    if (jobId != null) {
                        vm.fetchJobInvoice(jobId) { existingItems ->
                            if (existingItems.isNotEmpty()) {
                                pendingOldItems = existingItems
                                showKeepReplaceDialog = true
                            } else {
                                vm.convert(estimateId) { onSigned(pendingSignedJobId, pendingNeedsDeposit) }
                            }
                        }
                    } else {
                        vm.convert(estimateId) { onSigned(pendingSignedJobId, pendingNeedsDeposit) }
                    }
                }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { showAddToInvoiceDialog = false; onSigned(pendingSignedJobId, pendingNeedsDeposit) }) { Text("No") }
            }
        )
    }

    if (showKeepReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showKeepReplaceDialog = false; onSigned(pendingSignedJobId, pendingNeedsDeposit) },
            title = { Text("Invoice already has items") },
            text = { Text("Keep existing items and add estimate items, or replace all items with estimate items?") },
            confirmButton = {
                TextButton(onClick = {
                    showKeepReplaceDialog = false
                    vm.convert(estimateId) { newInvId ->
                        vm.appendItemsToInvoice(newInvId, pendingOldItems) { onSigned(pendingSignedJobId, pendingNeedsDeposit) }
                    }
                }) { Text("Keep & Add") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showKeepReplaceDialog = false
                    vm.convert(estimateId) { onSigned(pendingSignedJobId, pendingNeedsDeposit) }
                }) { Text("Replace") }
            }
        )
    }
}

// ─── SCREEN: Deposit Collection ───────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositCollectionScreen(
    estimateId: String,
    onBack: () -> Unit,
    onDone: (jobId: String?) -> Unit,
    vm: EstimateViewModel = hiltViewModel()
) {
    val est by vm.selected.collectAsState()
    val msg by vm.message.collectAsState()
    val depQr by vm.depositQr.collectAsState()
    val depLink by vm.depositLink.collectAsState()
    val depLoading by vm.depositLoading.collectAsState()
    val snack = remember { SnackbarHostState() }

    var method by remember { mutableStateOf("cash") }
    var amountText by remember { mutableStateOf("") }
    var collecting by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var remainingBalance by remember { mutableStateOf(0.0) }
    var collectedAmount by remember { mutableStateOf(0.0) }

    LaunchedEffect(estimateId) { vm.loadEst(estimateId) }
    LaunchedEffect(est) {
        est?.let { e ->
            if (amountText.isBlank()) {
                val depositAmt = if (e.deposit_type == "percentage") e.total * e.deposit_amount / 100.0 else e.deposit_amount
                if (depositAmt > 0) amountText = "%.2f".format(depositAmt)
            }
        }
    }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.clearMsg(); collecting = false } }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Collect Deposit", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (success) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = AppColors.Green, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(16.dp))
                Text("Deposit Collected!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = AppColors.Green)
                Spacer(Modifier.height(8.dp))
                Text(formatMoney(collectedAmount) + " received", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Payment method: ${method.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Date: ${java.time.LocalDate.now()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (remainingBalance > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("Remaining balance: ${formatMoney(remainingBalance)} due upon completion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(32.dp))
                AppButton(onClick = { onDone(est?.job_id) }, label = "Done", modifier = Modifier.fillMaxWidth())
            }
        } else {
            est?.let { e ->
                val depositDollarAmt = if (e.deposit_type == "percentage")
                    e.total * e.deposit_amount / 100.0 else e.deposit_amount

                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(8.dp))
                    // Summary card
                    CRMCard {
                        SectionLabel("DEPOSIT SUMMARY")
                        InfoRow(Icons.Default.Description, "Estimate", e.estimate_number)
                        InfoRow(Icons.Default.Person, "Customer", e.customerName)
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Estimate Total"); Text(formatMoney(e.total), fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Deposit Required", fontWeight = FontWeight.Bold, color = AppColors.Orange)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatMoney(depositDollarAmt), fontWeight = FontWeight.Bold, color = AppColors.Orange)
                                if (e.deposit_type == "percentage") Text("${e.deposit_amount.toInt()}% of total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    // Payment method
                    CRMCard {
                        SectionLabel("PAYMENT METHOD")
                        listOf("cash" to "Cash", "check" to "Check", "credit_card" to "Credit Card (ScanPay)", "other" to "Zelle/Venmo/Other").forEach { (value, label) ->
                            Row(Modifier.fillMaxWidth().clickable { method = value }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = method == value, onClick = { method = value })
                                Spacer(Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                    // Amount
                    CRMCard {
                        SectionLabel("DEPOSIT AMOUNT")
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Amount") },
                            prefix = { Text("$") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    // Buttons
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (method == "credit_card") {
                            // P2.38: real ScanPay — QR on-screen (customer present) + send a payment link.
                            // The webhook marks the deposit collected; we poll deposit-status in the dialogs.
                            AppButton(
                                onClick = { vm.createDepositScanPayQr(estimateId) },
                                label = "Show QR to Customer",
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = !depLoading,
                                loading = depLoading,
                                leadingIcon = Icons.Default.QrCode
                            )
                            AppButton(
                                onClick = { vm.createDepositScanPayLink(estimateId, "both") },
                                label = "Send Payment Link",
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = !depLoading,
                                leadingIcon = Icons.Default.Link
                            )
                        } else {
                            AppButton(
                                onClick = {
                                    val amt = amountText.toDoubleOrNull() ?: return@AppButton
                                    if (amt <= 0) return@AppButton
                                    collecting = true
                                    vm.collectDeposit(estimateId, amt, method) { remaining ->
                                        collecting = false
                                        collectedAmount = amt
                                        remainingBalance = remaining
                                        success = true
                                    }
                                },
                                label = "Collect Deposit",
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = amountText.toDoubleOrNull() != null && !collecting,
                                loading = collecting,
                                leadingIcon = Icons.Default.Payment
                            )
                        }
                        TextButton(onClick = { onDone(e.job_id) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Skip for Now", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            } ?: LoadingView()
        }
    }

    // P2.38: ScanPay deposit dialogs (poll deposit-status → success when paid).
    val depAmt = est?.let { if (it.deposit_type == "percentage") it.total * it.deposit_amount / 100.0 else it.deposit_amount } ?: 0.0
    val markPaid = { collectedAmount = depAmt; remainingBalance = (est?.total ?: depAmt) - depAmt; success = true; vm.clearDepositScanPay() }
    depQr?.let { qr ->
        DepositScanPayDialog("Scan to Pay Deposit", qr.qr_data_url, qr.payment_url, depAmt, estimateId, vm,
            onPaid = markPaid, onDismiss = { vm.clearDepositScanPay() })
    }
    depLink?.let { lk ->
        DepositScanPayDialog("Deposit Link Sent", null, lk.payment_url, depAmt, estimateId, vm,
            smsSent = lk.sms_sent, phoneUsed = lk.phone_used, onPaid = markPaid, onDismiss = { vm.clearDepositScanPay() })
    }
}

// P2.38: one dialog serves both the on-screen QR (qrDataUrl set) and the sent-link
// (qrDataUrl null) cases. Polls the estimate deposit-status every 3s; onPaid fires when
// the webhook has flipped deposit_collected true.
@Composable
private fun DepositScanPayDialog(
    title: String,
    qrDataUrl: String?,
    paymentUrl: String,
    amount: Double,
    estimateId: String,
    vm: EstimateViewModel,
    smsSent: Boolean = false,
    phoneUsed: String? = null,
    onPaid: () -> Unit,
    onDismiss: () -> Unit
) {
    val status by vm.depositStatus.collectAsState()
    val clipboard = LocalClipboardManager.current
    val qrBitmap = remember(qrDataUrl) {
        qrDataUrl?.let { runCatching {
            val bytes = Base64.decode(it.substringAfter("base64,"), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull() }
    }
    LaunchedEffect(estimateId) { while (true) { delay(3000); vm.pollDepositStatus(estimateId) } }
    LaunchedEffect(status) { if (status?.deposit_collected == true) onPaid() }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(formatMoney(amount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.Green)
                if (qrBitmap != null) {
                    Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "Deposit QR", modifier = Modifier.size(240.dp).clip(RoundedCornerShape(8.dp)))
                }
                if (smsSent && phoneUsed != null) {
                    Text("Sent to $phoneUsed", style = MaterialTheme.typography.bodySmall, color = AppColors.Green)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Waiting for payment…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppButton(onClick = { clipboard.setText(AnnotatedString(paymentUrl)) }, label = "Copy Link", modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Default.ContentCopy)
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

// ─── Deposit Settings Dialog ──────────────────────────────────────────────
@Composable
private fun DepositSettingsDialog(
    estimate: Estimate,
    onDismiss: () -> Unit,
    onSave: (depositRequired: Boolean, depositAmount: Double, depositType: String) -> Unit
) {
    var depositRequired by remember { mutableStateOf(estimate.deposit_required) }
    var depositType by remember { mutableStateOf(estimate.deposit_type.ifBlank { "fixed" }) }
    var amountText by remember { mutableStateOf(if (estimate.deposit_amount > 0) "%.0f".format(estimate.deposit_amount) else "") }

    val depositDollarPreview = if (depositType == "percentage")
        amountText.toDoubleOrNull()?.let { pct -> estimate.total * pct / 100.0 } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Deposit Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Require Deposit", fontWeight = FontWeight.Medium)
                    AppSwitch(checked = depositRequired, onCheckedChange = { depositRequired = it })
                }
                if (depositRequired) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = depositType == "fixed", onClick = { depositType = "fixed"; amountText = "" }, label = { Text("Fixed \$") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = depositType == "percentage", onClick = { depositType = "percentage"; amountText = "" }, label = { Text("Percentage %") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(if (depositType == "fixed") "Amount" else "Percentage") },
                        prefix = if (depositType == "fixed") { { Text("$") } } else null,
                        suffix = if (depositType == "percentage") { { Text("%") } } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
                    )
                    if (depositType == "percentage" && depositDollarPreview != null && depositDollarPreview > 0) {
                        Text("= ${formatMoney(depositDollarPreview)} deposit", style = MaterialTheme.typography.bodySmall, color = AppColors.Blue)
                    }
                    Text("Customer will be prompted to pay this deposit when signing",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = if (!depositRequired) 0.0 else amountText.toDoubleOrNull() ?: 0.0
                    onSave(depositRequired, amount, depositType)
                },
                enabled = !depositRequired || amountText.toDoubleOrNull() != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SignatureCanvas(strokes: MutableList<List<Offset>>, currentStroke: MutableList<Offset>) {
    Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.White).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))) {
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset -> currentStroke.clear(); currentStroke.add(offset) },
                onDrag      = { change, _ -> currentStroke.add(change.position) },
                onDragEnd   = { if (currentStroke.size > 1) { strokes.add(currentStroke.toList()) }; currentStroke.clear() }
            )
        }) {
            strokes.forEach { stroke ->
                val path = Path(); stroke.forEachIndexed { i, pt -> if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y) }
                drawPath(path, androidx.compose.ui.graphics.Color.Black, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (currentStroke.size > 1) {
                val path = Path(); currentStroke.forEachIndexed { i, pt -> if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y) }
                drawPath(path, androidx.compose.ui.graphics.Color.Black, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawLine(androidx.compose.ui.graphics.Color.LightGray, Offset(20f, size.height * 0.8f), Offset(size.width - 20f, size.height * 0.8f), strokeWidth = 1f)
        }
        if (strokes.isEmpty() && currentStroke.isEmpty()) Text("Sign here", color = androidx.compose.ui.graphics.Color.LightGray, modifier = Modifier.align(Alignment.Center))
        TextButton(onClick = { strokes.clear(); currentStroke.clear() }, modifier = Modifier.align(Alignment.TopEnd)) { Text("Clear", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
    }
}

// ─── SCREEN: Estimate Send ────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateSendScreen(
    estimateId: String, onBack: () -> Unit, onSent: () -> Unit,
    vm: EstimateViewModel = hiltViewModel()
) {
    val est      by vm.selected.collectAsState()
    val customer by vm.customer.collectAsState()
    val msg      by vm.message.collectAsState()
    val snack    = remember { SnackbarHostState() }
    var sending  by remember { mutableStateOf(false) }
    var saveToProfile by remember { mutableStateOf(true) }

    // Contact selection state — (value, checked, isEditing, editText)
    data class ContactEntry(val value: String, var checked: Boolean = true, var editText: String = value)

    // Build contact lists from customer data.
    // Use a stable list that is populated once when data first arrives, never rebuilt
    // (rebuilding on every customer/est update would reset user checkbox choices).
    val emailEntries = remember { mutableStateListOf<ContactEntry>() }
    val phoneEntries = remember { mutableStateListOf<ContactEntry>() }

    // Populate once when est+customer load — merge without wiping existing user edits
    LaunchedEffect(customer, est) {
        val emailPrimary = est?.cust_email
        val emailExtras  = customer?.emails ?: emptyList()
        val allEmails = buildList {
            emailPrimary?.let { add(it) }
            emailExtras.filter { it != emailPrimary }.forEach { add(it) }
        }
        // Add any addresses not already in the list (preserve existing checked state)
        val existingEmailValues = emailEntries.map { it.value }.toSet()
        allEmails.filter { it !in existingEmailValues }.forEach {
            emailEntries.add(ContactEntry(it, true))
        }

        val phonePrimary = est?.cust_phone
        val phoneExtras  = customer?.phones ?: emptyList()
        val allPhones = buildList {
            phonePrimary?.let { add(it) }
            phoneExtras.filter { it != phonePrimary }.forEach { add(it) }
        }
        val existingPhoneValues = phoneEntries.map { it.value }.toSet()
        allPhones.filter { it !in existingPhoneValues }.forEach {
            phoneEntries.add(ContactEntry(it, true))
        }
    }
    var showAddEmail by remember { mutableStateOf(false) }
    var showAddPhone by remember { mutableStateOf(false) }
    var newEmail by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    LaunchedEffect(estimateId) { vm.loadEst(estimateId) }
    LaunchedEffect(msg) {
        msg?.let {
            snack.showSnackbar(it)
            vm.clearMsg()
            sending = false  // always re-enable button (success or error)
            if (it.contains("sent", ignoreCase = true)) onSent()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
        TopAppBar(title = { Text("Send for Signature", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary card
            est?.let { e ->
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(e.estimate_number, fontWeight = FontWeight.Bold)
                        Text(e.customerName)
                        Text(formatMoney(e.total), fontWeight = FontWeight.Bold, color = AppColors.Blue)
                    }
                }
            }

            // Email contacts
            CRMCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("EMAIL")
                    IconButton(onClick = { showAddEmail = true }, Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = AppColors.Blue)
                    }
                }
                emailEntries.forEachIndexed { i, entry ->
                    var editing by remember { mutableStateOf(false) }
                    var editVal by remember(entry.value) { mutableStateOf(entry.value) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = entry.checked, onCheckedChange = { emailEntries[i] = entry.copy(checked = it) })
                        if (editing) {
                            OutlinedTextField(editVal, { editVal = it }, singleLine = true, modifier = Modifier.weight(1f).padding(end = 4.dp), shape = RoundedCornerShape(8.dp))
                            IconButton(onClick = { emailEntries[i] = entry.copy(value = editVal, editText = editVal); editing = false }, Modifier.size(32.dp)) {
                                Icon(Icons.Default.Check, null, tint = AppColors.Green, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Text(entry.value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { editing = true }, Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (showAddEmail) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(newEmail, { newEmail = it }, placeholder = { Text("Enter email") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                        IconButton(onClick = {
                            if (newEmail.isNotBlank()) {
                                emailEntries.add(ContactEntry(newEmail.trim(), true))
                                if (saveToProfile) est?.customer_id?.let { cid -> vm.addContact(cid, "email", newEmail.trim()) {} }
                                newEmail = ""; showAddEmail = false
                            }
                        }) { Icon(Icons.Default.Check, null, tint = AppColors.Green) }
                        IconButton(onClick = { newEmail = ""; showAddEmail = false }) {
                            Icon(Icons.Default.Close, null, tint = AppColors.Red)
                        }
                    }
                }
                if (emailEntries.isEmpty() && !showAddEmail) {
                    Text("No email on file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Phone contacts
            CRMCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("SMS / PHONE")
                    IconButton(onClick = { showAddPhone = true }, Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = AppColors.Blue)
                    }
                }
                phoneEntries.forEachIndexed { i, entry ->
                    var editing by remember { mutableStateOf(false) }
                    var editVal by remember(entry.value) { mutableStateOf(entry.value) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = entry.checked, onCheckedChange = { phoneEntries[i] = entry.copy(checked = it) })
                        if (editing) {
                            OutlinedTextField(editVal, { editVal = it }, singleLine = true, modifier = Modifier.weight(1f).padding(end = 4.dp), shape = RoundedCornerShape(8.dp))
                            IconButton(onClick = { phoneEntries[i] = entry.copy(value = editVal, editText = editVal); editing = false }, Modifier.size(32.dp)) {
                                Icon(Icons.Default.Check, null, tint = AppColors.Green, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Text(entry.value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { editing = true }, Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (showAddPhone) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(newPhone, { newPhone = it }, placeholder = { Text("Enter phone") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                        IconButton(onClick = {
                            if (newPhone.isNotBlank()) {
                                phoneEntries.add(ContactEntry(newPhone.trim(), true))
                                if (saveToProfile) est?.customer_id?.let { cid -> vm.addContact(cid, "phone", newPhone.trim()) {} }
                                newPhone = ""; showAddPhone = false
                            }
                        }) { Icon(Icons.Default.Check, null, tint = AppColors.Green) }
                        IconButton(onClick = { newPhone = ""; showAddPhone = false }) {
                            Icon(Icons.Default.Close, null, tint = AppColors.Red)
                        }
                    }
                }
                if (phoneEntries.isEmpty() && !showAddPhone) {
                    Text("No phone on file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Save to profile toggle
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppSwitch(checked = saveToProfile, onCheckedChange = { saveToProfile = it })
                Spacer(Modifier.width(12.dp))
                Text("Save new contacts to customer profile", style = MaterialTheme.typography.bodySmall)
            }

            val hasRecipient = emailEntries.any { it.checked } || phoneEntries.any { it.checked }
            AppButton(
                onClick = {
                    sending = true
                    val sendEmail      = emailEntries.any { it.checked }
                    val sendSms        = phoneEntries.any { it.checked }
                    val checkedEmails  = emailEntries.filter { it.checked }.map { it.value }
                    val checkedPhones  = phoneEntries.filter  { it.checked }.map { it.value }
                    android.util.Log.d("SendEstimate", "emailEntries size=${emailEntries.size} all=${emailEntries.map { "${it.value}:checked=${it.checked}" }}")
                    android.util.Log.d("SendEstimate", "sending with emails: $checkedEmails phones: $checkedPhones")
                    vm.send(estimateId, sendSms, sendEmail, checkedEmails, checkedPhones)
                },
                label = "Send for Signature",
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = hasRecipient && !sending,
                loading = sending,
                leadingIcon = Icons.Default.Draw
            )
        }
    }
}
