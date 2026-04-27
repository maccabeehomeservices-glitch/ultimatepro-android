package com.ultimatepro.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.TimesheetReport
import com.ultimatepro.domain.model.TimesheetStatus
import com.ultimatepro.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimesheetViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {

    private val _status  = MutableStateFlow<TimesheetStatus?>(null)
    val timesheetStatus  = _status.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val isLoading        = _loading.asStateFlow()

    private val _msg     = MutableStateFlow<String?>(null)
    val message          = _msg.asStateFlow()

    private val _report        = MutableStateFlow<TimesheetReport?>(null)
    val report                 = _report.asStateFlow()

    private val _reportLoading = MutableStateFlow(false)
    val reportLoading          = _reportLoading.asStateFlow()

    private val _techs   = MutableStateFlow<List<User>>(emptyList())
    val techs            = _techs.asStateFlow()

    init { loadStatus() }

    fun loadStatus() {
        viewModelScope.launch {
            when (val r = repo.getTimesheetStatus()) {
                is Result.Success -> _status.value = r.data
                is Result.Error   -> {}   // not clocked in or no timesheets yet
            }
        }
    }

    fun clockIn() {
        viewModelScope.launch {
            _loading.value = true
            when (val r = repo.clockIn()) {
                is Result.Success -> { loadStatus(); _msg.value = "Clocked in!" }
                is Result.Error   -> _msg.value = r.message
            }
            _loading.value = false
        }
    }

    fun clockOut() {
        viewModelScope.launch {
            _loading.value = true
            when (val r = repo.clockOut()) {
                is Result.Success -> {
                    val mins = r.data.totalMinutes ?: 0
                    _msg.value = "Clocked out! Total: ${mins / 60}h ${mins % 60}m"
                    loadStatus()
                }
                is Result.Error -> _msg.value = r.message
            }
            _loading.value = false
        }
    }

    fun loadReport(startDate: String, endDate: String, userId: String? = null) {
        viewModelScope.launch {
            _reportLoading.value = true
            when (val r = repo.getTimesheetReport(startDate, endDate, userId)) {
                is Result.Success -> _report.value = r.data
                is Result.Error   -> _msg.value = r.message
            }
            _reportLoading.value = false
        }
    }

    fun loadTechs() {
        viewModelScope.launch {
            when (val r = repo.getTechnicians()) {
                is Result.Success -> _techs.value = r.data
                is Result.Error   -> {}
            }
        }
    }

    fun clearMsg() { _msg.value = null }
}
