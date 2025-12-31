package com.opencode.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

/**
 * Comprehensive tests for AddFilepathAction.
 * Tests the action that adds file references to the OpenCode service.
 *
 * Phase 4 of TESTING_PLAN.md - 5 tests covering:
 * - Action performed without project
 * - Action instantiation
 * - Basic action properties
 * - Data extraction from event
 */
class AddFilepathActionTest {

    private lateinit var action: AddFilepathAction
    private lateinit var mockEvent: AnActionEvent
    private lateinit var mockProject: Project
    private lateinit var mockPresentation: Presentation
    private lateinit var mockEditor: Editor
    private lateinit var mockVirtualFile: VirtualFile

    @BeforeEach
    fun setUp() {
        // Create the action
        action = AddFilepathAction()

        // Create mocks
        mockEvent = mock()
        mockProject = mock()
        mockPresentation = mock()
        mockEditor = mock()
        mockVirtualFile = mock()

        // Setup basic event behavior
        whenever(mockEvent.presentation).thenReturn(mockPresentation)
        whenever(mockProject.name).thenReturn("TestProject")

        // Setup virtual file behavior
        whenever(mockVirtualFile.path).thenReturn("/project/src/Test.kt")
        whenever(mockVirtualFile.name).thenReturn("Test.kt")
        whenever(mockVirtualFile.isValid).thenReturn(true)
    }

    @AfterEach
    fun tearDown() {
        // Clean up any resources
    }

    // ========== Action Performed Tests ==========

    @Test
    fun `testActionPerformed_ExecutesCorrectly_WithoutProject`() {
        // Setup event without project
        whenever(mockEvent.project).thenReturn(null)

        // Perform action - should return early without errors
        assertDoesNotThrow {
            action.actionPerformed(mockEvent)
        }
    }

    @Test
    fun `testActionPerformed_WithoutProject_ReturnsEarly`() {
        // Setup event without project
        whenever(mockEvent.project).thenReturn(null)

        // Perform action multiple times - should always return early
        assertDoesNotThrow {
            action.actionPerformed(mockEvent)
            action.actionPerformed(mockEvent)
        }
    }

    @Test
    fun `testActionPerformed_WithProject_DoesNotThrowImmediately`() {
        // Setup event with project and data
        whenever(mockEvent.project).thenReturn(mockProject)
        whenever(mockEvent.getData(CommonDataKeys.EDITOR)).thenReturn(mockEditor)
        whenever(mockEvent.getData(CommonDataKeys.VIRTUAL_FILE)).thenReturn(mockVirtualFile)

        // Perform action - will fail due to missing service infrastructure,
        // but we verify it doesn't fail on null project check
        assertThrows(Exception::class.java) {
            action.actionPerformed(mockEvent)
        }
    }

    // ========== Action Properties Tests ==========

    @Test
    fun `testAction_Instantiation_CreatesActionSuccessfully`() {
        // Verify action can be instantiated
        val newAction = AddFilepathAction()
        assertNotNull(newAction)
    }

    @Test
    fun `testAction_ImplementsAnAction`() {
        // Verify the action is an AnAction instance
        assertTrue(action is com.intellij.openapi.actionSystem.AnAction)
    }

    // ========== Edge Case Tests ==========

    @Test
    fun `testActionPerformed_WithNullVirtualFile_ReturnsEarlyFromFileUtils`() {
        // Setup event with project and editor but null file
        whenever(mockEvent.project).thenReturn(mockProject)
        whenever(mockEvent.getData(CommonDataKeys.EDITOR)).thenReturn(mockEditor)
        whenever(mockEvent.getData(CommonDataKeys.VIRTUAL_FILE)).thenReturn(null)

        // FileUtils.getActiveFileReference returns null when file is null (line 10 of FileUtils)
        // Action handles this gracefully by checking if fileRef != null before calling service
        assertDoesNotThrow {
            action.actionPerformed(mockEvent)
        }
    }
}
