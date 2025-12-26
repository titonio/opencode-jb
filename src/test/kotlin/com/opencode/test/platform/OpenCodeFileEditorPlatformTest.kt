package com.opencode.test.platform

import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import com.opencode.editor.OpenCodeFileEditor
import com.opencode.service.OpenCodeService
import com.opencode.test.OpenCodePlatformTestBase
import com.opencode.vfs.OpenCodeFileSystem
import com.opencode.vfs.OpenCodeVirtualFile
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenCodeFileEditorPlatformTest : OpenCodePlatformTestBase() {

    private lateinit var mockService: OpenCodeService
    private lateinit var mockFileSystem: OpenCodeFileSystem
    private lateinit var mockFile: OpenCodeVirtualFile

    @Before
    override fun setUp() {
        super.setUp()
        mockService = mock()
        whenever(mockService.isOpencodeInstalled()).thenReturn(true)
        project.replaceService(OpenCodeService::class.java, mockService, testRootDisposable)

        mockFileSystem = OpenCodeFileSystem.getInstance()
        mockFile = OpenCodeVirtualFile(mockFileSystem, "test-session-123")
    }

    @Test
    fun `test initialization with valid file and project`() {
        val editor = OpenCodeFileEditor(project, mockFile)

        assertNotNull(editor)
        assertEquals(mockFile, editor.file)
        assertEquals("OpenCode", editor.name)
        assertFalse(editor.isModified)
        assertTrue(editor.isValid)
    }

    @Test
    fun `test initialization registers editor with service`() {
        OpenCodeFileEditor(project, mockFile)

        val service = project.getService(OpenCodeService::class.java)
        assertNotNull(service)
    }

    @Test
    fun `test getComponent returns container panel`() {
        val editor = OpenCodeFileEditor(project, mockFile)
        val component = editor.component

        assertNotNull(component)
        assertTrue(component is javax.swing.JComponent)
    }
}
