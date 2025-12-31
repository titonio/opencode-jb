package com.opencode.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.opencode.model.SessionInfo
import com.opencode.service.OpenCodeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

private val LOG = logger<SessionListDialog>()

/**
 * Abstraction for UI dialogs to allow testing.
 * Provides methods for displaying various dialog types without depending on concrete implementations.
 */
interface DialogProvider {
    /**
     * Shows an input dialog allowing the user to enter text.
     *
     * @param parent The parent component for the dialog
     * @param message The message to display in the dialog
     * @param title The title of the dialog
     * @return The text entered by the user, or null if cancelled
     */
    fun showInputDialog(parent: Component, message: String, title: String): String?

    /**
     * Shows an error dialog with the specified message.
     *
     * @param parent The parent component for the dialog
     * @param message The error message to display
     * @param title The title of the dialog
     */
    fun showErrorDialog(parent: Component, message: String, title: String)

    /**
     * Shows an informational message dialog.
     *
     * @param parent The parent component for the dialog
     * @param message The informational message to display
     * @param title The title of the dialog
     */
    fun showInfoMessage(parent: Component, message: String, title: String)

    /**
     * Shows a yes/no confirmation dialog.
     *
     * @param parent The parent component for the dialog
     * @param message The message to display in the dialog
     * @param title The title of the dialog
     * @return true if user selects Yes, false otherwise
     */
    fun showYesNoDialog(parent: Component, message: String, title: String): Boolean

    /**
     * Shows an option dialog with multiple choices.
     *
     * @param parent The parent component for the dialog
     * @param message The message to display in the dialog
     * @param title The title of the dialog
     * @param options Array of option labels to display
     * @param initialValue The index of the initially selected option
     * @return The index of the selected option, or -1 if cancelled
     */
    fun showOptionDialog(
        parent: Component,
        message: String,
        title: String,
        options: Array<String>,
        initialValue: Int
    ): Int
}

/**
 * Default implementation of [DialogProvider] using IntelliJ Platform's Messages API.
 * Provides real dialog functionality for production use.
 */
class DefaultDialogProvider : DialogProvider {
    override fun showInputDialog(parent: Component, message: String, title: String): String? =
        Messages.showInputDialog(parent, message, title, null)

    override fun showErrorDialog(parent: Component, message: String, title: String) {
        Messages.showErrorDialog(parent, message, title)
    }

    override fun showInfoMessage(parent: Component, message: String, title: String) {
        Messages.showInfoMessage(parent, message, title)
    }

    override fun showYesNoDialog(parent: Component, message: String, title: String): Boolean {
        return Messages.showYesNoDialog(parent, message, title, Messages.getQuestionIcon()) == Messages.YES
    }

    override fun showOptionDialog(
        parent: Component,
        message: String,
        title: String,
        options: Array<String>,
        initialValue: Int
    ): Int {
        return Messages.showDialog(parent, message, title, options, initialValue, Messages.getInformationIcon())
    }
}

/**
 * Dialog for displaying and managing OpenCode sessions.
 *
 * Allows users to view available sessions, create new sessions, delete sessions,
 * and share sessions. Uses [SessionListViewModel] for business logic and implements
 * [SessionListViewModel.ViewCallback] to receive state updates.
 *
 * @param project The IntelliJ project this dialog belongs to
 * @param service The OpenCode service for session operations
 * @param dialogProvider The dialog provider for displaying dialogs (default: DefaultDialogProvider)
 */
class SessionListDialog(
    private val project: Project,
    service: OpenCodeService,
    private val dialogProvider: DialogProvider = DefaultDialogProvider()
) : DialogWrapper(project), SessionListViewModel.ViewCallback {

    private val sessionListModel = DefaultListModel<SessionInfo>()

    /**
     * The list component displaying available sessions.
     * Exposed for testing purposes to verify list state.
     */
    val sessionList = JBList(sessionListModel)

    /**
     * The root panel containing the dialog's UI components.
     * Contains the session list, scroll pane, and button panel.
     */
    var rootPanel: JPanel? = null

    // ViewModel handles all business logic
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val viewModel = SessionListViewModel(service, viewModelScope)

    init {
        title = "OpenCode Sessions"
        setOKButtonText("Open Session")
        viewModel.setCallback(this)
        init()
        viewModel.loadSessions()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        rootPanel = panel
        panel.preferredSize = Dimension(DIALOG_WIDTH, DIALOG_HEIGHT)

        configureSessionList(panel)
        panel.add(JBScrollPane(sessionList), BorderLayout.CENTER)
        panel.add(createButtonPanel(), BorderLayout.SOUTH)

        return panel
    }

    private fun configureSessionList(@Suppress("UnusedParameter") panel: JPanel) {
        sessionList.cellRenderer = SessionCellRenderer()
        sessionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        sessionList.addListSelectionListener {
            viewModel.selectSession(sessionList.selectedValue)
        }
    }

    private fun createButtonPanel(): JPanel {
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)

        buttonPanel.add(
            createActionButton("New Session") {
                val title = dialogProvider.showInputDialog(
                    rootPanel ?: contentPane,
                    "Enter session title (optional):",
                    "New Session"
                )
                if (title != null) {
                    viewModel.createSession(if (title.isBlank()) null else title)
                }
            }
        )
        buttonPanel.add(Box.createHorizontalStrut(BUTTON_SPACING))
        buttonPanel.add(createActionButton("Delete") { deleteSelectedSession() })
        buttonPanel.add(Box.createHorizontalStrut(BUTTON_SPACING))
        buttonPanel.add(createActionButton("Share") { shareSelectedSession() })
        buttonPanel.add(Box.createHorizontalGlue())
        buttonPanel.add(createActionButton("Refresh") { viewModel.loadSessions() })

        return buttonPanel
    }

    private fun createActionButton(label: String, action: () -> Unit): JButton {
        val button = JButton(label)
        button.addActionListener { action() }
        return button
    }

    override fun doCancelAction() {
        super.doCancelAction()
        viewModelScope.cancel()
    }

    override fun doOKAction() {
        super.doOKAction()
        viewModelScope.cancel()
    }

    // ========== ViewCallback Implementation ==========

    override fun onSessionsLoaded(sessions: List<SessionInfo>) {
        SwingUtilities.invokeLater {
            sessionListModel.clear()
            sessions.forEach { sessionListModel.addElement(it) }
            isOKActionEnabled = !sessionListModel.isEmpty
        }
    }

    override fun onSessionSelected(session: SessionInfo?) {
        SwingUtilities.invokeLater {
            isOKActionEnabled = session != null
        }
    }

    override fun onError(message: String) {
        SwingUtilities.invokeLater {
            dialogProvider.showErrorDialog(
                rootPanel ?: contentPane,
                message,
                "Error"
            )
        }
    }

    override fun onSuccess(message: String) {
        SwingUtilities.invokeLater {
            // Could show a subtle notification, for now we just silently succeed
            // to avoid too many popups for the user
        }
    }

    override fun onShareUrlGenerated(url: String) {
        SwingUtilities.invokeLater {
            // Copy to clipboard
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val selection = java.awt.datatransfer.StringSelection(url)
            clipboard.setContents(selection, selection)

            dialogProvider.showInfoMessage(
                rootPanel ?: contentPane,
                "Session shared! URL copied to clipboard:\n$url",
                "Share Successful"
            )
        }
    }

    // ========== UI Action Handlers ==========

    private fun deleteSelectedSession() {
        val session = sessionList.selectedValue ?: return

        val confirm = dialogProvider.showYesNoDialog(
            rootPanel ?: contentPane,
            "Delete session \"${viewModel.getSessionDisplayTitle(session)}\"?",
            "Confirm Delete"
        )

        if (confirm) {
            viewModel.deleteSession(session)
        }
    }

    private fun shareSelectedSession() {
        val session = sessionList.selectedValue ?: return

        if (viewModel.isSessionShared(session)) {
            // Already shared, show URL with option to unshare
            val shareUrl = viewModel.getShareUrl(session)!!
            val options = arrayOf("Copy URL", "Unshare", "Cancel")
            val choice = dialogProvider.showOptionDialog(
                rootPanel ?: contentPane,
                "Share URL: $shareUrl",
                "Session Shared",
                options,
                0
            )

            when (choice) {
                0 -> { // Copy URL
                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val selection = java.awt.datatransfer.StringSelection(shareUrl)
                    clipboard.setContents(selection, selection)
                }
                1 -> { // Unshare
                    viewModel.unshareSession(session)
                }
            }
        } else {
            // Not shared, share it
            viewModel.shareSession(session)
        }
    }

    /**
     * Returns the currently selected session.
     *
     * @return The selected [SessionInfo], or null if no session is selected
     */
    fun getSelectedSession(): SessionInfo? = viewModel.getSelectedSession()

    companion object {
        private const val DIALOG_WIDTH = 600
        private const val DIALOG_HEIGHT = 400
        private const val BUTTON_SPACING = 5
        private const val ID_DISPLAY_LENGTH = 12
    }

    private class SessionCellRenderer : DefaultListCellRenderer() {
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())

        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

            if (value is SessionInfo) {
                val shareIcon = if (value.share != null) " 🔗" else ""
                val title = value.title
                val displayId = value.id.take(ID_DISPLAY_LENGTH) + "..."
                val updateTime = try {
                    val instant = Instant.ofEpochMilli(value.time.updated)
                    dateFormatter.format(instant)
                } catch (e: DateTimeException) {
                    LOG.warn("Failed to format date for session ${value.id}", e)
                    "Unknown"
                }

                text = "<html><b>$title</b>$shareIcon<br><small>ID: $displayId | Updated: $updateTime</small></html>"
            }

            return this
        }
    }
}
