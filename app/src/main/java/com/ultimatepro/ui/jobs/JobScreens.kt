package com.ultimatepro.ui.jobs

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.core.content.FileProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.ultimatepro.util.formatJobInstant
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ContentValues
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import coil.compose.AsyncImage
import com.ultimatepro.domain.model.ContractorConnection
import com.ultimatepro.domain.model.CustomerHistory
import com.ultimatepro.domain.model.DuplicateCustomerInfo
import com.ultimatepro.domain.model.EstimateSummary
import com.ultimatepro.domain.model.HistoryNote
import com.ultimatepro.domain.model.HistoryPhoto
import com.ultimatepro.domain.model.InvoiceSummary
import com.ultimatepro.domain.model.JobCompletionDetails
import com.ultimatepro.domain.model.JobSummary
import com.ultimatepro.domain.model.Estimate
import com.ultimatepro.domain.model.Invoice
import com.ultimatepro.domain.model.Job
import com.ultimatepro.domain.model.JobPart
import com.ultimatepro.domain.model.JobPhoto
import com.ultimatepro.domain.model.ParsedTicket
import com.ultimatepro.domain.model.RecentJobSummary
import com.ultimatepro.domain.model.User
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.ultimatepro.ui.common.*
import com.ultimatepro.ui.phone.PhoneViewModel
import com.ultimatepro.ui.phone.SmsMessagesList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

// ── Send-to recipient (unified technician + partner) ──────────────────────
data class SendToRecipient(
    val id:    String,
    val name:  String,
    val type:  String,          // "roster_tech" | "app_user" | "partner"
    val phone: String? = null,
    val email: String? = null
)

// ── Local form model for line items ───────────────────────────────────────
data class LineItemInput(
    val name:      String,
    val qty:       Double,
    val unitPrice: Double,
    val isLabor:   Boolean = false   // false = charge/part, true = labor
) {
    val total: Double get() = qty * unitPrice
}

// ── ViewModel state ────────────────────────────────────────────────────────
data class JobsState(
    val loading:            Boolean         = true,
    val jobs:               List<Job>       = emptyList(),
    val selected:           Job?            = null,
    val error:              String?         = null,
    val saved:              Boolean         = false,
    val isParsing:          Boolean         = false,
    val parsedTicket:       ParsedTicket?   = null,
    val parsedSuccess:      Boolean         = false,
    val originalTicketText: String          = "",
    val techs:              List<User>      = emptyList(),
    val rosterTechs:        List<com.ultimatepro.domain.model.RosterTech> = emptyList(),
    val jobEstimates:       List<Estimate>  = emptyList(),
    val jobInvoice:         Invoice?        = null,
    val beforePhotos:       List<JobPhoto>  = emptyList(),
    val afterPhotos:        List<JobPhoto>  = emptyList(),
    val pendingBeforeUri:   Uri?            = null,
    val pendingAfterUri:    Uri?            = null,
    val duplicateCustomer:  DuplicateCustomerInfo? = null,
    val duplicateCheckDone: Boolean         = false,
    val activeConnections:  List<ContractorConnection> = emptyList(),
    val partnerActionMsg:   String?         = null,
    val reminderMsg:        String?         = null,
    val dispatchMsg:        String?         = null,
    val dispatching:        Boolean         = false,
    val completion:         JobCompletionDetails? = null,
    val customerHistory:    CustomerHistory? = null,
    val historyLoading:     Boolean         = false,
    val currentUserRole:    String?         = null,
    val sendToRecipients:   List<SendToRecipient> = emptyList(),
    val sendToLoading:      Boolean         = false,
    val sendToMsg:          String?         = null,
    val parts:              List<JobPart>   = emptyList(),
    val currentUser:        User?           = null,
    val jobsPermission:     String?         = null,   // resolved jobs level (Option B)
    val newInvoiceId:       String?         = null
)

@HiltViewModel
class JobViewModel @Inject constructor(
    private val repo: CrmRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _s = MutableStateFlow(JobsState())
    val state = _s.asStateFlow()
    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess = _deleteSuccess.asStateFlow()
    init {
        load()
        savedStateHandle.get<String>("ticket")?.takeIf { it.isNotBlank() }?.let { parseTicket(it) }
    }
    fun load(
        statuses: List<String>? = null,
        techIds: List<String>? = null,
        from: String? = null,
        to: String? = null,
        activityFrom: String? = null,
        activityTo: String? = null,
        partnerView: Boolean = false,
        search: String? = null,
        sort: String = "upcoming"
    ) {
        viewModelScope.launch {
            _s.update { it.copy(loading = true) }
            val r = if (partnerView) {
                repo.getJobs(partnerView = true, sort = sort)
            } else {
                repo.getJobs(
                    status       = statuses?.takeIf { it.isNotEmpty() }?.joinToString(","),
                    techId       = techIds?.takeIf { it.isNotEmpty() }?.joinToString(","),
                    from         = from,
                    to           = to,
                    activityFrom = activityFrom,
                    activityTo   = activityTo,
                    search       = search,
                    sort         = sort,
                    includeAllStatuses = statuses?.contains("deleted") == true
                )
            }
            _s.update { it.copy(loading = false, jobs = (r as? Result.Success)?.data?.jobs ?: emptyList(), error = (r as? Result.Error)?.message) }
        }
    }
    fun deleteJob(id: String) {
        viewModelScope.launch {
            when (repo.deleteJob(id)) {
                is Result.Success -> { load(); _deleteSuccess.value = true }
                is Result.Error   -> { /* Silently ignore — dialog already closed */ }
            }
        }
    }
    fun clearDeleteSuccess() { _deleteSuccess.value = false }
    fun restoreJob(id: String) {
        viewModelScope.launch {
            repo.updateJobStatus(id, "unscheduled")
            load(statuses = listOf("deleted"))
        }
    }
    fun loadJob(id: String) {
        viewModelScope.launch {
            val r = repo.getJob(id)
            val user = repo.getCurrentUser()
            _s.update { it.copy(
                selected        = (r as? Result.Success)?.data,
                currentUserRole = user?.role,
                currentUser     = user,
                // Reset history when loading a new job
                customerHistory = null
            ) }
            loadJobDocuments(id)
            loadPhotos(id)
            loadCompletion(id)
            loadParts(id)
        }
    }

    fun loadCompletion(jobId: String) {
        viewModelScope.launch {
            val r = repo.getJobCompletion(jobId)
            if (r is Result.Success) _s.update { it.copy(completion = r.data) }
        }
    }

    fun confirmJobCompletion(jobId: String) {
        viewModelScope.launch {
            when (val r = repo.confirmJobCompletion(jobId)) {
                is Result.Success -> { loadCompletion(jobId); _s.update { it.copy(partnerActionMsg = "Completion confirmed") } }
                is Result.Error   -> _s.update { it.copy(error = r.message) }
            }
        }
    }
    fun loadJobDocuments(jobId: String) {
        viewModelScope.launch {
            val estimates = repo.getEstimates(jobId = jobId)
            val invoices  = repo.getInvoices(jobId = jobId)
            _s.update { it.copy(
                jobEstimates = (estimates as? Result.Success)?.data?.estimates ?: emptyList(),
                jobInvoice   = (invoices  as? Result.Success)?.data?.invoices?.firstOrNull()
            ) }
        }
    }
    private suspend fun fetchPhotos(jobId: String) {
        val before = repo.getUploads("job", jobId, "before_photo")
        val after  = repo.getUploads("job", jobId, "after_photo")
        _s.update { it.copy(
            beforePhotos = (before as? Result.Success)?.data?.map { m ->
                JobPhoto(
                    id       = (m["id"] as? Double)?.toInt() ?: 0,
                    filename = m["filename"] as? String ?: "",
                    url      = m["url"] as? String ?: "",
                    purpose  = m["purpose"] as? String ?: ""
                )
            } ?: emptyList(),
            afterPhotos = (after as? Result.Success)?.data?.map { m ->
                JobPhoto(
                    id       = (m["id"] as? Double)?.toInt() ?: 0,
                    filename = m["filename"] as? String ?: "",
                    url      = m["url"] as? String ?: "",
                    purpose  = m["purpose"] as? String ?: ""
                )
            } ?: emptyList(),
            pendingBeforeUri = null,
            pendingAfterUri  = null
        ) }
    }
    fun loadPhotos(jobId: String) { viewModelScope.launch { fetchPhotos(jobId) } }
    fun updateNotes(jobId: String, notes: String) {
        viewModelScope.launch { repo.updateJob(jobId, mapOf("notes" to notes.ifBlank { null })) }
    }
    fun uploadPhoto(jobId: String, purpose: String, bytes: ByteArray, localUri: Uri? = null) {
        viewModelScope.launch {
            // Show local preview immediately while upload is in flight
            if (localUri != null) {
                if (purpose == "before_photo") _s.update { it.copy(pendingBeforeUri = localUri) }
                else _s.update { it.copy(pendingAfterUri = localUri) }
            }
            val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "photo.jpg", body)
            repo.uploadFile(part, purpose, "job", jobId)
            fetchPhotos(jobId)  // suspends until complete, then clears pending URI
        }
    }
    fun deletePhoto(jobId: String, filename: String) {
        viewModelScope.launch { repo.deleteUpload(filename); loadPhotos(jobId) }
    }
    fun loadTechs() {
        viewModelScope.launch {
            val r = repo.getTechnicians()
            if (r is Result.Success) _s.update { it.copy(techs = r.data) }
        }
    }
    fun loadRosterTechs() {
        viewModelScope.launch {
            val r = repo.getRosterTechs()
            if (r is Result.Success) _s.update { it.copy(rosterTechs = r.data) }
        }
    }
    fun updateStatus(id: String, status: String, notes: String? = null) {
        viewModelScope.launch { repo.updateJobStatus(id, status, notes); loadJob(id) }
    }
    fun createJob(data: Map<String, Any?>, onDone: (String) -> Unit) {
        viewModelScope.launch {
            when (val r = repo.createJob(data)) { is Result.Success -> { load(); onDone(r.data.id) }; is Result.Error -> _s.update { it.copy(error = r.message) } }
        }
    }
    fun updateJob(id: String, data: Map<String, Any?>, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = repo.updateJob(id, data)) { is Result.Success -> { loadJob(id); onDone() }; is Result.Error -> _s.update { it.copy(error = r.message) } }
        }
    }
    fun parseTicket(text: String) {
        viewModelScope.launch {
            _s.update { it.copy(isParsing = true, error = null, originalTicketText = text) }
            when (val r = repo.parseTicket(text)) {
                is Result.Success -> {
                    val m = r.data
                    val rawPhones = (m["phone_numbers"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    val existingCustId = m["existing_customer_id"] as? String
                    // P2.1l Part B: 'phone' → auto-attach; 'name' (even name+address) → surface
                    // the choice without pre-attaching (default Create New). null → no match.
                    val matchType = m["match_type"] as? String
                    val ticket = ParsedTicket(
                        ticketRef          = m["ticket_ref"] as? String,
                        customerName       = m["customer_name"] as? String,
                        companyName        = m["company_name"] as? String,
                        phone              = rawPhones.firstOrNull() ?: m["phone"] as? String,
                        phoneNumbers       = rawPhones,
                        email              = m["email"] as? String,
                        address            = m["address"] as? String,
                        city               = m["city"] as? String,
                        state              = m["state"] as? String,
                        zip                = m["zip"] as? String,
                        jobTitle           = m["job_title"] as? String,
                        jobDescription     = m["job_description"] as? String,
                        scheduledDate      = m["scheduled_date"] as? String,
                        scheduledTime      = m["scheduled_time"] as? String,
                        source             = m["source"] as? String,
                        sourceReviewLink   = m["source_review_link"] as? String,
                        existingCustomerId = existingCustId,
                        matchType          = matchType,
                        leftoverNotes      = (m["leftover_notes"] as? String)?.takeIf { it.isNotBlank() }
                    )
                    // Build duplicate customer info if an existing customer was found
                    val dupInfo = if (existingCustId != null) {
                        val cMap = m["existing_customer"] as? Map<*, *>
                        val first = cMap?.get("first_name") as? String ?: ""
                        val last  = cMap?.get("last_name")  as? String
                        val addr  = listOfNotNull(
                            cMap?.get("address") as? String,
                            cMap?.get("city")    as? String,
                            cMap?.get("state")   as? String
                        ).joinToString(", ").ifBlank { null }
                        val recentJobs = (m["existing_jobs"] as? List<*>)?.mapNotNull { j ->
                            (j as? Map<*, *>)?.let { jm ->
                                RecentJobSummary(
                                    id            = jm["id"]             as? String ?: return@mapNotNull null,
                                    jobNumber     = jm["job_number"]     as? String ?: "",
                                    title         = jm["title"]          as? String ?: "",
                                    status        = jm["status"]         as? String ?: "",
                                    scheduledStart = jm["scheduled_start"] as? String
                                )
                            }
                        } ?: emptyList()
                        DuplicateCustomerInfo(
                            customerId   = existingCustId,
                            customerName = "$first ${last ?: ""}".trim(),
                            phone        = cMap?.get("phone") as? String,
                            address      = addr,
                            recentJobs   = recentJobs
                        )
                    } else null
                    _s.update { it.copy(isParsing = false, parsedTicket = ticket, parsedSuccess = true, duplicateCustomer = dupInfo) }
                }
                is Result.Error -> _s.update { it.copy(isParsing = false, error = r.message) }
            }
        }
    }
    fun clearParsedSuccess() { _s.update { it.copy(parsedSuccess = false) } }
    fun clearError()         { _s.update { it.copy(error = null) } }
    fun clearPartnerMsg()    { _s.update { it.copy(partnerActionMsg = null) } }
    fun clearReminderMsg()   { _s.update { it.copy(reminderMsg = null) } }
    fun clearDispatchMsg()   { _s.update { it.copy(dispatchMsg = null) } }

    @SuppressLint("MissingPermission")
    fun dispatchJob(jobId: String, context: android.content.Context) {
        viewModelScope.launch {
            _s.update { it.copy(dispatching = true) }
            try {
                val fusedClient = com.google.android.gms.location.LocationServices
                    .getFusedLocationProviderClient(context)
                val deferred = CompletableDeferred<android.location.Location?>()
                fusedClient.lastLocation
                    .addOnSuccessListener { deferred.complete(it) }
                    .addOnFailureListener { deferred.complete(null) }
                val loc = deferred.await()
                val lat = loc?.latitude ?: 0.0
                val lng = loc?.longitude ?: 0.0

                when (val r = repo.dispatchJob(jobId, lat, lng)) {
                    is Result.Success -> {
                        val eta = r.data["eta"] as? String
                        val msg = "Customer notified. Job status updated to En Route." +
                            if (eta != null && eta != "shortly") " ETA: $eta." else ""
                        _s.update { it.copy(
                            dispatching = false,
                            dispatchMsg = msg,
                            selected    = it.selected?.copy(status = "en_route")
                        ) }
                    }
                    is Result.Error -> _s.update { it.copy(dispatching = false, error = r.message) }
                }
            } catch (e: Exception) {
                _s.update { it.copy(dispatching = false, error = e.message ?: "Dispatch failed") }
            }
        }
    }

    fun loadCurrentUser() {
        viewModelScope.launch {
            val user = repo.getCurrentUser()
            if (user != null) _s.update { it.copy(currentUser = user, currentUserRole = user.role) }
            _s.update { it.copy(jobsPermission = repo.getMyPermissions()["jobs"]) }
        }
    }

    fun arrivedJob(jobId: String) {
        viewModelScope.launch {
            when (val r = repo.arrivedJob(jobId)) {
                is Result.Success -> _s.update { it.copy(
                    dispatchMsg = "Customer notified that you have arrived.",
                    selected    = it.selected?.copy(status = "in_progress")
                ) }
                is Result.Error -> _s.update { it.copy(error = r.message) }
            }
        }
    }

    fun loadParts(jobId: String) {
        viewModelScope.launch {
            val r = repo.getJobParts(jobId)
            if (r is Result.Success) _s.update { it.copy(parts = r.data) }
        }
    }

    fun savePart(jobId: String, name: String, cost: Double, provider: String) {
        viewModelScope.launch {
            val r = repo.addJobPart(jobId, mapOf("name" to name, "cost" to cost, "provider" to provider))
            when (r) {
                is Result.Success -> loadParts(jobId)
                is Result.Error   -> _s.update { it.copy(error = r.message) }
            }
        }
    }

    fun deletePart(jobId: String, partId: String) {
        viewModelScope.launch { repo.deleteJobPart(jobId, partId); loadParts(jobId) }
    }

    fun createJobInvoice(jobId: String, customerId: String) {
        viewModelScope.launch {
            val r = repo.createInvoice(mapOf(
                "job_id"     to jobId,
                "customer_id" to customerId,
                "line_items"  to emptyList<Any>()
            ))
            when (r) {
                is Result.Success -> _s.update { it.copy(jobInvoice = r.data, newInvoiceId = r.data.id) }
                is Result.Error   -> _s.update { it.copy(error = r.message) }
            }
        }
    }

    fun clearNewInvoiceId() { _s.update { it.copy(newInvoiceId = null) } }

    fun updateReminderMethod(jobId: String, method: String) {
        viewModelScope.launch {
            when (repo.updateJobReminderMethod(jobId, method)) {
                is Result.Success -> _s.update { it.copy(reminderMsg = "Reminder updated") }
                is Result.Error   -> { /* silent — user stays on same selection */ }
            }
        }
    }
    fun clearDuplicateCustomer() { _s.update { it.copy(duplicateCustomer = null, duplicateCheckDone = false, error = null) } }

    fun loadCustomerHistory(customerId: String, excludeJobId: String) {
        if (_s.value.historyLoading) return
        viewModelScope.launch {
            _s.update { it.copy(historyLoading = true) }
            when (val r = repo.getCustomerHistory(customerId, excludeJobId)) {
                is Result.Success -> _s.update { it.copy(customerHistory = r.data, historyLoading = false) }
                is Result.Error   -> _s.update { it.copy(historyLoading = false, error = r.message) }
            }
        }
    }
    fun resetDuplicateCheck()    { _s.update { it.copy(duplicateCheckDone = false) } }

    fun loadActiveConnections() {
        viewModelScope.launch {
            val r = repo.getConnections()
            if (r is Result.Success) {
                val connections = (r.data as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { m ->
                        val status = m["status"] as? String ?: return@mapNotNull null
                        if (status != "active") return@mapNotNull null
                        val agreementMap = m["latest_agreement"] as? Map<*, *>
                        val hasActiveAgreement = agreementMap?.get("status") == "active"
                        if (!hasActiveAgreement) return@mapNotNull null
                        ContractorConnection(
                            id = m["id"] as? String ?: return@mapNotNull null,
                            partnerCompanyId   = m["partner_company_id"] as? String ?: "",
                            partnerCompanyName = m["partner_company_name"] as? String ?: "",
                            status             = status
                        )
                    }
                } ?: emptyList()
                _s.update { it.copy(activeConnections = connections) }
            }
        }
    }

    fun sendJobToPartner(jobId: String, partnerCompanyId: String, techPermissions: Map<String, Boolean>) {
        viewModelScope.launch {
            val body: Map<String, Any?> = mapOf(
                "partner_company_id" to partnerCompanyId,
                "tech_permissions"   to techPermissions
            )
            when (val r = repo.sendJobToPartner(jobId, body)) {
                is Result.Success -> { loadJob(jobId); _s.update { it.copy(partnerActionMsg = "Job sent to partner") } }
                is Result.Error   -> _s.update { it.copy(error = r.message) }
            }
        }
    }

    fun confirmPartnerStatus(jobId: String, action: String) {
        viewModelScope.launch {
            when (val r = repo.confirmPartnerStatus(jobId, action)) {
                is Result.Success -> { loadJob(jobId); _s.update { it.copy(partnerActionMsg = if (action == "confirm") "Status confirmed" else "Status disputed") }  }
                is Result.Error   -> _s.update { it.copy(error = r.message) }
            }
        }
    }

    // Release held (pending-review) earnings. Backend guards via canApproveEarnings.
    fun approveEarnings(jobId: String) {
        viewModelScope.launch {
            when (val r = repo.approveEarnings(jobId)) {
                is Result.Success -> { loadJob(jobId); _s.update { it.copy(partnerActionMsg = "Earnings approved") } }
                is Result.Error   -> _s.update { it.copy(error = r.message) }
            }
        }
    }

    fun loadSendToRecipients(job: Job) {
        viewModelScope.launch {
            _s.update { it.copy(sendToLoading = true, sendToRecipients = emptyList()) }
            val recipients = mutableListOf<SendToRecipient>()

            // 1. Assigned roster tech
            if (job.assigned_roster_tech_id != null) {
                val existing = _s.value.rosterTechs.find { it.id == job.assigned_roster_tech_id }
                val tech = if (existing != null) existing else {
                    val r = repo.getRosterTechs()
                    if (r is Result.Success) { _s.update { s -> s.copy(rosterTechs = r.data) }; r.data.find { it.id == job.assigned_roster_tech_id } }
                    else null
                }
                tech?.let { recipients.add(SendToRecipient(it.id, it.name, "roster_tech", it.phone, it.email)) }
            }

            // 2. Assigned app user tech
            if (job.assigned_to != null) {
                val existing = _s.value.techs.find { it.id == job.assigned_to }
                val user = if (existing != null) existing else {
                    val r = repo.getTechnicians()
                    if (r is Result.Success) { _s.update { s -> s.copy(techs = r.data) }; r.data.find { it.id == job.assigned_to } }
                    else null
                }
                user?.let { recipients.add(SendToRecipient(it.id, it.fullName, "app_user", it.phone, it.email)) }
            }

            // 3. Active network partners
            val connR = repo.getActiveConnectionsSimple()
            if (connR is Result.Success) {
                connR.data.forEach { conn ->
                    recipients.add(SendToRecipient(conn.partnerId, conn.partnerName, "partner"))
                }
            }

            _s.update { it.copy(sendToLoading = false, sendToRecipients = recipients) }
        }
    }

    fun notifyRosterTech(jobId: String, techId: String, method: String, techName: String) {
        viewModelScope.launch {
            val methods = if (method == "both") listOf("sms", "email") else listOf(method)
            var errMsg: String? = null
            methods.forEach { m ->
                when (val r = repo.notifyRosterTech(jobId, m)) {
                    is Result.Success -> {}
                    is Result.Error   -> errMsg = r.message
                }
            }
            if (errMsg != null) _s.update { it.copy(error = errMsg) }
            else _s.update { it.copy(sendToMsg = "Job details sent to $techName") }
        }
    }

    fun notifyAppUserTech(jobId: String, userId: String, techName: String) {
        // App users see assigned jobs in the app; message confirms the assignment is visible
        _s.update { it.copy(sendToMsg = "Notification sent to $techName") }
    }

    fun clearSendToMsg() { _s.update { it.copy(sendToMsg = null) } }

    fun loadPartnerJobs() {
        viewModelScope.launch {
            _s.update { it.copy(loading = true) }
            val r = repo.getPartnerJobs()
            _s.update { it.copy(loading = false, jobs = (r as? Result.Success)?.data?.jobs ?: emptyList(), error = (r as? Result.Error)?.message) }
        }
    }

    /** Phone-based duplicate detection for manual form saves (no paste ticket).
     *  Sets duplicateCustomer if found, then sets duplicateCheckDone = true either way. */
    fun checkDuplicateByPhone(phone: String) {
        viewModelScope.launch {
            val r = repo.getCustomers(search = phone)
            val match = (r as? Result.Success)?.data?.customers?.firstOrNull { c ->
                c.phone == phone || c.phone2 == phone
            }
            if (match != null) {
                val jobs = repo.getJobs(custId = match.id)
                val recentJobs = (jobs as? Result.Success)?.data?.jobs?.take(3)?.map { j ->
                    RecentJobSummary(j.id, j.job_number, j.title, j.status, j.scheduled_start)
                } ?: emptyList()
                _s.update { it.copy(
                    duplicateCustomer  = DuplicateCustomerInfo(match.id, match.fullName, match.phone, match.fullAddress.ifBlank { null }, recentJobs),
                    duplicateCheckDone = true
                ) }
            } else {
                _s.update { it.copy(duplicateCheckDone = true) }
            }
        }
    }

    fun createJobWithCustomer(jobData: Map<String, Any?>, customerData: Map<String, Any?>?, extraPhones: List<String> = emptyList(), extraEmails: List<String> = emptyList(), onDone: (String) -> Unit) {
        viewModelScope.launch {
            var cid = jobData["customer_id"] as? String
            if (cid == null && customerData != null) {
                when (val r = repo.createCustomer(customerData)) {
                    is Result.Success -> cid = r.data.id
                    is Result.Error -> { _s.update { it.copy(error = r.message) }; return@launch }
                }
            }
            if (cid == null) { _s.update { it.copy(error = "Customer name is required") }; return@launch }
            when (val r = repo.createJob(jobData + mapOf("customer_id" to cid))) {
                is Result.Success -> {
                    val customerId = cid!!
                    extraPhones.filter { it.isNotBlank() }.forEach { ph ->
                        repo.addCustomerContact(customerId, "phone", ph, "mobile")
                    }
                    extraEmails.filter { it.isNotBlank() }.forEach { em ->
                        repo.addCustomerContact(customerId, "email", em, "personal")
                    }
                    load()
                    onDone(r.data.id)
                }
                is Result.Error -> _s.update { it.copy(error = r.message) }
            }
        }
    }
}

// ── Smart helpers for the Jobs dashboard ───────────────────────────────────
// Date helpers return ISO 8601 (local-naive) strings so the backend's TIMESTAMPTZ
// comparison treats them as the device's local wall clock.

private fun isoDate(cal: Calendar): String =
    "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

/** Returns (from, to) ISO 8601 bounds for a named range. Monday-Sunday week boundary. */
private fun dateBoundsFor(
    range: String,
    customFrom: String? = null,
    customTo: String? = null
): Pair<String?, String?> {
    val cal = Calendar.getInstance()
    return when (range) {
        "today" -> {
            val d = isoDate(cal)
            "${d}T00:00:00" to "${d}T23:59:59"
        }
        "yesterday" -> {
            cal.add(Calendar.DATE, -1)
            val d = isoDate(cal)
            "${d}T00:00:00" to "${d}T23:59:59"
        }
        "this_week" -> {
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val daysToMon = (dow - Calendar.MONDAY + 7) % 7
            cal.add(Calendar.DATE, -daysToMon)
            val from = isoDate(cal)
            cal.add(Calendar.DATE, 6)
            val to = isoDate(cal)
            "${from}T00:00:00" to "${to}T23:59:59"
        }
        "last_week" -> {
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val daysToMon = (dow - Calendar.MONDAY + 7) % 7
            cal.add(Calendar.DATE, -daysToMon - 7)
            val from = isoDate(cal)
            cal.add(Calendar.DATE, 6)
            val to = isoDate(cal)
            "${from}T00:00:00" to "${to}T23:59:59"
        }
        "this_month" -> {
            val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH) + 1
            val last = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            "%04d-%02d-01T00:00:00".format(y, m) to "%04d-%02d-%02dT23:59:59".format(y, m, last)
        }
        "last_30d" -> {
            val nowD = isoDate(cal)
            cal.add(Calendar.DATE, -30)
            val fromD = isoDate(cal)
            "${fromD}T00:00:00" to "${nowD}T23:59:59"
        }
        "custom" -> {
            (customFrom?.let { "${it}T00:00:00" }) to (customTo?.let { "${it}T23:59:59" })
        }
        else -> null to null  // "all"
    }
}

private fun todayIso(): String = isoDate(Calendar.getInstance())

/** Pick "upcoming" (next-task-first) for forward windows, "recent" for past-only windows. */
private fun sortFor(dateRange: String, customTo: String?): String = when (dateRange) {
    "yesterday", "last_week" -> "recent"
    "custom" -> if (customTo != null && customTo < todayIso()) "recent" else "upcoming"
    else -> "upcoming"
}

/** Humanize an ISO datetime: null → "Unscheduled", today → "Today, 2:00 PM", etc. */
@SuppressLint("SimpleDateFormat")
private fun humanizeScheduled(iso: String?): String {
    if (iso.isNullOrBlank()) return "Unscheduled"
    val date = try { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(iso) }
        catch (_: Exception) { try { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(iso) } catch (_: Exception) { null } }
        ?: return "Unscheduled"
    val tdy = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val schedDay = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val daysDiff = ((schedDay.timeInMillis - tdy.timeInMillis) / (1000L * 60 * 60 * 24)).toInt()
    val timeFmt = java.text.SimpleDateFormat("h:mm a").format(date)
    return when {
        daysDiff == 0 -> "Today, $timeFmt"
        daysDiff == 1 -> "Tomorrow, $timeFmt"
        daysDiff in -6..6 -> "${java.text.SimpleDateFormat("EEE, MMM d").format(date)} · $timeFmt"
        else -> "${java.text.SimpleDateFormat("MMM d, yyyy").format(date)} · $timeFmt"
    }
}

// ── Job List ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobListScreen(onJob: (String) -> Unit, onNewJob: () -> Unit, vm: JobViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var search       by remember { mutableStateOf("") }
    var dateRange    by remember { mutableStateOf("today") }
    var customFrom   by remember { mutableStateOf<String?>(null) }
    var customTo     by remember { mutableStateOf<String?>(null) }
    var selectedStatuses by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedTechIds  by remember { mutableStateOf<Set<String>>(emptySet()) }
    var partnerView      by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    var resumeKey by remember { mutableIntStateOf(0) }

    val dateChips = listOf(
        "today" to "Today",
        "yesterday" to "Yesterday",
        "this_week" to "This Week",
        "last_week" to "Last Week",
        "this_month" to "This Month",
        "last_30d" to "Last 30d",
        "custom" to "Custom",
        "all" to "All"
    )
    val activeFilterCount = selectedStatuses.size + selectedTechIds.size + (if (partnerView) 1 else 0)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { vm.loadTechs() }

    val pullState = rememberPullToRefreshState()

    val doLoad: () -> Unit = {
        val (from, to) = dateBoundsFor(dateRange, customFrom, customTo)
        vm.load(
            statuses     = selectedStatuses.toList().takeIf { it.isNotEmpty() },
            techIds      = selectedTechIds.toList().takeIf { it.isNotEmpty() },
            activityFrom = from,
            activityTo   = to,
            partnerView  = partnerView,
            sort         = sortFor(dateRange, customTo)
        )
    }
    LaunchedEffect(dateRange, customFrom, customTo, selectedStatuses, selectedTechIds, partnerView, resumeKey) { doLoad() }
    if (pullState.isRefreshing) { LaunchedEffect(Unit) { doLoad() } }
    LaunchedEffect(state.loading) { if (!state.loading && pullState.isRefreshing) pullState.endRefresh() }

    val displayedJobs = remember(state.jobs, search) {
        if (search.isBlank()) state.jobs
        else state.jobs.filter { job ->
            val q = search.trim()
            job.title.contains(q, ignoreCase = true) ||
            job.job_number.contains(q, ignoreCase = true) ||
            job.customerName.contains(q, ignoreCase = true) ||
            job.cust_phone?.contains(q, ignoreCase = true) == true ||
            job.fullAddress.contains(q, ignoreCase = true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Column {
                TopAppBar(
                    title   = { Text("Jobs", fontWeight = FontWeight.Bold) },
                    actions = { IconButton(onClick = onNewJob) { Icon(Icons.Default.Add, null) } }
                )

                // Date chip row + filter dropdown trigger
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.lazy.LazyRow(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(dateChips) { (key, label) ->
                            val displayLabel = if (key == "custom" && customFrom != null && customTo != null)
                                "$customFrom → $customTo" else label
                            FilterChip(
                                selected = dateRange == key,
                                onClick  = {
                                    if (key == "custom") {
                                        showCustomDateDialog = true
                                    } else {
                                        dateRange = key
                                        customFrom = null; customTo = null
                                    }
                                },
                                label    = { Text(displayLabel) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.Blue,
                                    selectedLabelColor     = Color.White
                                )
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filters")
                        }
                        if (activeFilterCount > 0) {
                            Surface(
                                color = AppColors.Blue,
                                shape = CircleShape,
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 4.dp).size(16.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        activeFilterCount.toString(),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                SearchField(
                    value    = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewJob, icon = { Icon(Icons.Default.Add, null) }, text = { Text("New Job") })
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).nestedScroll(pullState.nestedScrollConnection)) {
            when {
                state.loading -> LoadingView()
                state.error != null -> ErrorView(state.error!!, onRetry = { doLoad() })
                displayedJobs.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.WorkOutline, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    Spacer(Modifier.height(16.dp))
                    Text("No jobs found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Try a different filter or date range", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedJobs, key = { it.id }) { job ->
                        JobListCard(
                            job       = job,
                            onClick   = { if (job.status != "deleted") onJob(job.id) },
                            onRestore = { vm.restoreJob(job.id) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
            PullToRefreshContainer(state = pullState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }

    if (showCustomDateDialog) {
        CustomDateRangeDialog(
            initialFrom = customFrom,
            initialTo   = customTo,
            onApply     = { f, t ->
                customFrom = f; customTo = t; dateRange = "custom"
                showCustomDateDialog = false
            },
            onDismiss   = { showCustomDateDialog = false }
        )
    }
    if (showFilterDialog) {
        StatusTechFilterDialog(
            techs            = state.techs,
            initialStatuses  = selectedStatuses,
            initialTechIds   = selectedTechIds,
            initialPartnerView = partnerView,
            onApply = { ss, st, pv ->
                selectedStatuses = ss; selectedTechIds = st; partnerView = pv
                showFilterDialog = false
            },
            onClear = {
                selectedStatuses = emptySet(); selectedTechIds = emptySet(); partnerView = false
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }
}

@Composable
private fun JobListCard(job: Job, onClick: () -> Unit, onRestore: () -> Unit) {
    val isDeleted = job.status == "deleted"
    val sc = if (isDeleted) AppColors.Red else AppColors.jobStatus(job.status)
    Card(
        onClick   = { if (!isDeleted) onClick() },
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            AccentBar(sc)
            Column(Modifier.padding(14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (job.sent_by_company_name != null) {
                    Surface(color = AppColors.Green.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inbox, null, Modifier.size(12.dp), tint = AppColors.Green)
                            Spacer(Modifier.width(4.dp))
                            Text("From ${job.sent_by_company_name}", style = MaterialTheme.typography.labelSmall, color = AppColors.Green, fontWeight = FontWeight.Medium)
                        }
                    }
                } else if (job.sent_to_company_name != null) {
                    Surface(color = AppColors.Blue.copy(alpha = 0.10f), shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Sent to ${job.sent_to_company_name}", style = MaterialTheme.typography.labelSmall, color = AppColors.Blue, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Row 1: number + type chip + (priority/archived right)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(job.job_number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Surface(color = AppColors.Blue.copy(alpha = 0.10f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            job.type.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.Blue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    when {
                        isDeleted -> StatusBadge("Archived", AppColors.Red, small = true)
                        job.priority == "urgent" || job.priority == "high" -> PriorityBadge(job.priority)
                    }
                }

                // Row 2: customer name + member badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        job.customerName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1
                    )
                    if (job.membership_id != null) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = AppColors.Gold.copy(alpha = 0.18f), shape = RoundedCornerShape(8.dp)) {
                            Text(
                                "⭐ Member",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.Gold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Row 3: address (only if present)
                val addrParts = listOfNotNull(
                    job.address?.takeIf { it.isNotBlank() },
                    job.city?.takeIf { it.isNotBlank() }
                )
                if (addrParts.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            addrParts.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                // Row 4: scheduled / status / tech (or Restore if deleted)
                if (isDeleted) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = onRestore,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) { Text("Restore", color = AppColors.Blue, style = MaterialTheme.typography.labelMedium) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        // TZ display fix: render the list time in the job's zone (same helper as
                        // Calendar + Job Detail), not the old SimpleDateFormat device-local path.
                        val schedText = if (job.scheduled_start.isNullOrBlank()) "Unscheduled"
                            else job.scheduled_end?.takeIf { it != job.scheduled_start }?.let { end ->  // P2.19 window
                                "${formatJobInstant(job.scheduled_start, job.effective_timezone, "MMM d · h:mm a")} – ${formatJobInstant(end, job.effective_timezone, "h:mm a zzz")}"
                            } ?: formatJobInstant(job.scheduled_start, job.effective_timezone, "MMM d · h:mm a zzz")
                        Text(
                            schedText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (schedText == "Unscheduled")
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(sc))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (job.status == "holding") "Holding" else job.status.replace("_", " ").replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = sc,
                            fontWeight = FontWeight.Medium
                        )
                        job.techName?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDateRangeDialog(
    initialFrom: String?,
    initialTo: String?,
    onApply: (String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var fromDate by remember { mutableStateOf(initialFrom) }
    var toDate   by remember { mutableStateOf(initialTo) }
    var pickerTarget by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Date Range") },
        text = {
            Column {
                OutlinedButton(onClick = { pickerTarget = "from" }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("From: ${fromDate ?: "Pick start date"}")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { pickerTarget = "to" }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("To: ${toDate ?: "Pick end date"}")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (fromDate != null && toDate != null) onApply(fromDate, toDate) },
                enabled = fromDate != null && toDate != null
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (pickerTarget != null) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
                        val iso = "%04d-%02d-%02d".format(
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH) + 1,
                            cal.get(Calendar.DAY_OF_MONTH)
                        )
                        if (pickerTarget == "from") fromDate = iso else toDate = iso
                    }
                    pickerTarget = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickerTarget = null }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun StatusTechFilterDialog(
    techs: List<User>,
    initialStatuses: Set<String>,
    initialTechIds: Set<String>,
    initialPartnerView: Boolean,
    onApply: (Set<String>, Set<String>, Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var ss by remember { mutableStateOf(initialStatuses) }
    var st by remember { mutableStateOf(initialTechIds) }
    var pv by remember { mutableStateOf(initialPartnerView) }

    val statusOptions = listOf(
        "scheduled" to "Scheduled",
        "en_route" to "En Route",
        "in_progress" to "In Progress",
        "holding" to "Holding",
        "completed" to "Completed",
        "cancelled" to "Cancelled",
        "deleted" to "Deleted"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filters") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                SectionLabel("STATUS")
                statusOptions.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { ss = if (ss.contains(key)) ss - key else ss + key },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = ss.contains(key), onCheckedChange = { c -> ss = if (c) ss + key else ss - key })
                        val sc = if (key == "deleted") AppColors.Red else AppColors.jobStatus(key)
                        Box(Modifier.size(8.dp).clip(CircleShape).background(sc))
                        Spacer(Modifier.width(6.dp))
                        Text(label)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { pv = !pv },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = pv, onCheckedChange = { pv = it })
                    Icon(Icons.Default.Inbox, null, Modifier.size(14.dp), tint = AppColors.Green)
                    Spacer(Modifier.width(6.dp))
                    Text("Received (from partners)")
                }
                Spacer(Modifier.height(12.dp))
                SectionLabel("TECHNICIAN")
                if (techs.isEmpty()) {
                    Text(
                        "No technicians",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    techs.forEach { t ->
                        val name = "${t.first_name} ${t.last_name}".trim().ifBlank { t.email.ifBlank { t.id.take(8) } }
                        Row(
                            Modifier.fillMaxWidth().clickable { st = if (st.contains(t.id)) st - t.id else st + t.id },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = st.contains(t.id), onCheckedChange = { c -> st = if (c) st + t.id else st - t.id })
                            Text(name)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(ss, st, pv) }) { Text("Apply") } },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Clear All") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

// ── Job Detail ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun JobDetailScreen(
    jobId:            String,
    onBack:           () -> Unit,
    onCustomer:       (String) -> Unit,
    onInvoice:        (String) -> Unit,
    onEdit:           () -> Unit,
    onComplete:       () -> Unit = {},
    onPayment:        (String) -> Unit = {},
    onCreateEstimate: () -> Unit = {},
    onViewEstimate:   (String) -> Unit = {},
    onViewInvoice:    (String) -> Unit = {},
    onLinkedJob:      (String) -> Unit = {},
    onSmsThread:      (String) -> Unit = {},
    vm: JobViewModel = hiltViewModel(),
    smsVm: PhoneViewModel = hiltViewModel()
) {
    val state         by vm.state.collectAsState()
    val deleteSuccess by vm.deleteSuccess.collectAsState()
    val ctx = LocalContext.current
    var selectedTab         by remember { mutableStateOf(0) }
    var showStatus          by remember { mutableStateOf(false) }
    var showPaymentPrompt   by remember { mutableStateOf(false) }
    var showDeleteConfirm   by remember { mutableStateOf(false) }
    var showSendToDialog    by remember { mutableStateOf(false) }
    var showTechPerms       by remember { mutableStateOf(false) }
    var showCompletionSheet   by remember { mutableStateOf(false) }
    var showDispatchConfirm   by remember { mutableStateOf(false) }
    var showArrivedConfirm    by remember { mutableStateOf(false) }
    var showAddPartDialog     by remember { mutableStateOf(false) }
    var historyViewerUrl      by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(jobId) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.loadJob(jobId) }
    }
    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) { vm.clearDeleteSuccess(); onBack() }
    }
    val job             = state.selected
    val jobEstimates    = state.jobEstimates
    val jobInvoice      = state.jobInvoice
    val beforePhotos    = state.beforePhotos
    val afterPhotos     = state.afterPhotos
    val pendingBeforeUri = state.pendingBeforeUri
    val pendingAfterUri  = state.pendingAfterUri
    val completion       = state.completion

    // TASK 2A: notes editing
    var notesText  by remember(job?.notes) { mutableStateOf(job?.notes ?: "") }
    var savedNotes by remember(job?.notes) { mutableStateOf(job?.notes ?: "") }

    // TASK 2D: camera/gallery pickers
    val scope  = rememberCoroutineScope()
    val snack  = remember { SnackbarHostState() }
    val partnerActionMsg = state.partnerActionMsg
    LaunchedEffect(partnerActionMsg) {
        if (partnerActionMsg != null) { snack.showSnackbar(partnerActionMsg); vm.clearPartnerMsg() }
    }
    val reminderMsg = state.reminderMsg
    LaunchedEffect(reminderMsg) {
        if (reminderMsg != null) { snack.showSnackbar(reminderMsg); vm.clearReminderMsg() }
    }
    val dispatchMsg = state.dispatchMsg
    LaunchedEffect(dispatchMsg) {
        if (dispatchMsg != null) { snack.showSnackbar(dispatchMsg); vm.clearDispatchMsg() }
    }
    val sendToMsg = state.sendToMsg
    LaunchedEffect(sendToMsg) {
        if (sendToMsg != null) { snack.showSnackbar(sendToMsg); vm.clearSendToMsg() }
    }
    val newInvoiceId = state.newInvoiceId
    LaunchedEffect(newInvoiceId) {
        if (newInvoiceId != null) { vm.clearNewInvoiceId(); onViewInvoice(newInvoiceId) }
    }
    var photoTarget  by remember { mutableStateOf("before_photo") }
    var cameraUri    by remember { mutableStateOf<Uri?>(null) }
    var viewerPhoto  by remember { mutableStateOf<JobPhoto?>(null) }
    // Save uploaded photo to device gallery (Pictures/UltimatePro)
    val saveToGallery: (ByteArray, String, String) -> Unit = { bytes, jId, purpose ->
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "job_${jId}_${purpose}_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UltimatePro")
            }
            val imgUri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            imgUri?.let { ctx.contentResolver.openOutputStream(it)?.use { stream -> stream.write(bytes) } }
        } catch (_: Exception) { /* gallery save is best-effort */ }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraUri?.let { u ->
                ctx.contentResolver.openInputStream(u)?.readBytes()?.let { bytes ->
                    saveToGallery(bytes, jobId, photoTarget)
                    vm.uploadPhoto(jobId, photoTarget, bytes, u)
                }
            }
        }
    }
    val cameraPermState = rememberPermissionState(android.Manifest.permission.CAMERA) { granted ->
        if (granted) {
            val dir  = File(ctx.cacheDir, "photos").also { it.mkdirs() }
            val file = File.createTempFile("photo_", ".jpg", dir)
            cameraUri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            cameraUri?.let { cameraLauncher.launch(it) }
        } else {
            scope.launch { snack.showSnackbar("Camera permission required") }
        }
    }
    val launchCamera: () -> Unit = {
        if (cameraPermState.status.isGranted) {
            val dir  = File(ctx.cacheDir, "photos").also { it.mkdirs() }
            val file = File.createTempFile("photo_", ".jpg", dir)
            cameraUri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            cameraUri?.let { cameraLauncher.launch(it) }
        } else {
            cameraPermState.launchPermissionRequest()
        }
    }

    // FIX 2: PickVisualMedia requires API 33+; fall back to GetContent on older devices
    val galleryLauncherNew = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { u ->
            ctx.contentResolver.openInputStream(u)?.readBytes()?.let { bytes ->
                saveToGallery(bytes, jobId, photoTarget)
                vm.uploadPhoto(jobId, photoTarget, bytes, u)
            }
        }
    }
    val galleryLauncherOld = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u ->
            ctx.contentResolver.openInputStream(u)?.readBytes()?.let { bytes ->
                saveToGallery(bytes, jobId, photoTarget)
                vm.uploadPhoto(jobId, photoTarget, bytes, u)
            }
        }
    }
    val launchGallery: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            galleryLauncherNew.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            galleryLauncherOld.launch("image/*")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
        TopAppBar(
            title = { Text(job?.job_number ?: "Job Detail", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = { job?.let {
                // P2.1b item 3: Dispatch icon mirrors web (JobDetail.jsx) — shown for
                // unscheduled/scheduled, no cust_phone gate. Same POST /jobs/:id/dispatch
                // (geolocation-or-omit via FusedLocation in dispatchJob).
                if (job.status in listOf("unscheduled", "scheduled")) {
                    IconButton(onClick = { showDispatchConfirm = true }) {
                        Icon(Icons.Default.Navigation, "Dispatch", tint = Color(0xFF1A73E8))
                    }
                }
                if (job.status == "en_route") {
                    IconButton(onClick = { showArrivedConfirm = true }) {
                        Icon(Icons.Default.LocationOn, "Arrived", tint = Color(0xFF10B981))
                    }
                }
                IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, null, tint = AppColors.Red) }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit job") }
                // P2.1b item 2: removed the duplicate top-bar status (Update) icon —
                // the inline status control (tap the status pill below) is the single way.
            } }
        )
    }) { padding ->
        if (job == null) { LoadingView(); return@Scaffold }
        val sc = AppColors.jobStatus(job.status)

        // Permission check for History tab
        val currentRole    = state.currentUserRole ?: "technician"
        val isTech         = currentRole !in listOf("owner", "admin", "manager")
        val canViewHistory = !isTech || job.tech_permissions["view_history"] != false

        val smsState by smsVm.state.collectAsState()

        // Trigger history/messages load when tab changes
        LaunchedEffect(selectedTab) {
            when (selectedTab) {
                1 -> if (state.customerHistory == null && !state.historyLoading) {
                    vm.loadCustomerHistory(job.customer_id, jobId)
                }
                2 -> smsVm.loadJobMessages(jobId)
            }
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = MaterialTheme.colorScheme.surface,
                contentColor     = MaterialTheme.colorScheme.primary
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Details") })
                if (canViewHistory) {
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("History") })
                }
                Tab(
                    selected = selectedTab == 2,
                    onClick  = { selectedTab = 2 },
                    text     = { Text("Messages") }
                )
            }

            when (selectedTab) {
                1 -> if (canViewHistory) {
                    CustomerHistoryTab(
                        history    = state.customerHistory,
                        loading    = state.historyLoading,
                        onJob      = onLinkedJob,
                        onEstimate = onViewEstimate,
                        onInvoice  = onViewInvoice,
                        onPhoto    = { url -> historyViewerUrl = url }
                    )
                }
                2 -> {
                    val convId = smsState.jobMessages.firstOrNull()?.conversationId
                    SmsMessagesList(
                        messages       = smsState.jobMessages,
                        conversationId = convId,
                        onOpenThread   = if (convId != null) onSmsThread else null
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            // TASK 2B: status chip — tappable shortcut to status sheet
            item {
                Box(Modifier.fillMaxWidth().padding(14.dp).clickable { showStatus = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Circle, null, tint = sc, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(job.status.replace("_", " ").replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold, color = sc)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = sc, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        PriorityBadge(job.priority)
                    }
                }
            }
            // Earnings pending-review banner (gate). Approvers (owner/admin) get the
            // release button; everyone else sees it as informational.
            if (job.review_status == "pending_review") {
                item {
                    val canApproveEarnings = currentRole in listOf("owner", "admin")
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        color = AppColors.Orange.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.HourglassEmpty, null, tint = AppColors.Orange, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Earnings pending review", fontWeight = FontWeight.SemiBold, color = AppColors.Orange)
                                Text("Completed by a team member. Earnings are held until an owner or admin approves them.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (canApproveEarnings) {
                                TextButton(
                                    onClick = { vm.approveEarnings(jobId) },
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Text("Approve", color = AppColors.Orange, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            // Partner banner
            if (job.sent_by_company_name != null) {
                item {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        color = AppColors.Green.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Inbox, null, tint = AppColors.Green, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Received from ${job.sent_by_company_name}", fontWeight = FontWeight.SemiBold, color = AppColors.Green)
                                if (job.partner_status == "pending") {
                                    Text("Awaiting confirmation from sender", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else if (job.sent_to_company_name != null) {
                item {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        color = AppColors.Blue.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Share, null, tint = AppColors.Blue, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Sent to ${job.sent_to_company_name}", fontWeight = FontWeight.SemiBold, color = AppColors.Blue)
                                job.partner_status?.let { ps ->
                                    Text("Status: ${ps.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (job.partner_status == "pending") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { vm.confirmPartnerStatus(jobId, "confirm") },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text("Confirm", color = AppColors.Green, style = MaterialTheme.typography.labelMedium)
                                    }
                                    TextButton(onClick = { vm.confirmPartnerStatus(jobId, "dispute") },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text("Dispute", color = AppColors.Red, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Source badge
            if (job.source_type != null) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val (icon, label) = when (job.source_type) {
                            "network"          -> Icons.Default.Handshake to "Network · ${job.sent_by_company_name ?: "Partner"}"
                            "external_contact" -> Icons.Default.Person to "${job.job_source_name ?: "Source contact"}"
                            "own_company"      -> Icons.Default.Campaign to "${job.ad_channel_name ?: job.ad_channel_custom ?: "Own Company"}"
                            else               -> Icons.Default.Campaign to "Source"
                        }
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!isTech) {
                            Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (job.resolved_commission_pct != null) {
                                Text("Commission: ${job.resolved_commission_pct.toInt()}% (source rule)",
                                    style = MaterialTheme.typography.bodySmall, color = AppColors.Purple)
                            } else {
                                Text("Commission: default rate",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            // Membership banner
            if (job.membership_id != null) {
                item {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        color = AppColors.Purple.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Autorenew, null, tint = AppColors.Purple, modifier = Modifier.size(18.dp))
                            Text("Recurring Membership Job", fontWeight = FontWeight.SemiBold, color = AppColors.Purple)
                        }
                    }
                }
            }
            // Completion status banner
            if (job.status == "completed" && completion != null) {
                item {
                    when (completion.status) {
                        "confirmed" -> Surface(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            color = AppColors.Green.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, null, tint = AppColors.Green, modifier = Modifier.size(18.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Job completion confirmed", fontWeight = FontWeight.SemiBold, color = AppColors.Green)
                                    if (completion.sender_earns > 0 || completion.receiver_earns > 0) {
                                        Text("Your share: ${formatMoney(
                                            if (job.sent_by_company_id == null) completion.sender_earns else completion.receiver_earns
                                        )}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                TextButton(onClick = { showCompletionSheet = true }) { Text("Details") }
                            }
                        }
                        else -> Surface(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { showCompletionSheet = true },
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.HourglassEmpty, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                                Column(Modifier.weight(1f)) {
                                    val partnerName = job.sent_to_company_name ?: job.sent_by_company_name
                                    Text(
                                        if (partnerName != null) "Awaiting confirmation from $partnerName" else "Completion pending review",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text("Tap to review or confirm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { CRMCard {
                Text("Job Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                // Row 1: Reminder (left) + Scheduled date/time (right)
                if (job.scheduled_start != null) {
                    var reminderDropdownExpanded by remember { mutableStateOf(false) }
                    val reminderOptions = listOf("default" to "Default", "email" to "Email", "sms" to "SMS", "both" to "Both", "none" to "None")
                    var selectedReminder by remember(job.reminder_method) { mutableStateOf(job.reminder_method) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        if (job.reminder_sent_at == null) {
                            Box {
                                OutlinedButton(
                                    onClick = { reminderDropdownExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(reminderOptions.first { it.first == selectedReminder }.second, style = MaterialTheme.typography.bodySmall)
                                    Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(
                                    expanded = reminderDropdownExpanded,
                                    onDismissRequest = { reminderDropdownExpanded = false }
                                ) {
                                    reminderOptions.forEach { (value, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                selectedReminder = value
                                                reminderDropdownExpanded = false
                                                vm.updateReminderMethod(job.id, value)
                                            },
                                            leadingIcon = if (value == selectedReminder) {{
                                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                            }} else null
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(14.dp), tint = AppColors.Green)
                                Spacer(Modifier.width(4.dp))
                                Text("Reminder Sent", style = MaterialTheme.typography.bodySmall, color = AppColors.Green)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatJobInstant(job.scheduled_start, job.effective_timezone, "MMM d, yyyy"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            // P2.19: arrival window when a distinct end exists.
                            val jobTimeText = job.scheduled_end?.takeIf { it != job.scheduled_start }?.let { end ->
                                "${formatJobInstant(job.scheduled_start, job.effective_timezone, "h:mm a")} – ${formatJobInstant(end, job.effective_timezone, "h:mm a zzz")}"
                            } ?: formatJobInstant(job.scheduled_start, job.effective_timezone, "h:mm a zzz")
                            Text(jobTimeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                // Row 2: Source | Type | Assigned (three-column)
                // P2.1b: mirror web (JobDetail.jsx) — show the assigned user (tech_first/last)
                // XOR the roster tech (flat roster_tech_name from the GET /jobs/:id JOIN);
                // no dependency on state.rosterTechs being loaded.
                val techDisplayName = job.techName ?: job.roster_tech_name
                Row(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Source", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            // P2.1c: mirror web (JobDetail.jsx) — own-company jobs have no
                            // job_source_name/ad_channel_name; show "My Company", never blank.
                            job.job_source_name ?: job.ad_channel_name ?: "My Company",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Box(Modifier.width(1.dp).height(36.dp).align(Alignment.CenterVertically).background(MaterialTheme.colorScheme.outlineVariant))
                    Column(
                        Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Type", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            job.type?.replaceFirstChar { it.uppercase() } ?: "—",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    Box(Modifier.width(1.dp).height(36.dp).align(Alignment.CenterVertically).background(MaterialTheme.colorScheme.outlineVariant))
                    Column(
                        Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Assigned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            techDisplayName ?: "Unassigned",   // mirror web; blank only if both null
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            } }
            item { CRMCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Customer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { onCustomer(job.customer_id) }) { Text("View") }
                }
                InfoRow(Icons.Default.Person, "Name", job.customerName)
                job.cust_phone?.let { ph ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(ph, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$ph"))) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Phone, null, tint = AppColors.Blue, modifier = Modifier.size(17.dp))
                        }
                    }
                }
                job.cust_email?.let { em ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(em, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$em"))) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Email, null, tint = AppColors.Blue, modifier = Modifier.size(17.dp))
                        }
                    }
                }
                job.custFullAddress.takeIf { it.isNotBlank() }?.let { InfoRow(Icons.Default.LocationOn, "Address", it) }
            } }
            // Linked job card (go-back / follow-up reference)
            if (job.linked_job_id != null) {
                item { CRMCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Linked Job", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { onLinkedJob(job.linked_job_id) }) { Text("View") }
                    }
                    job.linked_job_number?.let { InfoRow(Icons.Default.ConfirmationNumber, "Job #", it) }
                    job.linked_job_title?.let  { InfoRow(Icons.Default.Work,               "Title", it) }
                } }
            }
            job.fullAddress.takeIf { it.isNotBlank() }?.let { addr ->
                item { CRMCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Job Site", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = {
                            val encoded = Uri.encode(addr)
                            val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded"))
                            navIntent.setPackage("com.google.android.apps.maps")
                            if (navIntent.resolveActivity(ctx.packageManager) != null) ctx.startActivity(navIntent)
                            else ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")))
                        }) { Icon(Icons.Default.Navigation, null, tint = AppColors.Blue) }
                    }
                    InfoRow(Icons.Default.LocationOn, "Address", addr)
                } }
            }
            if (job.line_items.isNotEmpty()) {
                item { CRMCard {
                    Text("Line Items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    job.line_items.filter { li -> li.name.isNotBlank() }.forEach { li ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(li.name, fontWeight = FontWeight.Medium)
                                Text("${formatQty(li.quantity)} x ${formatMoney(li.unit_price)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatMoney(li.total), fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider()
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", fontWeight = FontWeight.Bold)
                        Text(formatMoney(job.subtotal ?: 0.0), fontWeight = FontWeight.Bold, color = AppColors.Blue)
                    }
                } }
            }
            // TASK 2A: Editable notes field, auto-save on focus lost
            item { CRMCard {
                Text("Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { fs ->
                        if (!fs.isFocused && notesText != savedNotes) {
                            savedNotes = notesText
                            vm.updateNotes(jobId, notesText)
                        }
                    },
                    placeholder = { Text("Add notes…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    minLines = 3,
                    shape = RoundedCornerShape(10.dp)
                )
            } }
            // Tech Permissions card (only show when this is a received job)
            if (job.sent_by_company_id != null && job.tech_permissions.isNotEmpty()) {
                item { CRMCard {
                    Text("Your Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    val permLabels = mapOf(
                        "collect_payments" to "Collect Payments",
                        "add_notes"        to "Add Notes",
                        "take_photos"      to "Take Photos",
                        "edit_details"     to "Edit Job Details",
                        "cancel_job"       to "Cancel Job",
                        "view_history"     to "View Job History"
                    )
                    permLabels.forEach { (key, label) ->
                        val allowed = job.tech_permissions[key] == true
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (allowed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                null,
                                tint = if (allowed) AppColors.Green else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(label, style = MaterialTheme.typography.bodySmall, color = if (allowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                } }
            }

            // Tech Permissions management (sender / owner of the job)
            if (job.sent_to_company_id != null && job.sent_by_company_id == null) {
                item { CRMCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Partner Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showTechPerms = true }) { Text("Edit") }
                    }
                    val permLabels = mapOf(
                        "collect_payments" to "Collect Payments",
                        "add_notes"        to "Add Notes",
                        "take_photos"      to "Take Photos",
                        "edit_details"     to "Edit Job Details",
                        "cancel_job"       to "Cancel Job",
                        "view_history"     to "View Job History"
                    )
                    permLabels.forEach { (key, label) ->
                        val allowed = job.tech_permissions[key] == true
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (allowed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                null,
                                tint = if (allowed) AppColors.Green else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } }
            }

            // Actions
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Send To — unified: roster tech, app user, partner
                    if (job.status !in listOf("deleted", "cancelled")) {
                        OutlinedButton(
                            onClick = { vm.loadSendToRecipients(job); showSendToDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Send To")
                        }
                    }
                    if (jobEstimates.isEmpty()) {
                        // No estimates yet — single create button
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onCreateEstimate, Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Default.Description, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Create Estimate")
                            }
                            if (jobInvoice != null) {
                                OutlinedButton(onClick = { onViewInvoice(jobInvoice.id) }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                                    Icon(Icons.Default.Receipt, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("View Invoice")
                                }
                            }
                        }
                    } else {
                        // One or more estimates — show cards + add-another button
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            jobEstimates.forEach { est ->
                                val estStatusColor = when (est.status) {
                                    "approved" -> AppColors.Green
                                    "sent"     -> AppColors.Blue
                                    "declined" -> AppColors.Red
                                    else       -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                OutlinedCard(
                                    onClick  = { onViewEstimate(est.id) },
                                    shape    = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.Description, null, Modifier.size(15.dp), tint = AppColors.Blue)
                                            Text(est.estimate_number, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(formatMoney(est.total), fontWeight = FontWeight.SemiBold, color = AppColors.Blue, style = MaterialTheme.typography.bodyMedium)
                                            Surface(shape = RoundedCornerShape(4.dp), color = estStatusColor.copy(alpha = 0.12f)) {
                                                Text(
                                                    est.status.replaceFirstChar { it.uppercase() },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style    = MaterialTheme.typography.labelSmall,
                                                    color    = estStatusColor,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = onCreateEstimate) {
                                    Icon(Icons.Default.Add, null, Modifier.size(14.dp), tint = AppColors.Blue)
                                    Spacer(Modifier.width(3.dp))
                                    Text("+ Add Another Estimate", style = MaterialTheme.typography.bodySmall, color = AppColors.Blue)
                                }
                            }
                            if (jobInvoice != null) {
                                OutlinedButton(onClick = { onViewInvoice(jobInvoice.id) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                                    Icon(Icons.Default.Receipt, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("View Invoice")
                                }
                            }
                        }
                    }
                    // Add to Invoice button
                    if (job.status !in listOf("deleted", "cancelled")) {
                        if (jobInvoice == null) {
                            OutlinedButton(
                                onClick = { vm.createJobInvoice(jobId, job.customer_id) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Receipt, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add to Invoice")
                            }
                        }
                    }
                }
            }
            // Before/After photos — side by side
            item { CRMCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Before column
                    Column(Modifier.weight(1f)) {
                        Text("Before", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            beforePhotos.forEach { photo ->
                                Box(Modifier.fillMaxWidth().aspectRatio(1f).clickable { viewerPhoto = photo }) {
                                    AsyncImage(model = photo.url, contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop)
                                    IconButton(onClick = { vm.deletePhoto(jobId, photo.filename) },
                                        modifier = Modifier.size(24.dp).align(Alignment.TopEnd)
                                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)) {
                                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                            pendingBeforeUri?.let { uri ->
                                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                    AsyncImage(model = uri, contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop)
                                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(onClick = { photoTarget = "before_photo"; launchCamera() },
                                    modifier = Modifier.weight(1f).height(44.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))) {
                                    Icon(Icons.Default.PhotoCamera, "Camera", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { photoTarget = "before_photo"; launchGallery() },
                                    modifier = Modifier.weight(1f).height(44.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))) {
                                    Icon(Icons.Default.Photo, "Gallery", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    // After column
                    Column(Modifier.weight(1f)) {
                        Text("After", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            afterPhotos.forEach { photo ->
                                Box(Modifier.fillMaxWidth().aspectRatio(1f).clickable { viewerPhoto = photo }) {
                                    AsyncImage(model = photo.url, contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop)
                                    IconButton(onClick = { vm.deletePhoto(jobId, photo.filename) },
                                        modifier = Modifier.size(24.dp).align(Alignment.TopEnd)
                                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)) {
                                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                            pendingAfterUri?.let { uri ->
                                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                    AsyncImage(model = uri, contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop)
                                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(onClick = { photoTarget = "after_photo"; launchCamera() },
                                    modifier = Modifier.weight(1f).height(44.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))) {
                                    Icon(Icons.Default.PhotoCamera, "Camera", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { photoTarget = "after_photo"; launchGallery() },
                                    modifier = Modifier.weight(1f).height(44.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))) {
                                    Icon(Icons.Default.Photo, "Gallery", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } }
            // Parts section
            item { CRMCard {
                Text("Parts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                if (state.parts.isEmpty()) {
                    Text("No parts added yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.parts.forEach { part ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(part.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "${formatMoney(part.cost)} · ${if (part.provider == "tech") "Tech pays" else "Company pays"}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { vm.deletePart(jobId, part.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            } }
            // Parts + Charge Payment row
            if (job.status !in listOf("deleted", "cancelled")) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showAddPartDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Part", style = MaterialTheme.typography.labelMedium)
                        }
                        if (jobInvoice != null && jobInvoice.status != "paid") {
                            OutlinedButton(
                                onClick = { onPayment(jobInvoice.id) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AppColors.Green),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Green)
                            ) {
                                Icon(Icons.Default.Payment, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Charge Payment", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            // Bottom action row
            if (job.status !in listOf("deleted", "cancelled", "completed")) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { jobInvoice?.let { onViewInvoice(it.id) } },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            enabled = jobInvoice != null,
                            border = BorderStroke(1.dp, if (jobInvoice != null) AppColors.Blue else MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Blue)
                        ) { Text("Send Receipt", style = MaterialTheme.typography.labelMedium) }
                        OutlinedButton(
                            onClick = { vm.updateStatus(jobId, "cancelled") },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AppColors.Red),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Red)
                        ) { Text("Cancel Job", style = MaterialTheme.typography.labelMedium) }
                        Button(
                            onClick = onComplete,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green)
                        ) { Text("Completed", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            }   // end else -> LazyColumn (Details tab)
        }   // end when (selectedTab)
        }   // end Column
    }   // end Scaffold content
    // History photo viewer
    historyViewerUrl?.let { url ->
        Dialog(
            onDismissRequest = { historyViewerUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(model = url, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                IconButton(
                    onClick  = { historyViewerUrl = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp)
                        .size(40.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
        }
    }
    // Arrived confirmation dialog
    if (showArrivedConfirm && job != null) {
        AlertDialog(
            onDismissRequest = { showArrivedConfirm = false },
            title = { Text("Mark as Arrived?") },
            text  = { Text("This will notify ${job.customerName} that you have arrived and update the job status to In Progress.") },
            confirmButton = {
                TextButton(onClick = {
                    showArrivedConfirm = false
                    vm.arrivedJob(jobId)
                }) { Text("Arrived", color = AppColors.Green) }
            },
            dismissButton = {
                TextButton(onClick = { showArrivedConfirm = false }) { Text("Cancel") }
            }
        )
    }
    // Add Part dialog
    if (showAddPartDialog && job != null) {
        var partName     by remember { mutableStateOf("") }
        var partCost     by remember { mutableStateOf("") }
        var partProvider by remember { mutableStateOf("company") }
        AlertDialog(
            onDismissRequest = { showAddPartDialog = false },
            title = { Text("Add Part", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(partName, { partName = it }, label = { Text("Part Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                    OutlinedTextField(
                        partCost, { partCost = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Cost (\$)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("company" to "Company", "tech" to "Tech").forEach { (val_, label) ->
                            FilterChip(
                                selected = partProvider == val_,
                                onClick  = { partProvider = val_ },
                                label    = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (partName.isNotBlank()) {
                            vm.savePart(jobId, partName, partCost.toDoubleOrNull() ?: 0.0, partProvider)
                            showAddPartDialog = false
                        }
                    },
                    enabled = partName.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddPartDialog = false }) { Text("Cancel") } }
        )
    }
    // Dispatch confirmation dialog
    if (showDispatchConfirm && job != null) {
        AlertDialog(
            onDismissRequest = { showDispatchConfirm = false },
            title = { Text("Dispatch to ${job.customerName}?") },
            text  = { Text("This will notify the customer you're on your way and update the job status to En Route.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDispatchConfirm = false
                        vm.dispatchJob(jobId, ctx)
                    }
                ) { Text("Dispatch") }
            },
            dismissButton = {
                TextButton(onClick = { showDispatchConfirm = false }) { Text("Cancel") }
            }
        )
    }
    // Status bottom sheet
    if (showStatus && job != null) {
        val statuses = listOf("unscheduled", "scheduled", "en_route", "in_progress", "holding", "completed", "cancelled")
        ModalBottomSheet(onDismissRequest = { showStatus = false }) {
            SheetHandle()
            Text("Update Status", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            statuses.forEach { s ->
                val c = AppColors.jobStatus(s)
                ListItem(
                    headlineContent = { Text(s.replace("_", " ").replaceFirstChar { it.uppercase() }, fontWeight = if (s == job.status) FontWeight.Bold else FontWeight.Normal) },
                    leadingContent = { Icon(Icons.Default.Circle, null, tint = c, modifier = Modifier.size(10.dp)) },
                    trailingContent = { if (s == job.status) Icon(Icons.Default.Check, null, tint = AppColors.Green) },
                    modifier = Modifier.clickable {
                        showStatus = false
                        if (s == "completed") {
                            // Navigate to the complete job flow
                            onComplete()
                        } else {
                            vm.updateStatus(jobId, s)
                        }
                    }
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    // TASK 2B: Process payment prompt when job is marked completed
    if (showPaymentPrompt && jobInvoice != null) {
        AlertDialog(
            onDismissRequest = { showPaymentPrompt = false },
            title = { Text("Process Payment?") },
            text = { Text("Job marked as completed. Would you like to charge payment now?") },
            confirmButton = {
                Button(onClick = { showPaymentPrompt = false; onPayment(jobInvoice.id) }) {
                    Text("Process Payment")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentPrompt = false }) { Text("Later") }
            }
        )
    }
    // Archive confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Archive this job?") },
            text  = { Text("The job will be moved to deleted jobs and can be retrieved from job search. Estimates and invoices will be kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        vm.deleteJob(jobId)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Red)
                ) { Text("Archive") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    // Send To dialog (unified: roster tech, app user, partner)
    if (showSendToDialog && job != null) {
        SendToDialog(
            recipients  = state.sendToRecipients,
            loading     = state.sendToLoading,
            onNotifyRosterTech = { recipientId, method, name ->
                vm.notifyRosterTech(jobId, recipientId, method, name)
                showSendToDialog = false
            },
            onNotifyAppUser = { recipientId, name ->
                vm.notifyAppUserTech(jobId, recipientId, name)
                showSendToDialog = false
            },
            onSendToPartner = { partnerId ->
                val fullPerms = mapOf(
                    "collect_payments" to true, "add_notes" to true, "take_photos" to true,
                    "edit_details" to true, "cancel_job" to true, "view_history" to true
                )
                vm.sendJobToPartner(jobId, partnerId, fullPerms)
                showSendToDialog = false
            },
            onDismiss = { showSendToDialog = false }
        )
    }

    // Tech Permissions editor (owner only)
    if (showTechPerms && job != null) {
        TechPermissionsSheet(
            current   = job.tech_permissions,
            onSave    = { perms ->
                vm.updateJob(jobId, mapOf("tech_permissions" to perms)) {}
                showTechPerms = false
            },
            onDismiss = { showTechPerms = false }
        )
    }

    // Completion details / confirm sheet
    if (showCompletionSheet && completion != null && job != null) {
        CompletionDetailsSheet(
            completion = completion,
            isSender   = job.sent_by_company_id == null, // owner is sender
            isPartnerJob = job.agreement_id != null,
            onConfirm  = { vm.confirmJobCompletion(jobId); showCompletionSheet = false },
            onDismiss  = { showCompletionSheet = false }
        )
    }

    // Full-screen photo viewer — tap any thumbnail to open
    viewerPhoto?.let { photo ->
        Dialog(
            onDismissRequest = { viewerPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = photo.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { viewerPhoto = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }
        }
    }
}

// ── Customer History Tab ───────────────────────────────────────────────────

@Composable
private fun CustomerHistoryTab(
    history:    CustomerHistory?,
    loading:    Boolean,
    onJob:      (String) -> Unit,
    onEstimate: (String) -> Unit,
    onInvoice:  (String) -> Unit,
    onPhoto:    (String) -> Unit
) {
    if (loading && history == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val h = history ?: com.ultimatepro.domain.model.CustomerHistory()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Previous Jobs ──────────────────────────────────────────────────
        item {
            HistorySection("Previous Jobs", Icons.Default.Work, h.jobs.size) {
                if (h.jobs.isEmpty()) {
                    Text("None on record", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    h.jobs.forEach { job ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onJob(job.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${job.jobNumber}  ${job.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                job.address?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                job.scheduledStart?.take(10)?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            val sc = AppColors.jobStatus(job.status)
                            Surface(shape = RoundedCornerShape(20.dp), color = sc.copy(alpha = 0.13f)) {
                                Text(job.status.replace("_", " ").replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sc, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (job != h.jobs.last()) HorizontalDivider()
                    }
                }
            }
        }

        // ── Previous Estimates ─────────────────────────────────────────────
        item {
            HistorySection("Previous Estimates", Icons.Default.Description, h.estimates.size) {
                if (h.estimates.isEmpty()) {
                    Text("None on record", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    h.estimates.forEach { est ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onEstimate(est.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(est.estimateNumber, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                est.createdAt?.take(10)?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val estColor = AppColors.invoiceStatus(est.status)
                            Surface(shape = RoundedCornerShape(4.dp), color = estColor.copy(alpha = 0.13f)) {
                                Text(est.status.replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = estColor, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(formatMoney(est.total), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold, color = AppColors.Blue)
                        }
                        if (est != h.estimates.last()) HorizontalDivider()
                    }
                }
            }
        }

        // ── Previous Invoices ──────────────────────────────────────────────
        item {
            HistorySection("Previous Invoices", Icons.Default.Receipt, h.invoices.size) {
                if (h.invoices.isEmpty()) {
                    Text("None on record", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    h.invoices.forEach { inv ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onInvoice(inv.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(inv.invoiceNumber, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                inv.createdAt?.take(10)?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val invColor = AppColors.invoiceStatus(inv.status)
                            Surface(shape = RoundedCornerShape(4.dp), color = invColor.copy(alpha = 0.13f)) {
                                Text(inv.status.replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = invColor, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(formatMoney(inv.total), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold, color = AppColors.Blue)
                        }
                        if (inv != h.invoices.last()) HorizontalDivider()
                    }
                }
            }
        }

        // ── Notes from past jobs ───────────────────────────────────────────
        item {
            HistorySection("Notes from Past Jobs", Icons.Default.Notes, h.notes.size) {
                if (h.notes.isEmpty()) {
                    Text("None on record", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    h.notes.forEach { note ->
                        var expanded by remember { mutableStateOf(false) }
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                .clickable { expanded = !expanded }
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                note.jobNumber?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall,
                                        color = AppColors.Blue, fontWeight = FontWeight.SemiBold)
                                }
                                note.createdAt?.take(10)?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                text     = note.content,
                                style    = MaterialTheme.typography.bodySmall,
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                overflow = if (expanded) androidx.compose.ui.text.style.TextOverflow.Clip
                                           else androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (note != h.notes.last()) HorizontalDivider()
                    }
                }
            }
        }

        // ── Photos from past jobs ──────────────────────────────────────────
        item {
            HistorySection("Photos from Past Jobs", Icons.Default.PhotoLibrary, h.photos.size) {
                if (h.photos.isEmpty()) {
                    Text("None on record", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    val rows = h.photos.chunked(3)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        rows.forEach { rowPhotos ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                rowPhotos.forEach { photo ->
                                    AsyncImage(
                                        model              = photo.url,
                                        contentDescription = null,
                                        modifier           = Modifier.weight(1f).aspectRatio(1f)
                                            .clip(RoundedCornerShape(6.dp)).clickable { onPhoto(photo.url) },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                repeat(3 - rowPhotos.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySection(
    title:   String,
    icon:    androidx.compose.ui.graphics.vector.ImageVector,
    count:   Int,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    CRMCard {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = AppColors.Blue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(20.dp), color = AppColors.Blue.copy(alpha = 0.12f)) {
                    Text("$count",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.Blue, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null, modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    content = content
                )
            }
        }
    }
}

// ── Send To Dialog (unified: roster tech, app user, partner) ──────────────
@Composable
private fun SendToDialog(
    recipients:         List<SendToRecipient>,
    loading:            Boolean,
    onNotifyRosterTech: (id: String, method: String, name: String) -> Unit,
    onNotifyAppUser:    (id: String, name: String) -> Unit,
    onSendToPartner:    (partnerId: String) -> Unit,
    onDismiss:          () -> Unit
) {
    var selected by remember { mutableStateOf<SendToRecipient?>(null) }
    var method   by remember { mutableStateOf("sms") }

    val techRecipients    = recipients.filter { it.type in listOf("roster_tech", "app_user") }
    val partnerRecipients = recipients.filter { it.type == "partner" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send To") },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (recipients.isEmpty()) {
                Text(
                    "No recipients available. Assign a technician or connect with a partner first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (techRecipients.isNotEmpty()) {
                        Text(
                            "TECHNICIANS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        techRecipients.forEach { r ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        selected = r
                                        method = if (r.type == "roster_tech") {
                                            if (r.phone != null) "sms" else "email"
                                        } else "push"
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected?.id == r.id,
                                    onClick  = {
                                        selected = r
                                        method = if (r.type == "roster_tech") {
                                            if (r.phone != null) "sms" else "email"
                                        } else "push"
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(r.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (r.type == "roster_tech") "Roster Tech" else "App User",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (partnerRecipients.isNotEmpty()) {
                        if (techRecipients.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(
                            "NETWORK PARTNERS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        partnerRecipients.forEach { r ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { selected = r; method = "push" }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected?.id == r.id,
                                    onClick  = { selected = r; method = "push" }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(r.name, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Notification method for selected recipient
                    selected?.let { sel ->
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        when (sel.type) {
                            "roster_tech" -> {
                                Text(
                                    "Notify via",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (sel.phone != null) {
                                        FilterChip(
                                            selected = method == "sms",
                                            onClick  = { method = "sms" },
                                            label    = { Text("SMS") }
                                        )
                                    }
                                    if (sel.email != null) {
                                        FilterChip(
                                            selected = method == "email",
                                            onClick  = { method = "email" },
                                            label    = { Text("Email") }
                                        )
                                    }
                                    if (sel.phone != null && sel.email != null) {
                                        FilterChip(
                                            selected = method == "both",
                                            onClick  = { method = "both" },
                                            label    = { Text("Both") }
                                        )
                                    }
                                }
                            }
                            else -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Notifications, null, Modifier.size(16.dp), tint = AppColors.Blue)
                                    Spacer(Modifier.width(8.dp))
                                    Text("App Notification", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sel = selected ?: return@Button
                    when (sel.type) {
                        "roster_tech" -> onNotifyRosterTech(sel.id, method, sel.name)
                        "app_user"    -> onNotifyAppUser(sel.id, sel.name)
                        "partner"     -> onSendToPartner(sel.id)
                    }
                },
                enabled = selected != null
            ) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Tech Permissions Sheet (owner edits) ───────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TechPermissionsSheet(
    current:  Map<String, Boolean>,
    onSave:   (Map<String, Boolean>) -> Unit,
    onDismiss: () -> Unit
) {
    val permKeys = listOf("collect_payments", "add_notes", "take_photos", "edit_details", "cancel_job", "view_history")
    val permLabels = mapOf(
        "collect_payments" to "Collect Payments",
        "add_notes"        to "Add Notes",
        "take_photos"      to "Take Photos",
        "edit_details"     to "Edit Job Details",
        "cancel_job"       to "Cancel Job",
        "view_history"     to "View Job History"
    )
    val perms = remember { mutableStateMapOf<String, Boolean>().also { m -> permKeys.forEach { m[it] = if (it == "view_history") current[it] != false else current[it] == true } } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHandle()
        Text("Partner Permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text("Control what the partner company can do with this job.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        Spacer(Modifier.height(8.dp))
        permKeys.forEach { key ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(permLabels[key] ?: key, Modifier.weight(1f))
                AppSwitch(checked = perms[key] == true, onCheckedChange = { perms[key] = it })
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick  = { onSave(perms.toMap()) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape    = RoundedCornerShape(10.dp)
        ) { Text("Save Permissions") }
        Spacer(Modifier.height(32.dp))
    }
}

// ── Completion Details Sheet ───────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompletionDetailsSheet(
    completion:   JobCompletionDetails,
    isSender:     Boolean,
    isPartnerJob: Boolean,
    onConfirm:    () -> Unit,
    onDismiss:    () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHandle()
        Text("Completion Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            if (isPartnerJob) {
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        @Composable fun row(label: String, value: String) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                        }
                        if (completion.parts_amount > 0) {
                            row("Parts (${completion.parts_paid_by ?: "—"})", "-${formatMoney(completion.parts_amount)}")
                        }
                        if (completion.cc_fee_amount > 0) {
                            row("CC Fee (${completion.cc_fee_paid_by ?: "—"})", "-${formatMoney(completion.cc_fee_amount)}")
                        }
                        HorizontalDivider()
                        row("Net after deductions", formatMoney(completion.net_after_deductions))
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Sender earns", fontWeight = FontWeight.SemiBold)
                            Text(formatMoney(completion.sender_earns), fontWeight = FontWeight.SemiBold, color = AppColors.Blue)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Receiver earns", fontWeight = FontWeight.SemiBold)
                            Text(formatMoney(completion.receiver_earns), fontWeight = FontWeight.SemiBold, color = AppColors.Green)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                completion.submitterName?.let {
                    Text("Submitted by: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                completion.confirmerName?.let {
                    Text("Confirmed by: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            completion.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text("Notes: $it", style = MaterialTheme.typography.bodySmall)
            }
            if (completion.status == "pending" && isSender && isPartnerJob) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onConfirm, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green)) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Confirm Completion")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Complete Job Screen ────────────────────────────────────────────────────
data class CompleteJobState(
    val loading:  Boolean = true,
    val saving:   Boolean = false,
    val job:      Job?    = null,
    val invoice:  com.ultimatepro.domain.model.Invoice? = null,
    val error:    String? = null,
    val saved:    Boolean = false
)

@HiltViewModel
class CompleteJobViewModel @Inject constructor(
    private val repo: CrmRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _s = MutableStateFlow(CompleteJobState())
    val state = _s.asStateFlow()

    fun load(jobId: String) {
        viewModelScope.launch {
            _s.update { it.copy(loading = true) }
            val job = repo.getJob(jobId)
            val invoices = repo.getInvoices(jobId = jobId)
            _s.update { it.copy(
                loading = false,
                job     = (job as? Result.Success)?.data,
                invoice = (invoices as? Result.Success)?.data?.invoices?.firstOrNull()
            ) }
        }
    }

    fun complete(jobId: String, body: Map<String, Any?>, onDone: () -> Unit) {
        viewModelScope.launch {
            _s.update { it.copy(saving = true, error = null) }
            when (val r = repo.completeJob(jobId, body)) {
                is Result.Success -> {
                    _s.update { it.copy(saving = false, saved = true) }
                    // Silent inventory deduction — backend handles it automatically in jobs/status
                    // but we also call explicit endpoint for any client-side parts
                    onDone()
                }
                is Result.Error   -> _s.update { it.copy(saving = false, error = r.message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteJobScreen(
    jobId:  String,
    onBack: () -> Unit,
    vm:     CompleteJobViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(jobId) { vm.load(jobId) }

    val job     = state.job
    val invoice = state.invoice
    val invoiceTotal = invoice?.total ?: 0.0
    val isPartnerJob = job?.agreement_id != null

    // Form state — partner fields
    var partsPaidBy        by remember { mutableStateOf("none") }        // sender|receiver|none
    var partsAmount        by remember { mutableStateOf("") }
    var paymentCollectedBy by remember { mutableStateOf("sender") }      // sender|receiver
    var hasCcFee           by remember { mutableStateOf(false) }
    var ccFeeAmount        by remember { mutableStateOf("") }
    var ccFeePaidBy        by remember { mutableStateOf("split") }       // sender|receiver|split
    var completionNotes    by remember { mutableStateOf("") }

    // Live calculation
    val partsAmt  = partsAmount.toDoubleOrNull()  ?: 0.0
    val ccAmt     = if (hasCcFee) ccFeeAmount.toDoubleOrNull() ?: 0.0 else 0.0
    val net       = invoiceTotal - partsAmt - ccAmt
    val senderPct = job?.sender_keeps_pct   ?: 0.0
    val receiverPct = job?.receiver_keeps_pct ?: 0.0
    var senderEarns   = net * (senderPct   / 100)
    var receiverEarns = net * (receiverPct / 100)
    if (hasCcFee) when (ccFeePaidBy) {
        "split"    -> { senderEarns -= ccAmt / 2; receiverEarns -= ccAmt / 2 }
        "sender"   -> senderEarns   -= ccAmt
        "receiver" -> receiverEarns -= ccAmt
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Complete Job", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (state.loading) { LoadingView(); return@Scaffold }
        if (job == null) { ErrorView("Job not found", onRetry = { vm.load(jobId) }); return@Scaffold }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Job summary card
            CRMCard {
                Text("Job Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                InfoRow(Icons.Default.ConfirmationNumber, "Job #", job.job_number)
                InfoRow(Icons.Default.Person, "Customer", job.customerName)
                job.fullAddress.takeIf { it.isNotBlank() }?.let { InfoRow(Icons.Default.LocationOn, "Address", it) }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Invoice Total", fontWeight = FontWeight.Medium)
                    if (invoice != null)
                        Text(formatMoney(invoiceTotal), fontWeight = FontWeight.Bold, color = AppColors.Blue)
                    else
                        Text("No invoice found", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                }
            }

            if (isPartnerJob) {
                // ── PARTS ────────────────────────────────────────────────
                CRMCard {
                    Text("Parts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Who provided parts?", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("sender" to "Company", "receiver" to "Technician", "none" to "No Parts").forEach { (key, label) ->
                            FilterChip(selected = partsPaidBy == key, onClick = { partsPaidBy = key }, label = { Text(label) })
                        }
                    }
                    AnimatedVisibility(partsPaidBy != "none") {
                        OutlinedTextField(
                            value = partsAmount,
                            onValueChange = { partsAmount = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Parts Amount") },
                            prefix = { Text("$") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // ── PAYMENT ───────────────────────────────────────────────
                CRMCard {
                    Text("Payment Collection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Who collected payment?", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("sender" to "Sender", "receiver" to "Receiver").forEach { (key, label) ->
                            FilterChip(selected = paymentCollectedBy == key, onClick = { paymentCollectedBy = key }, label = { Text(label) })
                        }
                    }
                }

                // ── CC FEE ────────────────────────────────────────────────
                CRMCard {
                    Text("Credit Card Fee", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("CC processing fee?", Modifier.weight(1f))
                        AppSwitch(checked = hasCcFee, onCheckedChange = { hasCcFee = it })
                    }
                    AnimatedVisibility(hasCcFee) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = ccFeeAmount,
                                onValueChange = { ccFeeAmount = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("CC Fee Amount") },
                                prefix = { Text("$") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Text("Absorbed by:", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("sender" to "Sender", "receiver" to "Receiver", "split" to "Split 50/50").forEach { (key, label) ->
                                    FilterChip(selected = ccFeePaidBy == key, onClick = { ccFeePaidBy = key }, label = { Text(label) })
                                }
                            }
                        }
                    }
                }

                // ── LIVE CALCULATION ──────────────────────────────────────
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Blue.copy(alpha = 0.07f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Live Calculation", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = AppColors.Blue)
                        Spacer(Modifier.height(4.dp))
                        @Composable fun calcRow(label: String, value: String, bold: Boolean = false) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, style = if (bold) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                                    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = if (bold) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                                    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                        calcRow("Job Total", formatMoney(invoiceTotal))
                        if (partsAmt > 0) calcRow("- Parts (${if (partsPaidBy == "sender") "Company" else "Technician"})", "-${formatMoney(partsAmt)}")
                        if (ccAmt > 0) calcRow("- CC Fee (${ccFeePaidBy})", "-${formatMoney(ccAmt)}")
                        HorizontalDivider()
                        calcRow("Net", formatMoney(net), bold = true)
                        Spacer(Modifier.height(4.dp))
                        if (senderPct > 0) calcRow("Your share (${senderPct.toInt()}%)", formatMoney(senderEarns), bold = true)
                        if (receiverPct > 0) calcRow("Partner share (${receiverPct.toInt()}%)", formatMoney(receiverEarns), bold = true)
                    }
                }
            }

            // ── NOTES ─────────────────────────────────────────────────────
            CRMCard {
                Text("Completion Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = completionNotes,
                    onValueChange = { completionNotes = it },
                    placeholder = { Text("Completion notes…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // ── SUBMIT ────────────────────────────────────────────────────
            state.error?.let {
                Text(it, color = AppColors.Red, modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    val body = buildMap<String, Any?> {
                        if (isPartnerJob) {
                            put("parts_paid_by", partsPaidBy)
                            put("parts_amount", partsAmt)
                            put("payment_collected_by", paymentCollectedBy)
                            put("cc_fee_amount", ccAmt)
                            put("cc_fee_paid_by", ccFeePaidBy)
                        }
                        if (completionNotes.isNotBlank()) put("notes", completionNotes)
                    }
                    vm.complete(jobId, body) { onBack() }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape    = RoundedCornerShape(10.dp),
                enabled  = !state.saving
            ) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isPartnerJob) "Complete Job & Submit Split" else "Mark as Complete")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Job Form ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobFormScreen(onBack: () -> Unit, onSaved: () -> Unit, editJobId: String? = null, vm: JobViewModel = hiltViewModel(), sourceVm: com.ultimatepro.ui.settings.JobSourceViewModel = hiltViewModel()) {
    val state     by vm.state.collectAsState()
    val clipboard =  LocalClipboardManager.current

    // ── Core fields ────────────────────────────────────────────────────────
    var notes      by remember { mutableStateOf("") }
    var address    by remember { mutableStateOf("") }
    var city       by remember { mutableStateOf("") }
    var stateCode  by remember { mutableStateOf("") }
    var zip        by remember { mutableStateOf("") }
    var lat        by remember { mutableStateOf<Double?>(null) }
    var lng        by remember { mutableStateOf<Double?>(null) }
    var type       by remember { mutableStateOf("service") }
    var custName      by remember { mutableStateOf("") }
    var custPhone     by remember { mutableStateOf("") }
    var custEmail     by remember { mutableStateOf("") }
    var customerId    by remember { mutableStateOf<String?>(null) }
    var ticketNum     by remember { mutableStateOf("") }
    val extraCustPhones = remember { mutableStateListOf<String>() }
    val extraCustEmails = remember { mutableStateListOf<String>() }

    // ── Schedule ───────────────────────────────────────────────────────────
    var schedDate  by remember { mutableStateOf("") }
    var schedTime  by remember { mutableStateOf("") }
    var schedEndTime by remember { mutableStateOf("") }   // P2.19: optional arrival-window "to"
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = false)
    val endTimePickerState = rememberTimePickerState(initialHour = 10, initialMinute = 0, is24Hour = false)

    // ── Assignment ─────────────────────────────────────────────────────────
    var assignCat             by remember { mutableStateOf("self") }
    var assignedTo            by remember { mutableStateOf<String?>(null) }
    var assignedRosterTechId  by remember { mutableStateOf<String?>(null) }
    var showAssignDialog      by remember { mutableStateOf(false) }
    var linkedJobId           by remember { mutableStateOf<String?>(null) }
    var showDuplicateSheet by remember { mutableStateOf(false) }
    var autoSavePending    by remember { mutableStateOf(false) }

    // ── Notification toggles ───────────────────────────────────────────────
    var notifySms   by remember { mutableStateOf(true) }
    var notifyEmail by remember { mutableStateOf(false) }
    var notifyPush  by remember { mutableStateOf(false) }

    // ── Source ─────────────────────────────────────────────────────────────
    var selectedSourceKey  by remember { mutableStateOf("my_company") }
    var selectedSourceName by remember { mutableStateOf("My Company") }
    var sourceDropOpen by remember { mutableStateOf(false) }

    // Structured source tracking (resolved from unified dropdown)
    var sourceType       by remember { mutableStateOf<String?>("own_company") }
    var jobSourceId      by remember { mutableStateOf<String?>(null) }
    var adChannelId      by remember { mutableStateOf<String?>(null) }

    val contacts by sourceVm.contacts.collectAsState()
    val channels by sourceVm.channels.collectAsState()

    val types = listOf("service", "installation", "maintenance", "inspection", "repair", "emergency")

    // ── Load technicians + current user + source data once when form opens ──
    LaunchedEffect(Unit) { vm.loadTechs(); vm.loadRosterTechs(); vm.loadCurrentUser(); sourceVm.loadContacts(); sourceVm.loadChannels() }

    // ── Auto-set assignedTo when currentUser loads and category is "self" ──
    val currentUser = state.currentUser
    LaunchedEffect(currentUser?.id) {
        if (currentUser != null && assignCat == "self") {
            assignedTo = currentUser.id
        }
    }

    // ── Edit mode: reuse this redesigned form for editing (unified like web). Load the
    //    job once and prefill; the save path branches to updateJob. ──────────────────
    val isEdit = editJobId != null
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(editJobId) { if (editJobId != null) vm.loadJob(editJobId) }
    LaunchedEffect(state.selected, currentUser?.id) {
        if (isEdit && !initialized) {
            state.selected?.let { job ->
                if (job.id == editJobId) {
                    initialized = true
                    notes      = job.notes ?: ""
                    address    = job.address ?: ""; city = job.city ?: ""; stateCode = job.state ?: ""; zip = job.zip ?: ""
                    lat        = job.lat; lng = job.lng
                    type       = job.type
                    custName   = job.customerName; customerId = job.customer_id; custPhone = job.cust_phone ?: ""
                    assignedTo = job.assigned_to; assignedRosterTechId = job.assigned_roster_tech_id
                    assignCat  = when {
                        job.assigned_roster_tech_id != null -> "roster"
                        job.assigned_to != null && job.assigned_to == currentUser?.id -> "self"
                        job.assigned_to != null -> "team"
                        else -> "self"
                    }
                    job.scheduled_start?.let { start ->
                        schedDate = formatJobInstant(start, job.effective_timezone, "yyyy-MM-dd")
                        schedTime = formatJobInstant(start, job.effective_timezone, "HH:mm")
                        // P2.19: prefill the window "to" time when a distinct end exists.
                        job.scheduled_end?.takeIf { it != start }?.let { end ->
                            schedEndTime = formatJobInstant(end, job.effective_timezone, "HH:mm")
                        }
                    }
                    sourceType = job.source_type; jobSourceId = job.job_source_id; adChannelId = job.ad_channel_id
                    // P2.16 F2: resolve the dropdown label from the real source fields (job.source
                    // is null for ad-channel/contact jobs → it used to wrongly show "My Company").
                    // Mirrors the JobDetail source pill: job_source_name → ad_channel_name → My Company.
                    selectedSourceName = job.job_source_name ?: job.ad_channel_name ?: job.ad_channel_custom ?: "My Company"
                }
            }
        }
    }

    // ── Auto-fill all fields when ticket is parsed ─────────────────────────
    LaunchedEffect(state.parsedTicket) {
        state.parsedTicket?.let { t ->
            t.address?.let          { address   = it }
            t.city?.let             { city      = it }
            t.state?.let            { stateCode = it }
            t.zip?.let              { zip       = it }
            t.customerName?.let     { custName  = it }
            t.email?.let            { custEmail = it }
            t.ticketRef?.let        { ticketNum = it }
            t.scheduledDate?.let    { schedDate = it }
            t.scheduledTime?.let    { schedTime = it }
            t.source?.let           { selectedSourceName = it }
            // P2.1l Part B: only a PHONE match auto-attaches. A name-only match leaves
            // customerId null so dismissing the sheet defaults to Create New (the parsed
            // name/phone below still populate the form → a new customer is created on save).
            if (t.matchType == "phone") customerId = t.existingCustomerId

            // Multi-phone: primary goes in custPhone, extras collected for post-save
            val allPhones = t.phoneNumbers.ifEmpty { listOfNotNull(t.phone) }
            allPhones.firstOrNull()?.let { custPhone = it }
            extraCustPhones.clear()
            allPhones.drop(1).forEach { extraCustPhones.add(it) }

            // Notes = job description + any leftover info not captured by other fields
            notes = buildString {
                t.jobDescription?.takeIf { it.isNotBlank() }?.let { append(it) }
                t.leftoverNotes?.takeIf { it.isNotBlank() }?.let {
                    if (isNotBlank()) append("\n\n")
                    append(it)
                }
            }
        }
    }
    // ── Auto-dismiss success banner ────────────────────────────────────────
    LaunchedEffect(state.parsedSuccess) {
        if (state.parsedSuccess) { delay(3000L); vm.clearParsedSuccess() }
    }

    // ── Show duplicate sheet whenever VM detects a duplicate ───────────────
    LaunchedEffect(state.duplicateCustomer) {
        if (state.duplicateCustomer != null) {
            Log.d("DuplicateSheet", "showing sheet: ${state.duplicateCustomer?.customerName}")
            showDuplicateSheet = true
            autoSavePending    = false
        }
    }

    // ── Actual save — calls VM directly, customerId must already be resolved ─
    fun doSaveNow(forceCustomerId: String? = null, sendToTech: Boolean = false, forceNewCustomer: Boolean = false) {
        // forceNewCustomer (P2.21 "Create New") → resolve to null so a brand-new customer is
        // created from the parsed fields, never the matched duplicate (avoids state-timing).
        val resolvedCustomerId = if (forceNewCustomer) null else (forceCustomerId ?: customerId)
        Log.d("DuplicateSheet", "doSaveNow called with customerId: $resolvedCustomerId (forced=$forceCustomerId)")
        vm.clearError()
        // ── Edit mode: update the existing job. title/priority/line_items are intentionally
        //    OMITTED — the backend PUT COALESCEs missing columns (title/priority preserved) and
        //    only rewrites job_line_items when line_items is present (so parts are untouched).
        //    Assignment + schedule + source ARE sent so reassign/reschedule work. ──────────
        if (isEdit && editJobId != null) {
            val scheduledEdit = when {
                schedDate.isBlank() -> null
                else                -> "${schedDate}T${schedTime.ifBlank { "12:00" }}"
            }
            // P2.19: arrival-window end — only when date + start + end all set.
            val scheduledEndEdit = if (schedDate.isNotBlank() && schedTime.isNotBlank() && schedEndTime.isNotBlank())
                "${schedDate}T${schedEndTime}" else null
            val srcLabelEdit = buildString {
                if (selectedSourceName.isNotBlank() && selectedSourceName != "My Company") append(selectedSourceName)
                if (ticketNum.isNotBlank()) { if (isNotBlank()) append(" / "); append("Ticket #$ticketNum") }
            }
            vm.updateJob(editJobId, mapOf(
                "type"                    to type,
                "address"                 to address.ifBlank { null },
                "city"                    to city.ifBlank { null },
                "state"                   to stateCode.ifBlank { null },
                "zip"                     to zip.ifBlank { null },
                "notes"                   to notes.ifBlank { null },
                "scheduled_local"         to scheduledEdit,
                "scheduled_end_local"     to scheduledEndEdit,
                "lat"                     to lat,
                "lng"                     to lng,
                "assigned_to"             to assignedTo,
                "assigned_roster_tech_id" to assignedRosterTechId,
                "source"                  to srcLabelEdit.ifBlank { null },
                "source_type"             to sourceType,
                "job_source_id"           to jobSourceId,
                "ad_channel_id"           to adChannelId
            )) { onSaved() }
            return
        }
        val nameParts = custName.trim().split(" ", limit = 2)
        // TZ 3/3: send a naive wall-clock (YYYY-MM-DDTHH:mm) as scheduled_local + coords;
        // the backend resolves the address zone and converts to a UTC instant (Path B).
        // Date-only defaults to noon (mirrors web); blank date -> null (F2 preserved).
        val scheduled = when {
            schedDate.isBlank() -> null
            else                -> "${schedDate}T${schedTime.ifBlank { "12:00" }}"
        }
        // P2.19: arrival-window end — only when date + start + end all set.
        val scheduledEnd = if (schedDate.isNotBlank() && schedTime.isNotBlank() && schedEndTime.isNotBlank())
            "${schedDate}T${schedEndTime}" else null
        val srcLabel = buildString {
            if (selectedSourceName.isNotBlank() && selectedSourceName != "My Company") append(selectedSourceName)
            if (ticketNum.isNotBlank()) { if (isNotBlank()) append(" / "); append("Ticket #$ticketNum") }
        }
        val isNonSelf = assignCat != "self" && (assignedTo != null || assignedRosterTechId != null)
        val localRosterTechId = assignedRosterTechId
        vm.createJobWithCustomer(
            jobData = mapOf(
                "type"                 to type,
                "address"              to address.ifBlank { null },
                "city"                 to city.ifBlank { null },
                "state"                to stateCode.ifBlank { null },
                "zip"                  to zip.ifBlank { null },
                "notes"                    to notes.ifBlank { null },
                "scheduled_local"          to scheduled,
                "scheduled_end_local"      to scheduledEnd,
                "lat"                      to lat,
                "lng"                      to lng,
                "assigned_to"              to assignedTo,
                "assigned_roster_tech_id"  to assignedRosterTechId,
                "source"               to srcLabel.ifBlank { null },
                "source_type"          to sourceType,
                "job_source_id"        to jobSourceId,
                "ad_channel_id"        to adChannelId,
                "customer_id"          to resolvedCustomerId,
                "linked_job_id"        to linkedJobId,
                "notify_sms"           to if (isNonSelf) notifySms else false,
                "notify_email"         to if (isNonSelf) notifyEmail else false,
                "notify_push"          to if (isNonSelf) notifyPush else false,
                "skip_duplicate_check" to true
            ),
            customerData = if (resolvedCustomerId == null && custName.isNotBlank()) mapOf(
                "first_name" to (nameParts.getOrNull(0) ?: ""),
                "last_name"  to (nameParts.getOrNull(1) ?: ""),
                "phone"      to custPhone.ifBlank { null },
                "email"      to custEmail.ifBlank { null },
                "address"    to address.ifBlank { null },
                "city"       to city.ifBlank { null },
                "state"      to stateCode.ifBlank { null },
                "zip"        to zip.ifBlank { null }
            ) else null,
            extraPhones = extraCustPhones.toList(),
            extraEmails = extraCustEmails.toList()
        ) { newJobId ->
            if (sendToTech && localRosterTechId != null && (notifySms || notifyEmail)) {
                val method = when {
                    notifySms && notifyEmail -> "both"
                    notifySms -> "sms"
                    else -> "email"
                }
                val techName = state.rosterTechs.find { it.id == localRosterTechId }?.name ?: "Tech"
                vm.notifyRosterTech(newJobId, localRosterTechId, method, techName)
            }
            onSaved()
        }
    }

    // ── Save entry point — checks for duplicate first if needed ───────────
    fun doSave(sendToTech: Boolean = false) {
        // Edit mode has a fixed customer — skip the create-only duplicate flow.
        if (isEdit) { doSaveNow(); return }
        // If duplicate sheet is already showing, re-show it instead of saving
        if (state.duplicateCustomer != null) { showDuplicateSheet = true; return }
        // Already have a customer ID (from paste ticket or prior sheet selection) → save
        if (customerId != null) { doSaveNow(sendToTech = sendToTech); return }
        // Manual entry with phone → check for duplicate first
        if (custPhone.isNotBlank()) {
            autoSavePending = true
            vm.checkDuplicateByPhone(custPhone)
            return
        }
        // No phone, no existing customer → just save (new customer with name only)
        doSaveNow(sendToTech = sendToTech)
    }

    // ── When phone-check returns no duplicate, proceed with auto-save ─────
    LaunchedEffect(state.duplicateCheckDone) {
        if (state.duplicateCheckDone && autoSavePending) {
            autoSavePending = false
            vm.resetDuplicateCheck()
            if (state.duplicateCustomer == null) {
                doSaveNow()   // no duplicate → proceed
            }
            // if duplicateCustomer != null, the other LaunchedEffect shows the sheet
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (isEdit) "Edit Job" else "New Job", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = {
                if (!isEdit) {
                    if (state.isParsing) {
                        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = AppColors.Blue)
                        }
                    } else {
                        TextButton(
                            onClick = { clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { vm.parseTicket(it) } },
                            colors  = ButtonDefaults.textButtonColors(contentColor = AppColors.Blue)
                        ) {
                            Icon(Icons.Default.ContentPaste, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Paste Ticket", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                TextButton(
                    onClick = { doSave() },
                    enabled = !state.isParsing
                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
            }
        )
    }) { padding ->
        // KEY FIX: imePadding() before verticalScroll so keyboard shifts content up
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Paste Ticket banners ───────────────────────────────────────
            AnimatedVisibility(visible = state.parsedSuccess) {
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Green.copy(.12f)), shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = AppColors.Green, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Ticket parsed!", fontWeight = FontWeight.SemiBold, color = AppColors.Green)
                            if (ticketNum.isNotBlank()) Text("Ticket #$ticketNum", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!isEdit && customerId != null && custName.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Blue.copy(.12f)), shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = AppColors.Blue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Existing customer found — $custName", fontWeight = FontWeight.SemiBold, color = AppColors.Blue)
                    }
                }
            }
            if (customerId == null && custName.isNotBlank() && state.parsedTicket != null) {
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Purple.copy(.12f)), shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PersonAdd, null, tint = AppColors.Purple, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("New customer will be created — $custName", fontWeight = FontWeight.SemiBold, color = AppColors.Purple)
                    }
                }
            }

            // ── SOURCE (Option B: hidden for non-full job creators; admin sets it later) ──
            if ((state.jobsPermission ?: "full") == "full") {
            SectionLabel("SOURCE")
            ExposedDropdownMenuBox(expanded = sourceDropOpen, onExpandedChange = { sourceDropOpen = it }) {
                OutlinedTextField(
                    value = selectedSourceName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Job Source") },
                    leadingIcon = { Icon(Icons.Default.Campaign, null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceDropOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = sourceDropOpen, onDismissRequest = { sourceDropOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("My Company", fontWeight = FontWeight.SemiBold) },
                        onClick = {
                            selectedSourceKey = "my_company"; selectedSourceName = "My Company"
                            sourceType = "own_company"; jobSourceId = null; adChannelId = null
                            sourceDropOpen = false
                        },
                        leadingIcon = { Icon(Icons.Default.Business, null) }
                    )
                    if (contacts.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("CONTACTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {}, enabled = false
                        )
                        contacts.filter { it.isActive }.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = {
                                    selectedSourceKey = "contact_${c.id}"; selectedSourceName = c.name
                                    sourceType = "external_contact"; jobSourceId = c.id; adChannelId = null
                                    sourceDropOpen = false
                                },
                                leadingIcon = { Icon(Icons.Default.Person, null) }
                            )
                        }
                    }
                    if (channels.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("AD CHANNELS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {}, enabled = false
                        )
                        channels.filter { it.isActive }.forEach { ch ->
                            DropdownMenuItem(
                                text = { Text(ch.name) },
                                onClick = {
                                    selectedSourceKey = "channel_${ch.id}"; selectedSourceName = ch.name
                                    sourceType = "own_company"; adChannelId = ch.id; jobSourceId = null
                                    sourceDropOpen = false
                                },
                                leadingIcon = { Icon(Icons.Default.Tv, null) }
                            )
                        }
                    }
                }
            }
            }

            // ── JOB TYPE ──────────────────────────────────────────────────
            SectionLabel("JOB TYPE")
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(types) { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.replaceFirstChar { it.uppercase() }) }) }
            }

            // ── ASSIGN TECHNICIAN ─────────────────────────────────────────
            SectionLabel("ASSIGN TECHNICIAN")
            val assignmentLabel = when {
                assignCat == "self" -> "Self — ${state.currentUser?.fullName ?: "Me"}"
                assignedRosterTechId != null -> state.rosterTechs.find { it.id == assignedRosterTechId }?.name ?: "Roster Tech"
                assignedTo != null -> state.techs.find { it.id == assignedTo }?.fullName ?: "Team Member"
                else -> "Unassigned — tap to assign"
            }
            OutlinedButton(
                onClick = { showAssignDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Person, "Change assignment", Modifier.size(16.dp))  // P2.1b: test anchor (distinct from the "ASSIGN TECHNICIAN" label)
                Spacer(Modifier.width(8.dp))
                Text(assignmentLabel, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
            }

            // ── NOTIFICATION METHOD (visible when non-self tech assigned) ──
            val isNonSelf = assignCat != "self" && (assignedTo != null || assignedRosterTechId != null)
            if (isNonSelf && !isEdit) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "SEND VIA",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF1A73E8),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppSwitch(checked = notifySms, onCheckedChange = { notifySms = it })
                        Spacer(Modifier.width(4.dp))
                        Text("SMS", fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppSwitch(checked = notifyEmail, onCheckedChange = { notifyEmail = it })
                        Spacer(Modifier.width(4.dp))
                        Text("Email", fontSize = 14.sp)
                    }
                    if (assignCat == "team") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppSwitch(checked = notifyPush, onCheckedChange = { notifyPush = it })
                            Spacer(Modifier.width(4.dp))
                            Text("Push", fontSize = 14.sp)
                        }
                    }
                }
            }

            // ── CUSTOMER ──────────────────────────────────────────────────
            SectionLabel("CUSTOMER")
            if (isEdit) {
                // Edit mode: the job's customer is fixed — show it read-only (the update
                // path does not change the customer link, matching web edit).
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(custName.ifBlank { "Customer" }, fontWeight = FontWeight.SemiBold)
                            if (custPhone.isNotBlank()) Text(custPhone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
            if (customerId != null) {
                OutlinedTextField(custName, { custName = it; customerId = null }, label = { Text("Customer Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), trailingIcon = { Icon(Icons.Default.CheckCircle, null, tint = AppColors.Green) })
            } else {
                OutlinedTextField(custName, { custName = it }, label = { Text("Customer Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
            OutlinedTextField(custPhone, { custPhone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            extraCustPhones.forEachIndexed { idx, ph ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(ph, { extraCustPhones[idx] = it }, label = { Text("Phone ${idx + 2}") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    IconButton(onClick = { extraCustPhones.removeAt(idx) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            TextButton(onClick = { extraCustPhones.add("") }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                Text("+ Add phone number", color = AppColors.Blue, fontSize = 13.sp)
            }
            OutlinedTextField(custEmail, { custEmail = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            extraCustEmails.forEachIndexed { idx, em ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(em, { extraCustEmails[idx] = it }, label = { Text("Email ${idx + 2}") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    IconButton(onClick = { extraCustEmails.removeAt(idx) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            TextButton(onClick = { extraCustEmails.add("") }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                Text("+ Add email", color = AppColors.Blue, fontSize = 13.sp)
            }
            } // end else — create-mode editable customer inputs

            // ── ADDRESS ───────────────────────────────────────────────────
            SectionLabel("ADDRESS")
            PlacesAddressField(
                value = address,
                onValueChange = { address = it },
                onPlaceSelected = { street, c, s, z, la, ln -> address = street; city = c; stateCode = s; zip = z; lat = la; lng = ln },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(city, { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.weight(2f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(stateCode, { stateCode = it }, label = { Text("State") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(zip, { zip = it }, label = { Text("ZIP") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }

            // ── JOB NOTES ─────────────────────────────────────────────────
            SectionLabel("JOB NOTES")
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes for technician") }, minLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

            // ── SCHEDULE ──────────────────────────────────────────────────
            SectionLabel("SCHEDULE")
            OutlinedTextField(
                value = if (schedDate.isNotBlank()) formatDisplayDate(schedDate) else "",
                onValueChange = {},
                label = { Text("Scheduled Date") },
                placeholder = { Text("Tap to select date") },
                readOnly = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (schedDate.isNotBlank()) {
                            IconButton(onClick = { schedDate = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                            }
                        }
                        Icon(Icons.Default.CalendarMonth, null)
                    }
                },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor        = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor      = MaterialTheme.colorScheme.outline,
                    disabledLabelColor       = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor= MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            OutlinedTextField(
                value = if (schedTime.isNotBlank()) formatDisplayTime(schedTime) else "",
                onValueChange = {},
                label = { Text("Scheduled Time") },
                placeholder = { Text("Tap to select time") },
                readOnly = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().clickable { showTimePicker = true },
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (schedTime.isNotBlank()) {
                            IconButton(onClick = { schedTime = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                            }
                        }
                        Icon(Icons.Default.Schedule, null)
                    }
                },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor        = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor      = MaterialTheme.colorScheme.outline,
                    disabledLabelColor       = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor= MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            // P2.19: optional arrival-window "to" time (enabled once a start time is set)
            OutlinedTextField(
                value = if (schedEndTime.isNotBlank()) formatDisplayTime(schedEndTime) else "",
                onValueChange = {},
                label = { Text("Arrival Window End (optional)") },
                placeholder = { Text(if (schedTime.isBlank()) "Set a time first" else "Tap to select end time") },
                readOnly = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().clickable(enabled = schedTime.isNotBlank()) { showEndTimePicker = true },
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (schedEndTime.isNotBlank()) {
                            IconButton(onClick = { schedEndTime = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                            }
                        }
                        Icon(Icons.Default.Schedule, null)
                    }
                },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor        = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor      = MaterialTheme.colorScheme.outline,
                    disabledLabelColor       = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor= MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            if (schedDate.isNotBlank() || schedTime.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Event, null, tint = AppColors.Blue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    val display = buildString {
                        if (schedDate.isNotBlank()) append(formatDisplayDate(schedDate))
                        if (schedTime.isNotBlank()) {
                            if (isNotBlank()) append(" at "); append(formatDisplayTime(schedTime))
                            if (schedEndTime.isNotBlank()) append(" – ${formatDisplayTime(schedEndTime)}")  // P2.19 window
                        }
                    }
                    Text(display, style = MaterialTheme.typography.bodySmall, color = AppColors.Blue, fontWeight = FontWeight.Medium)
                }
            }

            // ── Error ─────────────────────────────────────────────────────
            state.error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(8.dp)) {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            // ── Action Buttons ────────────────────────────────────────────
            if (isEdit) {
                Button(
                    onClick = { doSave() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                    enabled = !state.isParsing
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { doSave() },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF1A73E8)),
                    enabled = !state.isParsing
                ) {
                    Text("Save Job", fontWeight = FontWeight.Bold, color = Color(0xFF1A73E8))
                }
                if (isNonSelf) {
                    Button(
                        onClick = { doSave(sendToTech = true) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                        enabled = !state.isParsing
                    ) {
                        Text("Save & Send", fontWeight = FontWeight.Bold)
                    }
                }
            }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    // ── Date Picker Dialog ─────────────────────────────────────────────────
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = millis
                        schedDate = "%04d-%02d-%02d".format(
                            cal.get(java.util.Calendar.YEAR),
                            cal.get(java.util.Calendar.MONTH) + 1,
                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Time Picker Dialog ─────────────────────────────────────────────────
    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                    TimePicker(state = timePickerState)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            schedTime = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                            showTimePicker = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }

    // P2.19: arrival-window "to" time picker
    if (showEndTimePicker) {
        Dialog(onDismissRequest = { showEndTimePicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Arrival Window End", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                    TimePicker(state = endTimePickerState)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            schedEndTime = "%02d:%02d".format(endTimePickerState.hour, endTimePickerState.minute)
                            showEndTimePicker = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }

    // ── Duplicate Customer Sheet ───────────────────────────────────────────
    if (showDuplicateSheet && state.duplicateCustomer != null) {
        val dupInfo = state.duplicateCustomer!!
        DuplicateCustomerSheet(
            info = dupInfo,
            onReturningCustomer = {
                showDuplicateSheet = false
                customerId = dupInfo.customerId  // keep UI state in sync
                notes = if (notes.isBlank()) "⭐ RETURNING CUSTOMER" else "⭐ RETURNING CUSTOMER\n\n$notes"
                vm.clearDuplicateCustomer()
                doSaveNow(forceCustomerId = dupInfo.customerId)  // pass explicitly — never rely on state timing
            },
            onGoBack = { jobId ->
                showDuplicateSheet = false
                customerId  = dupInfo.customerId
                linkedJobId = jobId
                notes = if (notes.isBlank()) "🔄 GO-BACK (WARRANTY)" else "🔄 GO-BACK (WARRANTY)\n\n$notes"
                vm.clearDuplicateCustomer()
                doSaveNow(forceCustomerId = dupInfo.customerId)
            },
            onFollowUp = { jobId ->
                showDuplicateSheet = false
                customerId  = dupInfo.customerId
                linkedJobId = jobId
                notes = if (notes.isBlank()) "📋 FOLLOW-UP" else "📋 FOLLOW-UP\n\n$notes"
                vm.clearDuplicateCustomer()
                doSaveNow(forceCustomerId = dupInfo.customerId)
            },
            onCreateNew = {
                // P2.21: explicit "Create New" — make a brand-new customer from the PARSED
                // name/phone (not the matched duplicate) and save the job immediately, mirroring
                // web's primary modal action. Keeps the parsed fields (unlike Cancel).
                showDuplicateSheet = false
                customerId = null
                vm.clearDuplicateCustomer()
                doSaveNow(forceNewCustomer = true)
            },
            onCancel = {
                showDuplicateSheet = false
                vm.clearDuplicateCustomer()
                notes = ""; address = ""; city = ""; stateCode = ""; zip = ""
                custName = ""; custPhone = ""; custEmail = ""; customerId = null
                ticketNum = ""; schedDate = ""; schedTime = ""
                sourceType = null; jobSourceId = null; adChannelId = null
                extraCustPhones.clear(); extraCustEmails.clear()
                linkedJobId = null
            },
            onDismiss = { showDuplicateSheet = false; vm.clearError() }  // swipe away — don't clear fields
        )
    }

    // ── Assign Technician Dialog ──────────────────────────────────────────────
    if (showAssignDialog) {
        var dialogCat by remember { mutableStateOf(assignCat) }
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            title = { Text("Assign Technician", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("self" to "Self", "team" to "Team", "roster" to "Roster", "partner" to "Partner").forEach { (cat, label) ->
                            FilterChip(selected = dialogCat == cat, onClick = { dialogCat = cat },
                                label = { Text(label, fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                        }
                    }
                    when (dialogCat) {
                        "self" -> {
                            ListItem(
                                headlineContent = { Text(state.currentUser?.fullName ?: "Me") },
                                supportingContent = { Text("Assign to yourself") },
                                leadingContent = { Icon(Icons.Default.Person, null, tint = AppColors.Blue) },
                                trailingContent = { if (assignCat == "self") Icon(Icons.Default.Check, null, tint = AppColors.Green) },
                                modifier = Modifier.clickable {
                                    assignCat = "self"; assignedTo = state.currentUser?.id
                                    assignedRosterTechId = null; showAssignDialog = false
                                }
                            )
                        }
                        "team" -> {
                            if (state.techs.isEmpty()) {
                                Text("No team members found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                ListItem(
                                    headlineContent = { Text("Unassigned", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingContent = { Icon(Icons.Default.PersonOutline, null) },
                                    modifier = Modifier.clickable { assignCat = "team"; assignedTo = null; assignedRosterTechId = null; showAssignDialog = false }
                                )
                                state.techs.forEach { tech ->
                                    val techColor = try { Color(android.graphics.Color.parseColor(tech.color ?: "#1565C0")) } catch (e: Exception) { AppColors.Blue }
                                    ListItem(
                                        headlineContent = { Text(tech.fullName) },
                                        leadingContent = { AvatarCircle(tech.initials, techColor, 28.dp) },
                                        trailingContent = { if (assignedTo == tech.id && assignCat == "team") Icon(Icons.Default.Check, null, tint = AppColors.Green) },
                                        modifier = Modifier.clickable { assignCat = "team"; assignedTo = tech.id; assignedRosterTechId = null; notifyPush = true; notifySms = false; notifyEmail = false; showAssignDialog = false }
                                    )
                                }
                            }
                        }
                        "roster" -> {
                            if (state.rosterTechs.isEmpty()) {
                                Text("No roster technicians found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                state.rosterTechs.forEach { rt ->
                                    ListItem(
                                        headlineContent = { Text(rt.name) },
                                        leadingContent = { Icon(Icons.Default.Engineering, null, tint = AppColors.Blue) },
                                        trailingContent = { if (assignedRosterTechId == rt.id) Icon(Icons.Default.Check, null, tint = AppColors.Green) },
                                        modifier = Modifier.clickable { assignCat = "roster"; assignedRosterTechId = rt.id; assignedTo = null; notifySms = true; notifyEmail = false; notifyPush = false; showAssignDialog = false }
                                    )
                                }
                            }
                        }
                        "partner" -> {
                            Text(
                                "Save the job first, then use 'Send To' from the job detail to assign to a partner company.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAssignDialog = false }) { Text("Done") }
            }
        )
    }
}

// ── Duplicate Customer Sheet ───────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateCustomerSheet(
    info: DuplicateCustomerInfo,
    onReturningCustomer: () -> Unit,
    onGoBack: (linkedJobId: String?) -> Unit,
    onFollowUp: (linkedJobId: String?) -> Unit,
    onCreateNew: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit = onCreateNew  // swipe-to-dismiss → create new from parsed fields (keeps fields; P2.1l/P2.21)
) {
    var step by remember { mutableStateOf<String?>(null) } // null=choose, "go_back", "follow_up"
    var selectedJobId by remember { mutableStateOf<String?>(null) }
    val lastJob = info.recentJobs.firstOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHandle()
        if (step == null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ── Header ──────────────────────────────────────────────────
                Text("Returning Customer Found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, tint = AppColors.Blue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(info.customerName, fontWeight = FontWeight.SemiBold, color = AppColors.Blue)
                }
                info.phone?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                info.address?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Last job summary
                lastJob?.let { j ->
                    Spacer(Modifier.height(4.dp))
                    val sc = AppColors.jobStatus(j.status)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape  = RoundedCornerShape(8.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Last job: ${j.jobNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(j.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                            StatusBadge(j.status.replace("_", " ").replaceFirstChar { it.uppercase() }, sc, small = true)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // ── Option 1: Returning Customer ────────────────────────────
                Button(
                    onClick = onReturningCustomer,
                    modifier = Modifier.fillMaxWidth(),
                    shape  = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Blue)
                ) { Text("Returning Customer", fontWeight = FontWeight.SemiBold) }
                Text("Use existing record, book a new job", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))

                // ── Option 2: Go-Back / Warranty ────────────────────────────
                Button(
                    onClick = { if (info.recentJobs.isEmpty()) onGoBack(null) else step = "go_back" },
                    modifier = Modifier.fillMaxWidth(),
                    shape  = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Orange)
                ) { Text("Go-Back / Warranty", fontWeight = FontWeight.SemiBold) }
                Text("Issue with a previous job, link to original", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))

                // ── Option 3: Follow-Up ─────────────────────────────────────
                Button(
                    onClick = { if (info.recentJobs.isEmpty()) onFollowUp(null) else step = "follow_up" },
                    modifier = Modifier.fillMaxWidth(),
                    shape  = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Purple)
                ) { Text("Follow-Up", fontWeight = FontWeight.SemiBold) }
                Text("Continuation of a previous visit, link to original", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))

                // ── Option 4: Create New (P2.21) — brand-new customer from parsed fields ──
                Button(
                    onClick = onCreateNew,
                    modifier = Modifier.fillMaxWidth(),
                    shape  = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green)
                ) { Text("Create New Customer", fontWeight = FontWeight.SemiBold) }
                Text("Not the same person — create a separate customer from the entered name/phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))

                // ── Option 5: Cancel ────────────────────────────────────────
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape  = RoundedCornerShape(12.dp)
                ) { Text("Cancel — Clear All Fields") }
                Text("Dismiss and start over", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            }
        } else {
            // ── Step 2: Link to a previous job ──────────────────────────────
            val isGoBack  = step == "go_back"
            val onConfirm: (String?) -> Unit = if (isGoBack) onGoBack else onFollowUp
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Link to Previous Job?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Select the job this ${if (isGoBack) "go-back" else "follow-up"} is for, or skip:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                info.recentJobs.forEach { job ->
                    val isSelected = selectedJobId == job.id
                    val sc = AppColors.jobStatus(job.status)
                    Card(
                        onClick = { selectedJobId = if (isSelected) null else job.id },
                        modifier = Modifier.fillMaxWidth().then(
                            if (isSelected) Modifier.border(2.dp, AppColors.Blue, RoundedCornerShape(12.dp)) else Modifier
                        ),
                        shape  = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) AppColors.Blue.copy(alpha = 0.1f)
                                             else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(job.jobNumber, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(job.title, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(2.dp))
                                StatusBadge(job.status.replace("_", " ").replaceFirstChar { it.uppercase() }, sc, small = true)
                            }
                            if (isSelected) Icon(Icons.Default.Check, null, tint = AppColors.Blue)
                        }
                    }
                }
                Button(
                    onClick = { onConfirm(selectedJobId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape  = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isGoBack) AppColors.Orange else AppColors.Purple)
                ) { Text(if (selectedJobId != null) "Link & Save" else "Skip — Save Without Link", fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = { step = null; selectedJobId = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }
        }
    }
}

// ── Job Edit ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobEditScreen(jobId: String, onBack: () -> Unit, onSaved: () -> Unit, onEditCustomer: (String) -> Unit = {}, vm: JobViewModel = hiltViewModel(), sourceVm: com.ultimatepro.ui.settings.JobSourceViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    var title      by remember { mutableStateOf("") }
    var notes      by remember { mutableStateOf("") }
    var address    by remember { mutableStateOf("") }
    var city       by remember { mutableStateOf("") }
    var stateCode  by remember { mutableStateOf("") }
    var zip        by remember { mutableStateOf("") }
    var lat        by remember { mutableStateOf<Double?>(null) }
    var lng        by remember { mutableStateOf<Double?>(null) }
    var priority   by remember { mutableStateOf("medium") }
    var type       by remember { mutableStateOf("service") }
    var jobSource  by remember { mutableStateOf("") }

    var schedDate      by remember { mutableStateOf("") }
    var schedTime      by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = false)

    var assignedTo            by remember { mutableStateOf<String?>(null) }
    var assignedRosterTechId  by remember { mutableStateOf<String?>(null) }
    var techDropdownOpen      by remember { mutableStateOf(false) }

    var lineItems     by remember { mutableStateOf(listOf<LineItemInput>()) }
    var showAddCharge by remember { mutableStateOf(false) }
    var showAddPart   by remember { mutableStateOf(false) }

    var sourceDropOpen   by remember { mutableStateOf(false) }
    var sourceType       by remember { mutableStateOf<String?>(null) }
    var jobSourceId      by remember { mutableStateOf<String?>(null) }
    var adChannelId      by remember { mutableStateOf<String?>(null) }
    var adChannelCustom  by remember { mutableStateOf("") }
    var contactDropOpen  by remember { mutableStateOf(false) }
    var channelDropOpen  by remember { mutableStateOf(false) }

    val contacts by sourceVm.contacts.collectAsState()
    val channels by sourceVm.channels.collectAsState()

    val priorities = listOf("low", "medium", "high", "urgent")
    val types      = listOf("service", "installation", "maintenance", "inspection", "repair", "emergency")
    val sources    = listOf("Google", "Yelp", "Referral", "Website", "Repeat Customer", "Other")

    LaunchedEffect(jobId) { vm.loadJob(jobId); vm.loadTechs(); vm.loadRosterTechs(); sourceVm.loadContacts(); sourceVm.loadChannels() }

    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(state.selected) {
        if (!initialized) {
            state.selected?.let { job ->
                initialized = true
                title     = job.title
                notes     = job.notes ?: ""
                address   = job.address ?: ""
                city      = job.city ?: ""
                stateCode = job.state ?: ""
                zip       = job.zip ?: ""
                priority  = job.priority
                type      = job.type
                assignedTo = job.assigned_to
                assignedRosterTechId = job.assigned_roster_tech_id
                jobSource = job.source ?: ""
                sourceType = job.source_type
                jobSourceId = job.job_source_id
                adChannelId = job.ad_channel_id
                adChannelCustom = job.ad_channel_custom ?: ""
                job.scheduled_start?.let { start ->
                    // TZ 3/3: show the job-zone wall-clock so editing doesn't shift the time.
                    schedDate = formatJobInstant(start, job.effective_timezone, "yyyy-MM-dd")
                    schedTime = formatJobInstant(start, job.effective_timezone, "HH:mm")
                }
                lat = job.lat
                lng = job.lng
                lineItems = job.line_items.map { li -> LineItemInput(li.name, li.quantity, li.unit_price) }
            }
        }
    }

    val subtotal         = lineItems.sumOf { it.total }
    val assignedTech     = state.techs.find { it.id == assignedTo }
    val assignedRosterTech = state.rosterTechs.find { it.id == assignedRosterTechId }
    val job              = state.selected

    fun doSave() {
        if (title.isBlank()) return
        // TZ 3/3: send scheduled_local wall-clock + coords; backend converts (Path B).
        // Date-only defaults to noon; blank date stays null (F2).
        val scheduled = when {
            schedDate.isBlank() -> null
            else                -> "${schedDate}T${schedTime.ifBlank { "12:00" }}"
        }
        val liBodies = lineItems.map { li -> mapOf("name" to li.name, "quantity" to li.qty, "unit_price" to li.unitPrice, "total" to li.total) }
        vm.updateJob(jobId, mapOf(
            "title"            to title,
            "type"             to type,
            "priority"         to priority,
            "address"          to address.ifBlank { null },
            "city"             to city.ifBlank { null },
            "state"            to stateCode.ifBlank { null },
            "zip"              to zip.ifBlank { null },
            "notes"            to notes.ifBlank { null },
            "source_type"      to sourceType,
            "job_source_id"    to jobSourceId,
            "ad_channel_id"    to adChannelId,
            "ad_channel_custom"        to adChannelCustom.ifBlank { null },
            "scheduled_local"          to scheduled,
            "lat"                      to lat,
            "lng"                      to lng,
            "assigned_to"              to assignedTo,
            "assigned_roster_tech_id"  to assignedRosterTechId,
            "line_items"               to liBodies
        )) { onSaved() }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (job != null) "Edit ${job.job_number}" else "Edit Job", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = {
                TextButton(onClick = { doSave() }, enabled = title.isNotBlank()) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }) { padding ->
        if (!initialized && job == null) { LoadingView(); return@Scaffold }

        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Customer (read-only) ──────────────────────────────────────
            job?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(job.customerName, fontWeight = FontWeight.SemiBold)
                            job.cust_phone?.let { ph -> Text(ph, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        TextButton(onClick = { onEditCustomer(job.customer_id) }) { Text("Edit →") }
                    }
                }
            }

            // ── JOB INFO ─────────────────────────────────────────────────
            SectionLabel("JOB INFO")
            OutlinedTextField(title, { title = it }, label = { Text("Title *") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

            // ── TYPE ─────────────────────────────────────────────────────
            SectionLabel("TYPE")
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(types) { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.replaceFirstChar { it.uppercase() }) }) }
            }

            // ── PRIORITY ─────────────────────────────────────────────────
            SectionLabel("PRIORITY")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                priorities.forEach { p -> FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.replaceFirstChar { it.uppercase() }) }, modifier = Modifier.weight(1f)) }
            }

            // ── LOCATION ─────────────────────────────────────────────────
            SectionLabel("LOCATION")
            PlacesAddressField(
                value = address,
                onValueChange = { address = it },
                onPlaceSelected = { street, c, s, z, la, ln -> address = street; city = c; stateCode = s; zip = z; lat = la; lng = ln },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(city, { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.weight(2f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(stateCode, { stateCode = it }, label = { Text("State") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(zip, { zip = it }, label = { Text("ZIP") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }

            // ── NOTES ────────────────────────────────────────────────────
            SectionLabel("NOTES")
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes for technician") }, minLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

            // ── SCHEDULE ─────────────────────────────────────────────────
            SectionLabel("SCHEDULE")
            OutlinedTextField(
                value = if (schedDate.isNotBlank()) formatDisplayDate(schedDate) else "",
                onValueChange = {},
                label = { Text("Scheduled Date") },
                placeholder = { Text("Tap to select date") },
                readOnly = true, singleLine = true,
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (schedDate.isNotBlank()) IconButton(onClick = { schedDate = "" }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp)) }
                        Icon(Icons.Default.CalendarMonth, null)
                    }
                },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline, disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant, disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant, disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant, disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant)
            )
            OutlinedTextField(
                value = if (schedTime.isNotBlank()) formatDisplayTime(schedTime) else "",
                onValueChange = {},
                label = { Text("Scheduled Time") },
                placeholder = { Text("Tap to select time") },
                readOnly = true, singleLine = true,
                modifier = Modifier.fillMaxWidth().clickable { showTimePicker = true },
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (schedTime.isNotBlank()) IconButton(onClick = { schedTime = "" }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp)) }
                        Icon(Icons.Default.Schedule, null)
                    }
                },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline, disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant, disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant, disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant)
            )
            if (schedDate.isNotBlank() || schedTime.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Event, null, tint = AppColors.Blue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    val display = buildString {
                        if (schedDate.isNotBlank()) append(formatDisplayDate(schedDate))
                        if (schedTime.isNotBlank()) { if (isNotBlank()) append(" at "); append(formatDisplayTime(schedTime)) }
                    }
                    Text(display, style = MaterialTheme.typography.bodySmall, color = AppColors.Blue, fontWeight = FontWeight.Medium)
                }
            }

            // ── ASSIGNMENT ───────────────────────────────────────────────
            SectionLabel("ASSIGNMENT")
            ExposedDropdownMenuBox(expanded = techDropdownOpen, onExpandedChange = { techDropdownOpen = it }) {
                OutlinedTextField(
                    value = assignedRosterTech?.name ?: assignedTech?.fullName ?: "Unassigned",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Assigned Technician") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = techDropdownOpen) },
                    leadingIcon = {
                        when {
                            assignedRosterTech != null -> Icon(Icons.Default.Engineering, null, tint = AppColors.Blue)
                            assignedTech != null -> {
                                val color = try { Color(android.graphics.Color.parseColor(assignedTech.color ?: "#1565C0")) } catch (e: Exception) { AppColors.Blue }
                                AvatarCircle(assignedTech.initials, color, 24.dp)
                            }
                            else -> Icon(Icons.Default.PersonOutline, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = techDropdownOpen, onDismissRequest = { techDropdownOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Unassigned", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { assignedTo = null; assignedRosterTechId = null; techDropdownOpen = false },
                        leadingIcon = { Icon(Icons.Default.PersonOutline, null) }
                    )
                    if (state.rosterTechs.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("TECHNICIANS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AppColors.Blue) },
                            onClick = {},
                            enabled = false
                        )
                        state.rosterTechs.forEach { rt ->
                            DropdownMenuItem(
                                text = { Text(rt.name) },
                                onClick = { assignedRosterTechId = rt.id; assignedTo = null; techDropdownOpen = false },
                                leadingIcon = { Icon(Icons.Default.Engineering, null, tint = AppColors.Blue) }
                            )
                        }
                    }
                    if (state.techs.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("APP USERS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {},
                            enabled = false
                        )
                        state.techs.forEach { tech ->
                            val techColor = try { Color(android.graphics.Color.parseColor(tech.color ?: "#1565C0")) } catch (e: Exception) { AppColors.Blue }
                            DropdownMenuItem(
                                text = { Text(tech.fullName) },
                                onClick = { assignedTo = tech.id; assignedRosterTechId = null; techDropdownOpen = false },
                                leadingIcon = { AvatarCircle(tech.initials, techColor, 28.dp) }
                            )
                        }
                    }
                }
            }

            // ── CHARGES & PARTS ──────────────────────────────────────────
            SectionLabel("CHARGES & PARTS")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showAddCharge = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add Charge") }
                OutlinedButton(onClick = { showAddPart = true },   modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add Part") }
            }
            if (lineItems.isNotEmpty()) {
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(4.dp)) {
                        lineItems.forEachIndexed { idx, li ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(li.name, fontWeight = FontWeight.Medium)
                                    Text("${formatQty(li.qty)} × ${formatMoney(li.unitPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(formatMoney(li.total), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { lineItems = lineItems.toMutableList().also { it.removeAt(idx) } }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            if (idx < lineItems.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Blue.copy(.08f)), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Subtotal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${lineItems.size} item${if (lineItems.size != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatMoney(subtotal), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.Blue)
                    }
                }
            }

            // ── SOURCE ───────────────────────────────────────────────────
            SectionLabel("SOURCE")
            Text("How did this job come in?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("network" to "Network", "external_contact" to "Source Contact", "own_company" to "Own Company").forEach { (key, label) ->
                    FilterChip(
                        selected = sourceType == key,
                        onClick  = {
                            sourceType = if (sourceType == key) null else key
                            if (sourceType != "external_contact") { jobSourceId = null }
                            if (sourceType != "own_company") { adChannelId = null; adChannelCustom = "" }
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            when (sourceType) {
                "external_contact" -> {
                    if (contacts.isEmpty()) {
                        Text("Add source contacts in Settings → Job Sources", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val selectedContact = contacts.find { it.id == jobSourceId }
                        ExposedDropdownMenuBox(expanded = contactDropOpen, onExpandedChange = { contactDropOpen = it }) {
                            OutlinedTextField(
                                value = selectedContact?.name ?: "Select source contact…", onValueChange = {}, readOnly = true,
                                label = { Text("Source Contact") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = contactDropOpen) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(expanded = contactDropOpen, onDismissRequest = { contactDropOpen = false }) {
                                DropdownMenuItem(text = { Text("— None —", color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = { jobSourceId = null; jobSource = ""; contactDropOpen = false })
                                contacts.filter { it.isActive }.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Column { Text(c.name); c.companyName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } },
                                        onClick = { jobSourceId = c.id; jobSource = c.name; contactDropOpen = false }
                                    )
                                }
                            }
                        }
                    }
                }
                "own_company" -> {
                    val selectedChannel = channels.find { it.id == adChannelId }
                    ExposedDropdownMenuBox(expanded = channelDropOpen, onExpandedChange = { channelDropOpen = it }) {
                        OutlinedTextField(
                            value = selectedChannel?.name ?: "Select ad channel…", onValueChange = {}, readOnly = true,
                            label = { Text("Ad Channel") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = channelDropOpen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(expanded = channelDropOpen, onDismissRequest = { channelDropOpen = false }) {
                            DropdownMenuItem(text = { Text("— None —", color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = { adChannelId = null; jobSource = ""; channelDropOpen = false })
                            channels.filter { it.isActive }.forEach { ch ->
                                DropdownMenuItem(
                                    text = { Text(ch.name) },
                                    onClick = { adChannelId = ch.id; jobSource = ch.name; if (ch.name == "Other") adChannelCustom = ""; channelDropOpen = false }
                                )
                            }
                        }
                    }
                    if (selectedChannel?.name == "Other") {
                        OutlinedTextField(adChannelCustom, { adChannelCustom = it }, label = { Text("Specify source") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                }
                "network" -> Text("This job came from your contractor network.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else      -> Text("Source: Unassigned", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            state.error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(8.dp)) {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = millis
                        schedDate = "%04d-%02d-%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                    TimePicker(state = timePickerState)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { schedTime = "%02d:%02d".format(timePickerState.hour, timePickerState.minute); showTimePicker = false }) { Text("OK") }
                    }
                }
            }
        }
    }
    if (showAddCharge) { AddLineItemDialog("Add Charge", "e.g. Labor, Service Call, Diagnostic", { li -> lineItems = lineItems + li; showAddCharge = false }, { showAddCharge = false }) }
    if (showAddPart)   { AddLineItemDialog("Add Part",   "e.g. Spring, Cable, Motor",            { li -> lineItems = lineItems + li; showAddPart = false },   { showAddPart = false }) }
}

// ── Add Line Item Dialog ───────────────────────────────────────────────────
@Composable
private fun AddLineItemDialog(
    title:     String,
    hint:      String,
    onAdd:     (LineItemInput) -> Unit,
    onDismiss: () -> Unit
) {
    var name  by remember { mutableStateOf("") }
    var qty   by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text("Description *") },
                    placeholder = { Text(hint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        qty, { qty = it },
                        label = { Text("Qty") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        price, { price = it },
                        label = { Text("Unit Price ($)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                val q = qty.toDoubleOrNull() ?: 1.0
                val p = price.toDoubleOrNull() ?: 0.0
                if (p > 0) {
                    Text(
                        "Total: ${formatMoney(q * p)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Blue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(LineItemInput(name.trim(), qty.toDoubleOrNull() ?: 1.0, price.toDoubleOrNull() ?: 0.0))
                    }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Helpers ────────────────────────────────────────────────────────────────
private fun formatDisplayDate(isoDate: String): String {
    return try {
        val p = isoDate.split("-")
        val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        "${months[p[1].toInt() - 1]} ${p[2].toInt()}, ${p[0]}"
    } catch (e: Exception) { isoDate }
}

private fun formatDisplayTime(hhmm: String): String {
    return try {
        val p   = hhmm.split(":")
        val h   = p[0].toInt()
        val m   = p[1].toInt()
        val amPm = if (h < 12) "AM" else "PM"
        val h12  = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        "$h12:${"%02d".format(m)} $amPm"
    } catch (e: Exception) { hhmm }
}

private fun formatQty(qty: Double): String =
    if (qty == qty.toLong().toDouble()) qty.toLong().toString() else "%.2f".format(qty)

// ── Places Address Autocomplete Field ─────────────────────────────────────────
@Composable
fun PlacesAddressField(
    value:            String,
    onValueChange:    (String) -> Unit,
    onPlaceSelected:  (street: String, city: String, state: String, zip: String, lat: Double?, lng: Double?) -> Unit,
    modifier:         Modifier = Modifier
) {
    val context      = LocalContext.current
    val placesClient = remember { Places.createClient(context) }
    var predictions  by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    // P2.18: skip the debounced query for one change after a programmatic set (select/clear)
    // so choosing a suggestion doesn't immediately reopen the dropdown.
    var suppressQuery by remember { mutableStateOf(false) }
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // P2.18: DEBOUNCE (~300ms). The old code called Places on every keystroke AND surfaced
    // suggestions in a focus-stealing DropdownMenu, so the IME (keyboard) was dismissed per
    // letter. Now a single debounced request runs after typing settles; suggestions render in
    // a non-focus-stealing Popup (below) so the keyboard stays up continuously.
    LaunchedEffect(value) {
        if (suppressQuery) { suppressQuery = false; return@LaunchedEffect }
        if (value.trim().length < 3) { predictions = emptyList(); showSuggestions = false; return@LaunchedEffect }
        delay(300)
        val req = FindAutocompletePredictionsRequest.builder().setQuery(value).build()
        placesClient.findAutocompletePredictions(req)
            .addOnSuccessListener { resp ->
                predictions = resp.autocompletePredictions
                showSuggestions = predictions.isNotEmpty()
            }
            .addOnFailureListener { showSuggestions = false }
    }

    val selectPrediction: (AutocompletePrediction) -> Unit = { prediction ->
        showSuggestions = false
        val placeFields = listOf(Place.Field.ADDRESS_COMPONENTS, Place.Field.ADDRESS, Place.Field.LAT_LNG)
        placesClient.fetchPlace(FetchPlaceRequest.newInstance(prediction.placeId, placeFields))
            .addOnSuccessListener { resp ->
                var streetNum = ""; var route = ""; var city = ""; var state = ""; var zip = ""
                resp.place.addressComponents?.asList()?.forEach { comp ->
                    when {
                        "street_number" in comp.types -> streetNum = comp.name
                        "route"         in comp.types -> route     = comp.name
                        "locality"      in comp.types -> city      = comp.name
                        "administrative_area_level_1" in comp.types -> state = comp.shortName ?: comp.name
                        "postal_code"   in comp.types -> zip       = comp.name
                    }
                }
                val street = buildString {
                    if (streetNum.isNotBlank()) append(streetNum)
                    if (route.isNotBlank()) { if (isNotBlank()) append(" "); append(route) }
                }.ifBlank { resp.place.address ?: prediction.getFullText(null).toString() }
                suppressQuery = true
                onValueChange(street)
                onPlaceSelected(street, city, state, zip, resp.place.latLng?.latitude, resp.place.latLng?.longitude)
            }
            .addOnFailureListener {
                suppressQuery = true
                onValueChange(prediction.getFullText(null).toString())
            }
    }

    Box(modifier) {
        OutlinedTextField(
            value         = value,
            onValueChange = { onValueChange(it) },   // P2.18: no inline Places call — the debounced effect drives it
            label      = { Text("Street") },
            singleLine = true,
            modifier   = Modifier.fillMaxWidth().onGloballyPositioned { fieldSize = it.size },
            shape      = RoundedCornerShape(12.dp),
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(
                        onClick   = { suppressQuery = true; onValueChange(""); predictions = emptyList(); showSuggestions = false },
                        modifier  = Modifier.size(36.dp)
                    ) { Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp)) }
                }
            }
        )

        if (showSuggestions && predictions.isNotEmpty()) {
            // P2.18: focusable=false Popup anchored directly under the field → keyboard stays up.
            Popup(
                offset = IntOffset(0, fieldSize.height),
                properties = PopupProperties(focusable = false),
                onDismissRequest = { showSuggestions = false }
            ) {
                Surface(
                    modifier = Modifier.width(with(density) { fieldSize.width.toDp() }),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        predictions.take(5).forEach { prediction ->
                            Row(
                                Modifier.fillMaxWidth().clickable { selectPrediction(prediction) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    Text(prediction.getPrimaryText(null).toString(), fontWeight = FontWeight.Medium)
                                    Text(
                                        prediction.getSecondaryText(null).toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
