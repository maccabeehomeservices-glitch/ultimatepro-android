package com.ultimatepro.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
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
class AuthViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {

    private val _state   = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    // Resolved per-section permission levels (Phase 3a). Loaded from the store at
    // session start; used to hide controls/nav a user can't use. No network call.
    private val _permissions = MutableStateFlow<Map<String, String>>(emptyMap())
    val permissions: StateFlow<Map<String, String>> = _permissions.asStateFlow()

    init {
        viewModelScope.launch { _loggedIn.value = repo.isLoggedIn() }
        viewModelScope.launch { _permissions.value = repo.getStoredPermissions() }
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
