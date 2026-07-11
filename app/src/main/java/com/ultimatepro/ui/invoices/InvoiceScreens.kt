package com.ultimatepro.ui.invoices

import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.*
import com.ultimatepro.ui.common.*
import com.ultimatepro.ui.settings.ReviewPlatformViewModel
import com.ultimatepro.ui.estimates.CustomerPickerSheet
import com.ultimatepro.ui.estimates.EditableLineItem
import com.ultimatepro.ui.estimates.SignatureCanvas
import com.ultimatepro.ui.pricebook.PickedEntry
import com.ultimatepro.ui.pricebook.PricebookPickerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

// ─── Invoice ViewModel ────────────────────────────────────────────────────
@HiltViewModel
class InvoiceViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {
    private val _invs = MutableStateFlow<List<Invoice>>(emptyList()); val invoices = _invs.asStateFlow()
    private val _sel  = MutableStateFlow<Invoice?>(null);             val selected  = _sel.asStateFlow()
    private val _l    = MutableStateFlow(true);                       val loading   = _l.asStateFlow()
    private val _msg  = MutableStateFlow<String?>(null);              val message   = _msg.asStateFlow()
    private val _qr       = MutableStateFlow<String?>(null);              val qrUrl     = _qr.asStateFlow()
    private val _customer = MutableStateFlow<Customer?>(null);            val customer  = _customer.asStateFlow()

    // ScanPay state
    private val _spQr     = MutableStateFlow<ScanPayQrResponse?>(null);     val scanPayQr     = _spQr.asStateFlow()
    private val _spLink   = MutableStateFlow<ScanPayLinkResponse?>(null);   val scanPayLink   = _spLink.asStateFlow()
    private val _spStatus = MutableStateFlow<ScanPayStatusResponse?>(null); val scanPayStatus = _spStatus.asStateFlow()
    private val _spLoading = MutableStateFlow(false);                        val scanPayLoading = _spLoading.asStateFlow()

    init { load() }

    fun load() { viewModelScope.launch { _l.value = true; when (val r = repo.getInvoices()) { is Result.Success -> _invs.value = r.data.invoices; else -> {} }; _l.value = false } }
    fun loadInv(id: String) { viewModelScope.launch { when (val r = repo.getInvoice(id)) { is Result.Success -> _sel.value = r.data; else -> {} } } }
    fun send(id: String, sms: Boolean = true, email: Boolean = true, emails: List<String> = emptyList(), phones: List<String> = emptyList()) {
        viewModelScope.launch {
            when (val r = repo.sendInvoice(id, sms, email, emails, phones)) {
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
                            else -> "Invoice sent! (some channels unavailable)"
                        }
                        else -> "Invoice sent!"
                    }
                }
                is Result.Error -> _msg.value = r.message
            }
        }
    }

    fun sign(id: String, signature: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            when (val r = repo.signInvoice(id, signature)) {
                is Result.Success -> { loadInv(id); onDone(true) }
                is Result.Error   -> { _msg.value = r.message; onDone(false) }
            }
        }
    }

    // P2.27 #3: a payment beyond the balance returns 409 'overpayment' — surface onNeedsConfirm
    // (the screen shows a confirm dialog) instead of a plain error; confirmOverpayment resends it.
    fun recordPayment(id: String, method: String, amount: Double?, notes: String? = null,
                      confirmOverpayment: Boolean = false, onNeedsConfirm: () -> Unit = {}, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = repo.recordInvoicePayment(id, method, amount, notes, confirmOverpayment)) {
                is Result.Success -> { loadInv(id); _msg.value = "Payment recorded!"; onDone() }
                is Result.Error   -> if (r.code == 409 && r.message == "overpayment") onNeedsConfirm() else _msg.value = r.message
            }
        }
    }

    fun sendReceipt(id: String, sms: Boolean = true, email: Boolean = true, emails: List<String> = emptyList(), phones: List<String> = emptyList(), sendReviewRequest: Boolean = false, onDone: () -> Unit) {
        viewModelScope.launch { when (val r = repo.sendInvoiceReceipt(id, sms, email, emails, phones, sendReviewRequest)) { is Result.Success -> { _msg.value = "Receipt sent!"; onDone() }; is Result.Error -> _msg.value = r.message } }
    }

    fun loadQr(id: String) {
        viewModelScope.launch { when (val r = repo.getInvoiceScanpayQr(id)) { is Result.Success -> _qr.value = r.data["qr_url"] as? String; is Result.Error -> _msg.value = r.message } }
    }

    fun createScanPayQr(invoiceId: String, amount: Double) {
        viewModelScope.launch {
            _spLoading.value = true
            when (val r = repo.createScanPayQr(invoiceId, amount)) {
                is Result.Success -> _spQr.value = r.data
                is Result.Error   -> _msg.value = r.message
            }
            _spLoading.value = false
        }
    }

    fun createScanPayLink(invoiceId: String, amount: Double, customerPhone: String? = null,
                          customerEmail: String? = null, method: String = "sms") {
        viewModelScope.launch {
            _spLoading.value = true
            when (val r = repo.createScanPayLink(invoiceId, amount, customerPhone, customerEmail, method)) {
                is Result.Success -> _spLink.value = r.data
                is Result.Error   -> _msg.value = r.message
            }
            _spLoading.value = false
        }
    }

    fun pollScanPayStatus(invoiceId: String) {
        viewModelScope.launch {
            when (val r = repo.getScanPayStatus(invoiceId)) {
                is Result.Success -> _spStatus.value = r.data
                is Result.Error   -> {}
            }
        }
    }

    fun clearScanPay() {
        _spQr.value = null; _spLink.value = null; _spStatus.value = null
    }

    fun addLineItems(invoiceId: String, entries: Collection<PickedEntry>) {
        viewModelScope.launch {
            val current = _sel.value ?: return@launch
            val newItems = entries.map { e ->
                mapOf("name" to e.item.name, "description" to e.item.description,
                    "quantity" to e.qty.toDouble(), "unit_price" to e.effectivePrice,
                    "item_type" to e.item.item_type, "taxable" to e.item.taxable,
                    "tax_rate" to 0.0, "image_url" to e.item.image_url,
                    "pricebook_id" to e.item.id, "price_overridden" to e.priceOverridden,
                    "total" to (e.qty.toDouble() * e.effectivePrice))
            }
            val existingItems = current.line_items.map { li ->
                mapOf("name" to li.name, "description" to li.description,
                    "quantity" to li.quantity, "unit_price" to li.unit_price,
                    "item_type" to li.item_type, "taxable" to li.taxable,
                    "tax_rate" to li.tax_rate, "image_url" to li.image_url,
                    "pricebook_id" to li.pricebook_id, "price_overridden" to li.price_overridden,
                    "total" to li.total)
            }
            when (val r = repo.updateInvoice(invoiceId, mapOf("line_items" to existingItems + newItems))) {
                is Result.Success -> { _sel.value = r.data; _msg.value = "Item added" }
                is Result.Error   -> _msg.value = r.message
            }
        }
    }

    // P2.40: persist inline line-item edits (remove / price edit). The composable builds the
    // full line_items array; a 400 (money guard: new total < money already collected) surfaces
    // the server's "already collected — refund/void first" message; a 200 whose body carries
    // requires_resign means editing a SIGNED invoice cleared the signature -> onResign re-prompts.
    fun saveLineItems(invoiceId: String, lineItems: List<Map<String, Any?>>, onResign: () -> Unit = {}, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            when (val r = repo.updateInvoice(invoiceId, mapOf("line_items" to lineItems))) {
                is Result.Success -> {
                    val resign = r.data.requires_resign  // read off the PUT body before the refresh
                    loadInv(invoiceId)                    // re-fetch so the JOINed customer name + totals are current
                    if (resign) { _msg.value = "Items changed — the signature was cleared. Please re-sign."; onResign() }
                    else _msg.value = "Invoice updated"
                    onSuccess()
                }
                is Result.Error -> _msg.value = r.message   // 400 money guard: stays in edit mode
            }
        }
    }

    fun createDraftInvoice(customerId: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            when (val r = repo.createInvoice(mapOf("customer_id" to customerId))) {
                is Result.Success -> onDone(r.data.id)
                is Result.Error   -> _msg.value = r.message
            }
        }
    }

    fun updateCustomerContact(customerId: String, field: String, value: String) {
        viewModelScope.launch { repo.updateCustomer(customerId, mapOf(field to value)) }
    }

    fun loadCustomer(customerId: String) { viewModelScope.launch { when (val r = repo.getCustomer(customerId)) { is Result.Success -> _customer.value = r.data; else -> {} } } }
    fun addContact(customerId: String, type: String, value: String, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = repo.addCustomerContact(customerId, type, value)) {
                is Result.Success -> { loadCustomer(customerId); onDone() }
                is Result.Error   -> _msg.value = r.message
            }
        }
    }
    fun stopFollowup(id: String) {
        viewModelScope.launch {
            when (val r = repo.stopInvoiceFollowup(id)) {
                is Result.Success -> { loadInv(id); _msg.value = "Follow-up reminders stopped" }
                is Result.Error   -> _msg.value = r.message
            }
        }
    }

    fun resetFollowup(id: String) {
        viewModelScope.launch {
            when (val r = repo.resetInvoiceFollowup(id)) {
                is Result.Success -> { loadInv(id); _msg.value = "Follow-up reminders resumed" }
                is Result.Error   -> _msg.value = r.message
            }
        }
    }

    fun clearMsg() { _msg.value = null }
}

// ─── SCREEN: Invoice List ─────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    onInvoice: (String) -> Unit,
    onBack: () -> Unit,
    onNewInvoice: (String) -> Unit = {},
    vm: InvoiceViewModel = hiltViewModel()
) {
    val invoices by vm.invoices.collectAsState(); val loading by vm.loading.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Invoices", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPicker = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Invoice") }
            )
        }
    ) { padding ->
        when {
            loading && invoices.isEmpty() -> LoadingView()
            invoices.isEmpty() -> EmptyView("No invoices yet", Icons.Default.Receipt)
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(invoices, key = { it.id }) { inv ->
                    InvoiceRow(inv) { onInvoice(inv.id) }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    if (showPicker) {
        CustomerPickerSheet(
            onPick    = { cust -> showPicker = false; onNewInvoice(cust.id) },
            onDismiss = { showPicker = false }
        )
    }
}

// ─── Reusable row (list screen + Customer Detail Invoices tab) ────────────
@Composable
fun InvoiceRow(inv: Invoice, onClick: () -> Unit) {
    val sc = AppColors.invoiceStatus(inv.status)
    Card(onClick = onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(inv.invoice_number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(inv.customerName, fontWeight = FontWeight.SemiBold)
                inv.due_date?.let { Text("Due: $it", style = MaterialTheme.typography.bodySmall, color = if (inv.is_overdue) AppColors.Red else MaterialTheme.colorScheme.onSurfaceVariant) }
                if (inv.isSigned) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Draw, null, tint = AppColors.Green, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("Signed", style = MaterialTheme.typography.labelSmall, color = AppColors.Green) }
            }
            Column(horizontalAlignment = Alignment.End) { Text(formatMoney(inv.total), fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); StatusBadge(inv.status.replaceFirstChar { it.uppercase() }, sc, small = true) }
        }
    }
}

// ─── SCREEN: Invoice New (create draft + navigate) ─────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceNewScreen(
    customerId: String,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    vm: InvoiceViewModel = hiltViewModel()
) {
    val msg by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.clearMsg() } }
    LaunchedEffect(customerId) {
        if (customerId.isNotBlank()) {
            vm.createDraftInvoice(customerId) { invoiceId -> onCreated(invoiceId) }
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snack) },
        topBar = { TopAppBar(title = { Text("New Invoice", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

// ─── SCREEN: Invoice Detail ───────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    id: String, onBack: () -> Unit,
    onSign: (String) -> Unit = {}, onPayment: (String) -> Unit = {},
    onReceipt: (String) -> Unit = {}, onSend: (String) -> Unit = {},
    onAddItem: () -> Unit = {},
    pickerVm: PricebookPickerViewModel? = null,
    vm: InvoiceViewModel = hiltViewModel()
) {
    val inv    by vm.selected.collectAsState()
    val msg    by vm.message.collectAsState()
    val picked by (pickerVm?.picked?.collectAsState() ?: remember { mutableStateOf(emptyMap<String, PickedEntry>()) })
    val authVm: com.ultimatepro.ui.auth.AuthViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val perms by authVm.permissions.collectAsState()
    val role by authVm.role.collectAsState()
    val snack  = remember { SnackbarHostState() }
    var showPaymentOptions by remember { mutableStateOf(false) }
    // P2.40: inline line-item edit mode + the editable draft (seeded from the invoice on open).
    var editing by remember(id) { mutableStateOf(false) }
    val draft = remember(id) { mutableStateListOf<EditItem>() }
    var showLinkPicker by remember(id) { mutableStateOf(false) } // P2.41 #4
    // Picker is activity-scoped; clear stale picks from a previous caller on mount.
    LaunchedEffect(Unit) { pickerVm?.clearPicked() }
    LaunchedEffect(id) { vm.loadInv(id) }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.clearMsg() } }
    LaunchedEffect(picked) {
        if (picked.isNotEmpty()) {
            vm.addLineItems(id, picked.values)
            pickerVm?.clearPicked()
        }
    }

    if (showPaymentOptions) {
        val i = inv
        if (i != null) {
            ModalBottomSheet(onDismissRequest = { showPaymentOptions = false }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Collect Payment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                    Button(
                        onClick = { showPaymentOptions = false; onPayment(i.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green)
                    ) {
                        Icon(Icons.Default.Payment, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Charge on site", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { showPaymentOptions = false; onSend(i.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Link, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Send payment link")
                    }
                }
            }
        }
    }

    if (showLinkPicker) {
        val i = inv
        if (i != null) {
            val spLoading by vm.scanPayLoading.collectAsState()
            SendLinkPickerDialog(
                amount = i.balance_due, defaultEmail = i.cust_email, defaultPhone = i.cust_phone, loading = spLoading,
                onDismiss = { showLinkPicker = false },
                onSend = { method, email, phone -> vm.createScanPayLink(i.id, i.balance_due, phone, email, method); showLinkPicker = false }
            )
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
        TopAppBar(title = { Text(inv?.invoice_number ?: "Invoice", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = { inv?.let { i -> if (i.status != "paid") IconButton(onClick = { onSend(i.id) }) { Icon(Icons.Default.Send, null) } } })
    }) { padding ->
        inv?.let { i ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                // Header
                item { CRMCard {
                    InfoRow(Icons.Default.Person, "Customer", i.customerName)
                    InfoRow(Icons.Default.Info, "Status", i.status.replaceFirstChar { it.uppercase() })
                    i.due_date?.let { InfoRow(Icons.Default.CalendarToday, "Due", it) }
                    if (i.isSigned) InfoRow(Icons.Default.Draw, "Signed", i.customer_signature_date?.take(10) ?: "Yes")
                    i.payment_link?.let { InfoRow(Icons.Default.Link, "Payment Link", it) }
                } }
                // Services / Materials / Discounts / Others
                val svcs = i.line_items.filter { it.item_type in listOf("service","labor") }
                val mats = i.line_items.filter { it.item_type == "material" }
                val discs = i.line_items.filter { it.item_type == "discount" }
                val others = i.line_items.filter { it.item_type !in listOf("service","labor","material","discount") }
                if (editing) {
                    // P2.40: inline editor — per-row remove + name/qty/price edit + add, one flat list.
                    item {
                        EditableInvoiceItemsCard(
                            draft = draft,
                            amountPaid = i.amount_paid,
                            onAdd = { draft.add(EditItem("", "1", "0.00", null, "service", false, 0.0, null, null, false)) },
                            onRemove = { idx -> if (idx in draft.indices) draft.removeAt(idx) },
                            onChange = { idx, item -> if (idx in draft.indices) draft[idx] = item },
                            onCancel = { editing = false; draft.clear() },
                            onSave = {
                                val payload = draft.filter { it.name.isNotBlank() }.map { it.toApiMap() }
                                vm.saveLineItems(i.id, payload, onResign = { onSign(i.id) }, onSuccess = { editing = false; draft.clear() })
                            }
                        )
                    }
                } else {
                    if (svcs.isNotEmpty()) item { InvLineItemsCard("LABOR", svcs) }
                    if (mats.isNotEmpty()) item { InvLineItemsCard("MATERIALS", mats) }
                    if (discs.isNotEmpty()) item { InvLineItemsCard("DISCOUNTS", discs, isDiscount = true) }
                    if (others.isNotEmpty()) item { InvLineItemsCard("ITEMS", others) }
                    // Edit items (P2.40) + Add Item
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (i.status != "paid" && i.status != "void") {
                                OutlinedButton(
                                    onClick = { draft.clear(); draft.addAll(i.line_items.map { EditItem.from(it) }); editing = true },
                                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                                ) { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Edit items") }
                            }
                            OutlinedButton(
                                onClick = onAddItem,
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                            ) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Add Item") }
                        }
                    }
                }
                // Totals
                item { CRMCard {
                    SectionLabel("SUMMARY")
                    listOf("Subtotal" to formatMoney(i.subtotal), "Tax" to formatMoney(i.tax_total)).forEach { (l,v) -> Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(l); Text(v) } }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total", fontWeight = FontWeight.Bold); Text(formatMoney(i.total), fontWeight = FontWeight.Bold, color = AppColors.Blue) }
                    if (i.deposit_paid > 0) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Deposit paid", color = AppColors.Green)
                                i.deposit_paid_at?.let { Text("Paid on ${it.take(10)}", style = MaterialTheme.typography.labelSmall, color = AppColors.Green) }
                            }
                            Text("-${formatMoney(i.deposit_paid)}", color = AppColors.Green, fontWeight = FontWeight.SemiBold)
                        }
                        val additionalPaid = i.amount_paid - i.deposit_paid
                        if (additionalPaid > 0.01) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Paid", color = AppColors.Green); Text(formatMoney(additionalPaid), color = AppColors.Green) }
                    } else if (i.amount_paid > 0) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Paid", color = AppColors.Green); Text(formatMoney(i.amount_paid), color = AppColors.Green) }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Balance Due", fontWeight = FontWeight.Bold); Text(formatMoney(i.balance_due), fontWeight = FontWeight.Bold, color = if (i.balance_due > 0) AppColors.Red else AppColors.Green) }
                } }
                // Notes
                i.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    item { CRMCard { SectionLabel("NOTES"); Text(notes, style = MaterialTheme.typography.bodyMedium) } }
                }
                // Terms (P2.17 PART 2 — company default or per-document override)
                i.terms?.takeIf { it.isNotBlank() }?.let { terms ->
                    item { CRMCard { SectionLabel("TERMS"); Text(terms, style = MaterialTheme.typography.bodyMedium) } }
                }
                // Actions
                item { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (i.status) {
                        "draft", "sent" -> {
                            Button(onClick = { onSign(i.id) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Draw, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Get Signature") }
                            OutlinedButton(onClick = { onSend(i.id) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Send, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Send Invoice") }
                        }
                        "signed" -> {
                            if (com.ultimatepro.domain.model.canUi(role, perms, "payments_refunds", "edit_self"))
                            Button(
                                onClick = { showPaymentOptions = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green)
                            ) { Icon(Icons.Default.Payment, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Charge Payment", fontWeight = FontWeight.SemiBold) }
                        }
                        "paid" -> {
                            if (!i.receipt_sent) Button(onClick = { onReceipt(i.id) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Receipt, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Send Receipt") }
                            else Card(colors = CardDefaults.cardColors(containerColor = AppColors.Green.copy(alpha = 0.1f)), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = AppColors.Green); Spacer(Modifier.width(8.dp)); Text("Payment complete — receipt sent", color = AppColors.Green, fontWeight = FontWeight.SemiBold) } }
                        }
                        "partial", "partially_paid" -> {
                            Button(onClick = { onReceipt(i.id) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Receipt, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Send Partial Receipt") }
                        }
                    }
                    // P2.37: ScanPay payment link — available for ANY invoice with a
                    // balance due, regardless of signature state (David collects after
                    // approval too). Creates the ScanPay invoice + texts the pay link.
                    if (i.status != "paid" && i.balance_due > 0 &&
                        com.ultimatepro.domain.model.canUi(role, perms, "payments_refunds", "edit_self")) {
                        val spLoading by vm.scanPayLoading.collectAsState()
                        OutlinedButton(
                            onClick = { showLinkPicker = true },   // P2.41 #4: open the picker
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !spLoading
                        ) {
                            if (spLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else { Icon(Icons.Default.Link, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Send Payment Link") }
                        }
                    }
                } }
                // Follow-up reminders status
                if (i.status in listOf("sent", "overdue")) {
                    item {
                        var showStopDialog   by remember { mutableStateOf(false) }
                        var showResumeDialog by remember { mutableStateOf(false) }
                        CRMCard {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (i.followup_stopped) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = if (i.followup_stopped) MaterialTheme.colorScheme.onSurfaceVariant else AppColors.Blue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Follow-up Reminders", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        when {
                                            i.followup_stopped -> "Stopped"
                                            i.followup_count == 0 -> "No reminders sent yet"
                                            else -> "${i.followup_count} reminder${if (i.followup_count > 1) "s" else ""} sent"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (i.followup_stopped) {
                                    TextButton(onClick = { showResumeDialog = true }) { Text("Resume") }
                                } else {
                                    TextButton(onClick = { showStopDialog = true }) { Text("Stop") }
                                }
                            }
                        }
                        if (showStopDialog) {
                            AlertDialog(
                                onDismissRequest = { showStopDialog = false },
                                title = { Text("Stop Follow-up Reminders") },
                                text = { Text("No more automatic reminders will be sent for this invoice. You can resume them later.") },
                                confirmButton = { TextButton(onClick = { vm.stopFollowup(i.id); showStopDialog = false }) { Text("Stop") } },
                                dismissButton = { TextButton(onClick = { showStopDialog = false }) { Text("Cancel") } }
                            )
                        }
                        if (showResumeDialog) {
                            AlertDialog(
                                onDismissRequest = { showResumeDialog = false },
                                title = { Text("Resume Follow-up Reminders") },
                                text = { Text("Automatic reminders will resume for this invoice and the count will be reset.") },
                                confirmButton = { TextButton(onClick = { vm.resetFollowup(i.id); showResumeDialog = false }) { Text("Resume") } },
                                dismissButton = { TextButton(onClick = { showResumeDialog = false }) { Text("Cancel") } }
                            )
                        }
                    }
                }
            }
        } ?: LoadingView()
    }
}

// ─── P2.40: inline invoice line-item editor ────────────────────────────────
// A lightweight editable row model (qty/price held as text for smooth typing; parsed for
// the total + the API). Mirrors the estimate builder's inline edit, kept local to invoices.
private data class EditItem(
    val name: String, val qtyText: String, val priceText: String,
    val description: String?, val item_type: String, val taxable: Boolean,
    val tax_rate: Double, val image_url: String?, val pricebook_id: String?, val price_overridden: Boolean
) {
    val qty get() = qtyText.toDoubleOrNull() ?: 1.0
    val price get() = priceText.toDoubleOrNull() ?: 0.0
    val lineTotal get() = qty * price
    fun toApiMap(): Map<String, Any?> = mapOf(
        "name" to name, "description" to description, "quantity" to qty, "unit_price" to price,
        "item_type" to item_type, "taxable" to taxable, "tax_rate" to tax_rate,
        "image_url" to image_url, "pricebook_id" to pricebook_id,
        "price_overridden" to price_overridden, "total" to lineTotal)
    companion object {
        fun from(li: LineItem): EditItem {
            val q = if (li.quantity == li.quantity.toLong().toDouble()) li.quantity.toLong().toString() else li.quantity.toString()
            return EditItem(li.name, q, "%.2f".format(li.unit_price), li.description, li.item_type,
                li.taxable, li.tax_rate, li.image_url, li.pricebook_id, li.price_overridden)
        }
    }
}

@Composable
private fun EditableInvoiceItemsCard(
    draft: List<EditItem>, amountPaid: Double,
    onAdd: () -> Unit, onRemove: (Int) -> Unit, onChange: (Int, EditItem) -> Unit,
    onCancel: () -> Unit, onSave: () -> Unit
) {
    CRMCard {
        SectionLabel("LINE ITEMS")
        draft.forEachIndexed { idx, it ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(it.name, { v -> onChange(idx, it.copy(name = v)) },
                        label = { Text("Item") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(it.qtyText, { v -> onChange(idx, it.copy(qtyText = v)) },
                            label = { Text("Qty") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(84.dp), shape = RoundedCornerShape(8.dp))
                        OutlinedTextField(it.priceText, { v -> onChange(idx, it.copy(priceText = v, price_overridden = true)) },
                            label = { Text("Price") }, prefix = { Text("$") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(120.dp), shape = RoundedCornerShape(8.dp))
                        Spacer(Modifier.weight(1f))
                        Text(formatMoney(it.lineTotal), fontWeight = FontWeight.SemiBold)
                    }
                }
                IconButton(onClick = { onRemove(idx) }) { Icon(Icons.Default.Close, "Remove", tint = AppColors.Red) }
            }
            HorizontalDivider()
        }
        TextButton(onClick = onAdd, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add item")
        }
        val editTotal = draft.sumOf { if (it.item_type == "discount") -it.lineTotal else it.lineTotal }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", fontWeight = FontWeight.Bold); Text(formatMoney(editTotal), fontWeight = FontWeight.Bold, color = AppColors.Blue)
        }
        if (amountPaid > 0) {
            Text("${formatMoney(amountPaid)} already collected — the new total can't go below that (refund or void first).",
                style = MaterialTheme.typography.labelSmall, color = AppColors.Orange, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
            Button(onClick = onSave, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Save changes") }
        }
    }
}

// P2.41 #4: pre-send picker for "Send Payment Link" (recipient + channel), so it no longer
// fires an SMS immediately. Mirrors the send-invoice picker; single recipient per channel
// (the ScanPay-link backend takes one phone + one email).
@Composable
private fun SendLinkPickerDialog(
    amount: Double, defaultEmail: String?, defaultPhone: String?, loading: Boolean,
    onDismiss: () -> Unit, onSend: (method: String, email: String?, phone: String?) -> Unit
) {
    var sendEmail by remember { mutableStateOf(!defaultEmail.isNullOrBlank()) }
    var sendSms   by remember { mutableStateOf(!defaultPhone.isNullOrBlank()) }
    var email by remember { mutableStateOf(defaultEmail ?: "") }
    var phone by remember { mutableStateOf(defaultPhone ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Payment Link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Send a secure payment link for ${formatMoney(amount)}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(sendEmail, { sendEmail = it }); Text("Email") }
                if (sendEmail) OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(sendSms, { sendSms = it }); Text("SMS") }
                if (sendSms) OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = {
            Button(
                enabled = !loading && (sendEmail || sendSms) && (!sendEmail || email.isNotBlank()) && (!sendSms || phone.isNotBlank()),
                onClick = {
                    val method = if (sendEmail && sendSms) "both" else if (sendEmail) "email" else "sms"
                    onSend(method, if (sendEmail) email.trim() else null, if (sendSms) phone.trim() else null)
                }
            ) { Text(if (loading) "Sending…" else "Send Link") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun InvLineItemsCard(label: String, items: List<LineItem>, isDiscount: Boolean = false) {
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
                Text(
                    text = if (isDiscount) "-${formatMoney(li.total)}" else formatMoney(li.total),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDiscount) AppColors.Red else Color.Unspecified
                )
            }
            HorizontalDivider()
        }
    }
}

// ─── SCREEN: Invoice Signature ────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceSignScreen(
    invoiceId: String, onBack: () -> Unit, onSigned: () -> Unit,
    vm: InvoiceViewModel = hiltViewModel()
) {
    val inv    by vm.selected.collectAsState()
    val msg    by vm.message.collectAsState()
    val snack  = remember { SnackbarHostState() }
    var agreed by remember { mutableStateOf(false) }
    var signing by remember { mutableStateOf(false) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke = remember { mutableStateListOf<Offset>() }

    LaunchedEffect(invoiceId) { vm.loadInv(invoiceId) }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.clearMsg() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
        TopAppBar(title = { Text("Customer Signature", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            inv?.let { i ->
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(i.invoice_number, fontWeight = FontWeight.Bold, color = AppColors.Blue)
                        Text(i.customerName)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Balance Due", fontWeight = FontWeight.SemiBold); Text(formatMoney(i.balance_due), fontWeight = FontWeight.Bold, color = AppColors.Red) }
                    }
                }
            }
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text("I find and agree that all work has been completed satisfactorily.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = agreed, onCheckedChange = { agreed = it }); Text("I agree that all work has been completed satisfactorily") }
            Text("Sign Below", fontWeight = FontWeight.SemiBold)
            SignatureCanvas(strokes = strokes, currentStroke = currentStroke)
            val density = LocalDensity.current
            Button(onClick = {
                if (strokes.isEmpty()) return@Button
                signing = true
                val w = with(density) { 380.dp.toPx().toInt() }; val h = with(density) { 200.dp.toPx().toInt() }
                val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val ac = android.graphics.Canvas(bm); ac.drawColor(android.graphics.Color.WHITE)
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = 6f; style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND }
                strokes.forEach { stroke -> val path = android.graphics.Path(); stroke.forEachIndexed { i, pt -> if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y) }; ac.drawPath(path, p) }
                val baos = ByteArrayOutputStream(); bm.compress(Bitmap.CompressFormat.PNG, 90, baos)
                val b64 = "data:image/png;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                vm.sign(invoiceId, b64) { success ->
                    signing = false
                    if (success) onSigned()
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp), enabled = agreed && strokes.isNotEmpty() && !signing) {
                if (signing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("Confirm & Sign", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── SCREEN: Payment ──────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    invoiceId: String, onBack: () -> Unit, onPaid: (String) -> Unit,
    vm: InvoiceViewModel = hiltViewModel()
) {
    val inv    by vm.selected.collectAsState()
    val msg    by vm.message.collectAsState()
    val qrUrl  by vm.qrUrl.collectAsState()
    val snack  = remember { SnackbarHostState() }
    var method      by remember { mutableStateOf("cash") }
    var notes       by remember { mutableStateOf("") }
    var paying      by remember { mutableStateOf(false) }
    var showOverpay by remember { mutableStateOf(false) }   // P2.27 #3 overpayment confirm
    var showLinkPicker by remember { mutableStateOf(false) } // P2.41 #4
    var showQr      by remember { mutableStateOf(false) }
    var amountText  by remember { mutableStateOf("") }

    LaunchedEffect(invoiceId) { vm.loadInv(invoiceId) }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.clearMsg() } }
    LaunchedEffect(method) { if (method == "scanpay_qr") vm.loadQr(invoiceId) }
    // Pre-fill amount once invoice loads
    LaunchedEffect(inv) { if (amountText.isBlank() && inv != null) amountText = "%.2f".format(inv!!.balance_due) }

    data class PayMethod(val id: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)
    val methods = listOf(
        PayMethod("scanpay_qr",   "ScanPay QR",    Icons.Default.QrCode,       Color(0xFF16a34a)),
        PayMethod("scanpay_link", "ScanPay Link",  Icons.Default.Link,          Color(0xFF1565C0)),
        PayMethod("cash",         "Cash",           Icons.Default.AttachMoney,  Color(0xFF2e7d32)),
        PayMethod("credit_card",  "Credit Card",   Icons.Default.CreditCard,    Color(0xFF1976D2)),
        PayMethod("check",        "Check",          Icons.Default.Description,  Color(0xFF5D4037)),
        PayMethod("venmo",        "Venmo/CashApp",  Icons.Default.PhoneAndroid, Color(0xFF6A1B9A))
    )

    Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
        TopAppBar(title = { Text("Process Payment", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // Amount — editable, pre-filled with balance due
            inv?.let { i ->
                val enteredAmount = amountText.toDoubleOrNull() ?: 0.0
                val remaining = i.balance_due - enteredAmount
                Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Blue.copy(alpha = 0.08f))) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Charge Amount", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Amount") },
                            prefix = { Text("$") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        if (remaining > 0.01) {
                            Spacer(Modifier.height(6.dp))
                            Text("Remaining balance: ${formatMoney(remaining)}", style = MaterialTheme.typography.bodySmall, color = AppColors.Orange)
                        }
                        Text(i.invoice_number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            Text("Select Payment Method", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // Method grid (2 cols)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                methods.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { m ->
                            val sel = method == m.id
                            Card(onClick = { method = m.id }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                                border = if (sel) androidx.compose.foundation.BorderStroke(2.dp, m.color) else null,
                                colors = CardDefaults.cardColors(containerColor = if (sel) m.color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(m.icon, null, tint = if (sel) m.color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                                    Text(m.label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodySmall, color = if (sel) m.color else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Notes (only for non-scanpay methods)
            if (method !in listOf("scanpay_qr", "scanpay_link")) {
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(10.dp))
            }

            Spacer(Modifier.height(8.dp))

            val chargeAmount = amountText.toDoubleOrNull() ?: (inv?.balance_due ?: 0.0)
            val spLoading by vm.scanPayLoading.collectAsState()

            when (method) {
                "scanpay_qr" -> Button(
                    onClick = { vm.createScanPayQr(invoiceId, chargeAmount) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !spLoading && chargeAmount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16a34a))
                ) {
                    if (spLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.QrCode, null); Spacer(Modifier.width(8.dp)); Text("Generate QR — ${formatMoney(chargeAmount)}", fontWeight = FontWeight.Bold) }
                }
                "scanpay_link" -> Button(
                    onClick = { showLinkPicker = true },   // P2.41 #4: open the picker
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !spLoading && chargeAmount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) {
                    if (spLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Link, null); Spacer(Modifier.width(8.dp)); Text("Send Payment Link — ${formatMoney(chargeAmount)}", fontWeight = FontWeight.Bold) }
                }
                else -> Button(
                    onClick = { paying = true; vm.recordPayment(invoiceId, method, chargeAmount, notes.ifBlank { null },
                        onNeedsConfirm = { paying = false; showOverpay = true },
                        onDone = { paying = false; onPaid(invoiceId) }) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !paying && chargeAmount > 0
                ) {
                    if (paying) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("Record Payment — ${formatMoney(chargeAmount)}", fontWeight = FontWeight.Bold) }
                }
            }

            // P2.27 #3: overpayment confirm — payment exceeds the remaining balance.
            if (showOverpay) {
                val bal = inv?.balance_due ?: 0.0
                AlertDialog(
                    onDismissRequest = { showOverpay = false },
                    title = { Text("Payment exceeds balance", fontWeight = FontWeight.Bold) },
                    text = { Text("This payment of ${formatMoney(chargeAmount)} exceeds the remaining balance of ${formatMoney(bal)} by ${formatMoney(chargeAmount - bal)}. Record it anyway?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showOverpay = false; paying = true
                            vm.recordPayment(invoiceId, method, chargeAmount, notes.ifBlank { null },
                                confirmOverpayment = true, onDone = { paying = false; onPaid(invoiceId) })
                        }) { Text("Record anyway", fontWeight = FontWeight.SemiBold) }
                    },
                    dismissButton = { TextButton(onClick = { showOverpay = false }) { Text("Cancel") } }
                )
            }

            // P2.41 #4: pre-send picker for the ScanPay payment link.
            if (showLinkPicker) {
                SendLinkPickerDialog(
                    amount = chargeAmount, defaultEmail = inv?.cust_email, defaultPhone = inv?.cust_phone, loading = spLoading,
                    onDismiss = { showLinkPicker = false },
                    onSend = { m, email, phone -> vm.createScanPayLink(invoiceId, chargeAmount, phone, email, m); showLinkPicker = false }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── ScanPay QR dialog ─────────────────────────────────────────────────
    val spQr     by vm.scanPayQr.collectAsState()
    val spStatus by vm.scanPayStatus.collectAsState()

    spQr?.let { qrResp ->
        ScanPayQrDialog(
            qrDataUrl    = qrResp.qr_data_url,
            paymentUrl   = qrResp.payment_url,
            amount       = amountText.toDoubleOrNull() ?: (inv?.balance_due ?: 0.0),
            invoiceId    = invoiceId,
            pollInterval = 3000L,
            vm           = vm,
            onPaid       = { vm.clearScanPay(); onPaid(invoiceId) },
            onDismiss    = { vm.clearScanPay() }
        )
    }

    // ── ScanPay Link dialog ───────────────────────────────────────────────
    val spLink by vm.scanPayLink.collectAsState()

    spLink?.let { linkResp ->
        ScanPayLinkDialog(
            paymentUrl   = linkResp.payment_url,
            smsSent      = linkResp.sms_sent,
            phoneUsed    = linkResp.phone_used,
            amount       = amountText.toDoubleOrNull() ?: (inv?.balance_due ?: 0.0),
            invoiceId    = invoiceId,
            pollInterval = 5000L,
            vm           = vm,
            onPaid       = { vm.clearScanPay(); onPaid(invoiceId) },
            onDismiss    = { vm.clearScanPay() }
        )
    }
}

// ── ScanPay QR full-screen dialog ─────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanPayQrDialog(
    qrDataUrl: String,
    paymentUrl: String,
    amount: Double,
    invoiceId: String,
    pollInterval: Long,
    vm: InvoiceViewModel,
    onPaid: () -> Unit,
    onDismiss: () -> Unit
) {
    val spStatus by vm.scanPayStatus.collectAsState()
    val clipboard = LocalClipboardManager.current

    // Decode base64 QR to Bitmap
    val qrBitmap = remember(qrDataUrl) {
        runCatching {
            val b64 = qrDataUrl.substringAfter("base64,")
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    // Poll for payment status
    LaunchedEffect(invoiceId) {
        while (true) {
            delay(pollInterval)
            vm.pollScanPayStatus(invoiceId)
        }
    }
    LaunchedEffect(spStatus) {
        if (spStatus?.status == "paid") onPaid()
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Scan to Pay", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(formatMoney(amount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.Green)

                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(240.dp).clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(Modifier.size(240.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.QrCode, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Waiting for payment…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(paymentUrl)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Link", style = MaterialTheme.typography.bodySmall)
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}

// ── ScanPay Link dialog ───────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanPayLinkDialog(
    paymentUrl: String,
    smsSent: Boolean,
    phoneUsed: String?,
    amount: Double,
    invoiceId: String,
    pollInterval: Long,
    vm: InvoiceViewModel,
    onPaid: () -> Unit,
    onDismiss: () -> Unit
) {
    val spStatus by vm.scanPayStatus.collectAsState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(invoiceId) {
        while (true) {
            delay(pollInterval)
            vm.pollScanPayStatus(invoiceId)
        }
    }
    LaunchedEffect(spStatus) {
        if (spStatus?.status == "paid") onPaid()
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.Link, null, Modifier.size(48.dp), tint = AppColors.Blue)
                Text("Payment Link Sent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(formatMoney(amount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.Blue)

                if (smsSent && phoneUsed != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = AppColors.Green.copy(alpha = 0.1f)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sms, null, tint = AppColors.Green, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("SMS sent to $phoneUsed", style = MaterialTheme.typography.bodySmall, color = AppColors.Green)
                        }
                    }
                }

                // Copyable link
                OutlinedTextField(
                    value = paymentUrl,
                    onValueChange = {},
                    label = { Text("Payment Link") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        IconButton(onClick = { clipboard.setText(AnnotatedString(paymentUrl)) }) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                        }
                    }
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Waiting for customer to pay…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}

// ─── SCREEN: Receipt ──────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(
    invoiceId: String, onBack: () -> Unit, onDone: (String?) -> Unit,
    vm: InvoiceViewModel = hiltViewModel(),
    reviewVm: ReviewPlatformViewModel = hiltViewModel()
) {
    val inv       by vm.selected.collectAsState()
    val customer  by vm.customer.collectAsState()
    val msg       by vm.message.collectAsState()
    val platforms by reviewVm.platforms.collectAsState()
    val snack     = remember { SnackbarHostState() }
    var sending   by remember { mutableStateOf(false) }
    var saveToProfile   by remember { mutableStateOf(true) }
    val hasActivePlatform = platforms.any { it.isActive }
    var sendReviewRequest by remember { mutableStateOf(true) }

    data class ContactEntry(val value: String, var checked: Boolean = true, var editText: String = value)

    val emailEntries = remember { mutableStateListOf<ContactEntry>() }
    val phoneEntries = remember { mutableStateListOf<ContactEntry>() }

    LaunchedEffect(customer, inv) {
        val emailPrimary = inv?.cust_email
        val emailExtras  = customer?.emails ?: emptyList()
        val allEmails = buildList {
            emailPrimary?.let { add(it) }
            emailExtras.filter { it != emailPrimary }.forEach { add(it) }
        }
        val existingEmailValues = emailEntries.map { it.value }.toSet()
        allEmails.filter { it !in existingEmailValues }.forEach { emailEntries.add(ContactEntry(it, true)) }

        val phonePrimary = inv?.cust_phone
        val phoneExtras  = customer?.phones ?: emptyList()
        val allPhones = buildList {
            phonePrimary?.let { add(it) }
            phoneExtras.filter { it != phonePrimary }.forEach { add(it) }
        }
        val existingPhoneValues = phoneEntries.map { it.value }.toSet()
        allPhones.filter { it !in existingPhoneValues }.forEach { phoneEntries.add(ContactEntry(it, true)) }
    }
    var showAddEmail by remember { mutableStateOf(false) }
    var showAddPhone by remember { mutableStateOf(false) }
    var newEmail by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    LaunchedEffect(invoiceId) { vm.loadInv(invoiceId) }
    LaunchedEffect(inv) { inv?.customer_id?.let { vm.loadCustomer(it) } }
    LaunchedEffect(msg) {
        msg?.let {
            snack.showSnackbar(it); vm.clearMsg()
            if (it.contains("receipt", true) || it.contains("sent", true)) sending = false
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
        TopAppBar(title = { Text("Payment Receipt", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Payment confirmation card
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Green.copy(alpha = 0.1f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, tint = AppColors.Green, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Payment Confirmed!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = AppColors.Green)
                    inv?.let { i ->
                        Spacer(Modifier.height(8.dp))
                        Text(formatMoney(i.amount_paid), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                        Text(i.payment_method?.replaceFirstChar { it.uppercase() } ?: "Payment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(i.invoice_number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                if (saveToProfile) inv?.customer_id?.let { cid -> vm.addContact(cid, "email", newEmail.trim()) {} }
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
                                if (saveToProfile) inv?.customer_id?.let { cid -> vm.addContact(cid, "phone", newPhone.trim()) {} }
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

            // Review request toggle (only shown if company has active platforms)
            if (hasActivePlatform) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppSwitch(checked = sendReviewRequest, onCheckedChange = { sendReviewRequest = it })
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Include review request", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text("Append a review link to the receipt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            val hasRecipient = emailEntries.any { it.checked } || phoneEntries.any { it.checked }
            Button(
                onClick = {
                    sending = true
                    val checkedEmails = emailEntries.filter { it.checked }.map { it.value }
                    val checkedPhones = phoneEntries.filter { it.checked }.map { it.value }
                    vm.sendReceipt(invoiceId, checkedPhones.isNotEmpty(), checkedEmails.isNotEmpty(), checkedEmails, checkedPhones, sendReviewRequest && hasActivePlatform) { sending = false; onDone(inv?.job_id) }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = hasRecipient && !sending
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("Send Receipt", fontWeight = FontWeight.Bold) }
            }

            OutlinedButton(onClick = { onDone(inv?.job_id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Done") }
        }
    }
}

@Composable
private fun ReceiptMethodCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, info: String, onClick: () -> Unit, modifier: Modifier) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, AppColors.Blue) else null,
        colors = CardDefaults.cardColors(containerColor = if (selected) AppColors.Blue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (selected) AppColors.Blue else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = if (selected) AppColors.Blue else MaterialTheme.colorScheme.onSurface)
            Text(info, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

// ─── SCREEN: Invoice Send ─────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceSendScreen(
    invoiceId: String, onBack: () -> Unit, onSent: () -> Unit,
    vm: InvoiceViewModel = hiltViewModel()
) {
    val inv      by vm.selected.collectAsState()
    val customer by vm.customer.collectAsState()
    val msg      by vm.message.collectAsState()
    val snack    = remember { SnackbarHostState() }
    var sending  by remember { mutableStateOf(false) }
    var saveToProfile by remember { mutableStateOf(true) }

    data class ContactEntry(val value: String, var checked: Boolean = true, var editText: String = value)

    val emailEntries = remember { mutableStateListOf<ContactEntry>() }
    val phoneEntries = remember { mutableStateListOf<ContactEntry>() }

    LaunchedEffect(customer, inv) {
        val emailPrimary = inv?.cust_email
        val emailExtras  = customer?.emails ?: emptyList()
        val allEmails = buildList {
            emailPrimary?.let { add(it) }
            emailExtras.filter { it != emailPrimary }.forEach { add(it) }
        }
        val existingEmailValues = emailEntries.map { it.value }.toSet()
        allEmails.filter { it !in existingEmailValues }.forEach { emailEntries.add(ContactEntry(it, true)) }

        val phonePrimary = inv?.cust_phone
        val phoneExtras  = customer?.phones ?: emptyList()
        val allPhones = buildList {
            phonePrimary?.let { add(it) }
            phoneExtras.filter { it != phonePrimary }.forEach { add(it) }
        }
        val existingPhoneValues = phoneEntries.map { it.value }.toSet()
        allPhones.filter { it !in existingPhoneValues }.forEach { phoneEntries.add(ContactEntry(it, true)) }
    }
    var showAddEmail by remember { mutableStateOf(false) }
    var showAddPhone by remember { mutableStateOf(false) }
    var newEmail by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    LaunchedEffect(invoiceId) { vm.loadInv(invoiceId) }
    LaunchedEffect(inv) { inv?.customer_id?.let { vm.loadCustomer(it) } }
    LaunchedEffect(msg) {
        msg?.let {
            snack.showSnackbar(it)
            vm.clearMsg()
            sending = false  // always re-enable button (success or error)
            if (it.contains("sent", ignoreCase = true)) onSent()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
        TopAppBar(title = { Text("Send Invoice", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary card
            inv?.let { i ->
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(i.invoice_number, fontWeight = FontWeight.Bold)
                        Text(i.customerName)
                        Text(formatMoney(i.total), fontWeight = FontWeight.Bold, color = AppColors.Blue)
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
                                if (saveToProfile) inv?.customer_id?.let { cid -> vm.addContact(cid, "email", newEmail.trim()) {} }
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
                                if (saveToProfile) inv?.customer_id?.let { cid -> vm.addContact(cid, "phone", newPhone.trim()) {} }
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
            Button(
                onClick = {
                    sending = true
                    val sendEmail     = emailEntries.any { it.checked }
                    val sendSms       = phoneEntries.any { it.checked }
                    val checkedEmails = emailEntries.filter { it.checked }.map { it.value }
                    val checkedPhones = phoneEntries.filter { it.checked }.map { it.value }
                    vm.send(invoiceId, sendSms, sendEmail, checkedEmails, checkedPhones)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = hasRecipient && !sending
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("Send Invoice", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
