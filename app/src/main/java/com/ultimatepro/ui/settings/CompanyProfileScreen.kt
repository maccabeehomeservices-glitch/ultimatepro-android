package com.ultimatepro.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.Company
import com.ultimatepro.domain.model.canUi
import com.ultimatepro.ui.common.AppButton
import com.ultimatepro.ui.common.AppColors
import com.ultimatepro.ui.common.ShineHairline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ── ViewModel ──────────────────────────────────────────────────────────────

data class CompanyProfileState(
    val loading:       Boolean  = true,
    val saving:        Boolean  = false,
    val uploading:     Boolean  = false,
    val name:          String   = "",
    val phone:         String   = "",
    val email:         String   = "",
    val website:       String   = "",
    val address:       String   = "",
    val city:          String   = "",
    val state:         String   = "",
    val zip:           String   = "",
    val tagline:       String   = "",
    val defaultTerms:  String   = "",
    val logoUrl:       String   = "",
    val ucmId:         String   = "",
    val snack:         String?  = null,
    val snackError:    Boolean  = false,
    // ── P3.10 branded email alias (<slug>@ultimatepro.pro) ──
    val canManageAlias: Boolean  = false,
    val aliasLoading:   Boolean  = true,
    val aliasCurrent:   String?  = null,    // claimed slug, null if none
    val aliasAddress:   String?  = null,    // current <slug>@ultimatepro.pro
    val aliasEditing:   Boolean  = false,   // field open (re-opened via Edit)
    val aliasInput:     String   = "",      // brand name being typed
    val aliasChecking:  Boolean  = false,   // debounced probe in flight
    val aliasAvailable: Boolean? = null,    // null = not yet checked
    val aliasReason:    String?  = null,    // reason code when unavailable
    val aliasBusy:      Boolean  = false,   // claim / remove in progress
    val showAliasRemove: Boolean = false,   // confirm-release dialog
    // ── P3.10 Tier 2: BYO sender email (verify your own address) ──
    val senderLoading:   Boolean = true,
    val senderStatus:    String  = "none",  // none | pending | verified
    val senderEmail:     String? = null,    // pending/verified address on file
    val senderInput:     String  = "",      // email being typed (status none)
    val senderBusy:      Boolean = false,   // verify / refresh / remove in progress
    val showSenderRemove: Boolean = false,  // confirm-remove dialog
)

const val ALIAS_DOMAIN_SUFFIX = "@ultimatepro.pro"

@HiltViewModel
class CompanyProfileViewModel @Inject constructor(
    private val repo: CrmRepository
) : ViewModel() {

    private val _s = MutableStateFlow(CompanyProfileState())
    val state = _s.asStateFlow()

    // P3.10: cancel/restart the availability probe on every keystroke (debounce).
    private var aliasCheckJob: Job? = null

    init { load(); loadAlias(); loadSenderEmail() }

    fun load() {
        viewModelScope.launch {
            _s.update { it.copy(loading = true) }
            when (val r = repo.getCompany()) {
                is Result.Success -> {
                    val c = r.data
                    _s.update { it.copy(
                        loading  = false,
                        name     = c.name,
                        phone    = c.phone ?: "",
                        email    = c.email ?: "",
                        website  = c.website ?: "",
                        address  = c.address ?: "",
                        city     = c.city ?: "",
                        state    = c.state ?: "",
                        zip      = c.zip ?: "",
                        tagline  = c.tagline ?: "",
                        defaultTerms = c.default_terms ?: "",
                        logoUrl  = c.logo_url ?: "",
                        ucmId    = c.ultimatecrm_id ?: "",
                    )}
                }
                is Result.Error -> _s.update { it.copy(loading = false, snack = "Failed to load", snackError = true) }
            }
        }
    }

    fun setField(field: String, value: String) {
        _s.update { s ->
            when (field) {
                "name"    -> s.copy(name = value)
                "phone"   -> s.copy(phone = value)
                "email"   -> s.copy(email = value)
                "website" -> s.copy(website = value)
                "address" -> s.copy(address = value)
                "city"    -> s.copy(city = value)
                "state"   -> s.copy(state = value)
                "zip"     -> s.copy(zip = value)
                "tagline" -> s.copy(tagline = value)
                "default_terms" -> s.copy(defaultTerms = value)
                else      -> s
            }
        }
    }

    fun uploadLogo(file: File) {
        viewModelScope.launch {
            _s.update { it.copy(uploading = true) }
            when (val r = repo.uploadCompanyLogo(file)) {
                is Result.Success -> _s.update { it.copy(uploading = false, logoUrl = r.data, snack = "Logo uploaded!", snackError = false) }
                is Result.Error   -> _s.update { it.copy(uploading = false, snack = "Upload failed: ${r.message}", snackError = true) }
            }
            file.delete()
        }
    }

    fun removeLogo() {
        viewModelScope.launch {
            _s.update { it.copy(logoUrl = "") }
            repo.updateCompany(mapOf("logo_url" to ""))
            _s.update { it.copy(snack = "Logo removed", snackError = false) }
        }
    }

    fun save() {
        viewModelScope.launch {
            val s = _s.value
            _s.update { it.copy(saving = true) }
            val data = mapOf(
                "name"     to s.name.trim(),
                "phone"    to s.phone.trim(),
                "email"    to s.email.trim(),
                "website"  to s.website.trim(),
                "address"  to s.address.trim(),
                "city"     to s.city.trim(),
                "state"    to s.state.trim(),
                "zip"      to s.zip.trim(),
                "tagline"  to s.tagline.trim(),
                "default_terms" to s.defaultTerms.trim(),
                "logo_url" to s.logoUrl,
            )
            when (repo.updateCompany(data)) {
                is Result.Success -> _s.update { it.copy(saving = false, snack = "Company profile saved!", snackError = false) }
                is Result.Error   -> _s.update { it.copy(saving = false, snack = "Failed to save", snackError = true) }
            }
        }
    }

    fun clearSnack() = _s.update { it.copy(snack = null) }

    // ── P3.10 branded email alias ───────────────────────────────────────────

    fun loadAlias() {
        viewModelScope.launch {
            _s.update { it.copy(aliasLoading = true) }
            // Same gate the settings nav uses for company/team management.
            val canManage = canUi(repo.getStoredRole(), repo.getStoredPermissions(), "team_settings", "full")
            when (val r = repo.getEmailAlias()) {
                is Result.Success -> _s.update { it.copy(
                    aliasLoading   = false,
                    canManageAlias = canManage,
                    aliasCurrent   = r.data.alias,
                    aliasAddress   = r.data.address,
                    aliasEditing   = false,
                    aliasInput     = "",
                    aliasAvailable = null,
                    aliasReason    = null,
                )}
                is Result.Error -> _s.update { it.copy(aliasLoading = false, canManageAlias = canManage) }
            }
        }
    }

    fun onAliasInput(value: String) {
        // Shared namespace is lowercase; normalize as they type so the preview + claim match.
        val v = value.lowercase().trim()
        _s.update { it.copy(aliasInput = v) }
        aliasCheckJob?.cancel()
        if (v.isBlank()) {
            _s.update { it.copy(aliasChecking = false, aliasAvailable = null, aliasReason = null) }
            return
        }
        aliasCheckJob = viewModelScope.launch {
            _s.update { it.copy(aliasChecking = true, aliasAvailable = null, aliasReason = null) }
            delay(400)
            when (val r = repo.checkEmailAlias(v)) {
                is Result.Success -> _s.update { it.copy(
                    aliasChecking  = false,
                    aliasAvailable = r.data.available,
                    aliasReason    = r.data.reason,
                )}
                is Result.Error -> _s.update { it.copy(aliasChecking = false, aliasAvailable = null, aliasReason = null) }
            }
        }
    }

    fun startEditAlias() = _s.update {
        // Pre-fill with the current slug so the owner can tweak it; availability re-checks on change.
        it.copy(aliasEditing = true, aliasInput = it.aliasCurrent ?: "", aliasChecking = false, aliasAvailable = null, aliasReason = null)
    }

    fun cancelEditAlias() {
        aliasCheckJob?.cancel()
        _s.update { it.copy(aliasEditing = false, aliasInput = "", aliasChecking = false, aliasAvailable = null, aliasReason = null) }
    }

    fun claimAlias() {
        val slug = _s.value.aliasInput.trim()
        if (slug.isBlank()) return
        aliasCheckJob?.cancel()
        viewModelScope.launch {
            _s.update { it.copy(aliasBusy = true, aliasChecking = false) }
            when (val r = repo.setEmailAlias(slug)) {
                is Result.Success -> _s.update { it.copy(
                    aliasBusy      = false,
                    aliasCurrent   = r.data.alias,
                    aliasAddress   = r.data.address,
                    aliasEditing   = false,
                    aliasInput     = "",
                    aliasAvailable = null,
                    aliasReason    = null,
                    snack          = "Branded email set!",
                    snackError     = false,
                )}
                is Result.Error -> _s.update { it.copy(aliasBusy = false, snack = r.message, snackError = true) }
            }
        }
    }

    fun removeAlias() {
        viewModelScope.launch {
            _s.update { it.copy(aliasBusy = true, showAliasRemove = false) }
            when (val r = repo.deleteEmailAlias()) {
                is Result.Success -> _s.update { it.copy(
                    aliasBusy      = false,
                    aliasCurrent   = null,
                    aliasAddress   = null,
                    aliasEditing   = false,
                    aliasInput     = "",
                    aliasAvailable = null,
                    aliasReason    = null,
                    snack          = "Branded email released",
                    snackError     = false,
                )}
                is Result.Error -> _s.update { it.copy(aliasBusy = false, snack = r.message, snackError = true) }
            }
        }
    }

    fun setAliasRemoveDialog(show: Boolean) = _s.update { it.copy(showAliasRemove = show) }

    // ── P3.10 Tier 2: BYO sender email ──────────────────────────────────────

    fun loadSenderEmail() {
        viewModelScope.launch {
            _s.update { it.copy(senderLoading = true) }
            when (val r = repo.getSenderEmail()) {
                is Result.Success -> _s.update { it.copy(
                    senderLoading = false,
                    senderStatus  = r.data.status ?: "none",
                    senderEmail   = r.data.email,
                    senderInput   = "",
                )}
                is Result.Error -> _s.update { it.copy(senderLoading = false) }
            }
        }
    }

    fun onSenderInput(value: String) = _s.update { it.copy(senderInput = value.trim()) }

    fun verifySenderEmail() {
        val email = _s.value.senderInput.trim()
        if (email.isBlank()) return
        viewModelScope.launch {
            _s.update { it.copy(senderBusy = true) }
            when (val r = repo.setSenderEmail(email)) {
                is Result.Success -> _s.update { it.copy(
                    senderBusy   = false,
                    senderStatus = r.data.status ?: "pending",
                    senderEmail  = r.data.email ?: email,
                    snack        = r.data.message ?: "Verification email sent — check your inbox.",
                    snackError   = false,
                )}
                is Result.Error -> _s.update { it.copy(
                    senderBusy = false,
                    snack      = senderErrorText(r.code, r.reason, r.message),
                    snackError = true,
                )}
            }
        }
    }

    fun refreshSenderStatus() {
        viewModelScope.launch {
            _s.update { it.copy(senderBusy = true) }
            when (val r = repo.getSenderEmailStatus()) {
                is Result.Success -> _s.update { it.copy(
                    senderBusy   = false,
                    senderStatus = r.data.status ?: "none",
                    senderEmail  = r.data.email,
                    snack        = if (r.data.status == "verified")
                        "Verified! Emails now send from ${r.data.email ?: "your address"}."
                    else "Not confirmed yet — click the link in the email, then Refresh.",
                    snackError   = false,
                )}
                is Result.Error -> _s.update { it.copy(senderBusy = false, snack = r.message, snackError = true) }
            }
        }
    }

    fun removeSenderEmail() {
        viewModelScope.launch {
            _s.update { it.copy(senderBusy = true, showSenderRemove = false) }
            when (val r = repo.deleteSenderEmail()) {
                is Result.Success -> _s.update { it.copy(
                    senderBusy   = false,
                    senderStatus = "none",
                    senderEmail  = null,
                    senderInput  = "",
                    snack        = "Sender email removed",
                    snackError   = false,
                )}
                is Result.Error -> _s.update { it.copy(senderBusy = false, snack = r.message, snackError = true) }
            }
        }
    }

    fun setSenderRemoveDialog(show: Boolean) = _s.update { it.copy(showSenderRemove = show) }
}

// ── Screen ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyProfileScreen(
    onBack: () -> Unit,
    vm: CompanyProfileViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(it) ?: return@launch
                    val tempFile = File.createTempFile("logo_", ".jpg", context.cacheDir)
                    tempFile.outputStream().use { out -> inputStream.copyTo(out) }
                    vm.uploadLogo(tempFile)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    LaunchedEffect(s.snack) {
        if (s.snack != null) {
            kotlinx.coroutines.delay(3000)
            vm.clearSnack()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Company Profile", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
                ShineHairline()
            }
        },
        snackbarHost = {
            s.snack?.let { msg ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (s.snackError) MaterialTheme.colorScheme.error else AppColors.Green
                        )
                    ) {
                        Text(
                            msg,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (s.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── UCM ID ──────────────────────────────────────────────────
            if (s.ucmId.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "ULTIMATEPRO ID",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.Blue,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.ucmId,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = AppColors.Blue
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(s.ucmId))
                                    vm.clearSnack()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(
                            "Share with contractors to connect on the network",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.Blue
                        )
                    }
                }
            }

            // ── LOGO ────────────────────────────────────────────────────
            SectionLabel("COMPANY LOGO")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (s.logoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = s.logoUrl,
                            contentDescription = "Company logo",
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        onClick = { photoLauncher.launch("image/*") },
                        label = if (s.uploading) "Uploading..." else "Upload Logo",
                        enabled = !s.uploading
                    )

                    if (s.logoUrl.isNotEmpty()) {
                        AppButton(
                            onClick = { vm.removeLogo() },
                            label = "Remove Logo",
                            labelColor = AppColors.Red
                        )
                    }
                }
            }

            // ── BUSINESS INFO ───────────────────────────────────────────
            SectionLabel("BUSINESS INFORMATION")

            CompanyField("Company Name", s.name, { vm.setField("name", it) })
            CompanyField("Tagline / Description", s.tagline, { vm.setField("tagline", it) }, maxLines = 2)

            // ── CONTACT ─────────────────────────────────────────────────
            SectionLabel("CONTACT")

            CompanyField("Phone", s.phone, { vm.setField("phone", it) }, keyboardType = KeyboardType.Phone)
            CompanyField("Email", s.email, { vm.setField("email", it) }, keyboardType = KeyboardType.Email)
            CompanyField("Website", s.website, { vm.setField("website", it) }, keyboardType = KeyboardType.Uri)

            // ── BRANDED EMAIL (P3.10) ───────────────────────────────────
            SectionLabel("BRANDED EMAIL")

            when {
                s.aliasLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Loading…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Claim (no alias) OR change (Edit re-opened the field).
                s.canManageAlias && (s.aliasCurrent == null || s.aliasEditing) -> {
                    OutlinedTextField(
                        value = s.aliasInput,
                        onValueChange = { vm.onAliasInput(it) },
                        label = { Text("Brand name") },
                        placeholder = { Text("yourbrand") },
                        suffix = {
                            Text(
                                ALIAS_DOMAIN_SUFFIX,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        isError = s.aliasInput.isNotBlank() && s.aliasAvailable == false,
                        trailingIcon = {
                            when {
                                s.aliasChecking -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                                )
                                s.aliasInput.isBlank() -> {}
                                s.aliasAvailable == true -> Icon(
                                    Icons.Default.CheckCircle, "Available", tint = AppColors.Green
                                )
                                s.aliasAvailable == false -> Icon(
                                    Icons.Default.ErrorOutline, "Unavailable",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    // Inline live status, else the naming rules as helper text.
                    val rules = "3–32 characters · lowercase letters, numbers, dots or dashes"
                    val statusText: String
                    val statusColor: Color
                    when {
                        s.aliasChecking -> {
                            statusText = "Checking availability…"
                            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        s.aliasInput.isBlank() -> {
                            statusText = rules
                            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        s.aliasAvailable == true -> {
                            statusText = "Available ✓"
                            statusColor = AppColors.Green
                        }
                        s.aliasAvailable == false -> {
                            statusText = aliasReasonText(s.aliasReason)
                            statusColor = MaterialTheme.colorScheme.error
                        }
                        else -> {
                            statusText = rules
                            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        fontWeight = if (s.aliasAvailable == true) FontWeight.Medium else FontWeight.Normal
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            onClick = { vm.claimAlias() },
                            label = if (s.aliasCurrent == null) "Claim" else "Save",
                            enabled = s.aliasAvailable == true && !s.aliasBusy && !s.aliasChecking,
                            loading = s.aliasBusy
                        )
                        if (s.aliasCurrent != null) {
                            AppButton(
                                onClick = { vm.cancelEditAlias() },
                                label = "Cancel",
                                ghost = true,
                                enabled = !s.aliasBusy
                            )
                        }
                    }
                }

                // An alias exists and we're not editing — show it prominently.
                s.aliasCurrent != null -> {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AlternateEmail, null,
                                    tint = AppColors.Blue, modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    s.aliasAddress ?: "",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Customer replies to this address go straight to your company inbox.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (s.canManageAlias) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton(
                                onClick = { vm.startEditAlias() },
                                label = "Edit",
                                leadingIcon = Icons.Default.Edit,
                                enabled = !s.aliasBusy
                            )
                            AppButton(
                                onClick = { vm.setAliasRemoveDialog(true) },
                                label = "Remove",
                                labelColor = AppColors.Red,
                                enabled = !s.aliasBusy
                            )
                        }
                    }
                }

                // No alias and the viewer can't manage it.
                else -> {
                    Text(
                        "No branded email set yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (s.showAliasRemove) {
                AlertDialog(
                    onDismissRequest = { vm.setAliasRemoveDialog(false) },
                    title = { Text("Release branded email?") },
                    text = {
                        Text(
                            "Releasing ${s.aliasAddress ?: "this address"} starts a cooldown " +
                            "before it can be claimed again. Customer replies will stop routing " +
                            "to your inbox."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { vm.removeAlias() }) {
                            Text("Release", color = AppColors.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.setAliasRemoveDialog(false) }) { Text("Keep") }
                    }
                )
            }

            // ── Tier 2: verify your OWN email as the sending identity ────
            Spacer(Modifier.height(4.dp))
            Text(
                "Or use your own email address",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            when {
                s.senderLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Loading…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Verified — emails now send from the company's own address.
                s.senderStatus == "verified" -> {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = AppColors.Green.copy(alpha = 0.12f)
                        )
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle, null,
                                tint = AppColors.Green, modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Verified — emails now send from ${s.senderEmail ?: ""}.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (s.canManageAlias) {
                        AppButton(
                            onClick = { vm.setSenderRemoveDialog(true) },
                            label = "Remove",
                            labelColor = AppColors.Red,
                            enabled = !s.senderBusy
                        )
                    }
                }

                // Pending — SendGrid link sent, waiting for the owner to confirm.
                s.senderStatus == "pending" -> {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Verification sent to ${s.senderEmail ?: "your address"} — check that " +
                                "inbox, click the SendGrid link, then Refresh.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Until you confirm, emails keep sending from your alias (or default) " +
                                "with replies going to ${s.senderEmail ?: "that address"}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (s.canManageAlias) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton(
                                onClick = { vm.refreshSenderStatus() },
                                label = "Refresh status",
                                leadingIcon = Icons.Default.Refresh,
                                enabled = !s.senderBusy,
                                loading = s.senderBusy
                            )
                            AppButton(
                                onClick = { vm.setSenderRemoveDialog(true) },
                                label = "Remove",
                                labelColor = AppColors.Red,
                                enabled = !s.senderBusy
                            )
                        }
                    }
                }

                // status == none, viewer can manage — offer the verify field.
                s.canManageAlias -> {
                    OutlinedTextField(
                        value = s.senderInput,
                        onValueChange = { vm.onSenderInput(it) },
                        label = { Text("Your email address") },
                        placeholder = { Text("you@yourcompany.com") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Text(
                        "We'll email you a confirmation link. Once you click it, your emails send " +
                        "from this address.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppButton(
                        onClick = { vm.verifySenderEmail() },
                        label = "Verify this address",
                        enabled = s.senderInput.isNotBlank() && !s.senderBusy,
                        loading = s.senderBusy
                    )
                }

                // status == none, viewer can't manage.
                else -> {
                    Text(
                        "No custom sending address set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (s.showSenderRemove) {
                AlertDialog(
                    onDismissRequest = { vm.setSenderRemoveDialog(false) },
                    title = { Text("Remove sender email?") },
                    text = {
                        Text(
                            "Emails will go back to sending from your branded alias (or the default " +
                            "address). You can verify it again later."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { vm.removeSenderEmail() }) {
                            Text("Remove", color = AppColors.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.setSenderRemoveDialog(false) }) { Text("Keep") }
                    }
                )
            }

            // ── DOCUMENT DEFAULTS ───────────────────────────────────────
            SectionLabel("DEFAULT TERMS & CONDITIONS")
            CompanyField("Terms auto-filled into new estimates & invoices", s.defaultTerms, { vm.setField("default_terms", it) }, maxLines = 6)

            // ── ADDRESS ─────────────────────────────────────────────────
            SectionLabel("ADDRESS")

            CompanyField("Street Address", s.address, { vm.setField("address", it) })

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = s.city,
                    onValueChange = { vm.setField("city", it) },
                    label = { Text("City") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = s.state,
                    onValueChange = { vm.setField("state", it) },
                    label = { Text("State") },
                    modifier = Modifier.width(80.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = s.zip,
                    onValueChange = { vm.setField("zip", it) },
                    label = { Text("ZIP") },
                    modifier = Modifier.width(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── SAVE BUTTON ─────────────────────────────────────────────
            AppButton(
                onClick = { vm.save() },
                label = "Save Changes",
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !s.saving,
                loading = s.saving
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// P3.10: map the API's unavailable `reason` code to friendly inline text.
private fun aliasReasonText(reason: String?): String = when (reason) {
    "required" -> "Enter a name"
    "length"   -> "3–32 characters"
    "format"   -> "lowercase letters, numbers, dots or dashes only"
    "reserved" -> "That name is reserved"
    "taken"    -> "Already taken"
    "cooldown" -> "Recently released — available again after a cooldown"
    else       -> "Not available"
}

// P3.10 Tier 2: map a rejected sender-email POST (code + reason) to friendly text.
// 503/no_key = verification not configured; the two 400 reasons get distinct copy;
// everything else (e.g. a SendGrid rejection) falls back to the server's message.
private fun senderErrorText(code: Int, reason: String?, fallback: String): String = when {
    code == 503 || reason == "no_key" -> "Email verification isn't set up"
    reason == "address_required"      -> "Add your company street + city first"
    reason == "format"                -> "Enter a valid email"
    else                              -> fallback
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = AppColors.Blue,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp
    )
}

@Composable
private fun CompanyField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = maxLines == 1,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}
