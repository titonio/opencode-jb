package com.opencode.model

import com.google.gson.Gson
import com.opencode.test.TestDataFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for SessionModels data classes and serialization.
 */
class SessionModelsTest {
    
    private val gson = Gson()
    
    // Serialization/Deserialization Tests
    
    @Test
    fun `SessionInfo serializes correctly`() {
        val session = TestDataFactory.createSessionInfo(
            id = "test-123",
            title = "Test Session"
        )
        
        val json = gson.toJson(session)
        
        assertTrue(json.contains("\"id\":\"test-123\""))
        assertTrue(json.contains("\"title\":\"Test Session\""))
    }
    
    @Test
    fun `SessionInfo deserializes correctly`() {
        val json = """
            {
                "id": "session-1",
                "title": "Test Session",
                "directory": "/test/path",
                "projectID": "project-1",
                "time": {
                    "created": 1703001600000,
                    "updated": 1703088000000,
                    "archived": null
                },
                "share": null
            }
        """.trimIndent()
        
        val session = gson.fromJson(json, SessionInfo::class.java)
        
        assertEquals("session-1", session.id)
        assertEquals("Test Session", session.title)
        assertEquals("/test/path", session.directory)
        assertEquals("project-1", session.projectID)
        assertEquals(1703001600000L, session.time.created)
        assertEquals(1703088000000L, session.time.updated)
        assertNull(session.time.archived)
        assertNull(session.share)
    }
    
    @Test
    fun `SessionInfo with share info deserializes correctly`() {
        val json = """
            {
                "id": "session-shared",
                "title": "Shared Session",
                "directory": "/test",
                "projectID": "project-1",
                "time": {
                    "created": 1703001600000,
                    "updated": 1703088000000
                },
                "share": {
                    "url": "https://opencode.ai/share/abc123"
                }
            }
        """.trimIndent()
        
        val session = gson.fromJson(json, SessionInfo::class.java)
        
        assertNotNull(session.share)
        assertEquals("https://opencode.ai/share/abc123", session.share?.url)
    }
    
    @Test
    fun `TimeInfo serializes correctly`() {
        val timeInfo = TimeInfo(
            created = 1703001600000L,
            updated = 1703088000000L,
            archived = null
        )
        
        val json = gson.toJson(timeInfo)
        
        assertTrue(json.contains("\"created\":1703001600000"))
        assertTrue(json.contains("\"updated\":1703088000000"))
    }
    
    @Test
    fun `TimeInfo with archived timestamp serializes correctly`() {
        val timeInfo = TimeInfo(
            created = 1703001600000L,
            updated = 1703088000000L,
            archived = 1703089000000L
        )
        
        val json = gson.toJson(timeInfo)
        
        assertTrue(json.contains("\"archived\":1703089000000"))
    }
    
    @Test
    fun `ShareInfo serializes correctly`() {
        val shareInfo = ShareInfo(url = "https://opencode.ai/share/token")
        
        val json = gson.toJson(shareInfo)
        
        assertTrue(json.contains("\"url\":\"https://opencode.ai/share/token\""))
    }
    
    @Test
    fun `CreateSessionRequest serializes correctly`() {
        val request = CreateSessionRequest(title = "New Session")
        
        val json = gson.toJson(request)
        
        assertTrue(json.contains("\"title\":\"New Session\""))
    }
    
    @Test
    fun `SessionResponse deserializes correctly`() {
        val json = """
            {
                "id": "new-session-123",
                "title": "New Session",
                "directory": "/test/path"
            }
        """.trimIndent()
        
        val response = gson.fromJson(json, SessionResponse::class.java)
        
        assertEquals("new-session-123", response.id)
        assertEquals("New Session", response.title)
        assertEquals("/test/path", response.directory)
    }
    
    // Property Tests
    
    @Test
    fun `isShared returns true when share info is present`() {
        val session = TestDataFactory.createSharedSession(
            shareUrl = "https://opencode.ai/share/test"
        )
        
        assertTrue(session.isShared)
    }
    
    @Test
    fun `isShared returns false when share info is null`() {
        val session = TestDataFactory.createSessionInfo()
        
        assertFalse(session.isShared)
    }
    
    @Test
    fun `shareUrl returns correct URL when shared`() {
        val expectedUrl = "https://opencode.ai/share/test-token"
        val session = TestDataFactory.createSharedSession(
            shareUrl = expectedUrl
        )
        
        assertEquals(expectedUrl, session.shareUrl)
    }
    
    @Test
    fun `shareUrl returns null when not shared`() {
        val session = TestDataFactory.createSessionInfo()
        
        assertNull(session.shareUrl)
    }
    
    // Edge Cases
    
    @Test
    fun `SessionInfo handles empty strings correctly`() {
        val session = TestDataFactory.createSessionInfo(
            id = "",
            title = "",
            directory = "",
            projectID = ""
        )
        
        assertEquals("", session.id)
        assertEquals("", session.title)
        assertEquals("", session.directory)
        assertEquals("", session.projectID)
    }
    
    @Test
    fun `SessionInfo with all optional fields null deserializes correctly`() {
        val json = """
            {
                "id": "minimal-session",
                "title": "Minimal",
                "directory": "/test",
                "projectID": "project-1",
                "time": {
                    "created": 1703001600000,
                    "updated": 1703088000000
                }
            }
        """.trimIndent()
        
        val session = gson.fromJson(json, SessionInfo::class.java)
        
        assertEquals("minimal-session", session.id)
        assertNull(session.time.archived)
        assertNull(session.share)
        assertFalse(session.isShared)
        assertNull(session.shareUrl)
    }
}
