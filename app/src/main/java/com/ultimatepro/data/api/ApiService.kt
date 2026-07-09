package com.ultimatepro.data.api

import com.ultimatepro.domain.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Auth ─────────────────────────────────────────────────────────────
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: Map<String, String>): Response<Map<String, String>>

    @GET("auth/me")
    suspend fun getMe(): Response<Map<String, Any>>

    @PUT("auth/me")
    suspend fun updateMe(@Body body: Map<String, @JvmSuppressWildcards String?>): Response<User>

    @PUT("auth/change-password")
    suspend fun changePassword(@Body body: Map<String, String>): Response<Map<String, String>>

    @PUT("auth/fcm-token")
    suspend fun updateFcmToken(@Body body: Map<String, String>): Response<Map<String, String>>

    @POST("auth/logout")
    suspend fun logout(): Response<Map<String, String>>

    // ── Company ───────────────────────────────────────────────────────────
    @GET("company")
    suspend fun getCompany(): Response<Company>

    @PUT("company")
    suspend fun updateCompany(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Company>

    @Multipart
    @POST("company/logo")
    suspend fun uploadCompanyLogo(
        @Part logo: MultipartBody.Part
    ): Response<Map<String, String>>

    @GET("company/custom-fields")
    suspend fun getCustomFields(@Query("entity") entity: String? = null): Response<List<Map<String, Any>>>

    @GET("company/joby-rules")
    suspend fun getJobyRules(): Response<List<Map<String, Any>>>

    @PUT("company/joby-rules/{id}")
    suspend fun updateJobyRule(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @POST("company/custom-fields")
    suspend fun createCustomField(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @PUT("company/custom-fields/{id}")
    suspend fun updateCustomField(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @DELETE("company/custom-fields/{id}")
    suspend fun deleteCustomField(@Path("id") id: String): Response<Map<String, String>>

    // ── Users ─────────────────────────────────────────────────────────────
    @GET("users")
    suspend fun getUsers(): Response<List<User>>

    @GET("users/technicians")
    suspend fun getTechnicians(): Response<List<User>>

    @POST("users")
    suspend fun createUser(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<User>

    @PUT("users/{id}")
    suspend fun updateUser(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<User>

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: String): Response<Map<String, String>>

    @PUT("users/{id}/reactivate")
    suspend fun reactivateUser(@Path("id") id: String): Response<User>

    // ── Customers ─────────────────────────────────────────────────────────
    @GET("customers")
    suspend fun getCustomers(
        @Query("search")  search: String?  = null,
        @Query("type")    type: String?    = null,
        @Query("page")    page: Int        = 1,
        @Query("limit")   limit: Int       = 50
    ): Response<CustomersResponse>

    @POST("customers")
    suspend fun createCustomer(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Customer>

    @GET("customers/{id}")
    suspend fun getCustomer(@Path("id") id: String): Response<Customer>

    @PUT("customers/{id}")
    suspend fun updateCustomer(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Customer>

    // P2.21: DELETE customers/{id} removed — customers are permanent (backend 403).

    @GET("customers/{id}/stats")
    suspend fun getCustomerStats(@Path("id") id: String): Response<Map<String, Any>>

    @GET("customers/{customerId}/history")
    suspend fun getCustomerHistory(
        @Path("customerId") customerId: String,
        @Query("exclude_job_id") excludeJobId: String
    ): Response<Map<String, Any>>

    @GET("customers/{id}/contacts")
    suspend fun getCustomerContacts(@Path("id") id: String): Response<List<CustomerContact>>

    @POST("customers/{id}/contacts")
    suspend fun addCustomerContact(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<CustomerContact>

    @PUT("customers/contacts/{contactId}")
    suspend fun updateCustomerContact(@Path("contactId") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<CustomerContact>

    @DELETE("customers/contacts/{contactId}")
    suspend fun deleteCustomerContact(@Path("contactId") id: String): Response<Map<String, String>>

    // ── Jobs ─────────────────────────────────────────────────────────────
    @GET("jobs")
    suspend fun getJobs(
        @Query("status")              status: String?  = null,
        @Query("assigned_to")         techId: String?  = null,
        @Query("customer_id")         custId: String?  = null,
        @Query("from")                from: String?    = null,
        @Query("to")                  to: String?      = null,
        @Query("activity_from")       activityFrom: String? = null,
        @Query("activity_to")         activityTo: String? = null,
        @Query("priority")            priority: String?= null,
        @Query("search")              search: String?  = null,
        @Query("sort")                sort: String? = null,
        @Query("page")                page: Int        = 1,
        @Query("limit")               limit: Int       = 50,
        @Query("include_all_statuses") includeAllStatuses: Boolean? = null,
        @Query("partner_view")        partnerView: Boolean? = null
    ): Response<JobsResponse>

    @GET("jobs/today")
    suspend fun getTodayJobs(): Response<List<Job>>

    @POST("jobs")
    suspend fun createJob(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Job>

    @GET("jobs/{id}")
    suspend fun getJob(@Path("id") id: String): Response<Job>

    @PUT("jobs/{id}")
    suspend fun updateJob(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Job>

    @DELETE("jobs/{id}")
    suspend fun deleteJob(@Path("id") id: String): Response<Map<String, String>>

    @POST("jobs/{id}/status")
    suspend fun updateJobStatus(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards String?>
    ): Response<Map<String, Any>>

    @POST("jobs/{id}/photos")
    suspend fun addJobPhoto(
        @Path("id") id: String,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @POST("jobs/{id}/complete")
    suspend fun completeJob(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Map<String, Any>>

    @GET("jobs/{id}/completion")
    suspend fun getJobCompletion(@Path("id") id: String): Response<Map<String, Any>>

    @POST("jobs/{id}/completion/confirm")
    suspend fun confirmJobCompletion(@Path("id") id: String): Response<Map<String, Any>>

    @POST("jobs/{id}/approve-earnings")
    suspend fun approveEarnings(@Path("id") id: String): Response<Map<String, Any>>

    @POST("jobs/{id}/send-to-partner")
    suspend fun sendJobToPartner(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Map<String, Any>>

    @POST("jobs/{id}/confirm-partner-status")
    suspend fun confirmPartnerStatus(
        @Path("id") id: String,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    // ── Schedules ─────────────────────────────────────────────────────────
    @GET("schedules")
    suspend fun getSchedules(
        @Query("from")    from: String?   = null,
        @Query("to")      to: String?     = null,
        @Query("user_id") userId: String? = null
    ): Response<List<Schedule>>

    @GET("schedules/availability")
    suspend fun getAvailability(
        @Query("start") start: String,
        @Query("end")   end: String
    ): Response<List<Map<String, Any>>>

    @POST("schedules")
    suspend fun createSchedule(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Schedule>

    @PUT("schedules/{id}")
    suspend fun updateSchedule(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Schedule>

    @DELETE("schedules/{id}")
    suspend fun deleteSchedule(@Path("id") id: String): Response<Map<String, String>>

    // ── Leads ─────────────────────────────────────────────────────────────
    @GET("leads")
    suspend fun getLeads(@Query("status") status: String? = null): Response<List<Lead>>

    @GET("leads/pipeline")
    suspend fun getPipeline(): Response<Map<String, List<Lead>>>

    @POST("leads")
    suspend fun createLead(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Lead>

    @PUT("leads/{id}")
    suspend fun updateLead(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Lead>

    @POST("leads/{id}/convert")
    suspend fun convertLead(@Path("id") id: String): Response<Map<String, Any>>

    @DELETE("leads/{id}")
    suspend fun deleteLead(@Path("id") id: String): Response<Map<String, String>>

    // ── GPS ───────────────────────────────────────────────────────────────
    @POST("gps/ping")
    suspend fun pingLocation(@Body body: Map<String, @JvmSuppressWildcards Double?>): Response<Map<String, Boolean>>

    @GET("gps/live")
    suspend fun getLiveTechs(): Response<List<TechLiveLocation>>

    // ── Price Book ────────────────────────────────────────────────────────
    @GET("pricebook/categories")
    suspend fun getPricebookCategories(): Response<List<PricebookCategory>>

    @POST("pricebook/categories")
    suspend fun createPricebookCategory(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<PricebookCategory>

    @PUT("pricebook/categories/{id}")
    suspend fun updatePricebookCategory(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<PricebookCategory>

    @DELETE("pricebook/categories/{id}")
    suspend fun deletePricebookCategory(@Path("id") id: String): Response<Map<String, String>>

    @GET("pricebook/items")
    suspend fun getPricebookItems(
        @Query("category_id") categoryId: String? = null,
        @Query("search")      search: String?     = null,
        @Query("type")        type: String?        = null
    ): Response<List<PricebookItem>>

    @GET("pricebook/items/{id}")
    suspend fun getPricebookItem(@Path("id") id: String): Response<PricebookItem>

    @POST("pricebook/items")
    suspend fun createPricebookItem(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<PricebookItem>

    @PUT("pricebook/items/{id}")
    suspend fun updatePricebookItem(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<PricebookItem>

    @DELETE("pricebook/items/{id}")
    suspend fun deletePricebookItem(@Path("id") id: String): Response<Map<String, String>>

    // ── Estimates ─────────────────────────────────────────────────────────
    @GET("estimates")
    suspend fun getEstimates(
        @Query("status")      status: String?  = null,
        @Query("customer_id") custId: String?  = null,
        @Query("job_id")      jobId: String?   = null,
        @Query("page")        page: Int        = 1
    ): Response<EstimatesResponse>

    @POST("estimates")
    suspend fun createEstimate(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Estimate>

    @GET("estimates/{id}")
    suspend fun getEstimate(@Path("id") id: String): Response<Estimate>

    @PUT("estimates/{id}")
    suspend fun updateEstimate(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Estimate>

    @POST("estimates/{id}/send")
    suspend fun sendEstimate(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @POST("estimates/{id}/sign")
    suspend fun signEstimate(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @POST("estimates/{id}/approve")
    suspend fun approveEstimate(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards String?>): Response<Map<String, Any>>

    @POST("estimates/{id}/convert-to-invoice")
    suspend fun convertEstimateToInvoice(@Path("id") id: String): Response<Map<String, Any>>

    @POST("estimates/{id}/add-photo")
    suspend fun addEstimatePhoto(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards String>): Response<Map<String, Any>>

    @PUT("estimates/{id}/deposit-settings")
    suspend fun updateDepositSettings(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Estimate>

    @POST("estimates/{id}/collect-deposit")
    suspend fun collectDeposit(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @GET("estimates/{id}/tiers")
    suspend fun getEstimateTiers(@Path("id") id: String): Response<List<EstimateTier>>

    @POST("estimates/{id}/tiers")
    suspend fun saveEstimateTiers(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<List<EstimateTier>>

    @POST("estimates/{id}/select-tier")
    suspend fun selectEstimateTier(@Path("id") id: String, @Body body: Map<String, String>): Response<Estimate>

    @DELETE("estimates/{id}")
    suspend fun deleteEstimate(@Path("id") id: String): Response<Map<String, String>>

    // ── Invoices ─────────────────────────────────────────────────────────
    @GET("invoices")
    suspend fun getInvoices(
        @Query("status")      status: String?   = null,
        @Query("customer_id") custId: String?   = null,
        @Query("job_id")      jobId: String?    = null,
        @Query("overdue")     overdue: Boolean? = null,
        @Query("page")        page: Int         = 1
    ): Response<InvoicesResponse>

    @POST("invoices")
    suspend fun createInvoice(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Invoice>

    @GET("invoices/{id}")
    suspend fun getInvoice(@Path("id") id: String): Response<Invoice>

    @PUT("invoices/{id}")
    suspend fun updateInvoice(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Invoice>

    @POST("invoices/{id}/send")
    suspend fun sendInvoice(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @POST("invoices/{id}/sign")
    suspend fun signInvoice(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @POST("invoices/{id}/payment")
    suspend fun recordInvoicePayment(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @POST("invoices/{id}/send-receipt")
    suspend fun sendInvoiceReceipt(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @GET("invoices/{id}/scanpay-qr")
    suspend fun getInvoiceScanpayQr(@Path("id") id: String): Response<Map<String, Any>>

    @POST("invoices/{id}/void")
    suspend fun voidInvoice(@Path("id") id: String): Response<Map<String, Any>>

    @PATCH("invoices/{id}/stop-followup")
    suspend fun stopInvoiceFollowup(@Path("id") id: String): Response<Map<String, Any>>

    @PATCH("invoices/{id}/reset-followup")
    suspend fun resetInvoiceFollowup(@Path("id") id: String): Response<Map<String, Any>>

    // ── Payments ─────────────────────────────────────────────────────────
    @GET("payments")
    suspend fun getPayments(
        @Query("invoice_id")  invoiceId: String?  = null,
        @Query("customer_id") customerId: String? = null,
        @Query("status")      status: String?     = null,
        @Query("page")        page: Int           = 1
    ): Response<Map<String, Any>>

    @POST("payments")
    suspend fun recordPayment(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    // P2.5: payments/scanpay/charge removed — phantom (no backend route, no caller).

    @POST("payments/scanpay-qr")
    suspend fun createScanPayQr(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<ScanPayQrResponse>

    @POST("payments/scanpay-link")
    suspend fun createScanPayLink(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<ScanPayLinkResponse>

    @GET("payments/scanpay-status/{invoiceId}")
    suspend fun getScanPayStatus(@Path("invoiceId") invoiceId: String): Response<ScanPayStatusResponse>

    @GET("payments/summary")
    suspend fun getPaymentSummary(
        @Query("from") from: String? = null,
        @Query("to")   to: String?   = null
    ): Response<Map<String, Any>>

    // ── Phone ─────────────────────────────────────────────────────────────
    @GET("phone/numbers")
    suspend fun getPhoneNumbers(): Response<List<PhoneNumber>>

    @GET("phone/calls")
    suspend fun getCallLogs(
        @Query("customer_id") customerId: String? = null,
        @Query("type")        type: String?       = null,
        @Query("page")        page: Int           = 1,
        @Query("limit")       limit: Int          = 50
    ): Response<CallsResponse>

    @POST("phone/calls/outbound")
    suspend fun makeOutboundCall(@Body body: Map<String, @JvmSuppressWildcards String?>): Response<Map<String, String>>

    @POST("phone/calls/{callSid}/disposition")
    suspend fun dispositionCall(
        @Path("callSid") callSid: String,
        @Body body: Map<String, @JvmSuppressWildcards String?>
    ): Response<Map<String, String>>

    @GET("phone/live-queue")
    suspend fun getLiveQueue(): Response<LiveQueueResponse>

    @POST("phone/second-chance/{id}/sms")
    suspend fun sendSecondChanceSms(@Path("id") id: String): Response<Map<String, String>>

    @GET("phone/second-chance")
    suspend fun getSecondChanceLeads(
        @Query("status") status: String = "new",
        @Query("page")   page: Int     = 1
    ): Response<SecondChanceResponse>

    @PUT("phone/second-chance/{id}")
    suspend fun updateSecondChanceLead(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards String?>
    ): Response<SecondChanceLead>

    @GET("phone/csr-stats")
    suspend fun getCsrStats(
        @Query("from") from: String? = null,
        @Query("to")   to: String?   = null
    ): Response<Map<String, Any>>

    @GET("phone/source-stats")
    suspend fun getSourceStats(
        @Query("from") from: String? = null,
        @Query("to")   to: String?   = null
    ): Response<List<Map<String, Any>>>

    @POST("phone/mask")
    suspend fun activateCallSecure(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("phone/sms/send")
    suspend fun sendSms(@Body body: Map<String, @JvmSuppressWildcards String?>): Response<Map<String, String>>

    @GET("phone/call-flows")
    suspend fun getCallFlows(): Response<List<Map<String, Any>>>

    @POST("phone/call-flows")
    suspend fun createCallFlow(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @PUT("phone/call-flows/{id}")
    suspend fun updateCallFlow(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    // ── Reports ───────────────────────────────────────────────────────────
    @GET("reports/dashboard")
    suspend fun getDashboardReport(
        @Query("from") from: String? = null,
        @Query("to")   to: String?   = null
    ): Response<DashboardResponse>

    @GET("reports/revenue")
    suspend fun getRevenueReport(
        @Query("from")     from: String?     = null,
        @Query("to")       to: String?       = null,
        @Query("group_by") groupBy: String   = "day"
    ): Response<List<Map<String, Any>>>

    @GET("reports/jobs")
    suspend fun getJobsReport(
        @Query("from") from: String? = null,
        @Query("to")   to: String?   = null
    ): Response<Map<String, Any>>

    @GET("reports/calls")
    suspend fun getCallsReport(
        @Query("from") from: String? = null,
        @Query("to")   to: String?   = null
    ): Response<Map<String, Any>>

    @GET("reports/earnings")
    suspend fun getEarningsReport(
        @Query("from")    from: String?    = null,
        @Query("to")      to: String?      = null,
        @Query("user_id") userId: String?  = null
    ): Response<Map<String, Any>>

    @GET("reports/revenue/export")
    suspend fun exportRevenueCsv(
        @Query("from") from: String? = null,
        @Query("to")   to: String?   = null
    ): Response<okhttp3.ResponseBody>

    @GET("reports/jobs/export")
    suspend fun exportJobsCsv(
        @Query("from") from: String? = null,
        @Query("to")   to: String?   = null
    ): Response<okhttp3.ResponseBody>

    @GET("reports/earnings/export")
    suspend fun exportEarningsCsv(
        @Query("from")    from: String?   = null,
        @Query("to")      to: String?     = null,
        @Query("user_id") userId: String? = null
    ): Response<okhttp3.ResponseBody>

    // ── FCM Token Registration ────────────────────────────────────────────
    @POST("notifications/fcm-token")
    suspend fun registerFcmToken(@Body body: Map<String, @JvmSuppressWildcards String?>): Response<Map<String, String>>

    @DELETE("notifications/fcm-token")
    suspend fun unregisterFcmToken(@Body body: Map<String, String>): Response<Map<String, String>>

    // ── Notifications ─────────────────────────────────────────────────────
    @GET("notifications")
    suspend fun getNotifications(
        @Query("unread_only") unreadOnly: Boolean? = null
    ): Response<NotificationsResponse>

    @PUT("notifications/read-all")
    suspend fun markAllRead(): Response<Map<String, String>>

    @PUT("notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): Response<Map<String, String>>

    // ── Bookings ─────────────────────────────────────────────────────────
    @GET("bookings")
    suspend fun getBookings(@Query("status") status: String = "pending"): Response<List<OnlineBooking>>

    @POST("bookings/{id}/confirm")
    suspend fun confirmBooking(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards String?>): Response<Map<String, Any>>

    @POST("bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards String?>): Response<Map<String, String>>

    @GET("bookings/widget-embed/{companyId}")
    suspend fun getWidgetEmbedCode(@Path("companyId") companyId: String): Response<Map<String, String>>

    // ── Uploads ───────────────────────────────────────────────────────────
    @Multipart
    @POST("uploads")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Query("purpose")     purpose: String?     = "photos",
        @Query("entity_type") entityType: String?  = null,
        @Query("entity_id")   entityId: String?    = null
    ): Response<Map<String, String>>

    @GET("uploads")
    suspend fun getUploads(
        @Query("entity_type") entityType: String,
        @Query("entity_id")   entityId: String,
        @Query("purpose")     purpose: String? = null
    ): Response<List<Map<String, Any>>>

    @DELETE("uploads/{filename}")
    suspend fun deleteUpload(@Path("filename") filename: String): Response<Map<String, String>>

    // ── Payroll ──────────────────────────────────────────────────────────
    @GET("payroll/summary")
    suspend fun getPayrollSummary(@QueryMap params: Map<String, String>): Response<Map<String, Any>>

    @GET("payroll/job-report")
    suspend fun getJobReport(@QueryMap params: Map<String, String>): Response<Map<String, Any>>

    // P2.27 (Bundle 4): the NEW per-actor reports that web + the report PDFs use — same
    // reference columns (payment-method split, parts, tip, fees, balance) across actor types.
    @GET("reports/tech/{userId}")
    suspend fun getActorReportTech(@Path("userId") userId: String, @QueryMap params: Map<String, String>): Response<Map<String, Any>>
    @GET("reports/roster/{rosterId}")
    suspend fun getActorReportRoster(@Path("rosterId") rosterId: String, @QueryMap params: Map<String, String>): Response<Map<String, Any>>
    @GET("reports/source/{sourceId}")
    suspend fun getActorReportSource(@Path("sourceId") sourceId: String, @QueryMap params: Map<String, String>): Response<Map<String, Any>>
    @GET("reports/partner/{connectionId}")
    suspend fun getActorReportPartner(@Path("connectionId") connectionId: String, @QueryMap params: Map<String, String>): Response<Map<String, Any>>
    @GET("reports/self")
    suspend fun getActorReportSelf(@QueryMap params: Map<String, String>): Response<Map<String, Any>>

    @GET("payroll/profit-by-source")
    suspend fun getProfitBySource(@QueryMap params: Map<String, String>): Response<Map<String, Any>>

    @GET("payroll/profit-rules")
    suspend fun getProfitRules(): Response<List<Map<String, Any>>>

    @POST("payroll/profit-rules")
    suspend fun createProfitRule(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @PUT("payroll/profit-rules/{id}")
    suspend fun updateProfitRule(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @DELETE("payroll/profit-rules/{id}")
    suspend fun deleteProfitRule(@Path("id") id: String): Response<Map<String, String>>

    // Option-1 pay run: mark all earnings in a date range paid (no period lock).
    @POST("payroll/earnings/mark-paid")
    suspend fun markEarningsPaid(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    // Permission model (sections, levels, role templates) for the Team Member grid.
    @GET("users/permission-schema")
    suspend fun getPermissionSchema(): Response<Map<String, Any>>

    @GET("payroll/bonuses")
    suspend fun getBonuses(@Query("user_id") userId: String? = null): Response<List<Map<String, Any>>>

    @POST("payroll/bonuses")
    suspend fun addBonus(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @DELETE("payroll/bonuses/{id}")
    suspend fun deleteBonus(@Path("id") id: String): Response<Map<String, String>>

    @GET("payroll/deductions")
    suspend fun getDeductions(@Query("user_id") userId: String? = null): Response<List<Map<String, Any>>>

    @POST("payroll/deductions")
    suspend fun addDeduction(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @DELETE("payroll/deductions/{id}")
    suspend fun deleteDeduction(@Path("id") id: String): Response<Map<String, String>>

    @POST("payroll/recalculate/{jobId}")
    suspend fun recalculateJobEarnings(@Path("jobId") jobId: String): Response<Map<String, Any>>

    // ── Reimbursements ────────────────────────────────────────────────
    @GET("payroll/reimbursements")
    suspend fun getReimbursements(
        @Query("user_id") userId: String? = null,
        @Query("status")  status: String? = null
    ): Response<List<Map<String, Any>>>

    @POST("payroll/reimbursements/{id}/approve")
    suspend fun approveReimbursement(@Path("id") id: String): Response<Map<String, Any>>

    @POST("payroll/reimbursements/{id}/pay")
    suspend fun payReimbursement(@Path("id") id: String): Response<Map<String, Any>>

    // ── Tech pay settings ─────────────────────────────────────────────
    @GET("payroll/tech-settings/{userId}")
    suspend fun getTechPaySettings(@Path("userId") userId: String): Response<Map<String, Any>>

    @PUT("payroll/tech-settings/{userId}")
    suspend fun updateTechPaySettings(
        @Path("userId") userId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Map<String, Any>>

    // ── Profit simulator ──────────────────────────────────────────────
    @POST("payroll/simulate")
    suspend fun simulateProfit(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    // P2.5: payroll/send-report/{userId} removed — phantom (no backend route).
    // Real tech-report send is POST /reports/tech/:userId/send (needs from/to).

    // ── Full user profile update (includes address, pay, emergency) ────
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): Response<User>

    // ── Ticket Parsing ─────────────────────────────────────────────────
    @POST("jobs/parse-ticket")
    suspend fun parseTicket(@Body body: Map<String, String>): Response<Map<String, Any>>

    // ── Contractor Network ─────────────────────────────────────────────
    @GET("network/connections")
    suspend fun getConnections(): Response<List<Map<String, Any>>>

    @GET("network/connections/active-simple")
    suspend fun getActiveConnectionsSimple(): Response<List<SimpleConnection>>

    @GET("network/connections/{id}")
    suspend fun getConnection(@Path("id") id: String): Response<Map<String, Any>>

    @POST("network/connections/invite")
    suspend fun inviteConnection(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @PUT("network/connections/{id}/respond")
    suspend fun respondConnection(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @PUT("network/connections/{id}/pause")
    suspend fun pauseConnection(@Path("id") id: String): Response<Map<String, Any>>

    @POST("network/agreements")
    suspend fun proposeAgreement(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @GET("network/agreements/{connectionId}")
    suspend fun getAgreements(@Path("connectionId") connectionId: String): Response<List<Map<String, Any>>>

    @PUT("network/agreements/{id}/respond")
    suspend fun respondAgreement(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @GET("network/my-id")
    suspend fun getMyNetworkId(): Response<Map<String, Any>>

    @GET("network/search")
    suspend fun searchCompanies(@Query("q") q: String, @Query("type") type: String): Response<List<Map<String, Any>>>

    @GET("network/connections/{id}/report")
    suspend fun getPartnerReport(
        @Path("id")        id:       String,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to")   dateTo:   String? = null
    ): Response<Map<String, Any>>

    @POST("network/connections/{id}/report/send")
    suspend fun sendPartnerReport(
        @Path("id") id:   String,
        @Body       body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Map<String, Any>>

    @GET("company")
    suspend fun getCompanyMap(): Response<Map<String, Any>>

    // ── Booking Settings ──────────────────────────────────────────────────
    @GET("settings/booking")
    suspend fun getBookingSettings(): Response<Map<String, Any>>

    @PUT("settings/booking")
    suspend fun updateBookingSettings(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    // ── Review Platforms ──────────────────────────────────────────────────
    @GET("settings/review-platforms")
    suspend fun getReviewPlatforms(): Response<List<Map<String, Any>>>

    @POST("settings/review-platforms")
    suspend fun createReviewPlatform(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @PUT("settings/review-platforms/{id}")
    suspend fun updateReviewPlatform(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @DELETE("settings/review-platforms/{id}")
    suspend fun deleteReviewPlatform(@Path("id") id: String): Response<Map<String, Any>>

    // ── Job Sources ───────────────────────────────────────────────────────────
    @GET("sources/contacts")
    suspend fun getSourceContacts(): Response<List<Map<String, Any>>>

    @POST("sources/contacts")
    suspend fun createSourceContact(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @PUT("sources/contacts/{id}")
    suspend fun updateSourceContact(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @DELETE("sources/contacts/{id}")
    suspend fun deleteSourceContact(@Path("id") id: String): Response<Map<String, String>>

    // ── Ad Channels ───────────────────────────────────────────────────────────
    @GET("sources/channels")
    suspend fun getAdChannels(): Response<List<Map<String, Any>>>

    @POST("sources/channels")
    suspend fun createAdChannel(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @PUT("sources/channels/{id}")
    suspend fun updateAdChannel(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    // ── Source Report ─────────────────────────────────────────────────────────
    @GET("sources/report")
    suspend fun getSourceReport(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to")   dateTo:   String? = null
    ): Response<Map<String, Any>>

    // ── Commission Rules ──────────────────────────────────────────────────────
    @GET("sources/commission-rules")
    suspend fun getCommissionRules(): Response<List<Map<String, Any>>>

    @POST("sources/commission-rules")
    suspend fun upsertCommissionRule(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @DELETE("sources/commission-rules/{id}")
    suspend fun deleteCommissionRule(@Path("id") id: String): Response<Map<String, String>>

    @GET("sources/commission-rules/resolve")
    suspend fun resolveCommissionForJob(@Query("job_id") jobId: String): Response<Map<String, Any>>

    // ── Inventory Settings ────────────────────────────────────────────────────
    @GET("inventory/settings")
    suspend fun getInventorySettings(): Response<Map<String, Any>>

    @PUT("inventory/settings")
    suspend fun updateInventorySettings(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    // ── Warehouse ─────────────────────────────────────────────────────────────
    @GET("inventory/warehouse")
    suspend fun getWarehouseInventory(): Response<List<Map<String, Any>>>

    @POST("inventory/warehouse")
    suspend fun upsertWarehouseItem(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @PUT("inventory/warehouse/{itemId}")
    suspend fun updateWarehouseItem(@Path("itemId") itemId: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    // ── Trucks ────────────────────────────────────────────────────────────────
    @GET("inventory/trucks")
    suspend fun getTrucks(): Response<List<Map<String, Any>>>

    @POST("inventory/trucks")
    suspend fun createTruck(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @PUT("inventory/trucks/{id}")
    suspend fun updateTruck(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @DELETE("inventory/trucks/{id}")
    suspend fun deleteTruck(@Path("id") id: String): Response<Map<String, String>>

    // ── Truck Stock ───────────────────────────────────────────────────────────
    @GET("inventory/trucks/{truckId}/stock")
    suspend fun getTruckStock(@Path("truckId") truckId: String): Response<List<Map<String, Any>>>

    @POST("inventory/trucks/{truckId}/stock")
    suspend fun upsertTruckStockItem(@Path("truckId") truckId: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @PUT("inventory/trucks/{truckId}/stock/{itemId}")
    suspend fun updateTruckStockItem(@Path("truckId") truckId: String, @Path("itemId") itemId: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @DELETE("inventory/trucks/{truckId}/stock/{itemId}")
    suspend fun deleteTruckStockItem(@Path("truckId") truckId: String, @Path("itemId") itemId: String): Response<Map<String, String>>

    @POST("inventory/trucks/{truckId}/send-items")
    suspend fun sendItemsToTruck(@Path("truckId") truckId: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @POST("inventory/trucks/{truckId}/return-items")
    suspend fun returnItemsFromTruck(@Path("truckId") truckId: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    // ── Restock Requests ──────────────────────────────────────────────────────
    @GET("inventory/restock-requests")
    suspend fun getRestockRequests(@Query("status") status: String? = null): Response<List<Map<String, Any>>>

    @POST("inventory/restock-requests")
    suspend fun createRestockRequest(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @PUT("inventory/restock-requests/{id}/fulfill")
    suspend fun fulfillRestockRequest(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    // ── Job Deduction ─────────────────────────────────────────────────────────
    @POST("inventory/deduct-job/{jobId}")
    suspend fun deductJobParts(@Path("jobId") jobId: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @GET("inventory/tech-truck/{userId}")
    suspend fun getTechTruck(@Path("userId") userId: String): Response<Map<String, Any>?>

    // ── Membership Plans ──────────────────────────────────────────────────────
    @GET("memberships/plans")
    suspend fun getMembershipPlans(): Response<List<Map<String, Any>>>

    @POST("memberships/plans")
    suspend fun createMembershipPlan(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @PUT("memberships/plans/{id}")
    suspend fun updateMembershipPlan(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @DELETE("memberships/plans/{id}")
    suspend fun deleteMembershipPlan(@Path("id") id: String): Response<Map<String, String>>

    // ── Customer Memberships ──────────────────────────────────────────────────
    @GET("memberships/customer/{customerId}")
    suspend fun getCustomerMemberships(@Path("customerId") customerId: String): Response<List<Map<String, Any>>>

    @POST("memberships/customer/{customerId}")
    suspend fun createCustomerMembership(@Path("customerId") customerId: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @PUT("memberships/{id}")
    suspend fun updateCustomerMembership(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @DELETE("memberships/{id}")
    suspend fun deleteCustomerMembership(@Path("id") id: String): Response<Map<String, String>>

    @POST("memberships/{id}/create-next-job")
    suspend fun createNextMembershipJob(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @GET("memberships/due-soon")
    suspend fun getMembershipsDueSoon(): Response<List<Map<String, Any>>>

    // ── Roster Techs ──────────────────────────────────────────────────────
    @GET("roster-techs")
    suspend fun getRosterTechs(): Response<List<RosterTech>>

    @POST("roster-techs")
    suspend fun createRosterTech(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<RosterTech>

    @PUT("roster-techs/{id}")
    suspend fun updateRosterTech(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<RosterTech>

    @DELETE("roster-techs/{id}")
    suspend fun deleteRosterTech(@Path("id") id: String): Response<Map<String, String>>

    @POST("roster-techs/notify-tech")
    suspend fun notifyRosterTech(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    // ── Import Wizard ─────────────────────────────────────────────────────
    @Multipart
    @POST("import/preview")
    suspend fun previewImport(
        @Part file: MultipartBody.Part,
        @Part("type") type: okhttp3.RequestBody
    ): Response<ImportPreviewResponse>

    @POST("import/execute")
    suspend fun executeImport(@Body request: ImportExecuteRequest): Response<ImportResultResponse>

    // ── Reminder override ─────────────────────────────────────────────────
    @PATCH("jobs/{id}/reminder-method")
    suspend fun updateJobReminderMethod(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards String>
    ): Response<Map<String, Any>>

    // ── Dispatch ──────────────────────────────────────────────────────────
    @POST("jobs/{id}/dispatch")
    suspend fun dispatchJob(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    @POST("jobs/{id}/arrived")
    suspend fun arrivedJob(@Path("id") id: String): Response<Map<String, Any>>

    // ── Job Parts ─────────────────────────────────────────────────────────
    @GET("jobs/{id}/parts")
    suspend fun getJobParts(@Path("id") id: String): Response<List<JobPart>>

    @POST("jobs/{id}/parts")
    suspend fun addJobPart(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<JobPart>

    @DELETE("jobs/{id}/parts/{partId}")
    suspend fun deleteJobPart(
        @Path("id") id: String,
        @Path("partId") partId: String
    ): Response<Map<String, String>>

    // ── SMS Conversations ─────────────────────────────────────────────────────
    @GET("sms/conversations")
    suspend fun getSmsConversations(): Response<List<SmsConversation>>

    @GET("sms/conversations/{id}/messages")
    suspend fun getConversationMessages(
        @Path("id") conversationId: String
    ): Response<ThreadResponse>

    @POST("sms/conversations/{id}/send")
    suspend fun sendSmsReply(
        @Path("id") conversationId: String,
        @Body body: Map<String, String>
    ): Response<SmsMessage>

    @GET("sms/customer/{customerId}/messages")
    suspend fun getCustomerMessages(
        @Path("customerId") customerId: String
    ): Response<List<SmsMessage>>

    @GET("sms/job/{jobId}/messages")
    suspend fun getJobMessages(
        @Path("jobId") jobId: String
    ): Response<List<SmsMessage>>

    // ── Timesheets ─────────────────────────────────────────────────────────
    @POST("timesheets/clock-in")
    suspend fun clockIn(): Response<Timesheet>

    @POST("timesheets/clock-out")
    suspend fun clockOut(): Response<Timesheet>

    @GET("timesheets/today")
    suspend fun getTodayTimesheet(): Response<Timesheet>

    @GET("timesheets/status")
    suspend fun getTimesheetStatus(): Response<TimesheetStatus>

    @GET("timesheets/report")
    suspend fun getTimesheetReport(
        @Query("start_date") startDate: String,
        @Query("end_date")   endDate: String,
        @Query("user_id")    userId: String? = null
    ): Response<TimesheetReport>

    // ── QuickBooks Online Integration ─────────────────────────────────────
    @GET("integrations/quickbooks/status")
    suspend fun getQboStatus(): Response<QboStatus>

    @GET("integrations/quickbooks/connect")
    suspend fun getQboConnectUrl(): Response<QboConnectResponse>

    @POST("integrations/quickbooks/sync/customers")
    suspend fun syncQboCustomers(): Response<QboSyncResult>

    @POST("integrations/quickbooks/sync/invoices")
    suspend fun syncQboInvoices(): Response<QboSyncResult>

    @POST("integrations/quickbooks/sync/payments")
    suspend fun syncQboPayments(): Response<QboSyncResult>

    @POST("integrations/quickbooks/sync/all")
    suspend fun syncQboAll(): Response<Map<String, @JvmSuppressWildcards QboSyncResult>>

    @PUT("integrations/quickbooks/settings")
    suspend fun updateQboSettings(@Body body: Map<String, String>): Response<Map<String, @JvmSuppressWildcards Any?>>

    @DELETE("integrations/quickbooks/disconnect")
    suspend fun disconnectQbo(): Response<Map<String, @JvmSuppressWildcards Any?>>
}
