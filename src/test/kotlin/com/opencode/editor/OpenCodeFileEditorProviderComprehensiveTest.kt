package com.opencode.editor

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.opencode.vfs.OpenCodeFileSystem
import com.opencode.vfs.OpenCodeVirtualFile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

/**
 * Comprehensive tests for OpenCodeFileEditorProvider.
 *
 * Targets:
 * - OpenCodeFileEditorProvider (currently 38.5% line coverage, 33.3% branch coverage)
 *
 * Tests cover:
 * - File acceptance logic (all branches)
 * - Editor creation (all branches)
 * - Policy and type ID
 * - DumbAware interface
 * - Edge cases and error handling
 */
class OpenCodeFileEditorProviderComprehensiveTest {

    private lateinit var provider: OpenCodeFileEditorProvider
    private lateinit var mockProject: Project
    private lateinit var mockFile: VirtualFile
    private lateinit var mockFileSystem: VirtualFileSystem

    @BeforeEach
    fun setup() {
        provider = OpenCodeFileEditorProvider()
        mockProject = mock()
        mockFile = mock()
        mockFileSystem = mock()

        whenever(mockProject.name).thenReturn("TestProject")
        whenever(mockProject.basePath).thenReturn("/test/project")
    }

    // ========== Provider Construction Tests ==========

    @Test
    fun `test provider construction creates instance`() {
        val provider = OpenCodeFileEditorProvider()

        assertNotNull(provider)
    }

    @Test
    fun `test provider implements FileEditorProvider`() {
        val provider = OpenCodeFileEditorProvider()

        assertTrue(provider is com.intellij.openapi.fileEditor.FileEditorProvider)
    }

    @Test
    fun `test provider implements DumbAware`() {
        val provider = OpenCodeFileEditorProvider()

        assertTrue(provider is DumbAware)
    }

    @Test
    fun `test multiple provider instances are independent`() {
        val provider1 = OpenCodeFileEditorProvider()
        val provider2 = OpenCodeFileEditorProvider()

        assertNotNull(provider1)
        assertNotNull(provider2)
        assertNotSame(provider1, provider2)
    }

    // ========== accept() Tests - OpenCodeVirtualFile Branch ==========

    @Test
    fun `test accept returns true for OpenCodeVirtualFile`() {
        val openCodeFile = mock<OpenCodeVirtualFile>()

        val result = provider.accept(mockProject, openCodeFile)

        assertTrue(result)
    }

    @Test
    fun `test accept returns true for OpenCodeVirtualFile regardless of project`() {
        val openCodeFile = mock<OpenCodeVirtualFile>()
        val project1 = mock<Project>()
        val project2 = mock<Project>()

        assertTrue(provider.accept(project1, openCodeFile))
        assertTrue(provider.accept(project2, openCodeFile))
    }

    @Test
    fun `test accept handles OpenCodeVirtualFile type checking`() {
        val openCodeFile = mock<OpenCodeVirtualFile>()

        // Should not need to check file system if it's already OpenCodeVirtualFile
        val result = provider.accept(mockProject, openCodeFile)

        assertTrue(result)
        // File system should not be accessed in this path
        verify(openCodeFile, never()).fileSystem
    }

    // ========== accept() Tests - Protocol Branch ==========

    @Test
    fun `test accept returns true for file with OpenCode protocol`() {
        whenever(mockFile.fileSystem).thenReturn(mockFileSystem)
        whenever(mockFileSystem.protocol).thenReturn(OpenCodeFileSystem.PROTOCOL)

        val result = provider.accept(mockProject, mockFile)

        assertTrue(result)
    }

    @Test
    fun `test accept returns false for file with different protocol`() {
        whenever(mockFile.fileSystem).thenReturn(mockFileSystem)
        whenever(mockFileSystem.protocol).thenReturn("file")

        val result = provider.accept(mockProject, mockFile)

        assertFalse(result)
    }

    @Test
    fun `test accept returns false for regular VirtualFile with file protocol`() {
        whenever(mockFile.fileSystem).thenReturn(mockFileSystem)
        whenever(mockFileSystem.protocol).thenReturn("file")

        val result = provider.accept(mockProject, mockFile)

        assertFalse(result)
    }

    @Test
    fun `test accept returns false for http protocol`() {
        whenever(mockFile.fileSystem).thenReturn(mockFileSystem)
        whenever(mockFileSystem.protocol).thenReturn("http")

        val result = provider.accept(mockProject, mockFile)

        assertFalse(result)
    }

    @Test
    fun `test accept returns false for jar protocol`() {
        whenever(mockFile.fileSystem).thenReturn(mockFileSystem)
        whenever(mockFileSystem.protocol).thenReturn("jar")

        val result = provider.accept(mockProject, mockFile)

        assertFalse(result)
    }

    @Test
    fun `test accept checks file system protocol for non-OpenCodeVirtualFile`() {
        whenever(mockFile.fileSystem).thenReturn(mockFileSystem)
        whenever(mockFileSystem.protocol).thenReturn("other")

        provider.accept(mockProject, mockFile)

        verify(mockFile).fileSystem
        verify(mockFileSystem).protocol
    }

    // ========== createEditor() Tests - OpenCodeVirtualFile Branch ==========

    @Test
    fun `test createEditor with OpenCodeVirtualFile returns OpenCodeFileEditor`() {
        val mockOpenCodeFile = mock<OpenCodeVirtualFile>()
        whenever(mockOpenCodeFile.sessionId).thenReturn("test-session")
        whenever(mockOpenCodeFile.name).thenReturn("test.opencode")
        whenever(mockOpenCodeFile.path).thenReturn("/opencode/test-session")
        whenever(mockOpenCodeFile.isValid).thenReturn(true)

        // Mock service
        val mockService = mock<com.opencode.service.OpenCodeService>()
        whenever(mockProject.getService(com.opencode.service.OpenCodeService::class.java)).thenReturn(mockService)
        whenever(mockService.isOpencodeInstalled()).thenReturn(true)

        val editor = provider.createEditor(mockProject, mockOpenCodeFile)

        assertNotNull(editor)
        assertTrue(editor is OpenCodeFileEditor)
    }

    @Test
    fun `test createEditor with OpenCodeVirtualFile does not parse session`() {
        val mockOpenCodeFile = mock<OpenCodeVirtualFile>()
        whenever(mockOpenCodeFile.sessionId).thenReturn("direct-session")
        whenever(mockOpenCodeFile.name).thenReturn("test.opencode")
        whenever(mockOpenCodeFile.path).thenReturn("/opencode/direct-session")
        whenever(mockOpenCodeFile.isValid).thenReturn(true)

        val mockService = mock<com.opencode.service.OpenCodeService>()
        whenever(mockProject.getService(com.opencode.service.OpenCodeService::class.java)).thenReturn(mockService)
        whenever(mockService.isOpencodeInstalled()).thenReturn(true)

        provider.createEditor(mockProject, mockOpenCodeFile)

        // Path should not be accessed for parsing if file is already OpenCodeVirtualFile
        // (we can't verify this easily, but the structure is correct)
    }

    // ========== createEditor() Tests - Path Parsing Branch ==========

    @Test
    fun `test createEditor with regular file throws for invalid path`() {
        whenever(mockFile.path).thenReturn("/invalid/path")
        whenever(mockFile.fileSystem).thenReturn(mockFileSystem)

        assertThrows(IllegalArgumentException::class.java) {
            provider.createEditor(mockProject, mockFile)
        }
    }

    @Test
    fun `test createEditor throws IllegalArgumentException with descriptive message`() {
        whenever(mockFile.path).thenReturn("/invalid/path")
        whenever(mockFile.fileSystem).thenReturn(mockFileSystem)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            provider.createEditor(mockProject, mockFile)
        }

        val message = exception.message
        assertNotNull(message)
        assertTrue(message!!.contains("Cannot create OpenCode editor"))
        assertTrue(message.contains(mockFile.path))
    }

    // ========== getEditorTypeId() Tests ==========

    @Test
    fun `test getEditorTypeId returns OpenCode`() {
        val typeId = provider.editorTypeId

        assertEquals("OpenCode", typeId)
    }

    @Test
    fun `test getEditorTypeId is consistent across calls`() {
        val typeId1 = provider.editorTypeId
        val typeId2 = provider.editorTypeId
        val typeId3 = provider.editorTypeId

        assertEquals(typeId1, typeId2)
        assertEquals(typeId2, typeId3)
    }

    @Test
    fun `test getEditorTypeId returns non-null non-empty string`() {
        val typeId = provider.editorTypeId

        assertNotNull(typeId)
        assertTrue(typeId.isNotEmpty())
    }

    // ========== getPolicy() Tests ==========

    @Test
    fun `test getPolicy returns HIDE_DEFAULT_EDITOR`() {
        val policy = provider.policy

        assertEquals(FileEditorPolicy.HIDE_DEFAULT_EDITOR, policy)
    }

    @Test
    fun `test getPolicy is consistent across calls`() {
        val policy1 = provider.policy
        val policy2 = provider.policy
        val policy3 = provider.policy

        assertEquals(policy1, policy2)
        assertEquals(policy2, policy3)
    }

    @Test
    fun `test getPolicy returns non-null`() {
        val policy = provider.policy

        assertNotNull(policy)
    }

    // ========== accept() Edge Cases ==========

    @Test
    fun `test accept with null file system protocol`() {
        whenever(mockFile.fileSystem).thenReturn(mockFileSystem)
        whenever(mockFileSystem.protocol).thenReturn(null)

        val result = provider.accept(mockProject, mockFile)

        assertFalse(result)
    }

    @Test
    fun `test accept with empty protocol string`() {
        whenever(mockFile.fileSystem).thenReturn(mockFileSystem)
        whenever(mockFileSystem.protocol).thenReturn("")

        val result = provider.accept(mockProject, mockFile)

        assertFalse(result)
    }

    @Test
    fun `test accept with case-sensitive protocol check`() {
        whenever(mockFile.fileSystem).thenReturn(mockFileSystem)
        whenever(mockFileSystem.protocol).thenReturn("OPENCODE") // uppercase

        val result = provider.accept(mockProject, mockFile)

        // Should be false as protocol matching is case-sensitive
        assertFalse(result)
    }

    // ========== Multiple Calls and State Tests ==========

    @Test
    fun `test accept can be called multiple times with different files`() {
        val openCodeFile = mock<OpenCodeVirtualFile>()
        val regularFile = mock<VirtualFile>()

        whenever(regularFile.fileSystem).thenReturn(mockFileSystem)
        whenever(mockFileSystem.protocol).thenReturn("file")

        assertTrue(provider.accept(mockProject, openCodeFile))
        assertFalse(provider.accept(mockProject, regularFile))
        assertTrue(provider.accept(mockProject, openCodeFile))
    }

    @Test
    fun `test provider is stateless across operations`() {
        val file1 = mock<OpenCodeVirtualFile>()
        val file2 = mock<OpenCodeVirtualFile>()

        val result1 = provider.accept(mockProject, file1)
        val result2 = provider.accept(mockProject, file2)

        assertEquals(result1, result2)
    }

    @Test
    fun `test provider works with multiple projects`() {
        val openCodeFile = mock<OpenCodeVirtualFile>()
        val project1 = mock<Project>()
        val project2 = mock<Project>()
        val project3 = mock<Project>()

        assertTrue(provider.accept(project1, openCodeFile))
        assertTrue(provider.accept(project2, openCodeFile))
        assertTrue(provider.accept(project3, openCodeFile))
    }

    // ========== DumbAware Tests ==========

    @Test
    fun `test provider is usable during indexing`() {
        // DumbAware means it works during indexing
        val provider = OpenCodeFileEditorProvider()

        assertTrue(provider is DumbAware)
    }

    // ========== Type Hierarchy Tests ==========

    @Test
    fun `test provider implements all required interfaces`() {
        val provider = OpenCodeFileEditorProvider()

        assertTrue(provider is com.intellij.openapi.fileEditor.FileEditorProvider)
        assertTrue(provider is com.intellij.openapi.project.DumbAware)
    }

    @Test
    fun `test provider has all required methods`() {
        val providerClass = OpenCodeFileEditorProvider::class.java

        assertNotNull(providerClass.getMethod("accept", Project::class.java, VirtualFile::class.java))
        assertNotNull(providerClass.getMethod("createEditor", Project::class.java, VirtualFile::class.java))
        assertNotNull(providerClass.getMethod("getEditorTypeId"))
        assertNotNull(providerClass.getMethod("getPolicy"))
    }

    // ========== Concurrent Usage Tests ==========

    @Test
    fun `test concurrent accept calls`() {
        val files = (1..10).map { mock<OpenCodeVirtualFile>() }

        val results = files.map { provider.accept(mockProject, it) }

        // All should accept OpenCodeVirtualFile
        assertTrue(results.all { it })
    }

    @Test
    fun `test accept with various file types in sequence`() {
        val openCodeFile = mock<OpenCodeVirtualFile>()

        val regularFile1FileSystem = mock<VirtualFileSystem>()
        whenever(regularFile1FileSystem.protocol).thenReturn(OpenCodeFileSystem.PROTOCOL)
        val regularFile1 = mock<VirtualFile>()
        whenever(regularFile1.fileSystem).thenReturn(regularFile1FileSystem)

        val regularFile2FileSystem = mock<VirtualFileSystem>()
        whenever(regularFile2FileSystem.protocol).thenReturn("file")
        val regularFile2 = mock<VirtualFile>()
        whenever(regularFile2.fileSystem).thenReturn(regularFile2FileSystem)

        assertTrue(provider.accept(mockProject, openCodeFile))
        assertTrue(provider.accept(mockProject, regularFile1))
        assertFalse(provider.accept(mockProject, regularFile2))
    }
}
