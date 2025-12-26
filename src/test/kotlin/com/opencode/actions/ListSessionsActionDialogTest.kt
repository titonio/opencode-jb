package com.opencode.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.opencode.model.SessionInfo
import com.opencode.service.OpenCodeService
import com.opencode.test.TestDataFactory
import com.opencode.ui.SessionListDialog
import com.opencode.vfs.OpenCodeFileSystem
import com.opencode.vfs.OpenCodeVirtualFile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*

/**
 * Comprehensive coverage tests for ListSessionsAction focused on dialog interaction and file opening.
 * 
 * Target: Increase coverage from 36.4% (4/11 lines) to 80%+ (9+/11 lines)
 * 
 * These tests specifically cover the uncovered lines 20-28:
 * - Line 17: Service retrieval from project
 * - Line 20: Dialog construction with project and service
 * - Line 21: Dialog.showAndGet() call and result handling
 * - Lines 22-23: Getting selected session from dialog
 * - Lines 25-27: Virtual file creation and file opening via FileEditorManager
 * 
 * Coverage improvements:
 * - Tests dialog construction and showing
 * - Tests user confirming with session selection  
 * - Tests user cancelling dialog
 * - Tests null session handling
 * - Tests file system and editor manager interaction
 */
class ListSessionsActionDialogTest {
    
    private lateinit var action: ListSessionsAction
    private lateinit var mockEvent: AnActionEvent
    private lateinit var mockProject: Project
    private lateinit var mockPresentation: Presentation
    private lateinit var mockService: OpenCodeService
    
    @BeforeEach
    fun setUp() {
        action = ListSessionsAction()
        mockEvent = mock()
        mockProject = mock()
        mockPresentation = mock()
        mockService = mock()
        
        whenever(mockEvent.presentation).thenReturn(mockPresentation)
        whenever(mockProject.name).thenReturn("TestProject")
        whenever(mockProject.basePath).thenReturn("/test/project")
        whenever(mockProject.getService(OpenCodeService::class.java)).thenReturn(mockService)
    }
    
    @AfterEach
    fun tearDown() {
        // Cleanup
    }
    
    // ========== Dialog Construction Tests ==========
    
    @Test
    fun `test actionPerformed creates SessionListDialog with correct parameters`() {
        var dialogConstructorCalled = false
        var constructorProject: Project? = null
        var constructorService: OpenCodeService? = null
        
        Mockito.mockConstruction(SessionListDialog::class.java) { mock, context ->
            dialogConstructorCalled = true
            if (context.arguments().size >= 2) {
                constructorProject = context.arguments()[0] as? Project
                constructorService = context.arguments()[1] as? OpenCodeService
            }
            // Mock dialog to return false (cancel) to avoid file opening complexity
            whenever(mock.showAndGet()).thenReturn(false)
        }.use {
            whenever(mockEvent.project).thenReturn(mockProject)
            
            action.actionPerformed(mockEvent)
            
            assertTrue(dialogConstructorCalled, "Dialog constructor should be called")
            assertSame(mockProject, constructorProject, "Dialog should receive correct project")
            assertSame(mockService, constructorService, "Dialog should receive correct service")
        }
    }
    
    @Test
    fun `test actionPerformed calls showAndGet on dialog`() {
        var showAndGetCalled = false
        
        Mockito.mockConstruction(SessionListDialog::class.java) { mock, _ ->
            whenever(mock.showAndGet()).thenAnswer {
                showAndGetCalled = true
                false
            }
        }.use {
            whenever(mockEvent.project).thenReturn(mockProject)
            
            action.actionPerformed(mockEvent)
            
            assertTrue(showAndGetCalled, "showAndGet should be called on dialog")
        }
    }
    
    @Test
    fun `test actionPerformed retrieves service from project`() {
        var serviceRetrieved = false
        
        val customMockProject = mock<Project> {
            on { name } doReturn "TestProject"
            on { basePath } doReturn "/test/project"
            on { getService(OpenCodeService::class.java) } doAnswer {
                serviceRetrieved = true
                mockService
            }
        }
        
        Mockito.mockConstruction(SessionListDialog::class.java) { mock, _ ->
            whenever(mock.showAndGet()).thenReturn(false)
        }.use {
            whenever(mockEvent.project).thenReturn(customMockProject)
            
            action.actionPerformed(mockEvent)
            
            assertTrue(serviceRetrieved, "Service should be retrieved from project")
        }
    }
    
    // ========== Dialog Result Handling Tests ==========
    
    @Test
    fun `test actionPerformed when user cancels dialog does not call getSelectedSession`() {
        var getSelectedSessionCalled = false
        
        Mockito.mockConstruction(SessionListDialog::class.java) { mock, _ ->
            whenever(mock.showAndGet()).thenReturn(false)  // User cancelled
            whenever(mock.getSelectedSession()).thenAnswer {
                getSelectedSessionCalled = true
                null
            }
        }.use {
            whenever(mockEvent.project).thenReturn(mockProject)
            
            action.actionPerformed(mockEvent)
            
            // When dialog returns false, we shouldn't even check for selected session
            assertFalse(getSelectedSessionCalled, "getSelectedSession should not be called when user cancels")
        }
    }
    
    @Test
    fun `test actionPerformed when user confirms calls getSelectedSession`() {
        var getSelectedSessionCalled = false
        
        Mockito.mockConstruction(SessionListDialog::class.java) { mock, _ ->
            whenever(mock.showAndGet()).thenReturn(true)  // User confirmed
            whenever(mock.getSelectedSession()).thenAnswer {
                getSelectedSessionCalled = true
                null  // But no session selected
            }
        }.use {
            whenever(mockEvent.project).thenReturn(mockProject)
            
            action.actionPerformed(mockEvent)
            
            assertTrue(getSelectedSessionCalled, "getSelectedSession should be called when user confirms")
        }
    }
    
    @Test
    fun `test actionPerformed when user confirms with null session does not open file`() {
        val fileSystemMock = Mockito.mockStatic(OpenCodeFileSystem::class.java)
        val editorManagerMock = Mockito.mockStatic(FileEditorManager::class.java)
        
        try {
            Mockito.mockConstruction(SessionListDialog::class.java) { mock, _ ->
                whenever(mock.showAndGet()).thenReturn(true)
                whenever(mock.getSelectedSession()).thenReturn(null)  // No session selected
            }.use {
                whenever(mockEvent.project).thenReturn(mockProject)
                
                action.actionPerformed(mockEvent)
                
                // Should not attempt to open file when session is null
                fileSystemMock.verifyNoInteractions()
                editorManagerMock.verifyNoInteractions()
            }
        } finally {
            fileSystemMock.close()
            editorManagerMock.close()
        }
    }
    
    // ========== Session Selection and File Opening Tests ==========
    
    @Test
    fun `test actionPerformed with selected session attempts to open file`() {
        val testSession = TestDataFactory.createSessionInfo(
            id = "test-session-123",
            title = "Test Session"
        )

        // Spy on action to intercept helper
        val spyAction = Mockito.spy(action)
        doNothing().`when`(spyAction).openSessionFile(any(), any())

        Mockito.mockConstruction(SessionListDialog::class.java) { mock, _ ->
            whenever(mock.showAndGet()).thenReturn(true)
            whenever(mock.getSelectedSession()).thenReturn(testSession)
        }.use {
            whenever(mockEvent.project).thenReturn(mockProject)

            spyAction.actionPerformed(mockEvent)

            verify(spyAction).openSessionFile(eq(mockProject), eq(testSession.id))
        }
    }

    @Test
    fun `test actionPerformed with different session IDs`() {
        val sessions = listOf(
            TestDataFactory.createSessionInfo(id = "session-A"),
            TestDataFactory.createSessionInfo(id = "session-B"),
            TestDataFactory.createSessionInfo(id = "session-C")
        )

        val spyAction = Mockito.spy(action)

        whenever(mockEvent.project).thenReturn(mockProject)

        sessions.forEach { session ->
            Mockito.reset(spyAction)
            doNothing().`when`(spyAction).openSessionFile(mockProject, session.id)

            Mockito.mockConstruction(SessionListDialog::class.java) { dialogMock, _ ->
                whenever(dialogMock.showAndGet()).thenReturn(true)
                whenever(dialogMock.getSelectedSession()).thenReturn(session)
            }.use {
                spyAction.actionPerformed(mockEvent)
                verify(spyAction).openSessionFile(eq(mockProject), eq(session.id))
            }
        }
    }
}
