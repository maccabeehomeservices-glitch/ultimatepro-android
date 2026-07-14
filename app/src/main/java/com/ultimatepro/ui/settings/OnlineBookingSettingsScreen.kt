package com.ultimatepro.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.BookingSettings
import com.ultimatepro.domain.model.ServiceArea
import com.ultimatepro.domain.model.TimeWindow
import com.ultimatepro.ui.common.AppButton
import com.ultimatepro.ui.common.AppColors
import com.ultimatepro.ui.common.AppSwitch
import com.ultimatepro.ui.common.CRMCard
import com.ultimatepro.ui.common.QtyStepperRow
import com.ultimatepro.ui.common.SectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ───────────────────────────────────────────────────────────────
@HiltViewModel
class OnlineBookingViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {
    private val _settings = MutableStateFlow<BookingSettings?>(null)
    val settings = _settings.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    private val _msg = MutableStateFlow<String?>(null)
    val message = _msg.asStateFlow()

    private val _ucmId = MutableStateFlow<String?>(null)
    val ucmId = _ucmId.asStateFlow()

    init { loadSettings(); loadUcmId() }

    fun loadSettings() {
        viewModelScope.launch {
            _loading.value = true
            when (val r = repo.getBookingSettings()) {
                is Result.Success -> _settings.value = r.data
                is Result.Error   -> _msg.value = r.message
            }
            _loading.value = false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadUcmId() {
        viewModelScope.launch {
            when (val r = repo.getCompanyRaw()) {
                is Result.Success -> _ucmId.value = r.data["ultimatecrm_id"] as? String
                else -> {}
            }
        }
    }

    fun saveSettings(
        enabled: Boolean,
        companyDisplayName: String,
        companyTagline: String,
        workingDays: List<String>,
        timeWindows: List<TimeWindow>,
        maxBookingsPerWindow: Int,
        serviceAreas: List<ServiceArea>,
        services: List<String>,
        confirmationMessage: String,
        reminderEnabled: Boolean,
        reminderHoursBefore: Int,
        reminderMethod: String,
        followupEnabled: Boolean,
        followupDaysAfter: Int,
        followupRepeatEvery: Int,
        followupMaxReminders: Int,
        followupMethod: String
    ) {
        viewModelScope.launch {
            _saving.value = true
            val twMaps = timeWindows.map {
                mapOf("id" to it.id, "label" to it.label, "time" to it.time, "enabled" to it.enabled)
            }
            when (val r = repo.updateBookingSettings(
                enabled              = enabled,
                companyDisplayName   = companyDisplayName.ifBlank { null },
                companyTagline       = companyTagline.ifBlank { null },
                workingDays          = workingDays,
                timeWindows          = twMaps,
                maxBookingsPerWindow = maxBookingsPerWindow,
                serviceAreas         = serviceAreas,
                services             = services,
                confirmationMessage  = confirmationMessage,
                reminderEnabled      = reminderEnabled,
                reminderHoursBefore  = reminderHoursBefore,
                reminderMethod       = reminderMethod,
                followupEnabled      = followupEnabled,
                followupDaysAfter    = followupDaysAfter,
                followupRepeatEvery  = followupRepeatEvery,
                followupMaxReminders = followupMaxReminders,
                followupMethod       = followupMethod
            )) {
                is Result.Success -> { _settings.value = r.data; _msg.value = "Settings saved!" }
                is Result.Error   -> _msg.value = r.message
            }
            _saving.value = false
        }
    }

    fun clearMsg() { _msg.value = null }
}

// ─── Screen ──────────────────────────────────────────────────────────────────
private val allDays = listOf("monday","tuesday","wednesday","thursday","friday","saturday","sunday")
private val defaultWindows = listOf(
    TimeWindow("morning",   "Morning",   "8:00 AM - 12:00 PM", true),
    TimeWindow("afternoon", "Afternoon", "12:00 PM - 5:00 PM", true),
    TimeWindow("evening",   "Evening",   "5:00 PM - 8:00 PM",  false)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineBookingSettingsScreen(
    onBack: () -> Unit,
    vm: OnlineBookingViewModel = hiltViewModel()
) {
    val settings by vm.settings.collectAsState()
    val loading  by vm.loading.collectAsState()
    val saving   by vm.saving.collectAsState()
    val msg      by vm.message.collectAsState()
    val ucmId    by vm.ucmId.collectAsState()
    val snack    = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.clearMsg() } }

    // Local form state
    var isInitialized       by remember { mutableStateOf(false) }
    var enabled             by remember { mutableStateOf(false) }
    var displayName         by remember { mutableStateOf("") }
    var tagline             by remember { mutableStateOf("") }
    var workingDays         by remember { mutableStateOf(listOf("monday","tuesday","wednesday","thursday","friday")) }
    var timeWindows         by remember { mutableStateOf(defaultWindows) }
    var maxPerWindow        by remember { mutableStateOf(3) }
    var serviceAreas        by remember { mutableStateOf(listOf<ServiceArea>()) }
    var services            by remember { mutableStateOf(listOf<String>()) }
    var confirmationMessage by remember { mutableStateOf("Thank you! We will confirm your appointment shortly.") }
    var newServiceText      by remember { mutableStateOf("") }
    var reminderEnabled     by remember { mutableStateOf(true) }
    var reminderHours       by remember { mutableIntStateOf(24) }
    var reminderMethod      by remember { mutableStateOf("email") }
    var followupEnabled     by remember { mutableStateOf(false) }
    var followupDaysAfter   by remember { mutableIntStateOf(3) }
    var followupRepeatEvery by remember { mutableIntStateOf(7) }
    var followupMaxReminders by remember { mutableIntStateOf(3) }
    var followupMethod      by remember { mutableStateOf("email") }

    // Service area dialog state
    var showAddServiceAreaDialog  by remember { mutableStateOf(false) }
    var editingServiceArea        by remember { mutableStateOf<ServiceArea?>(null) }
    var editingServiceAreaIndex   by remember { mutableStateOf(-1) }

    LaunchedEffect(settings) {
        val s = settings
        if (s != null && !isInitialized) {
            enabled             = s.enabled
            displayName         = s.companyDisplayName ?: ""
            tagline             = s.companyTagline ?: ""
            workingDays         = s.workingDays.ifEmpty { listOf("monday","tuesday","wednesday","thursday","friday") }
            timeWindows         = s.timeWindows.ifEmpty { defaultWindows }
            maxPerWindow        = s.maxBookingsPerWindow
            serviceAreas        = s.serviceAreas
            services            = s.services
            confirmationMessage = s.confirmationMessage
            reminderEnabled      = s.reminderEnabled
            reminderHours        = s.reminderHoursBefore
            reminderMethod       = s.reminderMethod
            followupEnabled      = s.followupEnabled
            followupDaysAfter    = s.followupDaysAfter
            followupRepeatEvery  = s.followupRepeatEvery
            followupMaxReminders = s.followupMaxReminders
            followupMethod       = s.followupMethod
            isInitialized        = true
        }
    }

    val bookingUrl = ucmId?.let {
        "https://ultimatecrm-backend-production.up.railway.app/book?company=$it"
    } ?: "Loading your booking link..."

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Online Booking", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (loading && settings == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Master toggle ──────────────────────────────────────────────
            CRMCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable Online Booking", fontWeight = FontWeight.SemiBold)
                        Text("Customers can request appointments online",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AppSwitch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }

            // ── Booking link (shown when enabled and UCM ID is ready) ─────
            AnimatedVisibility(visible = enabled && ucmId != null) {
                CRMCard {
                    SectionLabel("YOUR BOOKING LINK")
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(bookingUrl, modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            onClick = { clipboard.setText(AnnotatedString(bookingUrl)) },
                            label = "Copy",
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Default.ContentCopy
                        )
                    }
                }
            }

            AnimatedVisibility(visible = enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // ── Appearance ─────────────────────────────────────────
                    SectionLabel("APPEARANCE")
                    CRMCard {
                        OutlinedTextField(
                            value = displayName, onValueChange = { displayName = it },
                            label = { Text("Display Name") },
                            placeholder = { Text("Shown on your booking page") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tagline, onValueChange = { tagline = it },
                            label = { Text("Tagline (optional)") },
                            placeholder = { Text("e.g. Fast, reliable service") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // ── Availability ───────────────────────────────────────
                    SectionLabel("AVAILABILITY")
                    CRMCard {
                        Text("Working Days", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        allDays.chunked(4).forEach { chunk ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                chunk.forEach { day ->
                                    val checked = day in workingDays
                                    FilterChip(
                                        selected = checked,
                                        onClick  = {
                                            workingDays = if (checked) workingDays - day else workingDays + day
                                        },
                                        label = {
                                            Text(day.take(3).replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelSmall)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(4 - chunk.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(4.dp))
                        }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("Time Windows", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        timeWindows.forEachIndexed { idx, window ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(window.label, fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodySmall)
                                    Text(window.time, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                AppSwitch(
                                    checked = window.enabled,
                                    onCheckedChange = { on ->
                                        timeWindows = timeWindows.toMutableList()
                                            .also { it[idx] = window.copy(enabled = on) }
                                    }
                                )
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Max bookings per window", Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = { if (maxPerWindow > 1) maxPerWindow-- },
                                    modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Remove, null, Modifier.size(16.dp))
                                }
                                Text("$maxPerWindow", fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { if (maxPerWindow < 10) maxPerWindow++ },
                                    modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // ── Service Area ───────────────────────────────────────
                    SectionLabel("SERVICE AREA")
                    CRMCard {
                        if (serviceAreas.isEmpty()) {
                            Text("No service areas added — all locations accepted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                        }
                        serviceAreas.forEachIndexed { idx, sa ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editingServiceArea      = sa
                                        editingServiceAreaIndex = idx
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, null,
                                    tint = AppColors.Blue,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        buildString {
                                            append("ZIP: ${sa.zipCode}  |  Radius: ${sa.radiusMiles} miles")
                                            if (!sa.label.isNullOrBlank()) append("  |  ${sa.label}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                IconButton(
                                    onClick = { serviceAreas = serviceAreas.toMutableList().also { it.removeAt(idx) } },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, null,
                                        Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (idx < serviceAreas.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        AppButton(
                            onClick = {
                                editingServiceArea      = null
                                editingServiceAreaIndex = -1
                                showAddServiceAreaDialog = true
                            },
                            label = "Add Service Area",
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = Icons.Default.Add
                        )
                    }

                    // ── Services Offered ───────────────────────────────────
                    SectionLabel("SERVICES OFFERED")
                    CRMCard {
                        if (services.isEmpty()) {
                            Text("No services added yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                        }
                        services.forEach { svc ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(svc, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                IconButton(onClick = { services = services - svc },
                                    modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, null, Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newServiceText, onValueChange = { newServiceText = it },
                                placeholder = { Text("Add a service type") },
                                singleLine = true, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            IconButton(onClick = {
                                if (newServiceText.isNotBlank()) {
                                    services = services + newServiceText.trim()
                                    newServiceText = ""
                                }
                            }) { Icon(Icons.Default.Add, null, tint = AppColors.Blue) }
                        }
                    }

                    // ── Confirmation ───────────────────────────────────────
                    SectionLabel("CONFIRMATION")
                    CRMCard {
                        OutlinedTextField(
                            value = confirmationMessage, onValueChange = { confirmationMessage = it },
                            label = { Text("Confirmation Message") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // ── Appointment Reminders ──────────────────────────────────────
            SectionLabel("APPOINTMENT REMINDERS")
            CRMCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Send Reminders", fontWeight = FontWeight.SemiBold)
                        Text("Notify customers before their appointment",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AppSwitch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                }
                AnimatedVisibility(visible = reminderEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp),
                           modifier = Modifier.padding(top = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Hours Before", Modifier.weight(1f))
                            QtyStepperRow(
                                qty         = reminderHours,
                                onDecrement = { if (reminderHours > 1) reminderHours-- },
                                onIncrement = { if (reminderHours < 72) reminderHours++ },
                                minQty      = 1,
                                maxQty      = 72
                            )
                            Text("${reminderHours}h", modifier = Modifier.width(40.dp))
                        }
                        Text("Reminder Method", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("email" to "Email", "sms" to "SMS", "both" to "Both").forEach { (value, label) ->
                                FilterChip(
                                    selected = reminderMethod == value,
                                    onClick  = { reminderMethod = value },
                                    label    = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // ── Follow-up Reminders ────────────────────────────────────────
            SectionLabel("FOLLOW-UP REMINDERS")
            CRMCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Send Follow-up Reminders", fontWeight = FontWeight.SemiBold)
                        Text("Automatically remind customers about unpaid invoices",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AppSwitch(checked = followupEnabled, onCheckedChange = { followupEnabled = it })
                }
                AnimatedVisibility(visible = followupEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp),
                           modifier = Modifier.padding(top = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("First reminder after", Modifier.weight(1f))
                            QtyStepperRow(
                                qty         = followupDaysAfter,
                                onDecrement = { if (followupDaysAfter > 1) followupDaysAfter-- },
                                onIncrement = { if (followupDaysAfter < 30) followupDaysAfter++ },
                                minQty      = 1,
                                maxQty      = 30
                            )
                            Text("${followupDaysAfter}d", modifier = Modifier.width(40.dp))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Repeat every", Modifier.weight(1f))
                            QtyStepperRow(
                                qty         = followupRepeatEvery,
                                onDecrement = { if (followupRepeatEvery > 1) followupRepeatEvery-- },
                                onIncrement = { if (followupRepeatEvery < 30) followupRepeatEvery++ },
                                minQty      = 1,
                                maxQty      = 30
                            )
                            Text("${followupRepeatEvery}d", modifier = Modifier.width(40.dp))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Max reminders", Modifier.weight(1f))
                            QtyStepperRow(
                                qty         = followupMaxReminders,
                                onDecrement = { if (followupMaxReminders > 1) followupMaxReminders-- },
                                onIncrement = { if (followupMaxReminders < 10) followupMaxReminders++ },
                                minQty      = 1,
                                maxQty      = 10
                            )
                            Text("${followupMaxReminders}x", modifier = Modifier.width(40.dp))
                        }
                        Text("Reminder Method", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("email" to "Email", "sms" to "SMS", "both" to "Both").forEach { (value, label) ->
                                FilterChip(
                                    selected = followupMethod == value,
                                    onClick  = { followupMethod = value },
                                    label    = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // ── Save button ────────────────────────────────────────────────
            AppButton(
                onClick = {
                    vm.saveSettings(
                        enabled              = enabled,
                        companyDisplayName   = displayName,
                        companyTagline       = tagline,
                        workingDays          = workingDays,
                        timeWindows          = timeWindows,
                        maxBookingsPerWindow = maxPerWindow,
                        serviceAreas         = serviceAreas,
                        services             = services,
                        confirmationMessage  = confirmationMessage,
                        reminderEnabled      = reminderEnabled,
                        reminderHoursBefore  = reminderHours,
                        reminderMethod       = reminderMethod,
                        followupEnabled      = followupEnabled,
                        followupDaysAfter    = followupDaysAfter,
                        followupRepeatEvery  = followupRepeatEvery,
                        followupMaxReminders = followupMaxReminders,
                        followupMethod       = followupMethod
                    )
                },
                label       = "Save Settings",
                modifier    = Modifier.fillMaxWidth().height(52.dp),
                enabled     = !saving,
                loading     = saving,
                leadingIcon = Icons.Default.Save
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Add / Edit Service Area Dialog ─────────────────────────────────────────
    if (showAddServiceAreaDialog || editingServiceArea != null) {
        ServiceAreaDialog(
            initial = editingServiceArea,
            onDismiss = {
                showAddServiceAreaDialog = false
                editingServiceArea       = null
                editingServiceAreaIndex  = -1
            },
            onSave = { sa ->
                if (editingServiceAreaIndex >= 0) {
                    serviceAreas = serviceAreas.toMutableList().also { it[editingServiceAreaIndex] = sa }
                } else {
                    serviceAreas = serviceAreas + sa
                }
                showAddServiceAreaDialog = false
                editingServiceArea       = null
                editingServiceAreaIndex  = -1
            }
        )
    }
}

// ─── Service Area Dialog ──────────────────────────────────────────────────────
@Composable
private fun ServiceAreaDialog(
    initial: ServiceArea?,
    onDismiss: () -> Unit,
    onSave: (ServiceArea) -> Unit
) {
    var zipCode      by remember { mutableStateOf(initial?.zipCode ?: "") }
    var radiusMiles  by remember { mutableStateOf((initial?.radiusMiles ?: 25).toFloat()) }
    var label        by remember { mutableStateOf(initial?.label ?: "") }

    val zipValid = zipCode.length == 5 && zipCode.all { it.isDigit() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Service Area" else "Edit Service Area",
                      fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = zipCode,
                    onValueChange = { if (it.length <= 5 && it.all { c -> c.isDigit() }) zipCode = it },
                    label = { Text("ZIP Code *") },
                    placeholder = { Text("e.g. 23451") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    isError = zipCode.isNotEmpty() && !zipValid
                )

                Column {
                    Text("Coverage Radius: ${radiusMiles.toInt()} miles",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = radiusMiles,
                        onValueChange = { radiusMiles = it },
                        valueRange = 1f..100f,
                        steps = 98,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1 mi", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        Text("100 mi", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    placeholder = { Text("e.g. Virginia Beach") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(ServiceArea(
                        zipCode     = zipCode,
                        radiusMiles = radiusMiles.toInt(),
                        label       = label.ifBlank { null }
                    ))
                },
                enabled = zipValid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
