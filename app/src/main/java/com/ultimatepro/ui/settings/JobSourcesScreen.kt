package com.ultimatepro.ui.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.AdChannel
import com.ultimatepro.domain.model.CommissionRule
import com.ultimatepro.domain.model.JobSource
import com.ultimatepro.ui.common.AppButton
import com.ultimatepro.ui.common.AppSwitch
import com.ultimatepro.ui.common.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class JobSourceViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {

    private val _contacts = MutableStateFlow<List<JobSource>>(emptyList())
    val contacts = _contacts.asStateFlow()

    private val _channels = MutableStateFlow<List<AdChannel>>(emptyList())
    val channels = _channels.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _msg = MutableStateFlow<String?>(null)
    val msg = _msg.asStateFlow()

    fun clearMsg() { _msg.value = null }

    fun loadContacts() {
        viewModelScope.launch {
            when (val r = repo.getSourceContacts()) {
                is Result.Success -> _contacts.value = r.data
                is Result.Error   -> _msg.value = r.message
            }
        }
    }

    fun loadChannels() {
        viewModelScope.launch {
            when (val r = repo.getAdChannels()) {
                is Result.Success -> _channels.value = r.data
                is Result.Error   -> _msg.value = r.message
            }
        }
    }

    fun addContact(
        name: String, companyName: String?, phone: String?, email: String?,
        profitPct: Double, sendUpdates: Boolean, sendClosings: Boolean, notes: String?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _loading.value = true
            when (val r = repo.createSourceContact(name, companyName, phone, email, profitPct, sendUpdates, sendClosings, notes)) {
                is Result.Success -> { _contacts.value = _contacts.value + r.data; onDone() }
                is Result.Error   -> _msg.value = r.message
            }
            _loading.value = false
        }
    }

    fun updateContact(id: String, body: Map<String, Any?>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            when (val r = repo.updateSourceContact(id, body)) {
                is Result.Success -> {
                    _contacts.value = _contacts.value.map { if (it.id == id) r.data else it }
                    onDone()
                }
                is Result.Error -> _msg.value = r.message
            }
        }
    }

    fun deleteContact(id: String) {
        viewModelScope.launch {
            when (val r = repo.deleteSourceContact(id)) {
                is Result.Success -> _contacts.value = _contacts.value.filter { it.id != id }
                is Result.Error   -> _msg.value = r.message
            }
        }
    }

    fun addChannel(name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            when (val r = repo.createAdChannel(name)) {
                is Result.Success -> { _channels.value = _channels.value + r.data; onDone() }
                is Result.Error   -> _msg.value = r.message
            }
            _loading.value = false
        }
    }

    fun updateChannel(id: String, name: String? = null, isActive: Boolean? = null) {
        viewModelScope.launch {
            when (val r = repo.updateAdChannel(id, name, isActive)) {
                is Result.Success -> _channels.value = _channels.value.map { if (it.id == id) r.data else it }
                is Result.Error   -> _msg.value = r.message
            }
        }
    }

    private val _commissionRules = MutableStateFlow<List<CommissionRule>>(emptyList())
    val commissionRules = _commissionRules.asStateFlow()

    fun loadCommissionRules() {
        viewModelScope.launch {
            when (val r = repo.getCommissionRules()) {
                is Result.Success -> _commissionRules.value = r.data
                is Result.Error   -> _msg.value = r.message
            }
        }
    }

    fun saveCommissionRule(
        ruleType: String, jobSourceId: String? = null, adChannelId: String? = null,
        pct: Double, notes: String? = null, onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _loading.value = true
            when (val r = repo.upsertCommissionRule(ruleType, jobSourceId, adChannelId, pct, notes)) {
                is Result.Success -> {
                    val existing = _commissionRules.value.filter {
                        !(it.ruleType == r.data.ruleType &&
                          it.jobSourceId == r.data.jobSourceId &&
                          it.adChannelId == r.data.adChannelId)
                    }
                    _commissionRules.value = existing + r.data
                    onDone()
                }
                is Result.Error -> _msg.value = r.message
            }
            _loading.value = false
        }
    }

    fun deleteCommissionRule(id: String) {
        viewModelScope.launch {
            when (val r = repo.deleteCommissionRule(id)) {
                is Result.Success -> _commissionRules.value = _commissionRules.value.filter { it.id != id }
                is Result.Error   -> _msg.value = r.message
            }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobSourcesScreen(onBack: () -> Unit, vm: JobSourceViewModel = hiltViewModel()) {
    val contacts        by vm.contacts.collectAsState()
    val channels        by vm.channels.collectAsState()
    val commissionRules by vm.commissionRules.collectAsState()
    val loading         by vm.loading.collectAsState()
    val msg             by vm.msg.collectAsState()

    var selectedTab          by remember { mutableStateOf(0) }
    var showAddContact       by remember { mutableStateOf(false) }
    var editContact          by remember { mutableStateOf<JobSource?>(null) }
    var showDeleteContact    by remember { mutableStateOf<JobSource?>(null) }
    var showAddChannel       by remember { mutableStateOf(false) }
    var editChannel          by remember { mutableStateOf<AdChannel?>(null) }
    var showCommissionDialog by remember { mutableStateOf(false) }
    var editRule             by remember { mutableStateOf<CommissionRule?>(null) }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.clearMsg() } }

    LaunchedEffect(Unit) { vm.loadContacts(); vm.loadChannels(); vm.loadCommissionRules() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job Sources", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { showAddContact = true }, containerColor = AppColors.Blue) {
                    Icon(Icons.Default.Add, null, tint = androidx.compose.ui.graphics.Color.White)
                }
            } else if (selectedTab == 2) {
                FloatingActionButton(onClick = { editRule = null; showCommissionDialog = true }, containerColor = AppColors.Blue) {
                    Icon(Icons.Default.Add, null, tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Source Contacts") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Ad Channels") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Commission") })
            }

            when (selectedTab) {
                0 -> SourceContactsTab(
                    contacts       = contacts,
                    loading        = loading,
                    onToggle       = { c -> vm.updateContact(c.id, mapOf("is_active" to !c.isActive)) },
                    onEdit         = { editContact = it },
                    onDelete       = { showDeleteContact = it }
                )
                1 -> AdChannelsTab(
                    channels       = channels,
                    loading        = loading,
                    onToggle       = { c -> vm.updateChannel(c.id, isActive = !c.isActive) },
                    onEditName     = { editChannel = it },
                    onAddCustom    = { showAddChannel = true }
                )
                2 -> CommissionRulesTab(
                    rules    = commissionRules,
                    onEdit   = { editRule = it; showCommissionDialog = true },
                    onDelete = { vm.deleteCommissionRule(it.id) }
                )
            }
        }
    }

    // ── Add/Edit Contact Dialog ─────────────────────────────────────────────
    if (showAddContact || editContact != null) {
        AddEditContactDialog(
            initial   = editContact,
            loading   = loading,
            onDismiss = { showAddContact = false; editContact = null },
            onSave    = { name, co, phone, email, pct, upd, cls, notes ->
                val initial = editContact
                if (initial == null) {
                    vm.addContact(name, co, phone, email, pct, upd, cls, notes) {
                        showAddContact = false
                    }
                } else {
                    vm.updateContact(initial.id, mapOf(
                        "name" to name, "company_name" to co.ifBlank { null },
                        "phone" to phone.ifBlank { null }, "email" to email.ifBlank { null },
                        "profit_allocation_pct" to pct,
                        "send_updates" to upd, "send_closings" to cls,
                        "notes" to notes.ifBlank { null }
                    )) { editContact = null }
                }
            }
        )
    }

    // ── Delete Confirm ──────────────────────────────────────────────────────
    showDeleteContact?.let { c ->
        AlertDialog(
            onDismissRequest = { showDeleteContact = null },
            title   = { Text("Remove Source?") },
            text    = { Text("\"${c.name}\" will be archived and won't appear in new job forms.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteContact(c.id); showDeleteContact = null },
                    colors  = ButtonDefaults.textButtonColors(contentColor = AppColors.Red)
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { showDeleteContact = null }) { Text("Cancel") } }
        )
    }

    // ── Commission Rule Dialog ─────────────────────────────────────────────
    if (showCommissionDialog || editRule != null) {
        CommissionRuleDialog(
            initial   = editRule,
            contacts  = contacts,
            channels  = channels,
            loading   = loading,
            onDismiss = { showCommissionDialog = false; editRule = null },
            onSave    = { ruleType, sourceId, channelId, pct, notes ->
                vm.saveCommissionRule(ruleType, sourceId, channelId, pct, notes) {
                    showCommissionDialog = false; editRule = null
                }
            }
        )
    }

    // ── Add Custom Channel Dialog ───────────────────────────────────────────
    if (showAddChannel || editChannel != null) {
        var channelName by remember(editChannel) { mutableStateOf(editChannel?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showAddChannel = false; editChannel = null },
            title   = { Text(if (editChannel != null) "Rename Channel" else "Add Custom Channel", fontWeight = FontWeight.Bold) },
            text    = {
                OutlinedTextField(
                    value         = channelName,
                    onValueChange = { channelName = it },
                    label         = { Text("Channel name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ch = editChannel
                        if (ch == null) {
                            vm.addChannel(channelName.trim()) { showAddChannel = false }
                        } else {
                            vm.updateChannel(ch.id, name = channelName.trim())
                            editChannel = null
                        }
                    },
                    enabled = channelName.isNotBlank()
                ) { Text(if (editChannel != null) "Save" else "Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddChannel = false; editChannel = null }) { Text("Cancel") }
            }
        )
    }
}

// ── Source Contacts Tab ───────────────────────────────────────────────────────

@Composable
private fun SourceContactsTab(
    contacts: List<JobSource>,
    loading:  Boolean,
    onToggle: (JobSource) -> Unit,
    onEdit:   (JobSource) -> Unit,
    onDelete: (JobSource) -> Unit
) {
    if (loading && contacts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (contacts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.People, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No source contacts yet", fontWeight = FontWeight.SemiBold)
                Text("Tap + to add referral partners, home warranty companies,\nor anyone who sends you jobs.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(contacts, key = { it.id }) { contact ->
            SourceContactCard(contact, onToggle, onEdit, onDelete)
        }
    }
}

@Composable
private fun SourceContactCard(
    contact:  JobSource,
    onToggle: (JobSource) -> Unit,
    onEdit:   (JobSource) -> Unit,
    onDelete: (JobSource) -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(contact.name, fontWeight = FontWeight.SemiBold)
                contact.companyName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                contact.phone?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (contact.profitAllocationPct > 0) {
                    Text("${contact.profitAllocationPct.toInt()}% profit allocation",
                        style = MaterialTheme.typography.labelSmall, color = AppColors.Green)
                }
            }
            AppSwitch(checked = contact.isActive, onCheckedChange = { onToggle(contact) })
            IconButton(onClick = { onEdit(contact) }) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = { onDelete(contact) }) { Icon(Icons.Default.Delete, null, tint = AppColors.Red) }
        }
    }
}

// ── Ad Channels Tab ───────────────────────────────────────────────────────────

@Composable
private fun AdChannelsTab(
    channels:   List<AdChannel>,
    loading:    Boolean,
    onToggle:   (AdChannel) -> Unit,
    onEditName: (AdChannel) -> Unit,
    onAddCustom: () -> Unit
) {
    if (loading && channels.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
        items(channels, key = { it.id }) { channel ->
            ListItem(
                headlineContent = { Text(channel.name) },
                supportingContent = if (channel.isCustom) { { Text("Custom", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } else null,
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (channel.isCustom) {
                            IconButton(onClick = { onEditName(channel) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        AppSwitch(checked = channel.isActive, onCheckedChange = { onToggle(channel) })
                    }
                }
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        item {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                AppButton(
                    onClick = onAddCustom,
                    label = "Add Custom Channel",
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Default.Add
                )
            }
        }
    }
}

// ── Add/Edit Contact Dialog ───────────────────────────────────────────────────

@Composable
private fun AddEditContactDialog(
    initial:  JobSource?,
    loading:  Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, company: String, phone: String, email: String,
             profitPct: Double, sendUpdates: Boolean, sendClosings: Boolean, notes: String) -> Unit
) {
    val isEdit = initial != null
    var name         by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var company      by remember(initial) { mutableStateOf(initial?.companyName ?: "") }
    var phone        by remember(initial) { mutableStateOf(initial?.phone ?: "") }
    var email        by remember(initial) { mutableStateOf(initial?.email ?: "") }
    var profitPct    by remember(initial) { mutableStateOf(initial?.profitAllocationPct?.toString() ?: "0") }
    var sendUpdates  by remember(initial) { mutableStateOf(initial?.sendUpdates ?: true) }
    var sendClosings by remember(initial) { mutableStateOf(initial?.sendClosings ?: true) }
    var notes        by remember(initial) { mutableStateOf(initial?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Source Contact" else "Add Source Contact", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = company, onValueChange = { company = it },
                    label = { Text("Company (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Phone") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = profitPct, onValueChange = { profitPct = it },
                    label = { Text("Profit allocation %") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    supportingText = { Text("% of job net given to this contact") }
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Send status updates", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    AppSwitch(checked = sendUpdates, onCheckedChange = { sendUpdates = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Send job closings", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    AppSwitch(checked = sendClosings, onCheckedChange = { sendClosings = it })
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optional)") }, minLines = 2,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pct = profitPct.toDoubleOrNull() ?: 0.0
                    onSave(name.trim(), company, phone, email, pct, sendUpdates, sendClosings, notes)
                },
                enabled = name.isNotBlank() && !loading
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(if (isEdit) "Save" else "Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Commission Rules Tab ──────────────────────────────────────────────────────

@Composable
private fun CommissionRulesTab(
    rules:    List<CommissionRule>,
    onEdit:   (CommissionRule) -> Unit,
    onDelete: (CommissionRule) -> Unit
) {
    val defaultRule = rules.find { it.ruleType == "default" }
    val specificRules = rules.filter { it.ruleType != "default" }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Default commission card
        item {
            Text("Default Commission", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = AppColors.Blue)
            Spacer(Modifier.height(6.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Percent, null, modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        if (defaultRule != null) {
                            Text("Tech keeps ${defaultRule.techCommissionPct.toInt()}%",
                                fontWeight = FontWeight.SemiBold)
                            Text("Applied to all jobs with no specific source rule",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("No default set", fontWeight = FontWeight.SemiBold)
                            Text("Uses each tech's individual pay settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (defaultRule != null) {
                        IconButton(onClick = { onEdit(defaultRule) }) {
                            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDelete(defaultRule) }) {
                            Icon(Icons.Default.Delete, null, tint = AppColors.Red)
                        }
                    } else {
                        TextButton(onClick = {
                            onEdit(CommissionRule(id = "", ruleType = "default", techCommissionPct = 0.0))
                        }) { Text("Set Default") }
                    }
                }
            }
        }

        // Source-specific rules
        item {
            Spacer(Modifier.height(6.dp))
            Text("Source-Specific Rules", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = AppColors.Blue)
            Spacer(Modifier.height(6.dp))
        }

        if (specificRules.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No source-specific rules set. All jobs use your tech pay settings defaults.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        } else {
            items(specificRules, key = { it.id }) { rule ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val (icon, sourceName) = when (rule.ruleType) {
                            "source_contact" -> Icons.Default.Person to (rule.jobSourceName ?: "Source Contact")
                            "ad_channel"     -> Icons.Default.Campaign to (rule.adChannelName ?: "Ad Channel")
                            "network"        -> Icons.Default.Handshake to "Network Jobs"
                            else             -> Icons.Default.Percent to rule.ruleType
                        }
                        Icon(icon, null, modifier = Modifier.size(22.dp),
                            tint = when (rule.ruleType) {
                                "source_contact" -> AppColors.Blue
                                "ad_channel"     -> AppColors.Orange
                                "network"        -> AppColors.Green
                                else             -> MaterialTheme.colorScheme.onSurfaceVariant
                            })
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sourceName, fontWeight = FontWeight.SemiBold)
                            Text("Tech keeps ${rule.techCommissionPct.toInt()}%",
                                style = MaterialTheme.typography.bodySmall, color = AppColors.Purple)
                            rule.notes?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onEdit(rule) }) {
                            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDelete(rule) }) {
                            Icon(Icons.Default.Delete, null, tint = AppColors.Red)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Commission Rule Dialog ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommissionRuleDialog(
    initial:  CommissionRule?,
    contacts: List<JobSource>,
    channels: List<AdChannel>,
    loading:  Boolean,
    onDismiss: () -> Unit,
    onSave: (ruleType: String, jobSourceId: String?, adChannelId: String?,
             pct: Double, notes: String?) -> Unit
) {
    val isEdit = initial != null && initial.id.isNotBlank()

    // Determine initial apply-to selection
    val initApplyTo = when (initial?.ruleType) {
        "source_contact" -> 1
        "ad_channel"     -> 2
        "network"        -> 3
        else             -> 0   // default
    }
    var applyTo      by remember(initial) { mutableIntStateOf(initApplyTo) }
    var sourceId     by remember(initial) { mutableStateOf(initial?.jobSourceId ?: "") }
    var channelId    by remember(initial) { mutableStateOf(initial?.adChannelId ?: "") }
    var pctText      by remember(initial) { mutableStateOf(initial?.techCommissionPct?.toInt()?.toString() ?: "") }
    var notes        by remember(initial) { mutableStateOf(initial?.notes ?: "") }
    var contactOpen  by remember { mutableStateOf(false) }
    var channelOpen  by remember { mutableStateOf(false) }

    val applyLabels = listOf("Default (all jobs)", "Source contact", "Ad channel", "Network jobs")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Commission Rule" else "Add Commission Rule", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Apply To picker
                Text("Apply to:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                applyLabels.forEachIndexed { i, label ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = applyTo == i, onClick = { applyTo = i; sourceId = ""; channelId = "" })
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }

                // Conditional picker
                if (applyTo == 1 && contacts.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = contactOpen,
                        onExpandedChange = { contactOpen = it }
                    ) {
                        OutlinedTextField(
                            value = contacts.find { it.id == sourceId }?.name ?: "Select contact",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Source contact") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(contactOpen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(expanded = contactOpen, onDismissRequest = { contactOpen = false }) {
                            contacts.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    onClick = { sourceId = c.id; contactOpen = false }
                                )
                            }
                        }
                    }
                }

                if (applyTo == 2 && channels.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = channelOpen,
                        onExpandedChange = { channelOpen = it }
                    ) {
                        OutlinedTextField(
                            value = channels.find { it.id == channelId }?.name ?: "Select channel",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Ad channel") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(channelOpen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(expanded = channelOpen, onDismissRequest = { channelOpen = false }) {
                            channels.filter { it.isActive }.forEach { ch ->
                                DropdownMenuItem(
                                    text = { Text(ch.name) },
                                    onClick = { channelId = ch.id; channelOpen = false }
                                )
                            }
                        }
                    }
                }

                // Commission %
                OutlinedTextField(
                    value = pctText,
                    onValueChange = { pctText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Tech commission %") },
                    singleLine = true,
                    trailingIcon = { Text("%", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 12.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                )

                // Notes
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            val pct = pctText.toDoubleOrNull() ?: -1.0
            val validSource = applyTo != 1 || sourceId.isNotBlank()
            val validChannel = applyTo != 2 || channelId.isNotBlank()
            Button(
                onClick = {
                    val ruleType = when (applyTo) {
                        1    -> "source_contact"
                        2    -> "ad_channel"
                        3    -> "network"
                        else -> "default"
                    }
                    onSave(ruleType,
                        if (applyTo == 1) sourceId else null,
                        if (applyTo == 2) channelId else null,
                        pct, notes.ifBlank { null })
                },
                enabled = pct in 0.0..100.0 && validSource && validChannel && !loading
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(if (isEdit) "Save" else "Add Rule")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
