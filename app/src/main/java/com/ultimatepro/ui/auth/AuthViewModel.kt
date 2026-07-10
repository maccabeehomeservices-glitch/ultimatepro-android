package com.ultimatepro.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.data.session.SessionManager
import com.ultimatepro.domain.model.RegisterRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthState(
    val loading: Boolean = false,
    val error: String?   = null,
    val done: Boolean    = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: CrmRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state   = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    // Resolved per-section permission levels (Phase 3a). Used to hide controls/nav.
    private val _permissions = MutableStateFlow<Map<String, String>>(emptyMap())
    val permissions: StateFlow<Map<String, String>> = _permissions.asStateFlow()

    // Stored role — used for the owner/admin UI backstop (canUi). Reliable even on a
    // stale session (saved since before 3a).
    private val _role = MutableStateFlow<String?>(null)
    val role: StateFlow<String?> = _role.asStateFlow()

    init {
        viewModelScope.launch { _loggedIn.value = repo.isLoggedIn() }
        // P2.2: when the network layer signals an unrecoverable 401 (e.g. a reseed-invalidated
        // token whose refresh also fails), drop to logged-out → App() routes to the login screen.
        viewModelScope.launch {
            sessionManager.expired.collect { expired ->
                if (expired) { _loggedIn.value = false; sessionManager.reset() }
            }
        }
        viewModelScope.launch {
            _role.value = repo.getStoredRole()
            // Instant paint from the stored value, then refresh from /me (mirrors web,
            // which re-fetches /me on mount). Fixes stale sessions whose stored perms
            // are empty — without forcing a re-login. Keep stored value if /me fails.
            _permissions.value = repo.getStoredPermissions()
            val fresh = repo.getMyPermissions()
            if (fresh.isNotEmpty()) _permissions.value = fresh
        }
    }

    // P3.7: re-fetch resolved permissions (+ role) from /me. Called on app foreground
    // (ON_RESUME) so an owner's permission-grid change reaches a logged-in user without
    // re-login — "next app foreground at latest". Server enforcement is always live; this
    // keeps the UI-gating cache current too. Keeps stored values if /me fails (offline).
    fun refreshPermissions() = viewModelScope.launch {
        if (!repo.isLoggedIn()) return@launch
        val fresh = repo.getMyPermissions()
        if (fresh.isNotEmpty()) _permissions.value = fresh
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        _state.value = AuthState(loading = true)
        when (val r = repo.login(email, password)) {
            is Result.Success -> {
                _loggedIn.value = true
                _state.value = AuthState(done = true)
                registerFcmTokenIfAvailable()
            }
            is Result.Error   -> _state.value = AuthState(error = r.message)
        }
    }

    fun register(companyName: String, firstName: String, lastName: String, email: String, phone: String, password: String, inviteCode: String = "") = viewModelScope.launch {
        _state.value = AuthState(loading = true)
        val req = RegisterRequest(companyName, firstName, lastName, email, phone, password,
            invite_code = inviteCode.ifBlank { null })
        when (val r = repo.register(req)) {
            is Result.Success -> {
                _loggedIn.value = true
                _state.value = AuthState(done = true)
                registerFcmTokenIfAvailable()
            }
            is Result.Error   -> _state.value = AuthState(error = r.message)
        }
    }

    fun logout() = viewModelScope.launch {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            repo.unregisterFcmToken(token)
        } catch (e: Exception) { /* ignore — token cleanup is best-effort */ }
        repo.logout()
        _loggedIn.value = false
    }

    private fun registerFcmTokenIfAvailable() = viewModelScope.launch {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            repo.registerFcmToken(token)
        } catch (e: Exception) { /* ignore — token registration is best-effort */ }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
