package com.opencode.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.opencode.model.SessionInfo
import com.opencode.service.OpenCodeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.intellij.openapi.ui.Messages
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*

// Abstraction for UI dialogs to allow testing
interface DialogProvider {
    fun showInputDialog(parent: Component, message: String, title: String): String?
    fun showErrorDialog(parent: Component, message: String, title: String)
    fun showInfoMessage(parent: Component, message: String, title: String)
    fun showYesNoDialog(parent: Component, message: String, title: String): Boolean
    fun showOptionDialog(parent: Component, message: String, title: String, options: Array<String>, initialValue: Int): Int
}

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
    
    override fun showOptionDialog(parent: Component, message: String, title: String, options: Array<String>, initialValue: Int): Int {
        return Messages.showDialog(parent, message, title, options, initialValue, Messages.getInformationIcon())
    }
}

class SessionListDialog(
    private val project: Project,
    service: OpenCodeService,
    private val dialogProvider: DialogProvider = DefaultDialogProvider()
) : DialogWrapper(project), SessionListViewModel.ViewCallback {
    
    private val sessionListModel = DefaultListModel<SessionInfo>()
    
    // Exposed for testing
    val sessionList = JBList(sessionListModel)
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
        panel.preferredSize = Dimension(600, 400)
        
        // Configure list
        sessionList.cellRenderer = SessionCellRenderer()
        sessionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        sessionList.addListSelectionListener {
            viewModel.selectSession(sessionList.selectedValue)
        }
        
        // Scroll pane
        val scrollPane = JBScrollPane(sessionList)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // Button panel
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        
        val newSessionButton = JButton("New Session")
        newSessionButton.addActionListener {
            val title = dialogProvider.showInputDialog(
                panel,
                "Enter session title (optional):",
                "New Session"
            )
            if (title != null) { // User didn't cancel
                viewModel.createSession(if (title.isBlank()) null else title)
            }
        }
        
        val deleteButton = JButton("Delete")
        deleteButton.addActionListener {
            deleteSelectedSession()
        }
        
        val shareButton = JButton("Share")
        shareButton.addActionListener {
            shareSelectedSession()
        }
        
        val refreshButton = JButton("Refresh")
        refreshButton.addActionListener {
            viewModel.loadSessions()
        }
        
        buttonPanel.add(newSessionButton)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(deleteButton)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(shareButton)
        buttonPanel.add(Box.createHorizontalGlue())
        buttonPanel.add(refreshButton)
        
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        return panel
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
    
    fun getSelectedSession(): SessionInfo? = viewModel.getSelectedSession()
    
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
                val id = value.id.take(12) + "..."
                val updateTime = try {
                    val instant = Instant.ofEpochMilli(value.time.updated)
                    dateFormatter.format(instant)
                } catch (e: Exception) {
                    "Unknown"
                }
                
                text = "<html><b>$title</b>$shareIcon<br><small>ID: $id | Updated: $updateTime</small></html>"
            }
            
            return this
        }
    }
}
