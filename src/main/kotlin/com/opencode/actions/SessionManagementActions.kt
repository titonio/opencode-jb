package com.opencode.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.opencode.service.OpenCodeService
import com.opencode.ui.SessionListDialog
import com.opencode.vfs.OpenCodeFileSystem

/**
 * Action to show the session list dialog and open the selected session
 */
class ListSessionsAction : AnAction("List OpenCode Sessions") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<OpenCodeService>()
        
        // Show session list dialog
        val dialog = SessionListDialog(project, service)
        if (dialog.showAndGet()) {
            val selectedSession = dialog.getSelectedSession()
            if (selectedSession != null) {
                // Open tab with selected session using custom VFS
                val virtualFile = OpenCodeFileSystem.getInstance()
                    .findFileByPath(OpenCodeFileSystem.buildUrl(selectedSession.id))!!
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * Action to create a new session directly
 */
class NewSessionAction : AnAction("New OpenCode Session") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<OpenCodeService>()
        
        // Prompt for session title (optional)
        val title = javax.swing.JOptionPane.showInputDialog(
            null,
            "Enter session title (optional):",
            "New OpenCode Session",
            javax.swing.JOptionPane.PLAIN_MESSAGE
        )
        
        // User cancelled
        if (title == null) return
        
        // Create new session
        val sessionId = try {
            kotlinx.coroutines.runBlocking {
                service.createSession(if (title.isBlank()) null else title)
            }
        } catch (e: Exception) {
            javax.swing.JOptionPane.showMessageDialog(
                null,
                "Failed to create session: ${e.message}\nMake sure OpenCode is installed and the server can start.",
                "Error",
                javax.swing.JOptionPane.ERROR_MESSAGE
            )
            return
        }
        
        // Open new tab with new session using custom VFS
        val virtualFile = OpenCodeFileSystem.getInstance()
            .findFileByPath(OpenCodeFileSystem.buildUrl(sessionId))!!
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
