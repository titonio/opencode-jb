package com.opencode.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.opencode.vfs.OpenCodeFileSystem
import com.opencode.vfs.OpenCodeVirtualFile

/**
 * File editor provider for OpenCode virtual files.
 *
 * This provider is responsible for determining whether a file can be opened with
 * the OpenCode editor and creating the appropriate editor instance. It handles
 * files using the custom `opencode://` protocol managed by [OpenCodeFileSystem].
 *
 * The provider implements [DumbAware] to ensure the editor can be opened during
 * project indexing operations.
 *
 * @see OpenCodeFileEditor
 * @see OpenCodeVirtualFile
 */
class OpenCodeFileEditorProvider : FileEditorProvider, DumbAware {
    /**
     * Determines whether this provider can create an editor for the given file.
     *
     * Accepts files that are either [OpenCodeVirtualFile] instances or use the
     * OpenCode virtual file system protocol.
     *
     * @param project The project context (not used in acceptance logic)
     * @param file The virtual file to check
     * @return `true` if the file is an OpenCode virtual file, `false` otherwise
     */
    override fun accept(project: Project, file: VirtualFile): Boolean {
        // Accept files from our custom VFS
        return file is OpenCodeVirtualFile ||
            file.fileSystem.protocol == OpenCodeFileSystem.PROTOCOL
    }

    /**
     * Creates a new editor instance for the given OpenCode virtual file.
     *
     * This method retrieves or creates the appropriate [OpenCodeVirtualFile] instance
     * and instantiates an [OpenCodeFileEditor] for it. If the provided file is not already
     * an [OpenCodeVirtualFile], the method attempts to find the corresponding virtual file
     * through the [OpenCodeFileSystem].
     *
     * @param project The project in which the editor will be opened
     * @param file The virtual file to create an editor for
     * @return A new [OpenCodeFileEditor] instance
     * @throws IllegalArgumentException If the file cannot be resolved to an [OpenCodeVirtualFile]
     */
    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        // Get OpenCodeVirtualFile instance
        val openCodeFile = when (file) {
            is OpenCodeVirtualFile -> file
            else -> {
                // Parse sessionId from path and create proper file
                val sessionId = OpenCodeFileSystem.parseSessionId(file.path)
                if (sessionId != null) {
                    OpenCodeFileSystem.getInstance().findFileByPath(file.path) as? OpenCodeVirtualFile
                } else {
                    null
                }
            }
        } ?: throw IllegalArgumentException("Cannot create OpenCode editor for file: ${file.path}")

        return OpenCodeFileEditor(project, openCodeFile)
    }

    /**
     * Returns the unique identifier for this editor type.
     *
     * This ID is used by the IDE to identify editor instances of this type
     * and manage editor state persistence.
     *
     * @return The editor type identifier "OpenCode"
     */
    override fun getEditorTypeId(): String = "OpenCode"

    /**
     * Returns the policy for how this editor should be used alongside other editors.
     *
     * The [HIDE_DEFAULT_EDITOR] policy indicates that this editor should replace
     * the default text editor when opening OpenCode virtual files, preventing
     * duplicate editor tabs for the same file.
     *
     * @return The file editor policy
     */
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
