package com.ultimatepro.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.JobyRule
import com.ultimatepro.ui.common.AppButton
import com.ultimatepro.ui.common.AppSwitch
import com.ultimatepro.ui.common.ShineHairline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Labels matching web AutomationRules.jsx ───────────────────────────────────

private val TRIGGER_LABELS = mapOf(
    "job_created"       to "Job Created",
    "job_scheduled"     to "Job Scheduled",
    "job_completed"     to "Job Completed",
    "job_cancelled"     to "Job Cancelled",
    "estimate_sent"     to "Estimate Sent",
    "estimate_approved" to "Estimate Approved",
    "invoice_sent"      to "Invoice Sent",
    "invoice_paid"      to "Invoice Paid",
    "payment_received"  to "Payment Received"
)

private val ACTION_LABELS = mapOf(
    "send_sms"          to "Send SMS",
    "send_email"        to "Send Email",
    "send_notification" to "Send Notification",
    "auto_dispatch"     to "Auto-Dispatch",
    "create_follow_up"  to "Create Follow-Up"
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class AilotState(
    val rules: List<JobyRule> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class AilotViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {

    private val _state = MutableStateFlow(AilotState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = repo.getJobyRules()) {
                is Result.Success -> _state.update {
                    it.copy(rules = parseRules(r.data), loading = false)
                }
                is Result.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun toggleActive(rule: JobyRule) {
        val newActive = !rule.active
        // Optimistic update
        _state.update { s ->
            s.copy(rules = s.rules.map { if (it.id == rule.id) it.copy(active = newActive) else it })
        }
        viewModelScope.launch {
            when (val r = repo.updateJobyRule(rule.id, mapOf("active" to newActive))) {
                is Result.Error -> {
                    // Revert on failure
                    _state.update { s ->
                        s.copy(rules = s.rules.map { if (it.id == rule.id) it.copy(active = !newActive) else it })
                    }
                }
                else -> { /* optimistic update already applied */ }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRules(raw: List<Map<String, Any>>) = raw.map { m ->
        JobyRule(
            id              = m["id"] as? String ?: "",
            company_id      = m["company_id"] as? String ?: "",
            name            = m["name"] as? String ?: "",
            trigger_event   = m["trigger_event"] as? String ?: "",
            type            = m["type"] as? String ?: "",
            active          = m["active"] as? Boolean ?: true,
            delay_minutes   = (m["delay_minutes"] as? Number)?.toInt() ?: 0,
            notify_customer = m["notify_customer"] as? Boolean,
            notify_tech     = m["notify_tech"] as? Boolean,
            notify_owner    = m["notify_owner"] as? Boolean,
            dispatch_logic  = m["dispatch_logic"] as? String,
            sms_template    = m["sms_template"] as? String,
            email_template  = m["email_template"] as? String,
            created_at      = m["created_at"] as? String ?: ""
        )
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AilotScreen(
    onBack: () -> Unit,
    vm: AilotViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("⚡ Ailot", fontWeight = FontWeight.SemiBold)
                            Text("Smart Automation Rules", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                    },
                    actions = {
                        IconButton(onClick = { vm.load() }) { Icon(androidx.compose.ui.res.painterResource(com.ultimatepro.R.drawable.up_refresh), "Refresh") }
                    }
                )
                ShineHairline()
            }
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    AppButton(onClick = { vm.load() }, label = "Retry")
                }
            }
            state.rules.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FlashOn, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Text("No automation rules", style = MaterialTheme.typography.titleMedium)
                    Text("Configure rules on the web app", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.rules, key = { it.id }) { rule ->
                    RuleCard(rule = rule, onToggle = { vm.toggleActive(rule) })
                }
            }
        }
    }
}

@Composable
private fun RuleCard(rule: JobyRule, onToggle: () -> Unit) {
    val triggerLabel = TRIGGER_LABELS[rule.trigger_event] ?: rule.trigger_event
    val actionLabel  = ACTION_LABELS[rule.type] ?: rule.type

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.FlashOn,
                contentDescription = null,
                tint = if (rule.active) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    rule.name.ifBlank { "$triggerLabel → $actionLabel" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "When: $triggerLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Action: $actionLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (rule.delay_minutes > 0) {
                    Text(
                        "Delay: ${rule.delay_minutes} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AppSwitch(checked = rule.active, onCheckedChange = { onToggle() })
        }
    }
}
