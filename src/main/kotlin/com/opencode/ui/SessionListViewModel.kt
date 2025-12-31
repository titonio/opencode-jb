package com.opencode.ui

import com.intellij.openapi.diagnostic.logger
import com.opencode.model.SessionInfo
import com.opencode.service.OpenCodeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

private val LOG = logger<SessionListViewModel>()

/**
 * ViewModel for SessionListDialog that handles business logic separately from UI.
 * This separation enables unit testing of session management logic without UI dependencies.
 *
 * Responsibilities:
 * - Loading and refreshing session list
 * - Creating new sessions
 * - Deleting sessions
 * - Sharing/unsharing sessions
 * - Managing selection state
 *
 * The ViewModel communicates with the view through callbacks for:
 * - Session list updates
 * - Selection changes
 * - Error handling
 * - Success notifications
 */
class SessionListViewModel(
    private val service: OpenCodeService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    /**
     * Callback interface for view updates
     */
    interface ViewCallback {
        /**
         * Called when sessions have been loaded from the service
         * @param sessions The loaded list of sessions
         */
        fun onSessionsLoaded(sessions: List<SessionInfo>)

        /**
         * Called when a session selection changes
         * @param session The selected session, or null if selection is cleared
         */
        fun onSessionSelected(session: SessionInfo?)

        /**
         * Called when an error occurs during an operation
         * @param message The error message to display
         */
        fun onError(message: String)

        /**
         * Called when an operation completes successfully
         * @param message The success message to display
         */
        fun onSuccess(message: String)

        /**
         * Called when a share URL has been generated for a session
         * @param url The generated share URL
         */
        fun onShareUrlGenerated(url: String)
    }

    private var callback: ViewCallback? = null
    private var sessions: List<SessionInfo> = emptyList()
    private var selectedSession: SessionInfo? = null

    /**
     * Set the view callback for updates
     */
    fun setCallback(callback: ViewCallback) {
        this.callback = callback
    }

    /**
     * Load sessions from the service (internal suspend version)
     * @param forceRefresh Whether to force a cache refresh
     */
    private suspend fun loadSessionsSuspend(forceRefresh: Boolean = true) {
        val loadedSessions = service.listSessions(forceRefresh = forceRefresh)
        sessions = loadedSessions
        callback?.onSessionsLoaded(loadedSessions)
    }

    /**
     * Load sessions from the service
     * @param forceRefresh Whether to force a cache refresh
     */
    fun loadSessions(forceRefresh: Boolean = true) {
        scope.launch {
            loadSessionsSuspend(forceRefresh)
        }
    }

    /**
     * Create a new session
     * @param title Optional session title
     */
    fun createSession(title: String?) {
        scope.launch {
            try {
                val sessionId = service.createSession(title)
                loadSessionsSuspend(forceRefresh = true)

                val newSession = sessions.find { it.id == sessionId }
                if (newSession != null) {
                    selectSession(newSession)
                }
            } catch (e: IOException) {
                LOG.warn("Failed to create session", e)
                callback?.onError("Failed to create session: ${e.message}")
            }
        }
    }

    /**
     * Delete a session
     * @param session The session to delete
     * @return True if operation should proceed (for confirmation dialogs)
     */
    fun deleteSession(session: SessionInfo) {
        scope.launch {
            val success = service.deleteSession(session.id)
            if (success) {
                loadSessionsSuspend(forceRefresh = true)
                callback?.onSuccess("Session deleted successfully")
            } else {
                callback?.onError("Failed to delete session")
            }
        }
    }

    /**
     * Share a session (or get existing share URL)
     * @param session The session to share
     */
    fun shareSession(session: SessionInfo) {
        scope.launch {
            if (session.share != null) {
                callback?.onShareUrlGenerated(session.share.url)
                return@launch
            }

            val shareUrl = service.shareSession(session.id)

            if (shareUrl != null) {
                loadSessionsSuspend(forceRefresh = true)
                callback?.onShareUrlGenerated(shareUrl)
                callback?.onSuccess("Session shared successfully")
            } else {
                callback?.onError("Failed to share session")
            }
        }
    }

    /**
     * Unshare a session
     * @param session The session to unshare
     */
    fun unshareSession(session: SessionInfo) {
        scope.launch {
            val success = service.unshareSession(session.id)

            if (success) {
                loadSessionsSuspend(forceRefresh = true)
                callback?.onSuccess("Session unshared successfully")
            } else {
                callback?.onError("Failed to unshare session")
            }
        }
    }

    /**
     * Select a session
     * @param session The session to select, or null to clear selection
     */
    fun selectSession(session: SessionInfo?) {
        selectedSession = session
        callback?.onSessionSelected(session)
    }

    /**
     * Get the currently selected session
     */
    fun getSelectedSession(): SessionInfo? = selectedSession

    /**
     * Get the current list of sessions
     */
    fun getSessions(): List<SessionInfo> = sessions

    /**
     * Check if a session is already shared
     */
    fun isSessionShared(session: SessionInfo): Boolean = session.share != null

    /**
     * Get formatted session title for display
     */
    fun getSessionDisplayTitle(session: SessionInfo): String = session.title

    /**
     * Get share URL for a session (if shared)
     */
    fun getShareUrl(session: SessionInfo): String? = session.share?.url
}
