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
import com.ultimatepro.ui.common.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

@HiltViewModel
class CompanyProfileViewModel @Inject constructor(
    private val repo: CrmRepository
) : ViewModel() {

    private val _s = MutableStateFlow(CompanyProfileState())
    val state = _s.asStateFlow()

    init { load() }

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
            TopAppBar(
                title = { Text("Company Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
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
                            containerColor = if (s.snackError) Color(0xFFDC2626) else Color(0xFF16A34A)
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
                            color = Color(0xFF3B82F6)
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
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
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
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { photoLauncher.launch("image/*") },
                        enabled = !s.uploading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(if (s.uploading) "Uploading..." else "Upload Logo")
                    }

                    if (s.logoUrl.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { vm.removeLogo() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                        ) {
                            Text("Remove Logo")
                        }
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
            Button(
                onClick = { vm.save() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !s.saving,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Blue)
            ) {
                if (s.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
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
