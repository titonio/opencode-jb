package com.opencode.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.opencode.service.OpenCodeService
import com.opencode.ui.SessionListDialog
import com.opencode.vfs.OpenCodeFileSystem

/**
 * Action to show the session list dialog and open the selected session
 */
class ListSessionsAction : AnAction("List OpenCode Sessions") {

    /**
     * Shows the session list dialog and opens the selected session in the editor.
     *
     * @param e the action event containing project and context information
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<OpenCodeService>()

        val dialog = SessionListDialog(project, service)
        if (!dialog.showAndGet()) {
            return
        }

        val selectedSession = dialog.getSelectedSession()
        if (selectedSession != null) {
            openSessionFile(project, selectedSession.id)
        }
    }

    /**
     * Updates the action's enabled state based on whether a project is available.
     *
     * @param e the action event containing presentation information
     */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    /**
     * Opens a session file in the editor using the custom OpenCode virtual file system.
     *
     * @param project the project in which to open the file
     * @param sessionId the ID of the session to open
     */
    internal fun openSessionFile(project: Project, sessionId: String) {
        val virtualFile = OpenCodeFileSystem.getInstance()
            .findFileByPath(OpenCodeFileSystem.buildUrl(sessionId)) ?: return
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }
}

/**
 * Action to create a new session directly
 */
class NewSessionAction : AnAction("New OpenCode Session") {

    /**
     * Creates a new OpenCode session by prompting for an optional title,
     * then opens the session in a new editor tab.
     *
     * @param e the action event containing project and context information
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<OpenCodeService>()

        val title = javax.swing.JOptionPane.showInputDialog(
            null,
            "Enter session title (optional):",
            "New OpenCode Session",
            javax.swing.JOptionPane.PLAIN_MESSAGE
        ) ?: return

        val sessionId: String? = try {
            kotlinx.coroutines.runBlocking {
                service.createSession(if (title.isBlank()) null else title)
            }
        } catch (e: java.io.IOException) {
            javax.swing.JOptionPane.showMessageDialog(
                null,
                "Failed to create session: ${e.message ?: "Unknown error"}",
                "Error",
                javax.swing.JOptionPane.ERROR_MESSAGE
            )
            null
        } catch (e: com.google.gson.JsonSyntaxException) {
            javax.swing.JOptionPane.showMessageDialog(
                null,
                "Failed to create session: ${e.message ?: "Unknown error"}",
                "Error",
                javax.swing.JOptionPane.ERROR_MESSAGE
            )
            null
        }

        if (sessionId != null) {
            val virtualFile = OpenCodeFileSystem.getInstance()
                .findFileByPath(OpenCodeFileSystem.buildUrl(sessionId))!!
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }
    }

    /**
     * Updates the action's enabled state based on whether a project is available.
     *
     * @param e the action event containing presentation information
     */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
