package com.opencode.test

import com.opencode.model.*
import java.util.UUID

/**
 * Factory for creating test data objects.
 * Generates SessionInfo, requests, and other test data programmatically.
 */
object TestDataFactory {

    /**
     * Create a SessionInfo with default or custom values.
     */
    fun createSessionInfo(
        id: String = "test-${UUID.randomUUID()}",
        title: String = "Test Session",
        directory: String = "/test/path",
        projectID: String = "test-project",
        created: Long = System.currentTimeMillis() - 3600000,
        updated: Long = System.currentTimeMillis(),
        archived: Long? = null,
        shareUrl: String? = null
    ): SessionInfo {
        val timeInfo = TimeInfo(created, updated, archived)
        val shareInfo = if (shareUrl != null) ShareInfo(shareUrl) else null
        return SessionInfo(id, title, directory, projectID, timeInfo, shareInfo)
    }

    /**
     * Create a list of SessionInfo objects for testing.
     */
    fun createSessionList(count: Int): List<SessionInfo> {
        return (1..count).map { i ->
            createSessionInfo(
                id = "session-$i",
                title = "Test Session $i",
                updated = System.currentTimeMillis() - (i * 1000L)
            )
        }
    }

    /**
     * Create a CreateSessionRequest.
     */
    fun createSessionRequest(title: String = "Test Session"): CreateSessionRequest {
        return CreateSessionRequest(title)
    }

    /**
     * Create a SessionResponse.
     */
    fun createSessionResponse(
        id: String = "test-session-id",
        title: String = "Test Session",
        directory: String = "/test/path"
    ): SessionResponse {
        return SessionResponse(id, title, directory)
    }

    /**
     * Create a shared SessionInfo.
     */
    fun createSharedSession(
        id: String = "shared-${UUID.randomUUID()}",
        shareUrl: String = "https://opencode.ai/share/test-token"
    ): SessionInfo {
        return createSessionInfo(id = id, shareUrl = shareUrl)
    }
}
