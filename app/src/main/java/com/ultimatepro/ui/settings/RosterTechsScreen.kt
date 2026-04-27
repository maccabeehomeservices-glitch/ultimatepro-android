package com.ultimatepro.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.RosterTech
import com.ultimatepro.ui.common.AppColors
import com.ultimatepro.ui.common.QtyStepperRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ──────────────────────────────────────────────────────────────
data class RosterTechsState(
    val techs:   List<RosterTech> = emptyList(),
    val loading: Boolean           = true,
    val error:   String?           = null,
    val saving:  Boolean           = false
)

@HiltViewModel
class RosterTechsViewModel @Inject constructor(
    private val repo: CrmRepository
) : ViewModel() {
    private val _s = MutableStateFlow(RosterTechsState())
    val state = _s.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _s.update { it.copy(loading = true, error = null) }
            when (val r = repo.getRosterTechs()) {
                is Result.Success -> _s.update { it.copy(techs = r.data, loading = false) }
                is Result.Error   -> _s.update { it.copy(error = r.message, loading = false) }
            }
        }
    }

    fun save(id: String?, name: String, phone: String, email: String, commissionPct: Double, ccFeePct: Double, onDone: () -> Unit) {
        viewModelScope.launch {
            _s.update { it.copy(saving = true) }
            val data = buildMap<String, Any?> {
                put("name",           name)
                put("phone",          phone.ifBlank { null })
                put("email",          email.ifBlank { null })
                put("commission_pct", commissionPct)
                put("cc_fee_pct",     ccFeePct)
            }
            val r = if (id == null) repo.createRosterTech(data)
                    else           repo.updateRosterTech(id, data)
            _s.update { it.copy(saving = false) }
            if (r is Result.Success) { load(); onDone() }
            else if (r is Result.Error) _s.update { it.copy(error = r.message) }
        }
    }

    fun delete(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val r = repo.deleteRosterTech(id)
            if (r is Result.Success) { load(); onDone() }
            else if (r is Result.Error) _s.update { it.copy(error = r.message) }
        }
    }

    fun clearError() = _s.update { it.copy(error = null) }
}

// ── Screen ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RosterTechsScreen(
    onBack: () -> Unit,
    vm: RosterTechsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var showForm      by remember { mutableStateOf(false) }
    var editingTech   by remember { mutableStateOf<RosterTech?>(null) }
    var confirmDelete by remember { mutableStateOf<RosterTech?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Technicians", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingTech = null; showForm = true },
                containerColor = AppColors.Blue
            ) {
                Icon(Icons.Default.Add, null, tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.techs.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Engineering, null, modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No technicians yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap + to add a field technician", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.techs, key = { it.id }) { tech ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(tech.name, fontWeight = FontWeight.SemiBold)
                                if (!tech.phone.isNullOrBlank()) {
                                    Text(tech.phone, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!tech.email.isNullOrBlank()) {
                                    Text(tech.email, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (tech.commission_pct > 0 || tech.cc_fee_pct > 0) {
                                    Text(
                                        buildString {
                                            if (tech.commission_pct > 0) append("${tech.commission_pct.toInt()}% commission")
                                            if (tech.commission_pct > 0 && tech.cc_fee_pct > 0) append(" · ")
                                            if (tech.cc_fee_pct > 0) append("${tech.cc_fee_pct.toInt()}% CC fee")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppColors.Blue
                                    )
                                }
                            }
                            Row {
                                IconButton(onClick = { editingTech = tech; showForm = true }) {
                                    Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { confirmDelete = tech }) {
                                    Icon(Icons.Default.Delete, null, tint = AppColors.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            title = { Text("Error") },
            text  = { Text(err) },
            confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("OK") } }
        )
    }

    if (showForm) {
        TechFormDialog(
            tech    = editingTech,
            saving  = state.saving,
            onSave  = { name, phone, email, comm, cc ->
                vm.save(editingTech?.id, name, phone, email, comm, cc) { showForm = false }
            },
            onDismiss = { showForm = false }
        )
    }

    confirmDelete?.let { tech ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title   = { Text("Delete Technician?") },
            text    = { Text("${tech.name} will be removed and unassigned from any open jobs.") },
            confirmButton   = {
                TextButton(
                    onClick = { vm.delete(tech.id) { confirmDelete = null } },
                    colors  = ButtonDefaults.textButtonColors(contentColor = AppColors.Red)
                ) { Text("Delete") }
            },
            dismissButton   = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

// ── Form Dialog ────────────────────────────────────────────────────────────
@Composable
private fun TechFormDialog(
    tech:      RosterTech?,
    saving:    Boolean,
    onSave:    (name: String, phone: String, email: String, commissionPct: Double, ccFeePct: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name          by remember(tech) { mutableStateOf(tech?.name ?: "") }
    var phone         by remember(tech) { mutableStateOf(tech?.phone ?: "") }
    var email         by remember(tech) { mutableStateOf(tech?.email ?: "") }
    var commissionPct by remember(tech) { mutableIntStateOf(tech?.commission_pct?.toInt() ?: 0) }
    var ccFeePct      by remember(tech) { mutableIntStateOf(tech?.cc_fee_pct?.toInt() ?: 0) }
    var nameError     by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tech == null) "Add Technician" else "Edit Technician") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value          = name,
                    onValueChange  = { name = it; nameError = false },
                    label          = { Text("Name *") },
                    isError        = nameError,
                    supportingText = if (nameError) {{ Text("Name is required") }} else null,
                    modifier       = Modifier.fillMaxWidth(),
                    shape          = RoundedCornerShape(10.dp),
                    singleLine     = true
                )
                OutlinedTextField(
                    value         = phone,
                    onValueChange = { phone = it },
                    label         = { Text("Phone") },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(
                    value         = email,
                    onValueChange = { email = it },
                    label         = { Text("Email") },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Commission %", Modifier.weight(1f))
                    QtyStepperRow(
                        qty         = commissionPct,
                        onDecrement = { if (commissionPct > 0) commissionPct-- },
                        onIncrement = { if (commissionPct < 100) commissionPct++ },
                        minQty      = 0,
                        maxQty      = 100
                    )
                    Text("$commissionPct%", Modifier.width(40.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("CC Fee %", Modifier.weight(1f))
                    QtyStepperRow(
                        qty         = ccFeePct,
                        onDecrement = { if (ccFeePct > 0) ccFeePct-- },
                        onIncrement = { if (ccFeePct < 10) ccFeePct++ },
                        minQty      = 0,
                        maxQty      = 10
                    )
                    Text("$ccFeePct%", Modifier.width(40.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = {
                    if (name.isBlank()) { nameError = true; return@TextButton }
                    onSave(name.trim(), phone.trim(), email.trim(), commissionPct.toDouble(), ccFeePct.toDouble())
                },
                enabled  = !saving
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(if (tech == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}
