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
import com.ultimatepro.domain.model.CustomField
import com.ultimatepro.ui.common.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class CustomFieldsState(
    val fields: List<CustomField> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val saving: Boolean = false,
    val saveError: String? = null
)

@HiltViewModel
class CustomFieldsViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {

    private val _state = MutableStateFlow(CustomFieldsState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = repo.getCustomFields()) {
                is Result.Success -> _state.update { it.copy(fields = parseFields(r.data), loading = false) }
                is Result.Error   -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun create(label: String, fieldType: String, entity: String, options: List<String>, required: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, saveError = null) }
            val fieldKey = label.trim().lowercase().replace(Regex("\\s+"), "_").replace(Regex("[^a-z0-9_]"), "")
            val body: Map<String, Any?> = buildMap {
                put("label", label.trim())
                put("field_key", fieldKey)
                put("field_type", fieldType)
                put("entity", entity)
                put("required", required)
                if (fieldType == "dropdown" && options.isNotEmpty()) put("options", options)
            }
            when (val r = repo.createCustomField(body)) {
                is Result.Success -> {
                    _state.update { it.copy(saving = false) }
                    load()
                }
                is Result.Error -> _state.update { it.copy(saving = false, saveError = r.message) }
            }
        }
    }

    fun update(id: String, label: String, fieldType: String, entity: String, options: List<String>, required: Boolean, active: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, saveError = null) }
            val body: Map<String, Any?> = buildMap {
                put("label", label.trim())
                put("field_type", fieldType)
                put("entity", entity)
                put("required", required)
                put("active", active)
                if (fieldType == "dropdown") put("options", options) else put("options", emptyList<String>())
            }
            when (val r = repo.updateCustomField(id, body)) {
                is Result.Success -> {
                    _state.update { it.copy(saving = false) }
                    load()
                }
                is Result.Error -> _state.update { it.copy(saving = false, saveError = r.message) }
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repo.deleteCustomField(id)
            _state.update { s -> s.copy(fields = s.fields.filter { it.id != id }) }
        }
    }

    fun clearSaveError() { _state.update { it.copy(saveError = null) } }

    @Suppress("UNCHECKED_CAST")
    private fun parseFields(raw: List<Map<String, Any>>) = raw.map { m ->
        CustomField(
            id         = m["id"] as? String ?: "",
            company_id = m["company_id"] as? String ?: "",
            label      = m["label"] as? String ?: "",
            field_key  = m["field_key"] as? String ?: "",
            field_type = m["field_type"] as? String ?: "text",
            entity     = m["entity"] as? String ?: "job",
            options    = (m["options"] as? List<*>)?.filterIsInstance<String>(),
            required   = m["required"] as? Boolean ?: false,
            sort_order = (m["sort_order"] as? Number)?.toInt() ?: 0,
            active     = m["active"] as? Boolean ?: true,
            created_at = m["created_at"] as? String ?: ""
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private val ENTITY_LABELS = mapOf("job" to "Job", "customer" to "Customer", "estimate" to "Estimate")
private val FIELD_TYPE_LABELS = mapOf(
    "text" to "Text", "number" to "Number", "dropdown" to "Dropdown",
    "date" to "Date", "checkbox" to "Checkbox"
)
private val ENTITIES = listOf("job", "customer", "estimate")
private val FIELD_TYPES = listOf("text", "number", "dropdown", "date", "checkbox")

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFieldsScreen(
    onBack: () -> Unit,
    vm: CustomFieldsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<CustomField?>(null) }
    var deleteTarget by remember { mutableStateOf<CustomField?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom Fields", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { editingField = null; showDialog = true }) {
                        Icon(Icons.Default.Add, "Add Field")
                    }
                }
            )
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
                    Button(onClick = { vm.load() }) { Text("Retry") }
                }
            }
            else -> {
                val grouped = state.fields.groupBy { it.entity }
                if (state.fields.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Label, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Spacer(Modifier.height(12.dp))
                            Text("No custom fields", style = MaterialTheme.typography.titleMedium)
                            Text("Tap + to add one", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 16.dp
                        )
                    ) {
                        ENTITIES.forEach { entity ->
                            val entityFields = grouped[entity] ?: return@forEach
                            item {
                                Text(
                                    (ENTITY_LABELS[entity] ?: entity).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.Blue,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(entityFields, key = { it.id }) { field ->
                                FieldItem(
                                    field = field,
                                    onEdit = { editingField = it; showDialog = true },
                                    onDelete = { deleteTarget = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        FieldDialog(
            field = editingField,
            saving = state.saving,
            error = state.saveError,
            onDismiss = { showDialog = false; vm.clearSaveError() },
            onSave = { label, fieldType, entity, options, required, active ->
                val f = editingField
                if (f == null) {
                    vm.create(label, fieldType, entity, options, required)
                } else {
                    vm.update(f.id, label, fieldType, entity, options, required, active)
                }
                showDialog = false
            }
        )
    }

    deleteTarget?.let { field ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Field?") },
            text = { Text("\"${field.label}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.delete(field.id); deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FieldItem(field: CustomField, onEdit: (CustomField) -> Unit, onDelete: (CustomField) -> Unit) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(field.label, fontWeight = FontWeight.Medium)
                if (field.required) {
                    Text("*", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
                if (!field.active) {
                    Text("inactive", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        supportingContent = {
            val typeLabel = FIELD_TYPE_LABELS[field.field_type] ?: field.field_type
            val optionsSuffix = if (field.field_type == "dropdown" && !field.options.isNullOrEmpty()) {
                " · ${field.options.size} options"
            } else ""
            Text("$typeLabel$optionsSuffix · key: ${field.field_key}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Row {
                IconButton(onClick = { onEdit(field) }) {
                    Icon(Icons.Default.Edit, "Edit", Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDelete(field) }) {
                    Icon(Icons.Default.Delete, "Delete", Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldDialog(
    field: CustomField?,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (label: String, fieldType: String, entity: String, options: List<String>, required: Boolean, active: Boolean) -> Unit
) {
    var label     by remember(field) { mutableStateOf(field?.label ?: "") }
    var fieldType by remember(field) { mutableStateOf(field?.field_type ?: "text") }
    var entity    by remember(field) { mutableStateOf(field?.entity ?: "job") }
    var required  by remember(field) { mutableStateOf(field?.required ?: false) }
    var active    by remember(field) { mutableStateOf(field?.active ?: true) }
    var optionsText by remember(field) { mutableStateOf(field?.options?.joinToString("\n") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (field == null) "Add Custom Field" else "Edit Field") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Entity selector
                Text("Entity", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ENTITIES.forEach { e ->
                        FilterChip(
                            selected = entity == e,
                            onClick = { entity = e },
                            label = { Text(ENTITY_LABELS[e] ?: e) }
                        )
                    }
                }
                // Field type selector
                Text("Type", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FIELD_TYPES.forEach { t ->
                        FilterChip(
                            selected = fieldType == t,
                            onClick = { fieldType = t },
                            label = { Text(FIELD_TYPE_LABELS[t] ?: t) }
                        )
                    }
                }
                // Options for dropdown
                if (fieldType == "dropdown") {
                    OutlinedTextField(
                        value = optionsText,
                        onValueChange = { optionsText = it },
                        label = { Text("Options (one per line)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Required")
                    Switch(checked = required, onCheckedChange = { required = it })
                }
                if (field != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Active")
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val opts = optionsText.lines().map { it.trim() }.filter { it.isNotBlank() }
                    onSave(label, fieldType, entity, opts, required, active)
                },
                enabled = label.isNotBlank() && !saving
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
