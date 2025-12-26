package com.opencode.editor

import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.Disposable
import com.opencode.service.OpenCodeService
import com.opencode.vfs.OpenCodeVirtualFile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.mockito.kotlin.*
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * Comprehensive integration tests for OpenCodeFileEditor.
 * 
 * These tests cover the FileEditor implementation including:
 * - Editor lifecycle (creation, disposal)
 * - State management (save/restore)
 * - Session and port tracking
 * - Component retrieval
 * - Property change notifications
 * - Tab selection/deselection
 * 
 * Coverage targets:
 * - OpenCodeFileEditor (currently 0% coverage, 75 lines, 19 methods)
 * - OpenCodeEditorState (currently ~75% coverage, can improve to 100%)
 */
class OpenCodeFileEditorIntegrationTest {
    
    private lateinit var mockProject: Project
    private lateinit var mockService: OpenCodeService
    private lateinit var mockFile: OpenCodeVirtualFile
    private lateinit var mockApplication: Application
    private lateinit var testDisposable: Disposable
    
    @BeforeEach
    fun setup() {
        mockProject = mock()
        mockService = mock()
        mockFile = mock()
        
        // Setup basic mocks
        whenever(mockProject.name).thenReturn("TestProject")
        whenever(mockProject.basePath).thenReturn("/test/project")
        whenever(mockProject.getService(OpenCodeService::class.java)).thenReturn(mockService)
        
        whenever(mockFile.name).thenReturn("test-session.opencode")
        whenever(mockFile.path).thenReturn("/test/test-session.opencode")
        whenever(mockFile.isValid).thenReturn(true)
        whenever(mockFile.sessionId).thenReturn(null)
        
        whenever(mockService.isOpencodeInstalled()).thenReturn(true)

        // Setup Application mock
        mockApplication = mock()
        testDisposable = Disposer.newDisposable()
        
        // Mock invokeLater to do nothing to avoid UI creation issues in headless env
        whenever(mockApplication.invokeLater(any())).thenAnswer { 
            // Do nothing
        }
        
        // Mock executeOnPooledThread to run immediately
        whenever(mockApplication.executeOnPooledThread(any<Runnable>())).thenAnswer {
            (it.arguments[0] as Runnable).run()
            mock<java.util.concurrent.Future<*>>()
        }
        
        ApplicationManager.setApplication(mockApplication, testDisposable)

        // Setup common service mocks for coroutines
        runBlocking {
            whenever(mockService.isServerRunning(any())).thenReturn(false)
            whenever(mockService.createSession(anyOrNull())).thenReturn("test-session-id")
            whenever(mockService.getOrStartSharedServer()).thenReturn(12345)
        }
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(testDisposable)
    }
    
    // ========== Editor Creation and Initialization ==========
    
    @Test
    fun `test editor creation with valid project and file`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertNotNull(editor)
        assertEquals("OpenCode", editor.name)
    }
    
    @Test
    fun `test editor registers with service on creation`() {
        OpenCodeFileEditor(mockProject, mockFile)
        
        verify(mockService).registerActiveEditor(mockFile)
    }
    
    @org.junit.jupiter.api.Disabled("Requires platform UI services (Messages.showErrorDialog) - cannot mock static calls")
    @Test
    fun `test editor creation when opencode not installed`() {
        whenever(mockService.isOpencodeInstalled()).thenReturn(false)
        
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertNotNull(editor)
        verify(mockService, never()).registerActiveEditor(any())
    }
    
    @Test
    fun `test editor extracts session from virtual file`() {
        whenever(mockFile.sessionId).thenReturn("test-session-123")
        
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertEquals("test-session-123", editor.getCurrentSessionId())
    }
    
    // ========== Component Retrieval ==========
    
    @org.junit.jupiter.api.Disabled("Requires platform UI services - OpenCodeEditorPanel initialization needs terminal infrastructure")
    @Test
    fun `test getComponent returns valid JComponent`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val component = editor.component
        
        assertNotNull(component)
        assertTrue(component is JComponent)
    }
    
    @org.junit.jupiter.api.Disabled("Requires platform UI services (Messages.showErrorDialog) - cannot mock static calls")
    @Test
    fun `test getComponent returns placeholder when opencode not installed`() {
        whenever(mockService.isOpencodeInstalled()).thenReturn(false)
        
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        val component = editor.component
        
        assertNotNull(component)
    }
    
    @org.junit.jupiter.api.Disabled("Requires platform UI services - OpenCodeEditorPanel initialization needs terminal infrastructure")
    @Test
    fun `test getPreferredFocusedComponent returns component`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        // First call getComponent to initialize
        editor.component
        
        val focusedComponent = editor.preferredFocusedComponent
        
        // May be null before panel is fully initialized
        // Just verify it doesn't throw
        assertDoesNotThrow { editor.preferredFocusedComponent }
    }
    
    // ========== State Management ==========
    
    @Test
    fun `test getState returns OpenCodeEditorState`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val state = editor.getState(FileEditorStateLevel.FULL)
        
        assertNotNull(state)
        assertTrue(state is OpenCodeEditorState)
    }
    
    @Test
    fun `test getState preserves null session and port`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val state = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        
        assertNull(state.sessionId)
        assertNull(state.serverPort)
    }
    
    @org.junit.jupiter.api.Disabled("Requires platform UI services - OpenCodeEditorPanel initialization needs terminal infrastructure")
    @Test
    fun `test getState preserves session and port`() {
        whenever(mockFile.sessionId).thenReturn("session-abc")
        
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        // Simulate session/port being set
        val stateToRestore = OpenCodeEditorState("session-abc", 8000)
        editor.setState(stateToRestore)
        
        val state = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        
        assertEquals("session-abc", state.sessionId)
        assertEquals(8000, state.serverPort)
    }
    
    @Test
    fun `test setState restores session and port`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val state = OpenCodeEditorState("restored-session", 9000)
        editor.setState(state)
        
        assertEquals("restored-session", editor.getCurrentSessionId())
    }
    
    @Test
    fun `test setState with null values`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val state = OpenCodeEditorState(null, null)
        
        assertDoesNotThrow {
            editor.setState(state)
        }
    }
    
    @Test
    fun `test setState with non-OpenCodeEditorState is handled gracefully`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        val mockState = mock<com.intellij.openapi.fileEditor.FileEditorState>()
        
        assertDoesNotThrow {
            editor.setState(mockState)
        }
    }
    
    // ========== OpenCodeEditorState Tests ==========
    
    @Test
    fun `test OpenCodeEditorState canBeMergedWith same session`() {
        val state1 = OpenCodeEditorState("session-1", 8000)
        val state2 = OpenCodeEditorState("session-1", 8001)
        
        assertTrue(state1.canBeMergedWith(state2, FileEditorStateLevel.FULL))
    }
    
    @Test
    fun `test OpenCodeEditorState canBeMergedWith different session`() {
        val state1 = OpenCodeEditorState("session-1", 8000)
        val state2 = OpenCodeEditorState("session-2", 8000)
        
        assertFalse(state1.canBeMergedWith(state2, FileEditorStateLevel.FULL))
    }
    
    @Test
    fun `test OpenCodeEditorState canBeMergedWith null sessions`() {
        val state1 = OpenCodeEditorState(null, 8000)
        val state2 = OpenCodeEditorState(null, 8001)
        
        assertTrue(state1.canBeMergedWith(state2, FileEditorStateLevel.FULL))
    }
    
    @Test
    fun `test OpenCodeEditorState canBeMergedWith different state type`() {
        val state1 = OpenCodeEditorState("session-1", 8000)
        val state2 = mock<com.intellij.openapi.fileEditor.FileEditorState>()
        
        assertFalse(state1.canBeMergedWith(state2, FileEditorStateLevel.FULL))
    }
    
    // ========== Tab Selection Notifications ==========
    
    @Test
    fun `test selectNotify is called successfully`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertDoesNotThrow {
            editor.selectNotify()
        }
    }
    
    @Test
    fun `test deselectNotify is called successfully`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertDoesNotThrow {
            editor.deselectNotify()
        }
    }
    
    // ========== Property Change Listeners ==========
    
    @Test
    fun `test addPropertyChangeListener does not throw`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        val listener = mock<PropertyChangeListener>()
        
        assertDoesNotThrow {
            editor.addPropertyChangeListener(listener)
        }
    }
    
    @Test
    fun `test removePropertyChangeListener does not throw`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        val listener = mock<PropertyChangeListener>()
        
        assertDoesNotThrow {
            editor.addPropertyChangeListener(listener)
            editor.removePropertyChangeListener(listener)
        }
    }
    
    // ========== Editor Properties ==========
    
    @Test
    fun `test isModified returns false`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertFalse(editor.isModified)
    }
    
    @Test
    fun `test isValid returns true`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertTrue(editor.isValid)
    }
    
    @Test
    fun `test getName returns OpenCode`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertEquals("OpenCode", editor.name)
    }
    
    @Test
    fun `test getCurrentLocation returns null`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertNull(editor.currentLocation)
    }
    
    @Test
    fun `test getFile returns original file`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertEquals(mockFile, editor.file)
    }
    
    // ========== Disposal ==========
    
    @Test
    fun `test dispose unregisters editor from service`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        editor.dispose()
        
        verify(mockService).unregisterActiveEditor(mockFile)
    }
    
    @Test
    fun `test dispose can be called multiple times`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertDoesNotThrow {
            editor.dispose()
            editor.dispose()
        }
    }
    
    @Test
    fun `test dispose cleans up editor panel`() {
        // Setup service mock for successful initialization
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        // Initialize component to create editorPanel
        editor.component
        
        // Verify editorPanel was created by checking interactions
        // Note: Can't easily verify private fields, so we rely on behavior
        
        assertDoesNotThrow {
            editor.dispose()
        }
        
        // Verify unregistration
        verify(mockService).unregisterActiveEditor(mockFile)
    }
    
    // ========== Session Management ==========
    
    @Test
    fun `test getCurrentSessionId returns null initially`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertNull(editor.getCurrentSessionId())
    }
    
    @Test
    fun `test getCurrentSessionId returns session after setState`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        editor.setState(OpenCodeEditorState("new-session", 8080))
        
        assertEquals("new-session", editor.getCurrentSessionId())
    }
    
    // ========== Edge Cases ==========
    
    @Test
    fun `test editor with session in file and setState override`() {
        whenever(mockFile.sessionId).thenReturn("file-session")
        
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        assertEquals("file-session", editor.getCurrentSessionId())
        
        // setState should override
        editor.setState(OpenCodeEditorState("state-session", 9000))
        assertEquals("state-session", editor.getCurrentSessionId())
    }
    
    @org.junit.jupiter.api.Disabled("Requires platform UI services - OpenCodeEditorPanel initialization needs terminal infrastructure")
    @Test
    fun `test multiple getComponent calls return same component`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val component1 = editor.component
        val component2 = editor.component
        
        assertSame(component1, component2)
    }
    
    @Test
    fun `test editor state serialization cycle`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        // Set initial state
        val originalState = OpenCodeEditorState("cycle-test", 7777)
        editor.setState(originalState)
        
        // Get state
        val retrievedState = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        
        // Verify
        assertEquals(originalState.sessionId, retrievedState.sessionId)
        assertEquals(originalState.serverPort, retrievedState.serverPort)
    }
    
    @Test
    fun `test getState with different state levels`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        editor.setState(OpenCodeEditorState("test", 8000))
        
        val fullState = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        val undoState = editor.getState(FileEditorStateLevel.UNDO) as OpenCodeEditorState
        val navigationState = editor.getState(FileEditorStateLevel.NAVIGATION) as OpenCodeEditorState
        
        assertEquals("test", fullState.sessionId)
        assertEquals("test", undoState.sessionId)
        assertEquals("test", navigationState.sessionId)
    }
    
    // ========== Additional Coverage Tests ==========
    
    @org.junit.jupiter.api.Disabled("Requires platform UI services (Messages.showErrorDialog) - cannot test when OpenCode not installed")
    @Test
    fun `test getComponent when opencode not available shows placeholder`() {
        whenever(mockService.isOpencodeInstalled()).thenReturn(false)
        
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        val component = editor.component
        
        assertNotNull(component)
        // Verify it's the container panel
        assertTrue(component is JComponent)
    }
    
    @org.junit.jupiter.api.Disabled("Requires platform UI services (Messages.showErrorDialog) - cannot test when OpenCode not installed")
    @Test
    fun `test getComponent multiple calls with opencode not available`() {
        whenever(mockService.isOpencodeInstalled()).thenReturn(false)
        
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val component1 = editor.component
        val component2 = editor.component
        
        assertNotNull(component1)
        assertNotNull(component2)
        assertSame(component1, component2)
    }
    
    @Test
    fun `test getPreferredFocusedComponent when editor panel is null`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        // Don't call getComponent, so editorPanel stays null
        val focusedComponent = editor.preferredFocusedComponent
        
        // Should return null since editorPanel is not initialized
        assertNull(focusedComponent)
    }
    
    @Test
    fun `test dispose without initializing component`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        // Dispose without calling getComponent
        assertDoesNotThrow {
            editor.dispose()
        }
        
        verify(mockService).unregisterActiveEditor(mockFile)
    }
    
    @Test
    fun `test editor state with only sessionId set`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val state = OpenCodeEditorState("only-session", null)
        editor.setState(state)
        
        val retrieved = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        assertEquals("only-session", retrieved.sessionId)
        assertNull(retrieved.serverPort)
    }
    
    @Test
    fun `test editor state with only port set`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val state = OpenCodeEditorState(null, 9999)
        editor.setState(state)
        
        val retrieved = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        assertNull(retrieved.sessionId)
        assertEquals(9999, retrieved.serverPort)
    }
    
    @Test
    fun `test OpenCodeEditorState with mixed null sessions in canBeMergedWith`() {
        val state1 = OpenCodeEditorState(null, 8000)
        val state2 = OpenCodeEditorState("session-2", 8000)
        
        assertFalse(state1.canBeMergedWith(state2, FileEditorStateLevel.FULL))
        assertFalse(state2.canBeMergedWith(state1, FileEditorStateLevel.FULL))
    }
    
    @Test
    fun `test OpenCodeEditorState canBeMergedWith different FileEditorStateLevels`() {
        val state1 = OpenCodeEditorState("session-1", 8000)
        val state2 = OpenCodeEditorState("session-1", 8001)
        
        assertTrue(state1.canBeMergedWith(state2, FileEditorStateLevel.FULL))
        assertTrue(state1.canBeMergedWith(state2, FileEditorStateLevel.UNDO))
        assertTrue(state1.canBeMergedWith(state2, FileEditorStateLevel.NAVIGATION))
    }
    
    @Test
    fun `test selectNotify after deselectNotify`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertDoesNotThrow {
            editor.selectNotify()
            editor.deselectNotify()
            editor.selectNotify()
        }
    }
    
    @Test
    fun `test multiple setState calls`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        editor.setState(OpenCodeEditorState("first", 8000))
        assertEquals("first", editor.getCurrentSessionId())
        
        editor.setState(OpenCodeEditorState("second", 8001))
        assertEquals("second", editor.getCurrentSessionId())
        
        editor.setState(OpenCodeEditorState("third", 8002))
        assertEquals("third", editor.getCurrentSessionId())
    }
    
    @Test
    fun `test getState after multiple setState calls returns latest`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        editor.setState(OpenCodeEditorState("first", 8000))
        editor.setState(OpenCodeEditorState("second", 8001))
        editor.setState(OpenCodeEditorState("third", 8002))
        
        val state = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        assertEquals("third", state.sessionId)
        assertEquals(8002, state.serverPort)
    }
    
    @Test
    fun `test editor with virtual file having null sessionId`() {
        whenever(mockFile.sessionId).thenReturn(null)
        
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertNull(editor.getCurrentSessionId())
        verify(mockService).registerActiveEditor(mockFile)
    }
    
    @org.junit.jupiter.api.Disabled("Requires platform UI services (Messages.showErrorDialog) - cannot test when OpenCode not installed")
    @Test
    fun `test editor does not register when opencode not installed`() {
        whenever(mockService.isOpencodeInstalled()).thenReturn(false)
        
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        verify(mockService, never()).registerActiveEditor(any())
    }
    
    @Test
    fun `test getFile returns correct virtual file`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val returnedFile = editor.file
        
        assertSame(mockFile, returnedFile)
        assertEquals("test-session.opencode", returnedFile.name)
    }
    
    @Test
    fun `test OpenCodeEditorState equality`() {
        val state1 = OpenCodeEditorState("session-1", 8000)
        val state2 = OpenCodeEditorState("session-1", 8000)
        val state3 = OpenCodeEditorState("session-2", 8000)
        
        assertEquals(state1, state2)
        assertNotEquals(state1, state3)
    }
    
    @Test
    fun `test OpenCodeEditorState with different ports same session`() {
        val state1 = OpenCodeEditorState("session-1", 8000)
        val state2 = OpenCodeEditorState("session-1", 9000)
        
        // They are not equal (different port)
        assertNotEquals(state1, state2)
        // But they can be merged (same session)
        assertTrue(state1.canBeMergedWith(state2, FileEditorStateLevel.FULL))
    }
    
    @Test
    fun `test editor initialization with session from file`() {
        val sessionId = "file-based-session-id"
        whenever(mockFile.sessionId).thenReturn(sessionId)
        
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        assertEquals(sessionId, editor.getCurrentSessionId())
    }
    
    // ========== Enhanced State Management Tests ==========
    
    @Test
    fun `test state restoration at FULL level with complex session IDs`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val complexSessionId = "session-2024-12-25T10:30:45Z-user123-project-alpha-beta"
        val state = OpenCodeEditorState(complexSessionId, 12345)
        
        editor.setState(state)
        
        val retrievedState = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        
        assertEquals(complexSessionId, retrievedState.sessionId)
        assertEquals(12345, retrievedState.serverPort)
    }
    
    @Test
    fun `test state restoration at NAVIGATION level returns same state structure`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val state = OpenCodeEditorState("navigation-session", 8080)
        editor.setState(state)
        
        val retrievedState = editor.getState(FileEditorStateLevel.NAVIGATION) as OpenCodeEditorState
        
        // Current implementation returns same state for all levels
        assertEquals("navigation-session", retrievedState.sessionId)
        assertEquals(8080, retrievedState.serverPort)
    }
    
    @Test
    fun `test state restoration at UNDO level returns same state structure`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val state = OpenCodeEditorState("undo-session", 9090)
        editor.setState(state)
        
        val retrievedState = editor.getState(FileEditorStateLevel.UNDO) as OpenCodeEditorState
        
        // Current implementation returns same state for all levels
        assertEquals("undo-session", retrievedState.sessionId)
        assertEquals(9090, retrievedState.serverPort)
    }
    
    @Test
    fun `test state merging with identical session IDs`() {
        val state1 = OpenCodeEditorState("identical-session", 8000)
        val state2 = OpenCodeEditorState("identical-session", 8100)
        
        // States with same session ID can be merged
        assertTrue(state1.canBeMergedWith(state2, FileEditorStateLevel.FULL))
        assertTrue(state2.canBeMergedWith(state1, FileEditorStateLevel.FULL))
    }
    
    @Test
    fun `test state merging with different session IDs`() {
        val state1 = OpenCodeEditorState("session-alpha", 8000)
        val state2 = OpenCodeEditorState("session-beta", 8000)
        
        // States with different session IDs cannot be merged
        assertFalse(state1.canBeMergedWith(state2, FileEditorStateLevel.FULL))
        assertFalse(state2.canBeMergedWith(state1, FileEditorStateLevel.FULL))
    }
    
    @Test
    fun `test multiple setState and getState cycles preserve data`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        // Cycle 1
        editor.setState(OpenCodeEditorState("cycle1", 7000))
        var retrieved = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        assertEquals("cycle1", retrieved.sessionId)
        assertEquals(7000, retrieved.serverPort)
        
        // Cycle 2
        editor.setState(OpenCodeEditorState("cycle2", 7100))
        retrieved = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        assertEquals("cycle2", retrieved.sessionId)
        assertEquals(7100, retrieved.serverPort)
        
        // Cycle 3
        editor.setState(OpenCodeEditorState("cycle3", 7200))
        retrieved = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        assertEquals("cycle3", retrieved.sessionId)
        assertEquals(7200, retrieved.serverPort)
    }
    
    @Test
    fun `test state persistence across select and deselect cycles`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val state = OpenCodeEditorState("persistent-session", 5555)
        editor.setState(state)
        
        // Simulate select/deselect cycles
        editor.selectNotify()
        editor.deselectNotify()
        editor.selectNotify()
        editor.deselectNotify()
        
        // State should persist
        val retrieved = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        assertEquals("persistent-session", retrieved.sessionId)
        assertEquals(5555, retrieved.serverPort)
    }
    
    @Test
    fun `test complex lifecycle create setState select deselect setState getState`() {
        // Step 1: Create editor
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        assertNull(editor.getCurrentSessionId())
        
        // Step 2: Set initial state
        editor.setState(OpenCodeEditorState("initial-session", 3000))
        assertEquals("initial-session", editor.getCurrentSessionId())
        
        // Step 3: Select editor
        editor.selectNotify()
        
        // Step 4: Deselect editor
        editor.deselectNotify()
        
        // Step 5: Update state after deselect
        editor.setState(OpenCodeEditorState("updated-session", 3100))
        assertEquals("updated-session", editor.getCurrentSessionId())
        
        // Step 6: Get final state
        val finalState = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        assertEquals("updated-session", finalState.sessionId)
        assertEquals(3100, finalState.serverPort)
    }
    
    @Test
    fun `test state with very long session ID`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val veryLongSessionId = "session-" + "a".repeat(500) + "-end"
        val state = OpenCodeEditorState(veryLongSessionId, 6000)
        
        editor.setState(state)
        
        val retrieved = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        assertEquals(veryLongSessionId, retrieved.sessionId)
        assertEquals(6000, retrieved.serverPort)
    }
    
    @Test
    fun `test state with special characters in session ID`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val specialSessionId = "session-!@#$%^&*()_+-=[]{}|;':\",./<>?~`"
        val state = OpenCodeEditorState(specialSessionId, 7500)
        
        editor.setState(state)
        
        val retrieved = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        assertEquals(specialSessionId, retrieved.sessionId)
        assertEquals(7500, retrieved.serverPort)
    }
    
    @Test
    fun `test state with unicode characters in session ID`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val unicodeSessionId = "session-测试-🚀-αβγ-עברית"
        val state = OpenCodeEditorState(unicodeSessionId, 8500)
        
        editor.setState(state)
        
        val retrieved = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        assertEquals(unicodeSessionId, retrieved.sessionId)
        assertEquals(8500, retrieved.serverPort)
    }
    
    @Test
    fun `test state merging with null and non-null session combinations`() {
        val stateWithSession = OpenCodeEditorState("has-session", 8000)
        val stateWithoutSession = OpenCodeEditorState(null, 8000)
        
        // Different session ID status means they cannot be merged
        assertFalse(stateWithSession.canBeMergedWith(stateWithoutSession, FileEditorStateLevel.FULL))
        assertFalse(stateWithoutSession.canBeMergedWith(stateWithSession, FileEditorStateLevel.FULL))
    }
    
    @Test
    fun `test getState consistency across all FileEditorStateLevel values`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        editor.setState(OpenCodeEditorState("consistent-session", 4444))
        
        val fullState = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        val undoState = editor.getState(FileEditorStateLevel.UNDO) as OpenCodeEditorState
        val navState = editor.getState(FileEditorStateLevel.NAVIGATION) as OpenCodeEditorState
        
        // All levels should return same data in current implementation
        assertEquals(fullState.sessionId, undoState.sessionId)
        assertEquals(fullState.sessionId, navState.sessionId)
        assertEquals(fullState.serverPort, undoState.serverPort)
        assertEquals(fullState.serverPort, navState.serverPort)
    }
    
    @Test
    fun `test state with extreme port values`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        // Test with maximum valid port
        editor.setState(OpenCodeEditorState("max-port", 65535))
        var retrieved = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        assertEquals(65535, retrieved.serverPort)
        
        // Test with minimum valid port
        editor.setState(OpenCodeEditorState("min-port", 1))
        retrieved = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        assertEquals(1, retrieved.serverPort)
    }
    
    @Test
    fun `test setState followed immediately by getState preserves all data`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        val originalState = OpenCodeEditorState("immediate-session", 11111)
        editor.setState(originalState)
        
        val retrievedState = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        
        assertEquals(originalState.sessionId, retrievedState.sessionId)
        assertEquals(originalState.serverPort, retrievedState.serverPort)
    }
    
    @Test
    fun `test state merging respects all FileEditorStateLevel values`() {
        val state1 = OpenCodeEditorState("merge-session", 2000)
        val state2 = OpenCodeEditorState("merge-session", 2100)
        
        // Should be mergeable at all levels
        assertTrue(state1.canBeMergedWith(state2, FileEditorStateLevel.FULL))
        assertTrue(state1.canBeMergedWith(state2, FileEditorStateLevel.NAVIGATION))
        assertTrue(state1.canBeMergedWith(state2, FileEditorStateLevel.UNDO))
    }
    
    @Test
    fun `test complex lifecycle with interleaved setState and notification calls`() {
        val editor = OpenCodeFileEditor(mockProject, mockFile)
        
        editor.setState(OpenCodeEditorState("state1", 1000))
        editor.selectNotify()
        
        editor.setState(OpenCodeEditorState("state2", 1100))
        editor.deselectNotify()
        
        editor.selectNotify()
        editor.setState(OpenCodeEditorState("state3", 1200))
        
        val finalState = editor.getState(FileEditorStateLevel.FULL) as OpenCodeEditorState
        assertEquals("state3", finalState.sessionId)
        assertEquals(1200, finalState.serverPort)
    }
}
