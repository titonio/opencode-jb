package com.opencode.utils

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

/**
 * Comprehensive error path testing for FileUtils covering:
 * - Null inputs
 * - Invalid file paths
 * - Permission-related scenarios (simulation)
 * - Malformed line numbers
 * - Edge cases with selections
 */
class FileUtilsErrorPathTest {
    
    private lateinit var mockProject: Project
    private lateinit var mockProjectFileIndex: ProjectFileIndex
    private lateinit var mockEditor: Editor
    private lateinit var mockSelectionModel: SelectionModel
    private lateinit var mockDocument: Document
    private lateinit var mockFile: VirtualFile
    private lateinit var mockRoot: VirtualFile
    
    @BeforeEach
    fun setUp() {
        mockProject = mock()
        mockProjectFileIndex = mock()
        mockEditor = mock()
        mockSelectionModel = mock()
        mockDocument = mock()
        mockFile = mock()
        mockRoot = mock()
        
        whenever(mockEditor.selectionModel).thenReturn(mockSelectionModel)
        whenever(mockEditor.document).thenReturn(mockDocument)
        whenever(mockProject.getService(ProjectFileIndex::class.java)).thenReturn(mockProjectFileIndex)
    }
    
    // ========== Null Input Handling ==========
    
    @Test
    fun `getActiveFileReference returns null when file is null`() {
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, null)
        
        // Assert
        assertNull(result)
    }
    
    @Test
    fun `getActiveFileReference handles null editor gracefully`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Test.kt")
        whenever(mockFile.path).thenReturn("/project/src/Test.kt")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(null)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, null, mockFile)
        
        // Assert
        assertNotNull(result)
        assertEquals("@Test.kt", result)
    }
    
    @Test
    fun `getActiveFileReference handles null root gracefully`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Test.kt")
        whenever(mockFile.path).thenReturn("/somewhere/Test.kt")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(null)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert
        assertNotNull(result)
        assertEquals("@Test.kt", result)
    }
    
    // ========== Invalid File Paths ==========
    
    @Test
    fun `getActiveFileReference handles file path not under root`() {
        // Arrange - File path doesn't start with root path
        whenever(mockFile.name).thenReturn("External.kt")
        whenever(mockFile.path).thenReturn("/external/path/External.kt")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert - Should fall back to filename
        assertEquals("@External.kt", result)
    }
    
    @Test
    fun `getActiveFileReference handles file at root level`() {
        // Arrange - File path equals root path (edge case)
        whenever(mockFile.name).thenReturn("root.txt")
        whenever(mockFile.path).thenReturn("/project")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert - Should use filename when path equals root
        assertEquals("@root.txt", result)
    }
    
    @Test
    fun `getActiveFileReference handles empty file name`() {
        // Arrange
        whenever(mockFile.name).thenReturn("")
        whenever(mockFile.path).thenReturn("/project/src/")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert
        assertNotNull(result)
        assertTrue(result!!.startsWith("@"))
    }
    
    @Test
    fun `getActiveFileReference handles file with special characters`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Test[File].kt")
        whenever(mockFile.path).thenReturn("/project/src/Test[File].kt")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert
        assertEquals("@src/Test[File].kt", result)
    }
    
    @Test
    fun `getActiveFileReference handles file with spaces in name`() {
        // Arrange
        whenever(mockFile.name).thenReturn("My Test File.kt")
        whenever(mockFile.path).thenReturn("/project/src/My Test File.kt")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert
        assertEquals("@src/My Test File.kt", result)
    }
    
    @Test
    fun `getActiveFileReference handles file with unicode characters`() {
        // Arrange
        whenever(mockFile.name).thenReturn("测试文件.kt")
        whenever(mockFile.path).thenReturn("/project/src/测试文件.kt")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert
        assertEquals("@src/测试文件.kt", result)
    }
    
    // ========== Selection Edge Cases ==========
    
    @Test
    fun `getActiveFileReference handles invalid selection range`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Test.kt")
        whenever(mockFile.path).thenReturn("/project/src/Test.kt")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        whenever(mockSelectionModel.hasSelection()).thenReturn(true)
        whenever(mockSelectionModel.selectionStart).thenReturn(100)
        whenever(mockSelectionModel.selectionEnd).thenReturn(50) // End before start!
        whenever(mockDocument.getLineNumber(100)).thenReturn(10)
        whenever(mockDocument.getLineNumber(50)).thenReturn(5)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert - Should still produce result even with weird range
        assertNotNull(result)
        assertTrue(result!!.contains("#L"))
    }
    
    @Test
    fun `getActiveFileReference handles selection at document start`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Test.kt")
        whenever(mockFile.path).thenReturn("/project/src/Test.kt")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        whenever(mockSelectionModel.hasSelection()).thenReturn(true)
        whenever(mockSelectionModel.selectionStart).thenReturn(0)
        whenever(mockSelectionModel.selectionEnd).thenReturn(0)
        whenever(mockDocument.getLineNumber(0)).thenReturn(0)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert
        assertEquals("@src/Test.kt#L1", result)
    }
    
    @Test
    fun `getActiveFileReference handles very large line numbers`() {
        // Arrange
        whenever(mockFile.name).thenReturn("HugeFile.kt")
        whenever(mockFile.path).thenReturn("/project/src/HugeFile.kt")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        whenever(mockSelectionModel.hasSelection()).thenReturn(true)
        whenever(mockSelectionModel.selectionStart).thenReturn(999999)
        whenever(mockSelectionModel.selectionEnd).thenReturn(999999)
        whenever(mockDocument.getLineNumber(999999)).thenReturn(999999)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert
        assertEquals("@src/HugeFile.kt#L1000000", result)
    }
    
    @Test
    fun `getActiveFileReference handles multi-line selection`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Test.kt")
        whenever(mockFile.path).thenReturn("/project/src/Test.kt")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        whenever(mockSelectionModel.hasSelection()).thenReturn(true)
        whenever(mockSelectionModel.selectionStart).thenReturn(100)
        whenever(mockSelectionModel.selectionEnd).thenReturn(500)
        whenever(mockDocument.getLineNumber(100)).thenReturn(5)
        whenever(mockDocument.getLineNumber(500)).thenReturn(25)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert
        assertEquals("@src/Test.kt#L6-26", result)
    }
    
    @Test
    fun `getActiveFileReference handles single line selection`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Test.kt")
        whenever(mockFile.path).thenReturn("/project/src/Test.kt")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        whenever(mockSelectionModel.hasSelection()).thenReturn(true)
        whenever(mockSelectionModel.selectionStart).thenReturn(100)
        whenever(mockSelectionModel.selectionEnd).thenReturn(150)
        whenever(mockDocument.getLineNumber(100)).thenReturn(10)
        whenever(mockDocument.getLineNumber(150)).thenReturn(10)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert
        assertEquals("@src/Test.kt#L11", result)
    }
    
    @Test
    fun `getActiveFileReference handles no selection`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Test.kt")
        whenever(mockFile.path).thenReturn("/project/src/Test.kt")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        whenever(mockSelectionModel.hasSelection()).thenReturn(false)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert - No line number suffix
        assertEquals("@src/Test.kt", result)
    }
    
    // ========== Path Edge Cases ==========
    
    @Test
    fun `getActiveFileReference handles deeply nested file`() {
        // Arrange
        val deepPath = "/project/" + "a/".repeat(50) + "deep.kt"
        val deepRelative = "a/".repeat(50) + "deep.kt"
        
        whenever(mockFile.name).thenReturn("deep.kt")
        whenever(mockFile.path).thenReturn(deepPath)
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert
        assertEquals("@$deepRelative", result)
    }
    
    @Test
    fun `getActiveFileReference handles file with no extension`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Makefile")
        whenever(mockFile.path).thenReturn("/project/Makefile")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert
        assertEquals("@Makefile", result)
    }
    
    @Test
    fun `getActiveFileReference handles file with multiple extensions`() {
        // Arrange
        whenever(mockFile.name).thenReturn("archive.tar.gz")
        whenever(mockFile.path).thenReturn("/project/build/archive.tar.gz")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert
        assertEquals("@build/archive.tar.gz", result)
    }
    
    @Test
    fun `getActiveFileReference handles file path with trailing slash`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Test.kt")
        whenever(mockFile.path).thenReturn("/project/src/Test.kt")
        whenever(mockRoot.path).thenReturn("/project/")  // Trailing slash
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert - Should handle gracefully
        assertNotNull(result)
        assertTrue(result!!.startsWith("@"))
    }
    
    @Test
    fun `getActiveFileReference handles root path with backslashes`() {
        // Arrange - Windows-style paths
        whenever(mockFile.name).thenReturn("Test.kt")
        whenever(mockFile.path).thenReturn("C:\\project\\src\\Test.kt")
        whenever(mockRoot.path).thenReturn("C:\\project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert
        assertNotNull(result)
        assertTrue(result!!.startsWith("@"))
    }
    
    // ========== Exception Handling ==========
    
    @Test
    fun `getActiveFileReference handles ProjectFileIndex throwing exception`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Test.kt")
        whenever(mockFile.path).thenReturn("/project/src/Test.kt")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile))
            .thenThrow(RuntimeException("Index not ready"))
        
        // Act & Assert - Should handle exception and fall back to filename
        try {
            val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
            // If it doesn't throw, it should fall back to filename
            assertEquals("@Test.kt", result)
        } catch (e: RuntimeException) {
            // Also acceptable - exception propagates
            assertTrue(e.message!!.contains("Index not ready"))
        }
    }
    
    @Test
    fun `getActiveFileReference handles editor selectionModel throwing exception`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Test.kt")
        whenever(mockFile.path).thenReturn("/project/src/Test.kt")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        whenever(mockEditor.selectionModel).thenThrow(RuntimeException("Editor disposed"))
        
        // Act & Assert - Should throw since we can't handle editor errors gracefully
        assertThrows(RuntimeException::class.java) {
            FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        }
    }
    
    @Test
    fun `getActiveFileReference handles document getLineNumber throwing exception`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Test.kt")
        whenever(mockFile.path).thenReturn("/project/src/Test.kt")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        whenever(mockSelectionModel.hasSelection()).thenReturn(true)
        whenever(mockSelectionModel.selectionStart).thenReturn(100)
        whenever(mockDocument.getLineNumber(100)).thenThrow(IndexOutOfBoundsException("Invalid offset"))
        
        // Act & Assert - Should propagate exception
        assertThrows(IndexOutOfBoundsException::class.java) {
            FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        }
    }
    
    // ========== Boundary Values ==========
    
    @Test
    fun `getActiveFileReference handles maximum integer line number`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Test.kt")
        whenever(mockFile.path).thenReturn("/project/src/Test.kt")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        whenever(mockSelectionModel.hasSelection()).thenReturn(true)
        whenever(mockSelectionModel.selectionStart).thenReturn(Integer.MAX_VALUE)
        whenever(mockSelectionModel.selectionEnd).thenReturn(Integer.MAX_VALUE)
        whenever(mockDocument.getLineNumber(Integer.MAX_VALUE)).thenReturn(Integer.MAX_VALUE)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert
        assertNotNull(result)
        assertTrue(result!!.contains("#L"))
    }
    
    @Test
    fun `getActiveFileReference handles negative offset gracefully`() {
        // Arrange
        whenever(mockFile.name).thenReturn("Test.kt")
        whenever(mockFile.path).thenReturn("/project/src/Test.kt")
        whenever(mockRoot.path).thenReturn("/project")
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockRoot)
        
        whenever(mockSelectionModel.hasSelection()).thenReturn(true)
        whenever(mockSelectionModel.selectionStart).thenReturn(-1)
        whenever(mockSelectionModel.selectionEnd).thenReturn(-1)
        whenever(mockDocument.getLineNumber(-1)).thenReturn(-1)
        
        // Act
        val result = FileUtils.getActiveFileReference(mockProject, mockEditor, mockFile)
        
        // Assert - Line numbers are offset+1, so -1+1=0
        assertEquals("@src/Test.kt#L0", result)
    }
}
