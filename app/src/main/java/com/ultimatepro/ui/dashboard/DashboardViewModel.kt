package com.ultimatepro.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.*
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.*
import com.ultimatepro.ui.maps.JobMapPin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val loading: Boolean = true,
    val report: DashboardResponse? = null,
    val user: User? = null,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init { load() }

    suspend fun getNotificationUnreadCount(): Int {
        return when (val r = repo.getNotifications()) {
            is Result.Success -> r.data.unread_count
            else -> 0
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val user         = repo.getCurrentUser()
            val reportResult = repo.getDashboardReport()

            _state.update {
                it.copy(
                    loading = false,
                    user    = user,
                    report  = (reportResult as? Result.Success)?.data,
                    error   = (reportResult as? Result.Error)?.message
                )
            }
        }
    }
}
