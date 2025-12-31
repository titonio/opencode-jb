package com.opencode.editor

import com.intellij.openapi.vfs.VirtualFile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Comprehensive test suite for OpenCodeFileType.
 * Tests file type properties and behavior as specified in TESTING_PLAN.md Phase 5.
 *
 * OpenCodeFileType is a singleton that defines the file type for OpenCode terminal sessions.
 * It provides:
 * - Name and description for the file type
 * - Default extension (.opencode)
 * - Binary file indicator
 * - Read-only status
 *
 * Phase 5 Tests (5 tests):
 * - Basic properties (name, description, extension)
 * - Binary and read-only flags
 * - Charset handling
 */
class OpenCodeFileTypeTest {

    // ========== Basic Properties Tests ==========

    @Test
    fun `test getName returns OpenCode`() {
        // OpenCodeFileType should identify itself as "OpenCode"
        assertEquals("OpenCode", OpenCodeFileType.getName())
    }

    @Test
    fun `test getDescription returns correct description`() {
        // Description should clearly state it's for terminal sessions
        val description = OpenCodeFileType.getDescription()
        assertEquals("OpenCode terminal session", description)
    }

    @Test
    fun `test getDefaultExtension returns opencode`() {
        // Default extension should be ".opencode"
        assertEquals("opencode", OpenCodeFileType.getDefaultExtension())
    }

    // ========== Binary and Read-Only Tests ==========

    @Test
    fun `test isBinary returns true`() {
        // OpenCode files are treated as binary to prevent content rendering
        assertTrue(OpenCodeFileType.isBinary())
    }

    @Test
    fun `test isReadOnly returns false`() {
        // Files are not marked as globally read-only
        // (Read-only behavior is handled at the virtual file level)
        assertFalse(OpenCodeFileType.isReadOnly())
    }

    // ========== Charset Tests ==========

    @Test
    fun `test getCharset returns null for any file`() {
        // Arrange
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("test.opencode")
        val content = ByteArray(0)

        // Act
        val charset = OpenCodeFileType.getCharset(mockFile, content)

        // Assert - Binary files don't have charset
        assertNull(charset)
    }

    // ========== Icon Tests ==========

    @Test
    fun `test getIcon returns null`() {
        // Icon is null to avoid rendering issues
        // Future enhancement could provide a custom icon
        assertNull(OpenCodeFileType.getIcon())
    }
}
