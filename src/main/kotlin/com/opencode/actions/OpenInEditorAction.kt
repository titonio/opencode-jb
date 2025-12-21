package com.opencode.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.opencode.service.OpenCodeService
import com.opencode.vfs.OpenCodeFileSystem
import kotlinx.coroutines.runBlocking

class OpenInEditorAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<OpenCodeService>()
        
        // Create a new session
        val sessionId = try {
            runBlocking {
                service.createSession(null)
            }
        } catch (ex: Exception) {
            javax.swing.JOptionPane.showMessageDialog(
                null,
                "Failed to create session: ${ex.message}",
                "Error",
                javax.swing.JOptionPane.ERROR_MESSAGE
            )
            return
        }
        
        // Open editor with the new session
        val virtualFile = OpenCodeFileSystem.getInstance()
            .findFileByPath(OpenCodeFileSystem.buildUrl(sessionId))!!
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }
}
