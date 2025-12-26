package com.opencode.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.JBTerminalWidget
import com.opencode.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private val LOG = logger<OpenCodeService>()

@Service(Service.Level.PROJECT)
class OpenCodeService(
    private val project: Project
) {
    
    // Secondary constructor for testing - allows injection of ServerManager
    internal constructor(project: Project, serverManager: ServerManager?) : this(project) {
        if (serverManager != null) {
            this.serverManagerOverride = serverManager
        }
    }
    
    private val client by lazy {
        OkHttpClient.Builder()
            .readTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    
    // Allow tests to override the server manager
    private var serverManagerOverride: ServerManager? = null
    
    // Server manager - lazily initialized
    private val server: ServerManager by lazy {
        serverManagerOverride ?: DefaultServerManager(
            workingDirectory = File(project.basePath ?: System.getProperty("user.home")),
            client = client
        )
    }
    
    // Session cache
    private val sessionCache = mutableMapOf<String, SessionInfo>()
    private var lastCacheUpdate: Long = 0
    private val CACHE_TTL = 5000L // 5 seconds
    
    // Active tab tracking (single tab per project)
    private var activeEditorFile: VirtualFile? = null
    
    // Widget tracking (for legacy tool window support)
    private val widgetPorts = mutableMapOf<JBTerminalWidget, Int>()
    
    // Configuration
    private val MAX_SESSIONS_TO_KEEP = 10
    
    // Internal flag for testing to bypass platform calls that crash in mixed test environments
    internal var disablePlatformInteractions: Boolean = false
    
    /**
     * Check if OpenCode CLI is installed and available.
     */
    fun isOpencodeInstalled(): Boolean {
        return try {
            val process = ProcessBuilder("opencode", "--version")
                .redirectErrorStream(true)
                .start()
            process.waitFor(2, TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if an OpenCode editor tab is already open.
     */
    fun hasActiveEditor(): Boolean = activeEditorFile != null
    
    /**
     * Get the currently open editor's VirtualFile.
     */
    fun getActiveEditorFile(): VirtualFile? = activeEditorFile
    
    /**
     * Register an editor tab as active.
     */
    fun registerActiveEditor(file: VirtualFile) {
        activeEditorFile = file
    }
    
    /**
     * Unregister the active editor tab.
     */
    fun unregisterActiveEditor(file: VirtualFile) {
        if (activeEditorFile == file) {
            activeEditorFile = null
            // Delay server shutdown to handle tab drag/split scenarios
            // where editor is briefly disposed before new one is created
            scheduleServerShutdownCheck()
        }
    }
    
    private fun scheduleServerShutdownCheck() {
        // Wait 1 second before checking if we should stop the server
        // This handles the case where tab is dragged/split and editor recreated
        
        if (disablePlatformInteractions) {
            // In tests without platform application, just run the check directly or skip
            // Since we can't spawn a pooled thread via ApplicationManager, we'll just check immediately
            // but we won't wait, as that would block the test
            stopSharedServerIfUnused()
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            Thread.sleep(1000)
            ApplicationManager.getApplication().invokeLater {
                stopSharedServerIfUnused()
            }
        }
    }
    
    /**
     * Get or start the shared OpenCode server.
     */
    suspend fun getOrStartSharedServer(): Int? {
        return server.getOrStartServer()
    }
    
    private fun stopSharedServerIfUnused() {
        if (activeEditorFile == null) {
            server.stopServer()
        }
    }
    
    /**
     * Create a new session via API.
     */
    suspend fun createSession(title: String? = null): String = withContext(Dispatchers.IO) {
        LOG.info("Creating new OpenCode session")
        if (LOG.isDebugEnabled) {
            LOG.debug("  title: $title")
            LOG.debug("  project: ${project.name}")
        }
        
        val port = getOrStartSharedServer()
            ?: throw IOException("Failed to start OpenCode server")
        
        val sessionTitle = title ?: "IntelliJ Session - ${java.time.LocalDateTime.now()}"
        val requestBody = gson.toJson(CreateSessionRequest(sessionTitle))
        
        val request = Request.Builder()
            .url("http://localhost:$port/session?directory=${project.basePath}")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                LOG.warn("Failed to create session: HTTP ${response.code}")
                throw IOException("Failed to create session: ${response.code}")
            }
            
            val body = response.body?.string() ?: throw IOException("Empty response")
            val sessionResponse = gson.fromJson(body, SessionResponse::class.java)
            
            // Refresh cache and cleanup old sessions
            refreshSessionCache()
            cleanupOldSessions()
            
            LOG.info("Session created successfully: ${sessionResponse.id}")
            sessionResponse.id
        }
    }
    
    /**
     * List all available sessions.
     */
    suspend fun listSessions(forceRefresh: Boolean = false): List<SessionInfo> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && now - lastCacheUpdate < CACHE_TTL && sessionCache.isNotEmpty()) {
            return sessionCache.values.sortedByDescending { it.time.updated }
        }
        
        return refreshSessionCache()
    }
    
    private suspend fun refreshSessionCache(): List<SessionInfo> = withContext(Dispatchers.IO) {
        val port = server.getServerPort() ?: return@withContext emptyList()
        
        val request = Request.Builder()
            .url("http://localhost:$port/session?directory=${project.basePath}")
            .get()
            .build()
        
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                
                val body = response.body?.string() ?: return@withContext emptyList()
                val type = object : TypeToken<List<SessionInfo>>() {}.type
                val sessions: List<SessionInfo> = gson.fromJson(body, type)
                
                sessionCache.clear()
                sessions.forEach { sessionCache[it.id] = it }
                
                lastCacheUpdate = System.currentTimeMillis()
                sessions.sortedByDescending { it.time.updated }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to list sessions", e)
            emptyList()
        }
    }
    
    /**
     * Get a specific session by ID.
     */
    suspend fun getSession(sessionId: String): SessionInfo? = withContext(Dispatchers.IO) {
        val port = server.getServerPort() ?: return@withContext null
        
        val request = Request.Builder()
            .url("http://localhost:$port/session/$sessionId?directory=${project.basePath}")
            .get()
            .build()
        
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val body = response.body?.string() ?: return@withContext null
                gson.fromJson(body, SessionInfo::class.java)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Delete a session via API.
     */
    suspend fun deleteSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val port = server.getServerPort() ?: return@withContext false
        
        val request = Request.Builder()
            .url("http://localhost:$port/session/$sessionId?directory=${project.basePath}")
            .delete()
            .build()
        
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    sessionCache.remove(sessionId)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Share a session and get the share URL.
     */
    suspend fun shareSession(sessionId: String): String? = withContext(Dispatchers.IO) {
        val port = server.getServerPort() ?: return@withContext null
        
        val request = Request.Builder()
            .url("http://localhost:$port/session/$sessionId/share?directory=${project.basePath}")
            .post("{}".toRequestBody(jsonMediaType))
            .build()
        
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val body = response.body?.string() ?: return@withContext null
                val session = gson.fromJson(body, SessionInfo::class.java)
                
                // Update cache
                sessionCache[sessionId] = session
                
                session.shareUrl
            }
        } catch (e: Exception) {
            LOG.warn("Failed to share session $sessionId", e)
            null
        }
    }
    
    /**
     * Unshare a session.
     */
    suspend fun unshareSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val port = server.getServerPort() ?: return@withContext false
        
        val request = Request.Builder()
            .url("http://localhost:$port/session/$sessionId/share?directory=${project.basePath}")
            .delete()
            .build()
        
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    // Refresh the session in cache
                    getSession(sessionId)?.let { sessionCache[sessionId] = it }
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Cleanup old sessions, keeping only the MAX_SESSIONS_TO_KEEP most recent.
     */
    private suspend fun cleanupOldSessions() {
        val sessions = listSessions(forceRefresh = true)
        if (sessions.size <= MAX_SESSIONS_TO_KEEP) return
        
        // Sort by updated time, keep most recent
        val toDelete = sessions
            .sortedByDescending { it.time.updated }
            .drop(MAX_SESSIONS_TO_KEEP)
        
        // Delete old sessions
        toDelete.forEach { session ->
            try {
                deleteSession(session.id)
            } catch (e: Exception) {
                println("Failed to cleanup session ${session.id}: ${e.message}")
            }
        }
    }
    
    /**
     * Check if a server is running on the given port.
     * Made public so OpenCodeFileEditor can verify restored ports.
     */
    suspend fun isServerRunning(port: Int): Boolean {
        return server.isServerRunning(port)
    }
    
    // ========== Legacy methods for tool window support ==========
    
    fun registerWidget(widget: JBTerminalWidget, port: Int) {
        widgetPorts[widget] = port
    }
    
    fun unregisterWidget(widget: JBTerminalWidget) {
        widgetPorts.remove(widget)
    }
    
    fun initToolWindow(toolWindow: ToolWindow) {
        LOG.info("OpenCodeService.initToolWindow called")
        
        // Guard against missing platform infrastructure (unit tests)
        if (disablePlatformInteractions) {
            LOG.info("Skipping initToolWindow - platform interactions disabled")
            return
        }
        
        val app = ApplicationManager.getApplication()
        if (app == null) {
            LOG.warn("ApplicationManager not available, skipping initToolWindow")
            return
        }

        val contentFactory = com.intellij.ui.content.ContentFactory.getInstance()
        
        toolWindow.contentManager.removeAllContents(true)

        app.invokeLater {
            val panel = com.opencode.toolwindow.OpenCodeToolWindowPanel(project, this)
            val content = contentFactory.createContent(panel, "", false)
            toolWindow.contentManager.addContent(content)
            
            toolWindow.isAvailable = true
            toolWindow.isShowStripeButton = true
            toolWindow.setType(com.intellij.openapi.wm.ToolWindowType.SLIDING, null)
            
            LOG.info("OpenCode tool window initialized with restart-capable panel")
        }
    }

    fun createTerminalWidget(): Pair<JBTerminalWidget, Int> {
        // Guard against missing platform infrastructure (unit tests)
        if (disablePlatformInteractions || ApplicationManager.getApplication() == null) {
            // For tests, return a dummy pair or throw an exception that tests expect
            if (disablePlatformInteractions) {
                // Throw an exception that tests can catch safely without crashing the suite
                throw IllegalStateException("Platform interactions disabled for testing")
            }
            throw IllegalStateException("ApplicationManager not available")
        }

        val port = Random.nextInt(16384, 65536)
        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        val widgetDisposable = com.intellij.openapi.Disposable { }
        com.intellij.openapi.util.Disposer.register(project, widgetDisposable)

        val runner = object : org.jetbrains.plugins.terminal.LocalTerminalDirectRunner(project) {
            override fun configureStartupOptions(baseOptions: org.jetbrains.plugins.terminal.ShellStartupOptions): org.jetbrains.plugins.terminal.ShellStartupOptions {
                val envs = mutableMapOf<String, String>()
                envs["_EXTENSION_OPENCODE_PORT"] = port.toString()
                envs["OPENCODE_CALLER"] = "intellij"
                if (!isWindows) {
                    envs["TERM"] = "xterm-256color"
                }
                
                return baseOptions.builder()
                    .shellCommand(listOf("opencode", "--port", port.toString()))
                    .envVariables(envs)
                    .build()
            }
        }

        val startupOptions = org.jetbrains.plugins.terminal.ShellStartupOptions.Builder()
            .workingDirectory(project.basePath ?: System.getProperty("user.home"))
            .build()
        val terminalWidget = runner.startShellTerminalWidget(widgetDisposable, startupOptions, true)
        
        val widget = com.intellij.terminal.JBTerminalWidget.asJediTermWidget(terminalWidget)
            ?: throw IllegalStateException("Failed to create JBTerminalWidget")
        
        widgetPorts[widget] = port
        
        // Note: Server connection verification removed as it's handled by terminal widget
        
        return Pair(widget, port)
    }

    fun openTerminal(initialFile: String? = null) {
        println("OpenCodeService.openTerminal called: initialFile=$initialFile")
        
        // Guard against missing platform infrastructure (unit tests)
        if (disablePlatformInteractions) {
            println("Skipping openTerminal - platform interactions disabled")
            return
        }

        if (ApplicationManager.getApplication() == null) {
            println("ApplicationManager not available, skipping openTerminal")
            return
        }
        
        val openCodeToolWindow = ToolWindowManager.getInstance(project).getToolWindow("OpenCode")
        if (openCodeToolWindow == null) {
            println("OpenCode tool window is null - not initialized yet")
            return
        }
        
        println("Showing OpenCode tool window, isVisible=${openCodeToolWindow.isVisible}")
        
        openCodeToolWindow.activate {
            println("OpenCode tool window activated")
            
            ApplicationManager.getApplication().invokeLater {
                sendFileToOpenCode(initialFile)
            }
        }
    }
    
    private fun sendFileToOpenCode(initialFile: String?) {
        val widget = widgetPorts.keys.firstOrNull()
        println("Found widget: $widget, widgetPorts size: ${widgetPorts.size}")
        
        if (widget != null && initialFile != null) {
            val port = widgetPorts[widget]
            println("Sending file to port $port: $initialFile")
            if (port != null) {
                appendPromptAsync(port, initialFile)
            }
        } else if (initialFile != null) {
            println("Widget not found, cannot send file: $initialFile")
        }
    }

    fun addFilepath(filepath: String) {
        val widget = widgetPorts.keys.firstOrNull { it.isDisplayable }
        
        if (widget != null) {
            val port = widgetPorts[widget]
            if (port != null) {
                appendPromptAsync(port, filepath)
            }
        }
    }

    private fun appendPromptAsync(port: Int, text: String) {
        if (disablePlatformInteractions) {
             // For tests that want to verify execution flow but skip actual dispatch
             return
        }
        val app = ApplicationManager.getApplication()
        if (app != null) {
            app.executeOnPooledThread {
                try {
                    appendPrompt(port, text)
                } catch (e: Throwable) {
                    // Ignore all errors in async dispatch to prevent crashing
                }
            }
        }
    }

    private fun appendPrompt(port: Int, text: String) {
        val json = "{\"text\": \"$text\"}"
        val request = Request.Builder()
            .url("http://localhost:$port/tui/append-prompt")
            .post(json.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
        }
    }
}
