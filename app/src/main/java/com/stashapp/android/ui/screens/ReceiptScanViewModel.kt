package com.stashapp.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stashapp.shared.data.parsing.ReceiptLineParser
import com.stashapp.shared.domain.ReceiptScanResult
import com.stashapp.shared.domain.usecase.MatchReceiptUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ReceiptScanUiState {
    object Scanning : ReceiptScanUiState()
    data class Processing(val rawLines: List<String>) : ReceiptScanUiState()
    data class ReviewMatches(val result: ReceiptScanResult) : ReceiptScanUiState()
    object Error : ReceiptScanUiState()
}

class ReceiptScanViewModel(
    private val matchReceiptUseCase: MatchReceiptUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReceiptScanUiState>(ReceiptScanUiState.Scanning)
    val uiState: StateFlow<ReceiptScanUiState> = _uiState

    fun onTextCaptured(lines: List<String>) {
        _uiState.value = ReceiptScanUiState.Processing(lines)
        viewModelScope.launch {
            try {
                val receiptLines = ReceiptLineParser.parse(lines)
                val result = matchReceiptUseCase.execute(receiptLines)
                _uiState.value = ReceiptScanUiState.ReviewMatches(result)
            } catch (e: Exception) {
                _uiState.value = ReceiptScanUiState.Error
            }
        }
    }

    fun reset() {
        _uiState.value = ReceiptScanUiState.Scanning
    }

    class Factory(private val matchReceiptUseCase: MatchReceiptUseCase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReceiptScanViewModel(matchReceiptUseCase) as T
        }
    }
}
