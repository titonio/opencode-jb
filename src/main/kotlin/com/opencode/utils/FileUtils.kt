package com.opencode.utils

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile

object FileUtils {
    fun getActiveFileReference(project: Project, editor: Editor?, file: VirtualFile?): String? {
        if (file == null) return null
        
        val root = ProjectFileIndex.getInstance(project).getContentRootForFile(file)
        val relativePath = if (root != null) {
            val rootPath = root.path
            val filePath = file.path
            if (filePath.startsWith(rootPath)) {
                // Handle case where filePath equals rootPath (no relative path)
                if (filePath.length > rootPath.length) {
                    filePath.substring(rootPath.length + 1)
                } else {
                    file.name
                }
            } else {
                file.name
            }
        } else {
            file.name
        }
        
        var ref = "@$relativePath"
        
        if (editor != null) {
            val selectionModel = editor.selectionModel
            if (selectionModel.hasSelection()) {
                val startLine = editor.document.getLineNumber(selectionModel.selectionStart) + 1
                val endLine = editor.document.getLineNumber(selectionModel.selectionEnd) + 1
                
                if (startLine == endLine) {
                    ref += "#L$startLine"
                } else {
                    ref += "#L$startLine-$endLine"
                }
            }
        }
        
        return ref
    }
}
