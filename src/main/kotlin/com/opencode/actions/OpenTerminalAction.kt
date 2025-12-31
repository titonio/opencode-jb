package com.opencode.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.opencode.service.OpenCodeService
import com.opencode.utils.FileUtils

/**
 * Action that opens the OpenCode terminal tool window for the current project.
 *
 * This action retrieves the currently active file context (if any) and opens
 * the OpenCode terminal in the tool window with that file pre-loaded as context.
 */
class OpenTerminalAction : AnAction() {
    /**
     * Opens the OpenCode terminal tool window.
     *
     * @param e the action event containing project context and editor data
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val fileRef = FileUtils.getActiveFileReference(project, editor, file)

        project.service<OpenCodeService>().openTerminal(initialFile = fileRef)
    }
}
