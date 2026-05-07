package com.ultimatepro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.android.libraries.places.api.Places
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class UltimateProApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, "AIzaSyDtSGWBuiTFR5BbomG8ZFNYeiwUszkJiNQ")
        }
        // Crashlytics auto-initializes via the Gradle plugin; explicit collection
        // toggle ensures release builds report regardless of build-time defaults,
        // and custom keys make individual reports identifiable in the console.
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(true)
            setCustomKey("app_version",     BuildConfig.VERSION_NAME)
            setCustomKey("version_code",    BuildConfig.VERSION_CODE)
            setCustomKey("build_timestamp", BuildConfig.BUILD_TIMESTAMP)
        }
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)

            mgr.createNotificationChannel(NotificationChannel(
                CH_CALLS, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Real-time caller ID popups and call alerts" })

            mgr.createNotificationChannel(NotificationChannel(
                CH_GENERAL, "Job Alerts", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Job assignments, status updates, reminders" })

            mgr.createNotificationChannel(NotificationChannel(
                CH_LOCATION, "GPS Tracking", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Background location tracking for dispatch" })
        }
    }

    companion object {
        const val CH_CALLS    = "calls"
        const val CH_GENERAL  = "general"
        const val CH_LOCATION = "location"
    }
}
