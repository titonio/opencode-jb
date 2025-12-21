package com.opencode.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.JBTerminalWidget
import com.opencode.service.OpenCodeService
import com.opencode.vfs.OpenCodeVirtualFile
import kotlinx.coroutines.runBlocking
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import java.awt.BorderLayout

private val LOG = logger<OpenCodeFileEditor>()

class OpenCodeFileEditor(
    private val project: Project,
    private val file: OpenCodeVirtualFile
) : UserDataHolderBase(), FileEditor {
    
    private var widget: JBTerminalWidget? = null
    private var currentSessionId: String? = null
    private var serverPort: Int? = null
    private var isInitialized = false
    private var isOpencodeAvailable: Boolean = false
    private var stateWasRestored = false  // Track if setState() was called
    private var firstGetComponentCalled = false  // Track first getComponent() call
    
    // Use a container panel that we can dynamically update
    private val containerPanel = JPanel(BorderLayout())
    private val placeholderPanel by lazy { createPlaceholderPanel() }

    init {
        LOG.info("OpenCodeFileEditor created for ${file.name}")
        
        val service = project.service<OpenCodeService>()
        
        // Check if OpenCode is installed
        isOpencodeAvailable = service.isOpencodeInstalled()
        if (!isOpencodeAvailable) {
            LOG.warn("OpenCode CLI not installed")
            showOpencodeNotInstalledDialog()
        } else {
            // If file is OpenCodeVirtualFile and has a sessionId, use it
            if (file is OpenCodeVirtualFile && file.sessionId != null) {
                currentSessionId = file.sessionId
                LOG.debug("Session provided via VirtualFile: ${file.sessionId}")
            }
            
            service.registerActiveEditor(file)
        }
    }
    
    private fun showOpencodeNotInstalledDialog() {
        Messages.showErrorDialog(
            project,
            """
            OpenCode CLI is not installed or not found in PATH.
            
            Please install OpenCode:
            1. Visit: https://opencode.ai/install
            2. Follow installation instructions
            3. Restart IntelliJ IDEA
            """.trimIndent(),
            "OpenCode Not Installed"
        )
    }

    /**
     * Initialize the terminal widget with a session.
     * This is called from selectNotify() when the tab is shown.
     */
    private fun initializeWidget() {
        if (isInitialized || widget != null || !isOpencodeAvailable) return
        
        LOG.info("Initializing OpenCode widget")
        if (LOG.isDebugEnabled) {
            LOG.debug("  currentSessionId: $currentSessionId")
            LOG.debug("  serverPort: $serverPort")
        }
        
        val service = project.service<OpenCodeService>()
        
        // Handle server port restoration
        if (serverPort != null) {
            if (!service.isServerRunning(serverPort!!)) {
                LOG.warn("Restored server port $serverPort not running, will start new server")
                serverPort = null
            } else {
                LOG.info("Reusing existing server on port $serverPort")
            }
        }
        
        // Start server if needed
        if (serverPort == null) {
            serverPort = service.getOrStartSharedServer()
            if (serverPort == null) {
                LOG.error("Failed to start OpenCode server")
                Messages.showErrorDialog(
                    project,
                    "Failed to start OpenCode server after multiple attempts.",
                    "OpenCode Error"
                )
                return
            }
            LOG.info("Server started on port $serverPort")
        }
        
        // Handle session ID
        if (currentSessionId != null) {
            LOG.debug("Verifying restored session: $currentSessionId")
            val sessionExists = runBlocking {
                service.getSession(currentSessionId!!) != null
            }
            
            if (!sessionExists) {
                LOG.warn("Session $currentSessionId no longer exists, creating new session")
                Messages.showWarningDialog(
                    project,
                    "Previous session no longer exists. Creating new session.",
                    "Session Not Found"
                )
                currentSessionId = null
            } else {
                LOG.info("Using restored session: $currentSessionId")
            }
        }
        
        // Create new session if needed
        if (currentSessionId == null) {
            val newSession = runBlocking {
                try {
                    service.createSession(null) // Let OpenCode auto-generate title
                } catch (e: Exception) {
                    thisLogger().error("Failed to create session", e)
                    null
                }
            }
            currentSessionId = newSession
            LOG.info("New session created: $currentSessionId")
        }
        
        // If we still don't have a session, we can't continue
        if (currentSessionId == null) {
            LOG.error("No session available after initialization attempts")
            Messages.showErrorDialog(
                project,
                "Failed to create or retrieve OpenCode session.",
                "OpenCode Error"
            )
            return
        }
        
        // Create terminal
        widget = createTerminalForSession(serverPort!!, currentSessionId!!)
        isInitialized = true
        
        LOG.info("OpenCode widget initialized successfully - session=$currentSessionId port=$serverPort")
    }
    
    private fun createTerminalForSession(port: Int, sessionId: String): JBTerminalWidget {
        LOG.info("Creating terminal widget - port=$port, session=$sessionId")
        
        val widgetDisposable = com.intellij.openapi.Disposable { }
        com.intellij.openapi.util.Disposer.register(project, widgetDisposable)

        val runner = object : org.jetbrains.plugins.terminal.LocalTerminalDirectRunner(project) {
            override fun configureStartupOptions(
                baseOptions: org.jetbrains.plugins.terminal.ShellStartupOptions
            ): org.jetbrains.plugins.terminal.ShellStartupOptions {
                val envs = mutableMapOf<String, String>()
                envs["OPENCODE_CALLER"] = "intellij"
                
                val command = listOf(
                    "opencode",
                    "attach",
                    "http://localhost:$port",
                    "--session", sessionId,
                    "--dir", project.basePath ?: System.getProperty("user.home")
                )
                LOG.info("Terminal command configured: ${command.joinToString(" ")}")
                
                return baseOptions.builder()
                    .shellCommand(command)
                    .envVariables(envs)
                    .build()
            }
        }

        val startupOptions = org.jetbrains.plugins.terminal.ShellStartupOptions.Builder()
            .workingDirectory(project.basePath ?: System.getProperty("user.home"))
            .build()
        
        LOG.info("Starting shell terminal widget...")
        val terminalWidget = runner.startShellTerminalWidget(widgetDisposable, startupOptions, true)
        LOG.info("Shell terminal widget started: ${terminalWidget.javaClass.simpleName}")
        
        val jbWidget = com.intellij.terminal.JBTerminalWidget.asJediTermWidget(terminalWidget)
            ?: throw IllegalStateException("Failed to create JBTerminalWidget")
        
        LOG.info("JBTerminalWidget created, component=${jbWidget.component?.javaClass?.simpleName}, isComponentNull=${jbWidget.component == null}")
        
        return jbWidget
    }

    override fun getComponent(): JComponent {
        val widgetComponent = widget?.component
        val hasWidget = widgetComponent != null
        val isFirstCall = !firstGetComponentCalled
        
        if (LOG.isDebugEnabled) {
            LOG.debug("getComponent() called - initialized=$isInitialized, stateWasRestored=$stateWasRestored, isFirstCall=$isFirstCall, widgetNull=${widget == null}, componentNull=${widgetComponent == null}, hasWidget=$hasWidget")
        }
        
        if (isFirstCall) {
            firstGetComponentCalled = true
            
            // Initialize the container with placeholder
            containerPanel.removeAll()
            containerPanel.add(placeholderPanel, BorderLayout.CENTER)
            
            // On first getComponent() call:
            // - If setState() was already called (stateWasRestored=true), wait for selectNotify()
            // - If setState() was NOT called yet, this is a fresh open - initialize immediately
            if (!stateWasRestored && !isInitialized && isOpencodeAvailable) {
                LOG.info("First getComponent() without prior setState() - initializing immediately for fresh open")
                initializeWidget()
                
                // Update container with actual widget
                if (widget?.component != null) {
                    containerPanel.removeAll()
                    containerPanel.add(widget!!.component, BorderLayout.CENTER)
                    containerPanel.revalidate()
                    containerPanel.repaint()
                }
            } else if (LOG.isDebugEnabled) {
                LOG.debug("First getComponent() after setState() - will initialize in selectNotify()")
            }
        }
        
        return containerPanel
    }
    
    private fun createPlaceholderPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val message = if (!isOpencodeAvailable) {
            "OpenCode CLI is not installed. Please install from https://opencode.ai/install"
        } else {
            "Loading OpenCode terminal..."
        }
        panel.add(JLabel(message), BorderLayout.CENTER)
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        LOG.debug("getPreferredFocusedComponent() called")
        return widget?.preferredFocusableComponent
    }

    override fun getName(): String = "OpenCode"

    /**
     * Called when the editor tab is selected/shown.
     * This is called AFTER setState() and is the proper place to initialize.
     */
    override fun selectNotify() {
        LOG.info("OpenCode tab selected")
        if (LOG.isDebugEnabled) {
            LOG.debug("  isInitialized: $isInitialized")
            LOG.debug("  stateWasRestored: $stateWasRestored")
            LOG.debug("  currentSessionId: $currentSessionId")
            LOG.debug("  serverPort: $serverPort")
        }
        
        // Always initialize on selectNotify if not already initialized
        // By this point, setState() has already been called (if it will be called)
        if (!isInitialized && isOpencodeAvailable) {
            initializeWidget()
            
            // Update the container panel with the actual terminal widget
            widget?.component?.let { widgetComponent ->
                LOG.info("Updating container panel with terminal widget after initialization")
                ApplicationManager.getApplication().invokeLater {
                    containerPanel.removeAll()
                    containerPanel.add(widgetComponent, BorderLayout.CENTER)
                    containerPanel.revalidate()
                    containerPanel.repaint()
                    LOG.info("Container panel updated successfully")
                }
            } ?: LOG.error("Widget component is null after initialization!")
        }
    }

    /**
     * Called when the editor tab is deselected/hidden.
     */
    override fun deselectNotify() {
        LOG.debug("OpenCode tab deselected")
    }

    /**
     * Restore state after tab drag/move.
     * This is called BEFORE getComponent() during state restoration.
     */
    override fun setState(state: FileEditorState) {
        stateWasRestored = true  // Mark that we're in a restoration scenario
        
        if (state is OpenCodeEditorState) {
            currentSessionId = state.sessionId
            serverPort = state.serverPort
            LOG.info("State restored - session=${state.sessionId} port=${state.serverPort}")
        } else {
            LOG.debug("setState() called with unexpected type: ${state.javaClass.simpleName}")
        }
    }

    /**
     * Get current state for serialization.
     * Called during tab drag/move to save state.
     */
    override fun getState(level: FileEditorStateLevel): FileEditorState {
        val state = OpenCodeEditorState(
            sessionId = currentSessionId,
            serverPort = serverPort
        )
        LOG.info("State saved for serialization - session=$currentSessionId port=$serverPort")
        LOG.debug("State level: $level")
        return state
    }

    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        LOG.info("OpenCode editor disposed - session=$currentSessionId")
        val service = project.service<OpenCodeService>()
        service.unregisterActiveEditor(file)
        
        widget?.let { service.unregisterWidget(it) }
    }
    
    override fun getFile(): VirtualFile = file
    
    /**
     * Get current session ID (for actions/UI).
     */
    fun getCurrentSessionId(): String? = currentSessionId
}

/**
 * Serializable editor state.
 */
data class OpenCodeEditorState(
    val sessionId: String?,
    val serverPort: Int?
) : FileEditorState {
    override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean {
        return otherState is OpenCodeEditorState && otherState.sessionId == sessionId
    }
}
