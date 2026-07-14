package com.ultimatepro.ui.memberships

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultimatepro.domain.model.Customer
import com.ultimatepro.domain.model.MembershipPlan
import com.ultimatepro.ui.common.AppColors
import com.ultimatepro.ui.common.AppSwitch
import com.ultimatepro.ui.common.CRMCard
import com.ultimatepro.ui.common.ShineHairline
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private fun formatMembershipDate(isoDate: String): String = try {
    val p = isoDate.split("-")
    val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    "${months[p[1].toInt() - 1]} ${p[2].toInt()}, ${p[0]}"
} catch (e: Exception) { isoDate }

private fun millisToIso(millis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}

private val FREQUENCIES = listOf("weekly", "monthly", "quarterly", "semi_annually", "annually")
private fun frequencyLabel(f: String) = when (f) {
    "weekly"        -> "Weekly"
    "monthly"       -> "Monthly"
    "quarterly"     -> "Quarterly"
    "semi_annually" -> "Semi-annually"
    "annually"      -> "Annually"
    else            -> f.replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembershipPlansScreen(
    onBack: () -> Unit,
    vm: MembershipViewModel = hiltViewModel()
) {
    val plans by vm.plans.collectAsState()
    val loading by vm.plansLoading.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MembershipPlan?>(null) }
    var assigningPlan by remember { mutableStateOf<MembershipPlan?>(null) }
    var assignSuccess by remember { mutableStateOf(false) }
    var assignSuccessMessage by remember { mutableStateOf("") }
    var assignError by remember { mutableStateOf<String?>(null) }
    val customerSearch by vm.customerSearch.collectAsState()

    LaunchedEffect(Unit) { vm.loadPlans() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Membership Plans") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                    },
                    actions = {
                        IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Add Plan") }
                    }
                )
                ShineHairline()
            }
        }
    ) { padding ->
        if (loading && plans.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                if (plans.isEmpty()) {
                    item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Autorenew, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(48.dp))
                                Text("No membership plans", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { showAdd = true }) { Text("Add your first plan") }
                            }
                        }
                    }
                }
                items(plans, key = { it.id }) { plan ->
                    PlanCard(plan, onEdit = { editing = plan }, onDelete = { vm.deletePlan(plan.id) }, onAssign = { assigningPlan = plan })
                }
            }
        }
    }

    if (showAdd) {
        AddEditPlanDialog(
            plan = null,
            onDismiss = { showAdd = false },
            onSave = { name, desc, freq, price ->
                vm.createPlan(name, desc, freq, price) { showAdd = false }
            }
        )
    }
    editing?.let { plan ->
        AddEditPlanDialog(
            plan = plan,
            onDismiss = { editing = null },
            onSave = { name, desc, freq, price ->
                vm.updatePlan(plan.id, name, desc, freq, price, plan.isActive) { editing = null }
            }
        )
    }
    assigningPlan?.let { plan ->
        AssignMembershipDialog(
            plan = plan,
            customers = customerSearch,
            onSearch = { vm.searchCustomers(it) },
            onDismiss = { assigningPlan = null; vm.searchCustomers("") },
            onAssign = { customerId, startDate, endDate, renewalDate, notes ->
                vm.addMembership(customerId, plan.id, plan.name, startDate, endDate, renewalDate, notes) { ok, jobCount ->
                    if (ok) {
                        assigningPlan = null
                        assignSuccessMessage = "Membership assigned and $jobCount job${if (jobCount == 1) "" else "s"} created"
                        assignSuccess = true
                        vm.searchCustomers("")
                    } else {
                        assignError = "Failed to assign membership. Please check your connection and try again."
                    }
                }
            }
        )
    }
    if (assignSuccess) {
        AlertDialog(
            onDismissRequest = { assignSuccess = false },
            title = { Text("Membership Assigned") },
            text = { Text(assignSuccessMessage.ifBlank { "The membership plan has been assigned to the customer." }) },
            confirmButton = { TextButton(onClick = { assignSuccess = false }) { Text("OK") } }
        )
    }
    assignError?.let { msg ->
        AlertDialog(
            onDismissRequest = { assignError = null },
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { assignError = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun PlanCard(
    plan: MembershipPlan,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAssign: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    CRMCard {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Autorenew,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(plan.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    "${frequencyLabel(plan.frequency)} · $${String.format(Locale.US, "%.2f", plan.price)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                plan.description?.let {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!plan.isActive) {
                    Text("Inactive", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onAssign) { Icon(Icons.Default.PersonAdd, null, tint = AppColors.Blue) }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null) }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Plan") },
            text = { Text("Delete \"${plan.name}\"? Existing customer memberships using this plan will not be removed.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignMembershipDialog(
    plan: MembershipPlan,
    customers: List<Customer>,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onAssign: (customerId: String, startDate: String?, endDate: String?, renewalDate: String?, notes: String?) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Customer?>(null) }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var renewalDate by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showRenewalPicker by remember { mutableStateOf(false) }
    val startPickerState = rememberDatePickerState()
    val endPickerState = rememberDatePickerState()
    val renewalPickerState = rememberDatePickerState()

    LaunchedEffect(search) { onSearch(search) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (selected == null) "Assign: ${plan.name}" else selected!!.let { "${it.first_name} ${it.last_name ?: ""}".trim() }) },
        text = {
            if (selected == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search by name or phone…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.heightIn(max = 260.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (customers.isEmpty()) {
                                item {
                                    Text(
                                        if (search.isBlank()) "Type to search customers" else "No customers found",
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(customers, key = { it.id }) { c ->
                                    ListItem(
                                        headlineContent = { Text("${c.first_name} ${c.last_name ?: ""}".trim(), fontWeight = FontWeight.Medium) },
                                        supportingContent = c.phone?.let { p -> { Text(p, fontSize = 12.sp) } },
                                        modifier = Modifier.clickable { selected = c }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    selected!!.phone?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    // Start Date
                    OutlinedTextField(
                        value = if (startDate.isNotBlank()) formatMembershipDate(startDate) else "",
                        onValueChange = {},
                        label = { Text("Start Date") },
                        placeholder = { Text("Tap to select") },
                        readOnly = true, singleLine = true,
                        trailingIcon = { Icon(Icons.Default.CalendarMonth, null) },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().clickable { showStartPicker = true }
                    )
                    // End Date
                    OutlinedTextField(
                        value = if (endDate.isNotBlank()) formatMembershipDate(endDate) else "",
                        onValueChange = {},
                        label = { Text("End Date (optional)") },
                        placeholder = { Text("Tap to select") },
                        readOnly = true, singleLine = true,
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (endDate.isNotBlank()) IconButton(onClick = { endDate = "" }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp)) }
                                Icon(Icons.Default.CalendarMonth, null)
                            }
                        },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().clickable { showEndPicker = true }
                    )
                    // Renewal Date
                    OutlinedTextField(
                        value = if (renewalDate.isNotBlank()) formatMembershipDate(renewalDate) else "",
                        onValueChange = {},
                        label = { Text("Renewal Date (optional)") },
                        placeholder = { Text("Tap to select") },
                        readOnly = true, singleLine = true,
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (renewalDate.isNotBlank()) IconButton(onClick = { renewalDate = "" }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp)) }
                                Icon(Icons.Default.CalendarMonth, null)
                            }
                        },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().clickable { showRenewalPicker = true }
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (selected != null) {
                TextButton(onClick = {
                    onAssign(
                        selected!!.id,
                        startDate.ifBlank { null },
                        endDate.ifBlank { null },
                        renewalDate.ifBlank { null },
                        notes.trim().ifBlank { null }
                    )
                }) { Text("Assign") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (selected != null) selected = null else onDismiss() }) {
                Text(if (selected != null) "Back" else "Cancel")
            }
        }
    )

    if (showStartPicker) {
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startPickerState.selectedDateMillis?.let { startDate = millisToIso(it) }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = startPickerState) }
    }
    if (showEndPicker) {
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endPickerState.selectedDateMillis?.let { endDate = millisToIso(it) }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = endPickerState) }
    }
    if (showRenewalPicker) {
        DatePickerDialog(
            onDismissRequest = { showRenewalPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    renewalPickerState.selectedDateMillis?.let { renewalDate = millisToIso(it) }
                    showRenewalPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showRenewalPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = renewalPickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPlanDialog(
    plan: MembershipPlan?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?, frequency: String, price: Double) -> Unit
) {
    var name by remember { mutableStateOf(plan?.name ?: "") }
    var description by remember { mutableStateOf(plan?.description ?: "") }
    var frequency by remember { mutableStateOf(plan?.frequency ?: "monthly") }
    var priceText by remember { mutableStateOf(if (plan != null) String.format(Locale.US, "%.2f", plan.price) else "") }
    var freqExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (plan == null) "New Membership Plan" else "Edit Plan") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plan Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Frequency dropdown
                ExposedDropdownMenuBox(
                    expanded = freqExpanded,
                    onExpandedChange = { freqExpanded = it }
                ) {
                    OutlinedTextField(
                        value = frequencyLabel(frequency),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Frequency *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                        FREQUENCIES.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(frequencyLabel(f)) },
                                onClick = { frequency = f; freqExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price ($) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val price = priceText.toDoubleOrNull() ?: 0.0
                    if (name.isBlank()) return@TextButton
                    onSave(name.trim(), description.trim().ifBlank { null }, frequency, price)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
