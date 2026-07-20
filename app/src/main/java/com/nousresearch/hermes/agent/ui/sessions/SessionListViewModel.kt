package com.nousresearch.hermes.agent.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nousresearch.hermes.agent.core.session.SessionEntity
import com.nousresearch.hermes.agent.core.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SessionListViewModel — Manages session history list.
 *
 * Loads sessions from SessionStore, supports search via FTS5,
 * and handles session deletion.
 */
class SessionListViewModel(
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var currentQuery: String = ""

    init {
        observeSessions()
    }

    /**
     * Observe sessions reactively from the Room-backed store.
     */
    private fun observeSessions() {
        viewModelScope.launch {
            sessionStore.listSessions().collect { entities ->
                if (currentQuery.isBlank()) {
                    _sessions.value = entities
                }
            }
        }
    }

    /**
     * Load sessions matching the current query from the store.
     */
    fun loadSessions() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _sessions.value = if (currentQuery.isNotBlank()) {
                    sessionStore.searchMessages(currentQuery)
                        .map { msg ->
                            sessionStore.getSession(msg.sessionId)
                        }
                        .filterNotNull()
                        .distinctBy { it.id }
                } else {
                    // listSessions() is already emitting via Flow
                }
            } catch (e: Exception) {
                android.util.Log.e("SessionListVM", "Failed to load sessions", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Search sessions by query using FTS5.
     */
    fun search(query: String) {
        currentQuery = query
        _searchQuery.value = query

        if (query.isBlank()) {
            return // observeSessions() will restore the full list
        }

        viewModelScope.launch {
            try {
                val matchedMessages = sessionStore.searchMessages(query)
                val matchedSessionIds = matchedMessages
                    .map { it.sessionId }
                    .distinct()

                val matchedSessions = matchedSessionIds.mapNotNull { id ->
                    sessionStore.getSession(id)
                }

                _sessions.value = matchedSessions
            } catch (e: Exception) {
                android.util.Log.e("SessionListVM", "Search failed", e)
                _sessions.value = emptyList()
            }
        }
    }

    /**
     * Delete a session and refresh the list.
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                sessionStore.deleteSession(sessionId)
                _sessions.value = _sessions.value.filter { it.id != sessionId }
            } catch (e: Exception) {
                android.util.Log.e("SessionListVM", "Failed to delete session", e)
            }
        }
    }

    /**
     * Refresh the session list.
     */
    fun refresh() {
        currentQuery = ""
        _searchQuery.value = ""
        loadSessions()
    }
}
