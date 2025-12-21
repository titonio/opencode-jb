package com.opencode.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.opencode.service.OpenCodeService
import com.opencode.utils.FileUtils

class OpenTerminalAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        val fileRef = FileUtils.getActiveFileReference(project, editor, file)
        
        project.service<OpenCodeService>().openTerminal(initialFile = fileRef)
    }
}
