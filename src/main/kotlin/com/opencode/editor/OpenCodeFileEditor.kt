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

/**
 * Custom file editor for OpenCode AI sessions.
 *
 * Provides an editor tab that displays an embedded OpenCode terminal interface.
 * Manages the lifecycle of the OpenCodeEditorPanel and handles state persistence
 * across tab drag/move operations.
 *
 * @param project The IntelliJ project instance
 * @param file The virtual file being edited (must be OpenCodeVirtualFile)
 */
@Suppress("TooManyFunctions")
class OpenCodeFileEditor(
    private val project: Project,
    private val file: OpenCodeVirtualFile
) : UserDataHolderBase(), FileEditor {

    private var editorPanel: OpenCodeEditorPanel? = null
    private var currentSessionId: String? = null
    private var serverPort: Int? = null
    private var isOpencodeAvailable: Boolean = false
    private var stateWasRestored = false // Track if setState() was called

    // Property change listeners (required by FileEditor interface but not used)
    private val propertyChangeListeners = mutableListOf<PropertyChangeListener>()

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

    /**
     * Returns the main UI component for this editor.
     *
     * Lazily creates the OpenCodeEditorPanel on first call if OpenCode is available.
     * Returns a placeholder panel if OpenCode CLI is not installed.
     *
     * @return The root UI component to display in the editor tab
     */
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

    /**
     * Returns the component that should receive keyboard focus.
     *
     * @return The editor panel if created, null otherwise
     */
    override fun getPreferredFocusedComponent(): JComponent? {
        LOG.debug("getPreferredFocusedComponent() called")
        return editorPanel
    }

    /**
     * Returns the display name for this editor tab.
     *
     * @return The string "OpenCode" used as the tab title
     */
    override fun getName(): String = "OpenCode"

    /**
     * Called when the editor tab becomes selected/visible.
     *
     * This method is invoked by the platform when the user switches to this tab.
     */
    override fun selectNotify() {
        LOG.info("OpenCode editor tab selected")
    }

    /**
     * Called when the editor tab is deselected/hidden.
     *
     * This method is invoked by the platform when the user switches away from this tab.
     */
    override fun deselectNotify() {
        LOG.debug("OpenCode editor tab deselected")
    }

    /**
     * Restores the editor state after tab drag or move operations.
     *
     * This method is called before [getComponent] during state restoration.
     * Restores the session ID and server port from the previous state.
     *
     * @param state The state to restore (must be OpenCodeEditorState)
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
     * Returns the current editor state for serialization.
     *
     * Called during tab drag or move operations to save the current session
     * ID and server port for later restoration.
     *
     * @param level The requested level of state detail
     * @return The current editor state containing session ID and server port
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

    /**
     * Checks if the editor has unsaved changes.
     *
     * @return Always false, as the OpenCode editor does not track modifications
     */
    override fun isModified(): Boolean = false

    /**
     * Checks if the editor is in a valid state.
     *
     * @return Always true, as the editor is always valid
     */
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeListeners.add(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeListeners.remove(listener)
    }

    /**
     * Returns the current location within the editor.
     *
     * The OpenCode editor does not support navigation locations.
     *
     * @return Always null, as location tracking is not supported
     */
    override fun getCurrentLocation(): FileEditorLocation? = null

    /**
     * Disposes the editor and releases resources.
     *
     * Unregisters the editor from the OpenCode service and disposes
     * the embedded editor panel. Called when the editor tab is closed.
     */
    override fun dispose() {
        LOG.info("OpenCode editor disposed - session=$currentSessionId")
        val service = project.service<OpenCodeService>()
        service.unregisterActiveEditor(file)

        editorPanel?.let {
            it.dispose()
        }
        editorPanel = null
    }

    /**
     * Returns the virtual file associated with this editor.
     *
     * @return The OpenCodeVirtualFile being edited
     */
    override fun getFile(): VirtualFile = file

    /**
     * Returns the current OpenCode session ID.
     *
     * Provides access to the active session ID for use in actions and UI components.
     *
     * @return The current session ID, or null if no session is active
     */
    fun getCurrentSessionId(): String? = currentSessionId
}

/**
 * Serializable state for the OpenCode file editor.
 *
 * Stores the current session ID and server port for persistence across
 * tab drag and move operations.
 */
data class OpenCodeEditorState(
    /**
     * The unique identifier of the OpenCode session.
     *
     * Contains the session ID used to connect to the specific OpenCode conversation.
     */
    val sessionId: String?,

    /**
     * The port number of the OpenCode server.
     *
     * Contains the server port where the OpenCode session is running.
     */
    val serverPort: Int?
) : FileEditorState {
    /**
     * Determines if this state can be merged with another state.
     *
     * States can be merged if they are both OpenCodeEditorState instances
     * and have the same session ID.
     *
     * @param otherState The state to compare with
     * @param level The state level to consider
     * @return true if the states have the same session ID, false otherwise
     */
    override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean {
        return otherState is OpenCodeEditorState && otherState.sessionId == sessionId
    }
}
