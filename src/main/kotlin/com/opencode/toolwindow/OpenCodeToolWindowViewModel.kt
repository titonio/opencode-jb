package com.opencode.toolwindow

import com.intellij.openapi.diagnostic.logger
import com.opencode.service.OpenCodeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    
    enum class State {
        INITIALIZING, RUNNING, EXITED, RESTARTING
    }
    
    /**
     * Simplified callback interface for view updates.
     * The view (OpenCodeToolWindowPanel) implements this to respond to state changes.
     */
    interface ViewCallback {
        /**
         * Notifies the view that the state has changed.
         */
        fun onStateChanged(state: State)
        
        /**
         * Notifies the view that a port has been allocated and is ready.
         */
        fun onPortReady(port: Int)
        
        /**
         * Notifies the view that an error occurred.
         */
        fun onError(message: String)
        
        /**
         * Notifies the view that the process has exited.
         */
        fun onProcessExited()
    }
    
    private var callback: ViewCallback? = null
    
    @Volatile
    private var currentState: State = State.INITIALIZING
    
    @Volatile
    private var currentPort: Int? = null
    
    @Volatile
    private var isMonitoring = false
    
    fun getState(): State = currentState
    
    fun getCurrentPort(): Int? = currentPort
    
    fun setCallback(callback: ViewCallback) {
        this.callback = callback
    }
    
    /**
     * Initialize the ViewModel and allocate a port.
     * This is the main entry point for initializing the tool window.
     */
    fun initialize() {
        LOG.info("Initializing OpenCode terminal in tool window")
        setState(State.INITIALIZING)
        
        try {
            // Allocate a random port in the ephemeral range
            val port = Random.nextInt(16384, 65536)
            currentPort = port
            
            LOG.info("Port allocated: $port")
            callback?.onPortReady(port)
            
            // Transition to RUNNING and start monitoring
            setState(State.RUNNING)
            startProcessMonitoring()
            
        } catch (e: Exception) {
            LOG.warn("Failed to initialize OpenCode terminal: ${e.message}")
            callback?.onError("Failed to initialize OpenCode: ${e.message}")
        }
    }
    
    /**
     * Start process monitoring in a coroutine.
     */
    fun startProcessMonitoring() {
        if (isMonitoring) {
            LOG.warn("Process monitoring already active, ignoring start request")
            return
        }
        
        isMonitoring = true
        
        scope.launch {
            LOG.debug("Process monitoring coroutine started")
            
            while (isActive && isMonitoring && currentState == State.RUNNING) {
                delay(1000)
                
                val isAlive = checkServerHealth()
                
                if (!isAlive) {
                    LOG.info("OpenCode terminal process has exited")
                    handleProcessExit(autoRestartEnabled = getAutoRestartSetting())
                    break
                }
            }
            
            LOG.debug("Process monitoring coroutine stopped")
        }
    }
    
    /**
     * Stop process monitoring.
     */
    fun stopProcessMonitoring() {
        if (!isMonitoring) {
            LOG.debug("Process monitoring not active, ignoring stop request")
            return
        }
        
        LOG.info("Stopping process monitoring")
        isMonitoring = false
    }
    
    /**
     * Check if the server is healthy by attempting to connect to the port.
     */
    suspend fun checkServerHealth(): Boolean {
        val port = currentPort ?: return false
        
        return try {
            val isRunning = service.isServerRunning(port)
            LOG.debug("Server health check: port=$port, alive=$isRunning")
            isRunning
        } catch (e: Exception) {
            LOG.warn("Failed to check server health: ${e.message}")
            false
        }
    }
    
    /**
     * Handle process exit event.
     * Determines whether to auto-restart based on the autoRestartEnabled parameter.
     */
    fun handleProcessExit(autoRestartEnabled: Boolean) {
        if (currentState != State.RUNNING) {
            LOG.debug("handleProcessExit called but state is $currentState, ignoring")
            return
        }
        
        setState(State.EXITED)
        stopProcessMonitoring()
        
        // Notify the view
        callback?.onProcessExited()
        
        if (autoRestartEnabled) {
            LOG.info("Auto-restart enabled, restarting terminal")
            restart()
        } else {
            LOG.info("Auto-restart disabled, waiting for manual restart")
        }
    }
    
    /**
     * Restart the terminal.
     * Can be called manually by user or automatically on exit.
     */
    fun restart() {
        if (currentState == State.RESTARTING) {
            LOG.warn("Restart already in progress, ignoring duplicate request")
            return
        }
        
        LOG.info("Restarting OpenCode terminal")
        setState(State.RESTARTING)
        
        // Stop monitoring
        stopProcessMonitoring()
        
        // Clear port
        currentPort = null
        
        // Re-initialize
        initialize()
    }
    
    /**
     * Get the auto-restart setting from preferences.
     */
    private fun getAutoRestartSetting(): Boolean {
        return try {
            val settings = com.opencode.settings.OpenCodeSettings.getInstance()
            settings.state.autoRestartOnExit
        } catch (e: Exception) {
            LOG.warn("Failed to get auto-restart setting: ${e.message}")
            false
        }
    }
    
    /**
     * Set the current state and notify callback.
     */
    private fun setState(state: State) {
        currentState = state
        callback?.onStateChanged(state)
    }
    
    /**
     * Clean up resources when the view model is disposed.
     */
    fun dispose() {
        LOG.info("OpenCodeToolWindowViewModel disposed")
        
        // Stop monitoring
        stopProcessMonitoring()
        
        // Clear state
        currentPort = null
        
        // Clear callback
        callback = null
    }
}
