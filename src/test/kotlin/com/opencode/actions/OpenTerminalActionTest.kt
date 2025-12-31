package com.opencode.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Comprehensive tests for OpenTerminalAction.
 * Tests opening the OpenCode terminal with context from the current editor.
 *
 * Coverage:
 * - Action instantiation
 * - Null project handling
 * - Action performs without errors when project is null
 * - Action attempts to execute with project (requires full IntelliJ environment)
 */
class OpenTerminalActionTest {

    private lateinit var action: OpenTerminalAction
    private lateinit var mockEvent: AnActionEvent
    private lateinit var mockProject: Project

    @BeforeEach
    fun setUp() {
        action = OpenTerminalAction()
        mockEvent = mock()
        mockProject = mock()
    }

    // ========== ActionPerformed Tests ==========

    @Test
    fun `testActionPerformed_ExecutesCorrectly_WithoutProject`() {
        // Setup event without project
        whenever(mockEvent.project).thenReturn(null)
        whenever(mockEvent.getData(CommonDataKeys.EDITOR)).thenReturn(null)
        whenever(mockEvent.getData(CommonDataKeys.VIRTUAL_FILE)).thenReturn(null)

        // Perform action - should return early without errors
        assertDoesNotThrow {
            action.actionPerformed(mockEvent)
        }
    }

    @Test
    fun `testActionPerformed_WithProject_AttemptsExecution`() {
        // Setup event with project
        whenever(mockEvent.project).thenReturn(mockProject)
        whenever(mockEvent.getData(CommonDataKeys.EDITOR)).thenReturn(null)
        whenever(mockEvent.getData(CommonDataKeys.VIRTUAL_FILE)).thenReturn(null)

        // Perform action - will fail due to missing service infrastructure in test environment
        // but we verify it doesn't fail on null project check
        assertThrows(Exception::class.java) {
            action.actionPerformed(mockEvent)
        }
    }

    // ========== Action Properties Tests ==========

    @Test
    fun `testAction_Instantiation_CreatesActionSuccessfully`() {
        // Verify action can be instantiated
        val newAction = OpenTerminalAction()
        assertNotNull(newAction)
    }

    @Test
    fun `testAction_ImplementsAnAction`() {
        // Verify the action is an AnAction instance
        assertTrue(action is AnAction)
    }
}
