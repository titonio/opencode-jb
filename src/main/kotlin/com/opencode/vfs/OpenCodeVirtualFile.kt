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
 *
 * @param fileSystem The virtual file system that manages this file
 * @param sessionId The unique identifier of the OpenCode session
 */
class OpenCodeVirtualFile(
    private val fileSystem: OpenCodeFileSystem,

    /**
     * The unique identifier of the OpenCode session.
     *
     * This ID is used to construct the stable file path (opencode://session/<sessionId>)
     * and provides identity for editor state restoration.
     */
    val sessionId: String
) : VirtualFile() {

    /**
     * Returns the stable file path for this OpenCode session.
     *
     * The path is in the format opencode://session/<sessionId> and provides
     * stable identity for IntelliJ to restore editor state during tab operations.
     *
     * @return The file path string
     */
    override fun getPath(): String = OpenCodeFileSystem.buildUrl(sessionId)

    /**
     * Returns the name of this file.
     *
     * The name is derived from the session ID for display purposes.
     *
     * @return The file name
     */
    override fun getName(): String = "OpenCode-${sessionId.substring(0, DISPLAY_NAME_LENGTH)}"

    /**
     * Returns the presentable name for display in the UI.
     *
     * @return The presentable name (same as the file name)
     */
    override fun getPresentableName(): String = name

    /**
     * Returns the virtual file system that manages this file.
     *
     * @return The OpenCode file system
     */
    override fun getFileSystem(): VirtualFileSystem = fileSystem

    /**
     * Returns whether this file is currently valid and accessible.
     *
     * OpenCode virtual files are always valid as long as they exist in memory.
     *
     * @return true, as OpenCode virtual files are always valid
     */
    override fun isValid(): Boolean = true

    /**
     * Returns whether this file is a directory.
     *
     * OpenCode virtual files represent individual sessions, not directories.
     *
     * @return false, as this is a regular file
     */
    override fun isDirectory(): Boolean = false

    /**
     * Returns whether this file can be modified.
     *
     * OpenCode virtual files are read-only as they represent AI session data
     * managed by the OpenCode service.
     *
     * @return false, as OpenCode files are read-only
     */
    override fun isWritable(): Boolean = false

    /**
     * Returns the parent directory of this file.
     *
     * OpenCode virtual files have no parent as they exist at the root
     * of the opencode:// file system.
     *
     * @return null, as there is no parent directory
     */
    override fun getParent(): VirtualFile? = null

    /**
     * Returns the child files of this directory.
     *
     * OpenCode virtual files are not directories and have no children.
     *
     * @return null, as this file is not a directory
     */
    override fun getChildren(): Array<VirtualFile>? = null

    /**
     * Returns an output stream for writing to this file.
     *
     * @param requestor The object requesting the output stream
     * @param newModificationStamp The new modification stamp
     * @param newTimeStamp The new timestamp
     * @return An output stream for writing
     * @throws UnsupportedOperationException always, as OpenCode files are read-only
     */
    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
        throw UnsupportedOperationException("OpenCode files are read-only")
    }

    /**
     * Returns the contents of this file as a byte array.
     *
     * OpenCode virtual files have no actual content, as they are
     * used as placeholders for the OpenCode editor panel.
     *
     * @return An empty byte array
     */
    override fun contentsToByteArray(): ByteArray = ByteArray(0)

    /**
     * Returns the modification timestamp of this file.
     *
     * @return 0, as OpenCode virtual files have no real modification time
     */
    override fun getTimeStamp(): Long = 0L

    /**
     * Returns the length of this file in bytes.
     *
     * @return 0, as OpenCode virtual files have no actual content
     */
    override fun getLength(): Long = 0L

    /**
     * Refreshes the file state.
     *
     * @param asynchronous Whether the refresh should be asynchronous
     * @param recursive Whether to refresh children recursively
     * @param postRunnable Optional runnable to execute after refresh
     */
    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
        postRunnable?.run()
    }

    /**
     * Returns an input stream for reading the file contents.
     *
     * @return An empty input stream, as OpenCode virtual files have no content
     */
    override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    /**
     * Compares this file with another object for equality.
     *
     * Two OpenCode virtual files are equal if they have the same path.
     * This is critical for IntelliJ's editor state restoration.
     *
     * @param other The object to compare with
     * @return true if the objects are equal, false otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpenCodeVirtualFile) return false
        return path == other.path
    }

    /**
     * Returns the hash code for this file.
     *
     * The hash code is based on the file path, consistent with equals().
     *
     * @return The hash code value
     */
    override fun hashCode(): Int = path.hashCode()

    /**
     * Returns a string representation of this file.
     *
     * @return A string containing the session ID and path
     */
    override fun toString(): String = "OpenCodeVirtualFile(sessionId=$sessionId, path=$path)"

    companion object {
        private const val DISPLAY_NAME_LENGTH = 12
    }
}
