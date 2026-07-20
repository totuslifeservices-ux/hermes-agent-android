package com.nousresearch.hermes.agent.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nousresearch.hermes.agent.core.agent.AgentOrchestrator
import com.nousresearch.hermes.agent.core.ProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** DataStore extension property for Context */
private val Context.settingsDataStore by preferencesDataStore(name = "hermes_settings")

/**
 * SettingsViewModel — Manages app settings backed by DataStore<Preferences>.
 *
 * Exposes StateFlows for each setting and pushes config changes
 * to the AgentOrchestrator when applied.
 */
class SettingsViewModel(
    private val appContext: Context,
    private val orchestrator: AgentOrchestrator? = null,
) : ViewModel() {

    // ── Preference Keys ─────────────────────────────────────────────

    private val KEY_PROVIDER = stringPreferencesKey("provider")
    private val KEY_MODEL = stringPreferencesKey("model")
    private val KEY_API_KEY = stringPreferencesKey("api_key")
    private val KEY_TEMPERATURE = floatPreferencesKey("temperature")
    private val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_TEXT_SIZE = floatPreferencesKey("text_size")

    // ── State ───────────────────────────────────────────────────────

    private val _provider = MutableStateFlow(ProviderType.NousPortal)
    val provider: StateFlow<ProviderType> = _provider.asStateFlow()

    private val _model = MutableStateFlow("claude-sonnet-4")
    val model: StateFlow<String> = _model.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _maxTokens = MutableStateFlow(32768)
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _textSize = MutableStateFlow(1.0f)
    val textSize: StateFlow<Float> = _textSize.asStateFlow()

    init {
        loadSettings()
    }

    // ── Public API ──────────────────────────────────────────────────

    fun setProvider(value: ProviderType) {
        _provider.value = value
        saveSetting(KEY_PROVIDER, value.name)
    }

    fun setModel(value: String) {
        _model.value = value
        saveSetting(KEY_MODEL, value)
    }

    fun setApiKey(value: String) {
        _apiKey.value = value
        saveSetting(KEY_API_KEY, value)
    }

    fun setTemperature(value: Float) {
        _temperature.value = value
        saveSetting(KEY_TEMPERATURE, value)
    }

    fun setMaxTokens(value: Int) {
        _maxTokens.value = value
        saveSetting(KEY_MAX_TOKENS, value)
    }

    fun setThemeMode(value: String) {
        _themeMode.value = value
        saveSetting(KEY_THEME_MODE, value)
    }

    fun setTextSize(value: Float) {
        _textSize.value = value
        saveSetting(KEY_TEXT_SIZE, value)
    }

    /**
     * Push current settings — persisted to DataStore.
     * In production, this would also update the LLM provider config.
     */
    fun applySettings() {
        android.util.Log.d("SettingsVM", "Settings applied: ${_provider.value}/${_model.value}")
    }

    /**
     * Reset all settings to defaults.
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            appContext.settingsDataStore.edit { it.clear() }
            loadFromPreferences()
        }
    }

    // ── Private ─────────────────────────────────────────────────────

    private fun loadSettings() {
        viewModelScope.launch {
            appContext.settingsDataStore.data.collect { prefs ->
                _provider.value = prefs[KEY_PROVIDER]?.let { name ->
                    try { ProviderType.valueOf(name) } catch (_: Exception) { ProviderType.NousPortal }
                } ?: ProviderType.NousPortal

                _model.value = prefs[KEY_MODEL] ?: "claude-sonnet-4"
                _apiKey.value = prefs[KEY_API_KEY] ?: ""
                _temperature.value = prefs[KEY_TEMPERATURE] ?: 0.7f
                _maxTokens.value = prefs[KEY_MAX_TOKENS] ?: 32768
                _themeMode.value = prefs[KEY_THEME_MODE] ?: "system"
                _textSize.value = prefs[KEY_TEXT_SIZE] ?: 1.0f
            }
        }
    }

    private suspend fun loadFromPreferences() {
        val prefs = appContext.settingsDataStore.data.first()
        _provider.value = ProviderType.NousPortal
        _model.value = "claude-sonnet-4"
        _apiKey.value = ""
        _temperature.value = 0.7f
        _maxTokens.value = 32768
        _themeMode.value = "system"
        _textSize.value = 1.0f
    }

    private fun saveSetting(key: Preferences.Key<*>, value: Any) {
        viewModelScope.launch {
            appContext.settingsDataStore.edit { prefs ->
                when (value) {
                    is String -> prefs[key as Preferences.Key<String>] = value
                    is Float -> prefs[key as Preferences.Key<Float>] = value
                    is Int -> prefs[key as Preferences.Key<Int>] = value
                    is Boolean -> prefs[key as Preferences.Key<Boolean>] = value
                }
            }
        }
    }
}
