package com.opencode.editor

import com.intellij.openapi.diagnostic.logger
import com.opencode.service.OpenCodeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

private val LOG = logger<OpenCodeEditorPanelViewModel>()

/**
 * ViewModel for OpenCodeEditorPanel that handles business logic for:
 * - Server lifecycle management (start/stop/verify)
 * - Session lifecycle management (create/restore/verify)
 * - State management (initializing/running/exited/restarting)
 * - Process monitoring coordination
 *
 * This ViewModel is extracted from OpenCodeEditorPanel to enable comprehensive unit testing
 * without requiring terminal widget infrastructure.
 */
class OpenCodeEditorPanelViewModel(
    private val service: OpenCodeService,
    private val projectBasePath: String?,
    private val scope: CoroutineScope,
    initialSessionId: String? = null,
    initialServerPort: Int? = null
) {

    /**
     * Represents the current state of the OpenCode editor panel.
     *
     * @property INITIALIZING Server and session are being initialized
     * @property RUNNING Server and session are active and ready
     * @property EXITED Terminal process has exited
     * @property RESTARTING System is restarting after exit
     */
    enum class State {
        INITIALIZING,
        RUNNING,
        EXITED,
        RESTARTING
    }

    /**
     * Callback interface for view updates.
     * The view (OpenCodeEditorPanel) implements this to respond to state changes.
     */
    interface ViewCallback {
        /**
         * Called when the editor state changes.
         *
         * @param state the new state
         */
        fun onStateChanged(state: State)

        /**
         * Called when both session ID and server port are ready for use.
         *
         * @param sessionId the session ID for the editor
         * @param port the server port number
         */
        fun onSessionAndPortReady(sessionId: String, port: Int)

        /**
         * Called when an error occurs during editor operations.
         *
         * @param message the error message
         */
        fun onError(message: String)

        /**
         * Called when the terminal process exits.
         */
        fun onProcessExited()

        /**
         * Called to notify whether the terminal process is alive.
         *
         * @param isAlive true if the terminal is alive, false otherwise
         */
        fun onTerminalAlive(isAlive: Boolean)
    }

    private var callback: ViewCallback? = null

    @Volatile
    private var currentState: State = State.INITIALIZING

    /**
     * The session ID for the OpenCode editor.
     * Can be null before initialization or after cleanup.
     */
    @Volatile
    var sessionId: String? = initialSessionId
        private set

    /**
     * The server port for the OpenCode server.
     * Can be null before initialization or after cleanup.
     */
    @Volatile
    var serverPort: Int? = initialServerPort
        private set

    /**
     * Whether process monitoring is currently active.
     * Monitoring is started/stopped via startProcessMonitoring/stopProcessMonitoring.
     */
    @Volatile
    var isMonitoring: Boolean = false
        private set

    /**
     * Returns the current state of the editor panel.
     *
     * @return the current State value
     */
    fun getState(): State = currentState

    /**
     * Sets the callback for view updates.
     * The view should call this to receive state change notifications.
     *
     * @param callback the ViewCallback implementation
     */
    fun setCallback(callback: ViewCallback) {
        this.callback = callback
    }

    /**
     * Initialize server and session.
     * This is the main entry point for setting up the editor environment.
     */
    fun initialize() {
        LOG.info("Initializing OpenCodeEditorPanelViewModel - session=$sessionId, port=$serverPort")
        currentState = State.INITIALIZING
        callback?.onStateChanged(State.INITIALIZING)

        scope.launch {
            try {
                // Step 1: Handle server port restoration
                if (!handleServerPortRestoration()) {
                    return@launch
                }

                // Step 2: Start server if needed
                if (!ensureServerStarted()) {
                    return@launch
                }

                // Step 3: Handle session restoration/creation
                if (!handleSessionSetup()) {
                    return@launch
                }

                // Step 4: Notify view that session and port are ready
                val finalSessionId = sessionId!!
                val finalPort = serverPort!!
                LOG.info("Initialization complete - session=$finalSessionId, port=$finalPort")

                currentState = State.RUNNING
                callback?.onStateChanged(State.RUNNING)
                callback?.onSessionAndPortReady(finalSessionId, finalPort)
            } catch (e: IOException) {
                LOG.warn("Failed to initialize editor panel: ${e.message}", e)
                callback?.onError("Failed to initialize OpenCode: ${e.message}")
            }
        }
    }

    /**
     * Check if restored server port is still running.
     * If not, clear it so a new server will be started.
     */
    private suspend fun handleServerPortRestoration(): Boolean {
        if (serverPort != null) {
            val isRunning = service.isServerRunning(serverPort!!)
            if (!isRunning) {
                LOG.warn("Restored server port $serverPort not running, will start new server")
                serverPort = null
            } else {
                LOG.info("Reusing existing server on port $serverPort")
            }
        }
        return true
    }

    /**
     * Ensure server is started and port is available.
     */
    private suspend fun ensureServerStarted(): Boolean {
        if (serverPort == null) {
            serverPort = service.getOrStartSharedServer()
            if (serverPort == null) {
                LOG.warn("Failed to start OpenCode server")
                callback?.onError("Failed to start OpenCode server after multiple attempts")
                return false
            }
            LOG.info("Server started on port $serverPort")
        }
        return true
    }

    /**
     * Handle session restoration and creation.
     * If session exists, verify it. If not, create a new one.
     */
    private suspend fun handleSessionSetup(): Boolean {
        // Verify existing session if provided
        if (sessionId != null) {
            LOG.debug("Verifying restored session: $sessionId")
            val sessionExists = service.getSession(sessionId!!) != null

            if (!sessionExists) {
                LOG.warn("Session $sessionId no longer exists, creating new session")
                sessionId = null
            } else {
                LOG.info("Using restored session: $sessionId")
            }
        }

        // Create new session if needed
        if (sessionId == null) {
            val newSession = try {
                service.createSession(projectBasePath)
            } catch (e: IOException) {
                LOG.warn("Failed to create session", e)
                null
            }
            sessionId = newSession

            if (sessionId == null) {
                LOG.warn("No session available after initialization attempts")
                callback?.onError("Failed to create or retrieve OpenCode session")
                return false
            }

            LOG.info("New session created: $sessionId")
        }

        return true
    }

    /**
     * Start process monitoring.
     * This should be called by the view after terminal widget is created.
     */
    fun startProcessMonitoring() {
        LOG.debug("Process monitoring started")
        isMonitoring = true
    }

    /**
     * Stop process monitoring.
     * This should be called by the view when monitoring should stop.
     */
    fun stopProcessMonitoring() {
        LOG.debug("Process monitoring stopped")
        isMonitoring = false
    }

    /**
     * Check if terminal process is alive.
     * This can be called by the view during monitoring loop.
     *
     * @return true if alive, false if dead
     */
    suspend fun checkIfTerminalAlive(): Boolean {
        val port = serverPort ?: return false
        val serverAlive = service.isServerRunning(port)
        LOG.debug("Server health check: port=$port, alive=$serverAlive")
        callback?.onTerminalAlive(serverAlive)
        return serverAlive
    }

    /**
     * Handle process exit event.
     * This should be called by the view when terminal process exits.
     *
     * @param autoRestartEnabled whether auto-restart is enabled from settings
     */
    fun handleProcessExit(autoRestartEnabled: Boolean) {
        if (currentState != State.RUNNING) {
            LOG.debug("handleProcessExit called but state is $currentState, ignoring")
            return
        }

        LOG.info("Handling process exit - autoRestart=$autoRestartEnabled")
        currentState = State.EXITED
        isMonitoring = false

        callback?.onStateChanged(State.EXITED)
        callback?.onProcessExited()

        if (autoRestartEnabled) {
            LOG.info("Auto-restart enabled, triggering restart")
            restart()
        } else {
            LOG.info("Auto-restart disabled, waiting for user action")
        }
    }

    /**
     * Restart the terminal.
     * This can be called by user action or automatically on exit.
     */
    fun restart() {
        if (currentState == State.RESTARTING) {
            LOG.warn("Restart already in progress, ignoring duplicate request")
            return
        }

        LOG.info("Restarting OpenCode editor terminal")
        currentState = State.RESTARTING
        callback?.onStateChanged(State.RESTARTING)

        // Stop monitoring
        isMonitoring = false

        // Reinitialize (will reuse session if it still exists)
        initialize()
    }

    /**
     * Check if OpenCode CLI is installed.
     */
    suspend fun isOpencodeInstalled(): Boolean {
        return service.isOpencodeInstalled()
    }

    /**
     * Clean up resources.
     * This should be called when the panel is disposed.
     */
    fun dispose() {
        LOG.info("OpenCodeEditorPanelViewModel disposed")
        isMonitoring = false
        callback = null
    }
}
