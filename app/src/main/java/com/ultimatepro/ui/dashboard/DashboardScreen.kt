package com.ultimatepro.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.ultimatepro.ui.common.*
import com.ultimatepro.ui.maps.MapViewModel
import com.ultimatepro.ui.maps.jobStatusHue
import com.ultimatepro.ui.memberships.MembershipViewModel
import java.util.Calendar
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onJobClick:      (String) -> Unit,
    onAllJobs:       () -> Unit,
    onLiveMap:       () -> Unit,
    onPhone:         () -> Unit,
    onPasteTicket:   (String) -> Unit = {},
    onCustomer:      (String) -> Unit = {},
    onNotifications: () -> Unit = {},
    vm:         DashboardViewModel  = hiltViewModel(),
    mapVm:      MapViewModel        = hiltViewModel(),
    membershipVm: MembershipViewModel = hiltViewModel(),
    tsVm:       TimesheetViewModel  = hiltViewModel()
) {
    val state     by vm.state.collectAsState()
    val mapJobs   by mapVm.jobs.collectAsState()
    val dueSoon   by membershipVm.dueSoon.collectAsState()
    val tsStatus  by tsVm.timesheetStatus.collectAsState()
    val tsMsg     by tsVm.message.collectAsState()
    val clipboard =  LocalClipboardManager.current
    val density   = LocalDensity.current
    val snack     = remember { SnackbarHostState() }

    LaunchedEffect(tsMsg) { tsMsg?.let { snack.showSnackbar(it); tsVm.clearMsg() } }

    // Refresh map pins, due-soon memberships, and timesheet status when the screen resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mapVm.refresh()
                membershipVm.loadDueSoon()
                tsVm.loadStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Elapsed time counter: updates every 30 seconds while clocked in
    var elapsedMinutes by remember { mutableIntStateOf(0) }
    LaunchedEffect(tsStatus?.clockInAt, tsStatus?.clockedIn) {
        val clockInAt = tsStatus?.clockInAt
        if (tsStatus?.clockedIn == true && !clockInAt.isNullOrBlank()) {
            while (true) {
                elapsedMinutes = dashboardCalcElapsedMinutes(clockInAt)
                delay(30_000L)
            }
        } else {
            elapsedMinutes = 0
        }
    }

    var showClockOutDialog by remember { mutableStateOf(false) }
    var showClockInDialog  by remember { mutableStateOf(false) }

    // Notification unread count — poll every 30 s
    var notifUnread by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val res = vm.getNotificationUnreadCount()
                notifUnread = res
            } catch (_: Exception) {}
            delay(30_000L)
        }
    }

    val isClockedIn = tsStatus?.clockedIn == true
    val elapsedHrs  = elapsedMinutes / 60
    val elapsedMins = elapsedMinutes % 60
    val elapsedText = if (elapsedHrs > 0) "${elapsedHrs}h ${elapsedMins}m" else "${elapsedMins}m"

    val camState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(36.8529, -75.9780), 10f)
    }
    var mapReady      by remember { mutableStateOf(false) }
    var hasAutoZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(mapJobs, mapReady) {
        if (!mapReady || hasAutoZoomed || mapJobs.isEmpty()) return@LaunchedEffect
        hasAutoZoomed = true
        val paddingPx = with(density) { 60.dp.roundToPx() }
        if (mapJobs.size == 1) {
            camState.animate(CameraUpdateFactory.newLatLngZoom(mapJobs[0].latLng, 12f))
        } else {
            val builder = LatLngBounds.Builder()
            mapJobs.forEach { builder.include(it.latLng) }
            try {
                camState.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), paddingPx))
            } catch (_: Exception) {
                camState.animate(CameraUpdateFactory.newLatLngZoom(mapJobs[0].latLng, 11f))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${greeting()}, ${state.user?.first_name ?: "there"} 👋",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Here's your business today", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (notifUnread > 0) Badge { Text(if (notifUnread > 99) "99+" else notifUnread.toString()) }
                        }
                    ) {
                        IconButton(onClick = onNotifications) {
                            Icon(Icons.Default.Notifications, "Notifications")
                        }
                    }
                    IconButton(onClick = onLiveMap) { Icon(Icons.Default.Map, "Live Map") }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isClockedIn) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                if (isClockedIn) showClockOutDialog = true
                                else showClockInDialog = true
                            }
                    ) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = if (isClockedIn) "Clock Out" else "Clock In",
                            tint = if (isClockedIn) Color.White
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { vm.load() }) { Icon(Icons.Default.Refresh, "Refresh") }
                }
            )
        }
    ) { padding ->
        when {
            state.loading  -> LoadingView()
            state.error != null -> ErrorView(state.error!!, onRetry = { vm.load() })
            else -> {
                val r = state.report
                val secondChance = r?.second_chance?.new_count ?: 0

                Box(Modifier.fillMaxSize().padding(padding)) {
                    Column(Modifier.fillMaxSize()) {

                        // ── KPI row ───────────────────────────────────────
                        Spacer(Modifier.height(4.dp))
                        LazyRow(
                            Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item { KpiTile("Month Revenue", formatMoney(r?.revenue?.this_month ?: 0.0), Icons.Default.AttachMoney, AppColors.Blue, modifier = Modifier.width(150.dp)) }
                            item { KpiTile("Jobs Today",    "${r?.jobs?.total ?: 0}", Icons.Default.Work, AppColors.Purple, modifier = Modifier.width(130.dp)) }
                            item { KpiTile("Completed",     "${r?.jobs?.completed ?: 0}", Icons.Default.CheckCircle, AppColors.Green, modifier = Modifier.width(130.dp)) }
                            item { KpiTile("In Progress",   "${r?.jobs?.in_progress ?: 0}", Icons.Default.PlayArrow, AppColors.Orange, modifier = Modifier.width(130.dp)) }
                            item { KpiTile("Missed Calls",  "${r?.calls?.missed ?: 0}", Icons.Default.PhoneMissed, AppColors.Red, modifier = Modifier.width(130.dp)) }
                            item { KpiTile("2nd Chance",    "${r?.second_chance?.new_count ?: 0}", Icons.Default.Replay, AppColors.Gold, modifier = Modifier.width(140.dp)) }
                        }
                        Spacer(Modifier.height(8.dp))

                        // ── Second-chance banner ──────────────────────────
                        if (secondChance > 0) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                shape    = RoundedCornerShape(14.dp),
                                colors   = CardDefaults.cardColors(containerColor = AppColors.Gold.copy(alpha = 0.12f)),
                                onClick  = onPhone
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Replay, null, tint = AppColors.Gold, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("$secondChance unbooked calls need follow-up",
                                            fontWeight = FontWeight.SemiBold, color = AppColors.Gold,
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = AppColors.Gold)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        // ── Membership Due Soon ───────────────────────────
                        if (dueSoon.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                shape    = RoundedCornerShape(14.dp),
                                colors   = CardDefaults.cardColors(containerColor = AppColors.Purple.copy(alpha = 0.10f))
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Autorenew, null, tint = AppColors.Purple, modifier = Modifier.size(18.dp))
                                        Text(
                                            "${dueSoon.size} membership service${if (dueSoon.size != 1) "s" else ""} due soon",
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppColors.Purple,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    dueSoon.take(3).forEach { item ->
                                        Row(
                                            Modifier.fillMaxWidth().clickable { onCustomer(item.customerId) }.padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(item.customerName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                            Text(
                                                item.nextJobDate ?: "Due",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (dueSoon.size > 3) {
                                        Text("+${dueSoon.size - 3} more", style = MaterialTheme.typography.labelSmall, color = AppColors.Purple)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        // Clock in / out confirmation dialogs
                        if (showClockInDialog) {
                            AlertDialog(
                                onDismissRequest = { showClockInDialog = false },
                                title            = { Text("Clock In") },
                                text             = { Text("Clock In? This will start tracking your work hours.") },
                                confirmButton    = {
                                    TextButton(onClick = { tsVm.clockIn(); showClockInDialog = false }) {
                                        Text("Clock In", color = AppColors.Green)
                                    }
                                },
                                dismissButton    = {
                                    TextButton(onClick = { showClockInDialog = false }) { Text("Cancel") }
                                }
                            )
                        }
                        if (showClockOutDialog) {
                            AlertDialog(
                                onDismissRequest = { showClockOutDialog = false },
                                title            = { Text("Clock Out") },
                                text             = {
                                    Text("Clock out now? You've been working for $elapsedText.")
                                },
                                confirmButton    = {
                                    TextButton(onClick = { tsVm.clockOut(); showClockOutDialog = false }) {
                                        Text("Clock Out", color = AppColors.Red)
                                    }
                                },
                                dismissButton    = {
                                    TextButton(onClick = { showClockOutDialog = false }) { Text("Cancel") }
                                }
                            )
                        }

                        // ── Map — fills all remaining space ───────────────
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            GoogleMap(
                                modifier            = Modifier.fillMaxSize(),
                                cameraPositionState = camState,
                                properties          = MapProperties(mapType = MapType.NORMAL),
                                uiSettings          = MapUiSettings(
                                    zoomGesturesEnabled     = true,
                                    scrollGesturesEnabled   = false,
                                    rotationGesturesEnabled = false,
                                    tiltGesturesEnabled     = false,
                                    zoomControlsEnabled     = true,
                                    myLocationButtonEnabled = false
                                ),
                                onMapLoaded = { mapReady = true }
                            ) {
                                mapJobs.forEach { pin ->
                                    Marker(
                                        state   = rememberMarkerState(key = pin.job.id, position = pin.latLng),
                                        title   = pin.job.title,
                                        snippet = pin.job.customerName,
                                        icon    = BitmapDescriptorFactory.defaultMarker(jobStatusHue(pin.job.status)),
                                        onClick = { onJobClick(pin.job.id); false }
                                    )
                                }
                            }

                            // "View Full Map" — top-right overlay
                            TextButton(
                                onClick  = onLiveMap,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(Icons.Default.OpenInNew, null,
                                    modifier = Modifier.size(14.dp), tint = AppColors.Blue)
                                Spacer(Modifier.width(4.dp))
                                Text("Full Map", style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.Blue)
                            }

                            // Job count badge — bottom-left overlay
                            if (mapJobs.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                                    shape    = RoundedCornerShape(8.dp),
                                    color    = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                                ) {
                                    Text(
                                        "${mapJobs.size} active job${if (mapJobs.size != 1) "s" else ""}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style    = MaterialTheme.typography.labelSmall,
                                        color    = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // ── Paste Ticket FAB — overlaid above bottom nav ──────
                    ExtendedFloatingActionButton(
                        onClick        = { clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { onPasteTicket(it) } },
                        containerColor = AppColors.Blue,
                        contentColor   = Color.White,
                        modifier       = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Paste Ticket", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..11  -> "Good morning"
    in 12..16 -> "Good afternoon"
    else      -> "Good evening"
}

private fun dashboardCalcElapsedMinutes(clockInAt: String): Int {
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss.SSSSSS",
        "yyyy-MM-dd HH:mm:ss"
    )
    val clean = clockInAt.trimEnd('Z')
    for (fmt in formats) {
        try {
            val ms = SimpleDateFormat(fmt, Locale.US).parse(clean)?.time ?: continue
            return ((System.currentTimeMillis() - ms) / 60_000L).toInt().coerceAtLeast(0)
        } catch (_: Exception) { continue }
    }
    return 0
}
