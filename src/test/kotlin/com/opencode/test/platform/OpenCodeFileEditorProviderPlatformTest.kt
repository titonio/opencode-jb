package com.opencode.test.platform

import com.intellij.openapi.project.Project
import com.opencode.editor.OpenCodeFileEditorProvider
import com.opencode.test.OpenCodePlatformTestBase
import com.opencode.vfs.OpenCodeFileSystem
import com.opencode.vfs.OpenCodeVirtualFile
import org.junit.Test

class OpenCodeFileEditorProviderPlatformTest : OpenCodePlatformTestBase() {

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
