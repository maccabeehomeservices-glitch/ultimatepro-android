package com.ultimatepro.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P2.2 — global "session expired" signal. The network layer trips this when a request
 * 401s and a token refresh can't recover it (e.g. after a staging reseed invalidates the
 * stored JWT). AuthViewModel observes it and flips loggedIn=false, which routes the app to
 * the login screen — instead of the old dead "Retry" screen.
 *
 * A StateFlow (not SharedFlow) so a collector that subscribes AFTER the event still sees
 * the current value — there is no subscribe-race with the coroutine that fires it.
 */
@Singleton
class SessionManager @Inject constructor() {
    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    /** Called from the network layer when the session is unrecoverable. Non-suspend. */
    fun expire() { _expired.value = true }

    /** Consume the event once handled (and on a fresh login) so it doesn't re-fire. */
    fun reset() { _expired.value = false }
}
