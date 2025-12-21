package com.opencode.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.opencode.editor.OpenCodeVirtualFile

class OpenInEditorAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = OpenCodeVirtualFile.create()
        FileEditorManager.getInstance(project).openFile(file, true)
    }
}
