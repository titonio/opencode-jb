package com.opencode.service

import com.intellij.openapi.project.Project
import com.opencode.test.MockServerManager
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for OpenCodeService integration with ServerManager.
 *
 * Tests cover:
 * - Service properly delegates to ServerManager
 * - Server lifecycle in response to editor state changes
 * - Service behavior with successful and failed server starts
 *
 * Note: Detailed ServerManager behavior is tested in DefaultServerManagerTest.
 * This test suite focuses on service-level integration.
 */
class OpenCodeServiceServerTest {

    private lateinit var mockProject: Project
    private lateinit var mockWebServer: MockWebServer

    @BeforeEach
    fun setUp() {
        // Create a mock project
        mockProject = mock()
        whenever(mockProject.name).thenReturn("TestProject")
        whenever(mockProject.basePath).thenReturn("/test/project")

        // Initialize mock web server for HTTP tests
        mockWebServer = MockWebServer()
    }

    @AfterEach
    fun tearDown() {
        try {
            mockWebServer.shutdown()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    // ========== Service Integration with ServerManager ==========

    @Test
    fun `getOrStartSharedServer delegates to ServerManager and returns port`() = runBlocking {
        // Arrange
        val mockPort = 3000
        val mockServerManager = MockServerManager(mockPort = mockPort, shouldSucceed = true)
        val service = OpenCodeService(mockProject, mockServerManager)

        // Act
        val port = service.getOrStartSharedServer()

        // Assert
        assertEquals(mockPort, port)
        assertTrue(mockServerManager.isStarted)
    }

    @Test
    fun `getOrStartSharedServer returns null when ServerManager fails`() = runBlocking {
        // Arrange
        val mockServerManager = MockServerManager(shouldSucceed = false)
        val service = OpenCodeService(mockProject, mockServerManager)

        // Act
        val port = service.getOrStartSharedServer()

        // Assert
        assertNull(port)
    }

    @Test
    fun `isServerRunning delegates to ServerManager`() = runBlocking {
        // Arrange
        val mockPort = 3000
        val mockServerManager = MockServerManager(mockPort = mockPort, shouldSucceed = true)
        val service = OpenCodeService(mockProject, mockServerManager)

        // Start the server first
        service.getOrStartSharedServer()

        // Act
        val isRunning = service.isServerRunning(mockPort)

        // Assert
        assertTrue(isRunning)
    }

    @Test
    fun `isServerRunning returns false for wrong port`() = runBlocking {
        // Arrange
        val mockPort = 3000
        val wrongPort = 4000
        val mockServerManager = MockServerManager(mockPort = mockPort, shouldSucceed = true)
        val service = OpenCodeService(mockProject, mockServerManager)

        // Start the server first
        service.getOrStartSharedServer()

        // Act
        val isRunning = service.isServerRunning(wrongPort)

        // Assert
        assertFalse(isRunning)
    }
}
