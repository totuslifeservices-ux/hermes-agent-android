package com.nousresearch.hermes.agent.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ToolDashboardViewModel — Manages tool registry display and permissions.
 *
 * Loads available tools from ToolRegistry, tracks permission states,
 * and handles tool enable/disable toggles.
 */
class ToolDashboardViewModel(
    private val toolRegistry: ToolRegistry,
) : ViewModel() {

    // ── Tool state ──────────────────────────────────────────────────

    data class ToolUiState(
        val name: String,
        val description: String,
        val requiresConfirmation: Boolean,
        val requiresPermissions: List<String>,
        val isEnabled: Boolean = true,
        val permissionGranted: Map<String, Boolean> = emptyMap(),
    )

    private val _tools = MutableStateFlow<List<ToolUiState>>(emptyList())
    val tools: StateFlow<List<ToolUiState>> = _tools.asStateFlow()

    private val _selectedTool = MutableStateFlow<String?>(null)
    val selectedTool: StateFlow<String?> = _selectedTool.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadTools()
    }

    /**
     * Load all registered tools from the registry.
     */
    fun loadTools() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val toolNames = toolRegistry.getToolNames()
                val toolStates = toolNames.mapNotNull { name ->
                    val tool = toolRegistry.get(name) ?: return@mapNotNull null
                    ToolUiState(
                        name = tool.name,
                        description = tool.descriptor.description,
                        requiresConfirmation = tool.requiresConfirmation,
                        requiresPermissions = tool.requiresPermissions,
                        isEnabled = true,
                        permissionGranted = tool.requiresPermissions.associateWith { false },
                    )
                }
                _tools.value = toolStates
            } catch (e: Exception) {
                android.util.Log.e("ToolDashboardVM", "Failed to load tools", e)
                _tools.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Select a tool to show details.
     */
    fun selectTool(name: String?) {
        _selectedTool.value = name
    }

    /**
     * Toggle tool enabled/disabled.
     */
    fun toggleTool(name: String) {
        _tools.value = _tools.value.map { tool ->
            if (tool.name == name) {
                tool.copy(isEnabled = !tool.isEnabled)
            } else {
                tool
            }
        }
    }

    /**
     * Update permission state for a permission.
     */
    fun updatePermission(toolName: String, permission: String, granted: Boolean) {
        _tools.value = _tools.value.map { tool ->
            if (tool.name == toolName) {
                tool.copy(
                    permissionGranted = tool.permissionGranted + (permission to granted)
                )
            } else {
                tool
            }
        }
    }

    /**
     * Get the currently selected tool details.
     */
    fun getSelectedToolDetail(): ToolUiState? {
        val name = _selectedTool.value ?: return null
        return _tools.value.find { it.name == name }
    }
}
