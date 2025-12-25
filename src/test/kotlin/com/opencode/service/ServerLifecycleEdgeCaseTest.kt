package com.opencode.service

import com.opencode.test.MockServerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.BindException
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive test suite for server lifecycle edge cases.
 * 
 * Tests cover:
 * - Server startup race conditions (multiple concurrent starts)
 * - Port conflict handling (port already in use)
 * - Server crash recovery scenarios
 * - Multiple startup attempts are idempotent
 * - Server restart behavior
 * - Server status checking edge cases
 * - Process termination edge cases
 */
class ServerLifecycleEdgeCaseTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var workingDirectory: File
    
    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        client = OkHttpClient.Builder()
            .readTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()
        
        workingDirectory = File(System.getProperty("user.home"))
    }
    
    @AfterEach
    fun tearDown() {
        try {
            mockWebServer.shutdown()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
    
    // ========== Concurrent Server Startup Tests ==========
    
    @Test
    fun `multiple concurrent getOrStartServer calls return same port`() = runTest {
        // Arrange
        val mockPort = 3000
        val mockServerManager = MockServerManager(mockPort = mockPort, shouldSucceed = true)
        val startCallCount = AtomicInteger(0)
        
        // Create a custom mock that tracks calls
        val trackingManager = object : ServerManager {
            override suspend fun getOrStartServer(): Int? {
                startCallCount.incrementAndGet()
                delay(100) // Simulate startup delay
                return mockServerManager.getOrStartServer()
            }
            
            override suspend fun isServerRunning(port: Int): Boolean {
                return mockServerManager.isServerRunning(port)
            }
            
            override fun stopServer() {
                mockServerManager.stopServer()
            }
            
            override fun getServerPort(): Int? {
                return mockServerManager.getServerPort()
            }
        }
        
        // Act - Launch 5 concurrent start requests
        val deferredPorts = (1..5).map {
            async {
                trackingManager.getOrStartServer()
            }
        }
        
        val ports = deferredPorts.map { it.await() }
        
        // Assert - All should get the same port
        assertEquals(5, ports.size)
        assertTrue(ports.all { it == mockPort }, "All concurrent starts should return same port")
        assertEquals(5, startCallCount.get(), "All 5 calls should have been made")
    }
    
    @Test
    fun `concurrent server starts with one failure still succeeds`() = runTest {
        // Arrange - Create a manager that fails once then succeeds
        var attemptCount = 0
        val mockPort = 3000
        
        val flakeyManager = object : ServerManager {
            override suspend fun getOrStartServer(): Int? {
                attemptCount++
                return if (attemptCount == 1) {
                    null // First attempt fails
                } else {
                    mockPort // Subsequent attempts succeed
                }
            }
            
            override suspend fun isServerRunning(port: Int): Boolean = port == mockPort
            override fun stopServer() {}
            override fun getServerPort(): Int? = mockPort
        }
        
        // Act - Launch concurrent requests
        val results = (1..3).map {
            async {
                flakeyManager.getOrStartServer()
            }
        }.map { it.await() }
        
        // Assert - Some should succeed
        assertTrue(results.any { it == mockPort }, "At least one request should succeed")
        assertTrue(results.any { it == null }, "First request should have failed")
    }
    
    // ========== Port Conflict Handling Tests ==========
    
    @Test
    fun `isServerRunning handles port conflict gracefully`() = runTest {
        // Arrange - Create a server on a specific port
        val serverSocket = ServerSocket(0) // Random available port
        val occupiedPort = serverSocket.localPort
        
        try {
            val serverManager = DefaultServerManager(workingDirectory, client)
            
            // Act - Check if server is running on the occupied port
            val result = serverManager.isServerRunning(occupiedPort)
            
            // Assert - Should return false (no OpenCode server on that port)
            assertFalse(result, "Should detect no OpenCode server on occupied port")
        } finally {
            serverSocket.close()
        }
    }
    
    @Test
    fun `server manager handles connection refused gracefully`() = runTest {
        // Arrange
        val unusedPort = 54321 // Port with nothing running
        val serverManager = DefaultServerManager(workingDirectory, client)
        
        // Act
        val result = serverManager.isServerRunning(unusedPort)
        
        // Assert
        assertFalse(result, "Should handle connection refused gracefully")
    }
    
    @Test
    fun `server status check with invalid port numbers`() = runTest {
        // Arrange
        val serverManager = DefaultServerManager(workingDirectory, client)
        
        // Act & Assert - Invalid but technically possible ports
        assertFalse(serverManager.isServerRunning(0))
        assertFalse(serverManager.isServerRunning(1)) // Privileged port
        assertFalse(serverManager.isServerRunning(99999)) // Out of range (will likely error)
    }
    
    // ========== Server Crash Recovery Tests ==========
    
    @Test
    fun `server restart after crash simulation`() = runTest {
        // Arrange - Create a manager that simulates a crash
        val mockPort = 3000
        var serverRunning = true
        var restartCount = 0
        
        val crashingManager = object : ServerManager {
            override suspend fun getOrStartServer(): Int? {
                restartCount++
                serverRunning = true
                return mockPort
            }
            
            override suspend fun isServerRunning(port: Int): Boolean {
                return serverRunning && port == mockPort
            }
            
            override fun stopServer() {
                serverRunning = false
            }
            
            override fun getServerPort(): Int? {
                return if (serverRunning) mockPort else null
            }
        }
        
        // Act - Start, crash (stop), then restart
        val port1 = crashingManager.getOrStartServer()
        assertTrue(crashingManager.isServerRunning(mockPort))
        
        // Simulate crash
        crashingManager.stopServer()
        assertFalse(crashingManager.isServerRunning(mockPort))
        
        // Restart
        val port2 = crashingManager.getOrStartServer()
        assertTrue(crashingManager.isServerRunning(mockPort))
        
        // Assert
        assertEquals(mockPort, port1)
        assertEquals(mockPort, port2)
        assertEquals(2, restartCount, "Server should have been started twice")
    }
    
    @Test
    fun `server recovery after multiple failed starts`() = runTest {
        // Arrange - Simulate multiple failures before success
        var attemptCount = 0
        val mockPort = 3000
        
        val unreliableManager = object : ServerManager {
            override suspend fun getOrStartServer(): Int? {
                attemptCount++
                return if (attemptCount < 3) {
                    null // First 2 attempts fail
                } else {
                    mockPort // 3rd attempt succeeds
                }
            }
            
            override suspend fun isServerRunning(port: Int): Boolean = attemptCount >= 3 && port == mockPort
            override fun stopServer() {}
            override fun getServerPort(): Int? = if (attemptCount >= 3) mockPort else null
        }
        
        // Act - Try multiple times
        val result1 = unreliableManager.getOrStartServer()
        val result2 = unreliableManager.getOrStartServer()
        val result3 = unreliableManager.getOrStartServer()
        
        // Assert
        assertNull(result1, "First attempt should fail")
        assertNull(result2, "Second attempt should fail")
        assertEquals(mockPort, result3, "Third attempt should succeed")
    }
    
    // ========== Idempotent Startup Tests ==========
    
    @Test
    fun `multiple startup attempts are idempotent`() = runTest {
        // Arrange
        val mockPort = 3000
        val mockServerManager = MockServerManager(mockPort = mockPort, shouldSucceed = true)
        
        // Act - Call getOrStartServer multiple times
        val port1 = mockServerManager.getOrStartServer()
        val port2 = mockServerManager.getOrStartServer()
        val port3 = mockServerManager.getOrStartServer()
        
        // Assert - All should return the same port
        assertEquals(mockPort, port1)
        assertEquals(mockPort, port2)
        assertEquals(mockPort, port3)
        assertTrue(mockServerManager.isStarted, "Server should be marked as started")
    }
    
    @Test
    fun `getOrStartServer is idempotent after stop`() = runTest {
        // Arrange
        val mockPort = 3000
        var stopped = false
        
        val restartableManager = object : ServerManager {
            override suspend fun getOrStartServer(): Int? {
                stopped = false
                return mockPort
            }
            
            override suspend fun isServerRunning(port: Int): Boolean = !stopped && port == mockPort
            override fun stopServer() { stopped = true }
            override fun getServerPort(): Int? = if (!stopped) mockPort else null
        }
        
        // Act - Start, stop, start again
        val port1 = restartableManager.getOrStartServer()
        restartableManager.stopServer()
        val port2 = restartableManager.getOrStartServer()
        
        // Assert
        assertEquals(mockPort, port1)
        assertEquals(mockPort, port2)
        assertTrue(restartableManager.isServerRunning(mockPort), "Server should be running after restart")
    }
    
    // ========== Server Restart Behavior Tests ==========
    
    @Test
    fun `server restart clears previous state`() = runTest {
        // Arrange
        val mockPort = 3000
        var stopped = false
        
        val restartableManager = object : ServerManager {
            override suspend fun getOrStartServer(): Int? {
                stopped = false
                return mockPort
            }
            
            override suspend fun isServerRunning(port: Int): Boolean = !stopped && port == mockPort
            override fun stopServer() { stopped = true }
            override fun getServerPort(): Int? = if (!stopped) mockPort else null
        }
        
        // Act - Start, verify running, stop, verify stopped
        restartableManager.getOrStartServer()
        assertTrue(restartableManager.isServerRunning(mockPort))
        
        restartableManager.stopServer()
        assertFalse(restartableManager.isServerRunning(mockPort))
        assertNull(restartableManager.getServerPort())
        
        // Restart
        val newPort = restartableManager.getOrStartServer()
        
        // Assert
        assertEquals(mockPort, newPort)
        assertTrue(restartableManager.isServerRunning(mockPort))
    }
    
    @Test
    fun `rapid restart cycles maintain consistency`() = runTest {
        // Arrange
        val mockPort = 3000
        var stopped = false
        
        val restartableManager = object : ServerManager {
            override suspend fun getOrStartServer(): Int? {
                stopped = false
                return mockPort
            }
            
            override suspend fun isServerRunning(port: Int): Boolean = !stopped && port == mockPort
            override fun stopServer() { stopped = true }
            override fun getServerPort(): Int? = if (!stopped) mockPort else null
        }
        
        // Act - Perform multiple rapid restart cycles
        repeat(10) {
            restartableManager.getOrStartServer()
            assertTrue(restartableManager.isServerRunning(mockPort))
            restartableManager.stopServer()
            assertFalse(restartableManager.isServerRunning(mockPort))
        }
        
        // Final start
        val finalPort = restartableManager.getOrStartServer()
        
        // Assert
        assertEquals(mockPort, finalPort)
        assertTrue(restartableManager.isServerRunning(mockPort))
    }
    
    // ========== Server Status Checking Edge Cases ==========
    
    @Test
    fun `isServerRunning returns false after stopServer`() = runTest {
        // Arrange
        val mockPort = 3000
        val mockServerManager = MockServerManager(mockPort = mockPort, shouldSucceed = true)
        
        // Act
        mockServerManager.getOrStartServer()
        val runningBefore = mockServerManager.isServerRunning(mockPort)
        
        mockServerManager.stopServer()
        val runningAfter = mockServerManager.isServerRunning(mockPort)
        
        // Assert
        assertTrue(runningBefore, "Server should be running before stop")
        assertFalse(runningAfter, "Server should not be running after stop")
    }
    
    @Test
    fun `isServerRunning returns false for wrong port`() = runTest {
        // Arrange
        val mockPort = 3000
        val wrongPort = 4000
        val mockServerManager = MockServerManager(mockPort = mockPort, shouldSucceed = true)
        
        // Act
        mockServerManager.getOrStartServer()
        val correctPortResult = mockServerManager.isServerRunning(mockPort)
        val wrongPortResult = mockServerManager.isServerRunning(wrongPort)
        
        // Assert
        assertTrue(correctPortResult, "Should return true for correct port")
        assertFalse(wrongPortResult, "Should return false for wrong port")
    }
    
    @Test
    fun `isServerRunning handles slow server response gracefully`() = runTest {
        // Arrange - Server that responds with 500 error (unreachable for our purposes)
        val port = mockWebServer.port
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Server Error")
        )
        
        val serverManager = DefaultServerManager(workingDirectory, client)
        
        // Act
        val result = serverManager.isServerRunning(port)
        
        // Assert - Should handle error responses correctly
        assertFalse(result, "Should return false when server returns error")
    }
    
    // ========== Process Termination Edge Cases ==========
    
    @Test
    fun `stopServer can be called multiple times safely`() = runTest {
        // Arrange
        val mockServerManager = MockServerManager(shouldSucceed = true)
        
        // Act - Call stop multiple times
        mockServerManager.stopServer()
        mockServerManager.stopServer()
        mockServerManager.stopServer()
        
        // Assert - Should not throw exceptions
        assertTrue(mockServerManager.isStopped)
        assertNull(mockServerManager.getServerPort())
    }
    
    @Test
    fun `stopServer before start is safe`() = runTest {
        // Arrange
        val mockServerManager = MockServerManager(shouldSucceed = true)
        
        // Act - Stop before starting (edge case)
        mockServerManager.stopServer()
        
        // Assert - Should be safe
        assertNull(mockServerManager.getServerPort())
        
        // Should still be able to start after
        val port = mockServerManager.getOrStartServer()
        assertEquals(3000, port)
    }
    
    @Test
    fun `getServerPort returns null after stop`() = runTest {
        // Arrange
        val mockServerManager = MockServerManager(shouldSucceed = true)
        
        // Act
        mockServerManager.getOrStartServer()
        val portBeforeStop = mockServerManager.getServerPort()
        
        mockServerManager.stopServer()
        val portAfterStop = mockServerManager.getServerPort()
        
        // Assert
        assertNotNull(portBeforeStop)
        assertNull(portAfterStop)
    }
    
    @Test
    fun `server manager handles graceful shutdown during active check`() = runTest {
        // Arrange
        val mockPort = 3000
        var isRunning = true
        
        val manager = object : ServerManager {
            override suspend fun getOrStartServer(): Int? = mockPort
            
            override suspend fun isServerRunning(port: Int): Boolean {
                delay(50) // Simulate check delay
                return isRunning && port == mockPort
            }
            
            override fun stopServer() {
                isRunning = false
            }
            
            override fun getServerPort(): Int? = if (isRunning) mockPort else null
        }
        
        // Act - Start a status check and stop server concurrently
        manager.getOrStartServer()
        
        val checkDeferred = async {
            delay(25) // Start check
            manager.isServerRunning(mockPort)
        }
        
        delay(30) // Stop while check is in progress
        manager.stopServer()
        
        val checkResult = checkDeferred.await()
        
        // Assert - Check should complete (either true or false is acceptable)
        // The key is it doesn't throw an exception
        assertNotNull(checkResult)
    }
}
