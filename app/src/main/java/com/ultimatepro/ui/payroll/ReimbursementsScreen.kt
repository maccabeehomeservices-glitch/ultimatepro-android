package com.ultimatepro.ui.payroll

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.*
import com.ultimatepro.ui.common.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReimbursementItem(
    val id: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val status: String = "pending",
    val job_number: String? = null,
    val job_title: String? = null,
    val job_date: String? = null,
    val first_name: String = "",
    val last_name: String = "",
    val color: String = "#1565C0",
    val receipt_url: String? = null,
    val created_at: String? = null
) {
    val techName get() = "$first_name $last_name".trim()
    val initials get() = "${first_name.take(1)}${last_name.take(1)}".uppercase()
}

data class ReimbState(
    val loading: Boolean = true,
    val items: List<ReimbursementItem> = emptyList(),
    val total_pending: Double = 0.0,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class ReimbursementsViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {
    private val _s = MutableStateFlow(ReimbState())
    val state: StateFlow<ReimbState> = _s.asStateFlow()

    init { load() }

    fun load(status: String? = null) {
        viewModelScope.launch {
            _s.update { it.copy(loading = true) }
            when (val r = repo.getReimbursements(status = status)) {
                is Result.Success -> {
                    val items = r.data.map { m -> parseItem(m) }
                    val pending = items.filter { it.status == "pending" || it.status == "approved" }
                        .sumOf { it.amount }
                    _s.update { it.copy(loading = false, items = items, total_pending = pending) }
                }
                is Result.Error -> _s.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun approve(id: String) {
        viewModelScope.launch {
            when (repo.approveReimbursement(id)) {
                is Result.Success -> { _s.update { it.copy(message = "Approved") }; load() }
                is Result.Error   -> _s.update { it.copy(error = "Failed to approve") }
            }
        }
    }

    fun markPaid(id: String) {
        viewModelScope.launch {
            when (repo.payReimbursement(id)) {
                is Result.Success -> { _s.update { it.copy(message = "Marked as paid") }; load() }
                is Result.Error   -> _s.update { it.copy(error = "Failed to mark paid") }
            }
        }
    }

    fun clearMessages() { _s.update { it.copy(error = null, message = null) } }

    @Suppress("UNCHECKED_CAST")
    private fun parseItem(m: Map<String, Any>) = ReimbursementItem(
        id          = m["id"]?.toString() ?: "",
        amount      = (m["amount"] as? Number)?.toDouble() ?: (m["amount"] as? String)?.toDoubleOrNull() ?: 0.0,
        description = m["description"]?.toString() ?: "",
        status      = m["status"]?.toString() ?: "pending",
        job_number  = m["job_number"]?.toString(),
        job_title   = m["job_title"]?.toString(),
        job_date    = m["job_date"]?.toString()?.take(10),
        first_name  = m["first_name"]?.toString() ?: "",
        last_name   = m["last_name"]?.toString() ?: "",
        color       = m["color"]?.toString() ?: "#1565C0",
        receipt_url = m["receipt_url"]?.toString(),
        created_at  = m["created_at"]?.toString()?.take(10)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReimbursementsScreen(
    onBack: () -> Unit,
    vm: ReimbursementsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    var tab   by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.message) { if (state.message != null) { snack.showSnackbar(state.message!!); vm.clearMessages() } }
    LaunchedEffect(state.error)   { if (state.error   != null) { snack.showSnackbar(state.error!!);   vm.clearMessages() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Column {
            TopAppBar(
                title = { Text("Material Reimbursements", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = { vm.load() }) { Icon(androidx.compose.ui.res.painterResource(com.ultimatepro.R.drawable.up_refresh), null) } }
            )
            ShineHairline()
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Total pending banner
            if (state.total_pending > 0) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Orange.copy(alpha = 0.12f))
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PendingActions, null,
                            tint = AppColors.Orange, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Pending Reimbursements", fontWeight = FontWeight.SemiBold, color = AppColors.Orange)
                            Text("Total owed to technicians for materials they purchased",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatMoney(state.total_pending),
                            fontWeight = FontWeight.Bold, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
                            color = AppColors.Orange)
                    }
                }
            }

            TabRow(selectedTabIndex = tab) {
                listOf("Pending", "Approved", "Paid").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }

            val statusMap = listOf("pending", "approved", "paid")
            val filtered  = state.items.filter { it.status == statusMap[tab] }

            if (state.loading) {
                LoadingView()
            } else if (filtered.isEmpty()) {
                EmptyView(
                    "No ${listOf("pending","approved","paid")[tab]} reimbursements",
                    Icons.Default.Receipt
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        ReimbursementCard(
                            item      = item,
                            onApprove = { vm.approve(item.id) },
                            onPay     = { vm.markPaid(item.id) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ReimbursementCard(
    item: ReimbursementItem,
    onApprove: () -> Unit,
    onPay:     () -> Unit
) {
    val techColor = try { Color(android.graphics.Color.parseColor(item.color)) }
                   catch (e: Exception) { AppColors.Blue }

    val statusColor = when (item.status) {
        "paid"     -> AppColors.Green
        "approved" -> AppColors.Blue
        "rejected" -> AppColors.Red
        else       -> AppColors.Orange
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(item.initials, techColor, 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.techName, fontWeight = FontWeight.SemiBold)
                    item.job_number?.let {
                        Text("Job $it${if (item.job_date != null) " • ${item.job_date}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item.job_title?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatMoney(item.amount), fontWeight = FontWeight.Bold, color = AppColors.Orange)
                    StatusBadge(item.status.replaceFirstChar { it.uppercase() }, statusColor, small = true)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(item.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Receipt indicator
            if (item.receipt_url != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachFile, null,
                        Modifier.size(14.dp), tint = AppColors.Blue)
                    Spacer(Modifier.width(4.dp))
                    Text("Receipt attached", style = MaterialTheme.typography.labelSmall, color = AppColors.Blue)
                }
            }

            // Action buttons
            if (item.status == "pending" || item.status == "approved") {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.status == "pending") {
                        AppButton(
                            onClick = onApprove,
                            label = "Approve",
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Default.Check
                        )
                    }
                    if (item.status == "approved") {
                        AppButton(
                            onClick = onPay,
                            label = "Mark Paid",
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Default.Payments
                        )
                    }
                }
            }
        }
    }
}
