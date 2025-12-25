package com.opencode.service

import com.intellij.openapi.diagnostic.logger

private val LOG = logger<ServerManager>()

/**
 * Interface for managing OpenCode server lifecycle.
 * This abstraction enables testability by allowing injection of mock implementations.
 */
interface ServerManager {
    /**
     * Get or start the OpenCode server.
     * @return The port number if successful, null otherwise.
     */
    suspend fun getOrStartServer(): Int?
    
    /**
     * Check if the server is running on the given port.
     * @param port The port to check.
     * @return True if server is responding, false otherwise.
     */
    suspend fun isServerRunning(port: Int): Boolean
    
    /**
     * Stop the currently running server.
     */
    fun stopServer()
    
    /**
     * Get the current server port if running.
     */
    fun getServerPort(): Int?
}
