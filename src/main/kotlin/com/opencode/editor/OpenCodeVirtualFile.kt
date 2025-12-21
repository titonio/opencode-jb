package com.opencode.editor

import com.intellij.testFramework.LightVirtualFile

class OpenCodeVirtualFile : LightVirtualFile("OpenCode", OpenCodeFileType, "") {
    init {
        isWritable = false  // Make it read-only to prevent confusion
    }
    
    override fun getPresentableName(): String = "OpenCode"
    
    override fun getName(): String = "OpenCode"
    
    override fun getPath(): String = "OpenCode"
    
    // Override toString to ensure proper display
    override fun toString(): String = "OpenCode"
}
