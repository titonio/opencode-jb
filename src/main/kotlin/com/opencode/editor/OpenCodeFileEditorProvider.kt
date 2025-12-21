package com.opencode.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.opencode.vfs.OpenCodeFileSystem
import com.opencode.vfs.OpenCodeVirtualFile

class OpenCodeFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        // Accept files from our custom VFS
        return file is OpenCodeVirtualFile || 
               file.fileSystem.protocol == OpenCodeFileSystem.PROTOCOL
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        // Get OpenCodeVirtualFile instance
        val openCodeFile = when (file) {
            is OpenCodeVirtualFile -> file
            else -> {
                // Parse sessionId from path and create proper file
                val sessionId = OpenCodeFileSystem.parseSessionId(file.path)
                if (sessionId != null) {
                    OpenCodeFileSystem.getInstance().findFileByPath(file.path) as? OpenCodeVirtualFile
                } else null
            }
        } ?: throw IllegalArgumentException("Cannot create OpenCode editor for file: ${file.path}")
        
        return OpenCodeFileEditor(project, openCodeFile)
    }

    override fun getEditorTypeId(): String = "OpenCode"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
