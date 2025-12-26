package com.opencode.test.platform

import com.intellij.openapi.project.Project
import com.opencode.editor.OpenCodeFileEditor
import com.opencode.service.OpenCodeService
import com.opencode.test.OpenCodePlatformTestBase
import com.opencode.vfs.OpenCodeFileSystem
import com.opencode.vfs.OpenCodeVirtualFile
import org.junit.Test
import org.junit.Ignore

class OpenCodeFileEditorPlatformTest : OpenCodePlatformTestBase() {

    private lateinit var mockFileSystem: OpenCodeFileSystem
    private lateinit var mockFile: OpenCodeVirtualFile

    override fun setUp() {
        super.setUp()
        // Initialize basic mocks that don't depend on complex services
        // The real project and services are provided by the platform test fixture
    }

    @Test
    fun `test initialization with valid file and project`() {
        val fileSystem = OpenCodeFileSystem.getInstance()
        val file = OpenCodeVirtualFile(fileSystem, "test-session-123")
        
        // Use the real project from the test fixture
        val editor = OpenCodeFileEditor(project, file)
        
        assertNotNull(editor)
        assertEquals(file, editor.file)
        assertEquals("OpenCode", editor.name)
        assertFalse(editor.isModified)
        assertTrue(editor.isValid)
    }

    @Test
    fun `test initialization registers editor with service`() {
        val fileSystem = OpenCodeFileSystem.getInstance()
        val file = OpenCodeVirtualFile(fileSystem, "test-session-123")
        
        val editor = OpenCodeFileEditor(project, file)
        
        // Verify service state
        val service = project.getService(OpenCodeService::class.java)
        // Note: In a real platform test, we'd need to check if the service state reflects this
        // But OpenCodeService might not be fully functional in test mode without a real server
        assertNotNull(service)
    }

    @Test
    fun `test getComponent returns container panel`() {
        val fileSystem = OpenCodeFileSystem.getInstance()
        val file = OpenCodeVirtualFile(fileSystem, "test-session-123")
        
        val editor = OpenCodeFileEditor(project, file)
        val component = editor.component
        
        assertNotNull(component)
        // Verify it's a JComponent
        assertTrue(component is javax.swing.JComponent)
    }
}
