package com.opencode.utils

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile

/**
 * Utility object for generating file references from editor context.
 * Provides methods to create formatted file paths for use in OpenCode sessions.
 */
object FileUtils {
    /**
     * Generates a file reference string for the active editor context.
     *
     * The reference format is: `@relativePath` or `@relativePath#Lstart-end` for selections.
     * The path is relative to the project content root if available, otherwise uses the file name.
     *
     * @param project The current IntelliJ project
     * @param editor The active editor, or null if not available
     * @param file The currently open file, or null if no file is open
     * @return A formatted file reference string, or null if file is null
     */
    fun getActiveFileReference(project: Project, editor: Editor?, file: VirtualFile?): String? {
        if (file == null) return null

        val relativePath = resolveRelativePath(project, file)
        val lineReference = buildLineReference(editor)

        return "@$relativePath$lineReference"
    }

    private fun resolveRelativePath(project: Project, file: VirtualFile): String {
        val root = ProjectFileIndex.getInstance(project).getContentRootForFile(file) ?: return file.name
        val rootPath = root.path
        val filePath = file.path

        return when {
            !filePath.startsWith(rootPath) -> file.name
            filePath.length > rootPath.length -> filePath.substring(rootPath.length + 1)
            else -> file.name
        }
    }

    private fun buildLineReference(editor: Editor?): String {
        if (editor == null) return ""

        val selectionModel = editor.selectionModel
        return if (selectionModel.hasSelection()) {
            val startLine = editor.document.getLineNumber(selectionModel.selectionStart) + 1
            val endLine = editor.document.getLineNumber(selectionModel.selectionEnd) + 1
            if (startLine == endLine) {
                "#L$startLine"
            } else {
                "#L$startLine-$endLine"
            }
        } else {
            ""
        }
    }
}
