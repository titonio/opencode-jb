package com.opencode.service

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private val LOG = logger<DefaultServerManager>()

/**
 * Production implementation of ServerManager that manages actual OpenCode CLI processes.
 */
class DefaultServerManager(
    private val workingDirectory: File,
    private val client: OkHttpClient
) : ServerManager {
    
    private var serverPort: Int? = null
    private var serverProcess: Process? = null
    
    override suspend fun getOrStartServer(): Int? {
        LOG.info("Getting/starting shared OpenCode server")
        
        // Check if server is already running
        if (serverPort != null) {
            val existingPort = serverPort!!
            if (isServerRunning(existingPort)) {
                LOG.info("Reusing existing shared server on port $existingPort")
                return existingPort
            }
            LOG.warn("Existing shared server on port $existingPort is unresponsive; restarting")
            serverPort = null
            serverProcess?.destroy()
            serverProcess = null
        }
        
        // Try to start server with retries
        val maxRetries = 3
        LOG.info("Starting new OpenCode server...")
        repeat(maxRetries) { attempt ->
            try {
                val port = startServerInternal()
                LOG.debug("Attempt ${attempt + 1}: Testing server on port $port")
                if (waitForConnection(port, timeout = 10000)) {
                    serverPort = port
                    LOG.info("OpenCode server started successfully on port $port")
                    return port
                }
                LOG.warn("Server on port $port failed to respond (attempt ${attempt + 1})")
                serverProcess?.destroy()
                serverProcess = null
            } catch (e: Exception) {
                thisLogger().warn("Server start attempt ${attempt + 1} failed", e)
                serverProcess?.destroy()
                serverProcess = null
            }
        }
        
        LOG.warn("Failed to start OpenCode server after $maxRetries attempts")
        return null
    }
    
    private fun startServerInternal(): Int {
        val port = Random.nextInt(16384, 65536)
        val processBuilder = ProcessBuilder()
            .command("opencode", "serve", "--port", port.toString(), "--hostname", "127.0.0.1")
            .directory(workingDirectory)
            .redirectErrorStream(true)
        
        serverProcess = processBuilder.start()
        return port
    }
    
    override suspend fun isServerRunning(port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://localhost:$port/session")
                .get()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            LOG.debug("Health check failed on port $port", e)
            false
        }
    }
    
    internal suspend fun waitForConnection(port: Int, timeout: Long = 10000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeout) {
            if (isServerRunning(port)) return true
            kotlinx.coroutines.delay(200)
        }
        return false
    }
    
    override fun stopServer() {
        serverProcess?.destroy()
        serverProcess = null
        serverPort = null
        LOG.info("Server stopped")
    }
    
    override fun getServerPort(): Int? = serverPort
}
