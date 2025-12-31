package com.opencode.service

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.JBTerminalWidget
import com.opencode.model.CreateSessionRequest
import com.opencode.model.SessionInfo
import com.opencode.model.SessionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import kotlin.random.Random

private val LOG = logger<OpenCodeService>()

/**
 * Project-level service managing OpenCode server lifecycle and session operations.
 *
 * This service provides the main interface for interacting with the OpenCode CLI server,
 * including session management (create, list, delete, share), server lifecycle control,
 * and integration with IntelliJ platform components (editor tabs, tool windows, terminal widgets).
 *
 * @property project The IntelliJ project this service is associated with
 */
@Service(Service.Level.PROJECT)
class OpenCodeService(
    private val project: Project
) {

    private val client by lazy {
        OkHttpClient.Builder()
            .readTimeout(HTTP_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(HTTP_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
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

    // Active tab tracking (single tab per project)
    private var activeEditorFile: VirtualFile? = null

    // Widget tracking (for legacy tool window support)
    private val widgetPorts = mutableMapOf<JBTerminalWidget, Int>()

    // Internal flag for testing to bypass platform calls that crash in mixed test environments
    internal var disablePlatformInteractions: Boolean = false

    // Secondary constructor for testing - allows injection of ServerManager
    internal constructor(project: Project, serverManager: ServerManager?) : this(project) {
        if (serverManager != null) {
            this.serverManagerOverride = serverManager
        }
    }

    internal fun shouldShowDialogs(): Boolean {
        return !disablePlatformInteractions && !ApplicationManager.getApplication().isHeadlessEnvironment
    }

    /**
     * Check if OpenCode CLI is installed and available.
     *
     * @return true if OpenCode CLI is accessible, false otherwise
     */
    fun isOpencodeInstalled(): Boolean {
        if (disablePlatformInteractions) {
            return true
        }
        return try {
            val process = ProcessBuilder("opencode", "--version")
                .redirectErrorStream(true)
                .start()
            process.waitFor(PROCESS_WAIT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (e: IOException) {
            LOG.warn("Failed to check if OpenCode CLI is installed", e)
            false
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            LOG.warn("Check for OpenCode CLI installation interrupted", e)
            false
        }
    }

    /**
     * Check if an OpenCode editor tab is already open.
     *
     * @return true if an active editor is registered, false otherwise
     */
    fun hasActiveEditor(): Boolean = activeEditorFile != null

    /**
     * Get the currently open editor's VirtualFile.
     *
     * @return the active editor's VirtualFile, or null if no editor is open
     */
    fun getActiveEditorFile(): VirtualFile? = activeEditorFile

    /**
     * Register an editor tab as active.
     *
     * @param file the VirtualFile representing the editor tab to register
     */
    fun registerActiveEditor(file: VirtualFile) {
        activeEditorFile = file
    }

    /**
     * Unregister the active editor tab.
     *
     * Schedules a delayed check to stop the shared server if no editors remain active.
     * This delay handles tab drag/split scenarios where an editor is briefly disposed
     * before a new one is created.
     *
     * @param file the VirtualFile representing the editor tab to unregister
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
            Thread.sleep(SERVER_SHUTDOWN_DELAY_MS)
            ApplicationManager.getApplication().invokeLater {
                stopSharedServerIfUnused()
            }
        }
    }

    /**
     * Get or start the shared OpenCode server.
     *
     * Returns the port of an existing running server or starts a new one.
     *
     * @return the server port, or null if the server could not be started
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
     *
     * Starts the server if needed and creates a new session with the given title.
     * Automatically refreshes the session cache and cleans up old sessions after creation.
     *
     * @param title optional title for the session. If null, a default title is generated
     * @return the ID of the newly created session
     * @throws IOException if the server fails to start or the API request fails
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
     *
     * Returns cached sessions if available and fresh, otherwise fetches from the server.
     * Results are sorted by last updated time in descending order.
     *
     * @param forceRefresh if true, bypass the cache and fetch from server
     * @return list of sessions sorted by last updated time (most recent first)
     */
    suspend fun listSessions(forceRefresh: Boolean = false): List<SessionInfo> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && now - lastCacheUpdate < CACHE_TTL_MS && sessionCache.isNotEmpty()) {
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
        } catch (e: IOException) {
            LOG.warn("Failed to list sessions", e)
            emptyList()
        } catch (e: JsonParseException) {
            LOG.warn("Failed to parse session list JSON", e)
            emptyList()
        }
    }

    /**
     * Get a specific session by ID.
     *
     * @param sessionId the ID of the session to retrieve
     * @return the session info, or null if not found or request fails
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
        } catch (e: IOException) {
            LOG.warn("Failed to get session $sessionId", e)
            null
        } catch (e: JsonParseException) {
            LOG.warn("Failed to parse session JSON for $sessionId", e)
            null
        }
    }

    /**
     * Delete a session via API.
     *
     * Removes the session from the server cache if deletion succeeds.
     *
     * @param sessionId the ID of the session to delete
     * @return true if the session was deleted successfully, false otherwise
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
        } catch (e: IOException) {
            LOG.warn("Failed to delete session $sessionId", e)
            false
        }
    }

    /**
     * Share a session and get the share URL.
     *
     * Enables sharing for the session and returns the share URL.
     * Updates the session cache with the share information.
     *
     * @param sessionId the ID of the session to share
     * @return the share URL, or null if sharing failed
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
                val session = gson.fromJson(body, SessionInfo::class.java) ?: return@withContext null

                // Update cache
                sessionCache[sessionId] = session

                session.shareUrl
            }
        } catch (e: IOException) {
            LOG.warn("Failed to share session $sessionId", e)
            null
        } catch (e: JsonParseException) {
            LOG.warn("Failed to parse share session JSON for $sessionId", e)
            null
        }
    }

    /**
     * Unshare a session.
     *
     * Disables sharing for the session and refreshes the session cache.
     *
     * @param sessionId the ID of the session to unshare
     * @return true if unsharing succeeded, false otherwise
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
        } catch (e: IOException) {
            LOG.warn("Failed to unshare session $sessionId", e)
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
            } catch (e: IOException) {
                println("Failed to cleanup session ${session.id}: ${e.message}")
            }
        }
    }

    /**
     * Check if a server is running on the given port.
     *
     * Used by OpenCodeFileEditor to verify restored ports during session recovery.
     *
     * @param port the port number to check
     * @return true if a server is running on the port, false otherwise
     */
    suspend fun isServerRunning(port: Int): Boolean {
        return server.isServerRunning(port)
    }

    // ========== Legacy methods for tool window support ==========

    /**
     * Register a terminal widget with its associated server port.
     *
     * @param widget the terminal widget to register
     * @param port the server port the widget is connected to
     */
    fun registerWidget(widget: JBTerminalWidget, port: Int) {
        widgetPorts[widget] = port
    }

    /**
     * Unregister a terminal widget.
     *
     * @param widget the terminal widget to unregister
     */
    fun unregisterWidget(widget: JBTerminalWidget) {
        widgetPorts.remove(widget)
    }

    /**
     * Initialize the OpenCode tool window.
     *
     * Creates and configures the tool window panel for displaying the OpenCode terminal.
     *
     * @param toolWindow the tool window to initialize
     */
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

    /**
     * Create a new terminal widget for OpenCode.
     *
     * Creates a terminal widget running the OpenCode CLI with a randomly assigned port.
     * The widget is registered with the service for tracking.
     *
     * @return a pair containing the created widget and its assigned port
     * @throws IllegalStateException if platform interactions are disabled or ApplicationManager is unavailable
     */
    fun createTerminalWidget(): Pair<JBTerminalWidget, Int> {
        // Guard against missing platform infrastructure (unit tests)
        if (disablePlatformInteractions || ApplicationManager.getApplication() == null) {
            // For tests, return a dummy pair or throw an exception that tests expect
            throw IllegalStateException(
                if (disablePlatformInteractions) {
                    "Platform interactions disabled for testing"
                } else {
                    "ApplicationManager not available"
                }
            )
        }

        val port = Random.nextInt(PORT_RANGE_MIN, PORT_RANGE_MAX)
        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        val widgetDisposable = com.intellij.openapi.Disposable { }
        com.intellij.openapi.util.Disposer.register(project, widgetDisposable)

        val runner = object : org.jetbrains.plugins.terminal.LocalTerminalDirectRunner(project) {
            override fun configureStartupOptions(
                baseOptions: org.jetbrains.plugins.terminal.ShellStartupOptions
            ): org.jetbrains.plugins.terminal.ShellStartupOptions {
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

    /**
     * Open the OpenCode terminal in the tool window.
     *
     * Activates the OpenCode tool window and optionally sends a file path to the terminal.
     *
     * @param initialFile optional file path to send to the terminal upon activation
     */
    fun openTerminal(initialFile: String? = null) {
        println("OpenCodeService.openTerminal called: initialFile=$initialFile")

        // Guard against missing platform infrastructure (unit tests)
        if (disablePlatformInteractions) {
            println("Skipping openTerminal - platform interactions disabled")
            return
        }

        val openCodeToolWindow = ToolWindowManager.getInstance(project).getToolWindow("OpenCode")
        if (ApplicationManager.getApplication() == null || openCodeToolWindow == null) {
            println("ApplicationManager not available or OpenCode tool window is null - not initialized yet")
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

    /**
     * Add a file path to the OpenCode terminal prompt.
     *
     * Appends the file path to the currently active terminal widget.
     *
     * @param filepath the file path to add
     */
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
                } catch (e: IOException) {
                    LOG.warn("Failed to append prompt to OpenCode terminal", e)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    LOG.warn("Interrupted while appending prompt to OpenCode terminal", e)
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

    companion object {
        private const val HTTP_TIMEOUT_SECONDS = 5L
        private const val PROCESS_WAIT_TIMEOUT_SECONDS = 2L
        private const val CACHE_TTL_MS = 5000L
        private const val MAX_SESSIONS_TO_KEEP = 10
        private const val SERVER_SHUTDOWN_DELAY_MS = 1000L
        private const val PORT_RANGE_MIN = 16384
        private const val PORT_RANGE_MAX = 65536
    }
}
