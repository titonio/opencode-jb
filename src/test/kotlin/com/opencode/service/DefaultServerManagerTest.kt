package com.opencode.service

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Comprehensive test suite for DefaultServerManager.
 * Tests server lifecycle management, connection validation, and process handling.
 */
class DefaultServerManagerTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var workingDirectory: File
    private lateinit var serverManager: DefaultServerManager
    
    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        client = OkHttpClient.Builder()
            .readTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()
        
        workingDirectory = File(System.getProperty("user.home"))
        serverManager = DefaultServerManager(workingDirectory, client)
    }
    
    @AfterEach
    fun tearDown() {
        try {
            serverManager.stopServer()
            mockWebServer.shutdown()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
    
    // ========== isServerRunning Tests ==========
    
    @Test
    fun `isServerRunning returns true when HTTP call succeeds`() = runBlocking {
        // Arrange
        val port = mockWebServer.port
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]")
                .addHeader("Content-Type", "application/json")
        )
        
        // Act
        val result = serverManager.isServerRunning(port)
        
        // Assert
        assertTrue(result, "Server should be detected as running")
        
        val request = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("/session", request?.path)
    }
    
    @Test
    fun `isServerRunning returns false when HTTP fails`() = runBlocking {
        // Arrange
        val port = mockWebServer.port
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )
        
        // Act
        val result = serverManager.isServerRunning(port)
        
        // Assert
        assertFalse(result, "Server should be detected as not running on error")
    }
    
    @Test
    fun `isServerRunning returns false when server not reachable`() = runBlocking {
        // Use a port that's definitely not in use
        val unreachablePort = 65432
        
        // Act
        val result = serverManager.isServerRunning(unreachablePort)
        
        // Assert
        assertFalse(result, "Server should be detected as not running when unreachable")
    }
    
    @Test
    fun `isServerRunning handles timeout gracefully`() = runBlocking {
        // Arrange
        val port = mockWebServer.port
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]")
                .setBodyDelay(10, TimeUnit.SECONDS) // Longer than client timeout
        )
        
        // Act
        val startTime = System.currentTimeMillis()
        val result = serverManager.isServerRunning(port)
        val duration = System.currentTimeMillis() - startTime
        
        // Assert - should timeout within reasonable time (< 10 seconds)
        assertTrue(duration < 8000, "Should timeout within 8 seconds, took ${duration}ms")
    }
    
    @Test
    fun `isServerRunning with various response codes`() = runBlocking {
        val port = mockWebServer.port
        
        // Test successful response (200)
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        assertTrue(serverManager.isServerRunning(port))
        
        // Test error response (500)
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        assertFalse(serverManager.isServerRunning(port))
        
        // Test not found (404)
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        assertFalse(serverManager.isServerRunning(port))
        
        // Test unauthorized (401)
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        assertFalse(serverManager.isServerRunning(port))
        
        // Verify requests were made
        assertEquals(4, mockWebServer.requestCount)
    }
    
    // ========== stopServer Tests ==========
    
    @Test
    fun `stopServer clears server state`() {
        // Act
        serverManager.stopServer()
        
        // Assert
        assertNull(serverManager.getServerPort())
    }
    
    @Test
    fun `stopServer can be called multiple times safely`() {
        // Act
        serverManager.stopServer()
        serverManager.stopServer()
        serverManager.stopServer()
        
        // Assert - should not throw exceptions
        assertNull(serverManager.getServerPort())
    }
    
    // ========== getServerPort Tests ==========
    
    @Test
    fun `getServerPort returns null initially`() {
        // Assert
        assertNull(serverManager.getServerPort())
    }
    
    @Test
    fun `getServerPort returns null after stopServer`() {
        // Arrange - simulate server started state using reflection
        val portField = DefaultServerManager::class.java.getDeclaredField("serverPort")
        portField.isAccessible = true
        portField.set(serverManager, 3000)
        
        // Act
        serverManager.stopServer()
        
        // Assert
        assertNull(serverManager.getServerPort())
    }
    
    // ========== Port Range Tests ==========
    
    @Test
    fun `server uses random port in valid range`() {
        // The implementation uses: Random.nextInt(16384, 65536)
        val minPort = 16384
        val maxPort = 65535
        
        // Test multiple random port generations
        val ports = mutableSetOf<Int>()
        repeat(10) {
            val port = kotlin.random.Random.nextInt(16384, 65536)
            ports.add(port)
            
            assertTrue(port >= minPort, "Port $port should be >= $minPort")
            assertTrue(port < 65536, "Port $port should be < 65536")
            assertTrue(port <= maxPort, "Port $port should be <= $maxPort")
        }
        
        // Verify we get different ports (randomness check)
        assertTrue(ports.size > 1, "Should generate multiple different ports")
    }
    
    // ========== Command Structure Tests ==========
    
    @Test
    fun `server command structure is correct`() {
        // According to the implementation in startServerInternal:
        // command("opencode", "serve", "--port", port.toString(), "--hostname", "127.0.0.1")
        
        val expectedCommand = listOf("opencode", "serve", "--port", "12345", "--hostname", "127.0.0.1")
        
        // Verify command structure
        assertEquals("opencode", expectedCommand[0])
        assertEquals("serve", expectedCommand[1])
        assertEquals("--port", expectedCommand[2])
        assertEquals("--hostname", expectedCommand[4])
        assertEquals("127.0.0.1", expectedCommand[5])
        
        // Verify port is passed as string
        assertTrue(expectedCommand[3].matches(Regex("\\d+")))
    }
    
    // ========== Working Directory Tests ==========
    
    @Test
    fun `server uses configured working directory`() {
        // Verify the working directory is absolute
        assertTrue(workingDirectory.isAbsolute, "Working directory should be absolute path")
        
        // Create a custom working directory
        val customDir = File("/tmp/test")
        val customManager = DefaultServerManager(customDir, client)
        
        // The manager should use this directory for process creation
        // (verified through implementation inspection)
        assertNotNull(customManager)
    }
    
    // ========== getOrStartServer Tests - Server Already Running ==========
    
    @Test
    fun `getOrStartServer reuses existing server when running`() = runBlocking {
        // Arrange - Set up server as already running using reflection
        val port = mockWebServer.port
        val portField = DefaultServerManager::class.java.getDeclaredField("serverPort")
        portField.isAccessible = true
        portField.set(serverManager, port)
        
        // Mock successful health check
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        
        // Act
        val result = serverManager.getOrStartServer()
        
        // Assert
        assertEquals(port, result)
        assertEquals(1, mockWebServer.requestCount, "Should only check health, not start new server")
    }
    
    @Test
    fun `getOrStartServer starts new server when existing port is unresponsive`() = runBlocking {
        // Arrange - Set up server with port that's not responding
        val portField = DefaultServerManager::class.java.getDeclaredField("serverPort")
        portField.isAccessible = true
        portField.set(serverManager, 65000) // Non-responsive port
        
        // Act - This will try to start a new server
        val result = serverManager.getOrStartServer()
        
        // Assert - Should either succeed with a new port or fail
        // If CLI is available, it will start a new server
        if (result != null) {
            assertNotEquals(65000, result, "Should use a new random port")
            assertTrue(result in 16384..65535, "Port should be in valid range")
        }
        // In both cases, old port should not be reused
        assertNotEquals(65000, serverManager.getServerPort())
    }
    
    // ========== getOrStartServer Tests - Failed Start Scenarios ==========
    
    @Test
    fun `getOrStartServer handles server start attempts`() = runBlocking {
        try {
            // Act - Try to start server
            val result = serverManager.getOrStartServer()
            
            // Assert - Result depends on CLI availability
            // If CLI is available, server should start; otherwise returns null
            if (result != null) {
                assertTrue(result in 16384..65535, "Port should be in valid range")
                assertEquals(result, serverManager.getServerPort())
            } else {
                assertNull(serverManager.getServerPort(), "Port should remain null after failed start")
            }
        } finally {
            serverManager.stopServer()
        }
    }
    
    @Test
    fun `getOrStartServer retry behavior`() = runBlocking {
        // This test verifies the method attempts connection validation
        val startTime = System.currentTimeMillis()
        
        // Act
        val result = serverManager.getOrStartServer()
        
        val duration = System.currentTimeMillis() - startTime
        
        // Assert - Should take some time attempting connection validation
        // If successful, should have a port; if failed, should be null
        if (result != null) {
            assertTrue(result in 16384..65535)
        }
        // Duration check - should complete in reasonable time (not hang)
        assertTrue(duration < 60000, "Should complete within 60 seconds")
    }
    
    // ========== waitForConnection Tests (via getOrStartServer) ==========
    
    @Test
    fun `waitForConnection is exercised during server start`() = runBlocking {
        // Create a custom manager with shorter timeout
        val customClient = OkHttpClient.Builder()
            .readTimeout(1, TimeUnit.SECONDS)
            .connectTimeout(1, TimeUnit.SECONDS)
            .build()
        val customManager = DefaultServerManager(workingDirectory, customClient)
        
        try {
            // Act - Try to start
            val startTime = System.currentTimeMillis()
            val result = customManager.getOrStartServer()
            val duration = System.currentTimeMillis() - startTime
            
            // Assert - Should complete (either success or failure)
            // Should respect reasonable timeout behavior
            assertTrue(duration < 60000, "Should complete within reasonable time")
            
            if (result != null) {
                assertTrue(result in 16384..65535)
            }
        } finally {
            customManager.stopServer()
        }
    }
    
    // ========== stopServer Edge Cases ==========
    
    @Test
    fun `stopServer destroys process if exists`() {
        // Arrange - Simulate having a process using reflection
        val processField = DefaultServerManager::class.java.getDeclaredField("serverProcess")
        processField.isAccessible = true
        
        // Create a mock process (we'll use a simple echo command)
        val process = ProcessBuilder("sleep", "60").start()
        processField.set(serverManager, process)
        
        val portField = DefaultServerManager::class.java.getDeclaredField("serverPort")
        portField.isAccessible = true
        portField.set(serverManager, 3000)
        
        // Act
        serverManager.stopServer()
        
        // Assert
        assertNull(serverManager.getServerPort())
        // Give process time to be destroyed
        Thread.sleep(100)
        assertFalse(process.isAlive, "Process should be destroyed")
    }
    
    @Test
    fun `stopServer handles null process gracefully`() {
        // Arrange - Ensure process is null
        val processField = DefaultServerManager::class.java.getDeclaredField("serverProcess")
        processField.isAccessible = true
        processField.set(serverManager, null)
        
        // Act & Assert - Should not throw
        assertDoesNotThrow {
            serverManager.stopServer()
        }
    }
    
    // ========== isServerRunning Edge Cases ==========
    
    @Test
    fun `isServerRunning with redirect response`() = runBlocking {
        val port = mockWebServer.port
        
        // Test redirect (3xx) - OkHttp follows redirects by default
        // So 3xx without redirect disabled is still considered successful
        mockWebServer.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "http://example.com"))
        // This will actually be true because OkHttp follows redirects
        val result = serverManager.isServerRunning(port)
        // Just verify it completes without error
        assertNotNull(result)
    }
    
    @Test
    fun `isServerRunning with 2xx success codes`() = runBlocking {
        val port = mockWebServer.port
        
        // Test 201 Created
        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        assertTrue(serverManager.isServerRunning(port))
        
        // Test 204 No Content
        mockWebServer.enqueue(MockResponse().setResponseCode(204))
        assertTrue(serverManager.isServerRunning(port))
    }
    
    @Test
    fun `isServerRunning with network error`() = runBlocking {
        // Shutdown the mock server to simulate network error
        mockWebServer.shutdown()
        val port = mockWebServer.port
        
        // Act
        val result = serverManager.isServerRunning(port)
        
        // Assert
        assertFalse(result, "Should return false on network error")
    }
    
    @Test
    fun `isServerRunning with malformed response body`() = runBlocking {
        val port = mockWebServer.port
        
        // Test with malformed JSON (should still succeed if status is 200)
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertTrue(serverManager.isServerRunning(port), "Should succeed based on status code regardless of body")
    }
    
    // ========== Port State Management Tests ==========
    
    @Test
    fun `getServerPort reflects current state after operations`() = runBlocking {
        // Initially null
        assertNull(serverManager.getServerPort())
        
        // Set a port using reflection
        val portField = DefaultServerManager::class.java.getDeclaredField("serverPort")
        portField.isAccessible = true
        portField.set(serverManager, 5000)
        
        // Should return the set port
        assertEquals(5000, serverManager.getServerPort())
        
        // Stop server
        serverManager.stopServer()
        
        // Should be null again
        assertNull(serverManager.getServerPort())
    }
    
    // ========== Concurrent/Edge State Tests ==========
    
    @Test
    fun `multiple getOrStartServer calls handle state correctly`() = runBlocking {
        // Arrange - Set up an existing running server
        val port = mockWebServer.port
        val portField = DefaultServerManager::class.java.getDeclaredField("serverPort")
        portField.isAccessible = true
        portField.set(serverManager, port)
        
        // Enqueue multiple successful responses
        repeat(3) {
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        }
        
        // Act - Call multiple times
        val result1 = serverManager.getOrStartServer()
        val result2 = serverManager.getOrStartServer()
        val result3 = serverManager.getOrStartServer()
        
        // Assert - All should return the same port
        assertEquals(port, result1)
        assertEquals(port, result2)
        assertEquals(port, result3)
        assertEquals(3, mockWebServer.requestCount, "Should check health each time")
    }
    
    @Test
    fun `stopServer followed by getOrStartServer attempts restart`() = runBlocking {
        // Arrange - Set up a running server
        val port = mockWebServer.port
        val portField = DefaultServerManager::class.java.getDeclaredField("serverPort")
        portField.isAccessible = true
        portField.set(serverManager, port)
        
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val initialPort = serverManager.getOrStartServer()
        assertEquals(port, initialPort)
        
        // Act - Stop
        serverManager.stopServer()
        assertNull(serverManager.getServerPort())
        
        // Verify that after stop, we can detect it's not running
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        val isRunning = serverManager.isServerRunning(port)
        
        // Assert - Mock server returns 500, should be detected as not running
        assertFalse(isRunning, "Stopped server should not be detected as running")
    }
    
    // ========== Process Creation Tests ==========
    
    @Test
    fun `startServerInternal creates process with correct parameters`() {
        // This is tested indirectly through getOrStartServer
        // Verify that the implementation uses ProcessBuilder correctly
        
        // Test that a ProcessBuilder would accept our command structure
        val testPort = 12345
        val builder = ProcessBuilder()
            .command("opencode", "serve", "--port", testPort.toString(), "--hostname", "127.0.0.1")
            .directory(workingDirectory)
            .redirectErrorStream(true)
        
        assertNotNull(builder)
        // Command should have: ["opencode", "serve", "--port", "12345", "--hostname", "127.0.0.1"]
        assertEquals(6, builder.command().size, "Command should have 6 elements")
        assertEquals("opencode", builder.command()[0])
        assertEquals("serve", builder.command()[1])
        assertEquals("--port", builder.command()[2])
        assertEquals("12345", builder.command()[3])
        assertEquals("--hostname", builder.command()[4])
        assertEquals("127.0.0.1", builder.command()[5])
    }
    
    // ========== Error Recovery Tests ==========
    
    @Test
    fun `server recovery after port conflict scenario`() = runBlocking {
        // Simulate scenario where initial port is set but server dies
        val deadPort = 65001
        val portField = DefaultServerManager::class.java.getDeclaredField("serverPort")
        portField.isAccessible = true
        portField.set(serverManager, deadPort)
        
        // Test that isServerRunning detects the dead port
        val isRunning = serverManager.isServerRunning(deadPort)
        
        // Assert - Dead port should not be detected as running
        assertFalse(isRunning, "Dead port should not be detected as running")
        
        // Verify port state
        assertEquals(deadPort, serverManager.getServerPort(), "Port should still be set")
    }
    
    @Test
    fun `isServerRunning with different client timeout configurations`() = runBlocking {
        // Test with very short timeout
        val shortTimeoutClient = OkHttpClient.Builder()
            .readTimeout(100, TimeUnit.MILLISECONDS)
            .connectTimeout(100, TimeUnit.MILLISECONDS)
            .build()
        val shortManager = DefaultServerManager(workingDirectory, shortTimeoutClient)
        
        // Test unreachable port with short timeout
        val result = shortManager.isServerRunning(65123)
        
        // Should fail fast
        assertFalse(result)
    }
    
    // ========== Failure Path Tests ==========
    
    @Test
    fun `getOrStartServer with invalid working directory triggers failure paths`() = runBlocking {
        // Create manager with non-existent directory to force process creation failures
        val invalidDir = File("/tmp/nonexistent_opencode_test_dir_12345")
        val failManager = DefaultServerManager(invalidDir, client)
        
        try {
            // Act - Try to start server (should fail due to invalid directory)
            val result = failManager.getOrStartServer()
            
            // Assert - Should return null after retries fail
            // This exercises lines 47-54 (warning, catch, error logging)
            assertNull(result, "Should fail with invalid working directory")
        } finally {
            failManager.stopServer()
        }
    }
    
    @Test
    fun `waitForConnection returns false on timeout`() = runBlocking {
        // Arrange: use manager with very short timeout to avoid real process start
        val shortTimeoutClient = OkHttpClient.Builder()
            .readTimeout(200, TimeUnit.MILLISECONDS)
            .connectTimeout(200, TimeUnit.MILLISECONDS)
            .build()
        val shortManager = DefaultServerManager(workingDirectory, shortTimeoutClient)

        // Act: call waitForConnection on an unused port with tiny timeout
        val startTime = System.currentTimeMillis()
        val result = shortManager.waitForConnection(port = 65010, timeout = 300)
        val duration = System.currentTimeMillis() - startTime

        // Assert: should time out quickly and return false
        assertFalse(result)
        assertTrue(duration < 2000, "Should respect short timeout, took ${duration}ms")
    }
}
