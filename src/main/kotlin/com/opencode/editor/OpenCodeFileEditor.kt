package com.opencode.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.opencode.service.OpenCodeService
import com.opencode.vfs.OpenCodeVirtualFile
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

private val LOG = logger<OpenCodeFileEditor>()

class OpenCodeFileEditor(
    private val project: Project,
    private val file: OpenCodeVirtualFile
) : UserDataHolderBase(), FileEditor {
    
    private var editorPanel: OpenCodeEditorPanel? = null
    private var currentSessionId: String? = null
    private var serverPort: Int? = null
    private var isOpencodeAvailable: Boolean = false
    private var stateWasRestored = false  // Track if setState() was called
    
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
            if (service.shouldShowDialogs()) {
                showOpencodeNotInstalledDialog()
            }
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
        if (ApplicationManager.getApplication().isHeadlessEnvironment) {
            LOG.warn("OpenCode CLI not installed (headless mode, no dialog)")
            return
        }
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

    override fun getComponent(): JComponent {
        if (editorPanel == null && isOpencodeAvailable) {
            LOG.info("Creating OpenCodeEditorPanel - session=$currentSessionId, port=$serverPort")
            editorPanel = OpenCodeEditorPanel(
                project,
                currentSessionId,
                serverPort
            ) { newSessionId, newPort ->
                // Callback when session/port changes
                currentSessionId = newSessionId
                serverPort = newPort
                LOG.debug("Session/port updated: session=$newSessionId, port=$newPort")
            }
            
            containerPanel.removeAll()
            containerPanel.add(editorPanel!!, BorderLayout.CENTER)
            containerPanel.revalidate()
            containerPanel.repaint()
        } else if (!isOpencodeAvailable) {
            containerPanel.removeAll()
            containerPanel.add(placeholderPanel, BorderLayout.CENTER)
        }
        
        return containerPanel
    }
    
    private fun createPlaceholderPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val message = if (!isOpencodeAvailable) {
            "OpenCode CLI is not installed. Please install from https://opencode.ai/install"
        } else if (ApplicationManager.getApplication().isHeadlessEnvironment) {
            "OpenCode terminal is unavailable in headless test environment"
        } else {
            "Loading OpenCode terminal..."
        }
        panel.add(JLabel(message), BorderLayout.CENTER)
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        LOG.debug("getPreferredFocusedComponent() called")
        return editorPanel
    }

    override fun getName(): String = "OpenCode"

    /**
     * Called when the editor tab is selected/shown.
     */
    override fun selectNotify() {
        LOG.info("OpenCode editor tab selected")
    }

    /**
     * Called when the editor tab is deselected/hidden.
     */
    override fun deselectNotify() {
        LOG.debug("OpenCode editor tab deselected")
    }

    /**
     * Restore state after tab drag/move.
     * This is called BEFORE getComponent() during state restoration.
     */
    override fun setState(state: FileEditorState) {
        stateWasRestored = true
        
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
        
        editorPanel?.let {
            it.dispose()
        }
        editorPanel = null
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
