package com.ultimatepro.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "ultimatecrm_session")

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    companion object {
        val KEY_ACCESS  = stringPreferencesKey("access_token")
        val KEY_REFRESH = stringPreferencesKey("refresh_token")
        val KEY_USER    = stringPreferencesKey("user_json")
        val KEY_COMPANY = stringPreferencesKey("company_json")
        val KEY_ROLE    = stringPreferencesKey("user_role")
        val KEY_COMPANY_ID = stringPreferencesKey("company_id")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_PERMISSIONS = stringPreferencesKey("permissions_resolved")
    }

    suspend fun save(
        accessToken: String,
        refreshToken: String,
        userJson: String,
        companyJson: String,
        role: String,
        companyId: String,
        userId: String,
        permissionsJson: String = "{}"
    ) {
        ctx.dataStore.edit { p ->
            p[KEY_ACCESS]      = accessToken
            p[KEY_REFRESH]     = refreshToken
            p[KEY_USER]        = userJson
            p[KEY_COMPANY]     = companyJson
            p[KEY_ROLE]        = role
            p[KEY_COMPANY_ID]  = companyId
            p[KEY_USER_ID]     = userId
            p[KEY_PERMISSIONS] = permissionsJson
        }
    }

    suspend fun getAccessToken()  = ctx.dataStore.data.map { it[KEY_ACCESS]  }.first()
    suspend fun getRefreshToken() = ctx.dataStore.data.map { it[KEY_REFRESH] }.first()
    suspend fun getUserJson()     = ctx.dataStore.data.map { it[KEY_USER]    }.first()
    suspend fun getCompanyJson()  = ctx.dataStore.data.map { it[KEY_COMPANY] }.first()
    suspend fun getRole()         = ctx.dataStore.data.map { it[KEY_ROLE]    }.first()
    suspend fun getCompanyId()    = ctx.dataStore.data.map { it[KEY_COMPANY_ID] }.first()
    suspend fun getUserId()       = ctx.dataStore.data.map { it[KEY_USER_ID] }.first()
    suspend fun getPermissionsJson() = ctx.dataStore.data.map { it[KEY_PERMISSIONS] }.first()
    suspend fun isLoggedIn()      = getAccessToken()?.isNotBlank() == true

    suspend fun clear() {
        ctx.dataStore.edit { it.clear() }
    }
}
