@file:OptIn(ExperimentalMaterial3Api::class)

package com.ultimatepro.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.User
import com.ultimatepro.ui.common.AppColors
import com.ultimatepro.ui.common.ShineHairline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── State ────────────────────────────────────────────────────────────────────

data class TeamMembersState(
    val loading:    Boolean       = true,
    val saving:     Boolean       = false,
    val users:      List<User>    = emptyList(),
    val permSchema: PermSchema?   = null,
    val snack:      String?       = null,
    val snackError: Boolean       = false,
)

// Permission model fetched from GET /users/permission-schema (single source of truth).
data class PermSchema(
    val sections: List<String> = emptyList(),
    val levels: List<String> = emptyList(),
    val roleTemplates: Map<String, Map<String, String>> = emptyMap(),
)

data class TeamMemberForm(
    val firstName: String = "",
    val lastName:  String = "",
    val email:     String = "",
    val phone:     String = "",
    val role:      String = "technician",
    val password:  String = "",
    // Full per-section grid shown in the form; save sends only the delta vs template.
    val permissions: Map<String, String> = emptyMap(),
)

// Display labels for the permission grid (model itself comes from the backend schema).
private val SECTION_LABELS = mapOf(
    "jobs" to "Jobs", "customers" to "Customers", "estimates_invoices" to "Estimates & Invoices",
    "payments_refunds" to "Payments & Refunds", "pricebook" to "Pricebook",
    "accounting_earnings" to "Accounting & Earnings", "reports" to "Reports",
    "job_sources_commissions" to "Job Sources & Commissions", "team_settings" to "Team & Settings",
)
private val LEVEL_LABELS = mapOf(
    "none" to "None", "view" to "View", "edit_self" to "Edit (self)", "full" to "Full",
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class TeamMembersViewModel @Inject constructor(
    private val repo: CrmRepository
) : ViewModel() {

    private val _s = MutableStateFlow(TeamMembersState())
    val state = _s.asStateFlow()

    init { load(); loadPermSchema() }

    fun load() {
        viewModelScope.launch {
            _s.update { it.copy(loading = true) }
            when (val r = repo.getUsers()) {
                is Result.Success -> _s.update { it.copy(loading = false, users = r.data) }
                is Result.Error   -> _s.update { it.copy(loading = false, snack = "Failed to load", snackError = true) }
            }
        }
    }

    fun loadPermSchema() {
        viewModelScope.launch {
            when (val r = repo.getPermissionSchema()) {
                is Result.Success -> {
                    val d = r.data
                    val sections = (d["sections"] as? List<*>)?.map { it.toString() } ?: emptyList()
                    val levels   = (d["levels"]   as? List<*>)?.map { it.toString() } ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val tpl = (d["role_templates"] as? Map<String, Map<String, String>>) ?: emptyMap()
                    _s.update { it.copy(permSchema = PermSchema(sections, levels, tpl)) }
                }
                is Result.Error -> { /* schema optional; the grid simply won't render */ }
            }
        }
    }

    fun templateFor(role: String): Map<String, String> =
        _s.value.permSchema?.roleTemplates?.get(role) ?: emptyMap()

    // Delta vs the role template — only sections that differ are stored as overrides.
    private fun deltaPermissions(role: String, grid: Map<String, String>): Map<String, String> {
        val tpl = templateFor(role)
        return grid.filterKeys { tpl.containsKey(it) }.filter { (k, v) -> v != tpl[k] }
    }

    fun create(form: TeamMemberForm) {
        viewModelScope.launch {
            _s.update { it.copy(saving = true) }
            val data = mapOf<String, Any?>(
                "first_name" to form.firstName.trim(),
                "last_name"  to form.lastName.trim(),
                "email"      to form.email.trim(),
                "phone"      to form.phone.trim().ifEmpty { null },
                "role"       to form.role,
                "password"   to form.password,
                "permissions" to deltaPermissions(form.role, form.permissions).ifEmpty { null },
            )
            when (val r = repo.createUser(data)) {
                is Result.Success -> {
                    _s.update { it.copy(saving = false, users = it.users + r.data, snack = "Team member added", snackError = false) }
                }
                is Result.Error -> _s.update { it.copy(saving = false, snack = r.message, snackError = true) }
            }
        }
    }

    fun update(id: String, form: TeamMemberForm) {
        viewModelScope.launch {
            _s.update { it.copy(saving = true) }
            val data = buildMap<String, Any?> {
                put("first_name", form.firstName.trim())
                put("last_name",  form.lastName.trim())
                put("email",      form.email.trim())
                put("phone",      form.phone.trim().ifEmpty { null })
                put("role",       form.role)
                put("permissions", deltaPermissions(form.role, form.permissions).ifEmpty { null })
                if (form.password.isNotEmpty()) put("password", form.password)
            }
            when (val r = repo.updateUser(id, data)) {
                is Result.Success -> {
                    _s.update { it.copy(
                        saving = false,
                        users  = it.users.map { u -> if (u.id == id) r.data else u },
                        snack  = "User updated",
                        snackError = false
                    )}
                }
                is Result.Error -> _s.update { it.copy(saving = false, snack = r.message, snackError = true) }
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            when (val r = repo.deleteUser(id)) {
                is Result.Success -> {
                    _s.update { it.copy(users = it.users.filter { u -> u.id != id }, snack = "User removed", snackError = false) }
                }
                is Result.Error -> _s.update { it.copy(snack = r.message, snackError = true) }
            }
        }
    }

    fun reactivate(id: String) {
        viewModelScope.launch {
            when (val r = repo.reactivateUser(id)) {
                is Result.Success -> {
                    _s.update { it.copy(
                        users = it.users.map { u -> if (u.id == id) r.data else u },
                        snack = "User reactivated",
                        snackError = false
                    )}
                }
                is Result.Error -> _s.update { it.copy(snack = r.message, snackError = true) }
            }
        }
    }

    fun clearSnack() = _s.update { it.copy(snack = null) }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamMembersScreen(
    onBack: () -> Unit,
    vm: TeamMembersViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsState()
    var showAdd    by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<User?>(null) }
    var deleteTarget by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(s.snack) {
        if (s.snack != null) {
            kotlinx.coroutines.delay(3000)
            vm.clearSnack()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Team Members", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAdd = true }) {
                            Icon(Icons.Default.PersonAdd, "Add member")
                        }
                    }
                )
                ShineHairline()
            }
        },
        snackbarHost = {
            s.snack?.let { msg ->
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (s.snackError) MaterialTheme.colorScheme.error else AppColors.Green
                        )
                    ) {
                        Text(msg, color = Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    ) { padding ->
        if (s.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (s.users.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No team members", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap + to add a team member", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(s.users, key = { it.id }) { user ->
                UserCard(
                    user        = user,
                    onEdit      = { editTarget = user },
                    onDelete    = { deleteTarget = user },
                    onReactivate = { vm.reactivate(user.id) }
                )
            }
        }
    }

    // Add modal
    if (showAdd) {
        TeamMemberFormDialog(
            title    = "Add Team Member",
            initial  = TeamMemberForm(),
            saving   = s.saving,
            requirePassword = true,
            schema   = s.permSchema,
            initialOverrides = emptyMap(),
            onDismiss = { showAdd = false },
            onSave   = { form ->
                vm.create(form)
                showAdd = false
            }
        )
    }

    // Edit modal
    editTarget?.let { user ->
        TeamMemberFormDialog(
            title   = "Edit ${user.first_name}",
            initial = TeamMemberForm(
                firstName = user.first_name,
                lastName  = user.last_name,
                email     = user.email,
                phone     = user.phone ?: "",
                role      = user.role,
            ),
            saving  = s.saving,
            requirePassword = false,
            isOwner = user.role == "owner",
            schema  = s.permSchema,
            initialOverrides = user.permissions ?: emptyMap(),
            onDismiss = { editTarget = null },
            onSave   = { form ->
                vm.update(user.id, form)
                editTarget = null
            }
        )
    }

    // Delete confirm
    deleteTarget?.let { user ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove Team Member?") },
            text  = { Text("${user.first_name} ${user.last_name} will be deactivated and lose app access.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.delete(user.id); deleteTarget = null },
                    colors  = ButtonDefaults.textButtonColors(contentColor = AppColors.Red)
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

// ─── User Card ────────────────────────────────────────────────────────────────

@Composable
private fun UserCard(
    user: User,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReactivate: () -> Unit,
) {
    val roleColor = when (user.role) {
        "owner"      -> AppColors.Purple
        "admin"      -> AppColors.Blue
        "manager"    -> AppColors.Green
        "technician" -> AppColors.Orange
        "dispatcher" -> Color(0xFF4338CA)
        else         -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circle
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = roleColor.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxSize()
                ) {}
                Text(
                    (user.first_name.firstOrNull() ?: '?').uppercaseChar().toString(),
                    fontWeight = FontWeight.Bold,
                    color = roleColor
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${user.first_name} ${user.last_name}", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    if (!user.is_active) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEE2E2)
                        ) {
                            Text("Inactive", style = MaterialTheme.typography.labelSmall,
                                color = AppColors.Red,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(user.email, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                user.phone?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = roleColor.copy(alpha = 0.12f)) {
                    Text(user.role.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = roleColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }

            if (user.role != "owner") {
                if (!user.is_active) {
                    IconButton(onClick = onReactivate) {
                        Icon(Icons.Default.Refresh, "Reactivate", tint = AppColors.Blue)
                    }
                } else {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Remove", tint = AppColors.Red)
                    }
                }
            }
        }
    }
}

// ─── Form Dialog ──────────────────────────────────────────────────────────────

@Composable
private fun TeamMemberFormDialog(
    title: String,
    initial: TeamMemberForm,
    saving: Boolean,
    requirePassword: Boolean,
    isOwner: Boolean = false,
    schema: PermSchema? = null,
    initialOverrides: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit,
    onSave: (TeamMemberForm) -> Unit,
) {
    var form by remember { mutableStateOf(initial) }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var roleNote by remember { mutableStateOf("") }

    val roles = listOf("technician", "dispatcher", "manager", "admin")

    // Seed the full grid (template + initial overrides) once the schema is available.
    LaunchedEffect(schema) {
        if (schema != null && !isOwner && form.permissions.isEmpty()) {
            val tpl = schema.roleTemplates[form.role] ?: emptyMap()
            form = form.copy(permissions = schema.sections.associateWith { initialOverrides[it] ?: tpl[it] ?: "none" })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.firstName,
                        onValueChange = { form = form.copy(firstName = it) },
                        label = { Text("First Name") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = form.lastName,
                        onValueChange = { form = form.copy(lastName = it) },
                        label = { Text("Last Name") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = form.email,
                    onValueChange = { form = form.copy(email = it) },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = form.phone,
                    onValueChange = { form = form.copy(phone = it) },
                    label = { Text("Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                if (!isOwner) {
                    ExposedDropdownMenuBox(
                        expanded = false,
                        onExpandedChange = {}
                    ) {
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = form.role.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Role") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            roles.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(r.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        // Re-seed the grid to the new role template + clear overrides.
                                        val tpl = schema?.roleTemplates?.get(r) ?: emptyMap()
                                        val sections = schema?.sections ?: tpl.keys.toList()
                                        form = form.copy(role = r, permissions = sections.associateWith { tpl[it] ?: "none" })
                                        roleNote = "Reset to $r defaults"
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Permission grid (Phase 1: stored + editable; nothing enforces it yet).
                if (isOwner) {
                    Surface(color = AppColors.Blue.copy(alpha = 0.10f), shape = RoundedCornerShape(10.dp)) {
                        Text("Owner has full access to everything.",
                            modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                } else if (schema != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Permissions", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        if (roleNote.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(roleNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    schema.sections.forEach { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(SECTION_LABELS[section] ?: section, style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                schema.levels.forEach { level ->
                                    FilterChip(
                                        selected = form.permissions[section] == level,
                                        onClick = { form = form.copy(permissions = form.permissions + (section to level)) },
                                        label = { Text(LEVEL_LABELS[level] ?: level, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = form.password,
                    onValueChange = { form = form.copy(password = it) },
                    label = { Text(if (requirePassword) "Password *" else "New Password (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                if (error.isNotEmpty()) {
                    Text(error, color = AppColors.Red, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        form.firstName.isBlank() -> error = "First name is required"
                        form.lastName.isBlank()  -> error = "Last name is required"
                        form.email.isBlank()     -> error = "Email is required"
                        requirePassword && form.password.length < 8 -> error = "Password must be at least 8 characters"
                        else -> { error = ""; onSave(form) }
                    }
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Blue)
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (requirePassword) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
