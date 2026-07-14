package com.ultimatepro.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.QboStatus
import com.ultimatepro.domain.model.QboSyncResult
import com.ultimatepro.ui.common.AppButton
import com.ultimatepro.ui.common.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class QuickBooksViewModel @Inject constructor(
    private val repo: CrmRepository
) : ViewModel() {

    private val _status    = MutableStateFlow<QboStatus?>(null)
    val status = _status.asStateFlow()

    private val _loading   = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _syncing   = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()

    private val _syncResults = MutableStateFlow<Map<String, QboSyncResult>?>(null)
    val syncResults = _syncResults.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val _connectUrl = MutableSharedFlow<String>()
    val connectUrl = _connectUrl.asSharedFlow()

    fun load() = viewModelScope.launch {
        _loading.value = true
        when (val r = repo.getQboStatus()) {
            is Result.Success -> _status.value = r.data
            is Result.Error   -> _events.emit(r.message)
        }
        _loading.value = false
    }

    fun connect() = viewModelScope.launch {
        _loading.value = true
        when (val r = repo.getQboConnectUrl()) {
            is Result.Success -> _connectUrl.emit(r.data.url)
            is Result.Error   -> _events.emit(r.message)
        }
        _loading.value = false
    }

    fun syncAll() = viewModelScope.launch {
        _syncing.value = true
        _syncResults.value = null
        when (val r = repo.syncQboAll()) {
            is Result.Success -> {
                _syncResults.value = r.data
                _events.emit("Sync complete")
            }
            is Result.Error -> _events.emit(r.message)
        }
        _syncing.value = false
    }

    fun syncCustomers() = viewModelScope.launch {
        _syncing.value = true
        when (val r = repo.syncQboCustomers()) {
            is Result.Success -> _events.emit("Customers: ${r.data.synced} synced, ${r.data.errors} errors")
            is Result.Error   -> _events.emit(r.message)
        }
        _syncing.value = false
    }

    fun syncInvoices() = viewModelScope.launch {
        _syncing.value = true
        when (val r = repo.syncQboInvoices()) {
            is Result.Success -> _events.emit("Invoices: ${r.data.synced} synced, ${r.data.errors} errors")
            is Result.Error   -> _events.emit(r.message)
        }
        _syncing.value = false
    }

    fun syncPayments() = viewModelScope.launch {
        _syncing.value = true
        when (val r = repo.syncQboPayments()) {
            is Result.Success -> _events.emit("Payments: ${r.data.synced} synced, ${r.data.errors} errors")
            is Result.Error   -> _events.emit(r.message)
        }
        _syncing.value = false
    }

    fun disconnect() = viewModelScope.launch {
        _loading.value = true
        when (val r = repo.disconnectQbo()) {
            is Result.Success -> {
                _status.value = QboStatus(connected = false)
                _syncResults.value = null
                _events.emit("QuickBooks disconnected")
            }
            is Result.Error -> _events.emit(r.message)
        }
        _loading.value = false
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickBooksScreen(
    onBack: () -> Unit,
    vm: QuickBooksViewModel = hiltViewModel()
) {
    val context     = LocalContext.current
    val status      by vm.status.collectAsState()
    val loading     by vm.loading.collectAsState()
    val syncing     by vm.syncing.collectAsState()
    val syncResults by vm.syncResults.collectAsState()
    val snackState  = remember { SnackbarHostState() }
    var showDisconnectDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    LaunchedEffect(Unit) {
        vm.events.collect { msg -> snackState.showSnackbar(msg) }
    }

    LaunchedEffect(Unit) {
        vm.connectUrl.collect { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Integrations", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackState) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // QuickBooks Online card
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    // Header row
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            Modifier.size(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2CA01C)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("QB", color = Color.White, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("QuickBooks Online", fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                                if (loading) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else if (status?.connected == true) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFFE8F5E9)
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CheckCircle, null,
                                                Modifier.size(12.dp), tint = AppColors.Green)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Connected",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppColors.Green)
                                        }
                                    }
                                }
                            }
                            Text(
                                if (status?.connected == true && status?.company_name != null)
                                    "Connected to ${status?.company_name}"
                                else "Sync customers, invoices, and payments",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (status?.connected == true) {
                        // Sync All button
                        AppButton(
                            onClick     = { vm.syncAll() },
                            label       = if (syncing) "Syncing..." else "Sync All to QuickBooks",
                            modifier    = Modifier.fillMaxWidth().height(48.dp),
                            enabled     = !syncing && !loading,
                            loading     = syncing,
                            leadingIcon = Icons.Default.Sync
                        )

                        // Sync results
                        syncResults?.let { results ->
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Last Sync Results",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold)
                                    results.forEach { (key, result) ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(key.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.bodySmall)
                                            Text("${result.synced} synced${if (result.errors > 0) " · ${result.errors} errors" else ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (result.errors > 0) MaterialTheme.colorScheme.error
                                                        else AppColors.Green)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Individual sync row
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "Customers" to { vm.syncCustomers() },
                                "Invoices"  to { vm.syncInvoices() },
                                "Payments"  to { vm.syncPayments() },
                            ).forEach { (label, action) ->
                                AppButton(
                                    onClick  = { action() },
                                    label    = label,
                                    modifier = Modifier.weight(1f),
                                    enabled  = !syncing && !loading
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Disconnect button
                        AppButton(
                            onClick  = { showDisconnectDialog = true },
                            label    = "Disconnect QuickBooks",
                            modifier = Modifier.fillMaxWidth(),
                            enabled  = !loading && !syncing,
                            labelColor = AppColors.Red,
                            leadingIcon = Icons.Default.LinkOff
                        )
                    } else {
                        // Connect button
                        Button(
                            onClick  = { vm.connect() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled  = !loading,
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2CA01C))
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.Link, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (loading) "Connecting..." else "Connect QuickBooks Online")
                        }
                    }
                }
            }

            // Stripe placeholder
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        Modifier.size(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF6772E5).copy(alpha = 0.3f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Str", color = Color(0xFF6772E5), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Stripe", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        Text("Coming soon — online payment processing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }

    // Disconnect confirmation dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title   = { Text("Disconnect QuickBooks?") },
            text    = { Text("Your data will remain in QuickBooks but will no longer sync. You can reconnect at any time.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.disconnect(); showDisconnectDialog = false },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) { Text("Cancel") }
            }
        )
    }
}
