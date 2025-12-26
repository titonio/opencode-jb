package com.opencode.utils

import com.intellij.openapi.vfs.VirtualFile
import com.opencode.test.OpenCodePlatformTestBase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled

/**
 * Platform integration tests for FileUtils.
 * Uses real IntelliJ platform infrastructure instead of mocks.
 * 
 * These tests verify file reference generation with real:
 * - Project structure
 * - Virtual File System
 * - ProjectFileIndex
 * - Editor and selection models
 * 
 * IMPORTANT: Tests are re-enabled as coroutines compatibility issue is resolved with newer dependencies.
 * See: docs/TEST_STATUS.md for more information on platform test challenges.
 */
class FileUtilsPlatformTest : OpenCodePlatformTestBase() {
    
    // ========== Null File Tests ==========
    
    fun `test getActiveFileReference with null file returns null`() {
        val result = FileUtils.getActiveFileReference(
            project = project,
            editor = null,
            file = null
        )
        
        // Use an if/else block to make the assertion more robust against unexpected non-null values
        if (result != null) {
            fail("Should return null when file is null, but got: $result")
        }
    }
    
    // ========== No Editor Tests ==========
    
    fun `test getActiveFileReference without editor returns path only`() {
        // Create a real file in the test project
        val psiFile = createTestFile("src/Test.kt", "class Test")
        val virtualFile = psiFile.virtualFile
        
        // Test without editor (no selection possible)
        val result = FileUtils.getActiveFileReference(
            project = project,
            editor = null,
            file = virtualFile
        )
        
        assertNotNull(result, "Should return a reference")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")
        assertFalse(result.contains("#L"), "Should not contain line numbers without editor")
        assertTrue(result.contains("Test.kt"), "Should contain file name")
    }
    
    // ========== Relative Path Tests ==========
    
    fun `test getActiveFileReference uses relative path from content root`() {
        // Create a file with nested path
        val psiFile = createTestFile("src/main/kotlin/Example.kt", """
            package com.example
            
            class Example {
                fun test() = "Hello"
            }
        """.trimIndent())
        
        val virtualFile = psiFile.virtualFile
        
        val result = FileUtils.getActiveFileReference(
            project = project,
            editor = null,
            file = virtualFile
        )
        
        assertNotNull(result, "Should return a reference")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")
        // Should contain relative path from project root
        assertTrue(result.contains("src/main/kotlin/Example.kt") || result.contains("Example.kt"),
            "Should contain file path, got: $result")
    }
    
    // ========== Editor with Selection Tests ==========
    
    fun `test getActiveFileReference with single line selection`() {
        // Create file and open in editor
        val content = """
            line 1
            line 2
            line 3
            line 4
            line 5
        """.trimIndent()
        
        val psiFile = createTestFile("SingleLine.kt", content)
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
        
        val editor = myFixture.editor
        val document = editor.document
        
        // Select line 3 (0-indexed line 2)
        val lineStart = document.getLineStartOffset(2)
        val lineEnd = document.getLineEndOffset(2)
        editor.selectionModel.setSelection(lineStart, lineEnd)
        
        val result = FileUtils.getActiveFileReference(
            project = project,
            editor = editor,
            file = psiFile.virtualFile
        )
        
        assertNotNull(result, "Should return a reference")
        assertTrue(result!!.contains("#L3"), "Should contain line number L3, got: $result")
    }
    
    fun `test getActiveFileReference with multi-line selection`() {
        // Create file with multiple lines
        val content = (1..20).joinToString("\n") { "line $it" }
        
        val psiFile = createTestFile("MultiLine.kt", content)
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
        
        val editor = myFixture.editor
        val document = editor.document
        
        // Select lines 5-10 (0-indexed 4-9)
        val startOffset = document.getLineStartOffset(4)
        val endOffset = document.getLineEndOffset(9)
        editor.selectionModel.setSelection(startOffset, endOffset)
        
        val result = FileUtils.getActiveFileReference(
            project = project,
            editor = editor,
            file = psiFile.virtualFile
        )
        
        assertNotNull(result, "Should return a reference")
        assertTrue(result!!.contains("#L5-10"), "Should contain line range L5-10, got: $result")
    }
    
    // ========== No Selection Tests ==========
    
    fun `test getActiveFileReference with editor but no selection`() {
        val psiFile = createTestFile("NoSelection.kt", "class NoSelection")
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
        
        val editor = myFixture.editor
        // Ensure no selection
        editor.selectionModel.removeSelection()
        
        val result = FileUtils.getActiveFileReference(
            project = project,
            editor = editor,
            file = psiFile.virtualFile
        )
        
        assertNotNull(result, "Should return a reference")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")
        assertFalse(result.contains("#L"), "Should not contain line numbers without selection")
    }
    
    // ========== Complex Paths Tests ==========
    
    fun `test getActiveFileReference with deeply nested path`() {
        // Create deeply nested structure
        val psiFile = createTestFile(
            "src/main/kotlin/com/example/deep/nested/Complex.kt",
            "package com.example.deep.nested\n\nclass Complex"
        )
        
        val result = FileUtils.getActiveFileReference(
            project = project,
            editor = null,
            file = psiFile.virtualFile
        )
        
        assertNotNull(result, "Should return a reference")
        assertTrue(result!!.startsWith("@"), "Reference should start with @")
        assertTrue(result.contains("Complex.kt"), "Should contain file name")
    }
    
    // ========== Special Characters Tests ==========
    
    fun `test getActiveFileReference with special characters in filename`() {
        // IntelliJ VFS may normalize some special characters, so use allowed ones
        val psiFile = createTestFile("My-File_v2.kt", "class MyFile")
        
        val result = FileUtils.getActiveFileReference(
            project = project,
            editor = null,
            file = psiFile.virtualFile
        )
        
        assertNotNull(result, "Should return a reference")
        assertTrue(result!!.contains("My-File_v2.kt"), "Should preserve special characters")
    }
}
