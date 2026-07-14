package com.ultimatepro.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.ReviewPlatform
import com.ultimatepro.ui.common.AppButton
import com.ultimatepro.ui.common.AppColors
import com.ultimatepro.ui.common.AppSwitch
import com.ultimatepro.ui.common.CRMCard
import com.ultimatepro.ui.common.SectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ───────────────────────────────────────────────────────────────
@HiltViewModel
class ReviewPlatformViewModel @Inject constructor(private val repo: CrmRepository) : ViewModel() {
    private val _platforms = MutableStateFlow<List<ReviewPlatform>>(emptyList())
    val platforms = _platforms.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _msg = MutableStateFlow<String?>(null)
    val message = _msg.asStateFlow()

    init { loadPlatforms() }

    fun loadPlatforms() {
        viewModelScope.launch {
            _loading.value = true
            when (val r = repo.getReviewPlatforms()) {
                is Result.Success -> _platforms.value = r.data
                is Result.Error   -> _msg.value = r.message
            }
            _loading.value = false
        }
    }

    fun addPlatform(name: String, url: String, isDefault: Boolean, isActive: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = repo.createReviewPlatform(name, url, isDefault, isActive)) {
                is Result.Success -> { loadPlatforms(); onDone() }
                is Result.Error   -> _msg.value = r.message
            }
        }
    }

    fun updatePlatform(id: String, name: String?, url: String?, isDefault: Boolean?, isActive: Boolean?, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = repo.updateReviewPlatform(id, name, url, isDefault, isActive)) {
                is Result.Success -> { loadPlatforms(); onDone() }
                is Result.Error   -> _msg.value = r.message
            }
        }
    }

    fun deletePlatform(id: String) {
        viewModelScope.launch {
            when (val r = repo.deleteReviewPlatform(id)) {
                is Result.Success -> loadPlatforms()
                is Result.Error   -> _msg.value = r.message
            }
        }
    }

    fun clearMsg() { _msg.value = null }
}

// ─── Screen ──────────────────────────────────────────────────────────────────
private data class QuickAdd(val name: String, val urlHint: String)

private val quickAddPlatforms = listOf(
    QuickAdd("Google",     "https://g.page/r/YOUR_PLACE_ID/review"),
    QuickAdd("Thumbtack",  "https://www.thumbtack.com/biz/YOUR_BIZ/reviews"),
    QuickAdd("Facebook",   "https://www.facebook.com/YOUR_PAGE/reviews"),
    QuickAdd("Yelp",       "https://www.yelp.com/biz/YOUR_BIZ")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewPlatformsScreen(
    onBack: () -> Unit,
    vm: ReviewPlatformViewModel = hiltViewModel()
) {
    val platforms by vm.platforms.collectAsState()
    val loading   by vm.loading.collectAsState()
    val msg       by vm.message.collectAsState()
    val snack     = remember { SnackbarHostState() }

    var showSheet     by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf<ReviewPlatform?>(null) }

    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.clearMsg() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Review Platforms", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingTarget = null; showSheet = true }) {
                Icon(Icons.Default.Add, null)
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Quick-add buttons
            item {
                SectionLabel("QUICK ADD")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickAddPlatforms.take(2).forEach { qa ->
                        AppButton(
                            onClick = { editingTarget = ReviewPlatform(id = "", name = qa.name, url = qa.urlHint); showSheet = true },
                            label = qa.name,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickAddPlatforms.drop(2).forEach { qa ->
                        AppButton(
                            onClick = { editingTarget = ReviewPlatform(id = "", name = qa.name, url = qa.urlHint); showSheet = true },
                            label = qa.name,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Platforms list
            item { SectionLabel("YOUR PLATFORMS") }

            if (loading && platforms.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (platforms.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No platforms yet. Add one to include review links in receipts.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                items(platforms, key = { it.id }) { platform ->
                    PlatformCard(
                        platform  = platform,
                        onEdit    = { editingTarget = platform; showSheet = true },
                        onDelete  = { vm.deletePlatform(platform.id) },
                        onToggle  = { vm.updatePlatform(platform.id, null, null, null, !platform.isActive) {} }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showSheet) {
        AddEditPlatformSheet(
            initial  = editingTarget,
            onDismiss = { showSheet = false },
            onSave   = { name, url, isDefault, isActive ->
                val target = editingTarget
                if (target == null || target.id.isEmpty()) {
                    vm.addPlatform(name, url, isDefault, isActive) { showSheet = false }
                } else {
                    vm.updatePlatform(target.id, name, url, isDefault, isActive) { showSheet = false }
                }
            }
        )
    }
}

@Composable
private fun PlatformCard(
    platform: ReviewPlatform,
    onEdit:   () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    CRMCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(platform.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    if (platform.isDefault) {
                        Surface(shape = RoundedCornerShape(4.dp), color = AppColors.Blue.copy(alpha = 0.15f)) {
                            Text("Default", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = AppColors.Blue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(platform.url, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
            AppSwitch(
                checked  = platform.isActive,
                onCheckedChange = { onToggle() },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = AppColors.Red)
            }
        }
    }
}

@Composable
private fun AddEditPlatformSheet(
    initial:  ReviewPlatform?,
    onDismiss: () -> Unit,
    onSave:   (name: String, url: String, isDefault: Boolean, isActive: Boolean) -> Unit
) {
    val isEdit = initial != null && initial.id.isNotEmpty()
    var name      by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var url       by remember(initial) { mutableStateOf(TextFieldValue(initial?.url ?: "")) }
    var isDefault by remember(initial) { mutableStateOf(initial?.isDefault ?: false) }
    var isActive  by remember(initial) { mutableStateOf(initial?.isActive ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Platform" else "Add Platform", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Platform Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Review URL") },
                    singleLine = true, maxLines = 1, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Set as default platform", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && url.text.isNotBlank()) onSave(name.trim(), url.text.trim(), isDefault, isActive)
                },
                enabled = name.isNotBlank() && url.text.isNotBlank()
            ) {
                Text(if (isEdit) "Save Changes" else "Add Platform", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
