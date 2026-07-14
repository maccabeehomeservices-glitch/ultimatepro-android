package com.ultimatepro.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.AppNotification
import com.ultimatepro.ui.common.AppButton
import com.ultimatepro.ui.common.ShineHairline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class NotifUiState(
    val notifications: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0,
    val loading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {

    private val _state = MutableStateFlow(NotifUiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = repo.getNotifications()) {
                is Result.Success -> _state.update {
                    it.copy(
                        notifications = r.data.notifications,
                        unreadCount   = r.data.unread_count,
                        loading       = false
                    )
                }
                is Result.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            repo.markRead(id)
            _state.update { s ->
                val updated = s.notifications.map { if (it.id == id) it.copy(read = true) else it }
                val newUnread = updated.count { !it.read }
                s.copy(notifications = updated, unreadCount = newUnread)
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            repo.markAllRead()
            _state.update { s ->
                s.copy(
                    notifications = s.notifications.map { it.copy(read = true) },
                    unreadCount   = 0
                )
            }
        }
    }
}

// ── Helper ────────────────────────────────────────────────────────────────────

private fun timeAgo(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = sdf.parse(dateStr.substringBefore(".").substringBefore("Z")) ?: return ""
        val diff = System.currentTimeMillis() - date.time
        val mins = diff / 60_000
        when {
            mins < 1  -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 1440 -> "${mins / 60}h ago"
            mins < 10080 -> "${mins / 1440}d ago"
            else -> SimpleDateFormat("MMM d", Locale.US).format(date)
        }
    } catch (_: Exception) { "" }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun notifIcon(n: AppNotification): androidx.compose.ui.graphics.vector.ImageVector {
    val t = n.entity_type ?: n.type ?: ""
    return when {
        t.contains("job")                            -> Icons.Default.Work
        t.contains("invoice") || t.contains("pay")  -> Icons.Default.Receipt
        t.contains("estimate")                       -> Icons.Default.Description
        t.contains("booking")                        -> Icons.Default.DateRange
        else                                         -> Icons.Default.Notifications
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack:      () -> Unit,
    onJob:       (String) -> Unit = {},
    onInvoice:   (String) -> Unit = {},
    onEstimate:  (String) -> Unit = {},
    onCustomer:  (String) -> Unit = {},
    vm: NotificationsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notifications", fontWeight = FontWeight.SemiBold)
                        if (state.unreadCount > 0) {
                            Badge { Text(if (state.unreadCount > 99) "99+" else state.unreadCount.toString()) }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    if (state.unreadCount > 0) {
                        TextButton(onClick = { vm.markAllRead() }) {
                            Text("Mark all read", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )
            ShineHairline()
            }
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    AppButton(onClick = { vm.load() }, label = "Retry")
                }
            }
            state.notifications.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Notifications, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Text("No notifications", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("You're all caught up!", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.notifications, key = { it.id }) { n ->
                    NotificationCard(
                        notification = n,
                        onClick = {
                            if (!n.read) vm.markRead(n.id)
                            try {
                                when (n.entity_type) {
                                    "job"      -> n.entity_id?.let(onJob)
                                    "invoice"  -> n.entity_id?.let(onInvoice)
                                    "estimate" -> n.entity_id?.let(onEstimate)
                                    "customer" -> n.entity_id?.let(onCustomer)
                                }
                            } catch (_: Exception) {
                                // Navigation failed — stay on notifications page
                                vm.load()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotification,
    onClick: () -> Unit
) {
    val hasLink = notification.entity_id != null
    val bgColor = if (notification.read)
        MaterialTheme.colorScheme.surface
    else
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasLink) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = bgColor,
        tonalElevation = if (notification.read) 1.dp else 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (notification.read)
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    notifIcon(notification),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (notification.read)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        notification.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (notification.read) FontWeight.Normal else FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        timeAgo(notification.created_at),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                if (!notification.body.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        notification.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                if (!notification.read) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}
