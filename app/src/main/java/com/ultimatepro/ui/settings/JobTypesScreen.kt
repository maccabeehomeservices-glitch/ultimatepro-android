package com.ultimatepro.ui.settings

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// P3.8: Job Types settings — pick trades → toggle suggested job types + add custom.
// The active set drives the new-job form chips (GET /company/job-types).
data class TradeInfo(val key: String, val label: String, val jobTypes: List<String>)
data class JobTypesState(
    val trades: List<TradeInfo> = emptyList(),
    val selected: List<String> = emptyList(),
    val active: List<Triple<String, String, String>> = emptyList(), // (id, key, label)
    val loading: Boolean = true,
    val saving: Boolean = false,
)

@HiltViewModel
class JobTypesViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {
    private val _s = MutableStateFlow(JobTypesState())
    val state = _s.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _s.value = _s.value.copy(loading = true)
            when (val r = repo.getTrades()) {
                is Result.Success -> {
                    val reg = r.data["registry"] as? Map<*, *>
                    val trades = reg?.entries?.mapNotNull { (k, v) ->
                        val key = k as? String; val vm = v as? Map<*, *>
                        val label = vm?.get("label") as? String
                        val jt = (vm?.get("jobTypes") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                        if (key != null && label != null) TradeInfo(key, label, jt) else null
                    } ?: emptyList()
                    val selected = (r.data["selected"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    _s.value = _s.value.copy(trades = trades, selected = selected)
                }
                is Result.Error -> {}
            }
            _s.value = _s.value.copy(active = repo.getJobTypesFull(), loading = false)
        }
    }

    fun toggleTrade(key: String) {
        val cur = _s.value.selected
        val next = if (cur.contains(key)) cur.filter { it != key } else cur + key
        viewModelScope.launch {
            _s.value = _s.value.copy(saving = true, selected = next)
            repo.setTrades(next)
            _s.value = _s.value.copy(saving = false)
        }
    }

    fun addType(label: String) {
        if (label.isBlank()) return
        viewModelScope.launch {
            _s.value = _s.value.copy(saving = true)
            repo.addJobType(label.trim())
            _s.value = _s.value.copy(active = repo.getJobTypesFull(), saving = false)
        }
    }

    fun removeType(id: String) {
        viewModelScope.launch {
            _s.value = _s.value.copy(saving = true)
            repo.deleteJobType(id)
            _s.value = _s.value.copy(active = repo.getJobTypesFull(), saving = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun JobTypesScreen(onBack: () -> Unit, vm: JobTypesViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    var custom by remember { mutableStateOf("") }
    val activeKeys = remember(s.active) { s.active.map { it.second }.toSet() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Job Types", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
        )
    }) { pad ->
        if (s.loading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Trades
            Text("Your trades", fontWeight = FontWeight.SemiBold)
            Text("Pick the trades you do — each suggests common job types below.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                s.trades.forEach { t ->
                    FilterChip(selected = s.selected.contains(t.key), enabled = !s.saving,
                        onClick = { vm.toggleTrade(t.key) }, label = { Text(t.label) })
                }
            }

            // Suggested types for selected trades
            if (s.selected.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Suggested job types", fontWeight = FontWeight.SemiBold)
                Text("Tap to add. Added types are marked.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.trades.filter { s.selected.contains(it.key) }.forEach { t ->
                    Spacer(Modifier.height(8.dp))
                    Text(t.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        t.jobTypes.forEach { label ->
                            val added = activeKeys.contains(label.trim().lowercase())
                            AssistChip(
                                onClick = { if (!added) vm.addType(label) },
                                enabled = !added && !s.saving,
                                label = { Text(if (added) "$label ✓" else label) },
                                leadingIcon = if (!added) { { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) } } else null,
                            )
                        }
                    }
                }
            }

            // Active set + custom add
            Spacer(Modifier.height(20.dp))
            Text("Your job types", fontWeight = FontWeight.SemiBold)
            Text("These are the chips shown when creating a job.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                s.active.forEach { (id, _, label) ->
                    InputChip(selected = false, onClick = {}, label = { Text(label) },
                        trailingIcon = { IconButton(onClick = { vm.removeType(id) }, Modifier.size(18.dp)) { Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp)) } })
                }
                if (s.active.isEmpty()) Text("No job types yet — add one below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = custom, onValueChange = { custom = it },
                    placeholder = { Text("Add a custom job type…") },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.addType(custom); custom = "" }, enabled = custom.isNotBlank() && !s.saving,
                    shape = RoundedCornerShape(10.dp)) { Text("Add") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
