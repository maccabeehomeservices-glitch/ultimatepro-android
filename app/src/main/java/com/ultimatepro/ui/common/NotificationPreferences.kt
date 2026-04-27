package com.ultimatepro.ui.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notifDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_prefs")

@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val KEY_NEW_JOBS     = booleanPreferencesKey("notif_new_jobs")
    private val KEY_STATUS       = booleanPreferencesKey("notif_status_updates")
    private val KEY_PARTNER_JOBS = booleanPreferencesKey("notif_partner_jobs")
    private val KEY_BOOKINGS     = booleanPreferencesKey("notif_new_bookings")
    private val KEY_ESTIMATES    = booleanPreferencesKey("notif_estimate_signed")

    val newJobs:     Flow<Boolean> = context.notifDataStore.data.map { it[KEY_NEW_JOBS]     ?: true }
    val statusUpdates: Flow<Boolean> = context.notifDataStore.data.map { it[KEY_STATUS]       ?: true }
    val partnerJobs: Flow<Boolean> = context.notifDataStore.data.map { it[KEY_PARTNER_JOBS] ?: true }
    val newBookings: Flow<Boolean> = context.notifDataStore.data.map { it[KEY_BOOKINGS]     ?: true }
    val estimateSigned: Flow<Boolean> = context.notifDataStore.data.map { it[KEY_ESTIMATES]    ?: true }

    suspend fun setNewJobs(on: Boolean)        { context.notifDataStore.edit { it[KEY_NEW_JOBS]     = on } }
    suspend fun setStatusUpdates(on: Boolean)  { context.notifDataStore.edit { it[KEY_STATUS]       = on } }
    suspend fun setPartnerJobs(on: Boolean)    { context.notifDataStore.edit { it[KEY_PARTNER_JOBS] = on } }
    suspend fun setNewBookings(on: Boolean)    { context.notifDataStore.edit { it[KEY_BOOKINGS]     = on } }
    suspend fun setEstimateSigned(on: Boolean) { context.notifDataStore.edit { it[KEY_ESTIMATES]    = on } }

    fun isEnabled(type: String, prefs: Preferences): Boolean = when (type) {
        "new_job"         -> prefs[KEY_NEW_JOBS]     ?: true
        "job_status"      -> prefs[KEY_STATUS]       ?: true
        "partner_job",
        "partner_confirm" -> prefs[KEY_PARTNER_JOBS] ?: true
        "new_booking"     -> prefs[KEY_BOOKINGS]     ?: true
        "estimate_signed" -> prefs[KEY_ESTIMATES]    ?: true
        else              -> true
    }
}
