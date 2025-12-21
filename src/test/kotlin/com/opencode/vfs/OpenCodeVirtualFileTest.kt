package com.opencode.vfs

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Comprehensive tests for OpenCodeVirtualFile.
 * Tests virtual file properties, identity, and behavior as specified in TESTING_PLAN.md Phase 3.
 * 
 * The OpenCodeVirtualFile represents a virtual session file with:
 * - Stable identity via path (opencode://session/<sessionId>)
 * - Read-only access
 * - Proper equality and hashCode for state restoration
 */
class OpenCodeVirtualFileTest {
    
    private lateinit var mockFileSystem: OpenCodeFileSystem
    private lateinit var virtualFile: OpenCodeVirtualFile
    private val testSessionId = "abc123def456ghi789jkl012"
    
    @BeforeEach
    fun setUp() {
        // Create a mock file system
        mockFileSystem = mock()
        whenever(mockFileSystem.protocol).thenReturn("opencode")
        
        // Create virtual file with test session ID
        virtualFile = OpenCodeVirtualFile(mockFileSystem, testSessionId)
    }
    
    // ========== Constructor and Basic Properties ==========
    
    @Test
    fun `test constructor initializes with sessionId and fileSystem`() {
        // Verify the virtual file was created with correct properties
        assertNotNull(virtualFile)
        assertEquals(testSessionId, virtualFile.sessionId)
        assertEquals(mockFileSystem, virtualFile.fileSystem)
    }
    
    // ========== Path Tests ==========
    
    @Test
    fun `test getPath returns correct URL format`() {
        // Path should be: opencode://session/<sessionId>
        val expectedPath = "opencode://session/$testSessionId"
        assertEquals(expectedPath, virtualFile.path)
    }
    
    // ========== Name Tests ==========
    
    @Test
    fun `test getName shortens sessionId to first 12 characters`() {
        // Name format: OpenCode-<first 12 chars of sessionId>
        val expectedName = "OpenCode-abc123def456"
        assertEquals(expectedName, virtualFile.name)
    }
    
    @Test
    fun `test getPresentableName matches name`() {
        // Presentable name should match name
        assertEquals(virtualFile.name, virtualFile.presentableName)
        assertEquals("OpenCode-abc123def456", virtualFile.presentableName)
    }
    
    // ========== FileSystem Tests ==========
    
    @Test
    fun `test getFileSystem returns correct instance`() {
        // Should return the same file system instance passed to constructor
        val fileSystem = virtualFile.fileSystem
        assertNotNull(fileSystem)
        assertEquals(mockFileSystem, fileSystem)
    }
    
    // ========== Property Tests ==========
    
    @Test
    fun `test isValid always returns true`() {
        // Virtual files are always valid
        assertTrue(virtualFile.isValid)
    }
    
    @Test
    fun `test isDirectory always returns false`() {
        // OpenCode files are not directories
        assertFalse(virtualFile.isDirectory)
    }
    
    @Test
    fun `test isWritable always returns false`() {
        // OpenCode files are read-only
        assertFalse(virtualFile.isWritable)
    }
    
    // ========== Hierarchy Tests ==========
    
    @Test
    fun `test getParent returns null`() {
        // Virtual files have no parent
        assertNull(virtualFile.parent)
    }
    
    @Test
    fun `test getChildren returns null`() {
        // Virtual files have no children
        assertNull(virtualFile.children)
    }
    
    // ========== Write Operations Tests ==========
    
    @Test
    fun `test getOutputStream throws UnsupportedOperationException`() {
        // Attempting to write should throw exception
        val exception = assertThrows(UnsupportedOperationException::class.java) {
            virtualFile.getOutputStream(null, 0L, 0L)
        }
        
        assertEquals("OpenCode files are read-only", exception.message)
    }
    
    // ========== Content Tests ==========
    
    @Test
    fun `test contentsToByteArray returns empty array`() {
        // Virtual files have no content
        val contents = virtualFile.contentsToByteArray()
        assertNotNull(contents)
        assertEquals(0, contents.size)
    }
    
    @Test
    fun `test getInputStream returns empty stream`() {
        // Virtual files provide empty input stream
        val inputStream = virtualFile.inputStream
        assertNotNull(inputStream)
        
        val contents = inputStream.readBytes()
        assertEquals(0, contents.size)
    }
    
    @Test
    fun `test getTimeStamp returns zero`() {
        // Virtual files have no timestamp
        assertEquals(0L, virtualFile.timeStamp)
    }
    
    @Test
    fun `test getLength returns zero`() {
        // Virtual files have no length
        assertEquals(0L, virtualFile.length)
    }
    
    // ========== Refresh Tests ==========
    
    @Test
    fun `test refresh executes post-runnable`() {
        // Create a mock runnable to track execution
        val mockRunnable = mock<Runnable>()
        
        // Call refresh with the runnable
        virtualFile.refresh(false, false, mockRunnable)
        
        // Verify the runnable was executed
        verify(mockRunnable).run()
    }
    
    @Test
    fun `test refresh with null runnable does not throw`() {
        // Calling refresh with null runnable should not throw
        assertDoesNotThrow {
            virtualFile.refresh(false, false, null)
        }
    }
    
    // ========== Equality Tests ==========
    
    @Test
    fun `test equals with same instance returns true`() {
        // Same instance should be equal
        assertTrue(virtualFile.equals(virtualFile))
        assertEquals(virtualFile, virtualFile)
    }
    
    @Test
    fun `test equals with same sessionId returns true`() {
        // Two files with same sessionId should be equal (same path)
        val otherFile = OpenCodeVirtualFile(mockFileSystem, testSessionId)
        
        assertTrue(virtualFile.equals(otherFile))
        assertEquals(virtualFile, otherFile)
    }
    
    @Test
    fun `test equals with different sessionId returns false`() {
        // Files with different sessionIds should not be equal
        val differentFile = OpenCodeVirtualFile(mockFileSystem, "different-session-id")
        
        assertFalse(virtualFile.equals(differentFile))
        assertNotEquals(virtualFile, differentFile)
    }
    
    @Test
    fun `test equals with null returns false`() {
        // Null should not be equal
        assertFalse(virtualFile.equals(null))
        assertNotEquals(virtualFile, null)
    }
    
    @Test
    fun `test equals with different type returns false`() {
        // Different object type should not be equal
        assertFalse(virtualFile.equals("not a virtual file"))
        assertNotEquals(virtualFile, "not a virtual file")
    }
    
    // ========== HashCode Tests ==========
    
    @Test
    fun `test hashCode is consistent with equals`() {
        // Files that are equal must have same hashCode
        val otherFile = OpenCodeVirtualFile(mockFileSystem, testSessionId)
        
        assertEquals(virtualFile, otherFile)
        assertEquals(virtualFile.hashCode(), otherFile.hashCode())
    }
    
    @Test
    fun `test hashCode is based on path`() {
        // HashCode should be based on path
        val expectedHashCode = virtualFile.path.hashCode()
        assertEquals(expectedHashCode, virtualFile.hashCode())
    }
    
    @Test
    fun `test hashCode differs for different sessionIds`() {
        // Files with different sessionIds should have different hashCodes (usually)
        val differentFile = OpenCodeVirtualFile(mockFileSystem, "different-session-id")
        
        // While not strictly required by contract, it's good practice
        assertNotEquals(virtualFile.hashCode(), differentFile.hashCode())
    }
    
    // ========== ToString Tests ==========
    
    @Test
    fun `test toString contains sessionId and path`() {
        // toString should provide useful debugging information
        val stringValue = virtualFile.toString()
        
        assertTrue(stringValue.contains(testSessionId))
        assertTrue(stringValue.contains("opencode://session/$testSessionId"))
        assertTrue(stringValue.contains("OpenCodeVirtualFile"))
    }
}
