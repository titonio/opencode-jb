package com.opencode.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.opencode.model.SessionInfo
import com.opencode.model.SessionResponse
import com.opencode.test.MockOpenCodeServer
import com.opencode.test.MockServerManager
import com.opencode.test.TestDataFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive integration tests for OpenCodeService.
 * Tests end-to-end workflows that combine multiple operations.
 * 
 * Updated to use MockServerManager for improved testability.
 */
class OpenCodeServiceIntegrationTest {
    
    private lateinit var mockProject: Project
    private lateinit var mockServer: MockOpenCodeServer
    private lateinit var mockServerManager: MockServerManager
    private lateinit var service: OpenCodeService
    
    @BeforeEach
    fun setUp() {
        // Create a mock project
        mockProject = mock()
        whenever(mockProject.name).thenReturn("TestProject")
        whenever(mockProject.basePath).thenReturn("/test/project")
        
        mockServer = MockOpenCodeServer()
        mockServer.start()
        
        // Create mock server manager that returns the mock server's port
        mockServerManager = MockServerManager(mockPort = mockServer.port, shouldSucceed = true)
        
        // Create service with injected mock server manager
        service = OpenCodeService(mockProject, mockServerManager)
        service.disablePlatformInteractions = true
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
    fun `test full session lifecycle - create list share delete`() = runBlocking {
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
        
        // Setup smart dispatcher to handle all requests
        mockServer.setupSmartDispatcher(
            sessions = listOf(sessionInfo),
            createResponse = createResponse,
            shareSessionResponse = sharedSessionInfo,
            deleteSuccess = true
        )
        
        // Step 1: Create session
        val createdId = service.createSession(sessionTitle)
        assertEquals(sessionId, createdId)
        
        // Step 2: List sessions to verify it exists
        val sessions = service.listSessions(forceRefresh = true)
        assertEquals(1, sessions.size)
        assertEquals(sessionId, sessions[0].id)
        assertEquals(sessionTitle, sessions[0].title)
        assertFalse(sessions[0].isShared)
        
        // Step 3: Share the session
        val returnedShareUrl = service.shareSession(sessionId)
        assertEquals(shareUrl, returnedShareUrl)
        
        // Step 4: Verify session is now shared in cache (updated by shareSession)
        val cachedSessionsAfterShare = service.listSessions() // Use cache
        assertTrue(cachedSessionsAfterShare.any { it.isShared && it.id == sessionId })
        
        // Step 5: Delete the session
        val deleteResult = service.deleteSession(sessionId)
        assertTrue(deleteResult)
        
        // Step 6: Verify session is gone from cache (removed by deleteSession)
        // Check cache directly to avoid TTL issues
        val cache = getSessionCache()
        assertFalse(cache.containsKey(sessionId), "Session should be removed from cache after deletion")
        
        // Verify workflow completed successfully
        assertTrue(mockServer.getRequestCount() >= 4) // All operations made requests
    }
    
    // ========== Integration Test 2: Multiple Sessions with Cleanup ==========
    
    @Test
    fun `test multiple sessions with cleanup - creates 15 sessions and verifies only 10 kept`() = runBlocking {
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
            
            // Setup smart dispatcher for each iteration
            mockServer.setupSmartDispatcher(
                sessions = allSessions.toList(),
                createResponse = response,
                deleteSuccess = true
            )
            
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
        
        // Verify workflow completed successfully
        assertTrue(mockServer.getRequestCount() >= 15) // At least one request per session creation
    }
    
    // ========== Integration Test 3: Concurrent Session Operations ==========
    
    @Test
    fun `test concurrent session operations - thread safety`() = runBlocking {
        // This test verifies that the service handles concurrent operations correctly
        // and maintains thread safety
        
        // Arrange - Prepare multiple sessions
        val concurrentOperations = 20
        val sessionIds = (1..concurrentOperations).map { "concurrent-session-$it" }
        val sessions = sessionIds.map { id ->
            TestDataFactory.createSessionInfo(id = id, title = "Concurrent $id")
        }
        
        // Setup smart dispatcher to handle all types of requests
        mockServer.setupSmartDispatcher(
            sessions = sessions,
            getSessionResponse = sessions[0],
            deleteSuccess = true
        )
        
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
    
    // ========== Integration Test 4: Editor Registration Integration ==========
    
    @Test
    fun `test editor registration integration - register unregister with server lifecycle`() = runBlocking {
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
        // Server should be available through the MockServerManager
        
        // Verify server is accessible
        mockServer.setupSmartDispatcher(sessions = emptyList())
        val sessions = service.listSessions(forceRefresh = true)
        assertNotNull(sessions)
        
        // Register editor again
        service.registerActiveEditor(mockFile1)
        assertTrue(service.hasActiveEditor())
        
        // Create a session while editor is active
        val createResponse = TestDataFactory.createSessionResponse(id = "editor-session")
        mockServer.setupSmartDispatcher(
            sessions = emptyList(),
            createResponse = createResponse
        )
        
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
    
    // ========== Additional Integration Test: Error Recovery ==========
    
    @Test
    fun `test error recovery across multiple operations`() = runBlocking {
        // This test verifies that the service recovers gracefully from errors
        
        // Step 1: Successful operation
        val session = TestDataFactory.createSessionInfo(id = "error-test-1")
        mockServer.setupSmartDispatcher(sessions = listOf(session))
        val result1 = service.listSessions(forceRefresh = true)
        assertEquals(1, result1.size)
        
        // Step 2: Server error - create new dispatcher with error handling
        // The smart dispatcher doesn't have a specific error mode, so we'll test with a missing response
        mockServer.setupSmartDispatcher(sessions = emptyList()) // Returns empty list
        val result2 = service.listSessions(forceRefresh = true)
        // Should return empty list
        assertNotNull(result2)
        
        // Step 3: Recovery - server works again
        mockServer.setupSmartDispatcher(sessions = listOf(session))
        val result3 = service.listSessions(forceRefresh = true)
        assertEquals(1, result3.size)
        
        // Step 4: Create session fails (no createResponse configured)
        mockServer.setupSmartDispatcher(sessions = listOf(session)) // No createResponse
        try {
            service.createSession("Error Test")
            fail<Unit>("Expected exception")
        } catch (e: Exception) {
            // Expected
            assertTrue(e.message?.contains("Failed to create session") ?: false)
        }
        
        // Step 5: Service still functional after error
        mockServer.setupSmartDispatcher(sessions = listOf(session))
        val result4 = service.listSessions(forceRefresh = true)
        assertEquals(1, result4.size)
        
        // Verify cache is still functional
        val cache = getSessionCache()
        assertNotNull(cache)
        assertTrue(cache.containsKey("error-test-1"))
    }
}
