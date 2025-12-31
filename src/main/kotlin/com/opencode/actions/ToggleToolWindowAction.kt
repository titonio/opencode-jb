package com.opencode.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Action that toggles the visibility of the OpenCode tool window.
 *
 * If the tool window is currently visible, it will be hidden.
 * If it is hidden or not active, it will be activated and shown.
 */
class ToggleToolWindowAction : AnAction() {
    /**
     * Toggles the OpenCode tool window visibility when the action is performed.
     *
     * @param e The action event containing the project context
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("OpenCode") ?: return

        if (toolWindow.isVisible) {
            toolWindow.hide(null)
        } else {
            toolWindow.activate(null)
        }
    }
}
