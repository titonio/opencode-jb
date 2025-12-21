package com.opencode.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.opencode.service.OpenCodeService

class OpenCodeFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is OpenCodeVirtualFile || file.fileType == OpenCodeFileType
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val service = project.service<OpenCodeService>()
        
        // Check if an OpenCode editor is already open
        if (service.hasActiveEditor()) {
            val activeFile = service.getActiveEditorFile()
            if (activeFile != null && activeFile != file) {
                // Focus the existing tab instead of creating a new one
                ApplicationManager.getApplication().invokeLater {
                    FileEditorManager.getInstance(project).openFile(activeFile, true)
                }
                // Still need to return an editor, so create one but it won't be shown
                // since we're focusing the existing tab
            }
        }
        
        return OpenCodeFileEditor(project, file)
    }

    override fun getEditorTypeId(): String = "OpenCode"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
