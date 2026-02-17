package com.castor.feature.commandbar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castor.agent.orchestrator.AgentOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommandBarViewModel @Inject constructor(
    private val orchestrator: AgentOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommandBarState())
    val uiState: StateFlow<CommandBarState> = _uiState

    fun onSubmit(input: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, lastResponse = null) }
            try {
                val response = orchestrator.processInput(input)
                _uiState.update { it.copy(isProcessing = false, lastResponse = response) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, lastResponse = "Error: ${e.message}")
                }
            }
        }
    }
}
