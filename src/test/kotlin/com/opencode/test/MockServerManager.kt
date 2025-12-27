package com.opencode.test

import com.opencode.service.ServerManager

/**
 * Mock implementation of ServerManager for testing.
 * Returns a fixed port and always reports the server as running.
 */
class MockServerManager(
    private val mockPort: Int = 3000,
    private val shouldSucceed: Boolean = true
) : ServerManager {
    
    private var started = true // Start as true for testing convenience
    private var stopped = false
    
    val isStarted: Boolean
        get() = started
    
    val isStopped: Boolean
        get() = stopped
    
    /** Optional stub for session configuration; used by platform helpers. */
    fun configureSession(@Suppress("UNUSED_PARAMETER") response: com.opencode.model.SessionResponse) {
        // No-op placeholder for platform tests needing a hook
    }
    
    override suspend fun getOrStartServer(): Int? {
        return if (shouldSucceed) {
            started = true
            stopped = false
            mockPort
        } else {
            null
        }
    }
    
    override suspend fun isServerRunning(port: Int): Boolean {
        return shouldSucceed && started && !stopped && port == mockPort
    }
    
    override fun stopServer() {
        stopped = true
        started = false
    }
    
    override fun getServerPort(): Int? {
        return if (shouldSucceed && started && !stopped) mockPort else null
    }
}
