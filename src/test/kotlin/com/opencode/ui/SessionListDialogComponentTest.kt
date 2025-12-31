package com.opencode.ui

import com.opencode.model.SessionInfo
import com.opencode.model.TimeInfo
import com.opencode.test.TestDataFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList

/**
 * Component-level tests for SessionListDialog components.
 *
 * These tests focus on testing individual UI components that can be tested
 * without requiring full IntelliJ platform infrastructure.
 *
 * Tests cover:
 * - SessionCellRenderer display logic
 * - HTML formatting
 * - Share icon display
 * - Date formatting
 * - ID truncation
 * - Edge cases (null values, special characters)
 *
 * Note: Full dialog integration tests require platform infrastructure which currently
 * has coroutines compatibility issues. These component tests provide focused coverage
 * of the renderable UI logic.
 */
class SessionListDialogComponentTest {

    // Access the private SessionCellRenderer through reflection
    // In production, this renderer is used by SessionListDialog
    private fun createCellRenderer(): DefaultListCellRenderer {
        // Get the private static inner class SessionCellRenderer
        val rendererClass = Class.forName("com.opencode.ui.SessionListDialog\$SessionCellRenderer")
        return rendererClass.getDeclaredConstructor().newInstance() as DefaultListCellRenderer
    }

    // ========== Cell Renderer Tests ==========

    @Test
    fun `test cell renderer displays session title`() {
        // Arrange
        val renderer = createCellRenderer()
        val session = TestDataFactory.createSessionInfo(
            id = "test-123",
            title = "My Important Session"
        )
        val list = JList<SessionInfo>()

        // Act
        val component = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            false,
            false
        )

        // Assert
        assertTrue(component is JLabel, "Renderer should return JLabel")
        val label = component as JLabel
        val text = label.text

        assertTrue(text.contains("My Important Session"), "Should contain session title: $text")
        assertTrue(text.contains("<html>"), "Should use HTML formatting")
        assertTrue(text.contains("<b>"), "Should bold the title")
    }

    @Test
    fun `test cell renderer shows share icon for shared sessions`() {
        // Arrange
        val renderer = createCellRenderer()
        val shareUrl = "https://opencode.ai/share/abc123"
        val session = TestDataFactory.createSharedSession(
            id = "shared-session",
            shareUrl = shareUrl
        )
        val list = JList<SessionInfo>()

        // Act
        val component = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            false,
            false
        ) as JLabel

        // Assert
        val text = component.text
        assertTrue(text.contains("🔗"), "Should contain share icon: $text")
        assertTrue(text.contains(session.title), "Should still contain title")
    }

    @Test
    fun `test cell renderer does not show share icon for unshared sessions`() {
        // Arrange
        val renderer = createCellRenderer()
        val session = TestDataFactory.createSessionInfo(
            id = "regular-session",
            title = "Regular Session"
        )
        val list = JList<SessionInfo>()

        // Act
        val component = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            false,
            false
        ) as JLabel

        // Assert
        val text = component.text
        assertFalse(text.contains("🔗"), "Should not contain share icon: $text")
        assertTrue(text.contains("Regular Session"), "Should contain title")
    }

    @Test
    fun `test cell renderer truncates long session ID`() {
        // Arrange
        val renderer = createCellRenderer()
        val longId = "very-long-session-id-that-should-be-truncated-to-12-chars-plus-ellipsis"
        val session = TestDataFactory.createSessionInfo(
            id = longId,
            title = "Test Session"
        )
        val list = JList<SessionInfo>()

        // Act
        val component = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            false,
            false
        ) as JLabel

        // Assert
        val text = component.text
        // ID should be truncated to 12 chars + "..."
        assertTrue(text.contains("very-long-se..."), "Should truncate ID to 12 chars: $text")
        assertTrue(text.contains("ID:"), "Should label the ID field")
    }

    @Test
    fun `test cell renderer displays formatted timestamp`() {
        // Arrange
        val renderer = createCellRenderer()
        val specificTime = 1704067200000L // 2024-01-01 00:00:00 UTC
        val session = SessionInfo(
            id = "time-test",
            title = "Time Test Session",
            directory = "/test/path",
            projectID = "project",
            time = TimeInfo(created = specificTime, updated = specificTime),
            share = null
        )
        val list = JList<SessionInfo>()

        // Act
        val component = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            false,
            false
        ) as JLabel

        // Assert
        val text = component.text
        assertTrue(text.contains("Updated:"), "Should have Updated label")
        assertTrue(text.contains("2024-"), "Should contain year 2024")
        // Date format: yyyy-MM-dd HH:mm:ss
        assertTrue(
            text.matches(Regex(".*\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}.*")),
            "Should contain formatted date: $text"
        )
    }

    @Test
    fun `test cell renderer handles malformed timestamp gracefully`() {
        // Arrange
        val renderer = createCellRenderer()
        val session = SessionInfo(
            id = "bad-time",
            title = "Bad Time Session",
            directory = "/test/path",
            projectID = "project",
            time = TimeInfo(created = -1, updated = -1),
            share = null
        )
        val list = JList<SessionInfo>()

        // Act & Assert - Should not throw exception
        val component = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            false,
            false
        ) as JLabel

        val text = component.text
        // Should either show a date or "Unknown" without crashing
        assertTrue(text.contains("Updated:"), "Should have Updated label")
        // The actual behavior is to show the date or "Unknown" in catch block
    }

    @Test
    fun `test cell renderer shows all session information`() {
        // Arrange
        val renderer = createCellRenderer()
        val session = TestDataFactory.createSessionInfo(
            id = "complete-session-id-12345",
            title = "Complete Session"
        )
        val list = JList<SessionInfo>()

        // Act
        val component = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            false,
            false
        ) as JLabel

        // Assert
        val text = component.text
        // Should show: Title, ID (truncated), Updated timestamp
        assertTrue(text.contains("Complete Session"), "Should show title, got: $text")
        assertTrue(text.contains("ID:"), "Should show ID label, got: $text")
        // The ID is truncated to take(12) which is "complete-ses" not "complete-sess"
        assertTrue(
            text.contains("complete-ses") || text.contains("complete-session"),
            "Should show ID (possibly truncated), got: $text"
        )
        assertTrue(text.contains("Updated:"), "Should show updated label, got: $text")
        assertTrue(text.contains("<small>"), "Should use small text for metadata, got: $text")
    }

    @Test
    fun `test cell renderer selection state`() {
        // Arrange
        val renderer = createCellRenderer()
        val session = TestDataFactory.createSessionInfo()
        val list = JList<SessionInfo>()

        // Act - Test both selected and unselected states
        val unselected = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            false,
            false
        ) as JLabel

        val selected = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            true,
            false
        ) as JLabel

        // Assert
        // The renderer should handle selection state (background/foreground colors)
        // We can't easily test colors in unit tests, but we can verify it doesn't crash
        assertNotNull(unselected.text, "Unselected should have text")
        assertNotNull(selected.text, "Selected should have text")
        assertEquals(unselected.text, selected.text, "Text content should be same regardless of selection")
    }

    @Test
    fun `test cell renderer with null value`() {
        // Arrange
        val renderer = createCellRenderer()
        val list = JList<SessionInfo>()

        // Act
        val component = renderer.getListCellRendererComponent(
            list,
            null,
            0,
            false,
            false
        ) as JLabel

        // Assert
        // Should handle null gracefully (superclass behavior)
        assertNotNull(component, "Should return component even with null value")
        // With null value, renderer falls through to superclass which shows empty/toString
    }

    @Test
    fun `test cell renderer with focus state`() {
        // Arrange
        val renderer = createCellRenderer()
        val session = TestDataFactory.createSessionInfo(title = "Focus Test")
        val list = JList<SessionInfo>()

        // Act
        val withFocus = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            false,
            true
        ) as JLabel

        val withoutFocus = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            false,
            false
        ) as JLabel

        // Assert
        // Should handle focus state without error
        assertTrue(withFocus.text.contains("Focus Test"), "Should contain title with focus")
        assertTrue(withoutFocus.text.contains("Focus Test"), "Should contain title without focus")
    }

    // ========== SessionInfo Model Tests (used by dialog) ==========

    @Test
    fun `test SessionInfo isShared property for shared session`() {
        // Arrange
        val session = TestDataFactory.createSharedSession(shareUrl = "https://example.com/share")

        // Assert
        assertTrue(session.isShared, "Session with ShareInfo should be marked as shared")
        assertEquals("https://example.com/share", session.shareUrl, "Should return correct share URL")
    }

    @Test
    fun `test SessionInfo isShared property for unshared session`() {
        // Arrange
        val session = TestDataFactory.createSessionInfo()

        // Assert
        assertFalse(session.isShared, "Session without ShareInfo should not be marked as shared")
        assertNull(session.shareUrl, "Unshared session should return null for shareUrl")
    }

    @Test
    fun `test SessionInfo with special characters in title`() {
        // Arrange
        val specialTitle = "Test <Session> with \"quotes\" & symbols"
        val session = TestDataFactory.createSessionInfo(title = specialTitle)
        val renderer = createCellRenderer()
        val list = JList<SessionInfo>()

        // Act
        val component = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            false,
            false
        ) as JLabel

        // Assert
        val text = component.text
        // HTML entities might be escaped, but title should be present
        assertTrue(
            text.contains(specialTitle) || text.contains("&lt;") || text.contains("&quot;"),
            "Should handle special characters in title: $text"
        )
    }

    @Test
    fun `test multiple sessions with varying data`() {
        // Arrange
        val renderer = createCellRenderer()
        val list = JList<SessionInfo>()

        val sessions = listOf(
            TestDataFactory.createSessionInfo(id = "session-1", title = "Alpha"),
            TestDataFactory.createSharedSession(id = "session-2", shareUrl = "https://share.url"),
            TestDataFactory.createSessionInfo(id = "session-3", title = "Gamma")
        )

        // Act & Assert
        sessions.forEachIndexed { index, session ->
            val component = renderer.getListCellRendererComponent(
                list,
                session,
                index,
                false,
                false
            ) as JLabel

            assertNotNull(component.text, "Session $index should have rendered text")
            assertTrue(
                component.text.contains(session.title),
                "Session $index should contain its title"
            )

            if (session.isShared) {
                assertTrue(
                    component.text.contains("🔗"),
                    "Shared session $index should show share icon"
                )
            }
        }
    }

    // ========== Edge Case Tests ==========

    @Test
    fun `test cell renderer with very long title`() {
        // Arrange
        val renderer = createCellRenderer()
        val longTitle = "A".repeat(200)
        val session = TestDataFactory.createSessionInfo(title = longTitle)
        val list = JList<SessionInfo>()

        // Act
        val component = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            false,
            false
        ) as JLabel

        // Assert
        assertNotNull(component.text, "Should handle very long title")
        assertTrue(component.text.length < 500, "Rendered text should have reasonable length with HTML")
    }

    @Test
    fun `test cell renderer with empty title`() {
        // Arrange
        val renderer = createCellRenderer()
        val session = TestDataFactory.createSessionInfo(title = "")
        val list = JList<SessionInfo>()

        // Act
        val component = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            false,
            false
        ) as JLabel

        // Assert
        assertNotNull(component.text, "Should handle empty title")
        // Should still show ID and timestamp
        assertTrue(component.text.contains("ID:"), "Should show ID even with empty title")
        assertTrue(component.text.contains("Updated:"), "Should show timestamp even with empty title")
    }

    @Test
    fun `test cell renderer with short session ID`() {
        // Arrange
        val renderer = createCellRenderer()
        val shortId = "short"
        val session = TestDataFactory.createSessionInfo(id = shortId, title = "Short ID")
        val list = JList<SessionInfo>()

        // Act
        val component = renderer.getListCellRendererComponent(
            list,
            session,
            0,
            false,
            false
        ) as JLabel

        // Assert
        val text = component.text
        // Short IDs (<=12 chars) should still get "..." appended
        assertTrue(text.contains("short..."), "Should append ... even to short IDs: $text")
    }

    @Test
    fun `test cell renderer at different list indices`() {
        // Arrange
        val renderer = createCellRenderer()
        val session = TestDataFactory.createSessionInfo(title = "Index Test")
        val list = JList<SessionInfo>()

        // Act & Assert - Test various indices
        for (index in listOf(0, 1, 10, 99, 999)) {
            val component = renderer.getListCellRendererComponent(
                list,
                session,
                index,
                false,
                false
            ) as JLabel

            assertTrue(
                component.text.contains("Index Test"),
                "Should render correctly at index $index"
            )
        }
    }
}
