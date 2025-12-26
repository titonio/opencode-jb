package com.opencode.ui

import com.opencode.model.SessionInfo
import com.opencode.service.OpenCodeService
import com.opencode.test.MockServerManager
import com.opencode.test.TestDataFactory
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

/**
 * Comprehensive test suite for SessionListViewModel.
 * Tests all business logic for session management without UI dependencies.
 * 
 * Tests cover:
 * - Session loading and refreshing
 * - Session creation
 * - Session deletion
 * - Session sharing/unsharing
 * - Selection management
 * - Error handling
 * - Callback notifications
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionListViewModelTest {
    
    private lateinit var mockService: OpenCodeService
    private lateinit var mockCallback: SessionListViewModel.ViewCallback
    private lateinit var viewModel: SessionListViewModel
    
    private lateinit var testScope: kotlinx.coroutines.CoroutineScope
    
    @BeforeEach
    fun setUp() {
        
        testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        mockService = mock()
        mockCallback = mock()
        viewModel = SessionListViewModel(mockService, testScope)
        viewModel.setCallback(mockCallback)
    }
    
    // ========== Loading Tests ==========
    
    @Test
    fun `loadSessions calls service and notifies callback with results`() = runBlocking {
        // Arrange
        val sessions = listOf(
            TestDataFactory.createSessionInfo(id = "session-1", title = "Session 1"),
            TestDataFactory.createSessionInfo(id = "session-2", title = "Session 2")
        )
        whenever(mockService.listSessions(any())).thenReturn(sessions)
        
        // Act
        viewModel.loadSessions()
        
        
        // Assert
        verify(mockService).listSessions(forceRefresh = true)
        verify(mockCallback).onSessionsLoaded(sessions)
        assertEquals(sessions, viewModel.getSessions())
    }
    
    @Test
    fun `loadSessions handles errors gracefully`() = runBlocking {
        // Arrange
        whenever(mockService.listSessions(any())).thenThrow(RuntimeException("Network error"))
        
        // Act
        viewModel.loadSessions()
        
        
        // Assert
        verify(mockCallback).onError(argThat { contains("Failed to load sessions") })
    }
    
    @Test
    fun `loadSessions with forceRefresh false passes flag to service`() = runBlocking {
        // Arrange
        whenever(mockService.listSessions(any())).thenReturn(emptyList())
        
        // Act
        viewModel.loadSessions(forceRefresh = false)
        
        
        // Assert
        verify(mockService).listSessions(forceRefresh = false)
    }
    
    // ========== Session Creation Tests ==========
    
    @Test
    fun `createSession creates new session and auto-selects it`() = runBlocking {
        // Arrange
        val newSessionId = "new-session"
        val newSession = TestDataFactory.createSessionInfo(id = newSessionId, title = "New Session")
        whenever(mockService.createSession(any())).thenReturn(newSessionId)
        whenever(mockService.listSessions(any())).thenReturn(listOf(newSession))
        
        // Act
        viewModel.createSession("New Session")
        
        
        // Assert
        verify(mockService).createSession("New Session")
        verify(mockService).listSessions(forceRefresh = true)
        assertEquals(newSession, viewModel.getSelectedSession())
        verify(mockCallback).onSessionSelected(newSession)
    }
    
    @Test
    fun `createSession with null title passes null to service`() = runBlocking {
        // Arrange
        whenever(mockService.createSession(isNull())).thenReturn("session-id")
        whenever(mockService.listSessions(any())).thenReturn(emptyList())
        
        // Act
        viewModel.createSession(null)
        
        
        // Assert
        verify(mockService).createSession(isNull())
    }
    
    @Test
    fun `createSession handles errors`() = runBlocking {
        // Arrange
        whenever(mockService.createSession(any())).thenThrow(RuntimeException("Create failed"))
        
        // Act
        viewModel.createSession("Test")
        
        
        // Assert
        verify(mockCallback).onError(argThat { contains("Failed to create session") })
    }
    
    // ========== Session Deletion Tests ==========
    
    @Test
    fun `deleteSession deletes and reloads sessions`() = runBlocking {
        // Arrange
        val session = TestDataFactory.createSessionInfo(id = "delete-me")
        whenever(mockService.deleteSession(any())).thenReturn(true)
        whenever(mockService.listSessions(any())).thenReturn(emptyList())
        
        // Act
        viewModel.deleteSession(session)
        
        
        // Assert
        verify(mockService).deleteSession("delete-me")
        verify(mockService).listSessions(forceRefresh = true)
        verify(mockCallback).onSuccess(argThat { contains("deleted successfully") })
    }
    
    @Test
    fun `deleteSession shows error when service fails`() = runBlocking {
        // Arrange
        val session = TestDataFactory.createSessionInfo(id = "session")
        whenever(mockService.deleteSession(any())).thenReturn(false)
        
        // Act
        viewModel.deleteSession(session)
        
        
        // Assert
        verify(mockCallback).onError(argThat { contains("Failed to delete session") })
        verify(mockService, never()).listSessions(any())
    }
    
    @Test
    fun `deleteSession handles exceptions`() = runBlocking {
        // Arrange
        val session = TestDataFactory.createSessionInfo(id = "session")
        whenever(mockService.deleteSession(any())).thenThrow(RuntimeException("Delete error"))
        
        // Act
        viewModel.deleteSession(session)
        
        
        // Assert
        verify(mockCallback).onError(argThat { contains("Failed to delete session") })
    }
    
    // ========== Share Session Tests ==========
    
    @Test
    fun `shareSession for unshared session creates share and notifies`() = runBlocking {
        // Arrange
        val session = TestDataFactory.createSessionInfo(id = "session")
        val shareUrl = "https://opencode.ai/share/abc123"
        val sharedSession = TestDataFactory.createSharedSession(id = "session", shareUrl = shareUrl)
        
        whenever(mockService.shareSession(any())).thenReturn(shareUrl)
        whenever(mockService.listSessions(any())).thenReturn(listOf(sharedSession))
        
        // Act
        viewModel.shareSession(session)
        
        
        // Assert
        verify(mockService).shareSession("session")
        verify(mockCallback).onShareUrlGenerated(shareUrl)
        verify(mockCallback).onSuccess(argThat { contains("shared successfully") })
    }
    
    @Test
    fun `shareSession for already shared session returns existing URL`() = runBlocking {
        // Arrange
        val shareUrl = "https://opencode.ai/share/existing"
        val sharedSession = TestDataFactory.createSharedSession(id = "session", shareUrl = shareUrl)
        
        // Act
        viewModel.shareSession(sharedSession)
        
        
        // Assert
        verify(mockService, never()).shareSession(any())
        verify(mockCallback).onShareUrlGenerated(shareUrl)
        verify(mockCallback, never()).onSuccess(any())
    }
    
    @Test
    fun `shareSession handles null response from service`() = runBlocking {
        // Arrange
        val session = TestDataFactory.createSessionInfo(id = "session")
        whenever(mockService.shareSession(any())).thenReturn(null)
        
        // Act
        viewModel.shareSession(session)
        
        
        // Assert
        verify(mockCallback).onError(argThat { contains("Failed to share session") })
    }
    
    // ========== Unshare Session Tests ==========
    
    @Test
    fun `unshareSession unshares and reloads`() = runBlocking {
        // Arrange
        val shareUrl = "https://opencode.ai/share/test"
        val session = TestDataFactory.createSharedSession(id = "session", shareUrl = shareUrl)
        whenever(mockService.unshareSession(any())).thenReturn(true)
        whenever(mockService.listSessions(any())).thenReturn(emptyList())
        
        // Act
        viewModel.unshareSession(session)
        
        
        // Assert
        verify(mockService).unshareSession("session")
        verify(mockCallback).onSuccess(argThat { contains("unshared successfully") })
    }
    
    @Test
    fun `unshareSession handles failure`() = runBlocking {
        // Arrange
        val session = TestDataFactory.createSessionInfo(id = "session")
        whenever(mockService.unshareSession(any())).thenReturn(false)
        
        // Act
        viewModel.unshareSession(session)
        
        
        // Assert
        verify(mockCallback).onError(argThat { contains("Failed to unshare session") })
    }
    
    // ========== Selection Tests ==========
    
    @Test
    fun `selectSession updates selection and notifies callback`() {
        // Arrange
        val session = TestDataFactory.createSessionInfo(id = "session")
        
        // Act
        viewModel.selectSession(session)
        
        // Assert
        assertEquals(session, viewModel.getSelectedSession())
        verify(mockCallback).onSessionSelected(session)
    }
    
    @Test
    fun `selectSession with null clears selection`() {
        // Arrange - first select a session
        val session = TestDataFactory.createSessionInfo(id = "session")
        viewModel.selectSession(session)
        
        // Act - clear selection
        viewModel.selectSession(null)
        
        // Assert
        assertNull(viewModel.getSelectedSession())
        verify(mockCallback).onSessionSelected(null)
    }
    
    // ========== Utility Methods Tests ==========
    
    @Test
    fun `isSessionShared returns true for shared sessions`() {
        val sharedSession = TestDataFactory.createSharedSession(
            id = "session",
            shareUrl = "https://opencode.ai/share/test"
        )
        
        assertTrue(viewModel.isSessionShared(sharedSession))
    }
    
    @Test
    fun `isSessionShared returns false for unshared sessions`() {
        val session = TestDataFactory.createSessionInfo(id = "session")
        
        assertFalse(viewModel.isSessionShared(session))
    }
    
    @Test
    fun `getSessionDisplayTitle returns title`() {
        val titledSession = TestDataFactory.createSessionInfo(id = "1", title = "My Session")
        
        assertEquals("My Session", viewModel.getSessionDisplayTitle(titledSession))
    }
    
    @Test
    fun `getSessionDisplayTitle returns title for empty string`() {
        val emptyTitleSession = TestDataFactory.createSessionInfo(id = "1", title = "")
        
        // Even empty string is returned as-is (title field is non-null)
        assertEquals("", viewModel.getSessionDisplayTitle(emptyTitleSession))
    }
    
    @Test
    fun `getShareUrl returns URL for shared sessions`() {
        val shareUrl = "https://opencode.ai/share/test"
        val sharedSession = TestDataFactory.createSharedSession(id = "session", shareUrl = shareUrl)
        
        assertEquals(shareUrl, viewModel.getShareUrl(sharedSession))
    }
    
    @Test
    fun `getShareUrl returns null for unshared sessions`() {
        val session = TestDataFactory.createSessionInfo(id = "session")
        
        assertNull(viewModel.getShareUrl(session))
    }
    
    // ========== Edge Case Tests ==========
    
    @Test
    fun `createSession when new session not found in reload does not select`() = runBlocking {
        // Arrange - service returns different session ID than expected
        val newSessionId = "new-session"
        val differentSession = TestDataFactory.createSessionInfo(id = "different-id", title = "Different")
        whenever(mockService.createSession(any())).thenReturn(newSessionId)
        whenever(mockService.listSessions(any())).thenReturn(listOf(differentSession))
        
        // Act
        viewModel.createSession("New Session")
        
        
        // Assert
        verify(mockService).createSession("New Session")
        verify(mockService).listSessions(forceRefresh = true)
        // Session should not be selected since ID doesn't match
        assertNull(viewModel.getSelectedSession())
        verify(mockCallback, never()).onSessionSelected(any())
    }
    
    @Test
    fun `shareSession handles exception during share`() = runBlocking {
        // Arrange
        val session = TestDataFactory.createSessionInfo(id = "session")
        whenever(mockService.shareSession(any())).thenThrow(RuntimeException("Share error"))
        
        // Act
        viewModel.shareSession(session)
        
        
        // Assert
        verify(mockCallback).onError(argThat { contains("Failed to share session") && contains("Share error") })
    }
    
    @Test
    fun `unshareSession handles exception during unshare`() = runBlocking {
        // Arrange
        val session = TestDataFactory.createSessionInfo(id = "session")
        whenever(mockService.unshareSession(any())).thenThrow(RuntimeException("Unshare error"))
        
        // Act
        viewModel.unshareSession(session)
        
        
        // Assert
        verify(mockCallback).onError(argThat { contains("Failed to unshare session") && contains("Unshare error") })
    }
    
    @Test
    fun `loadSessions preserves previous sessions on error`() = runBlocking {
        // Arrange - first load succeeds, second fails
        val sessions = listOf(TestDataFactory.createSessionInfo(id = "session-1"))
        whenever(mockService.listSessions(any()))
            .thenReturn(sessions)
            .thenThrow(RuntimeException("Network error"))
        
        // Act - first load
        viewModel.loadSessions()
        
        assertEquals(sessions, viewModel.getSessions())
        
        // Act - second load fails
        viewModel.loadSessions()
        
        
        // Assert - should still have old sessions
        assertEquals(sessions, viewModel.getSessions())
        verify(mockCallback).onError(argThat { contains("Failed to load sessions") })
    }
    
    @Test
    fun `getSessions returns empty list initially`() {
        // Assert
        assertTrue(viewModel.getSessions().isEmpty())
    }
    
    @Test
    fun `getSelectedSession returns null initially`() {
        // Assert
        assertNull(viewModel.getSelectedSession())
    }
    
    @Test
    fun `callback can be set and receives updates`() = runBlocking {
        // Arrange
        val newCallback: SessionListViewModel.ViewCallback = mock()
        val sessions = listOf(TestDataFactory.createSessionInfo(id = "session-1"))
        whenever(mockService.listSessions(any())).thenReturn(sessions)
        
        // Act
        viewModel.setCallback(newCallback)
        viewModel.loadSessions()
        
        
        // Assert - new callback receives updates, old doesn't
        verify(newCallback).onSessionsLoaded(sessions)
        verify(mockCallback, never()).onSessionsLoaded(any())
    }
    
    // ========== Null Callback Tests (branch coverage) ==========
    
    @Test
    fun `loadSessions works without callback set`() = runBlocking {
        // Arrange
        val viewModelNoCallback = SessionListViewModel(mockService, testScope)
        val sessions = listOf(TestDataFactory.createSessionInfo(id = "session-1"))
        whenever(mockService.listSessions(any())).thenReturn(sessions)
        
        // Act - should not throw even without callback
        viewModelNoCallback.loadSessions()
        
        
        // Assert - sessions should still be loaded
        assertEquals(sessions, viewModelNoCallback.getSessions())
    }
    
    @Test
    fun `loadSessions handles error without callback set`() = runBlocking {
        // Arrange
        val viewModelNoCallback = SessionListViewModel(mockService, testScope)
        whenever(mockService.listSessions(any())).thenThrow(RuntimeException("Network error"))
        
        // Act - should not throw even without callback
        viewModelNoCallback.loadSessions()
        
        
        // Assert - no crash, sessions remain empty
        assertTrue(viewModelNoCallback.getSessions().isEmpty())
    }
    
    @Test
    fun `selectSession works without callback set`() {
        // Arrange
        val viewModelNoCallback = SessionListViewModel(mockService, testScope)
        val session = TestDataFactory.createSessionInfo(id = "session")
        
        // Act - should not throw even without callback
        viewModelNoCallback.selectSession(session)
        
        // Assert
        assertEquals(session, viewModelNoCallback.getSelectedSession())
    }
    
    @Test
    fun `createSession works without callback set`() = runBlocking {
        // Arrange
        val viewModelNoCallback = SessionListViewModel(mockService, testScope)
        whenever(mockService.createSession(any())).thenReturn("new-session")
        whenever(mockService.listSessions(any())).thenReturn(emptyList())
        
        // Act - should not throw even without callback
        viewModelNoCallback.createSession("Test")
        
        
        // Assert - no crash
        assertTrue(viewModelNoCallback.getSessions().isEmpty())
    }
    
    @Test
    fun `deleteSession works without callback set`() = runBlocking {
        // Arrange
        val viewModelNoCallback = SessionListViewModel(mockService, testScope)
        val session = TestDataFactory.createSessionInfo(id = "session")
        whenever(mockService.deleteSession(any())).thenReturn(true)
        whenever(mockService.listSessions(any())).thenReturn(emptyList())
        
        // Act - should not throw even without callback
        viewModelNoCallback.deleteSession(session)
        
        
        // Assert - no crash
        assertTrue(viewModelNoCallback.getSessions().isEmpty())
    }
    
    @Test
    fun `shareSession works without callback set`() = runBlocking {
        // Arrange
        val viewModelNoCallback = SessionListViewModel(mockService, testScope)
        val session = TestDataFactory.createSessionInfo(id = "session")
        whenever(mockService.shareSession(any())).thenReturn("https://opencode.ai/share/test")
        whenever(mockService.listSessions(any())).thenReturn(emptyList())
        
        // Act - should not throw even without callback
        viewModelNoCallback.shareSession(session)
        
        
        // Assert - no crash
        assertTrue(viewModelNoCallback.getSessions().isEmpty())
    }
    
    @Test
    fun `unshareSession works without callback set`() = runBlocking {
        // Arrange
        val viewModelNoCallback = SessionListViewModel(mockService, testScope)
        val session = TestDataFactory.createSessionInfo(id = "session")
        whenever(mockService.unshareSession(any())).thenReturn(true)
        whenever(mockService.listSessions(any())).thenReturn(emptyList())
        
        // Act - should not throw even without callback
        viewModelNoCallback.unshareSession(session)
        
        
        // Assert - no crash
        assertTrue(viewModelNoCallback.getSessions().isEmpty())
    }
}
