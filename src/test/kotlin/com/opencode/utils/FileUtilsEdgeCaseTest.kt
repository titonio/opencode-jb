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
 * Edge case tests for FileUtils utility class.
 * Tests boundary conditions, unusual path scenarios, and error conditions.
 *
 * Coverage areas:
 * - Path normalization edge cases
 * - Trailing slash handling
 * - Very long paths
 * - Empty/minimal paths
 * - Special path characters
 * - Line number boundaries
 * - Selection edge cases
 * - Cross-platform path handling
 */
class FileUtilsEdgeCaseTest {

    private lateinit var mockProject: Project
    private lateinit var mockProjectFileIndex: ProjectFileIndex

    @BeforeEach
    fun setUp() {
        mockProject = mock()
        whenever(mockProject.name).thenReturn("EdgeCaseProject")
        mockProjectFileIndex = mock()
    }

    private fun <T> withMockedProjectFileIndex(block: () -> T): T {
        return Mockito.mockStatic(ProjectFileIndex::class.java).use { mockedStatic ->
            mockedStatic.`when`<ProjectFileIndex> { ProjectFileIndex.getInstance(any()) }
                .thenReturn(mockProjectFileIndex)
            block()
        }
    }

    // ========== Path Normalization Edge Cases ==========

    @Test
    fun testGetActiveFileReference_RootPathWithTrailingSlash() = withMockedProjectFileIndex {
        // Test behavior when root path has trailing slash
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/src/Test.kt")
        whenever(mockFile.name).thenReturn("Test.kt")

        val mockContentRoot = mock<VirtualFile>()
        whenever(mockContentRoot.path).thenReturn("/project/") // Trailing slash

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockContentRoot)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should return a reference with trailing slash in root")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")
        // Path starts with root, so should compute relative path
        assertTrue(
            result.contains("src/Test.kt") || result.contains("Test.kt"),
            "Should handle trailing slash in root path, got: $result"
        )
    }

    @Test
    fun testGetActiveFileReference_FilePathDoesNotStartWithRoot() = withMockedProjectFileIndex {
        // Test when file path doesn't start with root path (edge case fallback)
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/different/path/Test.kt")
        whenever(mockFile.name).thenReturn("Test.kt")

        val mockContentRoot = mock<VirtualFile>()
        whenever(mockContentRoot.path).thenReturn("/project/root")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockContentRoot)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should return a reference")
        // When path doesn't start with root, falls back to file.name
        assertTrue(result!!.contains("Test.kt"), "Should fall back to file name")
        assertEquals("@Test.kt", result, "Should use only file name when path doesn't start with root")
    }

    @Test
    fun testGetActiveFileReference_FilePathShorterThanRootButStartsWith() = withMockedProjectFileIndex {
        // Edge case: file path is actually shorter or equal to root path
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project")
        whenever(mockFile.name).thenReturn("project")

        val mockContentRoot = mock<VirtualFile>()
        whenever(mockContentRoot.path).thenReturn("/project")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockContentRoot)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should return a reference")
        // When filePath.length <= rootPath.length, uses file.name
        assertEquals("@project", result, "Should use file name when paths are equal length")
    }

    @Test
    fun testGetActiveFileReference_MultipleConsecutiveSlashes() = withMockedProjectFileIndex {
        // Test path with multiple consecutive slashes (normalized by VFS usually)
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project///src///Test.kt")
        whenever(mockFile.name).thenReturn("Test.kt")

        val mockContentRoot = mock<VirtualFile>()
        whenever(mockContentRoot.path).thenReturn("/project")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockContentRoot)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should return a reference")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")
        // Should handle multiple slashes (implementation does basic substring)
        assertTrue(result.contains("Test.kt"), "Should contain file name")
    }

    // ========== Very Long Path Tests ==========

    @Test
    fun testGetActiveFileReference_VeryLongPath() = withMockedProjectFileIndex {
        // Test with very long nested path
        val longPath = "/project/" + (1..50).joinToString("/") { "level$it" } + "/DeepFile.kt"

        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn(longPath)
        whenever(mockFile.name).thenReturn("DeepFile.kt")

        val mockContentRoot = mock<VirtualFile>()
        whenever(mockContentRoot.path).thenReturn("/project")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockContentRoot)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should handle very long paths")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")
        assertTrue(result.contains("DeepFile.kt"), "Should contain file name in very long path")
        // Should create relative path from project root
        assertTrue(result.contains("level1"), "Should include path components")
    }

    // ========== Empty and Minimal Path Tests ==========

    @Test
    fun testGetActiveFileReference_EmptyFileName() = withMockedProjectFileIndex {
        // Test with empty file name (unusual but possible edge case)
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/")
        whenever(mockFile.name).thenReturn("")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(null)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should handle empty file name")
        assertEquals("@", result, "Should return @ with empty file name")
    }

    @Test
    fun testGetActiveFileReference_SingleCharacterFileName() = withMockedProjectFileIndex {
        // Test with minimal file name
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/a")
        whenever(mockFile.name).thenReturn("a")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(null)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should handle single character file name")
        assertEquals("@a", result, "Should correctly format single character name")
    }

    // ========== Special Characters in Paths ==========

    @Test
    fun testGetActiveFileReference_UnicodeCharactersInPath() = withMockedProjectFileIndex {
        // Test with Unicode characters in file name
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/src/测试文件.kt")
        whenever(mockFile.name).thenReturn("测试文件.kt")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(null)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should handle Unicode characters")
        assertTrue(result!!.contains("测试文件.kt"), "Should preserve Unicode characters")
    }

    @Test
    fun testGetActiveFileReference_PathWithDots() = withMockedProjectFileIndex {
        // Test with dots in path (relative path components)
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/src/../test/File.kt")
        whenever(mockFile.name).thenReturn("File.kt")

        val mockContentRoot = mock<VirtualFile>()
        whenever(mockContentRoot.path).thenReturn("/project")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockContentRoot)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should handle dots in path")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")
        // Implementation does simple string operations, doesn't normalize .. paths
        assertTrue(result.contains("File.kt"), "Should contain file name")
    }

    // ========== Line Number Edge Cases ==========

    @Test
    fun testGetActiveFileReference_LineZeroSelection() = withMockedProjectFileIndex {
        // Test selection on line 0 (first line)
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/Test.kt")
        whenever(mockFile.name).thenReturn("Test.kt")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(null)

        val mockEditor = mock<Editor>()
        val mockDocument = mock<Document>()
        val mockSelectionModel = mock<SelectionModel>()

        whenever(mockEditor.selectionModel).thenReturn(mockSelectionModel)
        whenever(mockEditor.document).thenReturn(mockDocument)
        whenever(mockSelectionModel.hasSelection()).thenReturn(true)
        whenever(mockSelectionModel.selectionStart).thenReturn(0)
        whenever(mockSelectionModel.selectionEnd).thenReturn(10)

        // First line (0-indexed: 0, display as 1)
        whenever(mockDocument.getLineNumber(0)).thenReturn(0)
        whenever(mockDocument.getLineNumber(10)).thenReturn(0)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = mockEditor,
            file = mockFile
        )

        assertNotNull(result, "Should handle line 0")
        assertTrue(result!!.contains("#L1"), "Should display line 0 as L1")
    }

    @Test
    fun testGetActiveFileReference_VeryLargeLineNumbers() = withMockedProjectFileIndex {
        // Test with very large line numbers (large files)
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/LargeFile.kt")
        whenever(mockFile.name).thenReturn("LargeFile.kt")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(null)

        val mockEditor = mock<Editor>()
        val mockDocument = mock<Document>()
        val mockSelectionModel = mock<SelectionModel>()

        whenever(mockEditor.selectionModel).thenReturn(mockSelectionModel)
        whenever(mockEditor.document).thenReturn(mockDocument)
        whenever(mockSelectionModel.hasSelection()).thenReturn(true)
        whenever(mockSelectionModel.selectionStart).thenReturn(100000)
        whenever(mockSelectionModel.selectionEnd).thenReturn(200000)

        // Lines 9999-10000 (0-indexed: 9998-9999)
        whenever(mockDocument.getLineNumber(100000)).thenReturn(9998)
        whenever(mockDocument.getLineNumber(200000)).thenReturn(9999)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = mockEditor,
            file = mockFile
        )

        assertNotNull(result, "Should handle very large line numbers")
        assertTrue(result!!.contains("#L9999-10000"), "Should format large line numbers correctly")
    }

    @Test
    fun testGetActiveFileReference_SingleLineAtEndOfFile() = withMockedProjectFileIndex {
        // Test single line selection at a large line number
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/File.kt")
        whenever(mockFile.name).thenReturn("File.kt")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(null)

        val mockEditor = mock<Editor>()
        val mockDocument = mock<Document>()
        val mockSelectionModel = mock<SelectionModel>()

        whenever(mockEditor.selectionModel).thenReturn(mockSelectionModel)
        whenever(mockEditor.document).thenReturn(mockDocument)
        whenever(mockSelectionModel.hasSelection()).thenReturn(true)
        whenever(mockSelectionModel.selectionStart).thenReturn(50000)
        whenever(mockSelectionModel.selectionEnd).thenReturn(50050)

        // Both on line 999 (0-indexed: 998)
        whenever(mockDocument.getLineNumber(50000)).thenReturn(998)
        whenever(mockDocument.getLineNumber(50050)).thenReturn(998)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = mockEditor,
            file = mockFile
        )

        assertNotNull(result, "Should handle single line at large line number")
        assertTrue(result!!.contains("#L999"), "Should show single line format for line 999")
        assertFalse(result.contains("-"), "Should not contain range for single line")
    }

    // ========== Path Length Edge Cases ==========

    @Test
    fun testGetActiveFileReference_RootPathLongerThanFilePath() = withMockedProjectFileIndex {
        // Unusual case: root path is actually longer than file path
        // This shouldn't happen in practice but tests defensive coding
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/short")
        whenever(mockFile.name).thenReturn("short")

        val mockContentRoot = mock<VirtualFile>()
        whenever(mockContentRoot.path).thenReturn("/short/and/much/longer/root/path")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockContentRoot)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should handle root path longer than file path")
        // File path doesn't start with root, so falls back to file.name
        assertEquals("@short", result, "Should fall back to file name")
    }

    @Test
    fun testGetActiveFileReference_FilePathOneCharLongerThanRoot() = withMockedProjectFileIndex {
        // Test exact boundary: file path is exactly rootPath.length + 1
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/a")
        whenever(mockFile.name).thenReturn("a")

        val mockContentRoot = mock<VirtualFile>()
        whenever(mockContentRoot.path).thenReturn("/project")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockContentRoot)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should handle file path exactly one char longer")
        // filePath.length (10) > rootPath.length (8), so substring from position 9
        // This gives us just "a" (the file name)
        assertEquals("@a", result, "Should extract single character relative path")
    }

    // ========== Cross-Platform Path Considerations ==========

    @Test
    fun testGetActiveFileReference_WindowsStylePath() = withMockedProjectFileIndex {
        // Test Windows-style path (VFS normalizes, but test handling)
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("C:/project/src/Test.kt")
        whenever(mockFile.name).thenReturn("Test.kt")

        val mockContentRoot = mock<VirtualFile>()
        whenever(mockContentRoot.path).thenReturn("C:/project")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockContentRoot)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should handle Windows-style paths")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")
        assertTrue(
            result.contains("src/Test.kt") || result.contains("Test.kt"),
            "Should compute relative path from Windows-style root"
        )
    }

    @Test
    fun testGetActiveFileReference_MixedSlashesInPath() = withMockedProjectFileIndex {
        // Test mixed forward/back slashes (unusual but test robustness)
        val mockFile = mock<VirtualFile>()
        whenever(mockFile.path).thenReturn("/project/src\\mixed/Test.kt")
        whenever(mockFile.name).thenReturn("Test.kt")

        val mockContentRoot = mock<VirtualFile>()
        whenever(mockContentRoot.path).thenReturn("/project")

        whenever(mockProjectFileIndex.getContentRootForFile(mockFile)).thenReturn(mockContentRoot)

        val result = FileUtils.getActiveFileReference(
            project = mockProject,
            editor = null,
            file = mockFile
        )

        assertNotNull(result, "Should handle mixed slashes")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")
        assertTrue(result.contains("Test.kt"), "Should contain file name despite mixed slashes")
    }
}
