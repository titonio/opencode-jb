package com.opencode.test.platform

import com.intellij.testFramework.replaceService
import com.opencode.editor.OpenCodeFileEditorProvider
import com.opencode.service.OpenCodeService
import com.opencode.test.OpenCodePlatformTestBase
import com.opencode.vfs.OpenCodeFileSystem
import com.opencode.vfs.OpenCodeVirtualFile
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenCodeFileEditorProviderPlatformTest : OpenCodePlatformTestBase() {

    private lateinit var mockService: OpenCodeService

    @Before
    override fun setUp() {
        super.setUp()
        mockService = mock()
        whenever(mockService.isOpencodeInstalled()).thenReturn(true)
        project.replaceService(OpenCodeService::class.java, mockService, testRootDisposable)
    }

    @Test
    fun `test createEditor with OpenCodeVirtualFile creates editor`() {
        val provider = OpenCodeFileEditorProvider()
        val fileSystem = OpenCodeFileSystem.getInstance()
        val file = OpenCodeVirtualFile(fileSystem, "test-session-456")

        val editor = provider.createEditor(project, file)

        assertNotNull(editor)
        assertEquals("OpenCode", editor.name)
    }
}
