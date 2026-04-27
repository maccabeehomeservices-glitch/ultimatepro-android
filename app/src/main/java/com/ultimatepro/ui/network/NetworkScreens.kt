package com.ultimatepro.ui.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.CompanySearchResult
import com.ultimatepro.domain.model.ContractorAgreement
import com.ultimatepro.domain.model.ContractorConnection
import com.ultimatepro.ui.common.AppColors
import com.ultimatepro.ui.common.CRMCard
import com.ultimatepro.ui.common.ErrorView
import com.ultimatepro.ui.common.LoadingView
import com.ultimatepro.ui.common.SectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val repo: CrmRepository
) : ViewModel() {

    private val _connections = MutableStateFlow<List<ContractorConnection>>(emptyList())
    val connections = _connections.asStateFlow()

    private val _selectedConnection = MutableStateFlow<ContractorConnection?>(null)
    val selectedConnection = _selectedConnection.asStateFlow()

    private val _agreements = MutableStateFlow<List<ContractorAgreement>>(emptyList())
    val agreements = _agreements.asStateFlow()

    private val _searchResults = MutableStateFlow<List<CompanySearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching = _searching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _msg = MutableStateFlow<String?>(null)
    val msg = _msg.asStateFlow()

    fun clearMsg() { _msg.value = null }
    fun clearError() { _error.value = null }

    fun loadConnections() {
        viewModelScope.launch {
            _loading.value = true
            when (val r = repo.getConnections()) {
                is Result.Success -> _connections.value = r.data.map { it.toConnection() }
                is Result.Error   -> _error.value = r.message
            }
            _loading.value = false
        }
    }

    fun loadConnection(id: String) {
        viewModelScope.launch {
            when (val r = repo.getConnection(id)) {
                is Result.Success -> _selectedConnection.value = r.data.toConnection()
                is Result.Error   -> _error.value = r.message
            }
        }
    }

    fun loadAgreements(connectionId: String) {
        viewModelScope.launch {
            when (val r = repo.getAgreements(connectionId)) {
                is Result.Success -> _agreements.value = r.data.map { it.toAgreement() }
                is Result.Error   -> _error.value = r.message
            }
        }
    }

    fun searchCompanies(q: String, type: String) {
        if (q.isBlank()) { _searchResults.value = emptyList(); return }
        viewModelScope.launch {
            _searching.value = true
            when (val r = repo.searchCompanies(q, type)) {
                is Result.Success -> _searchResults.value = r.data.map { it.toSearchResult() }
                is Result.Error   -> _error.value = r.message
            }
            _searching.value = false
        }
    }

    fun clearSearch() { _searchResults.value = emptyList() }

    fun inviteConnection(searchValue: String, searchType: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _loading.value = true
            when (val r = repo.inviteConnection(mapOf("search_value" to searchValue, "search_type" to searchType))) {
                is Result.Success -> {
                    _msg.value = "Connection request sent!"
                    loadConnections()
                    onDone()
                }
                is Result.Error -> _error.value = r.message
            }
            _loading.value = false
        }
    }

    fun respondConnection(id: String, action: String) {
        viewModelScope.launch {
            when (val r = repo.respondConnection(id, action)) {
                is Result.Success -> {
                    _msg.value = if (action == "accept") "Connection accepted!" else "Connection declined"
                    loadConnections()
                    loadConnection(id)
                }
                is Result.Error -> _error.value = r.message
            }
        }
    }

    fun pauseConnection(id: String) {
        viewModelScope.launch {
            when (val r = repo.pauseConnection(id)) {
                is Result.Success -> {
                    loadConnection(id)
                    loadConnections()
                }
                is Result.Error -> _error.value = r.message
            }
        }
    }

    fun proposeAgreement(
        connectionId: String,
        senderPct: Double,
        receiverPct: Double,
        reviewGoesTo: String,
        notes: String?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val body = buildMap<String, Any?> {
                put("connection_id", connectionId)
                put("sender_keeps_pct", senderPct)
                put("receiver_keeps_pct", receiverPct)
                put("review_goes_to", reviewGoesTo)
                if (!notes.isNullOrBlank()) put("notes", notes)
            }
            when (val r = repo.proposeAgreement(body)) {
                is Result.Success -> {
                    _msg.value = "Agreement proposal sent!"
                    loadAgreements(connectionId)
                    onDone()
                }
                is Result.Error -> _error.value = r.message
            }
        }
    }

    fun respondAgreement(
        id: String,
        connectionId: String,
        action: String,
        counterSenderPct: Double? = null,
        counterReceiverPct: Double? = null,
        counterNotes: String? = null,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val body = buildMap<String, Any?> {
                put("action", action)
                if (action == "counter") {
                    put("counter_sender_pct", counterSenderPct)
                    put("counter_receiver_pct", counterReceiverPct)
                    if (!counterNotes.isNullOrBlank()) put("counter_notes", counterNotes)
                }
            }
            when (val r = repo.respondAgreement(id, body)) {
                is Result.Success -> {
                    _msg.value = when (action) {
                        "accept"  -> "Agreement accepted!"
                        "decline" -> "Agreement declined"
                        "counter" -> "Counter-proposal sent!"
                        else      -> "Done"
                    }
                    loadAgreements(connectionId)
                    onDone()
                }
                is Result.Error -> _error.value = r.message
            }
        }
    }

    // ── Raw map → domain helpers ──────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.toConnection(): ContractorConnection {
        val la = (this["latest_agreement_id"] as? String)?.let {
            ContractorAgreement(
                id              = it,
                connectionId    = (this["id"] as? String) ?: "",
                proposedBy      = (this["latest_agreement_proposed_by"] as? String) ?: "",
                status          = (this["latest_agreement_status"] as? String) ?: "pending",
                senderKeptPct   = (this["latest_sender_keeps_pct"] as? Number)?.toDouble() ?: 0.0,
                receiverKeptPct = (this["latest_receiver_keeps_pct"] as? Number)?.toDouble() ?: 0.0,
                reviewGoesTo    = (this["latest_review_goes_to"] as? String) ?: "receiver"
            )
        }
        return ContractorConnection(
            id                   = (this["id"] as? String) ?: "",
            partnerCompanyId     = (this["partner_id"] as? String) ?: "",
            partnerCompanyName   = (this["partner_name"] as? String) ?: "",
            partnerUltimatecrmId = this["partner_ultimatecrm_id"] as? String,
            status               = (this["status"] as? String) ?: "pending",
            invitedBy            = this["invited_by"] as? String,
            latestAgreement      = la,
            createdAt            = this["created_at"] as? String
        )
    }

    private fun Map<String, Any>.toAgreement() = ContractorAgreement(
        id              = (this["id"] as? String) ?: "",
        connectionId    = (this["connection_id"] as? String) ?: "",
        proposedBy      = (this["proposed_by"] as? String) ?: "",
        status          = (this["status"] as? String) ?: "pending",
        senderKeptPct   = (this["sender_keeps_pct"] as? Number)?.toDouble() ?: 0.0,
        receiverKeptPct = (this["receiver_keeps_pct"] as? Number)?.toDouble() ?: 0.0,
        reviewGoesTo    = (this["review_goes_to"] as? String) ?: "receiver",
        notes           = this["notes"] as? String,
        createdAt       = this["created_at"] as? String,
        respondedAt     = this["responded_at"] as? String
    )

    private fun Map<String, Any>.toSearchResult() = CompanySearchResult(
        id             = (this["id"] as? String) ?: "",
        name           = (this["name"] as? String) ?: "",
        city           = this["city"] as? String,
        state          = this["state"] as? String,
        ultimatecrmId  = this["ultimatecrm_id"] as? String,
        profileMode    = this["profile_mode"] as? String
    )

    // ── Partner Report ────────────────────────────────────────────────────────

    private val _report = MutableStateFlow<Map<String, Any>?>(null)
    val report = _report.asStateFlow()

    private val _reportLoading = MutableStateFlow(false)
    val reportLoading = _reportLoading.asStateFlow()

    private val _reportSending = MutableStateFlow(false)
    val reportSending = _reportSending.asStateFlow()

    private val _reportMsg = MutableStateFlow<String?>(null)
    val reportMsg = _reportMsg.asStateFlow()

    fun clearReportMsg() { _reportMsg.value = null }

    fun loadReport(connectionId: String, dateFrom: String? = null, dateTo: String? = null) {
        viewModelScope.launch {
            _reportLoading.value = true
            when (val r = repo.getPartnerReport(connectionId, dateFrom, dateTo)) {
                is Result.Success -> _report.value = r.data
                is Result.Error   -> _error.value = r.message
            }
            _reportLoading.value = false
        }
    }

    fun sendReport(
        connectionId: String,
        dateFrom: String? = null,
        dateTo: String? = null,
        recipientEmail: String? = null,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _reportSending.value = true
            val body = buildMap<String, Any?> {
                dateFrom?.let       { put("date_from", it) }
                dateTo?.let         { put("date_to", it) }
                recipientEmail?.let { put("recipient_email", it) }
            }
            when (val r = repo.sendPartnerReport(connectionId, body)) {
                is Result.Success -> { _reportMsg.value = "Report sent!"; onDone() }
                is Result.Error   -> _error.value = r.message
            }
            _reportSending.value = false
        }
    }
}

// ─── Status chip helpers ──────────────────────────────────────────────────────

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "active"   -> "Active"   to AppColors.Green
        "pending"  -> "Pending"  to AppColors.Gold
        "paused"   -> "Paused"   to Color(0xFF9E9E9E)
        "declined" -> "Declined" to AppColors.Red
        else       -> status.replaceFirstChar { it.uppercase() } to AppColors.Blue
    }
    Surface(
        shape  = RoundedCornerShape(20.dp),
        color  = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style    = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color    = color
        )
    }
}

@Composable
private fun AgreementStatusChip(status: String) {
    val (label, color) = when (status) {
        "accepted"  -> "Active"   to AppColors.Green
        "pending"   -> "Pending"  to AppColors.Gold
        "declined"  -> "Declined" to AppColors.Red
        "countered" -> "Countered" to AppColors.Orange
        else        -> status.replaceFirstChar { it.uppercase() } to AppColors.Blue
    }
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.15f)) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

// ─── NetworkListScreen ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkListScreen(
    onConnection: (String) -> Unit,
    vm: NetworkViewModel = hiltViewModel()
) {
    val connections by vm.connections.collectAsState()
    val loading     by vm.loading.collectAsState()
    val error       by vm.error.collectAsState()
    val msg         by vm.msg.collectAsState()
    var showFind    by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadConnections() }

    val snackState = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snackState.showSnackbar(it); vm.clearMsg() } }
    LaunchedEffect(error) { error?.let { snackState.showSnackbar(it); vm.clearError() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Network", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showFind = true }) {
                        Icon(Icons.Default.PersonAdd, "Find Contractor")
                    }
                    IconButton(onClick = { vm.loadConnections() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackState) }
    ) { padding ->
        when {
            loading && connections.isEmpty() -> LoadingView()
            else -> {
                if (connections.isEmpty()) {
                    // Empty state
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.People, null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No connections yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("Connect with other contractors to share jobs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { showFind = true }) {
                                Icon(Icons.Default.PersonAdd, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Find Contractor")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(connections) { conn ->
                            ConnectionCard(conn, onClick = { onConnection(conn.id) })
                        }
                    }
                }
            }
        }
    }

    if (showFind) {
        FindContractorSheet(vm = vm, onDismiss = { showFind = false })
    }
}

@Composable
private fun ConnectionCard(conn: ContractorConnection, onClick: () -> Unit) {
    CRMCard {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(conn.partnerCompanyName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                StatusChip(conn.status)
            }
            conn.partnerUltimatecrmId?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val agr = conn.latestAgreement
            when {
                agr == null -> Text("No agreement",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                agr.status == "accepted" -> Text(
                    "Agreement active: You ${agr.senderKeptPct.toInt()}% / Them ${agr.receiverKeptPct.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Green)
                agr.status == "pending"  -> Text("Agreement pending response",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Gold)
                else -> Text("No active agreement",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─── NetworkDetailScreen ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDetailScreen(
    connectionId: String,
    onBack: () -> Unit,
    onReport: () -> Unit = {},
    vm: NetworkViewModel = hiltViewModel()
) {
    val conn       by vm.selectedConnection.collectAsState()
    val agreements by vm.agreements.collectAsState()
    val msg        by vm.msg.collectAsState()
    val error      by vm.error.collectAsState()
    var showPropose by remember { mutableStateOf(false) }
    var showCounter by remember { mutableStateOf<ContractorAgreement?>(null) }

    LaunchedEffect(connectionId) {
        vm.loadConnection(connectionId)
        vm.loadAgreements(connectionId)
    }

    val snackState = remember { SnackbarHostState() }
    LaunchedEffect(msg)   { msg?.let   { snackState.showSnackbar(it); vm.clearMsg() } }
    LaunchedEffect(error) { error?.let { snackState.showSnackbar(it); vm.clearError() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conn?.partnerCompanyName ?: "Connection", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackState) }
    ) { padding ->
        if (conn == null) {
            LoadingView()
        } else {
            val c = conn!!
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Connection status card ─────────────────────────────
                item {
                    CRMCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel("CONNECTION STATUS")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                StatusChip(c.status)
                                c.createdAt?.let {
                                    Text("Since ${it.take(10)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            c.partnerUltimatecrmId?.let {
                                Text("ID: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (c.status in listOf("active", "paused")) {
                                OutlinedButton(
                                    onClick = { vm.pauseConnection(connectionId) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        if (c.status == "active") Icons.Default.Pause else Icons.Default.PlayArrow,
                                        null, Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (c.status == "active") "Pause Connection" else "Resume Connection")
                                }
                            }
                            if (c.status == "pending" && c.invitedBy != null) {
                                // Show accept/decline only for the non-inviting party (server enforces this too)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick  = { vm.respondConnection(connectionId, "decline") },
                                        modifier = Modifier.weight(1f),
                                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Red)
                                    ) { Text("Decline") }
                                    Button(
                                        onClick  = { vm.respondConnection(connectionId, "accept") },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Accept") }
                                }
                            }
                        }
                    }
                }

                // ── Agreement card ────────────────────────────────────
                item {
                    CRMCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                SectionLabel("AGREEMENT")
                                if (c.status == "active") {
                                    TextButton(onClick = { showPropose = true }) {
                                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Propose")
                                    }
                                }
                            }

                            val activeAgreement = agreements.firstOrNull { it.status == "accepted" }
                            val pendingAgreement = agreements.firstOrNull { it.status == "pending" }

                            when {
                                activeAgreement != null -> {
                                    Text("You keep ${activeAgreement.senderKeptPct.toInt()}% when sending jobs",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold, color = AppColors.Green)
                                    Text("They keep ${activeAgreement.receiverKeptPct.toInt()}% when receiving jobs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Reviews go to: ${activeAgreement.reviewGoesTo.replaceFirstChar { it.uppercase() }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                pendingAgreement != null -> {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AgreementStatusChip("pending")
                                        Text("${pendingAgreement.senderKeptPct.toInt()}% / ${pendingAgreement.receiverKeptPct.toInt()}%",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick  = { showCounter = pendingAgreement },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Counter") }
                                        OutlinedButton(
                                            onClick = { vm.respondAgreement(pendingAgreement.id, connectionId, "decline") },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Red)
                                        ) { Text("Decline") }
                                        Button(
                                            onClick = { vm.respondAgreement(pendingAgreement.id, connectionId, "accept") },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Accept") }
                                    }
                                }
                                else -> Text("No active agreement. Propose terms to start sharing jobs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // ── Agreement history ─────────────────────────────────
                if (agreements.size > 1) {
                    item { SectionLabel("AGREEMENT HISTORY") }
                    items(agreements.drop(if (agreements.firstOrNull()?.status in listOf("accepted","pending")) 1 else 0)) { agr ->
                        CRMCard {
                            Row(Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("${agr.senderKeptPct.toInt()}% / ${agr.receiverKeptPct.toInt()}%",
                                    style = MaterialTheme.typography.bodySmall)
                                AgreementStatusChip(agr.status)
                            }
                        }
                    }
                }

                // ── Partner Reports button ────────────────────────────
                item {
                    Button(
                        onClick  = onReport,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Assessment, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Partner Reports")
                    }
                }
            }
        }
    }

    if (showPropose) {
        AgreementProposalSheet(
            connectionId = connectionId,
            vm           = vm,
            onDismiss    = { showPropose = false }
        )
    }

    showCounter?.let { pendingAgr ->
        AgreementProposalSheet(
            connectionId   = connectionId,
            vm             = vm,
            counterTo      = pendingAgr,
            onDismiss      = { showCounter = null }
        )
    }
}

// ─── AgreementProposalSheet ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgreementProposalSheet(
    connectionId: String,
    vm: NetworkViewModel,
    counterTo: ContractorAgreement? = null,
    onDismiss: () -> Unit
) {
    var senderPctText   by remember { mutableStateOf(counterTo?.senderKeptPct?.toInt()?.toString() ?: "50") }
    var receiverPctText by remember { mutableStateOf(counterTo?.receiverKeptPct?.toInt()?.toString() ?: "50") }
    var reviewGoesTo    by remember { mutableStateOf(counterTo?.reviewGoesTo ?: "receiver") }
    var notes           by remember { mutableStateOf("") }
    var editingField    by remember { mutableStateOf<String?>(null) }  // "sender" or "receiver"

    // Auto-balance the other field
    fun onSenderChange(v: String) {
        senderPctText = v
        val n = v.toIntOrNull()
        if (n != null && n in 0..100) receiverPctText = (100 - n).toString()
    }
    fun onReceiverChange(v: String) {
        receiverPctText = v
        val n = v.toIntOrNull()
        if (n != null && n in 0..100) senderPctText = (100 - n).toString()
    }

    val senderPct   = senderPctText.toDoubleOrNull()
    val receiverPct = receiverPctText.toDoubleOrNull()
    val valid       = senderPct != null && receiverPct != null &&
                      Math.abs(senderPct + receiverPct - 100.0) < 0.01

    ModalBottomSheet(onDismissRequest = onDismiss, windowInsets = WindowInsets(0)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (counterTo != null) "Counter Proposal" else "Propose Agreement Terms",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text("When receiving a job from them, you keep:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value         = senderPctText,
                onValueChange = { onSenderChange(it.filter { c -> c.isDigit() }.take(3)) },
                label         = { Text("You keep %") },
                suffix        = { Text("%") },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier      = Modifier.fillMaxWidth()
            )

            Text("They keep when receiving a job from you:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value         = receiverPctText,
                onValueChange = { onReceiverChange(it.filter { c -> c.isDigit() }.take(3)) },
                label         = { Text("They keep %") },
                suffix        = { Text("%") },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier      = Modifier.fillMaxWidth()
            )

            if (!valid) {
                Text("Percentages must add up to 100%",
                    style = MaterialTheme.typography.bodySmall, color = AppColors.Red)
            }

            // Review goes to — segmented control
            Text("Reviews go to:", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("sender" to "Sender", "receiver" to "Receiver", "both" to "Both").forEach { (value, label) ->
                    val selected = reviewGoesTo == value
                    FilterChip(
                        selected = selected,
                        onClick  = { reviewGoesTo = value },
                        label    = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedTextField(
                value         = notes,
                onValueChange = { notes = it },
                label         = { Text("Notes (optional)") },
                minLines      = 2,
                modifier      = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (valid) {
                        if (counterTo != null) {
                            vm.respondAgreement(
                                id               = counterTo.id,
                                connectionId     = connectionId,
                                action           = "counter",
                                counterSenderPct = senderPct,
                                counterReceiverPct = receiverPct,
                                counterNotes     = notes.ifBlank { null },
                                onDone           = onDismiss
                            )
                        } else {
                            vm.proposeAgreement(
                                connectionId = connectionId,
                                senderPct    = senderPct!!,
                                receiverPct  = receiverPct!!,
                                reviewGoesTo = reviewGoesTo,
                                notes        = notes.ifBlank { null },
                                onDone       = onDismiss
                            )
                        }
                    }
                },
                enabled  = valid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (counterTo != null) "Send Counter-Proposal" else "Send Proposal")
            }
        }
    }
}

// ─── FindContractorSheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindContractorSheet(
    vm: NetworkViewModel,
    onDismiss: () -> Unit
) {
    val searchResults by vm.searchResults.collectAsState()
    val searching     by vm.searching.collectAsState()
    val loading       by vm.loading.collectAsState()
    val error         by vm.error.collectAsState()

    var searchType  by remember { mutableStateOf("phone") }
    var searchQuery by remember { mutableStateOf("") }

    val typeOptions = listOf("phone" to "Phone", "email" to "Email", "ultimatecrm_id" to "UCM ID")

    ModalBottomSheet(onDismissRequest = { vm.clearSearch(); onDismiss() }, windowInsets = WindowInsets(0)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Find a Contractor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)

            // Search type tabs
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                typeOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = searchType == value,
                        onClick  = { searchType = value; vm.clearSearch() },
                        label    = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                label         = { Text(typeOptions.first { it.first == searchType }.second) },
                singleLine    = true,
                trailingIcon  = {
                    if (searching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { vm.searchCompanies(searchQuery, searchType) }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchCompanies(searchQuery, searchType) }),
                modifier      = Modifier.fillMaxWidth()
            )

            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.Red)
            }

            if (searchResults.isEmpty() && !searching && searchQuery.isNotBlank()) {
                Text("No results found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            searchResults.forEach { result ->
                CRMCard {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(result.name, fontWeight = FontWeight.SemiBold)
                            val location = listOfNotNull(result.city, result.state).joinToString(", ")
                            if (location.isNotBlank()) {
                                Text(location, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            result.ultimatecrmId?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.Blue)
                            }
                        }
                        Button(
                            onClick  = {
                                // Prefer UCM ID (globally unique); fall back to the query that found this result
                                val (sv, st) = if (result.ultimatecrmId != null)
                                    result.ultimatecrmId to "ultimatecrm_id"
                                else
                                    searchQuery to searchType
                                vm.inviteConnection(sv, st) { vm.clearSearch(); onDismiss() }
                            },
                            enabled  = !loading
                        ) { Text("Connect") }
                    }
                }
            }
        }
    }
}

// ─── PartnerReportScreen ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerReportScreen(
    connectionId: String,
    onBack: () -> Unit,
    vm: NetworkViewModel = hiltViewModel()
) {
    val report        by vm.report.collectAsState()
    val reportLoading by vm.reportLoading.collectAsState()
    val error         by vm.error.collectAsState()
    val reportMsg     by vm.reportMsg.collectAsState()

    var dateFrom by remember { mutableStateOf("") }
    var dateTo   by remember { mutableStateOf("") }
    var showSend by remember { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }
    val fromPickerState = rememberDatePickerState()
    val toPickerState   = rememberDatePickerState()

    LaunchedEffect(Unit) { vm.loadReport(connectionId) }

    val snackState = remember { SnackbarHostState() }
    LaunchedEffect(reportMsg) { reportMsg?.let { snackState.showSnackbar(it); vm.clearReportMsg() } }
    LaunchedEffect(error)     { error?.let     { snackState.showSnackbar(it); vm.clearError() } }

    @Suppress("UNCHECKED_CAST")
    fun reportSummary()    = (report?.get("summary")    as? Map<String, Any>)
    @Suppress("UNCHECKED_CAST")
    fun reportJobs()       = (report?.get("jobs")       as? List<Map<String, Any>>) ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    fun reportPeriod()     = (report?.get("period")     as? Map<String, Any>)
    @Suppress("UNCHECKED_CAST")
    fun reportConnection() = (report?.get("connection") as? Map<String, Any>)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val partnerName = reportConnection()?.get("partner_name") as? String ?: "Partner"
                    Text("Report: $partnerName", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        vm.loadReport(connectionId, dateFrom.ifBlank { null }, dateTo.ifBlank { null })
                    }) { Icon(Icons.Default.Refresh, "Refresh") }
                }
            )
        },
        floatingActionButton = {
            if (report != null) {
                ExtendedFloatingActionButton(
                    onClick = { showSend = true },
                    icon    = { Icon(Icons.Default.Send, null) },
                    text    = { Text("Send Report") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackState) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Date range filter
            item {
                CRMCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("DATE RANGE")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value         = if (dateFrom.isNotBlank()) formatNetDate(dateFrom) else "",
                                onValueChange = {},
                                label         = { Text("From") },
                                placeholder   = { Text("Tap to select") },
                                readOnly      = true,
                                singleLine    = true,
                                trailingIcon  = { Icon(Icons.Default.CalendarMonth, null) },
                                enabled       = false,
                                colors        = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor         = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor       = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor        = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledPlaceholderColor  = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier      = Modifier.weight(1f).clickable { showFromPicker = true }
                            )
                            OutlinedTextField(
                                value         = if (dateTo.isNotBlank()) formatNetDate(dateTo) else "",
                                onValueChange = {},
                                label         = { Text("To") },
                                placeholder   = { Text("Tap to select") },
                                readOnly      = true,
                                singleLine    = true,
                                trailingIcon  = { Icon(Icons.Default.CalendarMonth, null) },
                                enabled       = false,
                                colors        = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor         = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor       = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor        = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledPlaceholderColor  = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier      = Modifier.weight(1f).clickable { showToPicker = true }
                            )
                        }
                        Button(
                            onClick  = { vm.loadReport(connectionId, dateFrom.ifBlank { null }, dateTo.ifBlank { null }) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled  = !reportLoading
                        ) {
                            if (reportLoading) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color       = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Load Report")
                        }
                    }
                }
            }

            if (reportLoading && report == null) {
                item { LoadingView() }
                return@LazyColumn
            }

            report?.let {
                // Summary card
                reportSummary()?.let { summary ->
                    item {
                        CRMCard {
                            Column(Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionLabel("SUMMARY")
                                reportPeriod()?.let { period ->
                                    val from = (period["from"] as? String)?.take(10) ?: "All time"
                                    val to   = (period["to"]   as? String)?.take(10) ?: "Today"
                                    Text("Period: $from → $to",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                HorizontalDivider()

                                @Composable fun sRow(label: String, value: String) {
                                    Row(Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(label, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(value, style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                val totalJobs     = (summary["total_jobs"]     as? Number)?.toInt()    ?: 0
                                val totalInvoiced = (summary["total_invoiced"] as? Number)?.toDouble() ?: 0.0
                                val yourTotal     = (summary["your_total"]     as? Number)?.toDouble() ?: 0.0
                                val theirTotal    = (summary["their_total"]    as? Number)?.toDouble() ?: 0.0
                                val balance       = (summary["balance"]        as? Number)?.toDouble() ?: 0.0
                                sRow("Total Jobs",      totalJobs.toString())
                                sRow("Total Invoiced",  "$${"%.2f".format(totalInvoiced)}")
                                sRow("Your Earnings",   "$${"%.2f".format(yourTotal)}")
                                sRow("Their Earnings",  "$${"%.2f".format(theirTotal)}")
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("Balance",
                                        style      = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold)
                                    val balColor = when {
                                        balance > 0  -> AppColors.Green
                                        balance < 0  -> AppColors.Red
                                        else         -> MaterialTheme.colorScheme.onSurface
                                    }
                                    val balLabel = when {
                                        balance > 0  -> "$${"%.2f".format(balance)} owed to you"
                                        balance < 0  -> "$${"%.2f".format(-balance)} you owe"
                                        else         -> "Even"
                                    }
                                    Text(balLabel,
                                        style      = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color      = balColor)
                                }
                            }
                        }
                    }
                }

                // Jobs list
                val jobs = reportJobs()
                if (jobs.isNotEmpty()) {
                    item { SectionLabel("COMPLETED JOBS") }
                    items(jobs) { job ->
                        CRMCard {
                            Column(Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text((job["description"] as? String) ?: "Job",
                                        style      = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier   = Modifier.weight(1f))
                                    val role = (job["your_role"] as? String) ?: ""
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (role == "sender") AppColors.Blue.copy(alpha = 0.15f)
                                                else AppColors.Green.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            if (role == "sender") "Sent" else "Received",
                                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style      = MaterialTheme.typography.labelSmall,
                                            color      = if (role == "sender") AppColors.Blue else AppColors.Green,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                (job["completed_at"] as? String)?.let { at ->
                                    Text("Completed: ${at.take(10)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Invoice: $${"%.2f".format((job["invoice_total"] as? Number)?.toDouble() ?: 0.0)}",
                                        style = MaterialTheme.typography.bodySmall)
                                    Text("Your split: $${"%.2f".format((job["your_split"] as? Number)?.toDouble() ?: 0.0)}",
                                        style      = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = AppColors.Green)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center) {
                            Text("No completed jobs in this period",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showSend) {
        SendReportSheet(
            connectionId = connectionId,
            dateFrom     = dateFrom.ifBlank { null },
            dateTo       = dateTo.ifBlank { null },
            vm           = vm,
            onDismiss    = { showSend = false }
        )
    }

    if (showFromPicker) {
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    fromPickerState.selectedDateMillis?.let { dateFrom = netMillisToIso(it) }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = fromPickerState) }
    }
    if (showToPicker) {
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    toPickerState.selectedDateMillis?.let { dateTo = netMillisToIso(it) }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = toPickerState) }
    }
}

// ─── SendReportSheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendReportSheet(
    connectionId: String,
    dateFrom: String?,
    dateTo: String?,
    vm: NetworkViewModel,
    onDismiss: () -> Unit
) {
    val sending by vm.reportSending.collectAsState()
    var recipientEmail by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, windowInsets = WindowInsets(0)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Send Partner Report",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)

            Text("The report will be emailed to your office's registered address as a PDF attachment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value         = recipientEmail,
                onValueChange = { recipientEmail = it },
                label         = { Text("Additional recipient (optional)") },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier      = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    vm.sendReport(
                        connectionId   = connectionId,
                        dateFrom       = dateFrom,
                        dateTo         = dateTo,
                        recipientEmail = recipientEmail.ifBlank { null },
                        onDone         = onDismiss
                    )
                },
                enabled  = !sending,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Default.Send, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Send Report")
            }
        }
    }
}

private fun netMillisToIso(millis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}

private fun formatNetDate(isoDate: String): String = try {
    val p = isoDate.split("-")
    val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    "${months[p[1].toInt() - 1]} ${p[2].toInt()}, ${p[0]}"
} catch (e: Exception) { isoDate }
