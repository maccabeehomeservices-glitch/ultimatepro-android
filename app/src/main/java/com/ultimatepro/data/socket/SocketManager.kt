package com.ultimatepro.data.socket

import com.ultimatepro.BuildConfig
import com.ultimatepro.data.local.TokenStore
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class DocumentSignedEvent(
    val type:   String,   // "estimate" or "invoice"
    val id:     String,
    val status: String,
    val number: String    // estimate_number or invoice_number
)

@Singleton
class SocketManager @Inject constructor(
    private val tokenStore: TokenStore
) {
    private var socket: Socket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _documentSigned = MutableSharedFlow<DocumentSignedEvent>(extraBufferCapacity = 16)
    val documentSigned: SharedFlow<DocumentSignedEvent> = _documentSigned.asSharedFlow()

    /** Idempotent — safe to call multiple times; only connects if not already connected. */
    fun connect() {
        if (socket?.connected() == true) return
        scope.launch {
            try {
                val token     = tokenStore.getAccessToken()?.takeIf { it.isNotBlank() } ?: return@launch
                val companyId = tokenStore.getCompanyId()?.takeIf  { it.isNotBlank() } ?: return@launch

                val opts = IO.Options.builder()
                    .setAuth(mapOf("token" to token))
                    .build()

                socket = IO.socket(BuildConfig.SOCKET_URL, opts).apply {
                    on(Socket.EVENT_CONNECT) {
                        emit("join:company", companyId)
                    }
                    on("document:signed") { args ->
                        val json = args.getOrNull(0) as? JSONObject ?: return@on
                        _documentSigned.tryEmit(
                            DocumentSignedEvent(
                                type   = json.optString("type"),
                                id     = json.optString("id"),
                                status = json.optString("status"),
                                number = json.optString("estimate_number")
                                           .takeIf { it.isNotEmpty() }
                                           ?: json.optString("invoice_number")
                            )
                        )
                    }
                    connect()
                }
            } catch (_: Exception) {
                // Socket is non-critical — fail silently
            }
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
