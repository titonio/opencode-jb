package com.opencode.test

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Simple test to verify test infrastructure is working.
 */
class InfrastructureVerificationTest {
    
    @Test
    fun `test infrastructure is operational`() {
        assertTrue(true, "Basic assertion works")
    }
    
    @Test
    fun `test data factory creates session info`() {
        val session = TestDataFactory.createSessionInfo(
            id = "test-123",
            title = "Test Session"
        )
        
        assertEquals("test-123", session.id)
        assertEquals("Test Session", session.title)
        assertFalse(session.isShared)
    }
    
    @Test
    fun `mock web server can start and stop`() {
        val server = MockOpenCodeServer()
        server.start()
        
        assertTrue(server.port > 0, "Server should be assigned a port")
        assertNotNull(server.url, "Server should have a URL")
        
        server.shutdown()
    }
}
