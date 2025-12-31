package com.opencode.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.opencode.service.OpenCodeService
import com.opencode.vfs.OpenCodeFileSystem
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Action that creates a new OpenCode session and opens it in an editor tab.
 *
 * This action creates a new OpenCode session using the OpenCode service and opens it
 * in a dedicated editor tab using a virtual file with the opencode:// protocol. This provides
 * an alternative to the tool window, allowing users to work with OpenCode sessions
 * in a traditional editor interface with tabs.
 *
 * @see OpenTerminalAction for opening sessions in the tool window instead
 */
class OpenInEditorAction : AnAction() {
    /**
     * Creates a new OpenCode session and opens it in the editor.
     *
     * @param e the action event containing project and context information
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<OpenCodeService>()

        val sessionId = try {
            runBlocking {
                service.createSession(null)
            }
        } catch (ex: IOException) {
            LOG.warn("Failed to create session in editor action", ex)
            showError("Failed to create session: ${ex.message}")
            return
        }

        val virtualFile = OpenCodeFileSystem.getInstance()
            .findFileByPath(OpenCodeFileSystem.buildUrl(sessionId))

        if (virtualFile == null) {
            LOG.warn("Virtual file not found for session $sessionId")
            showError("Failed to open session in editor")
        } else {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }
    }

    private fun showError(message: String) {
        javax.swing.JOptionPane.showMessageDialog(
            null,
            message,
            "Error",
            javax.swing.JOptionPane.ERROR_MESSAGE
        )
    }
}
