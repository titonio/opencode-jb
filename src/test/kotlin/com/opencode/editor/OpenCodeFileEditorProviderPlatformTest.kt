package com.opencode.editor

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.replaceService
import com.opencode.service.OpenCodeService
import com.opencode.test.OpenCodePlatformTestBase
import com.opencode.vfs.OpenCodeFileSystem
import com.opencode.vfs.OpenCodeVirtualFile
import org.mockito.kotlin.*

/**
 * Platform test suite for OpenCodeFileEditorProvider.
 * Replaces the disabled OpenCodeFileEditorProviderTest.
 */
class OpenCodeFileEditorProviderPlatformTest : OpenCodePlatformTestBase() {

    private lateinit var provider: OpenCodeFileEditorProvider
    private lateinit var mockFileSystem: OpenCodeFileSystem
    private lateinit var mockService: OpenCodeService

    override fun setUp() {
        super.setUp()
        provider = OpenCodeFileEditorProvider()
        
        mockFileSystem = mock()
        whenever(mockFileSystem.protocol).thenReturn(OpenCodeFileSystem.PROTOCOL)
        
        // Mock service for editor creation
        mockService = mock()
        whenever(mockService.isOpencodeInstalled()).thenReturn(true)
        project.replaceService(OpenCodeService::class.java, mockService, testRootDisposable)
    }

    // ========== File Acceptance Tests ==========

    fun `test accept returns true for OpenCodeVirtualFile`() {
        // Arrange
        val openCodeFile = OpenCodeVirtualFile(mockFileSystem, "test-session-123")

        // Act
        val accepted = provider.accept(project, openCodeFile)

        // Assert
        assertTrue(accepted)
    }

    fun `test accept returns true for file with opencode protocol`() {
        // Arrange
        val mockFile = mock<VirtualFile>()
        val mockFs = mock<com.intellij.openapi.vfs.VirtualFileSystem>()
        whenever(mockFs.protocol).thenReturn(OpenCodeFileSystem.PROTOCOL)
        whenever(mockFile.fileSystem).thenReturn(mockFs)
        whenever(mockFile.path).thenReturn("opencode://session/abc123")

        // Act
        val accepted = provider.accept(project, mockFile)

        // Assert
        assertTrue(accepted)
    }

    fun `test accept returns false for non-OpenCode file`() {
        // Arrange
        val mockFile = mock<VirtualFile>()
        val mockFs = mock<com.intellij.openapi.vfs.VirtualFileSystem>()
        whenever(mockFs.protocol).thenReturn("file")
        whenever(mockFile.fileSystem).thenReturn(mockFs)
        whenever(mockFile.path).thenReturn("/regular/file.txt")

        // Act
        val accepted = provider.accept(project, mockFile)

        // Assert
        assertFalse(accepted)
    }

    // ========== Editor Creation Tests ==========

    fun `test createEditor with OpenCodeVirtualFile creates editor`() {
        // Arrange
        val openCodeFile = OpenCodeVirtualFile(mockFileSystem, "test-session-456")

        // Act
        val editor = provider.createEditor(project, openCodeFile)

        // Assert
        assertNotNull(editor)
        assertTrue(editor is OpenCodeFileEditor)
        assertEquals(openCodeFile, (editor as OpenCodeFileEditor).file)
    }

    // ========== Editor Type and Policy Tests ==========

    fun `test getEditorTypeId returns OpenCode`() {
        assertEquals("OpenCode", provider.getEditorTypeId())
    }

    fun `test getPolicy returns HIDE_DEFAULT_EDITOR`() {
        assertEquals(FileEditorPolicy.HIDE_DEFAULT_EDITOR, provider.getPolicy())
    }
}
