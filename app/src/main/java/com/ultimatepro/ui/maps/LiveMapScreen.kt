package com.ultimatepro.ui.maps

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.gson.Gson
import androidx.compose.ui.platform.LocalDensity
import com.google.maps.android.compose.*
import com.ultimatepro.data.repository.*
import com.ultimatepro.domain.model.Job
import com.ultimatepro.domain.model.TechLiveLocation
import com.ultimatepro.ui.common.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

// Geocoded job ready to display on the map
data class JobMapPin(val job: Job, val latLng: LatLng)

// Map job status → BitmapDescriptorFactory hue matching AppColors.jobStatus().
// Canonical palette per skills/ui-design-system.md §1.
fun jobStatusHue(status: String): Float = when (status) {
    "scheduled"   -> BitmapDescriptorFactory.HUE_BLUE    // #2563EB
    "en_route"    -> BitmapDescriptorFactory.HUE_ORANGE  // #F97316
    "in_progress" -> BitmapDescriptorFactory.HUE_AZURE   // #0EA5E9 (sky)
    "holding"     -> BitmapDescriptorFactory.HUE_YELLOW  // #D97706 (amber)
    else          -> 200f                                 // Muted blue-gray for unscheduled / unknown
}

@HiltViewModel
class MapViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {

    private val _techs = MutableStateFlow<List<TechLiveLocation>>(emptyList())
    val techs = _techs.asStateFlow()

    private val _jobs = MutableStateFlow<List<JobMapPin>>(emptyList())
    val jobs = _jobs.asStateFlow()

    private val geocodeCache = HashMap<String, LatLng?>()
    private val httpClient = OkHttpClient()
    private val gson = Gson()

    init {
        // Poll live tech locations every 15 s
        viewModelScope.launch {
            while (true) {
                when (val r = repo.getLiveTechs()) {
                    is Result.Success -> _techs.value = r.data
                    else -> {}
                }
                delay(15_000)
            }
        }
        // Load active job pins once on open
        viewModelScope.launch { loadJobPins() }
    }

    fun refresh() {
        viewModelScope.launch { loadJobPins() }
    }

    private suspend fun loadJobPins() {
        val r = repo.getJobs(status = "scheduled,en_route,in_progress,unscheduled", page = 1)
        val activeJobs = when (r) {
            is Result.Success -> r.data.jobs.filter { it.isActive }
            else -> {
                Log.e("MapVM", "getJobs failed: ${(r as? Result.Error)?.message}")
                return
            }
        }
        Log.d("MapVM", "loadJobPins: ${activeJobs.size} active jobs to place")
        val pins = mutableListOf<JobMapPin>()
        activeJobs.forEach { job ->
            val latLng = when {
                job.lat != null && job.lng != null -> LatLng(job.lat, job.lng)
                job.fullAddress.isNotBlank() -> geocodeAddress(job.fullAddress)
                else -> { Log.w("MapVM", "Job ${job.id} has no coords and no address"); null }
            }
            if (latLng != null) pins.add(JobMapPin(job, latLng))
            else Log.w("MapVM", "Could not place job ${job.id} '${job.title}' on map")
        }
        Log.d("MapVM", "loadJobPins: placed ${pins.size}/${activeJobs.size} pins")
        _jobs.value = pins
    }

    private suspend fun geocodeAddress(address: String): LatLng? {
        geocodeCache[address]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://maps.googleapis.com/maps/api/geocode/json" +
                    "?address=${Uri.encode(address)}" +
                    "&key=AIzaSyDy1o_mAO9O0uHM1Mun7OBuIveS18vQjyk"
                val body = httpClient.newCall(Request.Builder().url(url).build())
                    .execute().body?.string() ?: return@withContext null

                @Suppress("UNCHECKED_CAST")
                val json = gson.fromJson(body, Map::class.java)
                if (json["status"] != "OK") {
                    Log.w("MapVM", "Geocode status=${json["status"]} for: $address")
                    geocodeCache[address] = null
                    return@withContext null
                }
                val results = json["results"] as? List<Map<String, Any>>
                    ?: return@withContext null
                val location = (results.firstOrNull()
                    ?.get("geometry") as? Map<String, Any>)
                    ?.get("location") as? Map<String, Any>
                    ?: return@withContext null
                val lat = location["lat"] as? Double ?: return@withContext null
                val lng = location["lng"] as? Double ?: return@withContext null
                LatLng(lat, lng).also { geocodeCache[address] = it }
            } catch (e: Exception) {
                Log.e("MapVM", "Geocode exception for '$address': ${e.message}")
                null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapScreen(
    onBack: () -> Unit,
    onJob:  (String) -> Unit,
    vm:     MapViewModel = hiltViewModel()
) {
    val techs by vm.techs.collectAsState()
    val jobs  by vm.jobs.collectAsState()

    val camState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(36.8529, -75.9780), 11f)
    }
    var selectedTech  by remember { mutableStateOf<TechLiveLocation?>(null) }
    var selectedJob   by remember { mutableStateOf<JobMapPin?>(null) }
    var hasAutoZoomed by remember { mutableStateOf(false) }
    var mapReady      by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(jobs, mapReady) {
        if (!mapReady || hasAutoZoomed) return@LaunchedEffect
        if (jobs.isEmpty()) return@LaunchedEffect
        hasAutoZoomed = true
        val paddingPx = with(density) { 120.dp.roundToPx() }
        if (jobs.size == 1) {
            camState.animate(CameraUpdateFactory.newLatLngZoom(jobs[0].latLng, 13f))
        } else {
            val builder = LatLngBounds.Builder()
            jobs.forEach { builder.include(it.latLng) }
            try {
                camState.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), paddingPx))
            } catch (e: Exception) {
                camState.animate(CameraUpdateFactory.newLatLngZoom(jobs[0].latLng, 12f))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Map", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    Text(
                        "${techs.count { it.isOnline }} online · ${jobs.size} active jobs",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            val mapCtx = androidx.compose.ui.platform.LocalContext.current
            val darkMap = MaterialTheme.colorScheme.background == androidx.compose.ui.graphics.Color(0xFF141419)
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camState,
                properties = MapProperties(
                    mapType = MapType.NORMAL,
                    isMyLocationEnabled = false,
                    mapStyleOptions = com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(
                        mapCtx, if (darkMap) com.ultimatepro.R.raw.map_style_obsidian else com.ultimatepro.R.raw.map_style_light
                    )
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = false,
                    compassEnabled = true
                ),
                onMapLoaded = { mapReady = true }
            ) {
                // ── Tech pins (blue) ────────────────────────────────────────
                techs.filter { it.lat != null && it.lng != null }.forEach { tech ->
                    val markerState = rememberMarkerState(
                        key = tech.fullName,
                        position = LatLng(tech.lat!!, tech.lng!!)
                    )
                    Marker(
                        state   = markerState,
                        title   = tech.fullName,
                        snippet = tech.current_job_title ?: "Available",
                        icon    = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                        onClick = { selectedTech = tech; selectedJob = null; false }
                    )
                }

                // ── Job pins (status-colored) ───────────────────────────────
                jobs.forEach { pin ->
                    val markerState = rememberMarkerState(
                        key      = pin.job.id,
                        position = pin.latLng
                    )
                    Marker(
                        state   = markerState,
                        title   = pin.job.title,
                        snippet = "${pin.job.customerName} · ${pin.job.status.replace("_", " ")}",
                        icon    = BitmapDescriptorFactory.defaultMarker(jobStatusHue(pin.job.status)),
                        onClick = { selectedJob = pin; selectedTech = null; false }
                    )
                }
            }

            // ── Tech detail card ────────────────────────────────────────────
            selectedTech?.let { tech ->
                Card(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tech.fullName, fontWeight = FontWeight.Bold)
                            tech.current_job_title?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            tech.job_address?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        tech.current_job_id?.let { jid ->
                            IconButton(onClick = { onJob(jid) }) {
                                Icon(Icons.Default.OpenInNew, null, tint = AppColors.Blue)
                            }
                        }
                        IconButton(onClick = { selectedTech = null }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                }
            }

            // ── Job detail card ─────────────────────────────────────────────
            selectedJob?.let { pin ->
                val job = pin.job
                val sc  = AppColors.jobStatus(job.status)
                Card(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Circle, null,
                                    tint = sc, modifier = Modifier.size(8.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    job.status.replace("_", " ").replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sc
                                )
                            }
                            Text(job.title, fontWeight = FontWeight.Bold)
                            Text(job.customerName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            job.fullAddress.takeIf { it.isNotBlank() }?.let {
                                Text(it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onJob(job.id) }) {
                            Icon(Icons.Default.OpenInNew, null, tint = AppColors.Blue)
                        }
                        IconButton(onClick = { selectedJob = null }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                }
            }
        }
    }
}
