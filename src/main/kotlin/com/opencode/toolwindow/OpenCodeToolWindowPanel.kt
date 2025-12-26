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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.concurrent.Future
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

private val LOG = logger<OpenCodeToolWindowPanel>()

class OpenCodeToolWindowPanel(
    private val project: Project,
    private val service: OpenCodeService
) : JPanel(BorderLayout()), Disposable, OpenCodeToolWindowViewModel.ViewCallback {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val viewModel = OpenCodeToolWindowViewModel(
        service = service,
        scope = scope
    )
    
    private var widget: JBTerminalWidget? = null
    private var widgetDisposable: Disposable? = null
    private var monitoringJob: Future<*>? = null
    
    init {
        background = UIUtil.getPanelBackground()
        viewModel.setCallback(this)
        viewModel.initialize()
    }
    
    // ============================================
    // ViewCallback Implementation
    // ============================================
    
    override fun onStateChanged(state: OpenCodeToolWindowViewModel.State) {
        // LOG.debug("State changed to: $state")
    }
    
    override fun onPortReady(port: Int) {
        // LOG.info("Port ready: port=$port")
        
        ApplicationManager.getApplication().invokeLater {
            // Check if panel has been disposed before creating terminal
            if (Disposer.isDisposed(this)) {
                // LOG.warn("Panel already disposed, skipping terminal widget creation")
                return@invokeLater
            }
            
            try {
                // Create terminal widget
                val newWidget = createTerminalWidget(port)
                widget = newWidget
                
                // Register widget with service
                service.registerWidget(newWidget, port)
                
                // Show terminal in UI
                removeAll()
                add(newWidget.component, BorderLayout.CENTER)
                revalidate()
                repaint()
                
                // Start monitoring
                startProcessMonitoring()
                
                // LOG.info("OpenCode terminal initialized successfully - port=$port")
            } catch (e: Exception) {
                // LOG.error("Failed to create terminal widget", e)
                showErrorUI("Failed to start OpenCode: ${e.message}")
            }
        }
    }
    
    override fun onError(message: String) {
        // LOG.error("ViewModel error: $message")
        showErrorUI(message)
    }
    
    override fun onProcessExited() {
        // LOG.info("Process exited notification received")
        
        // Cleanup old widget
        cleanupWidget()
        
        val settings = OpenCodeSettings.getInstance()
        if (!settings.state.autoRestartOnExit) {
            // Only show restart UI if auto-restart is disabled
            // (auto-restart will be handled by ViewModel)
            showRestartUI()
        }
    }
    
    // ============================================
    // Terminal Widget Creation and Management
    // ============================================
    
    private fun createTerminalWidget(port: Int): JBTerminalWidget {
        // LOG.info("Creating terminal widget for tool window - port=$port")
        
        // Ensure panel is not disposed before creating widget
        if (Disposer.isDisposed(this)) {
            throw IllegalStateException("Cannot create terminal widget - panel already disposed")
        }
        
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
        
        // LOG.info("JBTerminalWidget created")
        
        return jbWidget
    }
    
    private fun startProcessMonitoring() {
        monitoringJob = ApplicationManager.getApplication().executeOnPooledThread {
            // LOG.debug("Process monitoring thread started for tool window panel")
            
            while (viewModel.getState() == OpenCodeToolWindowViewModel.State.RUNNING) {
                Thread.sleep(1000)
                
                val isAlive = checkIfTerminalAlive()
                
                if (!isAlive) {
                    // LOG.info("OpenCode terminal process has exited")
                    
                    ApplicationManager.getApplication().invokeLater {
                        handleProcessExit()
                    }
                    break
                }
            }
            
            // LOG.debug("Process monitoring thread stopped for tool window panel")
        }
    }
    
    private fun checkIfTerminalAlive(): Boolean {
        val currentWidget = widget ?: return false
        
        // Strategy 1: Check TTY connector
        try {
            val ttyConnector = currentWidget.ttyConnector
            if (ttyConnector != null) {
                val isConnected = ttyConnector.isConnected
                // LOG.debug("TTY connector check: isConnected=$isConnected")
                return isConnected
            }
        } catch (e: Exception) {
            // LOG.warn("Failed to check TTY connector status", e)
        }
        
        // Strategy 2: Fallback - delegate to ViewModel for server health check
        scope.launch {
            viewModel.checkServerHealth()
        }
        
        // Return based on server port availability
        return viewModel.getCurrentPort() != null
    }
    
    private fun handleProcessExit() {
        if (viewModel.getState() != OpenCodeToolWindowViewModel.State.RUNNING) {
            // LOG.debug("handleProcessExit called but state is ${viewModel.getState()}, ignoring")
            return
        }
        
        val settings = OpenCodeSettings.getInstance()
        viewModel.handleProcessExit(settings.state.autoRestartOnExit)
    }
    
    // ============================================
    // UI Display Methods
    // ============================================
    
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
            
            // LOG.info("Restart UI displayed for tool window panel")
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
        // LOG.info("Restarting OpenCode terminal (user request)")
        
        // Stop monitoring
        viewModel.stopProcessMonitoring()
        monitoringJob?.cancel(true)
        monitoringJob = null
        
        // Cleanup old widget
        cleanupWidget()
        
        // Delegate restart to ViewModel
        viewModel.restart()
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
        // LOG.info("OpenCodeToolWindowPanel disposed")
        
        // Stop monitoring
        viewModel.stopProcessMonitoring()
        monitoringJob?.cancel(true)
        monitoringJob = null
        
        // Cleanup widget
        cleanupWidget()
        
        // Dispose ViewModel
        viewModel.dispose()
        
        // Cancel coroutine scope
        scope.cancel()
    }
}
