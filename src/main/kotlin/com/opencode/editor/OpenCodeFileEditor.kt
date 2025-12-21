package com.opencode.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.components.service
import com.intellij.terminal.JBTerminalWidget
import com.opencode.service.OpenCodeService
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class OpenCodeFileEditor(private val project: Project, private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val widget: JBTerminalWidget
    private val port: Int

    init {
        println("OpenCodeFileEditor created for file: ${file.name}, presentableName: ${file.presentableName}")
        val service = project.service<OpenCodeService>()
        val (w, p) = service.createTerminalWidget()
        widget = w
        port = p
        println("OpenCodeFileEditor: widget created on port $port")
    }

    override fun getComponent(): JComponent = widget.component

    override fun getPreferredFocusedComponent(): JComponent? = widget.preferredFocusableComponent

    // Return a simple constant name for the tab title
    override fun getName(): String {
        val name = "OpenCode"
        println("OpenCodeFileEditor.getName() called, returning: $name")
        return name
    }

    override fun setState(state: FileEditorState) {}

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        val service = project.service<OpenCodeService>()
        service.unregisterWidget(widget)
        // JBTerminalWidget usually handles its own disposal when removed from UI hierarchy, 
        // but we can ensure cleanup if needed.
    }
    
    override fun getFile(): VirtualFile = file
}
