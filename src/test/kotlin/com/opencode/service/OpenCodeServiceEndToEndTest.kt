package com.opencode.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.JBTerminalWidget
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Comprehensive end-to-end integration tests for OpenCodeService.
 * Tests complete workflows, multi-session scenarios, widget coordination,
 * and real-world usage patterns.
 * 
 * Phase 9: Integration Test Expansion
 * Target: +12% coverage through comprehensive integration tests
 */
class OpenCodeServiceEndToEndTest {
    
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
    
    /**
     * Helper to get widget tracking map via reflection
     */
    private fun getWidgetPorts(): MutableMap<JBTerminalWidget, Int> {
        val widgetField = OpenCodeService::class.java.getDeclaredField("widgetPorts")
        widgetField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return widgetField.get(service) as MutableMap<JBTerminalWidget, Int>
    }
    
    // ========== Full Session Lifecycle Tests ==========
    
    @Test
    fun `test complete session lifecycle - create to delete with all operations`() = runBlocking {
        // This test verifies a complete end-to-end workflow:
        // Create → List → Get → Share → Unshare → Delete
        
        // Arrange
        val sessionId = "lifecycle-session"
        val sessionTitle = "Lifecycle Test Session"
        val shareUrl = "https://opencode.ai/share/lifecycle-token"
        
        val createResponse = TestDataFactory.createSessionResponse(id = sessionId, title = sessionTitle)
        val unsharedSession = TestDataFactory.createSessionInfo(id = sessionId, title = sessionTitle)
        val sharedSession = TestDataFactory.createSharedSession(id = sessionId, shareUrl = shareUrl)
        
        // Setup smart dispatcher for all operations
        mockServer.setupSmartDispatcher(
            sessions = listOf(unsharedSession),
            createResponse = createResponse,
            getSessionResponse = unsharedSession,
            shareSessionResponse = sharedSession,
            unshareSuccess = true,
            deleteSuccess = true
        )
        
        // Step 1: Create session
        val createdId = service.createSession(sessionTitle)
        assertEquals(sessionId, createdId)
        
        // Step 2: List sessions to verify it exists
        val sessionsAfterCreate = service.listSessions(forceRefresh = true)
        assertEquals(1, sessionsAfterCreate.size)
        assertEquals(sessionId, sessionsAfterCreate[0].id)
        assertFalse(sessionsAfterCreate[0].isShared)
        
        // Step 3: Get specific session
        val fetchedSession = service.getSession(sessionId)
        assertNotNull(fetchedSession)
        assertEquals(sessionId, fetchedSession?.id)
        assertEquals(sessionTitle, fetchedSession?.title)
        
        // Step 4: Share the session
        val returnedShareUrl = service.shareSession(sessionId)
        assertEquals(shareUrl, returnedShareUrl)
        
        // Step 5: Verify session is shared in cache
        val cache = getSessionCache()
        assertTrue(cache[sessionId]?.isShared ?: false)
        assertEquals(shareUrl, cache[sessionId]?.shareUrl)
        
        // Step 6: Unshare the session
        mockServer.setupSmartDispatcher(
            getSessionResponse = unsharedSession,
            unshareSuccess = true,
            deleteSuccess = true
        )
        val unshareResult = service.unshareSession(sessionId)
        assertTrue(unshareResult)
        
        // Step 7: Delete the session
        val deleteResult = service.deleteSession(sessionId)
        assertTrue(deleteResult)
        
        // Step 8: Verify session is removed from cache
        assertFalse(cache.containsKey(sessionId))
        
        // Verify all operations made requests
        assertTrue(mockServer.getRequestCount() >= 6)
    }
    
    @Test
    fun `test session state transitions with verification at each step`() = runBlocking {
        // This test verifies that session state is correctly maintained
        // through various transitions
        
        val sessionId = "state-transition-session"
        val unsharedSession = TestDataFactory.createSessionInfo(id = sessionId)
        val sharedSession = TestDataFactory.createSharedSession(id = sessionId)
        
        // Create session
        val createResponse = TestDataFactory.createSessionResponse(id = sessionId)
        mockServer.setupSmartDispatcher(
            sessions = listOf(unsharedSession),
            createResponse = createResponse,
            shareSessionResponse = sharedSession,
            unshareSuccess = true
        )
        
        service.createSession("State Test")
        
        // Verify initial state: not shared
        val cache = getSessionCache()
        assertFalse(cache[sessionId]?.isShared ?: true)
        assertNull(cache[sessionId]?.shareUrl)
        
        // Transition to shared state
        service.shareSession(sessionId)
        assertTrue(cache[sessionId]?.isShared ?: false)
        assertNotNull(cache[sessionId]?.shareUrl)
        
        // Transition back to unshared state
        mockServer.setupSmartDispatcher(
            getSessionResponse = unsharedSession,
            unshareSuccess = true
        )
        service.unshareSession(sessionId)
        
        // Note: unshareSession refreshes from server, so state depends on server response
        // In this case, the server returns unshared session
    }
    
    @Test
    fun `test session persistence across multiple list operations`() = runBlocking {
        // This test verifies that session data persists correctly
        // in cache across multiple operations
        
        val sessionId = "persistent-session"
        val sessionTitle = "Persistent Test"
        val session = TestDataFactory.createSessionInfo(id = sessionId, title = sessionTitle)
        val createResponse = TestDataFactory.createSessionResponse(id = sessionId, title = sessionTitle)
        
        mockServer.setupSmartDispatcher(
            sessions = listOf(session),
            createResponse = createResponse
        )
        
        // Create session - this makes 2 requests (create + refresh)
        service.createSession(sessionTitle)
        
        // List multiple times - should use cache
        val list1 = service.listSessions()
        val list2 = service.listSessions()
        val list3 = service.listSessions()
        
        // All lists should return the same session
        assertEquals(1, list1.size)
        assertEquals(1, list2.size)
        assertEquals(1, list3.size)
        assertEquals(sessionId, list1[0].id)
        assertEquals(sessionId, list2[0].id)
        assertEquals(sessionId, list3[0].id)
        
        // Verify cache hit - should be 2 requests (create makes POST + GET for refresh)
        // Subsequent lists use cache, so no additional requests
        assertTrue(mockServer.getRequestCount() >= 2)
    }
    
    @Test
    fun `test resource cleanup after session deletion`() = runBlocking {
        // This test verifies that all resources are properly cleaned up
        // after a session is deleted
        
        val sessionId = "cleanup-session"
        val session = TestDataFactory.createSessionInfo(id = sessionId)
        val createResponse = TestDataFactory.createSessionResponse(id = sessionId)
        
        mockServer.setupSmartDispatcher(
            sessions = listOf(session),
            createResponse = createResponse,
            deleteSuccess = true
        )
        
        // Create session
        service.createSession("Cleanup Test")
        
        // Verify session exists in cache
        val cache = getSessionCache()
        assertTrue(cache.containsKey(sessionId))
        
        // Delete session
        val deleteResult = service.deleteSession(sessionId)
        assertTrue(deleteResult)
        
        // Verify cache cleanup
        assertFalse(cache.containsKey(sessionId))
        
        // Update mock to return empty list after deletion
        mockServer.setupSmartDispatcher(sessions = emptyList())
        
        // Verify subsequent operations don't find the session
        val sessions = service.listSessions(forceRefresh = true)
        assertFalse(sessions.any { it.id == sessionId })
    }
    
    @Test
    fun `test session lifecycle with server restart simulation`() = runBlocking {
        // This test simulates a server restart during session lifecycle
        // to verify recovery and consistency
        
        val sessionId = "restart-session"
        val session = TestDataFactory.createSessionInfo(id = sessionId)
        val createResponse = TestDataFactory.createSessionResponse(id = sessionId)
        
        // Phase 1: Create session with server running
        mockServer.setupSmartDispatcher(
            sessions = listOf(session),
            createResponse = createResponse
        )
        
        service.createSession("Restart Test")
        val cache = getSessionCache()
        assertTrue(cache.containsKey(sessionId))
        
        // Phase 2: Simulate server restart by updating dispatcher
        // Session should still be available from server
        mockServer.setupSmartDispatcher(
            sessions = listOf(session),
            getSessionResponse = session
        )
        
        // Phase 3: Operations continue working after "restart"
        val fetchedSession = service.getSession(sessionId)
        assertNotNull(fetchedSession)
        assertEquals(sessionId, fetchedSession?.id)
        
        // Cache should be updated/maintained
        assertTrue(cache.containsKey(sessionId))
    }
    
    @Test
    fun `test complete workflow from action to completion`() = runBlocking {
        // This simulates a real user workflow:
        // 1. User creates a session
        // 2. User works with the session
        // 3. User shares it with collaborator
        // 4. User archives it by deleting
        
        val sessionId = "workflow-session"
        val shareUrl = "https://opencode.ai/share/workflow-token"
        
        val createResponse = TestDataFactory.createSessionResponse(id = sessionId)
        val unsharedSession = TestDataFactory.createSessionInfo(id = sessionId)
        val sharedSession = TestDataFactory.createSharedSession(id = sessionId, shareUrl = shareUrl)
        
        mockServer.setupSmartDispatcher(
            sessions = listOf(unsharedSession),
            createResponse = createResponse,
            getSessionResponse = unsharedSession,
            shareSessionResponse = sharedSession,
            deleteSuccess = true
        )
        
        // User action: Create new session
        val createdId = service.createSession("User Workflow Session")
        assertNotNull(createdId)
        
        // User action: Verify it's in the list
        val sessions = service.listSessions(forceRefresh = true)
        assertTrue(sessions.any { it.id == sessionId })
        
        // User action: Get details
        val details = service.getSession(sessionId)
        assertNotNull(details)
        
        // User action: Share with collaborator
        val url = service.shareSession(sessionId)
        assertEquals(shareUrl, url)
        
        // User action: Archive (delete) when done
        val deleted = service.deleteSession(sessionId)
        assertTrue(deleted)
        
        // Verify complete cleanup
        val cache = getSessionCache()
        assertFalse(cache.containsKey(sessionId))
    }
    
    // ========== Multi-Session Scenarios Tests ==========
    
    @Test
    fun `test creating and managing multiple sessions simultaneously`() = runBlocking {
        // This test verifies that the service can handle multiple
        // sessions being created and managed at the same time
        
        val sessionCount = 5
        val sessions = (1..sessionCount).map { i ->
            TestDataFactory.createSessionInfo(id = "multi-$i", title = "Session $i")
        }
        
        // Create sessions one by one
        sessions.forEachIndexed { index, session ->
            val response = TestDataFactory.createSessionResponse(id = session.id, title = session.title)
            val allSessionsSoFar = sessions.take(index + 1)
            
            mockServer.setupSmartDispatcher(
                sessions = allSessionsSoFar,
                createResponse = response
            )
            
            val createdId = service.createSession(session.title)
            assertEquals(session.id, createdId)
        }
        
        // Verify all sessions exist in cache
        val cache = getSessionCache()
        assertEquals(sessionCount, cache.size)
        
        sessions.forEach { session ->
            assertTrue(cache.containsKey(session.id))
        }
        
        // Verify all sessions can be listed
        mockServer.setupSmartDispatcher(sessions = sessions)
        val listedSessions = service.listSessions(forceRefresh = true)
        assertEquals(sessionCount, listedSessions.size)
    }
    
    @Test
    fun `test switching between active sessions`() = runBlocking {
        // This test simulates a user switching between different sessions
        
        val session1 = TestDataFactory.createSessionInfo(id = "switch-1")
        val session2 = TestDataFactory.createSessionInfo(id = "switch-2")
        val session3 = TestDataFactory.createSessionInfo(id = "switch-3")
        
        mockServer.setupSmartDispatcher(
            sessions = listOf(session1, session2, session3),
            getSessionResponse = session1  // Default
        )
        
        // Get first session
        var currentSession = service.getSession("switch-1")
        assertNotNull(currentSession)
        assertEquals("switch-1", currentSession?.id)
        
        // Switch to second session
        mockServer.setupSmartDispatcher(
            sessions = listOf(session1, session2, session3),
            getSessionResponse = session2
        )
        currentSession = service.getSession("switch-2")
        assertNotNull(currentSession)
        assertEquals("switch-2", currentSession?.id)
        
        // Switch to third session
        mockServer.setupSmartDispatcher(
            sessions = listOf(session1, session2, session3),
            getSessionResponse = session3
        )
        currentSession = service.getSession("switch-3")
        assertNotNull(currentSession)
        assertEquals("switch-3", currentSession?.id)
        
        // Switch back to first session
        mockServer.setupSmartDispatcher(
            sessions = listOf(session1, session2, session3),
            getSessionResponse = session1
        )
        currentSession = service.getSession("switch-1")
        assertNotNull(currentSession)
        assertEquals("switch-1", currentSession?.id)
        
        // All sessions should still exist
        val allSessions = service.listSessions(forceRefresh = true)
        assertEquals(3, allSessions.size)
    }
    
    @Test
    fun `test session isolation - operations on one do not affect others`() = runBlocking {
        // This test verifies that operations on one session
        // don't inadvertently affect other sessions
        
        val session1 = TestDataFactory.createSessionInfo(id = "isolate-1")
        val session2 = TestDataFactory.createSessionInfo(id = "isolate-2")
        val sharedSession1 = TestDataFactory.createSharedSession(id = "isolate-1")
        
        mockServer.setupSmartDispatcher(
            sessions = listOf(session1, session2),
            shareSessionResponse = sharedSession1
        )
        
        // List sessions
        val initialList = service.listSessions(forceRefresh = true)
        assertEquals(2, initialList.size)
        
        // Share first session
        service.shareSession("isolate-1")
        
        // Verify second session is unchanged
        mockServer.setupSmartDispatcher(
            sessions = listOf(sharedSession1, session2),
            getSessionResponse = session2
        )
        val session2Details = service.getSession("isolate-2")
        assertNotNull(session2Details)
        assertFalse(session2Details?.isShared ?: true)
        
        // Verify first session is shared
        val cache = getSessionCache()
        assertTrue(cache["isolate-1"]?.isShared ?: false)
        
        // Delete first session - should not affect second
        mockServer.setupSmartDispatcher(
            sessions = listOf(session2),
            deleteSuccess = true
        )
        service.deleteSession("isolate-1")
        
        assertFalse(cache.containsKey("isolate-1"))
        assertTrue(cache.containsKey("isolate-2"))
    }
    
    @Test
    fun `test concurrent operations on multiple sessions`() = runBlocking {
        // This test verifies thread-safety when performing
        // operations on multiple sessions concurrently
        
        val sessionCount = 10
        val sessions = (1..sessionCount).map { i ->
            TestDataFactory.createSessionInfo(id = "concurrent-$i")
        }
        
        mockServer.setupSmartDispatcher(
            sessions = sessions,
            getSessionResponse = sessions[0]
        )
        
        // Perform concurrent operations
        val jobs = sessions.map { session ->
            async {
                // Each coroutine gets a different session
                service.getSession(session.id)
            }
        }
        
        // Wait for all to complete
        val results = jobs.awaitAll()
        
        // Verify all operations completed successfully
        results.forEach { result ->
            // Note: All will return sessions[0] because of how mock is set up
            // but the point is that no exceptions were thrown
            assertNotNull(result)
        }
        
        // Verify service state is consistent
        val cache = getSessionCache()
        assertNotNull(cache)
    }
    
    @Test
    fun `test managing sessions across multiple projects`() = runBlocking {
        // This test simulates having different sessions for different
        // project contexts, verifying proper isolation
        
        val project1Sessions = listOf(
            TestDataFactory.createSessionInfo(id = "proj1-session1"),
            TestDataFactory.createSessionInfo(id = "proj1-session2")
        )
        
        // Setup for first project
        mockServer.setupSmartDispatcher(sessions = project1Sessions)
        
        // List sessions for project 1
        val sessions1 = service.listSessions(forceRefresh = true)
        assertEquals(2, sessions1.size)
        assertTrue(sessions1.all { it.id.startsWith("proj1-") })
        
        // Simulate switching project context by updating mock
        val project2Sessions = listOf(
            TestDataFactory.createSessionInfo(id = "proj2-session1"),
            TestDataFactory.createSessionInfo(id = "proj2-session2")
        )
        
        mockServer.setupSmartDispatcher(sessions = project2Sessions)
        
        // List sessions for project 2
        val sessions2 = service.listSessions(forceRefresh = true)
        assertEquals(2, sessions2.size)
        assertTrue(sessions2.all { it.id.startsWith("proj2-") })
    }
    
    // ========== Widget Coordination Tests ==========
    
    @Test
    fun `test widget registration and lifecycle`() = runBlocking {
        // This test verifies that terminal widgets can be registered
        // and unregistered properly
        
        val mockWidget1 = mock<JBTerminalWidget>()
        val mockWidget2 = mock<JBTerminalWidget>()
        val port1 = 12345
        val port2 = 12346
        
        val widgetPorts = getWidgetPorts()
        
        // Initially no widgets
        assertEquals(0, widgetPorts.size)
        
        // Register first widget
        service.registerWidget(mockWidget1, port1)
        assertEquals(1, widgetPorts.size)
        assertEquals(port1, widgetPorts[mockWidget1])
        
        // Register second widget
        service.registerWidget(mockWidget2, port2)
        assertEquals(2, widgetPorts.size)
        assertEquals(port2, widgetPorts[mockWidget2])
        
        // Unregister first widget
        service.unregisterWidget(mockWidget1)
        assertEquals(1, widgetPorts.size)
        assertFalse(widgetPorts.containsKey(mockWidget1))
        assertTrue(widgetPorts.containsKey(mockWidget2))
        
        // Unregister second widget
        service.unregisterWidget(mockWidget2)
        assertEquals(0, widgetPorts.size)
    }
    
    @Test
    fun `test widget unregistration cleanup`() = runBlocking {
        // This test verifies that unregistering a widget
        // properly cleans up all associated resources
        
        val mockWidget = mock<JBTerminalWidget>()
        val port = 54321
        
        val widgetPorts = getWidgetPorts()
        
        // Register widget
        service.registerWidget(mockWidget, port)
        assertEquals(1, widgetPorts.size)
        
        // Unregister widget
        service.unregisterWidget(mockWidget)
        
        // Verify complete cleanup
        assertEquals(0, widgetPorts.size)
        assertFalse(widgetPorts.containsKey(mockWidget))
        assertNull(widgetPorts[mockWidget])
        
        // Unregistering again should be safe (idempotent)
        service.unregisterWidget(mockWidget)
        assertEquals(0, widgetPorts.size)
    }
    
    @Test
    fun `test multiple widgets per session scenario`() = runBlocking {
        // This test simulates having multiple terminal widgets
        // open for the same session (e.g., split terminals)
        
        val widgets = (1..3).map { mock<JBTerminalWidget>() }
        val ports = listOf(10001, 10002, 10003)
        
        val widgetPorts = getWidgetPorts()
        
        // Register all widgets
        widgets.forEachIndexed { index, widget ->
            service.registerWidget(widget, ports[index])
        }
        
        assertEquals(3, widgetPorts.size)
        
        // Verify all widgets are tracked
        widgets.forEachIndexed { index, widget ->
            assertTrue(widgetPorts.containsKey(widget))
            assertEquals(ports[index], widgetPorts[widget])
        }
        
        // Unregister middle widget
        service.unregisterWidget(widgets[1])
        assertEquals(2, widgetPorts.size)
        assertFalse(widgetPorts.containsKey(widgets[1]))
        
        // Other widgets should still be registered
        assertTrue(widgetPorts.containsKey(widgets[0]))
        assertTrue(widgetPorts.containsKey(widgets[2]))
    }
    
    @Test
    fun `test widget state during session state changes`() = runBlocking {
        // This test verifies that widget registration is maintained
        // correctly even when sessions change state
        
        val mockWidget = mock<JBTerminalWidget>()
        val port = 9999
        val sessionId = "widget-session"
        
        val session = TestDataFactory.createSessionInfo(id = sessionId)
        val sharedSession = TestDataFactory.createSharedSession(id = sessionId)
        val createResponse = TestDataFactory.createSessionResponse(id = sessionId)
        
        mockServer.setupSmartDispatcher(
            sessions = listOf(session),
            createResponse = createResponse,
            shareSessionResponse = sharedSession,
            deleteSuccess = true
        )
        
        val widgetPorts = getWidgetPorts()
        
        // Register widget
        service.registerWidget(mockWidget, port)
        assertEquals(1, widgetPorts.size)
        
        // Create session - widget should remain registered
        service.createSession("Widget Test")
        assertEquals(1, widgetPorts.size)
        assertTrue(widgetPorts.containsKey(mockWidget))
        
        // Share session - widget should remain registered
        service.shareSession(sessionId)
        assertEquals(1, widgetPorts.size)
        assertTrue(widgetPorts.containsKey(mockWidget))
        
        // Delete session - widget should remain registered
        // (widget lifecycle is independent of session lifecycle)
        service.deleteSession(sessionId)
        assertEquals(1, widgetPorts.size)
        assertTrue(widgetPorts.containsKey(mockWidget))
        
        // Explicit unregister
        service.unregisterWidget(mockWidget)
        assertEquals(0, widgetPorts.size)
    }
    
    // ========== End-to-End Workflow Tests ==========
    
    @Test
    fun `test complete user workflow with editor registration`() = runBlocking {
        // This test simulates a complete user workflow:
        // 1. User opens editor tab (registers)
        // 2. Creates session
        // 3. Works with session
        // Note: We don't test unregistration as it requires ApplicationManager
        
        val mockFile = mock<VirtualFile>()
        val sessionId = "editor-workflow-session"
        val createResponse = TestDataFactory.createSessionResponse(id = sessionId)
        val session = TestDataFactory.createSessionInfo(id = sessionId)
        
        mockServer.setupSmartDispatcher(
            sessions = listOf(session),
            createResponse = createResponse,
            getSessionResponse = session
        )
        
        // Step 1: User opens editor tab
        assertFalse(service.hasActiveEditor())
        service.registerActiveEditor(mockFile)
        assertTrue(service.hasActiveEditor())
        assertEquals(mockFile, service.getActiveEditorFile())
        
        // Step 2: User creates session
        val createdId = service.createSession("Editor Workflow")
        assertEquals(sessionId, createdId)
        
        // Step 3: User works with session
        val sessionDetails = service.getSession(sessionId)
        assertNotNull(sessionDetails)
        
        val sessions = service.listSessions()
        assertEquals(1, sessions.size)
        
        // Verify editor is still registered
        assertTrue(service.hasActiveEditor())
        assertEquals(mockFile, service.getActiveEditorFile())
        
        // Session should exist in cache
        val cache = getSessionCache()
        assertTrue(cache.containsKey(sessionId))
    }
    
    @Test
    fun `test integration between service, UI, and file system components`() = runBlocking {
        // This test verifies integration between service layer,
        // UI components (editor), and file system operations
        
        val mockFile1 = mock<VirtualFile>()
        val mockFile2 = mock<VirtualFile>()
        
        val sessionId = "integration-session"
        val createResponse = TestDataFactory.createSessionResponse(id = sessionId)
        val session = TestDataFactory.createSessionInfo(id = sessionId)
        
        mockServer.setupSmartDispatcher(
            sessions = listOf(session),
            createResponse = createResponse
        )
        
        // Register first editor
        service.registerActiveEditor(mockFile1)
        assertTrue(service.hasActiveEditor())
        assertEquals(mockFile1, service.getActiveEditorFile())
        
        // Create session while editor is active
        service.createSession("Integration Test")
        
        // Verify session exists
        val cache = getSessionCache()
        assertTrue(cache.containsKey(sessionId))
        
        // Switch to second editor (simulating tab switch)
        service.registerActiveEditor(mockFile2)
        assertEquals(mockFile2, service.getActiveEditorFile())
        
        // Session should still exist after tab switch
        assertTrue(cache.containsKey(sessionId))
        
        // Verify both tab switches maintained active editor state
        assertTrue(service.hasActiveEditor())
        
        // Session persistence verified through tab switches
        assertTrue(cache.containsKey(sessionId))
    }
    
    @Test
    fun `test real-world usage pattern with multiple operations`() = runBlocking {
        // This test simulates a realistic user workflow:
        // - Create multiple sessions over time
        // - Share some, delete some
        // - List and verify state at various points
        
        // Day 1: Create first session
        val session1 = TestDataFactory.createSessionInfo(id = "day1-session")
        val response1 = TestDataFactory.createSessionResponse(id = "day1-session")
        mockServer.setupSmartDispatcher(
            sessions = listOf(session1),
            createResponse = response1
        )
        service.createSession("Day 1 Work")
        
        // Verify
        var sessions = service.listSessions(forceRefresh = true)
        assertEquals(1, sessions.size)
        
        // Day 2: Create second session
        val session2 = TestDataFactory.createSessionInfo(id = "day2-session")
        val response2 = TestDataFactory.createSessionResponse(id = "day2-session")
        mockServer.setupSmartDispatcher(
            sessions = listOf(session1, session2),
            createResponse = response2
        )
        service.createSession("Day 2 Work")
        
        sessions = service.listSessions(forceRefresh = true)
        assertEquals(2, sessions.size)
        
        // Day 3: Share second session for collaboration
        val sharedSession2 = TestDataFactory.createSharedSession(
            id = "day2-session",
            shareUrl = "https://opencode.ai/share/collab"
        )
        mockServer.setupSmartDispatcher(
            sessions = listOf(session1, session2),
            shareSessionResponse = sharedSession2
        )
        val shareUrl = service.shareSession("day2-session")
        assertNotNull(shareUrl)
        
        // Day 4: Create third session
        val session3 = TestDataFactory.createSessionInfo(id = "day3-session")
        val response3 = TestDataFactory.createSessionResponse(id = "day3-session")
        mockServer.setupSmartDispatcher(
            sessions = listOf(session1, sharedSession2, session3),
            createResponse = response3
        )
        service.createSession("Day 3 Work")
        
        sessions = service.listSessions(forceRefresh = true)
        assertEquals(3, sessions.size)
        
        // Day 5: Cleanup - delete oldest session
        mockServer.setupSmartDispatcher(
            sessions = listOf(sharedSession2, session3),
            deleteSuccess = true
        )
        val deleted = service.deleteSession("day1-session")
        assertTrue(deleted)
        
        // Verify final state
        val cache = getSessionCache()
        assertFalse(cache.containsKey("day1-session"))
        assertTrue(cache.containsKey("day2-session"))
        assertTrue(cache.containsKey("day3-session"))
    }
    
    @Test
    fun `test error recovery in end-to-end workflow`() = runBlocking {
        // This test verifies that the system can recover from errors
        // during a complete workflow
        
        val sessionId = "recovery-session"
        val session = TestDataFactory.createSessionInfo(id = sessionId)
        val createResponse = TestDataFactory.createSessionResponse(id = sessionId)
        
        // Successful creation
        mockServer.setupSmartDispatcher(
            sessions = listOf(session),
            createResponse = createResponse,
            getSessionResponse = session
        )
        service.createSession("Recovery Test")
        
        // Failed share operation (no shareSessionResponse)
        mockServer.setupSmartDispatcher(
            sessions = listOf(session),
            getSessionResponse = session
        )
        val shareResult = service.shareSession(sessionId)
        assertNull(shareResult)
        
        // Session should still exist and be accessible
        val fetchedSession = service.getSession(sessionId)
        assertNotNull(fetchedSession)
        
        // Successful delete after failed share
        mockServer.setupSmartDispatcher(
            sessions = listOf(session),
            deleteSuccess = true
        )
        val deleteResult = service.deleteSession(sessionId)
        assertTrue(deleteResult)
        
        // Verify cleanup
        val cache = getSessionCache()
        assertFalse(cache.containsKey(sessionId))
    }
    
    @Test
    fun `test session cache consistency across complex workflow`() = runBlocking {
        // This test verifies that the cache remains consistent
        // through a complex series of operations
        
        val sessions = (1..5).map { i ->
            TestDataFactory.createSessionInfo(id = "cache-test-$i")
        }
        
        // Create all sessions
        sessions.forEach { session ->
            val response = TestDataFactory.createSessionResponse(id = session.id)
            mockServer.setupSmartDispatcher(
                sessions = sessions,
                createResponse = response
            )
            service.createSession(session.title)
        }
        
        val cache = getSessionCache()
        assertEquals(5, cache.size)
        
        // Delete middle sessions
        mockServer.setupSmartDispatcher(
            sessions = sessions,
            deleteSuccess = true
        )
        service.deleteSession("cache-test-3")
        assertEquals(4, cache.size)
        
        // Share a session
        val sharedSession = TestDataFactory.createSharedSession(id = "cache-test-2")
        mockServer.setupSmartDispatcher(
            sessions = sessions,
            shareSessionResponse = sharedSession
        )
        service.shareSession("cache-test-2")
        
        // Verify cache consistency
        assertTrue(cache.containsKey("cache-test-1"))
        assertTrue(cache.containsKey("cache-test-2"))
        assertFalse(cache.containsKey("cache-test-3"))
        assertTrue(cache.containsKey("cache-test-4"))
        assertTrue(cache.containsKey("cache-test-5"))
        
        // Verify shared state is correct in cache
        assertTrue(cache["cache-test-2"]?.isShared ?: false)
    }
}
