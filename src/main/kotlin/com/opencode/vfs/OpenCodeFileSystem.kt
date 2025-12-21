package com.opencode.vfs

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSystem

/**
 * Custom Virtual File System for OpenCode session files.
 * Provides stable file identity required for editor state persistence across tab operations.
 * 
 * URL format: opencode://session/<sessionId>
 */
class OpenCodeFileSystem : VirtualFileSystem() {
    companion object {
        const val PROTOCOL = "opencode"
        const val PROTOCOL_PREFIX = "$PROTOCOL://"
        
        fun getInstance(): OpenCodeFileSystem {
            return VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as OpenCodeFileSystem
        }
        
        /**
         * Build VFS URL for a session.
         * Format: opencode://session/<sessionId>
         */
        fun buildUrl(sessionId: String): String = "${PROTOCOL_PREFIX}session/$sessionId"
        
        /**
         * Extract sessionId from a VFS URL.
         * Returns null if URL format is invalid.
         */
        fun parseSessionId(url: String): String? {
            if (!url.startsWith(PROTOCOL_PREFIX)) return null
            val parts = url.removePrefix(PROTOCOL_PREFIX).split("/")
            return if (parts.size >= 2 && parts[0] == "session") parts[1] else null
        }
    }
    
    override fun getProtocol(): String = PROTOCOL
    
    override fun findFileByPath(path: String): VirtualFile? {
        val sessionId = parseSessionId(path) ?: return null
        return OpenCodeVirtualFile(this, sessionId)
    }
    
    override fun refresh(asynchronous: Boolean) {
        // No-op: our files are virtual and don't need refresh from disk
    }
    
    override fun refreshAndFindFileByPath(path: String): VirtualFile? = findFileByPath(path)
    
    override fun addVirtualFileListener(listener: VirtualFileListener) {
        // No-op: our files don't change
    }
    
    override fun removeVirtualFileListener(listener: VirtualFileListener) {
        // No-op: our files don't change
    }
    
    override fun deleteFile(requestor: Any?, vFile: VirtualFile) {
        throw UnsupportedOperationException("OpenCode files cannot be deleted")
    }
    
    override fun moveFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile) {
        throw UnsupportedOperationException("OpenCode files cannot be moved")
    }
    
    override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) {
        throw UnsupportedOperationException("OpenCode files cannot be renamed")
    }
    
    override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile {
        throw UnsupportedOperationException("OpenCode file system does not support child files")
    }
    
    override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile {
        throw UnsupportedOperationException("OpenCode file system does not support directories")
    }
    
    override fun copyFile(
        requestor: Any?,
        virtualFile: VirtualFile,
        newParent: VirtualFile,
        copyName: String
    ): VirtualFile {
        throw UnsupportedOperationException("OpenCode files cannot be copied")
    }
    
    override fun isReadOnly(): Boolean = true
}
