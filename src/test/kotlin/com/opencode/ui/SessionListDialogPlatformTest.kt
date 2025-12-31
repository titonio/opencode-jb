package com.opencode.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import com.intellij.ui.components.JBList
import com.opencode.model.SessionInfo
import com.opencode.service.OpenCodeService
import com.opencode.test.OpenCodePlatformTestBase
import com.opencode.test.TestDataFactory
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.*
import javax.swing.*

/**
 * Comprehensive platform integration tests for SessionListDialog.
 */
class TestDialogProvider : DialogProvider {
    var inputDialogResult: String? = null
    var yesNoDialogResult: Boolean = true // Default to Yes
    var optionDialogResult: Int = 0 // Default to first option
    var lastError: String? = null
    var lastInfo: String? = null

    override fun showInputDialog(parent: java.awt.Component, message: String, title: String): String? = inputDialogResult

    override fun showErrorDialog(parent: java.awt.Component, message: String, title: String) {
        lastError = message
    }

    override fun showInfoMessage(parent: java.awt.Component, message: String, title: String) {
        lastInfo = message
    }

    override fun showYesNoDialog(parent: java.awt.Component, message: String, title: String): Boolean = yesNoDialogResult

    override fun showOptionDialog(parent: java.awt.Component, message: String, title: String, options: Array<String>, initialValue: Int): Int = optionDialogResult
}

class SessionListDialogPlatformTest : OpenCodePlatformTestBase() {

    private lateinit var mockService: OpenCodeService
    private lateinit var testDialogProvider: TestDialogProvider

    override fun setUp() {
        super.setUp()
        mockService = mock()
        testDialogProvider = TestDialogProvider()
        project.replaceService(OpenCodeService::class.java, mockService, testRootDisposable)

        // Default: return empty sessions list
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(emptyList())
        }
    }

    override fun tearDown() {
        super.tearDown()
    }

    // ========== Dialog Initialization Tests ==========

    fun `test dialog initialization creates UI components`() {
        // Arrange
        val sessions = TestDataFactory.createSessionList(2)
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)

            // Assert
            assertNotNull("Dialog should be created", dialog)
            assertEquals("Dialog title should be set", "OpenCode Sessions", dialog.title)

            // Verify center panel contains expected components
            // In headless environment or test mode, createCenterPanel might not be called immediately by DialogWrapper
            // So we force it or access it carefully.
            // DialogWrapper usually calls createCenterPanel in init(), which is called in constructor.
            // But contentPane is the wrapper panel.
            val centerPanel = findCenterPanel(dialog)
            assertNotNull("Center panel should exist", centerPanel)

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun `test dialog initialization loads sessions on creation`() {
        // Arrange
        val sessions = TestDataFactory.createSessionList(3)
        runBlocking {
            whenever(mockService.listSessions(forceRefresh = true)).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            // Pump events to allow coroutine on Main dispatcher to run
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }

        // Assert
        runBlocking {
            verify(mockService, atLeastOnce()).listSessions(forceRefresh = true)
        }
    }

    fun `test dialog center panel contains list and button panel`() {
        // Arrange
        val sessions = TestDataFactory.createSessionList(1)
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            val centerPanel = findCenterPanel(dialog)

            // Assert
            assertNotNull("Center panel should exist", centerPanel)

            // If centerPanel is found, we can check it
            if (centerPanel != null) {
                // The dialog structure might vary, but let's check for components we know are there
                // The root content pane might not be a JPanel with BorderLayout directly if it's the wrapper's pane
                // But we can look for our components inside it

                // Find scroll pane containing list
                val scrollPane = findComponentOfType(centerPanel, JScrollPane::class.java)
                assertNotNull("Should have scroll pane for list", scrollPane)

                // Find button panel - looking for a panel that contains buttons
                val buttons = findAllComponentsOfType(centerPanel, JButton::class.java)
                assertTrue("Should have buttons", buttons.isNotEmpty())
                val buttonPanel = buttons.first().parent
                assertNotNull("Should have button panel", buttonPanel)
            }

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    // ========== Session List Display Tests ==========

    fun `test empty session list disables OK button`() {
        // Arrange
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(emptyList())
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // Assert
            assertFalse("OK button should be disabled for empty list", dialog.isOKActionEnabled)

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun `test session list displays all sessions`() {
        // Arrange
        val sessions = listOf(
            TestDataFactory.createSessionInfo(id = "session-1", title = "Alpha Session"),
            TestDataFactory.createSessionInfo(id = "session-2", title = "Beta Session"),
            TestDataFactory.createSessionInfo(id = "session-3", title = "Gamma Session")
        )
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)

            // Trigger callback manually or wait
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(sessions)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            val list = findSessionList(dialog)

            // Assert
            assertNotNull("Session list should exist", list)
            val model = list!!.model
            assertEquals("List should have 3 sessions", 3, model.size)

            // Verify sessions are in the model
            val sessionIds = (0 until model.size).map { (model.getElementAt(it) as SessionInfo).id }
            assertTrue("Should contain session-1", sessionIds.contains("session-1"))
            assertTrue("Should contain session-2", sessionIds.contains("session-2"))
            assertTrue("Should contain session-3", sessionIds.contains("session-3"))

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun `test session list renders with custom cell renderer`() {
        // Arrange
        val shareUrl = "https://opencode.ai/share/test123"
        val sessions = listOf(
            TestDataFactory.createSessionInfo(id = "session-1", title = "Regular Session"),
            TestDataFactory.createSharedSession(id = "session-2", shareUrl = shareUrl)
        )
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(sessions)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            val list = findSessionList(dialog)

            // Assert
            assertNotNull("Session list should exist", list)
            val renderer = list!!.cellRenderer
            assertNotNull("List should have custom cell renderer", renderer)

            // Render first session (unshared)
            val renderedUnshared = renderer.getListCellRendererComponent(
                list,
                sessions[0],
                0,
                false,
                false
            ) as JLabel
            val unsharedText = renderedUnshared.text
            assertTrue("Should render session title", unsharedText.contains("Regular Session"))
            assertFalse("Unshared session should not show share icon", unsharedText.contains("🔗"))

            // Render second session (shared)
            val renderedShared = renderer.getListCellRendererComponent(
                list,
                sessions[1],
                1,
                false,
                false
            ) as JLabel
            val sharedText = renderedShared.text
            assertTrue("Should show share icon for shared session", sharedText.contains("🔗"))

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun `test large session list displays correctly`() {
        // Arrange - Create 50 sessions
        val sessions = TestDataFactory.createSessionList(50)
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(sessions)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            val list = findSessionList(dialog)

            // Assert
            assertNotNull("Session list should exist", list)
            assertEquals("List should have 50 sessions", 50, list!!.model.size)

            // Verify scrollability
            val scrollPane = findComponentOfType(findCenterPanel(dialog)!!, JScrollPane::class.java)
            assertNotNull("Should have scroll pane for large list", scrollPane)

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    // ========== User Interaction Tests ==========

    fun `test session selection enables OK button`() {
        // Arrange
        val sessions = TestDataFactory.createSessionList(2)
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(sessions)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            val list = findSessionList(dialog)!!

            // Note: Current implementation enables OK if list is not empty in onSessionsLoaded
            // But let's check if selection affects it too if we clear it?
            // Or strictly speaking, if we select, it should be enabled.

            // Select first session
            list.selectedIndex = 0
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // Assert
            assertTrue("OK button should be enabled after selection", dialog.isOKActionEnabled)
            assertNotNull("Selected session should be available", dialog.getSelectedSession())

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun `test getSelectedSession returns correct session`() {
        // Arrange
        val sessions = TestDataFactory.createSessionList(3)
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(sessions)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            val list = findSessionList(dialog)!!
            list.selectedIndex = 1 // Select second session
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // Assert
            val selectedSession = dialog.getSelectedSession()
            assertNotNull("Should return selected session", selectedSession)
            assertEquals("Should return second session", "session-2", selectedSession!!.id)

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun `test clearing selection disables OK button`() {
        // Arrange
        val sessions = TestDataFactory.createSessionList(2)
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(sessions)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            val list = findSessionList(dialog)!!

            // Select
            list.selectedIndex = 0
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            assertTrue("OK should be enabled after selection", dialog.isOKActionEnabled)

            // Clear
            list.clearSelection()
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // Assert
            assertFalse("OK button should be disabled after clearing selection", dialog.isOKActionEnabled)

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun `test button panel contains all required buttons`() {
        // Arrange
        val sessions = TestDataFactory.createSessionList(1)
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            val centerPanel = findCenterPanel(dialog)

            if (centerPanel != null) {
                // Find all buttons
                val buttons = findAllComponentsOfType(centerPanel, JButton::class.java)

                // Assert
                assertTrue("Should have at least 4 buttons", buttons.size >= 4)

                val buttonTexts = buttons.map { it.text }
                assertTrue("Should have New Session button", buttonTexts.contains("New Session"))
                assertTrue("Should have Delete button", buttonTexts.contains("Delete"))
                assertTrue("Should have Share button", buttonTexts.contains("Share"))
                assertTrue("Should have Refresh button", buttonTexts.contains("Refresh"))
            } else {
                fail("Could not find center panel")
            }

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun `test refresh button reloads sessions`() {
        // Arrange
        val initialSessions = TestDataFactory.createSessionList(2)
        val refreshedSessions = TestDataFactory.createSessionList(4)

        runBlocking {
            whenever(mockService.listSessions(forceRefresh = true))
                .thenReturn(initialSessions)
                .thenReturn(refreshedSessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(initialSessions)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            val list = findSessionList(dialog)!!
            assertEquals("Initial list should have 2 sessions", 2, list.model.size)

            // Find and click refresh button
            val refreshButton = findButtonByText(dialog, "Refresh")
            assertNotNull("Refresh button should exist", refreshButton)
            refreshButton!!.doClick()
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        // Assert
        runBlocking {
            verify(mockService, atLeast(2)).listSessions(forceRefresh = true)
        }
    }

    // ========== Data Refresh and Update Tests ==========

    fun `test dialog updates list when sessions are loaded`() {
        // Arrange
        val initialSessions = listOf(
            TestDataFactory.createSessionInfo(id = "session-1", title = "Session 1")
        )
        val updatedSessions = listOf(
            TestDataFactory.createSessionInfo(id = "session-1", title = "Session 1"),
            TestDataFactory.createSessionInfo(id = "session-2", title = "Session 2")
        )

        runBlocking {
            whenever(mockService.listSessions(any()))
                .thenReturn(initialSessions)
                .thenReturn(updatedSessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService)
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(initialSessions)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            var list = findSessionList(dialog)!!
            assertEquals("Initial list should have 1 session", 1, list.model.size)

            // Manually trigger update
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(updatedSessions)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // Assert
            list = findSessionList(dialog)!!
            assertEquals("Updated list should have 2 sessions", 2, list.model.size)

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun `test dialog handles session list updates via callback`() {
        // Arrange
        val sessions = TestDataFactory.createSessionList(3)
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // Flush initial load

            // Manually trigger callback
            val callback = dialog as SessionListViewModel.ViewCallback
            callback.onSessionsLoaded(TestDataFactory.createSessionList(5))
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            val list = findSessionList(dialog)!!

            // Assert
            assertEquals("List should be updated to 5 sessions", 5, list.model.size)

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    // ========== Dialog Lifecycle Tests ==========

    fun `test dialog disposes viewmodel scope on OK action`() {
        // Arrange
        val sessions = TestDataFactory.createSessionList(1)
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(sessions)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            val list = findSessionList(dialog)!!
            list.selectedIndex = 0
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // Close with OK
            dialog.close(DialogWrapper.OK_EXIT_CODE)

            // Assert - dialog should be disposed cleanly
            assertFalse("Dialog should be closed", dialog.isShowing)
        }
    }

    fun `test dialog disposes viewmodel scope on cancel action`() {
        // Arrange
        val sessions = TestDataFactory.createSessionList(1)
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)

            // Close with Cancel
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)

            // Assert - dialog should be disposed cleanly
            assertFalse("Dialog should be closed", dialog.isShowing)
        }
    }

    // ========== Error Handling Tests ==========

    fun `test dialog handles error loading sessions`() {
        // Arrange
        runBlocking {
            whenever(mockService.listSessions(any())).thenThrow(RuntimeException("Network error"))
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act & Assert - should not crash
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // Dialog should still be created, list should be empty
            val list = findSessionList(dialog)!!
            assertEquals("List should be empty on error", 0, list.model.size)

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun `test dialog displays error callback messages`() {
        // Arrange
        val sessions = TestDataFactory.createSessionList(1)
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        // Suppress dialogs
        // Messages.setTestDialog(TestDialog.OK)

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)

            // Trigger error callback directly
            val callback = dialog as SessionListViewModel.ViewCallback
            callback.onError("Test error message")
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun `test dialog handles success callback messages`() {
        // Arrange
        val sessions = TestDataFactory.createSessionList(1)
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            val callback = dialog as SessionListViewModel.ViewCallback

            // Trigger success callback
            callback.onSuccess("Test success message")
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    // ========== Edge Cases Tests ==========

    fun `test dialog with single session allows selection`() {
        // Arrange
        val sessions = listOf(
            TestDataFactory.createSessionInfo(id = "only-session", title = "Only Session")
        )
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(sessions)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            val list = findSessionList(dialog)!!
            list.selectedIndex = 0
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // Assert
            assertTrue("OK button should be enabled for single session", dialog.isOKActionEnabled)
            assertEquals("Should select the only session", "only-session", dialog.getSelectedSession()!!.id)

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun `test dialog list has single selection mode`() {
        // Arrange
        val sessions = TestDataFactory.createSessionList(5)
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(sessions)
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(sessions)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            val list = findSessionList(dialog)!!

            // Assert
            assertEquals(
                "List should use SINGLE_SELECTION mode",
                ListSelectionModel.SINGLE_SELECTION,
                list.selectionMode
            )

            // Try to select multiple (should only select last)
            list.selectedIndices = intArrayOf(0, 2, 4)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // In SINGLE_SELECTION mode, only one should be selected
            assertEquals("Should only have one selected item", 1, list.selectedIndices.size)

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun `test delete button prompts for confirmation`() {
        // Arrange
        val session = TestDataFactory.createSessionInfo(id = "delete-me", title = "To Delete")
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(listOf(session))
            whenever(mockService.deleteSession(any())).thenReturn(true)
        }

        // Configure dialog provider to say YES
        testDialogProvider.yesNoDialogResult = true

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(listOf(session))
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // Select session
            val list = findSessionList(dialog)!!
            list.selectedIndex = 0
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // Click Delete
            val deleteButton = findButtonByText(dialog, "Delete")
            assertNotNull("Delete button should exist", deleteButton)
            deleteButton!!.doClick()
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }

        // Assert
        runBlocking {
            verify(mockService).deleteSession("delete-me")
        }
    }

    fun `test delete button cancellation aborts deletion`() {
        // Arrange
        val session = TestDataFactory.createSessionInfo(id = "keep-me", title = "To Keep")
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(listOf(session))
        }

        // Configure dialog provider to say NO
        testDialogProvider.yesNoDialogResult = false

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(listOf(session))
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // Select session
            val list = findSessionList(dialog)!!
            list.selectedIndex = 0
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // Click Delete
            val deleteButton = findButtonByText(dialog, "Delete")
            deleteButton!!.doClick()
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }

        // Assert
        runBlocking {
            verify(mockService, never()).deleteSession(any())
        }
    }

    fun `test share button shares unshared session`() {
        // Arrange
        val session = TestDataFactory.createSessionInfo(id = "share-me")
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(listOf(session))
            whenever(mockService.shareSession(any())).thenReturn("https://url")
        }

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(listOf(session))
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // Select session
            findSessionList(dialog)!!.selectedIndex = 0

            // Click Share
            findButtonByText(dialog, "Share")!!.doClick()
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }

        // Assert
        runBlocking {
            verify(mockService).shareSession("share-me")
        }
    }

    fun `test share button shows options for shared session`() {
        // Arrange
        val session = TestDataFactory.createSharedSession(id = "already-shared")
        runBlocking {
            whenever(mockService.listSessions(any())).thenReturn(listOf(session))
            whenever(mockService.unshareSession(any())).thenReturn(true)
        }

        // Configure dialog provider to select "Unshare" (index 1)
        testDialogProvider.optionDialogResult = 1

        ApplicationManager.getApplication().invokeAndWait {
            // Act
            val dialog = SessionListDialog(project, mockService, testDialogProvider)
            (dialog as SessionListViewModel.ViewCallback).onSessionsLoaded(listOf(session))
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            // Select session
            findSessionList(dialog)!!.selectedIndex = 0

            // Click Share
            findButtonByText(dialog, "Share")!!.doClick()
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }

        // Assert
        runBlocking {
            verify(mockService).unshareSession("already-shared")
        }
    }

    // ========== Helper Methods ==========

    private fun findCenterPanel(dialog: SessionListDialog): JComponent? {
        // Access the center panel via contentPane
        return dialog.rootPanel
    }

    private fun findSessionList(dialog: SessionListDialog): JBList<SessionInfo>? {
        return dialog.sessionList
    }

    private fun findButtonByText(dialog: SessionListDialog, text: String): JButton? {
        val centerPanel = findCenterPanel(dialog) ?: return null
        return findAllComponentsOfType(centerPanel, JButton::class.java)
            .firstOrNull { it.text == text }
    }

    private fun <T : java.awt.Component> findComponentOfType(
        container: java.awt.Container,
        type: Class<T>,
        skipFirst: Boolean = false
    ): T? {
        var skipped = false
        val queue = java.util.LinkedList<java.awt.Component>()
        queue.add(container)

        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()

            if (type.isInstance(component)) {
                if (component == container && skipFirst && !skipped) {
                    skipped = true
                } else if (component != container || !skipFirst) {
                    @Suppress("UNCHECKED_CAST")
                    return component as T
                }
            }

            if (component is java.awt.Container) {
                for (child in component.components) {
                    queue.add(child)
                }
            }
        }
        return null
    }

    private fun <T : java.awt.Component> findAllComponentsOfType(
        container: java.awt.Container,
        type: Class<T>
    ): List<T> {
        val results = mutableListOf<T>()
        val queue = java.util.LinkedList<java.awt.Component>()
        queue.add(container)

        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            if (type.isInstance(component)) {
                @Suppress("UNCHECKED_CAST")
                results.add(component as T)
            }
            if (component is java.awt.Container) {
                for (child in component.components) {
                    queue.add(child)
                }
            }
        }
        return results
    }
}
