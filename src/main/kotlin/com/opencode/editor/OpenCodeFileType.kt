package com.opencode.editor

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IconManager
import javax.swing.Icon

object OpenCodeFileType : FileType {
    override fun getName(): String = "OpenCode"
    override fun getDescription(): String = "OpenCode terminal session"
    override fun getDefaultExtension(): String = "opencode"
    
    // Use a simple icon or null to avoid rendering issues
    override fun getIcon(): Icon? = null
    
    override fun isBinary(): Boolean = true
    override fun isReadOnly(): Boolean = false
    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}
