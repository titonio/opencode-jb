package com.opencode.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.opencode.model.SessionInfo
import com.opencode.service.OpenCodeService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*

class SessionListDialog(
    private val project: Project,
    private val service: OpenCodeService
) : DialogWrapper(project) {
    
    private val sessionListModel = DefaultListModel<SessionInfo>()
    private val sessionList = JBList(sessionListModel)
    private var selectedSession: SessionInfo? = null
    
    init {
        title = "OpenCode Sessions"
        setOKButtonText("Open Session")
        init()
        loadSessions()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(600, 400)
        
        // Configure list
        sessionList.cellRenderer = SessionCellRenderer()
        sessionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        sessionList.addListSelectionListener {
            selectedSession = sessionList.selectedValue
            isOKActionEnabled = selectedSession != null
        }
        
        // Scroll pane
        val scrollPane = JBScrollPane(sessionList)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // Button panel
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        
        val newSessionButton = JButton("New Session")
        newSessionButton.addActionListener {
            val title = JOptionPane.showInputDialog(
                panel,
                "Enter session title (optional):",
                "New Session",
                JOptionPane.PLAIN_MESSAGE
            )
            if (title != null) { // User didn't cancel
                createNewSession(if (title.isBlank()) null else title)
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
            loadSessions()
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
    
    private fun loadSessions() {
        sessionListModel.clear()
        val sessions = kotlinx.coroutines.runBlocking {
            service.listSessions(forceRefresh = true)
        }
        sessions.forEach { sessionListModel.addElement(it) }
        
        if (sessionListModel.isEmpty) {
            isOKActionEnabled = false
        }
    }
    
    private fun createNewSession(title: String?) {
        val sessionId = kotlinx.coroutines.runBlocking {
            service.createSession(title)
        }
        loadSessions()
        // Select the newly created session
        val index = (0 until sessionListModel.size()).find { 
            sessionListModel.getElementAt(it).id == sessionId
        }
        if (index != null) {
            sessionList.selectedIndex = index
        }
    }
    
    private fun deleteSelectedSession() {
        val session = sessionList.selectedValue ?: return
        
        val confirm = JOptionPane.showConfirmDialog(
            contentPane,
            "Delete session \"${session.title ?: session.id}\"?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION
        )
        
        if (confirm == JOptionPane.YES_OPTION) {
            val success = kotlinx.coroutines.runBlocking {
                service.deleteSession(session.id)
            }
            if (success) {
                loadSessions()
            } else {
                JOptionPane.showMessageDialog(
                    contentPane,
                    "Failed to delete session.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
    
    private fun shareSelectedSession() {
        val session = sessionList.selectedValue ?: return
        
        if (session.share != null) {
            // Already shared, show URL with option to unshare
            val options = arrayOf("Copy URL", "Unshare", "Cancel")
            val choice = JOptionPane.showOptionDialog(
                contentPane,
                "Share URL: ${session.share.url}",
                "Session Shared",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]
            )
            
            when (choice) {
                0 -> { // Copy URL
                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val selection = java.awt.datatransfer.StringSelection(session.share.url)
                    clipboard.setContents(selection, selection)
                }
                1 -> { // Unshare
                    val success = kotlinx.coroutines.runBlocking {
                        service.unshareSession(session.id)
                    }
                    if (success) {
                        loadSessions()
                    } else {
                        JOptionPane.showMessageDialog(
                            contentPane,
                            "Failed to unshare session.",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        } else {
            // Not shared, share it
            val shareUrl = kotlinx.coroutines.runBlocking {
                service.shareSession(session.id)
            }
            if (shareUrl != null) {
                loadSessions()
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                val selection = java.awt.datatransfer.StringSelection(shareUrl)
                clipboard.setContents(selection, selection)
                JOptionPane.showMessageDialog(
                    contentPane,
                    "Session shared! URL copied to clipboard:\n$shareUrl",
                    "Share Successful",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } else {
                JOptionPane.showMessageDialog(
                    contentPane,
                    "Failed to share session.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
    
    fun getSelectedSession(): SessionInfo? = selectedSession
    
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
                val title = value.title ?: "Untitled"
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
