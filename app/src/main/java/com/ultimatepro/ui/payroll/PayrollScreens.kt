package com.ultimatepro.ui.payroll

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ultimatepro.data.repository.*
import com.ultimatepro.domain.model.User
import com.ultimatepro.ui.common.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.ultimatepro.ui.dashboard.TimesheetViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

// P2.23: the backend returns SUM(numeric)/pct fields as JSON STRINGS ("780.00", "40.00",
// "3"). `as? Number` was null on strings → every money/commission field defaulted to 0
// (Reports/Payroll showed $0 despite correct backend data). These parse String OR Number.
private fun numD(v: Any?): Double = when (v) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull() ?: 0.0
    else -> 0.0
}
private fun numI(v: Any?): Int = numD(v).toInt()
// P2.27 F-c: report rows showed raw ISO ("2026-07-07T00:00:00.000Z"). Format YYYY-MM-DD → "Jul 7, 2026".
private val REPORT_MONTHS = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
private fun fmtReportDate(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val p = raw.take(10).split("-")
    return if (p.size == 3) "${REPORT_MONTHS.getOrElse((p[1].toIntOrNull() ?: 1) - 1) { p[1] }} ${p[2].toIntOrNull() ?: p[2]}, ${p[0]}" else raw.take(10)
}
private fun numDN(v: Any?): Double? = when (v) {   // nullable: null = field absent
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

// ── Data classes ────────────────────────────────────────────────────────────

data class TechPaySummary(
    val id: String = "",
    val first_name: String = "",
    val last_name: String = "",
    val color: String = "#1565C0",
    val hourly_rate: Double = 0.0,
    val commission_pct: Double = 0.0,
    val jobs_count: Int = 0,
    val total_sales: Double = 0.0,
    val total_material: Double = 0.0,
    val gross_earnings: Double = 0.0,
    val total_hours: Double = 0.0,
    val pending_bonuses: Double = 0.0,
    val pending_deductions: Double = 0.0,
    val balance_owed: Double = 0.0,
    val is_roster: Boolean = false,   // P2.27: actor type for the new /reports/{actor} drill routing
    val is_source: Boolean = false
) {
    val fullName get() = "$first_name $last_name".trim()
    val initials get() = "${first_name.take(1)}${last_name.take(1)}".uppercase()
    val actorType get() = if (is_source) "source" else if (is_roster) "roster" else "tech"
}

data class JobReportRow(
    val job_id: String = "",
    val job_number: String = "",
    val job_title: String = "",
    val job_date: String = "",
    val job_source: String? = null,
    val customer_name: String = "",
    val customer_phone: String? = null,
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val tech_name: String? = null,
    val tech_color: String? = null,
    val total_sale: Double = 0.0,
    val material_cost: Double = 0.0,
    val net_profit: Double = 0.0,
    val source_cost: Double = 0.0,
    val tech_profit: Double = 0.0,
    val company_profit: Double = 0.0,
    val hours_worked: Double = 0.0,
    val pay_type: String = "commission",
    val earning_paid: Boolean = false,
    val invoice_number: String? = null
)

data class TechJobRow(
    val job_id: String = "",
    val job_number: String = "",
    val job_title: String = "",
    val job_date: String = "",
    val job_source: String? = null,
    val customer_name: String = "",
    val address: String = "",
    val city: String = "",
    val total_sale: Double = 0.0,
    val material_cost: Double = 0.0,
    val profit: Double = 0.0,
    val hours_worked: Double = 0.0,
    val pay_type: String = "commission",
    val paid: Boolean = false
)

data class BonusRow(
    val id: String = "",
    val amount: Double = 0.0,
    val reason: String = "",
    val paid: Boolean = false,
    val created_at: String? = null
)

data class DeductionRow(
    val id: String = "",
    val amount: Double = 0.0,
    val reason: String = "",
    val deduction_type: String = "other",
    val applied: Boolean = false,
    val created_at: String? = null
)

// ── ViewModel ───────────────────────────────────────────────────────────────

data class PayrollState(
    val loading: Boolean = true,
    val summaryLoading: Boolean = false,
    val actorReportLoading: Boolean = false,   // P2.27: per-actor /reports/{actor} drill
    val jobReportLoading: Boolean = false,
    val techSummaries: List<TechPaySummary> = emptyList(),
    val totalSales: Double = 0.0,
    val totalTechCost: Double = 0.0,
    val totalCompanyProfit: Double = 0.0,
    val totalMaterial: Double = 0.0,
    val totalSourceCost: Double = 0.0,
    val totalJobs: Int = 0,
    val jobReport: List<JobReportRow> = emptyList(),
    val jobReportTotals: Map<String, Any> = emptyMap(),
    val selectedActorReport: Map<String, Any?> = emptyMap(),   // P2.27: Bundle-4 per-actor report payload
    val techs: List<User> = emptyList(),
    val period: String = "month",   // P2.20: match web Payroll's month-to-date default (identical day-window across platforms)
    val customFrom: String = "",
    val customTo: String = "",
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class PayrollViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {

    private val _state = MutableStateFlow(PayrollState())
    val state: StateFlow<PayrollState> = _state.asStateFlow()

    private val gson = Gson()

    init {
        viewModelScope.launch {
            val techsResult = repo.getTechnicians()
            _state.update { it.copy(techs = (techsResult as? Result.Success)?.data ?: emptyList()) }
            loadSummary("month")   // P2.20: match web Payroll month-to-date default
        }
    }

    fun loadSummary(period: String, from: String = "", to: String = "") {
        viewModelScope.launch {
            _state.update { it.copy(summaryLoading = true, period = period, customFrom = from, customTo = to) }
            val params = buildPeriodParams(period, from, to)
            when (val r = repo.getPayrollSummary(params)) {
                is Result.Success -> {
                    val data = r.data
                    @Suppress("UNCHECKED_CAST")
                    val techList = (data["techs"] as? List<Map<String, Any>>) ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val totals = (data["totals"] as? Map<String, Any>) ?: emptyMap()

                    _state.update { it.copy(
                        summaryLoading  = false,
                        techSummaries   = techList.map { parseTechSummary(it) },
                        totalSales      = numD(totals["total_sales"]),
                        totalMaterial   = numD(totals["total_material"]),
                        totalSourceCost = numD(totals["total_source_cost"]),
                        totalTechCost   = numD(totals["total_tech_cost"]),
                        totalCompanyProfit = numD(totals["total_company_profit"]),
                        totalJobs       = numI(totals["total_jobs"]),
                        loading         = false,
                    )}
                }
                is Result.Error -> _state.update { it.copy(summaryLoading = false, error = r.message, loading = false) }
            }
        }
    }

    fun loadJobReport(period: String, from: String = "", to: String = "", techId: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(jobReportLoading = true) }
            val params = buildPeriodParams(period, from, to) + if (techId != null) mapOf("tech_id" to techId) else emptyMap()
            when (val r = repo.getJobReport(params)) {
                is Result.Success -> {
                    val data = r.data
                    @Suppress("UNCHECKED_CAST")
                    val jobs = (data["jobs"] as? List<Map<String, Any>>) ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val totals = (data["totals"] as? Map<String, Any>) ?: emptyMap()
                    _state.update { it.copy(
                        jobReportLoading = false,
                        jobReport = jobs.map { parseJobRow(it) },
                        jobReportTotals = totals
                    )}
                }
                is Result.Error -> _state.update { it.copy(jobReportLoading = false, error = r.message) }
            }
        }
    }

    // P2.27 (Bundle 4): drill an actor (tech/roster/source/partner/self) onto the NEW
    // /reports/{actor} endpoints that web + the report PDFs use — same reference columns
    // (payment-method split, parts, fees, tip, balance) and the same numbers, replacing the
    // old payroll/tech-report path that diverged from web (P2.27 KEY FINDING).
    fun loadActorReport(actorType: String, id: String, period: String, from: String = "", to: String = "") {
        viewModelScope.launch {
            _state.update { it.copy(actorReportLoading = true, selectedActorReport = emptyMap()) }
            // The /reports/{actor} endpoints read from/to (NOT period) and default to a rolling
            // 30 days when absent. Send explicit month-to-date / week-to-date / today bounds —
            // the SAME window payroll/summary uses for the Overview cards — so the drill
            // reconciles to the cent with Overview + web (which also send explicit from/to).
            val (rangeFrom, rangeTo) = actorReportRange(period, from, to)
            val params = mapOf("from" to rangeFrom, "to" to rangeTo)
            when (val r = repo.getActorReport(actorType, id, params)) {
                is Result.Success -> _state.update { it.copy(actorReportLoading = false, selectedActorReport = r.data) }
                is Result.Error   -> _state.update { it.copy(actorReportLoading = false, error = r.message) }
            }
        }
    }

    // period → explicit [from,to] range (month-to-date etc.), matching payroll/summary's windows.
    private fun actorReportRange(period: String, from: String, to: String): Pair<String, String> {
        if (period == "custom" && from.isNotBlank() && to.isNotBlank()) return from to to
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val today = sdf.format(cal.time)
        return when (period) {
            "today" -> today to today
            // Monday-start ISO week to mirror the backend (Luxon startOf('week') = Monday) so
            // the Week drill reconciles with the Overview `period=week` cards to the cent.
            "week"  -> {
                val dow = cal.get(Calendar.DAY_OF_WEEK)            // Sun=1..Sat=7
                val back = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
                cal.add(Calendar.DAY_OF_MONTH, -back)
                sdf.format(cal.time) to today
            }
            else    -> { cal.set(Calendar.DAY_OF_MONTH, 1); sdf.format(cal.time) to today }   // month
        }
    }

    fun addBonus(userId: String, amount: Double, reason: String) {
        viewModelScope.launch {
            when (repo.addBonus(mapOf("user_id" to userId, "amount" to amount, "reason" to reason))) {
                is Result.Success -> { _state.update { it.copy(message = "Bonus added") }; refreshCurrentPeriod() }
                is Result.Error   -> _state.update { it.copy(error = "Failed to add bonus") }
            }
        }
    }

    fun addDeduction(userId: String, amount: Double, reason: String, type: String) {
        viewModelScope.launch {
            when (repo.addDeduction(mapOf("user_id" to userId, "amount" to amount, "reason" to reason, "deduction_type" to type))) {
                is Result.Success -> { _state.update { it.copy(message = "Deduction added") }; refreshCurrentPeriod() }
                is Result.Error   -> _state.update { it.copy(error = "Failed to add deduction") }
            }
        }
    }

    // Option-1 pay run: mark every earning in the current range paid. Reuses the same
    // period/from-to the summary is showing; backend resolves dates identically.
    fun markRangePaid() {
        viewModelScope.launch {
            val s = _state.value
            when (val r = repo.markEarningsPaid(buildPeriodParams(s.period, s.customFrom, s.customTo))) {
                is Result.Success -> {
                    val n = numI(r.data["count"])
                    _state.update { it.copy(message = "Marked $n earning(s) paid") }
                    refreshCurrentPeriod()
                }
                is Result.Error -> _state.update { it.copy(error = r.message ?: "Failed to mark paid") }
            }
        }
    }

    fun clearMessages() { _state.update { it.copy(error = null, message = null) } }

    private fun refreshCurrentPeriod() {
        val s = _state.value
        loadSummary(s.period, s.customFrom, s.customTo)
    }

    private fun buildPeriodParams(period: String, from: String, to: String): Map<String, String> {
        return if (period == "custom" && from.isNotBlank() && to.isNotBlank()) {
            mapOf("from" to from, "to" to to)
        } else {
            mapOf("period" to period)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTechSummary(m: Map<String, Any>) = TechPaySummary(
        id             = m["id"]?.toString() ?: "",
        first_name     = m["first_name"]?.toString() ?: "",
        last_name      = m["last_name"]?.toString() ?: "",
        color          = m["color"]?.toString() ?: "#1565C0",
        hourly_rate    = numD(m["hourly_rate"]),
        commission_pct = numD(m["commission_pct"]),
        jobs_count     = numI(m["jobs_count"]),
        total_sales    = numD(m["total_sales"]),
        total_material = numD(m["total_material"]),
        gross_earnings = numD(m["gross_earnings"]),
        total_hours    = numD(m["total_hours"]),
        pending_bonuses = numD(m["pending_bonuses"]),
        pending_deductions = numD(m["pending_deductions"]),
        balance_owed   = numD(m["balance_owed"]),
        is_roster      = m["is_roster"] as? Boolean ?: false,
        is_source      = m["is_source"] as? Boolean ?: false
    )

    @Suppress("UNCHECKED_CAST")
    private fun parseJobRow(m: Map<String, Any>) = JobReportRow(
        job_id         = m["job_id"]?.toString() ?: "",
        job_number     = m["job_number"]?.toString() ?: "",
        job_title      = m["job_title"]?.toString() ?: "",
        job_date       = m["job_date"]?.toString() ?: "",
        job_source     = m["job_source"]?.toString(),
        customer_name  = m["customer_name"]?.toString() ?: "",
        customer_phone = m["customer_phone"]?.toString(),
        address        = m["address"]?.toString() ?: "",
        city           = m["city"]?.toString() ?: "",
        state          = m["state"]?.toString() ?: "",
        tech_name      = m["tech_name"]?.toString(),
        tech_color     = m["tech_color"]?.toString(),
        total_sale     = numD(m["total_sale"]),
        material_cost  = numD(m["material_cost"]),
        net_profit     = numD(m["net_profit"]),
        source_cost    = numD(m["source_cost"]),
        tech_profit    = numD(m["tech_profit"]),
        company_profit = numD(m["company_profit"]),
        hours_worked   = numD(m["hours_worked"]),
        pay_type       = m["pay_type"]?.toString() ?: "commission",
        earning_paid   = m["earning_paid"] as? Boolean ?: false,
        invoice_number = m["invoice_number"]?.toString()
    )
}

// ── Main Payroll Screen ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayrollScreen(
    onActorDetail:    (String, String) -> Unit,   // P2.27: (actorType, id) → Bundle-4 per-actor report
    onTechSettings:   (String) -> Unit,
    onReimbursements: () -> Unit,
    onSimulator:      () -> Unit,
    onBack:           () -> Unit,
    vm: PayrollViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val authVm: com.ultimatepro.ui.auth.AuthViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val perms by authVm.permissions.collectAsState()
    val role by authVm.role.collectAsState()
    var tab       by remember { mutableIntStateOf(0) }
    var period    by remember { mutableStateOf("month") }   // P2.20: match VM + web month-to-date default
    var showPeriod by remember { mutableStateOf(false) }
    var showMarkPaid by remember { mutableStateOf(false) }
    val snack     = remember { SnackbarHostState() }

    val tabs = listOf("Overview", "Job Report", "By Tech")

    LaunchedEffect(state.message) { if (state.message != null) { snack.showSnackbar(state.message!!); vm.clearMessages() } }
    LaunchedEffect(state.error)   { if (state.error   != null) { snack.showSnackbar(state.error!!);   vm.clearMessages() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Payroll & Reports", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    // Period selector
                    TextButton(onClick = { showPeriod = true }) {
                        Text(periodLabel(period), color = AppColors.Blue)
                        Icon(Icons.Default.ArrowDropDown, null, tint = AppColors.Blue)
                    }
                    if (com.ultimatepro.domain.model.canUi(role, perms, "accounting_earnings", "full"))
                        IconButton(onClick = { showMarkPaid = true }) { Icon(Icons.Default.Paid, "Mark range paid", tint = AppColors.Green) }
                    IconButton(onClick = { vm.loadSummary(period) }) { Icon(androidx.compose.ui.res.painterResource(com.ultimatepro.R.drawable.up_refresh), null) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Quick-access row ────────────────────────────────────────
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(onClick = onSimulator, label = "Simulator", modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Default.Calculate)
                AppButton(onClick = onReimbursements, label = "Reimburse", modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Default.Receipt)
            }

            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = {
                        tab = i
                        when (i) {
                            0 -> vm.loadSummary(period)
                            1 -> vm.loadJobReport(period)
                        }
                    }, text = { Text(t) })
                }
            }

            when (tab) {
                0 -> OverviewTab(state, onActorDetail, onTechSettings, vm)
                1 -> JobReportTab(state, vm, period)
                2 -> ByTechTab(state, onActorDetail, onTechSettings, vm, period)
            }
        }
    }

    // Mark range paid (confirm — it's a money action)
    if (showMarkPaid) {
        AlertDialog(
            onDismissRequest = { showMarkPaid = false },
            title = { Text("Mark range paid?", fontWeight = FontWeight.Bold) },
            text = { Text("Mark all earnings in ${periodLabel(period)} as paid? This records that the team was paid for this range and lowers balance owed. Earnings can still recompute if a payment or refund lands later.") },
            confirmButton = {
                TextButton(onClick = { showMarkPaid = false; vm.markRangePaid() }) {
                    Text("Mark Paid", color = AppColors.Green, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { showMarkPaid = false }) { Text("Cancel") } }
        )
    }

    // Period picker
    if (showPeriod) {
        ModalBottomSheet(onDismissRequest = { showPeriod = false }) {
            SheetHandle()
            Text("Select Period", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            listOf("today" to "Today", "week" to "This Week",
                   "month" to "This Month", "custom" to "Custom Range").forEach { (key, label) ->
                ListItem(
                    headlineContent = { Text(label, fontWeight = if (period == key) FontWeight.Bold else FontWeight.Normal) },
                    trailingContent = { if (period == key) Icon(Icons.Default.Check, null, tint = AppColors.Green) },
                    modifier = Modifier.clickable {
                        period = key
                        showPeriod = false
                        vm.loadSummary(key)
                        if (tab == 1) vm.loadJobReport(key)
                    }
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Overview Tab ─────────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(state: PayrollState, onActorDetail: (String, String) -> Unit, onTechSettings: (String) -> Unit, vm: PayrollViewModel) {
    if (state.summaryLoading) { LoadingView(); return }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Company profit summary
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Blue.copy(alpha = 0.08f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Company Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        FinStat("Total Sales",   formatMoney(state.totalSales),           AppColors.Blue)
                        FinStat("Material",      "-${formatMoney(state.totalMaterial)}",  AppColors.Slate)
                        FinStat("Source Cost",   "-${formatMoney(state.totalSourceCost)}", AppColors.Orange)
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        FinStat("Tech Payout",   formatMoney(state.totalTechCost),       AppColors.Purple)
                        FinStat("Company Profit",formatMoney(state.totalCompanyProfit),  AppColors.Green)
                        FinStat("Jobs",          "${state.totalJobs}",                   AppColors.Slate)
                    }
                }
            }
        }

        // Tech payroll cards
        item {
            Text("Technician Payroll", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
        }

        if (state.techSummaries.isEmpty()) {
            item { EmptyView("No completed jobs in this period", Icons.Default.Work) }
        } else {
            items(state.techSummaries, key = { it.id }) { tech ->
                TechPayCard(tech = tech, onDetail = { onActorDetail(tech.actorType, tech.id) }, onSettings = { onTechSettings(tech.id) }, vm = vm)
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun TechPayCard(tech: TechPaySummary, onDetail: () -> Unit, onSettings: () -> Unit, vm: PayrollViewModel) {
    val color     = try { Color(android.graphics.Color.parseColor(tech.color)) } catch (e: Exception) { AppColors.Blue }
    var expanded  by remember { mutableStateOf(false) }
    var showBonus by remember { mutableStateOf(false) }
    var showDeduct by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            // Tech header row
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(tech.initials, color, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tech.fullName, fontWeight = FontWeight.Bold)
                    Text(
                        if (tech.hourly_rate > 0 && tech.commission_pct == 0.0)
                            "Hourly @ ${formatMoney(tech.hourly_rate)}/hr"
                        else
                            "Commission ${tech.commission_pct.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("${tech.jobs_count} jobs  •  ${String.format("%.1f", tech.total_hours)}h",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Settings gear
                IconButton(onClick = onSettings, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Settings, "Pay Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatMoney(tech.balance_owed), fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (tech.balance_owed > 0) AppColors.Green else AppColors.Slate)
                    Text("balance owed", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Earnings breakdown
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Sales",    formatMoney(tech.total_sales),    AppColors.Blue)
                MiniStat("Material","-${formatMoney(tech.total_material)}", AppColors.Slate)
                MiniStat("Earnings", formatMoney(tech.gross_earnings), AppColors.Purple)
                MiniStat("Bonuses", "+${formatMoney(tech.pending_bonuses)}", AppColors.Green)
                MiniStat("Deducts", "-${formatMoney(tech.pending_deductions)}", AppColors.Red)
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppButton(onClick = onDetail, label = "Full Report", modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Default.OpenInNew)
                AppButton(onClick = { showBonus = true }, label = "Bonus", modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Default.Add)
                AppButton(onClick = { showDeduct = true }, label = "Deduct", modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Default.Remove, labelColor = AppColors.Red)
            }
        }
    }

    // Bonus dialog
    if (showBonus) {
        AddAmountDialog(
            title = "Add Bonus for ${tech.fullName}",
            confirmLabel = "Add Bonus",
            confirmColor = AppColors.Green,
            onConfirm = { amount, reason -> vm.addBonus(tech.id, amount, reason); showBonus = false },
            onDismiss = { showBonus = false }
        )
    }

    // Deduction dialog
    if (showDeduct) {
        AddAmountDialog(
            title = "Add Deduction for ${tech.fullName}",
            confirmLabel = "Add Deduction",
            confirmColor = AppColors.Red,
            onConfirm = { amount, reason -> vm.addDeduction(tech.id, amount, reason, "other"); showDeduct = false },
            onDismiss = { showDeduct = false }
        )
    }
}

// ── Job Report Tab ─────────────────────────────────────────────────────────

@Composable
private fun JobReportTab(state: PayrollState, vm: PayrollViewModel, period: String) {
    LaunchedEffect(Unit) { vm.loadJobReport(period) }
    if (state.jobReportLoading) { LoadingView(); return }

    val totals = state.jobReportTotals

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Totals bar
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Blue.copy(alpha = 0.08f))) {
                Column(Modifier.padding(12.dp)) {
                    Text("${state.jobReport.size} Jobs", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MiniStat("Sales",    formatMoney(numD(totals["total_sales"])), AppColors.Blue)
                        MiniStat("Material", formatMoney(numD(totals["total_material"])), AppColors.Slate)
                        MiniStat("Source",   formatMoney(numD(totals["total_source_cost"])), AppColors.Orange)
                        MiniStat("Tech",     formatMoney(numD(totals["total_tech_profit"])), AppColors.Purple)
                        MiniStat("Company",  formatMoney(numD(totals["total_company_profit"])), AppColors.Green)
                    }
                }
            }
        }

        if (state.jobReport.isEmpty()) {
            item { EmptyView("No completed jobs in this period", Icons.Default.Work) }
        } else {
            items(state.jobReport, key = { it.job_id }) { job ->
                JobReportCard(job)
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun JobReportCard(job: JobReportRow) {
    var expanded by remember { mutableStateOf(false) }
    val techColor = try { job.tech_color?.let { Color(android.graphics.Color.parseColor(it)) } ?: AppColors.Blue }
                   catch (e: Exception) { AppColors.Blue }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier
            .padding(12.dp)
            .clickable { expanded = !expanded }) {
            // Row 1: job number + date + paid badge
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(job.job_number, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    job.job_source?.let { StatusBadge(it, AppColors.Accent, small = true) }
                    if (job.earning_paid) StatusBadge("Paid", AppColors.Green, small = true)
                    Text(fmtReportDate(job.job_date), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Row 2: customer name + address
            Text(job.customer_name, fontWeight = FontWeight.SemiBold)
            val addr = listOf(job.address, job.city, job.state).filter { it.isNotBlank() }.joinToString(", ")
            if (addr.isNotBlank()) Text(addr, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)

            // Row 3: tech name
            job.tech_name?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarCircle(it.take(2), techColor, 18.dp, 9.sp)
                    Spacer(Modifier.width(5.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Row 4: key financials (always visible)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Sale",    formatMoney(job.total_sale),     AppColors.Blue)
                MiniStat("Material",formatMoney(job.material_cost),  AppColors.Slate)
                MiniStat("Tech",    formatMoney(job.tech_profit),    AppColors.Purple)
                MiniStat("Company", formatMoney(job.company_profit), AppColors.Green)
            }

            // Expanded: full breakdown
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "Gross Sale"     to job.total_sale,
                        "Material Cost"  to -job.material_cost,
                        "Net Profit"     to job.net_profit,
                        "Source Cost"    to -job.source_cost,
                        "Tech Profit"    to job.tech_profit,
                        "Company Profit" to job.company_profit
                    ).forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                (if (value < 0) "-" else "") + formatMoney(Math.abs(value)),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    label == "Company Profit" -> AppColors.Green
                                    label == "Tech Profit"    -> AppColors.Purple
                                    value < 0                 -> AppColors.Red
                                    else                      -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                    if (job.pay_type == "hourly" && job.hours_worked > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("${String.format("%.2f", job.hours_worked)} hours worked (hourly)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    job.invoice_number?.let {
                        Text("Invoice: $it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Expand/collapse indicator
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, Modifier.align(Alignment.CenterHorizontally).size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── By Tech Tab ──────────────────────────────────────────────────────────────

@Composable
private fun ByTechTab(state: PayrollState, onActorDetail: (String, String) -> Unit, onTechSettings: (String) -> Unit, vm: PayrollViewModel, period: String) {
    // P2.27 F-b: source from the payroll/summary actor set (techSummaries) — which includes
    // ROSTER techs and SOURCES — not getTechnicians (users only). Roster techs were the
    // actors that carried the money on staging and were absent from this tab before.
    if (state.summaryLoading) { LoadingView(); return }
    if (state.techSummaries.isEmpty()) { EmptyView("No completed jobs in this period", Icons.Default.People); return }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Tap an actor to see their full reference report", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.techSummaries, key = { it.id }) { tech ->
            val color = try { Color(android.graphics.Color.parseColor(tech.color)) } catch (e: Exception) { AppColors.Blue }
            Card(onClick = { onActorDetail(tech.actorType, tech.id) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarCircle(tech.initials, color, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tech.fullName, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                tech.actorType == "source" -> "Source • ${tech.commission_pct.toInt()}% allocation"
                                tech.actorType == "roster"  -> "Roster • Commission ${tech.commission_pct.toInt()}%"
                                tech.hourly_rate > 0 && tech.commission_pct == 0.0 -> "Hourly @ ${formatMoney(tech.hourly_rate)}/hr"
                                else -> "Commission ${tech.commission_pct.toInt()}%"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("${tech.jobs_count} jobs this period",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatMoney(tech.balance_owed), fontWeight = FontWeight.Bold,
                            color = if (tech.balance_owed > 0) AppColors.Green else AppColors.Slate)
                        Text("owed", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Actor Detail Report Screen (P2.27 Bundle 4) ────────────────────────────
// Reference-column per-actor report (Pay Statement / Source Settlement) driven by the
// NEW /reports/{actor} endpoints that web + the report PDFs use — same reference columns
// (payment-method split, parts, fees, tip, balance) and the SAME numbers, replacing the
// old payroll/tech-report path that diverged from web (P2.27 KEY FINDING).

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActorReportScreen(
    actorType: String,
    id: String,
    onBack: () -> Unit,
    vm: PayrollViewModel = hiltViewModel(),
    tsVm: TimesheetViewModel = hiltViewModel()
) {
    val state    by vm.state.collectAsState()
    val tsReport by tsVm.report.collectAsState()
    var period   by remember { mutableStateOf("month") }   // match Overview month-to-date default

    LaunchedEffect(actorType, id) { vm.loadActorReport(actorType, id, period) }

    // Timesheet hours only apply to USER technicians (roster/source have none).
    LaunchedEffect(actorType, id, period) {
        if (actorType == "tech") {
            val (start, end) = payrollPeriodToDates(period)
            tsVm.loadReport(start, end, id)
        }
    }

    val report = state.selectedActorReport
    @Suppress("UNCHECKED_CAST")
    val actor   = (report["actor"] as? Map<String, Any?>) ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    val jobs    = (report["jobs"] as? List<Map<String, Any?>>) ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    val summary = (report["summary"] as? Map<String, Any?>) ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    val bonuses = (report["bonuses"] as? List<Map<String, Any?>>) ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    val deducts = (report["deductions"] as? List<Map<String, Any?>>) ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    val allTime = (report["all_time_balance"] as? Map<String, Any?>) ?: emptyMap()

    val isSource  = actorType == "source"
    val actorName = actor["name"]?.toString()?.takeIf { it.isNotBlank() } ?: "Report"
    val actorLabel = when (actorType) {
        "source"  -> "Source Settlement"
        "roster"  -> "Pay Statement (Roster)"
        "partner" -> "Partner Settlement"
        "self"    -> "Company Operations"
        else      -> "Pay Statement"
    }
    val commPct = numD(actor["commission_pct"]).let { if (it > 0) it else numD(actor["rate"]) }

    // Per-job reference roll-ups (Technician_Report reference summary boxes) — summed on the
    // client from the row data so the payment-method / parts / fees columns roll up on-screen.
    val sumTotal       = jobs.sumOf { numD(it["total_sale"]) }
    val sumFees        = jobs.sumOf { numD(it["fees"]) }
    val sumTParts      = jobs.sumOf { numD(it["tech_parts"]) }
    val sumCParts      = jobs.sumOf { numD(it["company_parts"]) }
    val sumCard        = jobs.sumOf { numD(it["card"]) }
    val sumCash        = jobs.sumOf { numD(it["cash"]) }
    val sumCheck       = jobs.sumOf { numD(it["check_amt"]) }
    val sumScan        = jobs.sumOf { numD(it["scanpay"]) }
    val sumVenmo       = jobs.sumOf { numD(it["venmo"]) }
    val sumTip         = jobs.sumOf { numD(it["tip"]) }
    val sumTechProfit  = jobs.sumOf { numD(it["tech_profit"]) }
    val sumSourceParts = jobs.sumOf { numD(it["parts"]) }
    val sumSourceEarned= jobs.sumOf { numD(it["source_earned"]) }
    val bonusTotal     = bonuses.sumOf { numD(it["amount"]) }
    val deductTotal    = deducts.sumOf { numD(it["amount"]) }

    // REPORT BALANCE (all-time, unpaid side). Source carries no bonuses/deductions ledger, so
    // its owed balance is the period settlement total.
    val allTimeOwed = numDN(allTime["unpaid"]) ?: (if (isSource) sumSourceEarned else 0.0)
    val allTimePaid = numD(allTime["paid"])
    // Net pay this period = period earnings + bonuses - deductions (source: settlement owed).
    val periodEarnings = if (isSource) sumSourceEarned else sumTechProfit
    val netPay = periodEarnings + bonusTotal - deductTotal

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(actorName, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(actorLabel, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = {
                listOf("today" to "Day", "week" to "Wk", "month" to "Mo").forEach { (key, label) ->
                    FilterChip(selected = period == key, onClick = {
                        period = key
                        vm.loadActorReport(actorType, id, key)
                        if (actorType == "tech") {
                            val (start, end) = payrollPeriodToDates(key)
                            tsVm.loadReport(start, end, id)
                        }
                    }, label = { Text(label, fontSize = 12.sp) })
                }
            }
        )
    }) { padding ->
        if (state.actorReportLoading) { LoadingView(); return@Scaffold }
        if (report.isEmpty()) { EmptyView("No report data", Icons.Default.Assessment); return@Scaffold }

        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── (source only) all-time settlement headline — the Tech Balance Sheet
            //    (tech/roster) drops the top headline per David's spec (bottom carries it). ──
            if (isSource) item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (allTimeOwed > 0) AppColors.Green.copy(0.1f) else AppColors.Slate.copy(0.1f)
                    )) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, null,
                            tint = if (allTimeOwed > 0) AppColors.Green else AppColors.Slate,
                            modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (isSource) "Settlement Owed (All Time)" else "Report Balance Owed (All Time)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatMoney(allTimeOwed), fontSize = 26.sp, fontWeight = FontWeight.Bold,
                                color = if (allTimeOwed > 0) AppColors.Green else AppColors.Slate)
                        }
                        if (allTimePaid > 0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Paid", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatMoney(allTimePaid), fontWeight = FontWeight.SemiBold, color = AppColors.Slate)
                            }
                        }
                    }
                }
            }

            // ── (source only) settlement summary ─────────────────────────
            if (isSource) item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (isSource) "Settlement Summary" else "Compensation Summary",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FinStat("Jobs", "${jobs.size}", AppColors.Blue)
                            FinStat("Total Sales", formatMoney(sumTotal), AppColors.Blue)
                            FinStat(if (isSource) "Rate" else "Comm %",
                                if (commPct > 0) "${commPct.toInt()}%" else "—", AppColors.Slate)
                        }
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FinStat(if (isSource) "Source Earned" else "Earnings", formatMoney(periodEarnings), AppColors.Purple)
                            if (!isSource) FinStat("+ Bonuses", "+${formatMoney(bonusTotal)}", AppColors.Green)
                            if (!isSource) FinStat("- Deductions", "-${formatMoney(deductTotal)}", AppColors.Red)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isSource) "Settlement This Period" else "Net Pay This Period", fontWeight = FontWeight.Bold)
                            Text(formatMoney(netPay), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppColors.Green)
                        }
                        // Timesheet hours (user techs only)
                        val tsTotalMins = tsReport?.summary?.firstOrNull()?.totalMinutes ?: 0
                        if (actorType == "tech" && tsTotalMins > 0) {
                            Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessTime, null, tint = AppColors.Blue, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Hours Worked", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${tsTotalMins / 60}h ${tsTotalMins % 60}m", fontWeight = FontWeight.SemiBold,
                                    color = AppColors.Blue, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // ── (source only) reference summary boxes ────────────────────
            if (isSource) item {
                val boxes = if (isSource)
                    listOf("Total" to sumTotal, "Parts" to sumSourceParts, "Source Earned" to sumSourceEarned)
                else
                    listOf("Total" to sumTotal, "Cash" to sumCash, "Card" to sumCard,
                           "Check" to sumCheck, "Venmo" to sumVenmo, "ScanPay" to sumScan,
                           "Fees" to sumFees, "T.Parts" to sumTParts, "C.Parts" to sumCParts,
                           "Tip" to sumTip)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Blue.copy(alpha = 0.06f))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Payment Breakdown", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        boxes.chunked(3).forEach { rowBoxes ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowBoxes.forEach { (label, value) ->
                                    Box(Modifier.weight(1f)) { MiniStat(label, formatMoney(value), AppColors.Slate) }
                                }
                                repeat(3 - rowBoxes.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }

            // ── Per-job rows: Tech Balance Sheet (tech/roster) or source settlement rows ──
            item {
                Text(if (isSource) "Jobs This Period" else "Tech Balance Sheet",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (jobs.isEmpty()) {
                item { EmptyView("No completed jobs in this period", Icons.Default.Work) }
            } else {
                items(jobs) { j -> if (isSource) ActorJobRow(j, true) else TechBalanceRow(j) }
            }
            // ── Bottom TOTAL + REPORT BALANCE (settlement) — Tech Balance Sheet only ──
            if (!isSource) item {
                val reportBalance = numD(summary["total_balance"])
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Blue.copy(alpha = 0.06f))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FinStat("Total", formatMoney(sumTotal), AppColors.Blue)
                            FinStat("Tech Cut", formatMoney(sumTechProfit), AppColors.Purple)
                            FinStat("Jobs", "${jobs.size}", AppColors.Slate)
                        }
                        Spacer(Modifier.height(10.dp)); HorizontalDivider(); Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("REPORT BALANCE", fontWeight = FontWeight.Bold)
                                Text(if (reportBalance < 0) "Company owes tech" else "Tech owes company",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(fmtSignedMoney(reportBalance), fontWeight = FontWeight.Bold, fontSize = 22.sp,
                                color = if (reportBalance < 0) AppColors.Green else AppColors.Orange)
                        }
                    }
                }
            }

            // ── Bonuses (user techs only) ────────────────────────────────
            if (bonuses.isNotEmpty()) {
                item { Text("Bonuses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(bonuses) { b ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Green.copy(alpha = 0.08f))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = AppColors.Green, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b["reason"]?.toString() ?: "", fontWeight = FontWeight.Medium)
                                b["created_at"]?.toString()?.take(10)?.let {
                                    Text(fmtReportDate(it), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("+${formatMoney(numD(b["amount"]))}", fontWeight = FontWeight.Bold, color = AppColors.Green)
                        }
                    }
                }
            }

            // ── Deductions (user techs only) ─────────────────────────────
            if (deducts.isNotEmpty()) {
                item { Text("Deductions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(deducts) { d ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Red.copy(alpha = 0.08f))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Remove, null, tint = AppColors.Red, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d["reason"]?.toString() ?: "", fontWeight = FontWeight.Medium)
                                Text(d["deduction_type"]?.toString()?.replaceFirstChar { it.uppercase() } ?: "",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("-${formatMoney(numD(d["amount"]))}", fontWeight = FontWeight.Bold, color = AppColors.Red)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// P2.27 Tech Balance Sheet (David's spec) — signed money + method-display map (mirrors the
// report PDF): credit_card/scanpay/venmo/cashapp/payment_link→CC, zelle/check→Check,
// cash→Cash, other→Other. Collector: tech='Tech', else 'Co.'.
private fun fmtSignedMoney(v: Double): String = (if (v < 0) "-" else "") + formatMoney(kotlin.math.abs(v))
private val BS_METHOD = mapOf(
    "credit_card" to "CC", "card" to "CC", "scanpay" to "CC", "venmo" to "CC", "cashapp" to "CC",
    "payment_link" to "CC", "paypal" to "CC", "zelle" to "Check", "check" to "Check", "cash" to "Cash")
private fun bsMethod(m: String?) = BS_METHOD[m] ?: "Other"
private fun bsCollector(c: String?) = if (c == "tech") "Tech" else "Co."

@Composable
private fun LabeledLines(label: String, lines: List<String>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.width(92.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium) }
        }
    }
}

// One per-job Tech Balance Sheet card (tech/roster): Ticket/Client/Date/Type + Total + signed
// Balance headline, then Payment ("Method (Collector) $amt", one line per payment) / Parts &
// Fees / Tech Profit (Cut + Rate or Hours) — content parity with the report PDF + web.
@Composable
private fun TechBalanceRow(j: Map<String, Any?>) {
    val bal = numD(j["balance"])
    val hours = numD(j["hours"]); val rate = numD(j["hourly_rate"]); val pct = numD(j["commission_pct"])
    val techParts = numD(j["tech_parts"]); val coParts = numD(j["company_parts"]); val fees = numD(j["fees"])
    @Suppress("UNCHECKED_CAST")
    val payments = (j["payments"] as? List<Map<String, Any?>>) ?: emptyList()

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(j["ticket"]?.toString() ?: "", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    j["job_type"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                        StatusBadge(it.replaceFirstChar { c -> c.uppercase() }, AppColors.Accent, small = true)
                    }
                    Text(fmtReportDate(j["date"]?.toString()), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(j["customer_name"]?.toString() ?: "", fontWeight = FontWeight.SemiBold)
            j["address"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(numD(j["total_sale"])), fontWeight = FontWeight.Bold, color = AppColors.Blue)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtSignedMoney(bal), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = if (bal < 0) AppColors.Green else AppColors.Orange)
                    Text(if (bal < 0) "company owes tech" else "tech owes company",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(6.dp))
            LabeledLines("Payment", if (payments.isEmpty()) listOf("—")
                else payments.map { "${bsMethod(it["method"]?.toString())} (${bsCollector(it["collected_by"]?.toString())})  ${formatMoney(numD(it["amount"]))}" })
            val pf = buildList {
                if (techParts > 0) add("T.Part: ${formatMoney(techParts)}")
                if (coParts > 0) add("C.Part: ${formatMoney(coParts)}")
                if (fees > 0) add("Fees: ${formatMoney(fees)}")
            }
            LabeledLines("Parts & Fees", if (pf.isEmpty()) listOf("—") else pf)
            val tp = buildList {
                add("Cut: ${formatMoney(numD(j["tech_profit"]))}")
                if (hours > 0 && rate > 0) add("Hours: ${if (hours % 1.0 == 0.0) hours.toInt().toString() else String.format("%.1f", hours)} @ ${formatMoney(rate)}/hr")
                if (pct > 0) add("Rate: ${pct.toInt()}%")
            }
            LabeledLines("Tech Profit", tp)
        }
    }
}

// One per-job reference row: core columns always visible + an expandable payment-method /
// parts / fees / tip breakdown (the full Technician_Report reference column set).
@Composable
private fun ActorJobRow(j: Map<String, Any?>, isSource: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val balance = numD(j["balance"])
    val commPct = numD(if (isSource) j["rate"] else j["commission_pct"])

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp).clickable { expanded = !expanded }) {
            // Row 1: ticket + date + balance
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(j["ticket"]?.toString() ?: "", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (balance > 0) StatusBadge("Bal ${formatMoney(balance)}", AppColors.Orange, small = true)
                    Text(fmtReportDate(j["date"]?.toString()), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            // Row 2: customer + address
            Text(j["customer_name"]?.toString() ?: "", fontWeight = FontWeight.SemiBold)
            j["address"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (isSource) j["job_info"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            // Row 3: core financials
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Total", formatMoney(numD(j["total_sale"])), AppColors.Blue)
                MiniStat(if (isSource) "Rate" else "Comm", if (commPct > 0) "${commPct.toInt()}%" else "—", AppColors.Slate)
                MiniStat(if (isSource) "Earned" else "Tech Profit",
                    formatMoney(numD(if (isSource) j["source_earned"] else j["tech_profit"])), AppColors.Purple)
            }
            // Expanded: full payment-method / parts / fees / tip breakdown
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    val rows = if (isSource)
                        listOf("Parts" to numD(j["parts"]), "Balance" to balance)
                    else
                        listOf("Cash" to numD(j["cash"]), "Card" to numD(j["card"]),
                               "Check" to numD(j["check_amt"]), "Venmo" to numD(j["venmo"]),
                               "ScanPay" to numD(j["scanpay"]), "Tip" to numD(j["tip"]),
                               "Tech Parts" to numD(j["tech_parts"]), "Company Parts" to numD(j["company_parts"]),
                               "Fees" to numD(j["fees"]), "Balance" to balance)
                    rows.filter { it.second != 0.0 }.forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatMoney(value), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (rows.none { it.second != 0.0 }) {
                        Text("No payment breakdown recorded", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                Modifier.align(Alignment.CenterHorizontally).size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Helper composables ────────────────────────────────────────────────────

private fun payrollPeriodToDates(period: String): Pair<String, String> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance()
    val today = sdf.format(cal.time)
    return when (period) {
        "today" -> today to today
        "week"  -> {   // Monday-start ISO week, mirroring the backend (P2.27 reconciliation)
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val back = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
            cal.add(Calendar.DAY_OF_MONTH, -back)
            sdf.format(cal.time) to today
        }
        "month" -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            sdf.format(cal.time) to today
        }
        else    -> today to today
    }
}

@Composable
private fun FinStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AddAmountDialog(
    title: String,
    confirmLabel: String,
    confirmColor: Color,
    onConfirm: (Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("Amount ($)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("Reason") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amt = amount.toDoubleOrNull()
                    if (amt != null && amt > 0 && reason.isNotBlank()) onConfirm(amt, reason)
                },
                enabled = amount.toDoubleOrNull()?.let { it > 0 } == true && reason.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(contentColor = confirmColor)
            ) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun periodLabel(period: String) = when (period) {
    "today" -> "Today"
    "week"  -> "This Week"
    "month" -> "This Month"
    else    -> "Custom"
}
