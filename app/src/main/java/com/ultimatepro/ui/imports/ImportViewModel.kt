package com.ultimatepro.ui.imports

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import com.ultimatepro.domain.model.FieldMapping
import com.ultimatepro.domain.model.ImportExecuteRequest
import com.ultimatepro.domain.model.ImportPreviewResponse
import com.ultimatepro.domain.model.ImportResultResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Base64
import javax.inject.Inject

sealed class ImportState {
    object Idle : ImportState()
    object Uploading : ImportState()
    data class MappingReview(val preview: ImportPreviewResponse, val mappings: MutableList<FieldMapping>) : ImportState()
    data class DuplicateChoice(val preview: ImportPreviewResponse, val mappings: List<FieldMapping>, val categoryAssignments: Map<String, String>, val categoryGuesses: Map<String, String>) : ImportState()
    object Importing : ImportState()
    data class Complete(val result: ImportResultResponse) : ImportState()
    data class Error(val message: String) : ImportState()
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repo: CrmRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state

    // Hold file bytes and name for re-use in execute step
    private var fileBytes: ByteArray? = null
    private var fileName: String = ""
    var importType: String = "pricebook"

    fun previewFile(uri: Uri, type: String, context: Context) {
        importType = type
        viewModelScope.launch {
            _state.value = ImportState.Uploading
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                    ?: run { _state.value = ImportState.Error("Could not read file"); return@launch }
                fileBytes = bytes
                fileName = getFileName(uri, context)
                when (val r = repo.previewImport(bytes, fileName, type)) {
                    is Result.Success -> {
                        val mappings = r.data.mappings.toMutableList()
                        _state.value = ImportState.MappingReview(r.data, mappings)
                    }
                    is Result.Error -> _state.value = ImportState.Error(r.message)
                }
            } catch (e: Exception) {
                _state.value = ImportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun proceedToDuplicateChoice(mappings: List<FieldMapping>, categoryAssignments: Map<String, String>, categoryGuesses: Map<String, String>) {
        val current = _state.value
        val preview = when (current) {
            is ImportState.MappingReview -> current.preview
            else -> return
        }
        _state.value = ImportState.DuplicateChoice(preview, mappings, categoryAssignments, categoryGuesses)
    }

    fun executeImport(duplicateAction: String, categoryAssignments: Map<String, String>, mappings: List<FieldMapping>, categoryGuesses: Map<String, String>) {
        val bytes = fileBytes ?: run { _state.value = ImportState.Error("File data lost"); return }
        viewModelScope.launch {
            _state.value = ImportState.Importing
            try {
                val base64 = Base64.getEncoder().encodeToString(bytes)
                val mappingsList = mappings.map { mapOf("sourceColumn" to it.sourceColumn, "targetField" to it.targetField) }
                val request = ImportExecuteRequest(
                    type                = importType,
                    fileData            = base64,
                    fileName            = fileName,
                    mappings            = mappingsList,
                    duplicateAction     = duplicateAction,
                    categoryAssignments = categoryAssignments,
                    categoryGuesses     = categoryGuesses
                )
                when (val r = repo.executeImport(request)) {
                    is Result.Success -> _state.value = ImportState.Complete(r.data)
                    is Result.Error   -> _state.value = ImportState.Error(r.message)
                }
            } catch (e: Exception) {
                _state.value = ImportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun reset() {
        fileBytes = null
        fileName = ""
        _state.value = ImportState.Idle
    }

    private fun getFileName(uri: Uri, context: Context): String {
        var name = "import_file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx) ?: name
        }
        return name
    }
}
