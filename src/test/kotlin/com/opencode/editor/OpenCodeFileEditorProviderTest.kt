package com.opencode.editor

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.opencode.vfs.OpenCodeFileSystem
import com.opencode.vfs.OpenCodeVirtualFile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

/**
 * Comprehensive test suite for OpenCodeFileEditorProvider.
 * Tests file editor provider acceptance, editor creation, and policies as specified in TESTING_PLAN.md Phase 5.
 * 
 * OpenCodeFileEditorProvider is responsible for:
 * - Accepting OpenCode virtual files for editing
 * - Creating OpenCodeFileEditor instances
 * - Defining editor policy (HIDE_DEFAULT_EDITOR)
 * 
 * Phase 5 Tests (8 tests):
 * - File acceptance logic
 * - Editor creation
 * - Editor type ID
 * - Policy configuration
 */
class OpenCodeFileEditorProviderTest {
    
    private lateinit var provider: OpenCodeFileEditorProvider
    private lateinit var mockProject: Project
    private lateinit var mockFileSystem: OpenCodeFileSystem
    
    @BeforeEach
    fun setUp() {
        provider = OpenCodeFileEditorProvider()
        
        mockProject = mock()
        whenever(mockProject.name).thenReturn("TestProject")
        whenever(mockProject.basePath).thenReturn("/test/project")
        
        mockFileSystem = mock()
        whenever(mockFileSystem.protocol).thenReturn(OpenCodeFileSystem.PROTOCOL)
    }
    
    // ========== File Acceptance Tests ==========
    
    @Test
    fun `test accept returns true for OpenCodeVirtualFile`() {
        // Arrange
        val openCodeFile = OpenCodeVirtualFile(mockFileSystem, "test-session-123")
        
        // Act
        val accepted = provider.accept(mockProject, openCodeFile)
        
        // Assert
        assertTrue(accepted)
    }
    
    @Test
    fun `test accept returns true for file with opencode protocol`() {
        // Arrange
        val mockFile = mock<VirtualFile>()
        val mockFs = mock<com.intellij.openapi.vfs.VirtualFileSystem>()
        whenever(mockFs.protocol).thenReturn(OpenCodeFileSystem.PROTOCOL)
        whenever(mockFile.fileSystem).thenReturn(mockFs)
        whenever(mockFile.path).thenReturn("opencode://session/abc123")
        
        // Act
        val accepted = provider.accept(mockProject, mockFile)
        
        // Assert
        assertTrue(accepted)
    }
    
    @Test
    fun `test accept returns false for non-OpenCode file`() {
        // Arrange
        val mockFile = mock<VirtualFile>()
        val mockFs = mock<com.intellij.openapi.vfs.VirtualFileSystem>()
        whenever(mockFs.protocol).thenReturn("file")
        whenever(mockFile.fileSystem).thenReturn(mockFs)
        whenever(mockFile.path).thenReturn("/regular/file.txt")
        
        // Act
        val accepted = provider.accept(mockProject, mockFile)
        
        // Assert
        assertFalse(accepted)
    }
    
    @Test
    fun `test accept returns false for different protocol`() {
        // Arrange
        val mockFile = mock<VirtualFile>()
        val mockFs = mock<com.intellij.openapi.vfs.VirtualFileSystem>()
        whenever(mockFs.protocol).thenReturn("http")
        whenever(mockFile.fileSystem).thenReturn(mockFs)
        
        // Act
        val accepted = provider.accept(mockProject, mockFile)
        
        // Assert
        assertFalse(accepted)
    }
    
    // ========== Editor Creation Tests ==========
    
    @Test
    @Disabled("OpenCodeFileEditor creation requires IntelliJ platform infrastructure (service registry)")
    fun `test createEditor with OpenCodeVirtualFile creates editor`() {
        // Arrange
        val openCodeFile = OpenCodeVirtualFile(mockFileSystem, "test-session-456")
        
        // Act & Assert
        // Would verify that OpenCodeFileEditor is created
        // Requires full platform infrastructure for service injection
        val editor = provider.createEditor(mockProject, openCodeFile)
        assertNotNull(editor)
        assertTrue(editor is OpenCodeFileEditor)
    }
    
    @Test
    @Disabled("Requires OpenCodeFileSystem singleton to be initialized with getInstance()")
    fun `test createEditor with protocol-only file attempts to find OpenCodeVirtualFile`() {
        // Arrange
        val mockFile = mock<VirtualFile>()
        val mockFs = mock<com.intellij.openapi.vfs.VirtualFileSystem>()
        whenever(mockFs.protocol).thenReturn(OpenCodeFileSystem.PROTOCOL)
        whenever(mockFile.fileSystem).thenReturn(mockFs)
        whenever(mockFile.path).thenReturn("opencode://session/abc123def")
        
        // Act & Assert
        // Would verify that provider tries to find/create OpenCodeVirtualFile
        // Requires OpenCodeFileSystem.getInstance() to work
        assertThrows(IllegalArgumentException::class.java) {
            provider.createEditor(mockProject, mockFile)
        }
    }
    
    // ========== Editor Type and Policy Tests ==========
    
    @Test
    fun `test getEditorTypeId returns OpenCode`() {
        // Editor type ID should be "OpenCode"
        assertEquals("OpenCode", provider.getEditorTypeId())
    }
    
    @Test
    fun `test getPolicy returns HIDE_DEFAULT_EDITOR`() {
        // Policy should hide default editor since we provide custom terminal UI
        assertEquals(FileEditorPolicy.HIDE_DEFAULT_EDITOR, provider.getPolicy())
    }
    
    // ========== Edge Case Tests for Coverage ==========
    
    @Test
    fun `test createEditor with invalid path throws IllegalArgumentException`() {
        // Arrange - file with no valid sessionId
        val mockFile = mock<VirtualFile>()
        val mockFs = mock<com.intellij.openapi.vfs.VirtualFileSystem>()
        whenever(mockFs.protocol).thenReturn(OpenCodeFileSystem.PROTOCOL)
        whenever(mockFile.fileSystem).thenReturn(mockFs)
        whenever(mockFile.path).thenReturn("opencode://invalid/path")
        
        // Act & Assert - should throw when unable to parse sessionId or find file
        assertThrows(IllegalArgumentException::class.java) {
            provider.createEditor(mockProject, mockFile)
        }
    }
    
    @Test
    fun `test createEditor with null sessionId throws IllegalArgumentException`() {
        // Arrange - file with path that can't be parsed
        val mockFile = mock<VirtualFile>()
        val mockFs = mock<com.intellij.openapi.vfs.VirtualFileSystem>()
        whenever(mockFs.protocol).thenReturn(OpenCodeFileSystem.PROTOCOL)
        whenever(mockFile.fileSystem).thenReturn(mockFs)
        whenever(mockFile.path).thenReturn("opencode://")
        
        // Act & Assert - should throw when sessionId is null
        assertThrows(IllegalArgumentException::class.java) {
            provider.createEditor(mockProject, mockFile)
        }
    }
    
    @Test
    fun `test accept with OpenCodeVirtualFile always returns true`() {
        // Multiple acceptance checks for OpenCodeVirtualFile
        val file1 = OpenCodeVirtualFile(mockFileSystem, "session-1")
        val file2 = OpenCodeVirtualFile(mockFileSystem, "session-2")
        
        assertTrue(provider.accept(mockProject, file1))
        assertTrue(provider.accept(mockProject, file2))
    }
}
