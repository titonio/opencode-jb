package com.opencode.editor

import com.intellij.openapi.components.service
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
import kotlinx.coroutines.runBlocking
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import java.awt.BorderLayout

class OpenCodeFileEditor(
    private val project: Project,
    private val file: VirtualFile
) : UserDataHolderBase(), FileEditor {
    
    private var widget: JBTerminalWidget? = null
    private var currentSessionId: String? = null
    private var serverPort: Int? = null
    private var isInitialized = false
    private var isOpencodeAvailable: Boolean = false

    init {
        println("OpenCodeFileEditor created for file: ${file.name}")
        
        val service = project.service<OpenCodeService>()
        
        // Check if OpenCode is installed
        isOpencodeAvailable = service.isOpencodeInstalled()
        if (!isOpencodeAvailable) {
            showOpencodeNotInstalledDialog()
        } else {
            // If file is OpenCodeVirtualFile and has a sessionId, use it
            if (file is OpenCodeVirtualFile && file.sessionId != null) {
                currentSessionId = file.sessionId
                println("OpenCodeFileEditor: using session from VirtualFile: ${file.sessionId}")
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
     */
    private fun initializeWidget() {
        if (isInitialized || widget != null || !isOpencodeAvailable) return
        
        val service = project.service<OpenCodeService>()
        
        // Start server if needed
        serverPort = service.getOrStartSharedServer()
        if (serverPort == null) {
            Messages.showErrorDialog(
                project,
                "Failed to start OpenCode server after multiple attempts.",
                "OpenCode Error"
            )
            return
        }
        
        // Handle session ID
        if (currentSessionId != null) {
            // Verify restored session exists
            val sessionExists = runBlocking {
                service.getSession(currentSessionId!!) != null
            }
            
            if (!sessionExists) {
                Messages.showWarningDialog(
                    project,
                    "Previous session no longer exists. Creating new session.",
                    "Session Not Found"
                )
                currentSessionId = null
            }
        }
        
        // Create new session if needed
        if (currentSessionId == null) {
            val newSession = runBlocking {
                try {
                    service.createSession(null) // Let OpenCode auto-generate title
                } catch (e: Exception) {
                    null
                }
            }
            currentSessionId = newSession
        }
        
        // If we still don't have a session, we can't continue
        if (currentSessionId == null) {
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
        
        println("OpenCodeFileEditor: initialized with session $currentSessionId on port $serverPort")
    }
    
    private fun createTerminalForSession(port: Int, sessionId: String): JBTerminalWidget {
        val widgetDisposable = com.intellij.openapi.Disposable { }
        com.intellij.openapi.util.Disposer.register(project, widgetDisposable)

        val runner = object : org.jetbrains.plugins.terminal.LocalTerminalDirectRunner(project) {
            override fun configureStartupOptions(
                baseOptions: org.jetbrains.plugins.terminal.ShellStartupOptions
            ): org.jetbrains.plugins.terminal.ShellStartupOptions {
                val envs = mutableMapOf<String, String>()
                envs["OPENCODE_CALLER"] = "intellij"
                
                return baseOptions.builder()
                    .shellCommand(listOf(
                        "opencode",
                        "attach",
                        "http://localhost:$port",
                        "--session", sessionId,
                        "--dir", project.basePath ?: System.getProperty("user.home")
                    ))
                    .envVariables(envs)
                    .build()
            }
        }

        val startupOptions = org.jetbrains.plugins.terminal.ShellStartupOptions.Builder()
            .workingDirectory(project.basePath ?: System.getProperty("user.home"))
            .build()
            
        val terminalWidget = runner.startShellTerminalWidget(widgetDisposable, startupOptions, true)
        
        return com.intellij.terminal.JBTerminalWidget.asJediTermWidget(terminalWidget)
            ?: throw IllegalStateException("Failed to create JBTerminalWidget")
    }

    override fun getComponent(): JComponent {
        if (!isInitialized) {
            initializeWidget()
        }
        return widget?.component ?: createPlaceholderPanel()
    }
    
    private fun createPlaceholderPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(JLabel("OpenCode is not available"), BorderLayout.CENTER)
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        if (!isInitialized) {
            initializeWidget()
        }
        return widget?.preferredFocusableComponent
    }

    override fun getName(): String = "OpenCode"

    /**
     * Restore state after tab drag/move.
     */
    override fun setState(state: FileEditorState) {
        if (state is OpenCodeEditorState) {
            currentSessionId = state.sessionId
            serverPort = state.serverPort
            println("OpenCodeFileEditor: restored state - session=${state.sessionId}, port=${state.serverPort}")
        }
    }

    /**
     * Get current state for serialization.
     */
    override fun getState(level: FileEditorStateLevel): FileEditorState {
        return OpenCodeEditorState(
            sessionId = currentSessionId,
            serverPort = serverPort
        )
    }

    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        println("OpenCodeFileEditor disposed for session $currentSessionId")
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
