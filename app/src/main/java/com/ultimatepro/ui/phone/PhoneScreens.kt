package com.ultimatepro.ui.phone

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.*
import com.ultimatepro.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ultimatepro.ui.common.*

// ── ViewModel ─────────────────────────────────────────────────────────────

data class PhoneUiState(
    val loading: Boolean = true,
    val callLogs: List<CallLog> = emptyList(),
    val secondChance: SecondChanceResponse = SecondChanceResponse(),
    val phoneNumbers: List<PhoneNumber> = emptyList(),
    val csrStats: List<CsrStats> = emptyList(),
    val sourceStats: List<Map<String, Any>> = emptyList(),
    val liveQueue: LiveQueueResponse = LiveQueueResponse(),
    val conversations: List<SmsConversation> = emptyList(),
    val conversationsLoading: Boolean = false,
    val threadMessages: List<SmsMessage> = emptyList(),
    val threadLoading: Boolean = false,
    val threadConversation: SmsConversation? = null,
    val customerMessages: List<SmsMessage> = emptyList(),
    val jobMessages: List<SmsMessage> = emptyList(),
    val smsMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class PhoneViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {
    private val _state = MutableStateFlow(PhoneUiState())
    val state: StateFlow<PhoneUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val logs    = repo.getCallLogs()
            val sc      = repo.getSecondChanceLeads()
            val numbers = repo.getPhoneNumbers()

            _state.update {
                it.copy(
                    loading      = false,
                    callLogs     = (logs as? Result.Success)?.data?.calls ?: emptyList(),
                    secondChance = (sc  as? Result.Success)?.data ?: SecondChanceResponse(),
                    phoneNumbers = (numbers as? Result.Success)?.data ?: emptyList(),
                    error        = (logs as? Result.Error)?.message
                )
            }
        }
    }

    fun loadCsrStats() {
        viewModelScope.launch {
            when (val r = repo.getCsrStats()) {
                is Result.Success -> {
                    @Suppress("UNCHECKED_CAST")
                    val stats = (r.data["stats"] as? List<Map<String, Any>>) ?: emptyList()
                    // Parse into CsrStats
                    _state.update { it.copy(csrStats = stats.map { m ->
                        CsrStats(
                            first_name        = m["first_name"]?.toString() ?: "",
                            last_name         = m["last_name"]?.toString() ?: "",
                            color             = m["color"]?.toString() ?: "#1565C0",
                            total_calls       = (m["total_calls"] as? Number)?.toInt() ?: 0,
                            booked_calls      = (m["booked_calls"] as? Number)?.toInt() ?: 0,
                            missed_calls      = (m["missed_calls"] as? Number)?.toInt() ?: 0,
                            booking_rate_pct  = (m["booking_rate_pct"] as? Number)?.toDouble(),
                            avg_duration_sec  = (m["avg_duration_sec"] as? Number)?.toInt(),
                        )
                    })}
                }
                else -> {}
            }
        }
    }

    fun updateSecondChance(id: String, status: String, notes: String? = null) {
        viewModelScope.launch {
            repo.updateSecondChanceLead(id, buildMap { put("status", status); if (notes != null) put("notes", notes) })
            load()
        }
    }

    fun sendSecondChanceSms(id: String) {
        viewModelScope.launch { repo.sendSecondChanceSms(id); load() }
    }

    fun loadLiveQueue() {
        viewModelScope.launch {
            when (val r = repo.getLiveQueue()) {
                is Result.Success -> _state.update { it.copy(liveQueue = r.data) }
                else -> {}
            }
        }
    }

    fun activateCallSecure(jobId: String) {
        viewModelScope.launch { repo.activateCallSecure(jobId) }
    }

    fun sendSms(to: String, body: String, customerId: String? = null) {
        viewModelScope.launch { repo.sendSms(to, body, customerId) }
    }

    fun loadConversations() {
        viewModelScope.launch {
            _state.update { it.copy(conversationsLoading = true) }
            when (val r = repo.getSmsConversations()) {
                is Result.Success -> _state.update { it.copy(conversations = r.data, conversationsLoading = false) }
                else -> _state.update { it.copy(conversationsLoading = false) }
            }
        }
    }

    fun loadThread(conversationId: String) {
        viewModelScope.launch {
            _state.update { it.copy(threadLoading = true) }
            val conv = _state.value.conversations.find { it.id == conversationId }
            _state.update { it.copy(threadConversation = conv) }
            when (val r = repo.getConversationMessages(conversationId)) {
                is Result.Success -> _state.update { it.copy(threadMessages = r.data, threadLoading = false) }
                else -> _state.update { it.copy(threadLoading = false) }
            }
        }
    }

    fun sendReply(conversationId: String, message: String, onSent: () -> Unit) {
        viewModelScope.launch {
            when (val r = repo.sendSmsReply(conversationId, message)) {
                is Result.Success -> {
                    _state.update { it.copy(threadMessages = it.threadMessages + r.data) }
                    onSent()
                }
                else -> {}
            }
        }
    }

    fun clearSmsMessage() { _state.update { it.copy(smsMessage = null) } }

    fun loadCustomerMessages(customerId: String) {
        viewModelScope.launch {
            when (val r = repo.getCustomerMessages(customerId)) {
                is Result.Success -> _state.update { it.copy(customerMessages = r.data) }
                else -> {}
            }
        }
    }

    fun loadJobMessages(jobId: String) {
        viewModelScope.launch {
            when (val r = repo.getJobMessages(jobId)) {
                is Result.Success -> _state.update { it.copy(jobMessages = r.data) }
                else -> {}
            }
        }
    }
}

// ── Phone Hub Screen ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneScreen(
    onSecondChance:   () -> Unit,
    onQueue:          () -> Unit,
    onCustomer:       (String) -> Unit,
    onSmsThread:      (String) -> Unit = {},
    vm: PhoneViewModel = hiltViewModel()
) {
    val state   by vm.state.collectAsState()
    val context = LocalContext.current
    var tab     by remember { mutableIntStateOf(0) }
    val tabs    = listOf("Call Log", "Messages", "CSR Stats", "Numbers")

    LaunchedEffect(tab) {
        when (tab) {
            1 -> vm.loadConversations()
            2 -> vm.loadCsrStats()
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Phone System", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = onQueue) {
                    BadgedBox(badge = {}) { Icon(Icons.Default.LiveTv, "Live Queue") }
                }
                IconButton(onClick = { vm.load() }) { Icon(Icons.Default.Refresh, null) }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Second Chance banner
            val scNew = state.secondChance.stats.total
            if (scNew > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = CardDefaults.cardColors(containerColor = AppColors.Gold.copy(alpha = 0.15f)),
                    onClick  = onSecondChance
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Replay, null, tint = AppColors.Gold, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("$scNew Second Chance Leads", fontWeight = FontWeight.Bold, color = AppColors.Gold)
                            val recovery = state.secondChance.stats.recovery_rate_pct
                            Text("${state.secondChance.stats.booked} booked${if (recovery != null) " • ${"%.0f".format(recovery)}% recovery" else ""}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = AppColors.Gold)
                    }
                }
            }

            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }

            when (tab) {
                0 -> CallLogTab(state.callLogs, onCustomer, context)
                1 -> SmsConversationsTab(state.conversations, state.conversationsLoading, onSmsThread)
                2 -> CsrStatsTab(state.csrStats)
                3 -> NumbersTab(state.phoneNumbers)
            }
        }
    }
}

@Composable
private fun CallLogTab(
    calls: List<CallLog>,
    onCustomer: (String) -> Unit,
    context: android.content.Context
) {
    if (calls.isEmpty()) { EmptyView("No calls yet", Icons.Default.Phone); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(calls, key = { it.id }) { call ->
            val isSms     = call.type.startsWith("sms")
            val isInbound = call.type.contains("inbound")
            val color     = when {
                call.status == "no-answer" -> AppColors.Red
                isSms && isInbound         -> AppColors.Purple
                isSms                      -> AppColors.Blue
                isInbound                  -> AppColors.Green
                else                       -> AppColors.Blue
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when {
                            isSms && isInbound         -> Icons.Default.Sms
                            isSms                      -> Icons.Default.Send
                            call.status == "no-answer" -> Icons.Default.PhoneMissed
                            isInbound                  -> Icons.Default.CallReceived
                            else                       -> Icons.Default.CallMade
                        },
                        null, tint = color, modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(call.customerName ?: call.from_number ?: "Unknown",
                            fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            call.source_tag?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = AppColors.Blue) }
                            call.duration_sec?.let { Text(formatDuration(it), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            call.disposition?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = AppColors.Green) }
                        }
                        call.created_at?.take(10)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Call back button
                    val number = call.from_number
                    if (number != null) {
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                        }) { Icon(Icons.Default.Phone, "Call back", tint = AppColors.Blue) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CsrStatsTab(stats: List<CsrStats>) {
    if (stats.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(stats) { csr ->
            val color = try { Color(android.graphics.Color.parseColor(csr.color)) } catch (e: Exception) { AppColors.Blue }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarCircle(csr.fullName.take(2), color)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(csr.fullName, fontWeight = FontWeight.SemiBold)
                        Text("${csr.total_calls} calls  •  ${csr.booked_calls} booked",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        csr.booking_rate_pct?.let {
                            Text("${"%.0f".format(it)}%", fontWeight = FontWeight.Bold, color = AppColors.Green)
                            Text("booking rate", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumbersTab(numbers: List<PhoneNumber>) {
    if (numbers.isEmpty()) { EmptyView("No phone numbers configured", Icons.Default.PhoneAndroid); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(numbers) { num ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneAndroid, null, tint = AppColors.Blue, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(num.number, fontWeight = FontWeight.SemiBold)
                        Text(num.friendly_name ?: num.type, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        num.source_tag?.let { Tag ->
                            Text("Source: $Tag", style = MaterialTheme.typography.labelSmall, color = AppColors.Blue)
                        }
                    }
                    if (num.active) StatusBadge("Active", AppColors.Green, small = true)
                    else StatusBadge("Inactive", AppColors.Slate, small = true)
                }
            }
        }
    }
}

// ── Second Chance Leads Screen ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondChanceScreen(
    onBack: () -> Unit,
    onCustomer: (String) -> Unit,
    vm: PhoneViewModel = hiltViewModel()
) {
    val state  by vm.state.collectAsState()
    val sc      = state.secondChance
    var tab     by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Second Chance Leads", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = { IconButton(onClick = { vm.load() }) { Icon(Icons.Default.Refresh, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Stats row
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Total" to "${sc.stats.total}",
                    "Called Back" to "${sc.stats.called_count}",
                    "Booked" to "${sc.stats.booked}",
                    "Recovery" to "${sc.stats.recovery_rate_pct?.let { "%.0f".format(it) } ?: "—"}%"
                ).forEach { (label, value) ->
                    Card(Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            TabRow(selectedTabIndex = tab) {
                listOf("New", "Called", "Booked", "Lost").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }

            val statuses = listOf("new", "called_back", "booked", "lost")
            val filtered = sc.leads.filter { it.status == statuses[tab] }

            if (filtered.isEmpty()) {
                EmptyView("No ${listOf("new","called-back","booked","lost")[tab]} leads", Icons.Default.Replay)
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { lead ->
                        SecondChanceCard(
                            lead      = lead,
                            onCallBack = {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${lead.from_number}")))
                                vm.updateSecondChance(lead.id, "called_back")
                            },
                            onBook    = { vm.updateSecondChance(lead.id, "booked") },
                            onLost    = { vm.updateSecondChance(lead.id, "lost") },
                            onSendSms = { vm.sendSecondChanceSms(lead.id) },
                            onCustomer = lead.from_number.let { { /* no customer id to navigate */ } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondChanceCard(
    lead: SecondChanceLead,
    onCallBack: () -> Unit,
    onBook:     () -> Unit,
    onLost:     () -> Unit,
    onSendSms:  () -> Unit,
    onCustomer: () -> Unit
) {
    val reasonColor = when (lead.reason) {
        "missed"    -> AppColors.Red
        "abandoned" -> AppColors.Orange
        else        -> AppColors.Slate
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Replay, null, tint = reasonColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(lead.displayName, fontWeight = FontWeight.SemiBold)
                    Text(lead.from_number, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(lead.reason.replace("_", " ").replaceFirstChar { it.uppercase() }, reasonColor, small = true)
            }
            lead.call_date?.take(10)?.let {
                Spacer(Modifier.height(4.dp))
                Text("Call on $it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (lead.status == "new" || lead.status == "assigned") {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = onCallBack, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.Phone, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Call", fontSize = 12.sp)
                    }
                    Button(onClick = onSendSms, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Purple),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.Sms, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("SMS", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onBook, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
                        Text("Booked", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onLost, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
                        Text("Lost", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ── Live Queue Screen ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveQueueScreen(onBack: () -> Unit, vm: PhoneViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val queue = state.liveQueue

    LaunchedEffect(Unit) { vm.loadLiveQueue() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Live Call Queue", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = { IconButton(onClick = { vm.loadLiveQueue() }) { Icon(Icons.Default.Refresh, null) } }
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Stats summary card
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Blue.copy(alpha = 0.08f))) {
                    Row(Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${queue.stats.calls_active}", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = AppColors.Blue)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhoneInTalk, null, tint = AppColors.Blue, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Active", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${queue.stats.calls_ringing}", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = AppColors.Orange)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhonePaused, null, tint = AppColors.Orange, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ringing", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val avgWait = queue.stats.avg_wait_sec
                            Text(if (avgWait != null) formatDuration(avgWait) else "—",
                                fontWeight = FontWeight.Bold, fontSize = 22.sp, color = AppColors.Slate)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AvTimer, null, tint = AppColors.Slate, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Avg Wait", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Active calls header
            if (queue.active_calls.isNotEmpty()) {
                item {
                    Text("Active Calls", fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp))
                }
                items(queue.active_calls, key = { it.id }) { call ->
                    val isActive  = call.status == "in-progress"
                    val callColor = if (isActive) AppColors.Green else AppColors.Orange
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isActive) Icons.Default.PhoneInTalk else Icons.Default.PhonePaused,
                                null, tint = callColor, modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(call.customerName, fontWeight = FontWeight.SemiBold)
                                Text(call.from_number ?: "", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                call.line_name?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = AppColors.Blue)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                StatusBadge(
                                    if (isActive) "In Progress" else "Ringing",
                                    callColor, small = true
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(formatDuration(call.seconds_elapsed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(call.agentName, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Green.copy(alpha = 0.08f))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = AppColors.Green)
                            Spacer(Modifier.width(12.dp))
                            Text("No active calls right now", color = AppColors.Green, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

@Composable
private fun SmsConversationsTab(
    conversations: List<SmsConversation>,
    loading: Boolean,
    onThread: (String) -> Unit
) {
    if (loading) { LoadingView(); return }
    if (conversations.isEmpty()) { EmptyView("No SMS conversations yet", Icons.Default.Sms); return }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(conversations, key = { it.id }) { conv ->
            val hasUnread = conv.unreadCount > 0
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onThread(conv.id) },
                shape    = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sms, null, tint = AppColors.Blue, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            conv.customerName?.trim()?.ifBlank { null } ?: conv.customerPhone,
                            fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold
                        )
                        conv.lastMessage?.let {
                            Text(
                                it.take(60),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                    if (hasUnread) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(AppColors.Blue, shape = RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${conv.unreadCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ── SMS Thread Screen ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsThreadScreen(
    conversationId: String,
    onBack: () -> Unit,
    vm: PhoneViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    var replyText by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(conversationId) { vm.loadThread(conversationId) }

    LaunchedEffect(state.threadMessages.size) {
        if (state.threadMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.threadMessages.lastIndex)
        }
    }

    val conv = state.threadConversation
    val displayName = conv?.customerName?.trim()?.ifBlank { null } ?: conv?.customerPhone ?: "Messages"

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(displayName, fontWeight = FontWeight.Bold)
                        conv?.customerPhone?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value         = replyText,
                    onValueChange = { replyText = it },
                    modifier      = Modifier.weight(1f),
                    placeholder   = { Text("Type a message…") },
                    shape         = RoundedCornerShape(24.dp),
                    maxLines      = 4,
                )
                IconButton(
                    onClick  = {
                        val msg = replyText.trim()
                        if (msg.isNotBlank()) {
                            vm.sendReply(conversationId, msg) { replyText = "" }
                        }
                    },
                    enabled = replyText.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, "Send", tint = if (replyText.isNotBlank()) AppColors.Blue else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) { padding ->
        if (state.threadLoading) { LoadingView(); return@Scaffold }
        if (state.threadMessages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No messages yet. Send the first one!", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(state.threadMessages, key = { it.id }) { msg ->
                SmsBubble(msg)
            }
        }
    }
}

// Public composable reused in customer/job detail tabs
@Composable
fun SmsMessagesList(
    messages: List<SmsMessage>,
    conversationId: String?,
    onOpenThread: ((String) -> Unit)?
) {
    Column(Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No messages yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id }) { msg -> SmsBubble(msg) }
            }
        }
        if (onOpenThread != null && conversationId != null) {
            Button(
                onClick  = { onOpenThread(conversationId) },
                modifier = Modifier.fillMaxWidth().padding(12.dp).height(48.dp),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Sms, null)
                Spacer(Modifier.width(8.dp))
                Text("Open Conversation", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SmsBubble(msg: SmsMessage) {
    val isOutbound = msg.direction == "outbound"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutbound) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isOutbound) Alignment.End else Alignment.Start) {
            Surface(
                shape  = RoundedCornerShape(
                    topStart    = 16.dp,
                    topEnd      = 16.dp,
                    bottomStart = if (isOutbound) 16.dp else 4.dp,
                    bottomEnd   = if (isOutbound) 4.dp  else 16.dp
                ),
                color  = if (isOutbound) AppColors.Blue else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    text     = msg.body,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color    = if (isOutbound) Color.White else MaterialTheme.colorScheme.onSurface,
                    style    = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text  = msg.createdAt.take(16).replace('T', ' '),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
