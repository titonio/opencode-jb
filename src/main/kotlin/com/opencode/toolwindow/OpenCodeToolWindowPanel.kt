package com.opencode.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalWidget
import com.intellij.util.ui.UIUtil
import com.opencode.service.OpenCodeService
import com.opencode.settings.OpenCodeSettings
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.concurrent.Future
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.random.Random

private val LOG = logger<OpenCodeToolWindowPanel>()

class OpenCodeToolWindowPanel(
    private val project: Project,
    private val service: OpenCodeService
) : JPanel(BorderLayout()), Disposable {
    
    private enum class State {
        INITIALIZING, RUNNING, EXITED, RESTARTING
    }
    
    @Volatile
    private var currentState: State = State.INITIALIZING
    
    private var widget: JBTerminalWidget? = null
    private var widgetDisposable: Disposable? = null
    private var monitoringJob: Future<*>? = null
    private var currentPort: Int? = null
    
    @Volatile
    private var isMonitoring = false
    
    init {
        background = UIUtil.getPanelBackground()
        startTerminal()
    }
    
    private fun startTerminal() {
        LOG.info("Starting OpenCode terminal in tool window")
        currentState = State.INITIALIZING
        
        ApplicationManager.getApplication().invokeLater {
            try {
                val (newWidget, port) = createTerminalWidget()
                widget = newWidget
                currentPort = port
                
                // Register widget with service
                service.registerWidget(newWidget, port)
                
                // Show terminal in UI
                removeAll()
                add(newWidget.component, BorderLayout.CENTER)
                revalidate()
                repaint()
                
                // Start monitoring
                currentState = State.RUNNING
                startProcessMonitoring()
                
                LOG.info("OpenCode terminal started successfully on port $port")
            } catch (e: Exception) {
                LOG.error("Failed to start OpenCode terminal", e)
                showErrorUI("Failed to start OpenCode: ${e.message}")
            }
        }
    }
    
    private fun createTerminalWidget(): Pair<JBTerminalWidget, Int> {
        val port = Random.nextInt(16384, 65536)
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        
        val widgetDisposable = Disposable { }
        Disposer.register(this, widgetDisposable)
        this.widgetDisposable = widgetDisposable
        
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
        val jbWidget = JBTerminalWidget.asJediTermWidget(terminalWidget)
            ?: throw IllegalStateException("Failed to create JBTerminalWidget")
        
        return Pair(jbWidget, port)
    }
    
    private fun startProcessMonitoring() {
        isMonitoring = true
        
        monitoringJob = ApplicationManager.getApplication().executeOnPooledThread {
            LOG.debug("Process monitoring thread started")
            
            while (isMonitoring && currentState == State.RUNNING) {
                Thread.sleep(1000)
                
                val isAlive = checkIfTerminalAlive()
                
                if (!isAlive) {
                    LOG.info("OpenCode terminal process has exited")
                    
                    ApplicationManager.getApplication().invokeLater {
                        handleProcessExit()
                    }
                    break
                }
            }
            
            LOG.debug("Process monitoring thread stopped")
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
        
        // Strategy 2: Fallback - check if port is still responding
        val port = currentPort ?: return false
        val portAlive = service.isServerRunning(port)
        LOG.debug("Port health check: port=$port, alive=$portAlive")
        return portAlive
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
            LOG.info("Auto-restart enabled, restarting terminal")
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
            gbc.insets = Insets(10, 10, 10, 10)
            gbc.anchor = GridBagConstraints.CENTER
            
            // Message label
            val messageLabel = JLabel("OpenCode has stopped running")
            panel.add(messageLabel, gbc)
            
            // Restart button
            gbc.gridy = 1
            gbc.insets = Insets(10, 10, 20, 10)
            val restartButton = JButton("Restart OpenCode")
            restartButton.addActionListener {
                restartTerminal()
            }
            panel.add(restartButton, gbc)
            
            add(panel, BorderLayout.CENTER)
            revalidate()
            repaint()
            
            LOG.info("Restart UI displayed")
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
        
        LOG.info("Restarting OpenCode terminal (manual request)")
        currentState = State.RESTARTING
        
        // Stop monitoring
        isMonitoring = false
        monitoringJob?.cancel(true)
        monitoringJob = null
        
        // Cleanup old widget
        cleanupWidget()
        
        // Start new terminal
        startTerminal()
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
        
        currentPort = null
    }
    
    override fun dispose() {
        LOG.info("OpenCodeToolWindowPanel disposed")
        
        // Stop monitoring
        isMonitoring = false
        monitoringJob?.cancel(true)
        monitoringJob = null
        
        // Cleanup widget
        cleanupWidget()
    }
}
