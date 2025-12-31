package com.opencode.editor

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
import java.io.IOException
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

private val LOG = logger<OpenCodeEditorPanel>()

/**
 * Main editor panel for OpenCode integration in IntelliJ IDEA.
 *
 * This panel provides a Swing-based UI that embeds an OpenCode terminal session,
 * allowing users to interact with the OpenCode AI assistant directly within the IDE.
 * It manages the lifecycle of the terminal widget, monitors process health, and
 * handles state changes through a ViewModel-based architecture.
 *
 * The panel implements the ViewCallback interface to receive notifications from
 * the ViewModel about state changes, session/port availability, errors, and
 * process lifecycle events.
 *
 * @param project The IntelliJ project this panel is associated with
 * @param sessionId Optional existing session ID to connect to
 * @param serverPort Optional server port for the OpenCode instance
 * @param onSessionChanged Callback invoked when session or port changes
 */
class OpenCodeEditorPanel(
    private val project: Project,
    sessionId: String?,
    serverPort: Int?,
    private val onSessionChanged: (sessionId: String?, port: Int?) -> Unit
) : JPanel(BorderLayout()), Disposable, OpenCodeEditorPanelViewModel.ViewCallback {

    private val service = project.getService(OpenCodeService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val viewModel = OpenCodeEditorPanelViewModel(
        service = service,
        projectBasePath = project.basePath,
        scope = scope,
        initialSessionId = sessionId,
        initialServerPort = serverPort
    )

    private var widget: JBTerminalWidget? = null
    private var widgetDisposable: Disposable? = null
    private var monitoringJob: java.util.concurrent.Future<*>? = null

    init {
        background = UIUtil.getPanelBackground()
        viewModel.setCallback(this)
        viewModel.initialize()
    }

    // ============================================
    // ViewCallback Implementation
    // ============================================

    /**
     * Handles state changes from the ViewModel.
     *
     * Currently only logs state changes for debugging purposes.
     *
     * @param state The new state of the editor panel
     */
    override fun onStateChanged(state: OpenCodeEditorPanelViewModel.State) {
        LOG.debug("State changed to: $state")
    }

    /**
     * Handles session and port readiness notification.
     *
     * Creates and configures the terminal widget when a session and port are available.
     * Registers the widget with the OpenCodeService and starts process monitoring.
     * Skips terminal widget creation in headless environments (e.g., during tests).
     *
     * @param sessionId The OpenCode session identifier
     * @param port The server port for the OpenCode instance
     */
    override fun onSessionAndPortReady(sessionId: String, port: Int) {
        LOG.info("Session and port ready: session=$sessionId, port=$port")

        val application = ApplicationManager.getApplication()
        if (application == null) {
            LOG.warn("No application instance (likely test); skipping terminal widget creation")
            onSessionChanged(sessionId, port)
            return
        }

        application.invokeLater {
            try {
                // Notify parent of session/port changes
                onSessionChanged(sessionId, port)

                // In headless environments (e.g., tests), skip terminal creation
                if (application.isHeadlessEnvironment) {
                    LOG.warn("Headless environment detected; skipping terminal widget creation")
                    return@invokeLater
                }

                // Create terminal widget
                val newWidget = createTerminalForSession(port, sessionId)
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

                LOG.info("OpenCode editor terminal initialized successfully - session=$sessionId port=$port")
            } catch (e: IOException) {
                LOG.error("Failed to create terminal widget", e)
                showErrorUI("Failed to initialize OpenCode: ${e.message}")
            }
        }
    }

    /**
     * Handles error notifications from the ViewModel.
     *
     * Displays an error UI with the provided message to inform the user
     * of any issues that occurred during operation.
     *
     * @param message The error message to display
     */
    override fun onError(message: String) {
        LOG.error("ViewModel error: $message")
        showErrorUI(message)
    }

    /**
     * Handles process exit notification.
     *
     * Cleans up the terminal widget when the OpenCode process exits.
     * If auto-restart is disabled in settings, displays a restart UI
     * to allow the user to manually restart the session.
     */
    override fun onProcessExited() {
        LOG.info("Process exited notification received")

        // Cleanup old widget
        cleanupWidget()

        val settings = OpenCodeSettings.getInstance()
        if (!settings.state.autoRestartOnExit) {
            // Only show restart UI if auto-restart is disabled
            // (auto-restart will be handled by ViewModel)
            showRestartUI()
        }
    }

    /**
     * Handles terminal alive status notifications.
     *
     * Currently only logs the status. Could be extended to display
     * visual indicators of terminal health in the UI.
     *
     * @param isAlive Whether the terminal process is currently alive
     */
    override fun onTerminalAlive(isAlive: Boolean) {
        // Optional: Could be used for UI indicators
        LOG.debug("Terminal alive status: $isAlive")
    }

    // ============================================
    // Terminal Widget Creation and Management
    // ============================================

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
                    "--session",
                    sessionId,
                    "--dir",
                    project.basePath ?: System.getProperty("user.home")
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
        val terminalWidget = runner.startShellTerminalWidget(
            widgetDisposable,
            startupOptions,
            FORCE_PTY_ALLOC
        )
        LOG.info("Shell terminal widget started: ${terminalWidget.javaClass.simpleName}")

        val jbWidget = JBTerminalWidget.asJediTermWidget(terminalWidget)
            ?: throw IllegalStateException("Failed to create JBTerminalWidget")

        LOG.info("JBTerminalWidget created")

        return jbWidget
    }

    private fun startProcessMonitoring() {
        viewModel.startProcessMonitoring()

        monitoringJob = ApplicationManager.getApplication().executeOnPooledThread {
            LOG.debug("Process monitoring thread started for editor panel")

            try {
                while (viewModel.isMonitoring && viewModel.getState() == OpenCodeEditorPanelViewModel.State.RUNNING) {
                    Thread.sleep(MONITORING_INTERVAL_MS)

                    val isAlive = checkIfTerminalAlive()

                    if (!isAlive) {
                        LOG.info("OpenCode editor terminal process has exited")

                        ApplicationManager.getApplication().invokeLater {
                            handleProcessExit()
                        }
                        break
                    }
                }
            } catch (e: InterruptedException) {
                LOG.debug("Process monitoring thread interrupted")
            }

            LOG.debug("Process monitoring thread stopped for editor panel")
        }
    }

    private fun checkIfTerminalAlive(): Boolean {
        val currentWidget = widget
        if (currentWidget == null) {
            return false
        }

        // Strategy 1: Check TTY connector
        return try {
            val ttyConnector = currentWidget.ttyConnector
            if (ttyConnector != null) {
                val isConnected = ttyConnector.isConnected
                LOG.debug("TTY connector check: isConnected=$isConnected")
                isConnected
            } else {
                // Strategy 2: Fallback - delegate to ViewModel for server health check
                scheduleServerHealthCheck()
                viewModel.serverPort != null
            }
        } catch (e: IOException) {
            LOG.warn("Failed to check TTY connector status", e)
            scheduleServerHealthCheck()
            viewModel.serverPort != null
        }
    }

    private fun scheduleServerHealthCheck() {
        scope.launch {
            viewModel.checkIfTerminalAlive()
        }
    }

    private fun handleProcessExit() {
        if (viewModel.getState() != OpenCodeEditorPanelViewModel.State.RUNNING) {
            LOG.debug("handleProcessExit called but state is ${viewModel.getState()}, ignoring")
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
            gbc.insets = Insets(INSET_10, INSET_10, INSET_5, INSET_10)
            gbc.anchor = GridBagConstraints.CENTER

            // Message label
            val messageLabel = JLabel("OpenCode has stopped running")
            panel.add(messageLabel, gbc)

            // Session info
            if (viewModel.sessionId != null) {
                gbc.gridy = 1
                gbc.insets = Insets(INSET_5, INSET_10, INSET_10, INSET_10)
                val sessionLabel = JLabel(
                    "<html><font size='-2' color='gray'>Session: " +
                        "${viewModel.sessionId}</font></html>"
                )
                panel.add(sessionLabel, gbc)
            }

            // Restart button
            gbc.gridy = 2
            gbc.insets = Insets(INSET_10, INSET_10, INSET_20, INSET_10)
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
            gbc.insets = Insets(INSET_10, INSET_10, INSET_10, INSET_10)
            gbc.anchor = GridBagConstraints.CENTER

            val errorLabel = JLabel("<html><center>$message</center></html>")
            panel.add(errorLabel, gbc)

            add(panel, BorderLayout.CENTER)
            revalidate()
            repaint()
        }
    }

    private fun restartTerminal() {
        LOG.info("Restarting OpenCode editor terminal (user request)")

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

    /**
     * Disposes all resources held by the panel.
     *
     * Stops process monitoring, cleans up the terminal widget, disposes
     * the ViewModel, and cancels the coroutine scope. This method is called
     * automatically when the editor tab is closed.
     */
    override fun dispose() {
        LOG.info("OpenCodeEditorPanel disposed")

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

    companion object {
        private const val MONITORING_INTERVAL_MS = 1000L
        private const val FORCE_PTY_ALLOC = true
        private const val INSET_5 = 5
        private const val INSET_10 = 10
        private const val INSET_20 = 20
    }
}
