package com.opencode.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.opencode.service.OpenCodeService
import com.opencode.utils.FileUtils

/**
 * Action that adds the current file path to the active OpenCode session context.
 *
 * This action retrieves the currently active file reference from the editor
 * and adds it to the OpenCode session, allowing the AI assistant to be aware
 * of the file being worked on.
 */
class AddFilepathAction : AnAction() {
    /**
     * Adds the current file path to the OpenCode session context.
     *
     * Retrieves the active file reference from the project, editor, and virtual file
     * data, then passes it to the OpenCode service to be added to the session.
     *
     * @param e The action event containing context data (project, editor, file)
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val fileRef = FileUtils.getActiveFileReference(project, editor, file)

        if (fileRef != null) {
            project.service<OpenCodeService>().addFilepath(fileRef)
        }
    }
}
