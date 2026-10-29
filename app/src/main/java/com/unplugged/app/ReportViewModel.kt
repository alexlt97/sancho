package com.unplugged.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportUiState(
    val reports: List<IssueReport> = emptyList(),
    val status: String? = null,
    val isWorking: Boolean = false,
)

class ReportViewModel(
    private val store: ReportStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun save(report: IssueReport) {
        runWork {
            store.save(report.copy(updatedAtMillis = System.currentTimeMillis()))
            "Report saved."
        }
    }

    fun delete(reportId: String) {
        runWork {
            store.delete(reportId)
            "Report deleted."
        }
    }

    fun addScreenshot(report: IssueReport, source: Uri) {
        runWork {
            store.addScreenshot(report, source)
            "Screenshot attached."
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isWorking = true, status = "Exporting reports…")
            runCatching {
                val count = store.export(uri)
                store.clear()
                count
            }.onSuccess { count ->
                _uiState.value = ReportUiState(status = "$count report(s) exported and cleared.")
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isWorking = false,
                    status = "Export failed: ${error.message ?: "unknown error"}",
                )
            }
        }
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(status = null)
    }

    private fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(reports = store.load())
        }
    }

    private fun runWork(operation: () -> String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isWorking = true)
            runCatching { operation() }
                .onSuccess { status ->
                    _uiState.value = ReportUiState(reports = store.load(), status = status)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isWorking = false,
                        status = "Report operation failed: ${error.message ?: "unknown error"}",
                    )
                }
        }
    }

    companion object {
        fun factory(store: ReportStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
                        return ReportViewModel(store) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
                }
            }
    }
}
