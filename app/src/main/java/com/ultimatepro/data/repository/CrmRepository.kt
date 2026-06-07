package com.ultimatepro.data.repository

import com.google.gson.Gson
import com.ultimatepro.data.api.ApiService
import com.ultimatepro.data.local.TokenStore
import com.ultimatepro.domain.model.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

// Sealed result - every API call returns one of these
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int = 0) : Result<Nothing>()
}

// Central wrapper that converts Retrofit responses
private suspend fun <T> call(block: suspend () -> Response<T>): Result<T> = try {
    val resp = block()
    if (resp.isSuccessful) {
        Result.Success(resp.body()!!)
    } else {
        val errorStr = resp.errorBody()?.string() ?: "Unknown error"
        // Try to extract "error" field from JSON
        val msg = try {
            Gson().fromJson(errorStr, Map::class.java)["error"]?.toString() ?: errorStr
        } catch (e: Exception) { errorStr }
        Result.Error(msg, resp.code())
    }
} catch (e: Exception) {
    Result.Error(e.message ?: "Network error — check your connection")
}

@Singleton
class CrmRepository @Inject constructor(
    private val api: ApiService,
    private val store: TokenStore
) {
    private val gson = Gson()

    // ── Auth ─────────────────────────────────────────────────────────────

    suspend fun login(email: String, password: String, fcmToken: String? = null): Result<AuthResponse> {
        val r = call { api.login(LoginRequest(email.trim().lowercase(), password, fcmToken)) }
        if (r is Result.Success) saveSession(r.data)
        return r
    }

    suspend fun register(req: RegisterRequest): Result<AuthResponse> {
        val r = call { api.register(req) }
        if (r is Result.Success) saveSession(r.data)
        return r
    }

    suspend fun logout() {
        try { api.logout() } catch (e: Exception) { /* ignore */ }
        store.clear()
    }

    suspend fun isLoggedIn() = store.isLoggedIn()

    suspend fun getCurrentUser(): User? = store.getUserJson()?.let {
        try { gson.fromJson(it, User::class.java) } catch (e: Exception) { null }
    }

    suspend fun getCurrentCompany(): Company? = store.getCompanyJson()?.let {
        try { gson.fromJson(it, Company::class.java) } catch (e: Exception) { null }
    }

    suspend fun updateFcmToken(token: String) =
        call { api.updateFcmToken(mapOf("fcm_token" to token)) }

    suspend fun registerFcmToken(token: String, deviceInfo: String? = null) =
        call { api.registerFcmToken(mapOf("token" to token, "device_info" to deviceInfo)) }

    suspend fun unregisterFcmToken(token: String) =
        call { api.unregisterFcmToken(mapOf("token" to token)) }

    private suspend fun saveSession(r: AuthResponse) {
        store.save(
            r.token, r.refresh_token,
            gson.toJson(r.user), gson.toJson(r.company),
            r.user.role, r.user.company_id, r.user.id
        )
    }

    // ── Company ───────────────────────────────────────────────────────────

    suspend fun getCompany()                             = call { api.getCompany() }
    suspend fun updateCompany(data: Map<String, Any?>)  = call { api.updateCompany(data) }

    suspend fun uploadCompanyLogo(file: java.io.File): Result<String> {
        return try {
            val requestFile = file.readBytes().toRequestBody("image/*".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("logo", file.name, requestFile)
            val response = api.uploadCompanyLogo(part)
            if (response.isSuccessful) {
                Result.Success(response.body()?.get("logo_url") ?: "")
            } else {
                Result.Error("Upload failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Upload failed")
        }
    }

    suspend fun getCustomFields(entity: String? = null) = call { api.getCustomFields(entity) }
    suspend fun createCustomField(data: Map<String, Any?>) = call { api.createCustomField(data) }
    suspend fun updateCustomField(id: String, data: Map<String, Any?>) = call { api.updateCustomField(id, data) }
    suspend fun deleteCustomField(id: String) = call { api.deleteCustomField(id) }
    suspend fun getJobyRules()                          = call { api.getJobyRules() }
    suspend fun updateJobyRule(id: String, data: Map<String, Any?>) = call { api.updateJobyRule(id, data) }

    // ── Users ─────────────────────────────────────────────────────────────

    suspend fun getUsers()                                           = call { api.getUsers() }
    suspend fun getTechnicians()                                     = call { api.getTechnicians() }
    suspend fun createUser(data: Map<String, Any?>)                 = call { api.createUser(data) }
    suspend fun updateUser(id: String, data: Map<String, Any?>)     = call { api.updateUser(id, data) }
    suspend fun deleteUser(id: String)                              = call { api.deleteUser(id) }
    suspend fun reactivateUser(id: String)                          = call { api.reactivateUser(id) }

    // ── Customers ─────────────────────────────────────────────────────────

    suspend fun getCustomers(search: String? = null, type: String? = null, page: Int = 1) =
        call { api.getCustomers(search, type, page) }
    suspend fun createCustomer(data: Map<String, Any?>)                = call { api.createCustomer(data) }
    suspend fun getCustomer(id: String)                                = call { api.getCustomer(id) }
    suspend fun updateCustomer(id: String, data: Map<String, Any?>)   = call { api.updateCustomer(id, data) }
    suspend fun deleteCustomer(id: String)                            = call { api.deleteCustomer(id) }
    suspend fun getCustomerStats(id: String)                          = call { api.getCustomerStats(id) }

    @Suppress("UNCHECKED_CAST")
    suspend fun getCustomerHistory(customerId: String, excludeJobId: String): com.ultimatepro.data.repository.Result<com.ultimatepro.domain.model.CustomerHistory> {
        return when (val r = call { api.getCustomerHistory(customerId, excludeJobId) }) {
            is Result.Success -> {
                val m = r.data
                val estimates = (m["estimates"] as? List<*>)?.mapNotNull { it as? Map<String, Any> }
                    ?.map { e -> com.ultimatepro.domain.model.EstimateSummary(
                        id             = e["id"] as? String ?: "",
                        estimateNumber = e["estimate_number"] as? String ?: "",
                        total          = (e["total"] as? Number)?.toDouble() ?: 0.0,
                        status         = e["status"] as? String ?: "",
                        createdAt      = e["created_at"] as? String,
                        jobId          = e["job_id"] as? String
                    ) } ?: emptyList()
                val invoices = (m["invoices"] as? List<*>)?.mapNotNull { it as? Map<String, Any> }
                    ?.map { i -> com.ultimatepro.domain.model.InvoiceSummary(
                        id            = i["id"] as? String ?: "",
                        invoiceNumber = i["invoice_number"] as? String ?: "",
                        total         = (i["total"] as? Number)?.toDouble() ?: 0.0,
                        status        = i["status"] as? String ?: "",
                        createdAt     = i["created_at"] as? String,
                        jobId         = i["job_id"] as? String
                    ) } ?: emptyList()
                val photos = (m["photos"] as? List<*>)?.mapNotNull { it as? Map<String, Any> }
                    ?.map { p -> com.ultimatepro.domain.model.HistoryPhoto(
                        id        = p["id"] as? String ?: "",
                        url       = p["url"] as? String ?: "",
                        purpose   = p["purpose"] as? String ?: "",
                        createdAt = p["created_at"] as? String,
                        jobId     = p["job_id"] as? String,
                        jobNumber = p["job_number"] as? String
                    ) } ?: emptyList()
                val notes = (m["notes"] as? List<*>)?.mapNotNull { it as? Map<String, Any> }
                    ?.map { n -> com.ultimatepro.domain.model.HistoryNote(
                        id        = n["id"] as? String ?: "",
                        content   = n["content"] as? String ?: "",
                        createdAt = n["created_at"] as? String,
                        jobId     = n["job_id"] as? String,
                        jobNumber = n["job_number"] as? String,
                        jobTitle  = n["job_title"] as? String
                    ) } ?: emptyList()
                val jobs = (m["jobs"] as? List<*>)?.mapNotNull { it as? Map<String, Any> }
                    ?.map { j -> com.ultimatepro.domain.model.JobSummary(
                        id             = j["id"] as? String ?: "",
                        jobNumber      = j["job_number"] as? String ?: "",
                        title          = j["title"] as? String ?: "",
                        status         = j["status"] as? String ?: "",
                        scheduledStart = j["scheduled_start"] as? String,
                        address        = j["address"] as? String,
                        totalCharged   = (j["total_charged"] as? Number)?.toDouble()
                    ) } ?: emptyList()
                Result.Success(com.ultimatepro.domain.model.CustomerHistory(estimates, invoices, photos, notes, jobs))
            }
            is Result.Error -> Result.Error(r.message)
        }
    }

    suspend fun getCustomerContacts(id: String)                       = call { api.getCustomerContacts(id) }
    suspend fun addCustomerContact(id: String, type: String, value: String, label: String? = null) =
        call { api.addCustomerContact(id, buildMap { put("type", type); put("value", value); if (label != null) put("label", label) }) }
    suspend fun updateCustomerContact(contactId: String, value: String, label: String? = null) =
        call { api.updateCustomerContact(contactId, buildMap { put("value", value); if (label != null) put("label", label) }) }
    suspend fun deleteCustomerContact(contactId: String)              = call { api.deleteCustomerContact(contactId) }

    // ── Jobs ─────────────────────────────────────────────────────────────

    suspend fun getJobs(
        status: String? = null, techId: String? = null, custId: String? = null,
        from: String? = null, to: String? = null,
        activityFrom: String? = null, activityTo: String? = null,
        priority: String? = null, search: String? = null,
        sort: String? = null, page: Int = 1, includeAllStatuses: Boolean = false,
        partnerView: Boolean = false
    ) = call { api.getJobs(status, techId, custId, from, to, activityFrom, activityTo,
        priority, search, sort, page,
        includeAllStatuses = if (includeAllStatuses) true else null,
        partnerView = if (partnerView) true else null) }

    suspend fun getTodayJobs()                                        = call { api.getTodayJobs() }
    suspend fun createJob(data: Map<String, Any?>)                   = call { api.createJob(data) }
    suspend fun getJob(id: String)                                   = call { api.getJob(id) }
    suspend fun updateJob(id: String, data: Map<String, Any?>)       = call { api.updateJob(id, data) }
    suspend fun deleteJob(id: String)                                = call { api.deleteJob(id) }

    suspend fun updateJobStatus(id: String, status: String, notes: String? = null) =
        call { api.updateJobStatus(id, buildMap { put("status", status); if (notes != null) put("notes", notes) }) }

    suspend fun completeJob(id: String, body: Map<String, Any?>)  = call { api.completeJob(id, body) }
    suspend fun approveEarnings(id: String)                       = call { api.approveEarnings(id) }
    suspend fun getJobCompletion(id: String): Result<com.ultimatepro.domain.model.JobCompletionDetails?> {
        return when (val r = call { api.getJobCompletion(id) }) {
            is Result.Success -> {
                val m = r.data
                // Backend returns {} when no completion record exists
                if (m == null || m["id"] == null) Result.Success(null)
                else Result.Success(com.ultimatepro.domain.model.JobCompletionDetails(
                    id                   = m["id"] as? String ?: "",
                    job_id               = m["job_id"] as? String ?: "",
                    parts_paid_by        = m["parts_paid_by"] as? String,
                    parts_amount         = (m["parts_amount"] as? Double) ?: 0.0,
                    payment_collected_by = m["payment_collected_by"] as? String,
                    cc_fee_amount        = (m["cc_fee_amount"] as? Double) ?: 0.0,
                    cc_fee_paid_by       = m["cc_fee_paid_by"] as? String,
                    net_after_deductions = (m["net_after_deductions"] as? Double) ?: 0.0,
                    sender_earns         = (m["sender_earns"] as? Double) ?: 0.0,
                    receiver_earns       = (m["receiver_earns"] as? Double) ?: 0.0,
                    submitted_by         = m["submitted_by"] as? String,
                    confirmed_by         = m["confirmed_by"] as? String,
                    confirmed_at         = m["confirmed_at"] as? String,
                    status               = m["status"] as? String ?: "pending",
                    notes                = m["notes"] as? String,
                    submitter_first      = m["submitter_first"] as? String,
                    submitter_last       = m["submitter_last"] as? String,
                    confirmer_first      = m["confirmer_first"] as? String,
                    confirmer_last       = m["confirmer_last"] as? String,
                    created_at           = m["created_at"] as? String
                ))
            }
            is Result.Error -> Result.Error(r.message)
        }
    }
    suspend fun confirmJobCompletion(id: String)                   = call { api.confirmJobCompletion(id) }

    suspend fun addJobPhoto(id: String, photoUrl: String) =
        call { api.addJobPhoto(id, mapOf("photo_url" to photoUrl)) }

    // ── Schedules ─────────────────────────────────────────────────────────

    suspend fun getSchedules(from: String? = null, to: String? = null, userId: String? = null) =
        call { api.getSchedules(from, to, userId) }
    suspend fun getAvailability(start: String, end: String) = call { api.getAvailability(start, end) }
    suspend fun createSchedule(data: Map<String, Any?>)      = call { api.createSchedule(data) }
    suspend fun updateSchedule(id: String, data: Map<String, Any?>) = call { api.updateSchedule(id, data) }
    suspend fun deleteSchedule(id: String)                   = call { api.deleteSchedule(id) }

    // ── Leads ─────────────────────────────────────────────────────────────

    suspend fun getLeads(status: String? = null)             = call { api.getLeads(status) }
    suspend fun getPipeline()                                = call { api.getPipeline() }
    suspend fun createLead(data: Map<String, Any?>)          = call { api.createLead(data) }
    suspend fun updateLead(id: String, data: Map<String, Any?>) = call { api.updateLead(id, data) }
    suspend fun convertLead(id: String)                      = call { api.convertLead(id) }
    suspend fun deleteLead(id: String)                       = call { api.deleteLead(id) }

    // ── GPS ───────────────────────────────────────────────────────────────

    suspend fun pingLocation(lat: Double, lng: Double, heading: Double? = null) =
        call { api.pingLocation(buildMap { put("lat", lat); put("lng", lng); if (heading != null) put("heading", heading) }) }

    suspend fun getLiveTechs() = call { api.getLiveTechs() }

    // ── Price Book ────────────────────────────────────────────────────────

    suspend fun getPricebookCategories()                                        = call { api.getPricebookCategories() }
    suspend fun createPricebookCategory(data: Map<String, Any?>)               = call { api.createPricebookCategory(data) }
    suspend fun updatePricebookCategory(id: String, data: Map<String, Any?>)  = call { api.updatePricebookCategory(id, data) }
    suspend fun deletePricebookCategory(id: String)                            = call { api.deletePricebookCategory(id) }
    suspend fun getPricebookItems(categoryId: String? = null, search: String? = null, type: String? = null) =
        call { api.getPricebookItems(categoryId, search, type) }
    suspend fun getPricebookItem(id: String)                                   = call { api.getPricebookItem(id) }
    suspend fun createPricebookItem(data: Map<String, Any?>)                   = call { api.createPricebookItem(data) }
    suspend fun updatePricebookItem(id: String, data: Map<String, Any?>)       = call { api.updatePricebookItem(id, data) }
    suspend fun deletePricebookItem(id: String)                                = call { api.deletePricebookItem(id) }

    // ── Roster Techs ──────────────────────────────────────────────────────

    suspend fun getRosterTechs()                                          = call { api.getRosterTechs() }
    suspend fun createRosterTech(data: Map<String, Any?>)                = call { api.createRosterTech(data) }
    suspend fun updateRosterTech(id: String, data: Map<String, Any?>)   = call { api.updateRosterTech(id, data) }
    suspend fun deleteRosterTech(id: String)                             = call { api.deleteRosterTech(id) }
    suspend fun notifyRosterTech(jobId: String, method: String)          =
        call { api.notifyRosterTech(mapOf("job_id" to jobId, "method" to method)) }

    suspend fun updateJobReminderMethod(jobId: String, method: String)   =
        call { api.updateJobReminderMethod(jobId, mapOf("reminder_method" to method)) }

    suspend fun dispatchJob(jobId: String, techLat: Double, techLng: Double) =
        call { api.dispatchJob(jobId, mapOf("tech_lat" to techLat, "tech_lng" to techLng)) }

    suspend fun arrivedJob(jobId: String) = call { api.arrivedJob(jobId) }
    suspend fun getJobParts(jobId: String) = call { api.getJobParts(jobId) }
    suspend fun addJobPart(jobId: String, data: Map<String, Any?>) = call { api.addJobPart(jobId, data) }
    suspend fun deleteJobPart(jobId: String, partId: String) = call { api.deleteJobPart(jobId, partId) }

    // ── Estimates ─────────────────────────────────────────────────────────

    suspend fun getEstimates(status: String? = null, custId: String? = null, jobId: String? = null, page: Int = 1) =
        call { api.getEstimates(status, custId, jobId, page) }
    suspend fun createEstimate(data: Map<String, Any?>)              = call { api.createEstimate(data) }
    suspend fun getEstimate(id: String)                              = call { api.getEstimate(id) }
    suspend fun updateEstimate(id: String, data: Map<String, Any?>) = call { api.updateEstimate(id, data) }
    suspend fun sendEstimate(id: String, sendSms: Boolean = true, sendEmail: Boolean = true, emails: List<String> = emptyList(), phones: List<String> = emptyList()): Result<Map<String, Any>> {
        android.util.Log.d("SendEstimate", "repo.sendEstimate emails=$emails phones=$phones")
        return call { api.sendEstimate(id, mapOf("send_sms" to sendSms, "send_email" to sendEmail, "emails" to emails, "phones" to phones)) }
    }
    suspend fun signEstimate(id: String, signature: String, signerName: String? = null) =
        call { api.signEstimate(id, buildMap { put("signature", signature); if (signerName != null) put("signer_name", signerName) }) }
    suspend fun approveEstimate(id: String, signatureUrl: String?, signerName: String?) =
        call { api.approveEstimate(id, mapOf("signature_url" to signatureUrl, "signer_name" to signerName)) }
    suspend fun convertEstimateToInvoice(id: String)                = call { api.convertEstimateToInvoice(id) }
    suspend fun addEstimatePhoto(id: String, url: String, photoType: String = "before") =
        call { api.addEstimatePhoto(id, mapOf("url" to url, "photo_type" to photoType)) }
    suspend fun getEstimateTiers(id: String)                        = call { api.getEstimateTiers(id) }
    suspend fun saveEstimateTiers(id: String, tiers: List<Map<String, Any?>>) =
        call { api.saveEstimateTiers(id, mapOf("tiers" to tiers)) }
    suspend fun selectEstimateTier(id: String, tierId: String)      =
        call { api.selectEstimateTier(id, mapOf("tier_id" to tierId)) }
    suspend fun deleteEstimate(id: String)                          = call { api.deleteEstimate(id) }
    suspend fun updateDepositSettings(id: String, depositRequired: Boolean, depositAmount: Double, depositType: String) =
        call { api.updateDepositSettings(id, mapOf("deposit_required" to depositRequired, "deposit_amount" to depositAmount, "deposit_type" to depositType)) }
    suspend fun collectDeposit(id: String, amountCollected: Double, paymentMethod: String) =
        call { api.collectDeposit(id, mapOf("amount_collected" to amountCollected, "payment_method" to paymentMethod)) }

    // ── Invoices ─────────────────────────────────────────────────────────

    suspend fun getInvoices(status: String? = null, custId: String? = null, jobId: String? = null, overdue: Boolean? = null, page: Int = 1) =
        call { api.getInvoices(status, custId, jobId, overdue, page) }
    suspend fun createInvoice(data: Map<String, Any?>)              = call { api.createInvoice(data) }
    suspend fun getInvoice(id: String)                              = call { api.getInvoice(id) }
    suspend fun updateInvoice(id: String, data: Map<String, Any?>) = call { api.updateInvoice(id, data) }
    suspend fun sendInvoice(id: String, sendSms: Boolean = true, sendEmail: Boolean = true, emails: List<String> = emptyList(), phones: List<String> = emptyList()) =
        call { api.sendInvoice(id, mapOf("send_sms" to sendSms, "send_email" to sendEmail, "emails" to emails, "phones" to phones)) }
    suspend fun signInvoice(id: String, signature: String) =
        call { api.signInvoice(id, mapOf("signature" to signature)) }
    suspend fun recordInvoicePayment(id: String, method: String, amount: Double?, notes: String? = null) =
        call { api.recordInvoicePayment(id, buildMap {
            put("method", method); if (amount != null) put("amount", amount); if (notes != null) put("notes", notes)
        }) }
    suspend fun sendInvoiceReceipt(id: String, sendSms: Boolean = true, sendEmail: Boolean = true, emails: List<String> = emptyList(), phones: List<String> = emptyList(), sendReviewRequest: Boolean = false) =
        call { api.sendInvoiceReceipt(id, mapOf("send_sms" to sendSms, "send_email" to sendEmail, "emails" to emails, "phones" to phones, "send_review_request" to sendReviewRequest)) }
    suspend fun getInvoiceScanpayQr(id: String)                     = call { api.getInvoiceScanpayQr(id) }
    suspend fun voidInvoice(id: String)                             = call { api.voidInvoice(id) }
    suspend fun stopInvoiceFollowup(id: String)                     = call { api.stopInvoiceFollowup(id) }
    suspend fun resetInvoiceFollowup(id: String)                    = call { api.resetInvoiceFollowup(id) }

    // ── Payments ─────────────────────────────────────────────────────────

    suspend fun getPayments(invoiceId: String? = null, customerId: String? = null, status: String? = null, page: Int = 1) =
        call { api.getPayments(invoiceId, customerId, status, page) }
    suspend fun recordPayment(data: Map<String, Any?>)              = call { api.recordPayment(data) }
    suspend fun scanpayCharge(data: Map<String, Any?>)              = call { api.scanpayCharge(data) }
    suspend fun createScanPayQr(invoiceId: String, amount: Double)  =
        call { api.createScanPayQr(mapOf("invoice_id" to invoiceId, "amount" to amount)) }
    suspend fun createScanPayLink(invoiceId: String, amount: Double, customerPhone: String? = null) =
        call { api.createScanPayLink(buildMap {
            put("invoice_id", invoiceId); put("amount", amount)
            if (customerPhone != null) put("customer_phone", customerPhone)
        }) }
    suspend fun getScanPayStatus(invoiceId: String)                 = call { api.getScanPayStatus(invoiceId) }
    suspend fun getPaymentSummary(from: String? = null, to: String? = null) = call { api.getPaymentSummary(from, to) }

    // ── Phone ─────────────────────────────────────────────────────────────

    suspend fun getPhoneNumbers()                                    = call { api.getPhoneNumbers() }
    suspend fun getCallLogs(customerId: String? = null, type: String? = null, page: Int = 1) =
        call { api.getCallLogs(customerId, type, page) }
    suspend fun makeOutboundCall(to: String, customerId: String?)    =
        call { api.makeOutboundCall(buildMap { put("to", to); if (customerId != null) put("customer_id", customerId) }) }
    suspend fun dispositionCall(callSid: String, disposition: String, customerId: String? = null, jobId: String? = null, notes: String? = null) =
        call { api.dispositionCall(callSid, buildMap {
            put("disposition", disposition)
            if (customerId != null) put("customer_id", customerId)
            if (jobId != null) put("job_id", jobId)
            if (notes != null) put("notes", notes)
        })}
    suspend fun getLiveQueue()                                       = call { api.getLiveQueue() }
    suspend fun sendSecondChanceSms(id: String)                      = call { api.sendSecondChanceSms(id) }
    suspend fun getSecondChanceLeads(status: String = "new", page: Int = 1) = call { api.getSecondChanceLeads(status, page) }
    suspend fun updateSecondChanceLead(id: String, data: Map<String, String?>) = call { api.updateSecondChanceLead(id, data) }
    suspend fun getCsrStats(from: String? = null, to: String? = null)         = call { api.getCsrStats(from, to) }
    suspend fun getSourceStats(from: String? = null, to: String? = null)      = call { api.getSourceStats(from, to) }
    suspend fun activateCallSecure(jobId: String)                  = call { api.activateCallSecure(mapOf("job_id" to jobId)) }
    suspend fun sendSms(to: String, body: String, customerId: String? = null) =
        call { api.sendSms(buildMap { put("to", to); put("body", body); if (customerId != null) put("customer_id", customerId) }) }
    suspend fun getCallFlows()                                       = call { api.getCallFlows() }
    suspend fun createCallFlow(data: Map<String, Any?>)             = call { api.createCallFlow(data) }
    suspend fun updateCallFlow(id: String, data: Map<String, Any?>) = call { api.updateCallFlow(id, data) }

    // ── Reports ───────────────────────────────────────────────────────────

    suspend fun getDashboardReport(from: String? = null, to: String? = null)         = call { api.getDashboardReport(from, to) }
    suspend fun getRevenueReport(from: String? = null, to: String? = null, groupBy: String = "day") = call { api.getRevenueReport(from, to, groupBy) }
    suspend fun getJobsReport(from: String? = null, to: String? = null)             = call { api.getJobsReport(from, to) }
    suspend fun getCallsReport(from: String? = null, to: String? = null)            = call { api.getCallsReport(from, to) }
    suspend fun getEarningsReport(from: String? = null, to: String? = null, userId: String? = null) = call { api.getEarningsReport(from, to, userId) }

    // ── Notifications ─────────────────────────────────────────────────────

    suspend fun getNotifications(unreadOnly: Boolean? = null)       = call { api.getNotifications(unreadOnly) }
    suspend fun markAllRead()                                        = call { api.markAllRead() }
    suspend fun markRead(id: String)                                = call { api.markRead(id) }

    // ── Bookings ─────────────────────────────────────────────────────────

    suspend fun getBookings(status: String = "pending")             = call { api.getBookings(status) }
    suspend fun confirmBooking(id: String, assignedTo: String?)     = call { api.confirmBooking(id, buildMap { if (assignedTo != null) put("assigned_to", assignedTo) }) }
    suspend fun cancelBooking(id: String, reason: String?)          = call { api.cancelBooking(id, buildMap { if (reason != null) put("reason", reason) }) }
    suspend fun getWidgetEmbedCode(companyId: String)               = call { api.getWidgetEmbedCode(companyId) }

    // ── Uploads ───────────────────────────────────────────────────────────

    suspend fun uploadFile(part: okhttp3.MultipartBody.Part, purpose: String? = "photos", entityType: String? = null, entityId: String? = null) =
        call { api.uploadFile(part, purpose, entityType, entityId) }

    suspend fun getUploads(entityType: String, entityId: String, purpose: String? = null) =
        call { api.getUploads(entityType, entityId, purpose) }

    suspend fun deleteUpload(filename: String) =
        call { api.deleteUpload(filename) }

    // ── Payroll ──────────────────────────────────────────────────────────

    suspend fun getPayrollSummary(params: Map<String, String>)                   = call { api.getPayrollSummary(params) }
    suspend fun markEarningsPaid(body: Map<String, Any?>)                        = call { api.markEarningsPaid(body) }
    suspend fun getPermissionSchema()                                            = call { api.getPermissionSchema() }
    suspend fun getJobReport(params: Map<String, String>)                        = call { api.getJobReport(params) }
    suspend fun getTechReport(userId: String, params: Map<String, String>)       = call { api.getTechReport(userId, params) }
    suspend fun getProfitBySource(params: Map<String, String>)                   = call { api.getProfitBySource(params) }
    suspend fun getProfitRules()                                                 = call { api.getProfitRules() }
    suspend fun createProfitRule(data: Map<String, Any?>)                        = call { api.createProfitRule(data) }
    suspend fun updateProfitRule(id: String, data: Map<String, Any?>)            = call { api.updateProfitRule(id, data) }
    suspend fun deleteProfitRule(id: String)                                     = call { api.deleteProfitRule(id) }
    suspend fun getBonuses(userId: String? = null)                               = call { api.getBonuses(userId) }
    suspend fun addBonus(data: Map<String, Any?>)                                = call { api.addBonus(data) }
    suspend fun deleteBonus(id: String)                                          = call { api.deleteBonus(id) }
    suspend fun getDeductions(userId: String? = null)                            = call { api.getDeductions(userId) }
    suspend fun addDeduction(data: Map<String, Any?>)                            = call { api.addDeduction(data) }
    suspend fun deleteDeduction(id: String)                                      = call { api.deleteDeduction(id) }
    suspend fun recalculateJobEarnings(jobId: String)                            = call { api.recalculateJobEarnings(jobId) }

    // ── Reimbursements ─────────────────────────────────────────────────
    suspend fun getReimbursements(userId: String? = null, status: String? = null) =
        call { api.getReimbursements(userId, status) }

    suspend fun approveReimbursement(id: String) = call { api.approveReimbursement(id) }
    suspend fun payReimbursement(id: String)      = call { api.payReimbursement(id) }

    // ── Tech pay settings ──────────────────────────────────────────────
    suspend fun getTechPaySettings(userId: String) = call { api.getTechPaySettings(userId) }

    suspend fun updateTechPaySettings(userId: String, data: Map<String, Any?>) =
        call { api.updateTechPaySettings(userId, data) }

    // ── Profit simulator ───────────────────────────────────────────────
    suspend fun simulateProfit(data: Map<String, Any?>) = call { api.simulateProfit(data) }

    // ── Send payroll report ─────────────────────────────────────────────
    suspend fun sendPayrollReport(userId: String, period: String, sendEmail: Boolean = true, sendSms: Boolean = false) =
        call { api.sendPayrollReport(userId, mapOf(
            "period"      to period,
            "send_email"  to sendEmail,
            "send_sms"    to sendSms
        ))}

    // ── Get single user ─────────────────────────────────────────────────
    suspend fun getUser(id: String) = call { api.getUser(id) }

    // ── Full user profile save ──────────────────────────────────────────
    suspend fun saveUserProfile(id: String, data: Map<String, Any?>) =
        call { api.updateUser(id, data) }

    // ── Ticket parsing ──────────────────────────────────────────────────
    suspend fun parseTicket(text: String) = call { api.parseTicket(mapOf("text" to text)) }

    // ── Contractor Network ───────────────────────────────────────────────
    suspend fun getMyNetworkId()                                   = call { api.getMyNetworkId() }
    suspend fun getConnections()                                   = call { api.getConnections() }
    suspend fun getActiveConnectionsSimple()                       = call { api.getActiveConnectionsSimple() }
    suspend fun getConnection(id: String)                          = call { api.getConnection(id) }
    suspend fun inviteConnection(body: Map<String, Any?>)          = call { api.inviteConnection(body) }
    suspend fun respondConnection(id: String, action: String)      = call { api.respondConnection(id, mapOf("action" to action)) }
    suspend fun pauseConnection(id: String)                        = call { api.pauseConnection(id) }
    suspend fun proposeAgreement(body: Map<String, Any?>)          = call { api.proposeAgreement(body) }
    suspend fun getAgreements(connectionId: String)                = call { api.getAgreements(connectionId) }
    suspend fun respondAgreement(id: String, body: Map<String, Any?>) = call { api.respondAgreement(id, body) }
    suspend fun searchCompanies(q: String, type: String)           = call { api.searchCompanies(q, type) }
    suspend fun getPartnerReport(id: String, dateFrom: String? = null, dateTo: String? = null) =
        call { api.getPartnerReport(id, dateFrom, dateTo) }
    suspend fun sendPartnerReport(id: String, body: Map<String, Any?>) = call { api.sendPartnerReport(id, body) }

    // ── Job partner sharing ───────────────────────────────────────────────
    suspend fun sendJobToPartner(jobId: String, body: Map<String, Any?>) = call { api.sendJobToPartner(jobId, body) }
    suspend fun confirmPartnerStatus(jobId: String, action: String)      = call { api.confirmPartnerStatus(jobId, mapOf("action" to action)) }
    suspend fun getPartnerJobs()                                         = call { api.getJobs(partnerView = true) }

    suspend fun getCompanyRaw(): Result<Map<String, Any>> = call { api.getCompanyMap() }

    // ── Booking Settings ──────────────────────────────────────────────────
    @Suppress("UNCHECKED_CAST")
    suspend fun getBookingSettings(): Result<BookingSettings> {
        return when (val r = call { api.getBookingSettings() }) {
            is Result.Success -> Result.Success(mapToBookingSettings(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun updateBookingSettings(
        enabled: Boolean? = null,
        companyDisplayName: String? = null,
        companyTagline: String? = null,
        primaryColor: String? = null,
        workingDays: List<String>? = null,
        timeWindows: List<Map<String, Any>>? = null,
        maxBookingsPerWindow: Int? = null,
        serviceAreaZips: String? = null,
        serviceAreas: List<ServiceArea>? = null,
        services: List<String>? = null,
        confirmationMessage: String? = null,
        reminderEnabled: Boolean? = null,
        reminderHoursBefore: Int? = null,
        reminderMethod: String? = null,
        followupEnabled: Boolean? = null,
        followupDaysAfter: Int? = null,
        followupRepeatEvery: Int? = null,
        followupMaxReminders: Int? = null,
        followupMethod: String? = null
    ): Result<BookingSettings> {
        val body = buildMap<String, Any?> {
            enabled?.let { put("enabled", it) }
            companyDisplayName?.let { put("company_display_name", it) }
            companyTagline?.let { put("company_tagline", it) }
            primaryColor?.let { put("primary_color", it) }
            workingDays?.let { put("working_days", it) }
            timeWindows?.let { put("time_windows", it) }
            maxBookingsPerWindow?.let { put("max_bookings_per_window", it) }
            serviceAreaZips?.let { put("service_area_zips", it) }
            serviceAreas?.let { list ->
                put("service_areas", list.map { sa ->
                    mapOf("zip_code" to sa.zipCode, "radius_miles" to sa.radiusMiles, "label" to sa.label)
                })
            }
            services?.let { put("services", it) }
            confirmationMessage?.let { put("confirmation_message", it) }
            reminderEnabled?.let { put("reminder_enabled", it) }
            reminderHoursBefore?.let { put("reminder_hours_before", it) }
            reminderMethod?.let { put("reminder_method", it) }
            followupEnabled?.let { put("followup_enabled", it) }
            followupDaysAfter?.let { put("followup_days_after", it) }
            followupRepeatEvery?.let { put("followup_repeat_every", it) }
            followupMaxReminders?.let { put("followup_max_reminders", it) }
            followupMethod?.let { put("followup_method", it) }
        }
        return when (val r = call { api.updateBookingSettings(body) }) {
            is Result.Success -> Result.Success(mapToBookingSettings(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToBookingSettings(m: Map<String, Any>): BookingSettings {
        val rawWindows = m["time_windows"] as? List<*> ?: emptyList<Any>()
        val windows = rawWindows.filterIsInstance<Map<String, Any>>().map { w ->
            TimeWindow(
                id      = w["id"] as? String ?: "",
                label   = w["label"] as? String ?: "",
                time    = w["time"] as? String ?: "",
                enabled = w["enabled"] as? Boolean ?: true
            )
        }
        val rawServiceAreas = m["service_areas"] as? List<*> ?: emptyList<Any>()
        val serviceAreas = rawServiceAreas.filterIsInstance<Map<String, Any>>().map { sa ->
            ServiceArea(
                zipCode     = sa["zip_code"] as? String ?: "",
                radiusMiles = (sa["radius_miles"] as? Double)?.toInt() ?: 25,
                label       = sa["label"] as? String
            )
        }
        val rawServices = m["services"] as? List<*> ?: emptyList<Any>()
        val rawDays = m["working_days"] as? List<*> ?: emptyList<Any>()
        return BookingSettings(
            id                   = m["id"] as? String,
            enabled              = m["enabled"] as? Boolean ?: false,
            companyDisplayName   = m["company_display_name"] as? String,
            companyTagline       = m["company_tagline"] as? String,
            primaryColor         = m["primary_color"] as? String ?: "#1A73E8",
            workingDays          = rawDays.filterIsInstance<String>(),
            timeWindows          = windows,
            maxBookingsPerWindow = (m["max_bookings_per_window"] as? Double)?.toInt() ?: 3,
            serviceAreaZips      = m["service_area_zips"] as? String ?: "",
            serviceAreas         = serviceAreas,
            services             = rawServices.filterIsInstance<String>(),
            confirmationMessage  = m["confirmation_message"] as? String
                                   ?: "Thank you! We will confirm your appointment shortly.",
            reminderEnabled      = m["reminder_enabled"] as? Boolean ?: true,
            reminderHoursBefore  = (m["reminder_hours_before"] as? Double)?.toInt() ?: 24,
            reminderMethod       = m["reminder_method"] as? String ?: "email",
            followupEnabled      = m["followup_enabled"] as? Boolean ?: false,
            followupDaysAfter    = (m["followup_days_after"] as? Double)?.toInt() ?: 3,
            followupRepeatEvery  = (m["followup_repeat_every"] as? Double)?.toInt() ?: 7,
            followupMaxReminders = (m["followup_max_reminders"] as? Double)?.toInt() ?: 3,
            followupMethod       = m["followup_method"] as? String ?: "email"
        )
    }

    // ── Review Platforms ──────────────────────────────────────────────────
    @Suppress("UNCHECKED_CAST")
    suspend fun getReviewPlatforms(): Result<List<ReviewPlatform>> {
        return when (val r = call { api.getReviewPlatforms() }) {
            is Result.Success -> Result.Success(r.data.map { m -> mapToReviewPlatform(m) })
            is Result.Error   -> Result.Error(r.message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToReviewPlatform(m: Map<String, Any>) = ReviewPlatform(
        id        = m["id"] as? String ?: "",
        name      = m["name"] as? String ?: "",
        url       = m["url"] as? String ?: "",
        isDefault = m["is_default"] as? Boolean ?: false,
        isActive  = m["is_active"] as? Boolean ?: true,
        createdAt = m["created_at"] as? String
    )

    suspend fun createReviewPlatform(name: String, url: String, isDefault: Boolean, isActive: Boolean): Result<ReviewPlatform> {
        return when (val r = call { api.createReviewPlatform(mapOf("name" to name, "url" to url, "is_default" to isDefault, "is_active" to isActive)) }) {
            is Result.Success -> Result.Success(mapToReviewPlatform(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    suspend fun updateReviewPlatform(id: String, name: String? = null, url: String? = null, isDefault: Boolean? = null, isActive: Boolean? = null): Result<ReviewPlatform> {
        val body = buildMap<String, Any?> {
            name?.let { put("name", it) }
            url?.let { put("url", it) }
            isDefault?.let { put("is_default", it) }
            isActive?.let { put("is_active", it) }
        }
        return when (val r = call { api.updateReviewPlatform(id, body) }) {
            is Result.Success -> Result.Success(mapToReviewPlatform(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    suspend fun deleteReviewPlatform(id: String) = call { api.deleteReviewPlatform(id) }

    // ── Job Sources ───────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    suspend fun getSourceContacts(): Result<List<JobSource>> {
        return when (val r = call { api.getSourceContacts() }) {
            is Result.Success -> Result.Success(r.data.map { mapToJobSource(it) })
            is Result.Error   -> Result.Error(r.message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToJobSource(m: Map<String, Any>) = JobSource(
        id                  = m["id"] as? String ?: "",
        name                = m["name"] as? String ?: "",
        companyName         = m["company_name"] as? String,
        phone               = m["phone"] as? String,
        email               = m["email"] as? String,
        profitAllocationPct = (m["profit_allocation_pct"] as? Number)?.toDouble() ?: 0.0,
        sendUpdates         = m["send_updates"] as? Boolean ?: true,
        sendClosings        = m["send_closings"] as? Boolean ?: true,
        notes               = m["notes"] as? String,
        isActive            = m["is_active"] as? Boolean ?: true
    )

    suspend fun createSourceContact(
        name: String, companyName: String? = null, phone: String? = null, email: String? = null,
        profitAllocationPct: Double = 0.0, sendUpdates: Boolean = true, sendClosings: Boolean = true,
        notes: String? = null
    ): Result<JobSource> {
        val body = buildMap<String, Any?> {
            put("name", name); put("company_name", companyName); put("phone", phone)
            put("email", email); put("profit_allocation_pct", profitAllocationPct)
            put("send_updates", sendUpdates); put("send_closings", sendClosings); put("notes", notes)
        }
        return when (val r = call { api.createSourceContact(body) }) {
            is Result.Success -> Result.Success(mapToJobSource(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    suspend fun updateSourceContact(id: String, body: Map<String, Any?>): Result<JobSource> {
        return when (val r = call { api.updateSourceContact(id, body) }) {
            is Result.Success -> Result.Success(mapToJobSource(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    suspend fun deleteSourceContact(id: String) = call { api.deleteSourceContact(id) }

    // ── Ad Channels ───────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    suspend fun getAdChannels(): Result<List<AdChannel>> {
        return when (val r = call { api.getAdChannels() }) {
            is Result.Success -> Result.Success(r.data.map { mapToAdChannel(it) })
            is Result.Error   -> Result.Error(r.message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToAdChannel(m: Map<String, Any>) = AdChannel(
        id           = m["id"] as? String ?: "",
        name         = m["name"] as? String ?: "",
        isCustom     = m["is_custom"] as? Boolean ?: false,
        isActive     = m["is_active"] as? Boolean ?: true,
        displayOrder = (m["display_order"] as? Number)?.toInt() ?: 0
    )

    suspend fun createAdChannel(name: String): Result<AdChannel> {
        return when (val r = call { api.createAdChannel(mapOf("name" to name)) }) {
            is Result.Success -> Result.Success(mapToAdChannel(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    suspend fun updateAdChannel(id: String, name: String? = null, isActive: Boolean? = null): Result<AdChannel> {
        val body = buildMap<String, Any?> {
            name?.let { put("name", it) }
            isActive?.let { put("is_active", it) }
        }
        return when (val r = call { api.updateAdChannel(id, body) }) {
            is Result.Success -> Result.Success(mapToAdChannel(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    // ── Commission Rules ──────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    suspend fun getCommissionRules(): Result<List<CommissionRule>> {
        return when (val r = call { api.getCommissionRules() }) {
            is Result.Success -> Result.Success(r.data.map { mapToCommissionRule(it) })
            is Result.Error   -> Result.Error(r.message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToCommissionRule(m: Map<String, Any>) = CommissionRule(
        id                = m["id"] as? String ?: "",
        ruleType          = m["rule_type"] as? String ?: "default",
        jobSourceId       = m["job_source_id"] as? String,
        jobSourceName     = m["job_source_name"] as? String,
        adChannelId       = m["ad_channel_id"] as? String,
        adChannelName     = m["ad_channel_name"] as? String,
        techCommissionPct = (m["tech_commission_pct"] as? Number)?.toDouble() ?: 0.0,
        notes             = m["notes"] as? String
    )

    suspend fun upsertCommissionRule(
        ruleType: String, jobSourceId: String? = null, adChannelId: String? = null,
        techCommissionPct: Double, notes: String? = null
    ): Result<CommissionRule> {
        val body = buildMap<String, Any?> {
            put("rule_type", ruleType)
            put("job_source_id", jobSourceId)
            put("ad_channel_id", adChannelId)
            put("tech_commission_pct", techCommissionPct)
            put("notes", notes)
        }
        return when (val r = call { api.upsertCommissionRule(body) }) {
            is Result.Success -> Result.Success(mapToCommissionRule(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    suspend fun deleteCommissionRule(id: String) = call { api.deleteCommissionRule(id) }

    @Suppress("UNCHECKED_CAST")
    suspend fun resolveCommissionForJob(jobId: String): Result<ResolvedCommission> {
        return when (val r = call { api.resolveCommissionForJob(jobId) }) {
            is Result.Success -> {
                val m = r.data
                Result.Success(ResolvedCommission(
                    pct        = (m["pct"] as? Number)?.toDouble(),
                    ruleType   = m["rule_type"] as? String,
                    sourceName = m["source_name"] as? String,
                    isDefault  = m["is_default"] as? Boolean ?: false
                ))
            }
            is Result.Error -> Result.Error(r.message)
        }
    }

    // ── Source Report ─────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    suspend fun getSourceReport(dateFrom: String? = null, dateTo: String? = null): Result<SourceReport> {
        return when (val r = call { api.getSourceReport(dateFrom, dateTo) }) {
            is Result.Success -> {
                val m = r.data
                fun parseRows(key: String, includePct: Boolean): List<SourceReportRow> =
                    (m[key] as? List<*>)?.filterIsInstance<Map<String, Any>>()?.map { row ->
                        SourceReportRow(
                            sourceName          = row["source_name"] as? String ?: "",
                            jobCount            = (row["job_count"] as? Number)?.toInt() ?: 0,
                            totalRevenue        = (row["total_revenue"] as? Number)?.toDouble() ?: 0.0,
                            avgTicket           = (row["avg_ticket"] as? Number)?.toDouble() ?: 0.0,
                            profitAllocationPct = if (includePct) (row["profit_allocation_pct"] as? Number)?.toDouble() else null
                        )
                    } ?: emptyList()
                Result.Success(SourceReport(
                    network          = parseRows("network", false),
                    externalContacts = parseRows("external_contacts", true),
                    ownCompany       = parseRows("own_company", false)
                ))
            }
            is Result.Error -> Result.Error(r.message)
        }
    }

    // ── Inventory ─────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    suspend fun getInventorySettings(): Result<InventorySettings> {
        return when (val r = call { api.getInventorySettings() }) {
            is Result.Success -> Result.Success(mapToInventorySettings(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    suspend fun updateInventorySettings(enabled: Boolean): Result<InventorySettings> {
        return when (val r = call { api.updateInventorySettings(mapOf("enabled" to enabled)) }) {
            is Result.Success -> Result.Success(mapToInventorySettings(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    private fun mapToInventorySettings(m: Map<String, Any>) = InventorySettings(
        id      = m["id"] as? String,
        enabled = m["enabled"] as? Boolean ?: false
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun getWarehouseInventory(): Result<List<InventoryItem>> {
        return when (val r = call { api.getWarehouseInventory() }) {
            is Result.Success -> Result.Success(r.data.map { mapToInventoryItem(it) })
            is Result.Error   -> Result.Error(r.message)
        }
    }

    suspend fun upsertWarehouseItem(pricebookItemId: String, qtyOnHand: Int, minQty: Int) =
        call { api.upsertWarehouseItem(mapOf("pricebook_item_id" to pricebookItemId, "qty_on_hand" to qtyOnHand, "min_qty" to minQty)) }

    suspend fun updateWarehouseItem(itemId: String, qtyOnHand: Int? = null, minQty: Int? = null) =
        call { api.updateWarehouseItem(itemId, buildMap { qtyOnHand?.let { put("qty_on_hand", it) }; minQty?.let { put("min_qty", it) } }) }

    @Suppress("UNCHECKED_CAST")
    suspend fun getTrucks(): Result<List<Truck>> {
        return when (val r = call { api.getTrucks() }) {
            is Result.Success -> Result.Success(r.data.map { mapToTruck(it) })
            is Result.Error   -> Result.Error(r.message)
        }
    }

    private fun mapToTruck(m: Map<String, Any>) = Truck(
        id               = m["id"] as? String ?: "",
        name             = m["name"] as? String ?: "",
        assignedToId     = m["assigned_to_id"] as? String,
        assignedToType   = m["assigned_to_type"] as? String,
        assignedToName   = m["assigned_to_name"] as? String,
        isActive         = m["is_active"] as? Boolean ?: true,
        itemCount        = (m["item_count"] as? Number)?.toInt() ?: 0,
        lowStockCount    = (m["low_stock_count"] as? Number)?.toInt() ?: 0
    )

    suspend fun createTruck(name: String, assignedToId: String?, assignedToType: String?, assignedToName: String?): Result<Truck> {
        return when (val r = call { api.createTruck(mapOf("name" to name, "assigned_to_id" to assignedToId, "assigned_to_type" to assignedToType, "assigned_to_name" to assignedToName)) }) {
            is Result.Success -> Result.Success(mapToTruck(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    suspend fun updateTruck(id: String, body: Map<String, Any?>): Result<Truck> {
        return when (val r = call { api.updateTruck(id, body) }) {
            is Result.Success -> Result.Success(mapToTruck(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    suspend fun deleteTruck(id: String) = call { api.deleteTruck(id) }

    @Suppress("UNCHECKED_CAST")
    suspend fun getTruckStock(truckId: String): Result<List<InventoryItem>> {
        return when (val r = call { api.getTruckStock(truckId) }) {
            is Result.Success -> Result.Success(r.data.map { mapToInventoryItem(it) })
            is Result.Error   -> Result.Error(r.message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToInventoryItem(m: Map<String, Any>) = InventoryItem(
        id              = m["id"] as? String ?: "",
        pricebookItemId = m["pricebook_item_id"] as? String ?: "",
        itemName        = m["item_name"] as? String ?: "",
        sku             = m["sku"] as? String,
        unitCost        = (m["unit_cost"] as? Number)?.toDouble() ?: 0.0,
        qtyOnHand       = (m["qty_on_hand"] as? Number)?.toInt() ?: 0,
        minQty          = (m["min_qty"] as? Number)?.toInt() ?: 0,
        isPermanent     = m["is_permanent"] as? Boolean ?: false,
        isLowStock      = m["is_low_stock"] as? Boolean ?: false
    )

    suspend fun upsertTruckStockItem(truckId: String, pricebookItemId: String, qtyOnHand: Int, minQty: Int, isPermanent: Boolean) =
        call { api.upsertTruckStockItem(truckId, mapOf("pricebook_item_id" to pricebookItemId, "qty_on_hand" to qtyOnHand, "min_qty" to minQty, "is_permanent" to isPermanent)) }

    suspend fun updateTruckStockItem(truckId: String, itemId: String, qtyOnHand: Int? = null, minQty: Int? = null, isPermanent: Boolean? = null) =
        call { api.updateTruckStockItem(truckId, itemId, buildMap { qtyOnHand?.let { put("qty_on_hand", it) }; minQty?.let { put("min_qty", it) }; isPermanent?.let { put("is_permanent", it) } }) }

    suspend fun deleteTruckStockItem(truckId: String, itemId: String) = call { api.deleteTruckStockItem(truckId, itemId) }

    suspend fun sendItemsToTruck(truckId: String, items: List<Map<String, Any?>>) =
        call { api.sendItemsToTruck(truckId, mapOf("items" to items)) }

    suspend fun returnItemsFromTruck(truckId: String, items: List<Map<String, Any?>>) =
        call { api.returnItemsFromTruck(truckId, mapOf("items" to items)) }

    @Suppress("UNCHECKED_CAST")
    suspend fun getRestockRequests(status: String? = null): Result<List<RestockRequest>> {
        return when (val r = call { api.getRestockRequests(status) }) {
            is Result.Success -> Result.Success(r.data.map { mapToRestockRequest(it) })
            is Result.Error   -> Result.Error(r.message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToRestockRequest(m: Map<String, Any>) = RestockRequest(
        id                = m["id"] as? String ?: "",
        truckId           = m["truck_id"] as? String ?: "",
        truckName         = m["truck_name"] as? String,
        requestedByName   = m["requested_by_name"] as? String,
        status            = m["status"] as? String ?: "pending",
        notes             = m["notes"] as? String,
        createdAt         = m["created_at"] as? String,
        fulfilledAt       = m["fulfilled_at"] as? String,
        items             = (m["items"] as? List<*>)?.filterIsInstance<Map<String, Any>>()?.map { item ->
            RestockRequestItem(
                id               = item["id"] as? String ?: "",
                pricebookItemId  = item["pricebook_item_id"] as? String ?: "",
                itemName         = item["item_name"] as? String ?: "",
                qtyRequested     = (item["qty_requested"] as? Number)?.toInt() ?: 0,
                qtyFulfilled     = (item["qty_fulfilled"] as? Number)?.toInt() ?: 0
            )
        } ?: emptyList()
    )

    suspend fun createRestockRequest(truckId: String, notes: String?, items: List<Map<String, Any?>>) =
        call { api.createRestockRequest(mapOf("truck_id" to truckId, "notes" to notes, "items" to items)) }

    suspend fun fulfillRestockRequest(id: String, items: List<Map<String, Any?>>) =
        call { api.fulfillRestockRequest(id, mapOf("items" to items)) }

    suspend fun deductJobParts(jobId: String, truckId: String, items: List<Map<String, Any?>>) =
        call { api.deductJobParts(jobId, mapOf("truck_id" to truckId, "items" to items)) }

    @Suppress("UNCHECKED_CAST")
    suspend fun getTechTruck(userId: String): Result<Truck?> {
        return when (val r = call { api.getTechTruck(userId) }) {
            is Result.Success -> Result.Success(r.data?.let { mapToTruck(it) })
            is Result.Error   -> Result.Error(r.message)
        }
    }

    // ── Membership Plans ──────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    suspend fun getMembershipPlans(): Result<List<MembershipPlan>> {
        return when (val r = call { api.getMembershipPlans() }) {
            is Result.Success -> Result.Success(r.data.map { mapToMembershipPlan(it) })
            is Result.Error   -> Result.Error(r.message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToMembershipPlan(m: Map<String, Any>) = MembershipPlan(
        id          = m["id"] as? String ?: "",
        name        = m["name"] as? String ?: "",
        description = m["description"] as? String,
        frequency   = m["frequency"] as? String ?: "monthly",
        price       = (m["price"] as? Number)?.toDouble() ?: 0.0,
        isActive    = m["is_active"] as? Boolean ?: true
    )

    suspend fun createMembershipPlan(name: String, description: String?, frequency: String, price: Double): Result<MembershipPlan> {
        return when (val r = call { api.createMembershipPlan(mapOf("name" to name, "description" to description, "frequency" to frequency, "price" to price)) }) {
            is Result.Success -> Result.Success(mapToMembershipPlan(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    suspend fun updateMembershipPlan(id: String, name: String, description: String?, frequency: String, price: Double, isActive: Boolean): Result<MembershipPlan> {
        return when (val r = call { api.updateMembershipPlan(id, mapOf("name" to name, "description" to description, "frequency" to frequency, "price" to price, "is_active" to isActive)) }) {
            is Result.Success -> Result.Success(mapToMembershipPlan(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    suspend fun deleteMembershipPlan(id: String) = call { api.deleteMembershipPlan(id) }

    // ── Customer Memberships ──────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    suspend fun getCustomerMemberships(customerId: String): Result<List<CustomerMembership>> {
        return when (val r = call { api.getCustomerMemberships(customerId) }) {
            is Result.Success -> Result.Success(r.data.map { mapToCustomerMembership(it) })
            is Result.Error   -> Result.Error(r.message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToCustomerMembership(m: Map<String, Any>) = CustomerMembership(
        id            = m["id"] as? String ?: "",
        customerId    = m["customer_id"] as? String ?: "",
        planId        = m["plan_id"] as? String,
        planName      = m["plan_name"] as? String ?: "",
        planFrequency = m["plan_frequency"] as? String ?: "monthly",
        planPrice     = (m["plan_price"] as? Number)?.toDouble() ?: 0.0,
        status        = m["status"] as? String ?: "active",
        startDate     = m["start_date"] as? String ?: "",
        nextJobDate   = m["next_job_date"] as? String,
        notes         = m["notes"] as? String
    )

    suspend fun createCustomerMembership(customerId: String, planId: String, startDate: String? = null, endDate: String? = null, renewalDate: String? = null, notes: String? = null): Result<CustomerMembership> {
        val body = mapOf("plan_id" to planId, "start_date" to startDate, "end_date" to endDate, "renewal_date" to renewalDate, "notes" to notes)
        android.util.Log.d("MembershipRepo", "createCustomerMembership customerId=$customerId body=$body")
        return when (val r = call { api.createCustomerMembership(customerId, body) }) {
            is Result.Success -> Result.Success(mapToCustomerMembership(r.data))
            is Result.Error   -> {
                android.util.Log.e("MembershipRepo", "createCustomerMembership failed: code=${r.code} message=${r.message}")
                Result.Error(r.message, r.code)
            }
        }
    }

    suspend fun updateCustomerMembership(id: String, status: String? = null, nextJobDate: String? = null, notes: String? = null): Result<CustomerMembership> {
        return when (val r = call { api.updateCustomerMembership(id, mapOf("status" to status, "next_job_date" to nextJobDate, "notes" to notes)) }) {
            is Result.Success -> Result.Success(mapToCustomerMembership(r.data))
            is Result.Error   -> Result.Error(r.message)
        }
    }

    suspend fun deleteCustomerMembership(id: String) = call { api.deleteCustomerMembership(id) }

    suspend fun createNextMembershipJob(membershipId: String, description: String? = null, techId: String? = null) =
        call { api.createNextMembershipJob(membershipId, mapOf("description" to description, "tech_id" to techId)) }

    @Suppress("UNCHECKED_CAST")
    suspend fun getMembershipsDueSoon(): Result<List<MembershipDueSoon>> {
        return when (val r = call { api.getMembershipsDueSoon() }) {
            is Result.Success -> Result.Success(r.data.map { m ->
                MembershipDueSoon(
                    id            = m["id"] as? String ?: "",
                    customerId    = m["customer_id"] as? String ?: "",
                    planName      = m["plan_name"] as? String ?: "",
                    planFrequency = m["plan_frequency"] as? String ?: "",
                    nextJobDate   = m["next_job_date"] as? String,
                    firstName     = m["first_name"] as? String ?: "",
                    lastName      = m["last_name"] as? String ?: "",
                    address       = m["address"] as? String,
                    city          = m["city"] as? String,
                    state         = m["state"] as? String
                )
            })
            is Result.Error -> Result.Error(r.message)
        }
    }

    // ── SMS Conversations ─────────────────────────────────────────────────────
    suspend fun getSmsConversations(): Result<List<SmsConversation>>      = call { api.getSmsConversations() }
    suspend fun getConversationMessages(conversationId: String): Result<List<SmsMessage>> = call { api.getConversationMessages(conversationId) }
    suspend fun sendSmsReply(conversationId: String, message: String): Result<SmsMessage> =
        call { api.sendSmsReply(conversationId, mapOf("message" to message)) }
    suspend fun getCustomerMessages(customerId: String): Result<List<SmsMessage>> = call { api.getCustomerMessages(customerId) }
    suspend fun getJobMessages(jobId: String): Result<List<SmsMessage>>   = call { api.getJobMessages(jobId) }

    // ── Timesheets ────────────────────────────────────────────────────────────
    suspend fun clockIn(): Result<Timesheet>                          = call { api.clockIn() }
    suspend fun clockOut(): Result<Timesheet>                         = call { api.clockOut() }
    suspend fun getTodayTimesheet(): Result<Timesheet>                = call { api.getTodayTimesheet() }
    suspend fun getTimesheetStatus(): Result<TimesheetStatus>         = call { api.getTimesheetStatus() }
    suspend fun getTimesheetReport(startDate: String, endDate: String, userId: String? = null): Result<TimesheetReport> =
        call { api.getTimesheetReport(startDate, endDate, userId) }

    // ── Import Wizard ────────────────────────────────────────────────────────
    suspend fun previewImport(fileBytes: ByteArray, fileName: String, type: String): Result<ImportPreviewResponse> {
        val mediaType  = "application/octet-stream".toMediaTypeOrNull()
        val requestFile = fileBytes.toRequestBody(mediaType)
        val filePart    = MultipartBody.Part.createFormData("file", fileName, requestFile)
        val typePart    = type.toRequestBody("text/plain".toMediaTypeOrNull())
        return call { api.previewImport(filePart, typePart) }
    }

    suspend fun executeImport(request: ImportExecuteRequest): Result<ImportResultResponse> =
        call { api.executeImport(request) }

    // ── Report CSV Export ─────────────────────────────────────────────────────
    suspend fun exportRevenueCsv(from: String? = null, to: String? = null) = call { api.exportRevenueCsv(from, to) }
    suspend fun exportJobsCsv(from: String? = null, to: String? = null)    = call { api.exportJobsCsv(from, to) }
    suspend fun exportEarningsCsv(from: String? = null, to: String? = null) = call { api.exportEarningsCsv(from, to) }

    // ── QuickBooks Online ─────────────────────────────────────────────────────
    suspend fun getQboStatus()      = call { api.getQboStatus() }
    suspend fun getQboConnectUrl()  = call { api.getQboConnectUrl() }
    suspend fun syncQboCustomers()  = call { api.syncQboCustomers() }
    suspend fun syncQboInvoices()   = call { api.syncQboInvoices() }
    suspend fun syncQboPayments()   = call { api.syncQboPayments() }
    suspend fun syncQboAll()        = call { api.syncQboAll() }
    suspend fun disconnectQbo()     = call { api.disconnectQbo() }
}
