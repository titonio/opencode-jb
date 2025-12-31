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
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Comprehensive tests for FileUtils utility class.
 * Tests file reference generation with various path scenarios and editor selections.
 *
 * Coverage:
 * - Null file handling
 * - Path resolution with and without content roots
 * - Editor selection handling (no selection, single-line, multi-line)
 * - Complex and special character paths
 */
class FileUtilsTest {

    private lateinit var mockProject: Project
    private lateinit var mockProjectFileIndex: ProjectFileIndex

    @BeforeEach
    fun setUp() {
        // Create mock project
        mockProject = mock()
        whenever(mockProject.name).thenReturn("TestProject")

        // Create mock ProjectFileIndex
        mockProjectFileIndex = mock()
    }

    /**
     * Helper function to execute a test with mocked ProjectFileIndex.getInstance()
     */
    private fun <T> withMockedProjectFileIndex(block: () -> T): T {
        return Mockito.mockStatic(ProjectFileIndex::class.java).use { mockedStatic ->
            mockedStatic.`when`<ProjectFileIndex> { ProjectFileIndex.getInstance(any()) }
                .thenReturn(mockProjectFileIndex)
            block()
        }
    }

    // ========== Null File Tests ==========

    @Test
    fun testGetActiveFileReference_NullFile_ReturnsNull() {
        // Test that null file returns null reference (doesn't need mocking)
        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = null
        )

        assertNull(result, "Should return null when file is null")
    }

    // ========== No Editor Tests ==========

    @Test
    fun testGetActiveFileReference_NoEditor_OnlyPath() = withMockedProjectFileIndex {
        // Create a mock virtual file
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/src/main/Test.kt")
        whenever(mockFile.name).thenReturn("Test.kt")

        // Mock ProjectFileIndex to return null (no content root)
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(null)

        // Test without editor (no selection possible)
        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should return a reference even without editor")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")
        assertFalse(result.contains("#L"), "Should not contain line numbers without editor")
        assertTrue(result.contains("Test.kt"), "Should contain file name when no content root")
    }

    // ========== Editor Without Selection Tests ==========

    @Test
    fun testGetActiveFileReference_WithEditor_NoSelection() = withMockedProjectFileIndex {
        // Create mocks
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/src/Example.kt")
        whenever(mockFile.name).thenReturn("Example.kt")

        // Mock ProjectFileIndex to return null (no content root)
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(null)

        val mockEditor = mock<Editor>()
        val mockSelectionModel = mock<SelectionModel>()

        // Configure selection model to have no selection
        whenever(mockEditor.selectionModel).thenReturn(mockSelectionModel)
        whenever(mockSelectionModel.hasSelection()).thenReturn(false)

        // Test with editor but no selection
        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = mockEditor,
            file = mockFile
        )

        assertNotNull(result, "Should return a reference")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")
        assertFalse(result.contains("#L"), "Should not contain line numbers without selection")
    }

    // ========== Single-Line Selection Tests ==========

    @Test
    fun testGetActiveFileReference_SingleLineSelection() = withMockedProjectFileIndex {
        // Create mocks
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/src/SingleLine.kt")
        whenever(mockFile.name).thenReturn("SingleLine.kt")

        // Mock ProjectFileIndex to return null (no content root)
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(null)

        val mockEditor = mock<Editor>()
        val mockDocument = mock<Document>()
        val mockSelectionModel = mock<SelectionModel>()

        // Configure selection model for single-line selection (line 5)
        whenever(mockEditor.selectionModel).thenReturn(mockSelectionModel)
        whenever(mockEditor.document).thenReturn(mockDocument)
        whenever(mockSelectionModel.hasSelection()).thenReturn(true)
        whenever(mockSelectionModel.selectionStart).thenReturn(100)
        whenever(mockSelectionModel.selectionEnd).thenReturn(150)

        // Both start and end on same line (line 5, 0-indexed, so returns 4)
        whenever(mockDocument.getLineNumber(100)).thenReturn(4)
        whenever(mockDocument.getLineNumber(150)).thenReturn(4)

        // Test single-line selection
        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = mockEditor,
            file = mockFile
        )

        assertNotNull(result, "Should return a reference")
        assertTrue(result!!.contains("#L5"), "Should contain single line number L5")
        assertFalse(result.contains("-"), "Should not contain line range for single line")
    }

    // ========== Multi-Line Selection Tests ==========

    @Test
    fun testGetActiveFileReference_MultiLineSelection() = withMockedProjectFileIndex {
        // Create mocks
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/src/MultiLine.kt")
        whenever(mockFile.name).thenReturn("MultiLine.kt")

        // Mock ProjectFileIndex to return null (no content root)
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(null)

        val mockEditor = mock<Editor>()
        val mockDocument = mock<Document>()
        val mockSelectionModel = mock<SelectionModel>()

        // Configure selection model for multi-line selection (lines 10-15)
        whenever(mockEditor.selectionModel).thenReturn(mockSelectionModel)
        whenever(mockEditor.document).thenReturn(mockDocument)
        whenever(mockSelectionModel.hasSelection()).thenReturn(true)
        whenever(mockSelectionModel.selectionStart).thenReturn(200)
        whenever(mockSelectionModel.selectionEnd).thenReturn(400)

        // Start on line 10 (0-indexed: 9), end on line 15 (0-indexed: 14)
        whenever(mockDocument.getLineNumber(200)).thenReturn(9)
        whenever(mockDocument.getLineNumber(400)).thenReturn(14)

        // Test multi-line selection
        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = mockEditor,
            file = mockFile
        )

        assertNotNull(result, "Should return a reference")
        assertTrue(result!!.contains("#L10-15"), "Should contain line range L10-15")
    }

    // ========== Relative Path Tests ==========

    @Test
    fun testGetActiveFileReference_RelativePath_FromContentRoot() = withMockedProjectFileIndex {
        // This test demonstrates the behavior when content root is available

        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/content-root/src/main/kotlin/Test.kt")
        whenever(mockFile.name).thenReturn("Test.kt")

        val mockContentRoot = mock<VirtualFile>()
        whenever(mockContentRoot.path).thenReturn("/project/content-root")

        // Mock ProjectFileIndex to return a content root
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockContentRoot)

        // Test with content root
        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should return a reference")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")
        assertTrue(result.contains("src/main/kotlin/Test.kt"), "Should contain relative path from content root")
    }

    // ========== No Content Root Tests ==========

    @Test
    fun testGetActiveFileReference_NoContentRoot_UsesFileName() = withMockedProjectFileIndex {
        // Create a file that's not under any content root
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/external/some/path/External.kt")
        whenever(mockFile.name).thenReturn("External.kt")

        // Mock ProjectFileIndex to return null (no content root)
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(null)

        // Test without content root
        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should return a reference")

        // When no content root is found, should use just the file name
        // The implementation falls back to file.name when root is null
        assertTrue(result!!.contains("External.kt"), "Should contain the file name")
    }

    // ========== File Equals Root Tests ==========

    @Test
    fun testGetActiveFileReference_FileEqualsRoot_UsesFileName() = withMockedProjectFileIndex {
        // Test the edge case where file path equals root path
        // This simulates when the file IS the content root itself

        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/root")
        whenever(mockFile.name).thenReturn("root")

        val mockContentRoot = mock<VirtualFile>()
        whenever(mockContentRoot.path).thenReturn("/project/root")

        // Mock ProjectFileIndex to return a content root with same path
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockContentRoot)

        // Test when file equals root
        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should return a reference")

        // Implementation handles case where filePath.length <= rootPath.length
        // Falls back to file.name
        assertTrue(result!!.contains("root"), "Should contain the file name")
    }

    // ========== Complex Path Tests ==========

    @Test
    fun testGetActiveFileReference_ComplexPath() = withMockedProjectFileIndex {
        // Test with a complex nested path structure
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/src/main/kotlin/com/example/deep/nested/package/ComplexFile.kt")
        whenever(mockFile.name).thenReturn("ComplexFile.kt")

        val mockContentRoot = mock<VirtualFile>()
        whenever(mockContentRoot.path).thenReturn("/project")

        // Mock ProjectFileIndex to return a content root
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockContentRoot)

        val mockEditor = mock<Editor>()
        val mockDocument = mock<Document>()
        val mockSelectionModel = mock<SelectionModel>()

        // Add a selection to make it more complex
        whenever(mockEditor.selectionModel).thenReturn(mockSelectionModel)
        whenever(mockEditor.document).thenReturn(mockDocument)
        whenever(mockSelectionModel.hasSelection()).thenReturn(true)
        whenever(mockSelectionModel.selectionStart).thenReturn(500)
        whenever(mockSelectionModel.selectionEnd).thenReturn(750)
        whenever(mockDocument.getLineNumber(500)).thenReturn(24)
        whenever(mockDocument.getLineNumber(750)).thenReturn(35)

        // Test complex path with selection
        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = mockEditor,
            file = mockFile
        )

        assertNotNull(result, "Should return a reference")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")
        assertTrue(result.contains("#L25-36"), "Should contain line range")
    }

    // ========== Special Characters Tests ==========

    @Test
    fun testGetActiveFileReference_SpecialCharacters() = withMockedProjectFileIndex {
        // Test with special characters in file name and path
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/src/test-files/My File (v2).kt")
        whenever(mockFile.name).thenReturn("My File (v2).kt")

        // Mock ProjectFileIndex to return null (no content root)
        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(null)

        // Test with special characters
        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should return a reference")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")

        // Should handle special characters in file names
        assertTrue(result.contains("My File (v2).kt"), "Should preserve special characters")
    }
}
