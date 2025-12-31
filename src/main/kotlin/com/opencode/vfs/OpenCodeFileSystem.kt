package com.opencode.vfs

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSystem

/**
 * Custom Virtual File System for OpenCode session files.
 * Provides stable file identity required for editor state persistence across tab operations.
 *
 * This file system is read-only and uses a custom protocol to reference OpenCode sessions.
 * Files are virtual and do not exist on disk, enabling seamless integration with IntelliJ's editor system.
 *
 * URL format: opencode://session/<sessionId>
 */
class OpenCodeFileSystem : VirtualFileSystem() {
    /**
     * Returns the protocol name for this file system.
     *
     * @return The protocol string "opencode"
     */
    override fun getProtocol(): String = PROTOCOL

    /**
     * Finds a virtual file by its VFS path.
     *
     * @param path The VFS path to the file (e.g., "opencode://session/session-123")
     * @return The OpenCodeVirtualFile for the session, or null if path is invalid
     */
    override fun findFileByPath(path: String): VirtualFile? {
        val sessionId = parseSessionId(path) ?: return null
        return OpenCodeVirtualFile(this, sessionId)
    }

    /**
     * Refreshes the file system state.
     * This is a no-op since files are virtual and do not require disk refresh.
     *
     * @param asynchronous Whether to perform refresh asynchronously (ignored)
     */
    override fun refresh(asynchronous: Boolean) {
        // No-op: our files are virtual and don't need refresh from disk
    }

    /**
     * Refreshes and finds a virtual file by its VFS path.
     *
     * @param path The VFS path to the file
     * @return The OpenCodeVirtualFile for the session, or null if path is invalid
     */
    override fun refreshAndFindFileByPath(path: String): VirtualFile? = findFileByPath(path)

    /**
     * Adds a virtual file listener to this file system.
     * This is a no-op since files do not change.
     *
     * @param listener The listener to add (ignored)
     */
    override fun addVirtualFileListener(listener: VirtualFileListener) {
        // No-op: our files don't change
    }

    /**
     * Removes a virtual file listener from this file system.
     * This is a no-op since files do not change.
     *
     * @param listener The listener to remove (ignored)
     */
    override fun removeVirtualFileListener(listener: VirtualFileListener) {
        // No-op: our files don't change
    }

    /**
     * Deletes a file from the file system.
     *
     * @param requestor The object requesting the deletion (ignored)
     * @param vFile The virtual file to delete
     * @throws UnsupportedOperationException OpenCode files cannot be deleted
     */
    override fun deleteFile(requestor: Any?, vFile: VirtualFile) {
        throw UnsupportedOperationException("OpenCode files cannot be deleted")
    }

    /**
     * Moves a file to a new parent directory.
     *
     * @param requestor The object requesting the move (ignored)
     * @param vFile The virtual file to move
     * @param newParent The new parent directory
     * @throws UnsupportedOperationException OpenCode files cannot be moved
     */
    override fun moveFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile) {
        throw UnsupportedOperationException("OpenCode files cannot be moved")
    }

    /**
     * Renames a file in the file system.
     *
     * @param requestor The object requesting the rename (ignored)
     * @param vFile The virtual file to rename
     * @param newName The new name for the file
     * @throws UnsupportedOperationException OpenCode files cannot be renamed
     */
    override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) {
        throw UnsupportedOperationException("OpenCode files cannot be renamed")
    }

    /**
     * Creates a child file under a directory.
     *
     * @param requestor The object requesting the creation (ignored)
     * @param vDir The parent directory
     * @param fileName The name of the file to create
     * @return The created virtual file
     * @throws UnsupportedOperationException OpenCode file system does not support child files
     */
    override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile {
        throw UnsupportedOperationException("OpenCode file system does not support child files")
    }

    /**
     * Creates a child directory under a directory.
     *
     * @param requestor The object requesting the creation (ignored)
     * @param vDir The parent directory
     * @param dirName The name of the directory to create
     * @return The created virtual directory
     * @throws UnsupportedOperationException OpenCode file system does not support directories
     */
    override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile {
        throw UnsupportedOperationException("OpenCode file system does not support directories")
    }

    /**
     * Copies a file to a new location.
     *
     * @param requestor The object requesting the copy (ignored)
     * @param virtualFile The virtual file to copy
     * @param newParent The new parent directory
     * @param copyName The name for the copied file
     * @return The copied virtual file
     * @throws UnsupportedOperationException OpenCode files cannot be copied
     */
    override fun copyFile(
        requestor: Any?,
        virtualFile: VirtualFile,
        newParent: VirtualFile,
        copyName: String
    ): VirtualFile {
        throw UnsupportedOperationException("OpenCode files cannot be copied")
    }

    /**
     * Indicates whether this file system is read-only.
     *
     * @return true, as OpenCode files cannot be modified
     */
    override fun isReadOnly(): Boolean = true

    companion object {
        /**
         * The protocol string for the OpenCode virtual file system.
         */
        const val PROTOCOL = "opencode"

        /**
         * The protocol prefix string used for OpenCode VFS URLs.
         */
        const val PROTOCOL_PREFIX = "$PROTOCOL://"

        /**
         * Returns the singleton instance of the OpenCodeFileSystem.
         *
         * @return The registered OpenCodeFileSystem instance
         */
        fun getInstance(): OpenCodeFileSystem {
            return VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as OpenCodeFileSystem
        }

        /**
         * Builds a VFS URL for a given session ID.
         * Format: opencode://session/<sessionId>
         *
         * @param sessionId The unique identifier for the OpenCode session
         * @return The VFS URL string for the session
         */
        fun buildUrl(sessionId: String): String = "${PROTOCOL_PREFIX}session/$sessionId"

        /**
         * Extracts the session ID from a VFS URL.
         *
         * @param url The VFS URL to parse (e.g., "opencode://session/session-123")
         * @return The session ID, or null if the URL format is invalid
         */
        fun parseSessionId(url: String): String? {
            if (!url.startsWith(PROTOCOL_PREFIX)) return null
            val parts = url.removePrefix(PROTOCOL_PREFIX).split("/")
            return if (parts.size >= 2 && parts[0] == "session") parts[1] else null
        }
    }
}
