package com.opencode.editor

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * FileType for OpenCode terminal session files.
 *
 * Files with the `.opencode` extension represent OpenCode AI assistant sessions
 * that can be opened in a custom editor within IntelliJ IDEA.
 */
object OpenCodeFileType : FileType {
    /**
     * Returns the unique name of this file type.
     *
     * @return The file type name "OpenCode"
     */
    override fun getName(): String = "OpenCode"

    /**
     * Returns a human-readable description of this file type.
     *
     * @return Description "OpenCode terminal session"
     */
    override fun getDescription(): String = "OpenCode terminal session"

    /**
     * Returns the default file extension for this file type.
     *
     * @return The file extension "opencode"
     */
    override fun getDefaultExtension(): String = "opencode"

    /**
     * Returns the icon used to display files of this type in the IDE.
     *
     * @return Null to use the default icon, avoiding rendering issues
     */
    override fun getIcon(): Icon? = null

    /**
     * Determines whether files of this type are binary files.
     *
     * OpenCode files are treated as binary because they are virtual files
     * managed by the OpenCode virtual file system rather than text files on disk.
     *
     * @return True to indicate this is a binary file type
     */
    override fun isBinary(): Boolean = true

    /**
     * Determines whether files of this type are read-only.
     *
     * @return False to allow editing of OpenCode session files
     */
    override fun isReadOnly(): Boolean = false

    /**
     * Returns the character set used to encode the file content.
     *
     * Since OpenCode files are virtual binary files, they do not use
     * character encoding and this method returns null.
     *
     * @param file The virtual file to check
     * @param content The binary content of the file
     * @return Null to indicate no character set is applicable
     */
    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}
