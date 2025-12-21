package com.opencode.vfs

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Virtual file representing an OpenCode session.
 * 
 * This file provides stable identity through its path (opencode://session/<sessionId>),
 * which allows IntelliJ to properly restore editor state during tab operations
 * (drag to split, detach, etc.).
 */
class OpenCodeVirtualFile(
    private val fileSystem: OpenCodeFileSystem,
    val sessionId: String
) : VirtualFile() {
    
    // CRITICAL: Stable path enables state restoration
    override fun getPath(): String = OpenCodeFileSystem.buildUrl(sessionId)
    
    override fun getName(): String = "OpenCode-${sessionId.substring(0, 12)}"
    
    override fun getPresentableName(): String = name
    
    override fun getFileSystem(): VirtualFileSystem = fileSystem
    
    override fun isValid(): Boolean = true
    
    override fun isDirectory(): Boolean = false
    
    override fun isWritable(): Boolean = false
    
    override fun getParent(): VirtualFile? = null
    
    override fun getChildren(): Array<VirtualFile>? = null
    
    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
        throw UnsupportedOperationException("OpenCode files are read-only")
    }
    
    override fun contentsToByteArray(): ByteArray = ByteArray(0)
    
    override fun getTimeStamp(): Long = 0L
    
    override fun getLength(): Long = 0L
    
    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
        postRunnable?.run()
    }
    
    override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    
    // Critical for equality checks and state restoration
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpenCodeVirtualFile) return false
        return path == other.path
    }
    
    override fun hashCode(): Int = path.hashCode()
    
    override fun toString(): String = "OpenCodeVirtualFile(sessionId=$sessionId, path=$path)"
}
