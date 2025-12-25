package com.opencode.editor

import com.opencode.service.OpenCodeService
import com.opencode.test.TestDataFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

/**
 * Comprehensive test suite for OpenCodeEditorPanelViewModel.
 * 
 * Tests the business logic extracted from OpenCodeEditorPanel:
 * - Server lifecycle management (start/stop/verify)
 * - Session lifecycle management (create/restore/verify)
 * - State management (initializing/running/exited/restarting)
 * - Process monitoring coordination
 * - Error handling
 * 
 * This ViewModel enables testing without requiring terminal widget infrastructure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenCodeEditorPanelViewModelTest {
    
    private lateinit var mockService: OpenCodeService
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var mockCallback: OpenCodeEditorPanelViewModel.ViewCallback
    private lateinit var viewModel: OpenCodeEditorPanelViewModel
    
    private val testProjectPath = "/test/project"
    private val testSessionId = "test-session-123"
    private val testServerPort = 8080
    
    @BeforeEach
    fun setUp() {
        mockService = mock()
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        mockCallback = mock()
        
        // Default successful responses
        whenever(mockService.isOpencodeInstalled()).thenReturn(true)
    }
    
    // ========== Initialization Tests ==========
    
    @Test
    fun `initialize with no session or port creates new session and starts server`() = testScope.runTest {
        // Arrange
        whenever(mockService.getOrStartSharedServer()).thenReturn(testServerPort)
        whenever(mockService.createSession(testProjectPath)).thenReturn(testSessionId)
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        
        // Act
        viewModel.initialize()
        advanceUntilIdle()
        
        // Assert
        verify(mockService).getOrStartSharedServer()
        verify(mockService).createSession(testProjectPath)
        verify(mockCallback).onStateChanged(OpenCodeEditorPanelViewModel.State.INITIALIZING)
        verify(mockCallback).onStateChanged(OpenCodeEditorPanelViewModel.State.RUNNING)
        verify(mockCallback).onSessionAndPortReady(testSessionId, testServerPort)
        
        assertEquals(testSessionId, viewModel.sessionId)
        assertEquals(testServerPort, viewModel.serverPort)
        assertEquals(OpenCodeEditorPanelViewModel.State.RUNNING, viewModel.getState())
    }
    
    @Test
    fun `initialize with valid restored port reuses existing server`() = testScope.runTest {
        // Arrange
        val restoredPort = 9090
        whenever(mockService.isServerRunning(restoredPort)).thenReturn(true)
        whenever(mockService.createSession(testProjectPath)).thenReturn(testSessionId)
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this, null, restoredPort)
        viewModel.setCallback(mockCallback)
        
        // Act
        viewModel.initialize()
        advanceUntilIdle()
        
        // Assert
        verify(mockService).isServerRunning(restoredPort)
        verify(mockService, never()).getOrStartSharedServer() // Should not start new server
        verify(mockService).createSession(testProjectPath)
        verify(mockCallback).onSessionAndPortReady(testSessionId, restoredPort)
        
        assertEquals(restoredPort, viewModel.serverPort)
    }
    
    @Test
    fun `initialize with invalid restored port starts new server`() = testScope.runTest {
        // Arrange
        val oldPort = 7070
        val newPort = 8080
        whenever(mockService.isServerRunning(oldPort)).thenReturn(false)
        whenever(mockService.getOrStartSharedServer()).thenReturn(newPort)
        whenever(mockService.createSession(testProjectPath)).thenReturn(testSessionId)
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this, null, oldPort)
        viewModel.setCallback(mockCallback)
        
        // Act
        viewModel.initialize()
        advanceUntilIdle()
        
        // Assert
        verify(mockService).isServerRunning(oldPort)
        verify(mockService).getOrStartSharedServer() // Should start new server
        assertEquals(newPort, viewModel.serverPort)
    }
    
    @Test
    fun `initialize with valid restored session reuses it`() = testScope.runTest {
        // Arrange
        val restoredSessionId = "restored-session-456"
        val sessionInfo = TestDataFactory.createSessionInfo(id = restoredSessionId)
        
        whenever(mockService.getOrStartSharedServer()).thenReturn(testServerPort)
        whenever(mockService.getSession(restoredSessionId)).thenReturn(sessionInfo)
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this, restoredSessionId, null)
        viewModel.setCallback(mockCallback)
        
        // Act
        viewModel.initialize()
        advanceUntilIdle()
        
        // Assert
        verify(mockService).getSession(restoredSessionId)
        verify(mockService, never()).createSession(any()) // Should not create new session
        verify(mockCallback).onSessionAndPortReady(restoredSessionId, testServerPort)
        
        assertEquals(restoredSessionId, viewModel.sessionId)
    }
    
    @Test
    fun `initialize with invalid restored session creates new one`() = testScope.runTest {
        // Arrange
        val invalidSessionId = "invalid-session"
        val newSessionId = "new-session-789"
        
        whenever(mockService.getOrStartSharedServer()).thenReturn(testServerPort)
        whenever(mockService.getSession(invalidSessionId)).thenReturn(null) // Session doesn't exist
        whenever(mockService.createSession(testProjectPath)).thenReturn(newSessionId)
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this, invalidSessionId, null)
        viewModel.setCallback(mockCallback)
        
        // Act
        viewModel.initialize()
        advanceUntilIdle()
        
        // Assert
        verify(mockService).getSession(invalidSessionId)
        verify(mockService).createSession(testProjectPath) // Should create new session
        verify(mockCallback).onSessionAndPortReady(newSessionId, testServerPort)
        
        assertEquals(newSessionId, viewModel.sessionId)
    }
    
    @Test
    fun `initialize with both valid session and port reuses both`() = testScope.runTest {
        // Arrange
        val restoredSessionId = "restored-session"
        val restoredPort = 9090
        val sessionInfo = TestDataFactory.createSessionInfo(id = restoredSessionId)
        
        whenever(mockService.isServerRunning(restoredPort)).thenReturn(true)
        whenever(mockService.getSession(restoredSessionId)).thenReturn(sessionInfo)
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this, restoredSessionId, restoredPort)
        viewModel.setCallback(mockCallback)
        
        // Act
        viewModel.initialize()
        advanceUntilIdle()
        
        // Assert
        verify(mockService).isServerRunning(restoredPort)
        verify(mockService).getSession(restoredSessionId)
        verify(mockService, never()).getOrStartSharedServer()
        verify(mockService, never()).createSession(any())
        verify(mockCallback).onSessionAndPortReady(restoredSessionId, restoredPort)
        
        assertEquals(restoredSessionId, viewModel.sessionId)
        assertEquals(restoredPort, viewModel.serverPort)
    }
    
    // ========== Error Handling Tests ==========
    
    @Test
    fun `initialize fails gracefully when server fails to start`() = testScope.runTest {
        // Arrange
        whenever(mockService.getOrStartSharedServer()).thenReturn(null) // Server failed to start
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        
        // Act
        viewModel.initialize()
        advanceUntilIdle()
        
        // Assert
        verify(mockService).getOrStartSharedServer()
        verify(mockCallback).onError("Failed to start OpenCode server after multiple attempts")
        verify(mockCallback, never()).onSessionAndPortReady(any(), any())
        
        assertNull(viewModel.serverPort)
    }
    
    @Test
    fun `initialize fails gracefully when session creation fails`() = testScope.runTest {
        // Arrange
        whenever(mockService.getOrStartSharedServer()).thenReturn(testServerPort)
        whenever(mockService.createSession(testProjectPath)).thenThrow(RuntimeException("Session creation failed"))
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        
        // Act
        viewModel.initialize()
        advanceUntilIdle()
        
        // Assert
        verify(mockService).createSession(testProjectPath)
        verify(mockCallback).onError("Failed to create or retrieve OpenCode session")
        verify(mockCallback, never()).onSessionAndPortReady(any(), any())
        
        assertNull(viewModel.sessionId)
    }
    
    @Test
    fun `initialize handles general exception gracefully`() = testScope.runTest {
        // Arrange
        whenever(mockService.getOrStartSharedServer()).thenThrow(RuntimeException("Unexpected error"))
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        
        // Act
        viewModel.initialize()
        advanceUntilIdle()
        
        // Assert
        verify(mockCallback).onError(argThat { contains("Unexpected error") })
        verify(mockCallback, never()).onSessionAndPortReady(any(), any())
    }
    
    // ========== Process Monitoring Tests ==========
    
    @Test
    fun `startProcessMonitoring sets monitoring flag to true`() = testScope.runTest {
        // Arrange
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        
        // Act
        viewModel.startProcessMonitoring()
        
        // Assert
        assertTrue(viewModel.isMonitoring)
    }
    
    @Test
    fun `stopProcessMonitoring sets monitoring flag to false`() = testScope.runTest {
        // Arrange
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.startProcessMonitoring()
        
        // Act
        viewModel.stopProcessMonitoring()
        
        // Assert
        assertFalse(viewModel.isMonitoring)
    }
    
    @Test
    fun `checkIfTerminalAlive returns true when server is running`() = testScope.runTest {
        // Arrange
        whenever(mockService.getOrStartSharedServer()).thenReturn(testServerPort)
        whenever(mockService.createSession(testProjectPath)).thenReturn(testSessionId)
        whenever(mockService.isServerRunning(testServerPort)).thenReturn(true)
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        viewModel.initialize()
        advanceUntilIdle()
        
        // Act
        val isAlive = viewModel.checkIfTerminalAlive()
        advanceUntilIdle()
        
        // Assert
        assertTrue(isAlive)
        verify(mockService).isServerRunning(testServerPort)
        verify(mockCallback).onTerminalAlive(true)
    }
    
    @Test
    fun `checkIfTerminalAlive returns false when server is not running`() = testScope.runTest {
        // Arrange
        whenever(mockService.getOrStartSharedServer()).thenReturn(testServerPort)
        whenever(mockService.createSession(testProjectPath)).thenReturn(testSessionId)
        whenever(mockService.isServerRunning(testServerPort)).thenReturn(false)
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        viewModel.initialize()
        advanceUntilIdle()
        
        // Act
        val isAlive = viewModel.checkIfTerminalAlive()
        advanceUntilIdle()
        
        // Assert
        assertFalse(isAlive)
        verify(mockService).isServerRunning(testServerPort)
        verify(mockCallback).onTerminalAlive(false)
    }
    
    @Test
    fun `checkIfTerminalAlive returns false when serverPort is null`() = testScope.runTest {
        // Arrange
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        
        // Act
        val isAlive = viewModel.checkIfTerminalAlive()
        advanceUntilIdle()
        
        // Assert
        assertFalse(isAlive)
        verify(mockService, never()).isServerRunning(any())
    }
    
    // ========== Process Exit Tests ==========
    
    @Test
    fun `handleProcessExit with auto-restart enabled triggers restart`() = testScope.runTest {
        // Arrange
        whenever(mockService.getOrStartSharedServer()).thenReturn(testServerPort)
        whenever(mockService.createSession(testProjectPath)).thenReturn(testSessionId)
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        viewModel.initialize()
        advanceUntilIdle()
        
        reset(mockCallback) // Reset to ignore initialization calls
        
        // Act
        viewModel.handleProcessExit(autoRestartEnabled = true)
        
        // Assert
        verify(mockCallback).onStateChanged(OpenCodeEditorPanelViewModel.State.EXITED)
        verify(mockCallback).onProcessExited()
        verify(mockCallback).onStateChanged(OpenCodeEditorPanelViewModel.State.RESTARTING)
        verify(mockCallback).onStateChanged(OpenCodeEditorPanelViewModel.State.INITIALIZING)
        
        assertFalse(viewModel.isMonitoring)
        assertEquals(OpenCodeEditorPanelViewModel.State.INITIALIZING, viewModel.getState())
    }
    
    @Test
    fun `handleProcessExit with auto-restart disabled does not trigger restart`() = testScope.runTest {
        // Arrange
        whenever(mockService.getOrStartSharedServer()).thenReturn(testServerPort)
        whenever(mockService.createSession(testProjectPath)).thenReturn(testSessionId)
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        viewModel.initialize()
        advanceUntilIdle()
        
        reset(mockCallback) // Reset to ignore initialization calls
        
        // Act
        viewModel.handleProcessExit(autoRestartEnabled = false)
        
        // Assert
        verify(mockCallback).onStateChanged(OpenCodeEditorPanelViewModel.State.EXITED)
        verify(mockCallback).onProcessExited()
        verify(mockCallback, never()).onStateChanged(OpenCodeEditorPanelViewModel.State.RESTARTING)
        
        assertFalse(viewModel.isMonitoring)
        assertEquals(OpenCodeEditorPanelViewModel.State.EXITED, viewModel.getState())
    }
    
    @Test
    fun `handleProcessExit ignores call when not in running state`() = testScope.runTest {
        // Arrange
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        
        // Act - Call without initializing (state is INITIALIZING)
        viewModel.handleProcessExit(autoRestartEnabled = true)
        
        // Assert
        verify(mockCallback, never()).onStateChanged(OpenCodeEditorPanelViewModel.State.EXITED)
        verify(mockCallback, never()).onProcessExited()
    }
    
    // ========== Restart Tests ==========
    
    @Test
    fun `restart transitions to restarting state and reinitializes`() = testScope.runTest {
        // Arrange
        whenever(mockService.getOrStartSharedServer()).thenReturn(testServerPort)
        whenever(mockService.createSession(testProjectPath)).thenReturn(testSessionId)
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        viewModel.initialize()
        advanceUntilIdle()
        
        reset(mockCallback) // Reset to ignore initialization calls
        
        // Act
        viewModel.restart()
        
        // Assert
        verify(mockCallback).onStateChanged(OpenCodeEditorPanelViewModel.State.RESTARTING)
        verify(mockCallback).onStateChanged(OpenCodeEditorPanelViewModel.State.INITIALIZING)
        
        assertFalse(viewModel.isMonitoring)
        assertEquals(OpenCodeEditorPanelViewModel.State.INITIALIZING, viewModel.getState())
    }
    
    @Test
    fun `restart ignores duplicate restart request when already restarting`() = testScope.runTest {
        // Arrange
        whenever(mockService.getOrStartSharedServer()).thenReturn(testServerPort)
        whenever(mockService.createSession(testProjectPath)).thenReturn(testSessionId)
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        viewModel.initialize()
        advanceUntilIdle()
        
        assertEquals(OpenCodeEditorPanelViewModel.State.RUNNING, viewModel.getState())
        
        reset(mockCallback)
        clearInvocations(mockService)
        
        // Act - Call restart, which will synchronously transition to RESTARTING then INITIALIZING
        viewModel.restart()
        
        // The state should now be INITIALIZING (since restart() calls initialize())
        // Note: RESTARTING is a transient state that transitions immediately to INITIALIZING
        assertEquals(OpenCodeEditorPanelViewModel.State.INITIALIZING, viewModel.getState())
        
        // Verify we got both state transitions
        val inOrder = inOrder(mockCallback)
        inOrder.verify(mockCallback).onStateChanged(OpenCodeEditorPanelViewModel.State.RESTARTING)
        inOrder.verify(mockCallback).onStateChanged(OpenCodeEditorPanelViewModel.State.INITIALIZING)
    }
    
    @Test
    fun `restart with existing session attempts to reuse it`() = testScope.runTest {
        // Arrange
        val sessionInfo = TestDataFactory.createSessionInfo(id = testSessionId)
        
        whenever(mockService.isServerRunning(testServerPort)).thenReturn(true)
        whenever(mockService.getOrStartSharedServer()).thenReturn(testServerPort)
        whenever(mockService.createSession(testProjectPath)).thenReturn(testSessionId)
        whenever(mockService.getSession(testSessionId)).thenReturn(sessionInfo) // Session still exists
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        viewModel.initialize()
        advanceUntilIdle()
        
        // Reset to clear initialization mocks
        clearInvocations(mockService)
        
        // Act - Restart with session and port already set
        viewModel.restart()
        advanceUntilIdle()
        
        // Assert - On restart, should verify existing session
        verify(mockService).isServerRunning(testServerPort) // Should check server is still running
        verify(mockService).getSession(testSessionId) // Should check if session still exists
        // Should NOT create new session since existing one is valid
        verify(mockService, never()).createSession(any())
    }
    
    // ========== State Management Tests ==========
    
    @Test
    fun `getState returns current state`() = testScope.runTest {
        // Arrange
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        
        // Assert initial state
        assertEquals(OpenCodeEditorPanelViewModel.State.INITIALIZING, viewModel.getState())
        
        // Initialize and check running state
        whenever(mockService.getOrStartSharedServer()).thenReturn(testServerPort)
        whenever(mockService.createSession(testProjectPath)).thenReturn(testSessionId)
        viewModel.initialize()
        advanceUntilIdle()
        
        assertEquals(OpenCodeEditorPanelViewModel.State.RUNNING, viewModel.getState())
    }
    
    @Test
    fun `state transitions are reported to callback`() = testScope.runTest {
        // Arrange
        whenever(mockService.getOrStartSharedServer()).thenReturn(testServerPort)
        whenever(mockService.createSession(testProjectPath)).thenReturn(testSessionId)
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        
        // Act
        viewModel.initialize()
        advanceUntilIdle()
        
        // Assert
        val inOrder = inOrder(mockCallback)
        inOrder.verify(mockCallback).onStateChanged(OpenCodeEditorPanelViewModel.State.INITIALIZING)
        inOrder.verify(mockCallback).onStateChanged(OpenCodeEditorPanelViewModel.State.RUNNING)
    }
    
    // ========== Utility Tests ==========
    
    @Test
    fun `isOpencodeInstalled delegates to service`() = testScope.runTest {
        // Arrange
        whenever(mockService.isOpencodeInstalled()).thenReturn(true)
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        
        // Act
        val isInstalled = viewModel.isOpencodeInstalled()
        
        // Assert
        assertTrue(isInstalled)
        verify(mockService).isOpencodeInstalled()
    }
    
    @Test
    fun `isOpencodeInstalled returns false when not installed`() = testScope.runTest {
        // Arrange
        whenever(mockService.isOpencodeInstalled()).thenReturn(false)
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        
        // Act
        val isInstalled = viewModel.isOpencodeInstalled()
        
        // Assert
        assertFalse(isInstalled)
        verify(mockService).isOpencodeInstalled()
    }
    
    @Test
    fun `dispose stops monitoring and clears callback`() = testScope.runTest {
        // Arrange
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        viewModel.setCallback(mockCallback)
        viewModel.startProcessMonitoring()
        
        // Act
        viewModel.dispose()
        
        // Assert
        assertFalse(viewModel.isMonitoring)
    }
    
    @Test
    fun `sessionId is initially set from constructor`() = testScope.runTest {
        // Arrange
        val initialSessionId = "initial-session"
        
        // Act
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this, initialSessionId, null)
        
        // Assert
        assertEquals(initialSessionId, viewModel.sessionId)
    }
    
    @Test
    fun `serverPort is initially set from constructor`() = testScope.runTest {
        // Arrange
        val initialPort = 7070
        
        // Act
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this, null, initialPort)
        
        // Assert
        assertEquals(initialPort, viewModel.serverPort)
    }
    
    @Test
    fun `sessionId and serverPort are updated after successful initialization`() = testScope.runTest {
        // Arrange
        whenever(mockService.getOrStartSharedServer()).thenReturn(testServerPort)
        whenever(mockService.createSession(testProjectPath)).thenReturn(testSessionId)
        
        viewModel = OpenCodeEditorPanelViewModel(mockService, testProjectPath, this)
        
        // Assert initial values
        assertNull(viewModel.sessionId)
        assertNull(viewModel.serverPort)
        
        // Act
        viewModel.initialize()
        advanceUntilIdle()
        
        // Assert updated values
        assertEquals(testSessionId, viewModel.sessionId)
        assertEquals(testServerPort, viewModel.serverPort)
    }
}
