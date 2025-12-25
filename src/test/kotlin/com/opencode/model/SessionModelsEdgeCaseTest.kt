package com.opencode.model

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.opencode.test.TestDataFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

/**
 * Edge case tests for SessionModels data classes focusing on:
 * - JSON serialization edge cases
 * - Data validation scenarios
 * - Data class behavior verification
 */
class SessionModelsEdgeCaseTest {
    
    private val gson = Gson()
    
    // ==================== JSON Serialization Edge Cases ====================
    
    @Test
    fun `malformed JSON throws JsonSyntaxException`() {
        val malformedJson = """
            {
                "id": "test-id",
                "title": "Test"
                "directory": "/path"
                missing comma above
            }
        """.trimIndent()
        
        assertThrows<JsonSyntaxException> {
            gson.fromJson(malformedJson, SessionInfo::class.java)
        }
    }
    
    @Test
    fun `incomplete JSON with missing required fields creates object with nulls`() {
        val incompleteJson = """
            {
                "id": "test-id"
            }
        """.trimIndent()
        
        // Gson allows missing fields and assigns null for non-nullable types (which causes runtime issues)
        // This test documents the actual behavior
        val session = gson.fromJson(incompleteJson, SessionInfo::class.java)
        
        assertEquals("test-id", session.id)
        // Other fields will be null despite being non-nullable in Kotlin
    }
    
    @Test
    fun `JSON with explicit null for required field deserializes with null`() {
        val jsonWithNull = """
            {
                "id": null,
                "title": "Test",
                "directory": "/path",
                "projectID": "project-1",
                "time": {
                    "created": 1703001600000,
                    "updated": 1703088000000
                }
            }
        """.trimIndent()
        
        // Gson allows null for non-nullable types
        val session = gson.fromJson(jsonWithNull, SessionInfo::class.java)
        
        assertNull(session.id)
    }
    
    @Test
    fun `JSON with type mismatch for string field deserializes incorrectly`() {
        val jsonWithTypeMismatch = """
            {
                "id": 12345,
                "title": "Test",
                "directory": "/path",
                "projectID": "project-1",
                "time": {
                    "created": 1703001600000,
                    "updated": 1703088000000
                }
            }
        """.trimIndent()
        
        // Gson will convert number to string
        val session = gson.fromJson(jsonWithTypeMismatch, SessionInfo::class.java)
        
        assertEquals("12345", session.id)
    }
    
    @Test
    fun `JSON with type mismatch for long field throws exception`() {
        val jsonWithInvalidTimestamp = """
            {
                "id": "test-id",
                "title": "Test",
                "directory": "/path",
                "projectID": "project-1",
                "time": {
                    "created": "not-a-number",
                    "updated": 1703088000000
                }
            }
        """.trimIndent()
        
        assertThrows<JsonSyntaxException> {
            gson.fromJson(jsonWithInvalidTimestamp, SessionInfo::class.java)
        }
    }
    
    @Test
    fun `JSON with extra unknown fields deserializes successfully ignoring extras`() {
        val jsonWithExtras = """
            {
                "id": "test-id",
                "title": "Test Session",
                "directory": "/path",
                "projectID": "project-1",
                "time": {
                    "created": 1703001600000,
                    "updated": 1703088000000,
                    "extraTimeField": "ignored"
                },
                "unknownField": "should be ignored",
                "anotherExtra": 123
            }
        """.trimIndent()
        
        val session = gson.fromJson(jsonWithExtras, SessionInfo::class.java)
        
        assertEquals("test-id", session.id)
        assertEquals("Test Session", session.title)
        assertEquals("/path", session.directory)
        assertEquals("project-1", session.projectID)
    }
    
    @Test
    fun `empty string vs null for optional ShareInfo URL`() {
        val jsonWithEmptyUrl = """
            {
                "id": "test-id",
                "title": "Test",
                "directory": "/path",
                "projectID": "project-1",
                "time": {
                    "created": 1703001600000,
                    "updated": 1703088000000
                },
                "share": {
                    "url": ""
                }
            }
        """.trimIndent()
        
        val session = gson.fromJson(jsonWithEmptyUrl, SessionInfo::class.java)
        
        assertNotNull(session.share)
        assertEquals("", session.share?.url)
        assertTrue(session.isShared) // Has share object even with empty URL
        assertEquals("", session.shareUrl)
    }
    
    // ==================== TimeInfo Edge Cases ====================
    
    @Test
    fun `TimeInfo with negative timestamp values deserializes successfully`() {
        val timeInfo = TimeInfo(
            created = -1000L,
            updated = -500L,
            archived = -100L
        )
        
        assertEquals(-1000L, timeInfo.created)
        assertEquals(-500L, timeInfo.updated)
        assertEquals(-100L, timeInfo.archived)
    }
    
    @Test
    fun `TimeInfo with zero timestamp values`() {
        val timeInfo = TimeInfo(
            created = 0L,
            updated = 0L,
            archived = 0L
        )
        
        assertEquals(0L, timeInfo.created)
        assertEquals(0L, timeInfo.updated)
        assertEquals(0L, timeInfo.archived)
    }
    
    @Test
    fun `TimeInfo with very large timestamp values`() {
        val timeInfo = TimeInfo(
            created = Long.MAX_VALUE,
            updated = Long.MAX_VALUE - 1,
            archived = Long.MAX_VALUE - 2
        )
        
        assertEquals(Long.MAX_VALUE, timeInfo.created)
        assertEquals(Long.MAX_VALUE - 1, timeInfo.updated)
        assertEquals(Long.MAX_VALUE - 2, timeInfo.archived)
    }
    
    @Test
    fun `TimeInfo with updated before created is allowed`() {
        // No validation prevents logical inconsistencies
        val timeInfo = TimeInfo(
            created = 2000L,
            updated = 1000L, // Updated before created
            archived = 500L  // Archived before both
        )
        
        assertEquals(2000L, timeInfo.created)
        assertEquals(1000L, timeInfo.updated)
        assertEquals(500L, timeInfo.archived)
    }
    
    // ==================== ShareInfo Validation ====================
    
    @Test
    fun `ShareInfo with malformed URL is accepted`() {
        val shareInfo = ShareInfo(url = "not-a-valid-url")
        
        assertEquals("not-a-valid-url", shareInfo.url)
    }
    
    @Test
    fun `ShareInfo with empty URL string`() {
        val shareInfo = ShareInfo(url = "")
        
        assertEquals("", shareInfo.url)
    }
    
    @Test
    fun `ShareInfo with very long URL`() {
        val longUrl = "https://example.com/" + "a".repeat(10000)
        val shareInfo = ShareInfo(url = longUrl)
        
        assertEquals(longUrl, shareInfo.url)
        assertTrue(shareInfo.url.length > 10000)
    }
    
    @Test
    fun `ShareInfo with special characters in URL`() {
        val specialUrl = "https://example.com/share?token=abc@123&user=test#fragment"
        val shareInfo = ShareInfo(url = specialUrl)
        
        assertEquals(specialUrl, shareInfo.url)
    }
    
    // ==================== Data Class Behavior ====================
    
    @Test
    fun `SessionInfo equality compares all fields correctly`() {
        val session1 = TestDataFactory.createSessionInfo(
            id = "test-1", 
            title = "Session",
            created = 1000L,
            updated = 2000L
        )
        val session2 = TestDataFactory.createSessionInfo(
            id = "test-1", 
            title = "Session",
            created = 1000L,
            updated = 2000L
        )
        
        assertEquals(session1, session2)
    }
    
    @Test
    fun `SessionInfo inequality when any field differs`() {
        val session1 = TestDataFactory.createSessionInfo(id = "test-1", title = "Session")
        val session2 = TestDataFactory.createSessionInfo(id = "test-2", title = "Session")
        
        assertNotEquals(session1, session2)
    }
    
    @Test
    fun `SessionInfo hashCode is consistent for equal objects`() {
        val session1 = TestDataFactory.createSessionInfo(
            id = "test-1", 
            title = "Session",
            created = 1000L,
            updated = 2000L
        )
        val session2 = TestDataFactory.createSessionInfo(
            id = "test-1", 
            title = "Session",
            created = 1000L,
            updated = 2000L
        )
        
        assertEquals(session1.hashCode(), session2.hashCode())
    }
    
    @Test
    fun `SessionInfo copy with modifications works correctly`() {
        val original = TestDataFactory.createSessionInfo(
            id = "original-id",
            title = "Original Title"
        )
        
        val modified = original.copy(title = "Modified Title")
        
        assertEquals("original-id", modified.id)
        assertEquals("Modified Title", modified.title)
        assertEquals("Original Title", original.title) // Original unchanged
    }
    
    @Test
    fun `TimeInfo copy preserves null archived value`() {
        val original = TimeInfo(
            created = 1000L,
            updated = 2000L,
            archived = null
        )
        
        val modified = original.copy(updated = 3000L)
        
        assertEquals(1000L, modified.created)
        assertEquals(3000L, modified.updated)
        assertNull(modified.archived)
    }
    
    @Test
    fun `SessionInfo toString contains field values`() {
        val session = TestDataFactory.createSessionInfo(
            id = "test-123",
            title = "Test Session"
        )
        
        val stringRepresentation = session.toString()
        
        assertTrue(stringRepresentation.contains("test-123"))
        assertTrue(stringRepresentation.contains("Test Session"))
    }
    
    @Test
    fun `CreateSessionRequest with special characters in title`() {
        val specialTitle = "Test & \"Session\" <with> 'special' chars\n\ttab"
        val request = CreateSessionRequest(title = specialTitle)
        
        assertEquals(specialTitle, request.title)
        
        val json = gson.toJson(request)
        val deserialized = gson.fromJson(json, CreateSessionRequest::class.java)
        
        assertEquals(specialTitle, deserialized.title)
    }
    
    @Test
    fun `SessionResponse handles Unicode characters correctly`() {
        val json = """
            {
                "id": "session-unicode",
                "title": "测试会话 🚀 тест",
                "directory": "/path/with/émojis/🎉"
            }
        """.trimIndent()
        
        val response = gson.fromJson(json, SessionResponse::class.java)
        
        assertEquals("测试会话 🚀 тест", response.title)
        assertEquals("/path/with/émojis/🎉", response.directory)
    }
    
    // ==================== Complex Scenario Tests ====================
    
    @Test
    fun `SessionInfo with all edge case values combined`() {
        val json = """
            {
                "id": "",
                "title": "Test & \"quoted\" <tags>",
                "directory": "/very/long/${"path/".repeat(100)}end",
                "projectID": "🚀-project",
                "time": {
                    "created": 0,
                    "updated": ${Long.MAX_VALUE},
                    "archived": null
                },
                "share": {
                    "url": ""
                },
                "ignoredField": "this should not cause errors"
            }
        """.trimIndent()
        
        val session = gson.fromJson(json, SessionInfo::class.java)
        
        assertEquals("", session.id)
        assertTrue(session.title.contains("Test"))
        assertTrue(session.directory.length > 500)
        assertEquals(0L, session.time.created)
        assertEquals(Long.MAX_VALUE, session.time.updated)
        assertNull(session.time.archived)
        assertNotNull(session.share)
        assertEquals("", session.shareUrl)
        assertTrue(session.isShared)
    }
}
