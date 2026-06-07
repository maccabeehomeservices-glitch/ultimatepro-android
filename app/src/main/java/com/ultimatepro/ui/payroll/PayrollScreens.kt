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
    val balance_owed: Double = 0.0
) {
    val fullName get() = "$first_name $last_name".trim()
    val initials get() = "${first_name.take(1)}${last_name.take(1)}".uppercase()
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
    val techReportLoading: Boolean = false,
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
    val selectedTechReport: Map<String, Any?> = emptyMap(),
    val techs: List<User> = emptyList(),
    val period: String = "week",
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
            loadSummary("week")
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
                        totalSales      = (totals["total_sales"] as? Number)?.toDouble() ?: 0.0,
                        totalMaterial   = (totals["total_material"] as? Number)?.toDouble() ?: 0.0,
                        totalSourceCost = (totals["total_source_cost"] as? Number)?.toDouble() ?: 0.0,
                        totalTechCost   = (totals["total_tech_cost"] as? Number)?.toDouble() ?: 0.0,
                        totalCompanyProfit = (totals["total_company_profit"] as? Number)?.toDouble() ?: 0.0,
                        totalJobs       = (totals["total_jobs"] as? Number)?.toInt() ?: 0,
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

    fun loadTechReport(userId: String, period: String, from: String = "", to: String = "") {
        viewModelScope.launch {
            _state.update { it.copy(techReportLoading = true) }
            val params = buildPeriodParams(period, from, to)
            when (val r = repo.getTechReport(userId, params)) {
                is Result.Success -> _state.update { it.copy(techReportLoading = false, selectedTechReport = r.data) }
                is Result.Error   -> _state.update { it.copy(techReportLoading = false, error = r.message) }
            }
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
                    val n = (r.data["count"] as? Number)?.toInt() ?: 0
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
        hourly_rate    = (m["hourly_rate"] as? Number)?.toDouble() ?: 0.0,
        commission_pct = (m["commission_pct"] as? Number)?.toDouble() ?: 0.0,
        jobs_count     = (m["jobs_count"] as? Number)?.toInt() ?: 0,
        total_sales    = (m["total_sales"] as? Number)?.toDouble() ?: 0.0,
        total_material = (m["total_material"] as? Number)?.toDouble() ?: 0.0,
        gross_earnings = (m["gross_earnings"] as? Number)?.toDouble() ?: 0.0,
        total_hours    = (m["total_hours"] as? Number)?.toDouble() ?: 0.0,
        pending_bonuses = (m["pending_bonuses"] as? Number)?.toDouble() ?: 0.0,
        pending_deductions = (m["pending_deductions"] as? Number)?.toDouble() ?: 0.0,
        balance_owed   = (m["balance_owed"] as? Number)?.toDouble() ?: 0.0
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
        total_sale     = (m["total_sale"] as? Number)?.toDouble() ?: 0.0,
        material_cost  = (m["material_cost"] as? Number)?.toDouble() ?: 0.0,
        net_profit     = (m["net_profit"] as? Number)?.toDouble() ?: 0.0,
        source_cost    = (m["source_cost"] as? Number)?.toDouble() ?: 0.0,
        tech_profit    = (m["tech_profit"] as? Number)?.toDouble() ?: 0.0,
        company_profit = (m["company_profit"] as? Number)?.toDouble() ?: 0.0,
        hours_worked   = (m["hours_worked"] as? Number)?.toDouble() ?: 0.0,
        pay_type       = m["pay_type"]?.toString() ?: "commission",
        earning_paid   = m["earning_paid"] as? Boolean ?: false,
        invoice_number = m["invoice_number"]?.toString()
    )
}

// ── Main Payroll Screen ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayrollScreen(
    onTechDetail:     (String) -> Unit,
    onTechSettings:   (String) -> Unit,
    onReimbursements: () -> Unit,
    onSimulator:      () -> Unit,
    onBack:           () -> Unit,
    vm: PayrollViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var tab       by remember { mutableIntStateOf(0) }
    var period    by remember { mutableStateOf("week") }
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
                    IconButton(onClick = { showMarkPaid = true }) { Icon(Icons.Default.Paid, "Mark range paid", tint = AppColors.Green) }
                    IconButton(onClick = { vm.loadSummary(period) }) { Icon(Icons.Default.Refresh, null) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Quick-access row ────────────────────────────────────────
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSimulator, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.Calculate, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Simulator", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onReimbursements, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.Receipt, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reimburse", style = MaterialTheme.typography.bodySmall)
                }
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
                0 -> OverviewTab(state, onTechDetail, onTechSettings, vm)
                1 -> JobReportTab(state, vm, period)
                2 -> ByTechTab(state, onTechDetail, onTechSettings, vm, period)
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
private fun OverviewTab(state: PayrollState, onTechDetail: (String) -> Unit, onTechSettings: (String) -> Unit, vm: PayrollViewModel) {
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
                TechPayCard(tech = tech, onDetail = { onTechDetail(tech.id) }, onSettings = { onTechSettings(tech.id) }, vm = vm)
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
                OutlinedButton(onClick = onDetail, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.OpenInNew, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Full Report", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { showBonus = true }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Bonus", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { showDeduct = true }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.Remove, null, Modifier.size(14.dp), tint = AppColors.Red)
                    Spacer(Modifier.width(4.dp))
                    Text("Deduct", fontSize = 12.sp, color = AppColors.Red)
                }
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
                        MiniStat("Sales",    formatMoney((totals["total_sales"] as? Number)?.toDouble() ?: 0.0), AppColors.Blue)
                        MiniStat("Material", formatMoney((totals["total_material"] as? Number)?.toDouble() ?: 0.0), AppColors.Slate)
                        MiniStat("Source",   formatMoney((totals["total_source_cost"] as? Number)?.toDouble() ?: 0.0), AppColors.Orange)
                        MiniStat("Tech",     formatMoney((totals["total_tech_profit"] as? Number)?.toDouble() ?: 0.0), AppColors.Purple)
                        MiniStat("Company",  formatMoney((totals["total_company_profit"] as? Number)?.toDouble() ?: 0.0), AppColors.Green)
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
                    Text(job.job_date, style = MaterialTheme.typography.labelSmall,
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
private fun ByTechTab(state: PayrollState, onTechDetail: (String) -> Unit, onTechSettings: (String) -> Unit, vm: PayrollViewModel, period: String) {
    if (state.techs.isEmpty()) { EmptyView("No technicians", Icons.Default.People); return }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Tap a technician to see their full report", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.techs, key = { it.id }) { tech ->
            val color = try { Color(android.graphics.Color.parseColor(tech.color)) } catch (e: Exception) { AppColors.Blue }
            val summary = state.techSummaries.find { it.id == tech.id }
            Card(onClick = { onTechDetail(tech.id) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarCircle(tech.initials, color, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tech.fullName, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (tech.hourly_rate > 0 && tech.commission_pct == 0.0)
                                "Hourly @ ${formatMoney(tech.hourly_rate)}/hr"
                            else
                                "Commission ${tech.commission_pct.toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        summary?.let {
                            Text("${it.jobs_count} jobs this period",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    summary?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatMoney(it.balance_owed), fontWeight = FontWeight.Bold,
                                color = if (it.balance_owed > 0) AppColors.Green else AppColors.Slate)
                            Text("owed", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Tech Detail Report Screen ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechReportScreen(
    userId: String,
    onBack: () -> Unit,
    vm: PayrollViewModel = hiltViewModel(),
    tsVm: TimesheetViewModel = hiltViewModel()
) {
    val state    by vm.state.collectAsState()
    val tsReport by tsVm.report.collectAsState()
    var period   by remember { mutableStateOf("week") }

    LaunchedEffect(userId) { vm.loadTechReport(userId, period) }

    // Load timesheet data for this tech whenever userId or period changes
    LaunchedEffect(userId, period) {
        val (start, end) = payrollPeriodToDates(period)
        tsVm.loadReport(start, end, userId)
    }

    @Suppress("UNCHECKED_CAST")
    val report  = state.selectedTechReport
    @Suppress("UNCHECKED_CAST")
    val tech    = report["tech"] as? Map<String, Any>
    @Suppress("UNCHECKED_CAST")
    val summary = report["summary"] as? Map<String, Any>
    @Suppress("UNCHECKED_CAST")
    val jobList = (report["jobs"] as? List<Map<String, Any>>) ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    val bonuses = (report["bonuses"] as? List<Map<String, Any>>) ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    val deducts = (report["deductions"] as? List<Map<String, Any>>) ?: emptyList()
    val allTimeBalance = (report["all_time_balance"] as? Number)?.toDouble() ?: 0.0
    val techName = tech?.let { "${it["first_name"]} ${it["last_name"]}" } ?: "Technician"

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(techName, fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = {
                listOf("today" to "Day", "week" to "Wk", "month" to "Mo").forEach { (key, label) ->
                    FilterChip(selected = period == key, onClick = {
                        period = key
                        vm.loadTechReport(userId, period)
                        val (start, end) = payrollPeriodToDates(key)
                        tsVm.loadReport(start, end, userId)
                    }, label = { Text(label, fontSize = 12.sp) })
                }
            }
        )
    }) { padding ->
        if (state.techReportLoading) { LoadingView(); return@Scaffold }
        if (report.isEmpty()) { LoadingView(); return@Scaffold }

        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── All-time balance banner ──────────────────────────────────
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (allTimeBalance > 0) AppColors.Green.copy(0.1f) else AppColors.Slate.copy(0.1f)
                    )) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, null,
                            tint = if (allTimeBalance > 0) AppColors.Green else AppColors.Slate,
                            modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Total Balance Owed (All Time)", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatMoney(allTimeBalance), fontSize = 26.sp, fontWeight = FontWeight.Bold,
                                color = if (allTimeBalance > 0) AppColors.Green else AppColors.Slate)
                        }
                    }
                }
            }

            // ── Period summary ───────────────────────────────────────────
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Period Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FinStat("Jobs",       "${(summary?.get("jobs_count") as? Number)?.toInt() ?: 0}", AppColors.Blue)
                            FinStat("Total Sales", formatMoney((summary?.get("total_sales") as? Number)?.toDouble() ?: 0.0), AppColors.Blue)
                            FinStat("Hours",      String.format("%.1f", (summary?.get("total_hours") as? Number)?.toDouble() ?: 0.0), AppColors.Slate)
                        }
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FinStat("Gross Earnings", formatMoney((summary?.get("gross_earnings") as? Number)?.toDouble() ?: 0.0), AppColors.Purple)
                            FinStat("+ Bonuses",      "+${formatMoney((summary?.get("bonus_total") as? Number)?.toDouble() ?: 0.0)}", AppColors.Green)
                            FinStat("- Deductions",   "-${formatMoney((summary?.get("deduction_total") as? Number)?.toDouble() ?: 0.0)}", AppColors.Red)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Net Payout This Period", fontWeight = FontWeight.Bold)
                            Text(formatMoney((summary?.get("net_payout") as? Number)?.toDouble() ?: 0.0),
                                fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppColors.Green)
                        }
                        // Timesheet hours for this period
                        val tsTotalMins = tsReport?.summary?.firstOrNull()?.totalMinutes ?: 0
                        if (tsTotalMins > 0) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessTime, null,
                                        tint = AppColors.Blue,
                                        modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Hours Worked", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "${tsTotalMins / 60}h ${tsTotalMins % 60}m",
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.Blue,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // ── Job breakdown ────────────────────────────────────────────
            if (jobList.isNotEmpty()) {
                item { Text("Jobs This Period", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(jobList) { j ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(j["job_number"]?.toString() ?: "", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(j["job_title"]?.toString() ?: "", fontWeight = FontWeight.SemiBold)
                                    Text(j["customer_name"]?.toString() ?: "", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val addr = listOf(j["address"], j["city"]).filterNotNull()
                                        .filter { it.toString().isNotBlank() }.joinToString(", ")
                                    if (addr.isNotBlank()) Text(addr, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(j["job_date"]?.toString()?.take(10) ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Text(formatMoney((j["total_sale"] as? Number)?.toDouble() ?: 0.0),
                                        fontWeight = FontWeight.SemiBold, color = AppColors.Blue)
                                    Text("Profit: ${formatMoney((j["profit"] as? Number)?.toDouble() ?: 0.0)}",
                                        style = MaterialTheme.typography.bodySmall, color = AppColors.Purple)
                                    j["job_source"]?.let { StatusBadge(it.toString(), AppColors.Accent, small = true) }
                                }
                            }
                            val commPct = (j["commission_pct"] as? Number)?.toDouble()
                            val resolvedPct = (j["resolved_commission_pct"] as? Number)?.toDouble()
                            if (commPct != null && commPct > 0) {
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${commPct.toInt()}% commission",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (resolvedPct != null) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AppColors.Purple.copy(alpha = 0.12f)
                                        ) {
                                            Text("Source rule", style = MaterialTheme.typography.labelSmall,
                                                color = AppColors.Purple,
                                                modifier = androidx.compose.ui.Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                            if ((j["pay_type"]?.toString() == "hourly") && (j["hours_worked"] as? Number)?.toDouble() ?: 0.0 > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text("${String.format("%.2f", (j["hours_worked"] as? Number)?.toDouble() ?: 0.0)} hours",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // ── Bonuses ──────────────────────────────────────────────────
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
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("+${formatMoney((b["amount"] as? Number)?.toDouble() ?: 0.0)}",
                                fontWeight = FontWeight.Bold, color = AppColors.Green)
                        }
                    }
                }
            }

            // ── Deductions ───────────────────────────────────────────────
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
                            Text("-${formatMoney((d["amount"] as? Number)?.toDouble() ?: 0.0)}",
                                fontWeight = FontWeight.Bold, color = AppColors.Red)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
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
        "week"  -> {
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
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
