package com.ultimatepro.domain.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

// ─── Auth ─────────────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String,
    val fcm_token: String? = null
)

data class RegisterRequest(
    val company_name: String,
    val first_name: String,
    val last_name: String,
    val email: String,
    val phone: String,
    val password: String,
    val invite_code: String? = null
)

data class AuthResponse(
    val token: String,
    val refresh_token: String,
    val user: User,
    val company: Company,
    // Resolved per-section permission levels (Phase 3a-0). null on older responses.
    val permissions_resolved: Map<String, String>? = null
)

// Permission RANK + check, mirroring backend utils/permissions.js. Phase 3a-0:
// available for UI gating; nothing is hidden yet.
val PERMISSION_RANK = mapOf("none" to 0, "view" to 1, "edit_self" to 2, "full" to 3)
fun canPermission(perms: Map<String, String>?, section: String, level: String): Boolean =
    (PERMISSION_RANK[perms?.get(section)] ?: 0) >= (PERMISSION_RANK[level] ?: 0)

// UI gate with an owner/admin backstop (Phase 3a fix). owner/admin are always full
// (matches web + server templates), so an empty/stale perms map never blanks their
// nav. NON-owner roles fall through to the rank check — the backstop is owner/admin
// ONLY, it does NOT grant other roles full access.
fun canUi(role: String?, perms: Map<String, String>?, section: String, level: String): Boolean =
    role == "owner" || role == "admin" || canPermission(perms, section, level)

// ─── Company ──────────────────────────────────────────────────────────────

@Parcelize
data class Company(
    val id: String = "",
    val name: String = "",
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val website: String? = null,
    val tagline: String? = null,
    val default_terms: String? = null,
    val logo_url: String? = null,
    val ultimatecrm_id: String? = null,
    val timezone: String = "America/New_York",
    val currency: String = "USD",
    val tax_rate: Double = 0.0,
    val subscription: String = "trial"
) : Parcelable

// ─── User ─────────────────────────────────────────────────────────────────

@Parcelize
data class User(
    val id: String = "",
    val company_id: String = "",
    val first_name: String = "",
    val last_name: String = "",
    val email: String = "",
    val phone: String? = null,
    val phone2: String? = null,
    val role: String = "technician",
    val avatar_url: String? = null,
    val color: String = "#1565C0",
    // Address
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val country: String? = "US",
    // Pay
    val is_active: Boolean = true,
    val hourly_rate: Double = 0.0,
    val commission_pct: Double = 0.0,
    val worker_type: String? = "employee",
    val material_policy: String? = "company_supplied",
    val sub_pct: Double = 0.0,
    val preferred_pay_method: String? = "check",
    // Emergency
    val emergency_name: String? = null,
    val emergency_phone: String? = null,
    // Granular permissions overrides (Phase 1: stored; null = use role template).
    val permissions: Map<String, String>? = null,
    val last_login: String? = null,
    val created_at: String? = null
) : Parcelable {
    val fullName get() = "$first_name $last_name".trim()
    val initials get() = "${first_name.take(1)}${last_name.take(1)}".uppercase()
    val isManager get() = role in listOf("owner", "admin", "manager")
    val isOwnerOrAdmin get() = role in listOf("owner", "admin")
    val fullAddress get() = listOfNotNull(address, city, state, zip).joinToString(", ")
    val payRateLabel get() = when {
        worker_type == "subcontractor" -> "Subcontractor ${sub_pct.toInt()}% of gross"
        hourly_rate > 0 -> "Hourly @ \$${"%.2f".format(hourly_rate)}/hr"
        commission_pct > 0 -> "Commission ${commission_pct.toInt()}%"
        else -> "No rate set"
    }
}

// ─── CustomerContact ──────────────────────────────────────────────────────

@Parcelize
data class CustomerContact(
    val id: Int = 0,
    val customer_id: String = "",
    val type: String = "",          // "phone" or "email"
    val value: String = "",
    val label: String? = null,
    val is_primary: Boolean = false,
    val created_at: String? = null
) : Parcelable

// ─── Customer ─────────────────────────────────────────────────────────────

@Parcelize
data class Customer(
    val id: String = "",
    val company_id: String = "",
    val first_name: String = "",
    val last_name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val phone2: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val emails: List<String> = emptyList(),
    val phones: List<String> = emptyList(),
    val contacts: List<CustomerContact> = emptyList(),
    val type: String = "residential",
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val source: String? = null,
    val rating: Int = 0,
    val is_active: Boolean = true,
    // Aggregated fields from API
    val total_jobs: Int? = null,
    val total_spent: Double? = null,
    val last_job_date: String? = null,
    val has_active_membership: Boolean = false,
    val portal_token: String? = null,
    val created_at: String? = null
) : Parcelable {
    val fullName get() = "$first_name ${last_name ?: ""}".trim()
    val initials get() = "${first_name.take(1)}${(last_name ?: "").take(1)}".uppercase()
    val fullAddress get() = listOfNotNull(address, city, state, zip).joinToString(", ")
}

// ─── Lead ─────────────────────────────────────────────────────────────────

@Parcelize
data class Lead(
    val id: String = "",
    val name: String = "",
    val email: String? = null,
    val phone: String? = null,
    val source: String? = null,
    val source_detail: String? = null,
    val status: String = "new",
    val value: Double? = null,
    val notes: String? = null,
    val assigned_to: String? = null,
    val follow_up_at: String? = null,
    val tags: List<String> = emptyList(),
    // Joined fields
    val assigned_first: String? = null,
    val assigned_last: String? = null,
    val created_at: String? = null
) : Parcelable

// ─── RosterTech ───────────────────────────────────────────────────────────

@Parcelize
data class RosterTech(
    val id: String = "",
    val name: String = "",
    val phone: String? = null,
    val email: String? = null,
    val commission_pct: Double = 0.0,
    val cc_fee_pct: Double = 0.0,
    val is_active: Boolean = true,
    val created_at: String? = null
) : Parcelable

// ─── Job ──────────────────────────────────────────────────────────────────

@Parcelize
data class Job(
    val id: String = "",
    val company_id: String = "",
    val customer_id: String = "",
    val assigned_to: String? = null,
    val job_number: String = "",
    val title: String = "",
    val description: String? = null,
    val type: String = "service",
    val status: String = "unscheduled",
    val priority: String = "medium",
    val scheduled_start: String? = null,
    val scheduled_end: String? = null,
    val actual_start: String? = null,
    val actual_end: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val job_timezone: String? = null,
    val effective_timezone: String? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val internal_notes: String? = null,
    val completion_notes: String? = null,
    val photos: List<String> = emptyList(),
    val signature_url: String? = null,
    val line_items: List<LineItem> = emptyList(),
    val source: String? = null,
    val reminder_sent: Boolean = false,
    val reminder_method: String = "default",
    val reminder_sent_at: String? = null,
    val linked_job_id: String? = null,
    // Joined fields
    val linked_job_number: String? = null,
    val linked_job_title: String? = null,
    val cust_first: String? = null,
    val cust_last: String? = null,
    val cust_phone: String? = null,
    val cust_phone2: String? = null,   // P2.35: 2nd customer phone, shown in the job's phone section
    val cust_email: String? = null,
    val cust_address: String? = null,
    val cust_city: String? = null,
    val cust_state: String? = null,
    val cust_lat: Double? = null,
    val cust_lng: Double? = null,
    val tech_first: String? = null,
    val tech_last: String? = null,
    val tech_phone: String? = null,
    val tech_avatar: String? = null,
    val tech_color: String? = null,
    val roster_tech_name: String? = null,   // GET /jobs/:id → rt.name AS roster_tech_name (P2.1b)
    val subtotal: Double? = null,
    // Partner / job-sharing fields
    val sent_to_company_id: String? = null,
    val sent_by_company_id: String? = null,
    val sent_to_company_name: String? = null,
    val sent_by_company_name: String? = null,
    val agreement_id: String? = null,
    val partner_status: String? = null,
    val tech_permissions: Map<String, Boolean> = emptyMap(),
    val sender_keeps_pct: Double? = null,
    val receiver_keeps_pct: Double? = null,
    // Source tracking
    val source_type: String? = null,
    val job_source_id: String? = null,
    val job_source_name: String? = null,
    val ad_channel_id: String? = null,
    val ad_channel_name: String? = null,
    val ad_channel_custom: String? = null,
    val resolved_commission_pct: Double? = null,
    val membership_id: String? = null,
    val assigned_roster_tech_id: String? = null,
    val tech_notify_method: String? = null,
    // Pending-review earnings gate: 'approved' (default) | 'pending_review'.
    val review_status: String? = null,
    val created_at: String? = null
) : Parcelable {
    val customerName get() = "$cust_first ${cust_last ?: ""}".trim().ifBlank { "—" }
    val techName get() = if (tech_first != null) "$tech_first ${tech_last ?: ""}".trim() else null
    val fullAddress get() = listOfNotNull(address, city, state, zip).joinToString(", ")
    val custFullAddress get() = listOfNotNull(cust_address, cust_city, cust_state).joinToString(", ")
    val isActive get() = status in listOf("scheduled", "en_route", "in_progress", "unscheduled")
    val isSentJob get() = sent_to_company_id != null || sent_by_company_id != null
}

@Parcelize
data class LineItem(
    val id: String? = null,
    val name: String = "",
    val description: String? = null,
    val sku: String? = null,
    val quantity: Double = 1.0,
    val unit: String = "ea",
    val unit_price: Double = 0.0,
    val discount_pct: Double = 0.0,
    val tax_rate: Double = 0.0,
    val total: Double = 0.0,
    val image_url: String? = null,
    val sort_order: Int = 0,
    val item_type: String = "service",
    val taxable: Boolean = false,
    val pricebook_id: String? = null,
    val price_overridden: Boolean = false
) : Parcelable

// ─── JobPart ──────────────────────────────────────────────────────────────

@Parcelize
data class JobPart(
    val id: String = "",
    val job_id: String = "",
    val company_id: String = "",
    val name: String = "",
    val cost: Double = 0.0,
    val provider: String = "company",  // "company" | "tech"
    val created_at: String? = null
) : Parcelable

// ─── EstimateTier ─────────────────────────────────────────────────────────

@Parcelize
data class EstimateTier(
    val id: String = "",
    @SerializedName("estimate_id")   val estimateId: String = "",
    @SerializedName("tier_label")    val tierLabel: String = "",
    val description: String? = null,
    @SerializedName("line_items")    val lineItems: List<LineItem> = emptyList(),
    val subtotal: Double = 0.0,
    @SerializedName("tax_total")     val taxTotal: Double = 0.0,
    @SerializedName("discount_total") val discountTotal: Double = 0.0,
    val total: Double = 0.0,
    @SerializedName("sort_order")    val sortOrder: Int = 0
) : Parcelable

// ─── Estimate ─────────────────────────────────────────────────────────────

@Parcelize
data class Estimate(
    val id: String = "",
    val customer_id: String = "",
    val job_id: String? = null,
    val estimate_number: String = "",
    val title: String? = null,
    val status: String = "draft",
    val subtotal: Double = 0.0,
    val tax_total: Double = 0.0,
    val discount_total: Double = 0.0,
    val total: Double = 0.0,
    val notes: String? = null,
    val terms: String? = null,
    val valid_until: String? = null,
    val sent_at: String? = null,
    val approved_at: String? = null,
    val signature_url: String? = null,
    val pdf_url: String? = null,
    val customer_signature: String? = null,
    val customer_signature_date: String? = null,
    // List fields are nullable because Gson uses sun.misc.Unsafe to
    // bypass Kotlin constructor defaults. When the backend response
    // omits a key (e.g. estimates table has no `tiers`/`before_photos`/
    // `after_photos` columns) Gson leaves the field as null, then
    // .copy() crashes with "Parameter specified as non-null is null".
    // Reads coerce via ?: emptyList().
    val before_photos: List<String>? = null,
    val after_photos: List<String>? = null,
    val line_items: List<LineItem>? = null,
    val cust_first: String? = null,
    val cust_last: String? = null,
    val cust_email: String? = null,
    val cust_phone: String? = null,
    val cust_address: String? = null,
    val cust_city: String? = null,
    val cust_state: String? = null,
    val cust_zip: String? = null,
    val deposit_required: Boolean = false,
    val deposit_amount: Double = 0.0,
    val deposit_type: String = "fixed",
    val deposit_collected: Boolean = false,
    val deposit_collected_at: String? = null,
    val deposit_payment_id: String? = null,
    @SerializedName("presentation_mode") val presentationMode: String = "standard",
    @SerializedName("selected_tier_id")  val selectedTierId: String? = null,
    val tiers: List<EstimateTier>? = null,
    val qbo_id: String? = null,
    val created_at: String? = null
) : Parcelable {
    val customerName get() = "$cust_first ${cust_last ?: ""}".trim().ifBlank { "—" }
    val isSigned get() = customer_signature != null
    val isGbb get() = presentationMode == "gbb"
}

// ─── Invoice ──────────────────────────────────────────────────────────────

@Parcelize
data class Invoice(
    val id: String = "",
    val customer_id: String = "",
    val job_id: String? = null,
    val estimate_id: String? = null,
    val invoice_number: String = "",
    val status: String = "draft",
    val subtotal: Double = 0.0,
    val tax_total: Double = 0.0,
    val discount_total: Double = 0.0,
    val total: Double = 0.0,
    val amount_paid: Double = 0.0,
    val balance_due: Double = 0.0,
    val notes: String? = null,
    val terms: String? = null,
    val due_date: String? = null,
    val sent_at: String? = null,
    val paid_at: String? = null,
    val pdf_url: String? = null,
    val payment_link: String? = null,
    val customer_signature: String? = null,
    val customer_signature_date: String? = null,
    val payment_method: String? = null,
    // Nullable because the invoices table has no before_photos/after_photos
    // columns; Gson leaves these null when the API response omits them.
    val before_photos: List<String>? = null,
    val after_photos: List<String>? = null,
    val receipt_sent: Boolean = false,
    val line_items: List<LineItem> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val cust_first: String? = null,
    val cust_last: String? = null,
    val cust_email: String? = null,
    val cust_phone: String? = null,
    val is_overdue: Boolean = false,
    val deposit_paid: Double = 0.0,
    val deposit_paid_at: String? = null,
    val followup_count: Int = 0,
    val followup_last_sent_at: String? = null,
    val followup_stopped: Boolean = false,
    val qbo_id: String? = null,
    val created_at: String? = null,
    // P2.40: set true on the PUT /invoices/:id response when editing the line items of a
    // signed invoice cleared the signature — the client re-prompts for a new signature.
    val requires_resign: Boolean = false
) : Parcelable {
    val customerName get() = "$cust_first ${cust_last ?: ""}".trim().ifBlank { "—" }
    val isSigned get() = customer_signature != null
}

// ─── Payment ──────────────────────────────────────────────────────────────

@Parcelize
data class Payment(
    val id: String = "",
    val customer_id: String = "",
    val invoice_id: String? = null,
    val amount: Double = 0.0,
    val method: String = "cash",
    val status: String = "completed",
    val notes: String? = null,
    val check_number: String? = null,
    val processor: String? = null,
    val processed_at: String? = null,
    val invoice_number: String? = null,
    val cust_first: String? = null,
    val cust_last: String? = null,
    val created_at: String? = null
) : Parcelable

// ─── Schedule ─────────────────────────────────────────────────────────────

@Parcelize
data class Schedule(
    val id: String = "",
    val user_id: String = "",
    val job_id: String? = null,
    val title: String? = null,
    val type: String = "job",
    val start_time: String = "",
    val end_time: String = "",
    val all_day: Boolean = false,
    val notes: String? = null,
    val color: String? = null,
    val tech_first: String? = null,
    val tech_last: String? = null,
    val tech_color: String? = null,
    val job_title: String? = null,
    val job_status: String? = null,
    val job_priority: String? = null,
    val job_address: String? = null
) : Parcelable

// ─── Phone / Calls ────────────────────────────────────────────────────────

data class IncomingCallEvent(
    val call_sid: String,
    val from: String,
    val source_tag: String?,
    val number_label: String?,
    val customer: CallCustomerInfo?,
    val is_new_customer: Boolean,
    val caller_location: String?,
    val call_log_id: String
)

data class CallCustomerInfo(
    val id: String,
    val name: String,
    val phone: String?,
    val type: String?,
    val address: String?,
    val city: String?,
    val tags: List<String>
)

data class CallLog(
    val id: String = "",
    val type: String = "",
    val from_number: String? = null,
    val to_number: String? = null,
    val duration_sec: Int? = null,
    val recording_url: String? = null,
    val body: String? = null,
    val status: String? = null,
    val source_tag: String? = null,
    val disposition: String? = null,
    val booked_job_id: String? = null,
    val cust_first: String? = null,
    val cust_last: String? = null,
    val user_first: String? = null,
    val user_last: String? = null,
    val created_at: String? = null
) {
    val customerName get() = if (cust_first != null) "$cust_first ${cust_last ?: ""}".trim() else null
}

data class SecondChanceLead(
    val id: String = "",
    val from_number: String = "",
    val caller_name: String? = null,
    val call_date: String? = null,
    val reason: String = "not_booked",
    val status: String = "new",
    val notes: String? = null,
    val assigned_to: String? = null,
    val follow_up_at: String? = null,
    val cust_first: String? = null,
    val cust_last: String? = null,
    val assigned_first: String? = null,
    val assigned_last: String? = null,
    val created_at: String? = null
) {
    val displayName get() = if (cust_first != null) "$cust_first ${cust_last ?: ""}".trim() else from_number
}

data class CsrStats(
    val id: String = "",
    val first_name: String = "",
    val last_name: String = "",
    val color: String = "#1565C0",
    val total_calls: Int = 0,
    val booked_calls: Int = 0,
    val missed_calls: Int = 0,
    val booking_rate_pct: Double? = null,
    val avg_duration_sec: Int? = null,
    val avg_answer_sec: Int? = null,
    val last_call_at: String? = null
) {
    val fullName get() = "$first_name $last_name".trim()
}

data class PhoneNumber(
    val id: String = "",
    val number: String = "",
    val friendly_name: String? = null,
    val type: String = "tracking",
    val source_tag: String? = null,
    val active: Boolean = true,
    val assigned_first: String? = null,
    val assigned_last: String? = null
)

// ─── Dashboard ────────────────────────────────────────────────────────────

data class DashboardKpis(
    val total_collected: Double = 0.0,
    val today: Double = 0.0,
    val this_month: Double = 0.0,
    val last_month: Double = 0.0,
    val payment_count: Int = 0,
    // Jobs summary
    val total: Int = 0,
    val completed: Int = 0,
    val cancelled: Int = 0,
    val in_progress: Int = 0,
    val scheduled: Int = 0,
    val completion_rate_pct: Double? = null,
    // Calls summary
    val total_calls: Int = 0,
    val missed: Int = 0,
    // Second chance
    val new_count: Int = 0,
    val recovery_rate_pct: Double? = null
)

data class DashboardResponse(
    val period: Map<String, String> = emptyMap(),
    val revenue: DashboardKpis? = null,
    val jobs: DashboardKpis? = null,
    val calls: DashboardKpis? = null,
    val second_chance: DashboardKpis? = null,
    val top_techs: List<User> = emptyList()
)

// ─── GPS ─────────────────────────────────────────────────────────────────

data class TechLiveLocation(
    val id: String = "",
    val first_name: String = "",
    val last_name: String = "",
    val color: String = "#1565C0",
    val avatar_url: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val heading: Double? = null,
    val speed: Double? = null,
    val recorded_at: String? = null,
    val current_job_id: String? = null,
    val current_job_title: String? = null,
    val current_job_status: String? = null,
    val job_address: String? = null
) {
    val fullName get() = "$first_name $last_name".trim()
    val isOnline get() = lat != null && lng != null
}

// ─── Booking ─────────────────────────────────────────────────────────────

data class OnlineBooking(
    val id: String = "",
    val contact_name: String = "",
    val contact_email: String? = null,
    val contact_phone: String = "",
    val service_type: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val preferred_date: String? = null,
    val preferred_time: String? = null,
    val notes: String? = null,
    val status: String = "pending",
    val cust_first: String? = null,
    val cust_last: String? = null,
    val created_at: String? = null
)

// ─── Notification ────────────────────────────────────────────────────────

data class AppNotification(
    val id: String = "",
    val title: String = "",
    val body: String? = null,
    val type: String? = null,
    val entity_type: String? = null,
    val entity_id: String? = null,
    val read: Boolean = false,
    val created_at: String? = null
)

data class NotificationsResponse(
    val notifications: List<AppNotification> = emptyList(),
    val unread_count: Int = 0
)

// ─── Price Book ───────────────────────────────────────────────────────────

@Parcelize
data class PricebookCategory(
    val id: String = "",
    val name: String = "",
    val type: String = "service",
    val description: String? = null,
    val image_url: String? = null,
    val is_active: Boolean = true,
    val item_count: Int = 0,
    val taxable: Boolean = false
) : Parcelable

@Parcelize
data class PricebookItem(
    val id: String = "",
    val category_id: String? = null,
    val sku: String? = null,
    val name: String = "",
    val description: String? = null,
    val image_url: String? = null,
    val unit_price: Double = 0.0,
    val cost_price: Double = 0.0,
    val item_type: String = "service",
    val taxable: Boolean = false,
    val is_active: Boolean = true,
    val category_name: String? = null
) : Parcelable

// ─── Paged responses ─────────────────────────────────────────────────────

data class JobsResponse(
    val jobs: List<Job> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pages: Int = 1
)

data class CustomersResponse(
    val customers: List<Customer> = emptyList(),
    val total: Int = 0,
    val page: Int = 1
)

data class EstimatesResponse(
    val estimates: List<Estimate> = emptyList(),
    val total: Int = 0,
    val page: Int = 1
)

data class InvoicesResponse(
    val invoices: List<Invoice> = emptyList(),
    val total: Int = 0
)

data class CallsResponse(
    val calls: List<CallLog> = emptyList(),
    val total: Int = 0,
    val page: Int = 1
)

data class SecondChanceResponse(
    val leads: List<SecondChanceLead> = emptyList(),
    val stats: SecondChanceStats = SecondChanceStats(),
    val page: Int = 1
)

data class SecondChanceStats(
    val total: Int = 0,
    val booked: Int = 0,
    val called_count: Int = 0,
    val recovery_rate_pct: Double? = null
)

data class JobPhoto(
    val id: Int = 0,
    val filename: String = "",
    val url: String = "",
    val purpose: String = "",
    val original_name: String? = null
)

data class LiveQueueCall(
    val id:              String  = "",
    val call_sid:        String  = "",
    val from_number:     String? = null,
    val status:          String  = "ringing",
    val seconds_elapsed: Int     = 0,
    val cust_first:      String? = null,
    val cust_last:       String? = null,
    val csr_first:       String? = null,
    val csr_last:        String? = null,
    val line_name:       String? = null,
    val source_tag:      String? = null,
) {
    val customerName: String get() = listOfNotNull(cust_first, cust_last).joinToString(" ").ifBlank { from_number ?: "Unknown" }
    val agentName:    String get() = listOfNotNull(csr_first,  csr_last).joinToString(" ").ifBlank { "Unassigned" }
}

data class LiveQueueStats(
    val calls_ringing: Int    = 0,
    val calls_active:  Int    = 0,
    val avg_wait_sec:  Int?   = null,
)

data class LiveQueueResponse(
    val active_calls: List<LiveQueueCall> = emptyList(),
    val stats:        LiveQueueStats      = LiveQueueStats(),
)

data class ParsedTicket(
    val ticketRef:          String? = null,  // external reference/ticket # from pasted text — NOT the internal job_number
    val customerName:       String? = null,
    val companyName:        String? = null,
    val phone:              String? = null,       // primary phone (first of phone_numbers)
    val phoneNumbers:       List<String> = emptyList(),
    val email:              String? = null,
    val address:            String? = null,
    val city:               String? = null,
    val state:              String? = null,
    val zip:                String? = null,
    val jobTitle:           String? = null,
    val jobDescription:     String? = null,
    val scheduledDate:      String? = null,
    val scheduledTime:      String? = null,
    val source:             String? = null,
    val sourceReviewLink:   String? = null,
    val existingCustomerId: String? = null,
    val matchType:          String? = null,   // P2.1l: 'phone' (auto-attach) | 'name' (surface choice) | null
    val leftoverNotes:      String? = null
)

// ─── Duplicate Customer Detection ────────────────────────────────────────

data class RecentJobSummary(
    val id: String,
    val jobNumber: String,
    val title: String,
    val status: String,
    val scheduledStart: String?
)

data class DuplicateCustomerInfo(
    val customerId: String,
    val customerName: String,
    val phone: String?,
    val address: String?,
    val recentJobs: List<RecentJobSummary>
)

// ─── ScanPay ──────────────────────────────────────────────────────────────

data class ScanPayQrResponse(
    val qr_data_url:  String = "",
    val payment_url:  String = "",
    val order_id:     String = ""
)

data class ScanPayLinkResponse(
    val payment_url:  String = "",
    val order_id:     String = "",
    val sms_sent:     Boolean = false,
    val phone_used:   String? = null
)

data class ScanPayStatusResponse(
    val status:  String  = "",
    val paid_at: String? = null,
    val amount:  Double? = null,
    val balance: Double? = null
)

// P2.38: poll target for the estimate-deposit ScanPay QR/link sheet.
data class DepositStatusResponse(
    val deposit_required:     Boolean = false,
    val deposit_collected:    Boolean = false,
    val deposit_collected_at: String? = null,
    val amount:               Double  = 0.0
)

// ─── Contractor Network ───────────────────────────────────────────────────

data class ContractorAgreement(
    val id: String = "",
    val connectionId: String = "",
    val proposedBy: String = "",
    val status: String = "pending",        // pending, accepted, declined, countered
    val senderKeptPct: Double = 0.0,
    val receiverKeptPct: Double = 0.0,
    val reviewGoesTo: String = "receiver", // sender, receiver, both
    val notes: String? = null,
    val createdAt: String? = null,
    val respondedAt: String? = null
)

data class ContractorConnection(
    val id: String = "",
    val partnerCompanyId: String = "",
    val partnerCompanyName: String = "",
    val partnerUltimatecrmId: String? = null,
    val status: String = "pending",        // pending, active, paused, declined
    val invitedBy: String? = null,
    val latestAgreement: ContractorAgreement? = null,
    val createdAt: String? = null
)

data class SimpleConnection(
    @SerializedName("connection_id") val connectionId: String = "",
    @SerializedName("partner_id")    val partnerId: String = "",
    @SerializedName("partner_name")  val partnerName: String = ""
)

data class CompanySearchResult(
    val id: String = "",
    val name: String = "",
    val city: String? = null,
    val state: String? = null,
    val ultimatecrmId: String? = null,
    val profileMode: String? = null
)

data class JobCompletionDetails(
    val id: String = "",
    val job_id: String = "",
    val parts_paid_by: String? = null,
    val parts_amount: Double = 0.0,
    val payment_collected_by: String? = null,
    val cc_fee_amount: Double = 0.0,
    val cc_fee_paid_by: String? = null,
    val net_after_deductions: Double = 0.0,
    val sender_earns: Double = 0.0,
    val receiver_earns: Double = 0.0,
    val submitted_by: String? = null,
    val confirmed_by: String? = null,
    val confirmed_at: String? = null,
    val status: String = "pending",
    val notes: String? = null,
    val submitter_first: String? = null,
    val submitter_last: String? = null,
    val confirmer_first: String? = null,
    val confirmer_last: String? = null,
    val created_at: String? = null
) {
    val submitterName get() = "$submitter_first ${submitter_last ?: ""}".trim().ifBlank { null }
    val confirmerName get() = "$confirmer_first ${confirmer_last ?: ""}".trim().ifBlank { null }
}

// ─── Customer History ─────────────────────────────────────────────────────

data class CustomerHistory(
    val estimates: List<EstimateSummary> = emptyList(),
    val invoices:  List<InvoiceSummary>  = emptyList(),
    val photos:    List<HistoryPhoto>    = emptyList(),
    val notes:     List<HistoryNote>     = emptyList(),
    val jobs:      List<JobSummary>      = emptyList()
)

data class EstimateSummary(
    val id:             String,
    val estimateNumber: String,
    val total:          Double,
    val status:         String,
    val createdAt:      String? = null,
    val jobId:          String? = null
)

data class InvoiceSummary(
    val id:            String,
    val invoiceNumber: String,
    val total:         Double,
    val status:        String,
    val createdAt:     String? = null,
    val jobId:         String? = null
)

data class HistoryPhoto(
    val id:        String,
    val url:       String,
    val purpose:   String,
    val createdAt: String? = null,
    val jobId:     String? = null,
    val jobNumber: String? = null
)

data class HistoryNote(
    val id:        String,
    val content:   String,
    val createdAt: String? = null,
    val jobId:     String? = null,
    val jobNumber: String? = null,
    val jobTitle:  String? = null
)

data class JobSummary(
    val id:           String,
    val jobNumber:    String,
    val title:        String,
    val status:       String,
    val scheduledStart: String? = null,
    val address:      String? = null,
    val totalCharged: Double? = null
)

data class TimeWindow(
    val id:      String,
    val label:   String,
    val time:    String,
    val enabled: Boolean = true
)

data class ServiceArea(
    val zipCode:      String,
    val radiusMiles:  Int,
    val label:        String? = null
)

data class BookingSettings(
    val id:                     String?           = null,
    val enabled:                Boolean           = false,
    val companyDisplayName:     String?           = null,
    val companyTagline:         String?           = null,
    val primaryColor:           String            = "#1A73E8",
    val workingDays:            List<String>      = listOf("monday","tuesday","wednesday","thursday","friday"),
    val timeWindows:            List<TimeWindow>  = emptyList(),
    val maxBookingsPerWindow:   Int               = 3,
    val serviceAreaZips:        String            = "",
    val serviceAreas:           List<ServiceArea> = emptyList(),
    val services:               List<String>      = emptyList(),
    val confirmationMessage:    String            = "Thank you! We will confirm your appointment shortly.",
    val reminderEnabled:        Boolean           = true,
    val reminderHoursBefore:    Int               = 24,
    val reminderMethod:         String            = "email",
    val followupEnabled:        Boolean           = false,
    val followupDaysAfter:      Int               = 3,
    val followupRepeatEvery:    Int               = 7,
    val followupMaxReminders:   Int               = 3,
    val followupMethod:         String            = "email"
)

data class ReviewPlatform(
    val id:        String,
    val name:      String,
    val url:       String,
    val isDefault: Boolean = false,
    val isActive:  Boolean = true,
    val createdAt: String? = null
)

// ─── Job Source / Ad Channel ──────────────────────────────────────────────

data class JobSource(
    val id:                  String,
    val name:                String,
    val companyName:         String? = null,
    val phone:               String? = null,
    val email:               String? = null,
    val profitAllocationPct: Double  = 0.0,
    val sendUpdates:         Boolean = true,
    val sendClosings:        Boolean = true,
    val notes:               String? = null,
    val isActive:            Boolean = true
)

data class AdChannel(
    val id:           String,
    val name:         String,
    val isCustom:     Boolean = false,
    val isActive:     Boolean = true,
    val displayOrder: Int     = 0
)

data class SourceReportRow(
    val sourceName:          String,
    val jobCount:            Int,
    val totalRevenue:        Double,
    val avgTicket:           Double,
    val profitAllocationPct: Double? = null
)

data class SourceReport(
    val network:          List<SourceReportRow> = emptyList(),
    val externalContacts: List<SourceReportRow> = emptyList(),
    val ownCompany:       List<SourceReportRow> = emptyList()
)

// ─── Commission Rules ─────────────────────────────────────────────────────

data class CommissionRule(
    val id:                 String,
    val ruleType:           String,   // default, source_contact, ad_channel, network
    val jobSourceId:        String?  = null,
    val jobSourceName:      String?  = null,
    val adChannelId:        String?  = null,
    val adChannelName:      String?  = null,
    val techCommissionPct:  Double,
    val notes:              String?  = null
)

data class ResolvedCommission(
    val pct:        Double?,
    val ruleType:   String?,
    val sourceName: String?,
    val isDefault:  Boolean
)

// ─── Inventory ────────────────────────────────────────────────────────────

data class InventorySettings(
    val id: String? = null,
    val enabled: Boolean = false
)

data class Truck(
    val id: String = "",
    val name: String = "",
    val assignedToId: String? = null,
    val assignedToType: String? = null,
    val assignedToName: String? = null,
    val isActive: Boolean = true,
    val itemCount: Int = 0,
    val lowStockCount: Int = 0
)

data class InventoryItem(
    val id: String = "",
    val pricebookItemId: String = "",
    val itemName: String = "",
    val sku: String? = null,
    val unitCost: Double = 0.0,
    val qtyOnHand: Int = 0,
    val minQty: Int = 0,
    val isPermanent: Boolean = false,
    val isLowStock: Boolean = false
)

data class RestockRequestItem(
    val id: String = "",
    val pricebookItemId: String = "",
    val itemName: String = "",
    val qtyRequested: Int = 0,
    val qtyFulfilled: Int = 0
)

data class RestockRequest(
    val id: String = "",
    val truckId: String = "",
    val truckName: String? = null,
    val requestedByName: String? = null,
    val status: String = "pending",
    val notes: String? = null,
    val createdAt: String? = null,
    val fulfilledAt: String? = null,
    val items: List<RestockRequestItem> = emptyList()
)

// ─── Memberships ──────────────────────────────────────────────────────────

data class MembershipPlan(
    val id:          String = "",
    val name:        String = "",
    val description: String? = null,
    val frequency:   String = "monthly",   // weekly|monthly|quarterly|semi_annually|annually
    val price:       Double = 0.0,
    val isActive:    Boolean = true
)

data class CustomerMembership(
    val id:            String = "",
    val customerId:    String = "",
    val planId:        String? = null,
    val planName:      String = "",
    val planFrequency: String = "monthly",
    val planPrice:     Double = 0.0,
    val status:        String = "active",   // active|paused|cancelled
    val startDate:     String = "",
    val nextJobDate:   String? = null,
    val notes:         String? = null
)

data class MembershipDueSoon(
    val id:            String = "",
    val customerId:    String = "",
    val planName:      String = "",
    val planFrequency: String = "",
    val nextJobDate:   String? = null,
    val firstName:     String = "",
    val lastName:      String = "",
    val address:       String? = null,
    val city:          String? = null,
    val state:         String? = null
) {
    val customerName get() = "$firstName $lastName".trim()
}

// ─── Import Wizard ────────────────────────────────────────────────────────

data class FieldMapping(
    val sourceColumn: String,
    val targetField:  String?,
    val confidence:   String = "low"
)

data class ImportPreviewResponse(
    val headers:         List<String>              = emptyList(),
    val sampleRows:      List<List<String>>        = emptyList(),
    val totalRows:       Int                       = 0,
    val mappings:        List<FieldMapping>        = emptyList(),
    val categoryGuesses: Map<String, String>       = emptyMap(),
    val notes:           String                    = ""
)

data class ImportExecuteRequest(
    val type:                String,
    val fileData:            String,
    val fileName:            String,
    val mappings:            List<Map<String, String?>>,
    val duplicateAction:     String                = "skip",
    val categoryAssignments: Map<String, String>   = emptyMap(),
    val categoryGuesses:     Map<String, String>   = emptyMap()
)

data class ImportErrorRow(
    val row:     Int,
    val message: String
)

data class ImportResultResponse(
    val imported: Int                  = 0,
    val updated:  Int                  = 0,
    val skipped:  Int                  = 0,
    val errors:   List<ImportErrorRow> = emptyList()
)

// ─── SMS / Two-way Messaging ──────────────────────────────────────────────────

data class SmsConversation(
    val id: String = "",
    @SerializedName("company_id")      val companyId: String = "",
    @SerializedName("customer_id")     val customerId: String? = null,
    @SerializedName("job_id")          val jobId: String? = null,
    @SerializedName("customer_phone")  val customerPhone: String = "",
    @SerializedName("customer_name")   val customerName: String? = null,
    @SerializedName("last_message")    val lastMessage: String? = null,
    @SerializedName("last_message_at") val lastMessageAt: String? = null,
    @SerializedName("unread_count")    val unreadCount: Int = 0
)

data class SmsMessage(
    val id: String = "",
    @SerializedName("conversation_id") val conversationId: String = "",
    val direction: String = "outbound",
    val body: String = "",
    @SerializedName("from_number") val fromNumber: String = "",
    @SerializedName("to_number")   val toNumber: String = "",
    @SerializedName("created_at")  val createdAt: String = "",
    val status: String? = null
)

// GET /sms/conversations/:id/messages now returns { conversation, messages }.
data class ThreadResponse(
    val conversation: SmsConversation? = null,
    val messages: List<SmsMessage> = emptyList()
)

// ─── Timesheets ────────────────────────────────────────────────────────────

data class Timesheet(
    val id: String = "",
    @SerializedName("user_id")       val userId: String = "",
    @SerializedName("clock_in_at")   val clockInAt: String = "",
    @SerializedName("clock_out_at")  val clockOutAt: String? = null,
    @SerializedName("total_minutes") val totalMinutes: Int? = null,
    val notes: String? = null,
    val date: String = "",
    @SerializedName("tech_name")     val techName: String? = null
)

data class TimesheetStatus(
    @SerializedName("clocked_in")          val clockedIn: Boolean = false,
    @SerializedName("clock_in_at")         val clockInAt: String? = null,
    @SerializedName("total_minutes_today") val totalMinutesToday: Int = 0
)

data class TimesheetReport(
    val timesheets: List<Timesheet>  = emptyList(),
    val summary:    List<TimesheetSummary> = emptyList()
)

data class TimesheetSummary(
    @SerializedName("tech_name")     val techName: String = "",
    @SerializedName("user_id")       val userId: String = "",
    @SerializedName("total_minutes") val totalMinutes: Int = 0,
    @SerializedName("days_worked")   val daysWorked: Int = 0
)

// ─── Custom Fields ────────────────────────────────────────────────────────────

data class CustomField(
    val id: String = "",
    val company_id: String = "",
    val label: String = "",
    val field_key: String = "",
    val field_type: String = "text",   // text, number, dropdown, date, checkbox
    val entity: String = "job",        // job, customer, estimate
    val options: List<String>? = null, // for field_type == "dropdown"
    val required: Boolean = false,
    val sort_order: Int = 0,
    val active: Boolean = true,
    val created_at: String = ""
)

// ─── Ailot / Automation Rules ─────────────────────────────────────────────────

data class JobyRule(
    val id: String = "",
    val company_id: String = "",
    val name: String = "",
    val trigger_event: String = "",   // NOT "trigger" — matches web rule.trigger_event
    val type: String = "",            // NOT "action" — matches web rule.type
    val active: Boolean = true,       // NOT "enabled" — matches web rule.active
    val delay_minutes: Int = 0,
    val notify_customer: Boolean? = null,
    val notify_tech: Boolean? = null,
    val notify_owner: Boolean? = null,
    val dispatch_logic: String? = null,
    val sms_template: String? = null,
    val email_template: String? = null,
    val created_at: String = ""
)

// ─── QuickBooks Online ────────────────────────────────────────────────────────

data class QboStatus(
    val connected: Boolean = false,
    val realm_id: String? = null,
    val company_name: String? = null,
    val environment: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

data class QboConnectResponse(
    val url: String
)

data class QboSyncDetail(
    val id: String = "",
    val status: String = "",
    val qbo_id: String? = null,
    val message: String? = null
)

data class QboSyncResult(
    val synced: Int = 0,
    val errors: Int = 0,
    val total: Int = 0,
    val details: List<QboSyncDetail> = emptyList()
)
