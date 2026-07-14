package com.ultimatepro.ui.payroll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.*
import com.ultimatepro.domain.model.User
import com.ultimatepro.ui.common.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.foundation.text.KeyboardOptions

data class SimulatorState(
    val loading: Boolean = false,
    val techs: List<User> = emptyList(),
    // Inputs
    val jobTotal:        String = "",
    val materialCost:    String = "",
    val matSellPrice:    String = "",
    val materialPaidBy:  String = "company",
    val selectedTechId:  String = "",
    val jobSource:       String = "",
    // Result
    val result: Map<String, Any>? = null,
    val error: String? = null
)

@HiltViewModel
class SimulatorViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {
    private val _s = MutableStateFlow(SimulatorState())
    val state: StateFlow<SimulatorState> = _s.asStateFlow()

    init {
        viewModelScope.launch {
            val r = repo.getTechnicians()
            _s.update { it.copy(techs = (r as? Result.Success)?.data ?: emptyList()) }
        }
    }

    fun update(block: SimulatorState.() -> SimulatorState) { _s.update(block) }

    fun simulate() {
        viewModelScope.launch {
            val s = _s.value
            _s.update { it.copy(loading = true, result = null) }
            val body: Map<String, Any?> = mapOf(
                "job_total"            to s.jobTotal.toDoubleOrNull(),
                "material_cost"        to s.materialCost.toDoubleOrNull(),
                "material_sell_price"  to s.matSellPrice.toDoubleOrNull(),
                "material_paid_by"     to s.materialPaidBy,
                "tech_id"              to s.selectedTechId.ifBlank { null },
                "job_source"           to s.jobSource.ifBlank { null }
            )
            when (val r = repo.simulateProfit(body)) {
                is Result.Success -> {
                    @Suppress("UNCHECKED_CAST")
                    val breakdown = r.data["breakdown"] as? Map<String, Any>
                    _s.update { it.copy(loading = false, result = breakdown) }
                }
                is Result.Error -> _s.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun clearError() { _s.update { it.copy(error = null) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfitSimulatorScreen(
    onBack: () -> Unit,
    vm: SimulatorViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        if (state.error != null) { snack.showSnackbar(state.error!!); vm.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Profit Simulator", fontWeight = FontWeight.Bold)
                        Text("Preview the split before a job is completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Job amount ───────────────────────────────────────────────
            OutlinedTextField(
                value = state.jobTotal,
                onValueChange = { vm.update { copy(jobTotal = it) } },
                label = { Text("Job Total ($)") },
                leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                placeholder = { Text("e.g. 1000") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            // ── Material fields ──────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.materialCost,
                    onValueChange = { vm.update { copy(materialCost = it) } },
                    label = { Text("Material Cost ($)") },
                    singleLine = true, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = state.matSellPrice,
                    onValueChange = { vm.update { copy(matSellPrice = it) } },
                    label = { Text("Sold At ($)") },
                    singleLine = true, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            Text("Cost = what was paid. Sold At = what the customer was charged. Leave equal if no markup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // ── Who paid for materials ────────────────────────────────────
            SectionLabel("WHO PAID FOR MATERIALS?")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("company" to "Company", "tech" to "Technician").forEach { (val_, label) ->
                    FilterChip(
                        selected = state.materialPaidBy == val_,
                        onClick  = { vm.update { copy(materialPaidBy = val_) } },
                        label    = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Technician ───────────────────────────────────────────────
            SectionLabel("TECHNICIAN (optional)")
            if (state.techs.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = state.techs.find { it.id == state.selectedTechId }?.fullName ?: "Select technician",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Technician") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("No technician") },
                            onClick = { vm.update { copy(selectedTechId = "") }; expanded = false })
                        state.techs.forEach { tech ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(tech.fullName, fontWeight = FontWeight.Medium)
                                        Text(
                                            if (tech.hourly_rate > 0) "Hourly @ ${formatMoney(tech.hourly_rate)}/hr"
                                            else "Commission ${tech.commission_pct.toInt()}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = { vm.update { copy(selectedTechId = tech.id) }; expanded = false }
                            )
                        }
                    }
                }
            }

            // ── Job source ───────────────────────────────────────────────
            OutlinedTextField(
                value = state.jobSource,
                onValueChange = { vm.update { copy(jobSource = it) } },
                label = { Text("Job Source (optional)") },
                placeholder = { Text("e.g. google, yelp, referral") },
                leadingIcon = { Icon(Icons.Default.Source, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // ── Calculate button ─────────────────────────────────────────
            AppButton(
                onClick     = { vm.simulate() },
                label       = "Calculate Split",
                modifier    = Modifier.fillMaxWidth().height(52.dp),
                enabled     = state.jobTotal.toDoubleOrNull() != null && !state.loading,
                loading     = state.loading,
                leadingIcon = Icons.Default.Calculate
            )

            // ── Results ──────────────────────────────────────────────────
            AnimatedVisibility(visible = state.result != null) {
                state.result?.let { r ->
                    SimulatorResultCard(r)
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SimulatorResultCard(r: Map<String, Any>) {
    val scenario = r["scenario"]?.toString() ?: "unknown"

    val scenarioLabel = when (scenario) {
        "company_supplied"  -> "Company Supplies Materials"
        "tech_reimbursed"   -> "Tech Gets Reimbursed"
        "tech_keeps_markup" -> "Tech Keeps Markup"
        "sub_gross_pct"     -> "Subcontractor"
        else                -> scenario
    }
    val scenarioColor = when (scenario) {
        "company_supplied"  -> AppColors.Blue
        "tech_reimbursed"   -> AppColors.Orange
        "tech_keeps_markup" -> AppColors.Purple
        "sub_gross_pct"     -> AppColors.Slate
        else                -> AppColors.Blue
    }

    fun d(key: String): Double = when (val v = r[key]) { is Number -> v.toDouble(); is String -> v.toDoubleOrNull() ?: 0.0; else -> 0.0 }  // P2.23 string-or-number

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, scenarioColor.copy(alpha = 0.4f))) {
        Column(Modifier.padding(16.dp)) {

            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(scenarioColor))
                Spacer(Modifier.width(8.dp))
                Text(scenarioLabel, fontWeight = FontWeight.Bold, color = scenarioColor)
            }

            r["description"]?.toString()?.let { desc ->
                Spacer(Modifier.height(6.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Big 3 tiles
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ResultTile("Tech Gets",    formatMoney(d("total_tech_payout")), AppColors.Purple)
                ResultTile("Company Gets", formatMoney(d("company_profit")),    AppColors.Green)
                ResultTile("Source Cost",  formatMoney(d("source_cost")),       AppColors.Orange)
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            // Full breakdown
            val rows = buildList {
                add("Gross Job Total"    to d("gross_job_total"))
                if (d("material_cost") > 0) add("Material Cost"    to -d("material_cost"))
                if (scenario == "tech_keeps_markup" && d("markup_kept") > 0)
                    add("Markup Kept by Tech" to d("markup_kept"))
                if (scenario != "sub_gross_pct") add("Net Profit" to d("net_profit"))
                if (d("source_cost") > 0)  add("Source Cost"  to -d("source_cost"))
                add("Tech Commission"   to d("tech_profit"))
                if (d("tech_reimbursement") > 0)
                    add("+ Reimbursement" to d("tech_reimbursement"))
                add("Company Profit"    to d("company_profit"))
            }

            rows.forEach { (label, value) ->
                val isSummary = label in listOf("Company Profit", "Net Profit")
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label,
                        style = if (isSummary) MaterialTheme.typography.bodyMedium
                                else MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSummary) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        (if (value < 0) "-" else "") + formatMoney(Math.abs(value)),
                        style = if (isSummary) MaterialTheme.typography.bodyMedium
                                else MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSummary) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            label == "Company Profit"      -> AppColors.Green
                            label.contains("Tech")         -> AppColors.Purple
                            label.contains("Reimbursement")-> AppColors.Orange
                            value < 0                      -> AppColors.Red
                            else                           -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            r["rule_name"]?.let {
                Spacer(Modifier.height(8.dp))
                Text("Rule applied: $it", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ResultTile(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
