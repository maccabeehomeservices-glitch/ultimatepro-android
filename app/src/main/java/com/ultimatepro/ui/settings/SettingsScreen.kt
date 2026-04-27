package com.ultimatepro.ui.settings

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.ui.auth.AuthViewModel
import com.ultimatepro.ui.common.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── NetworkIdViewModel ───────────────────────────────────────────────────────
@HiltViewModel
class NetworkIdViewModel @Inject constructor(
    private val repo: CrmRepository
) : ViewModel() {
    private val _ucmId = MutableStateFlow<String?>(null)
    val ucmId = _ucmId.asStateFlow()

    fun load() = viewModelScope.launch {
        @Suppress("UNCHECKED_CAST")
        when (val r = repo.getMyNetworkId()) {
            is Result.Success -> _ucmId.value = r.data["ultimatecrm_id"] as? String
            is Result.Error   -> { /* keep null */ }
        }
    }
}

// ─── SettingsScreen ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onCalendar:   () -> Unit,
    onEstimates:  () -> Unit,
    onInvoices:   () -> Unit,
    onPayments:   () -> Unit,
    onReports:    () -> Unit,
    onPayroll:    () -> Unit,
    onLiveMap:    () -> Unit,
    onPricebook:        () -> Unit = {},
    onNetwork:          () -> Unit = {},
    onReviewPlatforms:  () -> Unit = {},
    onOnlineBooking:    () -> Unit = {},
    onJobSources:          () -> Unit = {},
    onMembershipPlans:     () -> Unit = {},
    onInventory:           () -> Unit = {},
    onTechnicians:         () -> Unit = {},
    onTeamMembers:         () -> Unit = {},
    onCustomFields:        () -> Unit = {},
    onAilot:               () -> Unit = {},
    onIntegrations:        () -> Unit = {},
    onCompanyProfile:      () -> Unit = {},
    onLogout:     () -> Unit,
    isDark:       Boolean = false,
    onToggleDark: (Boolean) -> Unit = {},
    notifPrefs:   NotificationPreferences? = null,
    authVm: AuthViewModel = hiltViewModel(),
    networkIdVm: NetworkIdViewModel = hiltViewModel()
) {
    var showLogout by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ucmId by networkIdVm.ucmId.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) { networkIdVm.load() }

    // Collect notification preference states
    val notifNewJobs       by (notifPrefs?.newJobs       ?: kotlinx.coroutines.flow.flowOf(true)).collectAsState(true)
    val notifStatus        by (notifPrefs?.statusUpdates ?: kotlinx.coroutines.flow.flowOf(true)).collectAsState(true)
    val notifPartnerJobs   by (notifPrefs?.partnerJobs   ?: kotlinx.coroutines.flow.flowOf(true)).collectAsState(true)
    val notifNewBookings   by (notifPrefs?.newBookings   ?: kotlinx.coroutines.flow.flowOf(true)).collectAsState(true)
    val notifEstimates     by (notifPrefs?.estimateSigned ?: kotlinx.coroutines.flow.flowOf(true)).collectAsState(true)

    Scaffold(topBar = { TopAppBar(title = { Text("More", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))

            Section("Appearance") {
                ListItem(
                    headlineContent = { Text("Dark Mode") },
                    supportingContent = {
                        Text(if (isDark) "Dark" else "Light",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    leadingContent = {
                        Icon(if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = {
                        AppSwitch(
                            checked         = isDark,
                            onCheckedChange = onToggleDark
                        )
                    }
                )
            }

            Section("Views") {
                Item("Calendar",   Icons.Default.CalendarMonth, onCalendar)
                Item("Live Map",   Icons.Default.Map,           onLiveMap)
                Item("Reports",    Icons.Default.BarChart,      onReports)
            }

            Section("Business") {
                Item("Company Profile", Icons.Default.Business, onCompanyProfile,
                    description = "Name, logo, contact info, address")
                Item("Price Book", Icons.Default.MenuBook,      onPricebook,
                    description = "Manage services, materials, and pricing")
                Item("My Network", Icons.Default.People,        onNetwork,
                    description = "Connect with contractors, share jobs, split revenue")
                Item("Job Sources",      Icons.Default.Source,        onJobSources,
                    description = "Track where jobs come from — contacts, ads, network")
                Item("Review Platforms", Icons.Default.Star,   onReviewPlatforms,
                    description = "Manage review links sent with payment receipts")
                Item("Online Booking",  Icons.Default.CalendarMonth, onOnlineBooking,
                    description = "Let customers book appointments from a web link")
                Item("Membership Plans", Icons.Default.Autorenew, onMembershipPlans,
                    description = "Define recurring service plans for customers")
                Item("Inventory", Icons.Default.Inventory2, onInventory,
                    description = "Track parts across warehouse and trucks")
                Item("Technicians", Icons.Default.Engineering, onTechnicians,
                    description = "Manage field techs without app logins")
                Item("Team Members", Icons.Default.ManageAccounts, onTeamMembers,
                    description = "Manage app users, roles, and access")
                Item("Custom Fields", Icons.Default.Label, onCustomFields,
                    description = "Add custom data fields to jobs, customers, estimates")
                Item("⚡ Ailot", Icons.Default.FlashOn, onAilot,
                    description = "Smart Automation Rules")
                Item("Integrations", Icons.Default.Link, onIntegrations,
                    description = "Connect QuickBooks Online and other tools")
            }

            Section("Notifications") {
                NotifToggle("New Jobs",            Icons.Default.Work,           notifNewJobs)     { on -> scope.launch { notifPrefs?.setNewJobs(on) } }
                NotifToggle("Job Status Updates",  Icons.Default.Update,         notifStatus)      { on -> scope.launch { notifPrefs?.setStatusUpdates(on) } }
                NotifToggle("Partner Jobs",        Icons.Default.Handshake,      notifPartnerJobs) { on -> scope.launch { notifPrefs?.setPartnerJobs(on) } }
                NotifToggle("New Bookings",        Icons.Default.CalendarMonth,  notifNewBookings) { on -> scope.launch { notifPrefs?.setNewBookings(on) } }
                NotifToggle("Estimate Signed",     Icons.Default.Draw,           notifEstimates)   { on -> scope.launch { notifPrefs?.setEstimateSigned(on) } }
            }

            Section("Finance") {
                Item("Estimates",  Icons.Default.Description,   onEstimates)
                Item("Invoices",   Icons.Default.Receipt,       onInvoices)
                Item("Payments",   Icons.Default.Payment,       onPayments)
            }

            Section("Payroll") {
                Item("Payroll & Reports", Icons.Default.AccountBalance, onPayroll,
                    description = "Earnings, job reports, tech salary, bonuses")
            }

            // ── My UltimatePro ID ──────────────────────────────────────────
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("MY ULTRAPRO ID", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = AppColors.Blue,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                ucmId ?: "Loading...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = if (ucmId != null) AppColors.Blue
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        ucmId?.let { clipboard.setText(AnnotatedString(it)) }
                                    },
                                    enabled = ucmId != null
                                ) {
                                    Icon(Icons.Default.ContentCopy, "Copy ID",
                                        tint = if (ucmId != null) AppColors.Blue
                                               else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(
                                    onClick = {
                                        ucmId?.let { id ->
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "Find me on UltimatePro: $id")
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share UltimatePro ID"))
                                        }
                                    },
                                    enabled = ucmId != null
                                ) {
                                    Icon(Icons.Default.Share, "Share ID",
                                        tint = if (ucmId != null) AppColors.Blue
                                               else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Share this ID with other contractors to connect on the network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Section("Account") {
                Item("Sign Out",   Icons.Default.Logout, { showLogout = true }, tint = AppColors.Red)
            }

            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("UltimatePro v1.0.0", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title   = { Text("Sign Out?") },
            text    = { Text("You will be signed out of UltimatePro.") },
            confirmButton = {
                TextButton(onClick = { onLogout(); showLogout = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Red)) { Text("Sign Out") }
            },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, color = AppColors.Blue,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(content = content)
        }
    }
}

@Composable
private fun NotifToggle(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent  = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { AppSwitch(checked = checked, onCheckedChange = onToggle) }
    )
}

@Composable
private fun Item(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
    description: String? = null
) {
    ListItem(
        headlineContent = {
            Text(label, color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint)
        },
        supportingContent = description?.let { { Text(it, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        leadingContent = {
            Icon(icon, null,
                tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint)
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
