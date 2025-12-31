package com.opencode.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.opencode.service.OpenCodeService

/**
 * Factory for creating the OpenCode tool window in IntelliJ IDEA.
 */
class OpenCodeToolWindowFactory : ToolWindowFactory {
    /**
     * Creates the content for the OpenCode tool window.
     *
     * @param project The current project
     * @param toolWindow The tool window to create content for
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        project.service<OpenCodeService>().initToolWindow(toolWindow)
    }
}
