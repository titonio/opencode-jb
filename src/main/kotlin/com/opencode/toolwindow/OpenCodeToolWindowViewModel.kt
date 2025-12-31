package com.opencode.toolwindow

import com.intellij.openapi.diagnostic.logger
import com.opencode.service.OpenCodeService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.random.Random

private val LOG = logger<OpenCodeToolWindowViewModel>()

/**
 * ViewModel for OpenCodeToolWindowPanel that handles business logic for:
 * - Port allocation
 * - Process lifecycle management (start/monitor/restart)
 * - State management (initializing/running/exited/restarting)
 * - Health monitoring coordination
 *
 * This ViewModel is extracted from OpenCodeToolWindowPanel to enable comprehensive unit testing
 * without requiring terminal widget and UI infrastructure.
 */
class OpenCodeToolWindowViewModel(
    private val service: OpenCodeService,
    private val scope: CoroutineScope
) {

    @Volatile
    private var currentState: State = State.INITIALIZING

    @Volatile
    private var currentPort: Int? = null

    @Volatile
    private var isMonitoring = false

    private var callback: ViewCallback? = null

    /**
     * Represents the current state of the OpenCode process managed by this ViewModel.
     */
    enum class State {
        /**
         * Initial state while the ViewModel is setting up and allocating a port.
         */
        INITIALIZING,

        /**
         * Process is running and actively monitored for health.
         */
        RUNNING,

        /**
         * Process has exited and is not being monitored.
         */
        EXITED,

        /**
         * Process is being restarted; temporary state during re-initialization.
         */
        RESTARTING
    }

    /**
     * Simplified callback interface for view updates.
     * The view (OpenCodeToolWindowPanel) implements this to respond to state changes.
     */
    interface ViewCallback {
        /**
         * Called when the ViewModel state changes.
         *
         * @param state The new state of the ViewModel
         */
        fun onStateChanged(state: State)

        /**
         * Called when a port has been allocated and is ready for use.
         *
         * @param port The allocated port number
         */
        fun onPortReady(port: Int)

        /**
         * Called when an error occurs during initialization, monitoring, or other operations.
         *
         * @param message Human-readable error message describing what went wrong
         */
        fun onError(message: String)

        /**
         * Called when the OpenCode process has exited.
         * The view may choose to display a message or update the UI accordingly.
         */
        fun onProcessExited()
    }

    /**
     * Returns the current state of the ViewModel.
     *
     * @return The current State enum value
     */
    fun getState(): State = currentState

    /**
     * Returns the currently allocated port number, if any.
     *
     * @return The port number, or null if no port has been allocated
     */
    fun getCurrentPort(): Int? = currentPort

    /**
     * Sets the callback to receive ViewModel updates.
     * Only one callback can be registered at a time.
     *
     * @param callback The ViewCallback implementation to receive state changes, errors, and port allocations
     */
    fun setCallback(callback: ViewCallback) {
        this.callback = callback
    }

    /**
     * Initializes the ViewModel by allocating a random port and starting process monitoring.
     * This is the main entry point for initializing the tool window.
     *
     * Transition sequence: INITIALIZING → allocate port → RUNNING → start monitoring
     */
    fun initialize() {
        setState(State.INITIALIZING)

        val port = Random.nextInt(MIN_PORT, MAX_PORT)
        currentPort = port

        callback?.onPortReady(port)

        setState(State.RUNNING)
        startProcessMonitoring()
    }

    /**
     * Starts monitoring the OpenCode process health in a background coroutine.
     * The coroutine checks server health every second and handles process exit events.
     *
     * Monitoring continues until [stopProcessMonitoring] is called, the process exits,
     * or the coroutine scope is cancelled.
     */
    fun startProcessMonitoring() {
        if (isMonitoring) {
            return
        }

        isMonitoring = true

        scope.launch {
            while (isActive && isMonitoring && currentState == State.RUNNING) {
                delay(1000)

                val isAlive = checkServerHealth()

                if (!isAlive) {
                    handleProcessExit(autoRestartEnabled = getAutoRestartSetting())
                    break
                }
            }
        }
    }

    /**
     * Stops the background health monitoring coroutine.
     *
     * This is a no-op if monitoring is not currently active.
     */
    fun stopProcessMonitoring() {
        if (!isMonitoring) {
            return
        }

        isMonitoring = false
    }

    /**
     * Checks if the OpenCode server is running and healthy by attempting to connect to the allocated port.
     *
     * @return true if the server is running and responding, false otherwise
     * @throws CancellationException if the coroutine is cancelled during execution
     */
    suspend fun checkServerHealth(): Boolean {
        val port = currentPort ?: return false

        return try {
            service.isServerRunning(port)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            LOG.warn("Health check failed for port $port", e)
            false
        }
    }

    /**
     * Handles process exit events by transitioning to EXITED state and optionally restarting.
     *
     * Transition sequence: RUNNING → EXITED → (optionally) RESTARTING → RUNNING
     *
     * @param autoRestartEnabled If true, automatically restarts the process after exit;
     *                            otherwise, waits for manual restart
     */
    fun handleProcessExit(autoRestartEnabled: Boolean) {
        if (currentState != State.RUNNING) {
            return
        }

        setState(State.EXITED)
        stopProcessMonitoring()

        callback?.onProcessExited()

        if (autoRestartEnabled) {
            restart()
        }
    }

    /**
     * Restarts the terminal by re-initializing with a new port allocation.
     *
     * This is a no-op if a restart is already in progress (state is RESTARTING).
     * Can be called manually by the user or automatically on process exit if auto-restart is enabled.
     *
     * Transition sequence: any state → RESTARTING → INITIALIZING → RUNNING
     */
    fun restart() {
        if (currentState == State.RESTARTING) {
            return
        }

        setState(State.RESTARTING)

        stopProcessMonitoring()
        currentPort = null
        initialize()
    }

    /**
     * Get the auto-restart setting from preferences.
     */
    private fun getAutoRestartSetting(): Boolean {
        val settings = com.opencode.settings.OpenCodeSettings.getInstance()
        return settings.state.autoRestartOnExit
    }

    /**
     * Set the current state and notify callback.
     */
    private fun setState(state: State) {
        currentState = state
        callback?.onStateChanged(state)
    }

    /**
     * Cleans up resources when the ViewModel is no longer needed.
     *
     * Stops process monitoring, clears the allocated port, and removes the callback reference.
     * Should be called when the tool window is closed or disposed.
     */
    fun dispose() {
        stopProcessMonitoring()
        currentPort = null
        callback = null
    }

    companion object {
        private const val MIN_PORT = 16384
        private const val MAX_PORT = 65536
    }
}
