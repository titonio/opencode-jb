package com.opencode.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class OpenCodeFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is OpenCodeVirtualFile || file.fileType == OpenCodeFileType
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return OpenCodeFileEditor(project, file)
    }

    override fun getEditorTypeId(): String = "OpenCode"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
