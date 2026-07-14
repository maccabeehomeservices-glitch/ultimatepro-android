package com.ultimatepro.ui.imports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultimatepro.domain.model.FieldMapping
import com.ultimatepro.ui.common.AppButton
import com.ultimatepro.ui.common.AppColors
import com.ultimatepro.ui.common.LoadingView
import com.ultimatepro.ui.common.ShineHairline

private val PRICEBOOK_TARGET_FIELDS = listOf(
    null, "name", "price", "sku", "description", "cost", "category", "item_type"
)
private val CUSTOMER_TARGET_FIELDS = listOf(
    null, "name", "first_name", "last_name", "phone", "email", "address", "city", "state", "zip"
)

private val PRICEBOOK_REQUIRED_FIELDS = setOf("name", "price")
private val CUSTOMER_REQUIRED_FIELDS  = setOf("name")

private val CATEGORY_OPTIONS = listOf("Labor", "Materials", "Springs", "Doors", "Discount", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWizardScreen(
    type: String,  // "pricebook" or "customers"
    onBack: () -> Unit,
    vm: ImportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.importType = type }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { vm.previewFile(it, type, context) }
    }

    val title = if (type == "pricebook") "Import Pricebook" else "Import Customers"

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        vm.reset()
                        onBack()
                    }) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
            ShineHairline()
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is ImportState.Idle -> IdleStep(
                    type = type,
                    onPickFile = { fileLauncher.launch("*/*") }
                )
                is ImportState.Uploading -> LoadingView("Analyzing file...")
                is ImportState.MappingReview -> MappingReviewStep(
                    preview = s.preview,
                    initialMappings = s.mappings,
                    type = type,
                    onNext = { mappings, catAssignments, catGuesses ->
                        vm.proceedToDuplicateChoice(mappings, catAssignments, catGuesses)
                    },
                    onBack = { vm.reset() }
                )
                is ImportState.DuplicateChoice -> DuplicateChoiceStep(
                    onChoose = { action ->
                        val current = state
                        if (current is ImportState.DuplicateChoice) {
                            vm.executeImport(action, current.categoryAssignments, current.mappings, current.categoryGuesses)
                        }
                    },
                    onBack = { /* go back to mapping */ }
                )
                is ImportState.Importing -> LoadingView("Importing records...")
                is ImportState.Complete -> CompleteStep(result = s, onDone = { vm.reset(); onBack() })
                is ImportState.Error -> ErrorStep(message = s.message, onRetry = { vm.reset() })
            }
        }
    }
}

@Composable
private fun IdleStep(type: String, onPickFile: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Upload, null, Modifier.size(72.dp), tint = AppColors.Blue)
        Spacer(Modifier.height(24.dp))
        Text(
            if (type == "pricebook") "Import Pricebook Items" else "Import Customers",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Supports CSV, Excel (.xlsx), and TSV files.\nClaude AI will auto-detect your column mappings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        AppButton(
            onClick = onPickFile,
            label = "Choose File",
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = Icons.Default.FolderOpen
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingReviewStep(
    preview: com.ultimatepro.domain.model.ImportPreviewResponse,
    initialMappings: MutableList<FieldMapping>,
    type: String,
    onNext: (List<FieldMapping>, Map<String, String>, Map<String, String>) -> Unit,
    onBack: () -> Unit
) {
    val targetFields    = if (type == "pricebook") PRICEBOOK_TARGET_FIELDS else CUSTOMER_TARGET_FIELDS
    val requiredFields  = if (type == "pricebook") PRICEBOOK_REQUIRED_FIELDS else CUSTOMER_REQUIRED_FIELDS
    val mappings = remember { initialMappings.toMutableStateList() }
    // Only tracks items the user explicitly changed — Claude's guesses are kept separately in preview.categoryGuesses
    val categoryAssignments = remember { mutableStateMapOf<String, String>() }

    Column(Modifier.fillMaxSize()) {
        // Notes banner
        if (preview.notes.isNotBlank()) {
            Surface(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    preview.notes,
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Text(
            "${preview.totalRows} rows detected",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mapping rows
            item {
                Text("Column Mappings", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "YOUR FILE'S COLUMNS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        "ULTIMATEPRO FIELD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            itemsIndexed(mappings) { idx, mapping ->
                MappingRow(
                    sourceColumn    = mapping.sourceColumn,
                    targetField     = mapping.targetField,
                    confidence      = mapping.confidence,
                    targetFields    = targetFields,
                    isRequired      = mapping.targetField != null && requiredFields.contains(mapping.targetField),
                    onFieldSelected = { newField ->
                        mappings[idx] = mapping.copy(targetField = newField)
                    }
                )
            }

            // Category assignments (pricebook only)
            if (type == "pricebook" && preview.categoryGuesses.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Category Assignments", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                }
                items(preview.categoryGuesses.keys.toList()) { itemName ->
                    CategoryAssignRow(
                        itemName = itemName,
                        // Show user's explicit override if set, otherwise Claude's suggestion
                        selected = categoryAssignments[itemName] ?: (preview.categoryGuesses[itemName] ?: "Other"),
                        onSelect = { categoryAssignments[itemName] = it }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppButton(onClick = onBack, label = "Back", modifier = Modifier.weight(1f))
            AppButton(
                onClick = { onNext(mappings.toList(), categoryAssignments.toMap(), preview.categoryGuesses) },
                label = "Continue",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingRow(
    sourceColumn: String,
    targetField: String?,
    confidence: String,
    targetFields: List<String?>,
    isRequired: Boolean,
    onFieldSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val confidenceColor = when (confidence) {
        "high"   -> AppColors.Green
        "medium" -> AppColors.Orange
        else     -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Required fields cannot be set to skip — filter out null from dropdown options
    val availableFields = if (isRequired) targetFields.filterNotNull() else targetFields

    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sourceColumn, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (isRequired) {
                        Text(" *", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (isRequired) {
                    Text(
                        "(required)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        confidence.replaceFirstChar { it.uppercase() } + " confidence",
                        style = MaterialTheme.typography.labelSmall,
                        color = confidenceColor
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.widthIn(min = 130.dp)
                ) {
                    Text(targetField ?: "(skip)", fontSize = 12.sp, maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    availableFields.forEach { field ->
                        DropdownMenuItem(
                            text = { Text(field ?: "(skip)") },
                            onClick = { onFieldSelected(field); expanded = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryAssignRow(
    itemName: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(itemName, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.widthIn(min = 120.dp)
            ) {
                Text(selected, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CATEGORY_OPTIONS.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat) },
                        onClick = { onSelect(cat); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateChoiceStep(
    onChoose: (String) -> Unit,
    onBack: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onBack() },
        title = { Text("Duplicate Records") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What should happen when a record already exists?")
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onChoose("skip") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SkipNext, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Skip duplicates")
                }
                OutlinedButton(
                    onClick = { onChoose("update") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Update existing records")
                }
                OutlinedButton(
                    onClick = { onChoose("insert") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import all (allow duplicates)")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onBack) { Text("Back") }
        }
    )
}

@Composable
private fun CompleteStep(result: ImportState.Complete, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(72.dp), tint = AppColors.Green)
        Spacer(Modifier.height(24.dp))
        Text("Import Complete", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultRow(label = "Imported", value = result.result.imported, color = AppColors.Green)
                ResultRow(label = "Updated",  value = result.result.updated,  color = AppColors.Blue)
                ResultRow(label = "Skipped",  value = result.result.skipped,  color = AppColors.Orange)
                if (result.result.errors.isNotEmpty()) {
                    ResultRow(label = "Errors", value = result.result.errors.size, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (result.result.errors.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "First error: Row ${result.result.errors[0].row} — ${result.result.errors[0].message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(32.dp))
        AppButton(onClick = onDone, label = "Done", modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ResultRow(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value.toString(), fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp)
    }
}

@Composable
private fun ErrorStep(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Error, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text("Import Failed", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        AppButton(onClick = onRetry, label = "Try Again")
    }
}

@Composable
private fun LoadingView(message: String = "Loading...") {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
