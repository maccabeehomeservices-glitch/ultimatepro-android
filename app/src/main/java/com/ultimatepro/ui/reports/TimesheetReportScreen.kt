package com.ultimatepro.ui.reports

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultimatepro.domain.model.TimesheetSummary
import com.ultimatepro.domain.model.Timesheet
import com.ultimatepro.ui.common.AppButton
import com.ultimatepro.ui.common.AppColors
import com.ultimatepro.ui.common.CRMCard
import com.ultimatepro.ui.common.LoadingView
import com.ultimatepro.ui.common.SectionLabel
import com.ultimatepro.ui.dashboard.TimesheetViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimesheetReportScreen(
    onBack: () -> Unit,
    vm: TimesheetViewModel = hiltViewModel()
) {
    val report        by vm.report.collectAsState()
    val reportLoading by vm.reportLoading.collectAsState()
    val techs         by vm.techs.collectAsState()
    val msg           by vm.message.collectAsState()
    val snack         = remember { SnackbarHostState() }
    val context       = LocalContext.current

    LaunchedEffect(Unit) { vm.loadTechs() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.clearMsg() } }

    // Default: current month
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val cal = remember { Calendar.getInstance() }
    val todayStr = remember { sdf.format(cal.time) }
    val monthStartStr = remember {
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val s = sdf.format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, Calendar.getInstance().get(Calendar.DAY_OF_MONTH))
        s
    }

    var startDate by remember { mutableStateOf(monthStartStr) }
    var endDate   by remember { mutableStateOf(todayStr) }
    var selectedTechId   by remember { mutableStateOf<String?>(null) }
    var selectedTechName by remember { mutableStateOf("All Technicians") }
    var showTechDropdown by remember { mutableStateOf(false) }
    var showStartPicker  by remember { mutableStateOf(false) }
    var showEndPicker    by remember { mutableStateOf(false) }

    // DatePickerDialog — Start Date
    if (showStartPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                sdf.parse(startDate)?.time
            }.getOrNull()
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { millis ->
                        startDate = sdf.format(Date(millis))
                    }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    // DatePickerDialog — End Date
    if (showEndPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                sdf.parse(endDate)?.time
            }.getOrNull()
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { millis ->
                        endDate = sdf.format(Date(millis))
                    }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Timesheet Report", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    report?.let { r ->
                        IconButton(onClick = {
                            val sb = StringBuilder("Timesheet Report: $startDate – $endDate\n\n")
                            sb.append("SUMMARY\n")
                            r.summary.forEach { s ->
                                sb.append("${s.techName}: ${formatMins(s.totalMinutes)}, ${s.daysWorked} days\n")
                            }
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, sb.toString())
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Timesheet Report")
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share report"))
                        }) { Icon(Icons.Default.Share, "Share") }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Filter row ────────────────────────────────────────────────
            item {
                SectionLabel("DATE RANGE")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        onClick     = { showStartPicker = true },
                        label       = startDate,
                        modifier    = Modifier.weight(1f),
                        leadingIcon = Icons.Default.CalendarToday
                    )
                    Text("–", modifier = Modifier.align(Alignment.CenterVertically))
                    AppButton(
                        onClick     = { showEndPicker = true },
                        label       = endDate,
                        modifier    = Modifier.weight(1f),
                        leadingIcon = Icons.Default.CalendarToday
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Tech filter
                if (techs.isNotEmpty()) {
                    SectionLabel("TECHNICIAN FILTER")
                    Box {
                        OutlinedButton(
                            onClick   = { showTechDropdown = true },
                            modifier  = Modifier.fillMaxWidth(),
                            shape     = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Person, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(selectedTechName, Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(
                            expanded        = showTechDropdown,
                            onDismissRequest = { showTechDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text    = { Text("All Technicians") },
                                onClick = {
                                    selectedTechId   = null
                                    selectedTechName = "All Technicians"
                                    showTechDropdown = false
                                }
                            )
                            techs.forEach { tech ->
                                DropdownMenuItem(
                                    text    = { Text(tech.fullName) },
                                    onClick = {
                                        selectedTechId   = tech.id
                                        selectedTechName = tech.fullName
                                        showTechDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                // Run button
                AppButton(
                    onClick     = { vm.loadReport(startDate, endDate, selectedTechId) },
                    label       = "Run Report",
                    modifier    = Modifier.fillMaxWidth().height(48.dp),
                    enabled     = !reportLoading,
                    loading     = reportLoading,
                    leadingIcon = Icons.Default.Assessment
                )
            }

            // ── Summary cards ─────────────────────────────────────────────
            report?.summary?.let { summaries ->
                if (summaries.isNotEmpty()) {
                    item { SectionLabel("SUMMARY") }
                    items(summaries) { s -> TimesheetSummaryCard(s) }
                }
            }

            // ── Detail list ───────────────────────────────────────────────
            report?.timesheets?.let { sheets ->
                if (sheets.isNotEmpty()) {
                    item { SectionLabel("DETAILS") }
                    items(sheets) { ts -> TimesheetDetailRow(ts) }
                }
            }

            if (report?.timesheets?.isEmpty() == true) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No timesheets found for this range.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun TimesheetSummaryCard(s: TimesheetSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = AppColors.Blue.copy(alpha = 0.08f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, null, tint = AppColors.Blue, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.techName, fontWeight = FontWeight.SemiBold)
                Text("${s.daysWorked} day${if (s.daysWorked != 1) "s" else ""} worked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMins(s.totalMinutes), fontWeight = FontWeight.Bold, color = AppColors.Blue, fontSize = 16.sp)
                Text("total hours", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TimesheetDetailRow(ts: Timesheet) {
    CRMCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ts.techName?.let {
                    Text(it, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }
                Text(ts.date.take(10), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                val inTime  = ts.clockInAt.take(16).replace('T', ' ')
                val outTime = ts.clockOutAt?.take(16)?.replace('T', ' ') ?: "—"
                Text("In:  $inTime", style = MaterialTheme.typography.labelSmall)
                Text("Out: $outTime", style = MaterialTheme.typography.labelSmall)
                ts.totalMinutes?.let {
                    Text(formatMins(it), fontWeight = FontWeight.Bold, color = AppColors.Green,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun formatMins(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
