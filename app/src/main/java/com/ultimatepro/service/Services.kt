package com.ultimatepro.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.Preferences
import com.google.android.gms.location.*
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ultimatepro.MainActivity
import com.ultimatepro.R
import com.ultimatepro.UltimateProApp
import com.ultimatepro.data.local.TokenStore
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.ui.common.NotificationPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// ── FCM Push Notification Service ─────────────────────────────────────────

@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject lateinit var repo: CrmRepository
    @Inject lateinit var notifPrefs: NotificationPreferences

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "UltimatePro"
        val body  = message.notification?.body  ?: message.data["body"]  ?: ""
        val type  = message.data["type"] ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            // Check user's notification preferences before showing
            val enabled = when (type) {
                "new_job"                      -> notifPrefs.newJobs.first()
                "job_status"                   -> notifPrefs.statusUpdates.first()
                "partner_job", "partner_confirm" -> notifPrefs.partnerJobs.first()
                "new_booking"                  -> notifPrefs.newBookings.first()
                "estimate_signed"              -> notifPrefs.estimateSigned.first()
                else                           -> true
            }
            if (!enabled) return@launch

            val channelId = when {
                type.startsWith("call")     -> UltimateProApp.CH_CALLS
                type == "incoming_sms"      -> UltimateProApp.CH_GENERAL
                else                        -> UltimateProApp.CH_GENERAL
            }

            // Build deep-link intent based on notification type
            val intent = Intent(this@FcmService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                message.data.forEach { (k, v) -> putExtra(k, v) }
                when (type) {
                    "new_job", "job_status",
                    "partner_job", "partner_confirm" -> {
                        message.data["job_id"]?.let { putExtra("navigate_to", "job/$it") }
                    }
                    "new_booking" -> {
                        message.data["job_id"]?.takeIf { it.isNotBlank() }
                            ?.let { putExtra("navigate_to", "job/$it") }
                            ?: putExtra("navigate_to", "jobs")
                    }
                    "estimate_signed" -> {
                        message.data["estimate_id"]?.let { putExtra("navigate_to", "estimate/$it") }
                    }
                    "incoming_sms" -> {
                        val convId = message.data["conversation_id"]
                        val jobId  = message.data["job_id"]?.takeIf { it.isNotBlank() }
                        when {
                            jobId != null    -> putExtra("navigate_to", "job/$jobId")
                            convId != null   -> putExtra("navigate_to", "phone/sms/$convId")
                            else             -> putExtra("navigate_to", "phone")
                        }
                    }
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                this@FcmService, System.currentTimeMillis().toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this@FcmService, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(if (type.startsWith("call")) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
                .build()

            val mgr = getSystemService(NotificationManager::class.java)
            mgr.notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    override fun onNewToken(token: String) {
        // Use WorkManager for reliable token upload with retry on failure
        TokenUploadWorker.enqueue(applicationContext, token)
    }
}

// ── Background GPS Location Service ───────────────────────────────────────

@AndroidEntryPoint
class LocationService : Service() {

    @Inject lateinit var repo:       CrmRepository
    @Inject lateinit var tokenStore: TokenStore

    private lateinit var fusedClient: FusedLocationProviderClient
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                scope.launch {
                    try {
                        repo.pingLocation(loc.latitude, loc.longitude, loc.bearing.toDouble())
                    } catch (e: Exception) { /* silent — don't crash the service */ }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        startForeground(NOTIF_ID, buildNotification())
        startTracking()
    }

    @SuppressWarnings("MissingPermission")
    private fun startTracking() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L)
            .setMinUpdateDistanceMeters(30f)
            .setWaitForAccurateLocation(false)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, UltimateProApp.CH_LOCATION)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("UltimatePro")
            .setContentText("Location tracking active")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIF_ID = 1001
    }
}
