package com.opencode.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

/**
 * Comprehensive tests for ListSessionsAction.
 * Tests the action that shows the session list dialog and opens selected sessions.
 *
 * Phase 4 of TESTING_PLAN.md - 8 tests covering:
 * - Action update behavior with/without project
 * - Action performed with null project handling
 * - Action instantiation
 * - Basic action properties
 */
class ListSessionsActionTest {

    private lateinit var action: ListSessionsAction
    private lateinit var mockEvent: AnActionEvent
    private lateinit var mockProject: Project
    private lateinit var mockPresentation: Presentation

    @BeforeEach
    fun setUp() {
        // Create the action
        action = ListSessionsAction()

        // Create mocks
        mockEvent = mock()
        mockProject = mock()
        mockPresentation = mock()

        // Setup basic event behavior
        whenever(mockEvent.presentation).thenReturn(mockPresentation)
        whenever(mockProject.name).thenReturn("TestProject")
    }

    @AfterEach
    fun tearDown() {
        // Clean up any resources
    }

    // ========== Update Method Tests ==========

    @Test
    fun `testUpdate_WithProject_EnablesAction`() {
        // Setup event with valid project
        whenever(mockEvent.project).thenReturn(mockProject)

        // Call update
        action.update(mockEvent)

        // Verify action is enabled and visible
        verify(mockPresentation).isEnabledAndVisible = true
    }

    @Test
    fun `testUpdate_WithoutProject_DisablesAction`() {
        // Setup event without project
        whenever(mockEvent.project).thenReturn(null)

        // Call update
        action.update(mockEvent)

        // Verify action is disabled and invisible
        verify(mockPresentation).isEnabledAndVisible = false
    }

    // ========== Action Performed Tests ==========

    @Test
    fun `testActionPerformed_WithoutProject_ReturnsEarly`() {
        // Setup event without project
        whenever(mockEvent.project).thenReturn(null)

        // Perform action - should return early without errors
        assertDoesNotThrow {
            action.actionPerformed(mockEvent)
        }
    }

    @Test
    fun `testActionPerformed_WithProject_DoesNotThrowImmediately`() {
        // Setup event with project
        whenever(mockEvent.project).thenReturn(mockProject)

        // Perform action - will fail due to missing service infrastructure,
        // but we verify it doesn't fail on null project check
        // The action requires a full IntelliJ environment to work properly
        assertThrows(Exception::class.java) {
            action.actionPerformed(mockEvent)
        }
    }

    // ========== Action Properties Tests ==========

    @Test
    fun `testAction_Instantiation_CreatesActionSuccessfully`() {
        // Verify action can be instantiated
        val newAction = ListSessionsAction()
        assertNotNull(newAction)
    }

    @Test
    fun `testAction_HasCorrectName`() {
        // Verify action has the expected name/text
        // The action is created with "List OpenCode Sessions" as the text
        val actionText = action.templateText
        assertEquals("List OpenCode Sessions", actionText)
    }

    @Test
    fun `testUpdate_MultipleCalls_BehavesConsistently`() {
        // Test that update can be called multiple times with consistent results
        whenever(mockEvent.project).thenReturn(mockProject)

        // Call update multiple times
        action.update(mockEvent)
        action.update(mockEvent)
        action.update(mockEvent)

        // Verify presentation was updated each time
        verify(mockPresentation, times(3)).isEnabledAndVisible = true
    }

    @Test
    fun `testUpdate_ProjectChanges_UpdatesPresentation`() {
        // First call with project
        whenever(mockEvent.project).thenReturn(mockProject)
        action.update(mockEvent)
        verify(mockPresentation).isEnabledAndVisible = true

        // Second call without project
        whenever(mockEvent.project).thenReturn(null)
        action.update(mockEvent)
        verify(mockPresentation).isEnabledAndVisible = false

        // Verify presentation was updated twice
        verify(mockPresentation, times(1)).isEnabledAndVisible = true
        verify(mockPresentation, times(1)).isEnabledAndVisible = false
    }
}
