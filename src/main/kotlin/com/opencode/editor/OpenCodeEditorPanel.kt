package com.opencode.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalWidget
import com.intellij.util.ui.UIUtil
import com.opencode.service.OpenCodeService
import com.opencode.settings.OpenCodeSettings
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.concurrent.Future
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

private val LOG = logger<OpenCodeEditorPanel>()

class OpenCodeEditorPanel(
    private val project: Project,
    private var sessionId: String?,
    private var serverPort: Int?,
    private val onSessionChanged: (sessionId: String?, port: Int?) -> Unit
) : JPanel(BorderLayout()), Disposable {
    
    private enum class State {
        INITIALIZING, RUNNING, EXITED, RESTARTING
    }
    
    @Volatile
    private var currentState: State = State.INITIALIZING
    
    private var widget: JBTerminalWidget? = null
    private var widgetDisposable: Disposable? = null
    private var monitoringJob: Future<*>? = null
    
    @Volatile
    private var isMonitoring = false
    
    private val service = project.getService(OpenCodeService::class.java)
    
    init {
        background = UIUtil.getPanelBackground()
        initializeTerminal()
    }
    
    private fun initializeTerminal() {
        LOG.info("Initializing OpenCode editor terminal")
        currentState = State.INITIALIZING
        
        ApplicationManager.getApplication().invokeLater {
            try {
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
                        showErrorUI("Failed to start OpenCode server after multiple attempts")
                        return@invokeLater
                    }
                    LOG.info("Server started on port $serverPort")
                }
                
                // Handle session ID
                if (sessionId != null) {
                    LOG.debug("Verifying restored session: $sessionId")
                    val sessionExists = runBlocking {
                        service.getSession(sessionId!!) != null
                    }
                    
                    if (!sessionExists) {
                        LOG.warn("Session $sessionId no longer exists, creating new session")
                        sessionId = null
                    } else {
                        LOG.info("Using restored session: $sessionId")
                    }
                }
                
                // Create new session if needed
                if (sessionId == null) {
                    val newSession = runBlocking {
                        try {
                            service.createSession(null)
                        } catch (e: Exception) {
                            thisLogger().error("Failed to create session", e)
                            null
                        }
                    }
                    sessionId = newSession
                    LOG.info("New session created: $sessionId")
                }
                
                // If we still don't have a session, we can't continue
                if (sessionId == null) {
                    LOG.error("No session available after initialization attempts")
                    showErrorUI("Failed to create or retrieve OpenCode session")
                    return@invokeLater
                }
                
                // Notify parent of session/port changes
                onSessionChanged(sessionId, serverPort)
                
                // Create terminal
                val newWidget = createTerminalForSession(serverPort!!, sessionId!!)
                widget = newWidget
                
                // Register widget with service
                service.registerWidget(newWidget, serverPort!!)
                
                // Show terminal in UI
                removeAll()
                add(newWidget.component, BorderLayout.CENTER)
                revalidate()
                repaint()
                
                // Start monitoring
                currentState = State.RUNNING
                startProcessMonitoring()
                
                LOG.info("OpenCode editor terminal initialized successfully - session=$sessionId port=$serverPort")
            } catch (e: Exception) {
                LOG.error("Failed to initialize OpenCode editor terminal", e)
                showErrorUI("Failed to initialize OpenCode: ${e.message}")
            }
        }
    }
    
    private fun createTerminalForSession(port: Int, sessionId: String): JBTerminalWidget {
        LOG.info("Creating terminal widget for editor - port=$port, session=$sessionId")
        
        val widgetDisposable = Disposable { }
        Disposer.register(this, widgetDisposable)
        this.widgetDisposable = widgetDisposable
        
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
        
        val jbWidget = JBTerminalWidget.asJediTermWidget(terminalWidget)
            ?: throw IllegalStateException("Failed to create JBTerminalWidget")
        
        LOG.info("JBTerminalWidget created")
        
        return jbWidget
    }
    
    private fun startProcessMonitoring() {
        isMonitoring = true
        
        monitoringJob = ApplicationManager.getApplication().executeOnPooledThread {
            LOG.debug("Process monitoring thread started for editor panel")
            
            while (isMonitoring && currentState == State.RUNNING) {
                Thread.sleep(1000)
                
                val isAlive = checkIfTerminalAlive()
                
                if (!isAlive) {
                    LOG.info("OpenCode editor terminal process has exited")
                    
                    ApplicationManager.getApplication().invokeLater {
                        handleProcessExit()
                    }
                    break
                }
            }
            
            LOG.debug("Process monitoring thread stopped for editor panel")
        }
    }
    
    private fun checkIfTerminalAlive(): Boolean {
        val currentWidget = widget ?: return false
        
        // Strategy 1: Check TTY connector
        try {
            val ttyConnector = currentWidget.ttyConnector
            if (ttyConnector != null) {
                val isConnected = ttyConnector.isConnected
                LOG.debug("TTY connector check: isConnected=$isConnected")
                return isConnected
            }
        } catch (e: Exception) {
            LOG.warn("Failed to check TTY connector status", e)
        }
        
        // Strategy 2: Fallback - check if server is still running
        val port = serverPort ?: return false
        val serverAlive = service.isServerRunning(port)
        LOG.debug("Server health check: port=$port, alive=$serverAlive")
        return serverAlive
    }
    
    private fun handleProcessExit() {
        if (currentState != State.RUNNING) {
            LOG.debug("handleProcessExit called but state is $currentState, ignoring")
            return
        }
        
        currentState = State.EXITED
        isMonitoring = false
        
        // Cleanup old widget
        cleanupWidget()
        
        val settings = OpenCodeSettings.getInstance()
        if (settings.state.autoRestartOnExit) {
            LOG.info("Auto-restart enabled, restarting editor terminal")
            restartTerminal()
        } else {
            LOG.info("Auto-restart disabled, showing restart UI")
            showRestartUI()
        }
    }
    
    private fun showRestartUI() {
        ApplicationManager.getApplication().invokeLater {
            removeAll()
            
            val panel = JPanel(GridBagLayout())
            panel.background = UIUtil.getPanelBackground()
            
            val gbc = GridBagConstraints()
            gbc.gridx = 0
            gbc.gridy = 0
            gbc.insets = Insets(10, 10, 5, 10)
            gbc.anchor = GridBagConstraints.CENTER
            
            // Message label
            val messageLabel = JLabel("OpenCode has stopped running")
            panel.add(messageLabel, gbc)
            
            // Session info
            if (sessionId != null) {
                gbc.gridy = 1
                gbc.insets = Insets(5, 10, 10, 10)
                val sessionLabel = JLabel("<html><font size='-2' color='gray'>Session: $sessionId</font></html>")
                panel.add(sessionLabel, gbc)
            }
            
            // Restart button
            gbc.gridy = 2
            gbc.insets = Insets(10, 10, 20, 10)
            val restartButton = JButton("Restart OpenCode")
            restartButton.addActionListener {
                restartTerminal()
            }
            panel.add(restartButton, gbc)
            
            add(panel, BorderLayout.CENTER)
            revalidate()
            repaint()
            
            LOG.info("Restart UI displayed for editor panel")
        }
    }
    
    private fun showErrorUI(message: String) {
        ApplicationManager.getApplication().invokeLater {
            removeAll()
            
            val panel = JPanel(GridBagLayout())
            panel.background = UIUtil.getPanelBackground()
            
            val gbc = GridBagConstraints()
            gbc.gridx = 0
            gbc.gridy = 0
            gbc.insets = Insets(10, 10, 10, 10)
            gbc.anchor = GridBagConstraints.CENTER
            
            val errorLabel = JLabel("<html><center>$message</center></html>")
            panel.add(errorLabel, gbc)
            
            add(panel, BorderLayout.CENTER)
            revalidate()
            repaint()
        }
    }
    
    private fun restartTerminal() {
        if (currentState == State.RESTARTING) {
            LOG.warn("Restart already in progress, ignoring duplicate request")
            return
        }
        
        LOG.info("Restarting OpenCode editor terminal (user request)")
        currentState = State.RESTARTING
        
        // Stop monitoring
        isMonitoring = false
        monitoringJob?.cancel(true)
        monitoringJob = null
        
        // Cleanup old widget
        cleanupWidget()
        
        // Start new terminal (will reconnect to same session)
        initializeTerminal()
    }
    
    private fun cleanupWidget() {
        widget?.let { w ->
            service.unregisterWidget(w)
        }
        widget = null
        
        widgetDisposable?.let { d ->
            if (!Disposer.isDisposed(d)) {
                Disposer.dispose(d)
            }
        }
        widgetDisposable = null
    }
    
    override fun dispose() {
        LOG.info("OpenCodeEditorPanel disposed")
        
        // Stop monitoring
        isMonitoring = false
        monitoringJob?.cancel(true)
        monitoringJob = null
        
        // Cleanup widget
        cleanupWidget()
    }
}
