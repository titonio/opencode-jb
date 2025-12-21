package com.opencode.service

import com.intellij.openapi.project.Project
import com.opencode.model.SessionInfo
import com.opencode.model.SessionResponse
import com.opencode.test.MockOpenCodeServer
import com.opencode.test.TestDataFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Comprehensive test suite for OpenCodeService session management operations.
 * Tests session CRUD operations, caching behavior, and cleanup logic.
 * 
 * NOTE: Some tests are disabled due to architectural testing challenges with getOrStartSharedServer().
 * See docs/testing-challenges.md for details.
 */
class OpenCodeServiceSessionTest {
    
    private lateinit var mockProject: Project
    private lateinit var mockServer: MockOpenCodeServer
    private lateinit var service: OpenCodeService
    
    @BeforeEach
    fun setUp() {
        // Create a mock project
        mockProject = mock()
        whenever(mockProject.name).thenReturn("TestProject")
        whenever(mockProject.basePath).thenReturn("/test/project")
        
        mockServer = MockOpenCodeServer()
        mockServer.start()
        
        service = OpenCodeService(mockProject)
        
        // Inject mock server port into service via reflection
        val portField = OpenCodeService::class.java.getDeclaredField("sharedServerPort")
        portField.isAccessible = true
        portField.set(service, mockServer.port)
    }
    
    @AfterEach
    fun tearDown() {
        try {
            mockServer.shutdown()
        } catch (e: Exception) {
            // Ignore shutdown errors
        }
    }
    
    // ========== createSession Tests ==========
    
    @Test
    @Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `createSession with custom title creates session successfully`() = runTest {
        // Arrange
        val expectedTitle = "My Custom Session"
        val expectedId = "session-123"
        val response = TestDataFactory.createSessionResponse(
            id = expectedId,
            title = expectedTitle,
            directory = mockProject.basePath ?: "/test"
        )
        
        // First verify the mock server is working with a simple listSessions call
        mockServer.enqueueSessionList(listOf())
        service.listSessions()
        
        // Enqueue responses in order:
        // 1. isServerRunning() check in getOrStartSharedServer()
        mockServer.enqueueSessionList(listOf())
        // 2. POST /session (createSession)
        mockServer.enqueueSessionCreate(response)
        // 3. GET /session (refreshSessionCache after create)
        mockServer.enqueueSessionList(listOf())
        
        // Act
        val sessionId = service.createSession(expectedTitle)
        
        // Assert
        assertEquals(expectedId, sessionId)
        
        val request = mockServer.takeRequest() // warmup listSessions
        val request2 = mockServer.takeRequest() // isServerRunning
        val request3 = mockServer.takeRequest() // POST /session
        assertNotNull(request3)
        assertEquals("POST", request3?.method)
        assertTrue(request3?.path?.startsWith("/session") ?: false)
        assertTrue(request3?.body?.readUtf8()?.contains(expectedTitle) ?: false)
    }
    
    @Test
    @Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `createSession without title uses default title format`() = runTest {
        // Arrange
        val expectedId = "session-456"
        val response = TestDataFactory.createSessionResponse(
            id = expectedId,
            title = "IntelliJ Session - 2025-12-21T10:00:00"
        )
        
        mockServer.enqueueSessionCreate(response)
        mockServer.enqueueSessionList(listOf())
        
        // Act
        val sessionId = service.createSession()
        
        // Assert
        assertEquals(expectedId, sessionId)
        
        val request = mockServer.takeRequest()
        assertNotNull(request)
        assertTrue(request?.body?.readUtf8()?.contains("IntelliJ Session") ?: false)
    }
    
    @Test
    @Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `createSession handles server error gracefully`() = runTest {
        // Arrange
        mockServer.enqueueError(500, "Internal Server Error")
        
        // Act & Assert
        try {
            service.createSession("Test Session")
            fail("Expected IOException to be thrown")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Failed to create session") ?: false)
        }
    }
    
    @Test
    @Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `createSession updates cache after successful creation`() = runTest {
        // Arrange
        val sessionId = "new-session"
        val response = TestDataFactory.createSessionResponse(id = sessionId)
        val sessionInfo = TestDataFactory.createSessionInfo(id = sessionId)
        
        mockServer.enqueueSessionCreate(response)
        mockServer.enqueueSessionList(listOf(sessionInfo))
        
        // Act
        service.createSession("Test")
        
        // Assert - verify cache was refreshed
        val request2 = mockServer.takeRequest() // Skip create request
        val request3 = mockServer.takeRequest() // Cache refresh request
        assertNotNull(request3)
        assertEquals("GET", request3?.method)
    }
    
    @Test
    @Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `createSession triggers cleanup when needed`() = runTest {
        // Arrange: Create 11 sessions to trigger cleanup
        val sessions = TestDataFactory.createSessionList(11)
        val newSessionResponse = TestDataFactory.createSessionResponse(id = "new-session")
        
        mockServer.enqueueSessionCreate(newSessionResponse)
        mockServer.enqueueSessionList(sessions) // For refreshSessionCache
        mockServer.enqueueSessionList(sessions.take(11)) // For cleanupOldSessions call
        
        // Enqueue success responses for each delete operation (1 session should be deleted)
        mockServer.enqueueSuccess()
        
        // Act
        service.createSession("New Session")
        
        // Assert - verify DELETE request was made for oldest session
        mockServer.takeRequest() // POST /session
        mockServer.takeRequest() // GET /session (refresh after create)
        mockServer.takeRequest() // GET /session (cleanup list call)
        
        val deleteRequest = mockServer.takeRequest() // DELETE /session/{id}
        assertNotNull(deleteRequest)
        assertEquals("DELETE", deleteRequest?.method)
    }
    
    // ========== listSessions Tests ==========
    
    @Test
    fun `listSessions returns empty list when no sessions exist`() = runTest {
        // Arrange
        mockServer.enqueueSessionList(emptyList())
        
        // Act
        val sessions = service.listSessions()
        
        // Assert
        assertTrue(sessions.isEmpty())
        
        val request = mockServer.takeRequest()
        assertNotNull(request)
        assertEquals("GET", request?.method)
    }
    
    @Test
    fun `listSessions returns cached results within TTL`() = runTest {
        // Arrange
        val sessions = TestDataFactory.createSessionList(3)
        mockServer.enqueueSessionList(sessions)
        
        // Act - First call populates cache
        val firstCall = service.listSessions()
        
        // Act - Second call within TTL should use cache (no server call)
        val secondCall = service.listSessions()
        
        // Assert
        assertEquals(3, firstCall.size)
        assertEquals(3, secondCall.size)
        assertEquals(1, mockServer.getRequestCount()) // Only 1 request made
    }
    
    @Test
    fun `listSessions refreshes expired cache automatically`() = runTest {
        // Arrange
        val sessions = TestDataFactory.createSessionList(2)
        mockServer.enqueueSessionList(sessions)
        mockServer.enqueueSessionList(sessions)
        
        // Act - First call
        service.listSessions()
        
        // Simulate cache expiry by manipulating lastCacheUpdate
        val cacheField = OpenCodeService::class.java.getDeclaredField("lastCacheUpdate")
        cacheField.isAccessible = true
        cacheField.set(service, System.currentTimeMillis() - 10000L) // 10 seconds ago
        
        // Act - Second call after expiry
        service.listSessions()
        
        // Assert
        assertEquals(2, mockServer.getRequestCount()) // Both requests made
    }
    
    @Test
    fun `listSessions force refresh bypasses cache`() = runTest {
        // Arrange
        val sessions = TestDataFactory.createSessionList(2)
        mockServer.enqueueSessionList(sessions)
        mockServer.enqueueSessionList(sessions)
        
        // Act
        service.listSessions() // Populates cache
        service.listSessions(forceRefresh = true) // Forces refresh
        
        // Assert
        assertEquals(2, mockServer.getRequestCount())
    }
    
    @Test
    fun `listSessions handles server down gracefully`() = runTest {
        // Arrange - Server port not set
        val portField = OpenCodeService::class.java.getDeclaredField("sharedServerPort")
        portField.isAccessible = true
        portField.set(service, null)
        
        // Act
        val sessions = service.listSessions()
        
        // Assert
        assertTrue(sessions.isEmpty())
        assertEquals(0, mockServer.getRequestCount())
    }
    
    @Test
    fun `listSessions sorts by updated time descending`() = runTest {
        // Arrange - Sessions with different updated times
        val now = System.currentTimeMillis()
        val sessions = listOf(
            TestDataFactory.createSessionInfo(id = "old", updated = now - 10000),
            TestDataFactory.createSessionInfo(id = "newest", updated = now),
            TestDataFactory.createSessionInfo(id = "middle", updated = now - 5000)
        )
        mockServer.enqueueSessionList(sessions)
        
        // Act
        val result = service.listSessions()
        
        // Assert
        assertEquals(3, result.size)
        assertEquals("newest", result[0].id)
        assertEquals("middle", result[1].id)
        assertEquals("old", result[2].id)
    }
    
    // ========== getSession Tests ==========
    
    @Test
    fun `getSession returns session for valid ID`() = runTest {
        // Arrange
        val sessionId = "valid-session"
        val session = TestDataFactory.createSessionInfo(id = sessionId)
        mockServer.enqueueSession(session)
        
        // Act
        val result = service.getSession(sessionId)
        
        // Assert
        assertNotNull(result)
        assertEquals(sessionId, result?.id)
        
        val request = mockServer.takeRequest()
        assertTrue(request?.path?.contains(sessionId) ?: false)
    }
    
    @Test
    fun `getSession returns null for invalid ID`() = runTest {
        // Arrange
        mockServer.enqueueError(404, "Not Found")
        
        // Act
        val result = service.getSession("invalid-id")
        
        // Assert
        assertNull(result)
    }
    
    @Test
    fun `getSession returns null on server error`() = runTest {
        // Arrange
        mockServer.enqueueError(500, "Server Error")
        
        // Act
        val result = service.getSession("any-id")
        
        // Assert
        assertNull(result)
    }
    
    // ========== deleteSession Tests ==========
    
    @Test
    fun `deleteSession removes session successfully`() = runTest {
        // Arrange
        val sessionId = "session-to-delete"
        mockServer.enqueueSuccess()
        
        // Act
        val result = service.deleteSession(sessionId)
        
        // Assert
        assertTrue(result)
        
        val request = mockServer.takeRequest()
        assertEquals("DELETE", request?.method)
        assertTrue(request?.path?.contains(sessionId) ?: false)
    }
    
    @Test
    @Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `deleteSession updates cache after successful deletion`() = runTest {
        // Arrange
        val sessionId = "cached-session"
        val session = TestDataFactory.createSessionInfo(id = sessionId)
        
        // Populate cache first
        mockServer.enqueueSessionList(listOf(session))
        service.listSessions()
        
        // Delete the session
        mockServer.enqueueSuccess()
        
        // Act
        val result = service.deleteSession(sessionId)
        
        // Assert
        assertTrue(result)
        
        // Verify cache was updated by checking if subsequent list doesn't include deleted session
        val cachedSessions = service.listSessions() // Should use cache
        assertFalse(cachedSessions.any { it.id == sessionId })
    }
    
    @Test
    fun `deleteSession returns false on failure`() = runTest {
        // Arrange
        mockServer.enqueueError(500, "Server Error")
        
        // Act
        val result = service.deleteSession("any-id")
        
        // Assert
        assertFalse(result)
    }
    
    @Test
    fun `deleteSession handles server down gracefully`() = runTest {
        // Arrange - Server port not set
        val portField = OpenCodeService::class.java.getDeclaredField("sharedServerPort")
        portField.isAccessible = true
        portField.set(service, null)
        
        // Act
        val result = service.deleteSession("any-id")
        
        // Assert
        assertFalse(result)
    }
    
    // ========== shareSession Tests ==========
    
    @Test
    fun `shareSession returns share URL on success`() = runTest {
        // Arrange
        val sessionId = "session-to-share"
        val shareUrl = "https://opencode.ai/share/abc123"
        val sharedSession = TestDataFactory.createSharedSession(id = sessionId, shareUrl = shareUrl)
        
        mockServer.enqueueSession(sharedSession)
        
        // Act
        val result = service.shareSession(sessionId)
        
        // Assert
        assertEquals(shareUrl, result)
        
        val request = mockServer.takeRequest()
        assertEquals("POST", request?.method)
        assertTrue(request?.path?.contains("$sessionId/share") ?: false)
    }
    
    @Test
    fun `shareSession updates cache with shared session`() = runTest {
        // Arrange
        val sessionId = "session-to-share"
        val shareUrl = "https://opencode.ai/share/xyz789"
        val unsharedSession = TestDataFactory.createSessionInfo(id = sessionId)
        val sharedSession = TestDataFactory.createSharedSession(id = sessionId, shareUrl = shareUrl)
        
        // Populate cache with unshared session
        mockServer.enqueueSessionList(listOf(unsharedSession))
        service.listSessions()
        
        // Share the session
        mockServer.enqueueSession(sharedSession)
        
        // Act
        val result = service.shareSession(sessionId)
        
        // Assert
        assertEquals(shareUrl, result)
        
        // Verify cache was updated - check cached version is now shared
        val cachedSessions = service.listSessions() // Uses cache
        val cachedSession = cachedSessions.find { it.id == sessionId }
        assertTrue(cachedSession?.isShared ?: false)
        assertEquals(shareUrl, cachedSession?.shareUrl)
    }
    
    @Test
    fun `shareSession returns null on error`() = runTest {
        // Arrange
        mockServer.enqueueError(500, "Server Error")
        
        // Act
        val result = service.shareSession("any-id")
        
        // Assert
        assertNull(result)
    }
    
    // ========== unshareSession Tests ==========
    
    @Test
    fun `unshareSession removes share successfully`() = runTest {
        // Arrange
        val sessionId = "shared-session"
        val unsharedSession = TestDataFactory.createSessionInfo(id = sessionId)
        
        mockServer.enqueueSuccess() // DELETE /session/{id}/share
        mockServer.enqueueSession(unsharedSession) // GET /session/{id} for cache refresh
        
        // Act
        val result = service.unshareSession(sessionId)
        
        // Assert
        assertTrue(result)
        
        val deleteRequest = mockServer.takeRequest()
        assertEquals("DELETE", deleteRequest?.method)
        assertTrue(deleteRequest?.path?.contains("$sessionId/share") ?: false)
    }
    
    @Test
    @Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `unshareSession refreshes cache after success`() = runTest {
        // Arrange
        val sessionId = "shared-session"
        val sharedSession = TestDataFactory.createSharedSession(id = sessionId)
        val unsharedSession = TestDataFactory.createSessionInfo(id = sessionId)
        
        // Populate cache with shared session
        mockServer.enqueueSessionList(listOf(sharedSession))
        service.listSessions()
        
        // Unshare the session
        mockServer.enqueueSuccess()
        mockServer.enqueueSession(unsharedSession)
        
        // Act
        val result = service.unshareSession(sessionId)
        
        // Assert
        assertTrue(result)
        
        // Verify getSession was called to refresh cache
        mockServer.takeRequest() // DELETE
        val getRequest = mockServer.takeRequest() // GET
        assertEquals("GET", getRequest?.method)
        assertTrue(getRequest?.path?.contains(sessionId) ?: false)
    }
    
    @Test
    fun `unshareSession returns false on failure`() = runTest {
        // Arrange
        mockServer.enqueueError(500, "Server Error")
        
        // Act
        val result = service.unshareSession("any-id")
        
        // Assert
        assertFalse(result)
    }
    
    // ========== cleanupOldSessions Tests ==========
    
    @Test
    @Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `cleanupOldSessions keeps maximum 10 sessions`() = runTest {
        // Arrange - Create 15 sessions
        val sessions = TestDataFactory.createSessionList(15)
        
        // Mock responses: refreshSessionCache, then cleanupOldSessions listSessions, then 5 deletes
        val response = TestDataFactory.createSessionResponse(id = "trigger")
        mockServer.enqueueSessionCreate(response)
        mockServer.enqueueSessionList(sessions) // refreshSessionCache
        mockServer.enqueueSessionList(sessions) // cleanupOldSessions listSessions
        
        // Enqueue success for 5 delete operations (15 - 10 = 5 to delete)
        repeat(5) { mockServer.enqueueSuccess() }
        
        // Act - createSession triggers cleanup
        service.createSession("Trigger Cleanup")
        
        // Assert - Verify 5 DELETE requests were made
        mockServer.takeRequest() // POST /session
        mockServer.takeRequest() // GET /session (refresh)
        mockServer.takeRequest() // GET /session (cleanup)
        
        repeat(5) {
            val deleteRequest = mockServer.takeRequest()
            assertEquals("DELETE", deleteRequest?.method)
        }
    }
    
    @Test
    @Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `cleanupOldSessions sorts by updated time and deletes oldest`() = runTest {
        // Arrange - Create sessions with specific updated times
        val now = System.currentTimeMillis()
        val sessions = listOf(
            TestDataFactory.createSessionInfo(id = "newest", updated = now),
            TestDataFactory.createSessionInfo(id = "old-1", updated = now - 11000),
            TestDataFactory.createSessionInfo(id = "old-2", updated = now - 12000),
            TestDataFactory.createSessionInfo(id = "keep-1", updated = now - 1000),
            TestDataFactory.createSessionInfo(id = "keep-2", updated = now - 2000),
            TestDataFactory.createSessionInfo(id = "keep-3", updated = now - 3000),
            TestDataFactory.createSessionInfo(id = "keep-4", updated = now - 4000),
            TestDataFactory.createSessionInfo(id = "keep-5", updated = now - 5000),
            TestDataFactory.createSessionInfo(id = "keep-6", updated = now - 6000),
            TestDataFactory.createSessionInfo(id = "keep-7", updated = now - 7000),
            TestDataFactory.createSessionInfo(id = "keep-8", updated = now - 8000),
            TestDataFactory.createSessionInfo(id = "keep-9", updated = now - 9000)
        )
        
        val response = TestDataFactory.createSessionResponse(id = "trigger")
        mockServer.enqueueSessionCreate(response)
        mockServer.enqueueSessionList(sessions)
        mockServer.enqueueSessionList(sessions)
        
        // Enqueue success for 2 delete operations
        repeat(2) { mockServer.enqueueSuccess() }
        
        // Act
        service.createSession("Trigger")
        
        // Assert - Verify oldest sessions were deleted
        mockServer.takeRequest() // POST
        mockServer.takeRequest() // GET (refresh)
        mockServer.takeRequest() // GET (cleanup)
        
        val delete1 = mockServer.takeRequest()
        val delete2 = mockServer.takeRequest()
        
        assertTrue(delete1?.path?.contains("old-2") ?: false) // Oldest
        assertTrue(delete2?.path?.contains("old-1") ?: false) // Second oldest
    }
}
