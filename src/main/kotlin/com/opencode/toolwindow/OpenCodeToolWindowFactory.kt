package com.opencode.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.components.service
import com.opencode.service.OpenCodeService

class OpenCodeToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        project.service<OpenCodeService>().initToolWindow(toolWindow)
    }
}
