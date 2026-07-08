package com.ultimatepro.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.ultimatepro.ui.auth.*
import com.ultimatepro.ui.calendar.CalendarScreen
import com.ultimatepro.ui.common.CRMTheme
import com.ultimatepro.ui.customers.*
import com.ultimatepro.ui.dashboard.DashboardScreen
import com.ultimatepro.ui.estimates.*
import com.ultimatepro.ui.invoices.*
import com.ultimatepro.ui.jobs.*
import com.ultimatepro.ui.maps.LiveMapScreen
import com.ultimatepro.ui.payments.PaymentsScreen
import com.ultimatepro.ui.payroll.*
import com.ultimatepro.ui.phone.*
import com.ultimatepro.data.socket.SocketViewModel
import com.ultimatepro.ui.pricebook.*
import com.ultimatepro.ui.reports.ReportsScreen
import com.ultimatepro.ui.reports.TimesheetReportScreen
import com.ultimatepro.ui.network.NetworkDetailScreen
import com.ultimatepro.ui.network.NetworkListScreen
import com.ultimatepro.ui.network.PartnerReportScreen
import com.ultimatepro.ui.settings.SettingsScreen
import com.ultimatepro.ui.settings.ReviewPlatformsScreen
import com.ultimatepro.ui.settings.OnlineBookingSettingsScreen
import com.ultimatepro.ui.settings.JobSourcesScreen
import com.ultimatepro.ui.settings.RosterTechsScreen
import com.ultimatepro.ui.settings.CustomFieldsScreen
import com.ultimatepro.ui.settings.AilotScreen
import com.ultimatepro.ui.settings.CompanyProfileScreen
import com.ultimatepro.ui.settings.TeamMembersScreen
import com.ultimatepro.ui.settings.QuickBooksScreen
import com.ultimatepro.ui.imports.ImportWizardScreen
import com.ultimatepro.ui.inventory.InventoryScreen
import com.ultimatepro.ui.inventory.InventoryViewModel
import com.ultimatepro.ui.inventory.RestockRequestDetailScreen
import com.ultimatepro.ui.inventory.RestockRequestsScreen
import com.ultimatepro.ui.inventory.TruckStockScreen
import com.ultimatepro.ui.common.NotificationPreferences
import com.ultimatepro.ui.notifications.NotificationsScreen
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

object Route {
    const val LOGIN              = "login"
    const val REGISTER           = "register"
    const val DASHBOARD          = "dashboard"
    const val JOBS               = "jobs"
    const val JOB_DETAIL         = "jobs/{id}"
    const val JOB_NEW            = "jobs/new"
    const val JOB_EDIT           = "jobs/{id}/edit"
    const val CUSTOMERS          = "customers"
    const val CUSTOMER_DETAIL    = "customers/{id}"
    const val CUSTOMER_NEW       = "customers/new"
    const val CUSTOMER_EDIT      = "customers/{id}/edit"
    const val CALENDAR           = "calendar"
    const val ESTIMATES          = "estimates"
    const val ESTIMATE_DETAIL    = "estimates/{id}"
    const val INVOICES           = "invoices"
    const val INVOICE_DETAIL     = "invoices/{id}"
    const val PAYMENTS           = "payments"
    const val PHONE              = "phone"
    const val SECOND_CHANCE      = "phone/second-chance"
    const val CALL_QUEUE         = "phone/queue"
    const val LIVE_MAP           = "live-map"
    const val REPORTS            = "reports"
    // Payroll
    const val PAYROLL            = "payroll"
    const val TECH_REPORT        = "payroll/tech/{userId}"
    const val TECH_PAY_SETTINGS  = "payroll/settings/{userId}"
    const val REIMBURSEMENTS     = "payroll/reimbursements"
    const val PROFIT_SIMULATOR   = "payroll/simulator"
    const val SETTINGS            = "settings"
    const val REVIEW_PLATFORMS    = "settings/review-platforms"
    const val ONLINE_BOOKING      = "settings/online-booking"
    const val JOB_SOURCES         = "settings/job-sources"
    const val MEMBERSHIP_PLANS    = "settings/membership-plans"
    // Inventory
    const val INVENTORY           = "inventory"
    const val TRUCK_STOCK         = "inventory/trucks/{truckId}"
    const val RESTOCK_REQUESTS    = "inventory/restock-requests"
    const val RESTOCK_DETAIL      = "inventory/restock-requests/{requestId}"
    // Network
    const val NETWORK            = "network"
    const val NETWORK_DETAIL     = "network/{connectionId}"
    const val NETWORK_REPORT     = "network/{connectionId}/report"
    // Pricebook
    const val PRICEBOOK                 = "pricebook"
    const val PRICEBOOK_ALL             = "pricebook/all"          // flat picker — bypasses categories
    const val PRICEBOOK_CATEGORY        = "pricebook/category/{categoryId}" // picker: items in a category
    const val PRICEBOOK_ITEM            = "pricebook/item/{itemId}"
    const val PRICEBOOK_MANAGE          = "pricebook/manage"       // manage: category grid
    const val PRICEBOOK_MANAGE_CATEGORY = "pricebook/manage/{categoryId}/items" // manage: items in category
    // Estimate flow
    const val ESTIMATE_BUILD          = "estimates/build/{jobId}"
    const val ESTIMATE_BUILD_CUSTOMER = "estimates/build-customer/{customerId}"
    const val ESTIMATE_EDIT           = "estimates/{id}/edit"
    const val ESTIMATE_SIGN           = "estimates/{id}/sign"
    const val ESTIMATE_PRESENT_TIERS  = "estimates/{id}/present-tiers"
    const val ESTIMATE_SEND           = "estimates/{id}/send"
    const val COLLECT_DEPOSIT         = "estimates/{estimateId}/collect-deposit"
    // Invoice flow
    const val INVOICE_NEW        = "invoices/new/{customerId}"
    const val INVOICE_SIGN       = "invoices/{id}/sign"
    const val INVOICE_PAYMENT    = "invoices/{id}/payment"
    const val INVOICE_RECEIPT    = "invoices/{id}/receipt"
    const val INVOICE_SEND       = "invoices/{id}/send"
    // Job completion
    const val JOB_COMPLETE       = "jobs/{jobId}/complete"
    // Import
    const val IMPORT_PRICEBOOK   = "import/pricebook"
    const val IMPORT_CUSTOMERS   = "import/customers"
    // Roster Techs
    const val ROSTER_TECHS       = "settings/roster-techs"
    const val TEAM_MEMBERS       = "settings/team-members"
    const val CUSTOM_FIELDS      = "settings/custom-fields"
    const val AILOT              = "settings/ailot"
    const val INTEGRATIONS       = "settings/integrations"
    const val COMPANY_PROFILE    = "settings/company-profile"
    const val TIMESHEET_REPORT   = "reports/timesheets"
    const val SMS_THREAD         = "phone/sms/{conversationId}"
    const val NOTIFICATIONS      = "notifications"
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotifPrefsEntryPoint {
    fun notificationPreferences(): NotificationPreferences
}

private data class BottomTab(
    val route: String,
    val icon:  androidx.compose.ui.graphics.vector.ImageVector,
    val label: String
)

private val bottomTabs = listOf(
    BottomTab(Route.DASHBOARD, Icons.Default.Dashboard, "Dashboard"),
    BottomTab(Route.JOBS,      Icons.Default.Work,       "Jobs"),
    BottomTab(Route.CUSTOMERS, Icons.Default.People,     "Customers"),
    BottomTab(Route.PHONE,     Icons.Default.Phone,      "Phone"),
    BottomTab(Route.SETTINGS,  Icons.Default.MoreHoriz,  "More"),
)

@Composable
fun App(
    darkTheme: Boolean = false,
    onToggleDark: (Boolean) -> Unit = {}
) {
    CRMTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        val authVm: AuthViewModel = hiltViewModel()
        val loggedIn by authVm.loggedIn.collectAsState()
        val context = LocalContext.current
        val notifPrefs = remember(context) {
            EntryPoints.get(context.applicationContext, NotifPrefsEntryPoint::class.java)
                .notificationPreferences()
        }

        // Handle deep links from push notification intents
        val activity = LocalContext.current as? Activity
        LaunchedEffect(loggedIn) {
            if (!loggedIn) return@LaunchedEffect
            val raw = activity?.intent?.getStringExtra("navigate_to") ?: return@LaunchedEffect
            // Map short-form "job/$id" → actual nav route "jobs/$id"
            val route = when {
                raw.startsWith("job/")        -> "jobs/${raw.removePrefix("job/")}"
                raw.startsWith("estimate/")   -> "estimates/${raw.removePrefix("estimate/")}"
                raw == "jobs"                 -> Route.JOBS
                else                          -> raw  // phone/sms/{convId} matches directly
            }
            navController.navigate(route) {
                popUpTo(Route.DASHBOARD) { inclusive = false }
                launchSingleTop = true
            }
            // Consume the extra so rotation/recompose doesn't re-navigate
            activity.intent.removeExtra("navigate_to")
        }

        // P2.2: auto-logout. When the session dies at runtime (a 401 clears the token and
        // trips SessionManager → loggedIn=false), route to the login screen and clear the
        // back stack. Guarded on a true→false transition so it never fires on the initial
        // launch or the login→dashboard flow.
        val wasLoggedIn = remember { mutableStateOf(loggedIn) }
        LaunchedEffect(loggedIn) {
            if (wasLoggedIn.value && !loggedIn) {
                navController.navigate(Route.LOGIN) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
            wasLoggedIn.value = loggedIn
        }

        NavHost(
            navController    = navController,
            startDestination = if (loggedIn) Route.DASHBOARD else Route.LOGIN,
            enterTransition  = { slideInHorizontally { it } + fadeIn() },
            exitTransition   = { slideOutHorizontally { -it } + fadeOut() },
            popEnterTransition  = { slideInHorizontally { -it } + fadeIn() },
            popExitTransition   = { slideOutHorizontally { it } + fadeOut() }
        ) {
            // Auth
            composable(Route.LOGIN) {
                LoginScreen(onSuccess = { navController.navigate(Route.DASHBOARD) { popUpTo(0) } },
                    onRegister = { navController.navigate(Route.REGISTER) })
            }
            composable(Route.REGISTER) {
                RegisterScreen(onSuccess = { navController.navigate(Route.DASHBOARD) { popUpTo(0) } },
                    onBack = { navController.popBackStack() })
            }

            // Main bottom nav screens
            composable(Route.DASHBOARD) {
                ScaffoldWithNav(navController, Route.DASHBOARD) {
                    DashboardScreen(
                        onJobClick       = { navController.navigate("jobs/$it") },
                        onAllJobs        = { navController.navigate(Route.JOBS) },
                        onLiveMap        = { navController.navigate(Route.LIVE_MAP) },
                        onPhone          = { navController.navigate(Route.PHONE) },
                        onPasteTicket    = { text -> navController.navigate("jobs/new?ticket=${Uri.encode(text)}") },
                        onCustomer       = { navController.navigate("customers/$it") },
                        onNotifications  = { navController.navigate(Route.NOTIFICATIONS) }
                    )
                }
            }
            composable(Route.NOTIFICATIONS) {
                NotificationsScreen(
                    onBack      = { navController.popBackStack() },
                    onJob       = { navController.navigate("jobs/$it") },
                    onInvoice   = { navController.navigate("invoices/$it") },
                    onEstimate  = { navController.navigate("estimates/$it") },
                    onCustomer  = { navController.navigate("customers/$it") }
                )
            }
            composable(Route.JOBS) {
                ScaffoldWithNav(navController, Route.JOBS) {
                    JobListScreen(onJob = { navController.navigate("jobs/$it") },
                        onNewJob = { navController.navigate(Route.JOB_NEW) })
                }
            }
            composable(Route.CUSTOMERS) {
                ScaffoldWithNav(navController, Route.CUSTOMERS) {
                    CustomerListScreen(
                        onCustomer    = { navController.navigate("customers/$it") },
                        onNewCustomer = { navController.navigate(Route.CUSTOMER_NEW) },
                        onImport      = { navController.navigate(Route.IMPORT_CUSTOMERS) }
                    )
                }
            }
            composable(Route.PHONE) {
                ScaffoldWithNav(navController, Route.PHONE) {
                    PhoneScreen(onSecondChance = { navController.navigate(Route.SECOND_CHANCE) },
                        onQueue     = { navController.navigate(Route.CALL_QUEUE) },
                        onCustomer  = { navController.navigate("customers/$it") },
                        onSmsThread = { convId -> navController.navigate("phone/sms/$convId") })
                }
            }
            composable(Route.SETTINGS) {
                ScaffoldWithNav(navController, Route.SETTINGS) {
                    SettingsScreen(
                        onCalendar   = { navController.navigate(Route.CALENDAR) },
                        onEstimates  = { navController.navigate(Route.ESTIMATES) },
                        onInvoices   = { navController.navigate(Route.INVOICES) },
                        onPayments   = { navController.navigate(Route.PAYMENTS) },
                        onReports    = { navController.navigate(Route.REPORTS) },
                        onPayroll    = { navController.navigate(Route.PAYROLL) },
                        onLiveMap    = { navController.navigate(Route.LIVE_MAP) },
                        onPricebook        = { navController.navigate(Route.PRICEBOOK_MANAGE) },
                        onNetwork          = { navController.navigate(Route.NETWORK) },
                        onReviewPlatforms  = { navController.navigate(Route.REVIEW_PLATFORMS) },
                        onOnlineBooking    = { navController.navigate(Route.ONLINE_BOOKING) },
                        onJobSources       = { navController.navigate(Route.JOB_SOURCES) },
                        onMembershipPlans  = { navController.navigate(Route.MEMBERSHIP_PLANS) },
                        onInventory        = { navController.navigate(Route.INVENTORY) },
                        onTechnicians      = { navController.navigate(Route.ROSTER_TECHS) },
                        onTeamMembers      = { navController.navigate(Route.TEAM_MEMBERS) },
                        onCustomFields     = { navController.navigate(Route.CUSTOM_FIELDS) },
                        onAilot            = { navController.navigate(Route.AILOT) },
                        onIntegrations     = { navController.navigate(Route.INTEGRATIONS) },
                        onCompanyProfile   = { navController.navigate(Route.COMPANY_PROFILE) },
                        onLogout     = { authVm.logout(); navController.navigate(Route.LOGIN) { popUpTo(0) } },
                        isDark       = darkTheme,
                        onToggleDark = onToggleDark,
                        notifPrefs   = notifPrefs
                    )
                }
            }

            // ── Contractor Network ────────────────────────────────────
            composable(Route.NETWORK) {
                NetworkListScreen(
                    onConnection = { id -> navController.navigate("network/$id") }
                )
            }
            composable(Route.NETWORK_DETAIL, listOf(navArgument("connectionId") { type = NavType.StringType })) {
                val connectionId = it.arguments?.getString("connectionId") ?: ""
                NetworkDetailScreen(
                    connectionId = connectionId,
                    onBack       = { navController.popBackStack() },
                    onReport     = { navController.navigate("network/$connectionId/report") }
                )
            }
            composable(Route.NETWORK_REPORT, listOf(navArgument("connectionId") { type = NavType.StringType })) {
                val connectionId = it.arguments?.getString("connectionId") ?: ""
                PartnerReportScreen(
                    connectionId = connectionId,
                    onBack       = { navController.popBackStack() }
                )
            }

            composable(Route.REVIEW_PLATFORMS) {
                ReviewPlatformsScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.ONLINE_BOOKING) {
                OnlineBookingSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.JOB_SOURCES) {
                JobSourcesScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.MEMBERSHIP_PLANS) {
                com.ultimatepro.ui.memberships.MembershipPlansScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.ROSTER_TECHS) {
                RosterTechsScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.TEAM_MEMBERS) {
                TeamMembersScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.CUSTOM_FIELDS) {
                CustomFieldsScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.AILOT) {
                AilotScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.INTEGRATIONS) {
                QuickBooksScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.COMPANY_PROFILE) {
                CompanyProfileScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.INVENTORY) {
                InventoryScreen(
                    onBack            = { navController.popBackStack() },
                    onTruckStock      = { truckId -> navController.navigate("inventory/trucks/$truckId") },
                    onRestockRequests = { navController.navigate(Route.RESTOCK_REQUESTS) }
                )
            }
            composable(Route.TRUCK_STOCK, listOf(navArgument("truckId") { type = NavType.StringType })) {
                val truckId = it.arguments?.getString("truckId") ?: ""
                TruckStockScreen(
                    truckId           = truckId,
                    onBack            = { navController.popBackStack() },
                    onRestockRequests = { navController.navigate(Route.RESTOCK_REQUESTS) }
                )
            }
            composable(Route.RESTOCK_REQUESTS) {
                RestockRequestsScreen(
                    onBack   = { navController.popBackStack() },
                    onDetail = { id -> navController.navigate("inventory/restock-requests/$id") }
                )
            }
            composable(Route.RESTOCK_DETAIL, listOf(navArgument("requestId") { type = NavType.StringType })) {
                val requestId = it.arguments?.getString("requestId") ?: ""
                RestockRequestDetailScreen(
                    requestId = requestId,
                    onBack    = { navController.popBackStack() }
                )
            }

            // Detail screens
            composable(Route.JOB_DETAIL, listOf(navArgument("id") { type = NavType.StringType })) {
                val jobId = it.arguments?.getString("id") ?: ""
                JobDetailScreen(jobId = jobId,
                    onBack            = { navController.popBackStack() },
                    onEdit            = { navController.navigate("jobs/$jobId/edit") },
                    onComplete        = { navController.navigate("jobs/$jobId/complete") },
                    onCustomer        = { id -> navController.navigate("customers/$id") },
                    onInvoice         = { id -> navController.navigate("invoices/$id") },
                    onPayment         = { id -> navController.navigate("invoices/$id/payment") },
                    onCreateEstimate  = { navController.navigate("estimates/build/$jobId") },
                    onViewEstimate    = { id -> navController.navigate("estimates/$id") },
                    onViewInvoice     = { id -> navController.navigate("invoices/$id") },
                    onLinkedJob       = { id -> navController.navigate("jobs/$id") },
                    onSmsThread       = { convId -> navController.navigate("phone/sms/$convId") })
            }
            composable(Route.JOB_COMPLETE, listOf(navArgument("jobId") { type = NavType.StringType })) {
                val jobId = it.arguments?.getString("jobId") ?: ""
                CompleteJobScreen(
                    jobId  = jobId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Route.JOB_EDIT, listOf(navArgument("id") { type = NavType.StringType })) {
                // P2.1: edit reuses the redesigned JobFormScreen (unified create+edit, like web).
                JobFormScreen(
                    editJobId = it.arguments?.getString("id") ?: "",
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() })
            }
            composable(
                "jobs/new?ticket={ticket}",
                arguments = listOf(navArgument("ticket") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) {
                JobFormScreen(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
            }
            composable(Route.CUSTOMER_DETAIL, listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val customerId = entry.arguments?.getString("id") ?: ""
                CustomerDetailScreen(customerId = customerId,
                    onBack      = { navController.popBackStack() },
                    onEdit      = { navController.navigate("customers/$customerId/edit") },
                    onJob       = { id -> navController.navigate("jobs/$id") },
                    onNewJob    = { navController.navigate(Route.JOB_NEW) },
                    onDeleted   = { navController.navigate(Route.CUSTOMERS) { popUpTo(Route.CUSTOMERS) { inclusive = true } } },
                    onSmsThread = { convId -> navController.navigate("phone/sms/$convId") },
                    onEstimate  = { id -> navController.navigate("estimates/$id") },
                    onInvoice   = { id -> navController.navigate("invoices/$id") })
            }
            composable(Route.CUSTOMER_NEW) {
                CustomerFormScreen(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
            }
            composable(Route.CUSTOMER_EDIT, listOf(navArgument("id") { type = NavType.StringType })) {
                CustomerEditScreen(
                    customerId = it.arguments?.getString("id") ?: "",
                    onBack     = { navController.popBackStack() },
                    onSaved    = { navController.popBackStack() })
            }
            composable(Route.CALENDAR) {
                CalendarScreen(onJob = { navController.navigate("jobs/$it") }, onBack = { navController.popBackStack() })
            }
            composable(Route.ESTIMATES) {
                EstimateListScreen(
                    onEstimate    = { navController.navigate("estimates/$it") },
                    onBack        = { navController.popBackStack() },
                    onNewEstimate = { customerId -> navController.navigate("estimates/build-customer/$customerId") }
                )
            }
            composable(Route.ESTIMATE_DETAIL, listOf(navArgument("id") { type = NavType.StringType })) {
                val id = it.arguments?.getString("id") ?: ""
                val vm: EstimateViewModel = hiltViewModel()
                val socketVm: SocketViewModel = hiltViewModel()
                // Real-time: refresh when customer signs via the web signing page
                LaunchedEffect(Unit) {
                    socketVm.documentSigned.collect { event ->
                        if (event.type == "estimate" && event.id == id) vm.loadEst(id)
                    }
                }
                EstimateDetailScreen(
                    id                 = id,
                    onBack             = { navController.popBackStack() },
                    onSign             = { navController.navigate("estimates/$id/sign") },
                    onSend             = { navController.navigate("estimates/$id/send") },
                    onPresent          = { navController.navigate("estimates/$id/present-tiers") },
                    onEdit             = { navController.navigate("estimates/$id/edit") },
                    onConvertToInvoice = { invoiceId -> navController.navigate("invoices/$invoiceId") },
                    onCollectDeposit   = { eid -> navController.navigate("estimates/$eid/collect-deposit") },
                    vm                 = vm
                )
            }
            composable(Route.ESTIMATE_BUILD, listOf(navArgument("jobId") { type = NavType.StringType })) {
                val jobId = it.arguments?.getString("jobId") ?: ""
                val activity = LocalContext.current as ComponentActivity
                val pickerVm: PricebookPickerViewModel = hiltViewModel(activity)
                EstimateBuildScreen(
                    jobId              = jobId,
                    onBack             = { navController.popBackStack() },
                    onSign             = { estimateId -> navController.navigate("estimates/$estimateId/sign") },
                    onSend             = { estimateId -> navController.navigate("estimates/$estimateId/send") },
                    onAddFromPricebook = { t -> navController.navigate(Route.PRICEBOOK_ALL + "?type=$t") },  // P2.22
                    pickerVm           = pickerVm
                )
            }
            composable(Route.ESTIMATE_EDIT, listOf(navArgument("id") { type = NavType.StringType })) {
                val estimateId = it.arguments?.getString("id") ?: ""
                val activity = LocalContext.current as ComponentActivity
                val pickerVm: PricebookPickerViewModel = hiltViewModel(activity)
                // jobId is blank — EstimateBuildViewModel.loadExisting() will derive it from the estimate
                EstimateBuildScreen(
                    jobId              = "",
                    estimateId         = estimateId,
                    onBack             = { navController.popBackStack() },
                    onSign             = { eid -> navController.navigate("estimates/$eid/sign") },
                    onSend             = { eid -> navController.navigate("estimates/$eid/send") },
                    onAddFromPricebook = { t -> navController.navigate(Route.PRICEBOOK_ALL + "?type=$t") },  // P2.22
                    pickerVm           = pickerVm
                )
            }
            composable(Route.ESTIMATE_BUILD_CUSTOMER, listOf(navArgument("customerId") { type = NavType.StringType })) {
                val customerId = it.arguments?.getString("customerId") ?: ""
                val activity = LocalContext.current as ComponentActivity
                val pickerVm: PricebookPickerViewModel = hiltViewModel(activity)
                EstimateBuildScreen(
                    jobId              = "",
                    customerId         = customerId,
                    onBack             = { navController.popBackStack() },
                    onSign             = { eid -> navController.navigate("estimates/$eid/sign") },
                    onSend             = { eid -> navController.navigate("estimates/$eid/send") },
                    onAddFromPricebook = { t -> navController.navigate(Route.PRICEBOOK_ALL + "?type=$t") },  // P2.22
                    pickerVm           = pickerVm
                )
            }
            composable(Route.INVOICE_NEW, listOf(navArgument("customerId") { type = NavType.StringType })) {
                val customerId = it.arguments?.getString("customerId") ?: ""
                InvoiceNewScreen(
                    customerId = customerId,
                    onBack     = { navController.popBackStack() },
                    onCreated  = { invoiceId -> navController.navigate("invoices/$invoiceId") { popUpTo(Route.INVOICES) } }
                )
            }
            composable(Route.ESTIMATE_SIGN, listOf(navArgument("id") { type = NavType.StringType })) {
                val id = it.arguments?.getString("id") ?: ""
                EstimateSignScreen(
                    estimateId = id,
                    onBack     = { navController.popBackStack() },
                    onSigned   = { jobId, needsDeposit ->
                        when {
                            needsDeposit -> navController.navigate("estimates/$id/collect-deposit") {
                                popUpTo(Route.ESTIMATE_SIGN) { inclusive = true }
                            }
                            !jobId.isNullOrBlank() -> navController.navigate("jobs/$jobId") {
                                popUpTo(Route.ESTIMATES) { inclusive = false }
                            }
                            else -> navController.navigate("estimates/$id") {
                                popUpTo(Route.ESTIMATES) { inclusive = false }
                            }
                        }
                    }
                )
            }
            composable(Route.ESTIMATE_PRESENT_TIERS, listOf(navArgument("id") { type = NavType.StringType })) {
                val id = it.arguments?.getString("id") ?: ""
                PresentTiersScreen(
                    estimateId      = id,
                    onBack          = { navController.popBackStack() },
                    onTierSelected  = { _, estId ->
                        navController.navigate("estimates/$estId/sign") {
                            popUpTo(Route.ESTIMATE_PRESENT_TIERS) { inclusive = true }
                        }
                    }
                )
            }
            composable(Route.COLLECT_DEPOSIT, listOf(navArgument("estimateId") { type = NavType.StringType })) {
                val estimateId = it.arguments?.getString("estimateId") ?: ""
                DepositCollectionScreen(
                    estimateId = estimateId,
                    onBack     = { navController.popBackStack() },
                    onDone     = { jobId ->
                        if (!jobId.isNullOrBlank()) {
                            navController.navigate("jobs/$jobId") {
                                popUpTo(Route.ESTIMATES) { inclusive = false }
                            }
                        } else {
                            navController.navigate("estimates/$estimateId") {
                                popUpTo(Route.ESTIMATES) { inclusive = false }
                            }
                        }
                    }
                )
            }
            composable(Route.ESTIMATE_SEND, listOf(navArgument("id") { type = NavType.StringType })) {
                val id = it.arguments?.getString("id") ?: ""
                EstimateSendScreen(
                    estimateId = id,
                    onBack     = { navController.popBackStack() },
                    onSent     = { navController.popBackStack() }
                )
            }
            composable(Route.INVOICES) {
                InvoiceListScreen(
                    onInvoice    = { navController.navigate("invoices/$it") },
                    onBack       = { navController.popBackStack() },
                    onNewInvoice = { customerId -> navController.navigate("invoices/new/$customerId") }
                )
            }
            composable(Route.INVOICE_DETAIL, listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: ""
                val vm: InvoiceViewModel = hiltViewModel()
                val activity = LocalContext.current as ComponentActivity
                val pickerVm: PricebookPickerViewModel = hiltViewModel(activity)
                val socketVm: SocketViewModel = hiltViewModel()
                // Real-time: refresh when customer signs via the web signing page
                LaunchedEffect(Unit) {
                    socketVm.documentSigned.collect { event ->
                        if (event.type == "invoice" && event.id == id) vm.loadInv(id)
                    }
                }
                InvoiceDetailScreen(
                    id        = id,
                    onBack    = { navController.popBackStack() },
                    onSign    = { navController.navigate("invoices/$id/sign") },
                    onPayment = { navController.navigate("invoices/$id/payment") },
                    onReceipt = { navController.navigate("invoices/$id/receipt") },
                    onSend    = { navController.navigate("invoices/$id/send") },
                    onAddItem = { navController.navigate(Route.PRICEBOOK_ALL) },
                    pickerVm  = pickerVm,
                    vm        = vm
                )
            }
            composable(Route.INVOICE_SIGN, listOf(navArgument("id") { type = NavType.StringType })) {
                val id = it.arguments?.getString("id") ?: ""
                InvoiceSignScreen(
                    invoiceId = id,
                    onBack    = { navController.popBackStack() },
                    onSigned  = {
                        navController.navigate("invoices/$id/payment") {
                            popUpTo("invoices/$id") { inclusive = false }
                        }
                    }
                )
            }
            composable(Route.INVOICE_PAYMENT, listOf(navArgument("id") { type = NavType.StringType })) {
                val id = it.arguments?.getString("id") ?: ""
                PaymentScreen(
                    invoiceId = id,
                    onBack    = { navController.popBackStack() },
                    onPaid    = { navController.navigate("invoices/$id/receipt") { popUpTo("invoices/$id") } }
                )
            }
            composable(Route.INVOICE_RECEIPT, listOf(navArgument("id") { type = NavType.StringType })) {
                val id = it.arguments?.getString("id") ?: ""
                ReceiptScreen(
                    invoiceId = id,
                    onBack    = { navController.popBackStack() },
                    onDone    = { jobId ->
                        if (jobId != null) {
                            navController.navigate("jobs/$jobId") {
                                popUpTo("invoices/$id") { inclusive = true }
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate("invoices/$id") { popUpTo("invoices/$id") { inclusive = true } }
                        }
                    }
                )
            }
            composable(Route.INVOICE_SEND, listOf(navArgument("id") { type = NavType.StringType })) {
                val id = it.arguments?.getString("id") ?: ""
                InvoiceSendScreen(
                    invoiceId = id,
                    onBack    = { navController.popBackStack() },
                    onSent    = { navController.popBackStack() }
                )
            }
            composable(Route.PAYMENTS) { PaymentsScreen(onBack = { navController.popBackStack() }) }
            composable(Route.SECOND_CHANCE) {
                SecondChanceScreen(onBack = { navController.popBackStack() },
                    onCustomer = { navController.navigate("customers/$it") })
            }
            composable(Route.CALL_QUEUE)  { LiveQueueScreen(onBack = { navController.popBackStack() }) }
            composable(Route.SMS_THREAD, listOf(navArgument("conversationId") { type = NavType.StringType })) {
                val convId = it.arguments?.getString("conversationId") ?: ""
                SmsThreadScreen(conversationId = convId, onBack = { navController.popBackStack() })
            }
            composable(Route.LIVE_MAP) {
                LiveMapScreen(onBack = { navController.popBackStack() },
                    onJob = { navController.navigate("jobs/$it") })
            }
            composable(Route.REPORTS) {
                ReportsScreen(
                    onBack             = { navController.popBackStack() },
                    onTimesheetReport  = { navController.navigate(Route.TIMESHEET_REPORT) }
                )
            }
            composable(Route.TIMESHEET_REPORT) {
                TimesheetReportScreen(onBack = { navController.popBackStack() })
            }

            // ── Pricebook ─────────────────────────────────────────────────
            composable(Route.PRICEBOOK) {
                val invVm: InventoryViewModel = hiltViewModel()
                val invSettings  by invVm.settings.collectAsState()
                val techTruck    by invVm.techTruck.collectAsState()
                val truckStock   by invVm.truckStock.collectAsState()
                var showRestock  by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    invVm.loadSettings()
                    invVm.loadTechTruckForCurrentUser()
                }
                LaunchedEffect(techTruck) {
                    techTruck?.let { invVm.loadTruckStock(it.id) }
                }

                val canRestock = invSettings.enabled && techTruck != null

                PricebookCategoryScreen(
                    onCategory       = { cat -> navController.navigate("pricebook/category/${cat.id}") },
                    onDone           = { navController.popBackStack() },
                    onBack           = { navController.popBackStack() },
                    onRequestRestock = if (canRestock) ({ showRestock = true }) else null
                )

                if (showRestock && techTruck != null) {
                    com.ultimatepro.ui.inventory.RequestRestockDialog(
                        truck      = techTruck!!,
                        truckStock = truckStock,
                        onDismiss  = { showRestock = false },
                        onSubmit   = { truckId, notes, items ->
                            invVm.createRestockRequest(truckId, notes, items) { showRestock = false }
                        }
                    )
                }
            }
            // Flat picker — uses an activity-scoped PricebookPickerViewModel so the same
            // VM instance is shared with the calling Estimate/Invoice screen. No back-stack walk.
            composable(
                Route.PRICEBOOK_ALL + "?type={type}",
                listOf(navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) {
                val activity = LocalContext.current as ComponentActivity
                val pickerVm: PricebookPickerViewModel = hiltViewModel(activity)
                PricebookItemListScreen(
                    categoryId   = null,
                    categoryName = "All Items",
                    filter       = it.arguments?.getString("type"),   // P2.22: labor|material picker filter
                    vm           = pickerVm,
                    onItem       = { item -> navController.navigate("pricebook/item/${item.id}") },
                    onBack       = { navController.popBackStack() },
                    onDone       = { navController.popBackStack() }
                )
            }
            composable(Route.PRICEBOOK_CATEGORY, listOf(navArgument("categoryId") { type = NavType.StringType })) {
                val catId = it.arguments?.getString("categoryId") ?: ""
                val activity = LocalContext.current as ComponentActivity
                val pickerVm: PricebookPickerViewModel = hiltViewModel(activity)
                PricebookItemListScreen(
                    categoryId = catId,
                    vm         = pickerVm,
                    onItem     = { item -> navController.navigate("pricebook/item/${item.id}") },
                    onBack     = { navController.popBackStack() },
                    onDone     = { navController.popBackStack() }
                )
            }
            composable(Route.PRICEBOOK_ITEM, listOf(navArgument("itemId") { type = NavType.StringType })) { entry ->
                val itemId = entry.arguments?.getString("itemId") ?: ""
                val activity = LocalContext.current as ComponentActivity
                val pickerVm: PricebookPickerViewModel = hiltViewModel(activity)
                PricebookItemDetailScreen(
                    itemId  = itemId,
                    vm      = pickerVm,
                    onBack  = { navController.popBackStack() },
                    onAdded = { navController.popBackStack() }
                )
            }
            composable(Route.PRICEBOOK_MANAGE) {
                PricebookMainScreen(
                    onBack     = { navController.popBackStack() },
                    onCategory = { cat -> navController.navigate("pricebook/manage/${cat.id}/items") },
                    onImport   = { navController.navigate(Route.IMPORT_PRICEBOOK) }
                )
            }
            composable(
                Route.PRICEBOOK_MANAGE_CATEGORY,
                listOf(navArgument("categoryId") { type = NavType.StringType })
            ) {
                PricebookCategoryItemsScreen(
                    categoryId = it.arguments?.getString("categoryId") ?: "",
                    onBack     = { navController.popBackStack() }
                )
            }

            // ── Import Wizard ─────────────────────────────────────────────
            composable(Route.IMPORT_PRICEBOOK) {
                ImportWizardScreen(
                    type   = "pricebook",
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Route.IMPORT_CUSTOMERS) {
                ImportWizardScreen(
                    type   = "customers",
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Payroll hub ───────────────────────────────────────────────
            composable(Route.PAYROLL) {
                PayrollScreen(
                    onTechDetail    = { navController.navigate("payroll/tech/$it") },
                    onTechSettings  = { navController.navigate("payroll/settings/$it") },
                    onReimbursements= { navController.navigate(Route.REIMBURSEMENTS) },
                    onSimulator     = { navController.navigate(Route.PROFIT_SIMULATOR) },
                    onBack          = { navController.popBackStack() }
                )
            }
            composable(Route.TECH_REPORT, listOf(navArgument("userId") { type = NavType.StringType })) {
                TechReportScreen(userId = it.arguments?.getString("userId") ?: "",
                    onBack = { navController.popBackStack() })
            }
            composable(Route.TECH_PAY_SETTINGS, listOf(navArgument("userId") { type = NavType.StringType })) {
                TechPaySettingsScreen(userId = it.arguments?.getString("userId") ?: "",
                    onBack = { navController.popBackStack() })
            }
            composable(Route.REIMBURSEMENTS) {
                ReimbursementsScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.PROFIT_SIMULATOR) {
                ProfitSimulatorScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun ScaffoldWithNav(navController: NavHostController, current: String,
    content: @Composable () -> Unit) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(bottomBar = {
        NavigationBar {
            bottomTabs.forEach { tab ->
                NavigationBarItem(selected = route == tab.route,
                    onClick = {
                        navController.navigate(tab.route) {
                            popUpTo(Route.DASHBOARD) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    }, icon = { Icon(tab.icon, tab.label) }, label = { Text(tab.label) })
            }
        }
    }) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) { content() }
    }
}
