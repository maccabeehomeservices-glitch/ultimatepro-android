package com.ultimatepro.ui.memberships

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.Customer
import com.ultimatepro.domain.model.CustomerMembership
import com.ultimatepro.domain.model.MembershipDueSoon
import com.ultimatepro.domain.model.MembershipPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MembershipViewModel @Inject constructor(
    private val repo: CrmRepository
) : ViewModel() {

    // ── Plans ─────────────────────────────────────────────────────────────────
    private val _plans = MutableStateFlow<List<MembershipPlan>>(emptyList())
    val plans: StateFlow<List<MembershipPlan>> = _plans

    private val _plansLoading = MutableStateFlow(false)
    val plansLoading: StateFlow<Boolean> = _plansLoading

    private val _plansError = MutableStateFlow<String?>(null)
    val plansError: StateFlow<String?> = _plansError

    fun loadPlans() = viewModelScope.launch {
        _plansLoading.value = true
        when (val r = repo.getMembershipPlans()) {
            is Result.Success -> { _plans.value = r.data; _plansError.value = null }
            is Result.Error   -> _plansError.value = r.message
        }
        _plansLoading.value = false
    }

    fun createPlan(name: String, description: String?, frequency: String, price: Double, onDone: (Boolean) -> Unit) =
        viewModelScope.launch {
            when (repo.createMembershipPlan(name, description, frequency, price)) {
                is Result.Success -> { loadPlans(); onDone(true) }
                is Result.Error   -> onDone(false)
            }
        }

    fun updatePlan(id: String, name: String, description: String?, frequency: String, price: Double, isActive: Boolean, onDone: (Boolean) -> Unit) =
        viewModelScope.launch {
            when (repo.updateMembershipPlan(id, name, description, frequency, price, isActive)) {
                is Result.Success -> { loadPlans(); onDone(true) }
                is Result.Error   -> onDone(false)
            }
        }

    fun deletePlan(id: String) = viewModelScope.launch {
        repo.deleteMembershipPlan(id)
        loadPlans()
    }

    // ── Customer Memberships ──────────────────────────────────────────────────
    private val _memberships = MutableStateFlow<List<CustomerMembership>>(emptyList())
    val memberships: StateFlow<List<CustomerMembership>> = _memberships

    private val _membershipsLoading = MutableStateFlow(false)
    val membershipsLoading: StateFlow<Boolean> = _membershipsLoading

    fun loadCustomerMemberships(customerId: String) = viewModelScope.launch {
        _membershipsLoading.value = true
        when (val r = repo.getCustomerMemberships(customerId)) {
            is Result.Success -> _memberships.value = r.data
            is Result.Error   -> { /* no-op */ }
        }
        _membershipsLoading.value = false
    }

    fun addMembership(
        customerId: String,
        planId: String,
        planName: String,
        startDate: String? = null,
        endDate: String? = null,
        renewalDate: String? = null,
        notes: String? = null,
        onDone: (Boolean, Int) -> Unit
    ) = viewModelScope.launch {
        android.util.Log.d("MembershipVM", "addMembership: customerId=$customerId planId=$planId startDate=$startDate")
        when (val result = repo.createCustomerMembership(customerId, planId, startDate, endDate, renewalDate, notes)) {
            is Result.Success -> {
                android.util.Log.d("MembershipVM", "createCustomerMembership success id=${result.data.id}")
                loadCustomerMemberships(customerId)
                var jobsCreated = 0
                val jobTitle = "$planName — Membership Service"
                val jobNotes = "Auto-created from membership plan assignment"
                val j1 = repo.createJob(buildMap {
                    put("customer_id", customerId)
                    put("title", jobTitle)
                    put("status", "scheduled")
                    put("notes", jobNotes)
                    if (startDate != null) put("scheduled_start", startDate)
                })
                if (j1 is Result.Success) jobsCreated++
                if (!renewalDate.isNullOrBlank()) {
                    val j2 = repo.createJob(buildMap {
                        put("customer_id", customerId)
                        put("title", "$jobTitle (Renewal)")
                        put("status", "scheduled")
                        put("notes", "$jobNotes (renewal)")
                        put("scheduled_start", renewalDate)
                    })
                    if (j2 is Result.Success) jobsCreated++
                }
                onDone(true, jobsCreated)
            }
            is Result.Error -> {
                android.util.Log.e("MembershipVM", "createCustomerMembership failed: ${result.message} (code ${result.code})")
                onDone(false, 0)
            }
        }
    }

    fun updateMembershipStatus(id: String, customerId: String, status: String) = viewModelScope.launch {
        repo.updateCustomerMembership(id, status = status)
        loadCustomerMemberships(customerId)
    }

    fun deleteMembership(id: String, customerId: String) = viewModelScope.launch {
        repo.deleteCustomerMembership(id)
        loadCustomerMemberships(customerId)
    }

    fun createNextJob(membershipId: String, customerId: String, onDone: (Boolean) -> Unit) =
        viewModelScope.launch {
            when (repo.createNextMembershipJob(membershipId)) {
                is Result.Success -> { loadCustomerMemberships(customerId); onDone(true) }
                is Result.Error   -> onDone(false)
            }
        }

    // ── Customer Search ───────────────────────────────────────────────────────
    private val _customerSearch = MutableStateFlow<List<Customer>>(emptyList())
    val customerSearch: StateFlow<List<Customer>> = _customerSearch

    fun searchCustomers(query: String) = viewModelScope.launch {
        if (query.isBlank()) { _customerSearch.value = emptyList(); return@launch }
        when (val r = repo.getCustomers(search = query)) {
            is Result.Success -> _customerSearch.value = r.data.customers
            is Result.Error   -> { /* no-op */ }
        }
    }

    // ── Due Soon ──────────────────────────────────────────────────────────────
    private val _dueSoon = MutableStateFlow<List<MembershipDueSoon>>(emptyList())
    val dueSoon: StateFlow<List<MembershipDueSoon>> = _dueSoon

    fun loadDueSoon() = viewModelScope.launch {
        when (val r = repo.getMembershipsDueSoon()) {
            is Result.Success -> _dueSoon.value = r.data
            is Result.Error   -> { /* no-op */ }
        }
    }
}
