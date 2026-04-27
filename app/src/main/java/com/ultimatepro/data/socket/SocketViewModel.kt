package com.ultimatepro.data.socket

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin ViewModel that connects the Socket.IO singleton and exposes
 * the documentSigned flow to composables.
 *
 * Scoped to the NavBackStackEntry — connect() is idempotent so multiple
 * instances calling it is safe.
 */
@HiltViewModel
class SocketViewModel @Inject constructor(
    private val socketManager: SocketManager
) : ViewModel() {

    val documentSigned = socketManager.documentSigned

    init {
        socketManager.connect()
    }
}
