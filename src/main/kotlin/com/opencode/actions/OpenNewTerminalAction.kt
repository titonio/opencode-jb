package com.opencode.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.opencode.service.OpenCodeService
import com.opencode.utils.FileUtils

/**
 * Action to open OpenCode in a new terminal/tool window.
 *
 * This action is deprecated and removed from the menu but kept for backward compatibility.
 * It delegates to [OpenCodeService.openTerminal] with the active file reference.
 */
class OpenNewTerminalAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val fileRef = FileUtils.getActiveFileReference(project, editor, file)

        // Just call openTerminal since we don't support multiple tabs anymore
        project.service<OpenCodeService>().openTerminal(initialFile = fileRef)
    }
}
