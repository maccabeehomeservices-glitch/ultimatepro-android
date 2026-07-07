package com.ultimatepro.ui.customers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.Customer
import com.ultimatepro.domain.model.CustomerContact
import com.ultimatepro.domain.model.CustomerMembership
import com.ultimatepro.domain.model.Estimate
import com.ultimatepro.domain.model.Invoice
import com.ultimatepro.domain.model.Job
import com.ultimatepro.ui.common.*
import com.ultimatepro.ui.estimates.EstimateRow
import com.ultimatepro.ui.invoices.InvoiceRow
import com.ultimatepro.ui.memberships.AddEditPlanDialog
import com.ultimatepro.ui.memberships.MembershipViewModel
import com.ultimatepro.ui.phone.PhoneViewModel
import com.ultimatepro.ui.phone.SmsMessagesList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Form-only contact entry (not a domain model) ──────────────────────────
internal data class ContactEntry(val value: String, val label: String)

private val phoneLabels = listOf("Mobile", "Work", "Home", "Other")
private val emailLabels = listOf("Personal", "Work", "Other")

private fun splitContactValues(raw: String): List<String> =
    raw.split(Regex("[,/\\n;]+")).map { it.trim() }.filter { it.isNotBlank() }

// ── State & ViewModel ─────────────────────────────────────────────────────

data class CustomerState(
    val loading: Boolean = true,
    val customers: List<Customer> = emptyList(),
    val selected: Customer? = null,
    val contacts: List<CustomerContact> = emptyList(),
    val customerJobs: List<Job> = emptyList(),
    val customerEstimates: List<Estimate> = emptyList(),
    val customerInvoices: List<Invoice> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class CustomerViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {
    private val _s = MutableStateFlow(CustomerState())
    val state = _s.asStateFlow()
    init { load() }

    fun load(search: String? = null, type: String? = null) {
        viewModelScope.launch {
            _s.update { it.copy(loading = true) }
            val r = repo.getCustomers(search, type)
            _s.update {
                it.copy(
                    loading = false,
                    customers = (r as? Result.Success)?.data?.customers ?: emptyList(),
                    error = (r as? Result.Error)?.message
                )
            }
        }
    }

    fun loadCustomer(id: String) {
        viewModelScope.launch {
            val r = repo.getCustomer(id)
            _s.update { it.copy(selected = (r as? Result.Success)?.data) }
        }
    }

    fun loadContacts(customerId: String) {
        viewModelScope.launch {
            val r = repo.getCustomerContacts(customerId)
            _s.update { it.copy(contacts = (r as? Result.Success)?.data ?: emptyList()) }
        }
    }

    fun loadCustomerJobs(customerId: String) {
        viewModelScope.launch {
            val r = repo.getJobs(custId = customerId, includeAllStatuses = true)
            _s.update { it.copy(customerJobs = (r as? Result.Success)?.data?.jobs ?: emptyList()) }
        }
    }

    fun loadCustomerEstimates(customerId: String) {
        viewModelScope.launch {
            val r = repo.getEstimates(custId = customerId)
            _s.update { it.copy(customerEstimates = (r as? Result.Success)?.data?.estimates ?: emptyList()) }
        }
    }

    fun loadCustomerInvoices(customerId: String) {
        viewModelScope.launch {
            val r = repo.getInvoices(custId = customerId)
            _s.update { it.copy(customerInvoices = (r as? Result.Success)?.data?.invoices ?: emptyList()) }
        }
    }

    fun addContact(customerId: String, type: String, value: String, label: String) {
        viewModelScope.launch {
            repo.addCustomerContact(customerId, type, value, label)
            loadContacts(customerId)
        }
    }

    fun deleteContact(contactId: Int, customerId: String) {
        viewModelScope.launch {
            repo.deleteCustomerContact(contactId.toString())
            loadContacts(customerId)
        }
    }

    fun updateContact(contactId: Int, value: String, label: String) {
        viewModelScope.launch {
            repo.updateCustomerContact(contactId.toString(), value, label.lowercase())
        }
    }

    fun save(id: String?, data: Map<String, Any?>, onDone: () -> Unit) {
        viewModelScope.launch {
            val r = if (id == null) repo.createCustomer(data) else repo.updateCustomer(id, data)
            when (r) {
                is Result.Success -> { load(); onDone() }
                is Result.Error -> _s.update { it.copy(error = r.message) }
            }
        }
    }

    // P2.1l Part A: customers are permanent — selection-mode + delete plumbing
    // removed (P2.21). Only `refresh` remains from this block.
    fun refresh(search: String? = null) = load(search)

    /** Creates customer then POSTs all extra contacts (used by new-customer form only).
     *  Pairs are (value, label). */
    fun saveWithContacts(
        data: Map<String, Any?>,
        extraPhones: List<Pair<String, String>>,
        extraEmails: List<Pair<String, String>>,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            when (val r = repo.createCustomer(data)) {
                is Result.Success -> {
                    val cid = r.data.id
                    extraPhones.filter { it.first.isNotBlank() }.forEach { (v, l) ->
                        repo.addCustomerContact(cid, "phone", v, l.lowercase())
                    }
                    extraEmails.filter { it.first.isNotBlank() }.forEach { (v, l) ->
                        repo.addCustomerContact(cid, "email", v, l.lowercase())
                    }
                    load()
                    onDone()
                }
                is Result.Error -> _s.update { it.copy(error = r.message) }
            }
        }
    }
}

// ── Customer List ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CustomerListScreen(
    onCustomer: (String) -> Unit,
    onNewCustomer: () -> Unit,
    onImport: () -> Unit = {},
    vm: CustomerViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val authVm: com.ultimatepro.ui.auth.AuthViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val perms by authVm.permissions.collectAsState()
    val role by authVm.role.collectAsState()
    var search by remember { mutableStateOf("") }
    var resumeKey by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeKey) { if (resumeKey > 0) vm.load(search.ifBlank { null }) }

    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) { LaunchedEffect(Unit) { vm.refresh(search.ifBlank { null }) } }
    LaunchedEffect(state.loading) { if (!state.loading && pullState.isRefreshing) pullState.endRefresh() }

    Scaffold(topBar = {
        // P2.1l Part A / P2.21: customers are permanent — no selection/bulk-delete mode.
        Column {
            TopAppBar(
                title = { Text("Customers", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onImport) { Icon(Icons.Default.Upload, "Import") }
                    IconButton(onClick = onNewCustomer) { Icon(Icons.Default.PersonAdd, null) }
                }
            )
            SearchField(
                value = search,
                onValueChange = { search = it; vm.load(it.ifBlank { null }) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
        }
    }, floatingActionButton = {
        if (com.ultimatepro.domain.model.canUi(role, perms, "customers", "edit_self")) {
            ExtendedFloatingActionButton(
                onClick = onNewCustomer,
                icon = { Icon(Icons.Default.PersonAdd, null) },
                text = { Text("New") }
            )
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).nestedScroll(pullState.nestedScrollConnection)) {
            when {
                state.loading -> LoadingView()
                state.customers.isEmpty() -> EmptyView("No customers", Icons.Default.People)
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.customers, key = { it.id }) { c ->
                        Card(
                            // P2.1l Part A / P2.21: customers are permanent — plain tap-to-open,
                            // no long-press multi-select (its only action was delete).
                            modifier = Modifier.fillMaxWidth().clickable { onCustomer(c.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                AvatarCircle(c.initials, AppColors.Blue)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.fullName, fontWeight = FontWeight.SemiBold)
                                    c.phone?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    c.fullAddress.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    StatusBadge(
                                        c.type.replaceFirstChar { it.uppercase() },
                                        if (c.type == "commercial") AppColors.Orange else AppColors.Blue,
                                        small = true
                                    )
                                    if (c.has_active_membership) {
                                        StatusBadge("Member", AppColors.Green, small = true)
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
            PullToRefreshContainer(state = pullState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }

    // P2.1l Part A: customers are permanent — bulk-delete confirm removed.
}

// ── Customer Detail ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onJob: (String) -> Unit,
    onNewJob: () -> Unit,
    onDeleted: () -> Unit = {},
    onSmsThread: (String) -> Unit = {},
    onEstimate: (String) -> Unit = {},
    onInvoice: (String) -> Unit = {},
    vm: CustomerViewModel = hiltViewModel(),
    membershipVm: MembershipViewModel = hiltViewModel(),
    smsVm: PhoneViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val memberships by membershipVm.memberships.collectAsState()
    val plans by membershipVm.plans.collectAsState()
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val smsState by smsVm.state.collectAsState()
    var showAddPhone by remember { mutableStateOf(false) }
    var showAddEmail by remember { mutableStateOf(false) }
    var showAddMembership by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var custTab by remember { mutableStateOf(0) }
    LaunchedEffect(custTab, customerId) {
        when (custTab) {
            1 -> smsVm.loadCustomerMessages(customerId)
            2 -> vm.loadCustomerEstimates(customerId)
            3 -> vm.loadCustomerInvoices(customerId)
        }
    }

    LaunchedEffect(customerId) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.loadCustomer(customerId)
            vm.loadContacts(customerId)
            vm.loadCustomerJobs(customerId)
            membershipVm.loadCustomerMemberships(customerId)
            membershipVm.loadPlans()
        }
    }

    val c = state.selected
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(c?.fullName ?: "Customer", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = onNewJob) { Icon(Icons.Default.Add, "New Job") }
                // P2.1l Part A: customers are permanent — no delete/archive overflow menu.
            }
        )
    }) { padding ->
        if (c == null) { LoadingView(); return@Scaffold }

        val extraPhones = state.contacts.filter { it.type == "phone" && !it.is_primary }
        val extraEmails = state.contacts.filter { it.type == "email" && !it.is_primary }

        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = custTab, edgePadding = 0.dp) {
                Tab(selected = custTab == 0, onClick = { custTab = 0 }, text = { Text("Details") })
                Tab(selected = custTab == 1, onClick = { custTab = 1 }, text = { Text("Messages") })
                Tab(selected = custTab == 2, onClick = { custTab = 2 }, text = { Text("Estimates") })
                Tab(selected = custTab == 3, onClick = { custTab = 3 }, text = { Text("Invoices") })
            }
            when (custTab) {
                1 -> {
                    val convId = smsState.customerMessages.firstOrNull()?.conversationId
                    SmsMessagesList(
                        messages        = smsState.customerMessages,
                        conversationId  = convId,
                        onOpenThread    = if (convId != null) onSmsThread else null
                    )
                }
                2 -> {
                    if (state.customerEstimates.isEmpty()) EmptyView("No estimates yet", Icons.Default.Description)
                    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.customerEstimates, key = { it.id }) { est -> EstimateRow(est) { onEstimate(est.id) } }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
                3 -> {
                    if (state.customerInvoices.isEmpty()) EmptyView("No invoices yet", Icons.Default.Receipt)
                    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.customerInvoices, key = { it.id }) { inv -> InvoiceRow(inv) { onInvoice(inv.id) } }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                CRMCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Contact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        StatusBadge(
                            c.type.replaceFirstChar { it.uppercase() },
                            if (c.type == "commercial") AppColors.Orange else AppColors.Blue,
                            small = true
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    // ── Emails ──────────────────────────────────────────────
                    c.email?.let { email ->
                        ContactDetailRow(
                            value = email,
                            label = null,
                            actionIcon = Icons.Default.Email,
                            onAction = { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))) },
                            onDelete = null
                        )
                    }
                    extraEmails.forEach { contact ->
                        ContactDetailRow(
                            value = contact.value,
                            label = contact.label?.replaceFirstChar { it.uppercase() },
                            actionIcon = Icons.Default.Email,
                            onAction = { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${contact.value}"))) },
                            onDelete = { vm.deleteContact(contact.id, customerId) }
                        )
                    }
                    TextButton(
                        onClick = { showAddEmail = true },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add email", style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(Modifier.height(4.dp))

                    // ── Phones ──────────────────────────────────────────────
                    c.phone?.let { ph ->
                        ContactDetailRow(
                            value = ph,
                            label = null,
                            actionIcon = Icons.Default.Phone,
                            onAction = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$ph"))) },
                            onDelete = null
                        )
                    }
                    extraPhones.forEach { contact ->
                        ContactDetailRow(
                            value = contact.value,
                            label = contact.label?.replaceFirstChar { it.uppercase() },
                            actionIcon = Icons.Default.Phone,
                            onAction = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.value}"))) },
                            onDelete = { vm.deleteContact(contact.id, customerId) }
                        )
                    }
                    TextButton(
                        onClick = { showAddPhone = true },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add phone", style = MaterialTheme.typography.labelMedium)
                    }

                    c.source?.let { InfoRow(Icons.Default.Source, "Source", it) }
                }
            }

            if (!c.fullAddress.isNullOrBlank()) {
                item {
                    CRMCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Address", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (c.lat != null) TextButton(onClick = {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${c.lat},${c.lng}?q=${Uri.encode(c.fullAddress)}")))
                            }) { Text("Navigate") }
                        }
                        InfoRow(Icons.Default.LocationOn, "", c.fullAddress)
                    }
                }
            }

            c.notes?.let {
                item {
                    CRMCard {
                        Text("Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(it)
                    }
                }
            }

            // ── Customer Portal ───────────────────────────────────────────
            c.portal_token?.let { token ->
                val serverRoot = com.ultimatepro.BuildConfig.API_BASE_URL
                    .removeSuffix("/api/").removeSuffix("/api")
                val portalUrl = "$serverRoot/portal/$token"
                item {
                    CRMCard {
                        Text("Customer Portal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Share this link so the customer can view appointments, approve estimates, and pay invoices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            portalUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Portal Link", portalUrl))
                            }) {
                                Icon(Icons.Default.Link, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy Link")
                            }
                            OutlinedButton(onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, portalUrl)
                                    putExtra(Intent.EXTRA_SUBJECT, "Your account portal — ${c.fullName}")
                                }
                                ctx.startActivity(Intent.createChooser(share, "Share portal link"))
                            }) {
                                Icon(Icons.Default.Send, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }

            // ── Job History ───────────────────────────────────────────────
            if (state.customerJobs.isNotEmpty()) {
                item {
                    CRMCard {
                        Text("Job History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        state.customerJobs.forEach { job ->
                            val isDeleted = job.status == "deleted"
                            val sc = if (isDeleted) AppColors.Red else AppColors.jobStatus(job.status)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isDeleted) { onJob(job.id) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Circle, null, tint = sc, modifier = Modifier.size(8.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(job.job_number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(job.title, fontWeight = FontWeight.Medium, maxLines = 1)
                                    job.scheduled_start?.take(10)?.let { d ->
                                        Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                StatusBadge(
                                    if (isDeleted) "Archived" else job.status.replace("_", " ").replaceFirstChar { it.uppercase() },
                                    sc,
                                    small = true
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
            // ── Memberships ───────────────────────────────────────────────
            item {
                CRMCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Memberships", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showAddMembership = true }) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                    if (memberships.isEmpty()) {
                        Text(
                            "No active memberships",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    } else {
                        memberships.forEach { mem ->
                            MembershipRow(
                                mem = mem,
                                onStatusChange = { newStatus -> membershipVm.updateMembershipStatus(mem.id, customerId, newStatus) },
                                onCreateNextJob = { membershipVm.createNextJob(mem.id, customerId) {} },
                                onDelete = { membershipVm.deleteMembership(mem.id, customerId) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        } // end LazyColumn (Details tab)
            } // end when
        } // end Column wrapper
    }

    if (showAddMembership && plans.isNotEmpty()) {
        AddMembershipDialog(
            plans = plans,
            onDismiss = { showAddMembership = false },
            onAdd = { planId, planName, startDate, notes ->
                membershipVm.addMembership(customerId, planId, planName, startDate = startDate, notes = notes) { _, _ -> showAddMembership = false }
            }
        )
    } else if (showAddMembership) {
        AlertDialog(
            onDismissRequest = { showAddMembership = false },
            title = { Text("No Plans Available") },
            text = { Text("Create membership plans in Settings → Membership Plans first.") },
            confirmButton = { TextButton(onClick = { showAddMembership = false }) { Text("OK") } }
        )
    }

    // P2.1l Part A: customers are permanent — the delete-confirm dialog is removed.

    if (showAddPhone) {
        AddContactDialog(
            title = "Add Phone",
            placeholder = "(555) 000-0000",
            labels = phoneLabels,
            defaultLabel = "Mobile",
            keyboardType = KeyboardType.Phone,
            onAdd = { value, label ->
                vm.addContact(customerId, "phone", value, label.lowercase())
                showAddPhone = false
            },
            onDismiss = { showAddPhone = false }
        )
    }
    if (showAddEmail) {
        AddContactDialog(
            title = "Add Email",
            placeholder = "email@example.com",
            labels = emailLabels,
            defaultLabel = "Personal",
            keyboardType = KeyboardType.Email,
            onAdd = { value, label ->
                vm.addContact(customerId, "email", value, label.lowercase())
                showAddEmail = false
            },
            onDismiss = { showAddEmail = false }
        )
    }
}

// ── Customer Form (new) ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: CustomerViewModel = hiltViewModel()
) {
    var first     by remember { mutableStateOf("") }
    var last      by remember { mutableStateOf("") }
    var email     by remember { mutableStateOf("") }
    var phone     by remember { mutableStateOf("") }
    var address   by remember { mutableStateOf("") }
    var city      by remember { mutableStateOf("") }
    var stateCode by remember { mutableStateOf("") }
    var zip       by remember { mutableStateOf("") }
    var notes     by remember { mutableStateOf("") }
    var type      by remember { mutableStateOf("residential") }

    val extraPhones = remember { mutableStateListOf<ContactEntry>() }
    val extraEmails = remember { mutableStateListOf<ContactEntry>() }
    val state by vm.state.collectAsState()

    val canSave = first.isNotBlank()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("New Customer", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = {
                TextButton(
                    onClick = {
                        vm.saveWithContacts(
                            data = mapOf(
                                "first_name" to first, "last_name" to last.ifBlank { null },
                                "email" to email.ifBlank { null }, "phone" to phone.ifBlank { null },
                                "address" to address.ifBlank { null }, "city" to city.ifBlank { null },
                                "state" to stateCode.ifBlank { null }, "zip" to zip.ifBlank { null },
                                "notes" to notes.ifBlank { null }, "type" to type
                            ),
                            extraPhones = extraPhones.map { it.value to it.label },
                            extraEmails = extraEmails.map { it.value to it.label },
                            onDone = onSaved
                        )
                    },
                    enabled = canSave
                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
            }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionLabel("TYPE")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "residential", onClick = { type = "residential" }, label = { Text("Residential") }, modifier = Modifier.weight(1f))
                FilterChip(selected = type == "commercial",  onClick = { type = "commercial"  }, label = { Text("Commercial")  }, modifier = Modifier.weight(1f))
            }
            SectionLabel("CONTACT INFO")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(first, { first = it }, label = { Text("First *") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(last,  { last  = it }, label = { Text("Last")    }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            }

            // Primary email + extras
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            MultiContactFields(
                entries = extraEmails,
                labels = emailLabels,
                placeholder = "Email",
                keyboardType = KeyboardType.Email,
                addLabel = "+ Add email"
            )

            // Primary phone + extras
            OutlinedTextField(
                value = phone,
                onValueChange = { newVal ->
                    val parts = splitContactValues(newVal)
                    if (parts.size > 1) {
                        phone = parts[0]
                        parts.drop(1).forEach { extraPhones.add(ContactEntry(it, "Mobile")) }
                    } else phone = newVal
                },
                label = { Text("Phone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            MultiContactFields(
                entries = extraPhones,
                labels = phoneLabels,
                placeholder = "Phone",
                keyboardType = KeyboardType.Phone,
                addLabel = "+ Add phone number"
            )

            SectionLabel("ADDRESS")
            OutlinedTextField(address, { address = it }, label = { Text("Street") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(city,      { city      = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.weight(2f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(stateCode, { stateCode = it }, label = { Text("ST")   }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(zip,       { zip       = it }, label = { Text("ZIP")  }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            SectionLabel("NOTES")
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            state.error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(8.dp)) {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Customer Edit ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerEditScreen(
    customerId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: CustomerViewModel = hiltViewModel()
) {
    var first       by remember { mutableStateOf("") }
    var last        by remember { mutableStateOf("") }
    var email       by remember { mutableStateOf("") }
    var phone       by remember { mutableStateOf("") }
    var address     by remember { mutableStateOf("") }
    var city        by remember { mutableStateOf("") }
    var stateCode   by remember { mutableStateOf("") }
    var zip         by remember { mutableStateOf("") }
    var notes       by remember { mutableStateOf("") }
    var type        by remember { mutableStateOf("residential") }
    val state       by vm.state.collectAsState()
    var initialized by remember { mutableStateOf(false) }

    // Additional contacts managed live (add/delete go straight to API)
    val extraPhones = remember { mutableStateListOf<CustomerContact>() }
    val extraEmails = remember { mutableStateListOf<CustomerContact>() }
    val pendingPhones = remember { mutableStateListOf<ContactEntry>() }
    val pendingEmails = remember { mutableStateListOf<ContactEntry>() }

    LaunchedEffect(customerId) {
        vm.loadCustomer(customerId)
        vm.loadContacts(customerId)
    }

    LaunchedEffect(state.selected) {
        if (!initialized) state.selected?.let { c ->
            initialized = true
            first = c.first_name; last = c.last_name ?: ""; email = c.email ?: ""
            phone = c.phone ?: ""; address = c.address ?: ""; city = c.city ?: ""
            stateCode = c.state ?: ""; zip = c.zip ?: ""; notes = c.notes ?: ""
            type = c.type
        }
    }

    LaunchedEffect(state.contacts) {
        extraPhones.clear()
        extraEmails.clear()
        state.contacts.filter { it.type == "phone" && !it.is_primary }.forEach { extraPhones.add(it) }
        state.contacts.filter { it.type == "email" && !it.is_primary }.forEach { extraEmails.add(it) }
        pendingPhones.clear()
        pendingEmails.clear()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Edit Customer", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = {
                TextButton(
                    onClick = {
                        if (first.isNotBlank()) vm.save(
                            customerId,
                            mapOf(
                                "first_name" to first, "last_name" to last.ifBlank { null },
                                "email" to email.ifBlank { null }, "phone" to phone.ifBlank { null },
                                "address" to address.ifBlank { null }, "city" to city.ifBlank { null },
                                "state" to stateCode.ifBlank { null }, "zip" to zip.ifBlank { null },
                                "notes" to notes.ifBlank { null }, "type" to type
                            )
                        ) { onSaved() }
                    },
                    enabled = first.isNotBlank()
                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
            }
        )
    }) { padding ->
        if (!initialized && state.selected == null) { LoadingView(); return@Scaffold }

        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionLabel("TYPE")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "residential", onClick = { type = "residential" }, label = { Text("Residential") }, modifier = Modifier.weight(1f))
                FilterChip(selected = type == "commercial",  onClick = { type = "commercial"  }, label = { Text("Commercial")  }, modifier = Modifier.weight(1f))
            }
            SectionLabel("CONTACT INFO")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(first, { first = it }, label = { Text("First *") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(last,  { last  = it }, label = { Text("Last")    }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            }

            // Primary email field
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email (primary)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            // Additional emails (live — from API)
            extraEmails.forEachIndexed { idx, contact ->
                key(contact.id) {
                    ContactEditRow(
                        initialValue = contact.value,
                        initialLabel = contact.label?.replaceFirstChar { it.uppercase() } ?: "Personal",
                        labelOptions = emailLabels,
                        keyboardType = KeyboardType.Email,
                        onUpdate = { value, label -> vm.updateContact(contact.id, value, label) },
                        onDelete = {
                            vm.deleteContact(contact.id, customerId)
                            extraEmails.removeAt(idx)
                        }
                    )
                }
            }
            pendingEmails.forEachIndexed { idx, entry ->
                PendingContactField(
                    entry = entry,
                    labels = emailLabels,
                    keyboardType = KeyboardType.Email,
                    onChange = { pendingEmails[idx] = it },
                    onSave = { value, label ->
                        vm.addContact(customerId, "email", value, label.lowercase())
                        pendingEmails.removeAt(idx)
                    },
                    onRemove = { pendingEmails.removeAt(idx) }
                )
            }
            TextButton(
                onClick = { pendingEmails.add(ContactEntry("", "Personal")) },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
            ) {
                Text("+ Add email", color = AppColors.Blue, fontSize = 13.sp)
            }

            // Primary phone field
            OutlinedTextField(
                value = phone,
                onValueChange = { newVal ->
                    val parts = splitContactValues(newVal)
                    if (parts.size > 1) {
                        phone = parts[0]
                        // Extra ones saved immediately via API (we have the customer ID)
                        parts.drop(1).forEach { v ->
                            vm.addContact(customerId, "phone", v, "mobile")
                        }
                    } else phone = newVal
                },
                label = { Text("Phone (primary)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            // Additional phones (live — from API)
            extraPhones.forEachIndexed { idx, contact ->
                key(contact.id) {
                    ContactEditRow(
                        initialValue = contact.value,
                        initialLabel = contact.label?.replaceFirstChar { it.uppercase() } ?: "Mobile",
                        labelOptions = phoneLabels,
                        keyboardType = KeyboardType.Phone,
                        onUpdate = { value, label -> vm.updateContact(contact.id, value, label) },
                        onDelete = {
                            vm.deleteContact(contact.id, customerId)
                            extraPhones.removeAt(idx)
                        }
                    )
                }
            }
            pendingPhones.forEachIndexed { idx, entry ->
                PendingContactField(
                    entry = entry,
                    labels = phoneLabels,
                    keyboardType = KeyboardType.Phone,
                    onChange = { pendingPhones[idx] = it },
                    onSave = { value, label ->
                        vm.addContact(customerId, "phone", value, label.lowercase())
                        pendingPhones.removeAt(idx)
                    },
                    onRemove = { pendingPhones.removeAt(idx) }
                )
            }
            TextButton(
                onClick = { pendingPhones.add(ContactEntry("", "Mobile")) },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
            ) {
                Text("+ Add phone number", color = AppColors.Blue, fontSize = 13.sp)
            }

            SectionLabel("ADDRESS")
            OutlinedTextField(address, { address = it }, label = { Text("Street") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(city,      { city      = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.weight(2f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(stateCode, { stateCode = it }, label = { Text("ST")   }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(zip,       { zip       = it }, label = { Text("ZIP")  }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            SectionLabel("NOTES")
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            state.error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(8.dp)) {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

}

// ── Shared composable helpers ─────────────────────────────────────────────

/** Row shown in CustomerDetailScreen for a single contact entry. */
@Composable
private fun ContactDetailRow(
    value: String,
    label: String?,
    actionIcon: ImageVector,
    onAction: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            actionIcon, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        label?.let {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        IconButton(onClick = onAction, modifier = Modifier.size(32.dp)) {
            Icon(actionIcon, null, tint = AppColors.Blue, modifier = Modifier.size(17.dp))
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** Editable row for an existing API contact in CustomerEditScreen. Auto-saves on focus lost. */
@Composable
private fun ContactEditRow(
    initialValue: String,
    initialLabel: String,
    labelOptions: List<String>,
    keyboardType: KeyboardType,
    onUpdate: (value: String, label: String) -> Unit,
    onDelete: () -> Unit
) {
    var localValue by remember(initialValue) { mutableStateOf(initialValue) }
    var savedValue by remember(initialValue) { mutableStateOf(initialValue) }
    var labelIdx by remember(initialLabel) { mutableStateOf(labelOptions.indexOfFirst { it.equals(initialLabel, ignoreCase = true) }.coerceAtLeast(0)) }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = localValue,
            onValueChange = { localValue = it },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { fs ->
                    if (!fs.isFocused && localValue.isNotBlank() && localValue != savedValue) {
                        savedValue = localValue
                        onUpdate(localValue, labelOptions[labelIdx])
                    }
                },
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
        FilterChip(
            selected = true,
            onClick = {
                val newIdx = (labelIdx + 1) % labelOptions.size
                labelIdx = newIdx
                if (localValue.isNotBlank()) onUpdate(localValue, labelOptions[newIdx])
            },
            label = { Text(labelOptions[labelIdx], style = MaterialTheme.typography.labelSmall) }
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** New-contact draft field in CustomerEditScreen — POSTs to API on focus lost when non-blank. */
@Composable
private fun PendingContactField(
    entry: ContactEntry,
    labels: List<String>,
    keyboardType: KeyboardType,
    onChange: (ContactEntry) -> Unit,
    onSave: (value: String, label: String) -> Unit,
    onRemove: () -> Unit
) {
    var localValue by remember { mutableStateOf(entry.value) }
    val labelIdx = labels.indexOf(entry.label).coerceAtLeast(0)

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = localValue,
            onValueChange = { localValue = it },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { fs ->
                    if (!fs.isFocused && localValue.isNotBlank()) {
                        onSave(localValue, entry.label)
                    }
                },
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
        FilterChip(
            selected = true,
            onClick = { onChange(entry.copy(label = labels[(labelIdx + 1) % labels.size])) },
            label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) }
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** Inline list of editable contact fields used in CustomerFormScreen (new customer). */
@Composable
private fun MultiContactFields(
    entries: SnapshotStateList<ContactEntry>,
    labels: List<String>,
    placeholder: String,
    keyboardType: KeyboardType,
    addLabel: String
) {
    entries.forEachIndexed { idx, entry ->
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = entry.value,
                onValueChange = { newVal ->
                    val parts = splitContactValues(newVal)
                    if (parts.size > 1) {
                        entries[idx] = entry.copy(value = parts[0])
                        parts.drop(1).forEach { entries.add(ContactEntry(it, labels[0])) }
                    } else {
                        entries[idx] = entry.copy(value = newVal)
                    }
                },
                label = { Text("$placeholder ${idx + 2}") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
            )
            val cycleIdx = labels.indexOf(entry.label).coerceAtLeast(0)
            FilterChip(
                selected = true,
                onClick = { entries[idx] = entry.copy(label = labels[(cycleIdx + 1) % labels.size]) },
                label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) }
            )
            IconButton(onClick = { entries.removeAt(idx) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
    TextButton(
        onClick = { entries.add(ContactEntry("", labels[0])) },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
    ) {
        Text(addLabel, color = AppColors.Blue, fontSize = 13.sp)
    }
}

/** Dialog for adding a single contact from CustomerDetailScreen and CustomerEditScreen. */
@Composable
private fun AddContactDialog(
    title: String,
    placeholder: String,
    labels: List<String>,
    defaultLabel: String,
    keyboardType: KeyboardType,
    onAdd: (value: String, label: String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf("") }
    var label by remember { mutableStateOf(defaultLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEach { l ->
                        FilterChip(
                            selected = label == l,
                            onClick = { label = l },
                            label = { Text(l, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onAdd(value.trim(), label) }, enabled = value.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Membership UI helpers ─────────────────────────────────────────────────────

@Composable
private fun MembershipRow(
    mem: CustomerMembership,
    onStatusChange: (String) -> Unit,
    onCreateNextJob: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val statusColor = when (mem.status) {
        "active"    -> AppColors.Green
        "paused"    -> AppColors.Orange
        "cancelled" -> MaterialTheme.colorScheme.error
        else        -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Autorenew, null, tint = statusColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(mem.planName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                "${mem.planFrequency.replace("_", " ").replaceFirstChar { it.uppercase() }} · $${String.format(java.util.Locale.US, "%.2f", mem.planPrice)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            mem.nextJobDate?.let {
                Text("Next: $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        StatusBadge(
            mem.status.replaceFirstChar { it.uppercase() },
            statusColor,
            small = true
        )
        Box {
            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, null) }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (mem.status == "active") {
                    DropdownMenuItem(text = { Text("Schedule Next Job") }, onClick = { menuExpanded = false; onCreateNextJob() }, leadingIcon = { Icon(Icons.Default.Add, null) })
                    DropdownMenuItem(text = { Text("Pause") }, onClick = { menuExpanded = false; onStatusChange("paused") }, leadingIcon = { Icon(Icons.Default.Pause, null) })
                } else if (mem.status == "paused") {
                    DropdownMenuItem(text = { Text("Reactivate") }, onClick = { menuExpanded = false; onStatusChange("active") }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) })
                }
                DropdownMenuItem(text = { Text("Cancel Membership") }, onClick = { menuExpanded = false; onStatusChange("cancelled") }, leadingIcon = { Icon(Icons.Default.Cancel, null) })
                DropdownMenuItem(text = { Text("Remove", color = MaterialTheme.colorScheme.error) }, onClick = { menuExpanded = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMembershipDialog(
    plans: List<com.ultimatepro.domain.model.MembershipPlan>,
    onDismiss: () -> Unit,
    onAdd: (planId: String, planName: String, startDate: String?, notes: String?) -> Unit
) {
    var selectedPlanId by remember { mutableStateOf(plans.firstOrNull()?.id ?: "") }
    var notes by remember { mutableStateOf("") }
    var planExpanded by remember { mutableStateOf(false) }
    val selectedPlan = plans.find { it.id == selectedPlanId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Membership") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = planExpanded, onExpandedChange = { planExpanded = it }) {
                    OutlinedTextField(
                        value = selectedPlan?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Plan *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = planExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = planExpanded, onDismissRequest = { planExpanded = false }) {
                        plans.forEach { plan ->
                            DropdownMenuItem(
                                text = { Text("${plan.name} · ${plan.planFrequencyLabel()} · $${"%.2f".format(plan.price)}") },
                                onClick = { selectedPlanId = plan.id; planExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (selectedPlanId.isNotBlank()) onAdd(selectedPlanId, selectedPlan?.name ?: "", null, notes.ifBlank { null }) },
                enabled = selectedPlanId.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun com.ultimatepro.domain.model.MembershipPlan.planFrequencyLabel() = when (frequency) {
    "weekly"        -> "Weekly"
    "monthly"       -> "Monthly"
    "quarterly"     -> "Quarterly"
    "semi_annually" -> "Semi-annually"
    "annually"      -> "Annually"
    else            -> frequency
}
