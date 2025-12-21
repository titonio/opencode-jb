package com.opencode.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.opencode.model.SessionInfo
import com.opencode.model.SessionResponse
import com.opencode.test.MockOpenCodeServer
import com.opencode.test.TestDataFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive integration tests for OpenCodeService.
 * Tests end-to-end workflows that combine multiple operations.
 * 
 * NOTE: All tests disabled due to architectural testing challenges with getOrStartSharedServer().
 * See docs/testing-challenges.md for details. These tests require refactoring the service
 * to be more testable before they can be enabled.
 */
class OpenCodeServiceIntegrationTest {
    
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
        setServerPort(mockServer.port)
    }
    
    @AfterEach
    fun tearDown() {
        try {
            mockServer.shutdown()
        } catch (e: Exception) {
            // Ignore shutdown errors
        }
    }
    
    /**
     * Helper to set the server port via reflection
     */
    private fun setServerPort(port: Int?) {
        val portField = OpenCodeService::class.java.getDeclaredField("sharedServerPort")
        portField.isAccessible = true
        portField.set(service, port)
    }
    
    /**
     * Helper to set the server process via reflection
     */
    private fun setServerProcess(process: Process?) {
        val processField = OpenCodeService::class.java.getDeclaredField("sharedServerProcess")
        processField.isAccessible = true
        processField.set(service, process)
    }
    
    /**
     * Helper to get the session cache via reflection
     */
    private fun getSessionCache(): MutableMap<String, SessionInfo> {
        val cacheField = OpenCodeService::class.java.getDeclaredField("sessionCache")
        cacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return cacheField.get(service) as MutableMap<String, SessionInfo>
    }
    
    // ========== Integration Test 1: Full Session Lifecycle ==========
    
    @Test
@Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `test full session lifecycle - create list share delete`() = runTest {
        // This test verifies that a complete workflow of creating, listing, sharing,
        // and deleting a session works end-to-end
        
        // Arrange
        val sessionTitle = "Integration Test Session"
        val sessionId = "integration-session-1"
        val shareUrl = "https://opencode.ai/share/integration-token"
        
        val createResponse = TestDataFactory.createSessionResponse(
            id = sessionId,
            title = sessionTitle,
            directory = mockProject.basePath ?: "/test"
        )
        val sessionInfo = TestDataFactory.createSessionInfo(
            id = sessionId,
            title = sessionTitle
        )
        val sharedSessionInfo = TestDataFactory.createSharedSession(
            id = sessionId,
            shareUrl = shareUrl
        )
        
        // Step 1: Create session
        mockServer.enqueueSessionCreate(createResponse)
        mockServer.enqueueSessionList(listOf(sessionInfo)) // Cache refresh after create
        
        val createdId = service.createSession(sessionTitle)
        assertEquals(sessionId, createdId)
        
        // Verify create request was made
        val createRequest = mockServer.takeRequest()
        assertEquals("POST", createRequest?.method)
        assertTrue(createRequest?.path?.startsWith("/session") ?: false)
        assertTrue(createRequest?.body?.readUtf8()?.contains(sessionTitle) ?: false)
        
        // Step 2: List sessions to verify it exists
        mockServer.enqueueSessionList(listOf(sessionInfo))
        
        val sessions = service.listSessions(forceRefresh = true)
        assertEquals(1, sessions.size)
        assertEquals(sessionId, sessions[0].id)
        assertEquals(sessionTitle, sessions[0].title)
        assertFalse(sessions[0].isShared)
        
        // Verify list request
        val listRequest = mockServer.takeRequest()
        assertEquals("GET", listRequest?.method)
        
        // Step 3: Share the session
        mockServer.enqueueSession(sharedSessionInfo)
        
        val returnedShareUrl = service.shareSession(sessionId)
        assertEquals(shareUrl, returnedShareUrl)
        
        // Verify share request
        val shareRequest = mockServer.takeRequest()
        assertEquals("POST", shareRequest?.method)
        assertTrue(shareRequest?.path?.contains("$sessionId/share") ?: false)
        
        // Step 4: Verify session is now shared
        mockServer.enqueueSessionList(listOf(sharedSessionInfo))
        
        val sessionsAfterShare = service.listSessions(forceRefresh = true)
        assertEquals(1, sessionsAfterShare.size)
        assertTrue(sessionsAfterShare[0].isShared)
        assertEquals(shareUrl, sessionsAfterShare[0].shareUrl)
        
        // Step 5: Delete the session
        mockServer.enqueueSuccess()
        
        val deleteResult = service.deleteSession(sessionId)
        assertTrue(deleteResult)
        
        // Verify delete request
        val deleteRequest = mockServer.takeRequest()
        assertEquals("DELETE", deleteRequest?.method)
        assertTrue(deleteRequest?.path?.contains(sessionId) ?: false)
        
        // Step 6: Verify session is gone from cache
        val cachedSessions = service.listSessions() // Use cache
        assertFalse(cachedSessions.any { it.id == sessionId })
        
        // Verify workflow completed successfully
        assertTrue(mockServer.getRequestCount() >= 5) // All operations made requests
    }
    
    // ========== Integration Test 2: Multiple Sessions with Cleanup ==========
    
    @Test
@Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `test multiple sessions with cleanup - creates 15 sessions and verifies only 10 kept`() = runTest {
        // This test verifies that the automatic cleanup mechanism works correctly
        // when creating many sessions
        
        // Arrange - Create 15 sessions to trigger cleanup (max is 10)
        val sessionIds = (1..15).map { "session-$it" }
        val now = System.currentTimeMillis()
        
        // Track all created sessions with different updated times
        val allSessions = mutableListOf<SessionInfo>()
        
        // Create sessions one by one
        sessionIds.forEachIndexed { index, sessionId ->
            val sessionInfo = TestDataFactory.createSessionInfo(
                id = sessionId,
                title = "Session $index",
                updated = now - (index * 1000L) // Older sessions have earlier timestamps
            )
            allSessions.add(sessionInfo)
            
            val response = TestDataFactory.createSessionResponse(
                id = sessionId,
                title = "Session $index"
            )
            
            // Enqueue responses for createSession
            mockServer.enqueueSessionCreate(response)
            mockServer.enqueueSessionList(allSessions.toList()) // Cache refresh
            
            // Enqueue cleanup responses if needed (after 11th session)
            if (allSessions.size > 10) {
                mockServer.enqueueSessionList(allSessions.toList()) // cleanup list call
                val toDelete = allSessions.size - 10
                repeat(toDelete) { mockServer.enqueueSuccess() }
            }
            
            // Act - Create session
            val createdId = service.createSession("Session $index")
            assertEquals(sessionId, createdId)
        }
        
        // Assert - Verify cleanup happened
        // The oldest 5 sessions should have been deleted
        val cache = getSessionCache()
        assertEquals(10, cache.size) // Only 10 sessions remain
        
        // Verify the 10 most recent sessions are kept
        val remainingSessions = cache.values.sortedByDescending { it.time.updated }
        assertEquals(10, remainingSessions.size)
        
        // The newest sessions (1-10) should remain
        (1..10).forEach { index ->
            assertTrue(cache.containsKey("session-$index"))
        }
        
        // The oldest sessions (11-15) should be deleted
        (11..15).forEach { index ->
            assertFalse(cache.containsKey("session-$index"))
        }
        
        // Verify DELETE requests were made for oldest sessions
        // Count DELETE requests in the request history
        var deleteCount = 0
        while (true) {
            val request = mockServer.takeRequest(100, TimeUnit.MILLISECONDS) ?: break
            if (request.method == "DELETE") {
                deleteCount++
            }
        }
        
        assertEquals(5, deleteCount) // 15 - 10 = 5 sessions deleted
    }
    
    // ========== Integration Test 3: Concurrent Session Operations ==========
    
    @Test
@Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `test concurrent session operations - thread safety`() = runTest {
        // This test verifies that the service handles concurrent operations correctly
        // and maintains thread safety
        
        // Arrange - Prepare multiple sessions
        val concurrentOperations = 20
        val sessionIds = (1..concurrentOperations).map { "concurrent-session-$it" }
        val sessions = sessionIds.map { id ->
            TestDataFactory.createSessionInfo(id = id, title = "Concurrent $id")
        }
        
        // Enqueue many responses for concurrent operations
        repeat(concurrentOperations * 4) { // Each operation might need multiple requests
            mockServer.enqueueSessionList(sessions)
            mockServer.enqueueSession(sessions[0])
            mockServer.enqueueSuccess()
        }
        
        // Track successful operations
        val successfulLists = AtomicInteger(0)
        val successfulGets = AtomicInteger(0)
        val successfulDeletes = AtomicInteger(0)
        val errors = ConcurrentHashMap<String, Exception>()
        
        // Act - Launch concurrent operations
        val jobs = (1..concurrentOperations).map { index ->
            async {
                try {
                    // Each coroutine performs different operations
                    when (index % 3) {
                        0 -> {
                            // List sessions
                            val result = service.listSessions(forceRefresh = true)
                            if (result.isNotEmpty()) {
                                successfulLists.incrementAndGet()
                            }
                        }
                        1 -> {
                            // Get specific session
                            val result = service.getSession(sessionIds[index % sessionIds.size])
                            if (result != null) {
                                successfulGets.incrementAndGet()
                            }
                        }
                        2 -> {
                            // Delete session (mock - won't actually delete from server)
                            val result = service.deleteSession(sessionIds[index % sessionIds.size])
                            if (result) {
                                successfulDeletes.incrementAndGet()
                            }
                        }
                    }
                } catch (e: Exception) {
                    errors["operation-$index"] = e
                }
            }
        }
        
        // Wait for all operations to complete
        jobs.awaitAll()
        
        // Assert - Verify all operations completed without errors
        assertTrue(errors.isEmpty(), "Expected no errors but got: ${errors.keys}")
        
        // Verify operations were successful
        val totalSuccessful = successfulLists.get() + successfulGets.get() + successfulDeletes.get()
        assertTrue(totalSuccessful > 0, "Expected some operations to succeed")
        
        // Verify the service state is consistent
        val cache = getSessionCache()
        assertNotNull(cache) // Cache should exist and be accessible
        
        // Verify the mock server received all requests
        assertTrue(mockServer.getRequestCount() > 0)
    }
    
    // ========== Integration Test 4: Server Recovery After Crash ==========
    
    @Test
@Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `test server recovery after crash - restarts server if died`() = runTest {
        // This test verifies that the service can recover from a server crash
        // and restart the server automatically
        
        // Arrange - Simulate a crashed server by setting process to null
        // and port to a value that won't respond
        val deadPort = 9999
        setServerPort(deadPort)
        setServerProcess(null)
        
        // Verify server is not running on dead port
        assertFalse(service.isServerRunning(deadPort))
        
        // Act - Try to get or start server (should detect dead port and restart)
        // We'll mock the server restart by setting up the mock server
        val portField = OpenCodeService::class.java.getDeclaredField("sharedServerPort")
        portField.isAccessible = true
        portField.set(service, null) // Clear the dead port
        
        // Create a mock process for the new server
        val mockProcess = mock<Process>()
        
        // Mock getOrStartSharedServer by setting up the mock server port
        // In a real scenario, this would start a new process
        setServerPort(mockServer.port)
        setServerProcess(mockProcess)
        
        // Enqueue a successful response to verify server is working
        val session = TestDataFactory.createSessionInfo(id = "recovery-test")
        mockServer.enqueueSessionList(listOf(session))
        
        // Act - Perform an operation that requires the server
        val sessions = service.listSessions(forceRefresh = true)
        
        // Assert - Verify operation succeeded with new server
        assertNotNull(sessions)
        assertEquals(1, sessions.size)
        assertEquals("recovery-test", sessions[0].id)
        
        // Verify the new server port is set
        val portField2 = OpenCodeService::class.java.getDeclaredField("sharedServerPort")
        portField2.isAccessible = true
        val currentPort = portField2.get(service) as? Int
        assertEquals(mockServer.port, currentPort)
        
        // Verify server is running on new port
        assertTrue(service.isServerRunning(mockServer.port))
        
        // Verify request was made to the new server
        val request = mockServer.takeRequest()
        assertNotNull(request)
        assertEquals("GET", request?.method)
    }
    
    // ========== Integration Test 5: Editor Registration Integration ==========
    
    @Test
@Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `test editor registration integration - register unregister with server lifecycle`() = runTest {
        // This test verifies that editor registration/unregistration works correctly
        // and interacts properly with server lifecycle management
        
        // Arrange - Create mock virtual files for editors
        val mockFile1 = mock<VirtualFile>()
        val mockFile2 = mock<VirtualFile>()
        
        // Verify no editor is active initially
        assertFalse(service.hasActiveEditor())
        assertNull(service.getActiveEditorFile())
        
        // Act - Register first editor
        service.registerActiveEditor(mockFile1)
        
        // Assert - Verify first editor is registered
        assertTrue(service.hasActiveEditor())
        assertEquals(mockFile1, service.getActiveEditorFile())
        
        // Act - Register second editor (simulating tab switch)
        service.registerActiveEditor(mockFile2)
        
        // Assert - Verify second editor replaced first
        assertTrue(service.hasActiveEditor())
        assertEquals(mockFile2, service.getActiveEditorFile())
        assertNotEquals(mockFile1, service.getActiveEditorFile())
        
        // Act - Unregister wrong file (should not affect active editor)
        service.unregisterActiveEditor(mockFile1)
        
        // Assert - Active editor should still be mockFile2
        assertTrue(service.hasActiveEditor())
        assertEquals(mockFile2, service.getActiveEditorFile())
        
        // Act - Unregister correct file
        service.unregisterActiveEditor(mockFile2)
        
        // Assert - No active editor after unregistration
        // Note: Actual shutdown is scheduled, so we just verify the unregistration
        assertFalse(service.hasActiveEditor())
        assertNull(service.getActiveEditorFile())
        
        // Test server lifecycle integration
        // Set up mock server and process
        val mockProcess = mock<Process>()
        setServerPort(mockServer.port)
        setServerProcess(mockProcess)
        
        // Verify server is accessible
        mockServer.enqueueSessionList(emptyList())
        val sessions = service.listSessions(forceRefresh = true)
        assertNotNull(sessions)
        
        // Register editor again
        service.registerActiveEditor(mockFile1)
        assertTrue(service.hasActiveEditor())
        
        // Create a session while editor is active
        val createResponse = TestDataFactory.createSessionResponse(id = "editor-session")
        mockServer.enqueueSessionCreate(createResponse)
        mockServer.enqueueSessionList(emptyList())
        
        val sessionId = service.createSession("Editor Session")
        assertEquals("editor-session", sessionId)
        
        // Unregister editor - server should schedule shutdown
        service.unregisterActiveEditor(mockFile1)
        assertFalse(service.hasActiveEditor())
        
        // Verify the workflow completed successfully
        assertTrue(mockServer.getRequestCount() >= 2)
        
        // Test multiple rapid register/unregister cycles (simulating tab drag/split)
        service.registerActiveEditor(mockFile1)
        service.unregisterActiveEditor(mockFile1)
        service.registerActiveEditor(mockFile2) // Quick re-register
        
        // Should still have active editor
        assertTrue(service.hasActiveEditor())
        assertEquals(mockFile2, service.getActiveEditorFile())
        
        // Final cleanup
        service.unregisterActiveEditor(mockFile2)
        assertFalse(service.hasActiveEditor())
    }
    
    // ========== Additional Integration Test: Cache Behavior Across Operations ==========
    
    @Test
@Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `test cache consistency across multiple operations`() = runTest {
        // This test verifies that the cache remains consistent across various operations
        
        // Arrange
        val session1 = TestDataFactory.createSessionInfo(id = "cache-1", title = "Cache Test 1")
        val session2 = TestDataFactory.createSessionInfo(id = "cache-2", title = "Cache Test 2")
        val session3 = TestDataFactory.createSessionInfo(id = "cache-3", title = "Cache Test 3")
        
        // Step 1: List sessions - populate cache
        mockServer.enqueueSessionList(listOf(session1, session2))
        val initialList = service.listSessions(forceRefresh = true)
        assertEquals(2, initialList.size)
        
        // Verify cache is populated
        val cache = getSessionCache()
        assertEquals(2, cache.size)
        assertTrue(cache.containsKey("cache-1"))
        assertTrue(cache.containsKey("cache-2"))
        
        // Step 2: Create new session - should update cache
        val createResponse = TestDataFactory.createSessionResponse(id = "cache-3")
        mockServer.enqueueSessionCreate(createResponse)
        mockServer.enqueueSessionList(listOf(session1, session2, session3))
        
        service.createSession("Cache Test 3")
        
        // Verify cache now has 3 sessions
        assertEquals(3, cache.size)
        assertTrue(cache.containsKey("cache-3"))
        
        // Step 3: Share a session - should update that entry in cache
        val sharedSession1 = TestDataFactory.createSharedSession(
            id = "cache-1",
            shareUrl = "https://opencode.ai/share/test"
        )
        mockServer.enqueueSession(sharedSession1)
        
        service.shareSession("cache-1")
        
        // Verify cache has updated share info
        assertTrue(cache["cache-1"]?.isShared ?: false)
        assertEquals("https://opencode.ai/share/test", cache["cache-1"]?.shareUrl)
        
        // Step 4: Delete a session - should remove from cache
        mockServer.enqueueSuccess()
        service.deleteSession("cache-2")
        
        // Verify cache no longer has deleted session
        assertEquals(2, cache.size)
        assertFalse(cache.containsKey("cache-2"))
        assertTrue(cache.containsKey("cache-1"))
        assertTrue(cache.containsKey("cache-3"))
        
        // Step 5: List again without force refresh - should use cache
        val cachedList = service.listSessions(forceRefresh = false)
        assertEquals(2, cachedList.size)
        
        // Verify no additional server request was made (cache was used)
        // Count requests: list(1) + create(1) + create-refresh(1) + share(1) + delete(1) = 5
        assertEquals(5, mockServer.getRequestCount())
    }
    
    // ========== Additional Integration Test: Error Recovery ==========
    
    @Test
@Disabled("Blocked by getOrStartSharedServer() testing issue - see docs/testing-challenges.md")
    fun `test error recovery across multiple operations`() = runTest {
        // This test verifies that the service recovers gracefully from errors
        
        // Step 1: Successful operation
        val session = TestDataFactory.createSessionInfo(id = "error-test-1")
        mockServer.enqueueSessionList(listOf(session))
        val result1 = service.listSessions(forceRefresh = true)
        assertEquals(1, result1.size)
        
        // Step 2: Server error - should handle gracefully
        mockServer.enqueueError(500, "Server Error")
        val result2 = service.listSessions(forceRefresh = true)
        // Should return cached result or empty list
        assertNotNull(result2)
        
        // Step 3: Recovery - server works again
        mockServer.enqueueSessionList(listOf(session))
        val result3 = service.listSessions(forceRefresh = true)
        assertEquals(1, result3.size)
        
        // Step 4: Create session fails
        mockServer.enqueueError(500, "Create Failed")
        try {
            service.createSession("Error Test")
            fail<Unit>("Expected exception")
        } catch (e: Exception) {
            // Expected
            assertTrue(e.message?.contains("Failed to create session") ?: false)
        }
        
        // Step 5: Service still functional after error
        mockServer.enqueueSessionList(listOf(session))
        val result4 = service.listSessions(forceRefresh = true)
        assertEquals(1, result4.size)
        
        // Verify cache is still functional
        val cache = getSessionCache()
        assertNotNull(cache)
        assertTrue(cache.containsKey("error-test-1"))
    }
}
