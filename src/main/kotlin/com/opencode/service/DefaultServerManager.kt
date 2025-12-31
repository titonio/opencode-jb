package com.opencode.service

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.random.Random

private val LOG = logger<DefaultServerManager>()

/**
 * Production implementation of ServerManager that manages actual OpenCode CLI processes.
 *
 * Manages the lifecycle of OpenCode server instances, including starting, stopping,
 * and health checking servers. Implements retry logic for server startup and maintains
 * server state across method calls.
 *
 * @param workingDirectory The directory in which the OpenCode server process will run
 * @param client HTTP client used for server health checks
 */
class DefaultServerManager(
    private val workingDirectory: File,
    private val client: OkHttpClient
) : ServerManager {

    private var serverPort: Int? = null
    private var serverProcess: Process? = null

    /**
     * Returns an existing running server port or starts a new OpenCode server.
     *
     * If a server is already running and responsive, returns its port. Otherwise,
     * attempts to start a new server with up to 3 retries. Validates that the server
     * responds to health checks before returning the port.
     *
     * @return The port number of the running server, or null if server startup fails
     */
    override suspend fun getOrStartServer(): Int? {
        LOG.info("Getting/starting shared OpenCode server")

        var result: Int? = null

        // Check if server is already running
        val existingPort = serverPort
        if (existingPort != null) {
            if (isServerRunning(existingPort)) {
                LOG.info("Reusing existing shared server on port $existingPort")
                result = existingPort
            } else {
                LOG.warn("Existing shared server on port $existingPort is unresponsive; restarting")
                serverPort = null
                serverProcess?.destroy()
                serverProcess = null
            }
        }

        if (result == null) {
            result = tryStartServerWithRetries()
        }

        return result
    }

    private suspend fun tryStartServerWithRetries(): Int? {
        LOG.info("Starting new OpenCode server...")
        repeat(MAX_RETRIES) { attempt ->
            try {
                val port = startServerInternal()
                LOG.debug("Attempt ${attempt + 1}: Testing server on port $port")
                if (waitForConnection(port, timeout = CONNECTION_TIMEOUT_MS)) {
                    serverPort = port
                    LOG.info("OpenCode server started successfully on port $port")
                    return port
                }
                val message = "Server on port $port failed to respond to health checks " +
                    "within timeout (attempt ${attempt + 1}/$MAX_RETRIES)"
                LOG.warn(message)
                serverProcess?.destroy()
                serverProcess = null
            } catch (e: IOException) {
                thisLogger().warn("Server start attempt ${attempt + 1} failed", e)
                serverProcess?.destroy()
                serverProcess = null
            }
        }

        LOG.warn("Failed to start OpenCode server after $MAX_RETRIES attempts")
        return null
    }

    private fun startServerInternal(): Int {
        val port = Random.nextInt(MIN_PORT, MAX_PORT)
        val processBuilder = ProcessBuilder()
            .command("opencode", "serve", "--port", port.toString(), "--hostname", LOCALHOST)
            .directory(workingDirectory)
            .redirectErrorStream(true)

        serverProcess = processBuilder.start()
        return port
    }

    /**
     * Checks if an OpenCode server is running and responsive on the specified port.
     *
     * Performs a health check by making an HTTP GET request to the server's session
     * endpoint. Returns true if the server responds successfully.
     *
     * @param port The port number to check
     * @return true if the server is running and responsive, false otherwise
     */
    override suspend fun isServerRunning(port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://localhost:$port/session")
                .get()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: IOException) {
            LOG.debug("Health check failed on port $port", e)
            false
        } catch (e: IllegalArgumentException) {
            LOG.debug("Invalid port $port for health check", e)
            false
        }
    }

    /**
     * Waits for the server to become responsive on the specified port.
     *
     * Periodically checks if the server is running until it responds successfully
     * or the timeout expires.
     *
     * @param port The port number to monitor
     * @param timeout Maximum time to wait in milliseconds (default 10000)
     * @return true if the server became responsive within the timeout, false otherwise
     */
    internal suspend fun waitForConnection(port: Int, timeout: Long = CONNECTION_TIMEOUT_MS): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeout) {
            if (isServerRunning(port)) return true
            kotlinx.coroutines.delay(HEALTH_CHECK_DELAY_MS)
        }
        return false
    }

    /**
     * Stops the OpenCode server process and clears server state.
     *
     * Destroys the running server process if it exists and resets the port reference.
     */
    override fun stopServer() {
        serverProcess?.destroy()
        serverProcess = null
        serverPort = null
        LOG.info("Server stopped")
    }

    /**
     * Returns the port number of the currently running server.
     *
     * @return The server port number, or null if no server is running
     */
    override fun getServerPort(): Int? = serverPort

    companion object {
        private const val MAX_RETRIES = 3
        private const val CONNECTION_TIMEOUT_MS = 10000L
        private const val HEALTH_CHECK_DELAY_MS = 200L
        private const val MIN_PORT = 16384
        private const val MAX_PORT = 65536
        private const val LOCALHOST = "127.0.0.1"
    }
}
